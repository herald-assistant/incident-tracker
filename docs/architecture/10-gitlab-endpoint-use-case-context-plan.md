# GitLab Endpoint Use Case Context Tool Plan

## Cel

Nowy tool `gitlab_build_endpoint_use_case_context` ma budowac kompaktowy,
deterministyczny kontekst use case'u dla konkretnego endpointu HTTP w
repozytorium GitLaba.

Wynik ma pozwolic AI:

1. opisac funkcjonalnie, co robi endpoint,
2. opisac technicznie, jakie klasy/metody biora udzial w flow,
3. zbudowac diagram techniczny albo biznesowy,
4. skonfrontowac nowe wymaganie z istniejacym use case'em i ocenic, czy
   powinno wejsc do tego endpointu, innego endpointu, event listenera albo
   nowego use case'u.

Tool nie ma generowac narracji ani zwracac kodu zrodlowego. Ma zwracac fakty:
kompaktowy graf, liste klas, role, terminale, ostrzezenia i krotkie evidence.

## Decyzje MVP

- MVP dziala na `branch/ref` z hidden `ToolContext`. Nie schodzimy jeszcze do
  `commitSha`.
- Wynik musi jawnie raportowac `requestedBranch` i ograniczenie stabilnosci
  branch-based analysis.
- `gitLabGroup` i `gitLabBranch` pozostaja hidden contextem. Model nie podaje
  grupy ani brancha.
- Model podaje `projectName` oraz konkretny endpoint przez `endpointId` albo
  `httpMethod + endpointPath`.
- GitLab API jest zrodlem snapshotu kodu: repository tree, raw file/chunk i
  search fallback. Nie dodajemy na MVP pobierania `repository/archive.zip`.
- Refleksje i dynamiczne runtime registries ignorujemy. Uwzgledniamy natomiast
  dependency injection, interfejsy -> implementacje oraz dziedziczenie.
- Domyslny wynik jest tokenowo oszczedny: bez kodu, bez pelnego AST, bez
  pelnego bean registry i bez boilerplate'u.
- Logika analizy mieszka w `integrations.gitlab`. `agenttools.gitlab.mcp`
  wystawia cienka fasade toola.

## Non-goals MVP

- Brak gwarancji pelnej zgodnosci z runtime Springa dla `@Profile`,
  `@ConditionalOnProperty`, custom bean factory post processors i refleksji.
- Brak rozwijania async listenerow domyslnie.
- Brak pobierania i rozpakowywania pelnego archiwum repozytorium.
- Brak source code w wyniku toola. Fragmenty kodu pozostaja w istniejacych
  read/chunk tools.
- Brak frontend UI w tym etapie.

## Kontrakt toola

Docelowa nazwa:

```text
gitlab_build_endpoint_use_case_context
```

Model-facing input:

```json
{
  "projectName": "orders-api",
  "endpointId": "POST /api/orders -> pl.mkn.orders.OrderController#create",
  "httpMethod": "POST",
  "endpointPath": "/api/orders/123/submit",
  "sourcePathPrefix": "src/main/java",
  "outputMode": "COMPACT",
  "maxDepth": 8,
  "maxNodes": 80,
  "includeAsyncConsumers": false,
  "reason": "Opis endpointu /api/orders/submit i ocena nowego wymagania."
}
```

Zasady inputu:

- `projectName` jest wymagane.
- `endpointId` ma pierwszenstwo, bo pochodzi bezposrednio z
  `gitlab_list_repository_endpoints`.
- Jesli `endpointId` nie ma, wymagane sa `httpMethod` i `endpointPath`.
- `sourcePathPrefix` jest opcjonalny, domyslnie `src/main/java`.
- `outputMode` domyslnie `COMPACT`.
- `includeAsyncConsumers` domyslnie `false`; publikacje eventow sa wtedy
  terminalami flow.

Hidden input z `ToolContext`:

- `gitLabGroup`,
- `gitLabBranch`,
- `correlationId`,
- `environment` jako opcjonalny sygnal do warnings/profili, nie jako warunek
  runtime resolution w MVP.

## Output

