# Modular Architecture Roadmap

## Cel

Ten dokument opisuje, jak projekt przeszedl z historycznego ukladu
`analysis.*` do docelowego modelu platformy AI-augmented system analysis, w
ktorym incident analysis jest jednym z feature'ow zbudowanych na reusable
capability, tools i platformie AI.

To nie byl plan "big bang rename". Kolejnosc prac najpierw wygaszala zle
zaleznosci i stabilizowala kontrakty, a dopiero pozniej przenosila wieksze
pakiety do docelowych nazw. Obecny stan: produkcyjny i testowy root
`analysis.*` sa zamkniete; kolejny dowod architektury to drugi feature albo
spike reuse'u platformy/tools, najlepiej flow explorer, functional logic
explorer albo natural-language data diagnostics.

## Docelowy Model

Docelowa odpowiedzialnosc warstw:

```text
integrations/adapters        <- czyste capability do systemow zewnetrznych
agent-tools / mcp            <- reusable tools nad adapterami
ai-platform/copilot          <- platforma uruchamiania modelu + tools
api/shared-operator          <- cross-screen API dla FE/operatora nad platforma i integracjami
features/incident-analysis   <- konkretna analiza incydentow
features/...                 <- przyszle dedykowane analizy systemow
common/shared                <- male neutralne helpery i kontrakty
```

Docelowy kierunek zaleznosci:

```text
features.* -> ai-platform
features.* -> agent-tools
features.* -> integrations
features.* -> common/shared

api/shared-operator -> ai-platform
api/shared-operator -> integrations
api/shared-operator -> common/shared

ai-platform -> agent-tools
ai-platform -> common/shared

agent-tools -> integrations
agent-tools -> common/shared

integrations -> common/shared
```

Zakazane kierunki:

```text
integrations -> agent-tools
integrations -> ai-platform
integrations -> features.*
integrations -> api/shared-operator

agent-tools -> ai-platform
agent-tools -> features.*
agent-tools -> api/shared-operator

ai-platform -> features.*
ai-platform -> api/shared-operator

features.* -> api/shared-operator

common/shared -> integrations
common/shared -> agent-tools
common/shared -> ai-platform
common/shared -> features.*
common/shared -> api/shared-operator
```

## Shared UI/UX Contracts

Modularnosc feature'ow nie oznacza dowolnosci w UI. Nowy feature powinien
dostarczyc wlasny prompt, policy i merytoryczny result contract, ale
powtarzalne elementy pracy operatora maja byc wspolne kontraktowo i wizualnie:

- kroki runu i ich evidence references,
- activity trace AI, tool calls, tool evidence i feedback tooli,
- follow-up chat,
- usage/cost, visibility limits, confidence, warnings i source refs,
- import/export oraz stany empty/loading/error.

Backendowe kontrakty dla tych elementow powinny byc neutralne i mieszkac w
`shared`, jezeli zasilaja wiecej niz jeden feature. Frontend powinien uzywac
modeli z `core` i komponentow z `components`, a feature screen powinien
mapowac swoje dane, copy i akcje do wspolnego wzorca. Lokalny komponent albo
DTO jest poprawny tylko wtedy, gdy element niesie unikalna semantyke danego
feature'a, a nie tylko inna etykiete tego samego workflow.

## Docelowe Pakiety Java

Nazwy pakietow nie moga miec myslnikow, wiec praktyczny target w Javie:

```text
pl.mkn.incidenttracker.integrations.*
pl.mkn.incidenttracker.agenttools.*
pl.mkn.incidenttracker.aiplatform.copilot.*
pl.mkn.incidenttracker.api.*
pl.mkn.incidenttracker.features.incidentanalysis.*
pl.mkn.incidenttracker.features.<futurefeature>.*
pl.mkn.incidenttracker.common.*
pl.mkn.incidenttracker.shared.*
```

`common` moze pozostac miejscem na male helpery techniczne. `shared` warto
dodac dopiero wtedy, gdy przenosimy stabilny kontrakt domenowo-techniczny
uzywany przez kilka warstw, np. generyczny model evidence albo neutralny
context tool invocation. Nie przenosic wszystkiego do `shared` tylko po to,
zeby uciszyc import graph.

## Ownership Docelowych Warstw

### `integrations`

Warstwa integracji posiada:

- properties i konfiguracje klientow zewnetrznych,
- porty i adaptery REST,
- request/result DTO zewnetrznych capability,
- techniczne wyjatki danej integracji,
- services capability, ktore moga byc wywolane przez provider, tool albo
  shared/operator API.

Warstwa integracji nie posiada:

- evidence pipeline,
- MCP/Spring AI tools,
- promptow,
- Copilot SDK runtime,
- job flow,
- incident-specific heurystyk.

Ten sam adapter musi byc mozliwy do uzycia przez:

- provider evidence,
- tool agenta,
- zwykly endpoint REST,
- przyszly feature analityczny.

### `agenttools`

Warstwa tools posiada:

- neutralne kontrakty request/result/scope dla tools,
- nazwy tools i capability groups,
- mapowanie hidden tool context do scope'u capability,
- Spring AI/MCP exposure,
- delegacje do adapterow albo neutralnych use case'ow.

Warstwa tools nie posiada:

- szczegolow Copilot SDK,
- incident promptow,
- evidence collectorow,
- job state,
- domenowych decyzji konkretnej analizy.

MCP jest jedna z form ekspozycji tools. Jesli kiedys pojawi sie inny agent
runtime, powinien moc uzyc tych samych kontraktow capability bez importu
Copilota i bez importu incident analysis.

### `aiplatform.copilot`

Warstwa platformy AI posiada:

- runtime Copilot SDK,
- model/options provider,
- przygotowanie technicznej sesji na podstawie parametrow feature'a,
- `SessionConfig`, allowliste tools i hidden context jako mechanizmy runtime,
- generic tool invocation handler,
- neutralny kontrakt customizacji opisow tools,
- policies, budget, logging, user-visible usage i lifecycle sesji,
- generic artifact/prompt delivery mechanics tam, gdzie nie sa feature-specific.

Warstwa platformy AI nie posiada:

- incident-specific response contract,
- incident evidence pipeline,
- incident digest jako domenowej tresci,
- incident promptu ani incident skilli jako wlasnosci platformy,
- decyzji, ktore tools sa wlaczone dla konkretnego feature'a,
- semantyki GitLab/DB/Elastic jako stalego zestawu analizy incydentu,
- `correlationId` jako zalozenia platformowego,
- wiedzy, ze jedynym feature'em jest analiza incydentu.

Platforma powinna byc parametryzowanym runtime. Feature dostarcza gotowe dane
uruchomienia, a platforma tylko wykonuje je przez Copilot SDK.

Docelowy input platformy istnieje jako pierwszy inkrement
`CopilotRunRequest`. Dzis niesie prompt, parametry sesji, logical artifacts,
evidence sink i `runReference`; docelowo powinien zostac rozszerzony tak, aby
mogl niesc:

Kolejny inkrement jest rowniez w kodzie: incident preparation sklada
`CopilotSessionConfigRequest` przez `CopilotIncidentSessionConfigRequestFactory`.
Dzieki temu wybor skilli, model options i incidentowy komunikat odmowy tooli
sa lokalne dla feature preparation, a runtime factory tylko buduje techniczna
konfiguracje SDK.
Incident preparation sklada tez `CopilotToolSessionContext` przez
`CopilotIncidentToolSessionContextFactory`, wiec generowanie run/session id i
hidden scope initial/follow-up jest lokalne dla feature preparation.
Initial i follow-up tool policy przechodza przez `CopilotIncidentToolAccessPolicyFactory`,
wiec decyzje o wlaczeniu capability pozostaja w incident preparation zamiast w
assemblerach runtime requestu.
Follow-up nie przechodzi juz przez osobny artifact request ani renderer promptu;
`CopilotIncidentFollowUpRunAssembler` sklada resume request z
`sessionTarget=EXISTING(copilotSessionId)` i sama wiadomoscia operatora.
`CopilotIncidentRunRequestFactory` sklada finalny `CopilotRunRequest`, wiec
mapowanie rendered artifacts na platformowy input runtime jest w jednym miejscu.
`CopilotIncidentInitialRunAssembler` sklada juz
`CopilotIncidentInitialRunAssembly`, ktory trzyma `CopilotRunRequest`
oddzielnie od szczegolow przygotowania, zeby techniczne dane preparation nie
wchodzily do neutralnego requestu runtime.
`CopilotIncidentFollowUpRunAssembler` zwraca juz bezposrednio neutralny
`CopilotRunRequest`, wiec chat nie ma osobnego follow-up-only kontraktu
wykonania przed wejsciem do platformowego runtime.
`CopilotRunPreparationService` jest neutralnym wejsciem runtime
`CopilotRunRequest -> CopilotPreparedSession`; incident preparation sklada run
request, a runtime service przygotowuje techniczna sesje SDK.
`CopilotSdkExecutionGateway` mieszka w `aiplatform.copilot.runtime.execution`
i wykonuje neutralna `CopilotPreparedSession`; zwraca `CopilotExecutionResult`
z trescia odpowiedzi i user-visible `shared.ai.AnalysisAiUsage` agregowanym z
eventow SDK.
Rendered artifacts przechodza przez neutralny runtime type
`CopilotRenderedArtifact`, a mapowanie do `CopilotRunRequest.artifactContents`
robi platformowy `CopilotArtifactContentMapper`. Incident preparation nadal
renderuje sama tresc manifestu, digestu i evidence artifacts.

- prompt albo gotowe message/input do modelu,
- model options, np. model i reasoning effort,
- skill resources albo skill directories wybrane przez feature,
- tool definitions/callbacks oraz allowliste `availableTools`,
- hidden tool context jako neutralna mapa, nie jako incidentowy record,
- techniczne policies/budget hooks, jesli maja dotyczyc tej sesji,
- evidence sink/listeners dla wyniku tool invocation,
- response handler albo parser wyniku feature'a.

