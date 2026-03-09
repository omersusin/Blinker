package blinker.go.data.extension

import android.webkit.WebView

object ExtensionInjector {

    fun getApiShim(extensionId: String = "blinker-ext"): String = buildString {
        append("(function(){")
        append("if(window.__blinkerExtInit)return;")
        append("window.__blinkerExtInit=true;")

        // chrome namespace
        append("if(typeof chrome==='undefined')window.chrome={};")
        append("if(typeof browser==='undefined')window.browser=chrome;")

        // storage.local & storage.sync
        append("var _s=window.BlinkerBridge;")
        append("function _mkStore(){return{")
        append("get:function(k,c){var ks=k===undefined||k===null?'null':JSON.stringify(k);var r=JSON.parse(_s.storageLocalGet('$extensionId',ks));if(c)c(r);return Promise.resolve(r);},")
        append("set:function(i,c){_s.storageLocalSet('$extensionId',JSON.stringify(i));if(c)c();return Promise.resolve();},")
        append("remove:function(k,c){var ks=typeof k==='string'?'\"'+k+'\"':JSON.stringify(k);_s.storageLocalRemove('$extensionId',ks);if(c)c();return Promise.resolve();},")
        append("clear:function(c){_s.storageLocalClear('$extensionId');if(c)c();return Promise.resolve();},")
        append("getBytesInUse:function(k,c){var n=0;if(c)c(n);return Promise.resolve(n);}");
        append("};}")
        append("if(!chrome.storage)chrome.storage={};")
        append("if(!chrome.storage.local)chrome.storage.local=_mkStore();")
        append("if(!chrome.storage.sync)chrome.storage.sync=_mkStore();")
        append("if(!chrome.storage.managed)chrome.storage.managed=_mkStore();")
        append("if(!chrome.storage.session)chrome.storage.session=_mkStore();")
        append("if(!chrome.storage.onChanged)chrome.storage.onChanged={addListener:function(){},removeListener:function(){},hasListener:function(){return false;}};")

        // runtime
        append("if(!chrome.runtime)chrome.runtime={};")
        append("chrome.runtime.id='$extensionId';")
        append("if(!chrome.runtime.sendMessage)chrome.runtime.sendMessage=function(){return Promise.resolve();};")
        append("if(!chrome.runtime.onMessage)chrome.runtime.onMessage={addListener:function(){},removeListener:function(){},hasListener:function(){return false;}};")
        append("if(!chrome.runtime.onConnect)chrome.runtime.onConnect={addListener:function(){},removeListener:function(){}};")
        append("if(!chrome.runtime.connect)chrome.runtime.connect=function(){return{postMessage:function(){},onMessage:{addListener:function(){}},onDisconnect:{addListener:function(){}}};};")
        append("if(!chrome.runtime.getURL)chrome.runtime.getURL=function(p){return '${ExtensionUrlHandler.BASE_URL}/$extensionId/'+p;};")
        append("if(!chrome.runtime.getManifest)chrome.runtime.getManifest=function(){return{};};")
        append("if(!chrome.runtime.onInstalled)chrome.runtime.onInstalled={addListener:function(c){try{c({reason:'install'})}catch(e){}}};")
        append("if(!chrome.runtime.onStartup)chrome.runtime.onStartup={addListener:function(){}};")
        append("if(!chrome.runtime.getPlatformInfo)chrome.runtime.getPlatformInfo=function(c){var i={os:'android',arch:'arm'};if(c)c(i);return Promise.resolve(i);};")
        append("if(!chrome.runtime.lastError)chrome.runtime.lastError=null;")

        // tabs
        append("if(!chrome.tabs)chrome.tabs={};")
        append("if(!chrome.tabs.query)chrome.tabs.query=function(q,c){var r=JSON.parse(_s.tabsQuery());if(c)c(r);return Promise.resolve(r);};")
        append("if(!chrome.tabs.create)chrome.tabs.create=function(o,c){if(o&&o.url)_s.tabsCreate(o.url);if(c)c({});return Promise.resolve({});};")
        append("if(!chrome.tabs.sendMessage)chrome.tabs.sendMessage=function(t,m,c){if(c)c();return Promise.resolve();};")
        append("if(!chrome.tabs.onUpdated)chrome.tabs.onUpdated={addListener:function(){},removeListener:function(){}};")
        append("if(!chrome.tabs.onRemoved)chrome.tabs.onRemoved={addListener:function(){},removeListener:function(){}};")
        append("if(!chrome.tabs.onActivated)chrome.tabs.onActivated={addListener:function(){},removeListener:function(){}};")

        // i18n
        append("if(!chrome.i18n)chrome.i18n={};")
        append("if(!chrome.i18n.getMessage)chrome.i18n.getMessage=function(m,s){return m;};")
        append("if(!chrome.i18n.getUILanguage)chrome.i18n.getUILanguage=function(){return 'en';};")

        // extension
        append("if(!chrome.extension)chrome.extension={};")
        append("if(!chrome.extension.getURL)chrome.extension.getURL=function(p){return chrome.runtime.getURL(p);};")

        // alarms
        append("if(!chrome.alarms)chrome.alarms={create:function(){},get:function(n,c){if(c)c(null);return Promise.resolve(null);},getAll:function(c){if(c)c([]);return Promise.resolve([]);},clear:function(n,c){if(c)c(true);return Promise.resolve(true);},clearAll:function(c){if(c)c(true);return Promise.resolve(true);},onAlarm:{addListener:function(){},removeListener:function(){}}};")

        // notifications
        append("if(!chrome.notifications)chrome.notifications={create:function(i,o,c){if(c)c(i||'n');return Promise.resolve(i||'n');},clear:function(i,c){if(c)c(true);return Promise.resolve(true);},onClicked:{addListener:function(){}},onClosed:{addListener:function(){}}};")

        // contextMenus
        append("if(!chrome.contextMenus)chrome.contextMenus={create:function(){},remove:function(){},removeAll:function(c){if(c)c();return Promise.resolve();},onClicked:{addListener:function(){}}};")

        // webRequest (stub - cannot actually intercept in WebView)
        append("if(!chrome.webRequest)chrome.webRequest={onBeforeRequest:{addListener:function(){}},onBeforeSendHeaders:{addListener:function(){}},onHeadersReceived:{addListener:function(){}},onCompleted:{addListener:function(){}},onErrorOccurred:{addListener:function(){}}};")

        // commands
        append("if(!chrome.commands)chrome.commands={onCommand:{addListener:function(){}},getAll:function(c){if(c)c([]);return Promise.resolve([]);}};")

        // action/browserAction
        append("var _actionStub={setIcon:function(){return Promise.resolve();},setTitle:function(){return Promise.resolve();},setBadgeText:function(){return Promise.resolve();},setBadgeBackgroundColor:function(){return Promise.resolve();},setPopup:function(){return Promise.resolve();},getPopup:function(d,c){if(c)c('');return Promise.resolve('');},onClicked:{addListener:function(){}}};")
        append("if(!chrome.action)chrome.action=_actionStub;")
        append("if(!chrome.browserAction)chrome.browserAction=_actionStub;")

        // permissions
        append("if(!chrome.permissions)chrome.permissions={contains:function(p,c){if(c)c(true);return Promise.resolve(true);},request:function(p,c){if(c)c(true);return Promise.resolve(true);}};")

        // sync browser = chrome
        append("window.browser=chrome;")

        append("})();")
    }

