# Universal Analysis Event Model Migration Plan

## Cel dokumentu

Ten dokument opisuje plan migracji obecnego incident analysis do uniwersalnego
modelu `run -> events -> artifacts/result/chat`, ktory bedzie mogl obslugiwac
wiele feature'ow analitycznych.

Najblizszy drugi feature to analiza flow/use case'u z kodu: analityk podaje
zadanie w initial input, a Copilot ma odkryc i opisac end-to-end flow,
mikroserwisy, endpointy, reguly funkcjonalne, obiekty danych i integracje
wynikajace z implementacji.

Nie utrzymujemy kompatybilnosci wstecznej publicznego kontraktu job API ani
eksportu JSON podczas tej migracji. Mozemy jednak robic kroki wewnetrzne tak,
zeby kazdy etap byl kompilowalny, testowalny i mozliwy do review.

## Problem do rozwiazania

Aktualny model prezentacji analizy sklada sie z kilku rownoleglych mechanizmow:

- deterministic evidence pipeline publikuje lifecycle krokow przez listener,
- job state zapisuje `steps` i `evidenceSections`,
- Copilot SDK events sa mapowane na `AnalysisAiActivityEvent`,
- tool invocation ma osobne Spring events `Started`/`Finished`,
- GitLab/DB tool capture mapuje wyniki tools na `toolEvidenceSections`,
- frontend scala `aiActivityEvents` i `toolEvidenceSections` w jedna liste
  pracy AI przez `toolCallId`, timestampy i `toolCaptureOrder`.

To dziala dla pierwszego feature'a, ale trudno bedzie utrzymac to dla kolejnych
analiz, bo kontrakt prezentacji jest implicit i rozlany miedzy backend oraz
frontend.

Docelowo przebieg analizy powinien byc jawny jako append-only event log.
Projekcje UI, status krokow, usage, evidence/artifacts i finalny wynik powinny
byc widokami nad tym logiem albo osobnymi zasobami referencjonowanymi przez
eventy.

## Docelowy model mentalny

```text
AnalysisRun
  initialInput
  events[]
  artifacts[]
  result
  chatMessages[]
```

Feature decyduje:

- jaki ma publiczny request,
- jaki ma prompt, skille i tool policy,
- jakie artifacts/evidence tworzy,
- jaki ma finalny result contract,
- jak wyglada hidden tool context.

Warstwy reusable decyduja:

- jak uruchomic Copilota,
- jak wykonac tools,
- jak mapowac techniczne zdarzenia runtime na neutralne eventy,
- jak przekazywac event sink i artifact/evidence sink.

## Docelowe kontrakty

### `shared.analysis` albo `shared.runs`

Preferowana nazwa pakietu do rozstrzygniecia przy implementacji. Ten pakiet ma
trzymac neutralne kontrakty run/event, nie incident-specific DTO.

Minimalny kontrakt:

```java
public record AnalysisRunEvent(
        String eventId,
        String runId,
        String feature,
        AnalysisRunEventScope scope,
        long sequence,
        Instant occurredAt,
        String type,
        String category,
        String status,
        String title,
        String summary,
        AnalysisRunEventCorrelation correlation,
        Map<String, Object> payload
) {
}
```

Proponowane pola pomocnicze:

```java
public record AnalysisRunEventScope(
        String type,      // INITIAL, CHAT
        String id         // runId albo chat message id
) {
}

public record AnalysisRunEventCorrelation(
        String stepCode,
        String provider,
        String evidenceCategory,
        String artifactId,
        String toolCallId,
        String toolName,
        String turnId,
        String interactionId,
        String parentEventId
) {
}
```

Uwagi:

- `sequence` jest nadawane przez projection/recorder joba, nie przez Copilot
  SDK. Jest source of truth dla kolejnosci prezentacji.
- `occurredAt` jest dla czlowieka i metryk, ale nie powinien byc jedynym
  sort key.
- `type` jest stabilnym identyfikatorem maszynowym, np.
  `tool.completed`.
- `category` jest gruba grupa UI/produktowa, np. `TOOL`, `AI`, `EVIDENCE`.
- `status` jest stanem eventu, np. `STARTED`, `COMPLETED`, `FAILED`,
  `INFO`, `SKIPPED`.
- `payload` jest JSON-ready details. Nie powinien przenosic typow Copilot SDK.

