---
name: change-verification-orchestrator
description: Orkiestruje Change Verification - buduje ledger wymagan, uruchamia aktywne skille sekcyjne, wykonuje readiness gate i przekazuje wynik do write-report.
---

# Change Verification Orchestrator

## Cel

Poprowadz initial Change Verification od materialu zrodlowego do kompletnego
raportu. Nie jestes wlascicielem tresci sekcji ani finalnego formatu raportu.

## Wejscia

Zacznij od artefaktow `change-verification/*` osadzonych w promptcie. Target
issue jest glownym zakresem. Parent, subtaski i Confluence sa materialem
kontekstowym, chyba ze target issue jawnie wlacza ich fragment do swojego
zakresu.

## Algorytm

1. Zaladuj `change-verification-compliance-check`.
2. Zbuduj `RequirementLedger` obejmujacy:
   - kazde jawne acceptance criterion,
   - wymagania z opisu i komentarzy,
   - reguly z aktywnych instrukcji repozytorium,
   - od zera do pieciu osobnych `INFERRED_CRITICAL` checks wynikajacych z
     konkretnych sygnalow Jira, Confluence, MR, kodu albo operational context.
3. Dla `checkStoryCompliance=true` zaladuj
   `change-verification-story-compliance-section`.
4. Dla `checkInstructionCompliance=true` zaladuj
   `change-verification-instruction-compliance-section`.
5. Dla `checkStoryCompliance=true` zaladuj
   `change-verification-inferred-critical-checks-section`.
6. Po kazdej sekcji wykonaj readiness gate.
7. Gdy brak jest rozstrzygalny jednym focused odczytem GitLab albo Operational
   Context, wykonaj go i ponow tylko odpowiednia czesc analizy.
8. Gdy brak nie jest rozstrzygalny, oznacz go jako `visibility_limited`.
9. Zaladuj `change-verification-write-report` i przekaz mu ledger, aktywne
   drafty sekcji, findings, actions, source refs, gaps, open questions,
   visibility limits i confidence.
10. Przed handoffem usun z ledgeru, findings, gaps, open questions i actions
   wpisy utworzone wylacznie z limitow `source-discovery-limits` albo
   `instruction-source-limits`. Takie wpisy zachowaj tylko jako
   `visibilityLimits`.

## Readiness Gate

Status kazdego materialu ustaw jako:

- `ready`,
- `needs_deeper_evidence`,
- `visibility_limited`,
- `not_applicable`.

Nie przekazuj sekcji do write-report, gdy ma status
`needs_deeper_evidence`. Dopuszczalny jest jeden targeted retry dla konkretnej
luki. Po nim zapisz jawny limit widocznosci zamiast petlic albo zgadywac.

Przed write-report potwierdz:

- target issue pozostaje glownym zakresem,
- kazde jawne acceptance criterion ma osobny check,
- opis, komentarze, Confluence i implementacja zostaly przejrzane pod katem
  brakujacych kontroli release-critical,
- istnienie jawnego AC nie zatrzymalo dalszej dedukcji z innych zrodel,
- kazdy check ma `interpretationType`,
- kazdy `INFERRED_CRITICAL` check ma `interpretationType=inferred`, maksymalnie
  piec pozycji, konkretne `inferenceSignals`, `inferenceRationale`,
  `riskIfOmitted` i `confidence`,
- zadna kontrola `INFERRED_CRITICAL` nie znajduje sie w Story Compliance ani
  Instruction Compliance,
- instrukcje zostaly powiazane z plikami albo elementami zmiany, do ktorych
  maja zastosowanie,
- mocne twierdzenia maja source refs,
- sprzecznosci i braki widocznosci nie zostaly ukryte.
- limity techniczne platformy nie zostaly przedstawione jako wymagania,
  checki, findings ani problemy projektu.

## Handoff

Przekaz do `change-verification-write-report`:

```text
RequirementLedger
StoryComplianceSectionDraft?
InstructionComplianceSectionDraft?
InferredCriticalChecksSectionDraft?
findings
suggestedActions
references
visibilityLimits
openQuestions
gaps
warnings
confidence
```

## Antywzorce

Nie:

- zapisuj finalnych sekcji samodzielnie,
- traktuj acceptance criteria jako zamknietej listy wymagan,
- zamieniaj parenta albo calego Confluence w zakres target issue,
- ukrywaj inferencji pod etykieta `explicit`,
- koncz bez `change-verification-write-report`.
