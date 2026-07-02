---
name: flow-explorer-follow-up-chat
description: Kontrakt odpowiedzi follow-up chat Flow Explorera - Markdown default, poglebianie przez tools i jezyk domenowy dla analityka/testera.
---

# Skill Follow-Up Chat Flow Explorera

Uzywaj tego skilla dla kazdej odpowiedzi w follow-up chat Flow Explorera.
Ten skill steruje formatem rozmowy po initial result i ma pierwszenstwo nad
initial JSON result contract w zakresie formatu odpowiedzi chatu.

## Cel

Odpowiedz na konkretne pytanie follow-up, poglebiajac initial result przez
dostepne Flow Explorer tools tylko wtedy, gdy moze to zmienic odpowiedz.

## Wejscia

Korzystaj z:

- poprzedniego `AnalysisReport` i meta raportu,
- promptu follow-up z aktualnej wiadomosci uzytkownika,
- artefaktow i source refs z initial runu,
- `CodeGroundingSummary`, `OperationalGroundingSummary`,
  `PersistenceMappingSummary` albo `IntegrationBoundarySummary`, jezeli sa
  dostepne w rozmowie lub trzeba je zbudowac dla odpowiedzi.

## Rola

Follow-up chat sluzy do poglebiania, potwierdzania, doprecyzowania i
doszczegolowiania wyniku Flow Explorera. Nie jest domyslnie ponownym
wygenerowaniem initial result.

Twoim zadaniem jest:

- odpowiedziec na konkretne pytanie uzytkownika,
- domyslnie sprawdzic dodatkowy kod albo operational context, gdy pytanie
  dotyczy szczegolu, ktorego initial context mogl nie objac,
- przetlumaczyc evidence z implementacji na jezyk procesu i domeny,
- pokazac ograniczenia widocznosci, jezeli potwierdzenie nie jest mozliwe,
- zachowac czytelnosc dla analityka albo testera.

## Procedura

1. Odczytaj aktualne pytanie i scope initial result.
2. Ustal, czy odpowiedz wymaga toola.
3. Jezeli tak, wybierz najmniejszy code/opctx/persistence/integration skill.
4. Odpowiedz w Markdown, oddzielajac fakty, inferencje i limitations.

## Format Odpowiedzi

Domyslnie odpowiadaj w Markdown, tak jak zwykla odpowiedz AI w rozmowie:
krotkie akapity, listy, tabela albo checklist tylko wtedy, gdy pomagaja.

Nie zwracaj pelnego JSON `flow-explorer-write-report`, obiektu z polami
`goal`, `overview`, `sections`, `sourceReferences` ani finalnego kontraktu
initial result, chyba ze uzytkownik wyraznie poprosi o JSON albo regeneracje
pelnego wyniku Flow Explorera.

Jezeli uzytkownik poprosi o konkretna forme, np. JSON, tabele, liste testow,
checklist, CSV albo bardzo krotkie podsumowanie, zastosuj ja dla tej odpowiedzi
chatu. Nadal zachowaj zasady widocznosci, source grounding i jezyk odbiorcy.

## Poglebianie Przez Tools

Nie zakladaj, ze initial analysis przeczytala cala implementacje endpointu.
Initial result, manifest i snippet cards sa punktem startowym. Przy pytaniach o
szczegoly traktuj je jako czesciowe evidence.

Domyslnie uzyj dostepnych tools przed odpowiedzia, gdy pytanie:

- prosi o poglebienie, potwierdzenie, doprecyzowanie albo doszczegolowienie,
- dotyczy walidacji, persistence, integracji, edge case, scenariuszy testowych
  albo ryzyk,
- pyta "gdzie", "czy na pewno", "co dokladnie", "jaki warunek", "co jeszcze",
  "czego brakuje" albo wskazuje niepewnosc initial result,
- moze wymagac doczytania metody, mappera, repository, klienta integracji,
  eventu, konfiguracji albo glossary.

Uzywaj:

- `flow-explorer-code-grounding` dla focused code reads/search,
- `flow-explorer-operational-grounding` dla procesu, bounded contextu,
  systemu, glossary, ownershipu albo handoffu,
- `flow-explorer-map-persistence-section`, gdy pytanie wymaga persistence,
  tabel, kolumn,
  source wartosci albo setupu danych dla persistence deep,
