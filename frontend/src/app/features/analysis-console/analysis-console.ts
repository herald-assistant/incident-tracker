import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  Component,
  DestroyRef,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild
} from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
  AnalysisAiUsage,
  AnalysisAiModelOptionsResponse,
  AnalysisAiToolFeedback,
  ApiErrorResponse,
  AnalysisChatMessageResponse,
  AnalysisEvidenceSection,
  AnalysisJobStateSnapshot,
  ExportState,
  GitHubAuthStatus,
  TransportErrorState
} from '../../core/models/analysis.models';
import { AnalysisApiService } from '../../core/services/analysis-api.service';
import { GithubAuthService } from '../../core/services/github-auth.service';
import {
  buildAnalysisActionsHint,
  buildJobBannerMessage,
  defaultErrorMessage,
  formatDateTime,
  formatEvidenceSectionTitle,
  formatStatus,
  hasInProgressChat,
  isTerminalStatus,
  statusClassName,
  valueOrFallback
} from '../../core/utils/analysis-display.utils';
import { copyElementToClipboard, copyTextToClipboard } from '../../core/utils/clipboard.utils';
import {
  buildExportEnvelope,
  buildExportFileName,
  normalizeAnalysisJob,
  parseImportedAnalysis
} from '../../core/utils/analysis-import-export.utils';
import {
  AnalysisAiCostEstimate,
  estimateAnalysisAiCost,
  GITHUB_AI_CREDIT_USD
} from '../../core/utils/analysis-ai-usage-cost.utils';
import { AnalysisFinalResultComponent } from '../../components/analysis-final-result/analysis-final-result';
import { AnalysisStepsPanelComponent } from '../../components/analysis-steps-panel/analysis-steps-panel';
import { MarkdownContentComponent } from '../../components/markdown-content/markdown-content';
import { AttributeNamePipe } from '../../core/pipes/attribute-name.pipe';

const POLL_INTERVAL_MS = 1500;
const EMPTY_AI_MODEL_OPTIONS: AnalysisAiModelOptionsResponse = {
  defaultModel: '',
  defaultReasoningEffort: '',
  defaultReasoningEfforts: [],
  models: []
};
type SelectOption = {
  value: string;
  label: string;
  disabled?: boolean;
};
type RunContextItem = {
  label: string;
  value: string;
  tooltip?: string;
};

