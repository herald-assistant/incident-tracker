# System Overview

## Cel projektu

Projekt rozwija platforme do AI-augmented system analysis. Aplikacja ma
laczyc deterministic context gathering, curated operational context, reusable
agent tools i sesje AI, zeby pomagac operatorom, analitykom i developerom
rozumiec systemy.

Pierwszym produkcyjnym feature'em jest analiza incydentu na podstawie logow:
pobranych z Elasticsearch po `correlationId` albo zalaczonych jako CSV z
Kibana/Elastic Discover. Historyczna nazwa repo i publiczne URL-e
`/analysis/*` pochodza z pierwszego startu po `correlationId`, ale docelowo
nie ograniczaja produktu do incident trackingu.

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

1. operator wybiera zrodlo logow: Elasticsearch po `correlationId` albo upload
   CSV,
2. aplikacja pobiera albo waliduje i mapuje logi do wspolnej sekcji evidence,
3. aplikacja wzbogaca evidence danymi z systemow zewnetrznych,
4. AI interpretuje evidence,
5. AI moze dociagac dodatkowy kod z GitLaba i opcjonalnie zweryfikowac
   hipotezy danych przez Database tools,
6. aplikacja zwraca rozdzielony wynik: `functionalAnalysis` dla analityka
   biznesowo-systemowego oraz `technicalAnalysis` jako konkretny handoff do
   naprawy, weryfikacji albo przekazania dalej.

Dostepne obok Incident Analysis sa Flow Explorer, Change Verification,
Config Drift Viewer i Delivery Complexity Assessment.
Backendowa podstawa UI Explorer udostepnia feature-owned kontrakt,
asynchroniczny job oraz neutralna capability GitLaba do graph-first
rozpoznawania Angular/Nx route/view catalog i screen reachability. Integracja
wyszukuje produkcyjny lancuch `bootstrapApplication(...) -> provideRouter(...)`,
przechodzi tylko po osiagalnych importach, `children` i lazy routes, domyka
topologie tras przed rozwijaniem targetow widokow oraz nie buduje repository
inventory. Extensionless import stosuje deterministyczna kolejnosc TypeScript
`module.ts` -> `module/index.ts`, zatrzymuje sie po pierwszym trafieniu i
cache'uje resolution. Bezposredni `loadComponent: () => import(...)` rozwiazuje
default export do rzeczywistego symbolu widoku. Local lazy factory
`() => ImportedComponent` obsluguje named i default import, a literalne tablice
`routes` splaszczane przez `reduce/flatMap` sa rozwijane statycznie bez
wykonywania TypeScriptu. Ograniczenia katalogu pozostaja twarde: domyslnie
200 000 znakow na plik i 2 000 000 lacznie, z jedna diagnostyka przyczynowa po
wyczerpaniu limitu lacznego. Ref jest rozstrzygany
bezposrednio do immutable commit id przez GitLab, niezaleznie od metadanych
dowolnego pliku.
Feature-owned `GET /api/ui-explorer/screens` rozwiazuje repository/ref scope z
Operational Context i zwraca bounded katalog bez ujawniania danych GitLaba.
Kosztowny wynik route graph discovery jest utrwalany w local workspace pod
pelnym kluczem system/ref/repository/search scope/graph limits. Domyslny odczyt
reuse'uje wpis rowniez po restarcie aplikacji; jawne `refresh=true` omija i
usuwa tylko dopasowany wpis przed ponownym discovery. Automatyczne ladowanie
widokow w Angularze korzysta z cache, natomiast Enter w polu ref oraz
`Load views` wymuszaja refresh.
UI Explorer job waliduje katalogowa source revision, buduje wewnetrzny bounded
screen reachability graph i wystawia publiczny manifest, coverage, diagnostics
oraz limity bez repository scope. Graf zaczyna od effective route chain i
poddrzewa wybranego route node, laczy view targets przez jawne `ROUTED_CHILD`,
rozwija iteracyjny BFS osiagalnych komponentow i deduplikuje faktycznie uzyte
serwisy, state, guardy, walidatory i backend clients. Puste segmenty child
routes zachowuja najblizszego routowanego rodzica. Nie buduje pelnego
snapshotu plikow ani inventory; frontier pozostaje jawna kolejka dalszego
targeted research.
Wewnetrzny krok `AI_PREPARATION` przygotowuje siedem logical artifacts, feature
prompt i guidance dla trzech polskich skilli. Prompt osadza kazdy artifact
dokladnie raz i uklada je w kolejnosci request/sekcje, ekran/rewizja, effective
route i BFS z zaleznosciami, source slices, coverage/research queue, kontrakt
pisania funkcjonalnego oraz kontrakt odpowiedzi. Artifact source slices v7
grupuje komponenty, ich pelne osiagalne template'y i zaleznosci wedlug sciezki
pliku w pierwszym porzadku
reachability, renderuje wspolne importy i identyczne body raz oraz zachowuje
kazdy odmienny wariant, slice ref, symbol, metode, relacje i omission marker.
Coverage dodaje zwiezly `completenessSignals` do wykrywania pominiec w
sekcjach `DEEP`, bez tworzenia osobnej tresci raportu;
publikuje bezpieczne metadane artifacts oraz zapisuje dokladny `preparedPrompt`
przed wywolaniem AI. Feature ma rowniez izolowany provider Copilota:
readiness gate, hidden GitLab scope, default-deny allowliste, goal-driven
targeted research bez feature'owego limitu liczby wywolan oraz strict parser
kompletnej, partial i malformed odpowiedzi. Materialny brak child route, komponentu, template,
formularza, modala, serwisu, state logic albo klienta z zatwierdzonego
repository scope wymaga proby deterministycznego route albo symbol slice, a
generycznego search/read tylko wtedy, gdy nie istnieje jeszcze
bezpieczny `sliceRef`; wynik
z takim brakiem bez zarejestrowanej proby jest odrzucany. Kolejne konkretne
luki sa pobierane do osiagniecia readiness albo potwierdzenia granicy
runtime/zewnetrznego scope; licznik wywolan nie finalizuje analizy. Parser nie podnosi
deterministycznego `PARTIAL` do `READY` bez przechwyconego fallback source
evidence. `POST`
`/api/ui-explorer/jobs` zwraca `202`, a job wykonuje context, jednokrotne
przygotowanie promptu i provider poza watkiem HTTP. Atomowy snapshot publikuje
kroki, context/tool evidence, activity, usage, result i report oraz kontrolowane
`COMPLETED`, `PARTIAL`, `BLOCKED` albo `FAILED`. Kazdy krok wskazuje
`consumesEvidence` i `producesEvidence`, a przygotowany prompt pozostaje
dostepny rowniez po bledzie pozniejszej sesji AI. Terminalne snapshoty sa
sanitizowane z ukrytego scope'u i szczegolow tools, zachowuja prompt i sa
zapisywane w lokalnej historii pod feature key `ui-explorer`;
shared `/api/analysis/runs` odczytuje je po restarcie bez mozliwosci
kontynuacji. Osobny `tdw.ui-explorer-export/v5` zapewnia sanitizowany export
terminalnego wyniku oraz read-only import z dokladna walidacja wersji i
ponowna sanitizacja przed zapisem do historii. Workspace Angular pod
`/ui-explorer` udostepnia route, sidebar i landing card oraz feature-owned
konfiguracje z prawdziwych `input-options`, katalogu ekranow i shared katalogu
AI. Workspace uzywa tego samego zwartego wzorca analysis composer co Flow
Explorer: target row grupuje frontend, branch/ref, widok i odswiezenie
katalogu, scope row grupuje rozwijane tryby sekcji, model i reasoning,
a ponizej znajduje sie opis scenariusza oraz obszar wyniku.
Operator nadal moze edytowac wszystkie osiem trybow sekcji bez stalego
zajmowania wysokosci strony. Source revision jest automatycznie przypisana do
katalogu i czyszczona po zmianie scope'u. `Run UI Explorer` wysyla
feature-owned start request, natychmiast pokazuje snapshot z `202` i korzysta
ze wspolnego pollingu bez nakladania requestow. Podczas aktywnego runu
konfiguracja jest zablokowana. Shared analysis aside pokazuje kroki,
przypisane do nich deterministic/tool evidence, dokladny prompt przygotowany
przed AI, aktywnosc AI, feedback i usage. Stepper jest read-only inspektorem:
terminalny krok pozostaje klikalny takze wtedy, gdy poprzednik ma `PARTIAL`,
dzieki czemu `AI_PREPARATION` zawsze udostepnia zapisany prompt. Blad pollingu
zachowuje ostatni snapshot i pozwala jawnie ponowic odczyt. Terminalne statusy
to `COMPLETED`, `PARTIAL`, `BLOCKED` i `FAILED`. Dla `COMPLETED` i `PARTIAL`
workspace renderuje `report` jako glowny dokument: naglowek, podsumowanie,
aktywne sekcje, confidence, references, visibility limits i open questions.
Feature-owned `result` zasila business-first Markdown o kanonicznej strukturze
dla kazdej aktywnej sekcji; nie posiada osobnego kontraktu ani appendixu
zaleznosci przekrojowych. Techniczne source refs
sa zwijanym evidence; UI nie pokazuje raw JSON ani surowej tresci source w
glownym raporcie, a prepared prompt jest osobnym widokiem diagnostycznym w
aside. Shared result header, renderer Markdown, section content
i report meta utrzymuja znany wzorzec prezentacji, a caly dokument mozna
skopiowac albo pobrac jako Markdown. Katalog i naglowek raportu identyfikuja
widok przede wszystkim przez `routePattern`, a nazwe komponentu pokazuja jako
metadata pomocnicze; branch i immutable commit pozostaja w danych rewizji,
lecz nie sa podtytulem raportu. `BLOCKED`, `FAILED` oraz terminalny stan
bez raportu sa jawne i nie tworza wyniku zastepczego. Analysis History rozpoznaje
feature key `ui-explorer` i otwiera zapisany run przez `localRunId`, bez
ladowania pelnego JSON-a na liscie. Workspace waliduje dokladnie wewnetrzna
koperta `tdw.ui-explorer-local-run/v5`, odtwarza konfiguracje i raport jako
read-only oraz nie uruchamia pollingu, continuation, follow-up chatu ani resume.
Portable JSON jest importowany przez backendowa granice walidacji, a wynik live,
history albo imported jest eksportowany przez kanoniczny endpoint feature'a.
UI jawnie rozroznia wszystkie trzy pochodzenia, zachowuje copy/download Markdown
i odrzuca obca, starsza, nowsza lub uszkodzona koperta bez fallbacku.
Kolejne rodziny moga obejmowac functional logic explorer oraz
natural-language data diagnostics. Szczegolowy kierunek produktu jest opisany
w `product-direction.md`.

