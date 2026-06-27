# Flow Explorer Implementation Plan

## Cel dokumentu

Ten dokument jest roboczym planem dostarczenia drugiego feature'u platformy:
Flow Explorer. Ma byc aktualizowany krok po kroku w trakcie implementacji.

Flow Explorer ma pozwolic analitykowi, testerowi albo mniej technicznej osobie
poznac system bottom-up przez wybor systemu, wybor endpointu i wygenerowanie
czytelnej dokumentacji use case'u endpointu na podstawie deterministic context,
operational context, GitLab tools i sesji AI.

Rownoleglym celem jest sprawdzenie, czy granice `features`, `aiplatform`,
`agenttools`, `integrations`, `api`, `shared` i `common` sa faktycznie
feature-independent. Flow Explorer jest stress testem architektury, nie
pretekstem do duzego refaktoru bez potrzeby.

## Zasady pracy z planem

- Kazdy punkt implementacyjny najpierw opisujemy w rozmowie: co zmieniamy,
  dlaczego i jakie pliki/pakiety beda dotkniete.
- Implementujemy dopiero po zatwierdzeniu danego kroku.
- Po implementacji aktualizujemy checklisty w tym pliku.
- Jesli w trakcie pracy zmieni sie decyzja produktowa albo architektoniczna,
  dopisujemy ja do sekcji "Decision log".
- Refaktory granic robimy tylko wtedy, gdy Flow Explorer realnie pokazuje
  tarcie, zly import albo brak neutralnego kontraktu.
- Nie reuse'ujemy `features.incidentanalysis` jako generycznego core.
- Nie zachowujemy kompatybilnosci wstecznej dla roboczych kontraktow Flow
  Explorera, GitLab workbencha i nowych tooli, dopoki nie sa stabilnym publicznym
  API produktu.
- Jezeli kolejny krok pokazuje legacy albo poprzedni roboczy wariant, ktory
  przestal byc potrzebny, czyscimy go od razu zamiast utrzymywac rownolegle
  sciezki kompatybilnosci.
- Hidden `ToolContext` nie jest miejscem na feature-specific ani business-scope
  parametry potrzebne modelowi do wyboru danych. Moze niesc runtime metadata,
  identyfikatory sesji, dane do cache'owania wynikow stalych na poziomie sesji,
  korelacje logow/invocation, budzety, feature/runtime profile i inne
  informacje techniczne niewymagajace decyzji modelu. Dane takie jak
  application/system name, environment, branch/ref, endpoint, correlationId,
  projectName, filePath i podobne maja byc jawne dla modelu przez prompt,
  artefakty albo wynik innego toola, a nastepnie przekazywane jako parametry
  toola, z walidacja po stronie backendu i operational context.

## Decyzje startowe

- [x] Flow Explorer jest osobnym feature'em pod `features.flowexplorer`.
- [x] Incident analysis pozostaje pierwszym feature'em, ale nie jest core dla
  nowych analiz.
- [x] User-editable textarea oznacza "instrukcje / doprecyzowanie
  uzytkownika", a nie pelny prompt systemowy.
- [x] Canonical prompt, response contract, policy tools i skills buduje backend.
- [x] User instructions sa traktowane jako dane wejscia, nie jako nadpisanie
  polityki, formatu JSON ani zasad widocznosci.
- [x] Backend buduje minimalny deterministic flow spine dla endpointu przed AI.
- [x] AI poglabia szczegoly przez tools zalezne od celu uzytkownika.
- [x] Initial prompt ma byc optymalizowany kosztowo: compact flow manifest +
  wybrane high-value snippet cards, nie dump listy klas ani pelnych beanow.
- [x] Opcje dokumentacji maja byc male i priorytetyzujace, nie wielka lista
  "opisz wszystko".
- [x] MVP nie wlacza DB tools jako domyslnego zrodla danych runtime; persistence
  opisujemy code-first. DB moze zostac osobnym rozszerzeniem po MVP.
- [x] Runtime skille Flow Explorera beda w
  `src/main/resources/copilot/skills` i beda pisane po polsku z zachowaniem
  technicznych nazw klas, tooli i pol JSON.

## Zweryfikowany stan obecny

### Dokumentacja

- [x] `docs/architecture/00-product-direction.md` opisuje Flow Explorer jako
  planowany drugi feature i dowod platformowosci.
- [x] `docs/architecture/01-system-overview.md` opisuje obecne shared/operator
  API, GitLab endpoint inventory i operational context console.
- [x] `docs/architecture/02-key-decisions.md` wymaga, aby nowe feature'y mialy
  wlasny request/result, prompt, skills, tool policy, hidden context i result
  contract.
- [x] `docs/architecture/03-runtime-flow.md` potwierdza, ze obecny runtime
  incident analysis nie jest docelowym generycznym core.
- [x] `docs/architecture/05-package-dependencies.md` i
  `docs/architecture/06-modular-architecture-roadmap.md` opisuja docelowe
  zaleznosci warstw i wskazuja Flow Explorer jako najlepszy drugi dowod.
- [x] `docs/architecture/08-operational-context-model-tools-and-usage.md`
  opisuje operational context jako knowledge index dla Flow Explorera i
  wskazuje read modele: code-search, implementation, flow i blast-radius.

### Backend i AI platform

- [x] `aiplatform.copilot.runtime.CopilotRunRequest` jest neutralnym wejscem
  runtime: `runReference`, `auth`, `prompt`, `sessionConfigRequest`,
  `artifactContents`, `evidenceSink`, `activitySink`.
- [x] `aiplatform.copilot.runtime.CopilotSessionConfigRequest` przyjmuje
  session id, tools, allowliste, skill directories, model selection i denied
  message.
- [x] `aiplatform.copilot.runtime.execution.CopilotExecutionResult` zwraca
  content i user-visible usage.
- [x] `aiplatform.copilot.tools.CopilotSdkToolFactory` rejestruje callbacki
  Spring tools i dekoruje opisy przez neutralny
  `CopilotToolDescriptionCustomizer`.
- [x] `aiplatform.copilot.tools.context.CopilotToolSessionContext` jest
  neutralnym typem platformowym, ale ma incident-specific convenience
  konstruktor/gettery. To jest punkt do sprawdzenia podczas implementacji Flow
  Explorera.
- [x] Platformowy tool budget jest capability-based i rozpoznaje m.in. GitLab,
  DB, Elasticsearch i Operational Context po prefixach tooli.

### GitLab capability

- [x] `integrations.gitlab.GitLabRepositoryEndpointService` listuje Spring
  endpointy, laczy dane z kodu i OpenAPI oraz zwraca dokumentacje endpointu.
- [x] `integrations.gitlab.GitLabRepositoryEndpoint` zawiera dane potrzebne dla
  UI selecta: `endpointId`, `httpMethods`, `path`, `controllerClass`,
  `handlerMethod`, `filePath`, `lineStart`, `lineEnd`, request/response types,
  annotations, documentation, confidence, limitations i suggested next reads.
- [x] `integrations.gitlab.usecase.GitLabEndpointUseCaseContextService` buduje
  deterministic endpoint use-case context.
- [x] Endpoint use-case context zwraca teraz file-level `symbols` oraz
  method-level `methods` z klasa, podpisem, parametrami, zakresem linii,
  rola, depth, confidence i reason.
- [x] `GitLabEndpointUseCaseFileRole` ma role plikow przydatne dla flow spine:
  `CONTROLLER`, `USE_CASE_SERVICE`, `REPOSITORY_IMPLEMENTATION`,
  `SPRING_DATA_REPOSITORY`, `MAPPER`, `DOMAIN_MODEL`, `WEB_MODEL`,
  `EXTERNAL_CLIENT` itd.
- [x] `agenttools.gitlab.mcp.GitLabMcpTools` wystawia
  `gitlab_list_repository_endpoints` i
  `gitlab_build_endpoint_use_case_context` jako neutralne tools nad integracja.
- [x] `api.gitlab.GitLabRepositorySearchController` ma helper endpointy:
  `/api/gitlab/repository/endpoints` i
  `/api/gitlab/repository/endpoint-use-case-context`.

### Operational context

- [x] `api.operationalcontext.OperationalContextController` wystawia systemy,
  repozytoria i read modele dla FE.
- [x] `integrations.operationalcontext.OperationalContextRepositoryProjectPathResolver`
  potrafi rozwiazac project paths z system hints.
- [x] `agenttools.operationalcontext` wystawia neutralne `opctx_*` tools bez
  incidentowego scope'u.
- [x] Operational context uzywa `system` jako kanonicznego targetu katalogowego.

### Incident job jako wzorzec, nie core

- [x] `features.incidentanalysis.flow.AnalysisOrchestrator` pokazuje wzorzec:
  deterministic context, prepare prompt, analyze, user-visible prompt/activity.
- [x] `features.incidentanalysis.job.AnalysisJobService` pokazuje wzorzec
  in-memory async job + polling + follow-up chat.
- [x] `features.incidentanalysis.job.state.AnalysisJobState` pokazuje wzorzec
  projection job state, tool evidence, AI activity i chat.
- [x] Te klasy nie sa do importowania przez Flow Explorer.

### Frontend

- [x] Angular routes sa w `frontend/src/app/app.routes.ts`.
- [x] Sidebar jest w `frontend/src/app/components/app-shell/app-shell.ts`;
  Flow Explorer istnieje tam jako disabled nav item.
- [x] Spring forward route dla znanych Angular paths jest w
  `src/main/java/pl/mkn/tdw/ui/FrontendRouteController.java`.
- [x] GitLab workbench ma juz UI do endpoint inventory i endpoint use-case
  context; jest to material do reuse'u koncepcyjnego, nie docelowy ekran
  feature'u.

## Guardraile architektoniczne

- [x] Flow Explorer nie importuje `features.incidentanalysis`.
- [x] `features.incidentanalysis` nie importuje Flow Explorera.
- [x] `aiplatform` nie importuje zadnego `features.*`.
- [x] `agenttools` nie importuje zadnego `features.*` ani `aiplatform`.
- [x] `integrations` nie importuje `features`, `agenttools`, `aiplatform`
  ani `api`.
- [x] Shared/operator API w `api.*` nie staje sie orkiestratorem feature'u.
- [x] Feature-specific API Flow Explorera mieszka pod
  `features.flowexplorer.api`.
- [x] Reusable endpoint inventory, use-case context, source/file reads zostaja
  w `integrations.gitlab` i `agenttools.gitlab`.
- [x] Prompt, skills, response parser, feature policy, hidden context,
  artifact rendering i UI contract zostaja w `features.flowexplorer`.
- [x] Nie dodajemy produkcyjnych ani testowych klas pod historyczny root
  `analysis.*`.

## Docelowy UX MVP

### Ekran

Route:

```text
/flow-explorer
```

Glowne sekcje:

- wybor systemu,
- wybor endpointu,
- zakres dokumentacji,
- doprecyzowanie uzytkownika,
- podglad context coverage / prepared prompt,
- progress joba,
- wynik AI,
- follow-up chat.

### Wybor systemu

- [x] Lista internal systems z operational context.
- [x] Search/filter po nazwie, aliasach, runtime/deployment signals i summary.
- [x] Dlugie `system.summary` nie jest renderowane w collapsed row; row ma
  osobna akcje expand/collapse dla opisu systemu.
- [ ] Karta/szczegoly systemu: owner/team, lifecycle, code-search scopes,
  repozytoria, validation findings, open questions.
- [x] UI nie pokazuje GitLaba jako pierwszego pojecia; pokazuje system i
  scope implementacji.

### Wybor endpointu

- [x] Po wyborze systemu backend rozwiazuje repozytoria/code-search scope.
- [x] UI pobiera endpoint inventory dla glownego repo albo repozytoriow w
  scope zgodnie z decyzja implementacyjna.
- [x] Endpoint selector jest searchable comboboxem/lista, nie prostym selectem.
- [x] W collapsed item widac: method, path i summary/description.
- [ ] W collapsed item dodac confidence, jesli testy UX pokaza, ze pomaga w
  wyborze endpointu.
- [x] W expanded/popover item widac: controller, handler,
  request/response types, operationId i source.
- [ ] W expanded/popover item dodac tags, documentation source i suggested
  next reads jako final polish, jesli nie zaszumia wyboru endpointu.
- [ ] Ikona info ma custom tooltip/popover dostepny na hover, focus i click.
- [x] Tooltip pokazuje parametry z opisami, limitations i file/lines.

### Zakres dokumentacji

Presety:

- [x] "Chce zrozumiec endpoint jako analityk"
- [x] "Chce przygotowac testy"
- [x] "Chce sprawdzic wplyw zmiany"
- [x] "Chce techniczny handoff"

Obszary poglebienia:

- [x] Cel biznesowy i flow
- [x] Reguly i walidacje
- [x] Dane i persystencja
- [x] Integracje zewnetrzne
- [x] Scenariusze testowe
- [x] Ryzyka, ograniczenia i pytania otwarte

Zasada:

- [x] UI pozwala wybrac preset i maksymalnie kilka priorytetow poglebienia.
- [x] Checkboxy oznaczaja "pogleb te obszary w pierwszym przebiegu", nie
  "opisz wszystko".
- [x] Reszta moze trafic do "mozliwe dalsze poglebienia" albo follow-up.

### Doprecyzowanie uzytkownika

- [x] Textarea ma label w stylu "Doprecyzuj oczekiwany opis".
- [x] UI nie nazywa tego pola pelnym promptem.
- [x] Backend wstawia tresc jako `userInstructions`.
- [x] Canonical prompt blokuje zmiane kontraktu JSON, tools policy, jezyka
  wyniku i zasad widocznosci przez `userInstructions`.

### Wynik

Wynik ma byc zrozumialy dla analityka/testera:

- [x] krotkie podsumowanie celu endpointu,
- [x] flow krok po kroku,
- [x] reguly biznesowe i decyzje,
- [x] walidacje i bledy,
- [x] request/response contract,
- [x] persystencja code-first,
- [x] integracje zewnetrzne,
- [x] scenariusze testowe,
- [x] ryzyka, open questions i visibility limits,
- [x] source/provenance references,
- [x] confidence i usage.

## Proponowany backend contract MVP

### Feature packages

