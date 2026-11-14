import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';

@Component({
  selector: 'app-form-field',
  imports: [MatTooltipModule],
  templateUrl: './form-field.html',
  styleUrl: './form-field.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class FormFieldComponent {
  readonly controlId = input.required<string>();
  readonly label = input('');
  readonly tooltip = input('');
  readonly tooltipClass = input('');
  readonly hint = input('');
  readonly error = input('');
  readonly focusRequested = output<void>();
}
