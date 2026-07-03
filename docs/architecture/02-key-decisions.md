# Key Decisions

Ten dokument zbiera decyzje architektoniczne, ktore sa wazne przy utrzymaniu
platformy AI-augmented system analysis oraz pierwszego feature'a, czyli
analizy incydentu.

## 0. Produkt jest platforma, incident analysis jest pierwszym feature'em

Repo wystartowalo jako incident tracker, ale docelowy produkt jest szerszy:
ma byc platforma do AI-augmented system analysis. Incydent po `correlationId`
jest pierwszym pionowym feature'em, ktory dowodzi integracji, tools, Copilot
runtime i operator workflow.

Konsekwencje:

- `features.incidentanalysis` nie jest generycznym core dla kolejnych analiz,
- `aiplatform`, `agenttools`, `integrations`, `shared`, `common` i
  shared/operator `api.*` musza pozostac reusable poza incydentami,
- kolejne feature'y, np. flow explorer, functional logic explorer albo
  natural-language data diagnostics, maja dostarczac wlasny request/result,
  prompt, skille, tool policy, hidden context i UI/API,
- publiczne URL-e moga historycznie zawierac `analysis`, ale pakiety Javy
  maja odzwierciedlac ownership warstw,
- gdy nowa potrzeba wyglada "wspolnie", najpierw trzeba ustalic, czy to
  naprawde platform mechanics, reusable capability, shared/operator API czy
  tylko logika konkretnego feature'a.

## 0a. UI jest Team Delivery Workspace

Product-facing UI nazywa workspace `Team Delivery Workspace`. Technicznie repo
nadal rozwija platforme do AI-augmented system analysis, ale UI nie powinien
brzmiec jak narzedzie tylko dla analitykow ani jak pojedynczy incident tracker.
Ma wspierac caly zespol wytworczy i sposob pracy oparty o skills/capabilities,
a nie role-based organization.

Decyzje:

- `app.ui.title` parametryzuje glowny tytul UI.
- `app.ui.title` moze byc lokalnie nadpisane w Workspace Settings; override z
  `${tdw.workspace.directory}/settings.json` ma pierwszenstwo przed
  `application.properties`.
- Gdy `app.ui.title` nie ma tekstu, frontend pokazuje tylko
  `Team Delivery Workspace`.
- Gdy `app.ui.title` jest ustawione, jego wartosc jest tytulem, a
  `Team Delivery Workspace` podtytulem.
- Glowna nawigacja jest w lewym sidebarze, a topbar pozostaje kontekstowy.
- Sidebar ma grupy `Analysis Features`, `Tool Workbench` i `Platform`.
- `GET /` jest `Platform / Team Delivery Workspace`: startowym overview
  workspace'u z szybkim wejsciem do aktywnych feature'ow. Tresc jest
  customer-centric i tlumaczy oszczednosc czasu, nie mechanike AI/tools.
- `Analysis Features` zawiera dedykowane feature'y produktowe; na teraz
  aktywne sa `Incident Analysis` pod `/incident-analysis` oraz
  `Flow Explorer` pod `/flow-explorer`.
- `Tool Workbench` zawiera reusable capability do testow i debugowania:
  `Elastic Logs`, `GitLab Source`, `Database Tools` i `Operational Context`.
- `Operational Context` pozostaje w `Tool Workbench` jako context/catalog
  capability, a nie jako element sekcji `Platform`.
- `Platform` dotyczy overview i customizacji Team Delivery Workspace:
  workspace settings, personalizacji, autentykacji, konfiguracji modeli i
  podobnych ustawien.
- Zakres Workspace Settings obejmuje `app.ui.title`, podstawowe connection
  settings GitLaba (`analysis.gitlab.base-url`, `analysis.gitlab.group`,
  `analysis.gitlab.token`), Elasticsearch (`analysis.elasticsearch.base-url`,
  `analysis.elasticsearch.kibana-space-id`,
  `analysis.elasticsearch.index-pattern`,
  `analysis.elasticsearch.authorization-header`) oraz Dynatrace
  (`analysis.dynatrace.base-url`, `analysis.dynatrace.api-token`) oraz lokalny
  token Copilota (`analysis.ai.copilot.auth.local.github-token`). MVP nie
  wystawia `analysis.ai.copilot.auth.mode`,
  `analysis.ai.copilot.auth.local.display-name`, flag SSL ani technicznych
  limitow odpowiedzi integracji.

Konsekwencje UI:

- Nie dodajemy marketingowych hero do narzedzi codziennej pracy.
- Workspace overview moze opisywac platforme, ale ma byc action-oriented,
  spokojny, przyjazny dla osob nietechnicznych i zgodny z roboczym charakterem
  UI. Nie opisuje tools, promptow, runtime ani integracji pod spodem.
- Jeden ekran ma miec jeden dominujacy primary action.
- Workbench nie dostaje stalego trzykolumnowego layoutu dla response; wynik
  toola jest renderowany szeroko pod formularzem.
- Workbench endpointy i payloady pozostaja analysis-independent. Nie eksponuja
  `analysisRunId` ani incidentowego session scope'u.
- Statyczny opis capability w Workbench mieszka w topbarze pod ikona info, a
  nie w lokalnych `workbench-header` cards.
