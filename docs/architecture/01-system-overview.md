# System Overview

## Cel projektu

Projekt buduje aplikacje Spring Boot do analizy incydentow na podstawie
`correlationId`.

Docelowy flow jest nastepujacy:

1. uzytkownik wysyla zadanie analizy,
2. aplikacja zbiera evidence z systemow zewnetrznych,
3. AI interpretuje evidence,
4. AI moze dociagac dodatkowy kod z GitLaba i opcjonalnie zweryfikowac
   hipotezy danych przez Database tools,
5. aplikacja zwraca diagnoze i rekomendowany kolejny krok lub kierunek poprawki.

## Aktualny stan

Na dzisiaj projekt ma:

- zrodlowa aplikacje Angular w katalogu `frontend/`, ktora po buildzie
  produkcyjnym zapisuje bundle do `src/main/resources/static`,
- ekran `GET /` serwowany przez Spring Boot z mozliwoscia importu i eksportu
  zapisu zakonczonej analizy jako JSON,
- w ekranie `GET /` widok promptu przygotowanego dla AI, mozliwy do skopiowania
  nawet wtedy, gdy sesja Copilota zakonczy sie bledem,
- w ekranie `GET /` ostatni krok AI pokazuje tez user-facing GitLab/DB evidence
  dociagniete przez tools w trakcie sesji Copilota i odswieza je wraz z
  pollingiem joba,
- w ekranie `GET /` ostatni krok AI pokazuje sumaryczne tokeny oraz
  uproszczona estymacje GitHub AI Credits i kosztu USD; tooltip tlumaczy
  nietechnicznie szczegoly z eventow Copilota i przelicznik tokenowy,
- ekran `GET /evidence` do recznego testowania helper endpointow Elastica i
  GitLaba,
- glowne job-based API: `POST /analysis/jobs` i
  `GET /analysis/jobs/{analysisId}`,
  z opcjonalnym wyborem modelu AI i `reasoningEffort` przy starcie joba,
- follow-up chat dla zakonczonego joba przez
  `POST /analysis/jobs/{analysisId}/chat/messages`, ktory uruchamia nowa
  sesje AI z kontekstem tej samej analizy,
- endpoint shared/operator API `GET /analysis/ai/options`, ktory zwraca
  katalog modeli i dozwolone `reasoningEffort` z GitHub Copilot SDK, zeby
  frontend nie trzymal lokalnej listy modeli,
- AI-first flow oparty o `AnalysisEvidenceProvider`, `InitialAnalysisProvider` i
  osobny `AnalysisAiChatProvider` dla kontynuacji zakonczonego joba,
- factory definicji tools dla GitHub Copilot Java SDK oparta o Spring tools,
- MCP tools dla Elastica, GitLaba i warunkowo dla Database,
- pierwszy realny adapter REST do Elasticsearch/Kibana proxy,
- pierwszy realny adapter REST do Dynatrace Managed,
- pierwszy realny adapter REST do GitLaba,
- osobny endpoint do testowego wyszukiwania logow z Elastica po `correlationId`,
- osobny endpoint do testowego mapowania hintow komponentu na repozytoria i
  kandydatow plikow w GitLabie,
- osobny endpoint do rozwiazywania pliku z GitLaba po symbolu klasy/interfejsu.

## Glowne entrypointy HTTP

- `GET /`
  Angularowy ekran operacyjny do uruchamiania analizy z pola `correlationId`.
- `GET /evidence`
  Angularowy ekran pomocniczy do recznego testowania helper endpointow
  Elastica i GitLaba oraz podgladu odpowiedzi JSON.
- `POST /analysis/jobs`
  Asynchroniczny start analizy wykorzystywany przez UI Angular. Request niesie
  `correlationId` oraz opcjonalne preferencje wykonania AI: `model` i
  `reasoningEffort`.
- `GET /analysis/jobs/{analysisId}`
  Odczyt statusu, evidence, wyniku asynchronicznej analizy i historii
  follow-up chatu.
- `POST /analysis/jobs/{analysisId}/chat/messages`
  Asynchroniczne polecenie lub pytanie do AI po zakonczonej analizie. Backend
  reuse'uje evidence, wynik, historie rozmowy, model/reasoning oraz hidden
  scope tools z oryginalnego joba.
- `GET /analysis/ai/options`
  Shared/operator API z katalogiem modeli AI dla UI. Backend pobiera go z
  Copilot SDK i zwraca `reasoningEffort` tylko dla modeli, ktore SDK opisuje
  jako wspierajace te ustawienia. Endpoint nie jest krokiem incident job flow.
