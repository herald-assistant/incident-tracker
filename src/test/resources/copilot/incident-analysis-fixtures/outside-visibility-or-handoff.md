---
name: outside-visibility-or-handoff-crm-notification-rejection
expectedClassification: outside_visibility_or_handoff
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-operational-grounding
---

# Fixture: Outside Visibility Or Handoff - CRM Notification Rejection

## Cel

Ten fixture opisuje incydent, w ktorym lokalny system widzi odrzucenie, ale
przyczyna zrodlowa lezy poza aktualna widocznoscia.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `outside_visibility_or_handoff`.
3. Potem przejscie do `incident-operational-grounding`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-out-001`
- trigger: CRM contact notification submission
- failure point: CRM notification provider API rejects request
- log: `CrmNotificationRejectedException: status=422 reasonCode=CRM-VAL-19`
- local code hint: payload was built and sent successfully
- missing visibility: CRM notification validation details

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `local validation -> payload build -> CRM notification provider call -> external rejection`.
2. Zweryfikuj, czy lokalne evidence wystarcza tylko do handoffu.
3. Zaladuj `incident-operational-grounding` dla downstream owner, route i
   handoff evidence package.
4. Jesli root cause wymaga danych dostawcy powiadomien CRM, utrzymaj
   `outside_visibility_or_handoff`.
5. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze proces zostal odrzucony poza analizowanym systemem.

### `technicalAnalysis`

- Wskazuje payload/correlation/status/reason code i pytanie do odbiorcy.

## Antywzorce

- Nie udawaj potwierdzonego root cause po stronie dostawcy powiadomien CRM.
- Nie wymuszaj lokalnej poprawki, gdy evidence mowi o handoffie.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
