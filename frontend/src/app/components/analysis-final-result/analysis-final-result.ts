import { Component, OnDestroy, input, signal } from '@angular/core';

import { AnalysisResultResponse } from '../../core/models/analysis.models';
import { buildIncidentAnalysisResultMarkdown } from '../../core/utils/analysis-result-markdown.utils';
import { hasMeaningfulValue } from '../../core/utils/analysis-display.utils';
import { copyTextToClipboard } from '../../core/utils/clipboard.utils';
import { MarkdownContentComponent } from '../markdown-content/markdown-content';

type AnalysisResultTab = 'functional' | 'technical';

@Component({
  selector: 'app-analysis-final-result',
  imports: [MarkdownContentComponent],
  templateUrl: './analysis-final-result.html',
  styleUrl: './analysis-final-result.scss'
})
export class AnalysisFinalResultComponent implements OnDestroy {
  readonly result = input<AnalysisResultResponse | null>(null);
  readonly status = input('');

  protected readonly activeAnalysisTab = signal<AnalysisResultTab>('functional');
  protected readonly resultCopied = signal(false);
  protected readonly copyError = signal('');
  protected readonly hasMeaningfulValue = hasMeaningfulValue;
  private resultCopyFeedbackHandle: number | null = null;

  protected selectAnalysisTab(tab: AnalysisResultTab): void {
    this.activeAnalysisTab.set(tab);
  }

  protected async copyResultMarkdown(): Promise<void> {
    const result = this.result();
    if (!result) {
      return;
    }

    const copied = await copyTextToClipboard(buildIncidentAnalysisResultMarkdown(result));
    if (!copied) {
      this.copyError.set('Nie udało się skopiować wyniku analizy do schowka.');
      return;
    }

    this.copyError.set('');
    this.resultCopied.set(true);
    this.clearResultCopyFeedback();
    this.resultCopyFeedbackHandle = window.setTimeout(() => {
      this.resultCopied.set(false);
      this.resultCopyFeedbackHandle = null;
    }, 1600);
  }

  ngOnDestroy(): void {
    this.clearResultCopyFeedback();
  }

  private clearResultCopyFeedback(): void {
    if (this.resultCopyFeedbackHandle === null) {
      return;
    }
    window.clearTimeout(this.resultCopyFeedbackHandle);
    this.resultCopyFeedbackHandle = null;
  }
}
