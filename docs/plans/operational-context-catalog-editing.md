# Operational Context catalog editing - lokalny MVP

Status: `done`

Source need: [`../needs/operational-context-catalog-editing.md`](../needs/operational-context-catalog-editing.md)

Ostatnia aktualizacja: 2026-08-11

## Cel

Operator moze dodawac, edytowac i usuwac encje Operational Context z UI bez
recznej edycji zasobow aplikacji. Runtime, UI, tools i feature'y czytaja ten sam
biezacy katalog z lokalnej kopii:

```text
tdw-data/operational-context
```

`src/main/resources/operational-context` jest wylacznie seedem kopiowanym przy
pierwszym starcie. Istniejaca lokalna kopia nigdy nie jest automatycznie
nadpisywana nowszym seedem.

## Koncowe decyzje MVP

- Istnieje jeden katalog roboczy: `tdw-data/operational-context`.
- Katalog nie ma trybow storage ani alternatywnego runtime source.
- Lokalny, jednoosobowy MVP nie ma security ani rollout gate dla maintenance
  API.
- Katalog nie ma historii, rewizji, manifestow, slotow aktywacyjnych ani
  rollbacku. Backup jest odpowiedzialnoscia uzytkownika.
- Rownolegla edycja z kilku kart ma semantyke last-write-wins.
- Jedna mutacja zmienia dokladnie jeden logiczny dokument.
- Zmieniony dokument jest podmieniany atomowo z pliku tymczasowego w tym samym
  katalogu dopiero po dekodowaniu i walidacji kompletnego candidate catalog.
- ID istniejacej encji jest immutable. Rename jest osobna przyszla operacja
  migracji grafu.
- Delete stosuje `RESTRICT`; inbound references blokuja usuniecie i nie ma
  cascade.
- Validation i Open Questions sa wyliczonymi read modelami, nie osobnymi
  encjami CRUD.
- `system` pozostaje kanonicznym targetem relacji i code-search scope. Jawny
  ownership moze byc zapisany tylko na `system` i `bounded-context`.
- Zadne wspierane pole formularza nie wymaga surowego JSON, YAML ani Markdown.

## Dokumenty i typy

| Dokument | Kolekcja / rola | Typ CRUD |
| --- | --- | --- |
| `systems.yml` | `systems` | `system` |
| `repo-map.yml` | `repositories` | `repository` |
| `code-search-scopes.yml` | `codeSearchScopes` | `code-search-scope` |
| `processes.yml` | `processes` | `process` |
| `integrations.yml` | `integrations` | `integration` |
| `bounded-contexts.yml` | `boundedContexts` | `bounded-context` |
| `teams.yml` | `teams` | `team` |
| `glossary.yml` | `terms` | `glossary-term` |
| `handoff-rules.yml` | `handoffRules` | `handoff-rule` |
| `operational-context-index.md` | opis i zasady katalogu | read-only |

Wszystkie dziewiec typow YAML wspiera create, complete update, delete impact i
dozwolony delete.

## Runtime i zapis

### Bootstrap

1. Runtime rozwiazuje katalog przez
   `analysis.operational-context.storage-directory`, domyslnie
   `${tdw.workspace.directory}/operational-context`.
2. Gdy katalog nie istnieje, komplet dziesieciu dokumentow seeda jest zapisywany
   do sibling staging directory.
3. Dopiero kompletny staging jest przenoszony pod docelowa sciezke.
4. Gdy katalog juz istnieje, runtime odczytuje go bez ponownego kopiowania
   seeda.
5. Brak albo uszkodzenie wymaganego dokumentu jest jawnym bledem; runtime nie
   wraca po cichu do resources.

### Mutacja

1. Serwis pobiera komplet biezacych raw documents.
2. Codec dekoduje dokument wskazany przez typ i zachowuje root metadata oraz
   unknown extensions.
3. Create/update/delete modyfikuje jedna kolekcje w jednym dokumencie.
4. Caly candidate catalog przechodzi parsing, walidacje domenowa i porownanie z
   zaakceptowanym baseline findings.
5. Operacja jest odrzucana przed zapisem, jezeli wprowadza nowy albo gorszy
   blad.
6. Zmieniony dokument jest zapisywany do wygenerowanego pliku tymczasowego i
   atomowo podstawiany pod docelowa nazwe.
7. Kolejny odczyt UI, toola albo feature'a widzi aktualna lokalna kopie.

