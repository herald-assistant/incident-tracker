import { Component, OnDestroy, computed, input, signal } from '@angular/core';

import {
  AnalysisReport,
  AnalysisReportMeta,
  AnalysisReportReference,
  AnalysisReportSection,
  AnalysisResultResponse
} from '../../core/models/analysis.models';
import { buildIncidentAnalysisResultMarkdown } from '../../core/utils/analysis-result-markdown.utils';
import { hasMeaningfulValue } from '../../core/utils/analysis-display.utils';
import { copyTextToClipboard } from '../../core/utils/clipboard.utils';
import { AnalysisReportMetaComponent } from '../analysis-report-meta/analysis-report-meta';
import { AnalysisReportSectionContentComponent } from '../analysis-report-section-content/analysis-report-section-content';

type AnalysisResultTab = 'FUNCTIONAL_ANALYSIS' | 'TECHNICAL_HANDOFF';

interface IncidentResultSectionDisplay {
  id: AnalysisResultTab;
  title: string;
  tabLabel: string;
  markdown: string;
  emptyText: string;
  meta: AnalysisReportMeta;
}

interface IncidentResultDisplay {
  title: string;
  subTitle: string;
  detectedProblem: string;
  confidence: string;
  sections: IncidentResultSectionDisplay[];
  appendix: AnalysisReportMeta;
}

const EMPTY_REPORT_META: AnalysisReportMeta = {
  references: [],
  visibilityLimits: [],
  openQuestions: [],
  gaps: [],
  confidence: '',
  warnings: []
};

@Component({
  selector: 'app-analysis-final-result',
  imports: [AnalysisReportMetaComponent, AnalysisReportSectionContentComponent],
  templateUrl: './analysis-final-result.html',
  styleUrl: './analysis-final-result.scss'
})
export class AnalysisFinalResultComponent implements OnDestroy {
  readonly result = input<AnalysisResultResponse | null>(null);
  readonly report = input<AnalysisReport | null>(null);
  readonly status = input('');

  protected readonly display = computed(() => incidentDisplay(this.result(), this.report()));
  protected readonly activeAnalysisTab = signal<AnalysisResultTab>('FUNCTIONAL_ANALYSIS');
  protected readonly resultCopied = signal(false);
  protected readonly copyError = signal('');
  protected readonly hasMeaningfulValue = hasMeaningfulValue;
  private resultCopyFeedbackHandle: number | null = null;

  protected selectAnalysisTab(tab: AnalysisResultTab): void {
    this.activeAnalysisTab.set(tab);
  }

  protected async copyResultMarkdown(): Promise<void> {
    const result = this.result();
    const report = this.report();
    if (!result && !report) {
      return;
    }

    const copied = await copyTextToClipboard(
      report ? buildIncidentReportMarkdown(report) : buildIncidentAnalysisResultMarkdown(result!)
    );
    if (!copied) {
      this.copyError.set('Nie udało się skopiować wyniku analizy do schowka.');
      return;
    }

    this.copyError.set('');
    this.resultCopied.set(true);
    this.clearResultCopyFeedback();
    this.resultCopyFeedbackHandle = window.setTimeout(() => {
      this.resultCopied.set(false);
      this.resultCopyFeedbackHandle = null;
    }, 1600);
  }

  ngOnDestroy(): void {
    this.clearResultCopyFeedback();
  }

  private clearResultCopyFeedback(): void {
    if (this.resultCopyFeedbackHandle === null) {
      return;
    }
    window.clearTimeout(this.resultCopyFeedbackHandle);
    this.resultCopyFeedbackHandle = null;
  }
}

function incidentDisplay(
  result: AnalysisResultResponse | null,
  report: AnalysisReport | null
): IncidentResultDisplay | null {
  if (report) {
    const functionalSection = reportSectionById(report.sections, 'FUNCTIONAL_ANALYSIS');
    const technicalSection = reportSectionById(report.sections, 'TECHNICAL_HANDOFF');
    return {
      title: cleanText(report.header) || result?.detectedProblem || 'Finalna analiza',
      subTitle: cleanText(report.subHeader),
      detectedProblem: cleanText(report.header) || result?.detectedProblem || '',
      confidence: cleanText(report.meta?.confidence) || result?.confidence || '',
      sections: [
        sectionDisplay(
          'FUNCTIONAL_ANALYSIS',
          'Rezultat analizy funkcjonalnej',
          'Brak wyniku analizy funkcjonalnej.',
          functionalSection,
          result?.functionalAnalysis
        ),
        sectionDisplay(
          'TECHNICAL_HANDOFF',
          'Rezultat analizy technicznej',
          'Brak wyniku analizy technicznej.',
          technicalSection,
          result?.technicalAnalysis
        )
      ],
      appendix: normalizedMeta(report.meta)
    };
  }

  if (!result) {
    return null;
  }

  return {
    title: result.detectedProblem || 'Finalna analiza',
    subTitle: '',
    detectedProblem: result.detectedProblem,
    confidence: result.confidence,
    sections: [
      {
        id: 'FUNCTIONAL_ANALYSIS',
        title: 'Functional analysis',
        tabLabel: 'Rezultat analizy funkcjonalnej',
        markdown: result.functionalAnalysis,
        emptyText: 'Brak wyniku analizy funkcjonalnej.',
        meta: EMPTY_REPORT_META
      },
      {
        id: 'TECHNICAL_HANDOFF',
        title: 'Technical handoff',
        tabLabel: 'Rezultat analizy technicznej',
        markdown: result.technicalAnalysis,
        emptyText: 'Brak wyniku analizy technicznej.',
        meta: EMPTY_REPORT_META
      }
    ],
    appendix: {
      ...EMPTY_REPORT_META,
      visibilityLimits: [...(result.visibilityLimits ?? [])]
    }
  };
}

