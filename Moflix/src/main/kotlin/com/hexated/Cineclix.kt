package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.jsoup.Jsoup
import kotlin.math.roundToInt

open class Movie4k : Moflix() {
    override var name = "Movie4k"
    override var mainUrl = "https://api.movie4k.sx"
    override var lang = "de"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    override val mainPage = mainPageOf(
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Action&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Trend Filme",
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Drama&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Drama Filme",
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Comedy&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Komödie Filme"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(
            "$mainUrl/data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Action&country=&cast=&directors=&type=&order_by=trending&page=$page&limit=100",
            referer = "$mainUrl/"
        ).parsedSafe<Responses>()

        if (response != null && response.pager?.totalItems ?: 0 > 0) {
            val movies = response.movies.mapNotNull { movie ->
                newMovieSearchResponse(
                    movie.title,
                    movie._id,
                    TvType.Movie,
                    false
                ) {
                    this.posterUrl = movie.poster_path?.let { "${mainUrl}$it" } // Vollständige URL für Poster
                    this.backgroundPosterUrl = movie.backdrop_path?.let { "${mainUrl}$it" } // Vollständige URL für Hintergrund
                    this.year = movie.year.toString() // Jahr als String
                    this.rating = movie.rating.toFloatOrNull() // Bewertung als Float
                }
            }
            return newHomePageResponse(request.name, movies)
        } else {
            // Fehlerbehandlung: Keine Filme gefunden oder API-Fehler
            return newHomePageResponse(request.name, emptyList())
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        val response = app.get("$mainUrl/data/browse/?lang=2&keyword=$query&year=&rating=&votes=&genre=&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100", referer = "$mainUrl/")
            .parsedSafe<Responses>()

        return response?.movies?.mapNotNull { movie ->
            newMovieSearchResponse(
                movie.title,
                movie._id,
                TvType.Movie,
                false
            ) {
                this.posterUrl = movie.poster_path?.let { "${mainUrl}$it" } // Vollständige URL für Poster
                this.backgroundPosterUrl = movie.backdrop_path?.let { "${mainUrl}$it" } // Vollständige URL für Hintergrund
                this.year = movie.year.toString() // Jahr als String
                this.rating = movie.rating.toFloatOrNull() // Bewertung als Float
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(
            "$mainUrl/data/details/${url.fixId()}",
            referer = "$mainUrl/"
        ).parsedSafe<Responses>()

        // Verarbeiten der Details
        // (Details analog zu vorheriger Implementierung, z.B. mit Jsoup für SEO, etc.)
        // Hier wird vorausgesetzt, dass die Rückgabe die gleichen Datenstruktur hat.

        // Beispielantwort verarbeiten
        return if (res != null) {
            // Annehmen, dass die Antwort gültig ist und die nötigen Daten enthält
            newMovieLoadResponse(
                res.title?.name ?: "",
                url,
                TvType.Movie,
                LoadData(url = res.videos.mapNotNull { it.src })
            ) {
                this.posterUrl = res.title?.poster
                this.backgroundPosterUrl = res.title?.backdrop
                this.year = res.title?.year.toString()
                this.plot = res.title?.description
                this.rating = res.title?.rating?.toFloatOrNull()
                addImdbId(res.title?.imdbId)
                addTMDbId(res.title?.tmdbId)
            }
        } else {
            // Fehlerbehandlung
            throw Exception("Fehler beim Laden der Filmdetails.")
        }
    }

    // Hier die restlichen Methoden wie loadLinks, fixId und Datenklassen

    // ...
}

// Datenklassen, die die API-Antworten repräsentieren
data class Responses(
    @JsonProperty("pager") val pager: Pager? = null,
    @JsonProperty("movies") val movies: List<Movie>? = null,
)

data class Pager(
    @JsonProperty("totalItems") val totalItems: Int = 0,
    @JsonProperty("currentPage") val currentPage: Int = 0,
    @JsonProperty("pageSize") val pageSize: Int = 0,
    @JsonProperty("totalPages") val totalPages: Int = 0,
    @JsonProperty("startIndex") val startIndex: Int = 0,
    @JsonProperty("endIndex") val endIndex: Int = 0,
    @JsonProperty("pages") val pages: List<Int>? = null,
)

data class Movie(
    @JsonProperty("_id") val _id: String,
    @JsonProperty("title") val title: String,
    @JsonProperty("year") val year: Int,
    @JsonProperty("rating") val rating: String,
    @JsonProperty("backdrop_path") val backdrop_path: String?,
    @JsonProperty("poster_path") val poster_path: String?,
)

data class LoadData(
    val urls: List<String>? = listOf(),
)
