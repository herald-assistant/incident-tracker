# Runtime Configuration Tool Workbench

Status: in-progress

Source need: [runtime-configuration-tool-workbench](../needs/runtime-configuration-tool-workbench.md)

## Potrzeba / dlaczego

Named GitLab source i bezpieczny pipeline Runtime Configuration sa obecnie
widoczne tylko posrednio przez wynik joba. Potrzebny jest operator-facing,
readonly Workbench, ktory pozwoli zweryfikowac pobranie, mapowanie,
anonimizacje i dokladny AI-safe input bez tworzenia nowego kanalu raw data.

## Klasyfikacja

Poziom `L2`: zmiana dodaje feature-owned diagnostic API, nowy ekran Tool
Workbench i kolejnego konsumenta istniejacych serwisow pipeline'u. Nie zmienia
model-facing tool schema, job result ani security invariant. Gdyby zakres
wymagal ujawnienia raw content lub przeniesienia transformacji do shared/API,
nalezy podniesc zmiane do `L3` i ponownie zatwierdzic plan.

## Baseline

- `/gitlab` testuje neutralne GitLab repository/source capability oparte o
  glowne polaczenie kodowe, ale nie pokazuje named configuration repositories.
- `/operational-context` pokazuje katalog i code-search scopes.
- Runtime Configuration job rozwiazuje allowlistowane repozytorium i
  `internal-system`, czyta trzy role plikow, parsuje je, buduje immutable
  deterministic context, renderuje sanitizowane artefakty i dopiero wtedy
  uruchamia AI.
- `RuntimeConfigurationDeterministicContextService` jest wspolnym punktem
  source/parse/diff, a `RuntimeConfigurationPromptPreparationService`
  wlascicielem promptu i AI artifacts.
- `BASIC` omija deep service; `DEEP` korzysta z preflightu,
  `RuntimeConfigurationDeepContextService`, scope/ref/path limits i ownership.
- Publiczny job snapshot oraz export sa sanitizowane. Nie ma endpointu
  diagnostycznego, ktory pokazuje wszystkie etapy przed AI.

## Conformance Delta

Cel zmiany:
readonly preview tego samego pipeline'u w Tool Workbench.

Warstwa bedaca wlascicielem:
orchestration preview, DTO i endpoint pozostaja w
`features.runtimeconfigurationverification.workbench`; named GitLab pozostaje
w `integrations.gitlab`; frontend dostaje osobny ekran diagnostyczny.

Zmiana publicznego API/DTO:
nowy `POST /api/runtime-configuration-verification/workbench/preview`.
Input zawiera tylko mode, repositoryId, systemId, sourceBranch, targetBranch i
opcjonalny codeRef. Output zawiera source coverage, sanitizowany deterministic
context, anonymization summary, prompt/artifacts i visibility limits.

Zmiana context/evidence:
bez zmian w algorytmie; preview reuse'uje produkcyjne serwisy.

Zmiana prompt/artifacts/skills:
bez zmian. Workbench pokazuje output istniejacego preparation service i nie
uruchamia skilla ani Copilota.

Zmiana tools/policy/hidden scope/budzetu:
bez zmian. `BASIC` nie wywoluje deep dependencies; `DEEP` reuse'uje preflight
i backendowy scope. Preview nie daje modelowi ani UI dowolnego connection
scope'u.

Zmiana report/result:
bez zmian w job result i report. Preview ma osobny kontrakt diagnostyczny.

Zmiana job state/persistence/export:
bez zmian; preview nie tworzy joba, historii ani exportu.

Zmiana shared FE/UX:
nowa route i pozycja Tool Workbench, z reuse wzorca tool selector,
request/response JSON, loading/error/copy. Bez kopiowania komponentow
feature-result.

Nowe lub usuniete zaleznosci:
brak nowych kierunkow. `features.*` nadal zalezy od integracji/platformy,
`api.*` nie importuje feature'a, a reusable warstwy nie znaja Workbencha.

Konsumenci:
Runtime Configuration job pozostaje glownym konsumentem pipeline'u; Workbench
jest drugim. Regresja musi potwierdzic identyczny deterministic context i
artifact contents dla tego samego inputu.

Kompatybilnosc:
zmiana addytywna. Istniejace endpointy, export schema, historia i UI feature'a
pozostaja bez zmian.

## Proponowane rozwiazanie

