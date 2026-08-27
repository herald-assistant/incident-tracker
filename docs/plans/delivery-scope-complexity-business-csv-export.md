# Biznesowy CSV dla Delivery Scope Complexity

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Uzytkownik chce obrabiac wyniki Delivery Scope Complexity w Excelu i
porownywac punktacje issue pomiedzy okresami. Istniejacy export JSON sluzy do
przenoszenia calego runu i nie jest plaska tabela biznesowa.

## Proponowane rozwiazanie

Dodac feature-local, frontendowy export CSV terminalnego snapshotu. Jeden
wiersz reprezentuje issue z calego runu niezaleznie od filtrow UI. Wspolne
kolumny odwzorowuja zapisane dane Jira, Team, Delivery Unit, status oraz linki
MR. Kolumny oceny zawieraja punktowy wklad szesciu wymiarow, `finalScore` i
addytywne `pointsForAggregation`.

Ocena pozostaje ocena Delivery Unit. `pointsForAggregation` dostaje pelny
`finalScore` tylko przy issue z najpozniejszym `doneAt`, a przy remisie przy
leksykograficznie najmniejszym `issueKey`. CSV uzywa separatora `;`, UTF-8 z
BOM, CRLF i poprawnego cytowania komorek. Istniejacy JSON i backend pozostaja
bez zmian.

Zakres kolumn:

- `issueKey`, `issueUrl`, `summary`, `issueType`, `doneAt`, `teamId`,
  `teamName`, `teamFieldId`,
- `mergeRequestUrls`, `deliveryUnitId`, `assessmentStatus`,
- `noveltyPoints`, `structuralAndLogicPoints`,
  `businessAndInvariantsPoints`, `robustnessAndTestsPoints`,
  `refactorAndArchitecturePoints`, `distributionPoints`,
- `finalScore`, `pointsForAggregation`.

## Baseline i conformance delta

- Poziom: L2, feature export oraz neutralny helper pliku CSV uzywany przez dwa
  assessmenty.
- Publiczne API/DTO, discovery, evidence, AI, scoring, job state, persistence,
  import i export JSON: bez zmian.
- Shared frontend: nowy maly `core/utils` koduje plaska macierz do CSV oraz
  pobiera gotowy plik; nie zna modeli ani semantyki assessmentow.
- Konsumenci shared helpera: Delivery Complexity Assessment i Delivery Scope
  Complexity. Pierwszy zachowuje identyczny kontrakt CSV, ale przestaje
  utrzymywac lokalna kopie mechaniki pliku.
- Feature-local pozostaja nazwy plikow, naglowki, mapowanie snapshotu, wybor
  kotwicy agregacji i semantyka wynikow.
- Obecny snapshot przechowuje MR-y na poziomie Delivery Unit, dlatego ich
  wspolna lista zostanie powtorzona przy issue.
- Testy regresji: generator CSV, ekran feature'a, pelny frontend i build.

## Zakres

- Wydzielenie neutralnego kodowania i pobierania CSV do `core/utils` oraz
  migracja istniejacego eksportu Delivery Complexity Assessment bez zmiany
  zachowania.
- Generator i downloader CSV z testami.
- Osobna akcja `Eksportuj CSV` na ekranie terminalnego runu.
- Nazwa pliku z projektem i zakresem dat.
- Aktualizacja kanonicznego runtime flow.

## Non-goals

- Backendowy endpoint CSV i zmiany wersjonowanego JSON.
- Zmiana scoringu albo przypisanie osobnej oceny AI do issue.
- Eksport `score`, `scopeSignal`, `scope`, `scaledScore`, `weight`, evidence,
  promptu, raw response, usage, kosztu i opisow Jira.
- Odtworzenie relacji konkretne issue -> MR i eksport aktywnego filtra UI.
- Przenoszenie modeli, naglowkow albo logiki assessmentow do shared.

## Ograniczenia i ryzyka

- `finalScore` jest wynikiem Delivery Unit i nie jest addytywny po powtorzeniu
  przy issue; do sumowania sluzy wylacznie `pointsForAggregation`.
- Nieocenione jednostki maja puste wyniki i jawny `assessmentStatus`.
- CSV jest niewersjonowanym formatem biznesowym bez importu.

## Kryteria akceptacji

- Terminalny run ma dedykowana akcje CSV obok exportu JSON.
- CSV ma jeden wiersz per issue oraz uzgodnione kolumny w stalej kolejnosci.
- Linki MR sa niepuste, unikalne i rozdzielone przez ` | `.
- Wymiary zawieraja ich `points`, a puste assessmenty nie tworza zer.
- Jedna oceniona Delivery Unit ma dokladnie jedno `pointsForAggregation`, a
  suma tej kolumny odpowiada sumie `finalScore` jednostek.
- Cytowanie CSV, testy feature'a, pelne testy Angulara i build przechodza.

## Kroki

- [x] Krok 1: Wydzielic neutralna mechanike pliku CSV do `core/utils`,
  zmigrowac bez zmiany zachowania Delivery Complexity Assessment oraz pokryc
  obu konsumentow testami; dowod: regresja DCA i shared CSV przechodzi.
- [x] Krok 2: Dodac i przetestowac feature-local generator Delivery Scope
  Complexity; dowod: 5 testow mapowania przechodzi.
- [x] Krok 3: Podlaczyc akcje `Eksportuj CSV` i przetestowac download; dowod:
  lacznie 48 testow shared CSV i obu assessmentow przechodzi.
- [x] Krok 4: Zaktualizowac runtime flow, wykonac architecture diff, pelne
  testy Angulara i produkcyjny build; dowod: 467 testow Angulara przechodzi,
  produkcyjny build przechodzi i odswiezyl bundle w
  `src/main/resources/static`. Shared helper nie importuje modeli feature'ow,
  a oba assessmenty zachowuja lokalne mapowanie kolumn i agregacji.
