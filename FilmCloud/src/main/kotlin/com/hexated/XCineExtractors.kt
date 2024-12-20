package com.hexated

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.Streamhub
import com.lagradost.cloudstream3.extractors.Voe


class StreamTapeTo : StreamTape() {
    override var mainUrl = "https://streamtape.com"
}

class StreamHubGg : Streamhub() {
    override var name = "Streamhub Gg"
    override var mainUrl = "https://streamhub.gg"
}

class VoeSx: Voe() {
    override val name = "Voe Sx"
    override val mainUrl = "https://voe.sx"
}

class MetaGnathTuggers : Voe() {
    override val name = "Metagnathtuggers"
    override val mainUrl = "https://metagnathtuggers.com"
}

class FileLions : Filesim() {
    override val name = "Filelions"
    override var mainUrl = "https://filelions.to"
}

class Mixdrp : MixDrop(){
    override var mainUrl = "https://mixdrp.to"
}

class DoodReExtractor : DoodLaExtractor() {
    override var mainUrl = "https://dood.re"
}

class Streamzz : Streamcrypt() {
    override var name = "Streamzz"
    override var mainUrl = "https://streamzz.to"
}

open class Streamcrypt : ExtractorApi() {
    override var name = "Streamcrypt"
    override var mainUrl = "https://streamcrypt.net"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document
        doc.select("script").map { it.data() }.filter { it.contains("eval(function(p,a,c,k,e,d)") }
            .map { script ->
                val data = getAndUnpack(script)
                val link = Regex("src:['\"](.*)['\"]").find(data)?.groupValues?.getOrNull(1)
                    ?: return@map null
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        link,
                        "",
                        Qualities.Unknown.value
                    )
                )
            }
    }
}
