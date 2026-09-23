import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subscription, finalize } from 'rxjs';

import {
  AnalysisAiModelOptionsResponse,
  ApiErrorResponse,
  LocalAnalysisRunDetailResponse
} from '../../../core/models/analysis.models';
import { AiOptionsApiService } from '../../../core/services/ai-options-api.service';
import { AnalysisJobPollingService } from '../../../core/services/analysis-job-polling.service';
import { AnalysisRunHistoryApiService } from '../../../core/services/analysis-run-history-api.service';
import { appendOptimisticChatTurn } from '../../../core/utils/analysis-chat-optimistic.utils';
import {
  EMPTY_ANALYSIS_AI_MODEL_OPTIONS,
  defaultReasoningEffortForAiModel,
  listedDefaultAiModel,
  normalizeAnalysisAiModelOptions,
  reasoningEffortsForAiModel
} from '../../../core/utils/analysis-ai-model-options.utils';
import {
  UxInspectorCapture,
  UxInspectorExportEnvelope,
  UxInspectorInputOptionsResponse,
  UxInspectorJobStartRequest,
  UxInspectorJobStateSnapshot,
  UxInspectorJobStatus,
  UxInspectorLoadingState,
  UxInspectorResultSource,
  UxInspectorViewCatalogResponse
} from '../models/ux-inspector.models';
import { UxInspectorApiService } from '../services/ux-inspector-api.service';
import { UxInspectorCaptureIngressService } from '../services/ux-inspector-capture-ingress.service';
import { matchCapturedRouteToView } from '../utils/ux-inspector-view-route-match.utils';

@Injectable()
export class UxInspectorFacade {
  private readonly api = inject(UxInspectorApiService);
  private readonly historyApi = inject(AnalysisRunHistoryApiService);
  private readonly aiOptionsApi = inject(AiOptionsApiService);
  private readonly pollingService = inject(AnalysisJobPollingService);
  private readonly ingress = inject(UxInspectorCaptureIngressService);
  private readonly destroyRef = inject(DestroyRef);
  private pollingSubscription?: Subscription;
  private viewRequestId = 0;

