package com.nuvio.app.features.home.aggregate

import com.nuvio.app.features.addons.AddonBehaviorHints
import com.nuvio.app.features.addons.AddonCatalog
import com.nuvio.app.features.addons.AddonManifest
import com.nuvio.app.features.addons.AddonResource
import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.home.HomeCatalogDefinition
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AggregatedCatalogAggregatorTest {

    @Test
    fun sameTitleAcrossAddonsDeduplicatesToOneItemWithAllSources() {
        val now = 1_800_000_000_000L
        val results = listOf(
            fetchResult("com.addon.a", "Addon A", "tt1187043", "3 Idiots", "2009"),
            fetchResult("com.addon.b", "Addon B", "vega-3-idiots", "3 Idiots", "2009"),
            fetchResult("com.addon.c", "Addon C", "hdhub-3idiots", "3 Idiots"),
        )

        val (items, registry) = mergeAggregatedItems(results, StoredAggregatedRegistry(), now)

        assertEquals(1, items.size)
        assertEquals(3, items.first().sources.size)
        assertEquals(now, items.first().firstSeenEpochMs)
        assertEquals(1, registry.entries.size)
    }

    @Test
    fun imdbIdPreviewWinsAsPrimary() {
        val now = 1_800_000_000_000L
        val results = listOf(
            fetchResult("com.addon.a", "Addon A", "site-slug", "3 Idiots", poster = "https://a/poster.jpg"),
            fetchResult("com.addon.b", "Addon B", "tt1187043", "3 Idiots", poster = null),
        )

        val (items, _) = mergeAggregatedItems(results, StoredAggregatedRegistry(), now)

        assertEquals("tt1187043", items.first().preview.id)
    }

    @Test
    fun firstSeenTimestampSurvivesSubsequentRefreshes() {
        val firstSeen = 1_700_000_000_000L
        val now = 1_800_000_000_000L
        val registry = StoredAggregatedRegistry(
            entries = listOf(
                StoredAggregatedEntry(
                    matchKey = "imdb:tt1187043",
                    firstSeenEpochMs = firstSeen,
                    lastSeenEpochMs = firstSeen,
                ),
            ),
        )
        val results = listOf(fetchResult("com.addon.a", "Addon A", "tt1187043", "3 Idiots", "2009"))

        val (items, nextRegistry) = mergeAggregatedItems(results, registry, now)

        assertEquals(firstSeen, items.first().firstSeenEpochMs)
        assertEquals(now, items.first().lastSeenEpochMs)
        assertEquals(firstSeen, nextRegistry.entries.first().firstSeenEpochMs)
    }

    @Test
    fun sameTitleDifferentYearsStaysSeparate() {
        val now = 1_800_000_000_000L
        val results = listOf(
            fetchResult("com.addon.a", "Addon A", "dune-1984", "Dune", "1984"),
            fetchResult("com.addon.a", "Addon A", "dune-2021", "Dune", "2021"),
        )

        val (items, _) = mergeAggregatedItems(results, StoredAggregatedRegistry(), now)

        assertEquals(2, items.size)
    }

    @Test
    fun yearlessObservationFoldsIntoSingleYearedBucket() {
        val now = 1_800_000_000_000L
        val results = listOf(
            fetchResult("com.addon.a", "Addon A", "dune-2021", "Dune", "2021"),
            fetchResult("com.addon.b", "Addon B", "dune-slug", "Dune"),
        )

        val (items, _) = mergeAggregatedItems(results, StoredAggregatedRegistry(), now)

        assertEquals(1, items.size)
        assertEquals(2, items.first().sources.size)
    }

    @Test
    fun itemsSortNewestFirstSeenFirst() {
        val now = 1_800_000_000_000L
        val day = 24L * 60L * 60L * 1000L
        val registry = StoredAggregatedRegistry(
            entries = listOf(
                StoredAggregatedEntry("imdb:tt0000001", now - 10 * day, now - 10 * day),
                StoredAggregatedEntry("imdb:tt0000002", now - 1 * day, now - 1 * day),
            ),
        )
        val results = listOf(
            fetchResult("com.addon.a", "Addon A", "tt0000001", "Old Title", "2001"),
            fetchResult("com.addon.a", "Addon A", "tt0000002", "New Title", "2002"),
        )

        val (items, _) = mergeAggregatedItems(results, registry, now)

        assertEquals("New Title", items.first().preview.name)
    }

    @Test
    fun buildAggregateSourcesKeepsContentTypesAndExcludesLiveTv() {
        val addons = listOf(
            managedAddon(
                manifestId = "com.bridge",
                catalogs = listOf(
                    AddonCatalog(type = "movie", id = "latest-movies", name = "Latest Movies"),
                    AddonCatalog(type = "series", id = "latest-series", name = "Latest Series"),
                    AddonCatalog(type = "other", id = "cnc_4KHDHUB", name = "4K HDHUB"),
                    AddonCatalog(type = "tv", id = "live", name = "Live Channels"),
                ),
            ),
        )

        val sources = buildAggregateSources(addons)

        assertEquals(setOf("movie", "series", "other"), sources.map { it.definition.type }.toSet())
    }

    @Test
    fun failedSourcesAreSimplyAbsent() {
        val now = 1_800_000_000_000L
        val results = listOf(
            fetchResult("com.addon.a", "Addon A", "tt1187043", "3 Idiots", "2009"),
            AggregatedFetchResult(
                source = aggregateSource("com.addon.b", "Addon B"),
                items = emptyList(),
            ),
        )

        val (items, _) = mergeAggregatedItems(results, StoredAggregatedRegistry(), now)

        assertEquals(1, items.size)
        assertTrue(items.first().sources.all { it.addonId == "com.addon.a" })
    }

    @Test
    fun sortPrioritizesNewestReleaseYearThenNewestUpload() {
        val now = 1_800_000_000_000L
        val day = 24L * 60L * 60L * 1000L
        val results = listOf(
            fetchResult("com.addon.a", "Addon A", "tt0000001", "Old Known", "2001"),
            fetchResult("com.addon.a", "Addon A", "tt0000002", "Recent Release", "2026"),
            fetchResult("com.addon.a", "Addon A", "site-slug-no-year", "Unknown Year"),
        )

        val (items, _) = mergeAggregatedItems(results, StoredAggregatedRegistry(), now)

        // 2026 first, then the dated 2001 title; year-less items sort last, upload ties broken by name.
        assertEquals("Recent Release", items[0].preview.name)
        assertEquals("Old Known", items[1].preview.name)
        assertEquals("Unknown Year", items.last().preview.name)
    }

    @Test
    fun industryBucketsMapFromLanguageAndGenres() {
        assertEquals(INDUSTRY_BOLLYWOOD, industryFor("hi", emptyList()))
        assertEquals(INDUSTRY_HOLLYWOOD, industryFor("en", emptyList()))
        assertEquals(INDUSTRY_SOUTH_INDIAN, industryFor("ta", emptyList()))
        assertEquals(INDUSTRY_SOUTH_INDIAN, industryFor("te", emptyList()))
        assertEquals(INDUSTRY_KOREAN, industryFor("ko", emptyList()))
        assertEquals(INDUSTRY_ANIME, industryFor("ja", listOf("Animation")))
        assertEquals(INDUSTRY_INTERNATIONAL, industryFor("ja", emptyList()))
        assertEquals(INDUSTRY_CHINESE, industryFor("zh", emptyList()))
        assertEquals(INDUSTRY_INTERNATIONAL, industryFor("fr", emptyList()))
        assertEquals(null, industryFor(null, emptyList()))
    }

    @Test
    fun enrichmentPersistsAcrossRefreshes() {
        val now = 1_800_000_000_000L
        val registry = StoredAggregatedRegistry(
            entries = listOf(
                StoredAggregatedEntry(
                    matchKey = "imdb:tt1187043",
                    firstSeenEpochMs = now - 1,
                    lastSeenEpochMs = now - 1,
                    releaseYear = 2009,
                    originalLanguage = "hi",
                    genres = listOf("Comedy", "Drama"),
                ),
            ),
        )
        val results = listOf(fetchResult("com.addon.a", "Addon A", "tt1187043", "3 Idiots"))

        val (items, nextRegistry) = mergeAggregatedItems(results, registry, now)

        assertEquals(2009, items.first().releaseYear)
        assertEquals(listOf("Comedy", "Drama"), items.first().genres)
        assertEquals("hi", items.first().originalLanguage)
        assertEquals(2009, nextRegistry.entries.first().releaseYear)
    }

    @Test
    fun suffixedDuplicatesMergeIntoOneBucket() {
        val now = 1_800_000_000_000L
        val results = listOf(
            fetchResult("com.addon.a", "Addon A", "site-animals-nf", "Animals (2026) Netflix"),
            fetchResult("com.addon.b", "Addon B", "site-animals-plain", "Animals (2026)"),
        )

        val (items, _) = mergeAggregatedItems(results, StoredAggregatedRegistry(), now)

        assertEquals(1, items.size)
        assertEquals(2, items.first().sources.size)
        assertEquals(2026, items.first().releaseYear)
    }

    @Test
    fun adultTitlesMergeCleanedAndAreFlagged() {
        val now = 1_800_000_000_000L
        val results = listOf(
            fetchResult("com.addon.a", "Addon A", "site-blue", "[18+] Blue (2026) Primewire"),
            fetchResult("com.addon.b", "Addon B", "site-blue-2", "Blue (2026)"),
        )

        val (items, _) = mergeAggregatedItems(results, StoredAggregatedRegistry(), now)

        assertEquals(1, items.size)
        assertEquals(true, items.first().isAdult)
        assertEquals(INDUSTRY_ADULT, items.first().industry)
        assertEquals("Blue (2026)", items.first().preview.name)
    }

    @Test
    fun isoDateLabelFormatsStableDates() {
        // 1_800_000_000_000 ms is 2027-01-15T08:00:00Z.
        assertEquals("2027-01-15", 1_800_000_000_000L.toIsoDateLabel())
    }
}

