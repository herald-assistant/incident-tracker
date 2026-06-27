# GitLab Endpoint Use Case Context Plan

## Cel

Tool `gitlab_build_endpoint_use_case_context` ma byc szybka, punktowa
capability GitLaba, ktora dla konkretnego endpointu HTTP zwraca kompaktowa
liste plikow i symboli potrzebnych AI do dalszej interpretacji.

Tool nie ma sam opisywac funkcjonalnie ani technicznie endpointu. Jego
odpowiedzialnoscia jest wyznaczyc minimalny, uzasadniony kontekst kodu, ktory
AI moze pozniej doczytac istniejacymi tools, np. `gitlab_read_repository_file`
albo `gitlab_read_repository_file_chunk`, zeby:

- opisac jak endpoint dziala funkcjonalnie i technicznie,
- przygotowac diagram techniczny albo biznesowy,
- sprawdzic, czy nowe wymaganie pasuje do istniejacego endpointu,
- wskazac ograniczenia widocznosci i miejsca nierozstrzygniete.

Najwazniejsza zmiana wzgledem poprzedniego podejscia: tool nie buduje duzego
indeksu calego repozytorium. Dziala jak czlowiek czytajacy kod punktowo:
startuje od endpointu, czyta plik kontrolera, przechodzi po wywolaniach,
importach, interfejsach, implementacjach i wybranych typach domenowych.

## Non Goals MVP

W MVP celowo nie implementujemy:

- pelnego symbol solvera z classpath/buildem projektu,
- runtime Spring bean resolution 1:1,
- refleksji, proxy, AOP, runtime condition evaluation,
- pelnego grafu wszystkich metod,
- pelnego indeksu klas i metod repozytorium,
- analizy bytecode albo generated classes po buildzie,
- ekspansji async/event consumers bez jawnej przyszlej opcji,
- semantyki incident analysis.

Jesli cos jest nierozstrzygalne szybko i lokalnie, wynik ma zwrocic
`unresolved` albo `candidate`, zamiast zgadywac.

## Granice architektoniczne

Implementacja logiki mieszka w:

```text
src/main/java/pl/mkn/tdw/integrations/gitlab/usecase
```

Ten pakiet pozostaje neutralna capability integracji. Nie importuje:

- `analysis.*`,
- `agenttools.*`,
- `features.*`,
- `api.*`,
- `aiplatform.*`.

Ekspozycja toola mieszka w:

```text
src/main/java/pl/mkn/tdw/agenttools/gitlab/mcp
```

Shared/operator REST endpoint dla recznego testowania moze byc dodany pozniej
w `api.gitlab`, ale nie jest wymagany w pierwszym kroku MVP. Najpierw budujemy
capability i MCP tool.

## Wejscie toola

Model-facing schema:

```json
{
  "projectName": "orders-api",
  "endpointId": "GET /api/products/{ttaId} -> ...DataProductController#getProduct",
  "httpMethod": "GET",
  "endpointPath": "/api/products/{ttaId}",
  "maxDepth": 5,
  "maxFiles": 25,
  "reason": "..."
}
```

Zasady:

- `projectName` jest wymagany.
- `endpointId` jest preferowany, gdy pochodzi z
  `gitlab_list_repository_endpoints`.
- Jesli `endpointId` nie ma, wymagamy `httpMethod + endpointPath`.
- Skan kodu startuje od root repozytorium; source rooty w aplikacjach
  multi-module sa wykrywane z repository tree, bez model-facing prefixu.
- `maxDepth` domyslnie `5`, cap `8`.
- `maxFiles` domyslnie `25`, cap `40`.
- `reason` jest prostym operator-facing powodem po polsku.

Scope ukryty:

- `group` i `branch` pochodza z `ToolContext`,
- model nie podaje `gitLabGroup` ani `gitLabBranch`.

## Wyjscie toola

Wynik ma byc tokenowo maly. Nie zwracamy source code.

Proponowany model integracyjny:

```java
public record GitLabEndpointUseCaseContextResult(
        GitLabEndpointUseCaseRepositoryContext repository,
        GitLabEndpointUseCaseEndpointContext endpoint,
        List<GitLabEndpointUseCaseFileCandidate> files,
        List<GitLabEndpointUseCaseRelation> relations,
        List<GitLabEndpointUseCaseUnresolvedReference> unresolved,
        List<String> limitations,
        List<String> suggestedNextReads,
        GitLabEndpointUseCaseLimits limits,
        GitLabEndpointUseCaseConfidence confidence
) {
}
```

`files` jest najwazniejsza czescia wyniku:

```json
{
  "path": "src/main/java/.../DataProductController.java",
  "role": "CONTROLLER",
  "priority": 1,
  "symbols": ["getProduct", "getProductWebModel"],
  "reason": "Endpoint handler and local helper used by handler.",
  "confidence": "HIGH"
}
```

`relations` sa lekkim uzasadnieniem, nie pelnym grafem:

```json
{
  "from": "DataProductController#getProduct",
  "to": "ProductRepositoryPort.Query#getProductFormView",
  "kind": "INJECTED_PORT_CALL",
  "confidence": "HIGH"
}
```

`suggestedNextReads` powinno wskazywac konkretne pliki/symbole do dociagniecia
przez AI, np.:

```text
orders-api:src/main/java/.../DataProductController.java via gitlab_read_repository_file_outline
orders-api:src/main/java/.../UpdateProductService.java lines 20-70 via gitlab_read_repository_file_chunk
```

## Role plikow

Minimalny enum roli:

- `CONTROLLER`
- `OPENAPI_CONTRACT`
- `API_INTERFACE`
- `USE_CASE_PORT`
- `USE_CASE_SERVICE`
- `REPOSITORY_PORT`
- `REPOSITORY_IMPLEMENTATION`
- `SPRING_DATA_REPOSITORY`
- `MAPPER`
- `DOMAIN_MODEL`
- `WEB_MODEL`
- `PROJECTION`
- `CONFIGURATION`
- `EXTERNAL_CLIENT`
- `UNKNOWN`

Role maja pomagac AI w kolejnosci czytania. Nie sa kontraktem biznesowym.

## Priorytety plikow

Priorytet jest liczba rosnaca:

1. controller / API interface / OpenAPI contract,
2. bezposredni port albo service wywolany przez endpoint,
3. implementacja portu i lokalne helpery,
4. repository port/implementation i Spring Data repository,
5. mappery MapStruct/manualne mappery,
6. domena z logika biznesowa,
7. web models/projections,
8. konfiguracja i pozostale.

Przy przekroczeniu `maxFiles` obcinamy najnizszy priorytet i dodajemy
limitation.

## Start traversal

### Krok 1: endpoint inventory

Nie szukamy endpointu od zera. Startujemy przez istniejacy
`GitLabRepositoryEndpointService`.

Flow:

1. Zbuduj `GitLabRepositoryEndpointListRequest`.
2. Jesli podano `endpointId`, znajdz endpoint po dokladnym `endpointId`.
3. Jesli nie, filtruj po `httpMethod + endpointPath`.
4. Jesli wiele endpointow pasuje, zwroc `AMBIGUOUS_ENDPOINT` z kandydatami.
5. Jesli brak endpointu, zwroc `ENDPOINT_NOT_FOUND`.

To automatycznie wspiera sytuacje:

- Spring MVC annotations w kontrolerze,
- kontroler implementujacy generated OpenAPI interface,
- endpoint wykryty przez YAML + implementacje metody.

### Krok 2: plik startowy

Punktem startu jest:

- `endpoint.filePath`,
- `endpoint.controllerClass`,
- `endpoint.handlerMethod`,
- `endpoint.lineStart/lineEnd`,
- opcjonalnie `endpoint.annotations`, gdzie moga byc sygnaly
  `OpenApiContract`, `Implements ...`, `OperationId ...`.

## Strategia dostepu do GitLaba

Nie wolno tworzyc osobnej paginacji `repository/tree`.

Do listowania plikow uzywamy istniejacych mechanizmow:

- `GitLabRepositoryPort.listRepositoryFiles(...)`,
- adapter pod spodem ma korzystac z `GitLabRepositoryTreeService`,
- cache tree pozostaje w `GitLabRepositoryTreeSession`,
- capability moze dodatkowo uzyc `GitLabRepositoryAnalysisCache` dla
  krotkiego request/session cache AST i tresci plikow.

Cache w MVP:

- `sourceFileContent`: klucz `group/project/branch/filePath/maxCharacters`,
- `parsedAst`: klucz `group/project/branch/filePath/contentHash`,
- `repositoryTree`: przez istniejaca sesje tree,
- `endpointInventory`: przez obecny cache inventory.

Nie cache'ujemy duzych indeksow klas repozytorium.

## JavaParser

Uzywamy `javaparser-core`.

Konfiguracja:

- language level Java 21,
- tolerant parse tam, gdzie to mozliwe,
- parse problem trafia do `limitations`, a plik moze nadal trafic do wyniku
  jako `UNKNOWN`, jesli jest istotny.

MVP musi rozumiec skladnie Java 21 uzywana w przykladach:

- switch expressions,
- pattern matching w switch case,
- `yield`,
- records,
- sealed/non-sealed,
- pattern variables,
- local variable inference `var`,
- lambdas i method references jako terminalne lub lekkie sygnaly.

Nie uzywamy symbol-solvera w MVP.

## Model AST pliku

Dla kazdego przeczytanego pliku budujemy lekki model:

```java
record GitLabJavaAstFile(
        String path,
        String packageName,
        List<String> imports,
        List<String> staticImports,
        List<GitLabJavaTypeDeclaration> types,
        CompilationUnit compilationUnit
) {
}
```

Wazne: jeden plik moze zawierac wiele typow:

- public class,
- package-private classes,
- package-private interfaces,
- nested interfaces/classes,
- Spring Data repositories jako package-private interfaces.

Dlatego resolver nie moze zakladac `SimpleName.java == jedyny typ w pliku`.

## Rozwiazywanie typow

Resolver punktowy dla nazwy typu:

1. Typ zadeklarowany w tym samym pliku.
2. Import dokladny.
3. Import static, gdy wywolanie wyglada jak static helper.
4. Ten sam pakiet.
5. Nested type, np. `ProductRepositoryPort.Query`.
6. `repository tree` lookup po sciezce konczacej sie `/<SimpleName>.java`.
7. Ograniczone code search po nazwie typu, jesli tree lookup nie wystarczy.

Dla kazdego resolved type zwracamy:

- `filePath`,
- `qualifiedName` jesli da sie ustalic,
- `resolutionKind`,
- `confidence`.

Jesli jest wiecej kandydatow, nie zgadujemy. Dodajemy `unresolved` z
kandydatami.

## External Type Policy

Traversal powinien miec jawna polityke dla typow, ktorych zrodla z duzym
prawdopodobienstwem nie bedzie w repozytorium. Mechanizm jest podobny do
`GitLabDeterministicEvidenceProvider.IGNORED_STACKTRACE_PREFIXES`, ale dla
nowego toola nie powinien byc zwykla lista "ignoruj". Potrzebujemy klasyfikacji,
bo czesc frameworkowych typow niesie wazna semantyke traversal.

Dodac:

- `GitLabJavaExternalTypePolicy`,
- `GitLabJavaExternalTypeClassification`.

Proponowane klasyfikacje:

- `SKIP_SOURCE_LOOKUP`: nie szukaj pliku w GitLabie,
- `SEMANTIC_SIGNAL`: nie szukaj pliku, ale wykorzystaj adnotacje/typ do
  klasyfikacji flow,
- `TERMINAL_BOUNDARY`: potraktuj jako granice zewnetrzna,
- `LOCAL_LOOKUP_FIRST`: najpierw sprawdz repository tree, potem ewentualnie
  oznacz jako external/shared-library boundary.

Typowe prefixy `SKIP_SOURCE_LOOKUP`:

- `java.`,
- `javax.`,
- `jakarta.`,
- `jdk.`,
- `sun.`,
- `com.sun.`,
- `org.springframework.`,
- `org.apache.`,
- `org.hibernate.`,
- `org.slf4j.`,
- `ch.qos.logback.`,
- `io.micrometer.`,
- `reactor.`,
- `kotlin.`,
- `groovy.`,
- `lombok.`,
- `org.mapstruct.`,
- `com.fasterxml.`,
- `io.swagger.`,
- `org.openapitools.`.

Typy/adnotacje jako `SEMANTIC_SIGNAL`:

- `@RequiredArgsConstructor` -> DI przez final fields,
- `@Getter`, `@Data`, `@Value` -> Lombok accessors jako heurystyka,
- `@Mapper` -> MapStruct mapper,
- `@Transactional` -> metadata transakcyjna,
- `@EventListener` -> async/event boundary, nie sync endpoint flow MVP,
- `@RolesAllowed` -> metadata security endpointu,
- `JpaRepository`, `CrudRepository`, `PagingAndSortingRepository` -> Spring
  Data terminal repository,
- `ResponseEntity` -> wrapper odpowiedzi, nie osobny flow,
- `Optional` -> wrapper wartosci, nie osobny flow.

Wazna zasada: nie dodawac twardego ignorowania prefixow firmowych ani
produktowych. Dla typow takich jak `pl.centrum24...`, `pl.santander...` albo
innych lokalnych/shared-library prefixow najpierw wykonujemy tani lookup w
repository tree. Dopiero jesli pliku nie ma w repo, oznaczamy typ jako
`TERMINAL_BOUNDARY` albo `unresolved` z powodem:

```text
Type looks like internal/shared library class, but no matching source file was
found in the selected repository tree.
```

