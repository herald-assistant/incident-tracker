---
name: code-query-or-repository-logic-derived-query
expectedClassification: code_query_or_repository_logic
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-code-grounding
---

# Fixture: Code Query Or Repository Logic - Derived Query

## Cel

Ten fixture opisuje incydent, w ktorym root cause lezy w logice zapytania albo
predykatu repozytorium, a nie w samym stanie danych.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `code_query_or_repository_logic`.
3. Potem przejscie do `incident-code-grounding`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-query-001`
- trigger: search active CRM contact preference
- failure point: repository method returns empty result
- log: `ContactPreferenceNotFoundException for contactId=CRM-CONTACT-55 segment=CRM-SEG-09`
- code hint: `findByContactIdAndSegmentCodeAndStatus(contactId, segment, ACTIVE)`
- DB hint: row exists with `segment_code = CRM-LEGACY-SEG-09`

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `request -> CRM contact preference service -> repository predicate -> no CRM contact preference`.
2. Zaladuj `incident-code-grounding`.
3. Przeczytaj repository method, derived query albo `@Query`.
4. Porownaj predykat z business key i evidence.
5. Jesli kod uzywa zlego pola, join albo status filter, utrzymaj
   `code_query_or_repository_logic`.
6. Jezeli kod jest poprawny, wroc do DB diagnostics dla data predicate.
7. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze system nie odnajduje preferencji kontaktu CRM przez sposob wyszukiwania.

### `technicalAnalysis`

- Wskazuje repository method, predykat i oczekiwana korekte query albo danych.

## Antywzorce

- Nie mieszaj code query bug z `data_predicate_mismatch` bez rozroznienia.
- Nie proponuj korekty danych, gdy problemem jest zly predykat w kodzie.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
