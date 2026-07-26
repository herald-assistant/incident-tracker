import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, Subscription, switchMap, timer } from 'rxjs';

import {
  ChangeVerificationJobMode,
  ChangeVerificationJobStartRequest,
  ChangeVerificationJobStateSnapshot,
  ChangeVerificationSmokePack,
  ChangeVerificationSmokeTestExecution
} from '../../models/change-verification.models';
import { ChangeVerificationApiService } from '../../services/change-verification-api.service';
import {
  AnalysisReport,
  AnalysisReportMeta,
  AnalysisReportReference,
  AnalysisReportSection,
  ApiErrorResponse
} from '../../../../core/models/analysis.models';
import { AnalysisFeatureAsideComponent } from '../../../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisStepsPanelComponent } from '../../../../components/analysis-steps-panel/analysis-steps-panel';
import { AnalysisReportMetaComponent } from '../../../../components/analysis-report-meta/analysis-report-meta';
import { AnalysisReportSectionContentComponent } from '../../../../components/analysis-report-section-content/analysis-report-section-content';
import { formatStatus, statusClassName } from '../../../../core/utils/analysis-display.utils';
import { copyTextToClipboard } from '../../../../core/utils/clipboard.utils';
import { downloadJsonFile, readJsonFile, sanitizeFileNamePart } from '../../../../core/utils/json-file.utils';
import {
  buildChangeVerificationExportEnvelope,
  buildChangeVerificationExportFileName,
  ChangeVerificationExportState,
  parseImportedChangeVerificationResult
} from '../../utils/change-verification-import-export.utils';

interface ChangeVerificationReportDisplay {
  report: AnalysisReport;
  title: string;
  subTitle: string;
  confidence: string;
  sections: AnalysisReportSection[];
  appendix: AnalysisReportMeta;
}

const EMPTY_REPORT_META: AnalysisReportMeta = {
  references: [],
  visibilityLimits: [],
  openQuestions: [],
  gaps: [],
  confidence: '',
  warnings: []
};

