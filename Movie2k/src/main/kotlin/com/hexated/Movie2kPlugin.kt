package com.hexated
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Movie2kPlugin : Plugin() {
    override fun load(context: Context) {
      registerMainAPI(Movie2k))


     }
}
