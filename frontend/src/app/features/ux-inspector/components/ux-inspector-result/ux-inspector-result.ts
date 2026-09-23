import { Component, computed, input } from '@angular/core';

import {
  AnalysisReport,
  AnalysisReportMeta,
  AnalysisReportReference
} from '../../../../core/models/analysis.models';
import { buildReportShareDocument } from '../../../../core/utils/analysis-share.utils';
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
  readonly report = input.required<AnalysisReport>();
  readonly result = input<UxInspectorResultResponse | null>(null);
  readonly status = input<UxInspectorJobStatus>('COMPLETED');
  protected readonly shareDocument = computed(() => {
    const target = sanitizeFileNamePart(this.result()?.targetLabel || 'element');
    const revision = sanitizeFileNamePart(this.result()?.sourceRevision.revision || 'revision');
    return buildReportShareDocument(this.report(), `ux-inspector-${target}-${revision}.md`);
  });
  readonly answerSection = computed(() => this.report().sections[0] ?? null);
  readonly resultMeta = computed(() =>
    mergeReportMeta(this.report().meta, this.answerSection()?.meta)
  );
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
