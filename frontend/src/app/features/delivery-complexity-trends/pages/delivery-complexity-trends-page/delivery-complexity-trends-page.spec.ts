import { ComponentFixture, TestBed } from '@angular/core/testing';

import { buildExcelCsv } from '../../../../core/utils/csv-file.utils';
import { DeliveryComplexityTrendsPageComponent } from './delivery-complexity-trends-page';

const DCA_HEADERS = [
  'issueKey', 'issueUrl', 'summary', 'issueType', 'doneAt',
  'teamId', 'teamName', 'teamFieldId', 'mergeRequestUrls',
  'mergeRequestAuthorIds', 'mergeRequestAuthorNames',
  'deliveryUnitId', 'assessmentStatus', 'outcomeBreadth',
  'domainDecisionComplexity', 'applicationFlowComplexity',
  'boundaryAndDataComplexity', 'verificationStateSpace',
  'implementedCompatibilityScope', 'parameterizationComplexity',
  'score100', 'deliveredStoryPoints', 'pointsForAggregation',
  'timeSpentSeconds', 'originalEstimateSeconds', 'remainingEstimateSeconds',
  'timeTrackingCapturedAt'
] as const;

const DSC_HEADERS = [
  'issueKey', 'issueUrl', 'summary', 'issueType', 'doneAt',
  'teamId', 'teamName', 'teamFieldId', 'mergeRequestUrls',
  'mergeRequestAuthorIds', 'mergeRequestAuthorNames',
  'deliveryUnitId', 'assessmentStatus', 'noveltyPoints',
  'structuralAndLogicPoints', 'businessAndInvariantsPoints',
  'robustnessAndTestsPoints', 'refactorAndArchitecturePoints',
  'distributionPoints', 'finalScore', 'pointsForAggregation',
  'timeSpentSeconds', 'originalEstimateSeconds', 'remainingEstimateSeconds',
  'timeTrackingCapturedAt'
] as const;

