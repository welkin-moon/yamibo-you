package com.welkinmoon.yamiboyou.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap

interface SiteAdapter {
    val site: YamiboSite
    suspend fun loadFrontPage(): List<ContentItem>
}

/**
 * Cookie storage deliberately lives per-site. Yamibo's Discuz BBS and the new site
 * are separate web applications and must not accidentally share authentication state.
 * Persistence can replace this implementation later without changing repositories.
 */
class SiteCookieJar : CookieJar {
    private val cookies = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val bucket = this.cookies.getOrPut(url.host) { mutableListOf() }
        synchronized(bucket) {
            cookies.forEach { incoming ->
                bucket.removeAll { it.name == incoming.name && it.path == incoming.path }
                if (incoming.expiresAt > System.currentTimeMillis()) bucket += incoming
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()
        cookies.forEach { (_, bucket) ->
            synchronized(bucket) {
                bucket.removeAll { it.expiresAt <= now }
                result += bucket.filter { it.matches(url) }
            }
        }
        return result
    }

    fun clear() = cookies.clear()
}

object YamiboHttp {
    val bbsCookies = SiteCookieJar()
    val newSiteCookies = SiteCookieJar()

    val bbsClient: OkHttpClient = client(bbsCookies)
    val newSiteClient: OkHttpClient = client(newSiteCookies)

    private fun client(cookieJar: CookieJar) = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
}

class YamiboRepository(
    newSiteClient: OkHttpClient = YamiboHttp.newSiteClient
) {
    private val newSiteAdapter: SiteAdapter = YamiboNewSiteAdapter(newSiteClient)

    suspend fun load(site: YamiboSite): List<ContentItem> = when (site) {
        YamiboSite.BBS -> BbsRepository().loadFrontPageAsContent()
        YamiboSite.NEW_SITE -> newSiteAdapter.loadFrontPage()
    }
}

internal abstract class HtmlSiteAdapter(
    protected val client: OkHttpClient
) : SiteAdapter {
    protected suspend fun fetch(url: String): Document = fetchDocument(client, url)

    protected fun distinct(items: List<ContentItem>): List<ContentItem> =
        items.distinctBy { it.url }.filter { it.title.isNotBlank() }
}

internal suspend fun fetchDocument(client: OkHttpClient, url: String): Document =
    withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "YamiboYou/0.2 Android")
            .header("Accept-Language", "zh-CN,zh;q=0.9,zh-TW;q=0.8")
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} while loading $url" }
            val body = response.body?.string().orEmpty()
            Jsoup.parse(body, response.request.url.toString())
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
            .mapNotNull { link -> link.toContentItem() }
            .toList()

        if (preferred.isNotEmpty()) return distinct(preferred).take(60)

        return distinct(document.select("a[href]").mapNotNull { link ->
            val href = link.absUrl("href")
            val title = link.text().trim()
            if (!href.startsWith(site.baseUrl) || title.length < 4) null
            else ContentItem(title = title, url = href, site = site)
        }).take(60)
    }

    private fun org.jsoup.nodes.Element.toContentItem(): ContentItem? {
        val title = text().trim()
        val href = absUrl("href")
        if (title.length < 2 || href.isBlank()) return null
        val context = closest("article, li, .card, div")
        val subtitle = context?.selectFirst(".author, .meta, .desc, .description")?.text()
        return ContentItem(title = title, url = href, subtitle = subtitle, site = site)
    }
}
