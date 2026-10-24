import {
  ChangeVerificationDecisionStatus,
  ChangeVerificationInterpretationType,
  ChangeVerificationReleaseImpact,
  ChangeVerificationRuleOutcome,
  ChangeVerificationRuleSourceType
} from '../../models/change-verification.models';

type Description = { label: string; hint: string };

export const DECISIONS: Record<ChangeVerificationDecisionStatus, Description> = {
  READY: { label: 'Gotowe do release', hint: 'Wszystkie wymagania autora zostały potwierdzone w dostępnym materiale.' },
  NEEDS_ACTION: { label: 'Wymaga poprawy', hint: 'Co najmniej jedno wymaganie autora nie zostało spełnione.' },
  NEEDS_EVIDENCE: { label: 'Wymaga dowodu', hint: 'Brakuje dowodów pozwalających potwierdzić zgodność wszystkich wymagań.' },
  INCONCLUSIVE: { label: 'Brak rozstrzygnięcia', hint: 'Nie znaleziono wymagań źródłowych możliwych do oceny.' }
};

export const OUTCOMES: Record<ChangeVerificationRuleOutcome, Description> = {
  SATISFIED: { label: 'Spełniona', hint: 'Dostępny materiał potwierdza zgodność z wymaganiem. Nie oznacza to automatycznie wykonania testu w działającym systemie.' },
  NOT_SATISFIED: { label: 'Niespełniona', hint: 'Ocena wskazuje rozbieżność między wymaganiem a implementacją. Wniosek i dowody opisują jej podstawę.' },
  NOT_VERIFIED: { label: 'Niezweryfikowana', hint: 'Brakuje materiału pozwalającego rozstrzygnąć zgodność. Ten wynik nie oznacza potwierdzonej wady.' }
};

export const IMPACTS: Record<ChangeVerificationReleaseImpact, Description> = {
  NONE: { label: 'Bez wpływu', hint: 'Nie wskazano wpływu tej reguły na release.' },
  REVIEW: { label: 'Wymaga decyzji w review', hint: 'Wpływ tej reguły wymaga decyzji osoby odbierającej zmianę.' },
  BLOCKER: { label: 'Blokuje release', hint: 'Ta reguła została oznaczona jako blokująca release. Przy braku weryfikacji blokada nie jest dowodem wykrytej wady.' }
};

export const INTERPRETATIONS: Record<ChangeVerificationInterpretationType, Description> = {
  EXPLICIT: { label: 'Jawna', hint: 'Wymaganie jest zapisane wprost w źródle.' },
  NORMALIZED: { label: 'Ujednolicona', hint: 'AI uporządkowało zapis wymagania do postaci możliwej do sprawdzenia. Cytat pozostaje tekstem źródłowym.' },
  CONFLICTING: { label: 'Sprzeczne źródła', hint: 'Źródła zawierają sprzeczne zapisy; interpretacja nie rozstrzyga samodzielnie, który obowiązuje.' },
  NOT_VERIFIABLE: { label: 'Niemożliwa do zweryfikowania', hint: 'Zapis wymagania nie pozwala na jednoznaczne sprawdzenie. To typ interpretacji, a nie wynik sprawdzenia.' },
  INFERRED: { label: 'Wnioskowana przez AI', hint: 'Kontrola wynika z sygnałów dostrzeżonych przez AI, a nie z wymagania autora.' }
};

export const SOURCES: Record<ChangeVerificationRuleSourceType, string> = {
  ACCEPTANCE_CRITERION: 'Kryterium akceptacji',
  JIRA_DESCRIPTION: 'Opis zadania Jira',
  JIRA_COMMENT: 'Komentarz w Jira',
  CONFLUENCE: 'Confluence',
  REPOSITORY_INSTRUCTION: 'Instrukcja repozytorium',
  OPERATOR_INSTRUCTION: 'Wymaganie operatora',
  AI_SUGGESTION: 'Sugestia AI'
};

export const CONFIDENCE: Record<string, string> = { HIGH: 'Wysoka', MEDIUM: 'Średnia', LOW: 'Niska' };

export const HELP = {
  decision: 'Decyzja jest wyliczana z wyników wymagań autora. Dodatkowe kontrole AI jej nie zmieniają. Zakończenie analizy nie oznacza potwierdzenia zgodności.',
  source: 'Dosłowny fragment materiału źródłowego. Interpretacja AI jest pokazana osobno w szczegółach.',
  interpretation: 'Sprawdzalna interpretacja wymagania przygotowana przez AI; nie zastępuje cytatu źródłowego.',
  action: 'Zalecenie z wyniku analizy: krok potrzebny do usunięcia rozbieżności albo uzyskania dowodu. Aplikacja nie wykonuje go automatycznie.',
  evidence: 'Fakty i referencje, na których oparto ocenę.',
  missing: 'Materiał potrzebny do rozstrzygnięcia zgodności, a nie lista potwierdzonych wad.',
  limits: 'Braki widoczności, które ograniczyły ocenę tej reguły.',
  confidence: 'Deklarowana przez AI pewność dodatkowej kontroli. Nie zmienia decyzji z weryfikacji wymagań.'
};
