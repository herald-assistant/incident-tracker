#!/usr/bin/env node

import { constants as fsConstants } from 'node:fs';
import fs from 'node:fs/promises';
import path from 'node:path';
import process from 'node:process';
import { pathToFileURL } from 'node:url';

export const TIME_TRACKING_HEADERS = [
  'timeSpentSeconds',
  'originalEstimateSeconds',
  'remainingEstimateSeconds',
  'timeTrackingCapturedAt'
];

const REQUIRED_ASSESSMENT_HEADERS = [
  'issueKey',
  'doneAt',
  'deliveryUnitId',
  'assessmentStatus',
  'pointsForAggregation'
];
const DEFAULT_OUTPUT_DIRECTORY = '_enriched-time-tracking';
const DEFAULT_CONCURRENCY = 6;
const DEFAULT_TIMEOUT_MS = 30_000;
const DEFAULT_RETRIES = 3;

export class CsvFormatError extends Error {
  constructor(message, line, column) {
    super(`${message} (wiersz ${line}, kolumna ${column})`);
    this.name = 'CsvFormatError';
    this.line = line;
    this.column = column;
  }
}

export function parseExcelCsv(content) {
  const input = content.startsWith('\uFEFF') ? content.slice(1) : content;
  if (input.length === 0) {
    return [];
  }

  const rows = [];
  let row = [];
  let cell = '';
  let inQuotes = false;
  let quoteClosed = false;
  let endedWithLineBreak = false;
  let line = 1;
  let column = 1;

  const finishCell = () => {
    row.push(cell);
    cell = '';
    quoteClosed = false;
  };
  const finishRow = () => {
    finishCell();
    rows.push(row);
    row = [];
    endedWithLineBreak = true;
  };

  for (let index = 0; index < input.length; index += 1) {
    const character = input[index];
    if (inQuotes) {
      if (character === '"') {
        if (input[index + 1] === '"') {
          cell += '"';
          index += 1;
          column += 2;
          endedWithLineBreak = false;
          continue;
        }
        inQuotes = false;
        quoteClosed = true;
      } else if (character === '\r' || character === '\n') {
        if (character === '\r' && input[index + 1] === '\n') {
          index += 1;
        }
        cell += '\n';
        line += 1;
        column = 1;
        endedWithLineBreak = false;
        continue;
      } else {
        cell += character;
      }
      column += 1;
      endedWithLineBreak = false;
      continue;
    }

    if (quoteClosed && character !== ';' && character !== '\r' && character !== '\n') {
      throw new CsvFormatError('Nieoczekiwany znak po zamknieciu cytowanej komorki', line, column);
    }

    if (character === '"') {
      if (cell.length > 0) {
        throw new CsvFormatError('Cudzyslow moze rozpoczynac tylko pusta komorke', line, column);
      }
      inQuotes = true;
      endedWithLineBreak = false;
    } else if (character === ';') {
      finishCell();
      endedWithLineBreak = false;
    } else if (character === '\r' || character === '\n') {
      if (character === '\r' && input[index + 1] === '\n') {
        index += 1;
      }
      finishRow();
      line += 1;
      column = 1;
      continue;
    } else {
      cell += character;
      endedWithLineBreak = false;
    }
    column += 1;
  }

  if (inQuotes) {
    throw new CsvFormatError('Niezamknieta cytowana komorka', line, column);
  }
  if (!endedWithLineBreak || row.length > 0 || cell.length > 0 || quoteClosed) {
    finishRow();
  }
  return rows;
}

export function buildExcelCsv(rows) {
  return '\uFEFF' + rows
    .map((row) => row.map(csvCell).join(';'))
    .join('\r\n') + '\r\n';
}

export function inspectAssessmentCsv(fileName, rows) {
  if (rows.length === 0 || rows[0].every((value) => value.trim() === '')) {
    throw new Error(`${fileName}: plik CSV nie ma naglowka.`);
  }
  const headers = rows[0].map((header) => header.trim());
  const seen = new Set();
  for (const header of headers) {
    if (!header) {
      throw new Error(`${fileName}: pusty naglowek CSV.`);
    }
    if (seen.has(header)) {
      throw new Error(`${fileName}: zduplikowany naglowek '${header}'.`);
    }
    seen.add(header);
  }
  const missing = REQUIRED_ASSESSMENT_HEADERS.filter((header) => !seen.has(header));
  if (missing.length > 0) {
    throw new Error(
      `${fileName}: to nie jest obslugiwany eksport assessmentu; brak kolumn: ${missing.join(', ')}.`
    );
  }

  const issueKeyIndex = headers.indexOf('issueKey');
  const dataRows = rows.slice(1).filter((row) => row.some((value) => value.trim() !== ''));
  const issueKeys = [];
  for (let index = 0; index < dataRows.length; index += 1) {
    if (dataRows[index].length !== headers.length) {
      throw new Error(
        `${fileName}: wiersz ${index + 2} ma ${dataRows[index].length} kolumn zamiast ${headers.length}.`
      );
    }
    const issueKey = dataRows[index][issueKeyIndex].trim();
    if (!issueKey) {
      throw new Error(`${fileName}: pusty issueKey w wierszu ${index + 2}.`);
    }
    issueKeys.push(issueKey);
  }
  return { fileName, headers, dataRows, issueKeys };
}

