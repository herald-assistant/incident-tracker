# Własny kontrakt wykonania w porcie OpenAPI

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Użytkownik zlecił poprawę HttpAuthProfile w OpenApiExecutionPort. Import
kontraktu innej integracji w root porcie łamie obecną regułę testu granic.
Nie należy ukrywać go kwalifikowaną nazwą typu ani osłabiać guarda.
Zmiana L2, zatwierdzona poleceniem poprawienia tej granicy.

## Baseline i conformance delta

Port przyjmuje operation, arguments i HttpAuthProfile jako trzy parametry.
Neutralny OpenApiExecution już istnieje w integrations.openapi.contract
i wiąże operację z captured Auth; korzystają z niego katalog, snapshot
oraz callbacki SDK/MCP. Factory rozpakowuje go przed wywołaniem portu.
Pełna poprzednia regresja: 2322 testy, 1 failure granicy root portu.
Baseline guardów: target/openapi-execution-port-baseline.log.

Delta: execute(OpenApiExecution, OpenApiArguments). Port importuje wyłącznie
własne modele. REST rozpakowuje binding, factory przekazuje istniejący captured
binding. Własny kontrakt nadal reuse'uje neutralny httpauth.contract; graf
zależności nie zyskuje nowej krawędzi. Auth pozostaje poza argumentami AI.
Reguły testu granic pozostają bez zmian.

Konsumenci: OpenApiRestExecutor, OpenApiToolSetFactory, testy OpenAPI oraz
fake execution lambdy testów System Assistant. Bez zmian HTTP DTO, schematów
narzędzi, MCP wire, Auth, sesji, promptów, historii, konfiguracji V1 i FE.
Nie ma nowych modeli, zależności, aliasu starego execute ani migracji;
wszystkie wywołania przechodzą na bieżący kontrakt. Zmiany spoza zakresu,
w tym application.properties, pozostają nietknięte.

## Proponowane rozwiązanie

Reuse istniejącego OpenApiExecution zamiast nowego wrappera albo przenoszenia
HttpAuthProfile do shared. To jedna reprezentacja bindingu dla katalogu,
callbacka i wykonania. Importy root portu i ownership modeli pozostają jawne.

## Zakres

Port, adapter REST, factory i wszyscy konsumenci/testy, dokumentacja granicy
oraz pełna regresja z guardami i Basic/Bearer/OAuth2/OIDC/No Auth.

## Non-goals

Zmiana sposobu uwierzytelniania, ujawnianie Auth modelowi, nowe HTTP API,
osłabienie reguł importów, nowe modele auth lub historyczny overload portu.

## Ograniczenia i ryzyka

Przekazany binding musi pozostać tym samym captured zestawem op/Auth; nie
wolno ponownie wybierać Auth w callbacku ani pobierać bieżącej konfiguracji.
Kompilacja wszystkich konsumentów i istniejące testy Auth/SDK/MCP chronią
przed zamianą operacji, poświadczeń lub utratą granicy argumentów AI.

## Kryteria akceptacji

IntegrationPackageBoundaryTest i PackageDependencyGuardTest przechodzą bez
zmian reguł. Port używa wyłącznie własnych kontraktów, bez HttpAuthProfile
także w formie kwalifikowanej. Wszystkie testy OpenAPI/System Assistant i
pełna regresja backendu przechodzą, poza jawnie pominiętymi opt-in testami.

## Kroki

- [x] Krok 1: baseline, port/własny binding, adapter/factory i konsumenci;
  kompilacja oraz testy OpenAPI/System Assistant/guardów.
- [x] Krok 2: pełne testy backendu, dokumentacja ownership, diff review
  oraz zamknięcie wcześniejszego blockera walidacji.

## Dowody weryfikacji

- Baseline guardów: 22 testy, 1 failure root portu, 0 errors
  (target/openapi-execution-port-baseline.log).
- Zaktualizowano 37 bezpośrednich wywołań testowych i 8 fake lambd wykonania,
  zachowując argumenty i dane fixtures. Produkcyjny factory przekazuje
  istniejący binding bez nowego wyboru Auth; REST używa jego operacji i Auth.
- OpenAPI/System Assistant oraz oba guardy: 102 testy, 101 przeszło,
  1 live Copilot pominięty, 0 failures/errors
  (target/openapi-execution-port-targeted.log). Reguł guardów nie zmieniano.
- Dokumentacja granic pakietów, runtime i lokalne AGENTS integracji/tools
  opisują jeden kontrakt bindingu. Brak starej sygnatury i overloadu;
  git diff --check bez błędów.
- Pełna regresja backendu: 2322 testy, 2316 przeszło, 6 pominiętych,
  0 failures/errors, BUILD SUCCESS (target/openapi-execution-port-full-test.log).
  Oba testy architektury, OpenAPI Auth oraz SDK/MCP przechodzą. Nie zmieniano
  reguł guardów; brak nowych testów kopiujących implementację. Zamknięto
  wcześniejszy bloker planu skracania nazw narzędzi.

Plan pozostaje do przeglądu zakresu i dowodów wykonania.
