# Functional And Technical Optimization Backlog

## Jak czytac backlog

To nie jest finalny roadmap commit.
To jest uporzadkowana lista tematow, ktore warto warsztatowo ocenic w GPT Pro.

Skala priorytetu:

- `P1` wysoki impact / bliski horyzont
- `P2` sredni impact albo wazny fundament
- `P3` dalszy horyzont

## Co jest juz zrobione i nie wraca jako backlog bazowy

Te tematy sa juz w kodzie i nie powinny wracac jako "pierwszy krok":

- session-bound GitLab tools,
- session-bound Database tools,
- application-scoped DB discovery,
- jawny backendowy `sessionId`,
- hidden `ToolContext` z audit metadata,
- podzial skilli na core, GitLab i data diagnostics.

## Funkcjonalne

### P1. Lepszy wynik koncowy dla operatora

Cel:

- wynik ma byc bardziej akcyjny i latwiejszy do handoffu.

Kierunki:

- confidence level,
- suggested owner / handoff target,
- oddzielenie confirmed facts od hypothesis w osobnych sekcjach UI,
- jawne wskazanie: wewnatrz naszego systemu vs poza nasza widocznoscia.

Dotkniete miejsca:

- `AnalysisResultResponse`
- provider AI
- frontend components wyniku

### P1. Czytelniejsza prezentacja evidence i tool traces w UI

Cel:

- operator ma szybciej rozumiec, co AI zobaczylo i co dociagnelo w trakcie
  sesji.

Kierunki:

- osobna sekcja Dynatrace signals,
- lepszy viewer GitLab code evidence,
- decyzja, czy pokazywac DB findings jako osobny stream diagnostyczny,
- agregacja deployment facts,
- szybkie highlighty najwazniejszych sygnalow.

Dotkniete miejsca:

- frontend
- `AnalysisJobResponse`
- `toolEvidenceSections`

### P2. Historia i porownywanie analiz

Cel:

- latwiejszy powrot do poprzednich incidentow i eksportow.

Kierunki:

- trwala historia jobow,
- porownanie dwoch analiz,
- seed dla recurring incidents.

### P2. Rozszerzenie operational context

Cel:

- lepsze dopasowanie ownership, bounded context i repo mapy.

Kierunki:

- wlaczenie w wybranych srodowiskach,
- lepsze curated files,
- sensowniejsze limity matchowania.

### P3. Sugerowanie dalszych pytan diagnostycznych

Cel:

- jesli widocznosc jest slaba, system powinien sugerowac, czego jeszcze brakuje.

Przyklady:

- brakujacy downstream logs,
- brak dostepu do DB capability,
- brak potwierdzenia stanu asynchronicznego procesu.

## Techniczne

### P1. Telemetry i budzetowanie pracy Copilota

Cel:

- mierzalnosc i kontrola kosztu.

Kierunki:

- structured session metrics,
- tool budget,
- attachment size metrics,
- per-analysis cost profile,
- osobne metryki dla GitLab i DB tools.

### P1. Twardszy kontrakt odpowiedzi AI

Cel:

- mniej `AI_UNSTRUCTURED_RESPONSE`.

Kierunki:

- JSON response,
- stricter validation,
- fallback response builder.

### P1. Audit i governance dla session-bound tools

Cel:

- lepsza kontrola uzycia GitLab i DB capability.

Kierunki:

- limity tool calls,
- limity chars / rows / queries,
- czytelniejszy audit trail,
- decyzja, czy i kiedy wolno wlaczyc raw SQL.

### P1. Lepsza wydajnosc GitLab reads

Cel:

- mniejszy koszt czytania plikow.

Aktualny problem:

- `readFileChunk(...)` pobiera raw content calego pliku, a potem tnie linie po
  stronie aplikacji.

Mozliwe kierunki:

- streaming lub range-like strategy,
- request cache dla raw file content,
- lepsza granularity chunkow.

### P2. Hardening integracji

Cel:

- lepsza odpornosc runtime.

Kierunki:

- retry policy,
- timeout policy,
- fallback strategy,
- lepsze error classification.

### P2. Persystencja jobow

Cel:

- historia, niezaleznosc od restartu procesu, lepszy operator workflow.

Ryzyko:

- to zmienia architekture i wymaga swiadomej decyzji.

### P2. Lepsze deterministic heurystyki

Cel:

- mniej pracy po stronie modelu.

Kierunki:

- trafniejsze `container/service -> project`,
- lepsze stacktrace filtering,
- dokladniejsze dobieranie caller/callee context.

### P2. Wersjonowanie promptu, skilli i artifacts

Cel:

- latwiejsze A/B testy i debugowanie regresji.

Kierunki:

- `promptVersion`
- `skillVersion`
- `artifactFormatVersion`

### P3. Multi-stage AI flow

Cel:

- osobno planning, osobno evidence gap filling, osobno final answer.

### P3. Incident pattern memory

Cel:

- szybsze rozpoznawanie powtarzalnych klas incydentow.

## Testing And Quality

### P1. Testy end-to-end dla analizy

Cel:

- pewnosc, ze sync/job/evidence/AI glue dziala razem.

### P1. Testy kontraktu prompt + skills + parser

Cel:

- wykrywanie regresji w shape odpowiedzi i zasadach sesji.

### P2. Golden datasets dla incidentow

Cel:

- porownywanie trafnosci po zmianach promptu, skills i heurystyk.

### P2. Testy wydajnosciowe sesji AI, GitLab exploration i DB diagnostics

Cel:

- zrozumienie P95 i kosztownych outlierow.

## Sugerowana kolejnosc backlogu

1. telemetry Copilota i tooli
2. twardszy response contract
3. budget i governance dla tooli
4. lepszy deterministic context
5. UI dla evidence, tool traces i wyniku
6. persystencja jobow
7. operational context rollout
8. multi-stage AI