- [x] `features.flowexplorer.api`
- [x] `features.flowexplorer.job`
- [ ] `features.flowexplorer.flow`
- [x] `features.flowexplorer.context`
- [x] `features.flowexplorer.ai`
- [x] `features.flowexplorer.ai.copilot`
- [ ] `features.flowexplorer.ai.copilot.response`
- [x] `features.flowexplorer.ai.copilot.preparation`
- [x] `features.flowexplorer.ai.copilot.tools.description`

### API endpoints

Feature-owned:

```http
GET  /api/flow-explorer/config
GET  /api/flow-explorer/systems
GET  /api/flow-explorer/systems/{systemId}/endpoints
POST /api/flow-explorer/jobs
GET  /api/flow-explorer/jobs/{jobId}
POST /api/flow-explorer/jobs/{jobId}/chat/messages
```

Do rozstrzygniecia:

- [x] Czy `GET /api/flow-explorer/systems` ma byc feature-owned projection, czy UI
  ma bezposrednio uzyc `/api/operational-context/systems`.
- [ ] Czy endpoint inventory ma zwracac jeden scalony widok z wielu repo, czy
  najpierw tylko primary repository.
- [ ] Jak resolve'owac GitLab branch/ref dla feature'u bez incident evidence.

### Start request

Roboczy shape:

```json
{
  "systemId": "string",
  "endpointId": "string",
  "httpMethod": "GET",
  "endpointPath": "/api/example/{id}",
  "branch": "optional",
  "documentationPreset": "ANALYST_OVERVIEW",
  "focusAreas": ["BUSINESS_FLOW", "VALIDATIONS"],
  "userInstructions": "string",
  "model": "optional",
  "reasoningEffort": "optional"
}
```

### Job snapshot

Roboczy shape:

```json
{
  "jobId": "string",
  "systemId": "string",
  "endpoint": {},
  "branch": "string",
  "aiModel": "string",
  "reasoningEffort": "string",
  "status": "QUEUED|COLLECTING_CONTEXT|ANALYZING|COMPLETED|FAILED|NOT_FOUND",
  "steps": [],
  "contextSnapshot": {},
  "contextSections": [],
  "toolEvidenceSections": [],
  "aiActivityEvents": [],
  "toolFeedback": [],
  "chatMessages": [],
  "preparedPrompt": "string",
  "result": null
}
```

### Follow-up chat request/response

Request:

```json
{
  "message": "string"
}
```

Response:

- endpoint `POST /api/flow-explorer/jobs/{jobId}/chat/messages` zwraca aktualny
  `FlowExplorerJobStateSnapshot`;
- `chatMessages[]` zawiera role `USER`/`ASSISTANT`, status
  `IN_PROGRESS|COMPLETED|FAILED`, tresc, blad, timestampy, prompt
  follow-up oraz message-level `toolEvidenceSections`, `aiActivityEvents` i
  `toolFeedback`;
- chat jest dostepny tylko po jobie `COMPLETED` z `result`;
- backend blokuje drugi follow-up, gdy odpowiedz asystenta jest jeszcze
  `IN_PROGRESS`;
- follow-up prompt jest natural-language, a nie JSON-only. Result contract
  skill jest uzywany dla initial run, ale nie dla follow-up chatu.

Do rozstrzygniecia:

- [ ] Czy reuse'ujemy `shared.evidence.AnalysisEvidenceSection` dla
  `contextSections`, czy dodajemy neutralny `shared.context`/feature-local
  source context model.
- [ ] Czy `AnalysisAiActivityEvent`, `AnalysisAiUsage` i
  `AnalysisAiToolFeedback` pozostaja wystarczajaco neutralne dla Flow
  Explorera.

### AI response contract MVP

Aktualny kontrakt Flow Explorera jest goal-based i zostal doprecyzowany w
`docs/architecture/12-flow-explorer-goal-based-result-contract-plan.md`.
Initial run zwraca twardy JSON z `overview` oraz tylko aktywnymi sekcjami z
`sectionModes`; sekcja flow ma identyfikator `FUNCTIONAL_FLOW` i tytul
`Functional flow`.

Roboczy JSON-only contract:

```json
{
  "goal": "DEEP_DISCOVERY|TEST_SCENARIOS|RISK_DETECTION",
  "audience": "string",
  "overview": {
    "summary": "string",
    "primaryFlow": "string",
    "sourceRefs": ["string"]
  },
  "sections": [
    {
      "id": "FUNCTIONAL_FLOW|VALIDATIONS|PERSISTENCE|INTEGRATIONS",
      "title": "Functional flow|Validations|Persistence|Integrations",
      "mode": "compact|deep",
      "markdown": "string",
      "sourceRefs": ["string"],
      "visibilityLimits": ["string"],
      "openQuestions": ["string"]
    }
  ],
  "globalVisibilityLimits": ["string"],
  "globalOpenQuestions": ["string"],
  "sourceReferences": ["string"],
  "confidence": "high|medium|low"
}
```

`FUNCTIONAL_FLOW.markdown` ma opisywac flow w kolejnosci wystapienia:
autoryzacja/autentykacja, walidacja wejscia, dociagniecie danych, kalkulacje,
reguly funkcjonalne, rozgalezienia, koordynacja/routing, handoffy i efekty
uboczne. Evidence, ograniczenia widocznosci i pytania otwarte nie sa czescia
glownego markdownu sekcji; trafiaja do `sourceRefs`, `visibilityLimits` i
`openQuestions`, ktore UI pokazuje jako osobne zwijane elementy.

## Deterministic context gathering MVP

- [x] Resolve selected system from operational context.
- [x] Resolve code-search scope/repositories for selected system.
- [x] Resolve GitLab group from configuration.
- [x] Resolve branch/ref.
- [x] List endpoint inventory for selected repository/scope.
- [x] Resolve selected endpoint by `endpointId` or method/path.
- [x] Build endpoint use-case context by
  `GitLabEndpointUseCaseContextService`.
- [x] Build minimal flow spine:
  - endpoint/controller/handler,
  - primary use-case service or port,
  - direct collaborators,
  - candidate repositories/entities/clients/validators/mappers,
  - relations,
  - limitations,
  - suggested next reads.
- [x] Build compact flow manifest for prompt:
  - flow node role,
  - file path,
  - method names and `Lx-Ly` ranges,
  - one short reason why the node matters,
  - confidence/limitations only when they change how AI should treat the node.
- [x] Decide selected high-value snippet cards embedded deterministically in
  prompt.
- [x] Snippet cards V1 are scoped per flow node and include only material that
  improves analysis:
  - selected methods participating in the endpoint/use case,
  - minimal nearby context needed to understand the method,
  - explicit omissions such as `// ... omitted earlier lines ...`.
- [ ] Enrich snippet cards later with package, class-level annotations and
  dependency constructor signals when they explain the flow.
- [x] Avoid embedding low-value metadata in prompt:
  - full import lists unless imports carry semantic meaning,
  - unrelated methods in the same bean,
  - full DTO/model/entity bodies unless selected focus area requires them,
  - full class dumps for mappers/repositories/clients when signature/role is
    enough.
- [x] Keep full code exploration focused and AI-guided through GitLab tools.
- [x] Publish context coverage to job state before AI call.

Do rozstrzygniecia:

- [x] Czy backend powinien deterministycznie czytac initial snippets przez
  `GitLabRepositoryFilesByPathApiService`/integration service, czy zostawic
  wszystkie code snippets dla AI tools.
- [x] Jaki jest minimalny "flow spine" wystarczajacy dla modelu bez
  nadmiernego dumpu kodu.
- [x] Jak dobrac token budget/limit snippet cards per preset/focus area.

## AI runtime plan

### Flow Explorer skills

Nowe katalogi:

- [x] `src/main/resources/copilot/skills/flow-explorer-orchestrator`
- [x] `src/main/resources/copilot/skills/flow-explorer-gitlab-tools`
- [x] `src/main/resources/copilot/skills/flow-explorer-operational-context-tools`
- [x] `src/main/resources/copilot/skills/flow-explorer-result-contract`

Zasady skilli:

- [x] Skille po polsku.
- [x] Techniczne identyfikatory zostaja w oryginalnym brzmieniu.
- [x] Orkiestrator najpierw stabilizuje flow spine, potem dobiera poglebienia
  zgodnie z preset/focus areas/user instructions.
- [x] GitLab skill preferuje `gitlab_build_endpoint_use_case_context`,
  `gitlab_read_repository_files_by_path`, outline/chunks i focused reads.
- [x] Operational context skill sluzy do system/process/bounded context,
  ownership, glossary, code-search scope i handoff, ale nie jako dowod
  zachowania kodu.
- [x] Result contract skill wymusza JSON-only i odbiorce analityk/tester.

### Tool policy

- [x] Wlacz GitLab tools potrzebne dla endpoint flow:
  - `gitlab_list_available_repositories`,
  - `gitlab_list_repository_endpoints`,
  - `gitlab_build_endpoint_use_case_context`,
  - `gitlab_read_repository_file`,
  - `gitlab_read_repository_files_by_path`,
  - `gitlab_read_repository_file_outline`,
  - `gitlab_read_repository_file_chunk`,
  - `gitlab_read_repository_file_chunks`,
  - `gitlab_read_java_method_slice`,
  - opcjonalnie `gitlab_find_flow_context`.
- [x] Wlacz `opctx_*` tools dla doprecyzowania katalogu.
- [x] Wlacz `record_tool_feedback`.
- [x] Domyslnie nie wlaczaj DB tools w MVP.
- [x] Nie wlaczaj Elasticsearch tools w MVP, chyba ze pozniejsza decyzja
  rozszerzy feature o runtime/log validation.
- [x] Tool policy jest feature-owned.
- [x] Tool descriptions dla Flow Explorera sa dekorowane feature-owned
  customizerem, bez zmian w neutralnych tool implementations.

### Hidden ToolContext

- [x] Flow Explorer sklada wlasny hidden context:
  - run/job id,
  - Copilot session id,
  - GitLab group,
  - GitLab branch/ref,
  - selected system id,
  - selected endpoint id/method/path,
  - opcjonalnie resolved project/scope metadata.
- [x] Model-facing schema nie przyjmuje `gitLabGroup`, branch/ref ani system
  scope jako recznych parametrow.
- [ ] Jesli `CopilotToolSessionContext` incident convenience API przeszkadza,
  refaktorujemy go do bardziej neutralnego API bez zmiany runtime behavior.

## Architektura do zweryfikowania podczas implementacji

- [x] Czy `CopilotRunRequest` wystarcza bez zmian dla drugiego feature'u.
- [ ] Czy `CopilotSessionConfigRequest` powinien dostac feature-level budget
  preset albo per-run budget override.
- [ ] Czy `CopilotToolSessionContext` wymaga usuniecia incident-specific
  convenience konstruktora/getterow albo przeniesienia ich do incident feature.
- [ ] Czy `AnalysisEvidenceSection` jest nadal dobra nazwa/model dla Flow
  Explorer context/tool evidence.
- [ ] Czy `AnalysisAiActivityEvent`, `AnalysisAiUsage` i
  `AnalysisAiToolFeedback` powinny zostac pod `shared.ai` jako neutralne nazwy,
  czy wymagaja bardziej generic naming.
- [ ] Czy GitLab endpoint/use-case context result jest wystarczajaco neutralny
  dla UI feature'u, czy potrzebny jest feature-local DTO/projection.
- [ ] Czy operational context API potrzebuje dedicated internal systems
  projection dla Flow Explorera.
- [ ] Czy `api.gitlab` helper endpointy zostaja tylko workbench API, a Flow
  Explorer ma wlasna fasade.
- [ ] Czy in-memory job state mozna powtorzyc lokalnie w feature, czy warto
  wydzielic neutralny run/job support po drugim feature.

## Szczegolowa kolejnosc prac

### 0. Plan i dokumentacja

- [x] Zweryfikowac architekture i kluczowe klasy.
- [x] Utworzyc ten plan.
- [ ] Po akceptacji planu dodac krotka wzmianke o Flow Explorer planie do
  `docs/architecture/00-product-direction.md` albo zostawic ten dokument jako
  osobny source of truth dla implementacji.

### 1. Backend vertical skeleton

- [x] Utworzyc lokalne `AGENTS.md` dla `features.flowexplorer`.
- [x] Dodac pakiety feature'u bez logiki AI:
  - request DTO,
  - result DTO,
  - job status DTO,
  - controller,
  - service in-memory job state.
- [x] Dodac endpoint `POST /api/flow-explorer/jobs` i
  `GET /api/flow-explorer/jobs/{id}` z fake/skeleton result.
- [x] Dodac test controller/service dla skeletonu.
- [x] Uruchomic `mvn -q -Dtest=*FlowExplorer* test`.

Approval gate:

- [x] Przed implementacja opisac skeleton API i nazwy pakietow.
- [x] Po zatwierdzeniu implementowac.

### 2. Operational context system selection

- [x] Dodac feature-owned projection internal systems albo uzyc bezposrednio
  `/api/operational-context/systems`.
- [x] Jesli feature-owned, controller deleguje do
  `integrations.operationalcontext`, nie do `api.operationalcontext`.
- [x] Dodac DTO system row dla Flow Explorer UI.
- [x] Dodac testy resolve systemu i filtrowania internal systems.

Decision update:

- [x] Flow Explorer dostaje feature-owned endpoint
  `GET /api/flow-explorer/systems`.
- [x] Endpoint zwraca tylko systemy internal na podstawie `kind`: `internal`,
  `internal-*` oraz `api-gateway`.
- [x] Projection zawiera sygnaly dla UI selecta: nazwe, skrot, `kind`,
  statusy, criticality, summary, aliasy, liczbe repozytoriow, liczbe
  code-search scopes i owner team ids.

Approval gate:

- [x] Przed implementacja opisac, czy dodajemy feature endpoint czy reuse UI
  endpointu operational context.

### 3. Endpoint inventory dla systemu

- [x] Resolve system -> repositories/code-search scope.
- [x] Resolve GitLab group z konfiguracji.
- [x] Resolve branch/ref z requestu albo defaultu w application props.
- [x] Wywolac `GitLabRepositoryEndpointService` dla wybranego repo/scope.
- [x] Zmapowac wynik na UI-friendly endpoint option DTO.
- [x] Dodac support dla tooltip details: documentation parameters,
  limitations, suggested reads.
