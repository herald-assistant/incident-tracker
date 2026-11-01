import { Component, computed, input } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';

import { AnalysisReport, AnalysisReportSection } from '../../core/models/analysis.models';
import { mergeReportMeta, orderedReportSections } from '../../core/utils/analysis-report.utils';
import { AnalysisReportMetaComponent } from '../analysis-report-meta/analysis-report-meta';
import { AnalysisReportSectionContentComponent } from '../analysis-report-section-content/analysis-report-section-content';
import { MarkdownContentComponent } from '../markdown-content/markdown-content';

@Component({
  selector: 'app-analysis-report-panel',
  imports: [NgTemplateOutlet, MarkdownContentComponent, AnalysisReportMetaComponent, AnalysisReportSectionContentComponent],
  templateUrl: './analysis-report-panel.html',
  styleUrl: './analysis-report-panel.scss'
})
export class AnalysisReportPanelComponent {
  readonly report = input<AnalysisReport | null>(null);
  readonly embedded = input(false);
  readonly showTechnicalIds = input(true);
  readonly appendixSectionId = input('');
  readonly appendixTitle = input('Ograniczenia, pytania i źródła');
  protected readonly orderedSections = computed(() => orderedReportSections(this.report(), this.appendixSectionId()));
  protected readonly sections = computed(() => this.orderedSections().filter(s => !this.appendixSectionId() || s.id !== this.appendixSectionId()));
  protected readonly appendix = computed(() => this.appendixSectionId() ? this.orderedSections().find(s => s.id === this.appendixSectionId()) : undefined);
  protected readonly appendixMeta = computed(() => mergeReportMeta(this.report()?.meta, this.appendix()?.meta));

  protected hasText(value: string | null | undefined): boolean {
    return typeof value === 'string' && value.trim().length > 0;
  }

  protected reportTitle(report: AnalysisReport): string {
    return this.hasText(report.header) ? report.header : 'Raport analizy';
  }

  protected sectionTitle(section: AnalysisReportSection, index: number): string {
    return section.title?.trim() || section.id?.trim() || `Sekcja ${index + 1}`;
  }

  protected sectionOrdinal(section: AnalysisReportSection, index: number): number {
    return section.order ?? index + 1;
  }
}
