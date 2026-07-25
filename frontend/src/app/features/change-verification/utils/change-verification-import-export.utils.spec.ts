import { ChangeVerificationJobStateSnapshot } from '../models/change-verification.models';
import {
  buildChangeVerificationExportEnvelope,
  buildChangeVerificationExportFileName,
  CHANGE_VERIFICATION_RESULT_CONTRACT,
  parseImportedChangeVerificationResult
} from './change-verification-import-export.utils';

describe('change-verification-import-export utils', () => {
  it('should build and parse a completed Change Verification export envelope', () => {
    const exportedAt = '2026-07-26T10:00:00Z';
    const envelope = buildChangeVerificationExportEnvelope(changeVerificationJob(), exportedAt);

    const imported = parseImportedChangeVerificationResult(envelope);

    expect(envelope.schema).toBe('tdw.change-verification-export');
    expect(envelope.version).toBe(1);
    expect(envelope.payload.type).toBe('change-verification-analysis');
    expect(envelope.payload.resultContract).toBe(CHANGE_VERIFICATION_RESULT_CONTRACT);
    expect(envelope.payload.diagnostics.resultContract).toBe(CHANGE_VERIFICATION_RESULT_CONTRACT);
    expect(envelope.payload.diagnostics.target.issueKey).toBe('CRM-123');
    expect(envelope.payload.diagnostics.result.findingCount).toBe(1);
    expect(envelope.payload.diagnostics.result.smokeTestCount).toBe(1);
    expect(envelope.payload.diagnostics.result.readySmokeTestCount).toBe(1);
    expect(envelope.payload.diagnostics.workflow.contextEvidenceItemCount).toBe(1);
    expect(envelope.payload.diagnostics.workflow.toolEvidenceItemCount).toBe(1);
    expect(imported.exportedAt).toBe(exportedAt);
    expect(imported.job.jobId).toBe('change-job-1');
    expect(imported.job.result?.compliance.findings[0]?.summary).toBe('Story alignment confirmed');
  });

  it('should reject non Change Verification payloads', () => {
    expect(() => parseImportedChangeVerificationResult({ schema: 'tdw.flow-explorer-export' })).toThrow(
      'Wybierz plik wyeksportowany z Change Verification.'
    );
  });

  it('should reject non-completed jobs', () => {
    expect(() =>
      buildChangeVerificationExportEnvelope(
        changeVerificationJob({ status: 'ANALYZING', result: null }),
        '2026-07-26T10:00:00Z'
      )
    ).toThrow('Import i eksport wspiera tylko zakończone Change Verification runy COMPLETED.');
  });

  it('should build a stable export file name', () => {
    expect(
      buildChangeVerificationExportFileName(changeVerificationJob(), '2026-07-26T10:00:00Z')
    ).toBe('change-verification-CRM-123-completed-20260726-120000.json');
  });
});

function changeVerificationJob(
  overrides: Partial<ChangeVerificationJobStateSnapshot> = {}
): ChangeVerificationJobStateSnapshot {
  return {
    jobId: 'change-job-1',
    issueKey: 'CRM-123',
    issueUrl: 'https://jira.example.com/browse/CRM-123',
    modes: ['CHECK_COMPLIANCE', 'GENERATE_SMOKE_PACK'],
    checkStoryCompliance: true,
    checkInstructionCompliance: true,
    aiModel: 'gpt-test',
    reasoningEffort: 'medium',
    status: 'COMPLETED',
    currentStepCode: 'AI_ANALYSIS',
    currentStepLabel: 'AI analysis',
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-07-26T09:00:00Z',
    updatedAt: '2026-07-26T09:05:00Z',
    completedAt: '2026-07-26T09:05:00Z',
    steps: [
      {
        code: 'AI_ANALYSIS',
        label: 'AI analysis',
        phase: 'AI',
        status: 'COMPLETED',
        message: 'Done',
        itemCount: 1,
        startedAt: '2026-07-26T09:01:00Z',
        completedAt: '2026-07-26T09:05:00Z',
        consumesEvidence: [],
        producesEvidence: [],
        usage: null
      }
    ],
    contextSections: [
      {
        provider: 'jira',
        category: 'issue',
        items: [{ title: 'CRM-123', attributes: [{ name: 'status', value: 'Ready' }] }]
      }
    ],
    toolEvidenceSections: [
      {
        provider: 'gitlab',
        category: 'merge-requests',
        items: [{ title: 'MR !1', attributes: [{ name: 'repo', value: 'customer-api' }] }]
      }
    ],
    aiActivityEvents: [
      {
        eventId: 'event-1',
        parentEventId: '',
        type: 'TOOL_CALL',
        category: 'AI',
        status: 'COMPLETED',
        title: 'Read source',
        summary: 'Source inspected',
        turnId: 'turn-1',
        interactionId: 'interaction-1',
        toolCallId: 'tool-1',
        toolName: 'gitlab_search',
        timestamp: '2026-07-26T09:02:00Z',
        details: {}
      }
    ],
    preparedPrompt: 'Prompt',
    result: {
      status: 'READY',
      issueKey: 'CRM-123',
      issueUrl: 'https://jira.example.com/browse/CRM-123',
      modes: ['CHECK_COMPLIANCE', 'GENERATE_SMOKE_PACK'],
      prompt: 'Prompt',
      compliance: {
        storyComplianceRequested: true,
        instructionComplianceRequested: true,
        status: 'READY',
        findings: [
          {
            id: 'finding-1',
            severity: 'INFO',
            source: 'story',
            summary: 'Story alignment confirmed',
            details: 'The implementation follows acceptance criteria.',
            references: ['CRM-123'],
            suggestedAction: 'Proceed with release verification.'
          }
        ],
        suggestedActions: ['Proceed with release verification.'],
        visibilityLimits: ['No runtime logs were checked.']
      },
      smokePack: {
        requested: true,
        status: 'READY',
        postmanCollectionName: 'CRM-123 smoke',
        tests: [
          {
            id: 'smoke-1',
            name: 'Create customer',
            method: 'POST',
            path: '/api/customers',
            purpose: 'Verify the changed endpoint.',
            headers: [],
            queryParams: [],
            requestBody: '{}',
            responseAssertions: [{ type: 'STATUS', target: 'status', operator: 'EQ', expectedValue: '201' }],
            dbAssertions: [],
            dbAssertionSpecs: [],
            cleanup: null,
            cleanupHints: [],
            sourceRefs: ['CustomerController'],
            riskCovered: 'Creation path',
            reviewStatus: 'READY'
          }
        ],
        visibilityLimits: [],
        suggestedActions: [],
        confidence: 'MEDIUM'
      },
      execution: {
        requested: false,
        status: 'NOT_RUN',
        executedTestIds: [],
        testResults: [],
        cleanupActions: [],
        manualCleanupSql: null,
        visibilityLimits: []
      },
      usage: null
    },
    ...overrides
  };
}
