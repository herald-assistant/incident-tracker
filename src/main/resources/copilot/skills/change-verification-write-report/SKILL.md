---
name: change-verification-write-report
description: Finalizuje Change Verification jako osobne sekcje AnalysisReport, zapisuje meta, waliduje raport i zwraca fallback JSON.
---

# Change Verification Write Report

## Cel

Zapisz finalne aktywne sekcje compliance przez report tools i potwierdz
kompletnosc raportu. Ten skill jest jedynym wlascicielem finalnego formatu
initial compliance report.

Nie podawaj `reportId` w argumentach tooli. Backend przekazuje go przez hidden
`ToolContext`.

## Wejscia

Przyjmij od orkiestratora:

- `RequirementLedger`,
- aktywne drafty `StoryComplianceSectionDraft` i
  `InstructionComplianceSectionDraft`,
- findings i suggested actions,
- references, visibility limits, open questions, gaps, warnings i confidence,
- flagi `checkStoryCompliance` i `checkInstructionCompliance`.

## Readiness Gate

Przed zapisem potwierdz:

- kazde jawne acceptance criterion ma check,
- przeanalizowano inne pola Jira i powiazany kontekst pod katem dodatkowych
  wymagan,
- inferred requirements sa jawnie oznaczone,
- kazdy aktywny instruction source majacy zastosowanie ma check albo limit,
- mocne twierdzenia maja source refs,
- drafty nie maja statusu `needs_deeper_evidence`.

Gdy rozstrzygalnego materialu brakuje, zwroc orkiestratorowi:

```text
status: not_ready
missingArtifact: <ledger albo draft>
neededFor: STORY_COMPLIANCE | INSTRUCTION_COMPLIANCE | report_meta
suggestedSkill: <skill sekcyjny albo compliance-check>
minimumNextQuestion: <jedno waskie pytanie evidence>
reason: <dlaczego raport bylby zbyt plytki>
```

Po jednym targeted retry nierozstrzygalny brak zamien na
`visibility_limited`.

## Zapis Raportu

1. Dla `checkStoryCompliance=true` wywolaj `report_upsert_section`:
   - `id=STORY_COMPLIANCE`,
   - `title=Story compliance`,
   - `order=0`,
   - szczegolowy Markdown i section meta.
2. Dla `checkInstructionCompliance=true` wywolaj `report_upsert_section`:
   - `id=INSTRUCTION_COMPLIANCE`,
   - `title=Instruction compliance`,
   - `order=1`,
   - szczegolowy Markdown i section meta.
3. Wywolaj `report_update_header` z krotkim summary statusu.
4. Wywolaj `report_update_meta` z globalnymi references, visibility limits,
   open questions, gaps, confidence i warnings.
5. Wywolaj `report_get_current`.
6. Sprawdz, czy kazda aktywna sekcja istnieje, ma niepusty Markdown i meta.

Kazda sekcja musi zaczynac sie od `## Krotkie podsumowanie weryfikacji`, a
nastepnie zawierac `## Szczegolowy raport`. Checki maja pokazywac zrodlo,
cytat, `interpretationType`, konkretne oczekiwanie, status, proof z kodu/MR,
refs, gaps i suggested action.

## Finalna Odpowiedz

Po poprawnym `report_get_current` zwroc dokladnie jeden obiekt JSON zgodny z
`change-verification/response-contract.md`. Jest to fallback diagnostyczny i
wejscie do dalszej fazy smoke pack; zrodlem prawdy dla UI sa zapisane sekcje
`AnalysisReport`.

Gdy report tools nie sa dostepne albo zapis sie nie powiedzie, nadal zwroc
pelny JSON. Nie zwracaj opisu przed ani po JSON.

## Walidacja Jakosci

- Jawne AC nie sa jedynym analizowanym materialem.
- Wymagania inferowane nie sa przedstawione jako literalne.
- Parent, subtaski i Confluence nie rozszerzaja po cichu target scope.
- `FAILED` oznacza widoczny rozjazd, a brak proof oznacza `NOT_VERIFIED`.
- Report meta pokazuje refs, gaps, open questions i visibility limits.
- `report_get_current` potwierdza finalny zapis.

## Antywzorce

Nie:

- zapisuj jednego zbiorczego eseju zamiast osobnych sekcji,
- pomijaj cytatow i source refs,
- uznawaj braku dowodu za dowod niezgodnosci,
- ukrywaj dedukcji z nieprecyzyjnego materialu,
- koncz bez `report_get_current`, gdy report tools sa dostepne.
