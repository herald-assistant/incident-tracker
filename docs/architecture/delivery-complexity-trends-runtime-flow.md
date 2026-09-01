# Delivery Complexity Trends Runtime Flow

## Cel i granica

`Delivery Complexity Trends` jest uniwersalnym, frontendowym konsumentem
biznesowych CSV z `Delivery Complexity Assessment` oraz
`Delivery Scope Complexity`. Oba zrodla sa w prezentacji normalizowane do
wspolnej metryki `Complexity Points (CP)`; zrodlowe nazwy pol scoringu
pozostaja detalem kompatybilnego formatu CSV.

Ekran jest dostepny pod `GET /delivery-complexity-trends`. Nie uruchamia
assessmentu, nie ma API, historii ani storage po stronie serwera i nie importuje
wersjonowanych envelope JSON. Wybrane pliki sa odczytywane i przetwarzane tylko
w pamieci biezacej karty przegladarki. Odswiezenie lub wyczyszczenie ekranu
usuwa zestaw danych.

Jeden zestaw moze zawierac tylko CSV jednego rodzaju assessmentu. Porownanie
obu algorytmow nie jest celem tego widoku. Uzytkownik odpowiada za zalaczenie
runow wykonanych tym samym modelem i `reasoningEffort`: biznesowy CSV celowo
nie przechowuje tych metadanych, wiec ekran nie moze zweryfikowac zalozenia.

## Import i rozpoznanie formatu

UI przyjmuje od 1 do 50 plikow `.csv`, maksymalnie 20 MB i 200 000 wierszy
danych lacznie. Parser obsluguje format generowany przez oba assessmenty:
UTF-8 z opcjonalnym BOM, separator `;`, CRLF albo LF, cytowane separatory,
cudzyslowy i nowe linie.

Typ assessmentu jest rozpoznawany po jego wymaganych kolumnach merytorycznych,
a nie po nazwie pliku. Kazdy plik musi miec wspolne pola issue, Team, Delivery
Unit, statusu i `pointsForAggregation` oraz kompletny zestaw kolumn jednego
assessmentu. Zestaw mieszany, niejednoznaczny format, zduplikowane lub puste
naglowki, uszkodzone wiersze i nieprawidlowe liczby odrzucaja caly nowy import.
Poprzedni poprawny zestaw pozostaje wtedy widoczny.

Aktualne CSV zawieraja sparowane listy `mergeRequestAuthorIds` i
`mergeRequestAuthorNames`, rozdzielone ` | `. Starsze biznesowe CSV bez obu
kolumn nadal buduja trend i filtry zespolu/dat, ale nie dostarczaja filtra
autora. Obecnosc tylko jednej kolumny autora jest bledem kontraktu. Autor jest
identyfikowany po ID, a nazwa jest etykieta; rekord bez ID uzywa jawnego,
mniej stabilnego klucza opartego o nazwe i jest liczony w jakosci importu.

Cztery kolumny time tracking sa opcjonalne: `timeSpentSeconds`,
`originalEstimateSeconds`, `remainingEstimateSeconds` oraz
`timeTrackingCapturedAt`. Ich brak zachowuje caly dotychczasowy trend bez
sekcji efektywnosci. Puste komorki oznaczaja brak danych, nigdy zero.
Starsze raporty moga zostac wzbogacone poza aplikacja przez
`tools/enrich-assessment-csv-time-tracking.mjs`; skrypt zachowuje zrodla i
domyslnie zapisuje kopie w osobnym katalogu. Jest to snapshot aktualnego stanu
Jira w chwili backfillu, a nie rekonstrukcja historycznego nakladu.

## Deduplikacja i addytywnosc

Wiersze sa deduplikowane globalnie po `issueKey` bez rozrozniania wielkosci
liter. Wygrywa rekord z pozniejszym `doneAt`; przy tym samym czasie wygrywa
nowszy `timeTrackingCapturedAt`, a dopiero potem rekord z pliku wybranego
pozniej i pozniejszy wiersz. Starszy CSV bez timestampu zachowuje poprzednia
kolejnosc. Liczba usunietych i konfliktowych duplikatow jest jawna w sekcji
jakosci.

Po deduplikacji issue sa ponownie skladane po `deliveryUnitId`. Punkty jednej
Delivery Unit pochodza z jej pojedynczego niepustego
`pointsForAggregation`. Gdy starszy albo nietypowy zestaw utracil wiersz
kotwiczacy, ekran moze uzyc powtarzanego wyniku koncowego jednostki i pokazuje
ten fallback. Wiele kotwic albo rozbiezne wyniki koncowe nie sa sumowane po
issue; sa raportowane jako ostrzezenie jakosci.

