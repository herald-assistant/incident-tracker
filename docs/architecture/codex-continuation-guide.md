# Codex Continuation Guide

## Cel

Ten dokument jest krotka mapa wznowienia pracy w nowej sesji Codexa. Nie
powtarza pelnego runtime, decyzji ani backlogu. Wskazuje, gdzie znajduje sie
kanoniczny opis i kod.

## Kolejnosc startowa

1. Przeczytaj root `AGENTS.md`.
2. Przeczytaj `docs/AGENTS.md`.
3. Przeczytaj `product-direction.md`, `system-overview.md`,
   `key-decisions.md` i `package-dependencies.md`.
4. Dla Incident Analysis przeczytaj `incident-analysis-runtime-flow.md`.
5. Dla Operational Context przeczytaj
   `operational-context-model-tools-and-usage.md`.
6. Dla nowego feature'a albo zmiany L1-L3 przeczytaj
   `analysis-feature-delivery-playbook.md`.
7. Odszukaj odpowiadajacy dokument w `../needs/` i zatwierdzony plan w
   `../plans/`, jesli zmiana ma aktywny albo wymagany zakres wykonawczy.
8. Przeczytaj lokalne `AGENTS.md` dla wszystkich dotykanych katalogow.

Brak planu nie jest zgoda na wyprowadzenie rozwiazania bezposrednio z business
need. Zasady tworzenia i zatwierdzania planow sa w `../AGENTS.md`.

## Kanoniczne dokumenty

- `product-direction.md`
  oferta produktu, rodziny feature'ow i model rozszerzalnosci.
- `system-overview.md`
  high-level stan aplikacji, publiczne wejscia oraz mapa ownership.
- `key-decisions.md`
  trwale decyzje i konsekwencje.
- `incident-analysis-runtime-flow.md`
  szczegolowy runtime pierwszego feature'a referencyjnego.
- `package-dependencies.md`
  dozwolony graf zaleznosci backendu i frontendu.
- `operational-context-model-tools-and-usage.md`
  model katalogu, API, tools, maintenance i zasady uzycia.
- `analysis-feature-delivery-playbook.md`
  procedure bezpiecznego dostarczania i rozwoju feature'ow.
- `../needs/`
  aktywne albo potrzebne dla kolejnego inkrementu opisy problemu, wartosci,
  success criteria i ograniczen.
- `../plans/`
  zatwierdzane propozycje wykonania oraz checklisty aktywnego backlogu; nie
  jest to archiwum zakonczonych prac.

Gdy kod i dokumentacja sa rozbiezne, nie wybieraj automatycznie nowszego
timestampu. Ustal ownership kontraktu, sprawdz testy oraz runtime i zastosuj
priorytet zrodel opisany w playbooku. Niezdecydowany target pozostaje planem;
zatwierdzony invariant trafia do architektury.

## Mapa backendu

### Feature'y

- `src/main/java/pl/mkn/tdw/features/incidentanalysis`
  Incident Analysis: job, evidence, flow, AI contracts i Copilot preparation.
- `src/main/java/pl/mkn/tdw/features/flowexplorer`
  Flow Explorer: discovery, run, wynik i feature-owned AI behavior.
- `src/main/java/pl/mkn/tdw/features/changeverification`
  Change Verification: zgodnosc zmiany i jego wlasny kontrakt feature'a.

Feature'y sa rodzenstwem. Nie importuja siebie wzajemnie.

### Reusable platform i capability

- `src/main/java/pl/mkn/tdw/aiplatform`
  neutralny runtime AI, sesje, tools invocation, policies, budgets i usage.
- `src/main/java/pl/mkn/tdw/agenttools`
  neutralne capability tools oraz MCP exposure nad integracjami.
- `src/main/java/pl/mkn/tdw/integrations`
  adaptery do zewnetrznych systemow.
- `src/main/java/pl/mkn/tdw/api`
  shared/operator API niezalezne od jednego feature'a.
