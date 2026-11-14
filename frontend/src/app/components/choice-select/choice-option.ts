import { Component, booleanAttribute, input } from '@angular/core';

/** Declarative options for dynamic forms; rendered only by the parent select. */
@Component({
  selector: 'app-choice-option',
  template: '',
  host: { hidden: '' }
})
export class ChoiceOptionComponent {
  readonly value = input.required<string>();
  readonly label = input.required<string>();
  readonly hint = input('');
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly badge = input('');
  readonly tooltip = input('');
}
