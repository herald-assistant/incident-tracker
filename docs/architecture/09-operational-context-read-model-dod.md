# Operational Context read model DoD

## Cel dokumentu

Ten dokument definiuje oczekiwany rezultat dla docelowego read modelu
operational context. Ma byc Definition of Done dla kolejnych krokow
implementacyjnych, migracyjnych i porzadkujacych katalog.

Dokument zbiera decyzje z dyskusji:

- operational context ma wspierac nie tylko incident analysis, ale tez flow
  explorer, functional logic explorer, dokumentacje systemu, analize zasiegu
  bledu i onboarding nowego analityka,
- dokumentacja techniczna i funkcjonalna systemu jest czesciowo nieczytelna,
  rozproszona albo nieaktualna,
- analizowany system jest czesciowo legacy, czesciowo w refaktorze i czesciowo
  przepisany,
- write model w YAML ma byc latwy do zasilania przez AI i latwy do manualnego
  review,
- read model ma byc kompletny dla FE, LLM i tools nawet wtedy, gdy write model
  trzyma dany fakt tylko w jednym miejscu.

## Problem do rozwiazania

Obecny katalog przechowuje duzo relacji w wielu miejscach. To pomaga w prostym
odczycie pojedynczego pliku, ale zwieksza koszt utrzymania:

- AI musi aktualizowac ten sam fakt w kilku plikach,
- review musi sprawdzac spojnosci cross-file,
- tokeny sa spalane na powtorzenia,
- latwo o rozjazd pomiedzy systemem, bounded contextem, integracja, procesem i
  code-search scope,
- FE i LLM dostaja czasem pelny kontekst tylko dlatego, ze YAML jest
  zdenormalizowany recznie.

Docelowo YAML ma byc zoptymalizowany jako zrodlo faktow, a kod ma budowac
z niego wygodne projekcje.

## Glowna decyzja

Najpierw definiujemy read model jako specyfikacje potrzeb analitycznych, potem
z tego wyprowadzamy write model.

Nie oznacza to, ze read model jest recznie utrzymywanym zrodlem prawdy.
Read model jest wynikiem kompilacji faktow z YAML, kodu i indeksow katalogu.

Docelowy podzial:

- write model odpowiada na pytanie: gdzie fakt jest utrzymywany,
- relation/index builder odpowiada na pytanie: jak polaczyc fakty,
- read model odpowiada na pytanie: jaki kontekst dostaje FE, LLM albo tool dla
  konkretnego zadania.

## Zasada wlasciciela faktu

Fakt nie powinien byc utrzymywany wedlug typu encji, tylko wedlug typu faktu.

Przyklad:

- fakt "system ma deployment name, health endpoint i service aliases" nalezy do
  `systems.yml`,
- fakt "repozytorium ma moduly, source roots i biblioteki wymagane do
  przeszukania kodu" nalezy do `repo-map.yml`,
- fakt "bounded context jest zaimplementowany w systemie X, module Y i repo Z"
  nalezy do mapy implementacji,
- fakt "request przechodzi przez endpoint, DB write, notifications i zewnetrzny
  system" nalezy do flow modelu,
- fakt "integracja uzywa kolejki, endpointu, payloadu i source/target system"
  nalezy do `integrations.yml`,
- fakt "team ma ownership albo handoff dla implementacji, repo, systemu albo
  integracji" nalezy do `teams.yml`.

Relacje zwrotne, podsumowania sasiedztwa i kompletne widoki dla LLM sa
wyliczane w kodzie.

## Docelowe potrzeby analityczne

Read model musi wspierac co najmniej ponizsze klasy pytan.

### 1. Gdzie jest klasa, metoda albo konfiguracja

Przyklady pytan:

- Gdzie jest klasa `X`?
- Czy klasa jest w glownym repozytorium, module, shared library czy
  wygenerowanym kliencie?
- Jakie repozytoria trzeba przeszukac dla tej klasy?
- Czy kod jest w implementacji docelowej, w implementacji zastepowanej, czy w
  rownoleglym wariancie migracyjnym?

Wymagany read model:

- code-search scope z glownym repozytorium i bibliotekami,
- priorytet repozytoriow i modulow,
- package prefixes, class hints, endpoint hints, DB hints,
- lifecycle role implementacji,
- provenance, skad scope zostal wyprowadzony.

### 2. Jak wyglada flow requestu albo use case'u

Przyklady pytan:

- Jak plynie request FE -> backend -> DB -> integracja?
- Jak endpoint A w Agreement Process wywoluje Notifications?
- Ktory bounded context obsluguje walidacje, zapis, publikacje eventu i
  zewnetrzny handoff?
- Jak wejscie HTTP laczy sie z kolejka, DB i zewnetrznym systemem?

Wymagany read model:

- flow z triggerem, krokami i outcome,
- systemy, bounded contexty, implementacje i integracje na kazdym kroku,
- endpointy, kolejki, topic, tabele, payloady, klasy i moduly,
- rozroznienie read, write, command, query, async event, scheduler,
- mozliwosc wygenerowania grafu upstream/downstream z krokow flow.

### 3. Jaki jest zasieg bledu

Przyklady pytan:

- Co moze byc dotkniete awaria endpointu A?
- Jesli kolejka X nie konsumuje wiadomosci, jakie procesy i systemy sa
  zagrozone?
- Jesli tabela Y ma bledne dane, ktore bounded contexty i procesy moga byc
  dotkniete?
- Czy blad w support capability, np. Notifications, dotyka core process?

Wymagany read model:

- blast-radius graph z wezlami system, bounded context, implementation,
  integration, flow, process, datastore, external system,
- kierunek relacji i typ wplywu,
- krytycznosc i lifecycle role,
- confidence oraz ograniczenia widocznosci,
- lista dowodow i miejsc do doczytania kodu.

### 4. Jak zrozumiec proces biznesowy lub techniczny as-is

Przyklady pytan:

- Z jakich bounded contextow sklada sie proces?
- Ktore kroki procesu sa w monolicie, ktore w mikroserwisach, a ktore w shared
  libraries?
- Czy proces uzywa starej i nowej implementacji rownolegle?
- Gdzie konczy sie odpowiedzialnosc core contextu, a zaczyna support context?

Wymagany read model:

- proces z krokami,
- bounded contexty i implementacje dla kazdego kroku,
- flow i integracje powiazane z procesem,
- code-search scopes dla krokow,
- relacje do slownika i handoff rules.

### 5. Jak onboardowac nowego analityka

Przyklady pytan:

- Co musze wiedziec o tym systemie, zeby analizowac bledy?
- Jakie sa najwazniejsze procesy, endpointy, kolejki, tabele i repozytoria?
- Czego nie mylic w lokalnym jezyku domeny?
- Gdzie szukac kodu i jakie sa typowe pulapki migracyjne?

Wymagany read model:

- onboarding pack dla systemu/procesu/bounded contextu,
- glossary i local language,
- glowne flows,
- top integrations,
- code-search scopes,
- known gaps,
- migration/lifecycle notes,
- ownership i handoff.

### 6. Jak wygenerowac dokumentacje techniczna albo funkcjonalna

Przyklady pytan:

- Wygeneruj opis flow dla endpointu A.
- Wygeneruj diagram systemow i integracji dla procesu B.
- Opisz implementacje bounded contextu C.
- Opisz as-is zaleznosci pomiedzy stara i nowa implementacja.

Wymagany read model:

- documentation view z nazwami czytelnymi dla czlowieka,
- stabilny graf flow,
- provenance do YAML/kodu,
- wyrazne ograniczenia widocznosci,
- rozdzielenie faktow potwierdzonych od heurystyk.

## Docelowe artefakty read modelu

### Entity envelope

Podstawowy obiekt przekazywany FE albo LLM dla pojedynczej encji.

Musi zawierac:

- canonical identity: `id`, `type`, `name`, `summary`, `lifecycleStatus`,
- raw source facts istotne dla typu encji,
- derived neighbors: systemy, bounded contexty, integracje, procesy,
  repozytoria, implementacje, teams, flows,
- code-search bundle,
- flow bundle,
- blast-radius hints,
- ownership/handoff,
- glossary terms,
- known gaps,
- provenance dla kazdej relacji wyliczonej.

### Flow read model

Obiekt dla request-flow albo use-case-flow.

Musi zawierac:

- trigger: channel, source, target, endpoint/event/scheduler,
- ordered steps,
- dla kazdego kroku: system, bounded context, implementation, repository,
  module, code hints, DB hints, integration hints,
- wejscia i wyjscia kroku,
- failure modes i observability hints,
- upstream/downstream wyliczony z krokow,
- provenance.

### Code search read model

Obiekt dla GitLab tools i dla promptu LLM.

Musi zawierac:

- primary repository,
- imported/shared libraries wymagane do analizy,
- repository priority,
- include/exclude roots,
- module ids,
- package prefixes,
- class hints,
- endpoint hints,
- queue/topic hints,
- database/entity hints,
- migration role implementacji,
- reason, dlaczego scope obejmuje dane repo/modul.

