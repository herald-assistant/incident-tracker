# Krok 17: Skill Domenowy Dla Analizy Incydentow

W tym kroku przenosimy stala strategie pracy z GitLab toolami z promptu do
projektowego skilla Copilota i pakujemy go jako resource aplikacji.

Uwaga:
skill pozostaje aktualny koncepcyjnie, ale od czasu tego kroku dostalismy juz
jawny kontekst `gitLabGroup` i `branch`. Aktualny model integracji opisuje krok
18.

To jest wazne, bo teraz:

- prompt niesie glownie dane konkretnego przypadku,
- skill niesie powtarzalne zasady pracy,
- te same zasady mozemy reuse'owac w kolejnych analizach i projektach.

## Po co robimy ten krok

Po podpieciu GitLab tooli do sesji Copilota model juz moze korzystac z:

- `gitlab_search_repository_candidates`
- `gitlab_read_repository_file`
- `gitlab_read_repository_file_chunk`

To jednak nie wystarcza do dobrej pracy operacyjnej.

Model musi jeszcze wiedziec:

- kiedy zawezac przestrzen przeszukiwania,
- kiedy czytac chunk, a kiedy caly plik,
- kiedy przestac dogrywac kolejne pliki,
- jak odroznic silna diagnoze od slabej hipotezy.

Takie reguly sa wiedza proceduralna, wiec lepiej trzymac je w skillu niz w
kazdym promptcie osobno.

## Co dodalismy

- projektowy skill w `src/main/resources/copilot/skills/incident-analysis-gitlab-tools/SKILL.md`
- pola `skillResourceRoots`, `skillRuntimeDirectory`, `skillDirectories` i
  `disabledSkills` w konfiguracji Copilot SDK
- loader, ktory wypakowuje skille z classpath do katalogu runtime
- odchudzenie promptu tak, aby odwolal sie do zaladowanych skills zamiast
  powtarzac cala strategie w tresci requestu

## Gdzie patrzec w kodzie

- `src/main/resources/copilot/skills/incident-analysis-gitlab-tools/SKILL.md`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/preparation/CopilotSdkProperties.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/preparation/CopilotSkillRuntimeLoader.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/preparation/CopilotSdkPreparationService.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/ai/copilot/CopilotSkillRuntimeLoaderTest.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/ai/copilot/CopilotSdkPreparationServiceTest.java`

## Jak wyglada projektowy skill

Skill zostal zapisany zgodnie z formatem GitHub Copilot:

- osobny katalog skilla,
- plik `SKILL.md`,
- YAML frontmatter z `name` i `description`,
- markdown body z instrukcjami.

W naszym skillu sa 4 najwazniejsze grupy zasad:

1. `Evidence-first workflow`
   Najpierw analiza tego, co juz mamy.

2. `GitLab tool strategy`
   Najpierw search, potem preferencja dla chunkow, a pelny plik dopiero gdy to
   uzasadnione.

3. `Stop conditions`
   Jasne kryteria, kiedy nie ma sensu dociagac kolejnych plikow.

4. `Grounding rules`
   Zakaz zgadywania bez potwierdzenia w evidence lub kodzie.

## Dlaczego skill jest w resources

Ta aplikacja ma docelowo dzialac jako:

- JAR,
- obraz kontenerowy,
- deployowalny artefakt runtime.

Dlatego skill powinien byc czescia paczki aplikacji, a nie tylko plikiem
repozytoryjnym obok kodu.

`src/main/resources` jest tutaj naturalnym miejscem, bo:

- trafia do artefaktu aplikacji,
- jest dostepne po uruchomieniu w kontenerze,
- mozna je wypakowac do katalogu runtime, jesli Copilot SDK wymaga prawdziwej
  sciezki filesystemowej.

## Jak to podlaczylismy

W `CopilotSdkPreparationService` ustawiamy teraz w `SessionConfig`:

- `skillDirectories`
- `disabledSkills`

Ale nie wskazujemy juz bezposrednio katalogu z repo.

Zamiast tego:

1. skill jest trzymany w classpath resources,
2. `CopilotSkillRuntimeLoader` wypakowuje go do katalogu runtime,
3. dopiero wypakowana sciezka trafia do `SessionConfig.skillDirectories`.

Domyslne ustawienia to:

```properties
analysis.ai.copilot.permission-mode=approve-all
analysis.ai.copilot.skill-resource-roots=copilot/skills
analysis.ai.copilot.skill-runtime-directory=${java.io.tmpdir}/incident-tracker/copilot-skills
```

To oznacza, ze:

- zrodlem prawdy jest classpath root `copilot/skills`,
- runtime copy trafia do katalogu tymczasowego,
- Copilot SDK dostaje juz realna sciezke na dysku.

Opcjonalnie mozna tez dolozyc dodatkowe zewnetrzne katalogi:

```properties
analysis.ai.copilot.skill-directories=/opt/incident-tracker/copilot-skills
```

## Dlaczego nie trzymamy tego tylko w promptcie

Bo prompt powinien opisywac glownie:

- aktualny `correlationId`
- aktualne evidence
- oczekiwany format odpowiedzi

A skill powinien opisywac:

- stale zasady pracy,
- strategie korzystania z tooli,
- reguly zatrzymania i ograniczania kosztu,
- heurystyki dla kolejnych analiz.

To daje czystszy podzial odpowiedzialnosci i latwiejsze utrzymanie.

## Jak testowac

```powershell
mvn test
```

Najwazniejszy test:

- `CopilotSdkPreparationServiceTest`

Sprawdzaja teraz miedzy innymi, ze:

- skill jest wypakowywany z resources do runtime directory
- `SessionConfig` dostaje wypakowany `skillDirectories`
- prompt nadal zawiera dane konkretnej analizy
- sesja nadal dostaje wystawione GitLab tools

## Jak debugowac

Ustaw breakpointy w:

- `CopilotSdkPreparationService.prepare(...)`
- `CopilotSdkPreparationService.buildPrompt(...)`

I sprawdz:

- `sessionConfig.getSkillDirectories()`
- `sessionConfig.getTools()`
- `messageOptions.getPrompt()`

Dobry dodatkowy breakpoint to:

- `CopilotSkillRuntimeLoader.resolveSkillDirectories()`

## Co warto zrozumiec po tym kroku

1. Czym rozni sie skill od promptu operacyjnego?
2. Dlaczego strategie tool usage lepiej trzymac w skillu?
3. Dlaczego deployowalna aplikacja powinna miec skill jako resource runtime?
4. Dlaczego stop conditions sa tak samo wazne jak same tool-e?

## Co dalej

Nastepny dobry krok to:

- uruchomic analize z aktualnym runtime providerem Copilot SDK,
- zrobic pierwszy kontrolowany request do `/analysis`,
- sprawdzic, czy Copilot faktycznie korzysta z GitLab tooli,
- a potem dodac obserwowalnosc eventow sesji i wywolan narzedzi.
