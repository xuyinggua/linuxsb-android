# 烧饼社区客户端（linux.sb Android App）

基于 Android WebView 的 [linux.sb](https://linux.sb)（烧饼社区）浏览器壳应用。打包为一个 APK，内置油猴脚本、书签、多账号快速切换、字号缩放等增强功能。

## 功能特性

### 浏览
- 全屏 WebView 加载 `https://linux.sb`，支持返回/前进/刷新/外部浏览器打开
- JS 弹窗（alert/confirm/prompt）、文件上传、页面标题实时显示
- **字体大小缩放**：设置页拖动滑块 50%–200%，实时预览页面效果

### 油猴脚本（默认关闭，需在设置中开启）
- 设置页开关「是否启用脚本」：开启后显示底部「脚本」标签页，关闭时隐藏并自动停用所有已启用脚本
- 通过 GreasyFork 脚本地址（URL）下载安装，本地管理启停
- 解析 `@name / @namespace / @match / @include / @require / @run-at / @grant`
- 页面加载时注入脚本引擎，支持常用 `GM_*` API：
  - `GM_addStyle / GM_setValue / GM_getValue / GM_deleteValue / GM_listValues`
  - `GM_xmlhttpRequest`（经原生桥接绕过 CORS）
  - `GM_info / unsafeWindow / GM_openInTab / GM_notification`
- 说明：脚本功能未经过测试，可能会有未知问题，请谨慎使用

### 书签
- 顶部 ☆ 按钮收藏/取消收藏当前页（空心=未收藏，实心=已收藏）
- 手动添加书签（网址 + 标题）
- 书签页顶部快捷链接（如：签到 `https://linux.sb/daily_checkin`）

### 多账号快速切换
- 每个账号拥有独立的登录状态（Cookie 快照），一键切换（仅刷新当前页，不跳回首页）
- 隔离登录 WebView：点击「登录」在独立 Profile 中登录，不干扰主会话
  - 登录成功（从登录页跳转首页）后**自动保存 Cookie**，Toast 提示并自动关闭
  - 也可手动点「确认」保存
- 每个账号展示用户名与 UID（从 `https://linux.sb/profile` 解析）
- 账号备注可留空（自动命名）

### 设置
- 字体大小缩放（实时预览）
- 是否启用脚本（控制脚本标签页显隐）

## 技术栈

| 项 | 方案 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose + Material3 |
| WebView | Android System WebView（Chromium） |
| 多会话隔离 | `androidx.webkit` Profile API（WebView 105+） |
| 本地存储 | Room + SharedPreferences |
| 网络 | OkHttp |

## 工程结构

```
app/src/main/
  java/com/example/shaobing/
    MainActivity.kt          # 主界面：WebView 壳 + 底部导航 + 登录覆盖层
    ui/                      # Compose 页面：设置/书签/脚本/账号/登录
    scripts/                 # 油猴引擎：下载/解析/注入/GM_* 桥接
    profile/                 # 多账号：Cookie 快照 + 隔离登录
    web/                     # WebViewClient：HTML 注入、登录链接检测
    data/                    # AppState 全局状态
    db/                      # Room：书签/脚本/账号/快照/GM 值
  assets/runtime.js          # 注入页面的油猴运行时引擎
```

## 构建

环境要求：JDK 17、Android SDK（platform 34）、Android Studio。

```bash
# 调试包
./gradlew assembleDebug

# 发布包（需签名配置）
./gradlew assembleRelease
```

产物：
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

### 签名

发布签名读取 `keystore.properties`（已 gitignore）：
```
KEYSTORE=release.keystore
KEYALIAS=shaobing
STORE_PASSWORD=...
KEYPASSWORD=...
```

## 配置项

- 包名 / 应用名：`app/build.gradle.kts` 与 `app/src/main/res/values/strings.xml`
- 首页地址：`data/AppState.kt` 中的 `HOME_URL`

## 说明与限制

- 多账号切换基于单 WebView + Cookie/localStorage 快照；切号后仅刷新当前页
- 油猴脚本第一版以常用 GM_* API 为主，复杂脚本可能存在兼容问题
- 隔离登录依赖 WebView 105+（旧设备回退为主 WebView 内登录）
