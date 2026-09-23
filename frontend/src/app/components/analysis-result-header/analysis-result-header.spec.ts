import { TestBed } from '@angular/core/testing';

import { AnalysisResultHeaderComponent } from './analysis-result-header';

describe('AnalysisResultHeaderComponent', () => {
  it('renders the result heading, confidence and shared share action', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisResultHeaderComponent] }).compileComponents();

    const fixture = TestBed.createComponent(AnalysisResultHeaderComponent);
    fixture.componentRef.setInput('context', 'Change Verification: CRM-123');
    fixture.componentRef.setInput('confidence', 'medium');
    fixture.componentRef.setInput('shareDocument', {
      fileName: 'crm.md', markdown: '# CRM', markdownWithMeta: '# CRM\n\n## Meta'
    });
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.section-eyebrow')?.textContent?.trim()).toBe('Wynik');
    expect(compiled.querySelector('h3')?.textContent?.trim()).toBe('Finalna analiza');
    expect(compiled.textContent).toContain('Change Verification: CRM-123');
    expect(compiled.textContent).toContain('medium');
    expect(compiled.querySelector('button[aria-label="Udostępnij wynik"]')).not.toBeNull();
    expect(compiled.querySelector('.analysis-result-header__partial-notice')).toBeNull();
  });

  it('shows an accessible partial-result hint only when supplied', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisResultHeaderComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisResultHeaderComponent);
    fixture.componentRef.setInput('confidence', 'high');
    fixture.componentRef.setInput('partialNotice', 'Raport jest częściowy.');
    fixture.detectChanges();

    const hint = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.analysis-result-header__partial-notice');
    expect(hint?.getAttribute('aria-label')).toBe('Raport jest częściowy.');
    expect(hint?.getAttribute('tabindex')).toBe('0');
  });
});
