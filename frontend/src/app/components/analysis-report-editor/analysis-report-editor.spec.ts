import { TestBed } from '@angular/core/testing';
import { AnalysisReport } from '../../core/models/analysis.models';
import { AnalysisReportEditorComponent } from './analysis-report-editor';

describe('AnalysisReportEditorComponent', () => {
  const meta = { references: [], visibilityLimits: [], openQuestions: [], gaps: [], confidence: '', warnings: [] };
  const report: AnalysisReport = {
    reportId: 'crm-report', header: 'CRM', subHeader: '', markdownSummary: 'Pierwsza wersja.',
    sections: [{ id: 'OVERVIEW', title: 'Przegląd', order: 0, markdown: 'Sprawa klienta.', meta }],
    meta, revisionSha256: 'abc123'
  };

  it('previews a draft, saves only changed parts with the expected revision and cancels without saving', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisReportEditorComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisReportEditorComponent);
    fixture.componentRef.setInput('report', report);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const save = vi.fn();
    const cancel = vi.fn();
    fixture.componentInstance.save.subscribe(save);
    fixture.componentInstance.cancel.subscribe(cancel);
    const textareas = element.querySelectorAll<HTMLTextAreaElement>('textarea');
    expect(textareas).toHaveLength(2);
    expect(element.querySelector('select')).toBeNull();
    expect(element.textContent).not.toContain('Treść Markdown');
    for (const textarea of textareas) expect(textarea.getAttribute('rows')).toBe('10');
    textareas[0].value = '**Nowa wersja CRM.**';
    textareas[0].dispatchEvent(new Event('input'));
    textareas[1].value = '**Zmieniona sekcja CRM.**';
    textareas[1].dispatchEvent(new Event('input'));
    fixture.detectChanges();
    const buttons = Array.from(element.querySelectorAll<HTMLButtonElement>('button'));
    buttons[1].click();
    fixture.detectChanges();
    expect(element.querySelectorAll('.analysis-report-editor__preview')).toHaveLength(2);
    expect(Array.from(element.querySelectorAll('.analysis-report-editor__preview strong')).map(node => node.textContent))
      .toEqual(['Nowa wersja CRM.', 'Zmieniona sekcja CRM.']);
    buttons[2].click();
    expect(save).toHaveBeenCalledWith({
      expectedRevisionSha256: 'abc123', markdownSummary: '**Nowa wersja CRM.**',
      sections: [{ sectionId: 'OVERVIEW', markdown: '**Zmieniona sekcja CRM.**' }]
    });
    buttons[0].click();
    expect(cancel).toHaveBeenCalledOnce();
    expect(report.markdownSummary).toBe('Pierwsza wersja.');
  });
});
