import { buildExcelCsv } from '../../../core/utils/csv-file.utils';
import {
  ASSESSMENT_TREND_MAX_FILES,
  ASSESSMENT_TREND_MAX_TOTAL_BYTES,
  AssessmentTrendImportError,
  importAssessmentTrendFiles
} from './delivery-complexity-trends-import.utils';

const DCA_HEADERS = [
  'issueKey', 'issueUrl', 'summary', 'issueType', 'doneAt',
  'teamId', 'teamName', 'teamFieldId', 'mergeRequestUrls',
  'mergeRequestAuthorIds', 'mergeRequestAuthorNames',
  'deliveryUnitId', 'assessmentStatus', 'outcomeBreadth',
  'domainDecisionComplexity', 'applicationFlowComplexity',
  'boundaryAndDataComplexity', 'verificationStateSpace',
  'implementedCompatibilityScope', 'parameterizationComplexity',
  'score100', 'deliveredStoryPoints', 'pointsForAggregation'
] as const;

const DSC_HEADERS = [
  'issueKey', 'issueUrl', 'summary', 'issueType', 'doneAt',
  'teamId', 'teamName', 'teamFieldId', 'mergeRequestUrls',
  'mergeRequestAuthorIds', 'mergeRequestAuthorNames',
  'deliveryUnitId', 'assessmentStatus', 'noveltyPoints',
  'structuralAndLogicPoints', 'businessAndInvariantsPoints',
  'robustnessAndTestsPoints', 'refactorAndArchitecturePoints',
  'distributionPoints', 'finalScore', 'pointsForAggregation'
] as const;

describe('Delivery Complexity Trends CSV import', () => {
  it('should detect DCA, parse authors and deduplicate issue keys deterministically', async () => {
    const first = csvFile('2026-07.csv', DCA_HEADERS, [
      row(DCA_HEADERS, {
        issueKey: 'CRM-1',
        doneAt: '2026-07-10T09:00:00+02:00',
        deliveredStoryPoints: '8',
        pointsForAggregation: '8'
      })
    ]);
    const second = csvFile('2026-08.csv', DCA_HEADERS, [
      row(DCA_HEADERS, {
        issueKey: 'CRM-1',
        doneAt: '2026-07-12T09:00:00+02:00',
        mergeRequestAuthorIds: '101 | ',
        mergeRequestAuthorNames: 'Anna Nowak | Partner zewnętrzny',
        deliveredStoryPoints: '13',
        pointsForAggregation: '13'
      }),
      row(DCA_HEADERS, {
        issueKey: 'CRM-2',
        doneAt: '2026-08-02T10:00:00+02:00',
        deliveryUnitId: 'DU-CRM-2',
        deliveredStoryPoints: '5',
        pointsForAggregation: '5'
      })
    ]);

    const result = await importAssessmentTrendFiles([first, second]);

    expect(result.source).toBe('DELIVERY_COMPLEXITY_ASSESSMENT');
    expect(result.metricShortLabel).toBe('DSP');
    expect(result.rows.map((item) => item.issueKey)).toEqual(['CRM-1', 'CRM-2']);
    expect(result.rows[0].finalPoints).toBe(13);
    expect(result.rows[0].sourceFileName).toBe('2026-08.csv');
    expect(result.rows[0].dimensionValues).toEqual({
      outcomeBreadth: 2,
      domainDecisionComplexity: 3,
      applicationFlowComplexity: 3,
      boundaryAndDataComplexity: 2,
      verificationStateSpace: 3,
      implementedCompatibilityScope: 2,
      parameterizationComplexity: 3
    });
    expect(result.rows[0].authors).toEqual([
      { key: 'id:101', id: '101', name: 'Anna Nowak', usesNameFallback: false },
      {
        key: 'name:partner zewnętrzny',
        id: null,
        name: 'Partner zewnętrzny',
        usesNameFallback: true
      }
    ]);
    expect(result.quality).toMatchObject({
      inputRows: 3,
      uniqueIssues: 2,
      duplicatesRemoved: 1,
      conflictingDuplicates: 1,
      authorsUsingNameFallback: 1
    });
  });

  it('should use the later selected file when duplicate Done timestamps are equal', async () => {
    const first = csvFile('first.csv', DCA_HEADERS, [
      row(DCA_HEADERS, { issueKey: 'CRM-1', deliveredStoryPoints: '8', pointsForAggregation: '8' })
    ]);
    const second = csvFile('second.csv', DCA_HEADERS, [
      row(DCA_HEADERS, { issueKey: 'CRM-1', deliveredStoryPoints: '13', pointsForAggregation: '13' })
    ]);

    const result = await importAssessmentTrendFiles([first, second]);

    expect(result.rows[0].finalPoints).toBe(13);
    expect(result.rows[0].sourceFileName).toBe('second.csv');
  });

  it('should accept an older Scope CSV without author columns and expose the limitation', async () => {
    const legacyHeaders = DSC_HEADERS.filter(
      (header) => !['mergeRequestAuthorIds', 'mergeRequestAuthorNames'].includes(header)
    );
    const file = csvFile('legacy-scope.csv', legacyHeaders, [
      row(legacyHeaders, { finalScore: '122,5', pointsForAggregation: '122,5' })
    ]);

    const result = await importAssessmentTrendFiles([file]);

    expect(result.source).toBe('DELIVERY_SCOPE_COMPLEXITY');
    expect(result.rows[0].finalPoints).toBe(122.5);
    expect(result.rows[0].dimensionValues).toEqual({
      noveltyPoints: 20.5,
      structuralAndLogicPoints: 31,
      businessAndInvariantsPoints: 18,
      robustnessAndTestsPoints: 14,
      refactorAndArchitecturePoints: 13,
      distributionPoints: 26
    });
    expect(result.rows[0].authors).toEqual([]);
    expect(result.quality.legacyFilesWithoutAuthors).toBe(1);
    expect(result.quality.rowsWithoutAuthors).toBe(1);
  });

  it('should reject a mixed set of both assessments', async () => {
    const dca = csvFile('dca.csv', DCA_HEADERS, [row(DCA_HEADERS)]);
    const scope = csvFile('scope.csv', DSC_HEADERS, [row(DSC_HEADERS)]);

    await expect(importAssessmentTrendFiles([dca, scope])).rejects.toMatchObject({
      code: 'MIXED_ASSESSMENTS'
    });
  });

  it('should reject invalid rows and malformed CSV with the file context', async () => {
    const invalidPoints = csvFile('invalid.csv', DCA_HEADERS, [
      row(DCA_HEADERS, { deliveredStoryPoints: '-2', pointsForAggregation: '-2' })
    ]);
    const malformed = textFile('broken.csv', 'issueKey;doneAt\n"CRM-1;2026-07-01');
    const invalidDimension = csvFile('invalid-dimension.csv', DCA_HEADERS, [
      row(DCA_HEADERS, { outcomeBreadth: '4,5' })
    ]);

    await expect(importAssessmentTrendFiles([invalidPoints])).rejects.toMatchObject({
      code: 'INVALID_ROW'
    });
    await expect(importAssessmentTrendFiles([malformed])).rejects.toEqual(
      expect.objectContaining<Partial<AssessmentTrendImportError>>({
        code: 'MALFORMED_CSV',
        message: expect.stringContaining('broken.csv')
      })
    );
    await expect(importAssessmentTrendFiles([invalidDimension])).rejects.toMatchObject({
      code: 'INVALID_ROW'
    });
  });

  it('should reject a file with only one of the paired author columns', async () => {
    const headers = DCA_HEADERS.filter((header) => header !== 'mergeRequestAuthorIds');
    const file = csvFile('unpaired.csv', headers, [row(headers)]);

    await expect(importAssessmentTrendFiles([file])).rejects.toMatchObject({
      code: 'INCOMPLETE_AUTHOR_COLUMNS'
    });
  });

  it('should reject empty selections, empty files and bounded input violations', async () => {
    await expect(importAssessmentTrendFiles([])).rejects.toMatchObject({ code: 'NO_FILES' });
    await expect(importAssessmentTrendFiles([textFile('empty.csv', '')])).rejects.toMatchObject({
      code: 'EMPTY_FILE'
    });

    const tooManyFiles = Array.from(
      { length: ASSESSMENT_TREND_MAX_FILES + 1 },
      (_, index) => textFile(`report-${index}.csv`, '')
    );
    await expect(importAssessmentTrendFiles(tooManyFiles)).rejects.toMatchObject({
      code: 'FILE_LIMIT_EXCEEDED'
    });

    const oversized = {
      name: 'oversized.csv',
      size: ASSESSMENT_TREND_MAX_TOTAL_BYTES + 1
    } as File;
    await expect(importAssessmentTrendFiles([oversized])).rejects.toMatchObject({
      code: 'SIZE_LIMIT_EXCEEDED'
    });
  });
});

