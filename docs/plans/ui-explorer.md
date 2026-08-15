# UI Explorer - MVP dokumentacji widoku

Status: in-progress

Source need: [UI Explorer - dokumentacja funkcjonalna i techniczna widokow](../needs/ui-explorer.md)

Klasyfikacja: L2 - nowy feature analityczny L1, breaking evolution wspolnego
modelu Operational Context oraz nowa neutralna capability deterministycznego
rozpoznawania frontendu w integracji GitLab. Plan nie wprowadza nowego kierunku
zaleznosci, platformy runtime ani modelu bezpieczenstwa. Gdyby implementacja
wymagala wykonywania badanego frontendu, nowej zaleznosci parsera o istotnym
zasiegu, dostepu do sesji przegladarki lub nowego modelu credentiali, zakres
wymaga ponownej klasyfikacji przed ta zmiana.

Ten dokument jest propozycja wykonania. Status `draft` nie jest zgoda na
implementacje. Kazdy inkrement wymaga osobnej akceptacji zgodnie z
`docs/AGENTS.md`.

## Potrzeba i wynik produktu

UI Explorer ma zamieniac kod zlozonego frontendu w ustrukturyzowany raport o
jednym ekranie i scenariuszu. Uzytkownik wybiera system, wersje, ekran, cel i
glebokosc sekcji. Platforma samodzielnie buduje ograniczony kontekst, pozwala
AI uzupelnic go przez read-only tools i zwraca raport biznesowy albo
techniczny z dowodami, poziomami pewnosci oraz ograniczeniami widocznosci.

Feature jest rodzenstwem Incident Analysis, Flow Explorer, Config Drift Viewer,
Change Verification i Delivery Effectiveness Assessment. Nie zalezy od ich
pakietow, kontraktow joba, evidence pipeline ani skilli.

## Zakres MVP

- frontend jawnie zarejestrowany jako osobny system
  `systemType: internal-service` i `systemSubtype: frontend`,
- dokladnie jeden system-targeted code-search scope dla wybranego frontendu,
- jeden primary frontend repository source w tym scope; pozostale repozytoria
  scope nie sa traversowane w MVP,
- statyczna analiza kodu z wybranego branch/ref i przypisanie wyniku do
  rozpoznanej rewizji,
- katalog ekranow budowany z routingu i glownych komponentow widoku,
- trzy profile: `FUNCTIONAL_DOCUMENTATION`, `CHANGE_PREPARATION` oraz
  `TECHNICAL_DOCUMENTATION`,
- osiem stabilnych sekcji raportu z trybami `OFF`, `COMPACT`, `DEEP`,
- deterministyczny context snapshot przed uruchomieniem AI,
- poglebianie przez istniejace neutralne GitLab search/read tools,
- jawna ocena gotowosci i pokrycia sekcji przed zapisem raportu,
- asynchroniczny job, historia lokalna, import/export wersjonowanego JSON oraz
  eksport/copy raportu,
- nowy ekran Angular korzystajacy ze wspolnego shellu analizy, postepu, tool
  evidence, reportu, AI options i pollingu.

## Poza zakresem MVP

- uruchamianie badanego frontendu i automatyzacja przegladarki,
- logowanie do badanego systemu, przejmowanie sesji Keycloak lub credentiali,
- screenshot analysis i pixel-perfect dokumentacja,
- analiza implementacji backendu wykraczajaca poza kontrakty i wywolania
  widoczne z frontendu,
- automatyczna publikacja wyniku albo modyfikacja badanego repozytorium,
- trwaly globalny knowledge graph calego frontendu,
- multi-repository traversal w jednym runie,
- follow-up chat i continuation,
- porownanie dwoch wersji ekranu,
- generowanie albo wykonywanie testow badanego systemu.

## Baseline repozytorium

Platforma posiada juz:

- neutralny `CopilotRunRequest`, runtime session, hidden tool context,
  allowliste, polityki, budzety, eventy tool invocation i user-facing tool
  evidence,
- wspolne modele krokow, `contextSections`, activity, AI usage i reportu,
- `AnalysisReport` i renderer raportu uzywane przez kilka feature'ow,
- lokalna historie analiz oraz wzorce wersjonowanego import/export,
- `AiOptionsApiService` i `AnalysisJobPollingService` po stronie Angulara,
- wspolne komponenty przebiegu runu, evidence, raportu i shellu operatora,
- Operational Context wskazujacy systemy i repository scopes,
- integracje GitLab z odczytem plikow, wyszukiwaniem tresci, metadanymi branchy
  oraz genericznymi tools search/read.

Aktualna luka:

- `systemType` jest plaskim polem, a model nie ma `systemSubtype`,
- API, UI i maintenance Operational Context nie potrafia jawnie odroznic
  frontendu od innych `internal-service`,
- brak katalogu tras i ekranow frontendu,
- brak ograniczonego context snapshotu laczacego route, template, komponent,
  formularze, store i klientow API,
