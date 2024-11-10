
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import okhttp3.OkHttpClient


class StreamKiste: MainAPI() {
    override var name = "StreamKiste"
    override var mainUrl = "https://Streamkiste.tv"
    override var lang = "de"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    val client = OkHttpClient.Builder()
    .addInterceptor(CloudflareKiller())
    .build()

    override val mainPage = mainPageOf(
              "cat/filme/sortby/popular"  to "Derzeit Beliebt",
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
               "cat/zeichentrick" to "Zeichentrick",
    )
    
override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
    return try {
        // Versuchen, den Pfad mit einem page-Parameter zu bilden
        val response = app.get("$mainUrl/${request.data}?page=$page", client = client)

        // Prüfe, ob der HTTP-Statuscode auf Fehler hinweist
        when (response.statusCode) {
            403 -> {
                // HTTP 403: Forbidden
                throw HttpException("Zugriff verweigert (403): Die Seite ist gesperrt.")
            }
            502 -> {
                // HTTP 502: Bad Gateway
                throw HttpException("Fehler 502: Bad Gateway. Server nicht erreichbar.")
            }
            404 -> {
                // HTTP 404: Not Found
                throw HttpException("Fehler 404: Seite nicht gefunden.")
            }
            in 500..599 -> {
                // HTTP 5xx: Server Fehler
                throw HttpException("Serverfehler (5xx): Ein unerwarteter Fehler trat auf.")
            }
            else -> {
                // Alles in Ordnung
            }
        }

        // Wenn der Statuscode OK ist, analysiere die Seite weiter
        val document = response.document
        val home = document.select("div.movie-item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)

    } catch (e: HttpException) {
        // Spezifische Fehlerbehandlung für HTTP-Fehler
        logError("HTTP-Fehler: ${e.message}")
        return newHomePageResponse(request.name, emptyList()) // Rückgabe einer leeren Antwort
    } catch (e: Exception) {
        // Allgemeine Fehlerbehandlung für alles andere
        logError("Fehler beim Abrufen der Hauptseite: ${e.message}")
        return newHomePageResponse(request.name, emptyList()) // Rückgabe einer leeren Antwort
    }
}

// Eine Beispiel-Logging-Funktion
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

        // Erkenne, ob es sich um einen Film oder eine Serie handelt
        val type = when {
            href.contains("episode") -> TvType.TvSeries  // Wenn der Link 'episode' enthält, dann Serie
            title.contains("Film", ignoreCase = true) -> TvType.Movie  // Wenn der Titel "Film" enthält, dann Film
            else -> TvType.Movie  // Fallback: Ansonsten als Film behandeln
        }

        // Gebe das Ergebnis zurück
        return newTvSeriesSearchResponse(title, href, type) { 
            this.posterUrl = posterUrl 
        }
        
    } catch (e: Exception) {
        // Fehlerbehandlung für andere unerwartete Fehler
        logError("Fehler beim Verarbeiten des Elements: ${e.message}")
        return null
    }
}

// Logging-Funktion zur Fehlerprotokollierung
private fun logError(message: String) {
    // Hier könnte ein echtes Logging-Framework verwendet werden, z.B. Logback, Timber, etc.
    println("ERROR: $message")
}


