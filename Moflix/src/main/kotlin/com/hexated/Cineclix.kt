package com.hexated

import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.movieApi.MoviesResponse
import com.lagradost.cloudstream3.utils.Browser
import org.json.JSONObject

class Movie4k : Moflix() {
    override var name = "Movie4k"
    override var mainUrl = "https://api.movie4k.sx"
    
    override val mainPage = mainPageOf(
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Action&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Trend Filme",
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Drama&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Drama Filme",
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Comedy&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Komödie Filme"
    )

    override suspend fun getMovies(url: String): MoviesResponse {
        val response = Browser.get("$mainUrl/$url")
        return parseMoviesResponse(response)
    }
    
    private fun parseMoviesResponse(response: String): MoviesResponse {
        val json = JSONObject(response)
        val moviesArray = json.getJSONArray("movies")
        val moviesList = mutableListOf<Movie>()

        for (i in 0 until moviesArray.length()) {
            val movieObj = moviesArray.getJSONObject(i)
            val movie = Movie(
                id = movieObj.getString("_id"),
                title = movieObj.getString("title"),
                year = movieObj.optString("year", "Unbekannt"),
                description = movieObj.optString("description", "Keine Beschreibung verfügbar"),
                imageUrl = movieObj.optString("image_url", ""),
                genres = movieObj.optJSONArray("genres")?.let { 
                    (0 until it.length()).joinToString(", ") { it.getString(it.index) } 
                } ?: ""
            )
            moviesList.add(movie)
        }

        return MoviesResponse(movies = moviesList)
    }
}
