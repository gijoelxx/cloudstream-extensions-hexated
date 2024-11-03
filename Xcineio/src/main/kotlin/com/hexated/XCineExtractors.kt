package com.hexated

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.Streamhub
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import java.net.URI

class MoflixLink : MoflixClick() {
    override val name = "MoflixLink"
    override val mainUrl = "https://moflix-stream.link"
}

class MoflixFans : MoflixClick() {
    override val name = "MoflixFans"
    override val mainUrl = "https://moflix-stream.fans"
}

class Highstream : MoflixClick() {
    override val name = "Highstream"
    override val mainUrl = "https://highstream.tv"
}

open class MoflixClick : ExtractorApi() {
    override val name = "MoflixClick"
    override val mainUrl = "https://moflix-stream.click"
    override val requiresReferer = true



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
class StreamHubGg : Streamhub() {
    override var name = "Streamhub Gg"
    override var mainUrl = "https://streamhub.gg"
}
class StreamTapeAdblockuser : StreamTape() {
    override var mainUrl = "https://streamtapeadblockuser.xyz"
}

class StreamTapeTo : StreamTape() {
    override var mainUrl = "https://streamtape.to"
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
open class Doodstream : ExtractorApi() {
    override val name = "Doodstream"
    override val mainUrl = "https://doodstream.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val req = app.get(url)
        val host = getBaseUrl(req.url)
        val response0 = req.text
        val md5 = host + (Regex("/pass_md5/[^']*").find(response0)?.value ?: return)
        val trueUrl =
            app.get(md5, referer = req.url).text + "qWMG3yc6F5?token=" + md5.substringAfterLast("/")
        val quality = Regex("\\d{3,4}p").find(
            response0.substringAfter("<title>").substringBefore("</title>")
        )?.groupValues?.get(0)
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                trueUrl,
                mainUrl,
                getQualityFromName(quality),
                false
            )
        )
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

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
