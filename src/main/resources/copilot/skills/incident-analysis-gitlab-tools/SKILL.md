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

## Staly Kontekst Repozytorium

Traktuj `gitLabGroup` i `gitLabBranch` z manifestu/promptu jako stale.

Nie zmieniaj group ani branch. Nie wymyslaj project names. Wyprowadzaj project
names i file paths tylko z evidence oraz eksploracji repozytorium.

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

Tool czyta katalog repozytoriow z operational context dla stalej session group.
Nie przeszukuje kodu i nie zmienia branch ani group.

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

## Ugruntowanie Technical Analysis

Gdy manifest zawiera `TECHNICAL_ANALYSIS_GITLAB_RECOMMENDED` i GitLab tools sa
wlaczone, wykonaj focused GitLab exploration przed finalna odpowiedzia.

Uzyj tej proby, aby napisac konkretne `technicalAnalysis` zgodne z Technical
Handoff v1. Handoff powinien wyjasnic:

- jaka capability albo operacja jest dotknieta,
- co ja uruchamia,
- jakie dane albo obiekt biznesowy sa obslugiwane,
- jakie komponenty aplikacyjne uczestnicza wysokopoziomowo,
- gdzie incydent przerywa flow,
- czy impact dotyczy read, write, validation, async processing, integration
  albo handoff.

Nie zamieniaj `technicalAnalysis` w dump kodu. Wspominaj klasy, metody, pliki i
repozytoria jako precyzyjne evidence handoffu.

Jesli GitLab attempt nie znajduje uzytecznego flow contextu, zatrzymaj sie i
napisz limitation.

## Kolejnosc Tooli

1. Preferuj attached deterministic GitLab evidence.
2. Uzyj `gitlab_list_available_repositories`, gdy projectName/GitLab path jest
   niejasny, a evidence zawiera luzne clues systemu, modułu, endpointu,
   integracji albo bounded contextu.
3. Jesli operational context listuje `codeSearchScopes` albo kilka
   `codeSearchProjects`, uzyj ich jako jednego implementation scope.
4. Uzyj `gitlab_search_repository_candidates`, gdy project/file jest niejasny
   albo potrzebujesz ranked candidates.
5. Uzyj `gitlab_find_class_references`, gdy exception, stacktrace, entity,
   repository, DTO albo mapper class jest ugruntowana.
6. Gdy class references nic nie daja w main project, sprobuj raz po pozostalych
   projektach z operational-context scope.
7. Uzyj `gitlab_find_flow_context`, gdy znasz lokalna awarie, ale broader flow
   albo collaborators sa niejasni; przekaz focused `keywords` z evidence.
8. Uzyj `gitlab_read_repository_file_outline` przed full read.
9. Uzywaj `gitlab_read_repository_file_chunk` albo
   `gitlab_read_repository_file_chunks` przed full file.
10. Uzyj `gitlab_read_repository_file` tylko gdy plik jest krotki, chunk nie
    wystarcza, potrzebny jest class-level context albo broader flow nie da sie
    zrozumiec z chunks/outlines.

Jesli dany tool nie jest dostepny w sesji, uzyj dostepnych GitLab tools i
opisz limitation tylko wtedy, gdy wplywa na diagnoze.

## Strategia Szukania

Uzywaj inputow wynikajacych z evidence:

- stacktrace class names,
- exception names,
- repository method names,
- entity names,
- DTO names,
- endpoint albo operation names,
- queue/topic/message names,
- downstream client names,
- service/container/project hints,
- operational-context `codeSearchScopes`, `codeSearchProjects`, repository
  `project`, package roots i class hints,
- repository catalog signals, np. aliases, systems, bounded contexts, package
  prefixes, endpoint prefixes, module paths,
- business identifiers z logow.

Szukaj wystarczajaco szeroko, zeby znalezc istotny project i direct
collaborators, ale nie czytaj kazdego kandydata.

Preferuj ranked candidates i role hints zamiast slepych full-file reads.

