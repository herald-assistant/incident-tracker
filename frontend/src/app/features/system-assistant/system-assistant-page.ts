import { afterRenderEffect, Component, computed, DestroyRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Dialog, DialogModule, DialogRef } from '@angular/cdk/dialog';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ChoiceSelectComponent } from '../../components/choice-select/choice-select';
import { matchesProfileUrl, missingProfileResources } from './assistant-profiles';
import { TextFieldComponent } from '../../components/text-field/text-field';
import { SystemAssistantApi } from './system-assistant-api.service';
import { AssistantApiDefinition, AssistantConfiguration, AssistantTool, AssistantProfile } from './system-assistant.models';
import { OpenApiOperationPicker, OpenApiPickerResult } from './openapi-operation-picker';
import { OpenApiContractViewer } from './openapi-contract-viewer';
import { AnalysisResultTabsComponent } from '../../components/analysis-result-tabs/analysis-result-tabs';
import { AssistantConversation } from './system-assistant.models';
import { AnalysisFollowUpChatComponent } from '../../components/analysis-follow-up-chat/analysis-follow-up-chat';
import { AiExecutionControlsComponent } from '../../components/ai-execution-controls/ai-execution-controls';
import { AnalysisFeatureAsideComponent } from '../../components/analysis-feature-aside/analysis-feature-aside';
import { AnalysisStepsPanelComponent } from '../../components/analysis-steps-panel/analysis-steps-panel';
import { AnalysisJobPollingService } from '../../core/services/analysis-job-polling.service';
import { AiOptionsApiService } from '../../core/services/ai-options-api.service';
import { GithubAuthService } from '../../core/services/github-auth.service';
import { EMPTY_ANALYSIS_AI_MODEL_OPTIONS, normalizeAnalysisAiModelOptions, defaultReasoningEffortForAiModel } from '../../core/utils/analysis-ai-model-options.utils';
import { Subscription } from 'rxjs';
import type { FormTourSource } from '../../core/models/form-tour';
import { SystemAssistantIngress, SYSTEM_ASSISTANT_WINDOW } from './system-assistant-ingress.service';
import { AssistantConversationPicker } from './assistant-conversation-picker';
import { BrowserToolsSetupComponent } from '../../components/browser-tools-setup/browser-tools-setup';
import { downloadJsonFile, readJsonFile, sanitizeFileNamePart } from '../../core/utils/json-file.utils';
import { AssistantAuthProfiles } from './assistant-auth-profiles';
import { AssistantAuthProfile, AssistantOidcConnection } from './system-assistant.models';
import { AUTH_TYPES, authErrors, newAuthProfile } from './assistant-auth';
import { ASSISTANT_CONFIGURATION_FILE_MAX_BYTES, ASSISTANT_CONFIGURATION_FILE_NAME, configurationFile, parseConfigurationFile } from './system-assistant-configuration-file';

interface LoginMessage {
  conversationId: string;
  profileId: string;
  message: string;
  autoSend: boolean;
}

