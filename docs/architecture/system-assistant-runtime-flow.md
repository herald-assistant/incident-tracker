# System Assistant — runtime

## Zakres produktu

System Assistant jest samodzielnym chatem operacyjnym. Nie wymaga initial
analizy ani `AnalysisReport`. Użytkownik utrzymuje bieżący zestaw wielu
OpenAPI YAML i skilli roboczych, a kolejne tury tej samej rozmowy korzystają
z zapisanej aktualnej treści. Ekran: `/system-assistant`.

Operator może podłączyć HTTP(S) API dowolnego systemu, z hostem DNS, IP lub
localhost, w obsługiwanym podzbiorze OpenAPI 3.0.x opisanym poniżej.
Bieżący zakres obsługuje jedną osobę w workspace. Nie obejmuje source auth, załączników,
approval workflow ani integracji CLP.
Konfigurację, rozmowę i testy opisuje [instrukcja obsługi](../system-assistant.md).
Skille określają sposób pracy AI, nie egzekwują uprawnień biznesowych.
Kontrakt operatora lub jego proxy może ograniczać i orkiestrować wywołania API;
TDW nie posiada osobnego silnika polityk ani delegacji tożsamości źródłowej.

## Włączenie i wyłączenie

Flaga `system-assistant.enabled` jest domyślnie wyłączona i steruje dostępnością feature'a. Nie ustawia
ani nie waliduje `server.address`: adres nasłuchiwania TDW pozostaje wspólną
konfiguracją aplikacji. API konfiguracji, chat i MCP są dostępne pod adresem
TDW. Docelowe usługi wskazane przez YAML nie mają listy dopuszczonych hostów.
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

## Kontrakty HTTP

| Endpoint | Kontrakt |
| --- | --- |
| `GET /api/system-assistant/configuration` | Bieżące `{apis, skills, profiles}`; uszkodzony zapis daje 503 `CONFIGURATION_UNAVAILABLE` |
| `PUT /api/system-assistant/configuration` | Zastępuje cały aktualny zestaw po walidacji i skutecznym zapisie |
| `POST /api/system-assistant/configuration/openapi/preview` | `{id, yaml}`; katalog operacji, limit wyboru i specification (kopia JSON do dokumentacji), bez aktywacji |
| `POST /api/system-assistant/configuration/openapi/document` | `{id, yaml}`; zwalidowany dokument JSON do podglądu, bez zapisu i publikacji |
| `POST /api/system-assistant/configuration/openapi/selection` | `{id, yaml, operations:[{method,path}]}`; zwalidowany YAML wyboru i operationCount, bez zapisu |
| `GET /api/system-assistant/configuration/tools` | ID kontraktu `apiId`, wygenerowane nazwy, opisy, metody, ścieżki i input schema |
| `POST /api/system-assistant/conversations` | `{pageContext}` lub null context; `201` i pusta rozmowa bez AI |
| `GET /api/system-assistant/conversations/{id}` | ID, busy, przypięty pageContext i neutralne wiadomości |
| `POST /api/system-assistant/conversations/{id}/messages` | `{message, profileId, model?, reasoningEffort?}`; `202` i natychmiastowy snapshot |
| `DELETE /api/system-assistant/conversations/{id}` | `204`; aktywna tura daje `409` |
| `GET /api/analysis/runs` | Wspólny indeks historii; rozmowy mają `feature=system-assistant` |
| `GET /api/analysis/runs/{id}/export` | Archiwalny JSON transkryptu; bez konfiguracji API/skilli |
| `/api/openapi-tools/mcp` | Osobny Streamable HTTP MCP z initialize, list/call i powiadomieniem o zmianie tools |

API definition to `{id, name, yaml}`, skill to `{id, name, content}`.
ID elementu nie jest rewizją. Duplikaty ID i kolizje nazw tools blokują
aktywację. Pusty zestaw jest poprawny i pozwala rozmawiać bez API.

## Importowany podzbiór OpenAPI

Parser używa SafeConstructor SnakeYAML, odrzuca duplikaty kluczy i aliasy,
ogranicza głębokość oraz wielkość dokumentu. Nie obsługuje wszystkich
legalnych konstrukcji OpenAPI; nieobsługiwana semantyka daje `OPENAPI_INVALID`
z lokalizacją przed zapisem.

