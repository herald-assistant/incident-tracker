import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatTooltip } from '@angular/material/tooltip';
import { MatTooltipHarness } from '@angular/material/tooltip/testing';
import { By } from '@angular/platform-browser';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { BehaviorSubject, of, throwError } from 'rxjs';

import { choice } from '../../../../../testing/form-controls';
import {
  AnalysisAiModelOptionsResponse,
  GitHubAuthStatus
} from '../../../../core/models/analysis.models';
import { AiOptionsApiService } from '../../../../core/services/ai-options-api.service';
import { AnalysisJobPollingService } from '../../../../core/services/analysis-job-polling.service';
import { AnalysisRunHistoryApiService } from '../../../../core/services/analysis-run-history-api.service';
import { GithubAuthService } from '../../../../core/services/github-auth.service';
import {
  ConfigDriftViewerBranchCoverage,
  ConfigDriftViewerDeepPreflight,
  ConfigDriftViewerDiffProjection,
  ConfigDriftViewerComponentRunSnapshot,
  ConfigDriftViewerInputOptions,
  ConfigDriftViewerJobStateSnapshot,
  ConfigDriftViewerResult
} from '../../models/config-drift-viewer.models';
import { ConfigDriftViewerApiService } from '../../services/config-drift-viewer-api.service';
import { ConfigDriftViewerPageComponent } from './config-drift-viewer-page';