export function enrichAssessmentCsv(document, snapshotsByIssueKey) {
  const retainedHeaders = document.headers.filter(
    (header) => !TIME_TRACKING_HEADERS.includes(header)
  );
  const doneAtIndex = retainedHeaders.indexOf('doneAt');
  const outputHeaders = [
    ...retainedHeaders.slice(0, doneAtIndex + 1),
    ...TIME_TRACKING_HEADERS,
    ...retainedHeaders.slice(doneAtIndex + 1)
  ];
  const oldIndexes = new Map(document.headers.map((header, index) => [header, index]));
  const issueKeyIndex = oldIndexes.get('issueKey');
  let updatedRows = 0;
  let unchangedRows = 0;

  const outputRows = document.dataRows.map((row) => {
    const issueKey = row[issueKeyIndex].trim();
    const snapshot = snapshotsByIssueKey.get(normalizeIssueKey(issueKey));
    if (snapshot) {
      updatedRows += 1;
    } else {
      unchangedRows += 1;
    }
    return outputHeaders.map((header) => {
      if (TIME_TRACKING_HEADERS.includes(header)) {
        if (snapshot) {
          return snapshot[header] ?? '';
        }
        const oldIndex = oldIndexes.get(header);
        return oldIndex === undefined ? '' : row[oldIndex];
      }
      return row[oldIndexes.get(header)];
    });
  });

  return {
    content: buildExcelCsv([outputHeaders, ...outputRows]),
    updatedRows,
    unchangedRows
  };
}

