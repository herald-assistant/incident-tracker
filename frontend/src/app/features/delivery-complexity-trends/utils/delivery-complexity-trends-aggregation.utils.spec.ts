import {
  AssessmentTrendDataset,
  AssessmentTrendFilters,
  AssessmentTrendIssueRow
} from '../models/delivery-complexity-trends.models';
import { buildAssessmentTrendView } from './delivery-complexity-trends-aggregation.utils';

describe('Delivery Complexity Trends aggregation', () => {
  it('should calculate complexity points per person-day and period deltas', () => {
    const data = dataset([
      issue({
        issueKey: 'CRM-1',
        deliveryUnitId: 'DU-JAN',
        aggregationPoints: null,
        timeSpentSeconds: 4 * 3600
      }),
      issue({
        issueKey: 'CRM-2',
        deliveryUnitId: 'DU-JAN',
        aggregationPoints: 8,
        timeSpentSeconds: 4 * 3600
      }),
      issue({
        issueKey: 'CRM-3',
        deliveryUnitId: 'DU-FEB',
        doneAt: '2026-02-02T09:00:00+01:00',
        finalPoints: 12,
        aggregationPoints: 12,
        timeSpentSeconds: 8 * 3600
      })
    ]);

    const efficiency = buildAssessmentTrendView(
      data,
      filters({ granularity: 'MONTH' })
    ).efficiency;

    expect(efficiency.pointsPerPersonDay).toBe(10);
    expect(efficiency.totalEligiblePoints).toBe(20);
    expect(efficiency.totalPersonDays).toBe(2);
    expect(efficiency.pointsCoveragePercent).toBe(100);
    expect(efficiency.periods.map((period) => ({
      key: period.key,
      value: period.pointsPerPersonDay,
      delta: period.delta,
      deltaPercent: period.deltaPercent
    }))).toEqual([
      { key: '2026-01', value: 8, delta: null, deltaPercent: null },
      { key: '2026-02', value: 12, delta: 4, deltaPercent: 50 }
    ]);
  });

  it('should exclude incomplete and zero-time units without treating missing time as zero', () => {
    const data = dataset([
      issue({ issueKey: 'CRM-1', deliveryUnitId: 'DU-INCOMPLETE', timeSpentSeconds: 3600 }),
      issue({
        issueKey: 'CRM-2',
        deliveryUnitId: 'DU-INCOMPLETE',
        aggregationPoints: null,
        timeSpentSeconds: null
      }),
      issue({
        issueKey: 'CRM-3',
        deliveryUnitId: 'DU-ZERO',
        finalPoints: 4,
        aggregationPoints: 4,
        timeSpentSeconds: 0
      }),
      issue({
        issueKey: 'CRM-4',
        deliveryUnitId: 'DU-ELIGIBLE',
        finalPoints: 8,
        aggregationPoints: 8,
        timeSpentSeconds: 8 * 3600
      })
    ]);

    const efficiency = buildAssessmentTrendView(data, filters()).efficiency;

    expect(efficiency.totalEligiblePoints).toBe(8);
    expect(efficiency.pointsCoveragePercent).toBe(40);
    expect(efficiency.unitsWithoutCompleteTime).toBe(1);
    expect(efficiency.unitsWithZeroTime).toBe(1);
    expect(efficiency.eligibleUnitCount).toBe(1);
  });

  it('should include cross-team units for tribe and exclude them from a selected team efficiency', () => {
    const data = dataset([
      issue({ issueKey: 'CRM-1', deliveryUnitId: 'DU-SHARED', timeSpentSeconds: 4 * 3600 }),
      issue({
        issueKey: 'CRM-2',
        deliveryUnitId: 'DU-SHARED',
        aggregationPoints: null,
        teamKey: 'id:team-b',
        teamId: 'team-b',
        teamName: 'Team B',
        timeSpentSeconds: 4 * 3600
      }),
      issue({
        issueKey: 'CRM-3',
        deliveryUnitId: 'DU-TEAM-A',
        finalPoints: 4,
        aggregationPoints: 4,
        timeSpentSeconds: 4 * 3600
      })
    ]);

    const tribe = buildAssessmentTrendView(data, filters()).efficiency;
    const team = buildAssessmentTrendView(data, filters({ teamKey: 'id:team-a' })).efficiency;

    expect(tribe.totalEligiblePoints).toBe(12);
    expect(tribe.crossTeamUnitsExcluded).toBe(0);
    expect(team.totalEligiblePoints).toBe(4);
    expect(team.crossTeamUnitsExcluded).toBe(1);
  });

  it('should compare original estimate with actual time for the same eligible sample', () => {
    const data = dataset([
      issue({
        timeSpentSeconds: 10 * 3600,
        originalEstimateSeconds: 8 * 3600,
        remainingEstimateSeconds: 2 * 3600
      })
    ]);

    const efficiency = buildAssessmentTrendView(data, filters()).efficiency;

    expect(efficiency.totalEstimatedPersonDays).toBe(1);
    expect(efficiency.totalActualPersonDaysForEstimate).toBe(1.25);
    expect(efficiency.estimateVariancePercent).toBe(25);
    expect(efficiency.estimateEligibleUnitCount).toBe(1);
    expect(efficiency.issuesWithRemainingEstimate).toBe(1);
  });

  it('should use configurable workday hours and hide periods when no timespent exists', () => {
    const withTime = dataset([issue({ timeSpentSeconds: 8 * 3600 })]);
    const withoutTime = dataset([issue()]);
    const emptyEfficiency = buildAssessmentTrendView(withoutTime, filters()).efficiency;

    expect(buildAssessmentTrendView(withTime, filters(), 8).efficiency.pointsPerPersonDay).toBe(8);
    expect(buildAssessmentTrendView(withTime, filters(), 4).efficiency.pointsPerPersonDay).toBe(4);
    expect(emptyEfficiency.hasTimeSpentData).toBe(false);
    expect(emptyEfficiency.hasEstimateData).toBe(false);
    expect(emptyEfficiency.periods).toEqual([]);
    expect(emptyEfficiency.pointsPerPersonDay).toBeNull();
  });

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

  it('should filter the whole unit by team, author or issue type found on any issue', () => {
    const rows = [
      issue({
        issueKey: 'CRM-1',
        deliveryUnitId: 'DU-SHARED',
        issueType: 'Bug',
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
      issue({
        issueKey: 'CRM-3',
        deliveryUnitId: 'DU-OTHER',
        issueType: 'Task',
        finalPoints: 5,
        aggregationPoints: 5
      }),
      issue({
        issueKey: 'CRM-4',
        deliveryUnitId: 'DU-STORY',
        issueType: 'Story',
        finalPoints: 3,
        aggregationPoints: 3
      })
    ];
    const data = dataset(rows);

    const byTeam = buildAssessmentTrendView(data, filters({ teamKey: 'id:team-b' }));
    const byAuthor = buildAssessmentTrendView(data, filters({ authorKey: 'id:202' }));
    const byBug = buildAssessmentTrendView(data, filters({ issueTypeKeys: ['type:bug'] }));
    const byBugOrStory = buildAssessmentTrendView(data, filters({
      issueTypeKeys: ['type:bug', 'type:story']
    }));

    expect(byTeam.totalPoints).toBe(8);
    expect(byTeam.issueCount).toBe(2);
    expect(byAuthor.totalPoints).toBe(8);
    expect(byAuthor.scoredUnitCount).toBe(1);
    expect(byBug.totalPoints).toBe(8);
    expect(byBug.issueCount).toBe(2);
    expect(byBugOrStory.totalPoints).toBe(11);
    expect(byBugOrStory.scoredUnitCount).toBe(2);
    expect(byBugOrStory.issueTypeOptions).toEqual([
      { key: 'type:bug', label: 'Bug', unitCount: 1 },
      { key: 'type:story', label: 'Story', unitCount: 2 },
      { key: 'type:task', label: 'Task', unitCount: 1 }
    ]);
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

  it('should group ISO weeks from Monday to Sunday across the calendar-year boundary', () => {
    const data = dataset([
      issue({
        issueKey: 'CRM-1',
        doneAt: '2020-12-31T23:30:00-10:00',
        doneDate: '2020-12-31',
        finalPoints: 5,
        aggregationPoints: 5
      }),
      issue({
        issueKey: 'CRM-2',
        doneAt: '2021-01-03T00:30:00+14:00',
        doneDate: '2021-01-03',
        deliveryUnitId: 'DU-CRM-2',
        finalPoints: 8,
        aggregationPoints: 8
      }),
      issue({
        issueKey: 'CRM-3',
        doneAt: '2021-01-04T09:00:00+01:00',
        doneDate: '2021-01-04',
        deliveryUnitId: 'DU-CRM-3',
        finalPoints: 13,
        aggregationPoints: 13
      })
    ]);

    const weeks = buildAssessmentTrendView(data, filters({ granularity: 'WEEK' }));

    expect(weeks.periods.map((period) => ({
      key: period.key,
      label: period.label,
      points: period.points,
      issues: period.issueCount
    }))).toEqual([
      { key: '2020-W53', label: 'tydz. 53 · 2020', points: 13, issues: 2 },
      { key: '2021-W01', label: 'tydz. 1 · 2021', points: 13, issues: 1 }
    ]);
  });

  it('should aggregate DCA averages and weighted score100 contributions once per unit', () => {
    const data = dataset([
      issue({
        issueKey: 'CRM-1',
        dimensionValues: dcaDimensions({
          outcomeBreadth: 1,
          domainDecisionComplexity: 4,
          boundaryAndDataComplexity: 1
        })
      }),
      issue({
        issueKey: 'CRM-2',
        deliveryUnitId: 'DU-CRM-2',
        finalPoints: 5,
        aggregationPoints: 5,
        dimensionValues: dcaDimensions({ outcomeBreadth: 3, domainDecisionComplexity: 2 })
      }),
      issue({
        issueKey: 'CRM-1-B',
        deliveryUnitId: 'DU-CRM-1',
        finalPoints: 8,
        aggregationPoints: null,
        dimensionValues: dcaDimensions({
          outcomeBreadth: 4,
          domainDecisionComplexity: 0,
          boundaryAndDataComplexity: 4
        })
      })
    ]);

    const view = buildAssessmentTrendView(data, filters({ granularity: 'MONTH' }));
    const outcome = view.periods[0].dimensions.find(
      (dimension) => dimension.key === 'outcomeBreadth'
    );
    const domain = view.periods[0].dimensions.find(
      (dimension) => dimension.key === 'domainDecisionComplexity'
    );
    const boundary = view.periods[0].dimensions.find(
      (dimension) => dimension.key === 'boundaryAndDataComplexity'
    );

    expect(outcome).toMatchObject({ total: 10, average: 2, sampleUnitCount: 2 });
    expect(domain).toMatchObject({ total: 30, average: 3, sampleUnitCount: 2 });
    expect(boundary).toMatchObject({ total: 11.25, average: 1.5, sampleUnitCount: 2 });
    expect(view.dimensionDefinitions).toHaveLength(7);
    expect(view.quality.unitsWithIncompleteDimensions).toBe(0);
  });

  it('should expose incomplete dimensions without treating them as zero samples', () => {
    const data = dataset([
      issue({
        dimensionValues: dcaDimensions({ parameterizationComplexity: null })
      })
    ]);

    const view = buildAssessmentTrendView(data, filters({ granularity: 'MONTH' }));
    const parameterization = view.periods[0].dimensions.find(
      (dimension) => dimension.key === 'parameterizationComplexity'
    );

    expect(parameterization).toMatchObject({
      total: null,
      average: null,
      sampleUnitCount: 0,
      totalDelta: null,
      averageDelta: null
    });
    expect(view.quality.unitsWithIncompleteDimensions).toBe(1);
  });

  it('should preserve the additive Scope breakdown', () => {
    const first = {
      noveltyPoints: 10,
      structuralAndLogicPoints: 15,
      businessAndInvariantsPoints: 10,
      robustnessAndTestsPoints: 5,
      refactorAndArchitecturePoints: 5,
      distributionPoints: 15
    };
    const second = {
      noveltyPoints: 20,
      structuralAndLogicPoints: 20,
      businessAndInvariantsPoints: 15,
      robustnessAndTestsPoints: 10,
      refactorAndArchitecturePoints: 10,
      distributionPoints: 15
    };
    const data = dataset([
      issue({
        issueKey: 'CRM-1',
        finalPoints: 60,
        aggregationPoints: 60,
        dimensionValues: first
      }),
      issue({
        issueKey: 'CRM-2',
        doneAt: '2026-02-01',
        doneDate: '2026-02-01',
        deliveryUnitId: 'DU-CRM-2',
        finalPoints: 90,
        aggregationPoints: 90,
        dimensionValues: second
      })
    ], 'DELIVERY_SCOPE_COMPLEXITY');

    const view = buildAssessmentTrendView(data, filters({ granularity: 'MONTH' }));

    expect(view.periods.map((period) => period.dimensions.reduce(
      (total, dimension) => total + (dimension.total ?? 0),
      0
    ))).toEqual([60, 90]);
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
      unitsWithConflictingFinalScores: 1,
      unitsWithIncompleteDimensions: 0
    });
  });
});

function filters(overrides: Partial<AssessmentTrendFilters> = {}): AssessmentTrendFilters {
  return {
    granularity: 'DAY',
    teamKey: '',
    authorKey: '',
    issueTypeKeys: [],
    fromDate: '',
    toDate: '',
    ...overrides
  };
}

function dataset(
  rows: AssessmentTrendIssueRow[],
  source: AssessmentTrendDataset['source'] = 'DELIVERY_COMPLEXITY_ASSESSMENT'
): AssessmentTrendDataset {
  return {
    source,
    metricLabel: source === 'DELIVERY_COMPLEXITY_ASSESSMENT'
      ? 'Delivered Story Points'
      : 'Complexity Points',
    metricShortLabel: source === 'DELIVERY_COMPLEXITY_ASSESSMENT' ? 'DSP' : 'punkty',
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
    timeSpentSeconds: null,
    originalEstimateSeconds: null,
    remainingEstimateSeconds: null,
    timeTrackingCapturedAt: null,
    dimensionValues: dcaDimensions(),
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

function dcaDimensions(
  overrides: Partial<Record<string, number | null>> = {}
): Readonly<Record<string, number | null>> {
  return {
    outcomeBreadth: 2,
    domainDecisionComplexity: 3,
    applicationFlowComplexity: 3,
    boundaryAndDataComplexity: 2,
    verificationStateSpace: 3,
    implementedCompatibilityScope: 2,
    parameterizationComplexity: 3,
    ...overrides
  };
}
