---
name: change-verification-inferred-critical-checks-section
description: Buduje osobna sekcje INFERRED_CRITICAL_CHECKS dla maksymalnie pieciu krytycznych kontroli nieopisanych w materialach zrodlowych.
---

# Change Verification Inferred Critical Checks Section

## Cel

Zbuduj `InferredCriticalChecksSectionDraft` z kontroli, ktore nie sa
wymaganiami Jira, Confluence ani instrukcjami repozytorium, ale sa istotne dla
manualnej decyzji release'owej. Sekcja uzupelnia slaba dokumentacje bez
udawania, ze AI zna ustalenia przekazane ustnie podczas refinementu.

## Kwalifikacja

Dodaj pozycje tylko wtedy, gdy:

- widoczny jest konkretny sygnal w Jira, Confluence, MR, kodzie albo
  operational context,
- brak kontroli moze istotnie naruszyc poprawnosc, bezpieczenstwo, integralnosc
  danych, kompatybilnosc kontraktu albo gotowosc release'u,
- kontrole da sie ocenic wobec aktualnego evidence albo jawnie oznaczyc jako
  `NOT_VERIFIED`,
- nie jest to ogolna best practice, preferencja stylistyczna ani sugestia
  refaktoru.

Zwroc od zera do pieciu pozycji, od najwyzszego ryzyka. Nie wypelniaj limitu
na sile.

## Kontrakt Checka

Kazda pozycja musi miec:

- `origin=INFERRED_CRITICAL`,
- `scope=INFERRED_CRITICAL_CHECKS`,
- `interpretationType=inferred`,
- `criticality=HIGH` albo `BLOCKER`,
- `expectedCriterion` jako konkretna kontrola,
- `inferenceRationale` wyjasniajace, dlaczego kontrola jest krytyczna,
- `inferenceSignals` z konkretnymi source refs lub elementami kodu,
- `verificationStatus=PASSED | WARNING | FAILED | NOT_VERIFIED`,
- `verifiedAgainst`, `analysis`, `evidenceRefs` i `gaps`,
- `riskIfOmitted`, `suggestedAction` i `confidence=high | medium | low`.

Nie uzywaj `criterionQuote` jako fikcyjnego cytatu. Ustaw `criterionSource` na
`AI_SUGGESTION`, a `criterionQuote` na `n/a`.

## Wymagana Struktura

Sekcja zaczyna sie komunikatem, ze pozycje nie sa wymaganiami kontraktowymi i
wymagaja decyzji czlowieka. Nastepnie pokaz:

1. krotkie podsumowanie liczby kontroli i ich statusow,
2. tabele `Status | Krytyczna kontrola | Dlaczego | Ryzyko | Dzialanie`,
3. szczegoly z sygnalami inferencji, evidence, gaps i confidence.

Gdy lista jest pusta, zwroc krotka sekcje informujaca, ze aktualne evidence nie
uzasadnia dodatkowej kontroli krytycznej. Nie ukrywaj visibility limits.

## Status I Handoff

Statusy tej sekcji nie zmieniaja Story Compliance ani Instruction Compliance.
Przekaz orkiestratorowi:

```text
InferredCriticalChecksSectionDraft
inferredCriticalChecks
references
visibilityLimits
gaps
warnings
confidence
readiness
```

Nie wywoluj report tools. Finalny zapis nalezy do
`change-verification-write-report`.
