# Domain Skill Generation - MVP

Status: in-progress

Source need: [Domain Skill Generation](../needs/domain-skill-generation.md)

Material wejsciowy:
[domain-skill-generator-specification.md](../../domain-skill-generator-specification.md)

Ostatnio zweryfikowano baseline w kodzie, dokumentacji i testach: 2026-08-09.
Read-only preflight oraz wyniki testow sa zapisane w sekcji
`Wynik Kroku 1: read-only preflight L2/L3`.

## Potrzeba / dlaczego

Platforma potrafi juz pobierac kod z GitLaba, budowac source-backed context,
uruchamiac kontrolowany runtime AI i prezentowac evidence, report oraz usage.
Nie potrafi jednak zamienic wiedzy o jednym module w zatwierdzony i
zwalidowany Agent Skill, ktory prowadzi coding agenta przez poprawne
odkrywanie vertical slice'u, granicy bounded contextu, miejsca implementacji
i walidacji zmiany.

Pierwszy zakres ma dostarczyc kompletny pion dla jednego modulu
Java/Spring/Maven:

```text
GitLab source
  -> immutable commit
  -> deterministic module manifest
  -> opcjonalna semantic proposal AI
  -> typed human review
  -> approved generation spec
  -> deterministic renderer
  -> validator
  -> preview i ZIP
```

Celem nie jest port draftu 1:1. Zachowujemy jego kluczowy lancuch zaufania:
fakty z kodu, inferencje z dowodami, decyzje czlowieka, deterministyczne
generowanie i walidacja. Maven, Java/Spring i format GitHub-compatible project
skill sa
profilami pierwszego wdrozenia, a nie neutralnym core calej platformy.

## Klasyfikacja zmiany i bramki zatwierdzania

Najwyzszy poziom planowanego zakresu: **L3 - architektoniczny**.

- L1: nowy sibling feature z wlasnym requestem, jobem, wynikiem, promptem,
  review, UI, rejestracja w shellu i artefaktem.
- L2: nowy neutralny `GitLabRepositoryRevisionPort`, rozwiazujacy branch/tag/SHA
  do niezmiennego commit SHA. Jest to wymagane, aby wyeliminowac race pomiedzy
  skanem, review i generowaniem bez rozszerzania podstawowego portu odczytu.
- L2: nowy neutralny `GitLabBoundedRepositoryReadPort` z paged inventory i
  byte-bounded text read. Obecne tree/read API ogranicza wynik dopiero po
  pobraniu, wiec nie moze egzekwowac limitow untrusted repo input.
- L2: addytywny structured payload w neutralnym kontrakcie reportu i report
  toolu, aby report-first AI moglo przeniesc walidowalna proposal bez parsowania
  swobodnego Markdownu.
- L3: session-bound policy redagowania tresci wszystkich eventow, activity i
  logow Copilota ustanawia nowa granice prywatnosci runtime. Generator wybiera
  metadata-only, a obecni konsumenci zachowuja dotychczasowy default.
- Inne zmiany L3 nie naleza do planu. Nie zmieniamy delivery mode Copilot SDK,
  nie udostepniamy filesystem/shell/terminal, nie tworzymy durable workflow
  engine, nie zapisujemy do repozytorium, nie tworzymy MR i nie uruchamiamy
  behavioral eval ani CI.

Plan jest `in-progress` po wykonaniu jawnie zatwierdzonego Kroku 1. Nie
zatwierdza to implementacji kolejnych krokow. Kazdy kolejny krok wymaga jawnego
zatwierdzenia albo zatwierdzenia calego wskazanego zakresu. Jezeli implementacja
wymaga dodatkowej zmiany w `shared`,
`aiplatform`, `agenttools`, `integrations` albo wspolnym FE,
najpierw nalezy zaktualizowac baseline, conformance delta, liste konsumentow i
uzyskac ponowna zgode.

### Decyzja architektoniczna L3: prywatnosc runtime Copilota

Generator przetwarza source-backed excerpts, ktorych nie wolno kopiowac do
operacyjnych preview. Sama feature-owned sanitization activity nie wystarcza,
bo tresc moze zostac zalogowana przed sinkiem przez runtime i tool listeners.
Dlatego policy jest kontraktem platformy przenoszonym bezposrednio przez
prepared session do gateway/session/client loggers, a po otwarciu sesji takze
rejestrowanym po session ID dla tool listeners. Obejmuje wszystkie
application-owned sciezki logowania runu. Dostepnosc `METADATA_ONLY` dodatkowo
wymaga canary dla JUL, stderr i lokalnych logow przypietego SDK/CLI.

Etapowanie i rollback:

- [ ] Dodac registry/policy z defaultem `STANDARD` i regresja dowiesc
  identycznego zachowania wszystkich obecnych konsumentow.
- [ ] Przetestowac `METADATA_ONLY` canary payloadem na kazdej sciezce logu i
  activity oraz w JUL, stderr i lokalnych `process-*.log` SDK/CLI, bez
  podpinania generatora.
- [ ] Dopiero po tym wlaczyc ASSISTED generatora z `METADATA_ONLY`; BASIC
  pozostaje niezalezny od Copilota.
- [ ] Zweryfikowac rollback: wylaczenie dostepnosci ASSISTED w `input-options`
  pozostawia BASIC. Cofniecie kontraktu platformowego wymaga redeployu, ale nie
  migracji danych ani zmiany istniejacych run requestow.

Policy nie obiecuje kontroli nad retencja danych po stronie zewnetrznego
providera modelu; data egress jest osobno widoczny operatorowi. Decyzja trafia
do `key-decisions.md` dopiero po zatwierdzeniu i wdrozeniu. Jezeli preflight
wykryje log output SDK/CLI, ktorego policy nie moze kontrolowac lub zredagowac,
ASSISTED pozostaje niedostepny; plan nie degraduje sie cicho do logowania
source-backed content.

## Baseline platformy przed zmiana

| Obszar | Aktualny stan | Ograniczenie istotne dla planu |
| --- | --- | --- |
| Feature | Nie istnieje `Domain Skill Generation` | Brak API, joba, review, UI i wyniku |
| GitLab source | `GitLabRepositoryPort` udostepnia tree, read, chunks, metadata, search i branch check | Brak osobnej capability `resolveRevision(ref) -> commitSha`; recursive tree pobiera wszystkie strony, a `readFile(maxCharacters)` limituje dopiero po pobraniu pelnego body |
| GitLab metadata | `GitLabRepositoryFileMetadata` zawiera `commitId`, hash i size pojedynczego pliku | Kotwiczenie calego repo przez dowolny plik byloby posrednie i kruche |
| Java/Spring | Istnieja REST endpoint inventory, Java method slice i endpoint/method use-case traversal | Sa celowanymi wycinkami, nie manifestem calego modulu; czesc traversal korzysta z blob search, ktorego spojnosc i limity dla commit SHA nie sa kontraktem skanera |
| Instructions | `InstructionContextDiscoveryService` zna globalne/lokalne `AGENTS.md`, Copilot instructions i wskazane docs | Zastany inventory/read moze byc unbounded; tresc repo pozostaje niezaufanym wejsciem |
| Maven | `pom.xml` projektu ma `javaparser-core`; pliki POM mozna czytac przez GitLab | Brak parsera reactor/module, parentow, roots i zaleznosci modulu |
| Operational Context | Katalog zna systems, repositories, code-search scopes i ownership | Jest wskazowka scope'u, nie dowodem o kodzie |
| Evidence | `shared.evidence.*` zasila UI i AI tool evidence | Brak feature-owned manifestu i stabilnych evidence IDs dla generatora |
| AI runtime | `CopilotRunRequest`, wybrane skille, jawna allowlista, hooks, activity, usage i report | Brak kontraktu semantic proposal generatora; event logger i activity publikuja ograniczone content/reasoning/tool previews |
| Runtime security | Glowny runtime blokuje filesystem, shell i terminal | Nie wolno oslabic tej granicy dla generatora ani evali |
| Report | `shared.ai.report.AnalysisReport` jest neutralnym kontraktem prezentacji, a sekcja ma Markdown i meta | Brak schema-bound structured payload; sam Markdown nie moze byc bezpiecznie mapowany do typed proposal ani byc inputem renderera |
| Job/UI | Istnieja feature-owned joby, polling i wspolne steps/evidence/activity/usage/report panels | Brak `AWAITING_REVIEW` i typed review editor |
| Generowanie | Brak produkcyjnego composera skilli | Brak template/profile/schema versions i file map |
| Walidacja | Brak walidatora wygenerowanego skilla | Brak structural/evidence/security checks |
| Artefakty | Feature'y maja wzorce export/history, ale nie sa wymagane w tym MVP | Brak bezpiecznego, reprodukowalnego ZIP skill package |
| Publikacja | GitLab capability jest read-only | Brak i brak potrzeby zapisu/MR w MVP |
| Behavioral eval | Brak izolowanego coding-agent runnera | Nie moze zostac zastapiony obecnym analysis runtime |

Baseline chronia miedzy innymi:

- `GitLabRestRepositoryAdapterTest`,
- `GitLabRepositoryEndpointServiceTest`,
- testy `integrations.gitlab.usecase`,
- `InstructionContextDiscoveryServiceTest`,
- testy `aiplatform.copilot.runtime` i session config,
- testy shared report/evidence oraz wspolnych komponentow Angulara,
- `PackageDependencyGuardTest`.

## Wynik Kroku 1: read-only preflight L2/L3

Krok wykonano 2026-08-09 po jawnym zatwierdzeniu. Audyt obejmowal kod,
graf konsumentow, oficjalne kontrakty GitLaba i celowane testy baseline. Nie
zmieniono kodu produkcyjnego, testowego ani konfiguracji. Zastany plik
`domain-skill-generator-specification.md` pozostal nietkniety.

### GitLab revision i bounded read

Potwierdzono:

- jedynym produkcyjnym implementerem `GitLabRepositoryPort` jest
  `GitLabRestRepositoryAdapter`; wspolny `TestGitLabRepositoryPort` i piec
  lokalnych fake'ow zostaja poza nowymi kontraktami,
- `GitLabRepositoryTreeService` pobiera wszystkie strony po `X-Next-Page`,
  materializuje pelna liste i zapisuje ja w session cache; adapter mapuje i
  cache'uje kolejna pelna liste,
- `readFile`, `readFileChunk` i analogiczny odczyt w
  `GitLabSourceResolveService` pobieraja cale `.body(String.class)` przed
  limitem; `maxCharacters` nie jest network ani memory cap,
- stary cache ma TTL 10 minut, maksymalnie 512 wpisow i klucze zawierajace
  literalny `ref`; przekazanie full SHA daje osobny klucz, wiec jego kontraktu
  nie trzeba zmieniac,
- adapter ma prywatny commit lookup i response z pelnym `id`, ale nie ma
  publicznego `resolveRevision`,
- GitLab Commits API przyjmuje hash, branch lub tag i zwraca pelne commit ID, a
  Repository Files API wspiera commit jako `ref`,
- blob search po full SHA nie jest udokumentowanym kontraktem i nie ma lokalnej
  regresji; skaner nie moze go uzywac,
- dokumentacja repository tree wymienia branch/tag. Paged tree po full SHA
  wymaga testu kompatybilnosci z docelowa wersja GitLaba, zanim zostanie uznane
  za domkniety kontrakt produkcyjny.

Wynik capability gap pozostaje waski: dwa osobne, obowiazkowe neutralne porty,
bez zmiany `GitLabRepositoryPort` i jego cache. `GitLabRepositoryRevisionPort`
zwraca requested ref, full commit SHA i committed time.
`GitLabBoundedRepositoryReadPort` wykonuje jeden upstream tree request na page
oraz streamuje najwyzej `maxBytes + 1`, z typed binary/LFS result i zamknieciem
body po hard-stop. Nowy port nie deleguje do starego full-body read/cache.

### Structured report

Potwierdzono:

- `AnalysisReportSection` ma piec pol, a obecny report tool przyjmuje tylko
  Markdown i meta; nie da sie z niego scisle odtworzyc typed semantic proposal,
