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

PATCH obsługuje JSON, body plikowe lub brak body, z dokładną walidacją schema
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
Query object obsługuje form i explode true/default: płaska mapa skalarnych
wartości staje się parametrami URL. Waliduj schema mapy, koduj także klucze,
odrzucaj null, zagnieżdżenia, arrays w mapie oraz kolizje nazw po rozwinięciu.
Array path i zagnieżdżone tablice pozostają poza zakresem.

Parametr path string może mieć jawne x-tdw-catch-all: true tylko na ostatnim,
pojedynczym pełnym segmencie wzorca. Wartość to względna ścieżka bez
początkowego /, URL, %, query/hash, backslash, znaków sterujących i segmentów
. lub ... Każdy segment koduj osobno; nie zmieniaj hosta ani base path.
Zwykły parametr bez tej flagi nadal przyjmuje tylko pojedynczy segment.
Metadata wykonawcze pochodzą z importera i są wspólne dla SDK/MCP.
DELETE może mieć body tylko w OpenAPI 3.1. GET pozostaje bez body.

Importer określa pozycje plikowe i kodowanie multipart lub surowego body. REST sprawdza wszystkie części i MIME przed HTTP oraz wysyła oryginalne bajty z OpenApiArguments. Odczyt z dysku i wybór pliku rozmowy należą do wywołującego. Szczegóły obsługiwanego podzbioru zawiera runtime System Assistanta.

Response content obsługuje application/json, text/plain ze string schema, */*
oraz image/png, image/jpeg, image/webp, image/gif, image/bmp, application/pdf, DOCX MIME i application/octet-stream
ze string/binary schema. Wspierany plik według rzeczywistego Content-Type
jest typowanym OpenApiFile w pamięci; JSON body zawiera tylko metadane.
Bez zależności od renderowania, SDK, MCP i storage rozmowy.
Metadane media types są per status/default; Accept wynika z kontraktu.
Jawne application/octet-stream ze string/binary schema rozpoznaje wspierany
format z bajtów po HTTP 2xx z wyłączeniem 204/205, gdy odpowiedź jest binarna
lub nie ma Content-Type. DOCX wymaga wpisu word/document.xml w ZIP.
Konkretny binarny MIME musi zgadzać się z wykrytym formatem; brak rozpoznania
lub niezgodność zachowuje status/outcome HTTP i daje fileDelivery FAILED,
bez pliku, base64 ani retry. Nie rozpoznawaj po nazwie lub rozszerzeniu.
Nie zmieniaj dekodowania JSON/text, błędów HTTP ani samego wildcardu.
Text/plain dekoduj wskazanym charsetem lub UTF-8 i zachowuj jako TextNode,
bez fallbacku błędnego JSON do tekstu. Jawny wildcard */* dekoduje application/json
i application/*+json jako JSON, text/* według charset/UTF-8, pozostałe jako
obiekt {encoding: base64, contentType, data} dla pozostałych binariów.
W samym wildcardzie brak Content-Type oznacza dane binarne; błędny zadeklarowany
JSON nadal ma nieustalony wynik dla mutacji.
Odczytuj pełne body bez aplikacyjnego
limitu rozmiaru odpowiedzi; timeouty transportu pozostają.
Fixtures mają być małe i fikcyjne; graniczne rozmiary/grafy generuj w testach.
Nie kopiuj kontraktów operatora ani ich nazw domenowych do fixtures.

Pattern zachowuj bez przepisywania lub dopisywania kotwic. Import sprawdza
typ string i składnię tą samą fabryką regex networknt co runtime; argumenty
waliduje JSON Schema przed HTTP. Używany jest silnik JDK z adaptacją networknt,
nie pełny ECMAScript. patternProperties pozostaje poza podzbiorem.
