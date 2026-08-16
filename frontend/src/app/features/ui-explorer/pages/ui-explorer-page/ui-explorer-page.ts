import { Component, DestroyRef, OnInit, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';

import { AnalysisFeatureAsideComponent } from '../../../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisStepsPanelComponent } from '../../../../components/analysis-steps-panel/analysis-steps-panel';
import { UiExplorerConfigurationComponent } from '../../components/ui-explorer-configuration/ui-explorer-configuration';
import { UiExplorerResultComponent } from '../../components/ui-explorer-result/ui-explorer-result';
import { UiExplorerJobStatus } from '../../models/ui-explorer.models';
import { UiExplorerFacade } from '../../state/ui-explorer.facade';
import { readJsonFile } from '../../../../core/utils/json-file.utils';

@Component({
  selector: 'app-ui-explorer-page',
  imports: [
    AnalysisFeatureAsideComponent,
    AnalysisStepsPanelComponent,
    UiExplorerConfigurationComponent,
    UiExplorerResultComponent
  ],
  providers: [UiExplorerFacade],
  templateUrl: './ui-explorer-page.html',
  styleUrl: './ui-explorer-page.scss'
})
export class UiExplorerPageComponent implements OnInit {
  readonly facade = inject(UiExplorerFacade);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly progressCount = computed(() => this.facade.job()?.steps.length ?? 0);
  readonly aiCount = computed(() => this.facade.job()?.aiActivityEvents.length ?? 0);
  readonly feedbackCount = computed(() => this.facade.job()?.toolFeedback.length ?? 0);
  readonly screenLabel = computed(
    () =>
      this.facade.job()?.result?.screen.label ??
      this.facade.selectedScreen()?.label ??
      this.facade.job()?.request.screenId ??
      'Selected CRM view'
  );
  readonly resultSourceLabel = computed(() => {
    const source = this.facade.resultSource();
    if (source?.origin === 'history') {
      return `Analysis History · ${source.localRunName || source.localRunId || 'UI Explorer run'}`;
    }
    if (source?.origin === 'imported') {
      return `Imported JSON · ${source.fileName || 'UI Explorer export'}`;
    }
    return 'Current run';
  });

  ngOnInit(): void {
    this.facade.initialize();
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const localRunId = params.get('localRunId')?.trim() ?? '';
        if (localRunId) {
          this.facade.loadLocalRun(localRunId);
        }
      });
  }

  triggerImport(fileInput: HTMLInputElement): void {
    this.facade.setPortabilityError('');
    fileInput.value = '';
    fileInput.click();
  }

  async importResult(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }

    try {
      const document = await readJsonFile(
        file,
        'Importowany plik UI Explorer nie jest poprawnym JSON.'
      );
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { localRunId: null },
        queryParamsHandling: 'merge',
        replaceUrl: true
      });
      this.facade.importAnalysis(document, file.name);
    } catch (error) {
      this.facade.setPortabilityError(
        error instanceof Error ? error.message : 'Nie udało się odczytać importu UI Explorer.'
      );
    } finally {
      input.value = '';
    }
  }

  startNewRun(): void {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { localRunId: null },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
    this.facade.clearResult();
  }

  jobStatusClass(status: UiExplorerJobStatus): string {
    switch (status) {
      case 'COMPLETED':
        return 'status-pill status-pill--done';
      case 'FAILED':
      case 'BLOCKED':
        return 'status-pill status-pill--error';
      case 'PARTIAL':
        return 'status-pill status-pill--queued';
      default:
        return 'status-pill status-pill--running';
    }
  }

  jobStatusLabel(status: UiExplorerJobStatus): string {
    switch (status) {
      case 'DISCOVERING_SCREEN':
        return 'discovering view';
      case 'BUILDING_CONTEXT':
        return 'building context';
      case 'ANALYZING':
        return 'analyzing';
      case 'COMPLETED':
        return 'completed';
      case 'PARTIAL':
        return 'partial';
      case 'BLOCKED':
        return 'blocked';
      case 'FAILED':
        return 'failed';
      default:
        return 'queued';
    }
  }

  terminalTitle(status: UiExplorerJobStatus): string {
    switch (status) {
      case 'COMPLETED':
        return 'UI Explorer run completed';
      case 'PARTIAL':
        return 'UI Explorer returned a partial run';
      case 'BLOCKED':
        return 'UI Explorer run is blocked';
      case 'FAILED':
        return 'UI Explorer run failed';
      default:
        return 'UI Explorer is working';
    }
  }
}
