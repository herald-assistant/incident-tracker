# Krok 18: Aktualna Integracja GitLab REST

Ten krok opisuje aktualny stan runtime integracji GitLaba po odejsciu od
produkcyjnego adaptera synthetic.

## Co zmienilismy

- `group` jest teraz konfigurowany w `application.properties`
- `POST /analysis` przyjmuje juz tylko `correlationId`
- `branch` i `environment` sa wyprowadzane z logow Elasticsearch, glownie z
  `container.image.name`
- repozytorium moze byc rozwiazywane po hintach komponentu z logow, glownie z
  `kubernetes.container.name`
- warstwa AI dostaje jawnie:
  - `correlationId`
  - `environment`
  - `gitLabBranch`
  - `gitLabGroup`
- GitLab tool-e i port repozytorium pracuja juz na pelnym kontekscie:
  - `group`
  - `projectName`
  - `branch`
  - `filePath`
- produkcyjny runtime GitLaba jest REST-only; fake repo zostalo tylko w testach

## Dlaczego to jest lepsze

W poprzednich krokach model mial evidence z logow i trace oraz mogl korzystac z
GitLab tooli, ale kontrakt repozytorium nie byl jeszcze przygotowany pod
rzeczywiste API i stabilny kontekst repo.

Teraz rozdzielamy odpowiedzialnosci:

- aplikacja wie, w jakiej grupie GitLaba pracuje
- logs badanego przypadku wskazuja branch i srodowisko deploymentu
- LLM nie musi zgadywac grupy ani brancha
- LLM ma juz tylko zinterpretowac:
  - ktory projekt jest istotny
  - ktore pliki warto dociagnac
- implementacja jest podzielona na:
  - `analysis.adapter.gitlab`
    generyczny adapter i source resolver,
  - `analysis.evidence.provider.gitlabdeterministic`
    mapping evidence -> GitLab,
  - `analysis.mcp.gitlab`
    tool-e dla AI

To jest bardzo dobra zmiana, bo zmniejsza pole do halucynacji.

## Aktualny przeplyw

1. Uzytkownik wysyla `POST /analysis`
2. W body jest tylko:
   - `correlationId`
3. `AnalysisService` buduje `AnalysisContext`
4. GitLab deterministic provider korzysta z:
   - `group` z properties
   - evidence z Elastic i deployment context
   - heurystyk z `container.image.name` i `kubernetes.container.name`
5. AI dostaje:
   - evidence sections
   - `environment`
   - `gitLabBranch`
   - `gitLabGroup`
6. Jesli AI potrzebuje dodatkowego kodu, wywoluje tool-e GitLaba juz z jawnie
   podanym:
   - `group`
   - `projectName`
   - `branch`
   - `filePath`

## Co dodalismy w kodzie

- `AnalysisRequest` z samym `correlationId`
- `AnalysisResultResponse` z `environment` i `gitLabBranch`
- `GitLabSourceResolveMatch`
- `GitLabProperties`
- `GitLabRestRepositoryAdapter`
- `GitLabDeterministicEvidenceProvider`, ktory rozumie deployment z logs i
  rozwiazuje odniesienia do kodu
- `GitLabRepositorySearchController` i `GitLabRepositorySearchService` do
  recznego testowania mapowania `component -> repo`
