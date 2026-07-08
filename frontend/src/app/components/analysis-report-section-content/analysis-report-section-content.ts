import { Component, input } from '@angular/core';

import { AnalysisReportMeta } from '../../core/models/analysis.models';
import { AnalysisReportMetaComponent } from '../analysis-report-meta/analysis-report-meta';
import { MarkdownContentComponent } from '../markdown-content/markdown-content';

@Component({
  selector: 'app-analysis-report-section-content',
  imports: [AnalysisReportMetaComponent, MarkdownContentComponent],
  templateUrl: './analysis-report-section-content.html',
  styleUrl: './analysis-report-section-content.scss'
})
export class AnalysisReportSectionContentComponent {
  readonly markdown = input('');
  readonly emptyText = input('No confirmed details for this section.');
  readonly meta = input<AnalysisReportMeta | null | undefined>(null);
  readonly metaAlign = input<'start' | 'end'>('end');

  protected content(): string {
    const value = this.markdown();
    return value && value.trim().length > 0 ? value : this.emptyText();
  }
}
