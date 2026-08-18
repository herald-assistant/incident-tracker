import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';

import { AnalysisReport } from '../../../../core/models/analysis.models';
import { AnalysisReportSectionContentComponent } from '../../../../components/analysis-report-section-content/analysis-report-section-content';
import { UiExplorerResultResponse } from '../../models/ui-explorer.models';
import { UiExplorerResultComponent } from './ui-explorer-result';

describe('UiExplorerResultComponent', () => {
  let fixture: ComponentFixture<UiExplorerResultComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [UiExplorerResultComponent] }).compileComponents();
    fixture = TestBed.createComponent(UiExplorerResultComponent);
    fixture.componentRef.setInput('report', crmReport());
    fixture.componentRef.setInput('result', crmResult());
    fixture.componentRef.setInput('status', 'PARTIAL');
    fixture.componentRef.setInput(
      'sectionModes',
      CRM_SECTION_IDS.map((sectionId, index) => ({
        sectionId,
        mode: index % 2 === 0 ? 'DEEP' : 'COMPACT'
      }))
    );
    fixture.detectChanges();
  });

  it('renders all eight functional CRM report sections with right-aligned metadata and no dependency appendix', () => {
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('app-analysis-result-header')).not.toBeNull();
    expect(compiled.querySelector('app-analysis-result-header h3')?.textContent?.trim())
      .toBe('/contacts/new');
    expect(compiled.querySelector(
      'app-analysis-result-header .analysis-result-header > div:first-child > p:last-child'
    )?.textContent?.trim())
      .toBe('CrmContactCreateComponent');
    expect(compiled.textContent).not.toContain('main @ crm-revision-a1b2c3');
    expect(compiled.querySelectorAll('.ui-explorer-result__sections > section')).toHaveLength(8);
    expect(compiled.textContent).toContain('Raport jest częściowy');
    expect(compiled.textContent).toContain('Dynamiczna walidacja kontaktu CRM kontroluje zapis.');
    expect(compiled.textContent).not.toContain('Zależności przekrojowe');
    expect(compiled.textContent).not.toContain('Powiązane warunki i zależności');
    expect(fixture.debugElement.queryAll(By.directive(AnalysisReportSectionContentComponent)))
      .toHaveLength(8);
    expect(fixture.debugElement.queryAll(By.directive(AnalysisReportSectionContentComponent))
      .every((element) => (element.componentInstance as AnalysisReportSectionContentComponent).metaAlign() === 'end'))
      .toBe(true);
    expect(compiled.textContent).not.toContain('Materiał do przygotowania zmiany');
    expect(compiled.textContent).toContain('Ograniczenia, pytania i źródła');
    expect(compiled.textContent).not.toContain('preparedPrompt');
    expect(compiled.textContent).not.toContain('raw CRM source body');
  });

  it('copies the complete business-readable CRM report as Markdown', async () => {
    const originalClipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
    const writeText = vi.fn(async (_value: string): Promise<void> => undefined);
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText }
    });

    try {
      clickButtonContaining(fixture.nativeElement, 'Copy result');
      await fixture.whenStable();
      fixture.detectChanges();

      expect(writeText).toHaveBeenCalledTimes(1);
      const markdown = String(writeText.mock.calls[0]?.[0]);
      expect(markdown).toContain('# /contacts/new');
      expect(markdown).toContain('_CrmContactCreateComponent_');
      expect(markdown).toContain('## Formularze i reguły');
      expect(markdown).not.toContain('## Zależności przekrojowe');
      expect(markdown).not.toContain('## Materiał do przygotowania zmiany');
      expect(markdown).toContain('Reguły runtime słownika CRM nie były widoczne.');
      expect((fixture.nativeElement as HTMLElement).textContent).toContain('Copied');
    } finally {
      if (originalClipboard) {
        Object.defineProperty(navigator, 'clipboard', originalClipboard);
      } else {
        Reflect.deleteProperty(navigator, 'clipboard');
      }
    }
  });

  it('downloads a Markdown file named from the anonymized CRM screen and revision', async () => {
    const originalCreateObjectURL = URL.createObjectURL;
    const originalRevokeObjectURL = URL.revokeObjectURL;
    const createObjectURL = vi.fn((_blob: Blob) => 'blob:crm-ui-report');
    const revokeObjectURL = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectURL });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectURL });
    const originalCreateElement = document.createElement.bind(document);
    let downloadedFileName = '';
    const createElement = vi.spyOn(document, 'createElement').mockImplementation(((tagName: string) => {
      const element = originalCreateElement(tagName);
      if (tagName.toLowerCase() === 'a') {
        vi.spyOn(element as HTMLAnchorElement, 'click').mockImplementation(() => {
          downloadedFileName = (element as HTMLAnchorElement).download;
        });
      }
      return element;
    }) as typeof document.createElement);

    try {
      clickButtonContaining(fixture.nativeElement, 'Download Markdown');

      expect(createObjectURL).toHaveBeenCalledTimes(1);
      const blob = createObjectURL.mock.calls[0]?.[0] as Blob;
      expect(blob.type).toBe('text/markdown;charset=utf-8');
      expect(blob.size).toBeGreaterThan(100);
      expect(downloadedFileName).toBe('ui-explorer-crm-contact-create-crm-revision-a1b2c3.md');
    } finally {
      createElement.mockRestore();
      Object.defineProperty(URL, 'createObjectURL', {
        configurable: true,
        value: originalCreateObjectURL
      });
      Object.defineProperty(URL, 'revokeObjectURL', {
        configurable: true,
        value: originalRevokeObjectURL
      });
    }
  });
});

