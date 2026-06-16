# Product Direction

## Cel produktu

Repo rozwija platforme do AI-augmented system analysis. Oznacza to, ze aplikacja
ma pomagac analitykom, operatorom i developerom rozumiec systemy przez
polaczenie:

- deterministic context gathering z systemow zewnetrznych,
- curated operational context,
- agent tools nad kodem, logami, runtime i danymi,
- sesji AI uruchamianych przez wspolna platforme,
- feature-specific kontraktow wyniku i workflow operatora.

Product-facing nazwa workspace'u w UI to `Team Delivery Workspace`. Ta nazwa
ma podkreslac, ze narzedzie wspiera codzienna prace calego zespolu
wytworczego, a nie tylko role analityczne. UI i nawigacja maja isc w kierunku
skills/capabilities-based organization: feature'y sa miejscem pracy nad
konkretnym rezultatem, reusable tools sa zapleczem diagnostycznym, a ustawienia
platformy dotycza samego workspace'u.

Pierwszym produkcyjnym feature'em jest analiza incydentu po `correlationId`.
Nazwa repo i czesc publicznych URL-i nadal odzwierciedlaja ten start, ale nie
definiuja juz granicy architektury. Kierunek produktu jest szerszy: jedna
platforma analityczna ma obslugiwac wiele sposobow zadawania pytan o system.

## Framing UI

Glowny shell UI jest zorganizowany wokol trzech grup:

- `Analysis Features` - dedykowane feature'y pracy operatora/zespolu, na razie
  `Incident Analysis` jako pierwszy dostepny feature oraz przyszle miejsca na
  Flow Explorer, Functional Logic i Data Diagnostics.
- `Tool Workbench` - operator-facing laboratorium reusable capability:
  Elastic Logs, GitLab Source, Database Tools i Operational Context. Te widoki
  sluza do testow, debugowania i recznego zebrania inputu; nie sa osobnymi
  feature'ami produktowymi ani nie powinny niesc logiki incydentu.
- `Platform` - customizacja Team Delivery Workspace: parametryzacja workspace'u,
  personalizacja, autentykacja, konfiguracja modeli i inne ustawienia
  platformy.

Topbar jest kontekstowy: pokazuje aktualny widok, breadcrumb i ewentualny
skompresowany `capabilityInfo` pod ikona info. Nie jest glowna nawigacja.

V1 UI ma byc jasny, prosty, korporacyjny i roboczy. Nie projektujemy
marketingowych hero, dekoracyjnych gradientow ani duzych kart opisowych jako
dominujacej kompozycji. Funkcjonalnosc i czytelnosc zlozonych analiz sa
wazniejsze niz efekt "cool".

## Planowane rodziny feature'ow

### Incident analysis

Operator podaje `correlationId`. System zbiera logi, deployment/runtime
signals, code evidence i operational context, a AI zwraca diagnoze,
uzasadnienie oraz rekomendowany kolejny krok. Follow-up chat pozwala dopytac o
wynik i wykonac dodatkowe, session-bound sprawdzenia przez tools.

Obecny publiczny kontrakt:

- `POST /analysis/jobs`
- `GET /analysis/jobs/{analysisId}`
- `POST /analysis/jobs/{analysisId}/chat/messages`

To jest pierwszy feature, a nie generyczny core dla pozostalych analiz.

### Flow explorer

Analityk pyta, jak przez system przechodzi konkretny request, proces albo use
case. Feature powinien umiec odkryc i opisac:

- entrypointy HTTP, eventy, kolejki, joby i workflow,
- komponenty wdrozeniowe i logiczne systemy,
- mikroserwisy, moduly, biblioteki i generated clients,
- bazy danych, tabele, encje i relacje,
- mechanizmy komunikacji oraz kolejnosc wywolan,
- luki widocznosci, ktore wymagaja doprecyzowania.

Naturalne capability reusable dla tego feature'a to GitLab tools, operational
context tools, Database tools, a w przyszlosci takze runtime/log tools, ale
prompt, result DTO, policy i UI powinny byc lokalne dla `features.flow...`.

### Functional logic explorer

Uzytkownik pyta o logike funkcjonalna konkretnego use case'u, procesu,
reguly biznesowej albo fragmentu domeny. Feature powinien laczyc kod,
operational context, glossary, bounded contexts i dokumentacje techniczna, ale
jego odpowiedz nie powinna byc incident diagnosis. Typowy wynik to opis
warunkow, wariantow, walidacji, regul, miejsc implementacji i otwartych pytan.

