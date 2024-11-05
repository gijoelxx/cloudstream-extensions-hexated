package com.hexated

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.toLoadResponse
import com.lagradost.cloudstream3.utils.*
import okhttp3.Headers
import org.jsoup.Jsoup

class Kinox : MainAPI() {
    override var name = "KinoxTo"
    override var mainUrl = "https://www21.kinox.to"
    override var lang = "de"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    
    // Request headers
    private val headers = Headers.headersOf(
        "Accept", "application/json, text/javascript, */*; q=0.01",
        "X-Requested-With", "XMLHttpRequest"
    )

    // Set main page for Action genre
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "$mainUrl/aGET/List/?sEcho=4&iColumns=7&sColumns=&iDisplayStart=${page * 25}&iDisplayLength=25" +
                "&iSortingCols=1&iSortCol_0=2&sSortDir_0=asc&bSortable_0=true&bSortable_1=true&bSortable_2=true" +
                "&bSortable_3=false&bSortable_4=false&bSortable_5=false&bSortable_6=true" +
                "&additional=%7B%22Length%22%3A60%2C%22fLetter%22%3A%22%22%2C%22fGenre%22%3A%221%22%7D"

        val response = app.get(url, headers = headers).text
        val json = JsonParser.parseString(response).asJsonObject

        // Parse JSON response
        val data = json.getAsJsonArray("aaData")
        val items = data.mapNotNull { item ->
            val columns = item.asJsonArray
            val titleHtml = columns[2].asString
            val yearMatch = Regex("""<span class="Year">(\d+)<\/span>""").find(titleHtml)
            val year = yearMatch?.groups?.get(1)?.value?.toIntOrNull()
            val title = Jsoup.parse(titleHtml).text()
            val link = Jsoup.parse(titleHtml).selectFirst("a")?.attr("href")

            if (link != null) {
                MovieSearchResponse(
                    title,
                    "$mainUrl$link",
                    TvType.Movie,
                    year = year
                )
            } else null
        }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun load(url: String): LoadResponse? {
        // Here you would add logic to retrieve more details about a movie or episode list for a series.
        return MovieLoadResponse(
            name = "Movie Title",
            url = url,
            type = TvType.Movie,
            year = 2021,
            description = "Film Beschreibung hier",
            episodes = emptyList()
        ).toLoadResponse()
    }
}
