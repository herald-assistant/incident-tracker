import {
  AnalysisAiActivityEvent,
  AnalysisChatMessageResponse,
  AnalysisAiToolFeedback,
  AnalysisAiUsage,
  AnalysisEvidenceAttribute,
  AnalysisEvidenceReference,
  AnalysisEvidenceItem,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse
} from '../../../core/models/analysis.models';
import { formatFileTimestamp, sanitizeFileNamePart } from '../../../core/utils/json-file.utils';
import {
  FlowExplorerAiResponse,
  FlowExplorerAnalysisGoal,
  FlowExplorerFocusArea,
  FlowExplorerJobStateSnapshot,
  FlowExplorerResult,
  FlowExplorerResultOverview,
  FlowExplorerResultSection,
  FlowExplorerResultSectionId,
  FlowExplorerResultSectionMode
} from '../models/flow-explorer.models';

export const FLOW_EXPLORER_EXPORT_SCHEMA = 'incident-tracker.flow-explorer-export';
export const FLOW_EXPLORER_EXPORT_VERSION = 1;

export interface FlowExplorerExportEnvelope {
  schema: string;
  version: number;
  exportedAt: string;
  payload: {
    type: 'flow-explorer-job';
    job: FlowExplorerJobStateSnapshot;
  };
}

export interface FlowExplorerExportState {
  origin: 'live' | 'imported';
  exportedAt: string;
  fileName: string;
  job: FlowExplorerJobStateSnapshot;
}

export function buildFlowExplorerExportEnvelope(
  job: FlowExplorerJobStateSnapshot,
  exportedAt: string
): FlowExplorerExportEnvelope {
  return {
    schema: FLOW_EXPLORER_EXPORT_SCHEMA,
    version: FLOW_EXPLORER_EXPORT_VERSION,
    exportedAt,
    payload: {
      type: 'flow-explorer-job',
      job
    }
  };
}

export function parseImportedFlowExplorerAnalysis(payload: unknown): {
  exportedAt: string;
  job: FlowExplorerJobStateSnapshot;
} {
  const payloadObject = asObject(payload);
  if (!payloadObject || payloadObject['schema'] !== FLOW_EXPLORER_EXPORT_SCHEMA) {
    throw new Error('Wybierz plik wyeksportowany z Flow Explorera.');
  }

  if (Number(payloadObject['version']) !== FLOW_EXPLORER_EXPORT_VERSION) {
    throw new Error('Ten plik eksportu Flow Explorera ma nieobsługiwaną wersję formatu.');
  }

  const envelopePayload = asObject(payloadObject['payload']);
  if (!envelopePayload || envelopePayload['type'] !== 'flow-explorer-job') {
    throw new Error('Plik eksportu nie zawiera wyniku Flow Explorera.');
  }

  const job = normalizeFlowExplorerJob(envelopePayload['job']);
  if (!job.status) {
    throw new Error('Plik nie zawiera statusu Flow Explorer joba.');
  }

  if (!isTerminalJobStatus(job.status) || hasActiveChat(job)) {
    throw new Error('Import wspiera tylko zakończone Flow Explorer analizy.');
  }

  return {
    exportedAt: normalizeString(payloadObject['exportedAt']),
    job
  };
}

