# Config Drift Verification - Business Need

## Cel dokumentu

Ten dokument opisuje potrzebe weryfikacji konfiguracji przygotowanej do
wdrozenia pomiedzy srodowiskami. Nie wybiera implementacji ani technologii
analizy.

## Kontekst biznesowy

Konfiguracja uruchomieniowa systemu jest utrzymywana w dedykowanym
repozytorium GitLaba, innym niz repozytoria kodu uzywane do budowania paczek.
Kazde srodowisko ma osobny branch. Domyslna lista branchy testowych to:

- `dev`,
- `dev2`,
- `uat`,
- `uat2`.

Repozytorium ma wspolny plik `global.var` w katalogu glownym. Kazdy komponent
wdrozeniowy ma wlasny katalog, np. `backend/`, z:

- `application.yml.kv` albo `application.yaml.kv`,
- `local.var`.

Plik aplikacyjny moze zawierac wiele dokumentow YAML oraz odwolania do
wartosci z plikow `.var`. Pliki `.var` zawieraja zagniezdzone wartosci,
adresy srodowiskowe, flagi i referencje do wartosci podstawianych w runtime.
Prawdziwe sekrety sa utrzymywane w Vault, a nie w porownywanym repozytorium.

Podczas recznego przenoszenia konfiguracji administrator moze:

- pominac klucz albo caly fragment konfiguracji,
- pozostawic wartosc z niewlasciwego srodowiska,
- skopiowac wartosc do niewlasciwego komponentu lub brancha,
- zmienic typ albo sposob odwolania do zmiennej,
- pozostawic odwolanie do nieistniejacej zmiennej,
- nieswiadomie wprowadzic konflikt pomiedzy konfiguracja globalna i lokalna,
- nie zauwazyc, ze plik jest brakujacy, niepoprawny albo niekompletny.

Zwykly tekstowy diff pokazuje zmienione linie, ale nie odpowiada, ktore zmiany
sa istotne dla wybranego komponentu, jaki jest ich efekt po rozwiazaniu
odwolan i ktore roznice wygladaja jak mozliwa pomylka.

## Potrzeba uzytkownika

Administrator lub osoba przygotowujaca wdrozenie chce przed wdrozeniem
porownac konfiguracje wybranego komponentu pomiedzy dwoma branchami
srodowiskowymi i otrzymac:

1. kompletne, czytelne podsumowanie roznic,
2. wskazanie zmian wymagajacych uwagi,
3. ocene, czy widoczne sa symptomy przeoczenia albo pomylki,
4. jawne ograniczenia analizy, gdy czegos nie udalo sie odczytac lub
   jednoznacznie zinterpretowac,
5. material pozwalajacy podjac ludzka decyzje: kontynuowac wdrozenie,
   wyjasnic roznice albo poprawic konfiguracje.

Porownanie ma byc read-only. Nie zmienia branchy, plikow ani procesu
wdrozenia.

## Minimalny input

Uzytkownik wskazuje:

- tryb analizy `BASIC` albo `DEEP`,
- repozytorium konfiguracji, jezeli dostepnych jest kilka,
- jeden lub wiele komponentow wdrozeniowych reprezentowanych w Operational
  Context jako `internal-system`; formularz inicjalnie zaznacza wszystkie
  dostepne komponenty,
- branch bazowy,
- branch porownywany,
- opcjonalny ref kodu dla trybu `DEEP`, jezeli uzytkownik chce analizowac
  konkretna wersje zamiast jawnie oznaczonego domyslnego refu repozytorium.

Oba branche musza byc roznymi branchami dostepnymi na skonfigurowanej liscie.
Potrzeba obejmuje porownania miedzy dowolnymi dwoma obslugiwanymi branchami;
system nie powinien zgadywac kierunku promocji tylko na podstawie nazwy.

Lista branchy prezentowana operatorowi ma byc konfigurowalna po stronie
aplikacji. Dozwolone nazwy naleza do rodzin `dev`, `test`, `uat` albo `zt` i
moga miec wielocyfrowy sufiks, np. `dev12`, `test3`, `uat20`, `zt7`. Brak
sufiksu pozostaje dozwolony dla domyslnych `dev` i `uat`.

