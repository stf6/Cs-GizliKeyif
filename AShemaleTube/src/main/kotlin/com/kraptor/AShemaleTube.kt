// ! Bu araç @stf6 / Cs-GizliKeyif için yazılmıştır.
package com.kraptor

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class AShemaleTube : MainAPI() {
    override var mainUrl = "https://www.ashemaletube.com"
    override var name = "AShemaleTube"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded   // Cloudflare protection

    private val tag = "gizlikeyif_$name"

    override val mainPage = mainPageOf(
        "$mainUrl/" to "Trending",
        "$mainUrl/videos/" to "Latest Videos",
        // Add more categories after testing selectors
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) request.data else "${request.data.trimEnd('/')}/$page/"
        val document = app.get(url).document

        // TODO: Adjust these selectors after inspecting real HTML (Cloudflare may change structure)
        val items = document.select("div.video-item, article.video, .thumb-list .thumb, .video-list .item")
            .mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = items,
                isHorizontalImages = true
            ),
            hasNext = items.isNotEmpty()
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val q = query.trim().replace(" ", "+")
        val url = if (page <= 1) "$mainUrl/search/?q=$q" else "$mainUrl/search/?q=$q&page=$page"
        val document = app.get(url).document
        val items = document.select("div.video-item, article.video, .thumb-list .thumb, .video-list .item")
            .mapNotNull { it.toSearchResult() }
        return items.toNewSearchResponseList()
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val a = this.selectFirst("a[href*=/videos/]") ?: return null
        val href = fixUrlNull(a.attr("href")) ?: return null
        val title = a.attr("title").ifBlank { a.text() }.ifBlank { return null }
        val poster = fixUrlNull(
            this.selectFirst("img")?.attr("data-src")
                ?: this.selectFirst("img")?.attr("data-lazy-src")
                ?: this.selectFirst("img")?.attr("src")
        )

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = poster
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        val tags = document.select(".tags a, .video-tags a, .tag-list a").map { it.text() }

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

        // Try common patterns for KVS / tube sites
        val m3u8 = Regex("""['\"](https?://[^'\"]+\\.m3u8[^'\"]*)['\"]""").find(html)?.groupValues?.get(1)
        val mp4 = Regex("""['\"](https?://[^'\"]+\\.mp4[^'\"]*)['\"]""").find(html)?.groupValues?.get(1)
            ?: document.selectFirst("meta[itemprop=contentURL]")?.attr("content")

        val link = m3u8 ?: mp4
        if (link.isNullOrBlank()) {
            Log.d(tag, "No video link found")
            return false
        }

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = link,
                type = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
            ) {
                this.referer = "$mainUrl/"
            }
        )
        return true
    }
}
