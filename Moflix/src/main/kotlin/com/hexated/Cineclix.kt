package com.hexated
import com.lagradost.cloudstream3.mainPageOf
class Cineclix : Moflix() {
    override var name = "Movie4k"
    override var mainUrl = "https://api.movie4k.sx"
    
    override val mainPage = mainPageOf(
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Action&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Trend Filme",
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Drama&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Drama Filme",
        "browse/?lang=2&keyword=&year=&rating=&votes=&genre=Comedy&country=&cast=&directors=&type=&order_by=trending&page=1&limit=100" to "Komödie Filme"
    )

}
