package com.nuvio.app.features.home.aggregate

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.tmdb.TmdbSettingsRepository
import com.nuvio.app.features.tmdb.buildTmdbUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Best-effort TMDB enrichment for aggregated items whose source catalogs expose no
 * release year or genres (typical for scraper bridges). One search call supplies the
 * release year and original language; one detail call supplies genre names. Results
 * are persisted by the caller, so each title is enriched once, ever.
 */
internal object AggregatedTmdbEnricher {
    private val log = Logger.withTag("AggregatedTmdbEnricher")
    private val json = Json { ignoreUnknownKeys = true }
    private val concurrency = Semaphore(6)

    data class Enrichment(
        val tmdbId: Int,
        val mediaType: String,
        val releaseYear: Int?,
        val originalLanguage: String?,
        val genres: List<String>,
    )

    suspend fun enrich(
        title: String,
        type: String,
        yearHint: Int?,
    ): Enrichment? = withContext(Dispatchers.Default) {
        val apiKey = TmdbSettingsRepository.effectiveApiKey().takeIf { it.isNotBlank() } ?: return@withContext null
        val search = concurrency.withPermit {
            runCatching {
                json.decodeFromString<TmdbSearchResponse>(
                    httpGetText(
                        buildTmdbUrl(
                            endpoint = "search/multi",
                            apiKey = apiKey,
                            query = mapOf(
                                "query" to title,
                                "include_adult" to "true",
                                "page" to "1",
                            ),
                        ),
                    ),
                )
            }.getOrElse { error ->
                log.w { "search failed for '$title': ${error.message}" }
                null
            }
        } ?: return@withContext null

        val preferredKind = if (type.equals("series", ignoreCase = true)) "tv" else "movie"
        val match = search.results
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            .maxByOrNull { candidate ->
                (if (candidate.mediaType == preferredKind) 1 else 0) * 1_000_000 +
                    (candidate.popularity?.toInt() ?: 0)
            } ?: return@withContext null

        val detail = concurrency.withPermit {
            runCatching {
                json.decodeFromString<TmdbDetailResponse>(
                    httpGetText(
                        buildTmdbUrl(
                            endpoint = "${match.mediaType}/${match.id}",
                            apiKey = apiKey,
                        ),
                    ),
                )
            }.getOrNull()
        }

        val year = match.releaseDate?.take(4)?.toIntOrNull()
            ?: detail?.releaseDate?.take(4)?.toIntOrNull()
            ?: match.firstAirDate?.take(4)?.toIntOrNull()
            ?: detail?.firstAirDate?.take(4)?.toIntOrNull()
        Enrichment(
            tmdbId = match.id,
            mediaType = match.mediaType,
            releaseYear = year ?: yearHint,
            originalLanguage = detail?.originalLanguage,
            genres = detail?.genres.orEmpty().map { genre -> genre.name }.filter { it.isNotBlank() },
        )
    }
}

@Serializable
private data class TmdbSearchResponse(
    val results: List<TmdbSearchItem> = emptyList(),
)

@Serializable
private data class TmdbSearchItem(
    val id: Int,
    val popularity: Double? = null,
    @SerialName("media_type") val mediaType: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
)

@Serializable
private data class TmdbDetailResponse(
    val genres: List<TmdbGenre> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
)

@Serializable
private data class TmdbGenre(
    val name: String = "",
)
