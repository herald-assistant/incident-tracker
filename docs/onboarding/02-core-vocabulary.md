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
modele, properties i services. Stabilne helper endpointy FE/operatora sa
shared/operator API w `api.*`, ale deleguja do adapterow.
Pakiet dla adapterow to `integrations.<system>`. Historyczny
`analysis.adapter` nie jest juz miejscem na nowe capability.

### Tool

Jawne narzedzie wystawione przez Spring AI i reuse'owane przez sesje Copilota.
Tool ma maly kontrakt i robi jeden konkretny krok eksploracji.

### Shared/operator API

Endpoint backendu dla frontendu albo operatora, ktory nie nalezy do jednego
dedykowanego feature'a. Moze byc fasada nad platforma AI albo integracja, np.
`GET /analysis/ai/options` albo `POST /api/elasticsearch/logs/search`. Pakiet
to `api.*`; endpointy konkretnego use case'u zostaja przy
`features.<feature>.api`.

### MCP

W tym projekcie to warstwa ekspozycji tools po stronie Spring AI. Nazwa
`analysis.mcp` historycznie grupowala narzedzia; reusable wrappery mieszkaja
teraz w `agenttools.<capability>.mcp`. Ich logika deleguje dalej do adapterow
albo use case'ow.

### Prompt

Operacyjna tresc requestu dla modelu, budowana z aktualnego incydentu i
zebranego evidence.

### Skill

Stale instrukcje runtime dla Copilota, pakowane jako resource aplikacji i
ladowane do sesji przez provider AI.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/features/incidentanalysis/evidence/AnalysisContext.java`
- `src/main/java/pl/mkn/incidenttracker/features/incidentanalysis/evidence/AnalysisEvidenceProvider.java`
- `src/main/java/pl/mkn/incidenttracker/integrations`
- `src/main/java/pl/mkn/incidenttracker/agenttools`
- `src/main/java/pl/mkn/incidenttracker/features/incidentanalysis/ai`

## Checkpoint

- Czy provider evidence moze bezposrednio robic `@Tool`?
- Czy adapter powinien znac prompt AI?
- Czym rozni sie helper endpoint `/api/...` od toola wywolywanego przez model?
