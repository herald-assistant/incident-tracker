# Runtime Configuration Verification

Status: done

Source need: [runtime-configuration-verification](../needs/runtime-configuration-verification.md)

## Potrzeba / dlaczego

Administrator potrzebuje przed wdrozeniem porownac konfiguracje wybranego
komponentu pomiedzy dwoma branchami srodowiskowymi i dostac nie tylko diff,
ale tez bezpieczna ocene mozliwych przeoczen. Konfiguracja mieszka na innej
instancji GitLaba niz kod, laczy wielodokumentowy YAML z `global.var` i
komponentowym `local.var`, a jej surowe wartosci moga zawierac sekrety.

Nowy workflow jest osobnym feature'em analitycznym. Nie nalezy do Incident
Analysis ani do Change Verification, bo ma wlasny input, deterministic
context, reguly bezpieczenstwa i kontrakt wyniku.

## Klasyfikacja zmiany

Poziom: **L3 - architektoniczny**.

Uzasadnienie:

- powstaje nowy publiczny feature i kontrakt joba - L1,
- potrzebna jest nowa reusable capability odczytu plikow z nazwanej,
  dodatkowej instancji GitLaba oraz rozszerzenie wspolnego UI/historii - L2,
- dochodzi nowa granica credentiali i wieloinstancyjne polaczenie z GitLabem,
  ktore nie moze naruszyc obecnego polaczenia do kodu - L3.

Zmiana ma byc addytywna. Rollback polega na wylaczeniu feature'a i usunieciu
konfiguracji jego repozytoriow/nazwanej instancji bez migracji istniejacych
konsumentow `analysis.gitlab`.

## Kontrakt discovery i zalozenia MVP

- Stabilny feature id: `runtime-configuration-verification`.
- Slug UI: `/runtime-configuration-verification`.
- API: `/api/runtime-configuration-verification/jobs`.
- Uzytkownik: administrator, release/deployment engineer albo developer.
- Decyzja: kontynuowac wdrozenie, poprawic konfiguracje albo wyjasnic roznice.
- Input: `mode`, `repositoryId`, `systemId`, `sourceBranch`, `targetBranch`,
  opcjonalne `codeRef`, `model` i `reasoningEffort`.
- `mode` przyjmuje `BASIC` albo `DEEP`.
- `systemId` wskazuje kanoniczny Operational Context `internal-system`.
  Backend rozstrzyga katalog konfiguracji z jednoznacznego
  runtime/deployment signalu systemu. Katalog nie jest publicznym inputem,
  drugim bytem ani drugim ownership targetem.
- Branch jest zgodny z `^(?:dev\d|zt00\d)$`; branche musza byc rozne.
- Dowolna para obslugiwanych branchy jest dozwolona. Backend nie zgaduje
  kierunku promocji na podstawie nazwy.
- `repositoryId` mapuje po stronie backendu na nazwane polaczenie GitLab i
  pelna sciezke projektu. Base URL, projekt i token nie sa publicznym inputem.
- Dla wybranego komponentu porownywane sa:
  - `/global.var`,
  - `/<resolvedConfigurationDirectory>/local.var`,
  - dokladnie jeden z
    `/<resolvedConfigurationDirectory>/application.yml.kv` albo
    `/<resolvedConfigurationDirectory>/application.yaml.kv`.
- Brak obu wariantow pliku aplikacyjnego oznacza niepelny wynik. Obecnosc obu
  oznacza niejednoznacznosc wymagajaca review.
- Surowa zawartosc jest przetwarzana tylko w pamieci i nie trafia do promptu,
  snapshotu, evidence, activity, historii ani eksportu.
- AI dostaje pelny zanonimizowany manifest obejmujacy zmienione i niezmienione
  parametry, zachowany schemat wielodokumentowego YAML, structural diff i
  deterministic findings. Nigdy nie dostaje dostepu do surowych plikow ani
  tools instancji GitLaba z konfiguracja; `DEEP` moze dostac tylko ograniczone
  tools GitLaba z kodem.
- Manifest zachowuje granice dokumentow/profile, zagniezdzenie, statyczne
  sciezki kluczy, typy, ksztalt kolekcji, zrodla definicji i reference graph.
  Nie przenosi surowych komentarzy ani wartosci skalarnych.
- Dla wartosci niewrazliwych dopuszczalny jest pseudonim stabilny tylko
  wewnatrz pojedynczego runu. Backend moze uzyc keyed HMAC wyłącznie
  wewnetrznie do przypisania losowego `valueToken`; HMAC i staly hash nie
  trafiaja do AI, snapshotu ani historii. Dla sekretow korelacja jest
  ograniczona do relacji tego samego klucza source-target.
- Dynamiczne klucze map wygladajace jak identyfikatory sa pseudonimizowane;
  statyczne nazwy parametrow pozostaja czytelne do groundingu w kodzie.
- Wynik deterministyczny i druga opinia AI sa rownorzednymi produktowymi
  czesciami rezultatu. AI nie jest rendererem diffu, a deterministic diff nie
  jest tylko ukrytym wsadem do promptu.
- `BASIC` uzywa Operational Context tylko do wyboru `internal-system` i
  rozstrzygniecia katalogu konfiguracji; sama analiza obejmuje deterministic
  diff oraz AI second opinion bez operational enrichment i code tools.
- `DEEP` zachowuje identyczny configuration result, a dodatkowo uzywa
  Operational Context, code-search scopes, focused GitLab code reads i
  ownership resolution do opisania znaczenia funkcjonalnego.