## Aktualny stan

Na dzisiaj projekt ma:

- zrodlowa aplikacje Angular w katalogu `frontend/`, ktora po buildzie
  produkcyjnym zapisuje bundle do `src/main/resources/static`,
- wspolny shell UI `Team Delivery Workspace` z lewym sidebarem, kontekstowym
  topbarem i grupami nawigacji `Analysis Features`, `Tool Workbench` oraz
  `Platform`,
- ekran `GET /` jako startowy `Platform / Team Delivery Workspace` overview,
  z szybkim wejsciem do aktywnych feature'ow i nietechnicznym opisem tego, jak
  platforma oszczedza czas w codziennej pracy,
- ekran `GET /incident-analysis` serwowany przez Spring Boot z mozliwoscia
  importu i eksportu zapisu zakonczonej analizy jako JSON,
- ekran `GET /change-verification` dla compliance-only review Jira/Confluence,
  instrukcji repozytorium i implementacji z MR. Wynik rozdziela source-defined
  `STORY_COMPLIANCE`, `INSTRUCTION_COMPLIANCE` oraz maksymalnie piec
  `INFERRED_CRITICAL_CHECKS`; sugestie AI sa oceniane wobec tego samego evidence,
  ale nie zmieniaja werdyktu source-defined compliance. Import/eksport uzywa
  tylko aktualnego `change-verification-result-v4` i odrzuca starsze wersje,
- ekran `GET /flow-explorer` do endpoint-first dokumentacji flow; nowe runy,
  kontrakt API i runtime obsluguja obecnie tylko `DEEP_DISCOVERY`, a
  `Test scenarios` oraz `Risk detection` sa widoczne jako disabled z oznaczeniem
  `SOON`. Eksporty i lokalne snapshoty tych dwoch wycofanych goals nie sa
  importowane, odtwarzane ani kontynuowane; aktualne wyniki `DEEP_DISCOVERY`
  pozostaja obslugiwane,
- ekran `GET /config-drift-viewer` do rownoleglego porownania
  konfiguracji wielu `internal-service` pomiedzy branchami z rodzin `dev`,
  `test`, `uat` i `zt`; `BASIC` pokazuje
  wylacznie deterministyczny diff per plik, a zaimplementowany `DEEP` dodaje
  oddzielna interpretacje AI; podczas aktualnego rollout readiness nowy wybor
  `DEEP` w glownym formularzu jest tymczasowo disabled i oznaczony `SOON`,
  natomiast zapisane/importowane wyniki `DEEP` nadal sa renderowane,
- ekran `GET /delivery-complexity-assessment` do uruchamiania oceny
  dostarczonej zlozonosci dla projektu Jira i zakresu dat; widok pokazuje
  postep, czastkowe Delivery Units, DSP, coverage, confidence, visibility
  limits i usage/cost oraz odtwarza aktywny lub terminalny local run,
- w ekranie `GET /incident-analysis` start analizy ma wybor zrodla logow:
  Elasticsearch po `correlationId` albo upload CSV; gdy konfiguracja
  Elasticsearch/Kibana jest niepelna, sciezka `correlationId` jest zablokowana,
  ale CSV upload pozostaje dostepny,
- w ekranie `GET /incident-analysis` widok promptu przygotowanego dla AI,
  mozliwy do skopiowania nawet wtedy, gdy sesja Copilota zakonczy sie bledem,
- w ekranie `GET /incident-analysis` ostatni krok AI pokazuje tez user-facing GitLab/DB evidence
  dociagniete przez tools w trakcie sesji Copilota i odswieza je wraz z
  pollingiem joba,
- w ekranie `GET /incident-analysis` ostatni krok AI pokazuje plaska liste
  aktywnosci Copilota i user-facing tool evidence:
  komunikaty/rozumowanie AI, usage/runtime oraz wywolania tools sa laczone w
  jeden tok wedlug zdarzen z pollingu, a kazdy wiersz ma ikone, prosty tekst,
  status i rozwijane szczegoly,
- w ekranie `GET /incident-analysis` ostatni krok AI pokazuje sumaryczne tokeny oraz
  uproszczona estymacje GitHub AI Credits i kosztu USD; tooltip tlumaczy
  nietechnicznie szczegoly z eventow Copilota i przelicznik tokenowy,
