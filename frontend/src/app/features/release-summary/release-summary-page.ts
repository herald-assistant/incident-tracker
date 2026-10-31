import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApplicationOption, ApplicationSelectComponent } from '../../components/application-select/application-select';
import { GitLabBranchSelectComponent } from '../../components/gitlab-branch-select/gitlab-branch-select';
import { AnalysisFeatureAsideComponent } from '../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisStepsPanelComponent } from '../../components/analysis-steps-panel/analysis-steps-panel';
import { AnalysisReportPanelComponent } from '../../components/analysis-report-panel/analysis-report-panel';
import { AnalysisResultHeaderComponent } from '../../components/analysis-result-header/analysis-result-header';
import { AnalysisResultTabsComponent } from '../../components/analysis-result-tabs/analysis-result-tabs';
import { ReleaseSummaryApiService } from './release-summary-api.service';
import { ReleaseRefKind, ReleaseSummarySnapshot, releaseRef, terminalReleaseSummary } from './release-summary.models';
import { AnalysisJobPollingService } from '../../core/services/analysis-job-polling.service';
import { AiOptionsApiService } from '../../core/services/ai-options-api.service';
import { AppUiConfigService } from '../../core/services/app-ui-config.service';
import { buildReportShareDocument } from '../../core/utils/analysis-share.utils';
import { EMPTY_ANALYSIS_AI_MODEL_OPTIONS, normalizeAnalysisAiModelOptions, listedDefaultAiModel,
  reasoningEffortsForAiModel, defaultReasoningEffortForAiModel } from '../../core/utils/analysis-ai-model-options.utils';