### Proponowana taksonomia eventow

```text
run.started
run.completed
run.failed

input.accepted
prompt.prepared

step.started
step.completed
step.failed
step.skipped

evidence.captured
artifact.created

ai.turn.started
ai.message
ai.reasoning
ai.usage
ai.context
ai.turn.completed
ai.error

tool.requested
tool.started
tool.progress
tool.completed
tool.failed
tool.rejected
tool.evidence.captured

chat.message.created
chat.message.completed
chat.message.failed

result.created
```

Ta lista jest startowa. Nowe typy moga dochodzic, ale powinny miec jasny
kontrakt payloadu i test frontendowy/renderingowy.

### Artifacts/evidence

Nie kazde dane powinny byc bezposrednio w payload eventu. Dla wiekszych danych
docelowo event powinien referencjonowac artifact/evidence resource:

```text
artifactId
provider
category
title
contentType
summary
payload/content
```

W pierwszych etapach mozemy nadal uzywac `AnalysisEvidenceSection` jako modelu
artifact/evidence, ale event powinien stac sie kontraktem przebiegu.

## Granice warstw

### Shared

Moze zawierac:

- neutralny model `AnalysisRunEvent`,
- neutralny listener/sink eventow,
- male enumy/statusy tylko jesli nie sa feature-specific.

Nie moze zawierac:

- incident promptu,
- Copilot SDK types,
- GitLab/DB specific mapperow,
- kontrolerow HTTP,
- orchestracji jobow.

### Aiplatform

Moze:

- przyjmowac `Consumer<AnalysisRunEvent>` albo neutralny event sink w
  `CopilotRunRequest`,
- mapowac SDK session events na neutralne AI/runtime eventy,
- mapowac generic tool lifecycle na neutralne tool events,
- utrzymac wewnetrzne Spring events tool invocation jako mechanike platformy.

Nie moze:

- znac incident feature'a,
- znac flow discovery feature'a,
- wybierac feature-specific promptu, tools ani result contractu.

### Feature

Kazdy feature ma:

- wlasny job/run API,
- wlasny result contract,
- wlasny prompt/skille/tool policy,
- wlasny mapping feature facts na event payloady i artifacts.

Incident analysis i flow discovery moga korzystac z tego samego event modelu,
ale nie powinny dziedziczyc od siebie flow orchestration.

## Fazy migracji

### Faza 0: Decyzja kontraktu i test baseline

Cel:

- zamknac nazwy pakietow, podstawowe pola eventu i liste event type'ow startowych,
- zapisac obecne zachowanie UI timeline jako baseline testowy.

Zakres:

- dodac lub rozszerzyc testy frontendowe dla obecnego timeline AI,
- dodac test backendowy pokazujacy obecne mutacje job state dla initial i chat,
- dopisac decyzje w dokumentacji, ze nowy publiczny kontrakt nie musi byc
  kompatybilny wstecz.

Oczekiwany rezultat:

- zespol ma jeden zapisany target event model,
- przed refaktorem wiemy, ktore zachowania timeline nie moga zniknac przez
  przypadek.

Weryfikacja:

- `mvn -q -Dtest=*Job* test` albo celowane testy job state,
- `cd frontend && npm test -- --watch=false`.

### Faza 1: Dodanie neutralnego modelu eventow

Cel:

- wprowadzic neutralny model eventu bez przepinania runtime.

Zakres:

- dodac pakiet `shared.analysis` albo `shared.runs`,
- dodac `AnalysisRunEvent`,
- dodac `AnalysisRunEventScope`,
- dodac `AnalysisRunEventCorrelation`,
- dodac `AnalysisRunEventSink` z `NO_OP`,
- dodac male factory/helpery tylko jesli realnie redukuja duplikacje.

Oczekiwany rezultat:

- reusable warstwy i feature'y moga importowac neutralny event contract,
- shared nadal nie importuje `aiplatform`, `features`, `agenttools`,
  `integrations` ani `api`.

Weryfikacja:

- `mvn -q -Dtest=PackageDependencyGuardTest test`,
- test konstruktora/immutability eventu.

### Faza 2: Event recorder w job state

Cel:

- uczynic event log pierwszoklasowa czescia projekcji joba.

Zakres:

- dodac do `AnalysisJobState` liste `events`,
- dodac wewnetrzny `recordEvent(...)`, ktory nadaje `sequence`,
- dodac `events` do `AnalysisJobStateSnapshot`,
- nie usuwac jeszcze obecnych list, dopoki frontend nie zostanie przepisany
  w tej samej lub kolejnej fazie,
- dodac analogiczny event log dla odpowiedzi chat albo wspolne pole
  `scope=CHAT`.

Oczekiwany rezultat:

- kazdy job ma append-only event log,
- initial i chat moga zapisywac eventy w jednym formacie,
- sequence jest stabilny i monotoniczny w obrebie runa.

Weryfikacja:

- test, ze `run.started`, `step.started`, `step.completed`, `prompt.prepared`,
  `ai.*`, `run.completed` maja rosnacy `sequence`,
- test, ze chat eventy maja `scope=CHAT` i `scope.id=assistantMessageId`.

### Faza 3: Mapping lifecycle incident flow na eventy

Cel:

- przepisac callbacki flow/evidence na eventy runa.

Zakres:

- `AnalysisJobStateListener` nadal moze implementowac
  `AnalysisExecutionListener`, ale kazdy callback powinien nagrywac event:
  - `onProviderStarted` -> `step.started`,
  - `onProviderCompleted` -> `step.completed` + `evidence.captured`,
  - `onAiStarted` -> `step.started` dla AI,
  - `onAiPromptPrepared` -> `prompt.prepared`,
  - final success/failure -> `run.completed` albo `run.failed`.
- status krokow moze pozostac projekcja budowana w job state.

Oczekiwany rezultat:

- deterministic pipeline jest widoczny w tym samym event logu co AI,
- `steps` przestaje byc jedynym zrodlem przebiegu.

Weryfikacja:

- test kolejnosci eventow dla znanego flow,
- test rownoleglego fan-outu Dynatrace/GitLab: eventy maja deterministyczna
  kolejnosc publikacji/projekcji.

### Faza 4: Mapping Copilot SDK activity na eventy uniwersalne

Cel:

- zastapic `AnalysisAiActivityEvent` neutralnym `AnalysisRunEvent`.

Zakres:

- rozszerzyc `CopilotRunRequest` i `CopilotPreparedSession` o `eventSink`,
- w `CopilotSdkExecutionGateway` mapowac SDK events na eventy:
  - `assistant.turn_start` -> `ai.turn.started`,
  - `assistant.reasoning` -> `ai.reasoning`,
  - `assistant.message` -> `ai.message`,
  - `assistant.usage` -> `ai.usage`,
  - `session.usage_info` -> `ai.context`,
  - context/truncation/compaction -> `ai.context`,
  - `session.error` -> `ai.error`,
  - SDK tool execution start/complete -> `tool.started`/`tool.completed`.
- usage accumulator moze zostac jako projekcja do finalnego result/step usage,
  ale usage eventy sa tez w logu.

Oczekiwany rezultat:

- frontend nie potrzebuje `AnalysisAiActivityEvent`,
- Copilot SDK nadal nie wycieka do publicznego API,
- eventy AI moga byc uzyte przez incident analysis i flow discovery.

Weryfikacja:

- test mapowania najwazniejszych SDK eventow na event type/category/status,
- test, ze awaria event sinka nie zabija sesji Copilota.

### Faza 5: Mapping tool invocation i tool evidence na eventy

Cel:

- usunac ukryty kontrakt `toolEvidenceSections + toolCaptureOrder` jako zrodlo
  timeline.

Zakres:

- wewnetrzne `CopilotToolInvocationStarted/FinishedEvent` moga zostac w
  platformie jako Spring eventy mechaniczne,
- feature-specific listenery GitLab/DB po capture powinny nagrywac
  `tool.evidence.captured`,
- event powinien zawierac:
  - `toolCallId`,
  - `toolName`,
  - provider/category evidence,
  - artifact/evidence reference,
  - reason,
  - capture order albo sequence wynikajace z event logu,
  - skrot payloadu user-facing.
- `CopilotToolEvidenceSessionStore` docelowo publikuje artifact/evidence
  update oraz event, zamiast wymagac od UI czytania specjalnych atrybutow.

Oczekiwany rezultat:

- jeden tool call jest widoczny jako ciag:
  `tool.requested -> tool.started -> tool.completed -> tool.evidence.captured`,
