package com.welkinmoon.yamiboyou.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document


enum class YamiboSite(val displayName: String, val baseUrl: String) {
    BBS("论坛", "https://bbs.yamibo.com/"),
    NEW_SITE("新站", "https://www.yamibo.com/")
}

data class ContentItem(
    val title: String,
    val url: String,
    val subtitle: String? = null,
    val site: YamiboSite
)

interface SiteAdapter {
    val site: YamiboSite
    suspend fun loadFrontPage(): List<ContentItem>
}

class YamiboRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    private val adapters: Map<YamiboSite, SiteAdapter> = mapOf(
        YamiboSite.BBS to DiscuzBbsAdapter(client),
        YamiboSite.NEW_SITE to YamiboNewSiteAdapter(client)
    )

    suspend fun load(site: YamiboSite): List<ContentItem> =
        adapters.getValue(site).loadFrontPage()
}

private abstract class HtmlSiteAdapter(
    protected val client: OkHttpClient
) : SiteAdapter {
    protected suspend fun fetch(url: String): Document = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "YamiboYou/0.1 Android")
            .header("Accept-Language", "zh-CN,zh;q=0.9")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} while loading $url" }
            val body = response.body?.string().orEmpty()
            Jsoup.parse(body, url)
        }
    }

    protected fun distinct(items: List<ContentItem>): List<ContentItem> =
        items.distinctBy { it.url }.filter { it.title.isNotBlank() }
}

private class DiscuzBbsAdapter(client: OkHttpClient) : HtmlSiteAdapter(client) {
    override val site = YamiboSite.BBS

    override suspend fun loadFrontPage(): List<ContentItem> {
        val document = fetch("${site.baseUrl}forum.php?mobile=2")
        val selectors = listOf(
            "a.xst",
            "a[href*='forum.php?mod=viewthread']",
            "a[href*='thread-']"
        )
        val links = selectors.asSequence()
            .flatMap { selector -> document.select(selector).asSequence() }
            .toList()

        return distinct(links.map { link ->
            ContentItem(
                title = link.text().trim(),
                url = link.absUrl("href"),
                subtitle = link.closest("tbody, li, div")?.selectFirst(".by, .num, .xg1")?.text(),
                site = site
            )
        }).take(60)
    }
}

private class YamiboNewSiteAdapter(client: OkHttpClient) : HtmlSiteAdapter(client) {
    override val site = YamiboSite.NEW_SITE

    override suspend fun loadFrontPage(): List<ContentItem> {
        val document = fetch(site.baseUrl)
        val preferredSelectors = listOf(
            "article a[href]",
            ".card a[href]",
            "a[href*='/novel/']",
            "a[href*='/comic/']",
            "a[href*='/work']"
        )

        val preferred = preferredSelectors.asSequence()
            .flatMap { selector -> document.select(selector).asSequence() }
            .mapNotNull { link -> link.toContentItem(document) }
            .toList()

        if (preferred.isNotEmpty()) return distinct(preferred).take(60)

        // Fallback for layout changes: keep meaningful same-origin links and let the UI stay usable.
        return distinct(document.select("a[href]").mapNotNull { link ->
            val href = link.absUrl("href")
            val title = link.text().trim()
            if (!href.startsWith(site.baseUrl) || title.length < 4) null
            else ContentItem(title = title, url = href, site = site)
        }).take(60)
    }

    private fun org.jsoup.nodes.Element.toContentItem(document: Document): ContentItem? {
        val title = text().trim()
        val href = absUrl("href")
        if (title.length < 2 || href.isBlank()) return null
        val context = closest("article, li, .card, div")
        val subtitle = context?.selectFirst(".author, .meta, .desc, .description")?.text()
        return ContentItem(title = title, url = href, subtitle = subtitle, site = site)
    }
}
