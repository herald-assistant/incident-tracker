const CSV_SEPARATOR = ';';
const CSV_LINE_BREAK = '\r\n';

export type CsvValue = string | number | null | undefined;

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
