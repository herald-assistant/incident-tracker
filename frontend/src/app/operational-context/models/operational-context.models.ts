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
  type: string;
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
  type: string;
  owner: ExplainableValueDto<string>;
  purpose: string;
  repos: ExplainableAggregateDto;
  processes: ExplainableAggregateDto;
  contexts: ExplainableAggregateDto;
  integrations: ExplainableAggregateDto;
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
  processes: ExplainableAggregateDto;
  contexts: ExplainableAggregateDto;
  packageRoots: ExplainableAggregateDto;
  entrypoints: ExplainableAggregateDto;
  runtimeMappings: ExplainableAggregateDto;
  modules: ExplainableAggregateDto;
  handoffReadiness: ExplainableAggregateDto;
  validation: ExplainableAggregateDto;
}

export interface OperationalContextProcessRowDto {
  id: string;
  name: string;
  owner: ExplainableValueDto<string>;
  purpose: string;
  systems: ExplainableAggregateDto;
  externalSystems: ExplainableAggregateDto;
  repos: ExplainableAggregateDto;
  contexts: ExplainableAggregateDto;
  steps: ExplainableAggregateDto;
  completionSignals: ExplainableAggregateDto;
  handoffHints: ExplainableAggregateDto;
  validation: ExplainableAggregateDto;
}

export interface OperationalContextIntegrationRowDto {
  id: string;
  name: string;
  from: string;
  to: string;
  owner: ExplainableValueDto<string>;
  partnerTeams: ExplainableAggregateDto;
  protocol: string;
  type: string;
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
  repos: ExplainableAggregateDto;
  processes: ExplainableAggregateDto;
  terms: ExplainableAggregateDto;
  relations: ExplainableAggregateDto;
  runtimeFingerprints: ExplainableAggregateDto;
  validation: ExplainableAggregateDto;
}

export interface OperationalContextTeamRowDto {
  id: string;
  name: string;
  purpose: string;
  ownsSystems: ExplainableAggregateDto;
  ownsRepos: ExplainableAggregateDto;
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
  typicalEvidenceSignals: ExplainableAggregateDto;
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

export type OperationalContextCatalogRow =
  | OperationalContextSystemRowDto
  | OperationalContextRepositoryRowDto
  | OperationalContextProcessRowDto
  | OperationalContextIntegrationRowDto
  | OperationalContextBoundedContextRowDto
  | OperationalContextTeamRowDto
  | OperationalContextGlossaryRowDto
  | OperationalContextHandoffRuleRowDto;
