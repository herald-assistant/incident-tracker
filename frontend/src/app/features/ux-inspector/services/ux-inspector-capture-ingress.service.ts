import { Injectable, InjectionToken, OnDestroy, inject, signal } from '@angular/core';

import {
  UX_INSPECTOR_CAPTURE_SCHEMA,
  UX_INSPECTOR_CAPTURE_VERSION,
  UX_INSPECTOR_CLIENT_NAME,
  UX_INSPECTOR_FEATURE_ID,
  UX_INSPECTOR_MAX_CAPTURE_BYTES,
  UX_INSPECTOR_PROTOCOL_VERSION,
  UxInspectorCapture,
  UxInspectorIngressStatus
} from '../models/ux-inspector.models';

export const UX_INSPECTOR_WINDOW = new InjectionToken<Window>('UX_INSPECTOR_WINDOW', {
  factory: () => window
});

const READY = 'TDW_UX_INSPECTOR_READY';
const CAPTURE = 'TDW_UX_INSPECTOR_CAPTURE';
const RECEIVED = 'TDW_UX_INSPECTOR_RECEIVED';
const ERROR = 'TDW_UX_INSPECTOR_ERROR';
const NONCE_PATTERN = /^[A-Za-z0-9_-]{22,128}$/;
const HANDSHAKE_TIMEOUT_MS = 12_000;

@Injectable()
export class UxInspectorCaptureIngressService implements OnDestroy {
  private readonly browserWindow = inject(UX_INSPECTOR_WINDOW);
  private opener: Window | null = null;
  private nonce = '';
  private sourceOrigin = '';
  private timeoutHandle: number | null = null;
  private listening = false;

  readonly capture = signal<UxInspectorCapture | null>(null);
  readonly status = signal<UxInspectorIngressStatus>('idle');
  readonly error = signal('');

  start(): void {
    if (this.listening || this.capture()) {
      return;
    }

    const rawFragment = this.browserWindow.location.hash.startsWith('#')
      ? this.browserWindow.location.hash.slice(1)
      : '';
    if (!rawFragment) {
      return;
    }

    const params = new URLSearchParams(rawFragment);
    const nonce = params.get('nonce') ?? '';
    const sourceOrigin = params.get('sourceOrigin') ?? '';
    this.clearFragment();

    if (
      [...params.keys()].some((key) => key !== 'nonce' && key !== 'sourceOrigin') ||
      !NONCE_PATTERN.test(nonce) ||
      !isExactHttpOrigin(sourceOrigin)
    ) {
      this.fail('Link transferu Browser Tools jest niepoprawny albo niekompletny.');
      return;
    }

    const opener = this.browserWindow.opener;
    if (!opener) {
      this.fail(
        'Nie można połączyć UX Inspectora ze stroną źródłową. Polityka popup/COOP mogła odłączyć okno.'
      );
      return;
    }

    this.nonce = nonce;
    this.sourceOrigin = sourceOrigin;
    this.opener = opener;
    this.status.set('waiting');
    this.error.set('');
    this.browserWindow.addEventListener('message', this.receiveMessage);
    this.listening = true;
    this.timeoutHandle = this.browserWindow.setTimeout(() => {
      this.sendError('HANDSHAKE_TIMEOUT');
      this.fail('Strona źródłowa nie przesłała elementu w wymaganym czasie. Uruchom Browser Tools ponownie.');
    }, HANDSHAKE_TIMEOUT_MS);

    opener.postMessage(
      {
        type: READY,
        protocolVersion: UX_INSPECTOR_PROTOCOL_VERSION,
        nonce
      },
      sourceOrigin
    );
  }

  consumeCapture(): void {
    this.capture.set(null);
    this.status.set('idle');
    this.error.set('');
  }

  ngOnDestroy(): void {
    this.cleanup();
    this.capture.set(null);
  }

