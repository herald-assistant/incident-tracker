import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, HostListener, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { finalize, Subscription } from 'rxjs';

import { AnalysisFeatureAsideComponent } from '../../../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisReportPanelComponent } from '../../../../components/analysis-report-panel/analysis-report-panel';
import {
  AnalysisResultTabItem,
  AnalysisResultTabsComponent
} from '../../../../components/analysis-result-tabs/analysis-result-tabs';
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
  ConfigDriftViewerDiffRendererComponent
} from '../../components/config-drift-viewer-diff-renderer/config-drift-viewer-diff-renderer';
import {
  ConfigDriftViewerDeepPreflight,
  ConfigDriftViewerDeterministicContext,
  ConfigDriftViewerFinding,
  ConfigDriftViewerInputOptions,
  ConfigDriftViewerJobStartRequest,
  ConfigDriftViewerJobStateSnapshot,
  ConfigDriftViewerMode
} from '../../models/config-drift-viewer.models';
import { ConfigDriftViewerApiService } from '../../services/config-drift-viewer-api.service';
import {
  buildConfigDriftViewerExportEnvelope,
  buildConfigDriftViewerExportFileName
} from '../../utils/config-drift-viewer-import-export.utils';

type ResultOrigin = 'live' | 'local' | 'imported';
type SelectOption = { value: string; label: string };

const EMPTY_INPUT_OPTIONS: ConfigDriftViewerInputOptions = {
  modes: ['BASIC', 'DEEP'],
  branches: [],
  repositories: [],
  systems: []
};

