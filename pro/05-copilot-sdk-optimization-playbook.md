# Copilot SDK Optimization Playbook

## Cel optymalizacji

Optymalizacja Copilot SDK w tym projekcie powinna jednoczesnie poprawic:

1. trafnosc diagnozy,
2. stabilnosc shape odpowiedzi,
3. koszt i czas sesji,
4. przewidywalnosc pracy z GitLab i DB tools,
5. debuggability i governance.

## Co juz jest wdrozone

To sa rzeczy, ktore jeszcze niedawno byly planem, a dzisiaj sa juz stanem kodu:

- session-bound GitLab tools,
- session-bound Database tools,
- jawny backendowy `sessionId`,
- hidden `ToolContext` z `correlationId`, `environment`, `gitLabGroup`,
  `gitLabBranch`, `analysisRunId`, `copilotSessionId`, `toolCallId`,
  `toolName`,
- application-scoped DB discovery przez `applicationNamePattern`,
- warunkowe wlaczanie DB capability przez `analysis.database.enabled`,
- trzy runtime skille zamiast jednego duzego pakietu zasad.

To przesuwa ciezar optymalizacji z "napraw kontrakt tooli" na
"zmierz, ogranicz i uporzadkuj eksploatacje runtime".

## Najwazniejsze dzwignie na teraz

### A. Twardszy response contract

Obecny parser nadal bazuje na tekscie.

Rekomendacja:

- przejsc na JSON-only response contract albo fenced JSON block,
- zachowac czytelny fallback, ale zmniejszyc zaleznosc od tekstowego parsera.

Zysk:

- mniej `AI_UNSTRUCTURED_RESPONSE`,
- mniej parser maintenance,
- prostszy frontend i downstream debug.

### B. Telemetry per analysis session

Zbieraj jawnie:

- liczbe sekcji evidence,
- liczbe itemow evidence,
- liczbe attachment files,
- rozmiar attachment artifacts,
- liczbe tool calls per type,
- liczbe GitLab reads per strategy,
- liczbe DB queries per tool,
- latency:
  - evidence collection,
  - prompt preparation,
  - Copilot execution,
  - tools.

Zysk:

- da sie porownywac eksperymenty,
- latwiej wykryc regresje,
- mozna wreszcie zamknac dyskusje "czy session-bound tools realnie pomagaja".

### C. Exploration budget

Przyklady:

- max `gitlab_read_repository_file` calls na sesje,
- max laczny rozmiar odczytanego kodu,
- preferowanie `chunk` nad `file`,
- max liczba DB queries,
- max rows / chars zwroconych przez DB per sesja,
- sygnal ostrzegawczy, gdy agent zaczyna "browse'owac" zamiast diagnozowac.

Mozna to zrobic:

- w skillu jako zasada,
- w backendzie jako twarda kontrola,
- najlepiej w obu warstwach.

### D. Lepszy deterministic context

Praktyka:

- lepszy ranking stacktrace -> file chunk,
- lepszy ranking `container/service -> project`,
- dorzucenie kilku bardziej kontekstowych chunkow w deterministic mode,
- np. nie tylko failing method, ale tez bezposredni caller.

Zysk:

- mniej narzedziowego biegana po repo,
- mniejsza sesja,
- lepsza czytelnosc dla operatora.

### E. Jasny audit trail dla DB capability

Kolejny logiczny krok po session-bound DB tools to:

- policzalne logi query budgetu,
- lepsze rozroznienie typed tools vs raw SQL,
- decyzja, czy DB tool results maja trafic do UI jako osobny strumien
  diagnostyczny.

## Quick wins

### 1. Przestan budowac prompt dwa razy

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

### 2. Dodaj telemetry do Copilot + tools

Najpierw lightweight:

- structured log event per analysis,
- liczniki tool calls,
- liczniki bytes/chars,
- latency buckets.

### 3. Dodaj budget enforcement

Najpierw soft:

- warnings w logach,
- threshold alerts.

Potem hard:

- odciecie dalszych expensive tooli po przekroczeniu budzetu.

### 4. Rozszerz evidence capture policy

Rozwaz:

- czy outline z GitLaba ma byc pokazywany w UI,
- czy DB findings powinny miec osobna projekcje,
- czy operator ma widziec tylko "fetched code", czy tez "validated data facts".

## Sredni horyzont

### 1. Planner/executor pattern dla repo i DB exploration

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
- osobny "runtime highlights" artifact,
- opcjonalnie osobny "data diagnostics summary" artifact, jesli DB tools byly
  uzyte.

Takie streszczenia moga:

- ograniczyc liczbe otwieranych artifacts,
- zmniejszyc koszt reasoning,
- pomoc modelowi szybciej wejsc w hipoteze.

### 3. Capability-aware model routing

Jesli SDK i srodowisko to wspieraja, rozdziel decyzje:

- tanszy / szybszy model do wstepnej klasyfikacji,
- mocniejszy model do finalnego writeupu,
- albo ten sam model z rozna `reasoningEffort`.

Obecny kod juz ma properties:

- `analysis.ai.copilot.model`
- `analysis.ai.copilot.reasoning-effort`

To daje dobra baze do eksperymentow bez duzego refaktoru.

### 4. Lepsza wspolpraca ze skillami

Obecny podzial na trzy skille jest juz lepszy niz dawny model "jeden skill do
wszystkiego", ale nadal mozna dopracowac:

- zakres core vs gitlab vs data diagnostics,
- wersjonowanie i rollout zmian,
- testy kontraktu prompt + skills + parser.

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
- czy model musial daleko eksplorowac repo albo DB,
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
- ograniczenie exploration budgetu i raw SQL.

## Najlepsza kolejnosc wdrazania

1. telemetry
2. twardszy response contract
3. exploration budget
4. jeden flow preparation zamiast podwojnego budowania promptu
5. lepszy deterministic code context
6. audit i UI projection dla DB capability
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

- telemetry + budget ogranicza kosztowne sesje bez spadku trafnosci.

Metrki:

- P50/P95 czasu sesji
- P50/P95 liczby tool calls
- manualna ocena trafnosci

### Eksperyment 3

Hipoteza:

- lepszy deterministic context zmniejszy potrzebe GitLab exploration.

Metrki:

- procent analiz bez zadnych tool calls,
- procent analiz tylko z 1-2 chunk reads,
- ocena trafnosci wyniku

### Eksperyment 4

Hipoteza:

- session-bound DB tools poprawiaja groundedness dla incydentow data-related
  bez niekontrolowanego browse'owania schematow.

Metrki:

- liczba query per sesja,
- procent sesji konczacych sie bez raw SQL,
- manualna ocena "czy wynik odroznia kod od danych".

## Miejsca w kodzie, gdzie najczesciej trzeba bedzie pracowac

- `CopilotSdkPreparationService`
- `CopilotSdkAnalysisAiProvider`
- `CopilotSdkToolBridge`
- `GitLabMcpTools`
- `DatabaseMcpTools`
- `DatabaseToolService`
- `ElasticMcpTools`
- `GitLabDeterministicEvidenceProvider`
- `SKILL.md`
- testy `analysis.ai.copilot.*`

## Czego nie robic przy optymalizacji Copilota

- nie wrzucac modeli adapter-specific do kontraktu AI,
- nie przenosic polityki eksploracji repo ani DB do adapterow,
- nie rozszerzac tools o cale `/analysis`,
- nie rozwiazywac problemow promptu przez wrzucanie coraz dluzszego promptu,
- nie obchodzic skilli przez przypadkowe hardkodowanie stalych zasad w wielu
  miejscach naraz.
