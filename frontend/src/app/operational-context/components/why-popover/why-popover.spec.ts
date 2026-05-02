import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { WhyPopoverComponent } from './why-popover';

describe('WhyPopoverComponent', () => {
  it('should show reasons and warnings', async () => {
    await TestBed.configureTestingModule({
      imports: [WhyPopoverComponent],
      providers: [provideAnimationsAsync('noop')]
    }).compileComponents();

    const fixture = TestBed.createComponent(WhyPopoverComponent);
    fixture.componentRef.setInput('title', 'Why this?');
    fixture.componentRef.setInput('summary', 'Matched serviceName.');
    fixture.componentRef.setInput('confidence', 'high');
    fixture.componentRef.setInput('reasons', [
      { label: 'serviceName', detail: 'app-core matched exactly.', strength: 'strong' }
    ]);
    fixture.componentRef.setInput('warnings', ['No partner team found.']);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Why this?');
    expect(compiled.textContent).toContain('Matched serviceName.');
    expect(compiled.textContent).toContain('app-core matched exactly.');
    expect(compiled.textContent).toContain('No partner team found.');
  });
});