- stary `report_upsert_section` nie moze dostac nowego argumentu, bo zmieniloby
  to model-facing schema obecnych feature'ow,
- osobny `report_upsert_structured_section` musi walidowac allowed section oraz
  dokladny tuple `(sectionId, schemaId, schemaVersion)`, limity i JSON Schema
  przed pierwsza mutacja store,
- repo nie ma obecnie biblioteki JSON Schema; jej jawny wybor i przypiecie
  wersji jest czescia Kroku 7,
- `AnalysisReportStructuredPayload` moze byc addytywnym nullable polem sekcji,
  ale wymaga piecioargumentowego konstruktora, starego JSON testu, null omission
  oraz defensywnej kopii `JsonNode`,
- report jest serializowany przez wszystkie cztery job snapshots/API i cztery
  envelope/persistence paths; Incident, Flow, Change i Runtime Configuration
  factories/mappers sa konsumentami regresji,
- shared Angular normalizer rekonstruuje sekcje i dzis odrzucilby nowe pole przy
  import/history round-trip. Payload musi byc zachowany jako inert JSON, ale
  nigdy renderowany jako HTML,
- dodanie structured toola do ogolnego legacy predicate rozszerzyloby allowlisty
  istniejacych feature'ow. Generator ma jawnie allowlistowac tylko report GET i
  structured upsert, bez poszerzenia legacy zestawu.

Schema registry ma byc rejestrowany atomowo razem z reportem przed pierwszym
tool call i czyszczony w tym samym `finally`. Odrzucenie scope/schema/size/depth
lub node count nie moze zmienic reportu. Stary JSON bez payload nie ma dzis
dedykowanego testu; jest to jawny baseline gap, a nie zalozona kompatybilnosc.

### Copilot privacy boundary

Potwierdzono, ze obecny `STANDARD` publikuje lub loguje content-backed dane w
kilku niezaleznych sciezkach:

- gateway activity dla user/assistant/reasoning, tool arguments/results,
  context i error previews,
- `CopilotSessionEventLogger` i `CopilotClientLifecycleLogger`,
- `CopilotToolInvocationLoggingListener`, budget warnings oraz wyjatki
  listenerow/event publishera,
- terminalny catch `CopilotSdkExecutionGateway`, ktory loguje
  `rootCauseMessage`, throwable i stack oraz zachowuje raw cause w
  `CopilotSdkInvocationException`,
- job services, ktore kopiuja `exception.getMessage()` do publicznego job error.

Feature-owned activity sanitizer nie zamyka tych sciezek. Dlatego
`CopilotRunContentPolicy` pozostaje zmiana L3 na granicy platformy i musi byc
przenoszona przed startem klienta, przez session events oraz tool invocation.
`METADATA_ONLY` wymaga generycznego public exception bez raw message/cause,
izolacji rownoleglych runow, cleanup po success/error oraz bounded tombstone dla
late events. `STANDARD` zachowuje obecne zachowanie.

Audyt przypietego `copilot-sdk-java:1.0.0` wykryl dodatkowe sciezki poza
per-run policy aplikacji: JSON-RPC logger JUL na poziomie `FINE`, stderr
`CliServerManager`, raw stderr w startup exception, domyslny CLI
`--log-level info` i lokalne `process-*.log` pod Copilot home. CLI nie byl
dostepny na `PATH` w srodowisku preflight, wiec live canary nie mogl zostac
wykonany. Przed wlaczeniem ASSISTED wymagany jest canary obejmujacy JUL,
stderr i pliki CLI. Jezeli output SDK/CLI nie moze zostac kontrolowany,
izolowany i usuniety bez wycieku, ASSISTED pozostaje niedostepny; BASIC nie
jest blokowany. Ewentualny upgrade SDK albo zmiana sposobu uruchamiania CLI
wymaga osobnej aktualizacji planu i zgody L3.

### Decyzja o jezyku package

`outputLanguage=pl-PL` nie wynika z source need ani draftu generatora. Krok 1
odrzuca je jako ciche zalozenie. Polski pozostaje wymagany dla wewnetrznego
runtime `SKILL.md` aplikacji. Jezyk wygenerowanego package wymaga osobnej decyzji
uzytkownika przed Krokiem 5 i nie blokuje Kroku 2.

### Baseline testow

| Obszar | Wynik | Uwagi |
| --- | --- | --- |
| GitLab core/tree/source/instructions/usecase | 23 klasy, 159 testow, PASS | 0 failures/errors/skipped |
| GitLab API/MCP/cztery feature'y/architecture | 22 klasy, 126 testow, PASS | zawiera `PackageDependencyGuardTest`; 0 failures/errors/skipped |
| Copilot runtime/tools/assemblery/joby | 21 klas, 121 testow, PASS | 0 failures/errors/skipped |
| Shared report/mappers/API/history/import | 22 klasy, 124 testy, PASS | 0 failures/errors/skipped |
| Angular report/import-export consumers | 9 plikow, 89 testow, PASS | jeden wskazany `change-verification-page.spec.ts` nie istnieje |

Pierwsza proba Copilot baseline zostala przerwana przez zbyt krotki timeout
orkiestratora i pelne ponowienie przeszlo. Pierwsza proba Angulara w sandboxie
zakonczyla sie `Access is denied`; identyczny retry poza sandboxem przeszedl.
Nie sa to failures kodu. Zastane ostrzezenia Mockito/ByteBuddy o dynamicznym
attach pozostaja poza zakresem feature'a.

Dokladne komendy baseline:

```text
mvn -q "-Dtest=GitLabRestRepositoryAdapterTest,GitLabRepositorySearchServiceTest,GitLabRepositoryEndpointServiceTest,GitLabSourceResolveServiceTest,InstructionContextDiscoveryServiceTest,GitLabJavaMethodSliceServiceTest,GitLabOpenApiEndpointSliceServiceTest,GitLabEndpointUseCase*Test,GitLabJava*Test" test

mvn -q "-Dtest=GitLabMcpToolsTest,GitLabMcpToolsContextTest,CopilotSdkToolFactoryTest,CopilotSdkToolFactoryDescriptionTest,GitLabRepositoryFilesByPathApiServiceTest,GitLabRepositorySearchControllerTest,GitLabSourceResolveControllerTest,ChangeVerificationSourceDiscoveryServiceTest,ChangeVerificationJobServiceTest,FlowExplorerContextServiceTest,FlowExplorerOpenApiContractServiceTest,FlowExplorerSnippetCardServiceTest,FlowExplorerEndpointInventoryServiceTest,GitLabDeterministicEvidenceProviderTest,AnalysisEvidenceCollectorTest,CopilotIncidentInitialPreparationServiceTest,RuntimeConfigurationCodeUsageSearchServiceTest,RuntimeConfigurationDeepContextServiceTest,RuntimeConfigurationDeepPreflightServiceTest,RuntimeConfigurationDeepPreflightControllerTest,SyntheticAdaptersTest,PackageDependencyGuardTest" test

mvn -q "-Dtest=CopilotRunPreparationServiceTest,CopilotSessionConfigFactoryTest,CopilotSdkExecutionGatewayTest,CopilotSdkToolFactoryTest,CopilotSdkToolFactoryBudgetTest,CopilotToolBudgetPolicyTest,CopilotToolBudgetRegistryTest,CopilotToolAffordanceEvidenceCaptureListenerTest,CopilotToolFeedbackInvocationListenerTest,CopilotReportSessionStoreTest,CopilotReportToolsTest,CopilotIncidentRunRequestFactoryTest,FlowExplorerCopilotRuntimePreparationTest,ChangeVerificationCopilotRunRequestAssemblerTest,RuntimeConfigurationCopilotRunRequestAssemblerTest,AnalysisJobFacadeTest,FlowExplorerJobServiceTest,FlowExplorerLocalRunChatHandlerTest,ChangeVerificationJobServiceTest,RuntimeConfigurationVerificationJobServiceTest,RuntimeConfigurationVerificationParallelJobServiceTest" test

mvn -q "-Dtest=AnalysisReportModelTest,CopilotReportSessionStoreTest,CopilotReportToolsTest,CopilotRunPreparationServiceTest,CopilotSdkExecutionGatewayTest,CopilotIncidentReportMapperTest,FlowExplorerReportMapperTest,ChangeVerificationReportMapperTest,RuntimeConfigurationReportMapperTest,IncidentAnalysisLocalRunPersisterTest,IncidentAnalysisLocalRunChatHandlerTest,FlowExplorerLocalRunPersisterTest,FlowExplorerLocalRunChatHandlerTest,ChangeVerificationLocalRunPersisterTest,RuntimeConfigurationVerificationPortabilityTest,AnalysisRunHistoryServiceTest,AnalysisRunHistoryControllerTest,AnalysisJobControllerTest,FlowExplorerJobControllerTest,ChangeVerificationJobControllerTest,RuntimeConfigurationVerificationJobControllerTest,RuntimeConfigurationVerificationImportControllerTest" test

npm --prefix frontend test -- --include=src/app/core/utils/analysis-import-export.utils.spec.ts --include=src/app/components/analysis-report-panel/analysis-report-panel.spec.ts --include=src/app/components/analysis-final-result/analysis-final-result.spec.ts --include=src/app/features/analysis-console/analysis-console.spec.ts --include=src/app/features/flow-explorer/utils/flow-explorer-import-export.utils.spec.ts --include=src/app/features/flow-explorer/pages/flow-explorer-page/flow-explorer-page.spec.ts --include=src/app/features/change-verification/utils/change-verification-import-export.utils.spec.ts --include=src/app/features/change-verification/pages/change-verification-page/change-verification-page.spec.ts --include=src/app/features/runtime-configuration-verification/utils/runtime-configuration-import-export.utils.spec.ts --include=src/app/features/runtime-configuration-verification/pages/runtime-configuration-verification-page/runtime-configuration-verification-page.spec.ts
```

Kryterium Kroku 1 jest spelnione: nie ma zmian produkcyjnych, implementerzy i
konsumenci sa jawni, baseline nie ma zastanego failure, a nowe luki zostaly
przeniesione do zakresu i macierzy kolejnych krokow. Krok 2 pozostaje
niezatwierdzony.

## Conformance delta

| Pole | Planowana zmiana |
| --- | --- |
| Cel zmiany | Zwalidowany skill package dla jednego modulu, oparty na dowodach i decyzjach czlowieka |
| Dlaczego nie wystarcza obecny mechanizm | Platforma analizuje kod, ale nie ma modelu epistemicznego, review, composera ani validatora skilli |
| Warstwa bedaca wlascicielem | `features.domainskillgeneration`; neutralne revision/bounded-read ports w `integrations.gitlab`, structured report payload w `shared.ai.report`/report tools i session content policy w `aiplatform.copilot.runtime` |
| Publiczne API/DTO | Addytywne `/api/domain-skill-generation/**`; bez aliasu `/analysis/**` |
| Context/evidence | Nowy wersjonowany `ModuleEvidenceManifest`; projekcja do `AnalysisEvidenceSection` tylko dla UI |
| Prompt/artifacts/skills | Nowy prompt ASSISTED, ograniczone inline artifacts i polski runtime skill generatora |
| Tools/policy/hidden scope/budzet | BASIC bez AI; ASSISTED tylko z wbudowanym `skill` i schema-bound structured report tools, bez GitLab/filesystem/shell/terminal tools |
| Report/result | `AnalysisReport` ze structured payload jest source of truth wyniku AI; payload mapuje sie scisle do typed review draft, Markdown jest deterministyczna prezentacja, a file map powstaje tylko z approved spec |
| Job state | Nowy state `AWAITING_REVIEW`, optimistic revision i manifest fingerprint |
| Persistence/export | Job, review, prepared prompt i ZIP sa tylko live/in-memory z TTL; bez historii, importu, exportu JSON i continuation w MVP |
| Shared FE/UX | Reuse polling i wspolnych paneli; review editor i file preview sa feature-owned |
| Nowe zaleznosci | Feature do integrations/aiplatform/shared/common; brak importow sibling feature'ow |
| Konsumenci shared mechanizmow | Pelna lista w sekcji consumer audit; ich zachowanie ma pozostac bez zmian |
| Kompatybilnosc | Nowe endpointy i wersjonowane kontrakty; brak migracji danych i legacy aliasu |
| Testy regresji | GitLab, architecture guard, backend/FE feature, shared UI consumers i pelny build |
| Dokumentacja | Nowy need/plan, a po implementacji runtime flow i aktualizacja kanonicznych map |
| Znany drift | Nie kopiujemy incidentowych aliasow/status assumptions, model-facing source scope ani GitLab-shaped continuation |

