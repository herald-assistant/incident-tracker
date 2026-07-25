# AGENTS

## Zakres

Ten pakiet jest czescia integracji GitLab i odpowiada za odkrywanie
repozytoryjnych instrukcji dla code source. Odnajduje globalne i lokalne
`AGENTS.md`, `.github/copilot-instructions.md` oraz repozytoryjne pliki
referencjonowane z instrukcji.

## Zasady

- Nie importuj `analysis.*`, `agenttools.*`, `features.*`, `api.*` ani
  `aiplatform.*`.
- Czytanie plikow idzie przez `GitLabRepositoryPort`, zeby instructions byly
  traktowane jako czesc tej samej code source capability co endpointy, pliki,
  use case'y i merge requesty.
- Zwracaj neutralne zrodla instrukcji i jawne limity widocznosci.
- Parsowanie referencji jest best-effort; nie zgaduj plikow poza repo-relative
  sciezkami.

## Weryfikacja

- Testuj discovery jako czysta logike testowym `GitLabRepositoryPort`.
- Po zmianie zaleznosci uruchom `PackageDependencyGuardTest`.
