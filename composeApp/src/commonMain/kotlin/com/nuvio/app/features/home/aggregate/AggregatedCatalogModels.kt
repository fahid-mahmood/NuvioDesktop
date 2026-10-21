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
) {
    val sourceCount: Int
        get() = sources.size
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
