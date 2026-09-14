import { provideLocationMocks } from '@angular/common/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MatTooltip } from '@angular/material/tooltip';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { AnalysisRunHistoryApiService } from '../../../core/services/analysis-run-history-api.service';
import { BehaviorSubject, of, Subject } from 'rxjs';

import {
  ExplainableAggregateDto,
  OpenQuestionDto,
  OperationalContextEntityDetailDto,
  OperationalContextHandoffRuleRowDto,
  OperationalContextReadModelProfile,
  OperationalContextSummaryDto,
  OperationalContextSystemRowDto,
  ValidationFindingDto
} from '../../models/operational-context.models';
import { OperationalContextApiService } from '../../services/operational-context-api.service';
import { OperationalContextMaintenanceApiService } from '../../services/operational-context-maintenance-api.service';
import { OperationalContextAssistanceApiService } from '../../services/operational-context-assistance-api.service';
import { AnalysisJobPollingService } from '../../../core/services/analysis-job-polling.service';
import { OperationalContextAssistanceJob } from '../../models/operational-context-assistance.models';
import { OperationalContextEditorState } from '../../models/operational-context-maintenance.models';
import { ContextAssistancePanelComponent } from '../../components/context-assistance-panel/context-assistance-panel';
import { OperationalContextMaintenanceFacade } from '../../services/operational-context-maintenance.facade';
import { ContextHomePageComponent } from './context-home-page';

