import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import {
  TIME_TRACKING_HEADERS,
  buildExcelCsv,
  collectJiraSnapshots,
  enrichAssessmentCsv,
  enrichDirectory,
  inspectAssessmentCsv,
  parseArguments,
  parseExcelCsv
} from './enrich-assessment-csv-time-tracking.mjs';

const OLD_HEADERS = [
  'issueKey', 'issueUrl', 'summary', 'issueType', 'doneAt',
  'teamId', 'teamName', 'teamFieldId', 'mergeRequestUrls',
  'mergeRequestAuthorIds', 'mergeRequestAuthorNames', 'deliveryUnitId',
  'assessmentStatus', 'outcomeBreadth', 'domainDecisionComplexity',
  'applicationFlowComplexity', 'boundaryAndDataComplexity',
  'verificationStateSpace', 'implementedCompatibilityScope',
  'parameterizationComplexity', 'score100', 'deliveredStoryPoints',
  'pointsForAggregation'
];
const OLD_SCOPE_HEADERS = [
  'issueKey', 'issueUrl', 'summary', 'issueType', 'doneAt',
  'teamId', 'teamName', 'teamFieldId', 'mergeRequestUrls',
  'mergeRequestAuthorIds', 'mergeRequestAuthorNames', 'deliveryUnitId',
  'assessmentStatus', 'noveltyPoints', 'structuralAndLogicPoints',
  'businessAndInvariantsPoints', 'robustnessAndTestsPoints',
  'refactorAndArchitecturePoints', 'distributionPoints', 'finalScore',
  'pointsForAggregation'
];

test('parser i zapis zachowuja separator, BOM, cudzyslowy oraz nowe linie', () => {
  const content = buildExcelCsv([
    ['issueKey', 'summary'],
    ['CRM-1', 'Tekst; z "cytatem"\ni nowa linia']
  ]);

  assert.equal(content.startsWith('\uFEFF'), true);
  assert.deepEqual(parseExcelCsv(content), [
    ['issueKey', 'summary'],
    ['CRM-1', 'Tekst; z "cytatem"\ni nowa linia']
  ]);
});

test('wstawia cztery kolumny po doneAt i zachowuje dane przy braku snapshotu', () => {
  const rows = [
    OLD_HEADERS,
    oldRow('CRM-1', 'Pierwsze'),
    oldRow('CRM-2', 'Drugie')
  ];
  const document = inspectAssessmentCsv('report.csv', rows);
  const snapshots = new Map([['CRM-1', {
    timeSpentSeconds: 14_400,
    originalEstimateSeconds: 21_600,
    remainingEstimateSeconds: 3_600,
    timeTrackingCapturedAt: '2026-09-01T08:00:00.000Z'
  }]]);

  const result = enrichAssessmentCsv(document, snapshots);
  const enriched = parseExcelCsv(result.content);
  const doneAtIndex = enriched[0].indexOf('doneAt');

  assert.deepEqual(enriched[0].slice(doneAtIndex + 1, doneAtIndex + 5), TIME_TRACKING_HEADERS);
  assert.deepEqual(enriched[1].slice(doneAtIndex + 1, doneAtIndex + 5), [
    '14400', '21600', '3600', '2026-09-01T08:00:00.000Z'
  ]);
  assert.deepEqual(enriched[2].slice(doneAtIndex + 1, doneAtIndex + 5), ['', '', '', '']);
  assert.equal(result.updatedRows, 1);
  assert.equal(result.unchangedRows, 1);
});

test('obsluguje starszy eksport Delivery Scope Complexity bez zmiany punktow', () => {
  const document = inspectAssessmentCsv('scope.csv', [
    OLD_SCOPE_HEADERS,
    scopeRow('CRM-9')
  ]);
  const result = parseExcelCsv(enrichAssessmentCsv(document, new Map([['CRM-9', {
    timeSpentSeconds: 18_000,
    originalEstimateSeconds: null,
    remainingEstimateSeconds: 0,
    timeTrackingCapturedAt: '2026-09-01T08:00:00.000Z'
  }]])).content);

  assert.equal(result[1][result[0].indexOf('timeSpentSeconds')], '18000');
  assert.equal(result[1][result[0].indexOf('originalEstimateSeconds')], '');
  assert.equal(result[1][result[0].indexOf('finalScore')], '120');
  assert.equal(result[1][result[0].indexOf('pointsForAggregation')], '120');
});

