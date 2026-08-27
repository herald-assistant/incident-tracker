import { Component, computed, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { merge } from 'rxjs';

import {
  AssessmentTrendDataset,
  AssessmentTrendGranularity,
  AssessmentTrendPeriod,
  AssessmentTrendStatusCount,
  AssessmentTrendView
} from '../../models/delivery-complexity-trends.models';
import { buildAssessmentTrendView } from '../../utils/delivery-complexity-trends-aggregation.utils';
import {
  ASSESSMENT_TREND_MAX_FILES,
  AssessmentTrendImportError,
  importAssessmentTrendFiles
} from '../../utils/delivery-complexity-trends-import.utils';

@Component({
  selector: 'app-delivery-complexity-trends-page',
  imports: [ReactiveFormsModule],
  templateUrl: './delivery-complexity-trends-page.html',
  styleUrl: './delivery-complexity-trends-page.scss'
})
export class DeliveryComplexityTrendsPageComponent {
  readonly dataset = signal<AssessmentTrendDataset | null>(null);
  readonly importing = signal(false);
  readonly importError = signal('');
  private readonly filterRevision = signal(0);

  readonly granularityControl = new FormControl<AssessmentTrendGranularity>('MONTH', {
    nonNullable: true
  });
  readonly teamControl = new FormControl('', { nonNullable: true });
  readonly authorControl = new FormControl('', { nonNullable: true });
  readonly fromDateControl = new FormControl('', { nonNullable: true });
  readonly toDateControl = new FormControl('', { nonNullable: true });

  readonly dateFilterInvalid = computed(() => {
    this.filterRevision();
    const fromDate = this.fromDateControl.value;
    const toDate = this.toDateControl.value;
    return Boolean(fromDate && toDate && fromDate > toDate);
  });

  readonly filtersActive = computed(() => {
    this.filterRevision();
    return this.granularityControl.value !== 'MONTH'
      || Boolean(
        this.teamControl.value
        || this.authorControl.value
        || this.fromDateControl.value
        || this.toDateControl.value
      );
  });

  readonly trend = computed<AssessmentTrendView | null>(() => {
    this.filterRevision();
    const dataset = this.dataset();
    if (!dataset) {
      return null;
    }
    return buildAssessmentTrendView(dataset, {
      granularity: this.granularityControl.value,
      teamKey: this.teamControl.value,
      authorKey: this.authorControl.value,
      fromDate: this.fromDateControl.value,
      toDate: this.toDateControl.value
    });
  });

  readonly loadedDateRange = computed(() => {
    const rows = this.dataset()?.rows ?? [];
    if (rows.length === 0) {
      return null;
    }
    const dates = rows.map((row) => row.doneDate).sort();
    return { from: dates[0], to: dates.at(-1)! };
  });

  constructor() {
    merge(
      this.granularityControl.valueChanges,
      this.teamControl.valueChanges,
      this.authorControl.valueChanges,
      this.fromDateControl.valueChanges,
      this.toDateControl.valueChanges
    )
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.filterRevision.update((revision) => revision + 1));
  }

  protected async loadFiles(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (files.length === 0) {
      return;
    }

    this.importing.set(true);
    this.importError.set('');
    try {
      const dataset = await importAssessmentTrendFiles(files);
      this.dataset.set(dataset);
      this.resetFilters();
    } catch (error) {
      this.importError.set(error instanceof AssessmentTrendImportError
        ? error.message
        : 'Nie udalo sie przetworzyc wybranych plikow CSV.');
    } finally {
      this.importing.set(false);
    }
  }

  protected clearDataset(): void {
    this.dataset.set(null);
    this.importError.set('');
    this.resetFilters();
  }

  protected resetFilters(): void {
    this.granularityControl.setValue('MONTH', { emitEvent: false });
    this.teamControl.setValue('', { emitEvent: false });
    this.authorControl.setValue('', { emitEvent: false });
    this.fromDateControl.setValue('', { emitEvent: false });
    this.toDateControl.setValue('', { emitEvent: false });
    this.filterRevision.update((revision) => revision + 1);
  }

  protected sourceLabel(dataset: AssessmentTrendDataset): string {
    return dataset.source === 'DELIVERY_COMPLEXITY_ASSESSMENT'
      ? 'Delivery Complexity Assessment'
      : 'Delivery Scope Complexity';
  }

  protected formatPoints(value: number): string {
    return value.toLocaleString('pl-PL', { maximumFractionDigits: 1 });
  }

  protected signedPoints(value: number | null): string {
    if (value === null) {
      return '-';
    }
    const prefix = value > 0 ? '+' : '';
    return `${prefix}${this.formatPoints(value)}`;
  }

  protected percentLabel(value: number | null): string {
    if (value === null) {
      return '-';
    }
    const prefix = value > 0 ? '+' : '';
    return `${prefix}${value.toLocaleString('pl-PL', { maximumFractionDigits: 1 })}%`;
  }

  protected barHeight(period: AssessmentTrendPeriod, trend: AssessmentTrendView): number {
    const maximum = Math.max(...trend.periods.map((item) => item.points), 0);
    if (maximum === 0) {
      return 2;
    }
    if (period.points === 0) {
      return 2;
    }
    return Math.max(7, (period.points / maximum) * 100);
  }

  protected chartAriaLabel(dataset: AssessmentTrendDataset, trend: AssessmentTrendView): string {
    const periods = trend.periods
      .map((period) => `${period.label}: ${this.formatPoints(period.points)} ${dataset.metricShortLabel}`)
      .join(', ');
    return `Trend ${dataset.metricLabel}. ${periods}. Dokladne dane znajduja sie w tabeli pod wykresem.`;
  }

  protected lastPeriod(trend: AssessmentTrendView): AssessmentTrendPeriod | null {
    return trend.periods.at(-1) ?? null;
  }

  protected statusLabel(status: AssessmentTrendStatusCount['status']): string {
    const labels: Record<string, string> = {
      COMPLETED: 'Ocenione',
      NOT_SCORABLE: 'Bez wystarczającego materiału',
      EXCLUDED: 'Wyłączone',
      FAILED: 'Nieudane',
      MIXED: 'Niespójny status',
      UNKNOWN: 'Nieznany status'
    };
    return labels[status] ?? status;
  }

  protected readonly maxFiles = ASSESSMENT_TREND_MAX_FILES;
}
