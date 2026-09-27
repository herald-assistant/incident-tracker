import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';

import { UiExplorerPageContext } from '../models/ui-explorer.models';
import {
  UI_EXPLORER_WINDOW,
  UiExplorerPageContextIngressService
} from './ui-explorer-page-context-ingress.service';

describe('UiExplorerPageContextIngressService', () => {
  const nonce = 'n_0123456789abcdefghijkl';
  const sourceOrigin = 'https://crm.example.com';
  const opener = { postMessage: vi.fn() };
  let receive: ((event: MessageEvent<unknown>) => void) | undefined;
  const browserWindow = {
    location: {
      hash: `#nonce=${nonce}&sourceOrigin=${encodeURIComponent(sourceOrigin)}`,
      pathname: '/ui-explorer',
      search: ''
    },
    history: { state: null, replaceState: vi.fn() },
    opener,
    addEventListener: vi.fn((_type: string, listener: (event: MessageEvent<unknown>) => void) => { receive = listener; }),
    removeEventListener: vi.fn(),
    setTimeout: vi.fn(() => 17),
    clearTimeout: vi.fn()
  };

  beforeEach(() => {
    vi.clearAllMocks();
    receive = undefined;
    browserWindow.location.hash = `#nonce=${nonce}&sourceOrigin=${encodeURIComponent(sourceOrigin)}`;
    TestBed.configureTestingModule({
      providers: [
        UiExplorerPageContextIngressService,
        { provide: UI_EXPLORER_WINDOW, useValue: browserWindow }
      ]
    });
  });

  it('accepts a strict one-time context from the exact opener and clears the URL fragment', () => {
    const ingress = TestBed.inject(UiExplorerPageContextIngressService);
    ingress.start();
    expect(browserWindow.history.replaceState).toHaveBeenCalledWith(null, '', '/ui-explorer');
    expect(opener.postMessage).toHaveBeenCalledWith({
      type: 'TDW_UI_EXPLORER_READY', protocolVersion: 1, nonce
    }, sourceOrigin);

    const context = crmContext();
    receive?.(message('https://other.example.com', opener, context));
    receive?.(message(sourceOrigin, {}, context));
    expect(ingress.context()).toBeNull();
    receive?.(message(sourceOrigin, opener, context));
    expect(ingress.context()).toEqual(context);
    expect(opener.postMessage).toHaveBeenLastCalledWith({
      type: 'TDW_UI_EXPLORER_RECEIVED', protocolVersion: 1,
      nonce, contextId: context.contextId
    }, sourceOrigin);
    expect(browserWindow.removeEventListener).toHaveBeenCalled();
    ingress.dismiss();
    expect(ingress.context()).toBeNull();
  });

  it('rejects malformed context and reports the failure to the source', () => {
    const ingress = TestBed.inject(UiExplorerPageContextIngressService);
    ingress.start();
    receive?.(message(sourceOrigin, opener, {
      ...crmContext(), page: { ...crmContext().page, extra: 'secret' }
    }));
    expect(ingress.status()).toBe('invalid');
    expect(ingress.context()).toBeNull();
    expect(opener.postMessage).toHaveBeenLastCalledWith(expect.objectContaining({
      type: 'TDW_UI_EXPLORER_ERROR', code: 'CONTEXT_INVALID'
    }), sourceOrigin);
  });

  function message(origin: string, source: unknown, context: unknown): MessageEvent<unknown> {
    return { origin, source, data: {
      type: 'TDW_UI_EXPLORER_CONTEXT', protocolVersion: 1, nonce,
      contextId: crmContext().contextId, context
    } } as MessageEvent<unknown>;
  }

  function crmContext(): UiExplorerPageContext {
    return {
      schema: 'tdw.ui-explorer-page-context', version: 1,
      contextId: 'ctx_0123456789abcdef',
      page: {
        origin: sourceOrigin,
        path: '/contacts/:value',
        componentBoundaryTags: ['crm-contact-view', 'crm-shell']
      }
    };
  }
});
