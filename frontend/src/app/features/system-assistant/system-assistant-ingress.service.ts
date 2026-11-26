import { inject, Injectable, InjectionToken, OnDestroy, signal } from '@angular/core';
import { filter, map, Observable, of, Subject, take, timeout } from 'rxjs';
import { AssistantCurrentPage } from './system-assistant.models';

export const SYSTEM_ASSISTANT_WINDOW = new InjectionToken<Window>('SYSTEM_ASSISTANT_WINDOW', { factory: () => window });

@Injectable()
export class SystemAssistantIngress implements OnDestroy {
  private readonly browser = inject(SYSTEM_ASSISTANT_WINDOW);
  readonly context = signal<AssistantCurrentPage | null>(null);
  readonly status = signal<'idle' | 'waiting' | 'received' | 'invalid'>('idle');
  readonly error = signal('');
  private parentWindow: Window | null = null;
  private nonce = '';
  private origin = '';
  private timer?: number;
  private requestSequence = 0;
  private readonly updates = new Subject<{id: string; page: AssistantCurrentPage}>();

  get connectionNonce(): string | null { return this.parentWindow ? this.nonce : null; }

  start(): void {
    if (this.status() !== 'idle') return;
    const fragment = this.browser.location.hash.slice(1); if (!fragment) return;
    const params = new URLSearchParams(fragment);
    this.browser.history.replaceState(null, '', this.browser.location.pathname + this.browser.location.search);
    const nonce = params.get('nonce') ?? ''; const origin = params.get('sourceOrigin') ?? '';
    try {
      if ([...params.keys()].length !== 2 || [...params.keys()].some(key => !['nonce', 'sourceOrigin'].includes(key))
        || !/^[A-Za-z0-9_-]{22,128}$/.test(nonce) || !isOrigin(origin) || this.browser.parent === this.browser || !this.browser.parent) throw new Error();
      this.connect(origin, nonce);
    } catch { this.fail('Nieprawidłowy link Browser Tools albo brak rodzica ramki.'); }
  }
  private connect(origin: string, nonce: string): void {
    this.parentWindow = this.browser.parent; this.nonce = nonce; this.origin = origin;
    this.status.set('waiting');
    this.browser.addEventListener('message', this.receive);
    this.timer = this.browser.setTimeout(() => this.fail('Nie otrzymano kontekstu strony. Możesz kontynuować bez niego.'), 12000);
    this.parentWindow!.postMessage({ type: 'TDW_SYSTEM_ASSISTANT_READY', protocolVersion: 1, nonce }, origin);
  }
  reconnect(value: unknown, nonce: unknown): boolean {
    if (!validContext(value) || typeof nonce !== 'string' || !/^[A-Za-z0-9_-]{22,128}$/.test(nonce)) return false;
    try {
      if (!this.browser.parent || this.browser.parent === this.browser) throw new Error();
      this.connect(value.origin, nonce);
    } catch { this.fail('Nie udało się ponownie połączyć ze stroną nadrzędną.'); }
    return true;
  }
  currentPage(): Observable<AssistantCurrentPage | null> {
    if (!this.parentWindow) return of(null);
    const parent = this.parentWindow;
    return new Observable<AssistantCurrentPage>(subscriber => {
      const id = 'request_' + ++this.requestSequence;
      const response = this.updates.pipe(filter(update => update.id === id), map(update => update.page), take(1)).subscribe(subscriber);
      parent.postMessage({type: 'TDW_SYSTEM_ASSISTANT_REQUEST', protocolVersion: 1, nonce: this.nonce, contextId: id}, this.origin);
      return response;
    }).pipe(timeout(12000));
  }
  private readonly receive = (event: MessageEvent): void => {
    if (event.origin !== this.origin || event.source !== this.parentWindow) return;
    const data = event.data;
    const connecting = this.status() === 'waiting';
    if (connecting && data?.type === 'TDW_SYSTEM_ASSISTANT_UPDATE') return;
    if (!connecting && (data?.type !== 'TDW_SYSTEM_ASSISTANT_UPDATE' || data.nonce !== this.nonce)) return;
    if (!exactKeys(data, ['type', 'protocolVersion', 'nonce', 'contextId', 'context'])
      || data['type'] !== (connecting ? 'TDW_SYSTEM_ASSISTANT_CONTEXT' : 'TDW_SYSTEM_ASSISTANT_UPDATE') || data['protocolVersion'] !== 1 || data['nonce'] !== this.nonce
      || typeof data['contextId'] !== 'string' || !/^[A-Za-z0-9_-]{8,96}$/.test(data['contextId'])
      || !validContext(data['context'], this.origin)) {
      this.fail('Odrzucono nieprawidłowy kontekst strony.'); return;
    }
    this.context.set(data['context']); this.status.set('received');
    if (this.timer !== undefined) this.browser.clearTimeout(this.timer);
    this.timer = undefined;
    if (connecting) this.parentWindow!.postMessage({ type: 'TDW_SYSTEM_ASSISTANT_RECEIVED', protocolVersion: 1, nonce: this.nonce, contextId: data['contextId'] }, this.origin);
    this.updates.next({id: data['contextId'], page: data['context']});
  };
  dismiss(): void { this.cleanup(); this.context.set(null); this.error.set(''); this.status.set('idle'); }
  private fail(message: string): void {
    this.context.set(null); this.error.set(message); this.status.set('invalid');
    try { this.parentWindow?.postMessage({type: 'TDW_SYSTEM_ASSISTANT_ERROR', protocolVersion: 1, nonce: this.nonce, code: 'CONTEXT_INVALID'}, this.origin); }
    catch { /* Parent frame may already be detached. */ }
    finally { this.cleanup(); }
  }
  private cleanup(): void {
    this.browser.removeEventListener('message', this.receive);
    if (this.timer !== undefined) this.browser.clearTimeout(this.timer);
    this.timer = undefined; this.parentWindow = null;
  }
  ngOnDestroy(): void { this.cleanup(); this.updates.complete(); }
}

function exactKeys(value: unknown, keys: string[]): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
    && Object.keys(value).length === keys.length && keys.every(key => Object.hasOwn(value, key));
}
function isOrigin(value: string): boolean {
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) && url.origin === value; }
  catch { return false; }
}
function validContext(value: unknown, expectedOrigin?: string): value is AssistantCurrentPage {
  if (!exactKeys(value, ['origin', 'pathname', 'url', 'title', 'capturedAt'])) return false;
  const origin = value['origin'];
  if (typeof origin !== 'string' || (expectedOrigin !== undefined && origin !== expectedOrigin) || typeof value['url'] !== 'string' || !isOrigin(origin)) return false;
  try {
    const url = new URL(value['url']);
    if (url.href !== value['url'] || url.origin !== origin || url.pathname !== value['pathname'] || url.username || url.password) return false;
  } catch { return false; }
  return typeof value['pathname'] === 'string' && value['pathname'].startsWith('/')
    && value['pathname'].length <= 2000 && !/[?#\u0000-\u001f]/.test(value['pathname'])
    && typeof value['title'] === 'string' && value['title'].length <= 200 && typeof value['capturedAt'] === 'string'
    && Number.isFinite(Date.parse(value['capturedAt']));
}
