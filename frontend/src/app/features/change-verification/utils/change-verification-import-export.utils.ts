import {
  AnalysisAiActivityEvent,
  AnalysisAiUsage,
  AnalysisEvidenceAttribute,
  AnalysisEvidenceItem,
  AnalysisEvidenceReference,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse
} from '../../../core/models/analysis.models';
import { normalizeAnalysisReport } from '../../../core/utils/analysis-import-export.utils';
import { formatFileTimestamp, sanitizeFileNamePart } from '../../../core/utils/json-file.utils';
import {
  ChangeVerificationDecision,
  ChangeVerificationDecisionStatus,
  ChangeVerificationJobStateSnapshot,
  ChangeVerificationRuleEvidence,
  ChangeVerificationRuleLedger,
  ChangeVerificationRuleResult,
  ChangeVerificationRuleSource,
  ChangeVerificationResult
} from '../models/change-verification.models';

export const CHANGE_VERIFICATION_EXPORT_SCHEMA = 'tdw.change-verification-export';
export const CHANGE_VERIFICATION_EXPORT_VERSION = 6;
export const CHANGE_VERIFICATION_EXPORT_PAYLOAD_TYPE = 'change-verification-analysis';
export const CHANGE_VERIFICATION_RESULT_CONTRACT = 'change-verification-result-v5';

export interface ChangeVerificationExportEnvelope {
  schema: string;
  version: number;
  exportedAt: string;
  payload: {
    type: typeof CHANGE_VERIFICATION_EXPORT_PAYLOAD_TYPE;
    resultContract: typeof CHANGE_VERIFICATION_RESULT_CONTRACT;
    diagnostics: ChangeVerificationExportDiagnostics;
    job: ChangeVerificationJobStateSnapshot;
  };
}

export interface ChangeVerificationExportDiagnostics {
  resultContract: typeof CHANGE_VERIFICATION_RESULT_CONTRACT;
  target: {
    issueKey: string;
    issueUrl: string;
  };
  request: {
    checkStoryCompliance: boolean;
    checkInstructionCompliance: boolean;
    aiModel: string;
    reasoningEffort: string;
  };
  result: {
    status: string;
    decisionStatus: string;
    totalRules: number;
    needsAttentionCount: number;
    visibilityLimitCount: number;
  };
  workflow: {
    stepCount: number;
    contextEvidenceItemCount: number;
    toolEvidenceItemCount: number;
    aiActivityEventCount: number;
    usageIncluded: boolean;
  };
  copilotRuntime: ChangeVerificationCopilotRuntimeDiagnostics | null;
  artifacts: ChangeVerificationDiagnosticArtifactSummary[];
}

export interface ChangeVerificationCopilotRuntimeDiagnostics {
  sdkVersion: string | null;
  cliVersion: string | null;
  protocolVersion: number | null;
  minimumCliVersion: string | null;
  compatible: boolean;
}

export interface ChangeVerificationDiagnosticArtifactSummary {
  name: string;
  kind: string;
  included: boolean;
  itemCount: number | null;
  characterCount: number | null;
}

export interface ChangeVerificationExportState {
  origin: 'live' | 'imported' | 'local';
  exportedAt: string;
  fileName: string;
  localRunId?: string;
  localRunName?: string;
  job: ChangeVerificationJobStateSnapshot;
}

export function buildChangeVerificationExportEnvelope(
  job: ChangeVerificationJobStateSnapshot,
  exportedAt: string
): ChangeVerificationExportEnvelope {
  const normalizedJob = normalizeChangeVerificationJob(job);
  assertCompletedExportableJob(normalizedJob);

  return {
    schema: CHANGE_VERIFICATION_EXPORT_SCHEMA,
    version: CHANGE_VERIFICATION_EXPORT_VERSION,
    exportedAt,
    payload: {
      type: CHANGE_VERIFICATION_EXPORT_PAYLOAD_TYPE,
      resultContract: CHANGE_VERIFICATION_RESULT_CONTRACT,
      diagnostics: buildChangeVerificationExportDiagnostics(normalizedJob),
      job: normalizedJob
    }
  };
}

