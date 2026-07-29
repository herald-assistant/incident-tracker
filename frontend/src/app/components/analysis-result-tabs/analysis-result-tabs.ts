import { Component, input, output } from '@angular/core';

export interface AnalysisResultTabItem {
  id: string;
  tabLabel: string;
}

@Component({
  selector: 'app-analysis-result-tabs',
  templateUrl: './analysis-result-tabs.html',
  styleUrl: './analysis-result-tabs.scss'
})
export class AnalysisResultTabsComponent {
  readonly tabs = input<readonly AnalysisResultTabItem[]>([]);
  readonly activeTabId = input('');
  readonly idPrefix = input('analysis');
  readonly ariaLabel = input('Sekcje wyniku analizy');
  readonly tabSelected = output<string>();
}
