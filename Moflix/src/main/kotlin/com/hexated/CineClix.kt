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

class Movie4k : CineClix() {
    override var name = "Movie4k"
    override var mainUrl = "https://api.movie4k.sx"

    override val mainPage = mainPageOf(
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Action&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Trend Filme",
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Drama&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Drama Filme",
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Comedy&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Komödie Filme"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get("$mainUrl/browse/?page=$page", referer = "$mainUrl/").parsedSafe<MainPageResponse>()
        
        val home = response?.movies?.mapNotNull { it.toSearchResponse() } ?: emptyList()
        
        return newHomePageResponse(request.name, home)
    }

    private fun Movie.toSearchResponse(): SearchResponse? {
        return newMovieSearchResponse(
            this.title,
            this._id,
            TvType.Movie
        ) {
            posterUrl = this@toSearchResponse.poster_path
            year = this.year
            rating = this.rating.toRatingInt()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val movieId = url.fixId()
        val res = app.get("$mainUrl/movies/$movieId", referer = "$mainUrl/").parsedSafe<MovieDetailResponse>()

        val title = res?.title ?: return LoadResponse.Error("Film nicht gefunden")
        val year = res.year
        val poster = res.poster_path
        val backdrop = res.backdrop_path
        val description = res.description
        val rating = res.rating.toRatingInt()
        
        return newMovieLoadResponse(title, url, TvType.Movie) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backdrop
            this.year = year
            this.plot = description
            this.rating = rating
            addTrailer(res.trailer)
            addImdbId(res.imdb_id)
            addTMDbId(res.tmdb_id)
        }
    }

    private fun String.fixId(): String {
        return this.substringAfterLast("/")
    }

    data class MainPageResponse(
        @JsonProperty("movies") val movies: List<Movie>
    )

    data class Movie(
        @JsonProperty("_id") val _id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("year") val year: Int,
        @JsonProperty("rating") val rating: String,
        @JsonProperty("backdrop_path") val backdrop_path: String?,
        @JsonProperty("poster_path") val poster_path: String?
    )

    data class MovieDetailResponse(
        @JsonProperty("title") val title: String,
        @JsonProperty("year") val year: Int,
        @JsonProperty("rating") val rating: String,
        @JsonProperty("description") val description: String,
        @JsonProperty("trailer") val trailer: String,
        @JsonProperty("imdb_id") val imdb_id: String,
        @JsonProperty("tmdb_id") val tmdb_id: String,
        @JsonProperty("poster_path") val poster_path: String,
        @JsonProperty("backdrop_path") val backdrop_path: String
    )
}
