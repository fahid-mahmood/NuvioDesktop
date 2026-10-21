package com.nuvio.app.features.home.aggregate

import com.nuvio.app.features.home.MetaPreview

data class AggregatedSourceRef(
    val addonId: String,
    val addonName: String,
    val manifestUrl: String,
    val catalogKey: String,
    val itemId: String,
)

data class AggregatedCatalogItem(
    val matchKey: String,
    val preview: MetaPreview,
    val firstSeenEpochMs: Long,
    val lastSeenEpochMs: Long,
    val sources: List<AggregatedSourceRef>,
    /** Release year from the source feed or TMDB enrichment; null until known. */
    val releaseYear: Int? = null,
    /** Spoken-language origin from TMDB, e.g. "hi", "en", "ta". */
    val originalLanguage: String? = null,
    val genres: List<String> = emptyList(),
) {
    val sourceCount: Int
        get() = sources.size

    val industry: String?
        get() = industryFor(originalLanguage, genres)
}

data class AggregatedCatalogUiState(
    val isEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val items: List<AggregatedCatalogItem> = emptyList(),
    val sourceCount: Int = 0,
    val errorMessage: String? = null,
)

internal fun AggregatedCatalogItem.relativeAddedDays(nowEpochMs: Long): Long {
    if (firstSeenEpochMs <= 0L) return 0L
    val days = (nowEpochMs - firstSeenEpochMs) / MILLIS_PER_DAY
    return days.coerceAtLeast(0L)
}

/** Year released first (newest year at top, unknown years last), then newest upload. */
internal val aggregatedItemComparator: Comparator<AggregatedCatalogItem> =
    compareByDescending<AggregatedCatalogItem> { it.releaseYear ?: Int.MIN_VALUE }
        .thenByDescending { it.firstSeenEpochMs }
        .thenBy { it.preview.name.lowercase() }

/**
 * Maps a TMDB original language (plus animation genres) to an industry bucket for
 * filtering. Returns null when the language is unknown.
 */
internal fun industryFor(originalLanguage: String?, genres: List<String>): String? {
    if (originalLanguage == null) return null
    val animated = genres.any { it.equals("Animation", ignoreCase = true) }
    return when (originalLanguage.lowercase()) {
        "hi" -> INDUSTRY_BOLLYWOOD
        "en" -> INDUSTRY_HOLLYWOOD
        "ta", "te", "ml", "kn" -> INDUSTRY_SOUTH_INDIAN
        "ko" -> INDUSTRY_KOREAN
        "ja" -> if (animated) INDUSTRY_ANIME else INDUSTRY_INTERNATIONAL
        "zh", "cn" -> INDUSTRY_CHINESE
        else -> INDUSTRY_INTERNATIONAL
    }
}

/** Stable, localizable-later industry keys; also used to group filter chips. */
internal const val INDUSTRY_BOLLYWOOD = "bollywood"
internal const val INDUSTRY_HOLLYWOOD = "hollywood"
internal const val INDUSTRY_SOUTH_INDIAN = "south_indian"
internal const val INDUSTRY_KOREAN = "korean"
internal const val INDUSTRY_ANIME = "anime"
internal const val INDUSTRY_CHINESE = "chinese"
internal const val INDUSTRY_INTERNATIONAL = "international"

/** Renders an epoch-milliseconds timestamp as a stable "yyyy-MM-dd" label. */
internal fun Long.toIsoDateLabel(): String {
    val epochDay = (this / MILLIS_PER_DAY).toInt()
    val (year, month, day) = civilFromEpochDay(epochDay)
    return buildString {
        append(year)
        append('-')
        if (month < 10) append('0')
        append(month)
        append('-')
        if (day < 10) append('0')
        append(day)
    }
}

/** Howard Hinnant's civil-from-days algorithm, valid across the whole supported range. */
private fun civilFromEpochDay(epochDay: Int): Triple<Int, Int, Int> {
    val z = epochDay + 719468
    val era = (if (z >= 0) z else z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val year = if (m <= 2) y + 1 else y
    return Triple(year, m, d)
}

private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
