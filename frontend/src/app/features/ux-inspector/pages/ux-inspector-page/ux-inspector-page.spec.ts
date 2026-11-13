import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { UxInspectorCaptureSnapshot } from '../../models/ux-inspector.models';
import { UxInspectorPageComponent } from './ux-inspector-page';

describe('UxInspectorPageComponent', () => {
  it.each(['unavailable', 'empty', 'available'])('loads capture and lazily previews HTTP history: %s', async (mode) => {
    const params = convertToParamMap({ captureId: 'cap_crm_contact_save' });
    const queryParamMap = new BehaviorSubject(params);
    const navigate = vi.fn(() => Promise.resolve(true));
    await TestBed.configureTestingModule({
      imports: [UxInspectorPageComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: ActivatedRoute, useValue: { queryParamMap, snapshot: { queryParamMap: params } } },
        { provide: Router, useValue: { navigate } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(UxInspectorPageComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    const snapshot = captureSnapshot();
    const fullUrl = 'https://crm.example.com/api/contacts?query=' + 'contact'.repeat(2000);
    if (mode !== 'unavailable') snapshot.httpHistory = { status: 'AVAILABLE', entries: [], capturedAt: '2026-11-11T10:00:00Z', source: 'https://crm.example.com', redactions: 1, reason: null };
    if (mode === 'available') snapshot.httpHistory.entries.push({ id: 7, startedAt: '2026-11-11T10:00:00Z', durationMs: 0, state: 'success',
      request: { method: 'GET', url: fullUrl, headers: {}, body: { format: 'unavailable', value: null, reason: 'SOURCE_OMITTED' } },
      response: { status: 200, headers: {}, body: { format: 'json', value: {
        memo: 'CRM_BODY'.repeat(1500),
        attachment: { $tdwType: 'File', name: 'crm-contacts.bin', type: 'application/octet-stream', size: 4, lastModified: 123456789,
          content: { encoding: 'base64', data: '//4AQQ==', byteLength: 4, redacted: false, reason: null } }
      }, reason: null } } });
    http.expectOne('/api/ux-inspector/captures/cap_crm_contact_save').flush(snapshot);
    http.expectOne('/api/ux-inspector/input-options').flush({
      featureId: 'ux-inspector', systems: [], configurationFindings: []
    });
    http.expectOne('/api/analysis/ai/options').flush({
      defaultModel: '', defaultReasoningEffort: '', defaultReasoningEfforts: [], models: []
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    fixture.componentInstance.facade.viewCatalog.set({
      systemId: 'crm-agent-portal', systemLabel: 'CRM Agent Portal',
      dataCollectedAt: '2026-08-15T10:30:00Z', sourceRevision: { branch: 'main' }, status: 'READY',
      views: [], diagnostics: [], limitations: []
    });
    fixture.detectChanges();
    expect(page.textContent).toContain('Data: 15.08.2026');
    expect(page.textContent).not.toContain('crm-revision-a1b2c3');
    expect(page.textContent).toContain('Zapisz kontakt');
    const preview = page.querySelector<HTMLDetailsElement>('.ux-inspector-capture-preview');
    expect(preview?.open).toBe(false);
    expect(preview?.querySelector('pre')).toBeNull();
    preview?.setAttribute('open', '');
    preview?.dispatchEvent(new Event('toggle'));
    fixture.detectChanges();
    expect(preview?.textContent).toContain('cap_crm_contact_save');
    expect(preview?.textContent).toContain('contact-save');
    expect(preview?.textContent).toContain('editable');
    const history = preview?.querySelector<HTMLDetailsElement>('.ux-inspector-http-history');
    expect(history?.open).toBe(false);
    expect(history?.querySelector('pre')).toBeNull();
    history?.setAttribute('open', '');
    history?.dispatchEvent(new Event('toggle'));
    fixture.detectChanges();
    if (mode === 'available') {
      expect(history?.textContent).toContain(fullUrl);
      expect(history?.textContent).toContain('Redakcje sekretów: 1');
      expect(history?.textContent).not.toContain('CRM_BODY');
      const entry = history?.querySelector<HTMLDetailsElement>('details');
      entry?.setAttribute('open', '');
      entry?.dispatchEvent(new Event('toggle'));
      fixture.detectChanges();
      expect(entry?.querySelector('pre')?.textContent).toContain('CRM_BODY'.repeat(1500));
      expect(entry?.querySelector('pre')?.textContent).toContain('crm-contacts.bin');
      expect(entry?.querySelector('pre')?.textContent).toContain('//4AQQ==');
      expect(entry?.querySelector('pre')?.textContent).toContain('123456789');
      expect(entry?.querySelector('pre')?.textContent).toContain('SOURCE_OMITTED');
    } else {
      expect(history?.textContent).toContain(mode === 'empty' ? 'Historia jest pusta.' : 'niedostępna');
    }

    fixture.componentInstance.facade.startedRunId.set('ux-job-1');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: expect.anything(),
      queryParams: { localRunId: 'ux-job-1', captureId: null },
      queryParamsHandling: 'merge', replaceUrl: true
    });
    http.verify();
  });
});

function captureSnapshot(): UxInspectorCaptureSnapshot {
  return {
    captureId: 'cap_crm_contact_save',
    capture: {
      schema: 'tdw.ux-inspector-capture', version: 1, captureId: 'cap_crm_contact_save',
      capturedAt: '2026-09-15T10:00:00Z', captureProfile: 'FORM_DIAGNOSTICS',
      page: { origin: 'https://crm.example.com', path: '/contacts/new', title: 'CRM', language: 'pl', queryParameterNames: [] },
      target: {
        tag: 'button', role: 'button', accessibleName: 'Zapisz kontakt', text: 'Zapisz kontakt',
        domFingerprint: { stableAttributes: { 'data-testid': 'contact-save' }, selectorCandidates: [],
          componentBoundaryTags: [], labelFor: null },
        state: { disabled: true, ariaDisabled: false, readOnly: false, required: false, invalid: false,
          checked: null, expanded: null, hidden: false },
        bounds: { x: 20, y: 40, width: 180, height: 42 }
      },
      ancestors: [], traversal: { observedDepth: 1, emittedNodeCount: 1, omittedNodeCount: 0, reachedDocumentRoot: true },
      signals: { shadowBoundaryCount: 0, frame: 'TOP_LEVEL', redactions: [] }, limits: [],
      client: { name: 'TDW UX Inspector', version: '1.7.0', featureId: 'ux-inspector' }
    },
    formFields: { status: 'AVAILABLE', fields: [], omittedSensitiveFields: 0, reason: null },
    store: { status: 'AVAILABLE', state: { contact: { editable: false } }, redactions: 0, reason: null },
    httpHistory: { status: 'UNAVAILABLE', entries: [], capturedAt: null, source: 'https://crm.example.com', redactions: 0, reason: 'GETTER_MISSING' }
  };
}