- wyspecjalizowane GitLab flow/context capability sa obecnie nastawione na
  kod Java i endpointy backendowe,
- brak kontraktu, skilli, policy, joba, historii i UI dla dokumentacji widoku,
- Operational Context celowo nie przechowuje zmiennego inwentarza tras,
  komponentow ani endpointow konkretnej rewizji.

Katalog ekranow i screen context pozostaja snapshotem wyprowadzonym z kodu.
Nie sa nowymi bytami katalogowymi Operational Context.

## Rejestracja frontendu w Operational Context

Frontend jest osobnym, kanonicznym systemem, nawet gdy dzieli monorepo z
backendem. Minimalna kompletna rejestracja sklada sie z trzech bytow:

1. `systems.yml`: `systemType: internal-service` oraz
   `systemSubtype: frontend`,
2. `repo-map.yml`: repository z `repositoryType: frontend` i kanonicznym
   `git.projectPath`,
3. `code-search-scopes.yml`: dokladnie jeden scope targetujacy system, z jednym
   repository `role: primary` oraz jawnym `searchMode`.

UI Explorer nie przyjmuje surowego repository id. `input-options` pokazuje
tylko systemy spelniajace powyzszy kontrakt. System z brakujacym subtype,
scope, primary repository albo niejednoznacznym primary jest pomijany i
zwracany jako jawny validation/configuration finding dla operatora.

Dla `systemType: internal-service` pole `systemSubtype` staje sie wymagane.
Kanoniczne MVP vocabulary to `frontend`, `backend`, `worker`, `mixed` i
`unknown`. `unknown` oznacza jawnie nieustalona klasyfikacje i nie kwalifikuje
systemu do UI Explorer. Nie wolno automatycznie ustawic `backend` ani
`frontend` na podstawie nazwy repozytorium.

Maintenance obejmuje aktualizacje:

- `operational-context-field-guidance.md`,
- `systems-yml-update-prompt.md`,
- `repo-map-yml-update-prompt.md`,
- `code-search-scopes-yml-update-prompt.md`,
- `operational-context-fill-order.md`,
- PowerShellowych checkpointow validation/cleanup, jezeli ich zachowanie lub
  raport wymaga dostosowania do breaking schema.

Przyklady maintenance, testy i fixtures uzywaja wylacznie silnie
zanonimizowanej domeny CRM, np. `crm-agent-portal`; nie kopiuja prawdziwych
nazw systemow, repository paths, ekranow, rol, endpointow ani payloadow.

## Polityka braku kompatybilnosci wstecznej

W zakresie kontraktow zmienianych przez ten plan nie utrzymujemy warstwy
kompatybilnosci:

- brak dual-read/dual-write i legacy aliasu dla `systemSubtype`,
- wszystkie bundled katalogi oraz konsumenci API/read modelu sa migrowani
  atomowo do docelowego kontraktu,
- brak fallbacku wyznaczajacego frontend z `package.json`, nazwy repozytorium
  albo `systemType` bez subtype,
- UI Explorer nie publikuje legacy request/result/job endpointow,
- import nieznanej lub starszej wersji exportu jest jawnie odrzucany zamiast
  cicho mapowany do nowego kontraktu.

Ta zgoda nie obejmuje przypadkowego lamania niezaleznych publicznych API.
Jezeli wspolny kontrakt ma konsumentow poza zmienianym zakresem, sa oni
aktualizowani i testowani w tym samym inkremencie; zmiana nie pozostawia
czasowego stanu mieszanego.

## Docelowe granice ownership

```text
features.uiexplorer
  request/result/report/section contract
  screen analysis context pipeline
  prompt/artifacts/skills guidance/tool policy
  async job/history/import-export/API

integrations.gitlab
  neutralne, bounded rozpoznanie Angular/Nx source tree
  katalog route/view i screen source context
  istniejace search/read/branch/revision capability

integrations.operationalcontext + api.operationalcontext
  kanoniczny systemType/systemSubtype
  repository i system-targeted code-search scope
  validation, maintenance API i operator-facing read models

agenttools.gitlab + aiplatform.copilot
  istniejace read-only search/read tools i neutralna mechanika runtime

shared + api + frontend shared components
  stabilne modele przebiegu, raport, usage, polling i wspolne UI
```

`features.uiexplorer` moze zalezec od integracji, agenttools, aiplatform,
shared i common. Zaden z tych pakietow nie moze importowac feature'a. Nowy
feature nie importuje klas z `features.flowexplorer` ani
`features.incidentanalysis`.

## Publiczny kontrakt MVP

### Identyfikacja

- feature id i slug: `ui-explorer`,
- backend package: `pl.mkn.tdw.features.uiexplorer`,
- route UI: `/ui-explorer`,
- lokalna historia i export maja wlasny typ feature'a, bez podszywania sie pod
  Flow Explorer.

### Input options i katalog ekranow

```text
GET /api/ui-explorer/input-options
GET /api/ui-explorer/screens?systemId={systemId}&branch={branch}
```

