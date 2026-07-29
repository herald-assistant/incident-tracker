import { TestBed } from '@angular/core/testing';

import { AnalysisResultTabsComponent } from './analysis-result-tabs';

describe('AnalysisResultTabsComponent', () => {
  it('should render shared result tabs and emit the selected id', async () => {
    await TestBed.configureTestingModule({
      imports: [AnalysisResultTabsComponent]
    }).compileComponents();

    const fixture = TestBed.createComponent(AnalysisResultTabsComponent);
    const tabSelected = vi.fn();
    fixture.componentInstance.tabSelected.subscribe(tabSelected);
    fixture.componentRef.setInput('idPrefix', 'change-verification');
    fixture.componentRef.setInput('activeTabId', 'STORY_COMPLIANCE');
    fixture.componentRef.setInput('tabs', [
      { id: 'STORY_COMPLIANCE', tabLabel: 'Story compliance' },
      { id: 'INSTRUCTION_COMPLIANCE', tabLabel: 'Instruction compliance' },
      { id: 'SMOKE_PACK', tabLabel: 'Smoke pack' }
    ]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const tabs = compiled.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    expect(tabs).toHaveLength(3);
    expect(tabs[0].classList).toContain('analysis-result-tab--active');
    expect(tabs[0].getAttribute('aria-controls')).toBe(
      'change-verification-panel-STORY_COMPLIANCE'
    );

    tabs[1].click();
    expect(tabSelected).toHaveBeenCalledWith('INSTRUCTION_COMPLIANCE');
  });
});
