import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AnalysisReport } from '../../core/models/analysis.models';
import { AnalysisReportPanelComponent } from './analysis-report-panel';

describe('AnalysisReportPanelComponent', () => {
  it('should render report header, markdown sections and meta', async () => {
    const fixture = await renderReport(report());
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('DOWNSTREAM_TIMEOUT');
    expect(compiled.querySelector('.analysis-report-panel__summary h3')?.textContent).toBe('DOWNSTREAM_TIMEOUT');
    expect((compiled.querySelector('.analysis-report-panel__details') as HTMLDetailsElement).open).toBe(true);
    expect(compiled.textContent).toContain('incident-report-1');
    expect(compiled.textContent).toContain('TECHNICAL_HANDOFF');
    expect(compiled.querySelector('#report-section-TECHNICAL_HANDOFF')).not.toBeNull();
    expect(compiled.textContent).toContain('CustomerClient.call');
    expect(compiled.textContent).toContain('Brak logow downstream.');
    expect(compiled.textContent).toContain('Potwierdzic timeout w systemie zewnetrznym.');
    expect(compiled.querySelector('.analysis-report-panel__markdown strong')?.textContent).toBe('Timeout');
  });

  it('embeds the report body without a second header or collapsible outer card', async () => {
    const fixture=await renderReport(report());fixture.componentRef.setInput('embedded',true);fixture.detectChanges();
    const element=fixture.nativeElement as HTMLElement;
    expect(element.querySelector('.analysis-report-panel__summary')).toBeNull();
    expect(element.querySelector('.analysis-report-panel__details')).toBeNull();
    expect(element.textContent).not.toContain('DOWNSTREAM_TIMEOUT');
    expect(element.querySelector('.analysis-report-panel__markdown strong')?.textContent).toBe('Timeout');
    expect(element.querySelector('#report-section-TECHNICAL_HANDOFF')).not.toBeNull();
    expect(element.querySelector('.analysis-report-section-meta')).not.toBeNull();
    expect(element.querySelector('.analysis-report-appendix')).not.toBeNull();
  });

  it('can hide technical identifiers while preserving section navigation and content', async () => {
    const fixture=await renderReport(report());fixture.componentRef.setInput('showTechnicalIds',false);fixture.detectChanges();
    const element=fixture.nativeElement as HTMLElement;
    expect(element.textContent).not.toContain('incident-report-1');
    expect(element.textContent).not.toContain('TECHNICAL_HANDOFF');
    expect(element.textContent).toContain('Technical handoff');
    expect(element.querySelector('#report-section-TECHNICAL_HANDOFF')).not.toBeNull();
  });

  it('keeps unnamed sections when the optional appendix is not configured', async () => {
    const value=report();value.sections[0]={...value.sections[0],id:'',title:''};
    const fixture=await renderReport(value);
    expect(fixture.nativeElement.querySelector('.analysis-report-panel__section h4')?.textContent).toBe('Sekcja 1');
    expect(fixture.nativeElement.querySelector('.analysis-report-section-content__markdown')?.textContent).toContain('CustomerClient.call');
  });

  it('sorts sections without mutating checkpoints and puts a collapsed appendix after them', async () => {
    const value=report();const template=value.sections[0];
    value.sections=[
      {...template,id:'TABLE',title:'Tabela customer',order:3},
      {...template,id:'APPENDIX',title:'Appendix',order:4,markdown:'Warunki wdrożenia CRM.'},
      {...template,id:'SUMMARY',title:'Podsumowanie',order:1},
      {...template,id:'TABLE_LIST',title:'Lista tabel',order:2}
    ];
    const original=[...value.sections];const fixture=await renderReport(value);
    fixture.componentRef.setInput('appendixSectionId','APPENDIX');fixture.detectChanges();
    const element=fixture.nativeElement as HTMLElement;
    expect([...element.querySelectorAll('.analysis-report-panel__section h4')].map(node=>node.textContent?.trim()))
      .toEqual(['Podsumowanie','Lista tabel','Tabela customer']);
    expect(value.sections).toEqual(original);
    const appendix=element.querySelector('.analysis-report-appendix') as HTMLDetailsElement;
    expect(appendix.open).toBe(false);expect(appendix.textContent).toContain('Warunki wdrożenia CRM.');
    const last=element.querySelector('#report-section-TABLE')!;
    expect(last.compareDocumentPosition(appendix)&Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(element.querySelectorAll('.analysis-report-appendix')).toHaveLength(1);
  });

  it('keeps source URLs in collapsed metadata and renders unsafe targets as text', async () => {
    const value=report();value.sections[0].meta.references=[
      {type:'source',label:'Definicja kolumny',target:'https://gitlab.example.com/CRM/customer/-/blob/abc/db/customer.xml',description:'Typ i wartość domyślna.'},
      {type:'source',label:'Niepoprawny adres',target:'javascript:alert(1)',description:''}
    ];
    const fixture=await renderReport(value);const element=fixture.nativeElement as HTMLElement;
    const meta=element.querySelector('.analysis-report-section-meta')!;
    expect((meta.querySelector('details') as HTMLDetailsElement).open).toBe(false);
    expect(meta.querySelector('a')?.textContent).toBe('Definicja kolumny');
    expect(meta.querySelector('a')?.getAttribute('href')).toContain('https://gitlab.example.com/CRM');
    expect(meta.querySelectorAll('a')).toHaveLength(1);
    expect(meta.textContent).not.toContain('https://gitlab.example.com');
    const markdown=element.querySelector('.analysis-report-section-content__markdown')!;
    expect(markdown.compareDocumentPosition(meta)&Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  async function renderReport(
    value: AnalysisReport
  ): Promise<ComponentFixture<AnalysisReportPanelComponent>> {
    await TestBed.configureTestingModule({
      imports: [AnalysisReportPanelComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(AnalysisReportPanelComponent);
    fixture.componentRef.setInput('report', value);
    fixture.detectChanges();
    return fixture;
  }

  function report(): AnalysisReport {
    return {
      reportId: 'incident-report-1',
      header: 'DOWNSTREAM_TIMEOUT',
      subHeader: 'CRM / CRM Customer Context',
      markdownSummary: '**Timeout** downstream blokuje proces profilu klienta CRM.',
      sections: [
        {
          id: 'TECHNICAL_HANDOFF',
          title: 'Technical handoff',
          order: 1,
          markdown: 'Sprawdz `CustomerClient.call` i timeout klienta.',
          meta: {
            references: [
              {
                type: 'code',
                label: 'CustomerClient.call',
                target: 'src/main/java/CustomerClient.java:L42',
                description: 'Wywolanie downstream.'
              }
            ],
            visibilityLimits: ['Brak logow downstream.'],
            openQuestions: ['Potwierdzic timeout w systemie zewnetrznym.'],
            gaps: ['Brak trace po stronie integracji.'],
            confidence: 'medium',
            warnings: []
          }
        }
      ],
      meta: {
        references: [],
        visibilityLimits: ['Brak logow downstream.'],
        openQuestions: [],
        gaps: [],
        confidence: 'medium',
        warnings: []
      }
    };
  }
});
