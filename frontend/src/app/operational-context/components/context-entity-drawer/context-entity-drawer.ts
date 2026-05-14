import { Component, input, output } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import {
  OperationalContextEntityDetailDto,
  OperationalContextReadModelBundle,
  ReadModelEntityRef
} from '../../models/operational-context.models';
import { WhyPopoverComponent } from '../why-popover/why-popover';

@Component({
  selector: 'app-context-entity-drawer',
  imports: [MatIconModule, WhyPopoverComponent],
  templateUrl: './context-entity-drawer.html',
  styleUrl: './context-entity-drawer.scss'
})
export class ContextEntityDrawerComponent {
  readonly detail = input<OperationalContextEntityDetailDto | null>(null);
  readonly readModels = input<OperationalContextReadModelBundle | null>(null);
  readonly readModelError = input('');
  readonly closeDrawer = output<void>();

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

  protected hasReadModels(bundle: OperationalContextReadModelBundle | null): boolean {
    return Boolean(bundle && Object.values(bundle).some(Boolean));
  }

  protected relationCount(bundle: OperationalContextReadModelBundle | null): number {
    const relations = bundle?.relations;
    return (relations?.outgoingRelations?.length || 0) + (relations?.incomingRelations?.length || 0);
  }

  protected derivedRelationCount(bundle: OperationalContextReadModelBundle | null): number {
    const relations = bundle?.relations;
    return [
      ...(relations?.outgoingRelations || []),
      ...(relations?.incomingRelations || [])
    ].filter((relation) => relation.derived).length;
  }

  protected entityLabels(values: ReadModelEntityRef[] | null | undefined): string {
    return (values || []).map((value) => value.label || value.id).join(', ');
  }

  protected scopeCount(bundle: OperationalContextReadModelBundle | null): number {
    return bundle?.codeSearch?.scopes?.length || 0;
  }

  protected repositoryCount(bundle: OperationalContextReadModelBundle | null): number {
    return bundle?.codeSearch?.repositories?.length || 0;
  }

  protected implementationIds(bundle: OperationalContextReadModelBundle | null): string {
    return (bundle?.implementations?.implementations || [])
      .map((implementation) => implementation.id)
      .join(', ');
  }

  protected flowSummary(bundle: OperationalContextReadModelBundle | null): string {
    const flow = bundle?.flow;
    if (!flow) {
      return '';
    }
    return `${flow.steps.length} steps, ${flow.edges.length} edges`;
  }

  protected impactedEntityLabels(
    values: Array<{ entity: ReadModelEntityRef }> | null | undefined
  ): string {
    return (values || []).map((value) => value.entity.label || value.entity.id).join(', ');
  }

  protected impactedImplementationIds(bundle: OperationalContextReadModelBundle | null): string {
    return (bundle?.blastRadius?.impactedImplementations || [])
      .map((value) => value.implementation.id)
      .join(', ');
  }

  protected totalBlastRadiusNodes(bundle: OperationalContextReadModelBundle | null): number {
    const blastRadius = bundle?.blastRadius;
    if (!blastRadius) {
      return 0;
    }
    return blastRadius.impactedSystems.length
      + blastRadius.impactedBoundedContexts.length
      + blastRadius.impactedIntegrations.length
      + blastRadius.impactedDataStores.length
      + blastRadius.impactedImplementations.length;
  }
}
