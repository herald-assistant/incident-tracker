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

export type AnalysisMode = 'CONSERVATIVE' | 'EXPLORATORY' | string;

export type AnalysisVariantStatus =
  | 'COMPLETED'
  | 'FAILED'
  | 'DISABLED'
  | 'SKIPPED'
  | string;

export type AnalysisProblemNature = 'CONFIRMED' | 'HYPOTHESIS' | string;

export type AnalysisConfidence = 'HIGH' | 'MEDIUM' | 'LOW' | string;

export interface AnalysisFlowDiagramMetadata {
  name: string;
  value: string;
}

export interface AnalysisFlowDiagramNode {
  id: string;
  kind: string;
  title: string;
  componentName: string;
  factStatus: string;
  firstSeenAt: string;
  metadata: AnalysisFlowDiagramMetadata[];
  errorSource: boolean;
}

export interface AnalysisFlowDiagramEdge {
  id: string;
  fromNodeId: string;
  toNodeId: string;
  sequence: number;
  interactionType: string;
  factStatus: string;
  startedAt: string;
  durationMs: number | null;
  supportSummary: string;
}

export interface AnalysisFlowDiagram {
  nodes: AnalysisFlowDiagramNode[];
  edges: AnalysisFlowDiagramEdge[];
}

export interface AnalysisVariantResultResponse {
  mode: AnalysisMode;
  status: AnalysisVariantStatus;
  detectedProblem: string;
  summary: string;
  recommendedAction: string;
  rationale: string;
  problemNature: AnalysisProblemNature;
  confidence: AnalysisConfidence | null;
  prompt: string;
  diagram: AnalysisFlowDiagram | null;
}

export interface AnalysisResultVariants {
  conservative: AnalysisVariantResultResponse;
  exploratory: AnalysisVariantResultResponse;
}

export interface AnalysisResultResponse {
  status: string;
  correlationId: string;
  environment: string;
  gitLabBranch: string;
  variants: AnalysisResultVariants;
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
  variantMode?: AnalysisMode | null;
  preparedPrompt?: string | null;
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
