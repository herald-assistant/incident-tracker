(function installTdwBrowserToolsProtocol(global) {
  'use strict';

  const GLOBAL_KEY = '__TDW_BROWSER_TOOLS_PROTOCOL_V1__';
  const PROTOCOL_VERSION = 1;
  const CAPTURE_VERSION = 3;
  const CAPTURE_SCHEMA = 'tdw.ux-inspector-capture';
  const PAGE_CONTEXT_SCHEMA = 'tdw.ui-explorer-page-context';
  const PAGE_CONTEXT_VERSION = 1;
  const MAX_PAGE_CONTEXT_BYTES = 4096;
  const MAX_TEXT_LENGTH = 180;
  const MAX_ACCESSIBLE_NAME_LENGTH = 140;
  const MAX_ATTRIBUTE_LENGTH = 100;
  const MAX_CLASSES = 8;
  const MAX_QUERY_NAMES = 16;
  const MAX_SELECTOR_CANDIDATES = 8;
  const MAX_COMPONENT_BOUNDARIES = 8;
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
    for (const className of safeClasses(element).slice(0, 2)) {
      push(`${tag}[class~="${className}"]`);
    }
    return result;
  }

  function componentBoundaryTags(ancestryElements, maxTags = MAX_COMPONENT_BOUNDARIES) {
    const tags = Array.from(
      new Set(
        ancestryElements
          .map((candidate) => candidate.tagName.toLowerCase())
          .filter((tag) => tag.includes('-'))
      )
    );
    return maxTags === null ? tags : tags.slice(0, maxTags);
  }

  function routedComponentBoundaryTags() {
    const candidates = Array.from(global.document.querySelectorAll('router-outlet'))
      .filter((outlet) => !outlet.hasAttribute('name') || outlet.getAttribute('name') === 'primary')
      .map((outlet) => outlet.nextElementSibling)
      .filter((component) => component && /^[a-z][a-z0-9-]*-[a-z0-9-]+$/.test(component.tagName.toLowerCase()))
      .map((component) => {
        let depth = 0;
        for (let parent = component.parentElement; parent; parent = parent.parentElement) depth++;
        return { component, depth };
      })
      .sort((left, right) => right.depth - left.depth);
    if (!candidates.length || candidates[1]?.depth === candidates[0].depth) return [];
    const ancestry = [];
    for (let node = candidates[0].component; node; node = node.parentElement) ancestry.push(node);
    return componentBoundaryTags(ancestry);
  }

  function capturePageContext() {
    const pageUrl = new URL(global.location.href);
    if (!['http:', 'https:'].includes(pageUrl.protocol) || global.top !== global) {
      throw new Error('UI Explorer supports only top-level HTTP(S) pages.');
    }
    const result = normalizePageContext({
      schema: PAGE_CONTEXT_SCHEMA,
      version: PAGE_CONTEXT_VERSION,
      contextId: createNonce(),
      page: {
        origin: pageUrl.origin,
        path: safeRouteHash(pageUrl.hash) || sanitizePathname(pageUrl.pathname),
        componentBoundaryTags: routedComponentBoundaryTags()
      }
    });
    if (!result.ok) throw new Error(result.error);
    return result.value;
  }

  function normalizePageContext(input) {
    if (!hasExactKeys(input, ['schema', 'version', 'contextId', 'page']) ||
        input.schema !== PAGE_CONTEXT_SCHEMA || input.version !== PAGE_CONTEXT_VERSION ||
        serializedSize(input) > MAX_PAGE_CONTEXT_BYTES ||
        !hasExactKeys(input.page, ['origin', 'path', 'componentBoundaryTags'])) {
      return failure('UI Explorer page context is invalid.');
    }
    const origin = normalizeOrigin(input.page.origin);
    const path = input.page.path;
    const tags = input.page.componentBoundaryTags;
    if (!origin || typeof input.contextId !== 'string' ||
        !SAFE_CAPTURE_ID_PATTERN.test(input.contextId) ||
        typeof path !== 'string' || !path.startsWith('/') || path.length > 500 ||
        path.includes('?') || path.includes('#') || sanitizePathname(path) !== path ||
        !Array.isArray(tags) || tags.length > MAX_COMPONENT_BOUNDARIES ||
        tags.some((tag) => typeof tag !== 'string' ||
          !/^[a-z][a-z0-9-]*-[a-z0-9-]+$/.test(tag)) ||
        new Set(tags).size !== tags.length) {
      return failure('UI Explorer page context fields are invalid.');
    }
    return success({
      schema: PAGE_CONTEXT_SCHEMA,
      version: PAGE_CONTEXT_VERSION,
      contextId: input.contextId,
      page: { origin, path, componentBoundaryTags: [...tags] }
    });
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
      componentBoundaryTags: componentBoundaryTags(ancestryElements, null),
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
    if (type === 'file') return 'FILE_CONTROL';
    if (SENSITIVE_AUTOCOMPLETE_PATTERN.test(control.getAttribute('autocomplete') || '')) {
      return 'SENSITIVE_AUTOCOMPLETE';
    }
    if (SENSITIVE_PATTERN.test(formControlKey(control))) return 'SENSITIVE_NAME';
    const rawValue = typeof control.value === 'string' ? control.value.trim() : '';
    if (/^Bearer\s+/i.test(rawValue) || JWT_VALUE_PATTERN.test(rawValue)) return 'SENSITIVE_VALUE';
    return null;
  }

  // Odczyt jest obserwacją całej widocznej strony w chwili capture.
  function captureVisibleFormFields() {
    const scope = global.document;
    const customValueSelector = 'san-select span.san-select__value';
    const nativeSelector = 'input, textarea, select';
    const errorSelector = [
      'mat-error', 'san-error', '.invalid-feedback', '.error-message',
      '.validation-message', '[class*="__error"]', '[class*="-error-message"]',
      '[data-validation-error]'
    ].join(',');
    const labelSelector = [
      'label', '[class*="label" i]', '[class*="text-top" i]',
      '[class*="checkbox__text" i]', '[class*="radio__text" i]'
    ].join(',');
    const labelNoiseSelector = [
      'script', 'style', 'template', 'input', 'textarea', 'select', 'button',
      'svg', 'canvas', 'san-icon', 'san-tooltip', '[role="tooltip"]',
      '[role="listbox"]', '[role="option"]', '[aria-hidden="true"]',
      '[hidden]', '[class*="suffix" i]', '[class*="prefix" i]',
      '[class*="hint" i]', '[class*="__value" i]',
      '[class*="selected-value" i]', '[contenteditable="true"]', errorSelector
    ].join(',');
    const otherControlSelector = [
      'input:not([type="hidden"]):not([type="button"]):not([type="submit"]):not([type="reset"]):not([type="image"])',
      'textarea', 'select', customValueSelector, '[role="combobox"]',
      '[role="textbox"]', '[role="checkbox"]', '[role="radio"]',
      '[contenteditable="true"]'
    ].join(',');
    const normalize = (value) => String(value || '').replace(/\s+/g, ' ').trim();
    const unique = (values) => [...new Set(values.filter(Boolean))];
    const selectIncludingSelf = (container, selector) => [
      ...(container.matches?.(selector) ? [container] : []),
      ...container.querySelectorAll(selector)
    ];
    const isDisplayed = (node) => {
      for (let current = node; current; current = current.parentElement) {
        const style = global.getComputedStyle(current);
        if (current.hidden || current.getAttribute('aria-hidden') === 'true' ||
            style.display === 'none' || ['hidden', 'collapse'].includes(style.visibility)) return false;
      }
      return true;
    };
    const contexts = (element, maxDepth) => {
      const result = [element];
      let parent = element.parentElement;
      for (let depth = 0; parent && depth < maxDepth; depth++, parent = parent.parentElement) {
        if (parent.matches('body, html')) break;
        const hasOtherControl = [...parent.querySelectorAll(otherControlSelector)].some((control) =>
          control !== element && !control.contains(element) && !element.contains(control));
        if (hasOtherControl) break;
        result.push(parent);
      }
      return result;
    };
    const referencedNodes = (element, attribute) => normalize(element.getAttribute(attribute))
      .split(/\s+/).filter(Boolean)
      .map((id) => element.getRootNode().getElementById?.(id)).filter(Boolean);
    const nodeText = (node, noiseSelector) => {
      if (!node) return '';
      const clone = node.cloneNode(true);
      clone.querySelectorAll(noiseSelector).forEach((child) => child.remove());
      clone.querySelectorAll('br').forEach((child) => child.replaceWith(' '));
      return normalize(clone.textContent);
    };
    const readLabel = (element) => {
      const labelledBy = referencedNodes(element, 'aria-labelledby')
        .map((node) => nodeText(node, labelNoiseSelector)).filter(Boolean).join(' ');
      if (labelledBy) return labelledBy;
      const ariaLabel = normalize(element.getAttribute('aria-label'));
      if (ariaLabel) return ariaLabel;
      const nativeLabel = [...(element.labels || [])]
        .map((node) => nodeText(node, labelNoiseSelector)).filter(Boolean).join(' | ');
      if (nativeLabel) return nativeLabel;
      let fallback = '';
      for (const parent of contexts(element, 6).slice(1)) {
        for (const candidate of selectIncludingSelf(parent, labelSelector)) {
          if (candidate.closest(labelNoiseSelector)) continue;
          const text = nodeText(candidate, labelNoiseSelector);
          if (text && text.length <= 240) return text;
        }
        const text = nodeText(parent, labelNoiseSelector);
        if (!fallback && text && text.length <= 240) fallback = text;
      }
      return fallback;
    };
    const messageText = (node) => nodeText(node, 'input, textarea, select, button, script, style, svg, san-icon');
    const readValidation = (element) => {
      const errors = [];
      const descriptions = [];
      const visited = new Set();
      let invalidSignal = false;
      let validSignal = false;
      const addError = (node) => {
        if (!visited.has(node)) { visited.add(node); errors.push(messageText(node)); }
      };
      for (const context of contexts(element, 8)) {
        const aria = normalize(context.getAttribute('aria-invalid')).toLowerCase();
        const ariaInvalid = aria !== '' && aria !== 'false';
        invalidSignal ||= ariaInvalid || context.classList.contains('ng-invalid');
        validSignal ||= aria === 'false' || context.classList.contains('ng-valid');
        if (ariaInvalid) referencedNodes(context, 'aria-errormessage').forEach(addError);
        for (const node of referencedNodes(context, 'aria-describedby')) {
          const matches = selectIncludingSelf(node, errorSelector).filter(isDisplayed);
          if (matches.length) matches.forEach(addError);
          else descriptions.push(messageText(node));
        }
        context.querySelectorAll(errorSelector).forEach((node) => { if (isDisplayed(node)) addError(node); });
      }
      const nativeInvalid = element.willValidate && element.validity ? !element.validity.valid : null;
      const nativeValidationMessage = nativeInvalid ? normalize(element.validationMessage) : '';
      if (nativeValidationMessage) errors.push(nativeValidationMessage);
      const messages = unique(errors);
      return {
        invalid: invalidSignal || nativeInvalid === true || messages.length ? true : validSignal ? false : null,
        errors: messages,
        descriptions: unique(descriptions).filter((text) => !messages.includes(text)),
        nativeInvalid,
        nativeValidationMessage
      };
    };
    const controls = [...scope.querySelectorAll(`${nativeSelector}, ${customValueSelector}`)]
      .filter((element) => isDisplayed(element) &&
        (element.tagName !== 'INPUT' || !['hidden', 'button', 'submit', 'reset', 'image', 'password', 'file'].includes(element.type)) &&
        !sensitiveFormControlReason(element));
    return controls.map((element) => {
      const native = element.matches(nativeSelector);
      const label = readLabel(element);
      const host = element.closest('[data-testid], [testid]');
      let value;
      let display;
      let source = 'dom';
      let checked = null;
      let indeterminate = null;
      if (!native) {
        value = normalize(element.textContent);
        display = value;
        source = 'display-text';
      } else if (element.tagName === 'SELECT') {
        const selected = [...element.selectedOptions];
        value = element.multiple ? selected.map((option) => option.value) : element.value;
        display = selected.map((option) => option.label).join(', ');
      } else if (element.type === 'checkbox' || element.type === 'radio') {
        value = element.value;
        checked = element.checked;
        indeterminate = element.type === 'checkbox' ? element.indeterminate : null;
        display = element.type === 'radio' ? (checked ? '◉' : '○') :
          (indeterminate ? '▣' : checked ? '☑' : '☐');
      } else {
        value = element.value;
        display = element.value;
      }
      if (/^Bearer\s+/i.test(String(value).trim()) || JWT_VALUE_PATTERN.test(String(value).trim())) {
        return null;
      }
      return {
        tag: element.tagName.toLowerCase(), type: native ? (element.type || '') : 'custom-select',
        name: element.getAttribute('name') || '', id: element.id || '',
        testId: host?.getAttribute('data-testid') || host?.getAttribute('testid') || '',
        label, disabled: native ? element.matches(':disabled') : null,
        value, display, source, checked, indeterminate, ...readValidation(element)
      };
    }).filter(Boolean);
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

  function describeAncestor(element, depth) {
    return {
      depth,
      tag: element.tagName.toLowerCase(),
      role: inferredRole(element),
      accessibleName: accessibleName(element),
      text: depth <= 8 ? safeElementText(element) : null,
      stableAttributes: stableAttributes(element)
    };
  }

  function selectAncestors(elements) {
    return elements.map((candidate, index) => describeAncestor(candidate, index + 1));
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

  function createNonce() {
    return createRandomToken('n_');
  }

  function round(value) {
    return Math.round(value * 100) / 100;
  }

  function redactionSignals(element, captureProfile) {
    const signals = new Set();
    if (captureProfile === 'ELEMENT_CONTEXT' && (isFormValueCarrier(element) || element.closest('form'))) {
      signals.add('FORM_VALUES_NOT_REQUESTED');
    }
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
      domFingerprint: domFingerprint(element, ancestry.elements),
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
      traversal: {
        observedDepth: ancestry.elements.length,
        emittedNodeCount: emittedAncestors.length + 1,
        omittedNodeCount: 0,
        reachedDocumentRoot
      },
      signals: {
        shadowBoundaryCount: ancestry.shadowBoundaryCount,
        frame: global.top === global ? 'TOP_LEVEL' : 'SAME_ORIGIN_FRAME',
        redactions: redactionSignals(element, captureProfile)
      },
      limits: [
        ...(!reachedDocumentRoot ? ['DOCUMENT_ROOT_NOT_REACHED'] : [])
      ],
      client: {
        name: 'TDW UX Inspector',
        version: options?.clientVersion || '1.0.0',
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
    const classTokenSelector = /^[a-z][a-z0-9-]{0,39}\[class~="[A-Za-z][A-Za-z0-9_.:-]*"\]$/;
    return idSelector.test(value) || attributeSelector.test(value) || classTokenSelector.test(value)
      ? value
      : null;
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
      .filter((tag) => typeof tag === 'string' && /^[a-z][a-z0-9-]{0,39}$/.test(tag) && tag.includes('-'));
    if (boundaries.length !== value.componentBoundaryTags.length ||
        new Set(boundaries).size !== boundaries.length) return null;
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
    if (!hasExactKeys(input, [
      'schema', 'version', 'capturedAt', 'captureProfile', 'page', 'target',
      'ancestors', 'traversal', 'signals', 'limits', 'client'
    ])) {
      return failure('Capture fields do not match the v3 contract.');
    }
    if (input.schema !== CAPTURE_SCHEMA || input.version !== CAPTURE_VERSION) {
      return failure('Unsupported capture schema or version.');
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
    const ancestorsInput = input.ancestors;
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
    const normalized = {
      schema: CAPTURE_SCHEMA,
      version: CAPTURE_VERSION,
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
      traversal: {
        observedDepth: safeInteger(traversalInput.observedDepth, ancestors.length + 1, 4096),
        emittedNodeCount: safeInteger(traversalInput.emittedNodeCount, ancestors.length + 1, 4096),
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
      input.version !== 1 ||
      input.featureId !== 'browser-tools'
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
      featureId: input.featureId,
      tdwOrigin
    });
  }

  function isProtocolMessage(data, type, nonce) {
    if (!isObject(data) || data.protocolVersion !== PROTOCOL_VERSION || data.type !== type || data.nonce !== nonce) {
      return false;
    }
    const shapes = {
      TDW_UI_EXPLORER_READY: ['type', 'protocolVersion', 'nonce'],
      TDW_UI_EXPLORER_CONTEXT: ['type', 'protocolVersion', 'nonce', 'contextId', 'context'],
      TDW_UI_EXPLORER_RECEIVED: ['type', 'protocolVersion', 'nonce', 'contextId'],
      TDW_UI_EXPLORER_ERROR: ['type', 'protocolVersion', 'nonce', 'code']
    };
    return Object.hasOwn(shapes, type) && hasExactKeys(data, shapes[type]);
  }

  function createMessage(type, nonce, extra) {
    return Object.freeze({
      ...(isObject(extra) ? extra : {}),
      protocolVersion: PROTOCOL_VERSION,
      type,
      nonce
    });
  }

  const api = Object.freeze({
    PROTOCOL_VERSION,
    CAPTURE_VERSION,
    CAPTURE_SCHEMA,
    PAGE_CONTEXT_SCHEMA,
    PAGE_CONTEXT_VERSION,
    MAX_PAGE_CONTEXT_BYTES,
    CAPTURE_PROFILES: Object.freeze(['ELEMENT_CONTEXT', 'FORM_DIAGNOSTICS']),
    normalizeText,
    isSafeIdentifier,
    normalizeOrigin,
    sanitizePathname,
    safeRouteHash,
    describeElement,
    captureElement,
    captureVisibleFormFields,
    capturePageContext,
    normalizeCapture,
    normalizePageContext,
    normalizeLauncherConfig,
    serializedSize,
    createNonce,
    createMessage,
    isUiExplorerReadyMessage: (data, nonce) =>
      isProtocolMessage(data, 'TDW_UI_EXPLORER_READY', nonce),
    isUiExplorerReceivedMessage: (data, nonce) =>
      isProtocolMessage(data, 'TDW_UI_EXPLORER_RECEIVED', nonce),
    isUiExplorerErrorMessage: (data, nonce) =>
      isProtocolMessage(data, 'TDW_UI_EXPLORER_ERROR', nonce)
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
