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
export const FLOW_EXPLORER_EXPORT_VERSION = 2;
export const FLOW_EXPLORER_EXPORT_PAYLOAD_TYPE = 'flow-explorer-analysis';
export const FLOW_EXPLORER_RESULT_CONTRACT = 'flow-explorer-goal-result-v1';

export interface FlowExplorerExportEnvelope {
  schema: string;
  version: number;
  exportedAt: string;
  payload: {
    type: typeof FLOW_EXPLORER_EXPORT_PAYLOAD_TYPE;
    resultContract: typeof FLOW_EXPLORER_RESULT_CONTRACT;
    diagnostics: FlowExplorerExportDiagnostics;
    job: FlowExplorerJobStateSnapshot;
  };
}

export interface FlowExplorerExportDiagnostics {
  resultContract: typeof FLOW_EXPLORER_RESULT_CONTRACT;
  target: {
    systemId: string;
    endpointId: string;
    httpMethod: string;
    endpointPath: string;
    branch: string;
  };
  request: {
    goal: FlowExplorerAnalysisGoal;
    focusAreas: FlowExplorerFocusArea[];
    aiModel: string;
    reasoningEffort: string;
  };
  result: {
    goal: FlowExplorerAnalysisGoal;
    confidence: string;
    sectionModes: Record<FlowExplorerResultSectionId, FlowExplorerResultSectionMode>;
    sourceReferenceCount: number;
    visibilityLimitCount: number;
    openQuestionCount: number;
  };
  context: {
    contextSnapshotIncluded: boolean;
    repositoryCount: number;
    flowNodeCount: number;
    relationCount: number;
    snippetCardCount: number;
    snippetCharacterCount: number;
    snippetBudgetReached: boolean;
    clippingNotes: string[];
    limitationCount: number;
  };
  workflow: {
    stepCount: number;
    contextEvidenceItemCount: number;
    toolEvidenceItemCount: number;
    aiActivityEventCount: number;
    toolFeedbackCount: number;
    chatMessageCount: number;
    usageIncluded: boolean;
  };
  artifacts: FlowExplorerDiagnosticArtifactSummary[];
  resultMarkdown: string;
}

export interface FlowExplorerDiagnosticArtifactSummary {
  name: string;
  kind: string;
  included: boolean;
  itemCount: number | null;
  characterCount: number | null;
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
  const normalizedJob = normalizeFlowExplorerJob(job);
  assertCompletedExportableJob(normalizedJob);

  return {
    schema: FLOW_EXPLORER_EXPORT_SCHEMA,
    version: FLOW_EXPLORER_EXPORT_VERSION,
    exportedAt,
    payload: {
      type: FLOW_EXPLORER_EXPORT_PAYLOAD_TYPE,
      resultContract: FLOW_EXPLORER_RESULT_CONTRACT,
      diagnostics: buildFlowExplorerExportDiagnostics(normalizedJob),
      job: normalizedJob
    }
  };
}

