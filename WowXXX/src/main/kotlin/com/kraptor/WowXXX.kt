// ! Bu araç @stf6 / Cs-GizliKeyif için yazılmıştır.
package com.kraptor

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import java.util.regex.Pattern

class WowXXX : MainAPI() {
    override var mainUrl = "https://www.wowxxx.to"
    override var name = "WowXXX"
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.NSFW)
    override val vpnStatus = VPNStatus.MightBeNeeded

    private val tag = "gizlikeyif_$name"
    private val ua = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
    )

    override val mainPage = mainPageOf(
        "$mainUrl/latest-updates/" to "Latest Updates",
        "$mainUrl/top-rated/" to "Top Rated",
        "$mainUrl/most-popular/" to "Most Popular",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data else "${request.data}$page/"
        val doc = app.get(url, headers = ua, timeout = 60).document

        val items = doc.select("div.item").mapNotNull { item ->
            val a = item.selectFirst("a[href*=/videos/]") ?: return@mapNotNull null
            val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val title = a.attr("title").trim().ifEmpty {
                item.selectFirst(".title")?.text()?.trim() ?: return@mapNotNull null
            }

            var poster = item.selectFirst("img")?.let { img ->
                img.attr("data-src").ifEmpty { img.attr("src") }
            }
            if (poster?.startsWith("//") == true) poster = "https:$poster"
            if (poster?.startsWith("/") == true) poster = "$mainUrl$poster"

            newMovieSearchResponse(title, href, TvType.NSFW) {
                this.posterUrl = poster
            }
        }

        return newHomePageResponse(
            HomePageList(request.name, items, isHorizontalImages = true),
            items.isNotEmpty()
        )
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val q = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "-")
        val url = if (page == 1) "$mainUrl/search/$q/relevance/" else "$mainUrl/search/$q/relevance/$page/"
        val document = app.get(url, headers = ua, timeout = 60).document

        val items = document.select("div.item").mapNotNull { item ->
            val a = item.selectFirst("a[href*=/videos/]") ?: return@mapNotNull null
            val href = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val title = a.attr("title").trim().ifEmpty {
                item.selectFirst(".title")?.text()?.trim() ?: return@mapNotNull null
            }

            var poster = item.selectFirst("img")?.let { img ->
                img.attr("data-src").ifEmpty { img.attr("src") }
            }
            if (poster?.startsWith("//") == true) poster = "https:$poster"
            if (poster?.startsWith("/") == true) poster = "$mainUrl$poster"

            newMovieSearchResponse(title, href, TvType.NSFW) {
                this.posterUrl = poster
            }
        }

        return items.toNewSearchResponseList()
    }

    override suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, headers = ua, timeout = 60).document
        val html = doc.html()
        val title = doc.title().trim().replace(" - wowxxx.to", "", true).trim()
            .ifBlank { doc.selectFirst("h1")?.text()?.trim() ?: return null }

        var poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
        if (poster.isNullOrBlank()) {
            poster = doc.selectFirst(".player-container img")?.attr("src")
        }

        val plotText = doc.selectFirst("meta[name=description]")?.attr("content")
        val tags = doc.select("div.item:has(span:contains(Categories)) a.link").map { it.text() }
        val actors = doc.select("div.item:has(span:contains(Pornstars)) a.btn_model").map { Actor(it.text()) }

        val recommendations = doc.select("div.item:has(a[href*=/videos/])").mapNotNull { item ->
            val a = item.selectFirst("a[href*=/videos/]") ?: return@mapNotNull null
            val recHref = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val recTitle = a.attr("title").trim().ifEmpty {
                item.selectFirst(".title")?.text()?.trim() ?: return@mapNotNull null
            }
            var recPoster = item.selectFirst("img")?.let { img ->
                img.attr("data-src").ifEmpty { img.attr("src") }
            }
            if (recPoster?.startsWith("//") == true) recPoster = "https:$recPoster"
            if (recPoster?.startsWith("/") == true) recPoster = "$mainUrl$recPoster"

            newMovieSearchResponse(recTitle, recHref, TvType.NSFW) {
                this.posterUrl = recPoster
            }
        }

        val videoId = doc.selectFirst("a.rate-like[data-video-id]")?.attr("data-video-id")
            ?: doc.selectFirst("span.video-favourites[data-object_id]")?.attr("data-object_id")
            ?: doc.selectFirst("#load-related[data-video-id]")?.attr("data-video-id")
            ?: Regex("""/(\\d{6,})_\\d+m\\.mp4""").find(html)?.groupValues?.get(1)
            ?: Regex("""img\\.wowxxx\\.to/\\d+/(\\d+)/""").find(html)?.groupValues?.get(1)

        val trailerUrl = videoId?.let { "https://cast.wowxxx.to/preview/$it.mp4" }

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = fixUrlNull(poster)
            this.plot = plotText
            this.tags = tags
            this.recommendations = recommendations
            addActors(actors)
            if (!trailerUrl.isNullOrBlank()) {
                addTrailer(trailerUrl, referer = mainUrl, addRaw = true, headers = ua)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false
        Log.d(tag, "loadLinks data = $data")
        try {
            val html = app.get(data, headers = ua, timeout = 60).text
            val matcher = Pattern.compile("src=['\"]([^'\"]*\\.mp4[^'\"]*)['\"]").matcher(html)
            var found = false
            while (matcher.find()) {
                var streamUrl = matcher.group(1) ?: continue
                if (streamUrl.startsWith("//")) streamUrl = "https:$streamUrl"

                val qualityMatch = Regex("(\\d{3,4})[mp]?\\.mp4").find(streamUrl)
                var qualityValue = Qualities.Unknown.value

                if (qualityMatch != null) {
                    val q = qualityMatch.groupValues[1].toIntOrNull() ?: 0
                    qualityValue = when (q) {
                        1080 -> Qualities.P1080.value
                        720 -> Qualities.P720.value
                        480 -> Qualities.P480.value
                        360 -> Qualities.P360.value
                        else -> Qualities.Unknown.value
                    }
                }

                callback.invoke(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = streamUrl,
                        type = ExtractorLinkType.VIDEO
                    ) {
                        this.quality = qualityValue
                        this.referer = mainUrl
                    }
                )
                found = true
            }
            return found
        } catch (e: Exception) {
            Log.d(tag, "loadLinks error: ${e.message}")
        }
        return false
    }
}
