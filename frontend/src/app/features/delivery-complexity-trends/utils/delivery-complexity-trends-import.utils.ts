import { CsvParseError, parseExcelCsv } from '../../../core/utils/csv-file.utils';
import {
  AssessmentTrendAuthor,
  AssessmentTrendDataset,
  AssessmentTrendImportedFile,
  AssessmentTrendIssueRow,
  AssessmentTrendSource
} from '../models/delivery-complexity-trends.models';

export const ASSESSMENT_TREND_MAX_FILES = 50;
export const ASSESSMENT_TREND_MAX_TOTAL_BYTES = 20 * 1024 * 1024;
export const ASSESSMENT_TREND_MAX_DATA_ROWS = 200_000;

const COMMON_REQUIRED_HEADERS = [
  'issueKey',
  'issueUrl',
  'summary',
  'issueType',
  'doneAt',
  'teamId',
  'teamName',
  'teamFieldId',
  'mergeRequestUrls',
  'deliveryUnitId',
  'assessmentStatus',
  'pointsForAggregation'
] as const;

const DELIVERY_COMPLEXITY_DIMENSION_HEADERS = [
  'outcomeBreadth',
  'domainDecisionComplexity',
  'applicationFlowComplexity',
  'boundaryAndDataComplexity',
  'verificationStateSpace',
  'implementedCompatibilityScope',
  'parameterizationComplexity'
] as const;

const DELIVERY_COMPLEXITY_HEADERS = [
  ...DELIVERY_COMPLEXITY_DIMENSION_HEADERS,
  'score100',
  'deliveredStoryPoints'
] as const;

const DELIVERY_SCOPE_DIMENSION_HEADERS = [
  'noveltyPoints',
  'structuralAndLogicPoints',
  'businessAndInvariantsPoints',
  'robustnessAndTestsPoints',
  'refactorAndArchitecturePoints',
  'distributionPoints'
] as const;

const DELIVERY_SCOPE_HEADERS = [
  ...DELIVERY_SCOPE_DIMENSION_HEADERS,
  'finalScore'
] as const;

const AUTHOR_ID_HEADER = 'mergeRequestAuthorIds';
const AUTHOR_NAME_HEADER = 'mergeRequestAuthorNames';
const TIME_SPENT_HEADER = 'timeSpentSeconds';
const ORIGINAL_ESTIMATE_HEADER = 'originalEstimateSeconds';
const REMAINING_ESTIMATE_HEADER = 'remainingEstimateSeconds';
const TIME_TRACKING_CAPTURED_AT_HEADER = 'timeTrackingCapturedAt';

export class AssessmentTrendImportError extends Error {
  constructor(
    readonly code: string,
    message: string
  ) {
    super(message);
    this.name = 'AssessmentTrendImportError';
  }
}

export async function importAssessmentTrendFiles(
  files: readonly File[]
): Promise<AssessmentTrendDataset> {
  validateFileSelection(files);

  const parsedFiles: ParsedAssessmentFile[] = [];
  let totalRows = 0;
  let ignoredEmptyRows = 0;

  for (let fileIndex = 0; fileIndex < files.length; fileIndex += 1) {
    const file = files[fileIndex];
    const parsed = await parseAssessmentFile(file, fileIndex);
    totalRows += parsed.rows.length;
    ignoredEmptyRows += parsed.ignoredEmptyRows;
    if (totalRows > ASSESSMENT_TREND_MAX_DATA_ROWS) {
      throw new AssessmentTrendImportError(
        'ROW_LIMIT_EXCEEDED',
        `Zestaw przekracza limit ${ASSESSMENT_TREND_MAX_DATA_ROWS.toLocaleString('pl-PL')} wierszy danych.`
      );
    }
    parsedFiles.push(parsed);
  }

  const source = parsedFiles[0].source;
  if (parsedFiles.some((file) => file.source !== source)) {
    throw new AssessmentTrendImportError(
      'MIXED_ASSESSMENTS',
      'Wszystkie pliki musza pochodzic z tego samego assessmentu.'
    );
  }

  const allRows = parsedFiles.flatMap((file) => file.rows);
  const deduplicated = deduplicateIssues(allRows);
  const fallbackAuthors = new Set(
    deduplicated.rows.flatMap((row) =>
      row.authors.filter((author) => author.usesNameFallback).map((author) => author.key)
    )
  );

  return {
    source,
    metricLabel: 'Complexity Points (CP)',
    metricShortLabel: 'CP',
    files: parsedFiles.map((file): AssessmentTrendImportedFile => ({
      name: file.name,
      rowCount: file.rows.length,
      hasAuthorColumns: file.hasAuthorColumns
    })),
    rows: deduplicated.rows,
    quality: {
      inputRows: allRows.length,
      uniqueIssues: deduplicated.rows.length,
      duplicatesRemoved: deduplicated.duplicatesRemoved,
      conflictingDuplicates: deduplicated.conflictingDuplicates,
      ignoredEmptyRows,
      legacyFilesWithoutAuthors: parsedFiles.filter((file) => !file.hasAuthorColumns).length,
      rowsWithoutAuthors: deduplicated.rows.filter((row) => row.authors.length === 0).length,
      authorsUsingNameFallback: fallbackAuthors.size
    }
  };
}

