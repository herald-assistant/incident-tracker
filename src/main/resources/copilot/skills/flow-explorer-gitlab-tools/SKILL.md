---
name: flow-explorer-gitlab-tools
description: Playbook uzycia GitLab tools w Flow Explorerze - focused reads, outline/chunks i endpoint use-case context bez niepotrzebnego dumpu kodu.
---

# Skill GitLab Tools Dla Flow Explorera

Uzywaj tego skilla, gdy orkiestrator potrzebuje dodatkowego evidence z kodu dla
wybranego endpointu.

## Rola Wobec Orkiestratora

GitLab tools maja uzupelnic konkretna luke w rozumieniu endpoint flow. Nie
sluza do masowego przegladania repozytorium.

Zanim uzyjesz toola, sprawdz `canonical-tool-inputs.md`,
`compact-flow-manifest.md` i `snippet-cards.md`. Jezeli potrzebny fragment kodu
jest juz w snippet cards, nie pobieraj go ponownie. Tool call ma dodac nowa
informacja, a nie potwierdzic to samo.

Przed tool call nazwij:

- jaka decyzja w dokumentacji zalezy od wyniku,
- jaki plik/metoda/rola flow jest celem,
- kiedy wynik wystarczy i nie trzeba czytac dalej.

## Dozwolone Tools

Flow Explorer moze korzystac z:

- `gitlab_list_available_repositories`,
- `gitlab_list_repository_endpoints`,
- `gitlab_build_endpoint_use_case_context`,
- `gitlab_read_repository_file`,
- `gitlab_read_repository_files_by_path`,
- `gitlab_read_repository_file_chunk`,
- `gitlab_read_repository_file_chunks`,
- `gitlab_read_repository_file_outline`,
- `gitlab_read_java_method_slice`,
- `gitlab_read_openapi_endpoint_slice`,
- `gitlab_find_flow_context`.

GitLab tools nie czytaja business scope'u z hidden `ToolContext`. Gdy wolanie
GitLab toola wymaga scope'u, przekaz jawnie wartosci z artefaktow Flow
Explorera:

- `branchRef` z `flow-explorer/canonical-tool-inputs.md`,
- `applicationName` jako nazwe aplikacji/systemu, jezeli pomaga zawęzic scope,
- `projectName` z `flow-explorer/canonical-tool-inputs.md`,
- `filePath` i `methodSelectors` z `flow-explorer/compact-flow-manifest.md`,
- `filePath` z `flow-explorer/openapi-endpoint-contract.md`, jezeli czytasz
  kontrakt OpenAPI.

Nie przekazuj `gitLabGroup`. Backend rozstrzyga GitLab group przez operational
context albo konfiguracje `analysis.gitlab.group`.

## Strategia Czytania Kodu

Preferowana kolejnosc:

1. Uzyj artefaktow: `canonical-tool-inputs.md`,
   `compact-flow-manifest.md` i `snippet-cards.md`.
2. Jezeli endpoint albo flow spine jest niepelny, uzyj
   `gitlab_build_endpoint_use_case_context`.
3. Jezeli znasz plik, ale nie metode, uzyj `gitlab_read_repository_file_outline`.
   Outline zwraca `typeSummaries`, `fieldSummaries`,
   `constructorSummaries` i `methodSummaries`. Adnotacje sa przypiete do
   elementu Java, na ktorym leza: typu, pola, konstruktora albo metody.
   Traktuj `fieldSummaries` jako neutralny zarys modelu, a nie automatyczna
   inferencje tabel/kolumn.
4. Jezeli potrzebujesz kontraktu endpointu z OpenAPI/Swagger YAML, uzyj
   `gitlab_read_openapi_endpoint_slice`. Nie czytaj calego pliku YAML ani
   surowego chunku, jezeli znasz `httpMethod`, `endpointPath` i `filePath`.
5. Jezeli znasz jedna albo kilka metod, uzyj jako pierwszy focused read
   `gitlab_read_java_method_slice`, bo zwraca wybrane metody razem z
   potrzebnymi polami, importami i prywatnymi helperami bez dumpu calej klasy.
6. Jezeli znasz tylko zakres linii albo parser Java nie rozstrzygnal metody,
   uzyj `gitlab_read_repository_file_chunk` albo
   `gitlab_read_repository_file_chunks`.
7. Jezeli potrzebujesz kilku znanych plikow, uzyj
   `gitlab_read_repository_files_by_path`.
8. `gitlab_read_repository_file` traktuj jako wyjatek dla malego pliku albo
   sytuacji, w ktorej outline/chunk nie wystarcza.

## Szybka Strategia Dla Slabszych Modeli

Gdy nie jest jasne, ktorego GitLab toola uzyc, trzymaj sie tej sciezki:

1. Nie zaczynaj od `gitlab_read_repository_file`. Najpierw sprawdz, czy
   `snippet-cards.md` juz zawiera potrzebny kod.
2. Jezeli znasz metode z `compact-flow-manifest.md`, uzyj
   `gitlab_read_java_method_slice`.
3. Jezeli znasz tylko zakres linii albo metoda nie parsuje sie jako Java, uzyj
   `gitlab_read_repository_file_chunk`. Czytaj waskie okna, zwykle 80-140
   linii, np. 1-120, 121-240, tylko gdy poprzedni wynik nie wystarczyl.
4. Jezeli potrzebujesz kilku konkretnych fragmentow naraz, uzyj
   `gitlab_read_repository_file_chunks` zamiast kilku osobnych calli.
