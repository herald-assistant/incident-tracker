import { Component, computed, input } from '@angular/core';

import { AnalysisReportMeta } from '../../../../core/models/analysis.models';
import { formatStatus } from '../../../../core/utils/analysis-display.utils';
import { AnalysisReportMetaComponent } from '../../../../components/analysis-report-meta/analysis-report-meta';
import { ChangeVerificationVerificationCheck } from '../../models/change-verification.models';

type ComplianceTone = 'positive' | 'warning' | 'critical' | 'neutral';

@Component({
  selector: 'app-change-verification-compliance-result',
  imports: [AnalysisReportMetaComponent],
  templateUrl: './change-verification-compliance-result.html',
  styleUrl: './change-verification-compliance-result.scss'
})
export class ChangeVerificationComplianceResultComponent {
  readonly checks = input<ChangeVerificationVerificationCheck[]>([]);
  readonly meta = input<AnalysisReportMeta | null>(null);
  readonly variant = input<'defined' | 'inferred-critical'>('defined');
  protected readonly inferredCritical = computed(() => this.variant() === 'inferred-critical');

  protected readonly orderedChecks = computed(() =>
    [...this.checks()].sort((left, right) => statusRank(left.verificationStatus) - statusRank(right.verificationStatus))
  );
  protected readonly attentionChecks = computed(() =>
    this.orderedChecks().filter((check) => !isPassed(check.verificationStatus))
  );
  protected readonly passedChecks = computed(() =>
    this.orderedChecks().filter((check) => isPassed(check.verificationStatus))
  );
  protected readonly sectionStatus = computed(() => {
    const statuses = this.checks().map((check) => normalizeStatus(check.verificationStatus));
    if (statuses.some(isCriticalStatus)) {
      return 'FAILED';
    }
    if (statuses.some((status) => !isPassed(status))) {
      return 'PASSED_WITH_WARNINGS';
    }
    return statuses.length > 0 ? 'PASSED' : 'NOT_VERIFIED';
  });
  protected readonly sectionSummary = computed(() => {
    const total = this.checks().length;
    const passed = this.passedChecks().length;
    const attention = this.attentionChecks().length;
    if (total === 0) {
      return this.inferredCritical()
        ? 'AI nie zidentyfikowało dodatkowych kontroli krytycznych.'
        : 'Brak strukturalnych kryteriów pozwalających podsumować ten zakres.';
    }
    if (attention === 0) {
      return this.inferredCritical()
        ? `Potwierdzono wszystkie ${total} kontrole zasugerowane przez AI.`
        : `Potwierdzono wszystkie ${total} kryteria objęte weryfikacją.`;
    }
    return `Potwierdzono ${passed} z ${total} kryteriów. ${attention} wymaga decyzji, korekty albo dodatkowego dowodu.`;
  });
  protected readonly confirmedHighlight = computed(() =>
    this.highlightText(
      this.passedChecks()[0],
      'Nie potwierdzono jeszcze żadnego kryterium w tym zakresie.'
    )
  );
  protected readonly riskHighlight = computed(() =>
    this.inferredCritical()
      ? this.riskIfOmitted(this.attentionChecks()[0] ?? this.checks()[0])
      : this.highlightText(
          this.attentionChecks()[0],
          'Nie wykryto rozbieżności wymagających uwagi.'
        )
  );
  protected readonly actionHighlight = computed(() => {
    const action = this.attentionChecks()
      .map((check) => cleanText(check.suggestedAction))
      .find(isMeaningfulText);
    return action || 'Brak dodatkowego działania wynikającego z tej sekcji.';
  });

  protected statusLabel(status: string | null | undefined): string {
    const formatted = formatStatus(status);
    if (formatted !== status) {
      return formatted;
    }
    const normalized = normalizeStatus(status).toLowerCase().replaceAll('_', ' ');
    return normalized ? normalized.charAt(0).toUpperCase() + normalized.slice(1) : 'Nieznany';
  }

  protected statusClass(status: string | null | undefined): string {
    const tone = statusTone(status);
    if (tone === 'positive') {
      return 'status-pill status-pill--done';
    }
    if (tone === 'critical') {
      return 'status-pill status-pill--error';
    }
    return 'status-pill change-verification-compliance__summary-status--warning';
  }

