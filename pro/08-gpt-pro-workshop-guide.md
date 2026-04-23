# GPT Pro Workshop Guide

## Po co ten plik

To jest gotowy przewodnik, jak prowadzic sesje w GPT Pro nad tym projektem bez
gubienia kontekstu i bez lamania glownych niezmiennikow architektury.

## Minimalny pakiet do wklejenia do GPT Pro

Jesli sesja ma byc krotka, daj GPT Pro:

1. `pro/README.md`
2. `pro/01-system-and-product-context.md`
3. `pro/02-architecture-and-code-map.md`
4. `pro/03-runtime-contracts-and-configuration.md`
5. `pro/04-copilot-sdk-current-state.md`

Jesli sesja ma dotyczyc roadmapy, dorzuc jeszcze:

- `pro/05-copilot-sdk-optimization-playbook.md`
- `pro/06-functional-and-technical-optimization-backlog.md`
- `pro/07-open-questions-and-decision-register.md`

## Zasady, ktore GPT Pro ma respektowac

Wklej jako stale ograniczenia:

```text
Traktuj ponizsze decyzje jako twarde, dopoki nie uzasadnisz swiadomie zmiany:
- POST /analysis przyjmuje tylko correlationId
- gitLabGroup pochodzi z konfiguracji aplikacji
- gitLabBranch i environment sa wyprowadzane z evidence
- glowny flow jest AI-first
- evidence providers sa sekwencyjne na AnalysisContext
- GitLab deterministic evidence, GitLab tools i DB tools to osobne capability
- GitLab i DB tools sa session-bound przez hidden ToolContext
- DB discovery jest application-scoped przez applicationNamePattern, nie schemaPattern
- skill Copilota jest runtime resource aplikacji
- nie mieszaj klas adapter-specific do kontraktu AI
- nie proponuj globalnego trust-all SSL
```

## Najlepsze typy sesji

### 1. Architecture review

Prompt:

```text
Na podstawie dolaczonych plikow z katalogu /pro przygotuj architecture review projektu.
Skup sie na:
1. glownych mocnych stronach obecnej architektury
2. miejscach o najwiekszym ryzyku funkcjonalnym i technicznym
3. punktach, gdzie architektura utrudnia optymalizacje Copilot SDK
4. rekomendacjach zmian w kolejnosci od najwiekszego impactu

Nie proponuj zmian, ktore lamia twarde niezmienniki projektu.
Daj wynik jako:
- executive summary
- top 7 findings
- proposed roadmap na 30 / 60 / 90 dni
```

### 2. Copilot optimization session

Prompt:

```text
Na podstawie plikow /pro zaprojektuj plan optymalizacji wykorzystania GitHub Copilot Java SDK.
Rozdziel wynik na:
1. quick wins
2. medium-term architecture changes
3. measurements and telemetry
4. experiment plan
5. ryzyka i rollback conditions

W kazdym punkcie wskaz:
- po co zmiana
- jakich klas lub modulow dotknie
- jak zmierzyc efekt

Uwzglednij, ze session-bound GitLab i DB tools sa juz wdrozone.
```

### 3. Product and operator workflow session

Prompt:

```text
Na podstawie plikow /pro przygotuj propozycje rozwoju funkcjonalnego produktu dla operatora incydentow.
Skup sie na:
- czytelnosci wyniku
- handoffach
- prezentacji evidence
- historii analiz
- roli operational context
- widocznosci GitLab i DB tool traces

Daj wynik jako backlog z priorytetami P1/P2/P3 oraz uzasadnieniem biznesowym.
```

### 4. Reliability and hardening session

Prompt:

```text
Na podstawie plikow /pro przygotuj technical hardening review.
Skup sie na:
- retry i timeout strategy
- failure handling
- persystencji jobow
- telemetry i observability
- ryzykach integracyjnych Elasticsearch / Dynatrace / GitLab / Oracle DB / Copilot

Daj wynik jako:
- top risks
- proposed mitigations
- minimal hardening plan
```

### 5. ADR session

Prompt:

```text
Na podstawie plikow /pro przygotuj 3 alternatywne decyzje architektoniczne dla tematu:
[tu wstaw temat, np. "exploration budget dla GitLab i DB tools" albo "JSON response contract dla Copilota"].

Dla kazdej opcji daj:
- context
- proposed change
- benefits
- tradeoffs
- impact on current classes/modules
- recommendation
```

### 6. Data diagnostics governance session

Prompt:

```text
Na podstawie plikow /pro przygotuj decyzje architektoniczna dla rolloutu DB capability.
Skup sie na:
- analysis.database.enabled
- application-scoped discovery
- query budgets
- raw SQL governance
- audit i UI projection wynikow DB tools

Daj wynik jako ADR z recommended rollout stages.
```

## Jakich wynikow oczekiwac od GPT Pro

Najbardziej uzyteczne sa:

- ADR-y
- experiment plans
- backlogi z impact / effort
- architecture reviews
- explicit tradeoff analyses

Mniej uzyteczne beda:

- bardzo ogolne eseje o "best practices",
- rady oderwane od konkretnych klas i flow tego repo,
- propozycje, ktore ignoruja ograniczenia requestu `/analysis`.

## Dobra praktyka pracy iteracyjnej

Zamiast jednego wielkiego promptu lepiej prowadzic sesje w 3 krokach:

1. review stanu obecnego
2. wygenerowanie 2-4 sensownych opcji
3. porownanie opcji i wybor jednej z planem wdrozenia

## Co warto prosic GPT Pro, zeby zawsze zwracal

Przy powazniejszych tematach pros o stale sekcje:

- assumptions
- source-backed observations
- proposed changes
- impacted modules
- risks
- success metrics

## Czego nie delegowac slepo do GPT Pro

- zmian lamacych invarianty requestu `/analysis`
- mieszania adapterow z AI layer
- przenoszenia heurystyk incidentowych do MCP tools albo adapterow
- refaktorow bez wskazania konkretnych punktow w kodzie

## Najlepszy sposob zamykania sesji

Na koniec pros GPT Pro o jeden z tych rezultatow:

1. "przygotuj decyzje w formacie ADR"
2. "przygotuj backlog implementacyjny w kolejnosci wdrazania"
3. "przygotuj plan eksperymentu wraz z metrykami"
4. "wskaz, ktore klasy i testy trzeba dotknac jako pierwsze"