Dedykowany ekran `Tool Workbench / Runtime Configuration Pipeline` pod
`/runtime-configuration-tools` pokazuje cztery perspektywy:

1. `Source acquisition` — resolved repository/system/directory, file role,
   path, branch, status, commit, timestamp, size, truncation/error code.
2. `Mapping` — dokument/profile, kanoniczna sciezka, typ, change kind,
   sensitivity, diff/finding references.
3. `Anonymization` — liczniki oraz per-node reprezentacja
   `PSEUDONYMIZED`, `SUPPRESSED` albo `STRUCTURE_ONLY`; nigdy raw value/hash.
4. `AI input` — dokladny prompt, lista artefaktow, ich rozmiar/truncation i
   tresc JSON przekazywana przez production preparation service.

Panel `DEEP scope` pokazuje wynik preflightu, used ref/ref source,
repo/path-prefix scope, Operational Context entities, ownership i visibility
limits. Nie duplikuje katalogowego Workbencha; linkuje do niego dla detailu.

## Zakres

- feature-owned preview API i serwis orchestration,
- reuse produkcyjnego deterministic/deep/preparation pipeline'u,
- jawny model decyzji anonimizacji wyprowadzony z sanitizowanego contextu,
- nowa route, sidebar item, formularz i cztery panele diagnostyczne,
- JSON copy oraz bezpieczne statusy bledow,
- backend/frontend tests, package dependency review i dokumentacja.

## Non-Goals

- raw source preview,
- wywolanie AI,
- zmiana parsera, klasyfikatora lub pseudonimizera,
- edycja konfiguracji,
- generyzacja feature pipeline'u do `shared`,
- zmiana GitLab/Operational Context tools.

## Ograniczenia i ryzyka

- Najwazniejsze ryzyko to przypadkowa serializacja raw source albo exception
  cause. Response musi byc budowany wylacznie z sanitizowanych modeli.
- Prompt moze byc duzy; obowiazuja istniejace limity artifact/manifest i
  jawne truncation.
- Preview wykonuje realne readonly odczyty GitLab i w `DEEP` code grounding,
  wiec UI musi pokazac czas, etap i visibility limits, ale nie tworzy joba.
- Pseudonimy sa run-local; dwa preview nie musza miec identycznych tokenow.
- Nie wolno dodac `api.* -> features.*`; endpoint zostaje przy feature.

## Kryteria akceptacji

- wszystkie cztery perspektywy sa dostepne z Tool Workbench,
- odpowiedz API i render UI nie zawieraja przygotowanych wartosci testowych
  oznaczonych jako sekrety/raw,
- source coverage i AI artifacts sa generowane przez te same serwisy co job,
- `BASIC` ma zero deep interactions,
- `DEEP` pokazuje scope/ref/ownership i bezpieczny partial result,
- 401/403/404/timeout oraz truncation maja czytelny, bezpieczny stan,
- test architektoniczny i pelne backend/frontend buildy przechodza.

## Kroki

- [x] Krok 1: Dodac feature-owned kontrakt i backend preview API. Zbudowac
  `workbench` orchestration nad scope resolverem, deterministic context,
  opcjonalnym deep contextem i prompt preparation. Response ma zawierac
  source coverage, sanitized mapping, anonymization decisions, prompt,
  artifacts i visibility limits, bez startu AI/joba/persistence. Dodac testy
  service/MockMvc: ten sam pipeline, `BASIC` bez deep calls, `DEEP` scope,
  walidacja inputu, large/truncated input oraz end-to-end raw-secret leak
  probe.
  Zrealizowano synchroniczny readonly endpoint
  `POST /api/runtime-configuration-verification/workbench/preview`.
  `RuntimeConfigurationWorkbenchPreviewService` reuse'uje scope resolver,
  deterministic context, opcjonalny deep context i production prompt
  preparation. Response rozdziela source acquisition, sanitizowane mapping,
  per-node decisions `PSEUDONYMIZED`/`SUPPRESSED`/`STRUCTURE_ONLY`/
  `NOT_PRESENT`, deep context, dokladny prompt, artifact contents/summaries
  oraz visibility limits. Preview nie uruchamia AI, joba ani persistence.
  Snapshot sanitizer jest reuse'owany jako defense-in-depth przed serializacja
  i przygotowaniem AI inputu; blad deterministic/preparation jest mapowany na
  stabilny `RUNTIME_CONFIGURATION_WORKBENCH_PREVIEW_FAILED` bez cause message,
  a blad DEEP daje bezpieczny partial preview.
  Dowod: `RuntimeConfigurationWorkbenchPreviewServiceTest` pokryl BASIC bez
  deep calls, DEEP scope/artifact, 5000 wezlow z pelnym mappingiem i
  truncated AI artifact, bezpieczny partial oraz serializacyjna probe raw
  sekretow; `RuntimeConfigurationWorkbenchPreviewControllerTest` pokryl
  normalizacje, walidacje i bezpieczny blad HTTP. Celowane testy wraz z
  `RuntimeConfigurationAiArtifactServiceTest`,
  `RuntimeConfigurationVerificationPortabilityTest` i
  `PackageDependencyGuardTest` przeszly. Regresja wszystkich testow
  `*RuntimeConfiguration*` oraz named GitLab zakonczyla sie powodzeniem;
  `git diff --check` nie wykazal bledow.