- ekrany Tool Workbench: `GET /elastic`, `GET /gitlab`, `GET /jira`,
  `GET /confluence`, `GET /config-drift-viewer-tools`, `GET /database` i
  `GET /operational-context` do recznego
  testowania, debugowania i zbierania inputu z reusable capability bez
  przenoszenia logiki incident analysis do tych widokow,
- ekran `GET /operational-context` w Tool Workbench do utrzymania katalogu
  systemow, repozytoriow, procesow, integracji, bounded contexts, zespolow,
  glossary, handoff rules, validation findings i open questions,
- ekran `GET /workspace-settings` w sekcji `Platform` do podgladu efektywnych
  wartosci z `application.properties` i zapisu lokalnych override'ow do
  `${tdw.workspace.directory}/settings.json` dla brandu UI oraz podstawowych
  parametrow Copilota, Jiry, Confluence, GitLaba, Elasticsearch i Dynatrace;
  dla Jiry i Confluence zakres obejmuje tylko `base-url` oraz `token`,
- ekran `GET /ai-skills` i deep link `GET /ai-skills/{skillName}` w sekcji
  `Platform` do przegladania i walidowanej edycji tego samego effective
  katalogu skilli, ktory dostaja sesje Copilota; UI nie pokazuje sciezki
  storage i nie udostepnia run ani assignment per feature,
- glowne job-based API: `POST /api/analysis/jobs` i
  `GET /api/analysis/jobs/{analysisId}`,
  z wyborem zrodla logow oraz opcjonalnym wyborem modelu AI i
  `reasoningEffort` przy starcie joba; legacy aliasy `/analysis/**` pozostaja
  tylko dla kompatybilnosci,
- feature-owned endpoint `GET /api/analysis/jobs/input-options`, ktory mowi UI,
  czy start przez Elasticsearch po `correlationId` jest dostepny,
- follow-up chat dla zakonczonego joba przez
  `POST /api/analysis/jobs/{analysisId}/chat/messages`, ktory kontynuuje zapisana
  sesje Copilota i wysyla do niej tresc wiadomosci operatora,
- shared/operator API `/analysis/runs` dla lokalnej historii runow; snapshot
  runu jest zapisywany od startu joba i aktualizowany w trakcie pracy backendu,
  zeby UI moglo odtworzyc input i ostatni znany stan bez ponownego klikania,
- endpoint shared/operator API `GET /analysis/ai/options`, ktory zwraca
  katalog modeli i dozwolone `reasoningEffort` z GitHub Copilot SDK, zeby
  frontend nie trzymal lokalnej listy modeli,
- endpoint shared/operator API `GET /api/ui/config`, ktory zwraca runtime
  konfiguracje brandu UI z fallbackiem `Team Delivery Workspace` oraz
  wymagany wspolny `defaultBranch` z `platform.source-code.default-branch`,
- shared/operator API `GET /api/workspace/settings` i
  `PUT /api/workspace/settings`, ktore laczy `application.properties` z
  lokalnym `settings.json`; jawny override z workspace'u ma pierwszenstwo,
- shared/operator API `GET /api/ai/skills` i
  `GET /api/ai/skills/{skillName}` dla metadanych oraz tresci efektywnego
  katalogu runtime; lookup uzywa dokladnej zwalidowanej nazwy i nie rozwiazuje
  model-facing ani operator-facing sciezki pliku,
- shared/operator API `GET /api/auth/github/status`,
  `GET /api/auth/github/start`, `GET /api/auth/github/callback` i
  `POST /api/auth/github/logout` dla autoryzacji Copilot SDK w trybach
  `LOCAL_TOKEN` oraz `GITHUB_APP`,
- shared/operator API `/api/operational-context/*` dla operator-facing odczytu
  curated operational context oraz capability-gated maintenance dziewieciu
  typow YAML w lokalnym workspace,
- feature-owned API `/api/config-drift-viewer/v1/*` dla input
  options, preflightu `DEEP`, asynchronicznych jobow oraz read-only importu,
- feature-owned `POST /api/change-verification/jobs` i
  `GET /api/change-verification/jobs/{jobId}` dla asynchronicznego source
  discovery, AI compliance i snapshotu z reportem, activity, evidence oraz
  usage,
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
- neutralna capability `integrations.gitlab.frontend` do statycznego,
  ograniczonego rozpoznawania Angular/Nx: workspace signals, standalone i
  module routes, lazy loading, guardy, route parameters, view roots,
  template/style, formularze, NgRx, REST, WebSocket i auth signals. Route
  discovery rozwiazuje rowniez importowane statyczne modele `const`, zagniezdzone
  property chains, proste interpolacje statycznych enumow oraz importy wskazane
  przez `baseUrl` i wildcard `compilerOptions.paths` w `tsconfig*.json`.
  Targeted module resolution zachowuje file-before-index precedence, cache'uje
  wynik i wspiera named oraz default-export lazy view targets.
  Capability nadal nie uruchamia ani nie kompiluje badanego TypeScriptu;
  niejednoznaczne aliasy, cykle, runtime factories, dynamiczne wyrazenia oraz
  osiagniete limity sa jawnymi diagnostics, a nie zgadywanym wynikiem.

## Glowne entrypointy HTTP

- `GET /`
  Angularowy ekran `Platform / Team Delivery Workspace` jako overview
  platformy, szybkie wejscie do aktywnych feature'ow i customer-centric opis
  automatyzacji pracy bez eksponowania mechaniki AI/tools.
- `GET /incident-analysis`
  Angularowy ekran `Analysis Features / Incident Analysis` do uruchamiania
  analizy z logow pobranych po `correlationId` albo z zalaczonego CSV.
- `GET /change-verification`
  Angularowy ekran `Analysis Features / Change Verification`. Operator podaje
  Jira key albo URL i wybiera Story Compliance oraz Instruction Compliance.
  Raport pokazuje wymagania zrodlowe osobno od niekontraktowych kontroli
  krytycznych zasugerowanych przez AI.
- `POST /api/change-verification/jobs`
  Uruchamia asynchroniczna weryfikacje. `GET` z `/{jobId}` zwraca snapshot
  krokow, source/tool evidence, activity, prompt, usage, strukturalne checki i
  kanoniczny `AnalysisReport`.
- `GET /config-drift-viewer`
  Angularowy workspace porownania konfiguracji runtime. Formularz wybiera
  repozytorium, wiele `internal-service` oraz branch zrodlowy/docelowy.
  Wszystkie systemy sa domyslnie zaznaczone, backend wykonuje izolowane
  porownania z limitem rownoleglosci, a wynik pokazuje zakladke per komponent.
  `BASIC` jest
  aktualnie jedynym trybem mozliwym do wybrania; widoczny `DEEP` ma disabled
  affordance `SOON`. Backendowy kontrakt i prezentacja zapisanych wynikow
  `DEEP` pozostaja dostepne.
- `GET /api/config-drift-viewer/v1/input-options`
  Zwraca dozwolone repozytoria konfiguracji, systemy, branche i tryby bez
  ujawniania connection credentials.
- `GET /api/config-drift-viewer/v1/deep-preflight`
  Sprawdza, czy system ma jednoznaczny configuration directory, code-search
  scope i osiagalny ref kodu. Zwraca blocker albo ograniczenia widocznosci.
- `POST /api/config-drift-viewer/v1/jobs`
  Uruchamia asynchroniczny batch dla uporzadkowanych `systemIds`. `GET` z
  `/{jobId}` zwraca postep parent joba oraz izolowane component snapshots z
  operatorskim `configurationDiff`; report, activity i usage sa per komponent
  i sa obecne tylko dla `DEEP`.
- `POST /api/config-drift-viewer/v1/imports`
  Waliduje kompletny kontrakt V1 calego batcha i zwraca snapshot read-only.
