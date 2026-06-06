---
name: incident-functional-analysis
description: Kontrakt Functional Analysis v1 dla pola functionalAnalysis: wynik dla analityka biznesowo-systemowego, z kontekstem systemu, procesu, bounded contextu i handoffu, bez szumu implementacyjnego.
---

# Skill Analizy Funkcjonalnej Incydentu

Uzywaj tego skilla dla pola `functionalAnalysis` w kazdym poczatkowym wyniku
analizy incydentu.

Odbiorca to analityk biznesowo-systemowy. Moze znac reguly biznesowe,
wysokopoziomowa architekture i ownership, ale nie zna kodu.

Celem jest pomoc analitykowi zrozumiec, gdzie incydent znajduje sie w systemie
i jak go poprawnie przekazac dalej. Nie pisz tutaj developerskiego handoffu.

## Rola Wobec Orkiestratora

Ten skill jest kontraktem wyniku dla `functionalAnalysis`. Orkiestrator uzywa
go po researchu flow, klasyfikacji bledu i zebraniu wystarczajacego evidence,
aby przetlumaczyc ustalenia na jezyk analityka biznesowo-systemowego.

Nie zaczynaj diagnostyki od nowa i nie uruchamiaj samodzielnie eksploracji
tools. Jezeli wynik nie da sie wypelnic, nazwij brakujace evidence i ogranicz
confidence zamiast dopowiadac proces albo ownership.

## Wejscie Oczekiwane Od Orkiestratora

Przyjmij:

- result-sufficient use-case flow,
- detected problem i failure point,
- klasyfikacje bledu oraz najwazniejsza hipoteze przyczynowa,
- evidence ledger: co jest potwierdzone, co jest hipoteza, czego nie widac,
- operational context: system, proces, bounded context, integracje, glossary,
  owner albo handoff route,
- material z GitLab/DB/runtime/downstream tylko w takim zakresie, w jakim
  trzeba go przetlumaczyc na znaczenie funkcjonalne.

## Czego Ten Skill Nie Diagnozuje

Nie diagnozuj tutaj:

- root cause od zera,
- szczegolowego code path jako materialu dla developera,
- predykatow SQL, mapperow, retry logic albo konfiguracji runtime,
- ownershipu bez katalogu albo innego evidence,
- naprawy technicznej.

Jezeli te informacje sa potrzebne, wroc do orkiestratora albo do
`technicalAnalysis` z limitation.

## Wklad Do Wyniku

Ten skill wypelnia tylko pole `functionalAnalysis`: system, proces, bounded
context, normalny flow, miejsce przerwania, znaczenie funkcjonalne, handoff i
ograniczenia widocznosci z perspektywy analityka.

## Minimalny Poziom Jakosci

`functionalAnalysis` musi byc:

- napisane po polsku,
- zrozumiale bez czytania kodu Java,
- ugruntowane w evidence incydentu i operational context,
- jawne co do systemu, procesu, bounded contextu i handoffu, gdy sa znane,
- wystarczajaco konkretne, zeby analityk mogl skierowac sprawe,
- wolne od root-cause claims opartych tylko na katalogu,
- jasne co do brakujacej widocznosci.

Jezeli wartosc jest nieznana, zachowaj sekcje i uzyj jednej z wartosci:

- `Nie ustalono`
- `Nie dotyczy`
- `Brak danych w evidence`
- `Hipoteza, wymaga potwierdzenia`

## Zasady Evidence

Uzywaj artefaktow incydentu jako podstawowego zrodla prawdy.

Operational Context sluzy do:

- kanonicznych nazw systemow,
- slownictwa procesu i bounded contextu,
- glossary i lokalnego jezyka,
- integracji oraz kontekstu upstream/downstream,
- guidance handoffu i ownershipu,
- code-search scope jako kontekstu pomocniczego.

Operational Context nie jest dowodem root cause. Moze wyjasnic, gdzie incydent
najprawdopodobniej lezy i jak go przekazac, ale twierdzenia o awarii musza byc
wspierane przez logi, kod, runtime albo DB evidence.

Szczegolow GitLaba uzywaj tylko do tlumaczenia implementacji na znaczenie
funkcjonalne. Nie zamieniaj tego pola w opis klas i metod.

## Format Wyniku

Uzyj dokladnie tej struktury top-level, w tej kolejnosci.

````markdown
# Functional Analysis v1: <krotki tytul dla analityka>

## 1. Gdzie jestesmy w systemie

| Pole | Wartosc |
|---|---|
| System / aplikacja | <kanoniczny system/aplikacja albo Nie ustalono> |
| Proces | <dotkniety proces albo Nie ustalono> |
| Bounded context | <bounded context albo Nie ustalono> |
| Integracja / downstream | <istotna integracja/system albo Nie dotyczy> |
| Wlasciciel / handoff | <team/owner/route albo Nie ustalono> |

## 2. Co ten fragment robi funkcjonalnie

<Wyjasnij capability biznesowa albo systemowa w 4-8 krotkich zdaniach.
Wspomnij obiekt biznesowy, decyzje, status, walidacje, integracje, event albo
przeplyw danych. Preferuj jezyk procesu zamiast jezyka kodu.>

## 3. Co sie stalo w tym incydencie

- **Objaw:** <co pokazuje evidence, jezykiem operatora/analityka>
- **Miejsce przerwania flow:** <gdzie przerwany jest proces/system flow>
- **Skutek funkcjonalny:** <co nie moze isc dalej, moze byc opoznione, odrzucone albo zle przekazane>

## 4. Dlaczego to ma znaczenie

<Wyjasnij wplyw na ciaglosc procesu, spojnosci danych, user/customer
experience, downstream systems, SLA, prace reczna albo handoff. Jezeli impact
nie jest potwierdzony, napisz to wprost.>

## 5. Komu to przekazac i po co

| Pole | Wartosc |
|---|---|
| Sugerowany odbiorca | <team/system owner/rola albo Nie ustalono> |
| Powod przekazania | <dlaczego ten odbiorca jest istotny> |
| Pierwsze pytanie / akcja | <jedna konkretna weryfikacja/akcja dla odbiorcy> |

## 6. Co jest potwierdzone, a czego nie wiemy

**Potwierdzone:**
- <fakt ugruntowany w evidence>

**Niepotwierdzone / brak widocznosci:**
- <brakujace evidence albo ograniczenie>
````

## Zasady Pisania

- Zacznij od procesu widocznego dla uzytkownika albo systemu, nie od exceptiona.
- Identyfikatorow kodu uzywaj tylko jako anchorow, np. "evidence wskazuje na
  klase `X`", nie jako glownego opisu.
- Wyjasniaj lokalny jargon, gdy pochodzi z glossary albo bounded contextu.
- Nie twierdz, ze znasz team, proces albo context, jezeli evidence incydentu
  lub tool results nie wspieraja dopasowania katalogowego.
- Jezeli incydent jest techniczny, ale funkcjonalnie istotny, najpierw opisz
  normalny flow biznesowo-systemowy, potem jego przerwanie.
- Jezeli problem lezy poza analizowanym systemem, napisz, jakie evidence trzeba
  przekazac do odbierajacego systemu albo zespolu.

## Antywzorce

Nie:

- pisz tylko "blad w klasie X" albo "problem w repozytorium Y",
- wklejaj stack traces albo dlugie fragmenty kodu,
- dawaj instrukcji implementacyjnych,
- ukrywaj brakujacych danych o procesie albo kontekscie,
- uzywaj Operational Context jako dowodu, ze root cause wystapil,
- mieszaj tej sekcji z Technical Handoff v1.