## Exception -> Entity/Repository -> DB Targeting

Gdy incydent sugeruje JPA, repository albo data issue, nie zaczynaj od broad DB
discovery.

Najpierw:

1. Ugruntuj klase z logs, stacktrace albo deterministic evidence:
   - entity,
   - repository,
   - DTO,
   - mapper,
   - validator,
   - service.
2. Jesli project jest niejasny, uzyj `gitlab_list_available_repositories`, a
   potem `gitlab_search_repository_candidates` z `projectName` z wybranego
   scope.
3. Jesli klasa jest ugruntowana, uzyj `gitlab_find_class_references` z:
   - FQCN, gdy znany,
   - simple class name,
   - hintami typu `@Entity`, `@Table`, `@Query`, `JpaRepository`,
     `JoinColumn`, `mappedBy`, repository method names, business keys albo
     exception names.
4. Czytaj entity/repository files przez outline albo focused chunks.
5. Dopiero potem kieruj DB tools kodowymi hintami table, column i relation.

Gdy manifest zawiera `DB_CODE_GROUNDING_NEEDED`, potraktuj to jako wymagana
probe przed DB table/column discovery, jesli GitLab tools sa dostepne.

Jesli nie znajdziesz entity albo repository, nie przegladaj bez konca.
Przejdz do DB discovery jako fallback i uwidocznij limitation w DB `reason`.

## Chunk-First Reading Strategy

Zacznij od najbardziej ugruntowanego miejsca:

- stacktrace file i line,
- class name z logow,
- method name z exception,
- repository method name,
- endpoint/controller/service name,
- deterministic candidate z evidence.

Czytaj na zewnatrz w tej kolejnosci:

1. failing method albo okolica stack frame,
2. containing class/service method,
3. direct collaborator:
   - repository,
   - mapper,
   - validator,
   - facade,
   - gateway,
   - downstream client,
   - scheduler,
   - listener,
   - outbox/event handler,
4. jeden albo dwa upstream/downstream kroki, gdy realnie poprawiaja wyjasnienie,
5. powiazane repo/component tylko gdy evidence wskazuje cross-component flow
   albo handoff.

## Co Wyciagac Z Kodu

Wyciagaj tylko to, co pomaga diagnozie i finalnemu UX:

- nazwa metody i odpowiedzialnosc,
- entry point albo trigger,
- repository predicate,
- sens Spring Data query z method names typu `findBy...AndStatus...`,
- jawne `@Query` predicates,
- entity/table/field names,
- JPA annotations: `@Entity`, `@Table`, `@Column`, `@JoinColumn`,
  `@JoinTable`, `mappedBy`, `@Embeddable`, `@ElementCollection`,
- ID albo business key,
- tenant/context/status filters,
- soft-delete albo validity filters,
- integration endpoint/client,
- async message/event type,
- error handling path,
- klasy importujace entity/repository i ich role,
- direct collaborators,
- ownership hints, jesli sa ugruntowane.

## Analiza Predykatu Repozytorium

Przy "not found", empty result, entity lookup, data filtering albo repository
failure zidentyfikuj:

- direct key predicate,
- business key predicate,
- query-derivation z repository method name,
- tenant/context predicate,
- status/state predicate,
- soft-delete predicate,
- validity-date predicate,
- type/discriminator predicate,
- joins albo relation loading,
- entity/relation annotations sugerujace tabele i linki do DB tools.

Te informacje maja prowadzic DB/data diagnostics.

## Wyjasnienie Szerszego Flow

Jesli failing method jest tylko lokalnym krokiem, wyjasnij surrounding flow.

Przyklady:

- controller/request handler -> service -> repository,
- listener/scheduler -> service -> outbox table -> downstream call,
- validator -> dictionary/reference lookup -> save,
- facade -> mapper -> downstream client.

Nie opisuj calego systemu. Opisz najmniejsze broader flow, ktore pomaga nowemu
analitykowi zrozumiec incydent.

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
