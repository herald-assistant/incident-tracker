import { Component, HostListener, computed, inject, signal } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';

import { UiExplorerScreenCatalogEntry } from '../../models/ui-explorer.models';
import { UiExplorerFacade } from '../../state/ui-explorer.facade';

@Component({
  selector: 'app-ui-explorer-screen-catalog',
  imports: [MatTooltipModule],
  templateUrl: './ui-explorer-screen-catalog.html',
  styleUrl: './ui-explorer-screen-catalog.scss'
})
export class UiExplorerScreenCatalogComponent {
  readonly facade = inject(UiExplorerFacade);
  readonly open = signal(false);
  readonly search = signal('');

  readonly filteredScreens = computed(() => {
    const query = this.search().trim().toLocaleLowerCase();
    const screens = this.facade.screenCatalog()?.screens ?? [];
    if (!query) {
      return screens;
    }
    return screens.filter((screen) =>
      [screen.label, screen.routePattern, screen.parentRoutePattern]
        .join(' ')
        .toLocaleLowerCase()
        .includes(query)
    );
  });

  readonly controlLabel = computed(() => {
    const selected = this.facade.selectedScreen();
    if (selected) {
      return selected.label;
    }
    switch (this.facade.screenState()) {
      case 'loading':
        return 'Loading views…';
      case 'error':
        return 'Unable to load views';
      case 'empty':
        return 'No selectable views';
      default:
        return this.facade.selectedSystemId() ? 'Load view inventory' : 'Select application first';
    }
  });

  readonly controlMeta = computed(() => {
    const selected = this.facade.selectedScreen();
    if (selected) {
      return selected.routePattern || 'Entry point without URL route';
    }
    const catalog = this.facade.screenCatalog();
    if (catalog) {
      return `${catalog.screens.length} views · ${catalog.sourceRevision.revision}`;
    }
    switch (this.facade.screenState()) {
      case 'loading':
        return 'discovering Angular routes';
      case 'error':
        return 'retry view inventory';
      default:
        return 'waiting for application';
    }
  });

  @HostListener('document:click')
  close(): void {
    this.open.set(false);
  }

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    this.open.set(false);
  }

  toggle(event: Event): void {
    event.stopPropagation();
    if (this.facade.screenState() === 'ready' && !this.facade.controlsLocked()) {
      this.open.update((current) => !current);
    }
  }

  keepOpen(event: Event): void {
    event.stopPropagation();
  }

  selectScreen(screenId: string, event: Event): void {
    event.stopPropagation();
    this.facade.selectScreen(screenId);
    this.open.set(false);
  }

  screenContext(screen: UiExplorerScreenCatalogEntry): string {
    const parts = [
      screen.lazyLoaded ? 'lazy' : '',
      screen.guards.length ? 'guarded' : '',
      screen.routeParameters.length ? `${screen.routeParameters.length} params` : ''
    ];
    return parts.filter(Boolean).join(' · ');
  }
}