  readonly inputState = signal<UxInspectorLoadingState>('idle');
  readonly viewState = signal<UxInspectorLoadingState>('idle');
  readonly aiOptionsState = signal<UxInspectorLoadingState>('idle');
  readonly inputError = signal('');
  readonly viewError = signal('');
  readonly aiOptionsError = signal('');
  readonly inputOptions = signal<UxInspectorInputOptionsResponse | null>(null);
  readonly viewCatalog = signal<UxInspectorViewCatalogResponse | null>(null);
  readonly aiOptions = signal<AnalysisAiModelOptionsResponse>(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
  readonly job = signal<UxInspectorJobStateSnapshot | null>(null);
  readonly isSubmitting = signal(false);
  readonly pollingActive = signal(false);
  readonly jobError = signal('');
  readonly authStartUrl = signal('');
  readonly resultSource = signal<UxInspectorResultSource | null>(null);
  readonly portabilityBusy = signal(false);
  readonly portabilityError = signal('');
  readonly chatSubmitting = signal(false);
  readonly chatError = signal('');
  readonly chatAuthStartUrl = signal('');

  readonly selectedSystemId = signal('');
  readonly branch = signal('');
  readonly selectedViewId = signal('');
  readonly viewMatchedFromCapture = signal(false);
  readonly question = signal('');
  readonly selectedModel = signal('');
  readonly selectedReasoningEffort = signal('');

  readonly capture = computed<UxInspectorCapture | null>(
    () => this.job()?.request.capture ?? this.ingress.capture()
  );
  readonly captureStatus = this.ingress.status;
  readonly captureError = this.ingress.error;
  readonly selectedSystem = computed(
    () => this.inputOptions()?.systems.find((item) => item.systemId === this.selectedSystemId()) ?? null
  );
  readonly selectedView = computed(
    () => this.viewCatalog()?.views.find((item) => item.viewId === this.selectedViewId()) ?? null
  );
  readonly sourceRevision = computed(() => this.viewCatalog()?.sourceRevision ?? null);
  readonly reasoningEfforts = computed(() =>
    reasoningEffortsForAiModel(this.aiOptions(), this.selectedModel())
  );
  readonly catalogMatchesSelection = computed(() => {
    const catalog = this.viewCatalog();
    return Boolean(
      catalog &&
        catalog.systemId === this.selectedSystemId() &&
        catalog.sourceRevision.branch === this.branch().trim() &&
        catalog.views.some((view) => view.viewId === this.selectedViewId())
    );
  });
  readonly aiSelectionValid = computed(() => {
    const model = this.aiOptions().models.find((item) => item.id === this.selectedModel());
    if (!model) return false;
    const efforts = this.reasoningEfforts();
    return efforts.length === 0
      ? !this.selectedReasoningEffort()
      : efforts.includes(this.selectedReasoningEffort());
  });
  readonly configurationReady = computed(
    () => Boolean(
      this.ingress.capture() &&
        this.selectedSystemId() &&
        this.branch().trim() &&
        this.selectedViewId() &&
        this.sourceRevision()?.revision &&
        this.question().trim() &&
        this.aiSelectionValid() &&
        this.catalogMatchesSelection()
    )
  );
  readonly isJobTerminal = computed(() => isTerminal(this.job()?.status));
  readonly isJobActive = computed(
    () => this.isSubmitting() || Boolean(this.job() && !this.isJobTerminal())
  );
  readonly controlsLocked = computed(() => this.isJobActive() || this.isReadOnlyResult());
  readonly canStartJob = computed(() => this.configurationReady() && !this.controlsLocked());
  readonly canRetryPolling = computed(
    () => Boolean(this.job() && (!this.isJobTerminal() || hasActiveChat(this.job()!)) && this.jobError() && !this.pollingActive())
  );
  readonly workflowIsRunning = computed(
    () => this.pollingActive() && this.job()?.status !== 'ANALYZING'
  );
  readonly aiWorkflowIsRunning = computed(
    () => this.pollingActive() && this.job()?.status === 'ANALYZING'
  );
  readonly isReadOnlyResult = computed(() => {
    const origin = this.resultSource()?.origin;
    return origin === 'history' || origin === 'imported';
  });
  readonly chatMessages = computed(() => this.job()?.chatMessages ?? []);
  readonly canUseChat = computed(() => {
    const snapshot = this.job();
    const source = this.resultSource();
    if (!snapshot?.report || !['COMPLETED', 'PARTIAL'].includes(snapshot.status) || source?.origin === 'imported') return false;
    return source?.origin === 'history'
      ? source.continuationEnabled === true
      : snapshot.chatAvailability?.available === true;
  });

  constructor() {
    effect(() => this.applyCapturedRouteSuggestion());
    this.destroyRef.onDestroy(() => this.stopPolling());
  }

  initialize(): void {
    this.ingress.start();
    this.loadInputOptions();
    this.loadAiOptions();
  }

  loadInputOptions(): void {
    this.inputState.set('loading');
    this.inputError.set('');
    this.api.getInputOptions().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (options) => {
        this.inputOptions.set(options);
        this.inputState.set(options.systems.length ? 'ready' : 'empty');
        if (!options.systems.some((item) => item.systemId === this.selectedSystemId())) {
          const first = options.systems[0];
          this.selectedSystemId.set(first?.systemId ?? '');
          this.branch.set(first?.defaultBranch ?? '');
          this.clearViewSelection();
        }
      },
      error: (error: HttpErrorResponse) => {
        this.inputState.set('error');
        this.inputError.set(readApiError(error, 'Nie udało się pobrać listy frontendów.'));
      }
    });
  }

  loadAiOptions(): void {
    this.aiOptionsState.set('loading');
    this.aiOptionsError.set('');
    this.aiOptionsApi.getOptions().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        const options = normalizeAnalysisAiModelOptions(response);
        this.aiOptions.set(options);
        this.aiOptionsState.set(options.models.length ? 'ready' : 'empty');
        const model = listedDefaultAiModel(options) || options.models[0]?.id || '';
        this.selectedModel.set(model);
        this.selectedReasoningEffort.set(defaultReasoningEffortForAiModel(options, model));
      },
      error: (error: HttpErrorResponse) => {
        this.aiOptionsState.set('error');
        this.aiOptionsError.set(readApiError(error, 'Nie udało się pobrać ustawień modelu.'));
      }
    });
  }

  selectSystem(systemId: string): void {
    if (this.controlsLocked() || systemId === this.selectedSystemId()) return;
    this.selectedSystemId.set(systemId);
    this.branch.set(this.selectedSystem()?.defaultBranch ?? '');
    this.clearViewSelection();
  }

  changeBranch(branch: string): void {
    if (this.controlsLocked()) return;
    const normalized = branch.trim();
    if (normalized !== this.branch()) {
      this.branch.set(normalized);
      this.clearViewSelection();
    }
    if (normalized && this.selectedSystemId() && !this.viewCatalog()) {
      this.loadViews();
    }
  }

  loadViews(refreshCache = false): void {
    if (this.controlsLocked()) return;
    const systemId = this.selectedSystemId();
    const branch = this.branch().trim();
    if (!systemId || !branch) {
      this.viewError.set('Wybierz frontend oraz branch lub ref.');
      return;
    }
    this.clearViewSelection();
    const requestId = ++this.viewRequestId;
    this.viewState.set('loading');
    this.api.getViews(systemId, branch, refreshCache).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (catalog) => {
        if (requestId !== this.viewRequestId || systemId !== this.selectedSystemId() || branch !== this.branch()) return;
        this.viewCatalog.set(catalog);
        this.viewState.set(catalog.views.length ? 'ready' : 'empty');
      },
      error: (error: HttpErrorResponse) => {
        if (requestId !== this.viewRequestId) return;
        this.viewState.set('error');
        this.viewError.set(readApiError(error, 'Nie udało się pobrać widoków dla wybranego refa.'));
      }
    });
  }

  selectView(viewId: string): void {
    if (this.controlsLocked()) return;
    this.selectedViewId.set(viewId);
    this.viewMatchedFromCapture.set(false);
  }

  updateQuestion(value: string): void {
    if (!this.controlsLocked()) this.question.set(value.slice(0, 4000));
  }

  selectModel(model: string): void {
    if (this.controlsLocked()) return;
    this.selectedModel.set(model);
    this.selectedReasoningEffort.set(defaultReasoningEffortForAiModel(this.aiOptions(), model));
  }

  selectReasoningEffort(effort: string): void {
    if (!this.controlsLocked()) this.selectedReasoningEffort.set(effort);
  }

  startJob(): void {
    if (!this.canStartJob()) {
      if (!this.ingress.capture()) this.jobError.set('Najpierw wskaż element przez TDW Browser Tools.');
      else this.jobError.set('Uzupełnij pytanie i potwierdź frontend, branch, view, model oraz reasoning effort.');
      return;
    }
    const request = this.buildRequest();
    if (!request) return;
    this.stopPolling();
    this.isSubmitting.set(true);
    this.jobError.set('');
    this.portabilityError.set('');
    this.api.startJob(request).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (snapshot) => {
        this.isSubmitting.set(false);
        this.job.set(snapshot);
        this.resultSource.set({ origin: 'live', fileName: '' });
        this.ingress.consumeCapture();
        if (!isTerminal(snapshot.status)) this.startPolling(snapshot.jobId);
      },
      error: (error: HttpErrorResponse) => {
        this.isSubmitting.set(false);
        this.applyJobError(error, 'Nie udało się uruchomić UX Inspector job.');
      }
    });
  }

  loadLocalRun(analysisId: string): void {
    const id = analysisId.trim();
    if (!id) return;
    this.stopPolling();
    this.portabilityBusy.set(true);
    this.portabilityError.set('');
    this.historyApi.getRun(id).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.portabilityBusy.set(false))
    ).subscribe({
      next: (detail) => this.applyLocalRun(detail),
      error: (error: HttpErrorResponse) => this.portabilityError.set(
        readApiError(error, 'Nie udało się odtworzyć runu UX Inspectora.')
      )
    });
  }

  importAnalysis(document: unknown, fileName: string): void {
    this.stopPolling();
    this.portabilityBusy.set(true);
    this.portabilityError.set('');
    this.api.importAnalysis(document).pipe(
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.portabilityBusy.set(false))
    ).subscribe({
      next: (snapshot) => {
        if (!isReadable(snapshot)) {
          this.portabilityError.set('Import nie zawiera ukończonego raportu UX Inspectora v1.');
          return;
        }
        this.job.set(snapshot);
        this.resultSource.set({ origin: 'imported', fileName });
      },
      error: (error: HttpErrorResponse) => this.portabilityError.set(
        readApiError(error, 'Nie udało się zaimportować wyniku UX Inspectora.')
      )
    });
  }

  sendChatMessage(message: string): void {
    const snapshot = this.job();
    const source = this.resultSource();
    const normalized = message.trim();
    if (!snapshot || !normalized || !this.canUseChat() || this.chatSubmitting()) return;
    const previous = snapshot;
    this.chatError.set('');
    this.chatAuthStartUrl.set('');
    this.chatSubmitting.set(true);
    this.job.set(appendOptimisticChatTurn({ ...snapshot, chatMessages: snapshot.chatMessages ?? [] }, normalized));
    const request$: Observable<UxInspectorJobStateSnapshot | LocalAnalysisRunDetailResponse> =
      source?.origin === 'history' && source.localRunId
        ? this.historyApi.sendChatMessage(source.localRunId, { message: normalized })
        : this.api.sendChatMessage(snapshot.jobId, normalized);
    request$.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.chatSubmitting.set(false))).subscribe({
      next: (response) => {
        if ('exportEnvelope' in response) { this.applyLocalRun(response); return; }
        this.job.set(response);
        if (hasActiveChat(response)) this.startPolling(response.jobId);
      },
      error: (error: HttpErrorResponse) => {
        this.job.set(previous);
        const payload = error.error as Partial<ApiErrorResponse> | null;
        this.chatError.set(readApiError(error, 'Nie udało się wysłać pytania do UX Inspectora.'));
        this.chatAuthStartUrl.set(typeof payload?.authStartUrl === 'string' ? payload.authStartUrl.trim() : '');
      }
    });
  }

  clearChatError(): void { this.chatError.set(''); this.chatAuthStartUrl.set(''); }

  setPortabilityError(value: string): void { this.portabilityError.set(value); }
  retryPolling(): void {
    const jobId = this.job()?.jobId;
    if (jobId && (!this.isJobTerminal() || (this.job() && hasActiveChat(this.job()!))) && !this.pollingActive()) this.startPolling(jobId);
  }

  private buildRequest(): UxInspectorJobStartRequest | null {
    const capture = this.ingress.capture();
    const revision = this.sourceRevision()?.revision;
    if (!capture || !revision || !this.catalogMatchesSelection()) return null;
    return {
      systemId: this.selectedSystemId(), branch: this.branch(), viewId: this.selectedViewId(),
      sourceRevision: revision, question: this.question().trim(), capture,
      model: this.selectedModel(), reasoningEffort: this.selectedReasoningEffort()
    };
  }

  private startPolling(jobId: string): void {
    this.stopPolling();
    this.jobError.set('');
    this.pollingActive.set(true);
    this.pollingSubscription = this.pollingService.poll({
      load: () => this.api.getJob(jobId), isTerminal: (value) => isTerminal(value.status) && !hasActiveChat(value), intervalMs: 1500
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (snapshot) => this.job.set(snapshot),
      error: (error: HttpErrorResponse) => {
        this.stopPolling();
        this.applyJobError(error, 'Nie udało się odświeżyć UX Inspector job.');
      },
      complete: () => this.pollingActive.set(false)
    });
  }

  private applyLocalRun(detail: LocalAnalysisRunDetailResponse): void {
    try {
      if (detail.feature !== 'ux-inspector') {
        throw new Error('Wybrany wpis historii nie jest runem UX Inspectora.');
      }
      const envelope = detail.exportEnvelope;
      if (!isUxInspectorExport(envelope)) throw new Error('Run ma nieobsługiwany kontrakt UX Inspectora.');
      const snapshot = envelope.payload.job;
      this.job.set(snapshot);
      this.resultSource.set({ origin: 'history', fileName: '', localRunId: detail.analysisId, localRunName: detail.name,
        continuationEnabled: detail.continuationEnabled });
      if (!isTerminal(snapshot.status)) this.startPolling(snapshot.jobId);
    } catch (error) {
      this.portabilityError.set(error instanceof Error ? error.message : 'Nie udało się odtworzyć runu UX Inspectora.');
    }
  }

  private stopPolling(): void {
    this.pollingSubscription?.unsubscribe();
    this.pollingSubscription = undefined;
    this.pollingActive.set(false);
  }

  private applyJobError(error: HttpErrorResponse, fallback: string): void {
    const payload = error.error as Partial<ApiErrorResponse> | null;
    this.jobError.set(readApiError(error, fallback));
    this.authStartUrl.set(typeof payload?.authStartUrl === 'string' ? payload.authStartUrl.trim() : '');
  }

  private clearViewSelection(): void {
    this.viewRequestId++;
    this.viewCatalog.set(null);
    this.selectedViewId.set('');
    this.viewMatchedFromCapture.set(false);
    this.viewState.set('idle');
    this.viewError.set('');
  }

  private applyCapturedRouteSuggestion(): void {
    const capture = this.ingress.capture();
    const catalog = this.viewCatalog();
    if (!capture || !catalog || this.controlsLocked() || this.selectedViewId()) return;
    if (catalog.systemId !== this.selectedSystemId() || catalog.sourceRevision.branch !== this.branch().trim()) return;

    const match = matchCapturedRouteToView(
      capture.page.path,
      catalog.views,
      capture.target.domFingerprint.componentBoundaryTags
    );
    if (!match) return;
    this.selectedViewId.set(match.viewId);
    this.viewMatchedFromCapture.set(true);
  }
}

