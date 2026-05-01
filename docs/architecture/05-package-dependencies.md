# Package Dependencies

## Cel

Ten dokument rozdziela dwa rozne widoki zaleznosci:

- runtime ownership: kto inicjuje kolejny krok i gdzie deleguje wykonanie,
- compile-time imports: ktory pakiet importuje klasy z innego pakietu.

Te widoki sa celowo osobne. Diagram runtime ownership pokazuje kierunek
wywolania/delegowania, a nie powrot wyniku. Compile-time graph pokazuje
rzeczywiste importy Javy.

Import graph ponizej powstal ze skanu `src/main/java` z uwzglednieniem
zwyklych i static importow.

## Turbo Wazne: Model Rozszerzalnosci

Compile-time graph ma wspierac docelowy model produktu, a nie tylko wygladac
ladnie w diagramie. Incident analysis jest pierwszym dedykowanym feature'em,
ale adaptery, tools/MCP i runtime AI maja pozostac reusable dla kolejnych
analiz oraz innych sposobow ekspozycji capability.

Szczegolowy plan dojscia do tego modelu jest w
`06-modular-architecture-roadmap.md`.

Docelowa interpretacja warstw:

```text
dedykowane feature'y analityczne
  -> platforma AI runtime
  -> reusable tools/MCP
  -> reusable adaptery/integracje
  -> systemy zewnetrzne

dedykowane feature'y analityczne
  -> deterministic evidence / feature orchestration
  -> reusable adaptery/integracje
```

W obecnym kodzie te warstwy nadal mieszkaja pod `analysis.*`, bo incident
analysis byl pierwszym use case'em. Nie oznacza to, ze wszystkie pakiety pod
`analysis` sa feature-specific. Przy kazdej wiekszej zmianie trzeba pilnowac
ponizszych zasad:

- `integrations.*` to docelowa reusable warstwa capability integracyjnych.
  Nie moze zalezec od evidence pipeline, MCP/tools, Copilota, flow ani job API.
  Ten sam adapter ma byc uzywalny przez provider evidence, tool, helper
  endpoint REST albo przyszly feature.
- `analysis.adapter` jest historycznym katalogiem po ekstrakcji adapterow.
  Nowe i przenoszone capability maja trafiac do `integrations.*`.
- `agenttools` to reusable ekspozycja capability nad adapterami. Nie powinno
  zalezec od dedykowanej analizy incydentow ani od szczegolow providera
  Copilot SDK.
- `analysis.ai.copilot` to aktualny adapter platformy AI runtime. Moze
  korzystac z reusable tools/MCP, budowac session config, allowliste,
  hidden context, telemetryke i evidence capture, ale docelowo ma robic to na
  podstawie parametrow przekazanych przez feature. Nie powinien stawac sie
  wlascicielem domenowej logiki analizy incydentu, promptu, skilli ani polityki
  doboru tools.
- `analysis.job`, `analysis.flow` i incident-specific evidence/prompt sa
  feature'em analizy incydentow. Moga zalezec od platformy, tools i adapterow,
  ale platforma, tools i adaptery nie moga zalezec od tego feature'a.
- Przyszle feature'y, np. analiza dokumentacji, chatboty albo generowanie
  scenariuszy, powinny dostarczyc wlasny prompt, evidence/source pipeline,
  skille, hidden context, policy uzycia capability i kontrakt odpowiedzi,
  zamiast reuse'owac incidentowy flow jako generyczny core.
- `common` i neutralne kontrakty maja pozostac male. Wyciagaj tam tylko te
  typy, ktore naprawde sa wspolne dla kilku capability albo feature'ow.

Praktyczna konsekwencja: cykle importow usuwamy przez oddanie kontraktu do
warstwy, ktora jest jego wlascicielem, a nie przez przepinanie zaleznosci na
skroty. Brak cykli jest skutkiem zdrowych granic, nie celem samym w sobie.

Docelowy runtime Copilota ma byc parametryzowany: feature przekazuje prompt,
skille, allowliste tools, hidden context, evidence sink i response parser.
Platforma zna Copilot SDK, session lifecycle, tool invocation, policies,
telemetryke i techniczna obsluge wynikow, ale nie wybiera incidentowych tools
ani nie zna `correlationId` jako stalego wymogu platformowego.

## Runtime Ownership Flow

Strzalka oznacza tutaj, kto inicjuje kolejny krok runtime albo do kogo
deleguje wykonanie. Nie pokazujemy tutaj powrotu wartosci do callera, bo taka
strzalka wyglada jak odwrotna zaleznosc pakietowa.

