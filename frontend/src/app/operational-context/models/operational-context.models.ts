export interface SourceReferenceDto {
  file: string;
  path: string;
  entityId: string | null;
}

export interface ExplanationReasonDto {
  label: string;
  detail: string;
  strength: 'strong' | 'medium' | 'weak' | string;
}

export interface ExplainableValueDto<T = string> {
  value: T;
  label: string;
  confidence?: string;
  reasons: ExplanationReasonDto[];
  warnings: string[];
  sourceRefs: SourceReferenceDto[];
}

export interface ExplainableAggregateDto {
  label: string;
  count: number;
  severity: 'ok' | 'warning' | 'error' | 'unknown' | string;
  confidence?: string;
  tooltip: string;
  groups: ExplainableBreakdownGroupDto[];
  reasons: ExplanationReasonDto[];
  warnings: string[];
  sourceRefs: SourceReferenceDto[];
  detailsType?: string;
  detailsIds: string[];
}

export interface ExplainableBreakdownGroupDto {
  label: string;
  count: number;
  items: ExplainableBreakdownItemDto[];
}

export interface ExplainableBreakdownItemDto {
  id: string;
  label: string;
  kind: string;
  reason: string;
  status: 'verified' | 'needs-review' | 'missing' | 'conflicting' | 'unknown' | string;
  sourceRefs: SourceReferenceDto[];
}

export interface ValidationFindingDto {
  id: string;
  severity: 'info' | 'warning' | 'error' | string;
  category: string;
  entityType: string;
  entityId: string;
  title: string;
  detail: string;
  sourceRefs: SourceReferenceDto[];
  suggestedFix: string;
  impact: string;
}

export interface OpenQuestionDto {
  id: string;
  sourceFile: string;
  entityType: string;
  entityId: string | null;
  question: string;
  severity: 'info' | 'warning' | 'error' | string;
  status: string;
}

export interface OperationalContextSummaryDto {
  systems: number;
  repositories: number;
  codeSearchScopes: number;
  processes: number;
  integrations: number;
  boundedContexts: number;
  teams: number;
  glossaryTerms: number;
  handoffRules: number;
  openQuestions: number;
  validationFindings: Record<string, number>;
  catalogStatus: 'empty' | 'partial' | 'ready' | 'hasIssues' | string;
  healthCards: ExplainableAggregateDto[];
}

export interface OperationalContextSystemRowDto {
  id: string;
  name: string;
  kind: string;
  owner: ExplainableValueDto<string>;
  purpose: string;
  relations: ExplainableAggregateDto;
  signals: ExplainableAggregateDto;
  handoffReadiness: ExplainableAggregateDto;
  validation: ExplainableAggregateDto;
  openQuestions: ExplainableAggregateDto;
}

export interface OperationalContextRepositoryRowDto {
  id: string;
  project: string;
  group: string;
  owner: ExplainableValueDto<string>;
  systems: ExplainableAggregateDto;
  contexts: ExplainableAggregateDto;
  packageRoots: ExplainableAggregateDto;
  entrypoints: ExplainableAggregateDto;
  runtimeMappings: ExplainableAggregateDto;
  modules: ExplainableAggregateDto;
  codeSearchScopes: ExplainableAggregateDto;
  codeSearchRoles: ExplainableAggregateDto;
  handoffReadiness: ExplainableAggregateDto;
  validation: ExplainableAggregateDto;
}

export interface OperationalContextCodeSearchScopeRowDto {
  id: string;
  name: string;
  lifecycleStatus: string;
  targets: ExplainableAggregateDto;
  repositories: ExplainableAggregateDto;
  packageHints: ExplainableAggregateDto;
  entryHints: ExplainableAggregateDto;
  dataHints: ExplainableAggregateDto;
  workflowHints: ExplainableAggregateDto;
  strategy: ExplainableAggregateDto;
  validation: ExplainableAggregateDto;
}

export interface OperationalContextProcessRowDto {
  id: string;
  name: string;
  owner: ExplainableValueDto<string>;
  purpose: string;
  systems: ExplainableAggregateDto;
  externalSystems: ExplainableAggregateDto;
  repositories: ExplainableAggregateDto;
  contexts: ExplainableAggregateDto;
  steps: ExplainableAggregateDto;
  completionSignals: ExplainableAggregateDto;
  handoffHints: ExplainableAggregateDto;
  validation: ExplainableAggregateDto;
}

export interface OperationalContextIntegrationRowDto {
  id: string;
  name: string;
  sourceSystem: string;
  targetSystems: string;
  owner: ExplainableValueDto<string>;
  partnerTeams: ExplainableAggregateDto;
  protocols: string;
  integrationStyle: string;
  processes: ExplainableAggregateDto;
  contexts: ExplainableAggregateDto;
  signals: ExplainableAggregateDto;
  handoffReadiness: ExplainableAggregateDto;
  validation: ExplainableAggregateDto;
}

