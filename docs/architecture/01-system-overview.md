# System Overview

## Cel projektu

Projekt rozwija platforme do AI-augmented system analysis. Aplikacja ma
laczyc deterministic context gathering, curated operational context, reusable
agent tools i sesje AI, zeby pomagac operatorom, analitykom i developerom
rozumiec systemy.

Pierwszym produkcyjnym feature'em jest analiza incydentu na podstawie
`correlationId`. Historyczna nazwa repo i publiczne URL-e `/analysis/*`
pochodza z tego startu, ale docelowo nie ograniczaja produktu do incident
trackingu.

Docelowy kierunek platformy:

1. dedykowany feature definiuje publiczny request, evidence/source gathering,
   prompt, tools policy i result contract,
2. reusable integracje zbieraja dane z systemow zewnetrznych,
3. reusable tools udostepniaja kontrolowana eksploracje kodu, logow,
   operational context i danych,
4. platforma AI uruchamia sesje z allowlista tools, hidden contextem,
   budgetami, usage i eventami runtime,
5. feature zwraca wynik zrozumialy dla operatora/analityka oraz jawne
   ograniczenia widocznosci.

Obecny incident flow jest pierwsza realizacja tego modelu:

1. operator wysyla `correlationId`,
2. aplikacja zbiera evidence z systemow zewnetrznych,
3. AI interpretuje evidence,
4. AI moze dociagac dodatkowy kod z GitLaba i opcjonalnie zweryfikowac
   hipotezy danych przez Database tools,
5. aplikacja zwraca rozdzielony wynik: `functionalAnalysis` dla analityka
   biznesowo-systemowego oraz `technicalAnalysis` jako konkretny handoff do
   naprawy, weryfikacji albo przekazania dalej.

Planowane kolejne rodziny feature'ow to m.in. flow explorer, pytania o logike
funkcjonalna use case'ow oraz natural-language data diagnostics. Szczegolowy
kierunek produktu jest opisany w `00-product-direction.md`.

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
- w ekranie `GET /` ostatni krok AI pokazuje plaska liste aktywnosci Copilota
  i user-facing tool evidence: komunikaty/rozumowanie AI, usage/runtime oraz
  wywolania tools sa laczone w jeden tok wedlug zdarzen z pollingu, a kazdy
  wiersz ma ikone, prosty tekst, status i rozwijane szczegoly,
- w ekranie `GET /` ostatni krok AI pokazuje sumaryczne tokeny oraz
  uproszczona estymacje GitHub AI Credits i kosztu USD; tooltip tlumaczy
  nietechnicznie szczegoly z eventow Copilota i przelicznik tokenowy,
- ekran `GET /elastic` do recznego testowania helper endpointow Elastica,
- ekran `GET /gitlab` do recznego testowania helper endpointow GitLaba, w tym
  repository search, endpoint inventory i source resolve,
- ekran `GET /database` do recznego testowania shared/operator endpointow nad
  `DatabaseToolService` z jawnym operatorskim `environment`,
- ekran `GET /operational-context` do utrzymania katalogu systemow, repozytoriow,
  procesow, integracji, bounded contexts, zespolow, glossary, handoff rules,
  validation findings i open questions,
- glowne job-based API: `POST /analysis/jobs` i
  `GET /analysis/jobs/{analysisId}`,
  z opcjonalnym wyborem modelu AI i `reasoningEffort` przy starcie joba,
- follow-up chat dla zakonczonego joba przez
  `POST /analysis/jobs/{analysisId}/chat/messages`, ktory uruchamia nowa
  sesje AI z kontekstem tej samej analizy,
- endpoint shared/operator API `GET /analysis/ai/options`, ktory zwraca
  katalog modeli i dozwolone `reasoningEffort` z GitHub Copilot SDK, zeby
  frontend nie trzymal lokalnej listy modeli,
- endpoint shared/operator API `GET /api/ui/config`, ktory zwraca runtime
  konfiguracje brandu UI z fallbackiem `Team Delivery Workspace`,
- shared/operator API `GET /api/auth/github/status`,
  `GET /api/auth/github/start`, `GET /api/auth/github/callback` i
  `POST /api/auth/github/logout` dla autoryzacji Copilot SDK w trybach
  `LOCAL_TOKEN` oraz `GITHUB_APP`,
- shared/operator API `/api/operational-context/*` dla operator-facing widoku
  curated operational context,
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
- `GET /elastic`
  Angularowy ekran pomocniczy do recznego testowania helper endpointow
  Elastica oraz podgladu odpowiedzi JSON.
