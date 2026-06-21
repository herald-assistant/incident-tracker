import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatTooltipModule } from '@angular/material/tooltip';
import { finalize } from 'rxjs';

import {
  AnalysisAiUsage,
  AnalysisAiModelOptionsResponse,
  ApiErrorResponse
} from '../../../../core/models/analysis.models';
import { AnalysisApiService } from '../../../../core/services/analysis-api.service';
import {
  FlowExplorerAnalysisGoal,
  FlowExplorerEndpointInventoryResponse,
  FlowExplorerEndpointOption,
  FlowExplorerEndpointParameter,
  FlowExplorerEndpointSource,
  FlowExplorerFocusArea,
  FlowExplorerJobStartRequest,
  FlowExplorerJobStateSnapshot,
  FlowExplorerSystemOption
} from '../../models/flow-explorer.models';
import { FlowExplorerApiService } from '../../services/flow-explorer-api.service';
import { downloadJsonFile, readJsonFile } from '../../../../core/utils/json-file.utils';
import {
  buildFlowExplorerExportEnvelope,
  buildFlowExplorerExportFileName,
  FlowExplorerExportState,
  normalizeFlowExplorerJob,
  parseImportedFlowExplorerAnalysis
} from '../../utils/flow-explorer-import-export.utils';
import { AnalysisFollowUpChatComponent } from '../../../../components/analysis-follow-up-chat/analysis-follow-up-chat';
import { AnalysisStepsPanelComponent } from '../../../../components/analysis-steps-panel/analysis-steps-panel';
import { MarkdownContentComponent } from '../../../../components/markdown-content/markdown-content';
import { copyElementToClipboard } from '../../../../core/utils/clipboard.utils';
import {
  AnalysisAiCostEstimate,
  estimateAnalysisAiCost,
  GITHUB_AI_CREDIT_USD
} from '../../../../core/utils/analysis-ai-usage-cost.utils';

type CatalogState = 'empty' | 'loading' | 'ready' | 'error';
type EndpointState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

interface FlowExplorerChoice<T extends string> {
  value: T;
  label: string;
  hint: string;
  disabled?: boolean;
}

interface FlowExplorerUsageStat {
  label: string;
  value: string;
}

const POLL_INTERVAL_MS = 1500;
const MAX_FOCUS_AREAS = 4;
const EMPTY_AI_MODEL_OPTIONS: AnalysisAiModelOptionsResponse = {
  defaultModel: '',
  defaultReasoningEffort: '',
  defaultReasoningEfforts: [],
  models: []
};

const ANALYSIS_GOALS: FlowExplorerChoice<FlowExplorerAnalysisGoal>[] = [
  {
    value: 'DEEP_DISCOVERY',
    label: 'Deep Discovery',
    hint: 'Complex endpoint understanding across flow, rules, data and integrations.'
  },
  {
    value: 'TEST_SCENARIOS',
    label: 'Test scenarios',
    hint: 'Prepare test coverage, data setup and negative paths.'
  },
  {
    value: 'RISK_DETECTION',
    label: 'Risk detection',
    hint: 'Find risks, visibility gaps and likely regression areas.'
  }
];

const FOCUS_AREA_OPTIONS: FlowExplorerChoice<FlowExplorerFocusArea>[] = [
  {
    value: 'BUSINESS_FLOW_RULES',
    label: 'Business flow/rules',
    hint: 'Deepen request path, decisions and business rules.'
  },
  {
    value: 'VALIDATIONS',
    label: 'Validations',
    hint: 'Input checks, rejected states and required data.'
  },
  {
    value: 'PERSISTENCE',
    label: 'Persistence',
    hint: 'Repositories, entities and stored state touched by the flow.'
  },
  {
    value: 'INTEGRATIONS',
    label: 'Integrations',
    hint: 'Deepen outbound clients, queues, events and handoffs.'
  }
];

@Component({
  selector: 'app-flow-explorer-page',
  imports: [
    MatTooltipModule,
    AnalysisFollowUpChatComponent,
    AnalysisStepsPanelComponent,
    MarkdownContentComponent
  ],
  templateUrl: './flow-explorer-page.html',
  styleUrl: './flow-explorer-page.scss'
})
export class FlowExplorerPageComponent implements OnInit {
  private readonly flowExplorerApi = inject(FlowExplorerApiService);
  private readonly analysisApi = inject(AnalysisApiService);
  private readonly destroyRef = inject(DestroyRef);
  private pollingTimer: ReturnType<typeof setInterval> | null = null;
  private resultCopyFeedbackHandle: number | null = null;

  readonly analysisGoals = ANALYSIS_GOALS;
  readonly focusAreaOptions = FOCUS_AREA_OPTIONS;
  readonly maxFocusAreas = MAX_FOCUS_AREAS;

  readonly catalogState = signal<CatalogState>('empty');
  readonly endpointState = signal<EndpointState>('idle');
  readonly systems = signal<FlowExplorerSystemOption[]>([]);
  readonly endpointInventory = signal<FlowExplorerEndpointInventoryResponse | null>(null);
  readonly catalogError = signal('');
  readonly endpointError = signal('');
  readonly configError = signal('');
  readonly systemSearch = signal('');
  readonly endpointSearch = signal('');
  readonly systemSelectOpen = signal(false);
  readonly endpointSelectOpen = signal(false);
  readonly goalSelectOpen = signal(false);
  readonly focusAreasSelectOpen = signal(false);
  readonly aiModelSelectOpen = signal(false);
  readonly reasoningEffortSelectOpen = signal(false);
  readonly branch = signal('');
  readonly selectedSystemId = signal('');
  readonly selectedEndpointId = signal('');
  readonly analysisGoal = signal<FlowExplorerAnalysisGoal>('DEEP_DISCOVERY');
  readonly focusAreas = signal<FlowExplorerFocusArea[]>(['BUSINESS_FLOW_RULES']);
  readonly selectedAiModel = signal('');
  readonly selectedReasoningEffort = signal('');
  readonly userInstructions = signal('');
  readonly job = signal<FlowExplorerJobStateSnapshot | null>(null);
  readonly exportState = signal<FlowExplorerExportState | null>(null);
  readonly jobError = signal('');
  readonly isSubmitting = signal(false);
  readonly chatError = signal('');
  readonly isSendingChat = signal(false);
  readonly isAiModelOptionsLoading = signal(false);
  readonly aiModelOptionsError = signal('');
  readonly aiModelCatalog = signal<AnalysisAiModelOptionsResponse>(EMPTY_AI_MODEL_OPTIONS);
  readonly resultCopied = signal(false);

