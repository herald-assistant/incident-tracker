# Copilot SDK Analysis Runtime

Ten onboarding opisuje aktualny runtime providera
`CopilotSdkAnalysisAiProvider`.

## Najwazniejszy kontrakt

Copilot dostaje przygotowany request przez generyczne
`AnalysisAiPreparedAnalysis`.

Flow:

1. `AnalysisAiProvider.prepare(request)` buduje `CopilotSdkPreparedRequest`,
2. orchestrator zapisuje `prepared.prompt()`,
3. `AnalysisAiProvider.analyze(prepared, listener)` uruchamia sesje SDK,
4. prepared request jest zamykany po execution.

Ownership prepared requestu:

- kod, ktory wywolal `prepare(request)`, zamyka returned
  `AnalysisAiPreparedAnalysis`,
- `analyze(request)` zamyka tylko prepared request utworzony przez siebie,
- `analyze(prepared, listener)` nie zamyka obiektu przekazanego przez caller,
- `CopilotSdkExecutionGateway` nie zamyka prepared requestu.

Nie ma juz podwojnego budowania promptu przez produkcyjne
`preparePrompt(...)` i `analyze(request)`. Preview promptu ma pochodzic z
realnego `prepare(request).prompt()`.

## Preparation

`CopilotSdkPreparationService`:

- ocenia evidence coverage,
- buduje coverage-aware tool policy,
- renderuje artefakty inline,
- laduje runtime skills,
- generuje prompt JSON-only,
- rejestruje metryki preparation.

Po refaktorze `CopilotSdkPreparationService` jest kompozytorem zaleznosci:

- `CopilotToolAccessPolicyFactory` buduje policy z coverage reportu i listy
  registered tools,
- `CopilotPromptRenderer` zawiera tekst promptu, JSON response contract,
  rendering capability groups i embedded artifact contents,
- `CopilotSessionConfigFactory` buduje `CopilotClientOptions`,
  `SessionConfig`, permission handler, hooks, safe lists, skill directories i
  disabled skills.
- `AnalysisAiOptions` z requestu jobowego moze nadpisac skonfigurowany
  `model` i `reasoningEffort` dla pojedynczej sesji; brak wyboru oznacza
  fallback do properties albo domyslow SDK.
- `CopilotSdkModelOptionsProvider` udostepnia osobny katalog modeli dla UI
  przez `CopilotClient.listModels()`, bez wpychania metadanych SDK do promptu
  albo job state.

Follow-up chat po zakonczonym jobie nie reuse'uje `AnalysisAiProvider`.
Ma osobny kontrakt `AnalysisAiChatProvider` i przygotowanie
`CopilotSdkFollowUpPreparationService`, bo odpowiedz nie jest JSON-only
diagnoza, tylko operatorska kontynuacja. Prompt follow-up dostaje finalny
wynik, historie rozmowy, evidence i poprzednie tool evidence.

Artefakty nie sa SDK attachments. Prompt zawiera logiczne pliki w kolejnosci:

```text
00-incident-manifest.json
01-incident-digest.md
02-... raw evidence
```

Manifest deklaruje `deliveryMode=embedded-prompt` i zawiera evidence coverage,
tool policy oraz indeks `itemIds`.

## Incident digest i itemId

`01-incident-digest.md` jest skompresowana warstwa kontekstu dla modelu:

- session facts,
- coverage summary,
- strongest log signals,
- deployment facts,
- operational code search scope,
- runtime highlights,
- code highlights,
- known evidence gaps.

Operational code search scope pochodzi z operational context i pokazuje
projekty GitLaba, pakiety oraz class hints dla dopasowanego systemu. To pomaga
Copilotowi szukac klas takze w bibliotekach i shared repozytoriach komponentu.

`itemId` sa generowane tylko w Copilot artifact rendering. Markdown artifacts
dostaja naglowki `## itemId: ...`, a JSON artifacts pole `itemId`.

## Tools

Runtime rejestruje tylko tools dozwolone przez `CopilotToolAccessPolicy`.
Policy uzywa `CopilotEvidenceCoverageReport`, a nie prostego sprawdzenia, czy
sekcja evidence istnieje.

Gdy GitLab scope jest resolved, manifest dostaje
`AFFECTED_FUNCTION_GITLAB_RECOMMENDED`. To zostawia focused GitLab tools do
malego lookupu przed finalna odpowiedzia, zeby `affectedFunction` bylo
szczegolowe i opisane jezykiem techniczno-funkcjonalnym, nie jako lista klas.

