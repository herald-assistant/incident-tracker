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
    version: 3,
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
  assert.equal(capture.version, 3);
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

test('protocol rejects forged fields and unsupported versions while allowing large captures', () => {
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
    protocol.normalizeCapture({ ...captureFixture(), version: 2 }).ok,
    false
  );
  assert.equal(
    protocol.normalizeCapture({
      ...captureFixture(),
      ancestors: Array.from({ length: 800 }, (_, index) => ({
        depth: index + 1, tag: 'section', role: null, accessibleName: 'x'.repeat(180),
        text: null, stableAttributes: {}
      }))
    }).ok,
    true
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
        src="https://tdw.example.com/browser-tools/loader.js?v=1.5.0"
        data-tdw-feature-id="browser-tools"
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
  assert.equal(shell.getAttribute('data-tdw-browser-tool-version'), '1.5.0');
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
    '1.5.0'
  );
  dom.window.close();
});

function selectUxElement(window, profile = 'ELEMENT_CONTEXT') {
  window.__TDW_BROWSER_TOOLS_TEST_MODE__ = true;
  window.__TDW_BROWSER_TOOL_CONFIG__ = config();
  window.eval(runtimeSource);
  const shell = window.document.querySelector('[data-tdw-browser-tool-root="browser-tools-shell"]');
  shell.shadowRoot.querySelector('.tdw-tools-launcher').click();
  shell.shadowRoot.querySelector('.tdw-tool-action[data-feature-id="ux-inspector"]').click();
  if (profile === 'FORM_DIAGNOSTICS') {
    const radio = shell.shadowRoot.querySelectorAll('.tdw-capture-profile input')[1];
    radio.checked = true;
    radio.dispatchEvent(new window.Event('change', { bubbles: true }));
  }
  shell.shadowRoot.querySelector('.tdw-capture-profile__start').click();
  const host = window.document.querySelector('[data-tdw-browser-tool-root="ux-inspector"]');
  const target = window.document.querySelector('[data-testid="crm-save"]');
  target.getBoundingClientRect = () => ({
    x: 30, y: 70, left: 30, top: 70, right: 210, bottom: 112,
    width: 180, height: 42, toJSON() {}
  });
  window.document.elementsFromPoint = () => [host, shell, target];
  const shield = host.shadowRoot.querySelector('.tdw-selection-shield');
  shield.dispatchEvent(new window.MouseEvent('pointermove', { bubbles: true, clientX: 60, clientY: 80 }));
  return { host, shield };
}

test('UX Inspector posts one snapshot from examined page and navigates with server capture ID', async () => {
  const dom = createDom(
    '<!doctype html><html><body><input name="contactName" value="Ada"><button data-testid="crm-save">Save CRM contact</button></body></html>',
    'https://crm.example.com/contacts/new'
  );
  const { window } = dom;
  const calls = [];
  const popup = { location: { href: '' }, closed: false };
  window.open = () => popup;
  window.getStoreState = () => ({ contacts: { selected: 'CRM contact', token: 'secret' } });
  window.fetch = async (url, options) => {
    calls.push({ url: String(url), options });
    return { ok: true, json: async () => ({ captureId: '11111111-2222-4333-8444-555555555555' }) };
  };
  const { host, shield } = selectUxElement(window, 'FORM_DIAGNOSTICS');
  await new Promise((resolve) => window.setTimeout(resolve, 25));
  shield.dispatchEvent(new window.MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }));
  await new Promise((resolve) => window.setTimeout(resolve, 30));

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'https://tdw.example.com/api/ux-inspector/captures');
  assert.equal(calls[0].options.method, 'POST');
  assert.equal(calls[0].options.mode, 'cors');
  assert.equal(calls[0].options.credentials, 'omit');
  const payload = JSON.parse(calls[0].options.body);
  assert.equal(payload.capture.version, 3);
  assert.equal('captureId' in payload.capture, false);
  assert.equal(payload.capture.page.origin, 'https://crm.example.com');
  assert.equal(payload.formFields.status, 'AVAILABLE');
  assert.equal(payload.store.status, 'AVAILABLE');
  assert.equal(payload.store.state.contacts.token, '[redacted]');
  assert.match(popup.location.href, /\/ux-inspector\?captureId=11111111-2222-4333-8444-555555555555$/);
  assert.equal(host.shadowRoot.querySelector('.tdw-status a').hidden, false);
  dom.window.close();
});

test('UX Inspector preserves frozen capture for retry after a network error', async () => {
  const dom = createDom('<!doctype html><html><body><button data-testid="crm-save">Save</button></body></html>',
    'https://crm.example.com/contacts/new');
  const { window } = dom;
  window.open = () => null;
  let attempts = 0;
  const bodies = [];
  window.fetch = async (_url, options) => {
    attempts++;
    bodies.push(options.body);
    if (attempts === 1) throw new Error('synthetic CRM network failure');
    return { ok: true, json: async () => ({ captureId: '11111111-2222-4333-8444-555555555555' }) };
  };
  const { host, shield } = selectUxElement(window);
  await new Promise((resolve) => window.setTimeout(resolve, 25));
  shield.dispatchEvent(new window.MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }));
  await new Promise((resolve) => window.setTimeout(resolve, 30));
  const retry = host.shadowRoot.querySelector('.tdw-status button');
  assert.equal(retry.hidden, false);
  retry.click();
  await new Promise((resolve) => window.setTimeout(resolve, 30));
  assert.equal(attempts, 2);
  assert.equal(bodies[0], bodies[1]);
  assert.equal(host.shadowRoot.querySelector('.tdw-status a').hidden, false);
  dom.window.close();
});

test('UX Inspector keeps valid element capture when optional store read fails', async () => {
  const dom = createDom('<!doctype html><html><body><button data-testid="crm-save">Save</button></body></html>',
    'https://crm.example.com/contacts/new');
  const { window } = dom;
  window.open = () => null;
  window.getStoreState = () => { throw new Error('synthetic CRM store failure'); };
  let payload;
  window.fetch = async (_url, options) => {
    payload = JSON.parse(options.body);
    return { ok: true, json: async () => ({ captureId: '11111111-2222-4333-8444-555555555555' }) };
  };
  const { shield } = selectUxElement(window);
  await new Promise((resolve) => window.setTimeout(resolve, 25));
  shield.dispatchEvent(new window.MouseEvent('click', { bubbles: true, cancelable: true, button: 0 }));
  await new Promise((resolve) => window.setTimeout(resolve, 30));
  assert.equal(payload.store.status, 'UNAVAILABLE');
  assert.equal(payload.capture.target.tag, 'button');
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

test('legacy UX Inspector launcher config is rejected', () => {
  const dom = createDom('<!doctype html><html><body></body></html>', 'https://crm.example.com/');
  const protocol = protocolFor(dom);
  assert.equal(protocol.normalizeLauncherConfig({ ...config(), featureId: 'ux-inspector' }).ok, false);
  assert.equal(protocol.normalizeLauncherConfig(config()).value.featureId, 'browser-tools');
  dom.window.close();
});

test('Browser Tools runtime only posts UX capture and uses no storage, cookie or dynamic code', () => {
  const combinedRuntime = protocolSource + '\\n' + runtimeSource;
  assert.match(combinedRuntime, /\/api\/ux-inspector\/captures/);
  assert.doesNotMatch(combinedRuntime, /\/api\/ux-inspector\/(store-snapshots|form-fields-snapshots)/);
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
