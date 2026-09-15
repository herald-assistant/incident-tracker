import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { TextEncoder } from 'node:util';

import { JSDOM } from 'jsdom';

const assetRoot = new URL('../../public/tdw-inspector/', import.meta.url);
const protocolSource = await readFile(new URL('protocol.js', assetRoot), 'utf8');
const loaderSource = await readFile(new URL('loader.js', assetRoot), 'utf8');
const runtimeSource = await readFile(new URL('runtime.js', assetRoot), 'utf8');
const captureSource = await readFile(new URL('capture.js', assetRoot), 'utf8');
const captureHtml = await readFile(new URL('capture.html', assetRoot), 'utf8');
const installSource = await readFile(new URL('install.js', assetRoot), 'utf8');
const installHtml = await readFile(new URL('install.html', assetRoot), 'utf8');

function createDom(html, url) {
  const dom = new JSDOM(html, {
    url,
    runScripts: 'outside-only',
    pretendToBeVisual: true
  });
  if (!dom.window.TextEncoder) {
    dom.window.TextEncoder = TextEncoder;
  }
  dom.window.eval(protocolSource);
  return dom;
}

function protocolFor(dom) {
  return dom.window.__TDW_BROWSER_TOOLS_PROTOCOL_V1__;
}

function config(tdwOrigin = 'https://tdw.example.com') {
  return {
    schema: 'tdw.browser-tool-launcher',
    version: 1,
    featureId: 'ui-explorer-inspector',
    tdwOrigin
  };
}

function captureFixture(overrides = {}) {
  return {
    captureVersion: 1,
    captureId: 'cap_0123456789abcdef',
    capturedAt: '2026-09-15T10:00:00.000Z',
    page: {
      origin: 'https://crm.example.com',
      pathname: '/contacts/new',
      routeHash: null,
      queryParameterNames: ['view'],
      title: 'CRM Agent Portal',
      language: 'pl'
    },
    selection: {
      target: {
        tag: 'button',
        role: 'button',
        accessibleName: 'Utwórz kontakt',
        text: 'Utwórz kontakt',
        stableAttributes: { 'data-testid': 'create-contact-button' },
        classes: ['primary-action'],
        state: {
          disabled: true,
          ariaDisabled: false,
          readOnly: false,
          required: false,
          invalid: false,
          checked: null,
          expanded: null,
          hidden: false
        }
      },
      ancestors: [
        {
          depth: 1,
          tag: 'form',
          role: null,
          accessibleName: 'Profil kontaktu',
          text: 'Profil kontaktu',
          stableAttributes: { 'data-testid': 'crm-contact-form' },
          classes: ['contact-form']
        },
        {
          depth: 4,
          tag: 'html',
          role: null,
          accessibleName: 'CRM Agent Portal',
          text: 'CRM Agent Portal',
          stableAttributes: {},
          classes: []
        }
      ],
      traversal: {
        observedDepth: 5,
        emittedNodeCount: 3,
        omittedNodeCount: 2,
        reachedDocumentRoot: true
      },
      shadowBoundaryCount: 0,
      frame: 'TOP_LEVEL',
      viewportBounds: { x: 20, y: 40, width: 160, height: 42 }
    },
    client: {
      name: 'TDW Inspector Lite',
      version: '1.0.0',
      featureId: 'ui-explorer-inspector'
    },
    ...overrides
  };
}

