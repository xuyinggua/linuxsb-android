# 开发指导（DEVELOPMENT.md）

烧饼社区客户端（linux.sb Android App）的开发与维护指南。本文面向后续开发/修改本项目的开发者与 AI 助手，描述了工程结构、核心机制、常见改动的下手位置与注意事项。

## 1. 项目概览

基于 Android WebView 的 [linux.sb](https://linux.sb) 浏览器壳应用，打包为一个 APK。核心能力：

- 全屏 WebView 浏览，返回/前进/刷新/外部浏览器打开
- **油猴脚本**（默认关闭）：从 GreasyFork 下载、注入、GM_* API 桥接
- **书签**：收藏当前页 + TOML 导入导出
- **多账号**：Cookie/localStorage 快照 + 隔离登录 WebView
- 字体缩放（50%–200% 实时预览）、夜间模式（跟随系统）

## 2. 技术栈与版本（gradle/libs.versions.toml）

| 项 | 版本 |
|---|---|
| Kotlin / AGP | 1.9.10 / 8.2.1 |
| Compose BOM | 2024.02.02（compiler 1.5.3） |
| minSdk / targetSdk / compileSdk | 24 / 34 / 34 |
| Room | 2.6.1（kapt 编译期注解） |
| OkHttp | 4.12.0 |
| androidx.webkit | 1.9.0（Profile 多会话） |
| tomlj | 1.1.1（书签 TOML） |

> 注意：Room 使用 **kapt**，新增实体/Dao 后需重新编译；`exportSchema = false`，数据库迁移需手写 `Migration`（见 `db/AppDatabase.kt`）。

## 3. 工程结构

```
app/src/main/
  java/com/example/shaobing/
    MainActivity.kt        # 入口：WebView 壳 + 底部导航 + 登录覆盖层（全部状态在此装配）
    ShaoBingApp.kt        # Application：全局 appContext / 协程作用域 / Room 单例
    data/AppState.kt      # 全局可变状态（webView 引用、当前 URL/标题、回调钩子）
    net/OkHttpHolder.kt   # OkHttpClient 单例
    web/                  # 主 WebView 行为
      BrowserClient.kt      # 拦截导航/外部 URL、HTML 注入、脚本引擎注入、页面回调
      BrowserChromeClient.kt# 标题/进度/JS 弹窗/文件上传
      ViewportFix.kt        # 修复 Android WebView vh=0 的 bug（CSS 注入）
    scripts/              # 油猴引擎（Kotlin 侧）
      ScriptManager.kt      # 下载/解析/落盘/读取脚本与依赖
      MetadataParser.kt     # 解析 ==UserScript== 元数据块
      MatchPattern.kt       # @match/@include 通配符/正则匹配
      GMBridge.kt          # @JavascriptInterface：GM_* API 的原生实现
    profile/ProfileManager.kt # 多账号：Cookie/存储快照、隔离登录、切号恢复
    ui/                   # Compose 页面
      Theme.kt              # 亮/暗主题
      Common.kt            # SecondaryScreen 及通用对话框
      SettingsScreen.kt    # 字体缩放 + 脚本开关
      ScriptsScreen.kt     # 脚本安装/启停/删除
      BookmarksScreen.kt   # 书签列表 + TOML 导入导出
      BookmarkIO.kt        # TOML 序列化/解析
      ProfilesScreen.kt    # 账号增删改 + 切换 + 登录
      LoginScreen.kt       # 隔离登录 WebView
      Prefs.kt             # SharedPreferences（字体缩放/脚本开关）
    db/                   # Room
      Entities.kt          # 5 张表
      Daos.kt              # 各表 DAO（同步方法 + suspend 混合）
      AppDatabase.kt       # DB 单例、版本与迁移
  assets/runtime.js       # 注入页面的 JS 油猴运行时引擎
```

## 4. 核心机制

### 4.1 页面注入与脚本引擎（web/BrowserClient.kt + assets/runtime.js）

- `shouldInterceptRequest` 拦截**主框架**的 HTML 请求，用 OkHttp 重发请求拿到 HTML 后，在 `<head>`/`<html>`/`<!doctype>` 之后插入 `<script>runtime.js</script>`（`injectIntoHtml`）。
- 注入资格：`Prefs.scriptsEnabled` 开启、主框架、Accept 含 `text/html`、非静态资源扩展名（见 `isInjectionEligible`）。
- `runtime.js` 运行时机：页面脚本最早执行；内部通过 `bridge.scriptsForUrl(location.href)` 拉取匹配脚本，按 `@run-at`（document-start/end/idle）执行。
- 同步重发的 Cookie 取自 `CookieManager`，并把响应的 `Set-Cookie` 写回 CookieManager，保证登录状态一致。

**GM_* 桥接链路**：JS 引擎 `createGM()` → `window.SbApp`（GMBridge，`addJavascriptInterface(GMBridge(this), "SbApp")`）→ Kotlin。GM 值存 Room `gm_values`；`GM_xmlhttpRequest` 由 OkHttp 原生发起（绕过 CORS），完成后回调 JS `__sb_xhr_cb`。

**重要安全约束**：`addJavascriptInterface` 的接口方法会被页面任何 JS 调用，GMBridge 内所有方法均已用 `runCatching` 包裹，新增桥接方法也必须如此，且不得暴露敏感能力。

### 4.2 多账号（profile/ProfileManager.kt）

- 5 张表：`profiles`（账号）、`profile_snapshots`（Cookie JSON + localStorage/sessionStorage JSON 快照）、`gm_values`（GM 数据）。
- 切号：`switchTo` → `snapshotCurrent`（保存当前账号 Cookie + WebStorage）→ `restore`（清空 CookieManager/WebStorage，写回目标快照 Cookie，经 `BrowserClient.queueJavascript` 在页面加载后恢复 localStorage，重设 `isCurrent`，`webView.reload()`）。
- 登录：优先 `WebViewFeature.MULTI_PROFILE`，在独立 `Profile`（名为 `login`）中打开登录页，从登录页跳到首页时自动 `autoSaveLogin` 抓取 Cookie/用户信息（`fetch('/profile')` 正则解析用户名与 UID），写回目标账号快照；旧设备回退为 `openLoginInMain` 主 WebView 内登录。

### 4.3 导航分发（web/BrowserClient.kt）

- `isLoginUrl`：`linux.sb` 且 path 以 `/login` 开头 → 交给 `onLoginLink`（弹出隔离登录）。
- `isExternal`：`linux.sb` 域内 → 留在 WebView；其他 http(s) → 交系统浏览器。

### 4.4 状态装配（MainActivity.kt）

- `AppState` 为全局可变对象（object），持 WebView 引用与回调钩子；Compose 通过 `LaunchedEffect` 订阅回调驱动 UI 状态。新增需要跨页面共享/驱动的状态，应沿此模式（AppState 字段 + 回调 + LaunchedEffect 订阅）。
- 顶部标题栏逻辑集中在 MainActivity；各子页面是覆盖在 WebView 上的全屏 Compose 层（`selectedTab` 控制）。

## 5. 常用改动上手位置

| 需求 | 位置 |
|---|---|
| 改首页地址 | `data/AppState.kt` 的 `HOME_URL` |
| 新增设置项 | `ui/Prefs.kt` 加字段；`ui/SettingsScreen.kt` 加 UI；读取处引用 |
| 新增表/字段 | `db/Entities.kt` + `db/Daos.kt`；**版本 +1** 并写 `Migration` |
| 新增 GM_* API | `assets/runtime.js` 的 `createGM` + `scripts/GMBridge.kt` 加 `@JavascriptInterface` |
| 调整页面注入策略 | `web/BrowserClient.kt`（`isInjectionEligible` / `injectIntoHtml`） |
| 新增底部标签页 | `MainActivity.kt` `BottomNavBar` 与 `when(selectedTab)` 分支 |
| 站点结构变化导致解析失败 | `profile/ProfileManager.kt` 中的正则（`user-header`/`user-name`、登录判定） |
| vh bug 影响新弹窗 | `web/ViewportFix.kt` 追加 CSS |

## 6. 构建与验证

```bash
./gradlew assembleDebug          # 调试包
./gradlew assembleRelease        # 发布包（需 keystore.properties 签名）
```

- 环境：JDK 17、Android SDK platform 34。
- 本项目**无单元测试**；改动后以 `assembleDebug` 编译通过 + 真机/模拟器人工验证为主。
- 发布签名读 `keystore.properties`（已 gitignore），缺失则 release 走默认（未签名）配置，见 `app/build.gradle.kts`。

## 7. 开发约定

- **代码风格**：与现有 Kotlin 一致（Kotlin official style），缩进 4 空格。
- **协程**：IO/耗时操作一律放 `ShaoBingApp.applicationScope.launch(Dispatchers.IO)`，UI 更新用 `withContext(Dispatchers.Main)`；DB 同步查询（`fun all()`）仅用于简单列表快照读取，写入/删除用 `suspend` DAO。
- **线程**：所有 WebView 操作（loadUrl/reload/evaluateJavascript）必须通过 `webView.post {}` 回到其线程。
- **错误处理**：桥接与网络路径用 `runCatching {}.getOrDefault/onFailure` 兜底，避免崩页。
- **中文文案**：所有 UI 文案为中文硬编码在 Composable 内；`res/values/strings.xml` 仅含应用名。
- **不添加代码注释**：除非为解释复杂业务（现有代码注释极少，且为 KDoc/段注释），保持与现有一致。

## 8. 已知限制（改动时注意）

- 多账号基于**单 WebView + Cookie 快照**，切号仅刷新当前页；Cookie 只覆盖 `https://linux.sb` 域（`COOKIE_DOMAINS`）。
- 油猴脚本为第一版，`GM_*` API 子集实现，复杂脚本（多 `@require`、iframe、高级 GM_* 权限）可能不兼容。
- 隔离登录依赖 WebView 105+ 的多 Profile 能力，旧设备回退主 WebView 内登录。
- `shouldInterceptRequest` 同步阻塞网络，`injectHtml` 每次主框架导航都会用 OkHttp 重发一次请求，注意与 WebView 自身请求的重复流量。
- WebView `vh` 解析为 0 的 bug 通过 `ViewportFix` CSS 覆盖，站点改版导致新弹窗仍塌陷时需同步更新。