export interface OperationalContextBoundedContextRowDto {
  id: string;
  name: string;
  owner: ExplainableValueDto<string>;
  purpose: string;
  systems: ExplainableAggregateDto;
  terms: ExplainableAggregateDto;
  relations: ExplainableAggregateDto;
  runtimeSignals: ExplainableAggregateDto;
  validation: ExplainableAggregateDto;
}

export interface OperationalContextTeamRowDto {
  id: string;
  name: string;
  purpose: string;
  ownsSystems: ExplainableAggregateDto;
  ownsRepositories: ExplainableAggregateDto;
  ownsProcesses: ExplainableAggregateDto;
  ownsContexts: ExplainableAggregateDto;
  ownsIntegrations: ExplainableAggregateDto;
  signals: ExplainableAggregateDto;
  handoffReadiness: ExplainableAggregateDto;
  validation: ExplainableAggregateDto;
}

export interface OperationalContextGlossaryRowDto {
  id: string;
  term: string;
  category: string;
  definition: string;
  matchSignals: ExplainableAggregateDto;
  canonicalReferences: ExplainableAggregateDto;
}

export interface OperationalContextHandoffRuleRowDto {
  id: string;
  title: string;
  routeTo: string;
  useWhen: ExplainableAggregateDto;
  requiredEvidence: ExplainableAggregateDto;
  expectedFirstAction: string;
  partnerTeams: ExplainableAggregateDto;
}

export interface OperationalContextSearchResultDto {
  type: string;
  id: string;
  label: string;
  subtitle: string;
  confidence: 'high' | 'medium' | 'low' | string;
  matchedFields: string[];
  why: string;
  actions: Record<string, string>;
}

export interface OperationalContextDetailSectionDto {
  title: string;
  fields: Record<string, unknown>;
}

export interface OperationalContextExplainabilitySectionDto {
  title: string;
  summary: string;
  confidence: string;
  reasons: ExplanationReasonDto[];
  warnings: string[];
  sourceRefs: SourceReferenceDto[];
}

export interface OperationalContextEntityDetailDto {
  type: string;
  id: string;
  title: string;
  subtitle: string;
  overviewSections: OperationalContextDetailSectionDto[];
  relatedEntities: ExplainableBreakdownGroupDto[];
  recognitionSignals: ExplainableBreakdownGroupDto[];
  explainabilitySections: OperationalContextExplainabilitySectionDto[];
  validationFindings: ValidationFindingDto[];
  openQuestions: OpenQuestionDto[];
  sourceReferences: SourceReferenceDto[];
  rawSourcePreview: string;
}

export interface ReadModelEntityRef {
  type: string;
  id: string;
  label?: string | null;
  lifecycleStatus?: string | null;
  summary?: string | null;
}

export interface ReadModelEntityKey {
  type: string;
  id: string;
}

export interface ReadModelSourceRef {
  file: string;
  entityType: string;
  entityId: string;
  fieldPath: string;
  relationRole?: string | null;
}

export interface ReadModelProvenance {
  canonical: boolean;
  derivation: string;
  confidence: string;
  sourceRefs: ReadModelSourceRef[];
  warnings: string[];
}

export interface ReadModelValidationFinding {
  severity: string;
  code: string;
  message: string;
  sourceRefs: ReadModelSourceRef[];
}

export interface ReadModelRelation {
  relationType: string;
  direction: string;
  source: ReadModelEntityKey;
  target: ReadModelEntityKey;
  role?: string | null;
  canonicalOwner?: ReadModelEntityKey | null;
  derived: boolean;
  provenance: ReadModelProvenance;
}

export interface OperationalContextEntityRelationsReadModelDto {
  contract: string;
  contractVersion: number;
  analysisTarget: ReadModelEntityRef;
  outgoingRelations: ReadModelRelation[];
  incomingRelations: ReadModelRelation[];
  neighbors: ReadModelEntityRef[];
  validationFindings: ReadModelValidationFinding[];
}

export interface OperationalContextCodeSearchReadModel {
  contract: string;
  contractVersion: number;
  analysisTarget: ReadModelEntityRef;
  scopes: Array<Record<string, unknown>>;
  repositories: Array<Record<string, unknown>>;
  aggregatedHints?: Record<string, unknown>;
  limitations: string[];
  validationFindings: ReadModelValidationFinding[];
}

export interface OperationalContextImplementationReadModel {
  contract: string;
  contractVersion: number;
  analysisTarget: ReadModelEntityRef;
  implementations: Array<{
    id: string;
    implementationKind?: string;
    lifecycleRole?: string;
    migrationStatus?: string;
    repository?: ReadModelEntityRef | null;
    codeSearchScope?: ReadModelEntityRef | null;
  }>;
  limitations: string[];
  validationFindings: ReadModelValidationFinding[];
}

