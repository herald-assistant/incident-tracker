# Wspolny katalog runtime skilli Copilota

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Dynamiczne materializowanie wybranego podzbioru skilli dla kazdego feature'a
tworzy nietrwale katalogi `selected-skills-*`. Analiza moze zebrac kompletne
evidence, a nastepnie upasc przed uruchomieniem AI, gdy katalog tymczasowy
zniknie albo nie zawiera wymaganego skilla. MVP potrzebuje jednego stabilnego
katalogu platformowego, dostepnego tak samo dla kazdej sesji.

## Baseline

- `CopilotSkillRuntimeLoader` wypakowuje resource roots do katalogu
  tymczasowego i tworzy fingerprintowane katalogi `selected-skills-*`.
- Incident Analysis, Flow Explorer, Change Verification i Config Drift Viewer
  przekazuja wybrane nazwy skilli przez `CopilotNamedSkillDirectoryResolver`.
- `CopilotSessionConfigRequest` niesie feature-owned `skillDirectories`.
- prompty feature'ow juz wskazuja starter albo workflow skilli.
- celowany baseline testow platformy i czterech konsumentow przechodzi.

## Conformance delta

- Cel zmiany: jeden mirror skilli pod
  `${analysis.ai.copilot.copilot-home}/skills`, domyslnie
  `tdw-data/copilot/skills`, dla wszystkich sesji Copilota.
- Wlasciciel: `aiplatform.copilot.runtime`.
- Publiczne API/DTO, evidence, report, job state, persistence, export i UI:
  bez zmian.
- Prompt/skille: tresc skilli i feature-owned guidance pozostaja; usunieta
  zostaje selekcja katalogu per feature.
- Tools/policy/hidden scope/budzet: bez zmian poza zawsze dostepnym built-in
  toolem `skill` przy platformowym katalogu.
- Zaleznosci: bez nowych kierunkow; usuniety zostaje feature -> named skill
  resolver.
- Konsumenci: Incident Analysis initial/follow-up, Flow Explorer
  initial/follow-up, Change Verification oraz Config Drift Viewer DEEP.
- Kompatybilnosc: brak. Usuwamy properties starego runtime katalogu,
  dodatkowe katalogi zewnetrzne, selected roots i pole requestu sesji.

## Proponowane rozwiazanie

Platforma przy starcie buduje deterministyczny mirror packaged resources
`copilot/skills` w katalogu `${copilot-home}/skills`, waliduje katalogi,
`SKILL.md`, frontmatter i unikalnosc nazw, a nastepnie przekazuje ten sam root
do `SessionConfig` i `ResumeSessionConfig`. Feature wskazuje przydatne skille
wylacznie w promptach i zachowuje ownership ich tresci oraz workflow.

## Zakres

- uproszczenie properties i loadera runtime skilli,
- usuniecie `CopilotNamedSkillDirectoryResolver` i selekcji per feature,
- platformowe ustawianie katalogu i toola `skill` dla kazdej sesji,
- migracja testow wszystkich konsumentow,
- aktualizacja instrukcji i dokumentacji architektonicznej.

## Non-goals

- edycja skilli przez UI lub uzytkownika,
- hot reload skilli bez restartu,
- zmiana promptow merytorycznych, tool allowlist albo kontraktow wynikow,
- migracja albo odczyt starych katalogow tymczasowych.

## Ograniczenia i ryzyka

- katalog jest platform-owned i przy starcie jest odtwarzany z packaged
  resources; lokalne reczne zmiany zostana nadpisane,
- wspolny katalog daje modelowi widocznosc nazw skilli innych feature'ow;
  granica bezpieczenstwa nadal opiera sie na feature-owned promptach,
  allowliscie tools, hookach i hidden scope,
- blad materializacji albo walidacji blokuje start aplikacji zamiast
  pojedynczej analizy.

## Kryteria akceptacji

- kazda sesja NEW i EXISTING dostaje jeden root `copilot-home/skills`,
- root zawiera wszystkie packaged skille i nie powstaja `selected-skills-*`,
- brak/blad `SKILL.md`, niezgodna lub zduplikowana nazwa blokuje start,
- feature session requests nie przenosza katalogow ani list wybranych skilli,
- prompty czterech konsumentow nadal wskazuja wlasciwy starter/workflow,
- celowane testy, `PackageDependencyGuardTest`, pelne `mvn -q test` oraz
  `git diff --check` przechodza.

## Kroki

- [x] Krok 1: Zmienic platformowy loader, properties i session config na jeden
  katalog `${copilot-home}/skills`; zweryfikowac testami materializacje,
  walidacje, NEW/EXISTING oraz brak selected roots.
- [x] Krok 2: Usunac resolver i selection wiring z czterech feature'ow,
  zachowac promptowe wskazanie skilli i zaktualizowac testy konsumentow.
- [x] Krok 3: Zaktualizowac kanoniczne dokumenty, `AGENTS.md` i konfiguracje,
  usuwajac stary kontrakt oraz properties bez aliasow.
- [x] Krok 4: Uruchomic celowana regresje, `PackageDependencyGuardTest`, pelne
  `mvn -q test`, wyszukiwanie starego kontraktu i `git diff --check`.