  readonly filteredSystems = computed(() => {
    const query = normalizeSearch(this.systemSearch());
    const systems = this.systems();
    if (!query) {
      return systems;
    }

    return systems.filter((system) => this.systemSearchText(system).includes(query));
  });
  readonly selectedSystem = computed(
    () => this.systems().find((system) => system.systemId === this.selectedSystemId()) ?? null
  );
  readonly endpoints = computed(() => this.endpointInventory()?.endpoints ?? []);
  readonly filteredEndpoints = computed(() => {
    const query = normalizeSearch(this.endpointSearch());
    const endpoints = this.endpoints();
    if (!query) {
      return endpoints;
    }

    return endpoints.filter((endpoint) => this.endpointSearchText(endpoint).includes(query));
  });
  readonly selectedEndpoint = computed(
    () =>
      this.endpoints().find((endpoint) => endpoint.endpointId === this.selectedEndpointId()) ?? null
  );
  readonly hasActiveChatMessage = computed(() => this.hasActiveChat(this.job()));
  readonly isJobActive = computed(() => {
    const job = this.job();
    return Boolean(job && (!this.isTerminalJobStatus(job.status) || this.hasActiveChat(job)));
  });
  readonly canStartJob = computed(
    () =>
      Boolean(this.selectedSystem() && this.selectedEndpoint()) &&
      !this.isSubmitting() &&
      !this.isJobActive()
  );
  readonly chatMessages = computed(() => this.job()?.chatMessages ?? []);
  readonly isChatAvailable = computed(
    () =>
      Boolean(this.job()?.result && this.job()?.status === 'COMPLETED') &&
      this.exportState()?.origin === 'live'
  );
  readonly chatHint = computed(() => {
    const job = this.job();
    if (!job?.result) {
      return 'Chat bedzie dostepny po zakonczeniu analizy.';
    }
    if (this.exportState()?.origin === 'imported') {
      return 'Importowany zapis jest tylko do odczytu. Chat dziala dla analiz zywych w backendzie.';
    }
    if (job.status !== 'COMPLETED') {
      return 'Chat bedzie dostepny po zakonczeniu analizy.';
    }
    if (this.hasActiveChat(job)) {
      return 'AI przygotowuje odpowiedz na poprzednie pytanie.';
    }
    return 'Pytania korzystaja z wyniku, deterministic contextu i dozwolonych tools dla Flow Explorera.';
  });
  readonly focusAreaLimitReached = computed(() => this.focusAreas().length >= MAX_FOCUS_AREAS);
  readonly systemCountLabel = computed(() => {
    const count = this.systems().length;
    return count === 1 ? '1 application' : `${count} applications`;
  });
  readonly endpointCountLabel = computed(() => {
    const count = this.endpoints().length;
    return count === 1 ? '1 endpoint' : `${count} endpoints`;
  });
  readonly selectedSystemLabel = computed(() => {
    const system = this.selectedSystem();
    if (system) {
      return system.name || system.shortName || system.systemId;
    }
    if (this.catalogState() === 'loading') {
      return 'Loading applications...';
    }
    if (this.catalogState() === 'error') {
      return 'Application catalog unavailable';
    }
    return 'Select application';
  });
  readonly selectedSystemMeta = computed(() => {
    const system = this.selectedSystem();
    if (!system) {
      return this.systemCountLabel();
    }
    return system.summary || system.shortName || 'Application selected';
  });
  readonly selectedEndpointLabel = computed(() => {
    const endpoint = this.selectedEndpoint();
    if (endpoint) {
      return `${this.endpointMethodLabel(endpoint)} ${endpoint.path}`;
    }
    if (!this.selectedSystem()) {
      return 'Select application first';
    }
    switch (this.endpointState()) {
      case 'loading':
        return 'Loading endpoints...';
      case 'error':
        return 'Endpoint inventory unavailable';
      case 'empty':
        return 'No endpoints found';
      case 'ready':
        return 'Select endpoint';
      default:
        return 'Load endpoints for branch/ref';
    }
  });
  readonly selectedEndpointMeta = computed(() => {
    const endpoint = this.selectedEndpoint();
    if (endpoint) {
      return this.endpointDescription(endpoint) || endpoint.operationId || endpoint.handlerMethod || 'Endpoint selected';
    }
    if (this.endpointState() === 'ready') {
      return this.endpointCountLabel();
    }
    return this.selectedSystem() ? this.endpointState() : 'waiting for application';
  });
  readonly selectedGoalLabel = computed(() => this.selectedGoalChoice().label);
  readonly selectedGoalMeta = computed(() => this.selectedGoalChoice().hint);
  readonly focusAreasLabel = computed(() => {
    const selected = this.selectedFocusAreaChoices();
    if (selected.length === 0) {
      return 'Select focus areas';
    }
    if (selected.length === 1) {
      return selected[0].label;
    }
    return `${selected[0].label} + ${selected.length - 1}`;
  });
  readonly focusAreasMeta = computed(() => {
    const selected = this.selectedFocusAreaChoices();
    if (selected.length === 0) {
      return `0 / ${MAX_FOCUS_AREAS} selected`;
    }
    return `${selected.length} / ${MAX_FOCUS_AREAS}: ${selected.map((choice) => choice.label).join(', ')}`;
  });
  readonly aiModelOptions = computed<FlowExplorerChoice<string>[]>(() => {
    if (this.isAiModelOptionsLoading()) {
      return [
        {
          value: this.selectedAiModel(),
          label: 'Loading AI models...',
          hint: 'Fetching model catalog from backend.',
          disabled: true
        }
      ];
    }

    return [
      {
        value: '',
        label: this.defaultModelLabel(),
        hint: 'Use backend default model for this Flow Explorer run.'
      },
      ...this.aiModelCatalog().models.map((model) => ({
        value: model.id,
        label: this.modelLabel(model.id, model.name),
        hint: model.supportsReasoningEffort
          ? `Reasoning efforts: ${model.reasoningEfforts.join(', ') || 'backend default'}`
          : 'This model does not expose reasoning effort choices.'
      }))
    ];
  });
  readonly availableReasoningEfforts = computed(() =>
    this.reasoningEffortsForModel(this.selectedAiModel())
  );
  readonly reasoningEffortOptions = computed<FlowExplorerChoice<string>[]>(() => {
    if (this.isAiModelOptionsLoading()) {
      return [
        {
          value: this.selectedReasoningEffort(),
          label: 'Loading reasoning effort...',
          hint: 'Fetching effort choices from backend.',
          disabled: true
        }
      ];
    }

    return [
      {
        value: '',
        label: this.defaultReasoningEffortLabel(),
        hint: 'Use backend default exploration depth.'
      },
      ...this.availableReasoningEfforts().map((effort) => ({
        value: effort,
        label: this.reasoningEffortLabel(effort),
        hint: this.reasoningEffortHint(effort)
      }))
    ];
  });
  readonly selectedAiModelLabel = computed(() => {
    const selected = this.selectedAiModel();
    if (!selected) {
      return this.defaultModelLabel();
    }
    return this.aiModelOptions().find((option) => option.value === selected)?.label ?? selected;
  });
  readonly selectedAiModelMeta = computed(() => {
    if (this.isAiModelOptionsLoading()) {
      return 'loading catalog';
    }
    if (this.aiModelOptionsError()) {
      return 'catalog unavailable';
    }
    const selected = this.selectedAiModel();
    if (!selected) {
      return 'backend default';
    }
    const model = this.aiModelCatalog().models.find((candidate) => candidate.id === selected);
    if (!model) {
      return 'custom model';
    }
    return model.supportsReasoningEffort ? 'reasoning configurable' : 'fixed reasoning';
  });
  readonly selectedReasoningEffortLabel = computed(() => {
    const selected = this.selectedReasoningEffort();
    if (!selected) {
      return this.defaultReasoningEffortLabel();
    }
    return this.reasoningEffortLabel(selected);
  });
  readonly selectedReasoningEffortMeta = computed(() => {
    const selected = this.selectedReasoningEffort();
    if (this.isAiModelOptionsLoading()) {
      return 'loading effort choices';
    }
    if (!this.availableReasoningEfforts().length) {
      return 'backend default';
    }
    return selected ? this.reasoningEffortHint(selected) : 'backend default exploration depth';
  });
  readonly resultSections = computed(() => {
    return this.job()?.result?.aiResponse?.sections ?? [];
  });
  constructor() {
    this.destroyRef.onDestroy(() => {
      this.stopPolling();
      this.clearResultCopyFeedback();
    });
  }