| Obszar | Obsługiwane | Odrzucane |
| --- | --- | --- |
| Dokument | `3.0.x`, metadata info/tags/externalDocs, paths, components.schemas | Swagger 2, OpenAPI 3.1+, inne pola wykonawcze |
| Server | Jeden jawny top-level URL HTTP(S) z dowolnym poprawnym hostem DNS/IP/localhost, opcjonalny base path | Brak hosta, nieprawidłowy port, credentials, query/hash, server variables i override path/operation |
| Operacje | GET, POST, PUT, DELETE; wymagany unikalny operationId z liter/cyfr/underscore | PATCH i pozostałe metody, callbacks/webhooks, operacyjne `$ref` |
| Parametry | Scalar path/query, path required; dziedziczenie path-level i override operacji | Headers/cookies, object/array, pozostałe style i content parameters |
| Body | POST/PUT `application/json`, wymagane lub opcjonalne body | GET/DELETE body, multipart, binary, pozostałe media types |
| Schema | Jawny type object/array/string/number/integer/boolean; properties/items/required/enum/additionalProperties boolean; minimum/maximum, min/maxLength i min/maxItems; readOnly, nullable, format; metadata description/title/example/default | oneOf/anyOf/allOf, pattern, dodatkowe keywords i schema-valued additionalProperties |
| References | Lokalne acykliczne `#/components/schemas/...`, bez rodzeństwa `$ref` | Zewnętrzne refs i cykle; rozwinięcie ponad 10000 węzłów lub 20 poziomów |
| Responses | Kody liczbowe 100–599 lub default, description, opcjonalne JSON content/schema | Response refs, headers, links, inne content types |
| Auth | Brak credentials; puste security | Wymagania security i components.securitySchemes |

Nazwa narzędzia: `oa_<apiId>_<operationId>`, do 64 znaków. Schema argumentów
grupuje parametry w `path`, `query` i `body`. Required, typy, enum i limity
są sprawdzane przed HTTP. Default nie jest samoczynnie dopisywany do requestu.
Operacja przechowuje osobno walidacyjny schema OpenAPI i projekcję JSON Schema
dla argumentów SDK/MCP. Pola readOnly są pomijane w projekcji i w requestowym
required; próba przesłania ich w argumentach jest odrzucana przed HTTP.
Adnotacja readOnly poza definicją pola nie wyłącza całego body lub parametru.
Nullable mapuje się w narzędziu na `type: [typ, "null"]`, ale nie znosi
obowiązku obecności pola ani ograniczeń enum. Format jest adnotacją zachowaną
także dla własnych formatów; backend nie dodaje walidacji składni UUID,
email lub dat. Typy readOnly/nullable muszą być boolean, a format string.
Oryginalne adnotacje pozostają w YAML-u wyboru, podglądzie i eksporcie.
Nie ma dowolnego URL ani nagłówka Authorization w argumentach modelu.

Limity: 12 YAML-i po 100000 znaków, do 60 operacji w YAML i 100 w zestawie;
20 skilli po 20000 znaków. Źródło wybierane w UI może mieć do 2000000 bajtów/znaków i 5000 operacji;
limit 100000 dotyczy przygotowanego YAML-a zapisywanego w konfiguracji.
Argumenty callbacka mają limit 65536 znaków, odpowiedź HTTP — 64 KiB.
Connect timeout wynosi 3 s, read timeout 8 s. Przekroczenia są jawne;
odpowiedź nie jest po cichu przycinana ani zastępowana pustym sukcesem.

## Wybór operacji przy imporcie

`OpenApiSelectionPort` jest neutralną capability obok portów importu i wykonania.
Odczytuje katalog źródła OpenAPI 3.0.x bez walidowania wykonania każdej
niewybranej operacji. Preview zwraca katalog kandydatów oraz kopię JSON
źródła do efemerycznej prezentacji szczegółów; nie nadaje jej statusu kontraktu
wykonawczego. Modal CDK używa tego samego renderera Swagger UI co podgląd.
Pokazuje metodę, ścieżkę, opis, operationId, parametry, odpowiedzi i schematy
oraz checkbox i powód niewybieralności operacji. Obsługuje wyszukiwanie, filtry metody/tagu,
tylko wybrane, zaznaczanie całego kontraktu i strony po 50 operacji.
Angular posiada wybór i filtry; przekazuje do iframe tylko paths bieżącej
strony wraz z kontekstem components. Globalna lista modeli jest w imporcie
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
Zaimportowany kontrakt nie ma trybu edycji. Metody poza GET/POST/PUT/DELETE i nieprawidłowy operationId są
niewybieralne. Pozostałą semantykę sprawdza obecny importer po wyborze.
`Zaznacz wszystko` wybiera wszystkie dostępne operacje całego kontraktu,
niezależnie od filtrów, strony i widoku tylko wybranych. `Odznacz wszystko`
usuwa cały wybór. Limit 60 nadal blokuje zatwierdzenie większego zestawu.

