---
name: flow-explorer-code-grounding
description: Focused code grounding dla Flow Explorera - artifact-first GitLab reads, code evidence, source refs i waski handoff do skilli sekcyjnych bez masowego przegladania repozytorium.
---

# Flow Explorer Code Grounding

Uzywaj tego skilla tylko wtedy, gdy initial evidence nie wystarcza do
aktywnego `goal` albo `sectionModes`.

## Cel

Domknij konkretna luke w kodzie przez najmniejszy potrzebny GitLab read/search
i zwroc `CodeGroundingSummary`: co kod potwierdza, skad pochodzi evidence i
ktory dalszy skill albo sekcja raportu tego potrzebuje.

Ten skill nie buduje finalnego raportu, nie domyka samodzielnie sekcji
`PERSISTENCE` ani sekcji `INTEGRATIONS` i nie definiuje ich kontraktow.
Dostarcza tylko code evidence do:

- `flow-explorer-map-persistence-section`,
- `flow-explorer-map-integrations-section`.

## Rola

GitLab tools maja domknac konkretna luke w rozumieniu wybranego endpointu.
Nie sluza do potwierdzania danych, ktore juz sa w artefaktach, ani do
eksploracji repozytorium z ciekawosci.

Przed kazdym tool call ustal:

- ktora sekcja wyniku zalezy od odpowiedzi,
- jaka klasa, metoda, plik, binding albo kontrakt jest celem,
- po jakim wyniku przestaniesz czytac dalej.

## Wejscia

Wymagane:

- `flow-explorer/canonical-tool-inputs.md`,
- `flow-explorer/compact-flow-manifest.md`,
- `flow-explorer/snippet-cards.md`,
- aktywny `goal`, `sectionModes`, `reasoningEffort` i konkretna luka evidence.

Opcjonalne:

- `flow-explorer/openapi-endpoint-contract.md`,
- `EndpointFlowSummary` z orkiestratora,
- `OperationalGroundingSummary`, gdy code evidence wymaga nazwy domenowej,
- `PersistenceMappingSummary` albo `IntegrationBoundarySummary`, gdy follow-up
  wymaga doczytania jednego brakujacego fragmentu.

## Scope Tooli

Dozwolone GitLab tools:

- `gitlab_list_available_repositories`,
- `gitlab_list_repository_endpoints`,
- `gitlab_build_endpoint_use_case_context`,
- `gitlab_build_java_method_use_case_context`,
- `gitlab_read_repository_file`,
- `gitlab_read_repository_files_by_path`,
- `gitlab_read_repository_file_chunk`,
- `gitlab_read_repository_file_chunks`,
- `gitlab_read_repository_file_outline`,
- `gitlab_read_java_method_slice`,
- `gitlab_read_openapi_endpoint_slice`,
- `gitlab_find_flow_context`.

GitLab tools nie czytaja functional scope'u z hidden `ToolContext`. Gdy tool
wymaga scope'u, przekaz jawnie:

- `branchRef`, `applicationName` i `projectName` z
  `flow-explorer/canonical-tool-inputs.md`,
- `pathPrefixes` z operational-context `codeSearchScopes`, jezeli dany
  repository ma `searchMode=path-prefixes`,
- `filePath` i `methodSelectors` z
  `flow-explorer/compact-flow-manifest.md`,
- `filePath`, `httpMethod` i `endpointPath` z
  `flow-explorer/openapi-endpoint-contract.md`, jezeli czytasz OpenAPI.

Nie przekazuj `gitLabGroup`.

`pathPrefixes` stosuj tylko dla discovery/search/flow/class-reference tools.
Gdy prefixy roznia sie per repozytorium, wykonaj osobne focused calls zamiast
mieszac je w jednym zapytaniu. Exact file/method/OpenAPI reads powinny uzywac
konkretnych sciezek z manifestu albo poprzedniego tool result.