Wynik powinien byc strukturalny i maly:

```json
{
  "repository": {
    "group": "TENANT-ALPHA",
    "projectName": "orders-api",
    "requestedBranch": "develop",
    "sourcePathPrefix": "src/main/java",
    "indexStatus": "BUILT_DURING_CALL"
  },
  "endpoint": {
    "endpointId": "POST /api/orders/{id}/submit -> OrderController#submit",
    "httpMethods": ["POST"],
    "inputPath": "/api/orders/123/submit",
    "matchedPathPattern": "/api/orders/{id}/submit",
    "controllerClass": "pl.mkn.orders.api.OrderController",
    "controllerMethod": "submit(String)",
    "sourcePath": "src/main/java/pl/mkn/orders/api/OrderController.java",
    "lineStart": 42,
    "lineEnd": 58
  },
  "useCaseSummary": {
    "mainResponsibility": "submit order",
    "businessObjects": ["Order"],
    "sideEffects": ["repository-write", "event-publish"],
    "externalSystems": ["PaymentGateway"],
    "asyncBoundaries": ["OrderSubmittedEvent"]
  },
  "graph": {
    "nodes": [],
    "edges": []
  },
  "classList": [],
  "warnings": [],
  "evidence": [],
  "suggestedNextReads": [],
  "limits": {
    "maxDepth": 8,
    "maxNodes": 80,
    "maxNodesReached": false
  }
}
```

### Node

```json
{
  "id": "n1",
  "kind": "METHOD",
  "classFqn": "pl.mkn.orders.application.SubmitOrderUseCase",
  "methodSignature": "submit(String)",
  "role": "USE_CASE_SERVICE",
  "depth": 1,
  "sourcePath": "src/main/java/pl/mkn/orders/application/SubmitOrderUseCase.java",
  "lineStart": 31,
  "lineEnd": 72,
  "terminal": false,
  "terminalReason": null
}
```

### Edge

```json
{
  "from": "n1",
  "to": "n2",
  "kind": "SYNC_CALL",
  "resolutionKind": "SPRING_BEAN_POLYMORPHIC",
  "call": "paymentPolicy.evaluate(order)",
  "line": 45,
  "confidence": "HIGH",
  "ambiguous": false
}
```

### Class list item

```json
{
  "classFqn": "pl.mkn.orders.domain.DefaultPaymentPolicy",
  "role": "POLICY",
  "depth": 2,
  "methods": ["evaluate(Order)"],
  "terminal": false,
  "reason": "Implementation of PaymentPolicy resolved by @Primary."
}
```

## Output modes

### COMPACT

Default for AI. Includes endpoint, use case summary, class list, selected
important graph edges, warnings and compact evidence. No source code.

### GRAPH

Includes method-level graph with all relevant nodes and edges up to limits.
Useful for technical diagrams.

### BUSINESS

Prioritizes roles, business objects, side effects, external systems and async
boundaries. Filters technical helper edges more aggressively.

### DEBUG

For manual verification and tests. May include bean candidates, unresolved
calls, inheritance details and skipped edges. Not recommended for normal AI
flow.

## Token discipline

Default result must not include:

- full source code,
- full method bodies,
- full imports,
- full AST,
- full bean registry,
- all DTO classes,
- getters/setters/builders,
- JDK/Spring/Lombok internals,
- generated implementation details unavailable in repository,
- async consumers unless requested.

Default result should include:

- participating classes/methods,
- roles,
- source path and line references,
- edge kind and resolution kind,
- terminal repositories/clients/events,
- short reasons,
- warnings instead of guesses.

## Internal architecture

### New integration service

Package:

```text
src/main/java/pl/mkn/incidenttracker/integrations/gitlab
```

Proposed classes:

- `GitLabEndpointUseCaseContextService`
- `GitLabEndpointUseCaseContextRequest`
- `GitLabEndpointUseCaseContextResult`
- `GitLabCodeIndex`
- `GitLabCodeIndexBuilder`
- `GitLabEndpointMatcher`
- `GitLabSpringBeanRegistryBuilder`
- `GitLabDependencyInjectionResolver`
- `GitLabTypeHierarchyResolver`
- `GitLabMethodCallGraphTraverser`
- `GitLabUseCaseContextCompressor`

