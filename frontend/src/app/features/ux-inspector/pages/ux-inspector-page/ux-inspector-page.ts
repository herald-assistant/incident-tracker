import { Component, DestroyRef, OnInit, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';

import { AnalysisFeatureAsideComponent } from '../../../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisReportPanelComponent } from '../../../../components/analysis-report-panel/analysis-report-panel';
import { AnalysisStepsPanelComponent } from '../../../../components/analysis-steps-panel/analysis-steps-panel';
import { GitLabBranchSelectComponent } from '../../../../components/gitlab-branch-select/gitlab-branch-select';
import { readJsonFile } from '../../../../core/utils/json-file.utils';
import { UxInspectorCapture, UxInspectorJobStatus } from '../../models/ux-inspector.models';
import { UxInspectorCaptureIngressService } from '../../services/ux-inspector-capture-ingress.service';
import { UxInspectorFacade } from '../../state/ux-inspector.facade';

@Component({
  selector: 'app-ux-inspector-page',
  imports: [
    AnalysisFeatureAsideComponent,
    AnalysisReportPanelComponent,
    AnalysisStepsPanelComponent,
    GitLabBranchSelectComponent
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
