import { HttpErrorResponse } from '@angular/common/http';
import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute } from '@angular/router';
import { Subscription, finalize } from 'rxjs';

import { AnalysisFeatureAsideComponent } from '../../../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisStepsPanelComponent } from '../../../../components/analysis-steps-panel/analysis-steps-panel';
import {
  AnalysisAiModelOptionsResponse,
  AnalysisPreparedPrompt,
  ApiErrorResponse,
  GitHubAuthStatus,
  LocalAnalysisRunDetailResponse
} from '../../../../core/models/analysis.models';
import { AiOptionsApiService } from '../../../../core/services/ai-options-api.service';
import { AnalysisJobPollingService } from '../../../../core/services/analysis-job-polling.service';
import { AnalysisRunHistoryApiService } from '../../../../core/services/analysis-run-history-api.service';
import { GithubAuthService } from '../../../../core/services/github-auth.service';
import {
  EMPTY_ANALYSIS_AI_MODEL_OPTIONS,
  defaultReasoningEffortForAiModel,
  listedDefaultAiModel,
  normalizeAnalysisAiModelOptions,
  reasoningEffortsForAiModel
} from '../../../../core/utils/analysis-ai-model-options.utils';
import {
  AnalysisAiCostEstimate,
  estimateAnalysisAiCost
} from '../../../../core/utils/analysis-ai-usage-cost.utils';
import { formatStatus, statusClassName } from '../../../../core/utils/analysis-display.utils';
import {
  downloadJsonFile,
  formatFileTimestamp,
  readJsonFile,
  sanitizeFileNamePart
} from '../../../../core/utils/json-file.utils';
import {
  DeliveryAssessmentAggregate,
  DeliveryAssessmentDimensions,
  DeliveryAssessmentUnit,
  DeliveryEffectivenessAssessmentExportEnvelope,
  DeliveryEffectivenessAssessmentJobStartRequest,
  DeliveryEffectivenessAssessmentJobStateSnapshot
} from '../../models/delivery-effectiveness-assessment.models';
import { DeliveryEffectivenessAssessmentApiService } from '../../services/delivery-effectiveness-assessment-api.service';

type SelectOption = { value: string; label: string };
type DimensionRow = { label: string; value: number };

