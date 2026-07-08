import { Component, input } from '@angular/core';

import { AnalysisReportMeta, AnalysisReportReference } from '../../core/models/analysis.models';

type AnalysisReportMetaVariant = 'section' | 'appendix';
type AnalysisReportMetaAlign = 'start' | 'end';

@Component({
  selector: 'app-analysis-report-meta',
  templateUrl: './analysis-report-meta.html',
  styleUrl: './analysis-report-meta.scss'
})
export class AnalysisReportMetaComponent {
  readonly meta = input<AnalysisReportMeta | null | undefined>(null);
  readonly variant = input<AnalysisReportMetaVariant>('section');
  readonly align = input<AnalysisReportMetaAlign>('end');
  readonly appendixEyebrow = input('Appendix');
  readonly appendixTitle = input('Limits, questions and references');

  protected hasMeta(meta: AnalysisReportMeta | null | undefined = this.meta()): boolean {
    return Boolean(
      meta &&
        (this.hasItems(meta.references) ||
          this.hasItems(meta.visibilityLimits) ||
          this.hasItems(meta.openQuestions) ||
          this.hasItems(meta.gaps) ||
          this.hasItems(meta.warnings))
    );
  }

  protected hasItems<T>(items: readonly T[] | null | undefined): boolean {
    return Array.isArray(items) && items.length > 0;
  }

  protected referenceLabel(reference: AnalysisReportReference): string {
    return this.firstText(reference.label, reference.target, reference.type, reference.description, 'Reference');
  }

  protected referenceDetails(reference: AnalysisReportReference): string {
    const label = this.referenceLabel(reference);
    return [reference.type, reference.target, reference.description]
      .map((value) => this.cleanText(value))
      .filter((value) => this.hasText(value) && value !== label)
      .join(' | ');
  }

  protected metaCount(meta: AnalysisReportMeta | null | undefined = this.meta()): number {
    if (!meta) {
      return 0;
    }
    return (
      meta.references.length +
      meta.visibilityLimits.length +
      meta.openQuestions.length +
      meta.gaps.length +
      meta.warnings.length
    );
  }

  protected countLabel(count: number, _singular: string, plural: string): string {
    return `${count} ${plural}`;
  }

  protected hasText(value: string | null | undefined): value is string {
    return typeof value === 'string' && value.trim().length > 0;
  }

  private firstText(...values: Array<string | null | undefined>): string {
    return values.map((value) => this.cleanText(value)).find((value) => this.hasText(value)) ?? '';
  }

  private cleanText(value: string | null | undefined): string {
    return typeof value === 'string' ? value.trim() : '';
  }
}
