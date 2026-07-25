import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Subscription, switchMap, timer } from 'rxjs';

import {
  ChangeVerificationFinding,
  ChangeVerificationFindingSeverity,
  ChangeVerificationJobMode,
  ChangeVerificationJobStartRequest,
  ChangeVerificationJobStateSnapshot,
  ChangeVerificationSmokePack,
  ChangeVerificationSmokeTestExecution,
  ChangeVerificationSmokeTest
} from '../../models/change-verification.models';
import { ChangeVerificationApiService } from '../../services/change-verification-api.service';
import { ApiErrorResponse } from '../../../../core/models/analysis.models';
import { AnalysisFeatureAsideComponent } from '../../../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisStepsPanelComponent } from '../../../../components/analysis-steps-panel/analysis-steps-panel';
import { formatStatus, statusClassName } from '../../../../core/utils/analysis-display.utils';
import { downloadJsonFile, readJsonFile, sanitizeFileNamePart } from '../../../../core/utils/json-file.utils';
import {
  buildChangeVerificationExportEnvelope,
  buildChangeVerificationExportFileName,
  ChangeVerificationExportState,
  parseImportedChangeVerificationResult
} from '../../utils/change-verification-import-export.utils';

interface ComplianceFindingGroup {
  severity: ChangeVerificationFindingSeverity;
  findings: ChangeVerificationFinding[];
}

@Component({
  selector: 'app-change-verification-page',
  imports: [AnalysisFeatureAsideComponent, AnalysisStepsPanelComponent],
  templateUrl: './change-verification-page.html',
  styleUrl: './change-verification-page.scss'
})
export class ChangeVerificationPageComponent {
  private readonly changeVerificationApi = inject(ChangeVerificationApiService);
  private readonly destroyRef = inject(DestroyRef);
  private pollingSubscription?: Subscription;

  readonly issueInput = signal('');
  readonly checkStoryCompliance = signal(true);
  readonly checkInstructionCompliance = signal(true);
  readonly generateSmokePack = signal(true);
  readonly executeSmokePack = signal(false);
  readonly userInstructions = signal('');
  readonly analysisEnvironment = signal('');
  readonly analysisDatabaseApplication = signal('');
  readonly job = signal<ChangeVerificationJobStateSnapshot | null>(null);
  readonly jobError = signal('');
  readonly smokePackDraft = signal('');
  readonly smokePackDraftError = signal('');
  readonly smokeExecutionBaseUrl = signal('');
  readonly smokeExecutionEnvironment = signal('');
  readonly smokeExecutionDatabaseApplication = signal('');
  readonly smokeExecutionError = signal('');
  readonly executeCleanup = signal(false);
  readonly isSavingSmokePack = signal(false);
  readonly isExecutingSmokePack = signal(false);
  readonly isSubmitting = signal(false);
  readonly exportState = signal<ChangeVerificationExportState | null>(null);

  readonly canStartJob = computed(
    () => Boolean(this.issueInput().trim()) && !this.isSubmitting()
  );
  readonly isImportedResult = computed(() => this.exportState()?.origin === 'imported');
  readonly canExportResult = computed(() => {
    const exportState = this.exportState();
    return Boolean(exportState?.job.status === 'COMPLETED' && exportState.job.result);
  });
  readonly importExportHint = computed(() => {
    const exportState = this.exportState();
    if (exportState?.origin !== 'imported') {
      return '';
    }
    return `Imported file: ${exportState.fileName}`;
  });
  readonly selectedModes = computed(() => this.buildModes());
  readonly complianceFindings = computed(() => this.job()?.result?.compliance.findings ?? []);
  readonly complianceFindingGroups = computed(() => this.groupFindings(this.complianceFindings()));
  readonly smokePack = computed(() => this.job()?.result?.smokePack ?? null);
  readonly smokeTests = computed(() => this.smokePack()?.tests ?? []);
  readonly execution = computed(() => this.job()?.result?.execution ?? null);
  readonly executionResults = computed(() => this.execution()?.testResults ?? []);
  readonly suggestedActions = computed(() => this.uniqueValues([
    ...(this.job()?.result?.compliance.suggestedActions ?? []),
    ...(this.smokePack()?.suggestedActions ?? [])
  ]));
  readonly visibilityLimits = computed(() => this.uniqueValues([
    ...(this.job()?.result?.compliance.visibilityLimits ?? []),
    ...(this.smokePack()?.visibilityLimits ?? []),
    ...(this.execution()?.visibilityLimits ?? [])
  ]));
  readonly readySmokeTestCount = computed(() =>
    this.smokeTests().filter((test) => this.normalized(test.reviewStatus) === 'READY').length
  );
  readonly reviewSmokeTestCount = computed(() =>
    this.smokeTests().filter((test) => this.normalized(test.reviewStatus) !== 'READY').length
  );
  readonly passedExecutionCount = computed(() =>
    this.executionResults().filter((result) => this.normalized(result.status) === 'PASSED').length
  );
  readonly failedExecutionCount = computed(() =>
    this.executionResults().filter((result) => this.normalized(result.status) === 'FAILED').length
  );
  readonly workflowIsRunning = computed(() => {
    const currentJob = this.job();
    return Boolean(currentJob && !this.isTerminalStatus(currentJob.status));
  });
  readonly aiWorkflowIsRunning = computed(() => {
    const currentJob = this.job();
    return Boolean(
      currentJob?.steps.some((step) =>
        step.phase === 'AI' && (step.status === 'RUNNING' || step.status === 'IN_PROGRESS')
      )
    );
  });
  readonly aiWorkflowItemCount = computed(() => {
    const currentJob = this.job();
    if (!currentJob) {
      return 0;
    }

    return (
      currentJob.aiActivityEvents.length +
      currentJob.toolEvidenceSections.reduce((count, section) => count + section.items.length, 0)
    );
  });

