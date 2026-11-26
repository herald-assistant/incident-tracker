import { afterRenderEffect, Component, computed, DestroyRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatTooltipModule } from '@angular/material/tooltip';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ChoiceSelectComponent } from '../../components/choice-select/choice-select';
import { matchesProfileUrl } from './assistant-profiles';
import { assistantErrorMessage, SystemAssistantApi } from './system-assistant-api.service';
import { AssistantConfigurationEditor } from './assistant-configuration-editor';
import { AnalysisResultTabsComponent } from '../../components/analysis-result-tabs/analysis-result-tabs';
import { AssistantConversation, AssistantProfileOption } from './system-assistant.models';
import { AnalysisFollowUpChatComponent } from '../../components/analysis-follow-up-chat/analysis-follow-up-chat';
import { AiExecutionControlsComponent } from '../../components/ai-execution-controls/ai-execution-controls';
import { AnalysisFeatureAsideComponent } from '../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisStepsPanelComponent } from '../../components/analysis-steps-panel/analysis-steps-panel';
import { AnalysisJobPollingService } from '../../core/services/analysis-job-polling.service';
import { AiOptionsApiService } from '../../core/services/ai-options-api.service';
import { GithubAuthService } from '../../core/services/github-auth.service';
import { EMPTY_ANALYSIS_AI_MODEL_OPTIONS, normalizeAnalysisAiModelOptions, defaultReasoningEffortForAiModel } from '../../core/utils/analysis-ai-model-options.utils';
import { concatMap, from, of, Subscription, switchMap } from 'rxjs';
import type { FormTourSource } from '../../core/models/form-tour';
import { SystemAssistantIngress, SYSTEM_ASSISTANT_WINDOW } from './system-assistant-ingress.service';
import { AssistantConversationPicker } from './assistant-conversation-picker';
import { BrowserToolsSetupComponent } from '../../components/browser-tools-setup/browser-tools-setup';

interface LoginMessage {
  conversationId: string;
  profileId: string;
  message: string;
  autoSend: boolean;
}

