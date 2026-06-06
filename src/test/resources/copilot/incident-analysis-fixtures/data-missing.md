---
name: data-missing-active-case-record
expectedClassification: data_missing
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-data-diagnostics
---

# Fixture: Data Missing - Active Case Record

## Cel

Ten fixture opisuje minimalny dry-run dla incydentu, w ktorym aplikacja
przerywa flow, bo oczekiwany rekord danych nie istnieje.

Fixture nie testuje odpowiedzi modelu. Testuje kontrakt oczekiwanego routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `data_missing`.
3. Potem przejscie do `incident-data-diagnostics`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

### Fingerprint

- `correlationId`: `corr-dm-001`
- `environment`: `dev3`
- trigger: HTTP request
- entry point: `GET /cases/CASE-404/active-record`
- failing service: `case-management-api`
- obiekt: case `CASE-404`
- widoczny punkt awarii: active case record lookup

### Log

```text
2026-06-06T10:14:23.124Z ERROR case-management-api
correlationId=corr-dm-001
ActiveCaseRecordNotFoundException: Active case record not found for caseId=CASE-404
at ActiveCaseRecordService.getActiveCaseRecordForCaseId(ActiveCaseRecordService.java:47)
```

### Code Hint

```text
ActiveCaseRecordService.getActiveCaseRecordForCaseId(caseId)
  -> ActiveCaseRecordRepository.findByCaseId(caseId)
  -> orElseThrow(ActiveCaseRecordNotFoundException)

Entity: ActiveCaseRecord
Table: ACTIVE_CASE_RECORD
Key column: CASE_ID
```

### Operational Context Hint

- system: `case-management`
- process: `case servicing`
- bounded context: `case records`
- expected handoff: Data / DBA albo wlasciciel zasilania rekordow sprawy

## Oczekiwany Dry Run Orkiestratora

1. Zaladuj `incident-analysis-orchestrator`.
2. Zbuduj fingerprint z logu i evidence.
3. Zbadaj flow use case'u przed klasyfikacja:
   `HTTP request -> controller -> service -> repository lookup -> missing row`.
4. Ustal failure point jako repository/entity loading.
5. Sklasyfikuj aktywna hipoteze jako `data_missing`.
6. Zaladuj `incident-data-diagnostics`.
7. Wykonaj DB test rozrozniajacy:
   - key-only check: `count(*) from ACTIVE_CASE_RECORD where CASE_ID = 'CASE-404'`
   - full-predicate check: taki sam jak aplikacyjny predykat, jezeli kod go
     wskazuje
8. Jesli key-only count = `0`, utrzymaj `data_missing`.
9. Jesli key-only count > `0`, ale full-predicate count = `0`, zmien klase na
   `data_predicate_mismatch`.
10. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze request prosi o aktywny rekord sprawy.
- Pokazuje, ze flow zatrzymuje sie na oczekiwanym obiekcie danych.
- Wskazuje, jaki proces albo bounded context jest dotkniety.
- Wskazuje handoff do wlasciciela danych, jezeli brak rekordu jest
  potwierdzony.

### `technicalAnalysis`

- Wskazuje service/repository lookup i table/key.
- Oddziela `data_missing` od `data_predicate_mismatch`.
- Proponuje pierwsza akcje: sprawdzic albo odtworzyc zasilanie rekordu dla
  `CASE-404`, a po korekcie powtorzyc request.
- Wymienia visibility limits, jezeli DB evidence albo ownership nie jest
  dostepny.

## Antywzorce

- Nie klasyfikuj tylko po nazwie exceptiona.
- Nie finalizuj `data_missing` bez DB evidence albo jawnego braku DB
  visibility.
- Nie uzywaj `incident-analysis-gitlab-tools` jako zamiennika DB evidence.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
