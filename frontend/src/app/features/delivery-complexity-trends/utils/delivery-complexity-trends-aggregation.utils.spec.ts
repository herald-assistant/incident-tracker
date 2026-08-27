import {
  AssessmentTrendDataset,
  AssessmentTrendFilters,
  AssessmentTrendIssueRow
} from '../models/delivery-complexity-trends.models';
import { buildAssessmentTrendView } from './delivery-complexity-trends-aggregation.utils';

describe('Delivery Complexity Trends aggregation', () => {
  it('should aggregate one contribution per Delivery Unit and calculate observed-period deltas', () => {
    const data = dataset([
      issue({
        issueKey: 'CRM-1',
        doneAt: '2026-01-05T09:00:00+01:00',
        deliveryUnitId: 'DU-CRM-1-CRM-2',
        finalPoints: 8,
        aggregationPoints: null
      }),
      issue({
        issueKey: 'CRM-2',
        doneAt: '2026-01-10T09:00:00+01:00',
        deliveryUnitId: 'DU-CRM-1-CRM-2',
        finalPoints: 8,
        aggregationPoints: 8
      }),
      issue({
        issueKey: 'CRM-3',
        doneAt: '2026-03-02T09:00:00+01:00',
        deliveryUnitId: 'DU-CRM-3',
        finalPoints: 13,
        aggregationPoints: 13
      })
    ]);

    const view = buildAssessmentTrendView(data, filters({ granularity: 'MONTH' }));

    expect(view.totalPoints).toBe(21);
    expect(view.scoredUnitCount).toBe(2);
    expect(view.issueCount).toBe(3);
    expect(view.periods.map((period) => ({
      key: period.key,
      points: period.points,
      delta: period.delta,
      deltaPercent: period.deltaPercent,
      units: period.unitCount,
      issues: period.issueCount
    }))).toEqual([
      {
        key: '2026-01',
        points: 8,
        delta: null,
        deltaPercent: null,
        units: 1,
        issues: 2
      },
      {
        key: '2026-03',
        points: 13,
        delta: 5,
        deltaPercent: 62.5,
        units: 1,
        issues: 1
      }
    ]);
  });

  it('should filter the whole unit by a team or author found on a non-anchor issue', () => {
    const rows = [
      issue({
        issueKey: 'CRM-1',
        deliveryUnitId: 'DU-SHARED',
        teamKey: 'id:team-b',
        teamId: 'team-b',
        teamName: 'Team B',
        authors: [{
          key: 'id:202', id: '202', name: 'Jan Kowalski', usesNameFallback: false
        }],
        finalPoints: 8,
        aggregationPoints: null
      }),
      issue({
        issueKey: 'CRM-2',
        doneAt: '2026-01-02T09:00:00+01:00',
        deliveryUnitId: 'DU-SHARED',
        finalPoints: 8,
        aggregationPoints: 8
      }),
      issue({ issueKey: 'CRM-3', deliveryUnitId: 'DU-OTHER', finalPoints: 5, aggregationPoints: 5 })
    ];
    const data = dataset(rows);

    const byTeam = buildAssessmentTrendView(data, filters({ teamKey: 'id:team-b' }));
    const byAuthor = buildAssessmentTrendView(data, filters({ authorKey: 'id:202' }));

    expect(byTeam.totalPoints).toBe(8);
    expect(byTeam.issueCount).toBe(2);
    expect(byAuthor.totalPoints).toBe(8);
    expect(byAuthor.scoredUnitCount).toBe(1);
  });

  it('should group calendar dates without browser-timezone shifts for days and quarters', () => {
    const data = dataset([
      issue({
        issueKey: 'CRM-1',
        doneAt: '2026-03-31T23:30:00-10:00',
        doneDate: '2026-03-31',
        finalPoints: 5,
        aggregationPoints: 5
      }),
      issue({
        issueKey: 'CRM-2',
        doneAt: '2026-04-01T00:30:00+14:00',
        doneDate: '2026-04-01',
        deliveryUnitId: 'DU-CRM-2',
        finalPoints: 8,
        aggregationPoints: 8
      })
    ]);

    const days = buildAssessmentTrendView(data, filters({ granularity: 'DAY' }));
    const quarters = buildAssessmentTrendView(data, filters({ granularity: 'QUARTER' }));

    expect(days.periods.map((period) => period.key)).toEqual(['2026-03-31', '2026-04-01']);
    expect(quarters.periods.map((period) => period.key)).toEqual(['2026-Q1', '2026-Q2']);
  });

  it('should apply inclusive dates and avoid a percentage after a zero period', () => {
    const data = dataset([
      issue({ issueKey: 'CRM-1', doneAt: '2026-01-01', doneDate: '2026-01-01', finalPoints: 0, aggregationPoints: 0 }),
      issue({ issueKey: 'CRM-2', doneAt: '2026-01-02', doneDate: '2026-01-02', deliveryUnitId: 'DU-2', finalPoints: 5, aggregationPoints: 5 }),
      issue({ issueKey: 'CRM-3', doneAt: '2026-01-03', doneDate: '2026-01-03', deliveryUnitId: 'DU-3', finalPoints: 8, aggregationPoints: 8 })
    ]);

    const view = buildAssessmentTrendView(data, filters({
      fromDate: '2026-01-01',
      toDate: '2026-01-02'
    }));

    expect(view.periods.map((period) => period.points)).toEqual([0, 5]);
    expect(view.periods[1].delta).toBe(5);
    expect(view.periods[1].deltaPercent).toBeNull();
  });

  it('should use a repeated final score only when the aggregation anchor is unavailable', () => {
    const data = dataset([
      issue({ issueKey: 'CRM-1', finalPoints: 8, aggregationPoints: null }),
      issue({
        issueKey: 'CRM-2',
        doneAt: '2026-01-02',
        doneDate: '2026-01-02',
        finalPoints: 13,
        aggregationPoints: null
      }),
      issue({
        issueKey: 'CRM-3',
        deliveryUnitId: 'DU-MULTI',
        finalPoints: 5,
        aggregationPoints: 5
      }),
      issue({
        issueKey: 'CRM-4',
        doneAt: '2026-01-04',
        doneDate: '2026-01-04',
        deliveryUnitId: 'DU-MULTI',
        finalPoints: 5,
        aggregationPoints: 5
      })
    ]);

    const view = buildAssessmentTrendView(data, filters());

    expect(view.totalPoints).toBe(18);
    expect(view.quality).toEqual({
      unitsUsingFinalScoreFallback: 1,
      unitsWithMultipleAggregationAnchors: 1,
      unitsWithConflictingFinalScores: 1
    });
  });
});

