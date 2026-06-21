---
name: flow-explorer-goal-risk-detection
description: Goal-specific template dla Flow Explorer Risk detection - wykrywanie ryzyk, luk widocznosci, regresji i pytan otwartych w czterech sekcjach compact/deep.
---

# Flow Explorer Goal: Risk Detection

Uzywaj tego skilla, gdy `goal` w promptcie ma wartosc `RISK_DETECTION`.

## Cel

`RISK_DETECTION` ma dac testerowi, analitykowi systemowemu, BA albo PO
czytelny obraz ryzyk zwiazanych z endpointem i jego flow. Initial result ma
pomoc ocenic, gdzie zachowanie moze byc niejasne, podatne na regresje,
zalezne od danych albo systemow zewnetrznych oraz ktore pytania trzeba
wyjasnic przed testami, zmiana albo odbiorem.

Nie zwracaj osobnego top-level pola `risks`. Ryzyka wpisuj w
`overview.markdown` i cztery stale sekcje kontraktu.

## Zasada Ogolna

Kazdy istotny wpis ryzyka oznacz jednym z typow:

- `Fakt z evidence` - wynika bezposrednio z kodu, deterministic contextu albo
  operational context,
- `Inferencja` - logiczny wniosek z evidence, ale nie bezposrednia deklaracja,
- `Luka widocznosci` - initial evidence nie pokazuje wystarczajacego fragmentu,
- `Pytanie otwarte` - wymaga potwierdzenia przez zespol, analityka albo
  dodatkowy follow-up.

Nie prezentuj hipotez jako faktow. Jezeli ryzyko wynika z braku danych, nazwij
to jako luka widocznosci i wskaz, czego brakuje.

Opisuj ryzyka jezykiem skutku dla procesu, testow, danych albo handoffu. Nie
rob z nazw klas/metod glownego ryzyka. Jezeli kod sugeruje wazne pojecie
domenowe, sprawdz operational context/glossary; gdy terminu brakuje, oznacz
nazwe jako inferowana i zglos luke przez feedback tool jako
`missing_operational_context`, jezeli tool jest dostepny.

Kazda sekcja ma patrzec na inny rodzaj ryzyka:

- `BUSINESS_FLOW_RULES`: niejasne reguly, warianty procesu, decyzje i skutki
  biznesowe,
- `VALIDATIONS`: brakujace albo niejasne walidacje, odrzucenia, statusy i dane
  wejscia,
- `PERSISTENCE`: stan danych, transakcje, read/write, duplikaty, idempotencja i
  regresje danych,
- `INTEGRATIONS`: handoffy, zaleznosci upstream/downstream, timeouty, retry,
  kolejki, eventy i odpowiedzialnosci systemow.

Nie duplikuj tego samego ryzyka w kilku sekcjach. Jezeli ryzyko dotyka kilku
sekcji, w kazdej sekcji opisz tylko jej perspektywe.

## Overview

`overview.markdown` ma zawierac:

- endpoint: metoda i path,
- krotka ocene glownego obszaru ryzyka,
- 3-5 najwazniejszych ryzyk w jezyku nietechnicznym,
- informacje, ktore sekcje sa `deep` i dlatego zawieraja wiecej szczegolow,
- najwazniejsza luke widocznosci, jezeli istnieje.

Overview nie jest pelnym rejestrem ryzyk. Ma ustawic priorytet czytania.

## Business flow/rules

### compact

Zwroc:

- 2-4 najwazniejsze ryzyka dotyczace sensu biznesowego endpointu,
- warianty procesu albo decyzje, ktore moga byc niejasne,
- mozliwy skutek dla uzytkownika, procesu albo testow,
- typ ryzyka: fakt, inferencja, luka widocznosci albo pytanie otwarte,
- source refs albo visibility limit.

### deep

Oprocz compact dodaj:

- ryzyka alternatywnych sciezek biznesowych,
- reguly, ktore wygladaja na ukryte w kodzie, konfiguracji albo upstreamie,
- ryzyka regresji po zmianie warunku biznesowego,
- rozroznienie: co kod potwierdza, co tylko sugeruje, czego nie widac,
- konkretne pytania do analityka albo zespolu, jezeli oczekiwane zachowanie nie
  jest jednoznaczne.

