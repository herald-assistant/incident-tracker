import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import {
  ApiErrorResponse,
  AnalysisJobStateSnapshot,
  LocalAnalysisRunDetailResponse,
  LocalAnalysisRunListItemResponse
} from '../../core/models/analysis.models';
import { AnalysisRunHistoryApiService } from '../../core/services/analysis-run-history-api.service';
import {
  buildExportFileName,
  parseImportedAnalysis
} from '../../core/utils/analysis-import-export.utils';
import {
  formatDateTime,
  formatStatus,
  statusClassName,
  valueOrFallback
} from '../../core/utils/analysis-display.utils';
import { downloadJsonFile } from '../../core/utils/json-file.utils';
import { AnalysisFinalResultComponent } from '../../components/analysis-final-result/analysis-final-result';
import { AnalysisFollowUpChatComponent } from '../../components/analysis-follow-up-chat/analysis-follow-up-chat';
import { AnalysisStepsPanelComponent } from '../../components/analysis-steps-panel/analysis-steps-panel';

type RunContextItem = {
  label: string;
  value: string;
};

@Component({
  selector: 'app-analysis-history-page',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AnalysisFinalResultComponent,
    AnalysisFollowUpChatComponent,
    AnalysisStepsPanelComponent
  ],
  templateUrl: './analysis-history-page.html',
  styleUrl: './analysis-history-page.scss'
})
export class AnalysisHistoryPageComponent {
  private readonly historyApi = inject(AnalysisRunHistoryApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly filterControl = new FormControl('', { nonNullable: true });
  readonly renameControl = new FormControl('', { nonNullable: true });

  readonly runs = signal<LocalAnalysisRunListItemResponse[]>([]);
  readonly filterText = signal('');
  readonly isListLoading = signal(false);
  readonly listError = signal('');
  readonly selectedRunId = signal('');
  readonly selectedDetail = signal<LocalAnalysisRunDetailResponse | null>(null);
  readonly selectedJob = signal<AnalysisJobStateSnapshot | null>(null);
  readonly isDetailLoading = signal(false);
  readonly detailError = signal('');
  readonly renamingAnalysisId = signal('');
  readonly renameError = signal('');
  readonly isRenaming = signal(false);
  readonly deletingAnalysisId = signal('');
  readonly deleteError = signal('');
  readonly exportingAnalysisId = signal('');

  readonly filteredRuns = computed(() => {
    const query = this.filterText().trim().toLowerCase();
    const runs = this.runs();
    if (!query) {
      return runs;
    }

    return runs.filter((run) =>
      [run.name, run.feature]
        .map((value) => String(value || '').toLowerCase())
        .some((value) => value.includes(query))
    );
  });

  readonly resultCountLabel = computed(() => {
    const filteredCount = this.filteredRuns().length;
    const totalCount = this.runs().length;
    if (filteredCount === totalCount) {
      return `${totalCount} ${totalCount === 1 ? 'run' : 'runy'}`;
    }

    return `${filteredCount} z ${totalCount}`;
  });

  constructor() {
    this.filterControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => this.filterText.set(value));

    this.loadRuns();
  }

