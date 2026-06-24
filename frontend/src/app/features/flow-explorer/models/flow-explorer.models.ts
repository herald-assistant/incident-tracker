import {
  AnalysisAiActivityEvent,
  AnalysisChatMessageResponse,
  AnalysisJobStepResponse,
  AnalysisAiToolFeedback,
  AnalysisAiUsage,
  AnalysisEvidenceSection
} from '../../../core/models/analysis.models';

export type FlowExplorerAnalysisGoal =
  | 'DEEP_DISCOVERY'
  | 'TEST_SCENARIOS'
  | 'RISK_DETECTION';

export type FlowExplorerFocusArea =
  | 'FUNCTIONAL_FLOW'
  | 'VALIDATIONS'
  | 'PERSISTENCE'
  | 'INTEGRATIONS';

export type FlowExplorerResultSectionId =
  | 'FUNCTIONAL_FLOW'
  | 'VALIDATIONS'
  | 'PERSISTENCE'
  | 'INTEGRATIONS';

export type FlowExplorerSectionMode = 'OFF' | 'COMPACT' | 'DEEP';
export type FlowExplorerResultSectionMode =
  | FlowExplorerSectionMode
  | 'off'
  | 'compact'
  | 'deep';

export interface FlowExplorerSectionModeRequest {
  id: FlowExplorerResultSectionId;
  mode: FlowExplorerSectionMode;
}

export interface FlowExplorerSectionModeAssignment {
  id: FlowExplorerResultSectionId;
  title: string;
  mode: FlowExplorerSectionMode;
}

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
  goal?: FlowExplorerAnalysisGoal;
  focusAreas?: FlowExplorerFocusArea[];
  sectionModes?: FlowExplorerSectionModeRequest[];
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
  goal: FlowExplorerAnalysisGoal;
  focusAreas: FlowExplorerFocusArea[];
  sectionModes: FlowExplorerSectionModeAssignment[];
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
  goal: FlowExplorerAnalysisGoal;
  prompt: string;
  aiResponse: FlowExplorerAiResponse | null;
  usage: AnalysisAiUsage | null;
}

export interface FlowExplorerAiResponse {
  goal: FlowExplorerAnalysisGoal;
  audience: string;
  overview: FlowExplorerResultOverview;
  sections: FlowExplorerResultSection[];
  globalVisibilityLimits: string[];
  globalOpenQuestions: string[];
  sourceReferences: string[];
  confidence: string;
}

export interface FlowExplorerResultOverview {
  markdown: string;
  confidence: string;
  sourceRefs: string[];
}

export interface FlowExplorerResultSection {
  id: FlowExplorerResultSectionId;
  title: string;
  mode: FlowExplorerResultSectionMode;
  markdown: string;
  sourceRefs: string[];
  visibilityLimits: string[];
  openQuestions: string[];
}
