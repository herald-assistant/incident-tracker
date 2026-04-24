# Architecture And Code Map

## Glowny przeplyw runtime

Najkrotszy opis architektury:

1. UI lub klient API wysyla `correlationId`,
2. orchestrator zbiera evidence sekwencyjnie,
3. orchestrator buduje request do AI,
4. Copilot SDK wykonuje sesje z promptem, skills, attachmentami i tools,
5. wynik wraca jako `AnalysisResultResponse`,
6. job flow projektuje stan do UI.

## Najwazniejsze warstwy

### `analysis.flow`

Wlasciciel runtime flow.

Glowna klasa:

- `AnalysisOrchestrator`

Odpowiada za:

- uruchomienie collectora evidence,
- zbudowanie `AnalysisAiAnalysisRequest`,
- przygotowanie promptu,
- uruchomienie AI providera,
- zmapowanie finalnego wyniku.

### `analysis.sync`

Cienki synchroniczny endpoint `POST /analysis`.

Klasy:

- `AnalysisController`
- `AnalysisService`

### `analysis.job`

Asynchroniczny flow i projekcja postepu dla frontendu.

Klasy:

- `AnalysisJobController`
- `AnalysisJobService`
- `AnalysisJobState`
- `AnalysisJobResponse`

### `analysis.evidence`

Sekwencyjny pipeline evidence.

Klasy:

- `AnalysisContext`
- `AnalysisEvidenceCollector`
- `AnalysisEvidenceProvider`
- `AnalysisEvidenceProviderDescriptor`
- `AnalysisEvidenceReference`

### `analysis.evidence.provider`

Konkretne kroki pipeline:

1. `elasticsearch`
2. `deployment`
3. `dynatrace`
4. `gitlabdeterministic`
5. `operationalcontext`

DB capability nie jest tu providerem.

### `analysis.adapter`

Integracje z systemami zewnetrznymi, reuse'owalne capability adapters i helper
endpointy.

Podkatalogi:

- `elasticsearch`
- `dynatrace`
- `gitlab`
- `database`
- `operationalcontext`

### `analysis.mcp`

Warstwa `@Tool` i ich rejestracji przez Spring AI.

Podkatalogi:

- `elasticsearch`
- `gitlab`
- `database`

### `analysis.ai`

Generyczny kontrakt AI i implementacja oparta o Copilot SDK.

Podkatalogi:

- `copilot/preparation`
- `copilot/execution`
- `copilot/tools`

## Kluczowe klasy do czytania w kolejnosci

Jesli GPT Pro ma analizowac architekture, najlepiej zaczac od tych plikow:

1. `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisOrchestrator.java`
2. `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisEvidenceCollector.java`
3. `src/main/java/pl/mkn/incidenttracker/analysis/evidence/provider/*`
4. `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/CopilotSdkAnalysisAiProvider.java`
5. `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/preparation/CopilotSdkPreparationService.java`
6. `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/execution/CopilotSdkExecutionGateway.java`
7. `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/tools/CopilotSdkToolBridge.java`
8. `src/main/java/pl/mkn/incidenttracker/analysis/mcp/gitlab/GitLabMcpTools.java`
9. `src/main/java/pl/mkn/incidenttracker/analysis/mcp/database/DatabaseMcpTools.java`
10. `src/main/java/pl/mkn/incidenttracker/analysis/adapter/database/DatabaseToolService.java`
11. `src/main/java/pl/mkn/incidenttracker/analysis/job/AnalysisJobService.java`

## Evidence pipeline

Collector uruchamia providery jawnie i w stalej kolejnosci:

1. `ElasticLogEvidenceProvider`
2. `DeploymentContextEvidenceProvider`
3. `DynatraceEvidenceProvider`
4. `GitLabDeterministicEvidenceProvider`
5. `OperationalContextEvidenceProvider`

To nie jest kolejnosc z `@Order`.
To jest jawna kolejnosc utrzymywana w `AnalysisEvidenceCollector`.

## Co publikuje kazdy provider

### 1. Elasticsearch

Publikuje:

- provider: `elasticsearch`
- category: `logs`

Zawiera m.in.:

- timestamp
- level
- serviceName
- className
- message
- exception
- namespace
- podName
- containerName
- containerImage

### 2. Deployment context

Publikuje:

- provider: `deployment-context`
- category: `resolved-deployment`

Zawiera m.in.:

- environment
- branch
- projectNameHint
- containerName
- containerImage
- commitSha

Dodatkowo tworzy item "Wejscie do lookupu Dynatrace".

### 3. Dynatrace

Publikuje:

- provider: `dynatrace`
- category: `runtime-signals`

Zawiera m.in.:

- matched services
- problems
- metrics
- signal categories
- correlation highlights

### 4. GitLab deterministic

Publikuje:

- provider: `gitlab`
- category: `resolved-code`

Zawiera m.in.:

- environment
- branch
- group
- projectName
- filePath
- referenceType
- symbol
- lineNumber
- content
- resolveScore

### 5. Operational context

Publikuje:

- provider: `operational-context`
- category: `matched-context`

To enrichment oparty o curated resource pack.
Jest domyslnie wylaczony.
Bazowy katalog jest ladowany przez osobny adapter query-based, a nie
bezposrednio przez sam provider.

## Typowane czytanie evidence

`AnalysisContext` przechowuje tylko generyczne sekcje evidence.

Silniej typowane czytanie robi sie przez helpery typu:

- `ElasticLogEvidenceView`
- `DeploymentContextEvidenceView`

To jest wazny kompromis:

- AI i UI dostaja stabilny format generyczny,
- backend nadal moze czytac evidence bez stringowego chaosu w calej bazie kodu.

## Granice odpowiedzialnosci

### Co nalezy do adaptera

- REST albo JDBC / routing polaczen,
- properties,
- port,
- DTO integracyjne,
- helper endpointy tam, gdzie capability jest pomocniczo testowalna,
- dla DB:
  - routing po environment,
  - application-to-schema resolution,
  - metadata client,
  - read-only query client,
  - SQL guard,
  - masking i limiting wynikow.

### Co nalezy do evidence provider

- heurystyki incidentowe,
- logs -> deployment,
- logs -> repo hints,
- runtime -> evidence section

### Co nalezy do MCP tool

- mala ekspozycja jednego capability,
- delegacja do adaptera albo use case'u,
- session-bound hidden context przez `ToolContext`,
- lekkie heurystyki prezentacyjne dla modelu, jesli nie zamieniaja sie w
  centralny rule engine.

### Co nalezy do AI layer

- prompt,
- skill loading,
- tool bridge,
- attachment artifacts,
- execution session,
- parsing odpowiedzi modelu.

## Frontend i artefakt deployowalny

Zrodlowy frontend zyje w `frontend/`.

Po buildzie wynik trafia do:

- `src/main/resources/static`

To oznacza:

- jeden backend,
- jeden frontend,
- jeden deployable artefact po stronie Spring Boot.

## Najwazniejsze resource packs

- `src/main/resources/copilot/skills`
- `src/main/resources/operational-context`

W runtime dzisiaj najwazniejsze skille to:

- `incident-analysis-core`
- `incident-analysis-gitlab-tools`
- `incident-data-diagnostics`

## Najwazniejsze testy do szybkiego zrozumienia systemu

- `AnalysisEvidenceCollectorTest`
- `GitLabDeterministicEvidenceProviderTest`
- `CopilotSdkPreparationServiceTest`
- `CopilotSdkToolBridgeTest`
- `CopilotSdkDatabaseToolBridgeTest`
- `GitLabMcpToolsTest`
- `DatabaseMcpToolsTest`
- `DatabaseToolServiceTest`
- `AnalysisJobServiceTest`

## Co jest szczegolnie wazne przy refaktorach

- nie duplikowac orchestracji miedzy sync i job,
- nie przenosic heurystyk incidentowych do adapterow,
- nie przenosic klas adapterow do warstwy AI,
- nie mieszac skills z kodem Java,
- nie rozbijac na boki odpowiedzialnosci collectora evidence,
- nie robic z DB capability kolejnego evidence providera bez swiadomej decyzji
  architektonicznej.
