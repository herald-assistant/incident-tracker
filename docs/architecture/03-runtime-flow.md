# Runtime Flow

## Flow `GET /`

To jest startowy frontend operacyjny serwowany przez Spring Boot.

### Co zwraca

- statyczny `index.html`,
- hashowane bundle `main-*.js` i `styles-*.css`,
- assets wygenerowane przez Angular CLI, np. `favicon.ico`.

### Rola tego flow

- daje prosty ekran do wpisania `correlationId`,
- wywoluje `POST /analysis/jobs` i potem polluje `GET /analysis/jobs/{analysisId}`,
- prezentuje status, environment, branch i diagnoze w czytelnej formie,
- pozwala zaimportowac albo wyeksportowac zakonczony zapis analizy do pliku JSON,
- jest zrodlowo utrzymywany jako aplikacja Angular w `frontend/`,
- produkcyjny build zapisuje wynik do `src/main/resources/static`,
- lokalny dev UI moze dzialac przez `cd frontend && npm start`, z proxy na
  backend Spring Boot.

## Flow `GET /evidence`

To jest pomocniczy frontend diagnostyczny serwowany przez ten sam backend
Spring Boot.

### Rola tego flow

- daje formularze do recznego odpalania helper endpointow adapterow,
- pozwala testowac `POST /api/elasticsearch/logs/search`,
- pozwala testowac `POST /api/gitlab/repository/search`,
- pozwala testowac `POST /api/gitlab/source/resolve` i wariant `preview`,
- pokazuje odpowiedzi backendu jako sformatowany JSON w polach tekstowych.

### Uwagi runtime

- route Angulara `/evidence` jest forwardowana po stronie Spring Boot do
  `index.html`,
- frontend nie trzyma lokalnej konfiguracji polaczen do Elastica ani GitLaba,
  tylko wywoluje istniejace helper endpointy backendu,
- ten widok nie wchodzi do glownego flow `/analysis`; sluzy do manualnych testow
  adapterow.

## Flow `POST /analysis/jobs`

To jest asynchroniczny flow wykorzystywany przez aktualny frontend `GET /`.

### Wejscie

Request zawiera:

- `correlationId`

### Krok 1: utworzenie joba

`AnalysisJobController` deleguje do `AnalysisJobService`, ktory:

1. generuje `analysisId`,
2. tworzy `AnalysisJobState` z krokami progressu,
3. zwraca poczatkowy snapshot joba ze statusem `QUEUED`,
4. uruchamia w tle watek analizy przez `TaskExecutor`.

### Krok 2: analiza w tle

Background task uruchamia:

1. wspolny `AnalysisOrchestrator`,
2. sekwencyjne zbieranie evidence przez `AnalysisEvidenceCollector`,
3. aktualizacje joba po kazdym kroku pipeline,
4. krok AI po zebraniu danych,
5. status terminalny `COMPLETED`, `FAILED` albo `NOT_FOUND`.

### Krok 3: polling statusu

Frontend odczytuje `GET /analysis/jobs/{analysisId}` do czasu statusu
terminalnego.

Snapshot joba zwraca:

- status calej analizy,
- aktualny krok i historie krokow,
- zebrane `evidenceSections`,
- `toolEvidenceSections`, czyli pliki GitLaba dociagniete przez AI tools juz w
  trakcie kroku `AI_ANALYSIS`,
- metadata kroku: `phase`, `consumesEvidence`, `producesEvidence`,
- `preparedPrompt`, czyli finalny prompt zlozony po evidence collection i przed
  wywolaniem Copilota,
- rozwiazane `environment` i `gitLabBranch`,
- finalny `AnalysisResultResponse`,
- kod i komunikat bledu, jesli analiza nie powiodla sie.

## Flow `POST /analysis`

To pozostaje glowny, synchroniczny kontrakt analizy po stronie API.
Ekran `GET /` korzysta jednak dzisiaj z wariantu jobowego, bo potrzebuje
pokazywac postep i dane przyrostowo.

