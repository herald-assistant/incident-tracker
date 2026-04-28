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
  AnalysisAiModelOptionsResponse,
  ApiErrorResponse,
  AnalysisChatMessageResponse,
  AnalysisEvidenceSection,
  AnalysisJobResponse,
  ExportState,
  TransportErrorState
} from '../../core/models/analysis.models';
import { AnalysisApiService } from '../../core/services/analysis-api.service';
import {
  buildAnalysisActionsHint,
  defaultErrorMessage,
  formatDateTime,
  formatEvidenceSectionTitle,
  formatStatus,
  hasInProgressChat,
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
import { MarkdownContentComponent } from '../../components/markdown-content/markdown-content';
import { AttributeNamePipe } from '../../core/pipes/attribute-name.pipe';

const POLL_INTERVAL_MS = 1500;
const EMPTY_AI_MODEL_OPTIONS: AnalysisAiModelOptionsResponse = {
  defaultModel: '',
  defaultReasoningEffort: '',
  defaultReasoningEfforts: [],
  models: []
};

@Component({
  selector: 'app-analysis-console',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    RouterLinkActive,
    AnalysisOverviewCardComponent,
    AnalysisStepsPanelComponent,
    MarkdownContentComponent,
    AttributeNamePipe
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
  readonly chatMessageControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required]
  });

  readonly isLoading = signal(false);
  readonly isChatSubmitting = signal(false);
  readonly formError = signal('');
  readonly chatError = signal('');
  readonly placeholderMode = signal<'idle' | 'loading'>('idle');
  readonly loadingCorrelationId = signal('');
  readonly transportError = signal<TransportErrorState | null>(null);
  readonly job = signal<AnalysisJobResponse | null>(null);
  readonly exportState = signal<ExportState | null>(null);
  readonly aiModelCatalog = signal<AnalysisAiModelOptionsResponse>(EMPTY_AI_MODEL_OPTIONS);
  readonly selectedAiModel = signal('');

  readonly aiModelOptions = computed(() => [
    { value: '', label: this.defaultModelLabel() },
    ...this.aiModelCatalog().models.map((model) => ({
      value: model.id,
      label: this.modelLabel(model.id, model.name)
    }))
  ]);
  readonly availableReasoningEfforts = computed(() =>
    this.reasoningEffortsForModel(this.selectedAiModel())
  );
  readonly reasoningEffortOptions = computed(() => [
    { value: '', label: this.defaultReasoningEffortLabel() },
    ...this.availableReasoningEfforts().map((effort) => ({
      value: effort,
      label: this.reasoningEffortLabel(effort)
    }))
  ]);

  readonly hasActiveState = computed(
    () =>
      this.placeholderMode() === 'loading' || this.transportError() !== null || this.job() !== null
  );
  readonly analysisActionsHint = computed(() => buildAnalysisActionsHint(this.exportState()));
  readonly canUseChat = computed(() => {
    const currentJob = this.job();
    return (
      currentJob?.status === 'COMPLETED' &&
      this.exportState()?.origin === 'live' &&
      !hasInProgressChat(currentJob)
    );
  });
  readonly chatHint = computed(() => {
    const currentJob = this.job();
    if (!currentJob) {
      return 'Chat będzie dostępny po zakończeniu analizy.';
    }
    if (this.exportState()?.origin === 'imported') {
      return 'Importowany zapis jest tylko do odczytu. Chat działa dla analiz żywych w backendzie.';
    }
    if (currentJob.status !== 'COMPLETED') {
      return 'Chat będzie dostępny po zakończeniu analizy.';
    }
    if (hasInProgressChat(currentJob)) {
      return 'AI przygotowuje odpowiedź na poprzednie polecenie.';
    }
    return 'Możesz dopytać o wynik, poprosić o weryfikację w repo lub DB albo wygenerować raport.';
  });
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
    this.loadAiModelOptions();
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
    this.chatError.set('');
    this.isLoading.set(true);
    this.placeholderMode.set('loading');
    this.loadingCorrelationId.set(correlationId);
    this.scrollResponseIntoView();

    const aiModel = this.aiModelControl.value.trim();
    const reasoningEffort = this.reasoningEffortControl.enabled
      ? this.reasoningEffortControl.value.trim()
      : '';

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

  clearChatError(): void {
    this.chatError.set('');
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
      this.chatError.set('');
      this.syncExportableState(imported.job, {
        origin: 'imported',
        exportedAt: imported.exportedAt,
        fileName: file.name
      });

      if (imported.job.correlationId) {
        this.correlationIdControl.setValue(imported.job.correlationId);
      }
      this.aiModelControl.setValue(imported.job.aiModel || '');
      this.selectedAiModel.set(imported.job.aiModel || '');
      this.reasoningEffortControl.setValue(imported.job.reasoningEffort || '');
      this.syncReasoningEffortSelection();

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

  onAiModelChanged(): void {
    this.selectedAiModel.set(this.aiModelControl.value.trim());
    this.syncReasoningEffortSelection();
    this.clearFormError();
  }

  submitChat(event: Event): void {
    event.preventDefault();

    const currentJob = this.job();
    const analysisId = this.activeAnalysisId || currentJob?.analysisId || '';
    const message = this.chatMessageControl.value.trim();

    if (!message) {
      this.chatError.set('Wpisz pytanie albo polecenie do AI.');
      return;
    }

    if (!currentJob || currentJob.status !== 'COMPLETED' || !analysisId) {
      this.chatError.set('Chat jest dostępny dopiero dla zakończonej analizy uruchomionej w backendzie.');
      return;
    }

    if (hasInProgressChat(currentJob)) {
      this.chatError.set('Poczekaj na zakończenie poprzedniej odpowiedzi AI.');
      return;
    }

    this.chatError.set('');
    this.isChatSubmitting.set(true);
    this.chatMessageControl.disable({ emitEvent: false });

    this.analysisApi
      .sendChatMessage(analysisId, { message })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isChatSubmitting.set(false);
          this.chatMessageControl.enable({ emitEvent: false });
        })
      )
      .subscribe({
        next: (job) => {
          this.chatMessageControl.setValue('', { emitEvent: false });
          this.activeAnalysisId = job.analysisId;
          this.applyJob(job, {
            origin: 'live',
            exportedAt: '',
            fileName: ''
          });

          if (this.shouldKeepPolling(job)) {
            this.schedulePoll(job.analysisId);
          }
        },
        error: (error) => {
          const transportError = this.toTransportError(
            error,
            'Nie udało się wysłać wiadomości do backendu.'
          );
          this.chatError.set(transportError.message);
        }
      });
  }

  protected chatMessageTitle(message: AnalysisChatMessageResponse): string {
    if (message.role === 'USER') {
      return 'Operator';
    }
    if (message.status === 'IN_PROGRESS') {
      return 'AI odpowiada';
    }
    if (message.status === 'FAILED') {
      return 'AI zakończyło odpowiedź błędem';
    }
    return 'AI';
  }

  protected chatMessageMeta(message: AnalysisChatMessageResponse): string {
    const status = message.role === 'ASSISTANT' ? formatStatus(message.status) : '';
    const timestamp = formatDateTime(message.completedAt || message.updatedAt || message.createdAt);
    return [status, timestamp].filter(Boolean).join(' · ');
  }

  protected evidenceSectionTitle(section: AnalysisEvidenceSection): string {
    return formatEvidenceSectionTitle(section);
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

          if (this.shouldKeepPolling(job)) {
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

  private loadAiModelOptions(): void {
    this.analysisApi
      .getAiModelOptions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (options) => {
          this.aiModelCatalog.set(this.normalizeAiModelOptions(options));
          this.syncReasoningEffortSelection();
        },
        error: () => {
          this.aiModelCatalog.set(EMPTY_AI_MODEL_OPTIONS);
          this.syncReasoningEffortSelection();
        }
      });
  }

  private normalizeAiModelOptions(
    options: AnalysisAiModelOptionsResponse | null
  ): AnalysisAiModelOptionsResponse {
    if (!options) {
      return EMPTY_AI_MODEL_OPTIONS;
    }

    return {
      defaultModel: typeof options.defaultModel === 'string' ? options.defaultModel : '',
      defaultReasoningEffort:
        typeof options.defaultReasoningEffort === 'string' ? options.defaultReasoningEffort : '',
      defaultReasoningEfforts: Array.isArray(options.defaultReasoningEfforts)
        ? options.defaultReasoningEfforts.filter((effort) => typeof effort === 'string')
        : [],
      models: Array.isArray(options.models)
        ? options.models
            .filter((model) => model && typeof model.id === 'string')
            .map((model) => ({
              id: model.id,
              name: typeof model.name === 'string' ? model.name : model.id,
              supportsReasoningEffort: Boolean(model.supportsReasoningEffort),
              reasoningEfforts: Array.isArray(model.reasoningEfforts)
                ? model.reasoningEfforts.filter((effort) => typeof effort === 'string')
                : [],
              defaultReasoningEffort:
                typeof model.defaultReasoningEffort === 'string'
                  ? model.defaultReasoningEffort
                  : ''
            }))
        : []
    };
  }

  private syncReasoningEffortSelection(): void {
    const availableEfforts = this.reasoningEffortsForModel(this.selectedAiModel());
    const currentEffort = this.reasoningEffortControl.value.trim();

    if (!availableEfforts.length) {
      this.reasoningEffortControl.setValue('', { emitEvent: false });
      this.reasoningEffortControl.disable({ emitEvent: false });
      return;
    }

    this.reasoningEffortControl.enable({ emitEvent: false });
    if (currentEffort && !availableEfforts.includes(currentEffort)) {
      this.reasoningEffortControl.setValue('', { emitEvent: false });
    }
  }

  private reasoningEffortsForModel(modelId: string): string[] {
    const catalog = this.aiModelCatalog();
    if (!modelId) {
      return catalog.defaultReasoningEfforts;
    }

    const model = catalog.models.find((candidate) => candidate.id === modelId);
    return model?.supportsReasoningEffort ? model.reasoningEfforts : [];
  }

  private defaultModelLabel(): string {
    const defaultModel = this.aiModelCatalog().defaultModel;
    return defaultModel ? `Domyślny backend (${defaultModel})` : 'Domyślny backend';
  }

  private defaultReasoningEffortLabel(): string {
    const defaultEffort = this.aiModelCatalog().defaultReasoningEffort;
    return defaultEffort ? `Domyślny backend (${defaultEffort})` : 'Domyślny backend';
  }

  private modelLabel(id: string, name: string): string {
    if (!name || name === id) {
      return id;
    }

    return `${name} (${id})`;
  }

  private reasoningEffortLabel(effort: string): string {
    return effort ? effort.charAt(0).toUpperCase() + effort.slice(1) : effort;
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
    if (!isTerminalStatus(job.status) || hasInProgressChat(job)) {
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

  private shouldKeepPolling(job: AnalysisJobResponse): boolean {
    return !isTerminalStatus(job.status) || hasInProgressChat(job);
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
