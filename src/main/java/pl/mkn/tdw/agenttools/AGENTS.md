# AGENTS

## Zakres

Ten katalog zawiera neutralne kontrakty reusable tools/capability wspolne dla
MCP wrappers, platform AI i przyszlych agent runtimes oraz przenoszone
inkrementalnie wrappery Spring AI/MCP.

Obejmuje:

- `context/`
  hidden tool context keys wspolne dla runtime invocation,
- `database/`, `elasticsearch/`, `gitlab/`, `operationalcontext/`
  neutralne nazwy tools i prefixy capability,
- `<capability>/mcp/`
  wrappery Spring AI/MCP delegujace do reusable integracji albo neutralnych
  use case'ow.

Nie obejmuje:

- adapterow/integracji,
- Copilot SDK runtime,
- incident promptow, skilli, evidence pipeline ani policy feature'a.

## Zasady

- Trzymaj tu tylko male, stabilne kontrakty, ktore sa potrzebne przynajmniej
  dwom warstwom, np. MCP wrapperom i Copilot runtime.
- Wrappery `agenttools.*.mcp` moga importowac `integrations.*`, bo tools sa
  warstwa nad adapterami.
- `agenttools.*` nie moze importowac `analysis.*`, `aiplatform.*` ani
  `features.*`.
- Nazwy tools sa kontraktem capability. Zmiana nazwy toola to zmiana runtime
  contractu i wymaga testow MCP, tool factory/policy oraz dokumentacji.
- Implementacje tooli sa w `agenttools.<capability>.mcp`. Nie przywracaj ich do
  historycznego `analysis.mcp.*`.
- Kazda nowa capability albo nowy zestaw metod `@Tool` musi miec jawna
  konfiguracje `ToolCallbackProvider` w tym samym pakiecie
  `agenttools.<capability>.mcp`, zwykle jako
  `<Capability>McpToolConfiguration` z
  `MethodToolCallbackProvider.builder().toolObjects(...)`. Sam `@Component`
  z metodami `@Tool` nie wystarcza, bo Copilot SDK runtime widzi tylko
  zarejestrowane callbacki.
- Jesli bean tooli jest wlaczany flaga `@ConditionalOnProperty`, konfiguracja
  `ToolCallbackProvider` ma uzywac tej samej flagi. Nie rozdzielaj warunkow
  aktywacji beana tooli i callback providera.
- Dodaj test Spring contextu dla kazdej nowej capability MCP, ktory po
  wlaczeniu wymaganych properties zbiera wszystkie `ToolCallbackProvider` i
  asertuje komplet publicznych nazw tooli. Taki test ma lapac sytuacje, w
  ktorej implementacja toola istnieje, ale AI/MCP runtime nie ma do niej
  dostepu.
- Jesli tool ma byc dostepny z Copilotem w konkretnym feature, zaktualizuj
  feature'owa policy/allowliste oraz test policy. Rejestracja callbacka
  potwierdza, ze runtime zna tool, a policy potwierdza, ze dany feature
  faktycznie go dopuszcza w sesji.
- Operational context tools uzywaja prefixu `opctx_` i pozostaja neutralnym
  katalogiem encji. Wystawiaja `opctx_get_scope`, `opctx_list_entities`,
  `opctx_search` i `opctx_get_entity`.
- Operational context MCP mapper moze importowac
  `integrations.operationalcontext`, ale nie moze importowac incident feature'a,
  Copilot runtime ani HTTP API. Nie zwracaj raw payload/source preview.
- `codeSearchScope` w operational context tools jest wirtualna encja z
  `code-search-scopes.yml/codeSearchScopes`, nie osobny komponent runtime.
- GitLab tools korzystajace z operational context maja wystawiac systemy jako
  kanoniczny target repozytoriow i `codeSearchScopes`. Nie dodawaj model-facing
  pol ani odpowiedzi dla osobnego targetu runtime; runtime/deployment names
  moga byc tylko sygnalami dopasowania albo opisem.
- `gitlab_list_repository_branches`, `gitlab_list_repository_tree`,
  `gitlab_list_repository_files`, `gitlab_search_repository_files` i
  `gitlab_read_repository_file` i `gitlab_read_repository_file_chunk` sa jednym
  neutralnym zestawem read-only dla projektow w glownej grupie GitLab.
  `projectName` i (dla odczytu tresci) `branchRef` sa jawne w schema, a grupa
  wynika z konfiguracji. Lista galezi pozwala ustalic ref drugiego projektu.
  Hidden `GitLabRepositoryToolScope` przypina wybrany projekt do commita
  operatora i kolejne projekty w tej grupie do osobnych commitow. Tree/list/search
  zwracaja sciezki, nie `sourceRef`; pelny odczyt i odczyt fragmentu z
  przypietego commita rejestruja ref. Nawigacja i odczyt nie filtruja nazw ani tresci na podstawie
  wzorcow danych wrazliwych; pozostaja walidacja sciezki, rozmiaru i tekstu.
  Feature wybiera allowliste i budzet z tego wspolnego katalogu.
- `gitlab_read_openapi_endpoint_slice` jest neutralnym semantycznym odczytem
  jednej operacji OpenAPI 3.x albo Swagger 2.0 z pliku JSON/YAML/YML. Przyjmuje
  `filePath` oraz `httpMethod + endpointPath` albo dokladny `operationId`,
  zwraca typowany operation slice, efektywny context i ograniczone lokalne
  `$ref`. W session-bound scope odczyt odbywa sie na przypietym commicie i
  rejestruje zweryfikowany `sourceRef`. Feature decyduje, czy tool jest
  dostepny i kiedy ma zastapic ogolne full/chunk reads.
- Frontendowe route branch i TypeScript symbol slice tools mieszkaja w
  `gitlab.frontend.mcp` i deleguja do `integrations.gitlab.frontend`. Route
  branch przyjmuje bezpieczny screen `sliceRef` i `reason`. TypeScript symbol
  slice nie ujawnia syntetycznych refow: przyjmuje naturalny `filePath` z
  `declaringTypeName` albo dokladne wspolrzedne importu widocznego w kodzie
  (`consumerFilePath`, `moduleSpecifier`, `importedSymbol`), opcjonalne
  `memberNames` i `reason`. Group, project, path prefixes oraz source revision
  pochodza z hidden session context, a import jest rozwiazywany na zadanie z
  przypietego commita. Tool nie wymaga przygotowanego katalogu targetow. Nie
  przywracaj scope jako pol toola. Nie wystawiaj pelnego Screen
  Reachability jako MCP result, gdy zasila juz initial prompt.

## Weryfikacja

- `PackageDependencyGuardTest` pilnuje, zeby `agenttools.*` nie importowalo
  warstw aplikacyjnych ani platformowych.
