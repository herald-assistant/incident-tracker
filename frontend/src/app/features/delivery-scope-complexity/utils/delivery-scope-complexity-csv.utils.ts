import {
  CsvValue,
  buildExcelCsv,
  downloadCsvFile
} from '../../../core/utils/csv-file.utils';
import { sanitizeFileNamePart } from '../../../core/utils/json-file.utils';
import {
  DeliveryScopeComplexityJobStateSnapshot,
  DeliveryScopeIssue,
  DeliveryScopeUnit
} from '../models/delivery-scope-complexity.models';

export const DELIVERY_SCOPE_COMPLEXITY_CSV_HEADERS = [
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
  'noveltyPoints',
  'structuralAndLogicPoints',
  'businessAndInvariantsPoints',
  'robustnessAndTestsPoints',
  'refactorAndArchitecturePoints',
  'distributionPoints',
  'finalScore',
  'pointsForAggregation'
] as const;

export function buildDeliveryScopeComplexityCsv(
  job: DeliveryScopeComplexityJobStateSnapshot
): string {
  const rows = job.units.flatMap((unit) => rowsForUnit(unit));
  return buildExcelCsv([DELIVERY_SCOPE_COMPLEXITY_CSV_HEADERS, ...rows]);
}

export function deliveryScopeComplexityCsvFileName(
  job: DeliveryScopeComplexityJobStateSnapshot
): string {
  return [
    'delivery-scope-complexity',
    sanitizeFileNamePart(job.jiraProject),
    job.fromDate,
    job.toDate
  ].join('-') + '.csv';
}

export function downloadDeliveryScopeComplexityCsv(
  job: DeliveryScopeComplexityJobStateSnapshot
): void {
  downloadCsvFile(
    deliveryScopeComplexityCsvFileName(job),
    buildDeliveryScopeComplexityCsv(job)
  );
}

function rowsForUnit(unit: DeliveryScopeUnit): CsvValue[][] {
  const aggregationIssueKey = unit.assessment ? aggregationAnchor(unit.issues)?.issueKey : null;
  const mergeRequestUrls = Array.from(new Set(
    unit.mergeRequests
      .map((mergeRequest) => mergeRequest.webUrl?.trim())
      .filter((url): url is string => Boolean(url))
  )).join(' | ');

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
      unit.unitId,
      unit.status,
      dimensions?.novelty.points,
      dimensions?.structuralAndLogic.points,
      dimensions?.businessAndInvariants.points,
      dimensions?.robustnessAndTests.points,
      dimensions?.refactorAndArchitecture.points,
      dimensions?.distribution.points,
      unit.assessment?.finalScore,
      issue.issueKey === aggregationIssueKey ? unit.assessment?.finalScore : null
    ];
  });
}

function aggregationAnchor(issues: DeliveryScopeIssue[]): DeliveryScopeIssue | null {
  return issues.reduce<DeliveryScopeIssue | null>((selected, issue) => {
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