To jest kluczowe dla generated OpenAPI interfaces. Typ moze wygladac jak
generated source albo shared contract, ale jesli plik istnieje w repo, powinien
zostac dodany jako `API_INTERFACE`. Jesli pliku nie ma, tool opiera sie na YAML
i implementacji kontrolera oraz dodaje limitation.

## Rozwiazywanie metod

MVP nie robi pelnej overload resolution.

Reguly:

- Preferuj metode w typie docelowym o tej samej nazwie.
- Jesli jest jedna metoda o tej nazwie, uznaj za `HIGH`.
- Jesli jest kilka overloadow, uzyj liczby argumentow jako heurystyki.
- Jesli nadal wiele, zwroc wszystkich kandydatow jako ambiguous.
- Dla private helperow w tej samej klasie rozstrzygaj bezposrednio po nazwie.
- Dla default methods w interface czytaj interface jako normalny plik.

Nie wchodzimy w generyki poza zachowaniem tekstu typu jako hint.

## Traversal metody

Traversal startuje od handlera endpointu.

Dla ciala metody zbieramy:

- wywolania lokalnych helperow,
- wywolania na polach klasy,
- wywolania statyczne,
- `Mapper.INSTANCE.method(...)`,
- `new SomeType(...)`,
- typy w castach,
- typy w `switch case`,
- typy w `instanceof`,
- returned type, jesli jest domenowy/web/projection,
- rzucane wyjatki domenowe.

Kazde wykrycie tworzy `TraversalEdge`, ktory moze dodac plik do wyniku.

## Dependency Injection

Nie odtwarzamy pelnego runtime Springa.

Reguly MVP:

- `@RequiredArgsConstructor` + `final field` = injected dependency.
- Konstruktor z parametrem = injected dependency.
- `@Autowired` field/constructor = injected dependency.
- `@Service`, `@Component`, `@Repository`, `@RestController`,
  `@Configuration`, `@AdapterBean`, `@UseCaseBean` = potencjalny bean.
- Field type jako interface -> szukamy implementacji przez `implements`.
- Field name jest tylko tie-breakerem, nie dowodem.
- `@Qualifier` i bean name mozna zapisac jako hint, ale nie musi byc
  rozstrzygane w MVP.

Przy wielu implementacjach:

- dodajemy interface/port do `files`,
- dodajemy top kandydatow implementacji do `unresolved`,
- jesli kandydatow jest malo i mieszcza sie w limicie, mozna dodac je do
  `files` z `confidence=MEDIUM`.

## Interfejsy i implementacje

Szczegolnie wazne dla przykladow:

```java
private final UpdateProductPort updateProductPort;
private final ProductRepositoryPort.Query productQueryRepository;
private final ProductRepositoryPort.Command productCommandRepository;
```

Strategia szukania implementacji:

1. Zbuduj keywordy:
   - `implements UpdateProductPort`,
   - `implements ProductRepositoryPort.Query`,
   - `implements Query`,
   - pelna nazwa interfejsu,
   - simple name interfejsu.
2. Uzyj ograniczonego code search albo tree lookup + read kandydatow.
3. JavaParser waliduje `implements`.
4. Dla nested interface `ProductRepositoryPort.Query` obsluz:
   - typ nadrzedny `ProductRepositoryPort`,
   - nested type `Query`,
   - zapis w kodzie `implements ProductRepositoryPort.Query`.

Implementacje package-private musza byc obslugiwane.

## Spring Data repositories

Interfejs:

```java
interface SomeRepo extends JpaRepository<Entity, Long> {
}
```

Jest terminalnym beanem. Tool:

- dodaje plik jako `SPRING_DATA_REPOSITORY`,
- dodaje metode repozytorium jako symbol, jesli jest jawnie zadeklarowana,
- dla inherited CRUD/query methods nie szuka implementacji,
- moze dodac encje generyczna jako `DOMAIN_MODEL`, jesli latwo ja rozpoznac.

W przykladzie:

- `ProductQueryJpaRepository`,
- `OverdraftQueryJpaRepository`,
- `MultilineQueryJpaRepository`,
- `ProductCommandJpaRepository`

sa package-private interface w plikach repozytorium i musza zostac wykryte.

## Lombok

Nie generujemy kodu Lomboka.

Reguly:

- `@RequiredArgsConstructor` wspiera DI.
- `@Getter`, `@Data`, `@Value` oznaczaja, ze `getX()` albo `isX()` moze
  pochodzic z pola.
- Jesli wywolanie gettera prowadzi do typu domenowego, dodaj plik modelu tylko
  wtedy, gdy:
  - metoda aktualnie traversowana wywoluje logike domenowa,
  - model zawiera metody biznesowe,
  - model jest typem `switch case`, `instanceof`, cast albo return type.