## Proponowane rozwiazanie

Feature ma dwa jawne tryby:

| Tryb | Przebieg | Data egress do modelu |
| --- | --- | --- |
| `BASIC` | scan -> manual review -> render -> validate -> ZIP | brak |
| `ASSISTED` | scan -> typed proposal AI -> review -> render -> validate -> ZIP | manifest i ograniczone, widoczne fragmenty evidence |

`BASIC` jest pierwszym kompletnym pionem i nie zalezy od dostepnosci
Copilota. `ASSISTED` dodaje propozycje, ale nie zmienia faktow i nie renderuje
plikow. Awaria, timeout albo niepoprawna odpowiedz AI nie niszczy manifestu;
job przechodzi do manualnego review z widocznym ograniczeniem.

### Wybrane alternatywy i trade-offy

- Rozszerzenie `GitLabRepositoryPort` metoda z domyslnym `unavailable` byloby
  mniejszym diffem, ale ukrywaloby brak obowiazkowej capability. Osobny revision
  port zachowuje istniejace fakes i wymusza implementacje tam, gdzie jest
  potrzebna.
- Uzycie obecnego recursive tree i `readFile(maxCharacters)` byloby szybsze,
  lecz limity dzialalyby dopiero po pobraniu danych. Osobny paged/byte-bounded
  port jest wieksza zmiana integracji, ale daje egzekwowalna granice sieci i
  pamieci dla niezaufanego repozytorium.
- Strict JSON w finalnej odpowiedzi modelu bylby prostszy, lecz odwrocilby
  platformowy report-first flow. Schema-bound payload w reportcie kosztuje
  neutralna zmiane L2, ale daje jeden audytowalny source of truth bez parsowania
  Markdownu.
- Feature-owned typed proposal tool uniknalby zmiany shared report modelu, ale
  tworzylby drugi session store i boczny kanal wyniku AI. Nie wybieramy go.
- Historia z odtwarzaniem ZIP zwiekszylaby wygode, lecz wymagalaby trwalego
  przechowywania source-backed artefaktu i osobnego API. MVP wybiera live TTL i
  jawne ponowne uruchomienie.
- Backendowa property nie steruje statycznym Angular route. MVP dodaje route
  dopiero z gotowym BASIC, a rollback wymaga ponownego wdrozenia zamiast
  pozornej feature flag.

### Stabilny kontrakt discovery

- Nazwa produktu: `Domain Skill Generation`.
- Feature ID: `domain-skill-generation`.
- Pakiet backendu: `pl.mkn.tdw.features.domainskillgeneration`.
- Slug UI: `/domain-skill-generation`.
- Prefix API: `/api/domain-skill-generation`.
- Uzytkownik: developer, owner modulu, tech lead albo architekt.
- Decyzja: czy opis granicy i kierunku modulu jest wystarczajacy, aby
  wygenerowac i pobrac skill package.
- Minimalny input: `mode`, `systemId`, `repositoryId`, `ref`, `modulePath`;
  w ASSISTED opcjonalnie `model` i `reasoningEffort`.
- Wynik: manifest, visibility limits, opcjonalna proposal/report, review,
  validation report i metadata ZIP.
- Deterministic sources: GitLab POM/Java/tests/instructions oraz katalogowy
  scope Operational Context.
- AI-guided source tools: brak w MVP; material AI jest przygotowany przed
  uruchomieniem. Model ma tylko runtime `skill` i schema-bound report tools.
- Follow-up chat: nie.
- Local continuation: nie.
- Historia/import/export JSON: nie w MVP.
- Output profile: wewnetrznie wersjonowany `github-project-skill-v1`, zgodny z
  project-level formatem skilla wspieranym przez GitHub. `v1` jest wersja
  generatora, nie deklaracja oficjalnej wersji standardu GitHub.
- Output language wygenerowanego package pozostaje otwarta decyzja produktowa.
  Ani source need, ani material wejsciowy nie ustalaja `pl-PL`. Przed Krokiem 5
  uzytkownik wybiera staly `pl-PL` albo jawne typed `outputLanguage` w
  request/review. Techniczne identyfikatory pozostaja w oryginalnym brzmieniu.
  To osobna decyzja od niezmiennika, ze wewnetrzny runtime skill aplikacji musi
  byc po polsku.
- Target profile: jeden wersjonowany profil
  `modular-monolith-bounded-context-v1`.

### Ownership i granice pakietow

```text
features.domainskillgeneration
  -> integrations.gitlab
  -> integrations.operationalcontext
  -> aiplatform.copilot
  -> shared
  -> common
```

Zakazane pozostaja:

```text
domainskillgeneration -> features.incidentanalysis
domainskillgeneration -> features.flowexplorer
domainskillgeneration -> features.changeverification
domainskillgeneration -> features.runtimeconfigurationverification
integrations/aiplatform/shared -> domainskillgeneration
```

Planowany ksztalt feature'a:

```text
features/domainskillgeneration/
  AGENTS.md
  input/                     # feature-owned source/module options
  job/
    api/
    state/
    error/
  source/                    # catalog scope i immutable revision
  scan/
    maven/
    java/
    instructions/
  manifest/                  # facts, unknowns, provenance i fingerprint
  ai/
    model/
    preparation/
    copilot/
    report/
  review/                    # decisions, exceptions i optimistic gate
  generation/
    spec/
    template/
    render/
  validation/
  artifact/
```

Nowe zasoby:

```text
src/main/resources/copilot/skills/domain-skill-generation-analyze/SKILL.md
src/main/resources/domain-skill-generation/profiles/
src/main/resources/domain-skill-generation/templates/
src/main/resources/domain-skill-generation/schemas/
frontend/src/app/features/domain-skill-generation/
```

Whole-module scanner, manifest, proposal, review i generation spec sa
feature-owned. Nie trafiaja do `shared` przy pierwszym konsumencie.

## Publiczne API

| Endpoint | Zachowanie |
| --- | --- |
| `GET /api/domain-skill-generation/jobs/input-options` | Tryby, status AI/auth, dozwolony katalog source, profile, TTL i effective limits potrzebne ekranowi |
| `GET /api/domain-skill-generation/module-options` | Krotkie POM-only discovery modulow dla dozwolonego `systemId/repositoryId/ref`; zwraca uzyty commit SHA i nie uruchamia pelnego skanu |
| `POST /api/domain-skill-generation/jobs` | Waliduje input, zwraca `202 Accepted` i natychmiastowy snapshot |
| `GET /api/domain-skill-generation/jobs/{jobId}` | Zwraca lekki snapshot: lifecycle, manifest summary/coverage, ograniczona evidence projection, review, report, usage i artifact metadata |
| `GET /api/domain-skill-generation/jobs/{jobId}/manifest/evidence` | Zwraca stronicowany, sanitizowany evidence index dla live joba |
| `POST /api/domain-skill-generation/jobs/{jobId}/review-decisions` | Przyjmuje typed review i zwraca `202` po atomowym uruchomieniu generation |
| `GET /api/domain-skill-generation/jobs/{jobId}/skill-package/preview` | Zwraca canonical file map do podgladu po successful validation |
| `GET /api/domain-skill-generation/jobs/{jobId}/skill-package` | Zwraca ZIP tylko po successful validation |

Nie powstaje alias `/analysis/**`. Token, GitLab group, base URL i project path
nie sa polami publicznego requestu. Aktualny Operational Context nie modeluje
`connectionId`, wiec feature go nie wprowadza.
Katalog modeli pozostaje pobierany ze wspolnego
`GET /api/analysis/ai/options`; feature nie duplikuje model catalog API.

### Start request

Planowany request:

```text
mode: BASIC | ASSISTED
systemId: katalogowy system
repositoryId: repozytorium nalezace do scope systemu
ref: branch, tag albo commit wskazany przez operatora
modulePath: kanoniczna relatywna sciezka POSIX; "." oznacza root
model: tylko ASSISTED, opcjonalne
reasoningEffort: tylko ASSISTED, opcjonalne
```

Backend dopuszcza repozytorium tylko przez precyzyjna relacje katalogowa
`system -> codeSearchScope -> repository`. GitLab project/path pochodzi z tego
repozytorium w Operational Context, natomiast group, base URL i token z jednego
skonfigurowanego adaptera `analysis.gitlab`; katalogowy path musi byc zgodny z
tym skonfigurowanym group scope. `repositoryId` poza ta relacja jest odrzucane.
`modulePath` musi wskazywac modul zwrocony przez POM-only discovery
dla tego samego repo/ref, miescic sie w dozwolonych `pathPrefixes` scope'u albo
jego jawnym root scope i zostac ponownie potwierdzony przez POM po rozwiazaniu
ref przy starcie joba.

Feature kanonikalizuje tylko wybrana projekcje
system/code-search-scope/repository. Jezeli katalog nie ma jawnej wersji,
oblicza `catalogProjectionFingerprint` jako SHA-256 tej projekcji i oznacza go
jako derived locator, nie evidence o kodzie ani architekturze docelowej.

### Review request

Review nie przyjmuje manifestu ani zmodyfikowanych faktow. Zawiera:

```text
reviewRequestId
expectedRevision
manifestFingerprint
action: GENERATE | STOP
skillName
boundedContextId i boundedContextName
targetProfileId i targetProfileVersion
targetArchitectureCard
decyzje dla nierozstrzygnietych role assignments
ACCEPT | REJECT | REPLACE | DEFER_AS_UNKNOWN dla proposal items
uzasadnienie wymagane dla REPLACE, DEFER_AS_UNKNOWN i STOP
wyjatki: scope, reason, owner, do-not-extend, opcjonalne expiry
wybrane validationCommandTemplateIds
reviewerLabel opcjonalny
```

`reviewerLabel` jest deklaracja operatora, nie uwierzytelnionym approvalem.
`approvedAt` nadaje serwer. Ten trust model musi byc widoczny w provenance.
Serwer przechowuje przez caly czas zycia joba mapowanie
`reviewRequestId -> canonical payload digest + outcome`. Identyczny retry jest
idempotentny; ten sam ID z innym payloadem oraz request ze starym
`expectedRevision` albo fingerprintem zwraca `409 Conflict`.

`DEFER_AS_UNKNOWN` nie moze zostac wyrenderowane jako twarda regula. Jezeli
odroczone pole jest wymagane przez target profile, review musi wybrac `STOP`.
`STOP` jest swiadomym terminalnym wynikiem bez package, a nie awaria techniczna.

`targetArchitectureCard` jest wymagany w BASIC i ASSISTED. To typed, nie
Markdownowy kontrakt obejmujacy: mission/boundary modulu, owned capabilities i
domain concepts, inbound entrypoints/contracts, application use cases,
data/persistence ownership, outbound ports/integrations, allowed dependencies,
forbidden placements, placement rules i validation expectations. Kazdy element
jest decyzja operatora albo wskazuje evidence IDs; AI moze tylko wypelnic
propozycje startowe. Brak AI nigdy nie zmniejsza kompletnosci review.

### Lifecycle joba

```text
QUEUED
  -> RESOLVING_SOURCE
  -> SCANNING
  -> PROPOSING              # tylko ASSISTED; failure moze przejsc dalej
  -> AWAITING_REVIEW
  -> GENERATING
  -> VALIDATING
  -> COMPLETED | STOPPED | UNSUPPORTED_SCOPE | EXPIRED | FAILED
```

`AWAITING_REVIEW` pauzuje polling, ale nie jest terminalnym wynikiem. Po
przyjeciu review polling startuje ponownie. Job i jego revision sa
thread-safe; tylko jedno przejscie `AWAITING_REVIEW -> GENERATING` wykonuje
renderer.

Job status nie jest stanem workflow wygenerowanego skilla. Wewnetrzny stan
skilla `BLOCKED` opisuje zachowanie przyszlego coding agenta, a nie awarie
generatora.

