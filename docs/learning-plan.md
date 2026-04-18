# Plan Budowy Aplikacji Krok Po Kroku

Ten dokument prowadzi nas malymi krokami od pustego Spring Boota do aplikacji,
ktora:

- serwuje prosty ekran operacyjny do uruchamiania analizy,
- przyjmuje `correlationId`,
- pobiera dane z systemow zewnetrznych,
- analizuje bledy,
- proponuje rekomendowana poprawke do kodu,
- korzysta z GitHub Copilot Java SDK oraz MCP tools/serverow.

Kazdy krok ma byc:

1. maly,
2. samodzielnie testowalny,
3. latwy do zdebugowania,
4. skonczony zanim przejdziemy dalej.

## Dokumenty stale

Ten plan opisuje sciezke nauki i kolejne iteracje.
Aktualny obraz systemu i decyzji architektonicznych jest dodatkowo opisany w:

1. `docs/architecture/01-system-overview.md`
2. `docs/architecture/02-key-decisions.md`
3. `docs/architecture/03-runtime-flow.md`
4. `docs/architecture/04-codex-continuation-guide.md`

## Zasada pracy

Po kazdym kroku robimy zawsze:

1. mala implementacje,
2. jeden test automatyczny,
3. jeden test reczny,
4. krotkie zrozumienie "co sie wydarzylo".

## Kroki

### 1. Starter aplikacji Spring Boot

Cel:
Postawic minimalna aplikacje Spring Boot z wymaganymi zaleznosciami i
potwierdzic, ze uruchamia sie lokalnie.

Uczysz sie:

- struktury projektu Maven,
- roli `pom.xml`,
- klasy `@SpringBootApplication`,
- podstaw uruchamiania i debugowania Spring Boot.

Efekt:

- aplikacja buduje sie,
- test kontekstu przechodzi,
- JAR uruchamia sie lokalnie.

### 2. Pierwszy endpoint REST

Cel:
Dodac najprostszy endpoint HTTP i zobaczyc, jak request trafia do kontrolera.

Uczysz sie:

- `@RestController`,
- `@PostMapping`,
- `@RequestBody`,
- request/response JSON,
- testowania endpointow.

### 3. Walidacja `correlationId` w request body

Cel:
Przyjac `correlationId` jako dane wejsciowe analizy, sprawdzic jego brak lub
niepoprawna wartosc i zwrocic kontrolowany blad.

Uczysz sie:

- walidacji request body,
- `@Valid`,
- prostych odpowiedzi `400 Bad Request`.

### 4. Rozszerzenie DTO i kontraktu API

Cel:
Uporzadkowac kontrakt API i wprowadzic przewidywalny format odpowiedzi bledow.

Uczysz sie:

- projektowania DTO,
- `@RestControllerAdvice`,
- projektowania odpowiedzi bledow,
- stabilnego kontraktu API dla klienta.

### 5. Warstwa serwisowa

Cel:
Przeniesc logike z kontrolera do serwisu.

Uczysz sie:

- podzialu odpowiedzialnosci,
- testow jednostkowych,
- wstrzykiwania zaleznosci.

### 6. Obsluga bledow HTTP

Cel:
Dodac spojna obsluge bledow HTTP dla wyjatkow biznesowych i technicznych.

Uczysz sie:

- `@ControllerAdvice`,
- mapowania wyjatkow na odpowiedzi HTTP,
- projektowania odpowiedzi bledow poza sama walidacja requestu,
- roznicy miedzy bledem walidacji i bledem domenowym.

### 7. Pierwszy model evidence i wyniku analizy

Cel:
Zbudowac pierwszy model wyniku analizy oraz zrozumiec, jakie dane beda potrzebne
do AI.

Uczysz sie:

- modelowania wyniku analizy,
- modelowania evidence,
- oddzielania zrodel danych od przyszlego providera AI.

### 8. Porty i adaptery

