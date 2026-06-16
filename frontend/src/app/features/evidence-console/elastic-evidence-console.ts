import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { Observable } from 'rxjs';

import { ApiErrorResponse } from '../../core/models/analysis.models';
import {
  ElasticHttpCallLogsPayload,
  ElasticHttpCallSummaryPayload,
  ElasticLogDetailLevel,
  ElasticLogSearchPayload,
  EvidenceApiService
} from '../../core/services/evidence-api.service';
import { copyTextToClipboard } from '../../core/utils/clipboard.utils';

type ToolStateStatus = 'idle' | 'loading' | 'success' | 'error';
type ElasticToolKey = 'logSearch' | 'httpSummary' | 'httpLogs';
type ElasticJsonPayloadKey = 'request' | 'response';

interface ToolState {
  status: ToolStateStatus;
  statusCode: number | null;
  message: string;
  endpoint: string;
  requestJson: string;
  responseJson: string;
  durationMs: number | null;
}

interface ElasticToolDefinition {
  key: ElasticToolKey;
  label: string;
  category: string;
  endpoint: string;
  summary: string;
}

interface ElasticToolGroup {
  category: string;
  tools: ElasticToolDefinition[];
}

const ELASTIC_TOOLS: ElasticToolDefinition[] = [
  {
    key: 'logSearch',
    label: 'Log Search',
    category: 'Logs',
    endpoint: 'logs/search',
    summary:
      'Wyszukuje logi po correlationId. Backend dobiera Kibana space, index pattern, auth i limity z konfiguracji.'
  },
  {
    key: 'httpSummary',
    label: 'HTTP Call Summary',
    category: 'HTTP diagnostics',
    endpoint: 'logs/http-calls/summary',
    summary:
      'Porównuje podobne wywołania HTTP po path prefixie w wybranym oknie czasu.'
  },
  {
    key: 'httpLogs',
    label: 'HTTP Call Logs',
    category: 'HTTP diagnostics',
    endpoint: 'logs/http-calls/fetch',
    summary:
      'Pobiera logi dla konkretnego correlationId albo przykładu HTTP path/status/metoda.'
  }
];

const ELASTIC_TOOL_GROUPS = ELASTIC_TOOLS.reduce<ElasticToolGroup[]>((groups, tool) => {
  const existingGroup = groups.find((group) => group.category === tool.category);
  if (existingGroup) {
    existingGroup.tools.push(tool);
  } else {
    groups.push({
      category: tool.category,
      tools: [tool]
    });
  }
  return groups;
}, []);

