# Domain Skill Generation - Business Need

Status: active

Ostatnio zweryfikowano zakres potrzeby: 2026-08-09. Stan implementacji,
wybrane rozwiazanie techniczne i kolejnosc dostarczania naleza do kodu oraz
zatwierdzonych dokumentow w `../plans/`, nie do tego pliku.

## Cel dokumentu

Ten dokument opisuje potrzebe produktowa i oczekiwany rezultat feature'u
`Domain Skill Generation`. Nie opisuje architektury implementacji i nie
stanowi zgody na rozpoczecie kodowania.

Kazdy plan rozwoju tego feature'a powinien wskazywac ten plik jako
`Source need` i jawnie okreslac, ktora czesc potrzeby realizuje.

## Kontekst biznesowy

Zespoly coraz czesciej uzywaja coding agentow do pracy w rozbudowanych
repozytoriach. Agent moze szybko zaproponowac technicznie poprawna zmiane, ale
bez znajomosci konkretnego modulu czesto wybiera pierwsze mozliwe miejsce
implementacji zamiast miejsca zgodnego z odpowiedzialnosciami domenowymi.

W modularnym monolicie ta roznica jest szczegolnie istotna. Ta sama regula
moze zostac umieszczona w kontrolerze, serwisie, validatorze, mapperze,
agregacie albo repozytorium. Kod moze sie kompilowac, a mimo to:

- wzmacniac niewlasciwa warstwe,
- omijac publiczny kontrakt modulu,
- tworzyc zaleznosc do implementacji innego bounded contextu,
- powielac regule biznesowa,
- rozszerzac istniejace naruszenie architektury,
- utrudniac przyszle wydzielenie modulu,
- deklarowac walidacje, ktora nie zostala rzeczywiscie wykonana.

Wiedza potrzebna do podjecia poprawnej decyzji jest rozproszona pomiedzy
kodem produkcyjnym, testami, konfiguracja buildu, instrukcjami repozytorium,
dokumentacja i wiedza wlascicieli modulu. Ogolna dokumentacja architektury nie
wystarcza, poniewaz nie prowadzi agenta przez odkrycie konkretnego vertical
slice'u przed rozpoczeciem edycji.

Potrzebny jest powtarzalny sposob zamiany wiedzy o jednym module w przenosny,
audytowalny Agent Skill, ktory pomaga kolejnym agentom pracowac zgodnie z
granicami i zamierzonym kierunkiem architektury tego modulu.

## Problem uzytkownika

Uzytkownik chce wskazac modul i otrzymac odpowiedz na pytanie:

```text
Jak przygotowac dla tego modulu skill, ktory opiera sie na rzeczywistym
kodzie, respektuje jego granice i prowadzi coding agenta do poprawnego miejsca
zmiany bez zgadywania?
```

Feature powinien pomagac szczegolnie wtedy, gdy:

- modul ma wiele pozornie poprawnych miejsc implementacji tej samej zmiany,
- struktura kodu nie odzwierciedla jednoznacznie odpowiedzialnosci domenowych,
- architektura obserwowana zawiera legacy albo przejsciowe wyjatki,
- zespol chce wskazac architekture docelowa bez uznawania kazdego obecnego
  rozwiazania za wzorzec,
- wiedza o granicy bounded contextu jest rozproszona albo niepelna,
- coding agent rozpoczyna edycje przed przeczytaniem calego use case'u,
- modul ma pozostac mozliwy do przyszlego wydzielenia,
- istniejacy skill wymaga bezpiecznej aktualizacji po zmianach w module.

## Uzytkownicy i momenty pracy

Glowni uzytkownicy:

- developer odpowiedzialny za modul,
- tech lead albo architekt zatwierdzajacy granice i kierunek rozwoju,
- owner bounded contextu,
- zespol wdrazajacy coding agentow do codziennej pracy,
- zespol przygotowujacy modul do przyszlego wydzielenia.

Najwazniejsze momenty uzycia:

