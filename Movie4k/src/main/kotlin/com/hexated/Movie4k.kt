package com.hexated

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor

open class Movie4k : MainAPI() {
    override var name = "Movie4k"
    override var mainUrl = "https://movie4k.stream"
    override var lang = "de"
    override val hasQuickSearch = true
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    open var mainAPI = "https://api.movie4k.stream"

    override val mainPage = mainPageOf(
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=trending" to "Trending",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Action&country=&cast=&directors=&type=&order_by=trending"  to "Action Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Kom%C3%B6die&country=&cast=&directors=&type=&order_by=trending"  to "Komödien Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Dokumentation&country=&cast=&directors=&type=&order_by=trending"  to "Dokumentations Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Drama&country=&cast=&directors=&type=&order_by=trending"  to "Drama Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Horror&country=&cast=&directors=&type=&order_by=trending"  to "Horror Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Romantik&country=&cast=&directors=&type=&order_by=trending"  to "Romantik Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Sci-Fi&country=&cast=&directors=&type=&order_by=trending"  to "Sci-Fi Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Thriller&country=&cast=&directors=&type=&order_by=trending"  to "Thriller Filme",
 
    )

    private fun getImageUrl(link: String?): String? {
        if (link == null) return null
        return if (link.startsWith("/")) "https://image.tmdb.org/t/p/w500/$link" else link
    }

    private fun getBackupImageUrl(link: String?): String? {
        if (link == null) return null
        return "https://cdn.movie4k.stream/data${link.substringAfter("/data")}"
    }

override suspend fun getMainPage(
    page: Int,
    request: MainPageRequest
): HomePageResponse {
    val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "X-Requested-With" to "XMLHttpRequest",
        "Referer" to "$mainUrl/"
    )
    
    val home = app.get("$mainAPI/${request.data}&page=$page", headers = headers)
        .parsedSafe<MediaResponse>()?.movies?.mapNotNull { res ->
            res.toSearchResponse()
        } ?: throw ErrorLoadingException()
    
    return newHomePageResponse(request.name, home)
}


    private fun Media.toSearchResponse(): SearchResponse? {
        return newAnimeSearchResponse(
            title ?: original_title ?: return null,
//            Data(_id).toJson(),
            Link(id=_id).toJson(),
            TvType.TvSeries,
            false
        ) {
            this.posterUrl = getImageUrl(poster_path ?: backdrop_path) ?: getBackupImageUrl(img)
            addDub(last_updated_epi?.toIntOrNull())
            addSub(totalEpisodes?.toIntOrNull())
        }
    }

    
    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
    val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "X-Requested-With" to "XMLHttpRequest",
        "Referer" to "$mainUrl/"
    )
    
    val res = app.get("$mainAPI/data/search/?lang=2&keyword=$query", headers = headers).text
    return tryParseJson<ArrayList<Media>>(res)?.mapNotNull {
        it.toSearchResponse()
    } ?: throw ErrorLoadingException()
    }

override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title = document.selectFirst("h1.inner-page__title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.inner-page__img img")?.attr("src"))
        val tags = document.select("ul.inner-page__list li:contains(Genre:) a").map { it.text() }
        val year = document.selectFirst("ul.inner-page__list li:contains(Jahr:)")?.ownText()
            ?.toIntOrNull()
        val description = document.select("div.inner-page__text.text.clearfix").text()
        val duration = document.selectFirst("ul.inner-page__list li:contains(Zeit:)")?.ownText()
            ?.filter { it.isDigit() }?.toIntOrNull()
        val actors = document.selectFirst("ul.inner-page__list li:contains(Darsteller:)")?.ownText()
            ?.split(",")?.map { it.trim() }
        val recommendations = document.select("div.section__content.section__items div.movie-item")
            .mapNotNull { it.toSearchResult() }
        val type = if (document.select("ul.series-select-menu.ep-menu")
                .isNullOrEmpty()
        ) TvType.Movie else TvType.TvSeries
        val trailer = document.selectFirst("div.stretch-free-width.mirrors span:contains(Trailer)")
            ?.attr("data-link") ?: document.selectFirst("div#trailer iframe")?.attr("src")

        if (type == TvType.Movie) {
            val link = document.select("div.stretch-free-width.mirrors span").map {
                fixUrl(it.attr("data-link"))
            }
            return newMovieLoadResponse(title, url, TvType.Movie, Links(link).toJson()) {
                this.posterUrl = poster
                this.year = year
                plot = description
                this.tags = tags
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
            }
        } else {
            val episodes = document.select("ul.series-select-menu.ep-menu li[id*=serie]").map {
                val name = it.selectFirst("a")?.text()
                val link = it.select("li").map { eps ->
                    fixUrl(eps.select("a").attr("data-link"))
                }
                Episode(
                    Links(link).toJson(),
                    name
                )
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes = episodes) {
                this.posterUrl = poster
                this.year = year
                plot = description
                this.tags = tags
                this.duration = duration
                this.recommendations = recommendations
                addActors(actors)
                addTrailer(trailer)
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
        loadData.apmap {
            val link = fixUrlNull(it.link) ?: return@apmap null
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
        @JsonProperty("genres") val genres: String? = null,
        @JsonProperty("storyline") val storyline: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("streams") val streams: ArrayList<Streams>? = arrayListOf(),
    )

    data class Media(
        @JsonProperty("_id") val _id: String? = null,
        @JsonProperty("original_title") val original_title: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("poster_path") val poster_path: String? = null,
        @JsonProperty("backdrop_path") val backdrop_path: String? = null,
        @JsonProperty("img") val img: String? = null,
        @JsonProperty("imdb_id") val imdb_id: String? = null,
        @JsonProperty("totalEpisodes") val totalEpisodes: String? = null,
        @JsonProperty("last_updated_epi") val last_updated_epi: String? = null,
    )

    data class MediaResponse(
        @JsonProperty("movies") val movies: ArrayList<Media>? = arrayListOf(),
    )

}
