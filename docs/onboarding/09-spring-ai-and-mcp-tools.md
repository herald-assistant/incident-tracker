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

## Najwazniejsze klasy

- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/elasticsearch/ElasticMcpTools.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/elasticsearch/ElasticMcpToolConfiguration.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/gitlab/GitLabMcpTools.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp/gitlab/GitLabMcpToolConfiguration.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/tools/CopilotSdkToolBridge.java`

## Co jest wazne architektonicznie

- tool nie powinien miec logiki calej analizy,
- tool deleguje do adaptera albo use case'u,
- warstwa MCP jest o ekspozycji capability, nie o integracji REST,
- lekkie heurystyki prezentacyjne dla Copilota, np. `inferredRole` albo
  `recommendedReadStrategy`, moga zyc w warstwie MCP, o ile nie staja sie
  centralnym rule engine,
- ten sam tool moze byc widoczny w kontekscie Spring AI i w sesji Copilota.

## Sprawdz lokalnie

- przeczytaj konfiguracje `MethodToolCallbackProvider`,
- zobacz, jak `CopilotSdkToolBridge` buduje `ToolDefinition`,
- uruchom testy z `src/test/java/pl/mkn/incidenttracker/analysis/mcp`.

## Checkpoint

- Dlaczego `analysis.mcp` nie powinno wykonywac `RestClient` bezposrednio?
- Po co jest osobna konfiguracja `ToolCallbackProvider`, skoro metody maja
  juz `@Tool`?
