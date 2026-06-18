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
- `gitlab_find_flow_context`.

`gitLabGroup` i `gitLabBranch` pochodza z hidden ToolContext. Nie podawaj ich
jako model-facing input.

## Strategia Czytania Kodu

Preferowana kolejnosc:

1. Uzyj artefaktow: `compact-flow-manifest.md` i `snippet-cards.md`.
2. Jezeli endpoint albo flow spine jest niepelny, uzyj
   `gitlab_build_endpoint_use_case_context`.
3. Jezeli znasz plik, ale nie metode, uzyj `gitlab_read_repository_file_outline`.
4. Jezeli znasz metode albo zakres, uzyj `gitlab_read_repository_file_chunk`
   albo `gitlab_read_repository_file_chunks`.
5. Jezeli potrzebujesz kilku znanych plikow, uzyj
   `gitlab_read_repository_files_by_path`.
6. `gitlab_read_repository_file` traktuj jako wyjatek dla malego pliku albo
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
