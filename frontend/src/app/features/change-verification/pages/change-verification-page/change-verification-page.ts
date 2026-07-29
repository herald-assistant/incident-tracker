import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnDestroy, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { finalize, Subscription, switchMap, timer } from 'rxjs';

import {
  ChangeVerificationCompliance,
  ChangeVerificationJobStartRequest,
  ChangeVerificationJobStateSnapshot,
  ChangeVerificationVerificationCheck
} from '../../models/change-verification.models';
import { ChangeVerificationApiService } from '../../services/change-verification-api.service';
import {
  AnalysisAiModelOptionsResponse,
  AnalysisReport,
  AnalysisReportMeta,
  AnalysisReportReference,
  AnalysisReportSection,
  ApiErrorResponse,
  LocalAnalysisRunDetailResponse
} from '../../../../core/models/analysis.models';
import { AnalysisApiService } from '../../../../core/services/analysis-api.service';
import { AnalysisRunHistoryApiService } from '../../../../core/services/analysis-run-history-api.service';
import { AnalysisFeatureAsideComponent } from '../../../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisStepsPanelComponent } from '../../../../components/analysis-steps-panel/analysis-steps-panel';
import { AnalysisReportMetaComponent } from '../../../../components/analysis-report-meta/analysis-report-meta';
import { AnalysisReportSectionContentComponent } from '../../../../components/analysis-report-section-content/analysis-report-section-content';
import { AnalysisResultHeaderComponent } from '../../../../components/analysis-result-header/analysis-result-header';
import { AnalysisResultTabsComponent } from '../../../../components/analysis-result-tabs/analysis-result-tabs';
import { ChangeVerificationComplianceResultComponent } from '../../components/change-verification-compliance-result/change-verification-compliance-result';
import { formatStatus, statusClassName } from '../../../../core/utils/analysis-display.utils';
import { copyTextToClipboard } from '../../../../core/utils/clipboard.utils';
import { downloadJsonFile, readJsonFile } from '../../../../core/utils/json-file.utils';
import {
  buildChangeVerificationExportEnvelope,
  buildChangeVerificationExportFileName,
  ChangeVerificationExportState,
  parseImportedChangeVerificationResult
} from '../../utils/change-verification-import-export.utils';
import {
  defaultReasoningEffortForAiModel,
  EMPTY_ANALYSIS_AI_MODEL_OPTIONS,
  listedDefaultAiModel,
  normalizeAnalysisAiModelOptions,
  reasoningEffortsForAiModel
} from '../../../../core/utils/analysis-ai-model-options.utils';

type SelectOption = {
  value: string;
  label: string;
  disabled?: boolean;
};

interface ChangeVerificationReportDisplay {
  report: AnalysisReport;
  title: string;
  confidence: string;
  sections: ChangeVerificationReportSectionDisplay[];
  appendix: AnalysisReportMeta;
}

interface ChangeVerificationReportSectionDisplay {
  id: string;
  title: string;
  tabLabel: string;
  markdown: string;
  emptyText: string;
  meta: AnalysisReportMeta;
  complianceChecks: ChangeVerificationVerificationCheck[];
  isComplianceSection: boolean;
}