- `POST /api/gitlab/source/resolve`
  Narzedzie pomocnicze do znalezienia pliku po symbolu.
- `POST /api/gitlab/source/resolve/preview`
  Wersja do recznego testowania, zwracajaca skrocona tresc pliku.
- `POST /api/gitlab/repository/search`
  Narzedzie pomocnicze do recznego testowania mapowania `component -> repo` i
  opcjonalnego wyszukiwania kandydatow plikow.
- `POST /api/elasticsearch/logs/search`
  Narzedzie pomocnicze do wyszukiwania logow z Kibana proxy po `correlationId`.
  To jest jedyny endpoint testowy Elastica. Nie ma juz wariantu `preview`.

## Glowny podzial pakietow

Szczegolowy diagram runtime/data-flow i compile-time importow jest w
`05-package-dependencies.md`.

- `pl.mkn.incidenttracker.analysis`
  Wspolne DTO, wynik i wyjatki analizy.
- `pl.mkn.incidenttracker.agenttools`
  Reusable tools/capability uzywane przez MCP wrappers i platforme AI, np.
  hidden tool context keys, nazwy tools oraz przenoszone wrappery MCP nad
  integracjami. Adaptery nie powinny importowac `agenttools`.
- `pl.mkn.incidenttracker.common`
  Male helpery wspolne dla calej aplikacji, np. `JsonPayloadReader`.
- `pl.mkn.incidenttracker.features.incidentanalysis.flow`
  Orkiestracja runtime analizy incydentu, response i listenery postepu flow.
- `pl.mkn.incidenttracker.features.incidentanalysis.job`
  Asynchroniczny feature `POST /analysis/jobs`,
  `GET /analysis/jobs/{analysisId}` i
  `POST /analysis/jobs/{analysisId}/chat/messages`.
- `pl.mkn.incidenttracker.features.incidentanalysis.job.api`
  Kontroler job API oraz request/response DTO dla UI.
- `pl.mkn.incidenttracker.features.incidentanalysis.job.state`
  In-memory projekcja joba: statusy, kroki, chat messages, snapshot i listener
  mapujacy zdarzenia orkiestratora na stan joba.
- `pl.mkn.incidenttracker.features.incidentanalysis.job.error`
  Wyjatki job API mapowane przez globalny handler bledow.
- `pl.mkn.incidenttracker.analysis.options`
  Opcje wykonania AI, katalog modeli i endpoint `GET /analysis/ai/options`.
  To przejsciowa fasada shared/operator API, a nie czesc incident feature'a ani
  wewnetrzna czesc providera AI. Implementacja endpointu mapuje platformowy
  katalog modeli Copilota na obecne DTO aplikacji. Docelowy split:
  neutralne preferencje wykonania AI w `shared.ai`, a HTTP controller/DTO
  katalogu modeli w `api.aioptions` albo rownowaznym pakiecie `api.*`.
- `pl.mkn.incidenttracker.features.incidentanalysis.evidence`
  Deterministyczne zbieranie evidence przez providery i jawny opis krokow
  pipeline, z rownoleglym fan-outem Dynatrace + GitLab po deployment context.
- `pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.deployment`
  Wyprowadzanie deployment context z logs jako osobny krok przed Dynatrace i GitLabem.
- `pl.mkn.incidenttracker.features.incidentanalysis.ai.initial`
  Poczatkowa analiza incydentu: provider, request, preparation i response
  JSON-only diagnozy.
- `pl.mkn.incidenttracker.features.incidentanalysis.ai.chat`
  Follow-up chat po zakonczonej analizie incydentu.
- `pl.mkn.incidenttracker.shared.ai`
  Neutralny kontrakt token/cost/usage dla flow, job UI, telemetry i feature'ow.
- `pl.mkn.incidenttracker.shared.evidence`
  Neutralny model evidence przekazywany miedzy evidence pipeline, flow, job UI
  i AI: `AnalysisEvidenceSection`, `AnalysisEvidenceItem`,
  `AnalysisEvidenceAttribute`; zawiera tez neutralny listener aktualizacji tool
  evidence przekazywany miedzy providerem AI, jobem i feature'em.
- `pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.operationalcontext`
  Enrichment katalogiem operacyjnym: sygnaly incydentu, matcher i mapper evidence.
- `pl.mkn.incidenttracker.integrations.operationalcontext`
  Query-based adapter curated operational context catalog i filtrowania go do
  reuse'u przez evidence i kolejne capability.
