---
name: flow-explorer-gitlab-tools
description: Algorytm uzycia GitLab tools w Flow Explorerze - artifact-first, focused reads i Java/Spring heurystyki bez masowego przegladania repozytorium.
---

# Skill GitLab Tools Dla Flow Explorera

Uzywaj tego skilla tylko wtedy, gdy initial evidence nie wystarcza do
aktywnego `goal` albo `sectionModes`.

## Rola

GitLab tools maja domknac konkretna luke w rozumieniu wybranego endpointu.
Nie sluza do potwierdzania danych, ktore juz sa w artefaktach, ani do
eksploracji repozytorium z ciekawosci.

Przed kazdym tool call ustal:

- ktora sekcja wyniku zalezy od odpowiedzi,
- jaka klasa, metoda, plik, binding albo kontrakt jest celem,
- po jakim wyniku przestaniesz czytac dalej.

## Scope Tooli

Dozwolone GitLab tools:

- `gitlab_list_available_repositories`,
- `gitlab_list_repository_endpoints`,
- `gitlab_build_endpoint_use_case_context`,
- `gitlab_build_java_method_use_case_context`,
- `gitlab_read_repository_file`,
- `gitlab_read_repository_files_by_path`,
- `gitlab_read_repository_file_chunk`,
- `gitlab_read_repository_file_chunks`,
- `gitlab_read_repository_file_outline`,
- `gitlab_read_java_method_slice`,
- `gitlab_read_openapi_endpoint_slice`,
- `gitlab_find_flow_context`.

GitLab tools nie czytaja functional scope'u z hidden `ToolContext`. Gdy tool
wymaga scope'u, przekaz jawnie:

- `branchRef`, `applicationName` i `projectName` z
  `flow-explorer/canonical-tool-inputs.md`,
- `filePath` i `methodSelectors` z
  `flow-explorer/compact-flow-manifest.md`,
- `filePath`, `httpMethod` i `endpointPath` z
  `flow-explorer/openapi-endpoint-contract.md`, jezeli czytasz OpenAPI.

Nie przekazuj `gitLabGroup`.

## Algorytm

1. Sprawdz `flow-explorer/canonical-tool-inputs.md`,
   `flow-explorer/compact-flow-manifest.md`, `flow-explorer/snippet-cards.md`
   i, jezeli istnieje, `flow-explorer/openapi-endpoint-contract.md`.
2. Jezeli potrzebny kod jest juz w `snippet-cards.md`, nie wolaj GitLab toola.
3. Jezeli brakuje endpoint spine, uzyj
   `gitlab_build_endpoint_use_case_context`.
4. Jezeli znasz klase/metode i chcesz kontynuowac use case flow, uzyj
   `gitlab_build_java_method_use_case_context` z `maxResults`.
5. Jezeli znasz metode i potrzebujesz jej tresci, uzyj
   `gitlab_read_java_method_slice`.
6. Jezeli znasz plik, ale nie metode, uzyj
   `gitlab_read_repository_file_outline`.
7. Jezeli znasz OpenAPI path/method/file, uzyj
   `gitlab_read_openapi_endpoint_slice`, nie czytaj calego YAML.
8. Jezeli parser Java nie pasuje albo masz tylko linie, uzyj
   `gitlab_read_repository_file_chunk` albo
   `gitlab_read_repository_file_chunks`.
9. Jezeli masz mala, ugruntowana liste plikow, uzyj
   `gitlab_read_repository_files_by_path`.
10. `gitlab_read_repository_file` jest wyjatkiem: maly plik albo przypadek, w
    ktorym outline, method slice i chunk nie odpowiadaja na pytanie.

Opcjonalnie mozesz raz przeczytac `.github/copilot-instructions.md` z primary
repozytorium, ale tylko jako repository guidance. Nie szukaj wariantow tej
sciezki po calym repozytorium.

## Algorytmy Sekcji

- `FUNCTIONAL_FLOW`: controller, use case service, routing, strategie,
  decyzje funkcjonalne, kalkulacje i handoffy. Czytaj kolejny kod tylko wtedy,
  gdy moze zmienic opis kroku albo wariantu.
- `VALIDATIONS`: request model, adnotacje DTO, validator, guard clause,
  error boundary. Nie czytaj mappera, jezeli nie zmienia walidacji ani edge
  case.
- `PERSISTENCE`: Spring Data/JPA/Hibernate first. Ustal repository method,
  query predicate, entity annotations, status fields, transaction boundary i
  source wartosci. Dla `DEEP` obowiazkowo uwzglednij kolumny z dziedziczenia,
  `@MappedSuperclass`, `@Embedded`/kompozycji i metod encji uzytych w flow,
  az dojdziesz do pol bezposrednio mapowanych na kolumny. Dla `DEEP` przygotuj
  `TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS`.
