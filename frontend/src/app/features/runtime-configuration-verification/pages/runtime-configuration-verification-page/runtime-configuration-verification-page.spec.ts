import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';

import {
  AnalysisAiModelOptionsResponse,
  GitHubAuthStatus
} from '../../../../core/models/analysis.models';
import { AiOptionsApiService } from '../../../../core/services/ai-options-api.service';
import { AnalysisJobPollingService } from '../../../../core/services/analysis-job-polling.service';
import { AnalysisRunHistoryApiService } from '../../../../core/services/analysis-run-history-api.service';
import { GithubAuthService } from '../../../../core/services/github-auth.service';
import {
  RuntimeConfigurationDeepPreflight,
  RuntimeConfigurationVerificationInputOptions,
  RuntimeConfigurationVerificationJobStateSnapshot,
  RuntimeConfigurationVerificationResult
} from '../../models/runtime-configuration-verification.models';
import { RuntimeConfigurationVerificationApiService } from '../../services/runtime-configuration-verification-api.service';
import { RuntimeConfigurationVerificationPageComponent } from './runtime-configuration-verification-page';

describe('RuntimeConfigurationVerificationPageComponent', () => {
  let fixture: ComponentFixture<RuntimeConfigurationVerificationPageComponent>;
  let api: {
    getInputOptions: ReturnType<typeof vi.fn>;
    getDeepPreflight: ReturnType<typeof vi.fn>;
    startJob: ReturnType<typeof vi.fn>;
    getJob: ReturnType<typeof vi.fn>;
    importResult: ReturnType<typeof vi.fn>;
  };
  let polling: { poll: ReturnType<typeof vi.fn> };
  let githubAuth: {
    getStatus: ReturnType<typeof vi.fn>;
    connect: ReturnType<typeof vi.fn>;
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
    githubAuth = {
      getStatus: vi.fn(() => of(localTokenAuthStatus())),
      connect: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [RuntimeConfigurationVerificationPageComponent],
      providers: [
        { provide: RuntimeConfigurationVerificationApiService, useValue: api },
        { provide: AiOptionsApiService, useValue: { getOptions: () => of(aiOptions()) } },
        { provide: AnalysisJobPollingService, useValue: polling },
        { provide: GithubAuthService, useValue: githubAuth },
        {
          provide: AnalysisRunHistoryApiService,
          useValue: { getRun: () => of({ feature: 'runtime-configuration-verification' }) }
        },
        {
          provide: ActivatedRoute,
          useValue: { queryParamMap: of(convertToParamMap({})) }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(RuntimeConfigurationVerificationPageComponent);
    fixture.detectChanges();
  });

  it('should render the BASIC form from backend options without calling DEEP capabilities', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const runButton = buttonContaining(compiled, 'Run verification');

    expect(compiled.textContent).toContain('Porównaj konfigurację środowisk');
    expect(compiled.textContent).toContain('Backend · backend');
    expect(compiled.textContent).toContain('Gotowe do porównania konfiguracji');
    expect(runButton?.disabled).toBe(false);
    expect(api.getDeepPreflight).not.toHaveBeenCalled();
  });

  it('should show the DEEP blocker and prevent starting the job', () => {
    api.getDeepPreflight.mockReturnValue(of(blockedPreflight()));
    buttonContaining(fixture.nativeElement, 'Deep')?.click();
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

  it('should block the run and offer GitHub connection or reauthentication', () => {
    fixture.componentInstance.githubAuthStatus.set(githubAppAuthStatus(false, false));
    fixture.detectChanges();

    let compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Połącz GitHub przed analizą');
    expect(buttonContaining(compiled, 'Run verification')?.disabled).toBe(true);
    buttonContaining(compiled, 'Połącz GitHub')?.click();
    expect(githubAuth.connect).toHaveBeenCalledTimes(1);

    fixture.componentInstance.githubAuthStatus.set(githubAppAuthStatus(false, true));
    fixture.detectChanges();
    compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Wymagane ponowne połączenie GitHub');
    expect(compiled.textContent).toContain('Połącz ponownie GitHub');
  });

  it('should start DEEP verification and render partial deterministic and AI results', () => {
    buttonContaining(fixture.nativeElement, 'Deep')?.click();
    fixture.componentInstance.codeRefControl.setValue('release/42');
    fixture.detectChanges();

    buttonContaining(fixture.nativeElement, 'Run verification')?.click();
    fixture.detectChanges();

    expect(api.startJob).toHaveBeenCalledWith({
      mode: 'DEEP',
      repositoryId: 'runtime-config',
      systemId: 'backend',
      sourceBranch: 'dev1',
      targetBranch: 'zt001',
      codeRef: 'release/42',
      model: 'gpt-5.4',
      reasoningEffort: 'medium'
    });
    expect(polling.poll).toHaveBeenCalledTimes(1);

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('INCOMPLETE');
    expect(compiled.textContent).toContain('Configuration result');
    expect(compiled.textContent).toContain('notifications.endpoint');
    expect(compiled.textContent).toContain('AI second opinion');
    expect(compiled.textContent).toContain('AI INTERPRETATION');
    expect(compiled.textContent).toContain('Raport operatora');
    expect(compiled.textContent).toContain('1 wywołań AI');
    expect(compiled.textContent).toContain('Functional impact');
    expect(compiled.textContent).toContain('Platform Team');
    expect(compiled.textContent).toContain('backend@release/42');
  });

  it('should keep deterministic facts usable when AI result is unavailable', () => {
    const deterministicOnly: RuntimeConfigurationVerificationResult = {
      ...result(),
      mode: 'BASIC',
      aiSecondOpinion: null,
      agreement: null,
      deepAnalysis: null
    };
    fixture.componentInstance.job.set(job({
      mode: 'BASIC',
      codeRef: null,
      result: deterministicOnly,
      report: null
    }));
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Configuration result');
    expect(compiled.textContent).toContain(
      'AI second opinion nie jest dostępne. Wynik deterministyczny pozostaje ważny.'
    );
    expect(compiled.textContent).toContain('NOT_ASSESSED');
    expect(compiled.textContent).not.toContain('Systemy, kod i ownership');
  });

  it('should expose disagreement, unknown ownership and code-grounding navigation', () => {
    const base = result();
    const deep = base.deepAnalysis!;
    const opinion = base.aiSecondOpinion!;
    const disagreement: RuntimeConfigurationVerificationResult = {
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
    expect(compiled.textContent).toContain('DISAGREEMENT');
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

  it('should filter differences and navigate from an AI reference to deterministic evidence', () => {
    fixture.componentInstance['job'].set(job());
    fixture.detectChanges();

    const fileFilter = fixture.nativeElement.querySelector(
      'select[aria-label="Filtr pliku"]'
    ) as HTMLSelectElement;
    fileFilter.value = 'LOCAL_VAR';
    fileFilter.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    const differenceTable = fixture.nativeElement.querySelector('.difference-table') as HTMLElement;
    expect(differenceTable.textContent).not.toContain('notifications.endpoint');
    expect(differenceTable.textContent).toContain('feature.enabled');

    const referenceButton = buttonContaining(fixture.nativeElement, 'difference-2');
    referenceButton?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.focusedReferenceId()).toBe('difference-2');
    expect(fixture.nativeElement.querySelector('#difference-2')?.classList)
      .toContain('reference-focused');
  });

  it('should pass imported JSON to the backend read-only import boundary', async () => {
    const file = new File(
      ['{"schema":"tdw.runtime-configuration-verification-export","version":1}'],
      'runtime-result.json',
      { type: 'application/json' }
    );
    Object.defineProperty(file, 'text', {
      configurable: true,
      value: () => Promise.resolve(
        '{"schema":"tdw.runtime-configuration-verification-export","version":1}'
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
      schema: 'tdw.runtime-configuration-verification-export',
      version: 1
    });
    expect(fixture.componentInstance.job()?.imported).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Import read-only');
  });
});

function inputOptions(): RuntimeConfigurationVerificationInputOptions {
  return {
    modes: ['BASIC', 'DEEP'],
    branches: ['dev1', 'zt001', 'dev2'],
    repositories: [{ id: 'runtime-config', label: 'Runtime config' }],
    systems: [{ id: 'backend', label: 'Backend', configurationDirectory: 'backend' }]
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
    mode: 'LOCAL_TOKEN',
    required: false,
    connected: false,
    githubLogin: null,
    displayName: null,
    tokenExpiresAt: null,
    reauthRequired: false,
    authStartUrl: null
  };
}

function githubAppAuthStatus(connected: boolean, reauthRequired: boolean): GitHubAuthStatus {
  return {
    mode: 'GITHUB_APP',
    required: true,
    connected,
    githubLogin: connected ? 'operator' : null,
    displayName: null,
    tokenExpiresAt: null,
    reauthRequired,
    authStartUrl: '/api/auth/github/start'
  };
}

function readyPreflight(): RuntimeConfigurationDeepPreflight {
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

function blockedPreflight(): RuntimeConfigurationDeepPreflight {
  return {
    ...readyPreflight(),
    status: 'BLOCKED',
    ready: false,
    blockers: [{ code: 'MISSING_SCOPE', message: 'Brak code-search scope.' }]
  };
}

function job(
  overrides: Partial<RuntimeConfigurationVerificationJobStateSnapshot> = {}
): RuntimeConfigurationVerificationJobStateSnapshot {
  return {
    jobId: 'job-1',
    mode: 'DEEP',
    repositoryId: 'runtime-config',
    systemId: 'backend',
    sourceBranch: 'dev1',
    targetBranch: 'zt001',
    codeRef: 'release/42',
    aiModel: 'gpt-5.4',
    reasoningEffort: 'medium',
    status: 'COMPLETED_WITH_LIMITATIONS',
    currentStepCode: null,
    currentStepLabel: null,
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-07-30T10:00:00Z',
    updatedAt: '2026-07-30T10:01:00Z',
    completedAt: '2026-07-30T10:01:00Z',
    steps: [
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
    ],
    contextSections: [],
    toolEvidenceSections: [],
    aiActivityEvents: [],
    preparedPrompt: 'safe prompt',
    result: result(),
    report: {
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
    imported: false,
    ...overrides
  };
}

function result(): RuntimeConfigurationVerificationResult {
  return {
    status: 'INCOMPLETE',
    mode: 'DEEP',
    deterministicResult: {
      repositoryId: 'runtime-config',
      systemId: 'backend',
      systemLabel: 'Backend',
      configurationDirectory: 'backend',
      sourceBranch: 'dev1',
      targetBranch: 'zt001',
      status: 'REVIEW_REQUIRED',
      sourceCoverage: { branch: 'dev1', branchExists: true, files: [], complete: true },
      targetCoverage: { branch: 'zt001', branchExists: true, files: [], complete: true },
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
        code: 'WRONG_ENVIRONMENT_MARKER',
        severity: 'HIGH',
        path: 'notifications.endpoint',
        differenceIds: ['difference-1'],
        referenceIds: []
      }]
    },
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
        kind: 'internal-system',
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
      cost: 0,
      apiDurationMs: 4200,
      apiCallCount: 1,
      model: 'gpt-5.4',
      contextTokenLimit: null,
      contextCurrentTokens: null,
      contextMessages: null
    }
  };
}

function buttonContaining(root: HTMLElement, text: string): HTMLButtonElement | null {
  return Array.from(root.querySelectorAll<HTMLButtonElement>('button'))
    .find((button) => button.textContent?.includes(text)) ?? null;
}