Jednoznacznie wykryty zakres multi-module/multi-bounded-context konczy job jako
`UNSUPPORTED_SCOPE`. Niejednoznaczny zakres trafia do review i moze zostac
zakonczony przez `STOPPED`. `EXPIRED` oznacza przekroczenie live TTL; nie jest
podstawa do odtworzenia joba z historii.

Snapshot rozdziela:

- source selection i resolved commit SHA,
- job revision/status/current step/error,
- shared steps, ograniczona evidence projection, metadata-only activity i usage,
- manifest summary, coverage, fingerprint i visibility limits zamiast pelnego
  `ModuleEvidenceManifest`,
- publiczny `AnalysisReport` ze schema-bound payload oraz typed review draft,
- review requirements i zatwierdzone decyzje,
- `SkillPackageValidationReport`,
- finalne artifact metadata, checksums i `artifactExpiresAt`,
- dokladny `preparedPrompt` tylko dla live ASSISTED joba; nie trafia on do
  activity, historii ani ZIP.

## Deterministyczny source i manifest

### Immutable source revision

Powstaje osobny, wymagany neutralny `GitLabRepositoryRevisionPort`:

```text
resolveRevision(group, projectName, ref)
  -> requestedRef, commitSha, committedAt
```

Produkcjny adapter realizuje read-only lookup przez GitLab Commits API.
`GitLabRestRepositoryAdapter` implementuje zarowno dotychczasowy
`GitLabRepositoryPort`, jak i nowy revision port.
Branch, tag albo podany SHA sa rozwiazywane raz na poczatku joba. Wszystkie
inventory i content calls w tym runie uzywaja pelnego commit SHA.
Istniejacy cache juz kluczuje po przekazanym ref; przekazanie pelnego SHA
wystarcza i plan nie zmienia jego kontraktu.

Kontrakt pozostaje neutralny i nie zna pojec `skill`, `manifest`, `review` ani
`evidence`. `GitLabRepositoryPort` i jego test doubles pozostaja bez zmian;
revision capability nie ma opcjonalnego defaultu `unavailable`.

### Bounded repository read

Obecny `GitLabRepositoryPort.listRepositoryFiles` materializuje recursive tree,
a `readFile(maxCharacters)` pobiera pelne raw body przed skroceniem. Skaner ich
nie uzywa. Powstaje wymagany neutralny `GitLabBoundedRepositoryReadPort`:

```text
listBlobsPage(group, projectName, commitSha, pathPrefix, cursor, pageSize)
  -> entries, nextCursor

readTextFileBounded(group, projectName, commitSha, path, maxBytes)
  -> bytes do bezpiecznego dekodowania, observedBytes, truncated
```

Inventory pobiera najwyzej jedna zadana strone i pozwala feature'owi przerwac
po limicie entries/pages/time. Bounded read uzywa metadata/HEAD gate, jezeli
dostepny, oraz stream hard-stop po `maxBytes + 1`; nie buforuje pelnego body.
Odrzuca binary/LFS content przed dekodowaniem. Ewentualny nowy cache jest
kluczowany przez host/project/commit SHA/path/cursor/limit i nie zmienia cache
dotychczasowego portu.

Skan nie uzywa `searchCandidateFiles` ani GitLab blob search. Dzieki temu nie
zalezy od nieudokumentowanej semantyki search dla commit SHA. Wszystkie symbole
i referencje sa odkrywane z limitowanego inventory i pobranych przez bounded
port plikow.

### Scope i bezpieczne sciezki

- `modulePath` jest normalizowany jako sciezka POSIX.
- Odrzucane sa sciezki absolutne, drive letters, backslash, `..`, puste
  segmenty, encoded separators, NUL i control characters.
- Skan jest ograniczony do subtree modulu.
- Wyjscie poza subtree jest dopuszczone tylko dla jawnie odkrytego parent POM
  oraz root/lokalnych instruction files, nadal w tym samym repo i SHA.
- Repozytorium nie jest klonowane i nie jest pobierane jako archiwum.
- Nie sa czytane binaria, LFS payloads ani pliki spoza allowlisty typow.

### Maven scan

Feature-owned parser:

- wykrywa root/standalone/reactor POM i modul wskazany przez `modulePath`,
- odczytuje coordinates, packaging, declared modules i dependencies,
- rozpoznaje standardowe i jawnie zadeklarowane source/test roots,
- zbiera plugin/profile/property names jako fakty, ale nie wykonuje ich,
- sledzi parent POM tylko po bezpiecznej sciezce w tym samym repo,
- nie pobiera parentow i artefaktow z zewnetrznych repozytoriow,
- nie uruchamia Mavena, wrappera ani pluginow,
- uzywa parsera XML z wylaczonym DTD, external entities i external schemas,
- mapuje unresolved parent/property/profile/generated sources na `UNKNOWN`
  i visibility limits.

Pelny Maven effective model, aktywacja profili i generated sources sa poza
MVP.

### Java/Spring scan

Istniejace endpoint inventory, Java method slices i use-case traversal sa
referencja dla modelu rol i heurystyk. Nie sa wywolywane przez skaner, jezeli
wewnetrznie korzystaja z unbounded tree/read albo blob search. Feature-owned
agregacja nad plikami z bounded snapshotu dostarcza:

- inventory produkcyjnych i testowych typow w module,
- annotations, extends/implements, package i source references,
- REST entrypoints i reprezentatywne vertical slices,
- role widoczne w istniejacym modelu use-case,
- declared module/internal/external dependencies,
- kandydatow test -> production symbol na podstawie jawnych referencji,
- unresolved calls, ambiguous roles i brakujace test mapping jako
  `INFERENCE` albo `UNKNOWN`, nigdy jako pewnik.

JavaParser nie staje sie nowym shared core. Pelny symbol solver, reflection,
runtime proxy resolution i dynamic event graph sa poza MVP.

### Instruction scan

Skan reuse'uje kanoniczne nazwy i reguly precedence z
`InstructionContextDiscoveryService`, ale inventory/content pobiera wylacznie
przez bounded port. Odkrywa root/path `AGENTS.md`, Copilot instructions i
wskazane dokumenty. Manifest zapisuje fakt, ze dana instrukcja istnieje oraz
ID sanitizowanego excerptu, ale nie jego tekst. Referenced docs sa czytane tylko
po osobnej kanonikalizacji sciezki, w tym samym repo/SHA i w limitowanym scope.
Tresc instrukcji z badanego repo jest niezaufanym materialem; nie moze nadpisac
zasad runtime generatora i nie staje sie automatycznie zatwierdzona decyzja
architektoniczna.

### Lifecycle tresci zrodlowej

- Raw bytes zyja tylko w ograniczonym buforze pojedynczego bounded read i sa
  odrzucane bezposrednio po parsowaniu/redakcji; nie trafiaja do job state.
- Secret redaction zachodzi przed manifestem, UI, activity, reportem i promptem.
- Live `SanitizedEvidenceExcerptStore` przechowuje tylko redagowane fragmenty,
  lacznie maksymalnie 5 MiB, pod evidence ID do konca job TTL.
- `ModuleEvidenceManifest` przechowuje path/symbol/linie, excerpt ID,
  `redactionApplied`, collector/version, limitations i fingerprint
  kanonicznego redagowanego fragmentu, ale nie source text ani raw fingerprint.
- Paged evidence API moze zwrocic limitowany sanitizowany excerpt operatorowi;
  prompt builder wybiera tylko budzetowany podzbior z tego samego store.
- ZIP zawiera jedynie sanitizowany evidence index bez excerpt text. Expiry albo
  restart usuwa store razem z promptem i manifestem.

### Limity MVP

Limity sa backend properties, nie polami requestu. POM-only `module-options`
ma osobny limit 2 000 inventory entries, 100 plikow POM, 2 MiB lacznego
content i 10 sekund oraz zwraca SHA uzyte do discovery. Nie uruchamia Java scan
ani AI.

Pierwsze wartosci pelnego runu:

- maksymalnie 10 000 wpisow inventory w scope,
- maksymalnie 2 000 plikow Java do parsowania,
- maksymalnie 1 MiB jednego pliku tekstowego,
- maksymalnie 25 MiB odczytanej tresci na scan,
- maksymalnie 5 MiB sanitizowanych excerpts w live store,
- maksymalnie 20 000 evidence entries,
- maksymalnie 8 poziomow parent POM,
- maksymalnie 120 sekund deterministic scan,
- maksymalnie 24 000 konserwatywnie estymowanych tokenow calego wejscia AI,
  lacznie z promptem, skillem i artifacts; osobna rezerwa 8 000 tokenow nie
  moze zostac zajeta przez source excerpts,
- maksymalnie 8 wywolan report tools,
- maksymalnie 2 MiB finalnego ZIP.

Osiagniecie limitu daje partial manifest i jawny visibility limit. Brak albo
truncation target POM, brak immutable revision lub brak minimalnego source
scope blokuje review zamiast generowac pozornie kompletny wynik.

Material AI jest wybierany deterministycznie wedlug priorytetu: manifest
summary, konflikty/unknowns, evidence dla entrypointow i granic, a dopiero potem
reprezentatywne excerpts. Przekroczenie budzetu kompresuje albo usuwa nizszy
priorytet i zapisuje coverage; nie ucina dowolnie JSON ani fragmentu symbolu.

### Admission control i live retention

Feature ma wlasny ograniczony executor i properties widoczne w
`input-options`. Domyslnie dopuszcza 2 aktywne runy, kolejke 8 runow i najwyzej
50 live jobow. Przepelnienie daje `429 Too Many Requests`, bez rozpoczecia
odczytu GitLab ani AI.

`AWAITING_REVIEW` ma domyslny TTL 8 godzin. Terminalny job i ZIP maja TTL 24
godziny; snapshot zawiera `expiresAt`/`artifactExpiresAt`, a UI pokazuje te
wartosci przed review i downloadem. Po TTL job przechodzi do `EXPIRED`, usuwa
manifest, prompt i file map; preview/download zwracaja wtedy `410 Gone`.
Krotki tombstone moze pozostac najwyzej godzine przed `404`. Retention nie jest
historia ani gwarancja odzyskania po restarcie.

### Model epistemiczny i fingerprint

Typowany `ModuleEvidenceManifest` jest zrodlem prawdy deterministic contextu.
`AnalysisEvidenceSection` jest jedynie projekcja dla wspolnego UI.

| Typ | Znaczenie | Dopuszczalne zrodlo |
| --- | --- | --- |
| `FACT` | Bezposrednia albo deterministycznie wyprowadzona obserwacja | source reference oraz collector/rule version |
| `INFERENCE` | Niepewna interpretacja | rule albo AI oraz istniejace evidence IDs |
| `DECISION` | Jawny wybor z review | approved spec i server timestamp |
| `EXCEPTION` | Ograniczone odstepstwo | scope, reason, owner i `do-not-extend` |
| `UNKNOWN` | Brak bezpiecznego rozstrzygniecia | powod i visibility limit |

Kazdy evidence entry zawiera stabilne ID, kind, source path, opcjonalny
symbol/line range, commit SHA, content fingerprint, collector ID/version,
derivation rule i limitations. Evidence IDs sa hashami kanonicznego zestawu
tych pol i nie zaleza od kolejnosci skanowania.

Manifest fingerprint jest SHA-256 kanonicznego JSON bez timestampow,
activity, AI proposal i danych UI. Zmiana dowolnego faktu, source revision,
limitu majacego wplyw na coverage albo collector version zmienia fingerprint.
Repo text i pelne pliki nie sa przechowywane w manifescie.

## ASSISTED AI contract

AI jest advisory i uruchamia sie dopiero po zamknieciu deterministic scan.

- Feature przygotowuje manifest oraz ograniczone evidence excerpts jako
  logiczne inline artifacts.
- Nie uzywa SDK attachments ani lokalnych sciezek.
- `preparedPrompt` jest dokladnie tekstem wykonanym przez runtime.
- Prompt jawnie oznacza repo code/comments/instructions jako niezaufane dane.
- Feature wybiera polski runtime skill
  `domain-skill-generation-analyze` przez selected skill root.
