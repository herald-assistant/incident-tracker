# System Assistant — runtime

## Zakres produktu

System Assistant jest samodzielnym chatem operacyjnym. Nie wymaga initial
analizy ani `AnalysisReport`. Użytkownik utrzymuje bieżący zestaw wielu
OpenAPI YAML i skilli roboczych, a kolejne tury tej samej rozmowy korzystają
z zapisanej aktualnej treści. Ekran: `/system-assistant`.

Operator może podłączyć HTTP(S) API dowolnego systemu, z hostem DNS, IP lub
localhost, w obsługiwanym podzbiorze OpenAPI 3.0.x/3.1.x opisanym poniżej.
Bieżący zakres obsługuje jedną osobę w workspace. Obsługuje source auth Basic/Bearer/OAuth2/OIDC. Obejmuje załączniki PNG/JPEG/WebP/GIF, PDF i DOCX. Nie obejmuje approval workflow ani integracji CLP.
Konfigurację, rozmowę i testy opisuje [instrukcja obsługi](../system-assistant.md).
Skille określają sposób pracy AI, nie egzekwują uprawnień biznesowych.
Kontrakt operatora lub jego proxy może ograniczać i orkiestrować wywołania API;
TDW nie posiada osobnego silnika polityk. OIDC uzyskuje własny access token
operatora; nie przejmuje sesji strony źródłowej.

## Włączenie i wyłączenie

Flaga `system-assistant.enabled` jest domyślnie wyłączona i steruje dostępnością feature'a. Nie ustawia
ani nie waliduje `server.address`: adres nasłuchiwania TDW pozostaje wspólną
konfiguracją aplikacji. API konfiguracji, chat i MCP są dostępne pod adresem
TDW. Docelowe usługi wskazane przez YAML nie mają listy dopuszczonych hostów.
Wychodzące API oraz połączenia backendu OAuth2/OIDC (discovery, tokeny, JWKS)
respektują `integrations.http.ignore-ssl-errors`: false weryfikuje certyfikat
i nazwę hosta, true pomija obie kontrole lokalnie dla klienta. Flaga wymaga
restartu. Nie zmienia TLS JVM ani przeglądarki i nie wyłącza walidacji JWT.
Oba tryby używają JDK HttpClient z lokalną polityką TLS. Transport zachowuje
PATCH, connect/read 3/8 s i brak redirectów. Aplikacja nie dodaje retry;
wbudowany retry GET JDK po niektórych zerwaniach połączenia jest dopuszczony.
Ta sama flaga steruje feature API, osobnym MCP, sidebar, landing
i akcją Browser Tools. Shared UI config zwraca `systemAssistantEnabled`.

`GET /browser-tools/availability.js` publikuje wyłącznie publiczną flagę
w efemerycznym globalu, z `Cache-Control: no-store`. Loader ładuje skrypt
przed protocol/runtime. Nie wymaga CORS API ani wysyłania cookies. Runtime
zużywa i usuwa global. Flaga z przeglądarki nie autoryzuje wykonania.
W trybie developerskim proxy Angulara przekazuje ten endpoint do backendu;
pozostałe zasoby Browser Tools pozostają serwowane przez dev server.
Proxy /analysis dopasowuje granicę segmentu; nie przechwytuje /analysis-history,
które pozostaje route frontendu developerskiego.
Bookmarklet utworzony na dev serverze zachowuje jego origin.

Wyłączenie wymaga ponownego uruchomienia z flagą `false`: nowe API i MCP
zwracają 404, UI ukrywa wejścia, otwarty link pokazuje niedostępność.
Konfiguracja na dysku pozostaje. Restart/wyłączenie nie cofa już wysłanych
operacji; ich stan trzeba ustalić w systemie źródłowym.

## Ownership

| Warstwa | Odpowiedzialność |
| --- | --- |
| `features.systemassistant` | Konfiguracja użytkownika, rozmowy, kolejka tur, prompt, transcript, skille robocze i kompozycja ekspozycji MCP |
| `integrations.openapi` | Porty importu i wykonania; typowane operacje i wyniki; parser, walidacja schema, RestClient |
| `integrations.httpauth` | Neutralne Basic/Bearer/OAuth2/OIDC, transport dostawcy i pamięciowy stan tokenów |
| `agenttools.openapi` | Generowane callbacki oraz aktualny katalog runtime |
| `agenttools.openapi.mcp` | Neutralny serwer Streamable HTTP; brak zależności od feature'a i Copilota |
| `aiplatform.copilot` | Istniejący runtime NEW, jawna allowlista/hook, invocation events, usage i cleanup sesji |
| `localworkspace.storage` | Ścieżka workspace i atomowa podmiana bieżącego pliku |
| Frontend feature | Edytor zestawu, wiadomości i orchestration widoku |
| Shared frontend | Kontrolki, chat, tok pracy AI, koszt, polling, bookmarklet setup i shell |

Brak zależności od sibling feature'ów. OpenAPI endpoint slice GitLaba nie
jest importerem wykonawczym i nie jest tutaj używany. Tools OpenAPI nie są
globalnymi `ToolCallbackProvider`; istniejący katalog MCP TDW pozostaje
odrębny.

`OpenApiExecutionPort.execute` przyjmuje własny `OpenApiExecution` (operacja
i captured Auth) oraz `OpenApiArguments`. Callback SDK/MCP przekazuje ten sam
binding do REST, bez ponownego wyboru profilu. HttpAuthProfile pozostaje
modelem neutralnego httpauth użytym wewnątrz kontraktu bindingu, poza root
portem i poza schema argumentów AI. Nie ma starszego overloadu wykonania.

## Kontrakty HTTP

| Endpoint | Kontrakt |
| --- | --- |
| `GET /api/system-assistant/configuration` | Bieżące `{apis, skills, profiles, authProfiles}`; uszkodzony zapis daje 503 `CONFIGURATION_UNAVAILABLE` |
| `PUT /api/system-assistant/configuration` | Zastępuje cały aktualny zestaw po walidacji i skutecznym zapisie |
| `POST /api/system-assistant/configuration/openapi/preview` | `{id, yaml}`; katalog operacji, limit wyboru i specification (kopia JSON do dokumentacji), bez aktywacji |
| `POST /api/system-assistant/configuration/openapi/document` | `{id, yaml}`; zwalidowany dokument JSON do podglądu, bez zapisu i publikacji |
| `POST /api/system-assistant/configuration/openapi/selection` | `{id, yaml, operations:[{method,path}]}`; zwalidowany YAML wyboru i operationCount, bez zapisu |
| `GET /api/system-assistant/configuration/profiles` | Wybór profilu w rozmowie. Zwraca wyłącznie bieżące `id`, `name` i `urlPattern`, w kolejności konfiguracji |
| `GET /api/system-assistant/configuration/tools` | ID kontraktu `apiId`, wygenerowane nazwy, opisy, metody, ścieżki i input schema |
| `POST /api/system-assistant/conversations` | `{pageContext}` lub null context; `201` i pusta rozmowa bez AI |
| `GET /api/system-assistant/conversations/{id}` | ID, busy, przypięty pageContext i neutralne wiadomości |
| `POST /api/system-assistant/conversations/{id}/messages` | `{message, profileId, model?, reasoningEffort?, pageContext?}`; `202` i natychmiastowy snapshot; `409 AUTH_LOGIN_REQUIRED` z authProfileId przed przyjęciem, jeżeli wymagane OIDC nie jest gotowe |
| `POST /api/system-assistant/conversations/{id}/attachments?name={filename}` | `application/octet-stream`; trwały strumieniowy zapis jednego pliku i snapshot rozmowy z listą `attachments: [{id,name,mimeType,size}]`; aktywna tura daje `409` |
| `DELETE /api/system-assistant/conversations/{id}` | `204`; aktywna tura daje `409` |
| `GET /api/analysis/runs` | Wspólny indeks historii; rozmowy mają `feature=system-assistant` |
| `GET /api/analysis/runs/{id}/export` | Archiwalny JSON transkryptu; bez konfiguracji API/skilli |
| `/api/openapi-tools/{profileId}/mcp` | Osobny Streamable HTTP MCP z initialize, list/call i powiadomieniem o zmianie tools |

