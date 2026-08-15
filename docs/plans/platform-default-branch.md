# Wspolny default branch platformy

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Flow Explorer pobiera default branch z feature-specific konfiguracji i API,
podczas gdy UI Explorer utrzymuje ten sam default jako hardcode w Angularze.
Powoduje to rozjazd zachowania oraz wymaga niezaleznej zmiany kilku feature'ow.
Uzytkownik zatwierdzil jedna platformowa konfiguracje bez kompatybilnosci
wstecznej dla starego klucza i endpointu.

## Proponowane rozwiazanie

Jedynym source of truth bedzie wymagane
`platform.source-code.default-branch` w `application.properties`, mapowane przez
mala neutralna konfiguracje w `common`. Backend Flow Explorera i shared
`GET /api/ui/config` beda czytac ten sam obiekt. Angularowy shared
`AppUiConfigService` dostarczy wartosc Flow Explorerowi i UI Explorerowi.
Feature-specific `features.flow-explorer.default-branch`, Java properties,
`GET /api/flow-explorer/config` oraz frontendowy model/metoda zostana usuniete.

## Zakres

- wspolna, walidowana konfiguracja backendu,
- migracja backendowego fallbacku Flow Explorera,
- rozszerzenie shared UI config contract,
- migracja obu explorerow do shared Angular config,
- aktualizacja testow i dokumentacji architektury.

## Non-goals

- zmiana repository-level `git.defaultBranch` w Operational Context,
- automatyczne wykrywanie default branch z GitLaba,
- fallback do `main` poza kanoniczna konfiguracja,
- kompatybilny alias starego property albo endpointu.

## Ograniczenia i ryzyka

- jest to L1 breaking migration kontraktu konfiguracji i frontendowego API,
- brak lub pusta wartosc platformowa ma zatrzymac start backendu zamiast cicho
  przyjmowac `main`,
- async odczyt shared UI config nie moze nadpisac branch/ref wpisanego przez
  operatora ani odtworzonego z historii,
- wszystkie nowe i zmienione testy pozostaja silnie zanonimizowanym CRM.

## Kryteria akceptacji

- istnieje dokladnie jeden aplikacyjny default branch,
- Flow Explorer i UI Explorer inicjalizuja sie ta sama wartoscia,
- stary property i feature-specific config endpoint nie istnieja,
- testy backendu i frontendu oraz package dependency guard przechodza.

## Baseline i konsumenci

Baseline: `features.flow-explorer.default-branch=main` zasila
`FlowExplorerProperties`, backendowy repository scope oraz
`GET /api/flow-explorer/config`; Flow Explorer pobiera ten endpoint. UI Explorer
inicjalizuje branch lokalna stala `main`. Shared `/api/ui/config` zawiera tylko
branding. Konsumenci delty: Flow Explorer repository scope i Angular workspace,
UI Explorer facade, shared UI config API/service oraz app-shell test fixtures.

## Kroki

- [x] Dodac wymagane `platform.source-code.default-branch`, zmigrowac backend
  Flow Explorera i shared UI config, usunac stary property/API bez aliasu oraz
  pokryc kontrakt testami CRM.
- [x] Zmigrowac Angular Flow Explorer i UI Explorer na `AppUiConfigService`,
  usunac feature-specific klienta/model oraz zweryfikowac race, blad configu i
  brak nadpisania wyboru operatora testami CRM.
- [x] Uruchomic celowane i pelne testy/build adekwatne dla wspolnej zmiany,
  wykonac architecture/import diff i zaktualizowac kanoniczna dokumentacje.

## Wynik weryfikacji

Breaking migracja zostala wykonana bez aliasu: stary property, Java
`FlowExplorerProperties`, feature-specific response/controller oraz Angular
client/model nie istnieja. `GET /api/ui/config` zwraca wspolny
`defaultBranch`, a oba explorery stosuja go tylko do pustego pola. Celowane
testy objely konfiguracje, shared API, repository scope, oba Angular workspace
i race asynchronicznego configu. Przeszlo 409 testow Angulara w 55 plikach,
produkcyjny build Angulara oraz `mvn -q -Pbackend-dev clean package` z pelnym
zestawem testow backendu. Package/import diff nie dodal odwrotnych zaleznosci,
a `git diff --check` przeszedl. Wszystkie nowe i zmienione przyklady testowe sa
silnie zanonimizowanym CRM.
