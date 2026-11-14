import { ChangeDetectionStrategy, Component, Input, booleanAttribute, computed, contentChildren, forwardRef, input, output, signal, viewChild } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatSelect, MatSelectModule } from '@angular/material/select';
import { FormFieldComponent } from '../form-field/form-field';
import { ChoiceOptionComponent } from './choice-option';
export { ChoiceOptionComponent } from './choice-option';

let nextSelectId = 0;

export interface ChoiceOption {
  value: string;
  label: string;
  hint?: string;
  disabled?: boolean;
  badge?: string;
  tooltip?: string;
}

@Component({
  selector: 'app-choice-select',
  imports: [MatSelectModule, FormFieldComponent],
  templateUrl: './choice-select.html',
  styleUrl: './choice-select.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[style.--field-label-height]': "size() === 'descriptive' ? '18px' : null",
    '[style.--field-label-gap]': "size() === 'descriptive' ? '6px' : null"
  },
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => ChoiceSelectComponent), multi: true }]
})
export class ChoiceSelectComponent implements ControlValueAccessor {
  readonly options = input<readonly ChoiceOption[]>([]);
  private readonly optionDefinitions = contentChildren(ChoiceOptionComponent);
  readonly availableOptions = computed<readonly ChoiceOption[]>(() => [
    ...this.options(),
    ...this.optionDefinitions().map(option => ({ value: option.value(), label: option.label(),
      hint: option.hint(), disabled: option.disabled(), badge: option.badge(), tooltip: option.tooltip() }))
  ]);
  readonly inputId = input(`choice-select-${nextSelectId++}`);
  readonly label = input('');
  readonly tooltip = input('');
  readonly tooltipClass = input('');
  readonly hint = input('');
  readonly error = input('');
  readonly ariaLabel = input('');
  readonly labelledBy = input('');
  readonly describedBy = input<string | null>('');
  readonly placeholder = input('Wybierz');
  readonly description = input('');
  readonly size = input<'standard' | 'descriptive'>('standard');
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly resetAfterSelection = input(false, { transform: booleanAttribute });
  readonly loading = input(false);
  readonly valueChange = output<string>();
  readonly opened = output<void>();
  readonly touched = output<void>();
  readonly selectedValue = signal('');
  readonly selectControl = viewChild(MatSelect);
  readonly descriptionIds = computed(() => [this.describedBy(), this.hint() ? `${this.inputId()}-hint` : '',
    this.error() ? `${this.inputId()}-error` : ''].filter(Boolean).join(' '));
  private readonly formDisabled = signal(false);
  readonly isDisabled = computed(() => this.disabled() || this.formDisabled());
  readonly selectedLabel = computed(() => this.availableOptions().find(option => option.value === this.selectedValue())?.label
    || this.selectedValue() || this.placeholder());
  readonly selectedTooltip = computed(() => this.availableOptions().find(option => option.value === this.selectedValue())?.tooltip
    || this.selectedLabel());
  readonly displayOptions = computed<readonly ChoiceOption[]>(() => this.availableOptions().some(option => option.value === this.selectedValue())
    ? this.availableOptions()
    : [{ value: this.selectedValue(), label: this.selectedLabel(), disabled: true }, ...this.availableOptions()]);
  readonly selectedDescription = computed(() => {
    const description = this.description().trim();
    return description.toLocaleLowerCase() === this.selectedLabel().trim().toLocaleLowerCase() ? '' : description;
  });

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  @Input() set value(value: string | null | undefined) {
    this.selectedValue.set(value ?? '');
  }

  writeValue(value: string | null): void { this.selectedValue.set(value ?? ''); }
  registerOnChange(fn: (value: string) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(disabled: boolean): void { this.formDisabled.set(disabled); }

  select(value: string): void {
    if (this.isDisabled()) return;
    this.selectedValue.set(value);
    this.onChange(value);
    this.valueChange.emit(value);
    if (this.resetAfterSelection()) {
      this.selectedValue.set('');
      // Both values can change within one render; reset Material's selection too.
      this.selectControl()?.writeValue('');
    }
  }

  touch(): void { this.onTouched(); this.touched.emit(); }
  onOpenedChange(open: boolean): void {
    if (open) this.opened.emit();
    else this.touch();
  }
}
