import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgTemplateOutlet } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { forkJoin } from 'rxjs';

import {
  ExplainableAggregateDto,
  OpenQuestionDto,
  OperationalContextCatalogRow,
  OperationalContextEntityDetailDto,
  OperationalContextSearchResultDto,
  OperationalContextSummaryDto,
  ValidationFindingDto
} from '../../models/operational-context.models';
import { OperationalContextApiService } from '../../services/operational-context-api.service';
import {
  ContextCatalogColumn,
  ContextCatalogTableComponent
} from '../../components/context-catalog-table/context-catalog-table';
import { ContextEntityDrawerComponent } from '../../components/context-entity-drawer/context-entity-drawer';
import { ExplainableCellComponent } from '../../components/explainable-cell/explainable-cell';
import { WhyPopoverComponent } from '../../components/why-popover/why-popover';

type ContextTab =
  | 'overview'
  | 'signal-resolver'
  | 'systems'
  | 'repositories'
  | 'code-search-scopes'
  | 'processes'
  | 'integrations'
  | 'bounded-contexts'
  | 'teams'
  | 'glossary'
  | 'handoff'
  | 'validation'
  | 'open-questions';

interface ContextTabItem {
  id: ContextTab;
  label: string;
}

interface ContextDataState {
  systems: OperationalContextCatalogRow[];
  repositories: OperationalContextCatalogRow[];
  codeSearchScopes: OperationalContextCatalogRow[];
  processes: OperationalContextCatalogRow[];
  integrations: OperationalContextCatalogRow[];
  boundedContexts: OperationalContextCatalogRow[];
  teams: OperationalContextCatalogRow[];
  glossary: OperationalContextCatalogRow[];
  handoffRules: OperationalContextCatalogRow[];
  validation: ValidationFindingDto[];
  openQuestions: OpenQuestionDto[];
}

const EMPTY_STATE: ContextDataState = {
  systems: [],
  repositories: [],
  codeSearchScopes: [],
  processes: [],
  integrations: [],
  boundedContexts: [],
  teams: [],
  glossary: [],
  handoffRules: [],
  validation: [],
  openQuestions: []
};

const TABS: ContextTabItem[] = [
  { id: 'overview', label: 'Overview' },
  { id: 'signal-resolver', label: 'Signal Resolver' },
  { id: 'systems', label: 'Systems' },
  { id: 'repositories', label: 'Repositories' },
  { id: 'code-search-scopes', label: 'Code Search' },
  { id: 'processes', label: 'Processes' },
  { id: 'integrations', label: 'Integrations' },
  { id: 'bounded-contexts', label: 'Bounded Contexts' },
  { id: 'teams', label: 'Teams' },
  { id: 'glossary', label: 'Glossary' },
  { id: 'handoff', label: 'Handoff' },
  { id: 'validation', label: 'Validation' },
  { id: 'open-questions', label: 'Open Questions' }
];