`BASIC` nie wymaga tokena Copilota ani preferencji modelu. `model` i
`reasoningEffort` sa istotne tylko dla `DEEP` i nie powinny byc pokazywane ani
wysylane przez UI w trybie `BASIC`.

## Tryby analizy

### `BASIC`

Tryb szybki koncentruje sie na konfiguracji:

- porownuje wymagane pliki,
- buduje structural i effective diff,
- pokazuje deterministic findings,
- pokazuje znormalizowany `configurationDiff` per plik, z dokladnymi
  wartosciami source/target, struktura wielodokumentowego YAML i `.var`,
  stanami `PRESENT`/`ABSENT` oraz czytelnymi markerami zmian,
- uzywa Operational Context tylko do wyboru kanonicznego `internal-system` i
  jego katalogu konfiguracji; nie wykorzystuje katalogu do interpretacji
  roznic ani nie czyta kodu systemu,
- nie uruchamia AI, nie przygotowuje promptu, nie sprawdza tokena Copilota i
  nie generuje AI usage/cost,
- jawnie informuje, ze nie potwierdza funkcjonalnego znaczenia kluczy ani
  ownershipu.

Ten tryb ma byc szybki, w pelni deterministyczny i dostepny bez konfiguracji
AI, gdy dostepne sa repozytorium konfiguracji oraz jednoznaczny
`internal-system` z katalogiem komponentu.

### `DEEP`

Tryb gleboki zaczyna od identycznego wyniku jak `BASIC`, a nastepnie:

1. przyjmuje wybrany komponent jako kanoniczny `internal-system` z
   Operational Context,
2. laczy roznice konfiguracyjne z tym systemem i identyfikuje inne systemy,
   integracje, procesy lub bounded contexty, ktorych moze dotyczyc roznica,
3. pobiera code-search scope kazdego zidentyfikowanego wewnetrznego systemu,
4. wyszukuje w kodzie miejsca uzycia zmienionych kluczy, prefixow,
   `@ConfigurationProperties`, `@Value`, flag, endpointow i innych
   potwierdzonych sygnalow,
5. interpretuje, jaka funkcjonalnosc albo zachowanie runtime moze ulec
   zmianie,
6. rozwiazuje ownership na podstawie systemu albo bounded contextu i wskazuje,
   do kogo zwrocic sie w przypadku niejasnosci,
7. buduje zanonimizowany manifest konfiguracji i uruchamia AI, ktore dodaje
   komentarze o ryzyku, rekomendacje i functional impact powiazane przez
   `differenceId`/`findingId`.

Komponent wdrozeniowy nie jest osobnym bytem obok systemu. Jego katalog
konfiguracji jest runtime/deployment signalem kanonicznego `internal-system`.
`DEEP` nie moze tworzyc drugiej mapy ownershipu ani zgadywac systemu na
podstawie samej nazwy katalogu. Kazdy wewnetrzny system analizowany w tym
trybie musi miec code-search scope. Brak scope'u, dostepu do repozytorium,
jednoznacznego dopasowania katalogu do `systemId` albo refu kodu jest widoczna
luka. Ownership pochodzi z jawnych danych systemu lub bounded contextu, nie z
nazwy repozytorium.

Repozytorium konfiguracji i repozytoria kodu moga znajdowac sie na roznych
instancjach GitLaba. Wynik musi wskazac ref kodu faktycznie wykorzystany dla
kazdego repozytorium oraz ostrzec, gdy nie ma dowodu, ze jest to wersja
wdrozona na porownywanym srodowisku.

## Oczekiwany rezultat

Wynik powinien rozdzielac fakty od interpretacji:

1. **wynik deterministyczny** - zawsze obecne, kompletne fakty o odczycie,
   strukturze, roznicach, odwolaniach i wykrytych regulami anomaliach,
2. **interpretacja AI w `DEEP`** - komentarze o ryzyku, mozliwy scenariusz
   pomylki, functional impact, rekomendacje i ownership/handoff.

