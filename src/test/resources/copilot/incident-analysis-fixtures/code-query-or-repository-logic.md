---
name: code-query-or-repository-logic-derived-query
expectedClassification: code_query_or_repository_logic
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-analysis-gitlab-tools
---

# Fixture: Code Query Or Repository Logic - Derived Query

## Cel

Ten fixture opisuje incydent, w ktorym root cause lezy w logice zapytania albo
predykatu repozytorium, a nie w samym stanie danych.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `code_query_or_repository_logic`.
3. Potem przejscie do `incident-analysis-gitlab-tools`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-query-001`
- trigger: search active entitlement
- failure point: repository method returns empty result
- log: `EntitlementNotFoundException for customerId=C-55 product=P-9`
- code hint: `findByCustomerIdAndProductCodeAndStatus(customerId, product, ACTIVE)`
- DB hint: row exists with `product_code = LEGACY-P-9`

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `request -> entitlement service -> repository predicate -> no entitlement`.
2. Zaladuj `incident-analysis-gitlab-tools`.
3. Przeczytaj repository method, derived query albo `@Query`.
4. Porownaj predykat z business key i evidence.
5. Jesli kod uzywa zlego pola, join albo status filter, utrzymaj
   `code_query_or_repository_logic`.
6. Jezeli kod jest poprawny, wroc do DB diagnostics dla data predicate.
7. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze system nie odnajduje uprawnienia przez sposob wyszukiwania.

### `technicalAnalysis`

- Wskazuje repository method, predykat i oczekiwana korekte query albo danych.

## Antywzorce

- Nie mieszaj code query bug z `data_predicate_mismatch` bez rozroznienia.
- Nie proponuj korekty danych, gdy problemem jest zly predykat w kodzie.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
