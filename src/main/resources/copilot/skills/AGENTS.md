# AGENTS

## Zakres

Ten katalog zawiera runtime skille Copilota pakowane z aplikacja. Skille sa
zasobem runtime, a nie dokumentacja `.github` ani kod Javy. Feature wybiera
podzbior skilli, platforma materializuje selected root z podkatalogami
`SKILL.md`, a GitHub Copilot laduje je przez built-in tool `skill`.

Zmiany w tym katalogu moga zmienic zachowanie AI bez zmiany publicznych HTTP
API, DTO ani pakietow Javy. Traktuj je jak kontrakt wykonawczy feature'a.

## Jezyk I Nazewnictwo

- Tresc runtime skilli pisz po polsku.
- Nie tlumacz identyfikatorow technicznych: nazwy skilli, tooli, pol JSON,
  klas, endpointow, section IDs, artifact names i kontraktow.
- Nazwy skilli maja opisywac zadanie albo odpowiedzialnosc, nie format toola.
  Preferuj `*-grounding`, `*-section`, `*-write-report`, `*-handoff`.
- Nie zostawiaj aliasowych katalogow ze starymi nazwami, jezeli renaming jest
  zaakceptowany. Test powinien pilnowac, ze stare katalogi nie istnieja.

## Model Odpowiedzialnosci

Kazdy skill musi miec jeden glowny powod istnienia:

- orkiestrator decyduje o kolejnosci pracy, readiness i petli zwrotnej,
- grounding skill dostarcza evidence albo scope, ale nie buduje sekcji wyniku,
- section/result skill jest wlascicielem ksztaltu konkretnej sekcji albo
  handoffu,
- goal skill jest soczewka celu, nie wlascicielem procedur technicznych,
- follow-up chat jest osobna sciezka po initial runie, bez pelnej orkiestracji
  initial result.

Nie dubluj szczegolowych procedur miedzy skillami. Jezeli procedura nalezy do
sekcji persistence, integration, technical handoff albo functional analysis,
trzymaj ja tylko w tym skillu. Orkiestrator moze wymagac rezultatu zgodnego z
tym skillem, ale nie powinien opisywac jego szczegolow.

## Orkiestratory

Orkiestrator moze:

- czytac manifest i artifact index,
- ustalac goal, aktywne section modes albo klasy diagnostyczne,
- utrzymywac ledger decyzji,
- wybierac nastepny waski skill albo tool capability,
- wykonywac readiness gate,
- przekazywac material do skilli wyniku.

Orkiestrator nie powinien:

- opisywac procedur GitLab, DB, `opctx_*`, persistence, integrations ani
  finalnych sekcji,
- definiowac fallback JSON, report tools albo Markdown result shape, jezeli
  istnieje dedykowany skill wyniku,
- finalizowac wyniku z pominieciem skilli result/write-report,
- zamieniac katalogu operational context w dowod root cause.

## Grounding I Skille Sekcyjne

Grounding skills odpowiadaja na waskie pytania evidence:

- code grounding: co kod potwierdza, obala albo zaweza,
- operational grounding: co katalog potwierdza o systemie, procesie, ownerze,
  scope albo handoffie,
- data diagnostics: co DB evidence potwierdza albo obala.

Skille sekcyjne odpowiadaja za analize konkretnej sekcji, zarowno dla trybu
`COMPACT`, jak i `DEEP`, jezeli sekcja nie jest `OFF`. Nie nazywaj ich `deep`,
jezeli obsluguja tez compact. Uzywaj `*-section`.

## Readiness I Petla Zwrotna

Kazdy wiekszy flow powinien miec dwa poziomy walidacji:

- lokalna walidacja w skillu specjalistycznym, z granica jednego waskiego
  poglebienia zamiast broad browse,
- readiness gate w orkiestratorze albo skillu write-report/result.

Preferowane statusy readiness:

- `ready`: artifact wystarcza do wyniku,
- `needs_deeper_evidence`: brak jest rozstrzygalny przez waski kolejny krok,
- `visibility_limited`: dalszy proof wymaga niedostepnej widocznosci,
- `not_applicable`: artifact nie jest potrzebny.

Jezeli wynikowy skill widzi rozstrzygalny brak, powinien zwrocic feedback do
orkiestracji zamiast wypelniac luke narracja. Feedback musi wskazywac:

```text
missingArtifact: <ktorego summary/ledger/evidence brakuje>
neededFor: <sekcja albo decyzja wyniku>
suggestedSkill: <nastepny skill albo widocznosc>
minimumNextQuestion: <jedno waskie pytanie, ktore moze zmienic wynik>
reason: <dlaczego bez tego wynik bylby zgadywaniem albo zbyt plytki>
```

Nie petl bez konca. Po waskim retry jezeli nadal nie ma proof, wpisz
`visibility_limited` i pozwol wynikowi pokazac limitation.

## Incident Analysis

Aktualny podzial:

- `incident-analysis-orchestrator`: tylko orkiestracja, ledger i readiness,
- `incident-code-grounding`: code evidence i `CodeGroundingSummary`,
- `incident-operational-grounding`: katalogowy scope, ownership i
  `OperationalGroundingSummary`,