@Component({
  selector: 'app-config-drift-viewer-page',
  imports: [
    ReactiveFormsModule,
    AnalysisFeatureAsideComponent,
    AnalysisReportPanelComponent,
    AnalysisResultTabsComponent,
    AnalysisStepsPanelComponent,
    ConfigDriftViewerDiffRendererComponent
  ],
  templateUrl: './config-drift-viewer-page.html',
  styleUrl: './config-drift-viewer-page.scss'
})
export class ConfigDriftViewerPageComponent implements OnDestroy {
  private readonly api = inject(ConfigDriftViewerApiService);
  private readonly aiOptionsApi = inject(AiOptionsApiService);
  private readonly pollingService = inject(AnalysisJobPollingService);
  private readonly historyApi = inject(AnalysisRunHistoryApiService);
  private readonly githubAuth = inject(GithubAuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private pollingSubscription?: Subscription;
  private preflightRequestId = 0;

  readonly modeControl = new FormControl<ConfigDriftViewerMode>('BASIC', {
    nonNullable: true
  });
  protected readonly deepModeSelectionDisabled = signal(true);
  readonly repositoryControl = new FormControl('', { nonNullable: true });
  readonly systemControl = new FormControl<string[]>([], { nonNullable: true });
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
  readonly deepPreflight = signal<ConfigDriftViewerDeepPreflight | null>(null);
  readonly preflightLoading = signal(false);
  readonly preflightError = signal('');
  readonly job = signal<ConfigDriftViewerJobStateSnapshot | null>(null);
  readonly jobError = signal('');
  readonly submitting = signal(false);
  readonly resultOrigin = signal<ResultOrigin>('live');
  readonly resultOriginLabel = signal('');
  readonly findingSeverityFilter = signal('ALL');
  readonly focusedReferenceId = signal('');
  readonly activeComponentId = signal('');
  readonly systemSelectOpen = signal(false);
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
  readonly selectedSystemIds = computed(() => {
    this.formRevision();
    const selected = new Set(this.systemControl.value);
    return this.inputOptions().systems
      .map((system) => system.id)
      .filter((systemId) => selected.has(systemId));
  });
  readonly systemSelectionLabel = computed(() =>
    `${this.selectedSystemIds().length} z ${this.inputOptions().systems.length} wybranych`
  );
  readonly systemSelectionInvalid = computed(() => this.selectedSystemIds().length === 0);
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
    this.formRevision();
    if (this.modeControl.value !== 'DEEP') {
      return false;
    }
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
      && this.selectedSystemIds().length > 0
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
  readonly componentTabs = computed<AnalysisResultTabItem[]>(() =>
    (this.job()?.components ?? []).map((component) => ({
      id: component.systemId,
      tabLabel: `${component.systemLabel || component.systemId} · ${formatStatus(component.status)}`
    }))
  );
  readonly activeComponent = computed(() => {
    const components = this.job()?.components ?? [];
    return components.find((component) => component.systemId === this.activeComponentId())
      ?? components[0]
      ?? null;
  });
  readonly aiWorkflowRunning = computed(() =>
    Boolean(this.activeComponent()?.steps.some((step) =>
      step.phase === 'AI' && ['RUNNING', 'IN_PROGRESS'].includes(step.status)
    ))
  );
  readonly aiWorkflowItemCount = computed(() => {
    const component = this.activeComponent();
    if (!component) {
      return 0;
    }
    return component.aiActivityEvents.length
      + component.toolEvidenceSections.reduce((count, section) => count + section.items.length, 0);
  });
  readonly deterministic = computed(() => this.activeComponent()?.result?.deterministicResult ?? null);
  readonly filteredFindings = computed(() => {
    const findings = this.deterministic()?.findings ?? [];
    return findings.filter((finding) =>
      this.findingSeverityFilter() === 'ALL'
      || finding.severity === this.findingSeverityFilter()
    );
  });
  readonly findingSeverities = computed(() =>
    unique((this.deterministic()?.findings ?? []).map((finding) => finding.severity))
  );
  readonly resultAvailable = computed(() => Boolean(this.activeComponent()?.result));
  readonly canExport = computed(() => Boolean(
    this.job()?.components.some((component) => component.result) && this.isTerminal(this.job()?.status)
  ));

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
    this.activeComponentId.set('');
    this.resetComponentViewState();
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
          this.applyJobSnapshot(job);
          this.startPolling(job.jobId);
        },
        error: (error: HttpErrorResponse) => {
          this.applyGithubAuthError(error);
          this.jobError.set(this.errorMessage(error));
        }
      });
  }

  protected toggleSystemSelect(event: MouseEvent): void {
    event.stopPropagation();
    this.systemSelectOpen.update((open) => !open);
  }

  protected selectAllSystems(): void {
    this.systemControl.setValue(this.inputOptions().systems.map((system) => system.id));
  }

  protected clearSystems(): void {
    this.systemControl.setValue([]);
  }

  protected setSystemSelected(systemId: string, selected: boolean): void {
    const next = new Set(this.systemControl.value);
    if (selected) {
      next.add(systemId);
    } else {
      next.delete(systemId);
    }
    this.systemControl.setValue(
      this.inputOptions().systems
        .map((system) => system.id)
        .filter((candidate) => next.has(candidate))
    );
  }

  protected isSystemSelected(systemId: string): boolean {
    return this.systemControl.value.includes(systemId);
  }

  protected closeSystemSelect(): void {
    this.systemSelectOpen.set(false);
  }

  @HostListener('document:click')
  protected closeSystemSelectFromOutside(): void {
    this.closeSystemSelect();
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
    if (mode === 'DEEP' && this.deepModeSelectionDisabled()) {
      return;
    }
    this.modeControl.setValue(mode === 'DEEP' ? 'DEEP' : 'BASIC');
  }

  protected setFindingSeverityFilter(value: string): void {
    this.findingSeverityFilter.set(value);
  }

  protected selectComponent(systemId: string): void {
    if (!this.job()?.components.some((component) => component.systemId === systemId)) {
      return;
    }
    this.activeComponentId.set(systemId);
    this.resetComponentViewState();
  }

  protected focusReference(referenceId: string): void {
    this.focusedReferenceId.set(referenceId);
    window.setTimeout(() => {
      const target = document.getElementById(referenceId);
      if (target && typeof target.scrollIntoView === 'function') {
        target.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }
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

  protected deterministicSummary(result: ConfigDriftViewerDeterministicContext): string {
    return [
      `różnice: ${result.differences.length}`,
      `findings: ${result.findings.length}`,
      `dokumenty: ${result.documents.length}`,
      `referencje: ${result.references.length}`,
      `${this.coverageSummary(result.sourceBranch, result.sourceCoverage)} → ${this.coverageSummary(
        result.targetBranch,
        result.targetCoverage
      )}`
    ].join(' · ');
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
            this.applyJobSnapshot(job, true);
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
    if (!job?.components.some((component) => component.result)) {
      return;
    }
    const exportedAt = new Date().toISOString();
    downloadJsonFile(
      buildConfigDriftViewerExportFileName(job),
      buildConfigDriftViewerExportEnvelope(job, exportedAt)
    );
  }

  protected trackFinding(_: number, finding: ConfigDriftViewerFinding): string {
    return finding.findingId;
  }

  protected findingTitle(finding: ConfigDriftViewerFinding): string {
    if (finding.code.endsWith('VAR_UNSUPPORTED_SYNTAX')) {
      return 'Błędna składnia blokuje rozwiązanie referencji';
    }
    if (finding.code === 'HARDCODED_SENSITIVE_VALUE_ADDED') {
      return 'Dodano literalną wartość wrażliwą';
    }
    return this.statusLabel(finding.code);
  }

  protected findingDescription(finding: ConfigDriftViewerFinding): string | null {
    if (finding.code.endsWith('VAR_UNSUPPORTED_SYNTAX') && finding.referenceIds.length > 0) {
      return 'Nierozwiązana referencja jest skutkiem tego samego błędu składni.';
    }
    if (finding.code === 'HARDCODED_SENSITIVE_VALUE_ADDED') {
      return 'W target dodano wartość bez bezpiecznego placeholdera. Popraw ją przed wdrożeniem.';
    }
    return null;
  }

  protected findingLocation(finding: ConfigDriftViewerFinding): string | null {
    if (!finding.filePath) {
      return null;
    }
    return finding.line ? `${finding.filePath}:${finding.line}` : finding.filePath;
  }

  private coverageSummary(
    branch: string,
    coverage: ConfigDriftViewerDeterministicContext['sourceCoverage']
  ): string {
    const status = coverage?.complete ? 'Complete' : 'Incomplete';
    const files = coverage?.files?.length ?? 0;
    return `${branch}: ${status}, ${this.fileCountLabel(files)}`;
  }

  private fileCountLabel(files: number): string {
    return files === 1 ? '1 plik' : `${files} plików`;
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
      || this.selectedSystemIds().length === 0
    ) {
      this.preflightLoading.set(false);
      return;
    }
    this.preflightLoading.set(true);
    this.api
      .getDeepPreflight(
        this.repositoryControl.value,
        this.selectedSystemIds()[0],
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
        next: (job) => this.applyJobSnapshot(job),
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
    if (detail.feature !== 'config-drift-viewer') {
      this.jobError.set(`Lokalny run ${detail.analysisId} nie jest Config Drift Viewer.`);
      return;
    }
    this.api
      .importResult(detail.exportEnvelope)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (job) => {
          this.applyJobToForm(job);
          this.applyJobSnapshot(job, true);
          this.resultOrigin.set('local');
          this.resultOriginLabel.set(detail.name || detail.analysisId);
        },
        error: (error: HttpErrorResponse) => this.jobError.set(this.errorMessage(error))
      });
  }

  private applyInputDefaults(options: ConfigDriftViewerInputOptions): void {
    if (!this.repositoryControl.value) {
      this.repositoryControl.setValue(options.repositories[0]?.id ?? '');
    }
    if (this.systemControl.value.length === 0) {
      this.systemControl.setValue(options.systems.map((system) => system.id));
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

  private applyJobToForm(job: ConfigDriftViewerJobStateSnapshot): void {
    this.modeControl.setValue(job.mode, { emitEvent: false });
    this.repositoryControl.setValue(job.repositoryId, { emitEvent: false });
    this.systemControl.setValue(job.systemIds, { emitEvent: false });
    this.sourceBranchControl.setValue(job.sourceBranch, { emitEvent: false });
    this.targetBranchControl.setValue(job.targetBranch, { emitEvent: false });
    this.codeRefControl.setValue(job.codeRef ?? '', { emitEvent: false });
    this.aiModelControl.setValue(job.aiModel ?? '', { emitEvent: false });
    this.reasoningEffortControl.setValue(job.reasoningEffort ?? '', { emitEvent: false });
    this.bumpFormRevision();
  }

  private applyJobSnapshot(
    job: ConfigDriftViewerJobStateSnapshot,
    resetSelection = false
  ): void {
    if (resetSelection) {
      this.activeComponentId.set('');
      this.resetComponentViewState();
    }
    this.job.set(job);
    if (!job.components.some((component) => component.systemId === this.activeComponentId())) {
      this.activeComponentId.set(job.components[0]?.systemId ?? '');
      this.resetComponentViewState();
    }
  }

  private resetComponentViewState(): void {
    this.findingSeverityFilter.set('ALL');
    this.focusedReferenceId.set('');
  }

  private startRequest(): ConfigDriftViewerJobStartRequest {
    const deep = this.modeControl.value === 'DEEP';
    const request: ConfigDriftViewerJobStartRequest = {
      mode: this.modeControl.value,
      repositoryId: this.repositoryControl.value,
      systemIds: this.selectedSystemIds(),
      sourceBranch: this.sourceBranchControl.value,
      targetBranch: this.targetBranchControl.value
    };
    if (!deep) {
      return request;
    }
    const model = this.aiModelControl.value.trim() || listedDefaultAiModel(this.aiModelCatalog());
    const reasoningEffort = this.reasoningEffortControl.value.trim()
      || defaultReasoningEffortForAiModel(this.aiModelCatalog(), model);
    if (this.codeRefControl.value.trim()) {
      request.codeRef = this.codeRefControl.value.trim();
    }
    if (model) {
      request.model = model;
    }
    if (reasoningEffort) {
      request.reasoningEffort = reasoningEffort;
    }
    return request;
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
