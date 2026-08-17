# 自动修复 WebView 视口单位 (vh) 的稳健方案（非全屏模式）

本方案通过 Android Insets 转发确保 `innerHeight` 的准确性，并利用 JS 动态注入 CSS 变量 `--sb-vh` 来彻底解决 `vh` 解析为 0 的 Bug。

## 方案设计

### 1. Insets 转发（保持非全屏）
在 `MainActivity.kt` 中保留 `ViewCompat.setOnApplyWindowInsetsListener`，以确保 WebView 能够正确计算可见高度（避开底部导航栏）。**不再开启全屏模式 (Edge-to-Edge)**。

### 2. 动态 CSS 变量
重写 `ViewportFix.kt`。注入一段 JS 监听 `resize` 事件，通过 `window.innerHeight` 动态更新 `--sb-vh` 变量。

### 3. 通用样式覆盖
不再使用硬编码的选择器和版本号。利用 `.modal-backdrop` 的层级关系，强制子元素 `.modal-panel` 使用动态变量或百分比进行高度限制。

---

## 拟议更改

### [基础组件]

#### [MODIFY] [MainActivity.kt](file:///D:/linuxsb-android/app/src/main/java/com/example/shaobing/MainActivity.kt)
- **移除** `enableEdgeToEdge()`。
- **保留** `setOnApplyWindowInsetsListener`。
- **优化** `WebSettings`（`useWideViewPort`, `loadWithOverviewMode`）。

#### [MODIFY] [ViewportFix.kt](file:///D:/linuxsb-android/app/src/main/java/com/example/shaobing/web/ViewportFix.kt)
- **彻底重写**。
- **实现**：
  - JS: 动态设置 `--sb-vh`。
  - CSS: 针对弹窗容器进行通用覆盖：
    ```css
    .modal-backdrop > .modal-panel {
        max-height: calc(var(--sb-vh, 1vh) * 100 - 32px) !important;
    }
    ```

#### [MODIFY] [BrowserClient.kt](file:///D:/linuxsb-android/app/src/main/java/com/example/shaobing/web/BrowserClient.kt)
- 恢复 `ViewportFix.injectJs()`。
- 保留 `view.requestLayout()`。

## 验证计划

### 自动化测试
- 确保构建成功。

### 手动验证
- 检查弹窗是否不再塌陷。
- 在 console 确认 `window.innerHeight` 是否随着键盘弹出而变化。
- 确认非全屏布局下的顶部/底部导航栏位置正常。
