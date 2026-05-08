import { HttpErrorResponse } from '@angular/common/http';
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';

import {
  AnalysisAiModelOptionsResponse,
  AnalysisJobStateSnapshot,
  GitHubAuthStatus
} from '../../core/models/analysis.models';
import { AnalysisApiService } from '../../core/services/analysis-api.service';
import { GithubAuthService } from '../../core/services/github-auth.service';
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
    expect(fixture.nativeElement.textContent).toContain('Domyślny backend (gpt-5.4)');
  });

  it('should start analysis without GitHub tokens or OAuth fields', async () => {
    const { fixture, analysisApi } = await createComponent(connectedStatus());
    const component = fixture.componentInstance;

    component.correlationIdControl.setValue('corr-123');
    component.submit(new Event('submit'));

    expect(analysisApi.startAnalysis).toHaveBeenCalledWith({
      correlationId: 'corr-123',
      model: undefined,
      reasoningEffort: undefined
    });
    const startAnalysisCalls = analysisApi.startAnalysis.mock.calls as unknown as Array<[unknown]>;
    expect(JSON.stringify(startAnalysisCalls[0][0])).not.toContain('token');
    expect(JSON.stringify(startAnalysisCalls[0][0])).not.toContain('githubAuthCode');
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
    component.chatMessageControl.setValue('Sprawdź jeszcze repozytorium.');
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

    component.submitChat(new Event('submit'));
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

  it('should expose copy actions for every chat message', async () => {
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
    expect(copyButtons).toHaveLength(2);
    expect(copyButtons[0].textContent).toContain('Kopiuj');
    expect(copyButtons[1].textContent).toContain('Kopiuj');
  });

  async function createComponent(
    status: GitHubAuthStatus,
    aiModelOptions$: Observable<AnalysisAiModelOptionsResponse> = of(modelOptions())
  ) {
    const analysisApi = {
      getAiModelOptions: vi.fn(() => aiModelOptions$),
      startAnalysis: vi.fn(() => of(queuedJob())),
      getAnalysis: vi.fn(() => of(queuedJob())),
      sendChatMessage: vi.fn(() => of(queuedJob()))
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
        { provide: AnalysisApiService, useValue: analysisApi },
        { provide: GithubAuthService, useValue: githubAuth }
      ]
    }).compileComponents();

    return {
      fixture: TestBed.createComponent(AnalysisConsoleComponent),
      analysisApi,
      githubAuth
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
      summary: 'Diagnoza testowa.',
      detectedProblem: 'DOWNSTREAM_TIMEOUT',
      recommendedAction: 'Sprawdź timeout.',
      rationale: 'Evidence wskazuje na timeout.',
      affectedFunction: 'Catalog client',
      affectedProcess: 'Billing',
      affectedBoundedContext: 'Billing Context',
      affectedTeam: 'Billing Team',
      prompt: 'Prepared prompt without tokens.',
      usage: null
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
        prompt: ''
      }
    ]
  };
}