- Wybrany komponent jest kanonicznym `internal-system` z Operational Context.
  Rozstrzygniety katalog konfiguracji pochodzi z jego runtime/deployment
  signalu; nie powstaje osobny katalogowy byt komponentu uruchomieniowego ani
  feature-owned mapa system ownership.
- Feature-owned repository profile wskazuje tylko named connection i project
  path repozytorium konfiguracji. Dalsze dotkniete systemy wynikaja z relacji
  Operational Context i potwierdzenia w kodzie.
- Kazdy wewnetrzny system czytany w `DEEP` musi miec poprawny code-search
  scope. Istniejaca walidacja
  `INTERNAL_SYSTEM_WITHOUT_CODE_SEARCH_SCOPE` pozostaje quality gate.
- `codeRef` jest opcjonalnym preferowanym refem dla repozytoriow kodu. Gdy go
  brak albo nie istnieje w konkretnym repozytorium, resolver moze uzyc
  faktycznego default branch projektu tylko z jawnym visibility limit i
  pokazaniem uzytego refu. Nie wolno przedstawic go jako potwierdzonej wersji
  wdrozonej.
- Deterministic diff pozostaje dostepny, gdy wykonanie AI sie nie powiedzie;
  ocena ma wtedy status `INCOMPLETE`.
- MVP nie ma follow-up chatu ani mutacji reportu.
- MVP zapisuje bezpieczny snapshot w lokalnej historii i wspiera wersjonowany,
  read-only import/export wyniku.
- Przykladami przekazanymi przez uzytkownika nie zasilamy repozytorium ani
  fixture testowych. Testy uzywaja malych, syntetycznych i pozbawionych
  sekretow danych.

## Proponowane rozwiazanie

Feature wykona read-only pipeline:

```text
request/preflight
  -> odczyt dokladnych plikow z obu branchy
  -> parsing i bezpieczna normalizacja
  -> strukturalny diff i analiza lancuchow odwołan
  -> deterministic findings
  -> BASIC: zanonimizowany configuration context dla AI
  -> DEEP: Operational Context match
       -> affected systems/integrations/processes/bounded contexts
       -> code-search scopes i focused code discovery
       -> ownership/handoff resolution
       -> zanonimizowany configuration + operational + code context dla AI
  -> report-first druga opinia i rekomendacja
  -> feature result + historia/eksport
```

Fakty, wyprowadzenia i interpretacja pozostaja rozdzielone:

1. **known facts** - pliki, metadane, sciezki kluczy, typy oraz informacja o
   dodaniu/usunieciu/zmianie,
2. **derived facts** - rozwiazane odwolania, wartosci efektywne, konflikty i
   deterministic findings oraz - dla `DEEP` - dopasowania katalogowe,
   code-search scopes, miejsca uzycia konfiguracji i resolved ownership,
3. **AI interpretation** - ocena znaczenia roznic, mozliwy scenariusz pomylki
   i sugerowana czynnosc; w `DEEP` takze opis wplywu funkcjonalnego.

Feature-owned status ma deterministic floor. AI moze podniesc poziom ryzyka,
ale nie moze:

- obnizyc `INCOMPLETE`, gdy wymagany plik jest niedostepny albo niepoprawny,
- usunac deterministic finding,
- przedstawic zanonimizowanej lub nierozwiazanej wartosci jako potwierdzonej.

AI zapisuje tylko sekcje nalezace do drugiej opinii. Deterministyczne sekcje,
pozycje diffu i findings sa tworzone przez backend, pozostaja niemutowalne dla
modelu i sa weryfikowane po zakonczeniu runu. Kazda obserwacja AI powinna
odwolywac sie do stabilnych identyfikatorow deterministic differences albo
findings. Obserwacja bez takiego oparcia jest hipoteza i musi byc tak
oznaczona.

### Publiczny wynik

Feature-owned result powinien zawierac:

- `status`: `NO_BLOCKING_ANOMALIES`, `REVIEW_REQUIRED`,
  `LIKELY_CONFIGURATION_ERROR` albo `INCOMPLETE`,
- `scope`: repozytorium, komponent, oba branche i bezpieczne revision refs,
- `mode`: wykonany `BASIC` albo `DEEP`,
- `coverage`: stan kazdego wymaganego pliku po obu stronach,
- `deterministicResult`:
  - `status`,
  - `differenceSummary` z licznikami per plik, change kind i severity,
  - `differences` jako strukturalne, filtrowalne pozycje bez sekretow,
  - `findings` z kategoria, severity, confidence, facts, references i
    suggested check,
- `aiSecondOpinion`:
  - stan wykonania AI,
  - conclusion i confidence,
  - observations rozdzielajace grounded interpretation od hypothesis,
  - referencje do `differenceId` i `findingId`,
  - priorytetowa lista czynnosci do ludzkiej weryfikacji,
- `agreement`: informacja, gdzie wynik deterministyczny i AI sa zgodne,
  rozbiezne albo AI nie moglo dokonac oceny,
- `deepAnalysis` dla `DEEP`:
  - `primarySystem` jako wybrany `internal-system` i runtime/deployment signal
    uzyty do rozstrzygniecia `resolvedConfigurationDirectory`,
  - `affectedSystems`, `integrations`, `processes` i `boundedContexts`,
  - `codeGrounding` z scope, repozytorium, uzytym refem, plikiem, symbolem,
    dopasowanym kluczem i confidence,
  - `functionalImpacts` z referencjami do difference/finding/context/code,
  - `ownership` z primary/partner owners, resolution path, handoff reason i
    visibility limits,
  - `coverage` pokazujace systemy bez scope'u, refu albo dostepnego kodu,
