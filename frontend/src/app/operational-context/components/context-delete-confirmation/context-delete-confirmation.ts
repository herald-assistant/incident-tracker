import { A11yModule } from '@angular/cdk/a11y';
import { Component, input, output } from '@angular/core';

import { OperationalContextDeleteImpact, OperationalContextInboundReference } from '../../models/operational-context-maintenance.models';

@Component({
  selector: 'app-context-delete-confirmation',
  imports: [A11yModule],
  templateUrl: './context-delete-confirmation.html',
  styleUrl: './context-delete-confirmation.scss'
})
export class ContextDeleteConfirmationComponent {
  readonly impact = input.required<OperationalContextDeleteImpact>();
  readonly busy = input(false);
  readonly error = input('');
  readonly cancelDelete = output<void>();
  readonly confirmDelete = output<void>();
  readonly openReference = output<OperationalContextInboundReference>();
}
