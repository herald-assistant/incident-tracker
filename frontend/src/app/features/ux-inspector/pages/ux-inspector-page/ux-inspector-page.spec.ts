import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { UxInspectorCapture } from '../../models/ux-inspector.models';
import { UX_INSPECTOR_WINDOW } from '../../services/ux-inspector-capture-ingress.service';
import { UxInspectorPageComponent } from './ux-inspector-page';

describe('UxInspectorPageComponent', () => {
  it('renders transferred target and a keyboard-accessible focused form without a manual fallback', async () => {
    const harness = createWindowHarness();
    const queryParamMap = new BehaviorSubject(convertToParamMap({}));
    await TestBed.configureTestingModule({
      imports: [UxInspectorPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: UX_INSPECTOR_WINDOW, useValue: harness.window },
        { provide: ActivatedRoute, useValue: { queryParamMap } },
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(UxInspectorPageComponent);
    const http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    harness.dispatch(captureFixture());
    http.expectOne('/api/ux-inspector/input-options').flush({
      featureId: 'ux-inspector', systems: [], configurationFindings: []
    });
    http.expectOne('/api/analysis/ai/options').flush({
      defaultModel: '', defaultReasoningEffort: '', defaultReasoningEfforts: [], models: []
    });
    await fixture.whenStable();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const main = page.querySelector<HTMLElement>('main[aria-label="UX Inspector"]');
    expect(main).not.toBeNull();
    expect(page.querySelector('#uxInspectorTitle')?.textContent).toContain('Zapytaj o wskazany element');
    expect(page.textContent).toContain('Zapisz kontakt');
    expect(page.textContent).toContain('diagnostyka formularza');
    expect(page.textContent).toContain('customer@example.test');
    expect(page.textContent).toContain('SENSITIVE_TYPE');
    expect(page.textContent).not.toContain('Wklej capture');
    expect(page.querySelector('.ux-inspector-read-only')).toBeNull();
    expect(page.querySelector('a[href="/browser-tools/install.html"]')).toBeNull();
    expect(page.querySelector('textarea')?.closest('label')?.textContent).toContain('Pytanie lub polecenie');
    const modelControl = Array.from(page.querySelectorAll<HTMLElement>('.ux-inspector-select'))
      .find((element) => element.textContent?.includes('Model AI'));
    const question = page.querySelector('textarea');
    expect(modelControl).toBeDefined();
    expect(modelControl!.compareDocumentPosition(question as Node) & Node.DOCUMENT_POSITION_FOLLOWING)
      .toBeTruthy();
    expect(page.querySelector<HTMLInputElement>('input[type="file"]')?.getAttribute('aria-label'))
      .toBe('Importuj wynik UX Inspectora');
    expect(page.querySelector<HTMLButtonElement>('button.primary-button')?.disabled).toBe(true);
    expect(Array.from(page.querySelectorAll<HTMLButtonElement>('button'))
      .every((button) => Boolean(button.textContent?.trim() || button.getAttribute('aria-label')))).toBe(true);
    expect(harness.replaceState).toHaveBeenCalledWith(null, '', '/ux-inspector');
    expect(harness.posted.at(-1)?.message).toMatchObject({
      type: 'TDW_UX_INSPECTOR_RECEIVED', captureId: 'cap_crm_contact_save'
    });

    fixture.componentInstance.facade.viewCatalog.set({
      systemId: 'crm-agent-portal', systemLabel: 'CRM Agent Portal',
      sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' }, status: 'READY',
      views: [{ viewId: 'crm-contact-create', label: 'Nowy kontakt', routePattern: '/contacts/new',
        componentSelectors: ['crm-contact-create'], status: 'READY', limitations: [] }],
      diagnostics: [], limitations: []
    });
    fixture.componentInstance.facade.selectedViewId.set('crm-contact-create');
    fixture.componentInstance.facade.viewMatchedFromCapture.set(true);
    fixture.detectChanges();
    expect(page.textContent).toContain('dopasowano z capture');

    const browserToolsButton = Array.from(page.querySelectorAll<HTMLButtonElement>('button'))
      .find((button) => button.textContent?.includes('Browser Tools'));
    browserToolsButton?.click();
    fixture.detectChanges();
    const dialog = page.querySelector<HTMLElement>('[role="dialog"]');
    const bookmarklet = dialog?.querySelector<HTMLAnchorElement>('.browser-tools-modal__bookmarklet');
    expect(dialog?.textContent).toContain('Dodaj Browser Tools do Chrome');
    expect(bookmarklet?.draggable).toBe(true);
    expect(bookmarklet?.getAttribute('href')).toMatch(/^javascript:/);
    expect(dialog?.textContent).not.toContain('Snippet');
    http.verify();
  });
});

const NONCE = 'n_0123456789abcdefghijklmnop';

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
      hash: `#nonce=${NONCE}&sourceOrigin=https%3A%2F%2Fcrm.example.com`,
      pathname: '/ux-inspector',
      search: ''
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
    posted,
    replaceState,
    dispatch(capture: UxInspectorCapture) {
      listener?.({
        origin: 'https://crm.example.com', source: opener,
        data: {
          type: 'TDW_UX_INSPECTOR_CAPTURE', protocolVersion: 1, nonce: NONCE,
          captureId: capture.captureId, capture
        }
      } as unknown as MessageEvent<unknown>);
    }
  };
}