export function buildFlowExplorerExportDiagnostics(
  job: FlowExplorerJobStateSnapshot
): FlowExplorerExportDiagnostics {
  assertCompletedExportableJob(job);

  const result = job.result;
  const aiResponse = result.aiResponse;
  const resultMarkdown = renderFlowExplorerResultMarkdown(job);
  const contextSnapshot = asObject(job.contextSnapshot);
  const coverage = asObject(contextSnapshot?.['coverage']);
  const snippetCharacterCount = normalizeNumber(coverage?.['snippetCharacterCount']);

  return {
    resultContract: FLOW_EXPLORER_RESULT_CONTRACT,
    target: {
      systemId: job.systemId,
      endpointId: job.endpointId,
      httpMethod: job.httpMethod,
      endpointPath: job.endpointPath,
      branch: job.branch
    },
    request: {
      goal: job.goal,
      focusAreas: job.focusAreas,
      aiModel: job.aiModel,
      reasoningEffort: job.reasoningEffort
    },
    result: {
      goal: aiResponse.goal,
      confidence: aiResponse.confidence || aiResponse.overview.confidence,
      sectionModes: sectionModeIndex(aiResponse.sections),
      sourceReferenceCount: aiResponse.sourceReferences.length,
      visibilityLimitCount:
        aiResponse.globalVisibilityLimits.length +
        aiResponse.sections.reduce((count, section) => count + section.visibilityLimits.length, 0),
      openQuestionCount:
        aiResponse.globalOpenQuestions.length +
        aiResponse.sections.reduce((count, section) => count + section.openQuestions.length, 0)
    },
    context: {
      contextSnapshotIncluded: Boolean(contextSnapshot),
      repositoryCount: contextArrayLength(contextSnapshot, 'repositories'),
      flowNodeCount: normalizeNumber(coverage?.['flowNodeCount']) || contextArrayLength(contextSnapshot, 'flowNodes'),
      relationCount: normalizeNumber(coverage?.['relationCount']) || contextArrayLength(contextSnapshot, 'relations'),
      snippetCardCount:
        normalizeNumber(coverage?.['snippetCardCount']) || contextArrayLength(contextSnapshot, 'snippetCards'),
      snippetCharacterCount,
      snippetBudgetReached: Boolean(coverage?.['snippetBudgetReached']),
      clippingNotes: diagnosticClippingNotes(contextSnapshot, coverage),
      limitationCount: contextArrayLength(contextSnapshot, 'limitations')
    },
    workflow: {
      stepCount: job.steps.length,
      contextEvidenceItemCount: evidenceItemCount(job.contextSections),
      toolEvidenceItemCount: evidenceItemCount(job.toolEvidenceSections),
      aiActivityEventCount: job.aiActivityEvents.length,
      toolFeedbackCount: job.toolFeedback.length,
      chatMessageCount: job.chatMessages.length,
      usageIncluded: Boolean(result.usage)
    },
    artifacts: diagnosticArtifactSummary(job, resultMarkdown, snippetCharacterCount),
    resultMarkdown
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
  if (!envelopePayload || envelopePayload['type'] !== FLOW_EXPLORER_EXPORT_PAYLOAD_TYPE) {
    throw new Error('Plik eksportu nie zawiera wyniku Flow Explorera.');
  }

  if (envelopePayload['resultContract'] !== FLOW_EXPLORER_RESULT_CONTRACT) {
    throw new Error('Plik eksportu Flow Explorera ma nieobsługiwany kontrakt wyniku.');
  }

  const diagnostics = asObject(envelopePayload['diagnostics']);
  if (!diagnostics || diagnostics['resultContract'] !== FLOW_EXPLORER_RESULT_CONTRACT) {
    throw new Error('Plik eksportu nie zawiera diagnostyki Flow Explorera w aktualnym formacie.');
  }

  const job = normalizeFlowExplorerJob(envelopePayload['job']);
  assertCompletedExportableJob(job);

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
  assertNoLegacyResultFields(resultObject);
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
  assertNoLegacyResultFields(responseObject);
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
  if (!Array.isArray(sections)) {
    throw new Error('Flow Explorer result nie zawiera wymaganych czterech sekcji.');
  }

  const byId = new Map<FlowExplorerResultSectionId, FlowExplorerResultSection>();
  sections.forEach((section) => {
    const normalized = normalizeSection(section);
    if (byId.has(normalized.id)) {
      throw new Error(`Flow Explorer result zawiera zdublowaną sekcję ${normalized.id}.`);
    }
    byId.set(normalized.id, normalized);
  });

  const missingSectionIds = SECTION_IDS.filter((id) => !byId.has(id));
  if (missingSectionIds.length > 0) {
    throw new Error(`Flow Explorer result nie zawiera sekcji: ${missingSectionIds.join(', ')}.`);
  }

  return SECTION_IDS.map((id) => byId.get(id) as FlowExplorerResultSection);
}

function normalizeSection(section: unknown): FlowExplorerResultSection {
  const sectionObject = asObject(section);
  const id = normalizeSectionId(sectionObject?.['id']);
  if (!id) {
    throw new Error('Flow Explorer result zawiera sekcję bez aktualnego identyfikatora.');
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

type ExportableFlowExplorerJob = FlowExplorerJobStateSnapshot & {
  result: FlowExplorerResult & {
    aiResponse: FlowExplorerAiResponse;
  };
};

function assertCompletedExportableJob(
  job: FlowExplorerJobStateSnapshot
): asserts job is ExportableFlowExplorerJob {
  if (job.status !== 'COMPLETED') {
    throw new Error('Import i eksport wspiera tylko zakończone Flow Explorer analizy COMPLETED.');
  }
  if (hasActiveChat(job)) {
    throw new Error('Import i eksport nie wspiera analiz z aktywną odpowiedzią follow-up.');
  }
  if (!job.result || !job.result.aiResponse) {
    throw new Error('Flow Explorer export wymaga ustrukturyzowanego wyniku AI.');
  }
  if (job.result.goal !== job.goal || job.result.aiResponse.goal !== job.goal) {
    throw new Error('Flow Explorer export ma niespójny goal w jobie i wyniku AI.');
  }
  if (job.result.status !== 'COMPLETED') {
    throw new Error('Flow Explorer export wymaga wyniku o statusie COMPLETED.');
  }
}

function renderFlowExplorerResultMarkdown(job: ExportableFlowExplorerJob): string {
  const result = job.result;
  const aiResponse = result.aiResponse;
  const sectionMarkdown = aiResponse.sections
    .map((section) =>
      [
        `## ${section.title}`,
        `Mode: ${normalizeSectionMode(section.mode)}`,
        section.markdown || 'No confirmed details.',
        sourceRefsMarkdown(section.sourceRefs),
        listMarkdown('Visibility limits', section.visibilityLimits),
        listMarkdown('Open questions', section.openQuestions)
      ]
        .filter(Boolean)
        .join('\n\n')
    )
    .join('\n\n');

  return [
    '# Flow Explorer result',
    `Target: ${result.httpMethod} ${result.endpointPath}`,
    `System: ${result.systemId}`,
    `Goal: ${result.goal}`,
    `Confidence: ${aiResponse.confidence || aiResponse.overview.confidence || 'n/a'}`,
    '## Overview',
    aiResponse.overview.markdown || 'No overview.',
    sourceRefsMarkdown(aiResponse.overview.sourceRefs),
    sectionMarkdown,
    listMarkdown('Global visibility limits', aiResponse.globalVisibilityLimits),
    listMarkdown('Global open questions', aiResponse.globalOpenQuestions),
    sourceRefsMarkdown(aiResponse.sourceReferences)
  ]
    .filter(Boolean)
    .join('\n\n')
    .trim();
}

function sourceRefsMarkdown(sourceRefs: string[]): string {
  return listMarkdown('Source refs', sourceRefs);
}

function listMarkdown(title: string, items: string[]): string {
  if (!items.length) {
    return '';
  }
  return [`### ${title}`, ...items.map((item) => `- ${item}`)].join('\n');
}

function sectionModeIndex(
  sections: FlowExplorerResultSection[]
): Record<FlowExplorerResultSectionId, FlowExplorerResultSectionMode> {
  return SECTION_IDS.reduce((index, id) => {
    const section = sections.find((candidate) => candidate.id === id);
    index[id] = normalizeSectionMode(section?.mode);
    return index;
  }, {} as Record<FlowExplorerResultSectionId, FlowExplorerResultSectionMode>);
}

function diagnosticClippingNotes(
  contextSnapshot: Record<string, unknown> | null,
  coverage: Record<string, unknown> | null
): string[] {
  const notes = [
    Boolean(coverage?.['snippetBudgetReached']) ? 'Snippet budget reached.' : '',
    Boolean(coverage?.['maxDepthReached']) ? 'Max flow depth reached.' : '',
    Boolean(coverage?.['maxFilesReached']) ? 'Max file scan limit reached.' : '',
    Boolean(coverage?.['readFileLimitReached']) ? 'Read file limit reached.' : '',
    ...contextStringArray(contextSnapshot, 'limitations')
  ].filter(Boolean);

  return notes.length ? notes : ['No clipping reported by deterministic context.'];
}

function diagnosticArtifactSummary(
  job: ExportableFlowExplorerJob,
  resultMarkdown: string,
  snippetCharacterCount: number
): FlowExplorerDiagnosticArtifactSummary[] {
  return [
    {
      name: 'flow-explorer-result.md',
      kind: 'user-facing-markdown',
      included: Boolean(resultMarkdown),
      itemCount: 5,
      characterCount: resultMarkdown.length
    },
    {
      name: 'jobSnapshot',
      kind: 'diagnostic-json',
      included: true,
      itemCount: 1,
      characterCount: null
    },
    {
      name: 'contextSnapshot',
      kind: 'deterministic-context-json',
      included: Boolean(job.contextSnapshot),
      itemCount: contextArrayLength(asObject(job.contextSnapshot), 'flowNodes'),
      characterCount: snippetCharacterCount || null
    },
    {
      name: 'preparedPrompt',
      kind: 'canonical-prompt',
      included: Boolean(job.preparedPrompt),
      itemCount: null,
      characterCount: job.preparedPrompt ? job.preparedPrompt.length : null
    },
    {
      name: 'contextSections',
      kind: 'workflow-evidence',
      included: job.contextSections.length > 0,
      itemCount: evidenceItemCount(job.contextSections),
      characterCount: null
    },
    {
      name: 'toolEvidenceSections',
      kind: 'tool-evidence',
      included: job.toolEvidenceSections.length > 0,
      itemCount: evidenceItemCount(job.toolEvidenceSections),
      characterCount: null
    },
    {
      name: 'aiActivityEvents',
      kind: 'ai-activity',
      included: job.aiActivityEvents.length > 0,
      itemCount: job.aiActivityEvents.length,
      characterCount: null
    },
    {
      name: 'toolFeedback',
      kind: 'tool-quality-feedback',
      included: job.toolFeedback.length > 0,
      itemCount: job.toolFeedback.length,
      characterCount: null
    },
    {
      name: 'usage',
      kind: 'token-and-cost-usage',
      included: Boolean(job.result.usage),
      itemCount: job.result.usage ? 1 : 0,
      characterCount: null
    }
  ];
}

function evidenceItemCount(sections: AnalysisEvidenceSection[]): number {
  return sections.reduce((count, section) => count + section.items.length, 0);
}

function contextArrayLength(contextSnapshot: Record<string, unknown> | null, fieldName: string): number {
  const value = contextSnapshot?.[fieldName];
  return Array.isArray(value) ? value.length : 0;
}

function contextStringArray(contextSnapshot: Record<string, unknown> | null, fieldName: string): string[] {
  const value = contextSnapshot?.[fieldName];
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}

function assertNoLegacyResultFields(value: Record<string, unknown> | null): void {
  if (!value) {
    return;
  }
  const legacyFields = LEGACY_RESULT_FIELDS.filter((field) => Object.prototype.hasOwnProperty.call(value, field));
  if (legacyFields.length > 0) {
    throw new Error(`Flow Explorer export zawiera legacy pola wyniku: ${legacyFields.join(', ')}.`);
  }
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
  if (isFlowExplorerGoal(normalized)) {
    return normalized;
  }
  throw new Error('Flow Explorer export zawiera nieznany cel analizy.');
}

function normalizeFocusAreas(value: unknown): FlowExplorerFocusArea[] {
  const focusAreas = normalizeStringArray(value);
  const unsupported = focusAreas.filter((focusArea) => !isFlowExplorerFocusArea(focusArea));
  if (unsupported.length > 0) {
    throw new Error(`Flow Explorer export zawiera nieznane focus areas: ${unsupported.join(', ')}.`);
  }
  return Array.from(new Set(focusAreas.filter(isFlowExplorerFocusArea)));
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

const LEGACY_RESULT_FIELDS = [
  'endpointContract',
  'flowSteps',
  'businessRules',
  'testScenarios',
  'risksAndEdgeCases'
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