### MCP facade

Package:

```text
src/main/java/pl/mkn/incidenttracker/agenttools/gitlab/mcp
```

Changes:

- add `GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT`,
- add DTO wrapper if needed in `GitLabToolDtos`,
- add `@Tool` method in `GitLabMcpTools`,
- keep all semantic analysis in `integrations.gitlab`.

## Code index model

Minimal internal model:

- `EndpointIndex`
  - HTTP methods,
  - normalized path patterns,
  - controller class,
  - controller method,
  - source location.
- `TypeIndex`
  - class/interface/record/enum FQN,
  - simple name,
  - package,
  - annotations,
  - fields,
  - constructors,
  - methods,
  - source path.
- `TypeHierarchyIndex`
  - `extends`,
  - `implements`,
  - interface -> implementations,
  - assignable types.
- `SpringBeanRegistry`
  - bean name,
  - bean type,
  - assignable types,
  - qualifiers,
  - primary,
  - source: component scan, bean method, repository interface, feign client,
    mapper.
- `MethodCallIndex`
  - owner class/method,
  - receiver expression,
  - receiver static type,
  - method name,
  - raw expression,
  - source line.

## Parsing strategy

MVP powinien uzyc AST parsera dla Java, bo regex nie wystarczy do DI,
interfejsow i dziedziczenia.

Kandydat techniczny:

```text
JavaParser + JavaSymbolSolver
```

Plan:

1. parsowac pliki `.java` z `sourcePathPrefix`,
2. ignorowac `src/test`, `target`, `build`, generated tam gdzie mozliwe,
3. wykrywac source roots typu `*/src/main/java`,
4. budowac wlasny serializowalny model posredni, a nie zwracac typow parsera,
5. przy bledzie parsera dodawac warning i kontynuowac indeksowanie pozostalych
   plikow.

## Endpoint matching

Kolejnosc:

1. jesli podano `endpointId`, szukac dokladnego endpointu z inventory,
2. jesli podano `httpMethod + endpointPath`, dopasowac path do patternu,
3. jesli jest wiele matchy, zwrocic `MULTIPLE_ENDPOINTS_MATCHED`,
4. jesli nie ma matcha, zwrocic `ENDPOINT_NOT_FOUND`.

Path matching:

- literal `/api/orders`,
- template `/api/orders/{id}`,
- warianty z trailing slash,
- wiele mappingow na jednej metodzie.

Rozwiazywanie path expressions:

- MVP: string literals,
- v1: `static final String` i proste konkatenacje,
- v2: placeholder default value `${api.base:/api}`,
- v3: properties/profile-aware.

Nie zgadywac nierozwiazanych pathow. Dodawac warning
`UNRESOLVED_ENDPOINT_PATH`.

## Spring Bean Registry

Uwzglednic:

- `@Component`,
- `@Service`,
- `@Repository`,
- `@Controller`,
- `@RestController`,
- `@Configuration`,
- `@Bean`,
- `@Primary`,
- `@Qualifier`,
- `@Mapper(componentModel = "spring")`,
- `@FeignClient`,
- Spring Data repository interfaces.

Bean assignability:

- concrete class,
- all interfaces,
- all superclasses.

Bean name:

- explicit annotation value when available,
- `@Bean` method name,
- default Spring-style lower camel case simple class name.

Conditional/profile annotations:

- record metadata,
- do not fully resolve in MVP,
- emit warning `CONDITIONAL_BEAN` when it affects candidate selection.

## Dependency Injection resolver

Supported injection points:

- constructor injection,
- Lombok `@RequiredArgsConstructor` over `final` fields,
- field injection with `@Autowired`,
- setter/method injection with `@Autowired`,
- record constructor parameters,
- `@Qualifier`,
- `@Primary`,
- bean name fallback by field/parameter name.

Resolution order:

1. required type,
2. `@Qualifier`,
3. single `@Primary`,
4. bean name equals field/parameter name,
5. single candidate,
6. ambiguous,
7. unresolved.

