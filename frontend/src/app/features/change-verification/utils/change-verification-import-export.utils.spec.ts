import { ChangeVerificationJobStateSnapshot } from '../models/change-verification.models';
import {
  buildChangeVerificationExportEnvelope,
  buildChangeVerificationExportFileName,
  CHANGE_VERIFICATION_RESULT_CONTRACT,
  parseImportedChangeVerificationResult
} from './change-verification-import-export.utils';

describe('change-verification-import-export utils', () => {
  it('builds and parses only the v6 rule-ledger envelope', () => {
    const exportedAt = '2026-07-26T10:00:00Z';
    const envelope = buildChangeVerificationExportEnvelope(changeVerificationJob(), exportedAt);
    const imported = parseImportedChangeVerificationResult(envelope);

    expect(envelope.version).toBe(6);
    expect(envelope.payload.resultContract).toBe('change-verification-result-v5');
    expect(envelope.payload.diagnostics.result).toEqual({
      status: 'COMPLETED',
      decisionStatus: 'READY',
      totalRules: 1,
      needsAttentionCount: 0,
      visibilityLimitCount: 0
    });
    expect(imported.job.result?.ruleLedger.rules[0]?.source.quote)
      .toBe('Po zapisaniu klient jest widoczny na liście.');
  });

  it('rejects the previous export version without migration', () => {
    const envelope = buildChangeVerificationExportEnvelope(
      changeVerificationJob(),
      '2026-07-26T10:00:00Z'
    ) as unknown as { version: number };
    envelope.version = 5;

    expect(() => parseImportedChangeVerificationResult(envelope)).toThrow(
      'Ten plik eksportu Change Verification ma nieobsługiwaną wersję formatu.'
    );
  });

  it('rejects a decision tampered independently from rule outcomes', () => {
    const envelope = buildChangeVerificationExportEnvelope(changeVerificationJob(), '2026-07-26T10:00:00Z');
    (envelope.payload.job.result!.ruleLedger.decision as { status: string }).status = 'NEEDS_ACTION';

    expect(() => parseImportedChangeVerificationResult(envelope)).toThrow(
      'Decyzja w imporcie nie odpowiada outcome reguł źródłowych.'
    );
  });

  it('parses an unfinished local v6 snapshot when completion is not required', () => {
    const envelope = {
      schema: 'tdw.change-verification-export',
      version: 6,
      exportedAt: '2026-07-26T09:02:00Z',
      payload: {
        type: 'change-verification-analysis',
        resultContract: CHANGE_VERIFICATION_RESULT_CONTRACT,
        diagnostics: { resultContract: CHANGE_VERIFICATION_RESULT_CONTRACT },
        job: changeVerificationJob({ status: 'ANALYZING', completedAt: null, result: null, report: null })
      }
    };

    const imported = parseImportedChangeVerificationResult(envelope, { requireCompleted: false });

    expect(imported.job.status).toBe('ANALYZING');
    expect(imported.job.result).toBeNull();
  });

  it('builds a stable export file name', () => {
    expect(buildChangeVerificationExportFileName(changeVerificationJob(), '2026-07-26T10:00:00Z'))
      .toBe('change-verification-CRM-123-completed-20260726-120000.json');
  });
});

function changeVerificationJob(
  overrides: Partial<ChangeVerificationJobStateSnapshot> = {}
): ChangeVerificationJobStateSnapshot {
  return {
    jobId: 'change-job-1',
    issueKey: 'CRM-123',
    issueUrl: 'https://jira.example.com/browse/CRM-123',
    checkStoryCompliance: true,
    checkInstructionCompliance: true,
    aiModel: 'gpt-test',
    reasoningEffort: 'medium',
    status: 'COMPLETED',
    currentStepCode: null,
    currentStepLabel: null,
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-07-26T09:00:00Z',
    updatedAt: '2026-07-26T09:05:00Z',
    completedAt: '2026-07-26T09:05:00Z',
    steps: [],
    contextSections: [],
    toolEvidenceSections: [],
    aiActivityEvents: [],
    preparedPrompt: 'Prompt',
    result: {
      status: 'COMPLETED',
      issueKey: 'CRM-123',
      issueUrl: 'https://jira.example.com/browse/CRM-123',
      prompt: 'Prompt',
      ruleLedger: {
        storyComplianceRequested: true,
        instructionComplianceRequested: true,
        decision: { status: 'READY', totalRules: 1, satisfied: 1, notSatisfied: 0, notVerified: 0 },
        rules: [{
          id: 'story-001',
          scope: 'STORY',
          source: {
            type: 'ACCEPTANCE_CRITERION',
            label: 'CRM-123 AC',
            reference: 'CRM-123#ac-1',
            quote: 'Po zapisaniu klient jest widoczny na liście.'
          },
          normalizedRule: 'Zapisany klient pojawia się na liście.',
          interpretationType: 'EXPLICIT',
          outcome: 'SATISFIED',
          releaseImpact: 'NONE',
          conclusion: 'Test przepływu potwierdza regułę.',
          evidence: [{ summary: 'Test przechodzi.', reference: 'CustomerFlowTest' }],
          missingEvidence: [],
          action: null,
          rationale: null,
          riskIfOmitted: null,
          signals: [],
          confidence: null
        }],
        additionalChecks: [],
        visibilityLimits: []
      },
      usage: null
    },
    report: {
      reportId: 'change-verification-CRM-123',
      header: 'Change Verification: CRM-123',
      subHeader: 'Decision READY',
      markdownSummary: 'Source rules: 1; satisfied: 1.',
      sections: [{
        id: 'RULE_LEDGER',
        title: 'Source-defined rules',
        order: 0,
        markdown: 'Reguła spełniona.',
        meta: { references: [], visibilityLimits: [], openQuestions: [], gaps: [], confidence: '', warnings: [] }
      }],
      meta: { references: [], visibilityLimits: [], openQuestions: [], gaps: [], confidence: '', warnings: [] }
    },
    ...overrides
  };
}
