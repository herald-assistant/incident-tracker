# UI Explorer Runtime Flow

## Cel i granica produktu

UI Explorer tworzy funkcjonalna dokumentacje jednego widoku frontendu w
konkretnym scenariuszu i na konkretnej rewizji kodu. Odbiorca jest analitykiem
biznesowo-systemowym: raport wyjasnia zachowanie uzytkownika, reguly, dane,
akcje, warianty i ograniczenia bez wymagania znajomosci Angulara ani
repozytorium.

UI Explorer nie uruchamia badanego interfejsu i nie obserwuje stanu runtime.
Statyczne zrodla potwierdzaja mozliwe zachowania, ale nie dowodza danych,
autoryzacji backendowej ani regul wykonywanych poza dostepnym repository
scope. UX Inspector pozostaje osobnym feature'em dla jednego elementu
wskazanego przez Browser Tools.

## Publiczne wejscia

Feature udostepnia:

- `GET /api/ui-explorer/input-options` - kwalifikujace sie frontendy i stale
  opcje formularza,
- `GET /api/ui-explorer/screens?systemId=...&branch=...&refresh=...` - katalog
  widokow przypiety do immutable source revision,
- `POST /api/ui-explorer/jobs` - asynchroniczny start analizy,
- `GET /api/ui-explorer/jobs/{jobId}` - aktualny snapshot runu,
- `POST /api/ui-explorer/jobs/{jobId}/chat/messages` - follow-up dla aktywnego
  joba,
- `GET /api/ui-explorer/jobs/{jobId}/export` - sanitizowany portable export,
- `POST /api/ui-explorer/imports` - walidowany import read-only,
- shared `GET /api/analysis/runs/{analysisId}` i
  `POST /api/analysis/runs/{analysisId}/chat/messages` - odczyt oraz
  kontynuacje z historii po restarcie backendu.

Start request zawiera tylko `systemId`, `branch`, katalogowy `screenId`,
oczekiwany `sourceRevision`, tryby sekcji, opcjonalny opis scenariusza oraz
preferencje modelu i reasoning. Nie przyjmuje repository id/path, grupy
GitLaba, tokenu, sciezek plikow, nazw komponentow, promptow ani tools.
Nieznane pola sa odrzucane.

Co najmniej jedna z osmiu sekcji musi byc aktywna. Tryby to `OFF`, `COMPACT`
i `DEEP`, a sekcje to:

1. `OVERVIEW` - cel i kontekst widoku,
2. `NAVIGATION_AND_ACCESS` - nawigacja i dostep,
3. `SCREEN_STRUCTURE` - struktura widoku,
4. `ACTIONS_AND_OUTCOMES` - akcje i rezultaty,
5. `FORMS_AND_RULES` - formularze i reguly,
6. `DATA_AND_SERVICES` - dane i uslugi,
7. `STATE_AND_SYNCHRONIZATION` - stan i synchronizacja,
8. `VARIANTS_AND_FAILURES` - warianty i sytuacje wyjatkowe.

## Rejestracja frontendu i katalog widokow

Lista aplikacji pochodzi z Operational Context. Frontend kwalifikuje sie tylko
jako jawnie zarejestrowany `internal-service/frontend` z jednym primary
repository i kontrolowanym code-search scope. Brak lub konflikt rejestracji
jest jawnym bledem konfiguracji; feature nie zgaduje repository.

Neutralny `frontendcatalog` i `integrations.gitlab.frontend` buduja katalog
Angular/Nx graph-first. Discovery zaczyna od produkcyjnego bootstrapu i routera,
przechodzi tylko po osiagalnych importach, `children` i lazy routes, a ref
rozwiazuje bezposrednio do immutable commit id. Nie tworzy repository
inventory i nie wykonuje TypeScriptu.

Obslugiwane statyczne wzorce obejmuja m.in. standalone/module routes,
`loadComponent`, `loadChildren`, local lazy factories, re-exporty, literalne
kolekcje splaszczane przez `reduce/flatMap` oraz anonimowy statyczny
`export default [...]` dla `loadChildren`. Dynamiczne kolekcje i
nierozstrzygalne wyrazenia pozostaja diagnostics zamiast podstawy do
zgadywania.

