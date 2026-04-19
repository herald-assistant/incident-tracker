import { TestBed } from '@angular/core/testing';

import { AnalysisFinalResultComponent } from './analysis-final-result';

describe('AnalysisFinalResultComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalysisFinalResultComponent]
    }).compileComponents();
  });

  it('should render exploratory diagram after switching from conservative to exploratory', async () => {
    const fixture = TestBed.createComponent(AnalysisFinalResultComponent);
    fixture.componentRef.setInput('status', 'COMPLETED');
    fixture.componentRef.setInput('result', {
      status: 'COMPLETED',
      correlationId: 'timeout-123',
      environment: 'dev3',
      gitLabBranch: 'release/2026.04',
      variants: {
        conservative: {
          mode: 'CONSERVATIVE',
          status: 'COMPLETED',
          detectedProblem: 'DOWNSTREAM_TIMEOUT',
          summary: 'Conservative summary',
          recommendedAction: '- Conservative action',
          rationale: '- Conservative rationale',
          problemNature: 'CONFIRMED',
          confidence: 'HIGH',
          prompt: 'Conservative prompt',
          diagram: null
        },
        exploratory: {
          mode: 'EXPLORATORY',
          status: 'COMPLETED',
          detectedProblem: 'EXPANDED_FLOW_HYPOTHESIS',
          summary: 'Exploratory summary',
          recommendedAction: '- Exploratory action',
          rationale: '- Exploratory rationale',
          problemNature: 'HYPOTHESIS',
          confidence: 'MEDIUM',
          prompt: 'Exploratory prompt',
          diagram: {
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
          }
        }
      }
    });

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const buttons = Array.from(
      compiled.querySelectorAll('.analysis-variant-toggle__button')
    ) as HTMLButtonElement[];

    expect(compiled.textContent).toContain('DOWNSTREAM_TIMEOUT');
    expect(compiled.textContent).not.toContain('Diagram flow');

    buttons[1]?.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(compiled.textContent).toContain('EXPANDED_FLOW_HYPOTHESIS');
    expect(compiled.textContent).toContain('Diagram flow');
    expect(compiled.textContent).toContain('backend');
    expect(compiled.textContent).toContain('catalog-service');
    expect(compiled.textContent).toContain('Hipoteza wynikajaca z logow i repo.');
  });
});