@Component({
  selector: 'app-elastic-evidence-console',
  imports: [ReactiveFormsModule],
  templateUrl: './elastic-evidence-console.html',
  styleUrl: './elastic-evidence-console.scss'
})
export class ElasticEvidenceConsoleComponent {
  private readonly evidenceApi = inject(EvidenceApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly tools = ELASTIC_TOOLS;
  readonly toolGroups = ELASTIC_TOOL_GROUPS;
  readonly selectedToolKey = signal<ElasticToolKey>('logSearch');
  readonly copiedJsonPayloadKey = signal<ElasticJsonPayloadKey | null>(null);
  readonly selectedTool = computed(
    () => this.tools.find((tool) => tool.key === this.selectedToolKey()) ?? this.tools[0]
  );
  readonly toolStates = signal<Record<ElasticToolKey, ToolState>>(this.createInitialToolStates());
  readonly state = computed(() => this.toolStates()[this.selectedToolKey()]);
  readonly hasResult = computed(() => this.state().status !== 'idle');

  readonly scopeForm = new FormGroup({
    correlationId: new FormControl('', { nonNullable: true }),
    timeWindowDays: new FormControl('7', { nonNullable: true }),
    serviceName: new FormControl('', { nonNullable: true })
  });

  readonly httpSummaryForm = new FormGroup({
    pathPattern: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    method: new FormControl('', { nonNullable: true }),
    sampleSize: new FormControl('300', { nonNullable: true })
  });

  readonly httpLogsForm = new FormGroup({
    path: new FormControl('', { nonNullable: true }),
    status: new FormControl('', { nonNullable: true }),
    method: new FormControl('', { nonNullable: true }),
    size: new FormControl('50', { nonNullable: true }),
    detailLevel: new FormControl<ElasticLogDetailLevel>('COMPACT', { nonNullable: true })
  });

  submit(event: Event): void {
    event.preventDefault();

    switch (this.selectedToolKey()) {
      case 'httpSummary':
        this.submitElasticHttpSummary();
        return;
      case 'httpLogs':
        this.submitElasticHttpLogs();
        return;
      default:
        this.submitElasticLogSearch();
    }
  }

  selectTool(toolKey: ElasticToolKey): void {
    if (this.tools.some((tool) => tool.key === toolKey)) {
      this.selectedToolKey.set(toolKey);
    }
  }

  resetSelectedFields(): void {
    switch (this.selectedToolKey()) {
      case 'httpSummary':
        this.httpSummaryForm.reset({
          pathPattern: '',
          method: '',
          sampleSize: '300'
        });
        return;
      case 'httpLogs':
        this.httpLogsForm.reset({
          path: '',
          status: '',
          method: '',
          size: '50',
          detailLevel: 'COMPACT'
        });
        return;
      default:
        this.scopeForm.controls.correlationId.reset('');
    }
  }

  controlInvalid(control: AbstractControl<unknown, unknown>): boolean {
    return control.invalid && (control.dirty || control.touched);
  }

  correlationIdMissingForSelectedTool(): boolean {
    return (
      this.selectedToolKey() === 'logSearch' &&
      this.scopeForm.controls.correlationId.value.trim().length === 0 &&
      (this.scopeForm.controls.correlationId.dirty || this.scopeForm.controls.correlationId.touched)
    );
  }

  httpLogsMissingInputForSelectedTool(): boolean {
    return (
      this.selectedToolKey() === 'httpLogs' &&
      this.scopeForm.controls.correlationId.value.trim().length === 0 &&
      this.httpLogsForm.controls.path.value.trim().length === 0 &&
      (this.scopeForm.controls.correlationId.touched || this.httpLogsForm.controls.path.touched)
    );
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

  toolButtonClass(tool: ElasticToolDefinition): string {
    return tool.key === this.selectedToolKey()
      ? 'elastic-tool-button elastic-tool-button--active'
      : 'elastic-tool-button';
  }

  toolStateStatus(tool: ElasticToolDefinition): ToolStateStatus {
    return this.toolStates()[tool.key]?.status ?? 'idle';
  }

  durationLabel(durationMs: number | null): string {
    if (durationMs === null) {
      return 'n/a';
    }

    return durationMs < 1000 ? `${durationMs} ms` : `${(durationMs / 1000).toFixed(1)} s`;
  }

  async copyJsonPayload(key: ElasticJsonPayloadKey, value: string): Promise<void> {
    if (!value) {
      return;
    }

    const copied = await copyTextToClipboard(value);
    if (!copied) {
      return;
    }

    this.copiedJsonPayloadKey.set(key);
    window.setTimeout(() => {
      if (this.copiedJsonPayloadKey() === key) {
        this.copiedJsonPayloadKey.set(null);
      }
    }, 1600);
  }

  downloadJsonPayload(key: ElasticJsonPayloadKey, value: string): void {
    if (!value) {
      return;
    }

    const blob = new Blob([value], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = this.elasticJsonPayloadFileName(key);
    link.click();
    URL.revokeObjectURL(url);
  }

  private submitElasticLogSearch(): void {
    const correlationId = this.scopeForm.controls.correlationId.value.trim();

    if (!correlationId) {
      this.scopeForm.controls.correlationId.markAsTouched();
      this.setCurrentToolState(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Uzupełnij correlationId, aby wywołać endpoint Elastica.'
        })
      );
      return;
    }

    const payload: ElasticLogSearchPayload = { correlationId };

    this.runRequest(
      this.evidenceApi.searchElasticLogs(payload),
      this.selectedTool(),
      payload
    );
  }

  private submitElasticHttpSummary(): void {
    if (this.httpSummaryForm.invalid) {
      this.httpSummaryForm.markAllAsTouched();
      this.setCurrentToolState(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Podaj path prefix dla porównania wywołań HTTP.'
        })
      );
      return;
    }

    const payload: ElasticHttpCallSummaryPayload = {
      pathPattern: this.httpSummaryForm.controls.pathPattern.value.trim(),
      method: this.optionalValue(this.httpSummaryForm.controls.method.value),
      serviceName: this.optionalValue(this.scopeForm.controls.serviceName.value),
      timeWindowDays: this.optionalNumber(this.scopeForm.controls.timeWindowDays.value),
      sampleSize: this.optionalNumber(this.httpSummaryForm.controls.sampleSize.value)
    };