  private readonly receiveMessage = (event: MessageEvent<unknown>): void => {
    if (event.source !== this.opener || event.origin !== this.sourceOrigin) {
      return;
    }
    if (!isPlainObject(event.data) || event.data['type'] !== CAPTURE) {
      this.rejectExpectedSource('MESSAGE_TYPE_INVALID', 'Browser Tools przesłał nieobsługiwany komunikat.');
      return;
    }
    if (!hasExactKeys(event.data, ['type', 'protocolVersion', 'nonce', 'captureId', 'capture'])) {
      this.rejectExpectedSource('MESSAGE_SHAPE_INVALID', 'Komunikat capture ma niepoprawny kontrakt.');
      return;
    }
    if (
      event.data['protocolVersion'] !== UX_INSPECTOR_PROTOCOL_VERSION ||
      event.data['nonce'] !== this.nonce
    ) {
      this.rejectExpectedSource('HANDSHAKE_MISMATCH', 'Nie zgadza się wersja protokołu albo jednorazowy nonce.');
      return;
    }

    const candidate = event.data['capture'];
    let bytes = UX_INSPECTOR_MAX_CAPTURE_BYTES + 1;
    try {
      bytes = new TextEncoder().encode(JSON.stringify(candidate)).byteLength;
    } catch {
      // Validation below produces the user-facing error.
    }
    if (bytes > UX_INSPECTOR_MAX_CAPTURE_BYTES || !isUxInspectorCapture(candidate)) {
      this.rejectExpectedSource('CAPTURE_INVALID', 'Capture v1 jest niepoprawny albo przekracza limit 128 KiB.');
      return;
    }
    if (
      event.data['captureId'] !== candidate.captureId ||
      candidate.page.origin !== this.sourceOrigin
    ) {
      this.rejectExpectedSource('CAPTURE_SCOPE_MISMATCH', 'Capture nie pasuje do źródłowego okna i handshake.');
      return;
    }

    this.capture.set(candidate);
    this.status.set('received');
    this.error.set('');
    this.opener?.postMessage(
      {
        type: RECEIVED,
        protocolVersion: UX_INSPECTOR_PROTOCOL_VERSION,
        nonce: this.nonce,
        captureId: candidate.captureId
      },
      this.sourceOrigin
    );
    this.cleanup();
  };

  private rejectExpectedSource(code: string, message: string): void {
    this.sendError(code);
    this.fail(message);
  }

  private sendError(code: string): void {
    if (!this.opener || !this.sourceOrigin || !this.nonce) {
      return;
    }
    this.opener.postMessage(
      { type: ERROR, protocolVersion: UX_INSPECTOR_PROTOCOL_VERSION, nonce: this.nonce, code },
      this.sourceOrigin
    );
  }

  private fail(message: string): void {
    this.cleanup();
    this.capture.set(null);
    this.status.set('invalid');
    this.error.set(message);
  }

  private cleanup(): void {
    if (this.listening) {
      this.browserWindow.removeEventListener('message', this.receiveMessage);
      this.listening = false;
    }
    if (this.timeoutHandle !== null) {
      this.browserWindow.clearTimeout(this.timeoutHandle);
      this.timeoutHandle = null;
    }
    this.opener = null;
    this.nonce = '';
    this.sourceOrigin = '';
  }

  private clearFragment(): void {
    const cleanUrl = `${this.browserWindow.location.pathname}${this.browserWindow.location.search}`;
    this.browserWindow.history.replaceState(this.browserWindow.history.state, '', cleanUrl);
  }
}

