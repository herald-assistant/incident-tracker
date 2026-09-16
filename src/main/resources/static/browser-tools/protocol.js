(function installTdwBrowserToolsProtocol(global) {
  'use strict';

  const GLOBAL_KEY = '__TDW_BROWSER_TOOLS_PROTOCOL_V3__';
  const PROTOCOL_VERSION = 3;
  const CAPTURE_VERSION = 3;
  const CAPTURE_SCHEMA = 'tdw.ux-inspector-capture';
  const REMOTE_LOADER_VERSION = '3.0.0';
  const MAX_CAPTURE_BYTES = 128 * 1024;
  const MAX_TEXT_LENGTH = 180;
  const MAX_ACCESSIBLE_NAME_LENGTH = 140;
  const MAX_ATTRIBUTE_LENGTH = 100;
  const MAX_CLASSES = 8;
  const MAX_ANCESTORS = 24;
  const MAX_QUERY_NAMES = 16;
  const MAX_SELECTOR_CANDIDATES = 8;
  const MAX_COMPONENT_BOUNDARIES = 8;
  const MAX_FORM_CONTROLS = 64;
  const MAX_EXCLUDED_FORM_CONTROLS = 32;
  const MAX_FORM_VALUE_LENGTH = 16 * 1024;
  const MAX_FORM_VALUE_CHARACTERS = 64 * 1024;
  const CAPTURE_PROFILES = new Set(['ELEMENT_CONTEXT', 'FORM_DIAGNOSTICS']);
  const STABLE_ATTRIBUTES = Object.freeze([
    'data-testid',
    'data-test',
    'data-cy',
    'name',
    'type',
    'formcontrolname',
    'aria-label',
    'aria-describedby'
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
    /(authorization|bearer|cookie|csrf|jwt|pass(word|wd)?|secret|session|token|one[-_ ]?time|otp|cvv|cvc)/i;
  const SENSITIVE_AUTOCOMPLETE_PATTERN =
    /(^|\s)(current-password|new-password|one-time-code|cc-number|cc-csc)(\s|$)/i;
  const JWT_VALUE_PATTERN = /^[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{16,}$/;
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

  function hasExactKeys(value, keys) {
    if (!isObject(value)) return false;
    const actual = Object.keys(value).sort();
    const expected = [...keys].sort();
    return actual.length === expected.length &&
      actual.every((key, index) => key === expected[index]);
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

  function selectorCandidates(element, attributes) {
    const tag = element.tagName.toLowerCase();
    const result = [];
    const push = (value) => {
      if (value && !result.includes(value) && result.length < MAX_SELECTOR_CANDIDATES) {
        result.push(value);
      }
    };
    if (attributes.id) push(`#${attributes.id}`);
    for (const name of ['data-testid', 'data-test', 'data-cy', 'formcontrolname', 'name']) {
      if (attributes[name]) {
        push(`${tag}[${name}="${attributes[name]}"]`);
      }
    }
    if (attributes['aria-label']) {
      push(`${tag}[aria-label="${attributes['aria-label']}"]`);
    }
    return result;
  }

  function componentBoundaryTags(ancestryElements) {
    return Array.from(
      new Set(
        ancestryElements
          .map((candidate) => candidate.tagName.toLowerCase())
          .filter((tag) => tag.includes('-'))
      )
    ).slice(0, MAX_COMPONENT_BOUNDARIES);
  }

  function labelFor(element) {
    if (!element.id || !isSafeIdentifier(element.id)) return null;
    if (
      (global.HTMLInputElement && element instanceof global.HTMLInputElement) ||
      (global.HTMLTextAreaElement && element instanceof global.HTMLTextAreaElement) ||
      (global.HTMLSelectElement && element instanceof global.HTMLSelectElement)
    ) {
      return element.labels?.length ? element.id : null;
    }
    return null;
  }

  function domFingerprint(element, ancestryElements) {
    const attributes = stableAttributes(element);
    return {
      stableAttributes: attributes,
      selectorCandidates: selectorCandidates(element, attributes),
      componentBoundaryTags: componentBoundaryTags(ancestryElements),
      labelFor: labelFor(element)
    };
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

  function validityState(control) {
    const validity = control?.validity;
    if (!validity) return null;
    return {
      valid: validity.valid === true,
      valueMissing: validity.valueMissing === true,
      typeMismatch: validity.typeMismatch === true,
      patternMismatch: validity.patternMismatch === true,
      tooShort: validity.tooShort === true,
      tooLong: validity.tooLong === true,
      rangeUnderflow: validity.rangeUnderflow === true,
      rangeOverflow: validity.rangeOverflow === true,
      stepMismatch: validity.stepMismatch === true,
      badInput: validity.badInput === true,
      customError: validity.customError === true,
      validationMessage: normalizeText(control.validationMessage, 300)
    };
  }

  function formControlKey(control) {
    return [
      control.getAttribute('name'),
      control.getAttribute('formcontrolname'),
      control.getAttribute('id'),
      control.getAttribute('autocomplete'),
      control.getAttribute('aria-label')
    ].filter(Boolean).join(' ');
  }

  function sensitiveFormControlReason(control) {
    const tag = control.tagName.toLowerCase();
    const type = (control.getAttribute('type') || tag).toLowerCase();
    if (type === 'password') return 'SENSITIVE_TYPE';
    if (type === 'hidden') return 'HIDDEN_CONTROL';
    if (type === 'file') return 'FILE_CONTROL';
    if (SENSITIVE_AUTOCOMPLETE_PATTERN.test(control.getAttribute('autocomplete') || '')) {
      return 'SENSITIVE_AUTOCOMPLETE';
    }
    if (SENSITIVE_PATTERN.test(formControlKey(control))) return 'SENSITIVE_NAME';
    const rawValue = typeof control.value === 'string' ? control.value.trim() : '';
    if (/^Bearer\s+/i.test(rawValue) || JWT_VALUE_PATTERN.test(rawValue)) return 'SENSITIVE_VALUE';
    return null;
  }

  function formControlIdentity(control) {
    const attributes = stableAttributes(control);
    return {
      tag: control.tagName.toLowerCase(),
      type: normalizeText(control.getAttribute('type'), 40),
      name: isSafeIdentifier(control.getAttribute('name')) ? control.getAttribute('name').trim() : null,
      formControlName: isSafeIdentifier(control.getAttribute('formcontrolname'))
        ? control.getAttribute('formcontrolname').trim()
        : null,
      accessibleName: accessibleName(control),
      stableAttributes: attributes
    };
  }

  function boundedFormValue(value, remainingCharacters) {
    const normalized = typeof value === 'string'
      ? value.normalize('NFC').replace(/\r\n/g, '\n')
      : '';
    const allowed = Math.max(0, Math.min(MAX_FORM_VALUE_LENGTH, remainingCharacters));
    if (normalized.length <= allowed) {
      return { value: normalized, truncated: false, used: normalized.length };
    }
    return {
      value: normalized.slice(0, allowed),
      truncated: true,
      used: allowed
    };
  }

  function selectedOptionValues(control, remainingCharacters) {
    if (!(global.HTMLSelectElement && control instanceof global.HTMLSelectElement)) {
      return { values: [], labels: [], truncated: false, used: 0 };
    }
    const values = [];
    const labels = [];
    let used = 0;
    let truncated = false;
    for (const option of Array.from(control.selectedOptions || [])) {
      const value = boundedFormValue(option.value, remainingCharacters - used);
      const label = boundedFormValue(option.textContent || '', remainingCharacters - used - value.used);
      values.push(value.value);
      labels.push(label.value);
      used += value.used + label.used;
      truncated ||= value.truncated || label.truncated;
      if (used >= remainingCharacters) break;
    }
    return { values, labels, truncated, used };
  }

  function describeFormControl(control, selectedTarget, remainingCharacters) {
    const identity = formControlIdentity(control);
    const options = selectedOptionValues(control, remainingCharacters);
    const remainingAfterOptions = Math.max(0, remainingCharacters - options.used);
    const isSelect = global.HTMLSelectElement && control instanceof global.HTMLSelectElement;
    const rawValue = typeof control.value === 'string' ? control.value : '';
    const bounded = isSelect
      ? { value: null, truncated: false, used: 0 }
      : boundedFormValue(rawValue, remainingAfterOptions);
    const state = elementState(control);
    return {
      control: {
        selectedTarget,
        ...identity,
        value: bounded.value,
        valueTruncated: bounded.truncated || options.truncated,
        checked: state.checked,
        selectedValues: options.values,
        selectedLabels: options.labels,
        disabled: state.disabled,
        readOnly: state.readOnly,
        required: state.required,
        validity: validityState(control)
      },
      used: options.used + bounded.used
    };
  }

  function describeSubmitter(control, selectedTarget) {
    const identity = formControlIdentity(control);
    return {
      selectedTarget,
      tag: identity.tag,
      type: identity.type,
      accessibleName: identity.accessibleName,
      stableAttributes: identity.stableAttributes,
      disabled: Boolean(control.disabled) || control.getAttribute('aria-disabled') === 'true'
    };
  }

  function isSubmitter(control) {
    const tag = control.tagName.toLowerCase();
    const type = (control.getAttribute('type') || '').toLowerCase();
    return tag === 'button' || (tag === 'input' && ['submit', 'button', 'reset', 'image'].includes(type));
  }

  function selectedFormControlOnly(element) {
    return isFormValueCarrier(element) ? [element] : [];
  }

  function captureFormSnapshot(element) {
    const form = 'form' in element && element.form
      ? element.form
      : element.closest('form');
    const allControls = form
      ? Array.from(form.elements || []).filter((candidate) => candidate instanceof global.Element)
      : selectedFormControlOnly(element);
    if (!form && allControls.length === 0) return null;

    const controls = [];
    const submitters = [];
    const excludedControls = [];
    let valueCharacters = 0;
    let omittedControlCount = 0;
    for (const control of allControls) {
      const selectedTarget = control === element || control.contains?.(element);
      if (isSubmitter(control)) {
        if (submitters.length < 16) submitters.push(describeSubmitter(control, selectedTarget));
        continue;
      }
      if (!isFormValueCarrier(control)) continue;
      const reason = sensitiveFormControlReason(control);
      if (reason) {
        if (excludedControls.length < MAX_EXCLUDED_FORM_CONTROLS) {
          const identity = formControlIdentity(control);
          excludedControls.push({
            tag: identity.tag,
            type: identity.type,
            name: identity.name,
            formControlName: identity.formControlName,
            reason
          });
        }
        continue;
      }
      if (controls.length >= MAX_FORM_CONTROLS) {
        omittedControlCount += 1;
        continue;
      }
      const described = describeFormControl(
        control,
        selectedTarget,
        Math.max(0, MAX_FORM_VALUE_CHARACTERS - valueCharacters)
      );
      valueCharacters += described.used;
      controls.push(described.control);
    }
    const formAttributes = form ? stableAttributes(form) : {};
    return {
      source: form ? 'NEAREST_FORM' : 'SELECTED_CONTROL_ONLY',
      stableAttributes: formAttributes,
      selectorCandidates: form ? selectorCandidates(form, formAttributes) : [],
      valid: form
        ? allControls
            .filter((control) => control && 'validity' in control && control.validity)
            .every((control) => control.validity.valid === true)
        : null,
      observedControlCount: allControls.length,
      emittedControlCount: controls.length,
      omittedControlCount,
      controls,
      submitters,
      excludedControls,
      valueCharacters,
      valuesTruncated: controls.some((control) => control.valueTruncated) || omittedControlCount > 0
    };
  }

  function describeElement(element) {
    return {
      tag: element.tagName.toLowerCase(),
      role: inferredRole(element),
      accessibleName: accessibleName(element),
      text: safeElementText(element),
      stableAttributes: stableAttributes(element),
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
      stableAttributes: descriptor.stableAttributes
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

  function redactionSignals(element, captureProfile, formSnapshot) {
    const signals = new Set();
    if (captureProfile === 'ELEMENT_CONTEXT' && (isFormValueCarrier(element) || element.closest('form'))) {
      signals.add('FORM_VALUES_NOT_REQUESTED');
    }
    if (formSnapshot?.excludedControls.length) signals.add('SENSITIVE_FORM_CONTROLS_EXCLUDED');
    const raw = String(element.textContent || '').slice(0, 1200);
    if (EMAIL_TEST_PATTERN.test(raw)) signals.add('EMAIL_REDACTED');
    if (UUID_TEST_PATTERN.test(raw) || /\b\d{6,}\b/.test(raw)) signals.add('IDENTIFIER_REDACTED');
    if (SENSITIVE_PATTERN.test(raw)) signals.add('SENSITIVE_TEXT_REMOVED');
    return Array.from(signals);
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
    const captureProfile = CAPTURE_PROFILES.has(options?.captureProfile)
      ? options.captureProfile
      : 'ELEMENT_CONTEXT';
    const formSnapshot = captureProfile === 'FORM_DIAGNOSTICS'
      ? captureFormSnapshot(element)
      : null;
    const emittedAncestors = selectAncestors(ancestry.elements.slice(1));
    const rect = element.getBoundingClientRect();
    const reachedDocumentRoot =
      ancestry.elements.at(-1)?.tagName.toLowerCase() ===
      global.document.documentElement.tagName.toLowerCase();
    const targetDescriptor = describeElement(element);
    const target = {
      tag: targetDescriptor.tag,
      role: targetDescriptor.role,
      accessibleName: targetDescriptor.accessibleName,
      text: targetDescriptor.text,
      domFingerprint: domFingerprint(element, ancestry.elements.slice(1)),
      state: targetDescriptor.state
    };
    target.bounds = {
      x: round(rect.x),
      y: round(rect.y),
      width: round(rect.width),
      height: round(rect.height)
    };
    const capture = {
      schema: CAPTURE_SCHEMA,
      version: CAPTURE_VERSION,
      captureId: createCaptureId(),
      capturedAt: options?.capturedAt || new Date().toISOString(),
      captureProfile,
      page: {
        origin: pageUrl.origin,
        path: safeRouteHash(pageUrl.hash) || sanitizePathname(pageUrl.pathname),
        title: normalizeText(global.document.title, 180),
        language: normalizeText(global.document.documentElement.lang, 20),
        queryParameterNames: Array.from(new Set(pageUrl.searchParams.keys()))
          .filter(isSafeIdentifier)
          .slice(0, MAX_QUERY_NAMES)
      },
      target,
      ancestors: emittedAncestors,
      formSnapshot,
      traversal: {
        observedDepth: ancestry.elements.length,
        emittedNodeCount: emittedAncestors.length + 1,
        omittedNodeCount: Math.max(0, ancestry.elements.length - emittedAncestors.length - 1),
        reachedDocumentRoot
      },
      signals: {
        shadowBoundaryCount: ancestry.shadowBoundaryCount,
        frame: global.top === global ? 'TOP_LEVEL' : 'SAME_ORIGIN_FRAME',
        redactions: redactionSignals(element, captureProfile, formSnapshot)
      },
      limits: [
        ...(emittedAncestors.length < ancestry.elements.length - 1 ? ['ANCESTORS_TRUNCATED'] : []),
        ...(!reachedDocumentRoot ? ['DOCUMENT_ROOT_NOT_REACHED'] : []),
        ...(captureProfile === 'FORM_DIAGNOSTICS' && !formSnapshot ? ['FORM_CONTEXT_NOT_FOUND'] : []),
        ...(formSnapshot?.valuesTruncated ? ['FORM_VALUES_TRUNCATED'] : [])
      ],
      client: {
        name: 'TDW UX Inspector',
        version: options?.clientVersion || '3.0.0',
        featureId: 'ux-inspector'
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
      stableAttributes: normalizeStableAttributes(value.stableAttributes)
    };
    if (includeState) {
      descriptor.state = normalizeState(value.state);
    }
    return descriptor;
  }

  function normalizeSelectorCandidate(value) {
    if (typeof value !== 'string' || value.length > 240) return null;
    const idSelector = /^#[A-Za-z][A-Za-z0-9_.:-]*$/;
    const attributeSelector = /^[a-z][a-z0-9-]{0,39}\[(data-testid|data-test|data-cy|formcontrolname|name|aria-label)="[A-Za-z][A-Za-z0-9_.:-]*"\]$/;
    return idSelector.test(value) || attributeSelector.test(value) ? value : null;
  }

  function normalizeDomFingerprint(value) {
    if (!isObject(value) || !hasExactKeys(value, [
      'stableAttributes', 'selectorCandidates', 'componentBoundaryTags', 'labelFor'
    ])) return null;
    if (!Array.isArray(value.selectorCandidates) || !Array.isArray(value.componentBoundaryTags)) return null;
    const candidates = value.selectorCandidates
      .map(normalizeSelectorCandidate)
      .filter(Boolean)
      .slice(0, MAX_SELECTOR_CANDIDATES);
    if (candidates.length !== Math.min(value.selectorCandidates.length, MAX_SELECTOR_CANDIDATES)) return null;
    const boundaries = value.componentBoundaryTags
      .filter((tag) => typeof tag === 'string' && /^[a-z][a-z0-9-]{0,39}$/.test(tag) && tag.includes('-'))
      .slice(0, MAX_COMPONENT_BOUNDARIES);
    if (boundaries.length !== Math.min(value.componentBoundaryTags.length, MAX_COMPONENT_BOUNDARIES)) return null;
    const normalizedLabelFor = value.labelFor === null
      ? null
      : (isSafeIdentifier(value.labelFor) ? value.labelFor.trim() : null);
    if (value.labelFor !== null && normalizedLabelFor === null) return null;
    return {
      stableAttributes: normalizeStableAttributes(value.stableAttributes),
      selectorCandidates: Array.from(new Set(candidates)),
      componentBoundaryTags: Array.from(new Set(boundaries)),
      labelFor: normalizedLabelFor
    };
  }

  function normalizeTargetDescriptor(value) {
    if (!isObject(value)) return null;
    const tag = boundedString(value.tag, 40, false);
    const fingerprint = normalizeDomFingerprint(value.domFingerprint);
    if (!tag || !/^[a-z][a-z0-9-]*$/.test(tag.toLowerCase()) || !fingerprint) return null;
    return {
      tag: tag.toLowerCase(),
      role: normalizeText(value.role, 40),
      accessibleName: normalizeText(value.accessibleName, MAX_ACCESSIBLE_NAME_LENGTH),
      text: normalizeText(value.text, MAX_TEXT_LENGTH),
      domFingerprint: fingerprint,
      state: normalizeState(value.state)
    };
  }

  function normalizeValidity(value) {
    const keys = [
      'valid', 'valueMissing', 'typeMismatch', 'patternMismatch', 'tooShort', 'tooLong',
      'rangeUnderflow', 'rangeOverflow', 'stepMismatch', 'badInput', 'customError',
      'validationMessage'
    ];
    if (value === null) return null;
    if (!isObject(value) || !hasExactKeys(value, keys)) return undefined;
    if (keys.slice(0, -1).some((key) => typeof value[key] !== 'boolean')) return undefined;
    return {
      valid: value.valid,
      valueMissing: value.valueMissing,
      typeMismatch: value.typeMismatch,
      patternMismatch: value.patternMismatch,
      tooShort: value.tooShort,
      tooLong: value.tooLong,
      rangeUnderflow: value.rangeUnderflow,
      rangeOverflow: value.rangeOverflow,
      stepMismatch: value.stepMismatch,
      badInput: value.badInput,
      customError: value.customError,
      validationMessage: normalizeText(value.validationMessage, 300)
    };
  }

  function normalizeNullableIdentifier(value) {
    if (value === null) return null;
    return isSafeIdentifier(value) ? value.trim() : undefined;
  }

  function normalizeRawFormValue(value, remainingCharacters) {
    if (value === null) return { value: null, truncated: false, used: 0 };
    if (typeof value !== 'string') return null;
    const bounded = boundedFormValue(value, remainingCharacters);
    return { value: bounded.value, truncated: bounded.truncated, used: bounded.used };
  }

  function normalizeStringValues(values, remainingCharacters) {
    if (!Array.isArray(values)) return null;
    const result = [];
    let used = 0;
    let truncated = false;
    for (const value of values.slice(0, 64)) {
      if (typeof value !== 'string') return null;
      const bounded = boundedFormValue(value, Math.max(0, remainingCharacters - used));
      result.push(bounded.value);
      used += bounded.used;
      truncated ||= bounded.truncated;
    }
    return { values: result, used, truncated };
  }

  function normalizeFormControl(value, remainingCharacters) {
    const keys = [
      'selectedTarget', 'tag', 'type', 'name', 'formControlName', 'accessibleName',
      'stableAttributes', 'value', 'valueTruncated', 'checked', 'selectedValues',
      'selectedLabels', 'disabled', 'readOnly', 'required', 'validity'
    ];
    if (!isObject(value) || !hasExactKeys(value, keys)) return null;
    const tag = boundedString(value.tag, 40, false);
    const type = value.type === null ? null : normalizeText(value.type, 40);
    const name = normalizeNullableIdentifier(value.name);
    const formControlName = normalizeNullableIdentifier(value.formControlName);
    const validity = normalizeValidity(value.validity);
    if (!tag || !/^[a-z][a-z0-9-]*$/.test(tag) || name === undefined ||
        formControlName === undefined || validity === undefined) return null;
    if (['password', 'hidden', 'file'].includes(String(type || '').toLowerCase()) ||
        SENSITIVE_PATTERN.test([type, name, formControlName].filter(Boolean).join(' '))) return null;
    const selectedValues = normalizeStringValues(value.selectedValues, remainingCharacters);
    if (!selectedValues) return null;
    const selectedLabels = normalizeStringValues(
      value.selectedLabels,
      Math.max(0, remainingCharacters - selectedValues.used)
    );
    if (!selectedLabels) return null;
    const raw = normalizeRawFormValue(
      value.value,
      Math.max(0, remainingCharacters - selectedValues.used - selectedLabels.used)
    );
    if (!raw) return null;
    if (raw.value && (/^Bearer\s+/i.test(raw.value.trim()) || JWT_VALUE_PATTERN.test(raw.value.trim()))) return null;
    return {
      control: {
        selectedTarget: value.selectedTarget === true,
        tag,
        type,
        name,
        formControlName,
        accessibleName: normalizeText(value.accessibleName, MAX_ACCESSIBLE_NAME_LENGTH),
        stableAttributes: normalizeStableAttributes(value.stableAttributes),
        value: raw.value,
        valueTruncated: value.valueTruncated === true || raw.truncated || selectedValues.truncated || selectedLabels.truncated,
        checked: value.checked === true || value.checked === false ? value.checked : null,
        selectedValues: selectedValues.values,
        selectedLabels: selectedLabels.values,
        disabled: value.disabled === true,
        readOnly: value.readOnly === true,
        required: value.required === true,
        validity
      },
      used: raw.used + selectedValues.used + selectedLabels.used
    };
  }

  function normalizeExcludedControl(value) {
    if (!isObject(value) || !hasExactKeys(value, ['tag', 'type', 'name', 'formControlName', 'reason'])) return null;
    const tag = boundedString(value.tag, 40, false);
    const type = value.type === null ? null : normalizeText(value.type, 40);
    const name = normalizeNullableIdentifier(value.name);
    const formControlName = normalizeNullableIdentifier(value.formControlName);
    const reasons = new Set(['SENSITIVE_TYPE', 'HIDDEN_CONTROL', 'FILE_CONTROL', 'SENSITIVE_AUTOCOMPLETE', 'SENSITIVE_NAME', 'SENSITIVE_VALUE']);
    if (!tag || name === undefined || formControlName === undefined || !reasons.has(value.reason)) return null;
    return { tag, type, name, formControlName, reason: value.reason };
  }

  function normalizeSubmitter(value) {
    if (!isObject(value) || !hasExactKeys(value, [
      'selectedTarget', 'tag', 'type', 'accessibleName', 'stableAttributes', 'disabled'
    ])) return null;
    const tag = boundedString(value.tag, 40, false);
    if (!tag) return null;
    return {
      selectedTarget: value.selectedTarget === true,
      tag,
      type: value.type === null ? null : normalizeText(value.type, 40),
      accessibleName: normalizeText(value.accessibleName, MAX_ACCESSIBLE_NAME_LENGTH),
      stableAttributes: normalizeStableAttributes(value.stableAttributes),
      disabled: value.disabled === true
    };
  }

  function normalizeFormSnapshot(value) {
    const keys = [
      'source', 'stableAttributes', 'selectorCandidates', 'valid', 'observedControlCount',
      'emittedControlCount', 'omittedControlCount', 'controls', 'submitters',
      'excludedControls', 'valueCharacters', 'valuesTruncated'
    ];
    if (!isObject(value) || !hasExactKeys(value, keys)) return null;
    if (!['NEAREST_FORM', 'SELECTED_CONTROL_ONLY'].includes(value.source) ||
        !Array.isArray(value.selectorCandidates) || !Array.isArray(value.controls) ||
        !Array.isArray(value.submitters) || !Array.isArray(value.excludedControls)) return null;
    const selectors = value.selectorCandidates.map(normalizeSelectorCandidate).filter(Boolean).slice(0, MAX_SELECTOR_CANDIDATES);
    if (selectors.length !== Math.min(value.selectorCandidates.length, MAX_SELECTOR_CANDIDATES)) return null;
    const controls = [];
    let used = 0;
    for (const candidate of value.controls.slice(0, MAX_FORM_CONTROLS)) {
      const normalized = normalizeFormControl(candidate, Math.max(0, MAX_FORM_VALUE_CHARACTERS - used));
      if (!normalized) return null;
      controls.push(normalized.control);
      used += normalized.used;
    }
    const submitters = value.submitters.slice(0, 16).map(normalizeSubmitter);
    const excludedControls = value.excludedControls.slice(0, MAX_EXCLUDED_FORM_CONTROLS).map(normalizeExcludedControl);
    if (submitters.some((item) => !item) || excludedControls.some((item) => !item)) return null;
    const observed = safeInteger(value.observedControlCount, controls.length + excludedControls.length, 4096);
    const omitted = safeInteger(value.omittedControlCount, 0, 4096);
    return {
      source: value.source,
      stableAttributes: normalizeStableAttributes(value.stableAttributes),
      selectorCandidates: Array.from(new Set(selectors)),
      valid: value.valid === true || value.valid === false ? value.valid : null,
      observedControlCount: observed,
      emittedControlCount: controls.length,
      omittedControlCount: omitted,
      controls,
      submitters,
      excludedControls,
      valueCharacters: used,
      valuesTruncated: value.valuesTruncated === true || controls.some((control) => control.valueTruncated) || omitted > 0
    };
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
      return failure('Capture exceeds the 128 KiB limit.');
    }
    if (!hasExactKeys(input, [
      'schema', 'version', 'captureId', 'capturedAt', 'captureProfile', 'page', 'target',
      'ancestors', 'formSnapshot', 'traversal', 'signals', 'limits', 'client'
    ])) {
      return failure('Capture fields do not match the v3 contract.');
    }
    if (input.schema !== CAPTURE_SCHEMA || input.version !== CAPTURE_VERSION) {
      return failure('Unsupported capture schema or version.');
    }
    const captureId = boundedString(input.captureId, 96, false);
    if (!captureId || !SAFE_CAPTURE_ID_PATTERN.test(captureId)) {
      return failure('Invalid capture id.');
    }
    const capturedAt = boundedString(input.capturedAt, 64, false);
    if (!capturedAt || Number.isNaN(Date.parse(capturedAt))) {
      return failure('Invalid capture timestamp.');
    }
    if (
      !hasExactKeys(input.page, ['origin', 'path', 'title', 'language', 'queryParameterNames']) ||
      !hasExactKeys(input.target, ['tag', 'role', 'accessibleName', 'text', 'domFingerprint', 'state', 'bounds']) ||
      !Array.isArray(input.ancestors) ||
      !hasExactKeys(input.traversal, ['observedDepth', 'emittedNodeCount', 'omittedNodeCount', 'reachedDocumentRoot']) ||
      !hasExactKeys(input.signals, ['shadowBoundaryCount', 'frame', 'redactions']) ||
      !Array.isArray(input.limits) ||
      !hasExactKeys(input.client, ['name', 'version', 'featureId'])
    ) {
      return failure('Capture sections are missing.');
    }
    if (!CAPTURE_PROFILES.has(input.captureProfile)) {
      return failure('Capture profile is invalid.');
    }
    const origin = normalizeOrigin(input.page.origin);
    if (!origin) {
      return failure('Capture page origin is invalid.');
    }
    const target = normalizeTargetDescriptor(input.target);
    if (!target) {
      return failure('Capture target is invalid.');
    }
    const boundsInput = input.target.bounds;
    if (!hasExactKeys(boundsInput, ['x', 'y', 'width', 'height'])) {
      return failure('Capture target bounds are invalid.');
    }
    target.bounds = {
      x: safeNumber(boundsInput.x),
      y: safeNumber(boundsInput.y),
      width: Math.max(0, safeNumber(boundsInput.width)),
      height: Math.max(0, safeNumber(boundsInput.height))
    };
    const ancestorsInput = input.ancestors.slice(0, MAX_ANCESTORS);
    const ancestors = [];
    for (const candidate of ancestorsInput) {
      if (!hasExactKeys(candidate, ['depth', 'tag', 'role', 'accessibleName', 'text', 'stableAttributes'])) {
        return failure('Capture ancestor fields are invalid.');
      }
      const descriptor = normalizeDescriptor(candidate, false);
      if (!descriptor) {
        return failure('Capture ancestor is invalid.');
      }
      ancestors.push({
        depth: safeInteger(candidate.depth, ancestors.length + 1, 1000),
        ...descriptor
      });
    }
    const traversalInput = input.traversal;
    const queryParameterNames = Array.isArray(input.page.queryParameterNames)
      ? Array.from(new Set(input.page.queryParameterNames.filter(isSafeIdentifier))).slice(
          0,
          MAX_QUERY_NAMES
        )
      : [];
    const frame = ['TOP_LEVEL', 'SAME_ORIGIN_FRAME'].includes(input.signals.frame)
      ? input.signals.frame
      : 'TOP_LEVEL';
    const clientName = boundedString(input.client.name, 80, false);
    const clientVersion = boundedString(input.client.version, 32, false);
    const featureId = boundedString(input.client.featureId, 80, false);
    if (
      clientName !== 'TDW UX Inspector' ||
      !clientVersion ||
      featureId !== 'ux-inspector'
    ) {
      return failure('Capture client metadata is invalid.');
    }
    const path = boundedString(input.page.path, 500, false);
    if (!path || !path.startsWith('/') || path.includes('?') || path.includes('#')) {
      return failure('Capture page path is invalid.');
    }
    const redactions = Array.isArray(input.signals.redactions)
      ? Array.from(new Set(input.signals.redactions.filter(isSafeCode))).slice(0, 24)
      : [];
    const limits = Array.from(new Set(input.limits.filter(isSafeCode))).slice(0, 32);
    const formSnapshot = input.formSnapshot === null ? null : normalizeFormSnapshot(input.formSnapshot);
    if (input.formSnapshot !== null && !formSnapshot) {
      return failure('Capture form snapshot is invalid.');
    }
    if (input.captureProfile === 'ELEMENT_CONTEXT' && formSnapshot !== null) {
      return failure('Element context capture must not contain a form snapshot.');
    }
    const normalized = {
      schema: CAPTURE_SCHEMA,
      version: CAPTURE_VERSION,
      captureId,
      capturedAt: new Date(capturedAt).toISOString(),
      captureProfile: input.captureProfile,
      page: {
        origin,
        path: sanitizePathname(path),
        title: normalizeText(input.page.title, 180),
        language: normalizeText(input.page.language, 20),
        queryParameterNames
      },
      target,
      ancestors,
      formSnapshot,
      traversal: {
        observedDepth: safeInteger(traversalInput.observedDepth, ancestors.length + 1, 4096),
        emittedNodeCount: safeInteger(traversalInput.emittedNodeCount, ancestors.length + 1, MAX_ANCESTORS + 1),
        omittedNodeCount: safeInteger(traversalInput.omittedNodeCount, 0, 4096),
        reachedDocumentRoot: traversalInput.reachedDocumentRoot === true
      },
      signals: {
        shadowBoundaryCount: safeInteger(input.signals.shadowBoundaryCount, 0, 64),
        frame,
        redactions
      },
      limits,
      client: {
        name: clientName,
        version: clientVersion,
        featureId
      }
    };
    if (serializedSize(normalized) > MAX_CAPTURE_BYTES) {
      return failure('Normalized capture exceeds the 128 KiB limit.');
    }
    return success(normalized);
  }

  function isSafeCode(value) {
    return typeof value === 'string' && /^[A-Z][A-Z0-9_]{1,79}$/.test(value);
  }

  function normalizeLauncherConfig(input) {
    if (!isObject(input)) {
      return failure('Launcher configuration is missing.');
    }
    if (
      !hasExactKeys(input, ['schema', 'version', 'featureId', 'tdwOrigin']) ||
      input.schema !== 'tdw.browser-tool-launcher' ||
      input.version !== 3 ||
      input.featureId !== 'ux-inspector'
    ) {
      return failure('Unsupported launcher configuration.');
    }
    const tdwOrigin = normalizeOrigin(input.tdwOrigin);
    if (!tdwOrigin) {
      return failure('TDW origin must be an HTTP(S) origin without a path.');
    }
    return success({
      schema: 'tdw.browser-tool-launcher',
      version: 3,
      featureId: 'ux-inspector',
      tdwOrigin
    });
  }

  function isProtocolMessage(data, type, nonce) {
    if (!isObject(data) || data.protocolVersion !== PROTOCOL_VERSION || data.type !== type || data.nonce !== nonce) {
      return false;
    }
    const keys = type === 'TDW_UX_INSPECTOR_READY'
      ? ['type', 'protocolVersion', 'nonce']
      : type === 'TDW_UX_INSPECTOR_RECEIVED'
        ? ['type', 'protocolVersion', 'nonce', 'captureId']
        : type === 'TDW_UX_INSPECTOR_ERROR'
          ? ['type', 'protocolVersion', 'nonce', 'code']
          : ['type', 'protocolVersion', 'nonce', 'captureId', 'capture'];
    return hasExactKeys(data, keys);
  }

  function createMessage(type, nonce, extra) {
    return Object.freeze({
      ...(isObject(extra) ? extra : {}),
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
      '//# sourceURL=tdw-browser-tools.js'
    ].join('\n');
  }

  function buildRemoteLauncherSource(config) {
    const normalized = normalizeLauncherConfig(config);
    if (!normalized.ok) {
      throw new Error(normalized.error);
    }
    const loaderUrl = new URL('/browser-tools/loader.js', normalized.value.tdwOrigin);
    loaderUrl.searchParams.set('v', REMOTE_LOADER_VERSION);
    const serializedLoaderUrl = JSON.stringify(loaderUrl.toString());
    const serializedFeatureId = JSON.stringify(normalized.value.featureId);
    return [
      ';(function(d){',
      "var s=d.createElement('script');",
      `s.src=${serializedLoaderUrl};`,
      `s.dataset.tdwFeatureId=${serializedFeatureId};`,
      "s.referrerPolicy='no-referrer';",
      "s.onerror=function(){s.remove();alert('TDW Browser Tools: skrypt zostal zablokowany. Uzyj DevTools Snippetu.');};",
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
    PROTOCOL_VERSION,
    CAPTURE_VERSION,
    CAPTURE_SCHEMA,
    CAPTURE_PROFILES: Object.freeze(['ELEMENT_CONTEXT', 'FORM_DIAGNOSTICS']),
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
      isProtocolMessage(data, 'TDW_UX_INSPECTOR_READY', nonce),
    isReceivedMessage: (data, nonce) =>
      isProtocolMessage(data, 'TDW_UX_INSPECTOR_RECEIVED', nonce),
    isCaptureMessage: (data, nonce) =>
      isProtocolMessage(data, 'TDW_UX_INSPECTOR_CAPTURE', nonce),
    isErrorMessage: (data, nonce) =>
      isProtocolMessage(data, 'TDW_UX_INSPECTOR_ERROR', nonce),
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
    console.error('[TDW Browser Tools] Protocol could not be initialized.', error);
  }
})(globalThis);
