import { TestBed } from '@angular/core/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

import { ExplainableAggregateDto } from '../../models/operational-context.models';
import { ContextCatalogTableComponent } from './context-catalog-table';

describe('ContextCatalogTableComponent', () => {
  it('should inspect aggregates without opening the entity drawer', async () => {
    await TestBed.configureTestingModule({
      imports: [ContextCatalogTableComponent],
      providers: [provideAnimationsAsync('noop')]
    }).compileComponents();

    const fixture = TestBed.createComponent(ContextCatalogTableComponent);
    const openRow = vi.fn();
    const openAggregateDetails = vi.fn();
    fixture.componentInstance.openRow.subscribe(openRow);
    fixture.componentInstance.openAggregateDetails.subscribe(openAggregateDetails);
    fixture.componentRef.setInput('columns', [
      { key: 'project', label: 'Repository' },
      { key: 'repositories', label: 'Repositories', type: 'aggregate' }
    ]);
    fixture.componentRef.setInput('rows', [
      {
        id: 'repo-1',
        project: 'repo-1',
        repositories: aggregate()
      }
    ]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    compiled
      .querySelector<HTMLButtonElement>('.explainable-cell__trigger')
      ?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(openRow).not.toHaveBeenCalled();
    expect(compiled.textContent).toContain('Breakdown');
    expect(compiled.textContent).toContain('Explicit reference.');
    const statusIcons = Array.from(compiled.querySelectorAll('.catalog-table__status-icon'));
    expect(statusIcons.map((icon) => icon.textContent?.trim())).toContain('check_circle');
    expect(statusIcons.map((icon) => icon.textContent?.trim())).toContain('error');
    expect(statusIcons.map((icon) => icon.getAttribute('aria-label'))).toContain('Verified');
    expect(statusIcons.map((icon) => icon.getAttribute('aria-label'))).toContain('Missing');

    const openDetailsButton = Array.from(compiled.querySelectorAll<HTMLButtonElement>('button'))
      .find((button) => button.textContent?.includes('Open details'));
    openDetailsButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();

    expect(openAggregateDetails).toHaveBeenCalledWith({ type: 'repository', id: 'repo-1' });
    expect(compiled.textContent).toContain('Breakdown');

    compiled
      .querySelector<HTMLButtonElement>('.catalog-table__title-button')
      ?.dispatchEvent(new MouseEvent('click', { bubbles: true }));

    expect(openRow).toHaveBeenCalledOnce();
  });
});

function aggregate(): ExplainableAggregateDto {
  return {
    label: 'Repositories',
    count: 2,
    severity: 'ok',
    confidence: 'high',
    tooltip: 'Repository scope.',
    groups: [
      {
        label: 'Repositories',
        count: 2,
        items: [
          {
            id: 'repo-1',
            label: 'repo-1',
            kind: 'repository',
            reason: 'Explicit reference.',
            status: 'verified',
            sourceRefs: []
          },
          {
            id: 'repo-2',
            label: 'repo-2',
            kind: 'repository',
            reason: 'Referenced repository is not present in the catalogue.',
            status: 'missing',
            sourceRefs: []
          }
        ]
      }
    ],
    reasons: [],
    warnings: [],
    sourceRefs: [],
    detailsType: 'repository',
    detailsIds: ['repo-1', 'repo-2']
  };
}
