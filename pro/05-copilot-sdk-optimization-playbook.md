# Copilot SDK Optimization Playbook

## Cel optymalizacji

Optymalizacja Copilot SDK w tym projekcie powinna jednoczesnie poprawic:

1. trafnosc diagnozy,
2. stabilnosc shape odpowiedzi,
3. koszt i czas sesji,
4. przewidywalnosc pracy z GitLab tools,
5. debuggability i governance.

## Najwazniejsze dzwignie

### A. Zmniejszenie swobody modelu tam, gdzie kontekst jest juz znany

Najlepszy kandydat:

- ukrycie `gitLabGroup` i `gitLabBranch` z kontraktow tooli

Obecny stan:

- model dostaje te wartosci w promptcie,
- ale jednoczesnie tools przyjmuja je jako parametry.

Rekomendacja:

- wprowadz session-scoped albo request-scoped wrapper na GitLab tools,
- zeby model podawal tylko:
  - `projectName`
  - `filePath`
  - `startLine/endLine`
  - ewentualnie `keywords` i `operationNames`

Zysk:

- mniej pomylek,
- mniejsza prompt burden,
- prostszy reasoning dla modelu.

## Quick wins

### 1. Ujednolic response contract do twardszego formatu

Opcje:

- JSON-only response contract,
- fenced JSON block,
- stricter post-processor z walidacja.

Po co:

- ograniczyc `AI_UNSTRUCTURED_RESPONSE`,
- zmniejszyc koszt parser maintenance,
- uproscic frontend i downstream debug.

Miejsca zmian:

- `CopilotSdkPreparationService`
- `CopilotSdkAnalysisAiProvider`
- testy parsera
- skill

### 2. Przestan budowac prompt dwa razy

Obecny stan:

- `preparePrompt(...)`
- potem `prepare(...)`

Rekomendacja:

- w orchestratorze wywolywac jedna preparation sciezke,
- zwracac z niej `preparedPrompt` i gotowy artifact summary,
- reuse'owac wynik przy analizie.

Zysk:

- mniej duplikacji,
- mniej kosztu preparation,
- jedno zrodlo prawdy dla promptu.

### 3. Dodaj telemetry per analysis session

Zbieraj jawnie:

- liczbe sekcji evidence,
- liczbe itemow evidence,
- liczbe attachment files,
- rozmiar attachment artifacts,
- liczbe tool calls per type,
- liczbe read file vs read chunk,
- latency:
  - evidence collection,
  - prompt preparation,
  - Copilot execution,
  - tools.

Zysk:

- wreszcie da sie porownywac eksperymenty,
- latwiej wykryc regresje.

### 4. Dodaj exploration budget

Przyklady:

- max `gitlab_read_repository_file` calls na sesje,
- max laczny rozmiar odczytanego kodu,
- preferowanie `chunk` nad `file`,
- max dodatkowych `elastic_search_logs_by_correlation_id`.

Mozna to zrobic:

- w skillu jako zasada,
- w backendzie jako twarda kontrola,
- najlepiej w obu warstwach.

### 5. Wzmocnij deterministic evidence tak, zeby AI rzadziej musial czytac repo

Praktyka:

- lepszy ranking stacktrace -> file chunk,
- lepszy ranking `container/service -> project`,
- dorzucenie kilku bardziej kontekstowych chunkow w deterministic mode,
- np. nie tylko failing method, ale tez bezposredni caller.

Zysk:

- mniej narzedziowego biegana po repo,
- mniejsza sesja,
- lepsza czytelnosc dla operatora.

### 6. Rozdziel diagnoze od opisu szerszego flow

Opcja dwuetapowa:

1. etap A:
   ustal najprawdopodobniejszy problem i czy trzeba jeszcze uzyc tools,
2. etap B:
   zbuduj finalny wynik dla operatora, lacznie z `affectedFunction`.

Zysk:

- bardziej kontrolowane repo exploration,
- mniej laczenia wszystkiego w jednym duzym promptcie.

## Sredni horyzont

### 1. Planner/executor pattern dla GitLab exploration

Zamiast zostawiac caly tok myslenia modelowi w jednym kroku:

- planner planuje 1-3 ruchy,
- executor wykonuje,
- writer sklada wynik.

Nie musi to byc od razu osobny model.
Mozna zaczac od jawnego wewnetrznego prompt protocol.