- `visibilityLimits`,
- `prompt`, `usage` oraz neutralny `AnalysisReport`.

Stabilne sekcje reportu:

- `verification-summary`,
- `deterministic-differences`,
- `deterministic-findings`,
- `ai-second-opinion`,
- `recommended-human-checks`,
- `affected-systems-and-context` - tylko `DEEP`,
- `functional-impact-and-code-grounding` - tylko `DEEP`,
- `ownership-and-handoff` - tylko `DEEP`,
- `visibility-and-gaps`.

Sekcje zakresu, deterministic result, affected systems, code grounding i
resolved ownership sa przygotowane przez backend. AI moze zapisac tylko
`ai-second-opinion`, `recommended-human-checks` oraz narracyjna interpretacje
`functional-impact-and-code-grounding`; nie moze zmienic referencji, coverage,
ownershipu ani visibility gaps dodanych przez backend.

### Tryb `DEEP`: operational i code grounding

Preflight `DEEP` sprawdza:

- Operational Context jest wlaczony i katalog nie ma blokujacych bledow,
- `systemId` wskazuje istniejacy system `kind=internal-system`,
- system ma jednoznaczny, bezpieczny runtime/deployment signal katalogu
  konfiguracji,
- bazowy system ma code-search scope z co najmniej jednym repozytorium,
- code GitLab jest dostepny niezaleznie od configuration GitLab,
- dla kazdego repozytorium mozna rozstrzygnac i pokazac faktycznie uzyty ref.

Backend wykorzystuje `integrations.operationalcontext` do deterministic
groundingu. Wybrany `internal-system` jest bazowym systemem bez dodatkowej
heurystyki. Dopiero identyfikacja innych dotknietych systemow wykorzystuje
sciezki kluczy, bezpieczne wartosci, nazwy integracji, flag, kolejek i
endpointow. Kandydat bez potwierdzenia pozostaje kandydatem.

Code discovery jest ograniczone do repozytoriow i `searchMode/pathPrefixes`
z code-search scopes. Najpierw wykonuje deterministic wyszukiwanie miejsc
uzycia zmienionej konfiguracji, m.in. exact property path, canonical relaxed
binding variants, `@ConfigurationProperties`, `@Value` i potwierdzone symbole.
AI moze wykonac tylko focused reads kandydackich plikow/metod oraz ograniczone
dodatkowe wyszukiwanie w tym samym scope. Nie dostaje dostepu do repozytorium
konfiguracji.

Ownership jest rozwiazywany przez istniejacy
`OperationalContextOwnershipResolution`. Owner pochodzi z systemu albo bounded
contextu. Nazwa repozytorium, komponentu lub teamu podobna do klucza nie jest
wystarczajacym dowodem. Wynik pokazuje primary owners, partner owners,
resolution path i powod handoffu albo `UNKNOWN/AMBIGUOUS`.

### Deterministic reguly MVP

Reguly nie uznaja kazdej roznicy za blad. Powinny wykrywac:

- brak, nieczytelnosc, truncation i blad skladni pliku,
- dodanie/usuniecie klucza i zmiane typu,
- duplikat albo konflikt definicji,
- nierozwiazane, cykliczne albo niejednoznaczne odwolanie,
- zmiane lancucha zrodla wartosci,
- marker obcego obslugiwanego srodowiska w wartosci docelowej,
- podejrzanie niezmieniona wartosc sklasyfikowana jako srodowiskowa,
- dodanie/usuniecie/zmiane wartosci wrazliwej bez jej ujawniania,
- roznice `global.var` wykorzystywane przez wybrany komponent,
- pozostale roznice globalne jako osobny kontekst, bez automatycznego
  przypisywania ich do komponentu.

Klasyfikacja wartosci wrazliwych korzysta z konserwatywnej polityki nazw
sciezek i typow. Porownanie surowych wartosci wrazliwych odbywa sie w pamieci;
publiczny model przechowuje tylko stan `ADDED`, `REMOVED`, `CHANGED` albo
`UNCHANGED_MASKED`.

Deterministic context zawiera dodatkowo sanitized typed tree dla wszystkich
parametrow, nie tylko pozycji diffu. Drzewo zachowuje dokument/profile context,
zagniezdzenie map i list, typy, cardinality/shape, source precedence i
reference graph. Pseudonimy wartosci sa run-local i nie sa surowymi hashami.
Komentarze YAML oraz dynamiczne klucze sklasyfikowane jako dane sa usuwane lub
pseudonimizowane przed zbudowaniem loggable/persistable DTO.

Parser `.var` jest best-effort dla obserwowanej skladni zagniezdzonych blokow,
map i przypisan. Konstrukcja, ktorej nie da sie jednoznacznie zinterpretowac,
tworzy visibility gap. Nie implementujemy pelnego Terraform/HCL przez
zgadywanie i nie wyliczamy wartosci wymagajacych wykonania zewnetrznego
silnika.

### UI

Ekran ma byc roboczym workspace'em:

- kompaktowy formularz repozytorium, komponentu i dwoch branchy,
- jeden primary action `Verify configuration`,
- wynik o pelnej szerokosci z wyraznym stanem i zakresem,
- wspolny pasek decyzji pokazujacy obok siebie deterministic status, status
  drugiej opinii AI, agreement/disagreement i laczny status,
- widoczny wybor `BASIC`/`DEEP` z krotkim opisem kosztu, czasu i zakresu;
  `DEEP` jest zablokowany z konkretnym powodem, gdy preflight nie jest gotowy,
- pierwszoplanowe podsumowanie licznikow oraz filtrowalna i grupowana tabela
  faktow deterministycznych,