test('deduplikuje issue pomiedzy plikami i pobiera pola Jira z Bearer tokenem', async (t) => {
  const requests = [];
  const server = http.createServer((request, response) => {
    requests.push({ url: request.url, authorization: request.headers.authorization });
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end(JSON.stringify({
      fields: {
        timespent: 7_200,
        timeoriginalestimate: 10_800,
        timeestimate: 1_800
      }
    }));
  });
  await listen(server);
  t.after(() => server.close());

  const result = await collectJiraSnapshots(['CRM-1', 'crm-1', 'CRM-2'], {
    baseUrl: serverUrl(server),
    token: 'secret-token',
    capturedAt: '2026-09-01T09:00:00.000Z',
    concurrency: 2,
    timeoutMs: 2_000,
    retries: 0,
    fetchImpl: fetch
  });

  assert.equal(requests.length, 2);
  assert.equal(requests.every((request) => request.authorization === 'Bearer secret-token'), true);
  assert.equal(requests.every((request) => request.url.includes(
    'fields=timespent,timeoriginalestimate,timeestimate'
  )), true);
  assert.deepEqual(result.snapshots.get('CRM-1'), {
    timeSpentSeconds: 7_200,
    originalEstimateSeconds: 10_800,
    remainingEstimateSeconds: 1_800,
    timeTrackingCapturedAt: '2026-09-01T09:00:00.000Z'
  });
});

test('ponawia HTTP 429 i akceptuje pozniejsza odpowiedz', async (t) => {
  let requestCount = 0;
  const server = http.createServer((request, response) => {
    requestCount += 1;
    if (requestCount === 1) {
      response.writeHead(429, { 'retry-after': '0' });
      response.end();
      return;
    }
    response.writeHead(200, { 'content-type': 'application/json' });
    response.end(JSON.stringify({ fields: { timespent: 3_600 } }));
  });
  await listen(server);
  t.after(() => server.close());

  const result = await collectJiraSnapshots(['CRM-1'], {
    baseUrl: serverUrl(server),
    token: 'token',
    capturedAt: '2026-09-01T09:00:00.000Z',
    concurrency: 1,
    timeoutMs: 2_000,
    retries: 1,
    fetchImpl: fetch
  });

  assert.equal(requestCount, 2);
  assert.equal(result.snapshots.get('CRM-1').timeSpentSeconds, 3_600);
});

test('nie ponawia niestabilnego bledu klienta HTTP 400', async () => {
  let requestCount = 0;
  const result = await collectJiraSnapshots(['CRM-1', 'CRM-2'], {
    baseUrl: 'https://jira.example.com',
    token: 'token',
    capturedAt: '2026-09-01T09:00:00.000Z',
    concurrency: 1,
    timeoutMs: 2_000,
    retries: 3,
    fetchImpl: async () => {
      requestCount += 1;
      return new Response('', { status: 400 });
    }
  }).catch((error) => error);

  assert.equal(requestCount, 2);
  assert.match(result.message, /Nie pobrano danych zadnego issue/);
});

test('wzbogaca wszystkie CSV do bezpiecznego katalogu wynikowego', async (t) => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'tdw-time-tracking-'));
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  await fs.writeFile(
    path.join(directory, 'may.csv'),
    buildExcelCsv([OLD_HEADERS, oldRow('CRM-1', 'Maj')]),
    'utf8'
  );
  await fs.writeFile(
    path.join(directory, 'june.csv'),
    buildExcelCsv([OLD_HEADERS, oldRow('CRM-1', 'Czerwiec'), oldRow('CRM-2', 'Czerwiec 2')]),
    'utf8'
  );
  let requestCount = 0;
  const fetchImpl = async () => {
    requestCount += 1;
    return new Response(JSON.stringify({ fields: { timespent: 3_600 } }), {
      status: 200,
      headers: { 'content-type': 'application/json' }
    });
  };

  const result = await enrichDirectory(baseOptions(directory, fetchImpl));

  assert.equal(result.fileCount, 2);
  assert.equal(result.rowCount, 3);
  assert.equal(result.requestedIssueCount, 2);
  assert.equal(requestCount, 2);
  const output = await fs.readFile(
    path.join(directory, '_enriched-time-tracking', 'may.csv'),
    'utf8'
  );
  assert.equal(parseExcelCsv(output)[0].includes('timeSpentSeconds'), true);
  assert.equal(
    await fs.readFile(path.join(directory, 'may.csv'), 'utf8'),
    buildExcelCsv([OLD_HEADERS, oldRow('CRM-1', 'Maj')])
  );
});

test('tryb in-place tworzy kopie zapasowa przed zmiana zrodla', async (t) => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'tdw-time-tracking-in-place-'));
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  const original = buildExcelCsv([OLD_HEADERS, oldRow('CRM-1', 'Maj')]);
  await fs.writeFile(path.join(directory, 'may.csv'), original, 'utf8');

  await enrichDirectory({
    ...baseOptions(directory, async () => new Response(
      JSON.stringify({ fields: { timespent: 3_600 } }),
      { status: 200, headers: { 'content-type': 'application/json' } }
    )),
    inPlace: true
  });

  const entries = await fs.readdir(directory);
  const backup = entries.find((name) => name.startsWith('may.csv.bak-'));
  assert.ok(backup);
  assert.equal(await fs.readFile(path.join(directory, backup), 'utf8'), original);
  assert.equal(parseExcelCsv(await fs.readFile(path.join(directory, 'may.csv'), 'utf8'))[0]
    .includes('timeSpentSeconds'), true);
});

