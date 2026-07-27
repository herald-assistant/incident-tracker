---
name: change-verification-story-compliance-section
description: Buduje szczegolowa sekcje STORY_COMPLIANCE z jawnych i inferowanych wymagan Jira oraz kontekstu Confluence.
---

# Change Verification Story Compliance Section

## Cel

Zbuduj `StoryComplianceSectionDraft` na podstawie `RequirementLedger`.
Oceniaj target issue. Parent, subtaski i Confluence wykorzystuj do
interpretacji, zaleznosci i ryzyk, a nie jako automatyczne rozszerzenie scope.

## Wymagana Struktura

Sekcja musi zawierac:

1. `Krotkie podsumowanie weryfikacji`:
   - laczny status,
   - najwazniejszy potwierdzony rezultat,
   - najwazniejszy rozjazd albo limit widocznosci.
2. `Szczegolowy raport`:
   - osobny blok dla kazdego jawnego acceptance criterion,
   - osobne bloki dla wymagan z opisu i komentarzy,
   - osobne bloki dla uzasadnionych wymagan inferowanych z kontekstu.
3. `Rozbieznosci i rekomendacje`:
   - konkretna korekta kodu, story albo kryterium,
   - pytania wymagajace decyzji ownera.

## Zasady Interpretacji

- `explicit`: wymaganie zapisane wprost.
- `normalized`: znaczenie zapisane nieprecyzyjnie, ale uporzadkowane bez
  dodawania nowego obowiazku.
- `inferred`: dodatkowe wymaganie wyprowadzone z kilku sygnalow. Zawsze
  napisz, ze jest inferowane i dlaczego.
- `conflicting`: zrodla daja sprzeczne oczekiwania.
- `not_verifiable`: nie ma wystarczajacego proof w widocznym kodzie lub MR.

Acceptance criteria sa najsilniejszym sygnalem, ale nie zwalniaja z analizy
opisu, komentarzy, powiazanego fragmentu Confluence i zachowania widocznego w
kodzie. Material pisany niestarannie normalizuj ostroznie. Nie poprawiaj
intencji autora po cichu.

## Kazdy Check

Kazdy check musi miec:

- `id`,
- `scope=STORY_COMPLIANCE`,
- `criterionSource`,
- `criterionQuote`,
- `interpretationType`,
- `expectedCriterion`,
- `verificationStatus`,
- `verifiedAgainst`,
- `analysis`,
- `evidenceRefs`,
- `gaps`,
- `suggestedAction`.

## Readiness

Zwroc `needs_deeper_evidence`, gdy konkretny odczyt kodu moze rozstrzygnac
status checka. Zwroc `visibility_limited`, gdy dalszy proof nie jest dostepny.
Sekcja jest `ready` dopiero, gdy wszystkie jawne AC maja check i wymagania
inferowane sa wyraznie oznaczone.

## Handoff

Przekaz orkiestratorowi:

```text
StoryComplianceSectionDraft
storyChecks
storyFindings
references
visibilityLimits
openQuestions
gaps
warnings
confidence
readiness
```

Nie wywoluj report tools. Finalny zapis nalezy do
`change-verification-write-report`.
