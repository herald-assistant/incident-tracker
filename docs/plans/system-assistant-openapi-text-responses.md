# Odpowiedzi tekstowe i minimalne fikcyjne fixtures OpenAPI

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Użytkownik zatwierdził obsługę text/plain jako string body wyniku SDK/MCP
oraz zażądał anonimizacji i uproszczenia testów. Dwa endpointy źródła mają
taką odpowiedź; nie wymagają nowego argumentu ani formatu wyniku. Poziom L2.

## Baseline i conformance delta

Importer dopuszcza JSON odpowiedzi, REST zawsze wysyła Accept application/json
i parsuje bytes jako JSON; nie-JSON powoduje nieustalony wynik. Wynik ma
JsonNode body. Wspólny callback zasila SDK/MCP oraz evidence. Aktualny audyt
to 214/224 importowalne pojedynczo. Baseline: testy OpenAPI/System Assistant
i guards (target/openapi-text-plain-baseline.log); znany failure guarda
HttpAuthProfile w OpenApiExecutionPort nie należy do tej zmiany.

Delta: importer waliduje application/json i text/plain (tekst wymaga string
schema). Neutralny model zachowuje media types per response status/default.
Accept jest sumą deklaracji, a dla braku content pozostaje application/json.
Rzeczywisty text/plain jest dekodowany charsetem odpowiedzi (domyślnie UTF-8)
i zwracany jako TextNode, wyłącznie dla odpowiedniej deklaracji status/default.
JSON nadal wymaga poprawnego JSON; brak Content-Type zachowuje dotychczasową
obsługę JSON. Nie ma fallbacku błędnego JSON do tekstu. Nieobsługiwany typ,
wadliwy charset lub kodowanie zwraca dotychczasowy nieustalony wynik.
204/205 i odpowiedź bez treści zachowują null; pusty text/plain z content
daje pusty string. Limit 64 KiB, no retry, Auth i write guard pozostają.

Konsumenci: integrations.openapi importer/model/REST, select/document,
konfiguracja V1, callbacki SDK, MCP i ich testy; bez zmian publicznych DTO,
FE, skilli, sesji, historii lub formatu V1. Brak nowych zależności. Model
operacji nie jest zapisany na dysku; aktualizujemy wszystkie konstruktory,
bez aliasów, starszych formatów lub migracji.

Fixture 131 KB z 244 schematami źródłowymi zostaje usunięta. Jedna mała
fikcyjna fixture kompozycji zastępuje oba poprzednie przykłady: dwa warianty,
wspólna baza, typed map, readOnly, uniqueItems, nullable i rekurencyjna odpowiedź.
Duży rozmiar sprawdza generowany padding, a złożoność refs generowany graf.
Nie zachowujemy skopiowanych ścieżek, organizacji, enumów i nazw domenowych.

## Proponowane rozwiązanie

Reuse JsonNode/TextNode i wspólnego wykonawcy; parser requestu pozostaje JSON.
Rozdzielenie parsera response content od request body zapobiega rozszerzeniu
zapisów. Testy API używają tylko loopback i fikcyjnych danych. Audyt pliku
operatora pozostaje lokalnym odczytem poza fixtures, bez wywoływania API.

## Zakres

text/plain response, deklaracje status/default i Accept, charset, limity,
SDK/MCP, testy kompozycji i usunięcie skopiowanej fixture, audyt i dokumentacja.

## Non-goals

text/plain request body, multipart, HTML/binary, nowe DTO wyniku, live Copilot,
zmiana Auth i niezależnego błędu architektonicznego, refaktor całego test suite.

## Ograniczenia i ryzyka

Tekst nie potwierdza skutków biznesowych; nie zmieniamy UNKNOWN/READ_ONLY.
Pomylenie JSON i tekstu mogłoby zmienić typ wartości, dlatego decyzja opiera
się na Content-Type, a nie wyglądzie body. Testy graniczne są syntetyczne,
nie uzależniają liczby wariantów i schematów od kontraktu operatora.

