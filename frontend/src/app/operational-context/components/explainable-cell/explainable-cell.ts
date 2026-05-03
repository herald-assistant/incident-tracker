import { Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
  ExplainableAggregateDto,
  ExplainableValueDto
} from '../../models/operational-context.models';

@Component({
  selector: 'app-explainable-cell',
  imports: [MatIconModule, MatTooltipModule],
  templateUrl: './explainable-cell.html',
  styleUrl: './explainable-cell.scss'
})
export class ExplainableCellComponent {
  readonly aggregate = input<ExplainableAggregateDto | null>(null);
  readonly value = input<ExplainableValueDto<string> | null>(null);
  readonly variant = input<'card' | 'table'>('card');
  readonly active = input(false);
  readonly openDetails = output<{ type: string; id: string }>();
  readonly inspect = output<void>();

  protected readonly trackByIndex = (index: number): number => index;

  protected displayLabel(): string {
    const aggregate = this.aggregate();
    if (aggregate) {
      if (this.isTableVariant()) {
        return this.compactAggregateLabel(aggregate);
      }
      return `${aggregate.label}: ${aggregate.count}`;
    }

    const value = this.value();
    return value?.label || value?.value || 'n/a';
  }

  protected metaLabel(): string {
    const aggregate = this.aggregate();
    if (aggregate) {
      if (aggregate.severity === 'error') {
        return 'needs fix';
      }
      if (aggregate.severity === 'warning') {
        return 'review';
      }
      if (aggregate.severity === 'unknown') {
        return 'not mapped';
      }
      return 'mapped';
    }

    const value = this.value();
    if (!value?.value) {
      return 'missing';
    }
    return value.confidence ? `confidence ${value.confidence}` : 'explicit';
  }

  protected confidence(): string {
    if (this.isTableVariant()) {
      return '';
    }
    return this.aggregate()?.confidence || this.value()?.confidence || '';
  }

  protected tooltip(): string {
    const aggregate = this.aggregate();
    if (aggregate?.tooltip) {
      return aggregate.tooltip;
    }

    const value = this.value();
    if (!value) {
      return 'No explanation available.';
    }

    return value.reasons.map((reason) => `${reason.label}: ${reason.detail}`).join('\n');
  }

  protected detailsIds(): string[] {
    return this.aggregate()?.detailsIds ?? [];
  }

  protected detailsType(): string {
    return this.aggregate()?.detailsType || '';
  }

  protected visibleItems<T>(items: T[]): T[] {
    return items.slice(0, this.isTableVariant() ? 6 : 10);
  }

  protected hiddenItemCount(items: unknown[]): number {
    return Math.max(0, items.length - (this.isTableVariant() ? 6 : 10));
  }

  protected visibleReasons() {
    return (this.aggregate()?.reasons || this.value()?.reasons || []).slice(0, this.isTableVariant() ? 1 : 3);
  }

  protected visibleWarnings(): string[] {
    return (this.aggregate()?.warnings || this.value()?.warnings || []).slice(0, this.isTableVariant() ? 2 : 4);
  }

  protected isTableVariant(): boolean {
    return this.variant() === 'table';
  }

  protected stopInteraction(event: Event): void {
    event.stopPropagation();
  }

  protected emitOpenDetails(event: Event, id: string): void {
    event.stopPropagation();
    const type = this.detailsType();
    if (!type || !id) {
      return;
    }
    this.openDetails.emit({ type, id });
  }

  protected emitInspect(event: Event): void {
    event.stopPropagation();
    this.inspect.emit();
  }

  private compactAggregateLabel(aggregate: ExplainableAggregateDto): string {
    if (aggregate.label.toLowerCase().includes('validation')) {
      return `${aggregate.count} ${aggregate.count === 1 ? 'issue' : 'issues'}`;
    }
    if (aggregate.label.toLowerCase().includes('handoff')) {
      return `${aggregate.count} checks`;
    }
    if (aggregate.label.toLowerCase().includes('question')) {
      return `${aggregate.count} questions`;
    }
    return String(aggregate.count);
  }
}
