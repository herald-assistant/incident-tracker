---
name: outside-visibility-or-handoff-partner-rejection
expectedClassification: outside_visibility_or_handoff
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-operational-context-tools
---

# Fixture: Outside Visibility Or Handoff - Partner Rejection

## Cel

Ten fixture opisuje incydent, w ktorym lokalny system widzi odrzucenie, ale
przyczyna zrodlowa lezy poza aktualna widocznoscia.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `outside_visibility_or_handoff`.
3. Potem przejscie do `incident-operational-context-tools`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-out-001`
- trigger: partner submission
- failure point: partner API rejects request
- log: `PartnerRejectedException: status=422 reasonCode=EXT-VAL-19`
- local code hint: payload was built and sent successfully
- missing visibility: partner validation details

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `local validation -> payload build -> partner call -> external rejection`.
2. Zweryfikuj, czy lokalne evidence wystarcza tylko do handoffu.
3. Zaladuj `incident-operational-context-tools` dla downstream owner, route i
   handoff evidence package.
4. Jesli root cause wymaga danych partnera, utrzymaj
   `outside_visibility_or_handoff`.
5. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze proces zostal odrzucony poza analizowanym systemem.

### `technicalAnalysis`

- Wskazuje payload/correlation/status/reason code i pytanie do odbiorcy.

## Antywzorce

- Nie udawaj potwierdzonego root cause po stronie partnera.
- Nie wymuszaj lokalnej poprawki, gdy evidence mowi o handoffie.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
