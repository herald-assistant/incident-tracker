# Asysta AI dla pelnej i spojnej rewizji Operational Context

Status: completed

Source need: [Asysta AI dla spojnych zmian Operational Context](../needs/operational-context-assisted-catalog-revision.md)

Zakres krokow 1-5 zostal jawnie zlecony przez uzytkownika 2026-09-13 po
przedstawieniu analizy ograniczen dotychczasowej asysty.

## Potrzeba / dlaczego

Obecny run dostaje tylko ID i do pieciu plikow root wybranego repo, nie ma
narzedzi eksploracji, pracuje w domyslnym tierze kontekstu i publikuje zmiany
po jednej encji. To nie wystarcza do poprawnej aktualizacji istniejacego
katalogu ani zmian wielu powiazanych wpisow.

## Klasyfikacja, baseline i conformance delta

Zmiana L3: dotyka modelu sesji Copilota, hidden scope narzedzi, materialu
promptu, zakresu draftu i wielodokumentowego zapisu. Baseline:
`CREATE_AREA` jest ograniczony do `system/repository/code-search-scope`,
`IMPROVE_ENTITY` i `RESOLVE_FINDING` do jednego UPDATE. Material AI ma listy
ID (maks. 100 na typ), wybrany target i do pieciu plikow z root repo. One-shot
Copilot nie ma tools. Operator podejmuje decyzje dla pojedynczych propozycji,
a maintenance zapisuje jeden YAML na raz. Poprzednia regresja: Angular 551/551,
backend 1477 testow bez failures/errors (1 skipped).

Delta: pelny, spojny snapshot dziewieciu aktywnych YAML z digestem; aktualne
reguly maintenance po porownaniu ze schematem; read-only GitLab tools
ograniczone do wskazanego projektu i przypietego commita; wymagany
`LONG_CONTEXT_REQUIRED`, jawny blad przy braku wsparcia; rozpoznawanie intencji
we wszystkich kanonicznych typach; draft wieloencyjny; walidacja calego
wynikowego katalogu i atomowa publikacja zatwierdzonego zestawu.