    fun injectAtStart(webView: WebView, url: String, manager: ExtensionManager) {
        val matches = manager.getContentScriptsForUrl(url)
        val startScripts = matches.filter { (_, cs) -> cs.runAt == "document_start" }
        if (startScripts.isEmpty()) return

        startScripts.forEach { (ext, cs) ->
            webView.evaluateJavascript(getApiShim(ext.id), null)
            cs.css.forEach { path -> manager.readFile(ext.id, path)?.let { injectCss(webView, it) } }
            cs.js.forEach { path -> manager.readFile(ext.id, path)?.let { injectJs(webView, it) } }
        }
    }

    fun inject(webView: WebView, url: String, manager: ExtensionManager) {
        val matches = manager.getContentScriptsForUrl(url)
        val scripts = matches.filter { (_, cs) -> cs.runAt != "document_start" }
        if (scripts.isEmpty()) return

        scripts.forEach { (ext, cs) ->
            webView.evaluateJavascript(getApiShim(ext.id), null)
            cs.css.forEach { path -> manager.readFile(ext.id, path)?.let { injectCss(webView, it) } }
            cs.js.forEach { path -> manager.readFile(ext.id, path)?.let { injectJs(webView, it) } }
        }
    }

    private fun injectCss(webView: WebView, css: String) {
        val escaped = css.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "")
        webView.evaluateJavascript(
            "(function(){var s=document.createElement('style');s.textContent='$escaped';(document.head||document.documentElement).appendChild(s);})()",
            null
        )
    }

    private fun injectJs(webView: WebView, js: String) {
        webView.evaluateJavascript(
            "(function(){try{\n$js\n}catch(e){console.error('Blinker ext:',e);}})()",
            null
        )
    }
}
