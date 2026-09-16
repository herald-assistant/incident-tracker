import { DOCUMENT } from '@angular/common';
import { Component, HostListener, inject, input, output } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';

import { buildBrowserToolsBookmarkletUrl } from '../../core/utils/browser-tools-bookmarklet.utils';

@Component({
  selector: 'app-browser-tools-setup-modal',
  templateUrl: './browser-tools-setup-modal.html',
  styleUrl: './browser-tools-setup-modal.scss'
})
export class BrowserToolsSetupModalComponent {
  private readonly document = inject(DOCUMENT);
  private readonly sanitizer = inject(DomSanitizer);

  readonly open = input(false);
  readonly closed = output<void>();
  readonly bookmarkletHref: SafeUrl = this.sanitizer.bypassSecurityTrustUrl(
    buildBrowserToolsBookmarkletUrl(this.document.location.origin)
  );

  @HostListener('document:keydown.escape')
  closeOnEscape(): void {
    if (this.open()) {
      this.closed.emit();
    }
  }

  closeFromBackdrop(event: MouseEvent): void {
    if (event.target === event.currentTarget) {
      this.closed.emit();
    }
  }
}
