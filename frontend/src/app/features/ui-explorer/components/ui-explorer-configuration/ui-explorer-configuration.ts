import { Component, HostListener, computed, inject, output, signal } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';

import { UiExplorerSectionId, UiExplorerSectionMode } from '../../models/ui-explorer.models';
import { UiExplorerFacade } from '../../state/ui-explorer.facade';
import { UiExplorerScreenCatalogComponent } from '../ui-explorer-screen-catalog/ui-explorer-screen-catalog';

type OpenMenu = 'system' | 'sections' | 'model' | 'reasoning' | null;

@Component({
  selector: 'app-ui-explorer-configuration',
  imports: [MatTooltipModule, UiExplorerScreenCatalogComponent],
  templateUrl: './ui-explorer-configuration.html',
  styleUrl: './ui-explorer-configuration.scss'
})
export class UiExplorerConfigurationComponent {
  readonly importRequested = output<void>();
  readonly facade = inject(UiExplorerFacade);
  readonly openMenu = signal<OpenMenu>(null);
  readonly systemSearch = signal('');

  readonly filteredSystems = computed(() => {
    const query = this.systemSearch().trim().toLocaleLowerCase();
    const systems = this.facade.inputOptions()?.systems ?? [];
    return query
      ? systems.filter((system) =>
          [system.label, system.summary, system.systemId]
            .join(' ')
            .toLocaleLowerCase()
            .includes(query)
        )
      : systems;
  });

  readonly sectionModeCounts = computed(() => {
    const modes = this.facade.sectionModes();
    const counts = { deep: 0, compact: 0, off: 0 };
    for (const section of this.facade.inputOptions()?.sections ?? []) {
      switch (modes[section.sectionId]) {
        case 'DEEP':
          counts.deep += 1;
          break;
        case 'COMPACT':
          counts.compact += 1;
          break;
        default:
          counts.off += 1;
      }
    }
    return counts;
  });

  readonly sectionModesLabel = computed(() => {
    const counts = this.sectionModeCounts();
    return `${counts.deep} deep / ${counts.compact} compact / ${counts.off} off`;
  });

  readonly sectionModesMeta = computed(() =>
    (this.facade.inputOptions()?.sections ?? [])
      .map(
        (section) =>
          `${section.label} ${this.modeLabel(this.facade.sectionModes()[section.sectionId])}`
      )
      .join(' · ')
  );

  readonly selectedModelLabel = computed(() => {
    const modelId = this.facade.selectedModel();
    return (
      this.facade.aiOptions().models.find((model) => model.id === modelId)?.name ??
      'No model selected'
    );
  });

  readonly selectedReasoningLabel = computed(
    () => this.facade.selectedReasoningEffort() || 'No effort selected'
  );

  @HostListener('document:click')
  closeMenus(): void {
    this.openMenu.set(null);
  }

  @HostListener('document:keydown.escape')
  closeMenusOnEscape(): void {
    this.openMenu.set(null);
  }

  keepMenuOpen(event: Event): void {
    event.stopPropagation();
  }

  toggleMenu(menu: Exclude<OpenMenu, null>, event: Event): void {
    event.stopPropagation();
    if (this.facade.controlsLocked()) {
      return;
    }
    this.openMenu.update((current) => (current === menu ? null : menu));
  }

  selectSystem(systemId: string, event: Event): void {
    event.stopPropagation();
    this.facade.selectSystem(systemId);
    this.openMenu.set(null);
  }

  selectSectionMode(
    sectionId: UiExplorerSectionId,
    mode: UiExplorerSectionMode,
    event?: Event
  ): void {
    event?.stopPropagation();
    this.facade.selectSectionMode(sectionId, mode);
  }

  selectModel(model: string, event: Event): void {
    event.stopPropagation();
    this.facade.selectModel(model);
    this.openMenu.set(null);
  }

  selectReasoning(effort: string, event: Event): void {
    event.stopPropagation();
    this.facade.selectReasoningEffort(effort);
    this.openMenu.set(null);
  }

  modeButtonClass(sectionId: UiExplorerSectionId, mode: UiExplorerSectionMode): string {
    return this.facade.sectionModes()[sectionId] === mode
      ? 'ui-explorer-mode-button ui-explorer-mode-button--selected'
      : 'ui-explorer-mode-button';
  }

  statusPillClass(): string {
    const jobStatus = this.facade.job()?.status;
    if (this.facade.isSubmitting() || this.facade.isJobActive()) {
      return 'status-pill status-pill--running';
    }
    if (jobStatus === 'FAILED' || jobStatus === 'BLOCKED') {
      return 'status-pill status-pill--error';
    }
    if (jobStatus === 'PARTIAL') {
      return 'status-pill status-pill--queued';
    }
    switch (this.facade.inputState()) {
      case 'loading':
        return 'status-pill status-pill--running';
      case 'error':
        return 'status-pill status-pill--error';
      case 'empty':
        return 'status-pill status-pill--queued';
      default:
        return 'status-pill status-pill--done';
    }
  }

  statusLabel(): string {
    if (this.facade.isSubmitting()) {
      return 'starting';
    }
    if (this.facade.job()) {
      return this.facade.job()!.status.toLocaleLowerCase();
    }
    return this.facade.configurationReady() ? 'configured' : this.facade.inputState();
  }

  runButtonLabel(): string {
    if (this.facade.isSubmitting()) {
      return 'Starting…';
    }
    if (this.facade.isJobActive()) {
      return 'Running…';
    }
    return 'Run UI Explorer';
  }

  private modeLabel(mode: UiExplorerSectionMode | undefined): string {
    return (
      this.facade.inputOptions()?.modes.find((option) => option.mode === mode)?.label ??
      'Pominięta'
    );
  }
}
