# Delivery Complexity Trends - analiza trendow z CSV

Status: draft

## Potrzeba

Uzytkownik wykonuje Delivery Complexity Assessment albo Delivery Scope
Complexity cyklicznie, np. raz w miesiacu. Pojedynczy raport pokazuje wynik
jednego zakresu, ale nie odpowiada na pytanie, jak obserwowalna dostarczona
zlozonosc zmienia sie w czasie.

Uzytkownik potrzebuje widoku uruchamianego na zadanie, do ktorego lokalnie
zalacza kilka biznesowych CSV z kolejnych okresow. Widok powinien polaczyc dane
bez zapisu po stronie serwera i pokazac, w ktorych dniach, tygodniach,
miesiacach albo kwartalach laczna zlozonosc rosla, malala lub pozostawala bez
zmian.

## Uzytkownicy i decyzje

Glownym uzytkownikiem jest osoba analizujaca dostawy zespolu, np. analityk,
product owner, engineering manager albo czlonek zespolu. Widok ma wspierac
rozmowe o charakterze i rozkladzie dostarczonych zmian, a nie ocene
produktywnosci ludzi.

Uzytkownik chce przede wszystkim:

- rozpoznac kierunek i skale zmiany pomiedzy kolejnymi okresami,
- wyjasnic, ktore oceny czastkowe najbardziej napedzily zmiane wyniku,
- przejsc pomiedzy perspektywa dnia, tygodnia, miesiaca i kwartalu,
- ograniczyc wynik do wybranego zespolu, autora MR, jednego lub kilku typow
  issue oraz zakresu dat,
- zobaczyc liczbe issue i Delivery Units stojacych za lacznym wynikiem,
- sprawdzic, ile rekordow zostalo pominietych, zduplikowanych albo nie mialo
  prawidlowej oceny.

## Oczekiwane wejscie

Uzytkownik zalacza jednoczesnie jeden lub wiele biznesowych plikow CSV
wyeksportowanych z jednego assessmentu:

- wszystkie pliki z Delivery Complexity Assessment, albo
- wszystkie pliki z Delivery Scope Complexity.

Jedna sesja podgladu nie miesza obu algorytmow. Porownywane raporty maja byc
wykonane tym samym modelem i reasoning effort, ale te informacje nie sa
elementem biznesowego CSV, dlatego ich zgodnosc pozostaje odpowiedzialnoscia
uzytkownika.

Pliki sa wybierane lokalnie i nie sa wysylane do backendu, dopisywane do
Analysis History ani przechowywane po odswiezeniu strony.

## Semantyka wyniku

CSV ma jeden wiersz na issue, natomiast ocena powstaje dla Delivery Unit i moze
byc powtorzona przy kilku issue. Widok musi zachowac nastepujace zasady:

- issue jest deduplikowane globalnie po `issueKey`,
- addytywna zlozonosc korzysta z `pointsForAggregation`, aby jedna Delivery
  Unit nie byla liczona wielokrotnie,
- okres wyniku wynika z `doneAt` wiersza niosacego addytywne punkty,
- filtr zespolu dopasowuje Delivery Unit, jezeli nalezy do niej issue tego
  zespolu,
- filtr osoby dopasowuje Delivery Unit, jezeli jej MR ma wskazanego autora,
- filtr typu issue dopasowuje Delivery Unit, jezeli zawiera co najmniej jedno
  issue dowolnego wybranego typu; kilka zaznaczen dziala jako OR,
- po dopasowaniu zespolu, autora lub typu issue do wyniku trafia pelna obserwowalna
  zlozonosc Delivery Unit; nie jest ona dzielona ani przypisywana jako wynik
  konkretnej osoby,
- jednostka zawierajaca kilka typow nadal jest liczona raz, ale moze pojawic
  sie w wynikach kilku osobno wykonanych filtrow, dlatego tych wynikow nie
  nalezy sumowac,
- brak wierszy w okresie nie moze byc automatycznie interpretowany jako zero,
  bo biznesowy CSV nie opisuje kompletnego kalendarza wykonanych raportow.

Porownanie okres do okresu dotyczy kolejnych okresow widocznych w zaladowanych
danych. Wzrost lub spadek jest opisem kierunku liczby punktow, a nie pozytywna
lub negatywna ocena pracy zespolu.

## Oczekiwany wynik

Po prawidlowym zaladowaniu plikow strona powinna pokazac:

- rozpoznany typ assessmentu i jednostke punktowa,
- filtry granulacji, zespolu, autora MR, typow issue oraz dat od-do,
- wykres slupkowy lacznej zlozonosci dla kolejnych okresow,
- sekcje `Co napedza zmiane`, ktora pokazuje laczny wklad wymiarow albo ich
  srednia na Delivery Unit,
