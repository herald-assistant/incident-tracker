import { Component, input, output } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';

export interface AnalysisResultTabStatus {
  kind: 'loading' | 'info' | 'error';
  label: string;
  tooltip?: string;
}

export interface AnalysisResultTabItem {
  id: string;
  tabLabel: string;
  status?: AnalysisResultTabStatus;
}

@Component({
  selector: 'app-analysis-result-tabs',
  imports: [MatTabsModule, MatTooltipModule],
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
