---
name: flow-explorer-risk-assessment
description: Goal guidance dla Flow Explorer RISK_DETECTION - soczewka celu dla ryzyk, luk i pytan otwartych bez przejmowania kontraktu raportu ani procedur sekcyjnych.
---

# Flow Explorer Goal: Risk Detection

Uzywaj tego skilla, gdy `goal` w promptcie ma wartosc `RISK_DETECTION`.

## Cel

Dostarcz `GoalGuidance` dla celu `RISK_DETECTION`: ukierunkuj wynik na ryzyka,
luki widocznosci, regresje i pytania otwarte zwiazane z endpointem.

Ten skill nie tworzy osobnego top-level pola `risks`, nie buduje finalnego
raportu i nie odkrywa samodzielnie persistence ani integracji. Finalny ksztalt
wyniku nalezy do `flow-explorer-write-report`.

## Wejscia

Korzystaj z:

- `EndpointFlowSummary`,
- aktywnych `sectionModes`,
- source refs, visibility limits i open questions,
- `CodeGroundingSummary` i `OperationalGroundingSummary`, jezeli sa dostepne,
- `PersistenceMappingSummary` i `IntegrationBoundarySummary`, jezeli ryzyko
  zalezy od danych albo granic zewnetrznych.

## Rola

`RISK_DETECTION` jest soczewka celu. Ma oznaczyc, ktore elementy materialu
podnosza ryzyko dla procesu, testow, danych albo handoffu.

Kazdy istotny wpis oznacz jako:

- `Fakt z evidence`,
- `Inferencja`,
- `Luka widocznosci`,
- `Pytanie otwarte`.

## Procedura

1. Ustal aktywne sekcje z `sectionModes`.
2. Dla kazdej aktywnej sekcji nazwij typ ryzyka, skutek i sposob zamkniecia.
3. Jezeli ryzyko wymaga kodu, popros o `CodeGroundingSummary`.
4. Jezeli ryzyko wymaga nazw domenowych, ownershipu albo handoffu, popros o
   `OperationalGroundingSummary`.
5. Jezeli ryzyko dotyczy danych albo integracji, oprzyj je na
   `PersistenceMappingSummary` albo `IntegrationBoundarySummary`.
6. Przekaz tylko `GoalGuidance` do `flow-explorer-write-report`.

## Akcent Celu

Dla aktywnych sekcji kieruj material tak:

- `OVERVIEW`: pokaz najwazniejszy obszar ryzyka i glowna luke widocznosci.
- `FUNCTIONAL_FLOW`: wskaz niejasne decyzje, warianty, routing, kalkulacje,
  skutki domenowe i regresje procesu.
- `VALIDATIONS`: wskaz ryzyka brakujacych walidacji, zlych danych, statusow,
  uprawnien, konfliktow stanu albo niejasnych odrzucen.
- `PERSISTENCE`: wskaz ryzyka stanu danych, transakcji, idempotencji, setupu i
  regresji na podstawie `PersistenceMappingSummary`, bez zgadywania tabel albo
  kolumn.
- `INTEGRATIONS`: wskaz ryzyka handoffow, upstream/downstream, timeoutow,
  retry, kolejek, eventow, ownerow albo kontraktow na podstawie
  `IntegrationBoundarySummary`, bez zgadywania payloadu albo headerow.

Nie powtarzaj tego samego ryzyka w wielu sekcjach bez innej perspektywy.

## Kontrakt Wyniku

Zwroc `GoalGuidance`:

```text
goal: RISK_DETECTION
riskFocus:
  - <najwazniejszy obszar ryzyka>
sectionGuidance:
  FUNCTIONAL_FLOW: <ryzyka procesu i warunkow, jezeli sekcja aktywna>
  VALIDATIONS: <ryzyka odrzucen i danych wejsciowych, jezeli sekcja aktywna>
  PERSISTENCE: <ryzyka danych na podstawie PersistenceMappingSummary, jezeli dotyczy>
  INTEGRATIONS: <ryzyka granic na podstawie IntegrationBoundarySummary, jezeli dotyczy>
sourceRefs:
  - <najwazniejsze refs, jezeli guidance ich wymaga>
visibilityLimits:
  - <braki, ktore zmieniaja ocene ryzyka>
openQuestions:
  - <pytania do zespolu albo analityka>
```

## Walidacja

Sprawdz, czy:

- guidance nie tworzy osobnego top-level `risks`,
- kazde ryzyko ma typ, skutek i sposob weryfikacji albo zamkniecia,
- ryzyka persistence nie zgaduja tabel ani kolumn bez `PersistenceMappingSummary`,
- ryzyka integrations nie zgaduja payloadu, headerow, retry ani ownera bez
  `IntegrationBoundarySummary`,
- hipotezy nie sa prezentowane jako fakty.

## Fallbacki

Jezeli brakuje evidence:

- nazwij brak jako `Luka widocznosci` albo `Pytanie otwarte`,
- wskaz, ktory artifact albo summary zamknalby ryzyko,
- nie podnos hipotezy do faktu.

## Artefakty Handoffu

Przekaz do orkiestratora albo `flow-explorer-write-report`:

- `GoalGuidance` dla `RISK_DETECTION`,
- zaleznosci od `CodeGroundingSummary`, `OperationalGroundingSummary`,
  `PersistenceMappingSummary` albo `IntegrationBoundarySummary`,
- source refs, visibility limits i open questions wazne dla ryzyk.

## Antywzorce

Nie:

- pisz finalnego raportu,
- tworz generycznej listy ryzyk niezwiazanych z endpointem,
- powtarzaj procedur skilli sekcyjnych,
- wymyslaj awarii integracji, payloadow, tabel albo ownerow,
- ukrywaj niepewnosci pod wysokim confidence.