- wartosc i zmiane bezwzgledna oraz procentowa wzgledem poprzedniego okresu z
  danymi,
- podsumowanie lacznej zlozonosci, liczby Delivery Units i unikalnych issue,
- tabelaryczne szczegoly okresow jako dokladne i dostepne uzupelnienie wykresu,
- osobna sekcje statusow ocen i jakosci importu, w tym liczbe plikow, wierszy,
  duplikatow oraz odrzuconych danych,

Interpretacja ocen czastkowych zalezy od zrodla. W Delivery Scope Complexity
kolumny `*Points` sa addytywne i w widoku lacznym skladaja sie na
`finalScore`. W Delivery Complexity Assessment surowe wymiary `0-4` pokazuja
profil typowej jednostki, a ich laczny widok jest wazonym wkladem do
`score100`; nie jest rozkladem progowego wyniku DSP.

## Kryteria sukcesu MVP

MVP jest uzyteczny, jezeli:

- uzytkownik moze wybrac wiele plikow CSV jednym razem,
- mieszany zestaw obu assessmentow jest odrzucany bez czesciowego podmienienia
  aktualnego widoku,
- powtorzone `issueKey` nie zwieksza liczby issue ani punktow,
- suma punktow nie zwielokrotnia Delivery Unit zawierajacych kilka issue,
- dzien, tydzien ISO, miesiac i kwartal tworza deterministyczne okresy z
  `doneAt`,
- filtry zespolu, autora, typow issue i dat zachowuja addytywna semantyke
  jednostek,
- wielokrotny filtr typow dziala jako OR i nie liczy wielotypowej Delivery Unit
  wiecej niz raz,
- wykres oraz tabela pokazuja ten sam wynik i te same zmiany okresowe,
- oceny czastkowe sa liczone z tej samej Delivery Unit, daty i filtrow co
  wynik glowny oraz zawsze pokazuja liczebnosc proby,
- Scope zachowuje dokladny addytywny rozklad punktow, a Assessment nie
  przedstawia wymiarow `0-4` jako liniowego rozkladu DSP,
- bledny, pusty albo niekompatybilny plik daje zrozumialy komunikat,
- starszy CSV bez informacji o autorach nadal pozwala zobaczyc trend i filtr
  zespolu, ale jawnie ogranicza filtr osoby,
- wszystkie obliczenia pozostaja lokalne w przegladarce.

## Non-goals MVP

MVP nie ma:

- porownywac obu assessmentow na jednym wykresie,
- weryfikowac modelu AI ani reasoning effort na podstawie nazwy pliku,
- zapisywac zestawu CSV, dashboardu albo ustawionych filtrow,
- tworzyc backendowego endpointu importu lub agregacji,
- prognozowac przyszlej zlozonosci ani oceniac velocity,
- dzielic punktow Delivery Unit pomiedzy zespoly lub autorow,
- przypisywac punktow wylacznie jednemu typowi issue ani traktowac typow jako
  rozlacznych kategorii Delivery Unit,
- tworzyc rankingu osob, zespolow, vendorow ani dostawcow,
- interpretowac profilu wymiarow jako oceny produktywnosci osoby lub zespolu,
- uznawac okresu bez zaimportowanych danych za okres z wynikiem zero,
- edytowac, poprawiac lub ponownie eksportowac zaladowanych danych.

## Ryzyka i kompromisy

- CSV nie zawiera modelu ani effortu, wiec strona nie moze potwierdzic ich
  zgodnosci pomiedzy raportami.
- Ten sam `issueKey` moze miec rozne wartosci w kilku plikach; potrzebna jest
  jawna, deterministyczna regula wyboru oraz licznik konfliktow.
- Autor MR opisuje powiazanie z zakresem zmiany. Jedna Delivery Unit moze miec
  wielu autorow, dlatego filtr osoby nie stanowi indywidualnej atrybucji.
- Jedna Delivery Unit moze zawierac kilka typow issue, a nazwy typow moga byc
  konfigurowane inaczej w roznych projektach Jira. Filtr opisuje metadane
  workflow i korelacje z profilem zlozonosci, a nie jej przyczyne ani
  rozlaczna klasyfikacje techniczna.
- Nazwa autora nie jest stabilnym identyfikatorem. Nowe eksporty powinny
  zachowac rowniez ID autora, a nazwy sluzyc do prezentacji.
- Bez metadanych pokrycia raportu nie da sie odroznic okresu bez dostaw od
  okresu, dla ktorego nie zalaczono CSV.
- Srednia wymiaru moze zmienic sie przy malej liczbie Delivery Units, dlatego
  widok musi eksponowac liczebnosc proby i nie ukrywac brakujacych ocen
  czastkowych.
