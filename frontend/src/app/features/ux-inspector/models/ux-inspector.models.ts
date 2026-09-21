import {
  AnalysisAiActivityEvent,
  AnalysisAiToolFeedback,
  AnalysisAiUsage,
  AnalysisChatMessageResponse,
  AnalysisEvidenceSection,
  AnalysisJobStepResponse,
  AnalysisReport,
  AnalysisReportReference
} from '../../../core/models/analysis.models';

export const UX_INSPECTOR_CAPTURE_SCHEMA = 'tdw.ux-inspector-capture' as const;
export const UX_INSPECTOR_CAPTURE_VERSION = 1 as const;
export const UX_INSPECTOR_CLIENT_NAME = 'TDW UX Inspector' as const;
export const UX_INSPECTOR_FEATURE_ID = 'ux-inspector' as const;
export const UX_INSPECTOR_PROTOCOL_VERSION = 1 as const;
export const UX_INSPECTOR_MAX_CAPTURE_BYTES = 128 * 1024;
export type UxInspectorCaptureProfile = 'ELEMENT_CONTEXT' | 'FORM_DIAGNOSTICS';

export type UxInspectorLoadingState = 'idle' | 'loading' | 'ready' | 'empty' | 'error';
export type UxInspectorTargetResolutionStatus = 'RESOLVED' | 'AMBIGUOUS' | 'NOT_FOUND';
export type UxInspectorJobStatus =
  | 'QUEUED'
  | 'RESOLVING_TARGET'
  | 'PREPARING_AI'
  | 'ANALYZING'
  | 'COMPLETED'
  | 'PARTIAL'
  | 'BLOCKED'
  | 'FAILED'
  | string;

export interface UxInspectorCapture {
  schema: typeof UX_INSPECTOR_CAPTURE_SCHEMA;
  version: typeof UX_INSPECTOR_CAPTURE_VERSION;
  captureId: string;
  capturedAt: string;
  captureProfile: UxInspectorCaptureProfile;
  page: {
    origin: string;
    path: string;
    title: string | null;
    language: string | null;
    queryParameterNames: string[];
  };
  target: {
    tag: string;
    role: string | null;
    accessibleName: string | null;
    text: string | null;
    domFingerprint: {
      stableAttributes: Record<string, string>;
      selectorCandidates: string[];
      componentBoundaryTags: string[];
      labelFor: string | null;
    };
    state: {
      disabled: boolean;
      ariaDisabled: boolean;
      readOnly: boolean;
      required: boolean;
      invalid: boolean;
      checked: boolean | null;
      expanded: boolean | null;
      hidden: boolean;
    };
    bounds: { x: number; y: number; width: number; height: number };
  };
  ancestors: Array<{
    depth: number;
    tag: string;
    role: string | null;
    accessibleName: string | null;
    text: string | null;
    stableAttributes: Record<string, string>;
  }>;
  formSnapshot: {
    source: 'NEAREST_FORM' | 'SELECTED_CONTROL_ONLY';
    stableAttributes: Record<string, string>;
    selectorCandidates: string[];
    valid: boolean | null;
    observedControlCount: number;
    emittedControlCount: number;
    omittedControlCount: number;
    controls: Array<{
      selectedTarget: boolean;
      tag: string;
      type: string | null;
      name: string | null;
      formControlName: string | null;
      accessibleName: string | null;
      stableAttributes: Record<string, string>;
      value: string | null;
      valueTruncated: boolean;
      checked: boolean | null;
      selectedValues: string[];
      selectedLabels: string[];
      disabled: boolean;
      readOnly: boolean;
      required: boolean;
      validity: {
        valid: boolean;
        valueMissing: boolean;
        typeMismatch: boolean;
        patternMismatch: boolean;
        tooShort: boolean;
        tooLong: boolean;
        rangeUnderflow: boolean;
        rangeOverflow: boolean;
        stepMismatch: boolean;
        badInput: boolean;
        customError: boolean;
        validationMessage: string | null;
      } | null;
    }>;
    submitters: Array<{
      selectedTarget: boolean;
      tag: string;
      type: string | null;
      accessibleName: string | null;
      stableAttributes: Record<string, string>;
      disabled: boolean;
    }>;
    excludedControls: Array<{
      tag: string;
      type: string | null;
      name: string | null;
      formControlName: string | null;
      reason: string;
    }>;
    valueCharacters: number;
    valuesTruncated: boolean;
  } | null;
  traversal: {
    observedDepth: number;
    emittedNodeCount: number;
    omittedNodeCount: number;
    reachedDocumentRoot: boolean;
  };
  signals: {
    shadowBoundaryCount: number;
    frame: string;
    redactions: string[];
  };
  limits: string[];
  client: {
    name: typeof UX_INSPECTOR_CLIENT_NAME;
    version: string;
    featureId: typeof UX_INSPECTOR_FEATURE_ID;
  };
}