```mermaid
flowchart LR
    OP["Operator UI"] --> JOBAPI["analysis.job.api"]
    JOBAPI --> JOB["analysis.job"]
    JOB --> FLOW["analysis.flow"]

    FLOW --> EVIDENCE["analysis.evidence"]
    EVIDENCE --> INTEGRATIONS["integrations"]
    INTEGRATIONS --> EXT

    FLOW --> INITIAL["analysis.ai.initial"]
    INITIAL --> INCIDENTCOPILOT["features.incidentanalysis.ai.copilot"]
    INCIDENTCOPILOT --> AIPLATFORM["aiplatform.copilot.runtime"]
    INCIDENTCOPILOT --> TOOLS["analysis.ai.copilot.tools"]
    TOOLS --> MCP["agenttools.*.mcp"]
    MCP --> INTEGRATIONS

    JOB --> CHAT
    CHAT["analysis.ai.chat"] --> INCIDENTCOPILOT

    JOB --> OPTIONS["analysis.options"]
    FLOW --> OPTIONS
    INITIAL --> OPTIONS
    CHAT --> OPTIONS
```

Wyniki wracaja do callera jako return values albo listener callbacks:
`AnalysisExecution`, `AnalysisResultResponse`, `preparedPrompt`,
`toolEvidenceSections` i `chatMessages`. To nie tworzy importu zwrotnego.

Najwazniejsze lancuchy ownership/dependency:

- deterministic initial analysis:
  `analysis.job -> analysis.flow -> analysis.evidence -> integrations`,
- initial AI:
  `analysis.flow -> analysis.ai.initial -> features.incidentanalysis.ai.copilot -> aiplatform.copilot.runtime`,
- AI-guided tools podczas initial analysis:
  `features.incidentanalysis.ai.copilot -> analysis.ai.copilot.tools -> agenttools.*.mcp -> integrations`,
- follow-up chat:
  `analysis.job -> analysis.ai.chat -> features.incidentanalysis.ai.copilot -> analysis.ai.copilot.tools -> agenttools.*.mcp -> integrations`,
- model/options:
  `analysis.job`, `analysis.flow` i `analysis.ai` korzystaja z bocznego
  kontraktu `analysis.options`.

## Compile-Time Import Graph

Strzalka oznacza tutaj: pakiet po lewej importuje pakiet po prawej.
Linie przerywane oznaczaja krawedzie odwrotne lub mocniej sprzegajace, ktore
warto pilnowac przy kolejnych refaktorach.

```mermaid
flowchart LR
    JOB["analysis.job"] --> FLOW["analysis.flow"]
    JOB --> AI["analysis.ai"]
    JOB --> EVIDENCE["analysis.evidence"]
    JOB --> OPTIONS["analysis.options"]
    JOB --> SHARED["shared"]

    FLOW --> EVIDENCE
    FLOW --> AI
    FLOW --> OPTIONS
    FLOW --> INTEGRATIONS["integrations"]
    FLOW --> SHARED

    EVIDENCE --> INTEGRATIONS["integrations"]
    EVIDENCE --> SHARED

    FEATURES["features"] --> AI
    FEATURES --> AIPLATFORM["aiplatform"]
    FEATURES --> AGENTTOOLS["agenttools"]
    FEATURES --> EVIDENCE
    FEATURES --> OPTIONS
    FEATURES --> SHARED

    AI --> AIPLATFORM["aiplatform"]
    AI --> AGENTTOOLS["agenttools"]
    AI --> OPTIONS
    AI --> COMMON["common"]
    AI --> SHARED

    AIPLATFORM --> SHARED

    AGENTTOOLS --> INTEGRATIONS

    API["api"] --> INTEGRATIONS
    API --> FLOW
    API --> JOB
```

## Aktualne Krawedzie

