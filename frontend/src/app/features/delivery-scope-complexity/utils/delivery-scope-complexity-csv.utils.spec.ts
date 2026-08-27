import {
  DeliveryScopeComplexityJobStateSnapshot,
  DeliveryScopeDimensionScore
} from '../models/delivery-scope-complexity.models';
import {
  DELIVERY_SCOPE_COMPLEXITY_CSV_HEADERS,
  buildDeliveryScopeComplexityCsv,
  deliveryScopeComplexityCsvFileName
} from './delivery-scope-complexity-csv.utils';

describe('Delivery Scope Complexity CSV', () => {
  it('should export one row per issue and count final score only on the latest Done issue', () => {
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

    const lines = csvLines(buildDeliveryScopeComplexityCsv(job));
    const headers = lines[0].split(';');
    const first = lines[1].split(';');
    const second = lines[2].split(';');

    expect(headers).toEqual([...DELIVERY_SCOPE_COMPLEXITY_CSV_HEADERS]);
    expect(first[headers.indexOf('issueKey')]).toBe('CRM-2');
    expect(first[headers.indexOf('mergeRequestUrls')]).toBe('https://gitlab.example.com/mr/7');
    expect(first[headers.indexOf('noveltyPoints')]).toBe('20,5');
    expect(first[headers.indexOf('finalScore')]).toBe('122,5');
    expect(first[headers.indexOf('pointsForAggregation')]).toBe('122,5');
    expect(second[headers.indexOf('pointsForAggregation')]).toBe('');
  });

  it('should choose the lower issue key when Done timestamps are equal', () => {
    const job = snapshot();
    job.units[0].issues = [
      issue('CRM-2', '2026-07-12T09:00:00Z'),
      issue('CRM-1', '2026-07-12T09:00:00Z')
    ];

    const lines = csvLines(buildDeliveryScopeComplexityCsv(job));
    const headers = lines[0].split(';');
    const rows = lines.slice(1).map((line) => line.split(';'));

    expect(rows[0][headers.indexOf('pointsForAggregation')]).toBe('');
    expect(rows[1][headers.indexOf('pointsForAggregation')]).toBe('122,5');
  });

  it('should leave scoring columns empty for an unassessed unit', () => {
    const job = snapshot();
    job.units[0].status = 'NOT_SCORABLE';
    job.units[0].assessment = null;

    const lines = csvLines(buildDeliveryScopeComplexityCsv(job));
    const headers = lines[0].split(';');
    const row = lines[1].split(';');

    expect(row[headers.indexOf('assessmentStatus')]).toBe('NOT_SCORABLE');
    expect(row[headers.indexOf('noveltyPoints')]).toBe('');
    expect(row[headers.indexOf('finalScore')]).toBe('');
    expect(row[headers.indexOf('pointsForAggregation')]).toBe('');
  });

  it('should preserve Excel-compatible quoting through the shared CSV writer', () => {
    const job = snapshot();
    job.units[0].issues[0].summary = 'Płatność; wariant "pilny"\nDruga linia';

    const csv = buildDeliveryScopeComplexityCsv(job);

    expect(csv.startsWith('\uFEFF')).toBe(true);
    expect(csv).toContain('"Płatność; wariant ""pilny""\nDruga linia"');
    expect(csv.endsWith('\r\n')).toBe(true);
  });

  it('should build a stable business file name from project and period', () => {
    expect(deliveryScopeComplexityCsvFileName(snapshot())).toBe(
      'delivery-scope-complexity-CRM-2026-07-01-2026-07-31.csv'
    );
  });
});

function snapshot(): DeliveryScopeComplexityJobStateSnapshot {
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
        finalScore: 122.5,
        dimensions: {
          novelty: dimension(20.5, 0.20),
          structuralAndLogic: dimension(31, 0.25),
          businessAndInvariants: dimension(18, 0.15),
          robustnessAndTests: dimension(14, 0.10),
          refactorAndArchitecture: dimension(13, 0.10),
          distribution: dimension(26, 0.20)
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
      totalComplexityPoints: 122.5,
      averageComplexityScore: 122.5,
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

function dimension(points: number, weight: number): DeliveryScopeDimensionScore {
  return {
    score: 80,
    scopeSignal: 0.5,
    scope: 1.3,
    scaledScore: 104,
    weight,
    points,
    evidence: []
  };
}

function issue(issueKey: string, doneAt: string) {
  return {
    issueKey,
    issueUrl: `https://jira.example.com/browse/${issueKey}`,
    summary: `Summary ${issueKey}`,
    issueType: 'Story',
    doneAt,
    team: { id: '1900', name: 'Team A', fieldId: 'customfield_10000' }
  };
}

function csvLines(csv: string): string[] {
  return csv.replace(/^\uFEFF/, '').trimEnd().split('\r\n');
}