describe('ContextHomePageComponent', () => {
  const navigatorClipboardDescriptor = Object.getOwnPropertyDescriptor(navigator, 'clipboard');

  afterEach(() => {
    vi.restoreAllMocks();
    restoreNavigatorClipboard(navigatorClipboardDescriptor);
  });

  it('explains overview counts in Polish without presenting them as a quality check', async () => {
    const card = { ...aggregate('Systems', 1), detailsType: 'system', severity: 'info', tooltip: 'Backend count.' };
    const { fixture } = await createComponent({ ...readySummary(), healthCards: [card] }, [systemRow()]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.overview-table__readiness')?.textContent).toContain('Są wpisy');
    const tooltip = fixture.debugElement.query(By.css('.overview-table .why-popover summary'))
      .injector.get(MatTooltip).message;
    expect(tooltip).toBe('Liczba systemów: 1.');
    expect(tooltip).not.toContain('Backend count.');
  });

  it('opens a saved assistance run from Analysis History without enabling a second write', async () => {
    const { fixture, historyApi, routeParams, assistancePolling } = await createComponent(emptySummary(), []);
    const archived: OperationalContextAssistanceJob = {
      ...queuedAssistanceJob(), status: 'COMPLETED', currentStepCode: 'ANALYZE',
      currentStepLabel: 'Przygotuj propozycje', preparedPrompt: 'Opis CRM: apiToken=fictional-example',
      steps: [], completedAt: '2026-09-13T10:02:00Z'
    };
    historyApi.getRun.mockReturnValue(of({
      analysisId: 'assistance-1', feature: 'operational-context-assistance',
      name: 'Asysta AI', status: 'COMPLETED', createdAt: archived.createdAt,
      updatedAt: archived.updatedAt, completedAt: archived.completedAt,
      continuationEnabled: false,
      exportEnvelope: { schema: 'tdw.operational-context-assistance-export', version: 2,
        mode: 'CREATE_AREA', target: null, exportedAt: archived.updatedAt, job: archived }
    }));

    routeParams.next(convertToParamMap({ localRunId: 'assistance-1' }));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(historyApi.getRun).toHaveBeenCalledWith('assistance-1');
    expect(fixture.componentInstance.selectedTab()).toBe('assistance');
    expect(fixture.componentInstance.assistanceHistoryReadOnly()).toBe(true);
    expect(fixture.componentInstance.assistanceJob()?.preparedPrompt)
      .toBe('Opis CRM: apiToken=fictional-example');
    expect(fixture.nativeElement.querySelector('.assistance-form')).toBeNull();
    expect(assistancePolling.poll).not.toHaveBeenCalled();
  });

  it('resumes an undecided saved assistance run from Analysis History', async () => {
    const { fixture, historyApi, routeParams, assistanceApi } = await createComponent(emptySummary(), []);
    const archived: OperationalContextAssistanceJob = {
      ...queuedAssistanceJob(), status: 'COMPLETED', currentStepCode: 'ANALYZE',
      completedAt: '2026-09-13T10:02:00Z',
      draft: { proposals: [{ operation: 'CREATE', entityType: 'system', entityId: 'crm-api',
        changes: [{ path: 'name', after: 'CRM API', reason: 'Opis operatora', basis: 'USER_STATEMENT',
          sourceRefs: ['operator:description'], confidence: 'HIGH', requiresConfirmation: false }],
        confidence: 'HIGH', requiresConfirmation: false, visibilityLimits: [] }],
        visibilityLimits: [] },
      reviewDraft: { selections: [{ selectedPaths: ['name'], confirmedPaths: [], editedValues: { name: 'CRM Customer API' } }] }
    };
    historyApi.getRun.mockReturnValue(of({
      analysisId: 'assistance-1', feature: 'operational-context-assistance',
      name: 'CRM assistance', status: 'COMPLETED', createdAt: archived.createdAt,
      updatedAt: archived.updatedAt, completedAt: archived.completedAt,
      continuationEnabled: false,
      exportEnvelope: { schema: 'tdw.operational-context-assistance-export', version: 2,
        mode: 'CREATE_AREA', target: null, exportedAt: archived.updatedAt, job: archived }
    }));
    assistanceApi.get.mockReturnValue(of(archived));

    routeParams.next(convertToParamMap({ localRunId: 'assistance-1' }));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(assistanceApi.get).toHaveBeenCalledWith('assistance-1');
    expect(fixture.componentInstance.assistanceHistoryReadOnly()).toBe(false);
    expect(fixture.nativeElement.querySelector('.assistance-proposal__actions')).not.toBeNull();
    expect(fixture.componentInstance.assistanceJob()?.reviewDraft?.selections[0].editedValues['name'])
      .toBe('CRM Customer API');
  });

  it('keeps the saved result visible when history is opened after the assistance panel was mounted', async () => {
    const { fixture, historyApi, routeParams } = await createComponent(emptySummary(), []);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.selectTab('assistance');
    fixture.detectChanges();

    const archived: OperationalContextAssistanceJob = {
      ...queuedAssistanceJob(), status: 'PARTIAL', currentStepCode: 'ANALYZE',
      currentStepLabel: 'Przygotuj propozycje', preparedPrompt: 'Zapisany prompt asysty',
      draft: { proposals: [], visibilityLimits: ['Brak potwierdzenia właściciela systemu.'] },
      completedAt: '2026-09-13T10:02:00Z'
    };
    historyApi.getRun.mockReturnValue(of({
      analysisId: 'assistance-1', feature: 'operational-context-assistance',
      name: 'Asysta AI', status: 'PARTIAL', createdAt: archived.createdAt,
      updatedAt: archived.updatedAt, completedAt: archived.completedAt,
      continuationEnabled: false,
      exportEnvelope: { schema: 'tdw.operational-context-assistance-export', version: 2,
        mode: 'CREATE_AREA', target: null, exportedAt: archived.updatedAt, job: archived }
    }));

    routeParams.next(convertToParamMap({ localRunId: 'assistance-1' }));
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.assistance-run')).not.toBeNull();
    expect(compiled.textContent).toContain('Brak potwierdzenia właściciela systemu.');
    expect(compiled.querySelector('.assistance-form')).toBeNull();
    expect(fixture.componentInstance.assistanceJob()?.jobId).toBe('assistance-1');
  });

  it('should render empty catalogue state', async () => {
    const { fixture } = await createComponent(emptySummary(), []);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Operational context catalogue is empty or incomplete.'
    );
  });

  it('should render catalogue rows and local filter', async () => {
    const { fixture } = await createComponent(readySummary(), [systemRow()]);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.selectTab('systems');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('CRM Contact Service');
    const headers = Array.from(
      fixture.nativeElement.querySelectorAll('.catalog-table__header-cell') as NodeListOf<HTMLElement>
    ).map((element) => element.textContent?.trim());
    expect(headers.slice(0, 6)).toEqual(['System', 'Type', 'Subtype', 'Owner', 'Repositories', 'Relations']);
    const rowCells = fixture.nativeElement.querySelectorAll('.catalog-table__row [role="cell"]') as NodeListOf<HTMLElement>;
    expect(rowCells[4].textContent).toContain('2');

    component.localFilterControl.setValue('missing');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('CRM Contact Service');
  });

  it('shows Add for both new structured CRM catalogue types only when supported', async () => {
    const { fixture, maintenance } = await createComponent(readySummary(), [systemRow()]);
    maintenance.writable.set(true);
    maintenance.supports.mockImplementation((type: string) =>
      ['glossary-term', 'handoff-rule'].includes(type)
    );
    fixture.componentInstance.selectTab('glossary');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Add glossary-term');
    fixture.componentInstance.addEntity();
    expect(maintenance.openCreate).toHaveBeenCalledWith('glossary-term');

    fixture.componentInstance.selectTab('handoff');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Add handoff-rule');
    fixture.componentInstance.addEntity();
    expect(maintenance.openCreate).toHaveBeenCalledWith('handoff-rule');

    fixture.componentInstance.selectTab('systems');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Add system');
  });

  it('builds anonymized CRM reference options for structured maintenance controls', async () => {
    const crmSystem = { ...systemRow(), id: 'crm-contact-core', name: 'CRM Contact Core' };
    const { fixture } = await createComponent(readySummary(), [crmSystem]);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.referenceOptions()['system']).toEqual([
      { id: 'crm-contact-core', label: 'CRM Contact Core' }
    ]);
  });

  it('should open entity drawer with details', async () => {
    const { fixture, api } = await createComponent(readySummary(), [systemRow()]);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.openEntity({ type: 'system', id: 'crm-contact-service' });
    fixture.detectChanges();

    expect(api.getEntity).toHaveBeenCalledWith('system', 'crm-contact-service');
    expect(api.getEntityRelationsReadModel).not.toHaveBeenCalled();
    expect(api.getCodeSearchReadModel).not.toHaveBeenCalled();
    expect(api.getAiApiPreviewRequests).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Core detail');
    expect(fixture.nativeElement.textContent).not.toContain('Read model projections');
    expect(fixture.nativeElement.textContent).not.toContain('AI API Preview');
    expect(drawerActionLabels(fixture.nativeElement)).toEqual([
      'Kopiuj szczegóły pozycji',
      'Otwórz dane źródłowe',
      'Zamknij szczegóły'
    ]);
  });

  it('opens assistance from the top toolbar without a duplicate tab or local-copy note', async () => {
    const { fixture } = await createComponent(readySummary(), [systemRow()]);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const button = compiled.querySelector('.context-toolbar__ai-button') as HTMLButtonElement;
    expect(button.textContent?.trim()).toBe('Uzupełnij z AI');
    expect(compiled.querySelector('.context-toolbar__note')).toBeNull();
    expect(compiled.querySelector('.context-tabs')?.textContent).not.toContain('Asysta AI');
    expect(compiled.textContent).not.toContain('Editable local copy');

    button.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedTab()).toBe('assistance');
    expect(button.getAttribute('aria-current')).toBe('page');
    expect(compiled.querySelector('.assistance-panel')).not.toBeNull();
  });

  it('opens AI assistance from an empty catalogue while keeping manual tabs', async () => {
    const { fixture } = await createComponent(emptySummary(), []);
    fixture.detectChanges();
    await fixture.whenStable();
    const button = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((candidate) => candidate.textContent?.includes('Utwórz obszar z pomocą AI'));
    button?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedTab()).toBe('assistance');
    expect(fixture.nativeElement.textContent).toContain('zapiszesz go jednym krokiem');
    expect(fixture.nativeElement.textContent).toContain('Systems');
  });

  it('opens entity improvement from the detail drawer with the selected entity target', async () => {
    const { fixture, maintenance } = await createComponent(readySummary(), [systemRow()]);
    maintenance.supports.mockReturnValue(true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.openEntity({ type: 'system', id: 'crm-contact-service' });
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('[aria-label="Zaproponuj uzupełnienie z AI"]') as HTMLButtonElement;
    button.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedTab()).toBe('assistance');
    expect(fixture.componentInstance.assistancePrefill()).toEqual({
      mode: 'IMPROVE_ENTITY',
      target: { kind: 'ENTITY', entityType: 'system', entityId: 'crm-contact-service' }
    });
    expect(fixture.nativeElement.querySelector('.entity-drawer')).toBeNull();
  });

  it('closes a pristine manual editor before opening assistance', async () => {
    const { fixture, maintenance } = await createComponent(readySummary(), [systemRow()]);
    maintenance.editor.set({ mode: 'edit', type: 'system', entity: editableSystem() });
    maintenance.closeEditor.mockImplementation(() => maintenance.editor.set(null));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('app-context-entity-editor-drawer')).not.toBeNull();

    fixture.componentInstance.startAreaAssistance();
    fixture.detectChanges();

    expect(maintenance.closeEditor).toHaveBeenCalled();
    expect(maintenance.cancelDelete).toHaveBeenCalled();
    expect(fixture.componentInstance.selectedTab()).toBe('assistance');
    expect(fixture.nativeElement.querySelector('app-context-entity-editor-drawer')).toBeNull();
  });

  it('keeps the current operation visible while maintenance is busy', async () => {
    const { fixture, maintenance } = await createComponent(readySummary(), [systemRow()]);
    maintenance.busy.set(true);
    fixture.detectChanges();

    fixture.componentInstance.startAreaAssistance();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedTab()).toBe('overview');
    expect(maintenance.closeEditor).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Trwa zapis lub usuwanie wpisu');
  });

  it('passes a validation finding and its exact target to an already mounted assistance form', async () => {
    const finding = validationFinding('missing-owner', 'warning', 'ownership', 'system',
      'crm-contact-service', 'Missing owner', 'systems.yml', '$.systems[id=crm-contact-service].ownership.status');
    const { fixture, maintenance, assistanceApi } = await createComponent(readySummary(), [systemRow()], [], [], [finding]);
    maintenance.supports.mockReturnValue(true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.startAreaAssistance();
    fixture.detectChanges();
    const panel = fixture.debugElement.query(By.directive(ContextAssistancePanelComponent))
      .componentInstance as ContextAssistancePanelComponent;
    panel.descriptionControl.setValue('Poprzedni opis');

    fixture.componentInstance.selectTab('validation');
    fixture.detectChanges();
    const action = Array.from(fixture.nativeElement.querySelectorAll('.maintenance-card__actions button') as NodeListOf<HTMLButtonElement>)
      .find((button) => button.textContent?.includes('Pomóż rozwiązać'));
    action?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.selectedTab()).toBe('assistance');
    expect(fixture.componentInstance.assistancePrefill().target).toEqual({
      kind: 'VALIDATION_FINDING', entityType: 'system', entityId: 'crm-contact-service', id: 'missing-owner'
    });
    expect(panel.descriptionControl.value).toContain('Missing owner');
    expect(panel.descriptionControl.value).not.toBe('Poprzedni opis');
    expect(fixture.componentInstance.assistanceJob()).toBeNull();

    assistanceApi.start.mockReturnValue(of(queuedAssistanceJob()));
    panel.start();
    expect(assistanceApi.start).toHaveBeenCalledWith(expect.objectContaining({
      mode: 'RESOLVE_FINDING',
      target: { kind: 'VALIDATION_FINDING', entityType: 'system', entityId: 'crm-contact-service', id: 'missing-owner' }
    }));
  });

  it('focuses an editable finding field and only offers question assistance for an entity target', async () => {
    const finding = validationFinding('missing-owner', 'warning', 'ownership', 'system',
      'crm-contact-service', 'Missing owner', 'systems.yml', '$.systems[id=crm-contact-service].ownership.status');
    const question = openQuestion('owner-question', 'warning', 'Who owns this system?');
    const unresolved = openQuestion('general-question', 'info', 'Which entity is affected?', 'systems.yml', 'system', null);
    const { fixture, maintenance } = await createComponent(readySummary(), [systemRow()], [question, unresolved], [], [finding]);
    maintenance.supports.mockReturnValue(true);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.selectTab('validation');
    fixture.detectChanges();
    const edit = Array.from(fixture.nativeElement.querySelectorAll('.maintenance-card__actions button') as NodeListOf<HTMLButtonElement>)
      .find((button) => button.textContent?.includes('Edit source'));
    edit?.click();
    expect(maintenance.openEdit).toHaveBeenCalledWith('system', 'crm-contact-service');
    expect(fixture.componentInstance.editorFocusPath()).toBe('ownership.status');

    fixture.componentInstance.selectTab('open-questions');
    fixture.detectChanges();
    const cards = fixture.nativeElement.querySelectorAll('.maintenance-card--question') as NodeListOf<HTMLElement>;
    expect(cards[0].textContent).toContain('Pomóż rozwiązać');
    expect(cards[1].textContent).not.toContain('Pomóż rozwiązać');
    expect(cards[1].textContent).toContain('Wskaż encję');
    const assist = Array.from(cards[0].querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((button) => button.textContent?.includes('Pomóż rozwiązać'));
    assist?.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.assistancePrefill().target).toEqual({
      kind: 'OPEN_QUESTION', entityType: 'system', entityId: 'crm-contact-service', id: 'owner-question'
    });
    expect(fixture.componentInstance.assistancePrefill().description).toContain(question.question);
  });

  it('keeps a pending start request and its job when switching away and back to assistance', async () => {
    const { fixture, assistanceApi, assistancePolling } = await createComponent(emptySummary(), []);
    const startResponse = new Subject<OperationalContextAssistanceJob>();
    assistanceApi.start.mockReturnValue(startResponse.asObservable());
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.componentInstance.startAreaAssistance();
    fixture.detectChanges();
    const firstPanel = fixture.debugElement.query(By.directive(ContextAssistancePanelComponent))
      .componentInstance as ContextAssistancePanelComponent;
    firstPanel.descriptionControl.setValue('System CRM');
    firstPanel.start();
    fixture.detectChanges();
    expect(fixture.componentInstance.assistanceJob()).toBeNull();

    fixture.componentInstance.selectTab('overview');
    fixture.detectChanges();
    startResponse.next(queuedAssistanceJob());
    fixture.detectChanges();
    expect(fixture.componentInstance.assistanceJob()?.jobId).toBe('assistance-1');
    expect(assistancePolling.poll).toHaveBeenCalledTimes(1);

    fixture.componentInstance.selectTab('assistance');
    fixture.detectChanges();
    const restoredPanel = fixture.debugElement.query(By.directive(ContextAssistancePanelComponent))
      .componentInstance as ContextAssistancePanelComponent;
    expect(restoredPanel).toBe(firstPanel);
    expect(restoredPanel.job()?.jobId).toBe('assistance-1');
    expect(assistancePolling.poll).toHaveBeenCalledTimes(1);
    expect(assistanceApi.start).toHaveBeenCalledTimes(1);
  });

  it('refreshes summary, validation and open questions after an assistance save', async () => {
    const { fixture, api } = await createComponent(emptySummary(), []);
    fixture.componentInstance.selectTab('assistance');
    fixture.detectChanges();
    const panel = fixture.debugElement.query(By.directive(ContextAssistancePanelComponent))
      .componentInstance as ContextAssistancePanelComponent;
    const initialSummaryLoads = api.getSummary.mock.calls.length;
    const initialValidationLoads = api.getValidation.mock.calls.length;
    const initialQuestionLoads = api.getOpenQuestions.mock.calls.length;

    panel.catalogChanged.emit({ type: 'system', id: 'customer-api' });
    fixture.detectChanges();

    expect(api.getSummary).toHaveBeenCalledTimes(initialSummaryLoads + 1);
    expect(api.getValidation).toHaveBeenCalledTimes(initialValidationLoads + 1);
    expect(api.getOpenQuestions).toHaveBeenCalledTimes(initialQuestionLoads + 1);
  });

  it('should render the source editor layout as read-only entity preview', async () => {
    const { fixture, api, maintenance, maintenanceApi } = await createComponent(readySummary(), [systemRow()]);
    maintenance.capabilities.set({ source: 'tdw-data/operational-context', supportedEntityTypes: ['system'] });
    maintenance.supports.mockImplementation((type: string) => type === 'system');
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.openEntity({ type: 'system', id: 'crm-contact-service' });
    fixture.detectChanges();

    expect(api.getEntity).toHaveBeenCalledWith('system', 'crm-contact-service');
    expect(maintenanceApi.getEntity).toHaveBeenCalledWith('system', 'crm-contact-service');
    expect(fixture.nativeElement.textContent).toContain('Basic');
    expect(fixture.nativeElement.textContent).toContain('Ownership and references');
    expect(fixture.nativeElement.textContent).not.toContain('Save');

    const summary = fixture.nativeElement.querySelector(
      '.entity-drawer app-context-entity-editor-drawer textarea#summary'
    ) as HTMLTextAreaElement | null;
    const previewInput = fixture.nativeElement.querySelector(
      '.entity-drawer app-context-entity-editor-drawer input'
    ) as HTMLInputElement | null;
    expect(summary?.value).toBe('Core system summary');
    expect(summary?.disabled).toBe(true);
    expect(previewInput?.disabled).toBe(true);
  });

  it('should keep search only in the signal resolver tab', async () => {
    const { fixture, api } = await createComponent(readySummary(), [systemRow()]);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.context-toolbar .signal-search')).toBeNull();

    component.selectTab('signal-resolver');
    component.searchControl.setValue('api-gateway');
    fixture.detectChanges();

    component.runSignalSearch(new Event('submit'));
    fixture.detectChanges();

    expect(api.search).toHaveBeenCalledWith('api-gateway');
    expect(api.getProfiledSearch).toHaveBeenCalledWith('api-gateway', 'default');
    expect(fixture.nativeElement.textContent).toContain('Search payload');
    expect(fixture.nativeElement.textContent).toContain('opctx_search(query=api-gateway)');
    expect(component.selectedTab()).toBe('signal-resolver');
    expect(fixture.nativeElement.textContent).toContain('Resolve catalogue signals');
  });

  it('should reload search AI API preview when switching search profile', async () => {
    const { fixture, api } = await createComponent(readySummary(), [systemRow()]);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.selectTab('signal-resolver');
    component.searchControl.setValue('limit');
    component.runSignalSearch(new Event('submit'));
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll(
      '.ai-api-preview-panel__profile-toggle button'
    ) as NodeListOf<HTMLButtonElement>;
    buttons[1].click();
    fixture.detectChanges();

    expect(api.getProfiledSearch).toHaveBeenLastCalledWith('limit', 'expanded');
    expect(fixture.nativeElement.textContent).toContain('expanded');
  });

  it('should use relevant filters on open questions', async () => {
    const { fixture } = await createComponent(readySummary(), [], [
      openQuestion('question-warning', 'warning', 'Who owns this process?'),
      openQuestion('question-info', 'info', 'Should this context be renamed?')
    ]);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.selectTab('open-questions');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const filterLabels = Array.from(
      compiled.querySelectorAll('.context-filters .toggle-row span')
    ).map((element) => element.textContent?.trim());
    expect(filterLabels).toEqual(['Warnings']);
    expect(fixture.nativeElement.textContent).toContain('Who owns this process?');
    expect(fixture.nativeElement.textContent).toContain('Should this context be renamed?');

    component.onlyWarningsControl.setValue(true);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Who owns this process?');
    expect(fixture.nativeElement.textContent).not.toContain('Should this CRM context be renamed?');
  });

  it('should expose maintenance targets and filters for validation findings', async () => {
    const { fixture } = await createComponent(
      readySummary(),
      [],
      [],
      [],
      [
        validationFinding(
          'missing-owner',
          'warning',
          'ownership',
          'system',
          'crm-contact-service',
          'Missing owner',
          'systems.yml',
          '$.systems[0].owner'
        ),
        validationFinding(
          'bad-scope',
          'info',
          'code-search',
          'code-search-scope',
          'core-scope',
          'Scope can be tighter',
          'code-search-scopes.yml',
          '$.codeSearchScopes[0]'
        )
      ]
    );
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.selectTab('validation');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('systems.yml');
    expect(fixture.nativeElement.textContent).toContain('$.systems[0].owner');
    expect(fixture.nativeElement.textContent).toContain('system/crm-contact-service');
    expect(fixture.nativeElement.textContent).toContain('code-search-scopes.yml');
    expect(fixture.nativeElement.querySelector('.maintenance-card')).not.toBeNull();

    const writeText = vi.fn(() => Promise.resolve());
    installNavigatorClipboard({ writeText });
    const copyButton = fixture.nativeElement.querySelector(
      '.maintenance-card .icon-action'
    ) as HTMLButtonElement;
    copyButton.click();
    await fixture.whenStable();

    expect(writeText).toHaveBeenCalledWith(
      'systems.yml $.systems[0].owner | system/crm-contact-service | ownership'
    );

    component.validationCategoryControl.setValue('ownership');
    component.validationSourceFileControl.setValue('systems.yml');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Missing owner');
    expect(fixture.nativeElement.textContent).not.toContain('Scope can be tighter');
  });

  it('should expose maintenance filters for open questions', async () => {
    const { fixture } = await createComponent(readySummary(), [], [
      openQuestion('question-warning', 'warning', 'Who owns this process?', 'systems.yml', 'system'),
      openQuestion('question-info', 'info', 'Should this CRM context be renamed?', 'glossary.yml', 'glossary-term')
    ]);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.selectTab('open-questions');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('systems.yml');
    expect(fixture.nativeElement.textContent).toContain('system/crm-contact-service');
    expect(fixture.nativeElement.textContent).toContain('glossary.yml');
    expect(fixture.nativeElement.querySelector('.maintenance-card--question')).not.toBeNull();

    component.questionSourceFileControl.setValue('systems.yml');
    component.questionEntityTypeControl.setValue('system');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Who owns this process?');
    expect(fixture.nativeElement.textContent).not.toContain('Should this context be renamed?');
  });

  it('should render open question maintenance target without null entity id noise', async () => {
    const { fixture } = await createComponent(readySummary(), [], [
      openQuestion('question-without-entity-id', 'info', 'Clarify CRM glossary term?', 'glossary.yml', 'glossary-term', null)
    ]);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.selectTab('open-questions');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('glossary-term');
    expect(fixture.nativeElement.textContent).not.toContain('glossary-term/null');
  });

  it('should not apply unsupported checkbox filters on handoff rules', async () => {
    const { fixture } = await createComponent(
      readySummary(),
      [],
      [],
      [handoffRule()]
    );
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.selectTab('handoff');
    component.onlyWarningsControl.setValue(true);
    component.onlyMissingOwnerControl.setValue(true);
    component.onlyOpenQuestionsControl.setValue(true);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const filterLabels = Array.from(
      compiled.querySelectorAll('.context-filters .toggle-row span')
    ).map((element) => element.textContent?.trim());
    expect(filterLabels).toEqual([]);
    expect(fixture.nativeElement.textContent).toContain('Backend service error');
  });
});

