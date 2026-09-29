import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { UxInspectorCaptureSnapshot } from '../../models/ux-inspector.models';
import { UxInspectorPageComponent } from './ux-inspector-page';

describe('UxInspectorPageComponent', () => {
  it('loads capture by URL ID and keeps its full preview collapsed until requested', async () => {
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
    http.expectOne('/api/ux-inspector/captures/cap_crm_contact_save').flush(captureSnapshot());
    http.expectOne('/api/ux-inspector/input-options').flush({
      featureId: 'ux-inspector', systems: [], configurationFindings: []
    });
    http.expectOne('/api/analysis/ai/options').flush({
      defaultModel: '', defaultReasoningEffort: '', defaultReasoningEfforts: [], models: []
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
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
      schema: 'tdw.ux-inspector-capture', version: 3, captureId: 'cap_crm_contact_save',
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
      client: { name: 'TDW UX Inspector', version: '1.5.0', featureId: 'ux-inspector' }
    },
    formFields: { status: 'AVAILABLE', fields: [], omittedSensitiveFields: 0, reason: null },
    store: { status: 'AVAILABLE', state: { contact: { editable: false } }, redactions: 0, reason: null }
  };
}