describe('ConfigDriftViewerPageComponent', () => {
  let fixture: ComponentFixture<ConfigDriftViewerPageComponent>;
  let api: {
    getInputOptions: ReturnType<typeof vi.fn>;
    getDeepPreflight: ReturnType<typeof vi.fn>;
    startJob: ReturnType<typeof vi.fn>;
    getJob: ReturnType<typeof vi.fn>;
    importResult: ReturnType<typeof vi.fn>;
  };
  let polling: { poll: ReturnType<typeof vi.fn> };
  let historyApi: { getRun: ReturnType<typeof vi.fn> };
  let routeParams: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let githubAuth: {
    getStatus: ReturnType<typeof vi.fn>;
    openSettings: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    api = {
      getInputOptions: vi.fn(() => of(inputOptions())),
      getDeepPreflight: vi.fn(() => of(readyPreflight())),
      startJob: vi.fn(() => of(job({ status: 'QUEUED', result: null }))),
      getJob: vi.fn(() => of(job())),
      importResult: vi.fn(() => of(job({ imported: true })))
    };
    polling = {
      poll: vi.fn(() => of(job()))
    };
    historyApi = { getRun: vi.fn(() => of({ feature: 'config-drift-viewer' })) };
    routeParams = new BehaviorSubject(convertToParamMap({}));
    githubAuth = {
      getStatus: vi.fn(() => of(localTokenAuthStatus())),
      openSettings: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [ConfigDriftViewerPageComponent],
      providers: [
        { provide: ConfigDriftViewerApiService, useValue: api },
        { provide: AiOptionsApiService, useValue: { getOptions: () => of(aiOptions()) } },
        { provide: AnalysisJobPollingService, useValue: polling },
        { provide: GithubAuthService, useValue: githubAuth },
        { provide: Router, useValue: { navigate: vi.fn(() => Promise.resolve(true)) } },
        {
          provide: AnalysisRunHistoryApiService,
          useValue: historyApi
        },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: routeParams.asObservable() }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ConfigDriftViewerPageComponent);
    fixture.detectChanges();
  });

  it('should render the BASIC form from backend options without calling DEEP capabilities', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const runButton = buttonContaining(compiled, 'Run verification');

    expect(compiled.textContent).toContain('Porównaj konfigurację środowisk');
    expect(compiled.textContent).toContain('3 z 3 wybranych');
    expect(compiled.textContent).toContain('Gotowe do porównania konfiguracji');
    expect(compiled.textContent).toContain('deterministyczny diff bez AI');
    expect(compiled.textContent).not.toContain('Model AI');
    expect(compiled.textContent).not.toContain('Reasoning effort');
    expect(compiled.textContent).not.toContain('Preferowany ref kodu');
    expect(runButton?.disabled).toBe(false);
    expect(buttonContaining(compiled, 'Basic')?.querySelector('.mode-option__copy')).not.toBeNull();
    expect(api.getDeepPreflight).not.toHaveBeenCalled();
  });

  it('should select all systems by default and support clear, select all and checkbox changes', () => {
    expect(fixture.componentInstance.systemControl.value).toEqual([
      'backend',
      'customer-profile',
      'notifications'
    ]);

    buttonContaining(fixture.nativeElement, '3 z 3 wybranych')?.click();
    fixture.detectChanges();
    let compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Backend · backend');
    expect(compiled.textContent).toContain('Customer Profile · customer-profile');
    expect(compiled.textContent).toContain('Notifications · notifications');

    buttonContaining(compiled, 'Wyczyść')?.click();
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance.systemControl.value).toEqual([]);
    expect(compiled.textContent).toContain('0 z 3 wybranych');
    expect(compiled.textContent).toContain('Wybierz co najmniej jeden komponent');
    expect(buttonContaining(compiled, 'Run verification')?.disabled).toBe(true);

    buttonContaining(compiled, 'Zaznacz wszystkie')?.click();
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
    const customerProfile = labelContaining(compiled, 'Customer Profile · customer-profile')
      ?.querySelector<HTMLInputElement>('input[type="checkbox"]');
    customerProfile?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.systemControl.value).toEqual([
      'backend',
      'notifications'
    ]);
    expect(fixture.nativeElement.textContent).toContain('2 z 3 wybranych');
    expect(buttonContaining(fixture.nativeElement, 'Run verification')?.disabled).toBe(false);

    buttonContaining(fixture.nativeElement, 'Run verification')?.click();
    expect(api.startJob).toHaveBeenCalledWith(expect.objectContaining({
      systemIds: ['backend', 'notifications']
    }));
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith([], {
      relativeTo: expect.anything(),
      queryParams: { localRunId: 'job-1' },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  });

  it('should expose DEEP as coming soon without allowing the operator to select it', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const deepButton = buttonContaining(compiled, 'Deep');

    expect(deepButton?.disabled).toBe(true);
    expect(deepButton?.getAttribute('aria-label')).toBe('Deep — wkrótce');
    expect(deepButton?.querySelector('.mode-option__badge')?.textContent?.trim()).toBe('Soon');

    deepButton?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.modeControl.value).toBe('BASIC');
    expect(api.getDeepPreflight).not.toHaveBeenCalled();
  });

  it('should start BASIC verification without AI-only request fields or AI result state', () => {
    const basic = basicResult();
    basic.visibilityLimits = ['Nie potwierdzono runtime.'];
    const completed = job({
      mode: 'BASIC',
      codeRef: null,
      aiModel: null,
      reasoningEffort: null,
      status: 'COMPLETED',
      preparedPrompt: null,
      result: basic,
      report: null
    });
    api.startJob.mockReturnValue(of(job({
      mode: 'BASIC',
      codeRef: null,
      aiModel: null,
      reasoningEffort: null,
      status: 'QUEUED',
      completedAt: null,
      preparedPrompt: null,
      result: null,
      report: null
    })));
    polling.poll.mockReturnValue(of(completed));

    buttonContaining(fixture.nativeElement, 'Run verification')?.click();
    fixture.detectChanges();

    expect(api.startJob).toHaveBeenCalledWith({
      mode: 'BASIC',
      repositoryId: 'runtime-config',
      systemIds: ['backend', 'customer-profile', 'notifications'],
      sourceBranch: 'dev1',
      targetBranch: 'test2'
    });
    const compiled = fixture.nativeElement as HTMLElement;
    const header = compiled.querySelector('.configuration-diff__header')!;
    expect(header.querySelector('h2')?.textContent?.trim()).toBe('dev1 → test2');
    expect(header.textContent).toContain('3 zmiany · 2 pliki');
    expect(header.textContent).toContain('Wymaga weryfikacji');
    expect(header.querySelector('button[aria-label="Udostępnij wynik"]')).not.toBeNull();
    expect(compiled.querySelector('.run-context')).toBeNull();
    expect(compiled.textContent).not.toContain('Configuration result');
    expect(compiled.textContent).not.toContain('Różnice w plikach konfiguracyjnych');
    expect(compiled.textContent).not.toContain('DETERMINISTYCZNE FAKTY');
    const tooltip = fixture.debugElement.query(By.css('.comparison-notice')).injector.get(MatTooltip);
    expect(tooltip.message).toContain('Ustalenia: 1');
    expect(tooltip.message).toContain('dev1: kompletne dane');
    expect(tooltip.message).toContain('test2: kompletne dane');
    expect(compiled.querySelector('.metric-grid')).toBeNull();
    expect(compiled.querySelector('.coverage-grid')).toBeNull();
    expect(compiled.textContent).not.toContain('AI second opinion');
    expect(compiled.textContent).not.toContain('NOT_ASSESSED');
    expect(compiled.textContent).not.toContain('Raport operatora');
    expect(compiled.querySelector('button[aria-label="Udostępnij wynik"]')).not.toBeNull();
    expect(fixture.componentInstance.shareDocument()?.markdown).toContain('## Uwagi do porównania');
    expect(fixture.componentInstance.shareDocument()?.markdown).not.toContain('Nie potwierdzono runtime.');
    expect(fixture.componentInstance.shareDocument()?.markdownWithMeta).toContain('Nie potwierdzono runtime.');
  });

  it('should keep context and results in the active tab panel and isolate a failed component', async () => {
    const backendJob = job({
      mode: 'BASIC',
      status: 'COMPLETED',
      codeRef: null,
      aiModel: null,
      reasoningEffort: null,
      preparedPrompt: null,
      result: basicResult(),
      report: null
    });
    const backend = backendJob.components[0]!;
    const customerProfileResult = basicResult();
    customerProfileResult.deterministicResult = {
      ...customerProfileResult.deterministicResult,
      systemId: 'customer-profile',
      systemLabel: 'Customer Profile',
      configurationDirectory: 'customer-profile'
    };
    customerProfileResult.configurationDiff = {
      ...customerProfileResult.configurationDiff!,
      files: customerProfileResult.configurationDiff!.files.map((file) => ({
        ...file,
        sourcePath: file.sourcePath?.replace('backend/', 'customer-profile/') ?? null,
        targetPath: file.targetPath?.replace('backend/', 'customer-profile/') ?? null
      }))
    };
    const customerProfile: ConfigDriftViewerComponentRunSnapshot = {
      ...backend,
      componentRunId: 'job-1:1',
      systemId: 'customer-profile',
      systemLabel: 'Customer Profile',
      configurationDirectory: 'customer-profile',
      result: customerProfileResult
    };
    const notifications: ConfigDriftViewerComponentRunSnapshot = {
      ...backend,
      componentRunId: 'job-1:2',
      systemId: 'notifications',
      systemLabel: 'Notifications',
      configurationDirectory: 'notifications',
      status: 'FAILED',
      errorCode: 'RUNTIME_CONFIG_SOURCE_FAILED',
      errorMessage: 'Nie udało się pobrać konfiguracji Notifications.',
      result: null,
      report: null
    };

    fixture.componentInstance.job.set({
      ...backendJob,
      status: 'COMPLETED_WITH_LIMITATIONS',
      systemIds: ['backend', 'customer-profile', 'notifications'],
      components: [backend, customerProfile, notifications]
    });
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    let tabs = Array.from(
      compiled.querySelectorAll<HTMLButtonElement>('.analysis-result-tab')
    );
    expect(tabs.map((tab) => tab.querySelector('.analysis-result-tab__label')?.textContent?.trim())).toEqual([
      'Backend',
      'Customer Profile',
      'Notifications'
    ]);
    expect(tabs[0]?.querySelector('.analysis-result-tab__status-icon')).toBeNull();
    expect(tabs[1]?.querySelector('.analysis-result-tab__status-icon')).toBeNull();
    expect(tabs[2]?.querySelector('.analysis-result-tab__status-icon--error')).not.toBeNull();
    expect(tabs[2]?.getAttribute('aria-label')).toContain('Analiza zakończona błędem');
    expect(fixture.componentInstance.componentTabs()[2].status?.tooltip)
      .toContain('Nie udało się pobrać konfiguracji Notifications.');
    expect(tabs[0]?.getAttribute('aria-selected')).toBe('true');
    expect(fixture.nativeElement.textContent).toContain('backend/application.yml.kv');
    const panel = compiled.querySelector('[role="tabpanel"]')!;
    expect(panel.querySelector('.configuration-diff__header h2')?.textContent).toBe('dev1 → test2');
    expect(fixture.componentInstance.comparisonDetails()).toContain('Komponent: Backend');
    expect(panel.querySelector('app-config-drift-viewer-diff-renderer')?.textContent)
      .toContain('backend/application.yml.kv');
    expect(panel.getAttribute('aria-labelledby')).toBe(tabs[0].id);

    tabs[1]?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.activeComponent()?.systemId).toBe('customer-profile');
    expect(fixture.nativeElement.textContent).toContain('customer-profile/application.yml.kv');
    expect(fixture.nativeElement.textContent).not.toContain('backend/application.yml.kv');
    expect(panel.querySelector('.configuration-diff__header h2')?.textContent).toBe('dev1 → test2');
    expect(fixture.componentInstance.comparisonDetails()).toContain('Komponent: Customer Profile');
    expect(panel.querySelector('app-config-drift-viewer-diff-renderer')?.textContent)
      .toContain('customer-profile/application.yml.kv');
    expect(panel.getAttribute('aria-labelledby')).toBe(tabs[1].id);

    tabs = Array.from(
      compiled.querySelectorAll<HTMLButtonElement>('.analysis-result-tab')
    );
    tabs[2]?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance.activeComponent()?.systemId).toBe('notifications');
    expect(fixture.nativeElement.textContent).toContain(
      'Nie udało się pobrać konfiguracji Notifications.'
    );
    expect(fixture.nativeElement.textContent).toContain('RUNTIME_CONFIG_SOURCE_FAILED');
    expect(panel.querySelector('.configuration-diff__header')).toBeNull();
    expect(panel.querySelector('.run-context h2')?.textContent).toBe('dev1 → test2');
    expect(fixture.componentInstance.comparisonDetails()).toContain('Komponent: Notifications');
    expect(panel.querySelector('[role="alert"]')?.textContent).toContain('RUNTIME_CONFIG_SOURCE_FAILED');
    expect(panel.querySelector('app-config-drift-viewer-diff-renderer')).toBeNull();
    expect(panel.getAttribute('aria-labelledby')).toBe(tabs[2].id);

    tabs[0].click();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(panel.querySelector('[role="alert"]')).toBeNull();
    expect(panel.querySelector('app-config-drift-viewer-diff-renderer')?.textContent)
      .toContain('backend/application.yml.kv');
    expect(compiled.querySelectorAll('.component-result [role="tabpanel"]')).toHaveLength(1);
  });

  it('should replace per-component loaders with success or limitations without changing the selected tab', () => {
    const running = job({ status: 'RUNNING', result: null, completedAt: null });
    const backend = running.components[0];
    const profile: ConfigDriftViewerComponentRunSnapshot = {
      ...backend, componentRunId: 'job-1:1', systemId: 'customer-profile',
      systemLabel: 'Customer Profile', status: 'QUEUED'
    };
    fixture.componentInstance.job.set({ ...running, components: [backend, profile] });
    fixture.componentInstance.activeComponentId.set('customer-profile');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.analysis-result-tab__spinner')).toHaveLength(2);
    expect(fixture.componentInstance.componentTabs().map(tab => tab.status?.label))
      .toEqual(['Analiza w toku', 'Oczekuje na uruchomienie']);

    const limitedResult = basicResult();
    limitedResult.visibilityLimits = ['Brak pliku w test2.'];
    fixture.componentInstance.job.set({
      ...running, status: 'COMPLETED_WITH_LIMITATIONS',
      components: [
        { ...backend, status: 'COMPLETED', result: basicResult() },
        { ...profile, status: 'COMPLETED_WITH_LIMITATIONS', result: limitedResult }
      ]
    });
    fixture.detectChanges();

    const tabs = compiled.querySelectorAll('.analysis-result-tab');
    expect(compiled.querySelector('.analysis-result-tab__spinner')).toBeNull();
    expect(tabs[0].textContent?.trim()).toBe('Backend');
    expect(tabs[0].querySelector('.analysis-result-tab__status-icon')).toBeNull();
    expect(tabs[1].querySelector('.analysis-result-tab__status-icon')?.textContent?.trim()).toBe('info');
    expect(tabs[1].getAttribute('aria-label')).toContain('Analiza zakończona z ograniczeniami');
    expect(fixture.componentInstance.componentTabs()[1].status?.tooltip).toContain('Brak pliku w test2.');
    expect(fixture.componentInstance.activeComponent()?.systemId).toBe('customer-profile');
    expect(tabs[1].getAttribute('aria-selected')).toBe('true');
  });

  it('should retain incomplete coverage warnings for a completed run without differences, independently of edited inputs', async () => {
    const comparison = basicResult();
    comparison.deterministicResult.status = 'NO_BLOCKING_ANOMALIES';
    comparison.deterministicResult.differences = [];
    comparison.deterministicResult.findings = [];
    comparison.deterministicResult.targetCoverage = {
      branch: 'test2', branchExists: true,
      files: [{
        role: 'LOCAL_VAR', path: 'crm/local.var', status: 'MISSING',
        commitId: null, lastCommitId: null, lastModifiedAt: null, sizeBytes: null, errorCode: null
      }]
    };
    comparison.configurationDiff.files = [];
    fixture.componentInstance.job.set(job({ mode: 'BASIC', status: 'COMPLETED', result: comparison, report: null }));
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const header = compiled.querySelector('.configuration-diff__header')!;
    expect(header.textContent).toContain('0 zmian · 0 plików');
    expect(header.textContent).toContain('Niepełne porównanie');
    expect(compiled.textContent).not.toContain('Uwagi do porównania');
    expect(compiled.querySelector('[aria-label="Filtr ważności uwag"]')).toBeNull();
    expect(compiled.querySelector('.finding-list')).toBeNull();
    const tooltip = await TestbedHarnessEnvironment.loader(fixture).getHarness(
      MatTooltipHarness.with({ selector: '.comparison-notice' })
    );
    await tooltip.show();
    expect(await tooltip.getTooltipText()).toContain('test2: niepełne dane');
    expect(await tooltip.getTooltipText()).toContain('crm/local.var: brak pliku');
    expect(await tooltip.getTooltipText()).toContain('Przebieg: Analiza zakończona');
    await tooltip.hide();

    fixture.componentInstance.sourceBranchControl.setValue('other-dev');
    fixture.componentInstance.targetBranchControl.setValue('other-test');
    fixture.detectChanges();
    expect(header.querySelector('h2')?.textContent).toBe('dev1 → test2');
    expect(fixture.componentInstance.comparisonDetails()).toContain('test2: niepełne dane');
    expect(fixture.componentInstance.comparisonDetails()).not.toContain('other-test');
  });

  it('should keep the findings section and allow resetting a filter with no matches', async () => {
    fixture.componentInstance.job.set(job({ status: 'COMPLETED', result: basicResult(), report: null }));
    fixture.componentInstance.findingSeverityFilter.set('INFO');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const findings = compiled.querySelector('.finding-list')!;
    const section = findings.closest('section')!;
    expect(section.querySelector('h3')?.textContent).toBe('Uwagi do porównania');
    expect(section.querySelector('.result-section__heading p')?.textContent).toBe('0 z 1');
    expect(findings.querySelector('.finding-card')).toBeNull();
    expect(findings.textContent).toContain('Brak ustaleń dla wybranego filtra.');

    const filter = await choice(fixture, '[aria-label="Filtr ważności uwag"]');
    await filter.clickOptions({ text: 'Wszystkie poziomy' });
    fixture.detectChanges();

    expect(section.querySelector('.result-section__heading p')?.textContent).toBe('1 z 1');
    expect(findings.querySelectorAll('.finding-card')).toHaveLength(1);
    expect(findings.querySelector('.empty-inline')).toBeNull();
  });

  it.each([
    { scenario: 'missing coverage', coverage: false, status: 'COMPLETED', limitedAi: false, label: 'Niepełne porównanie' },
    { scenario: 'AI limitations with complete file coverage', coverage: true, status: 'COMPLETED_WITH_LIMITATIONS', limitedAi: true, label: 'Analiza z ograniczeniami' },
    { scenario: 'ongoing analysis after complete deterministic comparison', coverage: true, status: 'RUNNING', limitedAi: false, label: 'Analiza w toku' },
    { scenario: 'complete comparison without limitations', coverage: true, status: 'COMPLETED', limitedAi: false, label: '' }
  ])('should distinguish $scenario in the compact notice', ({ coverage, status, limitedAi, label }) => {
    const comparison = basicResult();
    comparison.status = limitedAi ? 'INCOMPLETE' : 'NO_BLOCKING_ANOMALIES';
    comparison.deterministicResult.status = 'NO_BLOCKING_ANOMALIES';
    comparison.deterministicResult.findings = [];
    if (!coverage) comparison.deterministicResult.sourceCoverage = null;
    if (limitedAi) {
      comparison.mode = 'DEEP';
      comparison.aiSecondOpinion = { ...result().aiSecondOpinion!, visibilityLimits: ['Nie zweryfikowano wpływu na proces CRM.'] };
    }
    fixture.componentInstance.job.set(job({ status, result: comparison, report: null }));
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const notice = compiled.querySelector('.configuration-diff__header .comparison-notice')!;
    expect(notice.getAttribute('aria-label')).toBe(label || 'Szczegóły porównania');
    expect(notice.getAttribute('tabindex')).toBe('0');
    expect(notice.textContent?.replace('info', '').trim()).toBe(label);
    if (limitedAi) {
      expect(fixture.componentInstance.comparisonDetails()).toContain('test2: kompletne dane');
      expect(fixture.componentInstance.comparisonDetails()).toContain('Nie zweryfikowano wpływu na proces CRM.');
    }
  });

  it.each(['MISSING', 'AMBIGUOUS', 'TRUNCATED', 'ERROR', 'BRANCH_MISSING'])(
    'should preserve the incomplete warning when a required file is %s', (status) => {
      const comparison = basicResult();
      comparison.deterministicResult.targetCoverage!.files[2].status = status;
      fixture.componentInstance.job.set(job({ status: 'COMPLETED', result: comparison, report: null }));
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('.comparison-notice').textContent).toContain('Niepełne porównanie');
      expect(fixture.componentInstance.comparisonDetails()).toContain('test2: niepełne dane');
      expect(fixture.componentInstance.comparisonDetails()).toContain('crm/customer-service/application.yml.kv:');
    }
  );

  it.each([
    { branchExists: false, fileCount: 3, detail: 'branch nie istnieje' },
    { branchExists: true, fileCount: 0, detail: 'niepełne dane' },
    { branchExists: true, fileCount: 2, detail: 'niepełne dane' }
  ])('should reject incomplete coverage with branchExists=$branchExists and $fileCount files', ({ branchExists, fileCount, detail }) => {
    const comparison = basicResult();
    const coverage = comparison.deterministicResult.targetCoverage!;
    coverage.branchExists = branchExists;
    coverage.files = coverage.files.slice(0, fileCount);
    fixture.componentInstance.job.set(job({ status: 'COMPLETED', result: comparison, report: null }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.comparison-notice').textContent).toContain('Niepełne porównanie');
    expect(fixture.componentInstance.comparisonDetails()).toContain(`test2: ${detail}`);
  });

  it('should retain an incomplete deterministic result even when every file was read', () => {
    const comparison = basicResult();
    comparison.deterministicResult.status = 'INCOMPLETE';
    fixture.componentInstance.job.set(job({ status: 'COMPLETED_WITH_LIMITATIONS', result: comparison, report: null }));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.comparison-notice').textContent).toContain('Niepełne porównanie');
    expect(fixture.componentInstance.comparisonDetails()).toContain('dev1: kompletne dane');
    expect(fixture.componentInstance.comparisonDetails()).toContain('test2: kompletne dane');
  });

  it('should show the DEEP blocker and prevent starting the job', () => {
    api.getDeepPreflight.mockReturnValue(of(blockedPreflight()));
    fixture.componentInstance.modeControl.setValue('DEEP');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(api.getDeepPreflight).toHaveBeenCalledWith('runtime-config', 'backend', '');
    expect(compiled.textContent).toContain('Tryb DEEP jest zablokowany');
    expect(compiled.textContent).toContain('Brak code-search scope');
    expect(buttonContaining(compiled, 'Run verification')?.disabled).toBe(true);
  });

  it('should validate the branch pair and expose accessible mode state', () => {
    fixture.componentInstance.targetBranchControl.setValue('dev1');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Wybierz dwa różne środowiska');
    expect(buttonContaining(compiled, 'Run verification')?.disabled).toBe(true);
    expect(buttonContaining(compiled, 'Basic')?.getAttribute('aria-pressed')).toBe('true');
    expect(buttonContaining(compiled, 'Deep')?.getAttribute('aria-pressed')).toBe('false');
    expect(compiled.querySelector('select[aria-label="Filtr rodzaju zmiany"]')).toBeNull();
  });

  it('should block only DEEP runs when the PAT is missing', () => {
    fixture.componentInstance.githubAuthStatus.set(missingPatAuthStatus());
    fixture.detectChanges();

    let compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).not.toContain('Dodaj fine-grained PAT przed analizą');
    expect(buttonContaining(compiled, 'Run verification')?.disabled).toBe(false);

    fixture.componentInstance.modeControl.setValue('DEEP');
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Dodaj fine-grained PAT przed analizą');
    expect(buttonContaining(compiled, 'Run verification')?.disabled).toBe(true);
    buttonContaining(compiled, 'Workspace Settings')?.click();
    expect(githubAuth.openSettings).toHaveBeenCalledTimes(1);

    fixture.componentInstance.githubAuthStatus.set(localTokenAuthStatus());
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('GitHub Copilot gotowy');
  });

  it('should start DEEP verification and render partial deterministic and AI results', () => {
    (fixture.componentInstance as unknown as {
      deepModeSelectionDisabled: { set: (value: boolean) => void };
    }).deepModeSelectionDisabled.set(false);
    fixture.detectChanges();
    buttonContaining(fixture.nativeElement, 'Deep')?.click();
    fixture.componentInstance.codeRefControl.setValue('release/42');
    fixture.detectChanges();

    buttonContaining(fixture.nativeElement, 'Run verification')?.click();
    fixture.detectChanges();

    expect(api.startJob).toHaveBeenCalledWith({
      mode: 'DEEP',
      repositoryId: 'runtime-config',
      systemIds: ['backend', 'customer-profile', 'notifications'],
      sourceBranch: 'dev1',
      targetBranch: 'test2',
      codeRef: 'release/42',
      model: 'gpt-5.4',
      reasoningEffort: 'medium'
    });
    expect(polling.poll).toHaveBeenCalledTimes(1);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.configuration-diff__header')).not.toBeNull();
    expect(compiled.textContent).toContain('notifications.endpoint');
    expect(compiled.textContent).toContain('AI second opinion');
    expect(compiled.textContent).toContain('AI INTERPRETATION');
    expect(compiled.textContent).toContain('Raport operatora');
    expect(compiled.textContent).toContain('Functional impact');
    expect(compiled.textContent).toContain('Platform Team');
    expect(compiled.textContent).toContain('backend@release/42');
  });

  it('should keep deterministic facts usable when AI result is unavailable', () => {
    fixture.componentInstance.job.set(job({
      mode: 'BASIC',
      codeRef: null,
      aiModel: null,
      reasoningEffort: null,
      preparedPrompt: null,
      result: basicResult(),
      report: null
    }));
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.configuration-diff__header')).not.toBeNull();
    expect(compiled.textContent).toContain('Wymaga weryfikacji');
    expect(compiled.textContent).not.toContain('AI second opinion');
    expect(compiled.textContent).not.toContain('NOT_ASSESSED');
    expect(compiled.textContent).not.toContain('Systemy, kod i ownership');
  });

  it('should present parser root cause and hardcoded sensitive additions as critical findings', () => {
    const criticalResult = basicResult();
    criticalResult.deterministicResult.references = [{
      referenceId: 'reference-39', sourceRole: 'APPLICATION_YAML', documentIndex: 0,
      sourcePath: 'customer.parentId', targetPath: 'local.endpoints.crmRecords.customerRecordParentNodeId',
      referenceKind: 'SPRING_PLACEHOLDER', sourceStatus: 'RESOLVED', targetStatus: 'UNRESOLVED'
    }];
    criticalResult.deterministicResult.findings = [
      {
        findingId: 'finding-parser',
        code: 'TARGET_VAR_UNSUPPORTED_SYNTAX',
        severity: 'ERROR',
        path: 'local.endpoints.crmRecords.customerRecordParentNodeId',
        differenceIds: [],
        referenceIds: ['reference-39'],
        filePath: 'global.var',
        line: 196
      },
      {
        findingId: 'finding-secret',
        code: 'HARDCODED_SENSITIVE_VALUE_ADDED',
        severity: 'ERROR',
        path: 'spring.rabbitmq.password',
        differenceIds: ['difference-1'],
        referenceIds: []
      }
    ];
    fixture.componentInstance.job.set(job({
      mode: 'BASIC',
      status: 'COMPLETED_WITH_LIMITATIONS',
      result: criticalResult,
      report: null
    }));
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Analizator nie obsługuje tej składni');
    expect(compiled.textContent).toContain('Z tym samym problemem powiązano nieustaloną wartość zmiennej.');
    expect(compiled.textContent).toContain('global.var:196');
    expect(compiled.textContent).toContain('Dodano wrażliwą wartość bezpośrednio w konfiguracji');
    expect(compiled.textContent).toContain('Sprawdź ją i zastąp takim odwołaniem przed wdrożeniem.');
    expect(compiled.querySelector('.finding-severity')?.textContent).toBe('Błąd');
    const mainText = compiled.querySelector('.finding-list')!.textContent!;
    const shared = fixture.componentInstance.shareDocument()!;
    for (const view of fixture.componentInstance.findingViews()) {
      expect(mainText).toContain(view.title);
      expect(mainText).toContain(view.description);
      expect(shared.markdown).toContain(view.title);
      expect(shared.markdown).toContain(view.description);
      for (const location of view.locations) {
        expect(mainText).toContain(location);
        expect(shared.markdown).toContain(location);
      }
      expect(mainText).not.toContain(view.id);
      expect(mainText).not.toContain(view.code);
      expect(shared.markdown).not.toContain(view.code);
      expect(shared.markdownWithMeta).toContain(view.code);
    }
    expect(mainText).not.toContain('reference-39');
    buttonContaining(compiled, 'Pokaż powiązanie: customer.parentId')!.click();
    expect(fixture.componentInstance.focusedReferenceId()).toBe('reference-39');
  });

  it('should present and share readable notes from an existing report without mutating its stored sections', () => {
    const snapshot = job();
    const savedReport = snapshot.components[0].report!;
    savedReport.sections = [{
      id: 'deterministic-findings', title: 'Deterministic findings', order: 1,
      markdown: 'finding-1 · UNRESOLVED_REFERENCE', meta: savedReport.meta
    }];
    fixture.componentInstance.job.set(snapshot);
    fixture.detectChanges();

    const view = fixture.componentInstance.findingViews()[0];
    const report = fixture.componentInstance.presentationReport()!;
    expect(report.sections[0].title).toBe('Uwagi do porównania');
    expect(report.sections[0].markdown).toContain(view.title);
    expect(fixture.componentInstance.shareDocument()?.markdown).toContain(view.title);
    expect(fixture.componentInstance.shareDocument()?.markdown).not.toContain('UNRESOLVED_REFERENCE');
    expect(fixture.nativeElement.querySelector('app-analysis-report-panel').textContent).not.toContain('UNRESOLVED_REFERENCE');
    expect(savedReport.sections[0].markdown).toBe('finding-1 · UNRESOLVED_REFERENCE');
  });

  it('should expose unknown ownership and code-grounding navigation without the removed decision bar', () => {
    const base = result();
    const deep = base.deepAnalysis!;
    const opinion = base.aiSecondOpinion!;
    const disagreement: ConfigDriftViewerResult = {
      ...base,
      agreement: {
        status: 'DISAGREEMENT',
        explanation: 'AI wskazuje dodatkowe ryzyko.',
        alignedFindingIds: [],
        disputedFindingIds: ['finding-1']
      },
      aiSecondOpinion: {
        ...opinion,
        observations: [{
          ...opinion.observations[0]!,
          codeGroundingIds: ['grounding-1']
        }]
      },
      deepAnalysis: {
        ...deep,
        ownership: {
          ...deep.ownership!,
          primaryOwners: [],
          partnerOwners: [],
          handoffReason: 'Brak rozstrzygniętego zespołu.'
        }
      }
    };
    fixture.componentInstance.job.set(job({ result: disagreement }));
    fixture.detectChanges();

    let compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).not.toContain('DISAGREEMENT');
    expect(compiled.textContent).toContain('Ownership jest nieznany');
    expect(compiled.textContent).toContain('Deployed ref was not confirmed');
    buttonContaining(compiled, 'Code grounding · grounding-1')?.click();
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
    expect(fixture.componentInstance.focusedReferenceId()).toBe('grounding-1');
    expect(compiled.querySelector('#grounding-1')?.classList).toContain('reference-focused');
  });

  it('should stop on a polling error and allow an explicit retry', () => {
    polling.poll
      .mockReturnValueOnce(throwError(() => new HttpErrorResponse({
        status: 504,
        error: { message: 'Polling timeout.' }
      })))
      .mockReturnValueOnce(of(job()));

    buttonContaining(fixture.nativeElement, 'Run verification')?.click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Polling timeout');
    buttonContaining(fixture.nativeElement, 'Ponów odświeżanie')?.click();
    fixture.detectChanges();

    expect(polling.poll).toHaveBeenCalledTimes(2);
    expect(fixture.componentInstance.job()?.status).toBe('COMPLETED_WITH_LIMITATIONS');
  });

  it('should render file-oriented differences and navigate from AI to deterministic evidence', () => {
    fixture.componentInstance['job'].set(job());
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('backend/application.yml.kv');
    expect(compiled.textContent).toContain('backend/local.var');
    const inlineDiff = compiled.querySelector('.value-comparison--inline-diff');
    expect(inlineDiff?.textContent).toContain('source≠target');
    expect(inlineDiff?.querySelector('.inline-diff__removed')?.textContent).toBe('dev-a');
    expect(inlineDiff?.querySelector('.inline-diff__added')?.textContent).toBe('test2');
    expect(inlineDiff?.querySelector('code')?.getAttribute('aria-label')).toBe(
      'source "https://notifications.crm-dev-a.example.invalid", target "https://notifications.crm-test2.example.invalid"'
    );
    const booleanInlineDiff = Array.from(
      compiled.querySelectorAll<HTMLElement>('.value-comparison--inline-diff')
    ).find((diff) =>
      diff.querySelector('code')?.getAttribute('aria-label') === 'source false, target true'
    );
    expect(booleanInlineDiff?.textContent).toContain('source≠target');
    expect(booleanInlineDiff?.querySelector('.inline-diff__removed')?.textContent).toBe('false');
    expect(booleanInlineDiff?.querySelector('.inline-diff__added')?.textContent).toBe('true');
    const resolvedInlineDiff = compiled.querySelector('.effective-resolved-diff');
    expect(resolvedInlineDiff?.textContent).toContain('resolved');
    expect(resolvedInlineDiff?.textContent).toContain('source≠target');
    expect(resolvedInlineDiff?.querySelector('.inline-diff__removed')?.textContent).toBe('DEBUG');
    expect(resolvedInlineDiff?.querySelector('.inline-diff__added')?.textContent).toBe('INFO');
    expect(resolvedInlineDiff?.querySelector('code')?.getAttribute('aria-label')).toBe(
      'resolved source "DEBUG", target "INFO"'
    );
    expect(compiled.querySelector('select[aria-label="Filtr pliku"]')).toBeNull();

    const referenceButton = buttonContaining(compiled, 'Difference · difference-2');
    referenceButton?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.focusedReferenceId()).toBe('difference-2');
    expect(fixture.nativeElement.querySelector('#difference-2')?.closest('.configuration-row')?.classList)
      .toContain('reference-focused');
  });

  it('should pass imported JSON to the backend read-only import boundary', async () => {
    const file = new File(
      ['{"schema":"tdw.config-drift-viewer-export","version":1}'],
      'runtime-result.json',
      { type: 'application/json' }
    );
    Object.defineProperty(file, 'text', {
      configurable: true,
      value: () => Promise.resolve(
        '{"schema":"tdw.config-drift-viewer-export","version":1}'
      )
    });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: [file], configurable: true });

    await (fixture.componentInstance as unknown as {
      importResult: (event: Event) => Promise<void>;
    }).importResult({ target: input } as unknown as Event);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.importResult).toHaveBeenCalledWith({
      schema: 'tdw.config-drift-viewer-export',
      version: 1
    });
    expect(fixture.componentInstance.systemControl.value).toEqual(['backend']);
    expect(fixture.nativeElement.textContent).toContain('1 z 3 wybranych');
    expect(fixture.componentInstance.job()?.imported).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Import read-only');
  });

  it('restores a completed local comparison using file coverage from JSON without a synthetic complete field', async () => {
    const comparison = basicResult();
    comparison.deterministicResult.findings = [];
    comparison.visibilityLimits = [];
    const completed = job({ mode: 'BASIC', status: 'COMPLETED', result: comparison, report: null });
    const envelope = {
      schema: 'tdw.config-drift-viewer-export', version: 1,
      payload: { type: 'config-drift-viewer-analysis', resultContract: 'config-drift-viewer-result-v1', job: completed }
    };
    historyApi.getRun.mockReturnValue(of({
      analysisId: completed.jobId, feature: 'config-drift-viewer', exportEnvelope: envelope
    }));
    api.importResult.mockReturnValue(of(completed));

    routeParams.next(convertToParamMap({ localRunId: completed.jobId }));
    fixture.detectChanges();

    expect(api.importResult).toHaveBeenCalledWith(envelope);
    expect(api.startJob).not.toHaveBeenCalled();
    const header = fixture.nativeElement.querySelector('.configuration-diff__header');
    expect(header.textContent).toContain('Wymaga weryfikacji');
    expect(header.textContent).not.toContain('Niepełne porównanie');
    const tooltip = await TestbedHarnessEnvironment.loader(fixture).getHarness(
      MatTooltipHarness.with({ selector: '.comparison-notice' })
    );
    await tooltip.show();
    expect(await tooltip.getTooltipText()).toContain('dev1: kompletne dane. Pliki: 3.');
    expect(await tooltip.getTooltipText()).toContain('test2: kompletne dane. Pliki: 3.');
    await tooltip.hide();
    expect(fixture.componentInstance.shareDocument()?.markdown).toContain('dev1: Complete');
    expect(fixture.componentInstance.shareDocument()?.markdown).toContain('test2: Complete');
  });

  it('restores an active local run and resumes polling without importing a terminal result', () => {
    const active = job({ status: 'QUEUED', result: null });
    historyApi.getRun.mockReturnValue(of({
      analysisId: active.jobId,
      feature: 'config-drift-viewer',
      name: 'CRM runtime check',
      exportEnvelope: {
        schema: 'tdw.config-drift-viewer-export',
        version: 1,
        payload: {
          type: 'config-drift-viewer-analysis',
          resultContract: 'config-drift-viewer-result-v1',
          job: active
        }
      }
    }));

    routeParams.next(convertToParamMap({ localRunId: active.jobId }));

    expect(historyApi.getRun).toHaveBeenCalledWith(active.jobId);
    expect(api.importResult).not.toHaveBeenCalled();
    expect(polling.poll).toHaveBeenCalledTimes(1);
    expect(fixture.componentInstance.job()?.jobId).toBe(active.jobId);
  });
});

