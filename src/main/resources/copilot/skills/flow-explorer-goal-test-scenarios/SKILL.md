---
name: flow-explorer-goal-test-scenarios
description: Goal-specific template dla Flow Explorer Test scenarios - przygotowanie scenariuszy testowych, danych i regresji w aktywnych sekcjach sectionModes compact/deep.
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
wpisuj w `overview.markdown` i aktywne sekcje kontraktu wynikajace z
`sectionModes`.

## Zasada Ogolna

Kazda aktywna sekcja ma odpowiadac na inne pytanie testowe:

- `FUNCTIONAL_FLOW`: jakie sciezki funkcjonalne i warianty procesu pokryc,
- `VALIDATIONS`: jakie dane, stany i bledy powinny zostac odrzucone,
- `PERSISTENCE`: jaki setup danych, stan przed/po i regresje danych sprawdzic,
- `INTEGRATIONS`: jakie zaleznosci systemowe, handoffy i awarie integracji
  trzeba uwzglednic.

Nie duplikuj tej samej listy scenariuszy w kilku sekcjach. Jezeli scenariusz
dotyka kilku sekcji, w kazdej sekcji opisz tylko jej perspektywe.

Pisz scenariusze jezykiem procesu i danych biznesowych, nie jezykiem klas,
metod ani repozytoriow. Kod jest evidence dla setupu i oczekiwanego rezultatu,
ale nazwy implementacyjne trzymaj w source refs. Jezeli potrzebujesz nazwy
domenowej, sprawdz operational context/glossary; brak wartosciowego terminu
zglos przez feedback tool jako `missing_operational_context`, jezeli tool jest
dostepny.

## Overview

`overview.markdown` ma zawierac:

- endpoint: metoda i path,
- krotki opis celu testowego runu,
- najwazniejszy happy path,
- najwazniejsze obszary ryzyka testowego wynikajace z evidence,
- informację, ktore sekcje sa `deep` i dlatego zawieraja wiecej szczegolow.

Overview nie jest pelnym planem testow. Ma ustawic kontekst i priorytet.

## Functional flow

Sekcja `FUNCTIONAL_FLOW` zawsze trzyma strukture z kontraktu wyniku:
`Cel funkcjonalny`, `Flow krok po kroku`, `Koordynacja i routing`,
`Kalkulacje i reguly funkcjonalne`, `Rozgalezienia zalezne od kontekstu`,
`Handoffy i efekty uboczne`, `Akcent goal`.
Nie zmieniaj nazw punktow. W `TEST_SCENARIOS` punkt `Akcent goal` ma wskazac
konkretne sciezki i warunki do pokrycia testami.
Evidence, source refs i ograniczenia widocznosci przekazuj w osobnych polach
`sourceRefs`, `visibilityLimits` i `openQuestions`, nie w glownym markdownie.
Kazdy punkt `FUNCTIONAL_FLOW` formatuj jako czytelna liste albo kroki, a nie
jako jeden dlugi akapit. Poziom szczegolow dopasuj do zlozonosci flow: nie
pomijaj istotnych sciezek, regul, kalkulacji, rozgalezien ani handoffow tylko po
to, zeby odpowiedz byla krotsza.

### compact

Zwróć:

- najwazniejsze sciezki flow do pokrycia testami; jezeli flow jest zlozony,
  pokaz tyle sciezek, ile trzeba do uzytecznego pokrycia zgodnego z `goal`,
- kolejnosc etapow, ktore tester ma odtworzyc: auth/authz, walidacja inputu,
  pobranie danych, kalkulacje/decyzje, wzmianka o persistence,
  event/request/odpowiedz,
- warunki funkcjonalne, ktore zmieniaja oczekiwany wynik,
- oczekiwane rezultaty widoczne dla uzytkownika albo procesu.

### deep

Oprocz compact dodaj:

- warianty funkcjonalne i alternatywne sciezki,
- warunki wejscia, statusy, typy klienta/sprawy/obiektu albo flagi, ktore
  powinny byc przygotowane w danych testowych,
- koordynacje/routing zalezne od inputu, dociagnietych danych, stalych,
  konfiguracji albo kontekstu procesu,
- kalkulacje, reguly funkcjonalne i transformacje, ktore zmieniaja oczekiwany
  rezultat testu,
- oczekiwany rezultat dla kazdego wariantu,
- testy regresyjne dla decyzji funkcjonalnych,
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

Sekcja `INTEGRATIONS` w `TEST_SCENARIOS` dotyczy tylko testowania zaleznosci
poza analizowanym komponentem/systemem. Nie tworz przypadkow testowych dla
wewnetrznych eventow, listenerow, mapperow ani komunikacji miedzy klasami, chyba
ze evidence pokazuje zewnetrzny broker, destination, topic, queue, binding albo
handoff do innego systemu.

### compact

Zwróć:

- dla kazdego widocznego zewnetrznego systemu albo kanalu: nazwe, typ
  integracji, adres/path/destination/topic/queue/binding i cel w testowanym
  flow,
- czy test wymaga realnego systemu, mocka/stuba, kolejki, eventu, fixture albo
  kontraktu testowego,
- co trzeba zasymulowac: request/response, payload eventu, naglowki, status,
  timeout, blad albo brak odpowiedzi, jezeli sa widoczne,
- co wpisac jako limit, jezeli nie widac adresu, payloadu, naglowkow albo
  zachowania bledow integracji.

### deep

Oprocz compact dodaj:

- przypadki sukcesu i bledu dla kazdego konkretnego kontraktu zewnetrznego,
- setup danych i stubow dla HTTP path/URL template, destination/topic/queue,
  bindingu, payloadu, naglowkow i statusow,
- timeout, brak odpowiedzi, blad downstream/upstream, retry, fallback, DLQ,
  duplikacje eventu albo idempotencje, jezeli sa widoczne,
- dane przekazywane w handoffie, w tym pola payloadu/eventu istotne dla
  asercji testowej,
- potrzebe mocka/stuba/fixture dla systemu zewnetrznego wraz z tym, co ma
  zwrocic albo odebrac,
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
  aktywnymi sekcjami,
- powtarzaj tego samego scenariusza w kazdej sekcji,
- wymyslaj kody bledow, jezeli nie sa widoczne,
- opisuj testow technicznych bez wartosci dla analityka/testera,
- ukrywaj, ze do pelnego testu potrzebne sa dane albo zaleznosc zewnetrzna,
- przywracaj legacy top-level pola `testScenarios` albo `risksAndEdgeCases`.
