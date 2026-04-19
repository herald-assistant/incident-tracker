import { Component, computed, effect, input, signal } from '@angular/core';

import {
  AnalysisMode,
  AnalysisResultResponse,
  AnalysisVariantResultResponse
} from '../../core/models/analysis.models';
import { hasMeaningfulValue } from '../../core/utils/analysis-display.utils';
import { AnalysisFlowDiagramComponent } from '../analysis-flow-diagram/analysis-flow-diagram';
import { MarkdownContentComponent } from '../markdown-content/markdown-content';

@Component({
  selector: 'app-analysis-final-result',
  imports: [AnalysisFlowDiagramComponent, MarkdownContentComponent],
  templateUrl: './analysis-final-result.html',
  styleUrl: './analysis-final-result.scss'
})
export class AnalysisFinalResultComponent {
  readonly result = input<AnalysisResultResponse | null>(null);
  readonly status = input('');

  private readonly activeMode = signal<AnalysisMode>('CONSERVATIVE');

  protected readonly activeVariant = computed<AnalysisVariantResultResponse | null>(() => {
    const result = this.result();
    if (!result?.variants) {
      return null;
    }

    return this.activeMode() === 'EXPLORATORY'
      ? result.variants.exploratory
      : result.variants.conservative;
  });

  protected readonly hasMeaningfulValue = hasMeaningfulValue;
  protected readonly activeModeValue = this.activeMode.asReadonly();

  constructor() {
    effect(() => {
      const result = this.result();
      if (!result?.variants?.conservative) {
        this.activeMode.set('CONSERVATIVE');
        return;
      }

      if (this.activeMode() === 'EXPLORATORY' && !result.variants.exploratory) {
        this.activeMode.set('CONSERVATIVE');
      }
    }, { allowSignalWrites: true });
  }

  protected selectMode(mode: AnalysisMode): void {
    this.activeMode.set(mode);
  }

  protected badgeLabel(value: string | null | undefined, fallback: string): string {
    return value && value.trim() ? value : fallback;
  }

  protected isUnavailableVariant(variant: AnalysisVariantResultResponse | null): boolean {
    return (
      variant?.status === 'DISABLED' ||
      variant?.status === 'FAILED' ||
      variant?.status === 'SKIPPED'
    );
  }
}
