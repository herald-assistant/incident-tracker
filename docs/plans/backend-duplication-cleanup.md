# Backend duplication cleanup

Status: done

Source need: brak osobnego dokumentu; potrzeba techniczna utrzymania spojnych mechanizmow uzywanych przez wielu konsumentow

## Potrzeba / dlaczego

Powielone sciezki odczytu GitLaba, wyszukiwania Operational Context i zapisu
Analysis History utrudniaja utrzymanie zgodnego zachowania. W szczegolnosci
odczyt pliku moze zamienic blad GitLaba na pozorny brak pliku, a operator i AI
stosuja inne zasady wyszukiwania tego samego katalogu.

## Proponowane rozwiazanie

1. Ujednolicic rozstrzyganie sciezki pliku w neutralnym GitLab MCP wrapperze,
   zachowujac osobne kontrakty tools dla pelnego pliku, fragmentu i metadata.
2. Wydzielic neutralne dopasowanie wyszukiwania do
   `integrations.operationalcontext`; API i MCP zachowuja swoje DTO oraz limity.
3. Wydzielic z powtarzalnych persisterow maly mechanizm budowy i zapisu rekordu
   `LocalAnalysisRunRecord` w `localworkspace.analysisruns`; koperty i etykiety
   pozostaja feature-owned.

## Baseline i conformance delta

- GitLab: tools czytaja exact path, code-search prefix albo jeden jednoznaczny
  Java type fallback. Session-bound pinned reads pozostaja osobna bezpieczna
  sciezka. Nazwy i model-facing schema tools bez zmian. Obecny drift: zwykly
  odczyt lapie kazdy `RuntimeException` i zwraca `File not found`.
- Operational Context: operator API zwraca liste z `confidence` tekstowym, MCP
  zwraca ranking liczbowy, `matchedFields` i `matchedSignals`. Wspolne beda
  normalizacja i semantyka trafienia; projekcje i limity pozostaja lokalne.
- History: feature eksportuje wlasna koperte i display name, a zapisuje neutralny
  rekord v1 oraz index entry. Publiczne API, schema eksportu, continuation,
  prompt, job state i UI bez zmian.
- Konsumenci: GitLab MCP tools oraz ich feature allowlisty; Operational Context
  operator API i `opctx_search` w MCP; wszystkie feature-owned local run
  persisters korzystajace z neutralnego store. Kierunek zaleznosci pozostaje
  `api/agenttools -> integrations`, `features -> localworkspace`.
- Baseline testow: `GitLabMcpToolsTest`, `GitLabMcpToolsContextTest`,
  `OperationalContextViewServiceTest`, `OperationalContextMcpToolsTest` i
  `PackageDependencyGuardTest` przeszly przed edycja.

## Zakres

Trzy male, kolejne inkrementy backendowe. Bez zmiany publicznych DTO, nazw
tooli, katalogu Operational Context, kopert import/export i danych runu.

## Non-goals

- Scalanie eksperymentalnych feature'ow Delivery Complexity Assessment i
  Delivery Scope Complexity.
- Zmiana pinned revision scope albo polityki tooli.
- Wspolna orkiestracja jobow, parserow AI lub promptow.

## Ograniczenia i ryzyka

- GitLab fallback musi dzialac tylko przy rzeczywistym braku pliku; bledy
  dostepu, serwera i transportu musza pozostac rozroznialne.
- Wyszukiwanie API i MCP ma rozne kontrakty wyniku. Jedna definicja trafienia
  nie moze ujawnic raw payload ani source preview w toolach.
- Zapis historii musi zachowac dotychczasowy format rekordu i index entry.

## Kryteria akceptacji

- Jeden mechanizm rozstrzyga kandydatow GitLab dla odczytu pliku, fragmentu i
  metadata; testy rozrozniaja 404 od 401/403, 5xx i awarii transportu.
- API i MCP szukaja po tych samych znormalizowanych sygnalach katalogu;
  ich publiczne DTO i limity sa zachowane.
- Powtarzalny neutralny zapis runu ma jednego wlasciciela, a export envelopes
  i display names pozostaja w feature'ach.
- Adekwatne testy celowane i `mvn -q test` przechodza; diff nie wprowadza
  odwrotnych zaleznosci ani danych rzeczywistych do testow.

## Kroki

- [x] Krok 1: Ujednolicic GitLab path fallback i typowanie bledow, dodac testy
  404/401/403/5xx/transport oraz uruchomic testy GitLab MCP i architecture guard.
- [x] Krok 2: Ujednolicic wyszukiwanie Operational Context przy zachowaniu
  projekcji API/MCP, dodac testy rownowaznosci trafien i uruchomic ich testy.
- [x] Krok 3: Wydzielic neutralny zapis historii, przeniesc persistery
  inkrementalnie, uruchomic testy dotknietych feature'ow i `mvn -q test`.
- [x] Krok 4: Potwierdzic conformance, zaktualizowac dokumentacje wynikowego
  stanu i wykonac `git diff --check`.

## Weryfikacja

- Testy celowane GitLab MCP, Operational Context API/MCP, local run persisterow
  oraz `PackageDependencyGuardTest` przeszly.
- `mvn -q test` przeszedl w odizolowanym worktree z finalnymi zmianami kodu.
  Jeden zasob skilla w tym checkoutcie wymagal koncow linii LF zgodnych z Git;
  bez tego niezalezny `CopilotSkillRuntimeLoaderTest` porownywal CRLF z LF.
- Glowny katalog roboczy ma rownolegla, nieobjeta tym planem zmiane
  `DatabaseCapabilityDtos.toolName` na `toolNameX`, ktora uniemozliwia tam
  kompilacje. Nie modyfikowano jej.
- `git diff --check` bez bledow; dokumentacja wynikowego stanu znajduje sie w
  `docs/architecture/package-dependencies.md` i
  `docs/architecture/operational-context-model-tools-and-usage.md`.