describe('DeliveryComplexityTrendsPageComponent', () => {
  let fixture: ComponentFixture<DeliveryComplexityTrendsPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeliveryComplexityTrendsPageComponent]
    }).compileComponents();
    fixture = TestBed.createComponent(DeliveryComplexityTrendsPageComponent);
    fixture.detectChanges();
  });

  it('should import multiple reports and render matching monthly bars and table rows', async () => {
    expect(element().querySelector('.trend-empty-state')).not.toBeNull();

    await selectFiles([
      csvFile('july.csv', DCA_HEADERS, [
        row(DCA_HEADERS, {
          issueKey: 'CRM-1',
          doneAt: '2026-07-10T09:00:00+02:00',
          deliveredStoryPoints: '8',
          pointsForAggregation: '8'
        })
      ]),
      csvFile('august.csv', DCA_HEADERS, [
        row(DCA_HEADERS, {
          issueKey: 'CRM-2',
          doneAt: '2026-08-10T09:00:00+02:00',
          deliveryUnitId: 'DU-CRM-2',
          deliveredStoryPoints: '13',
          pointsForAggregation: '13'
        })
      ])
    ]);

    const compiled = element();
    expect(compiled.querySelector('.trend-import-card details')).toBeNull();
    expect(compiled.querySelector('.trend-summary-grid')).toBeNull();
    expect(compiled.textContent).not.toContain('Ostatnia zmiana (CP)');
    expect(compiled.textContent).not.toContain('Najważniejsze zmiany');
    expect(compiled.textContent).not.toContain('Status ocen');
    expect(compiled.querySelectorAll('.trend-bar')).toHaveLength(2);
    expect(compiled.querySelector('.trend-table')).toBeNull();
    expect(compiled.textContent).not.toContain('Szczegóły okresów');
    const directionLegend = compiled.querySelector<HTMLElement>('.trend-legend')!;
    expect(directionLegend.querySelector('.trend-legend__marker--first')).not.toBeNull();
    expect(directionLegend.textContent).toContain('pierwszy okres');
    expect(directionLegend.textContent).toContain('bez zmiany');
    expect(directionLegend.textContent).not.toContain('bez zmiany / pierwszy okres');
    const chartChanges = compiled.querySelectorAll<HTMLElement>('.trend-bar__change');
    expect(chartChanges[0].querySelectorAll('small')).toHaveLength(1);
    expect(chartChanges[0].textContent).toContain('pierwszy okres');
    expect(chartChanges[1].querySelectorAll('small')).toHaveLength(2);
    expect(chartChanges[1].querySelectorAll('small')[0].textContent?.trim()).toBe('+5');
    expect(chartChanges[1].querySelectorAll('small')[1].textContent?.trim()).toBe('+62,5%');
    expect(compiled.textContent).toContain('lip 2026');
    expect(compiled.textContent).toContain('sie 2026');
    expect(compiled.querySelector('.trend-filter-note')).toBeNull();
    expect(compiled.textContent).not.toContain('Kierunek nie oznacza oceny dobre/źle');
    expect(compiled.textContent).toContain('Co napędza zmianę');
    expect(compiled.textContent).toContain('Średnia ocena 0–4');
    expect(compiled.textContent).not.toContain('Największa zmiana wymiaru');
    expect(compiled.querySelector('.trend-dimension-driver')).toBeNull();
    expect(compiled.querySelectorAll('.trend-dimension-heatmap tbody tr')).toHaveLength(2);
    expect(compiled.querySelector('.trend-dimension-legend')).toBeNull();
    expect(compiled.querySelectorAll('.trend-dimension-heatmap tbody td small')).toHaveLength(0);
    expect(compiled.querySelectorAll('.trend-dimension-heatmap tbody th small')).toHaveLength(2);
    const qualityValues = Array.from(compiled.querySelectorAll('.trend-quality-grid > div'))
      .map((item) => item.textContent?.replace(/\s+/g, ' ').trim());
    expect(qualityValues).toContain('Ocenione2');
    expect(compiled.textContent).not.toContain('Wynik został policzony deterministycznie');
    expect(Array.from(
      compiled.querySelector<HTMLSelectElement>('.trend-field select')!.options
    ).map((option) => option.text.trim())).toEqual([
      'Dzień', 'Tydzień', 'Miesiąc', 'Kwartał'
    ]);

    const totalButton = Array.from(compiled.querySelectorAll<HTMLButtonElement>(
      '.trend-dimension-mode button'
    )).find((button) => button.textContent?.includes('Łączny wkład'))!;
    totalButton.click();
    fixture.detectChanges();

    expect(element().textContent).toContain('Nie są bezpośrednim rozkładem CP');
    expect(element().querySelectorAll('.trend-dimension-row')).toHaveLength(2);
  });

  it('should filter full Delivery Units by team and update the chart', async () => {
    await selectFiles([csvFile('teams.csv', DCA_HEADERS, [
      row(DCA_HEADERS, {
        issueKey: 'CRM-1',
        teamId: 'team-a',
        teamName: 'Team A',
        deliveredStoryPoints: '8',
        pointsForAggregation: '8'
      }),
      row(DCA_HEADERS, {
        issueKey: 'CRM-2',
        doneAt: '2026-08-10T09:00:00+02:00',
        teamId: 'team-b',
        teamName: 'Team B',
        deliveryUnitId: 'DU-CRM-2',
        deliveredStoryPoints: '5',
        pointsForAggregation: '5'
      })
    ])]);

    const teamSelect = Array.from(element().querySelectorAll<HTMLSelectElement>('.trend-field select'))[1];
    teamSelect.value = 'id:team-b';
    teamSelect.dispatchEvent(new Event('change', { bubbles: true }));
    fixture.detectChanges();

    expect(element().querySelector('.trend-bar__value')?.textContent?.trim()).toBe('5');
    expect(element().querySelectorAll('.trend-bar')).toHaveLength(1);
    expect(element().querySelector('.trend-dimension-heatmap tbody th small')?.textContent?.trim()).toBe(
      '1 Jira Issue w okresie'
    );
  });

  it('should render efficiency and estimate comparison when Jira time data is present', async () => {
    await selectFiles([csvFile('efficiency.csv', DCA_HEADERS, [
      row(DCA_HEADERS, {
        timeSpentSeconds: '28800',
        originalEstimateSeconds: '28800',
        remainingEstimateSeconds: '0',
        timeTrackingCapturedAt: '2026-07-11T08:00:00Z'
      }),
      row(DCA_HEADERS, {
        issueKey: 'CRM-2',
        doneAt: '2026-08-10T09:00:00+02:00',
        deliveryUnitId: 'DU-CRM-2',
        deliveredStoryPoints: '13',
        pointsForAggregation: '13',
        timeSpentSeconds: '28800',
        originalEstimateSeconds: '21600',
        remainingEstimateSeconds: '3600',
        timeTrackingCapturedAt: '2026-08-11T08:00:00Z'
      })
    ])]);

    const efficiency = element().querySelector<HTMLElement>('.trend-efficiency-panel')!;
    const estimateComparison = element().querySelector<HTMLElement>('.trend-effort-comparison')!;
    expect(efficiency).not.toBeNull();
    expect(estimateComparison).not.toBeNull();
    expect(efficiency.textContent).toContain('Efficiency');
    expect(efficiency.textContent).not.toContain('Original Estimate a Time Spent');
    expect(estimateComparison.textContent).toContain('Original Estimate a Time Spent');
    expect(estimateComparison.textContent).toContain('Odchylenie Time Spent');
    expect(estimateComparison.previousElementSibling).toBe(efficiency);
    expect(efficiency.textContent).toContain('Efficiency');
    expect(efficiency.textContent).toContain('CP/MD');
    expect(efficiency.textContent).toContain('Time Spent (MD)');
    expect(efficiency.querySelectorAll('.trend-efficiency-bar')).toHaveLength(2);
    const coverageLabels = Array.from(
      efficiency.querySelectorAll<HTMLElement>('.trend-efficiency-bar__coverage')
    );
    expect(coverageLabels.map((label) => label.textContent?.trim())).toEqual(['1 / 100%', '1 / 100%']);
    expect(coverageLabels[0].title).toBe('1 Jira Issue · 100% pokrycia');
    const efficiencyDetails = efficiency.querySelector<HTMLDetailsElement>('.trend-efficiency-details')!;
    expect(efficiencyDetails).not.toBeNull();
    expect(efficiencyDetails.open).toBe(false);
    expect(efficiencyDetails.querySelector('summary')?.textContent).toContain('Pokaż dokładne wartości okresów');
    expect(efficiency.querySelectorAll('.trend-efficiency-table tbody tr')).toHaveLength(2);
    const varianceRows = Array.from(
      estimateComparison.querySelectorAll<HTMLElement>('.trend-estimate-variance-row')
    );
    expect(varianceRows).toHaveLength(2);
    expect(estimateComparison.querySelector('.trend-effort-bars')).toBeNull();
    expect(varianceRows[0].title).toBe(
      'Original Estimate: 1 MD · Time Spent: 1 MD · Odchylenie: 0% · 1 Jira Issue'
    );
    expect(varianceRows[1].textContent).toContain('+33,3%');
    expect(
      estimateComparison.querySelector('.trend-estimate-variance-chart')?.getAttribute('aria-label')
    ).toContain('Wartość ujemna oznacza Time Spent poniżej estymaty');
    expect(efficiency.querySelector('.trend-summary-card strong')?.textContent?.trim()).toBe('10,5');
    expect(efficiency.querySelector('input[type="number"]')).toBeNull();
    expect(efficiency.textContent).toContain('1 MD odpowiada 8 godzinom');
    expect(efficiency.textContent).toContain('1 MD = 8 h');

    fixture.componentInstance.authorControl.setValue('id:101');
    fixture.detectChanges();
    expect(element().querySelector('.trend-efficiency-panel')?.textContent).toContain(
      'Nie przypisuje Time Spent ani Efficiency do tej osoby'
    );
  });

  it('should filter whole Delivery Units by multiple issue types without double counting', async () => {
    await selectFiles([csvFile('types.csv', DCA_HEADERS, [
      row(DCA_HEADERS, {
        issueKey: 'CRM-1',
        issueType: 'Bug',
        deliveryUnitId: 'DU-SHARED',
        deliveredStoryPoints: '8',
        pointsForAggregation: ''
      }),
      row(DCA_HEADERS, {
        issueKey: 'CRM-2',
        issueType: 'Story',
        doneAt: '2026-07-02T09:00:00+02:00',
        deliveryUnitId: 'DU-SHARED',
        deliveredStoryPoints: '8',
        pointsForAggregation: '8'
      }),
      row(DCA_HEADERS, {
        issueKey: 'CRM-3',
        issueType: 'Task',
        deliveryUnitId: 'DU-TASK',
        deliveredStoryPoints: '5',
        pointsForAggregation: '5'
      })
    ])]);

    const multiselect = element().querySelector<HTMLDetailsElement>('.trend-multiselect')!;
    const checkboxes = Array.from(multiselect.querySelectorAll<HTMLInputElement>('input'));
    const bug = checkboxes.find((input) => input.parentElement?.textContent?.includes('Bug'))!;
    const story = checkboxes.find((input) => input.parentElement?.textContent?.includes('Story'))!;

    expect(multiselect.querySelector('summary')?.textContent).toContain('Wszystkie typy');
    expect(bug.checked).toBe(false);
    expect(bug.parentElement?.querySelector('small')?.textContent?.trim()).toBe('1 Jira Issue');

    bug.click();
    fixture.detectChanges();

    expect(element().querySelector('.trend-bar__value')?.textContent?.trim()).toBe('8');
    expect(bug.checked).toBe(true);
    expect(multiselect.querySelector('summary')?.textContent).toContain('Bug');
    story.click();
    fixture.detectChanges();

    expect(element().querySelector('.trend-bar__value')?.textContent?.trim()).toBe('8');
    expect(multiselect.querySelector('summary')?.textContent).toContain('2 wybrane typy');
    const reset = element().querySelector<HTMLButtonElement>('[aria-label="Wyczyść filtry"]')!;
    reset.click();
    fixture.detectChanges();

    expect(element().querySelector('.trend-bar__value')?.textContent?.trim()).toBe('13');
    expect(multiselect.querySelector('summary')?.textContent).toContain('Wszystkie typy');
  });

  it('should default Scope reports to the additive dimension contribution', async () => {
    await selectFiles([csvFile('scope.csv', DSC_HEADERS, [row(DSC_HEADERS)])]);

    const buttons = Array.from(element().querySelectorAll<HTMLButtonElement>(
      '.trend-dimension-mode button'
    ));
    const totalButton = buttons.find((button) => button.textContent?.includes('Łączny wkład'))!;
    const averageButton = buttons.find(
      (button) => button.textContent?.includes('Średnia na Jira Issue')
    )!;

    expect(totalButton.getAttribute('aria-pressed')).toBe('true');
    expect(element().textContent).toContain('sumują się do Complexity Points');
    expect(element().querySelectorAll('.trend-dimension-row')).toHaveLength(1);

    averageButton.click();
    fixture.detectChanges();

    expect(element().textContent).toContain('Średni punktowy wkład wymiaru');
    expect(element().querySelectorAll('.trend-dimension-heatmap tbody tr')).toHaveLength(1);
  });

  it('should keep the previous dataset when a later selection mixes assessment formats', async () => {
    const dca = csvFile('dca.csv', DCA_HEADERS, [row(DCA_HEADERS)]);
    await selectFiles([dca]);
    expect(element().querySelector('.trend-bar__value')?.textContent?.trim()).toBe('8');

    const scope = csvFile('scope.csv', DSC_HEADERS, [row(DSC_HEADERS)]);
    await selectFiles([dca, scope]);

    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      'Wszystkie pliki musza pochodzic z tego samego assessmentu'
    );
    expect(element().querySelector('.trend-bar__value')?.textContent?.trim()).toBe('8');
  });

  it('should expose legacy author limitations and clear the local dataset', async () => {
    const headers = DCA_HEADERS.filter(
      (header) => !['mergeRequestAuthorIds', 'mergeRequestAuthorNames'].includes(header)
    );
    await selectFiles([csvFile('legacy.csv', headers, [row(headers)])]);

    expect(element().textContent).toContain('Brak danych o autorach');
    const qualityValues = Array.from(element().querySelectorAll('.trend-quality-grid > div'))
      .map((item) => item.textContent?.replace(/\s+/g, ' ').trim());
    expect(qualityValues).toContain('Starsze pliki bez autorów1');

    const clearButton = Array.from(element().querySelectorAll<HTMLButtonElement>('button'))
      .find((button) => button.textContent?.includes('Wyczyść dane'));
    clearButton?.click();
    fixture.detectChanges();

    expect(element().querySelector('.trend-empty-state')).not.toBeNull();
    expect(element().querySelector('.trend-summary-grid')).toBeNull();
  });

  async function selectFiles(files: File[]): Promise<void> {
    const input = element().querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { configurable: true, value: files });
    input.dispatchEvent(new Event('change', { bubbles: true }));
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }
});

