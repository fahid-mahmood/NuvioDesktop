package com.nuvio.app.features.home.aggregate

import com.nuvio.app.features.home.MetaPreview

private val imdbIdRegex = Regex("""\b(tt\d{5,10})\b""")
private val yearRegex = Regex("""\b(19|20)\d{2}\b""")
private val titleYearRegex = Regex("""\(((?:19|20)\d{2})\)""")
private val parenthesisedYearRegex = Regex("""\s*\(((?:19|20)\d{2})\)""")
private val adultPrefixRegex = Regex("""^\s*[\[\(]?\s*18\+\s*[\]\)]?\s*[-–:]?\s*""", RegexOption.IGNORE_CASE)
private val nonAlphanumericRegex = Regex("""[^a-z0-9]+""")
private val combiningAccentRegex = Regex("""[\u0300-\u036f]""")
private val collapsedWhitespaceRegex = Regex("""\s+""")
private val qualityTokenRegex = Regex(
    """^\d{3,4}p$|^[xh]?26[45]$|^hevc$|^avc?$|^aac\d*\.?\d*$|^\d+\.\d$|^\d+(\.\d+)?(mb|gb|kb)$|^[se]\d{1,2}$|^\d{1,2}x\d{1,2}$""",
)
private val digitTokenRegex = Regex("""^\d{1,2}$""")
private val sizeTokenRegex = Regex("""^\d+(\.\d+)?(mb|gb|kb)$""")

/**
 * Tokens that mark a title as a release name and never appear in a real title
 * ("Animals Netflix Dual Audio Hindi English WEB-DL 480p 355MB"). When three or
 * more of them appear, they are stripped from anywhere in the string.
 */
private val releaseNoiseTokens = setOf(
    // platforms / sources
    "netflix", "nf", "nfx", "am", "amzn", "amazon", "prime", "primevideo", "hotstar",
    "jiohotstar", "jiocinema", "disney", "disneyplus", "zee5", "zee", "sony", "sonyliv",
    "aha", "sunnxt", "ullu", "hoichoi", "mxplayer", "mx", "mubi", "st", "primewire",
    "vegamovies", "hdhub4u", "4khdhub", "movies4u", "rogmovies", "moviebox", "bollyflix",
    "uhdmovies", "moviesmod", "moviesdrive", "multimovies", "multmovies", "skymovies",
    "filmyzilla", "filmywap", "mp4moviez", "9xmovies", "worldfree4u", "khatrimaza",
    "123movies", "gomovies", "fmovies", "soap2day", "lookmovie", "watchseries",
    // languages / dubs
    "hindi", "english", "tamil", "telugu", "kannada", "malayalam", "marathi", "punjabi",
    "bengali", "urdu", "gujarati", "italian", "german", "french", "spanish", "russian",
    "turkish", "arabic", "thai", "indonesian", "chinese", "japanese", "korean",
    "portuguese", "dual", "audio", "dualaudio", "dub", "dubbed", "org", "orginal",
    "original", "originals", "multi", "subbed", "subs",
    // release formats and codecs
    "web", "webdl", "dl", "webrip", "hdrip", "brrip", "bdrip", "dvdrip", "hdr", "ipx", "ip", "player",
    "hd", "uhd", "sd", "cam", "rip", "4k", "10bit", "8bit", "hevc", "avc", "x264", "x265",
    "h264", "h265", "aac", "ac3", "eac3", "dts", "dd", "ddp", "dd2", "dd5", "ddp2", "ddp5",
    "blu", "ray", "bluray", "complete", "sealed", "added",
)

/** Softer tags stripped only from the end so real title words are never touched. */
private val trailingOnlyNoiseTokens = setOf(
    "movie", "film", "full", "uncut", "unrated", "proper", "repack", "extended",
    "remastered", "imax", "season", "series", "part",
)

/** Audio tokens that legitimize stripping a trailing bare number ("DD5 1"). */
private val audioTokens = setOf(
    "dd", "ddp", "dd2", "dd5", "ddp2", "ddp5", "ac3", "eac3", "dts", "aac", "audio",
    "dual", "dualaudio",
)

/** True when a source marks the title as adult, e.g. "[18+] Blue (2026)". */
fun isAdultTitle(title: String): Boolean =
    adultPrefixRegex.containsMatchIn(title.trim().take(12))

/** Display name without the adult marker prefix, e.g. "Blue (2026)". */
fun cleanDisplayTitle(title: String): String =
    title.trim().replaceFirst(adultPrefixRegex, "").trim()

