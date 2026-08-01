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
    expect(envelope.payload.job.components[0]?.result?.deterministicResult.differences[0]?.path)
      .toBe('notifications.endpoint');
    expect(
      envelope.payload.job.components[0]?.result?.configurationDiff?.files[0]
        ?.documents[0]?.root.children[0]?.source.value
    ).toBe('https://notifications.dev.test');
    expect(envelope.payload.job.components[0]?.result?.configurationDiffAnnotations?.[0]?.comment)
      .toBe('Endpoint kieruje ruch do innego systemu.');
    expect(buildRuntimeConfigurationExportFileName(job))
      .toBe('runtime-configuration-1-components-dev1-to-zt001.json');
  });

  it('should reject an unfinished job', () => {
    expect(() => buildRuntimeConfigurationExportEnvelope(
      {
        ...portableJob(),
        status: 'RUNNING',
        components: [{ ...portableJob().components[0]!, status: 'RUNNING', result: null }]
      },
      '2026-07-30T10:00:00Z'
    )).toThrow('Eksport wymaga zakończonego wyniku');
  });
});

function portableJob(): RuntimeConfigurationVerificationJobStateSnapshot {
  return {
    jobId: 'job-1',
    mode: 'DEEP',
    repositoryId: 'runtime-config',
    systemIds: ['backend/system'],
    sourceBranch: 'dev1',
    targetBranch: 'zt001',
    codeRef: null,
    aiModel: null,
    reasoningEffort: null,
    status: 'COMPLETED',
    currentStepCode: null,
    currentStepLabel: null,
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-07-30T09:59:00Z',
    updatedAt: '2026-07-30T10:00:00Z',
    completedAt: '2026-07-30T10:00:00Z',
    steps: [],
    components: [{
      componentRunId: 'job-1:0',
      systemId: 'backend/system',
      systemLabel: 'Backend',
      configurationDirectory: 'backend',
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
      preparedPrompt: null,
      result: portableResult(),
      report: null
    }],
    imported: false
  };
}

function portableResult(): RuntimeConfigurationVerificationResult {
  return {
    status: 'REVIEW_REQUIRED',
    mode: 'DEEP',
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
    configurationDiff: {
      sourceBranch: 'dev1',
      targetBranch: 'zt001',
      files: [{
        role: 'APPLICATION_YAML',
        format: 'YAML',
        sourcePath: 'backend/application.yml.kv',
        targetPath: 'backend/application.yml.kv',
        sourcePresent: true,
        targetPresent: true,
        documents: [{
          documentIndex: 0,
          sourcePresent: true,
          targetPresent: true,
          sourceProfile: {
            presence: 'ABSENT',
            type: null,
            value: null,
            cardinality: null
          },
          targetProfile: {
            presence: 'ABSENT',
            type: null,
            value: null,
            cardinality: null
          },
          root: {
            name: 'document-0',
            path: '',
            changeKind: 'UNCHANGED',
            source: {
              presence: 'PRESENT',
              type: 'MAP',
              value: null,
              cardinality: 1
            },
            target: {
              presence: 'PRESENT',
              type: 'MAP',
              value: null,
              cardinality: 1
            },
            differenceIds: [],
            children: [{
              name: 'endpoint',
              path: 'notifications.endpoint',
              changeKind: 'CHANGED',
              source: {
                presence: 'PRESENT',
                type: 'STRING',
                value: 'https://notifications.dev.test',
                cardinality: null
              },
              target: {
                presence: 'PRESENT',
                type: 'STRING',
                value: 'https://notifications.zt.test',
                cardinality: null
              },
              differenceIds: ['difference-1'],
              children: []
            }]
          }
        }]
      }]
    },
    configurationDiffAnnotations: [{
      sourceId: 'observation-1',
      kind: 'OBSERVATION',
      comment: 'Endpoint kieruje ruch do innego systemu.',
      confidence: null,
      hypothesis: false,
      differenceIds: ['difference-1'],
      findingIds: []
    }],
    aiSecondOpinion: null,
    agreement: null,
    deepAnalysis: null,
    visibilityLimits: [],
    prompt: null,
    usage: null
  };
}
