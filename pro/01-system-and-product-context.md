# System And Product Context

## Po co istnieje ten projekt

Projekt buduje aplikacje Spring Boot do analizy incydentow na podstawie
`correlationId`.

Docelowy scenariusz:

1. operator uruchamia analize dla konkretnego incydentu,
2. aplikacja zbiera evidence z systemow zewnetrznych,
3. AI interpretuje evidence,
4. AI moze dociagnac dodatkowy kod z GitLaba przez tools,
5. aplikacja zwraca diagnoze i rekomendowany kolejny krok.

## Kto jest uzytkownikiem

Glownym uzytkownikiem nie jest koncowy klient biznesowy, tylko operator,
analityk, tester albo developer, ktory:

- ma `correlationId`,
- chce szybko zawezic problem,
- potrzebuje zrozumiec nie tylko blad, ale tez szerszy flow funkcjonalny,
- moze potrzebowac handoffu do innego zespolu, administratorow albo wlasciciela
  integracji.

## Aktualny scope produktu

Projekt ma dzisiaj:

- frontend Angular serwowany z tego samego JAR-a,
- widok `GET /` do uruchamiania asynchronicznej analizy,
- widok `GET /evidence` do recznego testowania helper endpointow,
- glowne API synchroniczne `POST /analysis`,
- job API dla UI:
  - `POST /analysis/jobs`
  - `GET /analysis/jobs/{analysisId}`,
- rzeczywiste integracje REST z Elasticsearch, Dynatrace i GitLabem,
- AI provider oparty o GitHub Copilot Java SDK,
- MCP tools dla Elastica i GitLaba,
- runtime skill ladowny z zasobow aplikacji,
- opcjonalny curated operational context.

## Czego projekt swiadomie nie robi

Na dzisiaj projekt nie robi jeszcze:

- pelnej persystencji jobow,
- historii analiz w bazie,
- rule-based root cause engine jako glownego flow,
- Dynatrace tools w trakcie sesji AI,
- dedykowanej warstwy confidence scoring,
- jawnego cost and latency budgetingu dla Copilot session,
- wieloetapowego AI pipeline z osobnym plannerem i osobnym writerem wyniku.

## Najwazniejsze niezmienniki produktowe

### Wejscie do glownej analizy

- `POST /analysis` i `POST /analysis/jobs` przyjmuja tylko `correlationId`.
- `branch`, `environment` i `group` nie sa danymi requestu.

### Pochodzenie runtime facts

- `gitLabBranch` i `environment` sa wyprowadzane z evidence.
- `gitLabGroup` pochodzi z konfiguracji aplikacji.

### Dominujacy model analizy

- flow jest `AI-first`,
- evidence jest zebrane deterministycznie,
- interpretacja i finalna diagnoza pochodza z providera AI.

### Rozdzial odpowiedzialnosci GitLaba

GitLab wystepuje w trzech rolach:

1. adapter REST i helper endpointy,
2. deterministic evidence provider,
3. AI-guided tools.

Nie wolno mieszac tych rol przypadkowo.

## Zewnetrzne systemy i ich rola

### Elasticsearch / Kibana proxy

Rola:

- glowne zrodlo logow po `correlationId`,
- bazowy material do deployment context,
- opcjonalne dogrywanie logow przez tool AI.

### Dynatrace

Rola:

- enrichment runtime signals do promptu,
- korelacja z logami po namespace, podach, kontenerach i service names,
- brak tooli runtime dla AI.

### GitLab

Rola:

- deterministic code evidence z logow i deployment context,
- interactive repository exploration przez MCP tools,
- source resolve po symbolu.

## Jak wyglada wartosc biznesowa wyniku

Dobry wynik tej aplikacji powinien:

- wskazywac najbardziej prawdopodobny problem,
- pokazywac, co jest potwierdzone, a co tylko hipoteza,
- tlumaczyc, jaka funkcja systemu zostala przerwana,
- dawac konkretny next step i ewentualny handoff,
- byc czytelny dla osoby, ktora nie zna jeszcze obszaru.

To jest szczegolnie wazne, bo prompt i skill sa juz ustawione pod operatora lub
mid-level developera, a nie pod eksperta domenowego.

## Najwazniejsze entrypointy

- `GET /`
  Operatorski frontend do uruchamiania analizy.
- `GET /evidence`
  Frontend diagnostyczny do testowania helper endpointow.
- `POST /analysis`
  Glowny synchroniczny kontrakt analizy.
- `POST /analysis/jobs`
  Start joba dla UI.
- `GET /analysis/jobs/{analysisId}`
  Odczyt statusu i wyniku analizy.
- `POST /api/elasticsearch/logs/search`
  Helper endpoint log search.
- `POST /api/gitlab/repository/search`
  Helper endpoint repo search.
- `POST /api/gitlab/source/resolve`
  Helper endpoint resolve po symbolu.
- `POST /api/gitlab/source/resolve/preview`
  Helper endpoint resolve z ograniczona trescia.

## Wersje i glowny stack

- Java `17`
- Spring Boot `3.5.11`
- Spring AI `1.1.2`
- `copilot-sdk-java` `0.2.1-java.0`
- Angular `21.x`
- Node `22.14.0` dla buildu frontendowego

## Co znaczy "optymalizacja projektu" w tym repo

W praktyce sa tu trzy rownolegle cele:

1. lepsza trafnosc i czytelnosc analizy incydentu,
2. mniejszy koszt i mniejsza losowosc pracy Copilot SDK,
3. wieksza operacyjna przewidywalnosc calosci:
   - latencja,
   - fallback,
   - debuggability,
   - utrzymywalnosc granic modulow.