interface ParsedAssessmentFile {
  name: string;
  source: AssessmentTrendSource;
  hasAuthorColumns: boolean;
  ignoredEmptyRows: number;
  rows: AssessmentTrendIssueRow[];
}

async function parseAssessmentFile(file: File, fileIndex: number): Promise<ParsedAssessmentFile> {
  let content: string;
  try {
    content = await file.text();
  } catch {
    throw new AssessmentTrendImportError(
      'FILE_READ_FAILED',
      `Nie udalo sie odczytac pliku ${file.name}.`
    );
  }

  let matrix: string[][];
  try {
    matrix = parseExcelCsv(content);
  } catch (error) {
    const detail = error instanceof CsvParseError ? error.message : 'Nieprawidlowy format CSV.';
    throw new AssessmentTrendImportError(
      'MALFORMED_CSV',
      `Plik ${file.name} nie jest prawidlowym CSV: ${detail}`
    );
  }

  if (matrix.length === 0 || matrix[0].every((cell) => !cell.trim())) {
    throw new AssessmentTrendImportError(
      'EMPTY_FILE',
      `Plik ${file.name} nie zawiera naglowka.`
    );
  }

  const headers = matrix[0].map((header) => header.trim());
  validateHeaders(file.name, headers);
  const source = detectSource(file.name, headers);
  const headerIndex = new Map(headers.map((header, index) => [header, index]));
  const hasAuthorIds = headerIndex.has(AUTHOR_ID_HEADER);
  const hasAuthorNames = headerIndex.has(AUTHOR_NAME_HEADER);
  if (hasAuthorIds !== hasAuthorNames) {
    throw new AssessmentTrendImportError(
      'INCOMPLETE_AUTHOR_COLUMNS',
      `Plik ${file.name} musi zawierac obie kolumny autorow MR albo zadnej.`
    );
  }

  const rows: AssessmentTrendIssueRow[] = [];
  let ignoredEmptyRows = 0;
  for (let rowIndex = 1; rowIndex < matrix.length; rowIndex += 1) {
    const values = matrix[rowIndex];
    if (values.every((cell) => !cell.trim())) {
      ignoredEmptyRows += 1;
      continue;
    }
    if (values.length !== headers.length) {
      throw new AssessmentTrendImportError(
        'COLUMN_COUNT_MISMATCH',
        `Plik ${file.name}, wiersz ${rowIndex + 1}: liczba kolumn nie odpowiada naglowkowi.`
      );
    }
    rows.push(normalizeRow(
      file.name,
      fileIndex,
      rowIndex + 1,
      source,
      headerIndex,
      values,
      hasAuthorIds
    ));
  }

  return {
    name: file.name,
    source,
    hasAuthorColumns: hasAuthorIds,
    ignoredEmptyRows,
    rows
  };
}

function validateFileSelection(files: readonly File[]): void {
  if (files.length === 0) {
    throw new AssessmentTrendImportError('NO_FILES', 'Wybierz co najmniej jeden plik CSV.');
  }
  if (files.length > ASSESSMENT_TREND_MAX_FILES) {
    throw new AssessmentTrendImportError(
      'FILE_LIMIT_EXCEEDED',
      `Mozna zaladowac maksymalnie ${ASSESSMENT_TREND_MAX_FILES} plikow jednoczesnie.`
    );
  }
  const invalidFile = files.find((file) => !file.name.toLocaleLowerCase('pl').endsWith('.csv'));
  if (invalidFile) {
    throw new AssessmentTrendImportError(
      'UNSUPPORTED_FILE',
      `Plik ${invalidFile.name} nie ma rozszerzenia .csv.`
    );
  }
  const totalBytes = files.reduce((total, file) => total + file.size, 0);
  if (totalBytes > ASSESSMENT_TREND_MAX_TOTAL_BYTES) {
    throw new AssessmentTrendImportError(
      'SIZE_LIMIT_EXCEEDED',
      'Laczny rozmiar plikow przekracza limit 20 MB.'
    );
  }
}

