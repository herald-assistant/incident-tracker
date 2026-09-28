import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { TextEncoder } from 'node:util';

import { JSDOM } from 'jsdom';

const assetRoot = new URL('../../public/browser-tools/', import.meta.url);
const protocolSource = await readFile(new URL('protocol.js', assetRoot), 'utf8');
const loaderSource = await readFile(new URL('loader.js', assetRoot), 'utf8');
const runtimeSource = await readFile(new URL('runtime.js', assetRoot), 'utf8');

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
    featureId: 'browser-tools',
    tdwOrigin
  };
}

function captureFixture(overrides = {}) {
  return {
    schema: 'tdw.ux-inspector-capture',
    version: 2,
    captureId: 'cap_0123456789abcdef',
    capturedAt: '2026-09-15T10:00:00.000Z',
    captureProfile: 'ELEMENT_CONTEXT',
    page: {
      origin: 'https://crm.example.com',
      path: '/contacts/new',
      title: 'CRM Agent Portal',
      language: 'pl',
      queryParameterNames: ['view']
    },
    target: {
      tag: 'button',
      role: 'button',
      accessibleName: 'Utwórz kontakt',
      text: 'Utwórz kontakt',
      domFingerprint: {
        stableAttributes: { 'data-testid': 'create-contact-button' },
        selectorCandidates: ['button[data-testid="create-contact-button"]'],
        componentBoundaryTags: ['crm-contact-form'],
        labelFor: null
      },
      state: {
        disabled: true,
        ariaDisabled: false,
        readOnly: false,
        required: false,
        invalid: false,
        checked: null,
        expanded: null,
        hidden: false
      },
      bounds: { x: 20, y: 40, width: 160, height: 42 }
    },
    ancestors: [
      {
        depth: 1,
        tag: 'form',
        role: null,
        accessibleName: 'Profil kontaktu',
        text: 'Profil kontaktu',
        stableAttributes: { 'data-testid': 'crm-contact-form' }
      },
      {
        depth: 4,
        tag: 'html',
        role: null,
        accessibleName: 'CRM Agent Portal',
        text: 'CRM Agent Portal',
        stableAttributes: {}
      }
    ],
    traversal: {
      observedDepth: 5,
      emittedNodeCount: 3,
      omittedNodeCount: 2,
      reachedDocumentRoot: true
    },
    signals: {
      shadowBoundaryCount: 0,
      frame: 'TOP_LEVEL',
      redactions: []
    },
    limits: [],
    client: {
      name: 'TDW UX Inspector',
      version: '1.0.0',
      featureId: 'ux-inspector'
    },
    ...overrides
  };
}

test('element context capture redacts identifiers and never reads form values or query values', () => {
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
    featureId: 'ux-inspector',
    capturedAt: '2026-09-15T10:00:00.000Z'
  });
  const serialized = JSON.stringify(capture);

  assert.equal(capture.schema, 'tdw.ux-inspector-capture');
  assert.equal(capture.version, 2);
  assert.equal(capture.captureProfile, 'ELEMENT_CONTEXT');
  assert.equal(capture.page.path, '/customers/:value');
  assert.deepEqual(Array.from(capture.page.queryParameterNames), ['view']);
  assert.equal(capture.page.title, 'CRM contact [EMAIL]');
  assert.equal(capture.target.text, null);
  assert.equal(
    capture.target.accessibleName,
    'Primary contact [NUMBER] [EMAIL]'
  );
  assert.deepEqual(JSON.parse(JSON.stringify(capture.target.domFingerprint.stableAttributes)), {
    'data-testid': 'crm-contact-name',
    name: 'contactName',
    type: 'text'
  });
  assert.equal(capture.target.state.required, true);
  assert.equal(capture.target.state.invalid, true);
  assert.equal(capture.traversal.reachedDocumentRoot, true);
  assert.ok(capture.ancestors.length > 0);
  assert.equal('formSnapshot' in capture, false);
  assert.ok(capture.signals.redactions.includes('FORM_VALUES_NOT_REQUESTED'));
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_THIS_VALUE/);
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_TEXTAREA_DEFAULT/);
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_OPTION/);
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_EDITABLE/);
  assert.doesNotMatch(serialized, /raw-secret/);
  assert.doesNotMatch(serialized, /contact@example\.invalid/);
  dom.window.close();
});

