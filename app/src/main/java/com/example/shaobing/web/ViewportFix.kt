package com.example.shaobing.web

/**
 * 终极视口修复：针对 Android WebView 中 `vh` 解析为 0 的 Bug，
 * 通过 JS 动态计算 `window.innerHeight` 并映射为 CSS 变量 `--sb-vh`。
 * 同时强制覆盖常见的全屏弹窗容器高度。
 */
object ViewportFix {

    fun injectJs(): String = """
        (function(){
          function updateVh() {
            var vh = window.innerHeight * 0.01;
            document.documentElement.style.setProperty('--sb-vh', vh + 'px');
          }

          function applyGlobalStyles() {
            var s = document.getElementById("sb-viewport-fix-style");
            if (!s) {
              s = document.createElement("style");
              s.id = "sb-viewport-fix-style";
              (document.head || document.documentElement).appendChild(s);
            }
            s.textContent = [
              "/* WebView vh=0 动态补救 */",
              ".modal-backdrop > .modal-panel {",
              "  max-height: calc(var(--sb-vh, 1vh) * 100 - 32px) !important;",
              "}",
              "/* 针对某些直接挂在 body 下的 fixed 元素提供百分比回退 */",
              "@media screen {",
              "  .modal-panel { max-height: calc(100% - 32px) !important; }",
              "}"
            ].join("\n");
          }

          window.addEventListener('resize', updateVh);
          updateVh();
          applyGlobalStyles();
          
          // 延迟再次检查，防止某些框架在加载初期 innerHeight 为 0
          setTimeout(updateVh, 500);
        })();
    """.trimIndent()
}