- `@Builder`, `@SuperBuilder`, `@NoArgsConstructor`, `@AllArgsConstructor`
  sa traktowane jako metadata, nie jako flow.

Dla `Overdraft` istotne sa metody:

- `update(...)`,
- `calculateStatus()`,
- helpery walidujace status.

Same gettery/settery nie powoduja zejscia w model.

## MapStruct

MapStruct w MVP jest source-level mapperem.

Reguly:

- `@Mapper` -> rola `MAPPER`.
- `SomeMapper.INSTANCE.method(...)` -> czytaj plik mappera.
- `Mappers.getMapper(SomeMapper.class)` -> sygnal MapStruct instance.
- `default` methods sa traversowane jak zwykle metody.
- Generated implementation MapStruct jest terminalna i nie jest szukana.
- `uses = { IdentificationMapper.class }` dodajemy jako `MAPPER` tylko gdy:
  - metoda mappera jawnie odwoluje sie do tego typu,
  - albo `maxFiles` ma zapas i typ jest w tym samym bounded context.

Dla switchy w mapperach:

```java
return switch (web.getType()) {
  case OVERDRAFT -> OverdraftWebModelMapper.INSTANCE.mapOverdraft(...);
  case MULTILINE -> MultilineWebModelMapper.INSTANCE.mapMultiline(...);
};
```

Dodajemy potencjalne galezie switcha jako pliki o nizszym priorytecie, bo
tool nie zna runtime value `ProductType`.

## OpenAPI YAML i generated sources

Endpoint moze nie miec adnotacji mappingowych w klasie kontrolera, bo API jest
generowane z YAML.

Obecny `gitlab_list_repository_endpoints` juz wykrywa przypadek:

- OpenAPI YAML zawiera path/method/operationId,
- kontroler implementuje generated interface,
- wynik endpointu wskazuje implementacje kontrolera.

Nowy tool ma:

1. Przyjac taki endpoint z inventory.
2. Dodac YAML jako `OPENAPI_CONTRACT`, jesli `suggestedNextReads` albo
   annotations/limitations wskazuja contract source.
3. Dodac generated API interface jako `API_INTERFACE`, jesli plik istnieje w
   repo.
4. Startowac traversal od implementacji kontrolera, nie od generated code.
5. Nie probowac czytac generated classes, ktorych nie ma w repo.

Jesli generated interface nie istnieje w repo, dodajemy limitation:

```text
Generated OpenAPI interface is not present in repository tree; endpoint
contract was inferred from YAML and controller implementation.
```

## Spring Boot libs

Specjalne heurystyki:

- `ResponseEntity.ok(...)` jest wrapperem, nie osobnym flow.
- `@Transactional` jest metadata, nie osobnym flow.
- `@EventListener` metody nie wchodza do flow synchronicznego endpointu w MVP.
- `ApplicationEventPublisher.publishEvent(...)` moze zostac zapisany jako
  terminal async boundary.
- Feign clients `@FeignClient` sa terminalnym `EXTERNAL_CLIENT`.
- `JpaRepository`/`CrudRepository` sa terminalnymi repozytoriami.
- `@Cacheable` jest metadata; nie rozwijamy cache providerow.
- `@RolesAllowed`/security annotations ida do endpoint metadata, ale nie
  zmieniaja traversal.

## Limity i wydajnosc

Domysly MVP:

- `maxDepth = 5`,
- `maxFiles = 25`,
- `maxReadFiles = 60`,
- `maxSearchCandidatesPerInterface = 10`,
- `maxMethodCallsPerMethod = 30`,
- `maxCharactersPerFile = 120_000`,
- `maxTraversalMillis = 8000`.

Jesli limit zostanie osiagniety:

- wynik nadal zwraca czesciowa liste,
- `limits.*Reached = true`,
- `limitations` wyjasnia co obcieto.

Sortowanie plikow:

1. priorytet roli,
2. depth,
3. confidence,
4. path.

Deduplicate po `projectName + path`.

## Confidence

Poziomy:

- `HIGH`: bezposredni endpoint, import dokladny, jednoznaczna implementacja,
  jednoznaczna metoda.
- `MEDIUM`: heurystyka po same package/tree lookup, wiele branchy switcha,
  interface implementor wybrany jako najlepszy kandydat.
- `LOW`: search candidate bez jednoznacznej walidacji AST.

Global confidence wyniku to minimum istotnych elementow albo `MEDIUM`, jesli
sa unresolved references.

## Oczekiwany wynik dla GET z notatki

Flow:

```text
DataProductController#getProduct
  -> getProductWebModel
  -> ProductRepositoryPort.Query#getProductFormView
  -> ProductQueryRepository#getProductFormView
  -> OverdraftQueryJpaRepository / MultilineQueryJpaRepository
  -> ProductWebModelMapper#from(FormView<ProductType>)
  -> OverdraftFormViewMapping / MultilineFormViewMapping
```

Minimalne pliki:

- `DataProductController.java` jako `CONTROLLER`,
- `ProductRepositoryPort.java` jako `REPOSITORY_PORT`,
- `ProductQueryRepository.java` jako `REPOSITORY_IMPLEMENTATION`,
- `ProductWebModelMapper.java` jako `MAPPER`,
- `OverdraftFormViewMapping.java` jako `MAPPER`,
- `MultilineFormViewMapping.java` jako `MAPPER`, jesli branch switcha jest
  potencjalny,
- `OverdraftFormView.java` jako `PROJECTION`,
- `MultilineFormView.java` jako `PROJECTION`, jesli branch switcha jest
  potencjalny.

Nie powinno zejsc w caly model `Overdraft`, chyba ze wynik mappera albo
projection bezposrednio wymaga logiki domenowej.

## Oczekiwany wynik dla PUT z notatki

Flow:

```text
DataProductController#updateProduct
  -> getTtaId
  -> ProductWebModelMapper#from(ProductWebModel)
  -> OverdraftWebModelMapper / MultilineWebModelMapper
  -> UpdateProductPort#update
  -> UpdateProductService#update
  -> ProductRepositoryPort.Query#getProductForUpdate
  -> ProductQueryRepository#getProductForUpdate
  -> Product#update behavior
  -> ProductRepositoryPort.Command#update
  -> ProductCommandRepository#update
  -> ProductWebModelMapper#from(FormView<ProductType>)
  -> getProductWebModel
```

Minimalne pliki:

- `DataProductController.java` jako `CONTROLLER`,
- `UpdateProductPort.java` jako `USE_CASE_PORT`,
- `UpdateProductService.java` jako `USE_CASE_SERVICE`,
- `ProductRepositoryPort.java` jako `REPOSITORY_PORT`,
- `ProductQueryRepository.java` jako `REPOSITORY_IMPLEMENTATION`,
- `ProductCommandRepository.java` jako `REPOSITORY_IMPLEMENTATION`,
- `ProductWebModelMapper.java` jako `MAPPER`,
- web model/domain mappery z branchy switcha,
- `Overdraft.java` jako `DOMAIN_MODEL`, bo zawiera `update` i
  `calculateStatus`,
- potencjalnie `Multiline.java`, jesli branch switcha jest potencjalny.

Metody `@EventListener` w `UpdateProductService` nie wchodza do wyniku PUT w
MVP, bo nie sa synchronicznym flow endpointu.

## Kroki implementacji

### Krok 1: kontrakty integracyjne

Dodac pakiet:

```text
src/main/java/pl/mkn/tdw/integrations/gitlab/usecase
```

Dodac rekordy:

- `GitLabEndpointUseCaseContextRequest`,
- `GitLabEndpointUseCaseContextResult`,
- `GitLabEndpointUseCaseRepositoryContext`,
- `GitLabEndpointUseCaseEndpointContext`,
- `GitLabEndpointUseCaseFileCandidate`,
- `GitLabEndpointUseCaseRelation`,
- `GitLabEndpointUseCaseUnresolvedReference`,
- `GitLabEndpointUseCaseLimits`.

Dodatkowo enumy:

- `GitLabEndpointUseCaseFileRole`,
- `GitLabEndpointUseCaseRelationKind`,
- `GitLabEndpointUseCaseConfidence`.

Test:

- model defaults,
- null-safe lists,
- max limit normalization.

### Krok 2: endpoint resolver

Dodac:

- `GitLabEndpointUseCaseEndpointResolver`.

Resolver uzywa `GitLabRepositoryEndpointService`.

Testy:

- endpoint po `endpointId`,
- endpoint po `httpMethod + endpointPath`,
- ambiguous endpoint,
- endpoint not found,
- endpoint z OpenAPI/YAML-backed inventory.

### Krok 3: source session

Dodac:

- `GitLabEndpointUseCaseSourceSession`,
- `GitLabEndpointUseCaseSourceFile`,
- `GitLabEndpointUseCaseAstCache`.

Odpowiedzialnosc:

- read file z cache,
- parse AST z cache,
- list repository files przez port,
- limit przeczytanych plikow.

Testy:

- ten sam plik czytany raz,
- parse failure zwraca limitation,
- file limit.

### Krok 4: Java source resolver

Dodac:

- `GitLabJavaSourceResolver`,
- `GitLabJavaAstFile`,
- `GitLabJavaTypeDeclaration`.

