---
name: runtime-configuration-deep-review
description: Gleboka druga opinia o rozjezdzie runtime configuration, laczaca kompaktowe sanitizowane artefakty v2 z przygotowanym Operational Context, code grounding i ownershipem wybranego internal-system.
---

# Runtime Configuration DEEP Review

## Cel

Opowiedz, jak rozjazd może wpływać na działanie systemu, jakiej funkcjonalności
dotyczy oraz do kogo zwrócić się po szczegóły. Wynik deterministyczny,
Operational Context, code grounding i ownership pozostają faktami backendu.

## Granice bezpieczeństwa

- Repozytorium konfiguracji nie jest dostępne przez tools.
- GitLab tools służą tylko do focused verification w repozytoriach, refach i
  `pathPrefixes` z ukrytego scope wybranego `internal-system`.
- Nie wykonuj repository rediscovery ani broad code exploration.
- Operational Context tools służą tylko do odczytu przygotowanych entity IDs.
- Nie zmieniaj ownerów, `resolutionPath`, coverage, refów ani visibility
  limits.
- Nie próbuj odtwarzać wartości z `p:*`; representation code `M` jest
  suppressed sekretem.

## Procedura

1. Wykonaj wszystkie kroki BASIC dla kompletnego `configuration-tree.yaml`
   oraz kolumnowego `changes.json`.
2. Przeczytaj `deep-context.json`: primary system, affected entities,
   codeGrounding, użyte refy, ownership i visibility limits.
3. Najpierw korzystaj z przygotowanego contextu. Tool uruchom tylko wtedy,
   gdy jeden focused odczyt może istotnie potwierdzić albo obalić konkretną
   hipotezę.
4. Dla każdego `functionalImpact` podaj:
   - rozpoznaną funkcjonalność,
   - znaczenie zmiany dla zachowania systemu,
   - confidence,
   - istniejące `differenceId`/`findingId`,
   - istniejące `contextId`/`codeGroundingId`,
   - `hypothesis=true`, gdy kod nie potwierdza wpływu.
5. Gdy ownership jest `UNKNOWN` albo `AMBIGUOUS`, powiedz to wprost. Nie
   wybieraj ownera na podstawie podobnej nazwy repozytorium lub klucza.
6. Zapisz `ai-second-opinion`, `recommended-human-checks` i narrację
   `functional-impact-and-code-grounding` przez `report_upsert_section`.
   Backend zachowa referencje i meta tej sekcji.
7. Potwierdź raport przez `report_get_current`, następnie zwróć jeden JSON
   zgodny z `response-contract.json`.

## Observation vs hypothesis

`GROUNDED_OBSERVATION` musi wskazywać istniejący `differenceId` lub
`findingId`. `contextId` i `codeGroundingId` wzmacniają interpretację, ale nie
zastępują referencji do rozjazdu. Bez takiej referencji użyj `HYPOTHESIS`.

## Antywzorce

Nie:

- przedstawiaj default branch jako potwierdzonej wersji wdrożonej,
- czytaj pliku poza `pathPrefixes`,
- rozszerzaj zakres poza wybrany `internal-system`,
- modyfikuj deterministic result albo ownership,
- zamieniaj brak evidence w pewny opis funkcjonalności.
