package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element

class StreamKiste : MainAPI() {
    override var name = "StreamKiste TV"
    override var mainUrl = "https://streamkiste.tv"
    override var lang = "de"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)

    // Menü- und Navigationsstruktur
    override val mainPage = mainPageOf(
        "" to "Startseite",
        "cat/serien" to "Serien",
        "cat/filme" to "Filme",
        "cat/filme/sortby/popular" to "Derzeit Beliebt",
        "cat/filme/sortby/update" to "Letzte Updates",
        "search" to "Suche"
    )

    // Hauptseite für Filme und Serien
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").document
        val home = document.select("div#dle-content div.short").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    // Linkanpassung für Serien und Filme
    private fun getProperLink(uri: String): String {
        return "$mainUrl/$uri"
    }

    // Extrahiert relevante Informationen von der Hauptseite
    private fun Element.toSearchResult(): SearchResponse? {
        val href = this.selectFirst("a")?.attr("href") ?: return null
        val title = this.selectFirst("a")?.text() ?: return null
        val posterUrl = this.selectFirst("img")?.getImageAttr()
        return newTvSeriesSearchResponse(title, getProperLink(href), TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    // Implementierung der Suchfunktion
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/?sq=$query").document
        return document.select("div#dle-content div.titlecontrol").mapNotNull {
            it.toSearchResult()
        }
    }

    // Lädt Detailseite für einen Film oder eine Serie
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1#news-title")?.text() ?: ""
        val poster = document.selectFirst("div.images-border img")?.getImageAttr()
        val description = document.select("div.images-border").text()
        val year = """(\d{4})""".toRegex().find(title)?.groupValues?.get(1)?.toIntOrNull()
        val tags = document.select("li.category a").map { it.text() }

        val recommendations = document.select("ul.ul_related li").mapNotNull {
            it.toSearchResult()
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
            this.posterUrl = poster
            this.year = year
            this.plot = description
            this.tags = tags
            this.recommendations = recommendations
        }
    }

    // Extrahiert die Streaming-Links
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val streamResponse = fetchStreamLinks(data)
        streamResponse?.forEach {
            callback.invoke(
                ExtractorLink(
                    it.url,               // source: Die URL des Streams
                    it.mirror,            // name: Der Name des Mirrors (z. B. "Mirror 1")
                    it.url,               // url: Der tatsächliche Stream-Link
                    "Referer",            // referer: Der Referer-Header (falls erforderlich)
                    Qualities.Unknown.value, // quality: Qualität des Streams, hier als Platzhalter
                    ExtractorLinkType.M3U8,  // type: Der Link-Typ (hier M3U8)
                    emptyMap(),           // headers: Optional, leere Map für Header
                    emptyMap()            // extractorData: Optional, leere Map für Extraktor-Daten
                )
            )
        }
        return true
    }

    // Holt die Streaming-Links von der API
    private suspend fun fetchStreamLinks(data: String): List<Stream>? {
        val response = app.post("https://streamkiste.tv/include/fetch.php", data = mapOf(
            "mirror" to "1",
            "host" to "1",
            "pid" to "58540",
            "ceck" to "sk-stream",
            "season" to "1",
            "episode" to "1"
        )).document

        val streams = response?.select("div#stream-links a")?.mapNotNull {
            val url = it.attr("href")
            val mirror = it.parent()?.select("div#mirror-head")?.text()?.split("|")?.get(0)?.trim() ?: "Mirror 1"
            val date = it.parent()?.select("div#mirror-head")?.text()?.split("|")?.get(1)?.trim() ?: "Unknown"
            Stream(url, mirror, date)
        }
        return streams
    }

    // Hilfsklasse für Stream-Informationen
    data class Stream(
        val url: String,
        val mirror: String,
        val date: String
    )

    // Hilfsfunktion zum Abrufen von Bild-URLs
    private fun Element.getImageAttr(): String? {
        return when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("data-lazy-src") -> this.attr("data-lazy-src")
            this.hasAttr("srcset") -> this.attr("srcset").substringBefore(" ")
            else -> this.attr("src")
        }
    }
}