- osobna, rownorzedna sekcja `AI second opinion` z confidence, obserwacjami,
  hipotezami i lista czynnosci do sprawdzenia,
- przejscie z obserwacji AI do konkretnej pozycji diffu/findingu bez szukania
  jej recznie,
- w `DEEP` osobny widok `Functional impact` laczacy system, funkcjonalnosc,
  code reference, confidence i ownership/handoff,
- w `DEEP` stale widoczne repozytoria i refy kodu faktycznie uzyte w analizie,
- stale oznaczenia `FACT`, `DERIVED` i `AI INTERPRETATION`,
- shared aside dla krokow, AI activity, usage i prepared prompt,
- jawne stany partial/error/incomplete,
- historia oraz sanitizowany import/export.

## Ownership

| Element | Wlasciciel |
| --- | --- |
| job API, request/result, pipeline, parsowanie konfiguracji, diff i findings | `features.runtimeconfigurationverification` |
| dokladny odczyt plikow i metadanych z nazwanej instancji GitLaba | neutralne `integrations.gitlab` |
| sesja, report tools, usage i activity | `aiplatform.copilot` i `shared.ai` |
| prompt, artifacts, skille, report factory/mapper i policy | `features.runtimeconfigurationverification.ai` |
| lokalny zapis koperty runu | feature adapter nad `localworkspace.analysisruns` |
| formularz i semantyczny wynik | frontend `features/runtime-configuration-verification` |
| steps, activity, report, usage, auth, model options i historia | istniejace shared frontend `core/components` |

Nowy feature nie importuje Incident Analysis, Flow Explorer ani Change
Verification. Sibling feature'y sa tylko wzorcami zachowania.

## Reuse i capability gap analysis

| Potrzeba | Istniejacy mechanizm | Decyzja |
| --- | --- | --- |
| async job, kroki, usage i activity | neutralne DTO `shared.ai`; lifecycle w sibling feature'ach | feature-owned job state, reuse neutralnych DTO |
| report-first | `shared.ai.report` i report tools | reuse bez zmiany semantyki reportu |
| model options i auth | `api.aioptions`, `api.githubauth` | reuse; przed trzecim klientem FE wydzielic neutralny `AiOptionsApiService` |
| lokalna historia | `localworkspace.analysisruns`, `api.analysisruns` | feature-owned envelope/persister, bez continuation |
| UI workflow | shared aside, steps, report/result primitives | reuse; wydzielic wspolny polling helper, bo to kolejny konsument |
| GitLab exact file/metadata read | istniejacy port ma operacje plikowe, ale jest zwiazany z jednym `analysis.gitlab` | dodac addytywna, neutralna named-connection capability; nie migrowac legacy konsumentow w tym planie |
| parsing YAML | Jackson YAML jest obecny w aplikacji | feature-owned parser wielodokumentowy z zachowaniem document/profile context |
| parsing `.var` | brak | feature-owned, ograniczony parser obserwowanej skladni i jawne gaps |
| strukturalny diff i reference graph | brak | feature-owned modele i deterministic engine |
| affected systems i code-search scopes | `integrations.operationalcontext` read models | reuse w `DEEP`; brak scope'u jest gap/preflight failure |
| ownership/handoff | `OperationalContextOwnershipResolution` | reuse w `DEEP`, bez owner inference z repo |
| deterministic code usage search | GitLab search/read capability | feature-owned grounding nad neutralna integracja i scope boundaries |
| AI-guided code reads | GitLab tools istnieja | tylko `DEEP`, tylko code GitLab i resolved scopes; nigdy config GitLab |

## Conformance delta

- Cel zmiany: nowy sibling feature do weryfikacji konfiguracji branch-to-branch.
- Dlaczego nie wystarcza obecny mechanizm: Change Verification ocenia zmiane
  kodu wobec Jira/Confluence/instrukcji; nie zna repozytorium konfiguracji,
  skladni `.var`, effective values ani polityki sekretow tego use case'u.
- Warstwa bedaca wlascicielem: feature dla workflow; `integrations.gitlab`
  tylko dla neutralnego multi-instance read.
- Publiczne API/DTO: nowe, addytywne endpointy pod
  `/api/runtime-configuration-verification/**`.
- Context/evidence: nowe feature-owned sekcje coverage, parsed configuration,
  diff i findings.
- Prompt/artifacts/skills: nowe i feature-owned; tylko zanonimizowane dane.
- Tools/policy/hidden scope/budzet: `BASIC` ma report tools i built-in `skill`.
  `DEEP` dodaje `opctx_*` oraz focused GitLab code tools ograniczone resolved
  scopes/refami; bez DB i Elasticsearch. Osobne mode-aware default-deny policy
  i budzety.
- Report/result: nowe stabilne sekcje i feature-owned structured result.
- Job state/persistence/export: nowy feature adapter nad neutralna historia;
  bez chatu i continuation.
- Shared FE/UX: neutralny AI options client oraz polling helper z regresja
  istniejacych konsumentow; reszta reuse bez zmian.
- Nowe zaleznosci: feature do neutralnych platform/shared/integration; zadnych
  importow miedzy feature'ami.
- Konsumenci dotknietego shared mechanizmu: Incident Analysis, Flow Explorer,
  Change Verification, Analysis History i nowy feature dla AI options/polling.
- Kompatybilnosc: addytywne API, feature id i properties; brak zmian
  istniejacych requestow, wynikow i GitLab properties.
