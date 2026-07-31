# Runtime Configuration Verification

Status: done

Source need: [runtime-configuration-verification](../needs/runtime-configuration-verification.md)

## Potrzeba / dlaczego

Administrator potrzebuje przed wdrozeniem porownac konfiguracje wybranego
komponentu pomiedzy dwoma branchami srodowiskowymi i dostac nie tylko diff,
ale tez bezpieczna ocene mozliwych przeoczen. Konfiguracja mieszka na innej
instancji GitLaba niz kod, laczy wielodokumentowy YAML z `global.var` i
komponentowym `local.var`. Prawdziwe sekrety sa podstawiane z Vault i nie sa
odczytywane przez feature.

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
- Input wspolny: `mode`, `repositoryId`, `systemId`, `sourceBranch`,
  `targetBranch`.
- Input tylko dla `DEEP`: opcjonalne `codeRef`, `model` i `reasoningEffort`.
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
- Byte-identical surowa zawartosc i komentarze sa przetwarzane tylko w
  pamieci i nie trafiaja do promptu, evidence ani activity. Znormalizowana
  projekcja wyniku operatorskiego moze zachowac dokladne wartosci z plikow w
  job state, historii i eksporcie.
- `BASIC` nie buduje manifestu AI, promptu ani sesji Copilota.
- W `DEEP` AI dostaje pelny zanonimizowany manifest obejmujacy zmienione i
  niezmienione parametry, zachowany schemat wielodokumentowego YAML,
  structural diff i deterministic findings. Nigdy nie dostaje dostepu do
  surowych plikow ani tools instancji GitLaba z konfiguracja; moze dostac
  tylko ograniczone tools GitLaba z kodem.
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
- Wynik deterministyczny jest samodzielnym produktem trybu `BASIC` i
  niezmiennym fundamentem trybu `DEEP`. AI nie jest rendererem diffu.
- `BASIC` uzywa Operational Context tylko do wyboru `internal-system` i
  rozstrzygniecia katalogu konfiguracji. Analiza konczy sie po deterministic
  diffie, nie rozwiazuje auth Copilota, nie uruchamia AI, Operational
  Context enrichment ani code tools i nie generuje usage/cost.
- `DEEP` zachowuje identyczny configuration result, a dodatkowo uzywa
  Operational Context, code-search scopes, focused GitLab code reads i
  ownership resolution oraz AI do opisania ryzyka, rekomendacji i znaczenia
  funkcjonalnego.
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
- W `BASIC` finalny status jest bezposrednim odwzorowaniem deterministic
  statusu. Brak AI nie jest failure, limitation ani `INCOMPLETE`.
- W `DEEP` deterministic diff pozostaje dostepny, gdy enrichment albo AI sie
  nie powiedzie; job konczy sie wtedy z jawnym ograniczeniem.
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
  -> configurationDiff per plik
  -> BASIC: final result + historia/eksport
  -> DEEP: Operational Context match
       -> affected systems/integrations/processes/bounded contexts
       -> code-search scopes i focused code discovery
       -> ownership/handoff resolution
       -> zanonimizowany configuration + operational + code context dla AI
       -> report-first komentarze ryzyka, rekomendacje i functional impact
       -> enriched feature result + historia/eksport
