import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, computed, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, finalize } from 'rxjs';

import {
  AnalysisAiModelOptionsResponse,
  ApiErrorResponse,
  LocalAnalysisRunDetailResponse
} from '../../../core/models/analysis.models';
import { AiOptionsApiService } from '../../../core/services/ai-options-api.service';
import { AnalysisRunHistoryApiService } from '../../../core/services/analysis-run-history-api.service';
import { AnalysisJobPollingService } from '../../../core/services/analysis-job-polling.service';
import { AppUiConfigService } from '../../../core/services/app-ui-config.service';
import { downloadJsonFile } from '../../../core/utils/json-file.utils';
import {
  EMPTY_ANALYSIS_AI_MODEL_OPTIONS,
  defaultReasoningEffortForAiModel,
  listedDefaultAiModel,
  normalizeAnalysisAiModelOptions,
  reasoningEffortsForAiModel
} from '../../../core/utils/analysis-ai-model-options.utils';
import {
  UiExplorerConfigurationSnapshot,
  UiExplorerInputOptionsResponse,
  UiExplorerJobStartRequest,
  UiExplorerJobStateSnapshot,
  UiExplorerJobStatus,
  UiExplorerLoadingState,
  UiExplorerResultSource,
  UiExplorerScreenCatalogResponse,
  UiExplorerSectionId,
  UiExplorerSectionMode
} from '../models/ui-explorer.models';
import { UiExplorerApiService } from '../services/ui-explorer-api.service';
import {
  buildUiExplorerExportFileName,
  isUiExplorerExportEnvelope,
  parseUiExplorerLocalRunEnvelope
} from '../utils/ui-explorer-import-export.utils';

@Injectable()
export class UiExplorerFacade {
  private readonly api = inject(UiExplorerApiService);
  private readonly historyApi = inject(AnalysisRunHistoryApiService);
  private readonly aiOptionsApi = inject(AiOptionsApiService);
  private readonly pollingService = inject(AnalysisJobPollingService);
  private readonly uiConfig = inject(AppUiConfigService);
  private readonly destroyRef = inject(DestroyRef);
  private pollingSubscription?: Subscription;