`input-options` zwraca tylko systemy posiadajace
`systemType: internal-service`, `systemSubtype: frontend`, dokladnie jeden
system-targeted code-search scope i jednoznaczne primary repository. Odpowiedz
zawiera tez profile, sekcje, ich tryby oraz domyslne konfiguracje.

Katalog ekranow zwraca co najmniej:

- `screenId` - stabilny w obrebie repository i rewizji identyfikator wyboru,
- business-friendly `label`,
- route pattern lub rodzaj punktu wejscia,
- parent/navigation context, jezeli jest rozpoznawalny,
- sygnaly lazy loading, guardow i parametrow,
- `sourceRevision`,
- status rozpoznania i ograniczenia katalogu.

`screenId` nie jest dowolna sciezka pliku podawana przez uzytkownika. Backend
waliduje, ze pochodzi z katalogu dla wskazanego systemu i branch/ref.

### Start i odczyt joba

```text
POST /api/ui-explorer/jobs
GET  /api/ui-explorer/jobs/{jobId}
```

Start zwraca `202 Accepted` oraz pierwszy snapshot. Request zawiera:

- `systemId`,
- `branch`,
- `screenId`,
- `profile`,
- mape `sectionModes`,
- opcjonalne `scenarioDescription`,
- opcjonalne neutralne preferencje AI `model` i `reasoningEffort`.

Walidacja:

- dokladnie jeden system, branch/ref i ekran,
- wybrany system nadal spelnia frontend registration contract,
- maksymalnie osiem sekcji innych niz `OFF`,
- co najmniej jedna sekcja aktywna,
- kontrolowany limit dlugosci opisu scenariusza,
- enumy i mapa bez nieznanych kluczy,
- branch/ref musi istniec, a `screenId` nalezec do zbudowanego katalogu,
- brak model-facing repository id, grupy GitLab, tokenu albo sciezki pliku.

Snapshot joba reuse'uje wspolne modele, ale pozostaje kontraktem feature'a.
Zawiera co najmniej:

- identyfikator, status, timestamps i bezpieczny snapshot requestu,
- `steps`, `contextSections`, `toolEvidence`, `activity` i `toolFeedback`,
- `preparedPrompt` zgodnie z obecna polityka widocznosci platformy,
- `result`, `report`, `usage`, blad oraz source revision,
- stan eksportowalnosci.

MVP nie wystawia endpointu chatu.

## Profile i sekcje

Profile ustawiaja domyslne tryby sekcji, ale wszystkie korzystaja z jednego
result contract:

- `FUNCTIONAL_DOCUMENTATION` preferuje kontekst, strukture, akcje, formularze
  i warianty; techniczne sygnaly sa dowodami pomocniczymi,
- `CHANGE_PREPARATION` poglebia akcje, formularze, dane/uslugi, stan oraz
  miejsca oddzialywania i niewiadome wymagajace decyzji,
- `TECHNICAL_DOCUMENTATION` poglebia nawigacje, dane/uslugi, stan i powiazania
  z kodem, zachowujac funkcjonalne znaczenie.

Kanoniczne sekcje i ich id:

1. `OVERVIEW`
2. `NAVIGATION_AND_ACCESS`
3. `SCREEN_STRUCTURE`
4. `ACTIONS_AND_OUTCOMES`
5. `FORMS_AND_RULES`
6. `DATA_AND_SERVICES`
7. `STATE_AND_SYNCHRONIZATION`
8. `VARIANTS_AND_FAILURES`

Kazda aktywna sekcja ma mikro-kontrakt:

- czytelne biznesowo podsumowanie,
- uporzadkowane findings zamiast swobodnego eseju,
- zaleznosci i warunki wykonania,
- source references do repozytorium i symbolu/obszaru, gdy sa dostepne,
- `CONFIRMED`, `INFERRED` albo `UNKNOWN` dla kluczowych twierdzen,
- visibility limits i otwarte pytania,
- coverage status `READY`, `PARTIAL` albo `BLOCKED`.

`CHANGE_PREPARATION` dodaje do istniejacych sekcji impact notes, pytania do
uzgodnienia i prawdopodobne obszary zmiany. Nie generuje odrebnego,
nieporownywalnego raportu.

## Zasady analizy formularzy dynamicznych

Sekcja `FORMS_AND_RULES` rozroznia trzy poziomy precyzji:

1. **Jawna definicja w kodzie** - pola, reguly i zaleznosci moga zostac
   opisane jako potwierdzone z referencjami.
2. **Custom control lub builder dostepny w repozytorium** - raport opisuje
   semantyke wynikajaca z API i uzycia; zachowanie wspolne wymaga odczytu
   implementacji kontrolki/buildera.
3. **Definicja runtime albo niedostepna biblioteka** - raport opisuje miejsce
   pobrania, mapowanie i obslugiwane sygnaly, ale konkretne pola lub reguly sa
   `UNKNOWN`, dopoki ich definicja nie znajduje sie w evidence.