function inputOptions(): ConfigDriftViewerInputOptions {
  return {
    modes: ['BASIC', 'DEEP'],
    branches: ['dev1', 'test2', 'dev2'],
    repositories: [{ id: 'runtime-config', label: 'Runtime config' }],
    systems: [
      { id: 'backend', label: 'Backend', configurationDirectory: 'backend' },
      {
        id: 'customer-profile',
        label: 'Customer Profile',
        configurationDirectory: 'customer-profile'
      },
      {
        id: 'notifications',
        label: 'Notifications',
        configurationDirectory: 'notifications'
      }
    ]
  };
}

function aiOptions(): AnalysisAiModelOptionsResponse {
  return {
    defaultModel: 'gpt-5.4',
    defaultReasoningEffort: 'medium',
    defaultReasoningEfforts: ['medium'],
    models: [{
      id: 'gpt-5.4',
      name: 'GPT 5.4',
      supportsReasoningEffort: true,
      reasoningEfforts: ['low', 'medium'],
      defaultReasoningEffort: 'medium'
    }]
  };
}

function localTokenAuthStatus(): GitHubAuthStatus {
  return {
    configured: true,
    settingsUrl: '/workspace-settings'
  };
}

function missingPatAuthStatus(): GitHubAuthStatus {
  return {
    configured: false,
    settingsUrl: '/workspace-settings'
  };
}

