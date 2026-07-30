---
name: runtime-configuration-basic-review
description: Druga para oczu dla porownania runtime configuration w trybie BASIC, oparta wylacznie na zanonimizowanym manifeście, diffie i deterministic findings.
---

# Runtime Configuration BASIC Review

## Cel

Wyjasnij, co roznice konfiguracji moga oznaczac i co czlowiek powinien
sprawdzic przed promocja. Nie zastępuj wyniku deterministycznego i nie
powtarzaj go jako długiej listy.

## Granice

- Pracuj wyłącznie na artefaktach `runtime-configuration/*` z promptu.
- Nie używaj Operational Context ani GitLab tools.
- Nie próbuj odtwarzać wartości z `valueToken`.
- `MASKED` oznacza sekret; nie wnioskuj o jego treści.
- Nie zmieniaj statusu deterministycznego, diffu, findings, coverage ani
  referencji.
- `functionalImpacts` pozostaw jako pustą listę.

## Procedura

1. Przeczytaj `scope.json`, `coverage.json` oraz
   `differences-and-findings.json`.
2. Przeczytaj cały `manifest-index.json` i wszystkie wskazane grupy manifestu.
   Używaj również niezmienionych parametrów i zachowanej struktury YAML, gdy
   pomagają rozpoznać obszar funkcjonalny rozjazdu.
3. Oddziel:
   - fakt z `differenceId` lub `findingId`,
   - interpretację faktu,
   - hipotezę wymagającą ręcznego potwierdzenia.
4. Obserwację potwierdzoną oznacz jako `GROUNDED_OBSERVATION` i wskaż
   istniejący `differenceId` lub `findingId`.
5. Wniosek bez takiego oparcia oznacz jako `HYPOTHESIS`.
6. Zapisz tylko `ai-second-opinion` i `recommended-human-checks` przez
   `report_upsert_section`; potwierdź zapis przez `report_get_current`.
7. Zwróć jeden obiekt JSON zgodny z `response-contract.json`.

## Ocena

`LIKELY_CONFIGURATION_ERROR` wybierz tylko wtedy, gdy wzorzec różnic i
findings daje mocny sygnał pomyłki. Sam fakt, że środowiska się różnią, nie
jest błędem. Gdy nie da się rozstrzygnąć intencji, użyj `REVIEW_REQUIRED` albo
`INCONCLUSIVE` i dodaj konkretny human check.

## Antywzorce

Nie:

- usuwaj findingu, z którym się nie zgadzasz,
- obniżaj `INCOMPLETE`,
- przedstawiaj tokenu jako wartości konfiguracji,
- wymyślaj kodu, systemu, funkcjonalności lub ownera,
- zapisuj sekcji spoza ukrytej allowlisty raportu.
