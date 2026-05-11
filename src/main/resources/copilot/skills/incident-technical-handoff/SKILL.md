---
name: incident-technical-handoff
description: Enterprise-ready Markdown contract for generating repeatable, evidence-grounded technical handoffs for developers, QA, DevOps, data owners, partner teams, or another receiving team during incident follow-up chat.
---

# Incident Technical Handoff Skill

Use this skill when the user asks for a handoff, developer handoff, bug report,
ticket text, przekazanie, zgloszenie, raport dla developera, raport dla QA,
raport dla DevOps, przekazanie do innego zespolu, or any equivalent request
whose purpose is to give another technical person or team a ready-to-act result.

The frontend renders chat answers as Markdown, so the output must be direct,
well-structured Markdown. Do not wrap the handoff in JSON unless the user
explicitly asks for JSON.

## Goal

Produce a repeatable `Technical Handoff v1` document that another technical
person can use without re-reading the whole analysis.

The handoff must help the receiver answer:

- what happened,
- where it happened,
- what is confirmed,
- what is a hypothesis,
- what code, data, integration, runtime, or ownership area is involved,
- what concrete action is expected from the receiving person or team,
- how to verify that the issue is fixed or correctly routed.

## Mandatory quality bar

Every handoff must be:

- evidence-grounded,
- concrete enough to start implementation or verification,
- readable by a junior or mid-level technical person,
- stable in section order and naming,
- explicit about missing visibility,
- clear about the intended receiver and first action,
- free from unsupported ownership or root-cause claims.

If a value is missing, do not remove the section. Use one of:

- `Nie ustalono`
- `Nie dotyczy`
- `Brak danych w evidence`
- `Hipoteza, wymaga potwierdzenia`

## Receiver profile

Infer the receiver from the latest user message.

Use `Developer` when the user says:

- developer,
- dev,
- programista,
- poprawka,
- fix,
- bug,
- code change,
- PR.

Use `QA / Tester` when the user says:

- QA,
- tester,
- testy,
- scenariusz testowy,
- reprodukcja,
- regresja.

Use `DevOps / Platform` when the user says:

- DevOps,
- platform,
- deployment,
- pod,
- container,
- namespace,
- image,
- metrics,
- infrastructure,
- runtime.

Use `Data / DBA` when the user says:

- DBA,
- dane,
- DB,
- baza,
- tabela,
- rekord,
- migracja danych,
- referencja,
- slownik.

Use `Partner / Other Team` when the request is about another system, downstream,
upstream, external party, or ownership handoff.

If the receiver is not clear, use `Technical receiver` and state that the
target team is not fully established.

## Evidence rules

Use the completed incident artifacts, final analysis result, previous tool
evidence, chat history, and any new tool results requested by the user.

You may use tools only when the current handoff would otherwise miss a concrete
required detail and the needed capability is available in the session.

Use Operational Context tools for ownership, handoff rules, process, bounded
context, system, repository scope, and receiving team details.

Use GitLab tools for file, class, method, repository, predicate, call flow, or
code-change details.

Use Database tools for confirmed data state, missing rows, wrong predicates,
or DB verification targets.

Use Elasticsearch tools for additional log timing, stacktrace, frequency, or
request evidence.

Do not use catalog context as proof of root cause. It can support routing,
ownership, code-search scope, process vocabulary, and handoff readiness.

## Fact versus hypothesis language

Use direct language only for claims supported by evidence:

- `Potwierdzone: ...`
- `Evidence wskazuje, ze ...`
- `Logi pokazuja ...`
- `Kod w ... pokazuje ...`

Use hypothesis language when evidence is incomplete:

- `Hipoteza: ...`
- `Najbardziej prawdopodobne wyjasnienie: ...`
- `Wymaga potwierdzenia w ...`
- `Nie potwierdzono bezposrednio ...`

Never present inferred ownership, inferred DB state, inferred external-system
failure, or inferred code behavior as confirmed fact.

## Output format

Use exactly this top-level structure, in this order.

````markdown
# Technical Handoff v1: <short technical title>

## 1. Odbiorca i cel przekazania

| Pole | Wartosc |
|---|---|
| Profil odbiorcy | <Developer / QA / DevOps / Data / Partner / Technical receiver> |
| Sugerowany odbiorca | <team/person/system owner or Nie ustalono> |
| Cel przekazania | <one sentence: implement fix / verify data / validate runtime / route to partner> |
| Pierwsza oczekiwana akcja | <one concrete action> |

