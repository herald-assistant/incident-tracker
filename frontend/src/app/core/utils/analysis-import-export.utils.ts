import {
  AnalysisConfidence,
  AnalysisEvidenceAttribute,
  AnalysisEvidenceReference,
  AnalysisEvidenceItem,
  AnalysisEvidenceSection,
  AnalysisExportEnvelope,
  AnalysisFlowDiagram,
  AnalysisFlowDiagramEdge,
  AnalysisFlowDiagramMetadata,
  AnalysisFlowDiagramNode,
  AnalysisJobResponse,
  AnalysisJobStepResponse,
  AnalysisMode,
  AnalysisProblemNature,
  AnalysisResultResponse,
  AnalysisResultVariants,
  AnalysisVariantResultResponse
} from '../models/analysis.models';
import { isTerminalStatus } from './analysis-display.utils';

export const EXPORT_SCHEMA = 'incident-tracker.analysis-export';
export const EXPORT_VERSION = 2;

export function buildExportEnvelope(
  job: AnalysisJobResponse,
  exportedAt: string
): AnalysisExportEnvelope {
  return {
    schema: EXPORT_SCHEMA,
    version: EXPORT_VERSION,
    exportedAt,
    payload: {
      type: 'analysis-job',
      job
    }
  };
}

export function parseImportedAnalysis(payload: unknown): {
  exportedAt: string;
  job: AnalysisJobResponse;
} {
  const payloadObject = asObject(payload);
  if (!payloadObject) {
    throw new Error('Wybrany plik nie zawiera poprawnego zapisu analizy.');
  }

  let exportedAt = '';
  let jobPayload: unknown = payloadObject;

  if (payloadObject['schema'] === EXPORT_SCHEMA) {
    if (payloadObject['version'] !== EXPORT_VERSION) {
      throw new Error('Ten plik eksportu ma nieobsługiwaną wersję formatu.');
    }

    const envelopePayload = asObject(payloadObject['payload']);
    if (!envelopePayload || envelopePayload['type'] !== 'analysis-job') {
      throw new Error('Plik eksportu nie zawiera wyniku analizy.');
    }

    exportedAt = normalizeString(payloadObject['exportedAt']);
    jobPayload = envelopePayload['job'];
  } else if (!looksLikeAnalysisJob(payloadObject)) {
    throw new Error(
      'Nie rozpoznano formatu importu. Wybierz plik wyeksportowany z Incident Tracker.'
    );
  }

  const job = normalizeAnalysisJob(jobPayload);
  if (!job.status) {
    throw new Error('Plik nie zawiera statusu analizy.');
  }

  if (!isTerminalStatus(job.status)) {
    throw new Error('Import wspiera tylko zakończone analizy.');
  }

  return {
    exportedAt,
    job
  };
}

export function normalizeAnalysisJob(job: unknown): AnalysisJobResponse {
  const jobObject = asObject(job);
  if (!jobObject) {
    throw new Error('Plik eksportu nie zawiera poprawnego obiektu joba analizy.');
  }

  return {
    analysisId: normalizeString(jobObject['analysisId']),
    correlationId: normalizeString(jobObject['correlationId']),
    status: normalizeString(jobObject['status']),
    currentStepCode: normalizeString(jobObject['currentStepCode']),
    currentStepLabel: normalizeString(jobObject['currentStepLabel']),
    environment: normalizeString(jobObject['environment']),
    gitLabBranch: normalizeString(jobObject['gitLabBranch']),
    errorCode: normalizeString(jobObject['errorCode']),
    errorMessage: normalizeString(jobObject['errorMessage']),
    createdAt: normalizeString(jobObject['createdAt']),
    updatedAt: normalizeString(jobObject['updatedAt']),
    completedAt: normalizeString(jobObject['completedAt']),
    steps: Array.isArray(jobObject['steps'])
      ? jobObject['steps'].map(normalizeStep)
      : [],
    evidenceSections: Array.isArray(jobObject['evidenceSections'])
      ? jobObject['evidenceSections'].map(normalizeEvidenceSection)
      : [],
    result: asObject(jobObject['result']) ? normalizeResult(jobObject['result']) : null
  };
}

