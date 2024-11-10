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
        val home = document.select("div.movie-item").mapNotNull {  // Angepasster Selektor
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
        val title = this.selectFirst("h2")?.text() ?: return null  // Titel aus h2 extrahieren
        val posterUrl = this.selectFirst("img")?.getImageAttr()   // Bild-URL extrahieren
        return newTvSeriesSearchResponse(title, getProperLink(href), TvType.TvSeries) {
            this.posterUrl = posterUrl
        }
    }

    // Implementierung der Suchfunktion
    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search/?sq=$query").document
        return document.select("div.movie-item").mapNotNull {  // Angepasster Selektor
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

override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val streamResponse = fetchStreamLinks(data)
    streamResponse?.forEach {
        callback.invoke(
            // Erstellt eine Instanz von ExtractorLink
            ExtractorLink(
                source = it.url,  // Stream-URL
                name = it.mirror,  // Mirror-Name
                url = it.url,  // URL zum Stream
                referer = "Referer",  // (Kann je nach Bedarf angepasst werden)
                quality = Qualities.Unknown.value,  // Qualität des Streams (vorerst auf Unknown gesetzt)
                type = ExtractorLinkType.M3U8,  // Stream-Typ (hier z.B. M3U8, kann je nach Typ angepasst werden)
                headers = emptyMap(),  // Falls zusätzliche Header benötigt werden
                extractorData = emptyMap()  // Extrahierte Zusatzdaten, falls notwendig
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
            val mirrorAndDate = it.parent()?.select("div#mirror-head")?.text()?.split("|") ?: listOf("Mirror 1", "Unknown")
            val mirror = mirrorAndDate.getOrElse(0) { "Mirror 1" }
            val date = mirrorAndDate.getOrElse(1) { "Unknown" }
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