- `incident-data-diagnostics`: DB/data proof i `DataDiagnosticSummary`,
- `incident-functional-analysis`: jedyny wlasciciel `functionalAnalysis`,
- `incident-technical-handoff`: jedyny wlasciciel `technicalAnalysis`.

Nie przywracaj starych pol publicznego wyniku: `summary`,
`recommendedAction`, `rationale`, `affectedFunction`,
`evidenceReferences`.

Operational context w incident analysis jest groundingiem, scope guidance i
handoff routingiem. Nie jest samodzielnym dowodem root cause.

## Flow Explorer

Aktualny podzial:

- `flow-explorer-orchestrator`: kolejnosc pracy, goal, active sections,
  readiness i przekazanie do write-report,
- `flow-explorer-code-grounding`: focused code evidence, bez budowania sekcji,
- `flow-explorer-operational-grounding`: katalogowy evidence/scope,
- `flow-explorer-map-persistence-section`: wlasciciel sekcji `PERSISTENCE`
  dla `COMPACT` i `DEEP`,
- `flow-explorer-map-integrations-section`: wlasciciel sekcji `INTEGRATIONS`
  dla `COMPACT` i `DEEP`,
- `flow-explorer-write-report`: jedyny wlasciciel finalnego raportu,
  fallback JSON, source refs, confidence i walidacji `AnalysisReport`,
- `flow-explorer-deep-discovery`, `flow-explorer-test-scenario-design`,
  `flow-explorer-risk-assessment`: soczewki celu, nie techniczne procedury,
- `flow-explorer-follow-up-chat`: sciezka po initial runie, bez pelnej
  orkiestracji ani przepisywania zasad finalnego raportu.

Skille sekcyjne wlaczaj wtedy, gdy sekcja nie jest `OFF`, nie tylko wtedy, gdy
tryb jest `DEEP`.

## Ladowanie Przez Copilota

- Feature ma przekazywac do runtime wybrane nazwy skilli.
- Platforma ma przekazac do SDK katalog-root zawierajacy podkatalogi skilli z
  `SKILL.md`, nie bezposredni katalog pojedynczego skilla.
- Gdy `skillDirectories` nie sa puste, `skill` musi byc w effective
  `availableTools`.
- Prompt albo manifest powinien mowic modelowi, ktory starter skill zaladowac
  i kiedy dociagac pozostale skille.
- Model nie powinien twierdzic, ze zna szczegoly `SKILL.md`, dopoki nie
  zaladuje skilla przez tool `skill`.
- Nie instruuj modelu, zeby czytal lokalny filesystem w celu poznania skilli.

## Jakosc Kazdego Skilla

Kazdy runtime skill powinien zawierac:

- cel,
- wejscia,
- role wobec orkiestratora albo result contract,
- procedury albo algorytm tylko w zakresie swojej odpowiedzialnosci,
- output contract,
- walidacje,
- fallbacki,
- artifacts/handoff do nastepnego kroku.

Skille powinny rozrozniac fakty, inferencje, hipotezy i limity widocznosci.
Mocne twierdzenia wymagaja source refs albo jawnego oznaczenia jako hipoteza.

## Frontmatter SKILL.md

Kazdy `SKILL.md` musi zaczynac sie poprawnym YAML frontmatter z polami
`name` i `description`. `name` musi byc identyczne z nazwa katalogu skilla.
Jezeli `description` albo inne pole tekstowe zawiera dwukropek z nastepujaca
spacja, np. `DEEP: ...`, cytuj cala wartosc YAML:

```yaml
description: "Opis z dwukropkiem: dalsza tresc"
```

Nie zostawiaj niecytowanych wartosci z takim dwukropkiem, bo Copilot SDK moze
nie zindeksowac skilla i built-in tool `skill` zwroci `Skill not found`, mimo
ze katalog i plik `SKILL.md` fizycznie istnieja.

## Testy I Weryfikacja

Po zmianie nazw, katalogow albo doboru skilli uruchom odpowiednie testy:

- contract test runtime skilli danego feature'a,
- `CopilotRuntimeSkillFrontmatterTest`, ktory parsuje YAML frontmatter
  wszystkich runtime skilli,
- preparation/session config test, ktory sprawdza selected root i `skill` w
  `availableTools`,
- prompt preparation/rendering test, ktory sprawdza aktualne nazwy w
  promptcie/manifest,
- `mvn -q -DskipTests compile`.

Dodaj testy, jezeli zmiana wprowadza nowy skill, usuwa stary katalog, zmienia
relacje miedzy skillami albo przenosi procedury miedzy odpowiedzialnosciami.

## Antywzorce

Nie:

- rob globalnego "spaghetti skilli" z powtorzonymi procedurami,
- chowaj szczegolow sekcji w orkiestratorze,
- mieszaj incident analysis i Flow Explorera w jednym skillu,
- przenos incidentowych heurystyk do neutralnych tools,
- uzywaj operational context jako proof awarii,
- finalizuj wynik, gdy wymagany artifact ma `needs_deeper_evidence`,
- dodawaj Java DTO dla posrednich artifacts tylko dlatego, ze pojawily sie w
  `SKILL.md`.
