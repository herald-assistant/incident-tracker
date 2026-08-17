import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Observable, delay, of } from 'rxjs';

import {
  AnalysisAiModelOptionsResponse,
  GitHubAuthStatus,
  LocalAnalysisRunDetailResponse
} from '../../../../core/models/analysis.models';
import { AiOptionsApiService } from '../../../../core/services/ai-options-api.service';
import { AnalysisJobPollingOptions, AnalysisJobPollingService } from '../../../../core/services/analysis-job-polling.service';
import { AnalysisRunHistoryApiService } from '../../../../core/services/analysis-run-history-api.service';
import { GithubAuthService } from '../../../../core/services/github-auth.service';
import {
  DeliveryEffectivenessAssessmentExportEnvelope,
  DeliveryEffectivenessAssessmentJobStartRequest,
  DeliveryEffectivenessAssessmentJobStateSnapshot
} from '../../models/delivery-effectiveness-assessment.models';
import { DeliveryEffectivenessAssessmentApiService } from '../../services/delivery-effectiveness-assessment-api.service';
import { DeliveryEffectivenessAssessmentPageComponent } from './delivery-effectiveness-assessment-page';

describe('DeliveryEffectivenessAssessmentPageComponent', () => {
  it('should start, poll and keep the partial unit result visible in the aggregate', async () => {
    const queued = snapshot('QUEUED', 0, []);
    const completed = snapshot('COMPLETED', 8, [completedUnit()]);
    const { fixture, api } = await createComponent({ queued, completed });

    fixture.componentInstance.jiraProjectControl.setValue('crm');
    fixture.componentInstance.fromDateControl.setValue('2026-07-01');
    fixture.componentInstance.toDateControl.setValue('2026-07-31');
    fixture.detectChanges();
    const startButton = fixture.nativeElement.querySelector('.primary-button') as HTMLButtonElement;
    startButton.click();
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(api.getJob).toHaveBeenCalledWith('job-1');
    });

    expect(api.startJob).toHaveBeenCalledWith({
      jiraProject: 'CRM',
      fromDate: '2026-07-01',
      toDate: '2026-07-31',
      model: 'gpt-5',
      reasoningEffort: 'medium'
    });
    expect(fixture.nativeElement.textContent).toContain('8');
    expect(fixture.nativeElement.textContent).toContain('CRM-1');
    expect(fixture.nativeElement.textContent).toContain('Zakończona');
  });

  it('should restore an active local run and continue live polling', async () => {
    const active = snapshot('ANALYZING', 3, [completedUnit()]);
    const completed = snapshot('COMPLETED_WITH_WARNINGS', 3, [completedUnit()]);
    const { fixture, api, history } = await createComponent({
      localRun: active,
      completed,
      localRunId: 'job-1'
    });

    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(api.getJob).toHaveBeenCalledWith('job-1');
      expect(fixture.nativeElement.textContent).toContain('Zakończona z ostrzeżeniami');
    });

    expect(history.getRun).toHaveBeenCalledWith('job-1');
    expect(fixture.nativeElement.textContent).toContain('Stored CRM assessment');
    expect(fixture.nativeElement.textContent).toContain('CRM-1');
  });

  it('should reject a local run owned by another feature', async () => {
    const { fixture, history } = await createComponent({
      localRun: snapshot('ANALYZING', 0, []),
      localRunId: 'job-1',
      localFeature: 'flow-explorer'
    });

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(history.getRun).toHaveBeenCalledWith('job-1');
    expect(fixture.nativeElement.textContent).toContain('nie jest Delivery Effectiveness Assessment');
  });

  it('should present the token estimate instead of formatting the SDK multiplier as USD', async () => {
    const completed = snapshot('COMPLETED', 8, [completedUnit()]);
    completed.aggregate.usage = {
      inputTokens: 1_045_393,
      outputTokens: 72_414,
      cacheReadTokens: 837_120,
      cacheWriteTokens: 0,
      totalTokens: 1_117_807,
      cost: 15.51,
      apiDurationMs: 622_916,
      apiCallCount: 47,
      model: 'gpt-5.4-mini',
      contextTokenLimit: null,
      contextCurrentTokens: null,
      contextMessages: null
    };
    const { fixture } = await createComponent({ localRun: completed, localRunId: 'job-1' });

    expect(fixture.nativeElement.textContent).toContain('szacowany koszt tokenów');
    expect(fixture.nativeElement.textContent).toContain('15.51 jednostek mnożnika SDK');
    expect(fixture.nativeElement.textContent).not.toContain('$15.51 koszt');
  });

  it('should import a server-validated assessment and render it from the new history run', async () => {
    const completed = snapshot('COMPLETED_WITH_WARNINGS', 8, [completedUnit()]);
    const imported = { ...completed, jobId: 'delivery-assessment-import-1' };
    const { fixture, api } = await createComponent({ completed, importResult: imported });
    const portable = envelope(completed);
    const fileContent = JSON.stringify(portable);
    const file = new File([fileContent], 'crm-assessment.json', { type: 'application/json' });
    Object.defineProperty(file, 'text', {
      configurable: true,
      value: () => Promise.resolve(fileContent)
    });
    const input = fixture.nativeElement.querySelector('.run-file-input') as HTMLInputElement;
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [file]
    });

    input.dispatchEvent(new Event('change'));
    await vi.waitFor(() => {
      fixture.detectChanges();
      expect(api.importRun).toHaveBeenCalledWith(portable);
      expect(fixture.nativeElement.textContent).toContain('Import: crm-assessment.json');
    });

    expect(fixture.componentInstance.job()?.jobId).toBe('delivery-assessment-import-1');
    expect(fixture.nativeElement.textContent).toContain('CRM-1');
  });

  it('should export a terminal assessment through Analysis History', async () => {
    const completed = snapshot('COMPLETED', 8, [completedUnit()]);
    const originalCreateObjectURL = URL.createObjectURL;
    const originalRevokeObjectURL = URL.revokeObjectURL;
    const createObjectURL = vi.fn(() => 'blob:delivery-assessment-export');
    const revokeObjectURL = vi.fn();
    const clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: createObjectURL });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: revokeObjectURL });

    try {
      const { fixture, history } = await createComponent({
        localRun: completed,
        localRunId: 'job-1'
      });
      const exportButton = fixture.nativeElement.querySelector(
        'button[aria-label="Eksportuj run"]'
      ) as HTMLButtonElement;

      expect(exportButton.disabled).toBe(false);
      exportButton.click();
      await vi.waitFor(() => {
        fixture.detectChanges();
        expect(history.exportRun).toHaveBeenCalledWith('job-1');
        expect(createObjectURL).toHaveBeenCalledTimes(1);
      });
      expect(clickSpy).toHaveBeenCalledTimes(1);
    } finally {
      clickSpy.mockRestore();
      Object.defineProperty(URL, 'createObjectURL', {
        configurable: true,
        value: originalCreateObjectURL
      });
      Object.defineProperty(URL, 'revokeObjectURL', {
        configurable: true,
        value: originalRevokeObjectURL
      });
    }
  });
});