- `GET /gitlab`
  Angularowy ekran pomocniczy do recznego testowania helper endpointow
  GitLaba oraz podgladu odpowiedzi JSON. Legacy route `GET /evidence`
  przekierowuje w Angularze do `/elastic`.
- `GET /database`
  Angularowy ekran pomocniczy do recznego testowania Database tools przez
  shared/operator endpointy `/api/database/*`.
- `GET /operational-context`
  Angularowy ekran utrzymaniowy dla curated operational context: katalogu
  systemow, repozytoriow, code-search scopes, procesow, integracji,
  bounded contexts, zespolow, glossary, handoff rules, validation findings i
  open questions.
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
- `GET /api/ui/config`
  Shared/operator API konfiguracji brandu UI. Gdy `app.ui.title` nie ma
  tekstu, frontend pokazuje tylko `Team Delivery Workspace`; gdy property jest
  ustawione, wartosc property jest tytulem, a `Team Delivery Workspace`
  podtytulem.
- `GET /api/auth/github/status`
  Shared/operator API statusu autoryzacji Copilota. W `LOCAL_TOKEN` pokazuje
  lokalny token jako backendowy tryb dev, a w `GITHUB_APP` tworzy backendowa
  operator session cookie i raportuje, czy konto GitHub jest polaczone.
- `GET /api/auth/github/start`
  Start GitHub App OAuth web flow. Akceptuje tylko lokalny `returnUrl`, tworzy
  jednorazowy `state` powiazany z operator session i redirectuje do GitHuba.
- `GET /api/auth/github/callback`
  Callback OAuth: wymienia code na GitHub App user access token, pobiera profil
  i zapisuje zaszyfrowane tokeny po stronie backendu.
- `POST /api/auth/github/logout`
  Odlacza autoryzacje GitHub App dla biezacej operator session.
- `POST /api/gitlab/source/resolve`
  Narzedzie pomocnicze do znalezienia pliku po symbolu.
- `POST /api/gitlab/source/resolve/preview`
  Wersja do recznego testowania, zwracajaca skrocona tresc pliku.
- `POST /api/gitlab/repository/search`
  Narzedzie pomocnicze do recznego testowania mapowania `component -> repo` i
  opcjonalnego wyszukiwania kandydatow plikow.
- `POST /api/gitlab/repository/endpoints`
  Narzedzie pomocnicze do recznego testowania inventory endpointow REST w
  konkretnym repozytorium GitLaba.
- `POST /api/elasticsearch/logs/search`
  Narzedzie pomocnicze do wyszukiwania logow z Kibana proxy po `correlationId`.
  To jest jedyny endpoint testowy Elastica. Nie ma juz wariantu `preview`.
- `POST /api/database/*`
  Narzedzia pomocnicze do recznego testowania capability udostepnianych przez
  `DatabaseToolService`: scope, discovery tabel/kolumn, opis tabel, typed
  count/sample/group, relacje, joiny, porownanie mappingu i opcjonalny
  readonly SQL. Publiczny job flow nadal nie przyjmuje recznego scope DB.
- `GET /api/operational-context/*`
  Shared/operator API dla katalogu operational context: summary, listy encji,
  search, szczegoly encji, validation i open questions. To jest fasada nad
  `integrations.operationalcontext`, a nie incident job flow.

## Glowny podzial pakietow

Szczegolowy diagram runtime/data-flow i compile-time importow jest w
`05-package-dependencies.md`.

- `pl.mkn.incidenttracker`
  Glowna aplikacja Spring Boot.
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
- `pl.mkn.incidenttracker.api.aioptions`
  Shared/operator API dla katalogu modeli i endpointu
  `GET /analysis/ai/options`. Implementacja endpointu mapuje platformowy
  katalog modeli Copilota na obecne DTO aplikacji.
- `pl.mkn.incidenttracker.api.uiconfig`
  Shared/operator API runtime konfiguracji brandu UI dla Angulara. Nie jest
  czescia incident job flow.
- `pl.mkn.incidenttracker.api.githubauth`
  Shared/operator API autoryzacji GitHub dla UI oraz backendowa operator
  session cookie. Ten pakiet zna request HTTP, ale nie przechowuje tokenow w
  frontendzie ani publicznych requestach joba.
