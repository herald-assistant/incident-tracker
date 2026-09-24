import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NgTemplateOutlet } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { catchError, forkJoin, map, Observable, of } from 'rxjs';

import {
  ExplainableAggregateDto,
  OpenQuestionDto,
  OperationalContextAiApiPreviewEndpoint,
  OperationalContextAiSearchPreview,
  OperationalContextCatalogRow,
  OperationalContextEntityDetailDto,
  OperationalContextReadModelProfile,
  OperationalContextSearchResultDto,
  SourceReferenceDto,
  OperationalContextSummaryDto,
  ValidationFindingDto
} from '../../models/operational-context.models';
import { OperationalContextApiService } from '../../services/operational-context-api.service';
import { OperationalContextMaintenanceApiService } from '../../services/operational-context-maintenance-api.service';
import { AiApiPreviewPanelComponent } from '../../components/ai-api-preview-panel/ai-api-preview-panel';
import {
  ContextCatalogColumn,
  ContextCatalogTableComponent
} from '../../components/context-catalog-table/context-catalog-table';
import { ContextEntityDrawerComponent } from '../../components/context-entity-drawer/context-entity-drawer';
import { ContextEntityEditorDrawerComponent } from '../../components/context-entity-editor-drawer/context-entity-editor-drawer';
import { ContextDeleteConfirmationComponent } from '../../components/context-delete-confirmation/context-delete-confirmation';
import { ContextAssistancePanelComponent } from '../../components/context-assistance-panel/context-assistance-panel';
import { WhyPopoverComponent } from '../../components/why-popover/why-popover';
import { copyTextToClipboard } from '../../../core/utils/clipboard.utils';
import { rememberLocalRunId } from '../../../core/utils/local-run-route.utils';
import { OperationalContextMaintenanceFacade } from '../../services/operational-context-maintenance.facade';
import {
  isOperationalContextWritableType,
  OperationalContextEditorState,
  OperationalContextInboundReference,
  OperationalContextReferenceOption,
  OperationalContextReferenceOptions,
  OperationalContextWritableType
} from '../../models/operational-context-maintenance.models';
import {
  OperationalContextAssistanceJob,
  OperationalContextAssistancePrefill,
  isTerminalAssistanceStatus
} from '../../models/operational-context-assistance.models';
import { AnalysisRunHistoryApiService } from '../../../core/services/analysis-run-history-api.service';
import { OperationalContextAssistanceApiService } from '../../services/operational-context-assistance-api.service';

type ContextTab =
  | 'overview'
  | 'assistance'
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

