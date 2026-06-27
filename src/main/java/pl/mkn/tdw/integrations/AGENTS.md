# AGENTS

## Zakres

Ten katalog jest docelowa warstwa reusable integracji z systemami zewnetrznymi.
Pakiety pod `integrations.*` sa czystymi capability adapterami, ktore moga byc
uzywane przez evidence providers, tools/MCP, shared/operator endpointy REST
albo przyszle feature'y.

Obecnie obejmuje m.in.:

- `dynatrace/`
- `elasticsearch/`
- `gitlab/`
- `operationalcontext/`
- `database/`

Operational context jest tutaj query-based capability katalogu operacyjnego.
Incident-specific matching i mapowanie na evidence pozostaja w
`features.incidentanalysis.evidence.provider.operationalcontext`.
W modelu operational context `system` jest kanonicznym bytem dla aplikacji lub
uslugi. Nazwy deploymentu, kontenera, aplikacji i serwisu sa sygnalami albo
metadata systemu; nie dodawaj osobnych kontraktow referencyjnych
dla komponentu uruchomieniowego.

Database jest tutaj readonly capability diagnostyki danych: routing DataSource
per environment, metadata, SQL guard, masking/limiting wynikow oraz typed
request/result/scope/operator contracts. MCP mapuje hidden `ToolContext` na ten
scope, ale `integrations.database` nie importuje MCP ani `agenttools`.

## Zasady

- Nie importuj tutaj `analysis.*`, `agenttools.*`, `features.*`, `api.*` ani
  `aiplatform.*`.
- Trzymaj lokalnie properties, porty, modele request/result i adaptery REST dla
  danej capability.
- Stabilne endpointy FE/operatora trzymaj w `api.*`. Tutaj zostaw adapter,
  porty, modele request/result i service capability.
- Nietypowe zachowania HTTP izoluj lokalnie dla danej integracji.
- Nie dodawaj tu `AnalysisEvidenceProvider`, klas `@Tool`, promptow, skilli ani
  heurystyk incidentowych.
- Dla Database capability nie wprowadzaj globalnego `spring.datasource`, nie
  zgaduj schematow domenowo w kodzie i trzymaj mapping application-to-schema w
  konfiguracji.
- Dla Operational Context nie rozbijaj relacji katalogowych na system i osobny
  byt runtime. Repozytoria, procesy, bounded contexts, integracje i code-search
  scopes powinny odnosic sie do `systems`.
- Jesli capability potrzebuje neutralnego kontraktu wspolnego z innym
  feature'em, preferuj maly typ w `shared` albo `common`, ale dopiero gdy realnie
  usuwa zaleznosc.

## Weryfikacja

- `PackageDependencyGuardTest` pilnuje, zeby `integrations.*` nie zaczelo
  importowac warstw aplikacyjnych.
- Dla adapterow REST preferuj testy z `MockRestServiceServer`.
