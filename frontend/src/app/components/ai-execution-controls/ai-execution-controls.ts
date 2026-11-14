import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { AnalysisAiModelOptionsResponse } from '../../core/models/analysis.models';
import {
  defaultReasoningEffortForAiModel,
  reasoningEffortsForAiModel
} from '../../core/utils/analysis-ai-model-options.utils';
import { ChoiceOption, ChoiceSelectComponent } from '../choice-select/choice-select';

let nextAiExecutionId = 0;

const EFFORT_LABELS: Record<string, string> = {
  none: 'Wyłączony', minimal: 'Minimalny', low: 'Niski', medium: 'Średni',
  high: 'Wysoki', xhigh: 'Bardzo wysoki', max: 'Maksymalny'
};

/** Presentation only. The feature owns selection, defaults and request semantics. */
@Component({
  selector: 'app-ai-execution-controls',
  imports: [ChoiceSelectComponent],
  templateUrl: './ai-execution-controls.html',
  styleUrl: './ai-execution-controls.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AiExecutionControlsComponent {
  private readonly id = `ai-execution-${nextAiExecutionId++}`;
  readonly modelInputId = input(`${this.id}-model`);
  readonly reasoningInputId = input(`${this.id}-reasoning`);
  readonly catalog = input.required<AnalysisAiModelOptionsResponse>();
  readonly model = input('');
  readonly reasoningEffort = input('');
  readonly loading = input(false);
  readonly unavailable = input(false);
  readonly error = input('');
  readonly disabled = input(false);
  readonly modelDisabled = input(false);
  readonly reasoningDisabled = input(false);
  readonly allowDefaultSelection = input(false);
  readonly modelChange = output<string>();
  readonly reasoningEffortChange = output<string>();
  readonly modelTouched = output<void>();
  readonly reasoningTouched = output<void>();
  readonly opened = output<void>();

  private readonly catalogMessage = computed(() => {
    if (this.loading()) return 'Ładowanie modeli…';
    if (this.unavailable() || this.error()) return 'Katalog modeli niedostępny';
    return this.catalog().models.length ? '' : 'Brak dostępnych modeli';
  });
  readonly modelLocked = computed(() => this.disabled() || this.modelDisabled() || !!this.catalogMessage());
  readonly efforts = computed(() => reasoningEffortsForAiModel(this.catalog(), this.model()));
  readonly reasoningLocked = computed(() => this.disabled() || this.reasoningDisabled()
    || !!this.catalogMessage() || !this.efforts().length);
  private readonly defaultModelLabel = computed(() => {
    const id = this.catalog().defaultModel;
    return id ? `Domyślny: ${this.modelName(id)}` : 'Wybierz model AI';
  });
  private readonly emptyReasoningLabel = computed(() => {
    if (!this.efforts().length) {
      const id = this.model() || this.catalog().defaultModel;
      if (!id) return 'Najpierw wybierz model';
      const model = this.catalog().models.find(model => model.id === id);
      return model && !model.supportsReasoningEffort
        ? 'Model nie udostępnia reasoning' : 'Brak dostępnych poziomów';
    }
    const value = defaultReasoningEffortForAiModel(this.catalog(), this.model());
    return value ? `Domyślny: ${this.effortLabel(value)}` : 'Ustawienie domyślne';
  });

  readonly modelOptions = computed<ChoiceOption[]>(() => {
    const message = this.catalogMessage();
    if (message) return [{ value: this.model(), label: message, disabled: true }];
    const options: ChoiceOption[] = this.catalog().models.map(model => ({
      value: model.id, label: model.name || model.id, tooltip: model.id
    }));
    if (!this.model() || this.allowDefaultSelection()) {
      options.unshift({ value: '', label: this.defaultModelLabel(), disabled: !this.allowDefaultSelection() });
    }
    return this.withRestoredValue(options, this.model(), this.modelName(this.model()));
  });
  readonly reasoningOptions = computed<ChoiceOption[]>(() => {
    const message = this.loading() ? 'Ładowanie poziomów…' : this.catalogMessage();
    if (message) return [{ value: this.reasoningEffort(), label: message, disabled: true }];
    const options: ChoiceOption[] = this.efforts().map(value => ({ value, label: this.effortLabel(value), tooltip: value }));
    if (!this.reasoningEffort() || this.allowDefaultSelection()) {
      options.unshift({ value: '', label: this.emptyReasoningLabel(), disabled: !this.allowDefaultSelection() });
    }
    return this.withRestoredValue(options, this.reasoningEffort(), this.effortLabel(this.reasoningEffort()));
  });

  selectModel(value: string): void {
    if (!this.modelLocked()) this.modelChange.emit(value);
  }
  selectReasoning(value: string): void {
    if (!this.reasoningLocked()) this.reasoningEffortChange.emit(value);
  }
  private modelName(id: string): string {
    return this.catalog().models.find(model => model.id === id)?.name || id;
  }
  private effortLabel(value: string): string { return EFFORT_LABELS[value] || value; }
  private withRestoredValue(options: ChoiceOption[], value: string, label: string): ChoiceOption[] {
    return value && !options.some(option => option.value === value)
      ? [{ value, label, tooltip: `${value} — poza aktualnym katalogiem`, disabled: true }, ...options]
      : options;
  }
}
