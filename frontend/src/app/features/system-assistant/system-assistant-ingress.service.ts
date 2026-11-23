import { inject, Injectable, InjectionToken, OnDestroy, signal } from '@angular/core';
import { AssistantPageContext } from './system-assistant.models';

export const SYSTEM_ASSISTANT_WINDOW = new InjectionToken<Window>('SYSTEM_ASSISTANT_WINDOW', { factory: () => window });

@Injectable()
export class SystemAssistantIngress implements OnDestroy {
  private readonly browser = inject(SYSTEM_ASSISTANT_WINDOW);
  readonly context = signal<AssistantPageContext | null>(null);
  readonly status = signal<'idle' | 'waiting' | 'received' | 'invalid'>('idle');
  readonly error = signal('');
  private parentWindow: Window | null = null;
  private nonce = '';
  private origin = '';
  private timer?: number;

  start(): void {
    if (this.status() !== 'idle') return;
    const fragment = this.browser.location.hash.slice(1); if (!fragment) return;
    const params = new URLSearchParams(fragment);
    this.browser.history.replaceState(null, '', this.browser.location.pathname + this.browser.location.search);
    const nonce = params.get('nonce') ?? ''; const origin = params.get('sourceOrigin') ?? '';
    try {
      if ([...params.keys()].length !== 2 || [...params.keys()].some(key => !['nonce', 'sourceOrigin'].includes(key))
        || !/^[A-Za-z0-9_-]{22,128}$/.test(nonce) || !isOrigin(origin) || this.browser.parent === this.browser || !this.browser.parent) throw new Error();
      this.parentWindow = this.browser.parent; this.nonce = nonce; this.origin = origin;
      this.status.set('waiting');
      this.browser.addEventListener('message', this.receive);
      this.timer = this.browser.setTimeout(() => this.fail('Nie otrzymano kontekstu strony. Możesz kontynuować bez niego.'), 12000);
      this.parentWindow!.postMessage({ type: 'TDW_SYSTEM_ASSISTANT_READY', protocolVersion: 1, nonce }, origin);
    } catch { this.fail('Nieprawidłowy link Browser Tools albo brak rodzica ramki. Możesz kontynuować bez kontekstu.'); }
  }
  restoreContext(value: unknown): boolean {
    const origin = value && typeof value === 'object' ? (value as Record<string, unknown>)['origin'] : null;
    if (typeof origin !== 'string' || !isOrigin(origin) || !validContext(value, origin)) return false;
    this.cleanup(); this.context.set(value); this.error.set(''); this.status.set('received');
    return true;
  }
  private readonly receive = (event: MessageEvent): void => {
    if (event.origin !== this.origin || event.source !== this.parentWindow || this.status() !== 'waiting') return;
    const data = event.data;
    if (!exactKeys(data, ['type', 'protocolVersion', 'nonce', 'contextId', 'context'])
      || data['type'] !== 'TDW_SYSTEM_ASSISTANT_CONTEXT' || data['protocolVersion'] !== 1 || data['nonce'] !== this.nonce
      || typeof data['contextId'] !== 'string' || !/^[A-Za-z0-9_-]{8,96}$/.test(data['contextId'])
      || !validContext(data['context'], this.origin)) {
      this.fail('Odrzucono nieprawidłowy kontekst strony.'); return;
    }
    this.context.set(data['context']); this.status.set('received');
    try { this.parentWindow!.postMessage({ type: 'TDW_SYSTEM_ASSISTANT_RECEIVED', protocolVersion: 1, nonce: this.nonce, contextId: data['contextId'] }, this.origin); }
    finally { this.cleanup(); }
  };
  dismiss(): void { this.cleanup(); this.context.set(null); this.error.set(''); this.status.set('idle'); }
  private fail(message: string): void {
    this.error.set(message); this.status.set('invalid');
    try { this.parentWindow?.postMessage({type: 'TDW_SYSTEM_ASSISTANT_ERROR', protocolVersion: 1, nonce: this.nonce, code: 'CONTEXT_INVALID'}, this.origin); }
    catch { /* Parent frame may already be detached. */ }
    finally { this.cleanup(); }
  }
  private cleanup(): void {
    this.browser.removeEventListener('message', this.receive);
    if (this.timer !== undefined) this.browser.clearTimeout(this.timer);
    this.timer = undefined; this.parentWindow = null;
  }
  ngOnDestroy(): void { this.cleanup(); }
}

function exactKeys(value: unknown, keys: string[]): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
    && Object.keys(value).length === keys.length && keys.every(key => Object.hasOwn(value, key));
}
function isOrigin(value: string): boolean {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) && url.origin === value; }
  catch { return false; }
}
function validContext(value: unknown, origin: string): value is AssistantPageContext {
  if (!exactKeys(value, ['origin', 'pathname', 'title', 'capturedAt'])) return false;
  return value['origin'] === origin && typeof value['pathname'] === 'string' && value['pathname'].startsWith('/')
    && value['pathname'].length <= 2000 && !/[?#\u0000-\u001f]/.test(value['pathname'])
    && typeof value['title'] === 'string' && value['title'].length <= 200 && typeof value['capturedAt'] === 'string'
    && Number.isFinite(Date.parse(value['capturedAt']));
}