@Component({
  selector: 'app-system-assistant-page',
  imports: [AssistantConfigurationEditor, ChoiceSelectComponent, AssistantConversationPicker, NgTemplateOutlet, AnalysisResultTabsComponent, MatTooltipModule, AnalysisFollowUpChatComponent, AiExecutionControlsComponent, AnalysisFeatureAsideComponent, AnalysisStepsPanelComponent, BrowserToolsSetupComponent],
  providers: [SystemAssistantIngress],
  templateUrl: './system-assistant-page.html',
  styleUrl: './system-assistant-page.scss'
})
export class SystemAssistantPage implements OnInit, FormTourSource {
  private readonly loginContextKey = 'tdw.system-assistant.login-context';
  private readonly browser = inject(SYSTEM_ASSISTANT_WINDOW);
  private readonly route = inject(ActivatedRoute, {optional: true});
  private readonly router = inject(Router, {optional: true});
  readonly embedded = this.route?.snapshot.data['embeddedChat'] === true;
  private readonly loginReturnPath = this.embedded ? '/system-assistant/chat' : '/system-assistant';
  private readonly pendingLoginMessage = signal<LoginMessage | null>(null);
  private readonly authStatusLoaded = signal(false);
  readonly historyOpen = signal(this.embedded);
  readonly historyError = signal('');
  private conversationGeneration = 0;
  private readonly drafts = new Map<string, {text: string; error: string; unsent: string}>();
  readonly restoring = signal(false);
  readonly restoreError = signal(false);
  private restoredId = this.route?.snapshot.queryParamMap?.get('localRunId') || '';
  protected readonly api = inject(SystemAssistantApi);
  protected readonly destroyRef = inject(DestroyRef);
  readonly activeProfiles = signal<AssistantProfileOption[]>([]);
  readonly profileId = signal(this.route?.snapshot.queryParamMap?.get('profileId') || '');
  readonly availableProfiles = computed(() => this.activeProfiles().filter(profile => {
    if (!this.embedded) return true;
    const page = this.ingress.context();
    return page ? matchesProfileUrl(profile.urlPattern, page.origin + page.pathname) : profile.urlPattern === '*';
  }));
  readonly profileOptions = computed(() => this.availableProfiles().map(profile => ({value: profile.id, label: profile.name})));
  readonly selectedProfileId = computed(() => this.availableProfiles().some(profile => profile.id === this.profileId()) ? this.profileId() : '');
  readonly profileNotice = computed(() => this.availableProfiles().length ? 'Wybierz profil i model przed wysłaniem wiadomości.'
    : this.embedded ? 'Brak profili pasujących do strony. Dodaj lub popraw profil w konfiguracji TDW.' : 'Dodaj profil w zakładce Profile.');
  readonly workspaceView = signal('skills');
  readonly workspaceTabs = [
    {id: 'skills', tabLabel: 'Skille'},
    {id: 'contracts', tabLabel: 'Kontrakty'},
    {id: 'auth', tabLabel: 'Auth'},
    {id: 'profiles', tabLabel: 'Profile'},
    {id: 'conversation', tabLabel: 'Rozmowa'},
    {id: 'launch', tabLabel: 'Uruchamianie w systemie'}
  ];
  readonly error = signal('');
  readonly notice = signal('');
  readonly ready = signal(false);
  readonly configurationAvailable = signal(false);
  readonly ingress = inject(SystemAssistantIngress);
  readonly pageContext = computed(() => this.conversation() ? this.conversation()!.pageContext : this.ingress.context());
  readonly dirty = signal(false);
  readonly authBusyId = signal('');
  readonly conversation = signal<AssistantConversation | null>(null);
  readonly chatError = signal('');
  readonly loginNotice = signal('');
  readonly unsentMessage = signal('');
  private readonly chatPanel = viewChild(AnalysisFollowUpChatComponent);
  readonly formTourSteps = computed(() => {
    if (!this.ready()) return [];
    if (this.workspaceView() === 'skills') return [
      {anchor: 'assistant-skills', title: 'Instrukcje pracy', description: 'Dodaj lub popraw skill i zapisz aktualny zestaw.'}
    ];
    if (this.workspaceView() === 'auth') return [{anchor: 'assistant-auth', title: 'Uwierzytelnianie API', description: 'Dodaj nazwane uwierzytelnianie i zapisz. Wybierz je przy API w zakładce Profile. OIDC wymaga logowania przez stronę dostawcy.'}];
    if (this.workspaceView() === 'profiles') return [{anchor: 'assistant-profiles', title: 'Profile', description: 'Nazwij zestaw i przypisz istniejące skille oraz API. Zapisz, aby go aktywować.'}];
    if (this.workspaceView() === 'contracts') return [
      {anchor: 'assistant-api', title: 'Kontrakty OpenAPI', description: 'Wczytaj kontrakt w modalu i wybierz operacje. Podgląd pokazuje dokumentację i schematy.'}
    ];
    if (this.workspaceView() === 'launch') return [
      {anchor: 'assistant-launch', title: 'Uruchamianie w systemie', description: 'Zainstaluj Browser Tools i otwórz System Assistant ze strony systemu.'}
    ];
    return [
      {anchor: 'assistant-model', title: 'Model AI', description: 'Wybierz model i poziom rozumowania albo pozostaw ustawienia domyślne.'},
      {anchor: 'assistant-chat', title: 'Rozmowa', description: 'Zadaj pytanie lub wydaj polecenie. Przebieg pokazuje wykonane operacje i ich wyniki.'}
    ];
  });
  readonly submitting = signal(false);
  readonly uploading = signal(false);
  readonly pendingAttachmentNames = computed(() => (this.conversation()?.attachments ?? [])
    .filter(file => !file.messageId).map(file => file.name));
  readonly messageAttachmentNames = computed(() => {
    const names: Record<string, string[]> = Object.create(null);
    for (const file of this.conversation()?.attachments ?? []) {
      if (file.messageId) (names[file.messageId] ??= []).push(file.name);
    }
    return names;
  });
  readonly authReady = signal(false);
  readonly catalog = signal(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
  readonly catalogLoading = signal(true);
  readonly catalogError = signal('');
  readonly model = signal('');
  readonly reasoningEffort = signal('');
  readonly messages = computed(() => this.conversation()?.messages ?? []);
  readonly activities = computed(() => this.messages().flatMap(message => message.aiActivityEvents));
  readonly evidence = computed(() => this.messages().flatMap(message => message.toolEvidenceSections));
  readonly busy = computed(() => this.restoring() || this.uploading() || this.submitting() || !!this.conversation()?.busy);
  readonly canChat = computed(() => !this.uploading() && !this.historyOpen() && !this.restoring() && !this.restoreError() && this.configurationAvailable() && this.ingress.status() !== 'waiting' && this.authReady() && !this.catalogLoading() && !this.catalogError()
    && !!this.selectedProfileId() && !!(this.model() || this.catalog().defaultModel));
  private readonly polling = inject(AnalysisJobPollingService);
  private readonly optionsApi = inject(AiOptionsApiService);
  protected readonly auth = inject(GithubAuthService);
  private pollSubscription?: Subscription;
  private profileRequest?: Subscription;

  constructor() {
    afterRenderEffect(() => {
      const pending = this.pendingLoginMessage();
      const chat = this.chatPanel();
      if (!pending || !chat) return;
      const conversation = this.conversation();
      if (this.restoredId !== pending.conversationId || (conversation && conversation.id !== pending.conversationId)) {
        this.pendingLoginMessage.set(null); return;
      }
      chat.messageText.set(pending.message);
      if (pending.autoSend && (this.restoring() || !this.ready() || this.catalogLoading() || !this.authStatusLoaded())) return;
      this.pendingLoginMessage.set(null);
      if (!pending.autoSend || !this.canChat() || this.busy() || conversation?.id !== pending.conversationId
          || this.selectedProfileId() !== pending.profileId) return;
      chat.messageText.set(''); this.drafts.delete(pending.conversationId);
      this.unsentMessage.set(pending.message); this.submitting.set(true); this.chatError.set('');
      this.submitMessage(pending.conversationId, pending.message, true);
    });
  }

  ngOnInit(): void {
    const authReturn = this.route?.snapshot.queryParamMap?.get('auth');
    if ((authReturn === 'success' || authReturn === 'failed') && this.restoredId) this.restoreLoginContext(authReturn === 'success');
    this.ingress.start();
    if (authReturn) {
      if (this.restoredId) {
        this.workspaceView.set('conversation');
        this.loginNotice.set(authReturn === 'success' ? '' : 'Nie udało się zalogować. Spróbuj ponownie.');
      }
      else {
        this.workspaceView.set('auth');
        this.notice.set(authReturn === 'success' ? 'Zalogowano. API może korzystać z połączenia OIDC.'
          : 'Nie udało się zalogować. Sprawdź konfigurację i rozpocznij logowanie ponownie.');
      }
      void this.router?.navigate([], {relativeTo: this.route, queryParams: {auth: null}, queryParamsHandling: 'merge', replaceUrl: true});
    }
    if (this.restoredId) { this.historyOpen.set(false); this.restoreConversation(); }
    if (this.embedded) {
      this.api.profiles().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: profiles => this.configurationLoaded(profiles),
        error: error => this.configurationLoadFailed(error)
      });
    }
  }

  configurationLoaded(profiles: AssistantProfileOption[]): void {
    this.profileRequest?.unsubscribe();
    this.activeProfiles.set(profiles);
    this.configurationAvailable.set(true);
    this.initializeChat();
  }

  configurationLoadFailed(error: HttpErrorResponse): void {
    this.error.set(assistantErrorMessage(error));
    if (error.error?.code === 'CONFIGURATION_UNAVAILABLE') this.initializeChat();
  }

  private initializeChat(): void {
    if (this.ready()) return;
    this.ready.set(true);
    this.loadAi();
  }
  private loadAi(): void {
    this.optionsApi.getOptions().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => { this.catalog.set(normalizeAnalysisAiModelOptions(value)); this.catalogLoading.set(false); },
      error: () => { this.catalogLoading.set(false); this.catalogError.set('Nie udało się pobrać katalogu modeli. Odśwież stronę, aby spróbować ponownie.'); }
    });
    this.auth.getStatus().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => { this.authReady.set(value.configured); this.authStatusLoaded.set(true); },
      error: () => { this.authStatusLoaded.set(true); this.chatError.set('Nie udało się sprawdzić PAT Copilota. Sprawdź Workspace Settings.'); }
    });
  }
  chooseModel(model: string): void {
    this.model.set(model); this.reasoningEffort.set(defaultReasoningEffortForAiModel(this.catalog(), model));
  }
  attachFiles(input: HTMLInputElement): void {
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (!files.length || !this.canChat() || this.busy()) return;
    this.uploading.set(true); this.chatError.set('');
    const generation = this.conversationGeneration;
    const conversation = this.conversation();
    const source = conversation ? of(conversation) : this.api.create(this.ingress.context());
    source.pipe(
      switchMap(snapshot => {
        this.conversation.set(snapshot); this.updateConversationUrl(snapshot.id);
        return from(files).pipe(concatMap(file => this.api.attach(snapshot.id, file)));
      }), takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: snapshot => { if (generation === this.conversationGeneration) this.conversation.set(snapshot); },
      error: error => {
        this.chatError.set(assistantErrorMessage(error));
        const id = this.conversation()?.id;
        if (!id) { this.uploading.set(false); return; }
        // A lost response can still mean the file was saved. Reconcile without retrying the upload.
        this.api.conversation(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
          next: snapshot => { if (generation === this.conversationGeneration) this.conversation.set(snapshot); },
          error: () => this.uploading.set(false),
          complete: () => this.uploading.set(false)
        });
      },
      complete: () => this.uploading.set(false)
    });
  }
  send(message: string): void {
    if (!this.canChat() || this.busy()) return;
    this.pendingLoginMessage.set(null);
    this.loginNotice.set('');
    this.unsentMessage.set(message);
    this.submitting.set(true); this.chatError.set('');
    const existing = this.conversation();
    if (!existing) {
      this.api.create(this.ingress.context()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: conversation => { this.conversation.set(conversation); this.updateConversationUrl(conversation.id); this.submitMessage(conversation.id, message); },
        error: error => { this.submitting.set(false); this.chatError.set(assistantErrorMessage(error)); }
      });
    } else this.submitMessage(existing.id, message);
  }
  private submitMessage(id: string, message: string, resumedAfterLogin = false): void {
    const generation = this.conversationGeneration;
    const profileId = this.selectedProfileId();
    const previousMessageIds = new Set(this.conversation()?.messages.map(item => item.id) ?? []);
    this.api.send(id, message, profileId, this.model(), this.reasoningEffort()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: snapshot => { this.conversation.set(snapshot); this.unsentMessage.set(''); this.submitting.set(false); this.startPolling(); },
      error: error => {
        if (error instanceof HttpErrorResponse && error.status === 409 && error.error?.code === 'AUTH_LOGIN_REQUIRED'
            && typeof error.error.authProfileId === 'string' && error.error.authProfileId) {
          this.unsentMessage.set(''); this.chatPanel()?.messageText.set(message);
          if (resumedAfterLogin) {
            this.submitting.set(false);
            this.chatError.set('Nie udało się uzyskać dostępu do API po zalogowaniu. Spróbuj ponownie.');
            return;
          }
          this.loginForConversation(error.error.authProfileId, id, profileId, message);
          return; // Rejected before acceptance; only a successful bound login can resume it.
        }
        this.submitting.set(false); this.chatError.set(assistantErrorMessage(error));
        // Lost HTTP response may still mean the turn was accepted. Reconcile without resubmitting.
        this.api.conversation(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
          next: snapshot => {
            if (this.conversationGeneration !== generation || this.conversation()?.id !== id) return;
            this.conversation.set(snapshot);
            if (snapshot.messages.some(item => item.role === 'USER' && item.content === message && !previousMessageIds.has(item.id))) this.unsentMessage.set('');
            if (snapshot.busy) this.startPolling();
          }, error: () => {}
        });
      }
    });
  }
  private loginForConversation(authProfileId: string, conversationId: string, profileId: string, message: string): void {
    if (!this.rememberLoginContext(conversationId, profileId, message)) {
      this.submitting.set(false);
      this.chatError.set('Przeglądarka blokuje zachowanie wiadomości na czas logowania. Treść pozostaje w polu; sprawdź ustawienia prywatności i spróbuj ponownie.');
      return;
    }
    this.authBusyId.set(authProfileId); this.loginNotice.set('Przekierowywanie do logowania…');
    this.api.loginAuth(authProfileId, {conversationId, profileId, returnPath: this.loginReturnPath}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => this.browser.location.assign(result.authorizationUrl),
      error: error => {
        this.clearLoginContext();
        this.authBusyId.set(''); this.submitting.set(false); this.loginNotice.set(''); this.chatError.set(assistantErrorMessage(error));
      }
    });
  }
  private rememberLoginContext(conversationId: string, profileId: string, message: string): boolean {
    const context = this.embedded ? this.ingress.context() : null;
    try {
      this.browser.sessionStorage.setItem(this.loginContextKey,
        JSON.stringify({conversationId, profileId, returnPath: this.loginReturnPath, expiresAt: Date.now() + 300_000, context, message,
          model: this.model(), reasoningEffort: this.reasoningEffort()}));
      return true;
    } catch { this.clearLoginContext(); return false; }
  }
  private restoreLoginContext(autoSend: boolean): void {
    try {
      const raw = this.browser.sessionStorage.getItem(this.loginContextKey);
      if (!raw) return;
      this.browser.sessionStorage.removeItem(this.loginContextKey);
      const saved = JSON.parse(raw);
      const keys = ['conversationId', 'profileId', 'returnPath', 'expiresAt', 'context', 'message', 'model', 'reasoningEffort'];
      if (!saved || typeof saved !== 'object' || Array.isArray(saved) || Object.keys(saved).length !== keys.length
          || !keys.every(key => Object.hasOwn(saved, key))
          || !Number.isFinite(saved.expiresAt) || saved.expiresAt <= Date.now() || saved.expiresAt > Date.now() + 300_000
          || saved.conversationId !== this.restoredId || saved.profileId !== this.profileId() || saved.returnPath !== this.loginReturnPath
          || typeof saved.message !== 'string' || !saved.message.trim()
          || typeof saved.model !== 'string' || typeof saved.reasoningEffort !== 'string') return;
      if (this.embedded) {
        if (saved.context !== null && !this.ingress.restoreContext(saved.context)) return;
      } else if (saved.context !== null) return;
      this.drafts.set(this.restoredId, {text: saved.message, error: '', unsent: ''});
      this.model.set(saved.model); this.reasoningEffort.set(saved.reasoningEffort);
      this.pendingLoginMessage.set({conversationId: saved.conversationId, profileId: saved.profileId, message: saved.message, autoSend});
    } catch { this.clearLoginContext(); }
  }
  private clearLoginContext(): void {
    try { this.browser.sessionStorage.removeItem(this.loginContextKey); } catch { /* Storage may be blocked in an iframe. */ }
  }
  restoreMessage(): void { this.chatPanel()?.messageText.set(this.unsentMessage()); this.unsentMessage.set(''); }
  startPolling(): void {
    this.pollSubscription?.unsubscribe();
    const id = this.conversation()?.id; if (!id || !this.conversation()?.busy) return;
    const generation = this.conversationGeneration;
    this.chatError.set('');
    this.pollSubscription = this.polling.poll({ load: () => this.api.conversation(id), isTerminal: snapshot => !snapshot.busy })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: snapshot => { if (this.conversationGeneration === generation && this.conversation()?.id === id) this.conversation.set(snapshot); },
        error: error => { if (this.conversationGeneration === generation && this.conversation()?.id === id) this.chatError.set(assistantErrorMessage(error) + ' Wznów odświeżanie, aby ustalić wynik; nie wysyłaj ponownie polecenia.'); }
      });
  }
  restoreConversation(): void {
    if (!this.restoredId || this.restoring()) return;
    this.workspaceView.set('conversation'); this.restoring.set(true); this.restoreError.set(false); this.chatError.set('');
    this.api.conversation(this.restoredId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: conversation => {
        this.conversation.set(conversation); this.restoring.set(false);
        if (conversation.busy) this.startPolling();
      },
      error: error => { this.restoring.set(false); this.restoreError.set(true); this.chatError.set(assistantErrorMessage(error)); }
    });
  }
  private updateConversationUrl(id: string | null): void {
    this.restoredId = id || '';
    if (!this.embedded && this.router && this.route) void this.router.navigate([], {
      relativeTo: this.route, queryParams: {localRunId: id}, queryParamsHandling: 'merge', replaceUrl: true
    });
  }
  openConversationHistory(): void {
    if (this.busy()) return;
    this.historyError.set(''); this.historyOpen.set(true);
  }
  continueConversation(id: string): void {
    if (this.busy()) return;
    this.pendingLoginMessage.set(null);
    this.restoring.set(true); this.historyError.set('');
    this.api.conversation(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: conversation => {
        this.saveDraft(); this.pollSubscription?.unsubscribe(); this.conversationGeneration++;
        this.conversation.set(conversation); this.updateConversationUrl(conversation.id);
        this.restoreDraft(conversation.id); this.restoreError.set(false);
        this.restoring.set(false); this.historyOpen.set(false);
        if (conversation.busy) this.startPolling();
      },
      error: error => { this.restoring.set(false); this.historyError.set(assistantErrorMessage(error)); }
    });
  }
  private saveDraft(): void {
    this.drafts.set(this.conversation()?.id || '', {text: this.chatPanel()?.messageText() || '', error: this.chatError(), unsent: this.unsentMessage()});
  }
  private restoreDraft(id: string): void {
    const draft = this.drafts.get(id);
    this.chatPanel()?.messageText.set(draft?.text || '');
    this.chatError.set(draft?.error || ''); this.unsentMessage.set(draft?.unsent || '');
  }
  newConversation(): void {
    if (this.busy()) return;
    this.pendingLoginMessage.set(null);
    if (!this.conversation() && !this.restoreError()) { this.historyOpen.set(false); return; }
    this.saveDraft();
    this.conversationGeneration++;
    this.pollSubscription?.unsubscribe();
    this.conversation.set(null); this.chatError.set(''); this.unsentMessage.set(''); this.restoreError.set(false);
    this.chatPanel()?.messageText.set(''); this.updateConversationUrl(null); this.historyOpen.set(false);
    this.restoreDraft('');
  }
  refreshProfiles(): void {
    this.profileRequest?.unsubscribe();
    this.profileRequest = this.api.profiles().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => this.activeProfiles.set(value),
      error: error => this.chatError.set(assistantErrorMessage(error))
    });
  }
}
