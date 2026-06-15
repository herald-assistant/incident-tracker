import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, WritableSignal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { Observable } from 'rxjs';

import { ApiErrorResponse } from '../../core/models/analysis.models';
import {
  EvidenceApiService,
  GitLabEndpointUseCaseContextPayload,
  GitLabEndpointUseCaseContextResponse,
  GitLabEndpointUseCaseFileCandidate,
  GitLabEndpointUseCaseRelation,
  GitLabRepositoryEndpoint,
  GitLabRepositoryEndpointParameterDocumentation,
  GitLabRepositoryEndpointsPayload,
  GitLabRepositoryEndpointsResponse,
  GitLabRepositoryFilesByPathPayload,
  GitLabRepositoryFilesByPathResponse,
  GitLabRepositorySearchPayload,
  GitLabSourceResolvePayload
} from '../../core/services/evidence-api.service';
import { copyTextToClipboard } from '../../core/utils/clipboard.utils';

type ToolStateStatus = 'idle' | 'loading' | 'success' | 'error';
type GitLabJsonResponseKey =
  | 'repository-search'
  | 'endpoint-inventory'
  | 'endpoint-use-case-context'
  | 'repository-files-by-path'
  | 'source-resolve';

interface ToolState {
  status: ToolStateStatus;
  statusCode: number | null;
  message: string;
  responseJson: string;
  response: unknown | null;
}

interface GitLabUseCaseTreeNode {
  id: string;
  label: string;
  subtitle: string | null;
  role: string | null;
  confidence: string | null;
  relationKind: string | null;
  relationReason: string | null;
  relationFrom: string | null;
  relationTo: string | null;
  file: GitLabEndpointUseCaseFileCandidate | null;
  duplicate: boolean;
  group: boolean;
  children: GitLabUseCaseTreeNode[];
}

interface GitLabUseCaseTreeRow {
  node: GitLabUseCaseTreeNode;
  depth: number;
}

interface GitLabUseCaseTree {
  rows: GitLabUseCaseTreeRow[];
  unmatchedRelations: GitLabEndpointUseCaseRelation[];
  linkedFileCount: number;
  unlinkedFileCount: number;
}

@Component({
  selector: 'app-gitlab-evidence-console',
  imports: [ReactiveFormsModule, RouterLink, RouterLinkActive],
  templateUrl: './gitlab-evidence-console.html',
  styleUrl: './evidence-console.scss'
})
export class GitLabEvidenceConsoleComponent {
  private readonly evidenceApi = inject(EvidenceApiService);
  private readonly destroyRef = inject(DestroyRef);
  readonly gitLabRepositoryForm = new FormGroup({
    correlationId: new FormControl('', { nonNullable: true }),
    branch: new FormControl('', { nonNullable: true }),
    projectHints: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    operationNames: new FormControl('', { nonNullable: true }),
    keywords: new FormControl('', { nonNullable: true })
  });

  readonly gitLabEndpointForm = new FormGroup({
    group: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    projectName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    branch: new FormControl('HEAD', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    endpointPathPrefix: new FormControl('', { nonNullable: true }),
    httpMethod: new FormControl('', { nonNullable: true }),
    maxScannedFiles: new FormControl('120', {
      nonNullable: true,
      validators: [Validators.min(1), Validators.max(250)]
    })
  });

  readonly gitLabEndpointUseCaseContextForm = new FormGroup({
    group: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    projectName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    branch: new FormControl('HEAD', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    endpointId: new FormControl('', { nonNullable: true }),
    httpMethod: new FormControl('', { nonNullable: true }),
    endpointPath: new FormControl('', { nonNullable: true }),
    maxDepth: new FormControl('5', {
      nonNullable: true,
      validators: [Validators.min(1), Validators.max(8)]
    }),
    maxFiles: new FormControl('60', {
      nonNullable: true,
      validators: [Validators.min(1), Validators.max(100)]
    }),
    reason: new FormControl('Manualna weryfikacja kontekstu use-case endpointu.', {
      nonNullable: true
    })
  });

  readonly gitLabRepositoryFilesByPathForm = new FormGroup({
    group: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    projectName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    branch: new FormControl('HEAD', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    filePaths: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    maxCharactersPerFile: new FormControl('4000', {
      nonNullable: true,
      validators: [Validators.min(1), Validators.max(120000)]
    }),
    maxTotalCharacters: new FormControl('60000', {
      nonNullable: true,
      validators: [Validators.min(1), Validators.max(500000)]
    })
  });

  readonly gitLabSourceForm = new FormGroup({
    gitlabBaseUrl: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    groupPath: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    projectPath: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    ref: new FormControl('HEAD', { nonNullable: true }),
    symbol: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    preview: new FormControl(true, { nonNullable: true })
  });

  readonly gitLabRepositoryState = signal<ToolState>(
    this.idleState('Wpisz hinty projektu, aby przetestować mapowanie component -> repo.')
  );
  readonly gitLabEndpointState = signal<ToolState>(
    this.idleState('Podaj scope repozytorium, aby znaleźć endpointy Spring REST.')
  );
  readonly gitLabEndpointUseCaseContextState = signal<ToolState>(
    this.idleState('Wybierz endpoint z inventory albo uzupełnij selector, aby zbudować listę plików use-case.')
  );
  readonly gitLabRepositoryFilesByPathState = signal<ToolState>(
    this.idleState('Przenieś listę plików z use-case contextu albo wklej pełne path ręcznie.')
  );
  readonly gitLabSourceState = signal<ToolState>(
    this.idleState('Uzupełnij dane repozytorium i symbol, aby przetestować source resolve.')
  );

  readonly gitLabEndpointResult = computed(() =>
    this.asGitLabEndpointResult(this.gitLabEndpointState().response)
  );

  readonly gitLabEndpointUseCaseContextResult = computed(() =>
    this.asGitLabEndpointUseCaseContextResult(this.gitLabEndpointUseCaseContextState().response)
  );

  readonly gitLabUseCaseTree = computed(() =>
    this.buildGitLabUseCaseTree(this.gitLabEndpointUseCaseContextResult())
  );

  readonly gitLabRepositoryFilesByPathResult = computed(() =>
    this.asGitLabRepositoryFilesByPathResult(this.gitLabRepositoryFilesByPathState().response)
  );

  readonly selectedGitLabUseCaseTreeNodeId = signal<string | null>(null);
  readonly copiedJsonResponseKey = signal<GitLabJsonResponseKey | null>(null);

  readonly selectedGitLabUseCaseTreeNode = computed(() => {
    const tree = this.gitLabUseCaseTree();
    if (!tree || tree.rows.length === 0) {
      return null;
    }

    const selectedId = this.selectedGitLabUseCaseTreeNodeId();
    return tree.rows.find((row) => row.node.id === selectedId)?.node ?? tree.rows[0].node;
  });

  submitGitLabRepository(event: Event): void {
    event.preventDefault();

    if (this.gitLabRepositoryForm.invalid) {
      this.gitLabRepositoryForm.markAllAsTouched();
      this.gitLabRepositoryState.set(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Podaj co najmniej jeden project hint dla GitLaba.'
        })
      );
      return;
    }

    const payload: GitLabRepositorySearchPayload = {
      correlationId: this.optionalValue(this.gitLabRepositoryForm.controls.correlationId.value),
      branch: this.optionalValue(this.gitLabRepositoryForm.controls.branch.value),
      projectHints: this.toList(this.gitLabRepositoryForm.controls.projectHints.value),
      operationNames: this.toList(this.gitLabRepositoryForm.controls.operationNames.value),
      keywords: this.toList(this.gitLabRepositoryForm.controls.keywords.value)
    };

    this.runRequest(
      this.gitLabRepositoryState,
      this.evidenceApi.searchGitLabRepository(payload),
      payload,
      'Wysyłamy request do /api/gitlab/repository/search...'
    );
  }

  submitGitLabEndpointSearch(event: Event): void {
    event.preventDefault();

    if (this.gitLabEndpointForm.invalid) {
      this.gitLabEndpointForm.markAllAsTouched();
      this.gitLabEndpointState.set(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Uzupełnij group, projectName i branch dla GitLab endpoint inventory.'
        })
      );
      return;
    }