  loadRuns(): void {
    this.isListLoading.set(true);
    this.listError.set('');
    this.deleteError.set('');

    this.historyApi
      .listRuns()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isListLoading.set(false))
      )
      .subscribe({
        next: (response) => {
          this.runs.set(normalizeRuns(response?.runs ?? []));
        },
        error: (error) => {
          this.listError.set(toErrorMessage(error, 'Nie udało się odczytać lokalnej historii analiz.'));
        }
      });
  }

  openRun(run: LocalAnalysisRunListItemResponse): void {
    this.selectedRunId.set(run.analysisId);
    this.selectedDetail.set(null);
    this.selectedJob.set(null);
    this.detailError.set('');
    this.isDetailLoading.set(true);

    this.historyApi
      .getRun(run.analysisId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isDetailLoading.set(false))
      )
      .subscribe({
        next: (detail) => this.applyDetail(detail),
        error: (error) => {
          this.detailError.set(toErrorMessage(error, 'Nie udało się wczytać lokalnego runu.'));
        }
      });
  }

  exportRun(run: LocalAnalysisRunListItemResponse): void {
    const selectedDetail = this.selectedDetail();
    const selectedJob = this.selectedJob();
    if (selectedDetail?.analysisId === run.analysisId && selectedJob) {
      this.downloadRunExport(selectedDetail, selectedJob);
      return;
    }

    this.exportingAnalysisId.set(run.analysisId);
    this.detailError.set('');

    this.historyApi
      .getRun(run.analysisId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.exportingAnalysisId.set(''))
      )
      .subscribe({
        next: (detail) => {
          const job = this.parseDetailJob(detail, 'Nie udało się odczytać danych eksportu runu.');
          if (!job) {
            return;
          }
          this.downloadRunExport(detail, job);
        },
        error: (error) => {
          this.detailError.set(
            toErrorMessage(error, 'Nie udało się przygotować eksportu lokalnego runu.')
          );
        }
      });
  }

  startRename(run: LocalAnalysisRunListItemResponse): void {
    this.renamingAnalysisId.set(run.analysisId);
    this.renameControl.setValue(run.name || run.analysisId);
    this.renameError.set('');
  }

  cancelRename(): void {
    this.renamingAnalysisId.set('');
    this.renameControl.setValue('');
    this.renameError.set('');
  }

  saveRename(event: Event, run: LocalAnalysisRunListItemResponse): void {
    event.preventDefault();

    const name = this.renameControl.value.trim();
    if (!name) {
      this.renameError.set('Nazwa runu nie może być pusta.');
      return;
    }

    this.isRenaming.set(true);
    this.renameError.set('');

    this.historyApi
      .renameRun(run.analysisId, { name })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isRenaming.set(false))
      )
      .subscribe({
        next: (detail) => {
          this.replaceRunListItem(detail);
          if (this.selectedRunId() === detail.analysisId) {
            this.applyDetail(detail);
          }
          this.cancelRename();
        },
        error: (error) => {
          this.renameError.set(toErrorMessage(error, 'Nie udało się zmienić nazwy runu.'));
        }
      });
  }

  deleteRun(run: LocalAnalysisRunListItemResponse): void {
    const confirmed = window.confirm(
      `Usunąć lokalny run "${run.name || run.analysisId}"? Tej operacji nie można cofnąć.`
    );
    if (!confirmed) {
      return;
    }

    this.deletingAnalysisId.set(run.analysisId);
    this.deleteError.set('');

    this.historyApi
      .deleteRun(run.analysisId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.deletingAnalysisId.set(''))
      )
      .subscribe({
        next: () => {
          this.runs.set(this.runs().filter((candidate) => candidate.analysisId !== run.analysisId));
          if (this.selectedRunId() === run.analysisId) {
            this.selectedRunId.set('');
            this.selectedDetail.set(null);
            this.selectedJob.set(null);
            this.detailError.set('');
          }
        },
        error: (error) => {
          this.deleteError.set(toErrorMessage(error, 'Nie udało się usunąć lokalnego runu.'));
        }
      });
  }

  protected featureLabel(feature: string): string {
    if (feature === 'incident-analysis') {
      return 'Incident Analysis';
    }
    if (feature === 'flow-explorer') {
      return 'Flow Explorer';
    }
    return feature
      ? feature
          .split(/[-_]/)
          .filter(Boolean)
          .map((token) => token.charAt(0).toUpperCase() + token.slice(1))
          .join(' ')
      : 'Unknown feature';
  }

  protected dateTime(value: string): string {
    return formatDateTime(value) || 'n/a';
  }

  protected statusLabel(status: string): string {
    return formatStatus(status);
  }

  protected statusClass(status: string): string {
    return statusClassName(status);
  }

  protected runContextItems(job: AnalysisJobStateSnapshot): RunContextItem[] {
    return [
      { label: 'Correlation ID', value: job.correlationId || 'n/a' },
      { label: 'Analysis ID', value: valueOrFallback(job.analysisId) },
      { label: 'Environment', value: valueOrFallback(job.environment) },
      { label: 'Branch', value: valueOrFallback(job.gitLabBranch) },
      { label: 'Model', value: job.aiModel || 'default backend' },
      { label: 'Reasoning', value: job.reasoningEffort || 'default backend' }
    ];
  }

  protected trackRun(_: number, run: LocalAnalysisRunListItemResponse): string {
    return run.analysisId;
  }

  private applyDetail(detail: LocalAnalysisRunDetailResponse): void {
    const job = this.parseDetailJob(detail, 'Nie udało się odczytać danych lokalnego runu.');
    if (!job) {
      return;
    }

    this.selectedRunId.set(detail.analysisId);
    this.selectedDetail.set(detail);
    this.selectedJob.set(job);
    this.detailError.set('');
    this.replaceRunListItem(detail);
  }

  private replaceRunListItem(detail: LocalAnalysisRunDetailResponse): void {
    const replacement: LocalAnalysisRunListItemResponse = {
      analysisId: detail.analysisId,
      feature: detail.feature,
      name: detail.name,
      createdAt: detail.createdAt,
      updatedAt: detail.updatedAt,
      completedAt: detail.completedAt
    };
    this.runs.set(
      normalizeRuns(
        this.runs().map((run) => (run.analysisId === detail.analysisId ? replacement : run))
      )
    );
  }

  private downloadRunExport(
    detail: LocalAnalysisRunDetailResponse,
    job: AnalysisJobStateSnapshot
  ): void {
    const exportedAt = detail.exportEnvelope?.exportedAt || new Date().toISOString();
    downloadJsonFile(buildExportFileName(job, exportedAt), detail.exportEnvelope);
  }

  private parseDetailJob(
    detail: LocalAnalysisRunDetailResponse,
    fallback: string
  ): AnalysisJobStateSnapshot | null {
    try {
      return parseRunJob(detail);
    } catch (error) {
      this.detailError.set(error instanceof Error ? error.message : fallback);
      return null;
    }
  }
}