- Znany drift: `DEEP` reuse'uje obecne GitLab tools z model-facing
  `branchRef`, dlatego wykonany ref jest kanonicznie osadzony w artifacts i
  testowany; nie powstaje nowy tool z tym wzorcem. Singletonowe
  `analysis.gitlab` dla kodu pozostaje oddzielone od nowej named connection
  repozytorium konfiguracji.

## Zakres

- dokumentacja potrzeby, plan i kontrakt architektoniczny po wdrozeniu,
- addytywna konfiguracja nazwanych instancji GitLaba do read-only file access,
- backendowy feature, async job, deterministic diff i AI report,
- bezpieczna polityka redakcji,
- frontendowy workspace, historia i import/export,
- testy backendu, frontendu, security i architektury,
- rejestracja w shellu, landing page i historii.

## Non-goals

- automatyczna zmiana lub commit konfiguracji,
- trigger, approval albo blokada deploymentu,
- odczyt faktycznego runtime z klastra,
- porownanie kodu lub artefaktow binarnych,
- ogolny parser calego Terraform/HCL,
- przechowywanie surowych plikow,
- AI-guided odczyt dodatkowych plikow konfiguracji w MVP,
- pelny codebase review poza resolved code-search scopes i sygnalami
  zmienionej konfiguracji,
- follow-up chat i local continuation,
- migracja obecnych konsumentow `analysis.gitlab` na named connections,
- globalna refaktoryzacja job engine albo report framework.

## Ograniczenia i ryzyka

- Brak pary realnych branchy i przykladu `local.var` ogranicza kalibracje
  parsera oraz heurystyk. MVP ma bezpieczny fallback do `INCOMPLETE`/gap,
  a nowe realne konstrukcje beda dodawane jako syntetyczne fixture po
  potwierdzeniu semantyki.
- Pliki moga byc duze. Odczyt ma jawne limity, ale truncation wymaganych
  plikow nie moze dac zielonego wyniku.
- Heurystyki markerow srodowiska moga dawac false positives. Kazde takie
  ostrzezenie musi pokazac wzorzec i wymagac review.
- Redakcja oparta tylko o nazwe klucza nie jest wystarczajaca. Polityka musi
  laczyc sciezki, znane typy sekretow i konserwatywny fallback, a testy musza
  probowac przeciekow przez prompt, snapshot, report, loggable DTO i export.
- Dodatkowa instancja GitLaba ma odrebny token i ustawienia SSL. Nie wolno
  wprowadzac globalnego trust-all ani podmieniac singletonu uzywanego przez
  kod.
- Domyslny katalog Operational Context w repo jest obecnie pusty. `DEEP` nie
  moze udawac gotowosci bez runtime katalogu, system mappingu, scopes i
  ownershipu.
- Default branch repozytorium kodu nie jest dowodem wersji wdrozonej. Bez
  jawnego `codeRef` albo deployment metadata wynik pokazuje ten limit przy
  kazdej interpretacji funkcjonalnej.
- Live job pozostaje in-memory; historia nie jest durable worker queue.

## Kryteria akceptacji

- Oba branche i komponent sa walidowane, a backend czyta dokladnie wymagane
  pliki z backendowo skonfigurowanego repozytorium.
- Wynik pokazuje coverage, revisions, strukturalne roznice, reference graph,
  deterministic findings, AI assessment i visibility limits.
- Deterministic result i `AI second opinion` sa widoczne rownorzednie, maja
  osobne statusy oraz laczny sygnal agreement/disagreement.
- Kazda grounded observation AI wskazuje co najmniej jeden `differenceId` albo
  `findingId`; pozostale obserwacje sa jawnie oznaczone jako hypothesis.
- Brak/blad/truncation wymaganego pliku daje `INCOMPLETE`.
- `NO_BLOCKING_ANOMALIES` jest mozliwe tylko przy pelnym coverage i bez
  findingu wymuszajacego review.
- AI nie moze obnizyc deterministic status floor ani usunac findingu.
- `BASIC` korzysta z Operational Context tylko przy wyborze/walidacji
  `internal-system` i rozstrzygnieciu katalogu konfiguracji; nie wykonuje
  operational enrichment ani zadnego GitLab code call.
- `DEEP` identyfikuje system bazowy, korzysta tylko z repozytoriow
  dopuszczonych przez code-search scopes i pokazuje uzyty ref kazdego odczytu.
- System bazowy jest wybranym komponentem `kind=internal-system`; feature nie
  utrzymuje alternatywnej mapy component-to-system ani ownershipu.
- Funkcjonalny wniosek `DEEP` ma referencje do konfiguracji i Operational
  Context albo kodu; nieugruntowany wniosek jest hypothesis.
- Ownership pochodzi z systemu/bounded contextu i pokazuje resolution path;
  brak lub konflikt daje `UNKNOWN/AMBIGUOUS`, nie zgadywany team.
- Surowy sekret ani token GitLaba nie wystepuje w publicznym request/response,
  promptcie, evidence, activity, reportcie, historii, eksporcie ani logowanym
  modelu.
- Obecne GitLab flows, Incident Analysis, Flow Explorer i Change Verification
  zachowuja kontrakty i testy.
- Nowy pakiet nie importuje sibling feature'ow, a neutralne warstwy nie
  importuja nowego feature'a.
- UI pokazuje wynik i stan niepelny bez koniecznosci czytania raw JSON.
- Wszystkie testy celowane, architecture guard, backend suite, frontend suite,
  production frontend build i package przechodza.

## Kroki

