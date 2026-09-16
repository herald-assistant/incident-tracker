import { Component, DestroyRef, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router } from '@angular/router';

import { AnalysisFeatureAsideComponent } from '../../../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisStepsPanelComponent } from '../../../../components/analysis-steps-panel/analysis-steps-panel';
import { BrowserToolsSetupModalComponent } from '../../../../components/browser-tools-setup-modal/browser-tools-setup-modal';
import { GitLabBranchSelectComponent } from '../../../../components/gitlab-branch-select/gitlab-branch-select';
import { readJsonFile } from '../../../../core/utils/json-file.utils';
import { UxInspectorCapture, UxInspectorJobStatus } from '../../models/ux-inspector.models';
import { UxInspectorCaptureIngressService } from '../../services/ux-inspector-capture-ingress.service';
import { UxInspectorFacade } from '../../state/ux-inspector.facade';
import { UxInspectorResultComponent } from '../../components/ux-inspector-result/ux-inspector-result';

type OpenMenu = 'system' | 'view' | 'model' | 'reasoning' | null;

@Component({
  selector: 'app-ux-inspector-page',
  imports: [
    AnalysisFeatureAsideComponent,
    AnalysisStepsPanelComponent,
    BrowserToolsSetupModalComponent,
    GitLabBranchSelectComponent,
    MatTooltipModule,
    UxInspectorResultComponent
  ],
  providers: [UxInspectorCaptureIngressService, UxInspectorFacade],
  templateUrl: './ux-inspector-page.html',
  styleUrl: './ux-inspector-page.scss'
})
export class UxInspectorPageComponent implements OnInit {
  readonly facade = inject(UxInspectorFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly progressCount = computed(() => this.facade.job()?.steps.length ?? 0);
  readonly aiCount = computed(() => this.facade.job()?.aiActivityEvents.length ?? 0);
  readonly feedbackCount = computed(() => this.facade.job()?.toolFeedback.length ?? 0);
  readonly browserToolsModalOpen = signal(false);
  readonly openMenu = signal<OpenMenu>(null);
  readonly systemSearch = signal('');
  readonly viewSearch = signal('');
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
  readonly filteredViews = computed(() => {
    const query = this.viewSearch().trim().toLocaleLowerCase();
    const views = this.facade.viewCatalog()?.views ?? [];
    return query
      ? views.filter((view) =>
          [view.label, view.routePattern, view.viewId]
            .join(' ')
            .toLocaleLowerCase()
            .includes(query)
        )
      : views;
  });
  readonly selectedModelLabel = computed(
    () =>
      this.facade.aiOptions().models.find((model) => model.id === this.facade.selectedModel())
        ?.name ?? 'No model selected'
  );
  readonly selectedReasoningLabel = computed(
    () => this.facade.selectedReasoningEffort() || 'No effort selected'
  );
  readonly viewControlLabel = computed(() => {
    const selected = this.facade.selectedView();
    if (selected) return selected.routePattern || selected.label;
    switch (this.facade.viewState()) {
      case 'loading': return 'Loading views…';
      case 'error': return 'Unable to load views';
      case 'empty': return 'No selectable views';
      default: return this.facade.selectedSystemId() ? 'Load view inventory' : 'Select application first';
    }
  });
  readonly viewControlMeta = computed(() => {
    const selected = this.facade.selectedView();
    if (selected) return selected.label;
    const catalog = this.facade.viewCatalog();
    if (catalog) return `${catalog.views.length} views · ${catalog.sourceRevision.revision}`;
    switch (this.facade.viewState()) {
      case 'loading': return 'discovering Angular routes';
      case 'error': return 'retry view inventory';
      default: return 'waiting for application';
    }
  });
  readonly targetLabel = computed(() => {
    const capture = this.facade.capture();
    return capture?.target.accessibleName || capture?.target.text || capture?.target.tag || 'Wskazany element';
  });
  readonly resultSourceLabel = computed(() => {
    const source = this.facade.resultSource();
    if (source?.origin === 'history') return `Analysis History · ${source.localRunName || source.localRunId || 'UX Inspector run'}`;
    if (source?.origin === 'imported') return `Imported JSON · ${source.fileName || 'UX Inspector export'}`;
    return 'Current run';
  });

  ngOnInit(): void {
    this.facade.initialize();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const localRunId = params.get('localRunId')?.trim() ?? '';
      if (localRunId) this.facade.loadLocalRun(localRunId);
    });
  }

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
    if (!this.facade.controlsLocked()) {
      this.openMenu.update((current) => (current === menu ? null : menu));
    }
  }

  selectSystem(systemId: string, event: Event): void {
    event.stopPropagation();
    this.facade.selectSystem(systemId);
    this.openMenu.set(null);
  }

  selectView(viewId: string, event: Event): void {
    event.stopPropagation();
    this.facade.selectView(viewId);
    this.openMenu.set(null);
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

  async importResult(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    try {
      const document = await readJsonFile(file, 'Importowany plik UX Inspectora nie jest poprawnym JSON.');
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { localRunId: null },
        queryParamsHandling: 'merge',
        replaceUrl: true
      });
      this.facade.importAnalysis(document, file.name);
    } catch (error) {
      this.facade.setPortabilityError(error instanceof Error ? error.message : 'Nie udało się odczytać importu.');
    } finally {
      input.value = '';
    }
  }

  statusClass(status: UxInspectorJobStatus): string {
    if (status === 'COMPLETED') return 'status-pill status-pill--done';
    if (status === 'PARTIAL' || status === 'QUEUED') return 'status-pill status-pill--queued';
    if (status === 'FAILED' || status === 'BLOCKED') return 'status-pill status-pill--error';
    return 'status-pill status-pill--running';
  }

  statusLabel(status: UxInspectorJobStatus): string {
    return status.toLocaleLowerCase().replaceAll('_', ' ');
  }

  stableAttributeEntries(): Array<[string, string]> {
    return Object.entries(this.facade.capture()?.target.domFingerprint.stableAttributes ?? {});
  }

  formControlLabel(control: NonNullable<UxInspectorCapture['formSnapshot']>['controls'][number]): string {
    return control.formControlName || control.name || control.accessibleName || `<${control.tag}>`;
  }

  formControlValue(control: NonNullable<UxInspectorCapture['formSnapshot']>['controls'][number]): string {
    if (control.selectedLabels.length) return control.selectedLabels.join(', ');
    if (control.checked !== null) return control.checked ? 'checked' : 'unchecked';
    return control.value !== null ? control.value : '—';
  }

  triggerImport(input: HTMLInputElement): void {
    this.facade.setPortabilityError('');
    input.value = '';
    input.click();
  }
}