override suspend fun search(query: String): List<SearchResponse> {
    // Formatiere die URL für die Suchanfrage
    val searchUrl = "$mainUrl/search/?sq=$query" // Angepasst für Streamkiste

    // Lade die Suchergebnisseite
    val document = app.get(searchUrl, client = client).document

    // Extrahiere alle relevanten Film- oder Serien-Elemente (z.B. "movie-item")
    return document.select("div.movie-item").mapNotNull { element ->
        element.toSearchResult()
    }
}
override suspend fun load(url: String): LoadResponse {
    return try {
        // Hole das Dokument der angegebenen URL
        val document = app.get(url, client = client).document

        // Titel des Films/der Serie (z. B. "Venom")
        val title = document.selectFirst("h2")?.text() ?: run {
            logError("Titel konnte nicht gefunden werden für URL: $url")
            return LoadResponse(error = "Titel konnte nicht gefunden werden.")
        }

        // Poster-Bild (aus div.poster img)
        val poster = fixUrlNull(document.selectFirst("div.poster img")?.getImageAttr())
            ?: run {
                logError("Poster-Bild konnte nicht gefunden werden für URL: $url")
                return LoadResponse(error = "Poster-Bild konnte nicht gefunden werden.")
            }

        // Beschreibung (aus p.story oder div.excerpt)
        val description = document.selectFirst("div.excerpt")?.text() ?: ""

        // Jahr aus dem Titel extrahieren (z. B. (2024))
        val year = """(\d{4})""".toRegex().find(title)?.groupValues?.get(1)?.toIntOrNull()
            ?: run {
                logError("Jahr konnte nicht aus dem Titel extrahiert werden für URL: $url")
                null
            }

        // IMDb-Bewertung
        val imdbRating = document.selectFirst("p:contains(IMDB Rating)")?.text()?.substringAfter("IMDB Rating:")?.trim()

        // Genres / Kategorien (z. B. Abenteuer, Action)
        val categories = document.select("p:contains(Genres)").firstOrNull()?.text()?.substringAfter("Genres:")?.split(",")?.map { it.trim() } ?: emptyList()

        // Regisseur und Schauspieler
        val director = document.selectFirst("p:contains(Regisseur)")?.text()?.substringAfter("Regisseur:")?.trim()
        val actors = document.selectFirst("p:contains(Schauspieler)")?.text()?.substringAfter("Schauspieler:")?.trim()

        // Anzahl der Aufrufe
        val views = document.selectFirst("p:contains(Aufrufe)")?.text()?.substringAfter("Aufrufe:")?.trim()?.toIntOrNull()

        // Empfehlungen (Verwandte Filme/Serien)
        val recommendations = document.select("ul.ul_related li").mapNotNull {
            it.toSearchResult()
        }

        // Rückgabe der geladenen Daten
        LoadResponse(title, poster, description, year, imdbRating, categories, director, actors, views, recommendations)
        
    } catch (e: Exception) {
        // Allgemeine Fehlerbehandlung
        logError("Fehler beim Laden der URL: $url, Fehler: ${e.message}")
        return LoadResponse(error = "Fehler beim Laden der Seite: ${e.message}")
    }
}

// Logging-Funktion zur Fehlerprotokollierung
private fun logError(message: String) {
    // Hier könnte ein echtes Logging-Framework verwendet werden, z.B. Logback, Timber, etc.
    println("ERROR: $message")
}

// Stream-Links (aus div#stream-links a)
val streamLinks = try {
    document.select("div#stream-links a").mapNotNull {
        // Extrahiere den Stream-Link
        val streamUrl = it.attr("href").takeIf { it.isNotEmpty() }
            ?: run {
                logError("Fehlender Stream-Link (href-Attribut) in einem der Stream-Elemente.")
                return@mapNotNull null  // Kein gültiger Link gefunden
            }

        // Extrahiere Qualität (Standardwert "HD", falls nicht vorhanden)
        val quality = it.select("span.icon-hd")?.text().takeIf { it.isNotEmpty() } ?: "HD"

        // Extrahiere Sprache (Standardwert "Deutsch", falls nicht vorhanden)
        val language = it.select("span.language")?.text().takeIf { it.isNotEmpty() } ?: "Deutsch"

        // Extrahiere Datum (Standardwert "Unbekannt", falls nicht vorhanden)
        val date = it.select("span.date")?.text().takeIf { it.isNotEmpty() } ?: "Unbekannt"

        // Rückgabe der Stream-Daten, wenn alles korrekt extrahiert wurde
        StreamData(streamUrl, quality, language, date)
    }
} catch (e: Exception) {
    // Fehlerprotokollierung, falls ein unerwarteter Fehler auftritt
    logError("Fehler beim Extrahieren der Stream-Links: ${e.message}")
    emptyList()  // Rückgabe einer leeren Liste im Fehlerfall
}

