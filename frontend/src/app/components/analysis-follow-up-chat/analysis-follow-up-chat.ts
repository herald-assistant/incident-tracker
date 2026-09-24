import { Component, DestroyRef, ElementRef, computed, inject, input, output, signal, viewChild } from '@angular/core';

import {
  AnalysisAiToolFeedback,
  AnalysisChatMessageResponse,
  AnalysisEvidenceItem,
  AnalysisEvidenceSection
} from '../../core/models/analysis.models';
import {
  formatEvidenceSectionTitle
} from '../../core/utils/analysis-display.utils';
import { copyElementToClipboard } from '../../core/utils/clipboard.utils';
import { AttributeNamePipe } from '../../core/pipes/attribute-name.pipe';
import { MarkdownContentComponent } from '../markdown-content/markdown-content';
import { reportChangeDiff } from './report-change-diff.utils';
import type { ReportChangeDiffLine } from './report-change-diff.utils';

interface ReportChangeGroup {
  title: string;
  parts: { title: string | null; lines: ReportChangeDiffLine[] }[];
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
  readonly error = input('');
  readonly canUseChat = input(false);
  readonly isSubmitting = input(false);
  readonly needsAuth = input(false);
  readonly authActionLabel = input('Połącz GitHub');
  readonly openByDefault = input(false);
  readonly staticPanel = input(false);
  readonly eyebrow = input('Follow-up');
  readonly title = input('Kontynuacja analizy');
  readonly emptyText = input('Po zakończeniu analizy możesz dopytać AI o wynik albo zlecić dodatkową weryfikację.');
  readonly inputLabel = input('Wiadomość do AI');
  readonly placeholder = input('np. Poproś o doprecyzowanie wyniku albo dodatkową weryfikację.');
  readonly pendingText = input('AI analizuje polecenie i w razie potrzeby korzysta z dostępnych narzędzi.');
  readonly submitLabel = input('Wyślij');
  readonly submittingLabel = input('Wysyłanie');
  readonly ariaLabel = input('Follow-up chat');

  readonly sendMessage = output<string>();
  readonly clearError = output<void>();
  readonly connectAuth = output<void>();

  readonly messageText = signal('');
  readonly localError = signal('');
  readonly copiedChatMessageId = signal<string | null>(null);
  protected readonly reportChangeView = signal<ReportChangeGroup[] | null>(null);
  private readonly reportChangeDialog = viewChild<ElementRef<HTMLDialogElement>>('reportChangeDialog');

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

  protected preventStaticPanelToggle(event: Event): void {
    if (this.staticPanel()) {
      event.preventDefault();
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

  protected isReportChangeSection(section: AnalysisEvidenceSection): boolean {
    return section.provider === 'report' && section.category === 'report-change';
  }

  protected reportChangeItems(message: AnalysisChatMessageResponse): AnalysisEvidenceItem[] {
    return message.toolEvidenceSections
      .filter((section) => this.isReportChangeSection(section))
      .flatMap((section) => section.items)
      .filter((item) => item.attributes.some((attribute) => attribute.name === 'before')
        && item.attributes.some((attribute) => attribute.name === 'after'));
  }

  protected openReportChanges(message: AnalysisChatMessageResponse): void {
    const groups = new Map<string, ReportChangeGroup>();
    for (const item of this.reportChangeItems(message)) {
      const delimiter = item.title.indexOf(' — ');
      const title = delimiter >= 0 ? item.title.slice(0, delimiter) : item.title;
      const partTitle = delimiter >= 0 ? item.title.slice(delimiter + 3) : null;
      const before = item.attributes.find((attribute) => attribute.name === 'before')!.value;
      const after = item.attributes.find((attribute) => attribute.name === 'after')!.value;
      if (!groups.has(title)) {
        groups.set(title, { title, parts: [] });
      }
      groups.get(title)!.parts.push({ title: partTitle, lines: reportChangeDiff(before, after) });
    }
    if (groups.size === 0) {
      return;
    }
    this.reportChangeView.set([...groups.values()]);
    this.reportChangeDialog()?.nativeElement.showModal();
  }

  protected closeReportChange(): void {
    this.reportChangeDialog()?.nativeElement.close();
    this.reportChangeView.set(null);
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