API definition to `{id, name, yaml}`, skill to `{id, name, content}`.
ID elementu nie jest rewizją. Duplikaty ID i kolizje nazw tools blokują
aktywację. Pusty zestaw jest poprawny i pozwala rozmawiać bez API.
Profil przechowuje `apiBindings` z parami `{apiId, authProfileId}`
(null = No Auth) oraz `skillIds`. Definicja API nie wybiera uwierzytelniania.
Ten sam kontrakt może mieć różne Auth w różnych profilach.

MCP używa jawnego profilu z adresu endpointu; katalogi i sesje poszczególnych
profili są izolowane. Nieznany profil i stary nieskopowany adres nie otrzymują
fallbacku. Adres MCP wynika z ID profilu i nie jest prezentowany w formularzu.

## Uwierzytelnianie źródłowych API

Zakładka Auth utrzymuje Basic, Bearer, OAuth2 Client Credentials i OIDC.
Każde przypisanie API w profilu ma select Auth domyślnie No Auth. Pola są jawne we wspólnym JSON-ie,
a wykonawca wiąże je z operacją poza argumentami modelu. Logowanie OIDC
odbywa się przez przekierowanie i powrót /api/system-assistant/auth/callback;
stan tokenów jest pamięciowy. Status, login, callback i disconnect są
feature-owned API pod /api/system-assistant/auth.
Przed przyjęciem tury feature sprawdza/odnawia OIDC aktywnych operacji profilu.
`AUTH_LOGIN_REQUIRED` nie zapisuje wiadomości ani nie uruchamia AI. FE
rozpoczyna login z conversationId/profileId i returnPath ze ścisłej listy
/system-assistant albo /system-assistant/chat. Callback wiąże te dane z state
i przeglądarką, po czym przywraca tę samą rozmowę, profil i pełny lub osadzony
widok. Cookie tdw_oidc_browser ma HttpOnly, SameSite=None i zawsze Secure.
Odrzucona wiadomość jest zachowana w sessionStorage na maks. 5 min w obu
widokach, razem z conversationId/profileId/returnPath, modelem/reasoning
i terminem ważności.
Ramka dołącza snapshot strony odrzuconej wiadomości i nonce połączenia. Po powrocie ponawia handshake z rodzicem i porównuje pełny URL przed wznowieniem. Pełny ekran przekazuje context=null i nonce=null.
Po pasującym callbacku success wiadomość jest wysyłana automatycznie dokładnie
raz do tej samej idle rozmowy i profilu, z pierwotnymi preferencjami AI.
Wznowienie czeka na zamontowany chat, odczyt rozmowy, konfigurację, katalog
modeli i status PAT. Failed callback, niezgodny scope, błąd odczytu lub busy
zostawia tekst w polu, bez odroczonego wysłania po usunięciu przeszkody.
Nie ma komunikatu o przywracaniu tekstu. Cache jest konsumowany przy odczycie;
niepoprawny, wygasły, stary kształt lub inny scope jest odrzucany.
Ponowny AUTH_LOGIN_REQUIRED wznowionego POST nie uruchamia kolejnego loginu.
Nieustalony wynik POST nadal uruchamia tylko reconciliation odczytem, bez retry.
Treść nie trafia do URL, state dostawcy ani API loginu. Niedostępny storage
blokuje przekierowanie i zachowuje tekst w polu; błąd rozpoczęcia loginu
usuwa cache i również zachowuje tekst. Filtr profili używa bieżącej strony
potwierdzonej przez rodzica ramki. Blokada third-party cookies lub osadzania
providera nadal może uniemożliwić login. Ręczny login z Auth wraca do Auth.
Kontrakt, mechanikę i ograniczenia opisuje
[uwierzytelnianie HTTP API](http-api-authentication.md).

## Importowany podzbiór OpenAPI

Parser używa SafeConstructor SnakeYAML, odrzuca duplikaty kluczy i aliasy,
ogranicza głębokość oraz wielkość dokumentu. Nie obsługuje wszystkich
legalnych konstrukcji OpenAPI; nieobsługiwana semantyka daje `OPENAPI_INVALID`
z lokalizacją przed zapisem.

