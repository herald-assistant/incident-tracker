---
name: incident-analysis-gitlab-tools
description: Playbook korzystania z GitLab tools podczas analizy incydentu: rozumienie failing code path, predykatow repozytorium, integracji, flow i technicznego handoffu.
---

# Analiza Incydentu Z GitLab Tools

Uzywaj tego skilla, gdy logs, stacktrace, deterministic GitLab evidence albo
runtime signals sugeruja, ze do zrozumienia incydentu potrzebny jest kontekst
kodu:

- failing method,
- repository predicate,
- entity albo DTO mapping,
- integration path,
- validation path,
- async/event flow,
- operacja i flow potrzebne do handoffu.

## Rola Wobec Orkiestratora

Ten skill jest diagnostycznym playbookiem kodowym wybieranym przez
`incident-analysis-orchestrator` po wstepnym researchu flow i lokalizacji
przerwania.

Nie zaczynaj diagnozy od nowa. Twoim zadaniem jest ugruntowac kodem te
elementy, ktore sa potrzebne do rozroznienia hipotez i do napisania
`technicalAnalysis`.

## Wejscie Oczekiwane Od Orkiestratora

Przyjmij od orkiestratora:

- fingerprint incydentu,
- result-sufficient use-case flow albo jego aktualny szkic,
- failure point na flow,
- aktywne klasy bledu i hipotezy,
- evidence gaps z manifestu,
- znane repozytoria, code-search scopes, klasy, metody, endpointy albo
  package hints.

Jezeli brakuje tych danych, wykonaj tylko minimalne code search potrzebne do
ich uzyskania albo wroc do orkiestratora z prosba o doprecyzowanie flow.

## Klasy Bledu Obslugiwane Przez Ten Skill

Uzywaj tego skilla przede wszystkim dla klas:

- `code_mapping_or_type_conversion`,
- `code_query_or_repository_logic`,
- `code_validation_or_business_rule`.

Uzywaj go wspierajaco dla:

- `integration_downstream_failure`, gdy trzeba zrozumiec klienta integracji,
  endpoint, payload mapping albo error handling,
- `async_or_process_state`, gdy trzeba zrozumiec listener, scheduler, outbox,
  retry flow albo event handler,
- `data_missing`, `data_predicate_mismatch` i
  `data_orphan_or_stale_reference`, gdy DB diagnostics wymaga code-first
  ugruntowania entity, repository, table, relation albo predicate.

## Hipotezy, Ktore Skill Ma Potwierdzic Albo Obalic

Ten skill ma pomagac odpowiedziec:

- czy blad wynika z logiki kodu, mapowania, walidacji, predykatu albo
  integracyjnego calla,
- czy kod tylko ujawnia problem danych, runtime albo downstream,
- jaki jest najmniejszy techniczny flow od entry pointu do failure point,
- jakie klasy/metody/pliki sa evidence dla handoffu,
- jakie hints trzeba przekazac do DB, runtime albo integration diagnostics.

Nie traktuj samego znalezienia klasy jako root cause. Root cause wymaga
mechanizmu: warunek, input, predykat, mapping, call albo error path.

## Testy Rozrozniajace

Dla aktywnej hipotezy wybierz najmniejszy GitLab check, ktory moze ja
potwierdzic, oslabic albo obalic:

- dla mapping/type/null: przeczytaj mapper, repository method albo converter i
  porownaj expected type/state z tym, co pokazuje evidence,
- dla repository logic: ustal method name, derived predicate, `@Query`, entity
  annotations i filtry status/tenant/deleted/validity,
- dla validation/business rule: przeczytaj walidator albo service decision i
  ustal, jaki warunek odrzuca flow,
- dla integration failure: przeczytaj client/gateway, endpoint, payload
  mapping, retry/error handling i downstream boundary,
- dla async/process: przeczytaj listener/job/outbox handler i ustal event,
  state transition oraz miejsce retry/error handling.