- `GET /delivery-complexity-assessment`
  Angularowy workspace `Analysis Features / Delivery Complexity Assessment`
  z projektem Jira, zakresem dat, modelem/effort, pollingiem i czastkowymi
  wynikami jednostek.
- `POST /api/delivery-complexity-assessment/jobs`
  Tworzy job dopiero po synchronicznym zapisie snapshotu `QUEUED` w
  `Analysis History`. Discovery kandydatow issue, materialu i MR-ek dziala
  przez ograniczony fan-out, a `GET` z `/{jobId}` zwraca Jira progress,
  effective JQL, steps/activity, Delivery Units, aggregate, usage, visibility
  limits i report.
- `GET /elastic`
  Angularowy ekran `Tool Workbench / Elastic Logs` do recznego testowania
  helper endpointow Elastica oraz podgladu request/response JSON.
- `GET /gitlab`
  Angularowy ekran `Tool Workbench / GitLab Source` do recznego testowania
  helper endpointow GitLaba oraz podgladu request/response JSON. Grupa
  `Frontend Discovery` uruchamia read-only route graph Angular/Nx od jednego
  zweryfikowanego `provideRouter(...)` i source context wybranego `screenId`;
  pokazuje source revision, liczbe odwiedzonych route files/targeted reads,
  diagnostics, manifest plikow, technical signals i coverage bez uruchamiania
  AI ani rejestrowania nowego MCP toola. Nie listuje calego repository. Legacy route
  `GET /evidence`
  przekierowuje w Angularze do `/elastic`.
- `GET /jira`
  Angularowy ekran `Tool Workbench / Jira Source` do recznego pobierania
  materialu issue przez `POST /api/jira/issue/material` i podgladu
  request/response JSON.
- `GET /confluence`
  Angularowy ekran `Tool Workbench / Confluence Source` do recznego pobierania
  tekstu strony przez `POST /api/confluence/page/content`, wraz z
  rozpoznanym `pageId`, wersja i jawnymi ograniczeniami adaptera.
- `GET /config-drift-viewer-tools`
  Angularowy ekran `Tool Workbench / Config Drift Viewer`. Tworzy
  krotkozyjacy readonly preview pojedynczego scope'u i leniwie pokazuje source
  metadata, operatorski diff, mapping, anonimizacje, AI-safe prompt/artefakty
  oraz warunkowy DEEP scope bez uruchamiania AI albo tworzenia historii.
- `GET /database`
  Angularowy ekran `Tool Workbench / Database Tools` do recznego testowania
  Database tools przez shared/operator endpointy `/api/database/*`.
- `GET /operational-context`
  Angularowy ekran `Tool Workbench / Operational Context` dla curated
  operational context: katalogu systemow, repozytoriow, code-search scopes,
  procesow, integracji, bounded contexts, zespolow, glossary, handoff rules,
  validation findings i open questions. Bundled resources sa tylko seedem;
  widok utrzymuje lokalna kopie w `tdw-data/operational-context` i udostepnia
  Add/Edit/Delete dla dziewieciu typow YAML.
- `GET /workspace-settings`
  Angularowy ekran `Platform / Workspace Settings` do lokalnej customizacji
  workspace'u. Ekran pokazuje efektywne wartosci z `application.properties`
  oraz zrodlo kazdego pola; zapis trafia do
  `${tdw.workspace.directory}/settings.json`. Aktualny zakres obejmuje
  `app.ui.title`, lokalny token Copilota
  (`analysis.ai.copilot.auth.local.github-token`), podstawowe connection
  settings Jiry (`analysis.jira.base-url`, `analysis.jira.token`), Confluence
  (`analysis.confluence.base-url`, `analysis.confluence.token`), głównego
  GitLaba i named connection `runtime-config`, Elasticsearch i Dynatrace oraz
  sekrety tych integracji. `analysis.confluence.url-pattern` pozostaje
  deploymentowa allowlista adresow, a
  `analysis.confluence.max-text-characters` technicznym limitem odpowiedzi;
  zadne z tych dwoch pol nie jest eksponowane w Workspace Settings.
- `GET /ai-skills`
  Angularowy ekran `Platform / AI Skills`. Pokazuje status efektywnego
  katalogu runtime, wyszukiwanie i pomocnicze filtry workflow/responsibility.
  `GET /ai-skills/{skillName}` otwiera renderowany albo surowy `SKILL.md` pod
  bezposrednim URL-em, pozwala edytowac effective tresc i przywrocic packaged
  default. Lista i szczegol pokazuja stan `DEFAULT/CUSTOM` bez ujawniania
  lokalnej sciezki katalogu.
- `GET /api/ai/skills`
  Shared/operator lista zwalidowanych skilli z metadata, stanem
  `DEFAULT/CUSTOM` i dostepnoscia restore. `GET /api/ai/skills/{skillName}`
  zwraca body Markdown oraz dokladny `rawMarkdown`; `PUT` atomowo nadpisuje
  wskazany effective `SKILL.md`, a `POST .../restore-default` przywraca
  packaged wersje.
- `GET /api/analysis/jobs/input-options`
  Feature-owned endpoint dla UI startu analizy. Zwraca dostepne zrodla logow i
  powod blokady Elasticsearch, jezeli brakuje wymaganej konfiguracji
  Elasticsearch/Kibana.
- `GET /api/ui-explorer/input-options`
  Feature-owned katalog kwalifikujacych sie systemow
  `internal-service/frontend`, domyslnych trybow sekcji oraz dostepnosci
  screen catalog, source context i AI analysis.
- `GET /api/ui-explorer/screens?systemId={systemId}&branch={branch}`
  Rozwiazuje primary frontend repository oraz systemowy code-search scope z
  Operational Context, waliduje ref i zwraca business-friendly ekrany, source
  revision, `READY/PARTIAL/BLOCKED`, diagnostics oraz semantyczne graph
  coverage. Publiczny kontrakt nie ujawnia repository id/path, GitLab group ani
  project name. Katalog powstaje z jednego zweryfikowanego root routera i
  targeted traversal; liczba niepowiazanych plikow repozytorium nie zmienia
  wyniku.
- `POST /api/ui-explorer/jobs`
  Przyjmuje wybrany `systemId`, `branch`, katalogowy `screenId`, obowiazkowa
  `sourceRevision`, tryby sekcji, opis scenariusza i opcjonalne
  preferencje AI. Rewizja jest sprawdzana przed ekranem; zmieniony ref albo
  nieaktualny ekran zwraca konflikt wymagajacy odswiezenia katalogu. Start
  zwraca `202` ze snapshotem `QUEUED`; source context, preparation i analiza AI
  sa wykonywane asynchronicznie.
- `GET /api/ui-explorer/jobs/{jobId}`
  Zwraca kroki screen discovery/source context/AI preparation/AI, publiczne
  context sections z manifestem, sygnalami, coverage, diagnostics i hard
  boundary, bezpieczne metadane logical artifacts, dokladny `preparedPrompt`,
  tool evidence, activity, usage, source revision, result i report. Surowe
  pliki logical artifacts i wewnetrzny GitLab scope nie sa czescia odpowiedzi.
- `GET /api/ui-explorer/jobs/{jobId}/export`
  Zwraca sanitizowany `tdw.ui-explorer-export/v5` dla `COMPLETED/PARTIAL` z
  resultem i reportem. Potrafi odtworzyc portable payload z lokalnej historii
  po restarcie, ale nie ujawnia wewnetrznej koperty `run.json`.
