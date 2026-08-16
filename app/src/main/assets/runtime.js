(function () {
    "use strict";
    if (window.__SB_ENGINE__) return;
    window.__SB_ENGINE__ = true;

    var bridge = window.SbApp;
    if (!bridge) return;

    var currentUrl = location.href;

    function scriptKeyOf(ns, name) {
        return (ns || "default") + "::" + name;
    }

    function compilePattern(pattern) {
        if (typeof pattern !== "string") return null;
        if (pattern.length > 2 && pattern.charAt(0) === "/") {
            var last = pattern.lastIndexOf("/");
            if (last > 0) {
                var body = pattern.substring(1, last);
                var flags = pattern.substring(last + 1);
                try { return new RegExp(body, flags); } catch (e) { return null; }
            }
        }
        var re = "^";
        for (var i = 0; i < pattern.length; i++) {
            var c = pattern.charAt(i);
            if (c === "*") re += ".*";
            else if (c === "?") re += ".";
            else re += c.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        }
        re += "$";
        try { return new RegExp(re); } catch (e) { return null; }
    }

    function matchesAny(url, patterns) {
        if (!patterns || patterns.length === 0) return true;
        for (var i = 0; i < patterns.length; i++) {
            var p = compilePattern(patterns[i]);
            if (p && p.test(url)) return true;
        }
        return false;
    }

    function gmXhr(details) {
        var xhr = {
            status: 0,
            statusText: "",
            responseText: "",
            responseHeaders: "",
            readyState: 0,
            responseURL: "",
            onreadystatechange: null,
            onload: null,
            onerror: null,
            onabort: null,
            ontimeout: null,
            _aborted: false,
            _requestId: "r" + Math.random().toString(36).slice(2) + Date.now().toString(36),
            abort: function () { this._aborted = true; }
        };
        var headers = {};
        if (details && details.headers) {
            for (var k in details.headers) {
                if (Object.prototype.hasOwnProperty.call(details.headers, k)) headers[k] = details.headers[k];
            }
        }
        var payload = {
            url: (details && details.url) || "",
            method: ((details && details.method) || "GET").toUpperCase(),
            headers: headers,
            data: (details && details.data) || null,
            timeout: (details && details.timeout) || 0,
            contentType: (details && (details.contentType || details.headers && details.headers["Content-Type"])) || "text/plain"
        };
        if (typeof window.__sb_xhr_map === "undefined") window.__sb_xhr_map = {};
        if (typeof window.__sb_xhr_cb !== "function") {
            window.__sb_xhr_cb = function (requestId, status, statusText, headersJson, body, error) {
                var x = window.__sb_xhr_map[requestId];
                if (!x) return;
                if (x._aborted) {
                    if (x.onabort) { try { x.onabort(); } catch (e) {} }
                    delete window.__sb_xhr_map[requestId];
                    return;
                }
                x.status = status;
                x.statusText = statusText || "";
                x.responseHeaders = headersJson || "";
                x.responseText = body == null ? "" : body;
                x.readyState = 4;
                if (x.onreadystatechange) { try { x.onreadystatechange(); } catch (e) {} }
                if (error) {
                    if (x.ontimeout && /timeout/i.test(error)) { try { x.ontimeout(); } catch (e) {} }
                    if (x.onerror) { try { x.onerror(); } catch (e) {} }
                } else if (x.onload) {
                    try { x.onload(); } catch (e) {}
                }
                delete window.__sb_xhr_map[requestId];
            };
        }
        window.__sb_xhr_map[xhr._requestId] = xhr;
        try {
            bridge.xhr(xhr._requestId, JSON.stringify(payload));
        } catch (e) {
            delete window.__sb_xhr_map[xhr._requestId];
            if (xhr.onerror) { try { xhr.onerror(); } catch (_) {} }
        }
        return xhr;
    }

    function createGM(scriptKey, scriptInfo) {
        return {
            info: scriptInfo,
            addStyle: function (css) {
                var el = document.createElement("style");
                el.type = "text/css";
                el.setAttribute("data-sb-style", "1");
                try { el.appendChild(document.createTextNode(css)); } catch (e) { el.textContent = css; }
                (document.head || document.documentElement).appendChild(el);
            },
            setValue: function (key, value) {
                try { bridge.setValue(scriptKey, key, JSON.stringify(value)); } catch (e) {}
            },
            getValue: function (key, def) {
                try {
                    var v = bridge.getValue(scriptKey, key);
                    if (v === null || v === undefined || v === "" || v === "null") return def;
                    try { return JSON.parse(v); } catch (e) { return v; }
                } catch (e) { return def; }
            },
            deleteValue: function (key) { try { bridge.deleteValue(scriptKey, key); } catch (e) {} },
            listValues: function () {
                try { return JSON.parse(bridge.listValues(scriptKey) || "[]"); } catch (e) { return []; }
            },
            xmlHttpRequest: function (d) { return gmXhr(d); },
            xmlhttpRequest: function (d) { return gmXhr(d); },
            openInTab: function (url) { try { bridge.openTab(url); } catch (e) {} },
            notification: function (details) {
                var title, text;
                if (typeof details === "object" && details !== null) {
                    title = details.title || "";
                    text = details.text || "";
                } else {
                    title = String(details == null ? "" : details);
                    text = "";
                }
                try { bridge.notify(title, text); } catch (e) {}
            },
            registerMenuCommand: function (name, fn) {}
        };
    }

    var scripts = [];
    try {
        scripts = JSON.parse(bridge.scriptsForUrl(currentUrl) || "[]");
    } catch (e) {
        scripts = [];
    }

    function runScript(s) {
        try {
            var scriptKey = scriptKeyOf(s.namespace, s.name);
            var GM = createGM(scriptKey, {
                script: {
                    name: s.name,
                    namespace: s.namespace || "",
                    version: s.version || "",
                    description: s.description || "",
                    matches: s.matches || []
                },
                scriptHandler: "ShaoBing",
                scriptVersion: "1.0",
                platform: { name: "ShaoBing", version: "1.0", ice: true },
                scriptMetaStr: s.meta || ""
            });
            var fn = new Function(
                "unsafeWindow", "GM", "GM_addStyle", "GM_setValue", "GM_getValue",
                "GM_deleteValue", "GM_listValues", "GM_xmlhttpRequest", "GM_openInTab", "GM_notification",
                s.content + "\n//# sourceURL=shaobing://" + encodeURIComponent(s.name) + ".user.js"
            );
            fn.call(window, window, GM,
                function (css) { GM.addStyle(css); },
                function (k, v) { GM.setValue(k, v); },
                function (k, d) { return GM.getValue(k, d); },
                function (k) { GM.deleteValue(k); },
                function () { return GM.listValues(); },
                function (d) { return GM.xmlHttpRequest(d); },
                function (u) { GM.openInTab(u); },
                function (t) { GM.notification(t); }
            );
        } catch (e) {
            try { console.error("[ShaoBing] script error:", s.name, e); } catch (_) {}
        }
    }

    function runAt(at) {
        for (var i = 0; i < scripts.length; i++) {
            var s = scripts[i];
            if (s.runAt === at) runScript(s);
        }
    }

    runAt("document-start");

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", function () {
            runAt("document-end");
            runAt("document-idle");
        });
        window.addEventListener("load", function () {
            runAt("document-idle");
        });
        var idleTimer = setTimeout(function () {
            runAt("document-idle");
        }, 3000);
        window.addEventListener("load", function () { clearTimeout(idleTimer); });
    } else {
        runAt("document-end");
        runAt("document-idle");
    }
})();
