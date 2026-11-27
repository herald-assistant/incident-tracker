import { LocalAnalysisRunListItemResponse } from '../../core/models/analysis.models';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AssistantConfigurationEditor } from './assistant-configuration-editor';
import { SystemAssistantPage } from './system-assistant-page';
import { AssistantConfiguration, AssistantConversation } from './system-assistant.models';
import { Dialog } from '@angular/cdk/dialog';
import { CdkDragHandle, CdkDropList } from '@angular/cdk/drag-drop';
import { OpenApiOperationPicker } from './openapi-operation-picker';
import { By } from '@angular/platform-browser';
import { AnalysisFollowUpChatComponent } from '../../components/analysis-follow-up-chat/analysis-follow-up-chat';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { MatTooltipHarness } from '@angular/material/tooltip/testing';
import { MatMenuHarness } from '@angular/material/menu/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import {choice} from '../../../testing/form-controls';
import {newAuthProfile} from './assistant-auth';
import {SYSTEM_ASSISTANT_WINDOW} from './system-assistant-ingress.service';

function configurationEditor(fixture: ComponentFixture<SystemAssistantPage>): AssistantConfigurationEditor {
  return fixture.debugElement.query(By.directive(AssistantConfigurationEditor)).componentInstance;
}

function savedConversation(): AssistantConversation {
  return {id: 'saved-chat', busy: false, pageContext: {origin: 'https://crm.example', pathname: '/customers/C-100', title: 'Klient', capturedAt: '2026-11-21T10:00:00Z'},
    messages: ['Poprzednie pytanie', 'Poprzednia odpowiedź'].map((content, i) => ({
      id: `saved-${i}`, role: i ? 'ASSISTANT' : 'USER', status: 'COMPLETED', content,
      errorCode: '', errorMessage: '', createdAt: '', updatedAt: '', completedAt: '',
      toolEvidenceSections: [], aiActivityEvents: [], toolFeedback: [], prompt: ''
    }))};
}
const bridgeNonce = 'bridge_123456789012345678901234';
const currentIngress = {url: 'https://crm.example/tickets/CRM-202?tab=details#owner', origin: 'https://crm.example', pathname: '/tickets/CRM-202', title: 'CRM ticket', capturedAt: '2026-11-23T10:00:00Z'};
const loginContextKey = 'tdw.system-assistant.login-context';
const loginMessage = 'Sprawdź klienta';
const savedLoginContext = (embedded: boolean) => ({conversationId: 'saved-chat', profileId: 'crm_selected',
  returnPath: embedded ? '/system-assistant/chat' : '/system-assistant', expiresAt: Date.now() + 60_000,
  context: embedded ? currentIngress : null, nonce: embedded ? bridgeNonce : null, message: loginMessage, model: 'original-model', reasoningEffort: 'high'});