test('capture redacts identifiers and never reads form values or query values', () => {
  const dom = createDom(
    `<!doctype html>
      <html lang="pl">
        <head><title>CRM contact contact@example.invalid</title></head>
        <body>
          <main>
            <form data-testid="crm-contact-form">
              <label for="customer-secret">Primary contact 987654321 contact@example.invalid</label>
              <textarea>DO_NOT_CAPTURE_TEXTAREA_DEFAULT</textarea>
              <select><option>DO_NOT_CAPTURE_OPTION</option></select>
              <div contenteditable="true">DO_NOT_CAPTURE_EDITABLE</div>
              <input
                id="customer-secret"
                data-testid="crm-contact-name"
                name="contactName"
                type="text"
                value="DO_NOT_CAPTURE_THIS_VALUE"
                required
                aria-invalid="true"
              />
            </form>
          </main>
        </body>
      </html>`,
    'https://crm.example.com/customers/12345678?token=raw-secret&view=compact'
  );
  const protocol = protocolFor(dom);
  const input = dom.window.document.querySelector('input');
  input.getBoundingClientRect = () => ({
    x: 12,
    y: 24,
    left: 12,
    top: 24,
    right: 212,
    bottom: 64,
    width: 200,
    height: 40,
    toJSON() {}
  });

  const capture = protocol.captureElement(input, {
    clientVersion: '1.0.0',
    featureId: 'ui-explorer-inspector',
    capturedAt: '2026-09-15T10:00:00.000Z'
  });
  const serialized = JSON.stringify(capture);

  assert.equal(capture.page.pathname, '/customers/:value');
  assert.deepEqual(Array.from(capture.page.queryParameterNames), ['view']);
  assert.equal(capture.page.title, 'CRM contact [EMAIL]');
  assert.equal(capture.selection.target.text, null);
  assert.equal(
    capture.selection.target.accessibleName,
    'Primary contact [NUMBER] [EMAIL]'
  );
  assert.deepEqual(JSON.parse(JSON.stringify(capture.selection.target.stableAttributes)), {
    'data-testid': 'crm-contact-name',
    name: 'contactName',
    type: 'text'
  });
  assert.equal(capture.selection.target.state.required, true);
  assert.equal(capture.selection.target.state.invalid, true);
  assert.equal(capture.selection.traversal.reachedDocumentRoot, true);
  assert.ok(capture.selection.ancestors.length > 0);
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_THIS_VALUE/);
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_TEXTAREA_DEFAULT/);
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_OPTION/);
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_EDITABLE/);
  assert.doesNotMatch(serialized, /raw-secret/);
  assert.doesNotMatch(serialized, /contact@example\.invalid/);
  dom.window.close();
});

test('capture exposes useful button state and compacts ancestry to safe bounds', () => {
  const wrappers = Array.from(
    { length: 40 },
    (_, index) => `<div class="layer-${index}">`
  ).join('');
  const closings = '</div>'.repeat(40);
  const dom = createDom(
    `<!doctype html><html><body><main><section data-testid="crm-editor">
      ${wrappers}
      <button
        class="primary-action build-123456789"
        data-testid="create-contact-button"
        aria-expanded="false"
        disabled
      >Utwórz kontakt</button>
      ${closings}
    </section></main></body></html>`,
    'https://crm.example.com/contacts/new'
  );
  const protocol = protocolFor(dom);
  const button = dom.window.document.querySelector('button');
  const capture = protocol.captureElement(button, {
    clientVersion: '1.0.0',
    featureId: 'ui-explorer-inspector'
  });

  assert.equal(capture.selection.target.role, 'button');
  assert.equal(capture.selection.target.state.disabled, true);
  assert.equal(capture.selection.target.state.expanded, false);
  assert.deepEqual(Array.from(capture.selection.target.classes), ['primary-action']);
  assert.ok(capture.selection.ancestors.length <= 24);
  assert.ok(capture.selection.traversal.omittedNodeCount > 0);
  assert.equal(capture.selection.ancestors.at(-1).tag, 'html');
  dom.window.close();
});

test('protocol rejects forged or oversized captures and strips unknown fields', () => {
  const dom = createDom('<!doctype html><html><body></body></html>', 'https://tdw.example.com/');
  const protocol = protocolFor(dom);
  const candidate = captureFixture();
  candidate.untrustedExtra = '<script>ignored</script>';
  candidate.selection.target.untrustedExtra = 'ignored';

  const normalized = protocol.normalizeCapture(candidate);
  assert.equal(normalized.ok, true);
  assert.equal('untrustedExtra' in normalized.value, false);
  assert.equal('untrustedExtra' in normalized.value.selection.target, false);

  assert.equal(
    protocol.normalizeCapture({ ...candidate, captureVersion: 99 }).ok,
    false
  );
  assert.equal(
    protocol.normalizeCapture({
      ...candidate,
      page: { ...candidate.page, title: 'x'.repeat(70000) }
    }).ok,
    false
  );
  assert.equal(
    protocol.normalizeCapture({
      ...candidate,
      page: { ...candidate.page, origin: 'javascript:alert(1)' }
    }).ok,
    false
  );
  dom.window.close();
});

