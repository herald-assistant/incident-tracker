# Stabilne skracanie nazw narzędzi OpenAPI

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Długa nazwa narzędzia blokuje cztery operacje backend.json. Użytkownik
zatwierdził automatyczne skracanie ze stabilnym hashem i zachowaniem pełnego
operationId ("ok go"). Zmiana L2 dotyczy neutralnego importera SDK/MCP.

## Baseline i conformance delta

Importer i preview odrzucają nazwę oa_<apiId>_<operationId> ponad 64 znaki;
operationId ma osobny limit 64 znaków. SDK callbacks, MCP, allowlista i tool
evidence używają OpenApiOperation.name; konfiguracja i dokumentacja przechowują
oryginalny YAML. Globalny zapis konfiguracji odrzuca powtórzone nazwy.
Audyt: 194/224 operacje importowalne pojedynczo. Baseline: testy
OpenApiSelectionTest i OpenApiIntegrationTest.

Delta: nazwy do 64 znaków zachowują pełną postać; dłuższe mają 47 znaków
początku, underscore oraz 16 cyfr hex SHA-256 pełnej nazwy UTF-8. Pełny
operationId pozostaje w YAML, preview, eksporcie i opisie narzędzia. Zniesiony
jest osobny limit długości operationId; składnia liter/cyfr/underscore i
unikalność pozostają. Kolizja wynikowych nazw blokuje import zamiast
nadpisywania albo zależnego od kolejności numerowania.

Ownership: integrations.openapi.service. Konsumenci: preview/selection/document,
konfiguracja V1, callbacki SDK, katalog MCP, allowlista i evidence. Bez zmian
HTTP DTO, schematów argumentów, Auth, sesji, skilli, joba, historii i FE.
Brak nowych zależności lub kierunków importów; dotychczasowy limit długości
usuwa się z obu walidatorów. Brak aliasów, migracji i starszych formatów.
Zapis nie przechowuje wygenerowanych nazw; nie ma danych do migracji.

## Proponowane rozwiązanie

Jeden helper importera generuje deterministyczne nazwy i wspólnie sprawdza
składnię operationId. Callbacki i MCP reuse'ują wynik. Alternatywa 128 znaków
nie zapewnia zgodności downstream; ręczne skracanie zmieniałoby źródłowy kontrakt.

## Zakres

Skracanie, kontrola kolizji, zachowanie pełnego identyfikatora, testy graniczne
i transportowe, dokumentacja oraz audyt pliku użytkownika bez wywoływania API.

## Non-goals

Podnoszenie limitów runtime, normalizacja znaków operationId, tablicowe query,
multipart, pozostałe blokery, zmiana frontendu i wywoływanie API użytkownika.

## Ograniczenia i ryzyka

Hash 64-bitowy ma małe, ale niezerowe ryzyko kolizji; importer i zapis całego
katalogu wykrywają duplikaty i odrzucają publikację. Zmiana apiId/operationId
zmienia nazwę; kolejność i wybór innych operacji jej nie zmieniają. Test live
Copilota pozostaje opt-in. Zachowujemy zastane zmiany workspace.

## Kryteria akceptacji

Wynikowe nazwy mają do 64 dozwolonych znaków, są stabilne i rozróżniają długie
wspólne prefiksy; pełne operationId pozostają dostępne. Wywołanie skróconej
nazwy przez callback i realny MCP trafia pod prawidłową metodę/ścieżkę.
Regresja OpenAPI/System Assistant i guards przechodzi. Audyt potwierdza
197 importowalnych oraz pozostały query blocker czwartej operacji.

## Kroki

- [x] Krok 1: baseline, helper nazw, importer/preview i testy granicy 64/65,
  operationId ponad 128, stabilności, wspólnych prefiksów i kolizji.
- [x] Krok 2: test callbacka i rzeczywistego MCP z długimi identyfikatorami;
  regresja wszystkich konsumentów oraz granic pakietów. Bloker guarda usunięto
  przez własny kontrakt wykonania portu; reguły pozostały bez zmian.
- [x] Krok 3: aktualizacja kanonicznego runtime i instrukcji konfiguracji;
  audyt wszystkich 224 operacji i przegląd diffu.

## Dowody weryfikacji

- Baseline: 9 testów przeszło (`target/openapi-tool-names-baseline-jdk.log`).
- Regresja OpenAPI/System Assistant/guards: 86 testów, 0 failures/errors,
  1 pominięty opt-in live Copilot (`target/openapi-tool-names-regression.log`).
- Audyt: 224 operacje, 197 importowalnych pojedynczo i 27 blokowanych;
  długość nazwy nie blokuje żadnej operacji. Czwarta wcześniej blokowana
  nazwa dotyczy GET /clp/pricing-orders/base/latest z tablicowym query.
  Raporty: `target/backend-openapi-audit.json` oraz `.txt`. Bez wywołań API.
- Pełna regresja backendu (`target/openapi-tool-names-full-test.log`):
  2307 testów, 2300 przeszło, 1 failure, 0 errors, 6 pominiętych.
  OpenApiToolNamesTest, SystemAssistantCompositionTest (w tym dodatkowa
  kolizja między API), transport MCP i pozostałe testy OpenAPI/System Assistant
  przeszły. Test live Copilot jest opt-in i nie został uruchomiony.
- Jedyny failure: IntegrationPackageBoundaryTest.openApiRootContainsOnlyPublicPorts.
  OpenApiExecutionPort.java zmienił się równolegle, już po udanej celowanej
  regresji: dodano import integrations.httpauth.contract.HttpAuthProfile,
  którego istniejący guard root portu nie dopuszcza. Zmiana nie należy do
  skracania nazw. Bloker zamknięto następnie przez
  [własny kontrakt portu](openapi-execution-port-boundary.md): root przyjmuje
  OpenApiExecution zamiast bezpośrednio HttpAuthProfile. Regresja wszystkich
  konsumentów i obu guardów: 102 testy, 0 failures/errors, 1 pominięty.
- Przegląd ownership: helper pozostaje w integrations.openapi.service,
  brak nowych zależności i zmian DTO/FE. git diff --check bez błędów.
