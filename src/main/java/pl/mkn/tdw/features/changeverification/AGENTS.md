# AGENTS

## Zakres

Ten katalog zawiera dedykowany feature Change Verification. Feature ma
porownywac zmiane z materialem z Jira/Confluence i instrukcjami repozytorium.

## Najpierw przeczytaj

- `docs/architecture/change-verification-runtime-flow.md` jest kanonicznym
  opisem source scope, joba, promptu, ledgeru, decyzji, reportu, import/export
  i UI.
- Dla zmiany L1-L3 przeczytaj dodatkowo
  `docs/architecture/analysis-feature-delivery-playbook.md` i przygotuj nowy
  albo zaktualizowany need oraz zatwierdzony plan. Zakonczonych planow nie
  traktuj jako dokumentacji aktualnego stanu.

## Zasady

- Nie importuj `features.incidentanalysis` ani `features.flowexplorer`.
  Istniejace feature'y sa wzorcami porownawczymi, ale nie core dla Change
  Verification.
- Feature moze zalezec od `aiplatform`, `agenttools`, `integrations`,
  `shared` i `common`.
- Prompt, response parser, result contract, tool policy, hidden context,
  skills, job API i UI contract sa wlasnoscia Change Verification.
- `ruleLedger.rules` jest jedynym zrodlem prawdy wyniku. Kazda regula autora
  wystepuje raz, zachowuje cytat i source reference, a backend wylicza decyzje
  wylacznie z jej outcome. Maksymalnie piec `additionalChecks` pozostaje jawnie
  oddzielone i nie moze zmieniac decyzji source-defined.
- Target issue jest glownym zakresem. Direct subtasks rozszerzaja wyszukiwanie
  MR, ale parent, siblings i wymagania subtaskow nie staja sie automatycznie
  regulami targetu. Instruction discovery wykonuj raz na unikalne
  `(projectPath, analysisRef)` z suma deduplikowanych changed files.
- Parser odpowiedzi AI waliduje caly ledger. Nie pomijaj po cichu wadliwych
  wpisow, nie akceptuj powtorzonych ids i wymagaj powiazania visibility limits
  z istniejacymi regulami. Surowe limity discovery pozostaja w diagnostyce.
- Report jest deterministyczna projekcja ledgeru. Sesja AI Change Verification
  nie uzywa report tools i nie tworzy konkurencyjnego Markdown.
- W fazie V1 Change Verification utrzymuje tylko aktualny kontrakt
  request/result/report/export/import. Nie dodawaj aliasow, migratorow,
  przeciazen ani normalizacji istniejacych wylacznie dla kompatybilnosci
  wstecznej tego feature'a. Safety fallbacki aktualnego flow nie sa
  kompatybilnoscia wsteczna.
- Reusable capability, np. Jira, Confluence, GitLab MR discovery i
  instructions discovery, powinny mieszkac poza feature'em w odpowiednich `integrations.*` oraz
  `agenttools.*`.
- UI utrzymuje jedna karte na regule, z cytatem autora przed interpretacja,
  filtrem attention/satisfied/all i domyslnie zwinietymi dodatkowymi checks AI.
  Nie przywracaj osobnych sekcji priority review, confirmed scope ani full
  material.

## Weryfikacja

- `PackageDependencyGuardTest` ma pilnowac braku zaleznosci pomiedzy
  `features.changeverification`, `features.incidentanalysis` i
  `features.flowexplorer`.
- Celowany backend: `mvn -q "-Dtest=*ChangeVerification*" test`.
- Celowany frontend: `npm --prefix frontend test -- --include
  "src/app/features/change-verification/**/*.spec.ts"`.
- Dla zmiany kontraktu backend-frontend wykonaj pelna macierz z root
  `AGENTS.md`.
