package com.example.shaobing.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import com.example.shaobing.ShaoBingApp
import com.example.shaobing.data.AppState
import com.example.shaobing.db.Userscript
import com.example.shaobing.scripts.ScriptManager
import com.example.shaobing.scripts.ScriptManager.ScriptSearchResult
import com.example.shaobing.scripts.ScriptManager.ScriptSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScriptsScreen(onBack: () -> Unit) {
    var scripts by remember { mutableStateOf(ScriptManager.all(ShaoBingApp.appContext)) }
    var builtins by remember { mutableStateOf(ScriptManager.builtinScripts(ShaoBingApp.appContext)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Userscript?>(null) }
    var installingBuiltin by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun refresh() {
        scripts = ScriptManager.all(ShaoBingApp.appContext)
        builtins = ScriptManager.builtinScripts(ShaoBingApp.appContext)
    }

    fun markScriptsChanged() {
        AppState.onScriptsChanged?.invoke()
    }

    SecondaryScreen(
        title = "油猴脚本",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showSearch = true }, enabled = !downloading) {
                Icon(Icons.Default.Search, contentDescription = "搜索脚本")
            }
            IconButton(onClick = { showAddDialog = true }, enabled = !downloading) {
                Icon(Icons.Default.Add, contentDescription = "添加脚本")
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "脚本功能未经过测试，可能会有未知问题，请谨慎使用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                item {
                    Text(
                        "内置脚本",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                    )
                }
                items(builtins, key = { it.asset }) { b ->
                    BuiltinRow(
                        builtin = b,
                        installing = installingBuiltin == b.asset,
                        onInstall = {
                            installingBuiltin = b.asset
                            ShaoBingApp.applicationScope.launch {
                                try {
                                    val installed = ScriptManager.installBuiltin(ShaoBingApp.appContext, b.asset)
                                    refresh()
                                    if (installed) {
                                        toast("已安装「${b.name}」")
                                        markScriptsChanged()
                                    } else {
                                        toast("该脚本已安装")
                                    }
                                } catch (e: Exception) {
                                    toast("安装失败：${e.message}")
                                } finally {
                                    installingBuiltin = null
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
                if (scripts.isEmpty()) {
                    item {
                        Text(
                            "还没有安装第三方脚本\n可添加下方内置脚本，或点击右上角搜索 / + 安装",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp)
                        )
                    }
                } else {
                    items(scripts, key = { it.id }) { s ->
                        UserscriptRow(
                            script = s,
                            onToggle = { enabled ->
                                ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
                                    ShaoBingApp.db.userscriptDao().setEnabled(s.id, enabled)
                                }
                                scripts = scripts.map {
                                    if (it.id == s.id) it.copy(enabled = enabled) else it
                                }
                                markScriptsChanged()
                            },
                            onDelete = { deleting = s }
                        )
                        HorizontalDivider()
                    }
                }
            }

            if (downloading) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text("正在下载并解析脚本…", modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        TextInputDialog(
            title = "安装油猴脚本",
            label = "脚本地址",
            placeholder = "https://greasyfork.org/zh-CN/scripts/xxx",
            onDismiss = { showAddDialog = false },
            onConfirm = { url ->
                showAddDialog = false
                downloading = true
                error = null
                ShaoBingApp.applicationScope.launch {
                    try {
                        val result = ScriptManager.downloadFromUrl(url)
                        refresh()
                    } catch (e: ScriptManager.DownloadError) {
                        error = e.message
                    } catch (e: Exception) {
                        error = "安装失败：${e.message}"
                    } finally {
                        downloading = false
                    }
                }
            }
        )
    }

    error?.let { msg ->
        ConfirmDialog(
            title = "安装失败",
            message = msg,
            onConfirm = { error = null },
            onDismiss = { error = null }
        )
    }

    deleting?.let { s ->
        ConfirmDialog(
            title = "删除脚本",
            message = "确定删除脚本「${s.name}」吗？将同时清除其本地数据。",
            onConfirm = {
                deleting = null
                ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
                    ShaoBingApp.db.userscriptDao().delete(s.id)
                    ShaoBingApp.db.gmValueDao().clear("${s.namespace ?: "default"}::${s.name}")
                    withContext(Dispatchers.Main) { refresh() }
                    markScriptsChanged()
                }
            },
            onDismiss = { deleting = null }
        )
    }

    if (showSearch) {
        ScriptSearchDialog(
            installed = scripts.mapNotNull { it.sourceUrl.takeIf { u -> u.isNotBlank() } }.toSet(),
            onDismiss = { showSearch = false },
            onInstalled = {
                refresh()
                markScriptsChanged()
            }
        )
    }
}

@Composable
private fun UserscriptRow(
    script: Userscript,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                script.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                (script.description ?: script.sourceUrl).ifBlank { script.sourceUrl },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(max = 20.dp)
            )
        }
        Switch(checked = script.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BuiltinRow(
    builtin: ScriptManager.BuiltinInfo,
    installing: Boolean,
    onInstall: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                builtin.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    if (!builtin.version.isNullOrBlank()) append("v${builtin.version} · ")
                    append(builtin.description ?: "内置脚本")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        if (installing) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else if (builtin.installed) {
            Text("已安装", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onInstall) { Text("安装") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScriptSearchDialog(
    installed: Set<String>,
    onDismiss: () -> Unit,
    onInstalled: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(ScriptSource.GREASYFORK) }
    var results by remember { mutableStateOf<List<ScriptSearchResult>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var localInstalled by remember { mutableStateOf(installed) }
    var installing by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun doSearch() {
        if (query.isBlank() || searching) return
        searching = true
        error = null
        ShaoBingApp.applicationScope.launch {
            try {
                results = ScriptManager.searchScripts(source, query)
            } catch (e: Exception) {
                error = "搜索失败：${e.message}"
                results = null
            } finally {
                searching = false
            }
        }
    }

    fun install(r: ScriptSearchResult) {
        if (installing != null) return
        installing = r.codeUrl
        ShaoBingApp.applicationScope.launch {
            try {
                ScriptManager.downloadFromUrl(r.pageUrl)
                localInstalled = localInstalled + r.pageUrl + r.codeUrl
                toast("已安装「${r.name}」")
                onInstalled()
            } catch (e: ScriptManager.DownloadError) {
                toast(e.message ?: "安装失败")
            } catch (e: Exception) {
                toast("安装失败：${e.message}")
            } finally {
                installing = null
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)
        ) {
            Column(Modifier.padding(top = 12.dp, bottom = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "搜索脚本",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    ScriptSource.entries.forEach { s ->
                        FilterChip(
                            selected = source == s,
                            onClick = {
                                source = s
                                results = null
                                error = null
                            },
                            label = { Text(s.label) },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("输入关键词，如 dark、视频下载") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { doSearch() }, enabled = !searching && query.isNotBlank()) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    when {
                        searching -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        error != null -> Text(
                            error ?: "",
                            Modifier.align(Alignment.Center).padding(24.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                        results == null -> Text(
                            "输入关键词搜索 ${ScriptSource.entries.joinToString(" / ") { it.label }} 的脚本，点击「安装」即可下载",
                            Modifier.align(Alignment.Center).padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        results!!.isEmpty() -> Text(
                            "没有找到相关脚本",
                            Modifier.align(Alignment.Center).padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> {
                            val list = results!!
                            LazyColumn(Modifier.fillMaxSize()) {
                                items(list, key = { it.codeUrl }) { r ->
                                    SearchResultRow(
                                        result = r,
                                        installed = r.pageUrl in localInstalled || r.codeUrl in localInstalled,
                                        installing = installing == r.codeUrl,
                                        onInstall = { install(r) }
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: ScriptSearchResult,
    installed: Boolean,
    installing: Boolean,
    onInstall: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                result.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    if (result.version.isNotBlank()) append("v${result.version} · ")
                    append("安装 ${result.totalInstalls}")
                    if (result.description.isNotBlank()) append("\n").append(result.description)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(max = 60.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        if (installed) {
            Text("已安装", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        } else if (installing) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            TextButton(onClick = onInstall) { Text("安装") }
        }
    }
}
