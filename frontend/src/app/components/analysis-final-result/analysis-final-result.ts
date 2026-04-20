import { Component, input } from '@angular/core';

import { AnalysisResultResponse } from '../../core/models/analysis.models';
import { hasMeaningfulValue } from '../../core/utils/analysis-display.utils';
import { MarkdownContentComponent } from '../markdown-content/markdown-content';

@Component({
  selector: 'app-analysis-final-result',
  imports: [MarkdownContentComponent],
  templateUrl: './analysis-final-result.html',
  styleUrl: './analysis-final-result.scss'
})
export class AnalysisFinalResultComponent {
  readonly result = input<AnalysisResultResponse | null>(null);
  readonly status = input('');

  protected readonly hasMeaningfulValue = hasMeaningfulValue;
}