### Wejscie

Request zawiera:

- `correlationId`

`gitLabGroup` pochodzi z konfiguracji.
`environment` i `gitLabBranch` sa rozwiazywane pozniej z evidence.

### Krok 1: kontroler

`AnalysisController`:

- przyjmuje request,
- uruchamia walidacje,
- deleguje do `AnalysisService`.

### Krok 2: zbieranie evidence

`AnalysisService` deleguje do `AnalysisOrchestrator`, a ten wywoluje
`AnalysisEvidenceCollector`.

Collector:

1. tworzy `AnalysisContext`,
2. uruchamia wszystkich `AnalysisEvidenceProvider` po kolei,
3. po deployment context uruchamia rownolegle Dynatrace i GitLab deterministic
   na tym samym snapshotcie contextu,
4. dolacza ich wyniki w stalej kolejnosci pipeline,
5. po kazdym zakonczonym kroku wzbogaca context o kolejna sekcje evidence.

Aktualni providerzy:

- Elasticsearch
- Deployment context
- Dynatrace runtime signals
- GitLab deterministic resolution
- Operational context enrichment

Kazdy krok deklaruje tez:

- `phase`,
- `consumesEvidence`,
- `producesEvidence`.

To jest wykorzystywane przez job progress i frontend do pokazania realnych
powiazan miedzy providerami.

Deployment context provider:

- korzysta z logow zebranych z Elastica,
- wyprowadza `environment`, `gitLabBranch`, `projectName` i hinty deploymentowe,
- publikuje osobna sekcje evidence `deployment-context/resolved-deployment`,
- jest wspolnym zrodlem faktow dla Dynatrace, GitLaba i orchestration flow.

Dynatrace provider:

- korzysta z logow z Elastica i z deployment context,
- wyprowadza `namespace`, `pod`, `container`, `microservice` i okno czasu
  incydentu,
- szuka najlepiej dopasowanych encji `SERVICE` w Dynatrace,
- pobiera problemy i metryki service-level dla najlepszego dopasowania,
- publikuje strukturalne `runtime-signals` do evidence i UI,
- w tej sekcji jawnie rozroznia status pobrania
  (`COLLECTED`, `UNAVAILABLE`, `DISABLED`, `SKIPPED`),
- dla dopasowanych komponentow rozroznia brak sygnalow problemowych od braku
  widocznosci,
- do attachmentu Copilota renderuje skrocony markdownowy summary komponentow,
  zamiast surowego JSON sekcji Dynatrace,
- nie wystawia osobnych tooli Dynatrace dla Copilota.

GitLab deterministic provider:

- korzysta z logow z Elastica i z deployment context,
- nie rozwiazuje juz sam `environment` ani `branch`,
- wykorzystuje hinty komponentu, glownie `kubernetes.container.name`, zeby
  rozwiazac rzeczywisty projekt GitLaba w skonfigurowanej grupie i jej
  podgrupach,
- szuka odniesien do kodu po pelnej sciezce, stacktrace albo nazwie klasy,
- pobiera dopasowane pliki lub chunki z GitLaba przez REST,
- publikuje strukturalne `resolved-code` do evidence i UI,
- do attachmentu Copilota moze renderowac czytelny markdown z metadanymi pliku
  i blokiem kodu zamiast surowego JSON sekcji GitLaba.

Dynatrace i GitLab deterministic:

- startuja po deployment context z tego samego snapshotu logs + deployment,
- wykonuja sie rownolegle, bo nie zaleza od siebie nawzajem,
- sa nadal raportowane i dolaczane do `AnalysisContext` w deterministycznej
  kolejnosci: najpierw Dynatrace, potem GitLab.

Operational context provider:

- czyta juz zebrane evidence, glownie logs, deployment, Dynatrace i GitLaba,
- buduje z nich sygnaly incydentu,
- dopasowuje katalog operacyjny: systemy, integracje, procesy, repozytoria,
  bounded contexts, zespoly, glossary i handoff rules,