- [x] Krok 2: Dodac ekran `Runtime Configuration Pipeline` w Tool Workbench.
  Zarejestrowac route/sidebar/capability info, selector repo/system/branch/mode
  oraz sekcje Source acquisition, Mapping, Anonymization, AI input i
  warunkowy DEEP scope. Dodac request/response JSON, copy, loading/error,
  czytelne etykiety `FETCHED METADATA`, `DERIVED`, `AI-SAFE` i brak raw-value
  affordance. Testy: HttpTestingController, formularz, render wszystkich
  etapow, deep blocker/partial, truncation, copy, accessibility i shell route.
  Zrealizowano osobny ekran pod `/runtime-configuration-tools`, wpis w grupie
  Tool Workbench i route capability info. Formularz korzysta z backendowej
  allowlisty repozytoriow, `internal-system`, branchy i trybow. Wynik jest
  podzielony na piec perspektyw: source metadata, kanoniczny mapping,
  per-node decyzje anonimizacji, dokladny prompt/artifacts oraz warunkowy
  DEEP preflight/code scope/ownership. UI nie ma affordance dla raw values,
  pokazuje provenance labels, visibility limits, partial/blocker, truncation,
  bezpieczne bledy oraz collapsible request/response z akcjami copy. Dla
  duzych odpowiedzi tabela ogranicza render do 500 wierszy i zachowuje pelny
  bezpieczny payload w JSON response.
  Dowod: `RuntimeConfigurationVerificationApiService` ma test dokladnego POST
  preview; `RuntimeConfigurationWorkbenchPageComponent` ma testy BASIC,
  wszystkich perspektyw, raw-secret leak probe, DEEP partial/blocker,
  ownership, truncation, walidacji branch pair, bezpiecznej serializacji bledu
  i dostepnych akcji. `app.spec.ts` pokrywa lazy route, sidebar oraz capability
  info. Pelny frontend: 36 plikow testowych / 215 testow przeszlo; produkcyjny
  `npm run build` przeszedl i wygenerowal aktualny bundle. `git diff --check`
  nie wykazal bledow.

- [ ] Krok 3: Hardening i zgodnosc konsumentow. Porownac preview z jobem dla
  tego samego deterministic/deep fixture, przetestowac 401/403/404/timeout,
  puste/niepelne Operational Context, limity payloadu i serializacje bledow.
  Wykonac architecture diff, `PackageDependencyGuardTest`, testy feature'a i
  pelne testy frontendowe.

- [ ] Krok 4: Zaktualizowac kanoniczna dokumentacje wynikowego stanu i
  produkcyjny bundle. Uzupelnic system overview, runtime flow, Tool Workbench
  opis oraz root/lokalne instrukcje tylko w zakresie faktycznie wdrozonego
  zachowania. Dowod: `mvn -q clean test`,
  `npm --prefix frontend test -- --watch=false`,
  `npm --prefix frontend run build`, `mvn -q -DskipTests package` i
  `git diff --check`.

## Bramka zatwierdzania

Plan jest draftem. Kazdy krok wymaga jawnego `go`; zatwierdzenie kroku nie
obejmuje kolejnych. Ujawnienie raw configuration, zmiana security boundary
albo przeniesienie pipeline'u do reusable warstwy wymaga aktualizacji planu i
ponownego zatwierdzenia.
