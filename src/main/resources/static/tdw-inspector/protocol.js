(function installTdwBrowserToolsProtocol(global) {
  'use strict';

  const GLOBAL_KEY = '__TDW_BROWSER_TOOLS_PROTOCOL_V1__';
  const CHANNEL = 'tdw.browser-tools';
  const PROTOCOL_VERSION = 1;
  const CAPTURE_VERSION = 1;
  const REMOTE_LOADER_VERSION = '1.0.0';
  const MAX_CAPTURE_BYTES = 64 * 1024;
  const MAX_TEXT_LENGTH = 180;
  const MAX_ACCESSIBLE_NAME_LENGTH = 140;
  const MAX_ATTRIBUTE_LENGTH = 100;
  const MAX_CLASSES = 8;
  const MAX_ANCESTORS = 24;
  const MAX_QUERY_NAMES = 16;
  const STABLE_ATTRIBUTES = Object.freeze([
    'data-testid',
    'data-test',
    'data-cy',
    'name',
    'type'
  ]);
  const SEMANTIC_TAGS = new Set([
    'article',
    'aside',
    'dialog',
    'fieldset',
    'form',
    'footer',
    'header',
    'main',
    'nav',
    'section'
  ]);
  const SENSITIVE_PATTERN =
    /(authorization|bearer|cookie|csrf|jwt|pass(word|wd)?|secret|session|token)/i;
  const DYNAMIC_IDENTIFIER_PATTERN =
    /(@|[0-9]{6,}|[0-9a-f]{8}-[0-9a-f-]{20,})/i;
  const EMAIL_PATTERN = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi;
  const EMAIL_TEST_PATTERN = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/i;
  const UUID_PATTERN =
    /\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b/gi;
  const UUID_TEST_PATTERN =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  const LONG_NUMBER_PATTERN = /\b\d{6,}\b/g;
  const SAFE_TOKEN_PATTERN = /^[A-Za-z][A-Za-z0-9_.:-]*$/;
  const SAFE_CAPTURE_ID_PATTERN = /^[A-Za-z0-9_-]{8,96}$/;

  function success(value) {
    return { ok: true, value };
  }

  function failure(error) {
    return { ok: false, error };
  }

  function isObject(value) {
    return value !== null && typeof value === 'object' && !Array.isArray(value);
  }

  function truncate(value, limit) {
    return value.length <= limit
      ? value
      : `${value.slice(0, Math.max(0, limit - 1))}…`;
  }

  function normalizeText(value, limit) {
    if (typeof value !== 'string' || !value) {
      return null;
    }
    const normalized = value
      .replace(EMAIL_PATTERN, '[EMAIL]')
      .replace(UUID_PATTERN, '[ID]')
      .replace(LONG_NUMBER_PATTERN, '[NUMBER]')
      .replace(/\s+/g, ' ')
      .trim();
    if (!normalized || SENSITIVE_PATTERN.test(normalized)) {
      return null;
    }
    return truncate(normalized, limit);
  }

  function isSafeIdentifier(value) {
    if (typeof value !== 'string') {
      return false;
    }
    const normalized = value.trim();
    return (
      normalized.length > 0 &&
      normalized.length <= MAX_ATTRIBUTE_LENGTH &&
      SAFE_TOKEN_PATTERN.test(normalized) &&
      !SENSITIVE_PATTERN.test(normalized) &&
      !DYNAMIC_IDENTIFIER_PATTERN.test(normalized)
    );
  }

  function normalizeOrigin(value) {
    if (typeof value !== 'string' || value.length > 2048) {
      return null;
    }
    try {
      const url = new URL(value);
      if (
        !['http:', 'https:'].includes(url.protocol) ||
        url.username ||
        url.password ||
        url.pathname !== '/' ||
        url.search ||
        url.hash ||
        url.origin === 'null'
      ) {
        return null;
      }
      return url.origin;
    } catch {
      return null;
    }
  }

  function sanitizePathname(pathname) {
    if (typeof pathname !== 'string') {
      return '/';
    }
    const normalizedPath = pathname.startsWith('/') ? pathname : `/${pathname}`;
    const sanitized = normalizedPath
      .split('/')
      .map((segment) => {
        if (!segment) {
          return '';
        }
        let decoded;
        try {
          decoded = decodeURIComponent(segment);
        } catch {
          return ':value';
        }
        if (
          /^\d+$/.test(decoded) ||
          UUID_TEST_PATTERN.test(decoded) ||
          EMAIL_TEST_PATTERN.test(decoded) ||
          decoded.length > 80
        ) {
          return ':value';
        }
        return truncate(segment, 80);
      })
      .join('/');
    return truncate(sanitized || '/', 500);
  }

  function safeRouteHash(hash) {
    if (typeof hash !== 'string') {
      return null;
    }
    let route = hash;
    if (route.startsWith('#!/')) {
      route = route.slice(2);
    } else if (route.startsWith('#/')) {
      route = route.slice(1);
    } else if (!route.startsWith('/')) {
      return null;
    }
    route = route.split('?')[0] || '';
    if (!route || route.includes('=') || SENSITIVE_PATTERN.test(route)) {
      return null;
    }
    return sanitizePathname(route);
  }

  function safeClasses(element) {
    return Array.from(element.classList || [])
      .filter(isSafeIdentifier)
      .slice(0, MAX_CLASSES)
      .map((value) => truncate(value, 64));
  }

  function stableAttributes(element) {
    const result = {};
    const id = element.getAttribute('id');
    if (id && isSafeIdentifier(id)) {
      result.id = truncate(id, MAX_ATTRIBUTE_LENGTH);
    }
    for (const name of STABLE_ATTRIBUTES) {
      const value = element.getAttribute(name);
      if (value && isSafeIdentifier(value)) {
        result[name] = truncate(value, MAX_ATTRIBUTE_LENGTH);
      }
    }
    return result;
  }

  function isFormValueCarrier(element) {
    return (
      element.matches('input, textarea, select') ||
      element.matches('[contenteditable]:not([contenteditable="false"])')
    );
  }

  function safeElementText(element) {
    if (isFormValueCarrier(element)) {
      return null;
    }
    if (!global.NodeFilter || typeof global.document.createTreeWalker !== 'function') {
      return null;
    }
    const walker = global.document.createTreeWalker(
      element,
      global.NodeFilter.SHOW_TEXT,
      {
        acceptNode(node) {
          const parent = node.parentElement;
          if (
            !parent ||
            parent.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"])') ||
            parent.closest('script, style, noscript, template')
          ) {
            return global.NodeFilter.FILTER_REJECT;
          }
          return global.NodeFilter.FILTER_ACCEPT;
        }
      }
    );
    const fragments = [];
    let visited = 0;
    let rawLength = 0;
    let node = walker.nextNode();
    while (node && visited < 500 && fragments.length < 80 && rawLength < 1000) {
      visited += 1;
      const value = node.nodeValue?.trim();
      if (value) {
        fragments.push(value);
        rawLength += value.length;
      }
      node = walker.nextNode();
    }
    return normalizeText(fragments.join(' '), MAX_TEXT_LENGTH);
  }

  function labelledByText(element) {
    const ids = (element.getAttribute('aria-labelledby') || '')
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 8);
    if (!ids.length) {
      return null;
    }
    const root = element.getRootNode();
    const labels = ids
      .map((id) => {
        if (
          (root instanceof global.Document || root instanceof global.ShadowRoot) &&
          typeof root.getElementById === 'function'
        ) {
          return root.getElementById(id)?.textContent || '';
        }
        return '';
      })
      .join(' ');
    return normalizeText(labels, MAX_ACCESSIBLE_NAME_LENGTH);
  }

  function accessibleName(element) {
    const ariaLabel = normalizeText(
      element.getAttribute('aria-label'),
      MAX_ACCESSIBLE_NAME_LENGTH
    );
    if (ariaLabel) {
      return ariaLabel;
    }
    const labelled = labelledByText(element);
    if (labelled) {
      return labelled;
    }
    if (
      global.HTMLInputElement &&
      element instanceof global.HTMLInputElement &&
      element.labels?.length
    ) {
      const label = Array.from(element.labels)
        .map((item) => item.textContent || '')
        .join(' ');
      const normalized = normalizeText(label, MAX_ACCESSIBLE_NAME_LENGTH);
      if (normalized) {
        return normalized;
      }
    }
    const title = normalizeText(element.getAttribute('title'), MAX_ACCESSIBLE_NAME_LENGTH);
    if (title) {
      return title;
    }
    const tag = element.tagName.toLowerCase();
    const textNamedTags = new Set([
      'a',
      'button',
      'label',
      'legend',
      'option',
      'summary',
      'td',
      'th'
    ]);
    return textNamedTags.has(tag) ? safeElementText(element) : null;
  }

  function inferredRole(element) {
    const explicit = normalizeText(element.getAttribute('role'), 40);
    if (explicit) {
      return explicit;
    }
    const tag = element.tagName.toLowerCase();
    if (tag === 'button') return 'button';
    if (tag === 'a' && element.hasAttribute('href')) return 'link';
    if (tag === 'select') return 'combobox';
    if (tag === 'textarea') return 'textbox';
    if (global.HTMLInputElement && element instanceof global.HTMLInputElement) {
      if (element.type === 'checkbox') return 'checkbox';
      if (element.type === 'radio') return 'radio';
      if (['button', 'submit', 'reset'].includes(element.type)) return 'button';
      return 'textbox';
    }
    return null;
  }

  function booleanAttribute(element, name) {
    const value = element.getAttribute(name);
    if (value === 'true') return true;
    if (value === 'false') return false;
    return null;
  }

  function elementState(element) {
    const isInput =
      global.HTMLInputElement && element instanceof global.HTMLInputElement;
    const isTextarea =
      global.HTMLTextAreaElement && element instanceof global.HTMLTextAreaElement;
    const isSelect =
      global.HTMLSelectElement && element instanceof global.HTMLSelectElement;
    const isButton =
      global.HTMLButtonElement && element instanceof global.HTMLButtonElement;
    const formControl = isInput || isTextarea || isSelect || isButton ? element : null;
    const checkable =
      isInput && ['checkbox', 'radio'].includes(element.type) ? element : null;
    return {
      disabled: Boolean(formControl?.disabled),
      ariaDisabled: element.getAttribute('aria-disabled') === 'true',
      readOnly: Boolean((isInput || isTextarea) && element.readOnly),
      required: Boolean(formControl && 'required' in formControl && formControl.required),
      invalid:
        element.getAttribute('aria-invalid') === 'true' ||
        Boolean(formControl && 'validity' in formControl && !formControl.validity.valid),
      checked: checkable ? Boolean(checkable.checked) : null,
      expanded: booleanAttribute(element, 'aria-expanded'),
      hidden:
        Boolean(global.HTMLElement && element instanceof global.HTMLElement && element.hidden) ||
        element.getAttribute('aria-hidden') === 'true'
    };
  }

  function describeElement(element) {
    return {
      tag: element.tagName.toLowerCase(),
      role: inferredRole(element),
      accessibleName: accessibleName(element),
      text: safeElementText(element),
      stableAttributes: stableAttributes(element),
      classes: safeClasses(element),
      state: elementState(element)
    };
  }

  function collectAncestry(element) {
    const elements = [];
    let current = element;
    let shadowBoundaryCount = 0;
    while (current) {
      elements.push(current);
      if (current.parentElement) {
        current = current.parentElement;
        continue;
      }
      const root = current.getRootNode();
      if (global.ShadowRoot && root instanceof global.ShadowRoot) {
        shadowBoundaryCount += 1;
        current = root.host;
        continue;
      }
      current = null;
    }
    return { elements, shadowBoundaryCount };
  }

  function isSemanticAncestor(element) {
    const tag = element.tagName.toLowerCase();
    return (
      SEMANTIC_TAGS.has(tag) ||
      tag.includes('-') ||
      Boolean(element.getAttribute('role')) ||
      Object.keys(stableAttributes(element)).length > 0
    );
  }

  function describeAncestor(element, depth) {
    const descriptor = describeElement(element);
    return {
      depth,
      tag: descriptor.tag,
      role: descriptor.role,
      accessibleName: descriptor.accessibleName,
      text: depth <= 8 ? descriptor.text : null,
      stableAttributes: descriptor.stableAttributes,
      classes: descriptor.classes
    };
  }

  function selectAncestors(elements) {
    const selectedIndices = new Set();
    const nearTargetCount = Math.min(8, elements.length);
    const nearRootStart = Math.max(nearTargetCount, elements.length - 4);
    for (let index = 0; index < nearTargetCount; index += 1) {
      selectedIndices.add(index);
    }
    for (let index = nearRootStart; index < elements.length; index += 1) {
      selectedIndices.add(index);
    }
    elements.forEach((candidate, index) => {
      if (selectedIndices.size < MAX_ANCESTORS && isSemanticAncestor(candidate)) {
        selectedIndices.add(index);
      }
    });
    return Array.from(selectedIndices)
      .sort((left, right) => left - right)
      .slice(0, MAX_ANCESTORS)
      .map((index) => describeAncestor(elements[index], index + 1));
  }

  function createRandomToken(prefix) {
    if (global.crypto?.randomUUID) {
      return `${prefix}${global.crypto.randomUUID().replaceAll('-', '')}`;
    }
    if (global.crypto?.getRandomValues) {
      const bytes = new Uint8Array(18);
      global.crypto.getRandomValues(bytes);
      const token = Array.from(bytes)
        .map((value) => value.toString(16).padStart(2, '0'))
        .join('');
      return `${prefix}${token}`;
    }
    return `${prefix}${Date.now().toString(36)}${performance.now().toString(36).replace('.', '')}`;
  }

  function createCaptureId() {
    return createRandomToken('cap_');
  }

  function createNonce() {
    return createRandomToken('n_');
  }

  function round(value) {
    return Math.round(value * 100) / 100;
  }

  function captureElement(element, options) {
    if (!(element instanceof global.Element)) {
      throw new TypeError('TDW Inspector requires a DOM Element.');
    }
    const pageUrl = new URL(options?.pageUrl || global.location.href);
    if (!['http:', 'https:'].includes(pageUrl.protocol)) {
      throw new Error('TDW Inspector supports only HTTP(S) pages.');
    }
    const ancestry = collectAncestry(element);
    const emittedAncestors = selectAncestors(ancestry.elements.slice(1));
    const rect = element.getBoundingClientRect();
    const capture = {
      captureVersion: CAPTURE_VERSION,
      captureId: createCaptureId(),
      capturedAt: options?.capturedAt || new Date().toISOString(),
      page: {
        origin: pageUrl.origin,
        pathname: sanitizePathname(pageUrl.pathname),
        routeHash: safeRouteHash(pageUrl.hash),
        queryParameterNames: Array.from(new Set(pageUrl.searchParams.keys()))
          .filter(isSafeIdentifier)
          .slice(0, MAX_QUERY_NAMES),
        title: normalizeText(global.document.title, 180) || '',
        language: normalizeText(global.document.documentElement.lang, 20)
      },
      selection: {
        target: describeElement(element),
        ancestors: emittedAncestors,
        traversal: {
          observedDepth: ancestry.elements.length,
          emittedNodeCount: emittedAncestors.length + 1,
          omittedNodeCount: Math.max(
            0,
            ancestry.elements.length - emittedAncestors.length - 1
          ),
          reachedDocumentRoot:
            ancestry.elements.at(-1)?.tagName.toLowerCase() ===
            global.document.documentElement.tagName.toLowerCase()
        },
        shadowBoundaryCount: ancestry.shadowBoundaryCount,
        frame: global.top === global ? 'TOP_LEVEL' : 'SAME_ORIGIN_FRAME',
        viewportBounds: {
          x: round(rect.x),
          y: round(rect.y),
          width: round(rect.width),
          height: round(rect.height)
        }
      },
      client: {
        name: 'TDW Inspector Lite',
        version: options?.clientVersion || '1.0.0',
        featureId: options?.featureId || 'ui-explorer-inspector'
      }
    };
    const normalized = normalizeCapture(capture);
    if (!normalized.ok) {
      throw new Error(normalized.error);
    }
    return normalized.value;
  }

  function boundedString(value, limit, allowEmpty) {
    if (typeof value !== 'string') {
      return null;
    }
    const trimmed = value.trim();
    if ((!allowEmpty && !trimmed) || trimmed.length > limit) {
      return null;
    }
    return trimmed;
  }

  function safeInteger(value, fallback, max) {
    return Number.isInteger(value) && value >= 0 && value <= max ? value : fallback;
  }

  function safeNumber(value) {
    return typeof value === 'number' && Number.isFinite(value)
      ? Math.max(-1000000, Math.min(1000000, round(value)))
      : 0;
  }

  function normalizeState(value) {
    const state = isObject(value) ? value : {};
    const optionalBoolean = (candidate) =>
      candidate === true || candidate === false ? candidate : null;
    return {
      disabled: state.disabled === true,
      ariaDisabled: state.ariaDisabled === true,
      readOnly: state.readOnly === true,
      required: state.required === true,
      invalid: state.invalid === true,
      checked: optionalBoolean(state.checked),
      expanded: optionalBoolean(state.expanded),
      hidden: state.hidden === true
    };
  }

  function normalizeStableAttributes(value) {
    if (!isObject(value)) {
      return {};
    }
    const allowedNames = new Set(['id', ...STABLE_ATTRIBUTES]);
    const result = {};
    for (const [name, candidate] of Object.entries(value)) {
      if (
        allowedNames.has(name) &&
        typeof candidate === 'string' &&
        isSafeIdentifier(candidate)
      ) {
        result[name] = truncate(candidate, MAX_ATTRIBUTE_LENGTH);
      }
    }
    return result;
  }

  function normalizeDescriptor(value, includeState) {
    if (!isObject(value)) {
      return null;
    }
    const tag = boundedString(value.tag, 40, false);
    if (!tag || !/^[a-z][a-z0-9-]*$/.test(tag.toLowerCase())) {
      return null;
    }
    const role = normalizeText(value.role, 40);
    const descriptor = {
      tag: tag.toLowerCase(),
      role,
      accessibleName: normalizeText(value.accessibleName, MAX_ACCESSIBLE_NAME_LENGTH),
      text: normalizeText(value.text, MAX_TEXT_LENGTH),
      stableAttributes: normalizeStableAttributes(value.stableAttributes),
      classes: Array.isArray(value.classes)
        ? Array.from(new Set(value.classes.filter(isSafeIdentifier)))
            .slice(0, MAX_CLASSES)
            .map((item) => truncate(item, 64))
        : []
    };
    if (includeState) {
      descriptor.state = normalizeState(value.state);
    }
    return descriptor;
  }

  function serializedSize(value) {
    let json;
    try {
      json = JSON.stringify(value);
    } catch {
      return Number.POSITIVE_INFINITY;
    }
    if (global.TextEncoder) {
      return new global.TextEncoder().encode(json).byteLength;
    }
    return json.length * 2;
  }

  function normalizeCapture(input) {
    if (!isObject(input)) {
      return failure('Capture must be an object.');
    }
    if (serializedSize(input) > MAX_CAPTURE_BYTES) {
      return failure('Capture exceeds the 64 KiB limit.');
    }
    if (input.captureVersion !== CAPTURE_VERSION) {
      return failure('Unsupported capture version.');
    }
    const captureId = boundedString(input.captureId, 96, false);
    if (!captureId || !SAFE_CAPTURE_ID_PATTERN.test(captureId)) {
      return failure('Invalid capture id.');
    }
    const capturedAt = boundedString(input.capturedAt, 64, false);
    if (!capturedAt || Number.isNaN(Date.parse(capturedAt))) {
      return failure('Invalid capture timestamp.');
    }
    if (!isObject(input.page) || !isObject(input.selection) || !isObject(input.client)) {
      return failure('Capture sections are missing.');
    }
    const origin = normalizeOrigin(input.page.origin);
    if (!origin) {
      return failure('Capture page origin is invalid.');
    }
    const target = normalizeDescriptor(input.selection.target, true);
    if (!target) {
      return failure('Capture target is invalid.');
    }
    const ancestorsInput = Array.isArray(input.selection.ancestors)
      ? input.selection.ancestors.slice(0, MAX_ANCESTORS)
      : [];
    const ancestors = [];
    for (const candidate of ancestorsInput) {
      const descriptor = normalizeDescriptor(candidate, false);
      if (!descriptor) {
        continue;
      }
      ancestors.push({
        depth: safeInteger(candidate.depth, ancestors.length + 1, 1000),
        ...descriptor
      });
    }
    const traversalInput = isObject(input.selection.traversal)
      ? input.selection.traversal
      : {};
    const viewportInput = isObject(input.selection.viewportBounds)
      ? input.selection.viewportBounds
      : {};
    const queryParameterNames = Array.isArray(input.page.queryParameterNames)
      ? Array.from(new Set(input.page.queryParameterNames.filter(isSafeIdentifier))).slice(
          0,
          MAX_QUERY_NAMES
        )
      : [];
    const frame = ['TOP_LEVEL', 'SAME_ORIGIN_FRAME'].includes(input.selection.frame)
      ? input.selection.frame
      : 'TOP_LEVEL';
    const clientName = boundedString(input.client.name, 80, false);
    const clientVersion = boundedString(input.client.version, 32, false);
    const featureId = boundedString(input.client.featureId, 80, false);
    if (
      clientName !== 'TDW Inspector Lite' ||
      !clientVersion ||
      featureId !== 'ui-explorer-inspector'
    ) {
      return failure('Capture client metadata is invalid.');
    }
    const normalized = {
      captureVersion: CAPTURE_VERSION,
      captureId,
      capturedAt: new Date(capturedAt).toISOString(),
      page: {
        origin,
        pathname: sanitizePathname(input.page.pathname),
        routeHash: safeRouteHash(input.page.routeHash),
        queryParameterNames,
        title: normalizeText(input.page.title, 180) || '',
        language: normalizeText(input.page.language, 20)
      },
      selection: {
        target,
        ancestors,
        traversal: {
          observedDepth: safeInteger(traversalInput.observedDepth, ancestors.length + 1, 10000),
          emittedNodeCount: safeInteger(
            traversalInput.emittedNodeCount,
            ancestors.length + 1,
            MAX_ANCESTORS + 1
          ),
          omittedNodeCount: safeInteger(traversalInput.omittedNodeCount, 0, 10000),
          reachedDocumentRoot: traversalInput.reachedDocumentRoot === true
        },
        shadowBoundaryCount: safeInteger(input.selection.shadowBoundaryCount, 0, 100),
        frame,
        viewportBounds: {
          x: safeNumber(viewportInput.x),
          y: safeNumber(viewportInput.y),
          width: Math.max(0, safeNumber(viewportInput.width)),
          height: Math.max(0, safeNumber(viewportInput.height))
        }
      },
      client: {
        name: clientName,
        version: clientVersion,
        featureId
      }
    };
    if (serializedSize(normalized) > MAX_CAPTURE_BYTES) {
      return failure('Normalized capture exceeds the 64 KiB limit.');
    }
    return success(normalized);
  }

  function normalizeLauncherConfig(input) {
    if (!isObject(input)) {
      return failure('Launcher configuration is missing.');
    }
    if (
      input.schema !== 'tdw.browser-tool-launcher' ||
      input.version !== 1 ||
      input.featureId !== 'ui-explorer-inspector'
    ) {
      return failure('Unsupported launcher configuration.');
    }
    const tdwOrigin = normalizeOrigin(input.tdwOrigin);
    if (!tdwOrigin) {
      return failure('TDW origin must be an HTTP(S) origin without a path.');
    }
    return success({
      schema: 'tdw.browser-tool-launcher',
      version: 1,
      featureId: 'ui-explorer-inspector',
      tdwOrigin
    });
  }

  function isProtocolMessage(data, type, nonce) {
    return (
      isObject(data) &&
      data.channel === CHANNEL &&
      data.protocolVersion === PROTOCOL_VERSION &&
      data.type === type &&
      data.nonce === nonce
    );
  }

  function createMessage(type, nonce, extra) {
    return Object.freeze({
      ...(isObject(extra) ? extra : {}),
      channel: CHANNEL,
      protocolVersion: PROTOCOL_VERSION,
      type,
      nonce
    });
  }

  function buildLauncherSource(config, protocolSource, runtimeSource) {
    const normalized = normalizeLauncherConfig(config);
    if (!normalized.ok) {
      throw new Error(normalized.error);
    }
    if (
      typeof protocolSource !== 'string' ||
      !protocolSource.trim() ||
      typeof runtimeSource !== 'string' ||
      !runtimeSource.trim()
    ) {
      throw new Error('Launcher sources are missing.');
    }
    if (protocolSource.length + runtimeSource.length > 300000) {
      throw new Error('Launcher sources are unexpectedly large.');
    }
    const serializedConfig = JSON.stringify(normalized.value);
    return [
      ';try{delete globalThis.__TDW_BROWSER_TOOL_CONFIG__;}catch(_error){}',
      `globalThis.__TDW_BROWSER_TOOL_CONFIG__=Object.freeze(${serializedConfig});`,
      protocolSource,
      runtimeSource,
      '//# sourceURL=tdw-inspector-lite.js'
    ].join('\n');
  }

  function buildRemoteLauncherSource(config) {
    const normalized = normalizeLauncherConfig(config);
    if (!normalized.ok) {
      throw new Error(normalized.error);
    }
    const loaderUrl = new URL('/tdw-inspector/loader.js', normalized.value.tdwOrigin);
    loaderUrl.searchParams.set('v', REMOTE_LOADER_VERSION);
    const serializedLoaderUrl = JSON.stringify(loaderUrl.toString());
    const serializedFeatureId = JSON.stringify(normalized.value.featureId);
    return [
      ';(function(d){',
      "var s=d.createElement('script');",
      `s.src=${serializedLoaderUrl};`,
      `s.dataset.tdwFeatureId=${serializedFeatureId};`,
      "s.referrerPolicy='no-referrer';",
      "s.onerror=function(){s.remove();alert('TDW Inspector Lite: skrypt zostal zablokowany. Uzyj DevTools Snippetu.');};",
      '(d.head||d.documentElement).appendChild(s);',
      '}(document));'
    ].join('');
  }

  function toBookmarkUrl(source) {
    if (typeof source !== 'string' || !source.trim()) {
      throw new Error('Launcher source is empty.');
    }
    return `javascript:${encodeURIComponent(source)}`;
  }

  const api = Object.freeze({
    CHANNEL,
    PROTOCOL_VERSION,
    CAPTURE_VERSION,
    MAX_CAPTURE_BYTES,
    normalizeText,
    isSafeIdentifier,
    normalizeOrigin,
    sanitizePathname,
    safeRouteHash,
    describeElement,
    captureElement,
    normalizeCapture,
    normalizeLauncherConfig,
    serializedSize,
    createNonce,
    createMessage,
    isReadyMessage: (data, nonce) =>
      isProtocolMessage(data, 'TDW_INSPECTOR_READY', nonce),
    isReceivedMessage: (data, nonce) =>
      isProtocolMessage(data, 'TDW_INSPECTOR_RECEIVED', nonce),
    isCaptureMessage: (data, nonce) =>
      isProtocolMessage(data, 'TDW_INSPECTOR_CAPTURE', nonce),
    buildLauncherSource,
    buildRemoteLauncherSource,
    toBookmarkUrl
  });

  try {
    delete global[GLOBAL_KEY];
    Object.defineProperty(global, GLOBAL_KEY, {
      configurable: true,
      enumerable: false,
      writable: false,
      value: api
    });
  } catch (error) {
    console.error('[TDW Inspector Lite] Protocol could not be initialized.', error);
  }
})(globalThis);
