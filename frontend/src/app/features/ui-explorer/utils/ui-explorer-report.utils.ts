import {
  AnalysisReport,
  AnalysisReportMeta,
  AnalysisReportReference,
  AnalysisReportSection
} from '../../../core/models/analysis.models';
import { sanitizeFileNamePart } from '../../../core/utils/json-file.utils';
import { UiExplorerResultResponse } from '../models/ui-explorer.models';

export function buildUiExplorerReportMarkdown(
  report: AnalysisReport,
  result: UiExplorerResultResponse | null
): string {
  const sections = [...(report.sections ?? [])]
    .sort(compareSections)
    .map(reportSectionMarkdown)
    .filter(Boolean);
  const dependencies = (result?.crossSectionDependencies ?? [])
    .map((dependency) =>
      normalizeMarkdown(
        `- ${sectionLabel(dependency.sourceSection)} → ${sectionLabel(dependency.targetSection)}: ${dependency.description}`
      )
    )
    .filter(Boolean);
  const changePreparation = changePreparationMarkdown(result);

  return [
    `# ${normalizeMarkdown(report.header) || 'UI Explorer result'}`,
    normalizeMarkdown(report.subHeader) ? `_${normalizeMarkdown(report.subHeader)}_` : '',
    normalizeMarkdown(report.markdownSummary),
    ...sections,
    dependencies.length > 0
      ? `## Zależności przekrojowe\n\n${dependencies.join('\n')}`
      : '',
    changePreparation,
    reportMetaMarkdown('Ograniczenia, pytania i źródła', report.meta)
  ]
    .filter(Boolean)
    .join('\n\n')
    .trim();
}

export function buildUiExplorerReportFileName(result: UiExplorerResultResponse | null): string {
  const screen = sanitizeFileNamePart(result?.screen?.screenId ?? 'screen');
  const revision = sanitizeFileNamePart(result?.sourceRevision?.revision ?? 'revision');
  return `ui-explorer-${screen}-${revision}.md`;
}

export function downloadUiExplorerMarkdown(fileName: string, markdown: string): void {
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

function reportSectionMarkdown(section: AnalysisReportSection): string {
  const title = normalizeMarkdown(section.title || section.id || 'Sekcja');
  const content = normalizeMarkdown(section.markdown);
  const meta = reportMetaMarkdown('Wiarygodność i widoczność sekcji', section.meta);
  return [`## ${title}`, content, meta].filter(Boolean).join('\n\n');
}

function reportMetaMarkdown(title: string, meta: AnalysisReportMeta | null | undefined): string {
  const groups = [
    bulletGroup('Źródła', (meta?.references ?? []).map(referenceText)),
    bulletGroup('Ograniczenia widoczności', meta?.visibilityLimits ?? []),
    bulletGroup('Otwarte pytania', meta?.openQuestions ?? []),
    bulletGroup('Luki', meta?.gaps ?? []),
    bulletGroup('Ostrzeżenia', meta?.warnings ?? []),
    normalizeMarkdown(meta?.confidence ?? '')
      ? `- Poziom pewności: ${normalizeMarkdown(meta?.confidence ?? '')}`
      : ''
  ].filter(Boolean);
  return groups.length > 0 ? [`### ${title}`, ...groups].join('\n\n') : '';
}

function bulletGroup(title: string, values: string[]): string {
  const items = values.map(normalizeMarkdown).filter(Boolean);
  return items.length > 0 ? [`**${title}**`, ...items.map((item) => `- ${item}`)].join('\n') : '';
}

function referenceText(reference: AnalysisReportReference): string {
  return [reference.label, reference.type, reference.target, reference.description]
    .map(normalizeMarkdown)
    .filter(Boolean)
    .join(' | ');
}

function changePreparationMarkdown(result: UiExplorerResultResponse | null): string {
  const summary = result?.changePreparationSummary;
  if (!summary) {
    return '';
  }
  return [
    '## Materiał do przygotowania zmiany',
    normalizeMarkdown(summary.changeGoal),
    bulletGroup('Prawdopodobne obszary wpływu', summary.likelyImpactAreas),
    bulletGroup('Decyzje do podjęcia', summary.decisionsRequired)
  ]
    .filter(Boolean)
    .join('\n\n');
}

function compareSections(left: AnalysisReportSection, right: AnalysisReportSection): number {
  const leftOrder = typeof left.order === 'number' ? left.order : Number.MAX_SAFE_INTEGER;
  const rightOrder = typeof right.order === 'number' ? right.order : Number.MAX_SAFE_INTEGER;
  return leftOrder - rightOrder;
}

function normalizeMarkdown(value: string | null | undefined): string {
  return String(value ?? '')
    .replace(/\r\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function sectionLabel(sectionId: string): string {
  return SECTION_LABELS[sectionId] ?? sectionId;
}

const SECTION_LABELS: Record<string, string> = {
  OVERVIEW: 'Cel i kontekst widoku',
  NAVIGATION_AND_ACCESS: 'Nawigacja i dostęp',
  SCREEN_STRUCTURE: 'Struktura widoku',
  ACTIONS_AND_OUTCOMES: 'Akcje i rezultaty',
  FORMS_AND_RULES: 'Formularze i reguły',
  DATA_AND_SERVICES: 'Dane i usługi',
  STATE_AND_SYNCHRONIZATION: 'Stan i synchronizacja',
  VARIANTS_AND_FAILURES: 'Warianty i sytuacje wyjątkowe'
};
