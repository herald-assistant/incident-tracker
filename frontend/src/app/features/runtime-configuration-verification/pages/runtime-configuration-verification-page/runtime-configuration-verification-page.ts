import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { finalize, Subscription } from 'rxjs';

import { AnalysisFeatureAsideComponent } from '../../../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisReportPanelComponent } from '../../../../components/analysis-report-panel/analysis-report-panel';
import { AnalysisStepsPanelComponent } from '../../../../components/analysis-steps-panel/analysis-steps-panel';
import {
  AnalysisAiModelOptionsResponse,
  ApiErrorResponse,
  GitHubAuthStatus,
  LocalAnalysisRunDetailResponse
} from '../../../../core/models/analysis.models';
import { AiOptionsApiService } from '../../../../core/services/ai-options-api.service';
import { AnalysisJobPollingService } from '../../../../core/services/analysis-job-polling.service';
import { AnalysisRunHistoryApiService } from '../../../../core/services/analysis-run-history-api.service';
import { GithubAuthService } from '../../../../core/services/github-auth.service';
import {
  defaultReasoningEffortForAiModel,
  EMPTY_ANALYSIS_AI_MODEL_OPTIONS,
  listedDefaultAiModel,
  normalizeAnalysisAiModelOptions,
  reasoningEffortsForAiModel
} from '../../../../core/utils/analysis-ai-model-options.utils';
import { formatStatus, statusClassName } from '../../../../core/utils/analysis-display.utils';
import { downloadJsonFile, readJsonFile } from '../../../../core/utils/json-file.utils';
import {
  RuntimeConfigurationDeepPreflight,
  RuntimeConfigurationDifference,
  RuntimeConfigurationFinding,
  RuntimeConfigurationVerificationInputOptions,
  RuntimeConfigurationVerificationJobStartRequest,
  RuntimeConfigurationVerificationJobStateSnapshot,
  RuntimeConfigurationVerificationMode
} from '../../models/runtime-configuration-verification.models';
import { RuntimeConfigurationVerificationApiService } from '../../services/runtime-configuration-verification-api.service';
import {
  buildRuntimeConfigurationExportEnvelope,
  buildRuntimeConfigurationExportFileName
} from '../../utils/runtime-configuration-import-export.utils';

type ResultOrigin = 'live' | 'local' | 'imported';
type SelectOption = { value: string; label: string };

const EMPTY_INPUT_OPTIONS: RuntimeConfigurationVerificationInputOptions = {
  modes: ['BASIC', 'DEEP'],
  branches: [],
  repositories: [],
  systems: []
};