function isUxInspectorCapture(value: unknown): value is UxInspectorCapture {
  if (!isPlainObject(value) || !hasExactKeys(value, [
    'schema', 'version', 'captureId', 'capturedAt', 'captureProfile', 'page', 'target', 'ancestors',
    'formSnapshot', 'traversal', 'signals', 'limits', 'client'
  ])) return false;
  if (
    value['schema'] !== UX_INSPECTOR_CAPTURE_SCHEMA ||
    value['version'] !== UX_INSPECTOR_CAPTURE_VERSION ||
    typeof value['captureId'] !== 'string' ||
    typeof value['capturedAt'] !== 'string' ||
    !['ELEMENT_CONTEXT', 'FORM_DIAGNOSTICS'].includes(String(value['captureProfile']))
  ) return false;

  const page = value['page'];
  const target = value['target'];
  const traversal = value['traversal'];
  const signals = value['signals'];
  const client = value['client'];
  return isPlainObject(page) && hasExactKeys(page, ['origin', 'path', 'title', 'language', 'queryParameterNames']) &&
    typeof page['origin'] === 'string' && typeof page['path'] === 'string' &&
    isNullableString(page['title']) && isNullableString(page['language']) && isStringArray(page['queryParameterNames']) &&
    isTarget(target) && Array.isArray(value['ancestors']) && value['ancestors'].every(isAncestor) &&
    isFormSnapshot(value['formSnapshot']) &&
    !(value['captureProfile'] === 'ELEMENT_CONTEXT' && value['formSnapshot'] !== null) &&
    isPlainObject(traversal) && hasExactKeys(traversal, ['observedDepth', 'emittedNodeCount', 'omittedNodeCount', 'reachedDocumentRoot']) &&
    isFiniteNumber(traversal['observedDepth']) && isFiniteNumber(traversal['emittedNodeCount']) &&
    isFiniteNumber(traversal['omittedNodeCount']) && typeof traversal['reachedDocumentRoot'] === 'boolean' &&
    isPlainObject(signals) && hasExactKeys(signals, ['shadowBoundaryCount', 'frame', 'redactions']) &&
    isFiniteNumber(signals['shadowBoundaryCount']) && typeof signals['frame'] === 'string' && isStringArray(signals['redactions']) &&
    isStringArray(value['limits']) && isPlainObject(client) &&
    hasExactKeys(client, ['name', 'version', 'featureId']) &&
    client['name'] === UX_INSPECTOR_CLIENT_NAME && typeof client['version'] === 'string' &&
    client['featureId'] === UX_INSPECTOR_FEATURE_ID;
}

function isTarget(value: unknown): boolean {
  if (!isPlainObject(value) || !hasExactKeys(value, [
    'tag', 'role', 'accessibleName', 'text', 'domFingerprint', 'state', 'bounds'
  ])) return false;
  const state = value['state'];
  const bounds = value['bounds'];
  return typeof value['tag'] === 'string' && isNullableString(value['role']) &&
    isNullableString(value['accessibleName']) && isNullableString(value['text']) &&
    isDomFingerprint(value['domFingerprint']) && isPlainObject(state) &&
    hasExactKeys(state, ['disabled', 'ariaDisabled', 'readOnly', 'required', 'invalid', 'checked', 'expanded', 'hidden']) &&
    ['disabled', 'ariaDisabled', 'readOnly', 'required', 'invalid', 'hidden'].every((key) => typeof state[key] === 'boolean') &&
    isNullableBoolean(state['checked']) && isNullableBoolean(state['expanded']) &&
    isPlainObject(bounds) && hasExactKeys(bounds, ['x', 'y', 'width', 'height']) &&
    ['x', 'y', 'width', 'height'].every((key) => isFiniteNumber(bounds[key]));
}

function isDomFingerprint(value: unknown): boolean {
  return isPlainObject(value) && hasExactKeys(value, [
    'stableAttributes', 'selectorCandidates', 'componentBoundaryTags', 'labelFor'
  ]) && isStringRecord(value['stableAttributes']) && isStringArray(value['selectorCandidates']) &&
    isStringArray(value['componentBoundaryTags']) && isNullableString(value['labelFor']);
}

function isFormSnapshot(value: unknown): boolean {
  if (value === null) return true;
  if (!isPlainObject(value) || !hasExactKeys(value, [
    'source', 'stableAttributes', 'selectorCandidates', 'valid', 'observedControlCount',
    'emittedControlCount', 'omittedControlCount', 'controls', 'submitters',
    'excludedControls', 'valueCharacters', 'valuesTruncated'
  ])) return false;
  return ['NEAREST_FORM', 'SELECTED_CONTROL_ONLY'].includes(String(value['source'])) &&
    isStringRecord(value['stableAttributes']) && isStringArray(value['selectorCandidates']) &&
    isNullableBoolean(value['valid']) && isFiniteNumber(value['observedControlCount']) &&
    isFiniteNumber(value['emittedControlCount']) && isFiniteNumber(value['omittedControlCount']) &&
    Array.isArray(value['controls']) && value['controls'].every(isFormControl) &&
    Array.isArray(value['submitters']) && value['submitters'].every(isFormSubmitter) &&
    Array.isArray(value['excludedControls']) && value['excludedControls'].every(isExcludedFormControl) &&
    isFiniteNumber(value['valueCharacters']) && typeof value['valuesTruncated'] === 'boolean';
}