Ambiguity is not hidden. In `COMPACT`, do not expand all ambiguous branches by
default. Return warning and representative candidates. In `GRAPH` or `DEBUG`,
allow expansion up to limits.

## Interfaces and implementations

When receiver static type is an interface:

1. check whether receiver is an injected field/parameter,
2. resolve concrete bean by DI resolver,
3. resolve method on concrete type,
4. if ambiguous, mark edge `ambiguous=true`,
5. if unresolved, create terminal/unresolved node and warning.

Resolution kinds:

- `DIRECT_METHOD`,
- `THIS_METHOD`,
- `SUPER_METHOD`,
- `INHERITED_METHOD`,
- `SPRING_BEAN`,
- `SPRING_BEAN_POLYMORPHIC`,
- `STATIC_METHOD`,
- `NEW_INSTANCE`,
- `EXTERNAL_LIBRARY`,
- `UNRESOLVED`.

## Dziedziczenie

Support:

- class `extends`,
- interface `extends`,
- class `implements`,
- abstract classes,
- inherited fields,
- inherited methods,
- method override,
- `this.method()`,
- `super.method()`.

Method lookup:

1. concrete class,
2. nearest override,
3. superclass chain,
4. interface default methods when available,
5. unresolved warning.

## Call graph traversal

Start:

```text
matched controller method
```

Traversal:

1. get method calls from `MethodCallIndex`,
2. resolve receiver,
3. resolve target method,
4. classify target role and edge kind,
5. add node/edge,
6. continue only when target is relevant and not terminal,
7. stop at `maxDepth`, `maxNodes` and package filters.

Receiver cases:

- `this.method()`,
- `super.method()`,
- `field.method()`,
- `parameter.method()`,
- `localVariable.method()`,
- `StaticClass.method()`,
- `new SomeClass().method()`.

Default package filters:

- include inferred application root package,
- exclude `java.`, `javax.`, `jakarta.`, `org.springframework.`, `lombok.`,
  `org.slf4j.`.

## Terminal nodes

Terminal by default:

- Spring Data repository interface,
- Feign client,
- WebClient/RestTemplate wrapper,
- Kafka/Rabbit/JMS publisher,
- `ApplicationEventPublisher`,
- MapStruct mapper interface without generated implementation,
- external library,
- JDK/Spring Framework class.

Terminal node is not an error. It marks a meaningful boundary.

## Async and events

Default:

- event publish is shown as terminal `EVENT_PUBLISH`,
- listeners are not expanded.

Future option:

```json
{
  "includeAsyncConsumers": true
}
```

Then resolver may search:

- `@EventListener`,
- `@TransactionalEventListener`,
- `@KafkaListener`,
- `@RabbitListener`,
- `@JmsListener`.

## Mapper, DTO and domain model filtering

Mapper:

- include if explicitly called in endpoint flow,
- include if it maps request -> command or domain -> response,
- skip generated implementation unless available and relevant.

DTO:

- do not add DTO-only classes as default nodes,
- expose request/response DTO names in endpoint/use case summary,
- include only when user requests data model details or DTO contains relevant
  validation/business semantics.

Domain model:

- list business objects in `useCaseSummary.businessObjects`,
- add domain class node only if a business method is called, for example
  `order.submit()` or `application.approve(decision)`.

## Role classification

Roles:

- `CONTROLLER`,
- `USE_CASE_SERVICE`,
- `SERVICE`,
- `DOMAIN_SERVICE`,
- `POLICY`,
- `STRATEGY`,
- `VALIDATOR`,
- `MAPPER`,
- `REPOSITORY`,
- `EXTERNAL_CLIENT`,
- `EVENT_PUBLISHER`,
- `EVENT_LISTENER`,
- `CONFIGURATION`,
- `DOMAIN_MODEL`,
- `UNKNOWN`.

Role inference examples:

- `@RestController` / `@Controller` -> `CONTROLLER`,
- `@Service` and name contains `UseCase` -> `USE_CASE_SERVICE`,
- `@Service` and name contains `Policy` -> `POLICY`,
- `@Service` and name contains `Strategy` -> `STRATEGY`,
- `@Repository` or Spring Data interface -> `REPOSITORY`,
- `@FeignClient` or name contains `Client` / `Gateway` -> `EXTERNAL_CLIENT`,
- name contains `Mapper` -> `MAPPER`,
- name contains `Validator` -> `VALIDATOR`,
- event publisher wrapper -> `EVENT_PUBLISHER`.

## Edge kinds

Useful edge kinds for AI diagrams:

- `SYNC_CALL`,
- `VALIDATION`,
- `MAPPING`,
- `REPOSITORY_READ`,
- `REPOSITORY_WRITE`,
- `EXTERNAL_CALL`,
- `EVENT_PUBLISH`,
- `ASYNC_BOUNDARY`,
- `CONFIGURES_BEAN`,
- `INHERITANCE_CALL`,
- `UNRESOLVED_CALL`.

## Warnings

Standard warning codes:

- `BRANCH_REF_NOT_IMMUTABLE`,
- `ENDPOINT_NOT_FOUND`,
- `MULTIPLE_ENDPOINTS_MATCHED`,
- `UNRESOLVED_ENDPOINT_PATH`,
- `PARSER_ERROR`,
- `UNRESOLVED_BEAN`,
- `AMBIGUOUS_BEAN`,
- `CONDITIONAL_BEAN`,
- `UNRESOLVED_METHOD_CALL`,
- `AMBIGUOUS_METHOD_OVERLOAD`,
- `INHERITED_METHOD_RESOLVED`,
- `GENERATED_CODE_NOT_AVAILABLE`,
- `ASYNC_CONSUMERS_SKIPPED`,
- `MAX_DEPTH_REACHED`,
- `MAX_NODES_REACHED`,
- `EXTERNAL_LIBRARY_SKIPPED`.

Warnings should be short and structured. They are how AI knows which claims
must not be stated as certain.

## Confidence

Deterministic confidence:

- `HIGH`
  - endpoint matched uniquely,
  - main DI edges resolved,
  - no major unresolved/ambiguous branches.
- `MEDIUM`
  - some calls unresolved,
  - conditional beans present,
  - fallback static type resolution used.
- `LOW`
  - endpoint path partially unresolved,
  - many ambiguous beans,
  - parser errors affect core files,
  - max limits reached early.

## Implementation checklist

### Step 1: Contracts and naming

- [x] Add `GitLabToolNames.BUILD_ENDPOINT_USE_CASE_CONTEXT`.
- [x] Add integration request/result records.
- [x] Add output enums: `OutputMode`, `Role`, `NodeKind`, `EdgeKind`,
  `ResolutionKind`, `Confidence`.
- [x] Add warnings/evidence records.
- [x] Add unit tests for DTO defaults and immutability.

### Step 2: GitLab source snapshot on branch

- [x] Reuse `GitLabRepositoryTreeService` and request-scoped cache.
- [x] Add branch-based index key:
      `group/projectName/branch/sourcePathPrefix`.
- [x] Add `BRANCH_REF_NOT_IMMUTABLE` warning in every MVP result.
- [x] Filter source files: Java main sources only, no tests/generated/build.
- [x] Add limits for indexed files and read characters.

### Step 3: Parser and code index

- [x] Add Java parser dependency after dependency review.
- [x] Build `TypeIndex` from `.java` files.
- [x] Build `TypeHierarchyIndex`.
- [x] Build `MethodCallIndex`.
- [x] Keep parser-specific classes out of public result DTO.
- [x] Continue indexing after parser errors and collect warnings.

### Step 4: Endpoint index and matcher

- [x] Reuse or adapt `GitLabRepositoryEndpointService` parsing rules.
- [x] Build endpoint index from parsed annotations.
- [x] Match by `endpointId`.
- [x] Match actual path against path pattern.
- [x] Return candidates for ambiguous matches.
- [x] Add tests for literal path, path variable and multiple mappings.

### Step 5: Spring Bean Registry

