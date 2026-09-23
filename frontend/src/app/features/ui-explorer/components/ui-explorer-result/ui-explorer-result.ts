import { Component, computed, input } from '@angular/core';

import { AnalysisReport, AnalysisReportSection } from '../../../../core/models/analysis.models';
import { buildReportShareDocument } from '../../../../core/utils/analysis-share.utils';
import { AnalysisReportMetaComponent } from '../../../../components/analysis-report-meta/analysis-report-meta';
import { AnalysisReportSectionContentComponent } from '../../../../components/analysis-report-section-content/analysis-report-section-content';
import { AnalysisResultHeaderComponent } from '../../../../components/analysis-result-header/analysis-result-header';
import { MarkdownContentComponent } from '../../../../components/markdown-content/markdown-content';
import {
  UiExplorerJobStatus,
  UiExplorerResultResponse,
  UiExplorerSectionId,
  UiExplorerSectionMode,
  UiExplorerSectionModeAssignment
} from '../../models/ui-explorer.models';
import { buildUiExplorerReportFileName } from '../../utils/ui-explorer-report.utils';

@Component({
  selector: 'app-ui-explorer-result',
  imports: [
    AnalysisReportMetaComponent,
    AnalysisReportSectionContentComponent,
    AnalysisResultHeaderComponent,
    MarkdownContentComponent
  ],
  templateUrl: './ui-explorer-result.html',
  styleUrl: './ui-explorer-result.scss'
})
export class UiExplorerResultComponent {
  readonly report = input.required<AnalysisReport>();
  readonly result = input<UiExplorerResultResponse | null>(null);
  readonly status = input<UiExplorerJobStatus>('COMPLETED');
  readonly sectionModes = input<UiExplorerSectionModeAssignment[]>([]);
  readonly orderedSections = computed(() => [...this.report().sections].sort(compareSections));
  protected readonly shareDocument = computed(() =>
    buildReportShareDocument(this.report(), buildUiExplorerReportFileName(this.result()))
  );

  protected statusClass(): string {
    return this.status() === 'PARTIAL'
      ? 'status-pill status-pill--queued'
      : 'status-pill status-pill--done';
  }

  protected statusLabel(): string {
    return this.status() === 'PARTIAL' ? 'wynik częściowy' : 'wynik kompletny';
  }

  protected sectionMode(section: AnalysisReportSection): UiExplorerSectionMode | null {
    const sectionId = section.id as UiExplorerSectionId;
    return this.sectionModes().find((assignment) => assignment.sectionId === sectionId)?.mode ?? null;
  }

  protected sectionModeLabel(mode: UiExplorerSectionMode | null): string {
    switch (mode) {
      case 'DEEP':
        return 'pogłębiona';
      case 'COMPACT':
        return 'skrócona';
      case 'OFF':
        return 'pominięta';
      default:
        return '';
    }
  }

  protected sectionIcon(sectionId: string): string {
    return SECTION_ICONS[sectionId] ?? 'description';
  }

  protected hasText(value: string | null | undefined): boolean {
    return typeof value === 'string' && value.trim().length > 0;
  }

}

function compareSections(left: AnalysisReportSection, right: AnalysisReportSection): number {
  const leftOrder = typeof left.order === 'number' ? left.order : Number.MAX_SAFE_INTEGER;
  const rightOrder = typeof right.order === 'number' ? right.order : Number.MAX_SAFE_INTEGER;
  return leftOrder - rightOrder;
}

const SECTION_ICONS: Record<string, string> = {
  OVERVIEW: 'summarize',
  NAVIGATION_AND_ACCESS: 'route',
  SCREEN_STRUCTURE: 'dashboard',
  ACTIONS_AND_OUTCOMES: 'touch_app',
  FORMS_AND_RULES: 'dynamic_form',
  DATA_AND_SERVICES: 'database',
  STATE_AND_SYNCHRONIZATION: 'sync',
  VARIANTS_AND_FAILURES: 'rule'
};