- Jasny motyw jest defaultem V1; tokeny CSS maja pozwalac na przyszle style.

## 1. Publiczny request analizy pozostaje minimalny

`POST /analysis/jobs` jest publicznym startem analizy. Przyjmuje
`correlationId` oraz opcjonalne preferencje wykonania AI: `model` i
`reasoningEffort`.

Lista dostepnych modeli dla UI pochodzi z shared/operator endpointu
`GET /analysis/ai/options`. Endpoint mapuje metadane Copilot SDK na generyczny
kontrakt aplikacji i zwraca `reasoningEffort` tylko tam, gdzie SDK wystawia
support albo domyslna wartosc dla danego modelu.

Runtime nie przywraca `branch`, `environment`, `gitLabGroup` ani innych pol
sterujacych evidence scope'em do publicznego requestu.

Konsekwencje:

- `environment` jest wyprowadzany z evidence, przede wszystkim z logow
  Elasticsearch i deployment context.
- `gitLabBranch` jest wyprowadzany z evidence deployment/runtime.
- `gitLabGroup` pochodzi z konfiguracji aplikacji.
- uzytkownik nie moze recznie przesterowac zakresu GitLaba albo DB przez
  publiczne API analizy.
- wybor modelu i `reasoningEffort` dotyczy tylko konfiguracji sesji AI, nie
  zmienia deterministycznie zbieranego evidence ani ukrytych scope'ow tools.
- frontend nie hardcoduje mozliwosci modeli; backend pozostaje source of truth
  i moze uzyc fallbacku do skonfigurowanych domyslow, gdy SDK chwilowo nie
  zwroci katalogu.

## 1a. Copilot authentication ma dwa tryby

Copilot authentication has two modes:

- `LOCAL_TOKEN` for local/dev runs, using a configured GitHub token from
  `analysis.ai.copilot.auth.local.github-token` albo `COPILOT_GITHUB_TOKEN`.
- `GITHUB_APP` for operator-facing runs, using a GitHub App user access token
  zwiazany z backendowa operator session cookie.

Public analysis and chat requests never carry GitHub tokens or OAuth codes.
The job flow carries only a non-secret AI auth reference. The actual token is
resolved inside `aiplatform.copilot.runtime.auth` immediately before
`CopilotClientOptions` are created.

`CopilotClientOptions` must always receive `githubToken` explicitly and
`useLoggedInUser=false`, so the backend never falls back to locally cached CLI
credentials. GitHub App installation tokens are not used for Copilot SDK,
because Copilot usage should belong to the GitHub user account in
operator-facing mode.

Konsekwencje:

- frontend pobiera status przez `GET /api/auth/github/status` zanim pobierze
  `GET /analysis/ai/options`,
- `GET /analysis/ai/options`, `POST /analysis/jobs` i follow-up chat sa
  auth-aware, ale ich publiczne payloady pozostaja minimalne,
- GitHub App access/refresh tokens pozostaja po stronie backendu, w store sa
  zaszyfrowane, a refresh token rotation jest zapisywana atomowo,
- missing local token, missing GitHub auth i reauth sa kontrolowanymi bledami
  API, nie fallbackiem do lokalnie zalogowanego uzytkownika.

## 2. Flow pozostaje AI-first

Evidence pipeline zbiera deterministyczny material, ale diagnoza i
rekomendacja sa wynikiem providera AI. Nie przenosimy diagnozowania do
centralnego rule engine.

Heurystyki sa dozwolone tylko jako:

- deterministyczne wzbogacanie `AnalysisContext`,
- ocena coverage i luk evidence,
- polityka dostepu do tools,
- walidacja shape odpowiedzi AI,
- logowanie i audyt evidence widoczny dla operatora.

Heurystyki nie powinny zastapic modelu w budowaniu diagnozy biznesowej.

## 3. Evidence pipeline jest deterministyczny na `AnalysisContext`

Kolejne kroki evidence providerow czytaja i aktualizuja `AnalysisContext`.
Po resolved deployment context kroki Dynatrace i GitLab deterministic moga
dzialac rownolegle z tego samego snapshotu contextu.

Provider evidence zwraca `shared.evidence.AnalysisEvidenceSection`. AI layer
nie powinien czytac DTO adapterow bezposrednio.

## 4. GitLab ma trzy osobne capability

GitLab w systemie nie jest jedna abstrakcja:

- adapter i source resolve do ogolnego dostepu do GitLaba,
- deterministic evidence provider do deployment context/code references,
- AI-guided tools do dociagania kodu w sesji Copilota.

Te role nie powinny byc mieszane. Deterministic evidence ma przygotowac
najlepszy snapshot przed AI, a tools sa tylko do uzupelniania luk.

## 5. Skills Copilota sa runtime resource

Skille Copilota sa pakowane z aplikacja z `src/main/resources/copilot/skills`.
Nie traktujemy ich jako plikow `.github` repozytorium hosta.

Skill przechowuje stale zasady pracy modelu. Dane konkretnego incydentu
niesie prompt i artefakty przygotowane w runtime.

## 6. Granica AI pozostaje generyczna

Kontrakt wejscia do AI to `InitialAnalysisRequest` i lista neutralnych
`shared.evidence.AnalysisEvidenceSection`. Prompt builder i provider AI nie
przyjmuja klas adapter-specific.

