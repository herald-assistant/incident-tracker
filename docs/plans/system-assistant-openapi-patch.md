# Obsługa PATCH w importowanych API

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator chce korzystać z operacji PATCH widocznych w importowanym katalogu.
Użytkownik jawnie zlecił dodanie obsługi PATCH; ten zakres jest zatwierdzony.
Poziom L2: rozszerzenie neutralnej integracji używanej przez SDK i MCP.

## Baseline i konsumenci

Preview kataloguje PATCH, lecz oznacza go jako niedostępny. Importer odrzuca
metodę i body, a security validation oraz domknięcie securitySchemes pomijają
PATCH. REST korzysta już z JDK HTTP client i dynamicznego HttpMethod, więc
transport nie wymaga osobnego wykonawcy ani zmiany klienta. UI i renderer
potrafią wyświetlać, filtrować i wybierać dowolną metodę z katalogu.
Wcześniejsza regresja bieżącego BE: 73 testy, zero failures/errors, 1 pominięty
live Copilot (`target/openapi-final-regression.log`). Późniejsza poprawka
paginacji dotyczyła tylko FE; 7 testów pickera i build przeszły.

Konsumenci: preview/selection/document, zapis i odczyt konfiguracji V1,
securityScheme closure, katalog tools, callbacki SDK i scoped MCP oraz
neutralny REST. Semantyka skutków UNKNOWN/MUTATION/READ_ONLY, Auth poza AI,
limity 60/100, transkrypty i NEW sesja per tura pozostają.

## Proponowane rozwiązanie i conformance delta

Dodać PATCH do obecnego zestawu wykonywalnych metod w parserze, selekcji
i obsłudze security. Dopuścić body application/json, również opcjonalne,
oraz operacje bez body. Walidować dokładnie schema autora; PATCH nie znosi
required, nie dopisuje defaultów i nie zamienia żądania na PUT. Reuse całego
dotychczasowego toru SDK/MCP/REST, bez nowego kontraktu DTO i wersji formatu.
Usunąć nieaktualne testy i dokumentację uznające PATCH za niewspierany;
negatywne przypadki metod przenieść na HEAD.

## Zakres

PATCH z application/json lub bez body, dziedziczenie parametrów path/query,
operation-level security, walidacja, HTTP i guard po nieustalonym zapisie.

## Non-goals

JSON Patch i Merge Patch z odrębnymi media types, HEAD/OPTIONS, uploady,
parametry header/cookie oraz zmiana limitu nazw narzędzi.

## Ograniczenia i ryzyka

W pliku operatora jest 9 operacji PATCH: 7 ma JSON body, 2 nie mają body.
Siedem deklaruje też nieobsługiwany parametr header correlationId. Samo
dodanie PATCH nie oznacza obsługi tych nagłówków; źródło nie jest po cichu
przepisywane. Testy lokalne nie wywołują API operatora. Nie ma retry,
redirectów ani nowej ścieżki kompatybilności. Bieżący format pozostaje V1.

## Kryteria akceptacji

PATCH jest wybieralny w preview i zachowany w samodzielnym kontrakcie.
Security i schemat body są walidowane, poprawne argumenty trafiają jako
rzeczywisty PATCH do lokalnego API przez SDK callback i MCP, a błędne nie
docierają do HTTP. Operacje bez body i opcjonalne body działają. Nieustalony
potencjalny zapis blokuje kolejne zapisy tej tury/sesji. Inne ograniczenia
kontraktu nadal mają jawne błędy.

## Kroki

- [x] Krok 1: parser, selection i security oraz aktualizacja testów dawnego
  zakazu PATCH. Weryfikacja: import i selection, zachowanie operation-level
  securitySchemes, nieznany scheme odrzucany, HEAD pozostaje niedostępny.
- [x] Krok 2: testy lokalnego HTTP, częściowego JSON, required, opcjonalnego
  i brakującego body, Auth i guardu. SDK/MCP używają tego samego wykonawcy;
  MCP wire potwierdza transport PATCH i walidację przed HTTP.
- [x] Krok 3: aktualizacja dokumentacji, testów pickera i regresja wszystkich
  konsumentów BE, MCP oraz architecture guards. Kryterium: wszystkie testy
  aktualnego kontraktu przechodzą; opt-in live Copilot raportowany osobno.


## Weryfikacja

- `target/openapi-patch-regression.log`: 78 testów, 0 failures/errors,
  1 pominięty opt-in live Copilot; 77 wykonanych testów przeszło.
  Zakres: OpenApi*, SystemAssistant*, PackageDependencyGuardTest i
  IntegrationPackageBoundaryTest.
- `OpenApiPatchTest`: selection i operation-level security, rzeczywisty
  HTTP PATCH, częściowy JSON bez dopisywania defaultu, null, Auth,
  required, body opcjonalne i brak body; nieobsługiwane media types
  nadal odrzucane. Niepoprawne argumenty nie docierają do HTTP.
- SDK test zachowuje schema PATCH i walidowany callback. MCP wire wysyła
  rzeczywisty PATCH, waliduje przed HTTP i utrzymuje WRITE_BLOCKED po
  OUTCOME_UNKNOWN. Nie wykonano sesji rzeczywistego modelu ani API operatora.
- `target/openapi-patch-frontend-tests.log`: 8/8 testów pickera, w tym
  wybór i filtr PATCH. Kod produkcyjny FE nie wymagał zmiany; metoda
  pochodzi z bieżącego katalogu backendu.
- `git diff --check`: bez błędów. Dokumentacja runtime, obsługi i tworzenia
  konfiguracji opisuje aktualny podzbiór. Format V1 i limity pozostają.

Plan pozostaje do przeglądu zakresu i dowodów wykonania.

## Kolejny zatwierdzony zakres

Pomijanie opcjonalnych header dostarczono osobnym
[planem](system-assistant-optional-headers.md). Nagłówek correlationId
nie blokuje już importu operacji PATCH z pliku operatora.