function readyPreflight(): ConfigDriftViewerDeepPreflight {
  return {
    status: 'READY',
    repositoryId: 'runtime-config',
    systemId: 'backend',
    systemLabel: 'Backend',
    resolvedConfigurationDirectory: 'backend',
    repositories: [],
    blockers: [],
    visibilityLimits: [],
    ready: true
  };
}

function blockedPreflight(): ConfigDriftViewerDeepPreflight {
  return {
    ...readyPreflight(),
    status: 'BLOCKED',
    ready: false,
    blockers: [{ code: 'MISSING_SCOPE', message: 'Brak code-search scope.' }]
  };
}

function job(
  overrides: Partial<ConfigDriftViewerJobStateSnapshot> & {
    preparedPrompt?: string | null;
    result?: ConfigDriftViewerResult | null;
    report?: ConfigDriftViewerComponentRunSnapshot['report'];
  } = {}
): ConfigDriftViewerJobStateSnapshot {
  const {
    preparedPrompt = 'safe prompt',
    result: componentResult = result(),
    report = {
      reportId: 'runtime-report-1',
      header: 'Raport operatora',
      subHeader: 'Deterministic facts and AI interpretation',
      markdownSummary: 'Sprawdź zmianę przed wdrożeniem.',
      sections: [],
      meta: {
        references: [],
        visibilityLimits: [],
        openQuestions: [],
        gaps: [],
        confidence: 'MEDIUM',
        warnings: []
      }
    },
    ...jobOverrides
  } = overrides;
  const status = jobOverrides.status ?? 'COMPLETED_WITH_LIMITATIONS';
  const completedAt = jobOverrides.completedAt === undefined
    ? '2026-07-30T10:01:00Z'
    : jobOverrides.completedAt;
  const componentSteps = [
    {
      code: 'DIFF',
      label: 'Deterministic comparison',
      phase: 'CONFIGURATION',
      status: 'COMPLETED',
      message: 'Ready',
      itemCount: 2,
      startedAt: '2026-07-30T10:00:01Z',
      completedAt: '2026-07-30T10:00:02Z'
    }
  ];
  return {
    jobId: 'job-1',
    mode: 'DEEP',
    repositoryId: 'runtime-config',
    systemIds: ['backend'],
    sourceBranch: 'dev1',
    targetBranch: 'test2',
    codeRef: 'release/42',
    aiModel: 'gpt-5.4',
    reasoningEffort: 'medium',
    status,
    currentStepCode: null,
    currentStepLabel: null,
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-07-30T10:00:00Z',
    updatedAt: '2026-07-30T10:01:00Z',
    completedAt,
    steps: [],
    components: [{
      componentRunId: 'job-1:0',
      systemId: 'backend',
      systemLabel: 'Backend',
      configurationDirectory: 'backend',
      status,
      currentStepCode: null,
      currentStepLabel: null,
      errorCode: null,
      errorMessage: null,
      createdAt: '2026-07-30T10:00:00Z',
      updatedAt: '2026-07-30T10:01:00Z',
      completedAt,
      steps: componentSteps,
      contextSections: [],
      toolEvidenceSections: [],
      aiActivityEvents: [],
      preparedPrompt,
      result: componentResult,
      report
    }],
    imported: false,
    ...jobOverrides
  };
}