| Obszar | Obsługiwane | Odrzucane |
| --- | --- | --- |
| Dokument | JSON/YAML, `3.0.x` i `3.1.x`, metadata info/tags/externalDocs, paths, components.schemas, zachowane `x-*` | Swagger 2, inne wersje i nieobsługiwane pola wykonawcze |
| Server | Jeden jawny top-level URL HTTP(S) z dowolnym poprawnym hostem DNS/IP/localhost, opcjonalny base path | Brak hosta, nieprawidłowy port, credentials, query/hash, server variables i override path/operation |
| Operacje | GET, POST, PUT, DELETE, PATCH; wymagany unikalny operationId z liter/cyfr/underscore | HEAD/OPTIONS/TRACE i pozostałe metody, callbacks/webhooks, operacyjne `$ref` |
| Parametry | Scalar path/query, query array ze scalar items, płaski query object z form/explode true; path required, opcjonalny jawny catch-all na ostatnim segmencie; dziedziczenie i override; opcjonalne header są dokumentacją | Wymagane header, cookies, array path, zagnieżdżone/null wartości query object, pozostałe style/content i nullable tablice query |
| Body | POST/PUT/PATCH oraz DELETE w 3.1: `application/json`, wymagane lub opcjonalne body; `schema: {}` dopuszcza dowolny JSON | GET body, DELETE body w 3.0, multipart, binary request, pozostałe request media types |
| Schema | object/array/scalars, properties/items/required/enum, `allOf`/`oneOf`/`anyOf`, discriminator jako guidance wariantów, boolean lub typowane additionalProperties, uniqueItems, pattern, limity, readOnly/writeOnly, format i metadata; w 3.0 nullable, w 3.1 null/tablica typów/const | not, patternProperties, multipleOf, inne nieobsługiwane keywords i niestandardowe dialekty |
| References | Lokalne `#/components/schemas/...`; requesty: acykliczna projekcja inline do 30000 węzłów i 64 poziomów; odpowiedzi: ograniczony graf z produktywną rekursją; rodzeństwo schema `$ref` w 3.1 zachowuje przecięcie ograniczeń | Zewnętrzne refs, cykliczne aliasy, rekurencyjne requesty, rodzeństwo `$ref` w 3.0, przekroczenie budżetu |
| Responses | Kody liczbowe 100–599 lub default, description, opcjonalne application/json, text/plain ze string schema, wspierane MIME plików lub application/octet-stream ze string/binary schema lub */* | Response refs, headers, links, pozostałe jawne content types |
| Auth | security i components.securitySchemes dla Basic/Bearer/OAuth2/openIdConnect; wykonanie według wybranego profilu Auth | apiKey, digest, credentials w URL i model-facing Authorization |

### Generyczne ścieżki i query

Zwykła operacja `paths` może mieć parametr `in: path`, `required: true`,
`schema.type: string` i `x-tdw-catch-all: true`. To rozszerzenie TDW, nie
standardowy wildcard OpenAPI. Parametr musi wystąpić raz, jako ostatni pełny
segment wzorca, np. `/{resourcePath}` albo `/rest/{resourcePath}`.
AI przekazuje `path.resourcePath: "rest/api/3/issue/DEMO-1"`. Wartość nie
zaczyna się `/`, nie zawiera URL, `%`, query/hash, backslash, znaków
sterujących lub segmentów `.`/`..`. Każdy segment jest kodowany osobno;
server host i base path pozostają stałe. Zwykłe parametry nadal są
pojedynczymi segmentami. Flaga przechodzi przez preview/select/document,
zapis YAML V1 i wspólny parsed kontrakt SDK/MCP, bez nowego endpointu.

Standardowy parametr query `type: object`, `style: form`, `explode: true`
(także domyślne true) rozwija klucze mapy do nazw parametrów URL. Np.
parametr nazwany `params` dostaje `query.params: {fields: "summary,status"}`.
Wartości to string/number/boolean; nie obsługujemy zagnieżdżonych obiektów,
tablic w mapie ani null, których serializacja nie jest częścią tego wariantu.
Jawne query arrays nadal obsługują powtarzane klucze/CSV. Schema mapy jest
walidowany; klucze i wartości kodowane osobno, kolizja z innym parametrem
lub mapą jest błędem przed HTTP. Pusta mapa jest dozwolona.

Metodę określa zwykła operacja get/post/put/patch/delete. Swobodne JSON body
opisuje `schema: {}`; DELETE body wymaga OpenAPI 3.1, GET nie ma body.
Host pochodzi z servers, Auth z captured przypisania API–Auth w profilu.
AI nie może przekazać URL, metody ani nagłówków poza zdefiniowanym schema.
Skutki nadal określa `x-tdw-operation-effect`, bez zgadywania po metodzie.

Odpowiedź z `content: {'*/*': {schema: {}}}` zachowuje dowolny JSON (także
application/*+json), tekst text/* z charset/UTF-8, wspierany plik według Content-Type albo reprezentację binarną
`{encoding: "base64", contentType: "...", data: "..."}` dla innych typów
i braku Content-Type. Dla rozpoznanych plików nie umieszczamy base64 w JSON wyniku.
Status HTTP pozostaje w OpenApiResult. Błędny JSON
nie ma fallbacku do tekstu i nadal blokuje dalsze mutacje po nieustalonym wyniku.
Nie ma nowego retry, redirectu ani wyboru Auth przez AI.

[Przykładowy kontrakt Jira](../examples/system-assistant/jira-generic-http.yaml)
udostępnia pięć metod na przykładowym hoście; przed użyciem operator podaje
własny host i przypina Auth w profilu.

Nazwa narzędzia ma maksymalnie 64 znaki. Pełna postać to
`oa_<apiId>_<operationId>`; gdy jest dłuższa, importer zachowuje jej pierwsze
47 znaków i dodaje `_` oraz pierwsze 16 cyfr hex SHA-256 pełnej nazwy UTF-8.
Wynik jest niezależny od kolejności i wyboru innych operacji. Ten sam wynik
zasila SDK, MCP, allowlistę i evidence. Kolizja wynikowych nazw blokuje import
lub zapis całego katalogu, zamiast nadpisywać operację. `operationId` wymaga
składni `[a-zA-Z][a-zA-Z0-9_]*` i unikalności w dokumencie; nie ma osobnego
limitu 64 znaków. Pełny identyfikator pozostaje w YAML, preview, podglądzie,
eksporcie V1 i opisie narzędzia. Zmiana apiId albo operationId zmienia nazwę.
Schema argumentów grupuje parametry w `path`, `query` i `body`. Required, typy, enum i limity
są sprawdzane przed HTTP. Default nie jest samoczynnie dopisywany do requestu.
Opcjonalne parametry `in: header` (`required: false` lub brak required) nie
blokują importu. Po zastosowaniu dziedziczenia i override są pomijane przed
budowaniem argumentów i listy parametrów wykonawczych. Header names są
rozpoznawane bez rozróżnienia wielkości liter. Ich schema/default/example nie
służą do przygotowania requestu. Oryginalne deklaracje zostają w YAML,
podglądzie i eksporcie; preview oraz opis narzędzia informują o pominięciu.
Wymagany header oraz niepoprawny typ required blokują import. Narzędzie nie
ma argumentu header/headers; dodatkowe argumenty są odrzucane przed HTTP.
Nie generujemy ani nie wysyłamy correlationId z takiej deklaracji.
Authorization z operator-owned Auth pozostaje odrębnym mechanizmem.

Metadane operacji zachowują media types odpowiedzi dla każdego statusu oraz
`default`, także puste deklaracje bez content. `Accept` zawiera sumę typów
z dokumentu; przy braku content pozostaje application/json. Poniższe reguły
dotyczą deklaracji bez `*/*`; dekodowanie jawnego wildcardu opisano wyżej. Rzeczywisty
`Content-Type` rozstrzyga odczyt: application/json jest parsowany jako JSON,
a text/plain jako tekstowy JsonNode w istniejącym `body` wyniku. Text/plain
wymaga deklaracji dla danego statusu lub default; jawny status bez content
nie dziedziczy default. Charset pochodzi z nagłówka, domyślnie UTF-8; błędne
kodowanie lub charset daje nieustalony wynik, bez zastępowania znaków.
Brak Content-Type zachowuje obsługę JSON poza jawną deklaracją odpowiedzi
application/octet-stream, opisaną w sekcji plików. Nie ma fallbacku błędnego JSON do
tekstu ani interpretacji tekstu wyglądającego jak JSON. Pusty text/plain
zwraca pusty string; 204/205 i pusty JSON zwracają null. Pozostałe media
types z niepustym body są nieobsługiwane poza obsługą plików opisaną poniżej.
Adapter odczytuje pełne body bez aplikacyjnego limitu rozmiaru odpowiedzi;
Auth, status HTTP i guard
potencjalnego zapisu pozostają.

Query array pozostaje tablicą JSON w `arguments.query.<name>` SDK/MCP.
Wykonawca HTTP serializuje `style: form` (także domyślne): `explode: true`
(domyślne dla query) powtarza klucz, np. `ids=101&ids=102`; `explode: false`
łączy osobno kodowane elementy literalnym przecinkiem, np. `ids=101,102`.
Przecinek wewnątrz elementu jest kodowany jako `%2C`, a znaki `&`, `=`, `+`,
`%`, `#`, nawiasy i Unicode nie zmieniają struktury URI. Kolejność elementów
jest zachowana; `uniqueItems`, enum, required i limity waliduje JSON Schema.
`items` wymaga typu string/integer/number/boolean bez null; brak jawnych typów,
obiekty, zagnieżdżone tablice i inne style nie są zgadywane.
Brak opcjonalnej tablicy pomija parametr. Jawna pusta tablica jest odrzucana
przed HTTP, bez zamiany na brak parametru lub pusty string; opis argumentu
informuje o wymaganiu niepustej tablicy. Pusty string jako element tablicy
stringów pozostaje pustą wartością. Oryginalny schema/YAML pozostaje bez zmian.
Efektywne `explode` należy do neutralnych metadanych `Parameter`, bez
model-facing wyboru serializacji, dowolnego URL albo query stringa.

PATCH używa tego samego wykonawcy i walidacji co pozostałe operacje.
Dopuszcza `application/json`, opcjonalne body oraz brak body. Nie znosi
schema required, nie dopisuje defaultów, nie pobiera całego obiektu do
scalenia i nie zamienia metody na PUT. `application/json-patch+json` oraz
`application/merge-patch+json` nie należą do obsługiwanego podzbioru.
Operation-level security i jego securitySchemes są walidowane i zachowane
w selekcji również dla PATCH.

Operacja przechowuje osobno walidacyjny schema OpenAPI i projekcję JSON Schema
argumentów. Walidacja korzysta z networknt JSON Schema Validator 2.0.0,
Draft 2020-12 po adaptacji OpenAPI 3.0; pobieranie zasobów jest wyłączone.
Format pozostaje adnotacją. `allOf` zachowuje wszystkie ograniczenia,
`oneOf` wymaga dokładnie jednego dopasowania, `anyOf` co najmniej jednego.
`pattern` jest ograniczeniem stringów, nie adnotacją. Import sprawdza typ
string oraz składnię tą samą fabryką regex co walidacja requestu: jawnie
skonfigurowaną JDKRegularExpressionFactory networknt. To składnia silnika JDK
z adaptacją networknt, bez gwarancji pełnej zgodności z ECMAScript. Nie
przepisujemy wzorców i nie dopisujemy kotwic: bez `^`/`$` wystarczy pasujący
fragment tekstu. Pusty pattern jest poprawny; null i inne typy nie podlegają
samemu pattern (pozostałe ograniczenia nadal obowiązują). Wzorzec pozostaje
w YAML, podglądzie, eksporcie i schema SDK/MCP, także przez refs/allOf oraz
zagnieżdżone tablice i mapy. Błędny typ/składnia daje OPENAPI_INVALID ze
wskazaniem /pattern przed publikacją. Niezgodna wartość jest odrzucana przed
HTTP przez istniejący walidator; nie ma model-facing obejścia. Schema
odpowiedzi ma kontrolę składni, bez nowej walidacji wartości odpowiedzi.
Discriminator i jawny mapping są zachowane w dokumencie, a projekcja opisuje
je w description; nie zastępują walidacji wariantów ani nie ustanawiają
wywnioskowanych powiązań enum–nazwa klasy. Mapping zawiera istniejące lokalne
schema refs. Preview pokazuje uwagi o oneOf, braku jawnego mapping i skutkach.

