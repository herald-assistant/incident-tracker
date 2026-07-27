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

## Wymagana Struktura

Sekcja musi zawierac:

1. `Krotkie podsumowanie weryfikacji`.
2. `Szczegolowy raport` z osobnym checkiem dla kazdej istotnej reguly.
3. `Rozbieznosci i rekomendacje`.

Kazdy check musi miec ten sam kontrakt co `verificationChecks`, z:

- `scope=INSTRUCTION_COMPLIANCE`,
- cytatem i sciezka pliku instrukcji,
- `interpretationType`,
- lista plikow/MR/kodu, wobec ktorych regule sprawdzono,
- statusem i source refs.

Gdy instrukcja jest ogolna, ale jej zastosowanie do zmiany wymaga
interpretacji, uzyj `interpretationType=inferred`. Gdy kilka instrukcji jest
sprzecznych, uzyj `conflicting` i nie wybieraj jednej po cichu.

## Readiness

Sekcja jest `ready`, gdy:

- wszystkie pobrane instrukcje majace zastosowanie zostaly ocenione,
- pliki referencjonowane przez copilot instructions zostaly uwzglednione albo
  maja jawny limit widocznosci,
- kazdy zarzut wskazuje konkretna regule i konkretny element zmiany,
- brak proof jest `NOT_VERIFIED`, a nie `FAILED`.

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
