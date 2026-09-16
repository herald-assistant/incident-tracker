(function configureTdwBrowserToolsDemo(global) {
  'use strict';

  global.__TDW_BROWSER_TOOL_CONFIG__ = Object.freeze({
    schema: 'tdw.browser-tool-launcher',
    version: 3,
    featureId: 'ux-inspector',
    tdwOrigin: global.location.origin
  });
})(globalThis);
