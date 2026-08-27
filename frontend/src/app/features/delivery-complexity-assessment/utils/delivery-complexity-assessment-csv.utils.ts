import {
  CsvValue,
  buildExcelCsv,
  downloadCsvFile
} from '../../../core/utils/csv-file.utils';
import { sanitizeFileNamePart } from '../../../core/utils/json-file.utils';
import {
  DeliveryAssessmentIssue,
  DeliveryAssessmentUnit,
  DeliveryComplexityAssessmentJobStateSnapshot
} from '../models/delivery-complexity-assessment.models';

export const DELIVERY_COMPLEXITY_ASSESSMENT_CSV_HEADERS = [
  'issueKey',
  'issueUrl',
  'summary',
  'issueType',
  'doneAt',
  'teamId',
  'teamName',
  'teamFieldId',
  'mergeRequestUrls',
  'mergeRequestAuthorIds',
  'mergeRequestAuthorNames',
  'deliveryUnitId',
  'assessmentStatus',
  'outcomeBreadth',
  'domainDecisionComplexity',
  'applicationFlowComplexity',
  'boundaryAndDataComplexity',
  'verificationStateSpace',
  'implementedCompatibilityScope',
  'parameterizationComplexity',
  'score100',
  'deliveredStoryPoints',
  'pointsForAggregation'
] as const;

export function buildDeliveryComplexityAssessmentCsv(
  job: DeliveryComplexityAssessmentJobStateSnapshot
): string {
  const rows = job.units.flatMap((unit) => rowsForUnit(unit));
  return buildExcelCsv([DELIVERY_COMPLEXITY_ASSESSMENT_CSV_HEADERS, ...rows]);
}

export function deliveryComplexityAssessmentCsvFileName(
  job: DeliveryComplexityAssessmentJobStateSnapshot
): string {
  return [
    'delivery-complexity-assessment',
    sanitizeFileNamePart(job.jiraProject),
    job.fromDate,
    job.toDate
  ].join('-') + '.csv';
}

export function downloadDeliveryComplexityAssessmentCsv(
  job: DeliveryComplexityAssessmentJobStateSnapshot
): void {
  downloadCsvFile(
    deliveryComplexityAssessmentCsvFileName(job),
    buildDeliveryComplexityAssessmentCsv(job)
  );
}

function rowsForUnit(unit: DeliveryAssessmentUnit): CsvValue[][] {
  const aggregationIssueKey = unit.assessment ? aggregationAnchor(unit.issues)?.issueKey : null;
  const mergeRequestUrls = Array.from(new Set(
    unit.mergeRequests
      .map((mergeRequest) => mergeRequest.webUrl?.trim())
      .filter((url): url is string => Boolean(url))
  )).join(' | ');
  const mergeRequestAuthors = authorsForUnit(unit);

  return unit.issues.map((issue) => {
    const dimensions = unit.assessment?.dimensions;
    return [
      issue.issueKey,
      issue.issueUrl,
      issue.summary,
      issue.issueType,
      issue.doneAt,
      issue.team?.id,
      issue.team?.name,
      issue.team?.fieldId,
      mergeRequestUrls,
      mergeRequestAuthors.map((author) => author.id).join(' | '),
      mergeRequestAuthors.map((author) => author.name).join(' | '),
      unit.unitId,
      unit.status,
      dimensions?.outcomeBreadth,
      dimensions?.domainDecisionComplexity,
      dimensions?.applicationFlowComplexity,
      dimensions?.boundaryAndDataComplexity,
      dimensions?.verificationStateSpace,
      dimensions?.implementedCompatibilityScope,
      dimensions?.parameterizationComplexity,
      unit.assessment?.score100,
      unit.assessment?.deliveredStoryPoints,
      issue.issueKey === aggregationIssueKey ? unit.assessment?.deliveredStoryPoints : null
    ];
  });
}

function authorsForUnit(unit: DeliveryAssessmentUnit): { id: string; name: string }[] {
  const authors = new Map<string, { id: string; name: string }>();
  for (const mergeRequest of unit.mergeRequests) {
    const id = mergeRequest.authorId === null ? '' : String(mergeRequest.authorId);
    const rawName = mergeRequest.authorName?.trim() ?? '';
    if (!id && !rawName) {
      continue;
    }
    const key = id ? `id:${id}` : `name:${rawName.toLocaleLowerCase('pl')}`;
    if (!authors.has(key)) {
      authors.set(key, { id, name: rawName || `Author ${id}` });
    }
  }
  return Array.from(authors.values());
}

function aggregationAnchor(issues: DeliveryAssessmentIssue[]): DeliveryAssessmentIssue | null {
  return issues.reduce<DeliveryAssessmentIssue | null>((selected, issue) => {
    if (!selected) {
      return issue;
    }
    const selectedTimestamp = timestamp(selected.doneAt);
    const issueTimestamp = timestamp(issue.doneAt);
    if (issueTimestamp > selectedTimestamp) {
      return issue;
    }
    if (issueTimestamp === selectedTimestamp && issue.issueKey < selected.issueKey) {
      return issue;
    }
    return selected;
  }, null);
}

function timestamp(value: string): number {
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? Number.NEGATIVE_INFINITY : parsed;
}
