---
name: change-verification-orchestrator
description: Orkiestruje Change Verification i zwraca jeden kanoniczny rejestr regul zrodlowych.
---

# Change Verification Orchestrator

## Cel

Przeprowadz jedna, spojna ocene regul autora i zwroc wylacznie JSON zgodny z
`change-verification/response-contract.md`. Nie tworz raportu Markdown,
findings, globalnego statusu ani globalnej listy dzialan.

## Workflow

1. Zaladuj `change-verification-compliance-check`.
2. Zacznij od target issue i artefaktow `change-verification/*`.
3. Wyodrebnij kazda obowiazujaca regule zrodlowa dokladnie raz. Zachowaj jej
   doslowny cytat, typ i referencje; osobno zapisz precyzyjna normalizacje.
4. Powiaz regule z widoczna implementacja i evidence. Gdy jeden celowany
   odczyt GitLab albo Operational Context moze rozstrzygnac konkretny brak,
   wykonaj go. Nie eksploruj kodu bez zwiazku z regula.
5. Po jednym celowanym retry wybierz `NOT_VERIFIED`, jezeli nadal brakuje
   materialu. Nie zgaduj.
6. Oddziel od zera do pieciu uzasadnionych `additionalChecks`. Nie przedstawiaj
   ich jako regul Jira, Confluence ani repozytorium.
7. Wykonaj finalna kontrole ledgeru i zwroc jeden obiekt JSON bez tekstu przed
   nim ani po nim.

## Finalna kontrola

- kazda regula autora wystepuje raz i ma unikalne `id`,
- cytat autora nie zostal zastapiony interpretacja AI,
- `rules` zawiera tylko scope `STORY` lub `INSTRUCTION`,
- `additionalChecks` zawiera tylko scope `ADDITIONAL` i source
  `AI_SUGGESTION`,
- wynik reguly jest jednym z `SATISFIED`, `NOT_SATISFIED`, `NOT_VERIFIED`,
- `releaseImpact` jest niezalezny od wyniku,
- `SATISFIED` ma evidence i `releaseImpact=NONE`,
- `NOT_SATISFIED` ma konkretne action,
- `NOT_VERIFIED` ma missingEvidence i action,
- kazdy visibility limit wskazuje istniejace `affectedRuleIds`,
- techniczne limity discovery nie staly sie samodzielnymi regulami ani
  problemami projektu.

## Antywzorce

Nie uruchamiaj report tools. Nie dziel wyniku na priority review, confirmed
scope i full material. Nie kopiuj tej samej reguly do kilku list. Nie wyliczaj
globalnego werdyktu; zrobi to deterministycznie backend.