Jesli AI layer potrzebuje typowego widoku evidence, powinien uzyc helperow
widoku nad generycznymi `AnalysisEvidenceSection`, np. widokow dla logow,
runtime signals albo resolved code evidence.

## 7. Prepared analysis jest budowane raz

Orchestrator nie buduje juz promptu debugowego osobno od requestu
wykonywanego przez AI.

Aktualny flow:

1. orchestrator buduje `InitialAnalysisRequest`,
2. wywoluje `InitialAnalysisProvider.prepare(request)`,
3. zapisuje `prepared.prompt()` w stanie joba,
4. wykonuje `InitialAnalysisProvider.analyze(prepared, listener)`,
5. zamyka `InitialAnalysisPreparation` w `finally`/try-with-resources.

Ownership prepared analysis jest jawny:

- wlasciciel obiektu zwroconego z `prepare(request)` zamyka go po uzyciu,
- `analyze(prepared, listener)` nie zamyka prepared analysis przekazanego
  przez caller,
- gateway wykonujacy SDK nie przejmuje ownership i nie zamyka przygotowanej
  sesji.

W Copilocie sa trzy jawne poziomy:

- `CopilotInitialAnalysisPreparation` implementuje initial-facing
  `InitialAnalysisPreparation` i niesie `InitialAnalysisRequest`,
- `CopilotRunRequest` jest platformowym inputem wykonania. Niesie neutralny
  `runReference`, prompt, parametry sesji, logiczne artefakty i evidence sink,
  czyli to, co feature przekazuje do runtime,
- `CopilotPreparedSession` jest neutralnym technicznym obiektem wykonania SDK,
  uzywanym przez execution gateway oraz follow-up chat. Powstaje z
  `CopilotRunRequest`; incident analysis moze mapowac `correlationId` na
  `runReference`, ale runtime nie traktuje `correlationId` jako wlasnego pola.

Follow-up chat nie implementuje ani nie reuse'uje `InitialAnalysisPreparation`.

`InitialAnalysisProvider` nie ma produkcyjnych shortcutow dodanych tylko dla
testow, takich jak `analyze(request)`, oddzielne `preparePrompt(...)` albo
domyslne prepared adaptery. Testy tworza wlasne prepared fixtures.

## 8. Artefakty Copilota sa inline w promptcie

Aktualny runtime nie uzywa SDK attachments jako zrodla evidence. Artefakty
incydentu sa renderowane jako logiczne pliki i osadzane inline w promptcie.
`MessageOptions` dostaje finalny prompt przez `setPrompt(prompt)`.

Nie zakladamy lokalnych sciezek plikowych dla artefaktow. Zmiana delivery
mode na SDK attachments bylaby jawna zmiana runtime wymagajaca testow,
dokumentacji i planu rollbacku.

## 9. Manifest i digest sa pierwszymi artefaktami

Kolejnosc artefaktow Copilota zaczyna sie od:

1. `00-incident-manifest.json`
2. `01-incident-digest.md`
3. artefakty raw evidence

Manifest zawiera indeks artefaktow, polityke tools, coverage report i
deklaruje `deliveryMode=embedded-prompt`. Digest kompresuje najwazniejsze
fakty sesji, logi, deployment/runtime, code highlights i znane luki evidence.

`AnalysisEvidenceItem` nie dostal publicznego pola `itemId`. Stabilne
`itemId` sa generowane tylko podczas renderowania artefaktow Copilota i
pojawiaja sie w manifest, JSON artifacts i markdown artifacts.

## 10. Initial result jest report-first

Kanonicznym wynikiem initial analysis jest generyczny `AnalysisReport` z
`shared.ai.report`, a nie finalna tresc odpowiedzi tekstowej Copilota.
Backend tworzy `reportId` przy skladaniu runu, przekazuje scaffold raportu do
`CopilotRunRequest.initialReport`, a runtime rejestruje go w
`CopilotReportSessionStore` na czas pojedynczego `sendAndWait`.

Model zapisuje wynik przez platformowe report tools:

- `report_update_header`,
- `report_upsert_section`,
- `report_update_meta`,
- `report_get_current`.

Report tools sa session-bound. Model-facing schema nie przyjmuje `reportId`;
scope pochodzi z hidden `ToolContext`, razem z feature name i lista
dozwolonych sekcji. Tool odrzuca sekcje spoza `allowedReportSectionIds`.

Po zakonczeniu `sendAndWait` execution gateway zwraca ostatni snapshot raportu
w `CopilotExecutionResult.report()`. Feature mapuje ten raport na swoj
publiczny kontrakt:

- Incident Analysis mapuje `header` na `detectedProblem`, sekcje
  `FUNCTIONAL_ANALYSIS` i `TECHNICAL_HANDOFF` na obecne pola
  `functionalAnalysis` i `technicalAnalysis`, a meta references na affected
  process/bounded context/team oraz visibility limits.
- Flow Explorer mapuje `OVERVIEW` i aktywne sekcje raportu na
  `FlowExplorerAiResponse`, zachowujac feature-specific widok dla UI.

