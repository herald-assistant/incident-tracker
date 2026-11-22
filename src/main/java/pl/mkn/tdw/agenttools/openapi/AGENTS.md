# OpenAPI tools

Neutralne callbacki nad `integrations.openapi`; bez semantyki System Assistant
i zależności od Copilota. `create` zwraca zestaw jednej tury. MCP ma własny
dynamiczny katalog, odrębny od globalnego MCP TDW; nie rejestruj tych callbacków
jako globalnego ToolCallbackProvider. Nieustalony zapis blokuje kolejne
mutacje tej samej tury, bez samoczynnego retry.

Katalog i factory przyjmują neutralne OpenApiExecution: operację wraz
z captured profilem Auth. Definicje SDK/MCP zawierają tylko opis i input
schema operacji, bez poświadczeń. AI nie wybiera profilu i nie podaje
Authorization. MCP korzysta z tego samego wykonawcy co SDK.

Katalog grupuje executions według neutralnego scopeId. Każdy scope ma
oddzielny endpoint `/api/openapi-tools/{scopeId}/mcp`, transport i sesje.
Nie ma nieskopowanego endpointu, domyślnego scope ani wyboru Auth przez AI.
Feature mapuje profile na scopes; neutralny katalog nie importuje feature'a.
