import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';

import { AnalysisReport } from '../../../../core/models/analysis.models';
import { AnalysisReportSectionContentComponent } from '../../../../components/analysis-report-section-content/analysis-report-section-content';
import { UxInspectorResultComponent } from './ux-inspector-result';

describe('UxInspectorResultComponent', () => {
  let fixture: ComponentFixture<UxInspectorResultComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [UxInspectorResultComponent] }).compileComponents();
    fixture = TestBed.createComponent(UxInspectorResultComponent);
    fixture.componentRef.setInput('report', crmReport());
    fixture.componentRef.setInput('status', 'PARTIAL');
    fixture.detectChanges();
  });

  it('renders one answer and one right-aligned, deduplicated metadata block below it', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const sectionContent = fixture.debugElement.query(By.directive(AnalysisReportSectionContentComponent));

    expect(compiled.textContent).toContain('Pole właściciela ustawia osobę odpowiedzialną za kontakt CRM.');
    expect(compiled.querySelector('.analysis-result-header__partial-notice')?.getAttribute('aria-label'))
      .toContain('Odpowiedź jest częściowa');
    expect(compiled.textContent).not.toContain('wynik częściowy');
    expect(compiled.querySelector('.ux-inspector-result__notice')).toBeNull();
    expect(compiled.textContent).not.toContain('Meta raportu');
    expect(compiled.querySelectorAll('app-analysis-report-meta')).toHaveLength(1);
    expect((sectionContent.componentInstance as AnalysisReportSectionContentComponent).metaAlign()).toBe('end');
    const metadata = compiled.querySelector('app-analysis-report-meta')?.textContent ?? '';
    expect((metadata.match(/ContactEditorComponent/g) ?? [])).toHaveLength(1);
    expect((metadata.match(/Brak konfiguracji słownika CRM/g) ?? [])).toHaveLength(1);
  });
});

function crmReport(): AnalysisReport {
  const reference = {
    type: 'source',
    label: 'ContactEditorComponent',
    target: 'crm-agent-portal:src/app/contacts/contact-editor.ts#L20-L40',
    description: 'Syntetyczny komponent CRM'
  };
  return {
    reportId: 'ux-inspector-report-crm',
    header: 'Pole właściciela kontaktu',
    subHeader: 'ContactEditorComponent',
    markdownSummary: 'Pole należy do formularza kontaktu CRM.',
    sections: [{
      id: 'answer',
      title: 'Odpowiedź',
      order: 1,
      markdown: 'Pole właściciela ustawia osobę odpowiedzialną za kontakt CRM.',
      meta: {
        references: [reference],
        visibilityLimits: ['Brak konfiguracji słownika CRM'],
        openQuestions: [],
        gaps: ['Nie potwierdzono polityki uprawnień'],
        confidence: 'HIGH',
        warnings: ['Wartość runtime pochodzi z capture']
      }
    }],
    meta: {
      references: [reference],
      visibilityLimits: ['Brak konfiguracji słownika CRM'],
      openQuestions: [],
      gaps: [],
      confidence: 'HIGH',
      warnings: []
    }
  };
}
