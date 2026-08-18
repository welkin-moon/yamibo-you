package com.welkinmoon.yamiboyou.data

import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class BbsRepository(
    private val client: OkHttpClient = YamiboHttp.bbsClient
) {
    suspend fun loadForums(): List<BbsForum> {
        val doc = fetchDocument(client, YamiboSite.BBS.baseUrl)
        val candidates = doc.select("a[href*='forum-'], a[href*='forum.php?mod=forumdisplay&fid=']")
        return candidates.mapNotNull { link ->
            val url = link.absUrl("href")
            val id = forumId(url) ?: return@mapNotNull null
            val name = link.text().trim()
            if (name.length < 2) return@mapNotNull null
            val container = link.closest(".fl_g, .fl_row, td, li, div")
            BbsForum(
                id = id,
                name = name,
                url = url,
                description = container?.selectFirst(".fl_i, .fl_by, .xg2")?.text()?.trim()?.takeIf { it.isNotBlank() },
                stats = container?.selectFirst(".fl_i, .fl_by")?.text()?.trim()?.takeIf { it.isNotBlank() }
            )
        }.distinctBy { it.id }
    }

    suspend fun loadFrontPageAsContent(): List<ContentItem> =
        loadForums().map { forum ->
            ContentItem(
                title = forum.name,
                url = forum.url,
                subtitle = forum.description ?: forum.stats,
                site = YamiboSite.BBS
            )
        }

    suspend fun loadForum(forum: BbsForum, page: Int = 1): BbsForumPage {
        val url = "${YamiboSite.BBS.baseUrl}forum-${forum.id}-$page.html"
        val doc = fetchDocument(client, url)
        val rows = doc.select("tbody[id^=normalthread_], tbody[id^=stickthread_]")
        val threads = rows.mapNotNull(::parseThreadRow).distinctBy { it.id }
        return BbsForumPage(
            forum = forum,
            threads = threads,
            currentPage = page,
            totalPages = parseTotalPages(doc)
        )
    }

    suspend fun loadThread(thread: BbsThread, page: Int = 1): BbsThreadPage {
        val url = "${YamiboSite.BBS.baseUrl}thread-${thread.id}-$page-1.html"
        val doc = fetchDocument(client, url)
        val postTables = doc.select("table[id^=pid], div[id^=post_]")
        val posts = postTables.mapNotNull { parsePost(it, thread) }.distinctBy { it.id }
        val formHash = doc.selectFirst("input[name=formhash]")?.attr("value")?.takeIf { it.isNotBlank() }
        return BbsThreadPage(
            threadId = thread.id,
            title = doc.selectFirst("#thread_subject")?.text()?.trim().takeUnless { it.isNullOrBlank() } ?: thread.title,
            url = url,
            posts = posts,
            currentPage = page,
            totalPages = parseTotalPages(doc),
            formHash = formHash,
            canReply = formHash != null && doc.selectFirst("form[id^=fastpostform], #fastpostform") != null
        )
    }

    private fun parseThreadRow(row: Element): BbsThread? {
        val titleLink = row.selectFirst("a.xst")
            ?: row.select("a[href*='thread-'], a[href*='viewthread']")
                .firstOrNull { threadId(it.absUrl("href")) != null }
            ?: return null
        val url = titleLink.absUrl("href")
        val id = threadId(url) ?: return null
        val authorCell = row.selectFirst("td.by")
        val stats = row.selectFirst("td.num")
        val statNumbers = stats?.select("a, em")?.mapNotNull { it.text().filter(Char::isDigit).toIntOrNull() }.orEmpty()
        return BbsThread(
            id = id,
            title = titleLink.text().trim(),
            url = url,
            author = authorCell?.selectFirst("cite a, cite")?.text()?.trim(),
            authorUrl = authorCell?.selectFirst("cite a")?.absUrl("href")?.takeIf { it.isNotBlank() },
            tag = row.selectFirst("em a, .common a")?.text()?.trim()?.takeIf { it.isNotBlank() },
            replies = statNumbers.getOrNull(0),
            views = statNumbers.getOrNull(1),
            lastReply = row.select("td.by").getOrNull(1)?.text()?.trim(),
            sticky = row.id().startsWith("stickthread_") || row.hasClass("stickthread")
        )
    }

    private fun parsePost(container: Element, thread: BbsThread): BbsPost? {
        val id = postId(container) ?: return null
        val message = container.selectFirst("#postmessage_$id, .t_f[id^=postmessage_], .t_f") ?: return null
        val author = container.selectFirst(".authi a.xw1, .authi a[href*='space-uid'], .pls .xw1")?.text()?.trim()
            ?: container.selectFirst(".authi")?.text()?.trim()
            ?: "匿名用户"
        val authorUrl = container.selectFirst(".authi a.xw1, .authi a[href*='space-uid']")?.absUrl("href")?.takeIf { it.isNotBlank() }
        val avatar = container.selectFirst(".avatar img")?.absUrl("src")?.takeIf { it.isNotBlank() }
        val time = container.selectFirst(".authi em, .pti .authi")?.text()?.replace("发表于", "")?.trim()?.takeIf { it.isNotBlank() }
        val floor = container.selectFirst(".pi strong a em, .pi strong a, .pi strong")?.text()?.trim()
            ?.takeIf { it.isNotBlank() } ?: "#"
        val imageUrls = message.select("img").mapNotNull { image ->
            sequenceOf("zoomfile", "file", "src")
                .map { image.absUrl(it) }
                .firstOrNull { it.isNotBlank() }
        }.distinct()

        val text = message.clone().apply {
            select("script, style").remove()
            select("img").forEach { it.after("\n[图片]\n") }
            select("br").after("\n")
            select("p, div, blockquote, li").append("\n")
        }.wholeText().replace(Regex("\n{3,}"), "\n\n").trim()

        return BbsPost(
            id = id,
            floor = floor,
            author = author,
            authorUrl = authorUrl,
            avatarUrl = avatar,
            time = time,
            text = text,
            imageUrls = imageUrls,
            isOriginalPoster = authorUrl != null && authorUrl == thread.authorUrl,
            fromMobile = container.selectFirst(".mobile-type, img[alt*=手机], img[title*=手机]") != null
        )
    }

    private fun parseTotalPages(doc: Document): Int {
        val pageText = doc.selectFirst(".pg label span, .pg label")?.text().orEmpty()
        val explicit = Regex("/\\s*(\\d+)\\s*页").find(pageText)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (explicit != null) return explicit.coerceAtLeast(1)
        return doc.select(".pg a").mapNotNull { it.text().trim().toIntOrNull() }.maxOrNull()?.coerceAtLeast(1) ?: 1
    }

    private fun forumId(url: String): Int? =
        Regex("forum-(\\d+)-").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("[?&]fid=(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun threadId(url: String): Long? =
        Regex("thread-(\\d+)-").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: Regex("[?&]tid=(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()

    private fun postId(container: Element): Long? {
        val direct = Regex("(?:pid|post_)(\\d+)").find(container.id())?.groupValues?.getOrNull(1)?.toLongOrNull()
        if (direct != null) return direct
        return container.selectFirst("[id^=postmessage_]")?.id()
            ?.removePrefix("postmessage_")?.toLongOrNull()
    }
}
