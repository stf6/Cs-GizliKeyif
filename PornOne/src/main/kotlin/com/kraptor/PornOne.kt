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
        "/ass/" to "Ass",
        "/bbw/" to "BBW",
        "/big-boobs/" to "Big Boobs",
        "/big-dick/" to "Big Dick",
        "/blonde/" to "Blonde",
        "/brunette/" to "Brunette",
        "/busty/" to "Busty",
        "/creampie/" to "Creampie",
        "/daughter/" to "Daughter",
        "/double-anal/" to "Double Anal",
        "/foursome/" to "Foursome",
        "/gangbang/" to "Gangbang",
        "/indian/" to "Indian",
        "/lesbian/" to "Lesbian",
        "/milf/" to "Milf",
        "/mom/" to "Mom",
        "/natural-tits/" to "Natural Tits",
        "/stepmom/" to "Stepmom",
        "/teacher/" to "Teacher",
        "/threesome/" to "Threesome",
        "/wife/" to "Wife",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl${request.data}$page", timeout = 30).document
        val responseList = document.select(".popbop.vidLinkFX").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(
            HomePageList(request.name, responseList, isHorizontalImages = true),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.select(".videotitle").text().ifBlank { return null }
        val href = fixUrlNull(this.attr("href")) ?: return null
        var posterUrl = this.select(".imgvideo").attr("data-src")
        if (posterUrl.isEmpty()) {
            posterUrl = this.select(".imgvideo").attr("src")
        }

        return newMovieSearchResponse(title, href, TvType.NSFW) {
            this.posterUrl = fixUrlNull(posterUrl)
        }
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val document = app.get("$mainUrl/search?q=$query&page=$page", timeout = 30).document
        val results = document.select(".popbop.vidLinkFX").mapNotNull { it.toSearchResult() }
        return results.toNewSearchResponseList()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val script = document.select("script[data-react-helmet=\"true\"]").html()
        return try {
            val jsonObj = JSONObject(script)
            val title = jsonObj.optString("name").ifBlank { document.selectFirst("h1")?.text() ?: return null }
            val poster = try {
                jsonObj.getJSONArray("thumbnailUrl").optString(0)
            } catch (_: Exception) {
                document.selectFirst("meta[property=og:image]")?.attr("content")
            }
            val description = jsonObj.optString("description")

            newMovieLoadResponse(title, url, TvType.NSFW, url) {
                this.posterUrl = fixUrlNull(poster)
                this.plot = description
            }
        } catch (e: Exception) {
            Log.d(tag, "load fallback: ${e.message}")
            val title = document.selectFirst("h1")?.text()?.trim() ?: return null
            val poster = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
            newMovieLoadResponse(title, url, TvType.NSFW, url) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d(tag, "loadLinks data = $data")
        val doc = app.get(data).document
        val sources = doc.select("#pornone-video-player source")
        if (sources.isEmpty()) {
            // Fallback: try common video sources
            val html = doc.html()
            val m3u8 = Regex("""['\"](https?://[^'\"]+\\.m3u8[^'\"]*)['\"]""").find(html)?.groupValues?.get(1)
            val mp4 = Regex("""['\"](https?://[^'\"]+\\.mp4[^'\"]*)['\"]""").find(html)?.groupValues?.get(1)
            val link = m3u8 ?: mp4
            if (link.isNullOrBlank()) return false
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

        sources.forEach { item ->
            val src = item.attr("src")
            if (src.isBlank()) return@forEach
            val quality = item.attr("res").toIntOrNull() ?: 0
            callback.invoke(
                newExtractorLink(
                    source = name,
                    name = name,
                    url = src,
                    type = ExtractorLinkType.VIDEO
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = quality
                }
            )
        }
        return true
    }
}
