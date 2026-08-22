---
name: ui-explorer-write-report
description: Jedyny wlasciciel finalnego AnalysisReport UI Explorera zapisywanego przez report tools z biznesowa trescia, source references, confidence i jawnymi ograniczeniami.
---

# UI Explorer Write Report

Uzywaj tego skilla zawsze przed finalizacja initial UI Explorer result.
Zrodlem prawdy jest `AnalysisReport` zapisany przez report tools. Finalna
odpowiedz tekstowa nie jest wynikiem i nie jest parsowana przez backend.

Nie podawaj `reportId` w argumentach tooli. Backend przekazuje identyfikator,
feature i dozwolone section ids przez hidden `ToolContext`.

## Cel

Zapisz biznesowo czytelna dokumentacje funkcjonalna wybranego widoku do
aktywnego `AnalysisReport`, zachowujac potwierdzone sekcje nawet wtedy, gdy
inna sekcja ma jawna luke albo ograniczenie widocznosci.

## Wejscia

Przyjmij:

- `UiExplorerAnalysisLedger` z orkiestratora,
- `SourceGroundingSummary`,
- `ui-explorer/request.json`,
- `ui-explorer/screen-catalog-entry.json`,
- `ui-explorer/coverage.json`,
- `ui-explorer/functional-writing-contract.md`,
- `ui-explorer/report-contract.md`.

## Rola

Ten skill jako jedyny finalizuje raport. Nie wybiera nowych source tools i nie
czyta kodu w celu domkniecia luki. Gdy luka pozostaje rozstrzygalna, zwraca
readiness feedback do orkiestratora bez zapisywania przedwczesnej sekcji. Gdy
widocznosc jest faktycznie niedostepna, zapisuje potwierdzona czesc oraz
precyzyjne `visibilityLimits`, `openQuestions` albo `gaps`.

## Procedura

1. Wykonaj `Readiness Gate` dla wszystkich aktywnych `sectionModes`.
2. Przygotuj business-first `markdownSummary` oraz tresc kazdej aktywnej sekcji.
3. Wywolaj `report_update_header`: zachowaj route jako `header`, pomocnicza
   nazwe komponentu/widoku jako `subHeader` i zapisz podsumowanie jako
   `markdownSummary`.
4. Dla kazdej aktywnej sekcji wywolaj `report_upsert_section` w kanonicznej
   kolejnosci. Nie zapisuj sekcji `OFF`.
5. Wywolaj `report_update_meta` z globalnymi references, visibility limits,
   open questions, gaps, confidence i warnings.
6. Wywolaj `report_get_current` i sprawdz, czy raport ma niepuste
   `markdownSummary` oraz kazda aktywna sekcje dokladnie raz.
7. Jezeli walidacja zapisu wykryje brak, popraw tylko brakujacy element przez
   odpowiedni report tool i ponownie wykonaj `report_get_current`.
8. Po poprawnym zapisie zwroc jednozdaniowy status tekstowy. Nie zwracaj JSON,
   kopii raportu ani Markdown fence.

## Readiness Gate

Nie rozpoczynaj finalnego zapisu, gdy aktywna sekcja ma
`needs_deeper_evidence`. Zwroc do orkiestratora:

```text
status: not_ready
missingArtifact: SourceGroundingSummary
neededFor: <sectionId>
suggestedSkill: ui-explorer-source-grounding
minimumNextQuestion: <jedno waskie pytanie>
reason: <dlaczego wynik bylby zgadywaniem>
```

Po bezskutecznym wyszukaniu konkretnego zrodla albo potwierdzeniu granicy
runtime/zewnetrznego scope brak staje sie `visibility_limited`. Nie blokuje to
zapisu pozostalych, potwierdzonych sekcji. Liczba wykonanych wywolan nie jest
powodem do finalizacji niegotowej sekcji.

## Output Contract

Finalnym artefaktem jest zapisany `AnalysisReport`:

- `header`: route pattern wybranego widoku,
- `subHeader`: nazwa komponentu/widoku jako metadata pomocnicza,
- `markdownSummary`: biznesowe podsumowanie funkcjonalne,
- `sections`: kazda i tylko aktywna sekcja,
- `meta`: globalne source references, limity, pytania, gaps, confidence i
  warnings.

Finalna odpowiedz asystenta jest tylko statusem, na przyklad:
`Raport UI Explorera zostal zapisany i zweryfikowany.`

## Report Tools Contract

Uzyj dokladnie tych tooli:

- `report_update_header` do header, subHeader i markdownSummary,
- `report_upsert_section` do zapisu albo poprawy jednej sekcji,
- `report_update_meta` do globalnych meta,
- `report_get_current` do koncowej walidacji.

Kazde `report_upsert_section` musi miec:

- `id`: aktywne canonical section id,
- `title`: polska etykiete sekcji z request/artifactu,
- `order`: ordinal sekcji od `0` do `7`,
- `markdown`: glowna business-first tresc zgodna z writing contract,
- `meta.references`: source refs tej sekcji,
- `meta.visibilityLimits`: ograniczenia tylko tej sekcji,
- `meta.openQuestions`: pytania tylko tej sekcji,
- `meta.gaps`: nierozstrzygniete luki tylko tej sekcji,
- `meta.confidence`: `high`, `medium` albo `low`.