## Kryteria akceptacji

216/224 operacje przechodzą import pojedynczo. Lokalny HTTP i MCP pokazują
string body, Accept, UTF-8 oraz jawny charset. Zły JSON, niezadeklarowany
text/plain, inne media, niepoprawne kodowanie i limit pozostają błędem.
Testy zachowują coverage kompozycji, odmowy przed HTTP, refs, cache/projekcji,
rozmiaru V1, SDK i guardów; fixtures nie zawierają danych skopiowanych z API.
Pełna regresja jest uruchomiona i zastane failures są opisane oddzielnie.

## Kroki

- [x] Krok 1: baseline, metadata i parser odpowiedzi, wykonawca text/plain;
  fikcyjne testy importu, HTTP, SDK/MCP, charset i błędów oraz limitu.
- [x] Krok 2: usunięcie skopiowanego kontraktu, jedna minimalna fixture,
  generowane dane graniczne, przegląd anonimizacji i regresja zachowanego sensu.
- [x] Krok 3: pełne testy backendu, guards, audyt, dokumentacja i diff review.

## Dowody weryfikacji

- Baseline: 92 testy, 1 znany failure IntegrationPackageBoundaryTest,
  1 pominięty live Copilot (target/openapi-text-plain-baseline.log).
- Regresja OpenAPI/System Assistant + PackageDependencyGuardTest:
  79 testów, 78 przeszło, 1 live Copilot pominięty, 0 failures/errors
  (target/openapi-text-plain-targeted.log).
- Usunięto fixtures product-composition-api.json (131426 B) i
  product-variants-api.yaml (2503 B); zastępuje je composition-api.yaml
  (2390 B, 4 schematy, dwa warianty i dwa endpointy). Model, ścieżki,
  enumy i nazwy pól są fikcyjne. Padding i graph refs powstają w testach.
  Nie usunięto testowanych zachowań ani walidacji przed HTTP.
- Przegląd testów i fixtures OpenAPI/System Assistant: brak nazw organizacji,
  ścieżek /clp, oryginalnych modeli i parametrów domenowych źródła;
  adresy fixtures są loopback, przykładowy email jest w example.com.
- Czyste target/test-classes/openapi nie zawiera obu usuniętych fixtures.
  Pełny clean test: 2317 testów, 2310 przeszło, 6 pominiętych,
  1 znany failure IntegrationPackageBoundaryTest (import HttpAuthProfile),
  0 errors (target/openapi-text-plain-clean-full-test.log).
- Dodatkowy negatywny przypadek wykrył MissingNode dla JSON zawierającego
  wyłącznie whitespace. Wykonawca odrzuca ten wynik tak samo jak błędny JSON;
  końcowa pełna regresja po poprawce: 2317 testów, 2310 przeszło,
  6 pominiętych, 1 znany failure IntegrationPackageBoundaryTest, 0 errors
  (target/openapi-text-plain-full-test.log). OpenApiTextResponseTest,
  wszystkie testy OpenAPI/System Assistant oraz PackageDependencyGuardTest
  przeszły. Brak nowych failures. Źródła i docs przeszły diff --check.
- Audyt backend.json: 216/224 importowalne pojedynczo, pozostałe 8 to
  multipart request bodies (target/backend-openapi-audit.json i .txt).
  Odczyt źródła do audytu nie dodaje go do fixtures; nie wywoływano API.
- Dokumentacja runtime/obsługi/authoring i lokalne zasady integracji opisują
  text/plain oraz fikcyjne fixtures. Zaktualizowano oba testowe konstruktory
  neutralnego modelu; brak nowych importów między warstwami i HTTP DTO.

Plan pozostaje do przeglądu zakresu i dowodów wykonania. Znany niezależny
failure guarda pozostaje identyczny z baseline; zmiany w OpenApiExecutionPort
i application.properties poza zakresem pozostawiono nietknięte.
