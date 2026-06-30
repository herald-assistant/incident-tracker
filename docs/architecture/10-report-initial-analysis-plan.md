# Report Artifact Plan: Initial Analysis

## Cel

Ten plan opisuje migracje initial analysis z modelu:

```text
sendAndWait -> finalna odpowiedz tekstowa/JSON -> parser -> result
```

na model:

```text
sendAndWait -> AI uzywa report tools -> backend zapisuje AnalysisReport -> result
```

MVP nie wersjonuje raportu. Trzymamy tylko ostatnia wersje raportu dla runu.
`reportId` jest generowany przez backend przy starcie initial analysis,
przekazywany do tools przez hidden `ToolContext` i zapisywany w rezultacie.

## Zasady pracy z planem

- Przed implementacja kazdego kroku opisujemy, co dokladnie bedzie zmieniane,
  jakie pliki/pakiety beda dotkniete i jakie testy uruchomimy.
- Implementacja kroku startuje dopiero po akceptacji zakresu.
- Po zakonczeniu kroku aktualizujemy ten plik: oznaczamy krok jako wykonany,
  dopisujemy odchylenia od planu i aktualizujemy nastepne kroki, jezeli
  zmienil sie kierunek.
- Jezeli w trakcie prac pojawi sie potrzebna zmiana architektury, najpierw
  aktualizujemy plan, potem implementujemy.
- Nie dodajemy kompatybilnosci wstecznej dla roboczego kontraktu raportu,
  chyba ze zostanie to jawnie ustalone.

## Zalozenia MVP

- Jeden run ma jeden aktywny raport.
- `reportId` jest unikalny i tworzy go backend, nie model.
- Brak wersjonowania historii raportu; przechowywana jest ostatnia wersja.
- Brak rownoleglych `sendAndWait` dla jednego raportu.
- Tool raportowy nie przyjmuje model-facing `reportId`; scope pochodzi z
  hidden `ToolContext`.
- Model raportu jest generyczny i nie zna semantyki Incident Analysis ani
  Flow Explorera.
- Feature decyduje o dozwolonych sekcjach, wymaganych sekcjach, promptach,
  skillach i mapowaniu raportu na publiczny kontrakt API/UI.

## Docelowy ksztalt raportu

```text
AnalysisReport
- reportId
- header
- subHeader
- markdownSummary
- sections
- meta
  - references
  - visibilityLimits
  - openQuestions
  - gaps
  - confidence
  - warnings
```

```text
AnalysisReportSection
- id
- title
- order
- markdown
- meta
  - references
  - visibilityLimits
  - openQuestions
  - gaps
  - confidence
```

## Kroki

### 1. Ustalenie kontraktu i ownership

Status: [x]

Przed wykonaniem:

- Opiszemy finalny minimalny kontrakt `AnalysisReport`, `AnalysisReportSection`
  i `AnalysisReportMeta`.
- Wskazemy pakiet docelowy, najpewniej `shared.ai.report`, i potwierdzimy, ze
  `shared` nie importuje warstw aplikacyjnych.
- Rozstrzygniemy nazwy pol dla `references`, `visibilityLimits`,
  `openQuestions`, `gaps`, `confidence` i `warnings`.

Implementacja:

- Dodano neutralne recordy raportu w `shared.ai.report`:
  `AnalysisReport`, `AnalysisReportSection`, `AnalysisReportMeta`,
  `AnalysisReportReference`.
- `AnalysisReportMeta.empty()` jest minimalnym helperem uzywanym do
  domyslnego meta raportu i sekcji.
- Model normalizuje listy do pustych, niemodyfikowalnych list i zostawia
  semantyke sekcji oraz walidacje dozwolonych `id` po stronie feature/tool
  policy.
- Dodano test modelu `AnalysisReportModelTest`.

### 2. Hidden context dla raportu

Status: [x]

Przed wykonaniem:

- Opiszemy, jakie klucze hidden contextu sa potrzebne dla raportu, np.
  `reportId`, `reportFeature`, `allowedReportSectionIds`.
- Wskazemy, czy klucze mieszkaja w `agenttools.context.AgentToolContextKeys`
  czy w platformowym pakiecie report tools.
- Potwierdzimy, ze `reportId` nie trafia do publicznego requestu ani do
  model-facing schema tooli.

Implementacja:

- Dodano klucze hidden contextu w `AgentToolContextKeys`: `reportId`,
  `reportFeature`, `allowedReportSectionIds`.
- Initial Incident Analysis dodaje ukryty `reportId`, `reportFeature =
  incident-analysis` oraz sekcje `FUNCTIONAL_ANALYSIS`, `TECHNICAL_HANDOFF`.
