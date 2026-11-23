# Pomijanie opcjonalnych nagłówków OpenAPI

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator chce importować API z opcjonalnymi nagłówkami, bez możliwości
podawania ich przez AI. W backend.json wszystkie 75 deklaracji header
correlationId nie ma pola required, czyli mają domyślne required=false.
Użytkownik jawnie zlecił ten zakres; poziom L2, neutralny importer SDK/MCP.

## Baseline i conformance delta

Obecny importer odrzuca każdy parametr header. Selekcja zachowuje parametry
i dokumentację; narzędzia mają wyłącznie path/query/body. Neutralny REST
ustawia Auth z profilu operatora poza argumentami AI. Bieżąca regresja BE:
78 testów, zero failures/errors, 1 pominięty live Copilot
(`target/openapi-patch-regression.log`). Wcześniejszy audyt wszystkich 224
operacji wykazał 131 importowalnych i 93 blokowane; 75 deklaruje header.

Zmiana: opcjonalny efektywny parametr header jest pomijany przed budowaniem
schematu requestu i listy wysyłanych parametrów. Metadane pozostają w źródle,
samodzielnym YAML, podglądzie i V1 eksporcie. Preview i opis narzędzia jawnie
informują o pomijaniu nagłówków. Nie walidujemy ich schematów jako argumentów
requestu i nie używamy default/example do wysyłania. Required musi pozostać
boolean; wymagany header jest odrzucany konkretnym błędem. Reuse jednej
reguły dziedziczenia i override parametrów dla parsera i diagnostyki;
nazwy nagłówków są rozpoznawane bez rozróżnienia wielkości liter.

Konsumenci: parser, preview/selection/document, konfiguracja V1,
SDK/MCP callbacks i REST, ogólny panel uwag FE. Ownership pozostaje w
integrations.openapi; bez zmian platformy, sesji, DTO, Auth i limitów.
Nie ma nowego formatu, aliasu ani ścieżki kompatybilności. Zastane zmiany
użytkownika w application.properties pozostają poza zakresem.

## Zakres

Wszystkie poprawnie nazwane opcjonalne parametry in: header, w tym brak
required, z zachowaniem efektywnego dziedziczenia parametrów. Required
headers pozostają blokowane. Próby przesłania header/headers jako argumentu
narzędzia są odrzucane przed HTTP. Auth operatora pozostaje niezależny.

## Non-goals

Przesyłanie lub generowanie correlationId, dowolnych nagłówków albo cookies,
pomijanie wymaganych parametrów, nowe Auth, zmiana pozostałych ograniczeń
OpenAPI oraz wywołania docelowego API operatora.

## Ograniczenia i ryzyka

Pominięcie opcjonalnego parametru jest zgodne z kontraktem, lecz nie stanowi
dowodu zachowania rzeczywistego serwera. Brak dostępu do dowolnych nagłówków
pozostaje twardą granicą także przy spreparowanych argumentach SDK/MCP.
Nagłówek Authorization z profilu Auth nadal może być wysłany; nie pochodzi
z parametru nagłówkowego OpenAPI ani z argumentów AI. Inne ograniczenia
źródła mogą nadal blokować daną operację.

## Kryteria akceptacji

Specyfikacja z opcjonalnym header bez schema przechodzi import i zachowuje
dokumentację. Schema narzędzia oraz lista parametrów wykonawczych nie
zawierają header. Lokalny HTTP potwierdza brak nagłówka, poprawne Auth i
odrzucanie prób jego podania przed HTTP. Required header i niepoprawny typ
required pozostają blokowane. Audyt backend.json jest odświeżony.

## Kroki

- [x] Krok 1: reguła efektywnych parametrów, parser i diagnostyka; testy
  required false/brak/true, błędnego typu, dziedziczenia i override.
- [x] Krok 2: SDK/HTTP/MCP — brak nagłówka i jego argumentów, brak użycia
  default/example, Auth poza AI, odrzucenie prób wstrzyknięcia przed HTTP.
- [x] Krok 3: regresja konsumentów i granic pakietów, dokumentacja oraz
  odświeżenie audytu wszystkich 224 operacji. Live Copilot raportować osobno.


## Weryfikacja

- `target/openapi-optional-headers-regression.log`: 82 testy, zero
  failures/errors; 81 wykonanych testów przeszło, 1 opt-in live Copilot
  pominięty. Zakres obejmuje integrację OpenAPI, System Assistant, MCP wire,
  SDK oraz architecture guards.
- OpenApiOptionalHeaderTest potwierdza zachowanie dokumentacji, pomijanie
  deklaracji bez schema i required, required=false, odrzucenie required=true
  i błędnego typu, case-insensitive override, duplikaty, brak header HTTP,
  brak użycia defaultów oraz zachowanie Auth z profilu.
- Testy SDK/MCP potwierdzają brak correlationId w input schema i odrzucanie
  prób podania headers przed HTTP. Guard po nieustalonym PATCH pozostaje.
- Aktualny audyt backend.json: 224 operacje, 194 importowalne pojedynczo,
  30 blokowanych z innych przyczyn; 75 pomijanych opcjonalnych deklaracji
  correlationId. Wszystkie 9 operacji PATCH przechodzi import. Raporty
  target/backend-openapi-audit.json i .txt zostały odświeżone.
- Nie wywoływano docelowego API; nie zmieniano kodu produkcyjnego FE,
  kontraktu konfiguracji ani formatu V1. Dokumentacja runtime, obsługi,
  tworzenia konfiguracji i lokalne instrukcje są aktualne.

Plan pozostaje do przeglądu zakresu i dowodów wykonania.