function validateHeaders(fileName: string, headers: string[]): void {
  if (headers.some((header) => !header)) {
    throw new AssessmentTrendImportError(
      'EMPTY_HEADER',
      `Plik ${fileName} zawiera pusta nazwe kolumny.`
    );
  }
  if (new Set(headers).size !== headers.length) {
    throw new AssessmentTrendImportError(
      'DUPLICATE_HEADER',
      `Plik ${fileName} zawiera powtorzona nazwe kolumny.`
    );
  }
  const missingCommon = COMMON_REQUIRED_HEADERS.filter((header) => !headers.includes(header));
  if (missingCommon.length > 0) {
    throw new AssessmentTrendImportError(
      'MISSING_HEADERS',
      `Plik ${fileName} nie zawiera wymaganych kolumn: ${missingCommon.join(', ')}.`
    );
  }
}

function detectSource(fileName: string, headers: string[]): AssessmentTrendSource {
  const deliveryComplexity = DELIVERY_COMPLEXITY_HEADERS.every((header) => headers.includes(header));
  const deliveryScope = DELIVERY_SCOPE_HEADERS.every((header) => headers.includes(header));
  if (deliveryComplexity === deliveryScope) {
    throw new AssessmentTrendImportError(
      'UNKNOWN_ASSESSMENT',
      `Nie mozna jednoznacznie rozpoznac typu assessmentu w pliku ${fileName}.`
    );
  }
  return deliveryComplexity
    ? 'DELIVERY_COMPLEXITY_ASSESSMENT'
    : 'DELIVERY_SCOPE_COMPLEXITY';
}

function normalizeRow(
  fileName: string,
  fileIndex: number,
  rowNumber: number,
  source: AssessmentTrendSource,
  headerIndex: ReadonlyMap<string, number>,
  values: string[],
  hasAuthorColumns: boolean
): AssessmentTrendIssueRow {
  const value = (header: string) => values[requiredIndex(headerIndex, header)]?.trim() ?? '';
  const optionalValue = (header: string) => {
    const index = headerIndex.get(header);
    return index === undefined ? '' : values[index]?.trim() ?? '';
  };
  const issueKey = value('issueKey');
  if (!issueKey) {
    throw rowError(fileName, rowNumber, 'issueKey nie moze byc pusty.');
  }
  const doneAt = value('doneAt');
  const doneDate = calendarDate(doneAt);
  if (!doneDate) {
    throw rowError(fileName, rowNumber, `doneAt ma nieprawidlowa wartosc: ${doneAt || '(pusta)'}.`);
  }
  const deliveryUnitId = value('deliveryUnitId');
  if (!deliveryUnitId) {
    throw rowError(fileName, rowNumber, 'deliveryUnitId nie moze byc pusty.');
  }

  const assessmentStatus = value('assessmentStatus');
  const finalPoints = parsePoints(
    value(source === 'DELIVERY_COMPLEXITY_ASSESSMENT' ? 'deliveredStoryPoints' : 'finalScore'),
    fileName,
    rowNumber,
    source === 'DELIVERY_COMPLEXITY_ASSESSMENT' ? 'deliveredStoryPoints' : 'finalScore'
  );
  const aggregationPoints = parsePoints(
    value('pointsForAggregation'),
    fileName,
    rowNumber,
    'pointsForAggregation'
  );
  if (assessmentStatus === 'COMPLETED' && finalPoints === null) {
    throw rowError(fileName, rowNumber, 'COMPLETED wymaga koncowej wartosci punktowej.');
  }
  if (
    aggregationPoints !== null
    && (finalPoints === null || Math.abs(aggregationPoints - finalPoints) > 0.000_001)
  ) {
    throw rowError(
      fileName,
      rowNumber,
      'pointsForAggregation musi byc rowne koncowej wartosci Jira Issue.'
    );
  }

  const teamId = nullable(value('teamId'));
  const teamName = nullable(value('teamName'));
  const authorIds = hasAuthorColumns ? value(AUTHOR_ID_HEADER) : '';
  const authorNames = hasAuthorColumns ? value(AUTHOR_NAME_HEADER) : '';
  const dimensionValues = parseDimensionValues(source, value, fileName, rowNumber);
  const timeSpentSeconds = parseSeconds(
    optionalValue(TIME_SPENT_HEADER), fileName, rowNumber, TIME_SPENT_HEADER
  );
  const originalEstimateSeconds = parseSeconds(
    optionalValue(ORIGINAL_ESTIMATE_HEADER), fileName, rowNumber, ORIGINAL_ESTIMATE_HEADER
  );
  const remainingEstimateSeconds = parseSeconds(
    optionalValue(REMAINING_ESTIMATE_HEADER), fileName, rowNumber, REMAINING_ESTIMATE_HEADER
  );
  const timeTrackingCapturedAt = parseTimestamp(
    optionalValue(TIME_TRACKING_CAPTURED_AT_HEADER),
    fileName,
    rowNumber,
    TIME_TRACKING_CAPTURED_AT_HEADER
  );

  return {
    source,
    issueKey,
    issueUrl: value('issueUrl'),
    summary: value('summary'),
    issueType: value('issueType'),
    doneAt,
    doneDate,
    teamKey: teamId
      ? `id:${teamId}`
      : teamName
        ? `name:${teamName.toLocaleLowerCase('pl')}`
        : null,
    teamId,
    teamName,
    teamFieldId: nullable(value('teamFieldId')),
    authors: hasAuthorColumns ? parseAuthors(authorIds, authorNames) : [],
    deliveryUnitId,
    assessmentStatus: assessmentStatus || 'UNKNOWN',
    finalPoints,
    aggregationPoints,
    timeSpentSeconds,
    originalEstimateSeconds,
    remainingEstimateSeconds,
    timeTrackingCapturedAt,
    dimensionValues,
    sourceFileName: fileName,
    sourceFileIndex: fileIndex,
    sourceRowNumber: rowNumber
  };
}