- testowy helper `TestGitLabRepositoryPort` zamiast produkcyjnego synthetic
  adaptera

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisRequest.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisResultResponse.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/AnalysisService.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisContext.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/GitLabProperties.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/GitLabRestRepositoryAdapter.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/provider/gitlabdeterministic/GitLabDeterministicEvidenceProvider.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter/gitlab/source/GitLabSourceResolveService.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/gitlab/GitLabMcpTools.java`

## Jak dziala realny adapter REST

`GitLabRestRepositoryAdapter` korzysta z GitLab REST API:

- search:
  `GET /projects/:id/search?scope=blobs&search=...&ref=branch`
- raw file:
  `GET /projects/:id/repository/files/:file_path/raw?ref=branch`

W praktyce:

- `group/projectName` jest zamieniane na identyfikator projektu w URL
- `branch` jest przekazywany jako `ref`
- wyszukiwanie blobow daje kandydackie pliki
- odczyt raw file sluzy do:
  - pelnego pliku
  - wyciecia chunka po liniach po stronie aplikacji

To jest nadal pierwsza wersja:

- chunk jest liczony po pobraniu raw file
- nie ma jeszcze paginacji i zaawansowanych fallbackow
- nie ma jeszcze retry i timeout policy

Ale jako fundament pod prawdziwe GitLab API jest to bardzo dobry krok.

## Jak dziala deterministic provider na podstawie logs

Provider analizuje evidence z Elastica i buduje deployment context.

Przyklad:

- `internal-openshift-image-registry.../banking-main-dev3/backend:20260324-112753-237-dev-omikron-<sha>`

Z takiego wpisu wyciagamy:

- `environment = dev3`
- `projectName = backend`
- `gitLabBranch = dev/omikron`
- `commitSha = <sha>`

Potem provider:

- normalizuje hinty projektu, np. `agreement-process -> agreement_process`,
- szuka dopasowanego repozytorium w skonfigurowanej grupie i podgrupach,
- potrafi rozwiazac przypadki typu
  `agreement-process -> PROCESSES/CREDIT_AGREEMENT_PROCESS`,
- probuje pelnych sciezek plikow znalezionych w logs,
- probuje symboli ze stacktrace z numerem linii,
- na koncu probuje nazw klas, takze w skroconej postaci symbolu,
- rozwiazuje plik przez `GitLabSourceResolveService`,
- pobiera chunk lub pelny plik z GitLaba i zwraca go jako evidence
  `gitlab/resolved-code`.

## Cache request-scoped w source resolverze

`GitLabSourceResolveService` moze w jednym requestcie wielokrotnie pytac o to
samo `repository/tree`, zwlaszcza gdy deterministic provider probuje kilku
symboli albo kilku wariantow projektu.

Aktualna zasada jest taka:

- drzewo repo jest cache'owane tylko per request,
- klucz obejmuje `gitlabBaseUrl`, `projectIdOrPath` i `ref`,
- poza requestem HTTP nie ma globalnego cache.

To zmniejsza liczbe wywolan do GitLaba, ale nie wprowadza jeszcze stalej
warstwy cache z TTL.

## Konfiguracja runtime GitLaba

W `application.properties`:

```properties
# analysis.gitlab.base-url=https://gitlab.example.com
analysis.gitlab.group=platform/backend
# analysis.gitlab.token=glpat_xxx
# analysis.gitlab.ignore-ssl-errors=false
```

Runtime nie ma juz przelacznika `analysis.gitlab.mode`.
Jesli potrzebujemy fake repo do testow, uzywamy helpera testowego.

## Co zmienilo sie w promptcie i skillu

Prompt Copilota dostaje teraz dodatkowo:

- `environment`
- `gitLabBranch`
- `gitLabGroup`

oraz instrukcje:

- nie zmieniaj samowolnie group i branch
- nie wymyslaj branch, jesli nie zostal rozwiazany z logs
- interpretuj tylko projekt i pliki

Skill domenowy tez zostal doprecyzowany pod te zasade.

## Jak testowac

```powershell
mvn test
```

Przy recznych testach adaptera mozna tez uzyc:

- `POST /api/gitlab/repository/search`
  do sprawdzenia mapowania `projectHints -> projectCandidates`
- `POST /api/gitlab/source/resolve`
  do sprawdzenia rozwiazywania symbolu do konkretnego pliku

Przykladowy request do mapowania repozytorium:

```json
{
  "projectHints": ["agreement-process"]
}
```

Przykladowy request do mapowania repozytorium i kandydatow plikow:

```json
{
  "correlationId": "agreement-123",
  "branch": "release-candidate",
  "projectHints": ["agreement-process"],
  "keywords": ["document"]
}
```

Najwazniejsze testy tego kroku:

- `AnalysisControllerTest`
- `AnalysisServiceTest`
- `GitLabDeterministicEvidenceProviderTest`
- `GitLabMcpToolsTest`
- `GitLabRestRepositoryAdapterTest`
- `CopilotSdkPreparationServiceTest`

## Co warto zrozumiec po tym kroku

1. Dlaczego `group` lepiej trzymac w konfiguracji niz dedukowac z logow?
2. Dlaczego `branch` i `environment` lepiej wyprowadzac z logs niz wymagac ich od klienta?
3. Dlaczego LLM powinien interpretowac tylko projekt i pliki, a nie zgadywac calego kontekstu repo?
4. Dlaczego warto bylo rozszerzyc kontrakt tooli do `group/project/branch/filePath` zanim podlaczymy kolejne systemy?

## Co dalej

Nastepny naturalny krok to:

- dodac statyczna mape `kubernetes.container.name -> project candidates`,
- dopracowac realny adapter GitLaba o limity, timeouty i obsluge bledow,
- ewentualnie dodac wyszukiwanie bardziej precyzyjnych kandydatow po projekcie,
- a potem przejsc do kolejnych realnych integracji, np. Elasticsearch i Dynatrace.
