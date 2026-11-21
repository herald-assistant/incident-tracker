# OpenAPI integration

Root zawiera tylko porty importu, wyboru operacji i wykonania. Kontrakty w `contract`, parser
i walidacja w `service`, RestClient w `adapter.rest`. Brak zależności od
feature'ów, MCP i Copilota. Wspierany podzbiór jest jawny w runtime dokumentacji.
API HTTP(S) mogą wskazywać dowolny poprawny host (DNS, IP, localhost), bez
listy hostów ani trybu lokalnego. Base URL nie zawiera credentials, query
ani fragmentu. Nie dodawaj redirectów, retry ani pobierania zewnętrznych refs.
Nie klasyfikuj skutków po samym HTTP. Nie wyłączaj walidacji TLS.
OpenApiOperation rozdziela schema walidacyjny OpenAPI od projekcji argumentów
JSON Schema: readOnly jest pomijane w narzędziu i odrzucane w żądaniu,
nullable dopuszcza null zgodnie z required/enum, a format jest adnotacją.

Selekcja operacji jest bezstanowa: kataloguje większy dokument i przygotowuje
samodzielny YAML wybranych metod/ścieżek z zależnymi refs. Nie zapisuje źródła
ani nie publikuje tools; wynik waliduje istniejący importer wykonawczy.

Projekcja document waliduje ten sam bieżący YAML i zwraca kopię JSON do
read-only podglądu. Nie zapisuje dokumentu i nie pobiera zewnętrznych refs.

Preview zwraca kopię JSON źródła do dokumentacji podczas wyboru. Ta projekcja
nie oznacza walidacji wykonawczej całego dużego kontraktu. Wybrane operacje
i ich domknięcie refs nadal weryfikuje select przed publikacją.
