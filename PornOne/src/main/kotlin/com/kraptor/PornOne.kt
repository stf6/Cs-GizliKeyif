// ! Bu araç @stf6 / Cs-GizliKeyif için yazılmıştır.
package com.kraptor

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject

class PornOne : MainAPI() {
    override var mainUrl = "https://pornone.com"
    override var name = "PornOne"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    private val tag = "gizlikeyif_$name"

    override val mainPage = mainPageOf(
        "/" to "Latest Updates",
        "/anal/" to "Anal",
        "/asian/" to "Asian",
        "/milf/" to "Milf",
        "/mom/" to "Mom",
        "/stepmom/" to "Stepmom",
        "/threesome/" to "Threesome",
        "/wife/" to "Wife",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl${request.data}$page", timeout = 30).document
        val responseList = document.select("a.popbop.vidLinkFX").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            HomePageList(request.name, responseList, isHorizontalImages = true),
            hasNext = responseList.isNotEmpty()
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val href = fixUrlNull(this.attr("href")) ?: return null
        if (!href.contains("pornone.com")) return null

        val title = this.selectFirst(".videotitle")?.text()?.trim()
            ?: this.selectFirst("img.imgvideo")?.attr("alt")?.trim()
            ?: return null

        var posterUrl = this.selectFirst("img.imgvideo")?.attr("data-src")
            ?.ifBlank { null }
            ?: this.selectFirst("img.imgvideo")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = app.get("$mainUrl/search?q=$query&page=$page", timeout = 30).document
        val results = document.select("a.popbop.vidLinkFX").mapNotNull { it.toSearchResult() }
        return results.toNewSearchResponseList()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, timeout = 30).document

        // Prefer JSON-LD / react helmet, fallback to og tags
        val script = document.selectFirst("script[data-react-helmet=true], script[type=application/ld+json]")?.html()
        if (!script.isNullOrBlank()) {
            try {
                val jsonObj = JSONObject(script)
                val title = jsonObj.optString("name").ifBlank {
                    document.selectFirst("h1")?.text()?.trim().orEmpty()
                }
                if (title.isBlank()) return null
                val poster = try {
                    jsonObj.getJSONArray("thumbnailUrl").optString(0)
                } catch (_: Exception) {
                    document.selectFirst("meta[property=og:image]")?.attr("content")
                }
                val description = jsonObj.optString("description")
                return newMovieLoadResponse(title, url, TvType.NSFW, url) {
                    this.posterUrl = fixUrlNull(poster)
                    this.plot = description
                }
            } catch (_: Exception) { }
        }

        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(tag, "loadLinks data = $data")
        val doc = app.get(data, timeout = 30).document
        val sources = doc.select("#pornone-video-player source, video source, source[src]")
        var found = false

        sources.forEach { item ->
            val src = item.attr("src").ifBlank { return@forEach }
            val quality = item.attr("res").toIntOrNull()
                ?: item.attr("label").filter { it.isDigit() }.toIntOrNull()
                ?: 0
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = src,
                    type = if (src.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = quality
                }
            )
            found = true
        }

        if (!found) {
            val html = doc.html()
            val m3u8 = Regex("""https?://[^\"'\\s]+\\.m3u8[^\"'\\s]*""").find(html)?.value
            val mp4 = Regex("""https?://[^\"'\\s]+\\.mp4[^\"'\\s]*""").findAll(html)
                .map { it.value }
                .firstOrNull { !it.contains("gallery.vcmdiawe") && !it.contains(".20.mp4") }

            val link = m3u8 ?: mp4
            if (!link.isNullOrBlank()) {
                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = link,
                        type = if (link.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                    ) { this.referer = "$mainUrl/" }
                )
                found = true
            }
        }
        return found
    }
}
