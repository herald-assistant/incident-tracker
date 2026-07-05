---
name: configuration-or-environment-feature-flag
expectedClassification: configuration_or_environment
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-operational-grounding
---

# Fixture: Configuration Or Environment - Feature Flag

## Cel

Ten fixture opisuje incydent spowodowany roznica konfiguracji albo srodowiska,
ktora zmienia zachowanie flow.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `configuration_or_environment`.
3. Potem przejscie do `incident-operational-grounding`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-conf-001`
- trigger: customer profile refresh
- failure point: feature-flagged customer segmentation path
- log: `MissingCustomerSegmentException for segment=VIP`
- config hint: `crm.segmentation.v2.enabled=true` in `dev3`, false in previous env
- operational context: customer segmentation owner differs from CRM profile owner

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `customer profile refresh job -> customer segmentation strategy selection -> missing customer segment`.
2. Sprawdz, czy evidence wskazuje property, feature flag, deployment albo env
   drift.
3. Zaladuj `incident-operational-grounding`, jezeli potrzebny jest system,
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