function filters(overrides: Partial<AssessmentTrendFilters> = {}): AssessmentTrendFilters {
  return {
    granularity: 'DAY',
    teamKey: '',
    authorKey: '',
    fromDate: '',
    toDate: '',
    ...overrides
  };
}

function dataset(rows: AssessmentTrendIssueRow[]): AssessmentTrendDataset {
  return {
    source: 'DELIVERY_COMPLEXITY_ASSESSMENT',
    metricLabel: 'Delivered Story Points',
    metricShortLabel: 'DSP',
    files: [],
    rows,
    quality: {
      inputRows: rows.length,
      uniqueIssues: rows.length,
      duplicatesRemoved: 0,
      conflictingDuplicates: 0,
      ignoredEmptyRows: 0,
      legacyFilesWithoutAuthors: 0,
      rowsWithoutAuthors: 0,
      authorsUsingNameFallback: 0
    }
  };
}

function issue(overrides: Partial<AssessmentTrendIssueRow> = {}): AssessmentTrendIssueRow {
  const value: AssessmentTrendIssueRow = {
    source: 'DELIVERY_COMPLEXITY_ASSESSMENT',
    issueKey: 'CRM-1',
    issueUrl: 'https://jira.example.com/browse/CRM-1',
    summary: 'Summary',
    issueType: 'Story',
    doneAt: '2026-01-01T09:00:00+01:00',
    doneDate: '2026-01-01',
    teamKey: 'id:team-a',
    teamId: 'team-a',
    teamName: 'Team A',
    teamFieldId: 'customfield_10000',
    authors: [{ key: 'id:101', id: '101', name: 'Anna Nowak', usesNameFallback: false }],
    deliveryUnitId: 'DU-CRM-1',
    assessmentStatus: 'COMPLETED',
    finalPoints: 8,
    aggregationPoints: 8,
    sourceFileName: 'report.csv',
    sourceFileIndex: 0,
    sourceRowNumber: 2,
    ...overrides
  };
  if (overrides.doneAt && overrides.doneDate === undefined) {
    value.doneDate = overrides.doneAt.slice(0, 10);
  }
  return value;
}
