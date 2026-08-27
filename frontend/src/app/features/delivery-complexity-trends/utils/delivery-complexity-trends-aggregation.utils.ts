import {
  AssessmentTrendDataset,
  AssessmentTrendFilterOption,
  AssessmentTrendFilters,
  AssessmentTrendGranularity,
  AssessmentTrendHighlight,
  AssessmentTrendIssueRow,
  AssessmentTrendPeriod,
  AssessmentTrendStatusCount,
  AssessmentTrendView
} from '../models/delivery-complexity-trends.models';

const MONTH_LABELS = [
  'sty', 'lut', 'mar', 'kwi', 'maj', 'cze',
  'lip', 'sie', 'wrz', 'paz', 'lis', 'gru'
] as const;

export function buildAssessmentTrendView(
  dataset: AssessmentTrendDataset,
  filters: AssessmentTrendFilters
): AssessmentTrendView {
  const units = buildUnits(dataset.rows);
  const selectedUnits = units.filter((unit) => matchesFilters(unit, filters));
  const periodMap = new Map<string, PeriodAccumulator>();

  for (const unit of selectedUnits) {
    if (unit.points === null || !unit.pointDate) {
      continue;
    }
    const key = periodKey(unit.pointDate, filters.granularity);
    const period = periodMap.get(key) ?? {
      key,
      label: periodLabel(key, filters.granularity),
      points: 0,
      unitIds: new Set<string>(),
      issueKeys: new Set<string>()
    };
    period.points += unit.points;
    period.unitIds.add(unit.id);
    unit.issueKeys.forEach((issueKey) => period.issueKeys.add(issueKey));
    periodMap.set(key, period);
  }

  const periods = Array.from(periodMap.values())
    .sort((first, second) => first.key.localeCompare(second.key))
    .map((period, index, all): AssessmentTrendPeriod => {
      const points = round1(period.points);
      const previous = all[index - 1];
      if (!previous) {
        return {
          key: period.key,
          label: period.label,
          points,
          delta: null,
          deltaPercent: null,
          direction: 'NONE',
          unitCount: period.unitIds.size,
          issueCount: period.issueKeys.size
        };
      }
      const previousPoints = round1(previous.points);
      const delta = round1(points - previousPoints);
      return {
        key: period.key,
        label: period.label,
        points,
        delta,
        deltaPercent: previousPoints === 0
          ? null
          : round1((delta / Math.abs(previousPoints)) * 100),
        direction: delta > 0 ? 'UP' : delta < 0 ? 'DOWN' : 'FLAT',
        unitCount: period.unitIds.size,
        issueCount: period.issueKeys.size
      };
    });

  const issueKeys = new Set(selectedUnits.flatMap((unit) => unit.issueKeys));
  const scoredUnits = selectedUnits.filter((unit) => unit.points !== null);

  return {
    periods,
    totalPoints: round1(scoredUnits.reduce((total, unit) => total + (unit.points ?? 0), 0)),
    scoredUnitCount: scoredUnits.length,
    totalUnitCount: selectedUnits.length,
    issueCount: issueKeys.size,
    statusCounts: statusCounts(selectedUnits),
    teamOptions: optionsForUnits(units, (unit) => unit.teams),
    authorOptions: optionsForUnits(units, (unit) => unit.authors),
    highlights: highlights(periods),
    quality: {
      unitsUsingFinalScoreFallback: units.filter((unit) => unit.usesFinalScoreFallback).length,
      unitsWithMultipleAggregationAnchors: units.filter((unit) => unit.multipleAnchors).length,
      unitsWithConflictingFinalScores: units.filter((unit) => unit.conflictingFinalScores).length
    }
  };
}

interface TrendUnit {
  id: string;
  issueKeys: string[];
  teams: Map<string, string>;
  authors: Map<string, string>;
  status: string;
  effectiveDate: string;
  pointDate: string | null;
  points: number | null;
  usesFinalScoreFallback: boolean;
  multipleAnchors: boolean;
  conflictingFinalScores: boolean;
}

interface PeriodAccumulator {
  key: string;
  label: string;
  points: number;
  unitIds: Set<string>;
  issueKeys: Set<string>;
}

function buildUnits(rows: AssessmentTrendIssueRow[]): TrendUnit[] {
  const rowsByUnit = new Map<string, AssessmentTrendIssueRow[]>();
  for (const row of rows) {
    const current = rowsByUnit.get(row.deliveryUnitId) ?? [];
    current.push(row);
    rowsByUnit.set(row.deliveryUnitId, current);
  }
  return Array.from(rowsByUnit.entries()).map(([id, unitRows]) => buildUnit(id, unitRows));
}

function buildUnit(id: string, rows: AssessmentTrendIssueRow[]): TrendUnit {
  const sorted = [...rows].sort(compareRows);
  const anchors = sorted.filter((row) => row.aggregationPoints !== null);
  const selectedAnchor = anchors.at(-1) ?? null;
  const finalRows = sorted.filter((row) => row.finalPoints !== null);
  const selectedFinal = finalRows.at(-1) ?? null;
  const finalPointValues = new Set(finalRows.map((row) => round1(row.finalPoints!)));
  const teams = new Map<string, string>();
  const authors = new Map<string, string>();

  for (const row of sorted) {
    if (row.teamKey && !teams.has(row.teamKey)) {
      teams.set(row.teamKey, row.teamName || row.teamId || 'Nieznany zespół');
    }
    for (const author of row.authors) {
      if (!authors.has(author.key)) {
        authors.set(author.key, author.name);
      }
    }
  }

  const statuses = Array.from(new Set(sorted.map((row) => row.assessmentStatus)));
  const usesFinalScoreFallback = !selectedAnchor && Boolean(selectedFinal);
  const pointRow = selectedAnchor ?? selectedFinal;

  return {
    id,
    issueKeys: Array.from(new Set(sorted.map((row) => row.issueKey))).sort((a, b) =>
      a.localeCompare(b, 'pl')
    ),
    teams,
    authors,
    status: statuses.length === 1 ? statuses[0] : 'MIXED',
    effectiveDate: sorted.at(-1)?.doneDate ?? '',
    pointDate: pointRow?.doneDate ?? null,
    points: pointRow
      ? round1(selectedAnchor?.aggregationPoints ?? selectedFinal?.finalPoints ?? 0)
      : null,
    usesFinalScoreFallback,
    multipleAnchors: anchors.length > 1,
    conflictingFinalScores: finalPointValues.size > 1
  };
}