async function createComponent(options: {
  queued?: DeliveryEffectivenessAssessmentJobStateSnapshot;
  completed?: DeliveryEffectivenessAssessmentJobStateSnapshot;
  localRun?: DeliveryEffectivenessAssessmentJobStateSnapshot;
  importResult?: DeliveryEffectivenessAssessmentJobStateSnapshot;
  localRunId?: string;
  localFeature?: string;
}) {
  const queued = options.queued ?? snapshot('QUEUED', 0, []);
  const completed = options.completed ?? snapshot('COMPLETED', 0, []);
  const api = {
    startJob: vi.fn<(request: DeliveryEffectivenessAssessmentJobStartRequest) => Observable<DeliveryEffectivenessAssessmentJobStateSnapshot>>(
      () => of(queued).pipe(delay(0))
    ),
    getJob: vi.fn<(jobId: string) => Observable<DeliveryEffectivenessAssessmentJobStateSnapshot>>(
      () => of(completed).pipe(delay(0))
    ),
    importRun: vi.fn<(document: unknown) => Observable<DeliveryEffectivenessAssessmentJobStateSnapshot>>(
      () => of(options.importResult ?? completed).pipe(delay(0))
    )
  };
  const aiOptions = {
    getOptions: vi.fn<() => Observable<AnalysisAiModelOptionsResponse>>(() => of({
      defaultModel: 'gpt-5',
      defaultReasoningEffort: 'medium',
      defaultReasoningEfforts: ['low', 'medium', 'high'],
      models: [{
        id: 'gpt-5',
        name: 'GPT-5',
        supportsReasoningEffort: true,
        reasoningEfforts: ['low', 'medium', 'high'],
        defaultReasoningEffort: 'medium'
      }]
    }))
  };
  const authStatus: GitHubAuthStatus = {
    mode: 'LOCAL_TOKEN',
    required: false,
    connected: true,
    reauthRequired: false
  };
  const auth = {
    getStatus: vi.fn(() => of(authStatus)),
    connect: vi.fn()
  };
  const history = {
    getRun: vi.fn<(analysisId: string) => Observable<LocalAnalysisRunDetailResponse>>(() => of({
      analysisId: 'job-1',
      feature: options.localFeature ?? 'delivery-effectiveness-assessment',
      name: 'Stored CRM assessment',
      status: options.localRun?.status ?? 'ANALYZING',
      createdAt: '2026-07-01T10:00:00Z',
      updatedAt: '2026-07-01T10:01:00Z',
      completedAt: '',
      exportEnvelope: envelope(options.localRun ?? queued),
      continuationEnabled: false
    })),
    exportRun: vi.fn<(analysisId: string) => Observable<unknown>>(() =>
      of(envelope(options.localRun ?? completed)).pipe(delay(0))
    )
  };
  const polling = {
    poll: vi.fn(<T>(pollingOptions: AnalysisJobPollingOptions<T>) => pollingOptions.load())
  };

  await TestBed.configureTestingModule({
    imports: [DeliveryEffectivenessAssessmentPageComponent],
    providers: [
      { provide: DeliveryEffectivenessAssessmentApiService, useValue: api },
      { provide: AiOptionsApiService, useValue: aiOptions },
      { provide: GithubAuthService, useValue: auth },
      { provide: AnalysisRunHistoryApiService, useValue: history },
      { provide: AnalysisJobPollingService, useValue: polling },
      {
        provide: ActivatedRoute,
        useValue: { queryParamMap: of(convertToParamMap({ localRunId: options.localRunId ?? '' })) }
      }
    ]
  }).compileComponents();

  const fixture = TestBed.createComponent(DeliveryEffectivenessAssessmentPageComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, api, history };
}