JSON-only response contract pozostaje tylko fallbackiem diagnostycznym, gdy
raport nie zostal zapisany albo jest niekompletny. Legacy labeled response
parser pozostaje usuniety. Finalna proza z `sendAndWait` moze byc przydatna w
diagnostyce, ale nie jest zrodlem prawdy initial result.

`AnalysisReport` jest neutralny i nie zna semantyki incydentu ani Flow
Explorera. Feature decyduje o dozwolonych sekcjach, required sections,
promptach, skillach i mapowaniu raportu na publiczny response. W MVP nie
wersjonujemy raportu; job state, local workspace i export trzymaja ostatni
snapshot raportu obok feature-specific `result`.

`functionalAnalysis` nadal jest pisane dla analityka biznesowo-systemowego i
musi uzywac operational context do osadzenia incydentu w systemie, procesie,
bounded context, logice funkcjonalnej i regule handoffu. `technicalAnalysis`
nadal jest Technical Handoff v1 dla osoby lub zespolu, ktory ma problem
naprawic, zweryfikowac albo przekazac do innego Tribe/administracji/infra.

## 11. Ukryty quality gate jest usuniety

Nie utrzymujemy obecnie niewidocznego dla uzytkownika quality gate po
parsingu odpowiedzi. Jakosc odpowiedzi jest egzekwowana przez prompt, JSON
schema, parser/fallback i testy kontraktu. Jesli walidacja jakosci ma wrocic,
powinna byc jawna dla operatora albo realnie zmieniac runtime, np. przez
repair/retry z testami i dokumentacja.

## 12. Tool policy jest coverage-aware

Nie uzywamy juz zasady "sekcja GitLab/Elasticsearch istnieje, wiec wylacz
tools". `CopilotIncidentEvidenceCoverageEvaluator` ocenia coverage generycznych
evidence i tworzy `CopilotIncidentEvidenceCoverageReport`.

`CopilotIncidentToolAccessPolicyFactory` jest jedynym produkcyjnym miejscem, ktore
laczy request, evaluator coverage i zarejestrowane tool definitions. Sama
`CopilotIncidentToolAccessPolicy` jest budowana z gotowego coverage reportu i nie
tworzy recznie nowego evaluatora.

Polityka:

- Elasticsearch tools sa wlaczane przy braku logow, truncation albo braku
  stacktrace.
- GitLab tools sa wlaczane przy braku code evidence albo gdy jest tylko
  symbol, stack frame, failing method lub brakuje flow context.
- Przy resolved GitLab scope coverage dodaje luke
  `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED`; wtedy model ma wykonac focused
  przeszukanie GitLaba przez tools, zeby `technicalAnalysis` bylo konkretne
  na poziomie wejscia, przeplywu, miejsca przerwania i rekomendowanej poprawki.
- Operational Context tools sa wlaczane dla luki
  `FUNCTIONAL_CONTEXT_GROUNDING_RECOMMENDED`, zeby `functionalAnalysis` nie bylo
  oderwane od katalogu systemow, procesow, bounded contextow, integracji,
  glossary i reguly handoffu.
- Gdy GitLab zna projekt/plik, zostaje ograniczony focused toolset.
- Przy DB-related symptomach coverage moze dodac luke
  `DB_CODE_GROUNDING_NEEDED`. Wtedy focused GitLab tools pozostaja dostepne do
  proby ugruntowania encji, repozytorium, tabel i relacji przed DB discovery,
  nawet jesli ogolny flow context z GitLaba wyglada na wystarczajacy.
- `gitlab_list_available_repositories` jest lekkim discovery tool nad
  operational context. Pozostaje dostepny razem z focused GitLab tools, zeby
  model mogl odnalezc `projectName`/`gitLabPath` repozytorium po aliasie,
  systemie, bounded context, pakiecie, endpointcie albo module, zanim uzyje
  search/read tools.
- `gitlab_list_available_repositories` zwraca tez `codeSearchScopes` z
  `code-search-scopes.yml`: semantic target, role, priorytety i `projectName`
  repozytoriow, ktore nalezy przeszukiwac razem dla dopasowanego zakresu.
- DB tools sa wlaczane tylko przy resolved environment i
  `IncidentDataDiagnosticNeed=LIKELY/REQUIRED`.
- Dla `POSSIBLE` dostepne sa tylko discovery tools.
- `db_execute_readonly_sql` pozostaje domyslnie zablokowany przez tool policy.

Coverage i luki evidence sa widoczne w manifest/prompt.

## 13. Tool budget jest egzekwowany w backendzie

Budzet tools jest session-bound i dziala jako generyczna
`CopilotToolInvocationPolicy` uruchamiana przez `CopilotToolInvocationHandler`
przed i po wywolaniu callbacka. Handler nie zna szczegolow budzetu ani
payloadu odmowy.

Domyslnie `analysis.ai.copilot.tool-budget.mode=soft`, czyli przekroczenia sa
logowane w backendzie, ale tool call nie jest blokowany. Tryb
`hard` zwraca kontrolowany wynik `denied_by_tool_budget`, zamiast zabijac cala
sesje wyjatkiem. Technicznie `CopilotToolBudgetPolicy` rzuca kontrolowany
`CopilotToolInvocationRejectedException`, a handler zamienia go na wynik dla
SDK i event terminalny `REJECTED`.

