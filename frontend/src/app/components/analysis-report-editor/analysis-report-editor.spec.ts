import { TestBed } from '@angular/core/testing';
import { EditorView } from 'codemirror';
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
    const editors = element.querySelectorAll<HTMLElement>('app-markdown-source-editor');
    expect(editors).toHaveLength(2);
    expect(element.querySelector('select')).toBeNull();
    expect(element.textContent).not.toContain('Treść Markdown');
    for (const editor of editors) {
      expect(editor.querySelector('.cm-content')?.getAttribute('aria-labelledby')).toContain('report-edit-title-');
    }
    replaceMarkdown(editors[0], '**Nowa wersja CRM.**');
    replaceMarkdown(editors[1], '**Zmieniona sekcja CRM.**');
    fixture.detectChanges();
    const buttons = Array.from(element.querySelectorAll<HTMLButtonElement>('button'));
    buttons[1].click();
    fixture.detectChanges();
    expect(element.querySelectorAll('.analysis-report-editor__preview')).toHaveLength(2);
    expect(Array.from(element.querySelectorAll('.analysis-report-editor__preview strong')).map(node => node.textContent))
      .toEqual(['Nowa wersja CRM.', 'Zmieniona sekcja CRM.']);
    buttons[1].click();
    fixture.detectChanges();
    expect(EditorView.findFromDOM(element.querySelector('#report-edit-markdownSummary .cm-editor') as HTMLElement)
      ?.state.doc.toString()).toBe('**Nowa wersja CRM.**');
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

function replaceMarkdown(host: HTMLElement, markdown: string): void {
  const view = EditorView.findFromDOM(host.querySelector('.cm-editor') as HTMLElement)!;
  view.dispatch({ changes: { from: 0, to: view.state.doc.length, insert: markdown } });
}
