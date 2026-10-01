import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { AnalysisAiInvocation } from '../../core/models/analysis.models';

@Component({
  selector: 'app-analysis-ai-invocations',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analysis-ai-invocations.html',
  styleUrl: './analysis-ai-invocations.scss'
})
export class AnalysisAiInvocationsComponent {
  readonly invocations = input.required<AnalysisAiInvocation[]>();
  readonly summaryOnly = input(false);
  readonly partCount = computed(() => Math.max(0, ...this.invocations()
    .filter((call) => call.role === 'EVIDENCE_PART').map((call) => call.partCount)));

  protected title(call: AnalysisAiInvocation): string {
    if (call.role === 'EVIDENCE_PART') return `Analiza części ${call.partNumber}/${call.partCount}`;
    if (call.role === 'SYNTHESIS') return 'Łączenie wyników';
    if (call.role === 'REDUCTION') return 'Porządkowanie ustaleń';
    return 'Analiza';
  }

  protected status(call: AnalysisAiInvocation): string {
    return ({ RUNNING: 'W toku', RAW_RESPONSE: 'Sprawdzanie odpowiedzi', COMPLETED: 'Zakończona', FAILED: 'Błąd' } as Record<string, string>)[call.status] ?? call.status;
  }
}
