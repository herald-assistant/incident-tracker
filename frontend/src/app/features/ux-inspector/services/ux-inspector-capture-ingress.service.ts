import { Injectable, InjectionToken, OnDestroy, inject, signal } from '@angular/core';

import {
  UX_INSPECTOR_CAPTURE_SCHEMA,
  UX_INSPECTOR_CAPTURE_VERSION,
  UX_INSPECTOR_CLIENT_NAME,
  UX_INSPECTOR_FEATURE_ID,
  UX_INSPECTOR_MAX_CAPTURE_BYTES,
  UX_INSPECTOR_PROTOCOL_VERSION,
  UxInspectorCapture,
  UxInspectorVisibleFormField,
  UxInspectorIngressStatus
} from '../models/ux-inspector.models';

export const UX_INSPECTOR_WINDOW = new InjectionToken<Window>('UX_INSPECTOR_WINDOW', {
  factory: () => window
});

const READY = 'TDW_UX_INSPECTOR_READY';
const CAPTURE = 'TDW_UX_INSPECTOR_CAPTURE';
const RECEIVED = 'TDW_UX_INSPECTOR_RECEIVED';
const ERROR = 'TDW_UX_INSPECTOR_ERROR';
const STORE_CHUNK = 'TDW_UX_INSPECTOR_STORE_CHUNK';
const STORE_ACK = 'TDW_UX_INSPECTOR_STORE_ACK';
const FORM_CHUNK = 'TDW_UX_INSPECTOR_FORM_CHUNK';
const FORM_ACK = 'TDW_UX_INSPECTOR_FORM_ACK';
const MAX_TRANSFER_BYTES = 16 * 1024 * 1024;
const NONCE_PATTERN = /^[A-Za-z0-9_-]{22,128}$/;
const HANDSHAKE_TIMEOUT_MS = 12_000;
type TransferKind = 'store' | 'form';
type TransferState = { expected: number; chunks: string[]; characters: number; timer: number | null; done: boolean };

@Injectable()
export class UxInspectorCaptureIngressService implements OnDestroy {
  private readonly browserWindow = inject(UX_INSPECTOR_WINDOW);
  private opener: Window | null = null;
  private nonce = '';
  private sourceOrigin = '';
  private timeoutHandle: number | null = null;
  private listening = false;
  private readonly transfers: Record<TransferKind, TransferState> = {
    store: { expected: 0, chunks: [], characters: 0, timer: null, done: true },
    form: { expected: 0, chunks: [], characters: 0, timer: null, done: true }
  };

