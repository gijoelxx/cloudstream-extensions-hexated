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
import kotlin.math.roundToInt

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
        val res = app.get("$mainAPI/data/search/?lang=2&keyword=$query", referer = "$mainUrl/").text
        return tryParseJson<ArrayList<Media>>(res)?.mapNotNull {
            it.toSearchResponse()
        } ?: throw ErrorLoadingException()
    }

    override suspend fun load(url: String): LoadResponse? {
    // Hole die Medieninformationen über den Endpunkt (wie im Beispiel)
    val res = app.get("$mainAPI/data/watch/?_id=${url.fixId()}", referer = "$mainUrl/")
        .parsedSafe<MediaDetail>() ?: throw ErrorLoadingException()

    val uri = Jsoup.parse(res.seo.toString()).selectFirst("link[rel=canonical]")?.attr("href")
    val id = res.title?.id
    val title = res.title?.name ?: ""
    val poster = res.title?.poster
    val backdrop = res.title?.backdrop
    val tags = res.title?.keywords?.mapNotNull { it.displayName }
    val year = res.title?.year
    val isSeries = res.title?.isSeries
    val certification = res.title?.certification
    val duration = res.title?.runtime
    val type = getType(isSeries)
    val description = res.title?.description
    val trailers = res.title?.videos?.filter { it.category.equals("trailer", true) }?.mapNotNull { it.src }
    val rating = "${res.title?.rating}".toRatingInt()
    val actors = res.credits?.actors?.mapNotNull {
        ActorData(Actor(it.name ?: return@mapNotNull null, it.poster), roleString = it.pivot?.character)
    }
    val recommendations = app.get("$mainAPI/data/related_movies/?_id=$id&lang=2", referer = "$mainUrl/")
        .parsedSafe<Responses>()?.titles?.mapNotNull { it.toSearchResponse() }

    return if (type == TvType.TvSeries) {
        // Abruf der Episoden pro Staffel bei Serien
        val episodes = res.seasons?.data?.mapNotNull { season ->
            app.get("$mainAPI/data/season/?_id=${res.title?.id}&season=${season.number}", referer = "$mainUrl/")
                .parsedSafe<Responses>()?.episodes?.data?.map { episode ->
                    val status = if (episode.status.equals("upcoming", true)) " • [UPCOMING]" else ""
                    Episode(
                        LoadData(id, episode.seasonNumber, episode.episodeNumber, isSeries).toJson(),
                        episode.name + status,
                        episode.seasonNumber,
                        episode.episodeNumber,
                        episode.poster,
                        episode.rating?.times(10)?.roundToInt(),
                        episode.description
                    ).apply {
                        this.addDate(episode.releaseDate?.substringBefore("T"))
                    }
                }
        }?.flatten() ?: emptyList()

        // Rückgabe der Serieninformationen
        newTvSeriesLoadResponse(title, uri ?: url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backdrop
            this.year = year
            this.showStatus = getStatus(res.title?.status)
            this.plot = description
            this.tags = tags
            this.rating = rating
            this.actors = actors
            this.duration = duration
            this.recommendations = recommendations
            this.contentRating = certification
            addTrailer(trailers)
            addImdbId(res.title?.imdbId)
            addTMDbId(res.title?.tmdbId)
        }
    } else {
        // Abruf der Links für Filme
        val urls = res.title?.videos?.filter { it.category.equals("full", true) }
        
        newMovieLoadResponse(
            title,
            uri ?: url,
            TvType.Movie,
            LoadData(isSeries = isSeries, urls = urls)
        ) {
            this.posterUrl = poster
            this.backgroundPosterUrl = backdrop
            this.year = year
            this.comingSoon = res.title?.status.equals("upcoming", true)
            this.plot = description
            this.tags = tags
            this.rating = rating
            this.actors = actors
            this.duration = duration
            this.recommendations = recommendations
            this.contentRating = certification
            addTrailer(trailers)
            addImdbId(res.title?.imdbId)
            addTMDbId(res.title?.tmdbId)
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