- Allowlista zawiera wbudowany `skill`, odczyt reportu i schema-bound zapis
  structured report section; nie zawiera GitLab source tools, zwyklego
  Markdown `upsert`, filesystem, shell ani terminal.
- Model nie dostaje GitLab group, tokenu ani base URL.
- Operator widzi, jaki zakres danych trafia do AI; BASIC nie wykonuje model
  call i nie powoduje data egress.

### Report-first ze structured payload

Obecny `AnalysisReportSection` ma tylko Markdown i meta. Parsowanie z niego
rol, wyjatkow i proposal items byloby kontraktem tekstowym, wiec plan obejmuje
minimalne neutralne rozszerzenie L2:

- opcjonalny `AnalysisReportStructuredPayload` z `schemaId`, `schemaVersion` i
  `JsonNode data`; konstruktor wykonuje defensywna kopie, a odczyt nie wystawia
  mutowalnego aliasu,
- addytywne pole payload w `AnalysisReportSection`, z kompatybilnym
  piecioargumentowym konstruktorem, `NON_NULL` JSON i obsluga starszego JSON bez
  pola,
- neutralny `report_upsert_structured_section` tool,
- neutralne `CopilotStructuredReportSchema` przekazywane w run request i
  rejestrowane session-bound: section ID, schema ID/version i JSON Schema bez
  semantyki feature'a,
- hidden scope eksponujacy modelowi tylko dozwolone section/schema IDs;
  payload spoza scope jest odrzucany przed zapisem,
- platformowy limit 128 KiB, glebokosci 20 i 2 000 JSON nodes, bez
  polymorphic/default typing oraz walidacja zarejestrowanego JSON Schema przed
  zapisem do report store; feature schema naklada wezsze limity pol i list.

Istniejacy Markdown report tool i zachowanie obecnych feature'ow pozostaja bez
zmian. Shared frontend moze ignorowac payload; nie renderuje go jako HTML.

Feature tworzy initial `AnalysisReport` scaffold z zamknieta allowlista:

```text
OBSERVED_ARCHITECTURE
SEMANTIC_PROPOSAL
CONFLICTS_AND_UNKNOWNS
```

Hidden report scope zawiera report ID, feature ID i allowed section IDs.
`SEMANTIC_PROPOSAL` dopuszcza tylko feature-owned schema
`domain-skill-semantic-proposal/v1` z limitem 32 KiB. Model zapisuje przez
structured report tool role assignments, target proposal, validation
proposals, conflicts, confidence, limitations i istniejace `evidenceIds`.
Feature deserializuje
payload ponownie z unknown-fields-rejected, waliduje wszystkie IDs/symbole oraz
buduje `DomainSkillSemanticProposal` i typed review draft. Nie parsuje
Markdownu.

Po walidacji `DomainSkillReportProjector` deterministycznie odtwarza Markdown i
meta wszystkich sekcji z typed proposal oraz manifest summary. W ten sposob
publiczny `AnalysisReport` pozostaje source of truth initial wyniku AI, payload
i prezentacja nie rozjezdzaja sie, a finalna odpowiedz assistant jest tylko
krotkim potwierdzeniem albo diagnostycznym fallbackiem. Renderer nie czyta
payloadu, Markdownu ani final response; dostaje dopiero approved spec.

### Auth i redakcja eventow Copilota

Frontend korzysta z `/api/auth/github/status` i wspolnego katalogu
`/api/analysis/ai/options`. Przy `POST /jobs` feature synchronicznie, jeszcze na
request thread, rozwiazuje niebedacy sekretem auth reference przez
`AnalysisAiAuthRefResolver`. Async job rozwiazuje token dopiero bezposrednio
przed runem, zawsze tworzy `NEW` session i nie zapisuje auth ref ani tokenu w
snapshotcie, activity, promptcie ani package.

Obecny runtime publikuje ograniczone preview user/assistant/reasoning i tool
arguments/results w activity oraz logach. Aby gwarancja generatora byla
prawdziwa, plan dodaje neutralny `CopilotRunContentPolicy` do
`CopilotRunRequest`, przekazuje ja bezposrednio loggerom pre-session/gateway i
rejestruje po session ID dla runtime/tool listeners:

- default `STANDARD` zachowuje dotychczasowe zachowanie konsumentow,
- `METADATA_ONLY` usuwa z activity i logow prompt/content/reasoning, tool
  arguments/results, session context, error message/stack i summaries z nich
  wyprowadzone; dotyczy tez terminalnego catch/logowania invocation failure,
- gateway mapuje failure na generyczny, typed public exception bez raw
  `rootCause.message`, stack ani raw cause; job error nie moze odzyskac tych
  danych downstream,
- pozostaja event IDs/types/timestamps, tool name/success, status code, usage i
  ogolne komunikaty bez tresci.

Zakres obejmuje gateway activity i catch, session/client lifecycle loggers,
tool invocation logging, budget warnings oraz kazdy listener logujacy
arguments/results/exception message. Brak policy dla session ID jest bledem w
trybie wymagajacym `METADATA_ONLY`, nie cichym fallbackiem do `STANDARD`.
Registry izoluje rownolegle sesje, usuwa wpis dopiero po zamknieciu
session/listeners i zachowuje bounded metadata-only tombstone przez grace
period dla late events. Cleanup zachodzi po sukcesie i bledzie, bez cross-talku
ze `STANDARD` runem.

Generator zawsze wybiera `METADATA_ONLY`. Dokladny `preparedPrompt` pozostaje
jawny tylko w live snapshot/UI dla zaufanego operatora i znika wraz z TTL.
Feature nie zapisuje pelnego promptu, source, final response ani reasoning poza
tym live polem i nigdy nie umieszcza ich w package.

Przy brakujacym lub niekompletnym reportcie, timeout, niedostepnym modelu albo
odrzuconej proposal job tworzy jawny warning i przechodzi do manualnego review
z deterministic manifestem.

## Review, generation i validation

### Review gate

Fakty sa read-only. Operator podejmuje decyzje tylko dla:

- nazwy i zakresu bounded contextu,
- wersjonowanego target profile,
- kompletnej `targetArchitectureCard` niezaleznie od trybu,
- nierozstrzygnietych rol i ownerow odpowiedzialnosci,
- zaakceptowania, odrzucenia, zastapienia albo odroczenia inferencji jako
  `UNKNOWN`,
- wyjatkow z polityka `do-not-extend`,
- kuratorowanych template IDs komend walidacyjnych,
- stabilnej nazwy skilla,
- finalnej akcji `GENERATE` albo `STOP`.

Komendy walidacyjne sa renderowane z feature-owned, wersjonowanych template'ow
oraz bezpiecznych wartosci modulu. Review nie przyjmuje dowolnej komendy shell.
Generator nie wykonuje tych komend i nie twierdzi, ze przeszly.

Tylko po poprawnym review z `action=GENERATE` serwer buduje immutable
`ApprovedDomainSkillGenerationSpec`. Spec zawiera decision digest,
`approvedAt`, kompletna target architecture card, manifest fingerprint, commit
SHA oraz wersje generatora, schematu, target profile i template pack. Ta sama
specyfikacja jest jedynym inputem renderera.

Jezeli review potwierdzi, ze modul obejmuje kilka bounded contextow albo
wybrany bounded context wykracza poza jeden modul, MVP zwraca jawne
`STOPPED` albo `UNSUPPORTED_SCOPE`, nie tworzy package i nie pozwala sprowadzic
takiego przypadku do sztucznie pewnego profilu jednego modulu.

### Renderer

Renderer jest czysta funkcja:

```text
approved spec + versioned profile + versioned templates -> canonical file map
```

File map ma stala allowliste:

```text
.github/skills/<skill-name>/SKILL.md
.github/skills/<skill-name>/references/module-boundary.md
.github/skills/<skill-name>/references/architecture-and-placement.md
.github/skills/<skill-name>/references/validation.md
.github/skills/<skill-name>/generation-evidence.json
```

MVP nie generuje skryptow. Nazwa pliku nigdy nie pochodzi z AI ani source
path. `skill-name` jest walidowanym slugiem. Renderer normalizuje UTF-8, LF,
ordering, YAML i Markdown escaping. User/AI text nie moze domknac frontmatter,
code fence ani utworzyc nowej sciezki.

Wygenerowany `SKILL.md` opisuje miekka maszyne stanow:

```text
SCOPE -> DISCOVER -> BOUNDARY -> CLASSIFY -> PLACE
      -> IMPLEMENT -> VALIDATE -> REPORT
```

Kazdy stan ma sciezke do `BLOCKED` oraz jawne powroty po odkryciu nowych
informacji. Skill nie udaje twardego enforcementu; testy architektury, hooks i
CI pozostaja osobnymi przyszlymi mechanizmami.

### Validator

Deterministyczny validator sprawdza:

- frontmatter i zgodnosc z output profile,
- allowlistowane, relatywne paths bez traversal,
- wewnetrzne references pozostajace w skill directory,
- istnienie symboli i evidence IDs uzytych w mocnych twierdzeniach,
- provenance kazdego twierdzenia jako fact albo decision,
- zatwierdzenie wszystkich wyjatkow,
- brak `UNKNOWN` wyrenderowanego jako twarda regula,
- spojnosc i osiagalnosc maszyny stanow,
- sciezke do `BLOCKED` i `REPORT`,
- zgodnosc profile/schema/template versions,
- checksums i manifest fingerprint,
- zakazane sekrety, permissions, scripts i pre-approved shell,
- limity rozmiaru package.

AI nie jest finalnym validatorem. Blad decyzji mozliwy do poprawienia wraca z
validation findings do `AWAITING_REVIEW`; blad techniczny konczy job jako
`FAILED`. Tylko successful validation prowadzi do `COMPLETED`.

### ZIP i provenance

Preview, validator i ZIP korzystaja z tej samej file map. Nie ma drugiej
sciezki renderowania.

ZIP ma:

- canonical entry order,
- stale entry timestamps,
- UTF-8 i reprodukowalne checksums,
- sanitizowany `Content-Disposition`,
- `Content-Type: application/zip`,
- `Cache-Control: no-store`,
- limit rozmiaru.

`generation-evidence.json` zawiera:

- niebedacy sekretem source locator: provider, fingerprint kanonicznego GitLab
  hosta (bez tokenu i user-info), project path, repository ID, requested ref,
  resolved commit SHA i module path,
- version/fingerprint snapshotu Operational Context uzytego do scope,
- manifest fingerprint i wersje generator/profile/template/schema,
- sanitizowany evidence index dla kazdego evidence ID faktycznie uzytego w
  wygenerowanym twierdzeniu albo zatwierdzonej decyzji: path, symbol/linie,
  collector/version i content fingerprint,
- zatwierdzone decisions/exceptions, server timestamp, deklarowany reviewer
  label i decision digest,
- checksums `SKILL.md` i plikow `references/`.

Nie zawiera wlasnego checksumu, tokenow, auth ref, hidden context, promptu,
pelnych zrodel, odpowiedzi modelu ani reasoning. Checksum samego
`generation-evidence.json` oraz calego ZIP znajduje sie w artifact metadata
joba, obliczanym po zamknieciu file map. Evidence wymagane przez wygenerowane
twierdzenia nie jest cicho ucinane; przekroczenie limitu package blokuje
validation i wymaga ograniczenia zatwierdzonego zakresu.

## Frontend

Nowy ekran reuse'uje:

- `AnalysisJobPollingService`,
- `analysis-feature-aside`,
- `analysis-steps-panel`,
- `analysis-evidence-panel`,
- `analysis-report-panel`,
- `analysis-report-section-content`,
- `analysis-report-meta`,
- `analysis-result-header`,
- `analysis-result-tabs`,
- `MarkdownContentComponent`,
- wspolne usage/cost i AI model option utilities,
- shell i landing routing.

Feature-owned pozostaja:

- formularz system/repository/ref/module/mode,
- podsumowanie effective scan limits i data egress,
- rozdzielony widok facts/inferences/unknowns,
- review editor,
- conflict/stale revision handling,
- validation report,
- file-map preview i download.