function row(
  headers: readonly string[],
  overrides: Record<string, string> = {}
): string[] {
  const defaults: Record<string, string> = {
    issueKey: 'CRM-1',
    issueUrl: 'https://jira.example.com/browse/CRM-1',
    summary: 'Obsługa płatności',
    issueType: 'Story',
    doneAt: '2026-07-01T09:00:00+02:00',
    teamId: '1900',
    teamName: 'Team A',
    teamFieldId: 'customfield_10000',
    mergeRequestUrls: 'https://gitlab.example.com/mr/7',
    mergeRequestAuthorIds: '101',
    mergeRequestAuthorNames: 'Anna Nowak',
    deliveryUnitId: 'DU-CRM-1',
    assessmentStatus: 'COMPLETED',
    outcomeBreadth: '2',
    domainDecisionComplexity: '3',
    applicationFlowComplexity: '3',
    boundaryAndDataComplexity: '2',
    verificationStateSpace: '3',
    implementedCompatibilityScope: '2',
    parameterizationComplexity: '3',
    score100: '72,5',
    deliveredStoryPoints: '8',
    noveltyPoints: '20,5',
    structuralAndLogicPoints: '31',
    businessAndInvariantsPoints: '18',
    robustnessAndTestsPoints: '14',
    refactorAndArchitecturePoints: '13',
    distributionPoints: '26',
    finalScore: '122,5',
    pointsForAggregation: headers.includes('finalScore') ? '122,5' : '8'
  };
  return headers.map((header) => overrides[header] ?? defaults[header] ?? '');
}

function csvFile(
  name: string,
  headers: readonly string[],
  rows: readonly (readonly string[])[]
): File {
  return textFile(name, buildExcelCsv([headers, ...rows]));
}

function textFile(name: string, content: string): File {
  const file = new File([content], name, { type: 'text/csv' });
  Object.defineProperty(file, 'text', {
    configurable: true,
    value: () => Promise.resolve(content)
  });
  return file;
}
