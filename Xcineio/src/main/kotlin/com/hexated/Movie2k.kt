package com.hexated
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element


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
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=Views" to "Most View Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=tvseries&order_by=Trending" to "Trending Serien",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=Updates" to "Updated Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=tvseries&order_by=Updates" to "Updated Serien",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Action&country=&cast=&directors=&type=&order_by=trending"  to "Action Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Animation&country=&cast=&directors=&type=&order_by=trending"  to "Animations Filme",
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
        val home =
            app.get("$mainAPI/${request.data}&page=$page", referer = "$mainUrl/")
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
    val document = app.get("$mainUrl/search/$query").document

    // Extrahieren der Filme und Serien aus den Suchergebnissen
    val results = document.select("div.result-item").mapNotNull { item ->
        val titleElement = item.selectFirst("div.title > a")
        val title = titleElement?.text() ?: return@mapNotNull null
        val href = getProperLink(titleElement.attr("href"))
        val posterUrl = item.selectFirst("img")?.attr("src") ?: return@mapNotNull null
        val type = item.selectFirst("div.type")?.text()?.trim() // Bestimmung des Typs

        // Erstellen einer Suchantwort basierend auf dem Typ
        when (type) {
            "Movie" -> {
                Media(title = title, _id = href).toSearchResponse(TvType.Movie, posterUrl)
            }
            "TV Series" -> {
                Media(title = title, _id = href).toSearchResponse(TvType.TvSeries, posterUrl)
            }
            else -> null // Für nicht unterstützte Typen
        }
    }

    return results // Rückgabe der kombinierten Ergebnisse
}

private fun Media.toSearchResponse(tvType: TvType, posterUrl: String): SearchResponse {
    return newMovieSearchResponse(this.title, this._id, tvType) {
        this.posterUrl = posterUrl
    }
}

    override suspend fun load(url: String): LoadResponse? {
        val id = parseJson<Link>(url).id

        val res = app.get("$mainAPI/data/watch/?_id=$id", referer = "$mainUrl/")
            .parsedSafe<MediaDetail>() ?: throw ErrorLoadingException()
        val type = if (res.tv == 1) "tv" else "movie"

        val recommendations =
            app.get("$mainAPI/data/related_movies/?lang=2&cat=$type&_id=$id&server=0").text.let {
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
