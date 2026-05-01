# AGENTS

## Zakres

Ten katalog zawiera neutralne kontrakty reusable tools/capability wspolne dla
MCP wrappers, platform AI i przyszlych agent runtimes.

Obejmuje:

- `context/`
  hidden tool context keys wspolne dla runtime invocation,
- `database/`, `elasticsearch/`, `gitlab/`
  neutralne nazwy tools i prefixy capability.

Nie obejmuje:

- implementacji `@Tool`,
- Spring AI/MCP rejestracji,
- adapterow/integracji,
- Copilot SDK runtime,
- incident promptow, skilli, evidence pipeline ani policy feature'a.

## Zasady

- Trzymaj tu tylko male, stabilne kontrakty, ktore sa potrzebne przynajmniej
  dwom warstwom, np. MCP wrapperom i Copilot runtime.
- `agenttools.*` nie moze importowac `analysis.*`, `integrations.*`,
  `aiplatform.*` ani `features.*`.
- Nazwy tools sa kontraktem capability. Zmiana nazwy toola to zmiana runtime
  contractu i wymaga testow MCP, tool factory/policy oraz dokumentacji.
- Implementacja toola zostaje po stronie warstwy exposure, obecnie
  `analysis.mcp.*`, dopoki nie przeniesiemy wrapperow w kolejnym kroku.

## Weryfikacja

- `PackageDependencyGuardTest` pilnuje, zeby `agenttools.*` nie importowalo
  warstw aplikacyjnych.