export function buildChangeVerificationExportDiagnostics(
  job: ChangeVerificationJobStateSnapshot
): ChangeVerificationExportDiagnostics {
  assertCompletedExportableJob(job);

  const ledger = job.result.ruleLedger;

  return {
    resultContract: CHANGE_VERIFICATION_RESULT_CONTRACT,
    target: {
      issueKey: job.issueKey,
      issueUrl: job.issueUrl
    },
    request: {
      checkStoryCompliance: job.checkStoryCompliance,
      checkInstructionCompliance: job.checkInstructionCompliance,
      aiModel: job.aiModel,
      reasoningEffort: job.reasoningEffort
    },
    result: {
      status: job.result.status,
      decisionStatus: ledger.decision.status,
      totalRules: ledger.decision.totalRules,
      needsAttentionCount: ledger.decision.notSatisfied + ledger.decision.notVerified,
      visibilityLimitCount: ledger.visibilityLimits.length
    },
    workflow: {
      stepCount: job.steps.length,
      contextEvidenceItemCount: evidenceItemCount(job.contextSections),
      toolEvidenceItemCount: evidenceItemCount(job.toolEvidenceSections),
      aiActivityEventCount: job.aiActivityEvents.length,
      usageIncluded: Boolean(job.result.usage)
    },
    copilotRuntime: buildCopilotRuntimeDiagnostics(job.aiActivityEvents),
    artifacts: [
      {
        name: 'change-verification-result',
        kind: 'result-json',
        included: true,
        itemCount: 1,
        characterCount: JSON.stringify(job.result).length
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
        name: 'analysisReport',
        kind: 'canonical-report',
        included: true,
        itemCount: job.report.sections.length,
        characterCount: JSON.stringify(job.report).length
      },
      {
        name: 'preparedPrompt',
        kind: 'canonical-prompt',
        included: Boolean(job.preparedPrompt),
        itemCount: null,
        characterCount: job.preparedPrompt ? job.preparedPrompt.length : null
      },
      {
        name: 'usage',
        kind: 'token-and-cost-usage',
        included: Boolean(job.result.usage),
        itemCount: job.result.usage ? 1 : 0,
        characterCount: null
      }
    ]
  };
}

export function parseImportedChangeVerificationResult(
  payload: unknown,
  options: { requireCompleted?: boolean } = {}
): {
  exportedAt: string;
  job: ChangeVerificationJobStateSnapshot;
} {
  const payloadObject = asObject(payload);
  if (!payloadObject || payloadObject['schema'] !== CHANGE_VERIFICATION_EXPORT_SCHEMA) {
    throw new Error('Wybierz plik wyeksportowany z Change Verification.');
  }

  if (Number(payloadObject['version']) !== CHANGE_VERIFICATION_EXPORT_VERSION) {
    throw new Error('Ten plik eksportu Change Verification ma nieobsługiwaną wersję formatu.');
  }

  const envelopePayload = asObject(payloadObject['payload']);
  if (!envelopePayload || envelopePayload['type'] !== CHANGE_VERIFICATION_EXPORT_PAYLOAD_TYPE) {
    throw new Error('Plik eksportu nie zawiera wyniku Change Verification.');
  }

  if (envelopePayload['resultContract'] !== CHANGE_VERIFICATION_RESULT_CONTRACT) {
    throw new Error('Plik eksportu Change Verification ma nieobsługiwany kontrakt wyniku.');
  }

  const diagnostics = asObject(envelopePayload['diagnostics']);
  if (!diagnostics || diagnostics['resultContract'] !== CHANGE_VERIFICATION_RESULT_CONTRACT) {
    throw new Error('Plik eksportu nie zawiera diagnostyki Change Verification w aktualnym formacie.');
  }

  const job = normalizeChangeVerificationJob(envelopePayload['job']);
  if (options.requireCompleted ?? true) {
    assertCompletedExportableJob(job);
  }

  return {
    exportedAt: normalizeString(payloadObject['exportedAt']),
    job
  };
}