    const payload: GitLabRepositoryEndpointsPayload = {
      group: this.gitLabEndpointForm.controls.group.value.trim(),
      projectName: this.gitLabEndpointForm.controls.projectName.value.trim(),
      branch: this.gitLabEndpointForm.controls.branch.value.trim(),
      endpointPathPrefix: this.optionalValue(
        this.gitLabEndpointForm.controls.endpointPathPrefix.value
      ),
      httpMethod: this.optionalValue(this.gitLabEndpointForm.controls.httpMethod.value),
      maxScannedFiles: this.optionalNumber(this.gitLabEndpointForm.controls.maxScannedFiles.value)
    };

    this.runRequest(
      this.gitLabEndpointState,
      this.evidenceApi.listGitLabRepositoryEndpoints(payload),
      payload,
      'Wysyłamy request do /api/gitlab/repository/endpoints...'
    );
  }

  submitGitLabEndpointUseCaseContext(event: Event): void {
    event.preventDefault();

    const endpointId = this.optionalValue(
      this.gitLabEndpointUseCaseContextForm.controls.endpointId.value
    );
    const httpMethod = this.optionalValue(
      this.gitLabEndpointUseCaseContextForm.controls.httpMethod.value
    );
    const endpointPath = this.optionalValue(
      this.gitLabEndpointUseCaseContextForm.controls.endpointPath.value
    );

    if (
      this.gitLabEndpointUseCaseContextForm.invalid ||
      (!endpointId && (!httpMethod || !endpointPath))
    ) {
      this.gitLabEndpointUseCaseContextForm.markAllAsTouched();
      this.gitLabEndpointUseCaseContextState.set(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message:
            'Uzupełnij group, projectName, branch oraz endpointId albo parę httpMethod + endpointPath.'
        })
      );
      return;
    }

    const payload: GitLabEndpointUseCaseContextPayload = {
      group: this.gitLabEndpointUseCaseContextForm.controls.group.value.trim(),
      projectName: this.gitLabEndpointUseCaseContextForm.controls.projectName.value.trim(),
      branch: this.gitLabEndpointUseCaseContextForm.controls.branch.value.trim(),
      endpointId,
      httpMethod,
      endpointPath,
      maxDepth: this.optionalNumber(this.gitLabEndpointUseCaseContextForm.controls.maxDepth.value),
      maxFiles: this.optionalNumber(this.gitLabEndpointUseCaseContextForm.controls.maxFiles.value),
      reason: this.optionalValue(this.gitLabEndpointUseCaseContextForm.controls.reason.value)
    };

    this.selectedGitLabUseCaseTreeNodeId.set(null);
    this.runRequest(
      this.gitLabEndpointUseCaseContextState,
      this.evidenceApi.buildGitLabEndpointUseCaseContext(payload),
      payload,
      'Wysyłamy request do /api/gitlab/repository/endpoint-use-case-context...'
    );
  }

  submitGitLabRepositoryFilesByPath(event: Event): void {
    event.preventDefault();

    const filePaths = this.toList(this.gitLabRepositoryFilesByPathForm.controls.filePaths.value);
    if (this.gitLabRepositoryFilesByPathForm.invalid || filePaths.length === 0) {
      this.gitLabRepositoryFilesByPathForm.markAllAsTouched();
      this.gitLabRepositoryFilesByPathState.set(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Uzupełnij group, projectName, branch i co najmniej jeden file path.'
        })
      );
      return;
    }

    const payload: GitLabRepositoryFilesByPathPayload = {
      group: this.gitLabRepositoryFilesByPathForm.controls.group.value.trim(),
      projectName: this.gitLabRepositoryFilesByPathForm.controls.projectName.value.trim(),
      branch: this.gitLabRepositoryFilesByPathForm.controls.branch.value.trim(),
      filePaths,
      maxCharactersPerFile: this.optionalNumber(
        this.gitLabRepositoryFilesByPathForm.controls.maxCharactersPerFile.value
      ),
      maxTotalCharacters: this.optionalNumber(
        this.gitLabRepositoryFilesByPathForm.controls.maxTotalCharacters.value
      )
    };

    this.runRequest(
      this.gitLabRepositoryFilesByPathState,
      this.evidenceApi.readGitLabRepositoryFilesByPath(payload),
      payload,
      'Wysyłamy request do /api/gitlab/repository/files/by-path...'
    );
  }

  useEndpointForContext(endpoint: GitLabRepositoryEndpoint): void {
    this.gitLabEndpointUseCaseContextForm.patchValue({
      group: this.gitLabEndpointForm.controls.group.value,
      projectName: this.gitLabEndpointForm.controls.projectName.value,
      branch: this.gitLabEndpointForm.controls.branch.value,
      endpointId: endpoint.endpointId,
      httpMethod: endpoint.httpMethods?.[0] || '',
      endpointPath: endpoint.path || endpoint.pathExpression || ''
    });
    this.selectedGitLabUseCaseTreeNodeId.set(null);
    this.gitLabEndpointUseCaseContextState.set(
      this.idleState('Endpoint przeniesiony z inventory. Uruchom context builder, aby zbudować listę plików.')
    );
  }

  useUseCaseFilesForRead(): void {
    const context = this.gitLabEndpointUseCaseContextResult();
    const filePaths = (context?.files || [])
      .map((file) => this.normalizePath(file.path))
      .filter((path): path is string => !!path);

    this.gitLabRepositoryFilesByPathForm.patchValue({
      group:
        context?.repository?.group ||
        this.gitLabEndpointUseCaseContextForm.controls.group.value,
      projectName:
        context?.repository?.projectName ||
        this.gitLabEndpointUseCaseContextForm.controls.projectName.value,
      branch:
        context?.repository?.branch ||
        this.gitLabEndpointUseCaseContextForm.controls.branch.value,
      filePaths: [...new Set(filePaths)].join('\n')
    });
    this.gitLabRepositoryFilesByPathState.set(
      this.idleState('Lista plików została przeniesiona z use-case contextu. Uruchom odczyt, aby pobrać content.')
    );
  }

  submitGitLabSource(event: Event): void {
    event.preventDefault();

    if (this.gitLabSourceForm.invalid) {
      this.gitLabSourceForm.markAllAsTouched();
      this.gitLabSourceState.set(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Uzupełnij wymagane pola source resolve dla GitLaba.'
        })
      );
      return;
    }

    const preview = this.gitLabSourceForm.controls.preview.value;
    const payload: GitLabSourceResolvePayload = {
      gitlabBaseUrl: this.gitLabSourceForm.controls.gitlabBaseUrl.value.trim(),
      groupPath: this.gitLabSourceForm.controls.groupPath.value.trim(),
      projectPath: this.gitLabSourceForm.controls.projectPath.value.trim(),
      ref: this.optionalValue(this.gitLabSourceForm.controls.ref.value),
      symbol: this.gitLabSourceForm.controls.symbol.value.trim()
    };

    this.runRequest(
      this.gitLabSourceState,
      this.evidenceApi.resolveGitLabSource(payload, preview),
      payload,
      `Wysyłamy request do ${preview ? '/api/gitlab/source/resolve/preview' : '/api/gitlab/source/resolve'}...`
    );
  }

  async copyJsonResponse(key: GitLabJsonResponseKey, responseJson: string): Promise<void> {
    const copied = await copyTextToClipboard(responseJson);
    if (!copied) {
      return;
    }

    this.copiedJsonResponseKey.set(key);
    window.setTimeout(() => {
      if (this.copiedJsonResponseKey() === key) {
        this.copiedJsonResponseKey.set(null);
      }
    }, 1600);
  }

  downloadJsonResponse(key: GitLabJsonResponseKey, responseJson: string): void {
    const blob = new Blob([responseJson], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');

    link.href = url;
    link.download = this.gitLabJsonResponseFileName(key);
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }

  async loadJsonResponseFile(key: GitLabJsonResponseKey, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file) {
      return;
    }

    const state = this.stateForJsonResponseKey(key);
    try {
      const text = await this.readFileText(file);
      const response = JSON.parse(text) as unknown;
      state.set({
        status: 'success',
        statusCode: null,
        message: `Załadowano odpowiedź JSON z pliku ${file.name}.`,
        response,
        responseJson: this.toFormattedJson(response)
      });
      if (key === 'endpoint-use-case-context') {
        this.selectedGitLabUseCaseTreeNodeId.set(null);
      }
    } catch {
      state.set(
        this.errorStateFromPayload({
          code: 'INVALID_JSON_FILE',
          message: `Nie udało się załadować poprawnego JSON z pliku ${file.name}.`
        })
      );
    } finally {
      if (input) {
        input.value = '';
      }
    }
  }

  controlInvalid(control: AbstractControl<unknown, unknown>): boolean {
    return control.invalid && (control.dirty || control.touched);
  }

  statusLabel(status: ToolStateStatus): string {
    switch (status) {
      case 'loading':
        return 'W toku';
      case 'success':
        return 'OK';
      case 'error':
        return 'Błąd';
      default:
        return 'Gotowe do testu';
    }
  }

  statusPillClass(status: ToolStateStatus): string {
    switch (status) {
      case 'loading':
        return 'status-pill status-pill--running';
      case 'success':
        return 'status-pill status-pill--done';
      case 'error':
        return 'status-pill status-pill--error';
      default:
        return 'status-pill status-pill--queued';
    }
  }

  statusTooltip(status: ToolStateStatus): string {
    switch (status) {
      case 'loading':
        return 'Request do backendu jest w trakcie wykonywania.';
      case 'success':
        return 'Backend zwrócił poprawną odpowiedź dla ostatniego wywołania.';
      case 'error':
        return 'Ostatnie wywołanie zakończyło się błędem lub backend zwrócił błąd.';
      default:
        return 'Tool jest gotowy do ręcznego testu; request nie został jeszcze wysłany.';
    }
  }

  httpStatusTooltip(statusCode: number | null): string {
    return statusCode === null
      ? 'Kod HTTP odpowiedzi backendu pojawi się po wywołaniu toola.'
      : `Kod HTTP ${statusCode} zwrócony przez backend dla ostatniego wywołania.`;
  }

  endpointMethods(endpoint: GitLabRepositoryEndpoint): string {
    return endpoint.httpMethods?.length ? endpoint.httpMethods.join(', ') : 'ANY';
  }

  endpointMethodTooltip(methods: string[] | null | undefined): string {
    const label = methods?.length ? methods.join(', ') : 'ANY';
    return `Metoda HTTP endpointu znaleziona w repozytorium: ${label}.`;
  }

  documentationSourceLabel(source: string | null | undefined): string {
    return this.displayToken(source) || 'DOCUMENTATION';
  }

  documentationSourceTooltip(source: string | null | undefined): string {
    switch ((source || '').toUpperCase()) {
      case 'OPENAPI_YAML':
        return 'Dokumentacja odczytana z pliku OpenAPI YAML w repozytorium.';
      case 'JAVA_OPENAPI_ANNOTATION':
        return 'Dokumentacja odczytana z adnotacji OpenAPI przy metodzie kontrolera.';
      case 'SPRING_SIGNATURE':
        return 'Dokumentacja parametrów odtworzona z adnotacji Spring MVC w sygnaturze metody.';
      case 'JAVA_OPENAPI_ANNOTATION+OPENAPI_YAML':
      case 'OPENAPI_YAML+JAVA_OPENAPI_ANNOTATION':
        return 'Dokumentacja scalona z adnotacji OpenAPI w Javie oraz pliku OpenAPI YAML.';
      default:
        return 'Źródło dokumentacji endpointu zwrócone przez backend.';
    }
  }

  parameterDocumentationTooltip(
    parameter: GitLabRepositoryEndpointParameterDocumentation
  ): string {
    const location = parameter.in || 'parameter';
    const required = parameter.required ? 'wymagany' : 'opcjonalny';
    const type = parameter.type ? ` Typ: ${parameter.type}.` : '';
    return `${parameter.name || 'Parametr'}: ${required} parametr ${location}.${type}`;
  }

  confidenceClass(confidence: string | null | undefined): string {
    const normalized = (confidence || '').toLowerCase();
    if (normalized === 'high') {
      return 'confidence-pill confidence-pill--high';
    }
    if (normalized === 'medium') {
      return 'confidence-pill confidence-pill--medium';
    }
    if (normalized === 'low') {
      return 'confidence-pill confidence-pill--low';
    }
    return 'confidence-pill';
  }

  confidenceTooltip(confidence: string | null | undefined): string {
    switch ((confidence || '').toLowerCase()) {
      case 'high':
        return 'Wysoka pewność: element został dopasowany bezpośrednio z kodu, relacji albo endpoint inventory.';
      case 'medium':
        return 'Średnia pewność: element jest przydatnym kandydatem, ale może wymagać ręcznej weryfikacji.';
      case 'low':
        return 'Niska pewność: element jest słabym kandydatem i traktuj go jako wskazówkę do sprawdzenia.';
      default:
        return 'Brak jednoznacznego poziomu pewności dla tego elementu.';
    }
  }

  selectGitLabUseCaseTreeNode(node: GitLabUseCaseTreeNode): void {
    this.selectedGitLabUseCaseTreeNodeId.set(node.id);
  }

  flowTreeNodeClass(node: GitLabUseCaseTreeNode): string {
    const classes = ['flow-tree-node'];
    if (this.selectedGitLabUseCaseTreeNode()?.id === node.id) {
      classes.push('flow-tree-node--selected');
    }
    if (node.group) {
      classes.push('flow-tree-node--group');
    }
    if (node.duplicate) {
      classes.push('flow-tree-node--duplicate');
    }
    if (node.role) {
      classes.push(`flow-tree-node--${this.cssToken(node.role)}`);
    }
    return classes.join(' ');
  }

  displayRole(role: string | null | undefined): string {
    return this.displayToken(role) || 'UNKNOWN';
  }

  displayRelationKind(kind: string | null | undefined): string {
    return this.displayToken(kind) || 'FLOW';
  }

  roleTooltip(role: string | null | undefined): string {
    const label = this.displayRole(role);
    switch ((role || '').toUpperCase()) {
      case 'ENTRYPOINT':
        return `${label}: start drzewa dla wybranego endpointu HTTP.`;
      case 'FILES':
        return `${label}: grupa plików zwróconych przez tool bez jednoznacznej krawędzi w flow.`;
      case 'CONTROLLER':
        return `${label}: klasa kontrolera obsługująca request HTTP.`;
      case 'OPENAPI_CONTRACT':
        return `${label}: kontrakt OpenAPI/YAML użyty do wygenerowania lub opisania API.`;
      case 'API_INTERFACE':
        return `${label}: interfejs API, zwykle generowany albo implementowany przez kontroler.`;
      case 'USE_CASE_PORT':
        return `${label}: port aplikacyjny, przez który kontroler lub serwis wchodzi w logikę use-case.`;
      case 'USE_CASE_SERVICE':
        return `${label}: serwis aplikacyjny zawierający główną logikę use-case.`;
      case 'REPOSITORY_PORT':
        return `${label}: port repozytorium używany przez logikę aplikacyjną.`;
      case 'REPOSITORY_IMPLEMENTATION':
        return `${label}: implementacja repozytorium albo adapter dostępu do danych.`;
      case 'SPRING_DATA_REPOSITORY':
        return `${label}: repozytorium Spring Data tworzone jako bean przez framework.`;
      case 'MAPPER':
        return `${label}: mapper konwertujący modele API, domenowe albo persistence.`;
      case 'DOMAIN_MODEL':
        return `${label}: model domenowy istotny dla danych przetwarzanych w use-case.`;
      case 'WEB_MODEL':
        return `${label}: model requestu albo odpowiedzi wystawiany przez API.`;
      case 'PROJECTION':
        return `${label}: projekcja danych używana do odczytu lub mapowania części modelu.`;
      case 'CONFIGURATION':
        return `${label}: konfiguracja Springa lub integracji wpływająca na flow.`;
      case 'EXTERNAL_CLIENT':
        return `${label}: klient zewnętrznej integracji, np. Feign albo REST client.`;
      case 'UNKNOWN':
        return `${label}: tool nie rozpoznał jednoznacznej roli pliku.`;
      default:
        return `${label}: rola pliku zwrócona przez context builder.`;
    }
  }

  relationTooltip(kind: string | null | undefined, reason: string | null | undefined): string {
    const base = this.relationKindTooltip(kind);
    return reason ? `${base} Powód dopasowania: ${reason}` : base;
  }

  relationKindTooltip(kind: string | null | undefined): string {
    const label = this.displayRelationKind(kind);
    switch ((kind || '').toUpperCase()) {
      case 'ENDPOINT_HANDLER':
        return `${label}: endpoint inventory wskazało handler HTTP jako początek flow.`;
      case 'LOCAL_METHOD_CALL':
        return `${label}: przejście wynika z lokalnego wywołania metody w tej samej klasie lub bliskim kontekście.`;
      case 'INJECTED_PORT_CALL':
        return `${label}: kod wywołuje zależność wstrzykniętą przez DI, więc to ważna ścieżka use-case.`;
      case 'INTERFACE_IMPLEMENTATION':
        return `${label}: tool znalazł klasę, która może implementować wywoływany interfejs.`;
      case 'STATIC_METHOD_CALL':
        return `${label}: przejście wynika z wywołania metody statycznej.`;
      case 'MAPPER_CALL':
        return `${label}: flow przechodzi przez mapper konwertujący dane między modelami.`;
      case 'REPOSITORY_CALL':
        return `${label}: kod przechodzi do repozytorium lub adaptera dostępu do danych.`;
      case 'DOMAIN_METHOD_CALL':
        return `${label}: flow przechodzi przez metodę modelu albo logiki domenowej.`;
      case 'SPRING_DATA_BOUNDARY':
        return `${label}: granica repozytorium Spring Data tworzonego jako bean przez framework.`;
      case 'EXTERNAL_BOUNDARY':
        return `${label}: granica wyjścia poza analizowany kod, np. integracja z innym systemem.`;
      case 'OPENAPI_CONTRACT':
        return `${label}: powiązanie z kontraktem OpenAPI/YAML opisującym endpoint albo model.`;
      case 'UNKNOWN':
        return `${label}: tool nie rozpoznał jednoznacznego typu relacji.`;
      default:
        return `${label}: relacja między plikami zwrócona przez context builder.`;
    }
  }

  routeChipTooltip(route: string): string {
    return `Endpoint backendowy aplikacji używany przez ten manualny test: ${route}.`;
  }

  countChipTooltip(kind: string, count: number): string {
    switch (kind) {
      case 'scanned-files':
        return `${count} plików zostało przeskanowanych podczas szukania endpointów.`;
      case 'candidate-files':
        return `${count} plików pasowało do heurystyk jako kandydaci do analizy endpointów.`;
      case 'processed-files':
        return `${count} plików zostało przetworzonych w batchowym odczycie po path.`;
      case 'unresolved':
        return `${count} referencji nie udało się jednoznacznie rozwiązać do pliku.`;
      case 'suggested-next-reads':
        return `${count} dodatkowych plików tool sugeruje doczytać, jeśli AI potrzebuje szerszego kontekstu.`;
      default:
        return `${count} elementów w tej kategorii.`;
    }
  }

  limitChipTooltip(kind: string): string {
    switch (kind) {
      case 'endpoint-scan':
        return 'Tool zatrzymał skanowanie endpointów na skonfigurowanym limicie plików.';
      case 'use-case-context':
        return 'Context builder osiągnął limit głębokości albo maksymalnej liczby plików.';
      case 'repository-files-by-path':
        return 'Batchowy odczyt plików zatrzymał się na limicie liczby plików albo łącznej liczbie znaków.';
      default:
        return 'Wynik został ograniczony przez limit bezpieczeństwa lub kosztu.';
    }
  }

  duplicateNodeTooltip(): string {
    return 'Ten plik pojawił się już wcześniej w drzewie; wpis jest skrótem, żeby nie powielać gałęzi flow.';
  }

  symbolTooltip(symbol: string): string {
    return `Symbol znaleziony w wybranym pliku use-case: ${symbol}.`;
  }

  displayPathName(path: string | null | undefined): string {
    const normalized = this.normalizePath(path);
    if (!normalized) {
      return '<unknown-file>';
    }
    const lastSlashIndex = normalized.lastIndexOf('/');
    return lastSlashIndex >= 0 ? normalized.slice(lastSlashIndex + 1) : normalized;
  }

  treeNodePadding(depth: number): number {
    return 12 + depth * 26;
  }

  private runRequest(
    state: WritableSignal<ToolState>,
    request$: Observable<unknown>,
    payload: unknown,
    loadingMessage: string
  ): void {
    state.set({
      status: 'loading',
      statusCode: null,
      message: loadingMessage,
      response: null,
      responseJson: this.toFormattedJson({
        status: 'WAITING',
        request: payload
      })
    });

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        state.set({
          status: 'success',
          statusCode: 200,
          message: 'Backend zwrócił odpowiedź JSON.',
          response,
          responseJson: this.toFormattedJson(response)
        });
      },
      error: (error) => state.set(this.toErrorState(error))
    });
  }

  private toErrorState(error: unknown): ToolState {
    if (error instanceof HttpErrorResponse) {
      const payload = this.normalizeErrorPayload(error.error, error.status, error.message);
      return {
        status: 'error',
        statusCode: error.status || null,
        message: payload.message,
        response: null,
        responseJson: this.toFormattedJson(payload.body)
      };
    }

    return this.errorStateFromPayload({
      code: 'REQUEST_FAILED',
      message: error instanceof Error ? error.message : 'Request zakończył się błędem.'
    });
  }

  private normalizeErrorPayload(
    payload: unknown,
    status: number,
    fallbackMessage: string
  ): { message: string; body: unknown } {
    if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
      const record = payload as Record<string, unknown>;
      const normalized = this.toApiErrorResponse(record);

      if (normalized) {
        return {
          message: normalized.message || `Request zakończył się błędem HTTP ${status}.`,
          body: normalized
        };
      }

      return {
        message: `Request zakończył się błędem HTTP ${status}.`,
        body: payload
      };
    }

    if (typeof payload === 'string' && payload.trim().length > 0) {
      try {
        const parsed = JSON.parse(payload) as unknown;
        return {
          message: `Request zakończył się błędem HTTP ${status}.`,
          body: parsed
        };
      } catch {
        return {
          message: `Request zakończył się błędem HTTP ${status}.`,
          body: {
            status,
            message: payload
          }
        };
      }
    }

    return {
      message: status > 0 ? `Request zakończył się błędem HTTP ${status}.` : fallbackMessage,
      body: {
        status,
        message: status > 0 ? fallbackMessage : 'Brak odpowiedzi HTTP od backendu.'
      }
    };
  }

  private toApiErrorResponse(payload: Record<string, unknown>): ApiErrorResponse | null {
    if (
      typeof payload['code'] !== 'string' &&
      typeof payload['message'] !== 'string' &&
      !Array.isArray(payload['fieldErrors'])
    ) {
      return null;
    }

    return {
      code: typeof payload['code'] === 'string' ? payload['code'] : '',
      message: typeof payload['message'] === 'string' ? payload['message'] : '',
      fieldErrors: Array.isArray(payload['fieldErrors'])
        ? payload['fieldErrors']
            .filter(
              (fieldError): fieldError is Record<string, unknown> =>
                !!fieldError && typeof fieldError === 'object' && !Array.isArray(fieldError)
            )
            .map((fieldError) => ({
              field: typeof fieldError['field'] === 'string' ? fieldError['field'] : '',
              message: typeof fieldError['message'] === 'string' ? fieldError['message'] : ''
            }))
        : []
    };
  }

  private errorStateFromPayload(payload: { code: string; message: string }): ToolState {
    return {
      status: 'error',
      statusCode: null,
      message: payload.message,
      response: null,
      responseJson: this.toFormattedJson(payload)
    };
  }

  private idleState(message: string): ToolState {
    return {
      status: 'idle',
      statusCode: null,
      message,
      response: null,
      responseJson: this.toFormattedJson({
        message
      })
    };
  }

  private toList(raw: string): string[] {
    return raw
      .split(/[\r\n,]+/)
      .map((value) => value.trim())
      .filter((value) => value.length > 0);
  }

  private optionalValue(raw: string): string | undefined {
    const value = String(raw || "").trim();
    return value.length > 0 ? value : undefined;
  }

  private optionalNumber(raw: string): number | undefined {
    const value = String(raw || "").trim();
    if (value.length === 0) {
      return undefined;
    }
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : undefined;
  }

  private toFormattedJson(value: unknown): string {
    const formatted = JSON.stringify(value, null, 2);
    return formatted === undefined ? 'null' : formatted;
  }

  private gitLabJsonResponseFileName(key: GitLabJsonResponseKey): string {
    const timestamp = new Date()
      .toISOString()
      .replace(/\.\d{3}Z$/, 'Z')
      .replace(/[-:]/g, '')
      .replace('T', '-');
    const safeKey = key.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
    return `gitlab-${safeKey}-${timestamp}.json`;
  }

  private stateForJsonResponseKey(key: GitLabJsonResponseKey): WritableSignal<ToolState> {
    switch (key) {
      case 'endpoint-inventory':
        return this.gitLabEndpointState;
      case 'endpoint-use-case-context':
        return this.gitLabEndpointUseCaseContextState;
      case 'repository-files-by-path':
        return this.gitLabRepositoryFilesByPathState;
      case 'source-resolve':
        return this.gitLabSourceState;
      default:
        return this.gitLabRepositoryState;
    }
  }

  private readFileText(file: File): Promise<string> {
    const fileWithText = file as File & { text?: () => Promise<string> };
    if (typeof fileWithText.text === 'function') {
      return fileWithText.text();
    }

    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result || ''));
      reader.onerror = () => reject(reader.error || new Error('File read failed.'));
      reader.readAsText(file);
    });
  }

  private asGitLabEndpointResult(response: unknown): GitLabRepositoryEndpointsResponse | null {
    if (!response || typeof response !== 'object' || Array.isArray(response)) {
      return null;
    }

    const record = response as Partial<GitLabRepositoryEndpointsResponse>;
    return Array.isArray(record.endpoints) ? (record as GitLabRepositoryEndpointsResponse) : null;
  }

  private asGitLabEndpointUseCaseContextResult(
    response: unknown
  ): GitLabEndpointUseCaseContextResponse | null {
    if (!response || typeof response !== 'object' || Array.isArray(response)) {
      return null;
    }

    const record = response as Partial<GitLabEndpointUseCaseContextResponse>;
    return Array.isArray(record.files) ? (record as GitLabEndpointUseCaseContextResponse) : null;
  }

  private asGitLabRepositoryFilesByPathResult(
    response: unknown
  ): GitLabRepositoryFilesByPathResponse | null {
    if (!response || typeof response !== 'object' || Array.isArray(response)) {
      return null;
    }

    const record = response as Partial<GitLabRepositoryFilesByPathResponse>;
    return Array.isArray(record.files) && typeof record.returnedFileCount === 'number'
      ? (record as GitLabRepositoryFilesByPathResponse)
      : null;
  }

  private buildGitLabUseCaseTree(
    result: GitLabEndpointUseCaseContextResponse | null
  ): GitLabUseCaseTree | null {
    if (!result) {
      return null;
    }

    const files = (result.files || []).filter((file) => !!this.normalizePath(file.path));
    const fileByPath = new Map<string, GitLabEndpointUseCaseFileCandidate>();
    for (const file of files) {
      const path = this.normalizePath(file.path);
      if (path) {
        fileByPath.set(path, file);
      }
    }

    const rootKey = 'endpoint';
    const root: GitLabUseCaseTreeNode = {
      id: 'endpoint-root',
      label: this.gitLabEndpointTreeLabel(result),
      subtitle: this.gitLabEndpointTreeSubtitle(result),
      role: 'ENTRYPOINT',
      confidence: result.confidence || null,
      relationKind: null,
      relationReason: null,
      relationFrom: null,
      relationTo: null,
      file: null,
      duplicate: false,
      group: false,
      children: []
    };

    const adjacency = new Map<string, GitLabEndpointUseCaseRelation[]>();
    const unmatchedRelations: GitLabEndpointUseCaseRelation[] = [];

    for (const relation of result.relations || []) {
      const fromKey = this.resolveGitLabRelationSource(relation.from, fileByPath, result, rootKey);
      const toPath = this.resolveGitLabRelationFilePath(relation.to, fileByPath, result);
      if (!fromKey || !toPath) {
        unmatchedRelations.push(relation);
        continue;
      }
      if (fromKey === toPath) {
        continue;
      }
      this.addTreeRelation(adjacency, fromKey, relation);
    }

    const controllerPath = this.resolveGitLabControllerPath(result, fileByPath);
    if (controllerPath && !this.hasTreeRelation(adjacency, rootKey, controllerPath, fileByPath, result)) {
      this.addTreeRelation(adjacency, rootKey, {
        from:
          result.endpoint?.endpointId ||
          result.endpoint?.path ||
          result.endpoint?.pathExpression ||
          'endpoint',
        to: this.gitLabEndpointHandlerSymbol(result) || controllerPath,
        kind: 'ENDPOINT_HANDLER',
        confidence: result.endpoint?.confidence || result.confidence || null,
        reason: 'Endpoint handler resolved by endpoint inventory.'
      });
    }

    const visited = new Set<string>();
    const linkedFiles = new Set<string>();
    root.children = this.treeRelationsFor(adjacency, rootKey, fileByPath, result)
      .map((relation, index) =>
        this.buildGitLabFileTreeNode(
          relation,
          fileByPath,
          result,
          adjacency,
          visited,
          linkedFiles,
          new Set<string>(),
          `root-${index}`
        )
      )
      .filter((node): node is GitLabUseCaseTreeNode => !!node);

    const unlinkedFiles = files
      .map((file) => this.normalizePath(file.path))
      .filter((path): path is string => !!path && !linkedFiles.has(path));

    if (unlinkedFiles.length > 0) {
      root.children.push({
        id: 'unlinked-files',
        label: 'Pozostałe pliki',
        subtitle: 'Pliki zwrócone przez tool bez jednoznacznej krawędzi w flow.',
        role: 'FILES',
        confidence: null,
        relationKind: null,
        relationReason: null,
        relationFrom: null,
        relationTo: null,
        file: null,
        duplicate: false,
        group: true,
        children: unlinkedFiles
          .sort((left, right) => this.compareGitLabFiles(fileByPath.get(left), fileByPath.get(right)))
          .map((path, index) =>
            this.fileTreeNode(
              path,
              fileByPath.get(path) || null,
              {
                kind: 'UNLINKED_FILE',
                reason: 'File returned by context builder without a matched relation.',
                from: null,
                to: path,
                confidence: fileByPath.get(path)?.confidence || null
              },
              `unlinked-${index}`,
              false,
              []
            )
          )
      });
    }

    return {
      rows: this.flattenGitLabUseCaseTree(root),
      unmatchedRelations,
      linkedFileCount: linkedFiles.size,
      unlinkedFileCount: unlinkedFiles.length
    };
  }

  private buildGitLabFileTreeNode(
    relation: GitLabEndpointUseCaseRelation,
    fileByPath: Map<string, GitLabEndpointUseCaseFileCandidate>,
    result: GitLabEndpointUseCaseContextResponse,
    adjacency: Map<string, GitLabEndpointUseCaseRelation[]>,
    visited: Set<string>,
    linkedFiles: Set<string>,
    pathStack: Set<string>,
    idSuffix: string
  ): GitLabUseCaseTreeNode | null {
    const path = this.resolveGitLabRelationFilePath(relation.to, fileByPath, result);
    if (!path) {
      return null;
    }

    const duplicate = visited.has(path) || pathStack.has(path);
    const nextStack = new Set(pathStack);
    nextStack.add(path);
    linkedFiles.add(path);

    if (!duplicate) {
      visited.add(path);
    }

    const children = duplicate
      ? []
      : this.treeRelationsFor(adjacency, path, fileByPath, result)
          .map((childRelation, index) =>
            this.buildGitLabFileTreeNode(
              childRelation,
              fileByPath,
              result,
              adjacency,
              visited,
              linkedFiles,
              nextStack,
              `${idSuffix}-${index}`
            )
          )
          .filter((node): node is GitLabUseCaseTreeNode => !!node);

    return this.fileTreeNode(
      path,
      fileByPath.get(path) || null,
      relation,
      idSuffix,
      duplicate,
      children
    );
  }

  private fileTreeNode(
    path: string,
    file: GitLabEndpointUseCaseFileCandidate | null,
    relation: Partial<GitLabEndpointUseCaseRelation>,
    idSuffix: string,
    duplicate: boolean,
    children: GitLabUseCaseTreeNode[]
  ): GitLabUseCaseTreeNode {
    return {
      id: `file:${path}:${idSuffix}`,
      label: this.displayPathName(path),
      subtitle: path,
      role: file?.role || 'UNKNOWN',
      confidence: file?.confidence || relation.confidence || null,
      relationKind: relation.kind || null,
      relationReason: relation.reason || null,
      relationFrom: relation.from || null,
      relationTo: relation.to || null,
      file,
      duplicate,
      group: false,
      children
    };
  }

  private treeRelationsFor(
    adjacency: Map<string, GitLabEndpointUseCaseRelation[]>,
    key: string,
    fileByPath: Map<string, GitLabEndpointUseCaseFileCandidate>,
    result: GitLabEndpointUseCaseContextResponse
  ): GitLabEndpointUseCaseRelation[] {
    const seenTargets = new Set<string>();
    return [...(adjacency.get(key) || [])]
      .filter((relation) => {
        const target = this.resolveGitLabRelationFilePath(relation.to, fileByPath, result);
        if (!target || seenTargets.has(target)) {
          return false;
        }
        seenTargets.add(target);
        return true;
      })
      .sort((left, right) => {
        const leftPath = this.resolveGitLabRelationFilePath(left.to, fileByPath, result);
        const rightPath = this.resolveGitLabRelationFilePath(right.to, fileByPath, result);
        return (
          this.relationPriority(left.kind) - this.relationPriority(right.kind) ||
          this.compareGitLabFiles(fileByPath.get(leftPath || ''), fileByPath.get(rightPath || '')) ||
          (leftPath || '').localeCompare(rightPath || '')
        );
      });
  }

  private addTreeRelation(
    adjacency: Map<string, GitLabEndpointUseCaseRelation[]>,
    fromKey: string,
    relation: GitLabEndpointUseCaseRelation
  ): void {
    const relations = adjacency.get(fromKey) || [];
    relations.push(relation);
    adjacency.set(fromKey, relations);
  }

  private hasTreeRelation(
    adjacency: Map<string, GitLabEndpointUseCaseRelation[]>,
    fromKey: string,
    targetPath: string,
    fileByPath: Map<string, GitLabEndpointUseCaseFileCandidate>,
    result: GitLabEndpointUseCaseContextResponse
  ): boolean {
    return (adjacency.get(fromKey) || []).some(
      (relation) => this.resolveGitLabRelationFilePath(relation.to, fileByPath, result) === targetPath
    );
  }

  private resolveGitLabRelationSource(
    value: string | null | undefined,
    fileByPath: Map<string, GitLabEndpointUseCaseFileCandidate>,
    result: GitLabEndpointUseCaseContextResponse,
    rootKey: string
  ): string | null {
    const normalized = this.normalizeToken(value);
    if (!normalized) {
      return null;
    }

    const endpoint = result.endpoint;
    const endpointTokens = [endpoint?.endpointId, endpoint?.path, endpoint?.pathExpression]
      .map((token) => this.normalizeToken(token))
      .filter((token): token is string => !!token);

    if (endpointTokens.includes(normalized)) {
      return rootKey;
    }

    return this.resolveGitLabRelationFilePath(value, fileByPath, result);
  }

  private resolveGitLabRelationFilePath(
    value: string | null | undefined,
    fileByPath: Map<string, GitLabEndpointUseCaseFileCandidate>,
    result: GitLabEndpointUseCaseContextResponse
  ): string | null {
    const normalized = this.normalizePath(value);
    if (!normalized) {
      return null;
    }

    if (fileByPath.has(normalized)) {
      return normalized;
    }

    const normalizedLookup = normalized.toLowerCase();
    for (const path of fileByPath.keys()) {
      if (normalizedLookup.includes(path.toLowerCase())) {
        return path;
      }
    }

    const endpoint = result.endpoint;
    const endpointFile = this.normalizePath(endpoint?.filePath);
    const handlerSymbol = this.normalizeToken(this.gitLabEndpointHandlerSymbol(result));
    if (endpointFile && handlerSymbol && handlerSymbol === this.normalizeToken(value) && fileByPath.has(endpointFile)) {
      return endpointFile;
    }

    let bestPath: string | null = null;
    let bestScore = 0;
    for (const [path, file] of fileByPath.entries()) {
      const score = this.gitLabFileMatchScore(path, file, value);
      if (score > bestScore) {
        bestPath = path;
        bestScore = score;
      }
    }

    return bestScore > 0 ? bestPath : null;
  }

  private gitLabFileMatchScore(
    path: string,
    file: GitLabEndpointUseCaseFileCandidate,
    value: string | null | undefined
  ): number {
    const token = this.normalizeToken(value);
    if (!token) {
      return 0;
    }

    const pathToken = path.toLowerCase();
    const baseName = this.displayPathName(path).replace(/\.java$/i, '').toLowerCase();
    const typeName = this.relationTypeName(value)?.toLowerCase() || '';
    const simpleType = typeName.includes('.') ? typeName.slice(typeName.lastIndexOf('.') + 1) : typeName;
    const methodName = this.relationMethodName(value)?.toLowerCase() || '';
    const symbols = file.symbols || [];
    let score = 0;

    if (pathToken === token) {
      score += 100;
    }
    if (typeName && baseName === simpleType) {
      score += 80;
    }
    if (typeName && pathToken.includes(`/${simpleType.toLowerCase()}.java`)) {
      score += 60;
    }
    if (symbols.some((symbol) => this.normalizeToken(symbol) === token)) {
      score += 90;
    }
    if (methodName && symbols.some((symbol) => this.normalizeToken(symbol) === methodName)) {
      score += 35;
    }
    if (symbols.some((symbol) => token.endsWith(`#${this.normalizeToken(symbol)}`))) {
      score += 30;
    }
    if (simpleType && pathToken.includes(simpleType.toLowerCase())) {
      score += 15;
    }

    return score;
  }

  private resolveGitLabControllerPath(
    result: GitLabEndpointUseCaseContextResponse,
    fileByPath: Map<string, GitLabEndpointUseCaseFileCandidate>
  ): string | null {
    const endpointPath = this.normalizePath(result.endpoint?.filePath);
    if (endpointPath && fileByPath.has(endpointPath)) {
      return endpointPath;
    }
    return (
      [...fileByPath.entries()].find(([, file]) => file.role === 'CONTROLLER')?.[0] ||
      [...fileByPath.keys()][0] ||
      null
    );
  }

  private gitLabEndpointTreeLabel(result: GitLabEndpointUseCaseContextResponse): string {
    const endpoint = result.endpoint;
    const method = endpoint?.httpMethods?.length ? endpoint.httpMethods.join(', ') : 'ANY';
    const path = endpoint?.path || endpoint?.pathExpression || endpoint?.endpointId || 'Endpoint';
    return `${method} ${path}`;
  }

  private gitLabEndpointTreeSubtitle(result: GitLabEndpointUseCaseContextResponse): string | null {
    const endpoint = result.endpoint;
    const handler = [endpoint?.controllerClass, endpoint?.handlerMethod].filter(Boolean).join('#');
    if (handler) {
      return handler;
    }
    return result.repository?.projectName || null;
  }

  private gitLabEndpointHandlerSymbol(result: GitLabEndpointUseCaseContextResponse): string | null {
    const endpoint = result.endpoint;
    if (!endpoint?.controllerClass && !endpoint?.handlerMethod) {
      return null;
    }
    return [endpoint.controllerClass, endpoint.handlerMethod].filter(Boolean).join('#');
  }

  private flattenGitLabUseCaseTree(root: GitLabUseCaseTreeNode): GitLabUseCaseTreeRow[] {
    const rows: GitLabUseCaseTreeRow[] = [];
    const visit = (node: GitLabUseCaseTreeNode, depth: number): void => {
      rows.push({ node, depth });
      for (const child of node.children) {
        visit(child, depth + 1);
      }
    };
    visit(root, 0);
    return rows;
  }

  private compareGitLabFiles(
    left: GitLabEndpointUseCaseFileCandidate | undefined,
    right: GitLabEndpointUseCaseFileCandidate | undefined
  ): number {
    return (
      (left?.priority || 99) - (right?.priority || 99) ||
      this.confidenceRank(right?.confidence) - this.confidenceRank(left?.confidence) ||
      (left?.path || '').localeCompare(right?.path || '')
    );
  }

  private relationPriority(kind: string | null | undefined): number {
    switch (kind) {
      case 'ENDPOINT_HANDLER':
        return 0;
      case 'INJECTED_PORT_CALL':
        return 10;
      case 'INTERFACE_IMPLEMENTATION':
        return 12;
      case 'LOCAL_METHOD_CALL':
        return 20;
      case 'DOMAIN_METHOD_CALL':
        return 30;
      case 'REPOSITORY_CALL':
      case 'SPRING_DATA_BOUNDARY':
        return 40;
      case 'MAPPER_CALL':
        return 50;
      case 'STATIC_METHOD_CALL':
        return 60;
      case 'EXTERNAL_BOUNDARY':
      case 'OPENAPI_CONTRACT':
        return 70;
      default:
        return 99;
    }
  }

  private confidenceRank(confidence: string | null | undefined): number {
    switch ((confidence || '').toLowerCase()) {
      case 'high':
        return 3;
      case 'medium':
        return 2;
      case 'low':
        return 1;
      default:
        return 0;
    }
  }

  private relationTypeName(value: string | null | undefined): string | null {
    const token = value?.trim();
    if (!token) {
      return null;
    }
    const methodIndex = token.lastIndexOf('#');
    return methodIndex >= 0 ? token.slice(0, methodIndex) : token;
  }

  private relationMethodName(value: string | null | undefined): string | null {
    const token = value?.trim();
    if (!token) {
      return null;
    }
    const methodIndex = token.lastIndexOf('#');
    return methodIndex >= 0 ? token.slice(methodIndex + 1) : null;
  }

  private normalizePath(value: string | null | undefined): string | null {
    const token = value?.trim().replace(/\\/g, '/').replace(/^\/+/, '');
    return token && token.length > 0 ? token : null;
  }

  private normalizeToken(value: string | null | undefined): string | null {
    const token = value?.trim().replace(/\\/g, '/').toLowerCase();
    return token && token.length > 0 ? token : null;
  }

  private displayToken(value: string | null | undefined): string {
    return (value || '')
      .replace(/_/g, ' ')
      .replace(/\bIMPLEMENTATION\b/g, 'IMPL')
      .trim();
  }

  private cssToken(value: string): string {
    return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  }

}