async function createComponent(
  summary: OperationalContextSummaryDto,
  systems: OperationalContextSystemRowDto[],
  openQuestions: OpenQuestionDto[] = [],
  handoffRules: OperationalContextHandoffRuleRowDto[] = [],
  validation: ValidationFindingDto[] = []
) {
  const api = {
    getSummary: vi.fn(() => of(summary)),
    getSystems: vi.fn(() => of(systems)),
    getRepositories: vi.fn(() => of([])),
    getCodeSearchScopes: vi.fn(() => of([])),
    getProcesses: vi.fn(() => of([])),
    getIntegrations: vi.fn(() => of([])),
    getBoundedContexts: vi.fn(() => of([])),
    getTeams: vi.fn(() => of([])),
    getGlossary: vi.fn(() => of([])),
    getHandoffRules: vi.fn(() => of(handoffRules)),
    getValidation: vi.fn(() => of(validation)),
    getOpenQuestions: vi.fn(() => of(openQuestions)),
    search: vi.fn(() => of([])),
    getProfiledSearch: vi.fn((query: string, profile: OperationalContextReadModelProfile) =>
      of(profiledSearchPayload(query, profile))
    ),
    profiledSearchUrl: vi.fn(
      (query: string, profile: OperationalContextReadModelProfile) =>
        `/api/operational-context/search?q=${encodeURIComponent(query)}&profile=${profile}`
    ),
    getEntity: vi.fn(() => of(entityDetail())),
    getEntityRelationsReadModel: vi.fn(() => of(relationsReadModel())),
    getCodeSearchReadModel: vi.fn(() => of(codeSearchReadModel())),
    getAiApiPreviewRequests: vi.fn(() => [])
  };
  const maintenance = {
    capabilities: signal<{ source: string; supportedEntityTypes: string[] } | null>(null),
    capabilitiesLoading: signal(false),
    capabilitiesError: signal(''),
    writable: signal(false),
    editor: signal<OperationalContextEditorState | null>(null),
    busy: signal(false),
    error: signal(''),
    fieldErrors: signal([]),
    deleteImpact: signal(null),
    deleteLoading: signal(false),
    deleteError: signal(''),
    saved$: new Subject(),
    deleted$: new Subject(),
    supports: vi.fn((_type: string) => false),
    loadCapabilities: vi.fn(),
    openCreate: vi.fn(),
    openEdit: vi.fn(),
    closeEditor: vi.fn(),
    save: vi.fn(),
    requestDelete: vi.fn(),
    cancelDelete: vi.fn(),
    confirmDelete: vi.fn()
  };
  const maintenanceApi = {
    getEntity: vi.fn(() => of(editableSystem()))
  };
  const assistanceApi = {
    start: vi.fn(),
    get: vi.fn(),
    decide: vi.fn()
  };
  const assistanceUpdates = new Subject<OperationalContextAssistanceJob>();
  const assistancePolling = { poll: vi.fn(() => assistanceUpdates.asObservable()) };
  const historyApi = { getRun: vi.fn() };
  const routeParams = new BehaviorSubject(convertToParamMap({}));

  await TestBed.configureTestingModule({
    imports: [ContextHomePageComponent],
    providers: [
      provideAnimationsAsync('noop'),
      provideLocationMocks(),
      provideRouter([]),
      { provide: OperationalContextApiService, useValue: api },
      { provide: OperationalContextMaintenanceApiService, useValue: maintenanceApi },
      { provide: OperationalContextAssistanceApiService, useValue: assistanceApi },
      { provide: AnalysisJobPollingService, useValue: assistancePolling },
      { provide: AnalysisRunHistoryApiService, useValue: historyApi },
      { provide: ActivatedRoute, useValue: { queryParamMap: routeParams.asObservable() } },
      { provide: OperationalContextMaintenanceFacade, useValue: maintenance }
    ]
  }).compileComponents();

  return {
    fixture: TestBed.createComponent(ContextHomePageComponent),
    api,
    maintenanceApi,
    maintenance,
    assistanceApi,
    assistancePolling,
    historyApi,
    routeParams
  };
}