Konsumenci: ekran Operational Context, feature-owned API/job/prompt/skill,
platformowy Copilot session builder, neutralne GitLab tools i adapter,
maintenance storage/validation oraz wszyscy czytelnicy katalogu (`opctx_*`,
GitLab discovery i feature'y analityczne). Granice pakietow pozostaja zgodne
z `docs/architecture/package-dependencies.md`.

## Proponowane rozwiazanie

Feature pobiera jeden snapshot runtime storage i przekazuje AI wszystkie
dziewiec dokumentow jako dane z digestem. Obowiazujace wskazowki merytoryczne
sa osobnym zasobem skilla po audycie wobec schematu/validatora; skrypty i
raporty porzadkowe nie sa instrukcjami AI. Przekroczenie jawnego limitu
materialu blokuje run, zamiast skrywac brak danych.

Copilot moze uzyc tylko read-only list/search/read dla wybranego GitLaba,
przypietych do tego samego projektu i commita. Zawartosc jest limitowana na
pojedyncza odpowiedz narzedzia, ale AI moze kontynuowac eksploracje. Sesja
wymaga long context. Wybory modelu/reasoning z requestu maja pierwszenstwo
przed domyslnymi ustawieniami feature'a.

AI przygotowuje typowany zestaw CREATE/UPDATE dla kanonicznych bytow. Parser
nie przyznaje uprawnien do zapisu: kontroluje scope, zrodla i pola, a neutralny
maintenance buduje oraz waliduje caly wynikowy katalog. Operator wybiera
zmiany w review i zatwierdza zestaw wzgledem jednego digesta; wszystkie
dotkniete YAML sa publikowane albo zadne. Bledy pokazuje sie per pole/encja.

Alternatywy: ogromny prompt zawierajacy cale repo skaluje sie z kodem i nie
pozwala AI czytac celowo; zapis kolejnych plikow po jednym pozostawia stan
czesciowy. Wybrany wariant daje pelna wiedze katalogowa i doczyt kodu na
zadanie oraz transakcje dla zatwierdzonego zestawu.

## Zakres

- Context snapshot, prompt i skill assistance.
- Feature-bound GitLab tools, platformowa sesja i konfiguracja modelu.
- Uogolnienie draftu i preview na wszystkie kanoniczne typy katalogu.
- Wielodokumentowy warunkowy maintenance i API/UI zatwierdzania zestawu.
- Testy, aktualizacja architektury, lokalnych instrukcji i planu.

## Non-goals

- Dostep AI do innych repozytoriow lub systemow zewnetrznych niz wskazane.
- Mutacyjne tools dla AI, automatyczny zapis, migracja katalogu do DB.
- Utrzymywanie starego sposobu zapisu jako rownoleglego kontraktu po
  wdrozeniu nowego review.

## Ograniczenia i ryzyka

- Pelny katalog moze przekroczyc mozliwosci nawet dlugiego kontekstu;
  fail-closed z jawnym komunikatem, bez cichego obciecia.
- Dane z YAML i GitLaba pozostaja niezaufane, sanitizowane; model nie moze
  wykonac instrukcji znalezionych w zrodlach.
- Zmiana publikacji jest L3. Rollback wdrozenia oznacza przywrocenie
  poprzedniego JAR-a; katalog pozostaje w tym samym schemacie. Przed zapisem
  tworzymy kopie dotknietych YAML w ramach procedury atomowej, bez osobnego
  historycznego magazynu.
- Brak wsparcia long context wybranego modelu blokuje AI run zamiast
  uruchamiac krotszy tier.

## Kryteria akceptacji

- AI widzi wszystkie aktywne dokumenty katalogu z jednego digesta oraz
  zweryfikowane, aktualne reguly merytoryczne.
- AI potrafi doczytac pliki z wybranego projektu/ref przez read-only tools,
  bez dostepu do innych projektow i bez zapisu.
- Prompt/skill, parser i preview wspieraja aktualizacje terminu, integracji
  A-B i powiazanych wpisow w kilku dokumentach.
- Operator widzi diff przed zapisem; stale dane, niepoprawne referencje i
  czesciowy blad publikacji nie pozostawiaja polowicznego katalogu.
- Regresja FE, backendu i grafu zaleznosci przechodzi.

## Kroki

- [x] Krok 1: Ustalic baseline i audyt reguł maintenance wobec schematu,
  podlaczyc pelny snapshot aktywnego katalogu z digestem do materialu AI.
  Dowod: test snapshotu 9 YAML, brak cichego obciecia i zgodnosc reguł.
- [x] Krok 2: Wlaczyc wymagany long context, domyslne opcje AI oraz bezpieczne
  narzedzia GitLab przypiete do projektu i commita. Dowod: test allowlisty,
  odmowy obcego projektu i braku wsparcia tieru, read dowolnego pliku.
- [x] Krok 3: Rozszerzyc rozpoznawanie intencji, prompt, skill, parser i
  preview na spójne zmiany wszystkich kanonicznych typow. Dowod: scenariusze
  terminu, integracji A-B oraz wybranych endpointow z konkretnymi source refs.
- [x] Krok 4: Dodac warunkowe zatwierdzanie calego zestawu, pelna walidacje
  wynikowego katalogu i atomowa publikacje dotknietych YAML; dostosowac
  API/UI review. Dowod: konflikt digesta, niepoprawna referencja, blad
  zapisu i brak czesciowych danych po odmowie.
- [x] Krok 5: Zaktualizowac kanoniczna dokumentacje i lokalne instrukcje,
  wykonac testy FE, build produkcyjny i `mvn -q -Pbackend-dev clean package`,
  `PackageDependencyGuardTest` oraz `git diff --check`.

## Wynik i weryfikacja

Wykonano 2026-09-13. Angular: 552 testy bez bledu i produkcyjny build.
Backend: `mvn -q -Pbackend-dev clean package`, 1510 testow bez failures/errors
(jeden skipped); osobny `PackageDependencyGuardTest` i `git diff --check`
przeszly. Powstaly JAR zawiera nowy skill i 11 instrukcji maintenance.

Read-only GitLab list/search dziala porcjami i zwraca kursor, gdy nie objal
calego repozytorium. Odczyt jest ograniczony do bezpiecznych sciezek i tresci
oraz 256 KiB na plik; sesja ma twardy budzet wywolan. Dla istniejacego systemu
brak jednoznacznego code-search-scope blokuje podlaczenie repo przed AI, a
operator nie moze zapisac repo bez aktualizacji wymaganego scope'u w tym samym
batchu. Publikacja daje spojny snapshot czytelnikom tej aplikacji i odtwarza
poprzedni katalog po przerwaniu. Bezposredni czytelnik plikow YAML w innym
procesie moze zobaczyc chwile przejsciowa podczas wymiany plikow.