private fun fetchResult(
    addonId: String,
    addonName: String,
    itemId: String,
    title: String,
    releaseInfo: String? = null,
    poster: String? = null,
): AggregatedFetchResult = AggregatedFetchResult(
    source = aggregateSource(addonId, addonName),
    items = listOf(
        MetaPreview(
            id = itemId,
            type = "movie",
            name = title,
            poster = poster,
            releaseInfo = releaseInfo,
            posterShape = PosterShape.Poster,
        ),
    ),
)

private fun aggregateSource(addonId: String, addonName: String): AggregateCatalogSource =
    AggregateCatalogSource(
        definition = HomeCatalogDefinition(
            key = "$addonId:movie:latest",
            defaultTitle = "Latest",
            catalogName = "Latest",
            addonName = addonName,
            manifestUrl = "https://$addonId.example/manifest.json",
            type = "movie",
            catalogId = "latest",
            supportsPagination = true,
            descriptorSignature = "sig",
        ),
        addonName = addonName,
    )

private fun managedAddon(manifestId: String, catalogs: List<AddonCatalog>): ManagedAddon =
    ManagedAddon(
        manifestUrl = "https://$manifestId.example/manifest.json",
        manifest = AddonManifest(
            id = manifestId,
            name = manifestId,
            description = "",
            version = "1.0.0",
            resources = listOf(AddonResource(name = "catalog", types = catalogs.map { it.type })),
            types = catalogs.map { it.type },
            catalogs = catalogs,
            behaviorHints = AddonBehaviorHints(),
            transportUrl = "https://$manifestId.example/manifest.json",
        ),
    )
