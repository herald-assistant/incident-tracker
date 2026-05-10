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
  `repo-map.yml/codeSearchScopes`, nie osobny komponent runtime.
- GitLab tools korzystajace z operational context maja wystawiac systemy jako
  kanoniczny target repozytoriow i `codeSearchScopes`. Nie dodawaj model-facing
  pol ani odpowiedzi dla osobnego targetu runtime; runtime/deployment names
  moga byc tylko sygnalami dopasowania albo opisem.

## Weryfikacja

- `PackageDependencyGuardTest` pilnuje, zeby `agenttools.*` nie importowalo
  warstw aplikacyjnych ani platformowych.
