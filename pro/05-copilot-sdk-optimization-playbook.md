# Copilot SDK Optimization Playbook

Ten playbook opisuje, jak dalej optymalizowac Copilota po wdrozonej bazie
PR-1..PR-8.

## Co jest juz wdrozone

Baseline optymalizacyjny:

- telemetry i session metrics,
- JSON-only response contract,
- response parser zachowujacy czesciowo sparsowane pola,
- report-only quality gate,
- evidence coverage evaluator,
- coverage-aware tool policy,
- incident digest,
- stable artifact `itemId`,
- evidence references w kontrakcie JSON,
- backend-enforced tool budget,
- tool description decorators,
- extended tool evidence capture,
- single prepared analysis flow.

To oznacza, ze kolejne optymalizacje powinny bazowac na metrykach, a nie na
samym intuicyjnym tuningu promptu.

## Najwazniejsze cele produktu

Wynik Copilota ma:

- wskazac problem,
- oddzielic fakty od hipotez,
- wyjasnic affected function jako flow, a nie tylko klase lub wyjatek,
- dac konkretny next step dla operatora/testera/developera,
- ujawnic visibility limits,
- odwolywac sie do evidence przez `artifactId` i `itemId`, gdy to mozliwe.

## Jak czytac metryki

Najpierw patrzec na:

- `totalExecutionDurationMs`,
- `sendAndWaitDurationMs`,
- `totalToolCalls`,
- `gitLabReadFileCalls`,
- `gitLabReadChunkCalls`,
- `gitLabReturnedCharacters`,
- `databaseQueryCalls`,
- `databaseRawSqlCalls`,
- `databaseReturnedCharacters`,
- `fallbackResponseUsed`,
- quality findings.

Interpretacja:

- duzo GitLab calls + plytkie `affectedFunction` oznacza problem coverage albo
  zle seedowanie tools,
- brak GitLab calls + plytkie `affectedFunction` oznacza zbyt agresywna policy
  albo brak gap w coverage,
- DB calls przy braku data issue oznaczaja zbyt szeroki `DataDiagnosticNeed`,
- fallback JSON oznacza problem kontraktu promptu albo model response.

## Nastepne dzwignie

1. Golden evaluation suite

   Zbudowac zestaw przykladow z oczekiwanym shape odpowiedzi, expected evidence
   references i akceptowalnymi next steps. To powinno byc glowne narzedzie
   oceny zmian promptu/policy.

2. Metrics dashboard albo raport offline

   Logi telemetryczne trzeba agregowac po analysis run i porownywac koszt,
   latency, fallback rate oraz quality findings.

3. Budget tuning

   Domyslne limity sa konserwatywnym baseline. Po kilku realnych analizach
   stroic osobno GitLab search, chunk calls, DB calls i returned characters.

4. Soft repair

   Quality gate moze w kolejnym kroku uruchomic druga, krotka runde modelu:
   "previous response failed these checks, return corrected JSON only".
   Nie mieszac tego z parserem ani tool execution.

5. UI dla audytu

   Pokazac tool evidence, budget warnings, quality findings i evidence
   references w job UI w sposob czytelny dla operatora.

6. Permission hardening

   Rozwazyc ostrzejszy default permission mode po potwierdzeniu, ze session
   hooks i allowlista tools pokrywaja wszystkie flow.

7. Model/routing experiments

   Dopiero po metrykach i golden eval porownywac modele, reasoning effort i
   ewentualne multi-stage flows.

## Zasady zmian promptu

- Nie wracac do legacy labeled response.
- Nie usuwac JSON-only instruction bez testow parsera/provider.
- Nie dodawac danych incydentu do skill files.
- Manifest i digest maja byc czytane przed raw evidence.
- Prompt powinien wymagac evidence references dla kluczowych claimow, ale
  tolerowac puste references, gdy evidence nie ma itemId.

## Zasady zmian tools

- Tools wlaczac przez coverage gaps, nie przez sam fakt rejestracji.
- Nie dawac modelowi scope'ow, ktore sa w hidden `ToolContext`.
- Drogi tool musi miec budget i guidance.
- Wynik diagnostycznie waznego toola powinien trafiec do
  `toolEvidenceSections`.
- Raw SQL wymaga osobnej decyzji, property i audytu.

## Zasady zmian artifactow

- Nie zakladac lokalnych sciezek.
- Nie przechodzic na SDK attachments bez jawnej decyzji.
- Zachowac `00-incident-manifest.json` i `01-incident-digest.md` jako pierwsze.
- Nie zmieniac publicznego `AnalysisEvidenceItem` tylko po to, zeby dodac
  `itemId`; item IDs sa artifact-only.

## Kolejnosc rekomendowana od teraz

1. Dodac golden tests/eval fixtures dla representative incidents.
2. Zrobic dashboard/raport z telemetry logs.
3. Dostosowac budget thresholds na podstawie danych.
4. Dodac UI projection dla quality/budget/tool traces/evidence refs.
5. Wprowadzic soft repair jako osobny etap.
6. Rozwazyc stricter permission mode.
7. Dopiero potem eksperymentowac z attachments, multi-agent planning albo
   model routing.