const COLUMNS: Record<string, ContextCatalogColumn[]> = {
  systems: [
    { key: 'name', label: 'System' },
    { key: 'kind', label: 'Kind' },
    { key: 'owner', label: 'Owner', type: 'owner' },
    { key: 'repositories', label: 'Repositories', type: 'aggregate' },
    { key: 'processes', label: 'Processes', type: 'aggregate' },
    { key: 'contexts', label: 'Contexts', type: 'aggregate' },
    { key: 'integrations', label: 'Integrations', type: 'aggregate' },
    { key: 'signals', label: 'Signals', type: 'aggregate' },
    { key: 'handoffReadiness', label: 'Handoff', type: 'aggregate' },
    { key: 'validation', label: 'Status', type: 'aggregate' }
  ],
  repositories: [
    { key: 'project', label: 'Repository' },
    { key: 'owner', label: 'Owner', type: 'owner' },
    { key: 'systems', label: 'Systems', type: 'aggregate' },
    { key: 'packageRoots', label: 'Package roots', type: 'aggregate' },
    { key: 'entrypoints', label: 'Entry classes', type: 'aggregate' },
    { key: 'runtimeMappings', label: 'Runtime mappings', type: 'aggregate' },
    { key: 'modules', label: 'Modules', type: 'aggregate' },
    { key: 'codeSearchScopes', label: 'Search scopes', type: 'aggregate' },
    { key: 'codeSearchRoles', label: 'Scope roles', type: 'aggregate' },
    { key: 'handoffReadiness', label: 'Handoff', type: 'aggregate' },
    { key: 'validation', label: 'Status', type: 'aggregate' }
  ],
  'code-search-scopes': [
    { key: 'name', label: 'Scope' },
    { key: 'lifecycleStatus', label: 'Lifecycle' },
    { key: 'targets', label: 'Targets', type: 'aggregate' },
    { key: 'repositories', label: 'Repositories', type: 'aggregate' },
    { key: 'packageHints', label: 'Packages', type: 'aggregate' },
    { key: 'entryHints', label: 'Entry hints', type: 'aggregate' },
    { key: 'dataHints', label: 'Data hints', type: 'aggregate' },
    { key: 'workflowHints', label: 'Workflows', type: 'aggregate' },
    { key: 'strategy', label: 'Strategy', type: 'aggregate' },
    { key: 'validation', label: 'Status', type: 'aggregate' }
  ],
  processes: [
    { key: 'name', label: 'Process' },
    { key: 'owner', label: 'Owner', type: 'owner' },
    { key: 'systems', label: 'Systems', type: 'aggregate' },
    { key: 'externalSystems', label: 'External systems', type: 'aggregate' },
    { key: 'contexts', label: 'Contexts', type: 'aggregate' },
    { key: 'steps', label: 'Steps', type: 'aggregate' },
    { key: 'completionSignals', label: 'Completion signals', type: 'aggregate' },
    { key: 'validation', label: 'Status', type: 'aggregate' }
  ],
  integrations: [
    { key: 'name', label: 'Integration' },
    { key: 'sourceSystem', label: 'Source' },
    { key: 'targetSystems', label: 'Targets' },
    { key: 'protocols', label: 'Protocols' },
    { key: 'integrationStyle', label: 'Style' },
    { key: 'owner', label: 'Owner', type: 'owner' },
    { key: 'partnerTeams', label: 'Partner teams', type: 'aggregate' },
    { key: 'processes', label: 'Processes', type: 'aggregate' },
    { key: 'contexts', label: 'Contexts', type: 'aggregate' },
    { key: 'signals', label: 'Signals', type: 'aggregate' },
    { key: 'handoffReadiness', label: 'Handoff', type: 'aggregate' },
    { key: 'validation', label: 'Status', type: 'aggregate' }
  ],
  'bounded-contexts': [
    { key: 'name', label: 'Context' },
    { key: 'owner', label: 'Owner', type: 'owner' },
    { key: 'systems', label: 'Systems', type: 'aggregate' },
    { key: 'repositories', label: 'Repositories', type: 'aggregate' },
    { key: 'processes', label: 'Processes', type: 'aggregate' },
    { key: 'terms', label: 'Terms', type: 'aggregate' },
    { key: 'relations', label: 'Relations', type: 'aggregate' },
    { key: 'validation', label: 'Status', type: 'aggregate' }
  ],
  teams: [
    { key: 'name', label: 'Team' },
    { key: 'ownsSystems', label: 'Systems', type: 'aggregate' },
    { key: 'ownsRepositories', label: 'Repositories', type: 'aggregate' },
    { key: 'ownsProcesses', label: 'Processes', type: 'aggregate' },
    { key: 'ownsContexts', label: 'Contexts', type: 'aggregate' },
    { key: 'ownsIntegrations', label: 'Integrations', type: 'aggregate' },
    { key: 'handoffReadiness', label: 'Handoff', type: 'aggregate' },
    { key: 'validation', label: 'Issues', type: 'aggregate' }
  ],
  glossary: [
    { key: 'term', label: 'Term' },
    { key: 'category', label: 'Category' },
    { key: 'definition', label: 'Definition' },
    { key: 'matchSignals', label: 'Match signals', type: 'aggregate' },
    { key: 'canonicalReferences', label: 'Canonical references', type: 'aggregate' }
  ],
  handoff: [
    { key: 'title', label: 'Rule' },
    { key: 'routeTo', label: 'Route to' },
    { key: 'useWhen', label: 'Use when', type: 'aggregate' },
    { key: 'requiredEvidence', label: 'Required evidence', type: 'aggregate' },
    { key: 'expectedFirstAction', label: 'Expected first action' },
    { key: 'partnerTeams', label: 'Partner teams', type: 'aggregate' }
  ]
};