function isFormControl(value: unknown): boolean {
  if (!isPlainObject(value) || !hasExactKeys(value, [
    'selectedTarget', 'tag', 'type', 'name', 'formControlName', 'accessibleName',
    'stableAttributes', 'value', 'valueTruncated', 'checked', 'selectedValues',
    'selectedLabels', 'disabled', 'readOnly', 'required', 'validity'
  ])) return false;
  return typeof value['selectedTarget'] === 'boolean' && typeof value['tag'] === 'string' &&
    isNullableString(value['type']) && isNullableString(value['name']) &&
    isNullableString(value['formControlName']) && isNullableString(value['accessibleName']) &&
    isStringRecord(value['stableAttributes']) && isNullableString(value['value']) &&
    typeof value['valueTruncated'] === 'boolean' && isNullableBoolean(value['checked']) &&
    isStringArray(value['selectedValues']) && isStringArray(value['selectedLabels']) &&
    typeof value['disabled'] === 'boolean' && typeof value['readOnly'] === 'boolean' &&
    typeof value['required'] === 'boolean' && isValidity(value['validity']);
}

function isValidity(value: unknown): boolean {
  if (value === null) return true;
  const booleanKeys = [
    'valid', 'valueMissing', 'typeMismatch', 'patternMismatch', 'tooShort', 'tooLong',
    'rangeUnderflow', 'rangeOverflow', 'stepMismatch', 'badInput', 'customError'
  ];
  return isPlainObject(value) && hasExactKeys(value, [...booleanKeys, 'validationMessage']) &&
    booleanKeys.every((key) => typeof value[key] === 'boolean') &&
    isNullableString(value['validationMessage']);
}

function isFormSubmitter(value: unknown): boolean {
  return isPlainObject(value) && hasExactKeys(value, [
    'selectedTarget', 'tag', 'type', 'accessibleName', 'stableAttributes', 'disabled'
  ]) && typeof value['selectedTarget'] === 'boolean' && typeof value['tag'] === 'string' &&
    isNullableString(value['type']) && isNullableString(value['accessibleName']) &&
    isStringRecord(value['stableAttributes']) && typeof value['disabled'] === 'boolean';
}

function isExcludedFormControl(value: unknown): boolean {
  return isPlainObject(value) && hasExactKeys(value, [
    'tag', 'type', 'name', 'formControlName', 'reason'
  ]) && typeof value['tag'] === 'string' && isNullableString(value['type']) &&
    isNullableString(value['name']) && isNullableString(value['formControlName']) &&
    typeof value['reason'] === 'string';
}

function isAncestor(value: unknown): boolean {
  return isPlainObject(value) && hasExactKeys(value, [
    'depth', 'tag', 'role', 'accessibleName', 'text', 'stableAttributes'
  ]) && isFiniteNumber(value['depth']) && typeof value['tag'] === 'string' &&
    isNullableString(value['role']) && isNullableString(value['accessibleName']) &&
    isNullableString(value['text']) && isStringRecord(value['stableAttributes']);
}

function isExactHttpOrigin(value: string): boolean {
  try {
    const parsed = new URL(value);
    return (parsed.protocol === 'http:' || parsed.protocol === 'https:') && parsed.origin === value;
  } catch {
    return false;
  }
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value: Record<string, unknown>, keys: string[]): boolean {
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  return actual.length === expected.length && actual.every((key, index) => key === expected[index]);
}

function isNullableString(value: unknown): boolean {
  return value === null || typeof value === 'string';
}

function isNullableBoolean(value: unknown): boolean {
  return value === null || typeof value === 'boolean';
}

function isFiniteNumber(value: unknown): boolean {
  return typeof value === 'number' && Number.isFinite(value);
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string');
}

function isStringRecord(value: unknown): value is Record<string, string> {
  return isPlainObject(value) && Object.values(value).every((item) => typeof item === 'string');
}