LLM nie powinien zgadywac, czy klasa jest w glownym repozytorium czy w
bibliotece.

### Blast radius read model

Obiekt dla analizy zasiegu bledu.

Musi zawierac:

- punkt startowy: endpoint, class, table, queue, topic, integration, system,
  bounded context albo flow,
- dotkniete flows,
- dotkniete procesy,
- dotkniete integracje i zewnetrzne systemy,
- dotkniete bounded contexty i implementacje,
- read/write direction,
- criticality,
- confidence,
- visibility limitations,
- suggested next evidence to fetch.

### Onboarding read model

Obiekt dla nowego analityka.

Musi zawierac:

- najwazniejsze systemy i bounded contexty,
- mapowanie local language,
- glowne procesy i flows,
- top repozytoria i code-search scopes,
- typowe awarie i handoff rules,
- migracje i rownolegle implementacje,
- czego nie mylic,
- gaps i ograniczenia katalogu.

## Docelowy write model

Write model ma byc minimalny, ale wystarczajacy do wygenerowania powyzszych
read modeli.

### `systems.yml`

Wlasciciel faktow o runtime i system identity:

- canonical system id,
- aliases,
- deployment/service/container/application names,
- health endpoint,
- criticality,
- runtime markers,
- platform dependencies tylko wtedy, gdy sa faktem runtime, a nie pochodna
  integracji.

Nie powinien recznie utrzymywac:

- listy wszystkich integracji systemu,
- listy wszystkich bounded contextow,
- backlinkow do samego siebie,
- relacji system-system, ktore wynikaja z `integrations.yml` albo `flows.yml`.

### `repo-map.yml`

Wlasciciel faktow o kodzie:

- repositories,
- GitLab paths,
- modules,
- source roots,
- build files,
- generated sources,
- shared/imported libraries,
- code-search scopes,
- package/class/endpoint/db hints.

Ten plik odpowiada za to, zeby GitLab tool mogl przeszukac glowny kod i
biblioteki bez zgadywania przez LLM.

### `implementation-map.yml`

Docelowo potrzebny jako osobny plik albo wyrazna sekcja write modelu.

Wlasciciel faktow:

- bounded context jest zaimplementowany w konkretnym systemie,
- implementacja mieszka w konkretnym repo/module/package,
- implementacja ma role lifecycle: `primary`, `source-implementation`,
  `target-implementation`, `parallel`, `fallback`, `deprecated`,
  `being-replaced`,
- implementacja moze wskazywac code-search scope,
- implementacja moze byc powiazana z migracja albo wariantem docelowym.

Ten model jest kluczowy, bo bounded context moze:

- byc w dedykowanym mikroserwisie,
- byc w module monolitu,
- miec stara i nowa implementacje rownolegle,
- odwolywac sie jeszcze do starej implementacji,
- korzystac z technicznego/support bounded contextu.

### `bounded-contexts.yml`

Wlasciciel faktow semantycznych:

- local language,
- granice domeny,
- core/support/technical context,
- invariants,
- concepts,
- czego nie mylic,
- analysis hints.

Nie powinien byc glownym miejscem utrzymywania relacji do systemow,
repozytoriow i integracji. Te relacje powinny wynikac z implementation map,
integrations i flows.

### `integrations.yml`

Wlasciciel faktow o kontrakcie komunikacji:

- source participant,
- target participant,
- channel,
- endpoint, queue, topic, database, file albo payload,
- auth/consistency/retry,
- criticality,
- failure modes.

Kanonicznym miejscem relacji integracja-system powinno byc `participants`.
`references.systems` i `references.boundedContexts` nie powinny powtarzac tego
samego faktu.

### `flows.yml`

Docelowo potrzebny jako osobny plik albo wyrazna sekcja write modelu.

Wlasciciel faktow o przebiegu requestu albo use case'u:

- trigger,
- ordered steps,
- endpointy,
- DB reads/writes,
- calls do support bounded contextow,
- integracje zewnetrzne,
- async events,
- schedulery,
- outcome,
- failure modes.

To z flows read model ma generowac upstream/downstream dla dokumentacji,
analizy zasiegu bledu i flow explorera.

### `processes.yml`

Wlasciciel faktow procesowych:

- proces biznesowy albo techniczny,
- kroki procesu,
- process boundary,
- outcome,
- participants na poziomie procesu,
- relacja do flows i bounded contextow, gdy jest faktem procesu.

