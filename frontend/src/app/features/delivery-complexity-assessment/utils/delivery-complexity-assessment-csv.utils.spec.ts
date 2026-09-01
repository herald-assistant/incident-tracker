import { DeliveryComplexityAssessmentJobStateSnapshot } from '../models/delivery-complexity-assessment.models';
import {
  DELIVERY_COMPLEXITY_ASSESSMENT_CSV_HEADERS,
  buildDeliveryComplexityAssessmentCsv,
  deliveryComplexityAssessmentCsvFileName
} from './delivery-complexity-assessment-csv.utils';

describe('Delivery Complexity Assessment CSV', () => {
  it('should export one row per issue and count DSP only on the latest Done issue', () => {
    const job = snapshot();
    job.units[0].issues = [
      issue('CRM-2', '2026-07-12T09:00:00Z'),
      issue('CRM-1', '2026-07-10T09:00:00Z')
    ];
    job.units[0].mergeRequests.push({
      ...job.units[0].mergeRequests[0],
      identity: 'duplicate-url',
      iid: 8
    });

    const lines = csvLines(buildDeliveryComplexityAssessmentCsv(job));
    const headers = lines[0].split(';');
    const first = lines[1].split(';');
    const second = lines[2].split(';');

    expect(headers).toEqual([...DELIVERY_COMPLEXITY_ASSESSMENT_CSV_HEADERS]);
    expect(first[headers.indexOf('issueKey')]).toBe('CRM-2');
    expect(first[headers.indexOf('timeSpentSeconds')]).toBe('14400');
    expect(first[headers.indexOf('originalEstimateSeconds')]).toBe('28800');
    expect(first[headers.indexOf('remainingEstimateSeconds')]).toBe('7200');
    expect(first[headers.indexOf('timeTrackingCapturedAt')]).toBe('2026-07-11T08:00:00Z');
    expect(first[headers.indexOf('mergeRequestUrls')]).toBe('https://gitlab.example.com/mr/7');
    expect(first[headers.indexOf('mergeRequestAuthorIds')]).toBe('101');
    expect(first[headers.indexOf('mergeRequestAuthorNames')]).toBe('MR Author');
    expect(first[headers.indexOf('score100')]).toBe('72,5');
    expect(first[headers.indexOf('deliveredStoryPoints')]).toBe('8');
    expect(first[headers.indexOf('pointsForAggregation')]).toBe('8');
    expect(second[headers.indexOf('pointsForAggregation')]).toBe('');
  });

  it('should keep unique MR author ids and names in the same stable order', () => {
    const job = snapshot();
    job.units[0].mergeRequests.push(
      {
        ...job.units[0].mergeRequests[0],
        identity: 'id:8',
        iid: 8,
        authorId: null,
        authorName: 'Fallback Author'
      },
      {
        ...job.units[0].mergeRequests[0],
        identity: 'id:9',
        iid: 9
      }
    );

    const lines = csvLines(buildDeliveryComplexityAssessmentCsv(job));
    const headers = lines[0].split(';');
    const row = lines[1].split(';');

    expect(row[headers.indexOf('mergeRequestAuthorIds')]).toBe('101 | ');
    expect(row[headers.indexOf('mergeRequestAuthorNames')]).toBe('MR Author | Fallback Author');
  });

  it('should choose the lower issue key when Done timestamps are equal', () => {
    const job = snapshot();
    job.units[0].issues = [
      issue('CRM-2', '2026-07-12T09:00:00Z'),
      issue('CRM-1', '2026-07-12T09:00:00Z')
    ];

    const lines = csvLines(buildDeliveryComplexityAssessmentCsv(job));
    const headers = lines[0].split(';');
    const rows = lines.slice(1).map((line) => line.split(';'));

    expect(rows[0][headers.indexOf('pointsForAggregation')]).toBe('');
    expect(rows[1][headers.indexOf('pointsForAggregation')]).toBe('8');
  });

  it('should leave scoring columns empty for an unassessed unit', () => {
    const job = snapshot();
    job.units[0].status = 'NOT_SCORABLE';
    job.units[0].assessment = null;

    const lines = csvLines(buildDeliveryComplexityAssessmentCsv(job));
    const headers = lines[0].split(';');
    const row = lines[1].split(';');

    expect(row[headers.indexOf('assessmentStatus')]).toBe('NOT_SCORABLE');
    expect(row[headers.indexOf('outcomeBreadth')]).toBe('');
    expect(row[headers.indexOf('deliveredStoryPoints')]).toBe('');
    expect(row[headers.indexOf('pointsForAggregation')]).toBe('');
  });

  it('should preserve missing Jira time tracking as empty cells', () => {
    const job = snapshot();
    job.units[0].issues[0] = {
      ...job.units[0].issues[0],
      timeSpentSeconds: null,
      originalEstimateSeconds: undefined,
      remainingEstimateSeconds: null,
      timeTrackingCapturedAt: undefined
    };

    const lines = csvLines(buildDeliveryComplexityAssessmentCsv(job));
    const headers = lines[0].split(';');
    const row = lines[1].split(';');

    expect(row[headers.indexOf('timeSpentSeconds')]).toBe('');
    expect(row[headers.indexOf('originalEstimateSeconds')]).toBe('');
    expect(row[headers.indexOf('remainingEstimateSeconds')]).toBe('');
    expect(row[headers.indexOf('timeTrackingCapturedAt')]).toBe('');
  });

  it('should quote separators, quotes and line breaks for Excel-compatible cells', () => {
    const job = snapshot();
    job.units[0].issues[0].summary = 'Profil klienta; wariant "pilny"\nDruga linia';

    const csv = buildDeliveryComplexityAssessmentCsv(job);

    expect(csv.startsWith('\uFEFF')).toBe(true);
    expect(csv).toContain('"Profil klienta; wariant ""pilny""\nDruga linia"');
    expect(csv.endsWith('\r\n')).toBe(true);
  });

  it('should build a stable business file name from project and period', () => {
    expect(deliveryComplexityAssessmentCsvFileName(snapshot())).toBe(
      'delivery-complexity-assessment-CRM-2026-07-01-2026-07-31.csv'
    );
  });
});

