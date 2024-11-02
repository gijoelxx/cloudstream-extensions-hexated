package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.mainPageOf

class Movie4k : Moflix() {
    override var name = "Movie4k"
    override var mainUrl = "https://api.movie4k.sx"
    
    override val mainPage = mainPageOf(
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Action&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Trend Filme",
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Drama&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Drama Filme",
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Comedy&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Komödie Filme"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val genre = request.data.split("/").first()
        val response = app.get("$mainUrl/browse/?lang=2&genre=$genre&page=$page&limit=100")
        val parsedResponse = response.parsedSafe<Movie4kResponse>()
        val movies = parsedResponse?.movies?.mapNotNull { it.toSearchResponse() } ?: emptyList()
        return newHomePageResponse(request.name, movies)
    }

    private fun Movie.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(
            this.title ?: return null,
            this.id.toString(),
            TvType.Movie,
            false
        ) {
            posterUrl = this@toSearchResponse.poster
        }
    }

    // Weitere Implementierungen für load, search, quickSearch usw. wie in Moflix
}

// Definiere die Datenklasse für die API-Antwort
data class Movie4kResponse(
    @JsonProperty("movies") val movies: List<Movie>
)

data class Movie(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("poster") val poster: String? = null,
    @JsonProperty("backdrop") val backdrop: String? = null,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("year") val year: Int? = null,
    @JsonProperty("rating") val rating: Float? = null,
)
