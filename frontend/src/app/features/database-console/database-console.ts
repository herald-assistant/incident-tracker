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
  DatabaseApiService,
  DatabaseToolScopePayload
} from '../../core/services/database-api.service';
import { copyTextToClipboard } from '../../core/utils/clipboard.utils';

type ToolStateStatus = 'idle' | 'loading' | 'success' | 'error';
type DatabaseJsonPayloadKey = 'request' | 'response';

type DatabaseToolKey =
  | 'scope'
  | 'findTables'
  | 'findColumns'
  | 'describeTable'
  | 'existsByKey'
  | 'countRows'
  | 'groupCount'
  | 'sampleRows'
  | 'checkOrphans'
  | 'findRelationships'
  | 'joinCount'
  | 'joinSample'
  | 'compareMapping'
  | 'readonlySql';

interface ToolState {
  status: ToolStateStatus;
  statusCode: number | null;
  message: string;
  endpoint: string;
  requestJson: string;
  responseJson: string;
  durationMs: number | null;
}

interface DatabaseToolDefinition {
  key: DatabaseToolKey;
  label: string;
  category: string;
  endpoint: string;
  summary: string;
  payloadRequired: boolean;
  examplePayload: unknown;
}

interface DatabaseToolGroup {
  category: string;
  tools: DatabaseToolDefinition[];
}