function queuedAssistanceJob(): OperationalContextAssistanceJob {
  return {
    jobId: 'assistance-1', status: 'QUEUED', currentStepCode: 'QUEUED', currentStepLabel: 'W kolejce',
    createdAt: '2026-09-13T10:00:00Z', updatedAt: '2026-09-13T10:00:00Z',
    steps: [], aiActivityEvents: [], sourceRefs: [], visibilityLimits: [], previews: []
  };
}

function editableSystem() {
  return {
    type: 'system' as const,
    id: 'crm-contact-service',
    sourceFile: 'systems.yml',
    payload: {
      id: 'crm-contact-service',
      name: 'CRM Contact Service',
      summary: 'Core system summary',
      ownership: {
        ownershipStatus: 'explicit',
        ownerTeamIds: ['crm-contact-team'],
        confidence: 'high'
      }
    }
  };
}

function handoffRule(): OperationalContextHandoffRuleRowDto {
  return {
    id: 'backend-service-error',
    title: 'Backend service error',
    useWhen: aggregate('Use when', 1),
    requiredEvidence: aggregate('Required evidence', 2),
    expectedFirstAction: 'Resolve owner from bounded context or system.'
  };
}

function openQuestion(
  id: string,
  severity: string,
  question: string,
  sourceFile = 'systems.yml',
  entityType = 'system',
  entityId: string | null = 'crm-contact-service'
): OpenQuestionDto {
  return {
    id,
    sourceFile,
    entityType,
    entityId,
    question,
    severity,
    status: 'open'
  };
}