  ngOnInit(): void {
    this.loadConfig();
    this.loadSystems();
    this.loadAiModelOptions();
  }

  @HostListener('document:click')
  protected closeCustomSelects(): void {
    this.systemSelectOpen.set(false);
    this.endpointSelectOpen.set(false);
    this.goalSelectOpen.set(false);
    this.focusAreasSelectOpen.set(false);
    this.aiModelSelectOpen.set(false);
    this.reasoningEffortSelectOpen.set(false);
  }

  protected loadSystems(): void {
    this.catalogState.set('loading');
    this.catalogError.set('');

    this.flowExplorerApi
      .getSystems()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (systems) => {
          this.systems.set(systems);
          this.catalogState.set(systems.length > 0 ? 'ready' : 'empty');
        },
        error: (error: HttpErrorResponse) => {
          this.systems.set([]);
          this.catalogError.set(this.errorMessage(error));
          this.catalogState.set('error');
        }
      });
  }

  protected selectSystem(system: FlowExplorerSystemOption): void {
    if (this.selectedSystemId() === system.systemId) {
      return;
    }

    this.resetJobState();
    this.selectedSystemId.set(system.systemId);
    this.selectedEndpointId.set('');
    this.endpointSearch.set('');
    this.endpointSelectOpen.set(false);
    this.loadEndpointInventory();
  }

  protected keepCustomSelectOpen(event: Event): void {
    event.stopPropagation();
  }

  protected toggleSystemSelect(event: Event): void {
    event.stopPropagation();
    const willOpen = !this.systemSelectOpen();
    this.systemSelectOpen.set(willOpen);
    this.endpointSelectOpen.set(false);
    this.goalSelectOpen.set(false);
    this.focusAreasSelectOpen.set(false);
    this.aiModelSelectOpen.set(false);
    this.reasoningEffortSelectOpen.set(false);
  }

  protected toggleEndpointSelect(event: Event): void {
    event.stopPropagation();
    if (!this.selectedSystem()) {
      return;
    }
    this.endpointSelectOpen.update((isOpen) => !isOpen);
    this.systemSelectOpen.set(false);
    this.goalSelectOpen.set(false);
    this.focusAreasSelectOpen.set(false);
    this.aiModelSelectOpen.set(false);
    this.reasoningEffortSelectOpen.set(false);
  }

  protected toggleGoalSelect(event: Event): void {
    event.stopPropagation();
    this.goalSelectOpen.update((isOpen) => !isOpen);
    this.systemSelectOpen.set(false);
    this.endpointSelectOpen.set(false);
    this.focusAreasSelectOpen.set(false);
    this.aiModelSelectOpen.set(false);
    this.reasoningEffortSelectOpen.set(false);
  }

  protected toggleFocusAreasSelect(event: Event): void {
    event.stopPropagation();
    this.focusAreasSelectOpen.update((isOpen) => !isOpen);
    this.systemSelectOpen.set(false);
    this.endpointSelectOpen.set(false);
    this.goalSelectOpen.set(false);
    this.aiModelSelectOpen.set(false);
    this.reasoningEffortSelectOpen.set(false);
  }

  protected toggleAiModelSelect(event: Event): void {
    event.stopPropagation();
    this.aiModelSelectOpen.update((isOpen) => !isOpen);
    this.systemSelectOpen.set(false);
    this.endpointSelectOpen.set(false);
    this.goalSelectOpen.set(false);
    this.focusAreasSelectOpen.set(false);
    this.reasoningEffortSelectOpen.set(false);
  }

  protected toggleReasoningEffortSelect(event: Event): void {
    event.stopPropagation();
    this.reasoningEffortSelectOpen.update((isOpen) => !isOpen);
    this.systemSelectOpen.set(false);
    this.endpointSelectOpen.set(false);
    this.goalSelectOpen.set(false);
    this.focusAreasSelectOpen.set(false);
    this.aiModelSelectOpen.set(false);
  }

  protected selectSystemFromDropdown(system: FlowExplorerSystemOption, event: Event): void {
    event.stopPropagation();
    this.systemSelectOpen.set(false);
    this.selectSystem(system);
  }

  protected selectEndpointFromDropdown(endpoint: FlowExplorerEndpointOption, event: Event): void {
    event.stopPropagation();
    this.endpointSelectOpen.set(false);
    this.selectEndpoint(endpoint);
  }

  protected selectGoalFromDropdown(value: FlowExplorerAnalysisGoal, event: Event): void {
    event.stopPropagation();
    this.goalSelectOpen.set(false);
    this.selectGoal(value);
  }

  protected toggleFocusAreaFromDropdown(value: FlowExplorerFocusArea, event: Event): void {
    event.stopPropagation();
    this.toggleFocusArea(value);
  }

  protected selectAiModelFromDropdown(value: string, event: Event): void {
    event.stopPropagation();
    this.aiModelSelectOpen.set(false);
    if (this.selectedAiModel() === value) {
      return;
    }
    this.resetJobState();
    this.selectedAiModel.set(value);
    this.syncReasoningEffortSelection();
  }

  protected selectReasoningEffortFromDropdown(value: string, event: Event): void {
    event.stopPropagation();
    this.reasoningEffortSelectOpen.set(false);
    if (this.selectedReasoningEffort() === value) {
      return;
    }
    this.resetJobState();
    this.selectedReasoningEffort.set(value);
  }

  protected onBranchChanged(value: string): void {
    this.resetJobState();
    this.branch.set(value);
    this.selectedEndpointId.set('');
    this.endpointInventory.set(null);
    this.endpointError.set('');
    this.endpointSearch.set('');
    this.endpointSelectOpen.set(false);
    this.endpointState.set('idle');
  }

  protected loadEndpointInventory(): void {
    const selectedSystem = this.selectedSystem();
    if (!selectedSystem) {
      this.endpointState.set('idle');
      return;
    }

    this.resetJobState();
    this.endpointState.set('loading');
    this.endpointError.set('');
    this.selectedEndpointId.set('');

    this.flowExplorerApi
      .getEndpointInventory(selectedSystem.systemId, { branch: this.branch() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (inventory) => {
          this.endpointInventory.set(inventory);
          this.endpointState.set(inventory.endpoints.length > 0 ? 'ready' : 'empty');
        },
        error: (error: HttpErrorResponse) => {
          this.endpointInventory.set(null);
          this.endpointError.set(this.errorMessage(error, 'Nie udalo sie pobrac endpointow.'));
          this.endpointState.set('error');
        }
      });
  }

  protected selectEndpoint(endpoint: FlowExplorerEndpointOption): void {
    if (this.selectedEndpointId() !== endpoint.endpointId) {
      this.resetJobState();
    }
    this.selectedEndpointId.set(endpoint.endpointId);
  }

  protected selectGoal(value: FlowExplorerAnalysisGoal): void {
    const goal = ANALYSIS_GOALS.find((candidate) => candidate.value === value);
    if (!goal || goal.disabled || this.analysisGoal() === value) {
      return;
    }
    this.resetJobState();
    this.analysisGoal.set(value);
  }

  protected toggleFocusArea(value: FlowExplorerFocusArea): void {
    const selected = this.focusAreas();
    if (selected.includes(value)) {
      this.resetJobState();
      this.focusAreas.set(selected.filter((candidate) => candidate !== value));
      return;
    }
    if (selected.length >= MAX_FOCUS_AREAS) {
      return;
    }
    this.resetJobState();
    this.focusAreas.set([...selected, value]);
  }

  protected onUserInstructionsChanged(value: string): void {
    if (this.userInstructions() === value) {
      return;
    }
    this.resetJobState();
    this.userInstructions.set(value);
  }

  protected clearChatError(): void {
    this.chatError.set('');
  }

  protected sendChatMessage(message: string): void {
    const job = this.job();
    const trimmedMessage = message.trim();
    if (!job || !trimmedMessage || !this.isChatAvailable() || this.isSendingChat() || this.hasActiveChatMessage()) {
      return;
    }

    this.isSendingChat.set(true);
    this.chatError.set('');

    this.flowExplorerApi
      .sendChatMessage(job.jobId, { message: trimmedMessage })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.isSendingChat.set(false);
          this.applyJobSnapshot(snapshot, {
            origin: 'live',
            exportedAt: '',
            fileName: ''
          });
          if (this.hasActiveChat(snapshot)) {
            this.startPolling(snapshot.jobId);
          }
        },
        error: (error: HttpErrorResponse) => {
          this.isSendingChat.set(false);
          this.chatError.set(this.errorMessage(error, 'Nie udalo sie wyslac pytania follow-up.'));
        }
      });
  }

  protected startJob(): void {
    const selectedSystem = this.selectedSystem();
    const selectedEndpoint = this.selectedEndpoint();
    if (!selectedSystem || !selectedEndpoint) {
      this.jobError.set('Wybierz system i endpoint przed uruchomieniem.');
      return;
    }

    this.stopPolling();
    this.isSubmitting.set(true);
    this.jobError.set('');
    this.job.set(null);
    this.exportState.set(null);

    this.flowExplorerApi
      .startJob(this.jobStartRequest(selectedSystem, selectedEndpoint))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.isSubmitting.set(false);
          this.applyJobSnapshot(snapshot, {
            origin: 'live',
            exportedAt: '',
            fileName: ''
          });
          if (!this.isTerminalJobStatus(snapshot.status) || this.hasActiveChat(snapshot)) {
            this.startPolling(snapshot.jobId);
          }
        },
        error: (error: HttpErrorResponse) => {
          this.isSubmitting.set(false);
          this.jobError.set(this.errorMessage(error, 'Nie udalo sie uruchomic Flow Explorer job.'));
        }
      });
  }

  triggerImport(fileInput: HTMLInputElement): void {
    this.jobError.set('');
    fileInput.value = '';
    fileInput.click();
  }

  async importFlowExplorerAnalysis(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file) {
      return;
    }

    try {
      const parsedContent = await readJsonFile(file, 'Wybrany plik nie zawiera poprawnego JSON-a.');
      const imported = parseImportedFlowExplorerAnalysis(parsedContent);

      this.stopPolling();
      this.isSubmitting.set(false);
      this.isSendingChat.set(false);
      this.chatError.set('');
      this.jobError.set('');
      this.applyJobSnapshot(imported.job, {
        origin: 'imported',
        exportedAt: imported.exportedAt,
        fileName: file.name
      });
      this.syncImportedControls(imported.job);
    } catch (error) {
      this.jobError.set(
        error instanceof Error
          ? error.message
          : 'Nie udalo sie wczytac pliku z wynikiem Flow Explorera.'
      );
    } finally {
      if (input) {
        input.value = '';
      }
    }
  }

  exportFlowExplorerAnalysis(): void {
    const exportState = this.exportState();
    if (!exportState) {
      return;
    }

    const exportedAt = new Date().toISOString();
    const payload = buildFlowExplorerExportEnvelope(exportState.job, exportedAt);
    downloadJsonFile(buildFlowExplorerExportFileName(exportState.job, exportedAt), payload);
  }

  protected async copyFlowExplorerResult(resultElement: HTMLElement): Promise<void> {
    const copied = await copyElementToClipboard(resultElement);
    if (!copied) {
      this.jobError.set('Nie udalo sie skopiowac wyniku Flow Explorera do schowka.');
      return;
    }

    this.jobError.set('');
    this.resultCopied.set(true);
    this.clearResultCopyFeedback();
    this.resultCopyFeedbackHandle = window.setTimeout(() => {
      this.resultCopied.set(false);
      this.resultCopyFeedbackHandle = null;
    }, 1600);
  }

  protected isFocusAreaSelected(value: FlowExplorerFocusArea): boolean {
    return this.focusAreas().includes(value);
  }

  protected statusPillClass(): string {
    switch (this.catalogState()) {
      case 'loading':
        return 'status-pill status-pill--running';
      case 'ready':
        return 'status-pill status-pill--done';
      case 'error':
        return 'status-pill status-pill--error';
      default:
        return 'status-pill status-pill--queued';
    }
  }

  protected endpointStatusPillClass(): string {
    switch (this.endpointState()) {
      case 'loading':
        return 'status-pill status-pill--running';
      case 'ready':
        return 'status-pill status-pill--done';
      case 'error':
        return 'status-pill status-pill--error';
      default:
        return 'status-pill status-pill--queued';
    }
  }

  protected jobStatusPillClass(status: string): string {
    switch (status) {
      case 'COMPLETED':
        return 'status-pill status-pill--done';
      case 'FAILED':
        return 'status-pill status-pill--error';
      case 'QUEUED':
        return 'status-pill status-pill--queued';
      default:
        return 'status-pill status-pill--running';
    }
  }

  protected systemTooltip(system: FlowExplorerSystemOption): string {
    const summary = system.summary?.trim() || 'Application description is not available.';
    const catalogMeta = [
      system.kind || 'application',
      system.lifecycleStatus || 'status n/a',
      system.ownerTeamIds.join(', ') || 'owner n/a'
    ].join(' · ');
    const repositoryCount =
      system.repositoryCount === 1
        ? '1 source repository'
        : `${system.repositoryCount} source repositories`;

    return `${summary}\n${catalogMeta}\n${repositoryCount}`;
  }

  protected systemOptionClass(system: FlowExplorerSystemOption): string {
    return [
      'flow-explorer-select-option',
      system.systemId === this.selectedSystemId() ? 'flow-explorer-select-option--selected' : ''
    ]
      .filter(Boolean)
      .join(' ');
  }

  protected endpointOptionClass(endpoint: FlowExplorerEndpointOption): string {
    return endpoint.endpointId === this.selectedEndpointId()
      ? 'flow-explorer-select-option flow-explorer-select-option--selected'
      : 'flow-explorer-select-option';
  }

  protected goalOptionClass(value: FlowExplorerAnalysisGoal): string {
    return this.analysisGoal() === value
      ? 'flow-explorer-select-option flow-explorer-select-option--selected'
      : 'flow-explorer-select-option';
  }

  protected focusAreaSelectOptionClass(value: FlowExplorerFocusArea): string {
    return this.isFocusAreaSelected(value)
      ? 'flow-explorer-select-option flow-explorer-select-option--selected'
      : 'flow-explorer-select-option';
  }

  protected aiModelOptionClass(value: string): string {
    return this.selectedAiModel() === value
      ? 'flow-explorer-select-option flow-explorer-select-option--selected'
      : 'flow-explorer-select-option';
  }

  protected reasoningEffortOptionClass(value: string): string {
    return this.selectedReasoningEffort() === value
      ? 'flow-explorer-select-option flow-explorer-select-option--selected'
      : 'flow-explorer-select-option';
  }

  protected endpointMethodLabel(endpoint: FlowExplorerEndpointOption): string {
    return endpoint.methods.length > 0 ? endpoint.methods.join(', ') : endpoint.method || 'HTTP';
  }

  protected endpointDescription(endpoint: FlowExplorerEndpointOption): string {
    return endpoint.summary || endpoint.description || endpoint.operationId || endpoint.controllerClass || '';
  }

  protected endpointSourceLabel(source: FlowExplorerEndpointSource | null): string {
    if (!source) {
      return '';
    }
    return `${source.projectName || source.repositoryId} · ${source.filePath}`;
  }

  protected endpointLineLabel(source: FlowExplorerEndpointSource | null): string {
    if (!source || source.lineStart <= 0 || source.lineEnd <= 0) {
      return '';
    }
    return `L${source.lineStart}-L${source.lineEnd}`;
  }

  protected parameterLabel(parameter: FlowExplorerEndpointParameter): string {
    const required = parameter.required ? 'required' : 'optional';
    return [parameter.name, parameter.in, parameter.type, required].filter(Boolean).join(' · ');
  }

  protected hasItems(items: unknown[] | null | undefined): boolean {
    return Boolean(items?.length);
  }

  protected confidencePillClass(confidence: string): string {
    switch (normalizeSearch(confidence)) {
      case 'high':
        return 'status-pill status-pill--done';
      case 'low':
        return 'status-pill status-pill--error';
      default:
        return 'status-pill status-pill--queued';
    }
  }

  protected sectionModePillClass(mode: string): string {
    return normalizeSearch(mode) === 'deep'
      ? 'status-pill status-pill--done'
      : 'status-pill status-pill--queued';
  }

  protected sectionModeLabel(mode: string): string {
    return normalizeSearch(mode) === 'deep' ? 'deep' : 'compact';
  }

  protected sectionIcon(sectionId: string): string {
    switch (sectionId) {
      case 'BUSINESS_FLOW_RULES':
        return 'account_tree';
      case 'VALIDATIONS':
        return 'rule';
      case 'PERSISTENCE':
        return 'database';
      case 'INTEGRATIONS':
        return 'hub';
      default:
        return 'article';
    }
  }

  protected usageStats(usage: AnalysisAiUsage | null | undefined): FlowExplorerUsageStat[] {
    return buildFlowExplorerUsageStats(usage ?? null);
  }

  protected usageTooltip(usage: AnalysisAiUsage | null | undefined): string {
    return buildFlowExplorerUsageTooltip(usage ?? null);
  }

  private loadConfig(): void {
    this.flowExplorerApi
      .getConfig()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (config) => {
          const defaultBranch = config.defaultBranch?.trim();
          if (defaultBranch && !this.branch()) {
            this.branch.set(defaultBranch);
          }
        },
        error: (error: HttpErrorResponse) => {
          this.configError.set(this.errorMessage(error, 'Nie udalo sie pobrac default branch.'));
        }
      });
  }

  private loadAiModelOptions(): void {
    this.isAiModelOptionsLoading.set(true);
    this.aiModelOptionsError.set('');

    this.analysisApi
      .getAiModelOptions()
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isAiModelOptionsLoading.set(false))
      )
      .subscribe({
        next: (options) => {
          this.aiModelCatalog.set(this.normalizeAiModelOptions(options));
          this.syncReasoningEffortSelection();
        },
        error: (error: HttpErrorResponse) => {
          this.aiModelCatalog.set(EMPTY_AI_MODEL_OPTIONS);
          this.aiModelOptionsError.set(this.errorMessage(error, 'Nie udalo sie pobrac modeli AI.'));
          this.syncReasoningEffortSelection();
        }
      });
  }

  private startPolling(jobId: string): void {
    this.stopPolling();
    this.refreshJob(jobId);
    this.pollingTimer = setInterval(() => this.refreshJob(jobId), POLL_INTERVAL_MS);
  }

  private refreshJob(jobId: string): void {
    this.flowExplorerApi
      .getJob(jobId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.applyJobSnapshot(snapshot, {
            origin: 'live',
            exportedAt: '',
            fileName: ''
          });
          if (this.isTerminalJobStatus(snapshot.status) && !this.hasActiveChat(snapshot)) {
            this.stopPolling();
          }
        },
        error: (error: HttpErrorResponse) => {
          this.stopPolling();
          this.jobError.set(this.errorMessage(error, 'Nie udalo sie odswiezyc Flow Explorer job.'));
        }
      });
  }

  private stopPolling(): void {
    if (this.pollingTimer === null) {
      return;
    }
    clearInterval(this.pollingTimer);
    this.pollingTimer = null;
  }

  private clearResultCopyFeedback(): void {
    if (this.resultCopyFeedbackHandle === null) {
      return;
    }
    window.clearTimeout(this.resultCopyFeedbackHandle);
    this.resultCopyFeedbackHandle = null;
  }

  private resetJobState(): void {
    this.stopPolling();
    this.clearResultCopyFeedback();
    this.job.set(null);
    this.exportState.set(null);
    this.jobError.set('');
    this.resultCopied.set(false);
    this.isSubmitting.set(false);
    this.chatError.set('');
    this.isSendingChat.set(false);
  }

  private isTerminalJobStatus(status: string): boolean {
    return status === 'COMPLETED' || status === 'FAILED';
  }

  private hasActiveChat(snapshot: FlowExplorerJobStateSnapshot | null | undefined): boolean {
    return Boolean(snapshot?.chatMessages?.some((message) => message.status === 'IN_PROGRESS'));
  }

  private applyJobSnapshot(
    job: FlowExplorerJobStateSnapshot,
    metadata: Pick<FlowExplorerExportState, 'origin' | 'exportedAt' | 'fileName'>
  ): void {
    const normalizedJob = normalizeFlowExplorerJob(job);
    this.job.set(normalizedJob);
    this.syncExportableState(normalizedJob, metadata);
  }

  private syncExportableState(
    job: FlowExplorerJobStateSnapshot,
    metadata: Pick<FlowExplorerExportState, 'origin' | 'exportedAt' | 'fileName'>
  ): void {
    if (job.status !== 'COMPLETED' || !job.result?.aiResponse || this.hasActiveChat(job)) {
      this.exportState.set(null);
      return;
    }

    this.exportState.set({
      origin: metadata.origin,
      exportedAt: metadata.exportedAt,
      fileName: metadata.fileName,
      job: normalizeFlowExplorerJob(job)
    });
  }

  private syncImportedControls(job: FlowExplorerJobStateSnapshot): void {
    this.selectedSystemId.set(job.systemId);
    this.selectedEndpointId.set(job.endpointId);
    this.branch.set(job.branch || this.branch());
    this.analysisGoal.set(job.goal || 'DEEP_DISCOVERY');
    this.focusAreas.set(job.focusAreas.length ? job.focusAreas.slice(0, MAX_FOCUS_AREAS) : ['BUSINESS_FLOW_RULES']);
    this.selectedAiModel.set(job.aiModel || '');
    this.selectedReasoningEffort.set(job.reasoningEffort || '');
    this.syncReasoningEffortSelection();
    this.userInstructions.set('');
  }

  private jobStartRequest(
    selectedSystem: FlowExplorerSystemOption,
    selectedEndpoint: FlowExplorerEndpointOption
  ): FlowExplorerJobStartRequest {
    const userInstructions = this.userInstructions().trim();
    const selectedAiModel = this.selectedAiModel().trim();
    const selectedReasoningEffort = this.selectedReasoningEffort().trim();
    return {
      systemId: selectedSystem.systemId,
      endpointId: selectedEndpoint.endpointId,
      httpMethod: selectedEndpoint.method || selectedEndpoint.methods[0],
      endpointPath: selectedEndpoint.path,
      branch: this.branch().trim() || undefined,
      goal: this.analysisGoal(),
      focusAreas: this.focusAreas(),
      userInstructions: userInstructions || undefined,
      model: selectedAiModel || undefined,
      reasoningEffort: selectedReasoningEffort || undefined
    };
  }

  private errorMessage(error: HttpErrorResponse, fallback = 'Nie udalo sie pobrac katalogu systemow.'): string {
    const body = error.error as Partial<ApiErrorResponse> | null;
    if (body?.message) {
      return body.message;
    }
    if (error.message) {
      return error.message;
    }
    return fallback;
  }

  private systemSearchText(system: FlowExplorerSystemOption): string {
    return normalizeSearch(
      [
        system.systemId,
        system.name,
        system.shortName,
        system.kind,
        system.lifecycleStatus,
        system.operationalStatus,
        system.criticality,
        system.summary,
        ...system.aliases,
        ...system.ownerTeamIds
      ].join(' ')
    );
  }

  private endpointSearchText(endpoint: FlowExplorerEndpointOption): string {
    return normalizeSearch(
      [
        endpoint.endpointId,
        endpoint.method,
        ...endpoint.methods,
        endpoint.path,
        endpoint.pathExpression,
        endpoint.summary,
        endpoint.description,
        endpoint.operationId,
        ...endpoint.tags,
        endpoint.controllerClass,
        endpoint.handlerMethod
      ].join(' ')
    );
  }

  private selectedGoalChoice(): FlowExplorerChoice<FlowExplorerAnalysisGoal> {
    return (
      ANALYSIS_GOALS.find((goal) => goal.value === this.analysisGoal()) ??
      ANALYSIS_GOALS[0]
    );
  }

  private selectedFocusAreaChoices(): FlowExplorerChoice<FlowExplorerFocusArea>[] {
    const selected = this.focusAreas();
    return FOCUS_AREA_OPTIONS.filter((focusArea) => selected.includes(focusArea.value));
  }

  private normalizeAiModelOptions(
    options: AnalysisAiModelOptionsResponse | null
  ): AnalysisAiModelOptionsResponse {
    if (!options) {
      return EMPTY_AI_MODEL_OPTIONS;
    }

    return {
      defaultModel: typeof options.defaultModel === 'string' ? options.defaultModel : '',
      defaultReasoningEffort:
        typeof options.defaultReasoningEffort === 'string' ? options.defaultReasoningEffort : '',
      defaultReasoningEfforts: Array.isArray(options.defaultReasoningEfforts)
        ? options.defaultReasoningEfforts.filter((effort) => typeof effort === 'string')
        : [],
      models: Array.isArray(options.models)
        ? options.models
            .filter((model) => model && typeof model.id === 'string')
            .map((model) => ({
              id: model.id,
              name: typeof model.name === 'string' ? model.name : model.id,
              supportsReasoningEffort: Boolean(model.supportsReasoningEffort),
              reasoningEfforts: Array.isArray(model.reasoningEfforts)
                ? model.reasoningEfforts.filter((effort) => typeof effort === 'string')
                : [],
              defaultReasoningEffort:
                typeof model.defaultReasoningEffort === 'string'
                  ? model.defaultReasoningEffort
                  : ''
            }))
        : []
    };
  }

  private syncReasoningEffortSelection(): void {
    const availableEfforts = this.reasoningEffortsForModel(this.selectedAiModel());
    const selectedReasoningEffort = this.selectedReasoningEffort().trim();
    if (!availableEfforts.length) {
      this.selectedReasoningEffort.set('');
      return;
    }
    if (selectedReasoningEffort && !availableEfforts.includes(selectedReasoningEffort)) {
      this.selectedReasoningEffort.set('');
    }
  }

  private reasoningEffortsForModel(modelId: string): string[] {
    const catalog = this.aiModelCatalog();
    if (!modelId) {
      return catalog.defaultReasoningEfforts;
    }

    const model = catalog.models.find((candidate) => candidate.id === modelId);
    return model?.supportsReasoningEffort ? model.reasoningEfforts : [];
  }

  private defaultModelLabel(): string {
    const defaultModel = this.aiModelCatalog().defaultModel;
    return defaultModel ? `Default backend (${defaultModel})` : 'Default backend';
  }

  private defaultReasoningEffortLabel(): string {
    const defaultEffort = this.aiModelCatalog().defaultReasoningEffort;
    return defaultEffort ? `Default backend (${defaultEffort})` : 'Default backend';
  }

  private modelLabel(id: string, name: string): string {
    if (!name || name === id) {
      return id;
    }

    return `${name} (${id})`;
  }

  private reasoningEffortLabel(effort: string): string {
    return effort ? effort.charAt(0).toUpperCase() + effort.slice(1) : effort;
  }

  private reasoningEffortHint(effort: string): string {
    switch (normalizeSearch(effort)) {
      case 'low':
        return 'Artifact-first, minimal additional reads.';
      case 'high':
        return 'Deep exploration of edge cases and dependencies.';
      default:
        return 'Focused reads for missing primary flow details.';
    }
  }

}

