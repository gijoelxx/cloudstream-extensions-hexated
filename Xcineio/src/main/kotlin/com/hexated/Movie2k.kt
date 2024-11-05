package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

open class Movie2k : MainAPI() {
    override var name = "Movie2k"
    override var mainUrl = "https://www2.movie2k.ch"
    override var lang = "de"
    override val hasQuickSearch = true
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    open var mainAPI = "https://api.movie2k.ch"

    override val mainPage = mainPageOf(
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=trending" to "Trending",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=Views" to "Most Viewed Movies",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=tvseries&order_by=Trending" to "Trending Series",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=Updates" to "Updated Movies",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=tvseries&order_by=Updates" to "Updated Series",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Action&country=&cast=&directors=&type=&order_by=trending" to "Action Movies",
        // ... add other genres as needed
    )

    private fun getImageUrl(link: String?): String? {
        return if (link == null) null
        else if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    private fun getBackupImageUrl(link: String?): String? {
        return if (link == null) null
        else "https://cdn.movie4k.stream/data${link.substringAfter("/data")}"
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val home = app.get("$mainAPI/${request.data}&page=$page", referer = "$mainUrl/")
            .parsedSafe<MediaResponse>()?.movies?.mapNotNull { res ->
                res.toSearchResponse()
            } ?: throw ErrorLoadingException()
        return newHomePageResponse(request.name, home)
    }

    private fun Media.toSearchResponse(): SearchResponse? {
        return MovieSearchResponse(
            id = _id?.toIntOrNull(),
            name = title ?: original_title ?: return null,
            url = "$mainUrl/browse?c=movie&m=filter&keyword=$name", // Adjusted URL
            posterUrl = getImageUrl(poster_path ?: backdrop_path) ?: getBackupImageUrl(img),
            apiName = "Movie2K"
        )
    }
    data class MovieSearchResponse(
        override var id: Int? = null,
        override var name: String,
        override var apiName: String? = "Movie2K",
        override var url: String,
        override var posterUrl: String? = null
    ) : SearchResponse()

    suspend fun search(query: String): List<SearchResponse> {
        val res = app.get("$mainUrl/browse?c=movie&m=filter&keyword=$query", referer = mainUrl).text
        val document = Jsoup.parse(res)
        val results = document.select(".lister-item")

        return results.mapNotNull { element ->
            try {
                val titleElement = element.selectFirst(".lister-item-header a")
                val title = titleElement?.text() ?: return@mapNotNull null
                val url = titleElement?.attr("href")?.let { "$mainUrl$it" } ?: return@mapNotNull null

                val posterElement = element.selectFirst(".lister-item-image img")
                val posterUrl = posterElement?.attr("src")

                // Create and return the MovieSearchResponse
                MovieSearchResponse(
                    name = title,
                    url = url,
                    posterUrl = posterUrl ?: "",
                    apiName = "Movie2K"
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    override suspend fun load(url: String): LoadResponse? {
        val id = parseJson<Link>(url).id

        val res = app.get("$mainAPI/data/watch/?_id=$id", referer = "$mainUrl/")
            .parsedSafe<MediaDetail>() ?: throw ErrorLoadingException()
        val type = if (res.tv == 1) "tv" else "movie"

        val recommendations = app.get("$mainAPI/data/related_movies/?lang=2&cat=$type&_id=$id&server=0").text.let {
            tryParseJson<List<Media>>(it)
        }?.mapNotNull {
            it.toSearchResponse()
        }

        return if (type == "tv") {
            val episodes = res.streams?.groupBy { it.e.toString().toIntOrNull() }?.mapNotNull { eps ->
                val epsNum = eps.key
                val epsLink = eps.value.map { it.stream }.toJson()
                Episode(epsLink, episode = epsNum)
            } ?: emptyList()
            newTvSeriesLoadResponse(
                res.title ?: res.original_title ?: return null,
                url,
                TvType.TvSeries,
                episodes
            ) {
                this.posterUrl = getImageUrl(res.backdrop_path ?: res.poster_path)
                this.year = res.year
                this.plot = res.storyline ?: res.overview
                this.tags = listOf(res.genres ?: "")
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(
                res.original_title ?: res.title ?: return null,
                url,
                TvType.Movie,
                res.streams?.map { Link(it.stream) }?.toJson()
            ) {
                this.posterUrl = getImageUrl(res.backdrop_path ?: res.poster_path)
                this.year = res.year
                this.plot = res.storyline ?: res.overview
                this.tags = listOf(res.genres ?: "")
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<List<Link>>(data)
        loadData.forEach {
            val link = fixUrlNull(it.link) ?: return@forEach
            if (link.startsWith("https://dl.streamcloud")) {
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        link,
                        "",
                        Qualities.Unknown.value
                    )
                )
            } else {
                loadExtractor(
                    link,
                    "$mainUrl/",
                    subtitleCallback,
                    callback
                )
            }
        }
        return true
    }

    data class Link(
        val link: String? = null,
        val id: String? = null,
    )

    data class Season(
        @JsonProperty("_id") val _id: String? = null,
        @JsonProperty("s") val s: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
    )

    data class Streams(
        @JsonProperty("_id") val _id: String? = null,
        @JsonProperty("stream") val stream: String? = null,
        @JsonProperty("e") val e: Any? = null,
        @JsonProperty("e_title") val e_title: String? = null,
    )

    data class MediaDetail(
        @JsonProperty("_id") val _id: String? = null,
        @JsonProperty("tv") val tv: Int? = null,
        @JsonProperty("original_title") val original_title: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster_path") val poster_path: String? = null,
        @JsonProperty("backdrop_path") val backdrop_path: String? = null,
        @JsonProperty("imdb_id") val imdb_id: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("rating") val rating: String? = null,
        @JsonProperty("storyline") val storyline: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("genres") val genres: String? = null,
        @JsonProperty("streams") val streams: List<Streams>? = null
    )

    data class MediaResponse(
        @JsonProperty("movies") val movies: List<Media> = emptyList()
    )

    data class Media(
        @JsonProperty("_id") val _id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("original_title") val original_title: String? = null,
        @JsonProperty("img") val img: String? = null,
        @JsonProperty("poster_path") val poster_path: String? = null,
        @JsonProperty("backdrop_path") val backdrop_path: String? = null,
        @JsonProperty("year") val year: Int? = null
    ) {
        fun toSearchResponse(): SearchResponse? {
            return MovieSearchResponse(
                id = _id?.toIntOrNull(),
                name = title ?: original_title ?: return null,
                url = "$mainUrl/browse?c=movie&m=filter&keyword=$name",
                posterUrl = getImageUrl(poster_path ?: backdrop_path) ?: getBackupImageUrl(img),
                apiName = "Movie2K"
            )
        }
    }
}