  protected updateIssueInput(value: string): void {
    this.issueInput.set(value);
  }

  protected updateUserInstructions(value: string): void {
    this.userInstructions.set(value);
  }

  protected updateAnalysisEnvironment(value: string): void {
    this.analysisEnvironment.set(value);
  }

  protected updateAnalysisDatabaseApplication(value: string): void {
    this.analysisDatabaseApplication.set(value);
  }

  protected toggleStoryCompliance(checked: boolean): void {
    this.checkStoryCompliance.set(checked);
  }

  protected toggleInstructionCompliance(checked: boolean): void {
    this.checkInstructionCompliance.set(checked);
  }

  protected toggleSmokePack(checked: boolean): void {
    this.generateSmokePack.set(checked);
    if (!checked) {
      this.executeSmokePack.set(false);
    }
  }

  protected toggleExecution(checked: boolean): void {
    this.executeSmokePack.set(checked);
    if (checked) {
      this.generateSmokePack.set(true);
    }
  }

  protected startJob(): void {
    if (!this.canStartJob()) {
      return;
    }

    this.jobError.set('');
    this.isSubmitting.set(true);
    this.exportState.set(null);

    this.changeVerificationApi
      .startJob(this.jobStartRequest())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false))
      )
      .subscribe({
        next: (job) => {
          this.setJob(job);
          this.startPolling(job.jobId);
        },
        error: (error: HttpErrorResponse) => this.jobError.set(this.errorMessage(error))
      });
  }

  protected updateSmokePackDraft(value: string): void {
    this.smokePackDraft.set(value);
    this.smokePackDraftError.set('');
  }

  protected updateSmokeExecutionBaseUrl(value: string): void {
    this.smokeExecutionBaseUrl.set(value);
    this.smokeExecutionError.set('');
  }

  protected updateSmokeExecutionEnvironment(value: string): void {
    this.smokeExecutionEnvironment.set(value);
    this.smokeExecutionError.set('');
  }

  protected updateSmokeExecutionDatabaseApplication(value: string): void {
    this.smokeExecutionDatabaseApplication.set(value);
    this.smokeExecutionError.set('');
  }

  protected toggleCleanupExecution(checked: boolean): void {
    this.executeCleanup.set(checked);
  }

  protected statusLabel(status: string | null | undefined): string {
    return formatStatus(status);
  }

  protected statusPillClass(status: string | null | undefined): string {
    return `status-pill ${statusClassName(status)}`;
  }

  protected resultStatusPillClass(status: string | null | undefined): string {
    const normalized = this.normalized(status);
    if (['PASSED', 'READY', 'COMPLETED', 'SKIPPED'].includes(normalized)) {
      return 'status-pill status-pill--done';
    }
    if (['FAILED', 'BLOCKED', 'BLOCKER', 'NOT_READY'].includes(normalized)) {
      return 'status-pill status-pill--error';
    }
    if (['RUNNING', 'IN_PROGRESS', 'ANALYZING', 'COLLECTING_CONTEXT'].includes(normalized)) {
      return 'status-pill status-pill--running';
    }
    return 'status-pill status-pill--queued';
  }

  protected severityClass(severity: string | null | undefined): string {
    return `change-verification-severity change-verification-severity--${this.normalized(severity).toLowerCase() || 'info'}`;
  }

  protected reviewStatusClass(status: string | null | undefined): string {
    return `change-verification-review-status change-verification-review-status--${this.normalized(status).toLowerCase() || 'pending'}`;
  }

  protected httpSummary(result: ChangeVerificationSmokeTestExecution): string {
    if (!result.http) {
      return 'HTTP not executed';
    }
    return `${result.http.method} ${result.http.statusCode ?? 'n/a'} · ${result.http.durationMillis}ms`;
  }

  protected smokeAssertionCount(test: ChangeVerificationSmokeTest): number {
    return test.responseAssertions.length + test.dbAssertionSpecs.length + test.dbAssertions.length;
  }

  protected saveSmokePackDraft(): void {
    const currentJob = this.job();
    if (!currentJob?.jobId || this.isImportedResult()) {
      return;
    }

    const smokePack = this.parseSmokePackDraft();
    if (!smokePack) {
      return;
    }

    this.isSavingSmokePack.set(true);
    this.changeVerificationApi
      .updateSmokePack(currentJob.jobId, smokePack)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSavingSmokePack.set(false))
      )
      .subscribe({
        next: (updatedSmokePack) => {
          const job = this.job();
          if (!job?.result) {
            return;
          }
          this.setJob({
            ...job,
            result: {
              ...job.result,
              smokePack: updatedSmokePack
            }
          });
        },
        error: (error: HttpErrorResponse) => this.smokePackDraftError.set(this.errorMessage(error))
      });
  }

  protected downloadPostmanCollection(): void {
    const currentJob = this.job();
    if (!currentJob?.jobId || this.isImportedResult()) {
      return;
    }

    this.changeVerificationApi
      .getPostmanCollection(currentJob.jobId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (collection) =>
          downloadJsonFile(
            `${sanitizeFileNamePart(this.smokePack()?.postmanCollectionName || currentJob.jobId)}.postman_collection.json`,
            collection
          ),
        error: (error: HttpErrorResponse) => this.smokePackDraftError.set(this.errorMessage(error))
      });
  }

  protected executeAcceptedSmokePack(): void {
    const currentJob = this.job();
    const baseUrl = this.smokeExecutionBaseUrl().trim();
    if (this.isImportedResult()) {
      return;
    }
    if (!currentJob?.jobId || !baseUrl) {
      this.smokeExecutionError.set('Base URL is required before execution.');
      return;
    }

    this.isExecutingSmokePack.set(true);
    this.smokeExecutionError.set('');
    this.changeVerificationApi
      .executeSmokePack(currentJob.jobId, {
        baseUrl,
        environment: this.smokeExecutionEnvironment().trim() || undefined,
        databaseApplication: this.smokeExecutionDatabaseApplication().trim() || undefined,
        executeCleanup: this.executeCleanup()
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isExecutingSmokePack.set(false))
      )
      .subscribe({
        next: (execution) => {
          const job = this.job();
          if (!job?.result) {
            return;
          }
          this.setJob({
            ...job,
            result: {
              ...job.result,
              execution
            }
          });
        },
        error: (error: HttpErrorResponse) => this.smokeExecutionError.set(this.errorMessage(error))
      });
  }

  protected triggerImport(fileInput: HTMLInputElement): void {
    this.jobError.set('');
    fileInput.value = '';
    fileInput.click();
  }

  protected async importChangeVerificationResult(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    try {
      const payload = await readJsonFile(file, 'Importowany plik nie jest poprawnym JSON.');
      const imported = parseImportedChangeVerificationResult(payload);

      this.pollingSubscription?.unsubscribe();
      this.pollingSubscription = undefined;
      this.isSubmitting.set(false);
      this.isSavingSmokePack.set(false);
      this.isExecutingSmokePack.set(false);
      this.jobError.set('');
      this.smokeExecutionError.set('');

      this.issueInput.set(imported.job.issueUrl || imported.job.issueKey);
      this.checkStoryCompliance.set(imported.job.checkStoryCompliance);
      this.checkInstructionCompliance.set(imported.job.checkInstructionCompliance);
      this.generateSmokePack.set(imported.job.modes.includes('GENERATE_SMOKE_PACK'));
      this.executeSmokePack.set(imported.job.modes.includes('EXECUTE_SMOKE_PACK'));
      this.setJob(imported.job, {
        origin: 'imported',
        exportedAt: imported.exportedAt,
        fileName: file.name
      });
    } catch (error) {
      this.jobError.set(error instanceof Error ? error.message : 'Nie udało się zaimportować wyniku.');
    } finally {
      input.value = '';
    }
  }

  protected exportChangeVerificationResult(): void {
    const exportState = this.exportState();
    if (!exportState) {
      return;
    }

    try {
      const exportedAt = new Date().toISOString();
      const payload = buildChangeVerificationExportEnvelope(exportState.job, exportedAt);
      downloadJsonFile(buildChangeVerificationExportFileName(exportState.job, exportedAt), payload);
    } catch (error) {
      this.jobError.set(error instanceof Error ? error.message : 'Nie udało się wyeksportować wyniku.');
    }
  }

  private jobStartRequest(): ChangeVerificationJobStartRequest {
    const issue = this.issueInput().trim();
    const userInstructions = this.userInstructions().trim();
    const request: ChangeVerificationJobStartRequest = {
      modes: this.selectedModes(),
      checkStoryCompliance: this.checkStoryCompliance(),
      checkInstructionCompliance: this.checkInstructionCompliance(),
      userInstructions: userInstructions || undefined,
      environment: this.analysisEnvironment().trim() || undefined,
      databaseApplication: this.analysisDatabaseApplication().trim() || undefined
    };

    if (looksLikeUrl(issue)) {
      request.issueUrl = issue;
    } else {
      request.issueKey = issue;
    }

    return request;
  }

  private setJob(
    job: ChangeVerificationJobStateSnapshot,
    exportState?: Omit<ChangeVerificationExportState, 'job'>
  ): void {
    this.job.set(job);
    const nextDraft = job.result?.smokePack ? JSON.stringify(job.result.smokePack, null, 2) : '';
    if (!this.smokePackDraft() || this.smokePackDraft() === nextDraft || !job.result?.smokePack) {
      this.smokePackDraft.set(nextDraft);
    }
    this.smokePackDraftError.set('');

    if (exportState) {
      this.exportState.set({ ...exportState, job });
      return;
    }

    const currentExportState = this.exportState();
    if (currentExportState?.origin !== 'imported') {
      this.exportState.set({
        origin: 'live',
        exportedAt: currentExportState?.exportedAt ?? '',
        fileName: currentExportState?.fileName ?? '',
        job
      });
    }
  }

  private startPolling(jobId: string): void {
    this.pollingSubscription?.unsubscribe();
    if (this.isTerminalStatus(this.job()?.status)) {
      return;
    }

    this.pollingSubscription = timer(1000, 1500)
      .pipe(
        switchMap(() => this.changeVerificationApi.getJob(jobId)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({
        next: (job) => {
          this.setJob(job);
          if (this.isTerminalStatus(job.status)) {
            this.pollingSubscription?.unsubscribe();
            this.pollingSubscription = undefined;
          }
        },
        error: (error: HttpErrorResponse) => {
          this.jobError.set(this.errorMessage(error));
          this.pollingSubscription?.unsubscribe();
          this.pollingSubscription = undefined;
        }
      });
  }

  private isTerminalStatus(status: string | null | undefined): boolean {
    return status === 'COMPLETED' || status === 'FAILED';
  }

  private parseSmokePackDraft(): ChangeVerificationSmokePack | null {
    try {
      return JSON.parse(this.smokePackDraft()) as ChangeVerificationSmokePack;
    } catch {
      this.smokePackDraftError.set('Smoke pack draft is not valid JSON.');
      return null;
    }
  }

  private buildModes(): ChangeVerificationJobMode[] {
    const modes: ChangeVerificationJobMode[] = [];
    if (this.checkStoryCompliance() || this.checkInstructionCompliance()) {
      modes.push('CHECK_COMPLIANCE');
    }
    if (this.generateSmokePack()) {
      modes.push('GENERATE_SMOKE_PACK');
    }
    if (this.executeSmokePack()) {
      modes.push('EXECUTE_SMOKE_PACK');
    }
    return modes.length > 0 ? modes : ['CHECK_COMPLIANCE'];
  }

  private errorMessage(error: HttpErrorResponse): string {
    const body = error.error as Partial<ApiErrorResponse> | null;
    if (body?.message) {
      return body.message;
    }
    if (error.message) {
      return error.message;
    }
    return 'Nie udalo sie uruchomic Change Verification.';
  }

  private groupFindings(findings: ChangeVerificationFinding[]): ComplianceFindingGroup[] {
    const severityOrder: ChangeVerificationFindingSeverity[] = ['BLOCKER', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
    return severityOrder
      .map((severity) => ({
        severity,
        findings: findings.filter((finding) => finding.severity === severity)
      }))
      .filter((group) => group.findings.length > 0);
  }

  private uniqueValues(values: string[]): string[] {
    return Array.from(new Set(values.filter((value) => Boolean(value?.trim()))));
  }

  private normalized(value: string | null | undefined): string {
    return String(value || '').trim().toUpperCase();
  }
}

function looksLikeUrl(value: string): boolean {
  return /^https?:\/\//i.test(value);
}