`BASIC` konczy sie na wyniku deterministycznym i nie pokazuje pustych sekcji
AI. `DEEP` zachowuje ten sam czytelny diff i dodaje do niego interpretacje
powiazana precyzyjnymi referencjami. Interpretacja AI nie moze zastapic,
ukryc ani zmienic faktow deterministycznych.

Wynik powinien zawierac:

### Zakres i pokrycie

- repozytorium, komponent oraz porownywane branche,
- liste sprawdzonych plikow po obu stronach,
- identyfikatory wersji plikow lub commitow, jezeli sa dostepne,
- brakujace, nieczytelne, uciete albo niepoprawne pliki,
- jawne ograniczenia widocznosci.

### Podsumowanie roznic

- dodane, usuniete i zmienione klucze,
- zmiany typow wartosci,
- roznice w strukturze dokumentow YAML,
- roznice w `global.var` istotne dla wybranego komponentu,
- pozostale roznice globalne pokazane osobno jako szerszy kontekst,
- zmiany w lancuchach odwolan pomiedzy plikiem aplikacyjnym a plikami `.var`,
- informacja, czy wartosc efektywna zmienila sie mimo braku zmiany w pliku
  aplikacyjnym albo pozostala taka sama mimo zmiany zrodla.

### Podejrzane sytuacje

Kazde ostrzezenie powinno miec:

- kategorie i poziom istotnosci,
- wskazanie brancha, pliku i sciezki klucza,
- rozroznienie faktu i wyprowadzonego deterministycznie wniosku,
- krotkie uzasadnienie reguly,
- jawna informacje, gdy potrzebna jest decyzja czlowieka.

W `DEEP` ostrzezenie moze dodatkowo otrzymac jawnie oznaczona interpretacje
AI, poziom pewnosci i sugerowana czynnosc weryfikacyjna.

Analiza powinna umiec wskazac co najmniej:

- brak pliku lub klucza po jednej stronie,
- zmiane typu,
- niepoprawne albo nierozwiazane odwolanie,
- konflikt albo niejednoznacznosc definicji,
- marker innego obslugiwanego srodowiska w wartosci brancha porownywanego,
- podejrzanie niezmieniona wartosc o charakterze srodowiskowym,
- nowa albo zmieniona referencja do wartosci podstawianej w runtime,
- brak mozliwosci przeprowadzenia pelnej analizy.

Nie kazda roznica jest bledem. Wynik ma odroznic oczekiwana zmiane
srodowiskowa od anomalii wymagajacej sprawdzenia i nie moze przedstawic
heurystyki jako potwierdzonego bledu.

### Znaczenie funkcjonalne w trybie `DEEP`

Wynik gleboki powinien dodatkowo pokazac:

- wybrany kanoniczny `internal-system` oraz runtime/deployment signal laczacy
  go z katalogiem konfiguracji,
- pozostale potencjalnie dotkniete systemy oraz powiazane integracje,
  procesy i bounded contexty,
- konkretne miejsca w kodzie, ktore odczytuja zmieniona konfiguracje,
- opis, jakie zachowanie, funkcjonalnosc, polaczenie, scheduler, kolejka,
  feature flag albo sciezka procesu moze sie zmienic,
- confidence i rozroznienie potwierdzonego code grounding od hipotezy,
- uzyty code-search scope, repozytorium, ref, plik oraz symbol/metode,
- resolved ownership, powod handoffu i sugerowana osobe lub zespol do
  konsultacji,
- luki, gdy kod, ownership albo relacja funkcjonalna nie zostaly
  potwierdzone.

Informacja „dotyczy funkcjonalnosci X” musi miec oparcie w Operational Context
lub kodzie. Samo podobienstwo nazwy klucza nie jest wystarczajacym dowodem.

### Ocena gotowosci

Widok powinien pokazac osobno:

- stan deterministic verification,
- dla `DEEP`: stan wykonania i wniosek AI, laczny stan wymagajacy ludzkiej
  decyzji oraz wyrazny sygnal, gdy AI i reguly deterministyczne prowadza do
  roznych wnioskow.

W `BASIC` koncowy status wynika bezposrednio z deterministic verification.
Brak AI jest zamierzonym kontraktem trybu i nie moze powodowac statusu
`INCOMPLETE`, bledu ani visibility limitu.

