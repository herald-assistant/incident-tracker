# Czytelny prompt asysty Operational Context

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

W podgladzie przygotowanego promptu reguly `operational-context-maintenance/`
znajduja sie w tym samym JSON-ie co opis operatora i katalog, pod naglowkiem
okreslajacym caly blok jako niezaufany material. Utrudnia to ocene, ktore
fragmenty sa instrukcjami aplikacji, a ktore danymi do analizy, oraz review
problematycznych runow AI.

## Baseline i conformance delta (L1)

- Baseline: `OperationalContextAssistanceJobService` sklada JSON z digestem,
  dziewiecioma dokumentami i regułami. `PromptPreparationService` sanitizuje
  calosc, renderuje jeden zwarty JSON, a job zapisuje ten prompt w historii.
- Delta: przekazac reguły z pakietu osobno; prompt pokazuje je przed blokami
  danych. Dane operatora, metadane i drzewo GitLaba, tresci GitLaba oraz
  dokumenty katalogu sa oddzielnymi sekcjami; dokumenty sa pokazane plik po
  pliku w stalej kolejnosci.
- Konsumenci: job przygotowania promptu i zapisany snapshot/historia korzystaja
  z tego samego tekstu. Copilot provider dostaje go bez zmiany kontraktu.
  Parser, source refs, preview, zapis katalogu i UI nie zmieniaja kontraktow.

## Proponowane rozwiazanie

Zachowac istniejacy typed input oraz artefakt diagnostyczny JSON, ale wyniesc
reguły do osobnego pola wejscia i renderowac finalny prompt w czytelnych
sekcjach. Jednolity JSON pozostawalby prostszy w kodzie, lecz zaciera granice
zaufania i utrudnia przeglad.

## Zakres

Feature-owned skladanie promptu, testy jego struktury i dokumentacja
obowiazujacego zachowania.

## Non-goals

Zmiana systemowej hierarchii rol Copilota, schematu draftu, tools, limitow
zrodel, zapisow katalogu oraz wygladu panelu.

## Ograniczenia i ryzyka

Sekcje w jednym prompcie poprawiaja czytelnosc i wskazuja pochodzenie regul,
ale nie stanowia osobnej roli systemowej. Nadal obowiazuja parser, walidator,
read-only scope i jawna decyzja operatora. Formatowany JSON powieksza prompt;
material nie moze byc obcinany po cichu. Nie zapisujemy niefiltrowanych danych.

## Kryteria akceptacji

Przygotowany prompt pokazuje wszystkie 11 reguł ponad danymi i wszystkie
dziewiec dokumentow katalogu osobno, z jednym digestem. Sekcje zrodel
oddzielaja metadane/drzewo od tresci plikow. Diagnostyczny JSON nie miesza
regul z katalogiem. Brak kompletu regul blokuje run, a source refs i
sanityzacja zachowuja dotychczasowe dzialanie.

## Kroki

- [x] Krok 1: Ustalic baseline, pochodzenie regul i konsumentow promptu.
  Zweryfikowano w catalog material, jobie, providerze i testach feature'a.
- [x] Krok 2: Rozdzielic regul i dane w input oraz prompcie, zachowac source
  refs i zabezpieczenie przed brakujacymi regulami. Weryfikacja: test
  `OperationalContextAssistancePromptPreparationServiceTest`.
- [x] Krok 3: Zaktualizowac opis runtime i uruchomic testy feature'a oraz
  `mvn -q test`. Obie komendy przeszly; pelny backend mial 1525 testow,
  0 bledow, 0 porazek i 1 pominiety. Po dodaniu osobnego testu izolacji
  naglowka w niezaufanym JSON-ie ponownie przeszedl test promptu.