### Natural-language data diagnostics

Uzytkownik pyta o dane systemu jezykiem naturalnym. Feature powinien budowac
bezpieczny, readonly workflow nad Database capability:

- rozpoznac system, environment i zakres danych,
- odkryc tabele/kolumny/relacje przez typed tools,
- preferowac typed queries nad raw SQL,
- maskowac i limitowac wyniki,
- jawnie pokazywac ograniczenia widocznosci i ryzyko interpretacji danych.

Nie powinien reuse'owac incidentowego DB policy jako core. Incident analysis
moze uzywac DB tools do weryfikacji hipotez incydentu, a osobny data feature
moze miec wlasny kontrakt produktu, uprawnienia, audyt i UI.

## Model warstw

Docelowy podzial odpowiedzialnosci jest celowo powtarzany w dokumentacji i w
lokalnych `AGENTS.md`, bo to najwazniejsza decyzja utrzymaniowa repo:

```text
features.<feature>
  -> aiplatform
  -> agenttools
  -> integrations
  -> external systems

api shared/operator
  -> aiplatform / integrations / shared

shared/common
  -> male stabilne kontrakty i helpery
```

### `integrations`

Czyste capability do systemow zewnetrznych: porty, adaptery, properties,
modele request/result i services. Adapter ma byc reusable przez evidence
pipeline, tools, shared/operator API albo kolejny feature. Nie importuje
feature'ow, tools, platformy AI ani HTTP API.

### `agenttools`

Reusable tools/MCP nad integracjami. Definicje tools maja byc neutralne
funkcjonalnie, np. GitLab search, DB metadata albo `opctx_*` catalog browse.
Semantyka uzycia nalezy do promptu, skilla, policy i guidance konkretnego
feature'a.

### `aiplatform`

Platforma uruchamiania AI. Obecnie glownym runtime jest GitHub Copilot SDK.
Platforma zna lifecycle sesji, options, allowliste tools, hidden context,
invocation handler, policies, budget, usage i eventy techniczne. Nie zna
incident promptu, flow-explorer result DTO ani `correlationId` jako wymogu
platformowego.

### `api`

Shared/operator API dla frontendu i operatora, gdy endpoint jest wspolny dla
wielu ekranow albo jest fasada nad platforma/integracja. Przyklady:
`GET /analysis/ai/options`, `/api/database/*`,
`/api/operational-context/*`, GitLab/Elasticsearch helper endpoints.

Endpointy konkretnego use case'u zostaja przy `features.<feature>.api`.

### `features`

Dedykowane pionowe analizy. Feature dostarcza:

- publiczny request/response albo job/run API,
- source/evidence pipeline albo inny source gathering,
- prompt, skille i response contract,
- hidden tool context i tool policy,
- mapping artifacts/evidence/result na UI,
- follow-up chat, jesli jest czescia workflow.

Feature moze zalezec od platformy, tools i integracji. Platforma, tools,
integracje, shared i common nie moga zalezec od feature'a. Feature'y nie
powinny zalezec od siebie nawzajem.

## Zasada utrzymaniowa

Kazda nowa potrzeba powinna zaczynac sie od pytania:

```text
Czy to jest reusable capability, platform mechanics, shared/operator API,
czy feature-specific workflow?
```

Odpowiedz decyduje o pakiecie. Nie przenosimy klas do `shared` tylko po to,
zeby ukryc zla zaleznosc, i nie rozszerzamy incident analysis tak, jakby bylo
generycznym silnikiem analitycznym.

## Najwazniejszy dowod architektury

Kolejny duzy krok powinien potwierdzic, ze platforma nie jest tylko
przemianowanym incident trackerem. Najlepszym dowodem bedzie drugi feature,
np. flow explorer albo functional logic explorer, ktory:

1. ma wlasny request/result contract,
2. uzywa `aiplatform.copilot` przez neutralny run request,
3. wybiera reusable tools z `agenttools`,
4. korzysta z adapterow `integrations`,
5. nie importuje `features.incidentanalysis`,
6. publikuje przebieg przez wspolny run/event model, gdy ten zostanie
   wprowadzony.
