# OpenAPI tools

Neutralne callbacki nad `integrations.openapi`; bez semantyki System Assistant
i zależności od Copilota. `create` zwraca zestaw jednej tury. MCP ma własny
dynamiczny katalog, odrębny od globalnego MCP TDW; nie rejestruj tych callbacków
jako globalnego ToolCallbackProvider. Nieustalony zapis blokuje kolejne
mutacje tej samej tury, bez samoczynnego retry.

Pliki odpowiedzi są efemeryczne. ToolContentCallback zwraca tekst i binaria:
PDF jako obrazy wszystkich stron przez wspólny common.documents.PdfPageRenderer,
DOCX jako tekst i osadzone obsługiwane obrazy przez DocxContentReader, obrazy
bezpośrednio. SDK i MCP mapują ten neutralny wynik na własny transport.
Nie zapisuj pobranych plików do rozmowy ani nie pobieraj automatycznie URL z JSON.
Tekst wyniku zachowuje status/outcome HTTP i nie zawiera base64 obrazów.

Katalog i factory przyjmują neutralne OpenApiExecution: operację wraz
z captured profilem Auth. Definicje SDK/MCP zawierają tylko opis i input
schema operacji, bez poświadczeń. AI nie wybiera profilu i nie podaje
Authorization. MCP korzysta z tego samego wykonawcy co SDK.
Factory przekazuje istniejący OpenApiExecution bezpośrednio do portu wykonania
wraz z OpenApiArguments; nie rozdziela ani nie wybiera ponownie Auth.

Katalog grupuje executions według neutralnego scopeId. Każdy scope ma
oddzielny endpoint `/api/openapi-tools/{scopeId}/mcp`, transport i sesje.
Nie ma nieskopowanego endpointu, domyślnego scope ani wyboru Auth przez AI.
Feature mapuje profile na scopes; neutralny katalog nie importuje feature'a.

Import JSON/YAML obsługuje OpenAPI 3.0/3.1 i kompozycję schematów.
Kontrakt źródłowy, walidacja requestu i schemat argumentów mają odrębne role.
Nie zgaduj discriminator mapping i nie usuwaj ograniczeń allOf/oneOf.
Kanoniczne limity oraz semantykę x-tdw-operation-effect opisuje runtime
System Assistanta; GET bez potwierdzenia skutków pozostaje UNKNOWN.
