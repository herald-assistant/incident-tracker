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
  prompt: string;
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

export interface AnalysisJobResponse {
  analysisId: string;
  correlationId: string;
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