Katalog jest bounded. Domyslne limity to 200 000 znakow na plik i 2 000 000
lacznie; wyczerpanie limitu daje jedna diagnostyke przyczynowa. Wynik discovery
jest cache'owany w local workspace pod pelnym kluczem scope/ref/repository i
limitow. Zwykly odczyt reuse'uje cache po restarcie, a `refresh=true` usuwa i
odbudowuje tylko dopasowany wpis.

Publiczny katalog pokazuje biznesowa nazwe, `routePattern`, pomocnicza nazwe
komponentu, status i source revision. Nie ujawnia GitLab group, project ani
ukrytych search prefixes.

## Deterministyczny context pipeline

Job ponownie rozwiazuje hidden repository scope i wymaga zgodnosci
`sourceRevision` z katalogiem. Zmiana rewizji lub nieaktualny `screenId`
konczy sie kontrolowanym konfliktem wymagajacym odswiezenia katalogu.

`UiExplorerScreenReachabilityContextService` buduje bounded kontekst od
effective route chain oraz poddrzewa wybranego route node. Iteracyjny BFS
lacze routowane child views i osiagalne komponenty, po czym deduplikuje
faktycznie uzyte template'y, serwisy, state, guardy, walidatory i backend
clients. Puste segmenty child routes zachowuja najblizszego routowanego
rodzica.

Kontekst publikuje manifest, coverage, diagnostics, limity i jawna kolejke
dalszego researchu. Nie jest pelnym snapshotem repozytorium ani limitem dla
AI. Brak materialnego pliku, child route, formularza, modala, state logic lub
klienta w dozwolonym scope wymaga celowanego sprawdzenia przed uznaniem go za
ograniczenie widocznosci.

## Przygotowanie AI

Krok `AI_PREPARATION` jest wykonywany jednokrotnie przed otwarciem sesji.
Buduje feature prompt oraz logical artifacts zawierajace request i aktywne
sekcje, ekran i rewizje, route/reachability outline, source slices, coverage,
research queue, zasady funkcjonalnego pisania i kontrakt raportu. Kod,
komentarze oraz opis uzytkownika sa oznaczone jako untrusted evidence.

Source material zachowuje porzadek discovery, wszystkie rozne symbole,
metody, relacje i omission markers, a deduplikuje tylko identyczne importy lub
body w obrebie jednego pliku. Dokladny `preparedPrompt` jest publikowany po
przygotowaniu i pozostaje widoczny rowniez wtedy, gdy pozniejsza sesja AI
zawiedzie.

Workflow wskazuje trzy polskie runtime skille:

- `ui-explorer-orchestrator`,
- `ui-explorer-source-grounding`,
- `ui-explorer-write-report`.

Feature korzysta z platformowej polityki context tier w trybie `AUTO`.
Platforma podejmuje decyzje na podstawie dynamicznych metadanych modelu,
estymacji poczatkowego kontekstu i rzeczywistego usage. Ten sam mechanizm
obowiazuje create, resume i follow-up; feature nie zna rozmiarow okna ani nie
ustawia `long_context` bezposrednio.

## Sesja Copilota, tools i report-first result

Initial run tworzy nowa sesje Copilota z hidden repository/ref/source revision
oraz hidden report scope. Default-deny allowlista zawiera:

- `gitlab_read_frontend_route_branch_slice`,
- `gitlab_read_frontend_typescript_symbol_slice`,
- `gitlab_search_repository_candidates`,
- `gitlab_read_repository_file` lub chunked read,
- cztery platformowe `report_*` tools.

Route i TypeScript slices sa preferowane dla znanych targetow. Generyczny
search/read jest fallbackiem dla materialnej luki bez bezpiecznego targetu.
Model moze zawezic `pathPrefixes` do potomka hidden scope, ale nie moze go
rozszerzyc. Repository, branch/ref i commit nie sa model-facing inputem.
Feature nie naklada limitu poprawnych wywolan; petla konczy sie po readiness
albo po potwierdzeniu rzeczywistej granicy runtime, zewnetrznej biblioteki lub
scope.

