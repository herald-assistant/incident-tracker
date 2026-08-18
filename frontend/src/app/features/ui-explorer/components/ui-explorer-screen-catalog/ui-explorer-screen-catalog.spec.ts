import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UiExplorerScreenCatalogEntry } from '../../models/ui-explorer.models';
import { UiExplorerFacade } from '../../state/ui-explorer.facade';
import { UiExplorerScreenCatalogComponent } from './ui-explorer-screen-catalog';

describe('UiExplorerScreenCatalogComponent', () => {
  let fixture: ComponentFixture<UiExplorerScreenCatalogComponent>;

  const crmScreen: UiExplorerScreenCatalogEntry = {
    screenId: 'crm-contact-preferences',
    label: 'CrmContactPreferencesComponent',
    routePattern: '/crm/contacts/:contactId/preferences',
    parentRoutePattern: '/crm/contacts/:contactId',
    status: 'RESOLVED',
    lazyLoaded: true,
    guards: ['CrmContactAccessGuard'],
    routeParameters: ['contactId'],
    limitations: []
  };
  const selectedScreenId = signal(crmScreen.screenId);
  const selectedScreen = signal<UiExplorerScreenCatalogEntry | null>(crmScreen);
  const facade = {
    selectedScreenId,
    selectedScreen,
    selectedSystemId: signal('crm-agent-portal'),
    screenState: signal('ready'),
    controlsLocked: signal(false),
    screenCatalog: signal({
      systemId: 'crm-agent-portal',
      systemLabel: 'Synthetic CRM Agent Portal',
      sourceRevision: { branch: 'main', revision: 'crm-revision-a1b2c3' },
      status: 'READY',
      screens: [crmScreen],
      diagnostics: [],
      limitations: [],
      boundary: {
        visitedRouteNodeCount: 1,
        visitedRouteFileCount: 1,
        sourceReadCount: 1,
        aliasResolutionCount: 0,
        unresolvedEdgeCount: 0,
        limitReached: false,
        maxRouteNodes: 100,
        maxRouteFiles: 100,
        maxSourceReads: 100,
        maxAliasResolutions: 100,
        maxImportDepth: 10
      }
    }),
    selectScreen: vi.fn()
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UiExplorerScreenCatalogComponent],
      providers: [{ provide: UiExplorerFacade, useValue: facade }]
    }).compileComponents();
    fixture = TestBed.createComponent(UiExplorerScreenCatalogComponent);
    fixture.detectChanges();
  });

  it('presents the CRM route as the primary view identity and the component as metadata', () => {
    const root = fixture.nativeElement as HTMLElement;
    const control = root.querySelector('.ui-explorer-screen-select__value');

    expect(control?.querySelector('strong')?.textContent?.trim())
      .toBe('/crm/contacts/:contactId/preferences');
    expect(control?.querySelector('small')?.textContent?.trim())
      .toBe('CrmContactPreferencesComponent');

    root.querySelector<HTMLButtonElement>('.ui-explorer-screen-select__control')?.click();
    fixture.detectChanges();

    const option = root.querySelector('.ui-explorer-screen-option__body');
    expect(option?.querySelector('strong')?.textContent?.trim())
      .toBe('/crm/contacts/:contactId/preferences');
    expect(option?.querySelector('small')?.textContent?.trim())
      .toBe('CrmContactPreferencesComponent');
  });
});
