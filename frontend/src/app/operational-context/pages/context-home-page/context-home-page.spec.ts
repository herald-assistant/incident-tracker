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
  OperationalContextReadModelProfile,
  OperationalContextSummaryDto,
  OperationalContextSystemRowDto,
  ValidationFindingDto
} from '../../models/operational-context.models';
import { OperationalContextApiService } from '../../services/operational-context-api.service';
import { ContextHomePageComponent } from './context-home-page';

describe('ContextHomePageComponent', () => {
  const navigatorClipboardDescriptor = Object.getOwnPropertyDescriptor(navigator, 'clipboard');

  afterEach(() => {
    vi.restoreAllMocks();
    restoreNavigatorClipboard(navigatorClipboardDescriptor);
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
    expect(api.getEntityRelationsReadModel).not.toHaveBeenCalled();
    expect(api.getCodeSearchReadModel).not.toHaveBeenCalled();
    expect(api.getImplementationReadModel).not.toHaveBeenCalled();
    expect(api.getFlowReadModel).not.toHaveBeenCalled();
    expect(api.getBlastRadiusReadModel).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Core detail');
    expect(fixture.nativeElement.textContent).not.toContain('Read model projections');
    expect(fixture.nativeElement.textContent).toContain('AI API Preview');
    expect(fixture.nativeElement.textContent).toContain('opctx_get_entity');
    expect(fixture.nativeElement.textContent).toContain('include=[relations]');
    expect(fixture.nativeElement.textContent).toContain('API links');
    expect(fixture.nativeElement.textContent).toContain('Copy');
    expect(fixture.nativeElement.textContent).toContain('Open raw');
    expect(fixture.nativeElement.textContent).toContain('Close');
  });

  it('should reload AI API preview when switching profile', async () => {
    const { fixture, api } = await createComponent(readySummary(), [systemRow()]);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.openEntity({ type: 'system', id: 'app-core' });
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll(
      '.ai-api-preview-panel__profile-toggle button'
    ) as NodeListOf<HTMLButtonElement>;
    buttons[1].click();
    fixture.detectChanges();

    expect(api.getAiApiPreviewRequests).toHaveBeenLastCalledWith(
      'system',
      'app-core',
      'expanded',
      true
    );
    expect(fixture.nativeElement.textContent).toContain('expanded');
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
    expect(fixture.nativeElement.textContent).toContain('Resolve runtime or code signals');
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
    expect(fixture.nativeElement.textContent).not.toContain('Should this context be renamed?');
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
          'app-core',
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
    expect(fixture.nativeElement.textContent).toContain('system/app-core');
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
      'systems.yml $.systems[0].owner | system/app-core | ownership'
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
      openQuestion('question-info', 'info', 'Should this context be renamed?', 'glossary.md', 'glossary-term')
    ]);
    const component = fixture.componentInstance;

    fixture.detectChanges();
    await fixture.whenStable();
    component.selectTab('open-questions');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('systems.yml');
    expect(fixture.nativeElement.textContent).toContain('system/app-core');
    expect(fixture.nativeElement.textContent).toContain('glossary.md');
    expect(fixture.nativeElement.querySelector('.maintenance-card--question')).not.toBeNull();

    component.questionSourceFileControl.setValue('systems.yml');
    component.questionEntityTypeControl.setValue('system');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Who owns this process?');
    expect(fixture.nativeElement.textContent).not.toContain('Should this context be renamed?');
  });

  it('should render open question maintenance target without null entity id noise', async () => {
    const { fixture } = await createComponent(readySummary(), [], [
      openQuestion('question-without-entity-id', 'info', 'Clarify glossary term?', 'glossary.md', 'glossary-term', null)
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
    getImplementationReadModel: vi.fn(() => of(implementationReadModel())),
    getFlowReadModel: vi.fn(() => of(flowReadModel())),
    getBlastRadiusReadModel: vi.fn(() => of(blastRadiusReadModel())),
    getAiApiPreviewRequests: vi.fn(
      (
        type: string,
        id: string,
        profile: OperationalContextReadModelProfile,
        includeReadModels: boolean
      ) => {
        const requests = [
          aiApiPreviewRequest('entity', 'Entity detail', type, id, profile)
        ];
        if (includeReadModels) {
          requests.push(aiApiPreviewRequest('blast-radius', 'Blast radius', type, id, profile));
        }
        return requests;
      }
    )
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

function openQuestion(
  id: string,
  severity: string,
  question: string,
  sourceFile = 'systems.yml',
  entityType = 'system',
  entityId: string | null = 'app-core'
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
    kind: 'internal-application',
    owner: {
      value: 'core-team',
      label: 'Core Team',
      confidence: 'high',
      reasons: [],
      warnings: [],
      sourceRefs: []
    },
    purpose: 'Runs the core flow.',
    relations: aggregate('Relations', 0),
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

function relationsReadModel() {
  return {
    contract: 'operational-context.entity-relations',
    contractVersion: 1,
    analysisTarget: { type: 'system', id: 'app-core', label: 'App Core' },
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
    analysisTarget: { type: 'system', id: 'app-core', label: 'App Core' },
    scopes: [{}],
    repositories: [{}],
    limitations: [],
    validationFindings: []
  };
}

function implementationReadModel() {
  return {
    contract: 'operational-context.implementation-map',
    contractVersion: 1,
    analysisTarget: { type: 'system', id: 'app-core', label: 'App Core' },
    implementations: [{ id: 'core-scope::core-repo::api' }],
    limitations: [],
    validationFindings: []
  };
}

function flowReadModel() {
  return {
    contract: 'operational-context.flow',
    contractVersion: 1,
    analysisTarget: { type: 'system', id: 'app-core', label: 'App Core' },
    steps: [{ id: 'receive', order: 1, name: 'Receive', kind: 'http', systems: [], boundedContexts: [], integrations: [], dataStores: [], implementations: [] }],
    edges: [],
    involvedSystems: [{ type: 'system', id: 'app-core', label: 'App Core' }],
    involvedBoundedContexts: [],
    involvedIntegrations: [],
    involvedDataStores: [],
    limitations: [],
    validationFindings: []
  };
}

function blastRadiusReadModel() {
  return {
    contract: 'operational-context.blast-radius',
    contractVersion: 1,
    analysisTarget: { type: 'system', id: 'app-core', label: 'App Core' },
    impactedFlows: [{ flow: { type: 'process', id: 'core-process', label: 'core-process' }, impactedSteps: [], confidence: 'medium', reasons: [] }],
    impactedSystems: [{ entity: { type: 'system', id: 'app-core', label: 'App Core' }, impactType: 'downstream', confidence: 'medium' }],
    impactedBoundedContexts: [],
    impactedIntegrations: [],
    impactedDataStores: [],
    impactedImplementations: [{ implementation: { id: 'core-scope::core-repo::api' }, impactType: 'downstream-code', confidence: 'medium' }],
    suggestedNextEvidence: ['Use code-search scopes from impacted implementations to fetch targeted source code.'],
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
    data: { results: [{ type: 'system', id: 'app-core', label: 'App Core' }] },
    links: [{
      rel: 'entity',
      href: `/api/operational-context/entities/system?id=app-core&profile=${profile}`,
      profile,
      reason: 'Read compact entity detail.'
    }],
    availableExpansions: ['profile=expanded'],
    suggestedNextReads: [`opctx_search(query=${query})`, 'opctx_get_entity(type=system, id=app-core)'],
    nextReads: [{
      label: 'Entity',
      rel: 'entity',
      href: `/api/operational-context/entities/system?id=app-core&profile=${profile}`,
      profile,
      tool: 'opctx_get_entity',
      arguments: { type: 'system', id: 'app-core' },
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

function aiApiPreviewRequest(
  key: 'entity' | 'blast-radius',
  label: string,
  type: string,
  id: string,
  profile: OperationalContextReadModelProfile
) {
  return {
    key,
    label,
    url: `/api/operational-context/${key}?id=${id}&profile=${profile}`,
    request: of({
      contract: `operational-context.${key}`,
      contractVersion: 1,
      profile,
      analysisTarget: { type, id, label: 'App Core' },
      data: { id },
      links: [{
        rel: 'relations',
        href: `/api/operational-context/read-model/entities/${type}/relations?id=${id}&profile=${profile}`,
        profile,
        reason: 'Read compact relation graph.'
      }],
      availableExpansions: ['profile=expanded'],
      suggestedNextReads: ['opctx_get_entity(type=system, id=app-core)'],
      nextReads: [{
        label: 'Relations',
        rel: 'relations',
        href: `/api/operational-context/read-model/entities/${type}/relations?id=${id}&profile=${profile}`,
        profile,
        tool: 'opctx_get_entity',
        arguments: { type, id, include: ['relations'] },
        reason: 'Inspect upstream/downstream references.'
      }],
      suggestedTools: ['opctx_get_entity'],
      reasonToExpand: 'Expand only when compact evidence is not enough.',
      omittedBecause: [],
      truncation: {
        truncated: profile === 'default',
        reason: 'default compact profile',
        returnedCounts: { items: 1 },
        omittedCounts: { items: 2 }
      },
      relevanceScore: 0.9,
      confidence: 'high',
      limitations: [],
      provenance: { sourceRefCount: 1 },
      sourceRefs: [],
      validationFindings: []
    })
  };
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