Dla harmonogramow i innych wartosci automatycznie wyliczanych raport osobno
pokazuje:

- trigger i wejscia wyliczenia,
- rezultat poczatkowy,
- zakres recznej edycji,
- walidacje wykonywane po korekcie,
- warunki ponownego przeliczenia albo utraty zmian,
- operacje zapisu i obsluge konfliktu/bledu.

Sam fakt wywolania backendu nie uprawnia AI do dopowiedzenia jego algorytmu.

## Deterministyczny context pipeline

Przed uruchomieniem AI feature buduje bounded snapshot:

1. Rozwiazuje system `internal-service/frontend`, jego pojedynczy systemowy
   code-search scope i primary repository z Operational Context.
2. Waliduje branch/ref i zapisuje source revision.
3. Rozpoznaje konfiguracje workspace, route tree, lazy routes, redirecty,
   guardy i route-to-view roots bez globalnego indeksowania calego kodu.
4. Waliduje `screenId` i zbiera ograniczony screen source context:
   komponenty wejscia, template, bezposrednie style i konfiguracje, jawne
   children, formularze, akcje, store/API/WebSocket/auth candidates oraz
   istotne importy.
5. Buduje neutralny manifest plikow/symboli i wstepne coverage dla kazdej
   aktywnej sekcji.
6. Renderuje logical artifacts dla sesji Copilota.
7. AI poglebia tylko luki przez dozwolone search/read tools w ramach ukrytego
   repository/branch contextu i budzetu.
8. Readiness gate rozstrzyga, czy sekcja jest `READY`, `PARTIAL` lub `BLOCKED`;
   dopiero potem writer tworzy report.

Minimalne logical artifacts:

- `ui-explorer/request.json`,
- `ui-explorer/screen-catalog-entry.json`,
- `ui-explorer/context-snapshot.json`,
- `ui-explorer/evidence-manifest.md`,
- `ui-explorer/coverage.json`,
- `ui-explorer/response-contract.json`.

Snapshot ma twarde limity liczby plikow, rozmiaru tresci, liczby kandydatow i
glebokosci zaleznosci. Przekroczenie limitu jest visibility limit, a nie
powodem do cichego obciecia udajacego kompletna analize.

## Neutralna capability rozpoznawania frontendu

Nowa capability w `integrations.gitlab` ma pozostac niezalezna od UI Explorer
i Copilot SDK. Przyjmuje repository scope i revision rozstrzygniete przez
aplikacje, a zwraca typowane modele:

- workspace/framework signals,
- route/view catalog,
- selected screen source context,
- source manifest i coverage/diagnostics.

Capability obsluguje inkrementalnie najczestsze, statycznie rozpoznawalne
wzorce nowszego Angulara:

- standalone i module-based routes,
- `loadComponent`, `loadChildren`, redirects, guards i route parameters,
- komponenty z template/style inline oraz przez `templateUrl`/`styleUrls`,
- Reactive Forms i sygnaly customowych builderow/kontrolek,
- jawne NgRx actions/selectors/effects/reducers oraz store dispatch/select,
- wygenerowane klienty REST i bezposrednie wywolania `HttpClient`,
- jawne WebSocket/RxJS stream sources,
- widoczne role/permission checks i auth guards.

Rozpoznawanie ma byc heurystyczne i bounded, nie pelnym kompilatorem
TypeScript. Parser oparty wylacznie na regexach nie moze byc traktowany jako
pewne zrodlo zlozonych zaleznosci. Jezeli potrzebna bedzie nowa biblioteka
parsera, przed jej dodaniem trzeba zaktualizowac klasyfikacje, dependencies,
licencje, wplyw na build i plan rollbacku.

MVP przekazuje deterministyczny snapshot AI i reuse'uje istniejace genericzne
GitLab search/read tools do poglebiania. Nie dodaje UI-Explorer-specific tooli
do `agenttools` ani nazw tooli zawierajacych semantyke feature'a.

## Workflow AI i skille

Proponowany runtime workflow:

1. `ui-explorer-orchestrator` - czyta request, artifacts i section modes,
   planuje kolejnosc, pilnuje budzetu oraz readiness gate.
2. `ui-explorer-screen-grounding` - potwierdza route/view roots, granice
   ekranu i podstawowe source references.
3. `ui-explorer-forms-and-rules-section` - procedura dla formularzy,
   wyliczen, dynamicznych zachowan i poziomow precyzji.
4. `ui-explorer-data-and-state-section` - procedura laczenia danych, API,
   WebSocket, NgRx i efektow akcji.
5. `ui-explorer-write-report` - tworzy jeden result contract i
   `AnalysisReport` dopiero po readiness review.

Skille sa runtime resources pod `src/main/resources/copilot/skills`, napisane
po polsku z zachowaniem identyfikatorow technicznych. Kazdy ma jedna
odpowiedzialnosc; nie duplikuja pelnych procedur ani kontraktu odpowiedzi.