```

Fakty, wyprowadzenia i interpretacja pozostaja rozdzielone:

1. **known facts** - pliki, metadane, sciezki kluczy, typy oraz informacja o
   dodaniu/usunieciu/zmianie,
2. **derived facts** - rozwiazane odwolania, wartosci efektywne, konflikty i
   deterministic findings oraz - dla `DEEP` - dopasowania katalogowe,
   code-search scopes, miejsca uzycia konfiguracji i resolved ownership,
3. **AI interpretation tylko w `DEEP`** - ocena znaczenia roznic, mozliwy
   scenariusz pomylki, sugerowana czynnosc i opis wplywu funkcjonalnego.

W `BASIC` feature-owned status jest mapowany 1:1 z deterministic statusu. W
`DEEP` ma deterministic floor: AI moze podniesc poziom ryzyka, ale nie moze:

- obnizyc `INCOMPLETE`, gdy wymagany plik jest niedostepny albo niepoprawny,
- usunac deterministic finding,
- przedstawic zanonimizowanej lub nierozwiazanej wartosci jako potwierdzonej.

W `DEEP` AI zapisuje tylko sekcje nalezace do interpretacji. Deterministyczne
sekcje, pozycje diffu i findings sa tworzone przez backend, pozostaja
niemutowalne dla modelu i sa weryfikowane po zakonczeniu runu. Kazda
obserwacja AI powinna odwolywac sie do stabilnych identyfikatorow
deterministic differences albo findings. Obserwacja bez takiego oparcia jest
hipoteza i musi byc tak oznaczona.

### Publiczny wynik

Feature-owned result powinien zawierac:

- `status`: `NO_BLOCKING_ANOMALIES`, `REVIEW_REQUIRED`,
  `LIKELY_CONFIGURATION_ERROR` albo `INCOMPLETE`,
- `scope`: repozytorium, komponent, oba branche i bezpieczne revision refs,
- `mode`: wykonany `BASIC` albo `DEEP`,
- `coverage`: stan kazdego wymaganego pliku po obu stronach,
- `configurationDiff`: znormalizowane drzewa per plik/dokument z dokladnymi
  source/target values, `PRESENT`/`ABSENT`, change kind i stable
  `differenceId`,
- `deterministicResult`:
  - `status`,
  - `differenceSummary` z licznikami per plik, change kind i severity,
  - `differences` jako strukturalne, filtrowalne pozycje bez sekretow,
  - `findings` z kategoria, severity, confidence, facts, references i
    suggested check,
- `aiSecondOpinion` tylko dla `DEEP`:
  - stan wykonania AI,
  - conclusion i confidence,
  - observations rozdzielajace grounded interpretation od hypothesis,
  - referencje do `differenceId` i `findingId`,
  - priorytetowa lista czynnosci do ludzkiej weryfikacji,
- `agreement` tylko dla `DEEP`: informacja, gdzie wynik deterministyczny i AI
  sa zgodne, rozbiezne albo AI nie moglo dokonac oceny,
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
- `prompt`, `usage` oraz neutralny `AnalysisReport` tylko dla `DEEP`.

Dla `BASIC` pola `aiSecondOpinion`, `agreement`, `deepAnalysis`, `prompt`,
`usage` i `report` sa nieobecne/null zgodnie z kontraktem, a nie z powodu
bledu. Job nie tworzy kroku `AI`, AI activity ani tool evidence.

Stabilne sekcje reportu `DEEP`:

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
resolved ownership sa przygotowane przez backend. W `DEEP` AI moze zapisac
tylko `ai-second-opinion`, `recommended-human-checks` oraz narracyjna
interpretacje `functional-impact-and-code-grounding`; nie moze zmienic
referencji, coverage, ownershipu ani visibility gaps dodanych przez backend.
`BASIC` nie tworzy reportu AI.

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
- dodanie/usuniecie/zmiane referencji do wartosci podstawianej w runtime,
- roznice `global.var` wykorzystywane przez wybrany komponent,
- pozostale roznice globalne jako osobny kontekst, bez automatycznego
  przypisywania ich do komponentu.

Klasyfikacja wartosci wrazliwych pozostaje elementem sanitizacji wejscia AI.
Publiczna projekcja operatorska nie uzywa jej do maskowania: pokazuje dokladne
wartosci i referencje z testowych plikow konfiguracyjnych. Prawdziwe sekrety
sa poza zakresem feature'a i sa podstawiane z Vault w runtime.

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
- dla `BASIC`: jeden deterministic status bez placeholderow AI,
- dla `DEEP`: pasek decyzji pokazujacy obok siebie deterministic status,
  status interpretacji AI, agreement/disagreement i laczny status,
- widoczny wybor `BASIC`/`DEEP` z krotkim opisem kosztu, czasu i zakresu;
  `DEEP` jest zablokowany z konkretnym powodem, gdy preflight nie jest gotowy,
- model i reasoning effort sa widoczne oraz wysylane tylko dla `DEEP`,
- pierwszoplanowe podsumowanie licznikow oraz zagniezdzony renderer
  `configurationDiff` per plik w trybie `Zmiany`/`Caly plik`,
- tylko dla `DEEP`: osobna sekcja interpretacji AI z confidence,
  obserwacjami, hipotezami i lista czynnosci do sprawdzenia,
- tylko dla `DEEP`: przejscie z obserwacji AI do konkretnej pozycji
  diffu/findingu bez szukania jej recznie,
- w `DEEP` osobny widok `Functional impact` laczacy system, funkcjonalnosc,
  code reference, confidence i ownership/handoff,
- w `DEEP` stale widoczne repozytoria i refy kodu faktycznie uzyte w analizie,
- stale oznaczenia `FACT`, `DERIVED` oraz tylko dla `DEEP`
  `AI INTERPRETATION`,
- shared aside pokazuje dla `BASIC` tylko `SOURCE`, `PARSE`, `DIFF`; dla
  `DEEP` dodatkowo enrichment, AI activity, usage i prepared prompt,
- jawne stany partial/error/incomplete,
- historia oraz import/export operatorskiego wyniku.

## Ownership

| Element | Wlasciciel |
| --- | --- |
| job API, request/result, pipeline, parsowanie konfiguracji, diff i findings | `features.runtimeconfigurationverification` |
| dokladny odczyt plikow i metadanych z nazwanej instancji GitLaba | neutralne `integrations.gitlab` |
| sesja, report tools, usage i activity DEEP | `aiplatform.copilot` i `shared.ai` |
| prompt, artifacts, skille, report factory/mapper i policy DEEP | `features.runtimeconfigurationverification.ai` |
| lokalny zapis koperty runu | feature adapter nad `localworkspace.analysisruns` |
| formularz i semantyczny wynik | frontend `features/runtime-configuration-verification` |
| steps, activity, report, usage, auth, model options i historia | istniejace shared frontend `core/components` |

Nowy feature nie importuje Incident Analysis, Flow Explorer ani Change
Verification. Sibling feature'y sa tylko wzorcami zachowania.

## Reuse i capability gap analysis

| Potrzeba | Istniejacy mechanizm | Decyzja |
| --- | --- | --- |
| async job, kroki, usage i activity | neutralne DTO `shared.ai`; lifecycle w sibling feature'ach | feature-owned job state; usage/activity tylko DEEP |
| report-first | `shared.ai.report` i report tools | reuse tylko DEEP |
| model options i auth | `api.aioptions`, `api.githubauth` | reuse tylko DEEP; BASIC nie wywoluje tych capability |
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
- Sanitizacja wejscia AI musi nadal blokowac rzeczywiste wartosci przed
  promptem i odpowiedzia modelu. Nie jest ona polityka widocznosci wyniku dla
  operatora.
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
- Token GitLaba nie wystepuje w publicznym request/response, promptcie,
  evidence, activity, reportcie, historii ani eksporcie. Dokladne wartosci i
  referencje z testowych plikow moga wystapic w wyniku operatorskim, historii
  i eksporcie; nie trafiaja do wejscia ani odpowiedzi AI. Feature nie czyta
  prawdziwych sekretow z Vault.
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
  reference, literalny secret addition i truncation. Type/effective/sensitive
  placeholder differences oraz wartosci srodowiskowe pozostaja faktami bez
  heurystycznych findingow; test
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

## Rozszerzenie L3: operatorski diff i rozdzielenie trybow

Source need pozostaje bez zmian:
[runtime-configuration-verification](../needs/runtime-configuration-verification.md).
Rozszerzenie zastapi glowna tabele structural differences zagniezdzonym
widokiem per plik i zmieni kontrakt trybow: `BASIC` bedzie w pelni
deterministyczny, a tylko `DEEP` wykona operational/code enrichment i AI oraz
doda komentarze o ryzyku i rekomendacje.

### Klasyfikacja

Poziom: **L3 - architektoniczny**.

Zmiana obejmuje feature UI i publiczny result (L1), lokalna historie oraz
import/eksport (L2), a takze zmienia dotychczasowa granice persistence,
widocznosci wartosci, auth i AI runtime zalezne od trybu (L3). Zakres jest
ograniczony do testowych branchy `devX` i `zt00X`. Prawdziwe sekrety sa w
Vault; repozytorium zawiera konfiguracje i referencje, ktore operator moze
odczytac swoim tokenem GitLaba.

Rollback:

- usuniecie opcjonalnej projekcji operatorskiej z live result,
- przywrocenie renderowania obecnego `deterministicResult`,
- ponowne wlaczenie dotychczasowego AI flow dla `BASIC`,
- stare historie i eksporty pozostaja czytelne przez fallback do obecnego
  `deterministicResult`.

### Baseline przed zmiana

```text
Feature/capability:
Runtime Configuration Verification.