export function buildExportFileName(job: AnalysisJobResponse, exportedAt: string): string {
  const correlationId = sanitizeFileNamePart(job.correlationId || job.analysisId || 'analysis');
  const status = sanitizeFileNamePart((job.status || 'result').toLowerCase());
  return `incident-analysis-${correlationId}-${status}-${formatFileTimestamp(exportedAt)}.json`;
}

function looksLikeAnalysisJob(payload: Record<string, unknown>): boolean {
  return (
    typeof payload['status'] === 'string' &&
    ('analysisId' in payload || 'correlationId' in payload)
  );
}

function normalizeStep(step: unknown): AnalysisJobStepResponse {
  const stepObject = asObject(step);
  return {
    code: normalizeString(stepObject?.['code']),
    label: normalizeString(stepObject?.['label']),
    status: normalizeString(stepObject?.['status']),
    message: normalizeString(stepObject?.['message']),
    itemCount: typeof stepObject?.['itemCount'] === 'number' ? stepObject['itemCount'] : null,
    startedAt: normalizeString(stepObject?.['startedAt']),
    completedAt: normalizeString(stepObject?.['completedAt']),
    phase: normalizeString(stepObject?.['phase']),
    variantMode: normalizeMode(stepObject?.['variantMode']),
    preparedPrompt: normalizeNullableString(stepObject?.['preparedPrompt']),
    consumesEvidence: Array.isArray(stepObject?.['consumesEvidence'])
      ? stepObject['consumesEvidence'].map(normalizeEvidenceReference)
      : [],
    producesEvidence: Array.isArray(stepObject?.['producesEvidence'])
      ? stepObject['producesEvidence'].map(normalizeEvidenceReference)
      : []
  };
}

function normalizeEvidenceSection(section: unknown): AnalysisEvidenceSection {
  const sectionObject = asObject(section);
  return {
    provider: normalizeString(sectionObject?.['provider']),
    category: normalizeString(sectionObject?.['category']),
    items: Array.isArray(sectionObject?.['items'])
      ? sectionObject['items'].map(normalizeEvidenceItem)
      : []
  };
}

function normalizeEvidenceItem(item: unknown): AnalysisEvidenceItem {
  const itemObject = asObject(item);
  return {
    title: normalizeString(itemObject?.['title']),
    attributes: Array.isArray(itemObject?.['attributes'])
      ? itemObject['attributes'].map(normalizeAttribute)
      : []
  };
}

function normalizeAttribute(attribute: unknown): AnalysisEvidenceAttribute {
  const attributeObject = asObject(attribute);
  return {
    name: normalizeString(attributeObject?.['name']),
    value: normalizeString(attributeObject?.['value'])
  };
}

function normalizeResult(result: unknown): AnalysisResultResponse {
  const resultObject = asObject(result);
  return {
    status: normalizeString(resultObject?.['status']),
    correlationId: normalizeString(resultObject?.['correlationId']),
    environment: normalizeString(resultObject?.['environment']),
    gitLabBranch: normalizeString(resultObject?.['gitLabBranch']),
    variants: normalizeVariants(resultObject?.['variants'])
  };
}

function normalizeVariants(value: unknown): AnalysisResultVariants {
  const variantsObject = asObject(value);
  return {
    conservative: normalizeVariant(variantsObject?.['conservative'], 'CONSERVATIVE'),
    exploratory: normalizeVariant(variantsObject?.['exploratory'], 'EXPLORATORY')
  };
}

function normalizeVariant(
  value: unknown,
  fallbackMode: AnalysisMode
): AnalysisVariantResultResponse {
  const variantObject = asObject(value);
  return {
    mode: normalizeMode(variantObject?.['mode']) || fallbackMode,
    status: normalizeString(variantObject?.['status']),
    detectedProblem: normalizeString(variantObject?.['detectedProblem']),
    summary: normalizeString(variantObject?.['summary']),
    recommendedAction: normalizeString(variantObject?.['recommendedAction']),
    rationale: normalizeString(variantObject?.['rationale']),
    problemNature: normalizeProblemNature(variantObject?.['problemNature']),
    confidence: normalizeConfidence(variantObject?.['confidence']),
    prompt: normalizeString(variantObject?.['prompt']),
    diagram: asObject(variantObject?.['diagram']) ? normalizeDiagram(variantObject?.['diagram']) : null
  };
}

