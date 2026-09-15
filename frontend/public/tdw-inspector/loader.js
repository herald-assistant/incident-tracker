(function loadTdwBrowserTool(global) {
  'use strict';

  const LOAD_KEY = '__TDW_BROWSER_TOOL_REMOTE_LOAD__';
  const CONFIG_KEY = '__TDW_BROWSER_TOOL_CONFIG__';
  const LOADER_VERSION = '1.0.0';
  const LOAD_TIMEOUT_MS = 8000;
  const document = global.document;
  const loaderScript = document.currentScript;

  if (!(loaderScript instanceof global.HTMLScriptElement)) {
    reportFailure('Nie mozna ustalic adresu skryptu startowego TDW.');
    return;
  }

  let loaderUrl;
  try {
    loaderUrl = new URL(loaderScript.src);
  } catch {
    reportFailure('Adres skryptu startowego TDW jest niepoprawny.');
    loaderScript.remove();
    return;
  }

  const featureId = loaderScript.dataset.tdwFeatureId || '';
  if (
    !['http:', 'https:'].includes(loaderUrl.protocol) ||
    featureId !== 'ui-explorer-inspector'
  ) {
    reportFailure('Konfiguracja skryptu startowego TDW jest niepoprawna.');
    loaderScript.remove();
    return;
  }

  if (global[LOAD_KEY]) {
    loaderScript.remove();
    return;
  }

  const loadState = {};
  global[LOAD_KEY] = loadState;
  const tdwOrigin = loaderUrl.origin;

  void start();

  async function start() {
    try {
      await loadScript('protocol.js');
      global[CONFIG_KEY] = Object.freeze({
        schema: 'tdw.browser-tool-launcher',
        version: 1,
        featureId,
        tdwOrigin
      });
      await loadScript('runtime.js');
    } catch (error) {
      try {
        delete global[CONFIG_KEY];
      } catch {
        // The inspected page owns the main world and can make globals immutable.
      }
      console.error('[TDW Inspector Lite] Remote runtime loading failed.', error);
      reportFailure(
        'Nie udalo sie pobrac runtime z TDW. Strona mogla zablokowac CSP lub dostep do sieci lokalnej. Uzyj pelnego DevTools Snippetu.'
      );
    } finally {
      loaderScript.remove();
      if (global[LOAD_KEY] === loadState) {
        try {
          delete global[LOAD_KEY];
        } catch {
          global[LOAD_KEY] = null;
        }
      }
    }
  }

  function loadScript(fileName) {
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      const assetUrl = new URL(`/tdw-inspector/${fileName}`, tdwOrigin);
      assetUrl.searchParams.set('v', LOADER_VERSION);
      script.src = assetUrl.toString();
      script.referrerPolicy = 'no-referrer';
      script.async = true;

      let settled = false;
      const timer = global.setTimeout(
        () => finish(reject, new Error(`Timeout loading ${fileName}`)),
        LOAD_TIMEOUT_MS
      );

      script.addEventListener('load', () => finish(resolve), { once: true });
      script.addEventListener(
        'error',
        () => finish(reject, new Error(`Failed to load ${fileName}`)),
        { once: true }
      );
      (document.head || document.documentElement).appendChild(script);

      function finish(callback, value) {
        if (settled) {
          return;
        }
        settled = true;
        global.clearTimeout(timer);
        script.remove();
        callback(value);
      }
    });
  }

  function reportFailure(message) {
    console.error('[TDW Inspector Lite] ' + message);
    if (typeof global.alert === 'function') {
      global.alert('TDW Inspector Lite\n\n' + message);
    }
  }
})(globalThis);