Data punktow jednostki pochodzi z `doneAt` wiersza kotwiczacego, a przy
fallbacku z wybranego wiersza wyniku koncowego. Grupowanie uzywa kalendarzowej
czesci daty zapisanej w CSV, bez przesuniecia przez strefe czasowa przegladarki.

## Filtry, okresy i wynik

Uzytkownik wybiera granularnosc `Dzien`, `Tydzien`, `Miesiac` albo `Kwartal`
oraz moze ograniczyc widok po zespole, autorze MR, typach issue i inkluzywnych
datach `od`/`do`. `Tydzien` oznacza tydzien ISO od poniedzialku do niedzieli i
uzywa ISO week-year, dlatego pierwsze dni stycznia moga nalezec do ostatniego
tygodnia poprzedniego roku.

Filtry zespolu, autora i typu issue dzialaja na poziomie Delivery Unit. Jesli
jednostka ma pasujacy zespol, co najmniej jeden MR danego autora albo co
najmniej jedno issue wybranego typu, do trendu trafia jej pelna wartosc.
Wielokrotny wybor typow dziala jako OR, a pasujaca jednostka jest liczona raz
nawet wtedy, gdy zawiera kilka zaznaczonych typow. Katalog opcji pokazuje
liczbe Delivery Units zawierajacych dany typ. Filtr autora nie dzieli punktow
pomiedzy osoby i nie jest miara produktywnosci; analogicznie filtr typu nie
przypisuje punktow wylacznie jednej kategorii Jira.

Dla kazdego widocznego okresu ekran pokazuje:

- sume addytywnych punktow,
- zmiane bezwzgledna i procentowa wzgledem poprzedniego widocznego okresu z
  danymi,
- liczbe Delivery Units i unikalnych issue.

Brakujace okresy kalendarzowe nie sa dopisywane jako zera. Pierwszy widoczny
okres nie ma delty, a procent po poprzedniej wartosci zero pozostaje
nieokreslony. Slupki pokazuja kierunek wzrost/spadek/bez zmiany, ale ten
kierunek nie oznacza automatycznie oceny dobre/zle. Tabela pod wykresem jest
kanonicznym, dokladnym odpowiednikiem tych samych danych.

## Oceny czastkowe i przyczyny zmiany

Wymiary sa pobierane raz na Delivery Unit z tego samego wiersza kotwiczacego,
ktory niesie `pointsForAggregation`, albo z tego samego wiersza fallbacku co
wynik koncowy. Dzieki temu sekcja `Co napedza zmiane` zachowuje identyczna
deduplikacje, date punktowa, filtry i granularnosc jak wykres wyniku glownego.
Nie sumuje powtorzonych wymiarow z kazdego issue jednostki.

Semantyka prezentacji jest jawnie zalezna od assessmentu:

- dla Delivery Scope Complexity szesc kolumn `*Points` tworzy addytywny
  rozklad `finalScore`; domyslny skumulowany wykres laczny sumuje sie do
  `Complexity Points`, a tryb sredni pokazuje punktowy wklad wymiaru na
  Delivery Unit,
- dla Delivery Complexity Assessment domyslny heatmap pokazuje srednia
  surowa ocene `0-4`; opcjonalny wykres laczny stosuje wagi
  `10/20/20/15/10/10/15` i pokazuje wklad do `score100`. Nie jest to rozklad
  Delivered Story Points, poniewaz DSP powstaje przez progi wyniku `score100`.

Oba tryby pokazuja liczbe Delivery Units w okresie, a srednia dodatkowo liczbe
jednostek z dostepna wartoscia konkretnego wymiaru. Brakujace wymiary nie staja
sie zerami: sa pomijane w danej sredniej i liczone jako niepelne oceny
czastkowe w jakosci importu.

Profil wymiarow wyjasnia kompozycje obserwowalnej zlozonosci. Tak samo jak
wynik glowny nie jest miara produktywnosci osoby ani automatyczna ocena
dobrze/zle.

## Efektywnosc czasu i estimate

Warstwa prezentacji uzywa jednego slownika niezaleznie od assessmentu:

- `Complexity Points (CP)` oznacza koncowy wynik zlozonosci uzywany w trendzie,
- `Original Estimate (MD)` oznacza pierwotna estymacje z Jira przeliczona z
  sekund na MD,
