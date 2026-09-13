import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnChanges, OnDestroy, OnInit, SimpleChanges, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subscription, debounceTime } from 'rxjs';

import { AnalysisStepsPanelComponent } from '../../../components/analysis-steps-panel/analysis-steps-panel';
import { AnalysisFeatureAsideComponent } from '../../../components/analysis-feature-aside/analysis-feature-aside';
import { GitLabBranchSelectComponent } from '../../../components/gitlab-branch-select/gitlab-branch-select';
import { AnalysisAiUsage } from '../../../core/models/analysis.models';
import { AnalysisJobPollingService } from '../../../core/services/analysis-job-polling.service';
import { estimateAnalysisAiCost } from '../../../core/utils/analysis-ai-usage-cost.utils';
import {
  OperationalContextAssistanceJob,
  OperationalContextAssistanceBatchPreview,
  OperationalContextAssistanceProposalDecisionRequest,
  OperationalContextAssistanceFieldChange,
  OperationalContextAssistanceProposal,
  OperationalContextAssistancePrefill,
  OperationalContextAssistanceRequest,
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

@Component({
  selector: 'app-context-assistance-panel',
  imports: [ReactiveFormsModule, AnalysisStepsPanelComponent, AnalysisFeatureAsideComponent, GitLabBranchSelectComponent],
  templateUrl: './context-assistance-panel.html',
  styleUrl: './context-assistance-panel.scss'
})
export class ContextAssistancePanelComponent implements OnInit, OnChanges, OnDestroy {
  private readonly api = inject(OperationalContextAssistanceApiService);
  private readonly polling = inject(AnalysisJobPollingService);
  private readonly destroyRef = inject(DestroyRef);
  private pollSubscription?: Subscription;

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
  readonly pendingDecisionCheck = signal<PendingDecisionCheck | null>(null);
  readonly selectionByProposal = signal<Record<number, string[]>>({});
  readonly confirmationsByProposal = signal<Record<number, string[]>>({});
  readonly isRunning = computed(() => this.starting() || Boolean(this.job() && !isTerminalAssistanceStatus(this.job()!.status)));
  readonly reviewComplete = computed(() => Boolean(this.job()?.proposalDecisions?.length));
  readonly analysisProgressRunning = computed(() => Boolean(!this.historyReadOnly() && this.job()
    && !isTerminalAssistanceStatus(this.job()!.status)));
  readonly aiWorkRunning = computed(() => !this.historyReadOnly() && this.job()?.status === 'ANALYZING');
  readonly loadProjectBranches = (sourceKey: string, search: string) => sourceKey.startsWith('url:')
    ? this.api.sourceBranches({ projectUrl: sourceKey.slice(4) }, search)
    : this.api.sourceBranches({ project: sourceKey.slice(8) }, search);

  ngOnInit(): void {
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
      if (!this.historyReadOnly() && !isTerminalAssistanceStatus(previousJob.status)) this.startPolling(previousJob.jobId);
    }
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (this.historyReadOnly()) {
      if (changes['initialJob'] || changes['prefill'] || changes['historyReadOnly']) {
        this.pollSubscription?.unsubscribe();
        this.job.set(this.initialJob());
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
      }
      if (incoming && (incoming.jobId !== this.job()?.jobId || incoming.updatedAt !== this.job()?.updatedAt)) {
        this.pollSubscription?.unsubscribe();
        this.job.set(incoming);
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
        this.decisionError.set('');
        this.invalidateBatchPreview();
      }
      this.activePrefill.set(this.prefill());
      this.resetRepositoryFacts();
      this.contextSwitchNotice.set('');
      this.descriptionControl.setValue(this.activePrefill().description ?? '');
      this.error.set('');
    }
  }

  ngOnDestroy(): void {
    this.pollSubscription?.unsubscribe();
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
    this.contextSwitchNotice.set('');
    this.pollError.set(false);
    this.error.set('');
    this.decisionError.set('');
    this.pendingDecisionCheck.set(null);
    this.invalidateBatchPreview();
    this.selectionByProposal.set({});
    this.confirmationsByProposal.set({});
    this.starting.set(true);
    this.api.start(request)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.starting.set(false);
          this.job.set(snapshot);
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
    if (!selected) this.setConfirmed(proposalIndex, path, false);
    this.invalidateBatchPreview();
    this.decisionError.set('');
  }

  setProposalSelected(proposalIndex: number, proposal: OperationalContextAssistanceProposal, selected: boolean): void {
    if (this.historyReadOnly() || this.reviewComplete() || this.decisionBusy() || this.pendingDecisionCheck()) return;
    this.selectionByProposal.update((current) => ({
      ...current, [proposalIndex]: selected ? proposal.changes.map((change) => change.path) : []
    }));
    if (!selected) this.confirmationsByProposal.update((current) => ({ ...current, [proposalIndex]: [] }));
    this.invalidateBatchPreview();
    this.decisionError.set('');
  }

  requiresConfirmation(proposal: OperationalContextAssistanceProposal, change: OperationalContextAssistanceFieldChange): boolean {
    return proposal.requiresConfirmation || change.requiresConfirmation;
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
    this.invalidateBatchPreview();
    this.decisionError.set('');
  }

  reviewDecisions(): OperationalContextAssistanceProposalDecisionRequest[] {
    return (this.job()?.draft?.proposals ?? []).map((proposal, index) => {
      const selected = this.selectedPaths(index, proposal);
      const selectedPaths = proposal.changes.map((change) => change.path).filter((path) => selected.includes(path));
      return selectedPaths.length
        ? { action: 'APPLY', selectedPaths, confirmedPaths: selectedPaths.filter((path) => this.isConfirmed(index, path)) }
        : { action: 'SKIP', selectedPaths: [], confirmedPaths: [] };
    });
  }

  selectedDiff(): { entity: string; path: string; before: unknown; after: unknown }[] {
    return (this.job()?.draft?.proposals ?? []).flatMap((proposal, index) =>
      proposal.changes.filter((change) => this.isSelected(index, proposal, change.path))
        .map((change) => ({ entity: `${proposal.entityType}/${proposal.entityId}`, path: change.path,
          before: change.before, after: change.after })));
  }

  canPreview(): boolean {
    const snapshot = this.job();
    return Boolean(!this.historyReadOnly() && snapshot?.draft?.proposals.length
      && (snapshot.status === 'COMPLETED' || snapshot.status === 'PARTIAL')
      && !this.reviewComplete() && !this.previewBusy() && !this.decisionBusy() && !this.pendingDecisionCheck());
  }

  canSave(): boolean {
    const snapshot = this.job();
    const preview = this.batchPreview();
    if (!snapshot || !this.canPreview() || !preview?.valid || !preview.candidateDigest
      || this.reviewedSelection() !== JSON.stringify(this.reviewDecisions())) return false;
    return snapshot.draft!.proposals.every((proposal, index) => {
      const selected = this.selectedPaths(index, proposal);
      return proposal.changes.every((change) => !selected.includes(change.path)
        || !this.requiresConfirmation(proposal, change) || this.isConfirmed(index, change.path));
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
          this.decisionError.set(typeof error.error?.message === 'string' ? error.error.message
            : 'Nie udało się sprawdzić całego zestawu zmian. Wybór pól pozostał zachowany.');
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
        && this.samePaths(decision.selectedPaths, choice.selectedPaths);
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

  usageCost(usage: AnalysisAiUsage): string {
    const estimate = estimateAnalysisAiCost(usage);
    return estimate ? `~${estimate.dollars.toFixed(4)} USD (szacunek)` : 'brak szacunku kosztu';
  }

  usageCredits(usage: AnalysisAiUsage): string {
    const estimate = estimateAnalysisAiCost(usage);
    return estimate ? `~${estimate.credits.toFixed(2)}` : '—';
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