Przed sesja powstaje pusty `AnalysisReport` ograniczony do aktywnych sekcji.
Model zapisuje header, sekcje i globalne meta przez report tools, a na koncu
odczytuje caly raport. Zapisany `AnalysisReport` jest jedynym zrodlem prawdy.
Finalna odpowiedz tekstowa Copilota jest tylko statusem i nie jest parserem
ani fallbackiem JSON.

Backend waliduje sekcje i source references wobec deterministic context oraz
captured tool evidence. Brak calego raportu konczy run bledem. Brak pojedynczej
aktywnej sekcji zachowuje pozostale poprawne sekcje i daje `PARTIAL`.
Zwalidowany raport jest deterministycznie projektowany na
`UiExplorerResultResponse` dla publicznego API.

## Kontrakt wyniku

Raport jest business-first i zachowuje stabilny uklad aktywnych sekcji.
Opisuje kto wykonuje czynnosc, kiedy jest dostepna, od czego zalezy, co zmienia
i jaki efekt widzi uzytkownik. Fakty, wnioski i niewiadome sa rozroznione;
globalne meta zawiera confidence, visibility limits i open questions.

Nazwy klas, metod, operatorow i plikow sa zwijanym evidence. W glownej
narracji zostaja tylko identyfikatory potrzebne do wyjasnienia zachowania.
Warunek widocznosci w frontendzie nie jest dowodem autoryzacji backendowej.
Konfiguracja dostarczana dopiero w runtime, niedostepna biblioteka, backendowa
regula lub dane runtime sa jawna granica widocznosci.

Result nie ma osobnego appendixu zaleznosci ani cross-section dependency
contract. Relacje trafiaja do wlasciwej sekcji tylko wtedy, gdy wyjasniaja
warunek, akcje albo rezultat.

## Job i snapshot

`POST /api/ui-explorer/jobs` zwraca `202 Accepted` z pierwszym snapshotem.
Praca przebiega poza watkiem HTTP przez stany:

`QUEUED -> DISCOVERING_SCREEN -> BUILDING_CONTEXT -> ANALYZING ->
COMPLETED | PARTIAL | BLOCKED | FAILED`.

Atomowy snapshot publikuje kroki, deterministic i tool evidence, aktywnosc AI,
feedback, usage, prepared prompt, result, report, source revision, output
availability oraz historie chatu. Kazdy krok wskazuje konsumowane i
produkowane evidence. Brak katalogu, kontekstu, readiness lub AI daje jawny
stan; feature nie tworzy placeholderowego raportu.

## Follow-up chat

Po `COMPLETED` albo `PARTIAL` uzytkownik moze wyjasniac raport, poglebiac
ustalenia i pytac o pominiety wariant. Odpowiedz kontynuuje prosty,
funkcjonalny styl raportu i nie zaklada znajomosci kodu.

Follow-up wznawia ta sama sesje Copilota i zachowuje pierwotny immutable
commit oraz hidden repository scope. Ma piec read-only research tools z
initial runu, ale nie dostaje report tools ani hidden report scope. Raport
jest kontekstem tylko do odczytu. Prosba o zmiane raportu moze zwrocic
propozycje tekstu w rozmowie, lecz nie modyfikuje dokumentu.

Jeden run dopuszcza jeden aktywny turn. Wspolny operation guard chroni przed
rownoleglym wyslaniem i usunieciem runu. Live endpoint zwraca `202` z
wiadomoscia asystenta `IN_PROGRESS`; UI polluje snapshot do stanu
`COMPLETED` albo `FAILED`. Evidence, activity, feedback, prompt i usage turnu
sa przechowywane w wiadomosci, ale techniczne usage nie jest renderowane pod
trescia odpowiedzi.

## Historia, restart i portability

