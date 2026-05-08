import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal, WritableSignal } from '@angular/core';
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
  DatabaseApiService,
  DatabaseToolScopePayload
} from '../../core/services/database-api.service';

type ToolStateStatus = 'idle' | 'loading' | 'success' | 'error';

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
  responseJson: string;
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
      applicationPattern: 'agreement-process',
      tableNamePattern: 'AGREEMENT',
      entityOrKeywordHint: 'AgreementEntity',
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
      applicationPattern: 'agreement-process',
      tableNamePattern: 'AGREEMENT',
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
        schema: 'AGREEMENT_PROCESS_1',
        tableName: 'AGREEMENT'
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
        schema: 'AGREEMENT_PROCESS_1',
        tableName: 'AGREEMENT'
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
        schema: 'AGREEMENT_PROCESS_1',
        tableName: 'AGREEMENT'
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
        schema: 'AGREEMENT_PROCESS_1',
        tableName: 'AGREEMENT'
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
        schema: 'AGREEMENT_PROCESS_1',
        tableName: 'AGREEMENT'
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
        schema: 'AGREEMENT_PROCESS_1',
        tableName: 'AGREEMENT_EVENT'
      },
      childColumn: 'AGREEMENT_ID',
      parentTable: {
        schema: 'AGREEMENT_PROCESS_1',
        tableName: 'AGREEMENT'
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
          schema: 'AGREEMENT_PROCESS_1',
          tableName: 'AGREEMENT'
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
          schema: 'AGREEMENT_PROCESS_1',
          tableName: 'AGREEMENT'
        },
        {
          schema: 'AGREEMENT_PROCESS_1',
          tableName: 'AGREEMENT_EVENT'
        }
      ],
      joins: [
        {
          left: {
            table: {
              schema: 'AGREEMENT_PROCESS_1',
              tableName: 'AGREEMENT_EVENT'
            },
            column: 'AGREEMENT_ID'
          },
          right: {
            table: {
              schema: 'AGREEMENT_PROCESS_1',
              tableName: 'AGREEMENT'
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
          schema: 'AGREEMENT_PROCESS_1',
          tableName: 'AGREEMENT'
        },
        {
          schema: 'AGREEMENT_PROCESS_1',
          tableName: 'AGREEMENT_EVENT'
        }
      ],
      joins: [
        {
          left: {
            table: {
              schema: 'AGREEMENT_PROCESS_1',
              tableName: 'AGREEMENT_EVENT'
            },
            column: 'AGREEMENT_ID'
          },
          right: {
            table: {
              schema: 'AGREEMENT_PROCESS_1',
              tableName: 'AGREEMENT'
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
              schema: 'AGREEMENT_PROCESS_1',
              tableName: 'AGREEMENT'
            },
            column: 'ID'
          },
          alias: 'AGREEMENT_ID'
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
        schema: 'AGREEMENT_PROCESS_1',
        tableName: 'AGREEMENT'
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

@Component({
  selector: 'app-database-console',
  imports: [ReactiveFormsModule, RouterLink, RouterLinkActive],
  templateUrl: './database-console.html',
  styleUrl: './database-console.scss'
})
export class DatabaseConsoleComponent {
  private readonly databaseApi = inject(DatabaseApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly tools = DATABASE_TOOLS;
  readonly selectedToolKey = signal<DatabaseToolKey>('scope');
  readonly selectedTool = computed(
    () => this.tools.find((tool) => tool.key === this.selectedToolKey()) ?? this.tools[0]
  );

  readonly scopeForm = new FormGroup({
    environment: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    }),
    correlationId: new FormControl('', { nonNullable: true }),
    analysisRunId: new FormControl('', { nonNullable: true })
  });

  readonly toolControl = new FormControl<DatabaseToolKey>('scope', { nonNullable: true });
  readonly payloadControl = new FormControl(this.toFormattedJson(this.tools[0].examplePayload), {
    nonNullable: true
  });

  readonly state = signal<ToolState>(
    this.idleState('Wybierz environment, tool i uruchom request do DatabaseToolService.')
  );

  constructor() {
    this.toolControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((toolKey) => this.selectTool(toolKey, true));
  }

  submit(event: Event): void {
    event.preventDefault();

    if (this.scopeForm.invalid) {
      this.scopeForm.markAllAsTouched();
      this.state.set(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Uzupełnij environment dla ręcznego scope DB.'
        })
      );
      return;
    }

    const selectedTool = this.selectedTool();
    const parsedPayload = this.parsePayload(selectedTool);
    if (parsedPayload.failed) {
      this.state.set(
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
    if (this.toolControl.value !== nextTool.key) {
      this.toolControl.setValue(nextTool.key, { emitEvent: false });
    }
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
      environment: this.scopeForm.controls.environment.value.trim(),
      correlationId: this.optionalValue(this.scopeForm.controls.correlationId.value),
      analysisRunId: this.optionalValue(this.scopeForm.controls.analysisRunId.value)
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

    this.state.set({
      status: 'loading',
      statusCode: null,
      message: `Wysyłamy request do ${endpoint}...`,
      responseJson: this.toFormattedJson({
        status: 'WAITING',
        endpoint,
        request: requestPreview
      })
    });

    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        this.state.set({
          status: 'success',
          statusCode: 200,
          message: 'Backend zwrócił odpowiedź JSON z DatabaseToolService.',
          responseJson: this.toFormattedJson(response)
        });
      },
      error: (error) => this.state.set(this.toErrorState(error))
    });
  }

  private toErrorState(error: unknown): ToolState {
    if (error instanceof HttpErrorResponse) {
      const payload = this.normalizeErrorPayload(error.error, error.status, error.message);
      return {
        status: 'error',
        statusCode: error.status || null,
        message: payload.message,
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
      responseJson: this.toFormattedJson(payload)
    };
  }

  private idleState(message: string): ToolState {
    return {
      status: 'idle',
      statusCode: null,
      message,
      responseJson: this.toFormattedJson({
        message
      })
    };
  }

  private optionalValue(raw: string): string | undefined {
    const value = raw.trim();
    return value.length > 0 ? value : undefined;
  }

  private toFormattedJson(value: unknown): string {
    const formatted = JSON.stringify(value, null, 2);
    return formatted === undefined ? 'null' : formatted;
  }
}
