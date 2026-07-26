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
  ChangeVerificationExecution,
  ChangeVerificationFinding,
  ChangeVerificationFindingSeverity,
  ChangeVerificationJobMode,
  ChangeVerificationJobStateSnapshot,
  ChangeVerificationNameValue,
  ChangeVerificationResult,
  ChangeVerificationSmokeAssertion,
  ChangeVerificationSmokeAssertionResult,
  ChangeVerificationSmokeCleanup,
  ChangeVerificationSmokeCleanupResult,
  ChangeVerificationSmokeDbAssertion,
  ChangeVerificationSmokeHttpResult,
  ChangeVerificationSmokePack,
  ChangeVerificationSmokeTest,
  ChangeVerificationSmokeTestExecution
} from '../models/change-verification.models';

export const CHANGE_VERIFICATION_EXPORT_SCHEMA = 'tdw.change-verification-export';
export const CHANGE_VERIFICATION_EXPORT_VERSION = 2;
export const CHANGE_VERIFICATION_EXPORT_PAYLOAD_TYPE = 'change-verification-analysis';
export const CHANGE_VERIFICATION_RESULT_CONTRACT = 'change-verification-result-v2';

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
    modes: ChangeVerificationJobMode[];
    checkStoryCompliance: boolean;
    checkInstructionCompliance: boolean;
    aiModel: string;
    reasoningEffort: string;
  };
  result: {
    status: string;
    complianceStatus: string;
    findingCount: number;
    smokeTestCount: number;
    readySmokeTestCount: number;
    executionStatus: string;
    executionResultCount: number;
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
  const smokePack = job.result.smokePack;
  const execution = job.result.execution;

  return {
    resultContract: CHANGE_VERIFICATION_RESULT_CONTRACT,
    target: {
      issueKey: job.issueKey,
      issueUrl: job.issueUrl
    },
    request: {
      modes: job.modes,
      checkStoryCompliance: job.checkStoryCompliance,
      checkInstructionCompliance: job.checkInstructionCompliance,
      aiModel: job.aiModel,
      reasoningEffort: job.reasoningEffort
    },
    result: {
      status: job.result.status,
      complianceStatus: compliance.status,
      findingCount: compliance.findings.length,
      smokeTestCount: smokePack.tests.length,
      readySmokeTestCount: smokePack.tests.filter((test) => normalizeString(test.reviewStatus) === 'READY').length,
      executionStatus: execution.status,
      executionResultCount: execution.testResults.length,
      visibilityLimitCount: uniqueValues([
        ...compliance.visibilityLimits,
        ...smokePack.visibilityLimits,
        ...execution.visibilityLimits
      ]).length
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
    modes: normalizeModes(jobObject['modes']),
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
    modes: normalizeModes(resultObject?.['modes']),
    prompt: normalizeString(resultObject?.['prompt']),
    compliance: normalizeCompliance(resultObject?.['compliance']),
    smokePack: normalizeSmokePack(resultObject?.['smokePack']),
    execution: normalizeExecution(resultObject?.['execution']),
    usage: normalizeUsage(resultObject?.['usage'])
  };
}

function normalizeCompliance(compliance: unknown): ChangeVerificationCompliance {
  const complianceObject = asObject(compliance);
  return {
    storyComplianceRequested: normalizeBoolean(complianceObject?.['storyComplianceRequested']),
    instructionComplianceRequested: normalizeBoolean(complianceObject?.['instructionComplianceRequested']),
    status: normalizeString(complianceObject?.['status']),
    findings: Array.isArray(complianceObject?.['findings'])
      ? complianceObject['findings'].map(normalizeFinding)
      : [],
    suggestedActions: normalizeStringArray(complianceObject?.['suggestedActions']),
    visibilityLimits: normalizeStringArray(complianceObject?.['visibilityLimits'])
  };
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

function normalizeSmokePack(smokePack: unknown): ChangeVerificationSmokePack {
  const smokePackObject = asObject(smokePack);
  return {
    requested: normalizeBoolean(smokePackObject?.['requested']),
    status: normalizeString(smokePackObject?.['status']),
    postmanCollectionName: normalizeNullableString(smokePackObject?.['postmanCollectionName']),
    tests: Array.isArray(smokePackObject?.['tests'])
      ? smokePackObject['tests'].map(normalizeSmokeTest)
      : [],
    visibilityLimits: normalizeStringArray(smokePackObject?.['visibilityLimits']),
    suggestedActions: normalizeStringArray(smokePackObject?.['suggestedActions']),
    confidence: normalizeNullableString(smokePackObject?.['confidence'])
  };
}

function normalizeSmokeTest(test: unknown): ChangeVerificationSmokeTest {
  const testObject = asObject(test);
  return {
    id: normalizeString(testObject?.['id']),
    name: normalizeString(testObject?.['name']),
    method: normalizeString(testObject?.['method']),
    path: normalizeString(testObject?.['path']),
    purpose: normalizeString(testObject?.['purpose']),
    headers: Array.isArray(testObject?.['headers']) ? testObject['headers'].map(normalizeNameValue) : [],
    queryParams: Array.isArray(testObject?.['queryParams'])
      ? testObject['queryParams'].map(normalizeNameValue)
      : [],
    requestBody: normalizeNullableString(testObject?.['requestBody']),
    responseAssertions: Array.isArray(testObject?.['responseAssertions'])
      ? testObject['responseAssertions'].map(normalizeAssertion)
      : [],
    dbAssertions: normalizeStringArray(testObject?.['dbAssertions']),
    dbAssertionSpecs: Array.isArray(testObject?.['dbAssertionSpecs'])
      ? testObject['dbAssertionSpecs'].map(normalizeDbAssertion)
      : [],
    cleanup: asObject(testObject?.['cleanup']) ? normalizeCleanup(testObject?.['cleanup']) : null,
    cleanupHints: normalizeStringArray(testObject?.['cleanupHints']),
    sourceRefs: normalizeStringArray(testObject?.['sourceRefs']),
    riskCovered: normalizeNullableString(testObject?.['riskCovered']),
    reviewStatus: normalizeNullableString(testObject?.['reviewStatus'])
  };
}

function normalizeNameValue(item: unknown): ChangeVerificationNameValue {
  const itemObject = asObject(item);
  return {
    name: normalizeString(itemObject?.['name']),
    value: normalizeString(itemObject?.['value']),
    enabled: normalizeBoolean(itemObject?.['enabled'])
  };
}

function normalizeAssertion(assertion: unknown): ChangeVerificationSmokeAssertion {
  const assertionObject = asObject(assertion);
  return {
    type: normalizeString(assertionObject?.['type']),
    target: normalizeString(assertionObject?.['target']),
    operator: normalizeString(assertionObject?.['operator']),
    expectedValue: normalizeString(assertionObject?.['expectedValue'])
  };
}

function normalizeDbAssertion(assertion: unknown): ChangeVerificationSmokeDbAssertion {
  const assertionObject = asObject(assertion);
  return {
    id: normalizeString(assertionObject?.['id']),
    sql: normalizeString(assertionObject?.['sql']),
    operator: normalizeNullableString(assertionObject?.['operator']),
    expectedValue: normalizeNullableString(assertionObject?.['expectedValue']),
    description: normalizeNullableString(assertionObject?.['description'])
  };
}

function normalizeCleanup(cleanup: unknown): ChangeVerificationSmokeCleanup {
  const cleanupObject = asObject(cleanup);
  return {
    strategy: normalizeString(cleanupObject?.['strategy']),
    method: normalizeNullableString(cleanupObject?.['method']),
    path: normalizeNullableString(cleanupObject?.['path']),
    requestBody: normalizeNullableString(cleanupObject?.['requestBody']),
    manualSql: normalizeNullableString(cleanupObject?.['manualSql']),
    hints: normalizeStringArray(cleanupObject?.['hints'])
  };
}

function normalizeExecution(execution: unknown): ChangeVerificationExecution {
  const executionObject = asObject(execution);
  return {
    requested: normalizeBoolean(executionObject?.['requested']),
    status: normalizeString(executionObject?.['status']),
    executedTestIds: normalizeStringArray(executionObject?.['executedTestIds']),
    testResults: Array.isArray(executionObject?.['testResults'])
      ? executionObject['testResults'].map(normalizeTestExecution)
      : [],
    cleanupActions: normalizeStringArray(executionObject?.['cleanupActions']),
    manualCleanupSql: normalizeNullableString(executionObject?.['manualCleanupSql']),
    visibilityLimits: normalizeStringArray(executionObject?.['visibilityLimits'])
  };
}

function normalizeTestExecution(testExecution: unknown): ChangeVerificationSmokeTestExecution {
  const testExecutionObject = asObject(testExecution);
  return {
    testId: normalizeString(testExecutionObject?.['testId']),
    name: normalizeString(testExecutionObject?.['name']),
    status: normalizeString(testExecutionObject?.['status']),
    http: asObject(testExecutionObject?.['http']) ? normalizeHttpResult(testExecutionObject?.['http']) : null,
    responseAssertions: Array.isArray(testExecutionObject?.['responseAssertions'])
      ? testExecutionObject['responseAssertions'].map(normalizeAssertionResult)
      : [],
    dbAssertions: Array.isArray(testExecutionObject?.['dbAssertions'])
      ? testExecutionObject['dbAssertions'].map(normalizeAssertionResult)
      : [],
    cleanup: asObject(testExecutionObject?.['cleanup'])
      ? normalizeCleanupResult(testExecutionObject?.['cleanup'])
      : null
  };
}

function normalizeHttpResult(http: unknown): ChangeVerificationSmokeHttpResult {
  const httpObject = asObject(http);
  return {
    method: normalizeString(httpObject?.['method']),
    url: normalizeString(httpObject?.['url']),
    statusCode: normalizeNullableNumber(httpObject?.['statusCode']),
    durationMillis: normalizeNumber(httpObject?.['durationMillis']),
    bodyExcerpt: normalizeNullableString(httpObject?.['bodyExcerpt']),
    headers: Array.isArray(httpObject?.['headers']) ? httpObject['headers'].map(normalizeNameValue) : [],
    errorMessage: normalizeNullableString(httpObject?.['errorMessage'])
  };
}

function normalizeAssertionResult(assertion: unknown): ChangeVerificationSmokeAssertionResult {
  const assertionObject = asObject(assertion);
  return {
    type: normalizeString(assertionObject?.['type']),
    target: normalizeString(assertionObject?.['target']),
    status: normalizeString(assertionObject?.['status']),
    message: normalizeString(assertionObject?.['message'])
  };
}

function normalizeCleanupResult(cleanup: unknown): ChangeVerificationSmokeCleanupResult {
  const cleanupObject = asObject(cleanup);
  return {
    strategy: normalizeString(cleanupObject?.['strategy']),
    status: normalizeString(cleanupObject?.['status']),
    action: normalizeNullableString(cleanupObject?.['action']),
    manualSql: normalizeNullableString(cleanupObject?.['manualSql']),
    message: normalizeString(cleanupObject?.['message'])
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

function normalizeModes(value: unknown): ChangeVerificationJobMode[] {
  const modes = normalizeStringArray(value).filter(isChangeVerificationMode);
  return modes.length > 0 ? Array.from(new Set(modes)) : ['CHECK_COMPLIANCE'];
}

function normalizeSeverity(value: unknown): ChangeVerificationFindingSeverity {
  const normalized = normalizeString(value).toUpperCase();
  if (['INFO', 'LOW', 'MEDIUM', 'HIGH', 'BLOCKER'].includes(normalized)) {
    return normalized as ChangeVerificationFindingSeverity;
  }
  return 'INFO';
}

function isChangeVerificationMode(value: string): value is ChangeVerificationJobMode {
  return value === 'CHECK_COMPLIANCE' || value === 'GENERATE_SMOKE_PACK' || value === 'EXECUTE_SMOKE_PACK';
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