### 2. Context compression layer

Dzisiaj attachments sa 1:1 z evidence sections.

Mozliwe ulepszenie:

- osobny "incident digest" artifact,
- osobny "top candidate code map" artifact,
- osobny "runtime highlights" artifact.

Takie streszczenia moga:

- ograniczyc liczbe otwieranych artifacts,
- zmniejszyc koszt reasoning,
- pomoc modelowi szybciej wejsc w hipoteze.

### 3. Capability-aware model routing

Jesli SDK i srodowisko to wspieraja, rozdziel decyzje:

- tanszy / szybszy model do wstepnej klasyfikacji,
- mocniejszy model do finalnego writeup,
- albo ten sam model z rozna `reasoningEffort`.

Obecny kod juz ma properties:

- `analysis.ai.copilot.model`
- `analysis.ai.copilot.reasoning-effort`

To daje dobra baze do eksperymentow bez duzego refaktoru.

### 4. Lepsza wspolpraca ze skillami

Mozliwe kierunki:

- osobny skill dla "incident diagnosis core",
- osobny skill dla "gitlab exploration etiquette",
- osobny skill dla "handoff and ownership".

Dzisiaj wszystko jest w jednym skillu.
To jest wygodne, ale z czasem moze utrudnic iteracje.

## Dluzszy horyzont

### 1. Confidence scoring

Wynik powinien dostawac jawna ocene typu:

- `high`
- `medium`
- `low`

Oparta o:

- jak silne sa logs,
- czy jest deterministic code match,
- czy sa spojne runtime signals,
- czy model musial daleko eksplorowac repo,
- czy problem wyglada na wewnetrzny czy zewnetrzny.

### 2. Structured incident memory

Przyszly kierunek:

- zapamietywanie typowych incident patterns,
- mapowanie recurring symptoms na lepszy prompt context,
- ale bez powrotu do centralnego rule engine jako glownej diagnozy.

### 3. Runtime governance

W mocniej regulowanym srodowisku:

- mniej liberalny permission mode,
- audit trail dla tool usage,
- whitelisting skill directories,
- ograniczenie branch switching i repo scope.

## Najlepsza kolejnosc wdrazania

1. telemetry
2. twardszy response contract
3. ukrycie `group` i `branch` z GitLab tools
4. jeden flow preparation zamiast podwojnego budowania promptu
5. exploration budget
6. lepszy deterministic code context
7. ewentualnie dwuetapowy AI flow

## Proponowane eksperymenty

### Eksperyment 1

Hipoteza:

- JSON-only response zmniejszy fallbacki parsera.

Metrki:

- odsetek `AI_UNSTRUCTURED_RESPONSE`
- manualna ocena czytelnosci

### Eksperyment 2

Hipoteza:

- session-bound GitLab tools obniza liczbe blednych odczytow i skroci sesje.

Metrki:

- liczba tool calls,
- liczba nieudanych tool calls,
- czas sesji

### Eksperyment 3

Hipoteza:

- lepszy deterministic context zmniejszy potrzebe GitLab exploration.

Metrki:

- procent analiz bez zadnych tool calls,
- procent analiz tylko z 1-2 chunk reads,
- ocena trafnosci wyniku

### Eksperyment 4

Hipoteza:

- telemetry + budget uncina kosztowne outliery bez pogorszenia trafnosci.

Metrki:

- P50/P95 czasu sesji,
- P50/P95 liczby tool calls,
- manualna ocena trafnosci

## Miejsca w kodzie, gdzie najczesciej trzeba bedzie pracowac

- `CopilotSdkPreparationService`
- `CopilotSdkAnalysisAiProvider`
- `CopilotSdkToolBridge`
- `GitLabMcpTools`
- `ElasticMcpTools`
- `GitLabDeterministicEvidenceProvider`
- `SKILL.md`
- testy `analysis.ai.copilot.*`

## Czego nie robic przy optymalizacji Copilota

- nie wrzucac modeli adapter-specific do kontraktu AI,
- nie przenosic polityki eksploracji repo do adaptera GitLaba,
- nie rozszerzac tools o cale `/analysis`,
- nie rozwiazywac problemow promptu przez wrzucanie coraz dluzszego promptu,
- nie obchodzic skilli przez przypadkowe hardkodowanie stalych zasad w wielu
  miejscach naraz.