- publikuje osobna sekcje `operational-context/matched-context`.

Implementacyjnie:

- generyczny adapter i source resolver sa w `analysis.adapter.gitlab`,
- deployment fact derivation jest w `analysis.evidence.provider.deployment`,
- deterministic provider jest w `analysis.evidence.provider.gitlabdeterministic`.

### Krok 3: budowa requestu do AI

Po zebraniu evidence `AnalysisOrchestrator` buduje
`AnalysisAiAnalysisRequest`, ktory zawiera:

- `correlationId`
- `environment`
- `gitLabBranch`
- `gitLabGroup`
- `evidenceSections`

### Krok 4: provider AI

`AnalysisAiProvider` w runtime jest dzisiaj realizowany przez
`CopilotSdkAnalysisAiProvider`.

### Krok 5: przygotowanie sesji Copilota

`CopilotSdkPreparationService` przygotowuje:

- `CopilotClientOptions`
- `SessionConfig`
- `MessageOptions`

Na tym etapie:

- ladujemy skill,
- ladujemy tool definitions,
- skladamy prompt z danymi incydentu i evidence,
- ustawiamy strategie permission handling.

### Krok 6: wykonanie sesji

Warstwa execution:

- uruchamia klienta,
- tworzy sesje,
- wysyla prompt,
- odbiera odpowiedz modelu,
- loguje lifecycle klienta i eventy sesji.

### Krok 7: opcjonalne uzycie GitLab i Database tools przez AI

W trakcie sesji model moze skorzystac z tooli adapterow:

#### Elasticsearch

- `elastic_search_logs_by_correlation_id`

To jest AI-guided dogranie dodatkowych logow po tym samym `correlationId`,
gdy evidence z glownego flow jest zbyt skrotowe.
Tool przyjmuje tylko `correlationId`, a rozmiar i limity wyniku pochodza z
`analysis.elasticsearch.*`.

#### GitLab

- `gitlab_search_repository_candidates`
- `gitlab_find_flow_context`
- `gitlab_read_repository_file_outline`
- `gitlab_read_repository_file`
- `gitlab_read_repository_file_chunk`
- `gitlab_read_repository_file_chunks`

To jest AI-guided repository exploration.
Dynatrace nie ma tu osobnych tooli.

W job flow odpowiedzi `gitlab_read_repository_file`,
`gitlab_read_repository_file_chunk` i `gitlab_read_repository_file_chunks` sa
dodatkowo mapowane do `toolEvidenceSections`, zeby frontend mogl pokazac na
zywo, jakie pliki i fragmenty kodu AI dociagnelo juz podczas sesji.

Implementacyjnie te tool-e sa utrzymywane w `analysis.mcp.gitlab` i
korzystaja z portow z `analysis.adapter.gitlab`.

#### Database

- `db_get_scope`
- `db_find_tables`
- `db_find_columns`
- `db_describe_table`
- `db_exists_by_key`
- `db_count_rows`
- `db_group_count`
- `db_sample_rows`
- `db_check_orphans`
- `db_find_relationships`
- `db_join_count`
- `db_join_sample`
- `db_compare_table_to_expected_mapping`
- `db_execute_readonly_sql`

To jest AI-guided, session-bound data diagnostics.

Najwazniejsze reguly runtime:

- `environment` pochodzi z hidden `ToolContext`, nie z parametrow modelu,
- discovery jest application-scoped:
  `application/deployment/container/project name -> configured Oracle owner/schema`,
- discovery tools nie przyjmuja `schemaPattern`,
- exact data tools pracuja dopiero na dokladnym `schema.table`,
- raw SQL pozostaje opcjonalny i domyslnie wylaczony,
- typed DB tools sa preferowane nad `db_execute_readonly_sql`.

Implementacyjnie te tool-e sa utrzymywane w `analysis.mcp.database` i
korzystaja z serwisow i klientow z `analysis.adapter.database`.