Feature dostarcza prompt, starter guidance, artifacts, available tools,
hidden context, tool description customizations, budget policy, evidence sink
i response parser przez `CopilotRunRequest`. Platforma nie wybiera skilli ani
semantyki UI Explorer.

Tool policy jest default-deny. Dozwolone sa tylko:

- wymagane skills/report tools,
- neutralne Operational Context tools, jezeli snapshot ma jawna luke,
- ograniczony zestaw GitLab search/read tools dla wybranego repository i
  source revision.

Nowy kontrakt nie powiela znanego driftu przez wystawienie `gitLabGroup`,
`branchRef` albo repository path jako model-facing scope. Scope pochodzi z
hidden `ToolContext`. Jesli istniejacy genericzny tool nadal ma legacy input,
feature izoluje jego uzycie i nie utrwala tego wzorca w nowych kontraktach.

## Result i report

Publiczny result jest typowanym kontraktem feature'a, a nie dowolnym Markdown.
Zawiera:

- screen identity, scenario, profile i source revision,
- executive/functional overview,
- osiem sekcji z mode, coverage i typed findings,
- cross-section dependencies,
- change preparation summary dla odpowiedniego profilu,
- overall confidence,
- visibility limits i unresolved questions,
- usage.

Assembler deterministycznie mapuje result na `AnalysisReport`. Glowne sekcje
raportu sa stabilne, a source references i evidence pozostaja w komponentach
raportowych mozliwych do zweryfikowania. Surowy wynik modelu nie omija
assemblera.

## Historia, portability i wersjonowanie

- Zakonczone i nieudane runy sa zapisywane w lokalnej historii zgodnie ze
  wspolnym wzorcem platformy.
- Export jest wersjonowanym, sanitizowanym JSON-em bez tokenow, hidden context,
  credentiali i surowych sekretow.
- Import odtwarza read-only snapshot/history detail; nie wznawia sesji AI.
- Raport mozna skopiowac lub wyeksportowac w formie czytelnej dla analityka.
- Schema exportu ma jawna wersje oraz testy poprawnego odrzucania starszej i
  nieznanej wersji bez fallbacku.
- Source revision jest czescia resultu, reportu i exportu.

## UI/UX

Nowa strona korzysta ze wzorca innych analysis workspaces:

- kompaktowy header i konfiguracja bez marketing hero,
- selektory systemu, branch/ref, ekranu i profilu,
- advanced section modes schowane za progresywnym ujawnieniem,
- opis scenariusza jako opcjonalne pole doprecyzowujace,
- wspolny aside dla modelu/reasoning effort,
- wspolne kroki, activity, tool evidence i report renderer,
- polling przez `AnalysisJobPollingService`,
- AI options przez `AiOptionsApiService`,
- loading/empty/error states dla katalogu ekranow i runu,
- nowe wpisy shell navigation, landing page i Analysis History.

Strona nie moze kopiowac monolitycznego wzorca komponentu Flow Explorer.
Stan orkiestracji trafia do feature facade/store, a konfiguracja, katalog,
postep i wynik do osobnych komponentow prezentacyjnych. Komponent strony
pozostaje kompozycja.

Nazwy biznesowe nie eksponuja `MCP`, `tool`, `NgRx`, `AST` ani nazw plikow w
podstawowym przebiegu. Techniczne evidence jest dostepne w warstwie szczegolow.

## Bezpieczenstwo i trust boundaries

- Kod, komentarze, README, template i dane z badanego repozytorium sa
  niezaufanym evidence, a nie instrukcjami dla modelu.
- Sesja pozostaje read-only, z zablokowanym lokalnym filesystemem, shellem i
  terminalem.
- Repository, branch/ref i system scope sa rozstrzygane oraz walidowane przed
  sesja i przekazywane jako hidden context.
- Wynik nie zawiera tokenow, credentiali, pelnego hidden context ani sekretow
  znalezionych w kodzie.
- Limity rozmiaru, timeouty i budzet tool calls chronia przed nieograniczona
  eksploracja monolitu.
- Widocznosc kontrolki dla roli jest oznaczana jako client-side behavior, nie
  backend authorization guarantee.
- API korzysta z istniejacej ochrony aplikacji i walidacji DTO; feature nie
  tworzy nowego modelu sesji ani uwierzytelnienia.

## Conformance delta

| Obszar | Baseline | Zmiana MVP | Docelowa zgodnosc |
| --- | --- | --- | --- |
| Feature ownership | brak UI Explorer | nowy `features.uiexplorer` | sibling bez importow innych feature'ow |
| Source context | generic GitLab read/search, Java-oriented deep context | bounded Angular route/screen capability | neutralna integracja bez zaleznosci od feature'a |
| Operational Context | plaski `systemType`, repository i code-search scopes | wymagany `systemSubtype`, migracja konsumentow i frontend eligibility | jawny `internal-service/frontend`, nadal bez route/component inventory |
| AI runtime | neutralny `CopilotRunRequest` | feature prompt, skills, artifacts i policy | platforma bez semantyki UI Explorer |
| Public API | brak | input options, screens, async jobs | thin controllers i typowane DTO |
| Result/report | shared report primitives | wlasny result + deterministic assembler | report-first i wspolny renderer |
| Historia | shared wzorce per feature | UI Explorer history/import/export | typowany, wersjonowany, sanitizowany format |
| Frontend | shared shell, polling, AI options, evidence, report | nowy workspace i katalog ekranow | reuse bez kopiowania wspolnych mechanizmow |

