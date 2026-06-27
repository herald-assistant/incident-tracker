---
name: configuration-or-environment-feature-flag
expectedClassification: configuration_or_environment
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-operational-context-tools
---

# Fixture: Configuration Or Environment - Feature Flag

## Cel

Ten fixture opisuje incydent spowodowany roznica konfiguracji albo srodowiska,
ktora zmienia zachowanie flow.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `configuration_or_environment`.
3. Potem przejscie do `incident-operational-context-tools`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-conf-001`
- trigger: catalog refresh
- failure point: feature-flagged tax calculation path
- log: `MissingTaxProfileException for country=DE`
- config hint: `tax.v2.enabled=true` in `dev3`, false in previous env
- operational context: tax process owner differs from catalog owner

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `catalog refresh job -> tax strategy selection -> missing tax profile`.
2. Sprawdz, czy evidence wskazuje property, feature flag, deployment albo env
   drift.
3. Zaladuj `incident-operational-context-tools`, jezeli potrzebny jest system,
   owner, process albo config routing.
4. Jezeli roznica konfiguracji najlepiej tlumaczy przerwanie, utrzymaj
   `configuration_or_environment`.
5. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze inna sciezka procesu zostala wlaczona konfiguracja.

### `technicalAnalysis`

- Wskazuje property/flag/env, expected owner i weryfikacje konfiguracji.

## Antywzorce

- Nie zgaduj konfiguracji bez evidence.
- Nie mieszaj config drift z data missing, jezeli brak danych wynika z flagi.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
