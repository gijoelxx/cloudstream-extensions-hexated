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

open class Test : MainAPI() {
    override var name = "Movie4k"
    override var mainUrl = "https://movie4k.stream"
    override var lang = "de"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    override val mainAPI "https://api.movie4k.stream"

    companion object {
        fun getType(isSeries: Boolean?): TvType {
            return when (isSeries) {
                true -> TvType.TvSeries
                else -> TvType.Movie
            }
        }

        fun getStatus(t: String?): ShowStatus {
            return when (t) {
                "ongoing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

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

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get("$mainUrl/api/v1/search/$query?loader=searchPage", referer = "$mainUrl/")
            .parsedSafe<Responses>()?.results?.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get(
            "$mainUrl/api/v1/titles/${url.fixId()}?loader=titlePage",
            referer = "$mainUrl/"
        ).parsedSafe<Responses>()

        val uri = Jsoup.parse(res?.seo.toString()).selectFirst("link[rel=canonical]")?.attr("href")
        val id = res?.title?.id
        val title = res?.title?.name ?: ""
        val poster = res?.title?.poster
        val backdrop = res?.title?.backdrop
        val tags = res?.title?.keywords?.mapNotNull { it.displayName }
        val year = res?.title?.year
        val isSeries = res?.title?.isSeries
        val certification = res?.title?.certification
        val duration = res?.title?.runtime
        val type = getType(isSeries)
        val description = res?.title?.description
        val trailers = res?.title?.videos?.filter { it.category.equals("trailer", true) }
            ?.mapNotNull { it.src }
        val rating = "${res?.title?.rating}".toRatingInt()
        val actors = res?.credits?.actors?.mapNotNull {
            ActorData(
                Actor(it.name ?: return@mapNotNull null, it.poster),
                roleString = it.pivot?.character
            )
        }
        val recommendations = app.get("$mainUrl/api/v1/titles/$id/related", referer = "$mainUrl/")
            .parsedSafe<Responses>()?.titles?.mapNotNull { it.toSearchResponse() }

        return if (type == TvType.TvSeries) {
            val episodes = res?.seasons?.data?.mapNotNull { season ->
                app.get(
                    "$mainUrl/api/v1/titles/${res.title?.id}/seasons/${season.number}?loader=seasonPage",
                    referer = "$mainUrl/"
                ).parsedSafe<Responses>()?.episodes?.data?.map { episode ->
                    val status =
                        if (episode.status.equals("upcoming", true)) " • [UPCOMING]" else ""
                    Episode(
                        LoadData(
                            id,
                            episode.seasonNumber,
                            episode.episodeNumber,
                            res.title?.isSeries
                        ).toJson(),
                        episode.name + status,
                        episode.seasonNumber,
                        episode.episodeNumber,
                        episode.poster,
                        episode.rating?.times(10)?.roundToInt(),
                        episode.description,
                    ).apply {
                        this.addDate(episode.releaseDate?.substringBefore("T"))
                    }
                }
            }?.flatten() ?: emptyList()
            newTvSeriesLoadResponse(title, uri ?: url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.showStatus = getStatus(res?.title?.status)
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.actors = actors
                this.duration = duration
                this.recommendations = recommendations
                this.contentRating = certification
                addTrailer(trailers)
                addImdbId(res?.title?.imdbId)
                addTMDbId(res?.title?.tmdbId)
            }
        } else {
            val urls = res?.title?.videos?.filter { it.category.equals("full", true) }

            newMovieLoadResponse(
                title,
                uri ?: url,
                TvType.Movie,
                LoadData(isSeries = isSeries, urls = urls)
            ) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.comingSoon = res?.title?.status.equals("upcoming", true)
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.actors = actors
                this.duration = duration
                this.recommendations = recommendations
                this.contentRating = certification
                addTrailer(trailers)
                addImdbId(res?.title?.imdbId)
                addTMDbId(res?.title?.tmdbId)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val json = parseJson<LoadData>(data)

        val iframes = if (json.isSeries == true) {
            app.get(
                "$mainUrl/api/v1/titles/${json.id}/seasons/${json.season}/episodes/${json.episode}?loader=episodePage",
                referer = "$mainUrl/"
            ).parsedSafe<Episodes>()?.episode?.videos?.filter { it.category.equals("full", true) }
        } else {
            json.urls
        }

        iframes?.apmap { iframe ->
            loadCustomExtractor(
                iframe.src ?: return@apmap,
                "$mainUrl/",
                subtitleCallback,
                callback,
                iframe.quality?.substringBefore("/")?.filter { it.isDigit() }?.toIntOrNull()
            )
        }

        return true
    }

    private fun String.fixId(): String {
        val chunk = "/titles/"
        return if (this.contains(chunk)) this.substringAfter(chunk)
            .substringBefore("/") else this.substringAfterLast("/")
    }

    private suspend fun loadCustomExtractor(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            if (link.quality == Qualities.Unknown.value || !link.isM3u8) {
                callback.invoke(
                    ExtractorLink(
                        link.source,
                        link.name,
                        link.url,
                        link.referer,
                        quality ?: link.quality,
                        link.type,
                        link.headers,
                        link.extractorData
                    )
                )
            }
        }
    }

    private fun String.compress(): String {
        return this.replace("/original/", "/w500/")
    }

    data class LoadData(
        val id: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val isSeries: Boolean? = null,
        val urls: List<Videos>? = listOf(),
    )

    data class Responses(
        @JsonProperty("pagination") val pagination: Pagination? = null,
        @JsonProperty("title") val title: Title? = null,
        @JsonProperty("seo") val seo: String? = null,
        @JsonProperty("credits") val credits: Credits? = null,
        @JsonProperty("seasons") val seasons: Seasons? = null,
        @JsonProperty("episodes") val episodes: Episodes? = null,
        @JsonProperty("titles") val titles: ArrayList<Data>? = arrayListOf(),
        @JsonProperty("results") val results: ArrayList<Data>? = arrayListOf(),
    )

    data class Seasons(
        @JsonProperty("data") val data: ArrayList<Data>? = arrayListOf(),
    ) {
        data class Data(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("number") val number: Int? = null,
            @JsonProperty("poster") val poster: String? = null,
            @JsonProperty("release_date") val releaseDate: String? = null,
        )
    }

    data class Episodes(
        @JsonProperty("data") val data: ArrayList<Data>? = arrayListOf(),
        @JsonProperty("episode") val episode: Data? = null,
    ) {
        data class Data(
            @JsonProperty("id") val id: Int? = null,
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("description") val description: String? = null,
            @JsonProperty("season_number") val seasonNumber: Int? = null,
            @JsonProperty("episode_number") val episodeNumber: Int? = null,
            @JsonProperty("rating") val rating: Float? = null,
            @JsonProperty("poster") val poster: String? = null,
            @JsonProperty("release_date") val releaseDate: String? = null,
            @JsonProperty("status") val status: String? = null,
            @JsonProperty("videos") val videos: ArrayList<Videos>? = arrayListOf(),
        )
    }

    data class Pagination(
        @JsonProperty("data") val data: ArrayList<Data>? = arrayListOf(),
    )

    data class Data(
        @JsonProperty("id") val id: Any? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("backdrop") val backdrop: String? = null,
    )

    data class Credits(
        @JsonProperty("actors") val actors: ArrayList<Actors>? = arrayListOf(),
    ) {
        data class Actors(
            @JsonProperty("name") val name: String? = null,
            @JsonProperty("poster") val poster: String? = null,
            @JsonProperty("pivot") val pivot: Pivot? = null,
        ) {
            data class Pivot(
                @JsonProperty("character") val character: String? = null,
            )
        }
    }

    data class Videos(
        @JsonProperty("category") val category: String? = null,
        @JsonProperty("src") val src: String? = null,
        @JsonProperty("quality") val quality: String? = null,
    )

    data class Title(
        @JsonProperty("id") val id: Int? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("release_date") val releaseDate: String? = null,
        @JsonProperty("year") val year: Int? = null,
        @JsonProperty("runtime") val runtime: Int? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("backdrop") val backdrop: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("certification") val certification: String? = null,
        @JsonProperty("rating") val rating: Float? = null,
        @JsonProperty("imdb_id") val imdbId: String? = null,
        @JsonProperty("tmdb_id") val tmdbId: String? = null,
        @JsonProperty("status") val status: String? = null,
        @JsonProperty("is_series") val isSeries: Boolean? = null,
        @JsonProperty("videos") val videos: ArrayList<Videos>? = arrayListOf(),
        @JsonProperty("keywords") val keywords: ArrayList<Keywords>? = arrayListOf(),
    ) {
        data class Keywords(
            @JsonProperty("display_name") val displayName: String? = null,
        )
    }

}
