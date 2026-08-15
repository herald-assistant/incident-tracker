import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AnalysisResultHeaderComponent } from './analysis-result-header';

describe('AnalysisResultHeaderComponent', () => {
  it('should render the shared result heading, confidence and copy action', async () => {
    await TestBed.configureTestingModule({
      imports: [AnalysisResultHeaderComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(AnalysisResultHeaderComponent);
    fixture.componentRef.setInput('context', 'Change Verification: CRM-123');
    fixture.componentRef.setInput('confidence', 'medium');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.section-eyebrow')?.textContent?.trim()).toBe('Wynik');
    expect(compiled.querySelector('h3')?.textContent?.trim()).toBe('Finalna analiza');
    expect(compiled.textContent).toContain('Change Verification: CRM-123');
    expect(compiled.textContent).toContain('Confidence');
    expect(compiled.textContent).toContain('medium');
    expect(compiled.textContent).toContain('Copy result');
  });

  it('should emit copy request and show copied state', async () => {
    await TestBed.configureTestingModule({
      imports: [AnalysisResultHeaderComponent]
    }).compileComponents();

    const fixture: ComponentFixture<AnalysisResultHeaderComponent> =
      TestBed.createComponent(AnalysisResultHeaderComponent);
    const copyRequested = vi.fn();
    fixture.componentInstance.copyRequested.subscribe(copyRequested);
    fixture.detectChanges();

    fixture.nativeElement.querySelector('button')?.click();
    expect(copyRequested).toHaveBeenCalledTimes(1);

    fixture.componentRef.setInput('copied', true);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Copied');
  });

  it('should expose an optional shared Markdown download action', async () => {
    await TestBed.configureTestingModule({
      imports: [AnalysisResultHeaderComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(AnalysisResultHeaderComponent);
    const downloadRequested = vi.fn();
    fixture.componentInstance.downloadRequested.subscribe(downloadRequested);
    fixture.componentRef.setInput('downloadVisible', true);
    fixture.detectChanges();

    const button = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>('button')
    ).find((candidate) => candidate.textContent?.includes('Download Markdown'));
    button?.click();

    expect(button).toBeDefined();
    expect(downloadRequested).toHaveBeenCalledTimes(1);
  });
});
