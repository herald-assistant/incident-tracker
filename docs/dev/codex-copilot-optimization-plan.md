# Codex Copilot Optimization Plan

## Cel dokumentu

Ten dokument zapisuje baseline repo przed seria prac nad optymalizacja GitHub
Copilot Java SDK. Ten PR ma pozostac dokumentacyjny i nie zmienia runtime
aplikacji.

## Przeczytany kontekst

- `AGENTS.md`
- `docs/architecture/01-system-overview.md`
- `docs/architecture/02-key-decisions.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/architecture/04-codex-continuation-guide.md`
- `docs/onboarding/README.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/AGENTS.md`
- `pro/README.md`
- `pro/01-system-and-product-context.md`
- `pro/02-architecture-and-code-map.md`
- `pro/03-runtime-contracts-and-configuration.md`
- `pro/04-copilot-sdk-current-state.md`
- `pro/05-copilot-sdk-optimization-playbook.md`
- `pro/06-functional-and-technical-optimization-backlog.md`
- `pro/07-open-questions-and-decision-register.md`
- `pro/08-gpt-pro-workshop-guide.md`

Kodowo sprawdzone zostaly obecne klasy Copilot AI:

- `CopilotSdkAnalysisAiProvider`
- `CopilotSdkPreparationService`
- `CopilotArtifactService`
- `CopilotSdkPreparedRequest`
- `CopilotSdkProperties`
- `CopilotToolAccessPolicy`
- `CopilotSkillRuntimeLoader`
- `CopilotSdkExecutionGateway`
- `CopilotSdkToolBridge`
- `CopilotToolEvidenceCaptureRegistry`

## Baseline runtime finding

Aktualny kod uzywa embedded prompt artifacts, a nie SDK attachments.

Potwierdzenie:

- `CopilotArtifactService` renderuje manifest i sekcje evidence jako logiczne
  artefakty w pamieci.
- Manifest ustawia `artifactPolicy.deliveryMode` na `embedded-prompt`.
- `CopilotSdkPreparationService` wstawia liste artefaktow oraz pelne
  `Embedded artifact contents` bezposrednio do promptu.
- `CopilotSdkPreparationService` tworzy `MessageOptions` przez
  `new MessageOptions().setPrompt(prompt)`.
- `CopilotSdkPreparedRequest` przechowuje `artifactContents` jako mape
  diagnostyczna/testowa i nie sprzata zadnych plikow runtime.
- `CopilotSdkPreparationServiceTest` asercyjnie sprawdza, ze
  `messageOptions().getAttachments()` jest `null` albo puste.
- W aktualnych `CopilotSdkProperties` nie ma juz pola
  `attachment-artifact-directory`.

Wniosek dla kolejnych PR:

- planowanie optymalizacji powinno traktowac inline prompt artifacts jako stan
  bazowy,
- powrot do SDK attachments albo mieszany delivery mode bylby zmiana runtime,
  wymagajaca osobnego PR, testow przygotowania sesji i aktualizacji
  architektury.

## Planowana sekwencja PR

### PR 0: baseline dokumentacyjny

Zakres:

- zapisac invarianty w root `AGENTS.md`,
- zapisac baseline Copilot optimization plan,
- potwierdzic delivery mode artefaktow,
- uruchomic `mvn -q test`.

Status:

- ten dokument opisuje PR 0,
- bez zmian runtime.

### PR 1: telemetry sesji Copilota i tooli

Cel:

- wprowadzic mierzalny baseline kosztu, czasu i eksploracji przed zmianami
  zachowania.

Zakres:

- metryki liczby sekcji evidence i itemow,
- metryki liczby artefaktow i lacznego rozmiaru embedded content,
- liczniki tool calls per grupa capability,
- latency dla preparation, execution i tool invocations,
- structured log albo niewielki model telemetryczny per analiza.

### PR 2: twardszy kontrakt odpowiedzi AI

Cel:

- zmniejszyc ryzyko `AI_UNSTRUCTURED_RESPONSE`.

Zakres:

- przejsc na JSON-only albo fenced JSON contract,
- utrzymac fallback na tekstowy parser na czas migracji,
- zaktualizowac testy providera AI i promptu.

### PR 3: exploration budget i governance

Cel:

- ograniczyc kosztowne albo zbyt szerokie eksploracje GitLab i DB tools.

Zakres:

- soft budget warnings dla GitLab reads, DB queries, rows i chars,
- osobne liczniki dla raw SQL,
- pozniej hard limits w backendzie, jesli telemetry potwierdzi progi.

### PR 4: jeden flow przygotowania requestu

Cel:

- usunac podwojne budowanie promptu przez `preparePrompt(...)` i `prepare(...)`.

Zakres:

- wypracowac jeden wynik preparation z `preparedPrompt` i konfiguracja sesji,
- utrzymac obecny kontrakt UI/job snapshotu,
- bez zmiany tresci promptu poza mechaniczna deduplikacja.

### PR 5: lepszy deterministic context

Cel:

- zmniejszyc potrzebe AI-guided repo browsing.

Zakres:

- poprawic ranking stacktrace -> file/chunk,
- dolozyc najwazniejsze caller/callee context, jesli jest dostepny,
- mierzyc, czy spada liczba GitLab tool calls.

### PR 6: projekcja i audit wynikow tooli

Cel:

- pokazac operatorowi nie tylko finalny wynik, ale tez istotne fakty zdobyte
  przez tools.

Zakres:

- decyzja, ktore GitLab outline/search i DB tool results powinny trafic do UI,
- osobne sekcje dla potwierdzonych data facts,
- governance dla raw SQL i diagnostyki danych.

### PR 7: opcjonalny multi-stage AI flow

Cel:

- rozdzielic planowanie, dogrywanie brakow evidence i finalny write-up, jesli
  telemetry pokaze, ze pojedyncza sesja jest zbyt droga albo niestabilna.

Zakres:

- najpierw ADR/experiment memo,
- dopiero potem implementacja.

## Baseline test result

Data uruchomienia: `2026-04-26 01:19 Europe/Warsaw`

Komenda:

```powershell
mvn -q test
```

Wynik:

- exit code: `0`
- Surefire: `181` tests, `0` failures, `0` errors, `0` skipped
- Surefire reported time: `17.06 s`
- observed wall time: `17.4 s`

Uwagi:

- Logi testow zawieraja oczekiwane komunikaty z testow konfiguracji DB, np.
  brakujacy `missing.Driver`.
- Logi zawieraja ostrzezenie Mockito/Byte Buddy o dynamicznym agent loading na
  przyszlych JDK.
- Zadna z tych uwag nie powoduje bledu testow w baseline.
