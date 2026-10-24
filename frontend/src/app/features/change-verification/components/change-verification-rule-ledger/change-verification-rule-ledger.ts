import { NgTemplateOutlet } from '@angular/common';
import { Component, input, signal } from '@angular/core';
import { MatTooltipModule } from '@angular/material/tooltip';

import {
  ChangeVerificationRuleLedger,
  ChangeVerificationRuleOutcome,
  ChangeVerificationRuleResult,
  ChangeVerificationRuleScope,
  ChangeVerificationVisibilityLimit
} from '../../models/change-verification.models';
import {
  CONFIDENCE, DECISIONS, HELP, IMPACTS, INTERPRETATIONS, OUTCOMES, SOURCES
} from './change-verification-presentation';

type LedgerFilter = 'attention' | 'satisfied' | 'all';

@Component({
  selector: 'app-change-verification-rule-ledger',
  imports: [NgTemplateOutlet, MatTooltipModule],
  templateUrl: './change-verification-rule-ledger.html',
  styleUrl: './change-verification-rule-ledger.scss'
})
export class ChangeVerificationRuleLedgerComponent {
  readonly ledger = input<ChangeVerificationRuleLedger | null>(null);
  protected readonly filter = signal<LedgerFilter>('attention');
  protected readonly decisions = DECISIONS;
  protected readonly outcomes = OUTCOMES;
  protected readonly impacts = IMPACTS;
  protected readonly interpretations = INTERPRETATIONS;
  protected readonly sources = SOURCES;
  protected readonly confidence = CONFIDENCE;
  protected readonly help = HELP;

  protected asRule(rule: ChangeVerificationRuleResult): ChangeVerificationRuleResult {
    return rule;
  }

  protected assessmentHint(rule: ChangeVerificationRuleResult): string {
    return `${OUTCOMES[rule.outcome].hint} Wpływ na release to osobna ocena. ${IMPACTS[rule.releaseImpact].hint}`;
  }

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

  protected outcomeClass(outcome: ChangeVerificationRuleOutcome): string {
    return `rule-ledger__outcome rule-ledger__outcome--${outcome.toLowerCase().replaceAll('_', '-')}`;
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
