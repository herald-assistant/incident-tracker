# AGENTS

## Zakres

Ten katalog zawiera neutralne kontrakty reusable tools/capability wspolne dla
MCP wrappers, platform AI i przyszlych agent runtimes oraz przenoszone
inkrementalnie wrappery Spring AI/MCP.

Obejmuje:

- `context/`
  hidden tool context keys wspolne dla runtime invocation,
- `database/`, `elasticsearch/`, `gitlab/`
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

## Weryfikacja

- `PackageDependencyGuardTest` pilnuje, zeby `agenttools.*` nie importowalo
  warstw aplikacyjnych ani platformowych.
