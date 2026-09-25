import { TestBed } from '@angular/core/testing';
import { syntaxTree } from '@codemirror/language';
import { EditorView } from 'codemirror';

import { MarkdownSourceEditorComponent } from './markdown-source-editor';

describe('MarkdownSourceEditorComponent', () => {
  it('highlights Markdown, emits user edits and accepts external value and disabled updates', async () => {
    await TestBed.configureTestingModule({ imports: [MarkdownSourceEditorComponent] }).compileComponents();
    const fixture = TestBed.createComponent(MarkdownSourceEditorComponent);
    fixture.componentRef.setInput('labelledBy', 'crm-section-title');
    fixture.componentRef.setInput('value', '# CRM\n\n**Ważna sprawa**');
    const changed = vi.fn();
    fixture.componentInstance.valueChange.subscribe(changed);
    fixture.detectChanges();

    const view = EditorView.findFromDOM((fixture.nativeElement as HTMLElement).querySelector('.cm-editor')!);
    expect(view).not.toBeNull();
    expect(view!.contentDOM.getAttribute('aria-labelledby')).toBe('crm-section-title');
    expect(syntaxTree(view!.state).toString()).toContain('ATXHeading');
    expect(view!.dom.querySelector('.cm-line span')).not.toBeNull();

    view!.dispatch({ changes: { from: 0, to: view!.state.doc.length, insert: '## Poprawiony opis CRM' } });
    expect(changed).toHaveBeenCalledWith('## Poprawiony opis CRM');

    changed.mockClear();
    fixture.componentRef.setInput('value', 'Tekst z odświeżonego raportu.');
    fixture.componentRef.setInput('disabled', true);
    fixture.detectChanges();
    expect(view!.state.doc.toString()).toBe('Tekst z odświeżonego raportu.');
    expect(view!.state.readOnly).toBe(true);
    expect(view!.contentDOM.getAttribute('contenteditable')).toBe('false');
    expect(changed).not.toHaveBeenCalled();

    fixture.destroy();
  });
});
