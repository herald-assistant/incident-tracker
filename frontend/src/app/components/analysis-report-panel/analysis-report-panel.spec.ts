import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AnalysisReport } from '../../core/models/analysis.models';
import { AnalysisReportPanelComponent } from './analysis-report-panel';

describe('AnalysisReportPanelComponent', () => {
  it('should render report header, markdown sections and meta', async () => {
    const fixture = await renderReport(report());
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('DOWNSTREAM_TIMEOUT');
    expect(compiled.textContent).toContain('incident-report-1');
    expect(compiled.textContent).toContain('TECHNICAL_HANDOFF');
    expect(compiled.textContent).toContain('CustomerClient.call');
    expect(compiled.textContent).toContain('Brak logow downstream.');
    expect(compiled.textContent).toContain('Potwierdzic timeout w systemie zewnetrznym.');
    expect(compiled.querySelector('.analysis-report-panel__markdown strong')?.textContent).toBe('Timeout');
  });

  async function renderReport(
    value: AnalysisReport
  ): Promise<ComponentFixture<AnalysisReportPanelComponent>> {
    await TestBed.configureTestingModule({
      imports: [AnalysisReportPanelComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(AnalysisReportPanelComponent);
    fixture.componentRef.setInput('report', value);
    fixture.detectChanges();
    return fixture;
  }

  function report(): AnalysisReport {
    return {
      reportId: 'incident-report-1',
      header: 'DOWNSTREAM_TIMEOUT',
      subHeader: 'CRM / CRM Customer Context',
      markdownSummary: '**Timeout** downstream blokuje proces profilu klienta CRM.',
      sections: [
        {
          id: 'TECHNICAL_HANDOFF',
          title: 'Technical handoff',
          order: 1,
          markdown: 'Sprawdz `CustomerClient.call` i timeout klienta.',
          meta: {
            references: [
              {
                type: 'code',
                label: 'CustomerClient.call',
                target: 'src/main/java/CustomerClient.java:L42',
                description: 'Wywolanie downstream.'
              }
            ],
            visibilityLimits: ['Brak logow downstream.'],
            openQuestions: ['Potwierdzic timeout w systemie zewnetrznym.'],
            gaps: ['Brak trace po stronie integracji.'],
            confidence: 'medium',
            warnings: []
          }
        }
      ],
      meta: {
        references: [],
        visibilityLimits: ['Brak logow downstream.'],
        openQuestions: [],
        gaps: [],
        confidence: 'medium',
        warnings: []
      }
    };
  }
});