- [x] Dodac testy inventory mapping i error/empty states.

Decision update:

- [x] `branch`/`ref` jest inputem Flow Explorera.
- [x] Backend ma uzywac defaultu z `application.properties`, gdy request nie
  poda branch/ref.
- [x] UI powinno dostac default branch/ref z backendu i wysylac jawna wartosc
  tylko wtedy, gdy uzytkownik ja zmieni albo potwierdzi.
- [x] Response inventory powinien zwracac `resolvedRef`, zeby uzytkownik
  widzial, z jakiego kodu pochodzi lista endpointow.
- [x] Endpoint inventory jest wystawione jako
  `GET /api/flow-explorer/systems/{systemId}/endpoints`.
- [x] Zakres repozytoriow V1 to repozytoria z `system.references.repositories`,
  `system.codeSearchScope.repositories` oraz semantyczne `codeSearchScopes`
  targetujace wybrany system.
- [x] `POST /api/flow-explorer/jobs` przyjmuje opcjonalny `branch`, aby
  pozniejszy job mogl uzyc tego samego refa co endpoint inventory.

Approval gate:

- [x] Przed implementacja opisac branch/ref decision i zakres repozytoriow V1.

### 3a. GitLab endpoint context method-level model

- [x] Dodac neutralny `GitLabEndpointUseCaseMethodCandidate`.
- [x] Rozszerzyc `GitLabEndpointUseCaseFileCandidate` o `methods`, bez
  usuwania kompatybilnego `symbols`.
- [x] Zbierac metody w traversal tylko wtedy, gdy `GitLabJavaMethodLocator`
  faktycznie rozwiazal metode.
- [x] Zachowac `symbols` jako prosty indeks nazw metod/typow dla istniejacych
  tools i workbencha.
- [x] Wlaczyc pozycje tokenow JavaParsera w bounded
  `GitLabEndpointUseCaseSourceSession`, zeby metody mialy realne zakresy
  linii.
- [x] Dodac testy modelu, kompresora i traversal dla method-level context.
- [x] Dostosowac GitLab Source workbench do `files[].methods`, ale renderowac
  tylko minimalistycznie: nazwa metody i zakres linii `Lx-Ly`.

Decision update:

- [x] Flow Explorer bedzie budowal precyzyjniejszy flow spine na
  `files[].methods`, a nie na samych stringowych `symbols`.
- [x] `symbols` zostaje w publicznym modelu integracji jako backward-compatible
  skrot i suggested-read aid.
- [x] UI workbencha nie pokazuje pelnego podpisu, confidence, depth, reason ani
  roli metody; te dane zostaja w JSON/kontrakcie dla backendu i przyszlego
  context buildera.

Approval gate:

- [x] Przed implementacja opisac additive contract: nowe `methods`, stare
  `symbols` bez breaking change.

### 4. Deterministic endpoint context

- [x] Dodac flow-explorer context service.
- [x] Wywolac `GitLabEndpointUseCaseContextService`.
- [x] Zbudowac `FlowExplorerContextSnapshot`.
- [x] Zbudowac coverage/limitations dla UI.
- [x] Zdecydowac, czy initial snippets sa embedded.
- [x] Dodac testy dla resolved/unresolved endpoint context.

Decision update:

- [x] Step 4A dostarcza deterministyczny `FlowExplorerContextSnapshot` oraz
  `compact flow manifest`, ale nie osadza jeszcze snippet cards.
- [x] `POST /api/flow-explorer/jobs` w tym kroku konczy job statusem `COMPLETED`
  po przygotowaniu context snapshotu i prompt preview; AI execution zostaje
  poza Step 4A.
- [x] `preparedPrompt` jest teraz jawnie oznaczony jako deterministic context
  preview i zawiera `snippetCards: not collected in this step`.
- [x] Snippet cards beda osobnym krokiem 4B, z limitami tokenow per
  preset/focus area i bez pelnego dumpu klas.
- [x] Step 4B dodaje `snippetCards` do `FlowExplorerContextSnapshot`,
  coverage i `preparedPrompt`.
- [x] Snippet cards V1 sa czytane deterministycznie przez
  `GitLabRepositoryPort.readFileChunk`, po jednym chunku per wybrany flow node.
- [x] Budzet V1: 3 karty dla `ANALYST_OVERVIEW` i `TEST_PREPARATION`, 4 karty
  dla `TECHNICAL_HANDOFF` i `CHANGE_IMPACT`, maks. 6000 znakow na karte i
  14000 znakow lacznie.
- [x] Dobor V1 zawsze preferuje `CONTROLLER` i `USE_CASE_SERVICE`, a focus
  areas moga wprowadzic trzeci/czwarty typ: persistence, external integration
  albo validation/model boundary.

Approval gate:

- [x] Przed implementacja opisac minimalny flow spine i limit danych.

### 5. AI preparation i response parser

- [x] Dodac Flow Explorer AI response contracts.
- [x] Dodac JSON-only response parser z fallbackiem.
- [x] Dodac prompt renderer:
  - canonical instruction,
  - user instructions jako ograniczony fragment,
  - selected system/endpoint,
  - focus areas,
  - compact flow manifest,
  - selected snippet cards,
  - tool policy,
  - response schema.
- [x] Dodac artifact renderer: manifest/digest/context artifacts dla Flow
  Explorera.
- [x] Dodac testy prompt/response parsera.

Decision update:

- [x] Step 5A dostarcza feature-local response contract w
  `features.flowexplorer.ai`, bez importu incident analysis.
- [x] Step 5A dostarcza `FlowExplorerAiResponseParser`, ktory przyjmuje JSON
  albo fenced JSON i zwraca kontrolowany fallback z `visibilityLimits`, gdy
  odpowiedz nie spelnia kontraktu.
- [x] Step 5A dostarcza `FlowExplorerPromptPreparationService`, ktory sklada
  canonical prompt z policy, userInstructions, deterministic context coverage,
  compact flow manifest, snippet cards, limitations i JSON response schema.
- [x] `FlowExplorerJobService` nie sklada juz promptu recznie; deleguje to do
  feature-owned prompt preparation service.
- [x] Step 5A nie uruchamia Copilota, nie dodaje skills i nie zmienia
  `aiplatform`.
- [x] Step 5B dodaje `FlowExplorerArtifactService` oraz
  `FlowExplorerPromptPreparation`, z promptem, lista `CopilotRenderedArtifact`
  i mapa `artifactContents` gotowa pod przyszly `CopilotRunRequest`.
- [x] Step 5B renderuje stabilne artefakty:
  - `flow-explorer/context-snapshot.json`,
  - `flow-explorer/compact-flow-manifest.md`,
  - `flow-explorer/snippet-cards.md`,
  - `flow-explorer/coverage.json`,
  - `flow-explorer/response-contract.json`.
- [x] Step 5B nie zmienia delivery mode Copilota; tresc artefaktow pozostaje
  osadzona inline w promptcie, a `artifactContents` sa przygotowane na krok 6.

Approval gate:

- [x] Przed implementacja pokazac response schema i prompt sections.

### 6. Copilot runtime integration

- [x] Dodac Flow Explorer run assembler/factory analogiczny koncepcyjnie do
  incident preparation, ale bez importow incident feature'u.
- [x] Zbudowac Flow Explorer tool session context.
- [x] Zbudowac Flow Explorer tool policy.
- [x] Zbudowac session config request bez skill directories w 6A; Flow
  Explorer skills zostaja krokiem 7.
- [x] Uzyc `CopilotRunPreparationService` i execution gateway przez
  platformowe API.
- [x] Podpiac evidence/activity sinks do job state.
- [x] Dodac testy policy/assembler.

Approval gate:

- [x] Przed implementacja opisac wszystkie enabled tools i hidden context keys.

### 7. Flow Explorer skills

- [x] Dodac runtime skille Flow Explorera po polsku.
- [x] Dodac result contract skill.
- [x] Dodac GitLab tools playbook dla endpoint documentation.
- [x] Dodac opctx playbook dla system/process/ownership grounding.
- [x] Zweryfikowac, ze skille nie zawieraja lokalnych sciezek ani sekretow.

Approval gate:

- [x] Przed implementacja opisac nazwy skilli i ich odpowiedzialnosci.

### 8. Follow-up chat

Status: wykonane w Step 11D, po dzialajacym UI end-to-end.

- [x] Dodac chat request/response DTO.
- [x] Chat dziala dopiero po `COMPLETED`.
- [x] Chat reuse'uje context snapshot, result, previous tool evidence,
  history i hidden scope.
- [x] Obecna implementacja Flow Explorer chatu uruchamia osobna sesje
  Copilota; po platformowym kroku 009 docelowym wzorcem dla nowych follow-upow
  jest resume przez `sessionTarget=EXISTING(copilotSessionId)`, bez fallbacku.
- [x] Tool evidence z chatu jest przypisane do konkretnej odpowiedzi.
- [x] Dodac testy chatu.

Approval gate:

- [x] Przed implementacja opisac kontrakt chatu i scope reuse.

### 9. Frontend skeleton

- [x] Odblokowac nav item Flow Explorer.
- [x] Dodac route `/flow-explorer`.
- [x] Dodac Spring forward route `/flow-explorer/**`.
- [x] Dodac Angular feature folder:
  - page,
  - API service,
  - models,
  - page component; child components dopiero przy realnych kontrolkach Step 10.
- [x] Dodac basic empty/loading/error states.
- [x] Dodac testy routingu/shell.

Approval gate:

- [x] Przed implementacja opisac struktur UI i route.

### 10. Frontend system + endpoint selection

- [x] System searchable select/list.
- [x] Endpoint searchable combobox/list.
- [x] Endpoint info tooltip/popover.
- [ ] Endpoint details drawer/panel; w 10A zastapiony lekkim selected endpoint
  summary i popoverem info.
- [x] Context coverage preview.
- [x] Responsive layout bez nakladania tekstu.
- [x] Testy komponentow.

Approval gate:

- [x] Przed implementacja opisac UI controls i dane w tooltipie.

### 11. Frontend job execution and result

- [x] Presets + focus areas controls.
- [x] User instructions textarea.
- [x] Submit + polling.
- [x] Prepared prompt/context preview.
- [x] AI activity/tool evidence timeline.
- [x] Structured result view.
- [x] Follow-up chat panel jako koncowy etap po MVP end-to-end.
- [ ] Export/import decyzja: MVP optional.
- [x] Testy page/service/utils dla Step 11A/11B/11C/11D.

Approval gate:

- [x] Przed implementacja opisac Step 11A: scope controls, user instructions,
  start job, polling i prompt preview.
- [x] Przed implementacja opisac finalny layout wynikow.
- [x] Przed implementacja opisac trace/timeline jako warstwe transparentnosci,
  nie glowny wynik analizy.
- [x] Przed implementacja opisac follow-up chat jako natural-language
  doprecyzowanie po `COMPLETED`, z backendowym canonical promptem i bez
  JSON-only result contract skilla.

### 12. Weryfikacja i hardening

- [x] `mvn -q "-Dtest=*FlowExplorer*" test`
- [x] `mvn -q "-Dtest=FlowExplorerContextServiceTest,FlowExplorerEndpointInventoryServiceTest,FlowExplorerEndpointInventoryControllerTest,FlowExplorerJobServiceTest,FlowExplorerJobControllerTest,FlowExplorerSystemSelectionServiceTest,FlowExplorerSystemControllerTest,PackageDependencyGuardTest" test`
- [x] `mvn -q "-Dtest=FlowExplorerSnippetCardServiceTest,FlowExplorerContextServiceTest,FlowExplorerJobServiceTest,FlowExplorerJobControllerTest,FlowExplorerEndpointInventoryServiceTest,FlowExplorerEndpointInventoryControllerTest,PackageDependencyGuardTest" test`
- [x] `mvn -q "-Dtest=FlowExplorerAiResponseParserTest,FlowExplorerPromptPreparationServiceTest,FlowExplorerJobServiceTest,FlowExplorerJobControllerTest,*FlowExplorer*" test`
- [x] `mvn -q "-Dtest=FlowExplorerArtifactServiceTest,FlowExplorerPromptPreparationServiceTest,FlowExplorerAiResponseParserTest,FlowExplorerJobServiceTest,FlowExplorerJobControllerTest,*FlowExplorer*" test`
- [x] `mvn -q -Dtest=PackageDependencyGuardTest test`
- [x] `mvn -q "-Dtest=FlowExplorerCopilotRuntimePreparationTest,*FlowExplorer*,PackageDependencyGuardTest" test`
- [x] `mvn -q "-Dtest=CopilotNamedSkillDirectoryResolverTest,*FlowExplorer*,CopilotIncidentSessionConfigRequestFactoryTest,CopilotIncidentInitialPreparationServiceTest,CopilotIncidentInitialPreparationServiceEvidenceReferencePromptTest,CopilotIncidentInitialPreparationServiceCoveragePromptTest,PackageDependencyGuardTest" test`
- [x] `mvn -q "-Dtest=FlowExplorerJobServiceTest,FlowExplorerJobControllerTest,FlowExplorerCopilotRuntimePreparationTest,FlowExplorerAiResponseParserTest,PackageDependencyGuardTest" test`
- [x] `mvn -q "-Dtest=*FlowExplorer*,PackageDependencyGuardTest" test`
- [x] `mvn -q "-Dtest=FrontendPageTest,FlowExplorerCopilotRuntimePreparationTest,CopilotIncidentSessionConfigRequestFactoryTest" test`
- [x] `mvn -q "-Dtest=FlowExplorerConfigControllerTest,FlowExplorerSystemControllerTest,FlowExplorerEndpointInventoryControllerTest,FrontendPageTest" test`
- [x] `mvn -q "-Dtest=CopilotIncidentPromptRendererTest,CopilotIncidentToolSessionContextFactoryTest,CopilotIncidentToolAccessPolicyCoverageTest,CopilotIncidentEvidenceCoverageEvaluatorTest,CopilotIncidentRuntimeSkillsContractTest,CopilotIncidentToolDescriptionCustomizerTest,CopilotIncidentInitialPreparationServiceTest,CopilotIncidentInitialPreparationServiceCoveragePromptTest" test`
- [x] `mvn -q -DskipTests compile`
- [x] `mvn -q test`
- [x] `cd frontend && npm test -- --watch=false`
- [x] `cd frontend && npm run build`
- [ ] Manual UI smoke przez lokalny dev server / Spring Boot.
- [x] Sprawdzic generated static bundle, jesli build produkcyjny jest czescia
  zmiany.

