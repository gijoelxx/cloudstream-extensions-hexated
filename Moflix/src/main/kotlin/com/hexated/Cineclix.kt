package com.hexated

import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.* // Stelle sicher, dass du alle notwendigen Utils importierst
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTMDbId
import org.jsoup.Jsoup

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
            this.id.toString(), // Stelle sicher, dass dies ein String ist
            TvType.Movie,
            false
        ) {
            posterUrl = this.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
            this.year = this.year.toString() // Umwandlung in String
            this.rating = this.rating?.toFloat()?.times(10)?.toInt()?.toString() // Umwandlung in String
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
        ) {
            posterUrl = res.poster_path?.let { "https://image.tmdb.org/t/p/w500$it" }
            this.plot = res.description
            this.year = res.year.toString() // Umwandlung in String
            this.rating = res.rating?.toFloat()?.times(10)?.toInt()?.toString() // Umwandlung in String
            addImdbId(res.imdb_id)
            addTMDbId(res.tmdb_id)
            // Angenommen, trailer ist ein String, hier sollte auch ein Referer gegeben werden, wenn erforderlich
            this.addTrailer(res.trailer)
        }
    }

    data class MovieData(
        val id: Int,
        val title: String,
        val poster_path: String?,
        val year: Int,
        val rating: String? // oder Float, je nach API
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
