# UI Explorer: start Angular przez NgModule

Status: in-progress

Source need: [UI Explorer dla aplikacji Angular z NgModule](../needs/ui-explorer-ngmodule-routing.md)

## Potrzeba / dlaczego

Potwierdzony frontend z `bootstrapModule` i `RouterModule.forRoot` daje pusty
katalog, poniewaz discovery rozpoznaje tylko `bootstrapApplication` i
`provideRouter`. Uzytkownik zlecil dodanie obslugi tego wariantu.

## Proponowane rozwiazanie

Rozszerzyc neutralny parser i discovery Angulara w `integrations.gitlab.frontend`.
Od znalezionego startu przejsc po statycznych importach modulow do osiagalnego
`RouterModule.forRoot`, nastepnie wykorzystac istniejacy traversal tras i
komponentow. Zachowac ograniczenia scope, limity, diagnostyki i unikalnosc
produkcyjnego rootu. Uniewaznic poprzednia wersje cache katalogu widokow.

## Zakres

Start `platformBrowserDynamic().bootstrapModule(...)`, statyczne `@NgModule`
imports i `RouterModule.forRoot(...)`, wersja cache, testy CRM oraz aktualizacja
opisu runtime.

## Non-goals

`RouterModule.forChild`, dynamiczne metadata, uruchamianie TypeScriptu i zmiana
kontraktow HTTP, AI, raportu oraz UI.

## Ograniczenia i ryzyka

Zmiana L2 w neutralnej integracji. Konsumenci: UI Explorer, UX Inspector,
diagnostyczne API GitLab i frontend catalog. Parser pozostaje bounded i
targeted; nierozstrzygniete importy nie moga zostac uznane za widoki.

## Kryteria akceptacji

Katalog zwraca widoki z fikcyjnej aplikacji CRM z modulowym startem. Standalone
dziala nadal. Niejednoznaczne rooty i brak statycznego powiazania sa jawne.

## Baseline i conformance delta

- Baseline: publiczne `/api/ui-explorer/screens` mapuje neutralny katalog;
  discovery znajduje jeden produkcyjny standalone root, potem traversuje
  statyczne trasy, zachowuje immutable commit i diagnostics. UX Inspector uzywa
  tego samego katalogu. Publiczne DTO, job, AI i persistence pozostaja bez zmian.
- Delta: sposob znalezienia rootu Angular i jego pierwszej kolekcji tras oraz
  uniewaznienie poprzedniego cache katalogu. Bez nowych zaleznosci miedzy
  warstwami i bez zmiany HTTP/DTO, promptu,
  tool policy, result, historii lub wspolnego UX.
- Regresja: testy bootstrap i route graph, frontend catalog oraz
  `PackageDependencyGuardTest`; przed przekazaniem `mvn -q test`.

## Kroki

- [x] Rozszerzyc bounded discovery o modulowy start i osiagalny `forRoot`;
  zweryfikowac testem CRM oraz regresja standalone. Celowane testy bootstrap,
  route graph, catalog i guard przeszly; nowy katalog zwrocil 5 widokow.
- [ ] Zaktualizowac runtime flow i wykonac testy backendu oraz przeglad diffu;
  potwierdzic katalog na zarejestrowanym repo po odswiezeniu cache. Runtime
  flow, przeglad diffu, kompilacja i smoke sa wykonane. Pelny `mvn -q test`
  blokuje niezalezny blad kompilacji w rownolegle zmienianym tescie
  Operational Context; pozostaje powtorzyc go po naprawie tego testu.
