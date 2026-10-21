package com.nuvio.app.features.home.aggregate

import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.home.HomeCatalogDefinition
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.buildHomeCatalogDefinitions
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * A single addon catalog that participates in the aggregated "Latest Additions" row.
 * Built from the same definitions the home screen uses, minus live TV and catalogs
 * that require extra inputs.
 */
data class AggregateCatalogSource(
    val definition: HomeCatalogDefinition,
    val addonName: String,
)

// "other" is how scraper bridges (e.g. CNCVerse) expose movie/series content; live "tv" stays excluded.
private val aggregatableTypes = setOf("movie", "series", "other")

fun buildAggregateSources(addons: List<ManagedAddon>): List<AggregateCatalogSource> =
    buildHomeCatalogDefinitions(addons)
        .filter { definition -> definition.type in aggregatableTypes }
        .map { definition -> AggregateCatalogSource(definition = definition, addonName = definition.addonName) }

fun AddonCatalog.isAggregatable(): Boolean = type in aggregatableTypes

@Serializable
internal data class StoredAggregatedEntry(
    val matchKey: String,
    val firstSeenEpochMs: Long,
    val lastSeenEpochMs: Long,
    val sourceAddonIds: List<String> = emptyList(),
    val releaseYear: Int? = null,
    val originalLanguage: String? = null,
    val genres: List<String> = emptyList(),
)

@Serializable
internal data class StoredAggregatedRegistry(
    val version: Int = 1,
    val enabled: Boolean = true,
    val lastRefreshEpochMs: Long = 0L,
    val entries: List<StoredAggregatedEntry> = emptyList(),
)

internal object AggregatedCatalogRegistry {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val MAX_ENTRIES = 5000

    fun load(): StoredAggregatedRegistry =
        AggregatedCatalogStorage.loadPayload()
            ?.let { payload -> runCatching { json.decodeFromString<StoredAggregatedRegistry>(payload) }.getOrNull() }
            ?: StoredAggregatedRegistry()

    fun save(registry: StoredAggregatedRegistry) {
        AggregatedCatalogStorage.savePayload(json.encodeToString(registry.prune()))
    }

    private fun StoredAggregatedRegistry.prune(): StoredAggregatedRegistry {
        if (entries.size <= MAX_ENTRIES) return this
        val kept = entries.sortedByDescending { it.lastSeenEpochMs }.take(MAX_ENTRIES)
        return copy(entries = kept)
    }
}

internal data class AggregatedFetchResult(
    val source: AggregateCatalogSource,
    val items: List<MetaPreview>,
)

/** One source item with the identity signals merging needs. */
private data class SourceItemIdentity(
    val bucketKey: String,
    val titleBaseKey: String,
    val year: Int?,
    val item: MetaPreview,
    val sourceRef: AggregatedSourceRef,
)

/**
 * Merges every source's items into deduplicated [AggregatedCatalogItem]s.
 *
 * Items carrying an IMDb id bucket by that id. Scraper items with site-specific ids
 * bucket by normalized title + type (+ year when known) and then fold into an existing
 * IMDb bucket of the same title, so the same release appears once regardless of which
 * id scheme a source uses. Observations that both supply years never merge when the
 * years differ, keeping same-title remakes apart. `firstSeenEpochMs` is stamped the
 * first time a bucket is observed (previous observations come from [registry]),
 * keeping the "Added \<date\>" badge stable across refreshes.
 */
