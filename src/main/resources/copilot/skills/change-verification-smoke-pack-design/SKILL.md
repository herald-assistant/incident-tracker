---
name: change-verification-smoke-pack-design
description: "Projektuje edytowalny smoke pack dla zmiany na podstawie Jira, MR i wyniku compliance."
---

# Change Verification Smoke Pack Design

## Cel

Zaprojektuj krotki, edytowalny smoke pack dla zmiany. Wynik ma pomoc
release ownerowi, developerowi albo testerowi szybko zweryfikowac, czy kluczowa
funkcjonalnosc po wdrozeniu zyje, odpowiada zgodnie z oczekiwaniem i zostawia
dane, ktore da sie sprawdzic przez readonly DB assertions.

## Wejscia

Korzystaj tylko z artefaktow osadzonych w promptcie:

- `change-verification/smoke-source.md`
- `change-verification/smoke-compliance.md`
- `change-verification/smoke-response-contract.md`

Nie czytaj lokalnego filesystemu. Nie wykonuj requestow. Nie zgaduj danych,
ktorych nie da sie uczciwie wywnioskowac z artefaktow.

## Zasady projektowania

1. Projektuj maksymalnie maly smoke pack, ktory pokrywa najwazniejsze ryzyka
   zmiany. Preferuj 1-5 testow.
2. Kazdy test ma sprawdzac konkretny efekt biznesowy albo techniczny.
3. Jezeli endpoint albo request body sa niepewne, wpisz najlepsza hipoteze,
   ale ustaw `reviewStatus` na `NEEDS_REVIEW` i opisz ograniczenie.
4. `responseAssertions` maja byc wykonalne w Postmanie:
   - `STATUS` dla kodu HTTP,
   - `JSON_PATH` dla pola odpowiedzi,
   - `HEADER` dla naglowka.
5. Preferuj `dbAssertionSpecs` zamiast legacy `dbAssertions`. Kazdy
   `dbAssertionSpecs.sql` musi byc readonly `SELECT` albo `WITH`, bez `;` i bez
   slow wykonujacych zapis. AI ani platforma nie wykonuje zapisow do bazy.
   Wspierane operatory oczekiwan to `EXISTS`, `NOT_EXISTS`, `ROW_COUNT_EQ`,
   `ROW_COUNT_GT`, `ROW_COUNT_GTE`, `ROW_COUNT_LT`, `ROW_COUNT_LTE`.
6. Cleanup:
   - uzyj `ENDPOINT`, gdy widoczny jest endpoint czyszczacy albo naturalny
     delete/cancel endpoint,
   - uzyj `MANUAL_SQL`, gdy mozna zaproponowac SQL dla operatora,
   - uzyj `NEEDS_REVIEW`, gdy cleanup wymaga decyzji czlowieka,
   - uzyj `NONE`, gdy test jest bezstanowy.
7. `manualSql` moze byc tylko fallbackiem dla czlowieka. Nie sugeruj, ze AI ma
   go uruchomic.

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