function parseDimensionValues(
  source: AssessmentTrendSource,
  value: (header: string) => string,
  fileName: string,
  rowNumber: number
): Readonly<Record<string, number | null>> {
  const headers = source === 'DELIVERY_COMPLEXITY_ASSESSMENT'
    ? DELIVERY_COMPLEXITY_DIMENSION_HEADERS
    : DELIVERY_SCOPE_DIMENSION_HEADERS;
  return Object.fromEntries(headers.map((header) => {
    const parsed = parsePoints(value(header), fileName, rowNumber, header);
    if (
      source === 'DELIVERY_COMPLEXITY_ASSESSMENT'
      && parsed !== null
      && (!Number.isInteger(parsed) || parsed > 4)
    ) {
      throw rowError(fileName, rowNumber, `${header} musi byc liczba calkowita 0-4.`);
    }
    return [header, parsed];
  }));
}

function parseAuthors(idsValue: string, namesValue: string): AssessmentTrendAuthor[] {
  const ids = splitList(idsValue);
  const names = splitList(namesValue);
  const authors = new Map<string, AssessmentTrendAuthor>();
  const length = Math.max(ids.length, names.length);
  for (let index = 0; index < length; index += 1) {
    const id = ids[index] || null;
    const name = names[index] || (id ? `Author ${id}` : '');
    if (!id && !name) {
      continue;
    }
    const key = id ? `id:${id}` : `name:${name.toLocaleLowerCase('pl')}`;
    if (!authors.has(key)) {
      authors.set(key, {
        key,
        id,
        name,
        usesNameFallback: !id
      });
    }
  }
  return Array.from(authors.values());
}

function splitList(value: string): string[] {
  return value ? value.split('|').map((part) => part.trim()) : [];
}

function parsePoints(
  value: string,
  fileName: string,
  rowNumber: number,
  fieldName: string
): number | null {
  if (!value) {
    return null;
  }
  const normalized = value.replace(/\s/g, '').replace(',', '.');
  if (!/^(?:\d+(?:\.\d+)?|\.\d+)$/.test(normalized)) {
    throw rowError(fileName, rowNumber, `${fieldName} nie jest nieujemna liczba: ${value}.`);
  }
  const parsed = Number(normalized);
  if (!Number.isFinite(parsed)) {
    throw rowError(fileName, rowNumber, `${fieldName} nie jest skonczona liczba.`);
  }
  return parsed;
}

function parseSeconds(
  value: string,
  fileName: string,
  rowNumber: number,
  fieldName: string
): number | null {
  if (!value) {
    return null;
  }
  const normalized = value.replace(/\s/g, '');
  if (!/^\d+$/.test(normalized)) {
    throw rowError(fileName, rowNumber, `${fieldName} musi byc nieujemna liczba calkowita sekund.`);
  }
  const parsed = Number(normalized);
  if (!Number.isSafeInteger(parsed)) {
    throw rowError(fileName, rowNumber, `${fieldName} przekracza bezpieczny zakres liczbowy.`);
  }
  return parsed;
}

