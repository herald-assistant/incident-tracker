# Runtime Configuration Verification - Business Need

## Cel dokumentu

Ten dokument opisuje potrzebe weryfikacji konfiguracji przygotowanej do
wdrozenia pomiedzy srodowiskami. Nie wybiera implementacji ani technologii
analizy.

## Kontekst biznesowy

Konfiguracja uruchomieniowa systemu jest utrzymywana w dedykowanym
repozytorium GitLaba, innym niz repozytoria kodu uzywane do budowania paczek.
Kazde srodowisko ma osobny branch. Obslugiwane nazwy branchy maja postac:

- `devX`,
- `zt00X`,

gdzie `X` jest pojedyncza cyfra.

Repozytorium ma wspolny plik `global.var` w katalogu glownym. Kazdy komponent
wdrozeniowy ma wlasny katalog, np. `backend/`, z:

- `application.yml.kv` albo `application.yaml.kv`,
- `local.var`.

Plik aplikacyjny moze zawierac wiele dokumentow YAML oraz odwolania do
wartosci z plikow `.var`. Pliki `.var` zawieraja zagniezdzone wartosci,
adresy srodowiskowe, flagi i dane wrazliwe.

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
- komponent wdrozeniowy reprezentowany w Operational Context jako
  `internal-system`,
- branch bazowy,
- branch porownywany,
- opcjonalny ref kodu dla trybu `DEEP`, jezeli uzytkownik chce analizowac
  konkretna wersje zamiast jawnie oznaczonego domyslnego refu repozytorium.

Oba branche musza byc roznymi branchami zgodnymi z formatem `devX` albo
`zt00X`. Potrzeba obejmuje porownania miedzy dowolnymi dwoma obslugiwanymi
branchami; system nie powinien zgadywac kierunku promocji tylko na podstawie
nazwy.

## Tryby analizy

### `BASIC`

Tryb szybki koncentruje sie na konfiguracji:

- porownuje wymagane pliki,
- buduje structural i effective diff,
- pokazuje deterministic findings,
- buduje pelny, zanonimizowany manifest konfiguracji obejmujacy takze
  niezmienione parametry i zachowujacy schemat wielodokumentowego YAML,
- uruchamia druga opinie AI nad zanonimizowanym manifestem, roznicami
  konfiguracyjnymi i findings,
- uzywa Operational Context tylko do wyboru kanonicznego `internal-system` i
  jego katalogu konfiguracji; nie wykorzystuje katalogu do interpretacji
  roznic ani nie czyta kodu systemu,
- jawnie informuje, ze nie potwierdza funkcjonalnego znaczenia kluczy ani
  ownershipu.

Ten tryb ma byc szybszy, tanszy i dostepny, gdy dostepne sa repozytorium
konfiguracji oraz jednoznaczny `internal-system` z katalogiem komponentu.

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
   do kogo zwrocic sie w przypadku niejasnosci.

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

Wynik powinien rozdzielac fakty od interpretacji i traktowac dwa tory jako
rownorzedne elementy decyzji:

1. **wynik deterministyczny** - kompletne fakty o odczycie, strukturze,
   roznicach, odwolaniach i wykrytych regulami anomaliach,
2. **druga opinia AI** - interpretacja znaczenia tych faktow, mozliwy
   scenariusz pomylki i wskazanie, co czlowiek powinien sprawdzic.

Uzytkownik nie powinien musiec wybierac miedzy surowym diffem a opisem AI.
Oba wyniki musza byc widoczne, czytelne i powiazane precyzyjnymi
referencjami. Interpretacja AI nie moze zastapic, ukryc ani zmienic faktow
deterministycznych.

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
- rozroznienie faktu, wyprowadzonego wniosku i interpretacji,
- krotkie uzasadnienie,
- sugerowana czynnosc weryfikacyjna,
- poziom pewnosci lub jawna informacje, ze potrzebna jest decyzja czlowieka.

Analiza powinna umiec wskazac co najmniej:

- brak pliku lub klucza po jednej stronie,
- zmiane typu,
- niepoprawne albo nierozwiazane odwolanie,
- konflikt albo niejednoznacznosc definicji,
- marker innego obslugiwanego srodowiska w wartosci brancha porownywanego,
- podejrzanie niezmieniona wartosc o charakterze srodowiskowym,
- nowa albo zmieniona wartosc wrazliwa bez ujawniania jej tresci,
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
- stan wykonania i wniosek AI,
- laczny stan wymagajacy ludzkiej decyzji,
- wyrazny sygnal, gdy AI i reguly deterministyczne prowadza do roznych
  wnioskow.

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
- jaka jest niezalezna opinia AI i jej confidence,
- czy AI wskazuje te same miejsca co reguly, czy dodaje nowa hipoteze,
- jakie sa trzy najwazniejsze rzeczy do sprawdzenia przed wdrozeniem.

W trybie `DEEP` pierwszy widok powinien dodatkowo pokazac:

- dotkniete systemy i funkcjonalnosci,
- stopien code grounding,
- ownera lub jawny brak rozstrzygnietego ownershipu,
- refy kodu uzyte do analizy.

Szczegoly powinny byc dostepne bez utraty kontekstu:

- filtrowalna i grupowana lista faktow deterministycznych,
- osobna sekcja lub zakladka drugiej opinii AI,
- linki z obserwacji AI do konkretnych pozycji diffu i findings,
- czytelne oznaczenie `FACT`, `DERIVED` i `AI INTERPRETATION`,
- przed/po dla wartosci niewrazliwych oraz stan zmiany bez tresci dla
  sekretow,
- stale widoczny scope, coverage i visibility limits.

## Bezpieczenstwo i poufnosc

Konfiguracja moze zawierac hasla, tokeny, sekrety klientow, dane polaczen i
inne wartosci wrazliwe.

Wymagania:

- dane dostepowe do GitLaba nie sa inputem uzytkownika,
- surowe wartosci wrazliwe nie sa pokazywane w UI, promptach, logach,
  historii ani eksporcie,
- porownanie moze poinformowac, czy wartosc wrazliwa zostala dodana, usunieta
  albo zmieniona, bez ujawniania jej tresci,
- zanonimizowany manifest zachowuje granice dokumentow, profile, zagniezdzenie,
  nazwy statycznych parametrow, typy, ksztalt kolekcji, zrodla definicji,
  relacje odwołan i stan `UNCHANGED`, `CHANGED`, `ADDED` albo `REMOVED`,
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
- eksport i historia zawieraja tylko bezpieczny, zanonimizowany manifest,
  model roznic i findings,
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
- zadna wartosc oznaczona jako wrazliwa nie trafia do publicznego wyniku,
  promptu, logu, historii ani eksportu,
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
