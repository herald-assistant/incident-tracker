import { Component } from '@angular/core';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { By } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TextFieldComponent } from './text-field';

@Component({
  imports: [TextFieldComponent, ReactiveFormsModule],
  template: `<app-text-field inputId="crm-field" label="Opis CRM" [type]="type" [formControl]="control"
    [rows]="5" [maxLength]="4000" [min]="min" [max]="max" hint="Kontekst klienta." [error]="error"
    [readonly]="readonly" [autosize]="autosize" [minRows]="3" [maxRows]="10" list="crm-suggestions"
    (committed)="committed = $event" />`
})
class Host {
  control = new FormControl<string | number | null>('CRM');
  type: 'text' | 'textarea' | 'date' | 'number' | 'password' = 'text';
  min = '2026-10-01';
  max = '2026-10-31';
  error = '';
  committed = '';
  readonly = false;
  autosize = false;
}

describe('TextFieldComponent', () => {
  beforeEach(() => TestBed.configureTestingModule({ imports: [Host] }));

  it('binds FormControl value, touched state and disabled state', () => {
    const fixture = TestBed.createComponent(Host);
    const changed = vi.fn();
    fixture.componentInstance.control.valueChanges.subscribe(changed);
    fixture.detectChanges();
    expect(changed).not.toHaveBeenCalled();
    expect(fixture.componentInstance.control.pristine).toBe(true);
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.value).toBe('CRM');
    input.value = 'CRM — kontakt';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    expect(fixture.componentInstance.control.value).toBe('CRM — kontakt');
    expect(fixture.componentInstance.control.touched).toBe(true);
    fixture.componentInstance.control.disable();
    fixture.detectChanges();
    expect(input.disabled).toBe(true);
  });

  it('retains textarea limits and associates its label, hint and error', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.type = 'textarea';
    fixture.componentInstance.error = 'Podaj kontekst klienta.';
    fixture.detectChanges();
    const textarea = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    expect(textarea.rows).toBe(5);
    expect(textarea.maxLength).toBe(4000);
    expect(textarea.getAttribute('aria-describedby')).toBe('crm-field-hint crm-field-error');
    expect(textarea.getAttribute('aria-invalid')).toBe('true');
    fixture.nativeElement.querySelector('label').click();
    expect(document.activeElement).toBe(textarea);
  });

  it('keeps date strings unchanged and commits only on change, preserving min/max', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.type = 'date';
    fixture.componentInstance.control.setValue('2026-10-25');
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.type).toBe('date');
    expect(input.value).toBe('2026-10-25');
    expect(input.min).toBe('2026-10-01');
    expect(input.max).toBe('2026-10-31');
    input.value = '2026-10-26';
    input.dispatchEvent(new Event('input'));
    expect(fixture.componentInstance.control.value).toBe('2026-10-26');
    expect(fixture.componentInstance.committed).toBe('');
    input.dispatchEvent(new Event('change'));
    expect(fixture.componentInstance.committed).toBe('2026-10-26');
  });

  it('keeps number and null values, validates limits and reacts to a changed limit', async () => {
    const fixture = TestBed.createComponent(Host);
    const host = fixture.componentInstance;
    host.type = 'number'; host.min = '1'; host.max = '10'; host.control.setValue(5);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.value).toBe('5');
    input.value = '12'; input.dispatchEvent(new Event('input'));
    expect(host.control.value).toBe(12);
    expect(host.control.hasError('max')).toBe(true);
    host.max = '20'; fixture.detectChanges(); await fixture.whenStable();
    expect(host.control.valid).toBe(true);
    input.value = ''; input.dispatchEvent(new Event('input'));
    expect(host.control.value).toBeNull();
  });

  it('preserves password visibility, datalist and readonly behavior', () => {
    const fixture = TestBed.createComponent(Host);
    const host = fixture.componentInstance;
    host.type = 'password'; fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    expect(input.type).toBe('password');
    expect(input.getAttribute('list')).toBe('crm-suggestions');
    host.type = 'text'; host.readonly = true; fixture.changeDetectorRef.markForCheck(); fixture.detectChanges();
    expect(input.type).toBe('text');
    expect(input.readOnly).toBe(true);
    expect(input.value).toBe('CRM');
    input.value = 'Unexpected edit'; input.dispatchEvent(new Event('input'));
    expect(host.control.value).toBe('CRM');
  });

  it('passes autosize row limits to the shared textarea implementation', () => {
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.type = 'textarea'; fixture.componentInstance.autosize = true;
    fixture.detectChanges();
    const autosize = fixture.debugElement.query(By.directive(CdkTextareaAutosize)).injector.get(CdkTextareaAutosize);
    expect(autosize.enabled).toBe(true);
    expect(autosize.minRows).toBe(3);
    expect(autosize.maxRows).toBe(10);
    // CDK measures from one native row; minRows/maxRows determine the visible height.
    expect(fixture.nativeElement.querySelector('textarea').rows).toBe(1);
  });

  it('does not publish partial IME composition', () => {
    const fixture = TestBed.createComponent(Host); fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.dispatchEvent(new CompositionEvent('compositionstart'));
    input.value = 'CRM customer'; input.dispatchEvent(new Event('input'));
    expect(fixture.componentInstance.control.value).toBe('CRM');
    input.dispatchEvent(new CompositionEvent('compositionend'));
    expect(fixture.componentInstance.control.value).toBe('CRM customer');
  });
});