function buildFlowExplorerUsageStats(usage: AnalysisAiUsage | null): FlowExplorerUsageStat[] {
  const estimate = estimateAnalysisAiCost(usage);
  if (!usage || usage.totalTokens <= 0 || !estimate) {
    return [];
  }

  return [
    { label: 'Tokens', value: formatUsageTokenCount(usage.totalTokens) },
    { label: 'Credits', value: formatCredits(estimate.credits) },
    { label: 'Dollars', value: formatDollars(estimate.dollars) }
  ];
}

function buildFlowExplorerUsageTooltip(usage: AnalysisAiUsage | null): string {
  const estimate = estimateAnalysisAiCost(usage);
  if (!usage || !estimate) {
    return '';
  }

  const lines = [
    'Szacowany koszt analizy AI',
    '',
    `Tokens: ${formatUsageTokenCount(usage.totalTokens)} - laczna ilosc tekstu odczytanego przez model i wygenerowanej odpowiedzi.`,
    `Credits: ${formatCredits(estimate.credits)} - przeliczenie tokenow na GitHub AI Credits.`,
    `Dollars: ${formatDollars(estimate.dollars)} - orientacyjny koszt dodatkowego uzycia po wykorzystaniu pakietu.`,
    '',
    'Jak to liczymy:',
    `Nowy kontekst wyslany do AI: ${formatUsageTokenCount(
      estimate.newInputTokens
    )} tokenow x ${formatUsdRate(estimate.inputUsdPerMillion)} / 1M.`,
    `Kontekst odczytany z cache: ${formatUsageTokenCount(
      estimate.cachedInputTokens
    )} tokenow x ${formatUsdRate(
      estimate.cachedInputUsdPerMillion
    )} / 1M. To ponownie uzyty kontekst rozmowy/evidence, zwykle duzo tanszy niz nowy input.`,
    `Odpowiedz AI: ${formatUsageTokenCount(estimate.outputTokens)} tokenow x ${formatUsdRate(
      estimate.outputUsdPerMillion
    )} / 1M.`
  ];

  if (estimate.cacheWriteTokens > 0) {
    if (estimate.cacheWriteUsdPerMillion !== null) {
      lines.push(
        `Zapis do cache: ${formatUsageTokenCount(estimate.cacheWriteTokens)} tokenow x ${formatUsdRate(
          estimate.cacheWriteUsdPerMillion
        )} / 1M.`
      );
    } else {
      lines.push(
        `Zapis do cache: ${formatUsageTokenCount(
          estimate.cacheWriteTokens
        )} tokenow. Ten model nie ma osobnej stawki cache-write w tabeli, wiec pokazujemy to informacyjnie.`
      );
    }
  }

  lines.push('');
  lines.push(
    `Stawki: ${estimate.pricingModel}${
      estimate.usedFallbackPricing ? ' (model nierozpoznany, uzyty domyslny przelicznik)' : ''
    }, 1 credit = ${formatDollars(GITHUB_AI_CREDIT_USD)}.`
  );

  if (usage.apiCallCount > 0) {
    lines.push(
      `Wywolania modelu: ${formatUsageTokenCount(
        usage.apiCallCount
      )}. Jedna analiza moze miec kilka rund, zwlaszcza gdy AI pobiera dodatkowe dane przez tools.`
    );
  }

  if (usage.apiDurationMs > 0) {
    lines.push(`Czas po stronie API: ${formatDurationMs(usage.apiDurationMs)}.`);
  }

  if (usage.model) {
    lines.push(`Model zgloszony przez SDK: ${usage.model}.`);
  }

  if (usage.contextCurrentTokens !== null && usage.contextTokenLimit !== null) {
    lines.push(
      `Aktualny rozmiar kontekstu sesji: ${formatUsageTokenCount(
        usage.contextCurrentTokens
      )} / ${formatUsageTokenCount(
        usage.contextTokenLimit
      )} tokenow. To snapshot pamieci rozmowy, a nie osobna pozycja do doliczenia.`
    );
  }

  if (usage.contextMessages !== null) {
    lines.push(
      `Wiadomosci w kontekscie sesji: ${formatUsageTokenCount(
        usage.contextMessages
      )}. To pomaga ocenic, jak dluga byla sesja AI.`
    );
  }

  return lines.join('\n');
}

function formatUsageTokenCount(value: number | null | undefined): string {
  return new Intl.NumberFormat('en-US').format(Math.max(0, Math.round(Number(value ?? 0))));
}

function formatCredits(value: number): string {
  return new Intl.NumberFormat('pl-PL', {
    minimumFractionDigits: value < 10 ? 2 : 1,
    maximumFractionDigits: value < 10 ? 2 : 1
  }).format(value);
}

function formatDollars(value: number): string {
  return `$${new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  }).format(value)}`;
}

function formatUsdRate(value: number): string {
  return `$${new Intl.NumberFormat('en-US', {
    minimumFractionDigits: value < 1 ? 3 : 2,
    maximumFractionDigits: 3
  }).format(value)}`;
}

function formatDurationMs(value: number): string {
  if (value >= 1000) {
    return `${new Intl.NumberFormat('pl-PL', { maximumFractionDigits: 2 }).format(value / 1000)} s`;
  }

  return `${formatUsageTokenCount(value)} ms`;
}

function normalizeSearch(value: string): string {
  return value.trim().toLowerCase();
}