@Component({
  selector: 'app-change-verification-page',
  imports: [
    AnalysisFeatureAsideComponent,
    AnalysisStepsPanelComponent,
    AnalysisReportMetaComponent,
    AnalysisReportSectionContentComponent
  ],
  templateUrl: './change-verification-page.html',
  styleUrl: './change-verification-page.scss'
})
export class ChangeVerificationPageComponent implements OnDestroy {
  private readonly changeVerificationApi = inject(ChangeVerificationApiService);
  private readonly destroyRef = inject(DestroyRef);
  private pollingSubscription?: Subscription;

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
  readonly smokeExecutionError = signal('');
  readonly executeCleanup = signal(false);
  readonly isSavingSmokePack = signal(false);
  readonly isExecutingSmokePack = signal(false);
  readonly isSubmitting = signal(false);
  readonly exportState = signal<ChangeVerificationExportState | null>(null);
  readonly resultCopied = signal(false);
  readonly resultCopyError = signal('');
  private resultCopyFeedbackHandle: number | null = null;

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
  readonly smokePack = computed(() => this.job()?.result?.smokePack ?? null);
  readonly smokeTests = computed(() => this.smokePack()?.tests ?? []);
  readonly execution = computed(() => this.job()?.result?.execution ?? null);
  readonly executionResults = computed(() => this.execution()?.testResults ?? []);
  readonly reportDisplay = computed(() =>
    changeVerificationReportDisplay(this.job()?.report ?? null, this.job()?.result ?? null)
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

  protected httpSummary(result: ChangeVerificationSmokeTestExecution): string {
    if (!result.http) {
      return 'HTTP not executed';
    }
    return `${result.http.method} ${result.http.statusCode ?? 'n/a'} · ${result.http.durationMillis}ms`;
  }

  protected hasText(value: string | null | undefined): boolean {
    return hasText(value);
  }

  protected async copyResultMarkdown(): Promise<void> {
    const display = this.reportDisplay();
    if (!display) {
      return;
    }

    const copied = await copyTextToClipboard(buildChangeVerificationReportMarkdown(display.report));
    if (!copied) {
      this.resultCopyError.set('Nie udało się skopiować wyniku weryfikacji do schowka.');
      return;
    }

    this.resultCopyError.set('');
    this.resultCopied.set(true);
    this.clearResultCopyFeedback();
    this.resultCopyFeedbackHandle = window.setTimeout(() => {
      this.resultCopied.set(false);
      this.resultCopyFeedbackHandle = null;
    }, 1600);
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

  ngOnDestroy(): void {
    this.clearResultCopyFeedback();
  }

  private clearResultCopyFeedback(): void {
    if (this.resultCopyFeedbackHandle === null) {
      return;
    }
    window.clearTimeout(this.resultCopyFeedbackHandle);
    this.resultCopyFeedbackHandle = null;
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

  private normalized(value: string | null | undefined): string {
    return String(value || '').trim().toUpperCase();
  }
}

function looksLikeUrl(value: string): boolean {
  return /^https?:\/\//i.test(value);
}

function changeVerificationReportDisplay(
  report: AnalysisReport | null,
  result: ChangeVerificationJobStateSnapshot['result']
): ChangeVerificationReportDisplay | null {
  const effectiveReport = report ?? fallbackReportFromResult(result);
  if (!effectiveReport) {
    return null;
  }

  return {
    report: effectiveReport,
    title: cleanText(effectiveReport.header) || 'Change Verification result',
    subTitle: cleanText(effectiveReport.subHeader),
    confidence: cleanText(effectiveReport.meta?.confidence) || result?.smokePack?.confidence || '',
    sections: sortedSections(effectiveReport.sections),
    appendix: normalizedMeta(effectiveReport.meta)
  };
}

function fallbackReportFromResult(
  result: ChangeVerificationJobStateSnapshot['result']
): AnalysisReport | null {
  if (!result) {
    return null;
  }

  return {
    reportId: `change-verification-${result.issueKey || 'result'}`,
    header: `Change Verification: ${result.issueKey || result.issueUrl || 'change'}`,
    subHeader: `Compliance ${result.compliance.status || 'pending'} | Smoke ${result.smokePack.status || 'pending'} | Execution ${result.execution.status || 'pending'}`,
    markdownSummary: [
      `- Compliance: \`${result.compliance.status || 'pending'}\` with ${result.compliance.findings.length} finding(s).`,
      `- Smoke pack: \`${result.smokePack.status || 'pending'}\` with ${result.smokePack.tests.length} test(s).`,
      `- Execution: \`${result.execution.status || 'pending'}\`.`
    ].join('\n'),
    sections: [
      {
        id: 'CHANGE_COMPLIANCE',
        title: 'Change alignment',
        order: 0,
        markdown: complianceMarkdown(result),
        meta: {
          ...EMPTY_REPORT_META,
          references: result.compliance.findings.flatMap((finding) =>
            finding.references.map((reference) => ({
              type: 'change-source',
              label: finding.id || finding.source,
              target: reference,
              description: finding.summary
            }))
          ),
          visibilityLimits: [...result.compliance.visibilityLimits],
          warnings: result.compliance.findings
            .filter((finding) => ['MEDIUM', 'HIGH', 'BLOCKER'].includes(finding.severity))
            .map((finding) => `[${finding.severity}] ${finding.summary}`)
        }
      },
      {
        id: 'SMOKE_PACK',
        title: 'Smoke pack',
        order: 1,
        markdown: smokePackMarkdown(result),
        meta: {
          ...EMPTY_REPORT_META,
          references: result.smokePack.tests.flatMap((test) =>
            test.sourceRefs.map((reference) => ({
              type: 'smoke-source',
              label: test.id || test.name,
              target: reference,
              description: test.riskCovered || test.purpose
            }))
          ),
          visibilityLimits: [...result.smokePack.visibilityLimits],
          gaps: result.smokePack.tests
            .filter((test) => (test.reviewStatus || '').toUpperCase() !== 'READY')
            .map((test) => `${test.id || test.name} requires review before execution.`),
          confidence: cleanText(result.smokePack.confidence)
        }
      },
      {
        id: 'SMOKE_EXECUTION',
        title: 'Execution readiness',
        order: 2,
        markdown: executionMarkdown(result),
        meta: {
          ...EMPTY_REPORT_META,
          visibilityLimits: [...result.execution.visibilityLimits],
          gaps:
            result.execution.requested && result.execution.testResults.length === 0
              ? ['Execution waits for explicit operator run with base URL.']
              : []
        }
      }
    ],
    meta: {
      ...EMPTY_REPORT_META,
      references: [
        {
          type: 'jira',
          label: result.issueKey || 'Jira issue',
          target: result.issueUrl || result.issueKey,
          description: 'Target issue verified by Change Verification.'
        }
      ],
      visibilityLimits: uniqueText([
        ...result.compliance.visibilityLimits,
        ...result.smokePack.visibilityLimits,
        ...result.execution.visibilityLimits
      ]),
      confidence: cleanText(result.smokePack.confidence)
    }
  };
}

function complianceMarkdown(result: NonNullable<ChangeVerificationJobStateSnapshot['result']>): string {
  const lines = ['### Status', `\`${result.compliance.status || 'pending'}\``, '', '### Findings'];
  if (!result.compliance.findings.length) {
    lines.push('No compliance findings were returned.');
  }
  result.compliance.findings.forEach((finding) => {
    lines.push(`- **[${finding.severity}] ${finding.summary || 'Finding'}**`);
    addIndented(lines, finding.details);
    addIndented(lines, finding.suggestedAction ? `Suggested action: ${finding.suggestedAction}` : '');
  });
  if (result.compliance.suggestedActions.length) {
    lines.push('', '### Recommended actions');
    result.compliance.suggestedActions.forEach((action) => lines.push(`- ${action}`));
  }
  return compactMarkdown(lines);
}

function smokePackMarkdown(result: NonNullable<ChangeVerificationJobStateSnapshot['result']>): string {
  const lines = ['### Status', `\`${result.smokePack.status || 'pending'}\``];
  if (result.smokePack.postmanCollectionName) {
    lines.push('', `Collection: \`${result.smokePack.postmanCollectionName}\``);
  }
  lines.push('', '### Smoke tests');
  if (!result.smokePack.tests.length) {
    lines.push('No smoke tests were generated.');
  }
  result.smokePack.tests.forEach((test) => {
    lines.push(`- **${test.id || 'test'}: ${test.name || 'Smoke test'}**`);
    addIndented(lines, `\`${test.method || 'HTTP'} ${test.path || '/'}\``);
    addIndented(lines, test.riskCovered || test.purpose || '');
    addIndented(lines, `Review status: \`${test.reviewStatus || 'NEEDS_REVIEW'}\``);
  });
  if (result.smokePack.suggestedActions.length) {
    lines.push('', '### Recommended actions');
    result.smokePack.suggestedActions.forEach((action) => lines.push(`- ${action}`));
  }
  return compactMarkdown(lines);
}

function executionMarkdown(result: NonNullable<ChangeVerificationJobStateSnapshot['result']>): string {
  if (!result.execution.requested) {
    return 'Smoke execution was not requested in this run.';
  }
  const lines = ['### Status', `\`${result.execution.status || 'pending'}\``];
  if (!result.execution.testResults.length) {
    lines.push('', 'No smoke tests have been executed yet.');
  }
  result.execution.testResults.forEach((test) => {
    lines.push(`- **${test.testId || test.name || 'test'}**: \`${test.status || 'pending'}\``);
    if (test.http) {
      addIndented(lines, `${test.http.method} ${test.http.url} -> ${test.http.statusCode ?? 'n/a'} in ${test.http.durationMillis}ms`);
    }
  });
  return compactMarkdown(lines);
}

function buildChangeVerificationReportMarkdown(report: AnalysisReport): string {
  const lines = [
    `# ${cleanText(report.header) || 'Change Verification result'}`,
    cleanText(report.subHeader),
    cleanText(report.markdownSummary)
  ].filter(hasText);

  sortedSections(report.sections).forEach((section) => {
    lines.push('', `## ${cleanText(section.title) || cleanText(section.id) || 'Section'}`);
    lines.push(cleanText(section.markdown));
    lines.push(...metaMarkdown(section.meta));
  });

  const appendix = metaMarkdown(report.meta, 'Report metadata');
  if (appendix.length > 0) {
    lines.push('', ...appendix);
  }

  return lines.filter((line, index, all) => line !== '' || all[index - 1] !== '').join('\n');
}

function metaMarkdown(meta: AnalysisReportMeta | null | undefined, title = 'Section metadata'): string[] {
  const parts = [
    bulletGroup('References', (meta?.references ?? []).map(referenceText)),
    bulletGroup('Visibility limits', meta?.visibilityLimits ?? []),
    bulletGroup('Open questions', meta?.openQuestions ?? []),
    bulletGroup('Gaps', meta?.gaps ?? []),
    bulletGroup('Warnings', meta?.warnings ?? [])
  ].flat();
  return parts.length > 0 ? [`### ${title}`, ...parts] : [];
}

function bulletGroup(title: string, values: string[]): string[] {
  const cleaned = values.map(cleanText).filter(hasText);
  return cleaned.length > 0 ? [`#### ${title}`, ...cleaned.map((value) => `- ${value}`)] : [];
}

function referenceText(reference: AnalysisReportReference): string {
  return [reference.label, reference.type, reference.target, reference.description]
    .map(cleanText)
    .filter(hasText)
    .join(' | ');
}

function normalizedMeta(meta: AnalysisReportMeta | null | undefined): AnalysisReportMeta {
  return {
    references: [...(meta?.references ?? [])],
    visibilityLimits: uniqueText(meta?.visibilityLimits ?? []),
    openQuestions: uniqueText(meta?.openQuestions ?? []),
    gaps: uniqueText(meta?.gaps ?? []),
    confidence: cleanText(meta?.confidence),
    warnings: uniqueText(meta?.warnings ?? [])
  };
}

function sortedSections(sections: AnalysisReportSection[] | null | undefined): AnalysisReportSection[] {
  return [...(sections ?? [])].sort((left, right) => {
    const leftOrder = typeof left.order === 'number' ? left.order : Number.MAX_SAFE_INTEGER;
    const rightOrder = typeof right.order === 'number' ? right.order : Number.MAX_SAFE_INTEGER;
    return leftOrder - rightOrder;
  });
}

function addIndented(lines: string[], value: string): void {
  if (hasText(value)) {
    lines.push(`  - ${value.trim()}`);
  }
}

function compactMarkdown(lines: string[]): string {
  return lines.join('\n').replace(/\n{3,}/g, '\n\n').trim();
}

function uniqueText(values: string[]): string[] {
  return Array.from(new Set(values.map(cleanText).filter(hasText)));
}

function cleanText(value: string | null | undefined): string {
  return typeof value === 'string' ? value.trim() : '';
}

function hasText(value: string | null | undefined): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}
