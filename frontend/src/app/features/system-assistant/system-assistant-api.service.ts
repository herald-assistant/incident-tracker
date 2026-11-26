import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {
  AssistantConfiguration,
  AssistantProfileOption,
  AssistantConversation,
  AssistantPageContext,
  AssistantTool,
  AssistantOidcConnection,
  OpenApiDocument,
  OpenApiOperationKey,
  OpenApiPrepared,
  OpenApiPreview
} from './system-assistant.models';

@Injectable({providedIn: 'root'})
export class SystemAssistantApi {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/system-assistant';

  configuration() {
    return this.http.get<AssistantConfiguration>(`${this.base}/configuration`);
  }

  profiles() {
    return this.http.get<AssistantProfileOption[]>(`${this.base}/configuration/profiles`);
  }

  tools() {
    return this.http.get<AssistantTool[]>(`${this.base}/configuration/tools`);
  }

  save(value: AssistantConfiguration) {
    return this.http.put<AssistantConfiguration>(`${this.base}/configuration`, value);
  }

  authStatus() { return this.http.get<Record<string, AssistantOidcConnection>>(`${this.base}/auth/status`); }
  loginAuth(id: string, destination?: {conversationId: string; profileId: string; returnPath: '/system-assistant' | '/system-assistant/chat'}) {
    return this.http.post<{authorizationUrl: string}>(`${this.base}/auth/${encodeURIComponent(id)}/login`, destination ?? {returnPath: '/system-assistant'});
  }
  disconnectAuth(id: string) { return this.http.post<void>(`${this.base}/auth/${encodeURIComponent(id)}/disconnect`, {}); }

  previewOpenApi(id: string, yaml: string) {
    return this.http.post<OpenApiPreview>(`${this.base}/configuration/openapi/preview`, {id, yaml});
  }

  documentOpenApi(id: string, yaml: string) {
    return this.http.post<OpenApiDocument>(`${this.base}/configuration/openapi/document`, {id, yaml});
  }

  selectOpenApi(id: string, yaml: string, operations: OpenApiOperationKey[]) {
    return this.http.post<OpenApiPrepared>(`${this.base}/configuration/openapi/selection`, {id, yaml, operations});
  }

  create(pageContext: AssistantPageContext | null = null) {
    return this.http.post<AssistantConversation>(`${this.base}/conversations`, {pageContext});
  }

  conversation(id: string) {
    return this.http.get<AssistantConversation>(`${this.base}/conversations/${encodeURIComponent(id)}`);
  }

  attach(id: string, file: File) {
    return this.http.post<AssistantConversation>(`${this.base}/conversations/${encodeURIComponent(id)}/attachments`, file,
      {params: {name: file.name}, headers: {'Content-Type': 'application/octet-stream'}});
  }

  send(id: string, message: string, profileId: string, model: string, reasoningEffort: string) {
    return this.http.post<AssistantConversation>(`${this.base}/conversations/${encodeURIComponent(id)}/messages`, {
      message, profileId, ...(model ? {model} : {}), ...(reasoningEffort ? {reasoningEffort} : {})
    });
  }
}

export function assistantErrorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 404) return 'System Assistant jest wyłączony albo rozmowa już nie istnieje. Włącz system-assistant.enabled=true lub rozpocznij nową rozmowę.';
    if (error.error?.fieldErrors?.length) return error.error.fieldErrors.map((field: { field: string; message: string }) => `${field.field}: ${field.message}`).join('; ');
    return error.error?.message || 'Nie udało się wykonać operacji. Spróbuj ponownie.';
  }
  return 'Nie udało się wykonać operacji.';
}
