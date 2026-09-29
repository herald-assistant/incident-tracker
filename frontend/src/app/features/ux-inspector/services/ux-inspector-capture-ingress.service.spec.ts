import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { UxInspectorCaptureSnapshot } from '../models/ux-inspector.models';
import { UxInspectorCaptureIngressService } from './ux-inspector-capture-ingress.service';

describe('UxInspectorCaptureIngressService', () => {
  let service: UxInspectorCaptureIngressService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(), UxInspectorCaptureIngressService
    ] });
    service = TestBed.inject(UxInspectorCaptureIngressService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('loads one stored snapshot by ID and distinguishes empty available fields', () => {
    const snapshot = fixture('cap-crm-1');
    service.load('cap-crm-1');
    expect(service.status()).toBe('loading');
    const request = http.expectOne('/api/ux-inspector/captures/cap-crm-1');
    expect(request.request.method).toBe('GET');
    request.flush(snapshot);
    expect(service.capture()?.target.tag).toBe('button');
    expect(service.formStatus()).toBe('available');
    expect(service.formFields()).toEqual([]);
    expect(service.storeStatus()).toBe('available');
    expect(service.status()).toBe('received');
  });

  it('ignores a stale response after capture ID changes', () => {
    service.load('cap-crm-1');
    const old = http.expectOne('/api/ux-inspector/captures/cap-crm-1');
    service.load('cap-crm-2');
    const current = http.expectOne('/api/ux-inspector/captures/cap-crm-2');
    current.flush(fixture('cap-crm-2'));
    old.flush(fixture('cap-crm-1'));
    expect(service.snapshot()?.captureId).toBe('cap-crm-2');
  });

  it('reports expired memory and supports retry', () => {
    service.load('cap-crm-1');
    http.expectOne('/api/ux-inspector/captures/cap-crm-1').flush({}, { status: 404, statusText: 'Not Found' });
    expect(service.status()).toBe('invalid');
    expect(service.error()).toContain('nie istnieje');
    service.load('cap-crm-1');
    http.expectOne('/api/ux-inspector/captures/cap-crm-1').flush(fixture('cap-crm-1'));
    expect(service.status()).toBe('received');
  });

  it('rejects a snapshot returned for a different ID', () => {
    service.load('cap-crm-1');
    http.expectOne('/api/ux-inspector/captures/cap-crm-1').flush(fixture('cap-crm-2'));
    expect(service.status()).toBe('invalid');
    expect(service.capture()).toBeNull();
  });
});

function fixture(id: string): UxInspectorCaptureSnapshot {
  return {
    captureId: id,
    capture: { captureId: id, target: { tag: 'button' } } as UxInspectorCaptureSnapshot['capture'],
    formFields: { status: 'AVAILABLE', fields: [], omittedSensitiveFields: 0, reason: null },
    store: { status: 'AVAILABLE', state: {}, redactions: 0, reason: null }
  };
}
