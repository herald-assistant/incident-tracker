---
name: async-or-process-state-outbox-stuck
expectedClassification: async_or_process_state
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-data-diagnostics
---

# Fixture: Async Or Process State - Outbox Stuck

## Cel

Ten fixture opisuje incydent, w ktorym request zakonczyl lokalny zapis, ale
dalszy etap procesu nie rusza przez stuck event/outbox state.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `async_or_process_state`.
3. Potem przejscie do `incident-data-diagnostics`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-async-001`
- trigger: event `CustomerUpdated`
- failure point: outbox retry exhausted
- log: `OutboxMessageProcessingException eventId=EVT-778 retryCount=10`
- code hint: `CustomerUpdatedListener -> OutboxMessageRepository`
- table/key: `OUTBOX_MESSAGE.EVENT_ID = EVT-778`

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `event -> listener -> outbox row -> retry/error state`.
2. Zaladuj `incident-data-diagnostics`.
3. Wykonaj DB test rozrozniajacy: event row, state, retry count, timestamps,
   last error, next retry.
4. Jezeli event/process row jest stuck albo exhausted, utrzymaj
   `async_or_process_state`.
5. Jezeli last error wskazuje downstream, rozwaz `integration_downstream_failure`
   jako dodatkowa klase.
6. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze proces jest opozniony po asynchronicznym etapie.

### `technicalAnalysis`

- Wskazuje event id, table, state, retry count i sposob wznowienia/weryfikacji.

## Antywzorce

- Nie traktuj braku synchronicznego bledu HTTP jako braku incydentu.
- Nie restartuj procesu w rekomendacji bez wskazania stanu i ryzyka duplikacji.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