Approval gate:

- [ ] Przed finalnym merge opisac co zostalo zweryfikowane i czego nie udalo
  sie zweryfikowac.

## Testy granic architektury do dodania/rozszerzenia

- [x] `features.flowexplorer` nie importuje `features.incidentanalysis`.
- [x] `features.incidentanalysis` nie importuje `features.flowexplorer`.
- [x] `aiplatform` nie importuje `features`.
- [x] `agenttools` nie importuje `features`.
- [x] `integrations` nie importuje `features`, `agenttools`, `aiplatform`
  ani `api`.
- [x] `api` cross-screen nie importuje `features.flowexplorer`, chyba ze
  endpoint jest przeniesiony do feature-owned API.
- [x] Zamkniety root `analysis.*` nadal nie istnieje.

## Potencjalne refaktory wynikajace z drugiego feature'u

Refaktor dopiero, gdy Flow Explorer realnie potrzebuje zmiany:

- [ ] Przeniesienie incident-specific convenience API z
  `CopilotToolSessionContext` do incident preparation.
- [ ] Dodanie neutralnego `CopilotRunBudgetRequest` albo per-run budget
  override, jesli globalne budzety sa za sztywne.
- [ ] Wydzielenie neutralnego run/job projection dopiero po porownaniu
  incident job i flow explorer job.
- [ ] Zmiana nazwy albo modelu `AnalysisEvidenceSection`, jesli drugi feature
  pokazuje, ze nazwa `Analysis` jest za waska lub semantyka evidence za
  incidentowa.
- [ ] Wydzielenie wspolnych UI komponentow timeline/prompt/usage dopiero po
  rzeczywistym reuse w Flow Explorerze.

## Decision log

### 001. Flow Explorer jako osobny feature

Status: accepted.

Decyzja: implementacja idzie pod `features.flowexplorer`, z wlasnym API, job
state, promptem, skillami, policy i response contractem.

Powod: drugi feature ma zweryfikowac, czy platforma, tools i integracje sa
reusable bez importu incident analysis.

### 002. User textarea jako intencja, nie pelny prompt

Status: accepted.

Decyzja: UI moze pokazac edytowalne pole, ale backend traktuje jego zawartosc
jako `userInstructions` w canonical prompt. Nie pozwala to zmienic response
contractu, policy tools ani zasad widocznosci.

Powod: daje uzytkownikowi kontrole zakresu, ale nie rozbija stabilnego wyniku.

### 003. Deterministic flow spine przed AI

Status: accepted.

Decyzja: backend buduje minimalny endpoint use-case context przed sesja AI.
AI moze poglabiac szczegoly przez tools zgodnie z wybranym celem dokumentacji.

Powod: pelny dump kodu jest kosztowny, a pozostawienie calego discovery AI
zwieksza ryzyko bledzenia.

### 004. Maly zestaw focus areas

Status: accepted.

Decyzja: MVP ma presety i ograniczony zestaw obszarow poglebienia, zamiast
duzej checklisty wszystkiego.

Powod: zbyt szeroki zakres powoduje plytka i zbyt ogolna odpowiedz.

### 005. Branch/ref jako input z defaultem konfiguracyjnym

Status: accepted.

Decyzja: Flow Explorer przyjmuje `branch`/`ref` jako input. Jezeli request go
nie poda, backend uzywa defaultu z `application.properties`.

Powod: user moze eksplorowac konkretny branch, ale MVP nie wymaga od niego
technicznej decyzji przy standardowym uzyciu.

### 006. System selection pokazuje tylko internal systems

Status: accepted.

Decyzja: Flow Explorer pokazuje jako glowne aplikacje tylko systemy internal
na podstawie `kind`: `internal`, `internal-*` oraz `api-gateway`.

Powod: Flow Explorer zaczyna od aplikacji, ktore mozemy badac kodowo. Systemy
zewnetrzne pozostaja kontekstem integracji, nie glownym targetem pierwszego
selecta.

### 007. Endpoint inventory V1 skanuje deterministyczny scope repozytoriow

Status: accepted.

Decyzja: V1 skanuje repozytoria wskazane przez `system.references.repositories`,
`system.codeSearchScope.repositories` oraz semantyczne `codeSearchScopes`
targetujace wybrany system.

Powod: primary repo bywa za waskie, a caly katalog bylby zbyt kosztowny.
Code-search scope daje deterministyczna granice utrzymywana w operational
context.

### 008. Endpoint use-case context zwraca method-level candidates

Status: accepted.

Decyzja: `GitLabEndpointUseCaseContextResult.files[]` zachowuje `symbols`, ale
dostaje dodatkowe `methods` z precyzyjna metoda, podpisem, parametrami,
zakresem linii, rola, depth, reason i confidence. Metody sa dodawane tylko dla
realnie rozwiazanych `GitLabJavaMethodMatch`; typy, modele i nierozwiazane
kandydaty zostaja symbolami/file candidates.

Powod: Flow Explorer ma deterministycznie osadzic glowny flow na konkretnych
metodach uczestniczacych w endpoint use case, bez poczatkowego dumpu calych
beanow i bez heurystycznego zgadywania zakresow po stronie UI.

### 009. Method-level data w UI jako minimalistyczna projekcja

Status: accepted.

Decyzja: GitLab Source workbench moze korzystac z `files[].methods`, ale w
glownym widoku pokazuje tylko `methodName` oraz zakres linii w formacie
`Lx-Ly`. Pelny podpis, typ deklarujacy, role, depth, reason i confidence nie
sa renderowane jako widoczne detale metod.

Powod: dane metod sa potrzebne backendowi i Flow Explorer context builderowi,
ale uzytkownik workbencha ma szybko zobaczyc, ktore metody sa w flow, bez
zasmiecania ekranu informacjami, ktorych realnie nie bedzie weryfikowal.

### 010. Initial prompt jako compact manifest + selected snippet cards

Status: accepted.

Decyzja: initial prompt Flow Explorera nie dostaje pelnej listy klas, pelnych
beanow ani wszystkich danych z `GitLabEndpointUseCaseContextResult`. Backend
buduje kosztowo zoptymalizowany `compact flow manifest`, a do niego dolacza
tylko wybrane `snippet cards`, ktore realnie poprawiaja analize dla
wybranego preset/focus areas/user instructions.

`compact flow manifest` opisuje node'y flow strukturalnie:

- rola node'a w flow,
- path pliku,
- metody i zakresy linii `Lx-Ly`,
- krotki powod dolaczenia,
- confidence/limitations tylko wtedy, gdy AI powinno potraktowac node
  ostrozniej albo inaczej.

`snippet card` jest opcjonalna i ma byc zwiazana z konkretnym node'em flow.
Powinna zawierac tylko material potrzebny do zrozumienia use case'u:

- package,
- class-level annotations, jesli maja znaczenie,
- dependency fields/constructor signals, jesli tlumacza przejscie flow,
- konkretne metody uczestniczace w endpoint/use case,
- minimalny nearby context wymagany do zrozumienia metody,
- jawne markery pominiecia, np. `// ... omitted unrelated methods ...`.

Do initial promptu nie trafia domyslnie:

- pelna lista importow,
- niepowiazane metody tego samego beana,
- pelne DTO/modele/encje,
- pelne klasy mapperow, repozytoriow albo klientow integracyjnych, jezeli
  signature/rola/granica wystarcza,
- dane tylko dlatego, ze sa dostepne w kontrakcie GitLaba.

Powod: Flow Explorer ma minimalizowac koszt tokenow i ryzyko rozmycia wyniku.
Prompt powinien dac AI mape i najcenniejsze fragmenty kodu, a dalsze
poglebienie ma odbywac sie przez GitLab/Operational Context tools zgodnie z
wybranym celem uzytkownika.

### 011. Step 4A jako manifest-only deterministic context

Status: accepted.

Decyzja: pierwszy etap deterministic endpoint context nie osadza jeszcze
snippet cards w prompt preview. Backend buduje `FlowExplorerContextSnapshot`,
`coverage`, `limitations`, `repositories`, `flowNodes`, `relations` oraz
`compact flow manifest`. `preparedPrompt` zawiera jawny marker
`snippetCards: not collected in this step`.

Snippet cards zostaja osobnym krokiem 4B. Maja byc dobierane deterministycznie
z istniejacych integracji GitLaba, ale dopiero po ustaleniu limitow tokenow i
priorytetow per preset/focus area.

Powod: najpierw walidujemy, czy method-level flow spine jest wystarczajaco
precyzyjny i tani. Dopiero potem dodajemy kosztowniejsze fragmenty kodu, z
jasnym budzetem i bez rozmywania odpowiedzi przez nadmiar kontekstu.

### 012. Step 4B jako budgeted method-level snippet cards

Status: accepted.

Decyzja: snippet cards sa teraz czescia `FlowExplorerContextSnapshot` i
`preparedPrompt`. Backend czyta je deterministycznie przez
`GitLabRepositoryPort.readFileChunk`, uzywajac `flowNodes[].methods` jako
zrodla zakresow linii. V1 nie robi jeszcze AST enrichment importow,
class-level annotations ani constructor dependency signals.

Budzet V1:

- `ANALYST_OVERVIEW` i `TEST_PREPARATION`: maksymalnie 3 snippet cards,
- `TECHNICAL_HANDOFF` i `CHANGE_IMPACT`: maksymalnie 4 snippet cards,
- maksymalnie 6000 znakow na snippet card,
- maksymalnie 14000 znakow lacznie,
- 3 linie nearby context wokol wybranych metod.

Priorytet V1: `CONTROLLER`, `USE_CASE_SERVICE`, potem node'y zwiazane z
focus areas. `PERSISTENCE` preferuje repository/domain/projection roles,
`EXTERNAL_INTEGRATIONS` preferuje `EXTERNAL_CLIENT`, a `VALIDATIONS` preferuje
walidacyjna granice model/API, gdy flow spine ja wykryje.

Powod: to daje AI kilka najcenniejszych fragmentow kodu bez dumpu klas i bez
oddawania initial discovery w pelni toolom. Koszt jest jawny w coverage:
`snippetCardCount`, `snippetCharacterCount`, `snippetBudgetReached`.

### 013. Step 5A jako prompt i parser bez Copilot runtime

Status: accepted.

Decyzja: Step 5A dodaje feature-local AI response contract, parser odpowiedzi
oraz canonical prompt preparation, ale nie uruchamia jeszcze Copilota i nie
zmienia `aiplatform`.

`FlowExplorerPromptPreparationService` sklada prompt z sekcji:

- non-negotiable rules,
- user request i `userInstructions` jako dane, nie policy,
- deterministic context coverage,
- compact flow manifest,
- snippet cards,
- known limitations,
- required JSON response contract.

`FlowExplorerAiResponseParser` akceptuje czysty JSON albo fenced JSON. Gdy
odpowiedz AI nie jest poprawnym JSON-em, zwraca kontrolowany fallback z
`visibilityLimits` i `confidence=low`, zamiast przepuszczac niestrukturalna
odpowiedz dalej.

Powod: przed podpieciem kosztownego runtime AI potrzebujemy stabilnego kontraktu
wejscia/wyjscia, testow parsera i gwarancji, ze `userInstructions` nie moga
rozbijac schema ani polityki tools.

### 014. Step 5B jako artifacts przygotowane pod CopilotRunRequest

Status: accepted.

Decyzja: Flow Explorer ma feature-local `FlowExplorerArtifactService`, ktory
renderuje logiczne artefakty w neutralnym modelu `CopilotRenderedArtifact`.
`FlowExplorerPromptPreparation` zwraca teraz:

- `prompt`,
- `artifacts`,
- `artifactContents`.

Artefakty MVP:

- `flow-explorer/context-snapshot.json`,
- `flow-explorer/compact-flow-manifest.md`,
- `flow-explorer/snippet-cards.md`,
- `flow-explorer/coverage.json`,
- `flow-explorer/response-contract.json`.

Prompt nadal osadza najwazniejsze tresci inline. `artifactContents` sa
przygotowane jako payload dla przyszlego `CopilotRunRequest`, ale Step 5B nie
zmienia delivery mode, nie wlacza SDK attachments i nie uruchamia AI.

Powod: krok 6 powinien byc integracja runtime, a nie mieszanka decyzji o tym,
jak renderowac kontekst. Artefakty daja stabilna granice miedzy "co wysylamy
do AI" a "jak odpalamy Copilota".

### 015. Step 6A jako Copilot runtime skeleton bez AI execution

Status: accepted.

Decyzja: Flow Explorer ma feature-local szkic integracji z runtime Copilota w
`features.flowexplorer.ai.copilot.preparation`. Krok 6A buduje:

- `FlowExplorerCopilotToolAccessPolicy` z allowlista GitLab,
  Operational Context i `record_tool_feedback`,
- `FlowExplorerCopilotToolSessionContextFactory` budujacy techniczny session
  context dla runtime Copilota,
- `FlowExplorerCopilotSessionConfigRequestFactory` bez skill directories w
  6A; Step 7 wlacza nazwane katalogi skilli Flow Explorera,
- `FlowExplorerCopilotRunRequestAssembler`, ktory sklada `CopilotRunRequest`
  z promptem i `artifactContents`, ale nie uruchamia AI i nie zmienia job
  lifecycle.

MVP policy nie wlacza DB tools ani Elasticsearch tools. Skille Flow Explorera
sa nadal osobnym krokiem 7, zeby nie mieszac mechaniki runtime z playbookami
uzycia tools.

Boundary finding: historycznie neutralny `GitLabToolScope` w
`agenttools.gitlab` wymagal hidden `correlationId`, co bylo dziedzictwem
incident analysis. Decyzja 031 wycofuje ten kierunek dla Flow Explorera:
`runReference`, system, endpoint, repozytorium, branch/ref, preset/focus areas i
artefakty nie sa juz przekazywane jako hidden business scope tooli. Te dane maja
byc jawne w prompt/artifactach albo w model-facing parametrach przyszlych tooli.

