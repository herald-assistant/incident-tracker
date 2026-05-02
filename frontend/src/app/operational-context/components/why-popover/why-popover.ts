import { Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';

import { ExplanationReasonDto, SourceReferenceDto } from '../../models/operational-context.models';

@Component({
  selector: 'app-why-popover',
  imports: [MatIconModule, MatTooltipModule],
  templateUrl: './why-popover.html',
  styleUrl: './why-popover.scss'
})
export class WhyPopoverComponent {
  readonly title = input('Why this?');
  readonly summary = input('');
  readonly confidence = input('');
  readonly reasons = input<ExplanationReasonDto[]>([]);
  readonly warnings = input<string[]>([]);
  readonly sourceRefs = input<SourceReferenceDto[]>([]);
}