Nieudane dekodowanie, walidacja albo podmiana nie publikuje czesciowego
dokumentu. Operacja wieloplikowa nie jest czescia MVP.

## Kontrakt maintenance API

Prefix: `/api/operational-context/catalog`

| Metoda i sciezka | Znaczenie |
| --- | --- |
| `GET /capabilities` | logiczne zrodlo i lista wspieranych typow |
| `GET /entities/{type}/{id}` | kompletny kanoniczny editable payload |
| `POST /entities/{type}` | utworzenie encji |
| `PUT /entities/{type}/{id}` | kompletny update z immutable ID |
| `GET /entities/{type}/{id}/delete-impact` | inbound references i decyzja `allowed` |
| `DELETE /entities/{type}/{id}` | usuniecie bez cascade |

Write request zawiera osobne `type`, `id` i `payload`. API nie przyjmuje row,
detail, explainability ani `rawSourcePreview` DTO. Sciezki `type` i `id` musza
zgadzac sie z body. Bledy pol uzywaja stabilnych JSON Pointerow mapowanych przez
UI do prowadzonej kontrolki.

## Field/form contract

Kanoniczne znaczenie kazdego pola, zakres wartosci i wplyw runtime/AI opisuje
[`../../operational-context-maintenance/operational-context-field-guidance.md`](../../operational-context-maintenance/operational-context-field-guidance.md).

Kazdy label i nested label formularza ma dostepny z klawiatury tooltip z
czterema informacjami:

1. co wpisac,
2. rzeczywisty skutek runtime albo AI,
3. akceptowany format, liste lub zakres wartosci,
4. mocno zanonimizowany przyklad CRM, jezeli przyklad pomaga.

| Typ | Najwazniejsze prowadzone struktury |
| --- | --- |
| `system` | ownership, external participant, references, recognition signals, relations, runtime configuration directory, source coverage, gaps |
| `repository` | Git identity, references, recognition signals, relations, evidence, source coverage, gaps, AI exploration guidance |
| `code-search-scope` | target `system`/`bounded-context`, repository priorities, roles, search mode i path prefixes |
| `process` | participants, steps, boundary, lifecycle, completion signals, failure modes, data/artifacts, references, signals, relations, coverage i gaps |
| `integration` | source/targets/intermediaries/final targets, references, signals, relations, failure modes, coverage i gaps |
| `bounded-context` | local language, scope, semantic boundary, ownership, references, signals, relations, provenance evidence, AI hints, coverage i gaps |
| `team` | identity, lifecycle, aliases, use cases i recognition signals |
| `glossary-term` | definition, local boundaries, aliases, signals, typed references, related terms i AI hints |
| `handoff-rule` | applicability, exclusions, required evidence, first actions, references, affected entities, AI hints i limitations |

Unknown extensions pozostaja zachowane w complete-update round-tripie, ale nie
sa automatycznie wystawiane jako input i nie sa projektowane do AI. Pole staje
sie edytowalne dopiero po opisaniu go w field guidance, dodaniu walidacji,
projekcji runtime/AI, tooltipow i testow.

## Walidacja i graf

Maintenance validation obejmuje co najmniej:

- wymagane ID i pole display (`name`, `term` albo `title`),
- duplicate ID i mismatch path/body,
- immutable ID przy update,
- istnienie typowanych referencji i brak zabronionych self references,
- ownership tylko na systemie i bounded context,
- semantyke `ownershipStatus: explicit`,
- code-search target, repozytoria, priority, search mode i bezpieczne path
  prefixes,
- wymaganych uczestnikow integracji oraz kanoniczne relacje,
- struktury procesu: kroki, boundary, lifecycle, completion signals, failure
  modes i artifacts,
- struktury bounded context: local language, scope, semantic boundary, evidence
  i AI hints,
- repository Git identity, evidence oraz AI exploration hints,
- source coverage i actionable gaps,
- inbound references przed delete.

Validation baseline identyfikuje zaakceptowane zastane findings po logicznym
dokumencie, typie, ID, field path i rule code. Nie jest historia katalogu ani
wersjonowaniem danych. Mutacja nie moze wprowadzic nowego albo pogorszonego
bledu.

## UI i zachowanie operatora

- `Add` jest jedyna primary action naglowka zapisywalnej zakladki.
- `Save` jest primary action tylko wewnatrz editora.
- Detail drawer zachowuje `Copy`, `Open raw` i `Close`, a dodatkowo udostepnia
  `Edit` oraz `Delete`.
