package com.example.shaobing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shaobing.data.AppState

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    scriptsEnabled: Boolean,
    onScriptsEnabledChanged: (Boolean) -> Unit
) {
    var zoom by remember { mutableIntStateOf(Prefs.fontZoom) }
    var previewing by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (previewing) Color.Transparent else MaterialTheme.colorScheme.background)
    ) {
        Surface(
            tonalElevation = 3.dp,
            color = if (previewing) Color.Transparent else Color.Unspecified,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .alpha(if (previewing) 0f else 1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = !previewing) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "设置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
            }
        }
        if (previewing) Spacer(Modifier.height(1.dp)) else HorizontalDivider()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.background,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Text("字体大小缩放", style = MaterialTheme.typography.titleMedium)
                Text(
                    "拖动下方滑块，实时预览页面效果",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("50%")
                    Slider(
                        value = zoom.toFloat(),
                        onValueChange = {
                            previewing = true
                            zoom = it.toInt()
                            Prefs.fontZoom = zoom
                            AppState.webView?.post { AppState.webView?.let { wv -> Prefs.applyFontZoom(wv) } }
                        },
                        onValueChangeFinished = { previewing = false },
                        valueRange = 50f..200f,
                        steps = 29,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    )
                    Text("200%")
                }
                Text(
                    "当前：$zoom%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.width(0.dp))

            if (!previewing) {
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("是否启用脚本", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "启用后将在底部导航显示「脚本」标签页，可安装 GreasyFork 油猴脚本。脚本功能未经过测试，可能会有未知问题。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Switch(checked = scriptsEnabled, onCheckedChange = onScriptsEnabledChanged)
            }

            Text(
                "关于",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 28.dp)
            )
            Text(
                "烧饼社区客户端 v1.0\n基于 WebView 的 linux.sb 浏览器壳应用，支持油猴脚本、书签、多账号快速切换。\n\n感谢 deepseek v4 flash 的技术支持与协助开发。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
        }
    }
}
}
