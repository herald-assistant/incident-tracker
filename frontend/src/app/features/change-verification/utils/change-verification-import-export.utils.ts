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
  ChangeVerificationCompliance,
  ChangeVerificationFinding,
  ChangeVerificationFindingSeverity,
  ChangeVerificationJobStateSnapshot,
  ChangeVerificationResult
} from '../models/change-verification.models';

export const CHANGE_VERIFICATION_EXPORT_SCHEMA = 'tdw.change-verification-export';
export const CHANGE_VERIFICATION_EXPORT_VERSION = 4;
export const CHANGE_VERIFICATION_EXPORT_PAYLOAD_TYPE = 'change-verification-analysis';
export const CHANGE_VERIFICATION_RESULT_CONTRACT = 'change-verification-result-v4';

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
    complianceStatus: string;
    findingCount: number;
    visibilityLimitCount: number;
  };
  workflow: {
    stepCount: number;
    contextEvidenceItemCount: number;
    toolEvidenceItemCount: number;
    aiActivityEventCount: number;
    usageIncluded: boolean;
  };
  artifacts: ChangeVerificationDiagnosticArtifactSummary[];
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

  const compliance = job.result.compliance;

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
      complianceStatus: compliance.status,
      findingCount: compliance.findings.length,
      visibilityLimitCount: uniqueValues(compliance.visibilityLimits).length
    },
    workflow: {
      stepCount: job.steps.length,
      contextEvidenceItemCount: evidenceItemCount(job.contextSections),
      toolEvidenceItemCount: evidenceItemCount(job.toolEvidenceSections),
      aiActivityEventCount: job.aiActivityEvents.length,
      usageIncluded: Boolean(job.result.usage)
    },
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
  const inferredChecks = job.result.compliance.verificationChecks.filter(
    (check) => check.origin === 'INFERRED_CRITICAL'
  );
  if (inferredChecks.length > 5) {
    throw new Error('Change Verification obsługuje maksymalnie 5 krytycznych sugestii AI.');
  }
  const sectionIds = new Set(job.report.sections.map((section) => normalizeString(section.id).toUpperCase()));
  if (job.checkStoryCompliance && (
    !sectionIds.has('STORY_COMPLIANCE')
    || !sectionIds.has('INFERRED_CRITICAL_CHECKS')
  )) {
    throw new Error('Raport Change Verification nie zawiera kompletu sekcji aktualnego kontraktu.');
  }
  if (job.checkInstructionCompliance && !sectionIds.has('INSTRUCTION_COMPLIANCE')) {
    throw new Error('Raport Change Verification nie zawiera sekcji Instruction Compliance.');
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
  return {
    status: normalizeString(resultObject?.['status']),
    issueKey: normalizeString(resultObject?.['issueKey']),
    issueUrl: normalizeString(resultObject?.['issueUrl']),
    prompt: normalizeString(resultObject?.['prompt']),
    compliance: normalizeCompliance(resultObject?.['compliance']),
    usage: normalizeUsage(resultObject?.['usage'])
  };
}

function normalizeCompliance(compliance: unknown): ChangeVerificationCompliance {
  const complianceObject = asObject(compliance);
  return {
    storyComplianceRequested: normalizeBoolean(complianceObject?.['storyComplianceRequested']),
    instructionComplianceRequested: normalizeBoolean(complianceObject?.['instructionComplianceRequested']),
    status: normalizeString(complianceObject?.['status']),
    verificationChecks: Array.isArray(complianceObject?.['verificationChecks'])
      ? complianceObject['verificationChecks'].map(normalizeVerificationCheck)
      : [],
    findings: Array.isArray(complianceObject?.['findings'])
      ? complianceObject['findings'].map(normalizeFinding)
      : [],
    suggestedActions: normalizeStringArray(complianceObject?.['suggestedActions']),
    visibilityLimits: normalizeStringArray(complianceObject?.['visibilityLimits'])
  };
}

