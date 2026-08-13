import { TestBed } from '@angular/core/testing';

import { AnalysisFeatureAsideComponent } from './analysis-feature-aside';

describe('AnalysisFeatureAsideComponent', () => {
  it('should close the opened panel when user clicks outside the aside', async () => {
    await TestBed.configureTestingModule({
      imports: [AnalysisFeatureAsideComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(AnalysisFeatureAsideComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const progressTab = compiled.querySelector<HTMLButtonElement>('[aria-label="Przebieg analizy"]');
    progressTab?.click();
    fixture.detectChanges();

    expect(compiled.querySelector('.analysis-feature-aside')?.classList).toContain(
      'analysis-feature-aside--open'
    );

    compiled.querySelector<HTMLButtonElement>('.analysis-feature-aside__backdrop')?.click();
    fixture.detectChanges();

    expect(compiled.querySelector('.analysis-feature-aside')?.classList).not.toContain(
      'analysis-feature-aside--open'
    );
  });

  it('should keep the opened panel when user clicks inside the aside', async () => {
    await TestBed.configureTestingModule({
      imports: [AnalysisFeatureAsideComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(AnalysisFeatureAsideComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const progressTab = compiled.querySelector<HTMLButtonElement>('[aria-label="Przebieg analizy"]');
    progressTab?.click();
    fixture.detectChanges();

    compiled.querySelector<HTMLElement>('.analysis-feature-aside__panel')?.click();
    fixture.detectChanges();

    expect(compiled.querySelector('.analysis-feature-aside')?.classList).toContain(
      'analysis-feature-aside--open'
    );
  });
});
