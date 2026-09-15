import {
  UI_EXPLORER_CAPTURE_PREVIEW_VERSION,
  type UiAncestorDescriptor,
  type UiElementDescriptor,
  type UiElementState,
  type UiExplorerCapturePreview
} from './types';

const MAX_TEXT_LENGTH = 180;
const MAX_ACCESSIBLE_NAME_LENGTH = 140;
const MAX_ATTRIBUTE_LENGTH = 100;
const MAX_CLASSES = 8;
const MAX_ANCESTORS = 24;
const STABLE_ATTRIBUTES = ['data-testid', 'data-test', 'data-cy', 'name', 'type'] as const;
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
const SENSITIVE_PATTERN = /(authorization|bearer|cookie|csrf|jwt|password|secret|session|token)/i;
const DYNAMIC_IDENTIFIER_PATTERN = /(@|[0-9]{6,}|[0-9a-f]{8}-[0-9a-f-]{20,})/i;
const EMAIL_PATTERN = /\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi;
const UUID_PATTERN = /\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\b/gi;
const LONG_NUMBER_PATTERN = /\b\d{6,}\b/g;

export function captureUiElement(
  element: Element,
  extensionVersion: string,
  pageUrl: string = window.location.href,
  capturedAt: string = new Date().toISOString()
): UiExplorerCapturePreview {
  const url = new URL(pageUrl);
  const ancestry = collectAncestry(element);
  const emittedAncestors = selectAncestors(ancestry.elements.slice(1));
  const rect = element.getBoundingClientRect();

  return {
    captureVersion: UI_EXPLORER_CAPTURE_PREVIEW_VERSION,
    page: {
      origin: url.origin,
      pathname: sanitizePathname(url.pathname),
      routeHash: safeRouteHash(url.hash),
      queryParameterNames: [...new Set(url.searchParams.keys())]
        .filter((name) => isSafeIdentifier(name))
        .slice(0, 16),
      title: normalizeText(document.title, 180) ?? '',
      language: normalizeText(document.documentElement.lang, 20)
    },
    selection: {
      target: describeElement(element),
      ancestors: emittedAncestors,
      traversal: {
        observedDepth: ancestry.elements.length,
        emittedNodeCount: emittedAncestors.length + 1,
        omittedNodeCount: Math.max(0, ancestry.elements.length - emittedAncestors.length - 1),
        reachedDocumentRoot:
          ancestry.elements.at(-1)?.tagName.toLowerCase() === document.documentElement.tagName.toLowerCase()
      },
      shadowBoundaryCount: ancestry.shadowBoundaryCount,
      frame: window.top === window ? 'TOP_LEVEL' : 'SAME_ORIGIN_FRAME',
      viewportBounds: {
        x: round(rect.x),
        y: round(rect.y),
        width: round(rect.width),
        height: round(rect.height)
      }
    },
    client: {
      extensionVersion,
      capturedAt
    }
  };
}