Powod: `CopilotRunRequest` okazal sie wystarczajacy dla drugiego feature'u bez
zmian w `aiplatform`, a feature-owned assembler/policy potwierdzaja granice
pakietow. Nie odpalamy jeszcze AI, bo najpierw trzeba dodac skille,
execution gateway, parser wyniku i sinks job state.

### 016. Step 7 jako feature-scoped runtime skills

Status: accepted.

Decyzja: Flow Explorer ma cztery runtime skille w
`src/main/resources/copilot/skills`:

- `flow-explorer-orchestrator`,
- `flow-explorer-gitlab-tools`,
- `flow-explorer-operational-context-tools`,
- `flow-explorer-result-contract`.

Skille sa po polsku, ale zachowuja techniczne identyfikatory tooli, artefaktow,
pol JSON, klas i endpointow. Orkiestrator stabilizuje flow spine na podstawie
artefaktow i dopiero potem wybiera poglebienia. GitLab skill preferuje focused
reads, outline i chunks zamiast dumpow klas. Operational Context skill daje
system/process/ownership/glossary/handoff, ale nie jest dowodem zachowania
kodu. Result contract skill wymusza JSON-only i audience analityk/tester.

Boundary finding: globalny `CopilotSkillRuntimeLoader` zwraca root
`copilot/skills`, wiec po dodaniu drugiego feature'u jeden root moglby
przypadkowo wystawic skille wielu feature'ow w jednej sesji. Dodany zostal
neutralny `CopilotNamedSkillDirectoryResolver`, ktory wybiera skille po nazwie
i buduje session-specific root zawierajacy tylko wybrane katalogi skilli.
Flow Explorer session config dostaje root z `flow-explorer-*`, a incident
session config root ze swoimi `incident-*` skillami. Nie przekazujemy SDK listy
bezposrednich katalogow pojedynczych skilli, bo built-in `skill` tool moze
zaindeksowac wtedy tylko czesc z nich.

Powod: skills sa czescia kontraktu konkretnego feature'u. Platforma moze
przygotowac runtime directories, ale wybor konkretnych skilli musi pozostac
po stronie feature'a.

### 017. Step 6B jako AI execution w job state

Status: accepted.

Decyzja: `POST /api/flow-explorer/jobs` uruchamia teraz job asynchronicznie.
Snapshot startowy moze byc w `COLLECTING_CONTEXT`, a docelowy flow joba to:

- `DETERMINISTIC_CONTEXT`: backend buduje context snapshot, compact manifest,
  snippet cards, artifacts i canonical prompt,
- `AI_ANALYSIS`: Flow Explorer sklada `CopilotRunRequest`, przygotowuje sesje
  przez `CopilotRunPreparationService`, uruchamia `CopilotSdkExecutionGateway`
  i parsuje odpowiedz przez `FlowExplorerAiResponseParser`,
- `COMPLETED`: `result` zawiera summary pola dotychczasowego DTO oraz pelny
  `FlowExplorerAiResponse` w `aiResponse`, a takze `usage`,
- `FAILED`: deterministic context i prompt pozostaja w snapshot, a blad trafia
  do `errorCode`/`errorMessage`.

Tool evidence, `record_tool_feedback` i activity events sa przypiete do
`FlowExplorerJobState` przez runtime sinks. Nie dodano follow-up chatu,
DB/Elasticsearch tools ani dodatkowego job frameworka.

Powod: Flow Explorer dostaje realny wynik AI, ale nadal uzywa platformowego
runtime jako mechaniki. Feature pozostaje wlascicielem promptu, policy,
hidden contextu, parsera i job projection.

### 018. Step 9 jako lazy frontend skeleton

Status: accepted.

Decyzja: Follow-up chat zostaje odlozony na koniec MVP. Po Step 6B kolejnym
krokiem jest frontendowy skeleton Flow Explorera, aby szybciej zweryfikowac
end-to-end sciezke: system -> endpoint -> user instructions -> job -> wynik.

UI dostaje route `/flow-explorer` w sekcji `Analysis Features`, odblokowany
sidebar item i lazy-loaded page component. Backend dodaje forward
`/flow-explorer/**` tylko dla deep linkow SPA. Feature API jest wydzielone pod
`/api/flow-explorer/*`, zeby lokalny Angular dev server mogl proxy'owac API
przez istniejacy `/api` proxy bez konfliktu z route'em SPA.

Frontend ma feature-local modele i `FlowExplorerApiService` dla istniejacych
endpointow:

- `GET /api/flow-explorer/systems`,
- `GET /api/flow-explorer/systems/{systemId}/endpoints`,
- `POST /api/flow-explorer/jobs`,
- `GET /api/flow-explorer/jobs/{jobId}`.

Szkielet pokazuje tylko basic empty/loading/error states katalogu systemow i
placeholdery nastepnych etapow. Nie implementuje jeszcze searchable selecta,
endpoint comboboxa, tooltipow, focus areas, prompt preview, job polling ani
result view.

Powod: UI granica HTTP i routing sa gotowe pod kolejne kroki, ale ekran nie
udaje jeszcze gotowego workflow. Child components wydzielimy dopiero wtedy,
gdy Step 10 wprowadzi realne kontrolki wyboru systemu i endpointu.

### 019. Step 10A jako endpoint discovery UI

Status: accepted.

Decyzja: Step 10 dzielimy na 10A i pozniejszy detail/result polish. 10A
dostarcza realny discovery workflow w UI:

- searchable system list ladowany z `GET /api/flow-explorer/systems`,
- branch/ref input z defaultem pobieranym z nowego feature endpointu
  `GET /api/flow-explorer/config`,
- automatyczne pobranie endpoint inventory po wyborze systemu,
- lokalne filtrowanie endpointow po metodzie, path, opisie, operationId,
  tagach i handlerze,
- compact coverage preview: resolved ref, repozytoria, pliki i endpoint count,
- endpoint info popover z handlerem, source line range, request/response types,
  parametrami i ograniczeniami,
- selected endpoint summary jako lekka kotwica pod Step 11.

Nie dodajemy jeszcze endpoint details drawer. Popover ma ukrywac szczegoly pod
ikona info, a ekran ma pozostac cichy i gesty. Drawer wroci tylko jesli Step 11
albo przyszle testy UX pokaza, ze operator potrzebuje trwalego porownania raw
danych endpointu.

Powod: najpierw trzeba zoptymalizowac samo znalezienie endpointu. Pelny drawer
na tym etapie grozilby zaszumieniem ekranu danymi, ktorych uzytkownik zwykle
nie bedzie weryfikowal przed uruchomieniem analizy.

### 020. Step 11A jako job execution bridge

Status: accepted.

Decyzja: Step 11 dzielimy na mniejszy krok 11A i pozniejszy result polish.
11A dostarcza w UI:

- preset dokumentacji jako strukturalne pole `documentationPreset`,
- focus areas jako ograniczona lista priorytetow `focusAreas`, maksymalnie 4,
- textarea `User instructions`, ktora nie jest nazywana pelnym promptem,
- `POST /api/flow-explorer/jobs` z danymi wybranego systemu, endpointu,
  branch/ref, presetem, focus areas i `userInstructions`,
- polling `GET /api/flow-explorer/jobs/{jobId}` do statusu `COMPLETED` albo
  `FAILED`,
- kompaktowy job state oraz rozwijany `Prepared prompt preview`.

Nie dodajemy jeszcze structured result view, timeline AI/tool evidence ani
follow-up chatu. Te elementy zostaja kolejnymi krokami, z osobnym opisem
layoutu i osobna akceptacja.

Powod: uzytkownik moze juz przejsc realna sciezke end-to-end z UI do backend
joba, a jednoczesnie ekran nie probuje pokazac pelnego wyniku AI zanim
zaprojektujemy czytelna prezentacje dla analityka/testera.

### 021. Step 11B jako structured result view

Status: accepted.

Decyzja: Step 11B dodaje frontendowy widok wyniku po zakonczeniu joba, bez
zmiany backendowego kontraktu. UI renderuje:

- summary wyniku: status, confidence, endpoint, branch, intent i audience,
- visibility limits jako osobny blok,
- endpoint contract: purpose, parameters, request i response,
- flow steps z opisem plain-language jako glowna trescia,
- technical grounding i source refs schowane w `details`, zeby nie zaszumiec
  ekranu nietechnicznemu odbiorcy,
- focused findings tylko dla niepustych sekcji: business rules, validations,
  persistence, external integrations, test scenarios, risks i open questions,
- source references i usage jako kompaktowy footer.

Gdy backend zwroci `result`, ale `aiResponse` jest `null`, UI pokazuje
kontrolowany fallback zamiast pustego albo uszkodzonego ekranu.

Nie dodajemy jeszcze AI activity/tool evidence timeline ani follow-up chatu.
Timeline wymaga osobnego projektu, bo jest przydatny dla transparentnosci, ale
nie powinien rywalizowac wizualnie z glownym, biznesowo-testerskim wynikiem.

Powod: uzytkownik dostaje czytelny rezultat analizy end-to-end, a szczegoly
techniczne pozostaja dostepne tylko tam, gdzie pomagaja zaufac wynikowi albo
przekazac temat dalej.

### 022. Step 11C jako zwijany Analysis trace

Status: accepted.

Decyzja: Step 11C dodaje frontendowy panel `Analysis trace` pod wynikiem
Flow Explorera, bez zmian backendu. Panel jest zwijany i pokazuje:

- liczniki `aiActivityEvents`, `toolEvidenceSections.items` i `toolFeedback`,
- tool evidence sections jako provider/category + liczba items,
- evidence itemy jako title oraz maksymalnie kilka najwazniejszych attributes,
- chronologicznie posortowane AI activity events: status, title, summary,
  category/type/tool/timestamp,
- tool feedback: target tool, usefulness, expected data, confidence, summary
  i suggested improvement,
- empty state, gdy backend nie zwrocil trace ani evidence.

Nie pokazujemy raw payloadow ani JSON details jako domyslnej tresci. Panel ma
byc warstwa transparentnosci i debugowania, nie konkurencyjnym raportem do
glownego structured result view.

Powod: Flow Explorer powinien budowac zaufanie do wyniku i pokazywac, skad
pochodza dane, ale odbiorca analityk/tester nie powinien byc zmuszony do
czytania technicznego przebiegu AI przy kazdym uzyciu.

### 023. Step 11D jako follow-up chat bez JSON-only contractu

Status: accepted.

Decyzja: Follow-up chat Flow Explorera jest feature-owned i dziala dopiero po
zakonczonym jobie `COMPLETED` z `result`. `POST
/api/flow-explorer/jobs/{jobId}/chat/messages` przyjmuje tylko strukturalne pole
`message` i zwraca aktualny `FlowExplorerJobStateSnapshot`.

Backend buduje `FlowExplorerFollowUpChatRequest` z initial request,
`FlowExplorerContextSnapshot`, initial `result`, dotychczasowego tool evidence,
historii rozmowy i nowego pytania. Kazda odpowiedz asystenta uruchamia nowa
sesje Copilota z tym samym hidden scope i allowlista Flow Explorera.

Initial run nadal uzywa `flow-explorer-result-contract` i wymusza JSON-only
wynik. Follow-up prompt nie wlacza result-contract skilla i instruuje AI, aby
odpowiadalo naturalnym jezykiem dla analityka/testera, chyba ze uzytkownik
wyraznie poprosi o JSON. Tool evidence, activity events i `record_tool_feedback`
z follow-up sa przypisane do konkretnej odpowiedzi asystenta, a nie mieszane z
glownym trace'em initial run.

UI pokazuje follow-up chat pod wynikiem i nad zwijanym `Analysis trace`.
Wiadomosci sa proste: rola, status, tresc i kompaktowe liczniki evidence/events
dla odpowiedzi, bez raw payloadow na ekranie.

Powod: uzytkownik moze doprecyzowac rezultat bez rozbijania kontraktu initial
JSON i bez powtarzania kosztownego discovery od zera. Jednoczesnie follow-up
pozostaje scoped do Flow Explorera i nie przenosi incidentowego chatu jako
generycznego core.

### 024. System row summary jako expandable content

Status: accepted.

Decyzja: `flow-explorer-system-row__summary` nie jest renderowane w collapsed
state listy systemow. Wiersz systemu ma osobna akcje wyboru systemu i osobna
akcje expand/collapse dla summary, zeby dlugie opisy nie rozpychaly katalogu
aplikacji i nie spowalnialy skanowania listy.

Powod: wybor systemu jest czesta, skanujaca interakcja. Summary jest
wartosciowe jako kontekst, ale powinno byc czytane intencjonalnie, a nie
dominowac kazdy wiersz listy.

### 025. Flow Explorer API pod `/api/flow-explorer`

Status: accepted.

Decyzja: frontendowy route ekranu zostaje `/flow-explorer`, a wszystkie
backendowe endpointy feature'u sa wystawiane pod `/api/flow-explorer/*`.
Dotyczy to configu, listy systemow, endpoint inventory, jobow i follow-up
chatu.

Powod: route SPA i API nie powinny dzielic tego samego prefiksu. Przy lokalnym
uruchomieniu Angular dev server obsluguje `/flow-explorer` jako widok, a
istniejacy `proxy.conf.json` przekazuje `/api/*` do Spring Boot na
`localhost:8080`. To usuwa przypadek, w ktorym request API dostawal HTML
aplikacji zamiast JSON.

### 026. GitLab Java Method Slice jako kosztowy fast path

Status: accepted, implemented.

Decyzja: GitLab dostaje reusable capability `gitlab_read_java_method_slice`
oraz operator API `POST /api/gitlab/repository/java-method-slice`. Tool
pobiera pojedynczy plik Java, wybiera jedna albo wiele metod przez
`methodSelectors[]` (`methodName` + opcjonalne `lineStart`) i renderuje
kompaktowy wycinek klasy: package, istotne importy, naglowek typu, pola uzywane
przez wybrane metody, wybrane metody, prywatne helpery lokalne oraz markery
`// ... omitted ...` dla pominietego szumu.

