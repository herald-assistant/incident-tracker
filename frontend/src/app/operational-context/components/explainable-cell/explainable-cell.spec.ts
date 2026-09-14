import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { MatTooltip } from '@angular/material/tooltip';

import { ExplainableAggregateDto } from '../../models/operational-context.models';
import { ExplainableCellComponent } from './explainable-cell';

describe('ExplainableCellComponent', () => {
  it('should show count and breakdown', async () => {
    await TestBed.configureTestingModule({
      imports: [ExplainableCellComponent],
      providers: [provideAnimationsAsync('noop')]
    }).compileComponents();

    const fixture = TestBed.createComponent(ExplainableCellComponent);
    fixture.componentRef.setInput('aggregate', aggregate());
    fixture.componentRef.setInput('tooltipText', 'Repozytoria powiązane z systemem.');
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Repositories: 1');
    expect(compiled.textContent).toContain('crm-contact-service-repo');
    expect(compiled.textContent).toContain('Explicit reference');
    expect(fixture.debugElement.query(By.directive(MatTooltip)).injector.get(MatTooltip).message)
      .toBe('Repozytoria powiązane z systemem.');
  });
});

function aggregate(): ExplainableAggregateDto {
  return {
    label: 'Repositories',
    count: 1,
    severity: 'ok',
    confidence: 'high',
    tooltip: 'Repository scope.',
    groups: [
      {
        label: 'Repositories',
        count: 1,
        items: [
          {
            id: 'crm-contact-service-repo',
            label: 'crm-contact-service-repo',
            kind: 'repository',
            reason: 'System lists this repository.',
            status: 'verified',
            sourceRefs: []
          }
        ]
      }
    ],
    reasons: [
      {
        label: 'Explicit reference',
        detail: 'The system lists this repository.',
        strength: 'strong'
      }
    ],
    warnings: [],
    sourceRefs: [],
    detailsType: 'repository',
    detailsIds: ['crm-contact-service-repo']
  };
}
