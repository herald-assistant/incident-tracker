# Krok 1: Cel Systemu

## Cel

Zrozumiec, co ta aplikacja robi dzisiaj jako produkt i jaki problem operacyjny
rozwiazuje.

## Po tym kroku rozumiesz

- dlaczego jedynym wejsciem biznesowym jest `correlationId`,
- jaki wynik ma dostac operator albo developer,
- dlaczego system jest `AI-first`, ale nie jest "AI-only",
- jakie dane sa zbierane zanim model zacznie interpretacje.

## Glowny model

System dostaje `correlationId`, zbiera evidence z systemow zewnetrznych,
uzupelnia je o deployment context i operational context, a dopiero potem oddaje
to do providera AI.

Wynik ma byc praktyczny:

- nazwa problemu,
- krotkie podsumowanie,
- rekomendowany kolejny krok,
- uzasadnienie oparte o evidence.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisRequest.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisResultResponse.java`
- `docs/architecture/01-system-overview.md`
- `docs/architecture/02-key-decisions.md`

## Sprawdz lokalnie

- uruchom backend i otworz `GET /`,
- zobacz, ze UI prosi tylko o `correlationId`,
- przejrzyj strukture odpowiedzi API dla `POST /analysis` albo job flow.

## Checkpoint

- Dlaczego `branch` nie przychodzi z requestu?
- Jakie sa dwa glowne etapy systemu przed zwroceniem wyniku?
- Co oznacza w tym projekcie `AI-first`?
