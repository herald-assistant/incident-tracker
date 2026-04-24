# Krok 2: Slownik Pojec

## Cel

Zbudowac wspolny jezyk, zanim wejdziesz w pakiety i klasy.

## Po tym kroku rozumiesz

- czym rozni sie adapter od providera evidence,
- czym rozni sie tool od helper endpointu,
- czym rozni sie prompt od skilla,
- gdzie konczy sie orchestration, a gdzie zaczyna integracja.

## Najwazniejsze pojecia

### `AnalysisContext`

Stan sekwencyjnego pipeline evidence. Niesie `correlationId` i sekcje evidence
zebrane przez poprzednie kroki.

### `AnalysisEvidenceProvider`

Jeden krok pipeline, ktory czyta `AnalysisContext` i publikuje jedno
`AnalysisEvidenceSection`.

### Adapter

Warstwa capability blisko integracji albo zrodla danych, np. Elasticsearch,
Dynatrace, GitLab, Database albo curated operational context. Ma porty,
modele, properties i ewentualnie helper endpointy do recznego testowania.

### Tool

Jawne narzedzie wystawione przez Spring AI i reuse'owane przez sesje Copilota.
Tool ma maly kontrakt i robi jeden konkretny krok eksploracji.

### MCP

W tym projekcie to warstwa ekspozycji tools po stronie Spring AI. Nazwa
`analysis.mcp` grupuje narzedzia, ale ich logika deleguje dalej do adapterow
albo use case'ow.

### Prompt

Operacyjna tresc requestu dla modelu, budowana z aktualnego incydentu i
zebranego evidence.

### Skill

Stale instrukcje runtime dla Copilota, pakowane jako resource aplikacji i
ladowane do sesji przez provider AI.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisContext.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/evidence/AnalysisEvidenceProvider.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/adapter`
- `src/main/java/pl/mkn/incidenttracker/analysis/mcp`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai`

## Checkpoint

- Czy provider evidence moze bezposrednio robic `@Tool`?
- Czy adapter powinien znac prompt AI?
- Czym rozni sie helper endpoint `/api/...` od toola wywolywanego przez model?
