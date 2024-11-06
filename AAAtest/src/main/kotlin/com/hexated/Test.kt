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
    open var mainAPI = "https://api.movie4k.stream"

    // Kategorien
    private val categories = mapOf(
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=trending" to "Trending",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=Action&country=&cast=&directors=&type=&order_by=trending" to "Action Filme"
    )

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1.inner-page__title")?.text() ?: return null
        val genre = doc.select("ul.inner-page__list li:contains(Genre:) a").map { it.text() }
        val year = doc.selectFirst("ul.inner-page__list li:contains(Jahr:)")?.text()?.toIntOrNull()
        val description = doc.selectFirst("div.inner-page__text.text.clearfix")?.text()

        // Trailer
        val trailerUrl = doc.selectFirst("div#trailer iframe")?.attr("src")

        // Staffel und Episoden
        val seasons = doc.select("ul.seasons li").map { seasonElement ->
            val seasonNumber = seasonElement.attr("id").substringAfter("season_selection_s").toIntOrNull()
            val episodes = seasonElement.select("li[id^=episode_selection]").map { episodeElement ->
                val episodeNumber = episodeElement.attr("id").substringAfter("episode_selection_s${seasonNumber}e").toIntOrNull()
                EpisodeData(episodeNumber, episodeElement.attr("title"))
            }
            SeasonData(seasonNumber, episodes)
        }

        // Streams
        val streams = doc.select("ul.streamInfo li").map { streamElement ->
            val streamId = streamElement.attr("id")
            val streamQuality = streamElement.selectFirst("div.streaming")?.text()
            StreamData(streamId, streamQuality)
        }

        // Empfehlungen
        val recommendations = app.get("$mainAPI/data/related_movies/?lang=2&cat=movie&_id=${url.split("/").last()}").text.let {
            tryParseJson<List<Media>>(it)
        }?.mapNotNull { it.toSearchResponse() }

        return TvSeriesLoadResponse(
            title = title,
            url = url,
            type = TvType.Movie,
            seasonData = seasons,
            streamData = streams,
            genre = genre,
            year = year,
            description = description,
            recommendations = recommendations,
            trailerUrl = trailerUrl
        )
    }

    data class Keywords(@JsonProperty("display_name") val displayName: String?)
    data class Videos(@JsonProperty("category") val category: String?, @JsonProperty("src") val src: String?, @JsonProperty("quality") val quality: String?)
    data class MediaResponse(@JsonProperty("movies") val movies: List<Data>? = listOf())

    private fun Data.toSearchResponse(): SearchResponse? {
        val type = getType(isSeries)
        return newTvSeriesSearchResponse(name ?: "", "$mainUrl/title/$id", type) {
            posterUrl = getImageUrl(poster)
        }
    }
    
    data class SeasonData(val seasonNumber: Int?, val episodes: List<EpisodeData>)
    data class EpisodeData(val episodeNumber: Int?, val title: String)
    data class StreamData(val streamId: String, val quality: String?)
}