export function describeElement(element: Element): UiElementDescriptor {
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

function collectAncestry(element: Element): {
  elements: Element[];
  shadowBoundaryCount: number;
} {
  const elements: Element[] = [];
  let current: Element | null = element;
  let shadowBoundaryCount = 0;

  while (current) {
    elements.push(current);
    if (current.parentElement) {
      current = current.parentElement;
      continue;
    }
    const root = current.getRootNode();
    if (root instanceof ShadowRoot) {
      shadowBoundaryCount += 1;
      current = root.host;
      continue;
    }
    current = null;
  }

  return { elements, shadowBoundaryCount };
}

function selectAncestors(elements: Element[]): UiAncestorDescriptor[] {
  const selectedIndices = new Set<number>();
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

  return [...selectedIndices]
    .sort((left, right) => left - right)
    .slice(0, MAX_ANCESTORS)
    .map((index) => describeAncestor(elements[index]!, index + 1));
}

function describeAncestor(element: Element, depth: number): UiAncestorDescriptor {
  const descriptor = describeElement(element);
  return {
    depth,
    tag: descriptor.tag,
    role: descriptor.role,
    accessibleName: descriptor.accessibleName,
    text: descriptor.text,
    stableAttributes: descriptor.stableAttributes,
    classes: descriptor.classes
  };
}

function isSemanticAncestor(element: Element): boolean {
  const tag = element.tagName.toLowerCase();
  return (
    SEMANTIC_TAGS.has(tag) ||
    tag.includes('-') ||
    Boolean(element.getAttribute('role')) ||
    Object.keys(stableAttributes(element)).length > 0
  );
}

function stableAttributes(element: Element): Record<string, string> {
  const result: Record<string, string> = {};
  const id = element.getAttribute('id');
  if (id && isSafeIdentifier(id)) {
    result['id'] = truncate(id, MAX_ATTRIBUTE_LENGTH);
  }

  for (const name of STABLE_ATTRIBUTES) {
    const value = element.getAttribute(name);
    if (!value || SENSITIVE_PATTERN.test(name) || !isSafeIdentifier(value)) {
      continue;
    }
    result[name] = truncate(value, MAX_ATTRIBUTE_LENGTH);
  }
  return result;
}

function safeClasses(element: Element): string[] {
  return [...element.classList]
    .filter((value) => isSafeIdentifier(value))
    .slice(0, MAX_CLASSES)
    .map((value) => truncate(value, 64));
}

function accessibleName(element: Element): string | null {
  const ariaLabel = element.getAttribute('aria-label');
  if (ariaLabel) {
    return normalizeText(ariaLabel, MAX_ACCESSIBLE_NAME_LENGTH);
  }

  const labelledBy = element.getAttribute('aria-labelledby');
  if (labelledBy) {
    const label = labelledBy
      .split(/\s+/)
      .map((id) => document.getElementById(id)?.textContent ?? '')
      .join(' ');
    const normalized = normalizeText(label, MAX_ACCESSIBLE_NAME_LENGTH);
    if (normalized) {
      return normalized;
    }
  }

  if (element instanceof HTMLInputElement && element.labels?.length) {
    const label = [...element.labels].map((item) => item.textContent ?? '').join(' ');
    const normalized = normalizeText(label, MAX_ACCESSIBLE_NAME_LENGTH);
    if (normalized) {
      return normalized;
    }
  }

  return safeElementText(element) ?? normalizeText(element.getAttribute('title'), MAX_ACCESSIBLE_NAME_LENGTH);
}

function safeElementText(element: Element): string | null {
  if (element.matches('input, textarea, select, [contenteditable="true"]')) {
    return null;
  }
  return normalizeText(element.textContent, MAX_TEXT_LENGTH);
}

function elementState(element: Element): UiElementState {
  const formControl =
    element instanceof HTMLInputElement ||
    element instanceof HTMLTextAreaElement ||
    element instanceof HTMLSelectElement ||
    element instanceof HTMLButtonElement
      ? element
      : null;
  const checkable = element instanceof HTMLInputElement ? element : null;

  return {
    disabled: Boolean(formControl?.disabled),
    ariaDisabled: element.getAttribute('aria-disabled') === 'true',
    readOnly:
      (element instanceof HTMLInputElement || element instanceof HTMLTextAreaElement) &&
      element.readOnly,
    required: Boolean(formControl && 'required' in formControl && formControl.required),
    invalid:
      element.getAttribute('aria-invalid') === 'true' ||
      Boolean(formControl && 'validity' in formControl && !formControl.validity.valid),
    checked: checkable && ['checkbox', 'radio'].includes(checkable.type) ? checkable.checked : null,
    expanded: booleanAttribute(element, 'aria-expanded'),
    hidden:
      (element instanceof HTMLElement && element.hidden) || element.getAttribute('aria-hidden') === 'true'
  };
}

function inferredRole(element: Element): string | null {
  const explicit = normalizeText(element.getAttribute('role'), 40);
  if (explicit) {
    return explicit;
  }
  const tag = element.tagName.toLowerCase();
  if (tag === 'button') return 'button';
  if (tag === 'a' && element.hasAttribute('href')) return 'link';
  if (tag === 'select') return 'combobox';
  if (tag === 'textarea') return 'textbox';
  if (element instanceof HTMLInputElement) {
    if (element.type === 'checkbox') return 'checkbox';
    if (element.type === 'radio') return 'radio';
    if (element.type === 'button' || element.type === 'submit') return 'button';
    return 'textbox';
  }
  return null;
}

function booleanAttribute(element: Element, name: string): boolean | null {
  const value = element.getAttribute(name);
  if (value === 'true') return true;
  if (value === 'false') return false;
  return null;
}

function safeRouteHash(hash: string): string | null {
  if (!hash.startsWith('#/') && !hash.startsWith('#!/')) {
    return null;
  }
  const route = hash.slice(1).split('?')[0] ?? '';
  if (!route || route.includes('=') || SENSITIVE_PATTERN.test(route)) {
    return null;
  }
  return truncate(sanitizePathname(route), 300);
}

function isSafeIdentifier(value: string): boolean {
  const normalized = value.trim();
  return (
    normalized.length > 0 &&
    normalized.length <= MAX_ATTRIBUTE_LENGTH &&
    !SENSITIVE_PATTERN.test(normalized) &&
    !DYNAMIC_IDENTIFIER_PATTERN.test(normalized)
  );
}

function normalizeText(value: string | null | undefined, limit: number): string | null {
  if (!value) {
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

function sanitizePathname(pathname: string): string {
  const sanitized = pathname
    .split('/')
    .map((segment) => {
      let decoded = segment;
      try {
        decoded = decodeURIComponent(segment);
      } catch {
        return ':value';
      }
      if (
        /^\d+$/.test(decoded) ||
        /^[0-9a-f]{8}-[0-9a-f-]{20,}$/i.test(decoded) ||
        EMAIL_PATTERN.test(decoded)
      ) {
        EMAIL_PATTERN.lastIndex = 0;
        return ':value';
      }
      EMAIL_PATTERN.lastIndex = 0;
      return truncate(segment, 80);
    })
    .join('/');
  return truncate(sanitized || '/', 500);
}

function truncate(value: string, limit: number): string {
  return value.length <= limit ? value : `${value.slice(0, Math.max(0, limit - 1))}…`;
}

function round(value: number): number {
  return Math.round(value * 100) / 100;
}
