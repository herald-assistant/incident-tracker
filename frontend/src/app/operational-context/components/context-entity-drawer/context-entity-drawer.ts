import { Component, input, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import {
  OperationalContextAiApiPreview,
  OperationalContextEntityDetailDto,
  OperationalContextReadModelProfile
} from '../../models/operational-context.models';
import { copyTextToClipboard } from '../../../core/utils/clipboard.utils';
import { AiApiPreviewPanelComponent } from '../ai-api-preview-panel/ai-api-preview-panel';
import { WhyPopoverComponent } from '../why-popover/why-popover';

@Component({
  selector: 'app-context-entity-drawer',
  imports: [MatIconModule, AiApiPreviewPanelComponent, WhyPopoverComponent],
  templateUrl: './context-entity-drawer.html',
  styleUrl: './context-entity-drawer.scss'
})
export class ContextEntityDrawerComponent {
  readonly detail = input<OperationalContextEntityDetailDto | null>(null);
  readonly aiApiPreview = input<OperationalContextAiApiPreview | null>(null);
  readonly aiApiPreviewLoading = input(false);
  readonly aiApiPreviewError = input('');
  readonly aiApiPreviewProfile = input<OperationalContextReadModelProfile>('default');
  readonly aiApiPreviewProfileChange = output<OperationalContextReadModelProfile>();
  readonly closeDrawer = output<void>();
  readonly copiedEntity = signal(false);

  protected fieldEntries(fields: Record<string, unknown>): Array<{ key: string; value: string }> {
    return Object.entries(fields || {}).map(([key, value]) => ({
      key,
      value: this.renderValue(value)
    }));
  }

  protected renderValue(value: unknown): string {
    if (Array.isArray(value)) {
      return value.join(', ');
    }
    if (value && typeof value === 'object') {
      return JSON.stringify(value);
    }
    return value === null || value === undefined ? '' : String(value);
  }

  protected async copyEntityDetail(): Promise<void> {
    const detail = this.detail();
    if (!detail) {
      return;
    }

    const copied = await copyTextToClipboard(JSON.stringify(detail, null, 2));
    this.copiedEntity.set(copied);
  }

  protected openRawSource(): void {
    globalThis.document
      ?.getElementById('operational-context-raw-source')
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }
}
