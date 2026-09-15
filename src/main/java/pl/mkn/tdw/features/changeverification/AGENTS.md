# AGENTS

## Zakres

Ten katalog zawiera dedykowany feature Change Verification. Feature ma
porownywac zmiane z materialem z Jira/Confluence i instrukcjami repozytorium.

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

## Weryfikacja

- `PackageDependencyGuardTest` ma pilnowac braku zaleznosci pomiedzy
  `features.changeverification`, `features.incidentanalysis` i
  `features.flowexplorer`.