## 2. Streszczenie

<3-6 short sentences in Polish. Include symptom, impact, likely cause or
hypothesis, and next action.>

## 3. Priorytet / Severity

**Severity:** <niski / sredni / wysoki / krytyczny>

**Uzasadnienie:** <impact, determinism, affected users/flow, workaround,
visibility, urgency>

## 4. Srodowisko i zakres wykrycia

| Parametr | Wartosc |
|---|---|
| Srodowisko | <environment or Nie ustalono> |
| Branch | <gitLabBranch or Nie ustalono> |
| GitLab group | <gitLabGroup or Nie ustalono> |
| Namespace / runtime | <namespace/runtime or Nie ustalono> |
| Pod / instancja | <pod/instance or Nie ustalono> |
| Kontener / service | <container/service or Nie ustalono> |
| Commit / image | <commit/image or Nie ustalono> |
| correlationId / requestId | <id or Nie ustalono> |
| Czas zdarzenia | <timestamp/window or Nie ustalono> |

## 5. Objawy i wplyw

- **Objaw:** <what the operator sees>
- **Wplyw:** <blocked endpoint/job/process/capability>
- **Powtarzalnosc:** <confirmed/reproducible/unknown>
- **Zakres:** <single request/customer/environment/all known cases/unknown>
- **Obejscie:** <known workaround or Brak danych w evidence>

## 6. Dokladna lokalizacja techniczna

| Element | Lokalizacja |
|---|---|
| Repozytorium | <repo or Nie ustalono> |
| Modul / aplikacja | <module/app or Nie ustalono> |
| Plik | <file path or Nie ustalono> |
| Klasa / metoda | <class/method or Nie ustalono> |
| Linie | <lines or Nie ustalono> |
| Endpoint / event / job | <entry point or Nie ustalono> |
| Powiazane wywolania | <important callers/callees or Nie ustalono> |

## 7. Przyczyna zrodlowa albo najlepsza hipoteza

**Status:** <Potwierdzone / Hipoteza / Nie ustalono>

<Explain the root cause or best hypothesis. State why it happens here and what
condition triggers it. If root cause is not fully proven, say exactly what is
missing.>

## 8. Dowody

| Dowod | Zrodlo / ID | Co potwierdza |
|---|---|---|
| <artifact/tool/log/code/db/opctx> | <artifactId/itemId/tool result> | <claim supported by this evidence> |

## 9. Przeplyw wykonania

```text
<entry point>
  -> <controller/listener/job/client>
    -> <service/facade>
      -> <repository/integration/runtime component>
        -> <failure point>
```

If the flow is not established, write a short limitation instead of inventing
missing steps.

## 10. Proponowana poprawka albo oczekiwane dzialanie

**Rekomendacja:** <the preferred action>

**Dlaczego ten wariant:** <short evidence-based justification>

**Zakres zmiany / dzialania:**
- <file/data/runtime/config/integration action>

**Alternatywy:**
- <optional safer/defensive/refactor alternative or Nie dotyczy>

## 11. Testy i weryfikacja

| Typ weryfikacji | Co sprawdzic |
|---|---|
| Unit test | <specific unit test or Nie dotyczy> |
| Integration/API test | <specific integration test or Nie dotyczy> |
| DB/data check | <specific data check or Nie dotyczy> |
| Runtime/log verification | <specific log/metric/pod check or Nie dotyczy> |
| Manual regression | <scenario or Nie dotyczy> |

## 12. Ryzyka i skutki uboczne

- <risk 1>
- <risk 2>
- <Nie ustalono if no risk is visible>

## 13. Ograniczenia diagnostyki

- <missing DB/runtime/GitLab/log/downstream visibility>
- <unverified assumption>
- <scope limitation>

## 14. Definition of Done

- <fix/action implemented or routed>
- <tests/checks passed>
- <incident scenario verified>
- <handoff receiver confirms ownership or redirects with reason>

## 15. Referencje

| Artefakt | ID / lokalizacja | Rola |
|---|---|---|
| <artifact/tool/code/log/db/opctx> | <id/path> | <how it was used> |
````

## Section guidance

### Title

The title should identify the symptom and likely failing area.

Good:

- `ClassCastException: LocalDateTime -> LocalDate w MisFactoringRepositoryImpl`
- `EntityNotFoundException przy pobieraniu aktywnego limitu klienta`
- `Timeout downstream podczas synchronizacji statusu platnosci`

Bad:

- `Problem w aplikacji`
- `Blad`
- `Do sprawdzenia`

### Summary

The summary must be understandable without reading stacktraces.

Mention:

- what failed,
- where it failed,
- what was affected,
- whether the cause is confirmed or a hypothesis,
- what the receiver should do first.

### Severity

Choose severity by impact and reproducibility:

- `krytyczny`: broad outage, data loss/corruption risk, security risk, or
  critical process fully unavailable.
- `wysoki`: deterministic blocker for a significant endpoint/process or no
  practical workaround.
- `sredni`: partial degradation, limited scope, workaround exists, or issue
  affects non-critical flow.
- `niski`: cosmetic, rare, or low operational impact.

Do not inflate severity without evidence.

### Environment table

Prefer exact values from the incident session:

- `environment`,
- `gitLabBranch`,
- `gitLabGroup`,
- namespace,
- pod,
- container,
- commit,
- image,
- correlationId,
- timestamp.

Use `Nie ustalono` for missing fields.

### Technical location

For developer handoff, this section should be specific enough to open the
right file and method.

For QA, DevOps, Data or Partner handoff, keep the section but emphasize the
entry point, affected service, runtime object, data object, external system,
or verification target.

### Root cause

Explain the mechanism, not only the exception.

Good:

- `Kod zaklada LocalDate, ale natywne zapytanie Oracle DATE zwraca LocalDateTime.`
- `Rekord istnieje, ale nie spelnia predykatu status=ACTIVE uzywanego przez repozytorium.`

Bad:

- `Jest ClassCastException.`
- `Baza zwraca blad.`

### Evidence

Each important claim should have at least one supporting evidence row when
available.

Use artifact IDs, item IDs, tool result IDs, file paths, class names, log IDs,
or DB check names.

Do not paste huge raw logs or full files. Quote only short identifiers and
summarize the evidence.

### Proposed action

For Developer:

- specify file/method,
- expected code change,
- edge cases,
- tests.

For QA:

- specify reproduction path,
- input data,
- expected result,
- negative/regression cases.

For DevOps:

- specify namespace/pod/service/image/config/metric/log check,
- expected runtime signal,
- rollback or redeploy condition if grounded.

For Data / DBA:

- specify schema/application scope only if verified,
- table/column/key/predicate to check,
- masking/readonly expectations,
- data correction or ownership check.

For Partner / Other Team:

- specify system/integration,
- evidence package,
- exact question or action for receiver,
- expected reply or confirmation.

### Tests

Prefer concrete tests over generic "add tests".

Examples:

- `MisFactoringRepositoryTest` should cover `LocalDateTime`, `LocalDate`, and `null`.
- API test for `GET /...` returns `200` instead of `500` for the reproduced input.
- DB check confirms row exists and satisfies the full repository predicate.
- Log verification confirms the correlationId no longer emits the same exception.

### Diagnostic limitations

Always include what was not verified.

Examples:

- `Brak bezposredniego dostepu do bazy MIS.`
- `Dynatrace nie zwrocil danych dla srodowiska dev.`
- `Nie potwierdzono, czy problem wystepuje na wyzszych srodowiskach.`
- `Nie znaleziono pelnego upstream flow w dostepnym GitLab scope.`

## Short handoff mode

If the user explicitly asks for a short handoff, keep the same meaning but use
this compact structure:

```markdown
# Technical Handoff v1: <title>

## Cel i odbiorca
...

## Co sie dzieje
...

## Gdzie poprawic / sprawdzic
...

## Evidence
...

## Oczekiwana akcja
...

## Weryfikacja
...

## Ograniczenia
...
```

Do not use short mode unless the user explicitly asks for a short, concise, or
TL;DR handoff.

## Anti-patterns

Do not:

- output an unstructured narrative when a handoff is requested,
- skip sections because data is missing,
- invent a team or owner,
- claim DB state without DB evidence,
- claim external-system failure without downstream/runtime evidence,
- paste long stacktraces or large code blocks unless the user asks,
- return JSON for normal chat handoff,
- merge facts and hypotheses in one ambiguous sentence,
- write a handoff that only a senior developer who knows the system can use.
