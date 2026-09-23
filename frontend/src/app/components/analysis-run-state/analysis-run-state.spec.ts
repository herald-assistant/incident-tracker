import { TestBed } from '@angular/core/testing';

import { AnalysisRunStateComponent } from './analysis-run-state';

describe('AnalysisRunStateComponent', () => {
  it('renders a compact pending state and a distinct unavailable state', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisRunStateComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisRunStateComponent);
    fixture.componentRef.setInput('title', 'Analiza widoku CRM');
    fixture.componentRef.setInput('description', 'Trwa przygotowanie raportu.');
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[role="status"] .analysis-run-state__spinner')).not.toBeNull();
    expect(host.textContent).toContain('Analiza widoku CRM');

    fixture.componentRef.setInput('pending', false);
    fixture.componentRef.setInput('icon', 'report_off');
    fixture.detectChanges();
    expect(host.querySelector('[role="alert"] .analysis-run-state__spinner')).toBeNull();
    expect(host.querySelector('.analysis-run-state__icon')?.textContent?.trim()).toBe('report_off');
  });
});