- przed szerszym udostepnieniem coding agentow w repozytorium,
- po utworzeniu nowego modulu,
- po istotnej zmianie architektury albo publicznego kontraktu,
- gdy istniejacy skill jest niepelny lub nieaktualny,
- gdy modul zawiera legacy i trzeba oddzielic stan obecny od docelowego,
- przed pracami zwiekszajacymi zaleznosci pomiedzy modulami,
- przed rozpoczeciem przygotowan do ekstrakcji modulu.

## Docelowy rezultat

Docelowym rezultatem nie jest dowolnie wygenerowany Markdown ani streszczenie
modulu. Rezultatem ma byc zatwierdzony przez czlowieka, source-backed pakiet
skilla dla jednego modulu, ktory:

1. wskazuje zakres modulu i jego granice,
2. odroznia stan zaobserwowany od zatwierdzonego kierunku
   architektonicznego,
3. opisuje odpowiedzialnosci i reprezentatywne sciezki wykonania na podstawie
   konkretnych zrodel,
4. jawnie pokazuje niepewnosci, konflikty i ograniczenia widocznosci,
5. prowadzi agenta przez odkrycie kontekstu przed wyborem miejsca zmiany,
6. wymaga sprawdzenia granicy bounded contextu i odpowiedzialnosci elementu,
7. wymaga uczciwego raportowania wykonanej i niewykonanej walidacji,
8. pozwala zakonczyc prace stanem zablokowanym zamiast wymuszac zgadywanie,
9. moze byc przeniesiony razem z modulem albo ponownie wygenerowany po jego
   zmianie,
10. pozwala ustalic, z jakiego kodu, decyzji i wersji generatora powstal.

Najwazniejszy oczekiwany efekt to ograniczenie zmian, ktore pogarszaja granice
bounded contextu, oraz utrzymanie modulu w stanie mozliwym do przyszlego
wydzielenia.

## Zasady zaufania do wyniku

Wynik musi jawnie rozdzielac piec rodzajow informacji:

- `FACT` - informacja bezposrednio potwierdzona w dostepnych zrodlach,
- `INFERENCE` - propozycja interpretacji oparta na wskazanych faktach,
- `DECISION` - decyzja zatwierdzona przez uzytkownika,
- `EXCEPTION` - jawnie zaakceptowane odstepstwo od kierunku docelowego,
- `UNKNOWN` - informacja, ktorej nie da sie bezpiecznie rozstrzygnac.

Obowiazuja nastepujace zasady:

- fakt musi wskazywac zrodlo,
- wniosek musi wskazywac dowody, na ktorych zostal oparty,
- decyzja i wyjatek musza byc widoczne jako decyzje czlowieka,
- brak informacji nie moze zostac zamieniony w twierdzenie,
- istniejace naruszenie nie moze automatycznie stac sie wzorcem,
- obserwowana architektura nie jest automatycznie architektura docelowa,
- wynik AI jest propozycja do review, a nie samodzielnym zrodlem prawdy,
- finalny pakiet nie moze powstac z niezatwierdzonych niejednoznacznosci.

## Zakres funkcjonalny potrzeby

### Rozpoznanie modulu

Feature powinien zebrac wystarczajacy obraz wskazanego modulu, aby pokazac:

- jego tozsamosc i zakres,
- kod produkcyjny, testy i istotne instrukcje,
- wejscia i wyjscia,
- zaleznosci wewnetrzne i zewnetrzne,
- reprezentatywne vertical slice'y,
- role i odpowiedzialnosci widocznych elementow,
- publiczne kontrakty i potencjalne przekroczenia granic,
- dostepne sposoby walidacji,
- brakujace dane i ograniczenia analizy.

Rozpoznanie ma byc source-backed. Sama nazwa klasy, pakietu albo metody nie
jest wystarczajacym dowodem jej odpowiedzialnosci.

### Propozycja profilu modulu

Na podstawie zebranego materialu feature powinien przygotowac do review
propozycje:

- nazwy i zakresu bounded contextu,
- obserwowanego przeplywu i odpowiedzialnosci,
- kierunku architektury docelowej,
- reprezentatywnych przykladow,
- granic, kontraktow i ryzyk,
- dozwolonych wyjatkow,
- sposobow walidacji,
- przypadkow wymagajacych zatrzymania i pytania do czlowieka.

Kazda istotna propozycja powinna wskazywac zrodla albo pozostac oznaczona jako
nierozstrzygnieta.

