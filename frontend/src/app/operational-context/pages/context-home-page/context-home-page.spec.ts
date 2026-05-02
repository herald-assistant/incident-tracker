import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import {
  ExplainableAggregateDto,
  OperationalContextEntityDetailDto,
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
});

async function createComponent(
  summary: OperationalContextSummaryDto,
  systems: OperationalContextSystemRowDto[]
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
    getHandoffRules: vi.fn(() => of([])),
    getValidation: vi.fn(() => of([])),
    getOpenQuestions: vi.fn(() => of([])),
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