- [x] Krok 1: Zatwierdzic kontrakt i dodac pionowy skeleton. Utworzyc lokalne
  `AGENTS.md`, feature id/slug, walidowane DTO start/get, cienki controller,
  `BASIC/DEEP`, `systemId`, opcjonalny `codeRef`, pusty async snapshot, route
  skeleton i generyczna sibling-isolation rule w
  `PackageDependencyGuardTest`. Dowod: `MockMvc` dla `202`, walidacji mode,
  systemId, branchy i roznych branchy oraz test architecture guard. Ten krok
  nie laczy sie jeszcze z GitLabem ani AI. Weryfikacja 2026-07-30:
  `RuntimeConfigurationVerificationJobControllerTest`,
  `RuntimeConfigurationVerificationJobServiceTest` i
  `PackageDependencyGuardTest` przeszly; pelne `npm test -- --watch=false`
  przeszlo 192/192; `mvn -q -DskipTests compile`, produkcyjny
  `npm run build` i `git diff --check` zakonczyly sie powodzeniem.

- [x] Krok 2: Dodac neutralny, addytywny odczyt plikow z nazwanej instancji
  GitLaba. Wprowadzic backendowy katalog named connections i read-only port dla
  exact file content/metadata/branch existence, zachowujac bez zmian
  `analysis.gitlab` i jego konsumentow. Dodac feature-owned katalog
  `repositoryId -> connectionId/projectPath` oraz input-options laczace
  repozytorium z Operational Context `internal-system` i bezpiecznie
  rozstrzygnietym katalogiem konfiguracji.
  Dowod: `MockRestServiceServer` dla URL encoding, dwoch instancji, 404/401,
  metadata, limitu/truncation i per-connection SSL; test missing/ambiguous
  runtime signal, path traversal w sygnale, token niewidoczny w DTO/bledzie
  oraz regresja obecnego adaptera i Workspace Settings. Weryfikacja
  2026-07-30: wszystkie testy celowane kroku 2, regresje
  `GitLabRestRepositoryAdapterTest`, `WorkspaceSettingsServiceTest`,
  `WorkspaceSettingsControllerTest` i `PackageDependencyGuardTest` przeszly;
  `mvn -q clean test` zakonczyl sie powodzeniem; frontend przeszedl 192/192
  testow, produkcyjny `npm run build` oraz
  `mvn -q -DskipTests package` zakonczyly sie powodzeniem.

- [x] Krok 3: Dostarczyc deterministic configuration context. Pobrac oba
  snapshoty, sparsowac multi-document YAML i wspierany podzbior `.var`,
  zbudowac sanitized typed tree wszystkich parametrow, reference graph,
  coverage, structural/effective diff, redakcje i deterministic findings.
  Zachowac dokument/profile context, zagniezdzenie i shape; pseudonimizowac
  dynamiczne klucze oraz przypisywac run-local `valueToken` bez ujawniania
  HMAC/hash. Dla sekretow pokazywac tylko relacje tego samego klucza
  source-target. Nie utrwalac raw content ani komentarzy YAML. Dowod: testy
  syntetycznych fixture dla obu nazw YAML, multi-document/profile/nesting,
  unchanged inventory, rownosci tokenow w jednym runie i braku korelacji
  miedzy runami, dynamic key pseudonymization, global/local reference, missing
  key/file, type change, duplicate, malformed syntax, unresolved/cyclic
  reference, wrong-environment marker, unchanged environment value, secret
  change bez cross-key tokenu, unrelated global diff i truncation; test
  serializacji potwierdzajacy brak raw wartosci, komentarzy, HMAC i hashy.
  Weryfikacja 2026-07-30: testy `RuntimeConfigurationSourceLoaderTest`,
  `RuntimeConfigurationYamlParserTest`, `RuntimeConfigurationVarParserTest`
  oraz `RuntimeConfigurationDeterministicEngineTest` pokryly wskazane
  warianty source, parserow, pelnego sanitized inventory, reference graph,
  diffow, findingow, run-local tokenow i serializacji bez raw wartosci,
  komentarzy, sekretow, HMAC/hashow oraz dynamicznych kluczy.
  `PackageDependencyGuardTest`, pelne `mvn -q clean test` i
  `git diff --check` zakonczyly sie powodzeniem; po koncowym hardeningu
  serializacji i heurystyki `devX -> devY` ponownie przeszly testy celowane.

- [x] Krok 4: Dostarczyc deterministic `DEEP` context. Zweryfikowac wybrany
  komponent jako Operational Context `internal-system`, pobrac read models,
  zidentyfikowac kandydatow innych dotknietych
  systemow/integracji/procesow/bounded contexts, rozstrzygnac code-search
  scopes i refy, wykonac scoped search uzycia kluczy w kodzie oraz ownership
  resolution. Dowod: test gotowego i niedostepnego preflight, pustego katalogu,
  unknown/non-internal system, ambiguous/missing configuration-directory
  signal, braku
  scope'u, wielu dotknietych systemow, relaxed property binding,
  `@ConfigurationProperties`, `@Value`, scope/path boundary, ref fallback,
  niepotwierdzonego deployed ref oraz `UNKNOWN/AMBIGUOUS` ownership. `BASIC`
  ma test braku wywolan tych capability poza odczytem input options.
  Weryfikacja 2026-07-30: `RuntimeConfigurationDeepPreflightServiceTest`
  pokryl gotowy, wylaczony i niedostepny preflight, pusty katalog,
  unknown/non-internal system, missing/ambiguous configuration directory,
  brak scope'u, niebezpieczny input, fallback refu oraz jawny brak
  potwierdzenia deployed ref. `RuntimeConfigurationCodeUsageSearchServiceTest`
  potwierdzil exact/relaxed binding, `@ConfigurationProperties`, `@Value`
  oraz brak odczytu poza `pathPrefixes`.
  `RuntimeConfigurationDeepContextServiceTest` pokryl wiele dotknietych
  systemow, proces/integracje, code-grounding IDs, partner ownership,
  unknown/inferred i ambiguous ownership oraz zero wywolan capability dla
  `BASIC`. Bezpieczny endpoint preflight pokryl
  `RuntimeConfigurationDeepPreflightControllerTest`.
  Testy celowane z `PackageDependencyGuardTest`, pelne
  `mvn -q clean test` i `git diff --check` zakonczyly sie powodzeniem.

