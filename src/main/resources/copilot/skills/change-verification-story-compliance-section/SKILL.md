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

1. `Wynik weryfikacji`:
   - laczny status i liczby: potwierdzone / wymagajace uwagi / wszystkie,
   - jedno najwazniejsze potwierdzenie,
   - jedno najwazniejsze ryzyko albo limit,
   - jedno rekomendowane dzialanie.
2. `Wymaga uwagi`:
   - tabela tylko dla `FAILED`, `WARNING`, `NOT_VERIFIED`, `conflicting`
     i innych niejednoznacznych statusow,
   - kolumny: `Status | Kryterium | Wniosek | Rekomendowane dzialanie`,
   - najpowazniejsze pozycje jako pierwsze.
3. `Potwierdzone wymagania`:
   - zwarta tabela dla `PASSED`,
   - kolumny: `Wymaganie | Zrodlo | Co potwierdzono | Status`.
4. `Szczegoly kryteriow`:
   - osobny blok dla kazdego jawnego acceptance criterion,
   - osobne bloki dla wymagan z opisu i komentarzy,
   - osobne bloki dla uzasadnionych wymagan inferowanych z kontekstu.
5. `Rekomendowane dzialania`, tylko gdy istnieja konkretne dzialania.

Raport jest czytany przez czlowieka. Nie wypisuj nazw pol kontraktu jako
surowej listy `scope`, `criterionSource`, `criterionQuote`,
`interpretationType`, `verifiedAgainst`, `evidenceRefs`, `gaps` i
`suggestedAction`. Uzyj naturalnych etykiet i grupuj informacje wedlug decyzji,
ktora operator ma podjac.

Komorki tabel musza byc krotkie. Nie umieszczaj w nich dlugich cytatow, list
sciezek ani references. Pelny cytat i techniczny proof umiesc w szczegolach
kryterium. References, visibility limits, open questions, gaps i warnings
przekaz jako section meta, aby UI mogl pokazac je na zadanie.

Nie pokazuj pustych wartosci, `[]`, `n/a`, `Brak` ani sekcji bez tresci.

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

Ten kontrakt pozostaje wymagany w finalnym JSON, nawet gdy Markdown prezentuje
go w bardziej zwartej, human-first formie.

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
