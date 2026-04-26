# AGENTS

## Zakres

Ten katalog odpowiada za generyczny kontrakt AI i aktualna implementacje oparta
o GitHub Copilot Java SDK.

Obejmuje:

- kontrakty `AnalysisAiProvider`, request/response i generyczne modele
  evidence przekazywane do AI,
- `copilot/preparation/`
  budowe promptu, konfiguracji klienta, tool definitions i runtime skills,
- `copilot/execution/`
  wykonanie sesji, lifecycle klienta i logowanie eventow,
- `copilot/tools/`
  bridge pomiedzy Spring tools a tool definitions Copilot SDK.

Nie obejmuje:

- klas adapterow integracyjnych z `../adapter`,
- sekwencyjnego pipeline evidence z `../evidence`,
- kontrolerow HTTP i job flow z `../sync` i `../job`.

## Zasady modyfikacji

- `AnalysisAiProvider` ma pozostac stabilna granica miedzy flow a konkretnym
  SDK lub modelem.
- AI dostaje tylko `AnalysisAiAnalysisRequest` i generyczne
  `AnalysisEvidenceSection`. Nie wciskaj tu klas adapter-specific.
- Prompt ma niesc dane konkretnego incydentu. Stale zasady pracy z toolami i
  evidence powinny trafac do skilla albo jawnej konfiguracji preparation.
- Skill pozostaje runtime resource aplikacji w
  `src/main/resources/copilot/skills`, a nie plikiem w `.github`.
- Tool bridge ma reuse'owac istniejace Spring tools z `../mcp`, a nie dublowac
  ich implementacje.
- User-facing tool evidence ma pozostac proste: GitLab pokazuje plik, kod i
  `reason`, a Database pokazuje wynik i `reason`. Nie przywracaj dodatkowych
  pseudo-heurystyk ani technicznych pol do payloadu dla operatora.
- Permission handling musi byc jawnie ustawione. Nie zostawiaj domyslnego,
  nieczytelnego zachowania SDK.
- Parsing odpowiedzi modelu ma pozostac odporny na formatowanie, ale kontrakt
  pol `detectedProblem`, `summary`, `recommendedAction`, `rationale`,
  `affectedFunction`, `affectedProcess`, `affectedBoundedContext`,
  `affectedTeam` powinien pozostac stabilny dla flow i UI.
- Jesli kiedys dojda kolejne providery AI, trzymaj ich szczegoly lokalnie i nie
  rozlewaj zaleznosci SDK poza ten katalog.

## Testy

- Zmiany w `copilot/preparation` powinny miec testy promptu, konfiguracji sesji
  i ladowania skilli.
- Zmiany w `copilot/tools` powinny miec testy mapowania Spring tools na tool
  definitions.
- Zmiany w `copilot/execution` powinny zachowac kontrakty i obserwowalnosc
  lifecycle klienta i sesji.
- Zmiany w providerze AI powinny miec testy parsowania odpowiedzi modelu.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/02-key-decisions.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/onboarding/09-spring-ai-and-mcp-tools.md`
- `docs/onboarding/10-copilot-sdk-analysis-runtime.md`