test('form diagnostics reads every safe visible field on the page without a field-count limit', () => {
  const extra = Array.from({ length: 70 }, (_, index) =>
    `<input name="contactNote${index}" value="CRM note ${index}">`).join('');
  const dom = createDom(
    `<!doctype html><html><body>
      <crm-contact>
        <form id="contact-form">
          <label for="email">E-mail</label>
          <input id="email" name="email" value="customer@example.test" required>
          ${extra}
          <input name="password" type="password" value="DO_NOT_CAPTURE_PASSWORD">
          <input name="csrfToken" value="DO_NOT_CAPTURE_TOKEN">
          <input name="internalReference" type="hidden" value="crm-contact-42">
          <div style="display:none"><input name="invisible" value="DO_NOT_CAPTURE_HIDDEN"></div>
        </form>
        <form><label for="other">Other contact</label><input id="other" value="CRM second form"></form>
      </crm-contact>
    </body></html>`,
    'https://crm.example.com/contacts/new'
  );
  const protocol = protocolFor(dom);
  const input = dom.window.document.getElementById('email');
  const fields = protocol.captureVisibleFormFields();
  const capture = protocol.captureElement(input, { captureProfile: 'FORM_DIAGNOSTICS' });
  const serialized = JSON.stringify(fields);
  assert.equal(fields.length, 72);
  assert.equal(fields[0].label, 'E-mail');
  assert.equal(fields[0].value, 'customer@example.test');
  assert.equal(fields.at(-1).value, 'CRM second form');
  assert.equal('formSnapshot' in capture, false);
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_|crm-contact-42/);
  assert.equal(protocol.normalizeCapture(capture).ok, true);
  dom.window.close();
});

test('capture exposes useful button state and keeps every DOM ancestor', () => {
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
    featureId: 'ux-inspector'
  });

  assert.equal(capture.target.role, 'button');
  assert.equal(capture.target.state.disabled, true);
  assert.equal(capture.target.state.expanded, false);
  assert.equal('classes' in capture.target, false);
  assert.ok(capture.target.domFingerprint.selectorCandidates.includes(
    'button[class~="primary-action"]'
  ));
  assert.equal(capture.target.domFingerprint.selectorCandidates.some(
    (candidate) => candidate.includes('build-123456789')
  ), false);
  assert.equal(capture.ancestors.length, 44);
  assert.equal(capture.traversal.omittedNodeCount, 0);
  assert.equal(capture.traversal.emittedNodeCount, capture.traversal.observedDepth);
  assert.equal(capture.limits.includes('ANCESTORS_TRUNCATED'), false);
  assert.equal(capture.ancestors.at(-1).tag, 'html');
  dom.window.close();
});

test('UX capture keeps every custom-element boundary in the full ancestor chain', () => {
  const tags = Array.from({ length: 30 }, (_, index) => `crm-layer-${index + 1}`);
  const openings = tags.map((tag) => `<${tag}>`).join('');
  const closings = [...tags].reverse().map((tag) => `</${tag}>`).join('');
  const dom = createDom(
    `<!doctype html><html><body>${openings}<button data-testid="contact-save">Zapisz</button>${closings}</body></html>`,
    'https://crm.example.com/contacts/new'
  );
  const capture = protocolFor(dom).captureElement(dom.window.document.querySelector('button'), {});

  assert.deepEqual(Array.from(capture.target.domFingerprint.componentBoundaryTags), [...tags].reverse());
  assert.equal(capture.ancestors.filter((ancestor) => ancestor.tag.startsWith('crm-layer-')).length, 30);
  assert.ok(capture.ancestors.length > 24);
  assert.equal(capture.traversal.emittedNodeCount, capture.ancestors.length + 1);
  assert.equal(capture.limits.includes('ANCESTORS_TRUNCATED'), false);
  dom.window.close();
});