Obecna wartosc dla operatora:
Coverage, filtrowalna tabela structural differences, findings, osobna druga
opinia AI, agreement, DEEP context, ownership i visibility limits.

Publiczny input i output:
Input bez zmian. Live GET joba zwraca RuntimeConfigurationVerificationResult
z sanitizowanym deterministicResult, aiSecondOpinion, agreement, deepAnalysis,
visibilityLimits, prompt i usage.

Kanoniczne endpointy:
/api/runtime-configuration-verification/input-options,
/api/runtime-configuration-verification/deep-preflight,
POST/GET /api/runtime-configuration-verification/jobs,
POST /api/runtime-configuration-verification/imports.

Lifecycle joba i kroki:
SOURCE -> PARSE -> DIFF -> opcjonalny DEEP enrichment -> AI.
Deterministic result jest publikowany po DIFF i pozostaje dostepny przy
awarii DEEP albo AI.
`RuntimeConfigurationVerificationJobService.startJob()` bezwarunkowo
rozwiazuje auth/token Copilota takze dla `BASIC`. Po `DIFF` oba tryby
przechodza przez prompt preparation, AI runner i krok `AI`.
Interim result obu trybow ma limit `AI second opinion has not completed yet`.

Deterministic context/evidence:
Parser utrzymuje raw scalar values tylko w pamieci. Engine zwraca pelny
sanitizowany tree, run-local valueToken, differences/findings i odrzuca raw
values przed job state.

