import { DOCUMENT } from '@angular/common';
import { Component, computed, inject, input } from '@angular/core';

import { markdownToHtml } from '../../core/utils/markdown.utils';

@Component({
  selector: 'app-markdown-content',
  imports: [],
  templateUrl: './markdown-content.html',
  styleUrl: './markdown-content.scss'
})
export class MarkdownContentComponent {
  private readonly document = inject(DOCUMENT);
  readonly content = input('');
  readonly compact = input(false);

  protected readonly renderedHtml = computed(() => markdownToHtml(this.content()));

  protected navigateFragment(event: MouseEvent): void {
    const anchor = event.target instanceof Element ? event.target.closest('a') : null;
    const href = anchor?.getAttribute('href');
    if (!href?.startsWith('#')) return;
    // A hash resolved against Angular's base URL would otherwise leave the current route.
    event.preventDefault();
    let id: string;
    try { id = decodeURIComponent(href.slice(1)); } catch { return; }
    const target = this.document.getElementById(id);
    if (!target) return;
    let parent: HTMLElement | null = target;
    while (parent) {
      if (parent instanceof HTMLDetailsElement) parent.open = true;
      parent = parent.parentElement;
    }
    target.scrollIntoView?.({ block: 'start', behavior: 'smooth' });
  }
}
