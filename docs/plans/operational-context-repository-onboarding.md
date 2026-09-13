# Prowadzone podlaczanie repozytorium do Operational Context

Status: completed

Source need: [Podlaczanie repozytorium do analiz Operational Context](../needs/operational-context-repository-onboarding.md)

Zakres krokow 1-3 zatwierdzony przez uzytkownika 2026-09-13.

## Potrzeba / dlaczego

Operator podlacza nowy projekt GitLab glownie po to, by stal sie zrodlem dla
analiz. Wolny opis nie gwarantuje, ze poda role repozytorium i powiazany system.
Obecny `CREATE_AREA` zawsze proponuje najpierw system, co jest niepoprawne dla
niewdrazanej biblioteki. Potrzebny jest krotki formularz faktow operatora i
rozne, jawnie ograniczone sciezki propozycji.

## Klasyfikacja, baseline i conformance delta

Zmiana jest L1 w feature Operational Context Assistance: dotyka requestu,
promptu, skilla, parsera, review i jego UI. Nie zmienia neutralnego modelu
katalogu, tools ani kierunkow zaleznosci.

Baseline: `CREATE_AREA` przyjmuje opis i opcjonalne zrodlo GitLab. Prompt
przekazuje opis jako `operator:description` i wybrane pliki jako osobne zrodla.
Parser dopuszcza tylko `CREATE system -> repository -> code-search-scope` i
rozpoznaje wybrane zrodlo po dowolnym ref innym niz opis. Kontekst katalogu
zawiera tylko listy ID, bez zawartosci istniejacych scope'ow. Review i zapis
dzialaja po jednej encji, z walidacja candidate, odlozonym preview referencji
do wczesniejszego CREATE oraz kontrola aktualnosci katalogu.
Baseline weryfikacji przed ta zmiana (2026-09-13): Angular 547/547, build
produkcyjny i Surefire 1460 testow bez failures/errors (1 skipped).

Delta: gdy operator wybiera projekt GitLab, jeden widoczny select pyta
`Jak uzywany jest ten projekt?` z wartosciami `Nie wiem`, `Nowy wdrazany
system`, `Niewdrazana biblioteka`, `Kod istniejacego systemu`. Dla nowego
systemu pojawia sie pole nazwy oraz opcjonalnie rozwijana nazwa uslugi w
logach. Dla biblioteki mozna wskazac znane systemy korzystajace z niej, a dla
kodu istniejacego systemu wybrac ten system. Opcje systemow pochodza z
biezacego katalogu; pusta lista nie blokuje opisu repozytorium. Opis pozostaje
glownym miejscem na cel, proces i ograniczenia.

Typowane `repositoryFacts` w requestcie przenosza te odpowiedzi do backendu:
`usage` ma wartosci `UNKNOWN`, `DEPLOYED_SYSTEM`, `SHARED_LIBRARY`,
`EXISTING_SYSTEM`; opcjonalne `systemName` i `runtimeServiceName` sa dozwolone
tylko dla nowego systemu, `systemIds` to 0-5 ID dla biblioteki albo dokladnie
jedno ID dla kodu istniejacego systemu. Przy `UNKNOWN` dodatkowe pola sa
puste. Odpowiedzi sa dozwolone tylko w `CREATE_AREA` z wybranym GitLabem.
Serwer waliduje ich zgodnosc z trybem, zrodlem i istniejacymi ID, ogranicza
dlugosc tekstu i buduje osobny, sanitizowany `operatorFacts` w materialach AI.
Nie dopisuje etykiet formularza do opisu. Fakt operatora ma wlasny dozwolony
source ref i nie udaje obserwacji kodu. `hasSelectedSource` musi sprawdzac
rzeczywiste zrodlo GitLab, aby nowe refy operatora nie dawaly prawa do
propozycji repozytorium bez kodu.

## Proponowane rozwiazanie

W `CREATE_AREA` bez GitLaba zachowujemy dotychczasowa sciezke tworzenia
obszaru. Z GitLabem i jawna rola:

| Rola | Dozwolona propozycja |
| --- | --- |
| Nie wiem | `repository` bez nowego systemu; pytanie o role i wykorzystanie. |
| Nowy wdrazany system | `system -> repository -> systemowy code-search-scope`, tylko gdy relacja i nazwa sa wystarczajaco potwierdzone. Bez nazwy AI pyta, zamiast wymyslac kanoniczny system. |
| Niewdrazana biblioteka | `repository` typu `shared-library`, bez nowego systemu; opcjonalnie aktualizacja scope'ow jawnie wskazanych konsumentow. |
| Kod istniejacego systemu | `repository` oraz aktualizacja jednego, jawnie wybranego systemowego scope; bez nowego systemu. |

Projekt wybrany z GitLaba nie potwierdza ownera, `frontend`, sygnalu runtime
ani relacji systemu. Nazwa w logach, jesli podana, jest jawnym faktem
operatora dla `system.matchSignals.exact.serviceNames`; nie jest wnioskowana z
repo. AI moze nie zaproponowac wszystkich encji, gdy brakuje evidence.

Alternatywy: sklejanie odpowiedzi w textarea po stronie Angulara ukrywa
provenance i utrudnia walidacje; pokazanie wszystkich pol modelu katalogu
przeczy celowi kreatora. Wybrane podejscie zostawia tylko jedna stala decyzje
i warunkowe pytania.