## Lista konsumentow i wplyw

### Integracja GitLab

Aktualni konsumenci obejmuja Incident Analysis, Flow Explorer, Change
Verification, Config Drift Viewer, Delivery Effectiveness Assessment,
Operational Context oraz Tool Workbench. Nowa capability jest addytywna i nie
zmienia semantyki istniejacego odczytu plikow, wyszukiwania, branch resolution
ani Java context builders. Testy regresji maja potwierdzic brak nowych importow
z `features.*` i brak zmiany istniejacych publicznych tools.

### Shared backend i runtime

UI Explorer konsumuje istniejace modele report/usage/evidence/activity i
mechanike Copilota. Plan nie rozszerza ich tylko dla wygody feature'a. Jezeli
ujawni sie faktycznie wspolny, maly kontrakt, ekstrakcja wymaga osobnego punktu
planu oraz audytu wszystkich konsumentow.

### Frontend

Konsumentami zmienionej nawigacji beda shell, landing page i Analysis History.
Istniejace feature pages pozostaja bez zmian kontraktowych. Wspolne
`AiOptionsApiService`, `AnalysisJobPollingService`, progress/evidence/report
components sa konsumowane, nie duplikowane.

### Operational Context

Zmiana dotyka loadera i DTO integracji, maintenance schema/service, walidatora,
shared/operator API, operational context tools/read models, Angularowego
edytora i wszystkich feature'ow filtrujacych dotychczas `system.kind()`. Audit obejmuje
rowniez bundled YAML, effective catalog seed, import/export maintenance oraz
procedury w `operational-context-maintenance/`. Wszyscy konsumenci sa
migrowani w jednym inkremencie; nie zostaje przejsciowy fallback bez subtype.

## Macierz weryfikacji

| Warstwa | Wymagana weryfikacja |
| --- | --- |
| Operational Context | wymagany subtype dla `internal-service`, vocabulary, CRUD/read models/tools, bundled migration, primary scope eligibility i maintenance checkpoints |
| Kontrakty/API | walidacja requestu, enumow, screen membership, `202`, snapshot i bledy przez MockMvc |
| GitLab capability | fixtures standalone/module routes, lazy loading, redirects, guards, inline/external template, limits i niejednoznacznosci |
| Context pipeline | source revision, bounded traversal, form/store/API/WS/auth signals, coverage i visibility limits |
| AI boundary | prompt/artifacts, allowlista, hidden scope, budget, readiness, parser i malformed response |
| Result/report | wszystkie profile/modes, confidence, limits, deterministic assembly i brak surowego bypassu |
| Job/history | transitiony, failure/partial result, persistence, sanitizacja oraz versioned import/export |
| Architecture | package dependency guards i brak importow feature -> feature / integrations -> feature |
| Angular API/facade | input options, screen catalog, start/poll, terminal/error state i retry |
| Angular UI | konfiguracja, progressive disclosure, steps, evidence, report, history, loading/empty/error i accessibility |
| Security | prompt injection fixtures, secret redaction, path/scope validation, tool budget i read-only allowlista |

Globalny niezmiennik macierzy: kazdy test, fixture, snapshot, artifact i
przyklad domenowy dodany lub zmieniony przez ten plan jest silnie
zanonimizowany i dotyczy wylacznie CRM. Dozwolone sa syntetyczne nazwy w stylu
`crm-agent-portal`, `crm-contact-form` i `crm-contact-api`; zabronione jest
kopiowanie rzeczywistych nazw systemow, zespolow, repository paths, ekranow,
rol, endpointow, danych klienta i payloadow.

Docelowa weryfikacja zmiany wspolnej backend-frontend, po wykonaniu wszystkich
zatwierdzonych krokow:

```text
npm --prefix frontend test -- --watch=false
npm --prefix frontend run build
mvn -q -Pbackend-dev clean package
```

W petli implementacyjnej uruchamiane sa najpierw testy celowane do zmienionych
warstw. `npm ci` tylko przy braku zaleznosci albo zmianie lockfile/package.

## Plan inkrementalny

Kazdy punkt konczy sie osobno weryfikowalnym stanem. Nastepny punkt nie jest
autoryzowany przez zatwierdzenie poprzedniego.

### 1. Breaking foundation w Operational Context

- [x] Dodac kanoniczne `systemSubtype` do loadera, DTO, editable schema,
  maintenance CRUD, walidacji, operator API/read models, operational context
  tools i edytora Angular.
