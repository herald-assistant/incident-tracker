import { AnalysisReport, AnalysisReportMeta, AnalysisReportSection } from '../models/analysis.models';

export function orderedReportSections(report: AnalysisReport | null, appendixSectionId = ''): AnalysisReportSection[] {
  return [...(report?.sections ?? [])].sort((left, right) => {
    if (appendixSectionId && left.id !== right.id) {
      if (left.id === appendixSectionId) return 1;
      if (right.id === appendixSectionId) return -1;
    }
    return (left.order ?? Number.MAX_SAFE_INTEGER) - (right.order ?? Number.MAX_SAFE_INTEGER);
  });
}

export function mergeReportMeta(...values: Array<AnalysisReportMeta | null | undefined>): AnalysisReportMeta {
  const metas = values.filter((meta): meta is AnalysisReportMeta => !!meta);
  const references = new Map(metas.flatMap(meta => meta.references).map(ref => [JSON.stringify(ref), ref]));
  return {
    references: [...references.values()],
    visibilityLimits: [...new Set(metas.flatMap(meta => meta.visibilityLimits))],
    openQuestions: [...new Set(metas.flatMap(meta => meta.openQuestions))],
    gaps: [...new Set(metas.flatMap(meta => meta.gaps))],
    warnings: [...new Set(metas.flatMap(meta => meta.warnings))],
    confidence: metas.map(meta => meta.confidence).filter(Boolean).join(' / ')
  };
}
