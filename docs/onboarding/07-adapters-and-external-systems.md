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

Daje logi po `correlationId`. Jest reuse'owany przez provider evidence, MCP
tool i shared/operator endpoint `api.elasticsearch.ElasticLogSearchController`.

Czytaj:

- `integrations.elasticsearch.ElasticLogPort`
- `integrations.elasticsearch.ElasticRestLogAdapter`
- `integrations.elasticsearch.ElasticLogSearchService`

## Dynatrace

Nie jest tools-first.
Sluzy do runtime enrichment na podstawie logow i deployment context.

Czytaj:

- `integrations.dynatrace.DynatraceIncidentPort`
- `integrations.dynatrace.DynatraceIncidentQuery`
- `integrations.dynatrace.DynatraceRestIncidentAdapter`

## GitLab

Ma najwiecej capability:

- adapter repozytorium i source resolve,
- shared/operator endpointy do testowania,
- reuse przez provider deterministic,
- reuse przez MCP tools.

Czytaj:

- `integrations.gitlab.GitLabRepositoryPort`
- `integrations.gitlab.GitLabRestRepositoryAdapter`
- `integrations.gitlab.source.GitLabSourceResolveService`
- `api.gitlab.GitLabRepositorySearchController`
- `api.gitlab.source.GitLabSourceResolveController`

## Database

Nie jest evidence providerem i nie ma helper endpointu operatorskiego.
To opcjonalna capability readonly dla AI-guided data diagnostics.

Czytaj:

- `integrations.database.DatabaseToolProperties`
- `integrations.database.DatabaseConnectionRouter`
- `integrations.database.DatabaseApplicationScopeResolver`
- `integrations.database.DatabaseMetadataClient`
- `integrations.database.DatabaseReadOnlyQueryClient`
- `integrations.database.DatabaseSqlGuard`
- `integrations.database.DatabaseToolService`

## Operational Context

Nie jest zrodlem incidentowych heurystyk.
To reuse'owalny adapter katalogu operacyjnego, ktory laduje zasoby z
`src/main/resources/operational-context` i potrafi zwracac je po dowolnych
filtrach query-based.

Czytaj:

- `integrations.operationalcontext.OperationalContextPort`
- `integrations.operationalcontext.OperationalContextAdapter`
- `integrations.operationalcontext.OperationalContextQuery`

## Wazne zasady

- adapter nie powinien znac promptu ani logiki incidentowej,
- jesli capability ma ogolne query/filter API, trzymaj je po stronie adaptera,
  a heurystyki konkretnego use case'u dopiero po stronie providera evidence albo toola,
- nietypowe HTTP, np. ignorowanie SSL, jest lokalne dla konkretnej integracji,
- nietypowe JDBC, routing po environment, allowlisty schematow i SQL guard
  musza pozostac lokalne dla Database capability,
- helper endpoint testuje capability adaptera, nie glowny flow produktu,
- stabilne endpointy FE/operatora trzymaj w `api.*`, a adapter, porty i modele
  request/result w `integrations.*`.

## Checkpoint

- Gdzie powinien trafic nowy model DTO odpowiedzi z GitLaba?
- Czy adapter Dynatrace powinien wystawiac `@Tool`?
- Dlaczego `GitLabSourceResolveService` nie jest w `features.incidentanalysis.flow`?
