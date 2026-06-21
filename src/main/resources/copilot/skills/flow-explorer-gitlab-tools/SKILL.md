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
- `gitlab_find_flow_context`.

GitLab tools nie czytaja business scope'u z hidden `ToolContext`. Gdy wolanie
GitLab toola wymaga scope'u, przekaz jawnie wartosci z
`flow-explorer/canonical-tool-inputs.md`:

- `branchRef`,
- `applicationName` jako nazwe aplikacji/systemu, jezeli pomaga zawęzic scope,
- `projectName`,
- `filePath`, `chunks` albo `methodSelectors` zgodnie z konkretnym toolem.

Nie przekazuj `gitLabGroup`. Backend rozstrzyga GitLab group przez operational
context albo konfiguracje `analysis.gitlab.group`.

## Strategia Czytania Kodu

Preferowana kolejnosc:

1. Uzyj artefaktow: `canonical-tool-inputs.md`,
   `compact-flow-manifest.md` i `snippet-cards.md`.
2. Jezeli endpoint albo flow spine jest niepelny, uzyj
   `gitlab_build_endpoint_use_case_context`.
3. Jezeli znasz plik, ale nie metode, uzyj `gitlab_read_repository_file_outline`.
4. Jezeli znasz jedna albo kilka metod, uzyj jako pierwszy focused read
   `gitlab_read_java_method_slice`, bo zwraca wybrane metody razem z
   potrzebnymi polami, importami i prywatnymi helperami bez dumpu calej klasy.
5. Jezeli znasz tylko zakres linii albo parser Java nie rozstrzygnal metody,
   uzyj `gitlab_read_repository_file_chunk` albo
   `gitlab_read_repository_file_chunks`.
6. Jezeli potrzebujesz kilku znanych plikow, uzyj
   `gitlab_read_repository_files_by_path`.
7. `gitlab_read_repository_file` traktuj jako wyjatek dla malego pliku albo
   sytuacji, w ktorej outline/chunk nie wystarcza.

## Co Czytac Wedlug Focus Area

- `BUSINESS_FLOW`: controller, use case service, decyzje biznesowe.
- `VALIDATIONS`: request model, validator, guard clause, error boundary.
- `PERSISTENCE`: repository method, query predicate, entity status fields,
  transactional boundary.
- `EXTERNAL_INTEGRATIONS`: client, adapter, request mapper, response handling,
  retry/error handling.
- `TEST_SCENARIOS`: branch conditions, negative paths, edge cases, response
  contract.
- `CHANGE_IMPACT`: direct collaborators, outbound boundary, persisted state,
  public API contract.

## Zasady Kosztowe

- Nie czytaj niepowiazanych metod w tym samym beanie.
- Nie czytaj ponownie metod, ktore sa juz obecne w `snippet-cards.md`, chyba ze
  pytanie wymaga innego overloadu, helpera albo powiazanego pliku.
- Do kazdego GitLab tool call dodaj `branchRef` z
  `canonical-tool-inputs.md`. Jezeli artefakt podaje `applicationName`,
  `projectName` albo `filePath`, przekaz te wartosci dokladnie, chyba ze wynik
  poprzedniego toola wskazal precyzyjniejszy scope.
- Dla `gitlab_read_java_method_slice` podawaj `methodSelectors` z minimalnym
  inputem: zwykle wystarczy `methodName`. `lineStart` jest opcjonalne; uzyj go
  tylko gdy chcesz zawęzic wynik do jednego konkretnego overloadu. Gdy
  `lineStart` nie jest podany, tool zwroci wszystkie metody o tej nazwie w
  wybranej klasie.
- Nie uzywaj `gitlab_list_available_repositories` ani
  `gitlab_build_endpoint_use_case_context` do potwierdzania `projectName`,
  `branchRef` albo `filePath`, ktore sa juz w `canonical-tool-inputs.md`.
- Nie czytaj pelnych DTO/modeli, jezeli nazwa typu i kontrakt endpointu
  wystarczaja.
- Nie czytaj mappera tylko dlatego, ze istnieje. Czytaj go, gdy zmienia
  request/response semantics, walidacje albo edge case.
- Nie rozszerzaj flow poza wybrany endpoint bez powodu wynikajacego z
  `focusAreas`.

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
