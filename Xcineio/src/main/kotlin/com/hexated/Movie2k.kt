package com.hexated

import com.lagradost.cloudstream3.mainPageOf

class Movie2k : XCine() {
    override var name = "Movie2k"
    override var mainUrl = "https://movie2k.ch"
    override var mainAPI = "https://api.movie2k.ch"

    override val mainPage = mainPageOf(
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=trending" to "Derzeit Beliebt Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=movies&order_by=releases" to "Neu Filme",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=tvseries&order_by=trending" to "Derzeit Beliebt Serien",
        "data/browse/?lang=2&keyword=&year=&rating=&votes=&genre=&country=&cast=&directors=&type=tvseries&order_by=releases" to "Neu Serien",
    )
}
