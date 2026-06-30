import {
  AnalysisAiActivityEvent,
  AnalysisChatMessageResponse,
  AnalysisAiToolFeedback,
  AnalysisAiUsage,
  AnalysisEvidenceAttribute,
  AnalysisEvidenceReference,
  AnalysisEvidenceItem,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse,
  AnalysisReport,
  AnalysisReportMeta,
  AnalysisReportSection
} from '../../../core/models/analysis.models';
import { normalizeAnalysisReport } from '../../../core/utils/analysis-import-export.utils';
import { formatFileTimestamp, sanitizeFileNamePart } from '../../../core/utils/json-file.utils';
import { buildFlowExplorerReportMarkdown } from './flow-explorer-result-markdown.utils';
import {
  FlowExplorerAiResponse,
  FlowExplorerAnalysisGoal,
  FlowExplorerFocusArea,
  FlowExplorerJobStateSnapshot,
  FlowExplorerResult,
  FlowExplorerResultOverview,
  FlowExplorerResultSection,
  FlowExplorerResultSectionId,
  FlowExplorerResultSectionMode,
  FlowExplorerSectionMode,
  FlowExplorerSectionModeAssignment
} from '../models/flow-explorer.models';

export const FLOW_EXPLORER_EXPORT_SCHEMA = 'tdw.flow-explorer-export';
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
    sectionModes: FlowExplorerSectionModeAssignment[];
    aiModel: string;
    reasoningEffort: string;
  };
  result: {
    goal: FlowExplorerAnalysisGoal;
    confidence: string;
    sectionModes: Partial<Record<FlowExplorerResultSectionId, FlowExplorerResultSectionMode>>;
    sourceReferenceCount: number;
    visibilityLimitCount: number;
    openQuestionCount: number;
    followUpPromptCount: number;
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
  origin: 'live' | 'local' | 'imported';
  exportedAt: string;
  fileName: string;
  job: FlowExplorerJobStateSnapshot;
  localRunId?: string;
  localRunName?: string;
  continuationEnabled?: boolean;
}

export interface FlowExplorerImportOptions {
  requireCompleted?: boolean;
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

  const report = job.report;
  const resultMarkdown = buildFlowExplorerReportMarkdown(
    report,
    `${job.httpMethod} ${job.endpointPath}`.trim()
  );
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
      sectionModes: job.sectionModes,
      aiModel: job.aiModel,
      reasoningEffort: job.reasoningEffort
    },
    result: {
      goal: job.goal,
      confidence: reportConfidence(report),
      sectionModes: reportSectionModeIndex(job),
      sourceReferenceCount: reportReferenceCount(report),
      visibilityLimitCount: reportMetaItemCount(report, 'visibilityLimits'),
      openQuestionCount: reportMetaItemCount(report, 'openQuestions'),
      followUpPromptCount: 0
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
      usageIncluded: Boolean(usageFromJob(job))
    },
    artifacts: diagnosticArtifactSummary(job, resultMarkdown, snippetCharacterCount),
    resultMarkdown
  };
}

