const CSV_SEPARATOR = ';';
const CSV_LINE_BREAK = '\r\n';

export type CsvValue = string | number | null | undefined;

export class CsvParseError extends Error {
  constructor(
    message: string,
    readonly line: number,
    readonly column: number
  ) {
    super(`${message} (wiersz ${line}, kolumna ${column})`);
    this.name = 'CsvParseError';
  }
}

export function buildExcelCsv(rows: ReadonlyArray<ReadonlyArray<CsvValue>>): string {
  return '\uFEFF' + rows
    .map((row) => row.map(csvCell).join(CSV_SEPARATOR))
    .join(CSV_LINE_BREAK) + CSV_LINE_BREAK;
}

export function downloadCsvFile(fileName: string, content: string): void {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

export function parseExcelCsv(content: string): string[][] {
  const input = content.startsWith('\uFEFF') ? content.slice(1) : content;
  if (input.length === 0) {
    return [];
  }

  const rows: string[][] = [];
  let row: string[] = [];
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

    if (quoteClosed && character !== CSV_SEPARATOR && character !== '\r' && character !== '\n') {
      throw new CsvParseError('Nieoczekiwany znak po zamknieciu cytowanej komorki', line, column);
    }

    if (character === '"') {
      if (cell.length > 0) {
        throw new CsvParseError('Cudzyslow moze rozpoczynac tylko pusta komorke', line, column);
      }
      inQuotes = true;
      endedWithLineBreak = false;
    } else if (character === CSV_SEPARATOR) {
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
    throw new CsvParseError('Niezamknieta cytowana komorka', line, column);
  }
  if (!endedWithLineBreak || row.length > 0 || cell.length > 0 || quoteClosed) {
    finishRow();
  }
  return rows;
}

function csvCell(value: CsvValue): string {
  if (value === null || value === undefined) {
    return '';
  }
  const text = typeof value === 'number'
    ? String(value).replace('.', ',')
    : String(value);
  if (!/[;"\r\n]/.test(text)) {
    return text;
  }
  return `"${text.replace(/"/g, '""')}"`;
}
