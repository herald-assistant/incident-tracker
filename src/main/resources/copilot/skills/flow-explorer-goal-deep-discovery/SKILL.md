---
name: flow-explorer-goal-deep-discovery
description: Goal-specific template dla Flow Explorer Deep Discovery - kompleksowe zrozumienie endpointu przez Overview i cztery sekcje compact/deep.
---

# Flow Explorer Goal: Deep Discovery

Uzywaj tego skilla, gdy `goal` w promptcie ma wartosc `DEEP_DISCOVERY`.

## Cel

`DEEP_DISCOVERY` ma dac analitykowi, testerowi albo analitykowi systemowemu
samowystarczalne zrozumienie endpointu: co endpoint robi, przez jakie decyzje
przechodzi, jakie dane czyta/zapisuje, jakie walidacje sa istotne i jakie
integracje uczestnicza w flow.

Initial result ma byc kompletny dla wiekszosci potrzeb. Follow-up chat ma byc
potrzebny tylko dla doprecyzowan, nowych pytan albo luk widocznosci.

## Zasada Ogolna

Nie streszczaj kodu klasa po klasie. Zawsze tlumacz zachowanie endpointu:

- co wyzwala request,
- jaki jest glowny happy path,
- jakie decyzje albo reguly zmieniaja zachowanie,
- co moze przerwac flow,
- jaki stan jest czytany albo zmieniany,
- jakie systemy zewnetrzne dostaja sygnal albo dane,
- czego nie bylo widac w evidence.

Kod jest materialem zrodlowym, ale wynik ma czytac sie jak dokumentacja
funkcjonalno-techniczna. Nazwy klas, metod, beanow i plikow przenos do
referencji, a w tresci opisuj czynnosci systemu, reguly, decyzje, stany danych
i handoffy.

Wspieraj sie operational context i glossary. Jezeli implementacja sugeruje
ubiquitous language, ktorego brakuje w slowniku, uzyj roboczej nazwy jako
inferencji, dopisz limit albo pytanie otwarte i zglos luke przez feedback tool,
jezeli jest dostepny.

Przyklady ponizej sa CRM-specific i zanonimizowane; nie kopiuj nazw do wyniku
dla realnego endpointu.

- Zle: "`CustomerCaseController` wywoluje `CustomerCaseService.create`, potem
  `CustomerCaseRepository.save`."
- Dobrze: "Po przyjeciu zgloszenia system tworzy sprawe klienta, nadaje jej
  poczatkowy status i zapisuje ja tak, aby byla widoczna w dalszym procesie
  obslugi."
- Zle: "`EligibilityValidator` sprawdza `customerSegment` i
  `activeAgreementFlag`."
- Dobrze: "System dopuszcza kontynuacje tylko wtedy, gdy klient nalezy do
  obslugiwanego segmentu i ma aktywna relacje produktowa. Jezeli te terminy nie
  sa potwierdzone w glossary, oznacz je jako inferowane."

## Overview

`overview.markdown` ma zawierac zwarty opis:

- endpoint: metoda i path,
- glowny cel biznesowo-systemowy requestu,
- najwazniejszy flow w 3-5 zdaniach,
- najwazniejsze ograniczenie widocznosci, jezeli moze zmienic interpretacje,
- informacja, ktore sekcje sa `deep`, jezeli to pomaga odbiorcy czytac wynik.

Nie opisuj w Overview calej implementacji. Overview ma dac szybka orientacje.

## Business flow/rules

### compact

Zwróć:

- glowny przebieg requestu od wejscia HTTP do odpowiedzi,
- najwazniejsze decyzje biznesowe albo systemowe,
- efekt endpointu widoczny dla uzytkownika lub procesu,
- 1-3 source refs potwierdzajace primary flow.

### deep

Oprocz compact dodaj:

- warianty flow i warunki przejsc,
- reguly biznesowe, flagi, statusy, typy klienta/sprawy/obiektu, ktore
  zmieniaja zachowanie,
- error boundary: kiedy flow konczy sie bledem, brakiem danych albo odmowa,
- rozroznienie faktow z kodu od inferencji,
- konkretne source refs dla kazdego istotnego wariantu.

## Validations

### compact

Zwróć:

- najwazniejsze walidacje inputu albo stanu,
- co request musi miec, zeby przejsc dalej,
- co moze zostac odrzucone,
- visibility limit, jezeli walidacje nie byly widoczne w snippetach.