Laczny wynik koncowy powinien miec jeden z czytelnych stanow:

- `NO_BLOCKING_ANOMALIES` - nie znaleziono blokujacej anomalii przy pelnym
  zadeklarowanym pokryciu; nie jest to gwarancja poprawnosci wdrozenia,
- `REVIEW_REQUIRED` - sa roznice albo niejednoznacznosci wymagajace decyzji,
- `LIKELY_CONFIGURATION_ERROR` - istnieje mocny, wskazany dowod mozliwej
  pomylki,
- `INCOMPLETE` - brak danych lub blad odczytu/parsing uniemozliwia wiarygodna
  ocene.

Stan nie zastepuje akceptacji administratora i nie uruchamia wdrozenia.

## Szybka weryfikacja w UI

Pierwszy widok wyniku powinien pozwolic w kilkadziesiat sekund odpowiedziec:

- czy wszystkie wymagane pliki zostaly odczytane i poprawnie sparsowane,
- ile jest zmian oraz ile z nich wymaga uwagi,
- jaki jest deterministic status,
- ktore pliki i galezie konfiguracji trzeba zweryfikowac przed wdrozeniem.

W trybie `DEEP` pierwszy widok powinien dodatkowo pokazac:

- jaka jest opinia AI i jej confidence,
- czy AI wskazuje te same miejsca co reguly, czy dodaje nowa hipoteze,
- jakie sa najwazniejsze rekomendacje przed wdrozeniem,
- dotkniete systemy i funkcjonalnosci,
- stopien code grounding,
- ownera lub jawny brak rozstrzygnietego ownershipu,
- refy kodu uzyte do analizy.

Szczegoly powinny byc dostepne bez utraty kontekstu:

- wynik wielokomponentowy jest dzielony na zakladki: jedna zakladka na
  `internal-system`, z jego statusem, pokryciem i lista porownanych plikow,
- zmiana aktywnej zakladki nie miesza findingow, identyfikatorow roznic ani
  ograniczen widocznosci pomiedzy komponentami,
- podstawowy widok deterministyczny grupowany per plik i dokument, renderowany
  w zagniezdzonej postaci przypominajacej znormalizowany format zrodlowy
  (`YAML` dla `application.y[a]ml.kv`, zapis blokowy dla plikow `.var`),
- domyslny widok tylko zmienionych galezi z zachowaniem ich rodzicow oraz
  przelacznik pelnego pliku; niezmienione galezie moga byc zwijane, aby duza
  konfiguracja nie wymuszala przegladania tabeli po jednym polu,
- czerwone oznaczenie dla `ADDED`, `REMOVED` i potencjalnie lamliwego
  `TYPE_CHANGED`, zolte dla `CHANGED` i `EFFECTIVE_CHANGED`, bez oznaczenia dla
  `UNCHANGED`,
- tylko dla `DEEP`: osobna sekcja interpretacji AI, krotka adnotacja przy
  powiazanej linii konfiguracji oraz linki z pelnej obserwacji do konkretnych
  pozycji diffu i findings; powiazanie musi korzystac ze stabilnego
  `differenceId`/`findingId`, a nie z dopasowania tekstu albo sciezki,
- czytelne oznaczenie `FACT` i `DERIVED` oraz - tylko dla `DEEP` -
  `AI INTERPRETATION`,
- rzeczywiste przed/po dla wszystkich wartosci dostepnych operatorowi przez
  jego lokalnie skonfigurowany token GitLaba,
- stale widoczny scope, coverage i visibility limits.

Widok podobny do pliku jest projekcja operatorska, a nie nowym zrodlem faktow.
Backend podstawia do niego rzeczywiste wartosci deterministycznie, poza
odpowiedzia AI. Uprawnienie operatora do ich zobaczenia wynika z lokalnego,
jednouzytkownikowego uruchomienia i jego wlasnego tokenu GitLaba, ktory daje
mu dostep do tych samych plikow zrodlowych. AI dostarcza tylko komentarz
powiazany przez identyfikator w `DEEP` i nie moze zmienic rodzaju zmiany,
wartosci source/target ani struktury pliku. Nie wykonujemy globalnego
podmieniania pseudonimow w swobodnym tekscie AI.

