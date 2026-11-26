import { AnalysisChatMessageResponse } from '../../core/models/analysis.models';

export interface AssistantApiDefinition { id: string; name: string; yaml: string; }
export interface AssistantSkill { id: string; name: string; content: string; }
export interface AssistantApiBinding { apiId: string; authProfileId: string | null; }
export interface AssistantProfile { id: string; name: string; urlPattern: string; apiBindings: AssistantApiBinding[]; skillIds: string[]; }
export type AssistantProfileOption = Pick<AssistantProfile, 'id' | 'name' | 'urlPattern'>;
export interface AssistantConfiguration { apis: AssistantApiDefinition[]; skills: AssistantSkill[]; profiles: AssistantProfile[]; authProfiles: AssistantAuthProfile[]; }
export type AssistantAuthType = 'BASIC' | 'BEARER' | 'OAUTH2' | 'OIDC';
export interface AssistantAuthProfile {
  id: string; name: string; type: AssistantAuthType;
  username: string | null; password: string | null; token: string | null;
  tokenUrl: string | null; clientId: string | null; clientSecret: string | null;
  scopes: string | null; issuerUrl: string | null; redirectUri: string | null;
  clientAuthentication: string | null;
}
export interface AssistantOidcConnection { connected: boolean; username: string | null; expiresAt: string | null; message: string; }
export interface AssistantTool { apiId: string; name: string; description: string; method: string; path: string; inputSchema: unknown; }
export interface AssistantPageContext { origin: string; pathname: string; title: string; capturedAt: string; }
export interface AssistantAttachment { id: string; name: string; mimeType: string; size: number; messageId?: string | null; }
export interface AssistantCurrentPage extends AssistantPageContext { url: string; }
export interface AssistantConversation { id: string; busy: boolean; pageContext: AssistantPageContext | null; messages: AnalysisChatMessageResponse[]; attachments?: AssistantAttachment[]; }
export interface OpenApiOperationKey { method: string; path: string; }
export interface OpenApiCandidate extends OpenApiOperationKey { operationId: string; summary: string; tags: string[]; unavailableReason: string | null; warnings: string[]; }
export interface OpenApiPreview { title: string; operations: OpenApiCandidate[]; maxSelected: number; specification: Record<string, unknown>; }
export interface OpenApiSelectionRow extends OpenApiOperationKey { checked: boolean; disabled: boolean; unavailableReason: string | null; }
export interface OpenApiSelectionChange extends OpenApiOperationKey { checked: boolean; }
export interface OpenApiPrepared { yaml: string; operationCount: number; }
export interface OpenApiDocument { specification: Record<string, unknown>; }
