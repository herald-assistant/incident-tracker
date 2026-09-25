import { AfterViewInit, Component, ElementRef, OnDestroy, effect, input, output, viewChild } from '@angular/core';
import { markdown, markdownLanguage } from '@codemirror/lang-markdown';
import { HighlightStyle, syntaxHighlighting } from '@codemirror/language';
import { Compartment, EditorState } from '@codemirror/state';
import { EditorView, minimalSetup } from 'codemirror';
import { tags } from '@lezer/highlight';

const markdownHighlight = HighlightStyle.define([
  { tag: tags.heading, color: 'var(--primary)', fontWeight: '700' },
  { tag: tags.strong, color: 'var(--ai-text)', fontWeight: '700' },
  { tag: tags.emphasis, color: 'var(--success-text)', fontStyle: 'italic' },
  { tag: tags.link, color: 'var(--primary)', textDecoration: 'underline' },
  { tag: tags.url, color: 'var(--primary-dark)' },
  { tag: tags.monospace, color: 'var(--warning-text)', backgroundColor: 'var(--warning-soft)' },
  { tag: tags.quote, color: 'var(--success-text)' }
]);

const editorTheme = EditorView.theme({
  '&': {
    minWidth: '0',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-md)',
    backgroundColor: 'var(--surface)',
    color: 'var(--text-main)',
    fontFamily: 'var(--font-sans)',
    fontSize: '1rem',
    lineHeight: '1.5'
  },
  '&.cm-focused': {
    outline: 'none',
    borderColor: 'var(--primary)',
    boxShadow: 'var(--focus-ring)'
  },
  '.cm-content': { minHeight: '15em', padding: '.75rem 0' },
  '.cm-line': { padding: '0 .75rem' },
  '.cm-scroller': { overflowWrap: 'anywhere' }
});

@Component({
  selector: 'app-markdown-source-editor',
  template: '<div #host></div>',
  styleUrl: './markdown-source-editor.scss'
})
export class MarkdownSourceEditorComponent implements AfterViewInit, OnDestroy {
  readonly value = input('');
  readonly disabled = input(false);
  readonly labelledBy = input.required<string>();
  readonly valueChange = output<string>();

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('host');
  private readonly editability = new Compartment();
  private view?: EditorView;
  private applyingExternalValue = false;

  constructor() {
    effect(() => {
      const value = this.value();
      if (!this.view) return;

      if (this.view.state.doc.toString() !== value) {
        this.applyingExternalValue = true;
        try {
          this.view.dispatch({ changes: { from: 0, to: this.view.state.doc.length, insert: value } });
        } finally {
          this.applyingExternalValue = false;
        }
      }
    });
    effect(() => {
      const disabled = this.disabled();
      this.view?.dispatch({ effects: this.editability.reconfigure(this.editabilityExtensions(disabled)) });
    });
  }

  ngAfterViewInit(): void {
    this.view = new EditorView({
      doc: this.value(),
      parent: this.host().nativeElement,
      extensions: [
        minimalSetup,
        markdown({ base: markdownLanguage, completeHTMLTags: false }),
        EditorView.lineWrapping,
        syntaxHighlighting(markdownHighlight),
        editorTheme,
        EditorView.contentAttributes.of({ 'aria-labelledby': this.labelledBy(), spellcheck: 'false' }),
        this.editability.of(this.editabilityExtensions(this.disabled())),
        EditorView.updateListener.of(update => {
          if (update.docChanged && !this.applyingExternalValue) {
            this.valueChange.emit(update.state.doc.toString());
          }
        })
      ]
    });
  }

  ngOnDestroy(): void {
    this.view?.destroy();
  }

  private editabilityExtensions(disabled: boolean) {
    return [EditorState.readOnly.of(disabled), EditorView.editable.of(!disabled)];
  }
}