export async function fetchJiraTimeTracking(issueKey, options) {
  const apiBaseUrl = `${options.baseUrl.replace(/\/+$/, '')}/rest/api/2`;
  const url = `${apiBaseUrl}/issue/${encodeURIComponent(issueKey)}`
    + '?fields=timespent,timeoriginalestimate,timeestimate';
  let lastError;

  for (let attempt = 0; attempt <= options.retries; attempt += 1) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), options.timeoutMs);
    try {
      const response = await options.fetchImpl(url, {
        method: 'GET',
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${options.token}`
        },
        signal: controller.signal
      });
      if (response.ok) {
        const body = await response.json();
        const fields = body?.fields ?? {};
        return {
          timeSpentSeconds: nonNegativeInteger(fields.timespent),
          originalEstimateSeconds: nonNegativeInteger(fields.timeoriginalestimate),
          remainingEstimateSeconds: nonNegativeInteger(fields.timeestimate),
          timeTrackingCapturedAt: options.capturedAt
        };
      }
      const error = new Error(`Jira HTTP ${response.status} dla ${issueKey}`);
      error.status = response.status;
      if (response.status === 401 || response.status === 403 || response.status === 404) {
        throw error;
      }
      lastError = error;
      if (!isRetryableStatus(response.status) || attempt === options.retries) {
        throw error;
      }
      await sleep(retryDelayMs(response, attempt));
    } catch (error) {
      lastError = error;
      if (error.status === 401 || error.status === 403 || error.status === 404) {
        throw error;
      }
      if (error.status && !isRetryableStatus(error.status)) {
        throw error;
      }
      if (attempt === options.retries) {
        throw error;
      }
      await sleep(500 * 2 ** attempt);
    } finally {
      clearTimeout(timeout);
    }
  }
  throw lastError;
}

export async function collectJiraSnapshots(issueKeys, options) {
  const uniqueKeys = new Map();
  for (const issueKey of issueKeys) {
    const normalized = normalizeIssueKey(issueKey);
    if (!uniqueKeys.has(normalized)) {
      uniqueKeys.set(normalized, issueKey);
    }
  }
  const queue = Array.from(uniqueKeys.entries());
  const snapshots = new Map();
  const failures = [];
  let nextIndex = 0;

  async function worker() {
    while (true) {
      const current = nextIndex;
      nextIndex += 1;
      if (current >= queue.length) {
        return;
      }
      const [normalized, issueKey] = queue[current];
      try {
        snapshots.set(normalized, await fetchJiraTimeTracking(issueKey, options));
      } catch (error) {
        if (error.status === 401 || error.status === 403) {
          throw error;
        }
        failures.push({ issueKey, message: safeErrorMessage(error) });
      }
    }
  }

  await Promise.all(
    Array.from({ length: Math.min(options.concurrency, queue.length || 1) }, () => worker())
  );
  if (snapshots.size === 0 && failures.length > 0) {
    throw new Error(`Nie pobrano danych zadnego issue. Pierwszy blad: ${failures[0].message}`);
  }
  return { snapshots, failures, requestedIssueCount: queue.length };
}

export async function enrichDirectory(options) {
  const directory = path.resolve(options.directory);
  validateBaseUrl(options.baseUrl);
  validateOutputDirectory(options.outputDirectory);
  const entries = await fs.readdir(directory, { withFileTypes: true });
  const fileNames = entries
    .filter((entry) => entry.isFile() && entry.name.toLowerCase().endsWith('.csv'))
    .map((entry) => entry.name)
    .sort((first, second) => first.localeCompare(second, 'pl'));
  if (fileNames.length === 0) {
    throw new Error(`Brak plikow CSV w katalogu ${directory}.`);
  }

  const documents = [];
  for (const fileName of fileNames) {
    const content = await fs.readFile(path.join(directory, fileName), 'utf8');
    documents.push(inspectAssessmentCsv(fileName, parseExcelCsv(content)));
  }
  if (!options.dryRun && !options.inPlace && !options.overwrite) {
    const conflicts = [];
    for (const document of documents) {
      const destinationPath = path.join(directory, options.outputDirectory, document.fileName);
      if (await fileExists(destinationPath)) {
        conflicts.push(document.fileName);
      }
    }
    if (conflicts.length > 0) {
      throw new Error(
        `Pliki wynikowe juz istnieja: ${conflicts.join(', ')}. Uzyj --overwrite albo innego --output-directory.`
      );
    }
  }
  const issueKeys = documents.flatMap((document) => document.issueKeys);
  const capture = await withTlsVerificationMode(options.insecureTls, () =>
    collectJiraSnapshots(issueKeys, {
      baseUrl: options.baseUrl,
      token: options.token,
      capturedAt: options.capturedAt ?? new Date().toISOString(),
      concurrency: options.concurrency,
      timeoutMs: options.timeoutMs,
      retries: options.retries,
      fetchImpl: options.fetchImpl ?? fetch
    })
  );

  const outputs = [];
  for (const document of documents) {
    const enriched = enrichAssessmentCsv(document, capture.snapshots);
    const sourcePath = path.join(directory, document.fileName);
    const destinationPath = options.inPlace
      ? sourcePath
      : path.join(directory, options.outputDirectory, document.fileName);
    outputs.push({ sourcePath, destinationPath, ...enriched });
  }

  if (!options.dryRun) {
    if (!options.inPlace) {
      await fs.mkdir(path.join(directory, options.outputDirectory), { recursive: true });
    }
    for (const output of outputs) {
      await writeOutput(output, options);
    }
  }

  return {
    directory,
    fileCount: documents.length,
    rowCount: documents.reduce((total, document) => total + document.dataRows.length, 0),
    requestedIssueCount: capture.requestedIssueCount,
    fetchedIssueCount: capture.snapshots.size,
    failures: capture.failures,
    outputs
  };
}

async function writeOutput(output, options) {
  if (!options.inPlace && !options.overwrite) {
    await fs.writeFile(output.destinationPath, output.content, { encoding: 'utf8', flag: 'wx' });
    return;
  }
  if (options.inPlace) {
    const backupPath = `${output.sourcePath}.bak-${fileTimestamp()}`;
    await fs.copyFile(output.sourcePath, backupPath, fsConstants.COPYFILE_EXCL);
  }
  const temporaryPath = `${output.destinationPath}.tmp-${process.pid}-${Date.now()}`;
  await fs.writeFile(temporaryPath, output.content, 'utf8');
  try {
    await fs.rename(temporaryPath, output.destinationPath);
  } catch (error) {
    await fs.rm(temporaryPath, { force: true });
    throw error;
  }
}

function csvCell(value) {
  if (value === null || value === undefined) {
    return '';
  }
  const text = String(value);
  if (!/[;"\r\n]/.test(text)) {
    return text;
  }
  return `"${text.replace(/"/g, '""')}"`;
}