Gdy wynik GitLaba zwraca `projectName` i `filePath`, zachowaj je jako sygnal
dla operational grounding: moga zostac odwrotnie zmapowane przez
`codeSearchScope` i `pathPrefixes` do bounded contextu oraz resolved ownership.
Nie wyprowadzaj ownera bezposrednio z repozytorium w tym skillu.

## Algorytm

1. Sprawdz `flow-explorer/canonical-tool-inputs.md`,
   `flow-explorer/compact-flow-manifest.md`, `flow-explorer/snippet-cards.md`
   i, jezeli istnieje, `flow-explorer/openapi-endpoint-contract.md`.
2. Jezeli potrzebny kod jest juz w `snippet-cards.md`, nie wolaj GitLab toola.
3. Jezeli brakuje endpoint spine, uzyj
   `gitlab_build_endpoint_use_case_context`.
4. Jezeli znasz klase/metode i chcesz kontynuowac use case flow, uzyj
   `gitlab_build_java_method_use_case_context` z `maxResults`.
5. Jezeli znasz metode i potrzebujesz jej tresci, uzyj
   `gitlab_read_java_method_slice`.
6. Jezeli znasz plik, ale nie metode, uzyj
   `gitlab_read_repository_file_outline`.
7. Jezeli znasz OpenAPI path/method/file, uzyj
   `gitlab_read_openapi_endpoint_slice`, nie czytaj calego YAML.
8. Jezeli parser Java nie pasuje albo masz tylko linie, uzyj
   `gitlab_read_repository_file_chunk` albo
   `gitlab_read_repository_file_chunks`.
9. Jezeli masz mala, ugruntowana liste plikow, uzyj
   `gitlab_read_repository_files_by_path`.
10. `gitlab_read_repository_file` jest wyjatkiem: maly plik albo przypadek, w
    ktorym outline, method slice i chunk nie odpowiadaja na pytanie.

## Petla Evidence

Po kazdym tool result sprawdz, czy odpowiedz domyka pytanie evidence:

- jezeli wynik odpowiada na pytanie, zwroc `CodeGroundingSummary`,
- jezeli wynik jest zbyt plytki, ale wskazuje konkretny plik, metode, chunk,
  selector albo OpenAPI path, wykonaj jedno waskie poglebienie,
- jezeli kolejne poglebienie byloby tym samym pytaniem albo szerokim skanem,
  zwroc `visibility_limit`,
- jezeli `nextStep` wskazuje `map_persistence_section` albo
  `map_integrations_section`, nie przeskakuj bezposrednio do `write_report`.

Nie koncz jako `use_existing_evidence`, gdy wymagany fakt pozostaje
niepotwierdzony, a istnieje konkretny next read o wysokim information gain.

Opcjonalnie mozesz raz przeczytac `.github/copilot-instructions.md` z primary
repozytorium, ale tylko jako repository guidance. Nie szukaj wariantow tej
sciezki po calym repozytorium.

## Evidence Dla Skilli Sekcyjnych

Gdy orkiestrator albo skill sekcyjny prosi o kod:

- odpowiedz na jedno konkretne pytanie evidence, nie na cala sekcje raportu,
- dla `PERSISTENCE` zwroc touchpointy kodowe, source refs i luki widocznosci;
  nie domykaj tabel, kolumn, `SOURCE` ani przejsc ORM w tym skillu,
- dla `INTEGRATIONS` zwroc klienta, binding, path, payload clue, error
  boundary albo waski config key; nie buduj listy granic ani tabeli integracji,
- dla `FUNCTIONAL_FLOW` i `VALIDATIONS` zwroc tylko fakty, ktore zmieniaja
  opis kroku, wariantu, guard clause albo edge case,
- jezeli pytanie wymaga pelnej sekcji, w `nextStep` wskaz
  `map_persistence_section` albo `map_integrations_section`.

## Granice Evidence Z Kodu

