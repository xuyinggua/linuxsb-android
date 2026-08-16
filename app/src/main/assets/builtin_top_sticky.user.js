// ==UserScript==
// @name          顶部吸顶（内置）
// @namespace     com.shaobing.builtin
// @version       1.0
// @description   将顶栏固定在最上面，未开启时滚屏无法快速打开菜单
// @match         *://*/*
// @run-at        document-start
// @grant         GM_addStyle
// ==/UserScript==

GM_addStyle(
  ".top { " +
    "position: sticky !important; " +
    "top: 0px !important; " +
    "z-index: 100 !important; " +
  "}"
);
