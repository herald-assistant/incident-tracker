# GitLab Java Method Use Case Context Tool - plan

## Status

Scoped implementation plan dla nowego GitLab toola. Dokument nie zastepuje
`07-open-work-plan.md`; opisuje jeden zaakceptowany kierunek pracy, ktory po
implementacji powinien zostac odhaczony albo wlaczony do glownego planu.

## Cel

Dodac nowy tool podobny do `gitlab_build_endpoint_use_case_context`, ale
startujacy od konkretnej klasy i metody Java. Tool ma pozwolic AI kontynuowac
analize use case'u wtedy, gdy initial context albo poprzedni tool zatrzymal sie
na serwisie, handlerze, strategii, mapperze albo innej metodzie, a model chce
zobaczyc, co dzieje sie dalej.

Docelowa nazwa toola MCP:

```text
gitlab_build_java_method_use_case_context
```

Docelowy endpoint Workbench/API:

```http
POST /api/gitlab/repository/java-method-use-case-context
```

## Problem do rozwiazania

Obecny `Endpoint Use Case Context` dobrze startuje od HTTP endpointu, ale gdy
AI potrzebuje kontynuowac flow z glebiej polozonego miejsca, musi czesto
chodzic klasa po klasie:

- `read_repository_file_outline`,
- `read_java_method_slice`,
- kolejne odczyty plikow albo chunkow,
- reczne wnioskowanie, ktora zaleznosc jest nastepnym krokiem.

To jest kosztowne tokenowo i latwo prowadzi do zbyt szerokiego dociagania kodu.
Nowy tool powinien przyjac precyzyjny seed: klasa + metoda + opcjonalna linia
wewnatrz tej metody, a nastepnie uzyc tego samego traversal engine, ktory zna
dependency injection, repozytoria, MapStruct, Spring Data i strategy registry.

## Zakres V1

V1 ma byc neutralnym GitLab capability w warstwie `integrations.gitlab`,
`agenttools.gitlab` i `api.gitlab`. Nie moze zawierac zasad specyficznych dla
incident analysis ani flow explorera. Te zasady trafia dopiero do runtime
skilli.

V1 obejmuje:

- nowy request/response contract dla startu od metody Java,
- resolver seed method z `className`, `methodName`, opcjonalnego `filePath`,
  `lineNumber`, `parameterCount` i `parameterTypes`,
- reuse istniejacego traversal engine,
- result limit oparty o liczbe zwroconych wynikow, a nie tylko liczbe
  przeszukanych plikow,
- ekspozycje przez REST Workbench API,
- ekspozycje przez GitLab MCP tools,
- testy CRM-specific i zanonimizowane,
- aktualizacje Workbench UI,
- aktualizacje runtime skilli GitLab dla incident trackera i flow explorera.

V1 nie obejmuje:

- `focusHints`,
- nowego AI prompt contractu dla feature'ow,
- zmiany operational context modelu,
- globalnej przebudowy `Endpoint Use Case Context`,
- automatycznego full call graphu calego repozytorium.

## Kontrakt requestu

Proponowany model integracyjny:

```java
record GitLabJavaMethodUseCaseContextRequest(
        String projectName,
        String filePath,
        String className,
        String methodName,
        Integer lineNumber,
        Integer parameterCount,
        List<String> parameterTypes,
        Integer maxDepth,
        Integer maxResults
)
```

Uwagi:

- `projectName` jest jawny tak jak w innych GitLab tools.
- `className` powinno przyjmowac FQN, relative nested name albo simple name.
  FQN ma najwyzsza precyzje.
- `methodName` jest wymagane.
- `filePath` jest opcjonalne. Jesli jest podane, resolver czyta bezposrednio
  ten plik. Jesli go brak, resolver szuka klasy w repozytorium.
- `lineNumber` jest opcjonalne i oznacza dowolna 1-based linie wewnatrz
  docelowej metody. `lineStart` zwrocone przez outline albo method slice jest
  poprawnym `lineNumber`, ale tool nie powinien wymagac, aby byla to linia
  deklaracji.
- `parameterCount` i `parameterTypes` sa opcjonalnymi disambiguatorami dla
  overloadow.
- `maxDepth` zostaje, bo kontroluje logiczna glebokosc traversal.
- `maxResults` jest podstawowym limitem model-facing dla rozmiaru odpowiedzi.
  Zastepuje mentalny model `maxFiles` w nowym toolu.

MCP dodaje standardowe pola runtime:

- `branchRef`,
- opcjonalne `applicationName`,
- opcjonalny `reason`,
- hidden `ToolContext`.

REST/API dostaje `group` i `branch` tak jak obecne Workbench endpointy albo
przez analogiczny wrapper API requestu.

## Semantyka `lineNumber`

Istniejacy `gitlab_read_java_method_slice` ma pole nazwane `lineStart`, ale
implementacyjnie akceptuje rowniez linie mieszczaca sie w zakresie metody.
Nowy kontrakt powinien nazwac to wprost `lineNumber`, zeby AI moglo uzyc:

- linii ze stacktrace,
- linii z outline,
- linii z method slice,
- linii wskazanej przez operatora w Workbench.

Algorytm:

1. Parse Java przez JavaParser.
2. Znajdz typ pasujacy do `className`.
3. Znajdz metody o nazwie `methodName`.
4. Jesli `lineNumber` jest podane, zostaw metody, dla ktorych
   `lineStart <= lineNumber <= lineEnd`.
5. Jesli podano `parameterCount`, filtruj overloady po liczbie parametrow.
6. Jesli podano `parameterTypes`, wybierz najlepsze dopasowanie typow tak jak
   w obecnym `GitLabJavaMethodLocator`.
7. Gdy wynik nadal nie jest jednoznaczny, zwroc kandydatow zamiast zgadywac.

## Kontrakt odpowiedzi

Nie zwracac mylacego `endpoint = null` w kontrakcie endpointowym. Lepiej dodac
osobny response z takim samym rdzeniem:

```java
record GitLabJavaMethodUseCaseContextResult(
        GitLabEndpointUseCaseRepositoryContext repository,
        GitLabJavaMethodUseCaseEntryMethod entryMethod,
        List<GitLabEndpointUseCaseFileCandidate> files,
        List<GitLabEndpointUseCaseRelation> relations,
        List<GitLabEndpointUseCaseUnresolvedReference> unresolved,
        List<String> limitations,
        List<String> suggestedNextReads,
        GitLabJavaMethodUseCaseContextLimits limits,
        GitLabEndpointUseCaseConfidence confidence
)
```

`entryMethod` powinno zawierac:

- `status`: `RESOLVED`, `AMBIGUOUS`, `NOT_FOUND`, `PARSE_FAILED`,
  `READ_FAILED`, `INVALID_REQUEST`,
- `filePath`,
- `declaringTypeSimpleName`,
- `declaringTypeRelativeName`,
- `declaringTypeQualifiedName`,
- `methodName`,
- `signature`,
- `lineStart`,
- `lineEnd`,
- `parameterCount`,
- `parameterTypes`,
- `confidence`,
- `candidates`.

Kandydaci powinni miec podobny ksztalt do `GitLabJavaMethodSliceMethodCandidate`
i `GitLabEndpointUseCaseMethodCandidate`: file path, typ deklarujacy,
sygnatura, lineStart/lineEnd, liczba i typy parametrow.

## Limity i kompresja

Nowy tool powinien rozdzielic dwa typy limitow:

- limit eksploracji: wewnetrzny guard chroniacy runtime, np. maksymalna liczba
  odczytanych/parszowanych plikow,
- limit wyniku: model-facing `maxResults`, czyli ile najlepszych elementow
  zwracamy po rankingu i kompresji.

V1:

- `maxDepth` kontroluje glebokosc traversal,
- `maxResults` kontroluje liczbe zwroconych file/method candidates,
- wewnetrzny `maxReadFiles` zostaje server-side guardem,
- odpowiedz musi ujawnic `readFileCount`, `readFileLimitReached`,
  `maxResultsReached` i `maxDepthReached`,
- sortowanie wynikow powinno preferowac seed method, direct collaborators,
  use case service, strategie dopasowane przez registry, repository port,
  repository implementation, mapper, model domenowy i integracje.

Ten model mozna pozniej przeniesc do `Endpoint Use Case Context`, ale nie jest
to warunek V1.

## Backend implementation plan

1. Dodac request/response records w `integrations.gitlab.usecase`.
2. Dodac `GitLabJavaMethodUseCaseEntryResolver`, ktory:
   - normalizuje `className`, `filePath`, `methodName`,
   - czyta plik bezposrednio albo znajduje klase przez `GitLabJavaSourceResolver`,
   - parsuje AST,
   - rozstrzyga metode przez nazwe, typ, linie i overload metadata,
   - zwraca resolved entry method albo kandydatow.
3. Rozszerzyc `GitLabJavaMethodLocator` o wariant resolution po `lineNumber`
   albo dodac maly resolver obok niego, bez duplikowania mapowania kandydatow.
4. Rozszerzyc `GitLabEndpointUseCaseTraversalService` o metode startujaca od
   resolved seed method, np. `traverseFromMethod(...)`.
5. Zachowac obecne `traverse(...)` dla endpointu jako wrapper startujacy od
   endpoint handlera.
6. Dostosowac `GitLabEndpointUseCaseTraversalState` lub dodac neutralniejszy
   state wrapper tak, aby endpoint byl opcjonalnym typem wejscia, ale bez
   rozbijania istniejacego kontraktu endpointowego.
7. Dodac `GitLabJavaMethodUseCaseContextService`, ktory sklada resolver entry
   method + traversal + compressor.
8. Dodac endpoint REST w `GitLabRepositorySearchController`.
9. Dodac nazwe toola w `GitLabToolNames`.
10. Dodac metode MCP w `GitLabMcpTools` z opisem optymalnego uzycia.

