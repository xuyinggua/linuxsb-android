package com.example.shaobing.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shaobing.ShaoBingApp
import com.example.shaobing.db.Userscript
import com.example.shaobing.scripts.ScriptManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ScriptsScreen(onBack: () -> Unit) {
    var scripts by remember { mutableStateOf(ScriptManager.all(ShaoBingApp.appContext)) }
    var showAddDialog by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<Userscript?>(null) }

    fun refresh() {
        scripts = ScriptManager.all(ShaoBingApp.appContext)
    }

    SecondaryScreen(
        title = "油猴脚本",
        onBack = onBack,
        actions = {
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
                if (scripts.isEmpty()) {
                    item {
                        Text(
                            "还没有安装脚本\n点击右上角 + ，输入 GreasyFork 脚本地址即可安装",
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
                    val updated = ScriptManager.all(ShaoBingApp.appContext)
                    withContext(Dispatchers.Main) { scripts = updated }
                }
            },
            onDismiss = { deleting = null }
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
