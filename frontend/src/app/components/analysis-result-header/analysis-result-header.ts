import { Component, input } from '@angular/core';

import { hasMeaningfulValue } from '../../core/utils/analysis-display.utils';
import { AnalysisShareDocument } from '../../core/utils/analysis-share.utils';
import { AnalysisShareMenuComponent } from '../analysis-share-menu/analysis-share-menu';

@Component({
  selector: 'app-analysis-result-header',
  imports: [AnalysisShareMenuComponent],
  templateUrl: './analysis-result-header.html',
  styleUrl: './analysis-result-header.scss'
})
export class AnalysisResultHeaderComponent {
  readonly title = input('Finalna analiza');
  readonly context = input('');
  readonly confidence = input('');
  readonly shareDocument = input<AnalysisShareDocument | null>(null);

  protected readonly hasMeaningfulValue = hasMeaningfulValue;
}
