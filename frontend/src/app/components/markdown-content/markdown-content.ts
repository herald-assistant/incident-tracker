import { Component, computed, input } from '@angular/core';

import { markdownToHtml } from '../../core/utils/markdown.utils';

@Component({
  selector: 'app-markdown-content',
  imports: [],
  templateUrl: './markdown-content.html',
  styleUrl: './markdown-content.scss'
})
export class MarkdownContentComponent {
  readonly content = input('');
  readonly compact = input(false);

  protected readonly renderedHtml = computed(() => markdownToHtml(this.content()));
}
