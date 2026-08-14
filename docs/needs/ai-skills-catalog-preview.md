# Podglad katalogu AI Skills

Status: done (historyczny pierwszy inkrement read-only)

Aktualny kontrakt ekranu, persistence oraz API jest opisany w dokumentacji
architektury. Ponizsze ograniczenia dotyczyly pierwszego wdrozenia podgladu i
nie sa aktualnym kontraktem `Platform / AI Skills`.

## Uzytkownik i decyzja

Operator platformy chce szybko sprawdzic, jakie skille AI sa aktualnie
dostepne, za co odpowiadaja i jakie instrukcje zawieraja. Podglad ma ulatwiac
zrozumienie zachowania feature'ow i przeglad katalogu bez przechodzenia do
filesystemu lub kodu aplikacji.

## Problem dzisiaj

Skille sa wspolnym zasobem runtime, ale platforma nie pokazuje ich w UI.
Uzytkownik musi znac lokalizacje plikow, recznie przeszukiwac katalog oraz sam
rozpoznawac powiazane workflow. Operational Context ma juz czytelny widok
operatorski, natomiast analogiczny, bezpieczny podglad skilli nie istnieje.

## Oczekiwany rezultat

- AI Skills jest osobnym ekranem w grupie `Platform`.
- Ekran pokazuje efektywny katalog runtime, a nie alternatywna kopie danych.
- Uzytkownik moze wyszukiwac i filtrowac skille oraz przejsc do czytelnego
  podgladu wybranego `SKILL.md`.
- Widok szczegolowy udostepnia renderowana tresc i surowy Markdown.
- Prezentacja korzysta z istniejacego shella, gestosci, paneli, status stripow,
  typografii i zachowan responsywnych platformy.
- Tryb read-only jest jawny w UI.

## Jawna widocznosc i ograniczenia

- UI pokazuje nazwe, opis i tresc skilla dostepna w efektywnym katalogu
  runtime.
- UI nie pokazuje absolutnej sciezki lokalnego katalogu ani innych danych
  filesystemu.
- Grupowanie wedlug workflow i odpowiedzialnosci jest pomocnicza projekcja UX,
  a nie deklaracja runtime ani mechanizm wyboru skilli przez feature.
- Brak skilla albo niedostepny katalog jest pokazany jako kontrolowany blad,
  bez stack trace i szczegolow lokalnego srodowiska.

## Success metrics

- operator znajduje skill po nazwie lub opisie bez znajomosci filesystemu,
- przejscie z listy do tresci wymaga jednego wyboru,
- bezposredni URL otwiera wskazany skill,
- ekran pozostaje czytelny dla aktualnego katalogu oraz na mniejszych
  szerokosciach,
- API i UI nie daja mozliwosci zmiany katalogu.

## Non-goals

- tworzenie, edycja, usuwanie albo przesylanie skilli,
- uruchamianie skilla z ekranu,
- przypisywanie skilli per feature, run lub uzytkownik,
- zmiana promptow, workflow, allowlist tools albo sesji Copilota,
- hot reload i reczne odswiezanie katalogu bez restartu aplikacji,
- historia, wersjonowanie, diff, walidator autora lub approval workflow,
- ujawnianie sciezek `tdw-data`, katalogow stagingowych lub innych szczegolow
  storage.

## Ryzyka

- nazwa skilla nie zawsze pozwala jednoznacznie okreslic workflow; UI musi miec
  bezpieczna kategorie fallback zamiast ukrywac nieznane skille,
- bardzo dluga tresc wymaga stabilnego scrollowania i czytelnego renderowania,
- podglad nie moze stac sie pozornym mechanizmem konfiguracji lub selekcji
  skilli dla feature'a.