`lineStart` nie jest wymagany. Jezeli selector zawiera tylko `methodName`,
backend zwraca wszystkie overloady tej nazwy w wybranej klasie. To jest
intencjonalne, bo overloady zwykle maja wspolny core albo stanowia lekki
wariant wejscia; wymaganie linii zwiekszaloby koszt poznawczy modelu bez
realnej oszczednosci tokenow. `lineStart` zostaje opcjonalnym zawęzeniem, gdy
operator albo AI chce konkretny wariant.

To nie jest pelny type solver ani globalny slicer projektu. Na start celowo
ograniczamy sie do AST jednego pliku i lokalnej relewancji symboli, bo glowna
wartosc dla Flow Explorera to redukcja tokenow zanim AI poprosi o pelne pliki,
modele albo mappery. Jezeli selector jest niejednoznaczny, backend zwraca
kandydatow metod z liniami, zamiast zgadywac.

Flow Explorer skill ma preferowac `gitlab_read_java_method_slice`, gdy
`Endpoint Use Case Context` zwraca metode i zakres linii dla klasy w flow.
Pelne pliki, chunki i outline zostaja fallbackiem dla parser errors,
nieznanych metod, kontraktow OpenAPI/YAML, konfiguracji albo szerszego
kontekstu, ktory nie miesci sie w metodowym slice.

UI GitLab tool workbench pokazuje `Java Method Slice` obok pozostalych GitLab
tooli i pozwala testowac request po `group`, `projectName`, `branch`,
`filePath`, `methodSelectors` oraz flagach zakresu. Widok use-case contextu ma
lekki skrot z pojedynczej metody oraz `Slice all` dla metod wybranego pliku w
flow tree, zeby operator mogl szybko zweryfikowac dokladnie ten kod, ktory AI
powinno pozniej dociagnac.

Powod: initial context i prompt Flow Explorera maja byc optymalizowane pod
koszt i jakosc. Przekazywanie calej klasy dla kazdego beana marnuje okno
kontekstowe, a sama lista klas/metod nie daje wystarczajacego materialu do
analizy. Method slice daje dobre minimum: konkretny kod use-case'u plus
najblizsze zaleznosci lokalne bez modeli, mapperow i metod niezwiązanych z
endpointem, dopoki uzytkownik albo AI nie potrzebuje glebszego doczytania.

### 027. Initial Flow Explorer context ma uzywac Java Method Slice

Status: accepted, implemented.

Problem: obecny initial context Flow Explorera buduje `flowNodes` przez
`GitLabEndpointUseCaseContextService`, ale `snippetCards` sa nadal pobierane
przez `GitLabRepositoryPort.readFileChunk`. To oznacza, ze initial prompt ma
osobna, mniej precyzyjna sciezke czytania kodu niz nowy reusable
`GitLabJavaMethodSliceService`. W efekcie grozi nam duplikacja logiki
minimalizacji kodu oraz slabsza kontrola kosztu tokenow.

Decyzja: initial deterministic flow powinien korzystac z tej samej integracji
co AI-guided tool, ale przez inna warstwe wywolania:

- initial flow:
  `FlowExplorerContextService -> FlowExplorerSnippetCardService -> GitLabJavaMethodSliceService`,
- AI-guided follow-up / doglebianie:
  `gitlab_read_java_method_slice -> GitLabJavaMethodSliceService`.

`FlowExplorerSnippetCardService` nie powinien wolac MCP toola. Feature moze
zalezec od `integrations.gitlab.source`, ale nie powinien mieszac
deterministic backend flow z warstwa `agenttools.gitlab.mcp`. MCP pozostaje
wrapperem dla modelu, a integracja pozostaje wspolnym zrodlem prawdy dla
renderowania kompaktowego kodu Java.

Docelowy flow initial run:

1. Uzytkownik wybiera system, endpoint, branch/ref, preset, focus areas oraz
   `userInstructions`.
2. Backend rozwiazuje system i repository scope z operational context.
3. Backend buduje `Endpoint Use Case Context` przez
   `GitLabEndpointUseCaseContextService`.
4. Backend zamienia file/method candidates na `flowNodes`.
5. Backend wybiera high-value flow nodes wedlug preset/focus areas i budzetow.
6. Backend dla wybranych node'ow buduje `GitLabJavaMethodSliceRequest` z
   `methodSelectors[]`, domyslnie tylko po `methodName` bez `lineStart`.
7. Backend renderuje snippet cards z odpowiedzi `GitLabJavaMethodSliceService`.
8. Backend sklada artefakty:
   `context-snapshot.json`, `compact-flow-manifest.md`, `snippet-cards.md`,
   `coverage.json`, `response-contract.json`.
9. Backend buduje canonical prompt i dopina task-specific skills Flow
   Explorera w `CopilotRunRequest`.

Zakres implementacji:

- [x] W `FlowExplorerSnippetCardService` wstrzyknac
  `GitLabJavaMethodSliceService`.
- [x] Dla Java flow nodes z metodami budowac jeden method-slice request per
  plik/node zamiast liniowego `readFileChunk`.
- [x] Mapowac `FlowExplorerFlowMethod.methodName()` na
  `GitLabJavaMethodSliceMethodSelector.methodName`; `lineStart` zostawic
  puste w initial flow, chyba ze pozniejsza decyzja pokaze realna potrzebe
  doprecyzowania overloadu.
- [x] Ustawic `includeHelpers`, `includeFields` i `includeImports` na `true`,
  bo initial prompt potrzebuje zrozumiec lokalny flow metody, zaleznosci i
  minimalny kontekst klasy.
- [x] Respektowac istniejace budzety snippet cards:
  maksymalna liczba kart per preset, `MAX_CHARACTERS_PER_CARD` i
  `MAX_TOTAL_CHARACTERS`.
- [x] Zostawic `readFileChunk` jako fallback dla plikow nie-Java, braku metod,
  parser errors, pustego slice albo statusu bez uzytecznego contentu.
- [x] Przeniesc ograniczenia/failure reasons z odpowiedzi method-slice do
  `FlowExplorerSnippetCard.limitations` i coverage.
- [x] Zaktualizowac testy `FlowExplorerSnippetCardServiceTest`, aby
  potwierdzaly method-slice happy path, fallback i budzety.
- [x] Zaktualizowac dokumentacje decyzji po implementacji, wlacznie ze statusem
  tego kroku.

Granice architektoniczne:

- `integrations.gitlab.source` pozostaje reusable capability.
- `features.flowexplorer.context` orkiestruje wybor node'ow i budzet promptu,
  ale nie duplikuje AST slicingu.
- `agenttools.gitlab.mcp` pozostaje wrapperem AI-tool i nie jest wywolywany z
  deterministic initial flow.
- `aiplatform` nie dostaje wiedzy o endpointach, snippet cards ani GitLab
  method slicing.

Kolejnosc pracy dla tego kroku:

1. Przed implementacja opisac w rozmowie problem i proponowane zmiany w
   `FlowExplorerSnippetCardService` oraz testach.
2. Po akceptacji uzytkownika zaimplementowac przepiecie snippet cards na
   `GitLabJavaMethodSliceService`.
3. Uruchomic testy backendowe dla Flow Explorera i Java Method Slice.
4. Zaktualizowac checkboxy oraz status tej sekcji.

### 028. Context snapshot artifact bez duplikacji kodu snippetow

Status: accepted, implemented.

Problem: po przepieciu snippet cards na `GitLabJavaMethodSliceService` pelny
kod snippetow byl obecny w `flow-explorer/snippet-cards.md`, ale
`flow-explorer/context-snapshot.json` serializowal caly
`FlowExplorerContextSnapshot`, czyli rowniez `snippetCards[].content`. Obecny
runtime Copilota wysyla do modelu tylko `prompt`, wiec nie byl to podwojny
koszt tokenow w samym wywolaniu AI, ale byl to nadmiar w artefaktach, job
payloadzie diagnostycznym i potencjalne zrodlo przyszlej duplikacji, gdyby
ktos zaczal inline'owac snapshot.

Decyzja: `context-snapshot.json` jest manifestem deterministycznego kontekstu,
a nie miejscem na pelny kod. Zawiera system, endpoint, branch/ref,
repositories, flowNodes, relations, limitations, suggestedNextReads, coverage
oraz `snippetCards` jako metadane bez `content`. Pelny kod snippetow pozostaje
wylacznie w `flow-explorer/snippet-cards.md`.

Zakres implementacji:

- [x] `FlowExplorerArtifactService.renderContextSnapshot` renderuje
  znormalizowany manifest `FlowExplorerContextSnapshot` zamiast serializowac
  record jeden do jednego.
- [x] `snippetCards` w snapshot artifact zawieraja id, project/file, role,
  methods, line ranges, total lines, truncation, reason, character count,
  limitations oraz `contentArtifact=flow-explorer/snippet-cards.md`.
- [x] `snippet-cards.md` pozostaje jedynym artefaktem z pelnym kodem
  snippetow.
- [x] Job state i UI API nie zostaly zmienione w tym kroku.
- [x] Test `FlowExplorerArtifactServiceTest` potwierdza, ze snapshot artifact
  nie zawiera `public CustomerResponse getCustomer`, a `snippet-cards.md`
  nadal zawiera kod.

Powod: zmniejszamy szum artefaktow i ryzyko przyszlej duplikacji kontekstu bez
zmiany runtime delivery mode i bez ruszania kontraktu UI job state.

### 029. Prompt i skille preferuja initial evidence przed tool calls

Status: accepted, implemented.

Problem: backend deterministycznie przygotowuje `compact-flow-manifest.md` i
`snippet-cards.md` z method slices, ale prompt/skille mogly nadal sugerowac,
ze GitLab tools sa naturalna pierwsza droga do czytania kodu. To grozilo
powtornym pobieraniem tego samego materialu przez model i marnowaniem budzetu
na tool calls, ktore nie wnosza nowych informacji.

Decyzja: initial evidence jest pierwszym zrodlem prawdy dla AI. GitLab tools sa
uzywane dopiero wtedy, gdy brakuje konkretnego materialu do preset/focus areas
albo gdy trzeba rozstrzygnac ograniczenie widocznosci. Dla znanej klasy/metody
preferowanym focused read jest `gitlab_read_java_method_slice`; file chunks,
outline i full file reads pozostaja fallbackiem.

Zakres implementacji:

- [x] `FlowExplorerPromptPreparationService` instruuje AI, aby najpierw
  korzystalo z `compact-flow-manifest.md` i `snippet-cards.md`.
- [x] Prompt zabrania powtarzania GitLab tool calls dla kodu juz widocznego w
  `snippet-cards.md`.
- [x] Prompt wskazuje `gitlab_read_java_method_slice` jako preferowane
  poglebienie dla konkretnych metod.
- [x] Prompt usuwa nieaktualne sformulowanie o artefaktach jako payloadzie
  "przyszlego" `CopilotRunRequest` i opisuje aktualny stan: artifact payload
  sesji plus kluczowe tresci inline.
- [x] `flow-explorer-orchestrator` opisuje `context-snapshot.json` jako
  manifest bez pelnego kodu i `snippet-cards.md` jako initial code evidence.
- [x] `flow-explorer-gitlab-tools` wymaga sprawdzenia artefaktow przed tool
  callem i zakazuje ponownego czytania metod obecnych w snippet cards bez
  konkretnego powodu.
- [x] `FlowExplorerPromptPreparationServiceTest` pilnuje nowych zasad promptu.

Powod: po optymalizacji deterministic contextu trzeba zamknac petle
instrukcyjna. Model ma wydawac budzet na nowe braki, a nie na ponowne
odkrywanie kodu, ktory backend juz dolaczyl w minimalnej formie.

### 030. Tool description customizers sa scoped per feature

Status: superseded by 031, partially implemented.

Problem: `CopilotSdkToolFactory` stosowal globalna liste
`CopilotToolDescriptionCustomizer` bez informacji o feature runtime. Po dodaniu
Flow Explorera oznaczalo to ryzyko, ze incident-specific guidance dla GitLab,
DB, Elasticsearch albo operational context tools zostanie dopisane rowniez do
sesji Flow Explorera. To byl boundary drift miedzy feature policy a neutralna
platforma tooli.

Pierwotna decyzja: platformowy customizer opisow tooli dostaje
`CopilotToolSessionContext`, a feature oznacza sesje neutralnym hidden
`featureId`. Platforma nadal nie zna konkretnych feature'ow; tylko przekazuje
context. Feature-owned customizery same decyduja, czy dany opis dekorowac:

- `featureId=incident-analysis` wlacza
  `CopilotIncidentToolDescriptionCustomizer`,
- `featureId=flow-explorer` wlacza
  `FlowExplorerToolDescriptionCustomizer`.

Zakres implementacji:

- [x] Dodano neutralny hidden context key `AgentToolContextKeys.FEATURE_ID`.
- [x] `CopilotToolDescriptionCustomizer` przyjmuje
  `CopilotToolSessionContext`, `toolName` i `description`.
- [x] `CopilotSdkToolFactory` przekazuje session context do customizerow.
- [x] Incident hidden context ustawia `featureId=incident-analysis`.
- [x] Flow Explorer hidden context ustawia `featureId=flow-explorer`.
- [x] `CopilotIncidentToolDescriptionCustomizer` dziala tylko dla
  `incident-analysis`.
- [x] Dodano
  `features.flowexplorer.ai.copilot.tools.description.FlowExplorerToolDescriptionCustomizer`
  z guidance dla GitLab/opctx tools: najpierw initial artifacts, nie powtarzaj
  snippet cards, preferuj `gitlab_read_java_method_slice`, pelne/chunkowe
  odczyty jako fallback, operational context jako katalog a nie dowod kodu.
- [x] Testy potwierdzaja scoping incident/Flow Explorer customizerow oraz
  przekazywanie session context przez `CopilotSdkToolFactory`.

Korekta decyzji: `featureId` jako hidden `ToolContext` marker tez miesza
runtime metadata z feature policy. Scoping opisow tooli jest nadal potrzebny,
ale powinien byc przekazywany jako jawny runtime/profile context przy skladaniu
sesji, a nie jako hidden data dostepna pozniej dla tool invocation.

Powod: opisy tooli sa model-facing policy/guidance konkretnego feature'u, ale
sam `ToolContext` powinien pozostac mechanika runtime, nie nosnikiem
feature-specific kontraktu.

