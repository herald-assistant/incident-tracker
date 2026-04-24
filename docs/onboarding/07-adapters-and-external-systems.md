# Krok 7: Adaptery I Systemy Zewnetrzne

## Cel

Zrozumiec, jak projekt integruje sie z Elasticsearch, Dynatrace, GitLabem,
Database capability oraz curated operational context bez rozlewania szczegolow
REST, JDBC albo query/filter API po calej aplikacji.

## Po tym kroku rozumiesz

- po co sa porty i adaptery w tym repo,
- gdzie leza helper endpointy testowe,
- jakie zachowania konfiguracyjne sa lokalne dla konkretnej integracji,
- dlaczego operational context catalog ma osobny adapter zamiast ladowania go
  bezposrednio w providerze evidence.

## Elasticsearch

Daje logi po `correlationId` i helper endpoint do recznego searchu.
Jest tez reuse'owany przez provider evidence i MCP tool.

Czytaj:

- `analysis.adapter.elasticsearch.ElasticLogPort`
- `analysis.adapter.elasticsearch.ElasticRestLogAdapter`
- `analysis.adapter.elasticsearch.ElasticLogSearchService`

## Dynatrace

Nie jest tools-first.
Sluzy do runtime enrichment na podstawie logow i deployment context.

Czytaj:

- `analysis.adapter.dynatrace.DynatraceIncidentPort`
- `analysis.adapter.dynatrace.DynatraceIncidentQuery`
- `analysis.adapter.dynatrace.DynatraceRestIncidentAdapter`

## GitLab

Ma najwiecej capability:

- adapter repozytorium i source resolve,
- helper endpointy do testowania,
- reuse przez provider deterministic,
- reuse przez MCP tools.

Czytaj:

- `analysis.adapter.gitlab.GitLabRepositoryPort`
- `analysis.adapter.gitlab.GitLabRestRepositoryAdapter`
- `analysis.adapter.gitlab.source.GitLabSourceResolveService`

## Database

Nie jest evidence providerem i nie ma helper endpointu operatorskiego.
To opcjonalna capability readonly dla AI-guided data diagnostics.

Czytaj:

- `analysis.adapter.database.DatabaseToolProperties`
- `analysis.adapter.database.DatabaseConnectionRouter`
- `analysis.adapter.database.DatabaseApplicationScopeResolver`
- `analysis.adapter.database.DatabaseMetadataClient`
- `analysis.adapter.database.DatabaseReadOnlyQueryClient`
- `analysis.adapter.database.DatabaseSqlGuard`
- `analysis.adapter.database.DatabaseToolService`

## Operational Context

Nie jest zrodlem incidentowych heurystyk.
To reuse'owalny adapter katalogu operacyjnego, ktory laduje zasoby z
`src/main/resources/operational-context` i potrafi zwracac je po dowolnych
filtrach query-based.

Czytaj:

- `analysis.adapter.operationalcontext.OperationalContextPort`
- `analysis.adapter.operationalcontext.OperationalContextAdapter`
- `analysis.adapter.operationalcontext.OperationalContextQuery`

## Wazne zasady

- adapter nie powinien znac promptu ani logiki incidentowej,
- jesli capability ma ogolne query/filter API, trzymaj je po stronie adaptera,
  a heurystyki konkretnego use case'u dopiero po stronie providera evidence albo toola,
- nietypowe HTTP, np. ignorowanie SSL, jest lokalne dla konkretnej integracji,
- nietypowe JDBC, routing po environment, allowlisty schematow i SQL guard
  musza pozostac lokalne dla Database capability,
- helper endpoint testuje capability adaptera, nie glowny flow produktu.

## Checkpoint

- Gdzie powinien trafic nowy model DTO odpowiedzi z GitLaba?
- Czy adapter Dynatrace powinien wystawiac `@Tool`?
- Dlaczego `GitLabSourceResolveService` nie jest w `analysis.flow`?
