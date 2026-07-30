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
`POST /api/runtime-configuration-verification/workbench/preview` przyjmuje
mode, repositoryId, systemId, sourceBranch, targetBranch i opcjonalny codeRef.
Po breaking redesignzie output zawiera tylko wygasajacy `previewId`, summary,
liczniki, metadata artefaktow i visibility limits. Source coverage,
stronicowany mapping/anonymization, DEEP context, exact prompt i pojedyncze
artefakty sa pobierane przez dedykowane endpointy snapshotu.

Zmiana context/evidence:
bez zmian w algorytmie; preview reuse'uje produkcyjne serwisy.

Zmiana prompt/artifacts/skills:
artifact format v2 zastepuje verbose per-document manifesty jednym
hierarchicznym `configuration-tree.yaml` oraz kolumnowym `changes.json`.
Workbench nadal pokazuje output produkcyjnego preparation service i nie
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
kontrakt Workbench preview i nazwy jego artefaktow sa zmienione breaking,
bez aliasow i adapterow v1. Job API, export schema, historia i publiczny wynik
Runtime Configuration pozostaja bez zmian.

## Proponowane rozwiazanie

Dedykowany ekran `Tool Workbench / Runtime Configuration Pipeline` pod
`/runtime-configuration-tools` pokazuje piec perspektyw:

1. `Source acquisition` — resolved repository/system/directory, file role,
   path, branch, status, commit, timestamp, size, truncation/error code.
2. `Mapping` — stronicowane wezly z filtrem changed-only: dokument,
   kanoniczna sciezka, typ, change kind i sensitivity.
3. `Anonymization` — liczniki oraz stronicowana per-node reprezentacja
   `PSEUDONYMIZED`, `SUPPRESSED` albo `STRUCTURE_ONLY`; nigdy raw value/hash.
4. `AI input` — metadata artefaktow w summary; dokladny prompt i tresc
   pojedynczego artefaktu sa pobierane tylko po jawnej akcji.
5. `DEEP scope` — scoped context, code grounding, blockers i ownership.

Sanitizowany snapshot jest przechowywany w pamieci przez 10 minut, ma limit
32 wpisow i po wygasnieciu zwraca bezpieczne 404. Przelaczanie perspektyw nie
powtarza odczytu GitLaba.

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
- Prompt moze byc duzy; format v2 ma limity drzewa/changes/deep context,
  syntaktycznie poprawny marker truncation i jawne visibility limits.
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

- [x] Krok 3: Breaking redesign kontraktu preview i AI artifacts bez
  kompatybilnosci wstecznej. Zastapic monolityczny response lekkim,
  wygasajacym sanitizowanym snapshotem `previewId`, summary oraz lazy
  endpointami source/mapping/anonymization/DEEP/artifact/exact AI input.
  Mapping i anonymization maja byc stronicowane, a UI summary-first nie moze
  automatycznie materializowac pelnego JSON/promptu w DOM. Zastapic verbose
  manifesty kompaktowym artifact format v2, ktory raz zachowuje hierarchie,
  wszystkie changed/unchanged paths, typy, sensitivity i bezpieczna
  reprezentacje source/target; bogaty material ma dotyczyc tylko
  differences/findings/references. Usunac stare DTO/pola i stare nazwy
  artefaktow zamiast utrzymywac aliasy.
  Baseline z realnego Workbench fixture: response 2 100 659 znakow / 26 312
  linii, `mapping` 346 962, `anonymization` 292 877, prompt 415 312 znakow,
  przy 855 nodes, 136 differences, 103 findings i 90 references. Prototyp
  kompaktowej reprezentacji zachowujacej semantyke zajal 114 297 znakow
  zamiast 639 839 dla mapping + anonymization.
  Kryteria: initial summary ponizej 50 000 znakow dla tego fixture, exact
  prompt ponizej 150 000 znakow, wszystkie 855 paths i pseudonimy zachowane,
  zero raw-secret leak, bounded TTL/cache, bezpieczne 404 po expiry, brak
  ponownego odczytu GitLaba miedzy zakladkami. Porownac preview z jobem dla
  tego samego fixture, przetestowac pagination, changed-only, expiry,
  401/403/404/timeout, partial DEEP, truncation i serializacje bledow.
  Wykonac architecture diff, `PackageDependencyGuardTest`, testy feature'a i
  pelne testy frontendowe.
  Zrealizowano bez warstwy kompatybilnosci: initial response nie serializuje
  mappingu, decyzji anonimizacji, promptu ani artifact contents. Zwraca
  `previewId`, `expiresAt`, summary/liczniki oraz metadata artefaktow.
  Sanitizowany snapshot ma TTL 10 minut, limit 32 wpisow i bezpieczny kod
  `RUNTIME_CONFIGURATION_WORKBENCH_PREVIEW_NOT_FOUND`; lazy endpointy
  udostepniaja source, mapping, anonymization, DEEP, exact AI input i wybrany
  artefakt. Mapping/anonymization sa stronicowane do 200 wpisow, z domyslnym
  changed-only dla mappingu.
  Artifact v2 usuwa manifest-index, per-document manifesty i
  differences-and-findings. `configuration-tree.yaml` zachowuje hierarchie,
  granice dokumentu/profile, wszystkie changed/unchanged nodes, typy,
  sensitivity, cardinality oraz run-local pseudonimy przez opisane kody.
  `changes.json` przechowuje kolumnowo differences/findings/references.
  Truncation pozostawia poprawny YAML/JSON i nie modyfikuje deterministic
  contextu. Skille `runtime-configuration-basic-review` i
  `runtime-configuration-deep-review` zostaly przepisane bez nazw v1 i
  natywnie interpretuja kolumny, legendy oraz hierarchie formatu v2.
  Dowod: test reprezentatywny 855 nodes / 136 differences / 103 findings /
  90 references zachowuje ostatni node i ostatnie ID bez truncation, a exact
  prompt ma 115 891 znakow (limit 150 000). Initial summary ma test limitu
  50 000 znakow. Testy pokrywaja raw-secret leak, pagination, changed-only,
  TTL, eviction, bezpieczne 404, BASIC bez DEEP call, partial DEEP i brak
  ponownego deterministic/GitLab build. Przeszly celowane testy feature'a,
  `PackageDependencyGuardTest`, pelne `mvn -q clean test` (959 testow),
  36 plikow / 216 testow frontendu, produkcyjny build i package.

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