### deep

Oprocz compact dodaj:

- walidacje techniczne i biznesowe oddzielone jezykiem zrozumialym dla testera,
- negatywne warunki, bledy, statusy i wymagane dane,
- zaleznosci walidacji od persistence albo integracji,
- walidacje implicit, np. brak rekordu, brak konfiguracji, brak uprawnienia,
  jezeli wynikaja z kodu,
- source refs dla konkretnych metod/walidatorow.

## Persistence

### compact

Zwróć:

- czy endpoint czyta, zapisuje albo aktualizuje stan,
- najwazniejsze repozytoria/encje/tabele, jezeli sa widoczne,
- jaki stan decyduje o odpowiedzi,
- visibility limit, jezeli persistence nie bylo widoczne.

### deep

Oprocz compact dodaj:

- read/write path: jakie dane sa wyszukiwane, tworzone, aktualizowane albo
  usuwane,
- zaleznosc odpowiedzi od znalezionego/brakujacego stanu,
- transakcyjnosc, idempotencja albo kolejnosc zapisow, jezeli widoczne,
- pola/statusy/stany, ktore sa wazne dla analityka/testera,
- ryzyko niepelnej widocznosci modeli, mapperow albo repozytoriow.

Dla `deep` traktuj persistence result jako oczekiwany element wyniku, jezeli
endpoint tworzy, aktualizuje albo usuwa dane. Pokaz tabele danych
aktualizowanych, tworzonych albo usuwanych, a nie tylko liste klas
implementacyjnych.

Minimalny format tabeli w `persistence.deep`:

| Obiekt/encja albo tabela | Pole/dane | Operacja | Skad pochodzi wartosc | Transformacja/regula | Evidence | Pewnosc/limit |
| --- | --- | --- | --- | --- | --- | --- |

W kolumnie `Skad pochodzi wartosc` rozrozniaj:

- request/input endpointu,
- istniejacy stan z bazy,
- system zewnetrzny albo integracja,
- wartosc wyliczona w logice,
- wartosc domyslna, konfiguracja albo stala,
- brak widocznosci z powodu nieprzeczytanego mappera/modelu/repozytorium.

Nie wymyslaj nazw tabel ani kolumn, gdy evidence pokazuje tylko pola Java.
Jezeli widzisz adnotacje encji, DDL albo query, mozesz uzyc widocznej nazwy
tabeli/kolumny. Jezeli widzisz tylko model Java, pisz `encja/model Java` i
nazwe pola z evidence. Zrodlo wartosci wyprowadzaj z request DTO, mappera,
serwisu, repository query albo odpowiedzi integracji; gdy tego nie widac,
wpisz limit widocznosci zamiast zgadywania.

## Integrations

### compact

Zwróć:

- widoczne wywolania downstream/upstream, kolejki, eventy albo handoffy,
- cel integracji w flow endpointu,
- co dzieje sie, gdy integracja nie jest widoczna albo nie ma jej w initial
  evidence.

### deep

Oprocz compact dodaj:

- kierunek integracji i moment wywolania w flow,
- dane albo sygnaly przekazywane do systemu zewnetrznego,
- odpowiedzi/timeouty/bledy integracji, jezeli widoczne,
- zaleznosci operational context: proces, system, bounded context, owner,
  jezeli pomagaja zrozumiec handoff,
- source refs z kodu albo tools dla kazdej istotnej integracji.

## Compact Vs Deep

Sekcja `compact` ma byc krotka, ale nadal konkretna. Nie wpisuj pustego
"brak" bez wyjasnienia. Jezeli obszar nie wystepuje albo nie byl widoczny,
napisz to w sekcji i dodaj visibility limit.

Sekcja `deep` ma byc kompletna dla pracy bez follow-upu: ma zawierac warianty,
warunki, source refs, otwarte pytania i ograniczenia widocznosci.

## Antywzorce

Nie:

- dawaj ogolnikow typu "endpoint waliduje dane" bez wskazania jakich danych,
- opisuj metod pomocniczych, jezeli nie zmieniaja zachowania endpointu,
- mieszaj scenariuszy testowych jako osobnej listy; w tym celu scenariusze
  moga pojawic sie tylko jako konsekwencje flow/walidacji,
- udawaj pewnosci, gdy evidence nie pokazuje persistence albo integracji,
- zostawiaj sekcji pustej.
