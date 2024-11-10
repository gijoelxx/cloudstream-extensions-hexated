import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import okhttp3.OkHttpClient

class StreamKiste : MainAPI() {
    override var name = "StreamKiste"
    override var mainUrl = "https://Streamkiste.tv"
    override var lang = "de"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    val client = OkHttpClient.Builder()
        .addInterceptor(CloudflareKiller())
        .build()

    override val mainPage = mainPageOf(
        "cat/filme/sortby/popular" to "Derzeit Beliebt",
        "cat/kinofilme" to "Filme im Kino",
        "cat/filme/sortby/update" to "Letzte Updates",
        "cat/abenteuer" to "Abenteuer",
        "cat/action" to "Action",
        "cat/animation" to "Animation",
        "cat/biographie" to "Biographie",
        "cat/dokumentation" to "Dokumentation",
        "cat/drama" to "Drama",
        "cat/familie" to "Familie",
        "cat/fantasy" to "Fantasy",
        "cat/geschichte" to "Geschichte",
        "cat/horror" to "Horror",
        "cat/komoedie" to "Komoedie",
        "cat/krieg" to "Krieg",
        "cat/krimi" to "Krimi",
        "cat/lovestory" to "Lovestory",
        "cat/musical" to "Musical",
        "cat/mystery" to "Mystery",
        "cat/romantik" to "Romantik",
        "cat/sci-fi" to "Sci-Fi",
        "cat/sport" to "Sport",
        "cat/thriller" to "Thriller",
        "cat/western" to "Western",
        "cat/zeichentrick" to "Zeichentrick"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return try {
            val response = app.get("$mainUrl/${request.data}?page=$page", client = client)
            checkHttpStatus(response)

            val document = response.document
            val home = document.select("div.movie-item").mapNotNull { it.toSearchResult() }
            newHomePageResponse(request.name, home)
        } catch (e: Exception) {
            handleError("Fehler beim Abrufen der Hauptseite: ${e.message}")
            newHomePageResponse(request.name, emptyList())
        }
    }

    private fun checkHttpStatus(response: Response) {
        when (response.statusCode) {
            403 -> throw HttpException("Zugriff verweigert (403): Die Seite ist gesperrt.")
            502 -> throw HttpException("Fehler 502: Bad Gateway. Server nicht erreichbar.")
            404 -> throw HttpException("Fehler 404: Seite nicht gefunden.")
            in 500..599 -> throw HttpException("Serverfehler (5xx): Ein unerwarteter Fehler trat auf.")
        }
    }

    private fun handleError(message: String) {
        logError(message)
    }

    private fun logError(message: String) {
        // Hier könnte ein echtes Logging-Framework verwendet werden, z.B. Logback, Timber, etc.
        println("ERROR: $message")
    }

    private fun Element.toSearchResult(): SearchResponse? {
        return try {
            val href = getProperLink(this.selectFirst("a")?.attr("href") ?: return null)
            val title = this.selectFirst("a")?.text()
                ?: this.selectFirst("img")?.attr("alt")
                ?: this.selectFirst("a")?.attr("title")
                ?: run {
                    logError("Kein Titel oder Link gefunden")
                    return null
                }

            val posterUrl = fixUrlNull(
                (this.selectFirst("div.poster img") ?: this.selectFirst("img"))?.getImageAttr()
            ) ?: run {
                logError("Kein Poster-Bild gefunden")
                return null
            }

            val type = when {
                href.contains("episode") -> TvType.TvSeries
                title.contains("Film", ignoreCase = true) -> TvType.Movie
                else -> TvType.Movie
            }

            newTvSeriesSearchResponse(title, href, type) {
                this.posterUrl = posterUrl
            }

        } catch (e: Exception) {
            logError("Fehler beim Verarbeiten des Elements: ${e.message}")
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/search/?sq=$query"
        val document = app.get(searchUrl, client = client).document
        return document.select("div.movie-item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        return try {
            val document = app.get(url, client = client).document

            val title = document.selectFirst("h2")?.text() ?: run {
                logError("Titel konnte nicht gefunden werden für URL: $url")
                return LoadResponse(error = "Titel konnte nicht gefunden werden.")
            }

            val poster = fixUrlNull(document.selectFirst("div.poster img")?.getImageAttr())
                ?: run {
                    logError("Poster-Bild konnte nicht gefunden werden für URL: $url")
                    return LoadResponse(error = "Poster-Bild konnte nicht gefunden werden.")
                }

            val description = document.selectFirst("div.excerpt")?.text() ?: ""

            val year = """(\d{4})""".toRegex().find(title)?.groupValues?.get(1)?.toIntOrNull()

            val imdbRating = document.selectFirst("p:contains(IMDB Rating)")?.text()?.substringAfter("IMDB Rating:")?.trim()

            val categories = document.select("p:contains(Genres)").firstOrNull()?.text()?.substringAfter("Genres:")?.split(",")?.map { it.trim() } ?: emptyList()

            val director = document.selectFirst("p:contains(Regisseur)")?.text()?.substringAfter("Regisseur:")?.trim()
            val actors = document.selectFirst("p:contains(Schauspieler)")?.text()?.substringAfter("Schauspieler:")?.trim()

            val views = document.selectFirst("p:contains(Aufrufe)")?.text()?.substringAfter("Aufrufe:")?.trim()?.toIntOrNull()

            val recommendations = document.select("ul.ul_related li").mapNotNull { it.toSearchResult() }

            LoadResponse(title, poster, description, year, imdbRating, categories, director, actors, views, recommendations)

        } catch (e: Exception) {
            logError("Fehler beim Laden der URL: $url, Fehler: ${e.message}")
            LoadResponse(error = "Fehler beim Laden der Seite: ${e.message}")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            loadCustomExtractor(data, "$mainUrl/", subtitleCallback, callback)
            true
        } catch (e: Exception) {
            logError("Fehler beim Laden der Links für $data: ${e.message}")
            false
        }
    }

    private suspend fun loadCustomExtractor(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        quality: Int? = null,
    ) {
        try {
            loadExtractor(url, referer, subtitleCallback) { link ->
                if (link.quality == Qualities.Unknown.value) {
                    callback.invoke(
                        ExtractorLink(
                            link.source,
                            link.name,
                            link.url,
                            link.referer,
                            when (link.type) {
                                ExtractorLinkType.M3U8 -> link.quality
                                else -> quality ?: link.quality
                            },
                            link.type,
                            link.headers,
                            link.extractorData
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logError("Fehler beim Laden der Extraktor-Daten von der URL: $url. Fehler: ${e.message}")
        }
    }

    private fun Element.getImageAttr(): String? {
        return when {
            this.hasAttr("data-src") -> this.attr("data-src")
            this.hasAttr("data-lazy-src") -> this.attr("data-lazy-src")
            this.hasAttr("srcset") -> this.attr("srcset").substringBefore(" ")
            else -> this.attr("src")
        }
    }
}
