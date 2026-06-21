import {
  AnalysisAiActivityEvent,
  AnalysisChatMessageResponse,
  AnalysisJobStepResponse,
  AnalysisAiToolFeedback,
  AnalysisAiUsage,
  AnalysisEvidenceSection
} from '../../../core/models/analysis.models';

export type FlowExplorerDocumentationPreset =
  | 'ANALYST_OVERVIEW'
  | 'TEST_PREPARATION'
  | 'CHANGE_IMPACT'
  | 'TECHNICAL_HANDOFF';

export type FlowExplorerFocusArea =
  | 'BUSINESS_FLOW'
  | 'VALIDATIONS'
  | 'PERSISTENCE'
  | 'EXTERNAL_INTEGRATIONS'
  | 'TEST_SCENARIOS'
  | 'RISKS_AND_OPEN_QUESTIONS';

export type FlowExplorerJobStatus =
  | 'QUEUED'
  | 'COLLECTING_CONTEXT'
  | 'ANALYZING'
  | 'COMPLETED'
  | 'FAILED'
  | string;

export interface FlowExplorerSystemOption {
  systemId: string;
  name: string;
  shortName: string;
  kind: string;
  lifecycleStatus: string;
  operationalStatus: string;
  criticality: string;
  summary: string;
  aliases: string[];
  repositoryCount: number;
  codeSearchScopeCount: number;
  ownerTeamIds: string[];
}

export interface FlowExplorerConfig {
  defaultBranch: string;
}

export interface FlowExplorerEndpointInventoryQuery {
  branch?: string;
  endpointPathPrefix?: string;
  httpMethod?: string;
}

export interface FlowExplorerEndpointInventoryResponse {
  systemId: string;
  requestedBranch: string;
  resolvedRef: string;
  gitLabGroup: string;
  endpointPathPrefix: string;
  httpMethod: string;
  repositoryCount: number;
  scannedRepositoryCount: number;
  endpointCount: number;
  candidateFileCount: number;
  scannedFileCount: number;
  scannedFileLimitReached: boolean;
  repositories: FlowExplorerRepositoryInventory[];
  endpoints: FlowExplorerEndpointOption[];
  limitations: string[];
}

export interface FlowExplorerRepositoryInventory {
  repositoryId: string;
  projectName: string;
  projectPath: string;
  resolvedRef: string;
  candidateFileCount: number;
  scannedFileCount: number;
  scannedFileLimitReached: boolean;
  endpointCount: number;
  limitations: string[];
}

export interface FlowExplorerEndpointOption {
  endpointId: string;
  method: string;
  methods: string[];
  path: string;
  pathExpression: string;
  summary: string;
  description: string;
  operationId: string;
  tags: string[];
  controllerClass: string;
  handlerMethod: string;
  source: FlowExplorerEndpointSource | null;
  parameters: FlowExplorerEndpointParameter[];
  confidence: string;
  limitations: string[];
  suggestedNextReads: string[];
  tooltipDetails: FlowExplorerEndpointTooltipDetails | null;
}

export interface FlowExplorerEndpointSource {
  repositoryId: string;
  projectName: string;
  projectPath: string;
  filePath: string;
  lineStart: number;
  lineEnd: number;
}

export interface FlowExplorerEndpointParameter {
  name: string;
  in: string;
  required: boolean;
  type: string;
  description: string;
}

export interface FlowExplorerEndpointTooltipDetails {
  documentationSource: string;
  summary: string;
  description: string;
  operationId: string;
  tags: string[];
  parameters: FlowExplorerEndpointParameter[];
  requestTypes: string[];
  responseTypes: string[];
  annotations: string[];
  limitations: string[];
  suggestedNextReads: string[];
}

export interface FlowExplorerJobStartRequest {
  systemId: string;
  endpointId?: string;
  httpMethod?: string;
  endpointPath?: string;
  branch?: string;
  documentationPreset?: FlowExplorerDocumentationPreset;
  focusAreas?: FlowExplorerFocusArea[];
  userInstructions?: string;
  model?: string;
  reasoningEffort?: string;
}

export interface FlowExplorerChatMessageRequest {
  message: string;
}

export interface FlowExplorerJobStateSnapshot {
  jobId: string;
  systemId: string;
  endpointId: string;
  httpMethod: string;
  endpointPath: string;
  branch: string;
  documentationPreset: FlowExplorerDocumentationPreset;
  focusAreas: FlowExplorerFocusArea[];
  aiModel: string;
  reasoningEffort: string;
  status: FlowExplorerJobStatus;
  currentStepCode: string;
  currentStepLabel: string;
  errorCode: string;
  errorMessage: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string;
  steps: AnalysisJobStepResponse[];
  contextSnapshot: unknown | null;
  contextSections: AnalysisEvidenceSection[];
  toolEvidenceSections: AnalysisEvidenceSection[];
  aiActivityEvents: AnalysisAiActivityEvent[];
  toolFeedback: AnalysisAiToolFeedback[];
  chatMessages: AnalysisChatMessageResponse[];
  preparedPrompt: string;
  result: FlowExplorerResult | null;
}

export interface FlowExplorerResult {
  status: string;
  systemId: string;
  endpointId: string;
  httpMethod: string;
  endpointPath: string;
  branch: string;
  userIntentSummary: string;
  audienceSummary: string;
  confidence: string;
  visibilityLimits: string[];
  prompt: string;
  aiResponse: FlowExplorerAiResponse | null;
  usage: AnalysisAiUsage | null;
}

export interface FlowExplorerAiResponse {
  userIntentSummary: string;
  audienceSummary: string;
  endpointContract: FlowExplorerAiEndpointContract | null;
  flowSteps: FlowExplorerAiFlowStep[];
  businessRules: string[];
  validations: string[];
  persistence: string[];
  externalIntegrations: string[];
  testScenarios: string[];
  risksAndEdgeCases: string[];
  openQuestions: string[];
  visibilityLimits: string[];
  sourceReferences: string[];
  confidence: string;
}

export interface FlowExplorerAiEndpointContract {
  method: string;
  path: string;
  purpose: string;
  request: string[];
  response: string[];
  parameters: string[];
}

export interface FlowExplorerAiFlowStep {
  order: number;
  title: string;
  plainLanguage: string;
  technicalGrounding: string;
  sourceRefs: string[];
}
