package com.nuvio.app.features.home.aggregate

import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TitleNormalizerTest {

    @Test
    fun normalizesPunctuationAndCase() {
        assertEquals("dune part two", normalizeTitleForMatch("Dune: Part Two!"))
        assertEquals("3 idiots", normalizeTitleForMatch("3  Idiots"))
    }

    @Test
    fun foldsDiacritics() {
        assertEquals("amour", normalizeTitleForMatch("Amour"))
        assertEquals("resume", normalizeTitleForMatch("Résumé"))
    }

    @Test
    fun extractsImdbIdsFromVariousIdShapes() {
        assertEquals("tt1187043", extractImdbId("tt1187043"))
        assertEquals("tt1187043", extractImdbId("cnc:tt1187043"))
        assertEquals("tt0980982", extractImdbId("bridge:tt0980982:other"))
        assertNull(extractImdbId("vega-4k-slug-hd"))
    }

    @Test
    fun extractsYearsFromReleaseInfoShapes() {
        assertEquals(2024, extractReleaseYear("2024", null))
        assertEquals(2019, extractReleaseInfoAndAssert("2019–2024"))
        assertEquals(2009, extractReleaseYear("2009", null))
        assertEquals(2026, extractReleaseYear(null, "2026-10-05"))
        assertNull(extractReleaseYear("N/A", "unknown"))
    }

    private fun extractReleaseInfoAndAssert(info: String): Int? = extractReleaseYear(info, null)

    @Test
    fun extractsParenthesisedYearsFromTitles() {
        assertEquals(2022, yearFromTitle("KGF Chapter 2 (2022)"))
        assertEquals(2026, yearFromTitle("Some Movie (2026) 1080p"))
        assertNull(yearFromTitle("Moana 2"))
        assertNull(yearFromTitle("Sequel (3050)"))
    }

    @Test
    fun sourceSuffixesDoNotSplitMatchKeys() {
        assertEquals(normalizeTitleForMatch("Animals (2026) Netflix"), normalizeTitleForMatch("Animals (2026)"))
        assertEquals(normalizeTitleForMatch("Awarapan 2 (2026) AM"), normalizeTitleForMatch("Awarapan 2"))
        assertEquals(normalizeTitleForMatch("Movie Hindi DD5 1 1080p"), normalizeTitleForMatch("Movie"))
        // Sequel numbers and real title words are never stripped.
        assertEquals("awarapan 2", normalizeTitleForMatch("Awarapan 2"))
        assertEquals("hindi medium", normalizeTitleForMatch("Hindi Medium"))
        assertEquals("moana 2", normalizeTitleForMatch("Moana 2 1080p"))
    }

    @Test
    fun adultMarkersAreDetectedAndStripped() {
        assertEquals(true, isAdultTitle("[18+] Blue (2026)"))
        assertEquals(true, isAdultTitle("(18+) Friends Sex and Games"))
        assertEquals(false, isAdultTitle("Awarapan 2 (2026)"))
        assertEquals("Blue (2026)", cleanDisplayTitle("[18+] Blue (2026)"))
        assertEquals("Awarapan 2", titleForSearch("Awarapan 2 (2026) AM"))
        assertEquals("Blue", titleForSearch("[18+] Blue (2026) Primewire"))
        assertEquals("Blue (2026)", displayTitle("[18+] Blue (2026) Primewire"))
        assertEquals("Animals (2026)", displayTitle("Animals (2026) Netflix"))
    }

    @Test
    fun fullReleaseNamesNormalizeToTheCleanTitle() {
        // Real shapes from the CNCVerse bridge feeds.
        assertEquals(
            "animals",
            normalizeTitleForMatch("Animals Netflix Dual Audio Hindi English WEB-DL 480p 355MB 720p 970MB 1080p 2.5GB (2026)"),
        )
        assertEquals(
            "blue",
            normalizeTitleForMatch("Blue PrimeVideo Dual Audio Hindi Italian WEB-DL 480p 315MB 720p 950MB 1080p 1.8GB (2026)"),
        )
        assertEquals(
            "friends sex and games",
            normalizeTitleForMatch("Friends Sex and Games Season 1 PrimeVideo Dual Audio Hindi German 480p HD 720p 1080p WEB-DL (2026)"),
        )
        assertEquals(
            "mastram",
            normalizeTitleForMatch("Mastram Season 1 Hindi Complete MX Player Web Series 480p 90MB 720p 250MB"),
        )
        // Ordinary titles stay untouched.
        assertEquals("hindi medium", normalizeTitleForMatch("Hindi Medium"))
        assertEquals("movie 43", normalizeTitleForMatch("Movie 43"))
        assertEquals("attack part 1 is fine", normalizeTitleForMatch("Attack Part 1 is fine"))
    }

    @Test
    fun matchKeyPrefersImdbId() {
        val item = preview(id = "tt1187043", name = "3 Idiots", type = "movie")
        assertEquals("imdb:tt1187043", aggregateMatchKey(item))
    }

    @Test
    fun matchKeyFallsBackToNormalizedTitleAndType() {
        val item = preview(id = "vega-3-idiots-hd", name = "3 Idiots", type = "movie", releaseInfo = "2009")
        assertEquals("t:3 idiots|movie|2009", aggregateMatchKey(item))
    }

    @Test
    fun matchKeyWithoutYearStillStable() {
        val item = preview(id = "hdhub-slug", name = "Dune: Part Two", type = "movie", releaseInfo = null)
        assertEquals("t:dune part two|movie", aggregateMatchKey(item))
    }

    private fun preview(
        id: String,
        name: String,
        type: String,
        releaseInfo: String? = null,
    ): MetaPreview = MetaPreview(
        id = id,
        type = type,
        name = name,
        releaseInfo = releaseInfo,
        posterShape = PosterShape.Poster,
    )
}