Pola readOnly są pomijane w projekcji i requestowym required, również przy
allOf. Backend odrzuca ich wysłanie przed HTTP. Nullable nie znosi required
ani enum. `readOnly` na całym body lub parametrze nie usuwa tego argumentu.
Schematy odpowiedzi są sprawdzane jako graf dokumentacji; nie są rozwijane
w schemat argumentów ani walidowane jako runtime response.
Oryginalne adnotacje pozostają w YAML-u, podglądzie i eksporcie.

Limity: 12 kontraktów po 1000000 znaków, do 60 operacji w kontrakcie i 100
w zestawie; 20 skilli po 20000 znaków. Źródło może mieć do 2000000
bajtów/znaków i 5000 operacji. Projekcja argumentów ma do 250000 znaków na
narzędzie i 1000000 łącznie w katalogu. Limit dotyczy także rozwinięcia refs.
Argumenty callbacka mają limit 65536 znaków. Odpowiedź HTTP jest odczytywana
w całości, bez aplikacyjnego limitu rozmiaru.
Connect timeout wynosi 3 s, read timeout 8 s. Przekroczenia są jawne;
odpowiedź nie jest po cichu przycinana ani zastępowana pustym sukcesem.

## Wybór operacji przy imporcie

`OpenApiSelectionPort` jest neutralną capability obok portów importu i wykonania.
Odczytuje katalog źródła OpenAPI 3.0.x/3.1.x bez walidowania wykonania każdej
niewybranej operacji. Preview zwraca katalog kandydatów oraz kopię JSON
źródła do efemerycznej prezentacji szczegółów; nie nadaje jej statusu kontraktu
wykonawczego. Modal CDK używa tego samego renderera Swagger UI co podgląd.
Pokazuje metodę, ścieżkę, opis, operationId, parametry, odpowiedzi i schematy
oraz checkbox i powód niewybieralności operacji. Obsługuje wyszukiwanie, filtry metody/tagu,
tylko wybrane, zaznaczanie całego kontraktu i strony po 50 operacji.
Angular posiada wybór i filtry; przekazuje do iframe tylko paths bieżącej
strony wraz z kontekstem components i deklaracjami tylko używanych na niej tagów.
Paginacja pokazuje zakres i całkowitą liczbę operacji po filtrach; puste grupy
tagów z pozostałych stron nie są renderowane. Globalna lista modeli jest w imporcie
ukryta, podobnie jak powtórzony nagłówek kontraktu i sekcja serwera.
Ukrywanie zależy od trybu wyboru całej ramki, również po zwinięciu grup.
Wrapper dokumentacji nie ma bocznych odstępów w modalu wyboru. Checkboxy
pozostają wyśrodkowane względem nagłówka operacji także po zawinięciu ścieżki
lub rozwinięciu szczegółów; obserwatory układu są zwalniane po zwinięciu grupy.
Schematy odpowiedzi są dostępne po rozwinięciu operacji. Zmiana samego
checkboxa synchronizuje stan bez przeładowania dokumentu i zwijania szczegółów.
Nowy plik zaczyna z pustym wyborem. Modal ma jeden nagłówek i ikonę zamknięcia;
wiersz filtrów zawiera szukanie, metodę HTTP, tag, `Tylko wybrane` i ikony
`select_all` / `deselect` z tooltipami. Nazwa kontraktu jest w stopce obok
`Użyj wybranych operacji`, przed dodaniem do szkicu.
Zaimportowany kontrakt pozwala zmienić samą nazwę API w szkicu konfiguracji;
YAML, ID i wybór operacji nie mają trybu edycji. Metody poza GET/POST/PUT/DELETE/PATCH i nieprawidłowy operationId są
niewybieralne. Pozostałą semantykę sprawdza obecny importer po wyborze.
`Zaznacz wszystko` wybiera wszystkie dostępne operacje całego kontraktu,
niezależnie od filtrów, strony i widoku tylko wybranych. `Odznacz wszystko`
usuwa cały wybór. Limit 60 nadal blokuje zatwierdzenie większego zestawu.

Para metoda + ścieżka identyfikuje wybieraną operację. Przygotowanie zachowuje
wybrane metody, wspólne parametry i metadata ich ścieżek, domknięcie lokalnych
refs w components oraz używane tagi. Niewybrane operacje i nieosiągalne
components są usuwane. Nie pobiera zewnętrznych refs i nie pomija pól
wpływających na wybraną operację. Wynik przechodzi pełną walidację wykonawczą,
limit 60 operacji i 1000000 znaków na kontrakt. Błąd pozostawia modal oraz wybór.

Preview i przygotowanie są bezstanowe: nie zapisują pliku ani nie publikują
tools. Zatwierdzenie modalu dodaje przygotowany kontrakt do szkicu. Zapis
całej konfiguracji aktywuje wynik dla kolejnej tury i MCP. Pełny plik źródłowy
pozostaje wyłącznie w efemerycznym stanie otwartego modalu, bez archiwum,
cache i nowego pola persistence. Po zamknięciu zachowany jest tylko wynik.
Dodanie później innej operacji wymaga ponownego wczytania źródła. Anulowanie
nie zmienia szkicu. Ekran pokazuje kontrakty w osobnej zakładce, z akcjami podglądu, eksportu i usunięcia.
Zmiana treści kontraktu wymaga nowego importu. Nie ma ręcznej edycji YAML-a ani ID.
Ikona ołówka przy kontrakcie rozwija shared text-field „Nazwa API” (1–100 znaków,
nie same białe znaki). Edycja aktualizuje wyłącznie name w szkicu i etykiety
powiązanych API w profilach, bez zmiany YAML info.title, ID lub przypisań.
Utrwala ją wspólny zapis konfiguracji; niepoprawna nazwa blokuje zapis i otwiera
edytor. Przełączanie zakładek i błąd zapisu zachowują szkic. Import konfiguracji
i usunięcie edytowanego API resetują lokalny stan edytora.

## Widoki pracy i podgląd kontraktów

Wspólne `analysis-result-tabs` mają jeden poziom: Skille, Kontrakty, Auth, Profile,
Rozmowa oraz Uruchamianie w systemie. Bezpośrednie `/system-assistant` zaczyna od Skilli;
wejście z `localRunId` otwiera Rozmowę, a osadzony route Browser Tools zaczyna
od wyboru nowej lub zapisanej rozmowy, bez zakładek konfiguracji.
Nie ma nadrzędnej zakładki Konfiguracja ani drugiego przełącznika.
Sześć zakładek ma równą szerokość i wypełnia cały dostępny pasek przez
shared `equalWidth=true`; na wąskim ekranie pozostaje paginacja Material.
Sam wybór zakładki nie jest utrwalany w storage ani w URL.
Aktywny link sidebar nadal nie remountuje strony.

Strona posiada wspólny szkic skilli i kontraktów, preferencje AI, ingress
i rozmowę. Panel shared chatu jest ukrywany atrybutem hidden, więc zachowuje
wpisaną wiadomość; polling trwa również poza widokiem Rozmowa. Przełączenie
nie zapisuje konfiguracji i nie tworzy ani nie wysyła rozmowy.

Pełny ekran porównuje szkic z ostatnią konfiguracją odczytaną lub zapisaną
przez serwer. Gdy wartości się różnią, opuszczenie trasy wymaga potwierdzenia,
a odświeżenie lub zamknięcie karty wywołuje ostrzeżenie przeglądarki. Cofnięcie
edycji do zapisanych wartości usuwa ostrzeżenie. Błąd zapisu zachowuje ochronę.
Zmiana zakładki lub rozmowy zachowuje szkic bez pytania.

Jeśli rozmowa wymaga OIDC, pełny ekran pyta o odrzucenie zmian przed
rozpoczęciem logowania. Anulowanie pozostawia wiadomość w polu i pozwala
kontynuować edycję. Zgoda obejmuje jedno przekierowanie z tym samym szkicem.
Szkic konfiguracji pozostaje w pamięci strony.

