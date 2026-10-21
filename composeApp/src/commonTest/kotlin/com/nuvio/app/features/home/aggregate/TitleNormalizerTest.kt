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
