import { Component, computed, input } from '@angular/core';

import { AnalysisJobResponse } from '../../core/models/analysis.models';
import {
  bannerClassName,
  buildJobBannerMessage,
  buildJobTitle,
  formatStatus,
  statusClassName,
  valueOrFallback
} from '../../core/utils/analysis-display.utils';

@Component({
  selector: 'app-analysis-overview-card',
  imports: [],
  templateUrl: './analysis-overview-card.html',
  styleUrl: './analysis-overview-card.scss'
})
export class AnalysisOverviewCardComponent {
  readonly job = input.required<AnalysisJobResponse>();

  protected readonly jobTitle = computed(() => buildJobTitle(this.job()));
  protected readonly statusLabel = computed(() => formatStatus(this.job().status));
  protected readonly statusClass = computed(() => statusClassName(this.job().status));
  protected readonly bannerClass = computed(() => bannerClassName(this.job().status));
  protected readonly bannerMessage = computed(() => buildJobBannerMessage(this.job()));
  protected readonly metaItems = computed(() => [
    { label: 'Analysis ID', value: valueOrFallback(this.job().analysisId) },
    { label: 'Correlation ID', value: this.job().correlationId || 'n/a' },
    { label: 'Środowisko', value: valueOrFallback(this.job().environment) },
    { label: 'Gałąź GitLab', value: valueOrFallback(this.job().gitLabBranch) }
  ]);
}
