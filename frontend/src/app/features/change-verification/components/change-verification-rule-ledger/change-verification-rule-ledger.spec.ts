import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ChangeVerificationRuleLedger } from '../../models/change-verification.models';
import { ChangeVerificationRuleLedgerComponent } from './change-verification-rule-ledger';

describe('ChangeVerificationRuleLedgerComponent', () => {
  let fixture: ComponentFixture<ChangeVerificationRuleLedgerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ChangeVerificationRuleLedgerComponent] }).compileComponents();
    fixture = TestBed.createComponent(ChangeVerificationRuleLedgerComponent);
    fixture.componentRef.setInput('ledger', ledger());
    fixture.detectChanges();
  });

  it('renders the author quote once and keeps AI normalization in its details', () => {
    const buttons = [...fixture.nativeElement.querySelectorAll('.rule-ledger__filters button')] as HTMLButtonElement[];
    buttons[2].click();
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;

    expect(text.match(/Po zapisaniu klient jest widoczny na liście\./g)?.length).toBe(1);
    expect(text).toContain('Zapisany klient pojawia się na liście wyników.');
    expect(text).not.toContain('Priorytet review');
    expect(text).not.toContain('Pełny materiał');
  });

  it('starts with the needs-action filter and switches to satisfied rules', () => {
    let text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Instrukcja wymaga testu kontraktowego.');
    expect(text).not.toContain('Po zapisaniu klient jest widoczny na liście.');

    const buttons = [...fixture.nativeElement.querySelectorAll('.rule-ledger__filters button')] as HTMLButtonElement[];
    buttons[1].click();
    fixture.detectChanges();
    text = fixture.nativeElement.textContent as string;

    expect(text).toContain('Po zapisaniu klient jest widoczny na liście.');
    expect(text).not.toContain('Instrukcja wymaga testu kontraktowego.');
  });
});

function ledger(): ChangeVerificationRuleLedger {
  return {
    storyComplianceRequested: true,
    instructionComplianceRequested: true,
    decision: { status: 'NEEDS_EVIDENCE', totalRules: 2, satisfied: 1, notSatisfied: 0, notVerified: 1 },
    rules: [
      {
        id: 'story-001', scope: 'STORY',
        source: { type: 'ACCEPTANCE_CRITERION', label: 'CRM-123 AC', reference: 'CRM-123#ac-1', quote: 'Po zapisaniu klient jest widoczny na liście.' },
        normalizedRule: 'Zapisany klient pojawia się na liście wyników.', interpretationType: 'NORMALIZED',
        outcome: 'SATISFIED', releaseImpact: 'NONE', conclusion: 'Test potwierdza zachowanie.',
        evidence: [{ summary: 'Test przechodzi.', reference: 'CustomerFlowTest' }], missingEvidence: [], action: null,
        rationale: null, riskIfOmitted: null, signals: [], confidence: null
      },
      {
        id: 'instruction-001', scope: 'INSTRUCTION',
        source: { type: 'REPOSITORY_INSTRUCTION', label: 'AGENTS.md', reference: 'AGENTS.md:12', quote: 'Instrukcja wymaga testu kontraktowego.' },
        normalizedRule: 'Zmiana kontraktu ma test.', interpretationType: 'EXPLICIT', outcome: 'NOT_VERIFIED',
        releaseImpact: 'REVIEW', conclusion: 'Nie znaleziono testu.', evidence: [], missingEvidence: ['Brak testu.'],
        action: 'Dodaj test kontraktowy.', rationale: null, riskIfOmitted: null, signals: [], confidence: null
      }
    ],
    additionalChecks: [],
    visibilityLimits: [{ message: 'Nie znaleziono testu.', affectedRuleIds: ['instruction-001'] }]
  };
}
