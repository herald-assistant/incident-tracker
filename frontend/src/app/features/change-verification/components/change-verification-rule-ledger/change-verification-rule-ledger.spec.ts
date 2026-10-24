import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatTooltip } from '@angular/material/tooltip';

import { ChangeVerificationRuleLedger } from '../../models/change-verification.models';
import { ChangeVerificationRuleLedgerComponent } from './change-verification-rule-ledger';
import { DECISIONS, IMPACTS, INTERPRETATIONS, OUTCOMES, SOURCES } from './change-verification-presentation';

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

  it('keeps nine unverified rules compact without hiding their conclusions or next steps', () => {
    const input = ledger();
    input.rules = Array.from({ length: 9 }, (_, i) => ({ ...input.rules[1], id: `instruction-${i}` }));
    input.decision = { status: 'NEEDS_EVIDENCE', totalRules: 9, satisfied: 0, notSatisfied: 0, notVerified: 9 };
    input.visibilityLimits = [{ message: 'Brak dostępu do testów CRM.', affectedRuleIds: input.rules.map(rule => rule.id) }];
    const before = JSON.stringify(input);
    fixture.componentRef.setInput('ledger', input);
    fixture.detectChanges();

    const cards = [...fixture.nativeElement.querySelectorAll('.rule-ledger__rule')] as HTMLElement[];
    expect(cards.length).toBe(9);
    cards.forEach(card => {
      const details = card.querySelector('details')!;
      expect(details.open).toBe(false);
      expect(card.querySelector('.rule-ledger__conclusion')?.textContent).toContain('Nie znaleziono testu.');
      expect(card.querySelector('.rule-ledger__action')?.textContent).toContain('Dodaj test kontraktowy.');
      details.querySelector('summary')!.click();
      expect(details.open).toBe(true);
      expect(details.textContent).toContain('Brak dostępu do testów CRM.');
    });
    fixture.detectChanges();
    expect(JSON.stringify(input)).toBe(before);
    expect(fixture.nativeElement.textContent).not.toContain('dla części reguł');
  });

  it('explains assessments outside the disclosure and preserves impact as a separate axis', () => {
    const input = ledger();
    input.rules[1].releaseImpact = 'BLOCKER';
    fixture.componentRef.setInput('ledger', input);
    fixture.detectChanges();
    const card = fixture.nativeElement.querySelector('.rule-ledger__rule') as HTMLElement;
    const info = card.querySelector('.rule-ledger__assessment .rule-ledger__info') as HTMLElement;
    info.click();
    expect(card.querySelector('details')!.open).toBe(false);
    expect(info.tabIndex).toBe(0);
    expect(info.getAttribute('aria-label')).toBe('Znaczenie wyniku i wpływu na release');
    expect(card.querySelector('.rule-ledger__outcome')?.textContent).toBe('Niezweryfikowana');
    expect(card.querySelector('.rule-ledger__impact')?.textContent).toContain('Blokuje release');
    const tooltip = fixture.debugElement.queryAll(By.directive(MatTooltip))
      .map(el => el.injector.get(MatTooltip)).find(t => t.message.includes('Ten wynik nie oznacza'));
    expect(tooltip?.message).toContain('blokada nie jest dowodem wykrytej wady');
    expect(card.querySelector('summary')!.querySelector('[tabindex], button')).toBeNull();
  });

  it('renders every decision label without recalculating the supplied decision', () => {
    for (const status of Object.keys(DECISIONS) as Array<keyof typeof DECISIONS>) {
      const input = ledger();
      input.decision.status = status;
      fixture.componentRef.setInput('ledger', input);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('h3').textContent).toContain(DECISIONS[status].label);
      expect(input.decision.status).toBe(status);
    }
  });

  it('renders all outcomes, impacts, source types and interpretations in Polish', () => {
    const input = ledger();
    for (const outcome of Object.keys(OUTCOMES) as Array<keyof typeof OUTCOMES>) {
      for (const impact of Object.keys(IMPACTS) as Array<keyof typeof IMPACTS>) {
        for (const interpretation of Object.keys(INTERPRETATIONS) as Array<keyof typeof INTERPRETATIONS>) {
          input.rules = [{ ...input.rules[0], outcome, releaseImpact: impact, interpretationType: interpretation }];
          fixture.componentRef.setInput('ledger', { ...input });
          fixture.detectChanges();
          (fixture.nativeElement.querySelectorAll('.rule-ledger__filters button')[2] as HTMLButtonElement).click();
          fixture.detectChanges();
          expect(fixture.nativeElement.querySelector('.rule-ledger__outcome').textContent).toBe(OUTCOMES[outcome].label);
          expect(fixture.nativeElement.querySelector('.rule-ledger__impact').textContent).toContain(IMPACTS[impact].label);
          expect(fixture.nativeElement.querySelector('.rule-ledger__detail').textContent).toContain(INTERPRETATIONS[interpretation].label);
        }
      }
    }
    for (const type of Object.keys(SOURCES) as Array<keyof typeof SOURCES>) {
      input.rules[0] = { ...input.rules[0], source: { ...input.rules[0].source, type } };
      fixture.componentRef.setInput('ledger', { ...input });
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('.rule-ledger__reference').textContent).toContain(SOURCES[type]);
    }
  });

  it('keeps additional AI checks separate, collapsed and complete', () => {
    const input = ledger();
    input.additionalChecks = [{ ...input.rules[1], id: 'additional-1', scope: 'ADDITIONAL',
      source: { type: 'AI_SUGGESTION', label: 'AI', reference: 'CustomerService', quote: 'Sprawdź ponowienie zapisu klienta.' },
      interpretationType: 'INFERRED', rationale: 'Zmiana obsługuje ponowienia.',
      riskIfOmitted: 'Duplikacja klienta.', signals: ['Dodano obsługę ponowień.'], confidence: 'HIGH' }];
    const before = JSON.stringify(input);
    fixture.componentRef.setInput('ledger', input);
    fixture.detectChanges();
    const section = fixture.nativeElement.querySelector('.rule-ledger__additional') as HTMLDetailsElement;
    expect(section.open).toBe(false);
    expect(section.textContent).toContain('Nie zmieniają decyzji');
    section.querySelector('summary')!.click();
    const details = section.querySelector('.rule-ledger__details') as HTMLDetailsElement;
    expect(details.open).toBe(false);
    details.querySelector('summary')!.click();
    for (const confidence of ['HIGH', 'MEDIUM', 'LOW']) {
      input.additionalChecks[0].confidence = confidence;
      fixture.componentRef.setInput('ledger', { ...input });
      fixture.detectChanges();
      expect(details.textContent).toContain({ HIGH: 'Wysoka', MEDIUM: 'Średnia', LOW: 'Niska' }[confidence]);
    }
    expect(details.textContent).toContain('Duplikacja klienta.');
    expect(details.textContent).toContain('Dodano obsługę ponowień.');
    expect(details.textContent).toContain('Zmiana obsługuje ponowienia.');
    input.additionalChecks[0].confidence = 'HIGH';
    expect(JSON.stringify(input)).toBe(before);
  });

  it('retains an empty-ledger fallback and its global visibility limit', () => {
    const input = ledger();
    input.rules = [];
    input.decision = { status: 'INCONCLUSIVE', totalRules: 0, satisfied: 0, notSatisfied: 0, notVerified: 0 };
    input.visibilityLimits = [{ message: 'Nie udało się odczytać wyniku.', affectedRuleIds: [] }];
    fixture.componentRef.setInput('ledger', input);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.rule-ledger__global-limit').textContent).toContain('Nie udało się odczytać wyniku.');
    expect(fixture.nativeElement.querySelector('.rule-ledger__empty').textContent).toContain('Brak reguł źródłowych');
    expect(fixture.nativeElement.querySelector('.rule-ledger__filters')).toBeNull();
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