function nonNegativeInteger(value) {
  return Number.isSafeInteger(value) && value >= 0 ? value : null;
}

function normalizeIssueKey(issueKey) {
  return issueKey.trim().toUpperCase();
}

function isRetryableStatus(status) {
  return status === 429 || status >= 500;
}

function retryDelayMs(response, attempt) {
  const retryAfter = response.headers.get('retry-after');
  if (retryAfter) {
    const seconds = Number(retryAfter);
    if (Number.isFinite(seconds) && seconds >= 0) {
      return Math.min(30_000, seconds * 1000);
    }
    const date = Date.parse(retryAfter);
    if (!Number.isNaN(date)) {
      return Math.min(30_000, Math.max(0, date - Date.now()));
    }
  }
  return 500 * 2 ** attempt;
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function fileExists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch (error) {
    if (error?.code === 'ENOENT') {
      return false;
    }
    throw error;
  }
}

async function withTlsVerificationMode(insecureTls, operation) {
  if (!insecureTls) {
    return operation();
  }
  const previous = process.env.NODE_TLS_REJECT_UNAUTHORIZED;
  process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';
  try {
    return await operation();
  } finally {
    if (previous === undefined) {
      delete process.env.NODE_TLS_REJECT_UNAUTHORIZED;
    } else {
      process.env.NODE_TLS_REJECT_UNAUTHORIZED = previous;
    }
  }
}

function safeErrorMessage(error) {
  if (error?.name === 'AbortError') {
    return 'przekroczono limit czasu zapytania do Jira';
  }
  return error instanceof Error ? error.message : String(error);
}

function fileTimestamp() {
  return new Date().toISOString().replace(/[:.]/g, '-');
}

function parsePositiveInteger(value, optionName) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error(`${optionName} wymaga dodatniej liczby calkowitej.`);
  }
  return parsed;
}

export function parseArguments(argv, environment = process.env) {
  const options = {
    directory: process.cwd(),
    outputDirectory: DEFAULT_OUTPUT_DIRECTORY,
    baseUrl: (environment.JIRA_BASE_URL ?? environment.ANALYSIS_JIRA_BASE_URL)?.trim() ?? '',
    token: (environment.JIRA_TOKEN ?? environment.ANALYSIS_JIRA_TOKEN)?.trim() ?? '',
    concurrency: DEFAULT_CONCURRENCY,
    timeoutMs: DEFAULT_TIMEOUT_MS,
    retries: DEFAULT_RETRIES,
    inPlace: false,
    overwrite: false,
    dryRun: false,
    insecureTls: ['1', 'true', 'yes'].includes(
      (environment.JIRA_INSECURE_TLS ?? '').trim().toLowerCase()
    ),
    help: false
  };

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    const nextValue = () => {
      const value = argv[index + 1];
      if (!value || value.startsWith('--')) {
        throw new Error(`${argument} wymaga wartosci.`);
      }
      index += 1;
      return value;
    };
    if (argument === '--directory') {
      options.directory = nextValue();
    } else if (argument === '--output-directory') {
      options.outputDirectory = nextValue();
    } else if (argument === '--base-url') {
      options.baseUrl = nextValue().trim();
    } else if (argument === '--concurrency') {
      options.concurrency = parsePositiveInteger(nextValue(), argument);
      if (options.concurrency > 50) {
        throw new Error('--concurrency nie moze byc wieksze niz 50.');
      }
    } else if (argument === '--timeout-ms') {
      options.timeoutMs = parsePositiveInteger(nextValue(), argument);
    } else if (argument === '--retries') {
      const retries = Number(nextValue());
      if (!Number.isSafeInteger(retries) || retries < 0) {
        throw new Error(`${argument} wymaga nieujemnej liczby calkowitej.`);
      }
      if (retries > 10) {
        throw new Error('--retries nie moze byc wieksze niz 10.');
      }
      options.retries = retries;
    } else if (argument === '--in-place') {
      options.inPlace = true;
    } else if (argument === '--overwrite') {
      options.overwrite = true;
    } else if (argument === '--dry-run') {
      options.dryRun = true;
    } else if (argument === '--insecure') {
      options.insecureTls = true;
    } else if (argument === '--help' || argument === '-h') {
      options.help = true;
    } else {
      throw new Error(`Nieznany argument: ${argument}`);
    }
  }
  if (options.inPlace && options.outputDirectory !== DEFAULT_OUTPUT_DIRECTORY) {
    throw new Error('--in-place nie moze byc laczone z --output-directory.');
  }
  validateOutputDirectory(options.outputDirectory);
  return options;
}

