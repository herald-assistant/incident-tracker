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
- Operational context tools uzywaja prefixu `opctx_` i pozostaja neutralnym
  katalogiem encji. W V1 wystawiaja `opctx_get_scope`, `opctx_list_entities`,
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