### Krok 8: mapowanie odpowiedzi AI

Provider Copilota mapuje tekst odpowiedzi modelu na:

- `detectedProblem`
- `summary`
- `recommendedAction`
- `rationale`
- `affectedFunction`
- `affectedProcess`
- `affectedBoundedContext`
- `affectedTeam`

Potem `AnalysisOrchestrator` buduje `AnalysisResultResponse`, ktory zwraca tez
rozwiazane `environment`, `gitLabBranch` oraz jawne pola wynikowe dla
processu, bounded contextu i teamu, o ile analiza ma dla nich wystarczajace
ugruntowanie w `operational-context`.

## Flow `POST /api/elasticsearch/logs/search`

To jest odseparowany flow diagnostyczno-testowy.

### Wejscie

Request zawiera tylko:

- `correlationId`

Pozostale dane polaczenia i limity odpowiedzi pochodza z konfiguracji
`analysis.elasticsearch.*`.

### Krok 1: budowa zapytania Kibana proxy

Serwis buduje wywolanie:

- `POST /s/{space}/api/console/proxy`
- query params:
  `path={configuredIndexPattern}/_search`
  `method=GET`
- body z:
  - `size`
  - sortowaniem po `@timestamp asc`
  - filtrem `term fields.correlationId`

Adapter REST zawsze ignoruje bledy certyfikatu i hosta, ale tylko lokalnie dla
integracji Elasticsearch/Kibana.

### Krok 2: mapowanie odpowiedzi

Adapter mapuje:

- `_source.fields`
- `kubernetes`
- `container`
- metadata `hits.total`, `timed_out`, `took`

na typowany wynik `ElasticLogSearchResult`.

### Krok 3: odpowiedz

Endpoint zwraca:

- metadata wyszukiwania,
- liste wpisow logow,
- komunikat `OK` albo czytelny blad.

Nie ma osobnego wariantu `preview` dla Elastica.

## Flow `POST /api/gitlab/repository/search`

To jest odseparowany flow diagnostyczno-testowy dla adaptera repozytorium
GitLaba.

### Wejscie

Request zawiera:

- `projectHints`
- opcjonalnie `correlationId`
- opcjonalnie `branch`
- opcjonalnie `operationNames`
- opcjonalnie `keywords`

`group` nie przychodzi z requestu.
Pochodzi z konfiguracji `analysis.gitlab.group`.

### Krok 1: rozwiazanie projektow

Serwis wywoluje generyczny port repozytorium, ktory:

- normalizuje hinty projektow, np. `agreement-process -> agreement_process`,
- przeszukuje skonfigurowana grupe GitLaba razem z podgrupami,
- zwraca kandydatow repozytoriow z uzasadnieniem dopasowania.

To pozwala recznie sprawdzic logike typu:

- `agreement-process`
- `agreement_process`
- `BANKING/PROCESSES/CREDIT_AGREEMENT_PROCESS`

### Krok 2: opcjonalne wyszukiwanie plikow

Jesli request zawiera `operationNames` albo `keywords`, serwis:

- reuse'uje te same hinty projektu,
- odpytuje adapter o kandydatow plikow,
- zwraca ranking plikow razem z rozwiazanym projektem.

Jesli nie ma `operationNames` ani `keywords`, endpoint konczy sie na zwroceniu
samych kandydatow repozytoriow.

### Krok 3: odpowiedz

Odpowiedz zawiera:

- `group`
- `branch`
- `projectHints`
- `projectCandidates`
- `fileCandidates`
- `message`

## Flow `POST /api/gitlab/source/resolve`

To jest odseparowany flow diagnostyczno-testowy.

### Wejscie

Request zawiera:

- `gitlabBaseUrl`
- `groupPath`
- `projectPath`
- `ref`
- `symbol`

### Krok 1: rozwiazanie symbolu

Serwis rozbija symbol na:

- `simpleName`
- `packagePath`

### Krok 2: pobranie drzewa repozytorium

