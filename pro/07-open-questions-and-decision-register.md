# Open Questions And Decision Register

## Decyzje juz podjete

Te rzeczy traktuj jako ustalone, dopoki nie ma swiadomej zmiany architektonicznej:

1. `correlationId` jest jedynym biznesowym polem requestu glownego flow.
2. `gitLabGroup` pochodzi z konfiguracji aplikacji.
3. `gitLabBranch` i `environment` sa rozwiazywane z evidence.
4. Glowny flow jest `AI-first`.
5. Evidence providers sa sekwencyjne i pracuja na wspolnym `AnalysisContext`.
6. Skill Copilota jest runtime resource aplikacji.
7. GitLab deterministic evidence i GitLab MCP tools sa osobnymi capability.
8. Dynatrace nie jest dzisiaj tool-em runtime.
9. Ignorowanie SSL jest lokalne per integracja.
10. Job state jest obecnie w pamieci procesu.

## Otwarte pytania strategiczne

### 1. Jak mierzymy sukces analizy?

Brakuje jeszcze jawnej odpowiedzi:

- czy najwazniejsza jest trafnosc?
- czy szybkosc?
- czy jakosc handoffu?
- czy ograniczenie liczby tool calls?

Bez tego trudno uczciwie optymalizowac Copilota.

### 2. Czy finalny wynik ma byc bardziej ekspercki czy bardziej operatorski?

Obecny prompt jest ustawiony pod operatora / testera / mid-level developera.
To jest dobra decyzja, ale warto ja potwierdzic jako produktowy target.

### 3. Czy GitLab tools maja pozostac ogolne, czy maja byc session-bound?

To jedno z kluczowych pytan dla optymalizacji Copilot SDK.

### 4. Czy chcemy jeden etap AI, czy dwa?

Opcje:

- jeden etap:
  prosciej, taniej implementacyjnie
- dwa etapy:
  lepsza kontrola repo exploration i wyniku

### 5. Czy Dynatrace powinien dostac w przyszlosci tools?

Za:

- lepsze dogrywanie runtime context.

Przeciw:

- wiekszy koszt,
- wieksza zlozonosc sesji,
- ryzyko przesuniecia uwagi z logs i repo.

### 6. Czy operational context ma byc rolloutowany szerzej?

Dzisiaj:

- capability istnieje,
- ale jest domyslnie wylaczone.

### 7. Czy potrzebna jest persystencja jobow?

To mocno zmienia model systemu:

- historia,
- niezaleznosc od restartu,
- mozliwy backlog operatora,
- ale tez wieksza zlozonosc.

### 8. Jak mocno chcemy zamknac permission model Copilota?

Aktualny stan jest wygodny developersko, ale moze byc zbyt liberalny dla
bardziej restrykcyjnego srodowiska.

## Otwarte pytania techniczne

### 1. Jak duze sa realne artifacts attachments?

Bez telemetry nie wiemy:

- czy attachment strategy realnie zmniejsza koszt,
- czy model nie otwiera zbyt wielu artefaktow,
- czy nie produkujemy zbyt duzo sekcji i itemow.

### 2. Ktore elementy deterministic evidence daja najwiekszy zysk?

Do pomiaru:

- logs only
- logs + deployment
- logs + deployment + Dynatrace
- logs + deployment + GitLab deterministic

### 3. Ile realnie kosztuje repo exploration?

Do zmierzenia:

- liczba `searchRepositoryCandidates`,
- liczba `readFile`,
- liczba `readFileChunk`,
- wielkosc plikow,
- czas odpowiedzi GitLaba.

### 4. Czy parser tekstowy jest jeszcze wystarczajacy?

Pytanie praktyczne:

- czy poprawiac parser,
- czy przejsc na mocniejszy structured output.

### 5. Czy `affectedFunction` powinno pozostac polem wymaganym?

Dzisiaj to dobry guardrail dla handoffu i zrozumialosci.
Warto jednak swiadomie potwierdzic, czy fallback z powodu braku tego pola jest
pozadany.

## Rekomendowane metryki do wprowadzenia

### Metryki trafnosci

- manualny score diagnozy,
- manualny score `affectedFunction`,
- manualny score `recommendedAction`

### Metryki runtime

- total analysis latency,
- evidence latency,
- AI latency,
- tool latency,
- P50 / P95

### Metryki kosztu eksploracji

- tool calls per session,
- read file count,
- read chunk count,
- total chars returned by GitLab,
- total chars returned by Elastic tool

### Metryki struktury odpowiedzi

- rate `AI_UNSTRUCTURED_RESPONSE`,
- rate missing `affectedFunction`,
- rate fallback prompt review recommendation

## Rekomendowany format decyzji z GPT Pro

Dla kazdego wiekszego tematu warto prosic GPT Pro o wynik w jednym z formatow:

### ADR

- context
- options
- decision
- consequences

### Experiment memo

- hypothesis
- change
- metrics
- success threshold
- rollback condition

### Backlog memo

- problem
- user value
- technical value
- scope touched
- impact / effort
