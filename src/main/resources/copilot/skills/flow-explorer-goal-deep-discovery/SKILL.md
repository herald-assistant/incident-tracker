---
name: flow-explorer-goal-deep-discovery
description: Goal-specific template dla Flow Explorer Deep Discovery - kompleksowe zrozumienie endpointu przez Overview i aktywne sekcje sectionModes compact/deep.
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
- jakie decyzje albo warunki funkcjonalne zmieniaja zachowanie,
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

## Functional flow

Sekcja `FUNCTIONAL_FLOW` zawsze trzyma strukture z kontraktu wyniku:
`Cel funkcjonalny`, `Flow krok po kroku`, `Koordynacja i routing`,
`Kalkulacje i reguly funkcjonalne`, `Rozgalezienia zalezne od kontekstu`,
`Handoffy i efekty uboczne`, `Akcent goal`.
Nie zmieniaj nazw punktow. W `DEEP_DISCOVERY` punkt `Akcent goal` ma pokazac
najwazniejsze warianty flow i ich znaczenie dla zrozumienia endpointu.
Evidence, source refs i ograniczenia widocznosci przekazuj w osobnych polach
`sourceRefs`, `visibilityLimits` i `openQuestions`, nie w glownym markdownie.
Kazdy punkt `FUNCTIONAL_FLOW` formatuj jako czytelna liste albo kroki, a nie
jako jeden dlugi akapit. Poziom szczegolow dopasuj do zlozonosci flow: nie
pomijaj istotnych etapow, regul, kalkulacji, rozgalezien ani handoffow tylko po
to, zeby odpowiedz byla krotsza.

### compact

Zwróć:

- glowny przebieg requestu od wejscia HTTP do odpowiedzi albo handoffu,
- kolejnosc najwazniejszych etapow: auth/authz, walidacja inputu, pobranie
  danych, kalkulacje/decyzje, wzmianka o persistence, event/request/odpowiedz,
- najwazniejsze decyzje domenowe albo systemowe widoczne w kodzie,
- efekt endpointu widoczny dla uzytkownika lub procesu.

### deep

Oprocz compact dodaj:

- warianty flow i warunki przejsc w kolejnosci wystapienia,
- warunki funkcjonalne, flagi, statusy, typy klienta/sprawy/obiektu, ktore
  zmieniaja zachowanie,
- koordynacje i routing oparte na input, dociagnietych danych, stalych,
  konfiguracji, statusach albo kontekście procesu,
- szczegolowe kalkulacje, reguly funkcjonalne, klasyfikacje, transformacje i
  priorytety widoczne w kodzie,
- error boundary: kiedy flow konczy sie bledem, brakiem danych albo odmowa,
- rozroznienie faktow z kodu od inferencji,
- persistence i integracje tylko jako etap flow, a nie szczegoly nalezace do
  sekcji `PERSISTENCE` albo `INTEGRATIONS`.

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
endpoint tworzy, aktualizuje albo usuwa dane. Pokaz biznesowe mapowanie ORM:
ktora tabela i kolumna sa zapisywane oraz skad pochodzi zapisywana wartosc.
Nie pokazuj listy klas implementacyjnych jako rezultatu analizy.

Minimalny format tabeli w `persistence.deep`:

| TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS |
| --- | --- | --- | --- |

`SOURCE` jest obowiazkowe dla kazdej zapisywanej kolumny i musi byc jedna z
ponizszych wartosci:

- `GENERATED` - wartosc powstaje po stronie persistence/systemu bez danych od
  uzytkownika,
- `REQUEST` - wartosc pochodzi z requestu endpointu,
- `CALCULATED` - wartosc jest wyliczona przez logike analizowanego flow,
- biznesowa nazwa systemu albo komponentu, np. `System X`, jezeli zapisywana
  wartosc pochodzi z dedykowanego systemu zewnetrznego.

`SOURCE DETAILS` opisuje sciezke danych w jezyku przyjaznym dla
analityka/testera, np. `req.body.customer.email`, `param.customer.name`,
`resp.address.street`, albo krotka regule dla `CALCULATED`. Dla zrodel
zewnetrznych uzywaj biznesowej nazwy systemu lub komponentu z operational
context, glossary, handoffu albo widocznego kontraktu integracji; nie uzywaj
nazwy klienta technicznego, klasy, beana ani repozytorium jako `SOURCE`.

Nie koncz `PERSISTENCE=DEEP` bez ustalenia `SOURCE` dla zapisywanych danych.
Zrodlo wartosci wyprowadzaj z request DTO, mappera, serwisu, odpowiedzi
integracji, konfiguracji albo warunku funkcjonalnego. Jezeli brakuje widocznosci,
czytaj kolejne waskie fragmenty kodu zwiazane z flow, az potwierdzisz zrodlo
albo trafisz na twardy limit widocznosci. Dopiero wtedy dodaj
`visibilityLimits` albo `openQuestions`; nie wpisuj technicznego placeholdera
w `SOURCE`.

Mapowanie ORM, DDL albo query moze sluzyc do ustalenia `TABLE_NAME` i `COLUMN`,
ale szczegoly implementacyjne nie sa trescia tabeli wynikowej. Nie wstawiaj do
tabeli adnotacji, nazw klas, metod ani frameworkowych szczegolow persistence.
Evidence trzymaj w `sourceRefs`, a nie w kolumnach `SOURCE` lub
`SOURCE DETAILS`.

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
