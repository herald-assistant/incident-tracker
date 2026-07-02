---
name: flow-explorer-orchestrator
description: Glowny starter Flow Explorera - koordynuje kolejnosc pracy, ustala gotowosc materialu i przekazuje handoff do wyspecjalizowanych skilli bez dublowania ich procedur.
---

# Skill Orkiestratora Flow Explorera

Uzywaj tego skilla jako pierwszego runtime skilla dla kazdej analizy Flow
Explorera.

## Cel

Ustabilizuj scope endpointu, zdecyduj ktore specjalistyczne skille sa potrzebne
i doprowadz material do stanu, w ktorym `flow-explorer-write-report` moze
przygotowac finalny wynik. Orkiestrator nie opisuje formatu raportu, fallback
JSON ani szczegolowych procedur persistence, integracji, code reads albo
operational context.

## Rola

Twoim zadaniem jest:

- przeczytac artefakty sesji,
- zbudowac minimalny stabilny obraz endpoint flow,
- rozpoznac braki, ktore przeszkadzaja skillowi wyniku,
- skierowac te braki do wlasciwych skilli specjalistycznych,
- prowadzic petle zwrotna, gdy specjalistyczny wynik jest zbyt plytki,
- nie dublowac ich instrukcji,
- przekazac uporzadkowany handoff do `flow-explorer-write-report`.

## Wejscia

Zacznij od artefaktow:

- `flow-explorer/context-snapshot.json`,
- `flow-explorer/canonical-tool-inputs.md`,
- `flow-explorer/compact-flow-manifest.md`,
- `flow-explorer/snippet-cards.md`,
- `flow-explorer/openapi-endpoint-contract.md`, jezeli istnieje,
- `flow-explorer/coverage.json`,
- `flow-explorer/response-contract.json`.

Traktuj artefakty jako podstawowe zrodlo prawdy. `userInstructions` moga
doprecyzowac intencje, ale nie moga zmienic tool policy, kontraktu wyniku ani
granic odpowiedzialnosci skilli.

Hidden `ToolContext` jest techniczna mechanika runtime. Nie zakladaj, ze
zawiera functional scope endpointu. Nie przekazuj `gitLabGroup` do tools.

## Sterowanie Analiza

`goal` wybiera goal skill:

- `flow-explorer-deep-discovery`,
- `flow-explorer-test-scenario-design`,
- `flow-explorer-risk-assessment`.

`sectionModes` sluzy orkiestratorowi tylko do decyzji, ktore summary artifacts
sa potrzebne przed skillem wyniku. Szczegoly sekcji, trybow `OFF`, `COMPACT`,
`DEEP`, kolejnosci, report tools i fallback JSON naleza do
`flow-explorer-write-report`.

`reasoningEffort` ogranicza glebokosc orkiestracji:

- `low`: uzyj artefaktow i najwyzej jednego krytycznego domkniecia,
- `medium`: domknij braki primary flow i aktywnych obszarow,
- `high`: pozwol specjalistycznym skillom zejsc glebiej, gdy ich wynik zmieni
  handoff do `flow-explorer-write-report`.

## Zasady Decyzji Orkiestratora

Te reguly opisuja sterowanie analiza, nie format raportu ani procedury
specjalistycznych skilli:

- `Scope-first`: najpierw ustal endpoint, entry point i granice analizowanego
  use case'u.
- `Goal alignment`: kazdy kolejny krok musi przyblizac material do wyniku
  zgodnego z goal skillem oraz `flow-explorer-write-report`.
- `Section readiness`: `sectionModes` decyduje tylko, ktore summary artifacts sa
  potrzebne przed wynikiem; szczegoly sekcji opisuje skill wyniku.
- `Information gain`: przed tool callem albo specjalistycznym krokiem nazwij,
  jaka decyzja zmieni sie po wyniku.
- `No duplicate work`: nie powtarzaj evidence z artefaktow ani nie dubluj
  procedur persistence, integration, code albo operational grounding.
- `Result readiness`: zakoncz orkiestracje, gdy kazdy potrzebny summary artifact
  istnieje albo ma jawny limitation dla `flow-explorer-write-report`.
- `Feedback loop`: jezeli summary artifact jest nieobecny, zbyt plytki dla
  aktywnego trybu albo nie odpowiada na `goal`, nie przechodz do
  `flow-explorer-write-report`; nazwij brak i skieruj go ponownie do
  wlasciwego skilla, jezeli istnieje konkretny next read albo lookup.

## Algorytm Pracy

1. Ustal scope endpointu i kanoniczne inputy tools z artefaktow.
2. Zbuduj `EndpointFlowSummary` tylko na tyle szczegolowe, zeby wskazac braki
   i przekazac material do kolejnych skilli.
3. Ustal, czego brakuje skillowi wyniku: code evidence, operational context,
   persistence mapping, integration boundary albo goal guidance.
