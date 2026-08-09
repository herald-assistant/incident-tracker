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
- opcjonalny `InferredCriticalChecksSectionDraft`,
- findings i suggested actions,
- references, visibility limits, open questions, gaps, warnings i confidence,
- flagi `checkStoryCompliance` i `checkInstructionCompliance`.

## Readiness Gate

Przed zapisem potwierdz:

- kazde jawne acceptance criterion ma check,
- przeanalizowano inne pola Jira i powiazany kontekst pod katem dodatkowych
  wymagan,
- inferred critical checks sa w osobnym draftcie, maja komplet metadanych i
  nie przekraczaja limitu pieciu,
- kazdy aktywny instruction source majacy zastosowanie ma check albo limit,
- zaden check ani finding nie zostal utworzony z komunikatu platformy z
  `source-discovery-limits` albo `instruction-source-limits`,
- mocne twierdzenia maja source refs,
- drafty nie maja statusu `needs_deeper_evidence`.

Gdy rozstrzygalnego materialu brakuje, zwroc orkiestratorowi:

```text
status: not_ready
missingArtifact: <ledger albo draft>
neededFor: STORY_COMPLIANCE | INSTRUCTION_COMPLIANCE | INFERRED_CRITICAL_CHECKS | report_meta
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
3. Dla `checkStoryCompliance=true` wywolaj `report_upsert_section`:
   - `id=INFERRED_CRITICAL_CHECKS`,
   - `title=AI-suggested critical checks`,
   - kolejny `order` po aktywnych sekcjach source-defined compliance,
   - Markdown jawnie mowiacy, ze nie sa to wymagania kontraktowe.
4. Wywolaj `report_update_header` z krotkim summary statusu source-defined
   compliance. Inferred critical checks nie zmieniaja tego statusu.
5. Wywolaj `report_update_meta` z globalnymi references, visibility limits,
   open questions, gaps, confidence i warnings.
6. Wywolaj `report_get_current`.
7. Sprawdz, czy kazda aktywna sekcja istnieje, ma niepusty Markdown i meta.

Sekcje source-defined compliance musza miec human-first Markdown w kolejnosci:

1. `## Wynik weryfikacji` z werdyktem, licznikami i trzema najwazniejszymi
   wnioskami: potwierdzenie, ryzyko, dzialanie.
2. `## Wymaga uwagi` jako tabela niepotwierdzonych albo problematycznych
   kryteriow. Sekcja moze zostac pominieta, gdy nie ma takich pozycji.
3. `## Potwierdzone wymagania` jako zwarta tabela pozycji `PASSED`.
4. `## Szczegoly kryteriow` z pelnym cytatem, interpretacja i proof dla
   operatora, ktory chce wejsc glebiej.
5. `## Rekomendowane dzialania`, gdy istnieja konkretne dzialania.

Nie tworz z Markdown zrzutu pol kontraktu. W szczegolnosci nie wypisuj
mechanicznie `scope`, `criterionSource`, `criterionQuote`,
`interpretationType`, `verifiedAgainst`, `evidenceRefs`, `gaps` i
`suggestedAction`. Pelny kontrakt nadal musi znalezc sie w finalnym JSON.

Tabele musza byc skanowalne: krotkie komorki, bez dlugich cytatow, sciezek i
list references. References, visibility limits, open questions, gaps i
warnings zapisuj w section meta, aby UI prezentowal je jako zwijane informacje
na zadanie. Pomijaj puste wartosci, `[]`, `n/a`, `Brak` i puste sekcje.

Sekcja `INFERRED_CRITICAL_CHECKS` ma pokazac od zera do pieciu pozycji. Dla
kazdej pokaz kontrole, powod krytycznosci, sygnaly inferencji, status, ryzyko
pominiecia, rekomendowane dzialanie i confidence. Gdy lista jest pusta, napisz
krotko, ze na podstawie widocznego evidence AI nie zidentyfikowalo dodatkowej
kontroli krytycznej. Nie tworz sztucznych pozycji.

## Finalna Odpowiedz

Po poprawnym `report_get_current` zwroc dokladnie jeden obiekt JSON zgodny z
`change-verification/response-contract.md`. Jest to fallback diagnostyczny;
zrodlem prawdy dla UI sa zapisane sekcje `AnalysisReport`.

Gdy report tools nie sa dostepne albo zapis sie nie powiedzie, nadal zwroc
pelny JSON. Nie zwracaj opisu przed ani po JSON.

## Walidacja Jakosci

- Jawne AC nie sa jedynym analizowanym materialem.
- Inferred critical checks nie sa przedstawione jako literalne wymagania i nie
  zmieniaja statusu source-defined compliance.
- Parent, subtaski i Confluence nie rozszerzaja po cichu target scope.
- `FAILED` oznacza widoczny rozjazd, a brak proof oznacza `NOT_VERIFIED`.
- Limity discovery platformy sa wylacznie `visibilityLimits`; nie zmieniaja
  statusu compliance i nie sa najwazniejszym ryzykiem, checkiem, findingiem,
  gapem, pytaniem ani rekomendacja dla zespolu.
- Report meta pokazuje refs, gaps, open questions i visibility limits.
- Najwazniejsze rozjazdy sa widoczne przed pozycjami potwierdzonymi.
- Markdown nie wymaga znajomosci nazw pol kontraktu JSON.
- `report_get_current` potwierdza finalny zapis.

## Antywzorce

Nie:

- zapisuj jednego zbiorczego eseju zamiast osobnych sekcji,
- pomijaj cytatow i source refs,
- uznawaj braku dowodu za dowod niezgodnosci,
- przedstawiaj limit platformy jako kryterium lub problem projektu,
- ukrywaj dedukcji z nieprecyzyjnego materialu,
- koncz bez `report_get_current`, gdy report tools sa dostepne.