test('remote bookmarklet is small and both launcher variants have valid JavaScript', () => {
  const dom = createDom('<!doctype html><html><body></body></html>', 'https://tdw.example.com/');
  const protocol = protocolFor(dom);
  const snippetSource = protocol.buildLauncherSource(
    config(),
    '(function(){globalThis.protocolLoaded=true;}());',
    '(function(){globalThis.runtimeLoaded=true;}());'
  );
  const remoteSource = protocol.buildRemoteLauncherSource(config());
  const bookmarkUrl = protocol.toBookmarkUrl(remoteSource);
  const decodedBookmark = decodeURIComponent(bookmarkUrl.slice('javascript:'.length));

  assert.match(bookmarkUrl, /^javascript:/);
  assert.match(decodedBookmark, /https:\/\/tdw\.example\.com\/tdw-inspector\/loader\.js\?v=1\.0\.0/);
  assert.doesNotMatch(decodedBookmark, /installTdwBrowserToolsProtocol|bootstrapTdwBrowserTools/);
  assert.ok(new TextEncoder().encode(bookmarkUrl).byteLength < 2048);
  assert.doesNotThrow(() => new Function(decodedBookmark));
  assert.doesNotThrow(() => new Function(snippetSource));
  assert.match(snippetSource, /\n\(function\(\)\{globalThis\.protocolLoaded/);
  assert.doesNotMatch(snippetSource, /fetch\s*\(/);
  assert.equal(protocol.normalizeLauncherConfig(config('javascript:alert(1)')).ok, false);
  assert.equal(protocol.normalizeLauncherConfig(config('https://tdw.example.com/path')).ok, false);
  dom.window.close();
});

test('installer prepares a draggable bookmarklet and a complete snippet for its own TDW origin', async () => {
  const dom = new JSDOM(installHtml, {
    url: 'https://tdw.example.com/tdw-inspector/install.html',
    runScripts: 'outside-only',
    pretendToBeVisual: true
  });
  const { window } = dom;
  if (!window.TextEncoder) {
    window.TextEncoder = TextEncoder;
  }
  window.fetch = async (path) => ({
    ok: true,
    status: 200,
    text: async () => (String(path).endsWith('protocol.js') ? protocolSource : runtimeSource)
  });
  window.eval(protocolSource);
  window.eval(installSource);
  await new Promise((resolve) => window.setTimeout(resolve, 20));

  const link = window.document.getElementById('bookmarklet-link');
  const snippet = window.document.getElementById('snippet-source');
  const bookmarkSource = decodeURIComponent(
    link.getAttribute('href').slice('javascript:'.length)
  );
  assert.equal(link.getAttribute('aria-disabled'), 'false');
  assert.match(link.getAttribute('href'), /^javascript:/);
  assert.match(bookmarkSource, /https:\/\/tdw\.example\.com\/tdw-inspector\/loader\.js/);
  assert.doesNotThrow(() => new Function(bookmarkSource));
  assert.match(snippet.value, /"tdwOrigin":"https:\/\/tdw\.example\.com"/);
  assert.match(snippet.value, /data-tdw-browser-tool-root/);
  assert.doesNotThrow(() => new Function(snippet.value));
  assert.ok(new TextEncoder().encode(link.getAttribute('href')).byteLength < 2048);
  assert.equal(window.document.getElementById('copy-bookmark-url').disabled, false);
  assert.equal(window.document.getElementById('copy-snippet').disabled, false);
  assert.match(window.document.getElementById('loader-status').textContent, /pobierze runtime tylko/);
  dom.window.close();
});

test('remote loader derives TDW origin and loads protocol before runtime', async () => {
  const dom = new JSDOM(
    `<!doctype html><html><head></head><body>
      <script
        id="tdw-loader"
        src="https://tdw.example.com/tdw-inspector/loader.js?v=1.0.0"
        data-tdw-feature-id="ui-explorer-inspector"
      ></script>
    </body></html>`,
    {
      url: 'https://crm.example.com/contacts',
      runScripts: 'outside-only',
      pretendToBeVisual: true
    }
  );
  const { window } = dom;
  const loader = window.document.getElementById('tdw-loader');
  const loadedAssets = [];
  const alerts = [];
  let runtimeConfig = null;
  Object.defineProperty(window.document, 'currentScript', {
    configurable: true,
    value: loader
  });
  window.alert = (message) => alerts.push(message);
  const appendChild = window.document.head.appendChild.bind(window.document.head);
  window.document.head.appendChild = (node) => {
    const appended = appendChild(node);
    if (node instanceof window.HTMLScriptElement) {
      const assetUrl = new URL(node.src);
      loadedAssets.push(assetUrl.pathname);
      window.setTimeout(() => {
        if (assetUrl.pathname.endsWith('/protocol.js')) {
          window.eval(protocolSource);
        } else if (assetUrl.pathname.endsWith('/runtime.js')) {
          runtimeConfig = JSON.parse(
            JSON.stringify(window.__TDW_BROWSER_TOOL_CONFIG__)
          );
        }
        node.dispatchEvent(new window.Event('load'));
      }, 0);
    }
    return appended;
  };

  window.eval(loaderSource);
  await new Promise((resolve) => window.setTimeout(resolve, 30));

  assert.deepEqual(loadedAssets, [
    '/tdw-inspector/protocol.js',
    '/tdw-inspector/runtime.js'
  ]);
  assert.deepEqual(runtimeConfig, config());
  assert.deepEqual(alerts, []);
  assert.equal(loader.isConnected, false);
  assert.equal(window.__TDW_BROWSER_TOOL_REMOTE_LOAD__, undefined);
  dom.window.close();
});

test('runtime selects through its shield and transfers exactly one capture after strict handshake', async () => {
  const dom = createDom(
    '<!doctype html><html><body><button data-testid="crm-save">Save CRM contact</button></body></html>',
    'https://crm.example.com/contacts/new?view=compact'
  );
  const { window } = dom;
  const protocol = protocolFor(dom);
  const posted = [];
  let openedUrl = '';
  const bridgeWindow = {
    postMessage(message, targetOrigin) {
      posted.push({ message, targetOrigin });
    }
  };
  Object.defineProperty(window, 'open', {
    configurable: true,
    value(url) {
      openedUrl = String(url);
      return bridgeWindow;
    }
  });
  window.__TDW_INSPECTOR_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);

  const host = window.document.querySelector('[data-tdw-browser-tool-root]');
  assert.ok(host);
  assert.ok(host.shadowRoot);
  const shield = host.shadowRoot.querySelector('.tdw-selection-shield');
  const highlight = host.shadowRoot.querySelector('.tdw-highlight');
  const target = window.document.querySelector('button');
  target.getBoundingClientRect = () => ({
    x: 30,
    y: 70,
    left: 30,
    top: 70,
    right: 210,
    bottom: 112,
    width: 180,
    height: 42,
    toJSON() {}
  });
  window.document.elementsFromPoint = () => [host, target];
  let nativeClicks = 0;
  target.addEventListener('click', () => {
    nativeClicks += 1;
  });

  shield.dispatchEvent(
    new window.MouseEvent('pointermove', { bubbles: true, clientX: 60, clientY: 80 })
  );
  await new Promise((resolve) => window.setTimeout(resolve, 25));
  assert.equal(highlight.dataset.visible, 'true');
  assert.match(highlight.querySelector('.tdw-highlight-label').textContent, /button/);

  const blockedClick = new window.MouseEvent('click', {
    bubbles: true,
    cancelable: true,
    button: 0
  });
  shield.dispatchEvent(blockedClick);
  assert.equal(blockedClick.defaultPrevented, true);
  assert.equal(nativeClicks, 0);
  assert.match(openedUrl, /^https:\/\/tdw\.example\.com\/tdw-inspector\/capture\.html#/);

  const nonce = new URL(openedUrl).hash
    .slice(1);
  const nonceValue = new URLSearchParams(nonce).get('nonce');
  window.dispatchEvent(
    new window.MessageEvent('message', {
      origin: 'https://evil.example.com',
      source: bridgeWindow,
      data: protocol.createMessage('TDW_INSPECTOR_READY', nonceValue)
    })
  );
  assert.equal(posted.length, 0);

  window.dispatchEvent(
    new window.MessageEvent('message', {
      origin: 'https://tdw.example.com',
      source: bridgeWindow,
      data: protocol.createMessage('TDW_INSPECTOR_READY', nonceValue)
    })
  );
  assert.equal(posted.length, 1);
  assert.equal(posted[0].targetOrigin, 'https://tdw.example.com');
  assert.equal(posted[0].message.type, 'TDW_INSPECTOR_CAPTURE');
  assert.equal(posted[0].message.capture.selection.target.text, 'Save CRM contact');

  window.dispatchEvent(
    new window.MessageEvent('message', {
      origin: 'https://tdw.example.com',
      source: bridgeWindow,
      data: protocol.createMessage('TDW_INSPECTOR_RECEIVED', nonceValue, {
        captureId: posted[0].message.capture.captureId
      })
    })
  );
  await new Promise((resolve) => window.setTimeout(resolve, 5));
  assert.equal(window.document.querySelector('[data-tdw-browser-tool-root]'), null);
  dom.window.close();
});

test('relaunch disposes the previous singleton and Escape leaves no overlay', () => {
  const dom = createDom('<!doctype html><html><body><main>CRM</main></body></html>', 'https://crm.example.com/');
  const { window } = dom;
  window.__TDW_INSPECTOR_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);
  assert.equal(window.document.querySelectorAll('[data-tdw-browser-tool-root]').length, 1);

  window.eval(protocolSource);
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);
  assert.equal(window.document.querySelectorAll('[data-tdw-browser-tool-root]').length, 1);

  window.dispatchEvent(
    new window.KeyboardEvent('keydown', {
      key: 'Escape',
      bubbles: true,
      cancelable: true
    })
  );
  assert.equal(window.document.querySelectorAll('[data-tdw-browser-tool-root]').length, 0);
  dom.window.close();
});

test('trusted capture page validates source, renders preview and returns dummy accepted without REST', async () => {
  const nonce = 'n_0123456789abcdef012345';
  const dom = new JSDOM(captureHtml, {
    url:
      `https://tdw.example.com/tdw-inspector/capture.html#nonce=${nonce}&sourceOrigin=https%3A%2F%2Fcrm.example.com`,
    runScripts: 'outside-only',
    pretendToBeVisual: true
  });
  const { window } = dom;
  if (!window.TextEncoder) {
    window.TextEncoder = TextEncoder;
  }
  const posted = [];
  const opener = {
    postMessage(message, targetOrigin) {
      posted.push({ message, targetOrigin });
    }
  };
  Object.defineProperty(window, 'opener', {
    configurable: true,
    value: opener
  });
  window.__TDW_INSPECTOR_TEST_MODE__ = true;
  window.fetch = () => {
    throw new Error('Capture demo must not call REST.');
  };
  window.matchMedia = () => ({ matches: true });
  window.HTMLElement.prototype.scrollIntoView = function scrollIntoView() {};
  window.eval(protocolSource);
  const protocol = protocolFor(dom);
  window.eval(captureSource);

  assert.equal(posted.length, 1);
  assert.equal(posted[0].message.type, 'TDW_INSPECTOR_READY');
  assert.equal(posted[0].targetOrigin, 'https://crm.example.com');

  window.dispatchEvent(
    new window.MessageEvent('message', {
      origin: 'https://evil.example.com',
      source: opener,
      data: protocol.createMessage('TDW_INSPECTOR_CAPTURE', nonce, {
        capture: captureFixture()
      })
    })
  );
  assert.equal(window.document.getElementById('capture-workspace').hidden, true);

  window.dispatchEvent(
    new window.MessageEvent('message', {
      origin: 'https://crm.example.com',
      source: opener,
      data: protocol.createMessage('TDW_INSPECTOR_CAPTURE', nonce, {
        capture: captureFixture()
      })
    })
  );
  assert.equal(window.document.getElementById('capture-workspace').hidden, false);
  assert.match(window.document.getElementById('target-heading').textContent, /button/);
  assert.match(window.document.getElementById('capture-json').textContent, /create-contact-button/);
  assert.equal(posted.at(-1).message.type, 'TDW_INSPECTOR_RECEIVED');

  const question = window.document.getElementById('question');
  question.value = 'Dlaczego przycisk jest wyszarzony?';
  window.document
    .getElementById('analysis-form')
    .dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));
  assert.equal(window.document.getElementById('dummy-result').hidden, false);
  assert.match(window.document.getElementById('result-detail').textContent, /DUMMY · QUEUED/);
  assert.equal(window.document.getElementById('start-analysis').disabled, true);

  await new Promise((resolve) => window.setTimeout(resolve, 0));
  dom.window.close();
});

test('runtime bundle contains no network, storage, cookie or dynamic-code capability', () => {
  const combinedRuntime = protocolSource + '\\n' + runtimeSource;
  assert.doesNotMatch(combinedRuntime, /\bfetch\s*\(/);
  assert.doesNotMatch(combinedRuntime, /\bXMLHttpRequest\b/);
  assert.doesNotMatch(combinedRuntime, /\blocalStorage\b|\bsessionStorage\b/);
  assert.doesNotMatch(combinedRuntime, /\bdocument\.cookie\b/);
  assert.doesNotMatch(combinedRuntime, /\beval\s*\(|\bnew\s+Function\b/);
  assert.doesNotMatch(loaderSource, /\bfetch\s*\(|\bXMLHttpRequest\b/);
  assert.doesNotMatch(loaderSource, /\blocalStorage\b|\bsessionStorage\b/);
  assert.doesNotMatch(loaderSource, /\bdocument\.cookie\b/);
  assert.doesNotMatch(loaderSource, /\beval\s*\(|\bnew\s+Function\b/);
});