function normalizeDiagram(value: unknown): AnalysisFlowDiagram {
  const diagramObject = asObject(value);
  return {
    nodes: Array.isArray(diagramObject?.['nodes'])
      ? diagramObject['nodes'].map(normalizeDiagramNode)
      : [],
    edges: Array.isArray(diagramObject?.['edges'])
      ? diagramObject['edges'].map(normalizeDiagramEdge)
      : []
  };
}

function normalizeDiagramNode(value: unknown): AnalysisFlowDiagramNode {
  const nodeObject = asObject(value);
  return {
    id: normalizeString(nodeObject?.['id']),
    kind: normalizeString(nodeObject?.['kind']),
    title: normalizeString(nodeObject?.['title']),
    componentName: normalizeString(nodeObject?.['componentName']),
    factStatus: normalizeString(nodeObject?.['factStatus']),
    firstSeenAt: normalizeString(nodeObject?.['firstSeenAt']),
    metadata: Array.isArray(nodeObject?.['metadata'])
      ? nodeObject['metadata'].map(normalizeDiagramMetadata)
      : [],
    errorSource: Boolean(nodeObject?.['errorSource'])
  };
}

function normalizeDiagramEdge(value: unknown): AnalysisFlowDiagramEdge {
  const edgeObject = asObject(value);
  return {
    id: normalizeString(edgeObject?.['id']),
    fromNodeId: normalizeString(edgeObject?.['fromNodeId']),
    toNodeId: normalizeString(edgeObject?.['toNodeId']),
    sequence: typeof edgeObject?.['sequence'] === 'number' ? edgeObject['sequence'] : 0,
    interactionType: normalizeString(edgeObject?.['interactionType']),
    factStatus: normalizeString(edgeObject?.['factStatus']),
    startedAt: normalizeString(edgeObject?.['startedAt']),
    durationMs: typeof edgeObject?.['durationMs'] === 'number' ? edgeObject['durationMs'] : null,
    supportSummary: normalizeString(edgeObject?.['supportSummary'])
  };
}

function normalizeDiagramMetadata(value: unknown): AnalysisFlowDiagramMetadata {
  const metadataObject = asObject(value);
  return {
    name: normalizeString(metadataObject?.['name']),
    value: normalizeString(metadataObject?.['value'])
  };
}

function normalizeEvidenceReference(value: unknown): AnalysisEvidenceReference {
  const referenceObject = asObject(value);
  return {
    provider: normalizeString(referenceObject?.['provider']),
    category: normalizeString(referenceObject?.['category'])
  };
}

function normalizeString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function normalizeNullableString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null;
}

function normalizeMode(value: unknown): AnalysisMode {
  return typeof value === 'string' ? value : '';
}

function normalizeProblemNature(value: unknown): AnalysisProblemNature {
  return typeof value === 'string' ? value : '';
}

function normalizeConfidence(value: unknown): AnalysisConfidence | null {
  return typeof value === 'string' ? value : null;
}

function sanitizeFileNamePart(value: string): string {
  const normalized = String(value || '')
    .trim()
    .replace(/[^a-zA-Z0-9_-]+/g, '-')
    .replace(/^-+|-+$/g, '');

  return normalized ? normalized.substring(0, 48) : 'snapshot';
}

function formatFileTimestamp(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return 'snapshot';
  }

  return (
    [date.getFullYear(), pad2(date.getMonth() + 1), pad2(date.getDate())].join('') +
    '-' +
    [pad2(date.getHours()), pad2(date.getMinutes()), pad2(date.getSeconds())].join('')
  );
}

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}

function asObject(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }

  return value as Record<string, unknown>;
}
