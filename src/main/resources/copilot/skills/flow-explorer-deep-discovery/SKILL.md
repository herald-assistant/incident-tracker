---
name: flow-explorer-deep-discovery
description: Goal guidance dla Flow Explorer DEEP_DISCOVERY - soczewka celu dla pelnego zrozumienia endpointu bez przejmowania kontraktu raportu ani procedur sekcyjnych.
---

# Flow Explorer Goal: Deep Discovery

Uzywaj tego skilla, gdy `goal` w promptcie ma wartosc `DEEP_DISCOVERY`.

## Cel

Dostarcz `GoalGuidance` dla celu `DEEP_DISCOVERY`: ukierunkuj pozostale skille
na samowystarczalne zrozumienie endpointu przez analityka, testera albo
analityka systemowego.

Ten skill nie buduje finalnego raportu, nie definiuje formatu sekcji i nie
domyka procedur `PERSISTENCE` ani `INTEGRATIONS`. Finalny ksztalt wyniku nalezy
do `flow-explorer-write-report`, a szczegoly sekcyjne do:

- `flow-explorer-map-persistence-section`,
- `flow-explorer-map-integrations-section`.

## Wejscia

Korzystaj z:

- `EndpointFlowSummary`,
- aktywnych `sectionModes`,
- source refs, visibility limits i open questions,
- `CodeGroundingSummary` i `OperationalGroundingSummary`, jezeli sa dostepne,
- `PersistenceMappingSummary` i `IntegrationBoundarySummary`, jezeli aktywne
  sekcje ich wymagaja.

## Rola

`DEEP_DISCOVERY` jest soczewka celu. Ma odpowiedziec, co w materiale jest
najwazniejsze dla zrozumienia endpointu:

- glowny sens endpointu i efekt dla procesu,
- decyzje, reguly, warianty i edge case'y,
- dane czytane albo zmieniane jako element zachowania,
- handoffy i integracje jako element przeplywu,
- luki widocznosci, ktore moga zmienic interpretacje.

## Procedura

1. Ustal aktywne sekcje z `sectionModes`.
2. Nazwij akcent `DEEP_DISCOVERY` dla kazdej aktywnej sekcji.
3. Jezeli akcent wymaga kodu, popros o `CodeGroundingSummary`.
4. Jezeli akcent wymaga nazw domenowych, popros o `OperationalGroundingSummary`.
5. Jezeli akcent dotyczy persistence albo integrations, oprzyj go na
   `PersistenceMappingSummary` albo `IntegrationBoundarySummary`.
6. Przekaz tylko `GoalGuidance` do `flow-explorer-write-report`.

## Akcent Celu

Dla aktywnych sekcji kieruj material tak:

- `OVERVIEW`: pokaz cel endpointu, najwazniejszy przebieg i najwieksza luke
  widocznosci, jezeli moze zmienic interpretacje.
- `FUNCTIONAL_FLOW`: podkresl decyzje, warianty, kalkulacje, statusy, routing i
  skutki dla procesu.
- `VALIDATIONS`: podkresl warunki dopuszczenia, odrzucenia, edge case'y i
  niejawne guard clauses.
- `PERSISTENCE`: pokaz znaczenie read/write path dla zrozumienia endpointu,
  ale szczegoly tabel, kolumn i zrodel bierz tylko z `PersistenceMappingSummary`.
- `INTEGRATIONS`: pokaz znaczenie zewnetrznych handoffow dla flow, ale targety,
  adresy, payload i ownerow bierz tylko z `IntegrationBoundarySummary`.

Nie opisuj w tym skillu nazw punktow sekcji, kolejnosci sekcji, fallback JSON,
report tools, pelnej tabeli persistence ani pelnego kontraktu integracji.

## Kontrakt Wyniku

Zwroc `GoalGuidance`:

```text
goal: DEEP_DISCOVERY
emphasis:
  - <co jest najwazniejsze dla zrozumienia endpointu>
sectionGuidance:
  FUNCTIONAL_FLOW: <akcent celu, jezeli sekcja aktywna>
  VALIDATIONS: <akcent celu, jezeli sekcja aktywna>
  PERSISTENCE: <akcent celu + odnosnik do PersistenceMappingSummary, jezeli dotyczy>
  INTEGRATIONS: <akcent celu + odnosnik do IntegrationBoundarySummary, jezeli dotyczy>
sourceRefs:
  - <najwazniejsze refs, jezeli guidance ich wymaga>
visibilityLimits:
  - <luki, ktore zmieniaja zrozumienie endpointu>
```

## Walidacja

Sprawdz, czy:

- guidance nie zmienia `sectionModes`,
- nie przejmuje kontraktu `AnalysisReport`,
- persistence korzysta z `PersistenceMappingSummary`, gdy potrzebne sa szczegoly
  danych,
- integrations korzysta z `IntegrationBoundarySummary`, gdy potrzebne sa
  szczegoly granic,
- source refs i luki sa przekazane jako handoff, nie jako finalny raport.

## Fallbacki

Jezeli brakuje summary artifact:

- wskaz brak jako visibility limit albo open question,
- nie tworz tabel, payloadow, ownerow ani source values z domyslow,
- ogranicz `GoalGuidance` do potwierdzonej czesci flow.

## Artefakty Handoffu

Przekaz do orkiestratora albo `flow-explorer-write-report`:

- `GoalGuidance` dla `DEEP_DISCOVERY`,
- zaleznosci od `CodeGroundingSummary`, `OperationalGroundingSummary`,
  `PersistenceMappingSummary` albo `IntegrationBoundarySummary`,
- source refs i visibility limits wazne dla celu.

## Antywzorce

Nie:

- pisz finalnego raportu,
- definiuj struktury `FUNCTIONAL_FLOW` albo `VALIDATIONS`,
- powtarzaj procedur `flow-explorer-map-persistence-section` albo
  `flow-explorer-map-integrations-section`,
- opisuj endpointu klasa po klasie,
- udawaj pewnosci, gdy evidence nie pokazuje istotnego fragmentu.
