package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import org.jsoup.Jsoup

class Cineclix : Moflix() {
    override var name = "CineClix"
    override var mainUrl = "https://api.movie4k.sx"

    override val mainPage = mainPageOf(
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Action&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Trend Filme",
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Drama&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Drama Filme",
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Comedy&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Komödie Filme"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("$mainUrl/browse/?lang=2&order_by=trending&page=$page&limit=100")
            .parsedSafe<ResponseData>()

        val movies = response?.movies ?: emptyList()

        val home = movies.mapNotNull { movie ->
            newMovieSearchResponse(
                movie.title,
                movie._id,
                TvType.Movie,
                false
            ) {
                posterUrl = movie.poster_path
                this.year = movie.year?.toString() // Sicherstellen, dass year als String übergeben wird
                this.rating = movie.rating?.toDoubleOrNull() // Umwandeln von rating in Double
            }
        }

        return newHomePageResponse(request.name, home)
    }

    data class ResponseData(
        @JsonProperty("movies") val movies: List<MovieData>? = listOf()
    )

    data class MovieData(
        @JsonProperty("_id") val _id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("year") val year: Int?, // Jahr bleibt als Int
        @JsonProperty("rating") val rating: String?, // Rating als String
        @JsonProperty("poster_path") val poster_path: String
    )
}
