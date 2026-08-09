# Flow Explorer - tylko Deep Discovery

Status: done

Source need: brak osobnego dokumentu w `docs/needs/`; zakres zostal jawnie
zlecony i zatwierdzony przez uzytkownika 2026-08-09.

## Potrzeba / dlaczego

`TEST_SCENARIOS` i `RISK_DETECTION` nie sa obecnie gotowe produktowo. Flow
Explorer ma tymczasowo wykonywac tylko `DEEP_DISCOVERY`, a dwie pozostale
opcje maja pozostac widoczne w UI jako zapowiedz oznaczona `SOON`, bez
mozliwosci uruchomienia.

Zmiana nie utrzymuje kompatybilnosci archiwalnych wynikow dla wycofanych goals.
Eksport, lokalny snapshot albo wynik z `TEST_SCENARIOS` lub `RISK_DETECTION`
ma byc traktowany tak, jakby analiza nie byla obslugiwana: nie jest importowany,
odtwarzany ani kontynuowany. Nie usuwamy fizycznie plikow uzytkownika.

## Baseline

- Flow Explorer jest endpoint-first i uruchamia job przez
  `POST /api/flow-explorer/jobs`.
- Publiczny kontrakt backendu i frontendu dopuszcza trzy goals:
  `DEEP_DISCOVERY`, `TEST_SCENARIOS` i `RISK_DETECTION`; domyslny jest
  `DEEP_DISCOVERY`.
- Goal wybiera jedna soczewke runtime skill nad tym samym kontraktem raportu;
  tryby sekcji sa niezaleznym wymiarem.
- Runtime ma trzy goal skills oraz galezie promptu i wyboru skilli dla kazdego
  goal.
- FE pozwala wybrac wszystkie trzy opcje. Istniejacy komponent wyboru ma juz
  mechanike `disabled`, ale nie pokazuje pill `SOON`.
- Import/export i lokalne odtwarzanie akceptuja snapshoty wszystkich trzech
  goals.
- Context, sekcje raportu, tools, hidden scope, job lifecycle i follow-up chat
  sa wspolne i nie wymagaja zmiany dla `DEEP_DISCOVERY`.
- Baseline 2026-08-09: celowane testy backendu `FlowExplorer*` oraz dwa
  celowane pliki testow FE (strona i import/export), lacznie 34 testy FE,
  przechodza.

## Conformance delta

- Backendowy enum, request, snapshot, wynik, parser i eksport dopuszczaja tylko
  `DEEP_DISCOVERY`; stare wartosci sa odrzucane zamiast migrowane.
- Prompt, response contract i selekcja runtime skills zawieraja tylko
  `flow-explorer-deep-discovery`; skille dla scenariuszy i ryzyk zostaja
  usuniete.
- FE transport/result model zawiera tylko `DEEP_DISCOVERY`. Dwie przyszle
  wartosci istnieja wyłącznie jako nieaktywne opcje prezentacyjne z pill
  `SOON` i nie moga trafic do requestu.
- Import i odtwarzanie lokalnego runu odrzucaja payload z wycofanym goal;
  istniejace eksporty `DEEP_DISCOVERY` pozostaja obslugiwane.
- Nie zmieniamy wersji eksportu, bo jego aktualny kontrakt i dane
  `DEEP_DISCOVERY` pozostaja poprawne; walidacja goal staje sie wezsza.
- Context/evidence, section modes, tools/policy, report structure, job
  lifecycle, polling i follow-up dla `DEEP_DISCOVERY` pozostaja bez zmian.
- Nie powstaja nowe zaleznosci ani importy miedzy feature'ami.

## Konsumenci

- backend: enum i DTO joba/wyniku/eksportu, Jackson request parsing, AI response
  parser, artifact response contract, prompt preparation, runtime skill
  selection i session preparation;
- runtime resources: orchestrator, goal skill, report writer oraz katalog
  selected skills;
- frontend: modele request/result/snapshot, wybor goal, start joba,
  import/export i local history restore;
- testy i dokumentacja: parser, prompt, artifacts, runtime skills, job API,
  strona FE, import/export, lokalne instrukcje i backlog smoke testow.

## Ryzyka i zabezpieczenia