- `POST /api/ui-explorer/imports`
  Waliduje dokladnie aktualny schema/version/payload/result contract, spojny
  screen i source revision, ponownie sanitizuje niezaufany dokument, sklada
  report od nowa, usuwa dostarczony `preparedPrompt` i zapisuje wynik pod nowym
  id w Analysis History. Import jest read-only, bez sesji Copilota,
  continuation, resume i migracji starszych wersji.
- `POST /api/analysis/jobs`
  Asynchroniczny start analizy wykorzystywany przez UI Angular. Request jest
  multipart/form-data i niesie `source`, opcjonalne preferencje wykonania AI
  (`model`, `reasoningEffort`) oraz:
  `correlationId` dla `source=ELASTICSEARCH` albo `logFile` dla
  `source=CSV_UPLOAD`.
- `GET /api/analysis/jobs/{analysisId}`
  Odczyt statusu, evidence, wyniku asynchronicznej analizy i historii
  follow-up chatu.
- `POST /api/analysis/jobs/{analysisId}/chat/messages`
  Asynchroniczne polecenie lub pytanie do AI po zakonczonej analizie. Backend
  reuse'uje evidence, wynik, historie rozmowy, model/reasoning oraz hidden
  scope tools z oryginalnego joba.
- `GET /analysis/runs`
  Shared/operator API lokalnej historii runow z lekkiego `index.json`. Lista
  niesie status ostatniego snapshotu i nie laduje pelnych `run.json`.
- `GET /analysis/runs/{analysisId}`
  Odczyt pelnego lokalnego `run.json`, uzywany przez ekran historii do
  odtworzenia formularza i ostatniego znanego stanu runu.
- `GET /analysis/ai/options`
  Shared/operator API z katalogiem modeli AI dla UI. Backend pobiera go z
  Copilot SDK i zwraca `reasoningEffort` tylko dla modeli, ktore SDK opisuje
  jako wspierajace te ustawienia. Endpoint nie jest krokiem incident job flow.
- `GET /api/ui/config`
  Shared/operator API konfiguracji UI. Gdy `app.ui.title` nie ma tekstu,
  frontend pokazuje tylko `Team Delivery Workspace`; gdy property jest
  ustawione, wartosc property jest tytulem, a `Team Delivery Workspace`
  podtytulem. Odpowiedz zawiera tez `defaultBranch` z wymaganego
  `platform.source-code.default-branch`; Flow Explorer i UI Explorer uzywaja
  go tylko do inicjalizacji pustego branch/ref i nie nadpisuja wyboru
  operatora ani wartosci odtworzonej z historii. Nie istnieje feature-specific
  config endpoint ani lokalny fallback do `main`.
- `GET /api/workspace/settings`
  Shared/operator API odczytu efektywnych ustawien workspace'u, wartosci bazowej
  z `application.properties`, lokalnego override'u i zrodla pola.
- `PUT /api/workspace/settings`
  Shared/operator API zapisu lokalnych override'ow do `settings.json`. Pusta
  wartosc albo wartosc identyczna z `application.properties` usuwa override.
  Endpoint nie wystawia flag SSL ani technicznych ustawien Confluence
  `analysis.confluence.url-pattern` i
  `analysis.confluence.max-text-characters`.
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
- `POST /api/gitlab/frontend/catalog`
  Shared/operator API graph-first katalogu tras i widokow Angular/Nx. Request
  zawiera repository/ref scope i opcjonalne path prefixes; limity rootow,
  wezlow, targeted reads, glebokosci i znakow sa narzucone po stronie backendu
  i nie sa inputem operatora. Wynik zawiera route nodes, edges, effective route
  chains, graph coverage i ref rozstrzygniety do commit id bez file metadata.
- `POST /api/gitlab/frontend/screen-reachability`
  Shared/operator API deterministycznego researchu wybranego `screenId`.
  Zaczyna od effective route chain i minimalnego selected screen seed, a dalej
  buduje iteracyjny BFS komponentow oraz deduplikowany rejestr faktycznie
  uzywanych serwisow, fasad, state i operacji backendowych. Zwraca czytelny
  outline, symbol slices, source references, research gaps i preflight liczby
  znakow; nie buduje pelnego snapshotu plikow ani repository inventory.
- `POST /api/gitlab/frontend/route-branch-slice`
  Zwraca tylko effective route branch wybranego ekranu wraz z wymaganymi
  importami i jawnymi markerami pominietych sibling routes.
- `POST /api/gitlab/frontend/typescript-symbol-slice`
  Zwraca osiagalne symbole, lokalne helpery, relewantne pola/importy, template
  bindings i downstream references dla wskazanego pliku TypeScript.
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
  `integrations.operationalcontext`, a nie incident job flow. Osobny kontrakt
  `/api/operational-context/catalog/*` wystawia capabilities, editable entity,
  create/complete-PUT, delete impact i RESTRICT delete. Mutacje zapisuja jedna
  lokalna kopie i podlegaja walidacji domenowej; nie maja ETagu, security gate
  ani wersjonowania katalogu.

## Glowny podzial pakietow

Szczegolowy diagram runtime/data-flow i compile-time importow jest w
`package-dependencies.md`.

- `pl.mkn.tdw`
  Glowna aplikacja Spring Boot.
- `pl.mkn.tdw.agenttools`
  Reusable tools/capability uzywane przez MCP wrappers i platforme AI, np.
  hidden tool context keys, nazwy tools oraz przenoszone wrappery MCP nad
  integracjami. Adaptery nie powinny importowac `agenttools`.
- `pl.mkn.tdw.common`
  Male helpery wspolne dla calej aplikacji, np. `JsonPayloadReader`.
- `pl.mkn.tdw.features.incidentanalysis.flow`
  Orkiestracja runtime analizy incydentu, response i listenery postepu flow.
- `pl.mkn.tdw.features.incidentanalysis.job`
  Asynchroniczny feature `POST /api/analysis/jobs`,
  `GET /api/analysis/jobs/{analysisId}` i
  `POST /api/analysis/jobs/{analysisId}/chat/messages`.
- `pl.mkn.tdw.features.incidentanalysis.job.api`
  Kontroler job API oraz request/response DTO dla UI.
- `pl.mkn.tdw.features.incidentanalysis.job.state`
  In-memory projekcja joba: statusy, kroki, chat messages, snapshot i listener
  mapujacy zdarzenia orkiestratora na stan joba.
- `pl.mkn.tdw.features.incidentanalysis.job.error`
  Wyjatki job API mapowane przez globalny handler bledow.
- `pl.mkn.tdw.api.aioptions`
  Shared/operator API dla katalogu modeli i endpointu
  `GET /analysis/ai/options`. Implementacja endpointu mapuje platformowy
  katalog modeli Copilota na obecne DTO aplikacji.
- `pl.mkn.tdw.api.aiskills`
  Shared/operator API effective katalogu runtime skilli. Mapuje neutralna
  projekcje z `aiplatform.copilot.runtime` na liste, szczegol, zapis i restore
  pojedynczego pliku bez ujawniania sciezek storage ani selekcji per feature.
- `pl.mkn.tdw.api.uiconfig`
  Shared/operator API runtime konfiguracji brandu UI dla Angulara. Nie jest
  czescia incident job flow.
- `pl.mkn.tdw.api.workspacesettings`
  Shared/operator API lokalnych ustawien workspace'u. Pakiet laczy
  `application.properties` z `localworkspace.settings`, pokazuje zrodlo
  wartosci dla UI i aplikuje efektywne override'y do runtime properties
  uzywanych przez brand UI, lokalny token Copilota oraz integracje GitLaba,
  Elasticsearch i Dynatrace.
