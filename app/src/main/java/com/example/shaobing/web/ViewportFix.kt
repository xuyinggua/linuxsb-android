package com.example.shaobing.web

/**
 * Android WebView 的一个已知 bug：即使布局视口高度正确（clientHeight/innerHeight），
 * `vh`/`dvh` 单位仍会被解析为 0。站点 .modal-panel 依赖 `max-height:calc(100vh - 32px)`，
 * 导致弹窗塌缩成一条横线。这里注入等价的 CSS 覆盖修复（用相对视口的 `100%` 代替 vh）。
 */
object ViewportFix {

    private const val CSS =
        ".modal-panel{max-height:calc(100% - 32px) !important}" +
        ".modal-backdrop:has(.home-keyword-filter-settings){height:100% !important}" +
        "@media (max-width: 680px){.posting-notice-dialog{height:100% !important}}"

    fun injectJs(): String = """
        (function(){
          try {
            if (document.getElementById("sb-viewport-fix")) return;
            var s = document.createElement("style");
            s.id = "sb-viewport-fix";
            s.textContent = '$CSS';
            (document.head || document.documentElement).appendChild(s);
          } catch(e) {}
        })();
    """.trimIndent()
}
