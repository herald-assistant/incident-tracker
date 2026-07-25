import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';

import {
  ChangeVerificationJobMode,
  ChangeVerificationJobStartRequest,
  ChangeVerificationJobStateSnapshot,
  ChangeVerificationSmokePack
} from '../../models/change-verification.models';
import { ChangeVerificationApiService } from '../../services/change-verification-api.service';
import { ApiErrorResponse } from '../../../../core/models/analysis.models';
import { AnalysisStepsPanelComponent } from '../../../../components/analysis-steps-panel/analysis-steps-panel';

@Component({
  selector: 'app-change-verification-page',
  imports: [AnalysisStepsPanelComponent],
  templateUrl: './change-verification-page.html',
  styleUrl: './change-verification-page.scss'
})
export class ChangeVerificationPageComponent {
  private readonly changeVerificationApi = inject(ChangeVerificationApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly issueInput = signal('');
  readonly checkStoryCompliance = signal(true);
  readonly checkInstructionCompliance = signal(true);
  readonly generateSmokePack = signal(true);
  readonly executeSmokePack = signal(false);
  readonly userInstructions = signal('');
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

  readonly canStartJob = computed(
    () => Boolean(this.issueInput().trim()) && !this.isSubmitting()
  );
  readonly selectedModes = computed(() => this.buildModes());
  readonly complianceFindings = computed(() => this.job()?.result?.compliance.findings ?? []);
  readonly smokePack = computed(() => this.job()?.result?.smokePack ?? null);
  readonly execution = computed(() => this.job()?.result?.execution ?? null);

  protected updateIssueInput(value: string): void {
    this.issueInput.set(value);
  }

  protected updateUserInstructions(value: string): void {
    this.userInstructions.set(value);
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

    this.changeVerificationApi
      .startJob(this.jobStartRequest())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false))
      )
      .subscribe({
        next: (job) => this.setJob(job),
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

  protected saveSmokePackDraft(): void {
    const currentJob = this.job();
    if (!currentJob?.jobId) {
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
    if (!currentJob?.jobId) {
      return;
    }

    this.changeVerificationApi
      .getPostmanCollection(currentJob.jobId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (collection) => this.downloadJson(
          collection,
          `${this.smokePack()?.postmanCollectionName || currentJob.jobId}.postman_collection.json`
        ),
        error: (error: HttpErrorResponse) => this.smokePackDraftError.set(this.errorMessage(error))
      });
  }

  protected executeAcceptedSmokePack(): void {
    const currentJob = this.job();
    const baseUrl = this.smokeExecutionBaseUrl().trim();
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
          this.job.set({
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

  private jobStartRequest(): ChangeVerificationJobStartRequest {
    const issue = this.issueInput().trim();
    const userInstructions = this.userInstructions().trim();
    const request: ChangeVerificationJobStartRequest = {
      modes: this.selectedModes(),
      checkStoryCompliance: this.checkStoryCompliance(),
      checkInstructionCompliance: this.checkInstructionCompliance(),
      userInstructions: userInstructions || undefined
    };

    if (looksLikeUrl(issue)) {
      request.issueUrl = issue;
    } else {
      request.issueKey = issue;
    }

    return request;
  }

  private setJob(job: ChangeVerificationJobStateSnapshot): void {
    this.job.set(job);
    this.smokePackDraft.set(
      job.result?.smokePack ? JSON.stringify(job.result.smokePack, null, 2) : ''
    );
    this.smokePackDraftError.set('');
  }

  private parseSmokePackDraft(): ChangeVerificationSmokePack | null {
    try {
      return JSON.parse(this.smokePackDraft()) as ChangeVerificationSmokePack;
    } catch {
      this.smokePackDraftError.set('Smoke pack draft is not valid JSON.');
      return null;
    }
  }

  private downloadJson(value: unknown, fileName: string): void {
    const blob = new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = sanitizeFileName(fileName);
    anchor.click();
    URL.revokeObjectURL(url);
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
}

function looksLikeUrl(value: string): boolean {
  return /^https?:\/\//i.test(value);
}

function sanitizeFileName(value: string): string {
  return value.replace(/[\\/:*?"<>|]+/g, '-');
}