Terminalny snapshot jest sanitizowany i zapisywany pod feature key
`ui-explorer`. Publiczny local run nie zawiera tokenow ani hidden repository
scope. Prywatny `tdw.ui-explorer-continuation/v1` zachowuje minimalny frozen
scope, source revision i fingerprint raportu potrzebne do bezpiecznego resume.

Historia po restarcie korzysta z zapisanej sesji i prywatnego snapshotu.
Przed turnem sprawdza zgodnosc joba, raportu, source scope, immutable commit,
session id oraz trybu autoryzacji. Nieudostepniona lub uszkodzona kontynuacja
jest jawnie niedostepna; aplikacja nie uruchamia nowej analizy pod pozorem
kontynuacji. Turn przerwany restartem staje sie `FAILED` i nie jest wysylany
ponownie automatycznie.

Local i portable envelope maja aktualnie wersje 6 oraz result contract v6.
Reader obsluguje legacy v5. Export zawiera sanitizowany wynik i historie
rozmowy, ale nie private continuation snapshot ani session handle. Import v5
lub v6 jest ponownie walidowany i sanitizowany, tworzy nowy run read-only i
nigdy nie aktywuje chatu.

## Workspace Angular

Ekran `/ui-explorer` prowadzi przez wybor aplikacji, branch/ref, widoku,
trybow sekcji, scenariusza, modelu i reasoning. Source revision pochodzi z
katalogu i jest czyszczona po zmianie scope. Konfiguracja jest blokowana na
czas aktywnego runu.

Wspolny aside pokazuje postep, workflow AI, evidence, feedback i follow-up
chat. Raport dla `COMPLETED` i `PARTIAL` pokazuje header, summary, aktywne
sekcje, confidence, references, visibility limits oraz open questions. Mozna
go skopiowac lub pobrac jako Markdown. UI rozroznia wynik live, history i
imported; import jest wyraznie read-only.

Glowna identyfikacja widoku uzywa `routePattern`. Nazwa komponentu pozostaje
pomocniczym metadata, a branch i commit opisuja wersje zrodla. Raw source i
raw JSON nie sa glowna trescia raportu.

## Granice pakietow i bezpieczenstwo

- `features.uiexplorer` posiada publiczny kontrakt, katalogowy adapter,
  context pipeline, prompt/skille, policy tools, report, job, chat i
  persistence.
- `frontendcatalog` posiada neutralny katalog zarejestrowanych frontendow i
  jego cache.
- `integrations.gitlab.frontend` posiada parsery, graph discovery,
  reachability i source slices.
- `agenttools` i `aiplatform` pozostaja neutralne wobec feature'a.
- `shared.ai.chat` posiada tylko wspolny stan wiadomosci i capture turnu.
- `features.uiexplorer` oraz `features.uxinspector` nie importuja siebie.

Source, opisy i komentarze sa niezaufanym evidence. Sesja ma jawna allowliste,
pre-tool policy, pinned revision, walidowany path scope, limity pojedynczego
transferu i platformowy timeout. UI Explorer nie uruchamia shell/filesystem,
nie modyfikuje repozytorium, nie publikuje dokumentacji i nie przejmuje sesji
Keycloak badanego systemu.

Kierunki zaleznosci sa egzekwowane przez `PackageDependencyGuardTest`.

## Minimalna macierz weryfikacji

Zmiana runtime UI Explorera powinna pokrywac co najmniej:

- kwalifikacje Operational Context, branch/ref, cache i scoped refresh,
- route discovery, lazy patterns, limits, diagnostics i immutable revision,
- stale screen/source revision oraz bounded reachability,
- prompt/artifacts, untrusted boundaries i widocznosc prepared promptu,
- default-deny tool policy, hidden scope i readiness,
- report-first zapis, partial sections i walidacje source references,
- job transitions, persistence, v6 export/import i legacy v5,
- live oraz history follow-up, ten sam session id, operation guard,
  przerwany turn i read-only import,
- business-first rendering, aside, copy/download i statusy terminalne,
- testy Angulara, produkcyjny build, testy backendu i package guard dobrane do
  zmienionych kontraktow.

Wszystkie fixtures, snapshoty i przyklady domenowe sa fikcyjnym CRM.
