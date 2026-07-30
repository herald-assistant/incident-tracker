# AGENTS

## Zakres

Ten katalog zawiera dedykowany feature Runtime Configuration Verification.
Feature porownuje konfiguracje wybranego `internal-system` pomiedzy branchami
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
- Surowa konfiguracja i sekrety nie moga trafic do promptu, evidence,
  activity, reportu, historii, eksportu ani user-facing bledow.
- Wynik deterministyczny jest niemutowalny dla AI. AI dostarcza osobna,
  jawnie oznaczona druga opinie.
- `BASIC` nie wykonuje operational enrichment ani odczytu kodu.
- `DEEP` korzysta tylko z code-search scopes wybranego `internal-system` i
  rozwiazuje ownership z systemu albo bounded contextu.

## Weryfikacja

- `PackageDependencyGuardTest` pilnuje izolacji sibling feature'ow.
- Request/job API testuj przez `MockMvc`.
- Zmiany w deterministic parsing/diff, AI runtime, GitLab i Operational
  Context wymagaja testow wskazanych w zatwierdzonym planie
  `docs/plans/runtime-configuration-verification.md`.
- Kanoniczny runtime flow i security boundary sa opisane w
  `docs/architecture/runtime-configuration-verification-runtime-flow.md`.