5. Jezeli znasz plik, ale nie wiesz, ktora metoda jest wazna, uzyj
   `gitlab_read_repository_file_outline`, a potem method slice albo chunk.
6. `maxCharacters` w `gitlab_read_repository_file` nie oznacza pelnego pliku;
   to limit zwroconych znakow od poczatku pliku. Gdy wynik ma
   `truncated=true`, traktuj go jako prefix, nie jako caly plik.
7. Po `truncated=true` nie wnioskuj o dalszej czesci pliku. Jezeli dalsza czesc
   jest potrzebna, kontynuuj przez `gitlab_read_repository_file_chunk` na
   kolejnych liniach, korzystajac z `totalLines`, `returnedStartLine` i
   `returnedEndLine`.
8. Pelny `gitlab_read_repository_file` jest dopuszczalny tylko dla malego pliku
   albo gdy outline, method slice i chunk nie moga odpowiedziec na konkretne
   pytanie.

## Co Czytac Wedlug Focus Area

Focus area wskazuje kierunek, w ktorym masz poglębic juz ustalony primary flow.
Nie oznacza, ze inne elementy flow mozna pominac, ani ze wolno zwiekszyc
glebokosc eksploracji bez uzasadnienia. Glebokoscia steruje `reasoningEffort`.

- `BUSINESS_FLOW_RULES`: controller, use case service, decyzje biznesowe.
- `VALIDATIONS`: request model, validator, guard clause, error boundary.
- `PERSISTENCE`: repository method, query predicate, entity status fields,
  transactional boundary. Dla `sectionModes.PERSISTENCE=deep` oczekuj tabeli
  danych aktualizowanych/tworzonych/usuwanych ze zrodlem wartosci. Uzyj
  `gitlab_read_repository_file_outline` na encji/modelu/DTO/mapperze, zeby
  zebrac `fieldSummaries`, `typeSummaries` i adnotacje metod z
  `methodSummaries`, a potem `gitlab_read_java_method_slice` albo waski chunk
  na serwisie/mapperze, zeby ustalic, czy wartosc pochodzi z requestu, bazy,
  integracji, konfiguracji czy wyliczenia w logice.
- `INTEGRATIONS`: client, adapter, request mapper, response handling,
  retry/error handling.

## Zasady Kosztowe

- Nie czytaj niepowiazanych metod w tym samym beanie.
- Nie czytaj ponownie metod, ktore sa juz obecne w `snippet-cards.md`, chyba ze
  pytanie wymaga innego overloadu, helpera albo powiazanego pliku.
- Do kazdego GitLab tool call dodaj `branchRef` z
  `canonical-tool-inputs.md`. Jezeli artefakty podaja `applicationName`,
  `projectName` albo `filePath`, przekaz te wartosci dokladnie, chyba ze wynik
  poprzedniego toola wskazal precyzyjniejszy scope. `projectName` bierz z
  `canonical-tool-inputs.md`, a `filePath` i metody flow z
  `compact-flow-manifest.md`.
- Dla `gitlab_read_java_method_slice` podawaj `methodSelectors` z minimalnym
  inputem: zwykle wystarczy `methodName`. `lineStart` jest opcjonalne; uzyj go
  tylko gdy chcesz zawęzic wynik do jednego konkretnego overloadu. Gdy
  `lineStart` nie jest podany, tool zwroci wszystkie metody o tej nazwie w
  wybranej klasie.
- Nie uzywaj `gitlab_list_available_repositories` ani
  `gitlab_build_endpoint_use_case_context` do potwierdzania `projectName`,
  `branchRef` z `canonical-tool-inputs.md` ani `filePath` z
  `compact-flow-manifest.md`.
- Dla `reasoningEffort=low` wykonaj dodatkowy GitLab read tylko dla krytycznej
  luki, ktora moze zmienic wynik dla uzytkownika.
- Dla `reasoningEffort=medium` czytaj brakujace metody primary flow i
  najwazniejsze elementy wynikajace z sekcji oznaczonych jako `deep`.
- Dla `reasoningEffort=high` mozesz czytac pomocnicze walidatory, mappery,
  repository details albo klientow integracyjnych, jezeli maja realny wplyw na
  scenariusze, ryzyka albo edge case'y.
- Nie czytaj pelnych DTO/modeli, jezeli nazwa typu i kontrakt endpointu
  wystarczaja.
- Nie czytaj pelnego OpenAPI YAML, jezeli `gitlab_read_openapi_endpoint_slice`
  moze zwrocic operacje endpointu i powiazane schema/components.
- Nie czytaj mappera tylko dlatego, ze istnieje. Czytaj go, gdy zmienia
  request/response semantics, walidacje albo edge case.
- Nie rozszerzaj flow poza wybrany endpoint bez powodu wynikajacego z
  `sectionModes`, `goal` albo konkretnego `userInstructions`.

## Wklad Do Wyniku

Po kazdym istotnym tool result przeksztalc code evidence na prosty jezyk:

- co robi dany krok,
- dlaczego jest wazny dla uzytkownika,
- jaki source ref potwierdza stwierdzenie,
- czego nadal nie widac.

Nie cytuj dlugich fragmentow kodu w wyniku. Wystarcza plik/metoda/linie i
parafraza zachowania.

## Kiedy Wrocic Do Orkiestratora

Wroc, gdy:

- masz wystarczajace evidence dla wybranego zakresu,
- dalsze czytanie byloby tylko potwierdzeniem tego samego,
- potrzebny kontekst jest katalogowy, a nie kodowy,
- brak widocznosci trzeba wpisac do `visibilityLimits`.