- Uzywaj Java/Spring constructs tylko jako kotwic do source refs, nie jako
  procedury mapowania sekcji.
- Czytaj YAML/properties tylko po ugruntowanej nazwie z kodu: property
  placeholder, binding, client name, destination albo prefix.
- Nie czytaj DDL, Liquibase, Flyway, changelogow ani migracji SQL dla Flow
  Explorer persistence.
- Nie opisuj mappera jako integracji ani repository jako root cause. To sa
  evidence dla sekcji raportu albo specjalistycznych mapping skills.

## Zasady Kosztowe

- Nie zaczynaj od `gitlab_read_repository_file`.
- Nie czytaj ponownie metod widocznych w `snippet-cards.md`.
- Dla `gitlab_read_java_method_slice` podawaj minimalne `methodSelectors`;
  zwykle wystarczy `methodName`, a `lineStart` tylko zawęza overload.
- Po `truncated=true` nie wnioskuj o dalszej czesci pliku; jezeli jest
  potrzebna, kontynuuj przez chunk z `totalLines`, `returnedStartLine` i
  `returnedEndLine`.
- `reasoningEffort=low`: tylko krytyczna luka.
- `reasoningEffort=medium`: primary flow i aktywne sekcje `DEEP`.
- `reasoningEffort=high`: pomocnicze walidatory, mappery, repository details
  albo integracje tylko gdy zmieniaja scenariusze, ryzyka albo edge case.

## Wklad Do Wyniku

Po tool result zapisz wnioski jako:

- zachowanie systemu w prostym jezyku,
- source ref: artefakt, tool albo `projectName:path:Lx-Ly`,
- limit widocznosci, jezeli evidence jest niepelne.

Nie cytuj dlugiego kodu w wyniku.

## Kontrakt Wyniku

Zwroc do orkiestratora albo mapping skilla `CodeGroundingSummary`:

```text
question: <konkretna luka evidence>
codeFacts:
  - claim: <co kod potwierdza albo obala>
    sourceRef: <artifact/tool/projectName:path:Lx-Ly>
    confidence: fact | inference | visibility_gap
supports:
  - FUNCTIONAL_FLOW | VALIDATIONS | PERSISTENCE | INTEGRATIONS | report_meta
nextStep:
  - use_existing_evidence | map_persistence_section | map_integrations_section | write_report | visibility_limit
visibilityLimits:
  - <konkretny brak, jezeli jest>
```

## Walidacja

Przed powrotem sprawdz:

- kazdy tool call odpowiadal na konkretne pytanie,
- `branchRef`, `applicationName`, `projectName`, `filePath` i selectors
  pochodzily z artefaktow albo tool results,
- nie przeczytales ponownie fragmentu juz widocznego w `snippet-cards.md` bez
  nowego powodu,
- source refs sa wystarczajace dla raportu albo mapping skilla,
- braki sa nazwane jako `visibilityLimits`, a nie ukryte w opisie.

## Fallbacki

Jezeli GitLab tool jest niedostepny, odrzucony albo nie rozstrzyga luki:

- zwroc czesciowy `CodeGroundingSummary`,
- wskaz najmniejszy brakujacy plik/metode/konfiguracje,
- nie zgaduj dalszego flow, tabel, kolumn, payloadu ani targetu.

## Artefakty Handoffu

Wystaw:

- `CodeGroundingSummary`,
- source refs do uzycia przez `flow-explorer-write-report`,
- wskazanie, czy dalszy krok nalezy do `flow-explorer-map-persistence-section`,
  `flow-explorer-map-integrations-section`, operational context albo
  bezposrednio do raportu.

## Stop

Wroc do orkiestratora, gdy:

- masz evidence dla aktywnych sekcji,
- dalsze czytanie tylko potwierdzaloby to samo,
- potrzebny kontekst jest katalogowy, a nie kodowy,
- brak widocznosci nalezy wpisac do `visibilityLimits`.
