import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AnalysisReportMeta } from '../../../../core/models/analysis.models';
import { ChangeVerificationVerificationCheck } from '../../models/change-verification.models';
import { ChangeVerificationComplianceResultComponent } from './change-verification-compliance-result';

describe('ChangeVerificationComplianceResultComponent', () => {
  let fixture: ComponentFixture<ChangeVerificationComplianceResultComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChangeVerificationComplianceResultComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(ChangeVerificationComplianceResultComponent);
  });

  it('shows risks first and keeps criterion details and metadata collapsed', () => {
    fixture.componentRef.setInput('checks', [
      check({
        id: 'story-001',
        verificationStatus: 'PASSED',
        expectedCriterion: 'Publikacja eventu uruchamia inicjalizację.',
        analysis: 'Przepływ został potwierdzony testem integracyjnym.'
      }),
      check({
        id: 'story-002',
        verificationStatus: 'WARNING',
        expectedCriterion: 'Błąd publikacji nie może zostać pominięty.',
        analysis: 'Wyjątek jest logowany, ale nie jest propagowany.',
        suggestedAction: 'Dodać retry albo jawnie zaakceptować ryzyko.'
      })
    ]);
    fixture.componentRef.setInput('meta', reportMeta());
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    const headings = Array.from(element.querySelectorAll('h4')).map((node) => node.textContent?.trim());
    const rows = Array.from(element.querySelectorAll('tbody tr'));
    const details = Array.from(element.querySelectorAll('details')) as HTMLDetailsElement[];

    expect(headings).toContain('Wymaga uwagi');
    expect(headings).toContain('Potwierdzone wymagania');
    expect(rows[0].textContent).toContain('Błąd publikacji nie może zostać pominięty.');
    expect(element.textContent).toContain('Potwierdzono 1 z 2 kryteriów.');
    expect(details.length).toBeGreaterThan(2);
    expect(details.every((detail) => !detail.open)).toBe(true);
    expect(element.textContent).toContain('References');
    expect(element.textContent).toContain('Visibility limits');
    expect(element.textContent).not.toContain('criterionSource');
    expect(element.textContent).not.toContain('gaps: []');
  });

  it('shows the current-contract empty state instead of rendering historical markdown', () => {
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.textContent).toContain('Brak strukturalnego wyniku weryfikacji');
    expect(element.textContent).toContain('aktualny kontrakt');
    expect(element.querySelector('table')).toBeNull();
    expect(element.querySelector('app-analysis-report-section-content')).toBeNull();
  });

  it('shows inferred critical checks as non-contractual AI suggestions', () => {
    fixture.componentRef.setInput('variant', 'inferred-critical');
    fixture.componentRef.setInput('checks', [
      check({
        id: 'critical-001',
        origin: 'INFERRED_CRITICAL',
        scope: 'INFERRED_CRITICAL_CHECKS',
        criterionSource: 'AI_SUGGESTION',
        criterionQuote: 'n/a',
        interpretationType: 'inferred',
        criticality: 'HIGH',
        inferenceRationale: 'Zmiana publikuje event w ścieżce z retry.',
        inferenceSignals: ['EventPublisher', 'retry path'],
        riskIfOmitted: 'Ponowienie może utworzyć duplikat.',
        confidence: 'medium',
        expectedCriterion: 'Idempotencja publikacji',
        verificationStatus: 'NOT_VERIFIED'
      })
    ]);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('To nie są wymagania zapisane');
    expect(text).toContain('Idempotencja publikacji');
    expect(text).toContain('Ponowienie może utworzyć duplikat.');
    expect(text).toContain('EventPublisher; retry path');
    expect(text).toContain('nie zmienia wyniku Story Compliance');
  });
});

function check(
  overrides: Partial<ChangeVerificationVerificationCheck>
): ChangeVerificationVerificationCheck {
  return {
    id: 'story-check',
    origin: 'DEFINED',
    scope: 'STORY_COMPLIANCE',
    criterionSource: 'Jira acceptance criteria',
    criterionQuote: 'System powinien opublikować event.',
    interpretationType: 'explicit',
    criticality: null,
    inferenceRationale: null,
    inferenceSignals: [],
    riskIfOmitted: null,
    confidence: null,
    expectedCriterion: 'System publikuje event.',
    verificationStatus: 'PASSED',
    verifiedAgainst: 'backend/src/EventPublisher.java',
    analysis: 'Implementacja publikuje event.',
    evidenceRefs: [],
    gaps: [],
    suggestedAction: '',
    ...overrides
  };
}

function reportMeta(): AnalysisReportMeta {
  return {
    references: [
      {
        type: 'jira',
        label: 'CRM-123',
        target: 'https://jira.example.com/browse/CRM-123',
        description: 'Target issue'
      }
    ],
    visibilityLimits: ['Branch źródłowy został usunięty.'],
    openQuestions: [],
    gaps: ['Brak testu błędu publikacji.'],
    confidence: 'medium',
    warnings: ['Ryzyko utraty eventu.']
  };
}
