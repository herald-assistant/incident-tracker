---
name: inconclusive-insufficient-evidence
expectedClassification: inconclusive
starterSkill: incident-analysis-orchestrator
specializedSkill: none
---

# Fixture: Inconclusive - Insufficient Evidence

## Cel

Ten fixture opisuje incydent, w ktorym evidence nie wystarcza do uczciwej
klasyfikacji root cause.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `inconclusive`.
3. Potem nie ma osobnego skilla diagnostycznego.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-inc-001`
- trigger: unknown
- log: `Unhandled exception while processing request`
- missing evidence: no stacktrace, no endpoint, no deployment context, no DB
  key, no downstream status
- known limit: only generic error log is available

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja na tyle, na ile pozwala evidence.
2. Nie wymyslaj endpointu, systemu, ownera, DB table ani downstream.
3. Jezeli nie da sie rozroznic data/code/integration/runtime, utrzymaj
   `inconclusive`.
4. Wskaz minimalne brakujace evidence potrzebne do kolejnej proby diagnozy.
5. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol, ale
   oznacz ograniczenia widocznosci.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia tylko to, co wiadomo o procesie albo wprost pisze `Nie ustalono`.

### `technicalAnalysis`

- Nie udaje fixu; wskazuje brakujace logi, stacktrace, request path albo ownera.

## Antywzorce

- Nie klasyfikuj na sile.
- Nie tworz rekomendacji technicznej bez failure point.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
