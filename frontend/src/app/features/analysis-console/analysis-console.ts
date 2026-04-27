import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink, RouterLinkActive } from '@angular/router';

import {
  ApiErrorResponse,
  AnalysisJobResponse,
  ExportState,
  TransportErrorState
} from '../../core/models/analysis.models';
import { AnalysisApiService } from '../../core/services/analysis-api.service';
import {
  buildAnalysisActionsHint,
  defaultErrorMessage,
  isTerminalStatus
} from '../../core/utils/analysis-display.utils';
import {
  buildExportEnvelope,
  buildExportFileName,
  normalizeAnalysisJob,
  parseImportedAnalysis
} from '../../core/utils/analysis-import-export.utils';
import { AnalysisOverviewCardComponent } from '../../components/analysis-overview-card/analysis-overview-card';
import { AnalysisStepsPanelComponent } from '../../components/analysis-steps-panel/analysis-steps-panel';

const POLL_INTERVAL_MS = 1500;
const AI_MODEL_OPTIONS = [
  { value: '', label: 'Domyślny backend' },
  { value: 'gpt-5.4', label: 'GPT-5.4' },
  { value: 'gpt-5.4-mini', label: 'GPT-5.4 Mini' },
  { value: 'gpt-5.3-codex', label: 'GPT-5.3 Codex' },
  { value: 'gpt-5.3-codex-spark', label: 'GPT-5.3 Codex Spark' },
  { value: 'gpt-5.2', label: 'GPT-5.2' }
] as const;
const REASONING_EFFORT_OPTIONS = [
  { value: '', label: 'Domyślny backend' },
  { value: 'low', label: 'Low' },
  { value: 'medium', label: 'Medium' },
  { value: 'high', label: 'High' },
  { value: 'xhigh', label: 'XHigh' }
] as const;

