package com.nuvio.app.features.home.aggregate

import com.nuvio.app.features.home.MetaPreview

private val imdbIdRegex = Regex("""\b(tt\d{5,10})\b""")
private val yearRegex = Regex("""\b(19|20)\d{2}\b""")
private val nonAlphanumericRegex = Regex("""[^a-z0-9]+""")
private val combiningAccentRegex = Regex("""[\u0300-\u036f]""")
private val collapsedWhitespaceRegex = Regex("""\s+""")

/**
 * Normalizes a title for cross-addon matching: lowercased, diacritics folded to ASCII,
 * punctuation removed and whitespace collapsed, e.g. "Dune: Part Two!" -> "dune part two".
 */
fun normalizeTitleForMatch(title: String): String {
    val decomposed = decomposeUnicode(title)
    return combiningAccentRegex
        .replace(decomposed.lowercase(), "")
        .let { nonAlphanumericRegex.replace(it, " ") }
        .trim()
        .replace(collapsedWhitespaceRegex, " ")
}

/** Extracts an IMDb id (tt1234567) from an addon item id such as "tt1234567" or "bridge%3Att1234567". */
fun extractImdbId(id: String): String? =
    imdbIdRegex.find(id)?.groupValues?.get(1)?.lowercase()

/** Best-effort release year from releaseInfo ("2024" / "2024–2025") or an ISO release date. */
fun extractReleaseYear(releaseInfo: String?, rawReleaseDate: String?): Int? {
    rawReleaseDate?.trim()?.take(4)?.toIntOrNull()?.let { year ->
        if (year in 1900..2100) return year
    }
    return releaseInfo?.let { info -> yearRegex.find(info)?.value?.toIntOrNull() }
}

/**
 * Stable cross-addon match key. IMDb ids merge perfectly; scraper catalogs that use
 * site-specific ids fall back to normalized title + type + year (year omitted when
 * a source does not provide one, so the same title still merges).
 */
fun aggregateMatchKey(item: MetaPreview): String {
    extractImdbId(item.id)?.let { imdbId -> return "imdb:$imdbId" }
    val year = extractReleaseYear(item.releaseInfo, item.rawReleaseDate)
    return buildString {
        append("t:")
        append(normalizeTitleForMatch(item.name))
        append('|')
        append(item.type)
        if (year != null) {
            append('|')
            append(year)
        }
    }
}

/** Canonical NFD decomposition so accent marks can be stripped by [normalizeTitleForMatch]. */
internal expect fun decomposeUnicode(value: String): String
