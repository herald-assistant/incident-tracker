# Operational Context jako mapa dalszej analizy

Stan: zrealizowana.

## Problem

Operational Context ma dawac AI i operatorowi punkt startu oraz wskazywac,
gdzie szukac dalszych informacji o systemie. Utrzymywanie tego samego
powiazania w kilku miejscach utrudnia aktualizacje katalogu i prowadzi do
rozbieznosci. Samo usuniecie powtorzen moze jednak pogorszyc odnajdywanie
systemow, procesow, kontekstow, integracji, wlascicieli i zrodel kodu.

Informacja zapisana po jednej stronie relacji powinna byc dostepna przy
odczycie kazdej jej strony. Mozliwosc odtworzenia powiazania przez kilka
osobnych odczytow nie oznacza, ze AI faktycznie je odkryje.

## Uzytkownicy i wartosc

- Operator utrzymuje fakt w jednym miejscu i widzi jego powiazania.
- Analityk zaczynajacy od objawu, systemu, procesu lub pojecia otrzymuje
  czytelna mape dalszej analizy.
- AI dostaje uzasadnione wskazania, co moze przeczytac dalej i w jakim celu.

## Oczekiwany rezultat

Jednostronny zapis relacji ulatwia utrzymanie katalogu. Odczyt przedstawia
pelne znaczenie znanych powiazan niezaleznie od miejsca ich zapisu, bez
wymagania recznego utrzymywania odwrotnej relacji.

Kazdy kierunek dalszej eksploracji wskazuje cel, znaczenie powiazania,
uzasadnienie i pochodzenie informacji. Rozroznia fakt bezposredni od
powiazania posredniego. Uczestnictwo w integracji nie dowodzi, ze system
realizuje dany kontekst albo odpowiada za jego utrzymanie.

Mapa wspiera eksploracje. Obowiazek sprawdzenia konkretnego zrodla zalezy
od pytania i zasad danej analizy, a nie od samego istnienia polaczenia.

Odczyt kazdego typu encji jednakowo wskazuje dostepne zrodla kodu i ich
granice. Pusta sekcja nie moze mylnie sugerowac braku zrodel tylko dlatego,
ze punkt startu ma inny typ. Skrocony wynik pozwala doczytac wszystkie
repozytoria i granice wyszukiwania.

## Miary sukcesu

- Usuniecie rzeczywistego duplikatu nie odbiera mozliwosci odnalezienia
  powiazanych bytow, wlasciciela ani wlasciwych zrodel kodu.
- Rozpoczecie od dowolnej strony relacji pozwala dotrzec do drugiej strony.
- AI otrzymuje konkretny nastepny odczyt i jego cel, nie tylko ogolna rade
  dalszego szukania.
- Ograniczenie rozmiaru odpowiedzi nie ukrywa reszty mapy: widac zakres
  pominietych danych i sposob ich doczytania.
- Walidacja odroznia niespojnosc wiedzy od nadmiarowego zapisu poprawnego
  faktu. Sam spadek liczby bledow nie jest miara powodzenia.

## Ograniczenia i non-goals

Katalog pozostaje mapa orientacyjna, a nie inwentarzem klas, endpointow,
tabel czy deploymentow. Nawigacja nie nadaje dostepu do dodatkowych zrodel
i nie zmienia zakresu uprawnien analizy. Pochodne powiazania nie staja sie
automatycznie nowymi faktami o odpowiedzialnosci lub implementacji.

Potrzeba nie obejmuje samoczynnej korekty danych ani ich publikacji bez
decyzji operatora. Przy czyszczeniu katalogu nalezy zachowac rozne znaczenia
relacji, nawet gdy dotycza tej samej pary bytow.

## Stan realizacji

Mapa odczytu zapewnia nawigacje z obu stron jednostronnie zapisanego faktu.
Zrodla kodu sa dostepne spojnie dla wszystkich typow encji, z zachowaniem
granic per scope/repository. Krotsze odpowiedzi AI pozwalaja doczytac cala
mape, pelne sciezki i granice wyszukiwania.

Po decyzji operatora opublikowano deduplikacje katalogu. Porownanie przed/po
potwierdzilo zachowanie istniejacych powiazan, ownershipu, wynikow search
i granic kodu. UI potwierdza brak bledow i ostrzezen walidacji; luki wiedzy
pozostaja jawne jako otwarte pytania. Odpowiedzi AI wygenerowano ponownie
z opublikowanych danych.

Obowiazujacy stan opisuje [model, tools i zasady uzycia Operational Context](../architecture/operational-context-model-tools-and-usage.md).
