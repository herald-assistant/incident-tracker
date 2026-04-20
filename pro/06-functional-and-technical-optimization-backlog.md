# Functional And Technical Optimization Backlog

## Jak czytac backlog

To nie jest finalny roadmap commit.
To jest uporzadkowana lista tematow, ktore warto warsztatowo ocenic w GPT Pro.

Skala priorytetu:

- `P1` wysoki impact / bliski horyzont
- `P2` sredni impact albo wazny fundament
- `P3` dalszy horyzont

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

### P1. Czytelniejsza prezentacja evidence w UI

Cel:

- operator ma szybciej rozumiec, co AI zobaczylo.

Kierunki:

- osobna sekcja Dynatrace signals,
- lepszy viewer GitLab code evidence,
- agregacja deployment facts,
- szybkie highlighty najwazniejszych sygnalow.

Dotkniete miejsca:

- frontend
- `AnalysisJobResponse`

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
- brak dostepu do DB,
- brak potwierdzenia stanu asynchronicznego procesu.

## Techniczne

### P1. Telemetry i budzetowanie pracy Copilota

Cel:

- mierzalnosc i kontrola kosztu.

Kierunki:

- structured session metrics,
- tool budget,
- attachment size metrics,
- per-analysis cost profile.

### P1. Twardszy kontrakt odpowiedzi AI

Cel:

- mniej `AI_UNSTRUCTURED_RESPONSE`.

Kierunki:

- JSON response,
- stricter validation,
- fallback response builder.

### P1. Zmniejszenie swobody GitLab tools

Cel:

- mniej losowej eksploracji repo.

Kierunki:

- session-bound group/branch,
- tighter DTOs,
- mozliwy osobny "read deterministic candidate" tool.

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

### P2. Wersjonowanie promptu, skilla i artifacts

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

### P1. Testy kontraktu prompt + skill + parser

Cel:

- wykrywanie regresji w shape odpowiedzi i zasadach sesji.

### P2. Golden datasets dla incidentow

Cel:

- porownywanie trafnosci po zmianach promptu, skillu i heurystyk.

### P2. Testy wydajnosciowe sesji AI i GitLab exploration

Cel:

- zrozumienie P95 i kosztownych outlierow.

## Sugerowana kolejnosc backlogu

1. telemetry Copilota
2. twardszy response contract
3. session-bound GitLab tools
4. lepszy deterministic context
5. UI dla evidence i wyniku
6. persystencja jobow
7. operational context rollout
8. multi-stage AI
