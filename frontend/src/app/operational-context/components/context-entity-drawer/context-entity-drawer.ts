import { Component, input, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import {
  OperationalContextAiApiPreview,
  OperationalContextDetailSectionDto,
  OperationalContextEntityDetailDto,
  OperationalContextReadModelProfile,
  OperationalContextResolvedOwnerDto,
  OperationalContextResolvedOwnershipDto
} from '../../models/operational-context.models';
import { copyTextToClipboard } from '../../../core/utils/clipboard.utils';
import { AiApiPreviewPanelComponent } from '../ai-api-preview-panel/ai-api-preview-panel';
import { WhyPopoverComponent } from '../why-popover/why-popover';

interface DrawerField {
  key: string;
  value: string;
}

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

  protected fieldEntries(fields: Record<string, unknown>): DrawerField[] {
    return Object.entries(fields || {}).map(([key, value]) => ({
      key,
      value: this.renderValue(value)
    }));
  }

  protected resolvedOwnership(
    section: OperationalContextDetailSectionDto
  ): OperationalContextResolvedOwnershipDto | null {
    if (section.title.toLowerCase() !== 'resolved ownership') {
      return null;
    }

    const fields = section.fields || {};
    return {
      situationType: this.stringValue(fields['situationType']),
      primaryOwners: this.ownerArray(fields['primaryOwners']),
      partnerOwners: this.ownerArray(fields['partnerOwners']),
      resolutionPath: this.stringArray(fields['resolutionPath']),
      handoffReason: this.nullableString(fields['handoffReason']),
      visibilityLimits: this.stringArray(fields['visibilityLimits'])
    };
  }

  protected situationLabel(situationType: string): string {
    const labels: Record<string, string> = {
      'inside-system': 'inside system',
      'inside-bounded-context': 'inside bounded context',
      'system-boundary': 'system boundary',
      'bounded-context-boundary': 'bounded context boundary',
      'system-infrastructure': 'system infrastructure',
      unknown: 'unknown'
    };
    return labels[situationType] || this.humanizeToken(situationType);
  }

  protected ownerLabel(owner: OperationalContextResolvedOwnerDto): string {
    if (owner.ownerLabel) {
      return owner.ownerLabel;
    }
    if (owner.ownerTeamIds.length) {
      return owner.ownerTeamIds.join(', ');
    }
    return owner.targetLabel || owner.targetId || 'Unresolved owner';
  }

  protected ownerTarget(owner: OperationalContextResolvedOwnerDto): string {
    const label = owner.targetLabel || owner.targetId || 'unknown target';
    return `${this.humanizeToken(owner.targetType)}: ${label}`;
  }

  protected ownerMeta(owner: OperationalContextResolvedOwnerDto): string[] {
    return [
      owner.source ? `source: ${this.humanizeToken(owner.source)}` : '',
      owner.confidence ? `confidence: ${owner.confidence}` : '',
      owner.ownerTeamIds.length ? `teams: ${owner.ownerTeamIds.join(', ')}` : ''
    ].filter(Boolean);
  }

  protected trackOwner(index: number, owner: OperationalContextResolvedOwnerDto): string {
    return [
      owner.targetType,
      owner.targetId,
      owner.ownerLabel,
      owner.ownerTeamIds.join('|'),
      index
    ].filter((part) => part !== null && part !== undefined && part !== '').join(':');
  }

  protected hasItems(items: string[]): boolean {
    return items.some((item) => item.trim().length > 0);
  }

  protected renderValue(value: unknown): string {
    if (Array.isArray(value)) {
      return value.map((item) => this.renderValue(item)).join(', ');
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

  private ownerArray(value: unknown): OperationalContextResolvedOwnerDto[] {
    if (!Array.isArray(value)) {
      return [];
    }

    return value
      .filter((item): item is Record<string, unknown> => this.isRecord(item))
      .map((item) => ({
        targetType: this.stringValue(item['targetType']),
        targetId: this.nullableString(item['targetId']),
        targetLabel: this.nullableString(item['targetLabel']),
        ownerTeamIds: this.stringArray(item['ownerTeamIds']),
        ownerLabel: this.nullableString(item['ownerLabel']),
        source: this.stringValue(item['source']),
        confidence: this.stringValue(item['confidence'])
      }));
  }

  private stringArray(value: unknown): string[] {
    if (!Array.isArray(value)) {
      return [];
    }
    return value
      .map((item) => this.renderValue(item).trim())
      .filter(Boolean);
  }

  private stringValue(value: unknown): string {
    return this.renderValue(value).trim();
  }

  private nullableString(value: unknown): string | null {
    const rendered = this.stringValue(value);
    return rendered ? rendered : null;
  }

  private humanizeToken(value: string): string {
    return value.replace(/[-_]+/g, ' ').trim() || value;
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return Boolean(value && typeof value === 'object' && !Array.isArray(value));
  }
}