Cel:
Wydzielic interfejsy zrodel danych i przepiac serwis na prace przez porty.

Uczysz sie:

- architektury portow i adapterow,
- pierwszych syntetycznych adapterow,
- przygotowania kodu pod kolejne systemy.

### 9. Atrapy integracji z systemami zewnetrznymi

Cel:
Rozbudowac fake implementacje dla GitLaba, Elasticsearch i Dynatrace.

Uczysz sie:

- projektowania kontraktow integracyjnych,
- testowania flow bez prawdziwych systemow na bogatszych danych,
- weryfikacji transformacji danych,
- pracy na ustrukturyzowanych modelach zamiast surowych stringach,
- budowania providera zaleznosciowego, gdzie GitLab korzysta z contextu
  zebranego wczesniej z logs i trace.

### 10. Kontrakt providera AI

Cel:
Przygotowac interfejs i modele warstwy AI bez podpinania ich do glownego flow.

Uczysz sie:

- wzorca adaptera dla providera AI,
- osobnych modeli wejscia i wyjscia warstwy AI,
- budowania requestu AI na podstawie evidence zamiast gotowego wyniku regulowego,
- kontraktu `AnalysisEvidenceProvider` i registry opartego o IoC,
- sekwencyjnego `AnalysisContext`, ktory pozwala jednemu providerowi korzystac
  z evidence zwroconego przez inne providery,
- przygotowania miejsca pod przyszly Copilot SDK.

### 11. Pierwszy kontakt z GitHub Copilot Java SDK

Cel:
Przygotowac i zrozumiec realne obiekty SDK w izolacji, jeszcze bez uruchamiania
klienta i sesji.

Uczysz sie:

- podstaw modelu pracy SDK,
- konfiguracji `CopilotClientOptions`, `SessionConfig` i `MessageOptions`,
- budowania promptu z logow, trace i hintow z kodu,
- ograniczen i potrzeb konfiguracji po stronie providera.

### 12. Wydzielenie providera analizy AI

Cel:
Podpiac implementacje Copilot SDK pod przygotowany kontrakt providera AI.

Uczysz sie:

- separacji warstwy domenowej od modelu AI,
- mapowania odpowiedzi modelu z powrotem na nasz kontrakt analizy AI,
- wpinania konkretnej implementacji pod interfejs,
- latwego testowania z fake providerem i prawdziwym providerem,
- wyboru implementacji providera przez konfiguracje.

### 13. Pierwsza analiza z uzyciem Copilota

Cel:
Na podstawie prostych danych wejsciowych uzyskac diagnoze od modelu.

Uczysz sie:

- budowania promptu,
- walidacji wyniku,
- ograniczania odpowiedzialnosci modelu.

### 14. Wprowadzenie do MCP

Cel:
Zrozumiec czym sa MCP server, tools, resources i prompty oraz wystawic pierwsze
GitLab tool-e.

Uczysz sie:

- modelu MCP,
- po co narzedzia sa wystawiane osobno,
- jak agent korzysta z tooli,
- jak wystawic `gitlab_search_repository_candidates` i
  `gitlab_read_repository_file`.

### 15. Pierwszy MCP tool w Spring

Cel:
Rozszerzyc GitLab MCP o czytanie fragmentu pliku po liniach.

Uczysz sie:

- projektowania toola pod iteracyjne dogrywanie kodu,
- roznicy miedzy pelnym plikiem a fragmentem po liniach,
- zwracania metadanych takich jak `totalLines` i faktyczny zakres odpowiedzi.

### 16. MCP jako warstwa dostepu do danych

Cel:
Spiac Copilot SDK z wystawionymi GitLab tools, tak aby model mogl iteracyjnie
dociagac kod.

Uczysz sie:

- mostu miedzy `ToolCallback` a `ToolDefinition` w Copilot SDK,
- roznicy miedzy embedded tool bridge i prawdziwym transportem MCP,
- reuse'u tego samego zestawu tooli po stronie MCP i sesji Copilota.