Feature decyduje, jakie evidence ma znaczenie, jakie tools sa dostepne, jakie
skille sa ladowane i jaki jest kontrakt odpowiedzi. Platforma nie powinna
rekonstruowac tych decyzji na podstawie `correlationId`, nazw tooli albo
incidentowych coverage heurystyk.

### Aktualna Mapa Ownership Incident Copilot

Pierwszy incident-specific slice jest juz fizycznie przeniesiony do
`features.incidentanalysis.ai.copilot`. Ma to utrzymac Copilot runtime jako
platforme parametryzowana przez feature, a nie wlasciciela incident promptu.

`features.incidentanalysis.ai.copilot.preparation` zawiera:

- `CopilotIncidentInitialPreparationService`
- `CopilotIncidentFollowUpPreparationService`
- `CopilotIncidentInitialRunAssembler`
- `CopilotIncidentFollowUpRunAssembler`
- `CopilotIncidentInitialRunAssembly`
- `CopilotInitialAnalysisPreparation`
- `CopilotIncidentPromptRenderer`
- `CopilotIncidentDigestService`
- `CopilotIncidentToolAccessPolicy`
- `CopilotIncidentToolAccessPolicyFactory`
- `CopilotIncidentHiddenToolContextFactory`
- `CopilotIncidentToolSessionContextFactory`
- `CopilotIncidentSessionConfigRequestFactory`
- `CopilotIncidentRunRequestFactory`
- `CopilotIncidentArtifactService`
- `CopilotIncidentArtifactFormatVersion`
- `CopilotIncidentArtifactItemIdGenerator`

Incident artifact rendering, artifact format version i item-id policy sa
jawnie feature-owned, bo dotycza incident manifestu, digestu i evidence
artifacts. Neutralny runtime artifact model (`CopilotRenderedArtifact`) i
content mapowanie (`CopilotArtifactContentMapper`) sa juz poza nimi w
`aiplatform.copilot.runtime`.

`features.incidentanalysis.ai.copilot.coverage` zawiera
`CopilotIncidentEvidenceCoverageEvaluator`,
`CopilotIncidentEvidenceCoverageReport` i lokalne enumy coverage. Opisuja one
widocznosc Elasticsearch, GitLaba, runtime, operational context i data
diagnostic need dla analizy incydentu, a nie generyczna metryke dowolnego
feature'a.

`features.incidentanalysis.ai.copilot` zawiera tez incidentowe implementacje
aktualnych providerow Copilota: `CopilotInitialAnalysisProvider` i
`CopilotSdkAnalysisChatProvider`. Kontrakty initial/chat obecnego flow
mieszkaja juz w `features.incidentanalysis.ai.initial/chat`, wiec produkcyjny
pakiet `analysis.ai` zostal wygaszony.
Podpakiet `features.incidentanalysis.ai.copilot.response` zawiera incidentowy
JSON-only response contract i parser. Ukryty report-only quality gate zostal
usuniety, bo operator nie mial dostepu do jego findings.

`features.incidentanalysis.ai.copilot.tools` zawiera incident-specific GitLab i
Database tool evidence capture: listenery eventow invocation oraz mappery
wynikow tools na operator-facing `AnalysisEvidenceSection`. Podpakiet
`features.incidentanalysis.ai.copilot.tools.description` zawiera
incident-specific guidance doklejane do opisow GitLab/DB/Operational Context
tools przez platformowy `CopilotToolDescriptionCustomizer`. Sama implementacja
Operational Context tools pozostaje neutralna w `agenttools.operationalcontext`.

Platform-owned runtime jest juz poza `preparation`, w
`aiplatform.copilot.runtime` i `aiplatform.copilot.tools`:
`CopilotRunRequest`, `CopilotRunPreparationService`,
`CopilotPreparedSession`, `CopilotSessionConfigRequest`,
`CopilotSkillRuntimeLoader`, `CopilotRenderedArtifact`,
`CopilotArtifactContentMapper`, `CopilotPreparedSessionFactory`,
`CopilotSessionConfigFactory` oraz `CopilotSdkExecutionGateway`. Skill loader odpowiada tylko za materializacje
skonfigurowanych skill resources/directories do katalogow runtime. Feature
nadal decyduje, czy dana sesja uzyje tych katalogow, skladajac
`CopilotSessionConfigRequest`. Platformowe tools zawieraja tez
`CopilotSdkToolFactory`, ktory rejestruje Spring tools jako Copilot
`ToolDefinition` bez wiedzy o incident-specific guidance. Budget state jest
session-bound i zostaje w platformie tylko jako guardrail runtime.
Platformowy katalog modeli Copilota mieszka w
`aiplatform.copilot.runtime.options`; neutralne preferencje wykonania AI
mieszkaja w `shared.ai`, a HTTP fasada katalogu modeli w `api.aioptions`.

