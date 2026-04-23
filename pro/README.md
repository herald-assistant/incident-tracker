# Pro Context Pack

## Cel

Ten katalog jest paczka kontekstowa do pracy koncepcyjnej nad projektem w GPT
Pro.

Ma sluzyc do:

- szybkiego wejscia w projekt bez ponownego czytania calego repo,
- prowadzenia warsztatow architektonicznych i produktowych,
- planowania optymalizacji funkcjonalnych i technicznych,
- szczegolnie: optymalizacji wykorzystania GitHub Copilot Java SDK.

Stan opisany w tym katalogu odpowiada repo na dzien `2026-04-22`.

## Source of truth

Ten pakiet zostal zlozony na podstawie:

- `AGENTS.md`
- `docs/architecture/01-system-overview.md`
- `docs/architecture/02-key-decisions.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/architecture/04-codex-continuation-guide.md`
- `docs/onboarding/*`
- kluczowych klas z `src/main/java/pl/mkn/incidenttracker/analysis/*`
- `src/main/resources/application.properties`
- `src/main/resources/copilot/skills/incident-analysis-core/SKILL.md`
- `src/main/resources/copilot/skills/incident-analysis-gitlab-tools/SKILL.md`
- `src/main/resources/copilot/skills/incident-data-diagnostics/SKILL.md`
- wybranych testow w `src/test/java/pl/mkn/incidenttracker/analysis/*`

## Jak czytac

Najlepsza kolejnosc:

1. `01-system-and-product-context.md`
2. `02-architecture-and-code-map.md`
3. `03-runtime-contracts-and-configuration.md`
4. `04-copilot-sdk-current-state.md`
5. `05-copilot-sdk-optimization-playbook.md`
6. `06-functional-and-technical-optimization-backlog.md`
7. `07-open-questions-and-decision-register.md`
8. `08-gpt-pro-workshop-guide.md`

## Najwazniejsze stale zasady

- `POST /analysis` przyjmuje tylko `correlationId`.
- `gitLabBranch` i `environment` sa wyprowadzane z evidence, glownie z logow.
- `gitLabGroup` pochodzi z konfiguracji aplikacji.
- Glowny flow jest `AI-first`, nie `rule-based`.
- Evidence providers sa sekwencyjne i pracuja na wspolnym `AnalysisContext`.
- GitLab deterministic evidence, GitLab MCP tools i Database MCP tools sa
  osobnymi capability.
- GitLab i Database tools sa session-bound przez hidden `ToolContext`, a nie
  przez model-facing `group`, `branch`, `environment` albo `correlationId`.
- Skill Copilota jest runtime resource aplikacji, nie plikiem w `.github`.
- Nie wolno mieszac klas adapter-specific bezposrednio do kontraktu AI.
- Nie wolno robic globalnego "trust all SSL" dla calej aplikacji.

## Co jest tutaj najwazniejsze dla GPT Pro

Jesli celem sesji jest optymalizacja Copilot SDK, GPT Pro powinien rozumiec
jednoczesnie cztery rzeczy:

1. obecny runtime flow od `correlationId` do wyniku,
2. aktualny model prompt + attachments + tools + skills,
3. granice miedzy deterministic evidence, AI-guided GitLab exploration i
   AI-guided DB diagnostics,
4. miejsca, gdzie projekt ma dzisiaj ukryte koszty, ryzyka i ograniczenia.

Dlatego najwazniejsze pliki tego katalogu to:

- `03-runtime-contracts-and-configuration.md`
- `04-copilot-sdk-current-state.md`
- `05-copilot-sdk-optimization-playbook.md`
- `07-open-questions-and-decision-register.md`

## Kiedy aktualizowac ten katalog

Aktualizuj `/pro` po kazdej wiekszej zmianie w jednym z tych obszarow:

- glowny runtime flow,
- shape evidence i pipeline providerow,
- kontrakt AI request/response,
- session-bound `ToolContext` i bridge Copilot SDK,
- skill, tools albo bridge Copilot SDK,
- konfiguracja `analysis.ai.copilot.*`,
- konfiguracja `analysis.database.*`,
- frontend operatorski i job API,
- decyzje o tym, co jest deterministic evidence, a co AI-guided exploration.
