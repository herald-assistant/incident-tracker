import { Component, input, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import {
  OperationalContextAiApiPreviewEndpoint,
  OperationalContextReadModelLinkDto,
  OperationalContextReadModelNextReadDto,
  OperationalContextReadModelProfile
} from '../../models/operational-context.models';
import { copyTextToClipboard } from '../../../core/utils/clipboard.utils';

interface PreviewEntry {
  label: string;
  value: string;
}

@Component({
  selector: 'app-ai-api-preview-panel',
  imports: [MatIconModule],
  templateUrl: './ai-api-preview-panel.html',
  styleUrl: './ai-api-preview-panel.scss'
})
export class AiApiPreviewPanelComponent {
  readonly eyebrow = input('AI API Preview');
  readonly title = input('API payload');
  readonly variant = input<'card' | 'section'>('card');
  readonly endpoints = input<OperationalContextAiApiPreviewEndpoint[]>([]);
  readonly profile = input<OperationalContextReadModelProfile>('default');
  readonly loading = input(false);
  readonly error = input('');
  readonly emptyText = input('No AI API preview loaded.');
  readonly profileChange = output<OperationalContextReadModelProfile>();
  readonly copiedKey = signal('');

  protected selectProfile(profile: OperationalContextReadModelProfile): void {
    if (profile !== this.profile()) {
      this.profileChange.emit(profile);
    }
  }

  protected endpointMeta(endpoint: OperationalContextAiApiPreviewEndpoint): string[] {
    const payload = endpoint.payload;
    if (!payload) {
      return endpoint.error ? ['error'] : ['empty'];
    }

    return [
      payload.profile,
      payload.confidence ? `${payload.confidence} confidence` : '',
      payload.relevanceScore !== null && payload.relevanceScore !== undefined
        ? `score ${payload.relevanceScore}`
        : '',
      payload.truncation?.truncated ? 'truncated' : ''
    ].filter(Boolean);
  }

  protected targetEntries(endpoint: OperationalContextAiApiPreviewEndpoint): PreviewEntry[] {
    const target = endpoint.payload?.analysisTarget;
    if (!target) {
      return [];
    }
    if (!this.isRecord(target)) {
      return [{ label: 'Target', value: this.renderValue(target) }];
    }

    const preferredKeys = ['type', 'id', 'label', 'summary', 'query'];
    const entries: PreviewEntry[] = [];
    const append = (key: string): void => {
      if (!(key in target)) {
        return;
      }
      const value = target[key];
      if (value === null || value === undefined || value === '') {
        return;
      }
      entries.push({ label: this.titleCase(key), value: this.renderValue(value) });
    };

    preferredKeys.forEach(append);
    Object.keys(target)
      .filter((key) => !preferredKeys.includes(key))
      .forEach(append);
    return entries;
  }

  protected targetSummary(endpoint: OperationalContextAiApiPreviewEndpoint): string {
    const target = endpoint.payload?.analysisTarget;
    if (!target) {
      return 'No target';
    }
    if (!this.isRecord(target)) {
      return this.renderValue(target);
    }

    const label = target['label'] || target['id'] || target['query'];
    const type = target['type'];
    return [type, label].filter(Boolean).map((value) => this.renderValue(value)).join(' / ');
  }

  protected hasQuality(endpoint: OperationalContextAiApiPreviewEndpoint): boolean {
    const payload = endpoint.payload;
    return Boolean(
      payload
        && (
          payload.limitations.length
          || payload.omittedBecause.length
          || payload.reasonToExpand
          || payload.truncation?.truncated
          || this.returnedCounts(endpoint)
          || this.omittedCounts(endpoint)
        )
    );
  }

  protected qualitySummary(endpoint: OperationalContextAiApiPreviewEndpoint): string {
    const payload = endpoint.payload;
    if (!payload) {
      return '';
    }
    if (payload.truncation?.truncated) {
      return 'truncated';
    }
    if (payload.limitations.length) {
      return `${payload.limitations.length} limitations`;
    }
    if (payload.omittedBecause.length) {
      return `${payload.omittedBecause.length} omitted notes`;
    }
    return 'counts and limits';
  }

  protected qualityEntries(endpoint: OperationalContextAiApiPreviewEndpoint): PreviewEntry[] {
    const payload = endpoint.payload;
    if (!payload) {
      return [];
    }

    return [
      { label: 'Reason to expand', value: payload.reasonToExpand || '' },
      { label: 'Returned', value: this.returnedCounts(endpoint) },
      { label: 'Omitted', value: this.omittedCounts(endpoint) }
    ].filter((entry) => entry.value);
  }

  protected nextReads(endpoint: OperationalContextAiApiPreviewEndpoint): OperationalContextReadModelNextReadDto[] {
    return endpoint.payload?.nextReads || [];
  }

  protected legacyNextReads(endpoint: OperationalContextAiApiPreviewEndpoint): string[] {
    return this.nextReads(endpoint).length ? [] : endpoint.payload?.suggestedNextReads || [];
  }

  protected linkLabel(link: OperationalContextReadModelLinkDto): string {
    return this.titleCase(link.rel || 'read');
  }

  protected nextReadLabel(read: OperationalContextReadModelNextReadDto): string {
    return read.label || this.titleCase(read.rel || 'read');
  }

  protected nextReadTool(read: OperationalContextReadModelNextReadDto): string {
    return read.tool ? `${read.tool}${this.argumentSummary(read.arguments)}` : '';
  }

  protected nextReadKey(read: OperationalContextReadModelNextReadDto): string {
    return [
      read.rel,
      read.href,
      read.tool,
      JSON.stringify(read.arguments || {})
    ].filter(Boolean).join(':');
  }

  protected returnedCounts(endpoint: OperationalContextAiApiPreviewEndpoint): string {
    return this.countSummary(endpoint.payload?.truncation?.returnedCounts);
  }

  protected omittedCounts(endpoint: OperationalContextAiApiPreviewEndpoint): string {
    return this.countSummary(endpoint.payload?.truncation?.omittedCounts);
  }

  protected expansionSummary(endpoint: OperationalContextAiApiPreviewEndpoint): string {
    const count = endpoint.payload?.availableExpansions.length || 0;
    return count ? `${count} options` : 'No expansions';
  }

  protected restLinkSummary(endpoint: OperationalContextAiApiPreviewEndpoint): string {
    const count = endpoint.payload?.links.length || 0;
    return count ? `${count} links` : 'No links';
  }

  protected previewJson(endpoint: OperationalContextAiApiPreviewEndpoint): string {
    return JSON.stringify(endpoint.payload ?? { error: endpoint.error }, null, 2);
  }

  protected async copyUrl(endpoint: OperationalContextAiApiPreviewEndpoint): Promise<void> {
    const copied = await copyTextToClipboard(endpoint.url);
    this.copiedKey.set(copied ? `${endpoint.key}:url` : '');
  }

  protected async copyJson(endpoint: OperationalContextAiApiPreviewEndpoint): Promise<void> {
    const copied = await copyTextToClipboard(this.previewJson(endpoint));
    this.copiedKey.set(copied ? `${endpoint.key}:json` : '');
  }

  protected async copyLink(
    endpoint: OperationalContextAiApiPreviewEndpoint,
    link: OperationalContextReadModelLinkDto
  ): Promise<void> {
    const copied = await copyTextToClipboard(link.href);
    this.copiedKey.set(copied ? `${endpoint.key}:link:${link.rel}` : '');
  }

  protected async copyNextRead(
    endpoint: OperationalContextAiApiPreviewEndpoint,
    read: OperationalContextReadModelNextReadDto
  ): Promise<void> {
    const value = read.href || JSON.stringify({
      tool: read.tool,
      arguments: read.arguments,
      reason: read.reason
    }, null, 2);
    const copied = await copyTextToClipboard(value);
    this.copiedKey.set(copied ? `${endpoint.key}:next:${this.nextReadKey(read)}` : '');
  }

  private countSummary(values: Record<string, number> | null | undefined): string {
    return Object.entries(values || {})
      .filter(([, value]) => value > 0)
      .map(([key, value]) => `${key}: ${value}`)
      .join(', ');
  }

  private argumentSummary(args: Record<string, unknown>): string {
    const entries = Object.entries(args || {});
    if (!entries.length) {
      return '';
    }
    return '(' + entries.map(([key, value]) => `${key}=${this.argumentValue(value)}`).join(', ') + ')';
  }

  private argumentValue(value: unknown): string {
    return Array.isArray(value) ? `[${value.join(',')}]` : String(value);
  }

  private renderValue(value: unknown): string {
    if (Array.isArray(value)) {
      return value.map((item) => this.renderValue(item)).join(', ');
    }
    if (this.isRecord(value)) {
      return JSON.stringify(value);
    }
    return value === null || value === undefined ? '' : String(value);
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return Boolean(value && typeof value === 'object' && !Array.isArray(value));
  }

  private titleCase(value: string): string {
    return value
      .replace(/-/g, ' ')
      .split(' ')
      .filter(Boolean)
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }
}