### `api` / shared operator API

Warstwa `api` posiada cross-screen endpointy dla frontendu i operatora, ktore
nie sa wlasnoscia jednego dedykowanego feature'a. To osobna kategoria obok
`features.*.api`.

Przyklady:

- `GET /analysis/ai/options`, bo jest wspolnym katalogiem preferencji/modeli
  AI dla UI, a nie krokiem incident job flow,
- ogolne helper endpointy nad adapterami, np. Elasticsearch log search albo
  GitLab repository/source search, gdy staja sie wspolna powierzchnia FE.

Warstwa `api` moze posiadac:

- HTTP controller, request/response DTO i walidacje dla cross-screen use case,
- cienka fasade nad `aiplatform` albo `integrations`,
- globalny kontrakt bledow HTTP i walidacji.

Warstwa `api` nie posiada:

- orchestration konkretnego feature'a,
- evidence pipeline,
- promptow, skilli ani response contractow analizy,
- job state,
- adapter-specific klientow REST jako logiki integracyjnej.

Feature-specific endpointy zostaja przy feature, np.
`features.incidentanalysis.job.api`. Shared/operator API deleguje w dol do
platformy albo integracji i nie powinno byc importowane przez feature'y.
Cienkie diagnostyczne helper endpointy moga przejsciowo zostac przy
`integrations.<capability>`, jesli sa tylko manualnym testem adaptera. Gdy
staja sie stabilnym API dla wielu ekranow, docelowym miejscem jest `api.*`.

### `features.incidentanalysis`

Feature analizy incydentow posiada:

- publiczny job flow `POST /analysis/jobs`,
- follow-up chat dla zakonczonego joba,
- incident evidence collector i providery,
- incident-specific typed evidence views,
- resolved facts, np. `environment`, `gitLabBranch`, `gitLabGroup`,
- incident prompt, digest i response contract,
- incident skill resources i zasady ich doboru,
- incident tool access policy oraz hidden tool context dla GitLab/DB/Elastic,
  plus incidentowe zasady uzycia neutralnych `opctx_*`,
- mapowanie tool evidence na operator-facing evidence konkretnej analizy,
- mapowanie wyniku na UI/operator API.

Feature moze korzystac z integrations, tools i platformy AI, ale zadna z tych
warstw nie moze importowac feature'a incydentowego.

### `features.*`

Kolejne feature'y, np. flow explorer, functional logic explorer,
natural-language data diagnostics, analiza dokumentacji albo generowanie
scenariuszy, powinny dostarczyc wlasne:

- publiczne entrypointy albo command API,
- source/evidence pipeline,
- prompt i response contract,
- skille, polityke uzycia tools i hidden tool context,
- mapping wyniku na swoj UI albo API.

Nie powinny reuse'owac incidentowego `flow/job/evidence` jako generycznego
core. Reuse dotyczy platformy, tools, adapterow, operational context,
shared/operator API i malych shared kontraktow.

## Zasady Migracji

1. Najpierw kontrakty, potem implementacje.
2. Najpierw wygaszanie cykli, potem fizyczne rename'y pakietow.
3. Jedna capability na raz, np. najpierw Dynatrace, potem Database, potem
   GitLab.
4. Endpointy HTTP moga zachowac obecne URL-e nawet po zmianie pakietow.
5. Po kazdym etapie kod ma sie kompilowac i zachowywac dotychczasowy runtime.
6. Gdy ruch jest czysto pakietowy, unikac jednoczesnych zmian zachowania.
7. Nowe abstrakcje dodawac tylko wtedy, gdy usuwaja realna zaleznosc albo
   pozwalaja podpiac kolejny feature/runtime.

## Faza 0: Guardrails I Baseline

Cel: zapisac intencje i miec proste narzedzia do pilnowania kierunku.

Kroki:

1. Utrzymywac ten dokument jako roadmap source of truth.
2. Utrzymywac `AGENTS.md` jako twarde zasady dla agentow.
3. Dodac maly test architektoniczny dopiero po pierwszym wygaszeniu cykli albo
   dodac go w trybie "known exceptions".
   Stan obecny: `PackageDependencyGuardTest` blokuje powrot zamknietych
   krawedzi importow, m.in. `adapter -> evidence/mcp/ai/agenttools`,
   `mcp -> ai`, `evidence -> ai` oraz importy aplikacyjne w `shared`.
4. Mierzyc import graph skryptem albo ArchUnit, ale nie blokowac refactoru
   przez caly zastany dlug naraz.

Kryterium done:

- kazdy nowy refactor ma jasny kierunek warstwy,
- nowe importy "w gore" sa traktowane jako blad projektu,
- dokumentacja nazywa docelowy model tak samo w kazdym miejscu.

## Faza 1: Wydzielenie Neutralnych Kontraktow

Cel: zabrac kontrakty z pakietow, ktore nie sa ich wlascicielami.

Najbardziej oplacalne ruchy:

1. Przeniesc keys ukrytego tool contextu z Copilota do neutralnej warstwy
   tools, np. `agenttools.context.AgentToolContextKeys`.
   Stan obecny: keys mieszkaja w `agenttools.context.AgentToolContextKeys`,
   zeby MCP i Copilot runtime importowaly neutralny kontrakt.
2. Przeniesc typed DB request/result/scope/operator contracts z
   `analysis.mcp.database` do capability adaptera, np. `integrations.database`.
   Stan obecny: DB request/result/scope/operator contracts mieszkaja w
   `integrations.database`, a `agenttools.database.mcp` jest wrapperem Spring
   AI nad adapterem DB i mapuje hidden `ToolContext` na adapterowy scope.
3. Przeniesc generyczne evidence DTO z `analysis.ai.evidence` do neutralnego
   modelu, np. `shared.evidence` albo przejsciowo `analysis.evidence.model`.
   Stan obecny: generic evidence DTO mieszkaja w `shared.evidence`; neutralny
   `AnalysisAiToolEvidenceListener` takze mieszka w `shared.evidence`, bo
   laczy provider AI, job i feature bez zaleznosci od `analysis.ai`.
4. Trzymac token/cost usage DTO w neutralnym `shared.ai`, bo jest konsumowany
   przez flow, job UI i feature.

Kryterium done:

- MCP nie importuje Copilota tylko po to, zeby odczytac context keys,
- adapter DB nie importuje MCP DTO,
- adapter DB nie importuje `agenttools`; zaleznosc idzie od MCP/tools do
  adaptera,
- evidence pipeline nie importuje AI tylko po to, zeby zwrocic
  `AnalysisEvidenceSection`; neutralny model jest w `shared.evidence`.

## Faza 2: Wygaszenie Obecnych Cykli

Cel: usunac cykle top-level bez jeszcze pelnego przenoszenia pakietow.

Kroki:

1. Usunac `analysis.adapter.dynatrace -> analysis.evidence`.
   Factory z `ElasticLogEvidenceView` trzymac w evidence providerze, a
   adapterowi przekazywac czysty `DynatraceIncidentQuery`.
   Stan obecny: ta krawedz jest zamknieta, a Dynatrace zostal przeniesiony do
   `integrations.dynatrace`.
2. Usunac historyczna krawedz `analysis.adapter.database -> analysis.mcp.database`.
   DB adapter ma pracowac na neutralnych DB contracts.
3. Usunac `analysis.adapter -> agenttools`.
   DB capability DTO i scope maja byc przy adapterze, a hidden `ToolContext`
   mapowany po stronie MCP/tools.
4. Usunac `analysis.mcp -> analysis.ai.copilot`.
   Tool context keys i scope parsers maja byc neutralne dla platformy AI.
5. Usunac `analysis.evidence -> analysis.ai`.
   Generic evidence model ma byc poza `analysis.ai`.
   Stan obecny: generic evidence model mieszka w `shared.evidence`, a
   `analysis.evidence` nie importuje `analysis.ai`.

Kryterium done:

- produkcyjny i testowy root `analysis.*` jest zamkniety,
- adaptery mieszkaja w `integrations.*`,
- wrappery MCP/tools mieszkaja w `agenttools.<capability>.mcp`,
- cykle pozostaja tylko tam, gdzie sa jawnie zaakceptowane jako przejsciowe.

## Faza 3: Ekstrakcja `integrations`

Cel: fizycznie przeniesc adaptery do reusable warstwy integracji.

Kolejnosc sugerowana:

1. Dynatrace, bo po Fazie 2 powinien miec mala powierzchnie.
   Stan obecny: zrobione, pakiet mieszka w `integrations.dynatrace`.
2. Elasticsearch adapter i search service.
   Stan obecny: zrobione, pakiet mieszka w `integrations.elasticsearch`, a
   stabilny helper endpoint mieszka w `api.elasticsearch` pod tym samym URL-em.
3. GitLab adapter i source resolve.
   Stan obecny: zrobione, pakiet mieszka w `integrations.gitlab`, razem z
   `integrations.gitlab.source`.
4. Operational context adapter.
   Stan obecny: zrobione, pakiet mieszka w
   `integrations.operationalcontext`.
5. Database, po przeniesieniu neutralnych DB contracts.
   Stan obecny: zrobione, pakiet mieszka w `integrations.database`.

Target:

```text
analysis.adapter.elasticsearch -> integrations.elasticsearch [done]
analysis.adapter.dynatrace     -> integrations.dynatrace [done]
analysis.adapter.gitlab        -> integrations.gitlab [done]
analysis.adapter.database      -> integrations.database [done]
analysis.adapter.operationalcontext -> integrations.operationalcontext [done]
```

Kryterium done:

- feature incident analysis, tools i shared/operator endpointy importuja
  `integrations`,
- `integrations` importuje tylko `common/shared` i biblioteki techniczne,
- endpointy HTTP nadal dzialaja pod obecnymi URL-ami albo maja jawnie
  udokumentowany redirect/zmiane.