function matchesFilters(unit: TrendUnit, filters: AssessmentTrendFilters): boolean {
  if (filters.teamKey && !unit.teams.has(filters.teamKey)) {
    return false;
  }
  if (filters.authorKey && !unit.authors.has(filters.authorKey)) {
    return false;
  }
  const date = unit.pointDate ?? unit.effectiveDate;
  if (filters.fromDate && date < filters.fromDate) {
    return false;
  }
  if (filters.toDate && date > filters.toDate) {
    return false;
  }
  return true;
}

function optionsForUnits(
  units: TrendUnit[],
  values: (unit: TrendUnit) => ReadonlyMap<string, string>
): AssessmentTrendFilterOption[] {
  const options = new Map<string, { label: string; unitIds: Set<string> }>();
  for (const unit of units) {
    for (const [key, label] of values(unit)) {
      const current = options.get(key) ?? { label, unitIds: new Set<string>() };
      current.unitIds.add(unit.id);
      options.set(key, current);
    }
  }
  return Array.from(options.entries())
    .map(([key, value]) => ({ key, label: value.label, unitCount: value.unitIds.size }))
    .sort((first, second) => first.label.localeCompare(second.label, 'pl'));
}

function statusCounts(units: TrendUnit[]): AssessmentTrendStatusCount[] {
  const counts = new Map<string, number>();
  for (const unit of units) {
    counts.set(unit.status, (counts.get(unit.status) ?? 0) + 1);
  }
  const order = ['COMPLETED', 'NOT_SCORABLE', 'EXCLUDED', 'FAILED', 'MIXED', 'UNKNOWN'];
  return Array.from(counts.entries())
    .map(([status, count]) => ({ status, count }))
    .sort((first, second) => {
      const firstIndex = order.indexOf(first.status);
      const secondIndex = order.indexOf(second.status);
      return (firstIndex < 0 ? order.length : firstIndex)
        - (secondIndex < 0 ? order.length : secondIndex)
        || first.status.localeCompare(second.status);
    });
}

function highlights(periods: AssessmentTrendPeriod[]): AssessmentTrendHighlight[] {
  if (periods.length === 0) {
    return [];
  }
  const peak = periods.reduce((selected, period) =>
    period.points > selected.points ? period : selected
  );
  const increase = periods
    .filter((period) => (period.delta ?? 0) > 0)
    .reduce<AssessmentTrendPeriod | null>((selected, period) =>
      !selected || period.delta! > selected.delta! ? period : selected
    , null);
  const decrease = periods
    .filter((period) => (period.delta ?? 0) < 0)
    .reduce<AssessmentTrendPeriod | null>((selected, period) =>
      !selected || period.delta! < selected.delta! ? period : selected
    , null);

  return [
    {
      label: 'Najwyższa złożoność',
      periodLabel: peak.label,
      value: peak.points,
      kind: 'PEAK' as const
    },
    increase
      ? {
          label: 'Największy wzrost',
          periodLabel: increase.label,
          value: increase.delta!,
          kind: 'INCREASE' as const
        }
      : null,
    decrease
      ? {
          label: 'Największy spadek',
          periodLabel: decrease.label,
          value: decrease.delta!,
          kind: 'DECREASE' as const
        }
      : null
  ].filter((item): item is AssessmentTrendHighlight => item !== null);
}

function periodKey(date: string, granularity: AssessmentTrendGranularity): string {
  if (granularity === 'DAY') {
    return date;
  }
  if (granularity === 'MONTH') {
    return date.slice(0, 7);
  }
  const year = date.slice(0, 4);
  const month = Number(date.slice(5, 7));
  return `${year}-Q${Math.floor((month - 1) / 3) + 1}`;
}

function periodLabel(key: string, granularity: AssessmentTrendGranularity): string {
  if (granularity === 'QUARTER') {
    const [year, quarter] = key.split('-');
    return `${quarter} ${year}`;
  }
  const year = key.slice(0, 4);
  const month = Number(key.slice(5, 7));
  if (granularity === 'MONTH') {
    return `${MONTH_LABELS[month - 1]} ${year}`;
  }
  const day = Number(key.slice(8, 10));
  return `${day} ${MONTH_LABELS[month - 1]} ${year}`;
}

function compareRows(first: AssessmentTrendIssueRow, second: AssessmentTrendIssueRow): number {
  return comparableTimestamp(first) - comparableTimestamp(second)
    || first.sourceFileIndex - second.sourceFileIndex
    || first.sourceRowNumber - second.sourceRowNumber;
}

function comparableTimestamp(row: AssessmentTrendIssueRow): number {
  const parsed = Date.parse(row.doneAt);
  return Number.isNaN(parsed) ? Date.parse(`${row.doneDate}T00:00:00Z`) : parsed;
}

function round1(value: number): number {
  return Math.round((value + Number.EPSILON) * 10) / 10;
}