### 031. Wycofanie feature-specific scope z hidden ToolContext

Status: implemented.

Problem: dotychczasowy kierunek zakladal session-bound hidden scope dla tools:
`correlationId`, `environment`, `gitLabGroup`, `gitLabBranch`, a w kolejnym
kroku rowniez potencjalnie `runReference` i `featureId`. Dla incident analysis
to bylo wygodne, ale po dodaniu Flow Explorera widac, ze ten wzorzec nie
skaluje sie na feature-independent tools. Model nie widzi tych danych jako
parametrow toola, wiec trudniej zrozumiec kontrakt, trudniej testowac
zachowanie i latwo przemycic incidentowe zalozenia do innych analiz.

Nowa decyzja: reusable tools nie powinny dostawac business-scope danych przez
hidden `ToolContext`. Dane potrzebne do wyboru lub pobrania materialu maja byc
jawne dla modelu:

- w canonical prompt,
- w artefaktach,
- w structured response poprzedniego toola,
- jako model-facing parametry kolejnego toola.

Przyklady danych, ktore nie powinny byc ukrytym scope'em dla reusable tools:

- `correlationId`,
- `environment`,
- `applicationName` / `systemId`,
- `branch` / `branchRef`,
- `endpoint`,
- `projectName`,
- `filePath`,
- `gitLabGroup` jako model-facing wybor.

`ToolContext` moze nadal zawierac dane techniczne niewymagajace decyzji modelu:

- `analysisRunId`,
- `copilotSessionId`,
- actual SDK session id,
- `toolCallId`,
- nazwe toola podczas invocation,
- dane do cache'owania wynikow stalych na poziomie sesji,
- korelacje logow/invocation,
- runtime budgets i policy state,
- techniczny session/profile context potrzebny platformie, o ile nie jest
  uzywany jako ukryty business input toola.

Docelowy model dla Flow Explorera:

1. Prompt/artefakty zawieraja jawnie: application/system name, branch/ref,
   endpoint, selected preset/focus areas i initial context.
2. AI uzywa operational context tools po jawnych parametrach, np.
   `applicationName`, aby pobrac repozytoria, code-search scopes, bounded
   context, ownership i glossary.
3. GitLab tools dostaja jawne parametry wynikajace z promptu albo poprzednich
   tool results, np. `applicationName`, `projectName`, `branchRef`, `filePath`,
   `methodSelectors`.
4. Backend waliduje parametry przez operational context, konfiguracje i
   allowlisty. Model moze poprosic o dane, ale backend decyduje, czy zakres jest
   dozwolony.
5. GitLab group i inne sekrety/infrastrukturalne ustawienia pozostaja po
   stronie backendu/configu. Nie musza byc model-facing ani hidden business
   scope'em.

Zakres implementacji:

- [x] Przeniesc scoping tool description customizerow z hidden `featureId` na
  jawny runtime/session profile niedostepny jako hidden input dla tool
  invocation. Zrealizowane przez `CopilotToolDescriptionContext` przekazywany
  jawnie do `CopilotSdkToolFactory.createToolDefinitions(...)`.
- [x] Usunac `featureId` z hidden `ToolContext`.
- [x] Flow Explorer nie powinien ustawiac `CORRELATION_ID` jako job id w hidden
  context.
- [x] GitLab tools powinny stopniowo przechodzic na jawne parametry scope'u
  potrzebne modelowi, z walidacja przez operational context.
- [x] Incident-specific `correlationId` pozostaje domena incident analysis i
  nie powinien byc wymaganiem reusable GitLab/opctx tools.
- [x] Zaktualizowac skille/prompt, aby AI wiedzialo, ze application,
  environment, branch/ref i endpoint sa jawne i powinny byc przekazywane jako
  parametry tooli, jezeli tool ich wymaga.

Notatka implementacyjna 031A: nie utrzymujemy kompatybilnosci wstecznej.
Stary podpis `createToolDefinitions(CopilotToolSessionContext)` zostal usuniety,
a testy zostaly przestawione na jawne przekazywanie profilu opisow tooli.
Runtime invocation nadal dostaje `CopilotToolSessionContext`, ale customizery
opisow nie czytaja juz hidden contextu.

Notatka implementacyjna 031B: Flow Explorerowy hidden context zostal oczyszczony
z business scope'u. `FlowExplorerCopilotToolSessionContextFactory` przekazuje do
`CopilotToolSessionContext` puste feature hidden data, a platforma dopina tylko
techniczne `analysisRunId` i `copilotSessionId`. Usunieto legacy
`FlowExplorerCopilotHiddenToolContextKeys`; dane o systemie, endpointcie, branchu
i artefaktach pozostaja jawne w prompt/artifactach.

Notatka implementacyjna 031C: GitLab MCP tools nie czytaja juz
`gitLabGroup`, `gitLabBranch`, `correlationId` ani `environment` z hidden
`ToolContext`. Narzedzia dostaja jawny `branchRef` oraz opcjonalne
`applicationName`; `projectName`, `filePath`, `chunks` i `methodSelectors`
pozostaja model-facing parametrami konkretnego toola. Backend rozstrzyga
GitLab group przez `GitLabToolScopeResolver`: najpierw z operational context po
`projectName` albo `applicationName`, a potem z `analysis.gitlab.group`.
Nie utrzymujemy kompatybilnosci wstecznej dla starych podpisow tooli. Testy
potwierdzaja, ze GitLab tools dzialaja bez legacy hidden scope'u, a schema
Copilota pokazuje `branchRef`/`applicationName` i nadal ukrywa `group`,
`branch`, `correlationId` oraz `toolContext`.

Notatka implementacyjna 032: Flow Explorer skille, canonical prompt i follow-up
prompt mowia juz tym samym kontraktem co GitLab tools. AI dostaje jawne
`applicationName`, `branchRef`, endpoint, repozytoria, pliki i metody w
promptcie oraz artefaktach, a przy GitLab tool calls ma przekazywac
`branchRef`, `applicationName` i znane `projectName` jako model-facing
parametry. `context-snapshot.json` pokazuje `applicationName` i `branchRef`,
ale nie wystawia `gitLabGroup`; GitLab group pozostaje backend/config scope'em
rozstrzyganym przez operational context albo konfiguracje.

Powod: chcemy mniejszej magii runtime i bardziej jawnego kontraktu miedzy
promptem, operational context i narzedziami. AI powinno wiedziec, jakich danych
uzywa, a backend powinien walidowac zakres zamiast polegac na ukrytym
feature-specific scope.

### 033. Guard architektury API bez importu feature internals

Status: implemented.

Problem: przy domykaniu guardow okazalo sie, ze globalny
`api.ApiExceptionHandler` importowal konkretne wyjatki z
`features.incidentanalysis` i `features.flowexplorer`. To lamalo kierunek, w
ktorym `api.*` jest globalnym HTTP/shared-operator boundary, a feature-specific
endpointy i runtime kontrakty mieszkaja pod `features.*`.

Decyzja: feature-owned wyjatki, ktore maja byc widoczne jako standardowy
`ApiErrorResponse`, dziedzicza po neutralnym
`shared.error.UserFacingApplicationException`. Feature niesie kod bledu i
neutralny typ bledu (`NOT_FOUND`, `CONFLICT`, `SERVICE_UNAVAILABLE`), a
`ApiExceptionHandler` mapuje ten typ na HTTP status bez importowania klas
feature'a. Nie tworzymy kontraktu w `api.*`, bo feature'y nie powinny importowac
`api.*` jako runtime contract.

Zakres implementacji:

- [x] Dodano neutralny `shared.error.UserFacingApplicationException` i
  `UserFacingErrorType`.
- [x] Incident i Flow Explorer user-facing exceptions zostaly przepiete na
  shared error contract bez zmiany publicznych kodow bledow.
- [x] `ApiExceptionHandler` obsluguje jeden neutralny handler dla
  `UserFacingApplicationException`.
- [x] `PackageDependencyGuardTest` blokuje import `features.*` z `api.*`.
- [x] Targeted testy potwierdzaja guard oraz zachowanie kontrolerow job/endpoint.

Powod: drugi feature pokazal, ze globalne HTTP API moze bardzo latwo zaczac
znac szczegoly feature'ow. Shared error contract jest malym, stabilnym
kontraktem uzywanym przez kilka feature'ow i globalny handler, wiec zamyka
granice bez wypychania feature-specific logiki do `api.*`.

### 034. Incident prompts and skills aligned with explicit GitLab scope

Status: implemented.

Problem: po zmianie GitLab tools na jawne parametry scope'u Flow Explorer byl
spojny z nowym kontraktem, ale incident analysis nadal mialo legacy guidance w
initial/follow-up promptach i skillu `incident-analysis-gitlab-tools`.
Szczegolnie mylace byly instrukcje, ze GitLab tools dostaja hidden
`gitLabGroup`/`gitLabBranch` z backendu. Po 031C GitLab tools juz tego nie
czytaja.

Decyzja: incident analysis nadal pokazuje `gitLabBranch` i `gitLabGroup` w
promptach i artefaktach jako kontekst incydentu, ale GitLab tool calls maja
uzywac jawnych parametrow:

- `branchRef` przekazywany z `gitLabBranch` albo poprzedniego GitLab result,
- `projectName` z deterministic evidence, operational context albo poprzednich
  GitLab results,
- opcjonalne `applicationName`, gdy pomaga walidowac repository scope,
- bez przekazywania `gitLabGroup` jako inputu toola.

`CopilotIncidentHiddenToolContextFactory` nie dodaje juz
`GITLAB_BRANCH`/`GITLAB_GROUP` do hidden `ToolContext`; zostaja tylko
incidentowe `correlationId` i `environment` dla tooli, ktore nadal maja taki
kontrakt. Incident GitLab policy dopuszcza `gitlab_read_java_method_slice` jako
focused GitLab tool i preferuje go przed chunk/full-file reads dla znanej
metody Java. Follow-up GitLab tool availability wymaga teraz resolved branch,
a nie resolved group.

Powod: Flow Explorer ujawnil boundary drift, ale GitLab tools sa reusable
capability uzywana tez przez incident analysis. Wszystkie feature'y musza
uzywac tego samego jawnego kontraktu tooli, z group rozstrzyganym po stronie
backendu/configu.

Weryfikacja:

- [x] `mvn -q "-Dtest=CopilotIncidentPromptRendererTest,CopilotIncidentToolSessionContextFactoryTest,CopilotIncidentToolAccessPolicyCoverageTest,CopilotIncidentEvidenceCoverageEvaluatorTest,CopilotIncidentRuntimeSkillsContractTest,CopilotIncidentToolDescriptionCustomizerTest,CopilotIncidentInitialPreparationServiceTest,CopilotIncidentInitialPreparationServiceCoveragePromptTest" test`
- [x] `mvn -q "-Dtest=CopilotSdkToolFactoryDescriptionTest" test`
- [x] `mvn -q test`

### 035. Endpoint Use Case Context request no longer carries tool reason

Status: implemented.

Problem: `reason` z model-facing tool contract przeciekl do recznego UI
workbencha, shared API `/api/gitlab/repository/endpoint-use-case-context` i
`integrations.gitlab.usecase.GitLabEndpointUseCaseContextRequest`. Integracja
nie potrzebuje czytac powodu wywolania; buduje deterministyczny kontekst na
podstawie repository scope, branch, endpoint selector i limitow. To mieszalo
operator/tool evidence z czystym kontraktem integracji.

Decyzja: `reason` jako input zostaje w `agenttools.gitlab.mcp` tam, gdzie jest
powodem wywolania toola dla operatora/evidence/logow, ale nie jest
przekazywany do `GitLabEndpointUseCaseContextService`. Workbench UI i API
operatora nie pokazuja ani nie wysylaja `reason` dla Endpoint Use Case
Context. `reason` w wynikach flow nodes, relacji i unresolved references
zostaje, bo jest explainability rezultatu wygenerowana przez algorytm.

Weryfikacja:

- [x] `mvn -q "-Dtest=GitLabEndpointUseCaseContextModelTest,GitLabEndpointUseCaseContextServiceTest,GitLabEndpointUseCaseEndpointResolverTest,GitLabRepositorySearchControllerTest,GitLabMcpToolsTest,FlowExplorerContextServiceTest,FlowExplorerJobServiceTest" test`
- [x] `npm test -- --watch=false`
- [x] `mvn -q test`

### 036. GitLab Source Resolve no longer accepts base URL from UI

Status: implemented.

Problem: reczny GitLab workbench pokazywal `GitLab Base URL`, a
`GitLabSourceResolveRequest` wymagal `gitlabBaseUrl`. To byl legacy drift:
adres GitLaba jest konfiguracja integracji (`analysis.gitlab.base-url`), razem
z tokenem i SSL policy, a nie operator-facing parametrem requestu.

Decyzja: `GitLabSourceResolveRequest` przyjmuje tylko repository/symbol scope:
`groupPath`, `projectPath`, opcjonalny `ref` i `symbol`. `GitLabSourceResolveService`
czyta base URL z `GitLabProperties` i zwraca czytelny blad konfiguracji, jesli
`analysis.gitlab.base-url` nie jest ustawione. UI workbencha nie pokazuje juz
`GitLab Base URL` ani nie wysyla go w payloadzie. Deterministic GitLab evidence
provider korzysta z tego samego kontraktu source-resolve bez podawania base URL.

Weryfikacja:

- [x] `mvn -q "-Dtest=GitLabSourceResolveServiceTest,GitLabSourceResolveControllerTest,GitLabDeterministicEvidenceProviderTest" test`
- [x] `npm test -- --watch=false`
- [x] `mvn -q test`

### 037. Flow Explorer result export/import in UI

Status: implemented.

Problem: po smoke testach Flow Explorera brakowalo prostego sposobu zachowania
wyniku analizy do pliku i ponownego wczytania go na ekran. Incident analysis
miala juz taki UX, ale implementacja laczyla w komponencie czytanie pliku,
parsowanie JSON-a i pobieranie pliku.

Decyzja: Flow Explorer dostaje wlasny format eksportu
`incident-tracker.flow-explorer-export` w wersji `1`. Nie utrzymujemy
kompatybilnosci wstecznej ani nie importujemy surowych legacy snapshotow bez
envelope. Wspoldzielone zostaly tylko neutralne frontendowe helpery JSON/file:

- `readJsonFile`,
- `downloadJsonFile`,
- `sanitizeFileNamePart`,
- `formatFileTimestamp`.

Incident analysis korzysta teraz z tych samych helperow do samego IO pliku, ale
zachowuje wlasny incidentowy envelope i normalizacje. Flow Explorer ma
feature-owned normalizacje `FlowExplorerJobStateSnapshot`, zeby nie importowac
incidentowych kontraktow ani nie robic wspolnego "analysis export core" zanim
pojawi sie drugi realny reuse.

Zakres implementacji:

- [x] Dodano przycisk `Import` obok `Run Flow Explorer`.
- [x] Dodano przycisk `Export` widoczny dla zakonczonych snapshotow.
- [x] Importowany snapshot jest read-only dla follow-up chat; chat dziala tylko
  dla live joba w backendzie.
- [x] UI pokazuje nazwe wczytanego pliku, aby operator widzial, ze oglada
  snapshot.
- [x] Export/import obejmuje wynik, prepared prompt, trace, tool evidence,
  feedback i chat messages zapisane w job snapshot.
- [x] Import odrzuca niezakończone Flow Explorer joby oraz aktywne odpowiedzi
  follow-up.

Weryfikacja:

- [x] `npm test -- --watch=false`

### 038. Full-width collapsible Flow Explorer workspace sections

Status: implemented.

Problem: po smoke testach ekran Flow Explorera byl zbyt dwukolumnowy i
sidebar `Application catalog` zabieral miejsce pozostalej pracy. Przy rosnacej
liczbie sekcji wynik, job state, trace i chat wymagaja szybkiego zwijania, zeby
operator mogl skupic sie na aktualnym kroku.

Decyzja: Flow Explorer UI przechodzi na jedna pelnoszerokosciowa kolumne.
`Application catalog` jest pierwsza sekcja nad pozostala konfiguracja i
wynikami. Glowne karty Flow Explorera sa natywnymi panelami `details/summary`,
domyslnie otwartymi, zeby nie utrudniac smoke flow, ale kazda moze zostac
zwinieta przez uzytkownika.

Zakres implementacji:

- [x] Usunieto sticky/sidebar layout dla `Application catalog`.
- [x] `flow-explorer-layout` renderuje jedna pelna kolumne.
- [x] Gorne intro, katalog aplikacji, endpoint selection, coverage, endpoint
  list, selected endpoint, analysis request, job state, AI result i follow-up
  chat dostaly wspolny collapsible shell.
- [x] `Analysis trace` pozostaje collapsible przez istniejace `details`.
- [x] Dodano wspolne style `flow-explorer-collapsible` dla naglowkow i body.

Weryfikacja:

- [x] `npm test -- --watch=false`
- [x] Browser smoke: `Application catalog` jest pierwsza sekcja workspace,
  wszystkie glowne panele maja szerokosc kolumny, a zwiniety katalog realnie
  ukrywa zawartosc.

### 039. Align Flow Explorer header pills

Status: implemented.

Problem: po wprowadzeniu zwijalnych sekcji chipy i status pille w naglowkach
Flow Explorera byly rozmieszczone nierowno, bo `summary` dziedziczyl layout
flex z `space-between`, a ikona zwijania byla trzecim elementem flex.

Decyzja: lokalnie dla Flow Explorera naglowek `details/summary` uzywa ukladu
grid `title | chip/status | collapse icon`. Lewa czesc pozostaje elastyczna,
chip albo status pill jest wyrownany do prawej, a ikona zwijania ma stala
koncowa pozycje.

Zakres implementacji:

- [x] Zmieniono `flow-explorer-collapsible__summary` z flexowego rozkladu na
  lokalny grid.
- [x] Bezposrednie `panel-chip` i `status-pill` w summary sa wyrownane do
  prawej i zachowuja pojedyncza linie.

Weryfikacja:

- [x] `npm test -- --watch=false`
- [x] Browser smoke: chipy/status pille w zwijalnych naglowkach sa wyrownane
  do prawej przed ikona zwijania.

### 040. Compact custom selects for Flow Explorer target

Status: implemented.

Problem: `System selection`, `Endpoint selection`, `Endpoint list` i osobny
`Selected endpoint` zajmowaly za duzo miejsca jak na glowny happy path. Ekran
wygladal bardziej jak przegladarka inventory niz proste narzedzie
`application -> endpoint -> scope -> run`.

Decyzja: glowny target Flow Explorera jest jedna sekcja z dwoma customowymi
selectami:

- `Application` wybiera system z operational context i po zmianie resetuje
  aktualny job/result/chat oraz automatycznie laduje endpoint inventory.
- `Endpoint` wybiera endpoint z zaladowanego inventory i po zmianie resetuje
  aktualny job/result/chat bez ponownego pobierania calego inventory.
- `Branch / Ref` zostaje jako kompaktowy input obok selectow; zmiana branch
  czysci endpoint inventory i wymaga swiadomego `Load endpoints`.
- Szczegoly endpointu zostaja dostepne przez kompaktowy info tooltip w opcjach
  endpoint selecta oraz przez maly inline preview wybranego endpointu.

Zakres implementacji:

- [x] Usunieto osobne karty `Application catalog`, `Endpoint list` i
  `Selected endpoint`.
- [x] Dodano custom listbox dla aplikacji z lokalnym filtrem.
- [x] Dodano custom listbox dla endpointow z lokalnym filtrem i info tooltipem.
- [x] Przeniesiono coverage inventory do kompaktowych metryk pod selectami.
- [x] Dodano widoczny inline alert dla bledow ladowania endpoint inventory.
- [x] Dostosowano testy komponentu do nowego UX wyboru.

Weryfikacja:

- [x] `npm test -- --watch=false`
- [x] Browser smoke: zmiana aplikacji laduje endpointy i resetuje wynik, a
  zmiana endpointu resetuje wynik bez reloadu inventory.

### 041. Unified Flow Explorer configuration card

Status: implemented.

Problem: po przejsciu na custom selecty ekran nadal marnowal miejsce na trzy
osobne sekcje: `Flow Explorer`, `Endpoint target` i `Documentation scope`.
Dodatkowo pierwszy rzad mial nierowne wysokosci kontrolek, a preset/focus
areas zajmowaly zbyt duzo miejsca jako kafle.

Decyzja: konfiguracja Flow Explorera jest jedna zwijalna karta. Pierwszy rzad
zawiera `Application`, `Branch / Ref`, `Endpoint` i `Load endpoints`; drugi
rzad zawiera `Preset` i wielowyborczy `Focus areas`. Labelki dostaja kompaktowe
tooltipy z wyjasnieniem pola, zeby nie dokladac stalego tekstu na ekranie.

Zakres implementacji:

- [x] Scalono sekcje start, endpoint target i documentation scope w jedna karte
  `flow-explorer-composer`.
- [x] Ujednolicono wysokosc `Application`, `Branch / Ref`, `Endpoint` i
  `Load endpoints`.
- [x] Zamieniono preset z kafli na custom single-select.
- [x] Zamieniono focus areas z kafli na custom multi-select z limitem
  `maxFocusAreas`.
- [x] Dodano tooltipy przy labelach: application, branch/ref, endpoint,
  inventory, preset, focus areas i instrukcje uzytkownika.
- [x] Usunieto stare template sekcji i helpery CSS/TS dla kafli scope.

Weryfikacja:

- [x] `npm test -- --watch=false`
- [x] Browser smoke: konfiguracja jest jedna karta, pierwszy rzad ma rowne
  wysokosci, preset/focus dzialaja jako selecty.

### 042. Non-technical application select copy

Status: implemented.

Problem: application select pokazywal repo count jako `panel-chip` oraz
techniczne meta typu `internal-service`, status i owner bezposrednio w opcji.
Dla analityka/testera nie jest jasne, dlaczego jedna aplikacja sklada sie z
wielu repozytoriow, bibliotek albo parentow, wiec taki detal zaciemnial wybor.

Decyzja: widoczna opcja aplikacji pokazuje tylko nazwe aplikacji. Opis
aplikacji oraz techniczne szczegoly katalogowe sa dostepne pod ikona info w
tooltipie. Copy licznika katalogu mowi o `applications`, nie o `systems`.

Zakres implementacji:

- [x] Usunieto widoczny `repositoryCount` chip z opcji aplikacji.
- [x] Usunieto widoczne meta `kind/status/owner` z opcji aplikacji.
- [x] Dodano tooltip z opisem aplikacji i schowanymi szczegolami katalogowymi.
- [x] Wybrana aplikacja pokazuje opis/summary zamiast technicznego meta.
- [x] Zmieniono copy pustego wyboru z `systems` na `applications`.

Weryfikacja:

- [x] `npm test -- --watch=false`
- [x] Browser smoke: application select nie pokazuje repo count ani technicznego
  meta na wierzchu, a tooltip pokazuje opis aplikacji.

### 043. Application tooltip positioning polish

Status: implemented.

Problem: tooltip z opisem aplikacji byl dzieckiem listy selecta z
`overflow: auto`, wiec nawet wysoki `z-index` nie wystarczal i tooltip mogl
byc ucinany na krawedzi menu. Reczny panel nad lista rozwiazywal clipping, ale
znikal z pola widzenia po scrollu listy. Dodatkowo ikony tooltipow obok labeli
fieldow wizualnie siedzialy minimalnie nizej niz tekst labela.

Decyzja: uzywamy `MatTooltip` z Angular Material jako jednego mechanizmu
tooltipow dla opcji aplikacji i labeli fieldow. Tooltip jest renderowany jako
overlay poza scrollowanym menu, ma wspolna klase `flow-explorer-tooltip`,
pozycje `right` i customowe style w globalnym `styles.scss`. Menu aplikacji
zachowuje scroll i ma krotszy `max-height`, zeby nie wychodzilo poza ekran.
Ikony pomocy przy labelach dostaja delikatna korekte pionowa, bez zmiany
ukladu fieldow.

Zakres implementacji:

- [x] Dodano wariant menu application selecta z krotszym limitem wysokosci.
- [x] Zastapiono opis aplikacji natywnym `MatTooltip` po prawej stronie opcji.
- [x] Przeniesiono tooltip opisu aplikacji z calego buttona opcji na ikone
  info, zeby hover nazwy nie otwieral tooltipa.
- [x] Zastapiono reczne tooltipy labeli tym samym `MatTooltip`.
- [x] Dodano globalna klase `flow-explorer-tooltip` dla tooltipow Materiala.
- [x] Wyrownano pionowo ikony tooltipow obok labeli fieldow.
- [x] Wyzerowano lokalnie `margin-bottom` globalnego `.field-label` wewnatrz
  `.flow-explorer-field-label`, zeby tekst labela i ikona info mialy wspolna
  linie wyrownania.

Weryfikacja:

- [x] `npm test -- --watch=false`
- [x] Browser smoke: application menu pozostaje w viewportcie, ma `overflow:
  auto`, opcje aplikacji i label help maja podpiety `MatTooltip` z pozycja
  `right` oraz wspolna klase `flow-explorer-tooltip`.
- [x] Browser smoke: tooltip aplikacji jest triggerowany przez
  `.flow-explorer-system-info`/ikone info, a nie przez caly button opcji.
- [ ] Manual hover check: automatyzacja przegladarki nie wymusila renderu
  overlaya Materiala, wiec finalne odczucie hovera warto potwierdzic wizualnie
  na dzialajacym UI.

### 044. GitLab configured group as root namespace

Status: implemented.

Problem: repozytoria z operational context mogly miec `git.group` wskazujace
podgrupe GitLaba, np. synthetic `CRM/PROCESSES`, podczas gdy konfiguracja
aplikacji wskazuje root namespace, np. `CRM`. Dotychczasowa walidacja
porownywala grupy po dokladnym pathu, przez co Flow Explorer i GitLab tools
odrzucaly repozytoria z podgrup mimo ze pelny project path byl poprawnie pod
skonfigurowanym rootem.

Decyzja: `analysis.gitlab.group` traktujemy jako root namespace. Repozytorium
jest zgodne, gdy jego `git.group` albo `git.projectPath` jest tym samym pathem
albo znajduje sie pod tym rootem. Do adapterow GitLaba nadal przekazujemy
skonfigurowany root group, a `projectName` relatywizujemy wzgledem tego rootu,
np. `CRM/PROCESSES/CRM_CUSTOMER_PROCESS` -> `PROCESSES/CRM_CUSTOMER_PROCESS`.

Zakres implementacji:

- [x] Dodano neutralny helper `GitLabPathUtils` w `common`.
- [x] Flow Explorer repository scope akceptuje repozytoria z podgrup root
  namespace i relatywizuje project path.
- [x] GitLab tool scope resolver akceptuje repozytoria z podgrup, ale nie
  zmienia session group z configured root na podgrupe.
- [x] GitLab available repository mapper pokazuje repozytoria z podgrup w
  `gitlab_list_available_repositories`.
- [x] Testy regresji sa CRM-specific i zanonimizowane.

Weryfikacja:

- [x] `mvn -q "-Dtest=FlowExplorerEndpointInventoryServiceTest,GitLabMcpToolsTest" test`
- [x] `rg -n "CLP|clp|AgreementController"` na dotknietych testach nie zwraca
  nowych fixture'ow z realnymi nazwami.

## Open questions

- [ ] Czy result ma miec source refs jako stringi w MVP, czy strukturalny
  kontrakt z file/method/line/toolCallId?
- [x] Czy Flow Explorer ma miec import/export joba w MVP? Decyzja 037:
  tak, jako frontendowy zapis zakonczonego `FlowExplorerJobStateSnapshot` we
  wlasnym envelope feature'u.
- [ ] Czy DB tools maja wejsc jako V2 focus area "dane runtime", czy zostaja
  poza tym feature'em?
- [x] Czy `GitLabToolScope` powinien zostac zrefaktorowany tak, aby
  `correlationId` bylo opcjonalne albo zastapione neutralnym run/job id dla
  feature'ow innych niz incident analysis? Decyzja 031: nie zastępujemy tego
  ukrytym `runReference`; wycofujemy feature/business scope z hidden
  `ToolContext` i przechodzimy na jawne parametry tooli.
