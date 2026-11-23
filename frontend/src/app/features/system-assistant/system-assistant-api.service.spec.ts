import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SystemAssistantApi } from './system-assistant-api.service';

describe('SystemAssistantApi', () => {
  let api: SystemAssistantApi; let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(SystemAssistantApi); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('previews and prepares a subset without saving configuration', () => {
    api.previewOpenApi('crm', 'source').subscribe();
    const preview = http.expectOne('/api/system-assistant/configuration/openapi/preview');
    expect(preview.request.body).toEqual({id: 'crm', yaml: 'source'}); preview.flush({});
    api.selectOpenApi('crm', 'source', [{method: 'GET', path: '/tickets'}]).subscribe();
    const selected = http.expectOne('/api/system-assistant/configuration/openapi/selection');
    expect(selected.request.body).toEqual({id: 'crm', yaml: 'source', operations: [{method: 'GET', path: '/tickets'}]}); selected.flush({});
    http.expectNone('/api/system-assistant/configuration');
    api.documentOpenApi('crm', 'selected YAML').subscribe();
    const document = http.expectOne('/api/system-assistant/configuration/openapi/document');
    expect(document.request.body).toEqual({id: 'crm', yaml: 'selected YAML'}); document.flush({specification: {}});
  });
  it('sends only selected AI preferences and encodes conversation IDs', () => {
    api.send('a/b', 'Pytanie', 'crm', '', '').subscribe();
    const request = http.expectOne('/api/system-assistant/conversations/a%2Fb/messages');
    expect(request.request.body).toEqual({ message: 'Pytanie', profileId: 'crm' }); request.flush({});
    api.send('id', 'Dalej', 'crm', 'model', 'high').subscribe();
    const selected = http.expectOne('/api/system-assistant/conversations/id/messages');
    expect(selected.request.body).toEqual({ message: 'Dalej', profileId: 'crm', model: 'model', reasoningEffort: 'high' }); selected.flush({});
  });
  it('replaces the current configuration without revision fields', () => {
    api.save({authProfiles: [], profiles: [],  apis: [], skills: [] }).subscribe();
    const request = http.expectOne('/api/system-assistant/configuration');
    expect(request.request.method).toBe('PUT'); expect(request.request.body).toEqual({authProfiles: [], profiles: [],  apis: [], skills: [] }); request.flush({});
  });
  it('reads OIDC status and starts or disconnects a saved connection', () => {
    api.authStatus().subscribe(); http.expectOne('/api/system-assistant/auth/status').flush({});
    api.loginAuth('crm/one').subscribe();
    const login = http.expectOne('/api/system-assistant/auth/crm%2Fone/login');
    expect(login.request.method).toBe('POST'); expect(login.request.body).toEqual({returnPath: '/system-assistant'}); login.flush({authorizationUrl: 'https://sso.example.com/authorize'});
    api.loginAuth('crm', {conversationId: 'crm-chat', profileId: 'crm_profile', returnPath: '/system-assistant/chat'}).subscribe();
    const chatLogin = http.expectOne('/api/system-assistant/auth/crm/login');
    expect(chatLogin.request.body).toEqual({conversationId: 'crm-chat', profileId: 'crm_profile', returnPath: '/system-assistant/chat'});
    chatLogin.flush({authorizationUrl: 'https://sso.example.com/authorize'});
    api.disconnectAuth('crm').subscribe(); const disconnect = http.expectOne('/api/system-assistant/auth/crm/disconnect');
    expect(disconnect.request.method).toBe('POST'); disconnect.flush(null);
  });
});
