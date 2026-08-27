import { buildExcelCsv } from './csv-file.utils';

describe('CSV file utilities', () => {
  it('should build a semicolon-separated UTF-8 BOM document with CRLF lines', () => {
    const csv = buildExcelCsv([
      ['name', 'score', 'note'],
      ['Płatność', 72.5, null]
    ]);

    expect(csv).toBe('\uFEFFname;score;note\r\nPłatność;72,5;\r\n');
  });

  it('should quote separators, quotes and line breaks', () => {
    const csv = buildExcelCsv([
      ['Summary'],
      ['Wariant; "pilny"\nDruga linia']
    ]);

    expect(csv).toContain('"Wariant; ""pilny""\nDruga linia"');
  });
});
