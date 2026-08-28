# Delivery Complexity Trends Runtime Flow

## Cel i granica

`Delivery Complexity Trends` jest uniwersalnym, frontendowym konsumentem
biznesowych CSV z:

- `Delivery Complexity Assessment`, gdzie metryka to `Delivered Story Points`,
- `Delivery Scope Complexity`, gdzie metryka to `Complexity Points`.

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

## Deduplikacja i addytywnosc

Wiersze sa deduplikowane globalnie po `issueKey` bez rozrozniania wielkosci
liter. Wygrywa rekord z pozniejszym `doneAt`; przy tym samym czasie wygrywa
rekord z pliku wybranego pozniej, a nastepnie pozniejszy wiersz. Liczba
usunietych i konfliktowych duplikatow jest jawna w sekcji jakosci.

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
oraz moze ograniczyc widok po zespole, autorze MR i inkluzywnych datach
`od`/`do`. `Tydzien` oznacza tydzien ISO od poniedzialku do niedzieli i uzywa
ISO week-year, dlatego pierwsze dni stycznia moga nalezec do ostatniego
tygodnia poprzedniego roku.
Filtry zespolu i autora dzialaja na poziomie Delivery Unit: jesli jednostka ma
pasujacy zespol albo co najmniej jeden MR danego autora, do trendu trafia jej
pelna wartosc. Filtr autora nie dzieli punktow pomiedzy osoby i nie jest miara
produktywnosci.

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
jednostek z dostepna wartoscia konkretnego wymiaru. Karta `Najwieksza zmiana
wymiaru` wybiera najwieksza bezwzgledna delte wzgledem poprzedniego widocznego
okresu, osobno dla trybu lacznego i sredniego. Brakujace wymiary nie staja sie
zerami: sa pomijane w danej sredniej i liczone jako niepelne oceny czastkowe w
jakosci importu.

Profil wymiarow wyjasnia kompozycje obserwowalnej zlozonosci. Tak samo jak
wynik glowny nie jest miara produktywnosci osoby ani automatyczna ocena
dobrze/zle.

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