@Component({
  selector: 'app-release-summary-page',
  imports: [ApplicationSelectComponent, GitLabBranchSelectComponent, AnalysisFeatureAsideComponent,
    AnalysisStepsPanelComponent, AnalysisReportPanelComponent, AnalysisResultHeaderComponent, AnalysisResultTabsComponent],
  templateUrl: './release-summary-page.html',
  styleUrl: './release-summary-page.scss'
})
export class ReleaseSummaryPageComponent {
  readonly api = inject(ReleaseSummaryApiService);
  private readonly polling = inject(AnalysisJobPollingService);
  private readonly aiOptionsApi = inject(AiOptionsApiService);
  readonly uiConfig = inject(AppUiConfigService);
  private readonly route = inject(ActivatedRoute);
  readonly destroyRef = inject(DestroyRef);
  private pollSubscription?: Subscription;
  readonly applications = signal<ApplicationOption[]>([]);
  readonly systemId = signal('');
  readonly before = signal(''); readonly after = signal('');
  readonly beforeKind = signal<ReleaseRefKind>('BRANCH'); readonly afterKind = signal<ReleaseRefKind>('BRANCH');
  readonly loading = signal(false); readonly starting = signal(false); readonly error = signal('');
  readonly job = signal<ReleaseSummarySnapshot | null>(null);
  readonly activeTab = signal('report');
  readonly model = signal(''); readonly reasoning = signal('');
  readonly modelCatalog = signal(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
  readonly reasoningOptions = computed(() => reasoningEffortsForAiModel(this.modelCatalog(), this.model()));
  readonly modelsLoading = signal(false);
  readonly busy = computed(() => this.starting() || !!this.job() && !terminalReleaseSummary(this.job()!));
  readonly shareDocument = computed(() => this.job()?.report ? buildReportShareDocument(this.job()!.report!, 'release-summary.md') : null);
  readonly tabs = [{id: 'report', tabLabel: 'Raport'}, {id: 'coverage', tabLabel: 'Kompletność i źródła'}];
  readonly beforeLoader = (key: string, search: string, page?: number) =>
    this.api.refs(key.slice(0, key.lastIndexOf(':')), key.slice(key.lastIndexOf(':') + 1) as ReleaseRefKind, search, page);
  readonly afterLoader = this.beforeLoader;

  constructor() {
    this.uiConfig.load();
    this.loadApplications();
    this.modelsLoading.set(true);
    this.aiOptionsApi.getOptions().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: options => { this.modelCatalog.set(normalizeAnalysisAiModelOptions(options)); this.selectModel(listedDefaultAiModel(this.modelCatalog())); this.modelsLoading.set(false); },
      error: () => this.modelsLoading.set(false)
    });
    const localRunId = this.route.snapshot.queryParamMap.get('localRunId');
    if (localRunId) this.restore(localRunId);
  }
  loadApplications(): void {
    this.loading.set(true); this.error.set('');
    this.api.applications().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: items => { this.applications.set(items); this.loading.set(false); },
      error: () => { this.loading.set(false); this.error.set('Nie udało się pobrać aplikacji.'); }
    });
  }
  selectSystem(id: string): void { if(this.busy()) return; this.systemId.set(id); this.before.set(''); this.after.set(''); }
  setKind(side: 'before' | 'after', kind: ReleaseRefKind): void {
    if(this.busy()) return;
    if(side === 'before') { this.beforeKind.set(kind); this.before.set(''); } else { this.afterKind.set(kind); this.after.set(''); }
  }
  swap(): void {
    if(this.busy()) return;
    const value = this.before(), kind = this.beforeKind();
    this.before.set(this.after()); this.beforeKind.set(this.afterKind()); this.after.set(value); this.afterKind.set(kind);
  }
  selectModel(model: string): void {
    this.model.set(model); this.reasoning.set(defaultReasoningEffortForAiModel(this.modelCatalog(), model));
  }
  start(): void {
    if(!this.systemId() || !this.before() || !this.after() || this.busy() || !this.uiConfig.config().releaseSummaryEnabled) return;
    this.starting.set(true); this.error.set('');
    this.api.start({systemId: this.systemId(), mode: 'LIQUIBASE', baselineRef: releaseRef(this.before()), targetRef: releaseRef(this.after()),
      ...(this.model() ? {model: this.model()} : {}), ...(this.reasoning() ? {reasoningEffort: this.reasoning()} : {})})
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: job => { this.job.set(job); this.starting.set(false); this.activeTab.set('report'); if(!terminalReleaseSummary(job)) this.poll(job.jobId); },
        error: err => { this.starting.set(false); this.error.set(err?.error?.message || 'Nie udało się uruchomić porównania.'); }
      });
  }
  restore(id: string): void {
    this.api.get(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: job => { this.job.set(job); this.systemId.set(job.request.systemId);
        this.beforeKind.set(job.request.baselineRef.kind); this.afterKind.set(job.request.targetRef.kind);
        this.before.set(this.canonical(job.request.baselineRef)); this.after.set(this.canonical(job.request.targetRef));
        if(!terminalReleaseSummary(job)) this.poll(id); },
      error: () => this.error.set('Nie można otworzyć zapisanej analizy.')
    });
  }
  retryPolling(): void { const job = this.job(); if(job) { this.error.set(''); this.poll(job.jobId); } }
  statusLabel(status: string): string {
    return ({QUEUED: 'W kolejce', RUNNING: 'Analiza w toku', COMPLETED: 'Raport gotowy', PARTIAL: 'Raport częściowy',
      BLOCKED: 'Przygotowanie zablokowane', FAILED: 'Analiza przerwana', NO_CHANGES: 'Brak zmian Liquibase', INTERRUPTED: 'Przerwano przy restarcie'} as Record<string,string>)[status] || status;
  }
  private canonical(ref: {kind: ReleaseRefKind; name: string}): string {
    return ref.kind === 'BRANCH' ? 'refs/heads/' + ref.name : ref.kind === 'TAG' ? 'refs/tags/' + ref.name : ref.name;
  }
  private poll(id: string): void {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = this.polling.poll({load: () => this.api.get(id), initialDelayMs: 800, isTerminal: terminalReleaseSummary})
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: job => this.job.set(job), error: () => this.error.set('Odczyt postępu nie powiódł się. Możesz wznowić odświeżanie.')
      });
  }
}