function validationFinding(
  id: string,
  severity: string,
  category: string,
  entityType: string,
  entityId: string,
  title: string,
  file: string,
  path: string
): ValidationFindingDto {
  return {
    id,
    severity,
    category,
    entityType,
    entityId,
    title,
    detail: title,
    sourceRefs: [{ file, path, entityId }],
    suggestedFix: `Fix ${title}`,
    impact: `Impact ${title}`
  };
}

function emptySummary(): OperationalContextSummaryDto {
  return {
    systems: 0,
    repositories: 0,
    codeSearchScopes: 0,
    processes: 0,
    integrations: 0,
    boundedContexts: 0,
    teams: 0,
    glossaryTerms: 0,
    handoffRules: 0,
    openQuestions: 0,
    validationFindings: { info: 0, warning: 0, error: 0 },
    catalogStatus: 'ok',
    healthCards: []
  };
}

function readySummary(): OperationalContextSummaryDto {
  return {
    ...emptySummary(),
    systems: 1,
    catalogStatus: 'ok'
  };
}

function systemRow(): OperationalContextSystemRowDto {
  return {
    id: 'crm-contact-service',
    name: 'CRM Contact Service',
    systemType: 'internal-service',
    systemSubtype: 'backend',
    owner: {
      value: 'crm-contact-team',
      label: 'CRM Contact Team',
      confidence: 'high',
      reasons: [],
      warnings: [],
      sourceRefs: []
    },
    resolvedOwnership: resolvedOwnership(),
    purpose: 'Runs the core flow.',
    repositories: aggregate('Repositories', 2),
    relations: aggregate('Relations', 0),
    signals: aggregate('Signals', 1),
    handoffReadiness: aggregate('Handoff', 1),
    validation: aggregate('Status', 0),
    openQuestions: aggregate('Open questions', 0)
  };
}