Funkcje:

- extract package/imports/static imports,
- list all top-level and nested types,
- resolve same-file type,
- resolve exact import,
- resolve same package,
- resolve nested interface/class,
- tree lookup by simple name,
- ambiguous candidates.

Testy na:

- wiele klas w pliku,
- package-private interface,
- nested interface `ProductRepositoryPort.Query`,
- same package without import,
- ambiguous simple name.

### Krok 5: external type policy

Dodac:

- `GitLabJavaExternalTypePolicy`,
- `GitLabJavaExternalTypeClassification`.

Funkcje:

- klasyfikacja framework/library prefixow,
- semantic signals dla Spring/Lombok/MapStruct/Spring Data,
- `LOCAL_LOOKUP_FIRST` dla typow lokalnych i generated OpenAPI interfaces,
- blokada zbednych GitLab searchy dla `java.*`, `org.springframework.*`,
  `lombok.*`, `org.mapstruct.*`, `io.swagger.*`, `org.openapitools.*`,
- limitation dla internal/shared-library type bez pliku w repo.

Testy:

- `ResponseEntity` nie powoduje source lookup,
- `@RequiredArgsConstructor` zostaje sygnalem DI,
- `JpaRepository` oznacza terminal Spring Data repository,
- lokalny generated `DataProductApi` jest szukany w tree przed uznaniem za
  external,
- firmowy prefix bez pliku w tree trafia do unresolved/limitation.

### Krok 6: method locator

Dodac:

- `GitLabJavaMethodLocator`.

Funkcje:

- znajdz metode handlera po nazwie,
- znajdz helper w tej samej klasie,
- znajdz metoda interface/default,
- overload by argument count best effort.

Testy:

- overloaded methods,
- private helper,
- default interface method,
- brak metody.

### Krok 7: DI model

Dodac:

- `GitLabJavaDependencyModelBuilder`,
- `GitLabJavaInjectedDependency`,
- `GitLabJavaBeanCandidate`.

Funkcje:

- final fields + `@RequiredArgsConstructor`,
- constructor injection,
- `@Autowired`,
- stereotypes Spring/custom,
- field type/name metadata.

Testy:

- Lombok required args,
- constructor injection,
- field injection,
- `@AdapterBean`,
- `@UseCaseBean`,
- multiple fields same interface.

### Krok 8: interface implementor resolver

Dodac:

- `GitLabJavaInterfaceImplementorResolver`.

Funkcje:

- search keywords for interface,
- validate `implements` in AST,
- nested interface matching,
- package-private class matching,
- candidate list on ambiguity.

Testy:

- `UpdateProductPort -> UpdateProductService`,
- `ProductRepositoryPort.Query -> ProductQueryRepository`,
- `ProductRepositoryPort.Command -> ProductCommandRepository`,
- multiple implementations,
- no implementation.

### Krok 9: Spring Data terminal resolver

Dodac:

- `GitLabJavaSpringDataRepositoryDetector`.

Funkcje:

- detect `extends JpaRepository`,
- detect `CrudRepository`, `PagingAndSortingRepository`,
- extract entity type if simple,
- mark terminal.

Testy:

- package-private repository interface,
- empty Spring Data interface,
- custom declared method,
- entity generic extraction.

### Krok 10: MapStruct resolver

Dodac:

- `GitLabJavaMapStructResolver`.

Funkcje:

- detect `@Mapper`,
- detect `Mapper.INSTANCE.method`,
- detect `Mappers.getMapper(...)`,
- traverse default methods,
- add switch branch mapper calls,
- terminal generated implementation limitation.

Testy:

- `ProductWebModelMapper.INSTANCE.from(...)`,
- default method switch,
- `@Mapper(uses = ...)`,
- branch mappers from switch.

### Krok 11: traversal service

Dodac:

- `GitLabEndpointUseCaseTraversalService`,
- `GitLabEndpointUseCaseTraversalState`,
- `GitLabEndpointUseCaseTraversalNode`.

Algorytm:

1. Dodaj endpoint file jako `CONTROLLER`.
2. Zlokalizuj metode handlera.
3. Traversuj metode do limitu.
4. Dla helpera w tej samej klasie: dodaj symbol i wejdz glebiej.
5. Dla injected dependency: dodaj port/interface i implementacje.
6. Dla mapper call: dodaj mapper i wejdz w metode mappera.
7. Dla Spring Data: dodaj repository jako terminal.
8. Dla domenowej metody biznesowej: dodaj domain model i wejdz w metode.
9. Dla nierozstrzygnietych miejsc: dodaj unresolved.

Testy:

- GET flow z zalaczonej notatki,
- PUT flow z zalaczonej notatki,
- limit depth,
- limit max files,
- cycle detection,
- unresolved dependency.

### Krok 12: result compression

Dodac:

- `GitLabEndpointUseCaseResultCompressor`.

Funkcje:

- deduplicate files,
- merge symbols,
- sort by priority/depth/confidence/path,
- trim reasons,
- generate suggestedNextReads,
- global confidence,
- limitations.

Testy:

- duplicate file from multiple paths,
- priority ordering,
- max files truncation,
- suggested reads format.

### Krok 13: service facade

Dodac:

- `GitLabEndpointUseCaseContextService`.

Flow:

```text
validate request
 -> resolve endpoint
 -> open source session
 -> traverse
 -> compress
 -> return result
```

Testy:

- happy path GET,
- happy path PUT,
- endpoint not found,
- parse failure partial result,
- branch/ref is passed correctly.

### Krok 14: MCP tool

Przywrocic:

- `GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT`,
- metode w `GitLabMcpTools`,
- DTO tool response, jesli potrzebne albo bezposredni result integracyjny.

Opis toola ma jasno mowic:

- zwraca liste plikow i symboli,
- nie zwraca source code,
- AI powinno doczytac pliki/chunki kolejnymi tools,
- group/branch pochodza z hidden ToolContext.

Testy:

- `GitLabMcpToolsTest`,
- `GitLabMcpToolsContextTest`,
- `CopilotSdkToolFactoryTest`,
- budget policy.

### Krok 15: shared/operator REST endpoint

Opcjonalnie po MCP:

- endpoint w `api.gitlab`,
- request DTO w `api.gitlab`,
- manualny UI pozniej.

Nie robic frontendu w tym samym kroku co core traversal, zeby latwo izolowac
bledy parsera.

### Krok 16: dokumentacja i guidance

Po stabilizacji:

- uzupelnic opis capability w docs,
- dopisac Copilot-facing guidance, jesli tool ma byc uzywany przez incident
  flow albo przyszly flow explorer,
- opisac ograniczenia MVP.

## Test matrix

Backend unit:

- `GitLabEndpointUseCase*Test`,
- `GitLabRepositoryEndpointServiceTest`,
- `GitLabMcpToolsTest`,
- `GitLabMcpToolsContextTest`,
- `CopilotSdkToolFactoryTest`,
- `PackageDependencyGuardTest`.

Backend compile:

- `mvn -q -DskipTests compile`.

Targeted regression examples:

- GET z `DataProductController#getProduct`,
- PUT z `DataProductController#updateProduct`,
- OpenAPI/YAML endpoint z generated interface,
- plik z wieloma klasami/interfejsami,
- Spring Data repository bez implementacji,
- Lombok `@RequiredArgsConstructor`,
- MapStruct `@Mapper` + `INSTANCE`,
- Java 21 switch pattern.

## Kryteria akceptacji MVP

MVP jest gotowe, gdy:

1. Tool zwraca liste plikow bez source code.
2. Dla GET z notatki wskazuje controller, repository port,
   `ProductQueryRepository`, mapper i projekcje/form mappings.
3. Dla PUT z notatki wskazuje controller, `UpdateProductPort`,
   `UpdateProductService`, query/command repositories, mappery i domenowy
   model z logika update/status.
4. Nie probuje rozwinac Spring Data generated implementation.
5. Obsluguje package-private klasy/interfejsy w jednym pliku.
6. Obsluguje nested interfaces typu `ProductRepositoryPort.Query`.
7. Dla wielu implementacji zwraca kandydatow zamiast zgadywac.
8. Wynik miesci sie w kompaktowym JSON i ma `suggestedNextReads`.
9. Nie lamie granic pakietow `integrations -> agenttools/api/features`.
10. Testy targetowane i `PackageDependencyGuardTest` przechodza.

## Ryzyka i swiadome uproszczenia

- Bez symbol-solvera nie rozstrzygniemy wszystkich overloadow i generykow.
- Bez runtime Spring nie rozstrzygniemy wszystkich qualifierow i conditional
  beans.
- Search implementacji interfejsu moze zwrocic false positive; dlatego wynik
  ma `confidence` i `unresolved`.
- Generated OpenAPI interfaces moga nie istniec w repo. Wtedy endpoint
  inventory jest zrodlem prawdy, a traversal zaczyna sie od implementacji.
- Lombok generated accessors sa traktowane heurystycznie, nie jako realne
  metody AST.

Te uproszczenia sa akceptowalne, bo tool ma wskazac pliki do dalszej analizy
AI, a nie wydac ostateczny werdykt bez doczytania kodu.
