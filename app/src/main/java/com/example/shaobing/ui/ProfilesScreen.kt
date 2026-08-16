package com.example.shaobing.ui

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.shaobing.ShaoBingApp
import com.example.shaobing.data.AppState
import com.example.shaobing.db.UserProfile
import com.example.shaobing.profile.ProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfilesScreen(onBack: () -> Unit, onOpenLogin: () -> Unit, refreshTick: Int = 0) {
    var profiles by remember { mutableStateOf(ShaoBingApp.db.profileDao().all()) }
    var switching by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<UserProfile?>(null) }
    var deleting by remember { mutableStateOf<UserProfile?>(null) }

    LaunchedEffect(refreshTick) {
        if (refreshTick > 0) {
            profiles = ShaoBingApp.db.profileDao().all()
        }
    }

    SecondaryScreen(
        title = "账号（快速切换）",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建账号")
            }
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "每个账号拥有独立的登录状态（Cookie），点击即可一键切换；未登录的账号点击「登录」按钮登录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(profiles, key = { it.id }) { p ->
                    ProfileRow(
                        profile = p,
                        current = p.isCurrent,
                        onSwitch = {
                            if (!p.isCurrent && !switching) {
                                switching = true
                                val wv = AppState.webView
                                if (wv != null) {
                                    ProfileManager.switchTo(p, wv) {
                                        profiles = ShaoBingApp.db.profileDao().all()
                                        switching = false
                                    }
                                } else {
                                    val dao = ShaoBingApp.db.profileDao()
                                    ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
                                        for (x in dao.all()) dao.setCurrent(x.id, x.id == p.id)
                                        val updated = dao.all()
                                        withContext(Dispatchers.Main) {
                                            profiles = updated
                                            switching = false
                                            ProfileManager.notifyAccountChanged()
                                        }
                                    }
                                }
                            }
                        },
                        onLogin = {
                            val wv = AppState.webView
                            if (wv != null) {
                                if (ProfileManager.beginLogin(p)) {
                                    onOpenLogin()
                                } else if (p.isCurrent) {
                                    ProfileManager.openLoginInMain(wv)
                                } else if (!switching) {
                                    switching = true
                                    ProfileManager.switchTo(p, wv) {
                                        ProfileManager.openLoginInMain(wv)
                                        profiles = ShaoBingApp.db.profileDao().all()
                                        switching = false
                                    }
                                }
                            }
                        },
                        onRename = { renaming = p },
                        onDelete = { deleting = p }
                    )
                    HorizontalDivider()
                }
            }
            if (switching) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text("正在切换账号…", modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }

    if (showAdd) {
        TextInputDialog(
            title = "新建账号",
            label = "账号名称（备注，可不填）",
            placeholder = "留空则自动命名",
            allowEmpty = true,
            onConfirm = { name ->
                val finalName = name.ifBlank { "账号 ${profiles.size + 1}" }
                showAdd = false
                ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
                    ShaoBingApp.db.profileDao().insert(UserProfile(name = finalName))
                    val updated = ShaoBingApp.db.profileDao().all()
                    withContext(Dispatchers.Main) {
                        profiles = updated
                        ProfileManager.notifyAccountChanged()
                    }
                }
            },
            onDismiss = { showAdd = false }
        )
    }

    renaming?.let { p ->
        TextInputDialog(
            title = "重命名账号",
            label = "账号名称",
            initialValue = p.name,
            onConfirm = { name ->
                renaming = null
                ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
                    ShaoBingApp.db.profileDao().rename(p.id, name)
                    val updated = ShaoBingApp.db.profileDao().all()
                    withContext(Dispatchers.Main) {
                        profiles = updated
                        ProfileManager.notifyAccountChanged()
                    }
                }
            },
            onDismiss = { renaming = null }
        )
    }

    deleting?.let { p ->
        ConfirmDialog(
            title = "删除账号",
            message = "删除「${p.name}」后其登录状态将无法恢复，确定删除吗？",
            onConfirm = {
                deleting = null
                ShaoBingApp.applicationScope.launch(Dispatchers.IO) {
                    val dao = ShaoBingApp.db.profileDao()
                    dao.delete(p.id)
                    ShaoBingApp.db.profileSnapshotDao().delete(p.id)
                    if (dao.count() == 0) {
                        dao.insert(UserProfile(name = "默认账号", isCurrent = true))
                    } else if (p.isCurrent) {
                        dao.all().firstOrNull()?.let { dao.setCurrent(it.id, true) }
                    }
                    val updated = dao.all()
                    withContext(Dispatchers.Main) {
                        profiles = updated
                        ProfileManager.notifyAccountChanged()
                    }
                }
            },
            onDismiss = { deleting = null }
        )
    }
}

@Composable
private fun ProfileRow(
    profile: UserProfile,
    current: Boolean,
    onSwitch: () -> Unit,
    onLogin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val loggedIn = ProfileManager.isLoggedIn(profile)
    val subtitle = buildString {
        if (loggedIn) {
            if (!profile.username.isNullOrBlank()) append("@").append(profile.username)
            if (!profile.uid.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append("UID ").append(profile.uid)
            }
            if (current) append(" · 当前账号")
        } else {
            append(if (current) "当前账号（未登录）" else "未登录")
        }
    }

    Surface(
        onClick = onSwitch,
        color = if (current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (current) {
                Icon(Icons.Default.Check, contentDescription = "当前账号")
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!loggedIn) {
                TextButton(onClick = onLogin) {
                    Text("登录")
                }
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "重命名")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
