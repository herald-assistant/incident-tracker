import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, HostListener, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute } from '@angular/router';
import { finalize } from 'rxjs';

import {
  AnalysisAiUsage,
  AnalysisAiModelOptionsResponse,
  AnalysisReport,
  AnalysisReportMeta,
  AnalysisReportReference,
  AnalysisReportSection,
  ApiErrorResponse,
  LocalAnalysisRunDetailResponse
} from '../../../../core/models/analysis.models';
import { AnalysisApiService } from '../../../../core/services/analysis-api.service';
import { AnalysisRunHistoryApiService } from '../../../../core/services/analysis-run-history-api.service';
import {
  FlowExplorerAnalysisGoal,
  FlowExplorerEndpointInventoryResponse,
  FlowExplorerEndpointOption,
  FlowExplorerEndpointParameter,
  FlowExplorerEndpointSource,
  FlowExplorerFocusArea,
  FlowExplorerJobStartRequest,
  FlowExplorerJobStateSnapshot,
  FlowExplorerResultSectionId,
  FlowExplorerSectionMode,
  FlowExplorerSectionModeRequest,
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
import { buildFlowExplorerReportMarkdown } from '../../utils/flow-explorer-result-markdown.utils';
import { AnalysisFeatureAsideComponent } from '../../../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisFollowUpChatComponent } from '../../../../components/analysis-follow-up-chat/analysis-follow-up-chat';
import { AnalysisReportMetaComponent } from '../../../../components/analysis-report-meta/analysis-report-meta';
import { AnalysisReportSectionContentComponent } from '../../../../components/analysis-report-section-content/analysis-report-section-content';
import { AnalysisStepsPanelComponent } from '../../../../components/analysis-steps-panel/analysis-steps-panel';
import { copyTextToClipboard } from '../../../../core/utils/clipboard.utils';
import {
  AnalysisAiCostEstimate,
  estimateAnalysisAiCost,
  GITHUB_AI_CREDIT_USD
} from '../../../../core/utils/analysis-ai-usage-cost.utils';
import {
  defaultReasoningEffortForAiModel,
  EMPTY_ANALYSIS_AI_MODEL_OPTIONS,
  listedDefaultAiModel,
  normalizeAnalysisAiModelOptions,
  reasoningEffortsForAiModel
} from '../../../../core/utils/analysis-ai-model-options.utils';
import { appendOptimisticChatTurn } from '../../../../core/utils/analysis-chat-optimistic.utils';

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

interface FlowExplorerDisplayMeta {
  references: AnalysisReportReference[];
  visibilityLimits: string[];
  openQuestions: string[];
  gaps: string[];
  warnings: string[];
  confidence: string;
}

interface FlowExplorerDisplaySection {
  id: string;
  title: string;
  mode: string;
  markdown: string;
  meta: FlowExplorerDisplayMeta;
}

interface FlowExplorerDisplayResult {
  title: string;
  subTitle: string;
  status: string;
  goal: FlowExplorerAnalysisGoal;
  confidence: string;
  overview: FlowExplorerDisplaySection | null;
  sections: FlowExplorerDisplaySection[];
  appendix: FlowExplorerDisplayMeta;
  followUpPrompts: string[];
  usage: AnalysisAiUsage | null;
  emptyMessage: string;
}

type FlowExplorerExportMetadata = Pick<
  FlowExplorerExportState,
  | 'origin'
  | 'exportedAt'
  | 'fileName'
  | 'localRunId'
  | 'localRunName'
  | 'continuationEnabled'
>;

const POLL_INTERVAL_MS = 1500;

const ANALYSIS_GOALS: FlowExplorerChoice<FlowExplorerAnalysisGoal>[] = [
  {
    value: 'DEEP_DISCOVERY',
    label: 'Deep Discovery',
    hint: 'Complex endpoint understanding across functional flow, decisions, data and integrations.'
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
    value: 'FUNCTIONAL_FLOW',
    label: 'Functional flow',
    hint: 'Deepen request path, domain decisions and code-visible functional conditions.'
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

const SECTION_MODE_OPTIONS: FlowExplorerChoice<FlowExplorerSectionMode>[] = [
  {
    value: 'OFF',
    label: 'Off',
    hint: 'Do not ask AI to return this section.'
  },
  {
    value: 'COMPACT',
    label: 'Compact',
    hint: 'Return the section in a concise form.'
  },
  {
    value: 'DEEP',
    label: 'Deep',
    hint: 'Prioritize this section and return more detail.'
  }
];

const DEFAULT_SECTION_MODES: FlowExplorerSectionModeRequest[] = [
  { id: 'FUNCTIONAL_FLOW', mode: 'DEEP' },
  { id: 'VALIDATIONS', mode: 'COMPACT' },
  { id: 'PERSISTENCE', mode: 'COMPACT' },
  { id: 'INTEGRATIONS', mode: 'COMPACT' }
];

@Component({
  selector: 'app-flow-explorer-page',
  imports: [
    MatTooltipModule,
    AnalysisFeatureAsideComponent,
    AnalysisFollowUpChatComponent,
    AnalysisReportMetaComponent,
    AnalysisReportSectionContentComponent,
    AnalysisStepsPanelComponent,
  ],
  templateUrl: './flow-explorer-page.html',
  styleUrl: './flow-explorer-page.scss'
})
export class FlowExplorerPageComponent implements OnInit {
  private readonly flowExplorerApi = inject(FlowExplorerApiService);
  private readonly analysisApi = inject(AnalysisApiService);
  private readonly historyApi = inject(AnalysisRunHistoryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private pollingTimer: ReturnType<typeof setInterval> | null = null;
  private resultCopyFeedbackHandle: number | null = null;
  private followUpPromptCopyFeedbackHandle: number | null = null;

  readonly analysisGoals = ANALYSIS_GOALS;
  readonly focusAreaOptions = FOCUS_AREA_OPTIONS;
  readonly sectionModeOptions = SECTION_MODE_OPTIONS;

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
  readonly sectionModes = signal<FlowExplorerSectionModeRequest[]>(copySectionModes(DEFAULT_SECTION_MODES));
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
  readonly aiModelCatalog = signal<AnalysisAiModelOptionsResponse>(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
  readonly resultCopied = signal(false);
  readonly copiedFollowUpPromptIndex = signal<number | null>(null);

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
  readonly isImportedResult = computed(() => this.exportState()?.origin === 'imported');
  readonly isHistoryResult = computed(() => this.exportState()?.origin === 'local');
  readonly canStartJob = computed(
    () =>
      Boolean(this.selectedSystem() && this.selectedEndpoint()) &&
      !this.isSubmitting() &&
      !this.isJobActive()
  );
  readonly chatMessages = computed(() => this.job()?.chatMessages ?? []);
  readonly workflowIsRunning = computed(() => {
    const job = this.job();
    return Boolean(job && !this.isTerminalJobStatus(job.status));
  });
  readonly aiWorkflowIsRunning = computed(() => {
    const job = this.job();
    return Boolean(job?.steps.some((step) => step.code === 'AI_ANALYSIS' && step.status === 'IN_PROGRESS'));
  });
  readonly chatIsWaiting = computed(() => this.isSendingChat() || this.hasActiveChat(this.job()));
  readonly chatMessageCount = computed(() => this.chatMessages().length);
  readonly aiWorkflowItemCount = computed(() => {
    const job = this.job();
    if (!job) {
      return 0;
    }

    return (
      job.aiActivityEvents.length +
      job.toolEvidenceSections.reduce((count, section) => count + section.items.length, 0)
    );
  });
  readonly toolFeedbackCount = computed(() => this.job()?.toolFeedback.length ?? 0);
  readonly isChatAvailable = computed(() => {
    const exportState = this.exportState();
    return (
      Boolean(this.job()?.result && this.job()?.status === 'COMPLETED') &&
      (exportState?.origin === 'live' ||
        (exportState?.origin === 'local' && Boolean(exportState.continuationEnabled)))
    );
  });
  readonly focusAreas = computed<FlowExplorerFocusArea[]>(() =>
    this.sectionModes()
      .filter((sectionMode) => sectionMode.mode === 'DEEP')
      .map((sectionMode) => sectionMode.id)
  );
  readonly systemCountLabel = computed(() => {
    const count = this.systems().length;
    return count === 1 ? '1 application' : `${count} applications`;
  });
  readonly endpointCountLabel = computed(() => {
    const count = this.endpoints().length;
    return count === 1 ? '1 endpoint' : `${count} endpoints`;
  });
  readonly endpointInventoryDateLabel = computed(() => {
    const dataCollectedAt = this.endpointInventory()?.dataCollectedAt;
    const formatted = formatEndpointInventoryDate(dataCollectedAt);
    return formatted ? `Data: ${formatted}` : '';
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
    const counts = this.sectionModeCounts();
    if (counts.off === this.sectionModes().length) {
      return 'Overview only';
    }
    return `${counts.deep} deep / ${counts.compact} compact / ${counts.off} off`;
  });
  readonly focusAreasMeta = computed(() => {
    return this.sectionModes()
      .map((sectionMode) => `${sectionTitle(sectionMode.id)} ${this.sectionModeLabel(sectionMode.mode)}`)
      .join(' · ');
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

    return this.aiModelCatalog().models.map((model) => ({
      value: model.id,
      label: this.modelLabel(model.id, model.name),
      hint: model.supportsReasoningEffort
        ? `Reasoning efforts: ${model.reasoningEfforts.join(', ') || 'backend default'}`
        : 'This model does not expose reasoning effort choices.'
    }));
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

    return this.availableReasoningEfforts().map((effort) => ({
      value: effort,
      label: this.reasoningEffortLabel(effort),
      hint: this.reasoningEffortHint(effort)
    }));
  });
  readonly selectedAiModelLabel = computed(() => {
    const selected = this.selectedAiModel();
    if (!selected) {
      return 'No model selected';
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
      return this.aiModelCatalog().defaultModel ? 'backend default not listed' : 'no model selected';
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
      return 'No effort selected';
    }
    return this.reasoningEffortLabel(selected);
  });
  readonly selectedReasoningEffortMeta = computed(() => {
    const selected = this.selectedReasoningEffort();
    if (this.isAiModelOptionsLoading()) {
      return 'loading effort choices';
    }
    if (!this.availableReasoningEfforts().length) {
      return 'no effort choices';
    }
    return selected ? this.reasoningEffortHint(selected) : 'backend default not listed';
  });
  readonly displayResult = computed(() => {
    const job = this.job();
    return job?.report ? displayResultFromReport(job, job.report) : null;
  });
  readonly resultSections = computed(() => this.displayResult()?.sections ?? []);
  constructor() {
    this.route.queryParamMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const localRunId = params.get('localRunId')?.trim() ?? '';
        if (localRunId) {
          this.loadLocalFlowExplorerRun(localRunId);
        }
      });
    this.destroyRef.onDestroy(() => {
      this.stopPolling();
      this.clearResultCopyFeedback();
      this.clearFollowUpPromptCopyFeedback();
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

  protected selectSectionModeFromDropdown(
    sectionId: FlowExplorerResultSectionId,
    mode: FlowExplorerSectionMode,
    event: Event
  ): void {
    event.stopPropagation();
    this.selectSectionMode(sectionId, mode);
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

  protected loadEndpointInventory(refreshCache = false): void {
    const selectedSystem = this.selectedSystem();
    if (!selectedSystem) {
      this.endpointState.set('idle');
      return;
    }

    this.resetJobState();
    this.endpointState.set('loading');
    this.endpointError.set('');
    this.selectedEndpointId.set('');
    this.endpointInventory.set(null);

    const query = refreshCache
      ? { branch: this.branch(), refresh: true }
      : { branch: this.branch() };

    this.flowExplorerApi
      .getEndpointInventory(selectedSystem.systemId, query)
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

  protected selectSectionMode(sectionId: FlowExplorerResultSectionId, mode: FlowExplorerSectionMode): void {
    if (this.sectionModeFor(sectionId) === mode) {
      return;
    }
    this.resetJobState();
    this.sectionModes.update((sectionModes) =>
      sectionModes.map((sectionMode) =>
        sectionMode.id === sectionId ? { ...sectionMode, mode } : sectionMode
      )
    );
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

    const exportState = this.exportState();
    const previousJob = job;
    const previousExportState = exportState;
    if (exportState?.origin === 'local') {
      const localRunId = exportState.localRunId;
      if (!localRunId) {
        this.isSendingChat.set(false);
        this.chatError.set('Nie mozna kontynuowac lokalnego runu bez identyfikatora historii.');
        return;
      }

      this.job.set(appendOptimisticChatTurn(job, trimmedMessage));
      this.exportState.set(null);
      this.historyApi
        .sendChatMessage(localRunId, { message: trimmedMessage })
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({
          next: (detail) => {
            this.isSendingChat.set(false);
            this.applyLocalFlowExplorerRun(detail);
          },
          error: (error: HttpErrorResponse) => {
            this.isSendingChat.set(false);
            this.job.set(previousJob);
            this.exportState.set(previousExportState);
            this.chatError.set(this.errorMessage(error, 'Nie udalo sie wyslac lokalnego pytania follow-up.'));
          }
        });
      return;
    }

    this.job.set(appendOptimisticChatTurn(job, trimmedMessage));
    this.exportState.set(null);
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
          this.job.set(previousJob);
          this.exportState.set(previousExportState);
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

  protected async copyFlowExplorerResult(): Promise<void> {
    const job = this.job();
    if (!job?.report) {
      return;
    }

    const markdown = buildFlowExplorerReportMarkdown(job.report, `${job.httpMethod} ${job.endpointPath}`);

    if (!markdown) {
      return;
    }

    const copied = await copyTextToClipboard(markdown);
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

  protected async copyFollowUpPrompt(prompt: string, index: number): Promise<void> {
    const copied = await copyTextToClipboard(prompt);
    if (!copied) {
      this.jobError.set('Nie udalo sie skopiowac promptu do schowka.');
      return;
    }

    this.jobError.set('');
    this.copiedFollowUpPromptIndex.set(index);
    this.clearFollowUpPromptCopyFeedback();
    this.followUpPromptCopyFeedbackHandle = window.setTimeout(() => {
      this.copiedFollowUpPromptIndex.set(null);
      this.followUpPromptCopyFeedbackHandle = null;
    }, 1600);
  }

  protected sectionModeFor(sectionId: FlowExplorerResultSectionId): FlowExplorerSectionMode {
    return this.sectionModes().find((sectionMode) => sectionMode.id === sectionId)?.mode ?? 'COMPACT';
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

  protected sectionModeButtonClass(
    sectionId: FlowExplorerResultSectionId,
    mode: FlowExplorerSectionMode
  ): string {
    return [
      'flow-explorer-mode-segment__button',
      this.sectionModeFor(sectionId) === mode ? 'flow-explorer-mode-segment__button--selected' : ''
    ]
      .filter(Boolean)
      .join(' ');
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

  protected hasText(value: string | null | undefined): boolean {
    return typeof value === 'string' && value.trim().length > 0;
  }

  protected hasDisplayMeta(meta: FlowExplorerDisplayMeta | null | undefined): boolean {
    return Boolean(
      meta &&
        (this.hasItems(meta.references) ||
          this.hasItems(meta.visibilityLimits) ||
          this.hasItems(meta.openQuestions) ||
          this.hasItems(meta.gaps) ||
          this.hasItems(meta.warnings))
    );
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
    switch (normalizeSearch(mode)) {
      case 'deep':
        return 'status-pill status-pill--done';
      case 'off':
        return 'status-pill status-pill--error';
      default:
        return 'status-pill status-pill--queued';
    }
  }

  protected sectionModeLabel(mode: string): string {
    switch (normalizeSearch(mode)) {
      case 'deep':
        return 'deep';
      case 'off':
        return 'off';
      default:
        return 'compact';
    }
  }

  protected sectionIcon(sectionId: string): string {
    switch (sectionId) {
      case 'FUNCTIONAL_FLOW':
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

  protected followUpPromptCopyLabel(index: number): string {
    return this.copiedFollowUpPromptIndex() === index ? 'Skopiowano pytanie' : 'Kopiuj pytanie';
  }

  protected followUpPromptCountLabel(count: number): string {
    if (count === 1) {
      return '1 sugestia';
    }

    const lastDigit = count % 10;
    const lastTwoDigits = count % 100;
    const pluralSuffix = lastDigit >= 2 && lastDigit <= 4 && (lastTwoDigits < 12 || lastTwoDigits > 14)
      ? 'sugestie'
      : 'sugestii';

    return `${count} ${pluralSuffix}`;
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
          this.aiModelCatalog.set(normalizeAnalysisAiModelOptions(options));
          this.syncAiModelSelection();
          this.syncReasoningEffortSelection();
        },
        error: (error: HttpErrorResponse) => {
          this.aiModelCatalog.set(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
          this.aiModelOptionsError.set(this.errorMessage(error, 'Nie udalo sie pobrac modeli AI.'));
          this.syncAiModelSelection();
          this.syncReasoningEffortSelection();
        }
      });
  }

  private startPolling(jobId: string): void {
    this.stopPolling();
    this.pollingTimer = setInterval(() => this.refreshJob(jobId), POLL_INTERVAL_MS);
    this.refreshJob(jobId);
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

  private clearFollowUpPromptCopyFeedback(): void {
    if (this.followUpPromptCopyFeedbackHandle === null) {
      return;
    }
    window.clearTimeout(this.followUpPromptCopyFeedbackHandle);
    this.followUpPromptCopyFeedbackHandle = null;
  }

  private resetJobState(): void {
    this.stopPolling();
    this.clearResultCopyFeedback();
    this.clearFollowUpPromptCopyFeedback();
    this.job.set(null);
    this.exportState.set(null);
    this.jobError.set('');
    this.resultCopied.set(false);
    this.copiedFollowUpPromptIndex.set(null);
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
    metadata: FlowExplorerExportMetadata
  ): void {
    const normalizedJob = normalizeFlowExplorerJob(job);
    this.job.set(normalizedJob);
    this.syncExportableState(normalizedJob, metadata);
  }

  private syncExportableState(
    job: FlowExplorerJobStateSnapshot,
    metadata: FlowExplorerExportMetadata
  ): void {
    if (job.status !== 'COMPLETED' || !job.report || this.hasActiveChat(job)) {
      this.exportState.set(null);
      return;
    }

    this.exportState.set({
      ...metadata,
      origin: metadata.origin,
      exportedAt: metadata.exportedAt,
      fileName: metadata.fileName,
      job: normalizeFlowExplorerJob(job)
    });
  }

  private loadLocalFlowExplorerRun(analysisId: string): void {
    this.resetJobState();
    this.isSubmitting.set(true);

    this.historyApi
      .getRun(analysisId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isSubmitting.set(false))
      )
      .subscribe({
        next: (detail) => this.applyLocalFlowExplorerRun(detail),
        error: (error: HttpErrorResponse) => {
          this.jobError.set(this.errorMessage(error, 'Nie udalo sie wczytac lokalnego runu Flow Explorera.'));
        }
      });
  }

  private applyLocalFlowExplorerRun(detail: LocalAnalysisRunDetailResponse): void {
    try {
      if (detail.feature !== 'flow-explorer') {
        throw new Error(`Lokalny run ${detail.analysisId} nie jest runem Flow Explorera.`);
      }

      const imported = parseImportedFlowExplorerAnalysis(detail.exportEnvelope, {
        requireCompleted: false
      });
      this.applyJobSnapshot(imported.job, {
        origin: 'local',
        exportedAt: imported.exportedAt,
        fileName: '',
        localRunId: detail.analysisId,
        localRunName: detail.name,
        continuationEnabled: detail.continuationEnabled
      });
      this.syncImportedControls(imported.job);
      if (!this.isTerminalJobStatus(imported.job.status) || this.hasActiveChat(imported.job)) {
        this.startPolling(imported.job.jobId || detail.analysisId);
      }
    } catch (error) {
      this.jobError.set(
        error instanceof Error
          ? error.message
          : 'Nie udalo sie odtworzyc lokalnego runu Flow Explorera.'
      );
    }
  }

  private syncImportedControls(job: FlowExplorerJobStateSnapshot): void {
    this.selectedSystemId.set(job.systemId);
    this.selectedEndpointId.set(job.endpointId);
    this.branch.set(job.branch || this.branch());
    this.analysisGoal.set(job.goal || 'DEEP_DISCOVERY');
    this.sectionModes.set(normalizeSectionModes(job.sectionModes, job.focusAreas));
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
    const selectedAiModel = this.selectedAiModel().trim()
      || listedDefaultAiModel(this.aiModelCatalog());
    const selectedReasoningEffort = this.selectedReasoningEffort().trim()
      || defaultReasoningEffortForAiModel(this.aiModelCatalog(), selectedAiModel);
    return {
      systemId: selectedSystem.systemId,
      endpointId: selectedEndpoint.endpointId,
      httpMethod: selectedEndpoint.method || selectedEndpoint.methods[0],
      endpointPath: selectedEndpoint.path,
      branch: this.branch().trim() || undefined,
      goal: this.analysisGoal(),
      focusAreas: this.focusAreas(),
      sectionModes: this.sectionModes(),
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

  private sectionModeCounts(): { off: number; compact: number; deep: number } {
    return this.sectionModes().reduce(
      (counts, sectionMode) => {
        switch (sectionMode.mode) {
          case 'OFF':
            counts.off += 1;
            break;
          case 'DEEP':
            counts.deep += 1;
            break;
          default:
            counts.compact += 1;
        }
        return counts;
      },
      { off: 0, compact: 0, deep: 0 }
    );
  }

  private syncAiModelSelection(): void {
    if (this.selectedAiModel().trim()) {
      return;
    }

    this.selectedAiModel.set(listedDefaultAiModel(this.aiModelCatalog()));
  }

  private syncReasoningEffortSelection(): void {
    const availableEfforts = reasoningEffortsForAiModel(
      this.aiModelCatalog(),
      this.selectedAiModel()
    );
    const selectedReasoningEffort = this.selectedReasoningEffort().trim();
    if (!availableEfforts.length) {
      this.selectedReasoningEffort.set('');
      return;
    }
    if (selectedReasoningEffort && availableEfforts.includes(selectedReasoningEffort)) {
      return;
    }

    this.selectedReasoningEffort.set(
      defaultReasoningEffortForAiModel(this.aiModelCatalog(), this.selectedAiModel())
    );
  }

  private reasoningEffortsForModel(modelId: string): string[] {
    return reasoningEffortsForAiModel(this.aiModelCatalog(), modelId);
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

function displayResultFromReport(
  job: FlowExplorerJobStateSnapshot,
  report: AnalysisReport
): FlowExplorerDisplayResult {
  const sections = sortedReportSections(report.sections);
  const overviewSection = sections.find(isOverviewReportSection) ?? null;
  const overviewMarkdown = combineDistinctMarkdown(
    report.markdownSummary,
    overviewSection?.markdown ?? ''
  );
  const overviewMeta = displayMetaFromReport(overviewSection?.meta ?? report.meta);
  const overview =
    overviewSection || hasTextValue(overviewMarkdown) || hasDisplayMetaValue(overviewMeta)
      ? {
          id: overviewSection?.id || 'OVERVIEW',
          title: firstText(overviewSection?.title, 'Overview'),
          mode: 'overview',
          markdown: overviewMarkdown,
          meta: overviewMeta
        }
      : null;

  return {
    title: firstText(report.header, endpointLabel(job), 'Flow Explorer result'),
    subTitle: cleanText(report.subHeader),
    status: firstText(job.status, 'COMPLETED'),
    goal: job.goal || 'DEEP_DISCOVERY',
    confidence: firstText(report.meta?.confidence, overview?.meta.confidence, 'confidence n/a'),
    overview,
    sections: sections
      .filter((section) => !isOverviewReportSection(section))
      .map((section) => displaySectionFromReport(job, section)),
    appendix: displayMetaFromReport(report.meta),
    followUpPrompts: [],
    usage: usageFromJob(job),
    emptyMessage: ''
  };
}

function displaySectionFromReport(
  job: FlowExplorerJobStateSnapshot,
  section: AnalysisReportSection
): FlowExplorerDisplaySection {
  return {
    id: cleanText(section.id),
    title: reportSectionTitle(section),
    mode: reportSectionMode(job, section.id),
    markdown: cleanText(section.markdown),
    meta: displayMetaFromReport(section.meta)
  };
}

function displayMetaFromReport(meta: AnalysisReportMeta | null | undefined): FlowExplorerDisplayMeta {
  return {
    references: (meta?.references ?? [])
      .map(displayReferenceFromReport)
      .filter(
        (reference) =>
          hasTextValue(reference.label) ||
          hasTextValue(reference.target) ||
          hasTextValue(reference.type) ||
          hasTextValue(reference.description)
      ),
    visibilityLimits: cleanTextList(meta?.visibilityLimits),
    openQuestions: cleanTextList(meta?.openQuestions),
    gaps: cleanTextList(meta?.gaps),
    warnings: cleanTextList(meta?.warnings),
    confidence: cleanText(meta?.confidence)
  };
}

function displayReferenceFromReport(reference: AnalysisReportReference): AnalysisReportReference {
  return {
    type: cleanText(reference.type),
    label: firstText(reference.label, reference.target, reference.type, reference.description, 'Reference'),
    target: cleanText(reference.target),
    description: cleanText(reference.description)
  };
}

function emptyDisplayMeta(confidence = ''): FlowExplorerDisplayMeta {
  return {
    references: [],
    visibilityLimits: [],
    openQuestions: [],
    gaps: [],
    warnings: [],
    confidence
  };
}

function sortedReportSections(sections: AnalysisReportSection[] | null | undefined): AnalysisReportSection[] {
  return [...(sections ?? [])].sort((left, right) => {
    const leftOrder = typeof left.order === 'number' ? left.order : Number.MAX_SAFE_INTEGER;
    const rightOrder = typeof right.order === 'number' ? right.order : Number.MAX_SAFE_INTEGER;
    return leftOrder - rightOrder;
  });
}

function isOverviewReportSection(section: AnalysisReportSection): boolean {
  return normalizeSearch(section.id) === 'overview';
}

function reportSectionTitle(section: AnalysisReportSection): string {
  const id = cleanText(section.id);
  if (hasTextValue(section.title)) {
    return section.title.trim();
  }
  if (isSectionId(id)) {
    return sectionTitle(id);
  }
  return firstText(id, 'Section');
}

function reportSectionMode(job: FlowExplorerJobStateSnapshot, sectionId: string): FlowExplorerSectionMode {
  const normalizedId = cleanText(sectionId).toUpperCase();
  const assignment = job.sectionModes.find((sectionMode) => sectionMode.id === normalizedId);
  return assignment?.mode ?? 'COMPACT';
}

function endpointLabel(job: FlowExplorerJobStateSnapshot): string {
  return firstText(`${job.httpMethod} ${job.endpointPath}`.trim(), job.endpointId, 'Flow Explorer result');
}

function usageFromJob(job: FlowExplorerJobStateSnapshot): AnalysisAiUsage | null {
  const aiStepUsage = job.steps.find((step) => step.code === 'AI_ANALYSIS' && step.usage)?.usage;
  if (aiStepUsage) {
    return aiStepUsage;
  }
  return [...job.steps].reverse().find((step) => step.usage)?.usage ?? null;
}

function combineDistinctMarkdown(...values: Array<string | null | undefined>): string {
  const seen = new Set<string>();
  const parts: string[] = [];
  values.forEach((value) => {
    const markdown = cleanMarkdown(value);
    if (!markdown) {
      return;
    }
    const key = markdown.replace(/\s+/g, ' ').toLowerCase();
    if (seen.has(key)) {
      return;
    }
    seen.add(key);
    parts.push(markdown);
  });
  return parts.join('\n\n');
}

function cleanMarkdown(value: string | null | undefined): string {
  return cleanText(value).replace(/\r\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim();
}

function cleanTextList(values: string[] | null | undefined): string[] {
  return (values ?? []).map(cleanText).filter(hasTextValue);
}

function cleanText(value: string | null | undefined): string {
  return typeof value === 'string' ? value.trim() : '';
}

function firstText(...values: Array<string | null | undefined>): string {
  return values.map(cleanText).find(hasTextValue) ?? '';
}

function hasTextValue(value: string | null | undefined): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}

function hasDisplayMetaValue(meta: FlowExplorerDisplayMeta): boolean {
  return Boolean(
    meta.references.length ||
      meta.visibilityLimits.length ||
      meta.openQuestions.length ||
      meta.gaps.length ||
      meta.warnings.length
  );
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

function formatEndpointInventoryDate(value: string | null | undefined): string {
  if (!value) {
    return '';
  }

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '';
  }

  return new Intl.DateTimeFormat('pl-PL', {
    dateStyle: 'short',
    timeStyle: 'short'
  }).format(date);
}

function normalizeSearch(value: string): string {
  return value.trim().toLowerCase();
}

function copySectionModes(sectionModes: FlowExplorerSectionModeRequest[]): FlowExplorerSectionModeRequest[] {
  return sectionModes.map((sectionMode) => ({ ...sectionMode }));
}

function normalizeSectionModes(
  sectionModes: unknown,
  focusAreas: FlowExplorerFocusArea[] = []
): FlowExplorerSectionModeRequest[] {
  const byId = new Map<FlowExplorerResultSectionId, FlowExplorerSectionMode>();
  if (Array.isArray(sectionModes)) {
    sectionModes.forEach((candidate) => {
      if (!candidate || typeof candidate !== 'object') {
        return;
      }
      const value = candidate as Partial<FlowExplorerSectionModeRequest>;
      if (isSectionId(value.id) && isSectionMode(value.mode)) {
        byId.set(value.id, value.mode);
      }
    });
  }

  if (byId.size === 0) {
    focusAreas.forEach((focusArea) => byId.set(focusArea, 'DEEP'));
  }

  return DEFAULT_SECTION_MODES.map((sectionMode) => ({
    id: sectionMode.id,
    mode: byId.get(sectionMode.id) ?? 'COMPACT'
  }));
}

function sectionTitle(sectionId: FlowExplorerResultSectionId): string {
  return FOCUS_AREA_OPTIONS.find((option) => option.value === sectionId)?.label ?? sectionId;
}

function isSectionId(value: unknown): value is FlowExplorerResultSectionId {
  return (
    value === 'FUNCTIONAL_FLOW' ||
    value === 'VALIDATIONS' ||
    value === 'PERSISTENCE' ||
    value === 'INTEGRATIONS'
  );
}

function isSectionMode(value: unknown): value is FlowExplorerSectionMode {
  return value === 'OFF' || value === 'COMPACT' || value === 'DEEP';
}
