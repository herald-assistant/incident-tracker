# OpenAPI integration

Root zawiera tylko porty importu, wyboru operacji i wykonania. Kontrakty w `contract`, parser
i walidacja w `service`, RestClient w `adapter.rest`. Brak zależności od
feature'ów, MCP i Copilota. Wspierany podzbiór jest jawny w runtime dokumentacji.
API HTTP(S) mogą wskazywać dowolny poprawny host (DNS, IP, localhost), bez
listy hostów ani trybu lokalnego. Base URL nie zawiera credentials, query
ani fragmentu. Nie dodawaj redirectów, retry aplikacji ani pobierania zewnętrznych refs.
Nie klasyfikuj skutków po samym HTTP. TLS wynika wyłącznie ze wspólnej
flagi integrations.http.ignore-ssl-errors i fabryki IntegrationRestClientBuilderFactory.
False weryfikuje cert i hostname, true pomija je lokalnie dla klienta.
Oba tryby używają JDK HttpClient; true ma lokalny X509ExtendedTrustManager.
Zachowaj PATCH, 3/8 s i brak redirectów; nie zmieniaj TLS całej JVM.
Wbudowany retry GET JDK po niektórych zerwaniach jest jawnie dopuszczony,
również dla GET UNKNOWN. Nie ponawiaj POST/PUT/PATCH/DELETE.
Wykonanie przyjmuje typowany profil integrations.httpauth poza argumentami
narzędzia. No Auth nie dodaje Authorization. Security metadata Basic/Bearer/
OAuth2/openIdConnect są walidowane i domykane w selekcji; nie wybierają
poświadczeń operatora. Błąd Auth przed requestem nie jest nieustalonym zapisem
i nie uprawnia do retry operacji biznesowej.
Port execute przyjmuje własne kontrakty OpenApiExecution i OpenApiArguments.
Captured binding operacja–Auth jest wspólny dla katalogu, callbacka i REST;
nie rozpakowuj HttpAuthProfile w sygnaturze root portu ani nie pobieraj
bieżącego profilu ponownie podczas wykonania.
OpenApiOperation rozdziela schema walidacyjny OpenAPI od projekcji argumentów
JSON Schema: readOnly jest pomijane w narzędziu i odrzucane w żądaniu,
nullable dopuszcza null zgodnie z required/enum, a format jest adnotacją.

Selekcja operacji jest bezstanowa: kataloguje większy dokument i przygotowuje
samodzielny YAML wybranych metod/ścieżek z zależnymi refs. Nie zapisuje źródła
ani nie publikuje tools; wynik waliduje istniejący importer wykonawczy.

Nazwy narzędzi generuje wyłącznie importer: do 64 znaków, z deterministycznym
skrótem SHA-256 dla dłuższej postaci oa_<apiId>_<operationId>. Pełny operationId
pozostaje w dokumencie i opisie; jego długość nie jest powodem odrzucenia.
Kolizje nazw blokują publikację. SDK/MCP reuse'ują wynik bez własnego skracania.

Projekcja document waliduje ten sam bieżący YAML i zwraca kopię JSON do
read-only podglądu. Nie zapisuje dokumentu i nie pobiera zewnętrznych refs.

Preview zwraca kopię JSON źródła do dokumentacji podczas wyboru. Ta projekcja
nie oznacza walidacji wykonawczej całego dużego kontraktu. Wybrane operacje
i ich domknięcie refs nadal weryfikuje select przed publikacją.

Import JSON/YAML obsługuje OpenAPI 3.0/3.1 i kompozycję schematów.
Kontrakt źródłowy, walidacja requestu i schemat argumentów mają odrębne role.
Nie zgaduj discriminator mapping i nie usuwaj ograniczeń allOf/oneOf.
Kanoniczne limity oraz semantykę x-tdw-operation-effect opisuje runtime
System Assistanta; GET bez potwierdzenia skutków pozostaje UNKNOWN.

PATCH obsługuje application/json lub brak body, z dokładną walidacją schema
i operation-level security. Nie zastępuj go PUT ani nie dopisuj całego DTO.

Opcjonalne parametry header (brak required lub false) zachowuj w dokumentacji,
ale pomijaj w schema narzędzia i liście wykonawczej. Nie generuj ani nie
wysyłaj ich wartości. Wymagane header i błędny typ required blokują import.
Auth operatora pozostaje niezależny. Merge parametrów jest wspólny dla
parsera i diagnostyki; header names są case-insensitive.

Query array przyjmuje jawne scalar items bez null, style form i explode
true/default lub false. Metadata Parameter przechowuje efektywne explode;
SDK/MCP przekazują JSON array do wspólnego wykonawcy. Koduj każdy element
osobno, zachowaj kolejność i ograniczenia schema; nie wklejaj argumentów
do URI template. Pusta tablica jest jawnym błędem przed HTTP, nie omission.
Nie rozszerzaj tego na obiekty query, array path ani zagnieżdżone tablice.

Response content obsługuje application/json i text/plain ze string schema.
Metadane media types są per status/default; Accept wynika z kontraktu.
Text/plain dekoduj wskazanym charsetem lub UTF-8 i zachowuj jako TextNode,
bez fallbacku błędnego JSON do tekstu. Limit odpowiedzi jest przed dekodowaniem.
Fixtures mają być małe i fikcyjne; graniczne rozmiary/grafy generuj w testach.
Nie kopiuj kontraktów operatora ani ich nazw domenowych do fixtures.

Pattern zachowuj bez przepisywania lub dopisywania kotwic. Import sprawdza
typ string i składnię tą samą fabryką regex networknt co runtime; argumenty
waliduje JSON Schema przed HTTP. Używany jest silnik JDK z adaptacją networknt,
nie pełny ECMAScript. patternProperties pozostaje poza podzbiorem.
