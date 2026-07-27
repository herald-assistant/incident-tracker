---
name: change-verification-smoke-pack-design
description: "Projektuje edytowalny smoke pack dla zmiany na podstawie Jira, MR i wyniku compliance."
---

# Change Verification Smoke Pack Design

## Cel

Zaprojektuj krotki, edytowalny smoke pack dla zmiany. Wynik ma pomoc
release ownerowi, developerowi albo testerowi szybko zweryfikowac, czy kluczowa
funkcjonalnosc po wdrozeniu zyje i odpowiada zgodnie z oczekiwaniem.

## Wejscia

Pracuj artifact-first. Zacznij od artefaktow osadzonych w promptcie:

- `change-verification/smoke-source.md`
- `change-verification/smoke-compliance.md`
- `change-verification/smoke-response-contract.md`

Nie czytaj lokalnego filesystemu. Nie wykonuj requestow. Gdy artefakty nie
wystarczaja do uczciwego projektu smoke packa, uzywaj wylacznie wlaczonych
GitLab tools i Operational Context tools. Merge request wskazuje repozytorium i
ref startowy, ale nie jest twarda granica czytania kodu. Dociagaj tyle kodu, ile
jest potrzebne do zrozumienia endpointu, payloadu, walidacji albo use case'u w
ramach budzetu sesji. Nie zgaduj danych, ktorych nie da sie uczciwie
wywnioskowac z artefaktow albo pobranych narzedziami zrodel.

## Zasady projektowania

1. Projektuj maksymalnie maly smoke pack, ktory pokrywa najwazniejsze ryzyka
   target issue. Preferuj 1-5 testow.
2. Kazdy test ma sprawdzac konkretny efekt biznesowy albo techniczny.
3. Jezeli endpoint albo request body sa niepewne, wpisz najlepsza hipoteze,
   ale ustaw `reviewStatus` na `NEEDS_REVIEW` i opisz ograniczenie.
4. `responseAssertions` maja byc wykonalne w Postmanie:
   - `STATUS` dla kodu HTTP,
   - `JSON_PATH` dla pola odpowiedzi,
   - `HEADER` dla naglowka.
5. MVP Change Verification nie sprawdza bazy danych. Nie generuj DB assertions,
   SQL ani `manualSql`.
6. Cleanup:
   - uzyj `ENDPOINT`, gdy widoczny jest endpoint czyszczacy albo naturalny
     delete/cancel endpoint,
   - uzyj `NEEDS_REVIEW`, gdy cleanup wymaga decyzji czlowieka,
   - uzyj `NONE`, gdy test jest bezstanowy.
7. Jezeli cleanup wymagalby SQL albo DB inspection, ustaw cleanup na
   `NEEDS_REVIEW` i opisz ograniczenie bez SQL.

## Interpretacja zrodel

1. Target issue jest glownym zakresem smoke packa. Projektuj testy pod
   najwazniejsze efekty target issue.
2. Acceptance criteria target issue sa podstawowym materialem do wyboru ryzyk,
   asercji HTTP i oczekiwanego efektu.
3. Opis target issue doprecyzowuje scenariusze i dane testowe, ale nie
   zastepuje acceptance criteria.
4. Parent issue jest szerszym kontekstem. Gdy target issue jest subtaskiem,
   parent pomaga zrozumiec cel nadrzedny, ale nie oznacza, ze smoke pack ma
   pokryc wszystkie subtaski parenta.
5. Sibling subtaski wykorzystuj tylko wtedy, gdy sa niezbedne do uruchomienia
   albo oceny target issue. W innych przypadkach nie projektuj dla nich osobnych
   testow.
6. Confluence pages sa kontekstem domenowym i flow. Uzywaj ich do wyboru
   sensownego smoke scenariusza, ale testuj tylko fragment powiazany z target
   issue.
7. Merge requests i changed files sa zrodlem endpointow, payloadow i widocznych
   zmian implementacji. Gdy MR pochodzi z parenta albo sibling subtaska, traktuj
   go jako kontekst zaleznosci.
8. Repository Scope pokazuje repozytoria z MR, rozbicie `projectPath` na
   `rootGroup`/`groupPath`/`repositoryName` oraz dopasowania
   repo -> code search scope -> target. Nie interpretuj tego jako bezposredniej
   relacji repo -> system albo repo -> bounded-context.
9. Dla GitLab tools uzywaj pola `projectName` z Repository Scope jako
   kanonicznego inputu. Nie przekazuj `projectPath`, `rootGroup/projectName`
   ani pelnej sciezki MR jako parametru `projectName`.
10. Dla GitLab tools uzywaj pola `analysisRef` jako `branchRef`. `sourceRef`
   i `targetRef` sa kontekstem MR; po merge'u source branch moze byc usuniety
   i wtedy `analysisRef` wskazuje target branch.
11. Code search scope z operational context jest wskazowka, jaki system albo
   bounded context moze byc potrzebny do zrozumienia endpointow, payloadow i
   cleanupu aplikacyjnego.
12. Jesli zrodla sa sprzeczne albo za szerokie, generuj mniejszy smoke pack dla
   target issue i wpisz pozostale ryzyka w `visibilityLimits` lub
   `suggestedActions`.

## Status

Ustaw `status` wedlug zasad:

- `READY`: testy sa wystarczajaco konkretne do edycji i eksportu Postmana.
- `NEEDS_REVIEW`: smoke pack jest uzyteczny, ale wymaga doprecyzowania
  endpointu, danych testowych albo cleanupu.
- `INCONCLUSIVE`: material nie wystarcza do sensownego smoke packa.

## Output

Zwracaj dokladnie jeden obiekt JSON zgodny z
`change-verification/smoke-response-contract.md`. Nie dodawaj tekstu przed ani
po JSON.