- GitLab/DB details nadal sa dostepne jako artifacts/evidence,
- UI koreluje po event correlation, nie po ad hoc atrybutach evidence.

Weryfikacja:

- test GitLab capture: powstaje evidence/artifact i event
  `tool.evidence.captured`,
- test DB capture analogicznie,
- test rejected/failed tool invocation.

### Faza 6: Nowy publiczny snapshot joba

Cel:

- uproscic kontrakt API pod event model bez kompatybilnosci wstecznej.

Docelowy shape initial snapshotu:

```json
{
  "runId": "...",
  "feature": "incident-analysis",
  "status": "RUNNING|COMPLETED|FAILED|NOT_FOUND",
  "current": {
    "eventId": "...",
    "stepCode": "...",
    "title": "..."
  },
  "input": {},
  "events": [],
  "artifacts": [],
  "result": {},
  "chatMessages": []
}
```

Docelowy shape chat message:

```json
{
  "id": "...",
  "role": "USER|ASSISTANT",
  "status": "IN_PROGRESS|COMPLETED|FAILED",
  "content": "...",
  "events": [],
  "artifacts": [],
  "prompt": "..."
}
```

Zakres:

- zastapic `AnalysisJobStateSnapshot` nowym DTO albo gruntownie go zmienic,
- usunac publiczne `aiActivityEvents`,
- usunac publiczne `toolEvidenceSections` jako mechanizm timeline,
- zdecydowac, czy `steps` zostaja jako projekcja czy UI buduje kroki z
  eventow `step.*`.

Oczekiwany rezultat:

- backend API komunikuje jeden model przebiegu,
- export JSON ma nowy, prostszy format bez legacy pol.

Weryfikacja:

- MockMvc dla start/get job,
- test normalizacji eksportu po stronie FE po nowym shape.

### Faza 7: Frontend oparty o event timeline

Cel:

- przeniesc sklejanie przebiegu z ad hoc `aiActivityEvents + toolEvidence` na
  jawne `events`.

Zakres:

- dodac model TS `AnalysisRunEvent`,
- `AnalysisStepsPanelComponent` albo nowy `AnalysisRunTimelineComponent`
  powinien budowac timeline z `events`,
- usunac korelacje po `toolCaptureOrder` z evidence attributes,
- usunac `AnalysisAiActivityEvent` z modeli FE,
- artifacts/evidence renderowac przez references z eventow albo osobne sekcje
  detail.

Oczekiwany rezultat:

- timeline UI dziala dla incident analysis i bedzie mogl dzialac dla flow
  discovery,
- frontend nie zna technicznych atrybutow capture jako kontraktu prezentacji.

Weryfikacja:

- test timeline: AI messages, usage, context, tool lifecycle, tool evidence,
  failed tool,
- test import/export nowego formatu,
- test chat events.

### Faza 8: Wydzielenie generic run UI/service helpers

Cel:

- przygotowac frontend pod wiele feature'ow bez kopiowania calego ekranu.

Zakres:

- wydzielic reusable komponenty:
  - run overview,
  - run timeline,
  - artifact/evidence viewer,
  - prompt panel,
  - chat panel.
- feature screeny skladaja te komponenty z wlasnym copy/result rendererem.
- `AnalysisApiService` moze zostac incident-specific albo powstaje neutralny
  `RunApiClient` parametryzowany URL-em feature'a.

Oczekiwany rezultat:

- nowy feature moze uzyc tego samego timeline i chat UI,
- incident-specific result card zostaje lokalny dla incident feature.

Weryfikacja:

- test komponentow run timeline na neutralnych event fixtures,
- brak importow incident-specific modeli w generic komponentach.

### Faza 9: Drugi feature jako dowod architektury

Cel:

- potwierdzic, ze event model jest uniwersalny, a nie tylko przemianowanym
  incident analysis.

Proponowany feature:

```text
features.flowdiscovery
```

Use case:

- analityk podaje opis zadania, np. "Opisz end-to-end flow zlozenia wniosku X",
- Copilot uzywa GitLab/operational context tools,
- wynik opisuje mikroserwisy, endpointy, reguly funkcjonalne, obiekty danych,
  integracje, kolejnosc wywolan i luki widocznosci,
- follow-up chat reuse'uje run scope i event model.