function normalizeRuns(
  runs: LocalAnalysisRunListItemResponse[]
): LocalAnalysisRunListItemResponse[] {
  return [...runs]
    .map((run) => ({
      analysisId: run.analysisId || '',
      feature: run.feature || '',
      name: run.name || run.analysisId || '',
      createdAt: run.createdAt || '',
      updatedAt: run.updatedAt || '',
      completedAt: run.completedAt || ''
    }))
    .filter((run) => run.analysisId)
    .sort(compareRunsByMostRecent);
}

function compareRunsByMostRecent(
  left: LocalAnalysisRunListItemResponse,
  right: LocalAnalysisRunListItemResponse
): number {
  const leftTime = Date.parse(left.completedAt || left.updatedAt || left.createdAt || '');
  const rightTime = Date.parse(right.completedAt || right.updatedAt || right.createdAt || '');
  const normalizedLeftTime = Number.isFinite(leftTime) ? leftTime : 0;
  const normalizedRightTime = Number.isFinite(rightTime) ? rightTime : 0;
  return normalizedRightTime - normalizedLeftTime;
}

function parseRunJob(detail: LocalAnalysisRunDetailResponse): AnalysisJobStateSnapshot {
  return parseImportedAnalysis(detail.exportEnvelope).job;
}

function toErrorMessage(error: unknown, fallback: string): string {
  const status = error instanceof HttpErrorResponse ? error.status : 0;
  const payload = normalizeApiErrorPayload(error instanceof HttpErrorResponse ? error.error : null);

  if (payload?.message) {
    return payload.message;
  }
  if (status === 404) {
    return 'Nie znaleziono lokalnego runu. Odśwież listę historii.';
  }
  if (status === 409) {
    return 'Lokalny run jest niekompletny albo uszkodzony.';
  }
  if (status > 0) {
    return `Backend zwrócił HTTP ${status}.`;
  }

  return fallback;
}

function normalizeApiErrorPayload(payload: unknown): ApiErrorResponse | null {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    return null;
  }

  const payloadRecord = payload as Record<string, unknown>;
  return {
    code: typeof payloadRecord['code'] === 'string' ? payloadRecord['code'] : '',
    message: typeof payloadRecord['message'] === 'string' ? payloadRecord['message'] : '',
    authStartUrl:
      typeof payloadRecord['authStartUrl'] === 'string' ? payloadRecord['authStartUrl'] : null,
    fieldErrors: Array.isArray(payloadRecord['fieldErrors'])
      ? payloadRecord['fieldErrors']
          .filter(
            (fieldError): fieldError is Record<string, unknown> =>
              !!fieldError && typeof fieldError === 'object' && !Array.isArray(fieldError)
          )
          .map((fieldError) => ({
            field: typeof fieldError['field'] === 'string' ? fieldError['field'] : '',
            message: typeof fieldError['message'] === 'string' ? fieldError['message'] : ''
          }))
      : []
  };
}