function completeCoverage(branch: string): ConfigDriftViewerBranchCoverage {
  return {
    branch,
    branchExists: true,
    files: [
      { role: 'GLOBAL_VAR', path: 'global.var' },
      { role: 'LOCAL_VAR', path: 'crm/customer-service/local.var' },
      { role: 'APPLICATION_YAML', path: 'crm/customer-service/application.yml.kv' }
    ].map(file => ({
      ...file, status: 'AVAILABLE', commitId: 'crm-revision', lastCommitId: 'crm-revision',
      lastModifiedAt: '2026-07-30T10:00:00Z', sizeBytes: 128, errorCode: null
    }))
  };
}

function result(): ConfigDriftViewerResult {
  return {
    status: 'INCOMPLETE',
    mode: 'DEEP',
    deterministicResult: {
      repositoryId: 'runtime-config',
      systemId: 'backend',
      systemLabel: 'Backend',
      configurationDirectory: 'backend',
      sourceBranch: 'dev1',
      targetBranch: 'test2',
      status: 'REVIEW_REQUIRED',
      sourceCoverage: completeCoverage('dev1'),
      targetCoverage: completeCoverage('test2'),
      documents: [],
      references: [],
      differences: [
        {
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
        },
        {
          differenceId: 'difference-2',
          role: 'LOCAL_VAR',
          documentIndex: 0,
          path: 'feature.enabled',
          kind: 'CHANGED',
          sourceType: 'BOOLEAN',
          targetType: 'BOOLEAN',
          sensitivity: 'PUBLIC',
          sourceValueToken: 'value-3',
          targetValueToken: 'value-4'
        }
      ],
      findings: [{
        findingId: 'finding-1',
        code: 'UNRESOLVED_REFERENCE',
        severity: 'ERROR',
        path: 'notifications.endpoint',
        differenceIds: ['difference-1'],
        referenceIds: []
      }]
    },
    configurationDiff: configurationDiffProjection(),
    configurationDiffAnnotations: [
      {
        sourceId: 'observation-1',
        kind: 'OBSERVATION',
        comment: 'Zmiana może przełączyć integrację.',
        confidence: null,
        hypothesis: false,
        differenceIds: ['difference-2'],
        findingIds: []
      },
      {
        sourceId: 'impact-1',
        kind: 'FUNCTIONAL_IMPACT',
        comment: 'Wysyłka powiadomień: możliwa zmiana systemu docelowego.',
        confidence: 'MEDIUM',
        hypothesis: false,
        differenceIds: ['difference-1'],
        findingIds: ['finding-1']
      }
    ],
    aiSecondOpinion: {
      executionStatus: 'COMPLETED',
      conclusion: 'REVIEW_REQUIRED',
      confidence: 'MEDIUM',
      summary: 'Zmiana endpointu wymaga potwierdzenia.',
      observations: [{
        observationId: 'observation-1',
        type: 'GROUNDED_OBSERVATION',
        summary: 'Endpoint changed',
        explanation: 'Zmiana może przełączyć integrację.',
        differenceIds: ['difference-2'],
        findingIds: [],
        contextIds: [],
        codeGroundingIds: ['grounding-1']
      }],
      recommendedHumanChecks: ['Potwierdź endpoint z właścicielem integracji.'],
      functionalImpacts: [{
        impactId: 'impact-1',
        affectedFunctionality: 'Wysyłka powiadomień',
        impact: 'Możliwa zmiana systemu docelowego.',
        confidence: 'MEDIUM',
        hypothesis: false,
        systemIds: ['backend'],
        differenceIds: ['difference-1'],
        findingIds: ['finding-1'],
        contextIds: ['system:backend'],
        codeGroundingIds: ['grounding-1']
      }],
      visibilityLimits: []
    },
    agreement: {
      status: 'AGREEMENT',
      explanation: 'AI potwierdza finding deterministyczny.',
      alignedFindingIds: ['finding-1'],
      disputedFindingIds: []
    },
    deepAnalysis: {
      status: 'PARTIAL',
      primarySystem: {
        systemId: 'backend',
        label: 'Backend',
        kind: 'internal-service',
        resolvedConfigurationDirectory: 'backend',
        configurationDirectoryResolution: 'runtime/deployment signal',
        codeSearchScopeIds: ['backend-code']
      },
      affectedSystems: [],
      integrations: [],
      processes: [],
      boundedContexts: [],
      codeGrounding: [{
        groundingId: 'grounding-1',
        scopeId: 'backend-code',
        repositoryId: 'backend',
        projectPath: 'platform/backend',
        usedRef: 'release/42',
        filePath: 'src/Notifications.java',
        lineNumber: 42,
        symbol: 'Notifications',
        matchedPropertyPath: 'notifications.endpoint',
        differenceId: 'difference-1',
        usageKind: 'VALUE_INJECTION',
        confidence: 'HIGH'
      }],
      ownership: {
        situationType: 'inside-system',
        primaryOwners: [{
          targetType: 'system',
          targetId: 'backend',
          targetLabel: 'Backend',
          ownerTeamIds: ['platform-team'],
          ownerLabel: 'Platform Team',
          source: 'explicit-ownership',
          confidence: 'high'
        }],
        partnerOwners: [],
        resolutionPath: ['system:backend'],
        handoffReason: 'Skontaktuj się z Platform Team.',
        visibilityLimits: []
      },
      visibilityLimits: ['Deployed ref was not confirmed.']
    },
    visibilityLimits: ['Deployed ref was not confirmed.'],
    prompt: 'safe prompt',
    usage: {
      inputTokens: 1200,
      outputTokens: 300,
      cacheReadTokens: 0,
      cacheWriteTokens: 0,
      totalTokens: 1500,
      aiCredits: 0,
      apiDurationMs: 4200,
      apiCallCount: 1,
      model: 'gpt-5.4',
      contextTokenLimit: null,
      contextCurrentTokens: null,
      contextMessages: null, reasoningTokens: null
    }
  };
}