- `pl.mkn.tdw.api.githubauth`
  Shared/operator API autoryzacji GitHub dla UI oraz backendowa operator
  session cookie. Ten pakiet zna request HTTP, ale nie przechowuje tokenow w
  frontendzie ani publicznych requestach joba.
- `pl.mkn.tdw.features.incidentanalysis.evidence`
  Deterministyczne zbieranie evidence przez providery i jawny opis krokow
  pipeline, z rownoleglym fan-outem Dynatrace + GitLab po deployment context.
- `pl.mkn.tdw.features.incidentanalysis.evidence.provider.deployment`
  Wyprowadzanie deployment context z logs jako osobny krok przed Dynatrace i GitLabem.
- `pl.mkn.tdw.features.incidentanalysis.ai.initial`
  Poczatkowa analiza incydentu: provider, request, preparation i JSON-only
  response z rozdzielonym `functionalAnalysis` oraz `technicalAnalysis`.
- `pl.mkn.tdw.features.incidentanalysis.ai.chat`
  Follow-up chat po zakonczonej analizie incydentu.
- `pl.mkn.tdw.shared.ai`
  Neutralne preferencje wykonania AI, non-secret `AnalysisAiAuthRef` oraz
  kontrakty token/cost/usage i visible activity trace dla flow, job UI i
  feature'ow.
- `pl.mkn.tdw.shared.evidence`
  Neutralny model evidence przekazywany miedzy evidence pipeline, flow, job UI
  i AI: `AnalysisEvidenceSection`, `AnalysisEvidenceItem`,
  `AnalysisEvidenceAttribute`; zawiera tez neutralny listener aktualizacji tool
  evidence przekazywany miedzy providerem AI, jobem i feature'em.
- `pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext`
  Enrichment katalogiem operacyjnym: sygnaly incydentu, matcher i mapper evidence.
- `pl.mkn.tdw.integrations.operationalcontext`
  Query-based adapter curated operational context catalog, niezalezny codec,
  classpath seed source, lokalny store biezacych dokumentow, captured snapshot,
  walidacja i neutralna logika maintenance do reuse'u przez API, evidence i
  kolejne capability.
- `pl.mkn.tdw.features.incidentanalysis.ai.copilot`
  Incidentowe initial/chat providery oraz budowanie promptu, artifact digestu,
  guidance do uzycia skilli, tool policy, response parser i initial/follow-up
  run assembly. Ten pakiet sklada parametry dla platformowego runtime Copilota.
- `pl.mkn.tdw.aiplatform.copilot.runtime`
  Neutralne elementy runtime SDK: properties, model listing, client options,
  persistent effective katalog skilli, jego seed/save/restore, `SessionConfig`, `MessageOptions`
  i prepared session bez znajomosci incident promptu ani incident policy.
  Opcjonalne feature-owned durable system instructions mapuje 1:1 na SDK
  `systemMessage` w trybie `APPEND` dla create i resume. Platforma nie buduje
  ich tresci ani nie interpretuje kontraktu feature'a.
- `pl.mkn.tdw.aiplatform.copilot.runtime.context`
  Neutralna polityka context tier. Dla preference `AUTO` estymuje initial
  prompt razem z durable system instructions, definicjami tools i rezerwa oraz
  ustawia `long_context` przed create/resume po przekroczeniu progu okna z
  dynamicznego katalogu modeli. Feature z goal-driven researchem moze przekazac
  `LONG_CONTEXT_REQUIRED`; wtedy platforma ustawia tier przed create/resume bez
  zaleznosci od kompletnosci katalogu i potwierdza go przez typed
  `session.model.getCurrent` przed pierwszym `sendAndWait`. Brak potwierdzenia
  zatrzymuje run przed wyslaniem promptu. Runtime nie probuje zmieniac tieru w
  trakcie aktywnej wiadomosci. Decyzje sa widoczne jako
  `AnalysisAiActivityEvent` kategorii `CONTEXT`.
- `pl.mkn.tdw.aiplatform.copilot.runtime.auth`
  Platformowe rozstrzyganie tokena Copilot tuz przed zbudowaniem
  `CopilotClientOptions`. Runtime zawsze przekazuje `githubToken` jawnie i
  ustawia `useLoggedInUser=false`.
- `pl.mkn.tdw.aiplatform.copilot.runtime.options`
  Platformowy provider cache'owanego katalogu modeli Copilota i neutralne DTO.
  Pobiera pelny typed wynik `models.list`, zachowujac reasoning metadata oraz
  billing/capability potrzebne do dynamicznego rozpoznania context tier.
  `api.aioptions` jest waska fasada mapujaca tylko pola UI na endpoint
  `GET /analysis/ai/options`.
- `pl.mkn.tdw.aiplatform.copilot.runtime.execution`
  Uruchamianie klienta Copilota, sesji, lifecycle logging oraz
  `CopilotExecutionResult` z trescia odpowiedzi i user-visible
  `AnalysisAiUsage`; session events SDK sa mapowane na neutralny
  `AnalysisAiActivityEvent`, bez wystawiania typow SDK do UI.
- `pl.mkn.tdw.aiplatform.copilot.tools.context`
  Budowanie hidden `ToolContext` i session-bound scope dla Spring tools jako
  neutralna mechanika platformy.
- `pl.mkn.tdw.aiplatform.copilot.tools`
  `CopilotToolInvocationHandler`, czyli neutralna granica wykonania Spring
  `ToolCallback`: policies, hidden context, eventy invocation, kontrolowany
  rejection i parsing wyniku dla SDK.
- `pl.mkn.tdw.aiplatform.copilot.tools.events`
  Wewnetrzne eventy tool invocation: `Started` oraz terminalny `Finished` z
  outcome `COMPLETED`, `REJECTED` albo `FAILED`.
- `pl.mkn.tdw.aiplatform.copilot.tools.policy`
  Neutralne kontrakty policy invocation, kontrolowany rejection oraz session
  validation.
- `pl.mkn.tdw.aiplatform.copilot.tools.policy.budget`
  Platformowa budget policy, state, registry, properties oraz neutralny
  kontrakt decyzji.
- `pl.mkn.tdw.aiplatform.copilot.tools.logging`
  Subskrypcja eventow invocation do operacyjnego logowania request/result.
- `pl.mkn.tdw.aiplatform.copilot.tools.description`
  Neutralny kontrakt customizacji opisow tools, wykonywany przez runtime
  factory bez wiedzy o semantyce konkretnego feature'a.
- `pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory`
  Platformowa rejestracja Spring tools jako definicji Copilota.
- `pl.mkn.tdw.aiplatform.copilot.tools.evidence`
  Session-bound store publikujacy neutralne `AnalysisEvidenceSection` z wynikow
  tool invocation przez sink przekazany przez feature.
- `pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools`
  Incident-specific subskrypcje eventow GitLab/Database tools i mapowanie
  wynikow do user-facing evidence.
- `pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.description`
  Incident-specific guidance doklejane do opisow GitLab/Database tools dla
  Copilota.
- `pl.mkn.tdw.integrations.elasticsearch`
  Properties, porty, adapter REST, modele logow oraz service search dla
  Elasticsearch/Kibana.
- `pl.mkn.tdw.api.elasticsearch`
  Shared/operator endpoint testowy `POST /api/elasticsearch/logs/search`
  delegujacy do integracji Elasticsearch.
- `pl.mkn.tdw.agenttools.elasticsearch.mcp`
  MCP tools Elastica delegujace do `integrations.elasticsearch`.
- `pl.mkn.tdw.integrations.database`
  Routing polaczen, metadata Oracle, readonly query execution i SQL guard DB
  capability.
