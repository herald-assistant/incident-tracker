# Krok 1: Cel Systemu

## Cel

Zrozumiec, co ta aplikacja robi dzisiaj jako produkt i jaki problem operacyjny
rozwiazuje.

## Po tym kroku rozumiesz

- dlaczego jedynym wejsciem biznesowym jest `correlationId`,
- jaki wynik ma dostac operator albo developer,
- dlaczego system jest `AI-first`, ale nie jest "AI-only",
- jakie dane sa zbierane zanim model zacznie interpretacje,
- co operator moze zrobic po finalnym wyniku przez follow-up chat.

## Glowny model

System dostaje `correlationId`, zbiera evidence z systemow zewnetrznych,
uzupelnia je o deployment context i operational context, a dopiero potem oddaje
to do providera AI.

Wynik ma byc praktyczny:

- nazwa problemu,
- krotkie podsumowanie,
- rekomendowany kolejny krok,
- uzasadnienie oparte o evidence.

Po zakonczonej analizie live joba operator moze kontynuowac prace w formie
chatu: dopytac o wynik, poprosic o sprawdzenie dodatkowego faktu w repo albo
DB tools, albo wygenerowac raport z juz zebranym kontekstem. To nie jest nowa
analiza od zera, tylko kontynuacja nad tym samym snapshotem evidence i
scope'em tools wyprowadzonym przez backend.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisRequest.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/flow/AnalysisResultResponse.java`
- `docs/architecture/01-system-overview.md`
- `docs/architecture/02-key-decisions.md`

## Sprawdz lokalnie

- uruchom backend i otworz `GET /`,
- zobacz, ze UI prosi tylko o `correlationId`,
- przejrzyj strukture odpowiedzi API dla `POST /analysis` albo job flow,
- po zakonczonym live jobie sprawdz, ze chat reuse'uje `chatMessages` z
  `GET /analysis/jobs/{analysisId}`.

## Checkpoint

- Dlaczego `branch` nie przychodzi z requestu?
- Jakie sa dwa glowne etapy systemu przed zwroceniem wyniku?
- Co oznacza w tym projekcie `AI-first`?
- Dlaczego follow-up chat nie przyjmuje recznego `environment`, branch ani
  GitLab group?
