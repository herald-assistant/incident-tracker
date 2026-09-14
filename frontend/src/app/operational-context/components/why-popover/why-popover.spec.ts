import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MatTooltip } from '@angular/material/tooltip';

import { WhyPopoverComponent } from './why-popover';

describe('WhyPopoverComponent', () => {
  it('should show reasons and warnings', async () => {
    await TestBed.configureTestingModule({
      imports: [WhyPopoverComponent],
      providers: [provideAnimationsAsync('noop')]
    }).compileComponents();

    const fixture = TestBed.createComponent(WhyPopoverComponent);
    fixture.componentRef.setInput('title', 'Dlaczego?');
    fixture.componentRef.setInput('summary', 'Dopasowano nazwę usługi.');
    fixture.componentRef.setInput('tooltipText', 'Pokaż podstawę dopasowania.');
    fixture.componentRef.setInput('confidence', 'high');
    fixture.componentRef.setInput('reasons', [
      { label: 'serviceName', detail: 'crm-contact-service matched exactly.', strength: 'strong' }
    ]);
    fixture.componentRef.setInput('warnings', ['No partner team found.']);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Dlaczego?');
    expect(compiled.textContent).toContain('Dopasowano nazwę usługi.');
    expect(compiled.textContent).toContain('Uzasadnienie');
    expect(compiled.textContent).toContain('Uwagi');
    expect(fixture.debugElement.query(By.directive(MatTooltip)).injector.get(MatTooltip).message)
      .toBe('Pokaż podstawę dopasowania.');
    expect(compiled.textContent).toContain('crm-contact-service matched exactly.');
    expect(compiled.textContent).toContain('No partner team found.');
  });
});