## Workbench UI plan

Dodac nowy tool w `frontend/src/app/features/evidence-console` obok
`Endpoint Use Case Context`.

Pola formularza:

- `projectName`,
- `branch`,
- opcjonalne `filePath`,
- `className`,
- `methodName`,
- opcjonalne `lineNumber`,
- opcjonalne `parameterCount`,
- opcjonalne `parameterTypes`,
- `maxDepth`,
- `maxResults`.

Widok wyniku:

- status entry method,
- resolved method albo lista candidates,
- files z rolami, metodami i confidence,
- relations,
- unresolved,
- limitations,
- suggested next reads,
- limits/coverage strip.

UX:

- gdy response ma `AMBIGUOUS`, UI powinno pozwolic latwo przepisac wybranego
  kandydata do formularza,
- `lineNumber` powinno byc opisane jako linia wewnatrz metody, nie tylko linia
  deklaracji,
- `maxResults` powinno byc pokazywane jako limit zwracanego kontekstu.

## Skill update plan

Zaktualizowac runtime skille po implementacji backendu i UI.

### `incident-code-grounding`

Dodac zasade:

- jesli evidence, stacktrace, previous tool result albo initial prompt wskazuje
  konkretna klase i metode, a endpoint context nie obejmuje dalszego flow,
  preferuj `gitlab_build_java_method_use_case_context` przed recznym czytaniem
  kolejnych klas,
- uzywaj `lineNumber` ze stacktrace, outline albo method slice jako
  disambiguatora,
- przy `AMBIGUOUS` wybierz kandydata zgodny ze stacktrace, package scope,
  applicationName i dotychczasowa hipoteza,
- dla strategii typu `List<Interface>` pozwol traversalowi rozstrzygnac
  implementacje przez registry zamiast czytac wszystkie implementacje.

### `flow-explorer-code-grounding`

Dodac zasade:

- dla flow explorera endpoint context pozostaje pierwszym wyborem, gdy znany
  jest endpoint,
- nowy method context jest najlepszym follow-upem, gdy model chce kontynuowac
  od use case service, handlera, strategii, mappera, repository portu albo
  metody wskazanej przez wynik poprzedniego toola,
- uzywaj go do poszerzenia glownego flow, nie do pelnego dumpu repozytorium,
- `maxResults` dobieraj do celu: mniejszy dla compact, wiekszy dla deep
  discovery.

## Test plan

Wszystkie nowe testy i fixture'y maja byc CRM-specific i zanonimizowane.

Backend:

- resolves method by FQN + methodName,
- resolves method by filePath + simple class + methodName,
- resolves method by `lineNumber` inside method body,
- returns candidates for overload ambiguity,
- returns candidates for multiple classes with same simple name,
- traverses direct service dependency,
- traverses `List<Interface>` strategy registry to selected implementation,
- respects `maxDepth`,
- respects `maxResults` after ranking/compression,
- reports internal read-file guard separately from result limit.

MCP/API:

- validates session scope and branch,
- maps request to service,
- logs request/result without leaking source content,
- returns candidates and limitations unchanged.

UI:

- renders form,
- sends request,
- renders resolved entry method,
- renders ambiguous candidates,
- can copy selected candidate back to request fields,
- renders limits and limitations.

Skills:

- static tests or snapshot checks, jesli istnieja,
- manual smoke na CRM fixture dla incident tracker i flow explorer.

## Kolejnosc prac

1. Backend request/response + entry resolver.
2. Traversal start from method.
3. REST endpoint.
4. MCP tool.
5. Backend tests.
6. Workbench UI.
7. UI tests/build.
8. Skill updates dla incident trackera i flow explorera.
9. Smoke test na CRM use case.
10. Ocena, czy `Endpoint Use Case Context` tez powinien przejsc z `maxFiles`
    na `maxResults`.

## Otwarte decyzje

- Czy `maxResults` liczy tylko pliki, czy lacznie pliki + metody + unresolved?
  Proponowane V1: limituje file candidates po kompresji, a metody sa czescia
  tych file candidates.
- Czy response powinien miec strukturalne `nextReads` obok stringowego
  `suggestedNextReads`? Proponowane V1: zostawic stringi dla kompatybilnosci,
  dodac strukturalne next reads dopiero przy szerszym porzadkowaniu kontraktu.
- Czy nowe role powinny zawierac `ENTRY_METHOD` albo `FOCUS_METHOD`?
  Proponowane V1: nie zmieniac enumu, tylko oznaczyc seed w `entryMethod` i
  role file candidate dobrac heurystycznie.
- Czy `className` bez `projectName` powinno szukac po wszystkich dostepnych
  repozytoriach scope'u? Proponowane V1: wymagac `projectName`, a cross-repo
  discovery zostawic dla `gitlab_list_available_repositories` i
  `gitlab_search_repository_candidates`.

