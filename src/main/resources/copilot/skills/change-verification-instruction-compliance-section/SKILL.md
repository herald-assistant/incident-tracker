---
name: change-verification-instruction-compliance-section
description: Buduje szczegolowa sekcje INSTRUCTION_COMPLIANCE dla AGENTS.md, copilot-instructions i referencjonowanych plikow instrukcji.
---

# Change Verification Instruction Compliance Section

## Cel

Zbuduj `InstructionComplianceSectionDraft`, ktory pokazuje, czy widoczna
zmiana spelnia instrukcje obowiazujace dla zmienionych plikow i
repozytoriow.

## Zakres

Uwzglednij:

- globalne i lokalne `AGENTS.md`,
- `.github/copilot-instructions.md`,
- pliki wskazane przez copilot instructions,
- inne pobrane instruction sources,
- `applicableChangedFiles` oraz `referencedBy`.

Nie traktuj instrukcji jako wymagania biznesowego. Instrukcja jest regula
architektoniczna, implementacyjna, testowa albo repozytoryjna i musi byc
powiazana z konkretnym fragmentem zmiany, do ktorego ma zastosowanie.

Wpisy `limitations` z `source-discovery.md`, `source-discovery-limits` i
`instruction-source-limits` sa metadanymi pokrycia platformy, a nie
instrukcjami repozytorium. Pokaz je wylacznie w `visibilityLimits`. Nie tworz
z nich checkow, findings, gaps, open questions ani rekomendacji dla zespolu
i nie uwzgledniaj ich przy wyznaczaniu statusu sekcji. `NOT_VERIFIED` wymaga
konkretnej, zidentyfikowanej reguly projektu; sam komunikat o limicie lub
truncation nie jest regula.

## Wymagana Struktura

Sekcja musi zawierac:

1. `Wynik weryfikacji` z lacznym statusem, licznikami oraz najwazniejszym
   potwierdzeniem, ryzykiem i dzialaniem.
2. `Wymaga uwagi` jako tabela tylko dla niepotwierdzonych, ostrzegawczych,
   sprzecznych albo naruszonych regul:
   `Status | Regula | Wniosek | Rekomendowane dzialanie`.
3. `Potwierdzone wymagania` jako zwarta tabela:
   `Regula | Zrodlo | Co potwierdzono | Status`.
4. `Szczegoly kryteriow` z osobnym checkiem dla kazdej istotnej reguly.
5. `Rekomendowane dzialania`, tylko gdy istnieja konkretne dzialania.

Markdown ma byc raportem dla czlowieka, nie zrzutem kontraktu JSON. Nie
wypisuj nazw pol `scope`, `criterionSource`, `criterionQuote`,
`interpretationType`, `verifiedAgainst`, `evidenceRefs`, `gaps` i
`suggestedAction` jako technicznej listy. Uzywaj naturalnych etykiet.

W tabelach umieszczaj krotkie wnioski. Cytaty z instrukcji, dlugie sciezki,
proof i references przenies do szczegolow albo section meta. References,
visibility limits, open questions, gaps i warnings musza byc section meta,
aby UI pokazal je dopiero po rozwinieciu.

Nie pokazuj pustych wartosci, `[]`, `n/a`, `Brak` ani pustych sekcji.

Kazdy check musi miec ten sam kontrakt co `verificationChecks`, z:

- `origin=DEFINED`,
- `scope=INSTRUCTION_COMPLIANCE`,
- cytatem i sciezka pliku instrukcji,
- `interpretationType`,
- lista plikow/MR/kodu, wobec ktorych regule sprawdzono,
- statusem i source refs.

Pelny kontrakt checka pozostaje wymagany w finalnym JSON niezaleznie od
human-first formy Markdown.

Gdy instrukcja jest ogolna, ale jej zastosowanie do zmiany wymaga
interpretacji, nadal zachowaj `origin=DEFINED`, poniewaz regula pochodzi z
jawnego source; opisz interpretacje w `analysis`. Gdy kilka instrukcji jest
sprzecznych, uzyj `conflicting` i nie wybieraj jednej po cichu.

## Readiness

Sekcja jest `ready`, gdy:

- wszystkie pobrane instrukcje majace zastosowanie zostaly ocenione,
- pliki referencjonowane przez copilot instructions zostaly uwzglednione albo
  maja jawny limit widocznosci,
- kazdy zarzut wskazuje konkretna regule i konkretny element zmiany,
- brak proof jest `NOT_VERIFIED`, a nie `FAILED`.
- zaden check ani glowny wniosek nie opisuje ograniczenia platformy jako
  niezgodnosci projektu.

## Handoff

Przekaz orkiestratorowi:

```text
InstructionComplianceSectionDraft
instructionChecks
instructionFindings
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
