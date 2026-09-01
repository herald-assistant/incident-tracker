import { Component, computed, ElementRef, OnDestroy, signal, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { merge } from 'rxjs';

import {
  AssessmentTrendDataset,
  AssessmentTrendDimensionDefinition,
  AssessmentTrendDimensionMode,
  AssessmentTrendEfficiencyPeriod,
  AssessmentTrendEfficiencyView,
  AssessmentTrendFilterOption,
  AssessmentTrendGranularity,
  AssessmentTrendPeriod,
  AssessmentTrendPeriodDimension,
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
export class DeliveryComplexityTrendsPageComponent implements OnDestroy {
  readonly dataset = signal<AssessmentTrendDataset | null>(null);
  readonly importing = signal(false);
  readonly importError = signal('');
  readonly dimensionMode = signal<AssessmentTrendDimensionMode>('AVERAGE');
  readonly selectedIssueTypeKeys = signal<readonly string[]>([]);
  readonly filtersStuck = signal(false);
  private readonly filterRevision = signal(0);
  private filterStickyObserver?: IntersectionObserver;

  @ViewChild('filterStickySentinel')
  set filterStickySentinel(element: ElementRef<HTMLElement> | undefined) {
    this.filterStickyObserver?.disconnect();
    this.filterStickyObserver = undefined;
    this.filtersStuck.set(false);
    if (!element || typeof IntersectionObserver === 'undefined') {
      return;
    }
    this.filterStickyObserver = new IntersectionObserver(([entry]) => {
      this.filtersStuck.set(!entry.isIntersecting && entry.boundingClientRect.top < 64);
    }, {
      rootMargin: '-64px 0px 0px',
      threshold: 0
    });
    this.filterStickyObserver.observe(element.nativeElement);
  }

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
      || this.selectedIssueTypeKeys().length > 0
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
      issueTypeKeys: this.selectedIssueTypeKeys(),
      fromDate: this.fromDateControl.value,
      toDate: this.toDateControl.value
    });
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

  ngOnDestroy(): void {
    this.filterStickyObserver?.disconnect();
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
      this.dimensionMode.set(dataset.source === 'DELIVERY_SCOPE_COMPLEXITY' ? 'TOTAL' : 'AVERAGE');
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
    this.dimensionMode.set('AVERAGE');
    this.importError.set('');
    this.resetFilters();
  }

  protected resetFilters(): void {
    this.granularityControl.setValue('MONTH', { emitEvent: false });
    this.teamControl.setValue('', { emitEvent: false });
    this.authorControl.setValue('', { emitEvent: false });
    this.selectedIssueTypeKeys.set([]);
    this.fromDateControl.setValue('', { emitEvent: false });
    this.toDateControl.setValue('', { emitEvent: false });
    this.filterRevision.update((revision) => revision + 1);
  }

  protected sourceLabel(dataset: AssessmentTrendDataset): string {
    return dataset.source === 'DELIVERY_COMPLEXITY_ASSESSMENT'
      ? 'Delivery Complexity Assessment'
      : 'Delivery Scope Complexity';
  }

  protected issueTypeSelected(key: string): boolean {
    return this.selectedIssueTypeKeys().includes(key);
  }

  protected issueTypeSelectionLabel(options: readonly AssessmentTrendFilterOption[]): string {
    const selected = this.selectedIssueTypeKeys();
    if (selected.length === 0) {
      return 'Wszystkie typy';
    }
    if (selected.length === 1) {
      return options.find((option) => option.key === selected[0])?.label ?? '1 wybrany typ';
    }
    return `${selected.length} wybrane typy`;
  }

  protected jiraIssueCountLabel(count: number): string {
    return `${count} ${count === 1 ? 'Jira Issue' : 'Jira Issues'}`;
  }

  protected toggleIssueType(key: string): void {
    this.selectedIssueTypeKeys.update((selected) => selected.includes(key)
      ? selected.filter((item) => item !== key)
      : [...selected, key].sort()
    );
  }

  protected setDimensionMode(mode: AssessmentTrendDimensionMode): void {
    this.dimensionMode.set(mode);
  }

  protected dimensionModeDescription(dataset: AssessmentTrendDataset): string {
    if (this.dimensionMode() === 'AVERAGE') {
      return dataset.source === 'DELIVERY_COMPLEXITY_ASSESSMENT'
        ? 'Średnia ocena 0–4 pokazuje profil typowego ocenionego Jira Issue, niezależnie od liczby dostaw.'
        : 'Średni punktowy wkład wymiaru na ocenione Jira Issue oddziela charakter zmiany od liczby dostaw.';
    }
    return dataset.source === 'DELIVERY_COMPLEXITY_ASSESSMENT'
        ? 'Przy kompletnych ocenach ważone segmenty sumują się do score100. Nie są bezpośrednim rozkładem CP, które powstają przez progi.'
        : 'Przy kompletnych ocenach segmenty są wkładem wymiarów i sumują się do Complexity Points (CP) ocenionych Jira Issues.';
  }

  protected dimensionPeriodTotal(period: AssessmentTrendPeriod): number {
    return this.roundDimensionDisplay(period.dimensions.reduce(
      (total, dimension) => total + (dimension.total ?? 0),
      0
    ));
  }

  protected dimensionTotalBarWidth(
    period: AssessmentTrendPeriod,
    trend: AssessmentTrendView
  ): number {
    const maximum = Math.max(...trend.periods.map((item) => this.dimensionPeriodTotal(item)), 0);
    const value = this.dimensionPeriodTotal(period);
    return maximum === 0 ? 2 : Math.max(value === 0 ? 2 : 8, value / maximum * 100);
  }

  protected dimensionShare(
    dimension: AssessmentTrendPeriodDimension,
    period: AssessmentTrendPeriod
  ): number {
    const total = this.dimensionPeriodTotal(period);
    return total === 0 || dimension.total === null ? 0 : dimension.total / total * 100;
  }

  protected dimensionColor(index: number): string {
    return [
      '#0c66e4', '#087e8b', '#6554c0', '#c47a00', '#ae2e24', '#1f845a', '#44546f'
    ][index % 7];
  }

  protected dimensionHeatColor(
    index: number,
    dimension: AssessmentTrendPeriodDimension,
    definition: AssessmentTrendDimensionDefinition
  ): string {
    const value = dimension.average;
    if (value === null) {
      return 'transparent';
    }
    const ratio = Math.min(1, Math.max(0, value / definition.averageMaximum));
    const rgb = [
      '12, 102, 228', '8, 126, 139', '101, 84, 192', '196, 122, 0',
      '174, 46, 36', '31, 132, 90', '68, 84, 111'
    ][index % 7];
    return `rgba(${rgb}, ${0.08 + ratio * 0.28})`;
  }

  protected dimensionChartAriaLabel(
    trend: AssessmentTrendView,
    dataset: AssessmentTrendDataset
  ): string {
    const labels = new Map(trend.dimensionDefinitions.map((item) => [item.key, item.label]));
    const periods = trend.periods.map((period) => {
      const values = period.dimensions.map((dimension) =>
        `${labels.get(dimension.key) ?? dimension.key}: ${dimension.total === null
          ? 'brak danych'
          : this.formatDimensionPoints(dimension.total)}`
      ).join(', ');
      return `${period.label}, ${period.unitCount} Jira Issues: ${values}`;
    }).join('. ');
    return `Łączny wkład wymiarów dla ${this.sourceLabel(dataset)}. ${periods}.`;
  }

  protected formatPoints(value: number): string {
    return value.toLocaleString('pl-PL', { maximumFractionDigits: 1 });
  }

  protected formatDimensionPoints(value: number): string {
    return value.toLocaleString('pl-PL', { maximumFractionDigits: 2 });
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

  protected percentageLabel(value: number): string {
    return `${value.toLocaleString('pl-PL', { maximumFractionDigits: 1 })}%`;
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
    return `Trend ${dataset.metricLabel}. ${periods}. Zmiany liczbowe i procentowe są pokazane pod okresami.`;
  }

  protected formatEfficiency(value: number | null): string {
    return value === null
      ? '-'
      : value.toLocaleString('pl-PL', { maximumFractionDigits: 2 });
  }

  protected formatPersonDays(value: number | null): string {
    return value === null
      ? '-'
      : value.toLocaleString('pl-PL', { maximumFractionDigits: 2 });
  }

  protected signedEfficiency(value: number | null): string {
    if (value === null) {
      return '-';
    }
    return `${value > 0 ? '+' : ''}${this.formatEfficiency(value)}`;
  }

  protected efficiencyBarHeight(
    period: AssessmentTrendEfficiencyPeriod,
    efficiency: AssessmentTrendEfficiencyView
  ): number {
    const maximum = Math.max(...efficiency.periods.map((item) => item.pointsPerPersonDay), 0);
    return maximum === 0 ? 2 : Math.max(7, period.pointsPerPersonDay / maximum * 100);
  }

  protected estimateRealizationBarHeight(
    period: AssessmentTrendEfficiencyPeriod,
    efficiency: AssessmentTrendEfficiencyView
  ): number {
    if (period.estimateRealizationPercent === null) {
      return 2;
    }
    return Math.max(7, period.estimateRealizationPercent / this.estimateTrendMaximum(efficiency) * 100);
  }

  protected estimateBaselinePosition(efficiency: AssessmentTrendEfficiencyView): number {
    return 100 / this.estimateTrendMaximum(efficiency) * 100;
  }

  protected estimatePeriodCount(efficiency: AssessmentTrendEfficiencyView): number {
    return efficiency.periods.filter((period) => period.estimateUnitCount > 0).length;
  }

  protected estimateRealizationLabel(value: number | null): string {
    return value === null ? '-' : this.percentageLabel(value);
  }

  protected signedPercentagePoints(value: number | null): string {
    if (value === null) {
      return 'pierwszy okres';
    }
    const prefix = value > 0 ? '+' : '';
    return `${prefix}${value.toLocaleString('pl-PL', { maximumFractionDigits: 1 })} p.p.`;
  }

  protected estimateChangeMeaning(value: number | null): string {
    if (value === null) {
      return 'punkt odniesienia dla kolejnych okresów';
    }
    if (value < 0) {
      return 'efektywniej niż w poprzednim okresie';
    }
    if (value > 0) {
      return 'mniej efektywnie niż w poprzednim okresie';
    }
    return 'bez zmiany względem poprzedniego okresu';
  }

  protected estimateRealizationTooltip(period: AssessmentTrendEfficiencyPeriod): string {
    const relation = period.estimateVariancePercent === null || period.estimateVariancePercent === 0
      ? 'zgodnie z estymatą'
      : period.estimateVariancePercent < 0
        ? `przeszacowanie ${this.percentageLabel(Math.abs(period.estimateVariancePercent))}`
        : `niedoszacowanie ${this.percentageLabel(period.estimateVariancePercent)}`;
    return `Time Spent / Original Estimate: ${this.estimateRealizationLabel(period.estimateRealizationPercent)}`
      + ` · ${relation}`
      + ` · zmiana: ${this.signedPercentagePoints(period.estimateRealizationDeltaPoints)}`
      + ` · ${this.estimateChangeMeaning(period.estimateRealizationDeltaPoints)}`
      + ` · Original Estimate: ${this.formatPersonDays(period.estimatedPersonDays)} MD`
      + ` · Time Spent: ${this.formatPersonDays(period.actualPersonDaysForEstimate)} MD`
      + ` · ${this.jiraIssueCountLabel(period.estimateUnitCount)}`;
  }

  protected estimateRealizationChartAriaLabel(efficiency: AssessmentTrendEfficiencyView): string {
    const periods = efficiency.periods
      .filter((period) => period.estimateUnitCount > 0)
      .map((period) => `${period.label}: ${this.estimateRealizationLabel(period.estimateRealizationPercent)}, zmiana ${this.signedPercentagePoints(period.estimateRealizationDeltaPoints)}, ${this.estimateChangeMeaning(period.estimateRealizationDeltaPoints)}`)
      .join(', ');
    return `Trend Time Spent do Original Estimate. 100% oznacza realizację zgodną z estymatą, wartość poniżej 100% przeszacowanie, a powyżej 100% niedoszacowanie. ${periods}.`;
  }

  private estimateTrendMaximum(efficiency: AssessmentTrendEfficiencyView): number {
    const maximum = Math.max(...efficiency.periods.map((period) =>
      period.estimateRealizationPercent ?? 0
    ), 100);
    return maximum * 1.08;
  }

  protected efficiencyChartAriaLabel(efficiency: AssessmentTrendEfficiencyView): string {
    const periods = efficiency.periods.map((period) =>
      `${period.label}: ${this.formatEfficiency(period.pointsPerPersonDay)} CP/MD`
    ).join(', ');
    return `Efektywność dostarczenia. ${periods}. Dokładne dane znajdują się w tabeli pod wykresem.`;
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

  private roundDimensionDisplay(value: number): number {
    return Math.round((value + Number.EPSILON) * 100) / 100;
  }
}
