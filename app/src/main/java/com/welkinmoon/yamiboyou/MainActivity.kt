package com.welkinmoon.yamiboyou

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.welkinmoon.yamiboyou.data.BbsForum
import com.welkinmoon.yamiboyou.data.BbsForumPage
import com.welkinmoon.yamiboyou.data.BbsPost
import com.welkinmoon.yamiboyou.data.BbsRepository
import com.welkinmoon.yamiboyou.data.BbsThread
import com.welkinmoon.yamiboyou.data.BbsThreadPage
import com.welkinmoon.yamiboyou.data.ContentItem
import com.welkinmoon.yamiboyou.data.YamiboRepository
import com.welkinmoon.yamiboyou.data.YamiboSite

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { YamiboYouApp() }
    }
}

private sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>
}

private sealed interface BbsScreen {
    data object Forums : BbsScreen
    data class Forum(val forum: BbsForum, val page: Int = 1) : BbsScreen
    data class Thread(val forum: BbsForum, val thread: BbsThread, val page: Int = 1) : BbsScreen
}

@Composable
private fun YamiboYouApp() {
    val context = LocalContext.current
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(colorScheme = colorScheme) { YamiboHome() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YamiboHome() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var bbsScreen by remember { mutableStateOf<BbsScreen>(BbsScreen.Forums) }
    var refreshToken by remember { mutableIntStateOf(0) }

    val title = when {
        selectedTab == 1 -> "百合会新站"
        selectedTab == 2 -> "我的"
        bbsScreen is BbsScreen.Forum -> (bbsScreen as BbsScreen.Forum).forum.name
        bbsScreen is BbsScreen.Thread -> (bbsScreen as BbsScreen.Thread).thread.title
        else -> "百合会论坛"
    }
    val canGoBack = selectedTab == 0 && bbsScreen !is BbsScreen.Forums

    fun navigateBack() {
        bbsScreen = when (val current = bbsScreen) {
            is BbsScreen.Thread -> BbsScreen.Forum(current.forum)
            is BbsScreen.Forum -> BbsScreen.Forums
            BbsScreen.Forums -> BbsScreen.Forums
        }
    }

    BackHandler(enabled = canGoBack) { navigateBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (selectedTab < 2) {
                            Text(
                                if (selectedTab == 0) "bbs.yamibo.com" else "www.yamibo.com",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = ::navigateBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (selectedTab < 2) {
                        IconButton(onClick = { refreshToken++ }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Outlined.Forum, null) },
                    label = { Text("论坛") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Outlined.AutoStories, null) },
                    label = { Text("新站") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Outlined.AccountCircle, null) },
                    label = { Text("我的") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> BbsPane(
                    screen = bbsScreen,
                    refreshToken = refreshToken,
                    onOpenForum = { bbsScreen = BbsScreen.Forum(it) },
                    onOpenThread = { forum, thread -> bbsScreen = BbsScreen.Thread(forum, thread) },
                    onOpenForumPage = { forum, page -> bbsScreen = BbsScreen.Forum(forum, page) },
                    onOpenThreadPage = { forum, thread, page -> bbsScreen = BbsScreen.Thread(forum, thread, page) }
                )
                1 -> NewSitePane(refreshToken)
                else -> ProfilePlaceholder()
            }
        }
    }
}

@Composable
private fun BbsPane(
    screen: BbsScreen,
    refreshToken: Int,
    onOpenForum: (BbsForum) -> Unit,
    onOpenThread: (BbsForum, BbsThread) -> Unit,
    onOpenForumPage: (BbsForum, Int) -> Unit,
    onOpenThreadPage: (BbsForum, BbsThread, Int) -> Unit
) {
    val repository = remember { BbsRepository() }
    when (screen) {
        BbsScreen.Forums -> ForumIndex(repository, refreshToken, onOpenForum)
        is BbsScreen.Forum -> ThreadIndex(repository, screen, refreshToken, onOpenThread, onOpenForumPage)
        is BbsScreen.Thread -> ThreadReader(repository, screen, refreshToken, onOpenThreadPage)
    }
}