- `Time Spent (MD)` oznacza czas zalogowany w Jira przeliczony z sekund na MD,
- `Remaining Estimate (MD)` oznacza pozostala estymacje z Jira, gdy raport
  pokazuje jej wartosc,
- `Efficiency` ma jednostke `CP/MD`.

UI nie uzywa dla tych wartosci nazw Delivered Story Points, DSP, punktow
delivery ani osobodni. Techniczny kontrakt CSV pozostaje kompatybilny i nadal
przechowuje `timeSpentSeconds`, `originalEstimateSeconds` oraz
`remainingEstimateSeconds`. Przelicznik jest staly: `1 MD = 8 h`.

Gdy zaimportowany zestaw zawiera dane Jira time tracking, ekran dodaje osobna
sekcje bez zmiany istniejacych wykresow. Glowny wskaznik to:

`CP/MD`, gdzie MD ma zawsze 8 godzin.

Punkty sa nadal liczone raz na Delivery Unit, a `timeSpentSeconds` raz na
unikalne issue. Do wskaznika kwalifikuje sie tylko Delivery Unit, dla ktorej
kazde issue ma snapshot `timeSpentSeconds`, a suma czasu jest dodatnia.
Niepelny Time Spent i Time Spent rowny zero sa wykluczane i pokazywane osobno.
Pokrycie CP oznacza udzial CP kwalifikujacej sie proby we wszystkich CP
widocznego zakresu.

Calosc snapshotu czasu jest przypisana do okresu `doneAt` punktowej kotwicy
Delivery Unit. CSV nie zawiera historii worklogow, dlatego ekran nie rozklada
nakladu na rzeczywiste dni jego poniesienia. Okresy bez kwalifikujacej sie
proby nie sa dopisywane jako zera. Dla kazdego okresu widoczne sa CP,
Time Spent (MD), Efficiency (CP/MD), zmiana, pokrycie i wielkosc proby; okres ponizej
trzech Delivery Units jest liczony jako mala proba.

Dla calego tribe jednostki wielozespolowe pozostaja w wyniku. Po wyborze
konkretnego zespolu wskaznik obejmuje tylko Delivery Units, ktorych wszystkie
issue maja ten sam wybrany Team; jednostki wspoldzielone sa wykluczone i
policzone osobno. Filtr autora MR jedynie zaweza zakres dostaw. Nie rozdziela
`timespent` na autorow i nie tworzy indywidualnej miary efektywnosci.

Jesli ta sama kwalifikujaca sie jednostka ma kompletne, dodatnie
`originalEstimateSeconds`, ekran porownuje Original Estimate (MD) z Time Spent
(MD). Sumy MD pozostaja kontekstem dla calego zakresu, natomiast trend per
okres pokazuje znormalizowane odchylenie
`(Time Spent - Original Estimate) / Original Estimate * 100%` wokol osi `0%`.
Wartosc ujemna oznacza naklad ponizej estymaty, a dodatnia naklad powyzej
estymaty. Pozostale estimate nie sa mieszane z ta proba. Dodatni
`remainingEstimateSeconds` dla issue w Done jest licznikiem jakosci danych, a
nie czescia efektywnosci.

Wskaznik pokazuje obserwowana relacje wyniku assessmentu do zarejestrowanego
czasu. Nie dowodzi przyczynowego uzysku z AI i nie sluzy do rankingow osob.

Osobne sekcje pokazuja laczna zlozonosc, ostatnia zmiane, pokrycie jednostek i
issue, najwyzszy okres, najwiekszy wzrost/spadek, statusy ocen oraz jakosc
importu. Stan pusty prowadzi do wyboru plikow, a wyczyszczenie usuwa dane i
filtry.

## Ownership i zaleznosci

Modele, walidacja formatu assessmentow, deduplikacja, agregacja i strona
mieszkaja w `frontend/src/app/features/delivery-complexity-trends`. Feature nie
importuje kodu zadnego z dwoch sibling assessmentow. Kazdy assessment nadal
posiada wlasny mapper eksportu CSV.

Neutralny `frontend/src/app/core/utils/csv-file.utils.ts` posiada tylko zapis,
pobieranie i skladniowe parsowanie CSV; nie zna naglowkow, punktow, Delivery
Units ani semantyki assessmentow. Dodanie kolejnego zrodla trendu wymaga jego
jawnego kontraktu w feature trendow, bez przesuwania semantyki do `core`.
