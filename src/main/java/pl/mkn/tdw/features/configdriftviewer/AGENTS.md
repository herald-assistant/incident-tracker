# AGENTS

## Zakres

Ten katalog zawiera dedykowany feature Config Drift Viewer.
Feature porownuje konfiguracje wybranego `internal-service` pomiedzy branchami
srodowiskowymi, a w trybie `DEEP` moze dodatkowo interpretowac roznice przez
Operational Context, code-search scopes i kod systemu.

## Zasady

- Nie importuj `features.incidentanalysis`, `features.flowexplorer` ani
  `features.changeverification`. Sibling feature'y sa wzorcami zachowania, ale
  nie sa core tego feature'a.
- Feature moze zalezec od `aiplatform`, `agenttools`, `integrations`,
  `shared`, `localworkspace` i `common`.
- Publiczny input uzywa kanonicznego Operational Context `systemId`.
  Katalog konfiguracji jest rozstrzygany po stronie backendu z
  runtime/deployment signalu systemu i nie jest publicznym `componentPath`.
- Repozytorium konfiguracji i repozytoria kodu sa roznymi scope'ami oraz moga
  korzystac z roznych polaczen GitLab.
- Dokladne wartosci ze znormalizowanej projekcji operatorskiej moga trafic do
  UI, lokalnej historii, eksportu i Workbench. Nie moga trafic do promptu,
  AI artifacts, evidence, activity, reportu, logow ani user-facing bledow.
- Projekcja operatorska nie zawiera byte-identical plikow, komentarzy, tokenu
  GitLaba ani wartosci sekretow z Vault i ma redacted `toString`.
- Wynik deterministyczny jest niemutowalny dla AI. AI dostarcza osobna,
  jawnie oznaczona druga opinie.
- `BASIC` konczy run po deterministic `DIFF`: nie rozwiazuje auth Copilota,
  nie przygotowuje promptu/artefaktow, nie laduje skilla i nie uruchamia AI,
  reportu ani tools.
- `DEEP` korzysta tylko z code-search scopes wybranego `internal-service` i
  rozwiazuje ownership z systemu albo bounded contextu.
- Pakiet `ai` jest DEEP-only i nie moze importowac
  `deterministic.projection` ani `presentation`.

## Weryfikacja

- `PackageDependencyGuardTest` pilnuje izolacji sibling feature'ow.
- Request/job API testuj przez `MockMvc`.
- Zmiany w deterministic parsing/diff, AI runtime, GitLab i Operational
  Context wymagaja macierzy testow adekwatnej do poziomu L1-L3 z
  `docs/architecture/analysis-feature-delivery-playbook.md` oraz nowego,
  jawnie zatwierdzonego planu dla konkretnej zmiany.
- Kanoniczny runtime flow i security boundary sa opisane w
  `docs/architecture/config-drift-viewer-runtime-flow.md`.
