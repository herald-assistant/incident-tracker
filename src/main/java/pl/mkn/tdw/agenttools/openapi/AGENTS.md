# OpenAPI tools

Neutralne callbacki nad `integrations.openapi`; bez semantyki System Assistant
i zależności od Copilota. `create` zwraca zestaw jednej tury. MCP ma własny
dynamiczny katalog, odrębny od globalnego MCP TDW; nie rejestruj tych callbacków
jako globalnego ToolCallbackProvider. Nieustalony zapis blokuje kolejne
mutacje tej samej tury, bez samoczynnego retry.
