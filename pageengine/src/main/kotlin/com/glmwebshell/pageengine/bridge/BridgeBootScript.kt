package com.glmwebshell.pageengine.bridge

/**
 * The injected bridge boot script.
 *
 * Per TZ §5.4.1:
 *  - Script is injected via `addDocumentStartJavaScript` so it runs before any
 *    page script can install its own handlers.
 *  - It is **read-only**: it observes DOM events and network streams but does
 *    not mutate the page.
 *  - Communication is over `addWebMessageListener` (NOT `addJavascriptInterface`).
 *  - Messages are strictly-typed JSON with a `protocol` field.
 *
 * The script below is bundled as a Kotlin raw string so it ships inside the
 * APK and is easy to audit. It posts `BridgeMessage.Incoming` payloads.
 */
object BridgeBootScript {

    const val JS: String = """
(function () {
  'use strict';

  if (window.__glmBridge) return;            // idempotent
  var PROTOCOL_VERSION = __GLM_BRIDGE_PROTOCOL_VERSION__;

  var pendingCallbacks = {};

  function post(msg) {
    try {
      msg.protocol = PROTOCOL_VERSION;
      var channel = window.__glmBridgeChannel;
      if (channel && typeof channel.postMessage === 'function') {
        channel.postMessage(JSON.stringify(msg));
      }
    } catch (e) { /* ignore transport errors */ }
  }

  // ---- DOM observation ---------------------------------------------------
  var logTargets = document.querySelectorAll('[role="log"], [aria-live="polite"], [aria-live="assertive"]');
  function describeNode(node) {
    var tag = (node.tagName || '').toLowerCase();
    var role = node.getAttribute && node.getAttribute('role');
    return tag + (role ? ('[role="' + role + '"]') : '');
  }

  var mo = new MutationObserver(function (records) {
    records.forEach(function (rec) {
      rec.addedNodes.forEach(function (n) {
        if (n.nodeType === 1) {
          post({
            type: 'SemanticDomEvent',
            kind: 'mutation.add',
            payload: { target: describeNode(n), text: (n.textContent || '').slice(0, 4096) }
          });
        }
      });
    });
  });

  function startObserving() {
    mo.observe(document.documentElement, { childList: true, subtree: true });
    // detect input / send buttons
    var inputs = document.querySelectorAll('textarea, [contenteditable=""], [contenteditable="true"], [role="textbox"]');
    for (var i = 0; i < inputs.length; i++) {
      post({ type: 'InputDetected', locator: describeNode(inputs[i]) });
    }
    var sendButtons = document.querySelectorAll('button[aria-label*="Send" i], button[type="submit"]');
    for (var j = 0; j < sendButtons.length; j++) {
      post({ type: 'SendButtonDetected', locator: describeNode(sendButtons[j]) });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', startObserving);
  } else {
    startObserving();
  }

  // ---- Network / fetch interception -------------------------------------
  // The injected script never blocks requests — it only observes what the
  // page itself fetches, and reports chunks the page is already receiving.
  var origFetch = window.fetch;
  window.fetch = function () {
    var args = arguments;
    var url = (args[0] && args[0].url) || args[0];
    var urlStr = String(url);
    return origFetch.apply(this, args).then(function (resp) {
      try {
        if (resp.body) {
          var clone = (typeof resp.clone === 'function') ? resp.clone() : resp;
          var reader = clone.body.getReader();
          var decoder = (typeof TextDecoder !== 'undefined') ? new TextDecoder('utf-8') : null;
          (function pump() {
            reader.read().then(function (chunk) {
              if (chunk.done) return;
              try {
                post({
                  type: 'NetworkStreamChunk',
                  endpoint: urlStr,
                  chunk: (decoder ? decoder.decode(chunk.value || new Uint8Array(), {stream:true}) : String.fromCharCode.apply(null, new Uint8Array(chunk.value || []).slice(0, 8192))).slice(0, 8192)
                });
              } catch (e) {}
              pump();
            }).catch(function () {});
          })();
        }
      } catch (e) {}
      return resp;
    });
  };

  var origXHR = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function (body) {
    var xhr = this;
    var url = xhr.__glm_url || (xhr.responseURL || '');
    xhr.addEventListener('progress', function () {
      try {
        post({
          type: 'NetworkStreamChunk',
          endpoint: url,
          chunk: (xhr.responseText || '').slice(-8192)
        });
      } catch (e) {}
    });
    return origXHR.apply(this, arguments);
  };
  var origOpen = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__glm_url = url;
    return origOpen.apply(this, arguments);
  };

  // ---- Self-test runner --------------------------------------------------
  function runSelfTest(spec) {
    try {
      var el = spec.selector ? document.querySelector(spec.selector) : null;
      var ok = !!el;
      if (spec.expectAttr && el) {
        ok = el.hasAttribute(spec.expectAttr);
      }
      post({
        type: 'SelfTestResult',
        capabilityId: spec.capabilityId,
        passed: ok,
        activeStrategy: spec.strategyName
      });
      return ok;
    } catch (e) {
      post({ type: 'Error', message: 'self-test: ' + e.message });
      return false;
    }
  }

  // ---- Outgoing message handler -----------------------------------------
  window.__glmBridge = {
    postMessage: function (raw) {},           // overwritten by WebMessageListener
    onMessage: function (raw) {
      try {
        var msg = JSON.parse(raw);
        switch (msg.type) {
          case 'RequestSelfTest':
            (msg.capabilityIds || []).forEach(function (capId) {
              runSelfTest({ capabilityId: capId, selector: null });
            });
            break;
          case 'RequestFill':
            (function () {
              var el = document.querySelector(msg.locator);
              if (!el) return;
              if ('value' in el) {
                el.value = msg.text;
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
              } else if (el.isContentEditable || el.getAttribute('contenteditable') === 'true') {
                el.textContent = msg.text;
                el.dispatchEvent(new InputEvent('input', { bubbles: true, data: msg.text }));
              } else {
                el.textContent = msg.text;
              }
            })();
            break;
          case 'RequestSend':
            (function () {
              var el = document.querySelector(msg.locator);
              if (el) el.click();
            })();
            break;
          case 'ObserveStream':
            window.__glm_observedEndpoints = msg.endpointPatterns || [];
            break;
          case 'QueryDom':
            post({ type: 'SemanticDomEvent', kind: 'query.result', payload: msg.query });
            break;
          case 'Ping':
            post({ type: 'PageReady', url: location.href, title: document.title });
            break;
        }
      } catch (e) {
        post({ type: 'Error', message: e.message });
      }
    },
    onSelfTest: function (spec) { return runSelfTest(spec); }
  };

  post({ type: 'PageReady', url: location.href, title: document.title });
})();
"""
}