- Edycja odbywa sie w prawym drawerze; ID jest aktywne tylko podczas create.
- Close, zmiana taba i otwarcie innej encji chronia dirty form.
- Field errors pozostaja przy odpowiedniej kontrolce, a blad zapisu nie zamyka
  formularza.
- Delete dialog pokazuje type, ID, source i inbound references; confirm jest
  wylaczony, gdy delete jest blokowany.
- Validation i Open Questions zachowuja `Copy`; `Edit source` jest dodatkowa
  akcja tylko dla jednoznacznego wspieranego targetu.
- Po create/update/delete UI przeladowuje summary, wszystkie listy, validation,
  open questions, Signal Resolver, detail, relations, code-search i previews.
- Po update otwierany jest swiezy detail, po delete drawer jest zamykany, a
  aktywny tab i filtry pozostaja.
- Blad odczytu capabilities wylacza akcje zapisu, ale nie blokuje read view.

## Konsumenci

| Konsument | Wymagane zachowanie |
| --- | --- |
| Read API i `OperationalContextViewService` | Czyta biezaca lokalna kopie i zachowuje publiczne read DTO. |
| `opctx_*` | Pozostaja read-only i dostaja tylko jawne projekcje bez unknown extensions. |
| Incident Analysis | Nowy run korzysta z aktualnego katalogu i jawnych visibility limits. |
| Flow Explorer | Systemy, relacje i code-search scopes pochodza z biezacej kopii. |
| Config Drift Viewer | `runtime.configurationDirectory` pozostaje kanonicznym scope konfiguracji. |
| Change Verification | Repository/source matching korzysta z aktualnych map i scopes. |
| GitLab resolvers | Project path oraz search boundaries pochodza z repository i code-search scope. |
| Ownership/relation/search builders | Sa przebudowywane i walidowane z jednego odczytu candidate catalog. |

## Non-goals

- Edycja `operational-context-index.md` z UI.
- Dowolny raw YAML, Markdown albo JSON editor.
- Mutation tools dla AI albo MCP.
- Cascade delete albo automatyczny rename grafu.
- Wspoldzielony lub wieloinstancyjny katalog.
- Security, role, audyt autora i approval workflow.
- Historia zmian, diff, rollback albo wbudowany backup.
- Automatyczne scalanie nowszego seeda z lokalna kopia.
- Rozszerzanie katalogu o techniczne inventory kodu lub runtime.

## Kryteria akceptacji

- Pierwszy start tworzy kompletna lokalna kopie, a restart zachowuje zmiany.
- Nowszy seed nie nadpisuje istniejacej kopii.
- Create/update/delete dziala dla wszystkich dziewieciu typow YAML.
- Jedna mutacja zmienia jeden dokument, a blad nie pozostawia pliku
  czesciowego.
- Complete update zachowuje root metadata, pola niewidoczne w formularzu,
  unknown extensions i niedotkniete dokumenty.
- Delete referencjonowanej encji jest blokowany bez cascade.
- UI nie wysyla read projections ani raw preview.
- Kazdy input ma wartosciowy tooltip zgodny z rzeczywistym runtime/AI.
- Zadne wspierane pole nie wymaga raw JSON.
- UI i wszyscy konsumenci widza biezaca lokalna kopie po mutacji.
- Testy, fixture'y i przyklady sa mocno zanonimizowane; przyklady domenowe sa
  wylacznie CRM.
- Backend, frontend, package dependency guard i production build przechodza.
- Smoke CRUD nie dotyka rzeczywistego `tdw-data`.

## Kroki wykonania

- [x] Krok 1: Zinwentaryzowac dokumenty, pola, referencje, konsumentow i
  validation baseline. Przygotowac per-type field/form contract i JSON Pointer
  mapping. Weryfikacja: consumer audit, codec/read tests i review kontraktu.
  Polityka testow: wszystkie testy i fixture'y sa bardzo mocno
  zanonimizowane; przyklady domenowe dotycza wylacznie CRM.
- [x] Krok 2: Wydzielic source, codec, immutable read snapshot i query facade,
  zachowujac read-only `OperationalContextPort` oraz granice pakietow.
  Weryfikacja: adapter, codec, immutability, resolver i dependency guard.
  Polityka testow: wszystkie testy i fixture'y sa bardzo mocno
  zanonimizowane; przyklady domenowe dotycza wylacznie CRM.