/**
 * Best TMDB query: adult marker, parenthesised years, and noise removed.
 * "Awarapan 2 (2026) AM" becomes "Awarapan 2".
 */
fun titleForSearch(title: String): String =
    stripNoise(cleanDisplayTitle(title).replace(parenthesisedYearRegex, " "))

/**
 * Card display name: adult marker removed, trailing source/quality tags removed,
 * parenthesised year kept ("[18+] Blue (2026) Primewire" -> "Blue (2026)").
 */
fun displayTitle(title: String): String =
    stripNoise(cleanDisplayTitle(title))

/**
 * Normalizes a title for cross-addon matching: lowercased, diacritics folded,
 * parenthesised years removed (they live in the match key instead) and release-name
 * noise stripped — so "Animals (2026)" and "Animals Netflix Dual Audio Hindi WEB-DL
 * 480p 355MB (2026)" both normalize to "animals". Ordinary titles only lose
 * trailing tags, keeping "Hindi Medium" and "Movie 43" intact.
 */
fun normalizeTitleForMatch(title: String): String {
    val decomposed = decomposeUnicode(cleanDisplayTitle(title))
    val withoutYears = parenthesisedYearRegex.replace(decomposed.lowercase(), " ")
    val normalized = combiningAccentRegex
        .replace(withoutYears, "")
        .let { nonAlphanumericRegex.replace(it, " ") }
        .trim()
        .replace(collapsedWhitespaceRegex, " ")
    return stripNoise(normalized)
}

private fun isReleaseNoise(token: String): Boolean =
    token in releaseNoiseTokens || qualityTokenRegex.matches(token)

private fun stripNoise(value: String): String {
    val tokens = value.split(' ').filter { it.isNotBlank() }
    if (tokens.isEmpty()) return value
    val lowered = tokens.map { it.lowercase() }
    val aggressive = lowered.count(::isReleaseNoise) >= 3

    val kept = mutableListOf<String>()
    var i = 0
    var previousDroppedWasAudio = false
    while (i < tokens.size) {
        val token = lowered[i]
        val next = lowered.getOrNull(i + 1)
        val isSeasonMarker = token == "season" || token == "series" || token == "part"
        val dropToken = aggressive && (isReleaseNoise(token) || isSeasonMarker)
        // Decimal sizes split by punctuation leave a bare digit in front ("2 5gb"),
        // and audio channels lose their tag to the pass above ("DD5" -> "1").
        val isNumericDebris = digitTokenRegex.matches(token) && previousDroppedWasAudio ||
            (digitTokenRegex.matches(token) && next != null && sizeTokenRegex.matches(next))
        if (dropToken || isNumericDebris) {
            previousDroppedWasAudio = token in audioTokens
            // Consume the number glued to a season marker ("Season 1").
            if (dropToken && isSeasonMarker && next != null && digitTokenRegex.matches(next)) {
                i++
            }
            i++
            continue
        }
        previousDroppedWasAudio = false
        kept.add(tokens[i])
        i++
    }

    // Conservative final pass over the surviving tail for soft tags and codec debris.
    val keptLowered = kept.map { it.lowercase() }
    var end = kept.size
    while (end > 1) {
        val token = keptLowered[end - 1]
        val isNoise = isReleaseNoise(token) || token in trailingOnlyNoiseTokens
        val isBareNumberAfterAudioOrSeason = end >= 2 && digitTokenRegex.matches(token) &&
            (keptLowered[end - 2] in audioTokens || keptLowered[end - 2] == "season")
        if (!isNoise && !isBareNumberAfterAudioOrSeason) break
        end--
    }
    return kept.subList(0, end).joinToString(" ")
}

/** Extracts an IMDb id (tt1234567) from an addon item id such as "tt1234567" or "bridge%3Att1234567". */
fun extractImdbId(id: String): String? =
    imdbIdRegex.find(id)?.groupValues?.get(1)?.lowercase()

/** Year embedded in a title, e.g. "KGF Chapter 2 (2022)". Only parenthesised years count
 * to avoid matching sequels like "Moana 2". */
fun yearFromTitle(title: String): Int? {
    val match = titleYearRegex.find(title) ?: return null
    return match.groupValues[1].toIntOrNull()
}

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
    val year = extractReleaseYear(item.releaseInfo, item.rawReleaseDate) ?: yearFromTitle(item.name)
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
