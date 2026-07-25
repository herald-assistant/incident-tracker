# AGENTS

## Zakres

Ten katalog zawiera dedykowany feature Change Verification. Feature ma
porownywac zmiane z materialem z Jira/Confluence i instrukcjami repozytorium,
projektowac smoke pack dla zmiany oraz docelowo uruchamiac zaakceptowane testy
z bezpiecznym cleanupem.

## Zasady

- Nie importuj `features.incidentanalysis` ani `features.flowexplorer`.
  Istniejace feature'y sa wzorcami porownawczymi, ale nie core dla Change
  Verification.
- Feature moze zalezec od `aiplatform`, `agenttools`, `integrations`,
  `shared` i `common`.
- Prompt, response parser, result contract, tool policy, hidden context,
  skills, job API i UI contract sa wlasnoscia Change Verification.
- Reusable capability, np. Jira, Confluence, GitLab MR discovery,
  instructions discovery, smoke rendering, HTTP execution i DB assertions,
  powinny mieszkac poza feature'em w odpowiednich `integrations.*` oraz
  `agenttools.*`.
- Platforma ani AI nie moga wykonywac zapisow do DB. Cleanup DB jest albo
  przez allowlisted endpoint, albo jako manualna SQL-ka dla uzytkownika.

## Weryfikacja

- `PackageDependencyGuardTest` ma pilnowac braku zaleznosci pomiedzy
  `features.changeverification`, `features.incidentanalysis` i
  `features.flowexplorer`.