- `INTEGRATIONS`: najpierw kod klienta, adaptera, mappera request/response,
  publikacji eventu albo consumera. Konfiguracje czytaj dopiero, gdy kod
  wskaze nazwe klienta, binding, destination albo property placeholder.

## Heurystyki Java/Spring

Persistence:

- `@Entity`, `@Table`, `@Column`, `@JoinColumn`, `@Embedded`,
  `@AttributeOverride`, `@Enumerated` i `JpaRepository` sa pierwszym zrodlem
  mapowania ORM.
- Mapowanie kolumn czytaj gleboko przez `extends`, `@MappedSuperclass`,
  `@Embeddable`, `@Embedded`, kompozycje i akcesory encji. Nie wolno pominac
  kolumn tylko dlatego, ze pole jest w klasie bazowej albo wartosc jest
  pobierana przez metode obiektu zlozonego, np. `entity.getBase().getValue()`,
  `entity.getAudit().changedBy()` albo helper na parent entity.
- Jezeli metoda flow odwoluje sie do metody encji, a ta metoda zwraca albo
  wylicza wartosc z pol parenta/kompozycji, traktuj te pola jako czesc
  persistence mapping i ustal odpowiadajace im kolumny.
- Dla `PERSISTENCE=DEEP` nie koncz na lokalnych polach klasy potomnej. Zatrzymaj
  sie dopiero przy typach prostych mapowanych na kolumny, join columns,
  embedded attributes albo przy jawnym `visibilityLimits`, ze dalsze mapowanie
  nie jest widoczne.
- Liquibase/Flyway/DDL czytaj w sytuacji niespojnosci,
  gdy nie mozna ustalic typu pola, nazwy kolumny, tabeli albo relacji na podstawie kodu.
- Nie czytaj migracji dla samego potwierdzenia nazw tabel i kolumn.

Dziedziczenie i strategie:

- Gdy widzisz `extends`, `implements`, injection po interface albo
  `List<Interface>`, uzyj outline do ustalenia typu, pol, konstruktorow,
  adnotacji i relacji.
- Szukaj implementacji tylko wtedy, gdy runtime wybor nie wynika z
  `@Qualifier`, `@Primary`, nazwy beana albo jawnego warunku.
- Dla strategii spodziewaj sie metod `supports(...)`, `canHandle(...)`,
  `getType()`, `supportedType()` albo listy typow.

Integracje:

- Dla HTTP spodziewaj sie `@FeignClient`, `RestClient`, `WebClient` albo
  `RestTemplate`.
- Dla publikacji eventow spodziewaj sie `StreamBridge.send(...)` i nazwy
  bindingu w stalej, property albo argumencie metody.
- Dla konsumpcji spodziewaj sie `@Bean Consumer<T>`, `Consumer<T>`,
  `Consumer<Message<T>>`, `Function<T,R>`, `Supplier<T>`,
  `@RabbitListener`, `@EventListener` albo `@TransactionalEventListener`.
- YAML/properties traktuj jako resolver `spring.cloud.function.definition`,
  `spring.cloud.stream.bindings.<binding>.*`, destination, group, binder,
  contentType, retry albo DLQ, nie jako pierwsze zrodlo flow.

## Zasady Kosztowe

- Nie zaczynaj od `gitlab_read_repository_file`.
- Nie czytaj ponownie metod widocznych w `snippet-cards.md`.
- Dla `gitlab_read_java_method_slice` podawaj minimalne `methodSelectors`;
  zwykle wystarczy `methodName`, a `lineStart` tylko zawęza overload.
- Po `truncated=true` nie wnioskuj o dalszej czesci pliku; jezeli jest
  potrzebna, kontynuuj przez chunk z `totalLines`, `returnedStartLine` i
  `returnedEndLine`.
- `reasoningEffort=low`: tylko krytyczna luka.
- `reasoningEffort=medium`: primary flow i aktywne sekcje `DEEP`.
- `reasoningEffort=high`: pomocnicze walidatory, mappery, repository details
  albo integracje tylko gdy zmieniaja scenariusze, ryzyka albo edge case.

## Wklad Do Wyniku

Po tool result zapisz wnioski jako:

- zachowanie systemu w prostym jezyku,
- source ref: artefakt, tool albo `projectName:path:Lx-Ly`,
- limit widocznosci, jezeli evidence jest niepelne.

Nie cytuj dlugiego kodu w wyniku.

## Stop

Wroc do orkiestratora, gdy:

- masz evidence dla aktywnych sekcji,
- dalsze czytanie tylko potwierdzaloby to samo,
- potrzebny kontekst jest katalogowy, a nie kodowy,
- brak widocznosci nalezy wpisac do `visibilityLimits`.