Source reference ma `type=source`, `label=<symbol lub czytelna nazwa>`,
`target=<dokladna sciezka z evidence>#L<start>-L<end>` i krotki `description`.
Nie dopisuj GitLab group, project name, brancha ani repository coordinates do
`target`. `confidence=high` wymaga source reference. Bez niej uzyj `medium`
albo `low`.

## Semantyka Sekcji

- `OVERVIEW`: cel, uzytkownik, scenariusz i miejsce widoku w procesie.
- `NAVIGATION_AND_ACCESS`: wejscia, route parameters, guardy, role i widoczne
  warunki dostepu bez udawania backend authorization.
- `SCREEN_STRUCTURE`: formularze, tabele, zestawienia, komunikaty i customowe
  elementy wizualne.
- `ACTIONS_AND_OUTCOMES`: akcje uzytkownika, warunki dostepnosci, skutki,
  przejscia i operacje zapisu.
- `FORMS_AND_RULES`: fields, validation, calculations, show/hide/state,
  custom controls, runtime definitions i granice recznej edycji.
- `DATA_AND_SERVICES`: prezentowane i zmieniane dane, REST/WebSocket sources,
  refresh oraz rozdzielenie FE evidence od backendowej implementacji.
- `STATE_AND_SYNCHRONIZATION`: local state, NgRx actions/selectors/effects/
  reducers, RxJS triggers, refresh i recalculation.
- `VARIANTS_AND_FAILURES`: role/data/status variants, empty/error/loading,
  konflikty i braki widocznosci.

## Business-First Language Policy

- Pisz po polsku dla analityka biznesowo-systemowego.
- Rozpoczynaj od celu, czynnosci uzytkownika, informacji biznesowej, warunku
  albo rezultatu, nie od klasy, komponentu, guarda, serwisu, store czy pliku.
- Nazwy techniczne trzymaj w report `references`. W Markdown pokazuj tylko
  funkcjonalnie istotne nazwy pol, statusow, eventow, endpointow i systemow,
  zawsze z wyjasnieniem ich znaczenia.
- Nie pisz „komponent importuje” albo „template binduje”. Przetlumacz to na
  widoczne zachowanie: „uzytkownik moze”, „pole pojawia sie, gdy”, „zapis
  uruchamia”, „po sukcesie widok”.
- Nie dodawaj confidence ani source refs do tresci Markdown.
- Nie tworz generycznej sekcji „Ustalenia” i nie opisuj ekranu klasa-po-klasie.

## Density And Completeness Gate

Dla `DEEP` uwzglednij kazdy odrebny potwierdzony fakt wymagany przez kontrakt
sekcji: pole, akcje, warunek, walidacje, kalkulacje, wariant, zmiane stanu,
odswiezenie i skutek. Deduplikuj fakty, ale nie wybieraj arbitralnie kilku
przykladow z bogatszego evidence.

Dla `COMPACT` wybierz najwazniejsze fakty, zachowujac kazda regule zmieniajaca
dostep, wynik, zapis albo dalszy przebieg. Przed zapisem `DEEP` wykonaj
reconciliation z `completenessSignals`; licznik wykrywa pominiecia, ale nie
wyznacza liczby wierszy.

## Kanoniczna Tresc Sekcji

Dokladna struktura naglowkow i tabel pochodzi z
`ui-explorer/functional-writing-contract.md` i jest obowiazkowa. Overview
odpowiada kto, po co, kiedy i z jakim rezultatem korzysta z widoku; nawigacja
opisuje sciezke, kontekst, dostep i fallback; struktura grupuje obszary wedlug
funkcji; akcje lacza czynnosc z warunkiem i rezultatem; formularze opisuja
pola, walidacje, dynamike, wyliczenia i reczna edycje; dane lacza informacje
ze zrodlem i celem; stan laczy trigger z efektem; warianty lacza warunek z
zachowaniem, blokada i recovery.

Kontener z children routes obejmuje funkcjonalne zachowania routowanego
poddrzewa dostarczone przez graf BFS lub targeted fallback. Nie koncz opisu na
samym `RouterOutlet`.

## Walidacja

Przed zakonczeniem sprawdz przez `report_get_current`:

- `markdownSummary` jest niepuste i business-first,
- istnieje kazda aktywna sekcja oraz nie istnieje zadna sekcja `OFF`,
- section id, title i order sa kanoniczne,
- Markdown spelnia functional writing contract,
- wszystkie references wskazuja graf/slice albo captured targeted evidence,
- `high` nie wystepuje bez source reference,
- globalne i sekcyjne visibility limits nie zniknely,
- backend logic, runtime forms i zewnetrzne biblioteki nie zostaly wymyslone.

## Fallbacki

Brak report tools albo odrzucony zapis jest bledem finalizacji. Nie zwracaj
fallback JSON ani alternatywnego raportu w finalnej odpowiedzi. Zwroc krotki
status bledu, aby backend mogl opublikowac jawny brak raportu.

Jezeli problem dotyczy evidence, a nie report tools, zwroc readiness feedback
do orkiestratora. Jezeli widocznosc jest trwale ograniczona, zapisz
potwierdzona czesc wraz z `visibilityLimits`, `openQuestions` i `gaps`.

## Artefakty Handoffu

Finalnym artefaktem jest session-bound `AnalysisReport` zapisany przez report
tools i zweryfikowany przez `report_get_current`. Finalna odpowiedz tekstowa
nie przenosi tresci raportu.