## Validations

### compact

Zwroc:

- ryzyka brakujacych albo niejasnych walidacji danych wejscia,
- ryzyka odrzucenia zlego statusu, braku rekordu, pustego pola albo konfliktu,
- widoczne miejsca, w ktorych request moze przejsc mimo niepewnych danych,
- visibility limit, jezeli walidacje nie byly widoczne.

### deep

Oprocz compact dodaj:

- macierz ryzyk dla danych poprawnych, niepoprawnych i granicznych,
- rozroznienie walidacji technicznych i biznesowych,
- ryzyka niespojnych kodow bledow albo komunikatow,
- ryzyka braku walidacji uprawnien, stanu, konfiguracji albo zaleznosci
  zewnetrznej, jezeli wynikaja z evidence,
- source refs dla konkretnych guard clauses, validatorow albo luk.

## Persistence

### compact

Zwroc:

- ryzyka dotyczace danych czytanych albo zapisywanych przez endpoint,
- mozliwe problemy z brakiem rekordu, duplikatem, nieaktywnym statusem albo
  wielokrotnym wywolaniem,
- czy endpoint wyglada na read-only czy zmieniajacy stan,
- widoczne repozytoria/encje tylko wtedy, gdy pomagaja zrozumiec ryzyko.

### deep

Oprocz compact dodaj:

- ryzyka transakcyjnosci, idempotencji, kolejnosci zapisow albo rollbacku,
- ryzyka regresji danych po zmianie mappera, repozytorium albo encji,
- ryzyka niespojnosci miedzy stanem przed requestem i po requestcie,
- luki widocznosci modeli, mapperow albo repozytoriow, ktore trzeba
  potwierdzic,
- minimalne dane, ktore trzeba sprawdzic w testach, zeby ryzyko zamknac.

## Integrations

### compact

Zwroc:

- ryzyka zaleznosci od systemu zewnetrznego, kolejki, eventu albo handoffu,
- co moze pojsc zle przy braku odpowiedzi, bledzie downstream/upstream albo
  niekompletnej konfiguracji,
- operational context hints: system, owner, handoff route, jezeli pomagaja,
- visibility limit, jezeli integracji nie widac.

### deep

Oprocz compact dodaj:

- ryzyka timeoutow, retry, duplikacji eventu, opoznien i niespojnosci statusu,
- ryzyka kontraktu danych przekazywanych do integracji,
- ryzyka odpowiedzialnosci: ktory system/zespol powinien potwierdzic zachowanie,
- co trzeba zamockowac albo zasymulowac, zeby ryzyko przetestowac,
- pytania otwarte dla integracji, jezeli evidence nie pokazuje pelnego handoffu.

## Format Sekcji

W polu `markdown` kazdej sekcji uzywaj zwartych list. Dla kazdego istotnego
ryzyka podaj:

- typ: `Fakt z evidence`, `Inferencja`, `Luka widocznosci` albo `Pytanie otwarte`,
- ryzyko,
- skutek,
- jak je zweryfikowac albo zamknac,
- source ref albo visibility limit.

Compact ma byc zwarty, ale nie ogolnikowy. Deep ma byc wystarczajaco konkretne,
zeby tester albo analityk mogl przygotowac decyzje, pytania albo testy bez
typowego follow-upu.

## Antywzorce

Nie:

- tworz generycznej listy ryzyk niezwiazanych z endpointem,
- prezentuj braku evidence jako faktu,
- ukrywaj niepewnosci pod wysokim confidence,
- powtarzaj tego samego ryzyka w kazdej sekcji,
- skupiaj sie na nazwach klas bez wyjasnienia skutku dla procesu albo testow,
- wymyslaj awarii integracji, jezeli nie ma evidence ani sensownej inferencji,
- przywracaj legacy top-level pola `risksAndEdgeCases` albo `testScenarios`.