### 17. Skill domenowy Copilota

Cel:
Wydzielic stale zasady pracy z evidence i GitLab tools do projektowego skilla.

Uczysz sie:

- roznicy miedzy promptem operacyjnym i skillem,
- formatu `SKILL.md` z YAML frontmatter,
- pakowania skilli do `src/main/resources`,
- wypakowywania ich do runtime directory dla `SessionConfig.skillDirectories`,
- przenoszenia strategii tool usage do reusable instrukcji.

### 18. Pierwsza rzeczywista integracja GitLab

Cel:
Przejsc z syntetycznego adaptera GitLaba do pierwszej wersji REST API z jawnie
konfigurowana grupa oraz branch wyprowadzanym z logs zamiast z request body.

Uczysz sie:

- dlaczego `group` warto trzymac w konfiguracji aplikacji,
- dlaczego `branch` i `environment` lepiej wyprowadzac z evidence analizowanego
  incydentu,
- jak zaprojektowac kontrakt `group/project/branch/filePath`,
- jak podpiac pierwszy realny adapter GitLab REST bez psucia calego flow.

### 19. Agent i orkiestracja

Cel:
Zlozyc caly przeplyw: endpoint -> dane -> analiza -> wynik.

Uczysz sie:

- orkiestracji procesu,
- kolejnosci wywolan,
- odpowiedzialnosci agenta.

### 20. Rekomendacja poprawki do kodu

Cel:
Rozszerzyc wynik analizy o rekomendacje zmiany w kodzie.

Uczysz sie:

- przejscia od diagnozy do propozycji poprawki,
- pracy na fragmentach kodu i stacktrace,
- ograniczania halucynacji.

### 21. Prawdziwe integracje z pozostalymi systemami

Cel:
Rozwijac realne API Elasticsearch, podlaczyc Dynatrace oraz rozwijac dalej
realny GitLab.

Uczysz sie:

- konfiguracji polaczen,
- autoryzacji,
- timeoutow, retry i obslugi bledow.

### 22. Logowanie i obserwowalnosc tej aplikacji

Cel:
Dodac logowanie i podstawowa obserwowalnosc dopiero wtedy, gdy aplikacja bedzie
miala kilka realnych krokow i integracji.

Uczysz sie:

- sensownego momentu na dodanie logowania technicznego,
- rozroznienia miedzy identyfikatorem analizowanego przypadku i identyfikatorem
  requestu tej aplikacji,
- podstaw korelacji logow wewnatrz systemu analitycznego.

### 23. Trace i obserwowalnosc

Cel:
Zrozumiec relacje miedzy `correlationId`, `requestId` i `traceId`.

Uczysz sie:

- podstaw trace,
- roznicy miedzy logami i trace,
- jak laczyc identyfikatory z badanego systemu z diagnostyka tej aplikacji.

### 24. Twardnienie rozwiazania

Cel:
Dodac odpornosc i kontrole nad zachowaniem systemu.

Uczysz sie:

- fallbackow,
- ograniczen i walidacji,
- bezpieczniejszego wykorzystania AI.

### 25. Test end-to-end

Cel:
Zweryfikowac pelny scenariusz biznesowy od `correlationId` do rekomendacji.

Uczysz sie:

- myslenia calym przeplywem,
- testow integracyjnych end-to-end,
- diagnozowania problemow miedzy warstwami.

## Kolejnosc jest celowa

Najpierw rozumiemy:

1. Spring Boot,
2. HTTP i API,
3. request body i walidacje wejscia,
4. `correlationId` jako dane wejscia do analizy,
5. analize bez AI,
6. przygotowanie pod zrodla danych,
7. Copilot SDK,
8. MCP,
9. skill domenowy Copilota,
10. realne integracje,
11. dopiero potem logowanie, trace i twardnienie rozwiazania.

To pozwala zrozumiec po co dokladamy kolejny element, zamiast uruchamiac wszystko
naraz.
