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
    featureId: 'ux-inspector',
    tdwOrigin
  };
}

function captureFixture(overrides = {}) {
  return {
    schema: 'tdw.ux-inspector-capture',
    version: 1,
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
    formSnapshot: null,
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
  assert.equal(capture.version, 1);
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
  assert.equal(capture.formSnapshot, null);
  assert.ok(capture.signals.redactions.includes('FORM_VALUES_NOT_REQUESTED'));
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_THIS_VALUE/);
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_TEXTAREA_DEFAULT/);
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_OPTION/);
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_EDITABLE/);
  assert.doesNotMatch(serialized, /raw-secret/);
  assert.doesNotMatch(serialized, /contact@example\.invalid/);
  dom.window.close();
});

test('form diagnostics freezes allowed nearest-form values, including hidden controls, and excludes sensitive controls', () => {
  const dom = createDom(
    `<!doctype html><html><body>
      <crm-login>
        <form id="login-form">
          <label for="email">E-mail</label>
          <input id="email" name="email" formcontrolname="email" value="customer@example.test" required>
          <label for="reference">Reference</label>
          <input id="reference" name="reference" value="" required>
          <input name="password" type="password" value="DO_NOT_CAPTURE_PASSWORD">
          <input name="csrfToken" type="text" value="DO_NOT_CAPTURE_TOKEN">
          <input name="internalReference" type="hidden" value="crm-contact-42">
          <input name="attachment" type="file">
          <button type="submit" disabled>Sign in</button>
        </form>
      </crm-login>
    </body></html>`,
    'https://crm.example.com/login'
  );
  const protocol = protocolFor(dom);
  const input = dom.window.document.getElementById('email');
  input.getBoundingClientRect = () => ({
    x: 10, y: 20, left: 10, top: 20, right: 210, bottom: 60,
    width: 200, height: 40, toJSON() {}
  });

  const capture = protocol.captureElement(input, {
    clientVersion: '1.0.0',
    featureId: 'ux-inspector',
    captureProfile: 'FORM_DIAGNOSTICS',
    capturedAt: '2026-09-15T10:00:00.000Z'
  });
  const serialized = JSON.stringify(capture);

  assert.equal(capture.captureProfile, 'FORM_DIAGNOSTICS');
  assert.equal(capture.formSnapshot.source, 'NEAREST_FORM');
  assert.equal(capture.formSnapshot.valid, false);
  assert.equal(capture.formSnapshot.controls.length, 3);
  assert.equal(capture.formSnapshot.controls[0].selectedTarget, true);
  assert.equal(capture.formSnapshot.controls[0].value, 'customer@example.test');
  assert.equal(capture.formSnapshot.controls[1].validity.valueMissing, true);
  assert.equal(capture.formSnapshot.controls[2].type, 'hidden');
  assert.equal(capture.formSnapshot.controls[2].name, 'internalReference');
  assert.equal(capture.formSnapshot.controls[2].value, 'crm-contact-42');
  assert.equal(capture.formSnapshot.submitters[0].disabled, true);
  assert.deepEqual(
    Array.from(capture.formSnapshot.excludedControls, (control) => control.reason).sort(),
    ['FILE_CONTROL', 'SENSITIVE_NAME', 'SENSITIVE_TYPE']
  );
  assert.deepEqual(
    Array.from(capture.target.domFingerprint.componentBoundaryTags),
    ['crm-login']
  );
  assert.match(capture.target.domFingerprint.selectorCandidates[0], /#email|input\[formcontrolname/);
  assert.match(serialized, /crm-contact-42/);
  assert.doesNotMatch(serialized, /DO_NOT_CAPTURE_/);
  assert.ok(protocol.serializedSize(capture) <= protocol.MAX_CAPTURE_BYTES);
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
    featureId: 'ux-inspector'
  });

  assert.equal(capture.target.role, 'button');
  assert.equal(capture.target.state.disabled, true);
  assert.equal(capture.target.state.expanded, false);
  assert.equal('classes' in capture.target, false);
  assert.ok(capture.ancestors.length <= 24);
  assert.ok(capture.traversal.omittedNodeCount > 0);
  assert.equal(capture.ancestors.at(-1).tag, 'html');
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
        src="https://tdw.example.com/browser-tools/loader.js?v=1.0.0"
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
  assert.deepEqual(runtimeConfig, config());
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
  assert.equal(shell.getAttribute('data-tdw-browser-tool-version'), '1.0.0');
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
  const profiles = menu.querySelectorAll('.tdw-capture-profile input');
  assert.equal(profiles.length, 2);
  assert.equal(profiles[0].value, 'ELEMENT_CONTEXT');
  assert.equal(profiles[0].checked, true);
  profiles[1].checked = true;
  profiles[1].dispatchEvent(new window.Event('change', { bubbles: true }));

  action.click();
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
  assert.match(inspector.shadowRoot.querySelector('.tdw-status').textContent, /dołączę najbliższy formularz/);
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
  shell.shadowRoot.querySelector('.tdw-tool-action').click();

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
  shell.shadowRoot.querySelector('.tdw-tool-action').click();
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
  firstShell.shadowRoot.querySelector('.tdw-tools-launcher').click();
  firstShell.shadowRoot.querySelector('.tdw-tool-action').click();
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