Budget policy mieszka w `aiplatform.copilot.tools.policy.budget` i utrzymuje
session-bound state tylko na czas sesji Copilota. Walidacja session id jest
takim samym mechanizmem policy w
`aiplatform.copilot.tools.policy.session`, dzieki czemu handler nie ma
osobnych warunkow dla konkretnych regul runtime.

Budzet rozroznia m.in. total calls, grupy Elastic/GitLab/DB, GitLab search,
read file/chunk, returned characters oraz raw SQL attempts.

## 14. Tools sa session-bound i ukrywaja scope

Docelowo wszystkie integracyjne tools powinny dostawac scope przez ukryty
`ToolContext`. Model nie powinien podawac `correlationId`, `gitLabGroup`,
`gitLabBranch` ani `environment` jako jawnych argumentow dla tych scope'ow.

Stan kodu na dzisiaj: GitLab i DB spelniaja ten invariant; Elastic MCP tool
nadal ma jawny parametr `correlationId`. To jest znany drift implementacyjny,
nie nowy kontrakt do rozszerzania.

SessionConfig ma jawna allowliste tools, a `SessionHooks.onPreToolUse`
blokuje lokalny workspace/filesystem/shell/terminal w glownym flow analizy.
Incident preparation sklada `CopilotSessionConfigRequest`: wybiera allowed
tools, skill directories, model options i incidentowy komunikat odmowy toola.
Incident preparation sklada tez `CopilotToolSessionContext`: tworzy
`analysis-*`/`analysis-chat-*` session id i hidden tool context ze scope'u
incydentu.
Initial i follow-up tool policy powstaja przez `CopilotIncidentToolAccessPolicyFactory`,
zeby decyzje o dostepnych capability byly lokalne dla incident preparation.
Follow-up nie buduje juz requestu artefaktow ani pelnego promptu
kontynuacyjnego; `CopilotIncidentFollowUpRunAssembler` wymaga
`copilotSessionId`, wybiera `sessionTarget=EXISTING` i wysyla sama wiadomosc
operatora.
`CopilotIncidentRunRequestFactory` sklada finalny `CopilotRunRequest`, zeby
mapowanie artifact contents na platformowy input runtime bylo w jednym miejscu.
`CopilotSessionConfigFactory` jest juz tylko runtime factory, ktora zamienia
ten request na konfiguracje klienta SDK, `SessionConfig`,
`ResumeSessionConfig`, hooks, permission handler i disabled skills.

## 15. Tool descriptions moga byc dekorowane dla Copilota

Platformowy kontrakt `CopilotToolDescriptionCustomizer` pozwala feature'owi
uzupelnic opisy tools bez zmiany implementacji Spring tools. Incident feature
dostarcza `CopilotIncidentToolDescriptionCustomizer`, ktory dokleja krotkie
guidance do opisow drogich lub ryzykownych tools. Przyklady:

- full file read jest expensive i preferuje chunks/outline,
- GitLab search/flow context powinien uzywac konkretnych, ugruntowanych
  keywordow,
- GitLab flow/search guidance przypomina, ze
  `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` jest powodem do malego, focused
  GitLab lookupu pod Technical Handoff v1,
- GitLab available-repositories/search/class/flow guidance przypomina, ze
  operational context moze wskazywac kilka repozytoriow jednego systemu;
  biblioteki i shared modules z `codeSearchScopes` oraz kompatybilnych
  `codeSearchProjects` sa czescia scope'u szukania kodu,
- GitLab i DB tools powinny przekazywac krotki powod po polsku w `reason`,
- DB tools przypominaja modelowi, ze dla JPA/repository/data-access symptomow
  najpierw trzeba sprobowac ugruntowac encje, repozytorium, tabele i relacje z
  deterministic GitLab evidence albo focused GitLab tools; DB discovery jest
  fallbackiem, nie zgadywaniem tabel,
- DB sample rows nie sluzy do przegladania danych biznesowych,
- raw SQL jest last resort i moze byc zablokowany.

## 16. Tool evidence jest czescia audytu

`aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore` publikuje
tool evidence przez neutralny session-bound sink
`Consumer<AnalysisEvidenceSection>`. Provider AI adaptuje
`shared.evidence.AnalysisAiToolEvidenceListener` do tego sinka przed
wywolaniem execution gatewaya.

Capture obejmuje:

- GitLab file/chunk/chunks jako `gitlab/tool-fetched-code`,
- GitLab available repositories, search, outline, flow context i class references jako
  `gitlab/tool-discovery`,
- DB tools jako `database/tool-results`.

Widok uzytkownika dla GitLaba nadal trzyma prosty kontrakt operatorski:
`reason` podany przez model jest naglowkiem wpisu, a szczegoly sa pokazane w
tresci. Dla pobranego kodu UI pokazuje nazwe/sciezke pliku, tresc kodu i
metadata linii. Dla discovery tools UI pokazuje uporzadkowane szczegoly lookupu:
kandydatow plikow, grupy flow/class references, outline pliku i rekomendowane
dalsze odczyty.

DB capture publikuje tylko prosty wynik i `reason` podany przez model. Nie
utrzymujemy juz osobnych pytan diagnostycznych, technicznych parametrow ani
dodatkowych streszczen wyniku w user-facing evidence.

