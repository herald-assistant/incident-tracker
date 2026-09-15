import { NgTemplateOutlet } from '@angular/common';
import { Component, input, signal } from '@angular/core';

import {
  ChangeVerificationDecisionStatus,
  ChangeVerificationReleaseImpact,
  ChangeVerificationRuleLedger,
  ChangeVerificationRuleOutcome,
  ChangeVerificationRuleResult,
  ChangeVerificationRuleScope,
  ChangeVerificationVisibilityLimit
} from '../../models/change-verification.models';

type LedgerFilter = 'attention' | 'satisfied' | 'all';

@Component({
  selector: 'app-change-verification-rule-ledger',
  imports: [NgTemplateOutlet],
  templateUrl: './change-verification-rule-ledger.html',
  styleUrl: './change-verification-rule-ledger.scss'
})
export class ChangeVerificationRuleLedgerComponent {
  readonly ledger = input<ChangeVerificationRuleLedger | null>(null);
  protected readonly filter = signal<LedgerFilter>('attention');

  protected selectFilter(filter: LedgerFilter): void {
    this.filter.set(filter);
  }

  protected rulesForScope(scope: ChangeVerificationRuleScope): ChangeVerificationRuleResult[] {
    return (this.ledger()?.rules ?? []).filter((rule) =>
      rule.scope === scope && this.matchesFilter(rule)
    );
  }

  protected sourceRuleCount(scope: ChangeVerificationRuleScope): number {
    return (this.ledger()?.rules ?? []).filter((rule) => rule.scope === scope).length;
  }

  protected limitsFor(rule: ChangeVerificationRuleResult): ChangeVerificationVisibilityLimit[] {
    return (this.ledger()?.visibilityLimits ?? []).filter((limit) =>
      limit.affectedRuleIds.includes(rule.id)
    );
  }

  protected globalLimits(): ChangeVerificationVisibilityLimit[] {
    return (this.ledger()?.visibilityLimits ?? []).filter((limit) => !limit.affectedRuleIds.length);
  }

  protected decisionLabel(status: ChangeVerificationDecisionStatus): string {
    switch (status) {
      case 'READY':
        return 'Gotowe do release';
      case 'NEEDS_ACTION':
        return 'Wymaga poprawy';
      case 'NEEDS_EVIDENCE':
        return 'Wymaga dowodu';
      default:
        return 'Brak rozstrzygnięcia';
    }
  }

  protected decisionHint(status: ChangeVerificationDecisionStatus): string {
    switch (status) {
      case 'READY':
        return 'Wszystkie reguły autora zostały potwierdzone.';
      case 'NEEDS_ACTION':
        return 'Co najmniej jedna reguła autora nie została spełniona.';
      case 'NEEDS_EVIDENCE':
        return 'Nie ma wykazanej niezgodności, ale brakuje dowodu dla części reguł.';
      default:
        return 'Nie znaleziono reguł źródłowych możliwych do oceny.';
    }
  }

  protected outcomeLabel(outcome: ChangeVerificationRuleOutcome): string {
    switch (outcome) {
      case 'SATISFIED':
        return 'Spełniona';
      case 'NOT_SATISFIED':
        return 'Niespełniona';
      default:
        return 'Niezweryfikowana';
    }
  }

  protected impactLabel(impact: ChangeVerificationReleaseImpact): string {
    switch (impact) {
      case 'BLOCKER':
        return 'Blokuje release';
      case 'REVIEW':
        return 'Decyzja review';
      default:
        return 'Bez wpływu';
    }
  }

  protected sourceTypeLabel(value: string): string {
    return value.toLowerCase().replaceAll('_', ' ');
  }

  protected outcomeClass(outcome: ChangeVerificationRuleOutcome): string {
    return `rule-ledger__outcome rule-ledger__outcome--${outcome.toLowerCase().replaceAll('_', '-')}`;
  }

  protected decisionClass(status: ChangeVerificationDecisionStatus): string {
    return `rule-ledger__decision rule-ledger__decision--${status.toLowerCase().replaceAll('_', '-')}`;
  }

  protected isAttention(rule: ChangeVerificationRuleResult): boolean {
    return rule.outcome !== 'SATISFIED';
  }

  protected hasText(value: string | null | undefined): value is string {
    return typeof value === 'string' && value.trim().length > 0;
  }

  private matchesFilter(rule: ChangeVerificationRuleResult): boolean {
    if (this.filter() === 'attention') {
      return this.isAttention(rule);
    }
    if (this.filter() === 'satisfied') {
      return rule.outcome === 'SATISFIED';
    }
    return true;
  }
}
