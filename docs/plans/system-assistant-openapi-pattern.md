# Walidacja pattern w schematach OpenAPI

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Użytkownik zlecił obsługę pattern po audycie product.json. Dwie z 35 operacji
blokuje obecna allowlista keywordów. Należy zachować ograniczenie i wykonać
walidację przed HTTP; nie kopiować danych operatora do fixtures. Zmiana L2.

## Baseline i conformance delta

OpenApiSchema odrzuca pattern jako nieobsługiwane pole. Projekcja zachowuje
inne ograniczenia, networknt 2.0.0 waliduje request przed HTTP w jednym
wykonawcy SDK/MCP, odpowiedzi pozostają dokumentacją schema graphu.
Audyt product.json: 35 w preview, 33 importowalne łącznie, 2 blokowane.
Baseline: OpenApiSchemaTest i OpenApiIntegrationTest
(target/openapi-pattern-baseline.log). Znany failure IntegrationPackageBoundaryTest
dotyczący HttpAuthProfile pozostaje niezależny od tej zmiany.

Delta: pattern jest dozwolonym keywordem, wymaga stringa i poprawnej składni
w silniku networknt używanym przez runtime. Import i walidacja używają tej
samej fabryki JDKRegularExpressionFactory, jawnie wskazanej w konfiguracji.
To obsługiwany silnik JDK z adaptacją networknt, nie pełny silnik ECMAScript.
Nie przepisujemy regexu i nie dopisujemy kotwic. Reguła dotyczy stringów;
nullable/null i inne typy zachowują semantykę JSON Schema. Pattern pozostaje
w źródle, wyborze, podglądzie, eksporcie oraz model-facing schema SDK/MCP.
Błędny typ/składnia jest odrzucana z lokalizacją /pattern przed publikacją.

Ownership: integrations.openapi.service. Konsumenci: importer/response graph,
preview/select/document, konfiguracja V1, schema SDK/MCP, wspólny REST.
Bez zmian DTO, modeli, Auth, limitów, skilli, sesji, historii, FE i zależności.
Usuwamy test odrzucający poprawny pattern, zastępując go nadal nieobsługiwanym
keywordem; nie ma aliasów, migracji ani starszych wersji. Graph i storage V1
nie zmieniają formatu. Zastane zmiany użytkownika pozostają nietknięte.

## Proponowane rozwiązanie

Reuse kompilatora regularExpressionFactory i walidatora networknt, bez drugiej
implementacji dopasowania. Semantyka substring/anchors jest zgodna z
[JSON Schema pattern](https://json-schema.org/draft/2020-12/json-schema-validation#name-pattern)
w obsługiwanej składni. Mała fikcyjna fixture z wzorcami kodów elementów
pokrywa refs/allOf/zagnieżdżone tablice, path/query oraz projection SDK/MCP.

## Zakres

Pattern w obsługiwanych schematach OpenAPI 3.0/3.1, kontrola string/składni,
walidacja danych przed HTTP, zachowanie źródła i projekcji, audyt product.json.

## Non-goals

patternProperties, pełny ECMAScript lub nowy silnik regex, zmiana źródłowych
wzorców, usuwanie ograniczeń, regex timeout/nowa polityka budżetów, multipart,
niezależny błąd guarda, kopiowanie kontraktu do testów i wywoływanie API operatora.

## Ograniczenia i ryzyka

Składnia jest ograniczona przez istniejący silnik JDK networknt; pozostałe
keywordy i limity pozostają. Złożoność dopasowania zależy od utrzymywanego
przez operatora regexu; nie deklarujemy nowej izolacji albo timeoutu.
Pattern bez kotwic dopasowuje fragment tekstu, a pusty pattern jest poprawny.
Odpowiedzi nie zyskują walidacji wartości, tak jak dla pozostałych keywordów.

## Kryteria akceptacji

Product.json przechodzi import wszystkich 35 operacji razem, z pomiarem
rozmiarów schematów. Fikcyjne testy potwierdzają preservation, składnię,
substring/anchors, escaping, nullable, refs/allOf/nested i brak HTTP przy
błędnym argumencie; SDK/MCP mają oryginalny pattern. Pełna regresja backendu
i guards są uruchomione, a zastane failures raportowane oddzielnie.

## Kroki

- [x] Krok 1: baseline, allowlista i walidacja typu/składni tym samym silnikiem;
  małe fikcyjne testy zachowania i błędów importu/walidacji.
- [x] Krok 2: SDK/MCP i realny HTTP, aktualizacja nieaktualnego testu;
  pełna regresja backendu i guards bez nowych failures.
- [x] Krok 3: audyt całego product.json bez HTTP, dokumentacja i diff review.

## Dowody weryfikacji

- Baseline: 7 testów przeszło (target/openapi-pattern-baseline.log).
- OpenAPI/System Assistant + PackageDependencyGuardTest: 84 testy,
  83 przeszło, 1 opt-in live Copilot pominięty, 0 failures/errors
  (target/openapi-pattern-targeted.log). Testy używają wyłącznie fikcyjnych
  kodów elementów, bez wzorców lub modeli skopiowanych z product.json.
- Semantyka: bez kotwic dopasowuje się fragment, kotwice i literalna kropka
  zachowują znaczenie, pusty regex jest poprawny, null i inne typy nie są
  sprawdzane przez sam pattern. Import odrzuca nie-string i błędną składnię.
  Ref/allOf/nested array, path/query i query array odrzucają niezgodne wartości
  przed HTTP; SDK i realny MCP zachowują pattern w schema.
- Audyt product.json: direct import i selekcja wszystkich operacji przechodzą
  dla 35/35, zachowując 361 schematów. Przygotowany YAML: 298167 znaków;
  suma schema argumentów: 341213, największy schema: 93057. Mieszczą się
  w obecnych limitach. Raporty target/product-document-audit.json oraz
  target/product-operations-audit.json. Nie wywoływano API operatora.
- Dokumentacja runtime, obsługi, authoring i lokalne AGENTS są aktualne;
  brak zmian DTO/FE, nowych zależności lub migracji. Końcowy diff --check
  bez błędów; fixtures nadal są fikcyjne i bez nazw domenowych operatora.
- Pełna regresja backendu: 2322 testy, 2315 przeszło, 6 pominiętych,
  1 zastany failure IntegrationPackageBoundaryTest dla importu HttpAuthProfile
  w OpenApiExecutionPort, 0 errors (target/openapi-pattern-full-test.log).
  Wszystkie testy OpenAPI/System Assistant i PackageDependencyGuardTest
  przeszły. Brak nowych failures; live Copilot nie był uruchamiany.

Plan pozostaje do przeglądu zakresu i dowodów wykonania. Port i properties
zmienione wcześniej poza zakresem pozostają nietknięte.
