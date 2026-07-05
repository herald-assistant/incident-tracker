import { AnalysisJobStateSnapshot } from '../models/analysis.models';
import { buildExportEnvelope, parseImportedAnalysis } from './analysis-import-export.utils';

describe('analysis import/export utils', () => {
  it('should export analysis snapshots without GitHub tokens', () => {
    const envelope = buildExportEnvelope(completedJob(), '2026-05-02T10:05:00Z');

    const serialized = JSON.stringify(envelope);

    expect(envelope.schema).toBe('tdw.analysis-export');
    expect(serialized).not.toContain('ghu_secret_token');
    expect(serialized).not.toContain('ghr_secret_refresh');
    expect(serialized).not.toContain('githubAuthCode');
    expect(serialized).not.toContain('client_secret');
  });

  it('should import current tdw export envelopes', () => {
    const imported = parseImportedAnalysis(
      buildExportEnvelope(completedJob(), '2026-05-02T10:05:00Z')
    );

    expect(imported.exportedAt).toBe('2026-05-02T10:05:00Z');
    expect(imported.job.analysisId).toBe('analysis-1');
    expect(imported.job.status).toBe('COMPLETED');
    expect(imported.job.result?.detectedProblem).toBe('DOWNSTREAM_TIMEOUT');
    expect(imported.job.report?.reportId).toBe('incident-report-1');
    expect(imported.job.report?.sections[0]?.meta.references[0]?.target).toBe(
      'CustomerService.getCustomer'
    );
  });

  it('should reject legacy incident tracker export envelopes', () => {
    expect(() =>
      parseImportedAnalysis({
        schema: 'incident-tracker.analysis-export',
        version: 6,
        exportedAt: '2026-05-02T10:05:00Z',
        payload: {
          type: 'analysis-job',
          job: completedJob()
        }
      })
    ).toThrow('Nie rozpoznano formatu importu.');
  });

  it('should reject older tdw export envelope versions', () => {
    expect(() =>
      parseImportedAnalysis({
        ...buildExportEnvelope(completedJob(), '2026-05-02T10:05:00Z'),
        version: 5
      })
    ).toThrow('Ten plik eksportu ma nieobsługiwaną wersję formatu.');
  });

  it('should reject raw job snapshots without export envelope', () => {
    expect(() => parseImportedAnalysis(completedJob())).toThrow('Nie rozpoznano formatu importu.');
  });

  it('should allow non-terminal snapshots when parsing a local workspace run', () => {
    const queuedJob = {
      ...completedJob(),
      status: 'ANALYZING',
      completedAt: '',
      result: null,
      report: null
    };

    const imported = parseImportedAnalysis(
      buildExportEnvelope(queuedJob, '2026-05-02T10:02:00Z'),
      { requireTerminal: false }
    );

    expect(imported.job.status).toBe('ANALYZING');
    expect(imported.job.result).toBeNull();
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
    toolFeedback: [],
    chatMessages: [],
    preparedPrompt: 'Prepared prompt without GitHub tokens.',
    result: {
      status: 'COMPLETED',
      correlationId: 'corr-123',
      environment: 'dev3',
      gitLabBranch: 'main',
      detectedProblem: 'DOWNSTREAM_TIMEOUT',
      affectedProcess: 'CRM',
      affectedBoundedContext: 'CRM Customer Context',
      affectedTeam: 'CRM Team',
      functionalAnalysis: 'Analiza funkcjonalna procesu profilu klienta CRM.',
      technicalAnalysis: 'Analiza techniczna timeoutu w komponencie profilu klienta CRM.',
      confidence: 'medium',
      visibilityLimits: ['Brak potwierdzenia po stronie downstream.'],
      prompt: 'Final prompt without GitHub tokens.',
      usage: null
    },
    report: {
      reportId: 'incident-report-1',
      header: 'DOWNSTREAM_TIMEOUT',
      subHeader: 'CRM / CRM Customer Context',
      markdownSummary: 'Timeout downstream blokuje odczyt profilu klienta CRM.',
      sections: [
        {
          id: 'TECHNICAL_HANDOFF',
          title: 'Technical handoff',
          order: 1,
          markdown: 'CustomerService.getCustomer powinien sprawdzic timeout przy odczycie profilu CRM.',
          meta: {
            references: [
              {
                type: 'code',
                label: 'CustomerService.getCustomer',
                target: 'CustomerService.getCustomer',
                description: 'Miejsce wywolania downstream.'
              }
            ],
            visibilityLimits: ['Brak potwierdzenia po stronie downstream.'],
            openQuestions: [],
            gaps: [],
            confidence: 'medium',
            warnings: []
          }
        }
      ],
      meta: {
        references: [],
        visibilityLimits: ['Brak potwierdzenia po stronie downstream.'],
        openQuestions: [],
        gaps: [],
        confidence: 'medium',
        warnings: []
      }
    }
  };
}