function parseTimestamp(
  value: string,
  fileName: string,
  rowNumber: number,
  fieldName: string
): string | null {
  if (!value) {
    return null;
  }
  if (Number.isNaN(Date.parse(value))) {
    throw rowError(fileName, rowNumber, `${fieldName} ma nieprawidlowa wartosc: ${value}.`);
  }
  return value;
}

function calendarDate(value: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})(?:$|T)/.exec(value);
  if (!match) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year
    && date.getUTCMonth() === month - 1
    && date.getUTCDate() === day
    ? `${match[1]}-${match[2]}-${match[3]}`
    : null;
}

function deduplicateIssues(rows: AssessmentTrendIssueRow[]): {
  rows: AssessmentTrendIssueRow[];
  duplicatesRemoved: number;
  conflictingDuplicates: number;
} {
  const selected = new Map<string, AssessmentTrendIssueRow>();
  let duplicatesRemoved = 0;
  let conflictingDuplicates = 0;
  for (const row of rows) {
    const key = row.issueKey.toLocaleUpperCase('en');
    const current = selected.get(key);
    if (!current) {
      selected.set(key, row);
      continue;
    }
    duplicatesRemoved += 1;
    if (rowFingerprint(current) !== rowFingerprint(row)) {
      conflictingDuplicates += 1;
    }
    if (isPreferredDuplicate(row, current)) {
      selected.set(key, row);
    }
  }
  return {
    rows: Array.from(selected.values()).sort((first, second) =>
      first.doneDate.localeCompare(second.doneDate)
      || first.issueKey.localeCompare(second.issueKey, 'pl')
    ),
    duplicatesRemoved,
    conflictingDuplicates
  };
}

function isPreferredDuplicate(
  candidate: AssessmentTrendIssueRow,
  current: AssessmentTrendIssueRow
): boolean {
  const candidateTime = comparableTimestamp(candidate.doneAt, candidate.doneDate);
  const currentTime = comparableTimestamp(current.doneAt, current.doneDate);
  if (candidateTime !== currentTime) {
    return candidateTime > currentTime;
  }
  const candidateSnapshot = comparableOptionalTimestamp(candidate.timeTrackingCapturedAt);
  const currentSnapshot = comparableOptionalTimestamp(current.timeTrackingCapturedAt);
  if (candidateSnapshot !== currentSnapshot) {
    return candidateSnapshot > currentSnapshot;
  }
  if (candidate.sourceFileIndex !== current.sourceFileIndex) {
    return candidate.sourceFileIndex > current.sourceFileIndex;
  }
  return candidate.sourceRowNumber > current.sourceRowNumber;
}

function comparableOptionalTimestamp(value: string | null): number {
  if (!value) {
    return Number.NEGATIVE_INFINITY;
  }
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? Number.NEGATIVE_INFINITY : parsed;
}

function comparableTimestamp(value: string, fallbackDate: string): number {
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? Date.parse(`${fallbackDate}T00:00:00Z`) : parsed;
}

function rowFingerprint(row: AssessmentTrendIssueRow): string {
  return JSON.stringify({
    issueUrl: row.issueUrl,
    summary: row.summary,
    issueType: row.issueType,
    doneAt: row.doneAt,
    teamKey: row.teamKey,
    authors: row.authors.map((author) => [author.key, author.name]),
    deliveryUnitId: row.deliveryUnitId,
    assessmentStatus: row.assessmentStatus,
    finalPoints: row.finalPoints,
    aggregationPoints: row.aggregationPoints,
    timeSpentSeconds: row.timeSpentSeconds,
    originalEstimateSeconds: row.originalEstimateSeconds,
    remainingEstimateSeconds: row.remainingEstimateSeconds,
    timeTrackingCapturedAt: row.timeTrackingCapturedAt,
    dimensionValues: row.dimensionValues
  });
}

function requiredIndex(headerIndex: ReadonlyMap<string, number>, header: string): number {
  const index = headerIndex.get(header);
  if (index === undefined) {
    throw new Error(`Missing validated header ${header}`);
  }
  return index;
}

function nullable(value: string): string | null {
  return value || null;
}

function rowError(fileName: string, rowNumber: number, message: string): AssessmentTrendImportError {
  return new AssessmentTrendImportError(
    'INVALID_ROW',
    `Plik ${fileName}, wiersz ${rowNumber}: ${message}`
  );
}