Facts sa read-only. Inference i decision maja rozne oznaczenia. Markdown
preview uzywa sanitizowanego shared renderera, bez `[innerHTML]`.

Live job pozostaje in-memory. `AWAITING_REVIEW`, preview i ZIP nie przezywaja
restartu procesu ani TTL; UI pokazuje ograniczenie i czas wygasniecia. MVP nie
rejestruje feature'a w local history, nie zapisuje envelope i nie udostepnia
continuation/import/export. Ponowne pobranie jest mozliwe tylko z zywego joba
przed `artifactExpiresAt`.

## Reuse-first i capability gap analysis

| Potrzeba | Istniejacy mechanizm | Reuse bez zmian | Mala neutralna zmiana | Feature-owned nowa czesc |
| --- | --- | --- | --- | --- |
| Source scope | Operational Context catalog/resolvers | tak | brak | request validation i source selection |
| Immutable revision | GitLab REST adapter | czesciowo | wymagany `GitLabRepositoryRevisionPort` | run-scoped source snapshot |
| Bounded tree/read | obecny `GitLabRepositoryPort` nie egzekwuje network/memory cap | nie dla skanera | wymagany paged/byte-bounded port | scan orchestration i limits |
| Zastany tree/read/cache | `GitLabRepositoryPort` | tak dla obecnych konsumentow, nie dla skanera | brak zmiany kontraktu/cache | brak |
| Instructions | reguly `InstructionContextDiscoveryService` | czesciowo; bez jego unbounded source calls | brak | bounded discovery i manifest projection |
| REST/Java flow | endpoint/method use-case services i ich heurystyki | tylko tam, gdzie nie wykonuja unbounded/search calls | brak | module-wide aggregation nad bounded snapshotem |
| Maven module | GitLab file reads | tak | brak | secure parser i coverage |
| Evidence UI | `shared.evidence.*` | tak jako projekcja | brak | typed manifest |
| Steps/activity/usage | `shared.ai.*` | tak | brak | feature lifecycle mapping |
| AI runtime | `aiplatform.copilot.runtime` | tak | session-bound `CopilotRunContentPolicy` i registry, L3, default backward-compatible | preparation i auth flow |
| AI report | `AnalysisReport` | czesciowo | opcjonalny structured payload i schema-bound report tool | schema validator, typed mapper i deterministic presentation projector |
| Tools | selected skill/runtime allowlist | tak | neutralny structured report tool | default-deny feature config |
| Human review | brak | nie | brak | state, API i UI |
| Renderer/templates | brak | nie | brak | pure renderer i resources |
| Validator | brak | nie | brak | structural/evidence/security validators |
| ZIP | standard Java ZIP support | tak technicznie | brak | canonical file map i HTTP artifact |
| History/import/export | istniejace mechanizmy local workspace | nie w MVP | brak | brak |
| Polling/UI panels | shared Angular services/components | tak | tylko addytywne inputs, jesli udowodnione | page/review/preview |

## Lista konsumentow i regresja L2/L3

### GitLab revision i bounded-read capabilities

Produkcjny implementer nowych `GitLabRepositoryRevisionPort` i
`GitLabBoundedRepositoryReadPort`:

- `GitLabRestRepositoryAdapter`.

Bezposrednim konsumentem nowych portow jest tylko
`features.domainskillgeneration.source`. `GitLabRepositoryPort` nie dostaje
nowej metody, dlatego `TestGitLabRepositoryPort` i piec lokalnych fakes w
`ChangeVerificationJobServiceTest`, `AnalysisEvidenceCollectorTest`,
`GitLabJavaMethodSliceServiceTest`, `InstructionContextDiscoveryServiceTest`
oraz `GitLabJavaInterfaceImplementorResolverTest` pozostaja bez zmian.

Potwierdzeni bezposredni produkcyjni konsumenci starego portu to:

- `GitLabMcpTools`, `GitLabRepositorySearchController` i
  `GitLabRepositoryFilesByPathApiService`,
- `GitLabRepositorySearchService`, `GitLabRepositoryEndpointService`,
  `InstructionContextDiscoveryService`, `GitLabOpenApiEndpointSliceService` i
  `GitLabJavaMethodSliceService`,
- `GitLabEndpointUseCaseContextService`, `GitLabEndpointUseCaseSourceSession` i
  `GitLabJavaMethodUseCaseContextService`,
- `ChangeVerificationSourceDiscoveryService`, `FlowExplorerSnippetCardService`,
  `GitLabDeterministicEvidenceProvider`,
  `RuntimeConfigurationCodeUsageSearchService` i
  `RuntimeConfigurationDeepPreflightService`.

`GitLabRepositoryTreeService` ma dwoch bezposrednich konsumentow:
`GitLabRestRepositoryAdapter` i `GitLabSourceResolveService`. Po ich grafie
regresja obejmuje GitLab API/MCP oraz Incident Analysis, Flow Explorer, Change
Verification i Runtime Configuration Verification. `GitLabNamedExactRepositoryAdapter`
implementuje inny `GitLabExactRepositoryPort` i nie jest konsumentem starego
portu.

Produkcyjny adapter i fixture nowego feature'a maja pelne testy resolve, page
stop, byte hard-stop i cancellation behavior; istniejace metody, cache i
model-facing tool schemas pozostaja bez zmian.

### Structured report payload

Addytywne pole `AnalysisReportSection` i nowy report tool wymagaja regresji:

- report factories/mappers Incident Analysis, Flow Explorer, Change
  Verification i Runtime Configuration Verification,
- `CopilotReportTools`, `CopilotReportSessionStore` i execution gateway,
- cztery job snapshot/controller paths oraz envelope/persisters Incident,
  Flow, Change i Runtime Configuration,
- `AnalysisRunHistoryService`, local chat restore i Runtime Configuration
  import, mimo ze generator nie uzywa history/import/export,
- shared report API/model tests, `analysis-import-export.utils` i Angular report
  components. Normalizer ma zachowac payload jako inert JSON, a renderery nie
  moga umieszczac go w DOM jako HTML.

Stary piecioargumentowy konstruktor i JSON bez payload pozostaja wspierane.
Istniejace sekcje maja `payload=null`, a istniejace tools zachowuja schema i
zachowanie.

### Session-bound Copilot content policy

Zmiana `CopilotRunRequest`, prepared session, bezposrednich logger parameters i
nowego registry po `sessionId` dotyka assemblerow:

- `CopilotIncidentRunRequestFactory`,
- `FlowExplorerCopilotRunRequestAssembler`,
- `ChangeVerificationCopilotRunRequestAssembler`,
- `RuntimeConfigurationCopilotRunRequestAssembler`.

Policy jest rejestrowana przed session/tool listeners i usuwana w `finally`.
Obowiazuje w `CopilotSdkExecutionGateway` activity oraz terminal catch,
`CopilotSessionEventLogger`, `CopilotClientLifecycleLogger`,
`CopilotToolInvocationLoggingListener`, warningach
`CopilotToolBudgetPolicy`, event publisher, feedback/evidence listenerach oraz
feature-owned GitLab/Database/Change evidence capture, ktore moga logowac albo
publikowac arguments, results lub exception message. Publiczna propagacja
obejmuje `AnalysisJobFacade`, job services Flow/Change/Runtime Configuration,
local continuation/history i `ApiExceptionHandler`. Kompatybilne konstruktory
ustawiaja `STANDARD`,
wiec output i activity obecnych feature'ow nie zmieniaja sie. Canary tests
dowodza, ze `METADATA_ONLY` nie pozostawia tresci w zadnej application-owned
sciezce log/activity/public exception ani kontrolowanym JUL, stderr i lokalnym
logu SDK/CLI. Dopiero wtedy generator moze wybrac ten tryb. Regresja obejmuje
rownolegle `STANDARD` i `METADATA_ONLY`, cleanup po sukcesie/bledzie oraz late
events w grace period bez cross-talku.

### Shared frontend

Reuse wspolnych komponentow wymaga regresji Incident Analysis, Flow Explorer,
Change Verification, Runtime Configuration Verification i wspolnych
component/service tests. Brak delty kontraktu oznacza audyt potwierdzajacy
reuse, a nie modyfikowanie konsumentow. Nie tworzymy generycznego module
scanner ani review engine dla jednego feature'a.

## Zakres

Plan obejmuje:

- neutralne resolve GitLab ref do commit SHA,
- neutralne paged inventory i byte-bounded text reads,
- nowy sibling backend i frontend,
- input/module options,
- BASIC deterministic scan jednego modulu,
- typed manifest i evidence projection,
- manual review,
- target/output profile v1,
- deterministic renderer, validator, preview i ZIP,
- ASSISTED semantic proposal AI,
- neutralny structured report payload i session-bound metadata-only content
  policy,
- admission control oraz live retention/TTL,
- testy, architecture guard i dokumentacje wynikowego runtime.

## Non-goals

Plan swiadomie nie obejmuje:

- lokalnego filesystemu jako source,
- klonowania albo checkoutu repozytorium,
- wielu repozytoriow lub wielu modulow w jednym skillu,
- jednego bounded contextu obejmujacego wiele modulow,
- modulu zawierajacego wiele bounded contextow,
- innych jezykow, frameworkow i build tools,
- pelnego Maven effective model, profili i generated sources,
- dynamicznej analizy runtime,
- wykonywania Maven/test/CI commands,
- generowania skryptow, hooks i hard gates,
- behavior eval coding-agenta,
- filesystem/shell/terminal tools w Copilot runtime,
- follow-up chat i continuation,
- local history, import/export JSON i odtwarzania ZIP po restarcie lub TTL,
- durable recovery joba oczekujacego na review,
- automatycznego wykrywania i scalania recznie zmienionego skilla,
- zapisu do target repozytorium,
- merge request, publikacji i instalacji skilla,
- automatycznego drift scheduler/CI,
- ogolnego RBAC albo multi-user job ownership.

## Ograniczenia, bezpieczenstwo i ryzyka

### Trust model

MVP zaklada lokalne albo rownowazne trusted single-operator deployment.
Platforma nie ma ogolnego RBAC ani ownership jobow. Nieprzewidywalny UUID nie
jest zabezpieczeniem kodu ani ZIP-a w srodowisku multi-user. Uruchomienie
feature'a w takim srodowisku wymaga osobnego planu security/authorization i
nie moze byc przedstawiane jako wspierane przez ten MVP.

### Untrusted repository input

- Repo code, comments, POM, Markdown i instructions sa danymi, nie
  poleceniami dla generatora.
- XML parser ma twardo wylaczone DTD/XXE i external resource loading.
- Nie wykonujemy source scripts, build plugins ani validation commands.
- Prompt injection fixtures sa wymagane dla Java comments, POM i `AGENTS.md`.
- Sekrety i credential-like values sa redagowane przed publicznym manifestem,
  UI, AI, reportem i package; provenance zaznacza zastosowana redakcje.
- Generator BASIC nie loguje tresci plikow. ASSISTED pozostaje niedostepny,
  dopoki `CopilotRunContentPolicy.METADATA_ONLY` oraz canary SDK/CLI nie usuna
  content/reasoning/tool previews ze wszystkich application-owned activity,
  runtime/tool listeners i loggers oraz kontrolowanych lokalnych outputow CLI.
  Pelny `preparedPrompt` jest celowo widoczny tylko w live snapshot/UI z TTL.

### External scope i mutacje

- Publiczny input nie moze wybrac arbitralnego base URL, group ani tokenu.
- Repository musi byc osiagalne przez dokladna relacje katalogowa
  `system -> codeSearchScope -> repository`.
- GitLab calls sa read-only.
- AI i renderer nie maja mozliwosci zapisu do repozytorium.
- ZIP download jest jedyna zewnetrzna forma artefaktu.

### Ryzyka jakosci

- Static analysis nie widzi calej semantyki Spring proxy, reflection i
  dynamic events; luki musza zostac `UNKNOWN`.
- Czesty legacy pattern moze wygladac jak target; target zawsze wymaga review.
- Zbyt duzy manifest moze obnizyc jakosc proposal; obowiazuja coverage i
  prompt budgets.
- Manual review moze stac sie formalnoscia; UI wymaga aktywnych decyzji dla
  konfliktow i nie pozwala zatwierdzic brakujacych pol.
