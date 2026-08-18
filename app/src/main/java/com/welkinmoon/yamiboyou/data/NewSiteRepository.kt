package com.welkinmoon.yamiboyou.data

import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class NewSiteRepository(
    private val client: OkHttpClient = YamiboHttp.newSiteClient
) {
    suspend fun loadNovelFeed(): NewSiteFeed {
        val doc = fetchDocument(client, "${YamiboSite.NEW_SITE.baseUrl}site/novel")
        return NewSiteFeed(
            recommendations = parseNamedSections(doc, listOf("编辑推荐", "原创推荐", "二次元同人推荐", "三次元同人推荐")),
            recentUpdates = parseRecentUpdates(doc),
            rankings = parseNamedSections(doc, listOf("原创月榜", "二次元同人月榜", "三次元同人月榜", "热度榜", "战力榜"))
        )
    }

    private fun parseRecentUpdates(doc: Document): List<NovelUpdate> {
        val heading = doc.select("h1, h2, h3, h4").firstOrNull { it.text().trim() == "最近更新" }
        val table = heading?.let(::nextTable) ?: doc.select("table").firstOrNull { table ->
            table.text().contains("作品") && table.text().contains("章节") && table.text().contains("作者")
        }
        return table?.select("tr")?.mapNotNull { row ->
            val cells = row.select("td")
            if (cells.size < 3) return@mapNotNull null
            val workLink = cells[0].selectFirst("a[href*='/novel/']") ?: return@mapNotNull null
            val work = workFromLink(workLink) ?: return@mapNotNull null
            val chapter = cells.getOrNull(1)?.selectFirst("a[href]")
            val author = cells.getOrNull(2)?.selectFirst("a[href]")
            NovelUpdate(
                work = work,
                chapterTitle = chapter?.text()?.trim()?.takeIf { it.isNotBlank() },
                chapterUrl = chapter?.absUrl("href")?.takeIf { it.isNotBlank() },
                author = author?.text()?.trim()?.takeIf { it.isNotBlank() } ?: cells.getOrNull(2)?.text()?.trim(),
                authorUrl = author?.absUrl("href")?.takeIf { it.isNotBlank() },
                updatedAt = cells.getOrNull(3)?.text()?.trim()?.takeIf { it.isNotBlank() }
            )
        }?.distinctBy { it.work.url to it.chapterUrl }.orEmpty()
    }

    private fun parseNamedSections(doc: Document, names: List<String>): List<NewSiteSection> =
        names.mapNotNull { name ->
            val heading = doc.select("h1, h2, h3, h4, h5").firstOrNull { it.text().trim() == name }
                ?: return@mapNotNull null
            val items = collectSectionLinks(heading)
                .mapNotNull(::workFromLink)
                .distinctBy { it.url }
                .take(if (name.contains("榜")) 10 else 8)
            if (items.isEmpty()) null else NewSiteSection(name, items)
        }

    private fun collectSectionLinks(heading: Element): List<Element> {
        val result = mutableListOf<Element>()
        var node = heading.nextElementSibling()
        while (node != null) {
            if (node.tagName().matches(Regex("h[1-5]"))) break
            result += node.select("a[href*='/novel/']")
            node = node.nextElementSibling()
        }
        if (result.isNotEmpty()) return result

        // Some layouts wrap the heading and cards inside a shared section container.
        val container = heading.closest("section, .section, .box, .block, div")
        return container?.select("a[href*='/novel/']").orEmpty()
    }

    private fun nextTable(heading: Element): Element? {
        var node = heading.nextElementSibling()
        while (node != null) {
            if (node.tagName() == "table") return node
            node.selectFirst("table")?.let { return it }
            if (node.tagName().matches(Regex("h[1-4]"))) return null
            node = node.nextElementSibling()
        }
        return null
    }

    private fun workFromLink(link: Element): NovelWork? {
        val url = link.absUrl("href")
        if (!url.contains("/novel/")) return null
        val title = link.text().trim().removePrefix("[原创]").removePrefix("[同人]").trim()
        if (title.length < 2) return null
        val context = link.closest("li, article, .card, .item, div")
        val summary = context?.selectFirst("p, .desc, .description, .intro")?.text()?.trim()?.takeIf {
            it.isNotBlank() && it != title
        }
        val category = Regex("\\[(原创|同人)]").find(link.text())?.groupValues?.getOrNull(1)
        return NovelWork(
            id = Regex("/novel/(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull(),
            title = title,
            url = url,
            summary = summary,
            category = category
        )
    }
}