function row(headers: readonly string[], overrides: Record<string, string> = {}): string[] {
  const values: Record<string, string> = {
    issueKey: 'CRM-1',
    issueUrl: 'https://jira.example.com/browse/CRM-1',
    summary: 'Obsługa płatności',
    issueType: 'Story',
    doneAt: '2026-07-01T09:00:00+02:00',
    teamId: 'team-a',
    teamName: 'Team A',
    teamFieldId: 'customfield_10000',
    mergeRequestUrls: 'https://gitlab.example.com/mr/7',
    mergeRequestAuthorIds: '101',
    mergeRequestAuthorNames: 'Anna Nowak',
    deliveryUnitId: 'DU-CRM-1',
    assessmentStatus: 'COMPLETED',
    outcomeBreadth: '2',
    domainDecisionComplexity: '3',
    applicationFlowComplexity: '3',
    boundaryAndDataComplexity: '2',
    verificationStateSpace: '3',
    implementedCompatibilityScope: '2',
    parameterizationComplexity: '3',
    score100: '72,5',
    deliveredStoryPoints: '8',
    noveltyPoints: '20',
    structuralAndLogicPoints: '20',
    businessAndInvariantsPoints: '20',
    robustnessAndTestsPoints: '20',
    refactorAndArchitecturePoints: '20',
    distributionPoints: '20',
    finalScore: '120',
    pointsForAggregation: headers.includes('finalScore') ? '120' : '8',
    timeSpentSeconds: '',
    originalEstimateSeconds: '',
    remainingEstimateSeconds: '',
    timeTrackingCapturedAt: ''
  };
  return headers.map((header) => overrides[header] ?? values[header] ?? '');
}

function csvFile(name: string, headers: readonly string[], rows: string[][]): File {
  const content = buildExcelCsv([headers, ...rows]);
  const file = new File([content], name, { type: 'text/csv' });
  Object.defineProperty(file, 'text', {
    configurable: true,
    value: () => Promise.resolve(content)
  });
  return file;
}