Prompt, artifacts i runtime skills:
AI dostaje compact v2 sanitized artifacts z pelnym schematem, ale bez raw
values, sekretow i hashy. Bez zmiany delivery mode.

Tools, allowlista, policy, hidden scope i budzet:
`BASIC` ma osobny runtime skill/prompt i ograniczona allowliste bez operational
enrichment. `DEEP` ma dodatkowe scoped Operational Context i GitLab code
tools.

Report sections i mapper:
Raport zawiera sanitizowane deterministic references i AI second opinion.
Nie jest powierzchnia do pokazania raw values.

Follow-up semantics:
Brak follow-up chat.

Local history/import/export/continuation:
Local run i export v1 przechowuja sanitizowany snapshot. Import jest read-only.
Frontend obecnie eksportuje kopie live joba, a backendowy persister uzywa
RuntimeConfigurationVerificationSnapshotSanitizer.

Shared komponenty i modele FE:
Shared aside/report/usage pozostaja bez zmian. Semantyczny renderer wyniku
jest feature-owned. Formularz pokazuje AI model/reasoning niezaleznie od
trybu, a wynik zaklada dual-result decision bar.

Tool Workbench:
Preview buduje AI input dla `BASIC` i `DEEP`, pokazuje compact sanitized
artifacts, mapping i anonimizacje. Nie zawiera nowego `configurationDiff` i
nie ma stanu `AI input not generated`.

Aktualne visibility limits:
Historia/import nie odtwarza raw plikow; AI nie widzi raw values; DEEP ma
osobne limity Operational Context i code grounding.

Testy chroniace zachowanie:
RuntimeConfigurationDeterministicEngineTest,
RuntimeConfigurationVerificationJobServiceTest,
RuntimeConfigurationVerificationPortabilityTest,
RuntimeConfigurationVerificationJobControllerTest,
RuntimeConfigurationAiResponseParserTest,
RuntimeConfigurationVerificationPageComponent tests,
runtime-configuration-import-export.utils tests oraz
PackageDependencyGuardTest.

Znane drifty w dotykanym obszarze:
Brak. Walidacja requestu juz ogranicza branche do `devX` i `zt00X`.
```

Baseline 2026-07-30:

- celowane testy backendu i `PackageDependencyGuardTest` przeszly,
- pelny frontend baseline przeszedl: 36 suites / 216 testow,
- pierwsza proba frontendu w sandboxie nie byla testowym failure feature'a:
  Angular nie mial dostepu do workspace dependencies; ponowne uruchomienie w
  dozwolonym srodowisku zakonczylo sie powodzeniem.

### Conformance delta

```text
Cel zmiany:
1. BASIC: pokazac wylacznie deterministyczny configurationDiff per plik, bez
   auth Copilota, promptu, sesji AI, AI step, reportu AI i usage.
2. DEEP: zachowac identyczny deterministic configurationDiff i dodac
   Operational Context, code grounding, ownership oraz komentarze AI o ryzyku,
   rekomendacjach i functional impact.
3. Tool Workbench: pokazac operator projection dla obu trybow; dla BASIC
   pokazac `AI input not generated`, a dla DEEP dodatkowo sanitized AI input,
   artifacts i mapowanie identyfikatorow.

Dlaczego nie wystarcza obecny mechanizm:
Tabela rozbija konfiguracje na osobne wiersze, traci kontekst zagniezdzenia i
wymusza dlugie przewijanie. ValueToken jest pomocny AI, ale nie operatorowi.
Ponadto BASIC bezwarunkowo rozwiazuje token Copilota i uruchamia AI, wiec nie
jest faktycznie szybkim ani niezaleznym trybem deterministycznym. UI oraz job
state interpretuja brak AI jako stan niepelny zamiast zamierzonego kontraktu.

Warstwa bedaca wlascicielem:
Feature-owned backend presentation model/service oraz feature-owned Angular
renderer i mode policy. Integracja GitLab, platforma Copilot i shared UI nie
przejmuja semantyki wyboru trybu.