- Soft skill nie gwarantuje zachowania agenta; behavioral claim pozostaje
  niezweryfikowany do czasu osobnego eval runnera.
- Restart procesu albo TTL usuwa live review i artefakt; limitation jest
  widoczne i nie jest ukrywane za historia local workspace.

## Kompatybilnosc, migracja i rollback

- Wszystkie publiczne endpointy sa nowe i addytywne.
- Nie ma migracji DB. `AnalysisReportSection` dostaje addytywne, nullable pole
  structured payload; stary konstruktor i starszy JSON pozostaja wspierane.
- Manifest, proposal, approved spec, validation report, structured payload i
  output profile maja jawne `schema` i `version`.
- Unsupported version jest odrzucana; nie ma best-effort parsowania.
- Nowe `GitLabRepositoryRevisionPort` i `GitLabBoundedRepositoryReadPort` nie
  zmieniaja `GitLabRepositoryPort` ani jego test doubles.
- `CopilotRunContentPolicy` ma default `STANDARD`; tylko generator jawnie
  wybiera `METADATA_ONLY`.
- MVP nie wprowadza pozornej backendowej feature flag sterujacej statyczna
  trasa Angulara. Route/landing sa dodawane dopiero z kompletnym BASIC.
- Tryb ASSISTED ma osobna capability availability w `input-options`; moze
  zostac operacyjnie wylaczony przy zachowaniu BASIC i tej samej route.
- Pelny rollback wymaga ponownego wdrozenia/revertu kodu i bundle'a. Nie
  istnieja zewnetrzne mutacje ani migracje danych wymagajace osobnego rollbacku.

## Kryteria akceptacji

- Operator wybiera katalogowy system, repozytorium, ref i jeden modul Maven.
- Przypadek multi-bounded-context albo cross-module jest jawnie blokowany, a
  nie upraszczany do niepopartego profilu.
- Ref zostaje rozwiazany do commit SHA, a caly run czyta ten sam snapshot.
- Skan uzywa tylko paged inventory i byte-bounded reads po SHA; nie wywoluje
  blob search ani zastanych eager tree/read paths.
- Module discovery jest POM-only, ograniczone i pokazuje uzyty commit SHA.
- BASIC dziala bez Copilota i bez data egress do modelu.
- BASIC i ASSISTED wymagaja tej samej kompletnej typed target architecture card.
- Manifest ma stabilne evidence IDs, provenance, fingerprint i visibility
  limits.
- UI rozroznia facts, inferences, decisions, exceptions i unknowns.
- ASSISTED zapisuje schema-bound structured report payload i dodaje tylko
  typed, evidence-linked proposal; Markdown nie jest parsowany jako kontrakt.
- Awaria AI prowadzi do manualnego review zamiast utraty deterministic result.
- `preparedPrompt` w live snapshot jest identyczny z wykonanym promptem, a
  metadata-only application activity/logging/listeners nie zawieraja
  prompt/content/reasoning/tool/error previews.
- Publiczny job error/exception w `METADATA_ONLY` jest generyczny i nie zachowuje
  raw provider message, stack ani cause z canary.
- ASSISTED jest niedostepny, jezeli canary test nie obejmuje kontrolowanego
  SDK/CLI output albo wykryje content leak; BASIC pozostaje dostepny.
- Bez poprawnego review z akcja `GENERATE` finalny package nie powstaje;
  `DEFER_AS_UNKNOWN` moze wymusic `STOPPED`.
- Stale albo rownolegle review nie uruchamia drugiego generation.
- Renderer nie przyjmuje raw model response ani report markdown.
- Ta sama approved spec daje identyczna file map i checksums.
- Validator odrzuca martwe references, unsupported claims, unsafe paths,
  niespojny workflow, sekrety i zakazane permissions.
- ZIP jest dostepny tylko po successful validation i ma reprodukowalna
  zawartosc.
- ZIP zawiera `SKILL.md`, trzy references i `generation-evidence.json`, bez
  raw source/prompt/tokenow, za to z audytowalnym source locator, uzytym
  evidence index i decyzjami.
- Nie nastepuje zapis do repozytorium ani wykonanie komendy.
- UI pokazuje partial data, warnings, data egress i visibility limits.
- Pollowany snapshot nie zawiera pelnego manifestu; evidence jest stronicowane.
- Admission control odrzuca nadmiarowe starty, a UI pokazuje TTL joba i ZIP.
- MVP nie zapisuje historii/importu/exportu i nie udaje durable workflow.
- Nowy feature nie importuje sibling feature'a.
- Consumer regression, backend/FE tests, architecture guard i build
  produkcyjny przechodza.

## Macierz testow

### Integrations i source

- [ ] `GitLabRepositoryRevisionPort.resolveRevision` dla branch, tag i full SHA.
- [ ] URL encoding project/ref oraz mapping odpowiedzi do pelnego SHA.
- [ ] 403/404/429/5xx i timeout mapowane bez wycieku raw response.
- [ ] Paged inventory pobiera najwyzej zadana strone, respektuje cursor/page
  cap i przerywa sie po limicie/timeoucie.
- [ ] Bounded read nie buforuje ponad `maxBytes + 1`, odrzuca binary/LFS i
  anuluje HTTP body po hard-stop.
- [ ] Jeden job uzywa resolved SHA we wszystkich inventory/content calls.
- [ ] Skaner nigdy nie wywoluje `searchCandidateFiles`, blob search ani
  zastanych unbounded list/read methods.
- [ ] Istniejacy cache nie miesza moving ref, SHA ani projektu; jego kontrakt
  nie ulega zmianie.
- [ ] Repository ID spoza relacji `system -> codeSearchScope -> repository`
  jest odrzucane.
- [ ] `modulePath` spoza dozwolonych `pathPrefixes` jest odrzucane.
- [ ] POM-only module discovery ma osobny limit/timeout i zwraca uzyty SHA.
- [ ] Path traversal, encoded separators, drive path, NUL i control chars sa
  odrzucane.

### Maven, Java i instructions

- [ ] Root, nested reactor i standalone module fixtures.
- [ ] Malformed/truncated POM oraz brak target POM.
- [ ] DTD/XXE/external entity fixture nie wykonuje zewnetrznego odczytu.
- [ ] Safe parent POM i parent wychodzacy poza repo.
- [ ] Unresolved parent/property/profile/generated source daje unknown/limit.
- [ ] REST endpoint, service/port/repository/mapper/domain/external roles.
- [ ] Ambiguous role, parse failure i unresolved method call.
- [ ] Produkcyjne i test roots oraz evidence-linked test mapping.
- [ ] Max files/bytes/evidence/time daje partial coverage albo blocker zgodnie
  z kontraktem.
- [ ] Prompt injection w Java comment, POM, Markdown i `AGENTS.md` pozostaje
  danymi.
- [ ] Secret redaction zachodzi przed manifestem/UI/AI, ustawia
  `redactionApplied`, a referenced docs nie wychodza poza kanoniczny repo scope.
- [ ] Raw bytes nie trafiaja do job state; sanitized excerpt store respektuje
  5 MiB/TTL i jest usuwany przy expiry/restart.

### Manifest

- [ ] Stable ordering, evidence IDs i canonical fingerprint.
- [ ] Zmiana source commit/fact/collector version zmienia fingerprint.
- [ ] Timestamp/activity/AI ordering nie zmienia fingerprintu.
- [ ] Publiczny content fingerprint powstaje z redagowanego excerptu; raw text
  i raw fingerprint nie trafiaja do manifestu, API ani ZIP.
- [ ] Duplicate ID, missing provenance i invalid source range sa odrzucane.
- [ ] Inference wskazuje tylko istniejace evidence IDs.
- [ ] Test presence nie jest prezentowane jako test execution success.
- [ ] Instructions presence nie jest automatycznie target decision.
- [ ] Projekcja do `AnalysisEvidenceSection` nie staje sie source of truth.
- [ ] Polling snapshot zawiera summary/coverage, a paged endpoint zwraca
  stabilne strony sanitizowanego evidence bez duplikatow.

### Job, API i review

- [ ] Request validation i cross-field BASIC/ASSISTED przez MockMvc.
- [ ] `POST /jobs` zwraca `202` i natychmiastowy snapshot.
- [ ] Success, empty, partial, stopped, unsupported, expired i failure state dla
  kazdego kroku.
- [ ] Poprawne przejscia state machine i timestamps/revisions.
- [ ] Review jest dozwolone tylko w `AWAITING_REVIEW`.
- [ ] Review nie moze zmienic facts ani manifestu.
- [ ] Stale revision/fingerprint daje `409`.
- [ ] Concurrent approvals uruchamiaja jeden renderer.
- [ ] Ten sam `reviewRequestId` z identycznym payload digest jest idempotentny;
  ten sam ID z inna trescia daje `409`.
- [ ] Missing decision, invalid exception i command template sa odrzucane.
- [ ] BASIC wymaga kompletnej typed target architecture card; ASSISTED tylko
  proponuje jej wartosci i nie omija zadnego pola review.
- [ ] `DEFER_AS_UNKNOWN` wymaga uzasadnienia i wymusza `STOP`, jezeli profil
  potrzebuje tej decyzji.
- [ ] Potwierdzony multi-bounded-context/cross-module scope blokuje package.
- [ ] Fixable validation error moze wrocic do review; technical error daje
  stabilny `FAILED`.
- [ ] ZIP przed `COMPLETED` jest niedostepny.
- [ ] Download ma poprawne headers, po expiry daje `410` i nie ujawnia stack
  trace.
- [ ] Admission control respektuje active/queue limits, odrzuca nadmiar przez
  `429` i nie rozpoczyna external calls.
- [ ] Review/artifact TTL, `EXPIRED`, tombstone cleanup i max retained jobs nie
  przeciekaja promptu, manifestu ani file map po expiry.

### Renderer, validator i ZIP

- [ ] Golden fixture jednego referencyjnego modulu daje oczekiwana file map.
- [ ] Ta sama approved spec daje te same bytes i checksums.
- [ ] YAML, Markdown, backticks, fences, links i control chars sa escapowane.
- [ ] Skill name/path nie moze wyjsc poza fixed root.
- [ ] Validator negative fixtures pokrywaja kazda bramke.
- [ ] State workflow jest osiagalny i ma `BLOCKED` oraz `REPORT`.
- [ ] Unknown nie jest renderowane jako twarda regula.
- [ ] Unsupported evidence claim i niezatwierdzony exception sa odrzucane.
- [ ] ZIP ma fixed entries/order/timestamps, size cap i brak zip slip.
- [ ] Secret-like fixture nie trafia do package.
- [ ] `generation-evidence.json` ma source/opctx locator, uzyty evidence index,
  decisions/exceptions i file checksums bez raw source ani samochecksumu.

### ASSISTED AI

- [ ] BASIC nie wywoluje runtime Copilota.
- [ ] Prompt/artifacts/digest/coverage, konserwatywny token budget i exact
  prepared prompt w live snapshot.
- [ ] Runtime skill name, frontmatter, selected root i polska tresc.
- [ ] `AnalysisReportSection` structured payload jest addytywny; stary
  konstruktor/JSON/report tool i obecni konsumenci zachowuja zachowanie.
- [ ] Structured payload size/depth/node limits i brak polymorphic typing sa
  egzekwowane przed zapisem.
- [ ] Payload niezgodny z session-bound JSON Schema nie trafia do report store.
- [ ] Initial report scaffold, allowed section/schema/version hidden scope i
  completeness.
- [ ] Efektywna allowlista jest podzbiorem zarejestrowanych `skill`, report read
  i structured upsert tools, bez zwyklego Markdown upsert,
  GitLab/filesystem/shell/terminal.
- [ ] Feature policy, unknown tool/section/schema rejection i budzet report
  calls.
- [ ] Prompt nie zawiera tokenu, group ani base URL.
- [ ] Valid structured payload mapuje sie do typed proposal/review draft, a
  Markdown powstaje deterministycznie bez parsowania tekstu.
- [ ] Missing section, unknown field/symbol/evidence ID i invalid payload sa
  odrzucane.
