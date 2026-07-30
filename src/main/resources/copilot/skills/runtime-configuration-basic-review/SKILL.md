---
name: runtime-configuration-basic-review
description: Druga para oczu dla porownania runtime configuration w trybie BASIC, oparta wylacznie na kompaktowym zanonimizowanym drzewie, diffie i deterministic findings.
---

# Runtime Configuration BASIC Review

## Cel

Wyjasnij, co roznice konfiguracji moga oznaczac i co czlowiek powinien
sprawdzic przed promocja. Nie zastępuj wyniku deterministycznego i nie
powtarzaj go jako długiej listy.

## Granice

- Pracuj wyłącznie na artefaktach `runtime-configuration/*` z promptu.
- Nie używaj Operational Context ani GitLab tools.
- Nie próbuj odtwarzać wartości z reprezentacji `p:*`.
- W kolumnach `sourceRepresentation` i `targetRepresentation` kod `M`
  oznacza suppression sekretu; nie wnioskuj o jego treści.
- Nie zmieniaj statusu deterministycznego, diffu, findings, coverage ani
  referencji.
- `functionalImpacts` pozostaw jako pustą listę.

## Procedura

1. Przeczytaj `scope.json` i `coverage.json`.
2. Przeczytaj całe `configuration-tree.yaml`. Najpierw rozwiń
   `documentColumns`, `columns` i wszystkie legendy kodów. Hierarchia `tree`
   jest kanoniczną ścieżką parametru; `c` zawiera dzieci węzła. Uwzględniaj
   parametry zmienione i niezmienione, granice dokumentów/profile, typy,
   cardinality oraz relacje run-local pseudonimów.
3. Przeczytaj `changes.json` według `differenceColumns`, `findingColumns` i
   `referenceColumns`. Nie interpretuj pozycji tablic bez odpowiadającej
   definicji kolumny.
4. Oddziel:
   - fakt z `differenceId` lub `findingId`,
   - interpretację faktu,
   - hipotezę wymagającą ręcznego potwierdzenia.
5. Obserwację potwierdzoną oznacz jako `GROUNDED_OBSERVATION` i wskaż
   istniejący `differenceId` lub `findingId`.
6. Wniosek bez takiego oparcia oznacz jako `HYPOTHESIS`.
7. Zapisz tylko `ai-second-opinion` i `recommended-human-checks` przez
   `report_upsert_section`; potwierdź zapis przez `report_get_current`.
8. Zwróć jeden obiekt JSON zgodny z `response-contract.json`.

## Ocena

`LIKELY_CONFIGURATION_ERROR` wybierz tylko wtedy, gdy wzorzec różnic i
findings daje mocny sygnał pomyłki. Sam fakt, że środowiska się różnią, nie
jest błędem. Gdy nie da się rozstrzygnąć intencji, użyj `REVIEW_REQUIRED` albo
`INCONCLUSIVE` i dodaj konkretny human check.

## Antywzorce

Nie:

- usuwaj findingu, z którym się nie zgadzasz,
- obniżaj `INCOMPLETE`,
- przedstawiaj `p:*` jako wartości konfiguracji,
- wymyślaj kodu, systemu, funkcjonalności lub ownera,
- zapisuj sekcji spoza ukrytej allowlisty raportu.