Nie powinien recznie utrzymywac pelnego call graphu technicznego.

### `teams.yml`

Wlasciciel ownershipu i routing:

- responsibilities,
- target type i target id,
- role,
- confidence,
- evidence,
- handoff hints.

Ownership moze dotyczyc systemu, repozytorium, implementacji, integracji,
procesu albo flow step. Przy migracji ownership implementacji jest czesto
precyzyjniejszy niz ownership calego bounded contextu.

### `glossary.md`

Wlasciciel local language:

- terminy,
- aliasy,
- akronimy,
- match signals,
- czego nie mylic,
- wskazowki dla LLM.

Glossary nie powinno byc glownym miejscem utrzymywania grafu relacji.

### `handoff-rules.md`

Wlasciciel instrukcji operacyjnych:

- kiedy routowac,
- jaka evidence jest potrzebna,
- pierwsze akcje,
- partnerzy.

Handoff rules moga linkowac do encji, ale nie powinny byc kanonicznym zrodlem
relacji system-process-integration.

## Przykladowy flow write model

```yaml
flows:
  - id: agreement-submit-with-notification
    name: Agreement submit with notification
    type: request-flow
    lifecycleStatus: active
    trigger:
      channel: http
      source:
        systemId: operator-frontend
      target:
        systemId: clp-agreement-process
        endpoint: POST /clp/agreement/processhandler/{ttaId}/submit
        boundedContextId: agreement-process-management
        implementationId: agreement-process-management-in-clp-agreement
    steps:
      - id: validate-request
        kind: code
        boundedContextId: agreement-process-management
        implementationId: agreement-process-management-in-clp-agreement
        codeSearchScopeId: agreement-process-management-scope
      - id: write-process-state
        kind: database-write
        datastoreId: clp-oracle-db
        tables:
          - PROCESS
          - PROCESS_EVENT
      - id: send-notification
        kind: internal-capability-call
        boundedContextId: notifications
        integrationId: agreement-process-to-notifications
      - id: deliver-external-message
        kind: external-integration
        integrationId: notifications-to-external-system-b
    outcome:
      successSignals: []
      failureSignals: []
```

## Przykladowy implementation write model

```yaml
implementations:
  - id: agreement-process-management-in-clp-agreement
    boundedContextId: agreement-process-management
    systemId: clp-agreement-process
    repositoryId: clp-agreement-process-repo
    moduleIds:
      - clp-agreement-processhandler
    packagePrefixes:
      - pl.santander.clp.agreement.processhandler
    lifecycleRole: primary
    migrationStatus: active
    codeSearchScopeId: agreement-process-management-scope

  - id: product-management-in-backend
    boundedContextId: product-management
    systemId: clp-backend
    repositoryId: clp-backend-repo
    moduleIds:
      - clp-product
    packagePrefixes:
      - pl.santander.clp.product
    lifecycleRole: source-implementation
    migrationStatus: being-replaced
    replacedBy:
      - product-management-in-target-service
```

## Provenance

Kazda relacja w read modelu, ktora nie jest literalnym polem source YAML, musi
miec provenance:

```yaml
relatedIntegrations:
  - id: clp-agreement-to-task-manager
    relationType: derived
    derivedFrom:
      file: integrations.yml
      entityId: clp-agreement-to-task-manager
      field: participants
```

Provenance jest wymagane, bo:

- FE moze pokazac, dlaczego relacja istnieje,
- LLM moze uczciwie wskazac ograniczenia widocznosci,
- reviewer moze znalezc miejsce poprawki w write modelu,
- walidator moze odroznic fakt kanoniczny od projekcji.

## Relation/index builder

Docelowo aplikacja powinna miec warstwe budujaca indeks relacji z write modelu.

Odpowiedzialnosci:

- wczytanie source YAML/MD,
- walidacja kanonicznych faktow,
- zbudowanie grafu encji,
- zbudowanie backlinkow,
- zbudowanie read modeli dla FE i LLM,
- deduplikacja relacji,
- provenance,
- limity wielkosci payloadu dla LLM,
- selekcja kontekstu zalezne od pytania.

Builder nie powinien byc zalezy od incident analysis. To reusable capability
operational context.

## Reguly walidacji write modelu

DoD wymaga walidatora, ktory wykrywa co najmniej:

- self-reference encji,
- ten sam fakt relacyjny utrzymywany w dwoch plikach,
- `participants` i `references` powtarzajace te same systemy albo bounded
  contexty w jednej integracji,
