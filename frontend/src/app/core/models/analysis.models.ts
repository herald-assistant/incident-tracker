export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorResponse {
  code: string;
  message: string;
  fieldErrors: ApiFieldError[];
}

export type AnalysisJobStatus =
  | 'QUEUED'
  | 'COLLECTING_EVIDENCE'
  | 'ANALYZING'
  | 'COMPLETED'
  | 'FAILED'
  | 'NOT_FOUND'
  | string;

export type AnalysisJobStepStatus =
  | 'PENDING'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'FAILED'
  | 'SKIPPED'
  | string;

export interface AnalysisResultResponse {
  status: string;
  correlationId: string;
  environment: string;
  gitLabBranch: string;
  summary: string;
  detectedProblem: string;
  recommendedAction: string;
  rationale: string;
  affectedFunction: string;
  affectedProcess: string;
  affectedBoundedContext: string;
  affectedTeam: string;
  prompt: string;
}

export interface AnalysisStartRequest {
  correlationId: string;
  model?: string;
  reasoningEffort?: string;
}

export interface AnalysisChatMessageRequest {
  message: string;
}

export interface AnalysisAiModelOption {
  id: string;
  name: string;
  supportsReasoningEffort: boolean;
  reasoningEfforts: string[];
  defaultReasoningEffort: string;
}

export interface AnalysisAiModelOptionsResponse {
  defaultModel: string;
  defaultReasoningEffort: string;
  defaultReasoningEfforts: string[];
  models: AnalysisAiModelOption[];
}

export interface AnalysisEvidenceAttribute {
  name: string;
  value: string;
}

export interface AnalysisEvidenceItem {
  title: string;
  attributes: AnalysisEvidenceAttribute[];
}

export interface AnalysisEvidenceSection {
  provider: string;
  category: string;
  items: AnalysisEvidenceItem[];
}

export interface AnalysisEvidenceReference {
  provider: string;
  category: string;
}

export interface AnalysisJobStepResponse {
  code: string;
  label: string;
  phase?: string;
  status: AnalysisJobStepStatus;
  message: string;
  itemCount: number | null;
  startedAt: string;
  completedAt: string;
  consumesEvidence?: AnalysisEvidenceReference[];
  producesEvidence?: AnalysisEvidenceReference[];
}

export type AnalysisChatMessageRole = 'USER' | 'ASSISTANT' | string;
export type AnalysisChatMessageStatus = 'IN_PROGRESS' | 'COMPLETED' | 'FAILED' | string;

export interface AnalysisChatMessageResponse {
  id: string;
  role: AnalysisChatMessageRole;
  status: AnalysisChatMessageStatus;
  content: string;
  errorCode: string;
  errorMessage: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string;
  toolEvidenceSections: AnalysisEvidenceSection[];
  prompt: string;
}

export interface AnalysisJobResponse {
  analysisId: string;
  correlationId: string;
  aiModel: string;
  reasoningEffort: string;
  status: AnalysisJobStatus;
  currentStepCode: string;
  currentStepLabel: string;
  environment: string;
  gitLabBranch: string;
  errorCode: string;
  errorMessage: string;
  createdAt: string;
  updatedAt: string;
  completedAt: string;
  steps: AnalysisJobStepResponse[];
  evidenceSections: AnalysisEvidenceSection[];
  toolEvidenceSections: AnalysisEvidenceSection[];
  chatMessages: AnalysisChatMessageResponse[];
  preparedPrompt: string;
  result: AnalysisResultResponse | null;
}

export interface ExportState {
  origin: 'live' | 'imported';
  exportedAt: string;
  fileName: string;
  job: AnalysisJobResponse;
}

export interface TransportErrorState {
  code: string;
  message: string;
  details: string[];
  status: number;
}

export interface AnalysisExportEnvelope {
  schema: string;
  version: number;
  exportedAt: string;
  payload: {
    type: 'analysis-job';
    job: AnalysisJobResponse;
  };
}