- Najwieksze ryzyko to przypadkowe naruszenie `DEEP_DISCOVERY`; zabezpieczaja
  je testy promptu, parsera, skilli, joba, importu, UI i pelna macierz buildow.
- Stary lokalny wpis moze nadal istniec w storage/workspace, ale jego payload
  nie zostanie odtworzony. To zamierzony brak kompatybilnosci, nie migracja.
- Przyszle opcje UI nie moga rozszerzac typowanego kontraktu API; dostaja
  osobny UI-only typ i guard przed ustawieniem aktywnego goal.
- Usuniecie katalogow skilli musi pozostawic selected root z kompletem skilli
  wymaganych przez `DEEP_DISCOVERY` i follow-up.

## Non-goals

- fizyczne usuwanie eksportow, lokalnych runow lub danych uzytkownika;
- zmiana raportu, section modes, context collection, tools lub follow-up chat;
- implementacja albo poprawa `TEST_SCENARIOS` i `RISK_DETECTION`;
- zmiana entrypointu Flow Explorera poza obecny endpoint-first flow.

## Kryteria akceptacji

- Nowy job i odpowiedz AI obsluguja tylko `DEEP_DISCOVERY`.
- Request i import z wycofanym goal sa odrzucane.
- FE pokazuje `Test scenarios` oraz `Risk detection` jako disabled z pill
  `SOON`, a aktywnym goal pozostaje `Deep Discovery`.
- Backend nie zawiera implementacji ani runtime skills wycofanych goals.
- Istniejacy run/import `DEEP_DISCOVERY`, raport i follow-up nadal dzialaja.
- Celowane i pelne testy backendu/frontendu, produkcyjny build Angulara,
  package aplikacji oraz architecture guard przechodza.

## Kroki

- [x] Krok 1: zebrano baseline, conformance delta, konsumentow, ryzyka i
  macierz testow; celowane baseline'y backendu i FE przechodza.
- [x] Krok 2: zwezic backendowy kontrakt i runtime do `DEEP_DISCOVERY`, usunac
  dwa runtime skills oraz dodac test odrzucenia starego goal.
- [x] Krok 3: zwezic kontrakt FE, pokazac dwie disabled opcje z pill `SOON` i
  odrzucac stare payloady w imporcie/local restore.
- [x] Krok 4: zaktualizowac runtime resources, lokalne instrukcje i backlog
  smoke testu do aktualnego zakresu.
- [x] Krok 5: wykonac audyt pozostalych referencji, celowane testy, pelne testy,
  build produkcyjny, package i `git diff --check`; uzupelnic dowody i zamknac
  plan.

## Macierz testow

- backend contract/API: start `DEEP_DISCOVERY`, domyslny goal i HTTP 400 dla
  `TEST_SCENARIOS`/`RISK_DETECTION`;
- AI contract: prompt, response artifact i parser przyjmuja tylko Deep;
- runtime: selected skills zawieraja Deep i nie zawieraja usunietych katalogow;
- FE: obie przyszle opcje sa disabled, maja pill/a11y i nie zmieniaja goal;
- import/local history: Deep round-trip przechodzi, stary goal jest odrzucany;
- regresja: wszystkie testy Flow Explorera, pelne Maven/Angular, produkcyjny
  build, package i package dependency guard.

## Dowody wykonania

- Celowane testy backendu `FlowExplorer*`, w tym request binding, parser,
  runtime skills i odrzucenie kontynuacji starego local runu, przeszly.
- Celowane testy strony FE i import/export: 2 pliki / 35 testow przeszlo.
- `CopilotRuntimeSkillFrontmatterTest` i `PackageDependencyGuardTest` przeszly.
- `mvn -q clean test` przeszedl.
- Pelna regresja FE: 37 plikow / 231 testow przeszla.
- `npm run build` przebudowal produkcyjny bundle w
  `src/main/resources/static`; stary bundle zostal zastapiony.
- `mvn -q -DskipTests package` przeszedl z aktualnym bundle'em.
- Audyt `rg` potwierdzil brak nazw usunietych skilli w kodzie produkcyjnym i
  bundle'u. Tokens wycofanych goals zostaly tylko w UI-only placeholderach,
  testach jawnego odrzucenia i dokumentacji decyzji.
- `git diff --check` nie wykazal bledow whitespace ani markerow konfliktu.
