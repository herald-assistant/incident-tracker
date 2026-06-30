import {
  AnalysisReport,
  AnalysisReportMeta,
  AnalysisReportReference,
  AnalysisReportSection
} from '../../../core/models/analysis.models';

export function buildFlowExplorerReportMarkdown(
  report: AnalysisReport,
  fallbackHeading = 'Flow Explorer result'
): string {
  const heading = headingText(report.header || fallbackHeading);
  const subHeader = normalizeMarkdown(report.subHeader);
  const sections = [...(report.sections ?? [])]
    .sort((left, right) => {
      const leftOrder = typeof left.order === 'number' ? left.order : Number.MAX_SAFE_INTEGER;
      const rightOrder = typeof right.order === 'number' ? right.order : Number.MAX_SAFE_INTEGER;
      return leftOrder - rightOrder;
    })
    .map((section) => reportSectionMarkdown(section))
    .filter(Boolean);

  return [
    heading,
    subHeader ? `_${subHeader}_` : '',
    normalizeMarkdown(report.markdownSummary),
    ...sections,
    reportMetaMarkdown('Report metadata', report.meta)
  ]
    .filter(Boolean)
    .join('\n\n')
    .trim();
}

function headingText(value: string): string {
  return `# ${value || 'Flow Explorer result'}`;
}

function markdownSection(title: string, markdown: string): string {
  const content = normalizeMarkdown(markdown);
  if (!content) {
    return '';
  }
  return `## ${title.trim() || 'Section'}\n\n${content}`;
}

function normalizeMarkdown(markdown: string): string {
  return (markdown || '')
    .replace(/\r\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function reportSectionMarkdown(section: AnalysisReportSection): string {
  const title = section.title || section.id || 'Section';
  const meta = reportMetaMarkdown('Metadata', section.meta);
  return [markdownSection(title, section.markdown), meta].filter(Boolean).join('\n\n');
}

function reportMetaMarkdown(title: string, meta: AnalysisReportMeta | null | undefined): string {
  const confidence = normalizeMarkdown(meta?.confidence ?? '');
  const groups = [
    bulletGroup('References', (meta?.references ?? []).map(referenceText)),
    bulletGroup('Visibility limits', meta?.visibilityLimits ?? []),
    bulletGroup('Open questions', meta?.openQuestions ?? []),
    bulletGroup('Gaps', meta?.gaps ?? []),
    bulletGroup('Warnings', meta?.warnings ?? []),
    confidence ? `- Confidence: ${confidence}` : ''
  ].filter(Boolean);

  if (!groups.length) {
    return '';
  }
  return [`### ${title}`, ...groups].join('\n\n');
}

function bulletGroup(title: string, values: string[]): string {
  const items = values.map(normalizeMarkdown).filter(Boolean);
  if (!items.length) {
    return '';
  }
  return [`**${title}**`, ...items.map((item) => `- ${item}`)].join('\n');
}

function referenceText(reference: AnalysisReportReference | null | undefined): string {
  if (!reference) {
    return '';
  }
  return [reference.label, reference.type, reference.target, reference.description]
    .map(normalizeMarkdown)
    .filter(Boolean)
    .join(' | ');
}
