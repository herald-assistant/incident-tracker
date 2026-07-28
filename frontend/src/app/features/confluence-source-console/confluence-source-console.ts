import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiErrorResponse } from '../../core/models/analysis.models';
import {
  ConfluencePageContentPayload,
  ConfluencePageContentResponse,
  ConfluenceSourceApiService
} from '../../core/services/confluence-source-api.service';
import { copyTextToClipboard } from '../../core/utils/clipboard.utils';

type ConfluenceSourceStatus = 'idle' | 'loading' | 'success' | 'error';
type ConfluenceJsonPayloadKey = 'request' | 'response';

interface ToolState {
  status: ConfluenceSourceStatus;
  statusCode: number | null;
  message: string;
  endpoint: string;
  requestJson: string;
  responseJson: string;
  response: ConfluencePageContentResponse | null;
  durationMs: number | null;
}

const CONFLUENCE_SOURCE_ENDPOINT = '/api/confluence/page/content';

@Component({
  selector: 'app-confluence-source-console',
  imports: [ReactiveFormsModule],
  templateUrl: './confluence-source-console.html',
  styleUrl: '../source-console-layout.scss'
})
export class ConfluenceSourceConsoleComponent {
  private readonly confluenceSourceApi = inject(ConfluenceSourceApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly copiedJsonPayloadKey = signal<ConfluenceJsonPayloadKey | null>(null);
  readonly toolState = signal<ToolState>(
    this.idleState(
      'Podaj link do strony Confluence, aby sprawdzić readonly pobieranie jej treści.'
    )
  );
  readonly state = computed(() => this.toolState());
  readonly hasResult = computed(() => this.state().status !== 'idle');
  readonly result = computed(() => this.state().response);

  readonly scopeForm = new FormGroup({
    pageUrl: new FormControl('', {
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
          message: 'Uzupełnij link do strony Confluence.'
        })
      );
      return;
    }

    const payload: ConfluencePageContentPayload = {
      pageUrl: this.scopeForm.controls.pageUrl.value.trim()
    };
    const requestJson = this.toFormattedJson({
      endpoint: CONFLUENCE_SOURCE_ENDPOINT,
      method: 'POST',
      body: payload
    });
    const startedAt = Date.now();

    this.toolState.set({
      status: 'loading',
      statusCode: null,
      message: `Wysyłamy request do ${CONFLUENCE_SOURCE_ENDPOINT}...`,
      endpoint: CONFLUENCE_SOURCE_ENDPOINT,
      requestJson,
      responseJson: this.toFormattedJson({
        status: 'WAITING',
        endpoint: CONFLUENCE_SOURCE_ENDPOINT,
        request: payload
      }),
      response: null,
      durationMs: null
    });

    this.confluenceSourceApi
      .getPageContent(payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) =>
          this.toolState.set({
            status: 'success',
            statusCode: 200,
            message:
              response.limitations.length > 0
                ? `Backend zwrócił stronę z ${response.limitations.length} ograniczeniem/ograniczeniami.`
                : 'Backend zwrócił treść strony z adaptera Confluence.',
            endpoint: CONFLUENCE_SOURCE_ENDPOINT,
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
    this.scopeForm.reset({ pageUrl: '' });
  }

  controlInvalid(control: AbstractControl<unknown, unknown>): boolean {
    return control.invalid && (control.dirty || control.touched);
  }

  statusLabel(status: ConfluenceSourceStatus): string {
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

  statusPillClass(status: ConfluenceSourceStatus): string {
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

  contentLength(content: string | null | undefined): number {
    return content?.length ?? 0;
  }

  async copyJsonPayload(key: ConfluenceJsonPayloadKey, value: string): Promise<void> {
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

  downloadJsonPayload(key: ConfluenceJsonPayloadKey, value: string): void {
    if (!value) {
      return;
    }

    const blob = new Blob([value], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = this.confluenceJsonPayloadFileName(key);
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
        endpoint: CONFLUENCE_SOURCE_ENDPOINT,
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
      endpoint: CONFLUENCE_SOURCE_ENDPOINT,
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

  private confluenceJsonPayloadFileName(key: ConfluenceJsonPayloadKey): string {
    const pageUrl = this.scopeForm.controls.pageUrl.value || 'page';
    const pageId = pageUrl.match(/(?:pageId=|\/pages\/)(\d+)/i)?.[1] ?? 'page';
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
    return `confluence-${pageId}-${key}-${timestamp}.json`;
  }
}
