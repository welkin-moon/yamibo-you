package com.welkinmoon.yamiboyou.data

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

data class BbsForum(
    val id: Int,
    val name: String,
    val url: String,
    val description: String? = null,
    val stats: String? = null
)

data class BbsThread(
    val id: Long,
    val title: String,
    val url: String,
    val author: String? = null,
    val authorUrl: String? = null,
    val tag: String? = null,
    val replies: Int? = null,
    val views: Int? = null,
    val lastReply: String? = null,
    val sticky: Boolean = false
)

data class BbsForumPage(
    val forum: BbsForum,
    val threads: List<BbsThread>,
    val currentPage: Int = 1,
    val totalPages: Int = 1
)

data class BbsPost(
    val id: Long,
    val floor: String,
    val author: String,
    val authorUrl: String? = null,
    val avatarUrl: String? = null,
    val time: String? = null,
    val text: String,
    val imageUrls: List<String> = emptyList(),
    val isOriginalPoster: Boolean = false,
    val fromMobile: Boolean = false
)

data class BbsThreadPage(
    val threadId: Long,
    val title: String,
    val url: String,
    val posts: List<BbsPost>,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val formHash: String? = null,
    val canReply: Boolean = false
)

data class NovelWork(
    val id: Long?,
    val title: String,
    val url: String,
    val summary: String? = null,
    val category: String? = null
)

data class NovelUpdate(
    val work: NovelWork,
    val chapterTitle: String? = null,
    val chapterUrl: String? = null,
    val author: String? = null,
    val authorUrl: String? = null,
    val updatedAt: String? = null
)

data class NewSiteSection(
    val title: String,
    val items: List<NovelWork>
)

data class NewSiteFeed(
    val recommendations: List<NewSiteSection>,
    val recentUpdates: List<NovelUpdate>,
    val rankings: List<NewSiteSection>
)