function captureFixture(): UxInspectorCapture {
  return {
    schema: 'tdw.ux-inspector-capture', version: 1, captureId: 'cap_crm_contact_save',
    capturedAt: '2026-09-15T10:00:00Z',
    captureProfile: 'FORM_DIAGNOSTICS',
    page: { origin: 'https://crm.example.com', path: '/contacts/new', title: 'CRM', language: 'pl', queryParameterNames: [] },
    target: {
      tag: 'button', role: 'button', accessibleName: 'Zapisz kontakt', text: 'Zapisz kontakt',
      domFingerprint: { stableAttributes: { 'data-testid': 'contact-save' },
        selectorCandidates: ['button[data-testid="contact-save"]'], componentBoundaryTags: [], labelFor: null },
      state: { disabled: true, ariaDisabled: false, readOnly: false, required: false, invalid: false, checked: null, expanded: null, hidden: false },
      bounds: { x: 20, y: 40, width: 180, height: 42 }
    },
    ancestors: [],
    formSnapshot: {
      source: 'NEAREST_FORM', stableAttributes: { id: 'contact-form' }, selectorCandidates: ['#contact-form'],
      valid: false, observedControlCount: 2, emittedControlCount: 1, omittedControlCount: 0,
      controls: [{
        selectedTarget: false, tag: 'input', type: 'email', name: 'email', formControlName: 'email',
        accessibleName: 'E-mail', stableAttributes: { name: 'email' }, value: 'customer@example.test',
        valueTruncated: false, checked: null, selectedValues: [], selectedLabels: [], disabled: false,
        readOnly: false, required: true,
        validity: { valid: true, valueMissing: false, typeMismatch: false, patternMismatch: false,
          tooShort: false, tooLong: false, rangeUnderflow: false, rangeOverflow: false,
          stepMismatch: false, badInput: false, customError: false, validationMessage: null }
      }],
      submitters: [{ selectedTarget: true, tag: 'button', type: 'submit', accessibleName: 'Zapisz kontakt',
        stableAttributes: { 'data-testid': 'contact-save' }, disabled: true }],
      excludedControls: [{ tag: 'input', type: 'password', name: null, formControlName: null,
        reason: 'SENSITIVE_TYPE' }],
      valueCharacters: 21, valuesTruncated: false
    },
    traversal: { observedDepth: 2, emittedNodeCount: 1, omittedNodeCount: 1, reachedDocumentRoot: true },
    signals: { shadowBoundaryCount: 0, frame: 'TOP_LEVEL', redactions: [] },
    limits: [],
    client: { name: 'TDW UX Inspector', version: '1.0.0', featureId: 'ux-inspector' }
  };
}
