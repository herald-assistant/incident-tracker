---
name: change-verification-compliance-check
description: "Weryfikuje zgodnosc zmiany z Jira story, acceptance criteria i instrukcjami repozytorium."
---

# Change Verification Compliance Check

## Cel

Ocen, czy zaimplementowana zmiana jest zgodna z materialem Jira oraz
instrukcjami repozytorium dostarczonymi w artefaktach Change Verification.
Wynik ma pomoc release ownerowi, developerowi albo testerowi szybko zobaczyc
rozjazdy, ryzyka i rekomendowane korekty przed wdrozeniem.

## Wejscia

Korzystaj tylko z artefaktow osadzonych w promptcie:

- `change-verification/source-discovery.md`
- `change-verification/jira-issue.md`
- `change-verification/merge-requests.md`
- `change-verification/instruction-context.md`
- `change-verification/response-contract.md`

Nie czytaj lokalnego filesystemu. Nie zgaduj materialu z Jira, GitLaba ani
Confluence, ktorego nie ma w artefaktach.

## Procedura

1. Ustal obietnice zmiany:
   - opis story,
   - acceptance criteria,
   - komentarze i linki,
   - dodatkowe operator `userInstructions`.
2. Ustal widoczny zakres implementacji:
   - MR titles,
   - source/target branches,
   - commit titles,
   - changed files.
3. Ustal obowiazujace instrukcje:
   - globalne i lokalne `AGENTS.md`,
   - `.github/copilot-instructions.md`,
   - pliki referencjonowane przez instrukcje.
4. Porownaj trzy warstwy:
   - story vs widoczna implementacja,
   - acceptance criteria vs widoczna implementacja,
   - instructions vs widoczna implementacja.
5. Rozrozniaj:
   - fakt potwierdzony przez artefakt,
   - inferencje z nazw plikow, branchy albo commitow,
   - luki widocznosci.

## Status

Ustaw `status` wedlug zasad:

- `PASSED`: brak istotnych rozjazdow i material jest wystarczajacy.
- `PASSED_WITH_WARNINGS`: zmiana wyglada spojnie, ale sa ryzyka albo male luki.
- `FAILED`: widoczny jest konkretny rozjazd ze story, acceptance criteria albo instrukcjami.
- `INCONCLUSIVE`: material nie wystarcza do uczciwej oceny.

## Findings

Kazdy finding musi miec:

- `severity`: `INFO`, `LOW`, `MEDIUM`, `HIGH` albo `BLOCKER`,
- `source`: `STORY`, `ACCEPTANCE_CRITERIA`, `INSTRUCTIONS`,
  `IMPLEMENTATION` albo `VISIBILITY`,
- `summary`: krotko,
- `details`: co potwierdza evidence i co jest inferencja,
- `references`: nazwy artefaktow albo konkretne sciezki,
- `suggestedAction`: rekomendacja zmiany kodu, doprecyzowania story albo
  pytania do ownera.

Nie tworz findingu jako mocnego zarzutu, jezeli masz tylko brak widocznosci.
Wtedy uzyj `VISIBILITY` i wpisz ograniczenie.

## Output

Zwracaj dokladnie jeden obiekt JSON zgodny z
`change-verification/response-contract.md`. Nie dodawaj tekstu przed ani po
JSON.