Serwis wywoluje GitLab REST API:

- `repository/tree`
- `recursive=true`
- paginacja przez `X-Next-Page`

Jesli w tym samym requestcie serwis rozwiazuje ten sam `group/project/ref`
wielokrotnie, wynik drzewa moze zostac reused z cache request-scoped.

### Krok 3: ranking kandydatow

Serwis:

- filtruje `blob`,
- szuka nazw plikow zgodnych z symbolem,
- liczy score wedlug ustalonych regul,
- wybiera najlepszego kandydata.

### Krok 4: pobranie raw content

Serwis pobiera tresc najlepszego pliku przez:

- `repository/files/{filePath}/raw`

### Krok 5: odpowiedz

Odpowiedz zawiera:

- `matchedPath`
- `score`
- `candidates`
- `content`
- `message`

W wariancie `preview` tresc jest obcinana do 2000 znakow.

## Warstwy obserwowalnosci

Aktualnie mamy trzy poziomy logowania dla Copilota i tooli:

1. logi lifecycle klienta,
2. logi eventow sesji,
3. logi bridge i samych tooli.

To daje odpowiedzi na trzy rozne pytania:

- co stalo sie z klientem i sesja,
- jakie eventy i tool calle wykonal agent,
- jakie dokladnie dane dostalo i zwrocilo narzedzie.

## Kluczowe properties runtime

### AI

- `analysis.ai.copilot.github-token`
- `analysis.ai.copilot.permission-mode`
- `analysis.ai.copilot.send-and-wait-timeout`
- `analysis.ai.copilot.skill-resource-roots`
- `analysis.ai.copilot.skill-runtime-directory`

### GitLab

- `analysis.gitlab.base-url`
- `analysis.gitlab.group`
- `analysis.gitlab.token`
- `analysis.gitlab.ignore-ssl-errors`

### Database

- `analysis.database.enabled`
- `analysis.database.max-rows`
- `analysis.database.max-columns`
- `analysis.database.max-tables-per-search`
- `analysis.database.max-columns-per-search`
- `analysis.database.max-result-characters`
- `analysis.database.query-timeout`
- `analysis.database.connection-timeout`
- `analysis.database.raw-sql-enabled`
- `analysis.database.allow-all-schemas`
- `analysis.database.environments.<environment>.*`

### Elasticsearch

- `analysis.elasticsearch.base-url`
- `analysis.elasticsearch.kibana-space-id`
- `analysis.elasticsearch.index-pattern`
- `analysis.elasticsearch.authorization-header`
- `analysis.elasticsearch.evidence-size`
- `analysis.elasticsearch.evidence-max-message-characters`
- `analysis.elasticsearch.evidence-max-exception-characters`
- `analysis.elasticsearch.search-size`
- `analysis.elasticsearch.search-max-message-characters`
- `analysis.elasticsearch.search-max-exception-characters`
- `analysis.elasticsearch.tool-size`
- `analysis.elasticsearch.tool-max-message-characters`
- `analysis.elasticsearch.tool-max-exception-characters`

### Dynatrace

- `analysis.dynatrace.base-url`
- `analysis.dynatrace.api-token`
- `analysis.dynatrace.entity-page-size`
- `analysis.dynatrace.entity-fetch-max-pages`
- `analysis.dynatrace.entity-candidate-limit`
- `analysis.dynatrace.problem-page-size`
- `analysis.dynatrace.problem-limit`
- `analysis.dynatrace.problem-evidence-limit`
- `analysis.dynatrace.metric-entity-limit`
- `analysis.dynatrace.metric-resolution`
- `analysis.dynatrace.query-padding-before`
- `analysis.dynatrace.query-padding-after`

## Co jest jeszcze synthetic

Na teraz Elasticsearch, Dynatrace i GitLab maja rzeczywiste adaptery REST.
Testy utrzymuja osobne fake adaptery, ale produkcyjny runtime nie ma juz trybu
`synthetic` dla tych integracji.
