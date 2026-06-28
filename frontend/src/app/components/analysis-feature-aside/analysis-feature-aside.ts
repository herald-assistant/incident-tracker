import { Component, input, signal } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';

type AnalysisFeatureAsidePanel = 'progress' | 'ai' | 'chat' | 'feedback';

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
    tooltip: 'Follow-up chat'
  },
  {
    id: 'feedback',
    icon: 'reviews',
    label: 'Oceny narzędzi',
    tooltip: 'Oceny wyników narzędzi'
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

  protected readonly tabs = ASIDE_TABS;
  protected readonly activePanel = signal<AnalysisFeatureAsidePanel | null>(null);

  protected selectPanel(panel: AnalysisFeatureAsidePanel): void {
    this.activePanel.update((currentPanel) => currentPanel === panel ? null : panel);
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
    return this.tabs.find((tab) => tab.id === panel)?.label ?? panel;
  }
}