function normalizeVerificationCheck(check: unknown) {
  const checkObject = asObject(check);
  if (!checkObject) {
    throw new Error('Check Change Verification nie jest poprawnym obiektem aktualnego kontraktu.');
  }
  const origin = normalizeString(checkObject['origin']).toUpperCase();
  const scope = normalizeString(checkObject['scope']).toUpperCase();
  if (!['DEFINED', 'INFERRED_CRITICAL'].includes(origin)) {
    throw new Error('Check Change Verification nie zawiera obsługiwanego pola origin.');
  }
  if (origin === 'DEFINED' && !['STORY_COMPLIANCE', 'INSTRUCTION_COMPLIANCE'].includes(scope)) {
    throw new Error('Zdefiniowany check Change Verification ma nieobsługiwany scope.');
  }
  if (origin === 'INFERRED_CRITICAL' && scope !== 'INFERRED_CRITICAL_CHECKS') {
    throw new Error('Krytyczna sugestia AI ma nieobsługiwany scope.');
  }

  const normalized = {
    id: normalizeString(checkObject?.['id']),
    origin,
    scope,
    criterionSource: normalizeString(checkObject?.['criterionSource']),
    criterionQuote: normalizeString(checkObject?.['criterionQuote']),
    interpretationType: normalizeString(checkObject?.['interpretationType']),
    criticality: normalizeNullableString(checkObject?.['criticality']),
    inferenceRationale: normalizeNullableString(checkObject?.['inferenceRationale']),
    inferenceSignals: normalizeStringArray(checkObject?.['inferenceSignals']),
    riskIfOmitted: normalizeNullableString(checkObject?.['riskIfOmitted']),
    confidence: normalizeNullableString(checkObject?.['confidence']),
    expectedCriterion: normalizeString(checkObject?.['expectedCriterion']),
    verificationStatus: normalizeString(checkObject?.['verificationStatus']),
    verifiedAgainst: normalizeString(checkObject?.['verifiedAgainst']),
    analysis: normalizeString(checkObject?.['analysis']),
    evidenceRefs: normalizeStringArray(checkObject?.['evidenceRefs']),
    gaps: normalizeStringArray(checkObject?.['gaps']),
    suggestedAction: normalizeString(checkObject?.['suggestedAction'])
  };
  if (!normalized.id || !normalized.verificationStatus) {
    throw new Error('Check Change Verification nie zawiera wymaganych pól aktualnego kontraktu.');
  }
  if (origin === 'INFERRED_CRITICAL' && (
    !normalized.criticality
    || !normalized.inferenceRationale
    || normalized.inferenceSignals.length === 0
    || !normalized.riskIfOmitted
    || !normalized.confidence
  )) {
    throw new Error('Krytyczna sugestia AI nie zawiera pełnych metadanych aktualnego kontraktu.');
  }
  return normalized;
}

function normalizeFinding(finding: unknown): ChangeVerificationFinding {
  const findingObject = asObject(finding);
  return {
    id: normalizeString(findingObject?.['id']),
    severity: normalizeSeverity(findingObject?.['severity']),
    source: normalizeString(findingObject?.['source']),
    summary: normalizeString(findingObject?.['summary']),
    details: normalizeString(findingObject?.['details']),
    references: normalizeStringArray(findingObject?.['references']),
    suggestedAction: normalizeString(findingObject?.['suggestedAction'])
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

function uniqueValues(values: string[]): string[] {
  return Array.from(new Set(values.filter((value) => Boolean(value?.trim()))));
}

function normalizeSeverity(value: unknown): ChangeVerificationFindingSeverity {
  const normalized = normalizeString(value).toUpperCase();
  if (['INFO', 'LOW', 'MEDIUM', 'HIGH', 'BLOCKER'].includes(normalized)) {
    return normalized as ChangeVerificationFindingSeverity;
  }
  return 'INFO';
}

function normalizeString(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function normalizeNullableString(value: unknown): string | null {
  return typeof value === 'string' ? value : null;
}

function normalizeStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
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
