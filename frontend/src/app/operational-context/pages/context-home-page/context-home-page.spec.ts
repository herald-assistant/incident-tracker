import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import {
  ExplainableAggregateDto,
  OpenQuestionDto,
  OperationalContextEntityDetailDto,
  OperationalContextHandoffRuleRowDto,
  OperationalContextSummaryDto,
  OperationalContextSystemRowDto
} from '../../models/operational-context.models';
import { OperationalContextApiService } from '../../services/operational-context-api.service';
import { ContextHomePageComponent } from './context-home-page';

describe('ContextHomePageComponent', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('should render empty catalogue state', async () => {
    const { fixture } = await createComponent(emptySummary(), []);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Operational context catalogue is currently empty or incomplete.'
    );
  });

  it('should render catalogue rows and local filter', async () => {
    const { fixture } = await createComponent(readySummary(), [systemRow()]);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.selectTab('systems');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('App Core');

    component.localFilterControl.setValue('missing');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('App Core');
  });

  it('should open entity drawer with details', async () => {
    const { fixture, api } = await createComponent(readySummary(), [systemRow()]);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.openEntity({ type: 'system', id: 'app-core' });
    fixture.detectChanges();

    expect(api.getEntity).toHaveBeenCalledWith('system', 'app-core');
    expect(fixture.nativeElement.textContent).toContain('Core detail');
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
    expect(component.selectedTab()).toBe('signal-resolver');
    expect(fixture.nativeElement.textContent).toContain('Resolve runtime or code signals');
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
    expect(fixture.nativeElement.textContent).not.toContain('Should this context be renamed?');
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
  handoffRules: OperationalContextHandoffRuleRowDto[] = []
) {
  const api = {
    getSummary: vi.fn(() => of(summary)),
    getSystems: vi.fn(() => of(systems)),
    getRepositories: vi.fn(() => of([])),
    getProcesses: vi.fn(() => of([])),
    getIntegrations: vi.fn(() => of([])),
    getBoundedContexts: vi.fn(() => of([])),
    getTeams: vi.fn(() => of([])),
    getGlossary: vi.fn(() => of([])),
    getHandoffRules: vi.fn(() => of(handoffRules)),
    getValidation: vi.fn(() => of([])),
    getOpenQuestions: vi.fn(() => of(openQuestions)),
    search: vi.fn(() => of([])),
    getEntity: vi.fn(() => of(entityDetail()))
  };

  await TestBed.configureTestingModule({
    imports: [ContextHomePageComponent],
    providers: [
      provideAnimationsAsync('noop'),
      provideLocationMocks(),
      provideRouter([]),
      { provide: OperationalContextApiService, useValue: api }
    ]
  }).compileComponents();

  return {
    fixture: TestBed.createComponent(ContextHomePageComponent),
    api
  };
}

function handoffRule(): OperationalContextHandoffRuleRowDto {
  return {
    id: 'backend-service-error',
    title: 'Backend service error',
    routeTo: 'Owner of backend',
    useWhen: aggregate('Use when', 1),
    requiredEvidence: aggregate('Required evidence', 2),
    expectedFirstAction: 'Route to Owner of backend.',
    partnerTeams: aggregate('Partner teams', 0)
  };
}

function openQuestion(id: string, severity: string, question: string): OpenQuestionDto {
  return {
    id,
    sourceFile: 'systems.yml',
    entityType: 'system',
    entityId: 'app-core',
    question,
    severity,
    status: 'open'
  };
}

function emptySummary(): OperationalContextSummaryDto {
  return {
    systems: 0,
    repositories: 0,
    processes: 0,
    integrations: 0,
    boundedContexts: 0,
    teams: 0,
    glossaryTerms: 0,
    handoffRules: 0,
    openQuestions: 0,
    validationFindings: { info: 0, warning: 0, error: 0 },
    catalogStatus: 'empty',
    healthCards: []
  };
}

function readySummary(): OperationalContextSummaryDto {
  return {
    ...emptySummary(),
    systems: 1,
    catalogStatus: 'ready'
  };
}

function systemRow(): OperationalContextSystemRowDto {
  return {
    id: 'app-core',
    name: 'App Core',
    type: 'internal',
    owner: {
      value: 'core-team',
      label: 'Core Team',
      confidence: 'high',
      reasons: [],
      warnings: [],
      sourceRefs: []
    },
    purpose: 'Runs the core flow.',
    repos: aggregate('Repos', 1),
    processes: aggregate('Processes', 0),
    contexts: aggregate('Contexts', 0),
    integrations: aggregate('Integrations', 0),
    signals: aggregate('Signals', 1),
    handoffReadiness: aggregate('Handoff', 1),
    validation: aggregate('Status', 0),
    openQuestions: aggregate('Open questions', 0)
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
    id: 'app-core',
    title: 'Core detail',
    subtitle: 'internal',
    overviewSections: [{ title: 'Overview', fields: { id: 'app-core' } }],
    relatedEntities: [],
    recognitionSignals: [],
    explainabilitySections: [],
    validationFindings: [],
    openQuestions: [],
    sourceReferences: [],
    rawSourcePreview: ''
  };
}
