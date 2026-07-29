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
    expect(envelope.version).toBe(3);
    expect(envelope.payload.type).toBe('change-verification-analysis');
    expect(envelope.payload.resultContract).toBe(CHANGE_VERIFICATION_RESULT_CONTRACT);
    expect(envelope.payload.diagnostics.resultContract).toBe(CHANGE_VERIFICATION_RESULT_CONTRACT);
    expect(envelope.payload.diagnostics.target.issueKey).toBe('CRM-123');
    expect(envelope.payload.diagnostics.result.findingCount).toBe(1);
    expect(envelope.payload.diagnostics.workflow.contextEvidenceItemCount).toBe(1);
    expect(envelope.payload.diagnostics.workflow.toolEvidenceItemCount).toBe(1);
    expect(imported.exportedAt).toBe(exportedAt);
    expect(imported.job.jobId).toBe('change-job-1');
    expect(imported.job.result?.compliance.findings[0]?.summary).toBe('Story alignment confirmed');
    expect(imported.job.report?.header).toBe('Change Verification: CRM-123');
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

  it('should reject completed jobs without a canonical report', () => {
    expect(() =>
      buildChangeVerificationExportEnvelope(
        changeVerificationJob({ report: null }),
        '2026-07-26T10:00:00Z'
      )
    ).toThrow('Change Verification export wymaga kanonicznego raportu analizy.');
  });

  it('should parse a running local history envelope when completed result is not required', () => {
    const envelope = {
      schema: 'tdw.change-verification-export',
      version: 3,
      exportedAt: '2026-07-26T09:02:00Z',
      payload: {
        type: 'change-verification-analysis',
        resultContract: CHANGE_VERIFICATION_RESULT_CONTRACT,
        diagnostics: {
          resultContract: CHANGE_VERIFICATION_RESULT_CONTRACT
        },
        job: changeVerificationJob({
          status: 'ANALYZING',
          completedAt: null,
          result: null,
          report: null
        })
      }
    };

    const imported = parseImportedChangeVerificationResult(envelope, { requireCompleted: false });

    expect(imported.job.status).toBe('ANALYZING');
    expect(imported.job.result).toBeNull();
    expect(imported.job.report).toBeNull();
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
      prompt: 'Prompt',
      compliance: {
        storyComplianceRequested: true,
        instructionComplianceRequested: true,
        status: 'READY',
        verificationChecks: [
          {
            id: 'story-001',
            scope: 'STORY_COMPLIANCE',
            criterionSource: 'acceptance criteria',
            criterionQuote: 'Customer can be created.',
            interpretationType: 'explicit',
            expectedCriterion: 'Customer creation endpoint persists the requested customer.',
            verificationStatus: 'PASSED',
            verifiedAgainst: 'CustomerController',
            analysis: 'The endpoint implementation covers the creation path.',
            evidenceRefs: ['CRM-123', 'CustomerController'],
            gaps: [],
            suggestedAction: 'Proceed with release verification.'
          }
        ],
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
      usage: null
    },
    report: {
      reportId: 'change-verification-CRM-123',
      header: 'Change Verification: CRM-123',
      subHeader: 'Compliance READY',
      markdownSummary: '- Compliance: `READY` with 1 finding.',
      sections: [
        {
          id: 'STORY_COMPLIANCE',
          title: 'Story compliance',
          order: 0,
          markdown: 'Story alignment confirmed.',
          meta: {
            references: [{ type: 'jira', label: 'CRM-123', target: 'CRM-123', description: 'Target issue.' }],
            visibilityLimits: ['No runtime logs were checked.'],
            openQuestions: [],
            gaps: [],
            confidence: 'MEDIUM',
            warnings: []
          }
        },
        {
          id: 'INSTRUCTION_COMPLIANCE',
          title: 'Instruction compliance',
          order: 1,
          markdown: 'Instruction alignment confirmed.',
          meta: {
            references: [],
            visibilityLimits: [],
            openQuestions: [],
            gaps: [],
            confidence: 'MEDIUM',
            warnings: []
          }
        }
      ],
      meta: {
        references: [{ type: 'jira', label: 'CRM-123', target: 'https://jira.example.com/browse/CRM-123', description: 'Target issue.' }],
        visibilityLimits: ['No runtime logs were checked.'],
        openQuestions: [],
        gaps: [],
        confidence: 'MEDIUM',
        warnings: []
      }
    },
    ...overrides
  };
}