@Component({
  selector: 'app-context-home-page',
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    RouterLink,
    RouterLinkActive,
    ContextCatalogTableComponent,
    ContextEntityDrawerComponent,
    ExplainableCellComponent,
    WhyPopoverComponent
  ],
  templateUrl: './context-home-page.html',
  styleUrl: './context-home-page.scss'
})
export class ContextHomePageComponent {
  private readonly api = inject(OperationalContextApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly tabs = TABS;
  readonly selectedTab = signal<ContextTab>('overview');
  readonly summary = signal<OperationalContextSummaryDto | null>(null);
  readonly data = signal<ContextDataState>(EMPTY_STATE);
  readonly isLoading = signal(true);
  readonly errorMessage = signal('');
  readonly detail = signal<OperationalContextEntityDetailDto | null>(null);
  readonly detailError = signal('');
  readonly searchResults = signal<OperationalContextSearchResultDto[]>([]);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly localFilterControl = new FormControl('', { nonNullable: true });
  readonly onlyWarningsControl = new FormControl(false, { nonNullable: true });
  readonly onlyMissingOwnerControl = new FormControl(false, { nonNullable: true });
  readonly onlyOpenQuestionsControl = new FormControl(false, { nonNullable: true });
  readonly localFilter = signal('');
  readonly onlyWarnings = signal(false);
  readonly onlyMissingOwner = signal(false);
  readonly onlyOpenQuestions = signal(false);

  readonly currentColumns = computed(() => COLUMNS[this.selectedTab()] ?? []);
  readonly currentRows = computed(() => this.filteredRows(this.selectedTab()));
  readonly tableColumnCount = computed(() => Math.max(this.currentColumns().length, 1));
  readonly statusLabel = computed(() => this.summary()?.catalogStatus || 'loading');
  readonly statusText = computed(() => this.formatStatus(this.statusLabel()));
  readonly isIncomplete = computed(() => {
    const status = this.summary()?.catalogStatus;
    return status === 'empty' || status === 'partial';
  });

  constructor() {
    this.localFilterControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.localFilter.set(value));
    this.onlyWarningsControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.onlyWarnings.set(value));
    this.onlyMissingOwnerControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.onlyMissingOwner.set(value));
    this.onlyOpenQuestionsControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.onlyOpenQuestions.set(value));
    this.loadCatalogue();
  }

  selectTab(tab: ContextTab): void {
    this.selectedTab.set(tab);
  }

  runSignalSearch(event?: Event): void {
    event?.preventDefault();
    const query = this.searchControl.value.trim();
    if (!query) {
      this.searchResults.set([]);
      return;
    }

    this.api
      .search(query)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (results) => this.searchResults.set(results),
        error: () => this.errorMessage.set('Could not search operational context.')
      });
  }

  openRow(row: Record<string, unknown>): void {
    const id = this.rowId(row);
    const type = this.entityTypeForTab(this.selectedTab());
    if (!id || !type) {
      return;
    }
    this.openEntity({ type, id });
  }

  openEntity(target: { type: string; id: string }): void {
    if (!target.type || !target.id) {
      return;
    }
    if (target.type === 'validation') {
      this.selectTab('validation');
      return;
    }
    if (target.type === 'open-question') {
      this.selectTab('open-questions');
      return;
    }
    if (
      ![
        'system',
        'repository',
        'code-search-scope',
        'process',
        'integration',
        'bounded-context',
        'team',
        'glossary-term',
        'handoff-rule'
      ].includes(target.type)
    ) {
      return;
    }

    this.detailError.set('');
    this.api
      .getEntity(target.type, target.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => this.detail.set(detail),
        error: () => this.detailError.set(`Could not load ${target.type}/${target.id}.`)
      });
  }

  openSearchResult(result: OperationalContextSearchResultDto): void {
    this.openEntity({ type: result.type, id: result.id });
  }

  closeDrawer(): void {
    this.detail.set(null);
    this.detailError.set('');
  }

  protected validationRows(): ValidationFindingDto[] {
    const query = normalize(this.localFilter());
    return this.data()
      .validation.filter((finding) => {
        if (query && !normalize(JSON.stringify(finding)).includes(query)) {
          return false;
        }
        if (this.onlyWarnings() && !this.isWarningSeverity(finding.severity)) {
          return false;
        }
        return true;
      });
  }

  protected openQuestionRows(): OpenQuestionDto[] {
    const query = normalize(this.localFilter());
    return this.data()
      .openQuestions.filter((question) => {
        if (query && !normalize(JSON.stringify(question)).includes(query)) {
          return false;
        }
        if (this.onlyWarnings() && !this.isWarningSeverity(question.severity)) {
          return false;
        }
        return true;
      });
  }

  protected rowId(row: Record<string, unknown>): string {
    return String(row['id'] || '');
  }

  protected aggregate(row: Record<string, unknown>, key: string): ExplainableAggregateDto | null {
    const value = row[key] as ExplainableAggregateDto | null;
    return value && typeof value === 'object' && 'count' in value ? value : null;
  }

  protected validationStatusClass(severity: string): string {
    return `validation-severity validation-severity--${severity || 'info'}`;
  }

  protected selectedTabLabel(): string {
    return this.tabs.find((tab) => tab.id === this.selectedTab())?.label ?? '';
  }

  protected supportsWarningFilter(): boolean {
    return !['glossary', 'handoff'].includes(this.selectedTab());
  }

  protected supportsMissingOwnerFilter(): boolean {
    return ['systems', 'repositories', 'processes', 'integrations', 'bounded-contexts'].includes(this.selectedTab());
  }

  protected supportsOpenQuestionsFilter(): boolean {
    return this.selectedTab() === 'systems';
  }

  private loadCatalogue(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');

    forkJoin({
      summary: this.api.getSummary(),
      systems: this.api.getSystems(),
      repositories: this.api.getRepositories(),
      codeSearchScopes: this.api.getCodeSearchScopes(),
      processes: this.api.getProcesses(),
      integrations: this.api.getIntegrations(),
      boundedContexts: this.api.getBoundedContexts(),
      teams: this.api.getTeams(),
      glossary: this.api.getGlossary(),
      handoffRules: this.api.getHandoffRules(),
      validation: this.api.getValidation(),
      openQuestions: this.api.getOpenQuestions()
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.summary.set(response.summary);
          this.data.set({
            systems: response.systems,
            repositories: response.repositories,
            codeSearchScopes: response.codeSearchScopes,
            processes: response.processes,
            integrations: response.integrations,
            boundedContexts: response.boundedContexts,
            teams: response.teams,
            glossary: response.glossary,
            handoffRules: response.handoffRules,
            validation: response.validation,
            openQuestions: response.openQuestions
          });
          this.isLoading.set(false);
        },
        error: () => {
          this.errorMessage.set('Could not load operational context catalogue.');
          this.isLoading.set(false);
        }
      });
  }

  private filteredRows(tab: ContextTab): Array<Record<string, unknown>> {
    const rows = this.rowsForTab(tab).map((row) => row as unknown as Record<string, unknown>);
    const query = normalize(this.localFilter());
    return rows.filter((row) => {
      if (query && !normalize(JSON.stringify(row)).includes(query)) {
        return false;
      }
      if (this.supportsWarningFilter() && this.onlyWarnings() && !this.rowHasWarning(row)) {
        return false;
      }
      if (this.supportsMissingOwnerFilter() && this.onlyMissingOwner() && !this.rowMissingOwner(row)) {
        return false;
      }
      if (this.supportsOpenQuestionsFilter() && this.onlyOpenQuestions() && !this.rowHasOpenQuestions(row)) {
        return false;
      }
      return true;
    });
  }

  private rowsForTab(tab: ContextTab): OperationalContextCatalogRow[] {
    const data = this.data();
    switch (tab) {
      case 'systems':
        return data.systems;
      case 'repositories':
        return data.repositories;
      case 'code-search-scopes':
        return data.codeSearchScopes;
      case 'processes':
        return data.processes;
      case 'integrations':
        return data.integrations;
      case 'bounded-contexts':
        return data.boundedContexts;
      case 'teams':
        return data.teams;
      case 'glossary':
        return data.glossary;
      case 'handoff':
        return data.handoffRules;
      default:
        return [];
    }
  }

  protected entityTypeForTab(tab: ContextTab): string {
    switch (tab) {
      case 'systems':
        return 'system';
      case 'repositories':
        return 'repository';
      case 'code-search-scopes':
        return 'code-search-scope';
      case 'processes':
        return 'process';
      case 'integrations':
        return 'integration';
      case 'bounded-contexts':
        return 'bounded-context';
      case 'teams':
        return 'team';
      case 'glossary':
        return 'glossary-term';
      case 'handoff':
        return 'handoff-rule';
      default:
        return '';
    }
  }

  private rowHasWarning(row: Record<string, unknown>): boolean {
    return Object.values(row).some(
      (value) =>
        value &&
        typeof value === 'object' &&
        'severity' in value &&
        this.isWarningSeverity(String((value as { severity?: string }).severity))
    );
  }

  private rowMissingOwner(row: Record<string, unknown>): boolean {
    const owner = row['owner'] as { label?: string; value?: string } | undefined;
    return Boolean(owner && (!owner.value || owner.label === 'Missing owner'));
  }

  private rowHasOpenQuestions(row: Record<string, unknown>): boolean {
    const openQuestions = row['openQuestions'] as { count?: number } | undefined;
    return Number(openQuestions?.count || 0) > 0;
  }

  private formatStatus(status: string): string {
    switch (status) {
      case 'hasIssues':
        return 'Has issues';
      case 'ready':
        return 'Ready';
      case 'partial':
        return 'Partial';
      case 'empty':
        return 'Empty';
      default:
        return status || 'Loading';
    }
  }

  private isWarningSeverity(severity: string): boolean {
    return ['warning', 'error'].includes(String(severity || '').toLowerCase());
  }
}

function normalize(value: string): string {
  return value.trim().toLowerCase();
}
