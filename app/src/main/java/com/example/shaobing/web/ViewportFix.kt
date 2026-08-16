package com.example.shaobing.web

/**
 * Android WebView 已知 bug：CSS 视口单位 `vh`/`dvh`（及 `svh`/`lvh`）会解析为 0，
 * 而 `innerHeight`/`clientHeight` 正常。本站用到 vh 的元素都位于 `position:fixed;inset:0`
 * 的 backdrop 内，backdrop 高度由 `inset:0` 决定（=视口，正确），所以子元素用 `%` /
 * `calc(100% - Npx)` 覆盖即可等效替代 vh。
 *
 * 站点 CSS 版本基准（若站点改版导致以下版本变化，需重新审计 [FIXES]）：
 * `index.css?v=v8.6.5`、`plugins.css?v=ae34ffb0b838`。
 * 运行期可用 `window.__sbViewportStatus()` 查看各选择器命中数与注入的 CSS。
 */
object ViewportFix {

    private const val SITE_CSS_VERSIONS = "index.css?v=v8.6.5|plugins.css?v=ae34ffb0b838"

    private data class Fix(
        val selector: String,
        val siteRule: String,
        val css: String
    )

    private val FIXES = listOf(
        Fix(
            selector = ".modal-panel",
            siteRule = "index.css: .modal-panel max-height:calc(100vh - 32px)",
            css = ".modal-panel{max-height:calc(100% - 32px) !important}"
        ),
        Fix(
            selector = ".modal-backdrop:has(.home-keyword-filter-settings)",
            siteRule = "plugins.css: .modal-backdrop:has(.home-keyword-filter-settings) height:100dvh",
            css = ".modal-backdrop:has(.home-keyword-filter-settings){height:100% !important}"
        ),
        Fix(
            selector = ".posting-notice-dialog",
            siteRule = "plugins.css: .posting-notice-dialog max-height:calc(100vh - 40px)/calc(100dvh - 40px)",
            css = ".posting-notice-dialog{max-height:calc(100% - 40px) !important}"
        ),
        Fix(
            selector = ".posting-notice-dialog",
            siteRule = "plugins.css @media(max-width:680px): .posting-notice-dialog height:100vh/100dvh;max-height:none",
            css = "@media (max-width: 680px){.posting-notice-dialog{height:100% !important;max-height:none !important}}"
        )
    )

    private val CSS: String = buildString {
        for (f in FIXES) {
            append("/* ").append(f.siteRule).append(" */ ")
            append(f.css).append(' ')
        }
    }

    private val STATUS_SELECTORS_JSON: String =
        FIXES.map { it.selector }.distinct().joinToString(",", "[", "]") { "\"$it\"" }

    fun injectJs(): String = """
        (function(){
          var CSS = '$CSS';
          var SELECTORS = $STATUS_SELECTORS_JSON;
          var SITE_CSS = "$SITE_CSS_VERSIONS";

          function applyStyle(){
            try {
              var s = document.getElementById("sb-viewport-fix");
              if (!s) {
                s = document.createElement("style");
                s.id = "sb-viewport-fix";
                (document.head || document.documentElement).appendChild(s);
              }
              s.setAttribute("data-sb-css-version", SITE_CSS);
              s.textContent = "/* WebView vh/dvh=0 补偿，站点 CSS 版本基准: " + SITE_CSS + " */\n" + CSS;
            } catch(e) {}
          }

          function checkVersions(){
            try {
              var hrefs = Array.prototype.map.call(
                document.querySelectorAll('link[rel="stylesheet"]'),
                function(l){ return (l.getAttribute('href') || '').split('/').pop(); }
              );
              var expected = SITE_CSS.split('|');
              for (var i = 0; i < expected.length; i++){
                var found = hrefs.some(function(h){ return h.indexOf(expected[i]) >= 0; });
                if (!found) console.warn('[ShaoBing] 站点 CSS 版本与 ViewportFix 审计基准不一致，请重新审计：', expected[i]);
              }
            } catch(e) {}
          }

          window.__sbViewportStatus = function(){
            var out = {};
            for (var i = 0; i < SELECTORS.length; i++){
              try { out[SELECTORS[i]] = document.querySelectorAll(SELECTORS[i]).length; }
              catch(e){ out[SELECTORS[i]] = 'invalid-selector'; }
            }
            out.__css = (document.getElementById('sb-viewport-fix') || {}).textContent || '';
            return out;
          };

          applyStyle();
          checkVersions();
        })();
    """.trimIndent()
}
