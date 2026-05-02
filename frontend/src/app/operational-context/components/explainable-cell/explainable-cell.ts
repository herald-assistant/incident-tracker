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
  readonly openDetails = output<{ type: string; id: string }>();

  protected readonly trackByIndex = (index: number): number => index;

  protected displayLabel(): string {
    const aggregate = this.aggregate();
    if (aggregate) {
      return `${aggregate.label}: ${aggregate.count}`;
    }

    const value = this.value();
    return value?.label || value?.value || 'n/a';
  }

  protected severityClass(): string {
    const severity = this.aggregate()?.severity || 'unknown';
    return `explainable-cell--${severity}`;
  }

  protected confidence(): string {
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

  protected emitOpenDetails(id: string): void {
    const type = this.detailsType();
    if (!type || !id) {
      return;
    }
    this.openDetails.emit({ type, id });
  }
}