## Zakres i konsumenci

- Angular panel, model requestu i przekazanie opcji istniejacych systemow z
  obecnego `ContextHomePage`.
- Feature-owned API DTO, job, source refs, przygotowanie promptu, polski skill,
  parser draftu/scope oraz targeted context istniejacych systemowych scope'ow.
- Preview i warunkowy zapis sekwencji `CREATE repository -> UPDATE scope`.
- Kanoniczna architektura i instrukcje lokalne po wykonaniu; bez zmian w
  `opctx_*`, neutralnych integracjach i publicznym read API katalogu.

Konsumenci wyniku pozostaja ci sami: katalog UI, `opctx_*`, GitLab discovery,
Incident Analysis, Flow Explorer, Config Drift Viewer, Change Verification i
UI Explorer. Nowe powiazanie scope zmienia to, ktore repozytorium zobacza przy
danym systemie, dlatego testy musza objac resolver i co najmniej jeden
przeplyw od systemu do projektu GitLab.

## Non-goals

- Brak automatycznego zatwierdzania lub zbiorczego zapisu propozycji.
- Brak owner/team, wymuszonego subtype, plikow deploymentu, inwentarza klas
  i automatycznego skanowania innych repozytoriow.
- Brak tworzenia osobnego systemu dla biblioteki.

## Ograniczenia i ryzyka

- Stale ID systemu lub zmieniony scope musza zatrzymac propozycje przed
  zapisem; nie wolno dodac repozytorium do innego systemu.
- W systemowym scope zachowujemy obecny primary repo, search boundary i read
  order. Biblioteka dostaje role `library`/`shared-library` z nizszym
  priorytetem; brak jednoznacznego scope daje jawne pytanie/limit, nie zgadywanie.
- Gdy `repository` zostanie pominiete, pozniejszy UPDATE scope nie moze
  zapisac referencji do nieistniejacego repozytorium.
- Stale odpowiedzi o roli systemu i nazwa w logach sa twierdzeniami operatora,
  ale nadal podlegaja przegladowi i potwierdzeniu pol przed zapisem.
- Zmiana wspolnego kontraktu backend-frontend wymaga kolejno testow Angulara,
  builda produkcyjnego i `mvn -q -Pbackend-dev clean package`.

## Kryteria akceptacji

- Przy wybranym GitLab operator widzi tylko role i pasujace do niej pola;
  bez GitLaba dotychczasowe tworzenie obszaru nie zmienia zachowania.
- Biblioteka moze zakonczyc run poprawnym `repository` bez sztucznego
  `system`; wybrane, istniejace systemy moga otrzymac ja w swoich scope'ach.
- Nowy wdrazany system moze otrzymac nazwe i jawny sygnal z logow bez
  domyslania ownera ani frontendowej klasyfikacji.
- AI nie moze uzyc operatorowego source ref jako dowodu odczytu GitLaba ani
  aktualizowac scope systemu spoza zatwierdzonego wyboru.
- Review pokazuje zmiany i limity; kazda encja zapisuje sie osobno przez
  istniejaca warunkowa operacje maintenance.

## Kroki

- [x] Krok 1: Dodac kompaktowe pytania, warunkowy wybor istniejacych systemow
  i typowane `repositoryFacts`, ich walidacje oraz osobny material
  `operatorFacts`/source refs w promptcie.
  Rozroznic nowy system, biblioteke, kod istniejacego systemu i brak wiedzy;
  dopuscic repo-only bez sztucznego systemu, zachowujac stary scenariusz bez
  GitLaba. Powiazania z istniejacymi scope'ami czekaja na Krok 2 i sa jawnie
  ograniczone w review. Dowod: testy DTO, promptu, parsera, joba oraz
  panelu Angulara dla kazdej roli i pustego katalogu.
- [x] Krok 2: Wykorzystac wybrane systemy i dodac targeted context ich
  systemowych scope'ow. Dopuszczac tylko `CREATE repository` i nastepnie
  `UPDATE` wskazanych scope'ow, z preview zaleznym od wczesniejszego zapisu,
  ochrona primary repo i kontrola stale values. Dowod: integration test
  biblioteki dla dwoch konsumentow, pominiecie repo, brak scope, konflikt
  katalogu, resolver system -> kod i kontrakt FE.
- [x] Krok 3: Zaktualizowac kanoniczna architekture, lokalne instrukcje i
  scenariusze ekranu; wykonac regresje wszystkich konsumentow dotknietego
  kontraktu. Dowod: pelne testy Angulara, build produkcyjny,
  `mvn -q -Pbackend-dev clean package`, `PackageDependencyGuardTest` i
  `git diff --check`.

Weryfikacja wykonania (2026-09-13): testy Angulara 551/551 i build
produkcyjny przeszly; `mvn -q -Pbackend-dev clean package` wykonalo 1477 testow
bez failures/errors (1 skipped), w tym `PackageDependencyGuardTest` 4/4.
Test integracyjny zapisal biblioteke w dwoch systemowych scope'ach i potwierdzil
odmowe zaleznego UPDATE po pominieciu repozytorium. `git diff --check`
nie wykazal bledow whitespace.