## Wklad Do Wyniku

Po uzyciu tego skilla zwroc do orkiestratora material w tej postaci:

- `technicalAnalysis`: repozytorium, plik, klasa/metoda, entry point,
  execution flow, failing method, direct collaborators, predicate/mapping/call
  i evidence dla Technical Handoff v1,
- `functionalAnalysis`: krotkie tlumaczenie, jaka operacja biznesowo-systemowa
  jest obslugiwana, gdzie flow sie przerywa i czy potrzebny jest handoff,
- `visibilityLimits`: czego nie udalo sie potwierdzic w kodzie albo ktory
  fragment wymaga DB/runtime/downstream visibility,
- `confidence`: czy code hypothesis jest confirmed, strong_hypothesis,
  weak_hypothesis albo rejected.

## Kiedy Wrocic Do Orkiestratora

Zakoncz GitLab exploration i wroc z wynikiem, gdy:

- failure point i direct collaborators sa jasne dla handoffu,
- repository predicate, mapping, validation albo integration call jest
  zrozumiany,
- dalszy kod nie rozroznia hipotez,
- pytanie przeszlo do DB, runtime, downstream albo ownership visibility,
- code-search scope zostal wyczerpany w sposob focused.

Jesli GitLab nie potwierdza hipotezy kodowej, napisz to wprost i przekaz
orkiestratorowi, ktora klasa bledu powinna byc sprawdzona dalej.

## Staly Kontekst Repozytorium

Traktuj `gitLabBranch` z manifestu/promptu jako staly incident context. Gdy
wolanie GitLab toola wymaga branch/ref, przekaz jawnie `branchRef` ustawiony na
wartosc `gitLabBranch` albo branch zwrocony przez poprzedni GitLab tool result.

Nie przekazuj `gitLabGroup` do GitLab tools. Backend rozstrzyga GitLab group
przez operational context albo konfiguracje. `gitLabGroup` z
manifestu/promptu jest kontekstem diagnostycznym, nie model-facing inputem
toola.

Nie zmieniaj branch. Nie wymyslaj project names. Wyprowadzaj `projectName` i
file paths tylko z evidence, operational context albo eksploracji repozytorium.
Gdy tool dotyczy kodu konkretnej aplikacji/systemu i prompt albo operational
context podaje nazwe aplikacji, przekaz `applicationName` jako dodatkowe
zawężenie scope'u.

## Semantyczny Code-Search Scope

Bounded context, process, system albo integration moze byc implementowany przez
wiecej niz jeden GitLab project. Operational context moze podac
`codeSearchScope` dla semantic target, obejmujacy:

- glowne repozytorium serwisu,
- biblioteki wewnetrzne,
- shared domain modules,
- generated clients albo integration libraries,
- supporting modules pakowane do glownej implementacji albo przez nia wolane.

Gdy operational context podaje `codeSearchScopeIds`, `codeSearchRepoIds`,
`codeSearchProjects`, repository `project`, package roots albo class hints dla
dopasowanego targetu, traktuj cala liste jako jeden implementation search
scope.

Jesli sa `codeSearchRepositoryRoles`, zaczynaj od `primary-implementation` albo
priority `1`, a potem przechodz przez supporting libraries, generated clients,
integration adapters, legacy modules albo collaborators wedlug priorytetu.

Jesli ugruntowana klasa, entity, DTO, mapper, client albo repository nie zostala
znaleziona w glownym repozytorium, nie uznawaj kodu za niedostepny po jednym
lookupie. Wykonaj focused GitLab attempt przez pasujace `codeSearchScopes`,
pozostale `codeSearchProjects` albo dopasowane repository projects.

Uzyj tego szczegolnie, gdy:

- stacktrace class wyglada jak biblioteka albo shared module,
- failing method deleguje do shared client, mapper, validator albo repository
  abstraction,
- deterministic code evidence pokazuje entry point serwisu, ale nie klase,
  ktora decyduje o failing predicate,