Skille, Kontrakty, Auth i Profile korzystają ze wspólnej oprawy zapisu całego zestawu.
Przesunięcie wiersza w zakładce zmienia kolejność odpowiedniej tablicy w szkicu.
Eksport zachowuje tę kolejność, a wspólny zapis utrwala ją w `configuration.json`.
Skille, profile i Auth są początkowo zwinięte. Kliknięcie belki rozwija edytor;
belka pokazuje nazwę i odpowiednio ID skilla, zakres URL z liczbą skilli/API
profilu albo typ Auth. `*` jest prezentowane jako „Wszystkie adresy”.
Ostrzeżenie o brakujących zasobach profilu pozostaje dostępne po zwinięciu.
Dodany wpis jest od razu rozwinięty. Zwijanie zachowuje szkic, a przełączanie
zakładek zachowuje stan belek; stan jest wyłącznie lokalny dla otwartej strony.
Import konfiguracji rozpoczyna ze zwiniętymi wpisami. Zapis rozwija niepoprawne
wpisy Auth, aby pokazać błędy pól.
Nad każdą listą jest licznik wpisów i akcja `Dodaj…` po prawej, bez
powtarzania nazwy zakładki. Wewnętrzne poziome linie zastępują odstępy;
delikatny hover obejmuje całą belkę, łącznie z obszarem akcji.
Wiersze używają jednej gęstości, nazwy i pomocniczego opisu po lewej
oraz małych ikonowych akcji po prawej; usuwanie jest ostatnią akcją każdego
wiersza. Jeden sticky pasek pod listą pokazuje stan tylko niezapisanych zmian,
komunikaty i błąd zapisu. Import, eksport i `Zapisz konfigurację` są obok siebie
w grupie akcji całego zestawu; zakres zapisu wyjaśnia tooltip.
Pierwszy widok
pozwala dodawać, edytować i usuwać instrukcje; drugi dodaje kontrakt wyłącznie
przez modal importu z nazwą i wyborem operacji oraz oferuje edycję nazwy, podgląd, eksport i usunięcie.
Eksport pobiera dokładny YAML bieżącej belki jako plik `.yaml`, również ze
szkicu przed zapisem. Zawiera tylko zachowane operacje i potrzebne schematy;
nie odtwarza pełnego źródła i nie aktywuje konfiguracji. Nazwa pliku pochodzi
z nazwy kontraktu, z użyciem wspólnego sanityzowania nazw plików.
Belka ma nazwę, ID rozróżniające jednakowo nazwane kontrakty, opisany licznik
aktywnych operacji oraz ikony edycji nazwy, podglądu, eksportu i usunięcia z tooltipami.
Na belce kontraktu znajduje się liczba aktywnych operacji. Tooltip pokazuje
ich metody i ścieżki; można go otworzyć także fokusem klawiatury. Liczba pochodzi
z zapisanego katalogu, z jawnym przypisaniem `apiId` przygotowanym podczas
importu do snapshotu. Nie dopasowujemy narzędzi po prefiksie nazwy. Nowy szkic
ma zero aktywnych operacji do zapisu; odświeżanie lub błąd katalogu pokazuje
`—`, zamiast sugerować zero. Nie ma osobnej listy wszystkich narzędzi.
Jeden zapis nadal aktywuje cały zestaw; DTO i format zapisu nie zmieniają się.
Rozmowa zawiera kontekst strony, model/reasoning, chat, activity i evidence.
Uruchamianie w systemie pokazuje pełną instrukcję Browser Tools. Przewodnik
wskazuje wyłącznie kontrolki bieżącej zakładki.

`OpenApiSelectionPort.document` waliduje zapisany YAML tym samym importerem
i zwraca kopię JSON. Endpoint dokumentu nie zapisuje konfiguracji i nie
wywołuje systemu źródłowego. CDK modal wyświetla lokalny Swagger UI 5.35.1
w iframe sandbox z `allow-scripts`, z izolowanym stylem i cyklem życia.
Dokument trafia wyłącznie do konkretnego child window przez postMessage;
renderer przyjmuje dokument i aktualizacje wyłącznie od swojego rodzica.
Wspólny feature-owned OpenApiRenderer obsługuje tryb read-only oraz wybór
operacji. Plugin wrapComponents.operation dodaje kontrolowane checkboxy,
które zgłaszają metodę, ścieżkę i checked. Angular weryfikuje event.source,
typy, dokładne klucze wiadomości oraz znaną dostępną operację; backend nadal
waliduje finalny wybór. Zmiana strony/filtra aktualizuje specActions.updateJsonSpec,
a aktualizacja zaznaczeń odświeża tylko checkboxy pluginu. Escape wewnątrz ramki
przekazuje żądanie zamknięcia, weryfikowane po event.source. Po zamknięciu
fokus wraca do przycisku podglądu.

Zasoby JS/CSS i licencje są pod `/openapi-viewer/vendor`, kopiowane z przypiętego
pakietu npm; nie ma CDN. Katalog `/openapi-viewer` jest wyłączony z fallbacku
tras SPA, a brakujący zasób zwraca 404. Spec jest obiektem, bez url/configUrl. Wyłączone są
Try it out, online validator, query config i credentials, a requestInterceptor
odmawia wywołań HTTP. CSP ramki dodatkowo ma connect-src none. Podgląd nie
publikuje tools i nie uruchamia AI. Zewnętrzne katalogi i pozostałe feature'y
nie zmieniają sposobu renderowania ani CSS.

## Aktywacja i bieżący zapis

Zapis jest serializowany. Po walidacji cały zestaw przechodzi przez przygotowanie operacji,
aktualizacja handlerów MCP, atomowy zapis pliku i publikacja katalogu oraz
feature snapshotu. Zakończony sukcesem PUT oznacza aktywację całego zestawu.
Przy błędzie publikacji lub zapisu następuje przywrócenie poprzedniego
katalogu. MCP aktualizuje zmienione definicje narzędzi. Transport usuniętego profilu
zamyka dopiero po udanym zapisie, dzięki czemu przy błędzie klient zachowuje
sesję i stan blokady operacji o nieustalonym wyniku. Szkic UI pozostaje edytowalny.

Każdy zapis przygotowuje dla profilu listę bieżących skilli i operacji z przypisanym
uwierzytelnianiem. MCP publikuje te operacje. Przyjęcie tury pobiera gotowy zestaw
według `profileId` do sprawdzenia OIDC, promptu i callbacków SDK.

Jeden aktualny plik:
`${tdw.workspace.directory}/system-assistant/configuration.json`.
Koperta ma format `version=1`; treści nie mają wersji, rewizji, digestów,
ETag ani archiwalnych kopii. Restart odtwarza wyłącznie bieżący zestaw.
Uszkodzony plik blokuje użycie konfiguracji i chat, ale UI pozwala zapisać
poprawny zestaw. Nie stosuje się migracji ani cichego fallbacku do pustych danych.

## Profile i przypisania

Konfiguracja zawiera wspólne katalogi API/skilli oraz maksymalnie 20 profili:
`{id,name,urlPattern,apiBindings,skillIds}`. ID nadawane w UI jest stałe, nazwa
jest edytowalna i ma limit 100 znaków. Wzorzec ma domyślną wartość `*`
oraz limit 2300 znaków. Przypisania są referencjami do aktualnych
zasobów, nie kopiami. Jeden zapis aktywuje cały zestaw. Stary kształt V1
bez profiles nie jest obsługiwany ani migrowany.

Zakładka Profile ma edytor nazwy i wzorca oraz listy checkboxów API i skilli.
Przy wybranym API ma select Auth, domyślnie No Auth. Każde API występuje
w danym profilu najwyżej raz. Brak wskazanego wpisu Auth blokuje zapis.
Usunięcie zasobu pozostawia jego ID w przypisaniu. Ikona ostrzeżenia
w konfiguracji profilu pokazuje brakujące ID w tooltipie; operator może
usunąć przypisanie. Braki nie blokują zapisu ani rozmowy, również gdy
profil nie ma już żadnych dostępnych zasobów. Nie omija to walidacji
istniejących kontraktów YAML.

W chacie select Profil jest obok modelu/reasoningu. Wybór jest roboczym
stanem UI, bez zapisu w historii. Wymagane profileId jest wysyłane per tura.
Snapshot.forProfile wybiera istniejące przypisane API i skille oraz ich
operacje. Nieistniejący profil jest odrzucany przed przyjęciem wiadomości.
Nie ma fallbacku do całego katalogu. Trwająca tura używa zamrożonego zestawu,
zmiana profilu lub zasobu wpływa na następną przyjętą turę. Katalog MCP
danego profilu wynika z adresu endpointu i bieżącej konfiguracji. Wybór
w chacie nie przełącza profilu używanego przez odrębnego klienta MCP.