@Component({
  selector: 'app-change-verification-page',
  imports: [
    AnalysisFeatureAsideComponent,
    AnalysisStepsPanelComponent,
    AnalysisReportMetaComponent,
    AnalysisReportSectionContentComponent,
    AnalysisResultHeaderComponent,
    AnalysisResultTabsComponent,
    ChangeVerificationComplianceResultComponent,
    ReactiveFormsModule
  ],
  templateUrl: './change-verification-page.html',
  styleUrl: './change-verification-page.scss'
})
export class ChangeVerificationPageComponent implements OnDestroy {
  private readonly changeVerificationApi = inject(ChangeVerificationApiService);
  private readonly analysisApi = inject(AnalysisApiService);
  private readonly historyApi = inject(AnalysisRunHistoryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private pollingSubscription?: Subscription;

  readonly aiModelControl = new FormControl('', { nonNullable: true });
  readonly reasoningEffortControl = new FormControl('', { nonNullable: true });
  readonly issueInput = signal('');
  readonly checkStoryCompliance = signal(true);
  readonly checkInstructionCompliance = signal(true);
  readonly selectedAiModel = signal('');
  readonly selectedReasoningEffort = signal('');
  readonly userInstructions = signal('');
  readonly job = signal<ChangeVerificationJobStateSnapshot | null>(null);
  readonly jobError = signal('');
  readonly isSubmitting = signal(false);
  readonly isAiModelOptionsLoading = signal(false);
  readonly aiModelOptionsError = signal('');
  readonly aiModelCatalog = signal<AnalysisAiModelOptionsResponse>(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
  readonly exportState = signal<ChangeVerificationExportState | null>(null);
  readonly activeResultTab = signal('STORY_COMPLIANCE');
  readonly resultCopied = signal(false);
  readonly resultCopyError = signal('');
  private resultCopyFeedbackHandle: number | null = null;

  readonly canStartJob = computed(
    () => Boolean(this.issueInput().trim())
      && (this.checkStoryCompliance() || this.checkInstructionCompliance())
      && !this.isSubmitting()
  );
  readonly canExportResult = computed(() => {
    const exportState = this.exportState();
    return Boolean(exportState?.job.status === 'COMPLETED' && exportState.job.result && exportState.job.report);
  });
  readonly importExportHint = computed(() => {
    const exportState = this.exportState();
    if (exportState?.origin === 'imported') {
      return `Imported file: ${exportState.fileName}`;
    }
    if (exportState?.origin === 'local') {
      return `Local run: ${exportState.localRunName || exportState.localRunId || exportState.job.jobId}`;
    }
    return '';
  });
  readonly aiModelOptions = computed<SelectOption[]>(() => {
    if (this.isAiModelOptionsLoading()) {
      return [
        {
          value: this.selectedAiModel(),
          label: 'Ładowanie modeli AI...',
          disabled: true
        }
      ];
    }

    return this.aiModelCatalog().models.map((model) => ({
      value: model.id,
      label: this.modelLabel(model.id, model.name)
    }));
  });
  readonly availableReasoningEfforts = computed(() =>
    this.reasoningEffortsForModel(this.selectedAiModel())
  );
  readonly reasoningEffortOptions = computed<SelectOption[]>(() => {
    if (this.isAiModelOptionsLoading()) {
      return [
        {
          value: this.selectedReasoningEffort(),
          label: 'Ładowanie reasoning effort...',
          disabled: true
        }
      ];
    }

    return this.availableReasoningEfforts().map((effort) => ({
      value: effort,
      label: this.reasoningEffortLabel(effort)
    }));
  });
  readonly reportDisplay = computed(() =>
    changeVerificationReportDisplay(
      this.job()?.report ?? null,
      this.job()?.result?.compliance ?? null
    )
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

  constructor() {
    this.aiModelControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => {
        this.selectedAiModel.set((value || '').trim());
        this.syncReasoningEffortSelection();
      });
    this.reasoningEffortControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.selectedReasoningEffort.set((value || '').trim()));
    this.loadAiModelOptions();
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const localRunId = params.get('localRunId')?.trim() ?? '';
        if (localRunId) {
          this.loadLocalChangeVerificationRun(localRunId);
        }
      });
  }

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

  protected selectAiModel(value: string): void {
    this.selectedAiModel.set((value || '').trim());
    this.syncReasoningEffortSelection();
  }

  protected selectReasoningEffort(value: string): void {
    this.selectedReasoningEffort.set((value || '').trim());
  }

  protected selectResultTab(tabId: string): void {
    this.activeResultTab.set(cleanText(tabId));
  }

  protected activeResultTabId(sections: ChangeVerificationReportSectionDisplay[]): string {
    const active = this.activeResultTab();
    return sections.some((section) => section.id === active) ? active : sections[0]?.id ?? '';
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

  protected statusLabel(status: string | null | undefined): string {
    return formatStatus(status);
  }

  protected statusPillClass(status: string | null | undefined): string {
    return `status-pill ${statusClassName(status)}`;
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
      this.jobError.set('');

      this.issueInput.set(imported.job.issueUrl || imported.job.issueKey);
      this.checkStoryCompliance.set(imported.job.checkStoryCompliance);
      this.checkInstructionCompliance.set(imported.job.checkInstructionCompliance);
      this.selectedAiModel.set(imported.job.aiModel || '');
      this.selectedReasoningEffort.set(imported.job.reasoningEffort || '');
      this.syncAiModelSelection();
      this.syncReasoningEffortSelection();
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
    this.pollingSubscription?.unsubscribe();
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
    const selectedAiModel = this.selectedAiModel().trim()
      || listedDefaultAiModel(this.aiModelCatalog());
    const selectedReasoningEffort = this.selectedReasoningEffort().trim()
      || defaultReasoningEffortForAiModel(this.aiModelCatalog(), selectedAiModel);
    const request: ChangeVerificationJobStartRequest = {
      checkStoryCompliance: this.checkStoryCompliance(),
      checkInstructionCompliance: this.checkInstructionCompliance(),
      userInstructions: userInstructions || undefined,
      model: selectedAiModel || undefined,
      reasoningEffort: selectedReasoningEffort || undefined
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

  private loadLocalChangeVerificationRun(analysisId: string): void {
    this.pollingSubscription?.unsubscribe();
    this.pollingSubscription = undefined;
    this.job.set(null);
    this.jobError.set('');
    this.isSubmitting.set(true);

    this.historyApi
      .getRun(analysisId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false))
      )
      .subscribe({
        next: (detail) => this.applyLocalChangeVerificationRun(detail),
        error: (error: HttpErrorResponse) => {
          this.jobError.set(this.errorMessage(error));
        }
      });
  }

  private applyLocalChangeVerificationRun(detail: LocalAnalysisRunDetailResponse): void {
    try {
      if (detail.feature !== 'change-verification') {
        throw new Error(`Lokalny run ${detail.analysisId} nie jest runem Change Verification.`);
      }

      const imported = parseImportedChangeVerificationResult(detail.exportEnvelope, {
        requireCompleted: false
      });
      this.issueInput.set(imported.job.issueUrl || imported.job.issueKey);
      this.checkStoryCompliance.set(imported.job.checkStoryCompliance);
      this.checkInstructionCompliance.set(imported.job.checkInstructionCompliance);
      this.selectedAiModel.set(imported.job.aiModel || '');
      this.selectedReasoningEffort.set(imported.job.reasoningEffort || '');
      this.syncAiModelSelection();
      this.syncReasoningEffortSelection();
      this.setJob(imported.job, {
        origin: 'local',
        exportedAt: imported.exportedAt,
        fileName: '',
        localRunId: detail.analysisId,
        localRunName: detail.name
      });
      if (!this.isTerminalStatus(imported.job.status)) {
        this.startPolling(imported.job.jobId || detail.analysisId);
      }
    } catch (error) {
      this.jobError.set(
        error instanceof Error
          ? error.message
          : 'Nie udało się odtworzyć lokalnego runu Change Verification.'
      );
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

  private loadAiModelOptions(): void {
    this.isAiModelOptionsLoading.set(true);
    this.aiModelOptionsError.set('');

    this.analysisApi
      .getAiModelOptions()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isAiModelOptionsLoading.set(false))
      )
      .subscribe({
        next: (options) => {
          this.aiModelCatalog.set(normalizeAnalysisAiModelOptions(options));
          this.syncAiModelSelection();
          this.syncReasoningEffortSelection();
        },
        error: (error: HttpErrorResponse) => {
          this.aiModelCatalog.set(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
          this.aiModelOptionsError.set(this.errorMessage(error));
          this.syncAiModelSelection();
          this.syncReasoningEffortSelection();
        }
      });
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

  private syncAiModelSelection(): void {
    const selectedModel = this.selectedAiModel().trim();
    if (selectedModel) {
      if (this.aiModelControl.value !== selectedModel) {
        this.aiModelControl.setValue(selectedModel, { emitEvent: false });
      }
      return;
    }

    const currentModel = this.aiModelControl.value.trim();
    if (currentModel) {
      this.selectedAiModel.set(currentModel);
      return;
    }

    const defaultModel = listedDefaultAiModel(this.aiModelCatalog());
    this.aiModelControl.setValue(defaultModel, { emitEvent: false });
    this.selectedAiModel.set(defaultModel);
  }

  private syncReasoningEffortSelection(): void {
    const availableEfforts = reasoningEffortsForAiModel(
      this.aiModelCatalog(),
      this.selectedAiModel()
    );
    const selectedReasoningEffort = this.selectedReasoningEffort().trim();
    if (!availableEfforts.length) {
      this.reasoningEffortControl.setValue('', { emitEvent: false });
      this.reasoningEffortControl.disable({ emitEvent: false });
      this.selectedReasoningEffort.set('');
      return;
    }
    this.reasoningEffortControl.enable({ emitEvent: false });
    if (selectedReasoningEffort && availableEfforts.includes(selectedReasoningEffort)) {
      if (this.reasoningEffortControl.value !== selectedReasoningEffort) {
        this.reasoningEffortControl.setValue(selectedReasoningEffort, { emitEvent: false });
      }
      return;
    }

    const defaultReasoningEffort = defaultReasoningEffortForAiModel(
      this.aiModelCatalog(),
      this.selectedAiModel()
    );
    this.reasoningEffortControl.setValue(defaultReasoningEffort, { emitEvent: false });
    this.selectedReasoningEffort.set(defaultReasoningEffort);
  }

  private reasoningEffortsForModel(modelId: string): string[] {
    return reasoningEffortsForAiModel(this.aiModelCatalog(), modelId);
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
}

function looksLikeUrl(value: string): boolean {
  return /^https?:\/\//i.test(value);
}

function changeVerificationReportDisplay(
  report: AnalysisReport | null,
  compliance: ChangeVerificationCompliance | null
): ChangeVerificationReportDisplay | null {
  if (!report) {
    return null;
  }

  return {
    report,
    title: cleanText(report.header) || 'Change Verification result',
    confidence: cleanText(report.meta?.confidence),
    sections: changeVerificationReportSections(report.sections, compliance),
    appendix: normalizedMeta(report.meta)
  };
}

function changeVerificationReportSections(
  sections: AnalysisReportSection[] | null | undefined,
  compliance: ChangeVerificationCompliance | null
): ChangeVerificationReportSectionDisplay[] {
  return sortedSections(sections).map((section) => {
    const id = cleanText(section.id) || cleanText(section.title) || 'SECTION';
    const normalizedId = id.toUpperCase();
    return {
      id,
      title: cleanText(section.title) || id,
      tabLabel: changeVerificationTabLabel(id, section.title),
      markdown: cleanText(section.markdown),
      emptyText: `No confirmed details for ${changeVerificationTabLabel(id, section.title)}.`,
      meta: normalizedMeta(section.meta),
      complianceChecks: complianceChecksForSection(compliance, normalizedId),
      isComplianceSection: ['STORY_COMPLIANCE', 'INSTRUCTION_COMPLIANCE'].includes(normalizedId)
    };
  });
}

function complianceChecksForSection(
  compliance: ChangeVerificationCompliance | null,
  sectionId: string
): ChangeVerificationVerificationCheck[] {
  return [...(compliance?.verificationChecks ?? [])].filter((check) => {
    const scope = cleanText(check.scope).toUpperCase();
    if (sectionId === 'STORY_COMPLIANCE') {
      return ['STORY', 'ACCEPTANCE', 'JIRA', 'CONFLUENCE'].some((value) => scope.includes(value));
    }
    if (sectionId === 'INSTRUCTION_COMPLIANCE') {
      return ['INSTRUCTION', 'AGENTS', 'COPILOT'].some((value) => scope.includes(value));
    }
    return false;
  });
}

function changeVerificationTabLabel(id: string, title: string | null | undefined): string {
  switch (cleanText(id).toUpperCase()) {
    case 'STORY_COMPLIANCE':
      return 'Story compliance';
    case 'INSTRUCTION_COMPLIANCE':
      return 'Instruction compliance';
    default:
      return cleanText(title) || cleanText(id) || 'Result';
  }
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

function uniqueText(values: string[]): string[] {
  return Array.from(new Set(values.map(cleanText).filter(hasText)));
}

function cleanText(value: string | null | undefined): string {
  return typeof value === 'string' ? value.trim() : '';
}

function hasText(value: string | null | undefined): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}
