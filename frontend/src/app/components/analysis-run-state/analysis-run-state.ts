import { Component, input } from '@angular/core';

@Component({
  selector: 'app-analysis-run-state',
  templateUrl: './analysis-run-state.html',
  styleUrl: './analysis-run-state.scss'
})
export class AnalysisRunStateComponent {
  readonly title = input.required<string>();
  readonly description = input('');
  readonly pending = input(true);
  readonly icon = input('error');
}