- Initial Flow Explorer dodaje ukryty `reportId`, `reportFeature =
  flow-explorer` oraz `OVERVIEW` plus aktywne sekcje z
  `FlowExplorerResultSectionModeResolver`.
- Follow-up nie generuje nowego `reportId` w tym kroku. Wczytanie poprzedniego
  raportu i przekazanie jego scope'u zostaje w planie follow-up.
- `reportId` nie trafia do publicznego requestu ani do model-facing schema
  tooli.

### 3. Session-bound report store

Status: [x]

Przed wykonaniem:

- Opiszemy, jak backend rejestruje raport w czasie pojedynczego runu Copilota.
- Ustalimy API store'a: create/register, snapshot, update section, update meta,
  unregister.
- Potwierdzimy, ze store jest thread-safe dla kolejnych tool calls w ramach
  jednego `sendAndWait`, ale nie wprowadza wersjonowania historii.

Implementacja:

- Dodano platformowy `CopilotReportSessionStore` w
  `aiplatform.copilot.tools.report`.
- Store trzyma tylko ostatni snapshot `AnalysisReport` per `reportId`, bez
  wersjonowania historii.
- Store udostepnia `register`, `current`, `replace`, `upsertSection`,
  `updateMeta` i `unregister`.
- Mutacje sa atomowe przez `ConcurrentHashMap.compute`; snapshot raportu jest
  immutable przez neutralne recordy `shared.ai.report`.
- Store nie jest jeszcze wpiety w `CopilotSdkExecutionGateway`; rejestracja
  przed `sendAndWait` i zwrot snapshotu zostaja w kroku runtime wiring.

### 4. Generyczne report tools

Status: [x]

Przed wykonaniem:

- Opiszemy minimalny zestaw tools dla MVP.
- Preferowany zestaw startowy:
  - `report_get_current`
  - `report_upsert_section`
  - `report_update_meta`
- Potwierdzimy input schema i komunikaty bledow, szczegolnie dla niedozwolonej
  sekcji albo braku aktywnego raportu.

Implementacja:

- Dodano platformowe Spring `@Tool`: `report_get_current`,
  `report_upsert_section`, `report_update_meta`.
- Dodano `ToolCallbackProvider` w `CopilotReportToolConfiguration`.
- Tool'e odczytuja `reportId`, `reportFeature` i `allowedReportSectionIds`
  wylacznie z hidden `ToolContext`; model-facing schema nie zawiera
  `reportId`.
- `report_upsert_section` odrzuca sekcje spoza `allowedReportSectionIds`.
- Flow Explorer i Incident Analysis jawnie dopuszczaja report tools w swoich
  policy/allowlistach.
- Dodano testy rejestracji callbackow, odczytu raportu, upsertu sekcji,
  odrzucenia niedozwolonej sekcji, aktualizacji meta i braku aktywnego raportu.

### 5. Wiring w Copilot runtime

Status: [x]

Przed wykonaniem:

- Opiszemy zmiany w `CopilotRunRequest`, `CopilotPreparedSession` i
  `CopilotExecutionResult`.
- Ustalimy, czy raport jest przekazywany przez osobny sink/snapshot czy przez
  store rejestrowany w execution gateway.

Implementacja:

- Dodano opcjonalny `initialReport` do `CopilotRunRequest` i
  `CopilotPreparedSession`.
- Dodano opcjonalny `report` do `CopilotExecutionResult`.
- `CopilotPreparedSessionFactory` przenosi scaffold raportu z neutralnego run
  requestu do przygotowanej sesji.
- `CopilotSdkExecutionGateway` rejestruje aktywny raport w
  `CopilotReportSessionStore` przed `sendAndWait`, zwraca ostatni snapshot w
  execution result i zawsze robi `unregister` w `finally`.
- Gdy feature nie przekaze `initialReport`, runtime zachowuje dotychczasowe
  zachowanie i `report` w wyniku pozostaje `null`.
- Runtime nadal zwraca `content`, bo chat/status finalnej odpowiedzi moze byc
  przydatny diagnostycznie, ale nie jest zrodlem prawdy raportu.
- Tworzenie scaffoldow raportu przez konkretne feature'y zostaje w krokach 6 i
  7.

### 6. Flow Explorer initial jako pierwszy konsument

Status: [x]

Przed wykonaniem:

- Opiszemy mapowanie obecnego `FlowExplorerAiResponse` na `AnalysisReport`.
- Ustalimy sekcje Flow Explorera:
  - `OVERVIEW`
  - `FUNCTIONAL_FLOW`
  - `VALIDATIONS`
  - `PERSISTENCE`
  - `INTEGRATIONS`