- DB grounding zalezy od entity/repository w bibliotece.

Trzymaj search ograniczony: przeszukaj wskazane repozytoria konkretnymi
class/package/method hints, przeczytaj najlepszy outline/chunk i zatrzymaj sie,
gdy nie ma uzytecznego kodu biblioteki.

## Katalog Dostepnych Repozytoriow

Uzyj `gitlab_list_available_repositories`, gdy istotne repozytorium nie jest
ugruntowane przez logs, deterministic evidence albo code references, ale
incydent zawiera luzne clues o repozytorium, komponencie, module, package,
endpointcie, integracji, systemie, bounded context albo procesie.

Tool czyta katalog repozytoriow z operational context dla backendowo
rozstrzygnietej GitLab group. Nie przeszukuje kodu i nie zmienia branch.
Przekaz `branchRef` jawnie.

Uzyj zwroconego `projectName` jako inputu dla kolejnych GitLab search, flow,
outline, chunk i read tools. `gitLabPath`, `summary` i metadata repozytorium
traktuj jako kontekst rozrozniajacy.

Gdy odpowiedz zawiera `codeSearchScopes`, preferuj dopasowany semantic scope
zamiast pojedynczego repozytorium. Przekaz wszystkie `projectNames` z tego
scope razem do search/flow/class-reference tools.

Dopasowuj incident clues do:

- repository `name`, `aliases`, `projectName`, `gitLabPath`,
- `systems`, `runtimeComponents`, `boundedContexts`, `processes`,
  `integrations`,
- `packagePrefixes`, `endpointPrefixes`, `modulePaths`,
- `codeSearchScopes[].target.type/id`, repository roles, package prefixes,
  class hints i traversal guidance.

Preferuj jeden catalog call na poczatku cross-repository investigation. Nie
powtarzaj go, chyba ze nowe evidence wskazuje inna rodzine repozytoriow.

## `reason` Dla GitLab Tooli

Kazdy GitLab tool call musi miec opcjonalny argument `reason`.

Pisz `reason` po polsku jako jedno krotkie praktyczne zdanie dla junior
analityka. Wyjasnij, co tool call ma potwierdzic albo doprecyzowac. Nie
umieszczaj hidden reasoning, dlugiej analizy ani chain-of-thought.

Dobre przyklady:

- `Sprawdzam fragment metody ze stacktrace, zeby potwierdzic predykat repozytorium.`
- `Szukam uzyc klasy encji, zeby ustalic gdzie zaczyna sie przeplyw biznesowy.`
- `Czytam serwis i repozytorium, zeby wyjasnic juniorowi ktory warunek odcina dane.`

## Kiedy Nie Uzywac GitLab Tooli

Wyjatek: gdy manifest zawiera `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` i GitLab
tools sa wlaczone, wykonaj jeden focused GitLab attempt, zeby poprawic
`technicalAnalysis`, nawet gdy deterministic code evidence tlumaczy lokalna
awarie.

Nie wywoluj GitLab tools, gdy:

- deterministic code evidence zawiera istotny stack frame, surrounding method,
  direct collaborator i wystarczajacy flow context dla technical handoff, a
  `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` nie jest listed,
- logs, runtime signals i code evidence juz wyjasniaja prawdopodobny problem
  oraz lokalizacje/handoff, a luka GitLab nie jest listed,
- incydent jest wyraznie poza widocznoscia repozytorium,
- inny tool jest bardziej bezposredni dla hipotezy, np. DB tools dla
  konkretnego data check.

Jesli blad techniczny jest jasny, ale `technicalAnalysis` byloby zbyt plytkie,
uzyj GitLab tools, aby doczytac tyle surrounding code, ile potrzeba do
wyjasnienia flow i handoffu.

## Cel Eksploracji

Celem nie jest zmapowanie calego repozytorium.

Celem jest najmniejsze przydatne cross-file i, gdy potrzebne, cross-repository
flow, ktore wyjasnia:

- gdzie incydent sie zaczyna,
- ktory komponent odbiera albo tworzy failing operation,
- gdzie lezy failing method,
- jakie dane albo request wchodza do tej metody,
- ktore repository, mapper, validator, integration client, listener, scheduler
  albo outbox handler uczestniczy,
- gdzie incydent przerywa funkcje,
- co beginner albo mid-level analyst powinien sprawdzic dalej,
- ktory team albo owner moze dostac handoff, jesli evidence to wspiera.

## Focused Code Exploration

Gdy manifest zawiera `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` albo
`DB_CODE_GROUNDING_NEEDED`, wykonaj tylko taka eksploracje, ktora zmienia
diagnoze, DB targeting albo handoff.

Najpierw nazwij typ luki, ktora chcesz domknac:

- jezeli znasz konkretna klase i metode, ale brakuje tego, co dzieje sie dalej
  w use case, integracji, handoffie albo sciezce persistence, uzyj
  `gitlab_build_java_method_use_case_context` z `maxResults`,
- jezeli brakuje tresci konkretnej metody, predykatu, mappera, walidatora,
  helpera albo client calla, uzyj `gitlab_read_java_method_slice`,
- jezeli brakuje roli klasy, adnotacji, dziedziczenia, pol albo sygnatur metod,
  uzyj `gitlab_read_repository_file_outline`,
- jezeli parser Java albo zakres metody nie pasuje do pytania, dopiero wtedy
  uzyj focused chunk/chunks,
- full file zostaw jako ostatni krok dla krotkiego pliku albo przypadku, gdzie
  poprzednie odczyty nie wystarczaja do handoffu.

Preferowana kolejnosc:

1. deterministic GitLab evidence,
2. `gitlab_list_available_repositories` tylko gdy project/scope jest niejasny,
3. `gitlab_search_repository_candidates` dla ranked project/file candidates,
4. `gitlab_find_class_references` dla ugruntowanej klasy, entity, repository,
   DTO, mappera, validatora albo klienta,
5. `gitlab_find_flow_context` dla direct collaborators i broader flow,
6. `gitlab_build_java_method_use_case_context` dla znanej klasy/metody, gdy
   trzeba kontynuowac flow dalej niz lokalne cialo metody,
7. `gitlab_read_java_method_slice` dla znanego pliku Java i metody, bo zwraca
   wybrane metody z potrzebnymi polami, importami i prywatnymi helperami bez
   dumpu calej klasy,
8. `gitlab_read_repository_files_by_path` tylko dla ugruntowanej listy sciezek
   plikow, np. po wyniku `gitlab_build_endpoint_use_case_context`,
9. outline/chunk/chunks przed pojedynczym full file, gdy method slice nie pasuje
   do pytania albo parser Java nie wystarcza,
10. pojedynczy full file tylko gdy krotki plik albo chunk nie wystarcza.

Dla `gitlab_read_java_method_slice` zwykle wystarczy `methodSelectors` z
`methodName`. `lineStart` jest opcjonalne; uzyj go tylko, gdy trzeba zawęzic
wynik do konkretnego overloadu.

Szukaj po evidence, nie po ciekawosci:

- stacktrace class/method, exception, endpoint/operation,
- entity/repository/DTO/mapper/validator,
- queue/topic/event, scheduler/listener/outbox,
- downstream client/path/status,
- service/container/project i operational-context code-search scope,
- business key tylko jako pomocniczy clue, nie jako root cause.

## Co Zwrocic Z Kodu

Zwroc do orkiestratora tylko material, ktory wspiera diagnoze:

- entry point, failing method i direct collaborators,
- repository predicate: key, tenant/context, status/state, deleted/active,
  validity, type, joins/relation loading,
- entity/table/column/relation hints dla DB diagnostics,
- mapper/converter expected vs actual type/value,
- validator/business rule i warunek odrzucenia,
- integration client, endpoint, payload mapping, retry/error handling,
- async listener/scheduler/outbox handler i state transition,
- file/class/method evidence dla handoffu.

