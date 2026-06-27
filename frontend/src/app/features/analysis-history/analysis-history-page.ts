import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import {
  ApiErrorResponse,
  LocalAnalysisRunDetailResponse,
  LocalAnalysisRunListItemResponse
} from '../../core/models/analysis.models';
import { AnalysisRunHistoryApiService } from '../../core/services/analysis-run-history-api.service';
import { formatDateTime } from '../../core/utils/analysis-display.utils';
import {
  downloadJsonFile,
  formatFileTimestamp,
  sanitizeFileNamePart
} from '../../core/utils/json-file.utils';

@Component({
  selector: 'app-analysis-history-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './analysis-history-page.html',
  styleUrl: './analysis-history-page.scss'
})
export class AnalysisHistoryPageComponent {
  private readonly historyApi = inject(AnalysisRunHistoryApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly filterControl = new FormControl('', { nonNullable: true });
  readonly renameControl = new FormControl('', { nonNullable: true });

  readonly runs = signal<LocalAnalysisRunListItemResponse[]>([]);
  readonly filterText = signal('');
  readonly isListLoading = signal(false);
  readonly listError = signal('');
  readonly openingAnalysisId = signal('');
  readonly openError = signal('');
  readonly renamingAnalysisId = signal('');
  readonly renameError = signal('');
  readonly isRenaming = signal(false);
  readonly deletingAnalysisId = signal('');
  readonly deleteError = signal('');
  readonly exportingAnalysisId = signal('');
  readonly exportError = signal('');

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
      return `${totalCount} ${totalCount === 1 ? 'run' : 'runs'}`;
    }

    return `${filteredCount} of ${totalCount}`;
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
    this.openError.set('');
    this.exportError.set('');

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
    const route = routeForFeature(run.feature);
    if (!route) {
      this.openError.set(`Feature "${run.feature || 'unknown'}" nie ma jeszcze ekranu odtwarzania lokalnych runów.`);
      return;
    }

    this.openingAnalysisId.set(run.analysisId);
    this.openError.set('');

    void this.router
      .navigate([route], {
        queryParams: {
          localRunId: run.analysisId
        }
      })
      .then((navigated) => {
        if (!navigated) {
          this.openError.set('Nie udało się otworzyć lokalnego runu w ekranie feature.');
        }
      })
      .finally(() => this.openingAnalysisId.set(''));
  }

  exportRun(run: LocalAnalysisRunListItemResponse): void {
    this.exportingAnalysisId.set(run.analysisId);
    this.exportError.set('');

    this.historyApi
      .exportRun(run.analysisId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.exportingAnalysisId.set(''))
      )
      .subscribe({
        next: (exportEnvelope) => this.downloadRunExport(run, exportEnvelope),
        error: (error) => {
          this.exportError.set(
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

  protected featureIcon(feature: string): string {
    if (feature === 'incident-analysis') {
      return 'troubleshoot';
    }
    if (feature === 'flow-explorer') {
      return 'account_tree';
    }
    return 'analytics';
  }

  protected dateTime(value: string): string {
    return formatDateTime(value) || 'n/a';
  }

  protected trackRun(_: number, run: LocalAnalysisRunListItemResponse): string {
    return run.analysisId;
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

  private downloadRunExport(run: LocalAnalysisRunListItemResponse, exportEnvelope: unknown): void {
    const exportedAt = exportedAtFromEnvelope(exportEnvelope) || new Date().toISOString();
    downloadJsonFile(buildLocalRunExportFileName(run, exportedAt), exportEnvelope);
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

function routeForFeature(feature: string): string | null {
  if (feature === 'incident-analysis') {
    return '/incident-analysis';
  }
  if (feature === 'flow-explorer') {
    return '/flow-explorer';
  }
  return null;
}

function buildLocalRunExportFileName(
  detail: LocalAnalysisRunListItemResponse,
  exportedAt: string
): string {
  const feature = sanitizeFileNamePart(detail.feature || 'analysis');
  const name = sanitizeFileNamePart(detail.name || detail.analysisId || 'run');
  return `${feature}-${name}-${formatFileTimestamp(exportedAt)}.json`;
}

function exportedAtFromEnvelope(envelope: unknown): string {
  if (!envelope || typeof envelope !== 'object' || Array.isArray(envelope)) {
    return '';
  }
  const value = (envelope as Record<string, unknown>)['exportedAt'];
  return typeof value === 'string' ? value : '';
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