test('protocol rejects forged, unknown-field or oversized captures', () => {
  const dom = createDom('<!doctype html><html><body></body></html>', 'https://tdw.example.com/');
  const protocol = protocolFor(dom);
  const candidate = captureFixture();
  candidate.untrustedExtra = '<script>ignored</script>';
  candidate.target.untrustedExtra = 'ignored';

  const normalized = protocol.normalizeCapture(candidate);
  assert.equal(normalized.ok, false);
  assert.equal(
    protocol.normalizeCapture(
      captureFixture({
        client: {
          name: 'TDW Inspector Lite',
          version: '1.0.0',
          featureId: 'ux-inspector'
        }
      })
    ).ok,
    false
  );

  assert.equal(
    protocol.normalizeCapture({ ...captureFixture(), version: 3 }).ok,
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

test('remote loader derives TDW origin and loads protocol before runtime', async () => {
  const dom = new JSDOM(
    `<!doctype html><html><head></head><body>
      <script
        id="tdw-loader"
        src="https://tdw.example.com/browser-tools/loader.js?v=1.3.0"
        data-tdw-feature-id="ux-inspector"
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
    '/browser-tools/protocol.js',
    '/browser-tools/runtime.js'
  ]);
  assert.deepEqual(runtimeConfig, { ...config(), featureId: 'ux-inspector' });
  assert.deepEqual(alerts, []);
  assert.equal(loader.isConnected, false);
  assert.equal(window.__TDW_BROWSER_TOOL_REMOTE_LOAD__, undefined);
  dom.window.close();
});

test('runtime mounts a bottom-right Browser Tools menu and starts the light UX Inspector on demand', async () => {
  const dom = createDom(
    '<!doctype html><html><body><main><button id="page-action">CRM action</button></main></body></html>',
    'https://crm.example.com/contacts'
  );
  const { window } = dom;
  window.__TDW_BROWSER_TOOLS_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);

  const shell = window.document.querySelector(
    '[data-tdw-browser-tool-root="browser-tools-shell"]'
  );
  assert.ok(shell?.shadowRoot);
  assert.equal(shell.getAttribute('data-tdw-browser-tool-version'), '1.4.0');
  assert.equal(
    window.document.querySelector('[data-tdw-browser-tool-root="ux-inspector"]'),
    null
  );
  let pageClicks = 0;
  const pageAction = window.document.getElementById('page-action');
  pageAction.addEventListener('click', () => {
    pageClicks += 1;
  });
  pageAction.click();
  assert.equal(pageClicks, 1);

  const launcher = shell.shadowRoot.querySelector('.tdw-tools-launcher');
  const menu = shell.shadowRoot.querySelector('.tdw-tools-menu');
  const action = shell.shadowRoot.querySelector(
    '.tdw-tool-action[data-feature-id="ux-inspector"]'
  );
  const launcherLogo = launcher.querySelector('.tdw-tools-launcher__icon');
  assert.equal(launcherLogo?.tagName, 'IMG');
  assert.equal(launcherLogo.src, 'https://tdw.example.com/assets/brand/main-logo.png');
  assert.equal(launcherLogo.referrerPolicy, 'no-referrer');
  assert.equal(menu.hidden, true);
  assert.equal(launcher.getAttribute('aria-expanded'), 'false');

  launcher.click();
  await new Promise((resolve) => window.setTimeout(resolve, 0));
  assert.equal(menu.hidden, false);
  assert.equal(launcher.getAttribute('aria-expanded'), 'true');
  assert.equal(menu.querySelector('.tdw-tools-menu__heading strong').textContent, 'Browser tools');
  assert.equal(
    menu.querySelector('.tdw-tools-menu__brand-icon').src,
    'https://tdw.example.com/assets/brand/main-logo.png'
  );
  assert.doesNotMatch(
    menu.textContent,
    /TDW Browser Tools|Dostępne narzędzia|Kod działa tylko w tej karcie|Usuń narzędzia ze strony/
  );
  assert.match(action.textContent, /UX Inspector/);
  const explorerAction = menu.querySelector('.tdw-tool-action[data-feature-id="ui-explorer"]');
  assert.match(explorerAction.textContent, /UI Explorer/);
  assert.equal(menu.querySelector('.tdw-capture-profile').hidden, true);
  action.click();
  assert.equal(menu.querySelector('.tdw-capture-profile').hidden, false);
  const profiles = menu.querySelectorAll('.tdw-capture-profile input');
  assert.equal(profiles.length, 2);
  assert.equal(profiles[0].value, 'ELEMENT_CONTEXT');
  assert.equal(profiles[0].checked, true);
  profiles[1].checked = true;
  profiles[1].dispatchEvent(new window.Event('change', { bubbles: true }));

  menu.querySelector('.tdw-capture-profile__start').click();
  const inspector = window.document.querySelector(
    '[data-tdw-browser-tool-root="ux-inspector"]'
  );
  assert.ok(inspector?.shadowRoot);
  assert.equal(shell.hidden, true);
  assert.equal(inspector.shadowRoot.querySelector('.tdw-selection-shield').dataset.active, 'true');
  assert.equal(
    inspector.shadowRoot.querySelector('.tdw-status strong').textContent,
    'TDW UX Inspector'
  );
  assert.match(inspector.shadowRoot.querySelector('.tdw-status').textContent, /dołączę widoczne pola strony/);
  assert.match(inspector.shadowRoot.querySelector('.tdw-status').textContent, /Esc wyjdź/);
  assert.match(inspector.shadowRoot.querySelector('style').textContent, /background: #fbfcfe/);
  assert.doesNotMatch(
    inspector.shadowRoot.querySelector('style').textContent,
    /rgba\(7, 29, 73, 0\.97\)/
  );

  window.dispatchEvent(
    new window.KeyboardEvent('keydown', {
      key: 'Escape',
      bubbles: true,
      cancelable: true
    })
  );
  assert.equal(
    window.document.querySelector('[data-tdw-browser-tool-root="ux-inspector"]'),
    null
  );
  assert.equal(shell.hidden, false);
  dom.window.close();
});

test('new runtime replaces an older Browser Tools instance on the same page', () => {
  const dom = createDom('<!doctype html><html><body></body></html>', 'https://crm.example.com/');
  const { window } = dom;
  const staleHost = window.document.createElement('div');
  staleHost.setAttribute('data-tdw-browser-tool-version', '1.2.0');
  let disposed = false;
  let revealed = false;
  window.__TDW_BROWSER_TOOLS_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.__TDW_BROWSER_TOOL_ACTIVE_INSTANCE__ = {
    host: staleHost,
    reveal() { revealed = true; },
    dispose() { disposed = true; }
  };

  window.eval(runtimeSource);

  assert.equal(disposed, true);
  assert.equal(revealed, false);
  assert.equal(
    window.document.querySelector('[data-tdw-browser-tool-root="browser-tools-shell"]')
      ?.getAttribute('data-tdw-browser-tool-version'),
    '1.4.0'
  );
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
  window.__TDW_BROWSER_TOOLS_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);

  const shell = window.document.querySelector(
    '[data-tdw-browser-tool-root="browser-tools-shell"]'
  );
  assert.ok(shell?.shadowRoot);
  assert.equal(
    window.document.querySelector('[data-tdw-browser-tool-root="ux-inspector"]'),
    null
  );
  shell.shadowRoot.querySelector('.tdw-tools-launcher').click();
  shell.shadowRoot.querySelector('.tdw-tool-action[data-feature-id="ux-inspector"]').click();
  shell.shadowRoot.querySelector('.tdw-capture-profile__start').click();

  const host = window.document.querySelector(
    '[data-tdw-browser-tool-root="ux-inspector"]'
  );
  assert.ok(host?.shadowRoot);
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
  window.document.elementsFromPoint = () => [host, shell, target];
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
  assert.match(openedUrl, /^https:\/\/tdw\.example\.com\/ux-inspector#/);

  const nonce = new URL(openedUrl).hash
    .slice(1);
  const nonceValue = new URLSearchParams(nonce).get('nonce');
  window.dispatchEvent(
    new window.MessageEvent('message', {
      origin: 'https://evil.example.com',
      source: bridgeWindow,
      data: protocol.createMessage('TDW_UX_INSPECTOR_READY', nonceValue)
    })
  );
  assert.equal(posted.length, 0);

  window.dispatchEvent(
    new window.MessageEvent('message', {
      origin: 'https://tdw.example.com',
      source: bridgeWindow,
      data: protocol.createMessage('TDW_UX_INSPECTOR_READY', 'n_wrong_nonce_0123456789abcdef')
    })
  );
  assert.equal(posted.length, 0);

  window.dispatchEvent(
    new window.MessageEvent('message', {
      origin: 'https://tdw.example.com',
      source: bridgeWindow,
      data: protocol.createMessage('TDW_UX_INSPECTOR_READY', nonceValue)
    })
  );
  assert.equal(posted.length, 1);
  assert.equal(posted[0].targetOrigin, 'https://tdw.example.com');
  assert.equal(posted[0].message.type, 'TDW_UX_INSPECTOR_CAPTURE');
  assert.equal(posted[0].message.captureId, posted[0].message.capture.captureId);
  assert.equal(posted[0].message.capture.target.text, 'Save CRM contact');

  window.dispatchEvent(
    new window.MessageEvent('message', {
      origin: 'https://tdw.example.com',
      source: bridgeWindow,
      data: protocol.createMessage('TDW_UX_INSPECTOR_READY', nonceValue)
    })
  );
  assert.equal(posted.length, 1);

  window.dispatchEvent(
    new window.MessageEvent('message', {
      origin: 'https://tdw.example.com',
      source: bridgeWindow,
      data: protocol.createMessage('TDW_UX_INSPECTOR_RECEIVED', nonceValue, {
        captureId: posted[0].message.capture.captureId
      })
    })
  );
  await new Promise((resolve) => window.setTimeout(resolve, 5));
  assert.equal(
    window.document.querySelector('[data-tdw-browser-tool-root="ux-inspector"]'),
    null
  );
  assert.equal(shell.hidden, false);
  assert.equal(
    window.document.querySelectorAll('[data-tdw-browser-tool-root="browser-tools-shell"]').length,
    1
  );
  shell.shadowRoot.querySelector('.tdw-tools-launcher').click();
  assert.equal(shell.shadowRoot.querySelector('.tdw-capture-profile').hidden, true);
  assert.equal(
    shell.shadowRoot.querySelector('.tdw-tool-action[data-feature-id="ux-inspector"]').getAttribute('aria-expanded'),
    'false'
  );
  dom.window.close();
});

test('runtime fails closed and discards capture when the UX Inspector window is blocked', async () => {
  const dom = createDom(
    '<!doctype html><html><body><button data-testid="crm-save">Save CRM contact</button></body></html>',
    'https://crm.example.com/contacts/new'
  );
  const { window } = dom;
  Object.defineProperty(window, 'open', {
    configurable: true,
    value() {
      return null;
    }
  });
  window.__TDW_BROWSER_TOOLS_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);

  const shell = window.document.querySelector(
    '[data-tdw-browser-tool-root="browser-tools-shell"]'
  );
  shell.shadowRoot.querySelector('.tdw-tools-launcher').click();
  shell.shadowRoot.querySelector('.tdw-tool-action[data-feature-id="ux-inspector"]').click();
  shell.shadowRoot.querySelector('.tdw-capture-profile__start').click();
  const inspector = window.document.querySelector(
    '[data-tdw-browser-tool-root="ux-inspector"]'
  );
  const shield = inspector.shadowRoot.querySelector('.tdw-selection-shield');
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
  window.document.elementsFromPoint = () => [inspector, shell, target];

  shield.dispatchEvent(
    new window.MouseEvent('pointermove', { bubbles: true, clientX: 60, clientY: 80 })
  );
  await new Promise((resolve) => window.setTimeout(resolve, 25));
  shield.dispatchEvent(
    new window.MouseEvent('click', { bubbles: true, cancelable: true, button: 0 })
  );
  await new Promise((resolve) => window.setTimeout(resolve, 5));

  assert.equal(
    window.document.querySelector('[data-tdw-browser-tool-root="ux-inspector"]'),
    null
  );
  assert.equal(shell.hidden, false);
  assert.equal(inspector.shadowRoot.querySelector('.tdw-fallback-layer'), null);
  dom.window.close();
});

test('relaunch reuses the shell, Escape exits only the tool and menu X cleans everything', () => {
  const dom = createDom('<!doctype html><html><body><main>CRM</main></body></html>', 'https://crm.example.com/');
  const { window } = dom;
  window.__TDW_BROWSER_TOOLS_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);
  assert.equal(window.document.querySelectorAll('[data-tdw-browser-tool-root]').length, 1);

  const firstShell = window.document.querySelector(
    '[data-tdw-browser-tool-root="browser-tools-shell"]'
  );
  const launcher = firstShell.shadowRoot.querySelector('.tdw-tools-launcher');
  const inspectorButton = firstShell.shadowRoot.querySelector('.tdw-tool-action[data-feature-id="ux-inspector"]');
  const profileFieldset = firstShell.shadowRoot.querySelector('.tdw-capture-profile');
  launcher.click();
  inspectorButton.click();
  assert.equal(profileFieldset.hidden, false);
  launcher.click();
  launcher.click();
  assert.equal(profileFieldset.hidden, true);
  assert.equal(inspectorButton.getAttribute('aria-expanded'), 'false');
  inspectorButton.click();
  firstShell.shadowRoot.querySelector('.tdw-capture-profile__start').click();
  assert.equal(window.document.querySelectorAll('[data-tdw-browser-tool-root]').length, 2);

  window.dispatchEvent(
    new window.KeyboardEvent('keydown', {
      key: 'Escape',
      bubbles: true,
      cancelable: true
    })
  );
  assert.equal(window.document.querySelectorAll('[data-tdw-browser-tool-root]').length, 1);
  assert.equal(firstShell.hidden, false);
  launcher.click();
  assert.equal(profileFieldset.hidden, true);
  assert.equal(inspectorButton.getAttribute('aria-expanded'), 'false');

  window.eval(protocolSource);
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);
  assert.equal(window.document.querySelectorAll('[data-tdw-browser-tool-root]').length, 1);
  assert.equal(
    window.document.querySelector('[data-tdw-browser-tool-root="browser-tools-shell"]'),
    firstShell
  );
  assert.equal(firstShell.shadowRoot.querySelector('.tdw-tools-menu').hidden, false);

  const removeButton = firstShell.shadowRoot.querySelector('.tdw-tools-menu__close');
  assert.equal(removeButton.getAttribute('aria-label'), 'Usuń Browser Tools ze strony');
  removeButton.click();
  assert.equal(window.document.querySelectorAll('[data-tdw-browser-tool-root]').length, 0);
  dom.window.close();
});

test('UX Inspector transfers a frozen, redacted store after capture receipt', async () => {
  const dom = createDom('<!doctype html><html><body><button data-testid="crm-save">Save</button></body></html>', 'https://crm.example.com/contacts/new');
  const { window } = dom;
  const protocol = protocolFor(dom);
  const posted = [];
  let openedUrl = '';
  const bridgeWindow = { closed: false, location: { set href(value) { openedUrl = value; } },
    postMessage(message) { posted.push(message); } };
  window.open = (url) => { openedUrl = String(url); return bridgeWindow; };
  window.getStoreState = () => ({ contacts: { editable: false }, accessToken: 'crm-private' });
  window.__TDW_BROWSER_TOOLS_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);
  const shell = window.document.querySelector('[data-tdw-browser-tool-root="browser-tools-shell"]');
  shell.shadowRoot.querySelector('.tdw-tools-launcher').click();
  shell.shadowRoot.querySelector('.tdw-tool-action[data-feature-id="ux-inspector"]').click();
  shell.shadowRoot.querySelector('.tdw-capture-profile__start').click();
  const host = window.document.querySelector('[data-tdw-browser-tool-root="ux-inspector"]');
  const target = window.document.querySelector('button');
  target.getBoundingClientRect = () => ({ x: 10, y: 10, left: 10, top: 10, right: 100, bottom: 40,
    width: 90, height: 30, toJSON() {} });
  window.document.elementsFromPoint = () => [host, shell, target];
  const shield = host.shadowRoot.querySelector('.tdw-selection-shield');
  shield.dispatchEvent(new window.MouseEvent('pointermove', { bubbles: true, clientX: 20, clientY: 20 }));
  await new Promise((resolve) => window.setTimeout(resolve, 25));
  shield.dispatchEvent(new window.MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }));
  await new Promise((resolve) => window.setTimeout(resolve, 0));
  const nonce = new URLSearchParams(new URL(openedUrl).hash.slice(1)).get('nonce');
  const send = (type, extra = {}) => window.dispatchEvent(new window.MessageEvent('message', {
    origin: 'https://tdw.example.com', source: bridgeWindow, data: protocol.createMessage(type, nonce, extra)
  }));
  send('TDW_UX_INSPECTOR_READY');
  assert.equal(posted[0].storeStatus, 'AVAILABLE');
  assert.equal(posted[0].storeChunks, 1);
  send('TDW_UX_INSPECTOR_RECEIVED', { captureId: posted[0].captureId });
  assert.equal(posted[1].type, 'TDW_UX_INSPECTOR_STORE_CHUNK');
  assert.deepEqual(JSON.parse(posted[1].chunk), { contacts: { editable: false }, accessToken: '[redacted]' });
  send('TDW_UX_INSPECTOR_STORE_ACK', { captureId: posted[0].captureId, index: 0 });
  await new Promise((resolve) => window.setTimeout(resolve, 5));
  assert.equal(window.document.querySelector('[data-tdw-browser-tool-root="ux-inspector"]'), null);
  dom.window.close();
});

test('UX Inspector transfers visible form fields separately after capture receipt', async () => {
  const dom = createDom(
    '<!doctype html><html><body><label for="contact">Contact</label><input id="contact" value="CRM customer"><button>Save</button></body></html>',
    'https://crm.example.com/contacts/new'
  );
  const { window } = dom;
  const protocol = protocolFor(dom);
  const posted = [];
  let openedUrl = '';
  const bridgeWindow = { postMessage(message) { posted.push(message); } };
  window.open = (url) => { openedUrl = String(url); return bridgeWindow; };
  window.__TDW_BROWSER_TOOLS_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);
  const shell = window.document.querySelector('[data-tdw-browser-tool-root="browser-tools-shell"]');
  shell.shadowRoot.querySelector('.tdw-tools-launcher').click();
  shell.shadowRoot.querySelector('.tdw-tool-action[data-feature-id="ux-inspector"]').click();
  const formOption = shell.shadowRoot.querySelectorAll('.tdw-capture-profile input')[1];
  formOption.checked = true;
  formOption.dispatchEvent(new window.Event('change', { bubbles: true }));
  shell.shadowRoot.querySelector('.tdw-capture-profile__start').click();
  const host = window.document.querySelector('[data-tdw-browser-tool-root="ux-inspector"]');
  const target = window.document.querySelector('button');
  target.getBoundingClientRect = () => ({ x: 10, y: 10, left: 10, top: 10, right: 100, bottom: 40,
    width: 90, height: 30, toJSON() {} });
  window.document.elementsFromPoint = () => [host, shell, target];
  const shield = host.shadowRoot.querySelector('.tdw-selection-shield');
  shield.dispatchEvent(new window.MouseEvent('pointermove', { bubbles: true, clientX: 20, clientY: 20 }));
  await new Promise((resolve) => window.setTimeout(resolve, 25));
  shield.dispatchEvent(new window.MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }));
  const nonce = new URLSearchParams(new URL(openedUrl).hash.slice(1)).get('nonce');
  const send = (type, extra = {}) => window.dispatchEvent(new window.MessageEvent('message', {
    origin: 'https://tdw.example.com', source: bridgeWindow, data: protocol.createMessage(type, nonce, extra)
  }));
  send('TDW_UX_INSPECTOR_READY');
  assert.equal(posted[0].formStatus, 'AVAILABLE');
  assert.equal(posted[0].storeStatus, 'UNAVAILABLE');
  send('TDW_UX_INSPECTOR_RECEIVED', { captureId: posted[0].captureId });
  assert.equal(posted[1].type, 'TDW_UX_INSPECTOR_FORM_CHUNK');
  assert.equal(JSON.parse(posted[1].chunk)[0].value, 'CRM customer');
  send('TDW_UX_INSPECTOR_FORM_ACK', { captureId: posted[0].captureId, index: 0 });
  await new Promise((resolve) => window.setTimeout(resolve, 5));
  assert.equal(window.document.querySelector('[data-tdw-browser-tool-root="ux-inspector"]'), null);
  dom.window.close();
});

test('form field read failure leaves the element capture available', async () => {
  const dom = createDom(
    '<!doctype html><html><body><input id="contact" value="CRM customer"><button>Save</button></body></html>',
    'https://crm.example.com/contacts/new'
  );
  const { window } = dom;
  const protocol = protocolFor(dom);
  const posted = [];
  let openedUrl = '';
  const bridgeWindow = { postMessage(message) { posted.push(message); } };
  window.open = (url) => { openedUrl = String(url); return bridgeWindow; };
  window.__TDW_BROWSER_TOOLS_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);
  const shell = window.document.querySelector('[data-tdw-browser-tool-root="browser-tools-shell"]');
  shell.shadowRoot.querySelector('.tdw-tools-launcher').click();
  shell.shadowRoot.querySelector('.tdw-tool-action[data-feature-id="ux-inspector"]').click();
  const formOption = shell.shadowRoot.querySelectorAll('.tdw-capture-profile input')[1];
  formOption.checked = true;
  formOption.dispatchEvent(new window.Event('change', { bubbles: true }));
  shell.shadowRoot.querySelector('.tdw-capture-profile__start').click();
  const host = window.document.querySelector('[data-tdw-browser-tool-root="ux-inspector"]');
  const target = window.document.querySelector('button');
  target.getBoundingClientRect = () => ({ x: 10, y: 10, left: 10, top: 10, right: 100, bottom: 40,
    width: 90, height: 30, toJSON() {} });
  window.document.elementsFromPoint = () => [host, shell, target];
  Object.defineProperty(window.document.querySelector('input'), 'value', {
    configurable: true, get() { throw new Error('field read failed'); }
  });
  const shield = host.shadowRoot.querySelector('.tdw-selection-shield');
  shield.dispatchEvent(new window.MouseEvent('pointermove', { bubbles: true, clientX: 20, clientY: 20 }));
  await new Promise((resolve) => window.setTimeout(resolve, 25));
  shield.dispatchEvent(new window.MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }));
  const nonce = new URLSearchParams(new URL(openedUrl).hash.slice(1)).get('nonce');
  window.dispatchEvent(new window.MessageEvent('message', {
    origin: 'https://tdw.example.com', source: bridgeWindow,
    data: protocol.createMessage('TDW_UX_INSPECTOR_READY', nonce)
  }));
  assert.equal(posted[0].type, 'TDW_UX_INSPECTOR_CAPTURE');
  assert.equal(posted[0].capture.target.tag, 'button');
  assert.equal(posted[0].formStatus, 'UNAVAILABLE');
  dom.window.close();
});

test('UI Explorer opens immediately and transfers only route and routed component after a strict handshake', () => {
  const dom = createDom(
    '<!doctype html><html><body><crm-shell><router-outlet></router-outlet><crm-contact-view><button>Save</button></crm-contact-view></crm-shell></body></html>',
    'https://crm.example.com/contacts/123456?token=redacted'
  );
  const { window } = dom;
  const protocol = protocolFor(dom);
  const posted = [];
  let openedUrl = '';
  const bridgeWindow = { postMessage(message, targetOrigin) { posted.push({ message, targetOrigin }); } };
  window.open = (url) => { openedUrl = String(url); return bridgeWindow; };
  window.__TDW_BROWSER_TOOLS_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);

  const shell = window.document.querySelector('[data-tdw-browser-tool-root="browser-tools-shell"]');
  shell.shadowRoot.querySelector('.tdw-tools-launcher').click();
  shell.shadowRoot.querySelector('.tdw-tool-action[data-feature-id="ui-explorer"]').click();

  assert.match(openedUrl, /^https:\/\/tdw\.example\.com\/ui-explorer#/);
  assert.doesNotMatch(openedUrl, /contacts|crm-contact-view|123456|redacted/);
  assert.equal(window.document.querySelector('[data-tdw-browser-tool-root="ux-inspector"]'), null);
  const nonce = new URLSearchParams(new URL(openedUrl).hash.slice(1)).get('nonce');
  const ready = protocol.createMessage('TDW_UI_EXPLORER_READY', nonce);
  window.dispatchEvent(new window.MessageEvent('message', {
    origin: 'https://wrong.example.com', source: bridgeWindow, data: ready
  }));
  window.dispatchEvent(new window.MessageEvent('message', {
    origin: 'https://tdw.example.com', source: {}, data: ready
  }));
  assert.equal(posted.length, 0);
  window.dispatchEvent(new window.MessageEvent('message', {
    origin: 'https://tdw.example.com', source: bridgeWindow, data: ready
  }));
  assert.equal(posted.length, 1);
  assert.equal(posted[0].message.type, 'TDW_UI_EXPLORER_CONTEXT');
  assert.equal(posted[0].targetOrigin, 'https://tdw.example.com');
  assert.equal(posted[0].message.context.page.path, '/contacts/:value');
  assert.deepEqual(Array.from(posted[0].message.context.page.componentBoundaryTags), [
    'crm-contact-view', 'crm-shell'
  ]);
  assert.equal('target' in posted[0].message.context, false);
  assert.equal('formSnapshot' in posted[0].message.context, false);
  window.dispatchEvent(new window.MessageEvent('message', {
    origin: 'https://tdw.example.com', source: bridgeWindow, data: ready
  }));
  assert.equal(posted.length, 1);
  window.dispatchEvent(new window.MessageEvent('message', {
    origin: 'https://tdw.example.com', source: bridgeWindow,
    data: protocol.createMessage('TDW_UI_EXPLORER_RECEIVED', nonce, {
      contextId: posted[0].message.contextId
    })
  }));
  assert.equal(shell.hidden, false);
  dom.window.close();
});

test('UI Explorer page context rejects ambiguous main components and malformed payloads', () => {
  const dom = createDom(
    '<!doctype html><html><body><router-outlet></router-outlet><crm-left></crm-left><router-outlet></router-outlet><crm-right></crm-right></body></html>',
    'https://crm.example.com/contacts/new'
  );
  const protocol = protocolFor(dom);
  const context = protocol.capturePageContext();
  assert.deepEqual(Array.from(context.page.componentBoundaryTags), []);
  dom.window.document.querySelectorAll('router-outlet')[1].setAttribute('name', 'modal');
  assert.deepEqual(Array.from(protocol.capturePageContext().page.componentBoundaryTags), ['crm-left']);
  assert.equal(protocol.normalizePageContext({ ...context, extra: true }).ok, false);
  assert.equal(protocol.normalizePageContext({
    ...context, page: { ...context.page, path: '/contacts/new?secret=value' }
  }).ok, false);
  dom.window.close();
});

test('legacy UX Inspector launcher config remains accepted', () => {
  const dom = createDom('<!doctype html><html><body></body></html>', 'https://crm.example.com/');
  const protocol = protocolFor(dom);
  assert.equal(protocol.normalizeLauncherConfig({ ...config(), featureId: 'ux-inspector' }).ok, true);
  assert.equal(protocol.normalizeLauncherConfig(config()).value.featureId, 'browser-tools');
  dom.window.close();
});

test('Browser Tools runtime contains no active API calls, storage, cookie or dynamic-code capability', () => {
  const combinedRuntime = protocolSource + '\\n' + runtimeSource;
  assert.doesNotMatch(combinedRuntime, /\bfetch\s*\(/);
  assert.doesNotMatch(combinedRuntime, /\bXMLHttpRequest\b/);
  assert.doesNotMatch(combinedRuntime, /\blocalStorage\b|\bsessionStorage\b/);
  assert.doesNotMatch(combinedRuntime, /\bdocument\.cookie\b/);
  assert.doesNotMatch(combinedRuntime, /navigator\.clipboard|execCommand\s*\(/);
  assert.doesNotMatch(combinedRuntime, /\beval\s*\(|\bnew\s+Function\b/);
  assert.doesNotMatch(combinedRuntime, /capture\.html|showFallback|tdw-fallback/);
  assert.doesNotMatch(loaderSource, /\bfetch\s*\(|\bXMLHttpRequest\b/);
  assert.doesNotMatch(loaderSource, /\blocalStorage\b|\bsessionStorage\b/);
  assert.doesNotMatch(loaderSource, /\bdocument\.cookie\b/);
  assert.doesNotMatch(loaderSource, /\beval\s*\(|\bnew\s+Function\b/);
});
