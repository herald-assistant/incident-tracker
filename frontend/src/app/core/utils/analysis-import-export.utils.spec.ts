import { AnalysisJobStateSnapshot } from '../models/analysis.models';
import { buildExportEnvelope, parseImportedAnalysis } from './analysis-import-export.utils';

describe('analysis import/export utils', () => {
  it('should export analysis snapshots without GitHub tokens', () => {
    const envelope = buildExportEnvelope(completedJob(), '2026-05-02T10:05:00Z');

    const serialized = JSON.stringify(envelope);

    expect(serialized).not.toContain('ghu_secret_token');
    expect(serialized).not.toContain('ghr_secret_refresh');
    expect(serialized).not.toContain('githubAuthCode');
    expect(serialized).not.toContain('client_secret');
  });

  it('should import older job JSON without aiAccount field', () => {
    const olderJob = completedJob() as unknown as Record<string, unknown>;
    delete olderJob['aiAccount'];

    const imported = parseImportedAnalysis(olderJob);

    expect(imported.job.analysisId).toBe('analysis-1');
    expect(imported.job.status).toBe('COMPLETED');
    expect(imported.job.result?.detectedProblem).toBe('DOWNSTREAM_TIMEOUT');
  });
});

function completedJob(): AnalysisJobStateSnapshot {
  return {
    analysisId: 'analysis-1',
    correlationId: 'corr-123',
    aiModel: 'gpt-5.4',
    reasoningEffort: 'medium',
    status: 'COMPLETED',
    currentStepCode: 'AI_ANALYSIS',
    currentStepLabel: 'Budowanie końcowej analizy AI',
    environment: 'dev3',
    gitLabBranch: 'main',
    errorCode: '',
    errorMessage: '',
    createdAt: '2026-05-02T10:00:00Z',
    updatedAt: '2026-05-02T10:05:00Z',
    completedAt: '2026-05-02T10:05:00Z',
    steps: [],
    evidenceSections: [],
    toolEvidenceSections: [],
    aiActivityEvents: [],
    chatMessages: [],
    preparedPrompt: 'Prepared prompt without GitHub tokens.',
    result: {
      status: 'COMPLETED',
      correlationId: 'corr-123',
      environment: 'dev3',
      gitLabBranch: 'main',
      summary: 'Diagnoza testowa.',
      detectedProblem: 'DOWNSTREAM_TIMEOUT',
      recommendedAction: 'Sprawdź timeout.',
      rationale: 'Evidence wskazuje na timeout.',
      affectedFunction: 'Catalog client',
      affectedProcess: 'Billing',
      affectedBoundedContext: 'Billing Context',
      affectedTeam: 'Billing Team',
      prompt: 'Final prompt without GitHub tokens.',
      usage: null
    }
  };
}