@Component({
  selector: 'app-delivery-effectiveness-assessment-page',
  imports: [
    ReactiveFormsModule,
    CurrencyPipe,
    DecimalPipe,
    MatTooltipModule,
    AnalysisFeatureAsideComponent,
    AnalysisStepsPanelComponent
  ],
  templateUrl: './delivery-effectiveness-assessment-page.html',
  styleUrl: './delivery-effectiveness-assessment-page.scss'
})
export class DeliveryEffectivenessAssessmentPageComponent implements OnDestroy {
  private readonly api = inject(DeliveryEffectivenessAssessmentApiService);
  private readonly aiOptionsApi = inject(AiOptionsApiService);
  private readonly pollingService = inject(AnalysisJobPollingService);
  private readonly historyApi = inject(AnalysisRunHistoryApiService);
  private readonly githubAuth = inject(GithubAuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private pollingSubscription?: Subscription;

  readonly jiraProjectControl = new FormControl('', { nonNullable: true });
  readonly fromDateControl = new FormControl(defaultDate(-30), { nonNullable: true });
  readonly toDateControl = new FormControl(defaultDate(0), { nonNullable: true });
  readonly aiModelControl = new FormControl('', { nonNullable: true });
  readonly reasoningEffortControl = new FormControl('', { nonNullable: true });

  readonly aiModelCatalog = signal<AnalysisAiModelOptionsResponse>(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
  readonly aiOptionsLoading = signal(true);
  readonly aiOptionsError = signal('');
  readonly githubAuthStatus = signal<GitHubAuthStatus | null>(null);
  readonly githubAuthError = signal('');
  readonly job = signal<DeliveryEffectivenessAssessmentJobStateSnapshot | null>(null);
  readonly jobError = signal('');
  readonly submitting = signal(false);
  readonly portabilityBusy = signal(false);
  readonly localRunName = signal('');
  readonly expandedUnits = signal<ReadonlySet<string>>(new Set());
  private readonly formRevision = signal(0);

  readonly aiModelOptions = computed<SelectOption[]>(() =>
    this.aiModelCatalog().models.map((model) => ({ value: model.id, label: model.name }))
  );
  readonly reasoningEffortOptions = computed(() =>
    reasoningEffortsForAiModel(this.aiModelCatalog(), this.aiModelControl.value)
  );
  readonly workflowRunning = computed(() => {
    const job = this.job();
    return Boolean(job && !this.isTerminal(job.status));
  });
  readonly canImport = computed(() =>
    !this.submitting() && !this.portabilityBusy() && !this.workflowRunning()
  );
  readonly hasTerminalRun = computed(() => this.isTerminal(this.job()?.status));
  readonly canExport = computed(() => {
    const job = this.job();
    return Boolean(
      job && this.isTerminal(job.status) && !this.submitting() && !this.portabilityBusy()
    );
  });
  readonly preparedPrompts = computed<AnalysisPreparedPrompt[]>(() =>
    (this.job()?.units ?? [])
      .filter((unit) => Boolean(unit.preparedPrompt))
      .map((unit) => ({
        key: unit.unitId,
        title: `${unit.issues.map((issue) => issue.issueKey).join(', ') || unit.unitId} · ${unit.unitId}`,
        preparedAt: unit.promptPreparedAt,
        prompt: unit.preparedPrompt!
      }))
  );
  readonly usageCostEstimate = computed(() =>
    estimateAnalysisAiCost(this.job()?.aggregate?.usage ?? null)
  );
  readonly progressPercent = computed(() => {
    const job = this.job();
    if (!job) {
      return 0;
    }
    if (this.isTerminal(job.status)) {
      return 100;
    }
    const total = Math.max(job.totalIssues, job.discoveredIssues, job.units.length);
    return total > 0 ? Math.min(99, Math.round((job.processedIssues / total) * 100)) : 4;
  });
  readonly canStart = computed(() => {
    this.formRevision();
    const project = this.jiraProjectControl.value.trim();
    const from = this.fromDateControl.value;
    const to = this.toDateControl.value;
    const auth = this.githubAuthStatus();
    return Boolean(
      project
        && /^[A-Za-z][A-Za-z0-9_-]{0,49}$/.test(project)
        && from
        && to
        && from <= to
        && this.aiModelControl.value
        && !this.submitting()
        && !this.portabilityBusy()
        && !(auth?.mode === 'GITHUB_APP' && (!auth.connected || auth.reauthRequired))
    );
  });

  constructor() {
    [
      this.jiraProjectControl,
      this.fromDateControl,
      this.toDateControl,
      this.aiModelControl,
      this.reasoningEffortControl
    ].forEach((control) => control.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.formRevision.update((revision) => revision + 1)));
    this.aiModelControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.syncReasoningEffort());
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

  ngOnDestroy(): void {
    this.stopPolling();
  }

  protected startJob(): void {
    if (!this.canStart()) {
      return;
    }
    this.stopPolling();
    this.jobError.set('');
    this.localRunName.set('');
    this.expandedUnits.set(new Set());
    this.submitting.set(true);
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

  protected triggerImport(input: HTMLInputElement): void {
    if (!this.canImport()) {
      return;
    }
    input.value = '';
    input.click();
  }

  protected async importRun(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || !this.canImport()) {
      input.value = '';
      return;
    }

    this.jobError.set('');
    this.portabilityBusy.set(true);
    try {
      const document = await readJsonFile(
        file,
        'Plik nie zawiera poprawnego eksportu Delivery Effectiveness Assessment.'
      );
      this.api
        .importRun(document)
        .pipe(
          takeUntilDestroyed(this.destroyRef),
          finalize(() => {
            this.portabilityBusy.set(false);
            input.value = '';
          })
        )
        .subscribe({
          next: (job) => {
            this.stopPolling();
            this.applyJobToForm(job);
            this.job.set(job);
            this.expandedUnits.set(new Set());
            this.localRunName.set(`Import: ${file.name}`);
          },
          error: (error: HttpErrorResponse) => this.jobError.set(this.errorMessage(error))
        });
    } catch (error) {
      this.portabilityBusy.set(false);
      input.value = '';
      this.jobError.set(
        error instanceof Error ? error.message : 'Nie udało się odczytać pliku importu.'
      );
    }
  }

  protected exportRun(): void {
    const job = this.job();
    if (!job || !this.canExport()) {
      return;
    }

    this.jobError.set('');
    this.portabilityBusy.set(true);
    this.historyApi
      .exportRun(job.jobId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.portabilityBusy.set(false))
      )
      .subscribe({
        next: (document) => downloadJsonFile(this.exportFileName(job, document), document),
        error: (error: HttpErrorResponse) => this.jobError.set(this.errorMessage(error))
      });
  }