## Faza 4: Ekstrakcja `agenttools`

Cel: oddzielic reusable tools od incident analysis i Copilot SDK.

Kroki:

1. Przeniesc tool contracts i names do `agenttools.<capability>`.
   Stan obecny: tool names/prefixy dla Elasticsearch, GitLab, Database i
   Operational Context mieszkaja w `agenttools.<capability>`. Elasticsearch,
   GitLab, Database i Operational Context wrappery `@Tool` mieszkaja juz w
   `agenttools.<capability>.mcp`.
2. Przeniesc Spring AI/MCP wrappers do `agenttools.mcp.<capability>` albo
   `agenttools.<capability>.mcp`.
   Stan obecny: wybrany kierunek to `agenttools.<capability>.mcp`; pierwszy
   przeniesiony slice to Elasticsearch, drugi to GitLab, trzeci to Database.
3. Zostawic w tools tylko delegacje do integrations/use case'ow.
4. Utrzymac hidden scope jako neutralny `AgentToolContext`, a nie
   `CopilotToolContext`.
5. Dopiero potem przepiac platforme Copilot na nowe tools packages.

Kryterium done:

- tools nie importuja `features.incidentanalysis`,
- tools nie importuja `aiplatform.copilot`,
- Copilot jest klientem tools, a nie wlascicielem tools,
- potencjalny drugi agent runtime moglby zarejestrowac te same capability.

## Faza 5: Ekstrakcja `aiplatform.copilot`

Cel: zrobic z Copilot SDK runtime platforme, a nie czesc incident feature'a.

Kroki:

1. Przeniesc generic execution/session/tool invocation do
   `aiplatform.copilot`.
   Stan obecny: pierwszy neutralny slice jest przeniesiony:
   `aiplatform.copilot.runtime` zawiera run request, prepared session,
   session config, properties, model listing, skill loader, artifact mapping,
   execution gateway oraz user-visible usage mapping w execution result.
   Niewidoczna dla operatora telemetryka sesji Copilota zostala usunieta z
   aktualnego runtime.
   Platformowe `aiplatform.copilot.tools` zawiera juz
   `CopilotSdkToolFactory`, `CopilotToolInvocationHandler`, hidden `ToolContext`,
   `CopilotToolSessionContext`, eventy invocation, neutralne policy contracts,
   session validation, logging invocation, description customization contract,
   budget policy/state/registry i session-bound tool evidence store.
2. Zdefiniowac neutralny request platformowy, ktory niesie prompt, model
   options, skill resources, available tools, hidden context, evidence sink i
   response handler/parser, ale nie zaklada `correlationId`.
3. Przeniesc model options provider do platformy, a endpoint
   `/analysis/ai/options` zostawic jako shared/operator API fasade na platforme.
   Stan obecny: zrobione. `CopilotSdkModelOptionsProvider` i neutralne DTO
   katalogu modeli mieszkaja w `aiplatform.copilot.runtime.options`,
   `shared.ai.AnalysisAiOptions` niesie preferencje wykonania, a
   `api.aioptions` mapuje katalog na kontrakt endpointu aplikacji.
4. Oddzielic generic artifact delivery mechanics od incident-specific digestu.
5. Przeniesc incident prompt, incident digest i incident response JSON contract
   do `features.incidentanalysis`.
   Stan obecny: incident prompt i digest oraz initial/follow-up providery
   Copilota mieszkaja juz w `features.incidentanalysis.ai.copilot`. Response
   parser mieszka juz w `features.incidentanalysis.ai.copilot.response`.
   Ukryty quality gate zostal usuniety z aktualnego runtime.
6. Przeniesc incident tool access policy, incident coverage heurystyki,
   incident skill selection i operator-facing tool evidence mapping do
   `features.incidentanalysis`.
   Stan obecny: tool access policy, hidden context, skill/session request
   assembly i coverage heurystyki mieszkaja juz w
   `features.incidentanalysis.ai.copilot`. Operator-facing GitLab/DB tool
   evidence mapping mieszka juz w
   `features.incidentanalysis.ai.copilot.tools`, a incident-specific guidance
   opisow tools w `features.incidentanalysis.ai.copilot.tools.description`.
7. Platformowy tool invocation handler moze znac mechanike callbackow,
   allowlisty, policies, hidden context map i logowania, ale nie powinien
   znac nazw capability jako reguly domenowej, np. "GitLab przed DB dla
   incydentu".
8. Nie przenosic `InitialAnalysisRequest` do platformy w obecnym ksztalcie,
   bo zawiera incident-specific scope: `correlationId`, `environment`,
   `gitLabBranch`, `gitLabGroup`.

Kryterium done:

- `aiplatform.copilot` nie importuje `features.incidentanalysis`,
- platforma umie uruchomic AI dla dowolnego feature'a, ktory dostarczy prompt,
  skille, tools, hidden context, evidence sink i response handling,
- incident-specific coverage/prompt/digest/tool policy/tool evidence mapping
  nie mieszkaja w platform runtime,