internal fun mergeAggregatedItems(
    results: List<AggregatedFetchResult>,
    registry: StoredAggregatedRegistry,
    nowEpochMs: Long,
): Pair<List<AggregatedCatalogItem>, StoredAggregatedRegistry> {
    val knownFirstSeen = registry.entries.associateBy { it.matchKey }
    val identities = results.flatMap(::identitiesFor)

    val imdbBuckets = LinkedHashMap<String, MutableList<SourceItemIdentity>>()
    val titleBuckets = LinkedHashMap<String, MutableList<SourceItemIdentity>>()
    identities.forEach { identity ->
        val target = if (identity.bucketKey.startsWith("imdb:")) imdbBuckets else titleBuckets
        target.getOrPut(identity.bucketKey) { mutableListOf() }.add(identity)
    }

    val finalBuckets = LinkedHashMap<String, MutableList<SourceItemIdentity>>()
    imdbBuckets.forEach { (key, items) -> finalBuckets[key] = items }

    val imdbBucketYearsByTitle = imdbBuckets.mapValues { (_, items) ->
        items.firstNotNullOfOrNull { it.year }
    }
    val titleBucketsByBase = titleBuckets.entries.groupBy { (_, items) -> items.first().titleBaseKey }

    titleBucketsByBase.forEach { (titleBase, buckets) ->
        val imdbKeyForTitle = finalBuckets.keys.firstOrNull { key ->
            key.startsWith("imdb:") && finalBuckets[key]?.firstOrNull()?.titleBaseKey == titleBase
        }
        val imdbYear = imdbKeyForTitle?.let { key -> imdbBucketYearsByTitle[key] }

        val yearFul = buckets.filter { (key, _) -> key.count { c -> c == '|' } >= 2 }
        val yearLess = buckets.filter { (key, _) -> key.count { c -> c == '|' } < 2 }

        // Year-ful buckets fold into the same-title IMDb bucket only when the years agree.
        yearFul.forEach { (key, items) ->
            val year = items.first().year
            val foldsIntoImdb = imdbKeyForTitle != null && (imdbYear == null || imdbYear == year)
            if (foldsIntoImdb && imdbKeyForTitle != null) {
                finalBuckets[imdbKeyForTitle]!!.addAll(items)
            } else {
                finalBuckets[key] = items
            }
        }

        // Year-less buckets are ambiguous: fold into the IMDb bucket when it is the only
        // candidate (or the known years agree with it), else into the single year-ful
        // bucket of this title, else stand alone.
        yearLess.forEach { (key, items) ->
            val knownYears = yearFul.mapNotNull { (_, bucket) -> bucket.first().year }.distinct()
            val foldsIntoImdb = imdbKeyForTitle != null && imdbYear != null &&
                (knownYears.isEmpty() || knownYears == listOf(imdbYear))
            val foldsIntoUnknownYearImdb = imdbKeyForTitle != null && imdbYear == null && knownYears.isEmpty()
            when {
                foldsIntoImdb || foldsIntoUnknownYearImdb -> finalBuckets[imdbKeyForTitle]!!.addAll(items)
                yearFul.size == 1 && finalBuckets.containsKey(yearFul.first().key) ->
                    finalBuckets[yearFul.first().key]!!.addAll(items)
                else -> finalBuckets[key] = items
            }
        }
    }

    val mergedItems = finalBuckets.values.map { bucketItems ->
        val matchKey = bucketItems.first().bucketKey
        val stored = knownFirstSeen[matchKey]
        val firstSeen = stored?.firstSeenEpochMs ?: nowEpochMs
        val orderedSources = bucketItems
            .distinctBy { identity -> "${identity.sourceRef.addonId}:" + identity.item.id }
            .map { identity -> identity.sourceRef.copy(itemId = identity.item.id) }
        AggregatedCatalogItem(
            matchKey = matchKey,
            preview = bucketItems.map { it.item }.reduce(::pickPrimaryPreview),
            firstSeenEpochMs = firstSeen,
            lastSeenEpochMs = nowEpochMs,
            sources = orderedSources,
            releaseYear = bucketItems.firstNotNullOfOrNull { it.year } ?: stored?.releaseYear,
            originalLanguage = stored?.originalLanguage,
            genres = stored?.genres.orEmpty(),
        )
    }.sortedWith(aggregatedItemComparator)

    val nextEntries = LinkedHashMap<String, StoredAggregatedEntry>()
    mergedItems.forEach { item ->
        nextEntries[item.matchKey] = StoredAggregatedEntry(
            matchKey = item.matchKey,
            firstSeenEpochMs = item.firstSeenEpochMs,
            lastSeenEpochMs = item.lastSeenEpochMs,
            sourceAddonIds = item.sources.map { it.addonId }.distinct(),
            releaseYear = item.releaseYear,
            originalLanguage = item.originalLanguage,
            genres = item.genres,
        )
    }

    // Keep observing titles that did not surface this round so their first-seen date
    // survives when a source is temporarily slow rather than erasing history.
    knownFirstSeen.forEach { (matchKey, entry) ->
        if (!nextEntries.containsKey(matchKey) && nowEpochMs - entry.lastSeenEpochMs < RETENTION_MS) {
            nextEntries[matchKey] = entry
        }
    }

    val nextRegistry = registry.copy(
        lastRefreshEpochMs = nowEpochMs,
        entries = nextEntries.values.toList(),
    )
    return mergedItems to nextRegistry
}

private const val RETENTION_MS = 90L * 24L * 60L * 60L * 1000L

private fun identitiesFor(result: AggregatedFetchResult): List<SourceItemIdentity> =
    result.items.map { item ->
        val imdbId = extractImdbId(item.id)
        val titleBase = titleBaseKey(item)
        val year = extractReleaseYear(item.releaseInfo, item.rawReleaseDate)
            ?: yearFromTitle(item.name)
        val bucketKey = when {
            imdbId != null -> "imdb:$imdbId"
            year != null -> "$titleBase|$year"
            else -> titleBase
        }
        SourceItemIdentity(
            bucketKey = bucketKey,
            titleBaseKey = titleBase,
            year = year,
            item = item,
            sourceRef = AggregatedSourceRef(
                addonId = result.source.definition.key.substringBefore(':'),
                addonName = result.source.addonName,
                manifestUrl = result.source.definition.manifestUrl,
                catalogKey = result.source.definition.key,
                itemId = item.id,
            ),
        )
    }

private fun titleBaseKey(item: MetaPreview): String =
    "t:${normalizeTitleForMatch(item.name)}|${item.type}"

/** Prefers an IMDb-id preview so details open against a universal id when one source has it. */
private fun pickPrimaryPreview(current: MetaPreview, candidate: MetaPreview): MetaPreview {
    val candidateHasImdb = extractImdbId(candidate.id) != null
    val currentHasImdb = extractImdbId(current.id) != null
    return when {
        currentHasImdb -> current
        candidateHasImdb -> candidate
        current.poster.isNullOrBlank() && !candidate.poster.isNullOrBlank() -> candidate
        else -> current
    }
}
