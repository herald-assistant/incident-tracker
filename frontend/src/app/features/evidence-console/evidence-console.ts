import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, WritableSignal, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { ActivatedRoute, RouterLink, RouterLinkActive } from '@angular/router';
import { Observable } from 'rxjs';

import { ApiErrorResponse } from '../../core/models/analysis.models';
import {
  ElasticHttpCallLogsPayload,
  ElasticHttpCallSummaryPayload,
  ElasticLogDetailLevel,
  ElasticLogSearchPayload,
  EvidenceApiService,
  GitLabEndpointUseCaseContextPayload,
  GitLabEndpointUseCaseContextResponse,
  GitLabEndpointUseCaseOutputMode,
  GitLabRepositoryEndpoint,
  GitLabRepositoryEndpointsPayload,
  GitLabRepositoryEndpointsResponse,
  GitLabRepositorySearchPayload,
  GitLabSourceResolvePayload
} from '../../core/services/evidence-api.service';

type ToolStateStatus = 'idle' | 'loading' | 'success' | 'error';
type EvidenceIntegrationView = 'elastic' | 'gitlab';

interface ToolState {
  status: ToolStateStatus;
  statusCode: number | null;
  message: string;
  responseJson: string;
  response: unknown | null;
}

@Component({
  selector: 'app-evidence-console',
  imports: [ReactiveFormsModule, RouterLink, RouterLinkActive],
  templateUrl: './evidence-console.html',
  styleUrl: './evidence-console.scss'
})
export class EvidenceConsoleComponent {
  private readonly evidenceApi = inject(EvidenceApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly routeData = toSignal(this.route.data, {
    initialValue: this.route.snapshot.data
  });

  readonly activeIntegration = computed<EvidenceIntegrationView>(() =>
    this.routeData()['integration'] === 'gitlab' ? 'gitlab' : 'elastic'
  );

  readonly elasticForm = new FormGroup({
    correlationId: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    })
  });

  readonly elasticHttpSummaryForm = new FormGroup({
    pathPattern: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    method: new FormControl('', { nonNullable: true }),
    serviceName: new FormControl('', { nonNullable: true }),
    timeWindowDays: new FormControl('7', { nonNullable: true }),
    sampleSize: new FormControl('300', { nonNullable: true })
  });

  readonly elasticHttpLogsForm = new FormGroup({
    correlationId: new FormControl('', { nonNullable: true }),
    path: new FormControl('', { nonNullable: true }),
    status: new FormControl('', { nonNullable: true }),
    method: new FormControl('', { nonNullable: true }),
    timeWindowDays: new FormControl('7', { nonNullable: true }),
    size: new FormControl('50', { nonNullable: true }),
    detailLevel: new FormControl<ElasticLogDetailLevel>('COMPACT', { nonNullable: true })
  });

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
    sourcePathPrefix: new FormControl('src/main/java', { nonNullable: true }),
    maxScannedFiles: new FormControl('120', {
      nonNullable: true,
      validators: [Validators.min(1), Validators.max(250)]
    })
  });

  readonly gitLabUseCaseForm = new FormGroup({
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
    sourcePathPrefix: new FormControl('src/main/java', { nonNullable: true }),
    outputMode: new FormControl<GitLabEndpointUseCaseOutputMode>('COMPACT', {
      nonNullable: true
    }),
    maxDepth: new FormControl('8', {
      nonNullable: true,
      validators: [Validators.min(1), Validators.max(20)]
    }),
    maxNodes: new FormControl('80', {
      nonNullable: true,
      validators: [Validators.min(1), Validators.max(200)]
    }),
    includeAsyncConsumers: new FormControl(false, { nonNullable: true }),
    reason: new FormControl('Manual verification from GitLab console', { nonNullable: true })
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

  readonly elasticState = signal<ToolState>(
    this.idleState('Podaj correlationId i uruchom helper Elastica.')
  );
  readonly elasticHttpSummaryState = signal<ToolState>(
    this.idleState('Podaj path prefix, aby porównać podobne wywołania HTTP w Elasticu.')
  );
  readonly elasticHttpLogsState = signal<ToolState>(
    this.idleState('Podaj correlationId albo konkretny path, aby pobrać logi wywołania HTTP.')
  );
  readonly gitLabRepositoryState = signal<ToolState>(
    this.idleState('Wpisz hinty projektu, aby przetestować mapowanie component -> repo.')
  );
  readonly gitLabEndpointState = signal<ToolState>(
    this.idleState('Podaj scope repozytorium, aby znaleźć endpointy Spring REST.')
  );
  readonly gitLabUseCaseState = signal<ToolState>(
    this.idleState('Podaj endpoint, aby zbudować kontekst use-case z GitLaba.')
  );
  readonly gitLabSourceState = signal<ToolState>(
    this.idleState('Uzupełnij dane repozytorium i symbol, aby przetestować source resolve.')
  );

  readonly gitLabEndpointResult = computed(() =>
    this.asGitLabEndpointResult(this.gitLabEndpointState().response)
  );
  readonly gitLabUseCaseResult = computed(() =>
    this.asGitLabUseCaseResult(this.gitLabUseCaseState().response)
  );

  submitElastic(event: Event): void {
    event.preventDefault();

    if (this.elasticForm.invalid) {
      this.elasticForm.markAllAsTouched();
      this.elasticState.set(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Uzupełnij correlationId, aby wywołać endpoint Elastica.'
        })
      );
      return;
    }

    const payload: ElasticLogSearchPayload = {
      correlationId: this.elasticForm.controls.correlationId.value.trim()
    };

    this.runRequest(
      this.elasticState,
      this.evidenceApi.searchElasticLogs(payload),
      payload,
      'Wysyłamy request do /api/elasticsearch/logs/search...'
    );
  }

  submitElasticHttpSummary(event: Event): void {
    event.preventDefault();

    if (this.elasticHttpSummaryForm.invalid) {
      this.elasticHttpSummaryForm.markAllAsTouched();
      this.elasticHttpSummaryState.set(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Podaj path prefix dla porównania wywołań HTTP.'
        })
      );
      return;
    }

    const payload: ElasticHttpCallSummaryPayload = {
      pathPattern: this.elasticHttpSummaryForm.controls.pathPattern.value.trim(),
      method: this.optionalValue(this.elasticHttpSummaryForm.controls.method.value),
      serviceName: this.optionalValue(this.elasticHttpSummaryForm.controls.serviceName.value),
      timeWindowDays: this.optionalNumber(this.elasticHttpSummaryForm.controls.timeWindowDays.value),
      sampleSize: this.optionalNumber(this.elasticHttpSummaryForm.controls.sampleSize.value)
    };

    this.runRequest(
      this.elasticHttpSummaryState,
      this.evidenceApi.summarizeElasticHttpCalls(payload),
      payload,
      'Wysyłamy request do /api/elasticsearch/logs/http-calls/summary...'
    );
  }

  submitElasticHttpLogs(event: Event): void {
    event.preventDefault();

    const correlationId = this.optionalValue(this.elasticHttpLogsForm.controls.correlationId.value);
    const path = this.optionalValue(this.elasticHttpLogsForm.controls.path.value);

    if (!correlationId && !path) {
      this.elasticHttpLogsForm.markAllAsTouched();
      this.elasticHttpLogsState.set(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Podaj correlationId albo path, aby pobrać logi wywołania HTTP.'
        })
      );
      return;
    }

    const payload: ElasticHttpCallLogsPayload = {
      correlationId,
      path,
      status: this.optionalNumber(this.elasticHttpLogsForm.controls.status.value),
      method: this.optionalValue(this.elasticHttpLogsForm.controls.method.value),
      timeWindowDays: this.optionalNumber(this.elasticHttpLogsForm.controls.timeWindowDays.value),
      size: this.optionalNumber(this.elasticHttpLogsForm.controls.size.value),
      detailLevel: this.elasticHttpLogsForm.controls.detailLevel.value
    };

    this.runRequest(
      this.elasticHttpLogsState,
      this.evidenceApi.fetchElasticHttpCallLogs(payload),
      payload,
      'Wysyłamy request do /api/elasticsearch/logs/http-calls/fetch...'
    );
  }

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
      sourcePathPrefix: this.optionalValue(this.gitLabEndpointForm.controls.sourcePathPrefix.value),
      maxScannedFiles: this.optionalNumber(this.gitLabEndpointForm.controls.maxScannedFiles.value)
    };

    this.runRequest(
      this.gitLabEndpointState,
      this.evidenceApi.listGitLabRepositoryEndpoints(payload),
      payload,
      'Wysyłamy request do /api/gitlab/repository/endpoints...'
    );
  }

  submitGitLabUseCaseContext(event: Event): void {
    event.preventDefault();

    if (this.gitLabUseCaseForm.invalid) {
      this.gitLabUseCaseForm.markAllAsTouched();
      this.gitLabUseCaseState.set(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Uzupełnij group, projectName i branch dla GitLab use-case context.'
        })
      );
      return;
    }

    const payload: GitLabEndpointUseCaseContextPayload = {
      group: this.gitLabUseCaseForm.controls.group.value.trim(),
      projectName: this.gitLabUseCaseForm.controls.projectName.value.trim(),
      branch: this.gitLabUseCaseForm.controls.branch.value.trim(),
      endpointId: this.optionalValue(this.gitLabUseCaseForm.controls.endpointId.value),
      httpMethod: this.optionalValue(this.gitLabUseCaseForm.controls.httpMethod.value),
      endpointPath: this.optionalValue(this.gitLabUseCaseForm.controls.endpointPath.value),
      sourcePathPrefix: this.optionalValue(this.gitLabUseCaseForm.controls.sourcePathPrefix.value),
      outputMode: this.gitLabUseCaseForm.controls.outputMode.value,
      maxDepth: this.optionalNumber(this.gitLabUseCaseForm.controls.maxDepth.value),
      maxNodes: this.optionalNumber(this.gitLabUseCaseForm.controls.maxNodes.value),
      includeAsyncConsumers: this.gitLabUseCaseForm.controls.includeAsyncConsumers.value,
      reason: this.optionalValue(this.gitLabUseCaseForm.controls.reason.value)
    };

    this.runRequest(
      this.gitLabUseCaseState,
      this.evidenceApi.buildGitLabEndpointUseCaseContext(payload),
      payload,
      'Wysyłamy request do /api/gitlab/repository/endpoint-use-case-context...'
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

  endpointMethods(endpoint: GitLabRepositoryEndpoint): string {
    return endpoint.httpMethods?.length ? endpoint.httpMethods.join(', ') : 'ANY';
  }

  prepareGitLabUseCaseContext(endpoint: GitLabRepositoryEndpoint): void {
    this.gitLabUseCaseForm.patchValue({
      group: this.gitLabEndpointForm.controls.group.value.trim(),
      projectName: this.gitLabEndpointForm.controls.projectName.value.trim(),
      branch: this.gitLabEndpointForm.controls.branch.value.trim(),
      endpointId: endpoint.endpointId,
      httpMethod: endpoint.httpMethods?.[0] && endpoint.httpMethods[0] !== 'ANY'
        ? endpoint.httpMethods[0]
        : '',
      endpointPath: endpoint.path || endpoint.pathExpression || '',
      sourcePathPrefix: this.gitLabEndpointForm.controls.sourcePathPrefix.value.trim() || 'src/main/java',
      reason: `Manual verification of ${endpoint.endpointId}`
    });
    this.gitLabUseCaseState.set(
      this.idleState('Endpoint przeniesiony z inventory. Możesz uruchomić build context.')
    );
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
    const value = raw.trim();
    return value.length > 0 ? value : undefined;
  }

  private optionalNumber(raw: string): number | undefined {
    const value = raw.trim();
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

  private asGitLabEndpointResult(response: unknown): GitLabRepositoryEndpointsResponse | null {
    if (!response || typeof response !== 'object' || Array.isArray(response)) {
      return null;
    }

    const record = response as Partial<GitLabRepositoryEndpointsResponse>;
    return Array.isArray(record.endpoints) ? (record as GitLabRepositoryEndpointsResponse) : null;
  }

  private asGitLabUseCaseResult(response: unknown): GitLabEndpointUseCaseContextResponse | null {
    if (!response || typeof response !== 'object' || Array.isArray(response)) {
      return null;
    }

    const record = response as Partial<GitLabEndpointUseCaseContextResponse>;
    return record.graph && Array.isArray(record.classList)
      ? (record as GitLabEndpointUseCaseContextResponse)
      : null;
  }
}