- `pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot`
  Incidentowe initial/chat providery oraz budowanie promptu, artifact digestu,
  skill selection, tool policy, response parser, quality gate i
  initial/follow-up run assembly.
  Ten pakiet sklada parametry dla platformowego runtime Copilota.
- `pl.mkn.incidenttracker.aiplatform.copilot.runtime`
  Neutralne elementy runtime SDK: properties, model listing, client options,
  `SessionConfig`, `MessageOptions` i prepared session bez znajomosci incident
  promptu ani incident policy.
- `pl.mkn.incidenttracker.aiplatform.copilot.runtime.options`
  Platformowy provider katalogu modeli Copilota i neutralne DTO opcji modeli.
  `analysis.options` jest tylko fasada mapujaca ten katalog na endpoint
  `GET /analysis/ai/options`.
- `pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution`
  Uruchamianie klienta Copilota, sesji, lifecycle logging oraz neutralny port
  metryk execution bez zaleznosci od konkretnego feature'a.
- `pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry`
  Neutralny port telemetry sesji Copilota: preparation metrics, response state,
  quality report i usage snapshot bez zaleznosci od incident feature'a ani
  obecnego kontraktu UI usage.
- `pl.mkn.incidenttracker.aiplatform.copilot.runtime.telemetry.session`
  Platformowa implementacja telemetry sesji Copilota: registry metryk,
  summary/tool logging, usage events SDK, budget metrics listener i adapter
  `CopilotSessionTelemetry`.
- `pl.mkn.incidenttracker.aiplatform.copilot.runtime.quality`
  Neutralny payload raportu jakosci odpowiedzi uzywany przez telemetryke; same
  reguly quality gate pozostaja po stronie feature'a.
- `pl.mkn.incidenttracker.aiplatform.copilot.tools.context`
  Budowanie hidden `ToolContext` i session-bound scope dla Spring tools jako
  neutralna mechanika platformy.
- `pl.mkn.incidenttracker.aiplatform.copilot.tools`
  `CopilotToolInvocationHandler`, czyli neutralna granica wykonania Spring
  `ToolCallback`: policies, hidden context, eventy invocation, kontrolowany
  rejection i parsing wyniku dla SDK.
- `pl.mkn.incidenttracker.aiplatform.copilot.tools.events`
  Wewnetrzne eventy tool invocation: `Started` oraz terminalny `Finished` z
  outcome `COMPLETED`, `REJECTED` albo `FAILED`.
- `pl.mkn.incidenttracker.aiplatform.copilot.tools.policy`
  Neutralne kontrakty policy invocation, kontrolowany rejection oraz session
  validation.
- `pl.mkn.incidenttracker.aiplatform.copilot.tools.policy.budget`
  Platformowa budget policy, state, registry, properties oraz neutralny
  kontrakt decyzji i telemetry listenera.
- `pl.mkn.incidenttracker.aiplatform.copilot.tools.logging`
  Subskrypcja eventow invocation do operacyjnego logowania request/result.
- `pl.mkn.incidenttracker.aiplatform.copilot.tools.telemetry`
  Neutralna klasyfikacja pojedynczego tool invocation dla telemetryki i
  budgetow.
- `pl.mkn.incidenttracker.aiplatform.copilot.tools.description`
  Neutralny kontrakt customizacji opisow tools, wykonywany przez runtime
  factory bez wiedzy o semantyce konkretnego feature'a.
- `pl.mkn.incidenttracker.aiplatform.copilot.tools.CopilotSdkToolFactory`
  Platformowa rejestracja Spring tools jako definicji Copilota.
- `pl.mkn.incidenttracker.aiplatform.copilot.tools.evidence`
  Session-bound store publikujacy neutralne `AnalysisEvidenceSection` z wynikow
  tool invocation przez sink przekazany przez feature.
- `pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools`
  Incident-specific subskrypcje eventow GitLab/Database tools i mapowanie
  wynikow do user-facing evidence.
- `pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.tools.description`
  Incident-specific guidance doklejane do opisow GitLab/Database tools dla
  Copilota.
- `pl.mkn.incidenttracker.integrations.elasticsearch`
  Properties, porty, adapter REST, modele logow oraz endpoint testowy dla
  Elasticsearch/Kibana.
- `pl.mkn.incidenttracker.agenttools.elasticsearch.mcp`
  MCP tools Elastica delegujace do `integrations.elasticsearch`.
- `pl.mkn.incidenttracker.integrations.database`
  Routing polaczen, metadata Oracle, readonly query execution i SQL guard DB
  capability.