- [x] Krok 5: Dodac mode-aware report-first AI interpretation nad pelnym
  zanonimizowanym manifestem, diffem i findings. Utworzyc waski kontrakt AI,
  artifacts, polskie
  runtime skills, prompty `BASIC/DEEP`, report scaffold/mapper/fallback,
  default-deny policies i assembler `CopilotRunRequest`. `BASIC` ma tylko
  report tools/`skill`; `DEEP` ma dodatkowo ograniczone `opctx_*` i GitLab code
  tools. Dodac `aiSecondOpinion`, funkcjonalny impact, referencje do
  deterministic/context/code IDs, grounded observation/hypothesis i agreement
  evaluator. Dowod: AI widzi takze niezmienione parametry i zachowany schemat
  YAML, ale nie widzi raw wartosci, HMAC/hashow ani sekretow; mode-specific
  prompts, skill roots, allowlisty, budget, manifest grouping/truncation,
  scope/ref enforcement, dozwolone section IDs, parser fallback, brak
  referencji, hypothesis, agreement/disagreement oraz proby obnizenia
  `INCOMPLETE`, zmiany diffu/ownershipu lub usuniecia findingu.
  Weryfikacja 2026-07-30: dodano waski `aiSecondOpinion`, typed
  observation/hypothesis i functional impact z walidacja stabilnych
  deterministic/context/code IDs, agreement evaluator oraz laczny status z
  niemutowalnym deterministic floor. Artefakty grupuja pelny sanitized
  manifest, zachowuja dokument/profile i niezmienione parametry, maskuja
  wartosci wrazliwe rowniez przy celowo skazonym DTO oraz jawnie raportuja
  truncation. Prompty i polskie skille rozdzielaja `BASIC` od `DEEP`.
  Assembler `CopilotRunRequest` wybiera selected skill root, report scaffold i
  mode-specific allowliste; dodatkowe policy wymuszaja report section IDs,
  scope repozytorium/ref/path, punktowe Operational Context IDs oraz budzet
  focused reads. Testy `RuntimeConfigurationAiArtifactServiceTest`,
  `RuntimeConfigurationPromptPreparationServiceTest`,
  `RuntimeConfigurationAiResponseParserTest`,
  `RuntimeConfigurationAgreementEvaluatorTest`,
  `RuntimeConfigurationReportMapperTest`,
  `RuntimeConfigurationCopilotRunRequestAssemblerTest` i
  `RuntimeConfigurationCopilotPoliciesTest` pokryly fallback, brak
  referencji, hipotezy, agreement/disagreement i proby zmiany statusu,
  diffu, findingow oraz ownershipu. Testy celowane z
  `PackageDependencyGuardTest`, pelne `mvn -q clean test` i
  `git diff --check` zakonczyly sie powodzeniem.

- [x] Krok 6: Domknac async job, obserwowalnosc i bezpieczna portabilnosc.
  Wprowadzic thread-safe state, kroki `SOURCE`, `PARSE`, `DIFF`, `AI`,
  oraz warunkowe `OPERATIONAL_CONTEXT`, `CODE_GROUNDING`, `OWNERSHIP`,
  user-facing errors, local persister oraz wersjonowany export/import bez chat
  continuation. Configuration result ma pozostac widoczny przy bledzie
  `DEEP`/AI ze statusem `INCOMPLETE`. Dowod:
  `BASIC/DEEP` success/partial/failure/concurrency, natychmiastowy snapshot po
  `POST`, poprawny lifecycle krokow, persistence updates, history selection,
  export round-trip, unsupported version, read-only import i brak raw
  konfiguracji/sekretow w `run.json`.
  Weryfikacja 2026-07-30: thread-safe state publikuje kroki `SOURCE`, `PARSE`,
  `DIFF`, warunkowe `OPERATIONAL_CONTEXT`, `CODE_GROUNDING`, `OWNERSHIP` oraz
  `AI`, a `POST` zwraca snapshot `QUEUED` nawet przy synchronicznym executorze
  testowym. Testy `RuntimeConfigurationVerificationJobServiceTest` pokryly
  sukces `BASIC/DEEP`, czesciowy `DEEP`, awarie source, enrichment i AI,
  zachowanie deterministic result ze statusem `INCOMPLETE`, bezpieczne bledy
  oraz persistence updates.
  `RuntimeConfigurationVerificationJobStateConcurrencyTest` potwierdzil
  spojne snapshoty przy rownoleglym evidence/activity, a
  `RuntimeConfigurationVerificationPortabilityTest` potwierdzil feature
  history selection, wylaczone chat continuation, wersjonowany export
  round-trip, read-only import, odrzucenie nieznanej wersji i brak wrazliwych
  tokenow w `run.json`. Endpoint importu i bezpieczne bledy pokryl
  `RuntimeConfigurationVerificationImportControllerTest`.
  Testy celowane z `PackageDependencyGuardTest`, pelne `mvn -q clean test`
  oraz `git diff --check` zakonczyly sie powodzeniem.

