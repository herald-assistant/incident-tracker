import {
  AnalysisEvidenceAttribute,
  AnalysisEvidenceItem,
  AnalysisEvidenceSection,
  AnalysisExportEnvelope,
  AnalysisJobResponse,
  AnalysisJobStepResponse,
  AnalysisResultResponse
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
    aiModel: normalizeString(jobObject['aiModel']),
    reasoningEffort: normalizeString(jobObject['reasoningEffort']),
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
    toolEvidenceSections: Array.isArray(jobObject['toolEvidenceSections'])
      ? jobObject['toolEvidenceSections'].map(normalizeEvidenceSection)
      : [],
    preparedPrompt: normalizeString(jobObject['preparedPrompt']),
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
    completedAt: normalizeString(stepObject?.['completedAt'])
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
    summary: normalizeString(resultObject?.['summary']),
    detectedProblem: normalizeString(resultObject?.['detectedProblem']),
    recommendedAction: normalizeString(resultObject?.['recommendedAction']),
    rationale: normalizeString(resultObject?.['rationale']),
    affectedFunction: normalizeString(resultObject?.['affectedFunction']),
    affectedProcess: normalizeString(resultObject?.['affectedProcess']),
    affectedBoundedContext: normalizeString(resultObject?.['affectedBoundedContext']),
    affectedTeam: normalizeString(resultObject?.['affectedTeam']),
    prompt: normalizeString(resultObject?.['prompt'])
  };
}

function normalizeString(value: unknown): string {
  return typeof value === 'string' ? value : '';
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