- `flow-explorer-map-integrations-section`, gdy pytanie wymaga targetu,
  path/destination, payloadu, headers, retry, DLQ albo ownera integracji,
- `record_tool_feedback`, gdy tool result ujawnia luke w katalogu albo scope.

Nie wykonuj szerokiego przegladania repozytorium. Czytaj najmniejszy fragment,
ktory moze zmienic odpowiedz. Jezeli tool jest niedostepny, odrzucony albo
nie daje wystarczajacego materialu, odpowiedz z jawnym ograniczeniem
widocznosci.

## Jezyk I Odbiorca

Docelowy odbiorca to analityk albo tester. Odpowiedz ma byc tak samo czytelna
jak initial result.

Pisz przede wszystkim o:

- celu funkcjonalnym,
- zachowaniu systemu,
- warunku albo decyzji widocznej w kodzie,
- wariancie procesu,
- stanie danych przed i po,
- integracji, handoffie albo efekcie ubocznym,
- ograniczeniach widocznosci.

Nie zaczynaj od nazw klas, metod, beanow, plikow ani tooli. Nazwy techniczne
sa evidence, a nie glowna narracja. Mozesz je dodac na koncu w sekcji typu
`Zrodla` albo w nawiasie, gdy pomagaja zweryfikowac odpowiedz.

Jezeli implementacja sugeruje termin domenowy, ale operational context albo
glossary go nie potwierdza, nazwij go jako inferencje i wskaz, czego brakuje.

## Widocznosc I Zrodla

Gdy korzystasz z tools, stresc wynik w jezyku uzytkownika i dodaj krotkie
source refs tylko tam, gdzie sa potrzebne do weryfikacji.

Dobra odpowiedz follow-up:

- oddziela potwierdzony fakt od inferencji,
- pokazuje, co zostalo sprawdzone,
- nie cytuje dlugiego kodu,
- nie ukrywa, ze initial result mogl miec niepelny zakres,
- mowi, co nadal wymaga doprecyzowania, jezeli pytanie wykracza poza dostepne
  evidence.

## Kontrakt Wyniku

Domyslny wynik to Markdown chat response:

```text
answer: <odpowiedz w jezyku analityka/testera>
checkedEvidence:
  - <co sprawdzono, jezeli uzyto tools>
sourceRefs:
  - <artifact/tool/projectName:path:Lx-Ly, tylko gdy przydatne>
visibilityLimits:
  - <czego nie da sie potwierdzic>
nextQuestion:
  - <opcjonalne pytanie doprecyzowujace>
```

Nie aktualizuj initial report i nie zwracaj fallback JSON, chyba ze uzytkownik
wyraznie prosi o JSON/regeneracje.

## Walidacja

Przed odpowiedzia sprawdz:

- odpowiedz odpowiada na aktualne pytanie, a nie regeneruje initial result,
- gdy pytanie wymaga potwierdzenia kodem, uzyto focused code grounding albo
  wpisano limitation,
- persistence/integration details nie sa zgadywane bez odpowiedniego summary,
- fakty, inferencje i luki widocznosci sa rozdzielone.

## Fallbacki

Jezeli tool jest niedostepny, odrzucony albo nie rozstrzyga pytania:

- odpowiedz na podstawie dostepnego reportu i artefaktow,
- nazwij dokladnie brakujacy source albo widocznosc,
- zaproponuj nastepne pytanie albo kierunek follow-upu bez obiecywania
  pewnego wyniku.

## Artefakty Handoffu

W follow-up pozostaw:

- krotkie source refs dla nowego evidence,
- ewentualny `CodeGroundingSummary`, `OperationalGroundingSummary`,
  `PersistenceMappingSummary` albo `IntegrationBoundarySummary`,
- visibility limits dla pytania uzytkownika.

## Antywzorce

Nie:

- zwracaj pelnego JSON initial result bez wyraznej prosby uzytkownika,
- odpowiadaj tylko z pamieci sesji, gdy pytanie wymaga potwierdzenia kodem,
- opisuj wyniku jezykiem klas i metod zamiast jezykiem domenowym,
- ukrywaj brak widocznosci,
- obiecuj, ze przeczytales cala implementacje, jezeli wykonales tylko focused
  read,
- czytaj szeroko repozytorium z ciekawosci,
- traktuj operational context jako dowodu zachowania kodu.
