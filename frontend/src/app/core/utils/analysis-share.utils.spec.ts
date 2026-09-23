import { AnalysisReport } from '../models/analysis.models';
import { buildReportShareDocument } from './analysis-share.utils';

describe('buildReportShareDocument', () => {
  it('keeps the displayed section order and adds metadata only in the extended variant', () => {
    const report = {
      reportId: 'crm-report',
      header: 'Analiza CRM',
      subHeader: 'Proces kontaktu',
      markdownSummary: 'Kontakt przechodzi walidację.',
      sections: [
        { id: 'SECOND', title: 'Krok drugi', order: 2, markdown: 'Zapis kontaktu.', meta: {
          references: [{ label: 'CRM-2', type: 'JIRA', target: 'https://jira.example.com/CRM-2', description: 'Zadanie' }],
          visibilityLimits: [], openQuestions: [], gaps: [], warnings: [], confidence: 'HIGH'
        } },
        { id: 'FIRST', title: 'Krok pierwszy', order: 1, markdown: 'Walidacja kontaktu.', meta: {
          references: [], visibilityLimits: ['Brak śladu runtime.'],
          openQuestions: ['Kto potwierdza kontakt?'], gaps: ['Nieznany timeout.'], warnings: [], confidence: 'MEDIUM'
        } }
      ],
      meta: { references: [], visibilityLimits: [], openQuestions: [], gaps: [], warnings: ['Wynik częściowy.'], confidence: 'MEDIUM' }
    } as AnalysisReport;

    const document = buildReportShareDocument(report, 'crm.md');
    expect(document.fileName).toBe('crm.md');
    expect(document.markdown.indexOf('## Krok pierwszy')).toBeLessThan(document.markdown.indexOf('## Krok drugi'));
    expect(document.markdown).toContain('Walidacja kontaktu.');
    expect(document.markdown).not.toContain('Brak śladu runtime.');
    expect(document.markdownWithMeta).toContain('Brak śladu runtime.');
    expect(document.markdownWithMeta).toContain('Kto potwierdza kontakt?');
    expect(document.markdownWithMeta).toContain('Nieznany timeout.');
    expect(document.markdownWithMeta).toContain('https://jira.example.com/CRM-2');
    expect(document.markdownWithMeta).toContain('Wynik częściowy.');
  });
});