Session store zarzadza sesja i routingiem capture, a szczegoly mapowania wynikow
GitLab/DB sa oddzielone od lifecycle sesji.

`aiplatform.copilot.tools.CopilotSdkToolFactory` pozostaje warstwa rejestracji
tools: zbiera Spring `ToolCallback`, sortuje je, customizuje opisy, parsuje
input schema i tworzy `ToolDefinition`. Nie wykonuje tooli i nie interpretuje
wynikow.

`aiplatform.copilot.tools.CopilotToolInvocationHandler` pozostaje runtime
boundary: serializuje argumenty, uruchamia generyczne invocation policies,
buduje hidden `ToolContext`, wywoluje callback, publikuje wewnetrzne eventy
tool invocation i parsuje wynik dla SDK. Handler nie zna logiki GitLaba, DB,
metryk ani budget payloadu poza generycznym kontrolowanym rejection.

Event lifecycle:

1. policy before-invocation, w tym session validation i budget,
2. `Started` tylko po zaakceptowaniu invocation przez policies,
3. callback Spring tool,
4. policy after-invocation tylko po udanym callbacku,
5. terminalny `Finished(COMPLETED|REJECTED|FAILED)`.

Side-effecty sa subskrybowane przez dedykowane listenery: logging,
GitLab evidence capture i Database evidence capture. Publikator eventow izoluje
bledy listenerow, zeby awaria audytu albo logowania nie zmieniala wyniku toola.
`aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore` zarzadza
lifecycle sesji i publikacja zaktualizowanych sekcji, ale szczegoly mapowania
GitLab/DB pozostaja w odpowiednich pakietach tool capability.

## 17. Zostaje tylko usage widoczny dla uzytkownika

Na teraz nie utrzymujemy osobnej, niewidocznej dla operatora telemetryki
sesji Copilota. Runtime agreguje jedynie usage z eventow SDK
`assistant.usage` i `session.usage_info` do neutralnego
`shared.ai.AnalysisAiUsage`, bo ten kontrakt jest pokazany w job state/UI.

Tool evidence pobrane przez model nadal jest czescia audytu uzytkownika:
GitLab/DB capture publikuje `toolEvidenceSections`, a UI pokazuje je przy
analizie. Budzet tools pozostaje backendowym guardrailem i loguje
przekroczenia, ale jego liczniki nie sa osobnym feature'em telemetrycznym.

Jesli metryki optymalizacyjne wroca, powinny byc zaprojektowane jako jawny
productized element: z celem widocznym dla zespolu/operacji, testami,
dokumentacja i decyzja, gdzie uzytkownik lub operator ma do nich dostep.

## 17a. Tool feedback jest jawny i user-visible

Platforma Copilot udostepnia zawsze dostepny tool `record_tool_feedback`.
Model moze go uzyc, zeby zapisac widoczna dla operatora ocene wyniku
wczesniejszego toola: szczegolnie uzytecznego, czesciowego, pustego,
blednego, mylacego, zbyt szumnego albo zle scoped.

Decyzje:

- tool mieszka w `aiplatform.copilot.tools.feedback`, nie w incident feature,
- feedback nie przyjmuje `analysisId`, `correlationId`, `environment`,
  `gitLabGroup` ani `gitLabBranch`; scope pochodzi z biezacej sesji,
- sam callback toola pozostaje zwyklym wywolaniem Spring tool; zapis feedbacku
  do analizy robi listener zakonczonego invocation, publikujac neutralna
  sekcje przez ten sam `AnalysisAiToolEvidenceListener`, ktory obsluguje inne
  wyniki tools,
- feedback nie zuzywa exploration budgetu i nie jest targetem dla wlasnego
  feedbacku,
- wynik trafia do `shared.ai.AnalysisAiToolFeedback`, job state, UI oraz
  eksportu JSON konkretnej analizy,
- prompt renderer dodaje jedna centralna instrukcje uzycia feedbacku, gdy tool
  jest dostepny; nie dopisujemy tej samej wzmianki do kazdego skillu,
- feedback nie jest deterministic evidence i nie sluzy jako input do root
  cause diagnosis,
- feedback nie jest ukryta telemetryka, ukrytym quality gate'em ani
  automatyczna decyzja runtime.

W V1 feedback jest przechowywany tylko w stanie konkretnej analizy i
follow-up odpowiedzi chatu. Nie ma jeszcze trwalej historii ani agregacji
miedzy analizami.

## 18. Raw SQL jest oddzielnym ryzykiem

`db_execute_readonly_sql` jest traktowany osobno od typed DB tools.
Domyslnie tool policy go nie wlacza, a budzet ma osobny limit
`max-db-raw-sql-calls=0`.

Zmiana tej decyzji musi byc jawna i powinna obejmowac properties, testy oraz
audyt wyniku widoczny dla operatora.

## 19. Frontend/job API nie powinny wymagac wiedzy o SDK

Job state moze przechowywac prepared prompt i `toolEvidenceSections`, ale UI
nie powinien zalezec od typow Copilot SDK. Publiczne API pozostaje w modelu
analizy aplikacji.

