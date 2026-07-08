import { HttpErrorResponse } from '@angular/common/http';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';

import {
  AnalysisAiModelOptionsResponse,
  AnalysisJobInputOptionsResponse,
  AnalysisJobStateSnapshot,
  AnalysisReport,
  GitHubAuthStatus,
  LocalAnalysisRunDetailResponse
} from '../../core/models/analysis.models';
import { AnalysisApiService } from '../../core/services/analysis-api.service';
import { AnalysisRunHistoryApiService } from '../../core/services/analysis-run-history-api.service';
import { GithubAuthService } from '../../core/services/github-auth.service';
import { buildExportEnvelope } from '../../core/utils/analysis-import-export.utils';
import { AnalysisConsoleComponent } from './analysis-console';

describe('AnalysisConsoleComponent auth flow', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should show local token badge and load AI model options', async () => {
    const { fixture, analysisApi } = await createComponent(localStatus());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Copilot: token lokalny');
    expect(analysisApi.getAiModelOptions).toHaveBeenCalledTimes(1);
  });

  it('should not show a ready kicker before analysis starts', async () => {
    const { fixture } = await createComponent(localStatus());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('Tutaj pojawi się przebieg analizy.');
    expect(compiled.textContent).not.toContain('Gotowe');
    expect(compiled.querySelector('.analysis-placeholder-workspace .placeholder-kicker')).toBeNull();
  });

  it('should show GitHub connect CTA and skip model options when disconnected', async () => {
    const { fixture, analysisApi } = await createComponent(disconnectedStatus());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Połącz konto GitHub, aby uruchomić analizę AI przez Copilot.'
    );
    expect(fixture.nativeElement.textContent).toContain('Połącz GitHub');
    expect(analysisApi.getAiModelOptions).not.toHaveBeenCalled();
  });

  it('should show connected GitHub login and load AI model options', async () => {
    const { fixture, analysisApi } = await createComponent(connectedStatus());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('GitHub: octocat');
    expect(fixture.nativeElement.textContent).toContain(
      'Zużycie Copilot będzie przypisane do tego konta GitHub.'
    );
    expect(analysisApi.getAiModelOptions).toHaveBeenCalledTimes(1);
  });

  it('should show loaders while AI model options are loading', async () => {
    const aiModelOptions = new Subject<AnalysisAiModelOptionsResponse>();
    const { fixture } = await createComponent(localStatus(), aiModelOptions.asObservable());

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ładowanie modeli AI...');
    expect(fixture.nativeElement.textContent).toContain('Ładowanie reasoning effort...');
    expect(fixture.nativeElement.querySelectorAll('.select-loader')).toHaveLength(2);

    aiModelOptions.next(modelOptions());
    aiModelOptions.complete();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Ładowanie modeli AI...');
    expect(fixture.nativeElement.textContent).not.toContain('Ładowanie reasoning effort...');
    expect(fixture.nativeElement.querySelectorAll('.select-loader')).toHaveLength(0);
    expect(fixture.componentInstance.aiModelControl.value).toBe('');
    expect(fixture.componentInstance.reasoningEffortControl.value).toBe('medium');
    expect(fixture.nativeElement.textContent).not.toContain('Domyślny backend (gpt-5.4)');
  });

  it('should start analysis without GitHub tokens or OAuth fields', async () => {
    const { fixture, analysisApi } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    component.correlationIdControl.setValue('corr-123');
    component.submit(new Event('submit'));

    expect(analysisApi.startAnalysis).toHaveBeenCalledWith({
      source: 'ELASTICSEARCH',
      correlationId: 'corr-123',
      model: undefined,
      reasoningEffort: 'medium'
    });
    const startAnalysisCalls = analysisApi.startAnalysis.mock.calls as unknown as Array<[unknown]>;
    expect(JSON.stringify(startAnalysisCalls[0][0])).not.toContain('token');
    expect(JSON.stringify(startAnalysisCalls[0][0])).not.toContain('githubAuthCode');
  });

  it('should submit listed backend defaults as regular selected AI options', async () => {
    const { fixture, analysisApi } = await createComponent(
      connectedStatus(),
      of(modelOptionsWithListedDefault())
    );
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const aiModelOptions = Array.from(
      fixture.nativeElement.querySelectorAll('#aiModel option')
    ) as HTMLOptionElement[];
    const reasoningOptions = Array.from(
      fixture.nativeElement.querySelectorAll('#reasoningEffort option')
    ) as HTMLOptionElement[];

    expect(component.aiModelControl.value).toBe('gpt-5.4');
    expect(component.reasoningEffortControl.value).toBe('high');
    expect(aiModelOptions.some((option) => option.value === '')).toBe(false);
    expect(reasoningOptions.filter((option) => option.value === 'high')).toHaveLength(1);
    expect(fixture.nativeElement.textContent).not.toContain('Domyślny backend (high)');

    component.correlationIdControl.setValue('corr-123');
    component.submit(new Event('submit'));

    expect(analysisApi.startAnalysis).toHaveBeenCalledWith({
      source: 'ELASTICSEARCH',
      correlationId: 'corr-123',
      model: 'gpt-5.4',
      reasoningEffort: 'high'
    });
  });

  it('should show only CSV upload when Elasticsearch input is unavailable', async () => {
    const { fixture } = await createComponent(
      connectedStatus(),
      of(modelOptions()),
      { inputOptions$: of(inputOptionsWithoutElastic()) }
    );

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const component = fixture.componentInstance;
    const compiled = fixture.nativeElement as HTMLElement;
    const correlationInput = compiled.querySelector('#correlationId') as HTMLInputElement | null;

    expect(component.correlationIdControl.disabled).toBe(true);
    expect(correlationInput).toBeNull();
    expect(compiled.querySelector('[role="group"][aria-label="Log source"]')).toBeNull();
    expect(compiled.textContent).not.toContain('Elasticsearch/Kibana nie jest skonfigurowany.');
    expect(compiled.textContent).toContain('CSV file');
    expect(compiled.textContent).toContain('Choose CSV');
  });

  it('should submit CSV upload as the analysis input source', async () => {
    const { fixture, analysisApi } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;
    const file = new File(['@timestamp,fields.correlationId\n2026-07-04T10:00:00Z,corr-123'], 'logi.csv', {
      type: 'text/csv'
    });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [file]
    });

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    component.onLogFileSelected({ target: input } as unknown as Event);
    component.submit(new Event('submit'));

    expect(analysisApi.startAnalysis).toHaveBeenCalledWith({
      source: 'CSV_UPLOAD',
      logFile: file,
      model: undefined,
      reasoningEffort: 'medium'
    });
  });

  it('should render CSV validation errors next to the start form', async () => {
    const { fixture, analysisApi } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;
    const file = new File(['not,a,kibana,csv'], 'bad.csv', { type: 'text/csv' });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [file]
    });
    analysisApi.startAnalysis.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({
        status: 400,
        error: {
          code: 'INCIDENT_LOG_FILE_MISSING_COLUMNS',
          message: 'CSV log file is missing required columns.',
          fieldErrors: []
        }
      }))
    );

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    component.onLogFileSelected({ target: input } as unknown as Event);
    component.submit(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.transportError()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain(
      'CSV log file is missing required columns.'
    );
  });

  it('should switch the route to the local run immediately after starting analysis', async () => {
    const { fixture, router } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    component.correlationIdControl.setValue('corr-123');
    component.submit(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();

    expect(navigateSpy).toHaveBeenCalledWith([], {
      relativeTo: expect.anything(),
      queryParams: {
        localRunId: 'analysis-1',
        analysisId: null
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  });

  it('should hide form header after starting analysis', async () => {
    const { fixture } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.analysis-form__header')).not.toBeNull();

    component.correlationIdControl.setValue('corr-123');
    component.submit(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.analysis-form__header')).toBeNull();
  });

  it('should hide form header after importing analysis', async () => {
    const { fixture } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', {
      configurable: true,
      value: [
        {
          name: 'analysis.json',
          text: () =>
            Promise.resolve(
              JSON.stringify(buildExportEnvelope(completedJob(), '2026-05-02T10:05:00Z'))
            )
        }
      ]
    });

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.analysis-form__header')).not.toBeNull();

    await component.importAnalysis({ target: input } as unknown as Event);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.analysis-form__header')).toBeNull();
  });

  it('should load a local run from the analysis history route', async () => {
    const { fixture, historyApi } = await createComponent(
      connectedStatus(),
      of(modelOptions()),
      {
        localRunId: 'analysis-1',
        localRunDetail: localRunDetail()
      }
    );

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(historyApi.getRun).toHaveBeenCalledWith('analysis-1');
    expect(fixture.nativeElement.textContent).toContain('Analiza funkcjonalna procesu profilu klienta CRM.');
    expect(fixture.nativeElement.textContent).toContain('incident-report-1');
    expect(fixture.nativeElement.textContent).toContain('Technical handoff');
    expect(fixture.nativeElement.textContent).not.toContain('Lokalny run został otwarty z historii.');
  });

  it('should restore a live analysis from the analysis id route', async () => {
    const { fixture, analysisApi } = await createComponent(
      connectedStatus(),
      of(modelOptions()),
      {
        analysisId: 'analysis-1',
        liveJob: completedJob()
      }
    );

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(analysisApi.getAnalysis).toHaveBeenCalledWith('analysis-1');
    expect(fixture.componentInstance.correlationIdControl.value).toBe('corr-123');
    expect(fixture.nativeElement.textContent).toContain('Analiza funkcjonalna procesu profilu klienta CRM.');
  });

  it('should continue a local run through the analysis history API', async () => {
    const updatedDetail = localRunDetail(completedJobWithChat());
    const { fixture, analysisApi, historyApi } = await createComponent(
      connectedStatus(),
      of(modelOptions()),
      {
        localRunId: 'analysis-1',
        localRunDetail: localRunDetail(),
        localChatResponse: updatedDetail
      }
    );
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    component.submitChat('Kontynuuj lokalnie.');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(historyApi.sendChatMessage).toHaveBeenCalledWith('analysis-1', {
      message: 'Kontynuuj lokalnie.'
    });
    expect(analysisApi.sendChatMessage).not.toHaveBeenCalled();
    expect(component.job()?.chatMessages).toHaveLength(2);
    expect(component.job()?.chatMessages[1]?.content).toContain('timeout na downstream');
    expect(component.exportState()?.sourceEnvelope).toBe(updatedDetail.exportEnvelope);
  });

  it('should show a sent follow-up message before the live chat API responds', async () => {
    const chatResponse$ = new Subject<AnalysisJobStateSnapshot>();
    const { fixture, analysisApi } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;
    component.job.set(completedJob());
    component.exportState.set({
      origin: 'live',
      exportedAt: '',
      fileName: '',
      job: completedJob()
    });
    (component as unknown as { activeAnalysisId: string }).activeAnalysisId = 'analysis-1';
    analysisApi.sendChatMessage.mockReturnValueOnce(chatResponse$);

    component.submitChat('Sprawdź jeszcze repozytorium.');
    fixture.detectChanges();

    expect(component.job()?.chatMessages).toHaveLength(2);
    expect(component.job()?.chatMessages[0]?.role).toBe('USER');
    expect(component.job()?.chatMessages[0]?.content).toBe('Sprawdź jeszcze repozytorium.');
    expect(component.job()?.chatMessages[1]?.role).toBe('ASSISTANT');
    expect(component.job()?.chatMessages[1]?.status).toBe('IN_PROGRESS');
    expect(component.exportState()).toBeNull();

    chatResponse$.next(completedJobWithChat());
    chatResponse$.complete();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(component.job()?.chatMessages[1]?.content).toContain('timeout na downstream');
  });

  it('should expose detailed usage cost breakdown on the compact run context item', async () => {
    const { fixture } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;
    component.job.set(completedJobWithUsage());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const runContextItems = Array.from(
      fixture.nativeElement.querySelectorAll('.analysis-run-context__item')
    ) as HTMLElement[];
    const usageItem = runContextItems.find((item) => item.textContent?.includes('Usage'));
    const tooltip = usageItem?.getAttribute('aria-label') ?? '';

    expect(usageItem).not.toBeUndefined();
    expect(usageItem?.textContent).toContain('2 820 tokens');
    expect(usageItem?.classList.contains('analysis-run-context__item--with-tooltip')).toBe(true);
    expect(usageItem?.getAttribute('tabindex')).toBe('0');
    expect(tooltip).toContain('Szacowany koszt analizy AI');
    expect(tooltip).toContain('Nowy input: 1 800');
    expect(tooltip).toContain('Cache read: 300');
    expect(tooltip).toContain('Odpowiedź AI: 420');
    expect(tooltip).toContain('Model SDK: gpt-5.4');
  });

  it('should show auth CTA after auth-required job error', async () => {
    const { fixture, analysisApi } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;
    analysisApi.startAnalysis.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({
        status: 401,
        error: {
          code: 'GITHUB_COPILOT_AUTH_REQUIRED',
          message: 'Połącz konto GitHub, aby uruchomić analizę przez Copilot.',
          authStartUrl: '/api/auth/github/start',
          fieldErrors: []
        }
      }))
    );

    component.correlationIdControl.setValue('corr-123');
    component.submit(new Event('submit'));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Połącz konto GitHub, aby uruchomić analizę przez Copilot.'
    );
  });

  it('should show reconnect CTA after chat reauth error', async () => {
    const { fixture, analysisApi } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;
    component.job.set(completedJob());
    (component as unknown as { activeAnalysisId: string }).activeAnalysisId = 'analysis-1';
    analysisApi.sendChatMessage.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({
        status: 401,
        error: {
          code: 'GITHUB_COPILOT_REAUTH_REQUIRED',
          message: 'Połącz ponownie GitHub, aby kontynuować pracę z Copilot.',
          authStartUrl: '/api/auth/github/start',
          fieldErrors: []
        }
      }))
    );

    component.submitChat('Sprawdź jeszcze repozytorium.');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Połącz ponownie GitHub, aby kontynuować pracę z Copilot.'
    );
    expect(fixture.nativeElement.textContent).toContain('Połącz ponownie GitHub');
    const chatCalls = analysisApi.sendChatMessage.mock.calls as unknown as Array<[string, unknown]>;
    expect(JSON.stringify(chatCalls[0][1])).toBe(
      '{"message":"Sprawdź jeszcze repozytorium."}'
    );
  });

  it('should expose icon-only copy actions for completed chat messages', async () => {
    const { fixture } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;
    component.job.set(completedJobWithChat());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const copyButtons = fixture.nativeElement.querySelectorAll('.chat-message__copy-button');
    const chatPanel = fixture.nativeElement.querySelector(
      'details.analysis-chat'
    ) as HTMLDetailsElement | null;

    expect(chatPanel).not.toBeNull();
    expect(chatPanel?.open).toBe(true);
    expect(chatPanel?.querySelector('summary')?.textContent).toContain('Kontynuacja analizy');
    expect(chatPanel?.querySelector('summary')?.textContent).not.toContain('expand_more');
    expect(copyButtons).toHaveLength(2);
    expect(copyButtons[0].textContent).toContain('content_copy');
    expect(copyButtons[1].textContent).toContain('content_copy');
    expect(copyButtons[0].textContent).not.toContain('Kopiuj');
    expect(copyButtons[1].textContent).not.toContain('Kopiuj');
    expect(copyButtons[0].getAttribute('aria-label')).toContain('Kopiuj wiadomość');
    expect(fixture.nativeElement.querySelector('.chat-message__copy-loader')).toBeNull();
  });

  it('should render compact tool feedback on chat messages', async () => {
    const { fixture } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;
    const job = completedJobWithChat();
    job.chatMessages[1]!.toolFeedback = [
      {
        feedbackId: 'feedback-chat-1',
        targetToolName: 'db_find_tables',
        targetToolCallId: 'db-call-1',
        feedbackToolCallId: 'feedback-call-1',
        usefulness: 'not_useful',
        expectedDataReceived: 'no',
        issueCategory: 'wrong_scope',
        improvementArea: 'adapter_result',
        confidence: 'medium',
        summaryForOperator: 'Tool nie zwrócił tabel pasujących do pytania.',
        suggestedImprovement: 'Ulepszyć ranking tabel po aliasach aplikacji.',
        createdAt: '2026-05-02T10:04:00Z'
      }
    ];
    component.job.set(job);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Feedback jakości');
    expect(fixture.nativeElement.textContent).toContain('db_find_tables (db-call-1)');
    expect(fixture.nativeElement.textContent).toContain('Tool nie zwrócił tabel pasujących do pytania.');
  });

  it('should show a copy-sized loader instead of a copy action while the last AI message is pending', async () => {
    const { fixture } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;
    component.job.set(completedJobWithPendingChat());

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const copyButtons = fixture.nativeElement.querySelectorAll('.chat-message__copy-button');
    const pendingAssistant = fixture.nativeElement.querySelector(
      '.chat-message--assistant'
    ) as HTMLElement | null;
    const loader = pendingAssistant?.querySelector('.chat-message__copy-loader') as HTMLElement | null;

    expect(copyButtons).toHaveLength(1);
    expect(pendingAssistant?.textContent).toContain('AI analizuje polecenie');
    expect(loader).not.toBeNull();
    expect(loader?.getAttribute('aria-label')).toBe('AI przygotowuje odpowiedź');
  });

  async function createComponent(
    status: GitHubAuthStatus,
    aiModelOptions$: Observable<AnalysisAiModelOptionsResponse> = of(modelOptions()),
    routeOptions: {
      localRunId?: string;
      analysisId?: string;
      liveJob?: AnalysisJobStateSnapshot;
      localRunDetail?: LocalAnalysisRunDetailResponse;
      localChatResponse?: LocalAnalysisRunDetailResponse;
      inputOptions$?: Observable<AnalysisJobInputOptionsResponse>;
    } = {}
  ) {
    const analysisApi = {
      getInputOptions: vi.fn(() => routeOptions.inputOptions$ ?? of(inputOptions())),
      getAiModelOptions: vi.fn(() => aiModelOptions$),
      startAnalysis: vi.fn(() => of(queuedJob())),
      getAnalysis: vi.fn(() => of(routeOptions.liveJob ?? queuedJob())),
      sendChatMessage: vi.fn(() => of(queuedJob()))
    };
    const historyApi = {
      getRun: vi.fn(() => of(routeOptions.localRunDetail ?? localRunDetail())),
      sendChatMessage: vi.fn(() => of(routeOptions.localChatResponse ?? localRunDetail(completedJobWithChat())))
    };
    const githubAuth = {
      getStatus: vi.fn(() => of(status)),
      connect: vi.fn(),
      logout: vi.fn(() => of(undefined))
    };

    await TestBed.configureTestingModule({
      imports: [AnalysisConsoleComponent],
      providers: [
        provideAnimationsAsync('noop'),
        provideLocationMocks(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: of(
              convertToParamMap(
                routeOptions.localRunId
                  ? { localRunId: routeOptions.localRunId }
                  : routeOptions.analysisId
                    ? { analysisId: routeOptions.analysisId }
                    : {}
              )
            )
          }
        },
        { provide: AnalysisApiService, useValue: analysisApi },
        { provide: AnalysisRunHistoryApiService, useValue: historyApi },
        { provide: GithubAuthService, useValue: githubAuth }
      ]
    }).compileComponents();

    return {
      fixture: TestBed.createComponent(AnalysisConsoleComponent),
      analysisApi,
      historyApi,
      githubAuth,
      router: TestBed.inject(Router)
    };
  }
});