W panelu lista profili jest filtrowana przez pełne origin + pathname
bieżącej strony nadrzędnej, niezależnie od kontekstu rozmowy wybranej
z historii. Wildcard `*` dopasowuje dowolny ciąg, reszta znaków jest literalna;
dopasowanie całego ciągu rozróżnia wielkość liter i nie zawiera query/hash.
Brak poprawnego ingress dopuszcza tylko dokładny wzorzec `*`. Pełny ekran
zawsze pokazuje wszystkie profile. Brak wyboru lub dopasowań wymaga wyboru
lub konfiguracji, bez automatycznego wskazania profilu. Select odświeża
zapisane profile przy otwarciu przez `configuration/profiles`. Ramka używa tego
samego odczytu przy starcie. Pełną konfigurację i katalog definicji narzędzi
pobiera edytor na pełnym ekranie. Oba widoki współdzielą komponent rozmowy.

## Import i eksport konfiguracji

W dolnym pasku obok zapisu są ikony importu i eksportu całej konfiguracji
z tooltipami. Eksport `system-assistant-configuration.json` zawiera bieżący
szkic: `{version:1, configuration:{apis,skills,profiles,authProfiles}}`, z polami API `id,name,yaml`
i skilla `id,name,content`. Nie obejmuje rozmów, kontekstu strony, preferencji
AI ani runtime access/refresh tokenów. Obejmuje jawne pola katalogu Auth. Eksport zachowuje treść YAML/Markdown także przed zapisem.
Import zastępuje cały szkic i otwiera Skille; nie zapisuje ani nie publikuje
narzędzi. Dopiero `Zapisz` waliduje i aktywuje zestaw. Parser pliku wymaga
dokładnych pól i typów, wersji 1, do 12 API, 20 skilli, 20 profili i 20 konfiguracji Auth oraz limitu 100000000
bajtów. Pozwala odtworzyć niekompletne pola tekstowe; walidacja biznesowa
kontraktów pozostaje na serwerze przy zapisie. Błędny plik zachowuje szkic.
Podczas odczytu blokowana jest edycja konfiguracji i zapis, bez zatrzymywania
rozmowy. Nie ma migracji ani rewizji; import obejmuje profile i przypisania Auth.
Poprzedni kształt V1 bez authProfiles nie jest obsługiwany.
Eksport konfiguracji jest odrębny od eksportu transkryptu ze wspólnej
Analysis History. Nie ma importu rozmowy z pliku.

## Tura AI

1. API sprawdza PAT Copilota i przyjmuje jedną aktywną turę na rozmowę.
2. Pod blokadą rozmowy pobiera aktualny snapshot konfiguracji i rozwiązuje profileId, dopisuje USER
   i IN_PROGRESS assistant, a następnie oddaje wykonanie do lokalnej kolejki.
3. Callbacki tury zamykają jej niemutowalne operacje i przypisania Auth. Późniejsza edycja nie
   zmienia ich hosta, schema ani handlera.
4. Feature składa NEW `CopilotRunRequest`: wyłącznie aktualne tools i
   skille przypisane do profilu inline jako durable instructions, `skillsEnabled=false`, jawny
   transcript i mały kontekst strony. Feature-owned adapter po prepare dodaje wszystkie zapisane pliki rozmowy do MessageOptions jako SDK BlobAttachment (base64, MIME, oryginalna nazwa).
5. Instrukcje jawnie podają aktualną listę tools i ich liczbę, także zero.
   Historia zawiera wiadomości i wyniki działań, bez dawnych promptów,
   YAML-i i skilli jako aktywnej konfiguracji.
6. Gateway wykonuje sesję z istniejącą allowlistą/hook i publikuje activity.
   Feature-owned listener, ograniczony do sesji `system-assistant-*` i tools
   `oa_*`, przechwytuje argumenty, wynik, outcome i latency jako evidence.
7. Odpowiedź i usage zamykają wiadomość. Błąd zachowuje wykonane tool evidence;
   brak potwierdzenia nie jest deklaracją cofnięcia. Prompt wiadomości jest null.
8. Cleanup usuwa jednorazową sesję SDK. Następna tura tworzy nową sesję,
   korzystając z bieżącej konfiguracji i historii TDW.

Rozmowy mają trwały transkrypt we wspólnej historii, bez aplikacyjnego limitu
liczby tur ani długości wiadomości użytkownika. Wiadomość musi być niepusta.
Kolejka ma 16 oczekujących miejsc i 2 workers. Nie jest durable queue.
Historia promptu zawiera cały transkrypt i wyniki narzędzi, bez aplikacyjnego
limitu znaków ani skracania. Nadal obowiązuje pojemność kontekstu wybranego
modelu; większe treści zwiększają zużycie pamięci, dysku i tokenów.
Restart odtwarza transkrypt, ale nie ponawia działań
ani przerwanych tur. Kontynuacja wymaga nowej wiadomości użytkownika.

Bez report tools i initial reportu. Analysis History przechowuje transkrypt,
a otwarcie kieruje do feature-owned chatu. Istniejące analizy
zachowują swoje report-first oraz EXISTING continuation. Zewnętrzna
diagnostyka OTLP pozostaje konfiguracją platformy; feature nie tworzy
osobnego magazynu telemetryki ani historycznych konfiguracji.

## Załączniki rozmowy

### Plik jako odpowiedź OpenAPI