function envelope(job: DeliveryEffectivenessAssessmentJobStateSnapshot): DeliveryEffectivenessAssessmentExportEnvelope {
  return {
    schema: 'tdw.delivery-effectiveness-assessment-export',
    version: 1,
    exportedAt: '2026-07-01T10:00:00Z',
    payload: {
      type: 'delivery-effectiveness-assessment',
      resultContract: 'delivery-effectiveness-assessment-v1',
      job
    }
  };
}

function snapshot(
  status: string,
  points: number,
  units: DeliveryEffectivenessAssessmentJobStateSnapshot['units']
): DeliveryEffectivenessAssessmentJobStateSnapshot {
  const terminal = ['COMPLETED', 'COMPLETED_WITH_WARNINGS', 'FAILED'].includes(status);
  return {
    jobId: 'job-1',
    jiraProject: 'CRM',
    fromDate: '2026-07-01',
    toDate: '2026-07-31',
    aiModel: 'gpt-5',
    reasoningEffort: 'medium',
    status,
    currentStepCode: terminal ? 'COMPLETED' : 'UNIT_ASSESSMENT',
    currentStepLabel: terminal ? 'Completed' : 'Delivery Unit assessment',
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-07-01T10:00:00Z',
    updatedAt: '2026-07-01T10:01:00Z',
    completedAt: terminal ? '2026-07-01T10:01:00Z' : null,
    discoveredIssues: units.length,
    processedIssues: units.length,
    totalIssues: units.length,
    effectiveJql: 'project = "CRM"',
    steps: [],
    contextSections: [],
    aiActivityEvents: [],
    units,
    aggregate: {
      totalDeliveredStoryPoints: points,
      distribution: points ? { [String(points)]: 1 } : {},
      totalUnits: units.length,
      assessedUnits: units.length,
      excludedUnits: 0,
      notScorableUnits: 0,
      failedUnits: 0,
      coverage: units.length ? 1 : 0,
      confidence: units.length ? 'HIGH' : 'LOW',
      usage: null
    },
    visibilityLimits: [],
    report: null
  };
}

function completedUnit(): DeliveryEffectivenessAssessmentJobStateSnapshot['units'][number] {
  return {
    unitId: 'DU-CRM-1',
    status: 'COMPLETED',
    issues: [{
      issueKey: 'CRM-1',
      issueUrl: 'https://jira.example.com/browse/CRM-1',
      summary: 'Customer status',
      issueType: 'Story',
      doneAt: '2026-07-01T09:00:00Z'
    }],
    mergeRequests: [{
      identity: 'id:7',
      projectPath: 'crm/customer-api',
      iid: 7,
      title: 'Customer status',
      webUrl: 'https://gitlab.example.com/mr/7',
      mergedAt: '2026-07-01T09:00:00Z',
      changedPaths: ['src/CustomerStatus.java']
    }],
    assessment: {
      deliveredStoryPoints: 8,
      score100: 72,
      dimensions: {
        outcomeBreadth: 2,
        domainDecisionComplexity: 3,
        applicationFlowComplexity: 3,
        boundaryAndDataComplexity: 2,
        verificationStateSpace: 3,
        implementedCompatibilityScope: 2
      },
      confidence: 0.85,
      evidenceSummary: ['API and validation changed'],
      qualityFlags: []
    },
    visibilityLimits: [],
    errorCode: null,
    errorMessage: null,
    startedAt: '2026-07-01T10:00:00Z',
    completedAt: '2026-07-01T10:01:00Z',
    preparedPrompt: 'one-shot prompt with effective skill, Jira and MR code',
    promptPreparedAt: '2026-07-01T10:00:01Z',
    usage: null,
    report: null
  };
}