- [x] Krok 7: Dostarczyc frontendowy happy path i semantyczny wynik. Wydzielic
  neutralny `AiOptionsApiService` oraz wspolny polling helper z migracja i
  regresja istniejacych konsumentow. Dodac wybor `BASIC/DEEP`, preflight i
  powod blokady `DEEP`, `codeRef`, formularz, start, restore, polling bez
  overlap, dual-result decision bar, filtrowalne deterministic
  differences/findings, panel `AI second opinion`, agreement/disagreement,
  nawigacje po referencjach oraz `Functional impact` z systems/code/ownership.
  Dodac visibility limits, shared aside/report/usage, sanitizowany
  import/export i rejestracje shell/landing/history. Dowod:
  `HttpTestingController`, form/field errors, mode mapping, deep availability,
  auth/reauth, polling stop/retry, partial result, filtery, link AI ->
  diff/finding/code, brak AI, disagreement, unknown owner, ref warning,
  accessibility, import/export i shell/history tests.
  Zrealizowano neutralne `AiOptionsApiService` i
  `AnalysisJobPollingService` z natychmiastowym pierwszym odczytem,
  `exhaustMap` bez overlap oraz migracja Incident Analysis, Flow Explorer i
  Change Verification. Workspace Runtime Configuration Verification
  dostarcza formularz `BASIC/DEEP`, GitHub auth/reauth, preflight z blockerami,
  `codeRef`, start/restore/retry, decision bar, jawne `FACT`/`DERIVED`/
  `AI INTERPRETATION`, filtrowalne diffy i findings, shared report/aside/usage,
  nawigacje AI do diff/finding/code grounding, functional impact,
  ownership/handoff, visibility limits oraz read-only import/export.
  Feature jest dostepny z routingu, sidebara, landing page i historii.
  Dowod: testy `HttpTestingController` dla options/preflight/start/poll/import,
  test pollingu bez overlap, testy formularza, auth/reauth, partial/no-AI/
  disagreement/unknown-owner/ref-warning, filtrow i referencji, retry,
  accessibility, import/export oraz shell/history. Pelne
  `npm test -- --watch=false` zakonczylo sie wynikiem 35 suites / 208 testow,
  `npm run build` wygenerowalo produkcyjny bundle, izolowany
  `FrontendPageTest` oraz ponowne pelne `mvn -q test` zakonczyly sie
  powodzeniem, a `git diff --check` nie wykazal bledow whitespace.

- [x] Krok 8: Hardening, pelna weryfikacja i dokumentacja stanu. Wykonac probe
  przecieku sekretow end-to-end, limity duzych plikow, bezpieczne komunikaty
  401/403/404/timeout, budget/latency `BASIC` vs `DEEP`, puste i niepelne
  Operational Context, architecture diff i aktualizacje `product-direction`,
  `system-overview`, `key-decisions` tylko dla rzeczywiscie wdrozonego stanu,
  `package-dependencies`, operational-context usage, root/lokalnych
  `AGENTS.md`, `docs/README.md` oraz runtime flow nowego feature'a. Dowod:
  `mvn -q clean test`,
  `npm --prefix frontend test -- --watch=false`,
  `npm --prefix frontend run build`,
  `mvn -q -DskipTests package`,
  `git diff --check` i udokumentowany architecture/security review.
  Zrealizowano hardening named GitLab transportu: 401/403/404 i
  timeout/transport failure sa mapowane do typowanych kodow bez propagowania
  tokenu, upstream body ani raw exception message. Istniejace i rozszerzone
  testy potwierdzily backendowy limit pliku, truncation AI artifacts bez
  zmiany deterministic result, brak raw sekretow/hashow w promptcie, report,
  historii i exporcie, traversal guard, bezpieczne bledy joba oraz read-only
  import. `BASIC` ma strukturalnie krotsza sciezke bez Operational Context i
  code tools; `DEEP` ma allowliste scope/ref/path i feature budget. Testy
  pokryly pusty, wylaczony, niepelny, ambiguous i niedostepny Operational
  Context oraz zachowanie partial result.
  Architecture review potwierdzil composition ownership feature'a, brak
  zaleznosci sibling feature i brak odwrotnych importow reusable warstw.
  Security boundary, runtime flow i wynikowy stan zostaly zapisane w
  `docs/architecture/runtime-configuration-verification-runtime-flow.md`,
  `product-direction`, `system-overview`, `key-decisions`,
  `package-dependencies`, dokumentacji Operational Context, `AGENTS.md` i
  `docs/README.md`.
  Weryfikacja 2026-07-30: testy celowane i `PackageDependencyGuardTest`,
  `mvn -q clean test`, `npm --prefix frontend test -- --watch=false`
  (35 suites / 208 testow), `npm --prefix frontend run build`,
  `mvn -q -DskipTests package` oraz `git diff --check` zakonczyly sie
  powodzeniem. Powstal
  `target/incident-tracker-0.0.1-SNAPSHOT.jar`.

## Bramka zatwierdzania

Plan ma status `draft` i nie upowaznia do implementacji. Zatwierdzenie calego
planu pozwala wykonywac kroki 1-8 kolejno. Zatwierdzenie pojedynczego kroku
obejmuje tylko ten krok. Po kazdym kroku trzeba:

1. przedstawic dowod weryfikacji,
2. oznaczyc go `[x]` dopiero po spelnieniu kryteriow,
3. pokazac zakres i ryzyka kolejnego kroku,
4. uzyskac ponowne zatwierdzenie, jezeli zmieni sie kontrakt, security boundary
   albo zakres.