- reczne backlinki, ktore mozna wyliczyc,
- relacje process-process utrzymywane w obu kierunkach bez osobnego znaczenia,
- bounded-context relations utrzymywane symetrycznie bez osobnego znaczenia,
- code-search scope bez repozytorium podstawowego,
- implementation bez systemu, repozytorium albo code-search scope,
- flow step bez implementacji, integracji, datastore albo wyjasnionej luki,
- relacje bez lifecycle/confidence tam, gdzie system jest w migracji,
- brak provenance w read modelu.

## Reguly selekcji kontekstu dla LLM

LLM nie powinien dostawac calego katalogu domyslnie.

Minimalna selekcja:

- dla pytania o klase: code search read model + entity envelope dla
  najbardziej prawdopodobnych implementacji,
- dla pytania o endpoint: flow read model + code search bundle + DB/integration
  hints,
- dla pytania o blad w kolejce: integration envelope + affected flows +
  affected processes + code search dla producenta i konsumenta,
- dla pytania o proces: process envelope + flows + bounded context
  implementations,
- dla onboardingu: onboarding read model z limitowanym top-N sasiedztwem,
- dla dokumentacji: documentation read model, a nie raw catalog dump.

Kazdy payload dla LLM powinien miec:

- task-specific context,
- related entities z limitem,
- provenance,
- known gaps,
- suggested tools to fetch more evidence.

## Definition of Done

Implementacja jest gotowa, gdy:

1. Istnieje opisany kontrakt read modelu dla:
   - entity envelope,
   - flow read model,
   - code search read model,
   - blast radius read model,
   - onboarding read model.
2. Kazdy read model ma testy snapshot/contract pokazujace minimalny i typowy
   payload.
3. Relation/index builder generuje backlinki i sasiedztwo z write modelu, a nie
   z recznie utrzymywanych kopii relacji.
4. Kazda relacja wyliczona ma provenance.
5. YAML write model ma macierz wlascicieli faktow i walidator wykrywajacy
   redundancje.
6. LLM tools dostaja code-search scope obejmujacy glowne repozytorium i
   wymagane biblioteki.
7. Flow read model potrafi opisac request:
   FE -> endpoint -> bounded context -> DB -> support capability ->
   external system.
8. Blast radius read model potrafi wystartowac z endpointu, klasy, tabeli,
   kolejki, integracji, systemu albo bounded contextu.
9. Onboarding read model potrafi wygenerowac skondensowany opis systemu bez
   dumpowania calego katalogu.
10. FE korzysta z read modelu/projekcji, a nie z recznie zdenormalizowanych
    relacji w YAML.
11. Maintenance prompts zakazuja recznych backlinkow i wskazuja wlasciciela
    faktu.
12. Migracja istnieje jako seria malych krokow, a nie jednorazowy rewrite
    katalogu.

## Rekomendowana kolejnosc prac

1. Zdefiniowac kontrakty DTO read modelu bez migracji YAML.
2. Dodac relation/index builder nad obecnym katalogiem.
3. Dodac provenance i deduplikacje relacji.
4. Dodac walidator redundancji jako warning-only.
5. Dodac `implementation-map.yml` albo rownowazna sekcje.
6. Dodac `flows.yml` albo rownowazna sekcje.
7. Podpiac code-search read model do GitLab tools.
8. Podpiac entity/flow/blast-radius read models do operator API.
9. Zaktualizowac maintenance prompts.
10. Migrowac YAML partiami:
    - `integrations.yml`,
    - `systems.yml`,
    - `bounded-contexts.yml`,
    - `processes.yml`,
    - `repo-map.yml`,
    - `teams.yml`.
11. Zaostrzyc walidator redundancji z warning na error dla nowych wpisow.

## Ostateczny rezultat

Ostateczny rezultat to operational context jako kompilowalny katalog:

- YAML/MD sa prostym, reviewowalnym write modelem,
- kod buduje spójny graf relacji,
- FE dostaje wygodne widoki bez recznej redundancji w YAML,
- LLM dostaje task-specific context, nie caly katalog,
- GitLab tools wiedza, ktore repozytoria i biblioteki przeszukac,
- flow explorer i blast-radius analysis powstaja z tych samych faktow,
- onboarding nowego analityka jest generowany z read modelu,
- kazda odpowiedz AI moze pokazac provenance i ograniczenia widocznosci.

To jest kierunek, ktory obniza koszt maintenance przez AI, a jednoczesnie nie
odbiera LLM pelnego kontekstu potrzebnego do analizy systemu as-is.