- `pl.mkn.incidenttracker.features.incidentanalysis.evidence`
  Deterministyczne zbieranie evidence przez providery i jawny opis krokow
  pipeline, z rownoleglym fan-outem Dynatrace + GitLab po deployment context.
- `pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.deployment`
  Wyprowadzanie deployment context z logs jako osobny krok przed Dynatrace i GitLabem.
- `pl.mkn.incidenttracker.features.incidentanalysis.ai.initial`
  Poczatkowa analiza incydentu: provider, request, preparation i JSON-only
  response z rozdzielonym `functionalAnalysis` oraz `technicalAnalysis`.
- `pl.mkn.incidenttracker.features.incidentanalysis.ai.chat`
  Follow-up chat po zakonczonej analizie incydentu.
- `pl.mkn.incidenttracker.shared.ai`
  Neutralne preferencje wykonania AI, non-secret `AnalysisAiAuthRef` oraz
  kontrakty token/cost/usage i visible activity trace dla flow, job UI i
  feature'ow.
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
  skill selection, tool policy, response parser i initial/follow-up run assembly.
  Ten pakiet sklada parametry dla platformowego runtime Copilota.
- `pl.mkn.incidenttracker.aiplatform.copilot.runtime`
  Neutralne elementy runtime SDK: properties, model listing, client options,
  `SessionConfig`, `MessageOptions` i prepared session bez znajomosci incident
  promptu ani incident policy.
- `pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth`
  Platformowe rozstrzyganie tokena Copilot tuz przed zbudowaniem
  `CopilotClientOptions`. Runtime zawsze przekazuje `githubToken` jawnie i
  ustawia `useLoggedInUser=false`.
- `pl.mkn.incidenttracker.aiplatform.copilot.runtime.options`
  Platformowy provider katalogu modeli Copilota i neutralne DTO opcji modeli.
  `api.aioptions` jest fasada mapujaca ten katalog na endpoint
  `GET /analysis/ai/options`.
- `pl.mkn.incidenttracker.aiplatform.copilot.runtime.execution`
  Uruchamianie klienta Copilota, sesji, lifecycle logging oraz
  `CopilotExecutionResult` z trescia odpowiedzi i user-visible
  `AnalysisAiUsage`; session events SDK sa mapowane na neutralny
  `AnalysisAiActivityEvent`, bez wystawiania typow SDK do UI.
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
  kontrakt decyzji.
- `pl.mkn.incidenttracker.aiplatform.copilot.tools.logging`
  Subskrypcja eventow invocation do operacyjnego logowania request/result.
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
  Properties, porty, adapter REST, modele logow oraz service search dla
  Elasticsearch/Kibana.
- `pl.mkn.incidenttracker.api.elasticsearch`
  Shared/operator endpoint testowy `POST /api/elasticsearch/logs/search`
  delegujacy do integracji Elasticsearch.
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
  Konfiguracja, porty, adapter REST oraz modele/search service GitLaba.
- `pl.mkn.incidenttracker.integrations.github.auth`
  Integracja GitHub App OAuth: properties, klient exchange/refresh, profil
  uzytkownika, state store, zaszyfrowany authorization store i AES-GCM cipher.
- `pl.mkn.incidenttracker.api.gitlab`
  Shared/operator endpoint repository search GitLaba delegujacy do integracji.
- `pl.mkn.incidenttracker.api.gitlab.source`
  Shared/operator endpointy source resolve GitLaba:
  `POST /api/gitlab/source/resolve` i wariant preview.
- `pl.mkn.incidenttracker.api.database`
  Shared/operator endpointy testowe nad `integrations.database.DatabaseToolService`.
  Controller buduje manualny `DbCapabilityScope` z operatorskiego
  `environment` i deleguje do typed DB capability.
- `pl.mkn.incidenttracker.api.operationalcontext`
  Shared/operator endpointy i view service dla katalogu operational context.
  Pakiet mapuje reusable `integrations.operationalcontext` na DTO dla UI
  `/operational-context`, bez importowania incident flow.
- `pl.mkn.incidenttracker.features.incidentanalysis.evidence.provider.gitlabdeterministic`
  Deterministic mapowanie logs i deployment context na code evidence z GitLaba.
- `pl.mkn.incidenttracker.agenttools.gitlab.mcp`
  MCP tools GitLaba delegujace do `integrations.gitlab`.
- `pl.mkn.incidenttracker.integrations.gitlab.source`
  Osobny use case rozwiazywania pliku po symbolu.