  readonly capture = signal<UxInspectorCapture | null>(null);
  readonly status = signal<UxInspectorIngressStatus>('idle');
  readonly error = signal('');
  readonly storeState = signal<Record<string, unknown> | unknown[] | null>(null);
  readonly storeStatus = signal<'unavailable' | 'receiving' | 'available'>('unavailable');
  readonly formFields = signal<UxInspectorVisibleFormField[] | null>(null);
  readonly formStatus = signal<'unavailable' | 'receiving' | 'available'>('unavailable');

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
    this.storeState.set(null);
    this.storeStatus.set('unavailable');
    this.formFields.set(null);
    this.formStatus.set('unavailable');
    this.status.set('idle');
    this.error.set('');
  }

  ngOnDestroy(): void {
    this.cleanup();
    this.capture.set(null);
    this.storeState.set(null);
    this.formFields.set(null);
  }

  private readonly receiveMessage = (event: MessageEvent<unknown>): void => {
    if (event.source !== this.opener || event.origin !== this.sourceOrigin) return;
    if (this.capture()) {
      if (isPlainObject(event.data) && event.data['type'] === STORE_CHUNK) this.receiveChunk('store', event.data);
      if (isPlainObject(event.data) && event.data['type'] === FORM_CHUNK) this.receiveChunk('form', event.data);
      return;
    }
    if (!isPlainObject(event.data) || event.data['type'] !== CAPTURE ||
        !hasExactKeys(event.data, ['type', 'protocolVersion', 'nonce', 'captureId', 'capture',
          'storeStatus', 'storeChunks', 'formStatus', 'formChunks'])) {
      this.rejectExpectedSource('MESSAGE_SHAPE_INVALID', 'Komunikat capture ma niepoprawny kontrakt.');
      return;
    }
    if (event.data['protocolVersion'] !== UX_INSPECTOR_PROTOCOL_VERSION || event.data['nonce'] !== this.nonce) {
      this.rejectExpectedSource('HANDSHAKE_MISMATCH', 'Nie zgadza się wersja protokołu albo jednorazowy nonce.');
      return;
    }
    const candidate = event.data['capture'];
    let bytes = UX_INSPECTOR_MAX_CAPTURE_BYTES + 1;
    try { bytes = new TextEncoder().encode(JSON.stringify(candidate)).byteLength; } catch { /* invalid */ }
    if (bytes > UX_INSPECTOR_MAX_CAPTURE_BYTES || !isUxInspectorCapture(candidate)) {
      this.rejectExpectedSource('CAPTURE_INVALID', 'Capture v2 jest niepoprawny albo przekracza limit 128 KiB.');
      return;
    }
    if (event.data['captureId'] !== candidate.captureId || candidate.page.origin !== this.sourceOrigin) {
      this.rejectExpectedSource('CAPTURE_SCOPE_MISMATCH', 'Capture nie pasuje do źródłowego okna i handshake.');
      return;
    }
    this.capture.set(candidate);
    this.status.set('received');
    this.error.set('');
    if (this.timeoutHandle !== null) this.browserWindow.clearTimeout(this.timeoutHandle);
    this.timeoutHandle = null;
    this.opener?.postMessage({
      type: RECEIVED, protocolVersion: UX_INSPECTOR_PROTOCOL_VERSION,
      nonce: this.nonce, captureId: candidate.captureId
    }, this.sourceOrigin);
    this.beginTransfer('store', event.data['storeStatus'], event.data['storeChunks']);
    this.beginTransfer('form', candidate.captureProfile === 'FORM_DIAGNOSTICS'
      ? event.data['formStatus'] : 'UNAVAILABLE', event.data['formChunks']);
    if (this.transfers.store.done && this.transfers.form.done) this.cleanup();
  };

  private beginTransfer(kind: TransferKind, status: unknown, count: unknown): void {
    const state = this.transfers[kind];
    state.expected = status === 'AVAILABLE' && typeof count === 'number' &&
      Number.isInteger(count) && count > 0 && count <= 600 ? count : 0;
    state.chunks = [];
    state.characters = 0;
    state.done = state.expected === 0;
    if (kind === 'store') this.storeStatus.set(state.done ? 'unavailable' : 'receiving');
    else this.formStatus.set(state.done ? 'unavailable' : 'receiving');
    if (!state.done) this.resetTransferTimeout(kind);
  }

  private receiveChunk(kind: TransferKind, data: Record<string, unknown>): void {
    const state = this.transfers[kind];
    if (state.done) return;
    if (!hasExactKeys(data, ['type', 'protocolVersion', 'nonce', 'captureId', 'index', 'total', 'chunk']) ||
        data['protocolVersion'] !== UX_INSPECTOR_PROTOCOL_VERSION || data['nonce'] !== this.nonce ||
        data['captureId'] !== this.capture()?.captureId || data['total'] !== state.expected ||
        data['index'] !== state.chunks.length || typeof data['chunk'] !== 'string' ||
        data['chunk'].length > 32000 || state.characters + data['chunk'].length > MAX_TRANSFER_BYTES) {
      this.finishTransfer(kind, null);
      return;
    }
    state.chunks.push(data['chunk']);
    state.characters += data['chunk'].length;
    this.opener?.postMessage({
      type: kind === 'store' ? STORE_ACK : FORM_ACK,
      protocolVersion: UX_INSPECTOR_PROTOCOL_VERSION,
      nonce: this.nonce, captureId: this.capture()?.captureId, index: data['index']
    }, this.sourceOrigin);
    if (state.chunks.length < state.expected) {
      this.resetTransferTimeout(kind);
      return;
    }
    const json = state.chunks.join('');
    let parsed: unknown = null;
    try { parsed = JSON.parse(json); } catch { /* unavailable */ }
    if (new TextEncoder().encode(json).byteLength > MAX_TRANSFER_BYTES) parsed = null;
    if (kind === 'store') {
      this.finishTransfer(kind, parsed && typeof parsed === 'object' ? parsed : null);
    } else {
      this.finishTransfer(kind, isVisibleFormFields(parsed) ? parsed : null);
    }
  }

  private finishTransfer(kind: TransferKind, value: unknown): void {
    const state = this.transfers[kind];
    if (state.timer !== null) this.browserWindow.clearTimeout(state.timer);
    state.timer = null;
    state.done = true;
    state.expected = 0;
    state.chunks = [];
    state.characters = 0;
    if (kind === 'store') {
      const store = value && typeof value === 'object'
        ? value as Record<string, unknown> | unknown[] : null;
      this.storeState.set(store);
      this.storeStatus.set(store ? 'available' : 'unavailable');
    } else {
      const fields = Array.isArray(value) ? value as UxInspectorVisibleFormField[] : null;
      this.formFields.set(fields);
      this.formStatus.set(fields ? 'available' : 'unavailable');
    }
    if (this.transfers.store.done && this.transfers.form.done) this.cleanup();
  }

  private resetTransferTimeout(kind: TransferKind): void {
    const state = this.transfers[kind];
    if (state.timer !== null) this.browserWindow.clearTimeout(state.timer);
    state.timer = this.browserWindow.setTimeout(() => this.finishTransfer(kind, null), HANDSHAKE_TIMEOUT_MS);
  }

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
    this.storeState.set(null);
    this.storeStatus.set('unavailable');
    this.formFields.set(null);
    this.formStatus.set('unavailable');
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
    for (const state of Object.values(this.transfers)) {
      if (state.timer !== null) this.browserWindow.clearTimeout(state.timer);
      state.timer = null;
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
    'traversal', 'signals', 'limits', 'client'
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

function isVisibleFormFields(value: unknown): value is UxInspectorVisibleFormField[] {
  return Array.isArray(value) && value.every((field) =>
    isPlainObject(field) && hasExactKeys(field, [
      'tag', 'type', 'name', 'id', 'testId', 'label', 'disabled', 'value',
      'display', 'source', 'checked', 'indeterminate', 'invalid', 'errors',
      'descriptions', 'nativeInvalid', 'nativeValidationMessage'
    ]) &&
    ['tag', 'type', 'name', 'id', 'testId', 'label', 'display', 'nativeValidationMessage']
      .every((key) => typeof field[key] === 'string') &&
    (typeof field['value'] === 'string' || isStringArray(field['value'])) &&
    ['dom', 'display-text'].includes(String(field['source'])) &&
    ['disabled', 'checked', 'indeterminate', 'invalid', 'nativeInvalid']
      .every((key) => isNullableBoolean(field[key])) &&
    isStringArray(field['errors']) && isStringArray(field['descriptions']));
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
