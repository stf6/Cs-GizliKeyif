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
        val url = when {
            page <= 1 -> request.data
            request.data.endsWith("/") -> "${request.data}$page/"
            else -> "${request.data}/$page/"
        }
        val document = app.get(url, timeout = 30).document
        val items = document.select("a[href*=/video/]").mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
        return newHomePageResponse(
            HomePageList(request.name, items, isHorizontalImages = true),
            items.isNotEmpty()
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val q = query.trim().replace(" ", "-")
        val url = if (page <= 1) {
            "$mainUrl/search/videos/$q"
        } else {
            "$mainUrl/search/videos/$q/$page"
        }
        val document = app.get(url, timeout = 30).document
        val items = document.select("a[href*=/video/]").mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
        return items.toNewSearchResponseList()
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(this.attr("href")) ?: return null
        if (!href.contains("/video/")) return null

        // Prefer content-title text; fallback to img title/alt
        val title = this.selectFirst(".content-title")?.text()?.trim()
            ?: this.attr("title").trim().ifBlank { null }
            ?: this.selectFirst("img")?.attr("title")?.trim()?.ifBlank { null }
            ?: this.selectFirst("img")?.attr("alt")?.trim()?.ifBlank { null }
            ?: return null

        val poster = fixUrlNull(
            this.selectFirst("img")?.attr("data-src")
                ?: this.selectFirst("img")?.attr("data-original")
                ?: this.selectFirst("img")?.attr("src")
        )

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = poster
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, timeout = 30).document
        val title = document.selectFirst("h1")?.text()?.trim()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: return null
        val poster = fixUrlNull(
            document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst("video")?.attr("poster")
        )
        val tags = document.select(".tags a, .tag-list a, a[href*=/tags/]").map { it.text().trim() }.filter { it.isNotEmpty() }
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
        val document = app.get(data, timeout = 30).document
        val html = document.html()

        // videojs / direct sources
        document.select("video source, source[src], video[src]").forEach { el ->
            val src = el.attr("src").ifBlank { return@forEach }
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = src,
                    type = if (src.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) { this.referer = "$mainUrl/" }
            )
            return true
        }

        // Common KVS / tube patterns
        val patterns = listOf(
            Regex("""['\"](https?://[^'\"]+\\.m3u8[^'\"]*)['\"]"""),
            Regex("""['\"](https?://[^'\"]+\\.mp4[^'\"]*)['\"]"""),
            Regex("""video_url\s*:\s*['\"]([^'\"]+)['\"]"""),
            Regex("""file\s*:\s*['\"](https?://[^'\"]+)['\"]"""),
        )
        for (pat in patterns) {
            val link = pat.find(html)?.groupValues?.getOrNull(1)
            if (!link.isNullOrBlank()) {
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

        Log.d(tag, "No video link found")
        return false
    }
}
