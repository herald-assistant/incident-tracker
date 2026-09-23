import { Component, input, signal } from '@angular/core';
import { MatMenuModule } from '@angular/material/menu';

import { AnalysisShareDocument } from '../../core/utils/analysis-share.utils';
import { copyTextToClipboard } from '../../core/utils/clipboard.utils';

@Component({
  selector: 'app-analysis-share-menu',
  imports: [MatMenuModule],
  templateUrl: './analysis-share-menu.html',
  styleUrl: './analysis-share-menu.scss'
})
export class AnalysisShareMenuComponent {
  readonly document = input<AnalysisShareDocument | null>(null);
  readonly feedback = signal('');

  protected async copy(includeMeta: boolean): Promise<void> {
    const content = this.content(includeMeta);
    if (!content) return;
    this.feedback.set(
      (await copyTextToClipboard(content)) ? 'Skopiowano Markdown.' : 'Nie udało się skopiować Markdown.'
    );
  }

  protected download(includeMeta: boolean): void {
    const content = this.content(includeMeta);
    const document = this.document();
    if (!document || !content) return;
    const fileName = includeMeta
      ? document.fileName.replace(/\.md$/i, '-meta.md')
      : document.fileName;
    const url = URL.createObjectURL(new Blob([content], { type: 'text/markdown;charset=utf-8' }));
    const link = window.document.createElement('a');
    link.href = url;
    link.download = fileName;
    window.document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 0);
    this.feedback.set('Pobrano Markdown.');
  }

  private content(includeMeta: boolean): string {
    const document = this.document();
    return (includeMeta ? document?.markdownWithMeta : document?.markdown)?.trim() || '';
  }
}
