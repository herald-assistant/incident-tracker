import { textDiff } from './text-diff.utils';

describe('textDiff', () => {
  it('retains every line of a large file without exceeding the argument stack', () => {
    const before=Array.from({length:130_000},(_,i)=>`old_${i}`).join('\n');
    const after=Array.from({length:130_000},(_,i)=>`new_${i}`).join('\n');
    const lines=textDiff(before,after);
    expect(lines.filter(line=>line.kind!=='added').map(line=>line.text).join('\n')).toBe(before);
    expect(lines.filter(line=>line.kind!=='removed').map(line=>line.text).join('\n')).toBe(after);
  });
  it('keeps surrounding raw Markdown and marks changed lines', () => {
    expect(textDiff('# CRM\n**Stary opis**\nKoniec', '# CRM\n**Nowy opis**\nKoniec')).toEqual([
      { kind: 'context', text: '# CRM' },
      { kind: 'removed', text: '**Stary opis**' },
      { kind: 'added', text: '**Nowy opis**' },
      { kind: 'context', text: 'Koniec' }
    ]);
  });

  it('shows a newly inserted line without marking unchanged lines', () => {
    expect(textDiff('A\nC', 'A\nB\nC')).toEqual([
      { kind: 'context', text: 'A' },
      { kind: 'added', text: 'B' },
      { kind: 'context', text: 'C' }
    ]);
  });
});
