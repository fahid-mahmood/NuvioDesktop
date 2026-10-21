package com.nuvio.app.features.home.aggregate

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.catalog.mergeCatalogItems
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.withLock

/**
 * Maintains the aggregated "Latest Additions" row: fetches every enabled addon catalog,
 * deduplicates across sources, and persists first-seen timestamps so each title keeps a
 * stable "Added <date>" badge.
 */
object AggregatedCatalogRepository {
    private val log = Logger.withTag("AggregatedCatalogRepository")

    private const val MIN_AUTO_REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val FETCH_BATCH_SIZE = 4
    private const val MAX_ITEMS_PER_SOURCE = 100
    private const val MAX_PAGES_PER_SOURCE = 2
    private const val SOURCE_FETCH_TIMEOUT_MS = 30_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val persistenceMutex = Mutex()

    private val _uiState = MutableStateFlow(AggregatedCatalogUiState())
    val uiState: StateFlow<AggregatedCatalogUiState> = _uiState.asStateFlow()

    private var registry: StoredAggregatedRegistry = StoredAggregatedRegistry()
    private var registryLoaded = false
    private var activeJob: Job? = null
    private var activeSourceKey: String? = null
    private var lastFetchedSourceKey: String? = null

    fun refresh(addons: List<ManagedAddon>, force: Boolean = false) {
        ensureRegistryLoaded()
        if (!registry.enabled) {
            activeJob?.cancel()
            activeJob = null
            activeSourceKey = null
            _uiState.value = AggregatedCatalogUiState(isEnabled = false)
            return
        }

        val sources = buildAggregateSources(addons)
        val sourceKey = sources.joinToString(separator = "|") { source -> source.definition.cacheKey }

        if (!force && activeSourceKey == sourceKey && _uiState.value.isLoading) return
        val throttled = registry.lastRefreshEpochMs > 0L &&
            EpisodeReleaseDatePlatform.nowEpochMs() - registry.lastRefreshEpochMs < MIN_AUTO_REFRESH_INTERVAL_MS
        val sourcesUnchanged = sourceKey == lastFetchedSourceKey
        log.i { "refresh() — sources=${sources.size} force=$force throttled=$throttled sourcesUnchanged=$sourcesUnchanged" }
        if (!force && throttled && sourcesUnchanged && _uiState.value.items.isNotEmpty()) {
            publishFromRegistry(isEnabled = true, isLoading = false)
            return
        }

        activeJob?.cancel()
        activeSourceKey = sourceKey
        _uiState.update { it.copy(isEnabled = true, isLoading = true, errorMessage = null) }

        activeJob = scope.launch {
            val results = sources.chunked(FETCH_BATCH_SIZE).flatMap { batch ->
                batch.map { source ->
                    async {
                        runCatching {
                            withTimeout(SOURCE_FETCH_TIMEOUT_MS) {
                                fetchSourceItems(source, forceRefresh = force)
                            }
                        }.getOrElse { error ->
                            log.w { "fetch failed: ${source.definition.key} — ${error.message}" }
                            emptyList()
                        }.let { items -> AggregatedFetchResult(source = source, items = items) }
                    }
                }.awaitAll()
            }
            val fetchTime = EpisodeReleaseDatePlatform.nowEpochMs()
            val (items, nextRegistry) = mergeAggregatedItems(
                results = results,
                registry = registry,
                nowEpochMs = fetchTime,
            )
            registry = nextRegistry
            persistRegistry()

            if (activeSourceKey != sourceKey) return@launch
            activeSourceKey = null
            lastFetchedSourceKey = sourceKey
            log.i { "refresh() completed — merged=${items.size} entries from ${results.count { it.items.isNotEmpty() }} sources" }
            _uiState.value = AggregatedCatalogUiState(
                isEnabled = true,
                isLoading = false,
                items = items,
                sourceCount = sources.size,
                errorMessage = null,
            )
        }
    }

    fun setEnabled(enabled: Boolean) {
        ensureRegistryLoaded()
        registry = registry.copy(enabled = enabled)
        persistRegistry()
        if (!enabled) {
            activeJob?.cancel()
            activeJob = null
            activeSourceKey = null
            _uiState.value = AggregatedCatalogUiState(isEnabled = false)
        } else {
            _uiState.update { it.copy(isEnabled = true) }
        }
    }

    fun clear() {
        activeJob?.cancel()
        activeJob = null
        activeSourceKey = null
        _uiState.value = AggregatedCatalogUiState()
    }

    private suspend fun fetchSourceItems(
        source: AggregateCatalogSource,
        forceRefresh: Boolean,
    ): List<MetaPreview> {
        val definition = source.definition
        var items = emptyList<MetaPreview>()
        var nextSkip: Int? = null
        var pagesFetched = 0
        do {
            val page = fetchCatalogPage(
                manifestUrl = definition.manifestUrl,
                type = definition.type,
                catalogId = definition.catalogId,
                skip = nextSkip,
                maxItems = MAX_ITEMS_PER_SOURCE - items.size,
                forceRefresh = forceRefresh,
            )
            items = mergeCatalogItems(items, page.items)
            nextSkip = page.nextSkip
            pagesFetched++
        } while (
            definition.supportsPagination && nextSkip != null &&
            items.size < MAX_ITEMS_PER_SOURCE && pagesFetched < MAX_PAGES_PER_SOURCE
        )
        return items.take(MAX_ITEMS_PER_SOURCE)
    }

    private fun ensureRegistryLoaded() {
        if (registryLoaded) return
        registry = AggregatedCatalogRegistry.load()
        registryLoaded = true
        if (registry.enabled && registry.entries.isNotEmpty()) {
            publishFromRegistry(isEnabled = true, isLoading = false)
        }
    }

    /**
     * Poster and title data is not persisted (only timestamps), so entries that predate
     * this app launch wait for the next network refresh to reappear — their first-seen
     * dates are never lost meanwhile.
     */
    private fun publishFromRegistry(isEnabled: Boolean, isLoading: Boolean) {
        _uiState.update { state -> state.copy(isEnabled = isEnabled, isLoading = isLoading) }
    }

    private fun persistRegistry() {
        val snapshot = registry
        scope.launch {
            persistenceMutex.withLock {
                runCatching { AggregatedCatalogRegistry.save(snapshot) }
            }
        }
    }
}
