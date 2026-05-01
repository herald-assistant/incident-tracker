# Modular Architecture Roadmap

## Cel

Ten dokument opisuje, jak dojsc z obecnego ukladu `analysis.*` do docelowego
modelu, w ktorym incident analysis jest jednym z feature'ow zbudowanych na
reusable capability, tools i platformie AI.

To nie jest plan "big bang rename". Kolejnosc prac ma najpierw wygasic zle
zaleznosci i ustabilizowac kontrakty, a dopiero pozniej przenosic wieksze
pakiety do docelowych nazw.

## Docelowy Model

Docelowa odpowiedzialnosc warstw:

```text
integrations/adapters        <- czyste capability do systemow zewnetrznych
agent-tools / mcp            <- reusable tools nad adapterami
ai-platform/copilot          <- platforma uruchamiania modelu + tools
features/incident-analysis   <- konkretna analiza incydentow
features/...                 <- przyszle dedykowane analizy
common/shared                <- male neutralne helpery i kontrakty
```

Docelowy kierunek zaleznosci:

```text
features.* -> ai-platform
features.* -> agent-tools
features.* -> integrations

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

agent-tools -> ai-platform
agent-tools -> features.*

ai-platform -> features.*

common/shared -> integrations
common/shared -> agent-tools
common/shared -> ai-platform
common/shared -> features.*
```

## Docelowe Pakiety Java

Nazwy pakietow nie moga miec myslnikow, wiec praktyczny target w Javie:

```text
pl.mkn.incidenttracker.integrations.*
pl.mkn.incidenttracker.agenttools.*
pl.mkn.incidenttracker.aiplatform.copilot.*
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
- opcjonalne helper endpointy REST do recznego testowania capability.

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
- policies, budget, telemetry, logging i lifecycle sesji,
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
- mapowanie tool evidence na operator-facing evidence konkretnej analizy,
- mapowanie wyniku na UI/operator API.

Feature moze korzystac z integrations, tools i platformy AI, ale zadna z tych
warstw nie moze importowac feature'a incydentowego.

### `features.*`

Kolejne feature'y, np. analiza dokumentacji, chatbot albo generowanie
scenariuszy, powinny dostarczyc wlasne:

- publiczne entrypointy albo command API,
- source/evidence pipeline,
- prompt i response contract,
- skille, polityke uzycia tools i hidden tool context,
- mapping wyniku na swoj UI albo API.

Nie powinny reuse'owac incidentowego `flow/job/evidence` jako generycznego
core. Reuse dotyczy platformy, tools, adapterow i malych shared kontraktow.

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
   `analysis.mcp.database` do capability adaptera, np. `analysis.adapter.database`.
   Stan obecny: DB request/result/scope/operator contracts mieszkaja w
   `analysis.adapter.database`, a `analysis.mcp.database` jest wrapperem Spring
   AI nad adapterem DB i mapuje hidden `ToolContext` na adapterowy scope.
3. Przeniesc generyczne evidence DTO z `analysis.ai.evidence` do neutralnego
   modelu, np. `shared.evidence` albo przejsciowo `analysis.evidence.model`.
   Stan obecny: generic evidence DTO mieszkaja w `shared.evidence`, a
   `analysis.ai.evidence` zostaje miejscem dla `AnalysisAiToolEvidenceListener`.
4. Zostawic `AnalysisAiToolEvidenceListener` po stronie AI/platform/feature
   boundary, bo to nie jest zwykly model evidence.

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
2. Usunac `analysis.adapter.database -> analysis.mcp.database`.
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

- `analysis.adapter` nie importuje `analysis.evidence`, `analysis.mcp`,
  `analysis.ai` ani `agenttools`,
- `analysis.mcp` nie importuje `analysis.ai.copilot`,
- cykle pozostaja tylko tam, gdzie sa jawnie zaakceptowane jako przejsciowe.

## Faza 3: Ekstrakcja `integrations`

Cel: fizycznie przeniesc adaptery do reusable warstwy integracji.

Kolejnosc sugerowana:

1. Dynatrace, bo po Fazie 2 powinien miec mala powierzchnie.
2. Elasticsearch, lacznie z helper endpointem log search jako fasada nad
   integracja.
3. GitLab adapter i source resolve.
4. Operational context adapter.
5. Database, po przeniesieniu neutralnych DB contracts.

Target:

```text
analysis.adapter.elasticsearch -> integrations.elasticsearch
analysis.adapter.dynatrace     -> integrations.dynatrace
analysis.adapter.gitlab        -> integrations.gitlab
analysis.adapter.database      -> integrations.database
analysis.adapter.operationalcontext -> integrations.operationalcontext
```

Kryterium done:

- feature incident analysis, tools i helper endpointy importuja `integrations`,
- `integrations` importuje tylko `common/shared` i biblioteki techniczne,
- endpointy HTTP nadal dzialaja pod obecnymi URL-ami albo maja jawnie
  udokumentowany redirect/zmiane.

## Faza 4: Ekstrakcja `agenttools`

Cel: oddzielic reusable tools od incident analysis i Copilot SDK.

Kroki:

1. Przeniesc tool contracts i names do `agenttools.<capability>`.
2. Przeniesc Spring AI/MCP wrappers do `agenttools.mcp.<capability>` albo
   `agenttools.<capability>.mcp`.
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

1. Przeniesc generic execution/session/tool invocation/telemetry do
   `aiplatform.copilot`.
2. Zdefiniowac neutralny request platformowy, ktory niesie prompt, model
   options, skill resources, available tools, hidden context, evidence sink i
   response handler/parser, ale nie zaklada `correlationId`.
3. Przeniesc model options provider do platformy, a feature endpoint
   `/analysis/ai/options` zostawic jako fasade na platforme.
4. Oddzielic generic artifact delivery mechanics od incident-specific digestu.
5. Przeniesc incident prompt, incident digest i incident response JSON contract
   do `features.incidentanalysis`.
6. Przeniesc incident tool access policy, incident coverage heurystyki,
   incident skill selection i operator-facing tool evidence mapping do
   `features.incidentanalysis`.
7. Platformowy tool invocation handler moze znac mechanike callbackow,
   allowlisty, policies, hidden context map i telemetryki, ale nie powinien
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
analysis.job      -> features.incidentanalysis.job
analysis.flow     -> features.incidentanalysis.flow
analysis.evidence -> features.incidentanalysis.evidence
analysis.ai.initial/chat contracts specific to incident
                 -> features.incidentanalysis.ai
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
- `analysis.options` powinno zostac rozstrzygniete podczas Fazy 5: albo jako
  platform contract, albo jako feature facade nad platform options.

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

- analiza dokumentacji,
- chatbot nad operational context,
- generator scenariuszy testowych,
- maly flow "explain repository area" oparty o GitLab tools.

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
7. PR: przeniesc Dynatrace adapter do `integrations.dynatrace`.
8. PR: przepiac DB tools na contracts z `analysis.adapter.database`.
9. PR: przeniesc reszte adapterow capability po jednym obszarze.
10. PR: wydzielic generic Copilot runtime od incident prompt/digest.
11. PR: przeniesc incident job/flow/evidence do `features.incidentanalysis`.
12. PR: dodac minimalny drugi feature albo spike, ktory weryfikuje reuse
    platformy i tools.

## Decyzje Do Podjecia W Trakcie

Nie trzeba rozstrzygac wszystkiego od razu. Te decyzje powinny zapasc wtedy,
gdy dotykamy danego obszaru:

- Rozstrzygniete: generic evidence model mieszka w `shared.evidence`.
- Czy `agenttools` ma strukture `agenttools.<capability>.mcp`, czy
  `agenttools.mcp.<capability>`?
- Czy helper endpointy adapterow mieszkaja przy `integrations.<capability>.api`,
  czy w osobnym `api.integrations`?
- Czy `analysis.ai.initial` zostaje feature-specific, czy rozbijamy go na
  feature contract plus platform run contract?
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