### Weryfikacja przez czlowieka

Przed wygenerowaniem finalnego pakietu uzytkownik powinien moc:

- zaakceptowac albo odrzucic propozycje,
- zastapic propozycje jawna decyzja,
- pozostawic informacje jako nieznana,
- wskazac kierunek architektury docelowej,
- dodac albo ograniczyc wyjatek,
- zatwierdzic sposoby walidacji,
- zatrzymac generowanie.

Zatwierdzone decyzje powinny byc mozliwe do audytu i przyszlego ponownego
uzycia. Stabilne decyzje nie powinny wymagac ponownego odpowiadania, jezeli po
regeneracji nie pojawil sie nowy konflikt.

### Pakiet domenowego skilla

Wygenerowany pakiet powinien prowadzic coding agenta przez nastepujaca
kolejnosc pracy:

1. potwierdzenie zakresu zadania i modulu,
2. odczytanie niezbednych zrodel i analogicznych przykladow,
3. sprawdzenie granicy bounded contextu,
4. sklasyfikowanie rodzaju zmiany,
5. wybranie wlasciciela odpowiedzialnosci i miejsca implementacji,
6. wykonanie zmiany bez nieuzasadnionego rozszerzania zakresu,
7. przeprowadzenie dostepnej walidacji,
8. raport wyniku, ograniczen i niewykonanych sprawdzen.

Skill powinien przewidywac powrot do wczesniejszego etapu po odkryciu nowych
informacji oraz bezpieczne zatrzymanie, gdy brakuje danych albo decyzji.
Pakiet powinien zawierac tylko potrzebne, przenosne informacje. Nie powinien
byc kopia repozytorium ani dlugim raportem z analizy.

### Walidacja i pochodzenie wyniku

Przed udostepnieniem pakietu feature powinien sprawdzic co najmniej:

- kompletnosc wymaganych elementow,
- poprawnosc odwolania do materialow pomocniczych,
- zgodnosc mocnych twierdzen z dostepnymi zrodlami albo decyzjami,
- brak odwolania do nieistniejacych symboli,
- spojnosc zatwierdzonych decyzji i wyjatkow,
- brak niejawnego zamienienia `UNKNOWN` w regule twierdzaca,
- mozliwosc ustalenia wersji wejscia, decyzji i wyniku,
- brak ujawnionych sekretow albo danych wrazliwych.

Niepowodzenie walidacji powinno blokowac uznanie pakietu za gotowy.

### Utrzymanie po zmianach modulu

W dalszym rozwoju feature powinien pomagac:

- rozpoznac, ze skill moze byc nieaktualny,
- pokazac, co zmienilo sie od poprzedniej generacji,
- ponownie wykorzystac nadal aktualne decyzje,
- skierowac tylko nowe konflikty do review,
- bezpiecznie zregenerowac pakiet,
- nie nadpisywac recznych zmian bez wiedzy i zgody uzytkownika.

## Zakres pierwszej wersji

Pierwsza wersja produktowa obejmuje:

- jedno repozytorium GitLab i wskazana wersje zrodla,
- dokladnie jeden modul Maven,
- kod Java/Spring, z pierwszenstwem dla wejsc REST,
- jeden wersjonowany kierunek architektury docelowej,
- deterministyczny wynik dostepny takze bez AI, z opcjonalnym wzbogaceniem o
  propozycje AI,
- obowiazkowe review decyzji przed generowaniem,
- podglad wygenerowanego pakietu i pobranie go jako ZIP,
- walidacje strukturalna, dowodowa i bezpieczenstwa,
- jawne ograniczenia widocznosci i nierozstrzygniete kwestie.

Pierwsza wersja nie zapisuje automatycznie plikow do analizowanego
repozytorium, nie wykonuje zmian w jego kodzie i nie uruchamia znalezionych
polecen.

## Wartosc biznesowa

Feature ma zmniejszyc koszt powtarzalnego przekazywania agentom lokalnej
wiedzy o module oraz ryzyko zmian, ktore sa technicznie poprawne, ale
architektonicznie szkodliwe.

Oczekiwane efekty:

- mniej zmian umieszczanych w pierwszej widocznej klasie,
- mniej nieuzasadnionych zaleznosci pomiedzy bounded contextami,
- bardziej powtarzalne rozpoznanie kodu przed edycja,
- szybsze wdrozenie developerow i agentow do pracy w module,
- jawne rozroznienie legacy od zamierzonej architektury,
- mniej twierdzen o testach, ktore nie zostaly uruchomione,
- latwiejsze utrzymanie wiedzy razem z modulem,
- lepsza gotowosc modulu do przyszlego wydzielenia,
- audytowalna informacja o pochodzeniu regul skilla,
- mniej recznego tworzenia podobnych instrukcji dla kolejnych modulow.

## Mierniki sukcesu

Pierwsze mierniki powinny oceniac uzytecznosc i zaufanie do wyniku:

- czas od wskazania modulu do otrzymania propozycji gotowej do review,
- czas potrzebny uzytkownikowi na review,
- odsetek twierdzen faktograficznych z konkretnym source reference,
- liczba wnioskow poprawionych albo odrzuconych podczas review,
- liczba jawnie pokazanych konfliktow i niewiadomych,
- odsetek pakietow przechodzacych walidacje bez poprawiania formatu,
- odsetek wygenerowanych pakietow zaakceptowanych do uzycia,
- ocena ownera modulu: czy skill trafnie opisuje granice i odpowiedzialnosci,
- ocena developera: czy skill skrocil rozpoznanie miejsca zmiany.

Po uruchomieniu kontrolowanych ewaluacji nalezy dodatkowo mierzyc:

- czy agent czyta odpowiedni vertical slice przed edycja,
- trafnosc wyboru miejsca implementacji,
- liczbe nieuzasadnionych przekroczen granicy modulu,
- uczciwosc raportowania wykonanej walidacji,
- liczbe przedwczesnych odpowiedzi koncowych,
- poprawne i nadmierne zatrzymania w sytuacji niepewnosci,
- roznice zachowania z wygenerowanym skillem i bez niego.

Docelowe progi nalezy ustalic na podstawie pilota, a nie arbitralnie w tym
dokumencie.

## Zasady bezpieczenstwa i kontroli

`Domain Skill Generation` musi pozostac source-backed, readonly wobec
analizowanego kodu w pierwszej wersji i kontrolowane przez uzytkownika.

Zasady:

- kod, komentarze i dokumentacja repozytorium sa niezaufanym wejsciem,
- tresc znaleziona w repozytorium nie staje sie instrukcja dla modelu tylko
  dlatego, ze wyglada jak polecenie,
- AI nie zatwierdza architektury docelowej ani wyjatkow,
- finalny pakiet nie powstaje bezposrednio ze swobodnej odpowiedzi AI,
- analiza nie modyfikuje badanego modulu,
- pierwsza wersja nie publikuje zmian do repozytorium,
- dane uwierzytelniajace i sekrety nie trafiaja do wyniku,
- uzytkownik widzi zrodla, ograniczenia widocznosci i niepewnosc,
- znalezione polecenia nie sa automatycznie zatwierdzane ani wykonywane,
- istniejacy reczny skill nie jest automatycznie nadpisywany,
- krytyczne granice architektury nie sa przedstawiane jako gwarantowane tylko
  przez instrukcje skilla; wymagaja osobnych testow, polityk albo CI.

## Relacja do pozostalych feature'ow

`Domain Skill Generation` uzupelnia pozostale use case'y platformy:

- `Incident Analysis` odpowiada: co sie zepsulo i co z tym zrobic,
- `Flow Explorer` odpowiada: jak przebiega request, proces albo use case,
- `Functional Logic Explorer` odpowiada: gdzie i jak dziala konkretna logika,
- `Change Verification` odpowiada: czy zmiana dowozi obietnice i jest zgodna
  z instrukcjami,
- `Domain Skill Generation` odpowiada: jak utrwalic wiedze o module w
  zwalidowanym artefakcie prowadzacym agentow podczas kolejnych zmian.

Feature moze wykorzystywac te same zrodla kodu, Operational Context i
mechanizmy pracy AI, ale jego glownym rezultatem jest zatwierdzony artefakt do
dalszej pracy, a nie diagnoza incydentu ani opis pojedynczego use case'u.