Nie formatuj pelnego `technicalAnalysis`; dostarcz material, ktory
`incident-technical-handoff` moze pozniej zsyntetyzowac.

## DB Targeting Z Kodu

Dla JPA/repository/data symptoms nie zaczynaj od broad DB discovery, jezeli kod
moze ugruntowac table albo predicate.

Minimalny path:

```text
exception/log class -> entity/repository/mapper/service -> annotations/query
  -> table/columns/relation/predicate hints -> DB diagnostics
```

Jesli GitLab nie znajduje entity/repository w focused scope, zatrzymaj sie i
przekaz limitation do DB `reason` zamiast czytac kolejne niepowiazane pliki.

## Heurystyki Java/Spring

Dla ORM i persistence flow najpierw czytaj implementacje Java/Spring:

- entity annotations: `@Entity`, `@Table`, `@Column`, `@JoinColumn`,
  `@OneToMany`, `@ManyToOne`,
- repository methods, derived query names, `@Query`, specifications,
  criteria/query builders,
- service predicates, mappers i validators, ktore decyduja o filtrach danych.

Liquibase, Flyway albo DDL traktuj jako fallback, gdy adnotacje albo repository
query sa niepelne, sprzeczne albo incydent dotyczy migracji/schema drift.

Dla integracji spodziewaj sie czestych wzorcow:

- Feign clients, RestClient, WebClient albo RestTemplate,
- publikacja zdarzen przez `StreamBridge`,
- konsumpcja przez `Consumer`, `Function` albo `Supplier`,
- bindingi w `application.yml`, `application.yaml` albo `application.properties`.

YAML/properties czytaj dopiero wtedy, gdy kod pokazuje konkretnego clienta,
binding, channel, topic, queue albo property prefix. Nie zaczynaj od szerokiego
przegladania konfiguracji.

## Flow Do Wyjasnienia

Czytaj na zewnatrz od najbardziej ugruntowanego miejsca:

1. failing method albo okolica stack frame,
2. containing class/service method,
3. direct collaborator: repository, mapper, validator, gateway, downstream
   client, listener, scheduler albo outbox handler,
4. jeden upstream/downstream krok tylko gdy poprawia diagnoze albo handoff.

Nie opisuj calego systemu. Opisz najmniejsze broader flow, ktore pomaga
zrozumiec divergence point.

## Warunki Zatrzymania

Zatrzymaj czytanie kodu, gdy:

- likely failure point jest jasny,
- repository predicate albo integration call jest zrozumialy,
- affected operation i flow da sie wyjasnic newcomerowi,
- direct upstream/downstream collaborator jest wystarczajaco jasny dla handoffu,
- dalsze read bylyby spekulacyjne,
- pozostale pytanie wymaga DB/runtime/downstream visibility, nie wiecej kodu.

Nie zatrzymuj sie tylko dlatego, ze lokalny exception jest jasny. Zatrzymaj sie
dopiero, gdy techniczna awaria i affected flow sa wystarczajaco jasne.

## Budzet Kontekstu

Liczba tool calli jest mniej wazna niz jakosc kontekstu.

Dopuszczalna jest szersza eksploracja GitLaba, gdy realnie poprawia
`technicalAnalysis`, handoff albo next action.

Preferuj:

- ranked candidate searches,
- file outlines,
- focused chunks,
- male batches powiazanych chunks,

zamiast:

- wielu duzych full-file reads,
- powtarzanych odczytow tego samego pliku,
- czytania niepowiazanych kandydatow,
- dumpowania kodu do finalnej odpowiedzi.

## Grounding

Gdy opisujesz zachowanie kodu, wspomnij wspierajaca klase, metode, plik albo
tool result.

Jesli code context pozostaje niepelny, napisz limitation zamiast zgadywac.