Para metoda + ścieżka identyfikuje wybieraną operację. Przygotowanie zachowuje
wybrane metody, wspólne parametry i metadata ich ścieżek, domknięcie lokalnych
refs w components oraz używane tagi. Niewybrane operacje i nieosiągalne
components są usuwane. Nie pobiera zewnętrznych refs i nie pomija pól
wpływających na wybraną operację. Wynik przechodzi pełną walidację wykonawczą,
limit 60 operacji i 100000 znaków na YAML. Błąd pozostawia modal oraz wybór.

Preview i przygotowanie są bezstanowe: nie zapisują pliku ani nie publikują
tools. Zatwierdzenie modalu dodaje przygotowany kontrakt do szkicu. Zapis
całej konfiguracji aktywuje wynik dla kolejnej tury i MCP. Pełny plik źródłowy
pozostaje wyłącznie w efemerycznym stanie otwartego modalu, bez archiwum,
cache i nowego pola persistence. Po zamknięciu zachowany jest tylko wynik.
Dodanie później innej operacji wymaga ponownego wczytania źródła. Anulowanie
nie zmienia szkicu. Ekran pokazuje kontrakty w osobnej zakładce, z akcjami podglądu, eksportu i usunięcia.
Zmiana kontraktu wymaga nowego importu. Nie ma ręcznej edycji YAML-a, ID
lub nazwy zaimportowanego kontraktu.

## Widoki pracy i podgląd kontraktów

Wspólne `analysis-result-tabs` mają jeden poziom: Skille, Kontrakty, Profile,
Rozmowa oraz Uruchamianie w systemie. Bezpośrednie `/system-assistant` zaczyna od Skilli;
wejście z `localRunId` otwiera Rozmowę, a osadzony route Browser Tools zaczyna
od wyboru nowej lub zapisanej rozmowy, bez zakładek konfiguracji.
Nie ma nadrzędnej zakładki Konfiguracja ani drugiego przełącznika.
Sam wybór zakładki nie jest utrwalany w storage ani w URL.
Aktywny link sidebar nadal nie remountuje strony.

Strona posiada wspólny szkic skilli i kontraktów, preferencje AI, ingress
i rozmowę. Panel shared chatu jest ukrywany atrybutem hidden, więc zachowuje
wpisaną wiadomość; polling trwa również poza widokiem Rozmowa. Przełączenie
nie zapisuje konfiguracji i nie tworzy ani nie wysyła rozmowy.

Skille i Kontrakty korzystają ze wspólnej oprawy zapisu całego zestawu.
Akcje `Dodaj skill` lub `Dodaj OpenAPI` oraz wyróżniony `Zapisz` są na dole,
wyrównane do prawej; `Zapisz` jest ostatni. Komunikaty konfiguracji i błąd
zapisu są nad tym paskiem. Pierwszy widok
pozwala dodawać, edytować i usuwać instrukcje; drugi dodaje kontrakt wyłącznie
przez modal importu z nazwą i wyborem operacji oraz oferuje podgląd, eksport i usunięcie.
Eksport pobiera dokładny YAML bieżącej belki jako plik `.yaml`, również ze
szkicu przed zapisem. Zawiera tylko zachowane operacje i potrzebne schematy;
nie odtwarza pełnego źródła i nie aktywuje konfiguracji. Nazwa pliku pochodzi
z nazwy kontraktu, z użyciem wspólnego sanityzowania nazw plików.
Belka ma nazwę, licznik oraz ikony podglądu, eksportu i usunięcia z tooltipami.
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

Zapis jest serializowany: walidacja całego zestawu, przygotowanie operacji,
aktualizacja handlerów MCP, atomowy zapis pliku i publikacja katalogu oraz
feature snapshotu. Zakończony sukcesem PUT oznacza aktywację całego zestawu.
Przy błędzie publikacji lub zapisu następuje przywrócenie poprzedniego
katalogu; szkic UI pozostaje edytowalny, bez komunikatu o aktywacji.

Jeden aktualny plik:
`${tdw.workspace.directory}/system-assistant/configuration.json`.
Koperta ma format `version=1`; treści nie mają wersji, rewizji, digestów,
ETag ani archiwalnych kopii. Restart odtwarza wyłącznie bieżący zestaw.
Uszkodzony plik blokuje użycie konfiguracji i chat, ale UI pozwala zapisać
poprawny zestaw. Nie stosuje się migracji ani cichego fallbacku do pustych danych.