- dodanie drugiego feature'a nie wymaga dodawania warunkow w
  `aiplatform.copilot` po nazwach tools albo typach incident evidence.

## Faza 6: Ekstrakcja `features.incidentanalysis`

Cel: przeniesc obecny flow analizy incydentow do dedykowanego feature'a.

Target:

```text
analysis.job      -> features.incidentanalysis.job [done]
analysis.flow     -> features.incidentanalysis.flow [done]
analysis.evidence -> features.incidentanalysis.evidence [done]
analysis.ai.initial/chat contracts specific to incident
                 -> features.incidentanalysis.ai [done]
incident prompt/digest/coverage
                 -> features.incidentanalysis.ai
incident skill selection/tool policy/hidden context
                 -> features.incidentanalysis.ai
```

Uwagi:

- URL-e publiczne moga pozostac `/analysis/jobs`.
- Nazwa endpointu nie musi odzwierciedlac nazwy pakietu.
- UI moze nadal mowic "analysis", bo to jest product-facing jezyk aktualnego
  feature'a.
- `analysis.options` jest zamknietym historycznym pakietem produkcyjnym.
  Neutralne `AnalysisAiOptions` mieszkaja w `shared.ai`, a controller/DTO
  endpointu `GET /analysis/ai/options` w `api.aioptions`.
- `analysis.job` jest juz zamknietym historycznym pakietem produkcyjnym.
  Incident job API, state i errors mieszkaja w
  `features.incidentanalysis.job`.
- `analysis.evidence` jest juz zamknietym historycznym pakietem produkcyjnym.
  Incident evidence collector, providery i typed evidence views mieszkaja w
  `features.incidentanalysis.evidence`.

Kryterium done:

- feature incident analysis importuje platforme, tools i integrations,
- zadna z tych warstw nie importuje feature'a,
- feature sklada `CopilotRunRequest` albo rownowazny request platformowy,
- wszystkie incident-specific heurystyki sa lokalne dla feature'a.

## Faza 7: Enforcement Architektoniczny

Cel: po wygaszeniu glownego dlugu zaczac blokowac regresje.

Reguly do dodania w ArchUnit albo podobnym tescie:

```text
integrations.. should only depend on common.., shared.., java.., spring technical libs
agenttools.. should not depend on aiplatform.. or features..
aiplatform.. should not depend on features..
features.. may depend on aiplatform.., agenttools.., integrations.., common.., shared..
common.. and shared.. should not depend on application layers
```

Kolejny poziom:

```text
top-level slices should be free of cycles
```

Nie dodawac pelnej reguly cycle-free za wczesnie, jesli wymusi chaotyczny
refactor. Najpierw blokowac nowe zle kierunki, potem zamykac znane wyjatki.

## Faza 8: Drugi Feature Jako Dowod Architektury

Cel: upewnic sie, ze target nie jest tylko przemalowana analiza incydentow.

Dobry maly drugi feature:

- flow explorer dla requestu albo use case'u,
- functional logic explorer dla reguly biznesowej albo procesu,
- natural-language data diagnostics nad readonly DB capability,
- analiza dokumentacji,
- generator scenariuszy testowych.

Preferowany pierwszy dowod to flow explorer, bo najostrzej testuje granice:
GitLab tools, operational context, DB hints, result contract i UI timeline
powinny byc reusable bez importu incident analysis.

Minimalny dowod:

1. feature ma wlasny request/response,
2. feature uzywa `aiplatform.copilot`,
3. feature przekazuje do platformy wlasny prompt, skille, tools, hidden context
   i parser odpowiedzi,
4. feature wybiera tools z `agenttools`,
5. feature nie importuje `features.incidentanalysis`,
6. reusable warstwy nie importuja nowego feature'a.

Kryterium done:

- drugi feature korzysta z platformy i tools bez kopiowania incident flow,
- nowe potrzeby feature'a prowadza do rozszerzenia platform contracts albo
  tools contracts, a nie do dopisywania warunkow w incident analysis.

## Proponowana Kolejnosc Pierwszych PR-ow

1. PR: dodac ArchUnit dependency i jedna lagodna regule lub baseline skrypt
   import graph, bez blokowania calego dlugu.
2. PR: usunac `adapter.dynatrace -> evidence`.
3. PR: przeniesc tool context keys poza Copilota.
4. PR: przeniesc DB tool contracts poza MCP i usunac `adapter.database -> mcp`.
5. PR: przeniesc generic evidence DTO poza `analysis.ai`.
6. PR: dodac reguly zakazujace nowych importow `adapter -> evidence/mcp/ai`.
7. PR: przeniesc Dynatrace adapter do `integrations.dynatrace` [done].
8. PR: przeniesc Elasticsearch adapter do `integrations.elasticsearch` [done].
9. PR: przeniesc GitLab adapter i source resolve do `integrations.gitlab`
   [done].
10. PR: przeniesc operational context adapter do
    `integrations.operationalcontext` [done].