function validateBaseUrl(baseUrl) {
  let parsed;
  try {
    parsed = new URL(baseUrl);
  } catch {
    throw new Error(`Nieprawidlowy adres Jira: ${baseUrl}`);
  }
  if (!['http:', 'https:'].includes(parsed.protocol) || parsed.username || parsed.password) {
    throw new Error('Adres Jira musi byc URL HTTP(S) bez loginu i hasla.');
  }
}

function validateOutputDirectory(directoryName) {
  if (!directoryName
    || path.isAbsolute(directoryName)
    || directoryName === '.'
    || directoryName === '..'
    || directoryName.includes('/')
    || directoryName.includes('\\')) {
    throw new Error('--output-directory musi byc nazwa bezposredniego podkatalogu.');
  }
}

function usage() {
  return `
Uzupelnia starsze eksporty CSV assessmentow danymi time tracking z Jira.

Uzycie w PowerShell:
  $env:JIRA_BASE_URL = "https://jira.example.com"
  $env:JIRA_TOKEN = "personal-access-token"
  node "C:\\sciezka\\do\\enrich-assessment-csv-time-tracking.mjs"

Obslugiwane sa tez ANALYSIS_JIRA_BASE_URL i ANALYSIS_JIRA_TOKEN.

Opcje:
  --directory <katalog>         katalog z CSV; domyslnie aktualny katalog
  --output-directory <nazwa>   katalog wynikowy; domyslnie ${DEFAULT_OUTPUT_DIRECTORY}
  --base-url <url>              nadpisuje JIRA_BASE_URL
  --concurrency <n>            liczba rownoleglych zapytan; domyslnie ${DEFAULT_CONCURRENCY}
  --timeout-ms <n>             timeout pojedynczego zapytania; domyslnie ${DEFAULT_TIMEOUT_MS}
  --retries <n>                ponowienia dla HTTP 429/5xx; domyslnie ${DEFAULT_RETRIES}
  --dry-run                    pobiera i waliduje dane, ale nie zapisuje plikow
  --insecure                   wylacza weryfikacje TLS tylko dla zapytan Jira
  --overwrite                  pozwala nadpisac pliki w katalogu wynikowym
  --in-place                   nadpisuje zrodla po utworzeniu kopii .csv.bak-<timestamp>
  --help                       pokazuje te pomoc
`;
}

export async function runCli(argv = process.argv.slice(2), environment = process.env) {
  const options = parseArguments(argv, environment);
  if (options.help) {
    console.log(usage());
    return 0;
  }
  if (!options.baseUrl) {
    throw new Error('Brak JIRA_BASE_URL/ANALYSIS_JIRA_BASE_URL albo opcji --base-url.');
  }
  if (!options.token) {
    throw new Error('Brak JIRA_TOKEN/ANALYSIS_JIRA_TOKEN. Token nie jest przyjmowany jako argument CLI.');
  }

  console.log(`Katalog: ${path.resolve(options.directory)}`);
  console.log(options.dryRun
    ? 'Tryb dry-run: pliki nie zostana zapisane.'
    : options.inPlace
      ? 'Tryb in-place: przed zmiana kazdego CSV powstanie kopia zapasowa.'
      : `Wynik: podkatalog ${options.outputDirectory}`
  );
  if (options.insecureTls) {
    console.warn(
      'UWAGA: weryfikacja certyfikatu TLS jest wylaczona dla zapytan Jira w tym procesie.'
    );
  }
  const result = await enrichDirectory(options);
  console.log(
    `Pliki: ${result.fileCount}, wiersze: ${result.rowCount}, unikalne issue: ${result.requestedIssueCount}.`
  );
  console.log(`Pobrano z Jira: ${result.fetchedIssueCount}, bledy: ${result.failures.length}.`);
  for (const output of result.outputs) {
    console.log(
      `${path.basename(output.sourcePath)}: uzupelniono ${output.updatedRows}, pozostawiono ${output.unchangedRows}.`
    );
  }
  if (result.failures.length > 0) {
    for (const failure of result.failures.slice(0, 20)) {
      console.error(`- ${failure.issueKey}: ${failure.message}`);
    }
    if (result.failures.length > 20) {
      console.error(`... oraz ${result.failures.length - 20} kolejnych bledow.`);
    }
    return 2;
  }
  return 0;
}

const isMain = process.argv[1]
  && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
if (isMain) {
  runCli()
    .then((exitCode) => {
      process.exitCode = exitCode;
    })
    .catch((error) => {
      console.error(`BLAD: ${safeErrorMessage(error)}`);
      process.exitCode = 1;
    });
}