describe('SystemAssistantPage', () => {
  let http: HttpTestingController;
  let parentPage = currentIngress;
  beforeEach(() => {
    parentPage = currentIngress;
    TestBed.configureTestingModule({ imports: [SystemAssistantPage], providers: [provideHttpClient(), provideHttpClientTesting(),
      {provide: SYSTEM_ASSISTANT_WINDOW, useFactory: () => {
        const cache = new Map<string, string>();
        const events = new EventTarget();
        const parent = {postMessage(data: {type: string; nonce: string; contextId?: string}) {
          if (data.type !== 'TDW_SYSTEM_ASSISTANT_READY' && data.type !== 'TDW_SYSTEM_ASSISTANT_REQUEST') return;
          const reply = new MessageEvent('message', {origin: currentIngress.origin, data: {
            type: data.contextId ? 'TDW_SYSTEM_ASSISTANT_UPDATE' : 'TDW_SYSTEM_ASSISTANT_CONTEXT',
            protocolVersion: 1, nonce: data.nonce, contextId: data.contextId || 'opening_page', context: parentPage}});
          Object.defineProperty(reply, 'source', {value: parent}); events.dispatchEvent(reply);
        }};
        return Object.assign(events, {location: {hash: '', assign: vi.fn()}, confirm: vi.fn(), parent, setTimeout: vi.fn(), clearTimeout: vi.fn(), sessionStorage: {
          getItem: (key: string) => cache.get(key) ?? null,
          setItem: (key: string, value: string) => { cache.set(key, value); }, removeItem: (key: string) => { cache.delete(key); }
        }});
      }},
      {provide: ActivatedRoute, useValue: {snapshot: {data: {embeddedChat: false}}}}] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  const testProfile = {id: 'test_profile', name: 'Test CRM', urlPattern: '*', apiBindings: [], skillIds: []};
  function loadConfiguration({profiles = [testProfile], ...resources}: Partial<AssistantConfiguration> = {}) {
    if (TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat']) {
      http.expectOne('/api/system-assistant/configuration/profiles').flush(profiles.map(({id, name, urlPattern}) => ({id, name, urlPattern})));
      http.expectNone('/api/system-assistant/configuration');
      http.expectNone('/api/system-assistant/configuration/tools');
    } else {
      http.expectOne('/api/system-assistant/configuration').flush({apis: [], skills: [], authProfiles: [], profiles, ...resources});
      http.expectOne('/api/system-assistant/configuration/tools').flush([]);
    }
  }
  function ready(runs: LocalAnalysisRunListItemResponse[] = [], configuration: Partial<AssistantConfiguration> = {}) {
    const fixture = TestBed.createComponent(SystemAssistantPage); fixture.detectChanges();
    loadConfiguration(configuration);
    http.expectOne('/api/analysis/ai/options').flush({ defaultModel: 'test-model', defaultReasoningEffort: '', defaultReasoningEfforts: [], models: [] });
    http.expectOne('/api/auth/github/status').flush({ configured: true, settingsUrl: '/workspace-settings' });
    fixture.componentInstance.profileId.set('test_profile'); fixture.detectChanges();
    if (fixture.componentInstance.embedded) { http.expectOne('/api/analysis/runs').flush({runs}); fixture.detectChanges(); }
    return fixture;
  }
  function returningFromLogin(embedded: boolean) {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    Object.defineProperty(TestBed.inject(ActivatedRoute).snapshot, 'queryParamMap', {
      value: convertToParamMap({localRunId: 'saved-chat', profileId: 'crm_selected', auth: 'success'}), configurable: true});
    TestBed.inject(SYSTEM_ASSISTANT_WINDOW).sessionStorage.setItem(loginContextKey, JSON.stringify(savedLoginContext(embedded)));
    const fixture = TestBed.createComponent(SystemAssistantPage); fixture.detectChanges();
    return fixture;
  }
  it.each([false, true])('saves attachments before messages and blocks upload while busy (embedded=%s)', embedded => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    const fixture = ready(); const page = fixture.componentInstance;
    page.workspaceView.set('conversation'); page.historyOpen.set(false); fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input[type=file][aria-label="Wybierz załączniki"]') as HTMLInputElement;
    expect(input.accept.split(',')).toContain('.bmp');
    const first = new File(['one'], 'report.pdf'); const second = new File(['two'], 'picture.BMP');
    Object.defineProperty(input, 'files', {value: [first, second], configurable: true});
    page.attachFiles(input); fixture.detectChanges();
    expect(page.uploading()).toBe(true);
    expect(fixture.nativeElement.querySelector('.analysis-chat__form [role=status]').textContent).toContain('Zapisywanie');
    expect(fixture.nativeElement.querySelector('button[aria-label="Załącz pliki"]').disabled).toBe(true);
    http.expectOne('/api/system-assistant/conversations').flush({id: 'attachments', busy: false, pageContext: null, messages: []});
    const saved = {id: 'attachments', busy: false, pageContext: null, messages: [],
      attachments: [{id: 'file-1', name: first.name, mimeType: 'application/pdf', size: 3}]};
    const upload = http.expectOne(req => req.url.endsWith('/attachments/attachments'));
    expect(upload.request.body).toBe(first); upload.flush(saved);
    const next = http.expectOne(req => req.url.endsWith('/attachments/attachments'));
    expect(next.request.body).toBe(second);
    next.flush({...saved, attachments: [...saved.attachments, {id: 'file-2', name: second.name, mimeType: 'image/bmp', size: 3}]});
    fixture.detectChanges();
    expect(page.uploading()).toBe(false);
    expect(page.pendingAttachmentNames()).toEqual(['report.pdf', 'picture.BMP']);
    expect(fixture.nativeElement.querySelector('.analysis-chat__form .chat-attachments').textContent).toContain('report.pdf');
    http.expectNone('/api/system-assistant/conversations/attachments/messages');
    page.conversation.set({...saved, busy: true}); fixture.detectChanges();
    page.attachFiles(input);
    expect(fixture.nativeElement.querySelector('button[aria-label="Załącz pliki"]').disabled).toBe(true);
    http.expectNone(req => req.url.endsWith('/attachments/attachments'));
  });
  it('preserves the draft and unlocks the chat after an upload error', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    page.workspaceView.set('conversation'); page.historyOpen.set(false);
    page.conversation.set({id: 'saved', busy: false, pageContext: null, messages: []}); fixture.detectChanges();
    const chat = fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance as AnalysisFollowUpChatComponent;
    chat.messageText.set('Zbadaj plik');
    const input = fixture.nativeElement.querySelector('input[aria-label="Wybierz załączniki"]') as HTMLInputElement;
    Object.defineProperty(input, 'files', {value: [new File(['data'], 'report.pdf')]});
    page.attachFiles(input);
    http.expectOne(req => req.url.endsWith('/saved/attachments')).flush({message: 'Błąd zapisu'}, {status: 503, statusText: 'Unavailable'});
    http.expectOne('/api/system-assistant/conversations/saved').flush({id: 'saved', busy: false, pageContext: null, messages: [],
      attachments: [{id: 'accepted-file', name: 'report.pdf', mimeType: 'application/pdf', size: 4}]});
    expect(page.uploading()).toBe(false);
    expect(page.pendingAttachmentNames()).toEqual(['report.pdf']);
    expect(page.chatError()).toBeTruthy();
    expect(chat.messageText()).toBe('Zbadaj plik');
    http.expectNone('/api/system-assistant/conversations/saved/messages');
  });
  function loadLoginConfiguration() {
    loadConfiguration({profiles: [{...testProfile, id: 'crm_selected'}]});
    http.expectOne('/api/analysis/ai/options').flush({defaultModel: 'test-model', models: []});
  }
  it.each([false, true])('shows attachment names at their messages in the conversation snapshot (embedded=%s)', embedded => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    const fixture = ready(); const page = fixture.componentInstance;
    page.workspaceView.set('conversation'); page.historyOpen.set(false);
    if (embedded) page.ingress.reconnect(currentIngress, bridgeNonce);
    const saved = savedConversation();
    page.conversation.set({...saved, messages: [...saved.messages, ...saved.messages.map(message => ({...message, id: 'next-' + message.id}))],
      attachments: [
        {id: 'one', name: 'first.pdf', mimeType: 'application/pdf', size: 1, messageId: 'saved-0'},
        {id: 'two', name: 'second.png', mimeType: 'image/png', size: 2, messageId: 'next-saved-0'},
        {id: 'three', name: 'pending.docx', mimeType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', size: 3, messageId: null}
      ]});
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const users = root.querySelectorAll('.chat-message--user');
    expect(users[0].querySelector('.chat-attachments')?.textContent).toContain('first.pdf');
    expect(users[1].querySelector('.chat-attachments')?.textContent).toContain('second.png');
    expect(users[1].textContent).not.toContain('first.pdf');
    expect(root.querySelector('.analysis-chat__form .chat-attachments')?.textContent).toContain('pending.docx');
    expect(root.querySelector('.assistant-attachments')).toBeNull();
  });
  it.each([{existing: false, embedded: false}, {existing: true, embedded: false}, {existing: false, embedded: true}, {existing: true, embedded: true}])(
    'preserves rejected messages across OIDC without retrying (existing=$existing, embedded=$embedded)', ({existing, embedded}) => {
      const assign = vi.mocked(TestBed.inject(SYSTEM_ASSISTANT_WINDOW).location.assign);
      TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
      const fixture = ready(); const page = fixture.componentInstance;
      page.workspaceView.set('conversation'); page.historyOpen.set(false);
      page.model.set('original-model'); page.reasoningEffort.set('high');
      if (embedded) page.ingress.reconnect(currentIngress, bridgeNonce);
      if (existing) page.conversation.set(savedConversation());
      fixture.detectChanges();
      const chat = fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance as AnalysisFollowUpChatComponent;
      chat.messageText.set('Sprawdź klienta'); fixture.detectChanges();
      fixture.nativeElement.querySelector('.analysis-chat__form').dispatchEvent(new Event('submit', {cancelable: true}));
      if (!existing) http.expectOne('/api/system-assistant/conversations').flush({id: 'saved-chat', busy: false, messages: [], pageContext: null});
      http.expectOne('/api/system-assistant/conversations/saved-chat/messages').flush(
        {code: 'AUTH_LOGIN_REQUIRED', authProfileId: 'crm_oidc', message: 'Zaloguj'}, {status: 409, statusText: 'Conflict'});
      const login = http.expectOne('/api/system-assistant/auth/crm_oidc/login');
      expect(login.request.body).toEqual({conversationId: 'saved-chat', profileId: 'test_profile', returnPath: embedded ? '/system-assistant/chat' : '/system-assistant'});
      expect(page.unsentMessage()).toBe(''); expect(chat.messageText()).toBe(loginMessage);
      const cached = TestBed.inject(SYSTEM_ASSISTANT_WINDOW).sessionStorage.getItem(loginContextKey);
      expect(JSON.parse(cached!)).toEqual({conversationId: 'saved-chat', profileId: 'test_profile',
        returnPath: embedded ? '/system-assistant/chat' : '/system-assistant', expiresAt: expect.any(Number),
        context: embedded ? currentIngress : null, nonce: embedded ? bridgeNonce : null, message: loginMessage, model: 'original-model', reasoningEffort: 'high'});
      expect(JSON.parse(cached!).expiresAt).toBeGreaterThan(Date.now());
      expect(JSON.parse(cached!).expiresAt).toBeLessThanOrEqual(Date.now() + 300_000);
      expect(cached).not.toContain('Poprzednia odpowiedź');
      expect(login.request.body).not.toHaveProperty('message');
      http.expectNone('/api/system-assistant/conversations/saved-chat');
      login.flush({authorizationUrl: 'https://sso.example.com/authorize?state=opaque'});
      expect(assign).toHaveBeenCalledExactlyOnceWith('https://sso.example.com/authorize?state=opaque');
      page.send('Nie ponawiaj'); http.expectNone('/api/system-assistant/conversations/saved-chat/messages');
      fixture.destroy();
    });
  it.each(['stay', 'leave', 'edited during login', 'edits reverted', 'redirect failed'] as const)(
    'guards a conversation login with unsaved configuration (%s)', outcome => {
      const fixture = ready(); const page = fixture.componentInstance; const editor = configurationEditor(fixture);
      const browser = TestBed.inject(SYSTEM_ASSISTANT_WINDOW);
      const confirm = vi.mocked(browser.confirm).mockReturnValueOnce(outcome !== 'stay').mockReturnValue(false);
      const canUnload = () => browser.dispatchEvent(new Event('beforeunload', {cancelable: true}));
      editor.updateProfile(0, 'name', 'Szkic'); page.workspaceView.set('conversation');
      page.conversation.set(savedConversation()); fixture.detectChanges();
      page.send(loginMessage);
      http.expectOne('/api/system-assistant/conversations/saved-chat/messages').flush(
        {code: 'AUTH_LOGIN_REQUIRED', authProfileId: 'crm_oidc'}, {status: 409, statusText: 'Conflict'});
      expect(confirm).toHaveBeenCalledExactlyOnceWith('Opuścić System Assistant i odrzucić niezapisane zmiany konfiguracji?');
      const loginUrl = '/api/system-assistant/auth/crm_oidc/login';
      if (outcome === 'stay') http.expectNone(loginUrl);
      else {
        expect(canUnload()).toBe(false);
        if (outcome === 'edited during login') editor.addSkill();
        if (outcome === 'edits reverted') { editor.addSkill(); editor.remove('skills', 0); }
        if (outcome === 'redirect failed') vi.mocked(browser.location.assign).mockImplementation(() => { throw new Error(); });
        http.expectOne(loginUrl).flush({authorizationUrl: 'https://sso.example.com/authorize'});
      }
      if (outcome === 'leave' || outcome === 'edits reverted') {
        expect(browser.location.assign).toHaveBeenCalledOnce();
        expect(confirm).toHaveBeenCalledTimes(1); expect(canUnload()).toBe(true);
      } else {
        expect(confirm).toHaveBeenCalledTimes(outcome === 'edited during login' ? 2 : 1);
        expect(browser.location.assign).toHaveBeenCalledTimes(outcome === 'redirect failed' ? 1 : 0);
        expect(browser.sessionStorage.getItem(loginContextKey)).toBeNull();
        expect(page.busy()).toBe(false);
        expect(fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance.messageText()).toBe(loginMessage);
      }
      expect(canUnload()).toBe(false); expect(page.dirty()).toBe(true); fixture.destroy();
    });
  it.each([false, true])('keeps the message in the editor after a failed login start and clears the cache (embedded=%s)', embedded => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    const fixture = ready(); const page = fixture.componentInstance;
    page.historyOpen.set(false); if (embedded) page.ingress.reconnect(currentIngress, bridgeNonce);
    page.conversation.set(savedConversation()); page.send('Sprawdź klienta');
    http.expectOne('/api/system-assistant/conversations/saved-chat/messages').flush(
      {code: 'AUTH_LOGIN_REQUIRED', authProfileId: 'crm_oidc'}, {status: 409, statusText: 'Conflict'});
    http.expectOne('/api/system-assistant/auth/crm_oidc/login').flush({message: 'Dostawca niedostępny'}, {status: 400, statusText: 'Bad Request'});
    expect(page.submitting()).toBe(false); expect(page.chatError()).toBe('Dostawca niedostępny');
    expect(page.unsentMessage()).toBe(''); expect(page.authBusyId()).toBe('');
    expect(fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance.messageText()).toBe(loginMessage);
    expect(TestBed.inject(SYSTEM_ASSISTANT_WINDOW).sessionStorage.getItem(loginContextKey)).toBeNull(); fixture.destroy();
  });
  it.each([false, true].flatMap(embedded => ['success', 'failed'].flatMap(auth => [false, true].map(configFirst => ({embedded, auth, configFirst})))))(
    'resumes exactly once after successful OIDC and retains failed login drafts (auth=$auth, embedded=$embedded, configFirst=$configFirst)', ({auth, embedded, configFirst}) => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    Object.defineProperty(TestBed.inject(ActivatedRoute).snapshot, 'queryParamMap', {
      value: convertToParamMap({localRunId: 'saved-chat', profileId: 'crm_selected', auth}), configurable: true});
    const storage = TestBed.inject(SYSTEM_ASSISTANT_WINDOW).sessionStorage;
    storage.setItem(loginContextKey, JSON.stringify(savedLoginContext(embedded)));
    const fixture = TestBed.createComponent(SystemAssistantPage); fixture.detectChanges();
    const page = fixture.componentInstance;
    expect(page.profileId()).toBe('crm_selected');
    if (!configFirst) http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation());
    loadConfiguration({profiles: [{...testProfile, id: 'crm_selected', urlPattern: 'https://crm.example/tickets/*'}]});
    http.expectOne('/api/analysis/ai/options').flush({defaultModel: 'test-model', models: []});
    http.expectOne('/api/auth/github/status').flush({configured: true});
    if (configFirst) {
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('textarea').value).toBe(loginMessage);
      expect(page.canChat()).toBe(false);
      http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation());
    }
    fixture.detectChanges();
    if (auth === 'success') {
      const resumed = http.expectOne('/api/system-assistant/conversations/saved-chat/messages');
      expect(resumed.request.body).toEqual({message: loginMessage, profileId: 'crm_selected', model: 'original-model', reasoningEffort: 'high', ...(embedded ? {pageContext: currentIngress} : {})});
      expect(page.busy()).toBe(true);
      resumed.flush(savedConversation()); fixture.detectChanges();
    }
    expect(page.workspaceView()).toBe('conversation'); expect(page.selectedProfileId()).toBe('crm_selected');
    expect(page.messages()).toHaveLength(2); expect(page.unsentMessage()).toBe('');
    expect(page.historyOpen()).toBe(false); expect(page.canChat()).toBe(true);
    expect(page.pageContext()).toEqual(embedded ? currentIngress : savedConversation().pageContext);
    const chat = fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance as AnalysisFollowUpChatComponent;
    expect(chat.messageText()).toBe(auth === 'success' ? '' : loginMessage);
    expect(fixture.nativeElement.querySelector('textarea').value).toBe(auth === 'success' ? '' : loginMessage);
    expect(page.loginNotice()).not.toContain('Przywrócono');
    if (auth === 'success') expect(page.loginNotice()).toBe('');
    expect(storage.getItem(loginContextKey)).toBeNull();
    chat.messageText.set('Poprawiona wiadomość'); fixture.detectChanges();
    expect(chat.messageText()).toBe('Poprawiona wiadomość');
    if (embedded) {
      expect(page.ingress.context()).toEqual(currentIngress); expect(storage.getItem(loginContextKey)).toBeNull();
      expect(fixture.nativeElement.querySelector('app-analysis-feature-aside')).toBeNull();
    }
    http.expectNone(request => request.method === 'POST'); fixture.destroy();
  });
  it.each([false, true])('waits for PAT readiness before resuming the rejected message (embedded=%s)', embedded => {
    const fixture = returningFromLogin(embedded);
    http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation());
    loadLoginConfiguration(); fixture.detectChanges();
    http.expectNone(request => request.method === 'POST');
    http.expectOne('/api/auth/github/status').flush({configured: true}); fixture.detectChanges();
    http.expectOne('/api/system-assistant/conversations/saved-chat/messages').flush(savedConversation());
    fixture.detectChanges(); fixture.detectChanges();
    http.expectNone(request => request.method === 'POST'); fixture.destroy();
  });

  it.each([false, true])('stops a second login requirement instead of looping (embedded=%s)', embedded => {
    const fixture = returningFromLogin(embedded);
    http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation());
    loadLoginConfiguration(); http.expectOne('/api/auth/github/status').flush({configured: true}); fixture.detectChanges();
    http.expectOne('/api/system-assistant/conversations/saved-chat/messages').flush(
      {code: 'AUTH_LOGIN_REQUIRED', authProfileId: 'crm_oidc'}, {status: 409, statusText: 'Conflict'});
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('textarea').value).toBe(loginMessage);
    expect(fixture.componentInstance.busy()).toBe(false);
    expect(fixture.componentInstance.chatError()).toContain('po zalogowaniu');
    expect(TestBed.inject(SYSTEM_ASSISTANT_WINDOW).sessionStorage.getItem(loginContextKey)).toBeNull();
    expect(TestBed.inject(SYSTEM_ASSISTANT_WINDOW).location.assign).not.toHaveBeenCalled();
    http.expectNone(request => request.method === 'POST'); fixture.destroy();
  });

  it.each([false, true])('reconciles a failed resumed POST without another send (embedded=%s)', embedded => {
    const fixture = returningFromLogin(embedded);
    http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation());
    loadLoginConfiguration(); http.expectOne('/api/auth/github/status').flush({configured: true}); fixture.detectChanges();
    http.expectOne('/api/system-assistant/conversations/saved-chat/messages').flush({}, {status: 503, statusText: 'Unavailable'});
    http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation()); fixture.detectChanges();
    expect(fixture.componentInstance.unsentMessage()).toBe(loginMessage);
    http.expectNone(request => request.method === 'POST'); fixture.destroy();
  });

  it.each([false, true].flatMap(embedded => ['busy', 'profile_changed', 'pat_missing', 'load_failed'].map(blocker => ({embedded, blocker}))))(
    'retains the draft without deferred sending after $blocker (embedded=$embedded)', ({embedded, blocker}) => {
    const fixture = returningFromLogin(embedded); const page = fixture.componentInstance;
    const load = http.expectOne('/api/system-assistant/conversations/saved-chat');
    if (blocker === 'load_failed') load.flush({}, {status: 503, statusText: 'Unavailable'});
    else load.flush(savedConversation());
    loadLoginConfiguration(); http.expectOne('/api/auth/github/status').flush({configured: blocker !== 'pat_missing'});
    if (blocker === 'busy') page.conversation.update(value => ({...value!, busy: true}));
    if (blocker === 'profile_changed') page.profileId.set('other');
    fixture.detectChanges(); expect(fixture.nativeElement.querySelector('textarea').value).toBe(loginMessage);
    http.expectNone(request => request.method === 'POST');
    page.conversation.set(savedConversation()); page.restoreError.set(false); page.authReady.set(true); page.profileId.set('crm_selected');
    fixture.detectChanges(); http.expectNone(request => request.method === 'POST'); fixture.destroy();
  });

  it.each([false, true])('cancels the queued resume when the operator sends another message (embedded=%s)', embedded => {
    const fixture = returningFromLogin(embedded);
    http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation());
    loadLoginConfiguration(); http.expectOne('/api/auth/github/status').flush({configured: true});
    fixture.componentInstance.send('Inne polecenie');
    const sent = http.expectOne('/api/system-assistant/conversations/saved-chat/messages');
    expect(sent.request.body.message).toBe('Inne polecenie'); sent.flush(savedConversation()); fixture.detectChanges();
    http.expectNone(request => request.method === 'POST'); fixture.destroy();
  });

  it.each([false, true])('consumes a login message only once, including a panel without ingress (embedded=%s)', embedded => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    Object.defineProperty(TestBed.inject(ActivatedRoute).snapshot, 'queryParamMap', {
      value: convertToParamMap({localRunId: 'saved-chat', profileId: 'crm_selected', auth: 'success'}), configurable: true});
    const storage = TestBed.inject(SYSTEM_ASSISTANT_WINDOW).sessionStorage;
    storage.setItem(loginContextKey, JSON.stringify({...savedLoginContext(embedded), context: null, nonce: null}));
    const openReturn = () => {
      const fixture = TestBed.createComponent(SystemAssistantPage); fixture.detectChanges();
      http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation());
      loadConfiguration({profiles: [{...testProfile, id: 'crm_selected'}]});
      http.expectOne('/api/analysis/ai/options').flush({defaultModel: 'test-model', models: []});
      http.expectOne('/api/auth/github/status').flush({configured: true}); fixture.detectChanges();
      return fixture;
    };
    const first = openReturn();
    http.expectOne('/api/system-assistant/conversations/saved-chat/messages').flush(savedConversation()); first.detectChanges();
    expect(first.nativeElement.querySelector('textarea').value).toBe('');
    expect(first.componentInstance.canChat()).toBe(true); expect(storage.getItem(loginContextKey)).toBeNull(); first.destroy();
    const reloaded = openReturn(); expect(reloaded.nativeElement.querySelector('textarea').value).toBe('');
    expect(reloaded.componentInstance.canChat()).toBe(true);
    http.expectNone(request => request.method === 'POST'); reloaded.destroy();
  });

  it.each([false, true].flatMap(embedded => ['expired', 'future_expiry', 'wrong_chat', 'wrong_profile', 'wrong_view', 'extra_context', 'invalid_message', 'empty_message', 'invalid_model', 'invalid_effort', 'extra_field', 'manual_shape', 'old_shape', 'broken'].map(kind => ({embedded, kind}))))(
    'rejects $kind login context without restoring its message (embedded=$embedded)', ({embedded, kind}) => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    Object.defineProperty(TestBed.inject(ActivatedRoute).snapshot, 'queryParamMap', {
      value: convertToParamMap({localRunId: 'saved-chat', profileId: 'crm_selected', auth: 'success'}), configurable: true});
    const cached: Record<string, unknown> = {...savedLoginContext(embedded),
      conversationId: kind === 'wrong_chat' ? 'other-chat' : 'saved-chat',
      profileId: kind === 'wrong_profile' ? 'other-profile' : 'crm_selected',
      returnPath: kind === 'wrong_view' ? embedded ? '/system-assistant' : '/system-assistant/chat' : savedLoginContext(embedded).returnPath,
      expiresAt: kind === 'expired' ? 0 : kind === 'future_expiry' ? Date.now() + 600_000 : Date.now() + 60_000,
      context: kind === 'extra_context' ? {...currentIngress, cookies: 'not-allowed'} : embedded ? currentIngress : null,
      message: kind === 'invalid_message' ? 42 : kind === 'empty_message' ? '   ' : loginMessage};
    if (kind === 'extra_field') cached['extra'] = 'invalid';
    if (kind === 'invalid_model') cached['model'] = 42;
    if (kind === 'invalid_effort') cached['reasoningEffort'] = null;
    if (kind === 'manual_shape' || kind === 'old_shape') { delete cached['model']; delete cached['reasoningEffort']; }
    if (kind === 'old_shape') { delete cached['message']; delete cached['returnPath']; }
    TestBed.inject(SYSTEM_ASSISTANT_WINDOW).sessionStorage.setItem(loginContextKey, kind === 'broken' ? '{' : JSON.stringify(cached));
    const fixture = TestBed.createComponent(SystemAssistantPage); fixture.detectChanges(); const page = fixture.componentInstance;
    http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation());
    loadConfiguration({profiles: [{...testProfile, id: 'crm_selected', urlPattern: 'https://crm.example/customers/*'}]});
    http.expectOne('/api/analysis/ai/options').flush({defaultModel: 'test-model', models: []});
    http.expectOne('/api/auth/github/status').flush({configured: true}); fixture.detectChanges();
    expect(page.conversation()?.id).toBe('saved-chat'); expect(page.historyOpen()).toBe(false);
    expect(page.ingress.context()).toBeNull(); expect(page.selectedProfileId()).toBe(embedded ? '' : 'crm_selected');
    expect(page.canChat()).toBe(!embedded); expect(fixture.nativeElement.querySelector('textarea').value).toBe('');
    expect(TestBed.inject(SYSTEM_ASSISTANT_WINDOW).sessionStorage.getItem(loginContextKey)).toBeNull();
    http.expectNone(request => request.method === 'POST'); fixture.destroy();
  });
  it.each([false, true])('keeps the message and does not start login when storage is blocked (embedded=%s)', embedded => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    const browser = TestBed.inject(SYSTEM_ASSISTANT_WINDOW);
    vi.spyOn(browser.sessionStorage, 'setItem').mockImplementation(() => { throw new DOMException('Blocked', 'SecurityError'); });
    const fixture = ready(); const page = fixture.componentInstance;
    page.historyOpen.set(false); page.conversation.set(savedConversation()); if (embedded) page.ingress.reconnect(currentIngress, bridgeNonce);
    page.send('Sprawdź klienta');
    http.expectOne('/api/system-assistant/conversations/saved-chat/messages').flush(
      {code: 'AUTH_LOGIN_REQUIRED', authProfileId: 'crm_oidc'}, {status: 409, statusText: 'Conflict'});
    http.expectNone('/api/system-assistant/auth/crm_oidc/login'); expect(browser.location.assign).not.toHaveBeenCalled();
    expect(page.submitting()).toBe(false); expect(page.unsentMessage()).toBe('');
    expect(fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance.messageText()).toBe(loginMessage);
    expect(page.chatError()).toContain('Przeglądarka blokuje'); fixture.destroy();
  });
  it('assigns independent API authentication in two profiles through the shared select', async () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    const auth = {...newAuthProfile('BASIC', 'crm_auth', 'CRM access', 'http://localhost'), username: 'operator', password: 'public-test-password'};
    editor.configuration.update(value => ({...value, authProfiles: [auth], apis: [{id: 'crm', name: 'CRM', yaml: 'yaml'}],
      profiles: [testProfile, {...testProfile, id: 'other', name: 'Other CRM', apiBindings: [{apiId: 'crm', authProfileId: null}]}]}));
    editor.toggleProfileApi(0, 'crm', true); page.workspaceView.set('profiles'); fixture.detectChanges();
    fixture.nativeElement.querySelector('.assistant-profile .assistant-definition-toggle').click(); fixture.detectChanges();
    expect(editor.configuration().profiles[0].apiBindings).toEqual([{apiId: 'crm', authProfileId: null}]);
    const select = await choice(fixture, '.assistant-profile:first-child .assistant-profile-api mat-select');
    expect(await select.getValueText()).toBe('No Auth');
    await select.open(); await select.clickOptions({text: /CRM access/});
    expect(editor.configuration().profiles[0].apiBindings[0].authProfileId).toBe('crm_auth');
    expect(editor.configuration().profiles[1].apiBindings[0].authProfileId).toBeNull();
    expect(page.dirty()).toBe(true); http.expectNone('/api/system-assistant/configuration');
    editor.save(); const save = http.expectOne('/api/system-assistant/configuration');
    expect(save.request.body.authProfiles[0].password).toBe('public-test-password');
    expect(save.request.body.apis[0]).not.toHaveProperty('authProfileId');
    expect(save.request.body.profiles[0].apiBindings[0].authProfileId).toBe('crm_auth'); save.flush(editor.configuration());
    http.expectOne('/api/system-assistant/configuration/tools').flush([]); fixture.destroy();
  });
  it('keeps incomplete Auth in the draft and shows field errors before saving', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    editor.addAuth(); editor.save(); fixture.detectChanges();
    expect(page.workspaceView()).toBe('auth'); expect(page.dirty()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Podaj login.');
    http.expectNone('/api/system-assistant/configuration'); fixture.destroy();
  });
  it('keeps edits made while a manual OIDC login is starting', () => {
    const profile = newAuthProfile('OIDC', 'crm_oidc', 'CRM login', 'http://localhost');
    const fixture = ready([], {authProfiles: [profile]}); const editor = configurationEditor(fixture);
    http.expectOne('/api/system-assistant/auth/status').flush({});
    editor.loginAuth(profile.id); editor.addSkill();
    http.expectOne('/api/system-assistant/auth/crm_oidc/login').flush({authorizationUrl: 'https://sso.example.com/authorize'});
    expect(TestBed.inject(SYSTEM_ASSISTANT_WINDOW).location.assign).not.toHaveBeenCalled();
    expect(editor.dirty()).toBe(true); expect(editor.authBusyId()).toBe('');
    expect(editor.saveError()).toContain('Zapisz lub cofnij zmiany'); fixture.destroy();
  });
  it('ignores a stale OIDC status response after the configured profiles change', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    const profile = newAuthProfile('OIDC', 'crm_oidc', 'CRM login', 'http://localhost');
    editor.configuration.update(value => ({...value, authProfiles: [profile]})); editor.loadAuthStatus();
    const pending = http.expectOne('/api/system-assistant/auth/status');
    editor.configuration.update(value => ({...value, authProfiles: []})); editor.loadAuthStatus();
    pending.flush({crm_oidc: {connected: true, username: 'crm.operator', expiresAt: null, message: 'Połączono'}});
    expect(editor.authConnections()).toEqual({}); fixture.destroy();
  });
  it('keeps saved profiles separate from the draft and explains removed resources', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    page.workspaceView.set('profiles'); editor.addSkill(); editor.addProfile();
    editor.toggleProfileResource(0, 'skillIds', editor.configuration().skills[0].id, true);
    expect(page.activeProfiles()).toEqual([testProfile]);
    editor.remove('skills', 0); fixture.detectChanges();
    const warning = fixture.nativeElement.querySelector('[aria-label="Brakujące zasoby profilu"]');
    expect(warning).not.toBeNull(); expect(editor.profileWarning(editor.configuration().profiles[0])).toContain('Brak skilli:');
    editor.save(); http.expectOne('/api/system-assistant/configuration').flush(editor.configuration());
    http.expectOne('/api/system-assistant/configuration/tools').flush([]);
    expect(page.activeProfiles()).toEqual(editor.configuration().profiles);
    expect(page.canChat()).toBe(true); fixture.destroy();
  });
  it('checks the parent again at send, preserves a blocked draft and sends the full URL on the same conversation', () => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = true;
    const fixture = ready(); const page = fixture.componentInstance;
    page.historyOpen.set(false); page.conversation.set(savedConversation());
    page.ingress.reconnect(currentIngress, bridgeNonce);
    page.activeProfiles.set([testProfile, {...testProfile, id: 'tickets', urlPattern: 'https://crm.example/tickets/*'}]);
    page.profileId.set('tickets'); fixture.detectChanges();
    parentPage = {...currentIngress, pathname: '/cases/3', url: 'https://crm.example/cases/3?filter=a%2Fb#details'};
    page.send('Sprawdź ten ekran'); fixture.detectChanges();
    expect(page.canChat()).toBe(false); expect(page.profileId()).toBe('');
    expect(page.profileOptions().map(option => option.value)).toEqual(['test_profile']);
    expect(fixture.nativeElement.querySelector('textarea').value).toBe('Sprawdź ten ekran');
    http.expectNone(req => req.url.endsWith('/messages'));
    page.profileId.set('test_profile'); page.send('Sprawdź ten ekran');
    const turn = http.expectOne('/api/system-assistant/conversations/saved-chat/messages');
    expect(turn.request.body.pageContext).toEqual(parentPage);
    expect(page.pageUrl()).toBe(parentPage.url); turn.flush(savedConversation()); fixture.destroy();
  });
  it('keeps the original OIDC message for manual send if only the query or fragment changed', () => {
    parentPage = {...currentIngress, url: currentIngress.url + '-another'};
    const fixture = returningFromLogin(true);
    http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation());
    loadLoginConfiguration(); http.expectOne('/api/auth/github/status').flush({configured: true}); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('textarea').value).toBe(loginMessage);
    expect(fixture.componentInstance.pageUrl()).toBe(parentPage.url); expect(fixture.componentInstance.busy()).toBe(false);
    http.expectNone(req => req.url.endsWith('/messages')); fixture.destroy();
  });
  it('filters embedded profiles using live context rather than the restored conversation and keeps all profiles on the full page', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const profiles = [testProfile, {...testProfile, id: 'matched', urlPattern: 'https://crm.example/cases/*'},
      {...testProfile, id: 'hidden', urlPattern: 'https://other.example/*'}];
    page.activeProfiles.set(profiles); expect(page.availableProfiles()).toHaveLength(3); fixture.destroy();
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = true;
    const embedded = ready(); const panel = embedded.componentInstance;
    panel.activeProfiles.set(profiles); panel.conversation.set(savedConversation());
    expect(panel.availableProfiles().map(item => item.id)).toEqual(['test_profile']);
    panel.ingress.context.set({url: 'https://crm.example/cases/C-100', origin: 'https://crm.example', pathname: '/cases/C-100', title: 'CRM', capturedAt: ''});
    expect(panel.availableProfiles().map(item => item.id)).toEqual(['test_profile', 'matched']);
    expect(panel.pageContext()?.pathname).toBe('/cases/C-100'); embedded.destroy();
  });
  it.each([false, true])('refreshes only profile choices in the conversation (embedded=%s)', embedded => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    const fixture = ready();
    const page = fixture.componentInstance;
    page.refreshProfiles();
    http.expectNone('/api/system-assistant/configuration');
    http.expectNone('/api/system-assistant/configuration/tools');
    const profiles = [{id: 'updated', name: 'Zapisany profil', urlPattern: '*'}];
    http.expectOne('/api/system-assistant/configuration/profiles').flush(profiles);
    expect(page.activeProfiles()).toEqual(profiles);
  });
  it('requires an explicit profile and sends its ID without resetting the existing conversation', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    page.profileId.set(''); page.send('Bez wyboru'); http.expectNone('/api/system-assistant/conversations');
    const saved = savedConversation(); page.conversation.set(saved);
    page.activeProfiles.set([testProfile, {...testProfile, id: 'other', name: 'Other'}]);
    page.profileId.set('other'); page.send('Dalej');
    const request = http.expectOne('/api/system-assistant/conversations/saved-chat/messages');
    expect(request.request.body.profileId).toBe('other');
    request.flush(saved); expect(page.pageContext()).toEqual(saved.pageContext);
    page.activeProfiles.set([]); expect(page.canChat()).toBe(false); fixture.destroy();
  });
  it.each([false, true])('continues a saved conversation and preserves drafts when switching (embedded=%s)', embedded => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    const fixture = ready(); const page = fixture.componentInstance;
    page.workspaceView.set('conversation');
    const chat = fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance as AnalysisFollowUpChatComponent;
    page.newConversation(); fixture.detectChanges();
    chat.messageText.set('Szkic nowej rozmowy'); page.openConversationHistory(); fixture.detectChanges();
    http.expectOne('/api/analysis/runs').flush({runs: []});
    page.continueConversation('saved-chat'); expect(page.restoring()).toBe(true);
    http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation()); fixture.detectChanges();
    expect(page.historyOpen()).toBe(false); expect(page.pageContext()).toEqual(embedded ? null : savedConversation().pageContext);
    expect(fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance).toBe(chat);
    expect(chat.messageText()).toBe(''); chat.messageText.set('Szkic kontynuacji');
    page.newConversation(); fixture.detectChanges(); expect(chat.messageText()).toBe('Szkic nowej rozmowy');
    page.openConversationHistory(); fixture.detectChanges(); http.expectOne('/api/analysis/runs').flush({runs: []});
    page.continueConversation('saved-chat'); http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation()); fixture.detectChanges();
    expect(chat.messageText()).toBe('Szkic kontynuacji');
    expect(fixture.nativeElement.querySelector('app-analysis-feature-aside') !== null).toBe(!embedded);
    http.expectNone(request => request.method === 'DELETE'); fixture.destroy();
  });
  it.each([false, true])('keeps the current conversation after a failed choice and ignores late reconciliation (embedded=%s)', embedded => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    const fixture = ready(); const page = fixture.componentInstance;
    page.conversation.set(savedConversation()); page.historyOpen.set(false); fixture.detectChanges(); page.send('Nieustalony wynik');
    http.expectOne('/api/system-assistant/conversations/saved-chat/messages').flush({}, {status: 503, statusText: 'Unavailable'});
    const reconcile = http.expectOne('/api/system-assistant/conversations/saved-chat');
    page.openConversationHistory(); fixture.detectChanges(); http.expectOne('/api/analysis/runs').flush({runs: []});
    page.continueConversation('missing'); http.expectOne('/api/system-assistant/conversations/missing').flush({message: 'Usunięta rozmowa'}, {status: 404, statusText: 'Not found'});
    expect(page.historyOpen()).toBe(true); expect(page.historyError()).not.toBe(''); expect(page.conversation()?.id).toBe('saved-chat');
    page.continueConversation('other'); http.expectOne('/api/system-assistant/conversations/other').flush({...savedConversation(), id: 'other'});
    expect(page.conversation()?.id).toBe('other');
    page.continueConversation('saved-chat'); http.expectOne('/api/system-assistant/conversations/saved-chat').flush(savedConversation());
    reconcile.flush({...savedConversation(), messages: []}); expect(page.messages()).toHaveLength(2);
    page.conversation.set({...savedConversation(), id: 'other', busy: true}); page.openConversationHistory();
    expect(page.historyOpen()).toBe(false); fixture.destroy();
  });

  it.each([false, true])('opens history from the composer and returns without losing the current draft (embedded=%s)', embedded => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    const fixture = ready(); const page = fixture.componentInstance;
    page.workspaceView.set('conversation'); page.historyOpen.set(false);
    page.conversation.set(savedConversation()); fixture.detectChanges();
    const chat = fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance as AnalysisFollowUpChatComponent;
    chat.messageText.set('Niedokończone pytanie'); page.chatError.set('Błąd poprzedniej próby'); page.unsentMessage.set('Bez potwierdzenia');
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const history = root.querySelector<HTMLButtonElement>('.analysis-chat__actions [aria-label="Historia rozmów"]')!;
    expect(history).not.toBeNull(); history.click(); fixture.detectChanges();
    http.expectOne('/api/analysis/runs').flush({runs: []}); fixture.detectChanges();
    expect(root.querySelector<HTMLElement>('.assistant-chat')?.hidden).toBe(true);
    expect(root.querySelector<HTMLElement>('.assistant-history')?.hidden).toBe(false);
    expect(root.querySelector('app-analysis-feature-aside')).toBeNull();
    http.expectNone(request => request.method === 'POST' || request.method === 'PUT' || request.method === 'DELETE');
    root.querySelector<HTMLButtonElement>('[aria-label="Wróć do rozmowy"]')!.click(); fixture.detectChanges();
    expect(page.historyOpen()).toBe(false); expect(root.querySelector<HTMLElement>('.assistant-chat')?.hidden).toBe(false);
    expect(fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance).toBe(chat);
    expect(chat.messageText()).toBe('Niedokończone pytanie');
    expect(page.chatError()).toBe('Błąd poprzedniej próby'); expect(page.unsentMessage()).toBe('Bez potwierdzenia');
    page.conversation.update(value => ({...value!, busy: true})); fixture.detectChanges();
    expect(history.disabled).toBe(true); history.click(); expect(page.historyOpen()).toBe(false);
    fixture.destroy();
  });

  it('keeps history inside the conversation tab and preserves its search across configuration tabs', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    page.workspaceView.set('conversation'); page.openConversationHistory(); fixture.detectChanges();
    http.expectOne('/api/analysis/runs').flush({runs: []}); fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const search = root.querySelector<HTMLInputElement>('[aria-label="Szukaj rozmowy"]')!;
    search.value = 'CRM'; search.dispatchEvent(new Event('input', {bubbles: true}));
    page.workspaceView.set('skills'); fixture.detectChanges();
    expect(root.querySelector<HTMLElement>('.assistant-history')?.hidden).toBe(true);
    page.workspaceView.set('conversation'); fixture.detectChanges();
    expect(root.querySelector<HTMLElement>('.assistant-history')?.hidden).toBe(false);
    expect(root.querySelector<HTMLInputElement>('[aria-label="Szukaj rozmowy"]')?.value).toBe('CRM');
    http.expectNone('/api/analysis/runs'); fixture.destroy();
  });
  it('opens a history conversation directly in chat and continues the existing ID with its original context', () => {
    Object.defineProperty(TestBed.inject(ActivatedRoute).snapshot, 'queryParamMap', {value: convertToParamMap({localRunId: 'saved-chat'}), configurable: true});
    const fixture = ready(); const page = fixture.componentInstance;
    expect(page.workspaceView()).toBe('conversation'); expect(page.canChat()).toBe(false);
    const saved = savedConversation();
    http.expectOne('/api/system-assistant/conversations/saved-chat').flush(saved); fixture.detectChanges();
    expect(page.canChat()).toBe(true); expect(page.messages()).toHaveLength(2); expect(page.pageContext()).toEqual(saved.pageContext);
    expect(fixture.nativeElement.textContent).toContain('Poprzednia odpowiedź');
    page.send('Kontynuacja');
    const request = http.expectOne('/api/system-assistant/conversations/saved-chat/messages');
    expect(request.request.body).toEqual({message: 'Kontynuacja', profileId: 'test_profile'}); request.flush(saved);
    http.expectNone('/api/system-assistant/conversations');
    page.newConversation(); fixture.detectChanges();
    expect(page.conversation()).toBeNull(); expect(page.messages()).toEqual([]);
    http.expectNone(request => request.method === 'DELETE'); fixture.destroy();
  });
  it('blocks sending after a failed history load until retry or an explicit new conversation', () => {
    Object.defineProperty(TestBed.inject(ActivatedRoute).snapshot, 'queryParamMap', {value: convertToParamMap({localRunId: 'deleted'}), configurable: true});
    const fixture = ready(); const page = fixture.componentInstance;
    http.expectOne('/api/system-assistant/conversations/deleted').flush({message: 'Rozmowa usunięta'}, {status: 404, statusText: 'Not found'});
    fixture.detectChanges(); expect(page.canChat()).toBe(false); page.send('Nie twórz zastępczej rozmowy');
    http.expectNone('/api/system-assistant/conversations');
    expect(fixture.nativeElement.querySelector('[aria-label="Ponów wczytanie rozmowy"]')).not.toBeNull();
    page.newConversation(); expect(page.restoreError()).toBe(false); expect(page.canChat()).toBe(true); fixture.destroy();
  });
  it.each([true, false])('shows the standard AI/cost aside only outside the embedded panel (embedded=%s)', embedded => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = embedded;
    const fixture = ready(); fixture.componentInstance.conversation.set(savedConversation());
    fixture.componentInstance.workspaceView.set('conversation'); fixture.componentInstance.historyOpen.set(false); fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('app-analysis-feature-aside') !== null).toBe(!embedded);
    expect(root.querySelector('.analysis-chat__actions app-analysis-feature-aside')).toBeNull();
    if (!embedded) expect(root.querySelector('app-analysis-feature-aside')?.classList.contains('is-inline')).toBe(false);
    fixture.destroy();
  });
  it('keeps recovery in the bottom action row and restores an unconfirmed draft without resending', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    page.workspaceView.set('conversation'); page.unsentMessage.set('Odczytaj klienta'); page.chatError.set('Nie potwierdzono przyjęcia.');
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.analysis-chat__actions app-ai-execution-controls')).not.toBeNull();
    expect(root.querySelector('.analysis-chat__form .form-error')?.textContent).toContain('Nie potwierdzono');
    root.querySelector<HTMLButtonElement>('.analysis-chat__actions [aria-label="Przywróć do pola wiadomości"]')!.click(); fixture.detectChanges();
    expect(root.querySelector('textarea')!.value).toBe('Odczytaj klienta');
    expect(page.unsentMessage()).toBe(''); http.expectNone('/api/system-assistant/conversations'); fixture.destroy();
  });
  it('embedded route renders the existing chat and sends through the same backend API', () => {
    TestBed.inject(ActivatedRoute).snapshot.data['embeddedChat'] = true;
    const fixture = ready(); const page = fixture.componentInstance; const root = fixture.nativeElement as HTMLElement;
    expect(page.embedded).toBe(true);
    expect(root.querySelector('.assistant-chat')?.hasAttribute('hidden')).toBe(true);
    expect(root.querySelector('app-assistant-conversation-picker')).not.toBeNull();
    page.newConversation(); fixture.detectChanges();
    expect(root.querySelector('.assistant-chat')?.hasAttribute('hidden')).toBe(false);
    expect(root.querySelector('app-analysis-result-tabs')).toBeNull();
    expect(root.querySelector('.assistant-configuration')).toBeNull();
    expect(root.querySelector('.assistant-launch')).toBeNull();
    const chat = fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance as AnalysisFollowUpChatComponent;
    chat.sendMessage.emit('Odczytaj klienta');
    http.expectOne('/api/system-assistant/conversations').flush({id: 'embedded', busy: false, pageContext: null, messages: []});
    const send = http.expectOne('/api/system-assistant/conversations/embedded/messages');
    expect(send.request.body.message).toBe('Odczytaj klienta');
    send.flush({id: 'embedded', busy: false, pageContext: null, messages: []});
    expect(page.submitting()).toBe(false); fixture.destroy();
  });
  function configurationInput(content: string, size = content.length) {
    const input = {files: [{name: 'configuration.json', size, text: vi.fn(() => Promise.resolve(content))}], value: 'configuration.json'};
    return {input, event: {target: input} as unknown as Event};
  }
  it('protects only real changes on unload, including across tabs and a failed save', async () => {
    const fixture = ready(); const page = fixture.componentInstance; const editor = configurationEditor(fixture);
    const browser = TestBed.inject(SYSTEM_ASSISTANT_WINDOW);
    const canUnload = () => browser.dispatchEvent(new Event('beforeunload', {cancelable: true}));
    expect(canUnload()).toBe(true);
    editor.updateProfile(0, 'name', 'Szkic'); page.workspaceView.set('conversation'); fixture.detectChanges();
    expect(canUnload()).toBe(false); expect(browser.confirm).not.toHaveBeenCalled();
    editor.updateProfile(0, 'name', testProfile.name); fixture.detectChanges();
    expect(canUnload()).toBe(true); expect(page.canDeactivate()).toBe(true);
    editor.toggleProfileResource(0, 'skillIds', 'triage', true); fixture.detectChanges();
    expect(canUnload()).toBe(false);
    const original = {authProfiles: [], profiles: [testProfile], skills: [], apis: []};
    await editor.importConfiguration(configurationInput(JSON.stringify({version: 1, configuration: original})).event);
    fixture.detectChanges(); expect(canUnload()).toBe(true); expect(page.dirty()).toBe(false);
    editor.updateProfile(0, 'name', 'Nowa nazwa'); editor.save(); fixture.detectChanges();
    http.expectOne('/api/system-assistant/configuration').flush({}, {status: 503, statusText: 'Unavailable'});
    expect(canUnload()).toBe(false);
    editor.save(); http.expectOne('/api/system-assistant/configuration').flush(editor.configuration());
    http.expectOne('/api/system-assistant/configuration/tools').flush([]); fixture.detectChanges();
    expect(canUnload()).toBe(true);
    editor.updateProfile(0, 'name', testProfile.name); fixture.detectChanges();
    expect(canUnload()).toBe(false);
    fixture.destroy(); expect(canUnload()).toBe(true);
  });
  it('clears the warning after reverting an optional Auth field to its displayed empty value', () => {
    const auth = newAuthProfile('OAUTH2', 'access', 'Dostęp', 'http://localhost');
    const fixture = ready([], {authProfiles: [auth]}); const editor = configurationEditor(fixture);
    editor.updateAuthProfiles([{...auth, scopes: 'read'}]); expect(editor.dirty()).toBe(true);
    editor.updateAuthProfiles([{...auth, scopes: ''}]); expect(editor.dirty()).toBe(false);
    fixture.destroy();
  });
  it('imports the whole draft without activation and preserves chat, tools and AI preferences until Save', async () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    editor.addSkill(); page.workspaceView.set('conversation');
    const chat = fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance as AnalysisFollowUpChatComponent;
    chat.messageText.set('Niedokończona wiadomość');
    const conversation: AssistantConversation = {id: 'chat', busy: true, pageContext: null, messages: []};
    page.conversation.set(conversation); page.model.set('chosen'); page.reasoningEffort.set('high');
    const activeTools = [{apiId: 'old', name: 'read', description: '', method: 'GET', path: '/old', inputSchema: {}}];
    editor.tools.set(activeTools);
    const imported = {authProfiles: [], profiles: [], apis: [{id: 'products', name: 'Produkty', yaml: 'selected YAML'}], skills: [{id: 'new', name: 'Nowy', content: ''}]};
    const {input, event} = configurationInput(JSON.stringify({version: 1, configuration: imported}));
    await editor.importConfiguration(event); fixture.detectChanges();
    expect(input.value).toBe(''); expect(editor.configuration()).toEqual(imported); expect(page.dirty()).toBe(true);
    expect(page.workspaceView()).toBe('skills'); expect(page.notice()).toContain('Zapisz');
    expect(page.conversation()).toBe(conversation); expect(editor.tools()).toBe(activeTools);
    expect(page.model()).toBe('chosen'); expect(page.reasoningEffort()).toBe('high');
    expect(chat.messageText()).toBe('Niedokończona wiadomość');
    http.expectNone('/api/system-assistant/configuration'); http.expectNone('/api/system-assistant/configuration/tools');
    editor.save(); const rejected = http.expectOne('/api/system-assistant/configuration');
    expect(rejected.request.method).toBe('PUT'); expect(rejected.request.body).toEqual(imported);
    rejected.flush({message: 'Skill wymaga instrukcji'}, {status: 400, statusText: 'Bad request'});
    expect(editor.configuration()).toEqual(imported); expect(page.dirty()).toBe(true); expect(editor.tools()).toBe(activeTools);
    editor.updateSkill(0, 'content', 'Instrukcje'); editor.save();
    http.expectOne('/api/system-assistant/configuration').flush(editor.configuration());
    http.expectOne('/api/system-assistant/configuration/tools').flush([]);
    expect(page.dirty()).toBe(false); fixture.destroy();
  });
  it.each(['{broken', '{"version":2,"configuration":{"apis":[],"skills":[]}}', '{"version":1,"configuration":{"apis":null,"skills":[]}}'])('keeps unsaved work when import is invalid: %s', async content => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    editor.addSkill(); editor.updateSkill(0, 'content', 'Zachowaj'); page.workspaceView.set('contracts');
    const draft = editor.configuration(); const {input, event} = configurationInput(content);
    await editor.importConfiguration(event);
    expect(editor.configuration()).toBe(draft); expect(page.dirty()).toBe(true); expect(page.workspaceView()).toBe('contracts');
    expect(page.error()).not.toBe(''); expect(input.value).toBe(''); expect(editor.importing()).toBe(false);
    http.expectNone('/api/system-assistant/configuration'); fixture.destroy();
  });
  it('handles cancellation, unreadable files, file limits and repeated selection without replacing the draft', async () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture); editor.addSkill(); const draft = editor.configuration();
    await editor.importConfiguration({target: {files: [], value: ''}} as unknown as Event);
    expect(editor.configuration()).toBe(draft); expect(page.error()).toBe('');
    const oversized = configurationInput('{}', 100_000_001); await editor.importConfiguration(oversized.event);
    expect(oversized.input.files[0].text).not.toHaveBeenCalled(); expect(page.error()).toContain('100000000');
    const unreadable = configurationInput('{}'); unreadable.input.files[0].text.mockRejectedValue(new Error('Odczyt nieudany'));
    await editor.importConfiguration(unreadable.event); expect(page.error()).toBe('Odczyt nieudany'); expect(editor.configuration()).toBe(draft);
    const valid = configurationInput('{"version":1,"configuration":{"apis":[],"skills":[],"profiles":[],"authProfiles":[]}}');
    await editor.importConfiguration(valid.event); editor.addSkill(); await editor.importConfiguration(valid.event);
    expect(valid.input.files[0].text).toHaveBeenCalledTimes(2); expect(valid.input.value).toBe('');
    expect(editor.configuration()).toEqual({authProfiles: [], profiles: [], apis: [], skills: []}); expect(page.dirty()).toBe(true); fixture.destroy();
  });
  it('locks configuration while reading, without changing chat, and ignores completion after destruction', async () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture); editor.addSkill(); const draft = editor.configuration();
    let resolve!: (text: string) => void;
    const pending = configurationInput(''); pending.input.files[0].text.mockImplementation(() => new Promise(done => resolve = done));
    const operation = editor.importConfiguration(pending.event); fixture.detectChanges();
    expect(editor.importing()).toBe(true);
    expect(fixture.nativeElement.querySelector('.assistant-configuration fieldset').disabled).toBe(true);
    expect([...fixture.nativeElement.querySelectorAll('.assistant-configuration-actions button, .assistant-configuration-add button')].every((button: any) => button.disabled)).toBe(true);
    editor.addSkill(); editor.updateSkill(0, 'name', 'Ignored'); editor.remove('skills', 0); editor.save();
    expect(editor.configuration()).toBe(draft); expect(page.canChat()).toBe(true);
    http.expectNone('/api/system-assistant/configuration');
    fixture.destroy(); resolve('{"version":1,"configuration":{"apis":[],"skills":[]}}'); await operation;
    expect(editor.configuration()).toBe(draft); expect(pending.input.value).toBe('');
  });
  it('exports the visible draft through the header action with a JSON file and no state changes', async () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    editor.configuration.set({authProfiles: [], profiles: [], apis: [{id: 'api', name: 'Żółć', yaml: 'openapi: 3.0.3\n'}], skills: [{id: 's', name: 'Skill', content: '# Reguły\n'}]});
    page.workspaceView.set('launch'); page.model.set('private-model'); fixture.detectChanges();
    const originalCreate = Object.getOwnPropertyDescriptor(URL, 'createObjectURL');
    const originalRevoke = Object.getOwnPropertyDescriptor(URL, 'revokeObjectURL');
    const create = vi.fn((_blob: Blob) => 'blob:configuration'); const revoke = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {configurable: true, value: create});
    Object.defineProperty(URL, 'revokeObjectURL', {configurable: true, value: revoke});
    let fileName = '';
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {fileName = this.download;});
    try {
      const root = fixture.nativeElement as HTMLElement;
      const exportButton = root.querySelector<HTMLButtonElement>('[aria-label="Eksportuj konfigurację"]')!;
      expect(root.querySelector('[role=tablist]')?.contains(exportButton)).toBe(false);
      const tooltip = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatTooltipHarness.with({selector: '[aria-label="Eksportuj konfigurację"]'}));
      await tooltip.show(); expect(await tooltip.getTooltipText()).toBe('Eksportuj konfigurację'); await tooltip.hide();
      exportButton.click();
      expect(fileName).toBe('system-assistant-configuration.json'); const blob = create.mock.calls[0][0];
      expect(blob.type).toBe('application/json');
      const content = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.onerror = () => reject(reader.error); reader.readAsText(blob);
      });
      expect(JSON.parse(content)).toEqual({version: 1, configuration: editor.configuration()});
      expect(page.workspaceView()).toBe('launch'); expect(page.dirty()).toBe(true);
      expect(document.querySelector('a[download="system-assistant-configuration.json"]')).toBeNull();
      await vi.waitFor(() => expect(revoke).toHaveBeenCalledWith('blob:configuration'));
      http.expectNone('/api/system-assistant/configuration');
    } finally {
      click.mockRestore(); fixture.destroy();
      if (originalCreate) Object.defineProperty(URL, 'createObjectURL', originalCreate); else Reflect.deleteProperty(URL, 'createObjectURL');
      if (originalRevoke) Object.defineProperty(URL, 'revokeObjectURL', originalRevoke); else Reflect.deleteProperty(URL, 'revokeObjectURL');
    }
  });
  it.each(['skills', 'profiles', 'auth'] as const)('collapses existing %s, opens a new item and preserves expansion and edits across tabs', view => {
    const fixture = ready([], {skills: [{id: 'triage', name: 'Analiza CRM', content: 'Reguły'}],
      authProfiles: [{...newAuthProfile('BASIC', 'crm_access', 'Dostęp CRM', 'http://localhost'), username: 'operator', password: 'public-test-password'}]});
    const page = fixture.componentInstance; const editor = configurationEditor(fixture);
    page.workspaceView.set(view); fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const heading = () => root.querySelector<HTMLButtonElement>('.assistant-definition-toggle')!;
    const body = () => root.querySelector<HTMLElement>('.assistant-definition-body')!;
    const expected = {skills: ['Analiza CRM', 'triage'], profiles: ['Test CRM', 'Wszystkie adresy'], auth: ['Dostęp CRM', 'Basic']}[view];
    expect(heading().textContent).toContain(expected[0]); expect(heading().textContent).toContain(expected[1]);
    expect(heading().textContent).not.toContain('public-test-password');
    expect(heading().getAttribute('aria-expanded')).toBe('false'); expect(body().hidden).toBe(true);
    expect(heading().getAttribute('aria-controls')).toBe(body().id);
    heading().click(); fixture.detectChanges();
    expect(body().hidden).toBe(false); expect(page.dirty()).toBe(false);
    const field = body().querySelector<HTMLInputElement>('input')!;
    field.value = 'Robocza zmiana'; field.dispatchEvent(new Event('input', {bubbles: true})); fixture.detectChanges();
    expect(page.dirty()).toBe(true);
    heading().click(); fixture.detectChanges(); expect(body().hidden).toBe(true);
    if (view === 'skills') editor.addSkill(); else if (view === 'profiles') editor.addProfile(); else editor.addAuth();
    fixture.detectChanges();
    const bodies = root.querySelectorAll<HTMLElement>('.assistant-definition-body');
    expect(bodies).toHaveLength(2); expect(bodies[0].hidden).toBe(true); expect(bodies[1].hidden).toBe(false);
    page.workspaceView.set('conversation'); fixture.detectChanges(); page.workspaceView.set(view); fixture.detectChanges();
    expect(body().hidden).toBe(true);
    expect(root.querySelectorAll<HTMLElement>('.assistant-definition-body')[1].hidden).toBe(false);
    heading().click(); fixture.detectChanges();
    expect(body().querySelector<HTMLInputElement>('input')!.value).toBe('Robocza zmiana');
    fixture.destroy();
  });
  it.each([
    {view: 'skills', kind: 'skills', add: 'Dodaj skill', remove: 'Usuń skill Instrukcja'},
    {view: 'contracts', kind: 'apis', add: 'Dodaj OpenAPI', remove: 'Usuń kontrakt CRM'},
    {view: 'auth', kind: 'authProfiles', add: 'Dodaj uwierzytelnianie', remove: 'Usuń uwierzytelnianie Dostęp'},
    {view: 'profiles', kind: 'profiles', add: 'Dodaj profil', remove: 'Usuń profil Test CRM'}
  ] as const)('keeps list actions separate from global transfer and save in $view', ({view, kind, add, remove}) => {
    const fixture = ready([], {apis: [{id: 'crm', name: 'CRM', yaml: 'yaml'}], skills: [{id: 'guide', name: 'Instrukcja', content: 'Treść'}],
      authProfiles: [{...newAuthProfile('BASIC', 'access', 'Dostęp', 'http://localhost'), username: 'operator', password: 'public-password'}], profiles: [testProfile]});
    const page = fixture.componentInstance; const editor = configurationEditor(fixture);
    page.workspaceView.set(view); fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.assistant-configuration-header h2')).toBeNull();
    expect(root.querySelector('.assistant-section-count')?.textContent).toBe('Liczba wpisów: 1');
    expect(root.querySelector('.assistant-configuration-add button')?.textContent).toBe(add);
    expect(root.querySelector('.analysis-result-tabs--equal-width')).not.toBeNull();
    expect(root.querySelector('.analysis-result-tabs-header .assistant-configuration-transfer')).toBeNull();
    const saveBar = root.querySelector('.assistant-configuration-actions')!;
    expect(saveBar.querySelectorAll('button')).toHaveLength(3);
    expect(saveBar.querySelectorAll('.assistant-configuration-transfer button')).toHaveLength(2);
    expect(saveBar.querySelector('.primary-button')?.textContent).toBe('Zapisz konfigurację');
    expect(saveBar.querySelector('.assistant-save-state')).toBeNull();
    const deleteButton = root.querySelector<HTMLButtonElement>(`[aria-label="${remove}"]`)!;
    expect(deleteButton.closest('.assistant-definition-heading')).not.toBeNull();
    expect(deleteButton.classList.contains('assistant-definition-action')).toBe(true);
    deleteButton.click(); fixture.detectChanges();
    expect(editor.configuration()[kind]).toHaveLength(0);
    expect(saveBar.textContent).toContain('Niezapisane zmiany');
    expect(saveBar.querySelector<HTMLButtonElement>('.primary-button')?.disabled).toBe(false);
    http.expectNone('/api/system-assistant/configuration'); fixture.destroy();
  });

  it('saves the whole draft from any configuration tab and keeps errors and locks in the shared footer', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    editor.addSkill(); editor.updateSkill(0, 'content', 'Nowa instrukcja');
    page.workspaceView.set('profiles'); fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const save = () => root.querySelector<HTMLButtonElement>('.assistant-configuration-actions .primary-button')!;
    save().click(); fixture.detectChanges();
    const request = http.expectOne('/api/system-assistant/configuration');
    expect(request.request.body).toEqual(editor.configuration());
    expect(request.request.body.skills[0].content).toBe('Nowa instrukcja');
    expect(request.request.body.profiles).toEqual([testProfile]);
    expect(save().disabled).toBe(true);
    expect([...root.querySelectorAll<HTMLButtonElement>('.assistant-configuration-add button, .assistant-definition-heading button')].every(button => button.disabled)).toBe(true);
    request.flush({message: 'Nie zapisano zestawu'}, {status: 503, statusText: 'Unavailable'});
    page.workspaceView.set('contracts'); fixture.detectChanges();
    expect(root.querySelector('.assistant-configuration-actions [role=alert]')?.textContent).toBe('Nie zapisano zestawu');
    expect(page.dirty()).toBe(true); expect(save().disabled).toBe(false);
    save().click(); http.expectOne('/api/system-assistant/configuration').flush(editor.configuration());
    http.expectOne('/api/system-assistant/configuration/tools').flush([]); fixture.detectChanges();
    expect(page.dirty()).toBe(false); expect(save().disabled).toBe(true);
    expect(root.querySelector('.assistant-save-state')).toBeNull();
    expect(root.querySelector('.assistant-configuration-actions [role=alert]')).toBeNull();
    expect(root.querySelector('.assistant-configuration-actions [role=status]')?.textContent).toContain('Zapisano');
    fixture.destroy();
  });

  it('saves and reloads the dropped order of all four lists with their original entries and assignments', () => {
    const ids = ['crm', 'contacts', 'tickets'];
    const configuration: AssistantConfiguration = {
      skills: ids.map(id => ({id, name: id, content: 'Instrukcja ' + id})),
      apis: ids.map(id => ({id, name: id, yaml: 'YAML ' + id})),
      authProfiles: ids.map(id => ({...newAuthProfile('BEARER', id, id, 'http://localhost'), token: 'public-' + id})),
      profiles: ids.map(id => ({id, name: id, urlPattern: '*', skillIds: [id], apiBindings: [{apiId: id, authProfileId: id}]}))
    };
    const tabs = [['skills', 'skills'], ['contracts', 'apis'], ['auth', 'authProfiles'], ['profiles', 'profiles']] as const;
    const fixture = ready([], configuration);
    const editor = configurationEditor(fixture);
    const saveButton: HTMLButtonElement = fixture.nativeElement.querySelector('.assistant-configuration-actions .primary-button');
    const browser = TestBed.inject(SYSTEM_ASSISTANT_WINDOW);
    vi.mocked(browser.confirm).mockReturnValue(false);
    const canUnload = () => browser.dispatchEvent(new Event('beforeunload', {cancelable: true}));
    expect(saveButton.disabled).toBe(true);
    for (const [view, kind] of tabs) {
      fixture.componentInstance.workspaceView.set(view);
      fixture.detectChanges();
      const element = fixture.debugElement.query(By.directive(CdkDropList));
      const list = element.injector.get(CdkDropList);
      const items = list.getSortedItems();
      expect(items.map(item => item.data)).toEqual(configuration[kind]);
      expect(element.queryAll(By.directive(CdkDragHandle))).toHaveLength(ids.length);
      expect(element.queryAll(By.css('.assistant-definition-heading > app-assistant-order-handle:first-child'))).toHaveLength(ids.length);
      const drop = {previousContainer: list, container: list, item: items[2], previousIndex: 2, currentIndex: 0};
      element.triggerEventHandler('cdkDropListDropped', {...drop, isPointerOverContainer: false});
      expect(editor.configuration()[kind]).toEqual(configuration[kind]);
      element.triggerEventHandler('cdkDropListDropped', {...drop, isPointerOverContainer: true});
      fixture.detectChanges();
      expect(list.getSortedItems().map(item => item.data.id)).toEqual(['tickets', 'crm', 'contacts']);
    }
    expect(saveButton.disabled).toBe(false);
    expect(fixture.componentInstance.canDeactivate()).toBe(false);
    expect(canUnload()).toBe(false);
    http.expectNone('/api/system-assistant/configuration');
    saveButton.click();
    const save = http.expectOne('/api/system-assistant/configuration');
    for (const [, kind] of tabs) {
      const [first, second, third] = configuration[kind];
      expect(save.request.body[kind]).toEqual([third, first, second]);
    }
    save.flush(save.request.body);
    http.expectOne('/api/system-assistant/configuration/tools').flush([]);
    fixture.detectChanges();
    expect(saveButton.disabled).toBe(true);
    expect(fixture.componentInstance.canDeactivate()).toBe(true);
    expect(canUnload()).toBe(true);
    fixture.destroy();

    const reloaded = ready([], save.request.body);
    for (const [view] of tabs) {
      reloaded.componentInstance.workspaceView.set(view);
      reloaded.detectChanges();
      const titles: NodeListOf<HTMLElement> = reloaded.nativeElement.querySelectorAll('.assistant-definition-title');
      expect([...titles].map(title => title.textContent)).toEqual(['tickets', 'crm', 'contacts']);
    }
    reloaded.destroy();
  });

  it('keeps an open skill and its editable ID with the row when moving it from the handle menu', async () => {
    const fixture = ready([], {skills: ['crm', 'contacts'].map(id => ({id, name: id, content: id}))});
    const editor = configurationEditor(fixture);
    const root = fixture.nativeElement as HTMLElement;
    const field = () => root.querySelector('.assistant-definition-body:not([hidden]) input');
    root.querySelector<HTMLButtonElement>('.assistant-definition-toggle')!.click();
    fixture.detectChanges();
    const originalField = field();
    editor.updateSkill(0, 'id', 'contacts');
    fixture.detectChanges();
    const handle = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatMenuHarness.with({triggerText: 'drag_indicator'}));
    await handle.open();
    await handle.clickItem({text: 'Przesuń w dół'});
    expect(editor.configuration().skills.map(skill => skill.name)).toEqual(['contacts', 'crm']);
    expect(root.querySelectorAll<HTMLElement>('.assistant-definition-body')[1].hidden).toBe(false);
    expect(field()).toBe(originalField);
    await handle.open();
    await handle.clickItem({text: 'Przesuń w górę'});
    editor.updateSkill(0, 'id', 'crm');
    expect(editor.dirty()).toBe(false);
    fixture.destroy();
  });

  it('keeps the skill expansion with the edited row after changing its ID and deleting an earlier row', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    editor.configuration.update(value => ({...value, skills: [{id: 'first', name: 'Pierwszy', content: ''}, {id: 'second', name: 'Drugi', content: ''}]}));
    fixture.detectChanges();
    fixture.nativeElement.querySelectorAll('.assistant-definition-toggle')[1].click(); fixture.detectChanges();
    editor.updateSkill(1, 'id', 'renamed'); editor.remove('skills', 0); fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('.assistant-definition-toggle')!.textContent).toContain('renamed');
    expect(root.querySelector<HTMLElement>('.assistant-definition-body')!.hidden).toBe(false);
    editor.addSkill(); fixture.detectChanges(); editor.remove('skills', 1); editor.addSkill(); fixture.detectChanges();
    expect(root.querySelectorAll<HTMLElement>('.assistant-definition-body')[1].hidden).toBe(false);
    fixture.destroy();
  });
  it('edits only the API name in the draft, preserves it across tabs and saves it with the configuration', () => {
    const api = {id: 'crm', name: 'CRM', yaml: 'openapi: 3.0.3\ninfo:\n  title: CRM\n'};
    const profile = {...testProfile, apiBindings: [{apiId: 'crm', authProfileId: null}]};
    const fixture = ready([], {apis: [api], profiles: [profile]});
    const page = fixture.componentInstance; const editor = configurationEditor(fixture);
    page.workspaceView.set('contracts'); fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const body = () => root.querySelector<HTMLElement>('#assistant-api-name-0')!;
    const edit = root.querySelector<HTMLButtonElement>('[aria-label="Edytuj nazwę API CRM"]')!;
    expect(body().hidden).toBe(true);
    edit.click(); fixture.detectChanges();
    expect(body().hidden).toBe(false); expect(edit.getAttribute('aria-expanded')).toBe('true');
    expect(page.dirty()).toBe(false);
    const input = body().querySelector<HTMLInputElement>('input')!;
    expect(input.maxLength).toBe(100); expect(input.required).toBe(true);
    input.value = 'Obsługa klientów'; input.dispatchEvent(new Event('input', {bubbles: true})); fixture.detectChanges();
    expect(editor.configuration().apis).toEqual([{...api, name: 'Obsługa klientów'}]);
    expect(editor.configuration().profiles).toEqual([profile]); expect(page.dirty()).toBe(true);
    expect(root.querySelector('.assistant-contract h3')!.textContent).toBe('Obsługa klientów');
    http.expectNone('/api/system-assistant/configuration');
    page.workspaceView.set('profiles'); fixture.detectChanges();
    root.querySelector<HTMLButtonElement>('.assistant-definition-toggle')!.click(); fixture.detectChanges();
    expect(root.querySelector('.assistant-profile-resources')!.textContent).toContain('Obsługa klientów');
    page.workspaceView.set('contracts'); fixture.detectChanges();
    expect(body().hidden).toBe(false); expect(body().querySelector<HTMLInputElement>('input')!.value).toBe('Obsługa klientów');
    editor.save(); fixture.detectChanges();
    const save = http.expectOne('/api/system-assistant/configuration');
    expect(save.request.body).toEqual(editor.configuration());
    expect(body().querySelector<HTMLInputElement>('input')!.disabled).toBe(true);
    editor.updateApiName('crm', 'Ignored'); editor.toggleApiName('crm');
    expect(editor.configuration().apis[0].name).toBe('Obsługa klientów'); expect(body().hidden).toBe(false);
    save.flush(editor.configuration()); http.expectOne('/api/system-assistant/configuration/tools').flush([]);
    fixture.detectChanges(); expect(page.dirty()).toBe(false);
    expect(editor.configuration().apis[0]).toEqual({...api, name: 'Obsługa klientów'});
    fixture.destroy();
  });

  it.each(['', '   ', 'x'.repeat(101)])('opens the API name editor and blocks saving invalid name %s', name => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    editor.configuration.update(value => ({...value, apis: [{id: 'crm', name, yaml: 'yaml'}]}));
    editor.save(); fixture.detectChanges();
    expect(page.workspaceView()).toBe('contracts'); expect(editor.editingApiId()).toBe('crm');
    expect(fixture.nativeElement.querySelector('#assistant-api-name-0').hidden).toBe(false);
    expect(editor.saveError()).toBe(editor.apiNameError(name));
    http.expectNone('/api/system-assistant/configuration'); fixture.destroy();
  });

  it('keeps the edited API name after a failed save and clears editor state when that API is deleted', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    editor.configuration.update(value => ({...value, apis: [{id: 'crm', name: 'CRM', yaml: 'yaml'}]}));
    editor.toggleApiName('crm'); editor.updateApiName('crm', 'Nowa nazwa'); editor.save();
    http.expectOne('/api/system-assistant/configuration').flush({message: 'Zapis niedostępny'}, {status: 503, statusText: 'Unavailable'});
    expect(editor.configuration().apis[0].name).toBe('Nowa nazwa');
    expect(page.dirty()).toBe(true); expect(editor.editingApiId()).toBe('crm');
    editor.remove('apis', 0); expect(editor.editingApiId()).toBeNull();
    fixture.destroy();
  });

  it('opens invalid authentication on save so its field errors stay visible', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    editor.configuration.update(value => ({...value, authProfiles: [newAuthProfile('BASIC', 'invalid', 'Do poprawy', 'http://localhost')]}));
    page.workspaceView.set('auth'); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.assistant-definition-body').hidden).toBe(true);
    editor.save(); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.assistant-definition-body').hidden).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Podaj login.');
    http.expectNone('/api/system-assistant/configuration'); fixture.destroy();
  });
  it('starts on skills and preserves work across four flat views', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    const root = fixture.nativeElement as HTMLElement;
    const configuration = root.querySelector('.assistant-configuration') as HTMLElement;
    const conversation = root.querySelector('.assistant-chat') as HTMLElement;
    const launch = root.querySelector('.assistant-launch') as HTMLElement;
    const switchTo = (id: string) => {
      root.querySelector<HTMLButtonElement>(`#system-assistant-view-tab-${id}`)!.click(); fixture.detectChanges();
    };
    const chat = fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance as AnalysisFollowUpChatComponent;
    expect(page.workspaceView()).toBe('skills');
    expect([...root.querySelectorAll('[role=tab]')].map(tab => tab.textContent?.trim())).toEqual(['Skille', 'Kontrakty', 'Auth', 'Profile', 'Rozmowa', 'Uruchamianie w systemie']);
    expect(root.querySelectorAll('[role=tablist]')).toHaveLength(1);
    expect(configuration.hidden).toBe(false); expect(conversation.hidden).toBe(true); expect(launch.hidden).toBe(true);
    expect(page.formTourSteps().map(step => step.anchor)).toEqual(['assistant-skills']);
    editor.addSkill(); editor.updateSkill(0, 'content', 'Robocze instrukcje');
    switchTo('contracts');
    expect(page.formTourSteps().map(step => step.anchor)).toEqual(['assistant-api']);
    switchTo('conversation');
    expect(conversation.hidden).toBe(false); expect(configuration.hidden).toBe(true);
    expect(page.formTourSteps().map(step => step.anchor)).toEqual(['assistant-model', 'assistant-chat']);
    chat.messageText.set('Robocza wiadomość'); page.model.set('test-model'); page.reasoningEffort.set('high');
    const current: AssistantConversation = {id: 'ongoing-chat', busy: true, pageContext: null, messages: []};
    page.conversation.set(current);
    switchTo('launch');
    expect(launch.hidden).toBe(false); expect(conversation.hidden).toBe(true); expect(configuration.hidden).toBe(true);
    expect(page.formTourSteps().map(step => step.anchor)).toEqual(['assistant-launch']);
    switchTo('skills');
    expect(editor.configuration().skills[0].content).toBe('Robocze instrukcje');
    expect(root.querySelector<HTMLTextAreaElement>('.assistant-configuration textarea')?.value).toBe('Robocze instrukcje');
    expect(page.dirty()).toBe(true); expect(page.conversation()).toBe(current);
    switchTo('conversation');
    const preservedChat = fixture.debugElement.query(By.directive(AnalysisFollowUpChatComponent)).componentInstance as AnalysisFollowUpChatComponent;
    expect(preservedChat).toBe(chat); expect(preservedChat.messageText()).toBe('Robocza wiadomość');
    expect(page.model()).toBe('test-model'); expect(page.reasoningEffort()).toBe('high');
    http.expectNone('/api/system-assistant/configuration'); http.expectNone('/api/system-assistant/conversations');
    fixture.destroy();
  });
  it('imports a large source into a dialog and adds only the prepared subset to the draft', async () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    const source = 'source '.repeat(30_000);
    await editor.readNewOpenApi({target: {files: [{name: 'large.yaml', size: 210_000, text: () => Promise.resolve(source)}], value: 'large.yaml'}} as unknown as Event);
    fixture.detectChanges();
    const preview = http.expectOne('/api/system-assistant/configuration/openapi/preview');
    expect(preview.request.body.yaml).toBe(source); expect(editor.configuration().apis).toHaveLength(0);
    preview.flush({title: 'Duże CRM', operations: [{method: 'GET', path: '/tickets', operationId: 'getTickets', summary: '', tags: [], warnings: [], unavailableReason: null}], maxSelected: 60, specification: {openapi: '3.0.3', paths: {'/tickets': {get: {operationId: 'getTickets'}}}}});
    await fixture.whenStable();
    expect(document.querySelector('cdk-dialog-container')?.contains(document.activeElement)).toBe(true);
    const dialog = TestBed.inject(Dialog).openDialogs[0];
    const picker = dialog.componentInstance as OpenApiOperationPicker;
    picker.toggle(picker.preview()!.operations[0], true); picker.apply();
    http.expectOne('/api/system-assistant/configuration/openapi/selection').flush({yaml: 'selected YAML', operationCount: 1});
    expect(editor.configuration().apis).toHaveLength(1); expect(editor.configuration().apis[0].yaml).toBe('selected YAML');
    expect(editor.configuration().apis[0].name).toBe('Duże CRM'); expect(page.dirty()).toBe(true);
    http.expectNone('/api/system-assistant/configuration');
    editor.save(); const save = http.expectOne('/api/system-assistant/configuration');
    expect(save.request.body.apis[0].yaml).toBe('selected YAML'); save.flush(editor.configuration());
    http.expectOne('/api/system-assistant/configuration/tools').flush([]); fixture.destroy();
  });
  it('keeps drafts across tabs and offers preview, export and removal for contracts', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    const current = {id: 'crm', name: 'CRM', yaml: 'current YAML'};
    editor.configuration.set({authProfiles: [], profiles: [], apis: [current], skills: []}); editor.addSkill();
    editor.updateSkill(0, 'content', 'Instrukcje robocze'); fixture.detectChanges();
    const tabs = [...(fixture.nativeElement as HTMLElement).querySelectorAll('[mat-tab-link]')];
    tabs.find(tab => tab.textContent?.trim() === 'Kontrakty')!.dispatchEvent(new MouseEvent('click', {bubbles: true}));
    fixture.detectChanges();
    const buttons = [...(fixture.nativeElement as HTMLElement).querySelectorAll('button')].map(button => button.getAttribute('aria-label') ?? button.textContent?.trim());
    expect(buttons).toContain('Podgląd kontraktu CRM'); expect(buttons).toContain('Usuń kontrakt CRM');
    expect(buttons).toContain('Eksportuj kontrakt CRM');
    expect(buttons).not.toContain('Dodaj API ręcznie'); expect(buttons).not.toContain('Wybierz operacje');
    expect(fixture.nativeElement.querySelector('.assistant-configuration textarea')).toBeNull();
    page.workspaceView.set('skills'); fixture.detectChanges();
    expect(editor.configuration().skills[0].content).toBe('Instrukcje robocze');
    page.workspaceView.set('contracts'); editor.remove('apis', 0); fixture.detectChanges();
    expect(editor.configuration().apis).toHaveLength(0); expect(page.dirty()).toBe(true);
    http.expectNone('/api/system-assistant/configuration'); fixture.destroy();
  });
  it('opens read-only documentation without changing the configuration', () => {
    const current = {id: 'crm', name: 'CRM', yaml: 'current YAML'};
    const fixture = ready([], {apis: [current]}); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    editor.viewContract(current); fixture.detectChanges();
    http.expectOne('/api/system-assistant/configuration/openapi/document').flush({specification: {openapi: '3.0.3', paths: {}}});
    TestBed.inject(Dialog).openDialogs[0].close();
    expect(editor.configuration().apis[0]).toBe(current); expect(page.dirty()).toBe(false);
    http.expectNone('/api/system-assistant/configuration/openapi/selection'); fixture.destroy();
  });
  it('accepts an OpenAPI JSON source and keeps the version 3.1 document for backend selection', async () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture); page.workspaceView.set('contracts'); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[accept*=".yaml"]').getAttribute('accept')).toContain('.json');
    const source = JSON.stringify({openapi: '3.1.0', paths: {'/products': {post: {operationId: 'addProduct'}}}});
    await editor.readNewOpenApi({target: {files: [{name: 'backend.json', size: source.length, text: () => Promise.resolve(source)}], value: 'backend.json'}} as unknown as Event);
    fixture.detectChanges();
    const request = http.expectOne('/api/system-assistant/configuration/openapi/preview');
    expect(request.request.body.yaml).toBe(source);
    request.flush({title: 'Products', operations: [], maxSelected: 60, specification: JSON.parse(source)});
    TestBed.inject(Dialog).closeAll(); fixture.destroy();
  });
  it('downloads the exact YAML of the selected draft without saving or changing it', async () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    const current = {id: 'crm', name: 'CRM / demo', yaml: 'openapi: 3.0.3\ninfo:\n  title: Zgłoszenia — Łódź\n  version: 1.0.0\npaths:\n  /selected: {}\n'};
    editor.configuration.set({authProfiles: [], profiles: [], apis: [{id: 'other', name: 'Other', yaml: 'other YAML'}, current], skills: []});
    page.workspaceView.set('contracts'); fixture.detectChanges();
    const originalCreate = Object.getOwnPropertyDescriptor(URL, 'createObjectURL');
    const originalRevoke = Object.getOwnPropertyDescriptor(URL, 'revokeObjectURL');
    const create = vi.fn((_blob: Blob) => 'blob:contract-export'); const revoke = vi.fn();
    Object.defineProperty(URL, 'createObjectURL', {configurable: true, value: create});
    Object.defineProperty(URL, 'revokeObjectURL', {configurable: true, value: revoke});
    let fileName = ''; let attached = false;
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      fileName = this.download; attached = document.body.contains(this);
    });
    try {
      fixture.nativeElement.querySelector('[aria-label="Eksportuj kontrakt CRM / demo"]').click();
      expect(create).toHaveBeenCalledTimes(1); expect(fileName).toBe('CRM-demo.yaml'); expect(attached).toBe(true);
      const blob = create.mock.calls[0][0]; expect(blob.type).toBe('application/yaml;charset=utf-8');
      const content = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.onerror = () => reject(reader.error); reader.readAsText(blob);
      });
      expect(content).toBe(current.yaml);
      expect(editor.configuration().apis[1]).toBe(current); expect(page.dirty()).toBe(true);
      expect(document.querySelector('a[download="CRM-demo.yaml"]')).toBeNull();
      await vi.waitFor(() => expect(revoke).toHaveBeenCalledWith('blob:contract-export'));
      http.expectNone('/api/system-assistant/configuration');
    } finally {
      click.mockRestore(); fixture.destroy();
      if (originalCreate) Object.defineProperty(URL, 'createObjectURL', originalCreate); else Reflect.deleteProperty(URL, 'createObjectURL');
      if (originalRevoke) Object.defineProperty(URL, 'revokeObjectURL', originalRevoke); else Reflect.deleteProperty(URL, 'revokeObjectURL');
    }
  });
  it('shows active operations per contract and updates counts only after save', async () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    const apis = [{id: 'crm', name: 'CRM', yaml: 'yaml'}, {id: 'crm_a', name: 'Other', yaml: 'yaml'},
      {id: 'new', name: 'New draft', yaml: 'yaml'}];
    const tool = (apiId: string, name: string, path: string) => ({apiId, name, path, method: 'GET', description: '', inputSchema: {}});
    const active = [tool('crm', 'oa_crm_a_read', '/tickets'), tool('crm_a', 'oa_crm_a_write', '/other')];
    editor.configuration.set({authProfiles: [], profiles: [], apis, skills: []}); editor.tools.set(active); page.workspaceView.set('contracts');
    fixture.detectChanges();
    const counts = () => [...(fixture.nativeElement as HTMLElement).querySelectorAll('.assistant-contract-count')].map(node => node.textContent?.trim());
    expect(counts()).toEqual(['Aktywne operacje: 1', 'Aktywne operacje: 1', 'Aktywne operacje: 0']);
    expect(fixture.nativeElement.textContent).not.toContain('Aktywne narzędzia');
    const tooltip = await TestbedHarnessEnvironment.loader(fixture).getHarness(MatTooltipHarness.with({selector: '.assistant-contract-count'}));
    await tooltip.show(); expect(await tooltip.getTooltipText()).toContain('GET /tickets');
    expect(await tooltip.getTooltipText()).not.toContain('/other'); await tooltip.hide();
    editor.save(); http.expectOne('/api/system-assistant/configuration').flush(editor.configuration()); fixture.detectChanges();
    expect(counts()).toEqual(['Aktywne operacje: —', 'Aktywne operacje: —', 'Aktywne operacje: —']);
    http.expectOne('/api/system-assistant/configuration/tools').flush([...active, tool('new', 'oa_new_read', '/new')]);
    fixture.detectChanges(); expect(counts()).toEqual(['Aktywne operacje: 1', 'Aktywne operacje: 1', 'Aktywne operacje: 1']); fixture.destroy();
  });
  it('does not present unavailable operation counts as zero', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    editor.configuration.set({authProfiles: [], profiles: [], apis: [{id: 'crm', name: 'CRM', yaml: 'yaml'}], skills: []});
    page.workspaceView.set('contracts'); editor.loadTools();
    http.expectOne('/api/system-assistant/configuration/tools').flush({}, {status: 503, statusText: 'Unavailable'});
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.assistant-contract-count').textContent.trim()).toBe('Aktywne operacje: —');
    expect(fixture.nativeElement.querySelector('.assistant-configuration [role=alert]').textContent).toContain('Nie udało się pobrać');
    fixture.destroy();
  });
  it('starts chat without an analysis and recovers polling without resubmitting', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    page.workspaceView.set('conversation'); fixture.detectChanges();
    expect(page.canChat()).toBe(true);
    page.send('Odczytaj');
    http.expectOne('/api/system-assistant/conversations').flush({ id: 'chat', busy: false, pageContext: null, messages: [] });
    const accepted: AssistantConversation = { id: 'chat', busy: true, pageContext: null, messages: [] };
    http.expectOne('/api/system-assistant/conversations/chat/messages').flush(accepted, { status: 202, statusText: 'Accepted' });
    http.expectOne('/api/system-assistant/conversations/chat').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(page.conversation()?.busy).toBe(true); expect(page.chatError()).toContain('Wznów');
    page.workspaceView.set('skills'); fixture.detectChanges();
    page.startPolling();
    http.expectOne('/api/system-assistant/conversations/chat').flush({ ...accepted, busy: false });
    expect(page.conversation()?.busy).toBe(false); expect(page.workspaceView()).toBe('skills');
    fixture.destroy();
  });
  it('keeps a rejected configuration as an editable draft', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const editor = configurationEditor(fixture);
    editor.addSkill(); editor.updateSkill(0, 'content', 'broken'); editor.save();
    http.expectOne('/api/system-assistant/configuration').flush({ message: 'Niepoprawny skill' }, { status: 400, statusText: 'Bad request' });
    expect(editor.configuration().skills[0].content).toBe('broken'); expect(page.dirty()).toBe(true);
    expect(editor.saveError()).toContain('skill'); expect(page.error()).toBe(''); expect(page.notice()).toBe('');
    fixture.detectChanges();
    const section = (fixture.nativeElement as HTMLElement).querySelector('.assistant-configuration')!;
    const alert = section.querySelector('[role="alert"]')!;
    expect(alert.textContent).toContain('Niepoprawny skill');
    expect(alert.closest('.assistant-configuration-actions')?.querySelector('.primary-button')?.textContent?.trim()).toBe('Zapisz konfigurację');
    expect(fixture.nativeElement.querySelector('.assistant-workspace > [role="alert"]')).toBeNull();
    editor.save(); fixture.detectChanges();
    expect(section.querySelector('[role="alert"]')).toBeNull();
    http.expectOne('/api/system-assistant/configuration').flush(editor.configuration());
    http.expectOne('/api/system-assistant/configuration/tools').flush([]);
    expect(editor.saveError()).toBe(''); fixture.destroy();
  });
  it('retains a failed draft when an identical older message already exists', () => {
    const fixture = ready(); const page = fixture.componentInstance;
    const previous: AssistantConversation = {id: 'chat', busy: false, pageContext: null,
      messages: [{id: 'old-message', role: 'USER', content: 'Powtórz'} as AssistantConversation['messages'][number]]};
    page.conversation.set(previous); page.send('Powtórz');
    http.expectOne('/api/system-assistant/conversations/chat/messages').flush({}, {status: 503, statusText: 'Unavailable'});
    http.expectOne('/api/system-assistant/conversations/chat').flush(previous);
    expect(page.unsentMessage()).toBe('Powtórz');
    expect(page.submitting()).toBe(false); fixture.destroy();
  });
  it('allows repairing corrupt current configuration with an explicit empty import', async () => {
    const fixture = TestBed.createComponent(SystemAssistantPage); fixture.detectChanges();
    http.expectOne('/api/system-assistant/configuration').flush({ code: 'CONFIGURATION_UNAVAILABLE', message: 'Uszkodzony plik' }, { status: 503, statusText: 'Unavailable' });
    http.expectOne('/api/analysis/ai/options').flush({ defaultModel: 'test-model', models: [], defaultReasoningEffort: '', defaultReasoningEfforts: [] });
    http.expectOne('/api/auth/github/status').flush({ configured: true });
    const page = fixture.componentInstance; const editor = configurationEditor(fixture);
    expect(page.ready()).toBe(true); expect(page.canChat()).toBe(false); expect(page.dirty()).toBe(false);
    const empty = {apis: [], skills: [], profiles: [], authProfiles: []};
    await editor.importConfiguration(configurationInput(JSON.stringify({version: 1, configuration: empty})).event);
    expect(page.dirty()).toBe(true);
    editor.save(); http.expectOne('/api/system-assistant/configuration').flush(empty);
    http.expectOne('/api/system-assistant/configuration/tools').flush([]);
    expect(page.configurationAvailable()).toBe(true); expect(page.dirty()).toBe(false); fixture.destroy();
  });
});