- `pl.mkn.tdw.agenttools.database.mcp`
  Session-bound MCP tools diagnostyki danych delegujace do
  `pl.mkn.tdw.integrations.database`. Kontrakty
  request/result/scope i operatory DB mieszkaja przy integracji DB.
- `pl.mkn.tdw.integrations.dynatrace`
  Modele i adapter REST dla runtime signals Dynatrace
  (`entities`, `problems`, `metrics`).
- `pl.mkn.tdw.features.incidentanalysis.evidence.provider.dynatrace`
  Krok pipeline publikujacy runtime signals Dynatrace jako evidence.
- `pl.mkn.tdw.integrations.gitlab`
  Konfiguracja, porty, adapter REST oraz modele/search service GitLaba.
- `pl.mkn.tdw.integrations.github.auth`
  Integracja GitHub App OAuth: properties, klient exchange/refresh, profil
  uzytkownika, state store, zaszyfrowany authorization store i AES-GCM cipher.
- `pl.mkn.tdw.api.gitlab`
  Shared/operator endpoint repository search GitLaba delegujacy do integracji.
- `pl.mkn.tdw.api.gitlab.source`
  Shared/operator endpointy source resolve GitLaba:
  `POST /api/gitlab/source/resolve` i wariant preview.
- `pl.mkn.tdw.api.database`
  Shared/operator endpointy testowe nad `integrations.database.DatabaseToolService`.
  Controller buduje manualny `DbCapabilityScope` z operatorskiego
  `environment` i deleguje do typed DB capability.
- `pl.mkn.tdw.api.operationalcontext`
  Shared/operator endpointy i view service dla katalogu operational context.
  Pakiet mapuje reusable `integrations.operationalcontext` na DTO dla UI
  `/operational-context`, bez importowania incident flow.
- `pl.mkn.tdw.features.incidentanalysis.evidence.provider.gitlabdeterministic`
  Deterministic mapowanie logs i deployment context na code evidence z GitLaba.
- `pl.mkn.tdw.agenttools.gitlab.mcp`
  MCP tools GitLaba delegujace do `integrations.gitlab`.
- `pl.mkn.tdw.agenttools.gitlab.frontend.mcp`
  Neutralne frontendowe MCP tools dla route branch slice i TypeScript symbol
  slice. Model-facing input zawiera tylko `sliceRef` i `reason`;
  repository/ref/path/source revision i target symbolu sa ukrytym scope'em
  sesji. Pelny Screen Reachability zasila initial artifacts i nie jest toolem.
- `pl.mkn.tdw.integrations.gitlab.source`
  Osobny use case rozwiazywania pliku po symbolu.
- `pl.mkn.tdw.api`
  Obsluga bledow API, wspolny kontrakt walidacji i shared/operator API dla
  endpointow FE niezaleznych od jednego feature'a, np. fasady nad platforma
  albo integracjami. Endpointy konkretnego use case'u zostaja przy
  `features.<feature>.api`.
- `pl.mkn.tdw.ui`
  Cienki routing Spring MVC dla route'ow Angulara, np. `/elastic`, `/gitlab`
  i `/operational-context`.
- Zamkniety root `pl.mkn.tdw.analysis`
  Produkcyjny i testowy root `analysis.*` jest zamkniety. Publiczne URL-e
  moga nadal zawierac slowo `analysis`, ale nowe klasy Javy trafiaja do
  aktualnych wlascicieli: `features`, `api`, `integrations`, `agenttools`,
  `aiplatform`, `shared`, `common` albo `ui`.
- `frontend/`
  Workspace Angular z komponentami, serwisami i konfiguracja buildu UI.
- `src/main/resources/static`
  Wygenerowany produkcyjny bundle Angulara serwowany przez Spring Boot.

## Aktualny model UI

UI jest product-facing workspace'em `Team Delivery Workspace`, a nie juz
nawigacja wokol nazwy repo albo jednego feature'a. Widoczny tytul workspace'u
pochodzi z `GET /api/ui/config`: jezeli `app.ui.title` nie jest ustawione, UI
pokazuje tylko `Team Delivery Workspace`; jezeli jest ustawione, property jest
glownym tytulem, a `Team Delivery Workspace` podtytulem.
Workspace Settings moze nadpisac `app.ui.title` lokalnie w `settings.json`; po
zapisie `GET /api/ui/config` zwraca juz efektywna wartosc z workspace'u.
Ten sam endpoint przekazuje wymagany platformowy `defaultBranch` dla ekranow
pracujacych ze zrodlem kodu. Jego source of truth pozostaje
`application.properties`, poza zakresem lokalnego Workspace Settings.

Shell Angulara ma:

- lewy sidebar jako glowna nawigacje,
- zwijany rail ikonowy o stalej szerokosci dla pracy na szerszym ekranie,
- kontekstowy topbar z breadcrumbem i tytulem aktualnego widoku,
- ikone info w topbarze dla skompresowanego `capabilityInfo` ekranow
  Workbench,
- jasny motyw oparty o tokeny CSS i przygotowany do przyszlych wariantow.

Znaczenie grup UI:

- `Analysis Features` - pionowe feature'y produktowe. `Incident Analysis` jest
  pierwszym dostepnym feature'em; Flow Explorer i Change Verification sa
  kolejnymi dostepnymi feature'ami, podobnie jak Config Drift Viewer i
  Delivery Complexity Assessment, a Data Diagnostics pozostaje placeholderem
  dla przyszlego feature'a.
- `Tool Workbench` - zaplecze operatorskie reusable capability. Elastic,
  GitLab, Jira, Confluence, Database i Operational Context sa
  analysis-independent i nie eksponuja incidentowego `analysisRunId`;
  DB/GitLab scope dla AI pozostaje feature-owned hidden `ToolContext`.
- `Platform` - overview, konfiguracja i podglad zasobow samego Team Delivery
  Workspace: workspace settings, editable AI Skills, personalizacja,
  autentykacja i modele AI. Pozycje bez dedykowanych widokow pozostaja
  disabled placeholders.

## Aktualny model runtime

- Elasticsearch dziala przez rzeczywisty adapter REST do Kibana proxy.
- Elastic log evidence provider ma dwa wejscia: REST search po `correlationId`
  albo wczesniej sparsowane wpisy z uploadu CSV. W obu przypadkach publikuje
  te sama sekcje `elasticsearch/logs`, zeby deployment context, Dynatrace,
  GitLab deterministic, operational context i AI prompt nie rozgalezialy sie po
  zrodle logow.
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
- Frontendowe GitLab MCP tools mieszkaja w
  `agenttools.gitlab.frontend.mcp`, deleguja do neutralnego graph-first
  discovery i nie ujawniaja modelowi repository coordinates. Nie zwracaja
  ponownie pelnego Screen Reachability obecnego juz w initial promptcie;
  route branch slice obejmuje poddrzewo wybranego kontenera przez jeden
  session-bound safe ref.
- GitLab MCP tools potrafia nie tylko szukac kandydatow repo i flow contextu,
  ale tez znajdowac referencje/importy dla ugruntowanej klasy, zeby lepiej
  naprowadzac DB diagnostics.
- Database diagnostics sa osobna, opcjonalna capability AI-guided i nie sa
  evidence providerem.
- Operational context jest osobnym enrichment stepem nad juz zebranym evidence.
- Bazowy curated operational context jest ladowany przez osobny adapter, a nie
  bezposrednio przez sam provider enrichmentu.
- Operational context publikuje dla dopasowanego systemu jawny code search
  scope: repozytoria/projekty, role, priorytety, `reason` i `readFor`, zeby
  Copilot traktowal repo glowne oraz powiazane repozytoria pomocnicze jako
  wspolny scope kodu tego systemu. Konkretne klasy, endpointy i sciezki kodu
  sa odkrywane przez GitLab tools, nie utrzymywane w katalogu.
