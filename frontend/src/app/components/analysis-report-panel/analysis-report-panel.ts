import { Component, input } from '@angular/core';

import {
  AnalysisReport,
  AnalysisReportMeta,
  AnalysisReportReference,
  AnalysisReportSection
} from '../../core/models/analysis.models';
import { MarkdownContentComponent } from '../markdown-content/markdown-content';

@Component({
  selector: 'app-analysis-report-panel',
  imports: [MarkdownContentComponent],
  templateUrl: './analysis-report-panel.html',
  styleUrl: './analysis-report-panel.scss'
})
export class AnalysisReportPanelComponent {
  readonly report = input<AnalysisReport | null>(null);

  protected hasText(value: string | null | undefined): boolean {
    return typeof value === 'string' && value.trim().length > 0;
  }

  protected hasItems<T>(items: readonly T[] | null | undefined): boolean {
    return Array.isArray(items) && items.length > 0;
  }

  protected hasMeta(meta: AnalysisReportMeta | null | undefined): boolean {
    return Boolean(
      meta &&
        (this.hasItems(meta.references) ||
          this.hasItems(meta.visibilityLimits) ||
          this.hasItems(meta.openQuestions) ||
          this.hasItems(meta.gaps) ||
          this.hasItems(meta.warnings) ||
          this.hasText(meta.confidence))
    );
  }

  protected metaCount(meta: AnalysisReportMeta | null | undefined): number {
    if (!meta) {
      return 0;
    }
    return (
      meta.references.length +
      meta.visibilityLimits.length +
      meta.openQuestions.length +
      meta.gaps.length +
      meta.warnings.length +
      (this.hasText(meta.confidence) ? 1 : 0)
    );
  }

  protected reportTitle(report: AnalysisReport): string {
    return this.hasText(report.header) ? report.header : 'Raport analizy';
  }

  protected sectionTitle(section: AnalysisReportSection, index: number): string {
    if (this.hasText(section.title)) {
      return section.title;
    }
    if (this.hasText(section.id)) {
      return section.id;
    }
    return `Sekcja ${index + 1}`;
  }

  protected sectionOrdinal(section: AnalysisReportSection, index: number): number {
    return section.order ?? index + 1;
  }

  protected referenceLabel(reference: AnalysisReportReference, index: number): string {
    return (
      [reference.label, reference.target, reference.type].find((value) => this.hasText(value)) ??
      `Referencja ${index + 1}`
    );
  }

  protected referenceDetails(reference: AnalysisReportReference): string {
    return [reference.type, reference.target, reference.description]
      .filter((value) => this.hasText(value))
      .join(' | ');
  }
}
