package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.google.gson.JsonParser
import org.jsoup.Jsoup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class Kinox : MainAPI() {
    override var name = "KinoxTo"
    override var mainUrl = "https://www21.kinox.to"
    override var lang = "de"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // Headers als Map<String, String>
    private val headers = mapOf(
        "Accept" to "application/json, text/javascript, */*; q=0.01",
        "X-Requested-With" to "XMLHttpRequest"
    )

    // Hauptseite laden
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/aGET/List/?sEcho=4&iColumns=7&sColumns=&iDisplayStart=${page * 25}&iDisplayLength=25" +
                "&iSortingCols=1&iSortCol_0=2&sSortDir_0=asc&bSortable_0=true&bSortable_1=true&bSortable_2=true" +
                "&bSortable_3=false&bSortable_4=false&bSortable_5=false&bSortable_6=true" +
                "&additional=%7B%22Length%22%3A60%2C%22fLetter%22%3A%22%22%2C%22fGenre%22%3A%221%22%7D"

        val responseText = withContext(Dispatchers.IO) {
            app.get(url, headers = headers)
        }.text

        val json = JsonParser.parseString(responseText).asJsonObject
        val data = json.getAsJsonArray("aaData")
        
        // Extrahiere die Daten
        val items = data.mapNotNull { item ->
            val columns = item.asJsonArray
            val titleHtml = columns[2].asString
            val yearMatch = Regex("""<span class="Year">(\d+)<\/span>""").find(titleHtml)
            val year = yearMatch?.groups?.get(1)?.value?.toIntOrNull()
            val title = Jsoup.parse(titleHtml).text()
            val link = Jsoup.parse(titleHtml).selectFirst("a")?.attr("href")

            if (link != null) {
                MovieSearchResponse(
                    title = title,
                    url = "$mainUrl$link",
                    type = TvType.Movie,
                    year = year
                )
            } else null
        }

        return HomePageResponse(listOf(HomePageList(request.name, items)))
    }

    // Detaillierte Informationen eines Films oder einer Serie laden
    override suspend fun load(url: String): LoadResponse? {
        val responseText = withContext(Dispatchers.IO) {
            app.get(url, headers = headers)
        }.text

        // Parsen der HTML-Antwort
        val document = Jsoup.parse(responseText)
        val title = document.selectFirst("h1.title")?.text() ?: "Unbekannt" // HTML-Selektor anpassen
        val description = document.selectFirst("div.description")?.text() ?: "Keine Beschreibung verfügbar" // HTML-Selektor anpassen
        val yearText = document.selectFirst("span.Year")?.text()
        val year = yearText?.toIntOrNull()

        return MovieLoadResponse(
            name = title,
            url = url,
            type = TvType.Movie,
            year = year,
            description = description,
            episodes = emptyList(),
            apiName = name,
            dataUrl = url
        )
    }

    // Suchfunktion hinzufügen
    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/aGET/List/?sEcho=1&iColumns=7&sColumns=&iDisplayStart=0&iDisplayLength=25" +
                "&iSortingCols=1&iSortCol_0=2&sSortDir_0=asc&bSortable_0=true&bSortable_1=true&bSortable_2=true" +
                "&bSortable_3=false&bSortable_4=false&bSortable_5=false&bSortable_6=true" +
                "&additional=%7B%22Length%22%3A60%2C%22fLetter%22%3A%22%22%2C%22fGenre%22%3A%221%22%7D&sSearch=$query"

        val responseText = withContext(Dispatchers.IO) {
            app.get(url, headers = headers)
        }.text

        val json = JsonParser.parseString(responseText).asJsonObject
        val data = json.getAsJsonArray("aaData")
        
        // Extrahiere die Suchergebnisse
        return data.mapNotNull { item ->
            val columns = item.asJsonArray
            val titleHtml = columns[2].asString
            val yearMatch = Regex("""<span class="Year">(\d+)<\/span>""").find(titleHtml)
            val year = yearMatch?.groups?.get(1)?.value?.toIntOrNull()
            val title = Jsoup.parse(titleHtml).text()
            val link = Jsoup.parse(titleHtml).selectFirst("a")?.attr("href")

            if (link != null) {
                MovieSearchResponse(
                    title = title,
                    url = "$mainUrl$link",
                    type = TvType.Movie,
                    year = year
                )
            } else null
        }
    }
}