@Component({
  selector: 'app-runtime-configuration-verification-page',
  imports: [
    ReactiveFormsModule,
    AnalysisFeatureAsideComponent,
    AnalysisReportPanelComponent,
    AnalysisStepsPanelComponent
  ],
  templateUrl: './runtime-configuration-verification-page.html',
  styleUrl: './runtime-configuration-verification-page.scss'
})
export class RuntimeConfigurationVerificationPageComponent implements OnDestroy {
  private readonly api = inject(RuntimeConfigurationVerificationApiService);
  private readonly aiOptionsApi = inject(AiOptionsApiService);
  private readonly pollingService = inject(AnalysisJobPollingService);
  private readonly historyApi = inject(AnalysisRunHistoryApiService);
  private readonly githubAuth = inject(GithubAuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private pollingSubscription?: Subscription;
  private preflightRequestId = 0;

  readonly modeControl = new FormControl<RuntimeConfigurationVerificationMode>('BASIC', {
    nonNullable: true
  });
  readonly repositoryControl = new FormControl('', { nonNullable: true });
  readonly systemControl = new FormControl('', { nonNullable: true });
  readonly sourceBranchControl = new FormControl('', { nonNullable: true });
  readonly targetBranchControl = new FormControl('', { nonNullable: true });
  readonly codeRefControl = new FormControl('', { nonNullable: true });
  readonly aiModelControl = new FormControl('', { nonNullable: true });
  readonly reasoningEffortControl = new FormControl('', { nonNullable: true });

  readonly inputOptions = signal(EMPTY_INPUT_OPTIONS);
  readonly inputOptionsLoading = signal(true);
  readonly inputOptionsError = signal('');
  readonly aiModelCatalog = signal<AnalysisAiModelOptionsResponse>(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
  readonly aiOptionsLoading = signal(true);
  readonly aiOptionsError = signal('');
  readonly githubAuthStatus = signal<GitHubAuthStatus | null>(null);
  readonly githubAuthError = signal('');
  readonly deepPreflight = signal<RuntimeConfigurationDeepPreflight | null>(null);
  readonly preflightLoading = signal(false);
  readonly preflightError = signal('');
  readonly job = signal<RuntimeConfigurationVerificationJobStateSnapshot | null>(null);
  readonly jobError = signal('');
  readonly submitting = signal(false);
  readonly resultOrigin = signal<ResultOrigin>('live');
  readonly resultOriginLabel = signal('');
  readonly differenceKindFilter = signal('ALL');
  readonly differenceFileFilter = signal('ALL');
  readonly findingSeverityFilter = signal('ALL');
  readonly focusedReferenceId = signal('');
  private readonly formRevision = signal(0);

  readonly repositoryOptions = computed<SelectOption[]>(() =>
    this.inputOptions().repositories.map((option) => ({
      value: option.id,
      label: option.label
    }))
  );
  readonly systemOptions = computed<SelectOption[]>(() =>
    this.inputOptions().systems.map((option) => ({
      value: option.id,
      label: `${option.label} · ${option.configurationDirectory}`
    }))
  );
  readonly aiModelOptions = computed<SelectOption[]>(() =>
    this.aiModelCatalog().models.map((model) => ({
      value: model.id,
      label: model.name && model.name !== model.id ? `${model.name} (${model.id})` : model.id
    }))
  );
  readonly reasoningEffortOptions = computed(() =>
    reasoningEffortsForAiModel(this.aiModelCatalog(), this.aiModelControl.value)
  );
  readonly selectedSystem = computed(() => {
    this.formRevision();
    return this.inputOptions().systems.find(
      (system) => system.id === this.systemControl.value
    ) ?? null;
  });
  readonly branchPairInvalid = computed(() => {
    this.formRevision();
    return Boolean(
      this.sourceBranchControl.value
      && this.sourceBranchControl.value === this.targetBranchControl.value
    );
  });
  readonly deepBlocked = computed(() => {
    this.formRevision();
    return this.modeControl.value === 'DEEP'
      && !this.preflightLoading()
      && this.deepPreflight()?.status !== 'READY';
  });
  readonly githubAuthBlocked = computed(() => {
    const status = this.githubAuthStatus();
    return status?.mode === 'GITHUB_APP' && (!status.connected || status.reauthRequired);
  });
  readonly githubAuthActionLabel = computed(() =>
    this.githubAuthStatus()?.reauthRequired ? 'Połącz ponownie GitHub' : 'Połącz GitHub'
  );
  readonly canStart = computed(() => {
    this.formRevision();
    return Boolean(
      this.repositoryControl.value
      && this.systemControl.value
      && this.sourceBranchControl.value
      && this.targetBranchControl.value
      && !this.branchPairInvalid()
      && !this.deepBlocked()
      && !this.preflightLoading()
      && !this.githubAuthBlocked()
      && !this.submitting()
      && !this.workflowRunning()
    );
  });
  readonly workflowRunning = computed(() => {
    const job = this.job();
    return Boolean(job && !this.isTerminal(job.status));
  });
  readonly aiWorkflowRunning = computed(() =>
    Boolean(this.job()?.steps.some((step) =>
      step.phase === 'AI' && ['RUNNING', 'IN_PROGRESS'].includes(step.status)
    ))
  );
  readonly aiWorkflowItemCount = computed(() => {
    const job = this.job();
    if (!job) {
      return 0;
    }
    return job.aiActivityEvents.length
      + job.toolEvidenceSections.reduce((count, section) => count + section.items.length, 0);
  });
  readonly deterministic = computed(() => this.job()?.result?.deterministicResult ?? null);
  readonly filteredDifferences = computed(() => {
    const differences = this.deterministic()?.differences ?? [];
    return differences.filter((difference) =>
      (this.differenceKindFilter() === 'ALL' || difference.kind === this.differenceKindFilter())
      && (this.differenceFileFilter() === 'ALL' || difference.role === this.differenceFileFilter())
    );
  });
  readonly filteredFindings = computed(() => {
    const findings = this.deterministic()?.findings ?? [];
    return findings.filter((finding) =>
      this.findingSeverityFilter() === 'ALL'
      || finding.severity === this.findingSeverityFilter()
    );
  });
  readonly differenceKinds = computed(() =>
    unique((this.deterministic()?.differences ?? []).map((difference) => difference.kind))
  );
  readonly differenceFiles = computed(() =>
    unique((this.deterministic()?.differences ?? []).map((difference) => difference.role))
  );
  readonly findingSeverities = computed(() =>
    unique((this.deterministic()?.findings ?? []).map((finding) => finding.severity))
  );
  readonly resultAvailable = computed(() => Boolean(this.job()?.result));
  readonly canExport = computed(() => Boolean(this.job()?.result && this.isTerminal(this.job()?.status)));

  constructor() {
    this.modeControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.bumpFormRevision();
        this.refreshDeepPreflight();
      });
    this.repositoryControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.bumpFormRevision();
        this.refreshDeepPreflight();
      });
    this.systemControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.bumpFormRevision();
        this.refreshDeepPreflight();
      });
    this.codeRefControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.bumpFormRevision();
        this.refreshDeepPreflight();
      });
    this.sourceBranchControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.bumpFormRevision());
    this.targetBranchControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.bumpFormRevision());
    this.aiModelControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.bumpFormRevision();
        this.syncReasoningEffort();
      });
    this.reasoningEffortControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.bumpFormRevision());

    this.loadInputOptions();
    this.loadAiOptions();
    this.loadGithubAuthStatus();
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const localRunId = params.get('localRunId')?.trim();
        if (localRunId) {
          this.loadLocalRun(localRunId);
        }
      });
  }

  protected startJob(): void {
    if (!this.canStart()) {
      return;
    }
    this.stopPolling();
    this.jobError.set('');
    this.submitting.set(true);
    this.resultOrigin.set('live');
    this.resultOriginLabel.set('');
    this.api
      .startJob(this.startRequest())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.submitting.set(false))
      )
      .subscribe({
        next: (job) => {
          this.job.set(job);
          this.startPolling(job.jobId);
        },
        error: (error: HttpErrorResponse) => {
          this.applyGithubAuthError(error);
          this.jobError.set(this.errorMessage(error));
        }
      });
  }

  protected connectGithub(): void {
    this.githubAuth.connect();
  }

  protected retryPolling(): void {
    const jobId = this.job()?.jobId;
    if (jobId && !this.isTerminal(this.job()?.status)) {
      this.jobError.set('');
      this.startPolling(jobId);
    }
  }

  protected onModeSelected(mode: string): void {
    this.modeControl.setValue(mode === 'DEEP' ? 'DEEP' : 'BASIC');
  }

  protected setDifferenceKindFilter(value: string): void {
    this.differenceKindFilter.set(value);
  }

  protected setDifferenceFileFilter(value: string): void {
    this.differenceFileFilter.set(value);
  }

  protected setFindingSeverityFilter(value: string): void {
    this.findingSeverityFilter.set(value);
  }

  protected focusReference(referenceId: string): void {
    this.focusedReferenceId.set(referenceId);
    window.setTimeout(() => {
      document.getElementById(referenceId)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
  }

  protected isFocused(referenceId: string): boolean {
    return this.focusedReferenceId() === referenceId;
  }

  protected statusLabel(value: string | null | undefined): string {
    return formatStatus(value);
  }

  protected statusClass(value: string | null | undefined): string {
    return `status-pill ${statusClassName(value)}`;
  }

  protected displayValue(value: string | null | undefined): string {
    return value?.trim() || '—';
  }

  protected referenceLabel(id: string): string {
    return id.startsWith('difference')
      ? 'Difference'
      : id.startsWith('finding')
        ? 'Finding'
        : id.startsWith('grounding')
          ? 'Code grounding'
          : 'Reference';
  }

  protected triggerImport(input: HTMLInputElement): void {
    input.value = '';
    input.click();
  }

  protected async importResult(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    try {
      const document = await readJsonFile(file, 'Importowany plik nie jest poprawnym JSON.');
      this.submitting.set(true);
      this.api
        .importResult(document)
        .pipe(
          takeUntilDestroyed(this.destroyRef),
          finalize(() => this.submitting.set(false))
        )
        .subscribe({
          next: (job) => {
            this.stopPolling();
            this.applyJobToForm(job);
            this.job.set(job);
            this.resultOrigin.set('imported');
            this.resultOriginLabel.set(file.name);
            this.jobError.set('');
          },
          error: (error: HttpErrorResponse) => this.jobError.set(this.errorMessage(error))
        });
    } catch (error) {
      this.jobError.set(error instanceof Error ? error.message : 'Nie udało się odczytać pliku.');
    } finally {
      input.value = '';
    }
  }

  protected exportResult(): void {
    const job = this.job();
    if (!job?.result) {
      return;
    }
    const exportedAt = new Date().toISOString();
    downloadJsonFile(
      buildRuntimeConfigurationExportFileName(job),
      buildRuntimeConfigurationExportEnvelope(job, exportedAt)
    );
  }

  protected trackDifference(_: number, difference: RuntimeConfigurationDifference): string {
    return difference.differenceId;
  }

  protected trackFinding(_: number, finding: RuntimeConfigurationFinding): string {
    return finding.findingId;
  }

  ngOnDestroy(): void {
    this.stopPolling();
  }

  private loadInputOptions(): void {
    this.inputOptionsLoading.set(true);
    this.api
      .getInputOptions()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.inputOptionsLoading.set(false))
      )
      .subscribe({
        next: (options) => {
          this.inputOptions.set(options);
          this.applyInputDefaults(options);
        },
        error: (error: HttpErrorResponse) => {
          this.inputOptionsError.set(this.errorMessage(error));
          this.inputOptions.set(EMPTY_INPUT_OPTIONS);
        }
      });
  }

  private loadAiOptions(): void {
    this.aiOptionsLoading.set(true);
    this.aiOptionsApi
      .getOptions()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.aiOptionsLoading.set(false))
      )
      .subscribe({
        next: (options) => {
          this.aiModelCatalog.set(normalizeAnalysisAiModelOptions(options));
          this.aiModelControl.setValue(listedDefaultAiModel(this.aiModelCatalog()), {
            emitEvent: false
          });
          this.syncReasoningEffort();
        },
        error: (error: HttpErrorResponse) => {
          this.aiOptionsError.set(this.errorMessage(error));
          this.aiModelCatalog.set(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
        }
      });
  }

  private loadGithubAuthStatus(): void {
    this.githubAuthError.set('');
    this.githubAuth
      .getStatus()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (status) => this.githubAuthStatus.set(status),
        error: (error: HttpErrorResponse) => this.githubAuthError.set(this.errorMessage(error))
      });
  }

  private refreshDeepPreflight(): void {
    const requestId = ++this.preflightRequestId;
    this.deepPreflight.set(null);
    this.preflightError.set('');
    if (
      this.modeControl.value !== 'DEEP'
      || !this.repositoryControl.value
      || !this.systemControl.value
    ) {
      this.preflightLoading.set(false);
      return;
    }
    this.preflightLoading.set(true);
    this.api
      .getDeepPreflight(
        this.repositoryControl.value,
        this.systemControl.value,
        this.codeRefControl.value
      )
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          if (requestId === this.preflightRequestId) {
            this.preflightLoading.set(false);
          }
        })
      )
      .subscribe({
        next: (preflight) => {
          if (requestId === this.preflightRequestId) {
            this.deepPreflight.set(preflight);
          }
        },
        error: (error: HttpErrorResponse) => {
          if (requestId === this.preflightRequestId) {
            this.preflightError.set(this.errorMessage(error));
          }
        }
      });
  }

  private startPolling(jobId: string): void {
    this.stopPolling();
    if (this.isTerminal(this.job()?.status)) {
      return;
    }
    this.pollingSubscription = this.pollingService
      .poll({
        load: () => this.api.getJob(jobId),
        isTerminal: (job) => this.isTerminal(job.status),
        initialDelayMs: 500,
        intervalMs: 1500
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (job) => this.job.set(job),
        error: (error: HttpErrorResponse) => {
          this.jobError.set(this.errorMessage(error));
          this.stopPolling();
        }
      });
  }

  private stopPolling(): void {
    this.pollingSubscription?.unsubscribe();
    this.pollingSubscription = undefined;
  }

  private loadLocalRun(analysisId: string): void {
    this.submitting.set(true);
    this.historyApi
      .getRun(analysisId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.submitting.set(false))
      )
      .subscribe({
        next: (detail) => this.importLocalRun(detail),
        error: (error: HttpErrorResponse) => this.jobError.set(this.errorMessage(error))
      });
  }

  private importLocalRun(detail: LocalAnalysisRunDetailResponse): void {
    if (detail.feature !== 'runtime-configuration-verification') {
      this.jobError.set(`Lokalny run ${detail.analysisId} nie jest Runtime Configuration Verification.`);
      return;
    }
    this.api
      .importResult(detail.exportEnvelope)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (job) => {
          this.applyJobToForm(job);
          this.job.set(job);
          this.resultOrigin.set('local');
          this.resultOriginLabel.set(detail.name || detail.analysisId);
        },
        error: (error: HttpErrorResponse) => this.jobError.set(this.errorMessage(error))
      });
  }

  private applyInputDefaults(options: RuntimeConfigurationVerificationInputOptions): void {
    if (!this.repositoryControl.value) {
      this.repositoryControl.setValue(options.repositories[0]?.id ?? '');
    }
    if (!this.systemControl.value) {
      this.systemControl.setValue(options.systems[0]?.id ?? '');
    }
    if (!this.sourceBranchControl.value) {
      this.sourceBranchControl.setValue(options.branches[0] ?? '');
    }
    if (!this.targetBranchControl.value) {
      this.targetBranchControl.setValue(
        options.branches.find((branch) => branch !== this.sourceBranchControl.value) ?? ''
      );
    }
  }

  private applyJobToForm(job: RuntimeConfigurationVerificationJobStateSnapshot): void {
    this.modeControl.setValue(job.mode, { emitEvent: false });
    this.repositoryControl.setValue(job.repositoryId, { emitEvent: false });
    this.systemControl.setValue(job.systemId, { emitEvent: false });
    this.sourceBranchControl.setValue(job.sourceBranch, { emitEvent: false });
    this.targetBranchControl.setValue(job.targetBranch, { emitEvent: false });
    this.codeRefControl.setValue(job.codeRef ?? '', { emitEvent: false });
    this.aiModelControl.setValue(job.aiModel ?? '', { emitEvent: false });
    this.reasoningEffortControl.setValue(job.reasoningEffort ?? '', { emitEvent: false });
  }

  private startRequest(): RuntimeConfigurationVerificationJobStartRequest {
    const model = this.aiModelControl.value.trim()
      || listedDefaultAiModel(this.aiModelCatalog());
    const reasoningEffort = this.reasoningEffortControl.value.trim()
      || defaultReasoningEffortForAiModel(this.aiModelCatalog(), model);
    return {
      mode: this.modeControl.value,
      repositoryId: this.repositoryControl.value,
      systemId: this.systemControl.value,
      sourceBranch: this.sourceBranchControl.value,
      targetBranch: this.targetBranchControl.value,
      codeRef: this.modeControl.value === 'DEEP' && this.codeRefControl.value.trim()
        ? this.codeRefControl.value.trim()
        : undefined,
      model: model || undefined,
      reasoningEffort: reasoningEffort || undefined
    };
  }

  private syncReasoningEffort(): void {
    const efforts = reasoningEffortsForAiModel(this.aiModelCatalog(), this.aiModelControl.value);
    if (!efforts.length) {
      this.reasoningEffortControl.setValue('', { emitEvent: false });
      this.reasoningEffortControl.disable({ emitEvent: false });
      return;
    }
    this.reasoningEffortControl.enable({ emitEvent: false });
    if (!efforts.includes(this.reasoningEffortControl.value)) {
      this.reasoningEffortControl.setValue(
        defaultReasoningEffortForAiModel(this.aiModelCatalog(), this.aiModelControl.value),
        { emitEvent: false }
      );
    }
  }

  private isTerminal(status: string | null | undefined): boolean {
    return ['COMPLETED', 'COMPLETED_WITH_LIMITATIONS', 'FAILED'].includes(status ?? '');
  }

  private errorMessage(error: HttpErrorResponse): string {
    const response = error.error as Partial<ApiErrorResponse> | null;
    return response?.message || error.message || 'Nie udało się wykonać operacji.';
  }

  private applyGithubAuthError(error: HttpErrorResponse): void {
    const response = error.error as Partial<ApiErrorResponse> | null;
    if (
      response?.code !== 'GITHUB_COPILOT_AUTH_REQUIRED'
      && response?.code !== 'GITHUB_COPILOT_REAUTH_REQUIRED'
    ) {
      return;
    }
    this.githubAuthStatus.set({
      mode: 'GITHUB_APP',
      required: true,
      connected: false,
      githubLogin: null,
      displayName: null,
      tokenExpiresAt: null,
      reauthRequired: response.code === 'GITHUB_COPILOT_REAUTH_REQUIRED',
      authStartUrl: null
    });
  }

  private bumpFormRevision(): void {
    this.formRevision.update((value) => value + 1);
  }
}

function unique(values: string[]): string[] {
  return [...new Set(values.filter(Boolean))];
}