Zmiana publicznego API/DTO:
Opcjonalne `configurationDiff` w RuntimeConfigurationVerificationResult.
Pole zawiera znormalizowany wynik per plik i moze byc null dla starszych
historii/eksportow. Endpointy i enum trybu bez zmian. `codeRef`, `model` i
`reasoningEffort` sa dozwolone tylko dla DEEP; UI nie wysyla ich dla BASIC, a
backend odrzuca niezgodny request zamiast sugerowac, ze opcje zostaly uzyte.

Zmiana context/evidence:
Kanoniczny RuntimeConfigurationDeterministicContext bez zmian. Deterministic
build udostepnia jeden feature-owned rezultat z sanitized contextem i
projekcja operatorska z oryginalnymi nazwami kluczy oraz wszystkimi dokladnymi
wartosciami source/target. Source load i parsing sa wykonywane raz. BASIC nie
przekazuje sanitized contextu dalej do prompt/artifact preparation.

Zmiana prompt/artifacts/skills:
Prompt/artifacts/skills sa capability tylko DEEP. Usunac albo wycofac z
runtime wybor `runtime-configuration-basic-review` i galezie tworzace BASIC
Copilot run. Bez raw values i bez podmiany tokenow w tekscie AI. Obecne
observation oraz functional impact IDs sa zrodlem adnotacji. Guidance DEEP
doprecyzuje, ze komentarz ma byc krotki, opisac ryzyko/rekomendacje i nie
cytowac valueToken.

Zmiana tools/policy/hidden scope/budzetu:
BASIC nie tworzy session context, allowlisty ani budzetu tools i nie rozwiazuje
auth/token Copilota. DEEP zachowuje obecne default-deny scope'y Operational
Context i GitLab code oraz feature budget.

Zmiana report/result:
BASIC konczy z deterministic statusem oraz configurationDiff; aiSecondOpinion,
agreement, deepAnalysis, prompt, usage i report sa null/nieobecne i nie
tworza visibility limitu. DEEP zachowuje sanitizowany report i AI result.
`configurationDiff` jest osobna prezentacja operatorska: FACT values oraz -
tylko w DEEP - jawnie oznaczone AI annotations.

Zmiana job state/persistence/export:
BASIC lifecycle to SOURCE -> PARSE -> DIFF -> COMPLETED. Nie ma kroku AI,
AI activity, tool evidence ani komunikatu `AI pending`. DEEP zachowuje
enrichment -> AI i partial behavior. Job, historia i export zachowuja
`configurationDiff` razem z dokladnymi wartosciami z plikow. Pole jest
addytywne i opcjonalne, wiec export v1 nie wymaga migracji; starszy import nie
ma tego pola.

Zmiana Tool Workbench:
Backend preview zwraca configurationDiff z tej samej projekcji co job.
BASIC nie wywoluje prompt preparation i pokazuje jawny stan
`aiInputGenerated=false`/`AI input not generated`. DEEP pokazuje
configurationDiff, sanitized deterministic model, compact artifacts i
mapowanie original operator node -> sanitized path/token/differenceId.
Workbench nigdy nie wysyla actual values do AI.

Zmiana shared FE/UX:
Bez zmiany kontraktow shared komponentow. Formularz pokazuje model/reasoning,
codeRef i preflight tylko dla DEEP. BASIC nie renderuje pustego decision slotu,
AI panelu, reportu, promptu ani usage. Obecna tabela diffu zostaje zastapiona
lokalnym rendererem per plik; DEEP AI links nadal fokusuja `differenceId`.

Nowe lub usuniete zaleznosci:
Brak nowych zaleznosci miedzy top-level warstwami i brak nowych bibliotek.
BASIC usuwa runtime zaleznosc wykonania od Copilota, ale feature nadal zalezy
od platformy AI dla DEEP.

Consumer audit:
Backend runtime:
- RuntimeConfigurationVerificationJobStartRequest,
- RuntimeConfigurationVerificationJobService,
- RuntimeConfigurationVerificationJobState,
- RuntimeConfigurationVerificationResult i job snapshot,
- RuntimeConfigurationDeterministicContextService/listener,
- RuntimeConfigurationVerificationSnapshotSanitizer,
- local persister, import service/export envelope i job/import controllers.
Backend AI/DEEP:
- RuntimeConfigurationPromptPreparationService,
- RuntimeConfigurationCopilotRunRequestAssembler, skill names, scope/tool
  policies i session context factory,
- RuntimeConfigurationAiAssessmentService/combined status/agreement,
- report factory/mapper i runtime skills BASIC/DEEP.
Tool Workbench:
- RuntimeConfigurationWorkbenchPreviewService/store/snapshot,
- preview response, AI input, mapping i anonymization DTO/controller.
Frontend:
- runtime configuration request/result/job models i API service,
- main page TS/HTML/styles/tests,
- Workbench page TS/HTML/styles/tests,
- import/export utility, history restore i shared aside consumers.
Dokumentacja:
- need/plan, runtime flow, system overview, key decisions, local AGENTS,
  runtime skills contract i README/navigation opisy.