- Wskazemy, ktore pola `FlowExplorerResultResponse` beda mapowane z raportu.

Implementacja:

- Dodano feature-owned `FlowExplorerReportFactory`, ktory tworzy scaffold
  `AnalysisReport` z tym samym `reportId`, ktory report tools dostaja przez
  hidden `ToolContext`.
- `FlowExplorerCopilotRunRequestAssembler` przekazuje `initialReport` tylko dla
  initial run; follow-up nadal nie tworzy nowego raportu.
- Dodano `FlowExplorerReportMapper`, ktory mapuje `OVERVIEW` na publiczne
  `FlowExplorerResultOverview`, aktywne sekcje raportu na
  `FlowExplorerResultSection`, a meta raportu/sekcji na references,
  visibility limits, open questions, gaps i confidence.
- `FlowExplorerJobService` traktuje `executionResult.report()` jako glowne
  zrodlo wyniku, a dotychczasowy `FlowExplorerAiResponseParser` zostawia jako
  fallback diagnostyczny dla braku albo niekompletnego raportu.
- Prompt initial Flow Explorera i runtime skille `flow-explorer-orchestrator`
  oraz `flow-explorer-result-contract` zostaly przestawione na report tools:
  `report_upsert_section`, `report_update_meta`, `report_get_current`.
- Fallback JSON contract zostal zachowany tylko jako tryb awaryjny, gdy report
  tools nie sa dostepne albo zapis raportu sie nie powiedzie.

### 7. Incident Analysis initial jako drugi konsument

Status: [x]

Przed wykonaniem:

- Opiszemy mapowanie obecnego publicznego kontraktu incydentu na raport.
- Ustalimy sekcje incydentu, np.:
  - `FUNCTIONAL_ANALYSIS`
  - `TECHNICAL_HANDOFF`
  - opcjonalnie header/meta dla `detectedProblem`, `affectedProcess`,
    `affectedBoundedContext`, `affectedTeam`.
- Potwierdzimy, ze publiczny result nadal nie przywraca starych pol.

Implementacja:

- Dodano brakujacy generyczny tool `report_update_header`, bo mapowanie
  `detectedProblem` na `report.header` wymaga aktualizacji naglowka raportu
  bez wpychania tej wartosci do sekcji albo meta.
- `CopilotReportSessionStore` obsluguje aktualizacje `header`, `subHeader` i
  `markdownSummary`, a `CopilotReportTools` wystawia to przez hidden-context
  scoped `report_update_header`.
- Dodano feature-owned `CopilotIncidentReportFactory`, ktory tworzy scaffold
  raportu initial incident analysis z sekcjami `FUNCTIONAL_ANALYSIS` i
  `TECHNICAL_HANDOFF`.
- `CopilotIncidentInitialRunAssembler` przekazuje `initialReport` do
  platformowego runtime, uzywajac tego samego `reportId`, ktory report tools
  dostaja przez hidden `ToolContext`.
- Dodano `CopilotIncidentReportMapper`, ktory mapuje `AnalysisReport` na
  obecny publiczny kontrakt `InitialAnalysisResponse`:
  `header -> detectedProblem`, sekcje raportu -> `functionalAnalysis` i
  `technicalAnalysis`, meta references typu `process`, `boundedContext`,
  `team` -> pola affected oraz meta/section limits/gaps/warnings ->
  `visibilityLimits`.
- `CopilotInitialAnalysisProvider` traktuje snapshot raportu z runtime jako
  pierwsze zrodlo wyniku, a dotychczasowy JSON parser zostawia jako fallback,
  gdy raportu nie ma albo jest niekompletny.
- Incident prompt i runtime skill `incident-analysis-orchestrator` instruuja
  model, aby zapisywal wynik przez `report_update_header`,
  `report_upsert_section`, `report_update_meta` i weryfikowal
  `report_get_current`; fallback JSON zostaje tylko trybem awaryjnym.
- Publiczny result incydentu nie przywraca starych pol `summary`,
  `recommendedAction`, `rationale`, `affectedFunction` ani
  `evidenceReferences`.
- Flow Explorer policy i testy zostaly dopiete do nowego generycznego toola
  `report_update_header`, zeby oba konsumery widzialy ten sam zestaw report
  tools.

### 8. Job state, local workspace i export

Status: [x]

Przed wykonaniem:

- Opiszemy, gdzie raport jest przechowywany w state snapshotach.
- Ustalimy, czy initial result zawiera raport jako osobne pole oraz czy
  feature-specific response zostaje jako view nad raportem.
