import { FlowExplorerResult } from '../models/flow-explorer.models';

export function buildFlowExplorerResultMarkdown(result: FlowExplorerResult): string {
  const aiResponse = result.aiResponse;
  const heading = headingText(`${result.httpMethod} ${result.endpointPath}`.trim());

  if (!aiResponse) {
    return heading;
  }

  const sections = [
    markdownSection('Overview', aiResponse.overview.markdown),
    ...aiResponse.sections
      .filter((section) => section.mode?.toLowerCase() !== 'off')
      .map((section) => markdownSection(section.title, section.markdown))
  ].filter(Boolean);

  return [heading, ...sections].join('\n\n').trim();
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