const DATABASE_TOOLS: DatabaseToolDefinition[] = [
  {
    key: 'scope',
    label: 'Get Scope',
    category: 'Discovery',
    endpoint: 'scope',
    summary: 'Zwraca aplikacje, schematy i reguły bezpieczeństwa dla środowiska.',
    payloadRequired: false,
    examplePayload: {}
  },
  {
    key: 'findTables',
    label: 'Find Tables',
    category: 'Discovery',
    endpoint: 'tables/search',
    summary: 'Szuka tabel lub widoków po aplikacji, fragmencie nazwy i hincie domenowym.',
    payloadRequired: true,
    examplePayload: {
      applicationPattern: 'crm-service',
      tableNamePattern: 'CUSTOMER_PROFILE',
      entityOrKeywordHint: 'CustomerProfileEntity',
      limit: 10
    }
  },
  {
    key: 'findColumns',
    label: 'Find Columns',
    category: 'Discovery',
    endpoint: 'columns/search',
    summary: 'Szuka kolumn po aplikacji, tabeli, nazwie kolumny albo hincie pola Javy.',
    payloadRequired: true,
    examplePayload: {
      applicationPattern: 'crm-service',
      tableNamePattern: 'CUSTOMER_PROFILE',
      columnNamePattern: 'STATUS',
      javaFieldNameHint: 'statusCode',
      limit: 10
    }
  },
  {
    key: 'describeTable',
    label: 'Describe Table',
    category: 'Metadata',
    endpoint: 'tables/describe',
    summary: 'Opisuje dokładne schema.table: kolumny, PK/FK, indeksy i relacje.',
    payloadRequired: true,
    examplePayload: {
      table: {
        schema: 'CRM_APP_1',
        tableName: 'CUSTOMER_PROFILE'
      }
    }
  },
  {
    key: 'existsByKey',
    label: 'Exists By Key',
    category: 'Rows',
    endpoint: 'rows/exists-by-key',
    summary: 'Sprawdza istnienie rekordu po kluczu technicznym lub biznesowym.',
    payloadRequired: true,
    examplePayload: {
      table: {
        schema: 'CRM_APP_1',
        tableName: 'CUSTOMER_PROFILE'
      },
      keyValues: [
        {
          column: 'ID',
          value: '123'
        }
      ],
      projectionColumns: ['ID', 'STATUS']
    }
  },
  {
    key: 'countRows',
    label: 'Count Rows',
    category: 'Rows',
    endpoint: 'rows/count',
    summary: 'Liczy rekordy w dokładnej tabeli z typed filters i bind parameters.',
    payloadRequired: true,
    examplePayload: {
      table: {
        schema: 'CRM_APP_1',
        tableName: 'CUSTOMER_PROFILE'
      },
      filters: [
        {
          column: 'STATUS',
          operator: 'EQ',
          values: ['ERROR']
        }
      ]
    }
  },
  {
    key: 'groupCount',
    label: 'Group Count',
    category: 'Rows',
    endpoint: 'rows/group-count',
    summary: 'Grupuje rekordy po kolumnach, np. statusach albo typach błędów.',
    payloadRequired: true,
    examplePayload: {
      table: {
        schema: 'CRM_APP_1',
        tableName: 'CUSTOMER_PROFILE'
      },
      groupByColumns: ['STATUS'],
      filters: [],
      limit: 10
    }
  },
  {
    key: 'sampleRows',
    label: 'Sample Rows',
    category: 'Rows',
    endpoint: 'rows/sample',
    summary: 'Pobiera małą, jawną projekcję kolumn z opcjonalnym filtrem i sortowaniem.',
    payloadRequired: true,
    examplePayload: {
      table: {
        schema: 'CRM_APP_1',
        tableName: 'CUSTOMER_PROFILE'
      },
      columns: ['ID', 'STATUS', 'UPDATED_AT'],
      filters: [
        {
          column: 'STATUS',
          operator: 'EQ',
          values: ['ERROR']
        }
      ],
      orderBy: [
        {
          column: 'UPDATED_AT',
          direction: 'DESC'
        }
      ],
      limit: 5
    }
  },
  {
    key: 'checkOrphans',
    label: 'Check Orphans',
    category: 'Relationships',
    endpoint: 'relationships/orphans',
    summary: 'Sprawdza osierocone rekordy child -> parent dla jawnych tabel i kolumn.',
    payloadRequired: true,
    examplePayload: {
      childTable: {
        schema: 'CRM_APP_1',
        tableName: 'CUSTOMER_INTERACTION'
      },
      childColumn: 'CUSTOMER_ID',
      parentTable: {
        schema: 'CRM_APP_1',
        tableName: 'CUSTOMER_PROFILE'
      },
      parentColumn: 'ID',
      childFilters: [],
      sampleLimit: 5
    }
  },
  {
    key: 'findRelationships',
    label: 'Find Relationships',
    category: 'Relationships',
    endpoint: 'relationships/search',
    summary: 'Zwraca zadeklarowane i opcjonalnie inferowane relacje dla tabel.',
    payloadRequired: true,
    examplePayload: {
      tables: [
        {
          schema: 'CRM_APP_1',
          tableName: 'CUSTOMER_PROFILE'
        }
      ],
      depth: 1,
      includeInferred: true
    }
  },
  {
    key: 'joinCount',
    label: 'Join Count',
    category: 'Joins',
    endpoint: 'joins/count',
    summary: 'Liczy wynik jawnego join planu bez pobierania próbek danych.',
    payloadRequired: true,
    examplePayload: {
      tables: [
        {
          schema: 'CRM_APP_1',
          tableName: 'CUSTOMER_PROFILE'
        },
        {
          schema: 'CRM_APP_1',
          tableName: 'CUSTOMER_INTERACTION'
        }
      ],
      joins: [
        {
          left: {
            table: {
              schema: 'CRM_APP_1',
              tableName: 'CUSTOMER_INTERACTION'
            },
            column: 'CUSTOMER_ID'
          },
          right: {
            table: {
              schema: 'CRM_APP_1',
              tableName: 'CUSTOMER_PROFILE'
            },
            column: 'ID'
          },
          type: 'INNER'
        }
      ],
      filters: []
    }
  },
  {
    key: 'joinSample',
    label: 'Join Sample',
    category: 'Joins',
    endpoint: 'joins/sample',
    summary: 'Pobiera małą projekcję z jawnego join planu.',
    payloadRequired: true,
    examplePayload: {
      tables: [
        {
          schema: 'CRM_APP_1',
          tableName: 'CUSTOMER_PROFILE'
        },
        {
          schema: 'CRM_APP_1',
          tableName: 'CUSTOMER_INTERACTION'
        }
      ],
      joins: [
        {
          left: {
            table: {
              schema: 'CRM_APP_1',
              tableName: 'CUSTOMER_INTERACTION'
            },
            column: 'CUSTOMER_ID'
          },
          right: {
            table: {
              schema: 'CRM_APP_1',
              tableName: 'CUSTOMER_PROFILE'
            },
            column: 'ID'
          },
          type: 'INNER'
        }
      ],
      columns: [
        {
          column: {
            table: {
              schema: 'CRM_APP_1',
              tableName: 'CUSTOMER_PROFILE'
            },
            column: 'ID'
          },
          alias: 'CUSTOMER_ID'
        }
      ],
      filters: [],
      limit: 5
    }
  },
  {
    key: 'compareMapping',
    label: 'Compare Mapping',
    category: 'Metadata',
    endpoint: 'mappings/compare-table',
    summary: 'Porównuje tabelę z oczekiwanym mappingiem ORM z kodu lub evidence.',
    payloadRequired: true,
    examplePayload: {
      actualTable: {
        schema: 'CRM_APP_1',
        tableName: 'CUSTOMER_PROFILE'
      },
      expectedColumns: [
        {
          javaField: 'id',
          expectedColumn: 'ID',
          expectedSqlType: 'NUMBER',
          nullable: false,
          id: true
        }
      ],
      expectedRelationships: []
    }
  },
  {
    key: 'readonlySql',
    label: 'Readonly SQL',
    category: 'Raw SQL',
    endpoint: 'sql/readonly',
    summary: 'Uruchamia pojedynczy SELECT/WITH SELECT tylko gdy raw SQL jest jawnie włączony.',
    payloadRequired: true,
    examplePayload: {
      sql: 'SELECT 1 AS VALUE FROM dual',
      reason: 'Ręczny test połączenia przez konsolę /database.',
      maxRows: 5
    }
  }
];

