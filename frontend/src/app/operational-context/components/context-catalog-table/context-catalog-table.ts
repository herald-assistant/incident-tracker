import { Component, input, output, signal } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
  ExplainableAggregateDto,
  ExplainableBreakdownItemDto,
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
  imports: [ExplainableCellComponent, MatIconModule, MatTooltipModule],
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
  readonly inspector = signal<{ rowKey: string; columnKey: string } | null>(null);

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

  protected aggregateColumns(): ContextCatalogColumn[] {
    return this.columns().filter((column) => column.type === 'aggregate');
  }

  protected firstColumn(): ContextCatalogColumn | null {
    return this.columns()[0] ?? null;
  }

  protected rowTitle(row: Record<string, unknown>): string {
    const firstColumn = this.firstColumn();
    return firstColumn
      ? this.textValue(row, firstColumn.key)
      : this.textValue(row, 'id') || this.textValue(row, 'title');
  }

  protected rowKey(row: Record<string, unknown>): string {
    const firstColumn = this.firstColumn();
    return this.textValue(row, 'id')
      || this.textValue(row, 'title')
      || (firstColumn ? this.textValue(row, firstColumn.key) : '');
  }

  protected selectAggregate(row: Record<string, unknown>, column: ContextCatalogColumn): void {
    const rowKey = this.rowKey(row);
    const current = this.inspector();
    if (current?.rowKey === rowKey && current.columnKey === column.key) {
      this.inspector.set(null);
      return;
    }

    this.inspector.set({ rowKey, columnKey: column.key });
  }

  protected selectInspectorTab(row: Record<string, unknown>, column: ContextCatalogColumn): void {
    this.inspector.set({ rowKey: this.rowKey(row), columnKey: column.key });
  }

  protected closeInspector(): void {
    this.inspector.set(null);
  }

  protected isAggregateSelected(row: Record<string, unknown>, column: ContextCatalogColumn): boolean {
    const current = this.inspector();
    return current?.rowKey === this.rowKey(row) && current.columnKey === column.key;
  }

  protected isInspectorOpen(row: Record<string, unknown>): boolean {
    return this.inspector()?.rowKey === this.rowKey(row);
  }

  protected activeRow(): Record<string, unknown> | null {
    const current = this.inspector();
    if (!current) {
      return null;
    }
    return this.rows().find((row) => this.rowKey(row) === current.rowKey) ?? null;
  }

  protected activeColumn(): ContextCatalogColumn | null {
    const current = this.inspector();
    if (!current) {
      return null;
    }
    return this.aggregateColumns().find((column) => column.key === current.columnKey) ?? null;
  }

  protected activeAggregate(): ExplainableAggregateDto | null {
    const row = this.activeRow();
    const column = this.activeColumn();
    return row && column ? this.aggregateValue(row, column.key) : null;
  }

  protected selectedColumn(row: Record<string, unknown>): ContextCatalogColumn | null {
    const current = this.inspector();
    if (current?.rowKey !== this.rowKey(row)) {
      return null;
    }
    return this.aggregateColumns().find((column) => column.key === current.columnKey) ?? null;
  }

  protected selectedAggregate(row: Record<string, unknown>): ExplainableAggregateDto | null {
    const column = this.selectedColumn(row);
    return column ? this.aggregateValue(row, column.key) : null;
  }

  protected statusText(aggregate: ExplainableAggregateDto): string {
    if (aggregate.severity === 'error') {
      return 'Needs fix';
    }
    if (aggregate.severity === 'warning') {
      return 'Review';
    }
    if (aggregate.severity === 'unknown') {
      return 'Not mapped';
    }
    return 'Mapped';
  }

  protected itemStatusLabel(status: string): string {
    switch (status) {
      case 'needs-review':
        return 'Needs review';
      case 'missing':
        return 'Missing';
      case 'conflicting':
        return 'Conflicting';
      case 'verified':
        return 'Verified';
      default:
        return status || 'Unknown';
    }
  }

  protected itemStatusIcon(status: string): string {
    switch (status) {
      case 'verified':
        return 'check_circle';
      case 'missing':
        return 'error';
      case 'conflicting':
        return 'report';
      case 'needs-review':
        return 'rate_review';
      default:
        return 'help';
    }
  }

  protected canOpenItemDetails(
    aggregate: ExplainableAggregateDto,
    item: ExplainableBreakdownItemDto
  ): boolean {
    const target = this.itemDetailsTarget(aggregate, item);
    if (!target) {
      return false;
    }
    return item.status !== 'missing' || target.type === 'validation' || target.type === 'open-question';
  }

  protected emitItemDetails(
    aggregate: ExplainableAggregateDto,
    item: ExplainableBreakdownItemDto,
    event: Event
  ): void {
    event.stopPropagation();
    const target = this.itemDetailsTarget(aggregate, item);
    if (!target) {
      return;
    }
    this.openAggregateDetails.emit(target);
  }

  private itemDetailsTarget(
    aggregate: ExplainableAggregateDto,
    item: ExplainableBreakdownItemDto
  ): { type: string; id: string } | null {
    const type = this.normalizedDetailsType(item.type) || this.normalizedDetailsType(aggregate.detailsType || '');
    if (!type || !item.id) {
      return null;
    }
    return { type, id: item.id };
  }

  private normalizedDetailsType(type: string): string {
    switch (type) {
      case 'system':
      case 'repository':
      case 'process':
      case 'integration':
      case 'bounded-context':
      case 'team':
      case 'glossary-term':
      case 'handoff-rule':
      case 'validation':
      case 'open-question':
        return type;
      case 'systems':
        return 'system';
      case 'repo':
      case 'repos':
      case 'repositories':
        return 'repository';
      case 'context':
      case 'contexts':
      case 'bounded-contexts':
        return 'bounded-context';
      case 'teams':
        return 'team';
      case 'glossary':
        return 'glossary-term';
      default:
        return '';
    }
  }
}
