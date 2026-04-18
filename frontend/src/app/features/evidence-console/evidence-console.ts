import { HttpErrorResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { Observable } from 'rxjs';
import { Component, DestroyRef, WritableSignal, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { ApiErrorResponse } from '../../core/models/analysis.models';
import {
  ElasticLogSearchPayload,
  EvidenceApiService,
  GitLabRepositorySearchPayload,
  GitLabSourceResolvePayload
} from '../../core/services/evidence-api.service';

type ToolStateStatus = 'idle' | 'loading' | 'success' | 'error';

interface ToolState {
  status: ToolStateStatus;
  statusCode: number | null;
  message: string;
  responseJson: string;
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

  readonly elasticForm = new FormGroup({
    correlationId: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    })
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
  readonly gitLabRepositoryState = signal<ToolState>(
    this.idleState('Wpisz hinty projektu, aby przetestować mapowanie component -> repo.')
  );
  readonly gitLabSourceState = signal<ToolState>(
    this.idleState('Uzupełnij dane repozytorium i symbol, aby przetestować source resolve.')
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

  private toFormattedJson(value: unknown): string {
    const formatted = JSON.stringify(value, null, 2);
    return formatted === undefined ? 'null' : formatted;
  }
}
