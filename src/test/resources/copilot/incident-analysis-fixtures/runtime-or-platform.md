---
name: runtime-or-platform-pod-restart
expectedClassification: runtime_or_platform
starterSkill: incident-analysis-orchestrator
specializedSkill: none
---

# Fixture: Runtime Or Platform - Pod Restart

## Cel

Ten fixture opisuje incydent, w ktorym flow przerywa runtime albo platforma, a
nie kod, dane ani downstream.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `runtime_or_platform`.
3. Potem nie ma osobnego runtime skilla diagnostycznego.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-run-001`
- trigger: HTTP request `POST /notifications`
- failure point: request lost during pod restart
- runtime evidence: `container restart count increased`, `OOMKilled`
- log: `Request aborted while processing notification NOTIF-123`
- deployment: `notification-api`

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `HTTP request -> notification service -> runtime termination`.
2. Odczytaj runtime/Dynatrace evidence literalnie.
3. Jezeli component status pokazuje abnormal runtime signal zgodny z czasem
   incydentu, utrzymaj `runtime_or_platform`.
4. Jezeli runtime evidence jest `UNAVAILABLE` albo `NO_MATCH`, nie traktuj tego
   jako dowodu zdrowego runtime.
5. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze proces zostal przerwany technicznie w runtime.

### `technicalAnalysis`

- Wskazuje deployment/pod, sygnal runtime, okno czasu i akcje dla platformy.

## Antywzorce

- Nie diagnozuj code/data issue, jezeli jedyny mocny sygnal to OOM/restart.
- Nie uznawaj braku Dynatrace match za potwierdzenie zdrowia runtime.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
