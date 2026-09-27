import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import {
  AnalysisAiModelOption,
  AnalysisAiModelOptionsResponse,
  AnalysisAiModelTokenRates
} from '../../core/models/analysis.models';
import { AiOptionsApiService } from '../../core/services/ai-options-api.service';
import { normalizeAnalysisAiModelOptions } from '../../core/utils/analysis-ai-model-options.utils';

type PriceRow = {
  model: AnalysisAiModelOption;
  tier: 'Default' | 'Long context';
  tierHint: string | null;
  rates: AnalysisAiModelTokenRates | null;
};

@Component({
  selector: 'app-ai-models-page',
  imports: [RouterLink],
  templateUrl: './ai-models-page.html',
  styleUrl: './ai-models-page.scss'
})
export class AiModelsPageComponent {
  private readonly api = inject(AiOptionsApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly numberFormat = new Intl.NumberFormat('pl-PL', { maximumFractionDigits: 4 });

  readonly catalog = signal<AnalysisAiModelOptionsResponse | null>(null);
  readonly loading = signal(true);
  readonly error = signal(false);
  readonly query = signal('');
  readonly modelCount = computed(() => this.catalog()?.models.length ?? 0);
  readonly pricedCount = computed(() => this.catalog()?.models.filter((model) =>
    this.hasRates(model.pricing?.defaultRates ?? null)).length ?? 0);
  readonly rows = computed<PriceRow[]>(() => {
    const search = this.query().trim().toLocaleLowerCase('pl');
    const models = this.catalog()?.models ?? [];
    return models
      .filter((model) => !search || `${model.name} ${model.id}`.toLocaleLowerCase('pl').includes(search))
      .sort((left, right) =>
        Number(this.hasRates(right.pricing?.defaultRates ?? null)) -
        Number(this.hasRates(left.pricing?.defaultRates ?? null)) ||
        left.name.localeCompare(right.name, 'pl'))
      .flatMap((model) => {
        const result: PriceRow[] = [{ model, tier: 'Default', tierHint: null,
          rates: model.pricing?.defaultRates ?? null }];
        if (this.hasDifferentRates(model.pricing?.defaultRates ?? null,
            model.pricing?.longContextRates ?? null)) {
          const threshold = model.pricing?.longContextThresholdTokens;
          result.push({
            model,
            tier: 'Long context',
            tierHint: threshold ? `Powyżej ${this.numberFormat.format(threshold)} tokenów` : null,
            rates: model.pricing?.longContextRates ?? null
          });
        }
        return result;
      });
  });

  constructor() {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(false);
    this.api.getOptions().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (response) => {
        this.catalog.set(normalizeAnalysisAiModelOptions(response));
        this.loading.set(false);
      },
      error: () => {
        this.catalog.set(null);
        this.error.set(true);
        this.loading.set(false);
      }
    });
  }

  updateSearch(event: Event): void {
    this.query.set((event.target as HTMLInputElement).value);
  }

  category(model: AnalysisAiModelOption): string {
    switch (model.modelPickerCategory) {
      case 'lightweight': return 'Lżejszy';
      case 'versatile': return 'Uniwersalny';
      case 'powerful': return 'Do złożonych zadań';
      default: return '';
    }
  }

  price(rates: AnalysisAiModelTokenRates | null, field: keyof AnalysisAiModelTokenRates): string {
    const value = rates?.[field];
    return typeof value === 'number' && Number.isFinite(value) && value >= 0
      ? this.numberFormat.format(value)
      : '—';
  }

  private hasRates(rates: AnalysisAiModelTokenRates | null): boolean {
    return rates !== null && [rates.input, rates.cachedInput, rates.cacheWrite, rates.output]
      .some((value) => typeof value === 'number' && Number.isFinite(value) && value >= 0);
  }

  private hasDifferentRates(
    standard: AnalysisAiModelTokenRates | null,
    longContext: AnalysisAiModelTokenRates | null
  ): boolean {
    return this.hasRates(longContext) && (
      standard?.input !== longContext?.input ||
      standard?.cachedInput !== longContext?.cachedInput ||
      standard?.cacheWrite !== longContext?.cacheWrite ||
      standard?.output !== longContext?.output
    );
  }
}
