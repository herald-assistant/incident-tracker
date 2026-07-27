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
   - wymagania inferowane z Jira, Confluence i widocznego kodu,
   - reguly z aktywnych instrukcji repozytorium.
3. Dla `checkStoryCompliance=true` zaladuj
   `change-verification-story-compliance-section`.
4. Dla `checkInstructionCompliance=true` zaladuj
   `change-verification-instruction-compliance-section`.
5. Po kazdej sekcji wykonaj readiness gate.
6. Gdy brak jest rozstrzygalny jednym focused odczytem GitLab albo Operational
   Context, wykonaj go i ponow tylko odpowiednia czesc analizy.
7. Gdy brak nie jest rozstrzygalny, oznacz go jako `visibility_limited`.
8. Zaladuj `change-verification-write-report` i przekaz mu ledger, aktywne
   drafty sekcji, findings, actions, source refs, gaps, open questions,
   visibility limits i confidence.

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
- opis, komentarze i Confluence zostaly przejrzane pod katem wymagan
  inferowanych,
- istnienie jawnego AC nie zatrzymalo dalszej dedukcji z innych zrodel,
- kazdy check ma `interpretationType`,
- kazda inferencja ma `interpretationType=inferred` i wskazane zrodlo,
- instrukcje zostaly powiazane z plikami albo elementami zmiany, do ktorych
  maja zastosowanie,
- mocne twierdzenia maja source refs,
- sprzecznosci i braki widocznosci nie zostaly ukryte.

## Handoff

Przekaz do `change-verification-write-report`:

```text
RequirementLedger
StoryComplianceSectionDraft?
InstructionComplianceSectionDraft?
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