## Profile i przypisania

Konfiguracja zawiera wspólne katalogi API/skilli oraz maksymalnie 20 profili:
`{id,name,urlPattern,apiIds,skillIds}`. ID nadawane w UI jest stałe, nazwa
jest edytowalna i ma limit 100 znaków. Wzorzec ma domyślną wartość `*`
oraz limit 2300 znaków. Przypisania są referencjami do aktualnych
zasobów, nie kopiami. Jeden zapis aktywuje cały zestaw. Stary kształt V1
bez profiles nie jest obsługiwany ani migrowany.

Zakładka Profile ma edytor nazwy i wzorca oraz listy checkboxów API i skilli.
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
zmiana profilu lub zasobu wpływa na następną przyjętą turę. Globalny katalog
MCP nie jest filtrowany wyborem jednego chatu.

W panelu lista profili jest filtrowana przez pełne origin + pathname
przekazane przy uruchomieniu ramki, niezależnie od kontekstu rozmowy wybranej
z historii. Wildcard `*` dopasowuje dowolny ciąg, reszta znaków jest literalna;
dopasowanie całego ciągu rozróżnia wielkość liter i nie zawiera query/hash.
Brak poprawnego ingress dopuszcza tylko dokładny wzorzec `*`. Pełny ekran
zawsze pokazuje wszystkie profile. Brak wyboru lub dopasowań wymaga wyboru
lub konfiguracji, bez automatycznego wskazania profilu. Select odświeża
zapisane profile przy otwarciu; niezapisany szkic ich nie podmienia.

## Import i eksport konfiguracji

Obok wspólnego paska zakładek są ikony importu i eksportu całej konfiguracji
z tooltipami. Eksport `system-assistant-configuration.json` zawiera bieżący
szkic: `{version:1, configuration:{apis,skills,profiles}}`, z polami API `id,name,yaml`
i skilla `id,name,content`. Nie obejmuje rozmów, kontekstu strony, preferencji
AI ani autentykacji. Eksport zachowuje treść YAML/Markdown także przed zapisem.
Import zastępuje cały szkic i otwiera Skille; nie zapisuje ani nie publikuje
narzędzi. Dopiero `Zapisz` waliduje i aktywuje zestaw. Parser pliku wymaga
dokładnych pól i typów, wersji 1, do 12 API, 20 skilli i 20 profili oraz limitu 10000000
bajtów. Pozwala odtworzyć niekompletne pola tekstowe; walidacja biznesowa
kontraktów pozostaje na serwerze przy zapisie. Błędny plik zachowuje szkic.
Podczas odczytu blokowana jest edycja konfiguracji i zapis, bez zatrzymywania
rozmowy. Nie ma migracji ani rewizji; import obejmuje też profile i przypisania.
Eksport konfiguracji jest odrębny od eksportu transkryptu ze wspólnej
Analysis History. Nie ma importu rozmowy z pliku.

## Tura AI

1. API sprawdza PAT Copilota i przyjmuje jedną aktywną turę na rozmowę.
2. Pod blokadą rozmowy pobiera aktualny snapshot konfiguracji i rozwiązuje profileId, dopisuje USER
   i IN_PROGRESS assistant, a następnie oddaje wykonanie do lokalnej kolejki.
3. Callbacki tury zamykają jej niemutowalne operacje. Późniejsza edycja nie
   zmienia ich hosta, schema ani handlera.
4. Feature składa NEW `CopilotRunRequest`: wyłącznie aktualne tools i
   skille przypisane do profilu inline jako durable instructions, `skillsEnabled=false`, jawny
   transcript i mały kontekst strony. Nie używa SDK attachments.
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

Rozmowy mają trwały transkrypt we wspólnej historii; limit to 20 tur w rozmowie.
Kolejka ma 16 oczekujących miejsc i 2 workers. Nie jest durable queue.
Historia promptu ma limit 120000 znaków: przekroczenie jest błędem, bez
cichego skracania. Restart odtwarza transkrypt, ale nie ponawia działań
ani przerwanych tur. Kontynuacja wymaga nowej wiadomości użytkownika.

Bez report tools i initial reportu. Analysis History przechowuje transkrypt,
a otwarcie kieruje do feature-owned chatu. Istniejące analizy
zachowują swoje report-first oraz EXISTING continuation. Zewnętrzna
diagnostyka OTLP pozostaje konfiguracją platformy; feature nie tworzy
osobnego magazynu telemetryki ani historycznych konfiguracji.

