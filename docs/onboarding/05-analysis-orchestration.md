# Krok 5: Orkiestracja Analizy

## Cel

Zrozumiec, jak system sklada caly runtime flow od `correlationId` do wyniku.

## Po tym kroku rozumiesz

- role `AnalysisOrchestrator`,
- jak job reuse'uje wspolny flow,
- gdzie wpiete sa listenery progresu,
- gdzie budowany jest request do AI,
- co trafia do `AnalysisExecution`,
- dlaczego follow-up chat nie uruchamia ponownie orchestratora analizy.

## Glowna sekwencja

1. kontroler joba przyjmuje `AnalysisJobStartRequest`,
2. `AnalysisJobService` deleguje do `AnalysisOrchestrator`,
3. orchestrator uruchamia `AnalysisEvidenceCollector`,
4. po zebraniu danych buduje `InitialAnalysisRequest`,
5. provider AI przygotowuje prompt i wykonuje analize,
6. orchestrator mapuje wynik na `AnalysisResultResponse`.

`AnalysisExecution` trzyma potem razem:

- finalny `AnalysisContext`,
- request do AI,
- `preparedPrompt`,
- odpowiedz AI,
- wynik API.

Follow-up chat po `COMPLETED` jest obslugiwany w `AnalysisJobService` osobnym
kontraktem `AnalysisAiChatProvider`. Chat bierze zapisany `InitialAnalysisRequest`,
wynik, historie rozmowy i tool evidence, ale nie uruchamia ponownie
`AnalysisEvidenceCollector` ani nie tworzy nowego `AnalysisExecution`.

## Najwazniejsze klasy

- `src/main/java/pl/mkn/incidenttracker/features/incidentanalysis/flow/AnalysisOrchestrator.java`
- `src/main/java/pl/mkn/incidenttracker/features/incidentanalysis/flow/AnalysisExecution.java`
- `src/main/java/pl/mkn/incidenttracker/features/incidentanalysis/flow/AnalysisExecutionListener.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/job/AnalysisJobService.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/job/state/AnalysisJobStateListener.java`

## Na co zwrocic uwage

- `AnalysisJobStartRequest` przyjmuje `correlationId` oraz opcjonalne
  preferencje AI,
- `gitLabGroup` pochodzi z konfiguracji, nie z requestu,
- `environment` i `gitLabBranch` sa rozwiazywane z evidence,
- job flow nie ma osobnego orchestratora, tylko projekcje postepu,
- `AnalysisJobStateListener` tlumaczy zdarzenia `AnalysisExecutionListener` na
  mutacje `AnalysisJobState`,
- follow-up chat nie ma osobnego publicznego scope'u; reuse'uje request AI
  zapisany po poczatkowej analizie,
- `AnalysisExecutionListener` obsluguje nie tylko kroki evidence, ale tez
  `onAiStarted`, `onAiPromptPrepared` i `onAiToolEvidenceUpdated`,
- job snapshot jest projekcja in-memory, a nie trwala historia analiz.

## Sprawdz lokalnie

- przejdz przez `AnalysisOrchestrator.analyze(...)`,
- zobacz, jak `AnalysisJobService` wchodzi do wspolnego flow,
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
