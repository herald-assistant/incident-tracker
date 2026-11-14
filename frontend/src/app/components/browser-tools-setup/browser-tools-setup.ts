import { DOCUMENT } from '@angular/common';
import { Component, inject } from '@angular/core';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';

import { buildBrowserToolsBookmarkletUrl } from '../../core/utils/browser-tools-bookmarklet.utils';

@Component({
  selector: 'app-browser-tools-setup',
  templateUrl: './browser-tools-setup.html',
  styleUrl: './browser-tools-setup.scss'
})
export class BrowserToolsSetupComponent {
  private readonly document = inject(DOCUMENT);
  private readonly sanitizer = inject(DomSanitizer);

  readonly bookmarkletHref: SafeUrl = this.sanitizer.bypassSecurityTrustUrl(
    buildBrowserToolsBookmarkletUrl(this.document.location.origin)
  );

}