- Potwierdzimy zakres zmian w local workspace persistence i export/import.

Implementacja:

- Dodano opcjonalne pole `AnalysisReport report` do snapshotow job state
  Incident Analysis i Flow Explorera, obok dotychczasowego feature-specific
  `result`.
- Incident Analysis przenosi raport z `InitialAnalysisResponse` do
  `AnalysisJobState`, a Flow Explorer zapisuje `executionResult.report()`
  bezposrednio w `FlowExplorerJobState`.
- Local workspace persistence zapisuje ostatni snapshot raportu jako czesc
  joba, a lokalne follow-up handlery zachowuja istniejacy raport przy zapisie
  kolejnych wiadomosci.
- Export/import po stronie backendu i frontendu przenosi `report` jako
  addytywne, nullable pole snapshotu; stare eksporty bez raportu normalizuja
  sie do `report = null`.
- Flow Explorer diagnostics dostaly artefakt `analysisReport` typu
  `canonical-report-json`, zeby eksport jawnie pokazywal obecnosc
  kanonicznego raportu.
- Publiczne widoki nadal moga renderowac istniejace feature-specific `result`;
  wspolny komponent raportu zostaje zakresem kroku 9.

### 9. UI minimalne

Status: [x]

Przed wykonaniem:

- Opiszemy minimalne zmiany UI dla pokazania raportu z nowego modelu.
- Potwierdzimy, czy na MVP UI nadal renderuje stare feature-specific pola, czy
  zaczyna uzywac wspolnego komponentu raportu.

Implementacja:

- Dodano wspolny frontendowy `AnalysisReportPanelComponent`, ktory renderuje
  `AnalysisReport`: header, subHeader, markdown summary, sekcje markdown oraz
  meta raportu i sekcji.
- Panel uzywa istniejacego `MarkdownContentComponent`, wiec raportowe markdowny
  sa renderowane tym samym mechanizmem co dotychczasowe wyniki AI.
- Incident Analysis console pokazuje panel raportu pod obecnym
  `AnalysisFinalResultComponent`, jezeli `currentJob.report` istnieje.
- Flow Explorer pokazuje ten sam panel pod obecnym widokiem `AI result`,
  jezeli `snapshot.report` istnieje.
- Obecne feature-specific widoki i akcje kopiowania pozostaja bez refaktoru;
  raport jest widoczna kanoniczna warstwa obok nich.
- Dodano test komponentu panelu oraz asercje widocznosci raportu w Incident
  Analysis i Flow Explorerze.

### 10. Testy i dokumentacja

Status: [x]

Przed wykonaniem:

- Wskazemy testy jednostkowe i integracyjne dla modelu, tooli, policy,
  execution gateway i mapowania feature result.
- Wskazemy dokumenty architektury do aktualizacji.

Implementacja:

- Potwierdzono pokrycie testami dla neutralnego modelu raportu:
  `AnalysisReportModelTest`.
- Potwierdzono pokrycie platformowego report store i tools:
  `CopilotReportSessionStoreTest`, `CopilotReportToolsTest`, w tym callback
  registration, hidden-context scope, odmowe przy braku aktywnego raportu,
  odmowe sekcji spoza allowlisty oraz `report_update_header`.
- Potwierdzono pokrycie runtime wiring:
  `CopilotRunPreparationServiceTest`, `CopilotSdkExecutionGatewayTest`,
  runtime preparation/assembler tests dla Incident Analysis i Flow Explorera.
- Potwierdzono pokrycie mapperow feature result:
  `CopilotIncidentReportMapperTest`, `FlowExplorerReportMapperTest`,
  provider fallback tests oraz job service tests.
- Potwierdzono pokrycie job state, local workspace, export/import i UI:
  job service tests, local run persister/chat handler tests, frontend
  import/export utils, `AnalysisReportPanelComponent`, Incident console i Flow
  Explorer page tests.
- Zaktualizowano `docs/architecture/02-key-decisions.md`: initial result jest
  report-first, JSON-only contract zostaje fallbackiem diagnostycznym.
- Zaktualizowano `docs/architecture/03-runtime-flow.md`: preparation niesie
  `initialReport`, execution gateway rejestruje report store, a job/UI
  przechowuja i pokazuja `report`.
- Zaktualizowano `docs/architecture/04-codex-continuation-guide.md`: kolejny
  agent ma traktowac `AnalysisReport` jako zrodlo prawdy initial result.
- Uruchomiono backendowy targeted report/runtime/job suite oraz frontendowe
  `npm test -- --watch=false` i `npm run build`.