- Job flow reuse'uje orchestration warstwe `AnalysisOrchestrator`.
- Job flow moze przekazac do generycznego requestu AI opcjonalny wybor
  modelu i `reasoningEffort`; nie zmienia to evidence scope'u, branchy,
  srodowiska ani GitLab group.
- Follow-up chat jest kontynuacja zakonczonego joba, a nie nowym publicznym
  requestem analizy. Initial analysis tworzy sesje AI, a follow-up wznawia ja
  po zapisanym `copilotSessionId`, przekazuje jako prompt tylko tresc
  wiadomosci operatora oraz ponownie podpina session-bound tools w zakresie
  rozwiazanym przez pierwotna analize.
- Lista modeli i dostepnych `reasoningEffort` dla UI pochodzi z platformowego
  provider'a opcji Copilota przez backendowy shared/operator endpoint opcji AI.
  Frontend nie jest source of truth dla mozliwosci modeli.
- Runtime AI providerem jest GitHub Copilot SDK.
- Lifecycle procesu Copilot CLI jest wspolna odpowiedzialnoscia
  `aiplatform.copilot`. Na Windows runtime rozwiazuje CLI do bezwzglednego
  pliku `.exe` z working directory, `PATH` albo lokalnej instalacji WinGet, aby
  SDK kontrolowalo rzeczywisty proces zamiast shell wrappera,
  a kazdy klient konczy ograniczonym czasowo `stop()` z fallbackiem
  `forceStop()`, rowniez po bledzie startu.
- Elasticsearch tools dla Copilota sa wystawiane tylko wtedy, gdy efektywna
  konfiguracja Elasticsearch/Kibana jest kompletna. Brak tej konfiguracji
  blokuje tools `elastic_*` w initial analysis i follow-up chat niezaleznie od
  coverage gaps.
- Zuzycie tokenow jest zbierane z eventow sesji Copilota i wystawiane do UI
  jako generyczne `shared.ai.AnalysisAiUsage`, bez typow SDK w kontrakcie
  frontendu.
  Frontend liczy orientacyjne GitHub AI Credits/USD z tokenow i modelu jako
  product-facing estymacje oplacalnosci, nie jako fakture.
- Aktywnosc sesji Copilota jest productized i widoczna w job state jako
  generyczne `shared.ai.AnalysisAiActivityEvent`: turny, komunikaty,
  wywolania tooli, snapshoty context tokens/messages i usage eventy. Frontend
  merge'uje te eventy z `toolEvidenceSections` w jeden timeline analizy.
- Wszystkie skille Copilota sa pakowane jako immutable seed. Przy starcie
  loader dopisuje tylko brakujace pliki do persistent effective katalogu
  `${analysis.ai.copilot.copilot-home}/skills`, domyslnie
  `tdw-data/copilot/skills`, bez nadpisywania istniejacej tresci. Kazda nowa i
  wznawiana sesja dostaje ten sam root; feature wskazuje workflow w prompcie.
  Loader utrzymuje immutable snapshot metadata i Markdown, oblicza
  `DEFAULT/CUSTOM` oraz atomowo publikuje zapis lub restore jednego skilla.
- Frontend Angular jest buildowany w tym samym repo i serwowany z tego samego
  JAR-a jako statyczne zasoby.

## Najwazniejszy przeplyw

```mermaid
flowchart LR
    A["GET /"] --> HOME["Workspace overview landing"]
    HOME --> IA["GET /incident-analysis"]
    IA --> B["Angular bundle from static resources"]
    B --> U["GET /analysis/ai/options"]
    B --> IO["GET /api/analysis/jobs/input-options"]
    B --> C["POST /api/analysis/jobs\nsource=ELASTICSEARCH|CSV_UPLOAD"]
    C --> D["AnalysisJobFacade"]
    D --> LOGINPUT["Resolve log input\nElastic REST or CSV import"]
    LOGINPUT --> E["Background analysis task"]
    E --> F["AnalysisOrchestrator"]
    F --> G["AnalysisEvidenceCollector"]
    G --> H["Elastic log evidence provider\nREST or uploaded CSV"]
    G --> I["Deployment context evidence provider"]
    G --> J["Dynatrace evidence provider"]
    G --> K["GitLab deterministic evidence provider"]
    G --> OPCTX["Operational context evidence provider"]
    OPCTX --> M["AnalysisContext"]
    F --> N["InitialAnalysisProvider"]
    N --> O["Copilot SDK"]
    O --> P["Elastic tools\noptional and config-gated"]
    O --> R["GitLab tools (optional during session)"]
    O --> Q["Database tools (optional during session)"]
    N --> S["AnalysisResultResponse"]
    B --> T["GET /api/analysis/jobs/{analysisId}"]
    T --> D
    B --> U2["POST /api/analysis/jobs/{analysisId}/chat/messages"]
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

Ten helper nie jest wymagany dla uploadu CSV. Upload CSV jest alternatywnym
wejsciem do incident analysis i po walidacji zasila ten sam model logow, z
ktorego korzysta `elasticsearch/logs`.

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

## Dodatkowy use case GitLab endpoint discovery

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

Layout Workbench jest celowo dwustrefowy: lewy panel zawiera wspolny scope i
liste elementow do testu, a glowna przestrzen pokazuje formularz wybranego
elementu oraz wynik pod formularzem dopiero po wykonaniu requestu. Nie uzywamy
stalego trzykolumnowego ukladu dla response, bo wyniki JSON potrafia byc
szerokie i zlozone. `Request preview` jest zwijalny i po otrzymaniu odpowiedzi
domyslnie mniej dominujacy niz `JSON response`.

## Dodatkowy use case Operational Context workbench

To jest operator-facing flow utrzymaniowy w `Tool Workbench` dla reusable
katalogu systemow:

1. frontend route `/operational-context` pobiera dane z
   `/api/operational-context/*`,
2. backendowa fasada w `api.operationalcontext` deleguje do
   `integrations.operationalcontext`,
3. UI pobiera metadane gotowosci maintenance lokalnej kopii,
4. UI pokazuje summary, signal resolver, listy encji, validation findings,
   open questions i szczegoly encji oraz utrzymuje dziewiec typow YAML przez
   editor drawer i delete impact,
5. ten sam captured snapshot katalogu jest reuse'owany przez incident evidence provider,
   `opctx_*` tools, GitLab repository discovery i przyszle feature'y.

To nie jest osobny krok incident job flow. To shared/operator powierzchnia do
utrzymania jakosci katalogu, ktory ma byc reusable poza analiza incydentow.

UI `/operational-context` pokazuje kompaktowy status katalogu, zakladki
katalogowe, Signal Resolver, listy encji, inbox `Validation`, inbox
`Open Questions` oraz prawy detail drawer. Drawer ma stale akcje `Copy`,
`Open raw` i `Close`; dla wspieranych typow dodaje `Edit` i `Delete`, a Add jest
dostepne tylko na ich zakladkach. Szczegoly encji i raw preview nie
powinny byc modalem blokujacym prace. Dialog delete jest blokujacy, pokazuje
inbound references i nie oferuje cascade.

`src/main/resources/operational-context` jest bundled, immutable seedem.
Pierwszy start kopiuje go do `tdw-data/operational-context`; wszystkie kolejne
odczyty i mutacje korzystaja tylko z tej lokalnej kopii. Store nie ma trybow,
rewizji, manifestow, historii ani rollbacku. Zapis podmienia atomowo jeden
zmieniany dokument po walidacji domenowej.