## Granice i non-goals

Pierwszy zakres nie jest:

- generatorem dla wielu modulow jednoczesnie,
- obsluga wielu jezykow i frameworkow,
- automatycznym narzedziem do naprawy architektury,
- mechanizmem migracji modulu do mikroserwisu,
- gwarancja, ze kazdy model wykona wszystkie instrukcje,
- zamiennikiem testow architektonicznych, buildu, CI albo review,
- narzedziem do automatycznego zatwierdzania zaleznosci miedzy kontekstami,
- pelna rekonstrukcja wszystkich regul biznesowych,
- analiza dynamiczna srodowiska produkcyjnego,
- narzedziem do samodzielnej zmiany kodu badanego modulu,
- narzedziem do automatycznego tworzenia merge requestow,
- platforma do wykonywania nieograniczonych polecen shell,
- behavioral eval runnerem,
- mechanizmem automatycznego instalowania wygenerowanego skilla,
- zamiennikiem decyzji ownera modulu, tech leada albo architekta.

Pierwsza wersja ma udowodnic, ze dla jednego modulu mozna powtarzalnie
przygotowac source-backed, zatwierdzony i zwalidowany pakiet skilla.

## Ryzyka produktowe

Najwazniejsze ryzyka:

- wiarygodnie brzmiacy wniosek AI moze zostac pomylony z faktem,
- niepelna analiza kodu moze stworzyc falszywe poczucie pokrycia,
- zespol moze nie miec uzgodnionego kierunku architektury docelowej,
- istniejace legacy moze zostac przypadkowo utrwalone jako wzorzec,
- skill moze stac sie zbyt szczegolowy i szybko sie zestarzec,
- zbyt restrykcyjny skill moze blokowac poprawne zmiany,
- zbyt ogolny skill moze nie zmieniac zachowania agenta,
- brak ownera decyzji moze zatrzymywac review,
- rozne coding agenty moga inaczej aktywowac ten sam skill,
- uzytkownik moze oczekiwac twardej gwarancji, ktorej soft skill nie daje.

Wynik powinien komunikowac te granice zamiast ukrywac je za pojedynczym
poziomem confidence.

## Ustalone decyzje produktowe dla pierwszego planu

Pierwszy plan zachowuje nastepujace decyzje:

- jeden wygenerowany skill odpowiada jednemu wskazanemu modulowi,
- wejscie pochodzi z jednego repozytorium GitLab i wskazanej wersji,
- pierwsza wersja koncentruje sie na Java/Spring/Maven i wejsciach REST,
- obserwowany stan i architektura docelowa pozostaja osobnymi informacjami,
- AI moze proponowac interpretacje, ale nie zatwierdza decyzji zespolu,
- review czlowieka jest obowiazkowe przed finalnym generowaniem,
- finalny pakiet powstaje z zatwierdzonego profilu, nie bezposrednio z
  odpowiedzi AI,
- pierwsza wersja udostepnia podglad i ZIP,
- pierwsza wersja nie zapisuje automatycznie do badanego repozytorium,
- behavior evaluation, publikacja i automatyczny drift check sa kolejnymi
  etapami.

Zmiana ktorejkolwiek z tych decyzji powinna byc jawnie opisana i zatwierdzona
w planie.

## Otwarte decyzje produktowe przed kolejnymi planami

Przed planem obejmujacym dany zakres trzeba doprecyzowac:

- kto moze zatwierdzac bounded context, kierunek docelowy i wyjatki w
  srodowisku wielouzytkownikowym,
- jak postepowac, gdy modul obejmuje kilka bounded contextow,
- jak postepowac, gdy jeden bounded context obejmuje kilka modulow,
- jakie coding agenty maja byc oficjalnie wspierane,
- jak obslugiwac istniejacy reczny skill i reczne zmiany,
- gdzie przechowywac decyzje potrzebne do regeneracji,
- kiedy skill powinien zostac oznaczony jako nieaktualny,
- w jakim izolowanym srodowisku wykonywac przyszle behavior evals,
- czy przyszla publikacja ma tworzyc merge request,
- jakie hard gates powinny towarzyszyc skillowi w testach i CI.
