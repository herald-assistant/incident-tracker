# Krok 5: Orkiestracja Analizy

## Cel

Zrozumiec, jak system sklada caly runtime flow od `correlationId` do wyniku.

## Po tym kroku rozumiesz

- role `AnalysisOrchestrator`,
- jak sync i job reuse'uja ten sam flow,
- gdzie wpiete sa listenery progresu,
- gdzie budowany jest request do AI,
- co trafia do `AnalysisExecution`,
- dlaczego follow-up chat nie uruchamia ponownie orchestratora analizy.

## Glowna sekwencja

1. kontroler przyjmuje `AnalysisRequest`,
2. `sync` albo `job` deleguje do `AnalysisOrchestrator`,
3. orchestrator uruchamia `AnalysisEvidenceCollector`,
4. po zebraniu danych buduje `AnalysisAiAnalysisRequest`,
5. provider AI przygotowuje prompt i wykonuje analize,
6. orchestrator mapuje wynik na `AnalysisResultResponse`.

`AnalysisExecution` trzyma potem razem:

- finalny `AnalysisContext`,
- request do AI,
- `preparedPrompt`,
- odpowiedz AI,
- wynik API.

Follow-up chat po `COMPLETED` jest obslugiwany w `AnalysisJobService` osobnym
kontraktem `AnalysisAiChatProvider`. Chat bierze zapisany `AnalysisAiAnalysisRequest`,
wynik, historie rozmowy i tool evidence, ale nie uruchamia ponownie
`AnalysisEvidenceCollector` ani nie tworzy nowego `AnalysisExecution`.

## Najwazniejsze klasy

- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisOrchestrator.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisExecution.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisExecutionListener.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/sync/AnalysisService.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/job/AnalysisJobService.java`

## Na co zwrocic uwage

- `AnalysisRequest` przyjmuje tylko `correlationId`,
- `gitLabGroup` pochodzi z konfiguracji, nie z requestu,
- `environment` i `gitLabBranch` sa rozwiazywane z evidence,
- job flow nie ma osobnego orchestratora, tylko projekcje postepu,
- follow-up chat nie ma osobnego publicznego scope'u; reuse'uje request AI
  zapisany po finalnej analizie,
- `AnalysisExecutionListener` obsluguje nie tylko kroki evidence, ale tez
  `onAiStarted`, `onAiPromptPrepared` i `onAiToolEvidenceUpdated`,
- job snapshot jest projekcja in-memory, a nie trwala historia analiz.

## Sprawdz lokalnie

- przejdz przez `AnalysisOrchestrator.analyze(...)`,
- porownaj, jak `sync` i `job` wchodza do tego samego flow,
- zobacz, gdzie job zapisuje `preparedPrompt`,
- zobacz, gdzie do joba trafia `toolEvidenceSections`,
- przejdz przez `AnalysisJobState.startChatMessage(...)` i zobacz, jak powstaje
  `AnalysisAiChatRequest`.

## Checkpoint

- Dlaczego `AnalysisJobService` nie powinien miec osobnej logiki analizy?
- W ktorym miejscu najlepiej dodać nowy listener progresu?
- Dlaczego `preparedPrompt` i `toolEvidenceSections` sa elementem execution/job
  projection, a nie glownego evidence pipeline?
- Dlaczego tool evidence z chatu jest przypisane do konkretnej odpowiedzi
  assistant, a nie do glownego pipeline evidence?