// Log-Funktion für Fehlerprotokolle
private fun logError(message: String) {
    // Hier kannst du deine Logik für Fehlerprotokollierung einfügen
    // Beispiel: println oder Log.d, je nach Bedarf
    println("Fehler: $message")
}

    // JSON-Daten für Streams, die vom Server zurückgegeben werden
val streams = try {
    // Holen des JSON-Objekts
    val json = app.get("$url?json=true", client = client).jsonObject

    // Extrahieren der Stream-Daten
    json.getJSONArray("streams").mapNotNull {
        try {
            // Extrahieren der einzelnen Stream-Details
            val mirror = it.getString("mirror") ?: run {
                logError("Fehlender Wert für 'mirror' in Stream-Daten.")
                return@mapNotNull null
            }
            val date = it.getString("date") ?: "Unbekannt"
            val language = it.getString("language") ?: "Unbekannt"
            val quality = it.getString("quality") ?: "HD"
            val streamUrl = it.getString("url") ?: run {
                logError("Fehlender Wert für 'url' in Stream-Daten.")
                return@mapNotNull null
            }

            // Rückgabe der Stream-Daten
            StreamData(streamUrl, quality, language, date, mirror)
        } catch (e: Exception) {
            logError("Fehler beim Extrahieren eines Streams: ${e.message}")
            null // Wenn ein Fehler auftritt, überspringen wir diesen Stream
        }
    }
} catch (e: Exception) {
    // Fehlerbehandlung für Netzwerk- oder JSON-Parsing-Fehler
    logError("Fehler beim Abrufen oder Verarbeiten der JSON-Daten: ${e.message}")
    emptyList() // Leere Liste zurückgeben, wenn ein Fehler auftritt
}

// Log-Funktion für Fehlerprotokolle
private fun logError(message: String) {
    // Hier kannst du deine Logik für Fehlerprotokollierung einfügen
    // Beispiel: println oder Log.d, je nach Bedarf
    println("Fehler: $message")
}

// Rückgabe der vollständigen Antwort, inklusive Stream-Daten
return try {
    newTvSeriesLoadResponse(title, url, TvType.Movie, episodes = emptyList()) {
        // Sicherstellen, dass alle Felder gültige Werte haben
        this.posterUrl = poster ?: "DefaultPosterUrl" // Default-Wert für Poster-URL
        this.year = year ?: 0 // Standardjahr, falls nicht verfügbar
        this.plot = description ?: "Keine Beschreibung verfügbar" // Fallback-Beschreibung
        this.imdbRating = imdbRating ?: "Nicht bewertet" // Standardwert für IMDb-Bewertung
        this.categories = categories.ifEmpty { listOf("Keine Kategorien") } // Standardwert für Kategorien
        this.director = director ?: "Unbekannter Regisseur" // Fallback-Regisseur
        this.actors = actors ?: "Keine Schauspieler angegeben" // Fallback-Schauspieler
        this.views = views ?: 0 // Fallback-Wert für Views
        this.recommendations = recommendations.ifEmpty { listOf() } // Fallback für Empfehlungen
        this.streamLinks = streamLinks.ifEmpty { listOf() } // Fallback für Stream-Links
        this.streams = streams.ifEmpty { listOf() } // Fallback für Streams
    }
} 

// Definition der logError Methode
private fun logError(message: String, e: Exception) {
    Timber.e(e, message) // Protokolliere sowohl die Nachricht als auch die Ausnahme
}

// Fehlerbehandlung im catch-Block
catch (e: Exception) {
    // Fehlerbehandlung, falls irgendetwas bei der Rückgabe schiefgeht
    logError("Fehler beim Erstellen der Antwort: ${e.message}", e)
    
    // Rückgabe einer Antwort mit Standardwerten oder leeren Listen
    newTvSeriesLoadResponse(title, url, TvType.Movie, episodes = emptyList()) {
        this.posterUrl = "DefaultPosterUrl"
        this.year = 0
        this.plot = "Fehler bei der Beschreibung"
        this.imdbRating = "Fehler bei der Bewertung"
        this.categories = listOf("Unbekannte Kategorie")
        this.director = "Unbekannter Regisseur"
        this.actors = "Unbekannte Schauspieler"
        this.views = 0
        this.recommendations = listOf()
        this.streamLinks = listOf()
        this.streams = listOf()
    }
}