Shared auth, AI options, polling, aside i platform Copilot nie zmieniaja
kontraktu; zmienia sie tylko warunkowe uzycie przez feature.

Kompatybilnosc i migracja:
Pole opcjonalne. Stare historie i exporty renderuja fallback z obecnego
sanitizowanego drzewa. Stary rekord BASIC moze zawierac AI result i pozostaje
read-only history snapshotem wykonanym wedlug starego kontraktu. Nowy BASIC
nie uruchamia AI. Nowe runy zachowuja pelny znormalizowany widok.

Testy regresji:
Backend BASIC:
- start bez auth/token Copilota i verifyNoInteractions dla prompt/runner/deep,
- conditional request validation AI options/codeRef,
- dokladnie SOURCE/PARSE/DIFF, final status z deterministic statusu,
- brak AI step/activity/evidence/prompt/usage/report/AI visibility limit,
- configurationDiff live i persistence/export/import round-trip.
Backend DEEP:
- auth, enrichment, prompt, runner, annotations direct/finding-transitive,
- success, enrichment partial i AI failure z zachowanym configurationDiff,
- brak token replacement w AI prose i brak actual values w AI boundary.
Workbench:
- BASIC configurationDiff + aiInputGenerated=false + zero prompt preparation,
- DEEP configurationDiff + sanitized input/artifacts/mapping,
- actual values tylko po stronie operator projection.
Frontend:
- BASIC ukrywa AI controls/panele/usage i pokazuje tylko deterministic result,
- DEEP pokazuje controls, annotations, agreement, impact i usage,
- per-file rendering, markery, pelne wartosci, absent, focus, fallback,
  history/import i client export.
Pelne backend/frontend suites, build, package i architecture tests.

Dokumentacja:
Need i plan przed implementacja; po wdrozeniu runtime flow, key decision,
system overview, lokalny AGENTS invariant i usuniecie historycznych opisow
`BASIC` jako AI mode.

