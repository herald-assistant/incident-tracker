import type { Signal } from '@angular/core';

export interface FormTourStep {
  readonly anchor: string;
  readonly title: string;
  readonly description: string;
}

export interface FormTourSource {
  readonly formTourSteps: Signal<readonly FormTourStep[]>;
}
