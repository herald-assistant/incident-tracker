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

Pracuj artifact-first. Zacznij od artefaktow osadzonych w promptcie:

- `change-verification/source-discovery.md`
- `change-verification/jira-issue.md`
- `change-verification/repository-scope.md`
- `change-verification/merge-requests.md`
- `change-verification/instruction-context.md`
- `change-verification/response-contract.md`

Nie czytaj lokalnego filesystemu. Gdy artefakty nie wystarczaja do uczciwej
oceny, uzywaj wylacznie wlaczonych GitLab tools i Operational Context tools.
Merge request wskazuje repozytorium i ref startowy, ale nie jest twarda granica
czytania kodu. Dociagaj tyle kodu, ile jest potrzebne do zrozumienia endpointu,
use case'u albo bounded contextu w ramach budzetu sesji. Nie zgaduj materialu z
Jira ani Confluence, ktorego nie ma w artefaktach albo nie zostal pobrany przez
wlaczone narzedzia.

## Procedura

1. Ustal obietnice zmiany:
   - target issue podane przez uzytkownika,
   - acceptance criteria target issue,
   - opis target issue,
   - komentarze i linki,
   - dodatkowe operator `userInstructions`.
2. Ustal role materialu kontekstowego:
   - parent issue jako szerszy kontekst celu, AC, slownictwa i ryzyk,
   - Confluence pages jako kontekst domenowy i flow,
   - subtaski jako kontekst powiazanej pracy,
   - MR-y parenta albo sibling subtaskow jako kontekst zaleznosci.
3. Ustal widoczny zakres implementacji:
   - MR titles,
   - source/target branches,
   - commit titles,
   - changed files.
4. Ustal obowiazujace instrukcje:
   - globalne i lokalne `AGENTS.md`,
   - `.github/copilot-instructions.md`,
   - pliki referencjonowane przez instrukcje.
5. Porownaj trzy warstwy:
   - target issue vs widoczna implementacja,
   - acceptance criteria target issue vs widoczna implementacja,
   - instructions vs widoczna implementacja.
6. Rozrozniaj:
   - fakt potwierdzony przez artefakt,
   - inferencje z nazw plikow, branchy albo commitow,
   - luki widocznosci.

## Interpretacja zrodel

1. Target issue jest glownym zakresem weryfikacji. Nie rozszerzaj zakresu
   tylko dlatego, ze parent albo Confluence sa szersze.
2. Acceptance criteria target issue sa najsilniejszym sygnalem wymagan. Gdy sa
   sprzeczne z opisem, pokaz rozjazd jako finding.
3. Opis target issue zawieza i tlumaczy oczekiwane zachowanie. Uzywaj go do
   interpretacji AC, ale nie ignoruj AC.
4. Parent issue jest materialem kontekstowym. Gdy target issue jest subtaskiem,
   parent pomaga zrozumiec cel nadrzedny, slownictwo, linki i ryzyka, ale ocena
   zgodnosci ma byc zawiezona do target subtaska.
5. Subtaski target issue albo sibling subtaski parenta sa kontekstem powiazanej
   pracy. Traktuj je jako sygnal zaleznosci, nie jako dodatkowe wymagania
   target issue.
6. Confluence pages z remote-linkow sa materialem kontekstowym. Uzywaj ich do
   rozumienia domeny, flow, terminologii i ryzyk. Nie zamieniaj szerokiego opisu
   Confluence w wymaganie, jesli target issue nie laczy go jawnie ze zmiana.
7. Merge requests i changed files pokazuja widoczna implementacje. Gdy MR nalezy
   do parenta albo sibling subtaska, wykorzystuj go tylko tam, gdzie pomaga
   ocenic target issue albo zaleznosc target issue.
8. Repository Scope pokazuje repozytoria z MR, rozbicie `projectPath` na
   `rootGroup`/`groupPath`/`repositoryName` oraz dopasowania
   repo -> code search scope -> target. Nie interpretuj tego jako
   bezposredniej relacji repo -> system albo repo -> bounded-context.
9. Dla GitLab tools uzywaj pola `projectName` z Repository Scope jako
   kanonicznego inputu. Nie przekazuj `projectPath`, `rootGroup/projectName`
   ani pelnej sciezki MR jako parametru `projectName`.
10. Dla GitLab tools uzywaj pola `analysisRef` jako `branchRef`. `sourceRef`
   i `targetRef` sa kontekstem MR; po merge'u source branch moze byc usuniety
   i wtedy `analysisRef` wskazuje target branch.
11. Code search scope z operational context jest wskazowka, jaki system albo
   bounded context moze byc potrzebny do zrozumienia zmiany. Uzywaj Operational
   Context tools, gdy potrzebujesz doprecyzowac proces, system, bounded context,
   integracje albo slownictwo domenowe.
12. Instruction context opisuje oczekiwania architektoniczne i repozytoryjne.
   Stosuj je do widocznej implementacji, ale nie uzywaj ich jako zastepstwa dla
   brakujacych wymagan biznesowych.
13. Gdy zrodla sa sprzeczne, nie wybieraj po cichu. Raportuj rozbieznosc, wskaz
   ktore zrodla konfliktuja i zaproponuj doprecyzowanie story, AC albo
   implementacji.
14. Gdy zrodlo jest szersze niz target issue, ocen tylko czesc powiazana z
    target issue, a reszte opisz jako out of scope albo visibility limit.

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
