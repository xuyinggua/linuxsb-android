package com.example.shaobing

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star as OutlinedStar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.shaobing.data.AppState
import com.example.shaobing.db.Bookmark
import com.example.shaobing.profile.ProfileManager
import com.example.shaobing.scripts.GMBridge
import com.example.shaobing.scripts.ScriptManager
import com.example.shaobing.ui.BookmarksScreen
import com.example.shaobing.ui.LoginScreen
import com.example.shaobing.ui.Prefs
import com.example.shaobing.ui.ProfilesScreen
import com.example.shaobing.ui.ScriptsScreen
import com.example.shaobing.ui.SettingsScreen
import com.example.shaobing.ui.ShaoBingTheme
import com.example.shaobing.web.BrowserChromeClient
import com.example.shaobing.web.BrowserClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FileChooserBridge {
    var callback: ValueCallback<Array<Uri>>? = null
    var launcher: ((Intent) -> Unit)? = null

    fun onFileChooser(cb: ValueCallback<Array<Uri>>, mime: String) {
        callback = cb
        launcher?.invoke(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = if (mime.isBlank()) "*/*" else mime
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        )
    }
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProfileManager.ensureDefaultProfile()
        ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
            ScriptManager.installBuiltinIfNeeded(applicationContext)
        }
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        setContent {
            ShaoBingTheme {
                MainApp()
            }
        }
    }

    override fun onDestroy() {
        AppState.webView?.destroy()
        AppState.webView = null
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp() {
    val context = LocalContext.current
    val activity = context as? Activity

    val fileChooserBridge = remember { FileChooserBridge() }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = fileChooserBridge.callback
        fileChooserBridge.callback = null
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val uris = ArrayList<Uri>()
            val data = result.data!!
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i).uri?.let { uris.add(it) }
                }
            } ?: data.data?.let { uris.add(it) }
            cb?.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
        } else {
            cb?.onReceiveValue(null)
        }
    }
    fileChooserBridge.launcher = { intent -> fileChooserLauncher.launch(intent) }

    val browserClient = remember {
        BrowserClient(context) { url ->
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }
    }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            
            ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
                v.onApplyWindowInsets(insets.toWindowInsets())
                insets
            }

            Prefs.applyFontZoom(this)
            webViewClient = browserClient
            if (activity != null) {
                webChromeClient = BrowserChromeClient(activity) { cb, mime ->
                    fileChooserBridge.onFileChooser(cb, mime)
                }
            }
            addJavascriptInterface(GMBridge(this), "SbApp")
            AppState.userAgent = settings.userAgentString
            AppState.webView = this
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var topBarTitle by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(100) }
    var showLogin by remember { mutableStateOf(false) }
    var loginTargetUrl by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var currentBookmarked by remember { mutableStateOf(false) }
    var scriptsEnabled by remember { mutableStateOf(Prefs.scriptsEnabled) }
    var loginCloseTick by remember { mutableIntStateOf(0) }
    var needsRefresh by remember { mutableStateOf(false) }

    fun refreshBookmarkState() {
        val url = AppState.currentUrl
        currentBookmarked = url.startsWith("http") && ShaoBingApp.db.bookmarkDao().byUrl(url) != null
    }

    LaunchedEffect(Unit) {
        AppState.onTitleChanged = { topBarTitle = it }
        AppState.onProgressChanged = { progress = it }
        AppState.onUrlChanged = { refreshBookmarkState() }
        AppState.onPageFinished = { webView ->
            needsRefresh = false
            ProfileManager.onMainLoginCheck(webView)
        }
        AppState.onAccountChanged = { accountName = ProfileManager.currentProfileName() }
        AppState.onScriptsChanged = { needsRefresh = true }
        accountName = ProfileManager.currentProfileName()
        browserClient.onLoginLink = { url ->
            val cur = ShaoBingApp.db.profileDao().current()
            if (cur != null && ProfileManager.isMultiProfileSupported() && ProfileManager.beginLogin(cur)) {
                loginTargetUrl = url
                showLogin = true
                true
            } else {
                false
            }
        }
        refreshBookmarkState()
        if (AppState.currentUrl.isBlank() || AppState.currentUrl == AppState.HOME_URL) {
            webView.loadUrl(AppState.HOME_URL)
        } else {
            webView.loadUrl(AppState.currentUrl)
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            refreshBookmarkState()
        }
    }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun toggleCurrentBookmark() {
        val url = AppState.currentUrl.ifBlank { return }
        val dao = ShaoBingApp.db.bookmarkDao()
        val existing = dao.byUrl(url)
        if (existing != null) {
            ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
                dao.delete(existing.id)
            }
            currentBookmarked = false
            toast("已取消收藏")
        } else {
            ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
                dao.insert(
                    Bookmark(
                        title = AppState.currentTitle.ifBlank { AppState.HOME_URL }.let {
                            if (url == AppState.HOME_URL) "烧饼社区" else AppState.currentTitle.ifBlank { url }
                        },
                        url = url
                    )
                )
            }
            currentBookmarked = true
            toast("已收藏当前页")
        }
    }

    BackHandler(enabled = true) {
        val wv = AppState.webView
        when {
            showLogin -> showLogin = false
            selectedTab != 0 -> selectedTab = 0
            wv != null && wv.canGoBack() -> wv.post { wv.goBack() }
            else -> activity?.finish()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            if (selectedTab == 0) {
                Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { AppState.goBack() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "后退")
                            }
                            IconButton(onClick = { AppState.goForward() }) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "前进")
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (accountName.isNotBlank()) "当前账号：$accountName" else "",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    topBarTitle.ifBlank { "烧饼社区" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { AppState.reload() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新")
                            }
                            IconButton(onClick = { toggleCurrentBookmark() }) {
                                Icon(
                                    if (currentBookmarked) Icons.Filled.Star else Icons.Outlined.OutlinedStar,
                                    contentDescription = if (currentBookmarked) "取消收藏" else "收藏当前页",
                                    tint = if (currentBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(onClick = {
                                AppState.currentUrl.takeIf { it.startsWith("http") }?.let { url ->
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }
                            }) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = "外部打开")
                            }
                        }
                        if (progress < 100) {
                            LinearProgressIndicator(
                                progress = progress / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            BottomNavBar(selected = selectedTab, showScripts = scriptsEnabled) { selectedTab = it }
        }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )

            if (selectedTab == 0 && needsRefresh) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "脚本已更改，刷新页面后生效",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f).padding(vertical = 10.dp)
                        )
                        TextButton(onClick = {
                            needsRefresh = false
                            AppState.reload()
                        }) {
                            Text("刷新")
                        }
                        IconButton(onClick = { needsRefresh = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭提示")
                        }
                    }
                }
            }

            when (selectedTab) {
                1 -> ScriptsScreen(onBack = { selectedTab = 0 })
                2 -> BookmarksScreen(
                    onBack = { selectedTab = 0 },
                    onOpen = { url ->
                        AppState.open(url)
                        selectedTab = 0
                    }
                )
                3 -> ProfilesScreen(
                    onBack = { selectedTab = 0 },
                    onOpenLogin = { showLogin = true },
                    refreshTick = loginCloseTick
                )
                4 -> SettingsScreen(
                    onBack = { selectedTab = 0 },
                    scriptsEnabled = scriptsEnabled,
                    onScriptsEnabledChanged = { enabled ->
                        scriptsEnabled = enabled
                        Prefs.scriptsEnabled = enabled
                        if (!enabled) {
                            ScriptManager.disableAll()
                            if (selectedTab == 1) selectedTab = 0
                        }
                    }
                )
            }
        }
    }

        if (showLogin) {
            LoginScreen(
                onClose = {
                    showLogin = false
                    loginCloseTick++
                },
                initialUrl = loginTargetUrl
            )
        }
    }
}

@Composable
private fun BottomNavBar(selected: Int, showScripts: Boolean, onSelect: (Int) -> Unit) {
    data class Item(val tab: Int, val label: String, val icon: ImageVector)

    val items = buildList {
        add(Item(0, "首页", Icons.Default.Home))
        if (showScripts) add(Item(1, "脚本", Icons.Default.Extension))
        add(Item(2, "书签", Icons.Default.Bookmarks))
        add(Item(3, "账号", Icons.Default.People))
        add(Item(4, "设置", Icons.Default.Settings))
    }

    Surface(tonalElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            items.forEach { item ->
                val isSelected = selected == item.tab
                val tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(item.tab) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = tint)
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint
                    )
                }
            }
        }
    }
}