@Component({
  selector: 'app-analysis-console',
  imports: [
    ReactiveFormsModule,
    MatTooltipModule,
    AnalysisFinalResultComponent,
    AnalysisStepsPanelComponent,
    MarkdownContentComponent,
    AttributeNamePipe
  ],
  templateUrl: './analysis-console.html',
  styleUrl: './analysis-console.scss'
})
export class AnalysisConsoleComponent {
  private readonly analysisApi = inject(AnalysisApiService);
  private readonly githubAuth = inject(GithubAuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly responsePanel = viewChild<ElementRef<HTMLElement>>('responsePanel');

  readonly correlationIdControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required]
  });
  readonly aiModelControl = new FormControl('', { nonNullable: true });
  readonly reasoningEffortControl = new FormControl('', { nonNullable: true });
  readonly chatMessageControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required]
  });

  readonly isLoading = signal(false);
  readonly isChatSubmitting = signal(false);
  readonly formError = signal('');
  readonly chatError = signal('');
  readonly placeholderMode = signal<'idle' | 'loading'>('idle');
  readonly loadingCorrelationId = signal('');
  readonly transportError = signal<TransportErrorState | null>(null);
  readonly job = signal<AnalysisJobStateSnapshot | null>(null);
  readonly exportState = signal<ExportState | null>(null);
  readonly isAiModelOptionsLoading = signal(false);
  readonly aiModelCatalog = signal<AnalysisAiModelOptionsResponse>(EMPTY_AI_MODEL_OPTIONS);
  readonly selectedAiModel = signal('');
  readonly githubAuthStatus = signal<GitHubAuthStatus | null>(null);
  readonly githubAuthError = signal('');
  readonly githubReauthRequiredByError = signal(false);
  readonly chatNeedsGithubAuth = signal(false);
  readonly copiedChatMessageId = signal<string | null>(null);
  readonly copiedPreparedPrompt = signal(false);

  readonly aiModelOptions = computed<SelectOption[]>(() => {
    if (this.isAiModelOptionsLoading()) {
      return [
        {
          value: this.aiModelControl.value.trim(),
          label: 'Ładowanie modeli AI...',
          disabled: true
        }
      ];
    }

    return [
      { value: '', label: this.defaultModelLabel() },
      ...this.aiModelCatalog().models.map((model) => ({
        value: model.id,
        label: this.modelLabel(model.id, model.name)
      }))
    ];
  });
  readonly availableReasoningEfforts = computed(() =>
    this.reasoningEffortsForModel(this.selectedAiModel())
  );
  readonly reasoningEffortOptions = computed<SelectOption[]>(() => {
    if (this.isAiModelOptionsLoading()) {
      return [
        {
          value: this.reasoningEffortControl.value.trim(),
          label: 'Ładowanie reasoning effort...',
          disabled: true
        }
      ];
    }

    return [
      { value: '', label: this.defaultReasoningEffortLabel() },
      ...this.availableReasoningEfforts().map((effort) => ({
        value: effort,
        label: this.reasoningEffortLabel(effort)
      }))
    ];
  });
  readonly isAnalysisBlockedByAuth = computed(() => {
    const status = this.githubAuthStatus();
    return status?.mode === 'GITHUB_APP' && (!status.connected || status.reauthRequired);
  });
  readonly authBadgeText = computed(() => {
    const status = this.githubAuthStatus();
    if (!status) {
      return 'Copilot: sprawdzanie';
    }
    if (status.mode === 'LOCAL_TOKEN') {
      return 'Copilot: token lokalny';
    }
    if (status.connected && status.githubLogin) {
      return `GitHub: ${status.githubLogin}`;
    }
    return status.reauthRequired && this.githubReauthRequiredByError()
      ? 'GitHub: połącz ponownie'
      : 'GitHub: wymagane połączenie';
  });
  readonly authPanelTitle = computed(() => {
    const status = this.githubAuthStatus();
    if (status?.mode !== 'GITHUB_APP') {
      return '';
    }
    return status.connected
      ? `Połączono jako ${status.githubLogin || status.displayName || 'GitHub'}`
      : 'Połącz konto GitHub, aby uruchomić analizę AI przez Copilot.';
  });
  readonly authPanelDescription = computed(() => {
    const status = this.githubAuthStatus();
    if (status?.mode === 'GITHUB_APP' && status.connected) {
      return 'Zużycie Copilot będzie przypisane do tego konta GitHub.';
    }
    if (status?.mode === 'GITHUB_APP') {
      return 'Token pozostaje wyłącznie po stronie backendu i nie trafia do requestów analizy.';
    }
    return '';
  });
  readonly authActionLabel = computed(() => {
    const status = this.githubAuthStatus();
    return status?.reauthRequired && this.githubReauthRequiredByError()
      ? 'Połącz ponownie GitHub'
      : 'Połącz GitHub';
  });

  readonly hasActiveState = computed(
    () =>
      this.placeholderMode() === 'loading' || this.transportError() !== null || this.job() !== null
  );
  readonly analysisActionsHint = computed(() => buildAnalysisActionsHint(this.exportState()));
  readonly canUseChat = computed(() => {
    const currentJob = this.job();
    return (
      currentJob?.status === 'COMPLETED' &&
      this.exportState()?.origin === 'live' &&
      !hasInProgressChat(currentJob)
    );
  });
  readonly chatHint = computed(() => {
    const currentJob = this.job();
    if (!currentJob) {
      return 'Chat będzie dostępny po zakończeniu analizy.';
    }
    if (this.exportState()?.origin === 'imported') {
      return 'Importowany zapis jest tylko do odczytu. Chat działa dla analiz żywych w backendzie.';
    }
    if (currentJob.status !== 'COMPLETED') {
      return 'Chat będzie dostępny po zakończeniu analizy.';
    }
    if (hasInProgressChat(currentJob)) {
      return 'AI przygotowuje odpowiedź na poprzednie polecenie.';
    }
    return 'Możesz dopytać o wynik, poprosić o weryfikację w repo lub DB albo wygenerować raport.';
  });
  readonly placeholderKicker = computed(() =>
    this.placeholderMode() === 'loading' ? 'Trwa' : 'Gotowe'
  );
  readonly placeholderTitle = computed(() =>
    this.placeholderMode() === 'loading'
      ? 'Uruchamiamy analizę...'
      : 'Tutaj pojawi się przebieg analizy.'
  );
  readonly placeholderDescription = computed(() => {
    if (this.placeholderMode() === 'loading') {
      return `Analiza dla correlationId ${this.loadingCorrelationId()} została zainicjowana. Pierwsze dane pojawią się za chwilę.`;
    }

    return 'Po uruchomieniu zobaczysz postęp kroków, zebrane dane źródłowe i końcową diagnozę bez odświeżania widoku.';
  });

  private activeAnalysisId: string | null = null;
  private pollHandle: number | null = null;
  private chatCopyFeedbackHandle: number | null = null;
  private promptCopyFeedbackHandle: number | null = null;

  constructor() {
    this.loadGithubAuthStatus();
    this.destroyRef.onDestroy(() => {
      this.stopPolling();
      this.clearChatCopyFeedback();
      this.clearPromptCopyFeedback();
    });
  }

  submit(event: Event): void {
    event.preventDefault();

    const correlationId = this.correlationIdControl.value.trim();
    if (!correlationId) {
      this.showFormError('Podaj correlationId, aby uruchomić analizę.');
      return;
    }

    if (this.isAnalysisBlockedByAuth()) {
      this.connectGithub();
      return;
    }

    this.correlationIdControl.setValue(correlationId);
    this.clearFormError();
    this.stopPolling();
    this.activeAnalysisId = null;
    this.job.set(null);
    this.transportError.set(null);
    this.exportState.set(null);
    this.chatError.set('');
    this.isLoading.set(true);
    this.placeholderMode.set('loading');
    this.loadingCorrelationId.set(correlationId);
    this.scrollResponseIntoView();

    const aiModel = this.aiModelControl.value.trim();
    const reasoningEffort = this.reasoningEffortControl.enabled
      ? this.reasoningEffortControl.value.trim()
      : '';

    this.analysisApi
      .startAnalysis({
        correlationId,
        model: aiModel || undefined,
        reasoningEffort: reasoningEffort || undefined
      })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.isLoading.set(false))
      )
      .subscribe({
        next: (job) => {
          this.activeAnalysisId = job.analysisId;
          this.applyJob(job, {
            origin: 'live',
            exportedAt: '',
            fileName: ''
          });

          if (!isTerminalStatus(job.status)) {
            this.schedulePoll(job.analysisId);
          }
        },
        error: (error) =>
          this.renderTransportError(
            error,
            'Nie udało się połączyć z backendem. Sprawdź, czy aplikacja działa lokalnie.'
          )
      });
  }

  clearFormError(): void {
    this.formError.set('');
  }

  clearChatError(): void {
    this.chatError.set('');
    this.chatNeedsGithubAuth.set(false);
  }

  connectGithub(): void {
    this.githubAuth.connect();
  }

  logoutGithub(): void {
    this.githubAuth
      .logout()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.isAiModelOptionsLoading.set(false);
          this.aiModelCatalog.set(EMPTY_AI_MODEL_OPTIONS);
          this.loadGithubAuthStatus();
        },
        error: (error) => {
          const transportError = this.toTransportError(error, 'Nie udało się rozłączyć GitHuba.');
          this.githubAuthError.set(transportError.message);
        }
      });
  }

  triggerImport(fileInput: HTMLInputElement): void {
    this.clearFormError();
    fileInput.value = '';
    fileInput.click();
  }

  async importAnalysis(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file) {
      return;
    }

    try {
      const content = await file.text();
      let parsedContent: unknown;

      try {
        parsedContent = JSON.parse(content);
      } catch {
        throw new Error('Wybrany plik nie zawiera poprawnego JSON-a.');
      }

      const imported = parseImportedAnalysis(parsedContent);

      this.stopPolling();
      this.activeAnalysisId = null;
      this.isLoading.set(false);
      this.clearFormError();
      this.placeholderMode.set('idle');
      this.transportError.set(null);
      this.job.set(imported.job);
      this.chatError.set('');
      this.syncExportableState(imported.job, {
        origin: 'imported',
        exportedAt: imported.exportedAt,
        fileName: file.name
      });

      if (imported.job.correlationId) {
        this.correlationIdControl.setValue(imported.job.correlationId);
      }
      this.aiModelControl.setValue(imported.job.aiModel || '');
      this.selectedAiModel.set(imported.job.aiModel || '');
      this.reasoningEffortControl.setValue(imported.job.reasoningEffort || '');
      this.syncReasoningEffortSelection();

      this.scrollResponseIntoView();
    } catch (error) {
      this.showFormError(
        error instanceof Error
          ? error.message
          : 'Nie udało się wczytać pliku z zapisem analizy.'
      );
    } finally {
      if (input) {
        input.value = '';
      }
    }
  }

  exportAnalysis(): void {
    const exportState = this.exportState();
    if (!exportState) {
      return;
    }

    const exportedAt = new Date().toISOString();
    const payload = buildExportEnvelope(exportState.job, exportedAt);
    const blob = new Blob([JSON.stringify(payload, null, 2)], {
      type: 'application/json'
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = buildExportFileName(exportState.job, exportedAt);
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
  }

  onAiModelChanged(): void {
    this.selectedAiModel.set(this.aiModelControl.value.trim());
    this.syncReasoningEffortSelection();
    this.clearFormError();
  }

  submitChat(event: Event): void {
    event.preventDefault();

    const currentJob = this.job();
    const analysisId = this.activeAnalysisId || currentJob?.analysisId || '';
    const message = this.chatMessageControl.value.trim();

    if (!message) {
      this.chatError.set('Wpisz pytanie albo polecenie do AI.');
      return;
    }

    if (!currentJob || currentJob.status !== 'COMPLETED' || !analysisId) {
      this.chatError.set('Chat jest dostępny dopiero dla zakończonej analizy uruchomionej w backendzie.');
      return;
    }

    if (hasInProgressChat(currentJob)) {
      this.chatError.set('Poczekaj na zakończenie poprzedniej odpowiedzi AI.');
      return;
    }

    this.chatError.set('');
    this.isChatSubmitting.set(true);
    this.chatMessageControl.disable({ emitEvent: false });

    this.analysisApi
      .sendChatMessage(analysisId, { message })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.isChatSubmitting.set(false);
          this.chatMessageControl.enable({ emitEvent: false });
        })
      )
      .subscribe({
        next: (job) => {
          this.chatMessageControl.setValue('', { emitEvent: false });
          this.activeAnalysisId = job.analysisId;
          this.applyJob(job, {
            origin: 'live',
            exportedAt: '',
            fileName: ''
          });

          if (this.shouldKeepPolling(job)) {
            this.schedulePoll(job.analysisId);
          }
        },
        error: (error) => {
          const transportError = this.toTransportError(
            error,
            'Nie udało się wysłać wiadomości do backendu.'
          );
          this.applyGithubAuthError(transportError.code);
          this.chatError.set(transportError.message);
          this.chatNeedsGithubAuth.set(this.isGithubAuthError(transportError.code));
        }
      });
  }

  protected chatMessageTitle(message: AnalysisChatMessageResponse): string {
    if (message.role === 'USER') {
      return 'Operator';
    }
    if (message.status === 'IN_PROGRESS') {
      return 'AI odpowiada';
    }
    if (message.status === 'FAILED') {
      return 'AI zakończyło odpowiedź błędem';
    }
    return 'AI';
  }

  protected chatMessageMeta(message: AnalysisChatMessageResponse): string {
    const status = message.role === 'ASSISTANT' ? formatStatus(message.status) : '';
    const timestamp = formatDateTime(message.completedAt || message.updatedAt || message.createdAt);
    return [status, timestamp].filter(Boolean).join(' · ');
  }

  protected canCopyChatMessage(message: AnalysisChatMessageResponse): boolean {
    return message.status !== 'IN_PROGRESS';
  }

  protected isLastPendingAssistantMessage(
    message: AnalysisChatMessageResponse,
    messages: AnalysisChatMessageResponse[]
  ): boolean {
    return (
      message.role === 'ASSISTANT' &&
      message.status === 'IN_PROGRESS' &&
      messages.length > 0 &&
      messages[messages.length - 1]?.id === message.id
    );
  }

  protected evidenceSectionTitle(section: AnalysisEvidenceSection): string {
    return formatEvidenceSectionTitle(section);
  }

  protected toolFeedbackTarget(feedback: AnalysisAiToolFeedback): string {
    return feedback.targetToolCallId
      ? `${feedback.targetToolName || 'tool'} (${feedback.targetToolCallId})`
      : feedback.targetToolName || 'tool';
  }

  protected toolFeedbackMeta(feedback: AnalysisAiToolFeedback): string {
    return [
      toolFeedbackUsefulnessLabel(feedback.usefulness),
      toolFeedbackIssueLabel(feedback.issueCategory),
      toolFeedbackImprovementLabel(feedback.improvementArea),
      feedback.confidence ? `pewność: ${feedback.confidence}` : ''
    ]
      .filter(Boolean)
      .join(' · ');
  }

  protected statusLabel(status: string): string {
    return formatStatus(status);
  }

  protected statusClass(status: string): string {
    return statusClassName(status);
  }

  protected jobBannerMessage(job: AnalysisJobStateSnapshot): string {
    return buildJobBannerMessage(job);
  }

  protected runContextItems(job: AnalysisJobStateSnapshot): RunContextItem[] {
    return [
      { label: 'Correlation ID', value: job.correlationId || 'n/a' },
      { label: 'Analysis ID', value: valueOrFallback(job.analysisId) },
      { label: 'Environment', value: valueOrFallback(job.environment) },
      { label: 'Branch', value: valueOrFallback(job.gitLabBranch) },
      { label: 'Model', value: job.aiModel || 'default backend' },
      { label: 'Reasoning', value: job.reasoningEffort || 'default backend' },
      {
        label: 'Usage',
        value: this.usageSummary(job),
        tooltip: this.usageTooltip(job)
      }
    ];
  }

  protected canCopyPreparedPrompt(job: AnalysisJobStateSnapshot | null): boolean {
    return Boolean(this.preparedPromptText(job));
  }

  protected async copyPreparedPrompt(job: AnalysisJobStateSnapshot | null): Promise<void> {
    const prompt = this.preparedPromptText(job);
    if (!prompt) {
      return;
    }

    const copied = await copyTextToClipboard(prompt);
    if (!copied) {
      this.formError.set('Nie udało się skopiować promptu do schowka.');
      return;
    }

    this.formError.set('');
    this.copiedPreparedPrompt.set(true);
    this.clearPromptCopyFeedback();
    this.promptCopyFeedbackHandle = window.setTimeout(() => {
      this.copiedPreparedPrompt.set(false);
      this.promptCopyFeedbackHandle = null;
    }, 1600);
  }

  protected async copyChatMessage(
    messageElement: HTMLElement,
    message: AnalysisChatMessageResponse
  ): Promise<void> {
    const copied = await copyElementToClipboard(messageElement);
    if (!copied) {
      this.chatError.set('Nie udało się skopiować wiadomości do schowka.');
      return;
    }

    this.chatError.set('');
    this.copiedChatMessageId.set(message.id);
    if (this.chatCopyFeedbackHandle !== null) {
      window.clearTimeout(this.chatCopyFeedbackHandle);
    }

    this.chatCopyFeedbackHandle = window.setTimeout(() => {
      if (this.copiedChatMessageId() === message.id) {
        this.copiedChatMessageId.set(null);
      }
      this.chatCopyFeedbackHandle = null;
    }, 1600);
  }

  private pollAnalysis(analysisId: string): void {
    this.analysisApi
      .getAnalysis(analysisId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (job) => {
          if (analysisId !== this.activeAnalysisId) {
            return;
          }

          this.applyJob(job, {
            origin: 'live',
            exportedAt: '',
            fileName: ''
          });

          if (this.shouldKeepPolling(job)) {
            this.schedulePoll(analysisId);
          }
        },
        error: (error) => {
          if (analysisId !== this.activeAnalysisId) {
            return;
          }

          this.renderTransportError(
            error,
            'Nie udało się odczytać statusu analizy z backendu.'
          );
        }
      });
  }

  private schedulePoll(analysisId: string): void {
    this.stopPolling();
    this.pollHandle = window.setTimeout(() => this.pollAnalysis(analysisId), POLL_INTERVAL_MS);
  }

  private stopPolling(): void {
    if (this.pollHandle !== null) {
      window.clearTimeout(this.pollHandle);
      this.pollHandle = null;
    }
  }

  private clearChatCopyFeedback(): void {
    if (this.chatCopyFeedbackHandle !== null) {
      window.clearTimeout(this.chatCopyFeedbackHandle);
      this.chatCopyFeedbackHandle = null;
    }
    this.copiedChatMessageId.set(null);
  }

  private clearPromptCopyFeedback(): void {
    if (this.promptCopyFeedbackHandle !== null) {
      window.clearTimeout(this.promptCopyFeedbackHandle);
      this.promptCopyFeedbackHandle = null;
    }
  }

  private loadGithubAuthStatus(): void {
    this.githubAuthError.set('');
    this.isAiModelOptionsLoading.set(true);
    this.githubAuth
      .getStatus()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (status) => {
          this.githubReauthRequiredByError.set(false);
          this.githubAuthStatus.set(this.normalizeGithubAuthStatus(status));
          if (status.connected) {
            this.loadAiModelOptions();
          } else {
            this.isAiModelOptionsLoading.set(false);
            this.aiModelCatalog.set(EMPTY_AI_MODEL_OPTIONS);
            this.syncReasoningEffortSelection();
          }
        },
        error: (error) => {
          const transportError = this.toTransportError(
            error,
            'Nie udało się odczytać statusu autoryzacji GitHub.'
          );
          this.githubAuthError.set(transportError.message);
          this.isAiModelOptionsLoading.set(false);
          this.aiModelCatalog.set(EMPTY_AI_MODEL_OPTIONS);
          this.syncReasoningEffortSelection();
        }
      });
  }

  private loadAiModelOptions(): void {
    this.isAiModelOptionsLoading.set(true);
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
        error: (error) => {
          const transportError = this.toTransportError(
            error,
            'Nie udało się pobrać listy modeli AI.'
          );
          this.applyGithubAuthError(transportError.code);
          this.githubAuthError.set(transportError.message);
          this.aiModelCatalog.set(EMPTY_AI_MODEL_OPTIONS);
          this.syncReasoningEffortSelection();
        }
      });
  }

  private normalizeGithubAuthStatus(status: GitHubAuthStatus | null): GitHubAuthStatus | null {
    if (!status) {
      return null;
    }

    return {
      mode: status.mode,
      required: Boolean(status.required),
      connected: Boolean(status.connected),
      githubLogin: status.githubLogin || null,
      displayName: status.displayName || null,
      tokenExpiresAt: status.tokenExpiresAt || null,
      reauthRequired: Boolean(status.reauthRequired),
      authStartUrl: status.authStartUrl || null
    };
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
    const currentEffort = this.reasoningEffortControl.value.trim();

    if (!availableEfforts.length) {
      this.reasoningEffortControl.setValue('', { emitEvent: false });
      this.reasoningEffortControl.disable({ emitEvent: false });
      return;
    }

    this.reasoningEffortControl.enable({ emitEvent: false });
    if (currentEffort && !availableEfforts.includes(currentEffort)) {
      this.reasoningEffortControl.setValue('', { emitEvent: false });
    }
  }

  private usageSummary(job: AnalysisJobStateSnapshot): string {
    const usage = job.result?.usage;
    if (!usage || !usage.totalTokens) {
      return 'not-available';
    }

    const tokens = formatTokenCount(usage.totalTokens);
    const cost =
      typeof usage.cost === 'number' && usage.cost > 0 ? ` / $${usage.cost.toFixed(4)}` : '';
    return `${tokens} tokens${cost}`;
  }

  private usageTooltip(job: AnalysisJobStateSnapshot): string {
    const usage = job.result?.usage ?? null;
    const estimate = estimateAnalysisAiCost(usage);
    if (!usage || !estimate) {
      return '';
    }

    return buildUsageTooltip(usage, estimate);
  }

  private preparedPromptText(job: AnalysisJobStateSnapshot | null): string {
    return job?.preparedPrompt || job?.result?.prompt || '';
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
    return defaultModel ? `Domyślny backend (${defaultModel})` : 'Domyślny backend';
  }

  private defaultReasoningEffortLabel(): string {
    const defaultEffort = this.aiModelCatalog().defaultReasoningEffort;
    return defaultEffort ? `Domyślny backend (${defaultEffort})` : 'Domyślny backend';
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

  private applyJob(job: AnalysisJobStateSnapshot, metadata: Pick<ExportState, 'origin' | 'exportedAt' | 'fileName'>): void {
    const normalizedJob = normalizeAnalysisJob(job);
    this.placeholderMode.set('idle');
    this.transportError.set(null);
    this.job.set(normalizedJob);
    this.syncExportableState(normalizedJob, metadata);
  }

  private syncExportableState(
    job: AnalysisJobStateSnapshot,
    metadata: Pick<ExportState, 'origin' | 'exportedAt' | 'fileName'>
  ): void {
    if (!isTerminalStatus(job.status) || hasInProgressChat(job)) {
      this.exportState.set(null);
      return;
    }

    this.exportState.set({
      origin: metadata.origin,
      exportedAt: metadata.exportedAt,
      fileName: metadata.fileName,
      job: normalizeAnalysisJob(job)
    });
  }

  private renderTransportError(error: unknown, networkFallbackMessage: string): void {
    this.stopPolling();
    this.activeAnalysisId = null;
    this.exportState.set(null);
    this.job.set(null);
    this.placeholderMode.set('idle');
    const transportError = this.toTransportError(error, networkFallbackMessage);
    this.applyGithubAuthError(transportError.code);
    this.transportError.set(transportError);
    this.scrollResponseIntoView();
  }

  private shouldKeepPolling(job: AnalysisJobStateSnapshot): boolean {
    return !isTerminalStatus(job.status) || hasInProgressChat(job);
  }

  private toTransportError(
    error: unknown,
    networkFallbackMessage: string
  ): TransportErrorState {
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    const payload = this.normalizeApiErrorPayload(
      error instanceof HttpErrorResponse ? error.error : null
    );

    return {
      code: payload?.code || 'REQUEST_FAILED',
      message:
        payload?.message ||
        (status > 0 ? defaultErrorMessage(status) : networkFallbackMessage),
      details:
        payload?.fieldErrors.length
          ? payload.fieldErrors.map((fieldError) => `${fieldError.field}: ${fieldError.message}`)
          : [status > 0 ? `HTTP status: ${status}` : 'Brak odpowiedzi HTTP od backendu.'],
      status,
      authStartUrl: payload?.authStartUrl || null
    };
  }

  private normalizeApiErrorPayload(payload: unknown): ApiErrorResponse | null {
    if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
      return null;
    }

    const payloadRecord = payload as Record<string, unknown>;
    return {
      code: typeof payloadRecord['code'] === 'string' ? payloadRecord['code'] : '',
      message: typeof payloadRecord['message'] === 'string' ? payloadRecord['message'] : '',
      authStartUrl:
        typeof payloadRecord['authStartUrl'] === 'string' ? payloadRecord['authStartUrl'] : null,
      fieldErrors: Array.isArray(payloadRecord['fieldErrors'])
        ? payloadRecord['fieldErrors']
            .filter(
              (fieldError): fieldError is Record<string, unknown> =>
                !!fieldError && typeof fieldError === 'object' && !Array.isArray(fieldError)
            )
            .map((fieldError) => ({
              field: typeof fieldError['field'] === 'string' ? fieldError['field'] : '',
              message: typeof fieldError['message'] === 'string' ? fieldError['message'] : ''
            }))
        : []
    };
  }

  private isGithubAuthError(code: string): boolean {
    return code === 'GITHUB_COPILOT_AUTH_REQUIRED' || code === 'GITHUB_COPILOT_REAUTH_REQUIRED';
  }

  private applyGithubAuthError(code: string): void {
    if (!this.isGithubAuthError(code)) {
      return;
    }

    const status = this.githubAuthStatus();
    if (status?.mode !== 'GITHUB_APP') {
      return;
    }

    this.githubAuthStatus.set({
      ...status,
      connected: false,
      githubLogin: null,
      displayName: null,
      reauthRequired: code === 'GITHUB_COPILOT_REAUTH_REQUIRED',
      authStartUrl: status.authStartUrl || '/api/auth/github/start'
    });
    this.githubReauthRequiredByError.set(code === 'GITHUB_COPILOT_REAUTH_REQUIRED');
  }

  private showFormError(message: string): void {
    this.formError.set(message);
  }

  private scrollResponseIntoView(): void {
    this.responsePanel()?.nativeElement.scrollIntoView?.({
      behavior: 'smooth',
      block: 'start'
    });
  }
}

