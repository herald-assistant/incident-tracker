(function bootstrapTdwBrowserTools(global) {
  'use strict';

  const CONFIG_KEY = '__TDW_BROWSER_TOOL_CONFIG__';
  const PROTOCOL_KEY = '__TDW_BROWSER_TOOLS_PROTOCOL_V1__';
  const SINGLETON_KEY = '__TDW_BROWSER_TOOL_ACTIVE_INSTANCE__';
  const RUNTIME_VERSION = '1.0.0';
  const BRIDGE_TIMEOUT_MS = 6000;
  const testMode = global.__TDW_INSPECTOR_TEST_MODE__ === true;
  const protocol = global[PROTOCOL_KEY];
  const rawConfig = global[CONFIG_KEY];

  try {
    delete global[CONFIG_KEY];
    delete global[PROTOCOL_KEY];
  } catch {
    // A hostile page can make globals non-configurable. Validation below still applies.
  }

  if (!protocol) {
    console.error('[TDW Inspector Lite] Protocol is unavailable.');
    return;
  }
  const configResult = protocol.normalizeLauncherConfig(rawConfig);
  if (!configResult.ok) {
    console.error('[TDW Inspector Lite] ' + configResult.error);
    return;
  }
  if (!['http:', 'https:'].includes(global.location.protocol)) {
    console.error('[TDW Inspector Lite] Only HTTP(S) pages are supported.');
    return;
  }

  const existing = global[SINGLETON_KEY];
  if (existing && typeof existing.dispose === 'function') {
    existing.dispose('replaced');
  }

  const featureFactories = new Map([
    ['ui-explorer-inspector', (config) => new UiExplorerInspector(config)]
  ]);
  const factory = featureFactories.get(configResult.value.featureId);
  if (!factory) {
    console.error('[TDW Browser Tools] Unknown feature: ' + configResult.value.featureId);
    return;
  }

  function UiExplorerInspector(config) {
    this.config = config;
    this.state = 'bootstrapping';
    this.disposed = false;
    this.target = null;
    this.pointerKnown = false;
    this.pointerX = 0;
    this.pointerY = 0;
    this.animationFrame = null;
    this.resizeObserver = null;
    this.bridgeWindow = null;
    this.bridgeNonce = '';
    this.bridgeTimer = null;
    this.capture = null;

    this.host = global.document.createElement('div');
    this.host.setAttribute('data-tdw-browser-tool-root', 'ui-explorer-inspector');
    this.host.setAttribute('data-tdw-browser-tool-version', RUNTIME_VERSION);
    this.shadowRoot = this.host.attachShadow({ mode: testMode ? 'open' : 'closed' });
    this.style = global.document.createElement('style');
    this.style.textContent = INSPECTOR_STYLES;
    this.shield = element('div', 'tdw-selection-shield');
    this.shield.setAttribute('aria-hidden', 'true');
    this.highlight = element('div', 'tdw-highlight');
    this.highlight.dataset.visible = 'false';
    this.highlight.dataset.labelPosition = 'above';
    this.highlightLabel = element('span', 'tdw-highlight-label');
    this.highlightScan = element('span', 'tdw-highlight-scan');
    this.highlight.append(this.highlightScan, this.highlightLabel);
    this.status = element('div', 'tdw-status');
    this.status.setAttribute('role', 'status');
    this.status.setAttribute('aria-live', 'polite');
    this.statusMark = element('span', 'tdw-status-mark');
    this.statusText = element(
      'span',
      'tdw-status-text',
      'Wskaż element i kliknij · Escape anuluje'
    );
    this.status.append(
      this.statusMark,
      element('strong', '', 'TDW Inspector Lite'),
      this.statusText
    );
    this.shadowRoot.append(this.style, this.shield, this.highlight, this.status);

    this.onKeyDown = this.onKeyDown.bind(this);
    this.onPointerMove = this.onPointerMove.bind(this);
    this.blockShieldEvent = this.blockShieldEvent.bind(this);
    this.onShieldClick = this.onShieldClick.bind(this);
    this.onViewportChange = this.onViewportChange.bind(this);
    this.onWindowBlur = this.onWindowBlur.bind(this);
    this.onVisibilityChange = this.onVisibilityChange.bind(this);
    this.onBridgeMessage = this.onBridgeMessage.bind(this);
  }

  UiExplorerInspector.prototype.start = function start() {
    const root = global.document.documentElement;
    if (!root) {
      console.error('[TDW Inspector Lite] Document root is unavailable.');
      this.dispose('missing-document-root');
      return;
    }
    root.append(this.host);
    global.addEventListener('keydown', this.onKeyDown, true);
    global.addEventListener('scroll', this.onViewportChange, true);
    global.addEventListener('resize', this.onViewportChange, true);
    global.addEventListener('blur', this.onWindowBlur, true);
    global.document.addEventListener('visibilitychange', this.onVisibilityChange, true);
    this.shield.addEventListener('pointermove', this.onPointerMove, true);
    this.shield.addEventListener('pointerdown', this.blockShieldEvent, true);
    this.shield.addEventListener('mousedown', this.blockShieldEvent, true);
    this.shield.addEventListener('mouseup', this.blockShieldEvent, true);
    this.shield.addEventListener('contextmenu', this.blockShieldEvent, true);
    this.shield.addEventListener('click', this.onShieldClick, true);
    if (typeof global.ResizeObserver === 'function') {
      this.resizeObserver = new global.ResizeObserver(() =>
        this.updateHighlightGeometry()
      );
    }
    this.state = 'selecting';
    this.shield.dataset.active = 'true';
  };

  UiExplorerInspector.prototype.dispose = function dispose() {
    if (this.disposed) {
      return;
    }
    this.disposed = true;
    this.state = 'disposed';
    this.cancelAnimationFrame();
    this.clearBridgeTimer();
    this.resizeObserver?.disconnect();
    this.resizeObserver = null;
    global.removeEventListener('keydown', this.onKeyDown, true);
    global.removeEventListener('scroll', this.onViewportChange, true);
    global.removeEventListener('resize', this.onViewportChange, true);
    global.removeEventListener('blur', this.onWindowBlur, true);
    global.removeEventListener('message', this.onBridgeMessage, true);
    global.document.removeEventListener(
      'visibilitychange',
      this.onVisibilityChange,
      true
    );
    this.shield.removeEventListener('pointermove', this.onPointerMove, true);
    this.shield.removeEventListener('pointerdown', this.blockShieldEvent, true);
    this.shield.removeEventListener('mousedown', this.blockShieldEvent, true);
    this.shield.removeEventListener('mouseup', this.blockShieldEvent, true);
    this.shield.removeEventListener('contextmenu', this.blockShieldEvent, true);
    this.shield.removeEventListener('click', this.onShieldClick, true);
    this.host.remove();
    if (global[SINGLETON_KEY] === this) {
      try {
        delete global[SINGLETON_KEY];
      } catch {
        // The page can interfere with globals; the detached instance is still inert.
      }
    }
  };

  UiExplorerInspector.prototype.onKeyDown = function onKeyDown(event) {
    if (event.key !== 'Escape') {
      return;
    }
    consumeEvent(event);
    this.dispose('escape');
  };

  UiExplorerInspector.prototype.onPointerMove = function onPointerMove(event) {
    if (this.state !== 'selecting') {
      return;
    }
    this.pointerX = event.clientX;
    this.pointerY = event.clientY;
    this.pointerKnown = true;
    this.scheduleTargetUpdate();
  };

  UiExplorerInspector.prototype.blockShieldEvent = function blockShieldEvent(event) {
    if (this.state !== 'selecting') {
      return;
    }
    consumeEvent(event);
  };

  UiExplorerInspector.prototype.onShieldClick = function onShieldClick(event) {
    this.blockShieldEvent(event);
    if (this.state !== 'selecting' || event.button !== 0 || !this.target) {
      return;
    }
    try {
      this.capture = protocol.captureElement(this.target, {
        clientVersion: RUNTIME_VERSION,
        featureId: this.config.featureId
      });
      this.hideSelection();
      this.beginTransfer();
    } catch (error) {
      console.error('[TDW Inspector Lite] Capture failed.', error);
      this.hideSelection();
      this.showFallback(
        'Nie udało się bezpiecznie przygotować capture. Zamknij panel i spróbuj ponownie.',
        null
      );
    }
  };

  UiExplorerInspector.prototype.onViewportChange = function onViewportChange() {
    if (this.state === 'selecting' && this.pointerKnown) {
      this.scheduleTargetUpdate();
    }
  };

  UiExplorerInspector.prototype.onWindowBlur = function onWindowBlur() {
    if (this.state === 'selecting') {
      this.dispose('window-blur');
    }
  };

  UiExplorerInspector.prototype.onVisibilityChange = function onVisibilityChange() {
    if (
      this.state === 'selecting' &&
      global.document.visibilityState !== 'visible'
    ) {
      this.dispose('document-hidden');
    }
  };

  UiExplorerInspector.prototype.scheduleTargetUpdate = function scheduleTargetUpdate() {
    if (this.animationFrame !== null) {
      return;
    }
    this.animationFrame = global.requestAnimationFrame(() => {
      this.animationFrame = null;
      this.updateTargetAtPointer();
    });
  };

  UiExplorerInspector.prototype.updateTargetAtPointer = function updateTargetAtPointer() {
    if (this.state !== 'selecting' || !this.pointerKnown) {
      return;
    }
    const stack =
      typeof global.document.elementsFromPoint === 'function'
        ? global.document.elementsFromPoint(this.pointerX, this.pointerY)
        : [global.document.elementFromPoint(this.pointerX, this.pointerY)].filter(Boolean);
    const target = stack.find(
      (candidate) =>
        candidate !== this.host &&
        candidate.getRootNode() !== this.shadowRoot &&
        !candidate.closest?.('[data-tdw-browser-tool-root]')
    );
    if (!target || !target.isConnected) {
      this.target = null;
      this.resizeObserver?.disconnect();
      this.hideHighlight();
      return;
    }
    if (this.target !== target) {
      this.resizeObserver?.disconnect();
      this.target = target;
      this.resizeObserver?.observe(target);
    }
    this.updateHighlightGeometry();
  };

  UiExplorerInspector.prototype.updateHighlightGeometry =
    function updateHighlightGeometry() {
      if (this.state !== 'selecting' || !this.target?.isConnected) {
        this.hideHighlight();
        return;
      }
      const rect = this.target.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) {
        this.hideHighlight();
        return;
      }
      const descriptor = protocol.describeElement(this.target);
      this.highlight.style.transform = `translate3d(${rect.left}px, ${rect.top}px, 0)`;
      this.highlight.style.width = `${rect.width}px`;
      this.highlight.style.height = `${rect.height}px`;
      this.highlight.dataset.labelPosition = rect.top < 64 ? 'below' : 'above';
      this.highlightLabel.textContent = [
        descriptor.tag,
        descriptor.role ? `role=${descriptor.role}` : null,
        descriptor.accessibleName
      ]
        .filter(Boolean)
        .join(' · ');
      this.highlight.dataset.visible = 'true';
    };

  UiExplorerInspector.prototype.hideHighlight = function hideHighlight() {
    this.highlight.dataset.visible = 'false';
  };

  UiExplorerInspector.prototype.hideSelection = function hideSelection() {
    this.cancelAnimationFrame();
    this.resizeObserver?.disconnect();
    this.shield.dataset.active = 'false';
    this.hideHighlight();
    this.target = null;
  };

  UiExplorerInspector.prototype.cancelAnimationFrame = function cancelAnimationFrame() {
    if (this.animationFrame !== null) {
      global.cancelAnimationFrame(this.animationFrame);
      this.animationFrame = null;
    }
  };

  UiExplorerInspector.prototype.beginTransfer = function beginTransfer() {
    this.state = 'transferring';
    this.setStatus('Otwieram bezpieczny ekran TDW…', 'working');
    this.bridgeNonce = protocol.createNonce();
    const captureUrl = new URL('/tdw-inspector/capture.html', this.config.tdwOrigin);
    captureUrl.hash = new URLSearchParams({
      nonce: this.bridgeNonce,
      sourceOrigin: global.location.origin
    }).toString();
    global.addEventListener('message', this.onBridgeMessage, true);
    this.bridgeWindow = global.open(
      captureUrl.toString(),
      `tdw-inspector-${this.bridgeNonce}`,
      'popup=yes,width=820,height=920,resizable=yes,scrollbars=yes'
    );
    if (!this.bridgeWindow) {
      this.showFallback(
        'Przeglądarka zablokowała okno TDW. Skopiuj capture i otwórz ekran ręcznie.',
        this.capture
      );
      return;
    }
    this.resetBridgeTimer(
      'Nie udało się potwierdzić połączenia z TDW. Popup mógł zostać odcięty przez politykę strony.'
    );
  };

  UiExplorerInspector.prototype.onBridgeMessage = function onBridgeMessage(event) {
    if (
      event.origin !== this.config.tdwOrigin ||
      event.source !== this.bridgeWindow ||
      !this.bridgeNonce
    ) {
      return;
    }
    if (
      this.state === 'transferring' &&
      protocol.isReadyMessage(event.data, this.bridgeNonce)
    ) {
      try {
        this.bridgeWindow.postMessage(
          protocol.createMessage('TDW_INSPECTOR_CAPTURE', this.bridgeNonce, {
            capture: this.capture
          }),
          this.config.tdwOrigin
        );
        this.state = 'awaiting-receipt';
        this.setStatus('Capture wysłany · czekam na potwierdzenie TDW…', 'working');
        this.resetBridgeTimer(
          'TDW nie potwierdziło odbioru. Capture możesz przekazać ręcznie.'
        );
      } catch (error) {
        console.error('[TDW Inspector Lite] postMessage failed.', error);
        this.showFallback(
          'Nie udało się przekazać capture do TDW. Użyj bezpiecznego transferu ręcznego.',
          this.capture
        );
      }
      return;
    }
    if (
      this.state === 'awaiting-receipt' &&
      protocol.isReceivedMessage(event.data, this.bridgeNonce) &&
      event.data.captureId === this.capture?.captureId
    ) {
      this.clearBridgeTimer();
      this.state = 'completed';
      this.setStatus('Gotowe · pytanie dokończysz w TDW', 'success');
      global.setTimeout(() => this.dispose('transferred'), testMode ? 0 : 1100);
    }
  };

  UiExplorerInspector.prototype.resetBridgeTimer = function resetBridgeTimer(message) {
    this.clearBridgeTimer();
    this.bridgeTimer = global.setTimeout(
      () => this.showFallback(message, this.capture),
      testMode ? 80 : BRIDGE_TIMEOUT_MS
    );
  };

  UiExplorerInspector.prototype.clearBridgeTimer = function clearBridgeTimer() {
    if (this.bridgeTimer !== null) {
      global.clearTimeout(this.bridgeTimer);
      this.bridgeTimer = null;
    }
  };

  UiExplorerInspector.prototype.setStatus = function setStatus(message, kind) {
    this.status.hidden = false;
    this.status.dataset.kind = kind || 'neutral';
    this.statusText.textContent = message;
  };

  UiExplorerInspector.prototype.showFallback = function showFallback(message, capture) {
    this.clearBridgeTimer();
    global.removeEventListener('message', this.onBridgeMessage, true);
    this.state = 'fallback';
    this.status.hidden = true;
    const previous = this.shadowRoot.querySelector('.tdw-fallback-layer');
    previous?.remove();

    const layer = element('div', 'tdw-fallback-layer');
    const panel = element('section', 'tdw-fallback');
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-modal', 'true');
    panel.setAttribute('aria-labelledby', 'tdw-inspector-fallback-title');
    const eyebrow = element('span', 'tdw-eyebrow', 'TDW Inspector Lite');
    const title = element('h2', '', 'Potrzebny transfer ręczny');
    title.id = 'tdw-inspector-fallback-title';
    const explanation = element('p', 'tdw-fallback-description', message);
    const safety = element(
      'p',
      'tdw-fallback-safety',
      'Capture jest zredagowany, ale nadal sprawdź jego treść przed wklejeniem.'
    );
    panel.append(eyebrow, title, explanation, safety);

    let textarea = null;
    if (capture) {
      textarea = global.document.createElement('textarea');
      textarea.className = 'tdw-fallback-json';
      textarea.readOnly = true;
      textarea.setAttribute('aria-label', 'Zredagowany JSON capture');
      textarea.value = JSON.stringify(capture, null, 2);
      panel.append(textarea);
    }

    const feedback = element('p', 'tdw-fallback-feedback');
    feedback.setAttribute('role', 'status');
    feedback.setAttribute('aria-live', 'polite');
    const actions = element('div', 'tdw-fallback-actions');
    if (textarea) {
      const copyButton = button('tdw-button tdw-button-primary', 'Kopiuj capture');
      copyButton.addEventListener('click', async () => {
        const copied = await copyText(textarea.value, textarea);
        feedback.textContent = copied
          ? 'Capture skopiowany. Wklej go na ekranie TDW.'
          : 'Automatyczne kopiowanie nie zadziałało. Zaznacz tekst ręcznie.';
      });
      actions.append(copyButton);
    }
    const openLink = global.document.createElement('a');
    openLink.className = 'tdw-button tdw-button-secondary';
    openLink.href = new URL('/tdw-inspector/capture.html', this.config.tdwOrigin).toString();
    openLink.target = '_blank';
    openLink.rel = 'noopener';
    openLink.textContent = 'Otwórz ekran TDW';
    const closeButton = button('tdw-button tdw-button-quiet', 'Zamknij');
    closeButton.addEventListener('click', () => this.dispose('fallback-closed'));
    actions.append(openLink, closeButton);
    panel.append(feedback, actions);
    panel.addEventListener('keydown', (event) =>
      trapFocus(event, panel, this.shadowRoot)
    );
    layer.append(panel);
    this.shadowRoot.append(layer);
    global.setTimeout(
      () => (textarea || closeButton).focus({ preventScroll: true }),
      0
    );
  };

  async function copyText(value, textarea) {
    try {
      await global.navigator.clipboard.writeText(value);
      return true;
    } catch {
      try {
        textarea.focus();
        textarea.select();
        return global.document.execCommand('copy');
      } catch {
        return false;
      }
    }
  }

  function consumeEvent(event) {
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
  }

  function trapFocus(event, panel, shadowRoot) {
    if (event.key !== 'Tab') {
      return;
    }
    const focusable = Array.from(
      panel.querySelectorAll(
        'a[href], button:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )
    );
    if (!focusable.length) {
      return;
    }
    const first = focusable[0];
    const last = focusable.at(-1);
    if (event.shiftKey && shadowRoot.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && shadowRoot.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  function element(tag, className, text) {
    const node = global.document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (text) {
      node.textContent = text;
    }
    return node;
  }

  function button(className, text) {
    const node = element('button', className, text);
    node.type = 'button';
    return node;
  }

  const INSPECTOR_STYLES = `
    :host {
      all: initial;
      position: fixed;
      inset: 0;
      z-index: 2147483647;
      pointer-events: none;
      color: #172b4d;
      font-family: Inter, ui-sans-serif, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      font-size: 14px;
      line-height: 1.45;
      color-scheme: light;
    }

    *, *::before, *::after { box-sizing: border-box; }

    .tdw-selection-shield {
      position: fixed;
      inset: 0;
      display: none;
      pointer-events: auto;
      cursor: crosshair;
      background: transparent;
      touch-action: none;
    }

    .tdw-selection-shield[data-active="true"] { display: block; }

    .tdw-status {
      position: fixed;
      top: 18px;
      left: 50%;
      display: flex;
      align-items: center;
      gap: 8px;
      max-width: min(680px, calc(100vw - 28px));
      padding: 9px 14px;
      transform: translateX(-50%);
      color: white;
      border: 1px solid rgba(179, 212, 255, 0.58);
      border-radius: 999px;
      background:
        radial-gradient(circle at 80% 0, rgba(105, 167, 255, 0.5), transparent 38%),
        linear-gradient(120deg, rgba(7, 29, 73, 0.97), rgba(7, 71, 166, 0.96));
      box-shadow: 0 14px 42px rgba(7, 29, 73, 0.38), 0 0 0 4px rgba(12, 102, 228, 0.12);
      backdrop-filter: blur(10px);
      white-space: nowrap;
    }

    .tdw-status[hidden] { display: none; }
    .tdw-status[data-kind="success"] { background: linear-gradient(120deg, #164b35, #1f845a); }

    .tdw-status-mark {
      width: 9px;
      height: 9px;
      flex: 0 0 auto;
      border: 2px solid #deebff;
      border-radius: 50%;
      background: #69a7ff;
      box-shadow: 0 0 0 5px rgba(105, 167, 255, 0.2), 0 0 18px #69a7ff;
      animation: tdw-status-pulse 1.35s ease-in-out infinite;
    }

    .tdw-status-text {
      overflow: hidden;
      color: #deebff;
      text-overflow: ellipsis;
    }

    .tdw-highlight {
      position: fixed;
      top: 0;
      left: 0;
      display: none;
      overflow: visible;
      pointer-events: none;
      border: 2px solid #0c66e4;
      border-radius: 8px;
      background: rgba(222, 235, 255, 0.14);
      box-shadow:
        0 0 0 2px rgba(7, 71, 166, 0.92),
        0 0 0 8px rgba(12, 102, 228, 0.16),
        0 0 32px 12px rgba(12, 102, 228, 0.38),
        inset 0 0 24px rgba(222, 235, 255, 0.26);
      animation: tdw-highlight-pulse 1.4s ease-in-out infinite;
      will-change: transform, width, height;
    }

    .tdw-highlight[data-visible="true"] { display: block; }

    .tdw-highlight::before,
    .tdw-highlight::after {
      content: "";
      position: absolute;
      inset: -9px;
      border: 2px solid transparent;
      border-top-color: #69a7ff;
      border-bottom-color: #69a7ff;
      border-radius: 12px;
    }

    .tdw-highlight::after {
      inset: -6px -11px;
      border-top-color: transparent;
      border-bottom-color: transparent;
      border-left-color: #0747a6;
      border-right-color: #0747a6;
    }

    .tdw-highlight-scan {
      position: absolute;
      inset: 0;
      overflow: hidden;
      border-radius: 6px;
    }

    .tdw-highlight-scan::after {
      content: "";
      position: absolute;
      right: 0;
      left: 0;
      height: 2px;
      background: linear-gradient(90deg, transparent, #b3d4ff 22%, white 50%, #b3d4ff 78%, transparent);
      box-shadow: 0 0 14px 3px rgba(179, 212, 255, 0.8);
      animation: tdw-scan 1.8s ease-in-out infinite;
    }

    .tdw-highlight-label {
      position: absolute;
      left: -2px;
      bottom: calc(100% + 11px);
      max-width: min(390px, 72vw);
      padding: 7px 11px;
      overflow: hidden;
      color: white;
      font-size: 12px;
      font-weight: 760;
      line-height: 1.2;
      text-overflow: ellipsis;
      white-space: nowrap;
      border: 1px solid rgba(255, 255, 255, 0.28);
      border-radius: 999px;
      background: linear-gradient(120deg, #071d49, #0747a6 55%, #0c66e4);
      box-shadow: 0 8px 24px rgba(7, 71, 166, 0.34);
    }

    .tdw-highlight[data-label-position="below"] .tdw-highlight-label {
      top: calc(100% + 11px);
      bottom: auto;
    }

    .tdw-fallback-layer {
      position: fixed;
      inset: 0;
      display: grid;
      place-items: center;
      padding: 20px;
      pointer-events: auto;
      background: rgba(7, 20, 45, 0.62);
      backdrop-filter: blur(8px) saturate(0.9);
      animation: tdw-fade-in 150ms ease-out;
    }

    .tdw-fallback {
      width: min(720px, calc(100vw - 28px));
      max-height: min(840px, calc(100vh - 28px));
      padding: 24px;
      overflow: auto;
      border: 1px solid #b3d4ff;
      border-radius: 18px;
      background: white;
      box-shadow: 0 28px 90px rgba(9, 30, 66, 0.46), 0 0 0 5px rgba(12, 102, 228, 0.12);
    }

    .tdw-eyebrow {
      color: #0c66e4;
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.1em;
      text-transform: uppercase;
    }

    .tdw-fallback h2 { margin: 5px 0 0; color: #071d49; font-size: 22px; }
    .tdw-fallback-description { margin: 10px 0 0; color: #44546f; }

    .tdw-fallback-safety {
      margin: 14px 0 0;
      padding: 10px 12px;
      color: #0747a6;
      border: 1px solid #b3d4ff;
      border-radius: 10px;
      background: #deebff;
      font-weight: 650;
    }

    .tdw-fallback-json {
      width: 100%;
      min-height: 210px;
      margin-top: 14px;
      padding: 12px;
      resize: vertical;
      color: #172b4d;
      font: 11px/1.5 ui-monospace, SFMono-Regular, Consolas, monospace;
      border: 1px solid #b6c2cf;
      border-radius: 10px;
      background: #f7f8f9;
      outline: none;
    }

    .tdw-fallback-json:focus {
      border-color: #0c66e4;
      box-shadow: 0 0 0 3px rgba(12, 102, 228, 0.18);
    }

    .tdw-fallback-feedback { min-height: 20px; margin: 9px 0 0; color: #44546f; }

    .tdw-fallback-actions {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: 9px;
      margin-top: 14px;
    }

    .tdw-button {
      display: inline-flex;
      min-height: 42px;
      align-items: center;
      justify-content: center;
      padding: 0 16px;
      color: #0747a6;
      font: inherit;
      font-weight: 760;
      text-decoration: none;
      border: 1px solid #b3d4ff;
      border-radius: 10px;
      background: white;
      cursor: pointer;
    }

    .tdw-button-primary {
      color: white;
      border-color: #0c66e4;
      background: linear-gradient(120deg, #0747a6, #0c66e4);
      box-shadow: 0 8px 22px rgba(12, 102, 228, 0.24);
    }

    .tdw-button-quiet { color: #44546f; border-color: transparent; }

    @keyframes tdw-highlight-pulse {
      0%, 100% { filter: brightness(1); }
      50% { filter: brightness(1.18); }
    }

    @keyframes tdw-status-pulse {
      0%, 100% { transform: scale(0.9); opacity: 0.8; }
      50% { transform: scale(1.08); opacity: 1; }
    }

    @keyframes tdw-scan {
      0% { top: 0; opacity: 0; }
      16%, 84% { opacity: 0.95; }
      100% { top: calc(100% - 2px); opacity: 0; }
    }

    @keyframes tdw-fade-in { from { opacity: 0; } }

    @media (max-width: 620px) {
      .tdw-status strong { display: none; }
      .tdw-fallback-layer { padding: 8px; }
      .tdw-fallback { padding: 18px; }
      .tdw-fallback-actions { align-items: stretch; flex-direction: column; }
    }

    @media (prefers-reduced-motion: reduce) {
      .tdw-highlight,
      .tdw-highlight-scan::after,
      .tdw-status-mark,
      .tdw-fallback-layer { animation: none; }
    }
  `;

  const instance = factory(configResult.value);
  try {
    delete global[SINGLETON_KEY];
    Object.defineProperty(global, SINGLETON_KEY, {
      configurable: true,
      enumerable: false,
      writable: false,
      value: instance
    });
  } catch (error) {
    console.error('[TDW Inspector Lite] Could not establish a singleton.', error);
    return;
  }
  instance.start();
})(globalThis);
