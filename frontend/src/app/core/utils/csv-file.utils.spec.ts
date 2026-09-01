import { CsvParseError, buildExcelCsv, parseExcelCsv } from './csv-file.utils';

describe('CSV file utilities', () => {
  it('should build a semicolon-separated UTF-8 BOM document with CRLF lines', () => {
    const csv = buildExcelCsv([
      ['name', 'score', 'note'],
      ['Profil klienta', 72.5, null]
    ]);

    expect(csv).toBe('\uFEFFname;score;note\r\nProfil klienta;72,5;\r\n');
  });

  it('should quote separators, quotes and line breaks', () => {
    const csv = buildExcelCsv([
      ['Summary'],
      ['Wariant; "pilny"\nDruga linia']
    ]);

    expect(csv).toContain('"Wariant; ""pilny""\nDruga linia"');
  });

  it('should parse the writer output including BOM, decimal commas and quoted new lines', () => {
    const csv = buildExcelCsv([
      ['name', 'score', 'note'],
      ['Profil klienta', 72.5, 'Wariant; "pilny"\nDruga linia']
    ]);

    expect(parseExcelCsv(csv)).toEqual([
      ['name', 'score', 'note'],
      ['Profil klienta', '72,5', 'Wariant; "pilny"\nDruga linia']
    ]);
  });

  it('should parse LF rows and preserve empty cells', () => {
    expect(parseExcelCsv('a;b;c\n1;;3')).toEqual([
      ['a', 'b', 'c'],
      ['1', '', '3']
    ]);
  });

  it('should reject malformed quoted cells with a useful location', () => {
    expect(() => parseExcelCsv('a;b\n"unfinished;b')).toThrowError(CsvParseError);
    expect(() => parseExcelCsv('a;b\n"closed"x;b')).toThrowError(/wiersz 2, kolumna 9/);
  });
});