Znany drift:
Bez zmian. Nie rozszerzamy obslugiwanych branchy poza testowe `devX` i
`zt00X`.
```

### Decyzja projektowa

- `configurationDiff` powstaje deterministycznie zaraz po `DIFF`, wiec czytelny
  widok nie zalezy od sukcesu AI.
- `BASIC` publikuje ten wynik jako finalny bez sprawdzania Copilot auth i bez
  tworzenia promptu, reportu, usage lub sztucznego visibility limitu.
- W `DEEP` po odpowiedzi AI backend dolacza adnotacje przez istniejace
  `differenceId`/`findingId`. Nie podmienia pseudonimow w swobodnym tekscie AI
  i nie zmienia deterministic facts.
- AI fields w publicznym result sa warunkowe semantycznie, nie sa wypelniane
  placeholderami dla BASIC. Mode jest wystarczajacym rozroznieniem
  `not requested` od `failed`.
- UI, historia i eksport pokazuja dokladne source/target value dla wszystkich
  parametrow i referencji z plikow. Dla brakujacej strony pokazuja `ABSENT`.
  Token dostepowy GitLaba nie jest czescia pliku ani projekcji.
- Operator projection zachowuje oryginalne nazwy, takze dynamiczne klucze.
  Osobny sanitized deterministic context nadal pseudonimizuje je dla AI,
  promptu i odpowiedzi modelu w DEEP. BASIC nie uruchamia preparation.
- Feature nie odczytuje Vault. Wartosci wygladajace jak hasla albo tokeny sa
  zwyklymi referencjami konfiguracyjnymi i nie sa maskowane przed operatorem.
- DTO z actual values ma redacted `toString`. Nie moze trafic do promptu,
  reportu, evidence, activity, error message ani logowania.
- Widok domyslny pokazuje zmienione liscie oraz koniecznych rodzicow.
  Przelacznik `Caly plik` pokazuje takze `UNCHANGED`; niezmienione galezie sa
  zwijalne.
- Linia `UNCHANGED` pokazuje jedna wspolna wartosc bez markera. Linia
  zmieniona pokazuje jawne `source` i `target`, a `ADDED`/`REMOVED` pokazuje
  brakujaca strone jako `ABSENT`, dzieki czemu widok nadal przypomina plik i
  nie powtarza dwoch identycznych wartosci.
- `application.y[a]ml.kv` jest renderowane jako znormalizowany YAML z
  separatorami dokumentow. `global.var` i `local.var` sa renderowane jako
  znormalizowane zagniezdzone bloki/assignmenty. Nie obiecujemy zachowania
  komentarzy, whitespace ani byte-identical source.
- Kolory nie sa jedynym sygnalem: kazda zmiana ma ikone i tekst
  `added/removed/changed/type/effective`.
- Workbench uzywa tego samego buildera co job. Nie tworzy drugiej projekcji ani
  osobnego mapowania tylko do demonstracji.
- W Workbench `BASIC` konczy pipeline preview po operator projection i
  pokazuje `AI input not generated`; `DEEP` pokazuje dodatkowa granice
  anonimizacji i artefakty AI.

### Kroki rozszerzenia

- [x] Krok 9: Wprowadzic model znormalizowanego `configurationDiff`.
  Dodac typowane stany wartosci/presence, tree/doc projection dla YAML
  multi-document oraz `.var`, oryginalne dynamiczne klucze, wszystkie
  source/target values i mapowanie stable difference IDs. Kanoniczny
  deterministic context dla AI pozostaje bez raw values. Dowod: synthetic
  tests dla unchanged, added, removed, changed, type/effective change,
  list/map, dynamic key, multi-document/profile i referencji do Vault.
  Weryfikacja 2026-07-30: dodano osobny model i builder projekcji per plik z
  `PRESENT`/`ABSENT`, typed values, cardinality, profilami dokumentow,
  oryginalnymi sciezkami i redacted `toString`. Test syntetyczny potwierdza
  wszystkie wymagane rodzaje zmian, brak pliku, `null` kontra pusty tekst,
  YAML multi-document, `.var`, list/map, dynamic key, referencje do Vault,
  mapowanie ID oraz rozdzielenie actual values od sanitizowanego contextu AI.
  Przeszly testy celowane z parserami, deterministic engine, prompt/artifact
  preparation i `PackageDependencyGuardTest`, a takze pelne `mvn -q test`.
  Projekcja nie jest jeszcze podlaczona do job result ani persistence; to jest
  zakres kroku 10.

- [x] Krok 10: Zmienic runtime contract trybow i podlaczyc backendowy
  `configurationDiff`.
  Wprowadzic jeden deterministic build result zawierajacy sanitized context
  i operator projection bez ponownego source load/parsing. Dodac projekcje do
  live result, job state, local history, import/export i controller JSON.
  `BASIC` ma dzialac bez Copilot auth, konczyc sie po `DIFF`, mapowac finalny
  status z deterministic statusu i nie tworzyc AI step/activity/evidence,
  promptu, reportu ani usage. `DEEP` zachowuje auth -> enrichment -> AI oraz
  partial result. Input AI/code fields sa tylko DEEP; minimalna adaptacja
  frontendu nie wysyla ich dla BASIC i nie pokazuje phantom AI state.
  Dowod: request validation, no-token/no-AI interaction tests, dokladny
  lifecycle i status BASIC, DEEP success/partial/failure, live/interim JSON,
  persistence/export/import round-trip actual values i regresja
  `PackageDependencyGuardTest`.
  Weryfikacja 2026-07-31: deterministic pipeline zwraca sanitized context i
  operator projection z jednego load/parse, a `configurationDiff` jest czescia
  job result, controller JSON, historii oraz import/export. BASIC nie rozwiazuje
  auth/tokena, konczy lifecycle na `DIFF`, nie tworzy promptu, reportu, usage,
  AI activity ani tool evidence i mapuje status wprost z deterministic result;
  pola code/AI sa walidowane jako DEEP-only. Minimalny UI nie wysyla ani nie
  pokazuje pol i stanu AI w BASIC. Przeszly testy celowane runtime/controller/
  portability, pelne `mvn -q test`, 217 testow frontendu, production frontend
  build, `mvn -q -DskipTests package` oraz `git diff --check`.

- [x] Krok 11: Pokazac nowy model i rozdzielenie trybow w Tool Workbench.
  Rozszerzyc preview o `configurationDiff` z tego samego buildera co job oraz
  jawny `aiInputGenerated`. BASIC konczy preview przed prompt preparation i
  pokazuje `AI input not generated`. DEEP pokazuje operator projection obok
  sanitized deterministic modelu, compact artifacts oraz mapowania
  original/sanitized path, token i `differenceId`; actual values nigdy nie sa
  czescia AI input. Dowod: service/controller/component tests dla obu trybow,
  zero prompt preparation w BASIC, mapowanie dynamic keys i values, preview
  expiry oraz production frontend build.
  Weryfikacja 2026-07-31: initial preview pozostaje kompaktowym summary, a
  pelny `configurationDiff` z dokladnymi wartosciami jest pobierany leniwie z
  tego samego wygasajacego snapshotu. BASIC konczy sie po projekcji
  deterministycznej, zwraca `aiInputGenerated=false`, nie przygotowuje promptu
  ani artefaktow i pokazuje jawny stan `AI input not generated`. DEEP udostepnia
  te sama projekcje operatora oraz sanitizowane mapping/anonymization,
  compact artifacts i prompt. Mapping laczy oryginalne i sanitizowane
  name/path, typy, bezpieczne tokeny oraz `differenceId`, rowniez dla
  dynamicznej sciezki; testy potwierdzaja brak actual values po stronie AI.
  Przeszly testy service/controller/store/component/API obu trybow, expiry,
  pelne `mvn -q test` (970 testow), `npm test -- --watch=false` (218 testow),
  production `npm run build`, `mvn -q -DskipTests package` oraz
  `git diff --check`.

- [x] Krok 12: Dostarczyc mode-aware operatorski renderer i adnotacje DEEP.
  Zastapic primary structural-difference table widokiem per plik. Dodac
  file/document accordion, tryb `Zmiany`/`Caly plik`, zagniezdzenie YAML/.var,
  czerwone i zolte markery z tekstowa etykieta, source/target i pelne
  wartosci. BASIC pokazuje tylko deterministic FACT/DERIVED. W DEEP backend
  dolacza krotkie komentarze ryzyka/rekomendacji przez bezposrednie i
  finding-transitive IDs z observations/functional impacts, a UI zachowuje
  nawigacje `differenceId`, agreement, impact i ownership. Historia/import
  renderuja ten sam widok; starsze rekordy uzywaja fallbacku. Dowod:
   annotation join bez token replacement, component tests dla formatow,
   filtrowania, zwijania, accessibility, braku AI, DEEP focus, absent,
   fallbacku i client export oraz production frontend build.
  Weryfikacja 2026-07-31: primary structural table zostala zastapiona
  file/document accordion z trybami `Zmiany` i `Caly plik`, zagniezdzonym
  YAML/.var, pelnymi wartosciami source/target, jawnym `ABSENT`, markerami
  koloru i tekstu oraz zwijaniem niezmienionych galezi. BASIC pozostaje
  deterministyczny i nie dostaje adnotacji AI. DEEP dolacza krotkie komentarze
  observations/functional impacts przez direct i finding-transitive IDs bez
  podmiany tokenow oraz zachowuje nawigacje do pelnego kontekstu. Ten sam
  renderer obsluguje wynik live, historie i import, a starszy rekord bez
  projekcji korzysta z fallbacku. Przeszly pelne `mvn -q clean test`
  (975 testow), `npm test -- --watch=false` (222 testy), production
  `npm run build`, wizualna weryfikacja obu trybow i nawigacji oraz
  `git diff --check`.

- [x] Krok 13: Hardening i architecture diff. Potwierdzic, ze BASIC nie
  rozwiazuje Copilot auth ani nie wywoluje prompt/artifacts/skills/runner,
  actual values sa obecne tylko w operator UI/historii/eksporcie/Workbench
  projection, a DEEP AI boundary pozostaje sanitizowana. Usunac nieuzywane
  BASIC AI branches/skill, wykonac consumer audit publicznego DTO, testy
  architektury, pelne backend i frontend suites, package oraz aktualizacje
  runtime flow, key decisions, system overview i lokalnego AGENTS.
  Weryfikacja 2026-07-31: usunieto historyczny skill i wszystkie produkcyjne
  warianty AI dla BASIC. Prompt preparation, assembler i tool session context
  jawnie przyjmuja tylko DEEP, a BASIC konczy job i Workbench preview przed
  ta granica; testy potwierdzaja brak interakcji z auth, enrichmentem,
  preparation, runnerem, reportem i adnotacjami. Consumer audit publicznego
  `configurationDiff` objal job state/API, local history, export/import,
  Workbench projection oraz frontendowy renderer; pakiet AI nie jest
  konsumentem. `PackageDependencyGuardTest` blokuje import operator projection
  i presentation przez AI. Sanitizowane artefakty/prompt DEEP nie zawieraja
  actual values, podczas gdy operator projection zachowuje je w dozwolonych
  konsumentach i ma redacted `toString`. Usunieto nieuzywany CSS starej tabeli
  i zaktualizowano runtime flow, product direction, system overview, key
  decisions, package dependencies oraz lokalny AGENTS. Przeszly pelne
  `mvn -q clean test` (975 testow), `npm test -- --watch=false` (222 testy),
  production `npm run build`, `mvn -q -DskipTests package` oraz
  `git diff --check`.

## Bramka zatwierdzania

Kroki 1-13 sa zakonczone. Kroki 10-13 zostaly osobno zatwierdzone przez
uzytkownika 2026-07-31. Plan jest zakonczony.
