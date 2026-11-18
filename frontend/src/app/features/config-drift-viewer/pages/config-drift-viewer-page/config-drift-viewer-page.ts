import { AiExecutionControlsComponent } from '../../../../components/ai-execution-controls/ai-execution-controls';
import type { FormTourSource } from '../../../../core/models/form-tour';
import { CONFIG_DRIFT_VIEWER_FORM_TOUR } from '../../config-drift-viewer-form-tour';
import { ChoiceSelectComponent, ChoiceOptionComponent } from '../../../../components/choice-select/choice-select';
import { TextFieldComponent } from '../../../../components/text-field/text-field';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, HostListener, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize, Subscription } from 'rxjs';

import { AnalysisFeatureAsideComponent } from '../../../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisReportPanelComponent } from '../../../../components/analysis-report-panel/analysis-report-panel';
import {
  AnalysisResultTabItem,
  AnalysisResultTabStatus,
  AnalysisResultTabsComponent
} from '../../../../components/analysis-result-tabs/analysis-result-tabs';
import { AnalysisStepsPanelComponent } from '../../../../components/analysis-steps-panel/analysis-steps-panel';
import { AnalysisShareMenuComponent } from '../../../../components/analysis-share-menu/analysis-share-menu';
import { buildAnalysisShareDocument, buildReportShareDocument, markdownList } from '../../../../core/utils/analysis-share.utils';
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
import { readJsonFile } from '../../../../core/utils/json-file.utils';
import { rememberLocalRunId } from '../../../../core/utils/local-run-route.utils';
import {
  ConfigDriftViewerDiffRendererComponent
} from '../../components/config-drift-viewer-diff-renderer/config-drift-viewer-diff-renderer';
import {
  ConfigDriftViewerComponentRunSnapshot,
  ConfigDriftViewerDeepPreflight,
  ConfigDriftViewerDeterministicContext,
  ConfigDriftViewerExportEnvelope,
  ConfigDriftViewerInputOptions,
  ConfigDriftViewerJobStartRequest,
  ConfigDriftViewerJobStateSnapshot,
  ConfigDriftViewerMode
} from '../../models/config-drift-viewer.models';
import { ConfigDriftViewerApiService } from '../../services/config-drift-viewer-api.service';
import {
  CONFIG_DRIFT_FINDINGS_TITLE,
  ConfigDriftFindingView,
  configDriftFindingSeverityLabel,
  configDriftFindingsMarkdown,
  presentConfigDriftFindings,
  presentConfigDriftFindingsReport
} from '../../utils/config-drift-viewer-findings.presentation';

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
  imports: [AiExecutionControlsComponent, ChoiceOptionComponent, ChoiceSelectComponent, TextFieldComponent,
    ReactiveFormsModule,
    MatTooltipModule,
    AnalysisFeatureAsideComponent,
    AnalysisReportPanelComponent,
    AnalysisResultTabsComponent,
    AnalysisStepsPanelComponent,
    AnalysisShareMenuComponent,
    ConfigDriftViewerDiffRendererComponent
  ],
  templateUrl: './config-drift-viewer-page.html',
  styleUrl: './config-drift-viewer-page.scss'
})
export class ConfigDriftViewerPageComponent implements OnDestroy, FormTourSource {
  private readonly api = inject(ConfigDriftViewerApiService);
  private readonly aiOptionsApi = inject(AiOptionsApiService);
  private readonly pollingService = inject(AnalysisJobPollingService);
  private readonly historyApi = inject(AnalysisRunHistoryApiService);
  private readonly githubAuth = inject(GithubAuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
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
  readonly branchOptions = computed<SelectOption[]>(() =>
    this.inputOptions().branches.map(value => ({ value, label: value }))
  );
  readonly systemOptions = computed<SelectOption[]>(() =>
    this.inputOptions().systems.map((option) => ({
      value: option.id,
      label: `${option.label} · ${option.configurationDirectory}`
    }))
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
    return status !== null && !status.configured;
  });
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
  readonly formTourSteps = computed(() =>
    this.inputOptionsLoading() || this.submitting() || this.workflowRunning() || this.resultOrigin() !== 'live'
      ? []
      : CONFIG_DRIFT_VIEWER_FORM_TOUR
  );
  readonly componentTabs = computed<AnalysisResultTabItem[]>(() =>
    (this.job()?.components ?? []).map((component) => ({
      id: component.systemId,
      tabLabel: component.systemLabel || component.systemId,
      status: this.componentTabStatus(component)
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
  readonly comparisonNotice = computed(() => {
    const component = this.activeComponent();
    const result = component?.result;
    if (!component || !result) return null;
    const deterministic = result.deterministicResult;
    if (deterministic.status === 'INCOMPLETE'
      || !this.hasCompleteCoverage(deterministic.sourceCoverage)
      || !this.hasCompleteCoverage(deterministic.targetCoverage)) {
      return { label: 'Niepełne porównanie', warning: true };
    }
    if (deterministic.status === 'REVIEW_REQUIRED') {
      return { label: 'Wymaga weryfikacji', warning: true };
    }
    if (component.status === 'COMPLETED_WITH_LIMITATIONS' || result.status === 'INCOMPLETE'
      || result.visibilityLimits.length || result.aiSecondOpinion?.visibilityLimits.length) {
      return { label: 'Analiza z ograniczeniami', warning: true };
    }
    return {
      label: this.isTerminal(component.status) ? '' : 'Analiza w toku',
      warning: false
    };
  });
  readonly comparisonDetails = computed(() => {
    const job = this.job();
    const component = this.activeComponent();
    if (!job || !component) return '';
    const result = component.result;
    const deterministic = result?.deterministicResult;
    const limits = unique([...(result?.visibilityLimits ?? []), ...(result?.aiSecondOpinion?.visibilityLimits ?? [])]);
    return [
      `Komponent: ${component.systemLabel || component.systemId}`,
      `Repozytorium: ${job.repositoryId}`,
      `Tryb: ${job.mode}`,
      `Przebieg: ${this.componentTabStatus(component)?.label ?? 'Analiza zakończona'}`,
      component.currentStepLabel || job.currentStepLabel
        ? `Etap: ${component.currentStepLabel || job.currentStepLabel}` : '',
      job.codeRef ? `Ref kodu: ${job.codeRef}` : '',
      deterministic ? 'Porównanie deterministyczne (FACT)' : '',
      deterministic
        ? `Ustalenia: ${deterministic.findings.length} · dokumenty: ${deterministic.documents.length} · referencje: ${deterministic.references.length}` : '',
      deterministic ? this.coverageDetails(deterministic.sourceBranch, deterministic.sourceCoverage) : '',
      deterministic ? this.coverageDetails(deterministic.targetBranch, deterministic.targetCoverage) : '',
      limits.length ? `Ograniczenia:\n${limits.join('\n')}` : ''
    ].filter(Boolean).join('\n');
  });
  readonly findingsTitle = CONFIG_DRIFT_FINDINGS_TITLE;
  readonly findingViews = computed(() => {
    const context = this.deterministic();
    return context ? presentConfigDriftFindings(context) : [];
  });
  readonly filteredFindings = computed(() => {
    return this.findingViews().filter((finding) =>
      this.findingSeverityFilter() === 'ALL'
      || finding.severity === this.findingSeverityFilter()
    );
  });
  readonly findingSeverities = computed(() =>
    unique(this.findingViews().map(finding => finding.severity))
      .map(value => ({ value, label: configDriftFindingSeverityLabel(value) }))
  );
  readonly presentationReport = computed(() => {
    const report = this.activeComponent()?.report;
    return report ? presentConfigDriftFindingsReport(report, this.findingViews()) : null;
  });
  readonly resultAvailable = computed(() => Boolean(this.activeComponent()?.result));
  readonly shareDocument = computed(() => {
    const component = this.activeComponent();
    const result = component?.result;
    if (!component || !result) return null;
    const fileName = `config-drift-viewer-${component.systemId.replace(/[^a-zA-Z0-9_-]/g, '-')}.md`;
    const report = this.presentationReport();
    if (report) return buildReportShareDocument(report, fileName);
    const differences = result.deterministicResult.differences.map((difference) =>
      `- ${difference.path}: ${difference.kind} (${difference.sourceValueToken ?? 'brak'} → ${difference.targetValueToken ?? 'brak'})`
    );
    const findingsMarkdown = configDriftFindingsMarkdown(this.findingViews());
    const opinion = result.aiSecondOpinion;
    const content = [
      `# Config Drift Viewer: ${component.systemLabel || component.systemId}`,
      `${this.job()?.sourceBranch} → ${this.job()?.targetBranch}`,
      this.deterministicSummary(result.deterministicResult),
      '## Różnice konfiguracji',
      differences.join('\n') || 'Brak różnic.',
      findingsMarkdown ? `## ${this.findingsTitle}\n\n${findingsMarkdown}` : '',
      opinion ? `## Druga opinia AI\n\n${opinion.summary}` : '',
      ...(opinion?.observations ?? []).map((observation) =>
        `### ${observation.summary}\n\n${observation.explanation}`
      ),
      opinion ? markdownList('Zalecane kontrole', opinion.recommendedHumanChecks) : ''
    ].filter(Boolean).join('\n\n');
    const meta = [
      markdownList('Szczegóły techniczne uwag', this.findingViews().map(finding => `${finding.id}: ${finding.code}`)),
      markdownList('Referencje', result.deterministicResult.references.map((reference) =>
        `${reference.referenceId}: ${reference.sourcePath} → ${reference.targetPath}`
      )),
      markdownList('Limity widoczności', [...result.visibilityLimits, ...(opinion?.visibilityLimits ?? [])])
    ].filter(Boolean).join('\n\n');
    return buildAnalysisShareDocument(fileName, content, meta);
  });

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
        if (localRunId && this.job()?.jobId !== localRunId) {
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
          rememberLocalRunId(this.router, this.route, job.jobId);
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

  protected openCopilotSettings(): void {
    this.githubAuth.openSettings();
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

  private componentTabStatus(component: ConfigDriftViewerComponentRunSnapshot): AnalysisResultTabStatus | undefined {
    switch (component.status) {
      case 'QUEUED':
        return { kind: 'loading', label: 'Oczekuje na uruchomienie' };
      case 'RUNNING': {
        const label = 'Analiza w toku';
        return {
          kind: 'loading',
          label,
          tooltip: component.currentStepLabel ? `${label}\n${component.currentStepLabel}` : label
        };
      }
      case 'COMPLETED':
        return undefined;
      case 'COMPLETED_WITH_LIMITATIONS': {
        const label = 'Analiza zakończona z ograniczeniami';
        const limits = unique(component.result?.visibilityLimits ?? []);
        return {
          kind: 'info',
          label,
          tooltip: [label, ...limits].join('\n')
        };
      }
      case 'FAILED': {
        const label = 'Analiza zakończona błędem';
        return {
          kind: 'error',
          label,
          tooltip: component.errorMessage ? `${label}\n${component.errorMessage}` : label
        };
      }
      default:
        return { kind: 'info', label: this.statusLabel(component.status) };
    }
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

  protected trackFinding(_: number, finding: ConfigDriftFindingView): string {
    return finding.id;
  }

  private coverageSummary(
    branch: string,
    coverage: ConfigDriftViewerDeterministicContext['sourceCoverage']
  ): string {
    const status = this.hasCompleteCoverage(coverage) ? 'Complete' : 'Incomplete';
    const files = coverage?.files?.length ?? 0;
    return `${branch}: ${status}, ${this.fileCountLabel(files)}`;
  }

  private coverageDetails(
    branch: string,
    coverage: ConfigDriftViewerDeterministicContext['sourceCoverage']
  ): string {
    if (!coverage) return `${branch}: brak danych o kompletności.`;
    const status = !coverage.branchExists ? 'branch nie istnieje'
      : this.hasCompleteCoverage(coverage) ? 'kompletne dane' : 'niepełne dane';
    const fileStatuses: Record<string, string> = {
      MISSING: 'brak pliku', AMBIGUOUS: 'niejednoznaczna ścieżka',
      TRUNCATED: 'niepełna zawartość', ERROR: 'błąd odczytu', BRANCH_MISSING: 'brak brancha'
    };
    return [
      `${branch}: ${status}. Pliki: ${coverage.files.length}.`,
      ...coverage.files.filter(file => file.status !== 'AVAILABLE')
        .map(file => `  ${file.path}: ${fileStatuses[file.status] ?? this.statusLabel(file.status)}`)
    ].join('\n');
  }

  private hasCompleteCoverage(coverage: ConfigDriftViewerDeterministicContext['sourceCoverage']): boolean {
    // The JSON contract has no `complete` field; mirror BranchCoverage.complete() from its file evidence.
    return Boolean(coverage?.branchExists && coverage.files.length === 3
      && coverage.files.every(file => file.status === 'AVAILABLE'));
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
    const envelope = detail.exportEnvelope as Partial<ConfigDriftViewerExportEnvelope> | null;
    const savedJob = envelope?.schema === 'tdw.config-drift-viewer-export'
      && envelope.version === 1
      && envelope.payload?.type === 'config-drift-viewer-analysis'
      && envelope.payload.resultContract === 'config-drift-viewer-result-v1'
      ? envelope.payload.job : null;
    if (savedJob?.jobId === detail.analysisId && !this.isTerminal(savedJob.status)) {
      this.applyJobToForm(savedJob);
      this.applyJobSnapshot(savedJob, true);
      this.resultOrigin.set('local');
      this.resultOriginLabel.set(detail.name || detail.analysisId);
      this.startPolling(savedJob.jobId);
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

  selectAiModel(value: string): void {
    this.aiModelControl.markAsDirty();
    this.aiModelControl.setValue(value);
  }

  selectReasoningEffort(value: string): void {
    this.reasoningEffortControl.markAsDirty();
    this.reasoningEffortControl.setValue(value);
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
      response?.code !== 'COPILOT_PAT_REQUIRED'
      && response?.code !== 'COPILOT_PAT_INVALID'
    ) {
      return;
    }
    this.githubAuthStatus.set({
      configured: false,
      settingsUrl: '/workspace-settings'
    });
  }

  private bumpFormRevision(): void {
    this.formRevision.update((value) => value + 1);
  }
}

function unique(values: string[]): string[] {
  return [...new Set(values.filter(Boolean))];
}