@Component({
  selector: 'app-analysis-console',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    RouterLinkActive,
    AnalysisOverviewCardComponent,
    AnalysisStepsPanelComponent
  ],
  templateUrl: './analysis-console.html',
  styleUrl: './analysis-console.scss'
})
export class AnalysisConsoleComponent {
  private readonly analysisApi = inject(AnalysisApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly responsePanel = viewChild<ElementRef<HTMLElement>>('responsePanel');

  readonly correlationIdControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required]
  });
  readonly aiModelControl = new FormControl('', { nonNullable: true });
  readonly reasoningEffortControl = new FormControl('', { nonNullable: true });
  readonly aiModelOptions = AI_MODEL_OPTIONS;
  readonly reasoningEffortOptions = REASONING_EFFORT_OPTIONS;

  readonly isLoading = signal(false);
  readonly formError = signal('');
  readonly placeholderMode = signal<'idle' | 'loading'>('idle');
  readonly loadingCorrelationId = signal('');
  readonly transportError = signal<TransportErrorState | null>(null);
  readonly job = signal<AnalysisJobResponse | null>(null);
  readonly exportState = signal<ExportState | null>(null);

  readonly hasActiveState = computed(
    () =>
      this.placeholderMode() === 'loading' || this.transportError() !== null || this.job() !== null
  );
  readonly analysisActionsHint = computed(() => buildAnalysisActionsHint(this.exportState()));
  readonly placeholderKicker = computed(() =>
    this.placeholderMode() === 'loading' ? 'Trwa' : 'Gotowe'
  );
  readonly placeholderTitle = computed(() =>
    this.placeholderMode() === 'loading'
      ? 'Uruchamiamy analizę...'
      : 'Tutaj pojawi się przebieg analizy.'
  );
  readonly placeholderDescription = computed(() => {
    if (this.placeholderMode() === 'loading') {
      return `Analiza dla correlationId ${this.loadingCorrelationId()} została zainicjowana. Pierwsze dane pojawią się za chwilę.`;
    }

    return 'Po uruchomieniu zobaczysz postęp kroków, zebrane dane źródłowe i końcową diagnozę bez odświeżania widoku.';
  });

  private activeAnalysisId: string | null = null;
  private pollHandle: number | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.stopPolling());
  }

  submit(event: Event): void {
    event.preventDefault();

    const correlationId = this.correlationIdControl.value.trim();
    if (!correlationId) {
      this.showFormError('Podaj correlationId, aby uruchomić analizę.');
      return;
    }

    this.correlationIdControl.setValue(correlationId);
    this.clearFormError();
    this.stopPolling();
    this.activeAnalysisId = null;
    this.job.set(null);
    this.transportError.set(null);
    this.exportState.set(null);
    this.isLoading.set(true);
    this.placeholderMode.set('loading');
    this.loadingCorrelationId.set(correlationId);
    this.scrollResponseIntoView();

    const aiModel = this.aiModelControl.value.trim();
    const reasoningEffort = this.reasoningEffortControl.value.trim();

    this.analysisApi
      .startAnalysis({
        correlationId,
        model: aiModel || undefined,
        reasoningEffort: reasoningEffort || undefined
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoading.set(false))
      )
      .subscribe({
        next: (job) => {
          this.activeAnalysisId = job.analysisId;
          this.applyJob(job, {
            origin: 'live',
            exportedAt: '',
            fileName: ''
          });

          if (!isTerminalStatus(job.status)) {
            this.schedulePoll(job.analysisId);
          }
        },
        error: (error) =>
          this.renderTransportError(
            error,
            'Nie udało się połączyć z backendem. Sprawdź, czy aplikacja działa lokalnie.'
          )
      });
  }

  clearFormError(): void {
    this.formError.set('');
  }

  triggerImport(fileInput: HTMLInputElement): void {
    this.clearFormError();
    fileInput.value = '';
    fileInput.click();
  }

  async importAnalysis(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file) {
      return;
    }

    try {
      const content = await file.text();
      let parsedContent: unknown;

      try {
        parsedContent = JSON.parse(content);
      } catch {
        throw new Error('Wybrany plik nie zawiera poprawnego JSON-a.');
      }

      const imported = parseImportedAnalysis(parsedContent);

      this.stopPolling();
      this.activeAnalysisId = null;
      this.isLoading.set(false);
      this.clearFormError();
      this.placeholderMode.set('idle');
      this.transportError.set(null);
      this.job.set(imported.job);
      this.syncExportableState(imported.job, {
        origin: 'imported',
        exportedAt: imported.exportedAt,
        fileName: file.name
      });

      if (imported.job.correlationId) {
        this.correlationIdControl.setValue(imported.job.correlationId);
      }
      this.aiModelControl.setValue(imported.job.aiModel || '');
      this.reasoningEffortControl.setValue(imported.job.reasoningEffort || '');

      this.scrollResponseIntoView();
    } catch (error) {
      this.showFormError(
        error instanceof Error
          ? error.message
          : 'Nie udało się wczytać pliku z zapisem analizy.'
      );
    } finally {
      if (input) {
        input.value = '';
      }
    }
  }

  exportAnalysis(): void {
    const exportState = this.exportState();
    if (!exportState) {
      return;
    }

    const exportedAt = new Date().toISOString();
    const payload = buildExportEnvelope(exportState.job, exportedAt);
    const blob = new Blob([JSON.stringify(payload, null, 2)], {
      type: 'application/json'
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = buildExportFileName(exportState.job, exportedAt);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  private pollAnalysis(analysisId: string): void {
    this.analysisApi
      .getAnalysis(analysisId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (job) => {
          if (analysisId !== this.activeAnalysisId) {
            return;
          }

          this.applyJob(job, {
            origin: 'live',
            exportedAt: '',
            fileName: ''
          });

          if (!isTerminalStatus(job.status)) {
            this.schedulePoll(analysisId);
          }
        },
        error: (error) => {
          if (analysisId !== this.activeAnalysisId) {
            return;
          }

          this.renderTransportError(
            error,
            'Nie udało się odczytać statusu analizy z backendu.'
          );
        }
      });
  }

  private schedulePoll(analysisId: string): void {
    this.stopPolling();
    this.pollHandle = window.setTimeout(() => this.pollAnalysis(analysisId), POLL_INTERVAL_MS);
  }

  private stopPolling(): void {
    if (this.pollHandle !== null) {
      window.clearTimeout(this.pollHandle);
      this.pollHandle = null;
    }
  }

  private applyJob(job: AnalysisJobResponse, metadata: Pick<ExportState, 'origin' | 'exportedAt' | 'fileName'>): void {
    const normalizedJob = normalizeAnalysisJob(job);
    this.placeholderMode.set('idle');
    this.transportError.set(null);
    this.job.set(normalizedJob);
    this.syncExportableState(normalizedJob, metadata);
  }

  private syncExportableState(
    job: AnalysisJobResponse,
    metadata: Pick<ExportState, 'origin' | 'exportedAt' | 'fileName'>
  ): void {
    if (!isTerminalStatus(job.status)) {
      this.exportState.set(null);
      return;
    }

    this.exportState.set({
      origin: metadata.origin,
      exportedAt: metadata.exportedAt,
      fileName: metadata.fileName,
      job: normalizeAnalysisJob(job)
    });
  }

  private renderTransportError(error: unknown, networkFallbackMessage: string): void {
    this.stopPolling();
    this.activeAnalysisId = null;
    this.exportState.set(null);
    this.job.set(null);
    this.placeholderMode.set('idle');
    this.transportError.set(this.toTransportError(error, networkFallbackMessage));
    this.scrollResponseIntoView();
  }

  private toTransportError(
    error: unknown,
    networkFallbackMessage: string
  ): TransportErrorState {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    const payload = this.normalizeApiErrorPayload(
      error instanceof HttpErrorResponse ? error.error : null
    );

    return {
      code: payload?.code || 'REQUEST_FAILED',
      message:
        payload?.message ||
        (status > 0 ? defaultErrorMessage(status) : networkFallbackMessage),
      details:
        payload?.fieldErrors.length
          ? payload.fieldErrors.map((fieldError) => `${fieldError.field}: ${fieldError.message}`)
          : [status > 0 ? `HTTP status: ${status}` : 'Brak odpowiedzi HTTP od backendu.'],
      status
    };
  }

  private normalizeApiErrorPayload(payload: unknown): ApiErrorResponse | null {
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
      return null;
    }

    const payloadRecord = payload as Record<string, unknown>;
    return {
      code: typeof payloadRecord['code'] === 'string' ? payloadRecord['code'] : '',
      message: typeof payloadRecord['message'] === 'string' ? payloadRecord['message'] : '',
      fieldErrors: Array.isArray(payloadRecord['fieldErrors'])
        ? payloadRecord['fieldErrors']
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

  private showFormError(message: string): void {
    this.formError.set(message);
  }

  private scrollResponseIntoView(): void {
    this.responsePanel()?.nativeElement.scrollIntoView({
      behavior: 'smooth',
      block: 'start'
    });
  }
}