- [x] Wymagac `systemSubtype` dla kazdego `systemType: internal-service` z
  vocabulary `frontend`, `backend`, `worker`, `mixed`, `unknown`; nie
  inferowac wartosci z nazwy ani zawartosci repozytorium.
- [x] Zdefiniowac eligibility frontendu: `internal-service/frontend`, jeden
  system-targeted code-search scope, jedno primary repository oraz jawny
  `searchMode`.
- [x] Zmigrowac atomowo wszystkie bundled systemy `internal-service` i
  wszystkich konsumentow wspolnego kontraktu; nie pozostawiac dual-read,
  legacy aliasu ani fallbacku dla brakujacego subtype.
- [x] Zaktualizowac `operational-context-maintenance/`: field guidance,
  prompts dla system/repository/code-search scope, fill order oraz
  checkpointy PowerShell, ktorych zachowanie lub raport zalezy od schema.
- [x] Dodac finding dla brakujacego/nieznanego subtype, brakujacego scope,
  braku primary i wielu primary; `unknown` jest poprawne katalogowo, ale nie
  kwalifikuje systemu do UI Explorer.
- [ ] Potwierdzic pelna rejestracje co najmniej jednego frontendu w effective
  katalogu srodowiska odbiorowego; rzeczywistych identyfikatorow nie utrwalac
  w testach ani przykladach dokumentacji.
- [x] Pokryc wszystkich konsumentow Operational Context, CRUD/API/tools/UI,
  bundled seed oraz maintenance dry-run celowanymi testami i wykonac pelna
  weryfikacje zmiany wspolnej.
- [x] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

Checkpoint 2026-08-15: kontrakt, konsumenci, maintenance, migracja effective
katalogu do jawnego `unknown` oraz pelna weryfikacja sa zakonczone. Krok 1
pozostaje otwarty wylacznie na potwierdzenie i wpisanie jednego rzeczywistego
frontendu z primary repository i system-targeted code-search scope; danych
produkcyjnych nie inferowano ani nie utrwalono w testach.

### 2. Kontrakt i szkielet feature'a

- [ ] Dodac lokalne `AGENTS.md` dla `features.uiexplorer` z ownership,
  dozwolonymi zaleznosciami, kontraktem sekcji i non-goals.
- [ ] Dodac enumy profilu, sekcji i mode, request/result oraz publiczne DTO
  snapshotu bez zaleznosci od innych feature'ow.
- [ ] Dodac thin input-options i job controllers ze szkieletem serwisu,
  walidacja oraz kontraktem `202`, bez udawanego wyniku AI.
- [ ] Dodac result-to-report assembler contract i puste, jawne stany
  niedostepnosci.
- [ ] Dodac package dependency/architecture tests oraz testy publicznej
  walidacji.
- [ ] Zweryfikowac celowanymi testami backendu i `mvn -q test`.
- [ ] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

### 3. Neutralny katalog ekranow i source context

- [ ] Dodac typowane, neutralne modele route/view catalog i screen source
  context w `integrations.gitlab`.
- [ ] Zaimplementowac bounded discovery dla uzgodnionych wzorcow Angular/Nx z
  source revision, diagnostics i limitami.
- [ ] Dodac screen context builder dla wybranego katalogowego `screenId`, bez
  AI i bez importu `features.uiexplorer`.
- [ ] Pokryc fixtures dla standalone/module routes, lazy loading, guards,
  template variants, formularzy, NgRx, REST, WebSocket i sygnalow auth.
- [ ] Udokumentowac jawnie nieobslugiwane wzorce jako diagnostics/limits.
- [ ] Przeprowadzic audit istniejacych konsumentow GitLab i test regresji
  niezmienionych capability.
- [ ] Zweryfikowac celowanymi testami integracji i `mvn -q test`.
- [ ] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

### 4. Deterministyczny context pipeline i katalog API

- [ ] Rozwiazywac system/repository scope z Operational Context oraz
  walidowac branch/ref i rewizje.
- [ ] Wystawic katalog ekranow z business-friendly labelami, source revision
  i diagnostics.
- [ ] Zbudowac context snapshot, evidence manifest i coverage dla aktywnych
  sekcji.
- [ ] Dodac cache tylko jezeli jest bounded po repository+revision i ma jawna
  invalidacje; brak cache nie blokuje pierwszego MVP.
- [ ] Dodac limity rozmiaru/glebokosci/liczby plikow oraz jawne partial/blocked
  rezultaty.
- [ ] Pokryc brak repo scope, nieistniejacy branch, stale `screenId`,
  niejednoznaczny route i dynamiczne definicje runtime.
- [ ] Zweryfikowac celowanymi testami backendu i `mvn -q test`.
- [ ] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

### 5. Report-first workflow Copilota

- [ ] Dodac polskie skille runtime zgodnie z rozdzialem workflow, bez
  duplikowania instrukcji.
- [ ] Dodac logical artifacts, prompt builder, starter guidance i response
  schema dla jednego result contract.