Tool Workbench musi pokazywac ten podzial wprost:

- dla `BASIC`: operatorski `configurationDiff` oraz jawny stan
  `AI input not generated`,
- dla `DEEP`: ten sam `configurationDiff`, zanonimizowany model i artefakty
  przekazywane AI oraz mapowanie przez stabilne identyfikatory.

Workbench sluzy do inspekcji pobrania, parsowania, mapowania i granicy
anonimizacji. Nie moze sugerowac, ze `BASIC` przygotowuje albo wysyla prompt.

## Bezpieczenstwo i poufnosc

Repozytorium konfiguracji zawiera wartosci i referencje, do ktorych operator
ma dostep swoim tokenem GitLaba. Prawdziwe sekrety sa przechowywane w Vault i
podstawiane w runtime; feature nie laczy sie z Vault ani nie odczytuje ich
tresci. Feature obsluguje wylacznie skonfigurowane branche testowe; zakres
produkcyjny nie jest obslugiwany.

Wymagania:

- dane dostepowe do GitLaba nie sa inputem uzytkownika,
- token GitLaba pozostaje backend-only i nie jest wartoscia konfiguracji
  pokazywana w diffie,
- wynik operatorski pokazuje bez anonimizacji dokladne wartosci i referencje
  odczytane z plikow source/target,
- ten sam czytelny wynik moze byc zachowany w lokalnej historii oraz
  imporcie/eksporcie; nie przechowuje byte-identical plikow ani komentarzy,
- brak po stronie source albo target jest pokazywany operatorowi jako `BRAK`,
  a nie jako pusta wartosc,
- `BASIC` nie buduje promptu ani wejscia AI i nie wymaga dostepu do Copilota,
- zanonimizowany manifest budowany dla `DEEP` zachowuje granice dokumentow,
  profile, zagniezdzenie, nazwy statycznych parametrow, typy, ksztalt
  kolekcji, zrodla definicji, relacje odwołan i stan `UNCHANGED`, `CHANGED`,
  `ADDED` albo `REMOVED`,
- wartosci niewrazliwe moga otrzymac nieodwracalny pseudonim stabilny tylko
  wewnatrz jednego uruchomienia; AI nie dostaje surowego hasha ani stalego
  identyfikatora umozliwiajacego korelacje miedzy analizami,
- wartosci wrazliwe nie dostaja korelacji miedzy roznymi kluczami; widoczna
  pozostaje tylko relacja wartosci tego samego parametru pomiedzy branchami,
- dynamiczne klucze map wygladajace jak identyfikatory lub dane otrzymuja
  pseudonimy, podczas gdy statyczne sciezki konfiguracji pozostaja czytelne
  dla powiazania z kodem,
- surowe komentarze YAML nie sa przekazywane do AI, poniewaz moga zawierac
  dane operacyjne albo sekrety,
- surowe pliki nie sa trwale zapisywane jako rezultat analizy,
- eksport i historia zawieraja znormalizowany wynik per plik, model roznic,
  findings oraz - tylko dla `DEEP` - interpretacje AI,
- analiza nie zapisuje niczego w repozytorium konfiguracji.

## Uzytkownicy i moment pracy

Glowni uzytkownicy:

- administrator srodowiska,
- release/deployment engineer,
- developer przygotowujacy konfiguracje,
- osoba wykonujaca techniczny review wdrozenia.

Typowy moment uzycia:

1. po przygotowaniu zmian na branchu docelowym,
2. przed uruchomieniem wdrozenia,
3. po wykryciu podejrzenia rozjazdu miedzy srodowiskami,
4. przed przekazaniem konfiguracji do review innej osobie.

## Wartosc

- mniejsze ryzyko wdrozenia z niepelna albo pomylona konfiguracja,
- krotszy czas recznego przegladania duzych plikow,
- powtarzalny zakres kontroli dla kazdego komponentu,
- audytowalne uzasadnienie decyzji przed wdrozeniem,
- wczesne wykrywanie problemow, zanim pojawia sie jako blad runtime.

## Mierniki sukcesu

