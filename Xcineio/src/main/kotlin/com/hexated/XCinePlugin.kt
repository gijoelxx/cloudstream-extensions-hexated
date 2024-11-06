package com.hexated
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FilmCloud : Plugin() {
    override fun load(context: Context) {
        // Alle Provider sollten auf diese Weise hinzugefügt werden.
        // Bitte bearbeite die Provider-Liste nicht direkt.
        registerMainAPI(Aniworld())
        registerMainAPI(Xcinetop())
        registerMainAPI(Movie4k())
        registerMainAPI(KinoKiste())
        registerMainAPI(Kinoger())
        registerMainAPI(FilmpalastProvider())
        registerMainAPI(Serienstream())
        
        

        // Alle Extractor-APIs werden hier registriert
        registerExtractorAPI(Dooood())
        registerExtractorAPI(StreamHubGg())
        registerExtractorAPI(VoeSx())
        registerExtractorAPI(MetaGnathTuggers())
        registerExtractorAPI(FileLions())
        registerExtractorAPI(Kinogeru())
        registerExtractorAPI(StreamTapeTo())
        registerExtractorAPI(Mixdrp())
        registerExtractorAPI(DoodReExtractor())
        registerExtractorAPI(Streamzz())
        registerExtractorAPI(Streamcrypt())
    }
}