function resolvedOwnership() {
  return {
    situationType: 'inside-system',
    primaryOwners: [
      {
        targetType: 'system',
        targetId: 'crm-contact-service',
        targetLabel: 'CRM Contact Service',
        ownerTeamIds: ['crm-contact-team'],
        ownerLabel: null,
        source: 'explicit-ownership',
        confidence: 'high'
      }
    ],
    partnerOwners: [],
    resolutionPath: ['system:crm-contact-service -> ownership'],
    handoffReason: 'Problem typu `inside-system` prowadzi do crm-contact-team.',
    visibilityLimits: []
  };
}

function aggregate(label: string, count: number): ExplainableAggregateDto {
  return {
    label,
    count,
    severity: count > 0 ? 'ok' : 'unknown',
    confidence: 'high',
    tooltip: label,
    groups: [{ label, count: 0, items: [] }],
    reasons: [],
    warnings: [],
    sourceRefs: [],
    detailsType: '',
    detailsIds: []
  };
}

function entityDetail(): OperationalContextEntityDetailDto {
  return {
    type: 'system',
    id: 'crm-contact-service',
    title: 'Core detail',
    subtitle: 'internal',
    overviewSections: [{ title: 'Overview', fields: { id: 'crm-contact-service' } }],
    relatedEntities: [],
    recognitionSignals: [],
    explainabilitySections: [],
    validationFindings: [],
    openQuestions: [],
    sourceReferences: [],
    rawSourcePreview: ''
  };
}

