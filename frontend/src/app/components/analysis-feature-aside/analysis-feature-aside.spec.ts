import { TestBed } from '@angular/core/testing';

import { AnalysisFeatureAsideComponent } from './analysis-feature-aside';

describe('AnalysisFeatureAsideComponent', () => {
  it('should aggregate Copilot credits with follow-up usage and convert them at 100 credits per USD', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisFeatureAsideComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisFeatureAsideComponent);
    const usage = {
      inputTokens: 100, outputTokens: 20, cacheReadTokens: 0, cacheWriteTokens: 0,
      totalTokens: 120, aiCredits: 1.25, apiDurationMs: 500, apiCallCount: 1,
      model: 'model-a', contextTokenLimit: null, contextCurrentTokens: null, contextMessages: null
    };
    fixture.componentRef.setInput('usage', usage);
    fixture.componentRef.setInput('chatMessages', [{ role: 'ASSISTANT', usage: { ...usage, aiCredits: 0.75 } }]);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    compiled.querySelector<HTMLButtonElement>('[aria-label="Koszt AI"]')?.click();
    fixture.detectChanges();

    const cost = compiled.querySelector('.analysis-feature-aside__cost') as HTMLElement;
    expect(cost.textContent).toContain('2,00 kredytów');
    expect(cost.textContent).toContain('$0.02');
    expect(cost.textContent).toContain('240');
    expect(cost.textContent).toContain('model-a');
  });

  it('should distinguish zero credits from incomplete Copilot credit data', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisFeatureAsideComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisFeatureAsideComponent);
    const usage = {
      inputTokens: 0, outputTokens: 0, cacheReadTokens: 0, cacheWriteTokens: 0,
      totalTokens: 0, aiCredits: 0, apiDurationMs: 0, apiCallCount: 1,
      model: 'model-a', contextTokenLimit: null, contextCurrentTokens: null, contextMessages: null
    };
    fixture.componentRef.setInput('usage', usage);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[aria-label="Koszt AI"]')?.click();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('0,00 kredytów');

    fixture.componentRef.setInput('usage', { ...usage, aiCredits: null });
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('nie przekazał pełnych danych');
  });

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
