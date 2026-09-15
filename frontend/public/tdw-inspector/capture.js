(function initializeCapturePage(global) {
  'use strict';

  const protocol = global.__TDW_BROWSER_TOOLS_PROTOCOL_V1__;
  const bridgeStatus = byId('bridge-status');
  const manualImport = byId('manual-import');
  const manualJson = byId('manual-json');
  const manualError = byId('manual-error');
  const importButton = byId('import-capture');
  const workspace = byId('capture-workspace');
  const form = byId('analysis-form');
  const question = byId('question');
  const model = byId('model');
  const reasoningEffort = byId('reasoning-effort');
  const startButton = byId('start-analysis');
  const copyCaptureButton = byId('copy-capture');
  const resultCard = byId('dummy-result');
  let currentCapture = null;
  let bridgeTimer = null;

  if (!protocol) {
    setBridgeStatus('Nie udało się załadować lokalnego protokołu capture.', 'error');
    manualImport.open = true;
    return;
  }

  const bridge = readBridgeParameters();
  const bridgeSource = global.opener;
  global.addEventListener('message', onBridgeMessage, true);
  importButton.addEventListener('click', importManualCapture);
  copyCaptureButton.addEventListener('click', copyCurrentCapture);
  question.addEventListener('input', () => question.setCustomValidity(''));
  form.addEventListener('submit', startDummyAnalysis);

  if (bridge && bridgeSource) {
    try {
      bridgeSource.postMessage(
        protocol.createMessage('TDW_INSPECTOR_READY', bridge.nonce),
        bridge.sourceOrigin
      );
      bridgeTimer = global.setTimeout(() => {
        setBridgeStatus(
          'Automatyczny transfer nie odpowiedział. Wklej capture ręcznie.',
          'warning'
        );
        manualImport.open = true;
      }, global.__TDW_INSPECTOR_TEST_MODE__ === true ? 80 : 7000);
    } catch (error) {
      console.error('[TDW Inspector Lite] Ready handshake failed.', error);
      setBridgeStatus('Nie udało się połączyć z badaną stroną. Wklej capture ręcznie.', 'error');
      manualImport.open = true;
    }
  } else {
    setBridgeStatus('Otwórz badany ekran lub wklej zredagowany capture ręcznie.', 'neutral');
    manualImport.open = true;
  }

  clearFragment();

  function readBridgeParameters() {
    const params = new URLSearchParams(global.location.hash.slice(1));
    const nonce = params.get('nonce') || '';
    const sourceOrigin = protocol.normalizeOrigin(params.get('sourceOrigin'));
    if (!/^[A-Za-z0-9_-]{16,96}$/.test(nonce) || !sourceOrigin) {
      return null;
    }
    return { nonce, sourceOrigin };
  }

  function clearFragment() {
    if (!global.location.hash) {
      return;
    }
    try {
      global.history.replaceState(
        null,
        '',
        global.location.pathname + global.location.search
      );
    } catch {
      // The fragment never reaches the server; clearing is defense in depth.
    }
  }

  function onBridgeMessage(event) {
    if (
      !bridge ||
      !bridgeSource ||
      event.origin !== bridge.sourceOrigin ||
      event.source !== bridgeSource ||
      !protocol.isCaptureMessage(event.data, bridge.nonce)
    ) {
      return;
    }
    if (!acceptCapture(event.data.capture, 'automatic')) {
      return;
    }
    try {
      bridgeSource.postMessage(
        protocol.createMessage('TDW_INSPECTOR_RECEIVED', bridge.nonce, {
          captureId: currentCapture.captureId
        }),
        bridge.sourceOrigin
      );
    } catch (error) {
      console.error('[TDW Inspector Lite] Receipt acknowledgement failed.', error);
    }
    global.removeEventListener('message', onBridgeMessage, true);
  }

  function importManualCapture() {
    manualError.textContent = '';
    let parsed;
    try {
      parsed = JSON.parse(manualJson.value);
    } catch {
      manualError.textContent = 'Wklejony tekst nie jest poprawnym JSON.';
      return;
    }
    if (acceptCapture(parsed, 'manual')) {
      manualImport.open = false;
    }
  }

  function acceptCapture(candidate, source) {
    const normalized = protocol.normalizeCapture(candidate);
    if (!normalized.ok) {
      const message = `Capture został odrzucony: ${normalized.error}`;
      setBridgeStatus(message, 'error');
      manualError.textContent = message;
      manualImport.open = true;
      return false;
    }
    if (
      source === 'automatic' &&
      bridge &&
      normalized.value.page.origin !== bridge.sourceOrigin
    ) {
      const message = 'Capture został odrzucony: origin payloadu nie odpowiada badanej stronie.';
      setBridgeStatus(message, 'error');
      manualError.textContent = message;
      manualImport.open = true;
      return false;
    }
    if (bridgeTimer !== null) {
      global.clearTimeout(bridgeTimer);
      bridgeTimer = null;
    }
    currentCapture = normalized.value;
    renderCapture(currentCapture);
    setBridgeStatus(
      source === 'automatic'
        ? 'Capture odebrany i zwalidowany. Możesz zadać pytanie.'
        : 'Ręczny capture został zwalidowany. Możesz zadać pytanie.',
      'ready'
    );
    manualError.textContent = '';
    workspace.hidden = false;
    global.setTimeout(() => question.focus({ preventScroll: true }), 0);
    return true;
  }

  function renderCapture(capture) {
    const target = capture.selection.target;
    byId('target-heading').textContent = [
      target.tag,
      target.role ? `role=${target.role}` : null
    ]
      .filter(Boolean)
      .join(' · ');
    byId('target-description').textContent =
      target.accessibleName || target.text || 'Element bez dostępnej nazwy';
    byId('page-origin').textContent = capture.page.origin;
    byId('page-path').textContent =
      capture.page.pathname + (capture.page.routeHash || '');
    byId('ancestry-summary').textContent =
      `${capture.selection.traversal.emittedNodeCount} z ${capture.selection.traversal.observedDepth} węzłów`;
    byId('state-summary').textContent = summarizeState(target.state);
    byId('capture-size').textContent = formatBytes(protocol.serializedSize(capture));
    byId('capture-json').textContent = JSON.stringify(capture, null, 2);
  }

  function summarizeState(state) {
    const active = [
      state.disabled ? 'disabled' : null,
      state.ariaDisabled ? 'aria-disabled' : null,
      state.readOnly ? 'readonly' : null,
      state.required ? 'required' : null,
      state.invalid ? 'invalid' : null,
      state.checked === true ? 'checked' : null,
      state.checked === false ? 'unchecked' : null,
      state.expanded === true ? 'expanded' : null,
      state.expanded === false ? 'collapsed' : null,
      state.hidden ? 'hidden' : null
    ].filter(Boolean);
    return active.length ? active.join(' · ') : 'brak jawnego stanu';
  }

  async function copyCurrentCapture() {
    if (!currentCapture) return;
    const copied = await copyText(JSON.stringify(currentCapture, null, 2), manualJson);
    const original = copyCaptureButton.textContent;
    copyCaptureButton.textContent = copied ? 'Skopiowano' : 'Skopiuj ręcznie z preview';
    global.setTimeout(() => {
      copyCaptureButton.textContent = original;
    }, 1700);
  }

  function startDummyAnalysis(event) {
    event.preventDefault();
    if (!currentCapture || !form.reportValidity()) {
      return;
    }
    const normalizedQuestion = question.value.trim();
    if (!normalizedQuestion) {
      question.setCustomValidity('Podaj pytanie lub polecenie.');
      question.reportValidity();
      return;
    }
    question.setCustomValidity('');
    startButton.disabled = true;
    startButton.textContent = 'Przyjęto';
    resultCard.hidden = false;
    byId('result-title').textContent = 'Analiza demonstracyjna rozpoczęta';
    const jobId = createDummyJobId();
    byId('result-detail').textContent =
      `DUMMY · QUEUED · ${jobId} · ${model.value} / ${reasoningEffort.value}`;
    resultCard.scrollIntoView({ block: 'nearest', behavior: reducedMotion() ? 'auto' : 'smooth' });
  }

  function createDummyJobId() {
    const token = global.crypto?.randomUUID
      ? global.crypto.randomUUID().slice(0, 12)
      : Date.now().toString(36);
    return `ui-demo-${token}`;
  }

  function reducedMotion() {
    return global.matchMedia?.('(prefers-reduced-motion: reduce)').matches === true;
  }

  async function copyText(value, fallbackControl) {
    try {
      await global.navigator.clipboard.writeText(value);
      return true;
    } catch {
      try {
        fallbackControl.value = value;
        fallbackControl.focus();
        fallbackControl.select();
        return global.document.execCommand('copy');
      } catch {
        return false;
      }
    }
  }

  function setBridgeStatus(message, kind) {
    bridgeStatus.textContent = message;
    bridgeStatus.dataset.kind = kind;
  }

  function formatBytes(value) {
    return value < 1024 ? `${value} B` : `${(value / 1024).toFixed(1)} KiB`;
  }

  function byId(id) {
    const node = global.document.getElementById(id);
    if (!node) {
      throw new Error(`Missing capture element #${id}`);
    }
    return node;
  }
})(globalThis);
