import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AnalysisResultResponse } from '../../core/models/analysis.models';
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

  async function renderResult(): Promise<ComponentFixture<AnalysisFinalResultComponent>> {
    await TestBed.configureTestingModule({
      imports: [AnalysisFinalResultComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(AnalysisFinalResultComponent);
    fixture.componentRef.setInput('status', 'COMPLETED');
    fixture.componentRef.setInput('result', analysisResult());
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

  function clickButtonContaining(nativeElement: HTMLElement, text: string): void {
    const button = Array.from(nativeElement.querySelectorAll('button')).find((candidate) =>
      candidate.textContent?.includes(text)
    );
    expect(button).toBeTruthy();
    button?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  }
});
