package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.Jsoup
import kotlin.math.roundToInt
class Cineclix : Moflix() {
    override var name = "CineClix"
    override var mainUrl = "https://api.movie4k.sx"

    override val mainPage = mainPageOf(
        "browse/?lang=de&genre=Action&page=1&limit=100" to "Action Filme",
        "browse/?lang=de&genre=Drama&page=1&limit=100" to "Drama Filme",
        "browse/?lang=de&genre=Comedy&page=1&limit=100" to "Komödie Filme"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val home = app.get("$mainUrl/browse?page=$page&limit=100")
            .parsedSafe<Responses>()?.movies?.mapNotNull { it.toSearchResponse() } ?: emptyList()
        return newHomePageResponse(request.name, home)
    }

    private fun MovieData.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(
            this.title ?: return null,
            this.id.toString(), // Umwandlung in String
            TvType.Movie,
            false
        ).apply {
            posterUrl = this@toSearchResponse.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" } // Korrigierte Referenz
            year = this@toSearchResponse.year.toString() // Korrigierte Referenz und Umwandlung
            rating = this@toSearchResponse.rating?.toFloat()?.times(10)?.toInt()?.toString() // Korrigierte Referenz
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val movieId = url.toIntOrNull() ?: return LoadResponse.Empty
        val res = app.get("$mainUrl/movie/$movieId")
            .parsedSafe<MovieResponse>() ?: return LoadResponse.Empty

        return newMovieLoadResponse(
            res.title ?: "Unbekannt",
            url,
            TvType.Movie
        ).apply {
            posterUrl = res.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" } // Korrigierte Referenz
            plot = res.description
            year = res.year.toString() // Umwandlung in String
            rating = res.rating?.toFloat()?.times(10)?.toInt()?.toString() // Umwandlung in String
            addImdbId(res.imdb_id)
            addTMDbId(res.tmdb_id)
            // Trailer hinzufügen, falls vorhanden
            res.trailer?.let { addTrailer(it) }
        }
    }

    data class MovieData(
        val id: Int,
        val title: String,
        val poster_path: String?,
        val year: Int,
        val rating: String? // or Float, depending on the API
    )

    data class Responses(
        val movies: List<MovieData>
    )

    data class MovieResponse(
        val title: String,
        val poster_path: String?,
        val year: Int,
        val description: String,
        val rating: String?,
        val imdb_id: String?,
        val tmdb_id: String?,
        val trailer: String?
    )
}