  protected toggleUnit(unitId: string): void {
    const next = new Set(this.expandedUnits());
    if (next.has(unitId)) {
      next.delete(unitId);
    } else {
      next.add(unitId);
    }
    this.expandedUnits.set(next);
  }

  protected isUnitExpanded(unitId: string): boolean {
    return this.expandedUnits().has(unitId);
  }

  protected statusLabel(status: string | null | undefined): string {
    const labels: Record<string, string> = {
      DISCOVERING: 'Wyszukiwanie w Jira',
      COMPLETED_WITH_WARNINGS: 'Zakończona z ostrzeżeniami',
      COLLECTING_EVIDENCE: 'Zbieranie evidence',
      EXCLUDED: 'Wyłączona',
      NOT_SCORABLE: 'Brak podstaw do oceny'
    };
    return labels[status ?? ''] ?? formatStatus(status);
  }

  protected statusClass(status: string | null | undefined): string {
    if (status === 'COMPLETED_WITH_WARNINGS' || status === 'EXCLUDED' || status === 'NOT_SCORABLE') {
      return 'status-pill--queued';
    }
    return statusClassName(status);
  }

  protected percent(value: number | null | undefined): string {
    const normalized = Number.isFinite(value) ? Number(value) : 0;
    return `${Math.round(normalized * 100)}%`;
  }

  protected overallResultTooltip(job: DeliveryEffectivenessAssessmentJobStateSnapshot): string {
    return `Zbiorczy obraz obserwowalnej złożoności dostarczonej w projekcie ${job.jiraProject} `
      + `od ${job.fromDate} do ${job.toDate}. Powstaje z ${job.aggregate.totalUnits} Delivery Units `
      + 'utworzonych z issue Jira i powiązanych, scalonych Merge Requests; nie jest oceną produktywności zespołu.';
  }

  protected deliveredStoryPointsTooltip(aggregate: DeliveryAssessmentAggregate): string {
    return `Suma DSP z ${aggregate.assessedUnits} ocenionych Delivery Units wynosi ${aggregate.totalDeliveredStoryPoints}. `
      + 'DSP każdej jednostki backend wylicza z ważonej oceny sześciu wymiarów AI i mapuje na skalę '
      + '0, 1, 2, 3, 5, 8 lub 13. Jednostki bez oceny i błędne nie dodają punktów.';
  }

