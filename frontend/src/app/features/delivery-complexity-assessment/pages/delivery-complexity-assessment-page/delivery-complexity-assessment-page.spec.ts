import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatTooltip } from '@angular/material/tooltip';
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
  DeliveryComplexityAssessmentExportEnvelope,
  DeliveryComplexityAssessmentJobStartRequest,
  DeliveryComplexityAssessmentJobStateSnapshot
} from '../../models/delivery-complexity-assessment.models';
import { DeliveryComplexityAssessmentApiService } from '../../services/delivery-complexity-assessment-api.service';
import { DeliveryComplexityAssessmentPageComponent } from './delivery-complexity-assessment-page';

describe('DeliveryComplexityAssessmentPageComponent', () => {
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
    expect(fixture.nativeElement.textContent).toContain('nie jest Delivery Complexity Assessment');
  });

  it('should present the token estimate instead of formatting the SDK multiplier as USD', async () => {
    const unit = completedUnit();
    const usage = {
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
    unit.usage = usage;
    const completed = snapshot('COMPLETED', 8, [unit]);
    completed.aggregate.usage = usage;
    const { fixture } = await createComponent({ localRun: completed, localRunId: 'job-1' });

    expect(fixture.nativeElement.textContent).toContain('szacowany koszt tokenów');
    expect(fixture.nativeElement.textContent).toContain('47');
    expect(fixture.nativeElement.querySelector('.unit-row__cost').textContent).not.toContain('—');
    expect(fixture.nativeElement.textContent).not.toContain('jednostek mnożnika SDK');
    const overallCostMetrics = Array.from(
      fixture.nativeElement.querySelectorAll('.cost-summary dl > div') as NodeListOf<HTMLElement>
    ).map((metric) => ({
      label: metric.querySelector('dt')?.textContent.trim(),
      value: metric.querySelector('dd')?.textContent.trim()
    }));
    expect(overallCostMetrics).toEqual([
      { label: 'Input', value: '1 045 393' },
      { label: 'Cache', value: '837 120' },
      { label: 'Output', value: '72 414' },
      { label: 'Wywołania AI', value: '47' }
    ]);
    expect(fixture.componentInstance['unitCostTooltip'](unit)).toBe(
      'Input: 1 045 393 tokenów · Cache: 837 120 tokenów · Output: 72 414 tokenów · 47 wywołań AI'
    );
  });

  it('should use singular AI call label in the unit cost tooltip', async () => {
    const unit = completedUnit();
    unit.usage = {
      inputTokens: 16_000,
      outputTokens: 890,
      cacheReadTokens: 12_000,
      cacheWriteTokens: 0,
      totalTokens: 16_890,
      cost: 0.03,
      apiDurationMs: 5_000,
      apiCallCount: 1,
      model: 'gpt-5.4-mini',
      contextTokenLimit: null,
      contextCurrentTokens: null,
      contextMessages: null
    };
    const { fixture } = await createComponent({
      localRun: snapshot('COMPLETED', 8, [unit]),
      localRunId: 'job-1'
    });

    expect(fixture.componentInstance['unitCostTooltip'](unit)).toBe(
      'Input: 16 000 tokenów · Cache: 12 000 tokenów · Output: 890 tokenów · 1 wywołanie AI'
    );
  });

  it('should explain every overall result metric and its calculation', async () => {
    const completed = snapshot('COMPLETED', 8, [completedUnit()]);
    const { fixture } = await createComponent({ localRun: completed, localRunId: 'job-1' });

    const tooltips = fixture.debugElement
      .queryAll(By.css('.overall-result__help'))
      .map((element) => element.injector.get(MatTooltip).message);

    expect(tooltips).toHaveLength(6);
    expect(tooltips[0]).toContain('projekcie CRM od 2026-07-01 do 2026-07-31');
    expect(tooltips[0]).toContain('issue Jira i powiązanych, scalonych Merge Requests');
    expect(tooltips[1]).toContain('Suma DSP z 1 ocenionych Delivery Units wynosi 8');
    expect(tooltips[1]).toContain('0, 1, 2, 3, 5, 8 lub 13');
    expect(tooltips[2]).toContain('średnia confidence z 1 ocenionych jednostek wynosi 0,85');
    expect(tooltips[2]).toContain('HIGH od 0,80, MEDIUM od 0,60, LOW poniżej 0,60');
    expect(tooltips[3]).toContain('1 z 1 wszystkich jednostek');
    expect(tooltips[4]).toContain('EXCLUDED (0) i NOT_SCORABLE (0) wynosi 0');
    expect(tooltips[5]).toContain('Delivery Units ze statusem FAILED: 0');
  });

  it('should render one expandable issue table with row warnings and no duplicate report bands', async () => {
    const unit = completedUnit();
    unit.assessment!.qualityFlags = ['Only a bounded diff was available.'];
    unit.visibilityLimits = ['Diff content was truncated.'];
    unit.errorMessage = 'Assessment completed with a parser warning.';
    const completed = snapshot('COMPLETED_WITH_WARNINGS', 8, [unit]);
    const { fixture } = await createComponent({ localRun: completed, localRunId: 'job-1' });

    expect(fixture.nativeElement.querySelectorAll('.units-section')).toHaveLength(1);
    expect(fixture.nativeElement.querySelector('.units-heading__status').textContent)
      .toContain('Zakończona z ostrzeżeniami');
    expect(fixture.nativeElement.querySelector('.unit-warning-icon')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.run-strip')).toBeNull();
    expect(fixture.nativeElement.querySelector('.aggregate-band')).toBeNull();
    expect(fixture.nativeElement.querySelector('.visibility-band')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-analysis-report-panel')).toBeNull();

    const expand = fixture.nativeElement.querySelector(
      '.unit-row button[aria-label="Rozwiń szczegóły"]'
    ) as HTMLButtonElement;
    expand.click();
    fixture.detectChanges();

    const details = fixture.nativeElement.querySelector('.unit-details') as HTMLElement;
    expect(details.textContent).toContain('Evidence');
    expect(details.textContent).toContain('Quality flags');
    expect(details.textContent).toContain('Visibility limits');
    expect(details.textContent).toContain('Warnings');
    expect(details.textContent).toContain('Assessment completed with a parser warning.');
    expect(details.textContent).toContain('Merge Requests');
    const mergeRequestLink = details.querySelector('.unit-merge-request-link') as HTMLAnchorElement;
    expect(mergeRequestLink.href).toBe('https://gitlab.example.com/mr/7');
    expect(mergeRequestLink.target).toBe('_blank');
    expect(details.firstElementChild?.classList.contains('unit-merge-requests')).toBe(true);
  });

  it('should render not scorable units as skipped with an info icon', async () => {
    const unit = completedUnit();
    unit.status = 'NOT_SCORABLE';
    unit.mergeRequests = [];
    unit.assessment = null;
    unit.visibilityLimits = ['CRM-1: GitLab returned no merge request candidates.'];
    const completed = snapshot('COMPLETED_WITH_WARNINGS', 0, [unit]);
    const { fixture } = await createComponent({ localRun: completed, localRunId: 'job-1' });

    const status = fixture.nativeElement.querySelector('.unit-row__status') as HTMLElement;
    expect(status.textContent).toContain('Pominięto');
    expect(status.querySelector('.unit-attention-icon--info .material-symbols-outlined')?.textContent?.trim())
      .toBe('info');
    expect(status.querySelector('.unit-warning-icon')).toBeNull();
  });

  it('should not render attention icons for waiting units', async () => {
    const unit = completedUnit();
    unit.status = 'PENDING';
    unit.assessment = null;
    unit.visibilityLimits = ['Pending evidence is not final.'];
    unit.errorMessage = 'Waiting for worker.';
    const active = snapshot('ANALYZING', 0, [unit]);
    const { fixture } = await createComponent({ localRun: active, localRunId: 'job-1' });

    const status = fixture.nativeElement.querySelector('.unit-row__status') as HTMLElement;
    expect(status.textContent).toContain('Oczekuje');
    expect(status.querySelector('.unit-attention-icon')).toBeNull();
    expect(status.querySelector('.unit-warning-icon')).toBeNull();
  });

  it('should expose every merge request as a link above assessment dimensions', async () => {
    const unit = completedUnit();
    unit.mergeRequests.push({
      identity: 'id:8',
      projectPath: 'crm/customer-web',
      iid: 8,
      title: 'Display customer status',
      webUrl: 'https://gitlab.example.com/mr/8',
      mergedAt: '2026-07-01T09:30:00Z',
      authorId: 101,
      authorName: 'mr-author-101',
      changedPaths: ['src/customer-status.ts']
    });
    const completed = snapshot('COMPLETED', 8, [unit]);
    const { fixture } = await createComponent({ localRun: completed, localRunId: 'job-1' });

    const expand = fixture.nativeElement.querySelector(
      '.unit-row button[aria-label="Rozwiń szczegóły"]'
    ) as HTMLButtonElement;
    expand.click();
    fixture.detectChanges();

    const details = fixture.nativeElement.querySelector('.unit-details') as HTMLElement;
    const links = Array.from(details.querySelectorAll('.unit-merge-request-link')) as HTMLAnchorElement[];
    expect(links.map((link) => link.href)).toEqual([
      'https://gitlab.example.com/mr/7',
      'https://gitlab.example.com/mr/8'
    ]);
    expect(details.querySelector('.unit-merge-requests')?.nextElementSibling?.classList.contains('dimension-panel'))
      .toBe(true);
  });

  it('should filter the visible units and aggregate by Jira team and MR author', async () => {
    const first = completedUnit();
    const second = completedUnit();
    second.unitId = 'DU-CRM-2';
    second.issues[0] = {
      ...second.issues[0],
      issueKey: 'CRM-2',
      issueUrl: 'https://jira.example.com/browse/CRM-2',
      summary: 'Customer onboarding',
      team: { id: '1901', name: 'Team B', fieldId: 'customfield_10000' }
    };
    second.mergeRequests[0] = {
      ...second.mergeRequests[0],
      identity: 'id:9',
      iid: 9,
      title: 'Customer onboarding',
      webUrl: 'https://gitlab.example.com/mr/9',
      authorId: 202,
      authorName: 'mr-author-202'
    };
    second.assessment = {
      ...second.assessment!,
      deliveredStoryPoints: 3,
      score100: 35,
      confidence: 0.71
    };
    const completed = snapshot('COMPLETED', 11, [first, second]);
    const { fixture } = await createComponent({ localRun: completed, localRunId: 'job-1' });

    const selects = fixture.nativeElement.querySelectorAll('.unit-filters select') as NodeListOf<HTMLSelectElement>;
    selects[0].value = optionValue(selects[0], 'Team A');
    selects[0].dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.units-count').textContent).toContain('1 / 2');
    expect(fixture.nativeElement.querySelector('.overall-result__primary strong').textContent).toContain('8');
    expect(fixture.nativeElement.querySelector('.units-table').textContent).toContain('CRM-1');
    expect(fixture.nativeElement.querySelector('.units-table').textContent).not.toContain('CRM-2');

    const clearButton = fixture.nativeElement.querySelector(
      'button[aria-label="Wyczyść filtry"]'
    ) as HTMLButtonElement;
    clearButton.click();
    fixture.detectChanges();
    const refreshedSelects = fixture.nativeElement.querySelectorAll('.unit-filters select') as NodeListOf<HTMLSelectElement>;
    refreshedSelects[1].value = optionValue(refreshedSelects[1], 'mr-author-202');
    refreshedSelects[1].dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.units-count').textContent).toContain('1 / 2');
    expect(fixture.nativeElement.querySelector('.overall-result__primary strong').textContent).toContain('3');
    expect(fixture.nativeElement.querySelector('.units-table').textContent).toContain('CRM-2');
    expect(fixture.nativeElement.querySelector('.units-table').textContent).not.toContain('CRM-1');
  });

  it('should warn when a visible issue has merge requests from multiple authors', async () => {
    const unit = completedUnit();
    unit.mergeRequests.push({
      ...unit.mergeRequests[0],
      identity: 'id:9',
      iid: 9,
      authorId: 202,
      authorName: 'mr-author-202'
    });
    const completed = snapshot('COMPLETED', 8, [unit]);
    const { fixture } = await createComponent({ localRun: completed, localRunId: 'job-1' });

    const indicator = fixture.nativeElement.querySelector('.multi-author-indicator') as HTMLElement;
    expect(indicator.textContent).toContain('1');
    expect(fixture.componentInstance['multiAuthorTooltip']()).toContain('CRM-1');
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
  queued?: DeliveryComplexityAssessmentJobStateSnapshot;
  completed?: DeliveryComplexityAssessmentJobStateSnapshot;
  localRun?: DeliveryComplexityAssessmentJobStateSnapshot;
  importResult?: DeliveryComplexityAssessmentJobStateSnapshot;
  localRunId?: string;
  localFeature?: string;
}) {
  const queued = options.queued ?? snapshot('QUEUED', 0, []);
  const completed = options.completed ?? snapshot('COMPLETED', 0, []);
  const api = {
    startJob: vi.fn<(request: DeliveryComplexityAssessmentJobStartRequest) => Observable<DeliveryComplexityAssessmentJobStateSnapshot>>(
      () => of(queued).pipe(delay(0))
    ),
    getJob: vi.fn<(jobId: string) => Observable<DeliveryComplexityAssessmentJobStateSnapshot>>(
      () => of(completed).pipe(delay(0))
    ),
    importRun: vi.fn<(document: unknown) => Observable<DeliveryComplexityAssessmentJobStateSnapshot>>(
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
      feature: options.localFeature ?? 'delivery-complexity-assessment',
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
    imports: [DeliveryComplexityAssessmentPageComponent],
    providers: [
      { provide: DeliveryComplexityAssessmentApiService, useValue: api },
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

  const fixture = TestBed.createComponent(DeliveryComplexityAssessmentPageComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  fixture.detectChanges();
  return { fixture, api, history };
}

function envelope(job: DeliveryComplexityAssessmentJobStateSnapshot): DeliveryComplexityAssessmentExportEnvelope {
  return {
    schema: 'tdw.delivery-complexity-assessment-export',
    version: 2,
    exportedAt: '2026-07-01T10:00:00Z',
    payload: {
      type: 'delivery-complexity-assessment',
      resultContract: 'delivery-complexity-assessment-v2',
      job
    }
  };
}

function snapshot(
  status: string,
  points: number,
  units: DeliveryComplexityAssessmentJobStateSnapshot['units']
): DeliveryComplexityAssessmentJobStateSnapshot {
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
    }
  };
}

function completedUnit(): DeliveryComplexityAssessmentJobStateSnapshot['units'][number] {
  return {
    unitId: 'DU-CRM-1',
    status: 'COMPLETED',
    issues: [{
      issueKey: 'CRM-1',
      issueUrl: 'https://jira.example.com/browse/CRM-1',
      summary: 'Customer status',
      issueType: 'Story',
      doneAt: '2026-07-01T09:00:00Z',
      team: { id: '1900', name: 'Team A', fieldId: 'customfield_10000' }
    }],
    mergeRequests: [{
      identity: 'id:7',
      projectPath: 'crm/customer-api',
      iid: 7,
      title: 'Customer status',
      webUrl: 'https://gitlab.example.com/mr/7',
      mergedAt: '2026-07-01T09:00:00Z',
      authorId: 101,
      authorName: 'mr-author-101',
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
    usage: null
  };
}

function optionValue(select: HTMLSelectElement, labelPart: string): string {
  const option = Array.from(select.options).find((candidate) =>
    candidate.textContent?.includes(labelPart)
  );
  expect(option).toBeDefined();
  return option!.value;
}
