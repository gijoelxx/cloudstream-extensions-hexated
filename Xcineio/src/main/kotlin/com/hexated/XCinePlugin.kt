
package com.hexated
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FilmCloud: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(XCine())
        registerMainAPI(Movie4k())
        registerMainAPI(Movie2k())
        registerMainAPI(KinoKiste())
        registerMainAPI(FreeTVProvider())
        registerExtractorAPI(StreamTapeAdblockuser())
        registerExtractorAPI(StreamTapeTo())
        registerExtractorAPI(Mixdrp())
        registerExtractorAPI(DoodReExtractor())
        registerExtractorAPI(Streamzz())
        registerExtractorAPI(Streamcrypt())
    }
}