function snapshot(): DeliveryComplexityAssessmentJobStateSnapshot {
  return {
    jobId: 'job-1',
    jiraProject: 'CRM',
    fromDate: '2026-07-01',
    toDate: '2026-07-31',
    aiModel: 'gpt-5',
    reasoningEffort: 'medium',
    status: 'COMPLETED',
    currentStepCode: 'COMPLETED',
    currentStepLabel: 'Completed',
    errorCode: null,
    errorMessage: null,
    createdAt: '2026-07-01T10:00:00Z',
    updatedAt: '2026-07-01T10:01:00Z',
    completedAt: '2026-07-01T10:01:00Z',
    discoveredIssues: 1,
    processedIssues: 1,
    totalIssues: 1,
    effectiveJql: 'project = "CRM"',
    steps: [],
    contextSections: [],
    aiActivityEvents: [],
    units: [{
      unitId: 'DU-CRM-1',
      status: 'COMPLETED',
      issues: [issue('CRM-1', '2026-07-01T09:00:00Z')],
      mergeRequests: [{
        identity: 'id:7',
        projectPath: 'crm/customer-api',
        iid: 7,
        title: 'Customer status',
        webUrl: 'https://gitlab.example.com/mr/7',
        mergedAt: '2026-07-01T09:00:00Z',
        authorId: 101,
        authorName: 'MR Author',
        changedPaths: ['src/CustomerStatus.java']
      }],
      assessment: {
        deliveredStoryPoints: 8,
        score100: 72.5,
        dimensions: {
          outcomeBreadth: 2,
          domainDecisionComplexity: 3,
          applicationFlowComplexity: 3,
          boundaryAndDataComplexity: 2,
          verificationStateSpace: 3,
          implementedCompatibilityScope: 2,
          parameterizationComplexity: 3
        },
        confidence: 0.85,
        evidenceSummary: [],
        qualityFlags: []
      },
      visibilityLimits: [],
      errorCode: null,
      errorMessage: null,
      startedAt: '2026-07-01T10:00:00Z',
      completedAt: '2026-07-01T10:01:00Z',
      preparedPrompt: null,
      promptPreparedAt: null,
      rawAiResponse: null,
      usage: null
    }],
    aggregate: {
      totalDeliveredStoryPoints: 8,
      distribution: { 8: 1 },
      totalUnits: 1,
      assessedUnits: 1,
      excludedUnits: 0,
      notScorableUnits: 0,
      failedUnits: 0,
      coverage: 1,
      confidence: 'HIGH',
      usage: null
    }
  };
}

function issue(issueKey: string, doneAt: string) {
  return {
    issueKey,
    issueUrl: `https://jira.example.com/browse/${issueKey}`,
    summary: `Summary ${issueKey}`,
    issueType: 'Story',
    doneAt,
    team: { id: '1900', name: 'Team A', fieldId: 'customfield_10000' },
    timeSpentSeconds: 14400,
    originalEstimateSeconds: 28800,
    remainingEstimateSeconds: 7200,
    timeTrackingCapturedAt: '2026-07-11T08:00:00Z'
  };
}

function csvLines(csv: string): string[] {
  return csv.replace(/^\uFEFF/, '').trimEnd().split('\r\n');
}
