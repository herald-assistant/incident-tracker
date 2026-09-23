import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewChild, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subject, Subscription, catchError, concatMap, debounceTime, map, of } from 'rxjs';

import { AnalysisStepsPanelComponent } from '../../../components/analysis-steps-panel/analysis-steps-panel';
import { AnalysisFeatureAsideComponent } from '../../../components/analysis-feature-aside/analysis-feature-aside';
import { GitLabBranchSelectComponent } from '../../../components/gitlab-branch-select/gitlab-branch-select';
import { AnalysisJobPollingService } from '../../../core/services/analysis-job-polling.service';
import {
  OperationalContextAssistanceJob,
  OperationalContextAssistanceBatchPreview,
  OperationalContextAssistanceProposalDecisionRequest,
  OperationalContextAssistanceFieldChange,
  OperationalContextAssistanceProposal,
  OperationalContextAssistancePrefill,
  OperationalContextAssistanceRequest,
  OperationalContextAssistanceReviewDraft,
  OperationalContextAssistanceSourceOptions,
  OperationalContextRepositoryUsage,
  isTerminalAssistanceStatus
} from '../../models/operational-context-assistance.models';
import { OperationalContextReferenceOption } from '../../models/operational-context-maintenance.models';
import { OperationalContextAssistanceApiService } from '../../services/operational-context-assistance-api.service';

interface PendingDecisionCheck {
  jobId: string;
  decisions: OperationalContextAssistanceProposalDecisionRequest[];
  errorStatus: number;
  errorMessage: string;
}

interface BatchFieldIssue {
  proposalIndex: number;
  path: string;
  pointer: string;
  message: string;
  valueLabel: string;
  position: number | null;
}

@Component({
  selector: 'app-context-assistance-panel',
  imports: [ReactiveFormsModule, AnalysisStepsPanelComponent, AnalysisFeatureAsideComponent, GitLabBranchSelectComponent],
  templateUrl: './context-assistance-panel.html',
  styleUrl: './context-assistance-panel.scss'
})
export class ContextAssistancePanelComponent implements OnInit, OnChanges, OnDestroy {
  @ViewChild('reviewList') private reviewList?: ElementRef<HTMLElement>;
  private readonly api = inject(OperationalContextAssistanceApiService);
  private readonly polling = inject(AnalysisJobPollingService);
  private readonly destroyRef = inject(DestroyRef);
  private pollSubscription?: Subscription;
  private readonly reviewSaves = new Subject<{ jobId: string; draft: OperationalContextAssistanceReviewDraft; serialized: string }>();

  readonly prefill = input.required<OperationalContextAssistancePrefill>();
  readonly systemOptions = input<OperationalContextReferenceOption[]>([]);
  readonly activePrefill = signal<OperationalContextAssistancePrefill>({ mode: 'CREATE_AREA' });
  readonly initialJob = input<OperationalContextAssistanceJob | null>(null);
  readonly historyReadOnly = input(false);
  readonly jobChanged = output<OperationalContextAssistanceJob | null>();
  readonly catalogChanged = output<{ type: string; id: string }>();
  readonly newRunRequested = output<void>();

  readonly descriptionControl = new FormControl('', { nonNullable: true });
  readonly projectUrlControl = new FormControl('', { nonNullable: true });
  readonly catalogueProjectControl = new FormControl('', { nonNullable: true });
  readonly refControl = new FormControl('', { nonNullable: true });
  readonly includeGitLabControl = new FormControl(false, { nonNullable: true });
  readonly repositoryUsageControl = new FormControl<OperationalContextRepositoryUsage>('UNKNOWN', { nonNullable: true });
  readonly systemNameControl = new FormControl('', { nonNullable: true });
  readonly runtimeServiceNameControl = new FormControl('', { nonNullable: true });
  readonly existingSystemControl = new FormControl('', { nonNullable: true });
  readonly editValueControl = new FormControl('', { nonNullable: true });
  readonly librarySystemIds = signal<string[]>([]);
  readonly manualProject = signal(false);
  readonly manualRef = signal(false);
  readonly branchSourceKey = signal('');
  readonly sourceOptions = signal<OperationalContextAssistanceSourceOptions | null>(null);
  readonly sourceOptionsLoading = signal(false);
  readonly sourceOptionsError = signal('');

  readonly job = signal<OperationalContextAssistanceJob | null>(null);
  readonly starting = signal(false);
  readonly error = signal('');
  readonly contextSwitchNotice = signal('');
  readonly pollError = signal(false);
  readonly decisionBusy = signal(false);
  readonly previewBusy = signal(false);
  readonly batchPreview = signal<OperationalContextAssistanceBatchPreview | null>(null);
  readonly reviewedSelection = signal('');
  readonly decisionError = signal('');
  readonly batchFieldIssues = signal<BatchFieldIssue[]>([]);
  readonly batchGeneralErrors = signal<string[]>([]);
  readonly reviewSaveError = signal('');
  readonly reviewValidationVisible = signal(false);
  readonly pendingDecisionCheck = signal<PendingDecisionCheck | null>(null);
  readonly selectionByProposal = signal<Record<number, string[]>>({});
  readonly confirmationsByProposal = signal<Record<number, string[]>>({});
  readonly editedValuesByProposal = signal<Record<number, Record<string, unknown>>>({});
  readonly activeProposalIndex = signal(0);
  readonly composerExpanded = signal(true);
  readonly editingField = signal('');
  readonly editError = signal('');
  readonly isRunning = computed(() => this.starting() || Boolean(this.job() && !isTerminalAssistanceStatus(this.job()!.status)));
  readonly reviewComplete = computed(() => Boolean(this.job()?.proposalDecisions?.length));
  readonly analysisProgressRunning = computed(() => Boolean(!this.historyReadOnly() && this.job()
    && !isTerminalAssistanceStatus(this.job()!.status)));
  readonly aiWorkRunning = computed(() => !this.historyReadOnly() && this.job()?.status === 'ANALYZING');
  readonly loadProjectBranches = (sourceKey: string, search: string) => sourceKey.startsWith('url:')
    ? this.api.sourceBranches({ projectUrl: sourceKey.slice(4) }, search)
    : this.api.sourceBranches({ project: sourceKey.slice(8) }, search);