| Krawedz importow | Liczba | Status | Co oznacza |
| --- | ---: | --- | --- |
| `analysis.job -> analysis.flow` | 6 | oczekiwane | Job uruchamia orchestrator i mapuje wynik flow do snapshotu UI. |
| `analysis.job -> analysis.ai` | 9 | oczekiwane | Job trzyma chat, usage i zapisany `InitialAnalysisRequest` dla follow-up. |
| `analysis.job -> analysis.evidence` | 7 | oczekiwane | Job pokazuje kroki pipeline i runtime facts wyprowadzone z evidence. |
| `analysis.job -> analysis.options` | 2 | oczekiwane | Start joba niesie opcjonalne preferencje AI. |
| `analysis.job -> shared` | 4 | oczekiwane | Job snapshoty i API response niosa neutralny model evidence. |
| `analysis.flow -> analysis.evidence` | 5 | oczekiwane | Orchestrator uruchamia deterministic evidence collector. |
| `analysis.flow -> analysis.ai` | 6 | oczekiwane | Orchestrator buduje request AI i wywoluje initial provider. |
| `analysis.flow -> analysis.options` | 1 | oczekiwane | Flow przenosi preferencje AI do initial requestu. |
| `analysis.flow -> integrations` | 1 | do obserwacji | `AnalysisOrchestrator` czyta `GitLabProperties` dla `gitLabGroup`. Jezeli to urosnie, warto wydzielic neutralny resolver scope'u. |
| `analysis.flow -> shared` | 2 | oczekiwane | Flow przenosi neutralne evidence DTO miedzy collectorem, AI i response. |
| `analysis.evidence -> integrations` | 41 | oczekiwane | Providerzy Elasticsearch, Dynatrace, GitLab deterministic i operational context deleguja do docelowych reusable integracji. |
| `analysis.evidence -> shared` | 26 | oczekiwane | Evidence publikuje neutralne `AnalysisEvidenceSection` z `shared.evidence`. |
| `features -> aiplatform` | 19 | oczekiwane przejsciowo | Incident Copilot preparation sklada platformowy `CopilotRunRequest` i uzywa runtime types. |
| `features -> agenttools` | 4 | oczekiwane przejsciowo | Incident tool policy wybiera capability po neutralnych nazwach/prefixach tools. |
| `features -> analysis.ai` | 43 | oczekiwane przejsciowo | Incident provider/preparation implementuje aktualne kontrakty AI oraz korzysta z jeszcze nieprzeniesionej mechaniki tools, response, quality i telemetry. |
| `features -> analysis.evidence` | 11 | przejsciowe | Incident coverage/artifacts czytaja typed evidence view helpers do czasu przeniesienia evidence do feature'a. |
| `features -> analysis.options` | 1 | oczekiwane przejsciowo | Incident session config mapuje operator-facing preferencje modelu. |
| `features -> shared` | 9 | oczekiwane | Incident artifacts i coverage czytaja neutralne DTO evidence. |
| `analysis.ai -> aiplatform` | 5 | oczekiwane przejsciowo | Techniczne wykonanie/model options Copilota korzystaja z pierwszego wydzielonego platformowego runtime. |
| `analysis.ai -> agenttools` | 25 | oczekiwane przejsciowo | Runtime tools uzywaja neutralnych hidden context keys, nazw tools/prefixow capability oraz GitLab tool response DTO dla capture evidence. |
| `analysis.ai -> analysis.options` | 5 | oczekiwane | Providerzy AI/model options dostaja preferencje modelu/reasoning. |
| `analysis.ai -> common` | 2 | oczekiwane | Mappery tool evidence uzywaja `JsonPayloadReader`. |
| `analysis.ai -> shared` | 15 | oczekiwane | Runtime tools, response/quality i telemetry konsumuja neutralny model evidence. |
| `aiplatform -> shared` | 2 | oczekiwane | Platformowy run request i prepared session niosa neutralny model evidence jako sink output tooli. |
| `agenttools -> integrations` | 9 | oczekiwane | Przeniesione wrappery Elasticsearch, GitLab i Database MCP deleguja do `integrations`. |
| `api -> integrations` | 6 | oczekiwane | Globalny handler HTTP mapuje wyniki/wyjatki helper endpointow Elasticsearch i GitLab z `integrations`. |
| `api -> analysis.flow` | 1 | oczekiwane | Globalny handler HTTP mapuje `AnalysisDataNotFoundException`. |
| `api -> analysis.job` | 2 | oczekiwane | Globalny handler HTTP mapuje wyjatki job API. |

## Cykle Do Pilnowania

Po wydzieleniu generycznego modelu evidence aktualny kod nie ma juz cyklu
`analysis.ai <-> analysis.evidence` wynikajacego z `AnalysisEvidenceSection`.
To jest zamknieta granica i nie nalezy jej przywracac.

Do obserwacji zostaly krawedzie:

1. `features -> analysis.ai`

   Incident feature korzysta jeszcze z kontraktow AI, execution gateway,
   response/quality, telemetry i runtime tools mieszkajacych pod
   `analysis.ai`. Nie dodawac krawedzi odwrotnej `analysis.ai -> features`;
   providery i preparation nalezy przenosic do feature'a, a reusable runtime do
   `aiplatform`.

