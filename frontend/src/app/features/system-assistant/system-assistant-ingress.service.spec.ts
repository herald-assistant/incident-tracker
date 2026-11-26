import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { SYSTEM_ASSISTANT_WINDOW, SystemAssistantIngress } from './system-assistant-ingress.service';

describe('SystemAssistantIngress', () => {
  const nonce = 'n_12345678901234567890123456789012';
  let listener: ((event: MessageEvent) => void) | undefined;
  let received: unknown[];
  let parentWindow: { postMessage: (data: unknown) => void };
  beforeEach(() => {
    received = []; listener = undefined; parentWindow = { postMessage: data => received.push(data) };
    TestBed.configureTestingModule({ providers: [SystemAssistantIngress, { provide: SYSTEM_ASSISTANT_WINDOW, useValue: {
      location: { hash: `#nonce=${nonce}&sourceOrigin=http%3A%2F%2F127.0.0.1%3A8099`, pathname: '/system-assistant', search: '' },
      history: { replaceState() {} }, parent: parentWindow,
      addEventListener: (_: string, handler: (event: MessageEvent) => void) => listener = handler,
      removeEventListener: () => listener = undefined, setTimeout: () => 1, clearTimeout() {}
    } }] });
  });
  it('accepts context only from matching parent window, origin and nonce', () => {
    const ingress = TestBed.inject(SystemAssistantIngress); ingress.start();
    const data = { type: 'TDW_SYSTEM_ASSISTANT_CONTEXT', protocolVersion: 1, nonce, contextId: 'context_12345678',
      context: { origin: 'http://127.0.0.1:8099', pathname: '/tickets/CRM-101', url: 'http://127.0.0.1:8099/tickets/CRM-101?tab=main#row', title: 'CRM demo', capturedAt: '2026-11-20T12:00:00Z' } };
    listener?.({ source: parentWindow, origin: 'https://wrong.example', data } as unknown as MessageEvent);
    expect(ingress.context()).toBeNull();
    listener?.({ source: null, origin: 'http://127.0.0.1:8099', data } as unknown as MessageEvent);
    expect(ingress.context()).toBeNull();
    listener?.({ source: parentWindow, origin: 'http://127.0.0.1:8099', data } as unknown as MessageEvent);
    expect(ingress.context()?.pathname).toBe('/tickets/CRM-101'); expect(ingress.status()).toBe('received'); expect(received.length).toBe(2);
  });
  it('rejects a popup opener instead of treating it as an embedded parent', () => {
    const browser = TestBed.inject(SYSTEM_ASSISTANT_WINDOW) as unknown as {parent: unknown; opener: unknown};
    browser.parent = browser; browser.opener = parentWindow;
    const ingress = TestBed.inject(SystemAssistantIngress); ingress.start();
    expect(ingress.status()).toBe('invalid'); expect(received).toHaveLength(0);
  });
  it('resolves a send only with the matching response from the connected parent', async () => {
    const ingress = TestBed.inject(SystemAssistantIngress); ingress.start();
    const origin = 'http://127.0.0.1:8099';
    const page = {origin, pathname: '/cases', url: origin + '/cases?owner=one#first', title: 'Cases', capturedAt: '2026-11-20T12:00:00Z'};
    const deliver = (data: unknown, source: unknown = parentWindow) =>
      listener?.({source, origin, data} as MessageEvent);
    deliver({type: 'TDW_SYSTEM_ASSISTANT_CONTEXT', protocolVersion: 1, nonce, contextId: 'initial_page', context: page});
    const response = firstValueFrom(ingress.currentPage());
    const {contextId} = received.at(-1) as {contextId: string};
    const update = {type: 'TDW_SYSTEM_ASSISTANT_UPDATE', protocolVersion: 1, nonce, contextId, context: page};
    deliver(update, null); deliver({...update, nonce: 'wrong'}); deliver({...update, contextId: 'unrelated_push'});
    const current = {...page, url: origin + '/cases?owner=two#second', title: 'New title'};
    deliver({...update, context: current});
    expect(await response).toEqual(current); expect(ingress.context()).toEqual(current);
    ingress.ngOnDestroy(); expect(listener).toBeUndefined();
  });
  it('rejects a mismatched nonce and notifies the checked parent without passing context data', () => {
    const ingress = TestBed.inject(SystemAssistantIngress); ingress.start();
    listener?.({source: parentWindow, origin: 'http://127.0.0.1:8099', data: {
      type: 'TDW_SYSTEM_ASSISTANT_CONTEXT', protocolVersion: 1, nonce: 'wrong', contextId: 'context_12345678', context: {}
    }} as unknown as MessageEvent);
    expect(ingress.status()).toBe('invalid'); expect(ingress.context()).toBeNull();
    expect(received[1]).toEqual({type: 'TDW_SYSTEM_ASSISTANT_ERROR', protocolVersion: 1, nonce, code: 'CONTEXT_INVALID'});
  });
  it('rejects payloads containing extra data', () => {
    const ingress = TestBed.inject(SystemAssistantIngress); ingress.start();
    listener?.({ source: parentWindow, origin: 'http://127.0.0.1:8099', data: {
      type: 'TDW_SYSTEM_ASSISTANT_CONTEXT', protocolVersion: 1, nonce, contextId: 'context_12345678',
      context: { origin: 'http://127.0.0.1:8099', pathname: '/', title: 'CRM', capturedAt: '2026-11-20T12:00:00Z', cookies: 'forbidden' }
    } } as unknown as MessageEvent);
    expect(ingress.context()).toBeNull(); expect(ingress.status()).toBe('invalid'); expect(listener).toBeUndefined();
  });
});
