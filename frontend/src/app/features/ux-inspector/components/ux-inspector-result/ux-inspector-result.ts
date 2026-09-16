import { Component, DestroyRef, computed, inject, input, signal } from '@angular/core';

import {
  AnalysisReport,
  AnalysisReportMeta,
  AnalysisReportReference
} from '../../../../core/models/analysis.models';
import { copyTextToClipboard } from '../../../../core/utils/clipboard.utils';
import { sanitizeFileNamePart } from '../../../../core/utils/json-file.utils';
import { AnalysisReportSectionContentComponent } from '../../../../components/analysis-report-section-content/analysis-report-section-content';
import { AnalysisResultHeaderComponent } from '../../../../components/analysis-result-header/analysis-result-header';
import { MarkdownContentComponent } from '../../../../components/markdown-content/markdown-content';
import { UxInspectorJobStatus, UxInspectorResultResponse } from '../../models/ux-inspector.models';

@Component({
  selector: 'app-ux-inspector-result',
  imports: [
    AnalysisReportSectionContentComponent,
    AnalysisResultHeaderComponent,
    MarkdownContentComponent
  ],
  templateUrl: './ux-inspector-result.html',
  styleUrl: './ux-inspector-result.scss'
})
export class UxInspectorResultComponent {
  private readonly destroyRef = inject(DestroyRef);
  private copyFeedbackHandle: number | null = null;

  readonly report = input.required<AnalysisReport>();
  readonly result = input<UxInspectorResultResponse | null>(null);
  readonly status = input<UxInspectorJobStatus>('COMPLETED');
  readonly copied = signal(false);
  readonly actionError = signal('');
  readonly answerSection = computed(() => this.report().sections[0] ?? null);
  readonly resultMeta = computed(() =>
    mergeReportMeta(this.report().meta, this.answerSection()?.meta)
  );

  constructor() {
    this.destroyRef.onDestroy(() => this.clearCopyFeedback());
  }

  protected async copyResult(): Promise<void> {
    const copied = await copyTextToClipboard(buildUxInspectorMarkdown(this.report()));
    if (!copied) {
      this.actionError.set('Nie udało się skopiować odpowiedzi UX Inspectora.');
      return;
    }
    this.actionError.set('');
    this.copied.set(true);
    this.clearCopyFeedback();
    this.copyFeedbackHandle = window.setTimeout(() => {
      this.copied.set(false);
      this.copyFeedbackHandle = null;
    }, 1600);
  }

  protected downloadResult(): void {
    try {
      const target = sanitizeFileNamePart(this.result()?.targetLabel || 'element');
      const revision = sanitizeFileNamePart(this.result()?.sourceRevision.revision || 'revision');
      downloadMarkdown(`ux-inspector-${target}-${revision}.md`, buildUxInspectorMarkdown(this.report()));
      this.actionError.set('');
    } catch {
      this.actionError.set('Nie udało się pobrać odpowiedzi UX Inspectora.');
    }
  }

  protected statusClass(): string {
    return this.status() === 'PARTIAL'
      ? 'status-pill status-pill--queued'
      : 'status-pill status-pill--done';
  }

  private clearCopyFeedback(): void {
    if (this.copyFeedbackHandle !== null) {
      window.clearTimeout(this.copyFeedbackHandle);
      this.copyFeedbackHandle = null;
    }
  }
}

function mergeReportMeta(
  reportMeta: AnalysisReportMeta | null | undefined,
  sectionMeta: AnalysisReportMeta | null | undefined
): AnalysisReportMeta {
  return {
    references: uniqueReferences([
      ...(sectionMeta?.references ?? []),
      ...(reportMeta?.references ?? [])
    ]),
    visibilityLimits: uniqueStrings([
      ...(sectionMeta?.visibilityLimits ?? []),
      ...(reportMeta?.visibilityLimits ?? [])
    ]),
    openQuestions: uniqueStrings([
      ...(sectionMeta?.openQuestions ?? []),
      ...(reportMeta?.openQuestions ?? [])
    ]),
    gaps: uniqueStrings([...(sectionMeta?.gaps ?? []), ...(reportMeta?.gaps ?? [])]),
    warnings: uniqueStrings([
      ...(sectionMeta?.warnings ?? []),
      ...(reportMeta?.warnings ?? [])
    ]),
    confidence: sectionMeta?.confidence?.trim() || reportMeta?.confidence?.trim() || ''
  };
}

function uniqueStrings(values: string[]): string[] {
  return [...new Set(values.map((value) => value.trim()).filter(Boolean))];
}

function uniqueReferences(values: AnalysisReportReference[]): AnalysisReportReference[] {
  const unique = new Map<string, AnalysisReportReference>();
  for (const reference of values) {
    const key = [reference.type, reference.label, reference.target, reference.description].join('\u0000');
    if (!unique.has(key)) {
      unique.set(key, reference);
    }
  }
  return [...unique.values()];
}

function buildUxInspectorMarkdown(report: AnalysisReport): string {
  const section = report.sections[0];
  return [
    `# ${report.header?.trim() || 'UX Inspector'}`,
    report.subHeader?.trim() ? `_${report.subHeader.trim()}_` : '',
    report.markdownSummary?.trim() ?? '',
    section?.markdown?.trim() ?? ''
  ]
    .filter(Boolean)
    .join('\n\n');
}

function downloadMarkdown(fileName: string, markdown: string): void {
  const blob = new Blob([markdown], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}
