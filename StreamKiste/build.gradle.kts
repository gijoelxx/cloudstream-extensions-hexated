// use an integer for version numbers
version = 1


cloudstream {
    language = "de"
    // All of these properties are optional, you can safely remove them

    // description = "StreamKiste Debug"
    authors = listOf("GIJoelX")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries",
        "Movie",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=Streamkiste.tv&sz=%size%"
}
