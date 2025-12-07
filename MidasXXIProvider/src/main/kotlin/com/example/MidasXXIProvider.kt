package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import org.jsoup.nodes.Element

class MidasXXIProvider : MainAPI() {
    override var mainUrl = "https://ubi.ac.id"
    override var name = "MidasXXI"
    override var lang = "id"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/movies/page/" to "Movies",
        "$mainUrl/tvshows/page/" to "TV Series",
        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/anime/page/" to "Anime",
        "$mainUrl/genre/drama-korea/page/" to "Drama Korea"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + page).document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(HomePageList(request.name, home, false), true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h3 a, h2 a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href") ?: return null)
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src") ?: this.selectFirst("img")?.attr("src"))
        val quality = this.selectFirst("span.quality")?.text()?.trim()
        val type = if (href.contains("/tvshows/")) TvType.TvSeries else TvType.Movie
        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.quality = getQualityFromString(quality)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        return document.select("article.item").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
        val description = document.selectFirst("div[itemprop=description]")?.text()?.trim()
        val year = document.selectFirst("span.year")?.text()?.toIntOrNull()
        val rating = document.selectFirst("span[itemprop=ratingValue]")?.text()?.toRatingInt()
        val tags = document.select("span.genre a").map { it.text() }
        val actors = document.select("div.person").mapNotNull {
            val name = it.selectFirst("a")?.text() ?: return@mapNotNull null
            Actor(name, fixUrlNull(it.selectFirst("img")?.attr("src")))
        }
        val tvType = if (url.contains("/tvshows/")) TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("ul.episodios li").mapNotNull { ep ->
                val epTitle = ep.selectFirst("a")?.text()?.trim() ?: return@mapNotNull null
                val epHref = fixUrl(ep.selectFirst("a")?.attr("href") ?: return@mapNotNull null)
                val nums = ep.selectFirst("div.numerando")?.text()?.split("-") ?: return@mapNotNull null
                Episode(epHref, epTitle, nums[0].trim().toIntOrNull(), nums[1].trim().toIntOrNull())
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("iframe").forEach { 
            loadExtractor(fixUrl(it.attr("src")), subtitleCallback, callback)
        }
        document.select("ul#playeroptionsul li").forEach {
            val post = it.attr("data-post")
            val nume = it.attr("data-nume")
            val type = it.attr("data-type")
            if (post.isNotEmpty()) {
                try {
                    val res = app.post("$mainUrl/wp-admin/admin-ajax.php",
                        data = mapOf("action" to "doo_player_ajax", "post" to post, "nume" to nume, "type" to type)
                    ).parsedSafe<Response>()
                    res?.embed_url?.let { url -> loadExtractor(url, subtitleCallback, callback) }
                } catch (_: Exception) {}
            }
        }
        return true
    }

    data class Response(val embed_url: String?)
}
ENDOFFILE