  protected confidenceTooltip(job: DeliveryEffectivenessAssessmentJobStateSnapshot): string {
    const values = job.units
      .map((unit) => unit.assessment?.confidence)
      .filter((value): value is number => Number.isFinite(value));
    if (!values.length) {
      return 'Pewność ocen AI; nie opisuje kompletności analizy. Brak ocen daje poziom LOW. Gdy oceny istnieją, '
        + 'backend liczy ich nieważoną średnią: HIGH od 0,80, MEDIUM od 0,60, LOW poniżej 0,60.';
    }
    const average = values.reduce((sum, value) => sum + value, 0) / values.length;
    const formattedAverage = average.toLocaleString('pl-PL', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2
    });
    return `Pewność ocen AI; nie opisuje kompletności analizy. Nieważona średnia confidence z ${values.length} `
      + `ocenionych jednostek wynosi ${formattedAverage}, dlatego poziom to ${job.aggregate.confidence}. `
      + 'Progi: HIGH od 0,80, MEDIUM od 0,60, LOW poniżej 0,60.';
  }

  protected assessedUnitsTooltip(aggregate: DeliveryAssessmentAggregate): string {
    return `Delivery Units ze statusem COMPLETED, dla których AI zwróciło prawidłową ocenę, a backend `
      + `wyliczył DSP: ${aggregate.assessedUnits} z ${aggregate.totalUnits} wszystkich jednostek.`;
  }

  protected notAssessedUnitsTooltip(aggregate: DeliveryAssessmentAggregate): string {
    const total = aggregate.excludedUnits + aggregate.notScorableUnits;
    return `Suma jednostek EXCLUDED (${aggregate.excludedUnits}) i NOT_SCORABLE (${aggregate.notScorableUnits}) `
      + `wynosi ${total}. EXCLUDED nie reprezentuje dostarczenia, a NOT_SCORABLE nie ma wystarczającego `
      + 'evidence. Żadne z nich nie zwiększa DSP.';
  }

  protected failedUnitsTooltip(aggregate: DeliveryAssessmentAggregate): string {
    return `Delivery Units ze statusem FAILED: ${aggregate.failedUnits}. To przypadki niezakończone z powodu `
      + 'błędu przygotowania evidence, wywołania AI, walidacji lub parsowania odpowiedzi; nie zwiększają DSP.';
  }

  protected tokenCount(value: number): string {
    return value.toLocaleString('pl-PL');
  }

  protected dimensions(dimensions: DeliveryAssessmentDimensions): DimensionRow[] {
    return [
      { label: 'Outcome breadth', value: dimensions.outcomeBreadth },
      { label: 'Domain decisions', value: dimensions.domainDecisionComplexity },
      { label: 'Application flow', value: dimensions.applicationFlowComplexity },
      { label: 'Boundaries and data', value: dimensions.boundaryAndDataComplexity },
      { label: 'Verification state space', value: dimensions.verificationStateSpace },
      { label: 'Compatibility scope', value: dimensions.implementedCompatibilityScope }
    ];
  }

  protected unitCostEstimate(unit: DeliveryAssessmentUnit): AnalysisAiCostEstimate | null {
    return estimateAnalysisAiCost(unit.usage);
  }

  protected unitCostTooltip(unit: DeliveryAssessmentUnit): string {
    const usage = unit.usage;
    if (!usage) {
      return 'AI nie zostało wywołane dla tej jednostki.';
    }
    return [
      `Input: ${usage.inputTokens.toLocaleString('pl-PL')} tokenów`,
      `Cache: ${usage.cacheReadTokens.toLocaleString('pl-PL')} tokenów`,
      `Output: ${usage.outputTokens.toLocaleString('pl-PL')} tokenów`,
      this.aiCallsLabel(usage.apiCallCount)
    ].join(' · ');
  }

  protected unitWarnings(unit: DeliveryAssessmentUnit): string[] {
    return Array.from(new Set(
      [unit.errorMessage]
        .filter((warning): warning is string => Boolean(warning?.trim()))
    ));
  }

  protected unitHasAttention(unit: DeliveryAssessmentUnit): boolean {
    return Boolean(
      unit.visibilityLimits.length
      || unit.assessment?.qualityFlags.length
      || this.unitWarnings(unit).length
    );
  }

  protected unitHasDetails(unit: DeliveryAssessmentUnit): boolean {
    return Boolean(
      unit.assessment
      || unit.mergeRequests.length
      || unit.visibilityLimits.length
      || this.unitWarnings(unit).length
    );
  }

  protected unitAttentionLabel(unit: DeliveryAssessmentUnit): string {
    const total = unit.visibilityLimits.length
      + (unit.assessment?.qualityFlags.length ?? 0)
      + this.unitWarnings(unit).length;
    return `${total} ${total === 1 ? 'informacja wymaga' : 'informacje wymagają'} uwagi`;
  }

  protected trackUnit(_: number, unit: DeliveryAssessmentUnit): string {
    return unit.unitId;
  }

  private aiCallsLabel(count: number): string {
    const absolute = Math.abs(count);
    const lastTwoDigits = absolute % 100;
    const lastDigit = absolute % 10;
    if (absolute === 1) {
      return '1 wywołanie AI';
    }
    if (lastDigit >= 2 && lastDigit <= 4 && (lastTwoDigits < 12 || lastTwoDigits > 14)) {
      return `${count.toLocaleString('pl-PL')} wywołania AI`;
    }
    return `${count.toLocaleString('pl-PL')} wywołań AI`;
  }

  private startRequest(): DeliveryEffectivenessAssessmentJobStartRequest {
    const effort = this.reasoningEffortControl.value.trim();
    return {
      jiraProject: this.jiraProjectControl.value.trim().toUpperCase(),
      fromDate: this.fromDateControl.value,
      toDate: this.toDateControl.value,
      model: this.aiModelControl.value || undefined,
      reasoningEffort: effort || undefined
    };
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
          this.formRevision.update((revision) => revision + 1);
        },
        error: (error: HttpErrorResponse) => this.aiOptionsError.set(this.errorMessage(error))
      });
  }

  private loadGithubAuthStatus(): void {
    this.githubAuth
      .getStatus()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (status) => this.githubAuthStatus.set(status),
        error: (error: HttpErrorResponse) => this.githubAuthError.set(this.errorMessage(error))
      });
  }

  private syncReasoningEffort(): void {
    const efforts = this.reasoningEffortOptions();
    if (!efforts.includes(this.reasoningEffortControl.value)) {
      this.reasoningEffortControl.setValue(
        defaultReasoningEffortForAiModel(this.aiModelCatalog(), this.aiModelControl.value),
        { emitEvent: false }
      );
    }
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
    this.stopPolling();
    this.submitting.set(true);
    this.historyApi
      .getRun(analysisId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.submitting.set(false))
      )
      .subscribe({
        next: (detail) => this.applyLocalRun(detail),
        error: (error: HttpErrorResponse) => this.jobError.set(this.errorMessage(error))
      });
  }

  private applyLocalRun(detail: LocalAnalysisRunDetailResponse): void {
    if (detail.feature !== 'delivery-effectiveness-assessment') {
      this.jobError.set(`Lokalny run ${detail.analysisId} nie jest Delivery Effectiveness Assessment.`);
      return;
    }
    const job = jobFromEnvelope(detail.exportEnvelope);
    if (!job) {
      this.jobError.set('Lokalny run ma nieobsługiwany albo uszkodzony format.');
      return;
    }
    this.applyJobToForm(job);
    this.job.set(job);
    this.jobError.set('');
    this.localRunName.set(detail.name || detail.analysisId);
    if (!this.isTerminal(job.status)) {
      this.startPolling(job.jobId);
    }
  }

  private applyJobToForm(job: DeliveryEffectivenessAssessmentJobStateSnapshot): void {
    this.jiraProjectControl.setValue(job.jiraProject ?? '', { emitEvent: false });
    this.fromDateControl.setValue(job.fromDate ?? '', { emitEvent: false });
    this.toDateControl.setValue(job.toDate ?? '', { emitEvent: false });
    this.aiModelControl.setValue(job.aiModel ?? '', { emitEvent: false });
    this.reasoningEffortControl.setValue(job.reasoningEffort ?? '', { emitEvent: false });
    this.formRevision.update((revision) => revision + 1);
  }

  private isTerminal(status: string | null | undefined): boolean {
    return ['COMPLETED', 'COMPLETED_WITH_WARNINGS', 'FAILED'].includes(status ?? '');
  }

  private errorMessage(error: HttpErrorResponse): string {
    const response = error.error as Partial<ApiErrorResponse> | null;
    return response?.message || error.message || 'Nie udało się wykonać operacji.';
  }

  private applyGithubAuthError(error: HttpErrorResponse): void {
    const response = error.error as Partial<ApiErrorResponse> | null;
    if (!['GITHUB_COPILOT_AUTH_REQUIRED', 'GITHUB_COPILOT_REAUTH_REQUIRED'].includes(response?.code ?? '')) {
      return;
    }
    this.githubAuthStatus.set({
      mode: 'GITHUB_APP',
      required: true,
      connected: false,
      reauthRequired: response?.code === 'GITHUB_COPILOT_REAUTH_REQUIRED',
      authStartUrl: null
    });
  }

  private exportFileName(
    job: DeliveryEffectivenessAssessmentJobStateSnapshot,
    document: unknown
  ): string {
    const envelope = document as Partial<DeliveryEffectivenessAssessmentExportEnvelope> | null;
    const exportedAt = envelope?.exportedAt
      || job.completedAt
      || job.updatedAt
      || new Date().toISOString();
    return [
      'delivery-effectiveness-assessment',
      sanitizeFileNamePart(job.jiraProject),
      job.fromDate,
      job.toDate,
      formatFileTimestamp(exportedAt)
    ].join('-') + '.json';
  }
}

function defaultDate(dayOffset: number): string {
  const date = new Date();
  date.setHours(12, 0, 0, 0);
  date.setDate(date.getDate() + dayOffset);
  return date.toISOString().slice(0, 10);
}

function jobFromEnvelope(value: unknown): DeliveryEffectivenessAssessmentJobStateSnapshot | null {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return null;
  }
  const envelope = value as Partial<DeliveryEffectivenessAssessmentExportEnvelope>;
  if (
    envelope.schema !== 'tdw.delivery-effectiveness-assessment-export'
    || envelope.version !== 2
    || envelope.payload?.type !== 'delivery-effectiveness-assessment'
    || envelope.payload.resultContract !== 'delivery-effectiveness-assessment-v2'
    || !envelope.payload.job?.jobId
  ) {
    return null;
  }
  return envelope.payload.job;
}
