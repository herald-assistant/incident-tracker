import {
  RuntimeConfigurationVerificationJobStateSnapshot,
  RuntimeConfigurationVerificationResult
} from '../models/runtime-configuration-verification.models';
import {
  buildRuntimeConfigurationExportEnvelope,
  buildRuntimeConfigurationExportFileName
} from './runtime-configuration-import-export.utils';

describe('runtime configuration import/export utils', () => {
  it('should build the versioned portable envelope and safe file name', () => {
    const job = portableJob();
    const envelope = buildRuntimeConfigurationExportEnvelope(job, '2026-07-30T10:00:00Z');

    expect(envelope.schema).toBe('tdw.runtime-configuration-verification-export');
    expect(envelope.version).toBe(1);
    expect(envelope.payload.resultContract).toBe(
      'runtime-configuration-verification-result-v1'
    );
    expect(envelope.payload.job.result?.deterministicResult.differences[0]?.path)
      .toBe('notifications.endpoint');
    expect(buildRuntimeConfigurationExportFileName(job))
      .toBe('runtime-configuration-backend-system-dev1-to-zt001.json');
  });

  it('should reject an unfinished job', () => {
    expect(() => buildRuntimeConfigurationExportEnvelope(
      { ...portableJob(), status: 'RUNNING', result: null },
      '2026-07-30T10:00:00Z'
    )).toThrow('Eksport wymaga zakończonego wyniku');
  });
});

function portableJob(): RuntimeConfigurationVerificationJobStateSnapshot {
  return {
    jobId: 'job-1',
    mode: 'BASIC',
    repositoryId: 'runtime-config',
    systemId: 'backend/system',
    sourceBranch: 'dev1',
    targetBranch: 'zt001',
    codeRef: null,
    aiModel: 'gpt-5.4',
    reasoningEffort: 'medium',
    status: 'COMPLETED',
    currentStepCode: null,
    currentStepLabel: null,
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-07-30T09:59:00Z',
    updatedAt: '2026-07-30T10:00:00Z',
    completedAt: '2026-07-30T10:00:00Z',
    steps: [],
    contextSections: [],
    toolEvidenceSections: [],
    aiActivityEvents: [],
    preparedPrompt: 'safe prompt',
    result: portableResult(),
    report: null,
    imported: false
  };
}

function portableResult(): RuntimeConfigurationVerificationResult {
  return {
    status: 'REVIEW_REQUIRED',
    mode: 'BASIC',
    deterministicResult: {
      repositoryId: 'runtime-config',
      systemId: 'backend/system',
      systemLabel: 'Backend',
      configurationDirectory: 'backend',
      sourceBranch: 'dev1',
      targetBranch: 'zt001',
      status: 'REVIEW_REQUIRED',
      sourceCoverage: null,
      targetCoverage: null,
      documents: [],
      references: [],
      differences: [{
        differenceId: 'difference-1',
        role: 'APPLICATION_YAML',
        documentIndex: 0,
        path: 'notifications.endpoint',
        kind: 'CHANGED',
        sourceType: 'STRING',
        targetType: 'STRING',
        sensitivity: 'PUBLIC',
        sourceValueToken: 'value-1',
        targetValueToken: 'value-2'
      }],
      findings: []
    },
    aiSecondOpinion: null,
    agreement: null,
    deepAnalysis: null,
    visibilityLimits: [],
    prompt: 'safe prompt',
    usage: null
  };
}