export interface OperationalContextFlowReadModel {
  contract: string;
  contractVersion: number;
  analysisTarget: ReadModelEntityRef;
  trigger?: Record<string, unknown>;
  steps: Array<{
    id: string;
    order: number;
    name: string;
    kind: string;
    systems: ReadModelEntityRef[];
    boundedContexts: ReadModelEntityRef[];
    integrations: ReadModelEntityRef[];
    dataStores: ReadModelEntityRef[];
    implementations: Array<{ id: string; lifecycleRole?: string; migrationStatus?: string }>;
  }>;
  edges: Array<Record<string, unknown>>;
  involvedSystems: ReadModelEntityRef[];
  involvedBoundedContexts: ReadModelEntityRef[];
  involvedIntegrations: ReadModelEntityRef[];
  involvedDataStores: ReadModelEntityRef[];
  limitations: string[];
  validationFindings: ReadModelValidationFinding[];
}

export interface OperationalContextBlastRadiusReadModel {
  contract: string;
  contractVersion: number;
  analysisTarget: ReadModelEntityRef;
  impactedFlows: Array<{
    flow: ReadModelEntityRef;
    impactedSteps: Array<{ stepId: string; order: number; name: string; impactType: string }>;
    confidence: string;
    reasons: string[];
  }>;
  impactedSystems: Array<{ entity: ReadModelEntityRef; impactType: string; confidence: string }>;
  impactedBoundedContexts: Array<{ entity: ReadModelEntityRef; impactType: string; confidence: string }>;
  impactedIntegrations: Array<{ entity: ReadModelEntityRef; impactType: string; confidence: string }>;
  impactedDataStores: Array<{ entity: ReadModelEntityRef; impactType: string; confidence: string }>;
  impactedImplementations: Array<{ implementation: { id: string }; impactType: string; confidence: string }>;
  suggestedNextEvidence: string[];
  limitations: string[];
  validationFindings: ReadModelValidationFinding[];
}

export interface OperationalContextReadModelBundle {
  relations?: OperationalContextEntityRelationsReadModelDto | null;
  codeSearch?: OperationalContextCodeSearchReadModel | null;
  implementations?: OperationalContextImplementationReadModel | null;
  flow?: OperationalContextFlowReadModel | null;
  blastRadius?: OperationalContextBlastRadiusReadModel | null;
}

export type OperationalContextReadModelProfile = 'default' | 'expanded';

export type OperationalContextAiApiPreviewEndpointKey =
  | 'search'
  | 'entity'
  | 'relations'
  | 'code-search'
  | 'implementations'
  | 'flow'
  | 'blast-radius';

export interface OperationalContextReadModelLinkDto {
  rel: string;
  href: string;
  profile: string;
  reason: string;
}

export interface OperationalContextReadModelNextReadDto {
  label: string;
  rel: string;
  href: string | null;
  profile: string | null;
  tool: string | null;
  arguments: Record<string, unknown>;
  reason: string;
}

export interface OperationalContextReadModelTruncationDto {
  truncated: boolean;
  reason: string;
  returnedCounts: Record<string, number>;
  omittedCounts: Record<string, number>;
}

export interface OperationalContextProfiledReadModelDto {
  contract: string;
  contractVersion: number;
  profile: string;
  analysisTarget: unknown;
  data: Record<string, unknown>;
  links: OperationalContextReadModelLinkDto[];
  availableExpansions: string[];
  suggestedNextReads: string[];
  nextReads?: OperationalContextReadModelNextReadDto[];
  suggestedTools: string[];
  reasonToExpand: string | null;
  omittedBecause: string[];
  truncation: OperationalContextReadModelTruncationDto | null;
  relevanceScore: number | null;
  confidence: string | null;
  limitations: string[];
  provenance: unknown;
  sourceRefs: unknown[];
  validationFindings: unknown[];
}

export interface OperationalContextAiApiPreviewEndpoint {
  key: OperationalContextAiApiPreviewEndpointKey;
  label: string;
  url: string;
  payload: OperationalContextProfiledReadModelDto | null;
  error: string | null;
}

export interface OperationalContextAiApiPreview {
  profile: OperationalContextReadModelProfile;
  endpoints: OperationalContextAiApiPreviewEndpoint[];
}

export interface OperationalContextAiSearchPreview {
  query: string;
  profile: OperationalContextReadModelProfile;
  url: string;
  payload: OperationalContextProfiledReadModelDto | null;
  error: string | null;
}

export type OperationalContextCatalogRow =
  | OperationalContextSystemRowDto
  | OperationalContextRepositoryRowDto
  | OperationalContextCodeSearchScopeRowDto
  | OperationalContextProcessRowDto
  | OperationalContextIntegrationRowDto
  | OperationalContextBoundedContextRowDto
  | OperationalContextTeamRowDto
  | OperationalContextGlossaryRowDto
  | OperationalContextHandoffRuleRowDto;
