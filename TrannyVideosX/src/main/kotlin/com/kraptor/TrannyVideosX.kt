// ! Bu araç @stf6 / Cs-GizliKeyif için yazılmıştır.
package com.kraptor

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class TrannyVideosX : MainAPI() {
    override var mainUrl = "https://trannyvideosx.com"
    override var name = "TrannyVideosX"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    private val tag = "gizlikeyif_$name"

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Latest",
        "$mainUrl/videos/" to "All Videos",
        "$mainUrl/videos/most-popular/" to "Most Popular",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data.trimEnd('/')}/$page/"
        val document = app.get(url).document
        val items = document.select(".video-item, .item, .thumb, article, .video").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            HomePageList(request.name, items, isHorizontalImages = true),
            items.isNotEmpty()
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val q = query.trim().replace(" ", "+")
        val url = if (page <= 1) "$mainUrl/search/videos/$q" else "$mainUrl/search/videos/$q/$page"
        val document = app.get(url).document
        val items = document.select(".video-item, .item, .thumb, article, .video").mapNotNull { it.toSearchResult() }
        return items.toNewSearchResponseList()
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = this.selectFirst("a[href*=/video/], a[href*=/videos/]") ?: this.selectFirst("a[href]") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = a.attr("title")
            .ifBlank { this.selectFirst(".title, h2, h3")?.text().orEmpty() }
            .ifBlank { a.text() }
            .ifBlank { return null }
        val poster = fixUrlNull(
            this.selectFirst("img")?.attr("data-src")
                ?: this.selectFirst("img")?.attr("data-original")
                ?: this.selectFirst("img")?.attr("src")
        )
        return newMovieSearchResponse(title, href, TvType.NSFW) { this.posterUrl = poster }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val tags = document.select(".tags a, .tag-list a, a[rel=tag]").map { it.text() }
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(tag, "loadLinks data = $data")
        val document = app.get(data).document
        val html = document.html()

        val m3u8 = Regex("""['\"](https?://[^'\"]+\\.m3u8[^'\"]*)['\"]""").find(html)?.groupValues?.get(1)
        val mp4 = Regex("""['\"](https?://[^'\"]+\\.mp4[^'\"]*)['\"]""").find(html)?.groupValues?.get(1)
            ?: document.selectFirst("meta[itemprop=contentURL], video source, #player source")?.attr("src")

        val link = m3u8 ?: mp4
        if (link.isNullOrBlank()) return false

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = link,
                type = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) { this.referer = "$mainUrl/" }
        )
        return true
    }
}