Minimalny backend:

- `POST /flow-discovery/jobs`,
- `GET /flow-discovery/jobs/{runId}`,
- `POST /flow-discovery/jobs/{runId}/chat/messages`,
- wlasny result DTO:

```json
{
  "summary": "...",
  "entrypoints": [],
  "microservices": [],
  "endpoints": [],
  "businessRules": [],
  "dataObjects": [],
  "integrations": [],
  "sequence": [],
  "openQuestions": []
}
```

Oczekiwany rezultat:

- feature korzysta z `aiplatform.copilot`, `agenttools.gitlab`,
  `integrations.gitlab`, `integrations.operationalcontext`, `shared.ai` i
  wspolnego event modelu,
- nie importuje `features.incidentanalysis`,
- reusable warstwy nie importuja nowego feature'a.

Weryfikacja:

- PackageDependencyGuardTest dla braku zaleznosci miedzy feature'ami,
- test initial run eventow,
- test follow-up chat eventow,
- test prompt/result parsera nowego feature'a.

### Faza 10: Usuniecie legacy mechanizmow

Cel:

- domknac migracje i usunac rownolegle kontrakty.

Zakres:

- usunac `AnalysisAiActivityEvent` i `AnalysisAiActivityListener`, jesli nie
  ma juz konsumentow,
- usunac publiczne `aiActivityEvents`,
- usunac publiczne `toolEvidenceSections` jako timeline source,
- uproscic `AnalysisExecutionListener` albo zastapic go event sinkiem tam,
  gdzie nie jest potrzebny feature-specific callback,
- usunac `toolCaptureOrder` jako publiczny/FE-visible kontrakt,
- zaktualizowac docs architecture/onboarding.

Oczekiwany rezultat:

- istnieje jeden publiczny model przebiegu runa,
- legacy event/activity/evidence merge znika z FE i BE.

Weryfikacja:

- `mvn -q clean test`,
- `cd frontend && npm test -- --watch=false`,
- `mvn -q -DskipTests package`.

## Docelowe kryteria akceptacji migracji

Migracje uznajemy za zakonczona, gdy:

- incident analysis publikuje przebieg przez `events`,
- flow discovery publikuje przebieg przez ten sam event contract,
- frontendowy timeline renderuje oba feature'y bez wiedzy o Copilot SDK,
- chat initial/follow-up uzywa tego samego modelu event scope,
- tool calls i tool evidence sa powiazane przez `correlation.toolCallId`,
- evidence/artifacts sa referencjonowane przez eventy, a nie odwrotnie,
- reusable warstwy nie importuja feature'ow,
- feature'y nie importuja siebie nawzajem,
- export JSON opiera sie na nowym run/event modelu.

## Rzeczy, ktorych nie robic

- Nie robic `features.incidentanalysis` jako generycznego core dla innych
  analiz.
- Nie przenosic incident promptu, coverage heurystyk ani result contractu do
  `shared`.
- Nie robic eventu, ktory niesie klasy Copilot SDK albo adapter-specific DTO.
- Nie opierac kolejnosci UI tylko na timestampach.
- Nie chowac duzych plikow kodu albo wynikow DB w event payload, jesli powinny
  byc artifact/evidence resource.
- Nie dodawac nowej niewidocznej telemetryki. Event log ma byc productized i
  widoczny w job state/UI.
- Nie dodawac Maven modules tylko przy okazji tej migracji. Najpierw kontrakty,
  pakiety i testy zaleznosci.

## Kolejnosc pierwszych praktycznych PR-ow

1. PR: dodac neutralny model `AnalysisRunEvent` i testy kontraktu.
2. PR: dodac event recorder do `AnalysisJobState` i eventy lifecycle incident
   flow, bez zmian FE.
3. PR: przepiac Copilot SDK activity na `AnalysisRunEvent` rownolegle do
   obecnego activity.
4. PR: dodac eventy tool lifecycle/evidence capture.
5. PR: zmienic publiczny snapshot incident job na nowy run/event shape.
6. PR: przepisac frontend timeline na `events`.
7. PR: usunac legacy `aiActivityEvents/toolEvidenceSections` z kontraktu.
8. PR: dodac minimalny `features.flowdiscovery` jako proof of architecture.
9. PR: zaktualizowac dokumentacje runtime/onboarding i guard tests.