// Log-Funktion für Fehlerprotokolle
private fun logError(message: String) {
    // Hier kannst du deine Logik für Fehlerprotokollierung einfügen
    println("Fehler: $message")
}
override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    return try {
        // Wir laden die Seite mit den Daten
        loadCustomExtractor(data, "$mainUrl/", subtitleCallback, callback)
        true // Erfolgreiche Ausführung
    } catch (e: Exception) {
        // Fehlerbehandlung: Falls etwas schiefgeht
        logError("Fehler beim Laden der Links für $data: ${e.message}")
        false // Rückgabe von false, um einen Fehler anzuzeigen
    }
}

// Log-Funktion für Fehlerprotokolle
private fun logError(message: String) {
    // Hier kannst du deine Logik für Fehlerprotokollierung einfügen
    println("Fehler: $message")
}

private suspend fun loadCustomExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
)
 {
    // Wir laden die Extraktor-Daten mit der URL und Referer
    loadExtractor(url, referer, subtitleCallback) { link ->
        // Wenn die Qualität unbekannt ist, setzen wir die Qualität auf einen Standardwert oder nutzen die übergebene Qualität
        if (link.quality == Qualities.Unknown.value) {
            callback.invoke(
                ExtractorLink(
                    link.source,
                    link.name,
                    link.url,
                    link.referer,
                    when (link.type) {
                        ExtractorLinkType.M3U8 -> link.quality
                        else -> quality ?: link.quality // Setzen der Qualität falls sie unbekannt ist
                    },
                    link.type,
                    link.headers,
                    link.extractorData
                )
            )
        }
    }
}

private suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
) {
    try {
        // Extrahieren der Seite mit der URL
        val document = app.get(url, client = client).document

        // Extrahieren der Stream-Links aus dem div#stream-links Bereich
        document.select("div#stream-links a").forEach { element ->
            val streamUrl = element.attr("href")
            val mirror = element.attr("data-mirror")
            val host = element.attr("data-host")
            val hoster = element.selectFirst(".hoster")?.text() ?: "Unbekannter Hoster"
            val title = element.attr("title") // Beispiel: 'VOE', 'Streamtape'

            // An den Callback weitergeben
            callback.invoke(
                ExtractorLink(
                    streamUrl,
                    hoster,
                    mirror,
                    host,
                    linkType = ExtractorLinkType.Stream, // Stream Link-Typ
                    headers = mapOf(),
                    extractorData = null
                )
            )
        }

        // Extrahieren der Untertitel (falls vorhanden)
        document.select("div#subtitles a").forEach { subtitleElement ->
            val subtitleUrl = subtitleElement.attr("href")
            val subtitleName = subtitleElement.text()

            subtitleCallback.invoke(SubtitleFile(subtitleUrl, subtitleName))
        }
    } catch (e: Exception) {
        // Fehlerbehandlung für alle Ausnahmen (z.B. Netzwerkprobleme, Fehler bei der HTML-Verarbeitung)
        println("Fehler beim Laden der Extraktor-Daten von der URL: $url. Fehler: ${e.message}")
        // Optional: Hier könnte auch ein Fallback-Mechanismus eingebaut werden oder eine leere Antwort zurückgegeben werden
    }
}

// Eine Hilfsfunktion zum Extrahieren des Bildattributs
private fun Element.getImageAttr(): String? {
    return when {
        this.hasAttr("data-src") -> this.attr("data-src")
        this.hasAttr("data-lazy-src") -> this.attr("data-lazy-src")
        this.hasAttr("srcset") -> this.attr("srcset").substringBefore(" ")
        else -> this.attr("src")
    }
}