- [x] Krok 3: Dodac idempotentny bootstrap jednej lokalnej kopii, odczyt
  kompletu dokumentow i atomowa podmiane jednego dokumentu po walidacji.
  Weryfikacja: first start, restart, brak nadpisania, invalid candidate i fault
  injection zapisu. Polityka testow: wszystkie testy i fixture'y sa bardzo
  mocno zanonimizowane; przyklady domenowe dotycza wylacznie CRM.
- [x] Krok 4: Dodac create, complete update, delete impact i restricted delete
  wraz z cienkim shared/operator API dla wszystkich typow YAML. Weryfikacja:
  service tests i MockMvc dla type/ID mismatch, duplicate ID, field errors,
  references oraz blocked/allowed delete. Polityka testow: wszystkie testy i
  fixture'y sa bardzo mocno zanonimizowane; przyklady domenowe dotycza
  wylacznie CRM.
- [x] Krok 5: Zbudowac osobne maintenance DTO/API service/facade, editor drawer,
  delete dialog, dirty guard i pelne odswiezanie projekcji po mutacji.
  Weryfikacja: API, facade, drawer, page, keyboard/focus i reload tests.
  Polityka testow: wszystkie testy i fixture'y sa bardzo mocno
  zanonimizowane; przyklady domenowe dotycza wylacznie CRM.
- [x] Krok 6: Zmigrowac glossary i handoff rules z Markdown do strukturalnego
  YAML bez utraty semantyki oraz wlaczyc oba typy do CRUD. Weryfikacja: golden
  comparison, round-trip, API i formularze obu typow. Polityka testow:
  wszystkie testy i fixture'y sa bardzo mocno zanonimizowane; przyklady
  domenowe dotycza wylacznie CRM.
- [x] Krok 7: Uproscic wynik do jednego `tdw-data/operational-context` i usunac
  z aktywnego kodu niepotrzebna infrastrukture wspoldzielonego/versioned store.
  Weryfikacja: audit kodu, bootstrap, CRUD, consumers i packaged-JAR smoke.
  Polityka testow: wszystkie testy i fixture'y sa bardzo mocno
  zanonimizowane; przyklady domenowe dotycza wylacznie CRM.
- [x] Krok 8: Dodac field guidance i zastapic wszystkie zlozone raw inputs
  prowadzonymi kontrolkami z tooltipami, walidacja i jawnymi projekcjami
  runtime/AI. Weryfikacja: test adaptera kazdego typu, nested tooltips, backend
  field paths, tool/view projections, pelna regresja i build. Polityka testow:
  wszystkie testy i fixture'y sa bardzo mocno zanonimizowane; przyklady
  domenowe dotycza wylacznie CRM.

## Dowody wykonania

- Idempotentny bootstrap tworzy dziesiec dokumentow w odseparowanym katalogu i
  nie nadpisuje istniejacej kopii.
- Packaged-JAR smoke wykonal create, update, delete i restart na tymczasowym
  katalogu CRM bez dotykania rzeczywistego `tdw-data`.
- Dziewiec typow YAML ma kompletne maintenance API i formularze.
- Glossary oraz handoff rules zostaly bezstratnie przeniesione do YAML.
- Kazdy input i nested input ma tooltip opisujacy format, zakres i skutek
  runtime/AI.
- Ownership, references, Git identity, code-search repositories, process i
  integration semantics, relations, signals, failure modes, coverage, gaps,
  system runtime, repository exploration oraz bounded-context semantics maja
  dedykowane kontrolki.
- Unknown extensions sa zachowywane przy zapisie, ale nie wyciekaja do AI.
- Finalna weryfikacja po cleanupie: frontend 44 pliki / 340 testow,
  Operational Context 28 zestawow / 162 testy,
  `PackageDependencyGuardTest` oraz production build - sukces.
- Dodatkowy UI smoke po cleanupie nie zostal uzyty jako dowod: odseparowany
  proces nie uzyskal dostepu do lokalnego cache parent POM przy zablokowanej
  sieci. Proces i jego unikalny katalog pod `target` zostaly usuniete bez
  zapisu do rzeczywistego `tdw-data`; zachowany packaged-JAR smoke CRUD z
  poprzedniej weryfikacji pozostaje dowodem runtime.

Po kazdej kolejnej zmianie tego planu nalezy zaktualizowac liczby weryfikacji,
uruchomic audit nieaktywnych mechanizmow i potwierdzic, ze smoke nie zapisuje do
rzeczywistego `tdw-data`.