function basicResult(): ConfigDriftViewerResult {
  return {
    ...result(),
    status: 'REVIEW_REQUIRED',
    mode: 'BASIC',
    configurationDiff: configurationDiffProjection(),
    configurationDiffAnnotations: [],
    aiSecondOpinion: null,
    agreement: null,
    deepAnalysis: null,
    visibilityLimits: [],
    prompt: null,
    usage: null
  };
}

function configurationDiffProjection(): ConfigDriftViewerDiffProjection {
  const absent = {
    presence: 'ABSENT' as const,
    type: null,
    value: null,
    cardinality: null
  };
  return {
    sourceBranch: 'dev1',
    targetBranch: 'test2',
    files: [
      {
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
          sourceProfile: absent,
          targetProfile: absent,
          root: {
            name: 'document-0',
            path: '',
            changeKind: 'UNCHANGED',
            source: { presence: 'PRESENT', type: 'MAP', value: null, cardinality: 1 },
            target: { presence: 'PRESENT', type: 'MAP', value: null, cardinality: 1 },
            differenceIds: [],
            children: [{
              name: 'notifications',
              path: 'notifications',
              changeKind: 'UNCHANGED',
              source: { presence: 'PRESENT', type: 'MAP', value: null, cardinality: 1 },
              target: { presence: 'PRESENT', type: 'MAP', value: null, cardinality: 1 },
              differenceIds: [],
              children: [
                {
                  name: 'endpoint',
                  path: 'notifications.endpoint',
                  changeKind: 'CHANGED',
                  source: {
                    presence: 'PRESENT',
                    type: 'STRING',
                    value: 'https://notifications.crm-dev-a.example.invalid',
                    cardinality: null
                  },
                  target: {
                    presence: 'PRESENT',
                    type: 'STRING',
                    value: 'https://notifications.crm-test2.example.invalid',
                    cardinality: null
                  },
                  differenceIds: ['difference-1'],
                  children: []
                },
                {
                  name: 'level',
                  path: 'notifications.level',
                  changeKind: 'EFFECTIVE_CHANGED',
                  source: {
                    presence: 'PRESENT',
                    type: 'STRING',
                    value: '${local.notificationLevel}',
                    cardinality: null
                  },
                  target: {
                    presence: 'PRESENT',
                    type: 'STRING',
                    value: '${local.notificationLevel}',
                    cardinality: null
                  },
                  sourceEffective: {
                    presence: 'PRESENT',
                    type: 'STRING',
                    value: 'DEBUG',
                    cardinality: null
                  },
                  targetEffective: {
                    presence: 'PRESENT',
                    type: 'STRING',
                    value: 'INFO',
                    cardinality: null
                  },
                  differenceIds: ['difference-3'],
                  children: []
                }
              ]
            }]
          }
        }]
      },
      {
        role: 'LOCAL_VAR',
        format: 'VAR',
        sourcePath: 'backend/local.var',
        targetPath: 'backend/local.var',
        sourcePresent: true,
        targetPresent: true,
        documents: [{
          documentIndex: 0,
          sourcePresent: true,
          targetPresent: true,
          sourceProfile: absent,
          targetProfile: absent,
          root: {
            name: 'document-0',
            path: '',
            changeKind: 'UNCHANGED',
            source: { presence: 'PRESENT', type: 'MAP', value: null, cardinality: 1 },
            target: { presence: 'PRESENT', type: 'MAP', value: null, cardinality: 1 },
            differenceIds: [],
            children: [{
              name: 'feature',
              path: 'feature',
              changeKind: 'UNCHANGED',
              source: { presence: 'PRESENT', type: 'MAP', value: null, cardinality: 1 },
              target: { presence: 'PRESENT', type: 'MAP', value: null, cardinality: 1 },
              differenceIds: [],
              children: [{
                name: 'enabled',
                path: 'feature.enabled',
                changeKind: 'CHANGED',
                source: {
                  presence: 'PRESENT',
                  type: 'BOOLEAN',
                  value: false,
                  cardinality: null
                },
                target: {
                  presence: 'PRESENT',
                  type: 'BOOLEAN',
                  value: true,
                  cardinality: null
                },
                differenceIds: ['difference-2'],
                children: []
              }]
            }]
          }
        }]
      }
    ]
  };
}

function buttonContaining(root: HTMLElement, text: string): HTMLButtonElement | null {
  return Array.from(root.querySelectorAll<HTMLButtonElement>('button'))
    .find((button) => button.textContent?.includes(text)) ?? null;
}

function labelContaining(root: HTMLElement, text: string): HTMLLabelElement | null {
  return Array.from(root.querySelectorAll<HTMLLabelElement>('label'))
    .find((label) => label.textContent?.includes(text)) ?? null;
}
