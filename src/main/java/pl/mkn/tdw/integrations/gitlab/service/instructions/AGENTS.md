# AGENTS

## Zakres

Ten pakiet jest czescia integracji GitLab i odpowiada za odkrywanie
repozytoryjnych instrukcji dla code source. Odnajduje globalne i lokalne
`AGENTS.md`, `.github/copilot-instructions.md` oraz repozytoryjne pliki
referencjonowane z instrukcji.

Jawne `discoverApplicable` jest osobna operacja dla `.github/**/*.instructions.md`
na przypietym SHA. Dobiera `applyTo` do zadanych sciezek: obsluguje rozdzielone
przecinkiem globy z `*`, `**` i `?`; brak applyTo oznacza zakres repozytorium.
Nieobslugiwane wzorce (m.in. grupy klamrowe, klasy znakow i negacja), bledny YAML,
obciecie i limit discovery sa jawne. Nie zmieniaj przez te operacje zachowania
dotychczasowego `discover` ani nie dociagaj referencji i modulow poza zakresem.

## Zasady

- Nie importuj `analysis.*`, `agenttools.*`, `features.*`, `api.*` ani
  `aiplatform.*`.
- Czytanie plikow idzie przez `GitLabInstructionRepositoryPort`, zeby instructions byly
  traktowane jako czesc tej samej code source capability co endpointy, pliki,
  use case'y i merge requesty.
- Zwracaj neutralne zrodla instrukcji i jawne limity widocznosci.
- Parsowanie referencji jest best-effort; nie zgaduj plikow poza repo-relative
  sciezkami.

## Weryfikacja

- Testuj discovery jako czysta logike testowym `GitLabRepositoryPort`.
- Po zmianie zaleznosci uruchom `PackageDependencyGuardTest`.
