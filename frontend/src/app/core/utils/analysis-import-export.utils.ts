import {
  AnalysisEvidenceAttribute,
  AnalysisEvidenceItem,
  AnalysisEvidenceSection,
  AnalysisAiActivityEvent,
  AnalysisAiToolFeedback,
  AnalysisChatMessageResponse,
  AnalysisAiUsage,
  AnalysisExportEnvelope,
  AnalysisJobStateSnapshot,
  AnalysisJobStepResponse,
  AnalysisResultResponse
} from '../models/analysis.models';
import { isTerminalStatus } from './analysis-display.utils';

export const EXPORT_SCHEMA = 'incident-tracker.analysis-export';
export const EXPORT_VERSION = 6;
const SUPPORTED_EXPORT_VERSIONS = new Set([2, 3, 4, 5, 6]);

export function buildExportEnvelope(
  job: AnalysisJobStateSnapshot,
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
  job: AnalysisJobStateSnapshot;
} {
  const payloadObject = asObject(payload);
  if (!payloadObject) {
    throw new Error('Wybrany plik nie zawiera poprawnego zapisu analizy.');
  }

  let exportedAt = '';
  let jobPayload: unknown = payloadObject;

  if (payloadObject['schema'] === EXPORT_SCHEMA) {
    if (!SUPPORTED_EXPORT_VERSIONS.has(Number(payloadObject['version']))) {
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
      'Nie rozpoznano formatu importu. Wybierz plik wyeksportowany z Team Delivery Workspace.'
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

export function normalizeAnalysisJob(job: unknown): AnalysisJobStateSnapshot {
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
    aiActivityEvents: Array.isArray(jobObject['aiActivityEvents'])
      ? jobObject['aiActivityEvents'].map(normalizeAiActivityEvent)
      : [],
    toolFeedback: Array.isArray(jobObject['toolFeedback'])
      ? jobObject['toolFeedback'].map(normalizeToolFeedback)
      : [],
    chatMessages: Array.isArray(jobObject['chatMessages'])
      ? jobObject['chatMessages'].map(normalizeChatMessage)
      : [],
    preparedPrompt: normalizeString(jobObject['preparedPrompt']),
    result: asObject(jobObject['result']) ? normalizeResult(jobObject['result']) : null
  };
}

export function buildExportFileName(job: AnalysisJobStateSnapshot, exportedAt: string): string {
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
    consumesEvidence: Array.isArray(stepObject?.['consumesEvidence'])
      ? stepObject['consumesEvidence'].map(normalizeEvidenceReference)
      : [],
    producesEvidence: Array.isArray(stepObject?.['producesEvidence'])
      ? stepObject['producesEvidence'].map(normalizeEvidenceReference)
      : [],
    usage: normalizeUsage(stepObject?.['usage'])
  };
}

function normalizeEvidenceReference(reference: unknown): { provider: string; category: string } {
  const referenceObject = asObject(reference);
  return {
    provider: normalizeString(referenceObject?.['provider']),
    category: normalizeString(referenceObject?.['category'])
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

function normalizeChatMessage(message: unknown): AnalysisChatMessageResponse {
  const messageObject = asObject(message);
  return {
    id: normalizeString(messageObject?.['id']),
    role: normalizeString(messageObject?.['role']),
    status: normalizeString(messageObject?.['status']),
    content: normalizeString(messageObject?.['content']),
    errorCode: normalizeString(messageObject?.['errorCode']),
    errorMessage: normalizeString(messageObject?.['errorMessage']),
    createdAt: normalizeString(messageObject?.['createdAt']),
    updatedAt: normalizeString(messageObject?.['updatedAt']),
    completedAt: normalizeString(messageObject?.['completedAt']),
    toolEvidenceSections: Array.isArray(messageObject?.['toolEvidenceSections'])
      ? messageObject['toolEvidenceSections'].map(normalizeEvidenceSection)
      : [],
    aiActivityEvents: Array.isArray(messageObject?.['aiActivityEvents'])
      ? messageObject['aiActivityEvents'].map(normalizeAiActivityEvent)
      : [],
    toolFeedback: Array.isArray(messageObject?.['toolFeedback'])
      ? messageObject['toolFeedback'].map(normalizeToolFeedback)
      : [],
    prompt: normalizeString(messageObject?.['prompt'])
  };
}

function normalizeToolFeedback(feedback: unknown): AnalysisAiToolFeedback {
  const feedbackObject = asObject(feedback);
  return {
    feedbackId: normalizeString(feedbackObject?.['feedbackId']),
    targetToolName: normalizeString(feedbackObject?.['targetToolName']),
    targetToolCallId: normalizeString(feedbackObject?.['targetToolCallId']),
    feedbackToolCallId: normalizeString(feedbackObject?.['feedbackToolCallId']),
    usefulness: normalizeString(feedbackObject?.['usefulness']),
    expectedDataReceived: normalizeString(feedbackObject?.['expectedDataReceived']),
    issueCategory: normalizeString(feedbackObject?.['issueCategory']),
    improvementArea: normalizeString(feedbackObject?.['improvementArea']),
    confidence: normalizeString(feedbackObject?.['confidence']),
    summaryForOperator: normalizeString(feedbackObject?.['summaryForOperator']),
    suggestedImprovement: normalizeString(feedbackObject?.['suggestedImprovement']),
    createdAt: normalizeString(feedbackObject?.['createdAt'])
  };
}

function normalizeAiActivityEvent(event: unknown): AnalysisAiActivityEvent {
  const eventObject = asObject(event);
  return {
    eventId: normalizeString(eventObject?.['eventId']),
    parentEventId: normalizeString(eventObject?.['parentEventId']),
    type: normalizeString(eventObject?.['type']),
    category: normalizeString(eventObject?.['category']),
    status: normalizeString(eventObject?.['status']),
    title: normalizeString(eventObject?.['title']),
    summary: normalizeString(eventObject?.['summary']),
    turnId: normalizeString(eventObject?.['turnId']),
    interactionId: normalizeString(eventObject?.['interactionId']),
    toolCallId: normalizeString(eventObject?.['toolCallId']),
    toolName: normalizeString(eventObject?.['toolName']),
    timestamp: normalizeString(eventObject?.['timestamp']),
    details: asObject(eventObject?.['details']) ?? {}
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
    detectedProblem: normalizeString(resultObject?.['detectedProblem']),
    affectedProcess: normalizeString(resultObject?.['affectedProcess']),
    affectedBoundedContext: normalizeString(resultObject?.['affectedBoundedContext']),
    affectedTeam: normalizeString(resultObject?.['affectedTeam']),
    functionalAnalysis: normalizeString(resultObject?.['functionalAnalysis']),
    technicalAnalysis: normalizeString(resultObject?.['technicalAnalysis']),
    confidence: normalizeString(resultObject?.['confidence']),
    visibilityLimits: normalizeStringArray(resultObject?.['visibilityLimits']),
    prompt: normalizeString(resultObject?.['prompt']),
    usage: normalizeUsage(resultObject?.['usage'])
  };
}

function normalizeUsage(usage: unknown): AnalysisAiUsage | null {
  const usageObject = asObject(usage);
  if (!usageObject) {
    return null;
  }

  const totalTokens = normalizeNumber(usageObject['totalTokens']);
  const inputTokens = normalizeNumber(usageObject['inputTokens']);
  const outputTokens = normalizeNumber(usageObject['outputTokens']);

  if (totalTokens <= 0 && inputTokens <= 0 && outputTokens <= 0) {
    return null;
  }

  return {
    inputTokens,
    outputTokens,
    cacheReadTokens: normalizeNumber(usageObject['cacheReadTokens']),
    cacheWriteTokens: normalizeNumber(usageObject['cacheWriteTokens']),
    totalTokens,
    cost: normalizeNumber(usageObject['cost']),
    apiDurationMs: normalizeNumber(usageObject['apiDurationMs']),
    apiCallCount: normalizeNumber(usageObject['apiCallCount']),
    model: normalizeString(usageObject['model']),
    contextTokenLimit: normalizeNullableNumber(usageObject['contextTokenLimit']),
    contextCurrentTokens: normalizeNullableNumber(usageObject['contextCurrentTokens']),
    contextMessages: normalizeNullableNumber(usageObject['contextMessages'])
  };
}

function normalizeString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function normalizeStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

function normalizeNumber(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function normalizeNullableNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
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
