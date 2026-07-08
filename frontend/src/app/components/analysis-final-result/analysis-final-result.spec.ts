import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AnalysisReport, AnalysisResultResponse } from '../../core/models/analysis.models';
import { AnalysisFinalResultComponent } from './analysis-final-result';

describe('AnalysisFinalResultComponent', () => {
  const originalClipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard');

  afterEach(() => {
    if (originalClipboard) {
      Object.defineProperty(navigator, 'clipboard', originalClipboard);
      return;
    }
    Reflect.deleteProperty(navigator, 'clipboard');
  });

  it('should copy incident result as two markdown sections', async () => {
    const writeText = vi.fn<(value: string) => Promise<void>>(() => Promise.resolve());
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText }
    });

    const fixture = await renderResult();
    clickButtonContaining(fixture.nativeElement, 'Copy result');
    await new Promise<void>((resolve) => window.setTimeout(resolve, 0));
    fixture.detectChanges();

    const copiedMarkdown = writeText.mock.calls[0]?.[0] as string | undefined;
    expect(writeText).toHaveBeenCalledTimes(1);
    expect(copiedMarkdown).toContain('## Analiza funkcjonalna');
    expect(copiedMarkdown).toContain('Proces biznesowy zatrzymuje się na walidacji.');
    expect(copiedMarkdown).toContain('## Analiza techniczna');
    expect(copiedMarkdown).toContain('Timeout powstaje w `CustomerClient`.');
    expect(copiedMarkdown).not.toContain('Detected problem');
    expect(copiedMarkdown).not.toContain('LOW_CONFIDENCE');
    expect(copiedMarkdown).not.toContain('Brak logów aplikacyjnych.');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Copied');
  });

  it('should render report sections in tabs and hide empty meta groups', async () => {
    const fixture = await renderResult(report());
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Rezultat analizy funkcjonalnej');
    expect(compiled.textContent).toContain('Rezultat analizy technicznej');
    expect(compiled.textContent).toContain('Proces biznesowy zatrzymuje się na walidacji.');
    expect(compiled.textContent).not.toContain('References');
    expect(compiled.textContent).toContain('Limits, questions and references');

    clickButtonContaining(compiled, 'Rezultat analizy technicznej');
    fixture.detectChanges();

    expect(compiled.textContent).toContain('Timeout powstaje w CustomerClient.');
    expect(compiled.textContent).toContain('References');
    expect(compiled.textContent).toContain('CustomerClient.call');
    expect(compiled.textContent).toContain('Visibility limits');
    expect(compiled.textContent).not.toContain('Open questions');
  });

  async function renderResult(
    reportValue: AnalysisReport | null = null
  ): Promise<ComponentFixture<AnalysisFinalResultComponent>> {
    await TestBed.configureTestingModule({
      imports: [AnalysisFinalResultComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(AnalysisFinalResultComponent);
    fixture.componentRef.setInput('status', 'COMPLETED');
    fixture.componentRef.setInput('result', analysisResult());
    fixture.componentRef.setInput('report', reportValue);
    fixture.detectChanges();
    return fixture;
  }

  function analysisResult(): AnalysisResultResponse {
    return {
      status: 'COMPLETED',
      correlationId: 'corr-1',
      environment: 'test',
      gitLabBranch: 'main',
      detectedProblem: 'Detected problem',
      affectedProcess: '',
      affectedBoundedContext: '',
      affectedTeam: '',
      functionalAnalysis: 'Proces biznesowy zatrzymuje się na walidacji.',
      technicalAnalysis: 'Timeout powstaje w `CustomerClient`.',
      confidence: 'LOW_CONFIDENCE',
      visibilityLimits: ['Brak logów aplikacyjnych.'],
      prompt: '',
      usage: null
    };
  }

  function report(): AnalysisReport {
    return {
      reportId: 'incident-report-1',
      header: 'Detected problem',
      subHeader: 'CRM / test',
      markdownSummary: '',
      sections: [
        {
          id: 'FUNCTIONAL_ANALYSIS',
          title: 'Functional analysis',
          order: 1,
          markdown: 'Proces biznesowy zatrzymuje się na walidacji.',
          meta: {
            references: [],
            visibilityLimits: [],
            openQuestions: [],
            gaps: [],
            confidence: 'medium',
            warnings: []
          }
        },
        {
          id: 'TECHNICAL_HANDOFF',
          title: 'Technical handoff',
          order: 2,
          markdown: 'Timeout powstaje w `CustomerClient`.',
          meta: {
            references: [
              {
                type: 'code',
                label: 'CustomerClient.call',
                target: 'src/main/java/CustomerClient.java:L42',
                description: 'Wywolanie downstream.'
              }
            ],
            visibilityLimits: ['Brak logów aplikacyjnych.'],
            openQuestions: [],
            gaps: [],
            confidence: 'medium',
            warnings: []
          }
        }
      ],
      meta: {
        references: [],
        visibilityLimits: ['Brak logów aplikacyjnych.'],
        openQuestions: [],
        gaps: [],
        confidence: 'medium',
        warnings: []
      }
    };
  }

  function clickButtonContaining(nativeElement: HTMLElement, text: string): void {
    const button = Array.from(nativeElement.querySelectorAll('button')).find((candidate) =>
      candidate.textContent?.includes(text)
    );
    expect(button).toBeTruthy();
    button?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  }
});