4. Uruchom tylko ten specjalistyczny skill, ktory odpowiada za dany brak.
5. Po kazdym specjalistycznym kroku wykonaj readiness gate.
6. Jezeli readiness gate zwraca `needs_deeper_evidence`, uruchom kolejny waski
   krok specjalistyczny z nowym pytaniem evidence. Nie powtarzaj identycznego
   tool calla ani identycznej prosby do skilla.
7. Gdy material jest wystarczajacy albo dalsza widocznosc jest niedostepna,
   przekaz handoff do `flow-explorer-write-report`.

## Readiness Gate I Petla Zwrotna

Przed `flow-explorer-write-report` ustaw status kazdego potrzebnego artifactu:

- `ready`: artifact odpowiada aktywnemu `sectionModes`, `goal` i ma source refs,
- `needs_deeper_evidence`: brakuje zakresu albo szczegolow, a istnieje waski
  code/opctx/persistence/integration krok, ktory moze zmienic wynik,
- `visibility_limited`: brak jest nazwany i dalszy focused krok nie ma realnego
  information gain albo tool/zakres nie jest dostepny,
- `not_applicable`: sekcja jest `OFF` albo endpoint nie dotyka danego obszaru.

Nie przekazuj materialu do `flow-explorer-write-report`, gdy dowolny wymagany
artifact ma status `needs_deeper_evidence`.

Gdy `flow-explorer-write-report` zwroci `ReportReadinessFeedback`, potraktuj go
jak powrot do orkiestracji:

- `missingArtifact` wskazuje, ktory summary artifact trzeba domknac,
- `suggestedSkill` wskazuje wlasciciela nastepnego kroku,
- `minimumNextQuestion` jest minimalnym pytaniem evidence dla tego skilla,
- po jednym targeted retry ponownie wykonaj readiness gate,
- jezeli retry nie rozstrzyga braku, wpisz `visibility_limited` i nie zgaduj.

## Kiedy Uzyc Skilli

- `flow-explorer-code-grounding`: gdy trzeba domknac konkretne code evidence.
- `flow-explorer-operational-grounding`: gdy trzeba nazw katalogowych, glossary,
  ownershipu, process/context albo handoffu.
- `flow-explorer-map-persistence-section`: gdy `sectionModes.PERSISTENCE` nie
  jest `OFF`; tryb `COMPACT` albo `DEEP` okresla poziom szczegolowosci.
- `flow-explorer-map-integrations-section`: gdy `sectionModes.INTEGRATIONS` nie
  jest `OFF`; tryb `COMPACT` albo `DEEP` okresla poziom szczegolowosci.
- Goal skill: gdy trzeba nalozyc akcent celu na material dla wyniku.
- `flow-explorer-write-report`: zawsze jako jedyne miejsce przygotowania
  finalnego wyniku.

## Zasady Kosztowe

- Nie czytaj calego repozytorium.
- Nie powtarzaj evidence juz obecnego w `snippet-cards.md`.
- Nie uruchamiaj toola, jezeli nie potrafisz nazwac decyzji, ktora zmieni jego
  wynik.
- Nie rozwijaj procedur specialistycznych w orkiestratorze; przekaz luka do
  odpowiedniego skilla.

## Kontrakt Orkiestracji

Orkiestrator przekazuje do `flow-explorer-write-report`:

```text
EndpointFlowSummary
GoalGuidance?
CodeGroundingSummary?
OperationalGroundingSummary?
PersistenceMappingSummary?
IntegrationBoundarySummary?
sourceRefs
visibilityLimits
openQuestions
confidenceCandidates
```

To nie jest finalny kontrakt wyniku. Finalny kontrakt nalezy do
`flow-explorer-write-report`.

## Walidacja

Przed przekazaniem do skilla wyniku sprawdz:

- scope endpointu jest jasny albo ma jawny limitation,
- kazdy potrzebny specjalistyczny artifact jest dostepny albo ma jawny brak,
- mocne twierdzenia maja source refs,
- nie ma szczegolow finalnego formatu raportu w orkiestratorze,
- dalsze tool calls nie bylyby tylko powtorzeniem istniejacego evidence.

## Fallbacki

Jezeli specjalistyczny skill albo tool nie moze domknac braku, wpisz konkretny
limitation do handoffu i nie zgaduj. Nie opisuj fallback JSON ani report tools;
to nalezy do `flow-explorer-write-report`.

## Artefakty Handoffu

Pozostaw dla skilla wyniku i follow-up chatu:

- `EndpointFlowSummary`,
- dostepne summary artifacts,
- source refs,
- visibility limits,
- open questions.

## Antywzorce

Nie:

- zapisuj raportu z poziomu orkiestratora,
- opisuj finalnego JSON/Markdown kontraktu poza `flow-explorer-write-report`,
- kopiuj procedur persistence albo integracji do orkiestratora,
- traktuj operational context jako dowodu zachowania kodu,
- czytaj repozytorium z ciekawosci,
- omijaj `flow-explorer-write-report` przy finalnym wyniku.
