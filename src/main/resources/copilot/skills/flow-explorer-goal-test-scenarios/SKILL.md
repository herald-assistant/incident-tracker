---
name: flow-explorer-goal-test-scenarios
description: Goal-specific template dla Flow Explorer Test scenarios - przygotowanie scenariuszy testowych, danych i regresji w czterech sekcjach compact/deep.
---

# Flow Explorer Goal: Test Scenarios

Uzywaj tego skilla, gdy `goal` w promptcie ma wartosc `TEST_SCENARIOS`.

## Cel

`TEST_SCENARIOS` ma dac testerowi, analitykowi systemowemu albo osobie
przygotowujacej odbior gotowy material do planowania testow endpointu.
Initial result ma wskazac, co przetestowac, jakich danych potrzeba, ktore
warunki sa negatywne, jakie zaleznosci moga blokowac test i gdzie sa luki
widocznosci.

Nie zwracaj osobnego top-level pola `testScenarios`. Scenariusze testowe
wpisuj w `overview.markdown` i cztery stale sekcje kontraktu.

## Zasada Ogolna

Kazda sekcja ma odpowiadac na inne pytanie testowe:

- `BUSINESS_FLOW_RULES`: jakie sciezki biznesowe i warianty procesu pokryc,
- `VALIDATIONS`: jakie dane, stany i bledy powinny zostac odrzucone,
- `PERSISTENCE`: jaki setup danych, stan przed/po i regresje danych sprawdzic,
- `INTEGRATIONS`: jakie zaleznosci systemowe, handoffy i awarie integracji
  trzeba uwzglednic.

Nie duplikuj tej samej listy scenariuszy w kilku sekcjach. Jezeli scenariusz
dotyka kilku sekcji, w kazdej sekcji opisz tylko jej perspektywe.

## Overview

`overview.markdown` ma zawierac:

- endpoint: metoda i path,
- krotki opis celu testowego runu,
- najwazniejszy happy path,
- najwazniejsze obszary ryzyka testowego wynikajace z evidence,
- informację, ktore sekcje sa `deep` i dlatego zawieraja wiecej szczegolow.

Overview nie jest pelnym planem testow. Ma ustawic kontekst i priorytet.

## Business flow/rules

### compact

Zwróć:

- 2-4 najwazniejsze scenariusze procesowe/happy path,
- warunki biznesowe, ktore zmieniaja oczekiwany wynik,
- oczekiwane rezultaty widoczne dla uzytkownika albo procesu,
- source refs potwierdzajace flow.

### deep

Oprocz compact dodaj:

- warianty biznesowe i alternatywne sciezki,
- warunki wejscia, statusy, typy klienta/sprawy/obiektu albo flagi, ktore
  powinny byc przygotowane w danych testowych,
- oczekiwany rezultat dla kazdego wariantu,
- testy regresyjne dla decyzji biznesowych,
- pytania otwarte, jezeli kod nie pokazuje oczekiwanego zachowania.

## Validations

### compact

Zwróć:

- najwazniejsze negatywne przypadki danych wejściowych,
- wymagane pola albo stany,
- oczekiwany sposob odrzucenia requestu, jezeli jest widoczny,
- visibility limit, jezeli walidacje nie byly widoczne.

### deep

Oprocz compact dodaj:

- macierz danych poprawnych/niepoprawnych,
- walidacje techniczne i biznesowe jako osobne grupy testow,
- edge case'y typu brak rekordu, pusty identyfikator, zly status, konflikt
  stanu, brak uprawnienia, brak konfiguracji, jezeli wynikaja z evidence,
- source refs dla konkretnych walidatorow albo guard clauses,
- pytania do analityka albo zespolu, jezeli oczekiwany kod bledu nie jest
  widoczny.

## Persistence

### compact

Zwróć:

- jakie dane trzeba przygotowac przed testem,
- jaki stan endpoint czyta albo zmienia,
- co sprawdzic po wykonaniu requestu,
- widoczne repozytoria/encje, jezeli sa istotne dla setupu.

### deep

Oprocz compact dodaj:

- konkretne preconditions danych dla happy path i negative path,
- oczekiwane zmiany stanu po requestcie,
- regresje danych: duplikaty, brak rekordu, nieaktywny rekord, konflikt
  statusow, wielokrotne wywolanie,
- transakcyjnosc/idempotencje, jezeli widoczna,
- luki widocznosci modeli, mapperow albo repozytoriow, ktore trzeba
  potwierdzic poza initial evidence.

## Integrations

### compact

Zwróć:

- czy test wymaga systemu zewnetrznego, kolejki, eventu albo mocka/stuba,
- co trzeba zasymulowac, jezeli integracja jest widoczna,
- co wpisac jako limit, jezeli integracji nie widac w evidence.

### deep

Oprocz compact dodaj:

- przypadki sukcesu i bledu integracji,
- timeout, brak odpowiedzi, blad downstream/upstream albo retry, jezeli sa
  widoczne,
- dane przekazywane w handoffie,
- potrzebe mocka/stuba/fixture dla systemu zewnetrznego,
- operational context hints: system, owner, handoff route, jezeli pomagaja
  zorganizowac test.

## Format Sekcji

W polu `markdown` kazdej sekcji uzywaj krotkich list albo podpunktow w
Markdown. Dla kazdego istotnego scenariusza podaj:

- warunek/setup,
- akcje,
- oczekiwany rezultat,
- source ref albo visibility limit.

Compact ma byc zwarty. Deep ma byc wystarczajaco konkretny, zeby tester mogl
na tej podstawie przygotowac przypadki testowe bez typowego follow-upu.

## Antywzorce

Nie:

- tworz jednej generycznej listy "happy path / negative path" bez powiazania z
  czterema sekcjami,
- powtarzaj tego samego scenariusza w kazdej sekcji,
- wymyslaj kody bledow, jezeli nie sa widoczne,
- opisuj testow technicznych bez wartosci dla analityka/testera,
- ukrywaj, ze do pelnego testu potrzebne sa dane albo zaleznosc zewnetrzna,
- przywracaj legacy top-level pola `testScenarios` albo `risksAndEdgeCases`.