function buildUsageTooltip(
  usage: AnalysisAiUsage,
  estimate: AnalysisAiCostEstimate
): string {
  const lines = [
    'Szacowany koszt analizy AI',
    '',
    `Tokens: ${formatTokenCount(usage.totalTokens)}`,
    `Credits: ${formatCredits(estimate.credits)}`,
    `Cost: ${formatDollars(estimate.dollars)}`,
    '',
    'Breakdown:',
    `Nowy input: ${formatTokenCount(
      estimate.newInputTokens
    )} tokens × ${formatUsdRate(estimate.inputUsdPerMillion)} / 1M`,
    `Cache read: ${formatTokenCount(
      estimate.cachedInputTokens
    )} tokenów × ${formatUsdRate(
      estimate.cachedInputUsdPerMillion
    )} / 1M`,
    `Odpowiedź AI: ${formatTokenCount(estimate.outputTokens)} tokenów × ${formatUsdRate(
      estimate.outputUsdPerMillion
    )} / 1M`
  ];

  if (estimate.cacheWriteTokens > 0) {
    if (estimate.cacheWriteUsdPerMillion !== null) {
      lines.push(
        `Zapis do cache: ${formatTokenCount(estimate.cacheWriteTokens)} tokenów × ${formatUsdRate(
          estimate.cacheWriteUsdPerMillion
        )} / 1M`
      );
    } else {
      lines.push(
        `Zapis do cache: ${formatTokenCount(
          estimate.cacheWriteTokens
        )} tokenów bez osobnej stawki cache-write.`
      );
    }
  }

  lines.push('');
  lines.push(
    `Pricing: ${estimate.pricingModel}${
      estimate.usedFallbackPricing ? ' (fallback)' : ''
    } · 1 credit = ${formatDollars(GITHUB_AI_CREDIT_USD)}`
  );

  if (usage.apiCallCount > 0) {
    lines.push(`Wywołania modelu: ${formatTokenCount(usage.apiCallCount)}`);
  }

  if (usage.apiDurationMs > 0) {
    lines.push(`Czas API: ${formatDurationMs(usage.apiDurationMs)}`);
  }

  if (usage.model) {
    lines.push(`Model SDK: ${usage.model}`);
  }

  if (usage.contextCurrentTokens !== null && usage.contextTokenLimit !== null) {
    lines.push(
      `Kontekst sesji: ${formatTokenCount(
        usage.contextCurrentTokens
      )} / ${formatTokenCount(
        usage.contextTokenLimit
      )} tokenów`
    );
  }

  if (usage.contextMessages !== null) {
    lines.push(`Wiadomości w sesji: ${formatTokenCount(usage.contextMessages)}`);
  }

  lines.push('');
  lines.push('Estymacja operacyjna, nie faktura.');

  return lines.join('\n');
}

