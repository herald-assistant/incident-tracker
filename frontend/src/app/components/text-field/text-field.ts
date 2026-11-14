import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { ChangeDetectionStrategy, Component, ElementRef, Input, booleanAttribute, computed, effect, forwardRef, input, numberAttribute, output, signal, viewChild } from '@angular/core';
import { AbstractControl, ControlValueAccessor, NG_VALIDATORS, NG_VALUE_ACCESSOR, ValidationErrors, Validator, Validators } from '@angular/forms';
import { FormFieldComponent } from '../form-field/form-field';

let nextTextFieldId = 0;

@Component({
  selector: 'app-text-field',
  imports: [FormFieldComponent, CdkTextareaAutosize],
  templateUrl: './text-field.html',
  styleUrl: './text-field.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[style.--field-label-height]': "size() === 'descriptive' ? '18px' : null",
    '[style.--field-label-gap]': "size() === 'descriptive' ? '6px' : null"
  },
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => TextFieldComponent), multi: true },
    { provide: NG_VALIDATORS, useExisting: forwardRef(() => TextFieldComponent), multi: true }
  ]
})
export class TextFieldComponent implements ControlValueAccessor, Validator {
  readonly inputId = input(`text-field-${nextTextFieldId++}`);
  readonly label = input('');
  readonly ariaLabel = input('');
  readonly labelledBy = input('');
  readonly describedBy = input<string | null>('');
  readonly tooltip = input('');
  readonly tooltipClass = input('');
  readonly hint = input('');
  readonly error = input('');
  readonly type = input<'text' | 'search' | 'textarea' | 'date' | 'number' | 'password' | 'url' | 'email' | 'tel'>('text');
  readonly placeholder = input('');
  readonly rows = input(4, { transform: numberAttribute });
  readonly maxLength = input<number | null, unknown>(null, { transform: value => value == null ? null : numberAttribute(value) });
  readonly min = input<string | number | null>(null);
  readonly max = input<string | number | null>(null);
  readonly step = input<string | number | null>(null);
  readonly pattern = input<string | null>(null);
  readonly list = input<string | null>(null);
  readonly inputmode = input('');
  readonly wrap = input('soft');
  readonly autosize = input(false, { transform: booleanAttribute });
  readonly minRows = input(1, { transform: numberAttribute });
  readonly maxRows = input<number | undefined, unknown>(undefined, { transform: value => value == null ? undefined : numberAttribute(value) });
  readonly required = input(false, { transform: booleanAttribute });
  readonly readonly = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly autocomplete = input('off');
  readonly spellcheck = input(false, { transform: booleanAttribute });
  readonly appearance = input<'default' | 'code'>('default');
  readonly size = input<'standard' | 'descriptive'>('standard');
  readonly valueChange = output<string>();
  readonly committed = output<string>();
  readonly currentValue = signal('');
  private readonly formDisabled = signal(false);
  readonly isDisabled = computed(() => this.disabled() || this.formDisabled());
  readonly control = viewChild<ElementRef<HTMLInputElement | HTMLTextAreaElement>>('control');
  readonly descriptionIds = computed(() => [this.describedBy(), this.hint() ? `${this.inputId()}-hint` : '',
    this.error() ? `${this.inputId()}-error` : ''].filter(Boolean).join(' ') || null);
  private onChange: (value: string | number | null) => void = () => {};
  private onTouched: () => void = () => {};
  private onValidatorChange = () => {};
  private composing = false;

  constructor() {
    let initialized = false;
    effect(() => {
      this.type(); this.required(); this.min(); this.max(); this.maxLength(); this.pattern();
      // FormControl validates during setup. Notify only for later input changes,
      // otherwise mounting a field emits a spurious valueChanges event.
      if (initialized) this.onValidatorChange();
      initialized = true;
    });
  }

  @Input() set value(value: string | number | null | undefined) { this.currentValue.set(String(value ?? '')); }
  writeValue(value: string | number | null): void { this.currentValue.set(String(value ?? '')); }
  registerOnChange(fn: (value: string | number | null) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  registerOnValidatorChange(fn: () => void): void { this.onValidatorChange = fn; }
  validate(control: AbstractControl): ValidationErrors | null {
    const validators = [];
    if (this.required()) validators.push(Validators.required);
    if (this.maxLength() != null) validators.push(Validators.maxLength(this.maxLength()!));
    if (this.pattern()) validators.push(Validators.pattern(this.pattern()!));
    if (this.type() === 'number') {
      if (this.min() != null) validators.push(Validators.min(Number(this.min())));
      if (this.max() != null) validators.push(Validators.max(Number(this.max())));
    }
    return Validators.compose(validators)?.(control) ?? null;
  }
  setDisabledState(disabled: boolean): void { this.formDisabled.set(disabled); }
  focus(): void { this.control()?.nativeElement.focus(); }
  touch(): void { this.onTouched(); }
  startComposition(): void { this.composing = true; }
  endComposition(event: CompositionEvent): void {
    this.composing = false;
    this.update(event);
  }
  update(event: Event): void {
    if (this.isDisabled() || this.readonly() || this.composing) return;
    const value = (event.target as HTMLInputElement | HTMLTextAreaElement).value;
    this.currentValue.set(value);
    this.onChange(this.type() === 'number' ? (value === '' ? null : parseFloat(value)) : value);
    this.valueChange.emit(value);
  }
  commit(event: Event): void {
    this.committed.emit((event.target as HTMLInputElement).value);
  }
}
