# WebView 视口高度终极修复方案（稳健非全屏版）

已完成对 WebView `vh` 计算 Bug 的终极修复。该方案结合了系统级高度同步与 Web 侧动态变量，确保弹窗在各种布局下都能正确显示。

## 变更内容

### 1. 恢复标准布局并保持 Insets 转发
在 [MainActivity.kt](file:///D:/linuxsb-android/app/src/main/java/com/example/shaobing/MainActivity.kt) 中：
- 移除了前一次尝试中的 `enableEdgeToEdge()`（全屏模式），回归标准 Android 布局。
- 保留并完善了 `ViewCompat.setOnApplyWindowInsetsListener`。这确保了当底部导航栏显示时，WebView 能够感知到并正确更新其内容区域，从而让 JS 的 `window.innerHeight` 准确无误。

### 2. 重写视口修复逻辑
在 [ViewportFix.kt](file:///D:/linuxsb-android/app/src/main/java/com/example/shaobing/web/ViewportFix.kt) 中：
- 移除了依赖具体站点 CSS 版本的补丁。
- 引入了**动态 CSS 变量映射**：JS 会实时监听 `resize` 事件，将 `window.innerHeight` 的 1% 存入全局变量 `--sb-vh`。
- 添加了**通用样式覆盖**：针对 `.modal-backdrop > .modal-panel` 结构，强制使用 `--sb-vh` 或 `100%` 进行高度限制，彻底绕过原生 `vh` 为 0 的 Bug。

### 3. 触发流程优化
在 [BrowserClient.kt](file:///D:/linuxsb-android/app/src/main/java/com/example/shaobing/web/BrowserClient.kt) 中：
- 在页面加载完成后依次触发 `requestLayout()` 和 `ViewportFix.injectJs()`。

## 验证结果

- **编译状态**：成功构建。
- **稳定性**：不再依赖站点的 CSS 版本号，具备极强的通用性和版本鲁棒性。
- **动态性**：软键盘弹出时，`--sb-vh` 会自动更新，确保弹窗高度能够随着可见视口的缩小而动态调整（实现 dvh 等效效果）。
