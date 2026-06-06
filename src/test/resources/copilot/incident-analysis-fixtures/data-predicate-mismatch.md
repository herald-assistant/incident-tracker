---
name: data-predicate-mismatch-active-case-record
expectedClassification: data_predicate_mismatch
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-data-diagnostics
---

# Fixture: Data Predicate Mismatch - Active Case Record

## Cel

Ten fixture opisuje incydent, w ktorym rekord istnieje po kluczu, ale aplikacja
go nie widzi, bo odpada przez pelny predykat repozytorium.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `data_predicate_mismatch`.
3. Potem przejscie do `incident-data-diagnostics`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-dpm-001`
- trigger: HTTP request `GET /cases/CASE-409/active-record`
- failure point: repository lookup aktywnego rekordu
- log: `ActiveCaseRecordNotFoundException: Active record not found for caseId=CASE-409`
- code hint: `findByCaseIdAndStatusAndDeletedFalse(caseId, ACTIVE)`
- table/key: `ACTIVE_CASE_RECORD.CASE_ID = CASE-409`

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `HTTP request -> service -> repository predicate -> empty result`.
2. Ugruntuj pelny predykat z kodu albo evidence.
3. Zaladuj `incident-data-diagnostics`.
4. Wykonaj DB test rozrozniajacy:
   - key-only check: `CASE_ID = 'CASE-409'`
   - full-predicate check: `CASE_ID = 'CASE-409' and STATUS = 'ACTIVE' and DELETED = 0`
5. Jesli key-only count > `0`, ale full-predicate count = `0`, utrzymaj
   `data_predicate_mismatch`.
6. Jezeli key-only count = `0`, zmien klase na `data_missing`.
7. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze rekord sprawy istnieje, ale nie spelnia warunkow aktywnosci.
- Pokazuje, jaki etap procesu blokuje status, tenant, validity albo soft delete.

### `technicalAnalysis`

- Wskazuje repository predicate, table, key i odrzucajacy warunek.
- Proponuje akcje: poprawic stan danych albo zweryfikowac regule predykatu.

## Antywzorce

- Nie nazywaj tego `data_missing`, jezeli key-only check zwraca rekord.
- Nie zgaduj statusu ani tenant bez DB evidence.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