interface ContextTableHeader {
  label: string;
  tooltip: string;
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

function column(
  key: string,
  label: string,
  tooltip: string,
  type?: ContextCatalogColumn['type']
): ContextCatalogColumn {
  return type ? { key, label, tooltip, type } : { key, label, tooltip };
}

function header(label: string, tooltip: string): ContextTableHeader {
  return { label, tooltip };
}

const COLUMNS: Record<string, ContextCatalogColumn[]> = {
  systems: [
    column(
      'name',
      'System',
      'Nazwa systemu opisanego w katalogu. Pod nią znajdziesz jego powiązania, odpowiedzialność i sygnały rozpoznania.'
    ),
    column(
      'systemType',
      'Type',
      'Rodzaj systemu, np. usługa wewnętrzna, platforma lub system zewnętrzny.'
    ),
    column(
      'systemSubtype',
      'Subtype',
      'Dodatkowy rodzaj usługi wewnętrznej, np. frontend, backend lub worker. Wartość unknown oznacza, że klasyfikacja nie została potwierdzona.'
    ),
    column(
      'owner',
      'Owner',
      'Strona odpowiedzialna ustalona z danych o systemie lub jego obszarze domenowym. Brak właściciela oznacza, że katalog nie pozwala go potwierdzić.',
      'owner'
    ),
    column(
      'repositories',
      'Repositories',
      'Liczba repozytoriów przypisanych do zakresu wyszukiwania kodu tego systemu. Rozwiń, aby zobaczyć projekty.',
      'aggregate'
    ),
    column(
      'relations',
      'Relations',
      'Powiązania systemu z innymi pozycjami katalogu, np. procesami, integracjami i zespołami.',
      'aggregate'
    ),
    column(
      'signals',
      'Signals',
      'Nazwy, aliasy i inne trwałe wskazówki pomagające rozpoznać system w pytaniach lub danych operacyjnych.',
      'aggregate'
    ),
    column(
      'handoffReadiness',
      'Resolved handoff',
      'Właściciel i ewentualni partnerzy ustaleni z katalogu. Szczegóły pokazują, skąd wynika przypisanie i czego nie udało się potwierdzić.',
      'aggregate'
    ),
    column(
      'validation',
      'Status',
      'Liczba uwag dotyczących jakości wpisu systemu, np. brakujących lub niespójnych powiązań.',
      'aggregate'
    )
  ],
  repositories: [
    column(
      'project',
      'Repository',
      'Projekt GitLab opisany w katalogu. Otwórz go, aby sprawdzić powiązania i dane projektu.'
    ),
    column(
      'owner',
      'Owner',
      'Strona odpowiedzialna wywnioskowana z systemu lub obszaru domenowego powiązanego z repozytorium.',
      'owner'
    ),
    column(
      'systems',
      'Systems',
      'Systemy powiązane z tym repozytorium. Rozwiń, aby zobaczyć ich nazwy.',
      'aggregate'
    ),
    column(
      'contexts',
      'Contexts',
      'Obszary domenowe powiązane z kodem tego repozytorium.',
      'aggregate'
    ),
    column(
      'processes',
      'Processes',
      'Procesy, których kod lub konfiguracja może znajdować się w tym repozytorium.',
      'aggregate'
    ),
    column(
      'integrations',
      'Integrations',
      'Integracje powiązane z tym repozytorium.',
      'aggregate'
    ),
    column(
      'codeSearchScopes',
      'Search scopes',
      'Zakresy, w których kod tego repozytorium jest przeszukiwany razem z innymi projektami.',
      'aggregate'
    ),
    column(
      'codeSearchRoles',
      'Scope roles',
      'Rola repozytorium w każdym zakresie wyszukiwania kodu, np. główny projekt lub biblioteka.',
      'aggregate'
    ),
    column(
      'handoffReadiness',
      'Resolved handoff',
      'Strona odpowiedzialna ustalona przez powiązania repozytorium z systemem lub obszarem domenowym.',
      'aggregate'
    ),
    column(
      'validation',
      'Status',
      'Liczba uwag dotyczących danych repozytorium, np. niepełnej identyfikacji projektu lub powiązań.',
      'aggregate'
    )
  ],
  'code-search-scopes': [
    column(
      'name',
      'Scope',
      'Nazwa zestawu repozytoriów, w których należy szukać kodu wskazanego systemu lub obszaru domenowego.'
    ),
    column(
      'scopeType',
      'Type',
      'Rodzaj pozycji katalogu, której kod obejmuje ten zakres.'
    ),
    column(
      'lifecycleStatus',
      'Lifecycle',
      'Informacja, czy zakres wyszukiwania kodu jest aktywny, planowany lub wycofany.'
    ),
    column(
      'target',
      'Target',
      'System albo obszar domenowy, którego kod ma być odnaleziony w podanych repozytoriach.',
      'aggregate'
    ),
    column(
      'repositories',
      'Repositories',
      'Projekty należące do tego zakresu, wraz z rolą i kolejnością ich sprawdzania.',
      'aggregate'
    ),
    column(
      'searchBoundary',
      'Search boundary',
      'Część każdego repozytorium objęta wyszukiwaniem: cały projekt albo wybrane katalogi.',
      'aggregate'
    ),
    column(
      'limitations',
      'Limitations',
      'Znane braki w tym zakresie, np. kod strony zewnętrznej, do którego nie ma dostępu.',
      'aggregate'
    ),
    column(
      'validation',
      'Status',
      'Liczba uwag dotyczących kompletności i spójności zakresu wyszukiwania kodu.',
      'aggregate'
    )
  ],
  processes: [
    column(
      'name',
      'Process',
      'Nazwa procesu opisanego w katalogu, np. obsługi żądania lub przetwarzania zdarzenia.'
    ),
    column(
      'owner',
      'Owner',
      'Strona odpowiedzialna ustalona z systemu lub obszaru domenowego powiązanego z procesem.',
      'owner'
    ),
    column(
      'systems',
      'Systems',
      'Systemy uczestniczące w procesie, w tym główne i wspierające.',
      'aggregate'
    ),
    column(
      'externalSystems',
      'External systems',
      'Systemy lub partnerzy spoza lokalnego obszaru, którzy uczestniczą w procesie.',
      'aggregate'
    ),
    column(
      'repositories',
      'Repositories',
      'Repozytoria powiązane z kodem lub konfiguracją procesu.',
      'aggregate'
    ),
    column(
      'contexts',
      'Contexts',
      'Obszary domenowe, których funkcji dotyczy proces.',
      'aggregate'
    ),
    column(
      'steps',
      'Steps',
      'Opisane etapy procesu. Rozwiń, aby zobaczyć ich kolejność i powiązania.',
      'aggregate'
    ),
    column(
      'completionSignals',
      'Completion signals',
      'Statusy, zdarzenia lub inne oznaki pozwalające ocenić, jak proces się zakończył.',
      'aggregate'
    ),
    column(
      'validation',
      'Status',
      'Liczba uwag dotyczących opisu procesu i jego powiązań.',
      'aggregate'
    )
  ],
  integrations: [
    column(
      'name',
      'Integration',
      'Nazwa połączenia lub wymiany danych między systemami.'
    ),
    column(
      'sourceSystem',
      'Source',
      'System, z którego wychodzi komunikacja w tej integracji.'
    ),
    column(
      'targetSystems',
      'Targets',
      'Systemy odbierające komunikację, także te znajdujące się za pośrednikiem.'
    ),
    column(
      'category',
      'Category',
      'Rodzaj połączenia, np. zależność lokalna, połączenie z partnerem lub brama.'
    ),
    column(
      'integrationStyle',
      'Style',
      'Sposób wymiany danych, np. żądanie synchroniczne, zdarzenie lub przetwarzanie wsadowe.'
    ),
    column(
      'flowDirection',
      'Direction',
      'Kierunek przepływu danych z perspektywy systemu źródłowego.'
    ),
    column(
      'owner',
      'Owner',
      'Główna strona odpowiedzialna ustalona z systemów lub obszarów domenowych uczestniczących w integracji.',
      'owner'
    ),
    column(
      'partnerOwners',
      'Partner owners',
      'Pozostałe strony, które mogą być potrzebne przy wyjaśnianiu problemu na granicy systemów.',
      'aggregate'
    ),
    column(
      'processes',
      'Processes',
      'Procesy korzystające z tej integracji.',
      'aggregate'
    ),
    column(
      'contexts',
      'Contexts',
      'Obszary domenowe powiązane z tą integracją.',
      'aggregate'
    ),
    column(
      'signals',
      'Signals',
      'Nazwy, aliasy i inne trwałe wskazówki pomagające rozpoznać tę integrację.',
      'aggregate'
    ),
    column(
      'handoffReadiness',
      'Resolved handoff',
      'Główna strona odpowiedzialna, partnerzy i znane ograniczenia informacji o tej integracji.',
      'aggregate'
    ),
    column(
      'validation',
      'Status',
      'Liczba uwag dotyczących opisu integracji, jej stron lub powiązań.',
      'aggregate'
    )
  ],
  'bounded-contexts': [
    column(
      'name',
      'Context',
      'Nazwa obszaru domenowego, w którym określone pojęcia i reguły mają wspólne znaczenie.'
    ),
    column(
      'owner',
      'Owner',
      'Strona odpowiedzialna za ten obszar domenowy. Jej jawne przypisanie ma pierwszeństwo przed właścicielem systemu.',
      'owner'
    ),
    column(
      'systems',
      'Systems',
      'Systemy, które realizują lub wykorzystują funkcje tego obszaru.',
      'aggregate'
    ),
    column(
      'terms',
      'Terms',
      'Terminy słownika opisujące pojęcia używane w tym obszarze.',
      'aggregate'
    ),
    column(
      'relations',
      'Relations',
      'Powiązania z innymi obszarami domenowymi, procesami i integracjami.',
      'aggregate'
    ),
    column(
      'validation',
      'Status',
      'Liczba uwag dotyczących opisu i powiązań tego obszaru domenowego.',
      'aggregate'
    )
  ],
  teams: [
    column(
      'name',
      'Team',
      'Nazwa zespołu lub strony odpowiedzialnej zapisanej w katalogu.'
    ),
    column(
      'ownsSystems',
      'Systems',
      'Systemy, przy których ten zespół jest wskazany jako odpowiedzialny.',
      'aggregate'
    ),
    column(
      'ownsRepositories',
      'Repositories',
      'Repozytoria powiązane z odpowiedzialnością tego zespołu.',
      'aggregate'
    ),
    column(
      'ownsProcesses',
      'Processes',
      'Procesy powiązane z odpowiedzialnością lub udziałem tego zespołu.',
      'aggregate'
    ),
    column(
      'ownsContexts',
      'Contexts',
      'Obszary domenowe, za które ten zespół odpowiada.',
      'aggregate'
    ),
    column(
      'ownsIntegrations',
      'Integrations',
      'Integracje, przy których ten zespół występuje jako strona odpowiedzialna lub partner.',
      'aggregate'
    ),
    column(
      'handoffReadiness',
      'Handoff',
      'Powiązane informacje o odpowiedzialności i przekazywaniu spraw dotyczących tego zespołu.',
      'aggregate'
    ),
    column(
      'validation',
      'Issues',
      'Liczba uwag dotyczących wpisu zespołu i jego powiązań.',
      'aggregate'
    )
  ],
  glossary: [
    column(
      'term',
      'Term',
      'Słowo, skrót lub zwrot opisany w słowniku katalogu.'
    ),
    column(
      'category',
      'Category',
      'Rodzaj terminu, np. pojęcie biznesowe, status lub nazwa techniczna.'
    ),
    column(
      'definition',
      'Definition',
      'Wyjaśnienie znaczenia terminu. Definicja nie jest dowodem przyczyny bieżącego problemu.'
    ),
    column(
      'matchSignals',
      'Recognition signals',
      'Inne zapisy i zwroty, po których można rozpoznać ten termin.',
      'aggregate'
    ),
    column(
      'canonicalReferences',
      'Canonical references',
      'Pozycje katalogu, w których ten termin ma określone znaczenie.',
      'aggregate'
    )
  ],
  handoff: [
    column(
      'title',
      'Rule',
      'Nazwa reguły określającej, kiedy i jak przekazać sprawę dalej.'
    ),
    column(
      'useWhen',
      'Use when',
      'Warunki, które powinny być spełnione przed użyciem tej reguły.',
      'aggregate'
    ),
    column(
      'requiredEvidence',
      'Required evidence',
      'Informacje, które trzeba zebrać przed przekazaniem sprawy, aby odbiorca mógł ją sprawdzić.',
      'aggregate'
    ),
    column(
      'expectedFirstAction',
      'Expected first action',
      'Pierwsze konkretne działanie oczekiwane od osoby, która przejmie sprawę.'
    )
  ]
};

const OVERVIEW_COLUMNS: ContextTableHeader[] = [
  header(
    'Obszar',
    'Część katalogu, której dotyczy ten wiersz, np. systemy, procesy lub zespoły.'
  ),
  header(
    'Liczba pozycji',
    'Liczba wpisów zapisanych w tej części katalogu. Sama liczba nie oznacza, że dane są kompletne.'
  ),
  header(
    'Stan',
    'Dla zwykłych obszarów pokazuje, czy są w nich wpisy. W wierszu walidacji pokazuje, czy wykryto uwagi. Nie potwierdza kompletności danych.'
  ),
  header(
    'Wyjaśnienie',
    'Otwórz, aby zobaczyć, co jest liczone w tym wierszu.'
  )
];

const SIGNAL_RESOLVER_COLUMNS: ContextTableHeader[] = [
  header(
    'Match',
    'Pozycja katalogu, która pasuje do wpisanego tekstu.'
  ),
  header(
    'Type',
    'Rodzaj znalezionej pozycji, np. system, proces lub termin.'
  ),
  header(
    'Confidence',
    'Siła dopasowania do zapytania. Słabszy wynik warto sprawdzić w szczegółach pozycji.'
  ),
  header(
    'Why matched',
    'Pola katalogu, które pasowały do zapytania. Otwórz, aby sprawdzić podstawę dopasowania.'
  ),
  header(
    'Actions',
    'Otwórz szczegóły znalezionej pozycji katalogu.'
  )
];

const VALIDATION_COLUMNS: ContextTableHeader[] = [
  header(
    'Severity',
    'Priorytet uwagi: error wymaga poprawy, warning wskazuje ryzyko, a info ma charakter informacyjny.'
  ),
  header(
    'Category',
    'Rodzaj wykrytego problemu, np. brakujące powiązanie lub niepełny opis odpowiedzialności.'
  ),
  header(
    'Entity',
    'Pozycja katalogu, której dotyczy uwaga.'
  ),
  header(
    'Problem',
    'Opis braku lub niespójności wykrytej w katalogu.'
  ),
  header(
    'Suggested fix',
    'Wskazówka, jakie dane uzupełnić lub poprawić.'
  ),
  header(
    'Impact',
    'Możliwy skutek pozostawienia tej nieścisłości w katalogu.'
  ),
  header(
    'Maintenance target',
    'Plik i pozycja katalogu, w których można sprawdzić lub poprawić opisany problem.'
  ),
  header(
    'Actions',
    'Otwórz pozycję lub skopiuj miejsce wymagające poprawy.'
  )
];

const OPEN_QUESTION_COLUMNS: ContextTableHeader[] = [
  header(
    'Question',
    'Nierozstrzygnięta kwestia dotycząca danych katalogu.'
  ),
  header(
    'Maintenance target',
    'Plik i pozycja katalogu związane z pytaniem.'
  ),
  header(
    'Entity',
    'Pozycja katalogu, której dotyczy pytanie.'
  ),
  header(
    'Severity',
    'Priorytet wyjaśnienia pytania. Wyższa waga oznacza większy wpływ brakującej informacji.'
  ),
  header(
    'Status',
    'Informacja, czy pytanie pozostaje otwarte, czy zostało już wyjaśnione.'
  ),
  header(
    'Actions',
    'Skopiuj miejsce wymagające wyjaśnienia lub otwórz powiązaną pozycję.'
  )
];

@Component({
  selector: 'app-context-home-page',
  imports: [
    ReactiveFormsModule,
    NgTemplateOutlet,
    MatIconModule,
    MatTooltipModule,
    AiApiPreviewPanelComponent,
    ContextCatalogTableComponent,
    ContextEntityDrawerComponent,
    ContextEntityEditorDrawerComponent,
    ContextDeleteConfirmationComponent,
    ContextAssistancePanelComponent,
    WhyPopoverComponent
  ],
  templateUrl: './context-home-page.html',
  styleUrl: './context-home-page.scss'
})
export class ContextHomePageComponent {
  private readonly api = inject(OperationalContextApiService);
  private readonly maintenanceApi = inject(OperationalContextMaintenanceApiService);
  private readonly historyApi = inject(AnalysisRunHistoryApiService);
  private readonly assistanceApi = inject(OperationalContextAssistanceApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private requestedHistoryRunId = '';
  readonly maintenance = inject(OperationalContextMaintenanceFacade);
  private readonly destroyRef = inject(DestroyRef);

  readonly tabs = TABS;
  readonly overviewColumns = OVERVIEW_COLUMNS;
  readonly signalResolverColumns = SIGNAL_RESOLVER_COLUMNS;
  readonly validationColumns = VALIDATION_COLUMNS;
  readonly openQuestionColumns = OPEN_QUESTION_COLUMNS;
  readonly selectedTab = signal<ContextTab>('overview');
  readonly assistancePrefill = signal<OperationalContextAssistancePrefill>({ mode: 'CREATE_AREA' });
  readonly assistanceJob = signal<OperationalContextAssistanceJob | null>(null);
  readonly assistanceMounted = signal(false);
  readonly assistanceHistoryReadOnly = signal(false);
  readonly assistanceHistoryLoading = signal(false);
  readonly assistanceHistoryError = signal('');
  readonly editorFocusPath = signal<string | null>(null);
  readonly summary = signal<OperationalContextSummaryDto | null>(null);
  readonly data = signal<ContextDataState>(EMPTY_STATE);
  readonly isLoading = signal(true);
  readonly errorMessage = signal('');
  readonly detail = signal<OperationalContextEntityDetailDto | null>(null);
  readonly detailError = signal('');
  readonly entityPreview = signal<OperationalContextEditorState | null>(null);
  readonly entityPreviewLoading = signal(false);
  readonly entityPreviewError = signal('');
  readonly searchResults = signal<OperationalContextSearchResultDto[]>([]);
  readonly searchAiApiPreview = signal<OperationalContextAiSearchPreview | null>(null);
  readonly searchAiApiPreviewLoading = signal(false);
  readonly searchAiApiPreviewError = signal('');
  readonly searchAiApiPreviewProfile = signal<OperationalContextReadModelProfile>('default');
  private readonly searchAiApiPreviewQuery = signal('');
  private readonly selectedEntityTarget = signal<{ type: string; id: string } | null>(null);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly localFilterControl = new FormControl('', { nonNullable: true });
  readonly onlyWarningsControl = new FormControl(false, { nonNullable: true });
  readonly onlyMissingOwnerControl = new FormControl(false, { nonNullable: true });
  readonly onlyOpenQuestionsControl = new FormControl(false, { nonNullable: true });
  readonly validationSeverityControl = new FormControl('', { nonNullable: true });
  readonly validationCategoryControl = new FormControl('', { nonNullable: true });
  readonly validationEntityTypeControl = new FormControl('', { nonNullable: true });
  readonly validationSourceFileControl = new FormControl('', { nonNullable: true });
  readonly questionSeverityControl = new FormControl('', { nonNullable: true });
  readonly questionEntityTypeControl = new FormControl('', { nonNullable: true });
  readonly questionSourceFileControl = new FormControl('', { nonNullable: true });
  readonly questionStatusControl = new FormControl('', { nonNullable: true });
  readonly localFilter = signal('');
  readonly onlyWarnings = signal(false);
  readonly onlyMissingOwner = signal(false);
  readonly onlyOpenQuestions = signal(false);
  readonly validationSeverity = signal('');
  readonly validationCategory = signal('');
  readonly validationEntityType = signal('');
  readonly validationSourceFile = signal('');
  readonly questionSeverity = signal('');
  readonly questionEntityType = signal('');
  readonly questionSourceFile = signal('');
  readonly questionStatus = signal('');
  readonly copiedMaintenanceTarget = signal('');
  readonly editorDirty = signal(false);
  readonly maintenanceNotice = signal('');

  readonly currentColumns = computed(() => COLUMNS[this.selectedTab()] ?? []);
  readonly currentRows = computed(() => this.filteredRows(this.selectedTab()));
  readonly referenceOptions = computed<OperationalContextReferenceOptions>(() => ({
    system: this.referenceOptionRows(this.data().systems, 'name'),
    repository: this.referenceOptionRows(this.data().repositories, 'project'),
    'code-search-scope': this.referenceOptionRows(this.data().codeSearchScopes, 'name'),
    process: this.referenceOptionRows(this.data().processes, 'name'),
    integration: this.referenceOptionRows(this.data().integrations, 'name'),
    'bounded-context': this.referenceOptionRows(this.data().boundedContexts, 'name'),
    team: this.referenceOptionRows(this.data().teams, 'name'),
    'glossary-term': this.referenceOptionRows(this.data().glossary, 'term'),
    'handoff-rule': this.referenceOptionRows(this.data().handoffRules, 'title')
  }));
  readonly tableColumnCount = computed(() => Math.max(this.currentColumns().length, 1));
  readonly searchAiApiPreviewEndpoints = computed<OperationalContextAiApiPreviewEndpoint[]>(() => {
    const preview = this.searchAiApiPreview();
    return preview
      ? [{
          key: 'search',
          label: 'Search',
          url: preview.url,
          payload: preview.payload,
          error: preview.error
        }]
      : [];
  });
  readonly validationSeverityOptions = computed(() =>
    this.uniqueValues(this.data().validation.map((finding) => finding.severity))
  );
  readonly validationCategoryOptions = computed(() =>
    this.uniqueValues(this.data().validation.map((finding) => finding.category))
  );
  readonly validationEntityTypeOptions = computed(() =>
    this.uniqueValues(this.data().validation.map((finding) => finding.entityType))
  );
  readonly validationSourceFileOptions = computed(() =>
    this.uniqueValues(this.data().validation.map((finding) => this.firstSourceRef(finding)?.file))
  );
  readonly questionSeverityOptions = computed(() =>
    this.uniqueValues(this.data().openQuestions.map((question) => question.severity))
  );
  readonly questionEntityTypeOptions = computed(() =>
    this.uniqueValues(this.data().openQuestions.map((question) => question.entityType))
  );
  readonly questionSourceFileOptions = computed(() =>
    this.uniqueValues(this.data().openQuestions.map((question) => question.sourceFile))
  );
  readonly questionStatusOptions = computed(() =>
    this.uniqueValues(this.data().openQuestions.map((question) => question.status))
  );
  readonly statusLabel = computed(() => this.summary()?.catalogStatus || 'loading');
  readonly statusText = computed(() => this.formatStatus(this.statusLabel()));
  readonly isIncomplete = computed(() => {
    const summary = this.summary();
    const status = summary?.catalogStatus;
    const indexedEntities = summary
      ? summary.systems
        + summary.repositories
        + summary.codeSearchScopes
        + summary.processes
        + summary.integrations
        + summary.boundedContexts
        + summary.teams
        + summary.glossaryTerms
        + summary.handoffRules
      : 0;
    return status === 'empty' || status === 'partial' || indexedEntities === 0;
  });
  readonly writableTypeForTab = computed<OperationalContextWritableType | null>(() => {
    const type = this.entityTypeForTab(this.selectedTab());
    return isOperationalContextWritableType(type) && this.maintenance.supports(type) ? type : null;
  });
  readonly selectedEntityWritable = computed(() => {
    const target = this.selectedEntityTarget();
    return Boolean(target && isOperationalContextWritableType(target.type) && this.maintenance.supports(target.type));
  });

  constructor() {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const localRunId = params.get('localRunId')?.trim();
        if (localRunId && this.assistanceJob()?.jobId !== localRunId) this.loadAssistanceHistoryRun(localRunId);
      });
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
    this.validationSeverityControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.validationSeverity.set(value));
    this.validationCategoryControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.validationCategory.set(value));
    this.validationEntityTypeControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.validationEntityType.set(value));
    this.validationSourceFileControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.validationSourceFile.set(value));
    this.questionSeverityControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.questionSeverity.set(value));
    this.questionEntityTypeControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.questionEntityType.set(value));
    this.questionSourceFileControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.questionSourceFile.set(value));
    this.questionStatusControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.questionStatus.set(value));
    this.maintenance.saved$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => {
        this.maintenanceNotice.set(`${event.entity.type}/${event.entity.id} saved.`);
        this.reloadAfterMutation({ type: event.entity.type, id: event.entity.id });
      });
    this.maintenance.deleted$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => {
        this.maintenanceNotice.set(`${event.type}/${event.id} deleted.`);
        this.reloadAfterMutation(null);
      });
    this.maintenance.loadCapabilities();
    this.loadCatalogue();
  }

  selectTab(tab: ContextTab): void {
    if (!this.confirmDiscard()) return;
    if (tab === 'assistance') this.assistanceMounted.set(true);
    this.selectedTab.set(tab);
  }

  addEntity(): void {
    const type = this.writableTypeForTab();
    if (type && this.confirmDiscard()) {
      this.editorFocusPath.set(null);
      this.maintenanceNotice.set('');
      this.maintenance.openCreate(type);
    }
  }

  editSelectedEntity(): void {
    const target = this.selectedEntityTarget();
    if (target && isOperationalContextWritableType(target.type) && this.confirmDiscard()) {
      this.editorFocusPath.set(null);
      this.maintenanceNotice.set('');
      this.maintenance.openEdit(target.type, target.id);
    }
  }

  deleteSelectedEntity(): void {
    const target = this.selectedEntityTarget();
    if (target && isOperationalContextWritableType(target.type)) this.maintenance.requestDelete(target.type, target.id);
  }

  cancelEditor(): void {
    if (!this.confirmDiscard()) return;
    this.editorDirty.set(false);
    this.maintenance.closeEditor();
  }

  editSource(type: string, id: string | null | undefined, focusPath: string | null = null): void {
    if (id && isOperationalContextWritableType(type) && this.maintenance.supports(type) && this.confirmDiscard()) {
      this.editorFocusPath.set(focusPath);
      this.maintenanceNotice.set('');
      this.maintenance.openEdit(type, id);
    }
  }

  canEditSource(type: string, id: string | null | undefined): boolean {
    return Boolean(id && isOperationalContextWritableType(type) && this.maintenance.supports(type));
  }

  openDeleteReference(reference: OperationalContextInboundReference): void {
    this.maintenance.cancelDelete();
    this.openEntity({ type: reference.sourceType, id: reference.sourceId });
  }

  runSignalSearch(event?: Event): void {
    event?.preventDefault();
    const query = this.searchControl.value.trim();
    if (!query) {
      this.searchResults.set([]);
      this.resetSearchAiApiPreview();
      return;
    }

    this.searchAiApiPreviewProfile.set('default');
    this.searchAiApiPreviewQuery.set(query);
    this.loadSearchAiApiPreview('default');
    this.api
      .search(query)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (results) => this.searchResults.set(results),
        error: () => this.errorMessage.set('Could not search operational context.')
      });
  }

  loadSearchAiApiPreview(profile: OperationalContextReadModelProfile): void {
    const query = this.searchAiApiPreviewQuery() || this.searchControl.value.trim();
    if (!query) {
      this.resetSearchAiApiPreview();
      return;
    }

    this.searchAiApiPreviewQuery.set(query);
    this.searchAiApiPreviewProfile.set(profile);
    this.searchAiApiPreviewLoading.set(true);
    this.searchAiApiPreviewError.set('');
    this.api
      .getProfiledSearch(query, profile)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (payload) => {
          this.searchAiApiPreview.set({
            query,
            profile,
            url: this.api.profiledSearchUrl(query, profile),
            payload,
            error: null
          });
          this.searchAiApiPreviewLoading.set(false);
        },
        error: () => {
          this.searchAiApiPreview.set({
            query,
            profile,
            url: this.api.profiledSearchUrl(query, profile),
            payload: null,
            error: 'Could not load search AI API preview.'
          });
          this.searchAiApiPreviewError.set('Could not load search AI API preview.');
          this.searchAiApiPreviewLoading.set(false);
        }
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
    if (!this.confirmDiscard()) return;
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
    this.entityPreview.set(null);
    this.entityPreviewError.set('');
    this.entityPreviewLoading.set(this.canLoadEntityPreview(target));
    this.selectedEntityTarget.set(target);
    forkJoin({
      detail: this.api.getEntity(target.type, target.id),
      entityPreview: this.entityPreviewFor(target)
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ detail, entityPreview }) => {
          this.detail.set(detail);
          this.entityPreview.set(entityPreview);
          this.entityPreviewLoading.set(false);
        },
        error: () => {
          this.entityPreviewLoading.set(false);
          this.detailError.set(`Could not load ${target.type}/${target.id}.`);
        }
      });
  }

  startAreaAssistance(): void {
    this.openAssistance({ mode: 'CREATE_AREA' });
  }

  assistSelectedEntity(): void {
    const target = this.selectedEntityTarget();
    if (!target || !isOperationalContextWritableType(target.type)) return;
    this.openAssistance({
      mode: 'IMPROVE_ENTITY',
      target: { kind: 'ENTITY', entityType: target.type, entityId: target.id }
    });
  }

  assistValidationFinding(finding: ValidationFindingDto): void {
    if (!this.canAssistSource(finding.entityType, finding.entityId)) return;
    this.openAssistance({
      mode: 'RESOLVE_FINDING',
      target: {
        kind: 'VALIDATION_FINDING', entityType: finding.entityType as OperationalContextWritableType,
        entityId: finding.entityId, id: finding.id
      },
      description: `Wyjaśnij finding „${finding.title}”. ${finding.detail} Sugerowana poprawka: ${finding.suggestedFix}`.trim()
    });
  }

  assistOpenQuestion(question: OpenQuestionDto): void {
    if (!this.canAssistSource(question.entityType, question.entityId)) return;
    this.openAssistance({
      mode: 'RESOLVE_FINDING',
      target: {
        kind: 'OPEN_QUESTION', entityType: question.entityType as OperationalContextWritableType,
        entityId: question.entityId!, id: question.id
      },
      description: `Pomóż odpowiedzieć na otwarte pytanie: ${question.question}`
    });
  }

  canAssistSource(type: string, id: string | null | undefined): boolean {
    return Boolean(id && isOperationalContextWritableType(type));
  }

  editValidationSource(finding: ValidationFindingDto): void {
    this.editSource(finding.entityType, finding.entityId, this.findingFieldPath(finding));
  }

  private openAssistance(prefill: OperationalContextAssistancePrefill): void {
    if (this.maintenance.busy()) {
      this.maintenanceNotice.set('Trwa zapis lub usuwanie wpisu. Poczekaj na zakończenie operacji przed otwarciem asysty AI.');
      return;
    }
    if (!this.confirmDiscard()) return;
    if (this.maintenance.editor()) this.maintenance.closeEditor();
    this.editorDirty.set(false);
    this.editorFocusPath.set(null);
    this.maintenance.cancelDelete();
    this.closeDrawer();
    if (this.requestedHistoryRunId) this.clearAssistanceHistory();
    this.assistancePrefill.set(prefill);
    this.assistanceMounted.set(true);
    this.selectedTab.set('assistance');
  }

  startNewAssistance(): void {
    this.clearAssistanceHistory();
    this.assistancePrefill.set({ mode: 'CREATE_AREA' });
    this.assistanceMounted.set(true);
    this.selectedTab.set('assistance');
  }

  onAssistanceJobChanged(job: OperationalContextAssistanceJob | null): void {
    const previousRunId = this.assistanceJob()?.jobId;
    this.assistanceJob.set(job);
    if (job && job.jobId !== previousRunId && !this.requestedHistoryRunId) {
      rememberLocalRunId(this.router, this.route, job.jobId);
    }
  }

  private clearAssistanceHistory(): void {
    this.requestedHistoryRunId = '';
    this.assistanceHistoryReadOnly.set(false);
    this.assistanceHistoryLoading.set(false);
    this.assistanceHistoryError.set('');
    this.assistanceJob.set(null);
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { localRunId: null },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  private loadAssistanceHistoryRun(localRunId: string): void {
    this.requestedHistoryRunId = localRunId;
    this.assistanceHistoryReadOnly.set(true);
    this.assistanceJob.set(null);
    this.assistanceMounted.set(true);
    this.selectedTab.set('assistance');
    this.assistanceHistoryLoading.set(true);
    this.assistanceHistoryError.set('');
    this.historyApi.getRun(localRunId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          if (this.requestedHistoryRunId !== localRunId) return;
          this.assistanceHistoryLoading.set(false);
          try {
            if (detail.feature !== 'operational-context-assistance') {
              throw new Error('Wybrany zapis nie jest analizą asysty Operational Context.');
            }
            const restored = restoreAssistanceHistoryRun(detail.exportEnvelope);
            this.assistancePrefill.set(restored.prefill);
            this.assistanceJob.set(restored.job);
            this.assistanceHistoryReadOnly.set(isTerminalAssistanceStatus(restored.job.status));
            this.assistanceMounted.set(true);
            this.selectedTab.set('assistance');
            if (localRunId === restored.job.jobId
              && (restored.job.status === 'COMPLETED' || restored.job.status === 'PARTIAL')
              && !!restored.job.draft?.proposals.length && !restored.job.proposalDecisions?.length) {
              this.assistanceApi.get(restored.job.jobId)
                .pipe(takeUntilDestroyed(this.destroyRef))
                .subscribe({
                  next: (live) => {
                    if (this.requestedHistoryRunId !== localRunId) return;
                    this.assistanceJob.set(live);
                    this.assistanceHistoryReadOnly.set(Boolean(live.proposalDecisions?.length)
                      || !live.draft?.proposals.length
                      || (live.status !== 'COMPLETED' && live.status !== 'PARTIAL'));
                  },
                  error: () => {
                    if (this.requestedHistoryRunId !== localRunId) return;
                    this.assistanceHistoryError.set('Nie można wznowić decyzji dla tego zapisu. Wynik pozostaje do odczytu.');
                  }
                });
            }
          } catch (error) {
            this.assistanceHistoryError.set(error instanceof Error ? error.message : 'Nie udało się odtworzyć zapisanej asysty.');
          }
        },
        error: () => {
          if (this.requestedHistoryRunId !== localRunId) return;
          this.assistanceHistoryLoading.set(false);
          this.assistanceHistoryError.set('Nie udało się odczytać zapisanej analizy z historii.');
        }
      });
  }

  private findingFieldPath(finding: ValidationFindingDto): string | null {
    const sourcePath = this.firstSourceRef(finding)?.path?.trim();
    if (!sourcePath) return null;
    const path = sourcePath.includes('#') ? sourcePath.substring(sourcePath.indexOf('#') + 1) : sourcePath;
    const jsonPath = path.match(/^\$\.[A-Za-z][A-Za-z0-9]*\[[^\]]+\]\.(.+)$/);
    if (jsonPath) return jsonPath[1];
    if (path.startsWith('$')) return null;
    return path;
  }

  refreshAfterAssistanceSave(_target: { type: string; id: string }): void {
    this.maintenanceNotice.set('Zapisano wybrany zestaw zmian katalogu.');
    this.searchResults.set([]);
    this.resetSearchAiApiPreview();
    this.loadCatalogue();
  }

  openSearchResult(result: OperationalContextSearchResultDto): void {
    this.openEntity({ type: result.type, id: result.id });
  }

  closeDrawer(): void {
    this.detail.set(null);
    this.detailError.set('');
    this.entityPreview.set(null);
    this.entityPreviewLoading.set(false);
    this.entityPreviewError.set('');
    this.selectedEntityTarget.set(null);
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
        if (this.validationSeverity() && !this.exactMatch(finding.severity, this.validationSeverity())) {
          return false;
        }
        if (this.validationCategory() && !this.exactMatch(finding.category, this.validationCategory())) {
          return false;
        }
        if (this.validationEntityType() && !this.exactMatch(finding.entityType, this.validationEntityType())) {
          return false;
        }
        if (
          this.validationSourceFile()
          && !this.exactMatch(this.firstSourceRef(finding)?.file, this.validationSourceFile())
        ) {
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
        if (this.questionSeverity() && !this.exactMatch(question.severity, this.questionSeverity())) {
          return false;
        }
        if (this.questionEntityType() && !this.exactMatch(question.entityType, this.questionEntityType())) {
          return false;
        }
        if (this.questionSourceFile() && !this.exactMatch(question.sourceFile, this.questionSourceFile())) {
          return false;
        }
        if (this.questionStatus() && !this.exactMatch(question.status, this.questionStatus())) {
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

  protected firstSourceRef(finding: ValidationFindingDto): SourceReferenceDto | null {
    return finding.sourceRefs?.[0] ?? null;
  }

  protected validationMaintenanceTarget(finding: ValidationFindingDto): string {
    const source = this.firstSourceRef(finding);
    const sourceLabel = [source?.file, source?.path].filter(Boolean).join(' ');
    return [
      sourceLabel || 'unknown source',
      `${finding.entityType}/${finding.entityId}`,
      finding.category
    ].join(' | ');
  }

  protected openQuestionMaintenanceTarget(question: OpenQuestionDto): string {
    return [
      question.sourceFile || 'unknown source',
      this.openQuestionEntityLabel(question),
      question.status
    ].filter(Boolean).join(' | ');
  }

  protected openQuestionEntityLabel(question: OpenQuestionDto): string {
    return question.entityId
      ? `${question.entityType}/${question.entityId}`
      : question.entityType;
  }

  protected async copyMaintenanceTarget(
    event: Event,
    target: string,
    key: string
  ): Promise<void> {
    event.stopPropagation();
    const copied = await copyTextToClipboard(target);
    this.copiedMaintenanceTarget.set(copied ? key : '');
  }

  protected healthReadinessLabel(card: ExplainableAggregateDto): string {
    if (card.detailsType === 'validation') {
      return card.severity === 'error' ? 'Wymaga poprawy' : card.count > 0 ? 'Sprawdź uwagi' : 'Brak uwag';
    }
    return card.count > 0 ? 'Są wpisy' : 'Brak wpisów';
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

  private loadCatalogue(afterLoad?: () => void): void {
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
          afterLoad?.();
        },
        error: () => {
          this.errorMessage.set('Could not load operational context catalogue.');
          this.isLoading.set(false);
        }
      });
  }

  private reloadAfterMutation(target: { type: string; id: string } | null): void {
    this.editorDirty.set(false);
    this.maintenance.cancelDelete();
    this.maintenance.loadCapabilities();
    this.searchResults.set([]);
    this.resetSearchAiApiPreview();
    if (!target) this.closeDrawer();
    this.loadCatalogue(() => {
      if (target) this.openEntity(target);
    });
  }

  private confirmDiscard(): boolean {
    if (!this.maintenance.editor() || !this.editorDirty()) return true;
    const discard = globalThis.confirm('Discard unsaved operational context changes?');
    if (discard) {
      this.editorDirty.set(false);
      this.maintenance.closeEditor();
    }
    return discard;
  }

  private entityPreviewFor(target: { type: string; id: string }): Observable<OperationalContextEditorState | null> {
    if (!this.canLoadEntityPreview(target)) {
      return of(null);
    }

    const type = target.type as OperationalContextWritableType;
    return this.maintenanceApi.getEntity(type, target.id).pipe(
      map((entity) => ({ mode: 'edit' as const, type, entity })),
      catchError(() => {
        this.entityPreviewError.set('Could not load editable source preview.');
        return of(null);
      })
    );
  }

  protected overviewCardDescription(card: ExplainableAggregateDto): string {
    const labels: Record<string, string> = {
      system: 'systemów',
      repository: 'repozytoriów',
      'code-search-scope': 'zakresów wyszukiwania kodu',
      process: 'procesów',
      integration: 'integracji',
      'bounded-context': 'obszarów domenowych',
      team: 'zespołów',
      'open-question': 'otwartych pytań',
      validation: 'uwag dotyczących jakości katalogu'
    };
    const label = labels[card.detailsType || ''];
    return label
      ? `Liczba ${label}: ${card.count}.`
      : `Liczba pozycji: ${card.count}.`;
  }

  protected overviewCountLabel(card: ExplainableAggregateDto): string {
    if (card.detailsType === 'validation') {
      return `Uwagi: ${card.count}`;
    }
    if (card.detailsType === 'open-question') {
      return `Pytania: ${card.count}`;
    }
    return `Wpisy: ${card.count}`;
  }

  private canLoadEntityPreview(target: { type: string; id: string }): boolean {
    return Boolean(
      target.id
      && isOperationalContextWritableType(target.type)
      && this.maintenance.supports(target.type)
    );
  }

  private resetSearchAiApiPreview(): void {
    this.searchAiApiPreview.set(null);
    this.searchAiApiPreviewLoading.set(false);
    this.searchAiApiPreviewError.set('');
    this.searchAiApiPreviewProfile.set('default');
    this.searchAiApiPreviewQuery.set('');
  }

  private uniqueValues(values: Array<string | null | undefined>): string[] {
    return Array.from(
      new Set(
        values
          .map((value) => String(value || '').trim())
          .filter(Boolean)
      )
    ).sort((left, right) => left.localeCompare(right));
  }

  private referenceOptionRows(
    rows: OperationalContextCatalogRow[],
    labelKey: string
  ): OperationalContextReferenceOption[] {
    return rows
      .map((row) => {
        const values = row as unknown as Record<string, unknown>;
        const id = String(values['id'] || '').trim();
        const label = String(values[labelKey] || id).trim();
        return { id, label };
      })
      .filter((option) => option.id)
      .sort((left, right) => left.label.localeCompare(right.label));
  }

  private exactMatch(left: string | null | undefined, right: string): boolean {
    return normalize(left || '') === normalize(right);
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
      case 'ok':
        return 'OK';
      case 'warning':
        return 'Review';
      case 'error':
        return 'Needs fix';
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

function restoreAssistanceHistoryRun(envelopeValue: unknown): {
  job: OperationalContextAssistanceJob;
  prefill: OperationalContextAssistancePrefill;
} {
  const envelope = asRecord(envelopeValue);
  const job = asRecord(envelope?.['job']);
  const mode = envelope?.['mode'];
  const status = job?.['status'];
  const validModes = ['CREATE_AREA', 'IMPROVE_ENTITY', 'RESOLVE_FINDING'];
  const validStatuses = [
    'QUEUED', 'COLLECTING_CONTEXT', 'AI_PREPARATION', 'ANALYZING',
    'COMPLETED', 'PARTIAL', 'BLOCKED', 'FAILED'
  ];
  if (envelope?.['schema'] !== 'tdw.operational-context-assistance-export'
    || envelope['version'] !== 2
    || !validModes.includes(String(mode))
    || !job
    || typeof job['jobId'] !== 'string'
    || !validStatuses.includes(String(status))
    || typeof job['createdAt'] !== 'string'
    || typeof job['updatedAt'] !== 'string'
    || !Array.isArray(job['steps'])
    || !Array.isArray(job['aiActivityEvents'])
    || !Array.isArray(job['sourceRefs'])
    || !Array.isArray(job['visibilityLimits'])
    || !Array.isArray(job['previews'])
    || (job['proposalDecisions'] != null && !Array.isArray(job['proposalDecisions']))) {
    throw new Error('Zapis asysty ma nieobsługiwany lub uszkodzony format.');
  }
  const draft = asRecord(job['draft']);
  if (draft && (!Array.isArray(draft['proposals'])
    || !Array.isArray(draft['visibilityLimits']))) {
    throw new Error('Zapis asysty zawiera uszkodzony draft.');
  }
  const targetValue = asRecord(envelope['target']);
  const target = targetValue
    && (targetValue['kind'] === 'ENTITY' || targetValue['kind'] === 'VALIDATION_FINDING' || targetValue['kind'] === 'OPEN_QUESTION')
    && isOperationalContextWritableType(String(targetValue['entityType']))
    && typeof targetValue['entityId'] === 'string'
    ? {
        kind: targetValue['kind'],
        entityType: targetValue['entityType'],
        entityId: targetValue['entityId'],
        ...(typeof targetValue['id'] === 'string' ? { id: targetValue['id'] } : {})
      } as NonNullable<OperationalContextAssistancePrefill['target']>
    : undefined;
  return {
    job: job as unknown as OperationalContextAssistanceJob,
    prefill: { mode: mode as OperationalContextAssistancePrefill['mode'], ...(target ? { target } : {}) }
  };
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown>
    : null;
}