- `pl.mkn.incidenttracker.agenttools.database.mcp`
  Session-bound MCP tools diagnostyki danych delegujace do
  `pl.mkn.incidenttracker.integrations.database`. Kontrakty
  request/result/scope i operatory DB mieszkaja przy integracji DB.
- `pl.mkn.incidenttracker.integrations.dynatrace`
  Modele i adapter REST dla runtime signals Dynatrace
  (`entities`, `problems`, `metrics`).
- `pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.dynatrace`
  Krok pipeline publikujacy runtime signals Dynatrace jako evidence.
- `pl.mkn.incidenttracker.integrations.gitlab`
  Konfiguracja, porty, adapter REST oraz pomocnicze endpointy testowe GitLaba.
- `pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.gitlabdeterministic`
  Deterministic mapowanie logs i deployment context na code evidence z GitLaba.
- `pl.mkn.incidenttracker.agenttools.gitlab.mcp`
  MCP tools GitLaba delegujace do `integrations.gitlab`.
- `pl.mkn.incidenttracker.integrations.gitlab.source`
  Osobny use case rozwiazywania pliku po symbolu.
- `pl.mkn.incidenttracker.api`
  Obsluga bledow API i wspolny kontrakt walidacji. Docelowo takze miejsce na
  shared/operator API dla endpointow FE niezaleznych od jednego feature'a, np.
  fasady nad platforma albo integracjami. Endpointy konkretnego use case'u
  zostaja przy `features.<feature>.api`.
- `frontend/`
  Workspace Angular z komponentami, serwisami i konfiguracja buildu UI.
- `src/main/resources/static`
  Wygenerowany produkcyjny bundle Angulara serwowany przez Spring Boot.

## Aktualny model runtime

- Elasticsearch dziala przez rzeczywisty adapter REST do Kibana proxy.
- Dynatrace dziala przez rzeczywisty adapter REST.
- Dynatrace nie jest wystawiany jako MCP tool dla AI.
- Dynatrace sluzy tylko do inicjalnego wzbogacenia promptu
  o runtime signals skorelowane z logami Elastica i deployment context.
- GitLab w runtime dziala przez rzeczywisty adapter REST.
- Deployment context jest osobnym krokiem evidence i jest reuse'owany przez
  Dynatrace, GitLab deterministic provider i warstwe orchestration.
- Dynatrace i GitLab deterministic startuja po deployment context z tego samego
  snapshotu `AnalysisContext`, ale ich wyniki sa nadal dolaczane do evidence w
  stalej kolejnosci pipeline.
- GitLab deterministic provider i GitLab MCP tools sa wydzielone do osobnych
  pakietow; MCP tools mieszkaja w `agenttools.gitlab.mcp` i reuse'uja ten sam
  adapter GitLaba.
- GitLab MCP tools potrafia nie tylko szukac kandydatow repo i flow contextu,
  ale tez znajdowac referencje/importy dla ugruntowanej klasy, zeby lepiej
  naprowadzac DB diagnostics.
- Database diagnostics sa osobna, opcjonalna capability AI-guided i nie sa
  evidence providerem.
- Operational context jest osobnym enrichment stepem nad juz zebranym evidence.
- Bazowy curated operational context jest ladowany przez osobny adapter, a nie
  bezposrednio przez sam provider enrichmentu.
- Operational context publikuje dla dopasowanego systemu jawny code search
  scope: repozytoria/projekty, pakiety i class hints, zeby Copilot traktowal
  repo glowne, biblioteki i shared modules jako kod jednego komponentu
  wdrozeniowego.
- Job flow reuse'uje orchestration warstwe `AnalysisOrchestrator`.
- Job flow moze przekazac do generycznego requestu AI opcjonalny wybor
  modelu i `reasoningEffort`; nie zmienia to evidence scope'u, branchy,
  srodowiska ani GitLab group.
- Follow-up chat jest kontynuacja zakonczonego joba, a nie nowym publicznym
  requestem analizy. Kazda wiadomosc uruchamia osobna sesje AI, osadza
  evidence i finalny wynik w promptcie oraz wystawia session-bound tools tylko
  w zakresie rozwiazanym przez pierwotna analize.
- Lista modeli i dostepnych `reasoningEffort` dla UI pochodzi z platformowego
  provider'a opcji Copilota przez backendowy shared/operator endpoint opcji AI.
  Frontend nie jest source of truth dla mozliwosci modeli.