function localStatus(): GitHubAuthStatus {
  return {
    mode: 'LOCAL_TOKEN',
    required: false,
    connected: true,
    githubLogin: null,
    displayName: 'Local developer token',
    tokenExpiresAt: null,
    reauthRequired: false,
    authStartUrl: null
  };
}

function disconnectedStatus(): GitHubAuthStatus {
  return {
    mode: 'GITHUB_APP',
    required: true,
    connected: false,
    githubLogin: null,
    displayName: null,
    tokenExpiresAt: null,
    reauthRequired: true,
    authStartUrl: '/api/auth/github/start'
  };
}

function connectedStatus(): GitHubAuthStatus {
  return {
    mode: 'GITHUB_APP',
    required: true,
    connected: true,
    githubLogin: 'octocat',
    displayName: 'octocat',
    tokenExpiresAt: '2026-05-02T18:42:00Z',
    reauthRequired: false,
    authStartUrl: '/api/auth/github/start'
  };
}

function modelOptions(): AnalysisAiModelOptionsResponse {
  return {
    defaultModel: 'gpt-5.4',
    defaultReasoningEffort: 'medium',
    defaultReasoningEfforts: ['low', 'medium', 'high'],
    models: []
  };
}

function modelOptionsWithListedDefault(): AnalysisAiModelOptionsResponse {
  return {
    defaultModel: 'gpt-5.4',
    defaultReasoningEffort: 'high',
    defaultReasoningEfforts: ['low', 'medium', 'high'],
    models: [
      {
        id: 'gpt-5.4',
        name: 'GPT-5.4',
        supportsReasoningEffort: true,
        reasoningEfforts: ['low', 'medium', 'high'],
        defaultReasoningEffort: 'high'
      },
      {
        id: 'gpt-5.4-mini',
        name: 'GPT-5.4 mini',
        supportsReasoningEffort: true,
        reasoningEfforts: ['low', 'medium', 'high'],
        defaultReasoningEffort: 'medium'
      }
    ]
  };
}

