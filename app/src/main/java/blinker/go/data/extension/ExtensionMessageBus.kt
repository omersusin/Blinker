package blinker.go.data.extension

import android.webkit.WebView

/**
 * Routes chrome.runtime.sendMessage / onMessage between content scripts
 * (tab WebViews) and background page WebViews.
 *
 * Flow:
 *  1. Content script calls chrome.runtime.sendMessage(msg)
 *  2. JS shim calls BlinkerBridge.postMessageToBackground(extId, msgJson)
 *  3. Bridge calls ExtensionMessageBus.dispatchToBackground(...)
 *  4. Bus invokes window.__blinkerDispatch(msgJson, senderJson) in the
 *     background WebView via evaluateJavascript
 *  5. Background WebView iterates window.__blinkerMsgListeners and fires handlers
 */
object ExtensionMessageBus {

    private val backgroundViews = mutableMapOf<String, WebView>()

    fun register(extId: String, view: WebView) {
        backgroundViews[extId] = view
    }

    fun unregister(extId: String) {
        backgroundViews.remove(extId)
    }

    /**
     * Dispatch a message from a content script to the extension's background page.
     * Must be called from any thread; evaluateJavascript is posted to the main thread.
     *
     * @param extId      The extension id whose background should receive the message.
     * @param messageJson JSON-stringified message payload.
     * @param senderJson  JSON-stringified sender descriptor (url, tab info).
     */
    fun dispatchToBackground(extId: String, messageJson: String, senderJson: String) {
        val bgView = backgroundViews[extId] ?: return
        val js = """
            (function(){
                if(!window.__blinkerMsgListeners) return;
                var msg;
                try { msg = JSON.parse('${messageJson.replace("'", "\\'")}'); } catch(e){ msg = {}; }
                var sender;
                try { sender = JSON.parse('${senderJson.replace("'", "\\'")}'); } catch(e){ sender = {}; }
                window.__blinkerMsgListeners.forEach(function(fn){
                    try { fn(msg, sender, function(){}); } catch(e){}
                });
            })();
        """.trimIndent()
        bgView.post { bgView.evaluateJavascript(js, null) }
    }
}