function isTerminal(status: UxInspectorJobStatus | undefined): boolean {
  return status === 'COMPLETED' || status === 'PARTIAL' || status === 'BLOCKED' || status === 'FAILED';
}

function isReadable(snapshot: UxInspectorJobStateSnapshot): boolean {
  return (snapshot.status === 'COMPLETED' || snapshot.status === 'PARTIAL') &&
    Boolean(snapshot.report && snapshot.result && snapshot.exportAvailable);
}

function hasActiveChat(snapshot: UxInspectorJobStateSnapshot): boolean {
  return (snapshot.chatMessages ?? []).some((message) => message.role === 'ASSISTANT' && message.status === 'IN_PROGRESS');
}

function isUxInspectorExport(value: unknown): value is UxInspectorExportEnvelope {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false;
  const envelope = value as Partial<UxInspectorExportEnvelope>;
  const contractSupported = (envelope.version === 1 && envelope.payload?.resultContract === 'ux-inspector-result-v1') ||
    (envelope.version === 2 && envelope.payload?.resultContract === 'ux-inspector-result-v2');
  return envelope.schema === 'tdw.ux-inspector-export' && contractSupported &&
    envelope.payload?.type === 'ux-inspector-analysis' &&
    Boolean(envelope.payload.job);
}

function readApiError(error: HttpErrorResponse, fallback: string): string {
  const payload = error.error as Partial<ApiErrorResponse> | null;
  return typeof payload?.message === 'string' && payload.message.trim() ? payload.message.trim() : fallback;
}