function inputOptions(): AnalysisJobInputOptionsResponse {
  return {
    elasticsearch: {
      source: 'ELASTICSEARCH',
      enabled: true,
      disabledReason: null
    },
    csvUpload: {
      source: 'CSV_UPLOAD',
      enabled: true,
      disabledReason: null
    }
  };
}

function inputOptionsWithoutElastic(): AnalysisJobInputOptionsResponse {
  return {
    elasticsearch: {
      source: 'ELASTICSEARCH',
      enabled: false,
      disabledReason: 'Elasticsearch/Kibana nie jest skonfigurowany.'
    },
    csvUpload: {
      source: 'CSV_UPLOAD',
      enabled: true,
      disabledReason: null
    }
  };
}

function queuedJob(): AnalysisJobStateSnapshot {
  return {
    analysisId: 'analysis-1',
    correlationId: 'corr-123',
    aiModel: '',
    reasoningEffort: '',
    status: 'QUEUED',
    currentStepCode: 'ELASTICSEARCH_LOGS',
    currentStepLabel: 'Zbieranie logów z Elasticsearch',
    environment: '',
    gitLabBranch: '',
    errorCode: '',
    errorMessage: '',
    createdAt: '2026-05-02T10:00:00Z',
    updatedAt: '2026-05-02T10:00:00Z',
    completedAt: '',
    steps: [],
    evidenceSections: [],
    toolEvidenceSections: [],
    aiActivityEvents: [],
    toolFeedback: [],
    chatMessages: [],
    preparedPrompt: '',
    result: null
  };
}