export function normalizeChangeVerificationJob(job: unknown): ChangeVerificationJobStateSnapshot {
  const jobObject = asObject(job);
  if (!jobObject) {
    throw new Error('Plik eksportu nie zawiera poprawnego obiektu Change Verification joba.');
  }

  return {
    jobId: normalizeString(jobObject['jobId']),
    issueKey: normalizeString(jobObject['issueKey']),
    issueUrl: normalizeString(jobObject['issueUrl']),
    checkStoryCompliance: normalizeBoolean(jobObject['checkStoryCompliance']),
    checkInstructionCompliance: normalizeBoolean(jobObject['checkInstructionCompliance']),
    aiModel: normalizeString(jobObject['aiModel']),
    reasoningEffort: normalizeString(jobObject['reasoningEffort']),
    status: normalizeString(jobObject['status']),
    currentStepCode: normalizeNullableString(jobObject['currentStepCode']),
    currentStepLabel: normalizeNullableString(jobObject['currentStepLabel']),
    errorCode: normalizeNullableString(jobObject['errorCode']),
    errorMessage: normalizeNullableString(jobObject['errorMessage']),
    createdAt: normalizeString(jobObject['createdAt']),
    updatedAt: normalizeString(jobObject['updatedAt']),
    completedAt: normalizeNullableString(jobObject['completedAt']),
    steps: Array.isArray(jobObject['steps']) ? jobObject['steps'].map(normalizeStep) : [],
    contextSections: Array.isArray(jobObject['contextSections'])
      ? jobObject['contextSections'].map(normalizeEvidenceSection)
      : [],
    toolEvidenceSections: Array.isArray(jobObject['toolEvidenceSections'])
      ? jobObject['toolEvidenceSections'].map(normalizeEvidenceSection)
      : [],
    aiActivityEvents: Array.isArray(jobObject['aiActivityEvents'])
      ? jobObject['aiActivityEvents'].map(normalizeAiActivityEvent)
      : [],
    preparedPrompt: normalizeString(jobObject['preparedPrompt']),
    result: asObject(jobObject['result']) ? normalizeResult(jobObject['result']) : null,
    report: asObject(jobObject['report']) ? normalizeAnalysisReport(jobObject['report']) : null
  };
}

export function buildChangeVerificationExportFileName(
  job: ChangeVerificationJobStateSnapshot,
  exportedAt: string
): string {
  const issue = sanitizeFileNamePart(job.issueKey || job.issueUrl || job.jobId || 'change-verification');
  const status = sanitizeFileNamePart((job.status || 'result').toLowerCase());
  return `change-verification-${issue}-${status}-${formatFileTimestamp(exportedAt)}.json`;
}

type ExportableChangeVerificationJob = ChangeVerificationJobStateSnapshot & {
  result: ChangeVerificationResult;
  report: NonNullable<ChangeVerificationJobStateSnapshot['report']>;
};

