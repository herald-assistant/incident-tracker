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
  'score100', 'deliveredStoryPoints', 'pointsForAggregation'
] as const;

const DSC_HEADERS = [
  'issueKey', 'issueUrl', 'summary', 'issueType', 'doneAt',
  'teamId', 'teamName', 'teamFieldId', 'mergeRequestUrls',
  'mergeRequestAuthorIds', 'mergeRequestAuthorNames',
  'deliveryUnitId', 'assessmentStatus', 'noveltyPoints',
  'structuralAndLogicPoints', 'businessAndInvariantsPoints',
  'robustnessAndTestsPoints', 'refactorAndArchitecturePoints',
  'distributionPoints', 'finalScore', 'pointsForAggregation'
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
    const summaryCards = compiled.querySelectorAll<HTMLElement>('.trend-summary-card');
    expect(compiled.textContent).toContain('Delivery Complexity Assessment');
    expect(summaryCards[0].querySelector('strong')?.textContent?.trim()).toBe('21');
    expect(compiled.querySelectorAll('.trend-bar')).toHaveLength(2);
    expect(compiled.querySelectorAll('.trend-table tbody tr')).toHaveLength(2);
    expect(compiled.textContent).toContain('lip 2026');
    expect(compiled.textContent).toContain('sie 2026');
    expect(compiled.textContent).toContain('nie są to punkty osoby ani miara produktywności');
    expect(Array.from(
      compiled.querySelector<HTMLSelectElement>('.trend-field select')!.options
    ).map((option) => option.text.trim())).toEqual([
      'Dzień', 'Tydzień', 'Miesiąc', 'Kwartał'
    ]);
  });

  it('should filter full Delivery Units by team and update the chart and summary', async () => {
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

    expect(element().querySelector('.trend-summary-card strong')?.textContent?.trim()).toBe('5');
    expect(element().querySelectorAll('.trend-bar')).toHaveLength(1);
    expect(element().textContent).toContain('1 w wybranym zakresie');
  });

  it('should keep the previous dataset when a later selection mixes assessment formats', async () => {
    const dca = csvFile('dca.csv', DCA_HEADERS, [row(DCA_HEADERS)]);
    await selectFiles([dca]);
    expect(element().querySelector('.trend-summary-card strong')?.textContent?.trim()).toBe('8');

    const scope = csvFile('scope.csv', DSC_HEADERS, [row(DSC_HEADERS)]);
    await selectFiles([dca, scope]);

    expect(element().querySelector('[role="alert"]')?.textContent).toContain(
      'Wszystkie pliki musza pochodzic z tego samego assessmentu'
    );
    expect(element().querySelector('.trend-summary-card strong')?.textContent?.trim()).toBe('8');
    expect(element().textContent).toContain('Delivery Complexity Assessment');
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
    pointsForAggregation: headers.includes('finalScore') ? '120' : '8'
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