- kazdy wynik pokazuje pokrycie wszystkich wymaganych plikow po obu stronach,
- kazda roznica i kazde ostrzezenie ma precyzyjna referencje do pliku i
  sciezki klucza,
- zestaw testowych przypadkow obejmuje brak klucza, zmiane typu,
  nierozwiazane odwolanie, marker zlego srodowiska, podejrzanie skopiowana
  wartosc, brak pliku i blad skladni,
- UI, historia i eksport pokazuja dokladne wartosci source/target odczytane z
  plikow, bez maskowania przed operatorem,
- `BASIC` dziala bez tokena Copilota, promptu, sesji AI, AI usage i sekcji AI,
- zadna rzeczywista wartosc konfiguracji nie trafia do promptu ani odpowiedzi
  AI; w `DEEP` AI pracuje wylacznie na zanonimizowanym modelu,
- uzytkownik moze przejrzec roznice per plik w zagniezdzonej postaci bez
  tabeli zawierajacej osobny wiersz dla kazdego pola,
- dla `DEEP` kazda funkcjonalna interpretacja ma referencje do Operational
  Context lub konkretnego miejsca w kodzie,
- dla `DEEP` kazdy wewnetrzny system analizowany przez kod ma poprawny
  code-search scope albo jawny visibility gap,
- uzytkownik potrafi na podstawie wyniku wskazac, czy kontynuuje wdrozenie,
  poprawia konfiguracje czy eskaluje roznice do wyjasnienia,
- po zebraniu danych z realnych uzyc mozna zmierzyc odsetek trafnych
  ostrzezen i skalibrowac heurystyki bez zmiany faktograficznego diffu.

## Ograniczenia i ryzyka

- Dostarczone przyklady przedstawiaja pojedynczy `application.yml.kv` i
  `global.var`; nie zawieraja pary branchy ani przykladu `local.var`.
  Potwierdzaja ksztalt danych, ale nie definiuja jeszcze katalogu normalnych
  roznic srodowiskowych.
- Skladnia `.var` moze byc specyficzna dla istniejacego procesu
  konfiguracyjnego. Niejednoznaczna konstrukcja musi byc pokazana jako luka,
  a nie interpretowana przez zgadywanie.
- Analiza repozytorium potwierdza stan deklaratywnej konfiguracji na branchach,
  nie stan faktycznie uruchomiony na srodowisku.
- Analiza kodu opisuje wersje wskazana przez uzytkownika albo jawnie pokazany
  domyslny ref repozytorium. Bez deployment metadata nie potwierdza, ze ten
  kod jest aktualnie uruchomiony na srodowisku.
- Dostepnosc `DEEP` zalezy od jakosci Operational Context, code-search scopes,
  ownershipu i dostepu do osobnej instancji GitLaba z kodem.
- Brak ostrzezenia nie jest gwarancja poprawnosci wszystkich zaleznosci
  zewnetrznych ani sekretow.
- Heurystyki o markerach srodowiskowych moga dawac falszywe alarmy, dlatego
  wymagaja uzasadnienia i ludzkiej decyzji.

## Non-goals

- automatyczne wdrozenie albo zablokowanie wdrozenia,
- zapis lub automatyczna naprawa plikow w GitLabie,
- porownanie paczek binarnych albo kodu aplikacji,
- porownanie konfiguracji produkcyjnej albo branchy spoza skonfigurowanego
  zakresu testowego,
- pelna analiza calego systemu niezalezna od zmienionych kluczy
  konfiguracyjnych,
- odczyt faktycznej konfiguracji z uruchomionych podow, maszyn lub managera
  sekretow,
- ujawnianie albo walidowanie prawdziwej tresci sekretow,
- zastepowanie review administratora,
- ogolny edytor konfiguracji.

## Otwarte decyzje produktowe po MVP

- czy organizacja ma utrzymywac jawna liste oczekiwanych roznic dla par
  srodowisk,
- czy ostrzezenia odrzucone przez administratora maja tworzyc wersjonowane
  reguly wyciszen,
- czy przyszly zakres ma porownywac branch z konfiguracja faktycznie
  uruchomiona,
- czy wynik ma byc wymaganym artefaktem formalnego procesu wdrozeniowego.
