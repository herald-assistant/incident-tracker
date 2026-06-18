# AGENTS

## Zakres

Ten katalog zawiera dedykowany feature Flow Explorer. Feature pozwala
uzytkownikowi wybrac system i endpoint, zbudowac deterministic endpoint
use-case context, uruchomic sesje AI z feature-owned promptem/policy/skillami
oraz pokazac wynik dokumentacyjny dla analityka albo testera.

## Zasady

- Nie importuj `features.incidentanalysis`. Incident analysis jest wzorcem
  porownawczym, ale nie core dla Flow Explorera.
- Feature moze zalezec od `aiplatform`, `agenttools`, `integrations`,
  `shared` i `common`.
- Prompt, response parser, result contract, tool policy, hidden context,
  skills, job API i UI contract sa wlasnoscia Flow Explorera.
- Reusable capability zostaja poza feature'em:
  - GitLab endpoint inventory i endpoint use-case context w `integrations.gitlab`
    oraz `agenttools.gitlab`,
  - operational context katalog/read modele w `integrations.operationalcontext`
    i `agenttools.operationalcontext`,
  - Copilot runtime mechanics w `aiplatform.copilot`.
- User instructions z UI sa intencja uzytkownika, nie nadpisaniem canonical
  promptu, response contractu ani polityki tools.
- Domyslny MVP opisuje persistence code-first i nie wlacza DB tools jako
  runtime data diagnostics.

## Weryfikacja

- `PackageDependencyGuardTest` ma pilnowac braku zaleznosci pomiedzy
  `features.flowexplorer` i `features.incidentanalysis`.

