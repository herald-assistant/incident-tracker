import { Component, input } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';

export type SourceState = 'DEFAULT' | 'CUSTOM';

@Component({
  selector: 'app-source-state-pill',
  imports: [MatTooltipModule],
  template: `
    <span
      class="workspace-settings-source"
      [class.workspace-settings-source--custom]="state() === 'CUSTOM'"
      [matTooltip]="tooltip() ?? ''"
      [matTooltipDisabled]="!tooltip()"
      matTooltipPosition="above"
      [matTooltipShowDelay]="120"
    >
      {{ state() }}
    </span>
  `,
  styleUrl: './source-state-pill.scss'
})
export class SourceStatePillComponent {
  readonly state = input.required<SourceState>();
  readonly tooltip = input<string | null>(null);
}
