# Config Drift runtime settings

Status: done

Source need: [runtime-configuration-verification](../needs/runtime-configuration-verification.md)

## Potrzeba / dlaczego

Lista branchy widoczna w formularzu Config Drift Verification jest obecnie
generowana w kodzie. Dodatkowa instancja GitLaba używana przez named connection
`runtime-config` może być ustawiona tylko przez `application.properties`, mimo
że aplikacja ma operatorski mechanizm Workspace Settings dla pozostałych
integracji.

## Zakres

- przeniesienie listy branchy do
  `features.runtime-configuration-verification.branches`,
- wykorzystanie tej listy przez wspólne input options strony feature'a i Tool
  Workbench,
- dodanie do Workspace Settings pól dla
  `integrations.gitlab.named.connections.runtime-config.base-url` i `token`,
- zastosowanie override do istniejącego named GitLab connection bez restartu,
- aktualizacja lokalnego `settings.json`, UI, testów i dokumentacji.

## Non-goals

- zmiana formatów dozwolonych branchy w publicznym request DTO,
- wystawienie `ignore-ssl-errors`, repository catalog albo innych named
  connections w Workspace Settings,
- zmiana technicznego ID, route albo API Config Drift Verification.

## Baseline i conformance delta

- Baseline: input options generuje 20 branchy w Javie, a Workspace Settings
  obsługuje tylko główne `analysis.gitlab.*`.
- Delta: branch choices są związane z właściwością feature'a, a Workspace
  Settings otrzymuje addytywną grupę `runtimeConfigGitLab` mapowaną wyłącznie
  na named connection `runtime-config`.
- Konsumenci: główny formularz i Workbench przez input-options API; named
  connection registry i exact repository adapter przez mutowalny bean
  `GitLabNamedConnectionsProperties`; lokalny store, settings API i Angular UI.
- Zgodność danych: starszy `settings.json` bez nowej grupy jest odczytywany z
  pustymi override'ami; wersja schematu rośnie do `6`.
- Granice: `integrations.gitlab` pozostaje niezależne od API i feature'a;
  Workspace Settings nakłada tylko wartości na istniejący properties bean.

## Kroki

- [x] Krok 1: Skonfigurować branch choices przez `application.properties` i
  pokryć binding oraz input-options testami.
- [x] Krok 2: Dodać override named GitLaba `runtime-config` w backendzie,
  lokalnym store i Workspace Settings UI wraz z testami kontraktu.
- [x] Krok 3: Poprawić nazwę produktową na `Config Drift Verification`,
  zaktualizować dokumentację i wygenerowany bundle oraz wykonać pełną
  weryfikację.

  Weryfikacja 2026-08-01: lista branchy jest wiązana z properties i używana
  przez input-options bez generowania w kodzie. Workspace Settings zapisuje
  addytywną grupę `runtimeConfigGitLab`, aktualizuje named connection bez
  restartu i zachowuje mechanikę DEFAULT/CUSTOM oraz resetu. Schemat
  `settings.json` ma wersję `6`. Przeszły testy celowane backendu, pełne
  `mvn -q clean test`, `npm test -- --watch=false` (227 testów),
  `npm run build`, `mvn -q -DskipTests package` oraz `git diff --check`.
- [x] Krok 4: Zmienic domyslne branche na `dev`, `dev2`, `uat`, `uat2` i
  uogolnic walidacje requestu do bezpiecznej nazwy refa Git, aby lista nadal
  byla konfigurowalna bez zmiany kodu.

  Weryfikacja 2026-08-01: domyslna lista ma dokladnie cztery uzgodnione
  wartosci; input-options zachowuje ich kolejnosc, request joba i Workbench
  akceptuja `dev`/`uat`, a niebezpieczne znaki refa sa odrzucane. Przeszly
  testy input-options, bindingu properties, obu kontrolerow oraz start pelnego
  kontekstu Spring.
- [x] Krok 5: Ograniczyc walidacje branchy do rodzin `dev`, `test`, `uat` i
  `zt` z opcjonalnym wielocyfrowym sufiksem oraz pokryc requesty wielocyfrowe
  testami joba i Workbench.

  Weryfikacja 2026-08-01: request joba zaakceptowal `dev12` -> `uat345`, a
  Workbench `test12` -> `zt345`; domyslne `dev`, `dev2`, `uat`, `uat2` nadal
  przechodza walidacje properties. Przeszly testy kontrolerow, input-options,
  bindingu oraz start pelnego kontekstu Spring.