- [ ] Dodac feature-owned available tools, hidden context, description
  customizations, tool budget i coverage/readiness gate.
- [ ] Reuse'owac istniejace genericzne GitLab search/read tools tylko do
  uzupelnienia luk po deterministycznym snapshotcie.
- [ ] Dodac parser z obsluga malformed/partial response oraz deterministyczny
  report assembler.
- [ ] Pokryc prompt injection w kodzie/komentarzu, budget exhaustion,
  niedostepna biblioteke i brak definicji formularza runtime.
- [ ] Zweryfikowac celowanymi testami AI boundary i `mvn -q test`.
- [ ] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

### 6. Asynchroniczny job, historia i portability

- [ ] Zaimplementowac transitiony joba, kroki, context sections, activity,
  tool evidence, usage, failure i partial result.
- [ ] Zapisywac zakonczone/nieudane runy w lokalnej historii feature'a.
- [ ] Dodac wersjonowany sanitizowany export/import read-only i copy/export
  raportu.
- [ ] Zagwarantowac, ze source revision i visibility limits sa zachowane w
  jobie, historii, result/report i eksporcie.
- [ ] Pokryc restart, blad AI, blad source, import starszej/nieznanej wersji,
  redakcje i brak hidden context w eksporcie.
- [ ] Zweryfikowac celowanymi testami oraz `mvn -q test`.
- [ ] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

### 7. Workspace Angular dla UI Explorer

- [ ] Dodac route, navigation, landing card i Analysis History mapping.
- [ ] Dodac typowany API service, facade/state oraz osobne komponenty
  konfiguracji, katalogu, przebiegu i wyniku.
- [ ] Reuse'owac `AiOptionsApiService`, `AnalysisJobPollingService`, wspolny
  aside, steps/activity/evidence i report renderer.
- [ ] Zaimplementowac profile, section modes, opis scenariusza i
  business-friendly screen catalog z loading/empty/error/retry.
- [ ] Pokazac confidence, source revision, coverage i visibility limits bez
  technicznego zargonu w glownej sciezce.
- [ ] Dodac testy komponentow/facade/API, responsywnosci i podstawowej
  dostepnosci klawiatura.
- [ ] Zweryfikowac `npm --prefix frontend test -- --watch=false` oraz
  `npm --prefix frontend run build`.
- [ ] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

### 8. Pilot i hardening MVP

- [ ] Przygotowac zestaw co najmniej pieciu kontrolowanych fixture screens:
  prosty widok, lazy route z guardem, zlozony formularz, dynamiczny formularz
  runtime oraz cross-domain widok z NgRx/REST/WebSocket.
- [ ] Wykonac review raportow z analitykiem wedlug success criteria potrzeby,
  bez strojenia pod jedna aplikacje.
- [ ] Skalibrowac limity i domyslne section modes na podstawie usage,
  coverage i czasu runu.
- [ ] Potwierdzic brak sekretow, nieograniczonego traversal i instrukcji z
  niezaufanego kodu traktowanych jak prompt.
- [ ] Wykonac pelna macierz backend-frontend w kolejnosci wskazanej wyzej.
- [ ] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

### 9. Dokumentacja kanoniczna po wdrozeniu

- [ ] Dodac `ui-explorer-runtime-flow.md` dopiero po potwierdzeniu wynikowego
  kontraktu i zachowania.
- [ ] Zaktualizowac product direction, system overview, key decisions,
  package dependencies, continuation guide i katalog dokumentacji zgodnie z
  faktycznie wdrozonym stanem.
- [ ] Usunac z dokumentow obietnice, ktore nie weszly do MVP, albo przeniesc je
  do jawnego backlogu z nowa bramka akceptacji.
- [ ] Po zamknieciu planu przeniesc trwale decyzje do `architecture/` i nie
  traktowac planu jako kanonicznej dokumentacji runtime.
- [ ] Wszystkie testy, fixtures, snapshoty i przyklady tego kroku maja byc
  silnie zanonimizowane i dotyczyc wylacznie CRM.

## Bramka akceptacji

Przed implementacja punktu 1 uzytkownik powinien zatwierdzic co najmniej:

- jednostke analizy „widok w scenariuszu”,
- trzy profile i osiem sekcji,
- statyczny, single-repository zakres MVP,
- brak follow-up chat i runtime browser w MVP,
- publiczne endpointy i request shape,
- breaking `systemSubtype` i brak warstwy kompatybilnosci dla zmienianych
  kontraktow,
- eligibility `internal-service/frontend` przez systemowy code-search scope i
  primary repository,
- klasyfikacje L2 oraz nowa neutralna GitLab frontend discovery capability,
- silnie zanonimizowane, CRM-only testy i przyklady w kazdym kroku,
- podzial na dziewiec osobno akceptowanych inkrementow.

Zmiana tych decyzji aktualizuje najpierw source need albo ten plan; nie jest
implementowana przez domyslne rozszerzenie zakresu.