## Wykonanie i wynik operacji

RestClient wysyła JSON jako bajty UTF-8 z jawną długością body. Nie podąża
za redirectami ani nie implementuje retry. Wynik
zawiera `outcome`, status HTTP, body i ograniczenie potwierdzenia. HTTP
200 nie jest uniwersalną walidacją biznesową; model musi uwzględnić body.
Nieustalony wynik HTTP zapisu to `OUTCOME_UNKNOWN`; callbacki tej tury
blokują kolejne zapisy i pozwalają sprawdzić stan odczytem. Błąd odczytu
to `REQUEST_FAILED`. Rozróżnienie transportowe GET/pozostałe nie jest
klasyfikacją skutków dowolnego API. Skille operatora i kontrakt dostarczają
kontekst skutków operacji. Host pozostaje przypięty do skonfigurowanej operacji;
model nie wybiera URL-a ani nie przejmuje tokenów lub cookies strony.

Callbacki Copilota i MCP używają tego samego importera, modeli i wykonawcy.
Copilot używa callbacków lokalnie, nie połączenia MCP w SDK. MCP jest
rzeczywistym osobnym endpointem: aktualizuje listę i handlery oraz wysyła
notifications/tools/list_changed. Klient musi ponownie odczytać tools/list;
nie zakładamy automatycznego refreshu dowolnego klienta. Po usunięciu operacji
nowe wywołanie jej nazwy jest odrzucane.

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
nową linię. Ramka zajmuje wysokość viewportu z marginesem 8 px.
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

Payload zawiera tylko origin, pathname, tytuł do 200 znaków i capturedAt.
Bez query/hash, DOM, formularza, store, historii HTTP, cookies i tokenów.
Kontekst jest obserwacją z chwili otwarcia; nie wybiera credentials ani
autoryzowanego obiektu. ID zgłoszenia użytkownik podaje w wiadomości.
Zmiana strony nie przepina rozmowy. Zamknięcie panelu chowa tę samą ramkę,
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

## Wybór rozmowy w panelu osadzonym

Spójny kafelek System Assistant w Browser Tools otwiera w tej samej ramce
wybór nowej lub zapisanej rozmowy. Feature-owned picker pobiera wyłącznie
metadane ze wspólnego history API na originie TDW, filtruje feature
system-assistant, wyszukuje po nazwie i pokazuje kolejne partie po 50 wpisów.
Pełny transkrypt jest pobierany dopiero po wyborze ID. Rodzic ramki nie
otrzymuje listy, tytułów ani wiadomości; handshake pozostaje bez zmian.

Ikona historii w composerze wraca do wyboru. Otwarcie nie przeładowuje
iframe, a anulowanie zachowuje bieżący chat. Przy przełączaniu feature
zachowuje w pamięci szkic, błąd i wiadomość bez potwierdzenia dla danego ID;
nowa rozmowa wraca do roboczego nowego szkicu. Błąd wyboru pozostawia listę
i poprzednią rozmowę. Aktywna tura/wczytywanie blokują zmianę. Odpowiedzi
HTTP i pollingu poprzedniego widoku nie podmieniają nowego wyboru.
Kontynuacja używa poprzedniego pageContext i transkryptu oraz aktualnych
skilli/tools dla każdej następnej tury. Brak toku AI i kosztów w ramce.
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

Fixtures OpenAPI są w `src/test/resources/openapi`. Testy HTTP używają
własnych izolowanych serwerów; nie wymagają osobnego projektu demo.
Weryfikacja w przeglądarce obejmuje kontynuację wybranego zapisu na innej
stronie po zmianie konfiguracji: zachowanie zapamiętanego kodu i pierwotnego
pathname, zastosowanie aktualnego skilla oraz wywołanie aktualnego narzędzia
potwierdzone przez tool evidence i rzeczywiste żądanie HTTP.

`SYSTEM_ASSISTANT_LIVE_TEST=true` uruchamia `SystemAssistantCopilotLiveTest`:
prawdziwy Copilot, sześć tur na fikcyjnym CRM, odczyt, kalkulacja, jeden zapis
i read-back, dodanie/edycja/usunięcie API oraz skilli, pusty katalog i usage.
Test korzysta z istniejącego PAT Workspace Settings, izolowanego katalogu
CLI i nie zapisuje konfiguracji pilota do workspace operatora.
