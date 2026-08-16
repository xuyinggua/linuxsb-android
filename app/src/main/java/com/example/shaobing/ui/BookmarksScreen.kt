package com.example.shaobing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.shaobing.db.Bookmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    onOpen: (String) -> Unit
) {
    var bookmarks by remember { mutableStateOf(ShaoBingApp.db.bookmarkDao().all()) }
    var showAdd by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Bookmark?>(null) }

    SecondaryScreen(
        title = "书签",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加书签")
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "快捷链接",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                    )
                    QuickLinkRow(
                        label = "签到",
                        url = "https://linux.sb/daily_checkin",
                        onClick = { onOpen("https://linux.sb/daily_checkin") }
                    )
                    HorizontalDivider()
                }
                if (bookmarks.isEmpty()) {
                    item {
                        Text(
                            "还没有书签\n可在网页中点击右上角 ☆ 收藏当前页，或在此手动添加",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp)
                        )
                    }
                } else {
                    items(bookmarks, key = { it.id }) { b ->
                        BookmarkRow(
                            bookmark = b,
                            onOpen = { onOpen(b.url) },
                            onDelete = { deleting = b }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showAdd) {
        var url by remember { mutableStateOf("") }
        var title by remember { mutableStateOf("") }
        AlertInputDialog(
            onDismiss = { showAdd = false },
            onConfirm = {
                if (url.isNotBlank()) {
                    showAdd = false
                    ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
                        ShaoBingApp.db.bookmarkDao().insert(
                            Bookmark(
                                title = title.ifBlank { url },
                                url = if (url.startsWith("http")) url else "https://$url"
                            )
                        )
                        val updated = ShaoBingApp.db.bookmarkDao().all()
                        withContext(Dispatchers.Main) { bookmarks = updated }
                    }
                }
            },
            url = url,
            title = title,
            onUrlChange = { url = it },
            onTitleChange = { title = it }
        )
    }

    deleting?.let { b ->
        ConfirmDialog(
            title = "删除书签",
            message = "确定删除「${b.title}」吗？",
            onConfirm = {
                deleting = null
                ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
                    ShaoBingApp.db.bookmarkDao().delete(b.id)
                    val updated = ShaoBingApp.db.bookmarkDao().all()
                    withContext(Dispatchers.Main) { bookmarks = updated }
                }
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                bookmark.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                bookmark.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onOpen) {
            Icon(Icons.Default.OpenInNew, contentDescription = "打开")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun QuickLinkRow(
    label: String,
    url: String,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(Icons.Default.OpenInNew, contentDescription = "打开")
    }
}
