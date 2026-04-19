import { TestBed } from '@angular/core/testing';

import { AnalysisFlowDiagramComponent } from './analysis-flow-diagram';

describe('AnalysisFlowDiagramComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalysisFlowDiagramComponent]
    }).compileComponents();
  });

  it('should render nodes, hypotheses, and the error marker from the provided diagram', async () => {
    const fixture = TestBed.createComponent(AnalysisFlowDiagramComponent);
    fixture.componentRef.setInput('diagram', {
      nodes: [
        {
          id: 'backend',
          kind: 'COMPONENT',
          title: 'backend',
          componentName: 'backend',
          factStatus: 'FACT',
          firstSeenAt: '2026-04-20T10:15:30Z',
          metadata: [{ name: 'class', value: 'CustomerController' }],
          errorSource: true
        },
        {
          id: 'catalog-service',
          kind: 'COMPONENT',
          title: 'catalog-service',
          componentName: 'catalog-service',
          factStatus: 'HYPOTHESIS',
          firstSeenAt: '',
          metadata: [{ name: 'endpoint', value: '/inventory' }],
          errorSource: false
        }
      ],
      edges: [
        {
          id: 'backend->catalog-service',
          fromNodeId: 'backend',
          toNodeId: 'catalog-service',
          sequence: 1,
          interactionType: 'HTTP',
          factStatus: 'HYPOTHESIS',
          startedAt: '',
          durationMs: null,
          supportSummary: 'Hipoteza wynikajaca z logow i repo.'
        }
      ]
    });

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.textContent).toContain('backend');
    expect(compiled.textContent).toContain('catalog-service');
    expect(compiled.textContent).toContain('Błąd');
    expect(compiled.textContent).toContain('HTTP');
    expect(compiled.textContent).toContain('Hipoteza wynikajaca z logow i repo.');
    expect(compiled.textContent).toContain('class: CustomerController');
    expect(compiled.textContent).toContain('endpoint: /inventory');
  });
});
