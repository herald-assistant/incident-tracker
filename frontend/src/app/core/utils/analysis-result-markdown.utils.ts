import { AnalysisResultResponse } from '../models/analysis.models';

export function buildIncidentAnalysisResultMarkdown(
  result: Pick<AnalysisResultResponse, 'functionalAnalysis' | 'technicalAnalysis'>
): string {
  return [
    markdownSection(
      'Analiza funkcjonalna',
      result.functionalAnalysis,
      'Brak wyniku analizy funkcjonalnej.'
    ),
    markdownSection(
      'Analiza techniczna',
      result.technicalAnalysis,
      'Brak wyniku analizy technicznej.'
    )
  ].join('\n\n');
}

function markdownSection(title: string, markdown: string, fallback: string): string {
  return `## ${title}\n\n${normalizeMarkdown(markdown) || fallback}`;
}

function normalizeMarkdown(markdown: string): string {
  return (markdown || '')
    .replace(/\r\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}