@Component({
  selector: 'app-system-assistant-page',
  imports: [AssistantAuthProfiles, ChoiceSelectComponent, AssistantConversationPicker, NgTemplateOutlet, AnalysisResultTabsComponent, DialogModule, TextFieldComponent, MatTooltipModule, AnalysisFollowUpChatComponent, AiExecutionControlsComponent, AnalysisFeatureAsideComponent, AnalysisStepsPanelComponent, BrowserToolsSetupComponent],
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
  readonly configuration = signal<AssistantConfiguration>({ apis: [], skills: [], profiles: [], authProfiles: [] });
  readonly expandedDefinitions = signal<Record<'skills' | 'profiles' | 'authProfiles', ReadonlySet<string>>>({
    skills: new Set(), profiles: new Set(), authProfiles: new Set()
  });
  readonly activeProfiles = signal<AssistantProfile[]>([]);
  readonly profileId = signal(this.route?.snapshot.queryParamMap?.get('profileId') || '');
  readonly availableProfiles = computed(() => this.activeProfiles().filter(profile => {
    if (!this.embedded) return true;
    const page = this.ingress.context();
    return page ? matchesProfileUrl(profile.urlPattern, page.origin + page.pathname) : profile.urlPattern === '*';
  }));
  readonly profileOptions = computed(() => this.availableProfiles().map(profile => ({value: profile.id, label: profile.name})));
  readonly selectedProfileId = computed(() => this.availableProfiles().some(profile => profile.id === this.profileId()) ? this.profileId() : '');
  readonly profileNotice = computed(() => this.availableProfiles().length ? 'Wybierz profil przed wysłaniem wiadomości.'
    : this.embedded ? 'Brak profili pasujących do strony. Dodaj lub popraw profil w konfiguracji TDW.' : 'Dodaj profil w zakładce Profile.');
  readonly tools = signal<AssistantTool[]>([]);
  readonly toolsLoading = signal(false);
  readonly toolsError = signal('');
  readonly contractActivity = computed(() => new Map(this.configuration().apis.map(api => {
    const tools = this.tools().filter(tool => tool.apiId === api.id);
    const count = this.toolsLoading() || this.toolsError() ? null : tools.length;
    const tooltip = this.toolsLoading() ? 'Odświeżanie aktywnych operacji…' : this.toolsError()
      || (tools.length ? `Aktywne operacje: ${tools.length}\n${tools.map(tool => `${tool.method} ${tool.path}`).join('\n')}`
        : 'Brak aktywnych operacji. Zapisz kontrakt, aby udostępnić jego operacje.');
    return [api.id, {count, tooltip}] as const;
  })));
  readonly workspaceView = signal('skills');
  readonly workspaceTabs = [
    {id: 'skills', tabLabel: 'Skille'},
    {id: 'contracts', tabLabel: 'Kontrakty'},
    {id: 'auth', tabLabel: 'Auth'},
    {id: 'profiles', tabLabel: 'Profile'},
    {id: 'conversation', tabLabel: 'Rozmowa'},
    {id: 'launch', tabLabel: 'Uruchamianie w systemie'}
  ];
  readonly configurationEntryCount = computed(() => {
    const kind = this.workspaceView() === 'contracts' ? 'apis' : this.workspaceView() === 'auth' ? 'authProfiles'
      : this.workspaceView() === 'profiles' ? 'profiles' : 'skills';
    return this.configuration()[kind].length;
  });
  readonly error = signal('');
  readonly saveError = signal('');
  readonly notice = signal('');
  readonly ready = signal(false);
  readonly configurationAvailable = signal(false);
  readonly ingress = inject(SystemAssistantIngress);
  readonly pageContext = computed(() => this.conversation() ? this.conversation()!.pageContext : this.ingress.context());
  readonly saving = signal(false);
  readonly importing = signal(false);
  readonly configurationBusy = computed(() => this.saving() || this.importing());
  readonly dirty = signal(false);
  readonly authConnections = signal<Record<string, AssistantOidcConnection>>({});
  readonly authBusyId = signal('');
  readonly authStatusError = signal('');
  readonly authValidationVisible = signal(false);
  private authStatusGeneration = 0;
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
  readonly authReady = signal(false);
  readonly catalog = signal(EMPTY_ANALYSIS_AI_MODEL_OPTIONS);
  readonly catalogLoading = signal(true);
  readonly catalogError = signal('');
  readonly model = signal('');
  readonly reasoningEffort = signal('');
  readonly messages = computed(() => this.conversation()?.messages ?? []);
  readonly activities = computed(() => this.messages().flatMap(message => message.aiActivityEvents));
  readonly evidence = computed(() => this.messages().flatMap(message => message.toolEvidenceSections));
  readonly busy = computed(() => this.restoring() || this.submitting() || !!this.conversation()?.busy);
  readonly canChat = computed(() => !this.historyOpen() && !this.restoring() && !this.restoreError() && this.configurationAvailable() && this.ingress.status() !== 'waiting' && this.authReady() && !this.catalogLoading() && !this.catalogError()
    && !!this.selectedProfileId() && !!(this.model() || this.catalog().defaultModel));
  private readonly polling = inject(AnalysisJobPollingService);
  private readonly optionsApi = inject(AiOptionsApiService);
  protected readonly auth = inject(GithubAuthService);
  private pollSubscription?: Subscription;
  private toolsSubscription?: Subscription;
  private readonly dialogs = inject(Dialog);
  private picker?: DialogRef<OpenApiPickerResult>;
  private viewer?: DialogRef<void>;

  constructor() {
    this.destroyRef.onDestroy(() => { this.picker?.close(); this.viewer?.close(); });
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
    this.api.configuration().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => { this.configuration.set(value); this.activeProfiles.set(value.profiles); this.ready.set(true); this.configurationAvailable.set(true); this.loadTools(); this.loadAi(); this.loadAuthStatus(); },
      error: error => {
        this.error.set(this.message(error));
        if (error.error?.code === 'CONFIGURATION_UNAVAILABLE') { this.ready.set(true); this.loadAi(); }
      }
    });
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
        error: error => { this.submitting.set(false); this.chatError.set(this.message(error)); }
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
        this.submitting.set(false); this.chatError.set(this.message(error));
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
        this.authBusyId.set(''); this.submitting.set(false); this.loginNotice.set(''); this.chatError.set(this.message(error));
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
        error: error => { if (this.conversationGeneration === generation && this.conversation()?.id === id) this.chatError.set(this.message(error) + ' Wznów odświeżanie, aby ustalić wynik; nie wysyłaj ponownie polecenia.'); }
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
      error: error => { this.restoring.set(false); this.restoreError.set(true); this.chatError.set(this.message(error)); }
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
      error: error => { this.restoring.set(false); this.historyError.set(this.message(error)); }
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
  loadTools(): void {
    this.toolsSubscription?.unsubscribe();
    this.toolsLoading.set(true); this.toolsError.set('');
    this.toolsSubscription = this.api.tools().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: tools => { this.tools.set(tools); this.toolsLoading.set(false); },
      error: () => { this.toolsLoading.set(false); this.toolsError.set('Nie udało się pobrać aktywnych operacji. Odśwież stronę, aby spróbować ponownie.'); }
    });
  }
  refreshProfiles(): void {
    this.api.configuration().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => this.activeProfiles.set(value.profiles),
      error: error => this.chatError.set(this.message(error))
    });
  }
  addProfile(): void {
    if (this.configurationBusy() || this.configuration().profiles.length >= 20) return;
    const id = crypto.randomUUID();
    this.configuration.update(value => ({...value, profiles: [...value.profiles,
      {id, name: 'Nowy profil', urlPattern: '*', apiBindings: [], skillIds: []}]}));
    this.setDefinitionExpanded('profiles', id, true);
    this.dirty.set(true); this.notice.set('');
  }
  updateProfile(index: number, key: 'name' | 'urlPattern', content: string): void {
    if (this.configurationBusy()) return;
    this.configuration.update(value => ({...value, profiles: value.profiles.map((item, i) => i === index ? {...item, [key]: content} : item)}));
    this.dirty.set(true); this.notice.set('');
  }
  toggleProfileResource(index: number, key: 'skillIds', id: string, checked: boolean): void {
    if (this.configurationBusy()) return;
    this.configuration.update(value => ({...value, profiles: value.profiles.map((item, i) => i === index
      ? {...item, [key]: checked ? [...new Set([...item[key], id])] : item[key].filter(current => current !== id)} : item)}));
    this.dirty.set(true); this.notice.set('');
  }
  profileWarning(profile: AssistantProfile): string { return missingProfileResources(profile, this.configuration()); }
  profileSummary(profile: AssistantProfile): string {
    const scope = profile.urlPattern === '*' ? 'Wszystkie adresy' : profile.urlPattern || 'Brak wzorca URL';
    return `${scope} · Skille: ${profile.skillIds.length} · API: ${profile.apiBindings.length}`;
  }
  profileResources(profile: AssistantProfile, kind: 'apis' | 'skills') {
    const resources = this.configuration()[kind].map(item => ({id: item.id, name: item.name}));
    const ids = kind === 'apis' ? profile.apiBindings.map(binding => binding.apiId) : profile.skillIds;
    return [...resources, ...ids.filter(id => !resources.some(item => item.id === id)).map(id => ({id, name: 'Brak: ' + id}))];
  }
  addSkill(): void {
    if (this.configurationBusy()) return;
    const id = `skill_${crypto.randomUUID().slice(0, 8)}`;
    const index = this.configuration().skills.length;
    this.configuration.update(value => ({...value, skills: [...value.skills, {id, name: 'Nowy skill', content: ''}]}));
    this.setDefinitionExpanded('skills', '' + index, true);
    this.dirty.set(true); this.notice.set('');
  }
  updateSkill(index: number, key: 'id' | 'name' | 'content', content: string): void {
    if (this.configurationBusy()) return;
    this.configuration.update(value => ({...value, skills: value.skills.map((item, i) => i === index ? {...item, [key]: content} : item)}));
    this.dirty.set(true); this.notice.set('');
  }
  remove(kind: 'apis' | 'skills' | 'profiles', index: number): void {
    if (this.configurationBusy()) return;
    if (kind === 'skills') {
      this.expandedDefinitions.update(value => ({...value, skills: new Set([...value.skills]
        .map(Number).filter(current => current !== index).map(current => '' + (current > index ? current - 1 : current)))}));
    } else if (kind === 'profiles') this.setDefinitionExpanded('profiles', this.configuration().profiles[index].id, false);
    this.configuration.update(value => ({ ...value, [kind]: value[kind].filter((_, i) => i !== index) }));
    this.dirty.set(true); this.notice.set('');
  }
  exportConfiguration(): void {
    if (this.configurationBusy()) return;
    downloadJsonFile(ASSISTANT_CONFIGURATION_FILE_NAME, configurationFile(this.configuration()));
  }
  async importConfiguration(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file || this.configurationBusy()) { input.value = ''; return; }
    this.importing.set(true); this.error.set(''); this.notice.set('');
    try {
      if (file.size > ASSISTANT_CONFIGURATION_FILE_MAX_BYTES) throw new Error('Plik konfiguracji może mieć do 100000000 bajtów.');
      const value = await readJsonFile(file, 'Plik konfiguracji nie zawiera poprawnego JSON-a.');
      if (this.destroyRef.destroyed) return;
      const configuration = parseConfigurationFile(value);
      this.expandedDefinitions.set({skills: new Set(), profiles: new Set(), authProfiles: new Set()});
      this.configuration.set(configuration); this.dirty.set(true); this.saveError.set('');
      this.notice.set(`Wczytano konfigurację do szkicu (kontrakty: ${configuration.apis.length}, skille: ${configuration.skills.length}, profile: ${configuration.profiles.length}, Auth: ${configuration.authProfiles.length}). Zapisz, aby ją aktywować.`);
      this.workspaceView.set('skills');
    } catch (error) {
      if (!this.destroyRef.destroyed) this.error.set(error instanceof Error ? error.message : 'Nie udało się odczytać pliku konfiguracji.');
    } finally {
      input.value = '';
      if (!this.destroyRef.destroyed) this.importing.set(false);
    }
  }
  async readNewOpenApi(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0]; if (!file) return;
    try {
      if (file.size > 2_000_000) throw new Error('Plik źródłowy OpenAPI może mieć do 2000000 bajtów.');
      this.openPicker(await file.text(), file.name);
    } catch (error) { this.error.set(error instanceof Error ? error.message : 'Nie udało się odczytać pliku.'); }
    input.value = '';
  }
  private openPicker(yaml: string, sourceName: string): void {
    if (this.configurationBusy() || this.destroyRef.destroyed || this.picker || this.configuration().apis.length >= 12) return;
    this.error.set('');
    const id = `api_${crypto.randomUUID().slice(0, 8)}`;
    this.picker = this.dialogs.open<OpenApiPickerResult>(OpenApiOperationPicker, {
      data: {id, yaml, sourceName}, ariaLabelledBy: 'openApiPickerTitle',
      autoFocus: 'first-tabbable', restoreFocus: true, closeOnNavigation: true
    });
    this.picker.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(result => {
      this.picker = undefined;
      if (!result) return;
      this.configuration.update(value => ({...value, apis: [...value.apis, {id, name: result.title, yaml: result.yaml}]}));
      this.dirty.set(true);
      this.notice.set(`Operacje w zestawie: ${result.operationCount}. Zapisz konfigurację, aby je udostępnić.`);
    });
  }
  viewContract(contract: AssistantApiDefinition): void {
    if (this.viewer) return;
    this.viewer = this.dialogs.open<void>(OpenApiContractViewer, {
      data: contract, ariaLabelledBy: 'openApiViewerTitle', autoFocus: 'first-tabbable', restoreFocus: true, closeOnNavigation: true
    });
    this.viewer.closed.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.viewer = undefined);
  }
  exportContract(contract: AssistantApiDefinition): void {
    if (this.configurationBusy()) return;
    const url = URL.createObjectURL(new Blob([contract.yaml], {type: 'application/yaml;charset=utf-8'}));
    const link = document.createElement('a');
    link.href = url; link.download = `${sanitizeFileNamePart(contract.name)}.yaml`;
    try {
      document.body.appendChild(link); link.click();
    } finally {
      link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 0);
    }
  }
  save(): void {
    if (this.configurationBusy()) return;
    this.authValidationVisible.set(true);
    if (this.configuration().authProfiles.some(profile => Object.keys(authErrors(profile)).length)) {
      for (const profile of this.configuration().authProfiles) {
        if (Object.keys(authErrors(profile)).length) this.setDefinitionExpanded('authProfiles', profile.id, true);
      }
      this.workspaceView.set('auth'); this.saveError.set('Uzupełnij zaznaczone pola uwierzytelniania.'); return;
    }
    this.saving.set(true); this.error.set(''); this.saveError.set(''); this.notice.set('');
    this.api.save(this.configuration()).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: value => {
        this.configuration.set(value); this.activeProfiles.set(value.profiles); this.configurationAvailable.set(true); this.saving.set(false); this.dirty.set(false);
        this.notice.set('Zapisano. Kolejna tura użyje bieżącego zestawu API i skilli.'); this.loadTools();
      },
      error: error => { this.saving.set(false); this.saveError.set(this.message(error)); }
    });
  }
  addAuth(): void {
    if (this.configurationBusy() || this.configuration().authProfiles.length >= 20) return;
    const profile = newAuthProfile('BASIC', `auth_${crypto.randomUUID().slice(0, 8)}`, 'Nowe uwierzytelnianie', window.location.origin);
    this.updateAuthProfiles([...this.configuration().authProfiles, profile]);
    this.setDefinitionExpanded('authProfiles', profile.id, true);
  }
  toggleDefinition(kind: 'skills' | 'profiles' | 'authProfiles', id: string): void {
    if (!this.configurationBusy()) this.setDefinitionExpanded(kind, id, !this.expandedDefinitions()[kind].has(id));
  }
  private setDefinitionExpanded(kind: 'skills' | 'profiles' | 'authProfiles', id: string, expanded: boolean): void {
    this.expandedDefinitions.update(value => {
      const ids = new Set(value[kind]);
      if (expanded) ids.add(id); else ids.delete(id);
      return {...value, [kind]: ids};
    });
  }
  updateAuthProfiles(authProfiles: AssistantAuthProfile[]): void {
    if (this.configurationBusy()) return;
    this.configuration.update(value => ({...value, authProfiles})); this.dirty.set(true); this.notice.set('');
  }
  profileHasApi(profile: AssistantProfile, apiId: string): boolean { return profile.apiBindings.some(binding => binding.apiId === apiId); }
  profileApiAuth(profile: AssistantProfile, apiId: string): string { return profile.apiBindings.find(binding => binding.apiId === apiId)?.authProfileId || ''; }
  apiAuthOptions(profile: AssistantProfile, apiId: string) {
    const options = [{value: '', label: 'No Auth'}, ...this.configuration().authProfiles.map(auth => ({value: auth.id,
      label: auth.name || 'Nowe uwierzytelnianie', hint: AUTH_TYPES.find(type => type.value === auth.type)?.label || auth.type}))];
    const selected = this.profileApiAuth(profile, apiId);
    return selected && !options.some(option => option.value === selected)
      ? [...options, {value: selected, label: 'Niedostępne uwierzytelnianie'}] : options;
  }
  toggleProfileApi(index: number, apiId: string, checked: boolean): void {
    if (this.configurationBusy()) return;
    this.configuration.update(value => ({...value, profiles: value.profiles.map((profile, i) => i !== index ? profile : {...profile,
      apiBindings: checked ? this.profileHasApi(profile, apiId) ? profile.apiBindings : [...profile.apiBindings, {apiId, authProfileId: null}]
        : profile.apiBindings.filter(binding => binding.apiId !== apiId)})}));
    this.dirty.set(true); this.notice.set('');
  }
  updateProfileApiAuth(index: number, apiId: string, authProfileId: string): void {
    if (this.configurationBusy()) return;
    this.configuration.update(value => ({...value, profiles: value.profiles.map((profile, i) => i !== index ? profile : {...profile,
      apiBindings: profile.apiBindings.map(binding => binding.apiId === apiId ? {...binding, authProfileId: authProfileId || null} : binding)})}));
    this.dirty.set(true); this.notice.set('');
  }
  loadAuthStatus(): void {
    const generation = ++this.authStatusGeneration;
    if (this.embedded || !this.configuration().authProfiles.some(profile => profile.type === 'OIDC')) {
      this.authConnections.set({}); this.authStatusError.set(''); return;
    }
    this.api.authStatus().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: statuses => { if (generation === this.authStatusGeneration) { this.authConnections.set(statuses); this.authStatusError.set(''); } },
      error: error => { if (generation === this.authStatusGeneration) this.authStatusError.set(this.message(error)); }
    });
  }
  loginAuth(id: string): void {
    if (this.dirty() || this.configurationBusy() || this.authBusyId()) return;
    this.authBusyId.set(id); this.saveError.set('');
    this.api.loginAuth(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: result => this.browser.location.assign(result.authorizationUrl),
      error: error => { this.authBusyId.set(''); this.saveError.set(this.message(error)); }
    });
  }
  disconnectAuth(id: string): void {
    if (this.dirty() || this.configurationBusy() || this.authBusyId()) return;
    this.authBusyId.set(id); this.saveError.set('');
    this.api.disconnectAuth(id).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => { this.authBusyId.set(''); this.loadAuthStatus(); },
      error: error => { this.authBusyId.set(''); this.saveError.set(this.message(error)); }
    });
  }
  protected message(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 404) return 'System Assistant jest wyłączony albo rozmowa już nie istnieje. Włącz system-assistant.enabled=true lub rozpocznij nową rozmowę.';
      if (error.error?.fieldErrors?.length) return error.error.fieldErrors.map((field: { field: string; message: string }) => `${field.field}: ${field.message}`).join('; ');
      return error.error?.message || 'Nie udało się wykonać operacji. Spróbuj ponownie.';
    }
    return 'Nie udało się wykonać operacji.';
  }
}