- Runtime AI providerem jest GitHub Copilot SDK.
- Zuzycie tokenow jest zbierane z eventow sesji Copilota i wystawiane do UI
  jako generyczne `shared.ai.AnalysisAiUsage`, bez typow SDK w kontrakcie
  frontendu.
  Frontend liczy orientacyjne GitHub AI Credits/USD z tokenow i modelu jako
  product-facing estymacje oplacalnosci, nie jako fakture.
- Skill Copilota jest pakowany jako resource aplikacji i wypakowywany do
  katalogu runtime.
- Frontend Angular jest buildowany w tym samym repo i serwowany z tego samego
  JAR-a jako statyczne zasoby.

## Najwazniejszy przeplyw

```mermaid
flowchart LR
    A["GET /"] --> B["Angular bundle from static resources"]
    B --> U["GET /analysis/ai/options"]
    B --> C["POST /analysis/jobs"]
    C --> D["AnalysisJobService"]
    D --> E["Background analysis task"]
    E --> F["AnalysisOrchestrator"]
    F --> G["AnalysisEvidenceCollector"]
    G --> H["Elastic evidence provider"]
    G --> I["Deployment context evidence provider"]
    G --> J["Dynatrace evidence provider"]
    G --> K["GitLab deterministic evidence provider"]
    G --> L["Operational context evidence provider"]
    L --> M["AnalysisContext"]
    F --> N["InitialAnalysisProvider"]
    N --> O["Copilot SDK"]
    O --> P["Elastic tools (optional during session)"]
    O --> R["GitLab tools (optional during session)"]
    O --> Q["Database tools (optional during session)"]
    N --> S["AnalysisResultResponse"]
    B --> T["GET /analysis/jobs/{analysisId}"]
    T --> D
    B --> U2["POST /analysis/jobs/{analysisId}/chat/messages"]
    U2 --> D
    D --> V["Background follow-up chat task"]
    V --> W["AnalysisAiChatProvider"]
    W --> O
```

## Dodatkowy use case Elasticsearch log search

To jest osobny, pomocniczy flow diagnostyczno-testowy:

1. klient podaje tylko `correlationId`,
2. serwis bierze `analysis.elasticsearch.base-url`,
   `analysis.elasticsearch.kibana-space-id`,
   `analysis.elasticsearch.index-pattern`,
   `analysis.elasticsearch.authorization-header` i limity odpowiedzi z
   `application.properties`,
3. lokalny adapter REST zawsze ignoruje bledy certyfikatu i hosta tylko dla tej
   integracji,
4. serwis wywoluje Kibana console proxy przez `POST .../api/console/proxy`,
5. adapter mapuje `_source.fields`, `kubernetes` i `container` do typowanego
   modelu logu,
6. MCP tool i endpoint przyjmuja tylko `correlationId`, a adapter sam dobiera
   odpowiedni rozmiar i limity z konfiguracji,
7. endpoint zwraca wpisy, metadata i komunikat `OK` albo czytelny blad.

## Dodatkowy use case GitLab source resolve

To jest osobny, pomocniczy flow:

1. klient podaje `gitlabBaseUrl`, `groupPath`, `projectPath`, `ref`, `symbol`,
2. serwis pobiera drzewo repozytorium z GitLaba,
3. w granicach jednego requestu cache'uje to drzewo dla tego samego
   `gitlabBaseUrl/project/ref`,
4. ranking wybiera najlepszy plik,
5. serwis pobiera raw content,
6. endpoint zwraca kandydatow i tresc pliku.

Ten endpoint nie jest centralnym krokiem job flow analizy, ale ten sam serwis
jest reuse'owany przez GitLab deterministic provider.

## Dodatkowy use case GitLab repository search

To jest osobny, pomocniczy flow do recznego testowania mapowania repozytorium:

1. klient podaje `projectHints`, opcjonalnie `branch`, `operationNames` i
   `keywords`,
2. serwis bierze `analysis.gitlab.group` z konfiguracji,
3. adapter wyszukuje projekty w tej grupie i podgrupach po znormalizowanych
   hintach, np. `agreement-process -> agreement_process`,
4. jesli request zawiera `operationNames` albo `keywords`, adapter dodatkowo
   szuka kandydatow plikow,
5. endpoint zwraca rozwiazane repozytoria i opcjonalnie kandydatow plikow.

Ten endpoint nie jest czescia glownego job flow analizy, ale pomaga recznie
zweryfikowac te sama logike mapowania, z ktorej korzysta deterministic
provider i AI-guided exploration przez tools.
