export type AssessmentTrendSource =
  | 'DELIVERY_COMPLEXITY_ASSESSMENT'
  | 'DELIVERY_SCOPE_COMPLEXITY';

export type AssessmentTrendGranularity = 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER';

export type AssessmentTrendDimensionMode = 'TOTAL' | 'AVERAGE';

export interface AssessmentTrendAuthor {
  key: string;
  id: string | null;
  name: string;
  usesNameFallback: boolean;
}

export interface AssessmentTrendIssueRow {
  source: AssessmentTrendSource;
  issueKey: string;
  issueUrl: string;
  summary: string;
  issueType: string;
  doneAt: string;
  doneDate: string;
  teamKey: string | null;
  teamId: string | null;
  teamName: string | null;
  teamFieldId: string | null;
  authors: AssessmentTrendAuthor[];
  deliveryUnitId: string;
  assessmentStatus: string;
  finalPoints: number | null;
  aggregationPoints: number | null;
  dimensionValues: Readonly<Record<string, number | null>>;
  sourceFileName: string;
  sourceFileIndex: number;
  sourceRowNumber: number;
}

export interface AssessmentTrendImportedFile {
  name: string;
  rowCount: number;
  hasAuthorColumns: boolean;
}

export interface AssessmentTrendImportQuality {
  inputRows: number;
  uniqueIssues: number;
  duplicatesRemoved: number;
  conflictingDuplicates: number;
  ignoredEmptyRows: number;
  legacyFilesWithoutAuthors: number;
  rowsWithoutAuthors: number;
  authorsUsingNameFallback: number;
}

export interface AssessmentTrendDataset {
  source: AssessmentTrendSource;
  metricLabel: string;
  metricShortLabel: string;
  files: AssessmentTrendImportedFile[];
  rows: AssessmentTrendIssueRow[];
  quality: AssessmentTrendImportQuality;
}

export interface AssessmentTrendFilterOption {
  key: string;
  label: string;
  unitCount: number;
}

export interface AssessmentTrendFilters {
  granularity: AssessmentTrendGranularity;
  teamKey: string;
  authorKey: string;
  fromDate: string;
  toDate: string;
}

export interface AssessmentTrendPeriod {
  key: string;
  label: string;
  points: number;
  delta: number | null;
  deltaPercent: number | null;
  direction: 'UP' | 'DOWN' | 'FLAT' | 'NONE';
  unitCount: number;
  issueCount: number;
  dimensions: AssessmentTrendPeriodDimension[];
}

export interface AssessmentTrendDimensionDefinition {
  key: string;
  label: string;
  averageMaximum: number;
}

export interface AssessmentTrendPeriodDimension {
  key: string;
  total: number | null;
  average: number | null;
  sampleUnitCount: number;
  totalDelta: number | null;
  averageDelta: number | null;
}

export interface AssessmentTrendDimensionChange {
  dimensionKey: string;
  dimensionLabel: string;
  periodLabel: string;
  delta: number;
}

export interface AssessmentTrendStatusCount {
  status: string;
  count: number;
}

export interface AssessmentTrendHighlight {
  label: string;
  periodLabel: string;
  value: number;
  kind: 'PEAK' | 'INCREASE' | 'DECREASE';
}

export interface AssessmentTrendAggregationQuality {
  unitsUsingFinalScoreFallback: number;
  unitsWithMultipleAggregationAnchors: number;
  unitsWithConflictingFinalScores: number;
  unitsWithIncompleteDimensions: number;
}

export interface AssessmentTrendView {
  periods: AssessmentTrendPeriod[];
  totalPoints: number;
  scoredUnitCount: number;
  totalUnitCount: number;
  issueCount: number;
  statusCounts: AssessmentTrendStatusCount[];
  teamOptions: AssessmentTrendFilterOption[];
  authorOptions: AssessmentTrendFilterOption[];
  highlights: AssessmentTrendHighlight[];
  dimensionDefinitions: AssessmentTrendDimensionDefinition[];
  dimensionDrivers: Record<AssessmentTrendDimensionMode, AssessmentTrendDimensionChange | null>;
  quality: AssessmentTrendAggregationQuality;
}