const DATABASE_TOOL_GROUPS = DATABASE_TOOLS.reduce<DatabaseToolGroup[]>((groups, tool) => {
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
  selector: 'app-database-console',
  imports: [ReactiveFormsModule],
  templateUrl: './database-console.html',
  styleUrl: './database-console.scss'
})
export class DatabaseConsoleComponent {
  private readonly databaseApi = inject(DatabaseApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly tools = DATABASE_TOOLS;
  readonly toolGroups = DATABASE_TOOL_GROUPS;
  readonly selectedToolKey = signal<DatabaseToolKey>('scope');
  readonly copiedJsonPayloadKey = signal<DatabaseJsonPayloadKey | null>(null);
  readonly selectedTool = computed(
    () => this.tools.find((tool) => tool.key === this.selectedToolKey()) ?? this.tools[0]
  );
  readonly toolStates = signal<Record<DatabaseToolKey, ToolState>>(this.createInitialToolStates());
  readonly state = computed(() => this.toolStates()[this.selectedToolKey()]);
  readonly hasResult = computed(() => this.state().status !== 'idle');

  readonly scopeForm = new FormGroup({
    environment: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    })
  });

  readonly payloadControl = new FormControl(this.toFormattedJson(this.tools[0].examplePayload), {
    nonNullable: true
  });

  submit(event: Event): void {
    event.preventDefault();
    const selectedTool = this.selectedTool();

    if (this.scopeForm.invalid) {
      this.scopeForm.markAllAsTouched();
      this.setCurrentToolState(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Uzupełnij environment dla ręcznego scope DB.'
        })
      );
      return;
    }

    const parsedPayload = this.parsePayload(selectedTool);
    if (parsedPayload.failed) {
      this.setCurrentToolState(
        this.errorStateFromPayload({
          code: 'INVALID_JSON',
          message: parsedPayload.message
        })
      );
      return;
    }

    const scope = this.scopePayload();
    const requestPayload = selectedTool.payloadRequired ? parsedPayload.value : undefined;

    this.runRequest(
      this.databaseApi.runTool(selectedTool.endpoint, scope, requestPayload),
      selectedTool,
      scope,
      requestPayload
    );
  }

  selectTool(toolKey: DatabaseToolKey, resetPayload = false): void {
    const nextTool = this.tools.find((tool) => tool.key === toolKey);
    if (!nextTool) {
      return;
    }

    this.selectedToolKey.set(nextTool.key);
    if (resetPayload) {
      this.resetPayload();
    }
  }

  resetPayload(): void {
    this.payloadControl.setValue(this.toFormattedJson(this.selectedTool().examplePayload));
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

  toolButtonClass(tool: DatabaseToolDefinition): string {
    return tool.key === this.selectedToolKey()
      ? 'database-tool-button database-tool-button--active'
      : 'database-tool-button';
  }

  toolStateStatus(tool: DatabaseToolDefinition): ToolStateStatus {
    return this.toolStates()[tool.key]?.status ?? 'idle';
  }

  durationLabel(durationMs: number | null): string {
    if (durationMs === null) {
      return 'n/a';
    }

    if (durationMs < 1000) {
      return `${durationMs} ms`;
    }

    return `${(durationMs / 1000).toFixed(1)} s`;
  }

  async copyJsonPayload(key: DatabaseJsonPayloadKey, value: string): Promise<void> {
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

  downloadJsonPayload(key: DatabaseJsonPayloadKey, value: string): void {
    if (!value) {
      return;
    }

    const blob = new Blob([value], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = this.databaseJsonPayloadFileName(key);
    link.click();
    URL.revokeObjectURL(url);
  }

  private parsePayload(
    selectedTool: DatabaseToolDefinition
  ): { failed: false; value: unknown } | { failed: true; message: string } {
    if (!selectedTool.payloadRequired) {
      return { failed: false, value: undefined };
    }

    const raw = this.payloadControl.value.trim();
    if (!raw) {
      return { failed: false, value: {} };
    }

    try {
      return { failed: false, value: JSON.parse(raw) as unknown };
    } catch (error) {
      return {
        failed: true,
        message: error instanceof Error ? error.message : 'Payload JSON nie jest poprawny.'
      };
    }
  }

  private scopePayload(): DatabaseToolScopePayload {
    return {
      environment: this.scopeForm.controls.environment.value.trim()
    };
  }

  private runRequest(
    request$: Observable<unknown>,
    selectedTool: DatabaseToolDefinition,
    scope: DatabaseToolScopePayload,
    requestPayload: unknown
  ): void {
    const endpoint = `/api/database/${selectedTool.endpoint}`;
    const requestPreview = selectedTool.payloadRequired
      ? { scope, request: requestPayload }
      : { scope };
    const requestJson = this.toFormattedJson({
      endpoint,
      method: 'POST',
      body: requestPreview
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
        request: requestPreview
      }),
      durationMs: null
    });

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        this.setToolState(selectedTool.key, {
          status: 'success',
          statusCode: 200,
          message: 'Backend zwrócił odpowiedź JSON z DatabaseToolService.',
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

  private createInitialToolStates(): Record<DatabaseToolKey, ToolState> {
    return this.tools.reduce(
      (states, tool) => ({
        ...states,
        [tool.key]: this.idleState('Wybierz scope, payload i uruchom request.')
      }),
      {} as Record<DatabaseToolKey, ToolState>
    );
  }

  private setCurrentToolState(state: ToolState): void {
    this.setToolState(this.selectedToolKey(), state);
  }

  private setToolState(toolKey: DatabaseToolKey, state: ToolState): void {
    this.toolStates.update((states) => ({
      ...states,
      [toolKey]: state
    }));
  }

  private toFormattedJson(value: unknown): string {
    const formatted = JSON.stringify(value, null, 2);
    return formatted === undefined ? 'null' : formatted;
  }

  private databaseJsonPayloadFileName(key: DatabaseJsonPayloadKey): string {
    const safeEndpoint = this.selectedTool().endpoint.replace(/[^a-z0-9]+/gi, '-').toLowerCase();
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    return `database-${safeEndpoint}-${key}-${timestamp}.json`;
  }
}
