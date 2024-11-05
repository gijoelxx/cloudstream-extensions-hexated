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

open class Kinox : MainAPI() {
    override var name = "Kinox"
    override var mainUrl = "https://kinox.to"
    override var lang = "de"
    override val hasMainPage = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

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
        "neueste" to "Neueste Filme",
        "beliebteste" to "Beliebteste Filme",
        "serien" to "Serien",
        "animiert" to "Animierte Filme",
        "kinder" to "Kinder & Familien",
        "komödie" to "Komödien"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val home = app.get("$mainUrl/api/main", referer = mainUrl).parsedSafe<Responses>()?.data ?: emptyList()
        return newHomePageResponse(request.name, home.mapNotNull { it.toSearchResponse() })
    }

    private fun Data.toSearchResponse(): SearchResponse? {
        return newTvSeriesSearchResponse(
            this.name ?: return null,
            "${this.id}",
            getType(this.isSeries),
            false
        ) {
            posterUrl = this@toSearchResponse.poster?.compress()
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun search(query: String): List<SearchResponse>? {
        return app.get("$mainUrl/api/search/$query", referer = mainUrl)
            .parsedSafe<Responses>()?.results?.mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val res = app.get("$mainUrl/api/titles/${url.fixId()}", referer = mainUrl)
            .parsedSafe<Responses>()

        val uri = Jsoup.parse(res?.seo.toString()).selectFirst("link[rel=canonical]")?.attr("href")
        val id = res?.title?.id
        val title = res?.title?.name ?: ""
        val poster = res?.title?.poster
        val backdrop = res?.title?.backdrop
        val tags = res?.title?.keywords?.mapNotNull { it.displayName }
        val year = res?.title?.year
        val isSeries = res?.title?.isSeries
        val description = res?.title?.description
        val trailers = res?.title?.videos?.filter { it.category.equals("trailer", true) }
            ?.mapNotNull { it.src }
        val rating = "${res?.title?.rating}".toRatingInt()
        val actors = res?.credits?.actors?.mapNotNull {
            ActorData(Actor(it.name ?: return@mapNotNull null, it.poster), roleString = it.pivot?.character)
        }
        val recommendations = app.get("$mainUrl/api/titles/$id/related", referer = mainUrl)
            .parsedSafe<Responses>()?.titles?.mapNotNull { it.toSearchResponse() }

        return if (isSeries == true) {
            val episodes = res?.seasons?.data?.flatMap { season ->
                app.get("$mainUrl/api/titles/${res.title?.id}/seasons/${season.number}/episodes", referer = mainUrl)
                    .parsedSafe<Responses>()?.episodes?.data?.map { episode ->
                        Episode(
                            LoadData(id, season.number, episode.episodeNumber, isSeries).toJson(),
                            episode.name,
                            season.number,
                            episode.episodeNumber,
                            episode.poster,
                            episode.rating?.times(10)?.roundToInt(),
                            episode.description
                        ).apply {
                            this.addDate(episode.releaseDate?.substringBefore("T"))
                        }
                    }
            } ?: emptyList()

            newTvSeriesLoadResponse(title, uri ?: url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.showStatus = getStatus(res?.title?.status)
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailers)
                addImdbId(res?.title?.imdbId)
                addTMDbId(res?.title?.tmdbId)
            }
        } else {
            val urls = res?.title?.videos?.filter { it.category.equals("full", true) }
            newMovieLoadResponse(title, uri ?: url, TvType.Movie, LoadData(isSeries = isSeries, urls = urls)) {
                this.posterUrl = poster
                this.backgroundPosterUrl = backdrop
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.actors = actors
                this.recommendations = recommendations
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
            app.get("$mainUrl/api/titles/${json.id}/seasons/${json.season}/episodes/${json.episode}", referer = mainUrl)
                .parsedSafe<Episodes>()?.episode?.videos?.filter { it.category.equals("full", true) }
        } else {
            json.urls
        }

        iframes?.forEach { iframe ->
            loadCustomExtractor(
                iframe.src ?: return@forEach,
                mainUrl,
                subtitleCallback,
                callback,
                iframe.quality?.substringBefore("/")?.filter { it.isDigit() }?.toIntOrNull()
            )
        }

        return true
    }

    private fun String.fixId(): String {
        val chunk = "/titles/"
        return if (this.contains(chunk)) this.substringAfter(chunk).substringBefore("/") else this.substringAfterLast("/")
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