Zuzycie tokenow jest wystawiane jako generyczne
`shared.ai.AnalysisAiUsage`, a nie jako event albo typ Copilot SDK. Dzieki
temu UI moze pokazac sumaryczne tokeny,
uproszczone GitHub AI Credits/USD oraz szczegoly sesji AI bez znajomosci
mechaniki event streamu. Estymacja kosztu jest liczona w frontendzie z tokenow
i tabeli stawek modelu, bo sluzy do pokazania rzedu wielkosci oplacalnosci
analizy, a nie do rozliczen finansowych.

Refaktory w `features.incidentanalysis`, `aiplatform.copilot` i obecnych
fasadach `features.incidentanalysis.job` / `api.aioptions` nie powinny
wymagac wiedzy o typach SDK w UI:
`POST /analysis/jobs` moze przyjac tylko `correlationId` oraz generyczne
preferencje AI (`model`, `reasoningEffort`). Response pozostaje mapowany do pol
aplikacji, a artefakty Copilota nadal sa embedded inline w promptcie.

Katalog modeli jest osobnym backendowym endpointem opcji AI. UI moze pokazac
model i `reasoningEffort`, ale same listy pochodza z Copilot SDK przez
`api.aioptions.AnalysisAiModelOptionsProvider`, nie z kodu Angulara.

## 20. Follow-up chat jest kontynuacja joba

Po `COMPLETED` operator moze wyslac pytanie albo polecenie przez
`POST /analysis/jobs/{analysisId}/chat/messages`. To nie dodaje recznego
scope'u do publicznego requestu startu analizy.

Decyzje:

- wiadomosc chatu jest asynchroniczna i pollowana przez ten sam
  `GET /analysis/jobs/{analysisId}`,
- initial analysis uruchamia `sessionTarget=NEW`, a follow-up kontynuuje
  zapisana sesje SDK przez `sessionTarget=EXISTING(copilotSessionId)`,
- Incident Analysis follow-up wysyla do SDK tylko tresc wiadomosci operatora;
  kontekst rozmowy, evidence i poprzednie tool evidence pochodza z historii
  sesji Copilota, a nie z ponownie renderowanego promptu,
- Flow Explorer follow-up ma feature-owned chat prompt i skill
  `flow-explorer-follow-up-chat`, zeby odpowiedz domyslnie byla Markdownem,
  nie initial JSON result contract, i zeby poglebianie przez tools oraz jezyk
  domenowy byly jawna czescia kontraktu rozmowy,
- przy resume backend ponownie przekazuje aktualne tools, skille, hidden
  context, hooks, permission handler, model i `reasoningEffort`,
- GitLab i Database tools nadal sa session-bound przez hidden `ToolContext`;
  Elasticsearch korzysta z zakonczonej analizy jako scope'u sesji, ale ma
  jeszcze zastany jawny `correlationId` w schema toola,
- scope tools pochodzi z zakonczonej analizy: `correlationId`, `environment`,
  `gitLabBranch` i `gitLabGroup`,
- raw SQL pozostaje wylaczony domyslnie; chat preferuje typed DB tools,
- tool evidence pobrane w follow-up jest przypisane do odpowiedzi chatu, a nie
  do deterministycznego pipeline evidence.

Konsekwencje:

- importowany zapis analizy jest read-only dla UI chatu, bo backend nie ma
  lokalnego uchwytu sesji SDK,
- lokalny zapis runu moze byc kontynuowany tylko wtedy, gdy ma
  `copilotSessionId`; brak tego id jest bledem kontynuacji, bez fallbacku do
  nowej sesji,
- chat moze prosic AI o weryfikacje w repo, DB albo wygenerowanie raportu, ale
  model nie powinien wymyslac scope'u ani obchodzic blokady lokalnego workspace.

## 21. Optymalizacje Copilota prowadzimy inkrementalnie

Kolejnosc prac:

1. user-visible usage i baseline jakosci wyniku,
2. JSON response contract,
3. testy kontraktu odpowiedzi,
4. coverage-aware tool policy,
5. incident digest, item IDs i evidence references,
6. tool budget,
7. tool description decorators i audit capture,
8. single prepared analysis flow,
9. dokumentacja, pro context i decision records.

Dopiero po tych warstwach warto dodawac wieksze zmiany, np. soft repair,
multi-stage flow, routing modeli albo alternatywne delivery mode artefaktow.

Przy projektowaniu uzycia Copilot SDK nie wolno opierac decyzji tylko na
publicznych metodach Javy, jesli Java SDK albo bytecode nie wyjasnia semantyki
opcji. Wtedy obowiazkowo sprawdzamy upstream `github/copilot-sdk`, szczegolnie
`nodejs/README.md` oraz schemat/protokol pakietu npm `@github/copilot`, z
ktorego generowane sa kontrakty runtime. To tam trzeba potwierdzac domyslne
wartosci, progi, workspace sesji, eventy i bezpieczny sposob uzycia mechanizmow
SDK, tak jak przy weryfikacji `infiniteSessions`.

## 22. Docelowo Copilot jest parametryzowana platforma runtime

Docelowy `aiplatform.copilot` nie jest wlascicielem analizy incydentu. To
warstwa, ktora zna Copilot SDK, lifecycle sesji, `SessionConfig`, allowliste
tools, hidden context jako mechanizm, invocation handler, policies i
techniczne eventy.

Feature ma przekazac platformie gotowa konfiguracje uruchomienia, np.:

- prompt albo gotowy input do modelu,
- model i `reasoningEffort`,
- skille albo katalogi skilli,
- tool definitions/callbacks oraz allowliste `availableTools`,
- hidden tool context jako mape danych sesji,
- evidence sink/listeners dla wynikow tooli,
- neutralny identyfikator runu do logow, np. `runReference`,
- parser albo handler odpowiedzi feature'a.

Platforma nie powinna sama wybierac incident promptu, incident skilli,
GitLab/DB/Elastic tool policy ani JSON response contractu incydentu. Nie
powinna tez zakladac, ze kazda sesja ma `correlationId`, `environment`,
`gitLabBranch` albo `gitLabGroup`.

To jest warunek dla kolejnych feature'ow. Flow explorer moze potrzebowac
requestu opisowego zamiast `correlationId`, functional logic explorer moze
budowac wynik o regulach i wariantach use case'u, a natural-language data
diagnostics moze miec kontrakt nad readonly DB queries. Te roznice musza
zostac w `features.<feature>`, nie w `aiplatform.copilot`.

Stan obecny jest przejsciowy: `features.incidentanalysis.ai.copilot` zawiera
incident-specific prompt, coverage, policy i GitLab/DB capture evidence, a
`aiplatform.copilot.tools` zawiera coraz wieksza czesc neutralnej mechaniki
invocation. Podczas dalszej ekstrakcji trzeba rozdzielic pozostale klasy na:

- generic runtime Copilota,
- feature-owned incident preparation/policy/skills/evidence mapping.

## 23. Shared/operator API jest osobna kategoria

Nie kazdy endpoint backendu dla frontendu jest czescia dedykowanego feature'a.
Endpointy wspolne dla wielu ekranow albo bedace cienka fasada nad platforma
lub integracjami traktujemy jako shared/operator API.

Zasady:

- `features.<feature>.api` posiada endpointy konkretnego use case'u, np.
  incident job API,
- `api.*` jest miejscem dla cross-screen endpointow FE/operatora,
  np. katalogu opcji AI albo stabilnych fasad nad adapterami,
- stabilne helper endpointy Elasticsearch/GitLab mieszkaja w `api.*`,
- adapter, porty, service i modele request/result zostaja w `integrations.*`.

Konsekwencja dla obecnego kodu: historyczne `analysis.options` jest zamkniete.
Neutralne `AnalysisAiOptions` mieszka w `shared.ai`, HTTP fasada
`GET /analysis/ai/options` w `api.aioptions`, a katalog modeli Copilota zostaje
w `aiplatform.copilot.runtime.options`.

## 24. Local workspace jest stanem kontynuowalnym, export jest read-only

Aplikacja uruchamiana jako lokalny JAR uzywa prywatnego katalogu workspace'u
przekazanego przez `tdw.workspace.directory`. Domyslny launcher ustawia
`tdw-data` obok skryptu/JAR-a.

Decyzje MVP:

- `index.json` jest lekkim read modelem dla ekranu `Analysis History`; lista
  historii nie laduje wszystkich `run.json` i niesie status ostatniego
  snapshotu runu,
- pelny lokalny rekord jest w `runs/<analysisId>/run.json` i jest ladowany
  dopiero przy otwarciu, eksporcie albo kontynuacji konkretnego runu,
- run moze byc zapisany juz po utworzeniu joba (`QUEUED`) i potem nadpisywany
  kolejnymi snapshotami progressu; jest to stan operator UI/history, a nie
  kolejka workerow ani gwarancja wznowienia po restarcie backendu,
- `tokens.json` lezy obok `index.json`, przechowuje lokalne access tokeny
  zapisane z UI i nie jest czescia exportu,
- `settings.json` lezy obok `index.json` i przechowuje lokalne override'y
  workspace'u; gdy pole jest ustawione w tym pliku, ma pierwszenstwo przed
  `application.properties`, a puste albo identyczne z bazowa konfiguracja pole
  usuwa override,
- stan Copilota jest pod `${tdw.workspace.directory}/copilot`, zeby
  `resumeSession` moglo korzystac z tego samego lokalnego workspace'u,
- historia lokalnych runow jest shared/operator API pod `/analysis/runs`, a
  live polling joba zostaje przy `GET /analysis/jobs/{analysisId}`,
- export lokalnego runu zwraca tylko sanitizowany `exportEnvelope`; import
  exportu jest read-only i nie tworzy kontynuowalnego runu,
- w V1 nie ma osobnego diagnostic exportu; ewentualny tryb diagnostyczny musi
  miec osobny kontrakt,
- w V1 retencja jest reczna: uzytkownik usuwa run w UI albo caly katalog
  `tdw-data`; automatyczna retencja nie jest czescia MVP,
- usuniecie pojedynczego runu probuje best-effort usunac powiazana sesje
  Copilota przez SDK `deleteSession` i lokalny katalog
  `copilot/session-state/<copilotSessionId>`, gdy rekord kontynuacji zawiera
  `copilotSessionId`; awaria cleanupu Copilota nie blokuje usuniecia historii.

Pelny backup kontynuowalnego workspace'u oznacza skopiowanie calego katalogu
`tdw-data`, najlepiej przy zatrzymanej aplikacji. Zwykly export JSON nie jest
backupem sesji ani tokenow.
