import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatSelectHarness } from '@angular/material/select/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ChoiceOption, ChoiceSelectComponent, ChoiceOptionComponent } from './choice-select';

@Component({
  imports: [ChoiceSelectComponent, ReactiveFormsModule],
  template: `<app-choice-select inputId="crm-model" label="Model AI" tooltip="Model analizy CRM."
    hint="Wybierz model." [error]="error()" [options]="options()" [formControl]="control" />`
})
class Host {
  readonly control = new FormControl('crm-standard', { nonNullable: true });
  error = signal('');
  options = signal<ChoiceOption[]>([
    { value: '', label: 'Domyślny' },
    { value: 'crm-standard', label: 'CRM Standard', hint: 'Zwykła analiza klienta.' },
    { value: 'crm-detail', label: 'CRM Detail', hint: 'Szczegółowa analiza klienta.' },
    { value: 'crm-future', label: 'CRM Future', disabled: true, badge: 'Soon' }
  ]);
}

@Component({
  imports: [ChoiceSelectComponent, ChoiceOptionComponent],
  template: `<app-choice-select label="Dodaj zespół" resetAfterSelection [disabled]="disabled" (valueChange)="added.push($event)">
    <app-choice-option value="" label="Wybierz zespół" />
    @for (option of options(); track option.value) {
      <app-choice-option [value]="option.value" [label]="option.label" [disabled]="option.disabled ?? false" />
    }
  </app-choice-select>`
})
class DynamicHost {
  options = signal<ChoiceOption[]>([]);
  disabled = false;
  added: string[] = [];
}

describe('ChoiceSelect declarative options', () => {
  it('loads dynamic options and resets an add-reference picker after each selection', async () => {
    await TestBed.configureTestingModule({ imports: [DynamicHost], providers: [provideNoopAnimations()] }).compileComponents();
    const fixture = TestBed.createComponent(DynamicHost); fixture.detectChanges();
    const select = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSelectHarness);
    expect(await select.getValueText()).toBe('Wybierz zespół');
    fixture.componentInstance.options.set([
      { value: 'crm-team', label: 'CRM Team' }, { value: 'crm-inactive', label: 'CRM Inactive', disabled: true }
    ]);
    fixture.detectChanges();
    await select.open();
    const options = await select.getOptions({ text: 'CRM Inactive' });
    expect(await options[0].isDisabled()).toBe(true);
    await select.clickOptions({ text: 'CRM Team' });
    expect(fixture.componentInstance.added).toEqual(['crm-team']);
    expect(await select.getValueText()).toBe('Wybierz zespół');
    await select.clickOptions({ text: 'CRM Team' });
    expect(fixture.componentInstance.added).toEqual(['crm-team', 'crm-team']);
    fixture.componentInstance.disabled = true; fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
    expect(await select.isDisabled()).toBe(true);
  });
});

describe('ChoiceSelectComponent', () => {
  let fixture: ComponentFixture<Host>;
  let select: MatSelectHarness;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host], providers: [provideNoopAnimations()] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    select = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatSelectHarness);
  });

  it('connects label, hint and error to the focusable control', async () => {
    const control = fixture.nativeElement.querySelector('[role="combobox"]') as HTMLElement;
    expect(control.getAttribute('aria-label')).toBe('Model AI');
    expect(control.getAttribute('aria-describedby')).toContain('crm-model-hint');
    fixture.componentInstance.error.set('Wybierz dostępny model.');
    fixture.detectChanges();
    expect(control.getAttribute('aria-describedby')).toContain('crm-model-error');
    expect(control.getAttribute('aria-invalid')).toBe('true');
    fixture.nativeElement.querySelector('label').click();
    expect(document.activeElement).toBe(control);
  });

  it('writes values through the form, allows the empty default and marks the field touched', async () => {
    const values: string[] = [];
    fixture.componentInstance.control.valueChanges.subscribe(value => values.push(value));
    await select.open();
    await select.clickOptions({ text: /CRM Detail/ });
    expect(fixture.componentInstance.control.value).toBe('crm-detail');
    expect(fixture.componentInstance.control.touched).toBe(true);
    expect(values).toEqual(['crm-detail']);
    await select.open();
    await select.clickOptions({ text: 'Domyślny' });
    expect(fixture.componentInstance.control.value).toBe('');
    expect(await select.getValueText()).toBe('Domyślny');
  });

  it('honors disabled options and form-level locking', async () => {
    await select.open();
    const future = (await select.getOptions({ text: /CRM Future/ }))[0];
    expect(await future.isDisabled()).toBe(true);
    await select.close();
    fixture.componentInstance.control.disable();
    fixture.detectChanges();
    expect(await select.isDisabled()).toBe(true);
    fixture.componentInstance.control.enable();
    fixture.detectChanges();
    expect(await select.isDisabled()).toBe(false);
  });

  it('preserves a restored value while options arrive, without emitting another change', async () => {
    fixture.componentInstance.options.set([]);
    fixture.componentInstance.control.setValue('crm-detail');
    fixture.detectChanges();
    const changed = vi.fn();
    fixture.componentInstance.control.valueChanges.subscribe(changed);
    fixture.componentInstance.options.set([{ value: 'crm-detail', label: 'CRM Detail' }]);
    fixture.detectChanges();
    expect(await select.getValueText()).toBe('CRM Detail');
    expect(fixture.componentInstance.control.value).toBe('crm-detail');
    expect(changed).not.toHaveBeenCalled();
  });

  it('supports keyboard selection and Escape without committing a pending option', async () => {
    const control = fixture.nativeElement.querySelector('[role="combobox"]') as HTMLElement;
    control.focus();
    control.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', keyCode: 40, altKey: true, bubbles: true }));
    fixture.detectChanges();
    expect(await select.isOpen()).toBe(true);
    control.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', keyCode: 40, bubbles: true }));
    control.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));
    fixture.detectChanges();
    expect(await select.isOpen()).toBe(false);
    expect(fixture.componentInstance.control.value).toBe('crm-standard');
    expect(document.activeElement).toBe(control);
    control.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', keyCode: 40, altKey: true, bubbles: true }));
    control.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', keyCode: 40, bubbles: true }));
    control.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true }));
    fixture.detectChanges();
    expect(fixture.componentInstance.control.value).toBe('crm-detail');
  });
});
