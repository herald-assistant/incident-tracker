---
name: flow-explorer-test-scenario-design
description: Goal guidance dla Flow Explorer TEST_SCENARIOS - soczewka celu dla scenariuszy testowych bez przejmowania kontraktu raportu ani procedur sekcyjnych.
---

# Flow Explorer Goal: Test Scenarios

Uzywaj tego skilla, gdy `goal` w promptcie ma wartosc `TEST_SCENARIOS`.

## Cel

Dostarcz `GoalGuidance` dla celu `TEST_SCENARIOS`: ukierunkuj wynik na material
przydatny testerowi albo analitykowi przygotowujacemu odbior endpointu.

Ten skill nie tworzy osobnego top-level pola `testScenarios`, nie buduje
finalnego raportu i nie odkrywa samodzielnie persistence ani integracji.
Finalny ksztalt wyniku nalezy do `flow-explorer-write-report`.

## Wejscia

Korzystaj z:

- `EndpointFlowSummary`,
- aktywnych `sectionModes`,
- source refs, visibility limits i open questions,
- `CodeGroundingSummary` i `OperationalGroundingSummary`, jezeli sa dostepne,
- `PersistenceMappingSummary` i `IntegrationBoundarySummary`, jezeli testy
  zaleza od danych albo granic zewnetrznych.

## Rola

`TEST_SCENARIOS` jest soczewka celu. Ma wskazac, jak czytac material pod katem:

- happy path i alternate paths,
- danych wejsciowych, stanu przed i stanu po,
- odrzucen, walidacji i edge case'ow,
- setupu danych i regresji,
- zaleznosci zewnetrznych, stubow, kolejek albo handoffow,
- luk, ktore blokuja wiarygodny test.

## Procedura

1. Ustal aktywne sekcje z `sectionModes`.
2. Nazwij, jakie pytanie testowe ma obsluzyc kazda aktywna sekcja.
3. Jezeli pytanie testowe wymaga kodu, popros o `CodeGroundingSummary`.
4. Jezeli pytanie testowe wymaga terminu domenowego albo ownera, popros o
   `OperationalGroundingSummary`.
5. Jezeli pytanie testowe dotyczy danych albo integracji, uzyj
   `PersistenceMappingSummary` albo `IntegrationBoundarySummary`.
6. Przekaz tylko `GoalGuidance` do `flow-explorer-write-report`.

## Akcent Celu

Dla aktywnych sekcji kieruj material tak:

- `OVERVIEW`: ustaw priorytet testowy i najwazniejszy obszar niepewnosci.
- `FUNCTIONAL_FLOW`: wskaz sciezki, warianty, decyzje i oczekiwane efekty,
  ktore warto pokryc testami.
- `VALIDATIONS`: wskaz dane poprawne, niepoprawne, graniczne, wymagane stany i
  oczekiwane odrzucenia.
- `PERSISTENCE`: wskaz setup danych, stan przed/po i regresje danych na
  podstawie `PersistenceMappingSummary`, bez zgadywania tabel albo kolumn.
- `INTEGRATIONS`: wskaz potrzebe mockow, stubow, kolejek, eventow albo
  kontraktow testowych na podstawie `IntegrationBoundarySummary`, bez
  zgadywania payloadu, headerow albo ownera.

Nie powtarzaj tej samej listy scenariuszy w kilku sekcjach. Jezeli scenariusz
dotyka wielu obszarow, w `GoalGuidance` opisz tylko perspektywe danej sekcji.

## Kontrakt Wyniku

Zwroc `GoalGuidance`:

```text
goal: TEST_SCENARIOS
testFocus:
  - <najwazniejszy obszar testowy>
sectionGuidance:
  FUNCTIONAL_FLOW: <sciezki i oczekiwane efekty, jezeli sekcja aktywna>
  VALIDATIONS: <dane i odrzucenia, jezeli sekcja aktywna>
  PERSISTENCE: <setup/stany na podstawie PersistenceMappingSummary, jezeli dotyczy>
  INTEGRATIONS: <stuby/awarie na podstawie IntegrationBoundarySummary, jezeli dotyczy>
sourceRefs:
  - <najwazniejsze refs, jezeli guidance ich wymaga>
visibilityLimits:
  - <braki blokujace wiarygodny test>
```

## Walidacja

Sprawdz, czy:

- guidance nie tworzy osobnego top-level `testScenarios`,
- scenariusze wynikaja z aktywnych sekcji,
- setup persistence nie zgaduje tabel ani kolumn bez `PersistenceMappingSummary`,
- integracje nie zgaduja payloadu, headerow, retry ani ownera bez
  `IntegrationBoundarySummary`,
- kazdy istotny scenariusz ma oczekiwany efekt albo visibility limit.

## Fallbacki

Jezeli brakuje evidence:

- wpisz scenariusz jako open question albo visibility limit,
- nazwij, ktory artifact albo summary zamknalby luke,
- nie wymyslaj kodow bledow, payloadow ani stanu DB.

## Artefakty Handoffu

Przekaz do orkiestratora albo `flow-explorer-write-report`:

- `GoalGuidance` dla `TEST_SCENARIOS`,
- zaleznosci od `CodeGroundingSummary`, `OperationalGroundingSummary`,
  `PersistenceMappingSummary` albo `IntegrationBoundarySummary`,
- source refs i visibility limits wazne dla testow.

## Antywzorce

Nie:

- pisz finalnego raportu,
- tworz jednej generycznej listy testow poza sekcjami,
- powtarzaj procedur skilli sekcyjnych,
- wymyslaj kody bledow, payloady, tabele albo ownerow,
- opisuj testow jezykiem klas i metod zamiast procesu, danych i efektu.
