import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiErrorResponse } from '../../core/models/analysis.models';
import {
  JiraIssueMaterialPayload,
  JiraIssueMaterialResponse,
  JiraSourceApiService
} from '../../core/services/jira-source-api.service';
import { copyTextToClipboard } from '../../core/utils/clipboard.utils';

type JiraSourceStatus = 'idle' | 'loading' | 'success' | 'error';
type JiraJsonPayloadKey = 'request' | 'response';

interface ToolState {
  status: JiraSourceStatus;
  statusCode: number | null;
  message: string;
  endpoint: string;
  requestJson: string;
  responseJson: string;
  response: JiraIssueMaterialResponse | null;
  durationMs: number | null;
}

const JIRA_SOURCE_ENDPOINT = '/api/jira/issue/material';

@Component({
  selector: 'app-jira-source-console',
  imports: [ReactiveFormsModule],
  templateUrl: './jira-source-console.html',
  styleUrl: './jira-source-console.scss'
})
export class JiraSourceConsoleComponent {
  private readonly jiraSourceApi = inject(JiraSourceApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly copiedJsonPayloadKey = signal<JiraJsonPayloadKey | null>(null);
  readonly toolState = signal<ToolState>(
    this.idleState('Podaj issue key lub link do Jira, aby sprawdzić readonly pobieranie materialu issue.')
  );
  readonly state = computed(() => this.toolState());
  readonly hasResult = computed(() => this.state().status !== 'idle');
  readonly result = computed(() => this.state().response);

  readonly scopeForm = new FormGroup({
    issueRef: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required]
    })
  });

  submit(event: Event): void {
    event.preventDefault();

    if (this.scopeForm.invalid) {
      this.scopeForm.markAllAsTouched();
      this.toolState.set(
        this.errorStateFromPayload({
          code: 'VALIDATION_ERROR',
          message: 'Uzupełnij Jira issue key albo link do issue.'
        })
      );
      return;
    }

    const payload: JiraIssueMaterialPayload = {
      issueRef: this.scopeForm.controls.issueRef.value.trim()
    };
    const requestJson = this.toFormattedJson({
      endpoint: JIRA_SOURCE_ENDPOINT,
      method: 'POST',
      body: payload
    });
    const startedAt = Date.now();

    this.toolState.set({
      status: 'loading',
      statusCode: null,
      message: `Wysyłamy request do ${JIRA_SOURCE_ENDPOINT}...`,
      endpoint: JIRA_SOURCE_ENDPOINT,
      requestJson,
      responseJson: this.toFormattedJson({
        status: 'WAITING',
        endpoint: JIRA_SOURCE_ENDPOINT,
        request: payload
      }),
      response: null,
      durationMs: null
    });

    this.jiraSourceApi
      .getIssueMaterial(payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) =>
          this.toolState.set({
            status: 'success',
            statusCode: 200,
            message: 'Backend zwrócił materiał issue z Jira adaptera.',
            endpoint: JIRA_SOURCE_ENDPOINT,
            requestJson,
            responseJson: this.toFormattedJson(response),
            response,
            durationMs: Date.now() - startedAt
          }),
        error: (error) =>
          this.toolState.set(this.toErrorState(error, requestJson, Date.now() - startedAt))
      });
  }

  resetScope(): void {
    this.scopeForm.reset({ issueRef: '' });
  }

  controlInvalid(control: AbstractControl<unknown, unknown>): boolean {
    return control.invalid && (control.dirty || control.touched);
  }

  statusLabel(status: JiraSourceStatus): string {
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

  statusPillClass(status: JiraSourceStatus): string {
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

  durationLabel(durationMs: number | null): string {
    if (durationMs === null) {
      return 'n/a';
    }
    return durationMs < 1000 ? `${durationMs} ms` : `${(durationMs / 1000).toFixed(1)} s`;
  }

  async copyJsonPayload(key: JiraJsonPayloadKey, value: string): Promise<void> {
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

  downloadJsonPayload(key: JiraJsonPayloadKey, value: string): void {
    if (!value) {
      return;
    }

    const blob = new Blob([value], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = this.jiraJsonPayloadFileName(key);
    link.click();
    URL.revokeObjectURL(url);
  }

  private toErrorState(error: unknown, requestJson = '', durationMs: number | null = null): ToolState {
    if (error instanceof HttpErrorResponse) {
      const payload = this.normalizeErrorPayload(error.error, error.status, error.message);
      return {
        status: 'error',
        statusCode: error.status || null,
        message: payload.message,
        endpoint: JIRA_SOURCE_ENDPOINT,
        requestJson,
        responseJson: this.toFormattedJson(payload.body),
        response: null,
        durationMs
      };
    }

    return this.errorStateFromPayload(
      {
        code: 'REQUEST_FAILED',
        message: error instanceof Error ? error.message : 'Request zakończył się błędem.'
      },
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
      const normalized = this.toApiErrorResponse(payload as Record<string, unknown>);
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
      return {
        message: `Request zakończył się błędem HTTP ${status}.`,
        body: {
          status,
          message: payload
        }
      };
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
    requestJson = '',
    durationMs: number | null = null
  ): ToolState {
    return {
      status: 'error',
      statusCode: null,
      message: payload.message,
      endpoint: JIRA_SOURCE_ENDPOINT,
      requestJson,
      responseJson: this.toFormattedJson(payload),
      response: null,
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
      response: null,
      durationMs: null
    };
  }

  private toFormattedJson(value: unknown): string {
    const formatted = JSON.stringify(value, null, 2);
    return formatted === undefined ? 'null' : formatted;
  }

  private jiraJsonPayloadFileName(key: JiraJsonPayloadKey): string {
    const issueRef = this.scopeForm.controls.issueRef.value || 'issue';
    const safeIssueRef = issueRef.replace(/[^a-z0-9]+/gi, '-').toLowerCase() || 'issue';
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    return `jira-${safeIssueRef}-${key}-${timestamp}.json`;
  }
}