- `pl.mkn.incidenttracker.api`
  Obsluga bledow API, wspolny kontrakt walidacji i shared/operator API dla
  endpointow FE niezaleznych od jednego feature'a, np. fasady nad platforma
  albo integracjami. Endpointy konkretnego use case'u zostaja przy
  `features.<feature>.api`.
- `pl.mkn.incidenttracker.ui`
  Cienki routing Spring MVC dla route'ow Angulara, np. `/elastic`, `/gitlab`
  i `/operational-context`.
- Zamkniety root `pl.mkn.incidenttracker.analysis`
  Produkcyjny i testowy root `analysis.*` jest zamkniety. Publiczne URL-e
  moga nadal zawierac slowo `analysis`, ale nowe klasy Javy trafiaja do
  aktualnych wlascicieli: `features`, `api`, `integrations`, `agenttools`,
  `aiplatform`, `shared`, `common` albo `ui`.
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
  repo glowne, biblioteki i shared modules jako wspolny scope kodu tego
  systemu.
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
- Aktywnosc sesji Copilota jest productized i widoczna w job state jako
  generyczne `shared.ai.AnalysisAiActivityEvent`: turny, komunikaty,
  wywolania tooli, snapshoty context tokens/messages i usage eventy. Frontend
  merge'uje te eventy z `toolEvidenceSections` w jeden timeline analizy.
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
   hintach, np. `crm-service -> crm_service`,
4. jesli request zawiera `operationNames` albo `keywords`, adapter dodatkowo
   szuka kandydatow plikow,
5. endpoint zwraca rozwiazane repozytoria i opcjonalnie kandydatow plikow.

Ten endpoint nie jest czescia glownego job flow analizy, ale pomaga recznie
zweryfikowac te sama logike mapowania, z ktorej korzysta deterministic
provider i AI-guided exploration przez tools.

## Dodatkowy use case GitLab endpoint inventory

To jest osobny, pomocniczy flow do recznego testowania listowania endpointow
REST udostepnianych przez konkretne repozytorium:

1. klient podaje `group`, `projectName`, `branch` oraz opcjonalne filtry
   `endpointPathPrefix`, `httpMethod` i `maxScannedFiles`,
2. backend deleguje do `integrations.gitlab.GitLabRepositoryEndpointService`,
3. serwis uzywa wspolnego GitLab repository tree/cache od root repozytorium i
   sam wybiera produkcyjne source rooty w ukladzie multi-module,
4. parser best-effort znajduje Spring MVC/REST controller mappings,
5. endpoint zwraca liste endpointow, klasy/metody handlerow, pliki, linie,
   request/response types, confidence, limitations i suggested next reads.

Ten endpoint nie jest czescia glownego job flow analizy. Sluzy operatorowi do
manualnej weryfikacji tej samej capability, ktora jest wystawiona AI jako
`gitlab_list_repository_endpoints`.

## Dodatkowy use case Database workbench console

To jest osobny, pomocniczy flow diagnostyczno-testowy:

1. klient podaje operatorski `environment` jako neutralny scope integracji,
2. endpoint `/api/database/*` buduje techniczny scope workbench bez
   przyjmowania `correlationId`, `analysisRunId` ani incident/session scope'u,
3. request operacji jest przekazywany bezposrednio do `DatabaseToolService`,
4. integracja DB nadal egzekwuje configured environment, allowliste schematow,
   typed filters, masking/limiting i blokade raw SQL,
5. frontend `/database` pokazuje payload requestu, status HTTP i odpowiedz JSON.

Ten endpoint jest analysis-independent i nie zmienia glownego job flow analizy.
Incidentowy scope DB dla AI pozostaje feature-owned i jest przekazywany przez
hidden `ToolContext`, nie przez Workbench API.

## Dodatkowy use case Operational Context console

To jest operator-facing flow utrzymaniowy dla reusable katalogu systemow:

1. frontend route `/operational-context` pobiera dane z
   `/api/operational-context/*`,
2. backendowa fasada w `api.operationalcontext` deleguje do
   `integrations.operationalcontext`,
3. UI pokazuje summary, signal resolver, listy encji, validation findings,
   open questions i szczegoly encji,
4. ten sam katalog jest reuse'owany przez incident evidence provider,
   `opctx_*` tools, GitLab repository discovery i przyszle feature'y.

To nie jest osobny krok incident job flow. To shared/operator powierzchnia do
utrzymania jakosci katalogu, ktory ma byc reusable poza analiza incydentow.