export function parseImportedFlowExplorerAnalysis(payload: unknown, options: FlowExplorerImportOptions = {}): {
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
  if (options.requireCompleted ?? true) {
    assertCompletedExportableJob(job);
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
  const focusAreas = normalizeFocusAreas(jobObject['focusAreas']);
  const sectionModes = normalizeSectionModeAssignments(jobObject['sectionModes'], focusAreas);
  const activeSectionIds = activeSectionIdsFor(sectionModes);

  return {
    jobId: normalizeString(jobObject['jobId']),
    systemId: normalizeString(jobObject['systemId']),
    endpointId: normalizeString(jobObject['endpointId']),
    httpMethod: normalizeString(jobObject['httpMethod']),
    endpointPath: normalizeString(jobObject['endpointPath']),
    branch: normalizeString(jobObject['branch']),
    goal: normalizeGoal(jobObject['goal']),
    focusAreas,
    sectionModes,
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
    result: asObject(jobObject['result']) ? normalizeResult(jobObject['result'], activeSectionIds) : null,
    report: asObject(jobObject['report']) ? normalizeAnalysisReport(jobObject['report']) : null
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

function normalizeResult(result: unknown, activeSectionIds: FlowExplorerResultSectionId[]): FlowExplorerResult {
  const resultObject = asObject(result);
  assertOnlySupportedFields(resultObject, RESULT_FIELDS, 'Flow Explorer result');
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
      ? normalizeAiResponse(resultObject?.['aiResponse'], activeSectionIds)
      : null,
    usage: normalizeUsage(resultObject?.['usage'])
  };
}

function normalizeAiResponse(
  response: unknown,
  activeSectionIds: FlowExplorerResultSectionId[]
): FlowExplorerAiResponse {
  const responseObject = asObject(response);
  assertOnlySupportedFields(responseObject, AI_RESPONSE_FIELDS, 'Flow Explorer aiResponse');
  return {
    goal: normalizeGoal(responseObject?.['goal']),
    audience: normalizeString(responseObject?.['audience']) || 'business_or_system_analyst_tester',
    overview: normalizeOverview(responseObject?.['overview']),
    sections: normalizeSections(responseObject?.['sections'], activeSectionIds),
    globalVisibilityLimits: normalizeStringArray(responseObject?.['globalVisibilityLimits']),
    globalOpenQuestions: normalizeStringArray(responseObject?.['globalOpenQuestions']),
    sourceReferences: normalizeStringArray(responseObject?.['sourceReferences']),
    confidence: normalizeString(responseObject?.['confidence']),
    followUpPrompts: normalizeStringArray(responseObject?.['followUpPrompts'])
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

function normalizeSections(
  sections: unknown,
  activeSectionIds: FlowExplorerResultSectionId[]
): FlowExplorerResultSection[] {
  if (!Array.isArray(sections)) {
    throw new Error('Flow Explorer result nie zawiera wymaganej listy sekcji.');
  }

  const byId = new Map<FlowExplorerResultSectionId, FlowExplorerResultSection>();
  sections.forEach((section) => {
    const normalized = normalizeSection(section);
    if (normalizeString(normalized.mode).toLowerCase() === 'off') {
      throw new Error(`Flow Explorer result zawiera sekcję ${normalized.id} z trybem OFF.`);
    }
    if (byId.has(normalized.id)) {
      throw new Error(`Flow Explorer result zawiera zdublowaną sekcję ${normalized.id}.`);
    }
    byId.set(normalized.id, normalized);
  });

  const unexpectedSectionIds = Array.from(byId.keys()).filter((id) => !activeSectionIds.includes(id));
  if (unexpectedSectionIds.length > 0) {
    throw new Error(`Flow Explorer result zawiera sekcje oznaczone jako OFF: ${unexpectedSectionIds.join(', ')}.`);
  }

  const missingSectionIds = activeSectionIds.filter((id) => !byId.has(id));
  if (missingSectionIds.length > 0) {
    throw new Error(`Flow Explorer result nie zawiera sekcji: ${missingSectionIds.join(', ')}.`);
  }

  return activeSectionIds.map((id) => byId.get(id) as FlowExplorerResultSection);
}

function normalizeSection(section: unknown): FlowExplorerResultSection {
  const sectionObject = asObject(section);
  const rawId = normalizeString(sectionObject?.['id']);
  const id = normalizeSectionId(rawId);
  if (!id) {
    throw new Error('Flow Explorer result zawiera sekcję bez aktualnego identyfikatora.');
  }
  const rawTitle = normalizeString(sectionObject?.['title']);
  const title = rawTitle || sectionTitle(id);

  return {
    id,
    title,
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
  report: AnalysisReport;
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
  if (!job.report) {
    throw new Error('Flow Explorer export wymaga kanonicznego raportu analizy.');
  }
}

function reportConfidence(report: AnalysisReport): string {
  const overview = report.sections.find((section) => normalizeString(section.id) === 'OVERVIEW');
  return normalizeString(report.meta?.confidence) || normalizeString(overview?.meta?.confidence);
}

function reportSectionModeIndex(
  job: ExportableFlowExplorerJob
): Partial<Record<FlowExplorerResultSectionId, FlowExplorerResultSectionMode>> {
  const modes = new Map(job.sectionModes.map((sectionMode) => [sectionMode.id, sectionMode.mode]));
  return job.report.sections.reduce((index, section) => {
    const id = reportFlowSectionId(section);
    if (id) {
      index[id] = normalizeSectionMode(modes.get(id));
    }
    return index;
  }, {} as Partial<Record<FlowExplorerResultSectionId, FlowExplorerResultSectionMode>>);
}

function reportReferenceCount(report: AnalysisReport): number {
  return reportMetaReferenceCount(report.meta) +
    report.sections.reduce((count, section) => count + reportMetaReferenceCount(section.meta), 0);
}

function reportMetaReferenceCount(meta: AnalysisReportMeta | null | undefined): number {
  return meta?.references?.length ?? 0;
}

function reportMetaItemCount(
  report: AnalysisReport,
  field: 'visibilityLimits' | 'openQuestions'
): number {
  return reportMetaListCount(report.meta, field) +
    report.sections.reduce((count, section) => count + reportMetaListCount(section.meta, field), 0);
}

function reportMetaListCount(
  meta: AnalysisReportMeta | null | undefined,
  field: 'visibilityLimits' | 'openQuestions'
): number {
  return meta?.[field]?.length ?? 0;
}

function reportFlowSectionId(section: AnalysisReportSection): FlowExplorerResultSectionId | null {
  const id = normalizeString(section.id);
  return isFlowExplorerSectionId(id) ? id : null;
}

function usageFromJob(job: FlowExplorerJobStateSnapshot): AnalysisAiUsage | null {
  const aiStepUsage = job.steps.find((step) => step.code === 'AI_ANALYSIS' && step.usage)?.usage;
  if (aiStepUsage) {
    return aiStepUsage;
  }
  return [...job.steps].reverse().find((step) => step.usage)?.usage ?? null;
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
      itemCount: 1 + job.report.sections.length,
      characterCount: resultMarkdown.length
    },
    {
      name: 'analysisReport',
      kind: 'canonical-report-json',
      included: Boolean(job.report),
      itemCount: job.report ? 1 + job.report.sections.length : 0,
      characterCount: null
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
      included: Boolean(usageFromJob(job)),
      itemCount: usageFromJob(job) ? 1 : 0,
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

function assertOnlySupportedFields(
  value: Record<string, unknown> | null,
  allowedFields: readonly string[],
  owner: string
): void {
  if (!value) {
    return;
  }
  const allowed = new Set(allowedFields);
  const unsupported = Object.keys(value).filter((field) => !allowed.has(field));
  if (unsupported.length > 0) {
    throw new Error(`${owner} zawiera nieobsługiwane pola: ${unsupported.join(', ')}.`);
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

function normalizeSectionModeAssignments(
  value: unknown,
  focusAreas: FlowExplorerFocusArea[]
): FlowExplorerSectionModeAssignment[] {
  const byId = new Map<FlowExplorerResultSectionId, FlowExplorerSectionMode>();
  if (Array.isArray(value)) {
    value.forEach((item) => {
      const itemObject = asObject(item);
      const id = normalizeSectionId(itemObject?.['id']);
      const mode = normalizeRequestedSectionMode(itemObject?.['mode']);
      if (id && mode) {
        byId.set(id, mode);
      }
    });
  }

  if (byId.size === 0) {
    focusAreas.forEach((focusArea) => byId.set(focusArea, 'DEEP'));
  }

  return SECTION_IDS.map((id) => ({
    id,
    title: sectionTitle(id),
    mode: byId.get(id) ?? 'COMPACT'
  }));
}

function activeSectionIdsFor(sectionModes: FlowExplorerSectionModeAssignment[]): FlowExplorerResultSectionId[] {
  return sectionModes
    .filter((sectionMode) => sectionMode.mode !== 'OFF')
    .map((sectionMode) => sectionMode.id);
}

function normalizeRequestedSectionMode(value: unknown): FlowExplorerSectionMode | null {
  const normalized = normalizeString(value).toUpperCase();
  if (normalized === 'OFF' || normalized === 'COMPACT' || normalized === 'DEEP') {
    return normalized;
  }
  return null;
}

function normalizeSectionId(value: unknown): FlowExplorerResultSectionId | null {
  const normalized = normalizeString(value);
  return isFlowExplorerSectionId(normalized) ? normalized : null;
}

function normalizeSectionMode(value: unknown): FlowExplorerResultSectionMode {
  const normalized = normalizeString(value).toLowerCase();
  if (normalized === 'deep') {
    return 'deep';
  }
  if (normalized === 'off') {
    return 'off';
  }
  return 'compact';
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
  'FUNCTIONAL_FLOW',
  'VALIDATIONS',
  'PERSISTENCE',
  'INTEGRATIONS'
];

const RESULT_FIELDS = [
  'status',
  'systemId',
  'endpointId',
  'httpMethod',
  'endpointPath',
  'branch',
  'goal',
  'prompt',
  'aiResponse',
  'usage'
];

const AI_RESPONSE_FIELDS = [
  'goal',
  'audience',
  'overview',
  'sections',
  'globalVisibilityLimits',
  'globalOpenQuestions',
  'sourceReferences',
  'confidence',
  'followUpPrompts'
];

function sectionTitle(id: FlowExplorerResultSectionId): string {
  switch (id) {
    case 'FUNCTIONAL_FLOW':
      return 'Functional flow';
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
