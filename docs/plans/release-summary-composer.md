# Release summary: spójny formularz startowy

Status: done

Source need: [Release summary](../needs/release-summary.md)

## Potrzeba / dlaczego

Użytkownik zlecił uporządkowanie formularza według UX Inspectora: wspólny
wygląd pól, czytelny układ wyboru źródeł i ustawień AI oraz dolny pasek akcji.
Doprecyzował etykiety „Punkt wejścia” i „Stan docelowy”, usunięcie zamiany
kierunku oraz wygląd selektora aplikacji zgodny z ekranem referencyjnym.

## Baseline i conformance delta

Zmiana L2 ograniczona do frontendu, ponieważ application select jest wspólny
dla Release summary i Flow Explorera. Baseline: 33 testy obu stron i selektora
przechodzą. Kontrakty API, dobór modeli, typowanie refów, raport, historia,
import/export i polling pozostają bez zmian. Zmieniają się układ, etykiety,
prezentacja selektora oraz dostępne akcje formularza; usuwamy `swap` wraz
z jego testem. Nie powstaje zależność między feature'ami.

## Proponowane rozwiązanie

Reuse istniejących application/branch select, tokenów, przycisków, tooltipów
i natywnych selectów z obsługą klawiatury. Application select dostaje wspólny
wygląd: nazwa, opis, chevron, menu wyszukiwania. Trzy kolumny źródeł, dwa równe
pola AI i dolny pasek Generuj raport / Import / Export. Węższe ekrany
układają pola pionowo. Nie kopiujemy lokalnej implementacji menu UX Inspectora.

## Zakres i non-goals

Zakres: formularz Release summary, wspólny application select i aktualność
dokumentacji. Poza zakresem: zmiany backendu, mechanizmu raportowania, routingu,
konfiguracji modeli i nowy typ analizy.

## Ograniczenia i ryzyka

Zmiana shared kontrolki obejmuje także Flow Explorer: wymagane są testy obu
konsumentów i sprawdzenie widoku. Zachowujemy wybór po ID, wyszukiwanie,
blokady, odtwarzanie wybranego modelu i kierunek baseline → target. Bez zmiany
kontraktu zapisu nie powstają migratory ani nowe wersje danych.

## Kryteria akceptacji

- Pola mają spójne etykiety, wysokości i wyrównanie; przyciski są na dole.
- Punkt wejścia i stan docelowy pozostają jawne; brak funkcji zamiany kierunku.
- Application select pokazuje nazwę i opis jak na ekranie referencyjnym.
- Wybór aplikacji/refów/modelu działa klawiaturą i na wąskim ekranie.
- Testy frontendu i produkcyjny build przechodzą; aktualny widok jest dostępny lokalnie.

## Kroki

- [x] Uporządkować formularz i wspólny selektor, usunąć zamianę kierunku, zaktualizować dokumentację, uruchomić testy frontendu i build, sprawdzić oba ekrany oraz responsywność i zapisać commit. Dowód bazowy: 33 testy. Dowód końcowy: 687 testów w 88 plikach i produkcyjny build przeszły. Weryfikacja przeglądarkowa objęła Release summary, Flow Explorer, wybór aplikacji klawiaturą oraz szerokości 1280 i 390 px. Wszystkie kontrolki mają 58 px wysokości; brak poziomego przewijania formularza. Lokalna aplikacja korzysta z aktualnego bundle. Dokumentacja i instrukcje feature'a są aktualne. Zakres zatwierdzony bieżącym poleceniem użytkownika.

Pełny zestaw testów uruchomiono z lokalnym `maxWorkers: 4, isolate: true`.
Ograniczenie współbieżności eliminuje przeciążenie skutkujące timeoutami,
a izolacja rozdziela globalne mocki, które domyślny runner Angulara współdzieli
między plikami. Asercje i limity czasu pozostały bez zmian. W ramach regresji
zachowano oznaczenie loadera używane przez konsumenta Flow Explorer.
