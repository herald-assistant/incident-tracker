---
name: data-duplicate-or-non-unique-active-contract
expectedClassification: data_duplicate_or_non_unique
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-data-diagnostics
---

# Fixture: Data Duplicate Or Non Unique - Active Contract

## Cel

Ten fixture opisuje incydent, w ktorym aplikacja oczekuje jednego aktywnego
rekordu, ale DB zawiera wiele pasujacych rekordow.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `data_duplicate_or_non_unique`.
3. Potem przejscie do `incident-data-diagnostics`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-dup-001`
- trigger: HTTP request `GET /contracts/C-100/active-version`
- failure point: single-result repository query
- log: `IncorrectResultSizeDataAccessException: expected 1, actual 2`
- code hint: `findActiveByContractNumber(contractNumber)`
- table/key: `CONTRACT_VERSION.CONTRACT_NUMBER = C-100`

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `HTTP request -> active contract version lookup -> non-unique result`.
2. Zaladuj `incident-data-diagnostics`.
3. Wykonaj DB test rozrozniajacy:
   - count aktywnych rekordow po business key,
   - group by status/validity/deleted/tenant,
   - check nakladajacych sie dat waznosci.
4. Jesli aktywnych pasujacych rekordow jest wiecej niz jeden, utrzymaj
   `data_duplicate_or_non_unique`.
5. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze proces nie wie, ktora wersje kontraktu wybrac.
- Wskazuje ryzyko blednej decyzji biznesowej albo blokady procesu.

### `technicalAnalysis`

- Wskazuje query, table, business key i duplicate set.
- Proponuje akcje: usunac/zakonczyc duplikat albo doprecyzowac regule wyboru.

## Antywzorce

- Nie traktuj tego jako missing data.
- Nie proponuj losowego wyboru rekordu jako fix.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