2. `features -> analysis.evidence`

   Incident coverage/artifacts czytaja typed evidence view helpers. To jest
   przejsciowe do czasu przeniesienia evidence do `features.incidentanalysis`;
   nie uzywac tego jako pretekstu do importow `analysis.evidence -> features`.

## Kierunek Dla Nowych Zmian

Preferowany kierunek kompilacyjny dla obecnych pakietow:

```text
analysis.job -> analysis.flow -> analysis.evidence -> integrations
analysis.evidence -> shared
analysis.flow -> analysis.ai.initial
features.incidentanalysis.ai.copilot -> aiplatform.copilot.runtime
features.incidentanalysis.ai.copilot -> analysis.ai.copilot.tools
features.incidentanalysis.ai.copilot -> agenttools
analysis.ai.copilot -> aiplatform.copilot.runtime
analysis.ai.copilot -> agenttools
aiplatform.copilot.runtime -> shared
agenttools.*.mcp -> integrations
analysis.job/flow/ai -> analysis.options
analysis.job/flow/ai/features -> shared
api -> feature exceptions
any package -> common
```

Unikac nowych zaleznosci:

- `analysis.adapter -> analysis.evidence`,
- `analysis.adapter -> analysis.mcp`,
- `analysis.adapter -> analysis.ai`,
- `analysis.adapter -> agenttools`,
- `integrations -> analysis`,
- `integrations -> agenttools`,
- `integrations -> features`,
- `integrations -> aiplatform`,
- `aiplatform -> analysis`,
- `aiplatform -> features`,
- `aiplatform -> integrations`,
- `analysis.ai -> features`,
- `analysis.evidence -> features`,
- `analysis.mcp -> analysis.ai.copilot`,
- `analysis.ai -> analysis.mcp`,
- `analysis.flow -> konkretne adaptery` poza waskim scope/config resolverem,
- `analysis.job -> analysis.evidence.provider.*` poza prostym odczytem runtime
  facts do statusu UI.

Zamkniete krawedzie, ktorych nie przywracac:

- `analysis.adapter -> analysis.evidence`: adapter Dynatrace nie buduje juz
  query z `ElasticLogEvidenceView`; factory tego mapowania mieszka po stronie
  evidence providerow.
- `integrations -> analysis`: przeniesione adaptery `integrations.dynatrace`,
  `integrations.elasticsearch`, `integrations.gitlab` i
  `integrations.operationalcontext` oraz `integrations.database` pozostaja
  czystymi integracjami bez importow warstw aplikacyjnych.
- `analysis.mcp -> analysis.ai.copilot`: MCP wrappery mieszkaja teraz w
  `agenttools.<capability>.mcp`, a hidden tool context keys mieszkaja w
  neutralnym `agenttools.context.AgentToolContextKeys`.
- `analysis.adapter -> analysis.mcp`: DB request/result/scope/operator
  contracts mieszkaja teraz w `integrations.database`, a Database Spring AI
  tools mieszkaja w `agenttools.database.mcp`.
- `analysis.adapter -> agenttools`: adapter DB ma wlasne capability DTO i scope
  w `integrations.database`; MCP mapuje hidden `ToolContext` na ten scope.
- `analysis.evidence -> analysis.ai`: generyczne DTO evidence mieszkaja teraz
  w `shared.evidence`, a `analysis.ai.evidence` nie jest wlascicielem modelu
  evidence.
- `analysis.ai -> analysis.mcp`: GitLab tool response DTO uzywane przez
  capture evidence mieszkaja teraz w `agenttools.gitlab.mcp`, a Copilot
  runtime nie importuje historycznej warstwy MCP.
- `analysis.ai -> features`: incidentowe providery, preparation i coverage
  mieszkaja teraz w `features.incidentanalysis.ai.copilot`, a `analysis.ai`
  nie powinien importowac dedykowanego feature'a.

Najwazniejsze zamkniete krawedzie sa pilnowane przez
`PackageDependencyGuardTest`, ktory skanuje importy w `src/main/java`.

Przy dodawaniu kolejnych dedykowanych analiz nie traktowac
`analysis.job/flow/evidence` incydentow jako generycznego core. Najpierw
ustalic, ktora czesc jest reusable platform/capability, a ktora jest
feature-specific dla danej analizy.

Praktyczna zasada: jesli nowa klasa zaczyna potrzebowac importu "w gore" do
pakietu bardziej orchestration/UI/provider-specific, najpierw sprawdzic, czy
nie brakuje neutralnego DTO, resolvera albo listenera w blizszym pakiecie.
