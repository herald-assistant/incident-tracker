# Krok 9: Spring AI, Tools I MCP

## Cel

Zrozumiec, jak projekt uzywa Spring AI do wystawiania tools i jak te same tools
sa potem reuse'owane przez Copilot SDK.

## Po tym kroku rozumiesz

- czym jest `@Tool`,
- po co jest `ToolCallbackProvider`,
- dlaczego `analysis.mcp` jest osobna warstwa,
- jak Spring tool trafia do sesji Copilota.

## Model w tym projekcie

1. Klasa tools ma metody oznaczone `@Tool`.
2. Konfiguracja tworzy `ToolCallbackProvider`.
3. Provider AI zbiera callbacki ze Springa.
4. Bridge mapuje je na `ToolDefinition` Copilota.
5. Sesja Copilota moze je wywolac jako narzedzia runtime.

Aktualny zestaw GitLab MCP obejmuje:

- broad search kandydatow repozytoriow i plikow,
- wysokopoziomowe `find_flow_context`,
- outline pliku przed pelnym readem,
- focused single chunk read,
- male batch'e powiazanych chunkow,
- pelny read pliku, gdy jest krotki albo potrzebny w calosci.

Aktualny zestaw Database MCP obejmuje:

- session-bound `db_get_scope`,
- application-scoped discovery przez `db_find_tables` i `db_find_columns`,
- exact `schema.table` data checks typu `db_exists_by_key`, `db_count_rows`,
  `db_group_count`, `db_sample_rows`,
- relationship diagnostics typu `db_check_orphans`, `db_find_relationships`,
  `db_join_count`, `db_join_sample`,
- opcjonalne `db_compare_table_to_expected_mapping`,
- last-resort `db_execute_readonly_sql`, gdy backend jawnie wlaczy raw SQL.

## Najwazniejsze klasy

- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/elasticsearch/ElasticMcpTools.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/elasticsearch/ElasticMcpToolConfiguration.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/gitlab/GitLabMcpTools.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/gitlab/GitLabMcpToolConfiguration.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/database/DatabaseMcpTools.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/database/DatabaseMcpToolConfiguration.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/tools/CopilotSdkToolBridge.java`

## Co jest wazne architektonicznie

- tool nie powinien miec logiki calej analizy,
- tool deleguje do adaptera albo use case'u,
- warstwa MCP jest o ekspozycji capability, nie o integracji REST,
- lekkie heurystyki prezentacyjne dla Copilota, np. `inferredRole` albo
  `recommendedReadStrategy`, moga zyc w warstwie MCP, o ile nie staja sie
  centralnym rule engine,
- session-bound context, np. `environment`, `correlationId`, `gitLabGroup` albo
  `gitLabBranch`, powinien trafic do toola przez `ToolContext`, a nie przez
  model-facing parametry,
- ten sam tool moze byc widoczny w kontekscie Spring AI i w sesji Copilota,
- sama rejestracja Spring toola nie oznacza jeszcze, ze trafi on do konkretnej
  sesji Copilota:
  `CopilotSdkPreparationService` moze go odfiltrowac, jesli artefakty juz
  dostarczyly odpowiadajace mu dane,
- sesja Copilota dostaje tez allowliste `availableTools`, zeby zablokowac
  lokalne workspace/filesystem/shell tools i zostawic tylko jawnie dopuszczone
  MCP capability.

## Sprawdz lokalnie

- przeczytaj konfiguracje `MethodToolCallbackProvider`,
- zobacz, jak `CopilotSdkToolBridge` buduje `ToolDefinition`,
- uruchom testy z `src/test/java/pl/mkn/incidenttracker/analysis/mcp`.

## Checkpoint

- Dlaczego `analysis.mcp` nie powinno wykonywac `RestClient` bezposrednio?
- Po co jest osobna konfiguracja `ToolCallbackProvider`, skoro metody maja
  juz `@Tool`?
