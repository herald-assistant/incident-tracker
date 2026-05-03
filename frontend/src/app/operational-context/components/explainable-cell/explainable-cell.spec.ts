import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

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
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.textContent).toContain('Repositories: 1');
    expect(compiled.textContent).toContain('app-core-repo');
    expect(compiled.textContent).toContain('Explicit reference');
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
            id: 'app-core-repo',
            label: 'app-core-repo',
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
    detailsIds: ['app-core-repo']
  };
}
