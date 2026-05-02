import { Component, input, output } from '@angular/core';

import {
  ExplainableAggregateDto,
  ExplainableValueDto
} from '../../models/operational-context.models';
import { ExplainableCellComponent } from '../explainable-cell/explainable-cell';

export interface ContextCatalogColumn {
  key: string;
  label: string;
  type?: 'text' | 'aggregate' | 'owner';
}

@Component({
  selector: 'app-context-catalog-table',
  imports: [ExplainableCellComponent],
  templateUrl: './context-catalog-table.html',
  styleUrl: './context-catalog-table.scss'
})
export class ContextCatalogTableComponent {
  readonly columns = input<ContextCatalogColumn[]>([]);
  readonly rows = input<Array<Record<string, unknown>>>([]);
  readonly entityType = input('');
  readonly emptyText = input('No rows.');
  readonly openRow = output<Record<string, unknown>>();
  readonly openAggregateDetails = output<{ type: string; id: string }>();

  protected textValue(row: Record<string, unknown>, key: string): string {
    const value = row[key];
    if (typeof value === 'string') {
      return value;
    }
    if (typeof value === 'number') {
      return String(value);
    }
    return '';
  }

  protected aggregateValue(row: Record<string, unknown>, key: string): ExplainableAggregateDto | null {
    const value = row[key] as ExplainableAggregateDto | null;
    return value && typeof value === 'object' && 'count' in value ? value : null;
  }

  protected ownerValue(row: Record<string, unknown>, key: string): ExplainableValueDto<string> | null {
    const value = row[key] as ExplainableValueDto<string> | null;
    return value && typeof value === 'object' && 'label' in value ? value : null;
  }
}