test('preflight nie zapisuje czesci wynikow, gdy jeden plik docelowy juz istnieje', async (t) => {
  const directory = await fs.mkdtemp(path.join(os.tmpdir(), 'tdw-time-tracking-conflict-'));
  t.after(() => fs.rm(directory, { recursive: true, force: true }));
  const outputDirectory = path.join(directory, '_enriched-time-tracking');
  await fs.mkdir(outputDirectory);
  await fs.writeFile(
    path.join(directory, 'may.csv'),
    buildExcelCsv([OLD_HEADERS, oldRow('CRM-1', 'Maj')]),
    'utf8'
  );
  await fs.writeFile(
    path.join(directory, 'june.csv'),
    buildExcelCsv([OLD_HEADERS, oldRow('CRM-2', 'Czerwiec')]),
    'utf8'
  );
  await fs.writeFile(path.join(outputDirectory, 'june.csv'), 'existing', 'utf8');
  let requestCount = 0;

  await assert.rejects(
    enrichDirectory(baseOptions(directory, async () => {
      requestCount += 1;
      return new Response(
        JSON.stringify({ fields: { timespent: 3_600 } }),
        { status: 200, headers: { 'content-type': 'application/json' } }
      );
    })),
    /Pliki wynikowe juz istnieja: june.csv/
  );
  assert.equal(requestCount, 0);
  await assert.rejects(fs.access(path.join(outputDirectory, 'may.csv')));
  assert.equal(await fs.readFile(path.join(outputDirectory, 'june.csv'), 'utf8'), 'existing');
});

test('argumenty nie przyjmuja tokenu w CLI i domyslnie uzywaja aktualnego katalogu', () => {
  const options = parseArguments(
    ['--base-url', 'https://jira.example.com', '--concurrency', '4', '--dry-run'],
    { JIRA_TOKEN: 'secret' }
  );

  assert.equal(options.baseUrl, 'https://jira.example.com');
  assert.equal(options.token, 'secret');
  assert.equal(options.concurrency, 4);
  assert.equal(options.dryRun, true);
  assert.throws(() => parseArguments(['--token', 'secret'], {}), /Nieznany argument/);
  assert.throws(
    () => parseArguments(['--output-directory', '..\\outside'], {}),
    /nazwa bezposredniego podkatalogu/
  );
  assert.equal(parseArguments([], {
    ANALYSIS_JIRA_BASE_URL: 'https://jira.internal',
    ANALYSIS_JIRA_TOKEN: 'application-token'
  }).token, 'application-token');
});

function oldRow(issueKey, summary) {
  const values = {
    issueKey,
    issueUrl: `https://jira.example.com/browse/${issueKey}`,
    summary,
    issueType: 'Story',
    doneAt: '2026-05-10T09:00:00+02:00',
    teamId: 'team-a',
    teamName: 'Team A',
    teamFieldId: 'customfield_10000',
    mergeRequestUrls: 'https://gitlab.example.com/mr/1',
    mergeRequestAuthorIds: '101',
    mergeRequestAuthorNames: 'Anna Nowak',
    deliveryUnitId: `DU-${issueKey}`,
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
    pointsForAggregation: '8'
  };
  return OLD_HEADERS.map((header) => values[header] ?? '');
}

function scopeRow(issueKey) {
  const values = {
    issueKey,
    issueUrl: `https://jira.example.com/browse/${issueKey}`,
    summary: 'Zmiana zakresu',
    issueType: 'Story',
    doneAt: '2026-05-10T09:00:00+02:00',
    teamId: 'team-a',
    teamName: 'Team A',
    teamFieldId: 'customfield_10000',
    mergeRequestUrls: 'https://gitlab.example.com/mr/9',
    mergeRequestAuthorIds: '101',
    mergeRequestAuthorNames: 'Anna Nowak',
    deliveryUnitId: `DU-${issueKey}`,
    assessmentStatus: 'COMPLETED',
    noveltyPoints: '20',
    structuralAndLogicPoints: '20',
    businessAndInvariantsPoints: '20',
    robustnessAndTestsPoints: '20',
    refactorAndArchitecturePoints: '20',
    distributionPoints: '20',
    finalScore: '120',
    pointsForAggregation: '120'
  };
  return OLD_SCOPE_HEADERS.map((header) => values[header] ?? '');
}

function baseOptions(directory, fetchImpl) {
  return {
    directory,
    outputDirectory: '_enriched-time-tracking',
    baseUrl: 'https://jira.example.com',
    token: 'token',
    concurrency: 2,
    timeoutMs: 2_000,
    retries: 0,
    inPlace: false,
    overwrite: false,
    dryRun: false,
    capturedAt: '2026-09-01T09:00:00.000Z',
    fetchImpl
  };
}

function listen(server) {
  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
}

function serverUrl(server) {
  const address = server.address();
  return `http://127.0.0.1:${address.port}`;
}
