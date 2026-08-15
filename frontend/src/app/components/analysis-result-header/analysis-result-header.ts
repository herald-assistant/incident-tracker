import { Component, input, output } from '@angular/core';

import { hasMeaningfulValue } from '../../core/utils/analysis-display.utils';

@Component({
  selector: 'app-analysis-result-header',
  templateUrl: './analysis-result-header.html',
  styleUrl: './analysis-result-header.scss'
})
export class AnalysisResultHeaderComponent {
  readonly title = input('Finalna analiza');
  readonly context = input('');
  readonly confidence = input('');
  readonly copied = input(false);
  readonly copyAriaLabel = input('Kopiuj wynik analizy');
  readonly copiedAriaLabel = input('Skopiowano wynik analizy');
  readonly downloadVisible = input(false);
  readonly downloadAriaLabel = input('Pobierz wynik analizy jako Markdown');
  readonly copyRequested = output<void>();
  readonly downloadRequested = output<void>();

  protected readonly hasMeaningfulValue = hasMeaningfulValue;
}