    this.runRequest(
      this.evidenceApi.summarizeElasticHttpCalls(payload),
      this.selectedTool(),
      payload
    );
  }

  private submitElasticHttpLogs(): void {
    const correlationId = this.optionalValue(this.scopeForm.controls.correlationId.value);
    const path = this.optionalValue(this.httpLogsForm.controls.path.value);

    if (!correlationId && !path) {
      this.scopeForm.controls.correlationId.markAsTouched();
      this.httpLogsForm.controls.path.markAsTouched();
      this.setCurrentToolState(
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
      status: this.optionalNumber(this.httpLogsForm.controls.status.value),
      method: this.optionalValue(this.httpLogsForm.controls.method.value),
      timeWindowDays: this.optionalNumber(this.scopeForm.controls.timeWindowDays.value),
      size: this.optionalNumber(this.httpLogsForm.controls.size.value),
      detailLevel: this.httpLogsForm.controls.detailLevel.value
    };

    this.runRequest(
      this.evidenceApi.fetchElasticHttpCallLogs(payload),
      this.selectedTool(),
      payload
    );
  }

  private runRequest(
    request$: Observable<unknown>,
    selectedTool: ElasticToolDefinition,
    payload: unknown
  ): void {
    const endpoint = `/api/elasticsearch/${selectedTool.endpoint}`;
    const requestJson = this.toFormattedJson({
      endpoint,
      method: 'POST',
      body: payload
    });
    const startedAt = Date.now();

    this.setToolState(selectedTool.key, {
      status: 'loading',
      statusCode: null,
      message: `Wysyłamy request do ${endpoint}...`,
      endpoint,
      requestJson,
      responseJson: this.toFormattedJson({
        status: 'WAITING',
        endpoint,
        request: payload
      }),
      durationMs: null
    });

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        this.setToolState(selectedTool.key, {
          status: 'success',
          statusCode: 200,
          message: 'Backend zwrócił odpowiedź JSON z Elasticsearch helper API.',
          endpoint,
          requestJson,
          responseJson: this.toFormattedJson(response),
          durationMs: Date.now() - startedAt
        });
      },
      error: (error) =>
        this.setToolState(
          selectedTool.key,
          this.toErrorState(error, endpoint, requestJson, Date.now() - startedAt)
        )
    });
  }

  private toErrorState(
    error: unknown,
    endpoint = '',
    requestJson = '',
    durationMs: number | null = null
  ): ToolState {
    if (error instanceof HttpErrorResponse) {
      const payload = this.normalizeErrorPayload(error.error, error.status, error.message);
      return {
        status: 'error',
        statusCode: error.status || null,
        message: payload.message,
        endpoint,
        requestJson,
        responseJson: this.toFormattedJson(payload.body),
        durationMs
      };
    }

    return this.errorStateFromPayload(
      {
        code: 'REQUEST_FAILED',
        message: error instanceof Error ? error.message : 'Request zakończył się błędem.'
      },
      endpoint,
      requestJson,
      durationMs
    );
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

  private errorStateFromPayload(
    payload: { code: string; message: string },
    endpoint = '',
    requestJson = '',
    durationMs: number | null = null
  ): ToolState {
    return {
      status: 'error',
      statusCode: null,
      message: payload.message,
      endpoint,
      requestJson,
      responseJson: this.toFormattedJson(payload),
      durationMs
    };
  }

  private idleState(message: string): ToolState {
    return {
      status: 'idle',
      statusCode: null,
      message,
      endpoint: '',
      requestJson: '',
      responseJson: '',
      durationMs: null
    };
  }

  private createInitialToolStates(): Record<ElasticToolKey, ToolState> {
    return this.tools.reduce(
      (states, tool) => ({
        ...states,
        [tool.key]: this.idleState('Wybierz zakres, element testowy i uruchom request.')
      }),
      {} as Record<ElasticToolKey, ToolState>
    );
  }

  private setCurrentToolState(state: ToolState): void {
    this.setToolState(this.selectedToolKey(), state);
  }

  private setToolState(toolKey: ElasticToolKey, state: ToolState): void {
    this.toolStates.update((states) => ({
      ...states,
      [toolKey]: state
    }));
  }

  private optionalValue(raw: string): string | undefined {
    const value = String(raw || '').trim();
    return value.length > 0 ? value : undefined;
  }

  private optionalNumber(raw: string): number | undefined {
    const value = String(raw || '').trim();
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

  private elasticJsonPayloadFileName(key: ElasticJsonPayloadKey): string {
    const safeEndpoint = this.selectedTool().endpoint.replace(/[^a-z0-9]+/gi, '-').toLowerCase();
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    return `elastic-${safeEndpoint}-${key}-${timestamp}.json`;
  }
}
