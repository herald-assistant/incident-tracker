import { reportChangeDiff } from './report-change-diff.utils';

describe('reportChangeDiff', () => {
  it('keeps surrounding raw Markdown and marks changed lines', () => {
    expect(reportChangeDiff('# CRM\n**Stary opis**\nKoniec', '# CRM\n**Nowy opis**\nKoniec')).toEqual([
      { kind: 'context', text: '# CRM' },
      { kind: 'removed', text: '**Stary opis**' },
      { kind: 'added', text: '**Nowy opis**' },
      { kind: 'context', text: 'Koniec' }
    ]);
  });

  it('shows a newly inserted line without marking unchanged lines', () => {
    expect(reportChangeDiff('A\nC', 'A\nB\nC')).toEqual([
      { kind: 'context', text: 'A' },
      { kind: 'added', text: 'B' },
      { kind: 'context', text: 'C' }
    ]);
  });
});
