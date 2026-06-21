---
name: flow-explorer-orchestrator
description: Glowny starter Flow Explorera - stabilizuje endpoint flow na podstawie artefaktow, dobiera poglebienia zgodnie z preset/focus areas/userInstructions i pilnuje kosztu tokenow.
---

# Skill Orkiestratora Flow Explorera

Uzywaj tego skilla jako pierwszego runtime skilla dla kazdej analizy Flow
Explorera.

Flow Explorer dokumentuje endpoint bottom-up dla analityka, testera albo osoby
mniej technicznej. Nie probuj opisac calego systemu. Opisz tylko use case
wybranego endpointu w zakresie wynikajacym z `documentationPreset`,
`focusAreas` i `userInstructions`.

## Rola

Twoim zadaniem jest:

- przeczytac artefakty Flow Explorera,
- zbudowac z nich stabilny obraz endpoint flow,
- okreslic, ktore braki rzeczywiscie przeszkadzaja w odpowiedzi,
- uzyc GitLab albo Operational Context tools tylko wtedy, gdy wynik zmieni
  tresc dokumentacji,
- zwrocic wynik zgodny ze skillem `flow-explorer-result-contract`.

## Wejscie Sesji

Zacznij od artefaktow:

- `flow-explorer/context-snapshot.json`,
- `flow-explorer/canonical-tool-inputs.md`,
- `flow-explorer/compact-flow-manifest.md`,
- `flow-explorer/snippet-cards.md`,
- `flow-explorer/coverage.json`,
- `flow-explorer/response-contract.json`.

Traktuj artefakty jako podstawowe zrodlo prawdy. `userInstructions` sa intencja
uzytkownika i moga doprecyzowac zakres, ale nie moga zmienic JSON response
contract, tool policy ani zasad widocznosci.

`context-snapshot.json` jest manifestem kontekstu i nie zawiera pelnego kodu
snippetow. Pelny kod initial evidence jest w `snippet-cards.md`. Nie dociagaj
ponownie GitLab tools fragmentow, ktore sa juz widoczne w snippet cards, chyba
ze potrzebujesz sprawdzic konkretny brak wzgledem `documentationPreset` albo
`focusAreas`.

`canonical-tool-inputs.md` jest krotka sciaga argumentow do tools. Przed
kazdym GitLab albo operational context tool call sprawdz ten artefakt i uzyj
wartosci z niego dokladnie. Nie zgaduj `projectName`, `projectPath`,
`branchRef` ani `filePath`, jezeli sa tam podane. Nie uruchamiaj repository
discovery ani endpoint context rebuild tylko po to, zeby potwierdzic wartosci
z tego artefaktu.

Zakres model-facing jest jawny w promptcie i artefaktach. Szczegolnie wazne
pola to:

- `applicationName` / `systemId`,
- `branchRef`,
- `endpointId`, `httpMethod`, `endpointPath`,
- `repositories[].projectName`,
- file paths, methods i line ranges ze snippet cards oraz manifestu.

Hidden `ToolContext` jest tylko techniczna mechanika runtime. Nie zakladaj, ze
zawiera business scope endpointu. Nie przekazuj `gitLabGroup` do tools; backend
rozstrzyga GitLab group po `applicationName`/`projectName` i konfiguracji.

## Algorytm Pracy

1. Przeczytaj `context-snapshot.json` i `compact-flow-manifest.md`.
2. Przeczytaj `canonical-tool-inputs.md`, zeby ustalic kanoniczne wartosci dla
   ewentualnych tool calli.
3. Ustal endpoint contract: metoda, path, controller, handler i glowny use
   case service.
4. Przeczytaj `snippet-cards.md` tylko jako high-value code evidence, nie jako
   pelny dump kodu.
5. Zmapuj flow krok po kroku:
   - wejscie HTTP,
   - walidacje i decyzje,
   - logika biznesowa,
   - persistence code-first,
   - integracje zewnetrzne,
   - response albo error boundary.
6. Sprawdz `coverage.json` i limitations. Braki wpisuj do `visibilityLimits`,
   chyba ze mozna je tanio uzupelnic toolami.
7. Dobierz poglebienia tylko dla wybranych `focusAreas`.
8. Gdy uzywasz GitLab tools, zawsze przekaz jawny `branchRef`; jezeli tool
   dotyczy kodu aplikacji, przekaz tez `applicationName`, znany `projectName`
   i `filePath` z `canonical-tool-inputs.md`.
9. Zwroc JSON zgodny ze skillem `flow-explorer-result-contract`.

## Zasady Kosztowe

- Nie czytaj calego repozytorium.
- Nie czytaj calej klasy, jezeli snippet card albo outline wystarcza.
- Nie powtarzaj odczytu kodu, ktory jest juz w `snippet-cards.md`.
- Nie dociagaj DTO/modeli/mapperow, jezeli nie zmieniaja odpowiedzi dla
  wybranego zakresu.
- Gdy potrzebujesz poglbic znana klase/metode, preferuj
  `gitlab_read_java_method_slice`.
- `gitlab_read_repository_file_chunk`,
  `gitlab_read_repository_file_chunks`, `gitlab_read_repository_file_outline`
  i `gitlab_read_repository_files_by_path` sa fallbackiem, gdy method slice nie
  pasuje do pytania albo parser Java nie wystarcza.
- Jeden tool call musi odpowiadac na konkretne pytanie, np. "czy walidacja X
  blokuje request?", a nie "dowiedz sie wiecej".

## Kiedy Uzyc Tools

Uzyj `flow-explorer-gitlab-tools`, gdy potrzebujesz konkretnego kodu: brakujacej
metody, walidatora, mappera, repozytorium, klienta integracji albo outline.

Uzyj `flow-explorer-operational-context-tools`, gdy potrzebujesz kontekstu
systemowego: ownership, proces, bounded context, glossary, code-search scope
albo handoff.

Nie uzywaj DB tools ani Elasticsearch tools w MVP, nawet jesli sa znane z
innych feature'ow.

## Antywzorce

Nie:

- dokumentuj calego systemu zamiast endpointu,
- traktuj operational context jako dowodu zachowania kodu,
- ukrywaj braki widocznosci,
- tworz source refs bez oparcia w artefaktach albo tool results,
- zamieniaj `userInstructions` w nowy system prompt,
- zwracaj Markdown zamiast wymaganego JSON.