@Composable
private fun ForumIndex(repository: BbsRepository, refreshToken: Int, onOpenForum: (BbsForum) -> Unit) {
    var state by remember { mutableStateOf<LoadState<List<BbsForum>>>(LoadState.Loading) }
    LaunchedEffect(refreshToken) {
        state = LoadState.Loading
        state = runCatching { repository.loadForums() }.fold(
            { LoadState.Ready(it) },
            { LoadState.Failed(it.message ?: "论坛版块加载失败") }
        )
    }
    LoadBox(state) { forums ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(forums, key = { it.id }) { forum ->
                Card(onClick = { onOpenForum(forum) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(forum.name, style = MaterialTheme.typography.titleMedium)
                        forum.description?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadIndex(
    repository: BbsRepository,
    screen: BbsScreen.Forum,
    refreshToken: Int,
    onOpenThread: (BbsForum, BbsThread) -> Unit,
    onOpenPage: (BbsForum, Int) -> Unit
) {
    var state by remember(screen.forum.id, screen.page) { mutableStateOf<LoadState<BbsForumPage>>(LoadState.Loading) }
    LaunchedEffect(screen.forum.id, screen.page, refreshToken) {
        state = LoadState.Loading
        state = runCatching { repository.loadForum(screen.forum, screen.page) }.fold(
            { LoadState.Ready(it) }, { LoadState.Failed(it.message ?: "主题列表加载失败") }
        )
    }
    LoadBox(state) { page ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Pager(page.currentPage, page.totalPages) { onOpenPage(screen.forum, it) } }
            items(page.threads, key = { it.id }) { thread ->
                Card(onClick = { onOpenThread(screen.forum, thread) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (thread.sticky) {
                                Text("置顶", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                            }
                            thread.tag?.let {
                                Text("[$it]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(thread.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            thread.author?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            thread.replies?.let { Text("回复 $it", style = MaterialTheme.typography.bodySmall) }
                            thread.views?.let { Text("浏览 $it", style = MaterialTheme.typography.bodySmall) }
                        }
                        thread.lastReply?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { Pager(page.currentPage, page.totalPages) { onOpenPage(screen.forum, it) } }
        }
    }
}

@Composable
private fun ThreadReader(
    repository: BbsRepository,
    screen: BbsScreen.Thread,
    refreshToken: Int,
    onOpenPage: (BbsForum, BbsThread, Int) -> Unit
) {
    var state by remember(screen.thread.id, screen.page) { mutableStateOf<LoadState<BbsThreadPage>>(LoadState.Loading) }
    LaunchedEffect(screen.thread.id, screen.page, refreshToken) {
        state = LoadState.Loading
        state = runCatching { repository.loadThread(screen.thread, screen.page) }.fold(
            { LoadState.Ready(it) }, { LoadState.Failed(it.message ?: "帖子加载失败") }
        )
    }
    LoadBox(state) { page ->
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Pager(page.currentPage, page.totalPages) { onOpenPage(screen.forum, screen.thread, it) }
                    if (page.canReply) {
                        Text("已检测到登录会话与 formhash，可在下一阶段接原生回复。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(page.posts, key = { it.id }) { post ->
                PostCard(post)
                HorizontalDivider()
            }
            item {
                Box(Modifier.padding(16.dp)) {
                    Pager(page.currentPage, page.totalPages) { onOpenPage(screen.forum, screen.thread, it) }
                }
            }
        }
    }
}

@Composable
private fun PostCard(post: BbsPost) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(post.author, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (post.isOriginalPoster) Text("楼主", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (post.fromMobile) Text("手机", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
                post.time?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(post.floor, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        SelectionContainer {
            Text(post.text.ifBlank { "（无文本内容）" }, style = MaterialTheme.typography.bodyLarge)
        }
        post.imageUrls.forEachIndexed { index, url ->
            AssistChip(
                onClick = { uriHandler.openUri(url) },
                label = { Text("查看图片 ${index + 1}") },
                leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun Pager(current: Int, total: Int, onPage: (Int) -> Unit) {
    if (total <= 1) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = { onPage(current - 1) }, enabled = current > 1) { Text("上一页") }
        Text("$current / $total", style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = { onPage(current + 1) }, enabled = current < total) { Text("下一页") }
    }
}

@Composable
private fun NewSitePane(refreshToken: Int) {
    val repository = remember { YamiboRepository() }
    var state by remember { mutableStateOf<LoadState<List<ContentItem>>>(LoadState.Loading) }
    LaunchedEffect(refreshToken) {
        state = LoadState.Loading
        state = runCatching { repository.load(YamiboSite.NEW_SITE) }.fold(
            { LoadState.Ready(it) }, { LoadState.Failed(it.message ?: "新站加载失败") }
        )
    }
    LoadBox(state) { items -> ContentList(items) }
}

@Composable
private fun <T> LoadBox(state: LoadState<T>, content: @Composable (T) -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            LoadState.Loading -> CircularProgressIndicator()
            is LoadState.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text("加载失败", style = MaterialTheme.typography.titleMedium)
                Text(state.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is LoadState.Ready -> content(state.value)
        }
    }
}

@Composable
private fun ContentList(items: List<ContentItem>) {
    val uriHandler = LocalUriHandler.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂时没有解析到内容") }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.url }) { item ->
            Card(onClick = { uriHandler.openUri(item.url) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                    item.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilePlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Outlined.AccountCircle, contentDescription = null)
        Text("账号中心将在下一阶段接入")
        Text(
            "论坛与新站使用独立 CookieJar；登录、绑定和持久化会建立在这层 session abstraction 上。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
