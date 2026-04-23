# Krok 7: Adaptery I Systemy Zewnetrzne

## Cel

Zrozumiec, jak projekt integruje sie z Elasticsearch, Dynatrace, GitLabem i
Database capability bez rozlewania szczegolow REST albo JDBC po calej
aplikacji.

## Po tym kroku rozumiesz

- po co sa porty i adaptery w tym repo,
- gdzie leza helper endpointy testowe,
- jakie zachowania konfiguracyjne sa lokalne dla konkretnej integracji.

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

## Wazne zasady

- adapter nie powinien znac promptu ani logiki incidentowej,
- nietypowe HTTP, np. ignorowanie SSL, jest lokalne dla konkretnej integracji,
- nietypowe JDBC, routing po environment, allowlisty schematow i SQL guard
  musza pozostac lokalne dla Database capability,
- helper endpoint testuje capability adaptera, nie glowny flow produktu.

## Checkpoint

- Gdzie powinien trafic nowy model DTO odpowiedzi z GitLaba?
- Czy adapter Dynatrace powinien wystawiac `@Tool`?
- Dlaczego `GitLabSourceResolveService` nie jest w `analysis.flow`?