const CRM_SECTION_IDS = [
  'OVERVIEW',
  'NAVIGATION_AND_ACCESS',
  'SCREEN_STRUCTURE',
  'ACTIONS_AND_OUTCOMES',
  'FORMS_AND_RULES',
  'DATA_AND_SERVICES',
  'STATE_AND_SYNCHRONIZATION',
  'VARIANTS_AND_FAILURES'
] as const;

const CRM_SECTION_LABELS: Record<(typeof CRM_SECTION_IDS)[number], string> = {
  OVERVIEW: 'Cel i kontekst widoku',
  NAVIGATION_AND_ACCESS: 'Nawigacja i dostęp',
  SCREEN_STRUCTURE: 'Struktura widoku',
  ACTIONS_AND_OUTCOMES: 'Akcje i rezultaty',
  FORMS_AND_RULES: 'Formularze i reguły',
  DATA_AND_SERVICES: 'Dane i usługi',
  STATE_AND_SYNCHRONIZATION: 'Stan i synchronizacja',
  VARIANTS_AND_FAILURES: 'Warianty i sytuacje wyjątkowe'
};

function crmReport(): AnalysisReport {
  return {
    reportId: 'crm-ui-report-7c1',
    header: '/contacts/new',
    subHeader: 'CrmContactCreateComponent',
    markdownSummary: 'Widok umożliwia utworzenie syntetycznego kontaktu CRM.',
    sections: CRM_SECTION_IDS.map((sectionId, index) => ({
      id: sectionId,
      title: CRM_SECTION_LABELS[sectionId],
      order: index,
      markdown:
        sectionId === 'FORMS_AND_RULES'
          ? 'Dynamiczna walidacja kontaktu CRM kontroluje zapis.'
          : `Potwierdzony, syntetyczny opis CRM dla sekcji ${CRM_SECTION_LABELS[sectionId]}.`,
      meta: {
        references: [
          {
            type: 'source',
            label: `Crm${index + 1}View`,
            target: `crm-agent-portal:src/app/crm/view-${index + 1}.ts#L10-L20`,
            description: 'Anonymized CRM UI source'
          }
        ],
        visibilityLimits: sectionId === 'FORMS_AND_RULES'
          ? ['Reguły runtime słownika CRM nie były widoczne.']
          : [],
        openQuestions: [],
        gaps: sectionId === 'VARIANTS_AND_FAILURES' ? ['Section coverage: PARTIAL'] : [],
        confidence: sectionId === 'FORMS_AND_RULES' ? 'INFERRED' : 'CONFIRMED',
        warnings: []
      }
    })),
    meta: {
      references: [],
      visibilityLimits: ['Reguły runtime słownika CRM nie były widoczne.'],
      openQuestions: ['Która syntetyczna rola CRM zatwierdza kontakt?'],
      gaps: ['VARIANTS_AND_FAILURES: PARTIAL'],
      confidence: 'INFERRED',
      warnings: []
    }
  };
}

function crmResult(): UiExplorerResultResponse {
  return {
    screen: {
      systemId: 'crm-agent-portal',
      screenId: 'crm-contact-create',
      label: 'CrmContactCreateComponent',
      routePattern: '/contacts/new',
      navigationContext: 'Kontakty CRM > Nowy kontakt'
    },
    scenarioDescription: 'Syntetyczny scenariusz utworzenia kontaktu CRM.',
    sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' },
    functionalOverview: 'Widok umożliwia utworzenie syntetycznego kontaktu CRM.',
    sections: [],
    overallConfidence: 'INFERRED',
    visibilityLimits: ['Reguły runtime słownika CRM nie były widoczne.'],
    unresolvedQuestions: ['Która syntetyczna rola CRM zatwierdza kontakt?'],
    usage: null
  };
}

function clickButtonContaining(root: HTMLElement, text: string): void {
  const button = Array.from(root.querySelectorAll<HTMLButtonElement>('button')).find((candidate) =>
    candidate.textContent?.includes(text)
  );
  expect(button).toBeDefined();
  button?.click();
}
