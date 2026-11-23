# Tablice query jako argumenty narzędzi SDK i MCP

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

17 operacji backend.json przyjmuje listę identyfikatorów lub statusów w query.
Użytkownik zlecił poprawkę po wyjaśnieniu modelu arguments.query i wspólnego
wykonawcy HTTP. Zatwierdzony zakres to jednowymiarowe tablice skalarów,
style=form i oba warianty explode; zmiana L2 neutralnej integracji OpenAPI.

## Baseline i conformance delta

Importer dopuszcza tylko scalar path/query, a REST używa asText dla pojedynczej
wartości. Schema narzędzia już grupuje query w obiekcie arguments.query;
callbacki SDK i MCP mają wspólny wykonawca, Auth jest poza argumentami.
Selekcja zachowuje źródłowe schema i parametry. V1 konfiguracji przechowuje
YAML, a runtime parsuje go przy odczycie i zapisie. Audyt: 197/224 operacje
importowalne pojedynczo. Bazowe testy: target/openapi-query-arrays-baseline.log;
znany niezależny failure guarda OpenApiExecutionPort / HttpAuthProfile
z poprzedniej pełnej regresji pozostaje poza zakresem.

Delta: importer dopuszcza query type=array z jawnym scalar items
(string/integer/number/boolean), bez nullable na tablicy/elementach. Zachowuje
required, enum, uniqueItems i limity schema. Parameter zyskuje boolean explode
(domyślnie true dla form query, false dla simple path). REST wysyła powtarzane
klucze dla explode=true lub listę z literalnymi przecinkami dla false,
kodując każdy element osobno. Brak opcjonalnej tablicy pomija parametr;
pusta tablica jest odrzucana przed HTTP jako niemająca jednoznacznego zapisu,
bez podmiany na brak parametru lub pusty string. Jawny pusty string w tablicy
tekstów pozostaje elementem. Nie dopisujemy domyślnych wartości.

Ownership i konsumenci: integrations.openapi.contract/service/adapter.rest;
preview/selection/document, konfiguracja V1, callbacki SDK, katalog MCP,
testy modeli Parameter. Bez zmian publicznych HTTP DTO, FE, skilli, promptu
feature'a, sesji, Auth, historii i eksportu. Model-facing schema query
zachowuje deklarowaną tablicę. Nie ma nowych zależności. Stare dwuargumentowe
konstruktory Parameter są zastąpione u wszystkich konsumentów, bez aliasów,
wersjonowania albo migracji; runtime nie zapisuje tego modelu na dysku.

## Proponowane rozwiązanie

Reuse istniejącej walidacji JSON Schema i efektywnych parametrów; metadane
explode w neutralnym kontrakcie. Jeden serializer REST dla SDK/MCP, z testami
rzeczywistego URL. Wartości argumentów nigdy nie są wklejane do URI template.
Zmienna query nie może kolidować ze zmienną path. Odniesienie:
[OpenAPI Parameter Object](https://spec.openapis.org/oas/v3.1.0.html#parameter-object).

## Zakres

Tablice query, form/default, explode true/false/default, lokalne refs do schema,
dziedziczenie i override parametrów, schema SDK/MCP, transport HTTP i audyt.

## Non-goals

Obiekty query, tablice zagnieżdżone/obiektów/null, array path/header/cookie,
deepObject/spaceDelimited/pipeDelimited, multipart i inne blokery, wywołania
API operatora i live Copilot bez opt-in.

## Ograniczenia i ryzyka

Przecinek w elemencie musi być zakodowany osobno od separatora CSV; &, =, +,
%, #, Unicode i nawiasy nie mogą tworzyć nowych parametrów/fragmentu/hosta.
Puste tablice są jawnym ograniczeniem wykonania; źródła nie są przepisywane.
Zastane zmiany workspace pozostają nietknięte.

## Kryteria akceptacji

17 operacji z tablicami przechodzi import; audyt osiąga 214/224 pojedynczo.
SDK/MCP przyjmują JSON array i wysyłają prawidłowy GET. Rzeczywisty HTTP
potwierdza repeated/CSV, kodowanie, kolejność, brak optional, walidację enum,
uniqueItems, required, min/maxItems i typu przed HTTP. Obiekty oraz nieobsługiwane
style nadal są odrzucane. Regresja konsumentów i architecture guards są
uruchomione; niezależne zastane failures są raportowane oddzielnie.

## Kroki

- [x] Krok 1: baseline, parser, model Parameter i serializer; testy importu,
  refs/override, repeated/CSV i bezpiecznego URI oraz błędnych argumentów.
- [x] Krok 2: test SDK i realnego transportu MCP, regresja OpenAPI/System
  Assistant, guards i pełne testy backendu zgodnie z root AGENTS.md.
- [x] Krok 3: kanoniczny runtime, instrukcje konfiguracji, audyt backend.json,
  przegląd konsumentów i diffu; dowody wykonania.

## Dowody weryfikacji

- Baseline: 87 testów, 85 przeszło, 1 pominięty live Copilot, 1 znany
  failure IntegrationPackageBoundaryTest.openApiRootContainsOnlyPublicPorts
  dotyczący importu HttpAuthProfile w OpenApiExecutionPort
  (`target/openapi-query-arrays-baseline.log`).
- Testy celowane: 15 przeszło, bez failures/errors: OpenApiQueryArrayTest,
  SystemAssistantCompositionTest, SystemAssistantMcpWireTest
  (`target/openapi-query-arrays-targeted.log`).
- HTTP potwierdza repeated, CSV, liczby/boolean, Unicode i reserved chars,
  literalny separator CSV oraz osobne kodowanie przecinka w elemencie,
  zachowanie pustego stringa i pomijanie nieobecnej opcjonalnej tablicy.
  Przed HTTP odrzucane są niepoprawne elementy/typy, enum, duplikaty,
  min/maxItems i puste tablice; refs i path-level override są zachowane.
- Audyt wszystkich 224 operacji: 214 importowalnych pojedynczo, 10 blokowanych
  (8 multipart request i 2 text/plain response). Wszystkie 17 query array
  przechodzi selekcję i pełny import. Raporty:
  `target/backend-openapi-audit.json` oraz `.txt`. Bez wywołań API operatora.
- Dokumentacja runtime, authoring, obsługi i lokalne AGENTS są aktualne.
  Wszystkie konstruktory Parameter przestawiono na bieżący model;
  brak nowych kierunków zależności, publicznych DTO, aliasów lub migracji.
  git diff --check bez błędów.
- Pełna regresja backendu (`target/openapi-query-arrays-full-test.log`):
  2312 testów, 2305 przeszło, 6 pominiętych, 1 failure i 0 errors.
  Jedyny failure to zastany IntegrationPackageBoundaryTest dla importu
  HttpAuthProfile w OpenApiExecutionPort, identyczny z baseline; portu nie
  zmieniano. PackageDependencyGuardTest i wszystkie testy OpenAPI/System
  Assistant przeszły. Brak nowych failures. Live Copilot nie był uruchamiany.

Plan pozostaje do przeglądu zakresu i dowodów wykonania.
