# AGENTS

## Zakres

Ten katalog jest docelowa warstwa reusable integracji z systemami zewnetrznymi.
Pakiety pod `integrations.*` sa czystymi capability adapterami, ktore moga byc
uzywane przez evidence providers, tools/MCP, helper endpointy REST albo przyszle
feature'y.

Obecnie obejmuje m.in.:

- `dynatrace/`
- `elasticsearch/`
- `gitlab/`
- `operationalcontext/`

Operational context jest tutaj query-based capability katalogu operacyjnego.
Incident-specific matching i mapowanie na evidence pozostaja w
`analysis.evidence.provider.operationalcontext`.

## Zasady

- Nie importuj tutaj `analysis.*`, `agenttools.*`, `features.*` ani
  `aiplatform.*`.
- Trzymaj lokalnie properties, porty, modele request/result i adaptery REST dla
  danej capability.
- Cienkie helper endpointy REST do manualnego testowania capability sa
  dopuszczalne, jesli deleguja do integracji i nie importuja `analysis.*`.
- Nietypowe zachowania HTTP izoluj lokalnie dla danej integracji.
- Nie dodawaj tu `AnalysisEvidenceProvider`, klas `@Tool`, promptow, skilli ani
  heurystyk incidentowych.
- Jesli capability potrzebuje neutralnego kontraktu wspolnego z innym
  feature'em, preferuj maly typ w `shared` albo `common`, ale dopiero gdy realnie
  usuwa zaleznosc.

## Weryfikacja

- `PackageDependencyGuardTest` pilnuje, zeby `integrations.*` nie zaczelo
  importowac warstw aplikacyjnych.
- Dla adapterow REST preferuj testy z `MockRestServiceServer`.
