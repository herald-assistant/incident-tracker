(function bootstrapTdwBrowserTools(global) {
  'use strict';

  const CONFIG_KEY = '__TDW_BROWSER_TOOL_CONFIG__';
  const PROTOCOL_KEY = '__TDW_BROWSER_TOOLS_PROTOCOL_V1__';
  const SINGLETON_KEY = '__TDW_BROWSER_TOOL_ACTIVE_INSTANCE__';
  const RUNTIME_VERSION = '1.0.0';
  const BRIDGE_TIMEOUT_MS = 15000;
  const testMode = global.__TDW_BROWSER_TOOLS_TEST_MODE__ === true;
  const protocol = global[PROTOCOL_KEY];
  const rawConfig = global[CONFIG_KEY];

  try {
    delete global[CONFIG_KEY];
    delete global[PROTOCOL_KEY];
  } catch {
    // A hostile page can make globals non-configurable. Validation below still applies.
  }

  if (!protocol) {
    console.error('[TDW Browser Tools] Protocol is unavailable.');
    return;
  }
  const configResult = protocol.normalizeLauncherConfig(rawConfig);
  if (!configResult.ok) {
    console.error('[TDW Browser Tools] ' + configResult.error);
    return;
  }
  if (!['http:', 'https:'].includes(global.location.protocol)) {
    console.error('[TDW Browser Tools] Only HTTP(S) pages are supported.');
    return;
  }

  const existing = global[SINGLETON_KEY];
  if (existing && typeof existing.reveal === 'function') {
    existing.reveal(configResult.value);
    return;
  }
  if (existing && typeof existing.dispose === 'function') {
    existing.dispose('incompatible-runtime-replaced');
  }

  const featureFactories = new Map([
    [
      'ux-inspector',
      (config, onExit, options) => new UxInspector(config, onExit, options)
    ]
  ]);
  if (!featureFactories.has(configResult.value.featureId)) {
    console.error('[TDW Browser Tools] Unknown feature: ' + configResult.value.featureId);
    return;
  }

  function TdwBrowserTools(config) {
    this.config = config;
    this.state = 'bootstrapping';
    this.disposed = false;
    this.activeFeature = null;
    this.menuFocusTimer = null;
    this.captureProfile = 'ELEMENT_CONTEXT';

    this.host = global.document.createElement('div');
    this.host.setAttribute('data-tdw-browser-tool-root', 'browser-tools-shell');
    this.host.setAttribute('data-tdw-browser-tool-version', RUNTIME_VERSION);
    this.shadowRoot = this.host.attachShadow({ mode: testMode ? 'open' : 'closed' });
    this.style = global.document.createElement('style');
    this.style.textContent = SHELL_STYLES;
    this.dock = element('div', 'tdw-tools-dock');

    this.menu = element('section', 'tdw-tools-menu');
    this.menu.id = 'tdw-browser-tools-menu';
    this.menu.hidden = true;
    this.menu.setAttribute('role', 'dialog');
    this.menu.setAttribute('aria-modal', 'false');
    this.menu.setAttribute('aria-labelledby', 'tdw-browser-tools-title');

    const menuHeader = element('header', 'tdw-tools-menu__header');
    const menuBrand = element('div', 'tdw-tools-menu__brand');
    menuBrand.append(createBrandLogo(config, 'tdw-tools-menu__brand-icon'));
    const menuHeading = element('div', 'tdw-tools-menu__heading');
    const eyebrow = element('span', 'tdw-tools-menu__eyebrow', 'Team Delivery Workspace');
    const title = element('strong', '', 'Browser tools');
    title.id = 'tdw-browser-tools-title';
    menuHeading.append(eyebrow, title);
    menuBrand.append(menuHeading);
    this.removeButton = iconButton(
      'tdw-tools-menu__close',
      'Usuń Browser Tools ze strony',
      '×'
    );
    menuHeader.append(menuBrand, this.removeButton);

    this.inspectorButton = button('tdw-tool-action', '');
    this.inspectorButton.dataset.featureId = 'ux-inspector';
    this.inspectorButton.setAttribute('aria-label', 'Uruchom TDW UX Inspector');
    const actionIcon = element('span', 'tdw-tool-action__icon');
    actionIcon.setAttribute('aria-hidden', 'true');
    actionIcon.append(createInspectIcon());
    const actionCopy = element('span', 'tdw-tool-action__copy');
    actionCopy.append(
      element('strong', '', 'UX Inspector'),
      element('span', '', 'Wskaż element i przygotuj kontekst analizy')
    );
    const actionArrow = element('span', 'tdw-tool-action__arrow', '→');
    actionArrow.setAttribute('aria-hidden', 'true');
    this.inspectorButton.append(actionIcon, actionCopy, actionArrow);

    this.profileFieldset = element('fieldset', 'tdw-capture-profile');
    this.profileFieldset.append(element('legend', '', 'Zakres danych'));
    this.profileInputs = [
      profileOption('ELEMENT_CONTEXT', 'Element', 'Bez wartości formularza', true),
      profileOption(
        'FORM_DIAGNOSTICS',
        'Formularz',
        'Dołącz dozwolone wartości najbliższego formularza',
        false
      )
    ];
    this.profileInputs.forEach(({ label }) => this.profileFieldset.append(label));
    this.profileFieldset.append(
      element(
        'p',
        'tdw-capture-profile__note',
        'Hasła, tokeny i pliki są zawsze wykluczone. Pola hidden są dołączane.'
      )
    );

    this.menu.append(menuHeader, this.profileFieldset, this.inspectorButton);

    this.launcherButton = button('tdw-tools-launcher', '');
    this.launcherButton.setAttribute('aria-label', 'Otwórz TDW Browser Tools');
    this.launcherButton.setAttribute('aria-controls', this.menu.id);
    this.launcherButton.setAttribute('aria-expanded', 'false');
    this.launcherButton.append(createBrandLogo(config, 'tdw-tools-launcher__icon'));
    const launcherHint = element('span', 'tdw-tools-launcher__hint', 'TDW');
    launcherHint.setAttribute('aria-hidden', 'true');
    this.launcherButton.append(launcherHint);
    this.dock.append(this.menu, this.launcherButton);
    this.shadowRoot.append(this.style, this.dock);

    this.onLauncherClick = this.onLauncherClick.bind(this);
    this.onInspectorClick = this.startFeature.bind(this, 'ux-inspector');
    this.onProfileChange = this.onProfileChange.bind(this);
    this.onRemoveClick = this.dispose.bind(this, 'user-removed');
    this.onDocumentPointerDown = this.onDocumentPointerDown.bind(this);
    this.onMenuKeyDown = this.onMenuKeyDown.bind(this);
  }

  TdwBrowserTools.prototype.start = function start() {
    const root = global.document.documentElement;
    if (!root) {
      console.error('[TDW Browser Tools] Document root is unavailable.');
      this.dispose('missing-document-root');
      return;
    }
    root.append(this.host);
    this.launcherButton.addEventListener('click', this.onLauncherClick);
    this.inspectorButton.addEventListener('click', this.onInspectorClick);
    this.profileInputs.forEach(({ input }) =>
      input.addEventListener('change', this.onProfileChange)
    );
    this.removeButton.addEventListener('click', this.onRemoveClick);
    this.state = 'dock-idle';
  };

  TdwBrowserTools.prototype.reveal = function reveal() {
    if (this.disposed) {
      return;
    }
    if (this.activeFeature) {
      this.activeFeature.dispose('launcher-reopened');
    }
    this.host.hidden = false;
    this.openMenu();
  };

  TdwBrowserTools.prototype.onLauncherClick = function onLauncherClick(event) {
    event.preventDefault();
    event.stopPropagation();
    if (this.menu.hidden) {
      this.openMenu();
    } else {
      this.closeMenu(true);
    }
  };

  TdwBrowserTools.prototype.openMenu = function openMenu() {
    if (this.disposed || this.activeFeature) {
      return;
    }
    this.menu.hidden = false;
    this.launcherButton.setAttribute('aria-expanded', 'true');
    this.state = 'menu-open';
    global.document.addEventListener('pointerdown', this.onDocumentPointerDown, true);
    global.addEventListener('keydown', this.onMenuKeyDown, true);
    this.menuFocusTimer = global.setTimeout(() => {
      this.menuFocusTimer = null;
      if (!this.menu.hidden) {
        this.inspectorButton.focus({ preventScroll: true });
      }
    }, 0);
  };

  TdwBrowserTools.prototype.closeMenu = function closeMenu(restoreFocus) {
    if (this.menu.hidden) {
      return;
    }
    this.menu.hidden = true;
    this.launcherButton.setAttribute('aria-expanded', 'false');
    if (this.menuFocusTimer !== null) {
      global.clearTimeout(this.menuFocusTimer);
      this.menuFocusTimer = null;
    }
    global.document.removeEventListener('pointerdown', this.onDocumentPointerDown, true);
    global.removeEventListener('keydown', this.onMenuKeyDown, true);
    if (!this.disposed && !this.activeFeature) {
      this.state = 'dock-idle';
      if (restoreFocus) {
        this.launcherButton.focus({ preventScroll: true });
      }
    }
  };

  TdwBrowserTools.prototype.onDocumentPointerDown = function onDocumentPointerDown(event) {
    if (!event.composedPath().includes(this.host)) {
      this.closeMenu(false);
    }
  };

  TdwBrowserTools.prototype.onMenuKeyDown = function onMenuKeyDown(event) {
    if (event.key !== 'Escape' || this.menu.hidden) {
      return;
    }
    consumeEvent(event);
    this.closeMenu(true);
  };

  TdwBrowserTools.prototype.onProfileChange = function onProfileChange(event) {
    if (
      event.target?.checked &&
      protocol.CAPTURE_PROFILES.includes(event.target.value)
    ) {
      this.captureProfile = event.target.value;
    }
  };

  TdwBrowserTools.prototype.startFeature = function startFeature(featureId) {
    if (this.disposed || this.activeFeature) {
      return;
    }
    const featureFactory = featureFactories.get(featureId);
    if (!featureFactory) {
      console.error('[TDW Browser Tools] Unknown feature: ' + featureId);
      return;
    }
    this.closeMenu(false);
    this.host.hidden = true;
    this.state = 'feature-active';
    const feature = featureFactory(
      this.config,
      () => this.onFeatureExit(feature),
      { captureProfile: this.captureProfile }
    );
    this.activeFeature = feature;
    feature.start();
  };

  TdwBrowserTools.prototype.onFeatureExit = function onFeatureExit(feature) {
    if (this.activeFeature !== feature) {
      return;
    }
    this.activeFeature = null;
    if (this.disposed) {
      return;
    }
    this.host.hidden = false;
    this.state = 'dock-idle';
  };

  TdwBrowserTools.prototype.dispose = function dispose() {
    if (this.disposed) {
      return;
    }
    this.disposed = true;
    this.state = 'disposed';
    this.closeMenu(false);
    this.launcherButton.removeEventListener('click', this.onLauncherClick);
    this.inspectorButton.removeEventListener('click', this.onInspectorClick);
    this.profileInputs.forEach(({ input }) =>
      input.removeEventListener('change', this.onProfileChange)
    );
    this.removeButton.removeEventListener('click', this.onRemoveClick);
    this.activeFeature?.dispose('shell-disposed');
    this.activeFeature = null;
    this.host.remove();
    if (global[SINGLETON_KEY] === this) {
      try {
        delete global[SINGLETON_KEY];
      } catch {
        // The detached shell is inert even if the page owns an immutable global.
      }
    }
  };

  function UxInspector(config, onExit, options) {
    this.config = config;
    this.onExit = onExit;
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
    this.captureProfile = protocol.CAPTURE_PROFILES.includes(options?.captureProfile)
      ? options.captureProfile
      : 'ELEMENT_CONTEXT';

    this.host = global.document.createElement('div');
    this.host.setAttribute('data-tdw-browser-tool-root', 'ux-inspector');
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
      this.captureProfile === 'FORM_DIAGNOSTICS'
        ? 'Wskaż element · dołączę najbliższy formularz'
        : 'Wskaż element i kliknij'
    );
    const statusExit = element('span', 'tdw-status-exit');
    const escapeKey = element('kbd', '', 'Esc');
    statusExit.append(escapeKey, global.document.createTextNode(' wyjdź'));
    this.status.append(
      this.statusMark,
      element('strong', '', 'TDW UX Inspector'),
      this.statusText,
      statusExit
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

  UxInspector.prototype.start = function start() {
    const root = global.document.documentElement;
    if (!root) {
      console.error('[TDW UX Inspector] Document root is unavailable.');
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

  UxInspector.prototype.dispose = function dispose(reason) {
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
    const onExit = this.onExit;
    this.onExit = null;
    onExit?.(reason || 'disposed');
  };

  UxInspector.prototype.onKeyDown = function onKeyDown(event) {
    if (event.key !== 'Escape') {
      return;
    }
    consumeEvent(event);
    this.dispose('escape');
  };

  UxInspector.prototype.onPointerMove = function onPointerMove(event) {
    if (this.state !== 'selecting') {
      return;
    }
    this.pointerX = event.clientX;
    this.pointerY = event.clientY;
    this.pointerKnown = true;
    this.scheduleTargetUpdate();
  };

  UxInspector.prototype.blockShieldEvent = function blockShieldEvent(event) {
    if (this.state !== 'selecting') {
      return;
    }
    consumeEvent(event);
  };

  UxInspector.prototype.onShieldClick = function onShieldClick(event) {
    this.blockShieldEvent(event);
    if (this.state !== 'selecting' || event.button !== 0 || !this.target) {
      return;
    }
    try {
      this.capture = protocol.captureElement(this.target, {
        clientVersion: RUNTIME_VERSION,
        featureId: this.config.featureId,
        captureProfile: this.captureProfile
      });
      this.hideSelection();
      this.beginTransfer();
    } catch (error) {
      console.error('[TDW UX Inspector] Capture failed.', error);
      this.hideSelection();
      this.failTransfer('Nie udało się bezpiecznie przygotować capture. Spróbuj ponownie.');
    }
  };

  UxInspector.prototype.onViewportChange = function onViewportChange() {
    if (this.state === 'selecting' && this.pointerKnown) {
      this.scheduleTargetUpdate();
    }
  };

  UxInspector.prototype.onWindowBlur = function onWindowBlur() {
    if (this.state === 'selecting') {
      this.dispose('window-blur');
    }
  };

  UxInspector.prototype.onVisibilityChange = function onVisibilityChange() {
    if (
      this.state === 'selecting' &&
      global.document.visibilityState !== 'visible'
    ) {
      this.dispose('document-hidden');
    }
  };

  UxInspector.prototype.scheduleTargetUpdate = function scheduleTargetUpdate() {
    if (this.animationFrame !== null) {
      return;
    }
    this.animationFrame = global.requestAnimationFrame(() => {
      this.animationFrame = null;
      this.updateTargetAtPointer();
    });
  };

  UxInspector.prototype.updateTargetAtPointer = function updateTargetAtPointer() {
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

  UxInspector.prototype.updateHighlightGeometry =
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

  UxInspector.prototype.hideHighlight = function hideHighlight() {
    this.highlight.dataset.visible = 'false';
  };

  UxInspector.prototype.hideSelection = function hideSelection() {
    this.cancelAnimationFrame();
    this.resizeObserver?.disconnect();
    this.shield.dataset.active = 'false';
    this.hideHighlight();
    this.target = null;
  };

  UxInspector.prototype.cancelAnimationFrame = function cancelAnimationFrame() {
    if (this.animationFrame !== null) {
      global.cancelAnimationFrame(this.animationFrame);
      this.animationFrame = null;
    }
  };

  UxInspector.prototype.beginTransfer = function beginTransfer() {
    this.state = 'transferring';
    this.setStatus('Otwieram UX Inspector…', 'working');
    this.bridgeNonce = protocol.createNonce();
    const captureUrl = new URL('/ux-inspector', this.config.tdwOrigin);
    captureUrl.hash = new URLSearchParams({
      nonce: this.bridgeNonce,
      sourceOrigin: global.location.origin
    }).toString();
    global.addEventListener('message', this.onBridgeMessage, true);
    this.bridgeWindow = global.open(
      captureUrl.toString(),
      `tdw-ux-inspector-${this.bridgeNonce}`
    );
    if (!this.bridgeWindow) {
      this.failTransfer('Przeglądarka zablokowała otwarcie UX Inspectora.');
      return;
    }
    this.resetBridgeTimer(
      'Nie udało się potwierdzić połączenia z UX Inspectorem.'
    );
  };

  UxInspector.prototype.onBridgeMessage = function onBridgeMessage(event) {
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
          protocol.createMessage('TDW_UX_INSPECTOR_CAPTURE', this.bridgeNonce, {
            captureId: this.capture.captureId,
            capture: this.capture
          }),
          this.config.tdwOrigin
        );
        this.state = 'awaiting-receipt';
        this.setStatus('Capture wysłany · czekam na potwierdzenie TDW…', 'working');
        this.resetBridgeTimer('UX Inspector nie potwierdził odbioru capture.');
      } catch (error) {
        console.error('[TDW UX Inspector] postMessage failed.', error);
        this.failTransfer('Nie udało się przekazać capture do UX Inspectora.');
      }
      return;
    }
    if (protocol.isErrorMessage(event.data, this.bridgeNonce)) {
      this.failTransfer('UX Inspector odrzucił capture: ' + event.data.code + '.');
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

  UxInspector.prototype.resetBridgeTimer = function resetBridgeTimer(message) {
    this.clearBridgeTimer();
    this.bridgeTimer = global.setTimeout(
      () => this.failTransfer(message),
      testMode ? 80 : BRIDGE_TIMEOUT_MS
    );
  };

  UxInspector.prototype.clearBridgeTimer = function clearBridgeTimer() {
    if (this.bridgeTimer !== null) {
      global.clearTimeout(this.bridgeTimer);
      this.bridgeTimer = null;
    }
  };

  UxInspector.prototype.setStatus = function setStatus(message, kind) {
    this.status.hidden = false;
    this.status.dataset.kind = kind || 'neutral';
    this.statusText.textContent = message;
  };

  UxInspector.prototype.failTransfer = function failTransfer(message) {
    this.clearBridgeTimer();
    global.removeEventListener('message', this.onBridgeMessage, true);
    this.state = 'failed';
    this.bridgeNonce = '';
    this.capture = null;
    try {
      this.bridgeWindow?.close();
    } catch {
      // A page policy can detach the opened window before cleanup.
    }
    this.bridgeWindow = null;
    this.setStatus(message, 'error');
    global.setTimeout(() => this.dispose('transfer-failed'), testMode ? 0 : 2200);
  };

  function consumeEvent(event) {
    event.preventDefault();
    event.stopPropagation();
    event.stopImmediatePropagation();
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

  function profileOption(value, title, description, checked) {
    const label = element('label', 'tdw-capture-profile__option');
    const input = global.document.createElement('input');
    input.type = 'radio';
    input.name = 'tdw-capture-profile';
    input.value = value;
    input.checked = checked;
    const copy = element('span', 'tdw-capture-profile__copy');
    copy.append(element('strong', '', title), element('span', '', description));
    label.append(input, copy);
    return { input, label };
  }

  function iconButton(className, label, glyph) {
    const node = button(className, glyph);
    node.setAttribute('aria-label', label);
    node.title = label;
    return node;
  }

  function createBrandLogo(config, className) {
    const image = global.document.createElement('img');
    image.className = className;
    image.src = new URL('/assets/brand/main-logo.png', config.tdwOrigin).toString();
    image.alt = '';
    image.setAttribute('aria-hidden', 'true');
    image.referrerPolicy = 'no-referrer';
    image.draggable = false;
    image.width = 64;
    image.height = 64;
    return image;
  }

  function createInspectIcon() {
    const svg = svgElement('svg', {
      viewBox: '0 0 24 24',
      'aria-hidden': 'true',
      focusable: 'false'
    });
    svg.append(
      svgElement('path', {
        d: 'M4 9V5a1 1 0 0 1 1-1h4M15 4h4a1 1 0 0 1 1 1v4M20 15v4a1 1 0 0 1-1 1h-4M9 20H5a1 1 0 0 1-1-1v-4',
        fill: 'none',
        stroke: 'currentColor',
        'stroke-linecap': 'round',
        'stroke-width': '1.8'
      }),
      svgElement('path', {
        d: 'M9 8.4l7.4 3.1-3.1 1.2-1.2 3.1z',
        fill: 'currentColor'
      })
    );
    return svg;
  }

  function svgElement(tag, attributes) {
    const node = global.document.createElementNS('http://www.w3.org/2000/svg', tag);
    Object.entries(attributes).forEach(([name, value]) =>
      node.setAttribute(name, value)
    );
    return node;
  }

  const SHELL_STYLES = `
    :host {
      all: initial;
      position: fixed;
      inset: 0;
      z-index: 2147483646;
      pointer-events: none;
      color: #172b4d;
      font-family: Inter, ui-sans-serif, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      font-size: 14px;
      line-height: 1.45;
      color-scheme: light;
    }

    :host([hidden]) { display: none; }
    *, *::before, *::after { box-sizing: border-box; }
    [hidden] { display: none !important; }

    button {
      margin: 0;
      font: inherit;
    }

    .tdw-tools-dock {
      position: fixed;
      right: max(18px, env(safe-area-inset-right));
      bottom: max(18px, env(safe-area-inset-bottom));
      display: flex;
      align-items: flex-end;
      flex-direction: column;
      gap: 12px;
      pointer-events: none;
    }

    .tdw-tools-launcher {
      position: relative;
      display: grid;
      width: 58px;
      height: 58px;
      padding: 8px;
      place-items: center;
      pointer-events: auto;
      color: #0c66e4;
      border: 1px solid #c7d7ee;
      border-radius: 17px;
      background: #ffffff;
      box-shadow:
        0 12px 30px rgba(9, 30, 66, 0.2),
        0 0 0 4px rgba(12, 102, 228, 0.1);
      cursor: pointer;
      transition: transform 150ms ease, border-color 150ms ease, box-shadow 150ms ease;
      animation: tdw-dock-arrive 220ms cubic-bezier(0.2, 0.8, 0.2, 1);
    }

    .tdw-tools-launcher:hover {
      transform: translateY(-2px);
      border-color: #85b8ff;
      box-shadow:
        0 16px 34px rgba(9, 30, 66, 0.24),
        0 0 0 5px rgba(12, 102, 228, 0.13);
    }

    .tdw-tools-launcher:focus-visible,
    .tdw-tools-menu button:focus-visible {
      outline: 3px solid rgba(12, 102, 228, 0.34);
      outline-offset: 3px;
    }

    .tdw-tools-launcher__icon {
      display: block;
      width: 40px;
      height: 40px;
      object-fit: contain;
      filter: drop-shadow(0 4px 7px rgba(7, 71, 166, 0.16));
    }

    .tdw-tools-launcher__hint {
      position: absolute;
      right: 66px;
      padding: 5px 8px;
      color: #44546f;
      font-size: 11px;
      font-weight: 800;
      letter-spacing: 0.06em;
      border: 1px solid #d6e0ed;
      border-radius: 7px;
      background: #ffffff;
      box-shadow: 0 6px 18px rgba(9, 30, 66, 0.12);
      opacity: 0;
      transform: translateX(4px);
      transition: opacity 120ms ease, transform 120ms ease;
      pointer-events: none;
    }

    .tdw-tools-launcher:hover .tdw-tools-launcher__hint,
    .tdw-tools-launcher:focus-visible .tdw-tools-launcher__hint {
      opacity: 1;
      transform: translateX(0);
    }

    .tdw-tools-menu {
      width: min(350px, calc(100vw - 28px));
      padding: 14px;
      pointer-events: auto;
      border: 1px solid #d6e0ed;
      border-radius: 16px;
      background: #fbfcfe;
      box-shadow:
        0 22px 60px rgba(9, 30, 66, 0.22),
        0 0 0 4px rgba(12, 102, 228, 0.07);
      animation: tdw-menu-arrive 150ms ease-out;
    }

    .tdw-tools-menu__header,
    .tdw-tools-menu__brand,
    .tdw-tool-action {
      display: flex;
      align-items: center;
    }

    .tdw-tools-menu__header {
      justify-content: space-between;
      gap: 12px;
    }

    .tdw-tools-menu__brand { gap: 10px; min-width: 0; }

    .tdw-tools-menu__brand-icon {
      width: 35px;
      height: 35px;
      flex: 0 0 auto;
      padding: 3px;
      border: 1px solid #d6e0ed;
      border-radius: 10px;
      background: #ffffff;
      object-fit: contain;
    }

    .tdw-tools-menu__heading {
      display: flex;
      min-width: 0;
      flex-direction: column;
    }

    .tdw-tools-menu__heading strong { color: #172b4d; font-size: 15px; }

    .tdw-tools-menu__eyebrow {
      color: #0c66e4;
      font-size: 9px;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }

    .tdw-tools-menu__close {
      width: 32px;
      height: 32px;
      flex: 0 0 auto;
      padding: 0;
      color: #626f86;
      font-size: 22px;
      line-height: 1;
      border: 0;
      border-radius: 9px;
      background: transparent;
      cursor: pointer;
    }

    .tdw-tools-menu__close:hover { color: #ae2e24; background: #ffebe6; }

    .tdw-capture-profile {
      display: grid;
      gap: 7px;
      margin: 14px 0 0;
      padding: 10px;
      border: 1px solid #d6e0ed;
      border-radius: 12px;
      background: #ffffff;
    }

    .tdw-capture-profile legend {
      padding: 0 4px;
      color: #44546f;
      font-size: 10px;
      font-weight: 800;
      letter-spacing: 0.06em;
      text-transform: uppercase;
    }

    .tdw-capture-profile__option {
      display: flex;
      gap: 8px;
      align-items: flex-start;
      padding: 7px;
      border-radius: 8px;
      cursor: pointer;
    }

    .tdw-capture-profile__option:hover { background: #f1f5f9; }
    .tdw-capture-profile__option input { margin: 3px 0 0; accent-color: #0c66e4; }

    .tdw-capture-profile__copy {
      display: flex;
      min-width: 0;
      flex-direction: column;
    }

    .tdw-capture-profile__copy strong { color: #172b4d; font-size: 12px; }
    .tdw-capture-profile__copy span { color: #626f86; font-size: 10px; }

    .tdw-capture-profile__note {
      margin: 2px 4px 0;
      color: #626f86;
      font-size: 9px;
      line-height: 1.35;
    }

    .tdw-tool-action {
      width: 100%;
      gap: 11px;
      margin-top: 14px;
      padding: 11px;
      color: #172b4d;
      text-align: left;
      border: 1px solid #c7d7ee;
      border-radius: 12px;
      background: #ffffff;
      cursor: pointer;
      transition: border-color 140ms ease, box-shadow 140ms ease, transform 140ms ease;
    }

    .tdw-tool-action:hover {
      transform: translateY(-1px);
      border-color: #85b8ff;
      box-shadow: 0 8px 20px rgba(9, 30, 66, 0.1);
    }

    .tdw-tool-action__icon {
      display: grid;
      width: 38px;
      height: 38px;
      flex: 0 0 auto;
      place-items: center;
      color: #0c66e4;
      border: 1px solid #b3d4ff;
      border-radius: 10px;
      background: #e9f2ff;
    }

    .tdw-tool-action__icon svg { width: 22px; height: 22px; }

    .tdw-tool-action__copy {
      display: flex;
      min-width: 0;
      flex: 1;
      flex-direction: column;
    }

    .tdw-tool-action__copy strong { color: #0747a6; font-size: 14px; }
    .tdw-tool-action__copy span { color: #626f86; font-size: 11px; }
    .tdw-tool-action__arrow { color: #0c66e4; font-size: 20px; }

    @keyframes tdw-dock-arrive {
      from { opacity: 0; transform: translateY(10px) scale(0.94); }
    }

    @keyframes tdw-menu-arrive {
      from { opacity: 0; transform: translateY(6px) scale(0.98); }
    }

    @media (max-width: 480px) {
      .tdw-tools-dock { right: 12px; bottom: 12px; }
    }

    @media (prefers-reduced-motion: reduce) {
      .tdw-tools-launcher,
      .tdw-tools-menu { animation: none; transition: none; }
    }
  `;

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
      color: #172b4d;
      border: 1px solid #c7d7ee;
      border-radius: 999px;
      background: #fbfcfe;
      box-shadow: 0 12px 36px rgba(9, 30, 66, 0.2), 0 0 0 4px rgba(12, 102, 228, 0.08);
      backdrop-filter: blur(10px);
      white-space: nowrap;
    }

    .tdw-status[hidden] { display: none; }
    .tdw-status[data-kind="success"] { border-color: #7ee2b8; background: #eaf7f0; }
    .tdw-status[data-kind="error"] { border-color: #ffb8b2; background: #fff1f0; }
    .tdw-status strong { color: #0747a6; }

    .tdw-status-mark {
      width: 9px;
      height: 9px;
      flex: 0 0 auto;
      border: 2px solid #ffffff;
      border-radius: 50%;
      background: #0c66e4;
      box-shadow: 0 0 0 5px rgba(12, 102, 228, 0.13), 0 0 14px rgba(12, 102, 228, 0.48);
      animation: tdw-status-pulse 1.35s ease-in-out infinite;
    }

    .tdw-status[data-kind="success"] .tdw-status-mark {
      background: #1f845a;
      box-shadow: 0 0 0 5px rgba(31, 132, 90, 0.12);
    }

    .tdw-status[data-kind="error"] .tdw-status-mark {
      background: #c9372c;
      box-shadow: 0 0 0 5px rgba(201, 55, 44, 0.12);
    }

    .tdw-status-text {
      overflow: hidden;
      color: #44546f;
      text-overflow: ellipsis;
    }

    .tdw-status-exit {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      margin-left: 3px;
      padding-left: 10px;
      color: #626f86;
      font-size: 12px;
      border-left: 1px solid #d6e0ed;
    }

    .tdw-status-exit kbd {
      padding: 1px 5px;
      color: #44546f;
      font: 700 10px/1.5 ui-monospace, SFMono-Regular, Consolas, monospace;
      border: 1px solid #b6c2cf;
      border-bottom-width: 2px;
      border-radius: 5px;
      background: #ffffff;
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

    @media (max-width: 620px) {
      .tdw-status strong { display: none; }
    }

    @media (prefers-reduced-motion: reduce) {
      .tdw-highlight,
      .tdw-highlight-scan::after,
      .tdw-status-mark { animation: none; }
    }
  `;

  const instance = new TdwBrowserTools(configResult.value);
  try {
    delete global[SINGLETON_KEY];
    Object.defineProperty(global, SINGLETON_KEY, {
      configurable: true,
      enumerable: false,
      writable: false,
      value: instance
    });
  } catch (error) {
    console.error('[TDW Browser Tools] Could not establish a singleton.', error);
    return;
  }
  instance.start();
})(globalThis);