  readonly inputState = signal<UiExplorerLoadingState>('idle');
  readonly screenState = signal<UiExplorerLoadingState>('idle');
  readonly aiOptionsState = signal<UiExplorerLoadingState>('idle');
  readonly inputError = signal('');
  readonly screenError = signal('');
  readonly aiOptionsError = signal('');
  readonly inputOptions = signal<UiExplorerInputOptionsResponse | null>(null);
  readonly screenCatalog = signal<UiExplorerScreenCatalogResponse | null>(null);
  readonly aiOptions = signal<AnalysisAiModelOptionsResponse>(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
  readonly job = signal<UiExplorerJobStateSnapshot | null>(null);
  readonly isSubmitting = signal(false);
  readonly pollingActive = signal(false);
  readonly jobError = signal('');
  readonly authStartUrl = signal('');
  readonly resultSource = signal<UiExplorerResultSource | null>(null);
  readonly portabilityBusy = signal(false);
  readonly portabilityError = signal('');

  readonly selectedSystemId = signal('');
  readonly branch = signal('');
  readonly selectedScreenId = signal('');
  readonly sectionModes = signal<Partial<Record<UiExplorerSectionId, UiExplorerSectionMode>>>({});
  readonly scenarioDescription = signal('');
  readonly selectedModel = signal('');
  readonly selectedReasoningEffort = signal('');

  readonly selectedSystem = computed(
    () =>
      this.inputOptions()?.systems.find(
        (system) => system.systemId === this.selectedSystemId()
      ) ?? null
  );
  readonly selectedScreen = computed(
    () =>
      this.screenCatalog()?.screens.find(
        (screen) => screen.screenId === this.selectedScreenId()
      ) ?? null
  );
  readonly sourceRevision = computed(() => this.screenCatalog()?.sourceRevision ?? null);
  readonly reasoningEfforts = computed(() =>
    reasoningEffortsForAiModel(this.aiOptions(), this.selectedModel())
  );
  readonly hasActiveSection = computed(() =>
    Object.values(this.sectionModes()).some((mode) => mode === 'COMPACT' || mode === 'DEEP')
  );
  readonly catalogMatchesSelection = computed(() => {
    const catalog = this.screenCatalog();
    return Boolean(
      catalog &&
        catalog.systemId === this.selectedSystemId() &&
        catalog.sourceRevision.branch === this.branch().trim() &&
        catalog.screens.some((screen) => screen.screenId === this.selectedScreenId())
    );
  });
  readonly configurationReady = computed(
    () =>
      Boolean(
        this.selectedSystemId() &&
          this.branch().trim() &&
          this.selectedScreenId() &&
          this.sourceRevision()?.revision
      ) && this.hasActiveSection() && this.catalogMatchesSelection()
  );
  readonly configuration = computed<UiExplorerConfigurationSnapshot>(() => ({
    systemId: this.selectedSystemId(),
    branch: this.branch().trim(),
    screenId: this.selectedScreenId(),
    sourceRevision: this.sourceRevision()?.revision ?? '',
    sectionModes: { ...this.sectionModes() },
    scenarioDescription: this.scenarioDescription().trim(),
    model: this.selectedModel(),
    reasoningEffort: this.selectedReasoningEffort()
  }));
  readonly isJobTerminal = computed(() => isTerminalJobStatus(this.job()?.status));
  readonly isJobActive = computed(
    () => this.isSubmitting() || Boolean(this.job() && !this.isJobTerminal())
  );
  readonly controlsLocked = computed(() => this.isJobActive());
  readonly executionAvailable = computed(
    () => this.inputOptions()?.executionAvailability.status === 'AVAILABLE'
  );
  readonly canStartJob = computed(
    () => this.configurationReady() && this.executionAvailable() && !this.isJobActive()
  );
  readonly canRetryPolling = computed(
    () => Boolean(this.job() && !this.isJobTerminal() && this.jobError() && !this.pollingActive())
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

  constructor() {
    effect(() => this.applyPlatformDefaultBranch(this.uiConfig.config().defaultBranch));
    this.destroyRef.onDestroy(() => this.stopPolling());
  }

  initialize(): void {
    this.uiConfig.load();
    this.applyPlatformDefaultBranch(this.uiConfig.config().defaultBranch);
    this.loadInputOptions();
    this.loadAiOptions();
  }

  private applyPlatformDefaultBranch(defaultBranch: string): void {
    const normalized = defaultBranch.trim();
    if (!normalized || this.branch().trim()) {
      return;
    }
    this.branch.set(normalized);
    if (this.selectedSystemId() && this.inputOptions()) {
      this.loadScreens();
    }
  }

  loadInputOptions(): void {
    this.inputState.set('loading');
    this.inputError.set('');
    this.api
      .getInputOptions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (options) => {
          this.inputOptions.set(options);
          this.inputState.set(options.systems.length > 0 ? 'ready' : 'empty');
          this.applyPlatformDefaultBranch(this.uiConfig.config().defaultBranch);
          const selectedStillExists = options.systems.some(
            (system) => system.systemId === this.selectedSystemId()
          );
          if (!selectedStillExists) {
            this.selectedSystemId.set(options.systems[0]?.systemId ?? '');
            this.clearScreenSelection();
          }

          if (Object.keys(this.sectionModes()).length === 0) {
            this.applyDefaultSectionModes(options.defaultSectionModes);
          }

          if (this.selectedSystemId() && this.branch().trim()) {
            this.loadScreens();
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
    this.aiOptionsApi
      .getOptions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          const catalog = normalizeAnalysisAiModelOptions(response);
          this.aiOptions.set(catalog);
          this.aiOptionsState.set(catalog.models.length > 0 ? 'ready' : 'empty');
          const model = listedDefaultAiModel(catalog);
          this.selectedModel.set(model);
          this.selectedReasoningEffort.set(defaultReasoningEffortForAiModel(catalog, model));
        },
        error: (error: HttpErrorResponse) => {
          this.aiOptionsState.set('error');
          this.aiOptionsError.set(readApiError(error, 'Nie udało się pobrać ustawień modelu.'));
        }
      });
  }

  selectSystem(systemId: string): void {
    if (this.controlsLocked()) {
      return;
    }
    if (systemId === this.selectedSystemId()) {
      return;
    }
    this.selectedSystemId.set(systemId);
    this.clearScreenSelection();
    if (systemId && this.branch().trim()) {
      this.loadScreens();
    }
  }

  changeBranch(branch: string): void {
    if (this.controlsLocked()) {
      return;
    }
    this.branch.set(branch);
    this.clearScreenSelection();
  }

  loadScreens(): void {
    if (this.controlsLocked()) {
      return;
    }
    const systemId = this.selectedSystemId();
    const branch = this.branch().trim();
    if (!systemId || !branch) {
      this.screenState.set('idle');
      this.screenError.set('Wybierz frontend i podaj branch lub ref.');
      return;
    }

    this.clearScreenSelection();
    this.screenState.set('loading');
    this.screenError.set('');
    this.api
      .getScreens(systemId, branch)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (catalog) => {
          this.screenCatalog.set(catalog);
          this.screenState.set(catalog.screens.length > 0 ? 'ready' : 'empty');
        },
        error: (error: HttpErrorResponse) => {
          this.screenState.set('error');
          this.screenError.set(readApiError(error, 'Nie udało się rozpoznać ekranów dla tego refa.'));
        }
      });
  }

  selectScreen(screenId: string): void {
    if (this.controlsLocked()) {
      return;
    }
    this.selectedScreenId.set(screenId);
  }

  private applyDefaultSectionModes(defaults: UiExplorerInputOptionsResponse['defaultSectionModes']): void {
    const nextModes: Partial<Record<UiExplorerSectionId, UiExplorerSectionMode>> = {};
    for (const assignment of defaults) {
      nextModes[assignment.sectionId] = assignment.mode;
    }
    this.sectionModes.set(nextModes);
  }

  selectSectionMode(sectionId: UiExplorerSectionId, mode: UiExplorerSectionMode): void {
    if (this.controlsLocked()) {
      return;
    }
    this.sectionModes.update((current) => ({ ...current, [sectionId]: mode }));
  }

  selectModel(model: string): void {
    if (this.controlsLocked()) {
      return;
    }
    this.selectedModel.set(model);
    this.selectedReasoningEffort.set(defaultReasoningEffortForAiModel(this.aiOptions(), model));
  }

  selectReasoningEffort(reasoningEffort: string): void {
    if (this.controlsLocked()) {
      return;
    }
    this.selectedReasoningEffort.set(reasoningEffort);
  }

  updateScenarioDescription(description: string): void {
    if (this.controlsLocked()) {
      return;
    }
    this.scenarioDescription.set(description);
  }

  startJob(): void {
    if (!this.canStartJob()) {
      if (!this.isJobActive()) {
        this.jobError.set(
          this.catalogMatchesSelection()
            ? 'Wybierz widok i pozostaw co najmniej jedną aktywną sekcję.'
            : 'Katalog widoków jest nieaktualny. Wczytaj widoki ponownie i wybierz ekran.'
        );
      }
      return;
    }

    const request = this.buildStartRequest();
    if (!request) {
      this.jobError.set('Katalog widoków jest nieaktualny. Wczytaj widoki ponownie.');
      return;
    }

    this.stopPolling();
    this.isSubmitting.set(true);
    this.jobError.set('');
    this.portabilityError.set('');
    this.authStartUrl.set('');
    this.job.set(null);
    this.resultSource.set(null);

    this.api
      .startJob(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.isSubmitting.set(false);
          this.job.set(snapshot);
          this.resultSource.set({
            origin: 'live',
            exportedAt: '',
            fileName: ''
          });
          if (!isTerminalJobStatus(snapshot.status)) {
            this.startPolling(snapshot.jobId);
          }
        },
        error: (error: HttpErrorResponse) => {
          this.isSubmitting.set(false);
          this.applyJobError(error, 'Nie udało się uruchomić UI Explorer job.');
        }
      });
  }

  loadLocalRun(analysisId: string): void {
    const normalizedId = analysisId.trim();
    if (!normalizedId) {
      return;
    }

    this.stopPolling();
    this.job.set(null);
    this.resultSource.set(null);
    this.jobError.set('');
    this.portabilityError.set('');
    this.portabilityBusy.set(true);

    this.historyApi
      .getRun(normalizedId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.portabilityBusy.set(false))
      )
      .subscribe({
        next: (detail) => this.applyLocalRun(detail),
        error: (error: HttpErrorResponse) =>
          this.portabilityError.set(
            readApiError(error, 'Nie udało się odtworzyć lokalnego runu UI Explorer.')
          )
      });
  }

  importAnalysis(document: unknown, fileName: string): void {
    this.stopPolling();
    this.jobError.set('');
    this.portabilityError.set('');
    this.portabilityBusy.set(true);

    this.api
      .importAnalysis(document)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.portabilityBusy.set(false))
      )
      .subscribe({
        next: (snapshot) => {
          if (!isReadableResult(snapshot)) {
            this.portabilityError.set(
              'Import UI Explorer nie zwrócił zakończonego raportu w aktualnym kontrakcie.'
            );
            return;
          }
          this.job.set(snapshot);
          this.resultSource.set({
            origin: 'imported',
            exportedAt: '',
            fileName
          });
        },
        error: (error: HttpErrorResponse) =>
          this.portabilityError.set(
            readApiError(error, 'Nie udało się zaimportować wyniku UI Explorer.')
          )
      });
  }

  exportCurrentResult(): void {
    const snapshot = this.job();
    if (!snapshot?.exportAvailable || this.portabilityBusy()) {
      return;
    }

    this.portabilityError.set('');
    this.portabilityBusy.set(true);
    this.api
      .exportJob(snapshot.jobId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.portabilityBusy.set(false))
      )
      .subscribe({
        next: (envelope) => {
          if (!isUiExplorerExportEnvelope(envelope)) {
            this.portabilityError.set(
              'Backend zwrócił eksport UI Explorer w nieobsługiwanym formacie.'
            );
            return;
          }
          downloadJsonFile(
            buildUiExplorerExportFileName(snapshot, envelope.exportedAt),
            envelope
          );
        },
        error: (error: HttpErrorResponse) =>
          this.portabilityError.set(
            readApiError(error, 'Nie udało się wyeksportować wyniku UI Explorer.')
          )
      });
  }

  setPortabilityError(message: string): void {
    this.portabilityError.set(message);
  }

  clearResult(): void {
    this.stopPolling();
    this.job.set(null);
    this.resultSource.set(null);
    this.jobError.set('');
    this.portabilityError.set('');
    this.authStartUrl.set('');
  }

  retryPolling(): void {
    const jobId = this.job()?.jobId;
    if (!jobId || this.isJobTerminal() || this.pollingActive()) {
      return;
    }
    this.startPolling(jobId);
  }

  private buildStartRequest(): UiExplorerJobStartRequest | null {
    const configuration = this.configuration();
    if (!this.catalogMatchesSelection()) {
      return null;
    }
    return {
      systemId: configuration.systemId,
      branch: configuration.branch,
      screenId: configuration.screenId,
      sourceRevision: configuration.sourceRevision,
      sectionModes: { ...configuration.sectionModes },
      ...(configuration.scenarioDescription
        ? { scenarioDescription: configuration.scenarioDescription }
        : {}),
      ...(configuration.model ? { model: configuration.model } : {}),
      ...(configuration.reasoningEffort
        ? { reasoningEffort: configuration.reasoningEffort }
        : {})
    };
  }

  private startPolling(jobId: string): void {
    this.stopPolling();
    this.jobError.set('');
    this.authStartUrl.set('');
    this.pollingActive.set(true);
    this.pollingSubscription = this.pollingService
      .poll({
        load: () => this.api.getJob(jobId),
        isTerminal: (snapshot) => isTerminalJobStatus(snapshot.status),
        intervalMs: 1500
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => this.job.set(snapshot),
        error: (error: HttpErrorResponse) => {
          this.stopPolling();
          this.applyJobError(error, 'Nie udało się odświeżyć UI Explorer job.');
        },
        complete: () => this.pollingActive.set(false)
      });
  }

  private applyLocalRun(detail: LocalAnalysisRunDetailResponse): void {
    try {
      if (detail.feature !== 'ui-explorer') {
        throw new Error(`Lokalny run ${detail.analysisId} nie jest runem UI Explorer.`);
      }
      if (detail.continuationEnabled) {
        throw new Error('Lokalny run UI Explorer nie może udostępniać continuation.');
      }

      const restored = parseUiExplorerLocalRunEnvelope(detail.exportEnvelope);
      this.job.set(restored.job);
      this.resultSource.set({
        origin: 'history',
        exportedAt: restored.storedAt,
        fileName: '',
        localRunId: detail.analysisId,
        localRunName: detail.name
      });
    } catch (error) {
      this.portabilityError.set(
        error instanceof Error
          ? error.message
          : 'Nie udało się odtworzyć lokalnego runu UI Explorer.'
      );
    }
  }

  private stopPolling(): void {
    this.pollingSubscription?.unsubscribe();
    this.pollingSubscription = undefined;
    this.pollingActive.set(false);
  }

  private applyJobError(error: HttpErrorResponse, fallback: string): void {
    const response = error.error as Partial<ApiErrorResponse> | null;
    this.jobError.set(readApiError(error, fallback));
    this.authStartUrl.set(
      typeof response?.authStartUrl === 'string' ? response.authStartUrl.trim() : ''
    );
  }

  private clearScreenSelection(): void {
    this.screenCatalog.set(null);
    this.selectedScreenId.set('');
    this.screenState.set('idle');
    this.screenError.set('');
  }
}

function isTerminalJobStatus(status: UiExplorerJobStatus | undefined): boolean {
  return status === 'COMPLETED' || status === 'PARTIAL' || status === 'BLOCKED' || status === 'FAILED';
}

function readApiError(error: HttpErrorResponse, fallback: string): string {
  const response = error.error as Partial<ApiErrorResponse> | null;
  return typeof response?.message === 'string' && response.message.trim()
    ? response.message.trim()
    : fallback;
}

function isReadableResult(snapshot: UiExplorerJobStateSnapshot): boolean {
  return (
    (snapshot.status === 'COMPLETED' || snapshot.status === 'PARTIAL') &&
    Boolean(snapshot.result) &&
    Boolean(snapshot.report) &&
    snapshot.exportAvailable
  );
}
