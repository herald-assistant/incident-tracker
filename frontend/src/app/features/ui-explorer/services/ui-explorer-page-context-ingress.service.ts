import { Injectable, InjectionToken, OnDestroy, inject, signal } from '@angular/core';

import { UiExplorerPageContext } from '../models/ui-explorer.models';

export const UI_EXPLORER_WINDOW = new InjectionToken<Window>('UI_EXPLORER_WINDOW', {
  factory: () => window
});

const PROTOCOL_VERSION = 1;
const NONCE_PATTERN = /^[A-Za-z0-9_-]{22,128}$/;
const CONTEXT_ID_PATTERN = /^[A-Za-z0-9_-]{8,96}$/;
const COMPONENT_TAG_PATTERN = /^[a-z][a-z0-9-]*-[a-z0-9-]+$/;
const MAX_BYTES = 4096;
const HANDSHAKE_TIMEOUT_MS = 12_000;

@Injectable()
export class UiExplorerPageContextIngressService implements OnDestroy {
  private readonly browserWindow = inject(UI_EXPLORER_WINDOW);
  private opener: Window | null = null;
  private nonce = '';
  private sourceOrigin = '';
  private timeoutHandle: number | null = null;
  private listening = false;

  readonly context = signal<UiExplorerPageContext | null>(null);
  readonly status = signal<'idle' | 'waiting' | 'received' | 'invalid'>('idle');
  readonly error = signal('');

  start(): void {
    if (this.listening || this.context()) return;
    const rawFragment = this.browserWindow.location.hash.startsWith('#')
      ? this.browserWindow.location.hash.slice(1) : '';
    if (!rawFragment) return;
    const params = new URLSearchParams(rawFragment);
    const nonce = params.get('nonce') ?? '';
    const sourceOrigin = params.get('sourceOrigin') ?? '';
    this.clearFragment();
    if ([...params.keys()].some((key) => key !== 'nonce' && key !== 'sourceOrigin') ||
        !NONCE_PATTERN.test(nonce) || !isExactHttpOrigin(sourceOrigin)) {
      this.fail('Link transferu Browser Tools jest niepoprawny albo niekompletny.');
      return;
    }
    const opener = this.browserWindow.opener;
    if (!opener) {
      this.fail('Nie można połączyć UI Explorera ze stroną źródłową. Polityka popup/COOP mogła odłączyć okno.');
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
      this.fail('Strona źródłowa nie przesłała widoku w wymaganym czasie. Uruchom Browser Tools ponownie.');
    }, HANDSHAKE_TIMEOUT_MS);
    opener.postMessage({ type: 'TDW_UI_EXPLORER_READY', protocolVersion: PROTOCOL_VERSION, nonce }, sourceOrigin);
  }

  dismiss(): void {
    this.context.set(null);
    this.status.set('idle');
    this.error.set('');
  }

  ngOnDestroy(): void {
    this.cleanup();
    this.context.set(null);
  }

  private readonly receiveMessage = (event: MessageEvent<unknown>): void => {
    if (event.source !== this.opener || event.origin !== this.sourceOrigin) return;
    const data = event.data;
    if (!isPlainObject(data) || !hasExactKeys(data, ['type', 'protocolVersion', 'nonce', 'contextId', 'context']) ||
        data['type'] !== 'TDW_UI_EXPLORER_CONTEXT' || data['protocolVersion'] !== PROTOCOL_VERSION ||
        data['nonce'] !== this.nonce) {
      this.reject('MESSAGE_INVALID', 'Browser Tools przesłał nieobsługiwany komunikat.');
      return;
    }
    const candidate = data['context'];
    let bytes = MAX_BYTES + 1;
    try { bytes = new TextEncoder().encode(JSON.stringify(candidate)).byteLength; } catch { /* Invalid context below. */ }
    if (bytes > MAX_BYTES || !isPageContext(candidate)) {
      this.reject('CONTEXT_INVALID', 'Wskazówka widoku jest niepoprawna.');
      return;
    }
    if (data['contextId'] !== candidate.contextId || candidate.page.origin !== this.sourceOrigin) {
      this.reject('CONTEXT_SCOPE_MISMATCH', 'Wskazówka widoku nie pasuje do źródłowego okna.');
      return;
    }
    this.context.set(candidate);
    this.status.set('received');
    this.error.set('');
    this.opener?.postMessage({
      type: 'TDW_UI_EXPLORER_RECEIVED', protocolVersion: PROTOCOL_VERSION,
      nonce: this.nonce, contextId: candidate.contextId
    }, this.sourceOrigin);
    this.cleanup();
  };

  private reject(code: string, message: string): void {
    this.sendError(code);
    this.fail(message);
  }

  private sendError(code: string): void {
    if (this.opener && this.sourceOrigin && this.nonce) {
      this.opener.postMessage({
        type: 'TDW_UI_EXPLORER_ERROR', protocolVersion: PROTOCOL_VERSION,
        nonce: this.nonce, code
      }, this.sourceOrigin);
    }
  }

  private fail(message: string): void {
    this.cleanup();
    this.context.set(null);
    this.status.set('invalid');
    this.error.set(message);
  }

  private cleanup(): void {
    if (this.listening) this.browserWindow.removeEventListener('message', this.receiveMessage);
    if (this.timeoutHandle !== null) this.browserWindow.clearTimeout(this.timeoutHandle);
    this.listening = false;
    this.timeoutHandle = null;
    this.opener = null;
    this.nonce = '';
    this.sourceOrigin = '';
  }

  private clearFragment(): void {
    this.browserWindow.history.replaceState(
      this.browserWindow.history.state, '',
      `${this.browserWindow.location.pathname}${this.browserWindow.location.search}`
    );
  }
}

function isPageContext(value: unknown): value is UiExplorerPageContext {
  if (!isPlainObject(value) || !hasExactKeys(value, ['schema', 'version', 'contextId', 'page']) ||
      value['schema'] !== 'tdw.ui-explorer-page-context' || value['version'] !== 1 ||
      typeof value['contextId'] !== 'string' || !CONTEXT_ID_PATTERN.test(value['contextId'])) return false;
  const page = value['page'];
  if (!isPlainObject(page) || !hasExactKeys(page, ['origin', 'path', 'componentBoundaryTags']) ||
      !isExactHttpOrigin(page['origin']) || typeof page['path'] !== 'string' ||
      page['path'].length > 500 || !page['path'].startsWith('/') || /[?#]/.test(page['path'])) return false;
  const tags = page['componentBoundaryTags'];
  return Array.isArray(tags) && tags.length <= 8 &&
    tags.every((tag) => typeof tag === 'string' && COMPONENT_TAG_PATTERN.test(tag)) &&
    new Set(tags).size === tags.length;
}

function isExactHttpOrigin(value: unknown): value is string {
  if (typeof value !== 'string') return false;
  try {
    const parsed = new URL(value);
    return ['http:', 'https:'].includes(parsed.protocol) && parsed.origin === value;
  } catch { return false; }
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value);
}

function hasExactKeys(value: Record<string, unknown>, keys: string[]): boolean {
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  return actual.length === expected.length && actual.every((key, index) => key === expected[index]);
}