function formatTokenCount(value: number | null | undefined): string {
  return String(Math.round(Number(value ?? 0))).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');
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

  return `${formatTokenCount(value)} ms`;
}

function toolFeedbackUsefulnessLabel(value: string): string {
  const labels: Record<string, string> = {
    useful: 'użyteczne',
    partial: 'częściowe',
    not_useful: 'nieprzydatne',
    invalid: 'niepoprawne',
    error: 'błąd'
  };
  return labels[value] || value || '';
}

function toolFeedbackIssueLabel(value: string): string {
  const labels: Record<string, string> = {
    no_issue: 'bez problemu',
    no_data: 'brak danych',
    wrong_scope: 'zły zakres',
    incomplete: 'niepełne',
    too_much_noise: 'szum',
    ambiguous_result: 'niejednoznaczne',
    stale_data: 'nieświeże dane',
    access_error: 'błąd dostępu',
    tool_error: 'błąd toola',
    schema_or_format_issue: 'schema/format',
    missing_operational_context: 'brak opctx',
    missing_code_scope: 'brak scope kodu',
    model_misused_tool: 'złe użycie przez AI',
    other: 'inne'
  };
  return labels[value] || value || '';
}

function toolFeedbackImprovementLabel(value: string): string {
  const labels: Record<string, string> = {
    none: 'bez usprawnień',
    tool_contract: 'kontrakt toola',
    tool_description: 'opis toola',
    tool_policy: 'polityka tooli',
    adapter_result: 'wynik adaptera',
    operational_context_data: 'dane opctx',
    code_search_scope: 'scope kodu',
    database_mapping: 'mapowanie DB',
    ui_presentation: 'prezentacja UI',
    other: 'inne'
  };
  return labels[value] || value || '';
}