function sectionDisplay(
  id: AnalysisResultTab,
  fallbackTitle: string,
  emptyText: string,
  section: AnalysisReportSection | null,
  fallbackMarkdown: string | null | undefined
): IncidentResultSectionDisplay {
  return {
    id,
    title: cleanText(section?.title) || fallbackTitle,
    tabLabel: fallbackTitle,
    markdown: cleanText(section?.markdown) || cleanText(fallbackMarkdown),
    emptyText,
    meta: normalizedMeta(section?.meta)
  };
}

function reportSectionById(
  sections: AnalysisReportSection[] | null | undefined,
  id: AnalysisResultTab
): AnalysisReportSection | null {
  return (
    (sections ?? []).find((section) => cleanText(section.id).toUpperCase() === id) ?? null
  );
}

function normalizedMeta(meta: AnalysisReportMeta | null | undefined): AnalysisReportMeta {
  return {
    references: [...(meta?.references ?? [])],
    visibilityLimits: cleanTextList(meta?.visibilityLimits),
    openQuestions: cleanTextList(meta?.openQuestions),
    gaps: cleanTextList(meta?.gaps),
    confidence: cleanText(meta?.confidence),
    warnings: cleanTextList(meta?.warnings)
  };
}

function buildIncidentReportMarkdown(report: AnalysisReport): string {
  const lines = [
    `# ${cleanText(report.header) || 'Incident analysis result'}`,
    cleanText(report.subHeader),
    cleanText(report.markdownSummary)
  ].filter(hasText);

  sortedSections(report.sections).forEach((section) => {
    lines.push('');
    lines.push(`## ${cleanText(section.title) || cleanText(section.id) || 'Section'}`);
    lines.push(cleanText(section.markdown));
    lines.push(...metaMarkdown(section.meta));
  });

  const appendix = metaMarkdown(report.meta, 'Report metadata');
  if (appendix.length > 0) {
    lines.push('');
    lines.push(...appendix);
  }

  return lines.filter((line, index, all) => line !== '' || all[index - 1] !== '').join('\n');
}

function metaMarkdown(meta: AnalysisReportMeta | null | undefined, title = 'Section metadata'): string[] {
  const parts = [
    bulletGroup('References', (meta?.references ?? []).map(referenceText)),
    bulletGroup('Visibility limits', meta?.visibilityLimits ?? []),
    bulletGroup('Open questions', meta?.openQuestions ?? []),
    bulletGroup('Gaps', meta?.gaps ?? []),
    bulletGroup('Warnings', meta?.warnings ?? [])
  ].flat();
  return parts.length > 0 ? [`### ${title}`, ...parts] : [];
}

function bulletGroup(title: string, values: string[]): string[] {
  const cleaned = cleanTextList(values);
  return cleaned.length > 0 ? [`#### ${title}`, ...cleaned.map((value) => `- ${value}`)] : [];
}

function referenceText(reference: AnalysisReportReference): string {
  return [reference.label, reference.type, reference.target, reference.description]
    .map(cleanText)
    .filter(hasText)
    .join(' | ');
}

function sortedSections(sections: AnalysisReportSection[] | null | undefined): AnalysisReportSection[] {
  return [...(sections ?? [])].sort((left, right) => {
    const leftOrder = typeof left.order === 'number' ? left.order : Number.MAX_SAFE_INTEGER;
    const rightOrder = typeof right.order === 'number' ? right.order : Number.MAX_SAFE_INTEGER;
    return leftOrder - rightOrder;
  });
}

function cleanTextList(values: string[] | null | undefined): string[] {
  return (values ?? []).map(cleanText).filter(hasText);
}

function cleanText(value: string | null | undefined): string {
  return typeof value === 'string' ? value.trim() : '';
}

function hasText(value: string | null | undefined): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}
