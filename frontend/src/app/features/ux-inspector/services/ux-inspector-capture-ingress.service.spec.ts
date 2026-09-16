import { TestBed } from '@angular/core/testing';

import { UxInspectorCapture } from '../models/ux-inspector.models';
import {
  UX_INSPECTOR_WINDOW,
  UxInspectorCaptureIngressService
} from './ux-inspector-capture-ingress.service';

describe('UxInspectorCaptureIngressService', () => {
  it('clears the fragment and accepts one exact-origin capture v3', () => {
    const harness = createWindowHarness();
    const service = createService(harness.window);

    service.start();

    expect(harness.replaceState).toHaveBeenCalledWith(null, '', '/ux-inspector?localRunId=crm-run');
    expect(harness.posted[0]).toEqual({
      message: {
        type: 'TDW_UX_INSPECTOR_READY',
        protocolVersion: 3,
        nonce: VALID_NONCE
      },
      targetOrigin: 'https://crm.example.com'
    });

    const capture = captureFixture();
    harness.dispatch('https://crm.example.com', harness.opener, {
      type: 'TDW_UX_INSPECTOR_CAPTURE',
      protocolVersion: 3,
      nonce: VALID_NONCE,
      captureId: capture.captureId,
      capture
    });

    expect(service.capture()).toEqual(capture);
    expect(service.status()).toBe('received');
    expect(harness.posted.at(-1)).toEqual({
      message: {
        type: 'TDW_UX_INSPECTOR_RECEIVED',
        protocolVersion: 3,
        nonce: VALID_NONCE,
        captureId: capture.captureId
      },
      targetOrigin: 'https://crm.example.com'
    });
    expect(harness.listener).toBeNull();
  });

  it('ignores a foreign source and rejects a nonce mismatch from the expected source', () => {
    const harness = createWindowHarness();
    const service = createService(harness.window);
    service.start();
    const capture = captureFixture();

    harness.dispatch('https://evil.example.com', harness.opener, {
      type: 'TDW_UX_INSPECTOR_CAPTURE', protocolVersion: 3, nonce: VALID_NONCE,
      captureId: capture.captureId, capture
    });
    expect(service.status()).toBe('waiting');
    expect(service.capture()).toBeNull();

    harness.dispatch('https://crm.example.com', harness.opener, {
      type: 'TDW_UX_INSPECTOR_CAPTURE', protocolVersion: 3, nonce: 'n_invalid_invalid_invalid_123',
      captureId: capture.captureId, capture
    });
    expect(service.status()).toBe('invalid');
    expect(service.capture()).toBeNull();
    expect(harness.posted.at(-1)?.message).toMatchObject({
      type: 'TDW_UX_INSPECTOR_ERROR', code: 'HANDSHAKE_MISMATCH'
    });
  });

  it('rejects unknown capture fields and oversized payloads without persistence', () => {
    const harness = createWindowHarness();
    const service = createService(harness.window);
    service.start();
    const capture = { ...captureFixture(), unknown: 'rejected' };

    harness.dispatch('https://crm.example.com', harness.opener, {
      type: 'TDW_UX_INSPECTOR_CAPTURE', protocolVersion: 3, nonce: VALID_NONCE,
      captureId: capture.captureId, capture
    });

    expect(service.status()).toBe('invalid');
    expect(service.capture()).toBeNull();
    expect(JSON.stringify(harness.window)).not.toContain('localStorage');
  });

  it('fails closed when opener is unavailable', () => {
    const harness = createWindowHarness();
    harness.window.opener = null;
    const service = createService(harness.window);
    service.start();

    expect(service.status()).toBe('invalid');
    expect(service.error()).toContain('popup/COOP');
    expect(harness.posted).toHaveLength(0);
  });
});

const VALID_NONCE = 'n_0123456789abcdefghijklmnop';

function createService(windowValue: Window): UxInspectorCaptureIngressService {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      UxInspectorCaptureIngressService,
      { provide: UX_INSPECTOR_WINDOW, useValue: windowValue }
    ]
  });
  return TestBed.inject(UxInspectorCaptureIngressService);
}

function createWindowHarness() {
  let listener: ((event: MessageEvent<unknown>) => void) | null = null;
  const posted: Array<{ message: Record<string, unknown>; targetOrigin: string }> = [];
  const opener = {
    postMessage(message: Record<string, unknown>, targetOrigin: string) {
      posted.push({ message, targetOrigin });
    }
  };
  const replaceState = vi.fn();
  const fakeWindow = {
    location: {
      hash: `#nonce=${VALID_NONCE}&sourceOrigin=https%3A%2F%2Fcrm.example.com`,
      pathname: '/ux-inspector',
      search: '?localRunId=crm-run'
    },
    history: { state: null, replaceState },
    opener,
    addEventListener(type: string, callback: (event: MessageEvent<unknown>) => void) {
      if (type === 'message') listener = callback;
    },
    removeEventListener(type: string, callback: (event: MessageEvent<unknown>) => void) {
      if (type === 'message' && listener === callback) listener = null;
    },
    setTimeout: vi.fn(() => 1),
    clearTimeout: vi.fn()
  } as unknown as Window;
  return {
    window: fakeWindow,
    opener,
    posted,
    replaceState,
    get listener() { return listener; },
    dispatch(origin: string, source: object, data: Record<string, unknown>) {
      listener?.({ origin, source, data } as unknown as MessageEvent<unknown>);
    }
  };
}

function captureFixture(): UxInspectorCapture {
  return {
    schema: 'tdw.ux-inspector-capture', version: 3, captureId: 'cap_crm_contact_save',
    capturedAt: '2026-09-15T10:00:00Z',
    captureProfile: 'ELEMENT_CONTEXT',
    page: { origin: 'https://crm.example.com', path: '/contacts/new', title: 'CRM', language: 'pl', queryParameterNames: [] },
    target: {
      tag: 'button', role: 'button', accessibleName: 'Zapisz kontakt', text: 'Zapisz kontakt',
      domFingerprint: {
        stableAttributes: { 'data-testid': 'contact-save' },
        selectorCandidates: ['button[data-testid="contact-save"]'],
        componentBoundaryTags: [], labelFor: null
      },
      state: { disabled: true, ariaDisabled: false, readOnly: false, required: false, invalid: false, checked: null, expanded: null, hidden: false },
      bounds: { x: 20, y: 40, width: 180, height: 42 }
    },
    ancestors: [],
    formSnapshot: null,
    traversal: { observedDepth: 2, emittedNodeCount: 1, omittedNodeCount: 1, reachedDocumentRoot: true },
    signals: { shadowBoundaryCount: 0, frame: 'TOP_LEVEL', redactions: [] },
    limits: [],
    client: { name: 'TDW UX Inspector', version: '3.0.0', featureId: 'ux-inspector' }
  };
}
