import {
  AnalysisReport,
  AnalysisReportMeta,
  AnalysisReportReference
} from '../models/analysis.models';

export interface AnalysisShareDocument {
  fileName: string;
  markdown: string;
  markdownWithMeta: string;
}

export function buildAnalysisShareDocument(
  fileName: string,
  markdown: string,
  metaMarkdown = ''
): AnalysisShareDocument {
  const content = markdown.trim();
  const meta = metaMarkdown.trim();
  return {
    fileName: fileName.endsWith('.md') ? fileName : `${fileName}.md`,
    markdown: content,
    markdownWithMeta: [content, meta].filter(Boolean).join('\n\n')
  };
}

export function buildReportShareDocument(
  report: AnalysisReport,
  fileName: string
): AnalysisShareDocument {
  const sections = [...(report.sections ?? [])].sort(
    (left, right) => (left.order ?? Number.MAX_SAFE_INTEGER) - (right.order ?? Number.MAX_SAFE_INTEGER)
  );
  const heading = `# ${report.header?.trim() || 'Wynik analizy'}`;
  const intro = [
    heading,
    report.subHeader?.trim() ? `_${report.subHeader.trim()}_` : '',
    report.markdownSummary?.trim() || ''
  ].filter(Boolean);
  const content = [
    ...intro,
    ...sections.map((section) => [
      `## ${section.title?.trim() || section.id || 'Sekcja'}`,
      section.markdown?.trim() || ''
    ].filter(Boolean).join('\n\n'))
  ].filter(Boolean).join('\n\n');

  const withMeta = [
    ...intro,
    ...sections.map((section) => [
      `## ${section.title?.trim() || section.id || 'Sekcja'}`,
      section.markdown?.trim() || '',
      renderAnalysisShareMeta(section.meta, 'Meta sekcji')
    ].filter(Boolean).join('\n\n')),
    renderAnalysisShareMeta(report.meta, 'Meta raportu')
  ].filter(Boolean).join('\n\n');
  return { fileName, markdown: content, markdownWithMeta: withMeta };
}

export function renderAnalysisShareMeta(
  meta: AnalysisReportMeta | null | undefined,
  heading = 'Meta'
): string {
  const groups = [
    markdownList('Referencje', (meta?.references ?? []).map(renderReference)),
    markdownList('Limity widoczności', meta?.visibilityLimits ?? []),
    markdownList('Otwarte pytania', meta?.openQuestions ?? []),
    markdownList('Luki', meta?.gaps ?? []),
    markdownList('Ostrzeżenia', meta?.warnings ?? [])
  ].filter(Boolean);
  return groups.length ? [`### ${heading}`, ...groups].join('\n\n') : '';
}

export function markdownList(title: string, values: readonly string[]): string {
  const entries = values.map((value) => value.trim()).filter(Boolean);
  return entries.length ? [`**${title}**`, ...entries.map((value) => `- ${value}`)].join('\n') : '';
}

export interface DeliveryShareUnit {
  title: string;
  status: string;
  result: readonly string[];
  dimensions: readonly string[];
  evidence: readonly string[];
  references: readonly string[];
  limits: readonly string[];
  warnings: readonly string[];
}

export function buildDeliveryShareDocument(
  fileName: string,
  title: string,
  period: string,
  aggregate: readonly string[],
  units: readonly DeliveryShareUnit[]
): AnalysisShareDocument {
  const intro = [`# ${title}`, period, '## Wynik zbiorczy', ...aggregate.map((line) => `- ${line}`)];
  const unitContent = (unit: DeliveryShareUnit) => [
    `### ${unit.title}`,
    `Status: ${unit.status}`,
    ...unit.result.map((line) => `- ${line}`),
    markdownList('Wymiary', unit.dimensions),
    markdownList('Uzasadnienie', unit.evidence)
  ].filter(Boolean).join('\n\n');
  const markdown = [...intro, '## Delivery Units', ...units.map(unitContent)].join('\n\n');
  const markdownWithMeta = [
    ...intro,
    '## Delivery Units',
    ...units.map((unit) => [
      unitContent(unit),
      markdownList('Referencje', unit.references),
      markdownList('Limity widoczności', unit.limits),
      markdownList('Luki i ostrzeżenia', unit.warnings)
    ].filter(Boolean).join('\n\n'))
  ].join('\n\n');
  return { fileName, markdown, markdownWithMeta };
}

function renderReference(reference: AnalysisReportReference): string {
  return [reference.label, reference.type, reference.target, reference.description]
    .map((value) => value?.trim() || '')
    .filter(Boolean)
    .join(' | ');
}
