import { TestBed } from '@angular/core/testing';
import { TestKey } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatTooltip } from '@angular/material/tooltip';
import { MatTooltipHarness } from '@angular/material/tooltip/testing';
import { MatTabLinkHarness, MatTabNavBarHarness } from '@angular/material/tabs/testing';
import { By } from '@angular/platform-browser';

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
      { id: 'INSTRUCTION_COMPLIANCE', tabLabel: 'Instruction compliance' }
    ]);
    fixture.detectChanges();
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;
    const tabs = compiled.querySelectorAll<HTMLButtonElement>('[role="tab"]');
    expect(tabs).toHaveLength(2);
    expect(compiled.querySelector('.analysis-result-tab__spinner')).toBeNull();
    expect(compiled.querySelector('.analysis-result-tab__status-icon')).toBeNull();
    expect(tabs[0].getAttribute('aria-selected')).toBe('true');
    expect(tabs[0].getAttribute('aria-controls')).toBe(
      'change-verification-panel'
    );
    expect(compiled.querySelector('[role="tabpanel"]')?.getAttribute('aria-labelledby'))
      .toBe(tabs[0].id);

    tabs[1].click();
    expect(tabSelected).toHaveBeenCalledWith('INSTRUCTION_COMPLIANCE');
  });

  it('should navigate by arrows and Home/End, select with Enter/Space and label the active panel', async () => {
    await TestBed.configureTestingModule({ imports: [AnalysisResultTabsComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisResultTabsComponent);
    fixture.componentRef.setInput('tabs', [
      { id: 'profiles', tabLabel: 'CRM Customer Profiles' },
      { id: 'contacts', tabLabel: 'CRM Contacts' },
      { id: 'notifications', tabLabel: 'CRM Notifications' }
    ]);
    fixture.componentRef.setInput('activeTabId', 'profiles');
    const tabSelected = vi.fn((id: string) => fixture.componentRef.setInput('activeTabId', id));
    fixture.componentInstance.tabSelected.subscribe(tabSelected);
    fixture.detectChanges();

    const loader = TestbedHarnessEnvironment.loader(fixture);
    const nav = await loader.getHarness(MatTabNavBarHarness);
    const tabs = await loader.getAllHarnesses(MatTabLinkHarness);
    const hosts = await Promise.all(tabs.map(tab => tab.host()));
    await hosts[0].focus();
    await hosts[0].sendKeys(TestKey.RIGHT_ARROW);
    expect(await hosts[1].isFocused()).toBe(true);
    expect(await tabs[0].isActive()).toBe(true);
    expect(tabSelected).not.toHaveBeenCalled();

    await hosts[1].sendKeys(TestKey.END);
    expect(await hosts[2].isFocused()).toBe(true);
    await hosts[2].sendKeys(TestKey.ENTER);
    expect(tabSelected).toHaveBeenLastCalledWith('notifications');
    expect(await tabs[2].isActive()).toBe(true);
    const panel = (fixture.nativeElement as HTMLElement).querySelector('[role="tabpanel"]')!;
    expect(panel.getAttribute('aria-labelledby')).toBe('analysis-tab-notifications');

    await hosts[2].sendKeys(TestKey.LEFT_ARROW);
    expect(await hosts[1].isFocused()).toBe(true);
    await hosts[1].sendKeys(TestKey.HOME);
    expect(await hosts[0].isFocused()).toBe(true);
    await hosts[0].sendKeys(' ');
    expect(tabSelected).toHaveBeenLastCalledWith('profiles');
    expect(await tabs[0].isActive()).toBe(true);
    expect(panel.getAttribute('aria-labelledby')).toBe('analysis-tab-profiles');
    expect(await nav.getLinks()).toHaveLength(3);
  });

  it('should replace loading with an accessible notice and clear all status presentation on success', async () => {
    await TestBed.configureTestingModule({
      imports: [AnalysisResultTabsComponent]
    }).compileComponents();
    const fixture = TestBed.createComponent(AnalysisResultTabsComponent);
    fixture.componentRef.setInput('activeTabId', 'crm');
    fixture.componentRef.setInput('tabs', [{
      id: 'crm', tabLabel: 'CRM Customer Profile',
      status: { kind: 'loading', label: 'Analiza w toku' }
    }]);
    fixture.detectChanges();

    const tab = (fixture.nativeElement as HTMLElement).querySelector('[role="tab"]')!;
    const tooltip = fixture.debugElement.query(By.directive(MatTooltip)).injector.get(MatTooltip);
    expect(tab.querySelector('.analysis-result-tab__spinner')).not.toBeNull();
    expect(tab.getAttribute('aria-busy')).toBe('true');
    expect(tab.getAttribute('aria-label')).toContain('Analiza w toku');
    expect(tooltip.message).toBe('Analiza w toku');

    fixture.componentRef.setInput('tabs', [{
      id: 'crm', tabLabel: 'CRM Customer Profile',
      status: {
        kind: 'info', label: 'Analiza zakończona z ograniczeniami',
        tooltip: 'Analiza zakończona z ograniczeniami\nBrak pliku w test.'
      }
    }]);
    fixture.detectChanges();
    expect(tab.querySelector('.analysis-result-tab__spinner')).toBeNull();
    expect(tab.getAttribute('aria-busy')).toBeNull();
    expect(tab.querySelector('.analysis-result-tab__status-icon')?.textContent?.trim()).toBe('info');
    expect(tab.getAttribute('aria-label')).toContain('Analiza zakończona z ograniczeniami');
    expect(tooltip.message).toContain('Brak pliku w test.');
    expect(tooltip.disabled).toBe(false);
    const tooltipHarness = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatTooltipHarness);
    await tooltipHarness.show();
    expect(await tooltipHarness.getTooltipText()).toContain('Brak pliku w test.');

    fixture.componentRef.setInput('tabs', [{ id: 'crm', tabLabel: 'CRM Customer Profile' }]);
    fixture.detectChanges();
    expect(tab.textContent?.trim()).toBe('CRM Customer Profile');
    expect(tab.querySelector('.analysis-result-tab__status-icon')).toBeNull();
    expect(tab.getAttribute('aria-label')).toBeNull();
    expect(tab.getAttribute('aria-busy')).toBeNull();
    expect(tooltip.disabled).toBe(true);
    expect(tooltip.message).toBe('');
    expect(await tooltipHarness.isOpen()).toBe(false);
    expect(tab.getAttribute('aria-selected')).toBe('true');
  });
});