11. PR: przepiac DB tools na contracts z `integrations.database` [done].
12. PR: przeniesc Database capability do `integrations.database` [done].
13. PR: przeniesc neutralne tool names/prefixy do `agenttools.<capability>`
    [done].
14. PR: przenosic MCP wrappers capability po capability do docelowej warstwy
    tools; Elasticsearch [done], GitLab [done], Database [done],
    Operational Context [done].
15. PR: wydzielic generic Copilot runtime od incident prompt/digest [in
    progress: runtime, incident preparation, coverage i GitLab/DB tool evidence
    capture przeniesione; platformowe tool
    factory/handler/context/events/policy/logging/description/budget
    evidence store przeniesione do `aiplatform.copilot.tools`, execution
    gateway zwraca content + user-visible usage, a model options provider do
    `aiplatform.copilot.runtime.options`].
16. PR: przeniesc incident AI initial/chat contracts do
    `features.incidentanalysis.ai` [done].
17. PR: przeniesc incident flow do `features.incidentanalysis.flow` [done].
18. PR: przeniesc incident job do `features.incidentanalysis.job`, zachowujac
    publiczne URL-e `/analysis/jobs` [done].
19. PR: przeniesc incident evidence do
    `features.incidentanalysis.evidence` [done].
20. PR: wydzielic shared/operator API opcji AI: przeniesc neutralne preferencje
    wykonania do `shared.ai`, a controller/DTO `GET /analysis/ai/options` do
    `api.aioptions`, bez zmiany URL-a [done].
21. PR: przeniesc stabilne helper endpointy Elasticsearch/GitLab do shared/operator
    API `api.*`, zostawiajac adaptery, serwisy i modele w `integrations.*`
    [done].
22. PR: zamknac produkcyjny root `analysis.*` w guardzie i usunac ostatnie
    lokalne instrukcje z historycznych katalogow [done].
23. PR: zamknac testowy root `analysis.*` i przeniesc testy do pakietow
    aktualnych wlascicieli: `features`, `aiplatform`, `integrations` oraz
    `testsupport` [done].
24. PR: dodac minimalny drugi feature albo spike, najlepiej flow explorer,
    ktory weryfikuje reuse platformy, tools, integracji i operational contextu.

## Decyzje Do Podjecia W Trakcie

Nie trzeba rozstrzygac wszystkiego od razu. Te decyzje powinny zapasc wtedy,
gdy dotykamy danego obszaru:

- Rozstrzygniete: generic evidence model mieszka w `shared.evidence`.
- Rozstrzygniete: `agenttools` uzywa struktury
  `agenttools.<capability>.mcp`, zeby kontrakt capability i jego ekspozycja MCP
  byly blisko siebie.
- Rozstrzygniete kierunkowo: cross-screen FE/operator API mieszka docelowo w
  `api.*`, a feature-specific API przy `features.<feature>.api`. Cienkie
  diagnostyczne helper endpointy moga przejsciowo zostac przy
  `integrations.<capability>`, jesli sa tylko recznym testem adaptera.
- Rozstrzygniete: `analysis.ai.initial/chat` byly feature-specific i mieszkaja
  teraz w `features.incidentanalysis.ai.initial/chat`. Platform run contract
  pozostaje osobno jako `aiplatform.copilot.runtime.CopilotRunRequest`.
- Jaki bedzie docelowy ksztalt `CopilotRunRequest`: nazwa, pola, ownership
  `AutoCloseable` zasobow i kontrakt response parsera?
- Czy wprowadzamy osobny Maven module dopiero po stabilizacji pakietow?

Domyslna odpowiedz KISS: najpierw pakiety i compile-time imports, Maven modules
dopiero pozniej, jesli beda potrzebne.

## Czego Nie Robic

- Nie przenosic wszystkiego do `common` albo `shared`.
- Nie robic platformy AI, ktora zna `correlationId` jako obowiazkowe pole.
- Nie robic platformy AI, ktora sama wybiera incident prompt, incident skille,
  GitLab/DB/Elastic tool policy albo parser wyniku incydentu.
- Nie robic tools, ktore importuja Copilota.
- Nie robic adapterow, ktore wiedza o evidence pipeline.
- Nie robic drugiego feature'a przez kopiowanie incident `job/flow`.
- Nie laczyc rename pakietow z duza zmiana zachowania runtime.
- Nie wprowadzac abstrakcji "na zapas", jesli jedyny konsument nadal jest ten
  sam i nie usuwamy przez to realnej zaleznosci.

## Kryterium Koncowe

Plan mozna uznac za zrealizowany, gdy:

- adaptery sa reusable poza analiza incydentow,
- tools/MCP sa reusable poza Copilotem i poza analiza incydentow,
- Copilot runtime jest platforma uruchamiania AI, a nie domena incydentu,
- incident analysis jest feature'em, ktory sklada platforme, tools i adaptery,
- dodanie drugiej analizy nie wymaga importowania incident feature'a,
- test architektoniczny blokuje powrot najwazniejszych zakazanych zaleznosci.