function relationsReadModel() {
  return {
    contract: 'operational-context.entity-relations',
    contractVersion: 1,
    analysisTarget: { type: 'system', id: 'crm-contact-service', label: 'CRM Contact Service' },
    outgoingRelations: [{ derived: true }],
    incomingRelations: [],
    neighbors: [{ type: 'process', id: 'core-process', label: 'core-process' }],
    validationFindings: []
  };
}

function codeSearchReadModel() {
  return {
    contract: 'operational-context.code-search',
    contractVersion: 1,
    analysisTarget: { type: 'system', id: 'crm-contact-service', label: 'CRM Contact Service' },
    scopes: [{}],
    repositories: [{}],
    limitations: [],
    validationFindings: []
  };
}

function profiledSearchPayload(query: string, profile: OperationalContextReadModelProfile) {
  return {
    contract: 'operational-context.search',
    contractVersion: 1,
    profile,
    analysisTarget: { query },
    data: { results: [{ type: 'system', id: 'crm-contact-service', label: 'CRM Contact Service' }] },
    links: [{
      rel: 'entity',
      href: `/api/operational-context/entities/system?id=crm-contact-service&profile=${profile}`,
      profile,
      reason: 'Read compact entity detail.'
    }],
    availableExpansions: ['profile=expanded'],
    suggestedNextReads: [`opctx_search(query=${query})`, 'opctx_get_entity(type=system, id=crm-contact-service)'],
    nextReads: [{
      label: 'Entity',
      rel: 'entity',
      href: `/api/operational-context/entities/system?id=crm-contact-service&profile=${profile}`,
      profile,
      tool: 'opctx_get_entity',
      arguments: { type: 'system', id: 'crm-contact-service' },
      reason: 'Read top result details before choosing repositories.'
    }],
    suggestedTools: ['opctx_search', 'opctx_get_entity'],
    reasonToExpand: 'Use expanded search only when default ranking is insufficient.',
    omittedBecause: [],
    truncation: {
      truncated: profile === 'default',
      reason: 'search results limited for default profile',
      returnedCounts: { results: 1 },
      omittedCounts: { results: 2 }
    },
    relevanceScore: 0.8,
    confidence: 'high',
    limitations: ['Search results are lexical/ranked hints.'],
    provenance: { sourceRefCount: 0 },
    sourceRefs: [],
    validationFindings: []
  };
}

function drawerActionLabels(root: HTMLElement): Array<string | null> {
  return Array.from(
    root.querySelectorAll('.entity-drawer__actions button') as NodeListOf<HTMLButtonElement>
  ).map((button) => button.getAttribute('aria-label'));
}

function installNavigatorClipboard(clipboard: Partial<Clipboard>): void {
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: clipboard
  });
}

function restoreNavigatorClipboard(
  descriptor: PropertyDescriptor | undefined
): void {
  if (descriptor) {
    Object.defineProperty(navigator, 'clipboard', descriptor);
    return;
  }

  Reflect.deleteProperty(navigator, 'clipboard');
}
