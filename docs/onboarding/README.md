# Onboarding

Ten katalog jest nowa sciezka nauki projektu dla developera, ktory:

- zna Spring Boot i prace na kodzie backendowym,
- nie zna jeszcze GitHub Copilot Java SDK,
- nie zna Spring AI i warstwy tools/MCP,
- chce zrozumiec obecny stan systemu, a nie historie jego budowy.

## Jak czytac ten onboarding

To nie jest dokumentacja historyczna.
Kazdy krok opisuje aktualny model systemu i prowadzi od ogolnego obrazu do
bezpiecznego rozwijania feature'ow.

Najlepsza kolejnosc:

1. `01-current-system-purpose.md`
2. `02-core-vocabulary.md`
3. `03-package-map.md`
4. `04-entrypoints-and-user-flows.md`
5. `05-analysis-orchestration.md`
6. `06-evidence-pipeline.md`
7. `07-adapters-and-external-systems.md`
8. `08-gitlab-capabilities.md`
9. `09-spring-ai-and-mcp-tools.md`
10. `10-copilot-sdk-analysis-runtime.md`
11. `11-frontend-and-operator-workflow.md`
12. `12-testing-and-change-playbooks.md`

## Co czytac rownolegle

Te dokumenty sa nadal source of truth dla architektury:

1. `../architecture/01-system-overview.md`
2. `../architecture/02-key-decisions.md`
3. `../architecture/03-runtime-flow.md`
4. `../architecture/04-codex-continuation-guide.md`

Przy pracy runtime warto miec obok jeszcze:

- `../../src/main/resources/copilot/skills`
- `../../src/main/resources/operational-context`

## Minimalny tryb startowy

Jesli masz tylko 60-90 minut, przeczytaj najpierw:

1. `01-current-system-purpose.md`
2. `03-package-map.md`
3. `05-analysis-orchestration.md`
4. `06-evidence-pipeline.md`
5. `09-spring-ai-and-mcp-tools.md`

## Komendy startowe

- `mvn -q clean test`
- `cd frontend && npm test`
- `mvn -q -DskipTests package`
- `cd frontend && npm start`
- `cd frontend && npm run build`

Wazne:

- `mvn test` nie uruchamia testow Angulara,
- `mvn -q -DskipTests package` buduje tez frontend i kopiuje go do
  `src/main/resources/static`.

## Co ma byc efektem

Po przejsciu calej sciezki powinienes umiec:

- znalezc w kodzie glowny flow analizy po `correlationId`,
- odroznic adapter, provider evidence, tool i provider AI,
- odroznic finalna analize od follow-up chatu po zakonczonym jobie,
- zrozumiec, jak Copilot reuse'uje Spring tools,
- bezpiecznie dodac nowy krok evidence, nowy adapter albo nowy tool,
- ocenic, w ktorym module zmiana powinna wyladowac.