  protected toneClass(status: string | null | undefined): string {
    return `change-verification-compliance__status--${statusTone(status)}`;
  }

  protected criterionLabel(check: ChangeVerificationVerificationCheck): string {
    return firstText(check.expectedCriterion, check.criterionQuote, check.id, 'Kryterium');
  }

  protected sourceLabel(check: ChangeVerificationVerificationCheck): string {
    if (this.inferredCritical()) {
      return firstText(check.criticality ? `Krytyczność: ${check.criticality}` : '', 'Sugestia AI');
    }
    return firstText(check.criterionSource, check.scope, 'Źródło nieokreślone');
  }

  protected conclusion(check: ChangeVerificationVerificationCheck): string {
    return firstText(check.analysis, check.expectedCriterion, 'Brak opisu wyniku weryfikacji.');
  }

  protected action(check: ChangeVerificationVerificationCheck): string {
    const action = cleanText(check.suggestedAction);
    return isMeaningfulText(action) ? action : 'Brak dodatkowego działania.';
  }

  protected rationale(check: ChangeVerificationVerificationCheck): string {
    return firstText(check.inferenceRationale, check.analysis, 'Brak uzasadnienia inferencji.');
  }

  protected riskIfOmitted(check: ChangeVerificationVerificationCheck | undefined): string {
    if (!check) {
      return 'Nie wykryto dodatkowego ryzyka wymagającego uwagi.';
    }
    return firstText(check.riskIfOmitted, 'Ryzyko wymaga decyzji ownera.');
  }

  protected signals(check: ChangeVerificationVerificationCheck): string {
    return (check.inferenceSignals ?? []).map(cleanText).filter(Boolean).join('; ');
  }

  protected interpretationLabel(value: string | null | undefined): string {
    switch (normalizeStatus(value)) {
      case 'EXPLICIT':
        return 'Wymaganie jawne';
      case 'INFERRED':
        return 'Wymaganie wywnioskowane';
      case 'NOT_VERIFIABLE':
        return 'Brak możliwości pełnej weryfikacji';
      default:
        return formatStatus(value) || 'Nieokreślony';
    }
  }

  protected hasText(value: string | null | undefined): value is string {
    return cleanText(value).length > 0;
  }

  protected isMeaningful(value: string | null | undefined): boolean {
    return isMeaningfulText(value);
  }

  private highlightText(
    check: ChangeVerificationVerificationCheck | undefined,
    fallback: string
  ): string {
    if (!check) {
      return fallback;
    }
    return firstText(check.analysis, check.expectedCriterion, fallback);
  }
}

function statusRank(status: string | null | undefined): number {
  const normalized = normalizeStatus(status);
  if (isCriticalStatus(normalized)) {
    return 0;
  }
  if (['WARNING', 'PASSED_WITH_WARNINGS', 'PARTIAL'].includes(normalized)) {
    return 1;
  }
  if (!isPassed(normalized)) {
    return 2;
  }
  return 3;
}

function statusTone(status: string | null | undefined): ComplianceTone {
  const normalized = normalizeStatus(status);
  if (isCriticalStatus(normalized)) {
    return 'critical';
  }
  if (isPassed(normalized)) {
    return 'positive';
  }
  if (normalized) {
    return 'warning';
  }
  return 'neutral';
}

function isCriticalStatus(status: string): boolean {
  return ['FAILED', 'BLOCKED', 'BLOCKER', 'NOT_COMPLIANT', 'ERROR'].includes(status);
}

function isPassed(status: string | null | undefined): boolean {
  return ['PASSED', 'COMPLIANT', 'READY'].includes(normalizeStatus(status));
}

function normalizeStatus(value: string | null | undefined): string {
  return cleanText(value).toUpperCase().replace(/[\s-]+/g, '_');
}

function firstText(...values: Array<string | null | undefined>): string {
  return values.map(cleanText).find((value) => value.length > 0) ?? '';
}

function isMeaningfulText(value: string | null | undefined): boolean {
  const normalized = cleanText(value).toLowerCase();
  return normalized.length > 0 && !['brak', 'n/a', 'none', 'not applicable'].includes(normalized);
}

function cleanText(value: string | null | undefined): string {
  return typeof value === 'string' ? value.trim() : '';
}