function completedJob(): AnalysisJobStateSnapshot {
  return {
    ...queuedJob(),
    status: 'COMPLETED',
    currentStepCode: 'AI_ANALYSIS',
    currentStepLabel: 'Budowanie końcowej analizy AI',
    completedAt: '2026-05-02T10:01:00Z',
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
      prompt: 'Prepared prompt without tokens.',
      usage: null
    },
    report: incidentReport()
  };
}

function incidentReport(): AnalysisReport {
  return {
    reportId: 'incident-report-1',
    header: 'DOWNSTREAM_TIMEOUT',
    subHeader: 'CRM / CRM Customer Context',
    markdownSummary: 'Timeout downstream blokuje odczyt profilu klienta CRM.',
    sections: [
      {
        id: 'FUNCTIONAL_ANALYSIS',
        title: 'Functional analysis',
        order: 1,
        markdown: 'Analiza funkcjonalna procesu profilu klienta CRM.',
        meta: {
          references: [],
          visibilityLimits: [],
          openQuestions: [],
          gaps: [],
          confidence: 'medium',
          warnings: []
        }
      },
      {
        id: 'TECHNICAL_HANDOFF',
        title: 'Technical handoff',
        order: 2,
        markdown: 'Analiza techniczna timeoutu w komponencie profilu klienta CRM.',
        meta: {
          references: [
            {
              type: 'code',
              label: 'CustomerProfileClient.call',
              target: 'src/main/java/CustomerProfileClient.java:L42',
              description: 'Wywolanie downstream.'
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
  };
}

function localRunDetail(job: AnalysisJobStateSnapshot = completedJob()): LocalAnalysisRunDetailResponse {
  return {
    analysisId: 'analysis-1',
    feature: 'incident-analysis',
    name: 'CRM corr-123',
    status: job.status,
    createdAt: '2026-05-02T10:00:00Z',
    updatedAt: '2026-05-02T10:05:00Z',
    completedAt: '2026-05-02T10:05:00Z',
    exportEnvelope: buildExportEnvelope(job, '2026-05-02T10:05:00Z'),
    continuationEnabled: true
  };
}

function completedJobWithUsage(): AnalysisJobStateSnapshot {
  const job = completedJob();
  return {
    ...job,
    result: {
      ...job.result!,
      usage: {
        inputTokens: 2100,
        outputTokens: 420,
        cacheReadTokens: 300,
        cacheWriteTokens: 0,
        totalTokens: 2820,
        cost: 0.0123,
        apiDurationMs: 2430,
        apiCallCount: 2,
        model: 'gpt-5.4',
        contextTokenLimit: 128000,
        contextCurrentTokens: 9200,
        contextMessages: 6
      }
    }
  };
}

function completedJobWithChat(): AnalysisJobStateSnapshot {
  return {
    ...completedJob(),
    chatMessages: [
      {
        id: 'chat-user-1',
        role: 'USER',
        status: 'COMPLETED',
        content: 'Przygotuj opis do Jiry.',
        errorCode: '',
        errorMessage: '',
        createdAt: '2026-05-02T10:02:00Z',
        updatedAt: '2026-05-02T10:02:00Z',
        completedAt: '2026-05-02T10:02:00Z',
        toolEvidenceSections: [],
        aiActivityEvents: [],
        toolFeedback: [],
        prompt: ''
      },
      {
        id: 'chat-assistant-1',
        role: 'ASSISTANT',
        status: 'COMPLETED',
        content: '**Diagnoza:** timeout na downstream.',
        errorCode: '',
        errorMessage: '',
        createdAt: '2026-05-02T10:03:00Z',
        updatedAt: '2026-05-02T10:03:00Z',
        completedAt: '2026-05-02T10:03:00Z',
        toolEvidenceSections: [],
        aiActivityEvents: [],
        toolFeedback: [],
        prompt: ''
      }
    ]
  };
}

function completedJobWithPendingChat(): AnalysisJobStateSnapshot {
  return {
    ...completedJob(),
    chatMessages: [
      {
        id: 'chat-user-1',
        role: 'USER',
        status: 'COMPLETED',
        content: 'Przygotuj opis do Jiry.',
        errorCode: '',
        errorMessage: '',
        createdAt: '2026-05-02T10:02:00Z',
        updatedAt: '2026-05-02T10:02:00Z',
        completedAt: '2026-05-02T10:02:00Z',
        toolEvidenceSections: [],
        aiActivityEvents: [],
        toolFeedback: [],
        prompt: ''
      },
      {
        id: 'chat-assistant-1',
        role: 'ASSISTANT',
        status: 'IN_PROGRESS',
        content: '',
        errorCode: '',
        errorMessage: '',
        createdAt: '2026-05-02T10:03:00Z',
        updatedAt: '2026-05-02T10:03:00Z',
        completedAt: '',
        toolEvidenceSections: [],
        aiActivityEvents: [],
        toolFeedback: [],
        prompt: ''
      }
    ]
  };
}
