import { signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { of, Subject, throwError } from 'rxjs';

import { AppUiConfigService } from '../../../core/services/app-ui-config.service';
import { AnalysisJobPollingService } from '../../../core/services/analysis-job-polling.service';
import { OperationalContextAssistanceJob } from '../../models/operational-context-assistance.models';
import { OperationalContextAssistanceApiService } from '../../services/operational-context-assistance-api.service';
import { ContextAssistancePanelComponent } from './context-assistance-panel';

describe('ContextAssistancePanelComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient()] });
  });

  it('shows progress, Copilot activity, prompt and cost in the shared aside', async () => {
    const completed: OperationalContextAssistanceJob = {
      ...job('COMPLETED'),
      preparedPrompt: 'Sanitizowany prompt asysty',
      steps: [{ code: 'PREPARE_AI', label: 'Przygotuj asystę AI', phase: 'AI_PREPARATION',
        status: 'COMPLETED', message: 'Prompt gotowy.', itemCount: 1,
        startedAt: '2026-09-13T10:00:00Z', completedAt: '2026-09-13T10:00:01Z' }],
      usage: { inputTokens: 100, outputTokens: 20, cacheReadTokens: 0, cacheWriteTokens: 0,
        totalTokens: 120, cost: 0, apiDurationMs: 1000, apiCallCount: 1, model: 'gpt-5.4',
        contextTokenLimit: null, contextCurrentTokens: null, contextMessages: null }
    };
    const fixture = await initialJobFixture(completed);
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-analysis-feature-aside')).not.toBeNull();
    expect(compiled.querySelector('.assistance-run__aside-usage')?.textContent).toContain('120');
    expect(compiled.querySelector('.assistance-run__aside-usage')?.textContent).toContain('Koszt');
    expect(compiled.querySelector('app-analysis-feature-aside')?.textContent)
      .toContain('Inicjalny prompt asysty Operational Context');
  });

  it('keeps a restored history run read-only', async () => {
    const fixture = await initialJobFixture(job('COMPLETED'));
    fixture.componentRef.setInput('historyReadOnly', true);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.assistance-form')).toBeNull();
    expect(compiled.textContent).toContain('Zapis analizy z historii');
    expect(compiled.textContent).toContain('Nowa asysta na bieżącym katalogu');
    expect(compiled.querySelector('.assistance-proposal__actions')).toBeNull();
    expect(fixture.componentInstance.canPreview()).toBe(false);
  });

  it('shows questions and the missing-code limit for a blocked result without proposals', async () => {
    const blocked: OperationalContextAssistanceJob = {
      ...job('BLOCKED'), currentStepLabel: 'Przygotuj propozycje',
      errorMessage: 'Nie przygotowano propozycji, ponieważ nie odczytano kodu.',
      visibilityLimits: ['AI nie odczytało żadnego pliku kodu z wybranego projektu GitLab.'],
      draft: { proposals: [], questions: ['Który plik opisuje domenę?'], visibilityLimits: [] }
    };
    const fixture = await initialJobFixture(blocked);

    expect(fixture.nativeElement.textContent).toContain('Zablokowano');
    expect(fixture.nativeElement.textContent).toContain('Nie przygotowano propozycji');
    expect(fixture.nativeElement.textContent).toContain('Który plik opisuje domenę?');
    expect(fixture.nativeElement.textContent).toContain('AI nie odczytało żadnego pliku kodu');
    expect(fixture.componentInstance.canPreview()).toBe(false);
  });

  it('clears a terminal result when the operator selects another target', async () => {
    await TestBed.configureTestingModule({
      imports: [ContextAssistancePanelComponent],
      providers: [
        provideAnimationsAsync('noop'),
        { provide: OperationalContextAssistanceApiService, useValue: { start: vi.fn(), get: vi.fn() } },
        { provide: AnalysisJobPollingService, useValue: { poll: vi.fn() } },
        { provide: AppUiConfigService, useValue: { config: signal({ defaultBranch: '' }) } }
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(ContextAssistancePanelComponent);
    fixture.componentRef.setInput('prefill', { mode: 'CREATE_AREA' });
    fixture.componentRef.setInput('initialJob', job('COMPLETED'));
    const jobChanged = vi.fn();
    fixture.componentInstance.jobChanged.subscribe(jobChanged);
    fixture.detectChanges();
    const panel = fixture.componentInstance;
    panel.setConfirmed(0, 'name', true);

    fixture.componentRef.setInput('prefill', {
      mode: 'RESOLVE_FINDING',
      target: { kind: 'VALIDATION_FINDING', entityType: 'system', entityId: 'customer-api', id: 'missing-owner' },
      description: 'Wyjaśnij brak ownera'
    });
    fixture.detectChanges();

    expect(panel.job()).toBeNull();
    expect(jobChanged).toHaveBeenCalledWith(null);
    expect(panel.confirmationsByProposal()).toEqual({});
    expect(panel.descriptionControl.value).toBe('Wyjaśnij brak ownera');
    expect(fixture.nativeElement.textContent).toContain('missing-owner');
    expect(fixture.nativeElement.textContent).not.toContain('Propozycje do przeglądu');
  });

  it('keeps a running job attached to its original context when another target is selected', async () => {
    const pollingUpdates = new Subject<OperationalContextAssistanceJob>();
    await TestBed.configureTestingModule({
      imports: [ContextAssistancePanelComponent],
      providers: [
        provideAnimationsAsync('noop'),
        { provide: OperationalContextAssistanceApiService, useValue: { start: vi.fn(), get: vi.fn() } },
        { provide: AnalysisJobPollingService, useValue: { poll: vi.fn(() => pollingUpdates.asObservable()) } },
        { provide: AppUiConfigService, useValue: { config: signal({ defaultBranch: '' }) } }
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(ContextAssistancePanelComponent);
    fixture.componentRef.setInput('prefill', { mode: 'CREATE_AREA' });
    fixture.componentRef.setInput('initialJob', job('QUEUED'));
    fixture.detectChanges();
    const panel = fixture.componentInstance;
    panel.descriptionControl.setValue('Oryginalny obszar');

    fixture.componentRef.setInput('prefill', {
      mode: 'IMPROVE_ENTITY',
      target: { kind: 'ENTITY', entityType: 'system', entityId: 'customer-api' },
      description: 'Nowy cel'
    });
    fixture.detectChanges();

    expect(panel.activePrefill()).toEqual({ mode: 'CREATE_AREA' });
    expect(panel.descriptionControl.value).toBe('Oryginalny obszar');
    expect(panel.job()?.status).toBe('QUEUED');
    expect(panel.contextSwitchNotice()).toContain('Trwa poprzednia asysta');
    expect(fixture.nativeElement.textContent).toContain('Utwórz lub uzupełnij katalog');
    expect(fixture.nativeElement.textContent).not.toContain('Uzupełnij wpis');
  });

  it('starts operator-only assistance and renders a field diff with provenance and validation', async () => {
    const updates = new Subject<OperationalContextAssistanceJob>();
    const api = { start: vi.fn(() => of(job('QUEUED'))), get: vi.fn() };
    const polling = { poll: vi.fn(() => updates.asObservable()) };
    await TestBed.configureTestingModule({
      imports: [ContextAssistancePanelComponent],
      providers: [
        provideAnimationsAsync('noop'),
        { provide: OperationalContextAssistanceApiService, useValue: api },
        { provide: AnalysisJobPollingService, useValue: polling },
        { provide: AppUiConfigService, useValue: { config: signal({ defaultBranch: 'release/2026.09' }) } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(ContextAssistancePanelComponent);
    fixture.componentRef.setInput('prefill', { mode: 'CREATE_AREA' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#assistance-repository-usage')).toBeNull();
    fixture.componentInstance.descriptionControl.setValue('Customer API obsługuje profil klienta.');
    fixture.componentInstance.start();
    expect(api.start).toHaveBeenCalledWith({
      mode: 'CREATE_AREA', description: 'Customer API obsługuje profil klienta.'
    });

    updates.next(job('COMPLETED'));
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent || '';
    expect(text).toContain('Pełny diff przed/po');
    expect(text).toContain('Customer API');
    expect(text).toContain('Dlaczego i na jakiej podstawie?');
    expect(text).toContain('Nazwa pochodzi z opisu operatora.');
    expect(text).toContain('opis operatora');
    expect(text).toContain('operator:description');
    expect(text).toContain('Pytania do operatora');
    expect(text).toContain('Sprawdź cały zestaw');
    expect(fixture.nativeElement.querySelector('.assistance-change input[type="checkbox"]')).not.toBeNull();
    expect(fixture.componentInstance.canSave()).toBe(false);
  });

  it('requires a full GitLab URL and a branch when the project is entered manually', async () => {
    const api = { start: vi.fn(() => of(job('COMPLETED'))), get: vi.fn(),
      sourceOptions: vi.fn(() => of({ configuredBaseUrl: 'https://gitlab.example.com', configuredGroup: 'CRM', projects: [] })) };
    await TestBed.configureTestingModule({
      imports: [ContextAssistancePanelComponent],
      providers: [
        provideAnimationsAsync('noop'),
        { provide: OperationalContextAssistanceApiService, useValue: api },
        { provide: AnalysisJobPollingService, useValue: { poll: vi.fn() } },
        { provide: AppUiConfigService, useValue: { config: signal({ defaultBranch: '' }) } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(ContextAssistancePanelComponent);
    fixture.componentRef.setInput('prefill', { mode: 'CREATE_AREA' });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    (fixture.nativeElement.querySelector('#assistance-use-gitlab') as HTMLInputElement).click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Pełny adres URL projektu GitLab');
    expect(fixture.nativeElement.textContent).toContain('Podgrupy mogą być częścią adresu');
    expect(fixture.nativeElement.textContent).toContain('https://gitlab.example.com/CRM/podgrupa/projekt');
    component.descriptionControl.setValue('Opis obszaru');
    component.projectUrlControl.setValue('CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS');
    component.start();
    expect(api.start).not.toHaveBeenCalled();
    expect(component.error()).toContain('gałąź');

    component.useManualRef();
    expect(fixture.nativeElement.textContent).toContain('Gałąź');
    expect(fixture.nativeElement.textContent).not.toContain('Gałąź lub commit');
    component.refControl.setValue('release/2026.09');
    component.start();
    expect(api.start).not.toHaveBeenCalled();
    expect(component.error()).toContain('pełny adres URL');

    component.projectUrlControl.setValue('https://gitlab.example.com/CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS');
    component.refControl.setValue('1234567890abcdef1234567890abcdef12345678');
    component.start();
    expect(api.start).not.toHaveBeenCalled();
    expect(component.error()).toContain('Commit zostanie przypięty automatycznie');
    component.refControl.setValue('release/2026.09');
    component.start();
    expect(api.start).toHaveBeenCalledWith({
      mode: 'CREATE_AREA',
      description: 'Opis obszaru',
      gitLabSource: {
        projectUrl: 'https://gitlab.example.com/CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS',
        ref: 'release/2026.09'
      },
      repositoryFacts: { usage: 'UNKNOWN' }
    });

    api.start.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 400,
      error: { code: 'OPCTX_ASSISTANCE_INVALID_GITLAB_SOURCE',
        message: 'Adres projektu nie należy do skonfigurowanej grupy GitLab.' } })));
    component.start();
    expect(component.error()).toBe('Adres projektu nie należy do skonfigurowanej grupy GitLab.');
  });

  it('shows a nested catalogue project with its full path and sends its relative path', async () => {
    const api = { start: vi.fn(() => of(job('COMPLETED'))), get: vi.fn(),
      sourceBranches: vi.fn(() => of({ branches: [{ name: 'main', isDefault: true }], truncated: false, warnings: [] })),
      sourceOptions: vi.fn(() => of({ configuredBaseUrl: 'https://gitlab.example.com', configuredGroup: 'CRM',
        projects: [{ project: 'PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS', projectPath: 'CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS' }] })) };
    await TestBed.configureTestingModule({
      imports: [ContextAssistancePanelComponent],
      providers: [
        provideAnimationsAsync('noop'),
        { provide: OperationalContextAssistanceApiService, useValue: api },
        { provide: AnalysisJobPollingService, useValue: { poll: vi.fn() } },
        { provide: AppUiConfigService, useValue: { config: signal({ defaultBranch: 'main' }) } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(ContextAssistancePanelComponent);
    fixture.componentRef.setInput('prefill', { mode: 'CREATE_AREA' });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    (fixture.nativeElement.querySelector('#assistance-use-gitlab') as HTMLInputElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.sourceOptions).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.textContent).toContain('w grupie CRM');
    expect(fixture.nativeElement.querySelectorAll('#assistance-catalogue-project option').length).toBe(2);
    expect(fixture.nativeElement.textContent).toContain('CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS');
    component.descriptionControl.setValue('Opis obszaru');
    component.catalogueProjectControl.setValue('PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS');
    fixture.detectChanges();
    expect(api.sourceBranches).toHaveBeenCalledWith({ project: 'PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS' }, '');
    expect(component.refControl.value).toBe('main');
    component.start();
    expect(api.start).toHaveBeenCalledWith({ mode: 'CREATE_AREA', description: 'Opis obszaru',
      gitLabSource: { project: 'PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS', ref: 'main' },
      repositoryFacts: { usage: 'UNKNOWN' } });
  });

  it('loads branches for a pasted project URL and clears a ref from the previous project', async () => {
    const api = { start: vi.fn(() => of(job('COMPLETED'))), get: vi.fn(),
      sourceOptions: vi.fn(() => of({ configuredBaseUrl: 'https://gitlab.example.com', configuredGroup: 'CRM', projects: [] })),
      sourceBranches: vi.fn(() => of({ branches: [{ name: 'main', isDefault: true }], truncated: false, warnings: [] })) };
    await TestBed.configureTestingModule({
      imports: [ContextAssistancePanelComponent],
      providers: [
        provideAnimationsAsync('noop'),
        { provide: OperationalContextAssistanceApiService, useValue: api },
        { provide: AnalysisJobPollingService, useValue: { poll: vi.fn() } }
      ]
    }).compileComponents();
    const fixture = TestBed.createComponent(ContextAssistancePanelComponent);
    fixture.componentRef.setInput('prefill', { mode: 'CREATE_AREA' });
    fixture.detectChanges();
    (fixture.nativeElement.querySelector('#assistance-use-gitlab') as HTMLInputElement).click();
    await fixture.whenStable();
    fixture.detectChanges();

    const panel = fixture.componentInstance;
    panel.projectUrlControl.setValue('https://gitlab.example.com/CRM/PROCESSES/first');
    await new Promise((resolve) => setTimeout(resolve, 380));
    fixture.detectChanges();
    expect(api.sourceBranches).toHaveBeenCalledWith({ projectUrl: 'https://gitlab.example.com/CRM/PROCESSES/first' }, '');
    expect(panel.refControl.value).toBe('main');

    panel.projectUrlControl.setValue('https://gitlab.example.com/CRM/PROCESSES/second');
    expect(panel.refControl.value).toBe('');
    await new Promise((resolve) => setTimeout(resolve, 380));
    fixture.detectChanges();
    expect(api.sourceBranches).toHaveBeenCalledWith({ projectUrl: 'https://gitlab.example.com/CRM/PROCESSES/second' }, '');
    expect(panel.refControl.value).toBe('main');
  });

  it('blocks GitLab selection when the server URL is missing and still allows an operator-only run', async () => {
    const api = { start: vi.fn(() => of(job('COMPLETED'))), get: vi.fn(),
      sourceOptions: vi.fn(() => of({ configuredBaseUrl: null, configuredGroup: 'CRM', projects: [] })) };
    await TestBed.configureTestingModule({
      imports: [ContextAssistancePanelComponent],
      providers: [
        provideAnimationsAsync('noop'),
        { provide: OperationalContextAssistanceApiService, useValue: api },
        { provide: AnalysisJobPollingService, useValue: { poll: vi.fn() } },
        { provide: AppUiConfigService, useValue: { config: signal({ defaultBranch: 'main' }) } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(ContextAssistancePanelComponent);
    fixture.componentRef.setInput('prefill', { mode: 'CREATE_AREA' });
    fixture.detectChanges();
    const checkbox = fixture.nativeElement.querySelector('#assistance-use-gitlab') as HTMLInputElement;
    checkbox.click();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('nie ma skonfigurowanego adresu serwera');

    fixture.componentInstance.descriptionControl.setValue('Opis obszaru');
    fixture.componentInstance.start();
    expect(api.start).not.toHaveBeenCalled();

    checkbox.click();
    fixture.componentInstance.start();
    expect(api.start).toHaveBeenCalledWith({ mode: 'CREATE_AREA', description: 'Opis obszaru' });
  });

  it('sends a new deployed system name and only an explicitly supplied runtime service name', async () => {
    const { fixture, api } = await onboardingFixture();
    const panel = fixture.componentInstance;
    panel.repositoryUsageControl.setValue('DEPLOYED_SYSTEM');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#assistance-system-name')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('#assistance-existing-system')).toBeNull();
    panel.systemNameControl.setValue('Obsługa profili klientów');
    panel.runtimeServiceNameControl.setValue('customer-profile-service');
    panel.start();
    expect(api.start).toHaveBeenCalledWith(expect.objectContaining({ repositoryFacts: {
      usage: 'DEPLOYED_SYSTEM', systemName: 'Obsługa profili klientów', runtimeServiceName: 'customer-profile-service'
    } }));

    panel.repositoryUsageControl.setValue('UNKNOWN');
    expect(panel.systemNameControl.value).toBe('');
    expect(panel.runtimeServiceNameControl.value).toBe('');
    panel.start();
    expect(api.start).toHaveBeenLastCalledWith(expect.objectContaining({ repositoryFacts: { usage: 'UNKNOWN' } }));
  });

  it('requires a catalog system for existing code and sends only selected library consumers', async () => {
    const systems = [{ id: 'customer-profile', label: 'Profile klientów' }, { id: 'customer-support', label: 'Wsparcie klienta' }];
    const { fixture, api } = await onboardingFixture(systems);
    const panel = fixture.componentInstance;
    panel.repositoryUsageControl.setValue('EXISTING_SYSTEM');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#assistance-existing-system')).not.toBeNull();
    panel.start();
    expect(api.start).not.toHaveBeenCalled();
    expect(panel.error()).toContain('Wybierz istniejący system');
    panel.existingSystemControl.setValue('customer-profile');
    panel.start();
    expect(api.start).toHaveBeenCalledWith(expect.objectContaining({ repositoryFacts: {
      usage: 'EXISTING_SYSTEM', systemIds: ['customer-profile']
    } }));

    panel.repositoryUsageControl.setValue('SHARED_LIBRARY');
    fixture.detectChanges();
    expect(panel.existingSystemControl.value).toBe('');
    expect(fixture.nativeElement.querySelector('#assistance-existing-system')).toBeNull();
    panel.setLibrarySystemSelected('customer-profile', true);
    panel.setLibrarySystemSelected('customer-support', true);
    panel.start();
    expect(api.start).toHaveBeenLastCalledWith(expect.objectContaining({ repositoryFacts: {
      usage: 'SHARED_LIBRARY', systemIds: ['customer-profile', 'customer-support']
    } }));

    panel.includeGitLabControl.setValue(false);
    expect(panel.repositoryUsageControl.value).toBe('UNKNOWN');
    expect(panel.librarySystemIds()).toEqual([]);
    panel.start();
    expect(api.start).toHaveBeenLastCalledWith({ mode: 'CREATE_AREA', description: 'Opis projektu' });
  });

  it('allows library onboarding with an empty system catalog and hides role facts outside CREATE_AREA', async () => {
    const { fixture, api } = await onboardingFixture();
    const panel = fixture.componentInstance;
    panel.repositoryUsageControl.setValue('SHARED_LIBRARY');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('W katalogu nie ma jeszcze systemów do powiązania');
    panel.start();
    expect(api.start).toHaveBeenCalledWith(expect.objectContaining({ repositoryFacts: { usage: 'SHARED_LIBRARY' } }));

    fixture.componentRef.setInput('prefill', {
      mode: 'IMPROVE_ENTITY', target: { kind: 'ENTITY', entityType: 'repository', entityId: 'library-repo' }
    });
    fixture.detectChanges();
    expect(panel.repositoryUsageControl.value).toBe('UNKNOWN');
    expect(fixture.nativeElement.querySelector('#assistance-repository-usage')).toBeNull();
    panel.descriptionControl.setValue('Uzupełnij opis');
    panel.start();
    expect(api.start).toHaveBeenLastCalledWith({
      mode: 'IMPROVE_ENTITY', description: 'Uzupełnij opis',
      target: { kind: 'ENTITY', entityType: 'repository', entityId: 'library-repo' },
      gitLabSource: { projectUrl: 'https://gitlab.example.com/CRM/library-repo', ref: 'main' }
    });
  });

  it('limits a library to five explicitly chosen consuming systems', async () => {
    const systems = Array.from({ length: 6 }, (_, index) => ({ id: `system-${index + 1}`, label: `System ${index + 1}` }));
    const { fixture, api } = await onboardingFixture(systems);
    const panel = fixture.componentInstance;
    panel.repositoryUsageControl.setValue('SHARED_LIBRARY');
    for (const system of systems.slice(0, 5)) panel.setLibrarySystemSelected(system.id, true);
    fixture.detectChanges();
    const choices = fixture.nativeElement.querySelectorAll('.assistance-form__system-list input[type="checkbox"]') as NodeListOf<HTMLInputElement>;
    expect(choices.length).toBe(6);
    expect(choices[5].disabled).toBe(true);
    panel.setLibrarySystemSelected(systems[5].id, true);
    expect(panel.librarySystemIds()).toHaveLength(5);
    panel.start();
    expect(api.start).toHaveBeenCalledWith(expect.objectContaining({ repositoryFacts: {
      usage: 'SHARED_LIBRARY', systemIds: systems.slice(0, 5).map((system) => system.id)
    } }));
  });

  it('lets the operator retry GET after polling fails without creating a duplicate job', async () => {
    const api = { start: vi.fn(() => of(job('QUEUED'))), get: vi.fn() };
    const polling = { poll: vi.fn()
      .mockReturnValueOnce(throwError(() => new Error('network')))
      .mockReturnValueOnce(of(job('COMPLETED'))) };
    await TestBed.configureTestingModule({
      imports: [ContextAssistancePanelComponent],
      providers: [
        provideAnimationsAsync('noop'),
        { provide: OperationalContextAssistanceApiService, useValue: api },
        { provide: AnalysisJobPollingService, useValue: polling },
        { provide: AppUiConfigService, useValue: { config: signal({ defaultBranch: '' }) } }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(ContextAssistancePanelComponent);
    fixture.componentRef.setInput('prefill', { mode: 'CREATE_AREA' });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.descriptionControl.setValue('Opis obszaru');
    component.start();
    fixture.detectChanges();
    expect(component.job()?.status).toBe('QUEUED');
    expect(component.pollError()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Ponów odczyt joba');

    component.retryPolling();
    fixture.detectChanges();
    expect(component.job()?.status).toBe('COMPLETED');
    expect(component.pollError()).toBe(false);
    expect(api.start).toHaveBeenCalledTimes(1);
    expect(polling.poll).toHaveBeenCalledTimes(2);
  });

  it('previews independent proposals together and writes the chosen set once', async () => {
    const original = job('COMPLETED');
    original.draft!.proposals.push({
      operation: 'CREATE', entityType: 'repository', entityId: 'customer-api-repository',
      changes: [{ path: 'name', after: 'Customer API repository', basis: 'SOURCE_OBSERVATION',
        reason: 'Nazwa repozytorium pochodzi z GitLab.', sourceRefs: ['gitlab:README.md'],
        confidence: 'MEDIUM', requiresConfirmation: false }],
      confidence: 'MEDIUM', requiresConfirmation: false, questions: [], visibilityLimits: []
    });
    const preview = {
      expectedDigest: 'old-digest', candidateDigest: 'candidate-digest', valid: true,
      entities: [{ type: 'repository', id: 'customer-api-repository', sourceFile: 'repo-map.yml',
        payload: { id: 'customer-api-repository', name: 'Customer API repository' } }], violations: []
    };
    const updated = { ...original, proposalDecisions: [
      { proposalIndex: 0, action: 'SKIP' as const, selectedPaths: [], completedAt: '2026-09-13T10:05:00Z' },
      { proposalIndex: 1, action: 'APPLY' as const, selectedPaths: ['name'], completedAt: '2026-09-13T10:05:00Z' }
    ] };
    const api = { start: vi.fn(), get: vi.fn(), previewBatch: vi.fn(() => of(preview)),
      decideBatch: vi.fn(() => of(updated)) };
    const fixture = await batchFixture(original, api);
    const panel = fixture.componentInstance;
    const refreshed = vi.fn();
    panel.catalogChanged.subscribe(refreshed);
    panel.setProposalSelected(0, original.draft!.proposals[0], false);
    fixture.detectChanges();
    expect(panel.decisionLabel(0)).toBe('Pominięto w wyborze');
    expect(panel.selectedDiff()).toEqual([{ entity: 'repository/customer-api-repository',
      path: 'name', before: undefined, after: 'Customer API repository' }]);
    expect(fixture.nativeElement.textContent).toContain('Pełny diff przed/po');
    expect(panel.canSave()).toBe(false);

    panel.previewSelection();
    fixture.detectChanges();
    expect(api.previewBatch).toHaveBeenCalledWith('job-1', { decisions: [
      { action: 'SKIP', selectedPaths: [], confirmedPaths: [] },
      { action: 'APPLY', selectedPaths: ['name'], confirmedPaths: [] }
    ] });
    expect(fixture.nativeElement.textContent).toContain('Cały zestaw jest poprawny');
    expect(fixture.nativeElement.textContent).toContain('repo-map.yml');
    expect(panel.canSave()).toBe(true);
    panel.saveBatch();
    fixture.detectChanges();
    expect(api.decideBatch).toHaveBeenCalledTimes(1);
    expect(api.decideBatch).toHaveBeenCalledWith('job-1', { decisions: [
      { action: 'SKIP', selectedPaths: [], confirmedPaths: [] },
      { action: 'APPLY', selectedPaths: ['name'], confirmedPaths: [] }
    ], candidateDigest: 'candidate-digest' });
    expect(refreshed).toHaveBeenCalledTimes(1);
    expect(refreshed).toHaveBeenCalledWith({ type: 'repository', id: 'customer-api-repository' });
    expect(fixture.nativeElement.textContent).toContain('Cały zestaw został rozstrzygnięty');
  });

  it('reviews one proposal at a time and saves an explicitly confirmed manual correction', async () => {
    const original = job('COMPLETED');
    original.draft!.proposals[0].requiresConfirmation = false;
    original.draft!.proposals.push({
      operation: 'CREATE', entityType: 'glossary-term', entityId: 'customer-profile',
      changes: [{ path: 'term', after: 'Profil klienta', basis: 'USER_STATEMENT',
        reason: 'Termin pochodzi z opisu.', sourceRefs: ['operator:description'],
        confidence: 'MEDIUM', requiresConfirmation: false }],
      confidence: 'MEDIUM', requiresConfirmation: false, questions: [], visibilityLimits: []
    });
    const preview = { expectedDigest: 'old', candidateDigest: 'corrected', valid: true,
      entities: [], violations: [] };
    const updated = { ...original, proposalDecisions: [
      { proposalIndex: 0, action: 'APPLY' as const, selectedPaths: ['name'],
        editedValues: { name: 'Customer API v2' }, completedAt: '2026-09-13T10:05:00Z' },
      { proposalIndex: 1, action: 'SKIP' as const, selectedPaths: [],
        completedAt: '2026-09-13T10:05:00Z' }
    ] };
    const api = { start: vi.fn(), get: vi.fn(), previewBatch: vi.fn(() => of(preview)),
      decideBatch: vi.fn(() => of(updated)) };
    const fixture = await batchFixture(original, api);
    const panel = fixture.componentInstance;
    const proposals = original.draft!.proposals;
    const name = proposals[0].changes[0];
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('.assistance-review__item')).toHaveLength(2);
    expect(compiled.querySelectorAll('.assistance-proposal')).toHaveLength(1);
    expect(compiled.querySelector('.assistance-form')).toBeNull();

    panel.selectProposal(1);
    fixture.detectChanges();
    expect(compiled.querySelector('.assistance-proposal')?.textContent).toContain('Profil klienta');
    panel.selectProposal(0);
    panel.setProposalSelected(1, proposals[1], false);
    panel.beginFieldEdit(0, proposals[0], name);
    panel.editValueControl.setValue('Customer API v2');
    panel.saveFieldEdit(0, proposals[0], name);
    fixture.detectChanges();
    expect(panel.effectiveAfter(0, name)).toBe('Customer API v2');
    expect(compiled.textContent).toContain('Poprawione ręcznie');
    expect(panel.reviewDecisions()[0].editedValues).toEqual({ name: 'Customer API v2' });
    expect(panel.canSave()).toBe(false);

    panel.previewSelection();
    expect(panel.canSave()).toBe(false);
    panel.setConfirmed(0, 'name', true);
    expect(panel.batchPreview()).toBeNull();
    panel.previewSelection();
    fixture.detectChanges();
    expect(api.previewBatch).toHaveBeenLastCalledWith('job-1', { decisions: [
      { action: 'APPLY', selectedPaths: ['name'], confirmedPaths: ['name'],
        editedValues: { name: 'Customer API v2' } },
      { action: 'SKIP', selectedPaths: [], confirmedPaths: [] }
    ] });
    expect(panel.canSave()).toBe(true);
    panel.saveBatch();
    fixture.detectChanges();
    expect(api.decideBatch).toHaveBeenCalledWith('job-1', {
      decisions: [
        { action: 'APPLY', selectedPaths: ['name'], confirmedPaths: ['name'],
          editedValues: { name: 'Customer API v2' } },
        { action: 'SKIP', selectedPaths: [], confirmedPaths: [] }
      ], candidateDigest: 'corrected'
    });
    expect(panel.effectiveAfter(0, name)).toBe('Customer API v2');
  });

  it('requires valid JSON with the original collection type for a structured correction', async () => {
    const original = job('COMPLETED');
    const proposal = original.draft!.proposals[0];
    const references = { path: 'canonicalReferences', after: ['system/customer-api'],
      basis: 'USER_STATEMENT' as const, reason: 'Operator wskazał system.',
      sourceRefs: ['operator:description'], confidence: 'MEDIUM' as const,
      requiresConfirmation: false };
    proposal.changes.push(references);
    const fixture = await batchFixture(original, { start: vi.fn(), get: vi.fn() });
    const panel = fixture.componentInstance;
    panel.beginFieldEdit(0, proposal, references);
    panel.editValueControl.setValue('not json');
    panel.saveFieldEdit(0, proposal, references);
    expect(panel.editError()).toContain('poprawny JSON');
    panel.editValueControl.setValue('{"system":"customer-api"}');
    panel.saveFieldEdit(0, proposal, references);
    expect(panel.editError()).toContain('listę lub obiekt');
    panel.editValueControl.setValue('["system/customer-api", "system/support-api"]');
    panel.saveFieldEdit(0, proposal, references);
    expect(panel.effectiveAfter(0, references)).toEqual(['system/customer-api', 'system/support-api']);
    panel.resetFieldEdit(0, references.path);
    expect(panel.effectiveAfter(0, references)).toEqual(['system/customer-api']);
  });

  it('requires current whole-batch preview and confirmations before saving', async () => {
    const original = job('COMPLETED');
    const proposal = original.draft!.proposals[0];
    const valid = { expectedDigest: 'old', candidateDigest: 'candidate', valid: true, entities: [], violations: [] };
    const api = { start: vi.fn(), get: vi.fn(), previewBatch: vi.fn(() => of(valid)), decideBatch: vi.fn() };
    const fixture = await batchFixture(original, api);
    const panel = fixture.componentInstance;
    panel.previewSelection();
    expect(panel.canSave()).toBe(false);
    panel.setConfirmed(0, 'name', true);
    expect(panel.batchPreview()).toBeNull();
    panel.previewSelection();
    expect(panel.canSave()).toBe(true);
    panel.setSelected(0, proposal, 'name', false);
    expect(panel.canSave()).toBe(false);
    expect(panel.batchPreview()).toBeNull();
    panel.previewSelection();
    expect(panel.canSave()).toBe(true);
    expect(api.previewBatch).toHaveBeenLastCalledWith('job-1', { decisions: [
      { action: 'SKIP', selectedPaths: [], confirmedPaths: [] }
    ] });
    expect(api.decideBatch).not.toHaveBeenCalled();
  });

  it('accepts a valid combined preview even when the old single-proposal preview was deferred', async () => {
    const original = job('COMPLETED');
    original.draft!.proposals[0].requiresConfirmation = false;
    original.draft!.proposals.push({
      operation: 'CREATE', entityType: 'repository', entityId: 'customer-api-repository',
      changes: [{ path: 'references.systems', after: ['customer-api'], basis: 'AI_INTERPRETATION',
        reason: 'Repozytorium należy do proponowanego systemu.', sourceRefs: ['operator:description'],
        confidence: 'MEDIUM', requiresConfirmation: false }],
      confidence: 'MEDIUM', requiresConfirmation: false, questions: [], visibilityLimits: []
    });
    original.previews.push({ proposalIndex: 1, validationStatus: 'DEFERRED', valid: false,
      candidatePayload: null, violations: [], fieldErrors: [] });
    const api = { start: vi.fn(), get: vi.fn(), previewBatch: vi.fn(() => of({
      expectedDigest: 'old', candidateDigest: 'combined', valid: true, entities: [], violations: []
    })), decideBatch: vi.fn() };
    const fixture = await batchFixture(original, api);
    const panel = fixture.componentInstance;
    panel.previewSelection();
    fixture.detectChanges();
    expect(panel.canSave()).toBe(true);
    expect(api.previewBatch).toHaveBeenCalledWith('job-1', { decisions: [
      { action: 'APPLY', selectedPaths: ['name'], confirmedPaths: [] },
      { action: 'APPLY', selectedPaths: ['references.systems'], confirmedPaths: [] }
    ] });
    expect(fixture.nativeElement.textContent).not.toContain('Zależna od wcześniejszej propozycji');
  });

  it('shows whole-catalog violations and keeps save disabled for an invalid batch', async () => {
    const original = job('COMPLETED');
    const api = { start: vi.fn(), get: vi.fn(), previewBatch: vi.fn(() => of({
      expectedDigest: 'old', candidateDigest: 'invalid', valid: false, entities: [],
      violations: [{ code: 'MISSING_REFERENCE', fingerprint: 'repository/a', ruleCode: 'OPCTX_REF',
        severity: 'ERROR' }]
    })), decideBatch: vi.fn() };
    const fixture = await batchFixture(original, api);
    const panel = fixture.componentInstance;
    panel.setConfirmed(0, 'name', true);
    panel.previewSelection();
    fixture.detectChanges();
    expect(panel.canSave()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Cały zestaw wymaga poprawy');
    expect(fixture.nativeElement.textContent).toContain('OPCTX_REF');
    panel.saveBatch();
    expect(api.decideBatch).not.toHaveBeenCalled();
  });

  it('recovers a lost batch response through GET without sending another write', async () => {
    const original = job('COMPLETED');
    const applied = { ...original, proposalDecisions: [{ proposalIndex: 0, action: 'APPLY' as const,
      selectedPaths: ['name'], completedAt: '2026-09-13T10:05:00Z', catalogDigest: 'new-digest' }] };
    const api = { start: vi.fn(), get: vi.fn()
      .mockReturnValueOnce(throwError(() => new Error('offline')))
      .mockReturnValueOnce(of(applied)),
      previewBatch: vi.fn(() => of({ expectedDigest: 'old', candidateDigest: 'candidate',
        valid: true, entities: [], violations: [] })),
      decideBatch: vi.fn(() => throwError(() => new HttpErrorResponse({ status: 0 }))) };
    const fixture = await batchFixture(original, api);
    const panel = fixture.componentInstance;
    const refreshed = vi.fn();
    panel.catalogChanged.subscribe(refreshed);
    panel.setConfirmed(0, 'name', true);
    panel.previewSelection();
    panel.saveBatch();
    fixture.detectChanges();
    expect(panel.pendingDecisionCheck()).not.toBeNull();
    expect(panel.canSave()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Sprawdź wynik decyzji');

    panel.retryDecisionCheck();
    fixture.detectChanges();
    expect(api.decideBatch).toHaveBeenCalledTimes(1);
    expect(api.get).toHaveBeenCalledTimes(2);
    expect(api.get).toHaveBeenCalledWith('job-1');
    expect(refreshed).toHaveBeenCalledTimes(1);
    expect(panel.pendingDecisionCheck()).toBeNull();
    expect(panel.decisionError()).toBe('');
  });

  it('preserves the selection and requires a fresh preview after a stale conflict', async () => {
    const original = job('COMPLETED');
    const api = { start: vi.fn(), get: vi.fn(() => of(original)),
      previewBatch: vi.fn(() => of({ expectedDigest: 'old', candidateDigest: 'candidate',
        valid: true, entities: [], violations: [] })),
      decideBatch: vi.fn(() => throwError(() => new HttpErrorResponse({ status: 409 }))) };
    const fixture = await batchFixture(original, api);
    const panel = fixture.componentInstance;
    panel.setConfirmed(0, 'name', true);
    panel.previewSelection();
    panel.saveBatch();
    fixture.detectChanges();
    expect(api.get).toHaveBeenCalledWith('job-1');
    expect(panel.isSelected(0, original.draft!.proposals[0], 'name')).toBe(true);
    expect(panel.isConfirmed(0, 'name')).toBe(true);
    expect(panel.batchPreview()).toBeNull();
    expect(panel.canSave()).toBe(false);
    expect(panel.decisionError()).toContain('Katalog lub zestaw propozycji zmieniły się');
  });
});

async function initialJobFixture(initialJob: OperationalContextAssistanceJob) {
  await TestBed.configureTestingModule({
    imports: [ContextAssistancePanelComponent],
    providers: [
      provideAnimationsAsync('noop'),
      { provide: OperationalContextAssistanceApiService, useValue: { start: vi.fn(), get: vi.fn() } },
      { provide: AnalysisJobPollingService, useValue: { poll: vi.fn() } },
      { provide: AppUiConfigService, useValue: { config: signal({ defaultBranch: '' }) } }
    ]
  }).compileComponents();
  const fixture = TestBed.createComponent(ContextAssistancePanelComponent);
  fixture.componentRef.setInput('prefill', { mode: 'CREATE_AREA' });
  fixture.componentRef.setInput('initialJob', initialJob);
  fixture.detectChanges();
  return fixture;
}

async function batchFixture<T extends object>(original: OperationalContextAssistanceJob, api: T) {
  await TestBed.configureTestingModule({
    imports: [ContextAssistancePanelComponent],
    providers: [
      provideAnimationsAsync('noop'),
      { provide: OperationalContextAssistanceApiService, useValue: api },
      { provide: AnalysisJobPollingService, useValue: { poll: vi.fn() } },
      { provide: AppUiConfigService, useValue: { config: signal({ defaultBranch: '' }) } }
    ]
  }).compileComponents();
  const fixture = TestBed.createComponent(ContextAssistancePanelComponent);
  fixture.componentRef.setInput('prefill', { mode: 'CREATE_AREA' });
  fixture.componentRef.setInput('initialJob', original);
  fixture.detectChanges();
  return fixture;
}

async function onboardingFixture(systems: { id: string; label: string }[] = []) {
  const api = { start: vi.fn(() => of(job('COMPLETED'))), get: vi.fn(),
    sourceOptions: vi.fn(() => of({ configuredBaseUrl: 'https://gitlab.example.com', configuredGroup: 'CRM', projects: [] })) };
  await TestBed.configureTestingModule({
    imports: [ContextAssistancePanelComponent],
    providers: [
      provideAnimationsAsync('noop'),
      { provide: OperationalContextAssistanceApiService, useValue: api },
      { provide: AnalysisJobPollingService, useValue: { poll: vi.fn() } },
      { provide: AppUiConfigService, useValue: { config: signal({ defaultBranch: 'main' }) } }
    ]
  }).compileComponents();
  const fixture = TestBed.createComponent(ContextAssistancePanelComponent);
  fixture.componentRef.setInput('prefill', { mode: 'CREATE_AREA' });
  fixture.componentRef.setInput('systemOptions', systems);
  fixture.detectChanges();
  (fixture.nativeElement.querySelector('#assistance-use-gitlab') as HTMLInputElement).click();
  await fixture.whenStable();
  fixture.detectChanges();
  fixture.componentInstance.descriptionControl.setValue('Opis projektu');
  fixture.componentInstance.projectUrlControl.setValue('https://gitlab.example.com/CRM/library-repo');
  fixture.componentInstance.useManualRef();
  fixture.componentInstance.refControl.setValue('main');
  return { fixture, api };
}

function job(status: OperationalContextAssistanceJob['status']): OperationalContextAssistanceJob {
  return {
    jobId: 'job-1', status,
    currentStepCode: status === 'COMPLETED' ? 'COMPLETE' : 'QUEUE',
    currentStepLabel: status === 'COMPLETED' ? 'Ukończono' : 'W kolejce',
    createdAt: '2026-09-13T10:00:00Z', updatedAt: '2026-09-13T10:00:01Z',
    steps: [], aiActivityEvents: [], sourceRefs: ['operator:description'], visibilityLimits: [],
    previews: status === 'COMPLETED'
      ? [{ proposalIndex: 0, validationStatus: 'VALID', valid: true, candidatePayload: { id: 'customer-api' }, violations: [], fieldErrors: [] }]
      : [],
    draft: status === 'COMPLETED'
      ? {
          proposals: [{
            operation: 'CREATE', entityType: 'system', entityId: 'customer-api',
            changes: [{ path: 'name', after: 'Customer API', basis: 'USER_STATEMENT',
              reason: 'Nazwa pochodzi z opisu operatora.', sourceRefs: ['operator:description'],
              confidence: 'MEDIUM', requiresConfirmation: false }],
            confidence: 'MEDIUM', requiresConfirmation: true,
            questions: [], visibilityLimits: []
          }],
          questions: ['Kto jest właścicielem systemu?'], visibilityLimits: []
        }
      : null
  };
}
