import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';

import { AnalysisReport, AnalysisReportSection } from '../../../../core/models/analysis.models';
import { copyTextToClipboard } from '../../../../core/utils/clipboard.utils';
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
import {
  buildUiExplorerReportFileName,
  buildUiExplorerReportMarkdown,
  downloadUiExplorerMarkdown
} from '../../utils/ui-explorer-report.utils';

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
  private readonly destroyRef = inject(DestroyRef);
  private copyFeedbackHandle: number | null = null;

  readonly report = input.required<AnalysisReport>();
  readonly result = input<UiExplorerResultResponse | null>(null);
  readonly status = input<UiExplorerJobStatus>('COMPLETED');
  readonly sectionModes = input<UiExplorerSectionModeAssignment[]>([]);
  readonly resultCopied = signal(false);
  readonly actionError = signal('');
  readonly orderedSections = computed(() => [...this.report().sections].sort(compareSections));

  constructor() {
    this.destroyRef.onDestroy(() => this.clearCopyFeedback());
  }

  protected async copyReport(): Promise<void> {
    const copied = await copyTextToClipboard(
      buildUiExplorerReportMarkdown(this.report(), this.result())
    );
    if (!copied) {
      this.actionError.set('Nie udało się skopiować raportu UI Explorer do schowka.');
      return;
    }
    this.actionError.set('');
    this.resultCopied.set(true);
    this.clearCopyFeedback();
    this.copyFeedbackHandle = window.setTimeout(() => {
      this.resultCopied.set(false);
      this.copyFeedbackHandle = null;
    }, 1600);
  }

  protected downloadReport(): void {
    try {
      downloadUiExplorerMarkdown(
        buildUiExplorerReportFileName(this.result()),
        buildUiExplorerReportMarkdown(this.report(), this.result())
      );
      this.actionError.set('');
    } catch {
      this.actionError.set('Nie udało się pobrać raportu UI Explorer jako Markdown.');
    }
  }

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

  protected sectionLabel(sectionId: UiExplorerSectionId): string {
    return SECTION_LABELS[sectionId] ?? sectionId;
  }

  protected hasItems<T>(items: readonly T[] | null | undefined): boolean {
    return Array.isArray(items) && items.length > 0;
  }

  protected hasText(value: string | null | undefined): boolean {
    return typeof value === 'string' && value.trim().length > 0;
  }

  private clearCopyFeedback(): void {
    if (this.copyFeedbackHandle === null) {
      return;
    }
    window.clearTimeout(this.copyFeedbackHandle);
    this.copyFeedbackHandle = null;
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

const SECTION_LABELS: Record<UiExplorerSectionId, string> = {
  OVERVIEW: 'Cel i kontekst widoku',
  NAVIGATION_AND_ACCESS: 'Nawigacja i dostęp',
  SCREEN_STRUCTURE: 'Struktura widoku',
  ACTIONS_AND_OUTCOMES: 'Akcje i rezultaty',
  FORMS_AND_RULES: 'Formularze i reguły',
  DATA_AND_SERVICES: 'Dane i usługi',
  STATE_AND_SYNCHRONIZATION: 'Stan i synchronizacja',
  VARIANTS_AND_FAILURES: 'Warianty i sytuacje wyjątkowe'
};
