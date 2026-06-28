import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';

import {
  AnalysisAiToolFeedback,
  AnalysisChatMessageResponse,
  AnalysisEvidenceSection
} from '../../core/models/analysis.models';
import {
  formatDateTime,
  formatEvidenceSectionTitle,
  formatStatus
} from '../../core/utils/analysis-display.utils';
import { copyElementToClipboard } from '../../core/utils/clipboard.utils';
import { AttributeNamePipe } from '../../core/pipes/attribute-name.pipe';
import { MarkdownContentComponent } from '../markdown-content/markdown-content';

export interface AnalysisResultUpdateReviewRequest {
  messageId: string;
  resultUpdate: unknown;
}

@Component({
  selector: 'app-analysis-follow-up-chat',
  imports: [MarkdownContentComponent, AttributeNamePipe],
  templateUrl: './analysis-follow-up-chat.html',
  styleUrl: './analysis-follow-up-chat.scss'
})
export class AnalysisFollowUpChatComponent {
  private readonly destroyRef = inject(DestroyRef);

  readonly messages = input<AnalysisChatMessageResponse[]>([]);
  readonly hint = input('');
  readonly error = input('');
  readonly canUseChat = input(false);
  readonly isSubmitting = input(false);
  readonly needsAuth = input(false);
  readonly authActionLabel = input('Połącz GitHub');
  readonly openByDefault = input(false);
  readonly eyebrow = input('Follow-up');
  readonly title = input('Kontynuacja analizy');
  readonly emptyText = input('Po zakończeniu analizy możesz dopytać AI o wynik albo zlecić dodatkową weryfikację.');
  readonly inputLabel = input('Wiadomość do AI');
  readonly placeholder = input('np. Poproś o doprecyzowanie wyniku albo dodatkową weryfikację.');
  readonly pendingText = input('AI analizuje polecenie i w razie potrzeby korzysta z dostępnych narzędzi.');
  readonly submitLabel = input('Wyślij');
  readonly submittingLabel = input('Wysyłanie');
  readonly ariaLabel = input('Follow-up chat');
  readonly canReviewResultUpdates = input(false);
  readonly reviewChangesLabel = input('Review changes');

  readonly sendMessage = output<string>();
  readonly clearError = output<void>();
  readonly connectAuth = output<void>();
  readonly reviewResultUpdate = output<AnalysisResultUpdateReviewRequest>();

  readonly messageText = signal('');
  readonly localError = signal('');
  readonly copiedChatMessageId = signal<string | null>(null);

  readonly hasActiveAssistantMessage = computed(() =>
    this.messages().some((message) => message.role === 'ASSISTANT' && message.status === 'IN_PROGRESS')
  );
  readonly displayedError = computed(() => this.localError() || this.error());
  readonly canSubmit = computed(
    () =>
      this.canUseChat() &&
      !this.isSubmitting() &&
      !this.hasActiveAssistantMessage() &&
      this.messageText().trim().length > 0
  );
  readonly statusLabel = computed(() =>
    this.isSubmitting() || this.hasActiveAssistantMessage()
      ? 'AI odpowiada'
      : `${this.messages().length} ${this.messages().length === 1 ? 'wiadomość' : 'wiadomości'}`
  );

  private copyFeedbackHandle: number | null = null;

  constructor() {
    this.destroyRef.onDestroy(() => this.clearCopyFeedback());
  }

  protected onMessageChanged(value: string): void {
    this.messageText.set(value);
    this.localError.set('');
    if (this.error()) {
      this.clearError.emit();
    }
  }

  protected submit(event: Event): void {
    event.preventDefault();

    const message = this.messageText().trim();
    if (!message) {
      this.localError.set('Wpisz pytanie albo polecenie do AI.');
      return;
    }
    if (!this.canUseChat()) {
      this.localError.set('Chat jest dostępny dopiero dla zakończonej analizy uruchomionej w backendzie.');
      return;
    }
    if (this.hasActiveAssistantMessage()) {
      this.localError.set('Poczekaj na zakończenie poprzedniej odpowiedzi AI.');
      return;
    }

    this.localError.set('');
    this.sendMessage.emit(message);
    this.messageText.set('');
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

  protected canReviewResultUpdate(message: AnalysisChatMessageResponse): boolean {
    return (
      this.canReviewResultUpdates() &&
      message.role === 'ASSISTANT' &&
      message.status === 'COMPLETED' &&
      message.resultUpdate !== undefined &&
      message.resultUpdate !== null
    );
  }

  protected reviewChatMessageResultUpdate(message: AnalysisChatMessageResponse): void {
    const resultUpdate = message.resultUpdate;
    if (!this.canReviewResultUpdate(message) || resultUpdate === undefined || resultUpdate === null) {
      return;
    }

    this.reviewResultUpdate.emit({
      messageId: message.id,
      resultUpdate
    });
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

  protected async copyChatMessage(
    messageElement: HTMLElement,
    message: AnalysisChatMessageResponse
  ): Promise<void> {
    const copied = await copyElementToClipboard(messageElement);
    if (!copied) {
      this.localError.set('Nie udało się skopiować wiadomości do schowka.');
      return;
    }

    this.localError.set('');
    this.copiedChatMessageId.set(message.id);
    this.clearCopyFeedback();
    this.copyFeedbackHandle = window.setTimeout(() => {
      if (this.copiedChatMessageId() === message.id) {
        this.copiedChatMessageId.set(null);
      }
      this.copyFeedbackHandle = null;
    }, 1600);
  }

  private clearCopyFeedback(): void {
    if (this.copyFeedbackHandle !== null) {
      window.clearTimeout(this.copyFeedbackHandle);
      this.copyFeedbackHandle = null;
    }
  }
}

function toolFeedbackUsefulnessLabel(value: string): string {
  if (!value) {
    return '';
  }
  const normalized = value.toLowerCase();
  if (normalized === 'useful') {
    return 'użyteczne';
  }
  if (normalized === 'partially_useful') {
    return 'częściowo użyteczne';
  }
  if (normalized === 'not_useful') {
    return 'nieużyteczne';
  }
  return value;
}

function toolFeedbackIssueLabel(value: string): string {
  if (!value || value.toLowerCase() === 'none') {
    return '';
  }
  return `problem: ${value}`;
}

function toolFeedbackImprovementLabel(value: string): string {
  if (!value || value.toLowerCase() === 'none') {
    return '';
  }
  return `poprawa: ${value}`;
}