Jesli operational context wskazuje kilka `codeSearchProjects` dla tego samego
systemu, GitLab skill i tool descriptions kaza traktowac je jako jeden scope
kodu komponentu: main repo plus biblioteki/shared modules. Model nie powinien
uznawac klasy za niedostepna po jednym nietrafionym lookupie w main repo.

Gdy coverage wykryje DB-related symptom bez ugruntowanej encji/repozytorium w
GitLab evidence, manifest dostaje `DB_CODE_GROUNDING_NEEDED`. Wtedy policy
zostawia focused GitLab tools, zeby model mogl sprobowac znalezc mapowanie
entity/table/relations przed DB discovery; jesli to sie nie uda albo GitLab
tools nie ma w sesji, DB discovery jest jawnym fallbackiem.

Policy powstaje przez `CopilotToolAccessPolicyFactory`, aby ukryta zaleznosc
od coverage evaluatora nie byla zaszyta w samym recordzie policy.

`SessionHooks.onPreToolUse` blokuje lokalny workspace/filesystem/shell/terminal.
GitLab i DB tools pracuja przez hidden `ToolContext`. Elasticsearch ma jeszcze
zastany model-facing parametr `correlationId`; traktuj go jako drift do
migracji, nie jako wzorzec dla nowych tools.

W follow-up chat policy nie jest coverage-only. Najnowsza wiadomosc operatora
moze byc powodem do targeted uzycia tools, ale scope nadal pochodzi z
zakonczonej analizy:

- Elasticsearch wymaga aktualnego `correlationId`,
- GitLab wymaga `gitLabGroup` i `gitLabBranch`,
- Database wymaga resolved `environment`,
- raw SQL pozostaje domyslnie wylaczony.

`CopilotToolDescriptionDecorator` dodaje Copilot-facing guidance do opisow
drogich albo ryzykownych tools.

`CopilotSdkToolBridge` odpowiada za rejestracje definicji tools: zbiera Spring
callbacks, sortuje je, dekoruje description, parsuje input schema i tworzy
`ToolDefinition`. `CopilotToolInvocationHandler` odpowiada za wykonanie:
waliduje session id, buduje hidden context, pilnuje budgetu before/after,
wywoluje callback, zapisuje metryki, publikuje evidence capture i parsuje
result preview.

## Budget

`CopilotToolBudgetRegistry` tworzy state per `copilotSessionId`.
`CopilotToolBudgetGuard` jest wywolywany w bridge przed i po tool callbacku.

Domyslnie:

```properties
analysis.ai.copilot.tool-budget.enabled=true
analysis.ai.copilot.tool-budget.mode=soft
```

Soft mode nie blokuje. Hard mode zwraca kontrolowany result
`denied_by_tool_budget`.

## Tool evidence

Invocation handler publikuje tool evidence przez listener:

- `gitlab/tool-fetched-code`,
- `gitlab/tool-discovery`,
- `database/tool-results`.

GitLab user-facing evidence pokazuje `reason` podany przez model jako naglowek
wpisu. Dla file/chunk/chunks UI pokazuje plik/chunk, sciezke pliku i tresc
kodu. Dla search, outline, flow context i class references UI pokazuje
uporzadkowane szczegoly lookupu: kandydatow, grupy, outline i rekomendowane
dalsze odczyty.

DB user-facing evidence pokazuje `reason` podany przez model oraz wynik toola
jako `result`. Nie utrzymujemy juz diagnostycznego pytania, parametrow ani
dodatkowego streszczenia wyniku w payloadzie dla operatora.

Tool evidence z follow-up chatu jest zapisywane przy konkretnej odpowiedzi
`chatMessages`, a nie jako nowy deterministyczny provider evidence.

Registry capture zarzadza sesjami i routingiem. Mapowanie GitLab/DB wynikow
jest przeniesione do mapperow, zeby lifecycle sesji nie mieszal sie z
formatem payloadow poszczegolnych tools.

## Response parsing

Prompt wymaga odpowiedzi JSON-only. Parser akceptuje caly content jako JSON
albo fenced JSON block. Legacy labeled parser nie istnieje.

Wymagane pola:

- `detectedProblem`
- `summary`
- `recommendedAction`
- `affectedFunction`

Dodatkowe pola:

- `rationale`
- `affectedProcess`
- `affectedBoundedContext`
- `affectedTeam`
- `confidence`
- `evidenceReferences`
- `visibilityLimits`

Fallback zachowuje czesciowo sparsowane pola i ustawia
`AI_UNSTRUCTURED_RESPONSE` tylko wtedy, gdy brakuje `detectedProblem`.

## Quality gate

`CopilotResponseQualityGate` dziala domyslnie w `REPORT_ONLY`.
Findings sa widoczne w telemetryce/logach, ale nie zmieniaja runtime result.

## Telemetry

`CopilotSessionMetricsRegistry` agreguje metryki sesji, a
`CopilotMetricsLogger` emituje structured summary log.

Metryki obejmuja:

- rozmiary promptu i artefaktow,
- token usage z eventow `assistant.usage` oraz ostatni snapshot wykorzystania
  context window z `session.usage_info`,
- duration preparation/client/create session/sendAndWait/total,
- tool calls wedlug grup,
- drogie tool counters i returned characters,
- parser/fallback state,
- detected problem/confidence,
- quality findings,
- budget warnings/denials.

Mutable stan licznikow jest oddzielony od registry, ale pola
`CopilotAnalysisMetrics` i JSON summary log pozostaja generyczne dla aplikacji.
Provider mapuje token usage na `AnalysisAiUsage`, dzieki czemu job UI pokazuje
sume tokenow w ostatnim kroku bez zaleznosci od typow SDK. Frontend dodatkowo
liczy product-facing estymacje GitHub AI Credits i USD na podstawie tokenow,
modelu i prostego cennika. To ma pokazywac rzad wielkosci kosztu analizy, nie
zastepowac rozliczen GitHuba.

## Publiczny kontrakt produktu

Refaktory runtime Copilota nie zmieniaja kontraktow zewnetrznych:

- `POST /analysis` przyjmuje tylko `correlationId`,
- `POST /analysis/jobs` przyjmuje `correlationId` oraz opcjonalne generyczne
  preferencje AI: `model` i `reasoningEffort`,
- `POST /analysis/jobs/{analysisId}/chat/messages` przyjmuje tylko tresc
  wiadomosci follow-up dla zakonczonego joba,
- `GET /analysis/ai/options` zwraca modele i `reasoningEffort` dostepne wedlug
  Copilot SDK, a frontend nie hardcoduje tych list,
- `gitLabGroup` pochodzi z konfiguracji,
- `environment` i `gitLabBranch` sa wyprowadzane z evidence,
- artefakty Copilota sa embedded inline w promptcie, nie SDK attachments,
- response aplikacji pozostaje mapowany z JSON-only odpowiedzi AI do
  dotychczasowych pol.

## Properties

```properties
analysis.ai.copilot.working-directory=${user.dir}
analysis.ai.copilot.permission-mode=approve-all
analysis.ai.copilot.send-and-wait-timeout=5m
analysis.ai.copilot.model-options-timeout=20s
analysis.ai.copilot.model-options-cache-ttl=10m
analysis.ai.copilot.skill-resource-roots=copilot/skills
analysis.ai.copilot.skill-runtime-directory=${java.io.tmpdir}/incident-tracker/copilot-skills

analysis.ai.copilot.metrics.enabled=true
analysis.ai.copilot.metrics.log-summary=true
analysis.ai.copilot.metrics.log-tool-events=true

analysis.ai.copilot.quality-gate.enabled=true
analysis.ai.copilot.quality-gate.mode=report-only

analysis.ai.copilot.tool-budget.enabled=true
analysis.ai.copilot.tool-budget.mode=soft
analysis.ai.copilot.tool-budget.max-total-calls=16
analysis.ai.copilot.tool-budget.max-elastic-calls=1
analysis.ai.copilot.tool-budget.max-gitlab-calls=8
analysis.ai.copilot.tool-budget.max-gitlab-search-calls=3
analysis.ai.copilot.tool-budget.max-gitlab-read-file-calls=1
analysis.ai.copilot.tool-budget.max-gitlab-read-chunk-calls=6
analysis.ai.copilot.tool-budget.max-gitlab-returned-characters=80000
analysis.ai.copilot.tool-budget.max-db-calls=8
analysis.ai.copilot.tool-budget.max-db-raw-sql-calls=0
analysis.ai.copilot.tool-budget.max-db-returned-characters=64000
```
