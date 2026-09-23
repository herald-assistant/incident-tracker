import { Component, computed, input, signal } from '@angular/core';

import {
  AnalysisReport,
  AnalysisReportMeta,
  AnalysisReportSection,
  AnalysisResultResponse
} from '../../core/models/analysis.models';
import { buildIncidentAnalysisResultMarkdown } from '../../core/utils/analysis-result-markdown.utils';
import { hasMeaningfulValue } from '../../core/utils/analysis-display.utils';
import { buildAnalysisShareDocument, buildReportShareDocument, markdownList } from '../../core/utils/analysis-share.utils';
import { AnalysisReportMetaComponent } from '../analysis-report-meta/analysis-report-meta';
import { AnalysisReportSectionContentComponent } from '../analysis-report-section-content/analysis-report-section-content';
import { AnalysisResultHeaderComponent } from '../analysis-result-header/analysis-result-header';
import { AnalysisResultTabsComponent } from '../analysis-result-tabs/analysis-result-tabs';

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
  imports: [
    AnalysisReportMetaComponent,
    AnalysisReportSectionContentComponent,
    AnalysisResultHeaderComponent,
    AnalysisResultTabsComponent
  ],
  templateUrl: './analysis-final-result.html',
  styleUrl: './analysis-final-result.scss'
})
export class AnalysisFinalResultComponent {
  readonly result = input<AnalysisResultResponse | null>(null);
  readonly report = input<AnalysisReport | null>(null);
  readonly status = input('');

  protected readonly display = computed(() => incidentDisplay(this.result(), this.report()));
  protected readonly activeAnalysisTab = signal<AnalysisResultTab>('FUNCTIONAL_ANALYSIS');
  protected readonly shareDocument = computed(() => {
    const report = this.report();
    if (report) return buildReportShareDocument(report, 'incident-analysis.md');
    const result = this.result();
    if (!result) return null;
    const content = [`# ${result.detectedProblem || 'Wynik analizy incydentu'}`, buildIncidentAnalysisResultMarkdown(result)].join('\n\n');
    return buildAnalysisShareDocument('incident-analysis.md', content,
      markdownList('Limity widoczności', result.visibilityLimits ?? []));
  });
  protected readonly hasMeaningfulValue = hasMeaningfulValue;

  protected selectAnalysisTab(tab: string): void {
    if (tab === 'FUNCTIONAL_ANALYSIS' || tab === 'TECHNICAL_HANDOFF') {
      this.activeAnalysisTab.set(tab);
    }
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

function cleanTextList(values: string[] | null | undefined): string[] {
  return (values ?? []).map(cleanText).filter((value) => value.length > 0);
}

function cleanText(value: string | null | undefined): string {
  return typeof value === 'string' ? value.trim() : '';
}
