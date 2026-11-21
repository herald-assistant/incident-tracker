import { AnalysisChatMessageResponse } from '../../core/models/analysis.models';

export interface AssistantApiDefinition { id: string; name: string; yaml: string; }
export interface AssistantSkill { id: string; name: string; content: string; }
export interface AssistantConfiguration { apis: AssistantApiDefinition[]; skills: AssistantSkill[]; }
export interface AssistantTool { apiId: string; name: string; description: string; method: string; path: string; inputSchema: unknown; }
export interface AssistantPageContext { origin: string; pathname: string; title: string; capturedAt: string; }
export interface AssistantConversation { id: string; busy: boolean; pageContext: AssistantPageContext | null; messages: AnalysisChatMessageResponse[]; }
export interface OpenApiOperationKey { method: string; path: string; }
export interface OpenApiCandidate extends OpenApiOperationKey { operationId: string; summary: string; tags: string[]; unavailableReason: string | null; }
export interface OpenApiPreview { title: string; operations: OpenApiCandidate[]; maxSelected: number; specification: Record<string, unknown>; }
export interface OpenApiSelectionRow extends OpenApiOperationKey { checked: boolean; disabled: boolean; unavailableReason: string | null; }
export interface OpenApiSelectionChange extends OpenApiOperationKey { checked: boolean; }
export interface OpenApiPrepared { yaml: string; operationCount: number; }
export interface OpenApiDocument { specification: Record<string, unknown>; }
