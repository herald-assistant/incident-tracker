import { Component, computed, input, signal } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AnalysisAiUsage, AnalysisChatMessageResponse } from '../../core/models/analysis.models';

type AnalysisFeatureAsidePanel = 'progress' | 'ai' | 'chat' | 'feedback' | 'cost';

interface AnalysisFeatureAsideTab {
  id: AnalysisFeatureAsidePanel;
  icon: string;
  label: string;
  tooltip: string;
}

const ASIDE_TABS: AnalysisFeatureAsideTab[] = [
  {
    id: 'progress',
    icon: 'timeline',
    label: 'Przebieg analizy',
    tooltip: 'Przebieg analizy'
  },
  {
    id: 'ai',
    icon: 'psychology',
    label: 'Tok działania AI',
    tooltip: 'Tok działania AI'
  },
  {
    id: 'chat',
    icon: 'forum',
    label: 'Follow-up chat',
    tooltip: 'Dalsza rozmowa o wyniku'
  },
  {
    id: 'feedback',
    icon: 'reviews',
    label: 'Oceny narzędzi',
    tooltip: 'Oceny wyników narzędzi'
  },
  {
    id: 'cost',
    icon: 'paid',
    label: 'Koszt AI',
    tooltip: 'Zużycie kredytów i ekwiwalent USD'
  }
];

@Component({
  selector: 'app-analysis-feature-aside',
  imports: [MatTooltipModule],
  templateUrl: './analysis-feature-aside.html',
  styleUrl: './analysis-feature-aside.scss'
})
export class AnalysisFeatureAsideComponent {
  readonly ariaLabel = input('Panel pracy analizy');
  readonly progressActive = input(false);
  readonly aiActive = input(false);
  readonly chatActive = input(false);
  readonly feedbackActive = input(false);
  readonly progressCount = input(0);
  readonly aiCount = input(0);
  readonly chatCount = input(0);
  readonly feedbackCount = input(0);
  readonly showProgress = input(true);
  readonly showAi = input(true);
  readonly showChat = input(true);
  readonly showFeedback = input(true);
  readonly usage = input<AnalysisAiUsage | null>(null);
  readonly chatMessages = input<AnalysisChatMessageResponse[]>([]);

  protected readonly usages = computed(() => [
    this.usage(),
    ...this.chatMessages()
      .filter((message) => message.role === 'ASSISTANT')
      .map((message) => message.usage ?? null)
  ].filter((usage): usage is AnalysisAiUsage => usage !== null));
  protected readonly credits = computed(() => {
    const usages = this.usages();
    return usages.length > 0 && usages.every((usage) => typeof usage.aiCredits === 'number' && Number.isFinite(usage.aiCredits))
      ? usages.reduce((sum, usage) => sum + usage.aiCredits!, 0)
      : null;
  });
  protected readonly totalTokens = computed(() =>
    this.usages().reduce((sum, usage) => sum + usage.totalTokens, 0)
  );
  protected readonly totalCalls = computed(() =>
    this.usages().reduce((sum, usage) => sum + usage.apiCallCount, 0)
  );
  protected readonly models = computed(() =>
    [...new Set(this.usages().flatMap((usage) => (usage.model || '').split(',').map((model) => model.trim()).filter(Boolean)))].join(', ')
  );

  protected formatCredits(value: number): string {
    return new Intl.NumberFormat('pl-PL', { minimumFractionDigits: 2, maximumFractionDigits: 9 }).format(value);
  }

  protected formatUsd(value: number): string {
    return `$${new Intl.NumberFormat('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 9 }).format(value)}`;
  }

  protected formatCount(value: number): string {
    return new Intl.NumberFormat('pl-PL').format(value);
  }

  protected readonly tabs = computed(() =>
    ASIDE_TABS.filter((tab) => this.isVisible(tab.id))
  );
  protected readonly activePanel = signal<AnalysisFeatureAsidePanel | null>(null);

  protected selectPanel(panel: AnalysisFeatureAsidePanel): void {
    this.activePanel.update((currentPanel) => currentPanel === panel ? null : panel);
  }

  protected closePanel(): void {
    this.activePanel.set(null);
  }

  protected isLoading(panel: AnalysisFeatureAsidePanel): boolean {
    switch (panel) {
      case 'progress':
        return this.progressActive();
      case 'ai':
        return this.aiActive();
      case 'chat':
        return this.chatActive();
      case 'feedback':
        return this.feedbackActive();
      case 'cost':
        return false;
    }
  }

  protected badgeCount(panel: AnalysisFeatureAsidePanel): number {
    switch (panel) {
      case 'progress':
        return this.progressCount();
      case 'ai':
        return this.aiCount();
      case 'chat':
        return this.chatCount();
      case 'feedback':
        return this.feedbackCount();
      case 'cost':
        return 0;
    }
  }

  protected badgeLabel(count: number): string {
    return count > 99 ? '99+' : String(count);
  }

  protected badgeAriaLabel(panel: AnalysisFeatureAsidePanel): string {
    const count = this.badgeCount(panel);
    const loading = this.isLoading(panel);
    if (loading && count > 0) {
      return `${this.tabLabel(panel)}: trwa, ${count}`;
    }
    if (loading) {
      return `${this.tabLabel(panel)}: trwa`;
    }
    return `${this.tabLabel(panel)}: ${count}`;
  }

  private tabLabel(panel: AnalysisFeatureAsidePanel): string {
    return ASIDE_TABS.find((tab) => tab.id === panel)?.label ?? panel;
  }

  private isVisible(panel: AnalysisFeatureAsidePanel): boolean {
    switch (panel) {
      case 'progress':
        return this.showProgress();
      case 'ai':
        return this.showAi();
      case 'chat':
        return this.showChat();
      case 'feedback':
        return this.showFeedback();
      case 'cost':
        return this.usages().length > 0;
    }
  }
}