  ngOnInit(): void {
    this.reviewSaves.pipe(concatMap(({ jobId, draft, serialized }) =>
      this.api.saveReview(jobId, draft).pipe(
        map((saved) => ({ saved, serialized })),
        catchError(() => {
          if (this.job()?.jobId === jobId && !this.reviewComplete()) {
            this.reviewSaveError.set('Nie udało się zachować roboczych poprawek. Pozostały w tej przeglądarce; spróbuj ponownie zmienić wybór.');
          }
          return of(null);
        })
      )
    )).subscribe((result) => {
      if (!result) return;
      const key = this.reviewStorageKey(result.saved.jobId);
      try {
        const pending = localStorage.getItem(key);
        if (pending === result.serialized) localStorage.removeItem(key);
      } catch { /* The server has still persisted the review. */ }
      if (this.job()?.jobId === result.saved.jobId) this.reviewSaveError.set('');
    });
    this.activePrefill.set(this.prefill());
    this.descriptionControl.setValue(this.activePrefill().description ?? '');
    this.includeGitLabControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((enabled) => {
        if (!enabled) {
          this.resetRepositoryFacts();
          this.manualRef.set(false);
          this.selectBranchSource('');
        }
        if (enabled && this.sourceOptions()) {
          const key = this.manualProject()
            ? (this.isWebUrl(this.projectUrlControl.value.trim()) ? `url:${this.projectUrlControl.value.trim()}` : '')
            : (this.catalogueProjectControl.value.trim() ? `project:${this.catalogueProjectControl.value.trim()}` : '');
          this.selectBranchSource(key);
        }
        if (enabled && !this.sourceOptions() && !this.sourceOptionsLoading()) {
          queueMicrotask(() => {
            if (this.includeGitLabControl.value && !this.sourceOptions() && !this.sourceOptionsLoading()) {
              this.loadSourceOptions();
            }
          });
        }
      });
    this.catalogueProjectControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((project) => {
        if (this.includeGitLabControl.value && !this.manualProject()) {
          this.selectBranchSource(project.trim() ? `project:${project.trim()}` : '');
        }
      });
    this.projectUrlControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.includeGitLabControl.value && this.manualProject()) this.selectBranchSource('');
      });
    this.projectUrlControl.valueChanges
      .pipe(debounceTime(350), takeUntilDestroyed(this.destroyRef))
      .subscribe((projectUrl) => {
        if (this.includeGitLabControl.value && this.manualProject() && this.isWebUrl(projectUrl.trim())) {
          this.selectBranchSource(`url:${projectUrl.trim()}`, false);
        }
      });
    this.repositoryUsageControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.clearConditionalRepositoryFacts());
    const previousJob = this.initialJob();
    if (previousJob) {
      this.job.set(previousJob);
      this.restoreReviewDraft(previousJob);
      this.composerExpanded.set(false);
      if (!this.historyReadOnly() && !isTerminalAssistanceStatus(previousJob.status)) this.startPolling(previousJob.jobId);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.historyReadOnly()) {
      if (changes['initialJob'] || changes['prefill'] || changes['historyReadOnly']) {
        this.pollSubscription?.unsubscribe();
        this.job.set(this.initialJob());
        if (this.initialJob()) this.restoreReviewDraft(this.initialJob()!);
        this.composerExpanded.set(false);
        this.activeProposalIndex.set(0);
        this.cancelFieldEdit();
        if (changes['prefill']) {
          this.activePrefill.set(this.prefill());
          this.descriptionControl.setValue(this.activePrefill().description ?? '');
          this.contextSwitchNotice.set('');
        }
      }
      return;
    }
    if (changes['initialJob'] && !changes['initialJob'].firstChange) {
      const incoming = this.initialJob();
      if (!incoming) {
        this.pollSubscription?.unsubscribe();
        this.job.set(null);
        this.reviewValidationVisible.set(false);
        this.composerExpanded.set(true);
      }
      if (incoming && (incoming.jobId !== this.job()?.jobId || incoming.updatedAt !== this.job()?.updatedAt)) {
        const changedJob = incoming.jobId !== this.job()?.jobId;
        this.pollSubscription?.unsubscribe();
        this.job.set(incoming);
        if (changedJob) this.restoreReviewDraft(incoming);
        this.composerExpanded.set(false);
        this.activeProposalIndex.set(0);
        this.cancelFieldEdit();
        if (!this.historyReadOnly() && !isTerminalAssistanceStatus(incoming.status)) this.startPolling(incoming.jobId);
      }
    }
    if (changes['prefill'] && !changes['prefill'].firstChange) {
      const previousJob = this.job();
      if (this.starting() || this.previewBusy() || this.decisionBusy() || this.pendingDecisionCheck()
        || (previousJob && !isTerminalAssistanceStatus(previousJob.status))) {
        const notice = 'Trwa poprzednia asysta. Jej kontekst i wynik pozostają widoczne; po zakończeniu ponownie wybierz nowy cel.';
        this.contextSwitchNotice.set(notice);
        return;
      }
      if (previousJob) {
        this.job.set(null);
        this.jobChanged.emit(null);
        this.selectionByProposal.set({});
        this.confirmationsByProposal.set({});
        this.editedValuesByProposal.set({});
        this.activeProposalIndex.set(0);
        this.cancelFieldEdit();
        this.decisionError.set('');
        this.invalidateBatchPreview();
      }
      this.activePrefill.set(this.prefill());
      this.composerExpanded.set(true);
      this.resetRepositoryFacts();
      this.contextSwitchNotice.set('');
      this.descriptionControl.setValue(this.activePrefill().description ?? '');
      this.error.set('');
    }
  }

  ngOnDestroy(): void {
    this.pollSubscription?.unsubscribe();
    this.reviewSaves.complete();
  }

  private reviewStorageKey(jobId: string): string {
    return `tdw.opctx-assistance.review.${jobId}`;
  }

  private restoreReviewDraft(snapshot: OperationalContextAssistanceJob): void {
    this.reviewSaveError.set('');
    this.reviewValidationVisible.set(false);
    this.selectionByProposal.set({});
    this.confirmationsByProposal.set({});
    this.editedValuesByProposal.set({});
    if (snapshot.proposalDecisions?.length) {
      try { localStorage.removeItem(this.reviewStorageKey(snapshot.jobId)); } catch { /* Storage can be unavailable. */ }
      return;
    }
    let draft = snapshot.reviewDraft;
    let local = false;
    try {
      const pending = localStorage.getItem(this.reviewStorageKey(snapshot.jobId));
      if (pending) {
        draft = JSON.parse(pending) as OperationalContextAssistanceReviewDraft;
        local = true;
      }
    } catch { /* Fall back to the server's saved review. */ }
    if (!draft || !Array.isArray(draft.selections) || draft.selections.length !== snapshot.draft?.proposals.length) return;
    const selected: Record<number, string[]> = {};
    const confirmed: Record<number, string[]> = {};
    const edited: Record<number, Record<string, unknown>> = {};
    snapshot.draft.proposals.forEach((proposal, index) => {
      const entry = draft!.selections[index];
      const allowed = new Set(proposal.changes.map((change) => change.path));
      const paths = Array.isArray(entry?.selectedPaths) ? entry.selectedPaths.filter((path) => allowed.has(path)) : [];
      selected[index] = paths;
      confirmed[index] = Array.isArray(entry?.confirmedPaths)
        ? entry.confirmedPaths.filter((path) => paths.includes(path)) : [];
      edited[index] = Object.fromEntries(Object.entries(entry?.editedValues ?? {})
        .filter(([path]) => allowed.has(path)));
    });
    this.selectionByProposal.set(selected);
    this.confirmationsByProposal.set(confirmed);
    this.editedValuesByProposal.set(edited);
    if (local && !this.historyReadOnly()) this.persistReviewDraft();
  }

  private persistReviewDraft(): void {
    const snapshot = this.job();
    if (!snapshot || this.historyReadOnly() || this.reviewComplete()
      || (snapshot.status !== 'COMPLETED' && snapshot.status !== 'PARTIAL') || !snapshot.draft?.proposals.length) return;
    const draft: OperationalContextAssistanceReviewDraft = {
      selections: snapshot.draft.proposals.map((proposal, index) => ({
        selectedPaths: this.selectedPaths(index, proposal),
        confirmedPaths: this.confirmationsByProposal()[index] ?? [],
        editedValues: this.editedValuesByProposal()[index] ?? {}
      }))
    };
    const serialized = JSON.stringify(draft);
    try { localStorage.setItem(this.reviewStorageKey(snapshot.jobId), serialized); } catch { /* Server persistence still runs. */ }
    this.reviewSaves.next({ jobId: snapshot.jobId, draft, serialized });
  }

  start(event?: Event): void {
    event?.preventDefault();
    if (this.historyReadOnly()) return;
    if (this.isRunning() || this.previewBusy() || this.decisionBusy() || this.pendingDecisionCheck()) return;
    const description = this.descriptionControl.value.trim();
    if (!description || description.length > 4000) {
      this.error.set('Podaj opis o długości od 1 do 4000 znaków.');
      return;
    }
    const target = this.activePrefill().target;
    if (this.activePrefill().mode !== 'CREATE_AREA' && !target) {
      this.error.set('Wybierz encję lub finding w katalogu przed uruchomieniem asysty.');
      return;
    }
    const request: OperationalContextAssistanceRequest = {
      mode: this.activePrefill().mode,
      description,
      ...(target ? { target } : {})
    };
    if (this.includeGitLabControl.value) {
      if (this.sourceOptionsLoading()) {
        this.error.set('Poczekaj na sprawdzenie dostępnych projektów GitLab.');
        return;
      }
      if (this.sourceOptionsError() || !this.sourceOptions()?.configuredGroup || !this.sourceOptions()?.configuredBaseUrl) {
        this.error.set('Nie można teraz dołączyć projektu GitLab. Sprawdź adres serwera i grupę w konfiguracji albo wyłącz tę opcję.');
        return;
      }
      const ref = this.refControl.value.trim();
      const manual = this.manualProject();
      const project = this.catalogueProjectControl.value.trim();
      const projectUrl = this.projectUrlControl.value.trim();
      if (!(manual ? projectUrl : project) || !ref) {
        this.error.set('Wybierz projekt lub wklej jego pełny adres URL oraz podaj gałąź.');
        return;
      }
      if (/^(?:[a-fA-F0-9]{40}|[a-fA-F0-9]{64})$/.test(ref)) {
        this.error.set('Wybierz nazwę gałęzi. Commit zostanie przypięty automatycznie podczas analizy.');
        return;
      }
      if (manual && !this.isWebUrl(projectUrl)) {
        this.error.set('Wklej pełny adres URL projektu GitLab zaczynający się od https:// lub http://.');
        return;
      }
      request.gitLabSource = manual ? { projectUrl, ref } : { project, ref };
      if (request.mode === 'CREATE_AREA') {
        const usage = this.repositoryUsageControl.value;
        const facts: NonNullable<OperationalContextAssistanceRequest['repositoryFacts']> = { usage };
        if (usage === 'DEPLOYED_SYSTEM') {
          const systemName = this.systemNameControl.value.trim();
          const runtimeServiceName = this.runtimeServiceNameControl.value.trim();
          if (systemName.length > 160 || runtimeServiceName.length > 160) {
            this.error.set('Nazwa systemu i nazwa usługi mogą mieć maksymalnie 160 znaków.');
            return;
          }
          if (systemName) facts.systemName = systemName;
          if (runtimeServiceName) facts.runtimeServiceName = runtimeServiceName;
        } else if (usage === 'EXISTING_SYSTEM') {
          const systemId = this.existingSystemControl.value.trim();
          if (!systemId || !this.systemOptions().some((option) => option.id === systemId)) {
            this.error.set('Wybierz istniejący system z katalogu albo zmień sposób wykorzystania projektu.');
            return;
          }
          facts.systemIds = [systemId];
        } else if (usage === 'SHARED_LIBRARY' && this.librarySystemIds().length) {
          const systemIds = this.librarySystemIds();
          if (systemIds.length > 5 || systemIds.some((id) => !this.systemOptions().some((option) => option.id === id))) {
            this.error.set('Sprawdź wybrane systemy korzystające z biblioteki. Możesz wskazać maksymalnie 5 istniejących systemów.');
            return;
          }
          facts.systemIds = systemIds;
        }
        request.repositoryFacts = facts;
      }
    }
    this.pollSubscription?.unsubscribe();
    this.job.set(null);
    this.jobChanged.emit(null);
    this.reviewValidationVisible.set(false);
    this.contextSwitchNotice.set('');
    this.pollError.set(false);
    this.error.set('');
    this.decisionError.set('');
    this.pendingDecisionCheck.set(null);
    this.invalidateBatchPreview();
    this.selectionByProposal.set({});
    this.confirmationsByProposal.set({});
    this.editedValuesByProposal.set({});
    this.activeProposalIndex.set(0);
    this.cancelFieldEdit();
    this.starting.set(true);
    this.api.start(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.starting.set(false);
          this.job.set(snapshot);
          this.composerExpanded.set(false);
          this.jobChanged.emit(snapshot);
          if (!isTerminalAssistanceStatus(snapshot.status)) this.startPolling(snapshot.jobId);
        },
        error: (response: HttpErrorResponse) => {
          this.starting.set(false);
          this.error.set((response.error?.code === 'OPCTX_ASSISTANCE_INVALID_GITLAB_SOURCE'
              || response.error?.code === 'OPCTX_ASSISTANCE_GITLAB_NOT_CONFIGURED')
              && typeof response.error?.message === 'string'
            ? response.error.message
            : 'Nie udało się uruchomić asysty Operational Context.');
        }
      });
  }

  private isWebUrl(value: string): boolean {
    try {
      const url = new URL(value);
      return (url.protocol === 'https:' || url.protocol === 'http:')
        && !!url.hostname && !url.username && !url.password;
    } catch {
      return false;
    }
  }

  useManualProject(): void {
    this.manualProject.set(true);
    this.manualRef.set(false);
    this.catalogueProjectControl.setValue('');
    this.selectBranchSource('');
    this.error.set('');
  }

  useManualRef(): void {
    this.manualRef.set(true);
    this.refControl.setValue('');
    this.error.set('');
  }

  useBranchList(): void {
    this.manualRef.set(false);
    this.refControl.setValue('');
    this.error.set('');
  }

  selectBranch(branch: string): void {
    this.refControl.setValue(branch);
    this.error.set('');
  }

  private selectBranchSource(sourceKey: string, clearRef = true): void {
    if (this.branchSourceKey() !== sourceKey) this.branchSourceKey.set(sourceKey);
    if (clearRef) this.refControl.setValue('');
  }

  setLibrarySystemSelected(id: string, selected: boolean): void {
    if (!this.systemOptions().some((option) => option.id === id)) return;
    const ids = new Set(this.librarySystemIds());
    if (selected) {
      if (ids.size >= 5 && !ids.has(id)) {
        this.error.set('Możesz wskazać najwyżej 5 systemów korzystających z biblioteki.');
        return;
      }
      ids.add(id);
    } else {
      ids.delete(id);
    }
    this.librarySystemIds.set([...ids]);
    this.error.set('');
  }

  private resetRepositoryFacts(): void {
    this.repositoryUsageControl.setValue('UNKNOWN', { emitEvent: false });
    this.clearConditionalRepositoryFacts();
  }

  private clearConditionalRepositoryFacts(): void {
    this.systemNameControl.setValue('');
    this.runtimeServiceNameControl.setValue('');
    this.existingSystemControl.setValue('');
    this.librarySystemIds.set([]);
    this.error.set('');
  }

  useCatalogueProject(): void {
    this.manualProject.set(false);
    this.manualRef.set(false);
    this.projectUrlControl.setValue('');
    this.selectBranchSource('');
    this.error.set('');
  }

  retrySourceOptions(): void {
    this.loadSourceOptions();
  }

  private loadSourceOptions(): void {
    this.sourceOptionsLoading.set(true);
    this.sourceOptionsError.set('');
    this.api.sourceOptions()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (options) => {
          this.sourceOptionsLoading.set(false);
          this.sourceOptions.set(options);
          if (!options.projects.length && !this.manualProject()) {
            this.useManualProject();
          } else if (!this.manualProject() && this.catalogueProjectControl.value
            && !options.projects.some((project) => project.project === this.catalogueProjectControl.value)) {
            this.catalogueProjectControl.setValue('');
          }
        },
        error: () => {
          this.sourceOptionsLoading.set(false);
          this.sourceOptionsError.set('Nie udało się sprawdzić grupy i projektów GitLab.');
        }
      });
  }

  retryPolling(): void {
    if (this.historyReadOnly()) return;
    const currentJob = this.job();
    if (!currentJob || isTerminalAssistanceStatus(currentJob.status)) return;
    this.error.set('');
    this.pollError.set(false);
    this.startPolling(currentJob.jobId);
  }

  selectProposal(index: number): void {
    this.activeProposalIndex.set(index);
    this.cancelFieldEdit();
  }

  proposalTitle(proposalIndex: number, proposal: OperationalContextAssistanceProposal): string {
    const title = proposal.changes.find((change) => change.path === 'term' || change.path === 'name');
    const value = title ? this.effectiveAfter(proposalIndex, title) : null;
    return typeof value === 'string' && value.trim() ? value : proposal.entityId;
  }

  entityLabel(type: string): string {
    const labels: Record<string, string> = {
      system: 'System', repository: 'Repozytorium', 'code-search-scope': 'Zakres kodu',
      process: 'Proces', integration: 'Integracja', 'bounded-context': 'Kontekst',
      team: 'Zespół', 'glossary-term': 'Termin', 'handoff-rule': 'Reguła przekazania'
    };
    return labels[type] ?? type;
  }

  fieldLabel(path: string): string {
    const labels: Record<string, string> = {
      name: 'Nazwa', term: 'Termin', category: 'Kategoria', definition: 'Definicja',
      canonicalReferences: 'Powiązania', useFor: 'Kiedy używać', references: 'Powiązania',
      relatedTerms: 'Powiązane terminy',
      description: 'Opis', summary: 'Podsumowanie', repositories: 'Repozytoria',
      git: 'Projekt GitLab', ownership: 'Właściciel', participants: 'Uczestnicy'
    };
    return labels[path] ?? path.replace(/([a-z])([A-Z])/g, '$1 $2');
  }

  selectedFieldCount(): number {
    return (this.job()?.draft?.proposals ?? []).reduce((count, proposal, index) =>
      count + this.selectedPaths(index, proposal).length, 0);
  }

  selectedProposalCount(): number {
    return (this.job()?.draft?.proposals ?? []).filter((proposal, index) =>
      this.selectedPaths(index, proposal).length > 0).length;
  }

  editedFieldCount(): number {
    return (this.job()?.draft?.proposals ?? []).reduce((count, proposal, index) =>
      count + proposal.changes.filter((change) => this.isSelected(index, proposal, change.path)
        && this.isEdited(index, change.path)).length, 0);
  }

  canEditField(proposal: OperationalContextAssistanceProposal, change: OperationalContextAssistanceFieldChange): boolean {
    return !((proposal.entityType === 'repository' && change.path === 'git')
      || (proposal.entityType === 'code-search-scope' && change.path === 'repositories'));
  }

  isEdited(proposalIndex: number, path: string): boolean {
    const recorded = this.job()?.proposalDecisions?.find((decision) => decision.proposalIndex === proposalIndex);
    const values = recorded ? recorded.editedValues : this.editedValuesByProposal()[proposalIndex];
    return Object.prototype.hasOwnProperty.call(values ?? {}, path);
  }

  effectiveAfter(proposalIndex: number, change: OperationalContextAssistanceFieldChange): unknown {
    const recorded = this.job()?.proposalDecisions?.find((decision) => decision.proposalIndex === proposalIndex);
    const values = recorded ? recorded.editedValues : this.editedValuesByProposal()[proposalIndex];
    return Object.prototype.hasOwnProperty.call(values ?? {}, change.path)
      ? values![change.path] : change.after;
  }

  isEditing(proposalIndex: number, path: string): boolean {
    return this.editingField() === `${proposalIndex}|${path}`;
  }

  beginFieldEdit(proposalIndex: number, proposal: OperationalContextAssistanceProposal,
    change: OperationalContextAssistanceFieldChange): void {
    if (this.historyReadOnly() || this.reviewComplete() || this.decisionBusy()
      || this.pendingDecisionCheck() || !this.canEditField(proposal, change)) return;
    this.editingField.set(`${proposalIndex}|${change.path}`);
    this.editValueControl.setValue(this.valueText(this.effectiveAfter(proposalIndex, change)));
    this.editError.set('');
  }

  cancelFieldEdit(): void {
    this.editingField.set('');
    this.editError.set('');
  }

  saveFieldEdit(proposalIndex: number, proposal: OperationalContextAssistanceProposal,
    change: OperationalContextAssistanceFieldChange): void {
    if (!this.isEditing(proposalIndex, change.path) || !this.canEditField(proposal, change)) return;
    const raw = this.editValueControl.value;
    let value: unknown = raw;
    if (typeof change.after !== 'string') {
      try {
        value = JSON.parse(raw);
      } catch {
        this.editError.set('Podaj poprawny JSON dla tego pola.');
        return;
      }
      if (value === null || Array.isArray(change.after) !== Array.isArray(value)
        || (!Array.isArray(change.after) && (typeof value !== 'object' || Array.isArray(value)))) {
        this.editError.set('Zachowaj typ wartości: listę lub obiekt, zgodnie z propozycją.');
        return;
      }
    }
    if (new TextEncoder().encode(JSON.stringify(value)).length > 16_384) {
      this.editError.set('Ta poprawka jest zbyt długa. Limit wynosi 16 KiB na pole.');
      return;
    }
    const current = { ...(this.editedValuesByProposal()[proposalIndex] ?? {}) };
    if (JSON.stringify(value) === JSON.stringify(change.after)) delete current[change.path];
    else current[change.path] = value;
    this.editedValuesByProposal.update((all) => ({ ...all, [proposalIndex]: current }));
    this.setConfirmed(proposalIndex, change.path, false);
    this.invalidateBatchPreview();
    this.decisionError.set('');
    this.cancelFieldEdit();
  }

  resetFieldEdit(proposalIndex: number, path: string): void {
    if (this.historyReadOnly() || this.reviewComplete() || this.decisionBusy() || this.pendingDecisionCheck()) return;
    const current = { ...(this.editedValuesByProposal()[proposalIndex] ?? {}) };
    delete current[path];
    this.editedValuesByProposal.update((all) => ({ ...all, [proposalIndex]: current }));
    this.setConfirmed(proposalIndex, path, false);
    this.invalidateBatchPreview();
    this.cancelFieldEdit();
  }

  selectedPaths(proposalIndex: number, proposal: OperationalContextAssistanceProposal): string[] {
    const recorded = this.job()?.proposalDecisions?.find((decision) => decision.proposalIndex === proposalIndex);
    if (recorded) return recorded.selectedPaths;
    return this.selectionByProposal()[proposalIndex] ?? proposal.changes.map((change) => change.path);
  }

  isSelected(proposalIndex: number, proposal: OperationalContextAssistanceProposal, path: string): boolean {
    return this.selectedPaths(proposalIndex, proposal).includes(path);
  }

  setSelected(proposalIndex: number, proposal: OperationalContextAssistanceProposal, path: string, selected: boolean): void {
    if (this.historyReadOnly() || this.reviewComplete() || this.decisionBusy() || this.pendingDecisionCheck()) return;
    const paths = new Set(this.selectedPaths(proposalIndex, proposal));
    if (selected) paths.add(path);
    else paths.delete(path);
    this.selectionByProposal.update((current) => ({ ...current, [proposalIndex]: [...paths] }));
    if (!selected) this.confirmationsByProposal.update((current) => ({
      ...current, [proposalIndex]: (current[proposalIndex] ?? []).filter((confirmed) => confirmed !== path)
    }));
    this.persistReviewDraft();
    this.invalidateBatchPreview();
    this.decisionError.set('');
  }

  setProposalSelected(proposalIndex: number, proposal: OperationalContextAssistanceProposal, selected: boolean): void {
    if (this.historyReadOnly() || this.reviewComplete() || this.decisionBusy() || this.pendingDecisionCheck()) return;
    this.selectionByProposal.update((current) => ({
      ...current, [proposalIndex]: selected ? proposal.changes.map((change) => change.path) : []
    }));
    if (!selected) this.confirmationsByProposal.update((current) => ({ ...current, [proposalIndex]: [] }));
    this.persistReviewDraft();
    this.invalidateBatchPreview();
    this.decisionError.set('');
  }

  missingConfirmationCount(proposalIndex: number, proposal: OperationalContextAssistanceProposal): number {
    return proposal.changes.filter((change) => this.isSelected(proposalIndex, proposal, change.path)
      && this.requiresConfirmation(proposalIndex, proposal, change)
      && !this.isConfirmed(proposalIndex, change.path)).length;
  }

  omittedFieldCount(proposalIndex: number, proposal: OperationalContextAssistanceProposal): number {
    return proposal.changes.filter((change) => !this.isSelected(proposalIndex, proposal, change.path)).length;
  }

  totalMissingConfirmationCount(): number {
    return this.job()?.draft?.proposals.reduce((total, proposal, index) =>
      total + this.missingConfirmationCount(index, proposal), 0) ?? 0;
  }

  totalOmittedFieldCount(): number {
    return this.job()?.draft?.proposals.reduce((total, proposal, index) =>
      total + this.omittedFieldCount(index, proposal), 0) ?? 0;
  }

  reviewIssueForProposal(proposalIndex: number, proposal: OperationalContextAssistanceProposal): 'confirmation' | 'omitted' | null {
    if (!this.reviewValidationVisible() || this.historyReadOnly() || this.reviewComplete()) return null;
    if (this.missingConfirmationCount(proposalIndex, proposal)) return 'confirmation';
    return this.omittedFieldCount(proposalIndex, proposal) ? 'omitted' : null;
  }

  reviewIssueForChange(proposalIndex: number, proposal: OperationalContextAssistanceProposal,
    change: OperationalContextAssistanceFieldChange): 'confirmation' | 'omitted' | null {
    if (!this.reviewValidationVisible() || this.historyReadOnly() || this.reviewComplete()) return null;
    if (!this.isSelected(proposalIndex, proposal, change.path)) return 'omitted';
    return this.requiresConfirmation(proposalIndex, proposal, change)
      && !this.isConfirmed(proposalIndex, change.path) ? 'confirmation' : null;
  }

  firstReviewIssueIndex(): number {
    const proposals = this.job()?.draft?.proposals ?? [];
    const unconfirmed = proposals.findIndex((proposal, index) => this.missingConfirmationCount(index, proposal) > 0);
    return unconfirmed >= 0 ? unconfirmed
      : proposals.findIndex((proposal, index) => this.omittedFieldCount(index, proposal) > 0);
  }

  showFirstReviewIssue(): void {
    const index = this.firstReviewIssueIndex();
    if (index < 0) return;
    this.selectProposal(index);
    const item = this.reviewList?.nativeElement.querySelectorAll<HTMLButtonElement>('.assistance-review__item')[index];
    item?.focus();
    item?.scrollIntoView?.({ block: 'nearest' });
  }

  batchIssuesForProposal(proposalIndex: number): BatchFieldIssue[] {
    return this.batchFieldIssues().filter((issue) => issue.proposalIndex === proposalIndex);
  }

  batchIssuesForChange(proposalIndex: number, path: string): BatchFieldIssue[] {
    return this.batchFieldIssues().filter((issue) => issue.proposalIndex === proposalIndex && issue.path === path);
  }

  showBatchIssue(issue: BatchFieldIssue): void {
    this.selectProposal(issue.proposalIndex);
    setTimeout(() => {
      const field = document.getElementById(`assistance-change-${issue.proposalIndex}-${issue.path}`);
      field?.scrollIntoView?.({ block: 'center' });
    });
  }

  batchIssueText(issue: BatchFieldIssue): string {
    const location = issue.position === null ? this.fieldLabel(issue.path)
      : `${this.fieldLabel(issue.path)}, pozycja ${issue.position + 1}`;
    const detail = issue.message === 'Referenced entity does not exist'
      ? 'Wskazany identyfikator nie istnieje w wynikowym katalogu. Popraw lub usuń odwołanie albo dołącz propozycję tworzącą ten wpis.'
      : issue.message;
    return `${location}${issue.valueLabel ? `: ${issue.valueLabel}` : ''} — ${detail}`;
  }

  hasBulkApprovableChanges(proposalIndex: number, proposal: OperationalContextAssistanceProposal): boolean {
    const selected = this.selectedPaths(proposalIndex, proposal);
    return proposal.changes.some((change) => !selected.includes(change.path)
      || (this.requiresConfirmation(proposalIndex, proposal, change)
        && !this.isEdited(proposalIndex, change.path) && !this.isConfirmed(proposalIndex, change.path)));
  }

  hasUnconfirmedEdits(proposalIndex: number, proposal: OperationalContextAssistanceProposal): boolean {
    return proposal.changes.some((change) => this.isSelected(proposalIndex, proposal, change.path)
      && this.isEdited(proposalIndex, change.path) && !this.isConfirmed(proposalIndex, change.path));
  }

  bulkApprovalLabel(proposalIndex: number, proposal: OperationalContextAssistanceProposal): string {
    if (this.hasBulkApprovableChanges(proposalIndex, proposal)) return 'Zatwierdź wszystkie pola';
    return this.hasUnconfirmedEdits(proposalIndex, proposal)
      ? 'Potwierdź poprawki w szczegółach' : 'Pola gotowe do sprawdzenia';
  }

  approveProposalChanges(proposalIndex: number, proposal: OperationalContextAssistanceProposal): void {
    if (this.historyReadOnly() || this.reviewComplete() || this.previewBusy() || this.decisionBusy()
      || this.pendingDecisionCheck() || !this.hasBulkApprovableChanges(proposalIndex, proposal)) return;
    const paths = proposal.changes.map((change) => change.path);
    const confirmed = new Set(this.confirmationsByProposal()[proposalIndex] ?? []);
    for (const change of proposal.changes) {
      if (this.requiresConfirmation(proposalIndex, proposal, change) && !this.isEdited(proposalIndex, change.path)) {
        confirmed.add(change.path);
      }
    }
    this.selectionByProposal.update((current) => ({ ...current, [proposalIndex]: paths }));
    this.confirmationsByProposal.update((current) => ({ ...current, [proposalIndex]: [...confirmed] }));
    this.persistReviewDraft();
    this.invalidateBatchPreview();
    this.decisionError.set('');
  }

  requiresConfirmation(proposalIndex: number, proposal: OperationalContextAssistanceProposal,
    change: OperationalContextAssistanceFieldChange): boolean {
    return proposal.requiresConfirmation || change.requiresConfirmation || this.isEdited(proposalIndex, change.path);
  }

  isConfirmed(proposalIndex: number, path: string): boolean {
    return (this.confirmationsByProposal()[proposalIndex] ?? []).includes(path);
  }

  setConfirmed(proposalIndex: number, path: string, confirmed: boolean): void {
    if (this.historyReadOnly() || this.reviewComplete() || this.decisionBusy() || this.pendingDecisionCheck()) return;
    const paths = new Set(this.confirmationsByProposal()[proposalIndex] ?? []);
    if (confirmed) paths.add(path);
    else paths.delete(path);
    this.confirmationsByProposal.update((current) => ({ ...current, [proposalIndex]: [...paths] }));
    this.persistReviewDraft();
    this.invalidateBatchPreview();
    this.decisionError.set('');
  }

  reviewDecisions(): OperationalContextAssistanceProposalDecisionRequest[] {
    return (this.job()?.draft?.proposals ?? []).map((proposal, index) => {
      const selected = this.selectedPaths(index, proposal);
      const selectedPaths = proposal.changes.map((change) => change.path).filter((path) => selected.includes(path));
      if (!selectedPaths.length) return { action: 'SKIP', selectedPaths: [], confirmedPaths: [] };
      const edits = this.editedValuesByProposal()[index] ?? {};
      const editedValues = Object.fromEntries(selectedPaths.filter((path) => Object.prototype.hasOwnProperty.call(edits, path))
        .map((path) => [path, edits[path]]));
      return {
        action: 'APPLY', selectedPaths,
        confirmedPaths: selectedPaths.filter((path) => this.isConfirmed(index, path)),
        ...(Object.keys(editedValues).length ? { editedValues } : {})
      };
    });
  }

  selectedDiff(): { entity: string; path: string; before: unknown; after: unknown }[] {
    return (this.job()?.draft?.proposals ?? []).flatMap((proposal, index) =>
      proposal.changes.filter((change) => this.isSelected(index, proposal, change.path))
        .map((change) => ({ entity: `${proposal.entityType}/${proposal.entityId}`, path: change.path,
          before: change.before, after: this.effectiveAfter(index, change) })));
  }

  canPreview(): boolean {
    const snapshot = this.job();
    return Boolean(!this.historyReadOnly() && snapshot?.draft?.proposals.length
      && (snapshot.status === 'COMPLETED' || snapshot.status === 'PARTIAL')
      && !this.reviewComplete() && !this.previewBusy() && !this.decisionBusy()
      && !this.pendingDecisionCheck() && !this.editingField());
  }

  canSave(): boolean {
    const snapshot = this.job();
    const preview = this.batchPreview();
    if (!snapshot || !this.canPreview() || !preview?.valid || !preview.candidateDigest
      || this.reviewedSelection() !== JSON.stringify(this.reviewDecisions())) return false;
    return snapshot.draft!.proposals.every((proposal, index) => {
      const selected = this.selectedPaths(index, proposal);
      return proposal.changes.every((change) => !selected.includes(change.path)
        || !this.requiresConfirmation(index, proposal, change) || this.isConfirmed(index, change.path));
    });
  }

  decisionLabel(proposalIndex: number): string {
    const decision = this.job()?.proposalDecisions?.find((item) => item.proposalIndex === proposalIndex);
    if (decision?.action === 'APPLY') return 'Zapisano w katalogu';
    if (decision?.action === 'SKIP') return 'Pominięto';
    const proposal = this.job()?.draft?.proposals[proposalIndex];
    return proposal && !this.selectedPaths(proposalIndex, proposal).length ? 'Pominięto w wyborze' : 'W zestawie';
  }

  previewSelection(): void {
    const snapshot = this.job();
    if (!snapshot || !this.canPreview()) return;
    this.reviewValidationVisible.set(true);
    const decisions = this.reviewDecisions();
    const selection = JSON.stringify(decisions);
    this.previewBusy.set(true);
    this.decisionError.set('');
    this.invalidateBatchPreview();
    this.api.previewBatch(snapshot.jobId, { decisions })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (preview) => {
          this.previewBusy.set(false);
          if (selection !== JSON.stringify(this.reviewDecisions()) || this.job()?.jobId !== snapshot.jobId) return;
          this.batchPreview.set(preview);
          this.reviewedSelection.set(selection);
        },
        error: (error: HttpErrorResponse) => {
          this.previewBusy.set(false);
          if (selection !== JSON.stringify(this.reviewDecisions()) || this.job()?.jobId !== snapshot.jobId) return;
          this.captureBatchErrors(error, decisions);
        }
      });
  }

  saveBatch(): void {
    const snapshot = this.job();
    const preview = this.batchPreview();
    if (!snapshot || !preview || !this.canSave()) return;
    const decisions = this.reviewDecisions();
    this.decisionBusy.set(true);
    this.decisionError.set('');
    this.api.decideBatch(snapshot.jobId, { decisions, candidateDigest: preview.candidateDigest })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.decisionBusy.set(false);
          try { localStorage.removeItem(this.reviewStorageKey(updated.jobId)); } catch { /* Storage can be unavailable. */ }
          this.job.set(updated);
          this.jobChanged.emit(updated);
          this.notifyCatalogChanged(updated);
        },
        error: (error: HttpErrorResponse) => {
          const message = typeof error.error?.message === 'string' ? error.error.message : '';
          this.pendingDecisionCheck.set({
            jobId: snapshot.jobId, decisions, errorStatus: error.status, errorMessage: message
          });
          this.retryDecisionCheck();
        }
      });
  }

  retryDecisionCheck(): void {
    if (this.historyReadOnly()) return;
    const pending = this.pendingDecisionCheck();
    if (!pending) return;
    this.decisionBusy.set(true);
    this.decisionError.set('');
    this.api.get(pending.jobId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (updated) => {
          this.decisionBusy.set(false);
          this.pendingDecisionCheck.set(null);
          this.job.set(updated);
          this.jobChanged.emit(updated);
          if (!updated.proposalDecisions?.length) {
            this.invalidateBatchPreview();
            this.decisionError.set(this.decisionFailureMessage(pending));
          } else if (!this.sameDecisions(updated.proposalDecisions, pending.decisions)) {
            this.notifyCatalogChanged(updated);
            this.decisionError.set('Ten zestaw ma już inną decyzję. Katalog został odświeżony, sprawdź stan wpisów.');
          } else {
            this.notifyCatalogChanged(updated);
          }
        },
        error: () => {
          this.decisionBusy.set(false);
          this.decisionError.set('Nie udało się ustalić, czy decyzja została zapisana. Sprawdź stan tego samego joba przed ponowną próbą zapisu.');
        }
      });
  }

  private decisionFailureMessage(pending: PendingDecisionCheck): string {
    const message = pending.errorMessage;
    return pending.errorStatus === 409
      ? `${message || 'Katalog lub zestaw propozycji zmieniły się.'} Wybór pól pozostał zachowany. Sprawdź katalog i przygotuj nowy podgląd.`
      : pending.errorStatus === 422
        ? `${message || 'Wybrane pola nie tworzą poprawnego katalogu.'} Zmień wybór pól i sprawdź cały zestaw ponownie.`
        : message || 'Nie udało się zapisać zestawu. Wybór pól pozostał zachowany; sprawdź podgląd ponownie.';
  }

  private samePaths(left: string[], right: string[]): boolean {
    return left.length === right.length && left.every((path) => right.includes(path));
  }

  private sameDecisions(actual: OperationalContextAssistanceJob['proposalDecisions'], expected: OperationalContextAssistanceProposalDecisionRequest[]): boolean {
    return actual?.length === expected.length && expected.every((choice, index) => {
      const decision = actual[index];
      return decision.proposalIndex === index && decision.action === choice.action
        && this.samePaths(decision.selectedPaths, choice.selectedPaths)
        && JSON.stringify(decision.editedValues ?? {}) === JSON.stringify(choice.editedValues ?? {});
    });
  }

  private notifyCatalogChanged(snapshot: OperationalContextAssistanceJob): void {
    const applied = snapshot.proposalDecisions?.find((decision) => decision.action === 'APPLY');
    const proposal = applied && snapshot.draft?.proposals[applied.proposalIndex];
    if (proposal) this.catalogChanged.emit({ type: proposal.entityType, id: proposal.entityId });
  }

  private invalidateBatchPreview(): void {
    this.batchPreview.set(null);
    this.reviewedSelection.set('');
    this.batchFieldIssues.set([]);
    this.batchGeneralErrors.set([]);
  }

  private captureBatchErrors(error: HttpErrorResponse, decisions: OperationalContextAssistanceProposalDecisionRequest[]): void {
    const rawErrors: unknown[] = Array.isArray(error.error?.fieldErrors) ? error.error.fieldErrors : [];
    const appliedIndexes = decisions.flatMap((decision, index) => decision.action === 'APPLY' ? [index] : []);
    const proposals = this.job()?.draft?.proposals ?? [];
    const mapped: BatchFieldIssue[] = [];
    const general: string[] = [];
    for (const raw of rawErrors) {
      const field = typeof raw === 'object' && raw !== null ? raw as Record<string, unknown> : {};
      const pointer = typeof field['field'] === 'string' ? field['field'] : '';
      const message = typeof field['message'] === 'string' ? field['message'] : 'Niepoprawna wartość';
      const match = /^\/mutations\/(\d+)\/payload\/([^/]+)(?:\/(.*))?$/.exec(pointer);
      const proposalIndex = match ? appliedIndexes[Number(match[1])] : undefined;
      const path = match?.[2].replace(/~1/g, '/').replace(/~0/g, '~');
      const proposal = proposalIndex === undefined ? undefined : proposals[proposalIndex];
      const change = proposal?.changes.find((item) => item.path === path && this.isSelected(proposalIndex!, proposal, item.path));
      if (!change || !path) {
        general.push(`${pointer || 'Zestaw'}: ${message}`);
        continue;
      }
      const segments = match?.[3]?.split('/').map((segment) => segment.replace(/~1/g, '/').replace(/~0/g, '~')) ?? [];
      const value = segments.reduce<unknown>((current, segment) => {
        if (Array.isArray(current) && /^\d+$/.test(segment)) return current[Number(segment)];
        if (current && typeof current === 'object' && Object.prototype.hasOwnProperty.call(current, segment))
          return (current as Record<string, unknown>)[segment];
        return undefined;
      }, this.effectiveAfter(proposalIndex!, change));
      const valueLabel = typeof value === 'string' || typeof value === 'number' ? `„${String(value)}”` : '';
      mapped.push({ proposalIndex: proposalIndex!, path, pointer, message, valueLabel,
        position: segments.length && /^\d+$/.test(segments[0]) ? Number(segments[0]) : null });
    }
    this.batchFieldIssues.set(mapped);
    this.batchGeneralErrors.set(general);
    const errorNoun = mapped.length === 1 ? 'błąd' : mapped.length >= 2 && mapped.length <= 4 ? 'błędy' : 'błędów';
    this.decisionError.set(mapped.length
      ? `Zestaw zawiera ${mapped.length} ${errorNoun} walidacji. Otwórz wskazaną propozycję i popraw pole.`
      : typeof error.error?.message === 'string' ? error.error.message
        : 'Nie udało się sprawdzić całego zestawu zmian. Wybór pól pozostał zachowany.');
  }

  valueText(value: unknown): string {
    if (value === undefined) return '—';
    if (typeof value === 'string') return value;
    return JSON.stringify(value, null, 2) ?? 'null';
  }

  modeLabel(): string {
    switch (this.activePrefill().mode) {
      case 'CREATE_AREA': return 'Utwórz lub uzupełnij katalog';
      case 'IMPROVE_ENTITY': return 'Uzupełnij wpis';
      case 'RESOLVE_FINDING': return 'Wyjaśnij problem katalogu';
    }
  }

  targetKindLabel(kind: 'ENTITY' | 'VALIDATION_FINDING' | 'OPEN_QUESTION'): string {
    switch (kind) {
      case 'ENTITY': return 'Wpis';
      case 'VALIDATION_FINDING': return 'Finding walidacji';
      case 'OPEN_QUESTION': return 'Otwarte pytanie';
    }
  }

  statusLabel(status: OperationalContextAssistanceJob['status']): string {
    const labels: Record<OperationalContextAssistanceJob['status'], string> = {
      QUEUED: 'W kolejce',
      COLLECTING_CONTEXT: 'Zbieranie kontekstu',
      AI_PREPARATION: 'Przygotowanie AI',
      ANALYZING: 'Analiza',
      COMPLETED: 'Ukończono',
      PARTIAL: 'Częściowy wynik',
      BLOCKED: 'Zablokowano',
      FAILED: 'Niepowodzenie'
    };
    return labels[status];
  }

  operationLabel(operation: 'CREATE' | 'UPDATE'): string {
    return operation === 'CREATE' ? 'Utwórz' : 'Zmień';
  }

  basisLabel(basis: OperationalContextAssistanceFieldChange['basis']): string {
    switch (basis) {
      case 'USER_STATEMENT': return 'opis operatora';
      case 'SOURCE_OBSERVATION': return 'odczyt źródła';
      case 'AI_INTERPRETATION': return 'interpretacja AI';
    }
  }

  confidenceLabel(confidence: OperationalContextAssistanceFieldChange['confidence']): string {
    switch (confidence) {
      case 'LOW': return 'niska pewność';
      case 'MEDIUM': return 'średnia pewność';
      case 'HIGH': return 'wysoka pewność';
    }
  }

  private startPolling(jobId: string): void {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = this.polling.poll({
      load: () => this.api.get(jobId),
      isTerminal: (snapshot) => isTerminalAssistanceStatus(snapshot.status),
      intervalMs: 1500
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.job.set(snapshot);
          this.jobChanged.emit(snapshot);
          this.pollError.set(false);
          this.error.set('');
        },
        error: () => {
          this.pollError.set(true);
          this.error.set('Nie udało się odświeżyć przebiegu asysty. Ponów odczyt tego samego joba.');
        }
      });
  }
}