- `src/main/java/pl/mkn/tdw/shared`
  male neutralne kontrakty evidence, AI i run UI.
- `src/main/java/pl/mkn/tdw/common`
  male helpery techniczne.
- `src/main/java/pl/mkn/tdw/localworkspace`
  neutralny lokalny zapis ustawien, token references i kopert runow.

Zawsze potwierdz kierunek importu w `package-dependencies.md` i
`PackageDependencyGuardTest`.

## Mapa frontendu

- `frontend/src/app/core`
  neutralne modele, API services, auth i shared state.
- `frontend/src/app/components`
  reusable komponenty workflow operatora.
- `frontend/src/app/features`
  strony i prezentacja specyficzna dla feature'ow.
- shell i routing aplikacji
  composition root nawigacji oraz rejestracji feature'ow.

Nowy ekran reuse'uje `core` i `components`, ale nie importuje modelu albo
komponentu innego feature'a tylko dlatego, ze wyglada podobnie.

## Runtime resources

- `src/main/resources/copilot/skills`
  zrodlo skilli pakowanych do runtime. Przy starcie platforma odtwarza ich
  pelny mirror pod `${analysis.ai.copilot.copilot-home}/skills`, domyslnie
  `tdw-data/copilot/skills`. Tresc procedur pozostaje po polsku.
- `src/main/resources/operational-context`
  katalog systemow, procesow, bounded contexts, repozytoriow i handoffow.
- `src/main/resources/static`
  wygenerowany bundle frontendu; nie edytuj go recznie.

## Procedura kontynuacji zmiany

1. Sprawdz worktree i zachowaj cudze zmiany.
2. Nazwij potrzebe oraz ownership zmiany.
3. Sklasyfikuj zmiane L0-L3 wedlug playbooka.
4. Dla L1-L3 wykonaj baseline, conformance delta i liste konsumentow.
5. Sprawdz istniejacy dokument potrzeby i plan, jezeli dotycza zmiany.
6. Jezeli plan jest wymagany, ale go brakuje albo zakres sie zmienil,
   zaktualizuj `../plans/` zgodnie z `../AGENTS.md` i uzyskaj zatwierdzenie.
7. Implementuj tylko zatwierdzone kroki, reuse-first.
8. Po kazdym kroku wykonaj przypisana weryfikacje i oznacz `[x]` dopiero po
   spelnieniu kryterium akceptacji.
9. Po zakonczeniu zaktualizuj wynikowy stan w dokumentach architektonicznych.
10. Usun zakonczony plan, gdy nie ma juz roli operacyjnej; historie zachowuje
    Git.

## Szczegolna ostroznosc przy Copilot SDK

Gdy Java SDK, bytecode albo lokalny artefakt nie wyjasnia semantyki opcji,
sprawdz upstream `github/copilot-sdk`, zwlaszcza `nodejs/README.md` oraz
schemat/protokol `@github/copilot`. Nie zgaduj defaultow, limitow,
`infiniteSessions`, workspace sesji ani zachowania skill directories.

`SessionConfig.skillDirectories` i `ResumeSessionConfig.skillDirectories`
otrzymuja dokladnie jeden wspolny root `${copilot-home}/skills` zawierajacy
wszystkie podkatalogi skilli z `SKILL.md`. Feature nie przekazuje katalogow ani
podzbioru nazw skilli. Runtime przekazuje do `MessageOptions` tylko wykonany
prompt. Zmiana delivery mode, session semantics, allowlisty albo hidden scope
jest zmiana architektoniczna i wymaga planu, testow oraz rollbacku.

## Weryfikacja

Dobierz zakres do zmiany, zaczynajac od testow celowanych. Pelne komendy:

- `mvn -q clean test`
- `npm --prefix frontend test -- --watch=false`
- `npm --prefix frontend run build`
- `mvn -q -DskipTests package`

Po zmianie dokumentacji sprawdz wszystkie referencje, brak starych nazw i
`git diff --check`.
