import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { AnalysisFollowUpChatComponent } from '../../components/analysis-follow-up-chat/analysis-follow-up-chat';
import { AnalysisReportEditorComponent } from '../../components/analysis-report-editor/analysis-report-editor';
import { AnalysisReportEditRequest } from '../../core/models/analysis.models';
import { downloadJsonFile } from '../../core/utils/json-file.utils';
import { parseReleaseSummaryImport, releaseSummaryExport } from './release-summary-import-export';
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
    AnalysisStepsPanelComponent, AnalysisReportPanelComponent, AnalysisResultHeaderComponent, AnalysisResultTabsComponent,
    AnalysisFollowUpChatComponent, AnalysisReportEditorComponent],
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
  readonly imported = signal(false); readonly editing = signal(false); readonly saving = signal(false);
  readonly chatSubmitting = signal(false); readonly chatError = signal(''); readonly updateReport = signal(false);
  readonly canChat = computed(()=>!this.imported() && !this.busy() && !this.editing() && this.uiConfig.config().releaseSummaryEnabled && !!this.job()?.chatAvailability.available);
  readonly canEdit = computed(()=>!this.imported() && !this.busy() && !this.chatSubmitting() && this.uiConfig.config().releaseSummaryEnabled &&
    !!this.job()?.report && ['COMPLETED','PARTIAL','NO_CHANGES'].includes(this.job()!.status));
  readonly model = signal(''); readonly reasoning = signal('');
  readonly modelCatalog = signal(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
  readonly reasoningOptions = computed(() => reasoningEffortsForAiModel(this.modelCatalog(), this.model()));
  readonly modelsLoading = signal(false);
  readonly busy = computed(() => this.starting() || this.saving() || this.chatSubmitting() || !!this.job() && !terminalReleaseSummary(this.job()!));
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
    if(!this.systemId() || !this.before() || !this.after() || this.busy() || this.editing() || !this.uiConfig.config().releaseSummaryEnabled) return;
    this.starting.set(true); this.error.set(''); this.imported.set(false); this.editing.set(false);
    this.api.start({systemId: this.systemId(), mode: 'LIQUIBASE', baselineRef: releaseRef(this.before()), targetRef: releaseRef(this.after()),
      ...(this.model() ? {model: this.model()} : {}), ...(this.reasoning() ? {reasoningEffort: this.reasoning()} : {})})
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: job => { this.job.set(job); this.starting.set(false); this.activeTab.set('report'); if(!terminalReleaseSummary(job)) this.poll(job.jobId); },
        error: err => { this.starting.set(false); this.error.set(err?.error?.message || 'Nie udało się uruchomić porównania.'); }
      });
  }
  restore(id: string): void {
    this.imported.set(false);this.editing.set(false);
    this.api.get(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: job => { this.job.set(job); this.systemId.set(job.request.systemId);
        this.beforeKind.set(job.request.baselineRef.kind); this.afterKind.set(job.request.targetRef.kind);
        this.before.set(this.canonical(job.request.baselineRef)); this.after.set(this.canonical(job.request.targetRef));
        if(!terminalReleaseSummary(job)) this.poll(id); },
      error: () => this.error.set('Nie można otworzyć zapisanej analizy.')
    });
  }
  retryPolling(): void { const job = this.job(); if(job) { this.error.set(''); this.poll(job.jobId); } }
  sendChat(message:string):void {
    const job=this.job();if(!job||!this.canChat()||this.chatSubmitting())return;
    this.chatSubmitting.set(true);this.chatError.set('');
    this.api.chat(job.jobId,message,this.updateReport()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next:updated=>{this.job.set(updated);this.chatSubmitting.set(false);this.updateReport.set(false);if(!terminalReleaseSummary(updated))this.poll(updated.jobId);},
      error:err=>{this.chatSubmitting.set(false);this.chatError.set(err?.error?.message||'Nie udało się rozpocząć kontynuacji.');}
    });
  }
  saveReport(request:AnalysisReportEditRequest):void {
    const job=this.job();if(!job||!this.canEdit()||this.saving())return;
    this.saving.set(true);this.error.set('');
    this.api.edit(job.jobId,request).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next:updated=>{this.job.set(updated);this.editing.set(false);this.saving.set(false);},
      error:err=>{this.saving.set(false);this.error.set(err?.error?.message||'Nie zapisano zmian. Odśwież raport w razie konfliktu rewizji.');}
    });
  }
  async importFile(event:Event):Promise<void> {
    const input=event.target as HTMLInputElement,file=input.files?.[0];if(!file||this.busy())return;
    try {const imported=parseReleaseSummaryImport(JSON.parse(await file.text()));this.pollSubscription?.unsubscribe();
      this.job.set(imported);this.imported.set(true);this.editing.set(false);this.activeTab.set('report');this.error.set('');}
    catch(e){this.error.set(e instanceof Error?e.message:'Nie można odczytać pliku.');}finally{input.value='';}
  }
  exportFile():void {
    const job=this.job();if(!job?.exportAvailable||this.busy())return;
    if(this.imported()){downloadJsonFile('release-summary.json',releaseSummaryExport(job));return;}
    this.api.export(job.jobId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next:payload=>downloadJsonFile('release-summary.json',payload),error:()=>this.error.set('Nie udało się wyeksportować analizy.')});
  }
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

