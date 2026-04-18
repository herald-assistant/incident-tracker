# AGENTS

## Zakres

Ten katalog odpowiada za integracje z GitHub Copilot Java SDK.

## Podzial odpowiedzialnosci

- katalog glowny
  Provider AI oparty o Copilot SDK.
- `preparation/`
  Budowa `CopilotClientOptions`, `SessionConfig`, `MessageOptions`, promptu i
  runtime skills.
- `execution/`
  Lifecycle klienta, sesje, wykonanie requestu i logowanie eventow.
- `tools/`
  Bridge pomiedzy Spring tool callbacks a tool definitions Copilot SDK.

## Aktualne zalozenia architektoniczne

- Prompt ma niesc dane konkretnego incydentu.
- Skill ma niesc stale zasady pracy z evidence i toolami.
- Provider AI dostaje generyczne `AnalysisEvidenceSection`, nie klasy adapterow.
- `gitLabGroup`, rozwiazany `gitLabBranch` oraz `environment` sa jawnie
  przekazywane do promptu.
- AI ma interpretowac `project` i `filePath`, a nie zgadywac `gitLabGroup`.
- AI nie powinno wymyslac `gitLabBranch`, jesli nie zostal rozwiazany z logs.
- Tool definitions maja reuse'owac logike istniejacych Spring tools, a nie
  dublowac implementacje.

## Zasady modyfikacji

### Preparation

- Nie mieszaj kodu wykonawczego z warstwa preparation.
- Nie dodawaj w prompt builderze zaleznosci od provider-specific modeli
  adapterow.
- Jesli zmienia sie strategia pracy z toolami, najpierw sprawdz, czy to powinno
  trafic do skilla zamiast do promptu.

### Skills

- Skill jest runtime resource aplikacji.
- Zrodlo prawdy dla skilli to `src/main/resources/copilot/skills`.
- Jesli zmienia sie capability, aktualizuj skill razem z kodem.
- Nie przenos skilli do `.github`.

### Execution

- Permission handling musi byc jawnie ustawione.
- Logi lifecycle klienta, eventow sesji i tool execution maja pozostac
  rozdzielone.
- Nie usuwaj obserwowalnosci bez realnego powodu, bo jest potrzebna do
  diagnozowania zachowania modelu.

### Tools bridge

- Bridge ma mapowac istniejace Spring tools na Copilot tools.
- Nie duplikuj logiki GitLaba w warstwie bridge.
- Jesli dochodza nowe tools, opisz ich strategie uzycia w skillu lub
  dokumentacji.

## Testy

- Zmiany w `preparation` powinny miec testy budowy requestu i sesji.
- Zmiany w `tools` powinny miec testy mapowania tool definitions.
- Zmiany w `execution` powinny przynajmniej zachowac obecne kontrakty i
  logowanie bez psucia flow.

## Dokumenty do aktualizacji po wiekszej zmianie

- `docs/architecture/02-key-decisions.md`
- `docs/architecture/03-runtime-flow.md`
- `docs/11-copilot-sdk-preparation.md`
- `docs/12-copilot-sdk-provider.md`
- `docs/16-copilot-sdk-gitlab-tools.md`
- `docs/17-copilot-incident-analysis-skill.md`
