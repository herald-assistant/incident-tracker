# Krok 10: Runtime Copilot SDK

## Cel

Zrozumiec, jak projekt uzywa GitHub Copilot Java SDK do wykonania finalnej
analizy.

## Po tym kroku rozumiesz

- jak wyglada preparation requestu do Copilota,
- gdzie budowany jest prompt,
- jak ladowane sa runtime skills,
- jak odpowiedz modelu jest mapowana z powrotem na kontrakt aplikacji.

## Trzy czesci runtime

### Provider AI

`CopilotSdkAnalysisAiProvider` jest implementacja `AnalysisAiProvider`.
To glowna granica pomiedzy flow aplikacji a konkretnym SDK.

### Preparation

Buduje:

- `CopilotClientOptions`,
- `SessionConfig`,
- `MessageOptions`,
- prompt,
- liste tool definitions,
- skill directories.

### Execution

Uruchamia klienta, tworzy sesje, wysyla prompt i zbiera odpowiedz modelu.
W job flow execution rejestruje tez sesyjny listener, ktory przechwytuje
wyniki `gitlab_read_repository_file`, `gitlab_read_repository_file_chunk`
i `gitlab_read_repository_file_chunks` i publikuje je jako
`toolEvidenceSections` do pollowanego snapshotu analizy.

## Najwazniejsze klasy

- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/CopilotSdkAnalysisAiProvider.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/preparation/CopilotSdkPreparationService.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/execution/CopilotSdkExecutionGateway.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/tools/CopilotSdkToolBridge.java`

## Skill

Skill jest runtime resource aplikacji.
Zrodlo prawdy lezy w `src/main/resources/copilot/skills`, a loader wypakowuje go
do katalogu runtime dla sesji Copilota.

## Co warto zapamietac

- prompt niesie dane konkretnego incydentu,
- skill niesie stale zasady pracy,
- AI dostaje generyczne evidence, nie klasy adapter-specific,
- artefakty providerow, np. Elasticsearch `logs`, Dynatrace
  `runtime-signals` i GitLab `resolved-code`, moga byc renderowane jako
  czytelny markdown dla Copilota, podczas gdy API i UI nadal dostaja
  strukturalne evidence JSON,
- do sesji Copilota artefakty sa osadzane bezposrednio w promptcie, a nie jako
  sciezki do plikow na lokalnym dysku,
- preparation osadza te same artifacty rowniez bezposrednio w promptcie, zeby
  model mial pewny dostep do tresci nawet wtedy, gdy runtime nie pozwala ich
  "otwierac" jak osobnych plikow,
- artefakty sa nie tylko promptowym zaleceniem, ale tez wejciem do polityki
  sesji:
  jesli artefakty juz niosa dane Elastica albo deterministic GitLab code
  evidence, preparation nie wystawia odpowiadajacych im tool groups,
- `SessionConfig.availableTools` jest ustawiane jawnie, zeby odciac lokalny
  disk/workspace i shell tools; sesja widzi tylko jawnie dopuszczone Spring
  tools,
- `SessionHooks.onPreToolUse` jest dodatkowym guardrailem runtime i deny'uje
  kazdy tool spoza tej allowlisty, np. built-in `read_file`,
- hidden `ToolContext` niesie session-bound dane runtime, np. `correlationId`,
  `environment`, `gitLabGroup`, `gitLabBranch`, `analysisRunId`,
  `copilotSessionId` i metadata tool calla,
- AI-guided GitLab reads moga byc przechwytywane jako diagnostyczne
  `toolEvidenceSections` dla UI, ale nie staja sie elementem glownego pipeline
  `AnalysisEvidenceCollector`,
- skill i prompt moga kierowac model, aby przy symptomach JPA/repository
  najpierw wyprowadzil z GitLaba encje, predykat repozytorium, tabele i relacje
  jako hinty do DB tools,
- DB tools sa wlaczane warunkowo po `analysis.database.enabled=true` i nie
  wymagaja globalnego `spring.datasource`,
- parsing odpowiedzi modelu musi byc odporny na drobne roznice formatowania.

## Checkpoint

- Gdzie zmieniasz stale zasady pracy modelu?
- Gdzie zmieniasz sam prompt dla jednego requestu analizy?
- Dlaczego provider AI nie powinien znac klas adaptera Elasticsearch?