function assertCompletedExportableJob(
  job: ChangeVerificationJobStateSnapshot
): asserts job is ExportableChangeVerificationJob {
  if (job.status !== 'COMPLETED') {
    throw new Error('Import i eksport wspiera tylko zakończone Change Verification runy COMPLETED.');
  }
  if (!job.result) {
    throw new Error('Change Verification export wymaga wyniku weryfikacji.');
  }
  if (!job.report) {
    throw new Error('Change Verification export wymaga kanonicznego raportu analizy.');
  }
  if (job.result.ruleLedger.additionalChecks.length > 5) {
    throw new Error('Change Verification obsługuje maksymalnie 5 dodatkowych kontroli AI.');
  }
  const sectionIds = new Set(job.report.sections.map((section) => normalizeString(section.id).toUpperCase()));
  if (!sectionIds.has('RULE_LEDGER')) {
    throw new Error('Raport Change Verification nie zawiera projekcji rule ledger.');
  }
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

function normalizeResult(result: unknown): ChangeVerificationResult {
  const resultObject = asObject(result);
  if (!resultObject) {
    throw new Error('Wynik Change Verification nie jest poprawnym obiektem.');
  }
  return {
    status: normalizeString(resultObject['status']),
    issueKey: normalizeString(resultObject['issueKey']),
    issueUrl: normalizeString(resultObject['issueUrl']),
    prompt: normalizeString(resultObject['prompt']),
    ruleLedger: normalizeRuleLedger(resultObject['ruleLedger']),
    usage: normalizeUsage(resultObject['usage'])
  };
}

function normalizeRuleLedger(value: unknown): ChangeVerificationRuleLedger {
  const object = asObject(value);
  if (!object || !Array.isArray(object['rules']) || !Array.isArray(object['additionalChecks'])
    || !Array.isArray(object['visibilityLimits'])) {
    throw new Error('Wynik Change Verification nie zawiera pełnego rule ledger.');
  }
  const rules = object['rules'].map((rule) => normalizeRule(rule, false));
  const additionalChecks = object['additionalChecks'].map((rule) => normalizeRule(rule, true));
  if (additionalChecks.length > 5) {
    throw new Error('Rule ledger zawiera więcej niż 5 dodatkowych kontroli AI.');
  }
  const ids = [...rules, ...additionalChecks].map((rule) => rule.id);
  if (new Set(ids).size !== ids.length) {
    throw new Error('Rule ledger zawiera powtórzone identyfikatory reguł.');
  }
  const knownIds = new Set(ids);
  const visibilityLimits = object['visibilityLimits'].map((value) => {
    const limit = asObject(value);
    const message = requiredString(limit?.['message'], 'Visibility limit nie zawiera opisu.');
    const affectedRuleIds = requiredStringArray(limit?.['affectedRuleIds'], 'Visibility limit nie zawiera affectedRuleIds.');
    if (affectedRuleIds.some((id) => !knownIds.has(id))) {
      throw new Error('Visibility limit wskazuje nieistniejącą regułę.');
    }
    return { message, affectedRuleIds };
  });
  const derived = deriveDecision(rules);
  const supplied = normalizeDecision(object['decision']);
  if (JSON.stringify(derived) !== JSON.stringify(supplied)) {
    throw new Error('Decyzja w imporcie nie odpowiada outcome reguł źródłowych.');
  }
  return {
    storyComplianceRequested: normalizeBoolean(object['storyComplianceRequested']),
    instructionComplianceRequested: normalizeBoolean(object['instructionComplianceRequested']),
    decision: derived,
    rules,
    additionalChecks,
    visibilityLimits
  };
}

function normalizeDecision(value: unknown): ChangeVerificationDecision {
  const object = asObject(value);
  if (!object) {
    throw new Error('Rule ledger nie zawiera decyzji.');
  }
  return {
    status: enumValue(object['status'], ['READY', 'NEEDS_ACTION', 'NEEDS_EVIDENCE', 'INCONCLUSIVE'], 'Nieobsługiwany status decyzji.'),
    totalRules: requiredNumber(object['totalRules'], 'Decyzja nie zawiera totalRules.'),
    satisfied: requiredNumber(object['satisfied'], 'Decyzja nie zawiera satisfied.'),
    notSatisfied: requiredNumber(object['notSatisfied'], 'Decyzja nie zawiera notSatisfied.'),
    notVerified: requiredNumber(object['notVerified'], 'Decyzja nie zawiera notVerified.')
  };
}

function deriveDecision(rules: ChangeVerificationRuleResult[]): ChangeVerificationDecision {
  const satisfied = rules.filter((rule) => rule.outcome === 'SATISFIED').length;
  const notSatisfied = rules.filter((rule) => rule.outcome === 'NOT_SATISFIED').length;
  const notVerified = rules.filter((rule) => rule.outcome === 'NOT_VERIFIED').length;
  const status: ChangeVerificationDecisionStatus = rules.length === 0
    ? 'INCONCLUSIVE'
    : notSatisfied > 0
      ? 'NEEDS_ACTION'
      : notVerified > 0
        ? 'NEEDS_EVIDENCE'
        : 'READY';
  return { status, totalRules: rules.length, satisfied, notSatisfied, notVerified };
}

function normalizeRule(value: unknown, additional: boolean): ChangeVerificationRuleResult {
  const object = asObject(value);
  if (!object || !Array.isArray(object['evidence']) || !Array.isArray(object['missingEvidence'])
    || !Array.isArray(object['signals'])) {
    throw new Error('Reguła Change Verification nie spełnia aktualnego kontraktu.');
  }
  const scope = enumValue(object['scope'], ['STORY', 'INSTRUCTION', 'ADDITIONAL'], 'Reguła ma nieobsługiwany scope.');
  const source = normalizeRuleSource(object['source']);
  const outcome = enumValue(object['outcome'], ['SATISFIED', 'NOT_SATISFIED', 'NOT_VERIFIED'], 'Reguła ma nieobsługiwany outcome.');
  const releaseImpact = enumValue(object['releaseImpact'], ['NONE', 'REVIEW', 'BLOCKER'], 'Reguła ma nieobsługiwany releaseImpact.');
  const interpretationType = enumValue(object['interpretationType'], ['EXPLICIT', 'NORMALIZED', 'CONFLICTING', 'NOT_VERIFIABLE', 'INFERRED'], 'Reguła ma nieobsługiwany typ interpretacji.');
  const evidence = object['evidence'].map(normalizeRuleEvidence);
  const missingEvidence = requiredStringArray(object['missingEvidence'], 'Reguła ma niepoprawne missingEvidence.');
  const signals = requiredStringArray(object['signals'], 'Reguła ma niepoprawne signals.');
  const rule: ChangeVerificationRuleResult = {
    id: requiredString(object['id'], 'Reguła nie zawiera id.'),
    scope,
    source,
    normalizedRule: requiredString(object['normalizedRule'], 'Reguła nie zawiera interpretacji.'),
    interpretationType,
    outcome,
    releaseImpact,
    conclusion: requiredString(object['conclusion'], 'Reguła nie zawiera wniosku.'),
    evidence,
    missingEvidence,
    action: normalizeNullableString(object['action']),
    rationale: normalizeNullableString(object['rationale']),
    riskIfOmitted: normalizeNullableString(object['riskIfOmitted']),
    signals,
    confidence: normalizeNullableString(object['confidence'])
  };
  if ((additional && (scope !== 'ADDITIONAL' || source.type !== 'AI_SUGGESTION'))
    || (!additional && (scope === 'ADDITIONAL' || source.type === 'AI_SUGGESTION' || interpretationType === 'INFERRED'))) {
    throw new Error('Reguła znajduje się w niewłaściwej części ledgeru.');
  }
  if (additional && (interpretationType !== 'INFERRED' || !rule.rationale || !rule.riskIfOmitted
    || signals.length === 0 || !['HIGH', 'MEDIUM', 'LOW'].includes((rule.confidence ?? '').toUpperCase()))) {
    throw new Error('Dodatkowa kontrola AI nie zawiera wymaganych metadanych.');
  }
  if (outcome === 'SATISFIED' && (evidence.length === 0 || releaseImpact !== 'NONE')) {
    throw new Error('Spełniona reguła wymaga dowodu i releaseImpact NONE.');
  }
  if (outcome !== 'SATISFIED' && !rule.action) {
    throw new Error('Reguła wymagająca uwagi nie zawiera action.');
  }
  if (outcome === 'NOT_VERIFIED' && missingEvidence.length === 0) {
    throw new Error('Niezweryfikowana reguła nie zawiera missingEvidence.');
  }
  return rule;
}

function normalizeRuleSource(value: unknown): ChangeVerificationRuleSource {
  const object = asObject(value);
  return {
    type: enumValue(object?.['type'], ['ACCEPTANCE_CRITERION', 'JIRA_DESCRIPTION', 'JIRA_COMMENT', 'CONFLUENCE', 'REPOSITORY_INSTRUCTION', 'OPERATOR_INSTRUCTION', 'AI_SUGGESTION'], 'Nieobsługiwany typ źródła reguły.'),
    label: requiredString(object?.['label'], 'Źródło reguły nie zawiera label.'),
    reference: requiredString(object?.['reference'], 'Źródło reguły nie zawiera reference.'),
    quote: requiredString(object?.['quote'], 'Źródło reguły nie zawiera quote.')
  };
}

function normalizeRuleEvidence(value: unknown): ChangeVerificationRuleEvidence {
  const object = asObject(value);
  return {
    summary: requiredString(object?.['summary'], 'Evidence nie zawiera summary.'),
    reference: requiredString(object?.['reference'], 'Evidence nie zawiera reference.')
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

function evidenceItemCount(sections: AnalysisEvidenceSection[]): number {
  return sections.reduce((count, section) => count + section.items.length, 0);
}

function buildCopilotRuntimeDiagnostics(
  events: AnalysisAiActivityEvent[]
): ChangeVerificationCopilotRuntimeDiagnostics | null {
  const runtimeEvent = events.reduce<AnalysisAiActivityEvent | null>(
    (latest, event) => event.type === 'platform.copilot_runtime' ? event : latest,
    null
  );
  if (!runtimeEvent) {
    return null;
  }

  const details = runtimeEvent.details;
  return {
    sdkVersion: normalizeNullableString(details['sdkVersion']),
    cliVersion: normalizeNullableString(details['cliVersion']),
    protocolVersion: normalizeNullableNumber(details['protocolVersion']),
    minimumCliVersion: normalizeNullableString(details['minimumCliVersion']),
    compatible: normalizeBoolean(details['compatible'])
  };
}

function requiredString(value: unknown, message: string): string {
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(message);
  }
  return value.trim();
}

function requiredStringArray(value: unknown, message: string): string[] {
  if (!Array.isArray(value) || value.some((item) => typeof item !== 'string' || !item.trim())) {
    throw new Error(message);
  }
  return value.map((item) => (item as string).trim());
}

function requiredNumber(value: unknown, message: string): number {
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 0) {
    throw new Error(message);
  }
  return value;
}

function enumValue<const T extends string>(value: unknown, allowed: readonly T[], message: string): T {
  const normalized = normalizeString(value).trim().toUpperCase();
  if (!allowed.includes(normalized as T)) {
    throw new Error(message);
  }
  return normalized as T;
}

function normalizeString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function normalizeNullableString(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function normalizeBoolean(value: unknown): boolean {
  return value === true;
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
