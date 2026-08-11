# Wielosystemowy zakres GitLab code search

Status: completed

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Analiza jednego przeplywu moze zawierac logi z wielu `internal-service`.
Dotychczasowy pojedynczy `applicationName` ograniczal GitLab discovery do
jednego systemu i jednego zestawu code-search scope'ow, mimo ze deterministic
evidence wskazywalo kilka deploymentow.

## Proponowane rozwiazanie

Zastapic model-facing `applicationName` lista `applicationNames` bez aliasu
kompatybilnosci. Feature zapisuje wykryte systemy w hidden tool context, a
GitLab MCP traktuje brak parametru modelu jako uzycie domyslnej allowlisty z
sesji. Jawne `applicationNames` wybiera systemy dla danego callu i moze
rozszerzyc zakres poza domyslny evidence scope tylko dla CRM systemow
zarejestrowanych w operational context i majacych `codeSearchScope`. Scope
GitLaba jest unia code-search scope'ow wszystkich aktywnych systemow.

Operational Context zachowa wszystkie bezposrednio dopasowane
`internal-service` z nazw service/container/deployment nawet wtedy, gdy ich
liczba przekracza ogolny limit enrichmentu.

## Zakres

- pluralny kontrakt wszystkich GitLab MCP tools,
- neutralny hidden key dla dozwolonych systemow GitLaba,
- wielosystemowe rozstrzyganie grupy, code-search scope'ow i repozytoriow,
- Incident Analysis, Flow Explorer, Change Verification i Config Drift Viewer
  jako konsumenci hidden scope'u,
- runtime guidance, testy schematu, scope'u i detekcji.

## Non-goals

- zmiana publicznych requestow HTTP feature'ow,
- odblokowanie repozytoriow spoza dopasowanych code-search scope'ow,
- zmiana polityki broad search dla repozytoriow tylko wspierajacych,
- migracja aktywnych sesji Copilota ze starym schematem tooli.

## Ograniczenia i ryzyka

- zmiana schematu tooli jest celowo niekompatybilna; aktywne sesje trzeba
  rozpoczac ponownie,
- aliasy deploymentow musza byc rozstrzygane przez `system` i jego
  `matchSignals`, bez tworzenia osobnego bytu runtime,
- model moze rozszerzyc domyslny zakres tylko przez jawny system z katalogu,
  ktory ma `codeSearchScope`; nie moze otworzyc dowolnego repozytorium w
  grupie GitLab.

## Kryteria akceptacji

- schema GitLab tools zawiera `applicationNames` i nie zawiera
  `applicationName`,
- brak `applicationNames` w callu wykorzystuje wszystkie systemy z hidden
  context,
- brak listy uzywa hidden scope'u, a podana lista przechodzi tylko dla
  systemow z operational context majacych `codeSearchScope`,
- wszystkie bezposrednio wykryte `internal-service` trafiaja do operational
  context evidence niezaleznie od ogolnego limitu per typ,
- broad discovery obejmuje primary repozytoria z wielu dozwolonych scope'ow,
  ale nadal odrzuca repozytorium tylko wspierajace,
- testy celowane, `PackageDependencyGuardTest` i `mvn -q test` przechodza.

## Kroki

- [x] Krok 1: Zmienic kontrakt GitLab MCP i resolver scope'u; zweryfikowac
  schema oraz wielosystemowe broad discovery.
- [x] Krok 2: Zachowac wszystkie bezposrednie dopasowania `internal-service`
  i zasilic hidden scope feature'ow; zweryfikowac initial oraz follow-up.
- [x] Krok 3: Zaktualizowac runtime guidance i dokumentacje architektury oraz
  usunac stare odwolania do `applicationName` w kontrakcie tooli.
- [x] Krok 4: Uruchomic regresje konsumentow, test architektury i pelny zestaw
  testow backendu.
- [x] Krok 5: Poluzowac walidacje jawnych `applicationNames`, tak aby AI moglo
  dociagnac kod dodatkowego CRM systemu spoza domyslnego evidence scope, jezeli
  operational context definiuje dla niego `codeSearchScope`; zweryfikowac
  przypadek pozytywny i blokade systemu bez scope'u.