Response content przyjmuje `image/png`, `image/jpeg`, `image/webp`, `image/gif`,
`application/pdf` i `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
ze `schema: {type: string, format: binary}`. Działa też istniejący `*/*`.
Rzeczywisty Content-Type określa format; dla jawnego MIME musi być zgodny
z deklaracją odpowiedniego statusu/default. Nazwa pochodzi z Content-Disposition,
jest normalizowana do basename albo zastępowana `response.<ext>`.

Jawne `application/octet-stream` ze `schema: {type: string, format: binary}`
dla danego statusu lub `default` włącza rozpoznawanie pliku z bajtów po HTTP
2xx, z wyłączeniem 204/205. Działa bez Content-Type, z application/octet-stream
oraz z konkretnym binarnym MIME zgodnym z wykrytym formatem. Rozpoznawane są
PDF, PNG, JPEG, GIF i WebP po sygnaturze; DOCX wymaga ZIP z wpisem
`word/document.xml`. Nazwa i rozszerzenie pliku nie są dowodem formatu.
Nieznany format, pusta odpowiedź plikowa lub niezgodność Content-Type daje
`outcome: RESPONSE_RECEIVED`, zachowany status HTTP i `fileDelivery: FAILED`,
bez pliku ani base64. Operacja nie jest ponawiana. JSON/text korzysta z
dotychczasowego dekodowania; błędy HTTP nie uruchamiają tej detekcji.
Rozpoznanie nie dowodzi kompletnego odczytu dokumentu; PDF i DOCX przechodzą
jeszcze przez swoje czytniki. Samo `*/*` nie włącza rozpoznawania bajtów:
ogólny/brakujący MIME nadal daje reprezentację base64.

REST zwraca neutralny `OpenApiFile` w pamięci poza tekstowym JSON, którego body
zawiera nazwę, MIME i rozmiar. `agenttools.content.ToolContentCallback` przenosi
tekst i binaria niezależnie od SDK/MCP. Wspólny `common.documents.PdfPageRenderer`
renderuje wszystkie strony PDF do JPEG 144 DPI, tak samo jak przy uploadzie.
DOCX jest odczytywany przez JDK ZIP/StAX: tekst dokumentu, nagłówków, stopek,
przypisów i komentarzy oraz obsługiwane obrazy z word/media. Bez odtwarzania
układu Worda, wykonywania relacji zewnętrznych ani DTD. Obrazy są przekazywane
bezpośrednio. Wszystkie modele używają tej samej ścieżki.

Adapter Copilota zwraca `ToolResultObject` z `binaryResultsForLlm`, MCP tekst
i image content. Model otrzymuje treść po tool call w tej samej turze.
Nie zapisujemy pobranego pliku przy runie ani nie dołączamy go automatycznie
w następnych turach. Ponowne wywołanie toola wykonuje nowe HTTP z bieżącym
wynikiem; nie ma cache ani automatycznego pobierania adresów URL z JSON.
Zwykły tekstowy wynik (w tym tekst odczytany z DOCX) podlega dotychczasowemu
capture/history; binaria obrazów nie trafiają do activity, logów TDW ani eksportu.
Jawny zewnętrzny OTLP zachowuje własną konfigurację capture-content.
Błąd odczytu pliku zachowuje status i outcome wykonanego HTTP, dodaje
`fileDelivery: FAILED` i nie ponawia operacji. Write guard pozostaje.

### Pliki dodane przez operatora

Upload `application/octet-stream` z nazwą w query zapisuje dane bezpośrednio w
`${tdw.workspace.directory}/runs/{id}/attachments/{uuid}.{extension}`. Oryginalna
nazwa jest metadaną, MIME wynika z dozwolonego rozszerzenia. Upload wymaga
istniejącej rozmowy i tego samego operation guard co send/delete; podczas tury
zwraca konflikt. Nie uruchamia AI. Endpoint i UI nie nakładają limitu liczby
ani rozmiaru plików i nie sprawdzają kompatybilności modelu. Nadal obowiązują
limity transportu, zasobów i CLI/modelu.

Metadane są w `storeSnapshot.state.attachments` lokalnego run.json, poza
`exportEnvelope`, i w snapshotach feature API. Zapis transkryptu zachowuje ten
snapshot; brak załączników to pusta lista. Eksport rozmowy nie zawiera ani
binariów, ani katalogu załączników. Odczyt historii odtwarza metadane, a każda
NEW tura czyta ponownie pliki i przekazuje je jako SDK blobs. PDFBox renderuje
wszystkie strony PDF w pamięci do JPEG (144 DPI), z nazwą dokumentu i numerem
strony, jednakowo dla każdego modelu. Zachowuje to także skany i diagramy;
omija pomijanie natywnego PDF w ścieżce GPT z narzędziami. Pozostałe formaty
pozostają natywnymi blobs. Oryginał PDF pozostaje w runie, bez zapisu pochodnych,
OCR, zmiany modelu ani tools/policy. Brak zapisanego
pliku kończy turę jawnym błędem. Activity SDK zawiera tylko metadane attachments,
bez base64 i ścieżek plikowych. Zewnętrzny OTLP zachowuje swoją jawną konfigurację.

Neutralny `common.documents.DocumentAttachmentPreparer` posiada preparację
PDF oraz mapę rozszerzeń MIME załączników. Feature mapuje jego wynik na SDK
blobs. Ten sam mechanizm zasila [File Reader w Tool Workbench](file-reader-workbench.md),
który pokazuje obrazy bez AI, a dla DOCX zwraca tylko walidację odczytu.

Oba widoki mają przycisk wyboru wielu plików w slocie akcji shared chatu i listę
oczekujących nazw przy composerze. Przy przyjęciu wiadomości metadane nowych
plików dostają `messageId` tej wiadomości USER; null oznacza oczekujący plik.
Powiązanie jest zapisane przed AI i odtwarzane z historią, poza eksportem.
Odrzucenie przed przyjęciem nie przypisuje plików, a odrzucenie przez kolejkę
cofa powiązanie razem z wiadomością. Shared chat dostaje mapę nazw per ID
wiadomości i pokazuje je nad jej treścią; kolejne tury nie powielają etykiet.
Nie odtwarzamy dawnych powiązań z diagnostyki. Wybranie plików tworzy pustą rozmowę, jeśli to potrzebne,
i zapisuje je kolejno przed kolejną wiadomością. Upload blokuje wysyłanie,
wybór rozmowy i kolejne dodawanie. Po odświeżeniu nazwy wracają z GET rozmowy.
Nie ma usuwania pojedynczych plików ani osobnego cache na czas OIDC. Istniejące
odtwarzanie tekstu OIDC pozostaje. Pliki pozostają do usunięcia runu przez
feature API albo Analysis History; neutralny store usuwa cały katalog rekurencyjnie.
Nieudany zapis może pozostawić nieprzypisany plik, który też usuwa dopiero run.

## Wykonanie i wynik operacji

RestClient wysyła JSON jako bajty UTF-8 z jawną długością body. Nie podąża
za redirectami ani nie implementuje retry na poziomie aplikacji. JDK może
ponawiać GET po niektórych zerwaniach połączenia, także GET UNKNOWN;
pozostałe wykonywane metody POST/PUT/PATCH/DELETE nie są ponawiane. Wynik
zawiera `outcome`, status HTTP, body i ograniczenie potwierdzenia. HTTP
200 nie jest uniwersalną walidacją biznesową; model musi uwzględnić body.
Operacja ma jawne `x-tdw-operation-effect`: `READ_ONLY`, `MUTATION` albo
`UNKNOWN` (domyślnie). Nazwa ani metoda HTTP nie potwierdzają skutków.
Nieustalony wynik operacji MUTATION/UNKNOWN, także GET, to `OUTCOME_UNKNOWN`;
blokuje kolejne operacje MUTATION/UNKNOWN tej tury, dopuszczając READ_ONLY.
Błąd potwierdzonego odczytu daje `REQUEST_FAILED`. Skille i kontrakt opisują
znaczenie biznesowe, ale nie obchodzą technicznej blokady.
Host pozostaje przypięty do skonfigurowanej operacji;
model nie wybiera URL-a ani nie przejmuje tokenów lub cookies strony.

Callbacki Copilota i MCP używają tego samego importera, modeli i wykonawcy.
Copilot używa callbacków lokalnie, nie połączenia MCP w SDK. MCP jest
rzeczywistym osobnym endpointem: aktualizuje listę i handlery oraz wysyła
notifications/tools/list_changed. Klient musi ponownie odczytać tools/list;
nie zakładamy automatycznego refreshu dowolnego klienta. Po usunięciu operacji
nowe wywołanie jej nazwy jest odrzucane. MCP utrzymuje guard per sesja
protokołu i profil, także pomiędzy kolejnymi calls i zmianą katalogu.
Stan jest ograniczony do 1024 sesji na ekspozycję profilu; limit blokuje nowe
wywołania zamiast usuwać istniejące guardy. Usunięcie ekspozycji usuwa stan.

## Trwała historia rozmów

Każde POST tworzące rozmowę zapisuje ją do neutralnego LocalAnalysisRunStore
i wspólnego indeksu Analysis History z feature `system-assistant`. Koperta
feature'a `tdw.system-assistant-conversation`, version 1 zawiera createdAt
i conversation (id, busy, pageContext, messages). Nie zawiera konfiguracji
API/skilli ani uchwytu sesji SDK. Metadane indeksu obejmują tytuł z pierwszej
wiadomości (zmienialny z historii), status i daty. Plik jest w
`${tdw.workspace.directory}/runs/{id}/run.json`, indeks w
`${tdw.workspace.directory}/index.json`.
Wymagany jest włączony lokalny workspace.

Przyjęcie wiadomości jest zapisane przed uruchomieniem AI. Evidence/activity
i wynik aktualizują ten sam transkrypt. W pamięci pozostają tylko aktywne
tury i terminalne wyniki oczekujące na odzyskanie zapisu. Wspólny operation
guard blokuje usuwanie aktywnej rozmowy przez oba API. Błąd przyjęcia przed
zapisem nie uruchamia AI; po utracie storage wynik pozostaje do zapisania
bez ponownego wykonania. Restart zamienia utrwaloną IN_PROGRESS na FAILED
z TURN_INTERRUPTED, zachowuje evidence i nie ponawia operacji źródłowych.

Historia otwiera `/system-assistant?localRunId={id}` w zakładce Rozmowa.
Feature GET conversations/{id} odtwarza transkrypt i pierwotny kontekst strony.
Aktywna tura jest dalej odpytywana; zakończona pozwala wysłać nową wiadomość
na ten sam ID. Każda nowa tura pobiera aktualny snapshot konfiguracji i tworzy
NEW sesję SDK. Historyczny materiał jest danymi, nie aktywnym zestawem
narzędzi lub instrukcji. Nie korzystamy z generic history chat POST ani
SDK session continuation. Błąd odczytu nie tworzy zastępczej rozmowy.
`Nowa rozmowa` czyści bieżące UI/URL, zachowując poprzedni zapis; jawne DELETE
lub usunięcie z historii usuwa rozmowę. Usunięty zapis nie wraca z cache.

## Browser Tools i frontend

Kafelek `System Assistant` otwiera na badanej stronie panel w izolowanym
Shadow DOM i iframe TDW pod `/system-assistant/chat`, poza AppShell.
Ten sam feature-owned komponent reuse'uje template rozmowy i jej stan,
model/reasoning, chat i polling. Osadzony widok nie pokazuje
konfiguracji, sidebaru ani zakładek pełnego ekranu. Rozmowa w obu miejscach
ma kompaktowy układ: przewijana historia, edytor i jeden pasek akcji na dole.
Model/reasoning korzysta z shared compact 32 px; kontekst strony jest pod
ikoną informacji, nowa rozmowa pod ikoną. Tok AI i koszty nie są renderowane w panelu
osadzonym; pełny ekran używa standardowego shared aside. Bez powtórzonych nagłówków i opisów. Enter wysyła, Shift+Enter wstawia
nową linię. Ramka startuje z szerokością 700 px, ograniczoną dostępnym
viewportem, i zajmuje wysokość viewportu z marginesem 8 px.
Lewą krawędź można przeciągać (Pointer Events z capture i osłoną iframe);
minimum to 320 px albo szerokość mniejszego viewportu minus marginesy.
Maksimum to szerokość viewportu minus 16 px. Przycisk obok zamknięcia
przełącza maksymalizację i poprzednią szerokość. Rozmiar i maksymalizacja
pozostają w instancji panelu po schowaniu/otwarciu, bez storage i reloadu
iframe. Zmiana viewportu dopasowuje widoczny rozmiar bez utraty wybranej
szerokości. Separator jest dostępny z klawiatury: lewo/prawo zmienia o 20 px
(Shift: 50 px), Home/End ustawia minimum/maksimum. Anulowanie pointera,
utrata capture/fokusu, schowanie i dispose kończą gest i usuwają osłonę.
Handshake sprawdza konkretny origin, event.source (rodzic / contentWindow
ramki), nonce, wersję protokołu 1 i dokładne klucze wiadomości. Fragment
transferu jest usuwany z URL TDW. Nie ma transferu przez opener dla tej akcji.
UI Explorer zachowuje własny popup, a UX capture pozostaje osobny.

Rodzic przekazuje origin, pathname, pełny URL z query i fragmentem, tytuł do 200
znaków i capturedAt. Panel śledzi zmianę adresu i tytułu w tym samym dokumencie.
Przy wysyłaniu ponownie pobiera stronę od rodzica, potwierdza dopasowanie profilu
i przekazuje snapshot jako `pageContext` wiadomości. Prompt dostaje URL i tytuł
z tej tury. Zmiana strony zachowuje rozmowę i szkic, a utrata dopasowania
wymaga wyboru nowego profilu. Po powrocie z OIDC ramka ponownie nawiązuje
połączenie. Automatyczne wznowienie wymaga tego samego pełnego URL i profilu.
Zamknięcie panelu chowa tę samą ramkę,
zachowując rozmowę i draft wiadomości do ponownego otwarcia. Usunięcie Browser
Tools usuwa panel i listenery. Iframe ma no-referrer i sandbox
allow-scripts allow-same-origin allow-forms; wiadomości chatu pozostają na originie TDW.
Przy błędzie lub blokadzie frame-src panel udostępnia ponowienie i ręczny
link do pełnego TDW, bez zmiany CSP i uprawnień przeglądarki.
Bezpośredni route również działa bez strony źródłowej.

Niepotwierdzona wiadomość pozostaje dostępna do ręcznego przywrócenia;
błąd pollingu zachowuje snapshot i daje retry odczytu, bez ponawiania polecenia.
Nowa rozmowa odłącza dotychczasowy chat w UI, pozostawiając zapis w historii.
Klik aktywnego linku nawigacji nie remountuje ekranu.

## Wybór rozmowy na pełnym ekranie i w panelu osadzonym

Spójny kafelek System Assistant w Browser Tools otwiera w tej samej ramce
wybór nowej lub zapisanej rozmowy. Feature-owned picker pobiera wyłącznie
metadane ze wspólnego history API na originie TDW, filtruje feature
system-assistant, wyszukuje po nazwie i pokazuje kolejne partie po 50 wpisów.
Na `/system-assistant` ikona `Historia rozmów` w pasku akcji chatu zakładki
Rozmowa otwiera ten sam picker. Pełny ekran zachowuje początkową zakładkę
Skille; historia nie jest pobierana do czasu jej otwarcia. Wybór rozmowy
aktualizuje `localRunId` w URL.
Pełny transkrypt jest pobierany dopiero po wyborze ID. Rodzic ramki nie
otrzymuje listy, tytułów ani wiadomości; handshake pozostaje bez zmian.

Ikona historii w composerze wraca do wyboru w obu trybach. Otwarcie nie
przeładowuje iframe ani nie remountuje chatu, a anulowanie zachowuje bieżący
chat i szkic. Na pełnym ekranie picker pozostaje w zakładce Rozmowa;
przełączanie zakładek zachowuje wyszukiwanie. Aside jest ukryty w historii
i wraca z rozmową. Przy przełączaniu feature
zachowuje w pamięci szkic, błąd i wiadomość bez potwierdzenia dla danego ID;
nowa rozmowa wraca do roboczego nowego szkicu. Błąd wyboru pozostawia listę
i poprzednią rozmowę. Aktywna tura/wczytywanie blokują zmianę. Odpowiedzi
HTTP i pollingu poprzedniego widoku nie podmieniają nowego wyboru.
Kontynuacja w ramce łączy zapisany transkrypt z bieżącą stroną i aktualnymi skillami/tools.
Pełny ekran może przekazać pierwotny kontekst oznaczony jako strona przy otwarciu. Brak toku AI i kosztów w ramce.
Nagłówek osłony ma trzy równe pola ikon SVG: otwarcie TDW, maksymalizacja
i zamknięcie. Ikona przeciąganego bookmarkletu jest SVG, bez tekstowej
ligatury w nazwie; etykieta zakładki to `TDW Browser Tools`.

## Weryfikacja

Automatyczne testy obejmują importer/REST (także brak retry zapisu po zerwaniu
połączenia), zamrożenie konfiguracji tury, aktualne instrukcje, trwały
transkrypt i odtworzenie po restarcie, błędy storage, blokady usuwania,
realny transport MCP oraz wyłączenie flagi. Testy FE obejmują wspólny szkic,
import/eksport, wejście z historii, wybór rozmowy w ramce, zachowanie szkiców
oraz odrzucenie spóźnionych odpowiedzi HTTP. Node/JSDOM sprawdza protokół,
izolację ramki, resize/maximize i regresję UX Inspectora/UI Explorera.

Fixtures OpenAPI są w `src/test/resources/openapi` i zawierają małe,
fikcyjne modele oraz adresy loopback/example.invalid. Nie kopiujemy kontraktu
operatora ani jego nazw domenowych do testów. Jedna fixture kompozycji ma
dwa warianty i wspólną bazę; duży rozmiar i graniczne grafy refs są generowane
w testach. Testy HTTP używają własnych izolowanych serwerów; nie wymagają
osobnego projektu demo.
Weryfikacja w przeglądarce obejmuje kontynuację wybranego zapisu na innej
stronie po zmianie konfiguracji: zachowanie zapamiętanego kodu i pierwotnego
pathname, zastosowanie aktualnego skilla oraz wywołanie aktualnego narzędzia
potwierdzone przez tool evidence i rzeczywiste żądanie HTTP.

`SYSTEM_ASSISTANT_LIVE_TEST=true` uruchamia `SystemAssistantCopilotLiveTest`:
prawdziwy Copilot, sześć tur na fikcyjnym CRM, odczyt, kalkulacja, jeden zapis
i read-back, dodanie/edycja/usunięcie API oraz skilli, pusty katalog i usage.
Test korzysta z istniejącego PAT Workspace Settings, izolowanego katalogu
CLI i nie zapisuje konfiguracji pilota do workspace operatora.
Osobny przypadek `readsScannedPdfAndUsesOpenApiAcrossNewSessions` sprawdza
dwustronicowy PDF bez warstwy tekstowej razem z rzeczywistym odczytem OpenAPI,
także po przejściu do kolejnej NEW sesji.
`readsFilesReturnedByApiWithoutSavingAttachments` sprawdza pliki jako wyniki
OpenAPI: zmieniający się PDF po ponownym pobraniu, PNG i DOCX z osadzonym
obrazem, bez zapisanych załączników i base64 w historii TDW.