- [x] Detect component stereotypes.
- [x] Detect `@Configuration` and `@Bean`.
- [x] Detect `@Primary` and `@Qualifier`.
- [x] Detect `@Mapper(componentModel = "spring")`.
- [x] Detect `@FeignClient`.
- [x] Detect Spring Data repositories.
- [x] Compute assignable types through hierarchy.

### Step 6: Dependency Injection resolver

- [x] Constructor injection.
- [x] Lombok `@RequiredArgsConstructor` final fields.
- [x] Field injection.
- [x] Setter/method injection.
- [x] Record constructor.
- [x] Qualifier narrowing.
- [x] Primary selection.
- [x] Bean name fallback.
- [x] Ambiguous/unresolved warnings.

### Step 7: Call target resolver

- [x] Resolve `this.method()`.
- [x] Resolve `super.method()`.
- [x] Resolve field calls through DI when field is injected.
- [x] Resolve local variable and parameter static types.
- [x] Resolve interface receiver through bean registry when possible.
- [x] Resolve inherited methods.
- [x] Mark external/JDK/Spring targets as terminal/skipped.

### Step 8: Graph traversal

- [x] BFS/DFS from controller method.
- [x] Apply `maxDepth` and `maxNodes`.
- [x] Add terminal node handling.
- [x] Classify edge kinds.
- [x] Track depth and reasons.
- [x] Avoid cycles.
- [x] Add warnings when limits are reached.

### Step 9: Compression and output modes

- [x] Generate `classList` from method graph.
- [x] Generate compact `useCaseSummary`.
- [x] Implement `COMPACT`.
- [x] Implement `GRAPH`.
- [x] Implement `BUSINESS`.
- [x] Implement `DEBUG` last.
- [x] Ensure no source code is emitted by default.

### Step 10: MCP facade

- [x] Add tool method to `GitLabMcpTools`.
- [x] Read group/branch from hidden `ToolContext`.
- [x] Log request/result with counts, limits and warning count.
- [x] Keep model-facing schema small.
- [x] Add tests for tool wrapper and hidden scope usage.

### Step 11: Evidence capture and AI guidance

- [ ] Decide whether result should be captured as tool evidence.
- [ ] If captured, map to compact `AnalysisEvidenceSection`.
- [ ] Update incident Copilot guidance only if needed.
- [ ] Keep neutral tool description free of incident-specific semantics.

### Step 12: Verification

- [ ] Unit tests for endpoint matching.
- [ ] Unit tests for DI resolution: qualifier, primary, name fallback,
      ambiguous and unresolved.
- [ ] Unit tests for inheritance: inherited field, inherited method,
      override.
- [ ] Unit tests for repository/client/event terminals.
- [ ] Unit tests for token filters: DTO skip, mapper include only when called.
- [ ] Tool tests for `GitLabMcpTools`.
- [ ] `PackageDependencyGuardTest`.
- [ ] `mvn -q -DskipTests compile`.

## Suggested MVP test fixtures

Create small in-memory repository fixtures:

1. Controller -> concrete service -> repository terminal.
2. Controller -> interface service -> one `@Service` implementation.
3. Controller -> interface policy -> two implementations, one `@Primary`.
4. Controller -> interface policy -> two implementations, no resolver:
   `AMBIGUOUS_BEAN`.
5. Service extends base service and calls inherited method.
6. Service publishes event; listener skipped by default.
7. Mapper called explicitly; DTO classes not emitted as nodes.
8. Endpoint path variable match: `/api/orders/123` ->
   `/api/orders/{id}`.

## Open questions

- Czy dodajemy JavaParser od razu, czy robimy pierwszy bardzo waski parser AST
  tylko dla klas/metod/adnotacji?
- Jaki limit indexed files przyjmujemy na start: 300, 500 czy konfigurowalny?
- Czy branch-based index cache ma byc tylko request-scoped, czy aplikacyjny
  cache z TTL?
- Czy `BUSINESS` mode ma byc deterministyczny tylko z nazw/rol, czy pozniej
  dostanie osobny LLM-facing summarizer po stronie AI?
- Czy `includeAsyncConsumers` implementujemy w pierwszym toolu, czy dopiero po
  stabilizacji sync flow?