export interface UxInspectorSystemOption {
  systemId: string;
  label: string;
  summary: string;
  defaultBranch: string;
}

export interface UxInspectorInputOptionsResponse {
  featureId: typeof UX_INSPECTOR_FEATURE_ID;
  systems: UxInspectorSystemOption[];
  configurationFindings: Array<{
    severity: string;
    code: string;
    message: string;
    entityType: string;
    entityId: string;
  }>;
}

export interface UxInspectorSourceRevision {
  branch: string;
  revision: string;
}

export interface UxInspectorViewOption {
  viewId: string;
  label: string;
  routePattern: string;
  componentSelectors: string[];
  status: string;
  limitations: string[];
}

export interface UxInspectorViewCatalogResponse {
  systemId: string;
  systemLabel: string;
  sourceRevision: UxInspectorSourceRevision;
  status: string;
  views: UxInspectorViewOption[];
  diagnostics: Array<{
    severity: string;
    code: string;
    message: string;
    sourcePath: string;
  }>;
  limitations: string[];
}

export interface UxInspectorJobStartRequest {
  systemId: string;
  branch: string;
  viewId: string;
  sourceRevision: string;
  question: string;
  capture: UxInspectorCapture;
  model: string;
  reasoningEffort?: string;
}

export interface UxInspectorJobRequestSnapshot {
  systemId: string;
  systemLabel: string;
  branch: string;
  viewId: string;
  sourceRevision: string;
  question: string;
  capture: UxInspectorCapture;
  aiModel: string | null;
  reasoningEffort: string | null;
  targetResolutionStatus: UxInspectorTargetResolutionStatus | null;
  targetCandidateCount: number;
}

export interface UxInspectorResultResponse {
  captureId: string;
  targetLabel: string;
  view: { viewId: string; label: string; routePattern: string };
  sourceRevision: UxInspectorSourceRevision;
  resolutionStatus: UxInspectorTargetResolutionStatus;
  thesis: string;
  answer: string;
  confidence: string;
  sourceReferences: AnalysisReportReference[];
  visibilityLimits: string[];
  openQuestions: string[];
  usage: AnalysisAiUsage | null;
}

export interface UxInspectorJobStateSnapshot {
  jobId: string;
  request: UxInspectorJobRequestSnapshot;
  status: UxInspectorJobStatus;
  currentStepCode: string | null;
  currentStepLabel: string | null;
  errorCode: string | null;
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  steps: AnalysisJobStepResponse[];
  contextSections: AnalysisEvidenceSection[];
  toolEvidenceSections: AnalysisEvidenceSection[];
  aiActivityEvents: AnalysisAiActivityEvent[];
  toolFeedback: AnalysisAiToolFeedback[];
  preparedPrompt: string | null;
  result: UxInspectorResultResponse | null;
  report: AnalysisReport | null;
  usage: AnalysisAiUsage | null;
  sourceRevision: UxInspectorSourceRevision | null;
  outputAvailability: {
    status: 'AVAILABLE' | 'BLOCKED';
    code: string;
    message: string;
    missingCapabilities: string[];
  };
  exportAvailable: boolean;
  chatMessages?: AnalysisChatMessageResponse[];
  chatAvailability?: {
    available: boolean;
    code: string | null;
    message: string | null;
  };
}

export interface UxInspectorExportEnvelope {
  schema: 'tdw.ux-inspector-export';
  version: 1 | 2;
  exportedAt: string;
  payload: {
    type: 'ux-inspector-analysis';
    resultContract: 'ux-inspector-result-v1' | 'ux-inspector-result-v2';
    job: UxInspectorJobStateSnapshot;
  };
}

export interface UxInspectorResultSource {
  origin: 'live' | 'history' | 'imported';
  fileName: string;
  localRunId?: string;
  localRunName?: string;
  continuationEnabled?: boolean;
}

export type UxInspectorIngressStatus =
  | 'idle'
  | 'waiting'
  | 'received'
  | 'invalid'
  | 'unavailable';
