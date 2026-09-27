import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AiModelsPageComponent } from './ai-models-page';

describe('AiModelsPageComponent', () => {
  let fixture: ComponentFixture<AiModelsPageComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AiModelsPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]
    }).compileComponents();
    fixture = TestBed.createComponent(AiModelsPageComponent);
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('shows SDK token rates and a separate long context tier', () => {
    http.expectOne('/api/analysis/ai/options').flush({
      defaultModel: 'crm-versatile', defaultReasoningEffort: '', defaultReasoningEfforts: [],
      models: [
        {
          id: 'crm-versatile', name: 'CRM Versatile', supportsReasoningEffort: false,
          reasoningEfforts: [], defaultReasoningEffort: '', modelPickerCategory: 'versatile',
          pricing: {
            defaultRates: { input: 200, cachedInput: 20, cacheWrite: 250, output: 1200 },
            longContextRates: { input: 400, cachedInput: 40, cacheWrite: 500, output: 1800 },
            longContextThresholdTokens: 272000
          }
        },
        {
          id: 'crm-new', name: 'CRM New', supportsReasoningEffort: false,
          reasoningEfforts: [], defaultReasoningEffort: '', pricing: null
        },
        {
          id: 'crm-same-rates', name: 'CRM Same Rates', supportsReasoningEffort: false,
          reasoningEfforts: [], defaultReasoningEffort: '', pricing: {
            defaultRates: { input: 100, cachedInput: 10, cacheWrite: 0, output: 500 },
            longContextRates: { input: 100, cachedInput: 10, cacheWrite: 0, output: 500 },
            longContextThresholdTokens: 200000
          }
        }
      ]
    });
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.ai-models-toolbar .ai-models-unit strong')?.textContent)
      .toBe('AI credits za 1 mln tokenów');
    expect([...element.querySelectorAll('.ai-models-table thead th')]
      .map((header) => header.textContent?.trim()))
      .toEqual(['Model', 'Kategoria', 'Tier', 'Wejście', 'Odczyt z cache', 'Zapis do cache', 'Odpowiedź']);
    const rows = element.querySelectorAll('.ai-models-table tbody tr');
    expect(rows).toHaveLength(4);
    expect([...rows].filter((row) => row.textContent?.includes('CRM Same Rates'))).toHaveLength(1);
    const standardRow = [...rows].find((row) => row.textContent?.includes('CRM Versatile') && row.textContent?.includes('Default'));
    const longContextRow = [...rows].find((row) => row.textContent?.includes('Long context'));
    const missingPriceRow = [...rows].find((row) => row.textContent?.includes('CRM New'));
    expect(standardRow?.querySelector('.ai-models-table__category-pill')?.textContent).toBe('Uniwersalny');
    expect(standardRow?.querySelector('.ai-models-table__tier')?.textContent).toBe('Default');
    expect(standardRow?.querySelectorAll('td')[5].textContent?.trim()).toBe('1200');
    expect(longContextRow?.querySelector('.ai-models-table__tier')?.textContent).toBe('Long context');
    expect(longContextRow?.querySelector('.ai-models-table__tier')?.getAttribute('title')?.replace(/\s/g, ''))
      .toContain('272000');
    expect(longContextRow?.querySelectorAll('td')[5].textContent?.trim()).toBe('1800');
    expect(missingPriceRow?.textContent).toContain('—');
    expect(element.textContent).not.toContain('mnożnik premium');

    const search = element.querySelector<HTMLInputElement>('#aiModelsSearch')!;
    search.value = 'new';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(element.querySelectorAll('.ai-models-table tbody tr')).toHaveLength(1);
  });

  it('links to the official price list when the SDK has no rates', () => {
    http.expectOne('/api/analysis/ai/options').flush({
      defaultModel: '', defaultReasoningEffort: '', defaultReasoningEfforts: [],
      models: [{ id: 'crm-new', name: 'CRM New', supportsReasoningEffort: false,
        reasoningEfforts: [], defaultReasoningEffort: '', pricing: null }]
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Katalog nie zwrócił stawek');
    expect(element.querySelector('.ai-models-table')).toBeNull();
    expect(element.querySelector('a[href="https://docs.github.com/en/copilot/reference/copilot-billing/models-and-pricing"]')).not.toBeNull();
  });

  it('offers recovery when the live catalog cannot be read', () => {
    http.expectOne('/api/analysis/ai/options').flush(
      { code: 'COPILOT_PAT_REQUIRED', message: 'Token required' },
      { status: 401, statusText: 'Unauthorized' }
    );
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Nie udało się pobrać modeli');
    expect(element.querySelector('a[href="/workspace-settings"]')).not.toBeNull();

    element.querySelector<HTMLButtonElement>('.ai-models-state button')!.click();
    http.expectOne('/api/analysis/ai/options').flush({
      defaultModel: '', defaultReasoningEffort: '', defaultReasoningEfforts: [], models: []
    });
    fixture.detectChanges();
    expect(element.textContent).toContain('Brak modeli w katalogu');
  });
});