export function normalizeFlowExplorerJob(job: unknown): FlowExplorerJobStateSnapshot {
  const jobObject = asObject(job);
  if (!jobObject) {
    throw new Error('Plik eksportu nie zawiera poprawnego obiektu Flow Explorer joba.');
  }

  return {
    jobId: normalizeString(jobObject['jobId']),
    systemId: normalizeString(jobObject['systemId']),
    endpointId: normalizeString(jobObject['endpointId']),
    httpMethod: normalizeString(jobObject['httpMethod']),
    endpointPath: normalizeString(jobObject['endpointPath']),
    branch: normalizeString(jobObject['branch']),
    goal: normalizeGoal(jobObject['goal']),
    focusAreas: normalizeFocusAreas(jobObject['focusAreas']),
    aiModel: normalizeString(jobObject['aiModel']),
    reasoningEffort: normalizeString(jobObject['reasoningEffort']),
    status: normalizeString(jobObject['status']),
    currentStepCode: normalizeString(jobObject['currentStepCode']),
    currentStepLabel: normalizeString(jobObject['currentStepLabel']),
    errorCode: normalizeString(jobObject['errorCode']),
    errorMessage: normalizeString(jobObject['errorMessage']),
    createdAt: normalizeString(jobObject['createdAt']),
    updatedAt: normalizeString(jobObject['updatedAt']),
    completedAt: normalizeString(jobObject['completedAt']),
    steps: Array.isArray(jobObject['steps']) ? jobObject['steps'].map(normalizeStep) : [],
    contextSnapshot: jobObject['contextSnapshot'] ?? null,
    contextSections: Array.isArray(jobObject['contextSections'])
      ? jobObject['contextSections'].map(normalizeEvidenceSection)
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

export function buildFlowExplorerExportFileName(
  job: FlowExplorerJobStateSnapshot,
  exportedAt: string
): string {
  const systemId = sanitizeFileNamePart(job.systemId || job.jobId || 'flow-explorer');
  const endpoint = sanitizeFileNamePart(job.endpointPath || job.endpointId || 'endpoint');
  const status = sanitizeFileNamePart((job.status || 'result').toLowerCase());
  return `flow-explorer-${systemId}-${endpoint}-${status}-${formatFileTimestamp(exportedAt)}.json`;
}

function normalizeStep(step: unknown): AnalysisJobStepResponse {
  const stepObject = asObject(step);
  return {
    code: normalizeString(stepObject?.['code']),
    label: normalizeString(stepObject?.['label']),
    phase: normalizeString(stepObject?.['phase']),
    status: normalizeString(stepObject?.['status']),
    message: normalizeString(stepObject?.['message']),
    itemCount: normalizeNullableNumber(stepObject?.['itemCount']),
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

function normalizeEvidenceReference(reference: unknown): AnalysisEvidenceReference {
  const referenceObject = asObject(reference);
  return {
    provider: normalizeString(referenceObject?.['provider']),
    category: normalizeString(referenceObject?.['category'])
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

function normalizeResult(result: unknown): FlowExplorerResult {
  const resultObject = asObject(result);
  return {
    status: normalizeString(resultObject?.['status']),
    systemId: normalizeString(resultObject?.['systemId']),
    endpointId: normalizeString(resultObject?.['endpointId']),
    httpMethod: normalizeString(resultObject?.['httpMethod']),
    endpointPath: normalizeString(resultObject?.['endpointPath']),
    branch: normalizeString(resultObject?.['branch']),
    goal: normalizeGoal(resultObject?.['goal']),
    prompt: normalizeString(resultObject?.['prompt']),
    aiResponse: asObject(resultObject?.['aiResponse'])
      ? normalizeAiResponse(resultObject?.['aiResponse'])
      : null,
    usage: normalizeUsage(resultObject?.['usage'])
  };
}

function normalizeAiResponse(response: unknown): FlowExplorerAiResponse {
  const responseObject = asObject(response);
  return {
    goal: normalizeGoal(responseObject?.['goal']),
    audience: normalizeString(responseObject?.['audience']) || 'business_or_system_analyst_tester',
    overview: normalizeOverview(responseObject?.['overview']),
    sections: normalizeSections(responseObject?.['sections']),
    globalVisibilityLimits: normalizeStringArray(responseObject?.['globalVisibilityLimits']),
    globalOpenQuestions: normalizeStringArray(responseObject?.['globalOpenQuestions']),
    sourceReferences: normalizeStringArray(responseObject?.['sourceReferences']),
    confidence: normalizeString(responseObject?.['confidence'])
  };
}

function normalizeOverview(overview: unknown): FlowExplorerResultOverview {
  const overviewObject = asObject(overview);
  return {
    markdown: normalizeString(overviewObject?.['markdown']),
    confidence: normalizeString(overviewObject?.['confidence']),
    sourceRefs: normalizeStringArray(overviewObject?.['sourceRefs'])
  };
}

function normalizeSections(sections: unknown): FlowExplorerResultSection[] {
  const byId = new Map<FlowExplorerResultSectionId, FlowExplorerResultSection>();
  if (Array.isArray(sections)) {
    sections.forEach((section) => {
      const normalized = normalizeSection(section);
      if (normalized) {
        byId.set(normalized.id, normalized);
      }
    });
  }

  return SECTION_IDS.map((id) =>
    byId.get(id) ?? {
      id,
      title: sectionTitle(id),
      mode: 'compact',
      markdown: '',
      sourceRefs: [],
      visibilityLimits: [],
      openQuestions: []
    }
  );
}

function normalizeSection(section: unknown): FlowExplorerResultSection | null {
  const sectionObject = asObject(section);
  const id = normalizeSectionId(sectionObject?.['id']);
  if (!id) {
    return null;
  }

  return {
    id,
    title: normalizeString(sectionObject?.['title']) || sectionTitle(id),
    mode: normalizeSectionMode(sectionObject?.['mode']),
    markdown: normalizeString(sectionObject?.['markdown']),
    sourceRefs: normalizeStringArray(sectionObject?.['sourceRefs']),
    visibilityLimits: normalizeStringArray(sectionObject?.['visibilityLimits']),
    openQuestions: normalizeStringArray(sectionObject?.['openQuestions'])
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

function isTerminalJobStatus(status: string): boolean {
  return status === 'COMPLETED' || status === 'FAILED';
}

function hasActiveChat(snapshot: FlowExplorerJobStateSnapshot): boolean {
  return snapshot.chatMessages.some((message) => message.status === 'IN_PROGRESS');
}

function normalizeString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function normalizeStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

function normalizeGoal(value: unknown): FlowExplorerAnalysisGoal {
  const normalized = normalizeString(value);
  return isFlowExplorerGoal(normalized) ? normalized : 'DEEP_DISCOVERY';
}

function normalizeFocusAreas(value: unknown): FlowExplorerFocusArea[] {
  return normalizeStringArray(value).filter(isFlowExplorerFocusArea);
}

function normalizeSectionId(value: unknown): FlowExplorerResultSectionId | null {
  const normalized = normalizeString(value);
  return isFlowExplorerSectionId(normalized) ? normalized : null;
}

function normalizeSectionMode(value: unknown): FlowExplorerResultSectionMode {
  const normalized = normalizeString(value).toLowerCase();
  return normalized === 'deep' ? 'deep' : 'compact';
}

function normalizeNumber(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function normalizeNullableNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

function asObject(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }

  return value as Record<string, unknown>;
}

const SECTION_IDS: FlowExplorerResultSectionId[] = [
  'BUSINESS_FLOW_RULES',
  'VALIDATIONS',
  'PERSISTENCE',
  'INTEGRATIONS'
];

function sectionTitle(id: FlowExplorerResultSectionId): string {
  switch (id) {
    case 'BUSINESS_FLOW_RULES':
      return 'Business flow/rules';
    case 'VALIDATIONS':
      return 'Validations';
    case 'PERSISTENCE':
      return 'Persistence';
    case 'INTEGRATIONS':
      return 'Integrations';
  }
}

function isFlowExplorerGoal(value: string): value is FlowExplorerAnalysisGoal {
  return value === 'DEEP_DISCOVERY' || value === 'TEST_SCENARIOS' || value === 'RISK_DETECTION';
}

function isFlowExplorerFocusArea(value: string): value is FlowExplorerFocusArea {
  return SECTION_IDS.includes(value as FlowExplorerResultSectionId);
}

function isFlowExplorerSectionId(value: string): value is FlowExplorerResultSectionId {
  return SECTION_IDS.includes(value as FlowExplorerResultSectionId);
}