- [ ] Final assistant response nie zastepuje reportu jako source of truth.
- [ ] Timeout/unavailable runtime daje manual fallback i visibility limit.
- [ ] Auth ref jest rozwiazany na request thread, token dopiero przed `NEW` run
  i nie trafia do snapshotu/package.
- [ ] `METADATA_ONLY` usuwa content/reasoning/tool/error previews z activity i
  log mapping oraz raw cause/message z public exception; `STANDARD` nie zmienia
  zachowania obecnych assemblerow.
- [ ] Rownolegle `STANDARD` + `METADATA_ONLY` nie maja registry cross-talku;
  cleanup dziala po sukcesie/bledzie, a late event respektuje privacy tombstone.
- [ ] Raw response/report nie sa inputem renderera.

### Frontend

- [ ] Input options i exact request mapping.
- [ ] BASIC/ASSISTED fields oraz AI auth/model options tylko w ASSISTED.
- [ ] System/repository/ref/module selection i validation errors.
- [ ] Start oraz polling bez overlap.
- [ ] Polling pauzuje na `AWAITING_REVIEW` i wznawia sie po review.
- [ ] Facts sa read-only; inferences, decisions, exceptions i unknowns sa
  rozroznialne.
- [ ] Evidence navigation i source references.
- [ ] Review validation, stale `409`, retry i concurrent click protection.
- [ ] AI failure pokazuje manual fallback.
- [ ] Partial/empty/warning/failed/stopped/unsupported/expired/completed states.
- [ ] Preview jest XSS-safe i korzysta z `MarkdownContentComponent`.
- [ ] Download jest aktywny tylko po successful validation.
- [ ] Shared aside/steps/activity/evidence/usage/report zachowuja semantyke.
- [ ] Shell, route i landing mapping bez obietnicy runtime feature flag.
- [ ] TTL/artifact expiry i brak history recovery sa widoczne.
- [ ] Keyboard accessibility i responsive layout.

### Architecture i regresja

- [ ] `PackageDependencyGuardTest` obejmuje nowy sibling.
- [ ] Brak sibling imports i reverse dependencies.
- [ ] GitLab MCP/API/Incident/Flow/Change/Runtime Config regression.
- [ ] Structured report payload/report tool ma backward-compatible backend,
  JSON i shared frontend regression.
- [ ] Copilot content policy ma `STANDARD` regression i `METADATA_ONLY`
  canary redaction tests dla gateway catch/activity, session/client lifecycle,
  tool invocation, budget, pozostalych listeners, public exception, job error,
  JUL, stderr i lokalnych `process-*.log` SDK/CLI.
- [ ] Shared Angular component/service consumers pozostaja zieloni.
- [ ] Generator nie rejestruje local history/import/export/continuation.

### Pelna weryfikacja

- [ ] `mvn -q clean test`
- [ ] `npm --prefix frontend test -- --watch=false`
- [ ] `npm --prefix frontend run build`
- [ ] `mvn -q -DskipTests package`

## Kroki

- [x] **Krok 1: Read-only preflight L2/L3.** Bez edycji produkcyjnego kodu
  wyliczyc wszystkie implementacje i konsumentow revision/bounded-read, report
  oraz Copilot run/log contracts; uruchomic ich testy baseline i zapisac
  zastane failures. Potwierdzic GitLab commit lookup, eager tree/read behavior,
  stan pokrycia kompatybilnosci starszego report JSON, wszystkie miejsca
  content preview oraz SDK/CLI log output i zasadnosc
  `outputLanguage=pl-PL`. Audyt wykazal brak
  testu starego JSON, lokalne outputy SDK/CLI i brak podstaw dla stalego
  `pl-PL`; sa zapisane jako jawne delty/decyzje. Wynik:
  zaktualizowana consumer list i evidence baseline. Weryfikacja: wyniki testow,
  search inventory i diff dokumentu. Kryterium: brak zmian produkcyjnych, brak
  nieznanego konsumenta i osobna zgoda przed Krokiem 2.

- [ ] **Krok 2: Immutable i bounded GitLab capabilities.** Dodac wymagane
  `GitLabRepositoryRevisionPort` oraz `GitLabBoundedRepositoryReadPort` z
  implementacja REST w `GitLabRestRepositoryAdapter`, bez zmiany
  `GitLabRepositoryPort` i jego cache. Wynik: requested ref jest kotwiczony w
  pelnym SHA, inventory jest paged, a text read ma byte hard-stop. Weryfikacja:
  branch/tag/SHA, page/cursor/timeout/cancellation, `maxBytes + 1`, binary/LFS,
  URL/error mapping, adapter, compatibility smoke tree/raw po full SHA wobec
  docelowej wersji GitLaba i GitLab consumer regression oraz
  `PackageDependencyGuardTest`. Kryterium: kontrakty nie znaja semantyki
  feature'a, egzekwuja network/memory limits, a zastane odczyty dzialaja bez
  zmian.

- [ ] **Krok 3: Kontrakt i skeleton feature'a.** Dodac lokalne `AGENTS.md`,
  feature ID/slug, input/POM-only module options, request/light snapshot/paged
  evidence DTO, cienkie controllery, thread-safe state, admission control,
  retention/TTL i package guard. Nie dodawac runtime feature flag ani route UI.
  Wynik: `POST` zwraca `202`, `GET` lekki snapshot, a walidacja odrzuca
  niebezpieczny scope. Weryfikacja: MockMvc, facade/state, queue/TTL/error
  mapping i architecture tests. Kryterium: brak sibling imports, publicznych
  credential fields i nieograniczonego live state.

- [ ] **Krok 4: Deterministyczny scan i manifest.** Dostarczyc immutable
  source snapshot, secure Maven parser, Java/Spring/instruction aggregation,
  redakcje i live sanitized excerpt store przed manifestem,
  `ModuleEvidenceManifest`, stable IDs/fingerprint, coverage i visibility
  limits. Wynik: jeden modul daje powtarzalny typed manifest bez AI.
  Weryfikacja: Maven/Java/instruction fixtures,
  XXE/path/injection/redaction/lifecycle/limit tests, zakaz blob search i
  unbounded reads, canonicalization i paged evidence. Kryterium: caly scan
  uzywa jednego SHA i bounded portu, raw bytes nie trafiaja do state,
  partial/unknown sa jawne, a minimalne braki blokuja review.

- [ ] **Krok 5: Kompletny backend BASIC.** Dodac review z optimistic revision,
  payload-digest idempotency, `GENERATE|STOP`, `DEFER_AS_UNKNOWN`, profil
  `github-project-skill-v1`, wymagana typed target architecture card, approved
  spec, kuratorowane validation templates, pure renderer, validator,
  provenance, canonical file map i ZIP. Wynik: BASIC
  przechodzi scan -> review -> validated package bez Copilota albo konczy sie
  jawnym stopped/unsupported wynikiem. Weryfikacja: concurrency/idempotency,
  golden files, negative validator fixtures, deterministic ZIP/provenance i
  secret/path tests. Kryterium: ZIP powstaje tylko z approved spec po successful
  validation i jest dostepny tylko do `artifactExpiresAt`.

- [ ] **Krok 6: Frontend BASIC happy path.** Dodac route/page/form, source i
  module selection, shared run panels, typed review editor, stop/unknown states,
  TTL, validation view, preview i download. Zarejestrowac feature w
  shellu/landing dopiero po zamknieciu pionu. Wynik: operator moze wykonac caly
  BASIC z UI. Weryfikacja: API/page/component tests, polling
  pause/resume/no-overlap, stale review, expiry, XSS-safe preview,
  error/partial/accessibility states i build. Kryterium: UI nie pozwala
  edytowac facts ani pobrac niewalidowanego lub wygaslego ZIP.

- [ ] **Krok 7: Neutralne kontrakty report L2 i privacy L3.** Dodac opcjonalny
  `AnalysisReportStructuredPayload`, session-bound JSON Schema validation przed
  report store oraz `CopilotRunContentPolicy` przekazywana bezposrednio do
  pre-session/gateway loggers i przez registry po session ID do tool listeners.
  Defaulty zachowuja starsze zachowanie. Wynik:
  platforma zapisuje tylko ograniczony, zgodny payload i wykonuje
  metadata-only run bez semantyki generatora. Weryfikacja: stare
  konstruktory/JSON/report tools, wszystkie report factories/import-export i
  cztery Copilot assemblery; size/depth/schema rejection; canary secret przez
  gateway catch/activity, session/client loggers, invocation/budget/pozostale
  listeners, public exception/job error, JUL, stderr i lokalne logi SDK/CLI;
  concurrent mixed-policy isolation, success/error cleanup i late events.
  Kryterium: consumer regression przechodzi, `STANDARD` jest bez zmian, a
  `METADATA_ONLY` nie emituje ani nie propaguje canary w application-owned
  log/activity/error ani kontrolowanym lokalnym outputcie SDK/CLI. Brak tej
  gwarancji pozostawia ASSISTED niedostepny i nie jest omijany zmiana promptu.

- [ ] **Krok 8: ASSISTED report-first semantic proposal.** Dodac polski
  runtime skill, auth-ref flow, prompt/artifacts z token budget, initial report,
  feature schema validator/typed mapper, deterministic report projector,
  metadata-only activity i manual fallback. Wynik: ASSISTED proponuje decyzje
  z evidence IDs, ale nie generuje plikow i nie zmienia facts. Weryfikacja:
  preparation/auth/assembler/skill/structured-report tests, invalid payload,
  timeout fallback, allowlista, content policy oraz secret/data-egress audit.
  Kryterium: report payload jest source of truth, Markdown nie jest parsowany,
  renderer nie zalezy od AI output/report, a BASIC nie wykonuje Copilot call.

- [ ] **Krok 9: Hardening, architecture diff i dokumentacja stanu.** Wykonac
  pelna macierz testow, build produkcyjny, porownac finalny diff z baseline i
  conformance delta, zaktualizowac kanoniczne dokumenty, lokalne instrukcje i
  pozostaly backlog. Wynik: feature jest opisany jako rzeczywisty stan, nie
  tylko plan. Weryfikacja: wszystkie komendy pelnej weryfikacji, consumer
  audit, package dependency diff i manual UI smoke. Kryterium: brak nowego
  driftu, wszystkie wykonane kroki maja dowody i plan moze przejsc na `done`.

## Aktualizacja dokumentacji po implementacji

Krok 9 aktualizuje adekwatnie:

- [ ] root `AGENTS.md` i `features/AGENTS.md` tylko dla faktycznie nowych,
  stabilnych invariants,
- [ ] nowe `features/domainskillgeneration/AGENTS.md`,
- [ ] `docs/architecture/product-direction.md`,
- [ ] `docs/architecture/system-overview.md`,
- [ ] `docs/architecture/key-decisions.md` z zatwierdzona session privacy
  boundary i rollbackiem ASSISTED,
- [ ] `docs/architecture/package-dependencies.md`,
- [ ] nowy `docs/architecture/domain-skill-generation-runtime-flow.md`,
- [ ] `docs/architecture/codex-continuation-guide.md`,
- [ ] `docs/README.md`,
- [ ] frontend shell/landing documentation,
- [ ] `docs/plans/open-work.md` dla behavioral eval, drift, publication/MR,
  multi-user authorization, multi-module, historii i artifact recovery.

Po dostarczeniu wynikowy runtime i invariants trafiaja do `architecture/`.
Plan nie staje sie kanonicznym opisem dzialajacego feature'a.

## Materialy referencyjne

- [GitLab Commits API - retrieve a commit](https://docs.gitlab.com/api/commits/#retrieve-a-commit)
- [GitLab Repository Files API](https://docs.gitlab.com/api/repository_files/)
- [GitLab Repositories API](https://docs.gitlab.com/api/repositories/)
- [GitLab Search API](https://docs.gitlab.com/api/search/)
- [Adding agent skills for GitHub Copilot](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/customize-cloud-agent/add-skills)
- [GitHub Copilot hooks reference](https://docs.github.com/en/copilot/reference/hooks-reference)
- [GitHub Copilot SDK Node.js reference](https://github.com/github/copilot-sdk/blob/main/nodejs/README.md)
