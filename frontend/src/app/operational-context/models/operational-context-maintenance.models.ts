export const OPERATIONAL_CONTEXT_WRITABLE_TYPES = [
  'system',
  'repository',
  'code-search-scope',
  'process',
  'integration',
  'bounded-context',
  'team',
  'glossary-term',
  'handoff-rule'
] as const;

export type OperationalContextWritableType =
  (typeof OPERATIONAL_CONTEXT_WRITABLE_TYPES)[number];

export type OperationalContextPayload = Record<string, unknown>;

export interface OperationalContextMaintenanceCapabilities {
  source: string;
  supportedEntityTypes: OperationalContextWritableType[];
}

export interface OperationalContextEditableEntity {
  type: OperationalContextWritableType;
  id: string;
  sourceFile: string;
  payload: OperationalContextPayload;
}

export interface OperationalContextEntityWriteRequest {
  type: OperationalContextWritableType;
  id: string;
  payload: OperationalContextPayload;
}

export interface OperationalContextMutationResult {
  entity: OperationalContextEditableEntity;
}

export interface OperationalContextInboundReference {
  sourceType: string;
  sourceId: string;
  relationType: string;
  sourceFile: string | null;
  fieldPath: string | null;
}

export interface OperationalContextDeleteImpact {
  type: OperationalContextWritableType;
  id: string;
  sourceFile: string;
  allowed: boolean;
  inboundReferences: OperationalContextInboundReference[];
}

export interface OperationalContextFieldError {
  field: string;
  message: string;
}

export interface OperationalContextMaintenanceError {
  code: string;
  message: string;
  fieldErrors: OperationalContextFieldError[];
}

export interface OperationalContextReferenceOption {
  id: string;
  label: string;
}

export type OperationalContextReferenceOptions = Partial<
  Record<OperationalContextWritableType, OperationalContextReferenceOption[]>
>;

export interface OperationalContextEditorState {
  mode: 'create' | 'edit';
  type: OperationalContextWritableType;
  entity: OperationalContextEditableEntity;
}

export interface OperationalContextMutationEvent {
  action: 'create' | 'update';
  entity: OperationalContextEditableEntity;
}

export interface OperationalContextDeleteEvent {
  type: OperationalContextWritableType;
  id: string;
}

export function isOperationalContextWritableType(
  value: string | null | undefined
): value is OperationalContextWritableType {
  return OPERATIONAL_CONTEXT_WRITABLE_TYPES.includes(value as OperationalContextWritableType);
}
