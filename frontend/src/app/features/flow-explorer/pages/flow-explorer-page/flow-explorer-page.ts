import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import {
  AnalysisAiActivityEvent,
  AnalysisAiToolFeedback,
  AnalysisEvidenceAttribute,
  AnalysisEvidenceItem,
  ApiErrorResponse
} from '../../../../core/models/analysis.models';
import {
  FlowExplorerAiResponse,
  FlowExplorerChatMessageResponse,
  FlowExplorerDocumentationPreset,
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

type CatalogState = 'empty' | 'loading' | 'ready' | 'error';
type EndpointState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';

interface FlowExplorerChoice<T extends string> {
  value: T;
  label: string;
  hint: string;
}

interface FlowExplorerResultSection {
  title: string;
  items: string[];
}

const POLL_INTERVAL_MS = 1500;
const MAX_FOCUS_AREAS = 4;

const DOCUMENTATION_PRESETS: FlowExplorerChoice<FlowExplorerDocumentationPreset>[] = [
  {
    value: 'ANALYST_OVERVIEW',
    label: 'Analyst overview',
    hint: 'Business-friendly explanation of endpoint behavior.'
  },
  {
    value: 'TEST_PREPARATION',
    label: 'Test preparation',
    hint: 'Focus on scenarios, validations and edge cases.'
  },
  {
    value: 'CHANGE_IMPACT',
    label: 'Change impact',
    hint: 'Highlight dependencies and areas likely to be affected.'
  },
  {
    value: 'TECHNICAL_HANDOFF',
    label: 'Technical handoff',
    hint: 'More concrete implementation grounding for delivery work.'
  }
];

const FOCUS_AREA_OPTIONS: FlowExplorerChoice<FlowExplorerFocusArea>[] = [
  {
    value: 'BUSINESS_FLOW',
    label: 'Business flow',
    hint: 'Plain-language request path and decisions.'
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
    value: 'EXTERNAL_INTEGRATIONS',
    label: 'External integrations',
    hint: 'Outbound clients, queues and handoffs.'
  },
  {
    value: 'TEST_SCENARIOS',
    label: 'Test scenarios',
    hint: 'Useful happy path, negative and regression checks.'
  },
  {
    value: 'RISKS_AND_OPEN_QUESTIONS',
    label: 'Risks',
    hint: 'Unclear behavior, assumptions and visibility limits.'
  }
];

@Component({
  selector: 'app-flow-explorer-page',
  templateUrl: './flow-explorer-page.html',
  styleUrl: './flow-explorer-page.scss'
})
export class FlowExplorerPageComponent implements OnInit {
  private readonly flowExplorerApi = inject(FlowExplorerApiService);
  private readonly destroyRef = inject(DestroyRef);
  private pollingTimer: ReturnType<typeof setInterval> | null = null;

  readonly documentationPresets = DOCUMENTATION_PRESETS;
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
  readonly branch = signal('');
  readonly selectedSystemId = signal('');
  readonly selectedEndpointId = signal('');
  readonly documentationPreset = signal<FlowExplorerDocumentationPreset>('ANALYST_OVERVIEW');
  readonly focusAreas = signal<FlowExplorerFocusArea[]>(['BUSINESS_FLOW']);
  readonly userInstructions = signal('');
  readonly job = signal<FlowExplorerJobStateSnapshot | null>(null);
  readonly jobError = signal('');
  readonly isSubmitting = signal(false);
  readonly chatMessage = signal('');
  readonly chatError = signal('');
  readonly isSendingChat = signal(false);

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
  readonly isChatAvailable = computed(() => Boolean(this.job()?.result && this.job()?.status === 'COMPLETED'));
  readonly canSendChat = computed(
    () =>
      this.isChatAvailable() &&
      !this.isSendingChat() &&
      !this.hasActiveChatMessage() &&
      this.chatMessage().trim().length > 0
  );
  readonly focusAreaLimitReached = computed(() => this.focusAreas().length >= MAX_FOCUS_AREAS);
  readonly systemCountLabel = computed(() => {
    const count = this.systems().length;
    return count === 1 ? '1 system' : `${count} systems`;
  });
  readonly endpointCountLabel = computed(() => {
    const count = this.endpoints().length;
    return count === 1 ? '1 endpoint' : `${count} endpoints`;
  });
  readonly filteredEndpointCountLabel = computed(() => {
    const count = this.filteredEndpoints().length;
    return count === 1 ? '1 match' : `${count} matches`;
  });
  readonly resultSections = computed(() => {
    const aiResponse = this.job()?.result?.aiResponse;
    if (!aiResponse) {
      return [];
    }
    return this.buildResultSections(aiResponse);
  });
  readonly sortedAiActivityEvents = computed(() => {
    const events = this.job()?.aiActivityEvents ?? [];
    return [...events].sort(
      (left, right) => this.timestampMs(left.timestamp) - this.timestampMs(right.timestamp)
    );
  });
  readonly toolEvidenceItemCount = computed(() =>
    (this.job()?.toolEvidenceSections ?? []).reduce((total, section) => total + section.items.length, 0)
  );
  readonly hasTraceData = computed(() => {
    const job = this.job();
    return Boolean(
      job &&
        (job.toolEvidenceSections.length > 0 ||
          job.aiActivityEvents.length > 0 ||
          job.toolFeedback.length > 0)
    );
  });

  constructor() {
    this.destroyRef.onDestroy(() => this.stopPolling());
  }

  ngOnInit(): void {
    this.loadConfig();
    this.loadSystems();
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
    this.loadEndpointInventory();
  }

  protected onBranchChanged(value: string): void {
    this.resetJobState();
    this.branch.set(value);
    this.selectedEndpointId.set('');
    this.endpointInventory.set(null);
    this.endpointError.set('');
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

  protected selectPreset(value: FlowExplorerDocumentationPreset): void {
    if (this.documentationPreset() === value) {
      return;
    }
    this.resetJobState();
    this.documentationPreset.set(value);
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

  protected onChatMessageChanged(value: string): void {
    this.chatMessage.set(value);
    if (this.chatError()) {
      this.chatError.set('');
    }
  }

  protected sendChatMessage(): void {
    const job = this.job();
    const message = this.chatMessage().trim();
    if (!job || !message || !this.canSendChat()) {
      return;
    }

    this.isSendingChat.set(true);
    this.chatError.set('');

    this.flowExplorerApi
      .sendChatMessage(job.jobId, { message })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.isSendingChat.set(false);
          this.chatMessage.set('');
          this.job.set(snapshot);
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

    this.flowExplorerApi
      .startJob(this.jobStartRequest(selectedSystem, selectedEndpoint))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (snapshot) => {
          this.isSubmitting.set(false);
          this.job.set(snapshot);
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

  protected isFocusAreaSelected(value: FlowExplorerFocusArea): boolean {
    return this.focusAreas().includes(value);
  }

  protected presetButtonClass(value: FlowExplorerDocumentationPreset): string {
    return this.documentationPreset() === value
      ? 'flow-explorer-choice flow-explorer-choice--selected'
      : 'flow-explorer-choice';
  }

  protected focusAreaButtonClass(value: FlowExplorerFocusArea): string {
    return this.isFocusAreaSelected(value)
      ? 'flow-explorer-choice flow-explorer-choice--selected'
      : 'flow-explorer-choice';
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

  protected systemRowClass(system: FlowExplorerSystemOption): string {
    return system.systemId === this.selectedSystemId()
      ? 'flow-explorer-system-row flow-explorer-system-row--selected'
      : 'flow-explorer-system-row';
  }

  protected endpointRowClass(endpoint: FlowExplorerEndpointOption): string {
    return endpoint.endpointId === this.selectedEndpointId()
      ? 'flow-explorer-endpoint-row flow-explorer-endpoint-row--selected'
      : 'flow-explorer-endpoint-row';
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

  protected formatTokenCount(value: number | null | undefined): string {
    if (!value || value <= 0) {
      return 'n/a';
    }
    return new Intl.NumberFormat('en-US').format(value);
  }

  protected formatCost(value: number | null | undefined): string {
    if (!value || value <= 0) {
      return 'n/a';
    }
    return `$${value.toFixed(4)}`;
  }

  protected activityEventTitle(event: AnalysisAiActivityEvent): string {
    return event.title || event.summary || event.toolName || event.type || 'AI activity';
  }

  protected activityEventMeta(event: AnalysisAiActivityEvent): string {
    return [event.category, event.type, event.toolName, this.formatTimestamp(event.timestamp)]
      .filter(Boolean)
      .join(' · ');
  }

  protected evidenceSectionTitle(provider: string, category: string): string {
    return [provider, category].filter(Boolean).join(' · ') || 'Tool evidence';
  }

  protected evidenceAttributePreview(item: AnalysisEvidenceItem): AnalysisEvidenceAttribute[] {
    return item.attributes.filter((attribute) => attribute.value).slice(0, 4);
  }

  protected toolFeedbackTitle(feedback: AnalysisAiToolFeedback): string {
    return feedback.targetToolName || feedback.targetToolCallId || 'Tool feedback';
  }

  protected feedbackMeta(feedback: AnalysisAiToolFeedback): string {
    return [feedback.usefulness, feedback.expectedDataReceived, feedback.confidence].filter(Boolean).join(' · ');
  }

  protected chatMessageClass(message: FlowExplorerChatMessageResponse): string {
    const role = normalizeSearch(message.role || 'assistant');
    return `flow-explorer-chat-message flow-explorer-chat-message--${role}`;
  }

  protected chatRoleLabel(message: FlowExplorerChatMessageResponse): string {
    return message.role === 'USER' ? 'You' : 'AI';
  }

  protected chatMessageMeta(message: FlowExplorerChatMessageResponse): string {
    return [this.chatRoleLabel(message), this.formatTimestamp(message.updatedAt || message.createdAt)]
      .filter(Boolean)
      .join(' · ');
  }

  protected chatEvidenceItemCount(message: FlowExplorerChatMessageResponse): number {
    return (message.toolEvidenceSections ?? []).reduce((total, section) => total + section.items.length, 0);
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
          this.job.set(snapshot);
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

  private resetJobState(): void {
    this.stopPolling();
    this.job.set(null);
    this.jobError.set('');
    this.isSubmitting.set(false);
    this.chatMessage.set('');
    this.chatError.set('');
    this.isSendingChat.set(false);
  }

  private isTerminalJobStatus(status: string): boolean {
    return status === 'COMPLETED' || status === 'FAILED';
  }

  private hasActiveChat(snapshot: FlowExplorerJobStateSnapshot | null | undefined): boolean {
    return Boolean(snapshot?.chatMessages?.some((message) => message.status === 'IN_PROGRESS'));
  }

  private jobStartRequest(
    selectedSystem: FlowExplorerSystemOption,
    selectedEndpoint: FlowExplorerEndpointOption
  ): FlowExplorerJobStartRequest {
    const userInstructions = this.userInstructions().trim();
    return {
      systemId: selectedSystem.systemId,
      endpointId: selectedEndpoint.endpointId,
      httpMethod: selectedEndpoint.method || selectedEndpoint.methods[0],
      endpointPath: selectedEndpoint.path,
      branch: this.branch().trim() || undefined,
      documentationPreset: this.documentationPreset(),
      focusAreas: this.focusAreas(),
      userInstructions: userInstructions || undefined
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

  private buildResultSections(aiResponse: FlowExplorerAiResponse): FlowExplorerResultSection[] {
    return [
      { title: 'Business rules', items: aiResponse.businessRules },
      { title: 'Validations', items: aiResponse.validations },
      { title: 'Persistence', items: aiResponse.persistence },
      { title: 'External integrations', items: aiResponse.externalIntegrations },
      { title: 'Test scenarios', items: aiResponse.testScenarios },
      { title: 'Risks and edge cases', items: aiResponse.risksAndEdgeCases },
      { title: 'Open questions', items: aiResponse.openQuestions }
    ]
      .map((section) => ({
        ...section,
        items: section.items.filter((item) => item.trim().length > 0)
      }))
      .filter((section) => section.items.length > 0);
  }

  private formatTimestamp(value: string): string {
    const timestamp = this.timestampMs(value);
    if (!timestamp) {
      return '';
    }
    return new Intl.DateTimeFormat('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    }).format(timestamp);
  }

  private timestampMs(value: string): number {
    if (!value) {
      return 0;
    }
    const timestamp = Date.parse(value);
    return Number.isNaN(timestamp) ? 0 : timestamp;
  }
}

function normalizeSearch(value: string): string {
  return value.trim().toLowerCase();
}
