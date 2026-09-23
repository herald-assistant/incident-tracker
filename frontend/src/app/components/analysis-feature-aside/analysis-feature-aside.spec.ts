import { TestBed } from '@angular/core/testing';

import { AnalysisFeatureAsideComponent } from './analysis-feature-aside';

describe('AnalysisFeatureAsideComponent', () => {
  it('should aggregate Copilot credits with follow-up usage and convert them at 100 credits per USD', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisFeatureAsideComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisFeatureAsideComponent);
    const usage = {
      inputTokens: 100, outputTokens: 20, cacheReadTokens: 30, cacheWriteTokens: 5,
      totalTokens: 120, aiCredits: 1.25, apiDurationMs: 500, apiCallCount: 1,
      model: 'model-a', contextTokenLimit: null, contextCurrentTokens: null, contextMessages: null, reasoningTokens: 6
    };
    fixture.componentRef.setInput('usage', usage);
    fixture.componentRef.setInput('chatMessages', [{ role: 'ASSISTANT', usage: { ...usage, aiCredits: 0.75, reasoningTokens: 4 } }]);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    compiled.querySelector<HTMLButtonElement>('[aria-label="Koszt AI"]')?.click();
    fixture.detectChanges();

    const cost = compiled.querySelector('.analysis-feature-aside__cost') as HTMLElement;
    expect(cost.querySelector('.analysis-feature-aside__cost-credits')?.textContent).toContain('2,00');
    expect(cost.textContent).toContain('$0.02');
    expect(cost.textContent).toContain('240');
    expect(cost.textContent).toContain('model-a');
    const tokenCards = cost.querySelectorAll('.analysis-feature-aside__token-card');
    expect(tokenCards.length).toBe(2);
    expect(tokenCards[0]?.textContent).toContain('200');
    expect(tokenCards[1]?.textContent).toContain('40');
    const tokenDetails = cost.querySelectorAll('.analysis-feature-aside__token-details > div');
    expect(tokenDetails.length).toBe(3);
    expect(tokenDetails[0]?.textContent).toContain('Cache read');
    expect(tokenDetails[0]?.textContent).toContain('60');
    expect(tokenDetails[1]?.textContent).toContain('Cache write');
    expect(tokenDetails[1]?.textContent).toContain('10');
    expect(tokenDetails[2]?.textContent).toContain('Reasoning');
    expect(tokenDetails[2]?.textContent).toContain('10');
    expect(cost.textContent).toContain('reasoning są pokazane osobno');
  });

  it('should distinguish zero credits from incomplete Copilot credit data', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisFeatureAsideComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisFeatureAsideComponent);
    const usage = {
      inputTokens: 0, outputTokens: 0, cacheReadTokens: 0, cacheWriteTokens: 0,
      totalTokens: 0, aiCredits: 0, apiDurationMs: 0, apiCallCount: 1,
      model: 'model-a', contextTokenLimit: null, contextCurrentTokens: null, contextMessages: null, reasoningTokens: null
    };
    fixture.componentRef.setInput('usage', usage);
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[aria-label="Koszt AI"]')?.click();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.analysis-feature-aside__cost-credits')?.textContent).toContain('0,00');

    fixture.componentRef.setInput('usage', { ...usage, aiCredits: null, cacheReadTokens: null, cacheWriteTokens: null });
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('nie przekazał danych o kredytach');
    expect(compiled.querySelectorAll('.analysis-feature-aside__token-details > div')[0]?.textContent).toContain('Brak danych');
    expect(compiled.querySelectorAll('.analysis-feature-aside__token-details > div')[1]?.textContent).toContain('Brak danych');
    expect(compiled.querySelectorAll('.analysis-feature-aside__token-details > div')[2]?.textContent).toContain('Brak danych');
  });

  it('should round credits and USD to exactly two decimal places', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisFeatureAsideComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisFeatureAsideComponent);
    fixture.componentRef.setInput('usage', {
      inputTokens: 100, outputTokens: 20, cacheReadTokens: 0, cacheWriteTokens: 0,
      totalTokens: 120, aiCredits: 78.7542, apiDurationMs: 500, apiCallCount: 1,
      model: 'model-a', contextTokenLimit: null, contextCurrentTokens: null,
      contextMessages: null, reasoningTokens: 0
    });
    fixture.detectChanges();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('[aria-label="Koszt AI"]')?.click();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.analysis-feature-aside__cost-credits')?.textContent).toContain('78,75');
    expect(compiled.querySelector('.analysis-feature-aside__cost-equivalent')?.textContent).toContain('$0.79');
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
