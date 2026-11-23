# System Assistant — obsługa

System Assistant korzysta z kontraktów OpenAPI i instrukcji utrzymywanych
przez operatora. Umożliwia zadawanie pytań, odczyt stanu, obliczenia
i zlecanie operacji udostępnionych w kontrakcie. API może wskazywać dowolny
poprawny host HTTP(S), w obsługiwanym podzbiorze OpenAPI 3.0.x/3.1.x.
Feature jest domyślnie wyłączony; uruchom TDW z
`--system-assistant.enabled=true`. Wybór adresu nasłuchiwania TDW jest
niezależny od tej flagi. AI wymaga skonfigurowanego PAT Copilota w Workspace Settings.

## Konfiguracja

Cały zestaw można też przygotować poza TDW na podstawie repozytorium lub dokumentów.
Przekaż modelowi [samodzielną instrukcję tworzenia konfiguracji](system-assistant-configuration-authoring.md),
a następnie zaimportuj wygenerowany plik JSON.

Otwórz `/system-assistant`. Ekran zaczyna od `Skilli` i ma sześć zakładek:
`Skille`, `Kontrakty`, `Auth`, `Profile`, `Rozmowa` oraz `Uruchamianie w systemie`.
W zakładce `Skille` dodaj instrukcje pracy
asystenta. W zakładce `Kontrakty` wybierz `Dodaj OpenAPI` i wskaż plik JSON lub YAML
swojego API. W modalu wybierz operacje, nadaj nazwę kontraktu i użyj
`Użyj wybranych operacji`. Do szkicu trafiają tylko wybrane operacje
i potrzebne im schematy. Kliknij `Zapisz`, aby aktywować konfigurację.
Następna przyjęta tura korzysta z aktualnie zapisanego zestawu.
Zmiana szkicu nie wpływa na chat przed zapisem. Tura już rozpoczęta kończy
pracę na swoim zestawie. Przyciski dodawania oraz `Zapisz` są na dole po
prawej, a błędy zapisu nad nimi. Przełączanie zakładek zachowuje szkic.

Swagger UI pozwala przejrzeć parametry, odpowiedzi i schematy. Wyszukiwanie,
filtry metody/tagu i `Tylko wybrane` ułatwiają wybór. `Zaznacz wszystko`
obejmuje wszystkie dostępne operacje kontraktu, niezależnie od filtrów
i strony; `Odznacz wszystko` usuwa cały wybór. Niedostępne operacje mają
opis powodu. Anulowanie importu zachowuje poprzedni szkic.

Wiersz kontraktu ma akcje `Podgląd`, `Eksportuj YAML` i `Usuń`. Podgląd nie
wykonuje żądań do API. Aby zmienić zestaw operacji, usuń kontrakt ze szkicu
i zaimportuj nowy plik; oba kroki możesz aktywować jednym zapisem.
Licznik na belce pokazuje aktywne operacje zapisanego kontraktu, a tooltip
ich metody i ścieżki. Nowo dodany kontrakt ma zero aktywnych operacji do zapisu.

Ikony obok zakładek oferują `Importuj konfigurację` i `Eksportuj konfigurację`.
Eksport obejmuje cały bieżący szkic kontraktów, skilli, profili i Auth,
również niezapisany.
Import zastępuje szkic; wymaga `Zapisz`, aby aktywować zestaw. Niepoprawny
plik nie zmienia szkicu. Rozmowy, preferencje AI, logowanie do Copilota
i pamięciowe połączenia OIDC pozostają poza tym eksportem.

## Długie identyfikatory operacji

Długi `operationId` nie blokuje importu. Nazwa narzędzia ma do 64 znaków;
gdy trzeba, TDW skraca ją automatycznie i dodaje stabilny hash. Pełny
`operationId` pozostaje w dokumentacji, eksporcie i opisie narzędzia,
więc możesz używać go w instrukcjach. Zmiana kolejności albo wybór mniejszego
zestawu operacji nie zmienia wygenerowanej nazwy.

## Listy w parametrach query

Parametr query może być jednowymiarową tablicą tekstów, liczb lub boolean.
AI przekazuje np. `{"query":{"productIds":[101,102]}}`. Dla domyślnego
`style: form` i `explode: true` TDW wysyła `productIds=101&productIds=102`;
przy `explode: false` wysyła `productIds=101,102`. Elementy są kodowane osobno
i walidowane zgodnie ze schema, także enum, uniqueItems i limity liczby.
Opcjonalny parametr można pominąć. Pusta tablica jest odrzucana przed HTTP;
obiekty query, tablice zagnieżdżone i null nie należą do tego zakresu.

## Certyfikaty API i dostawcy logowania

Backend respektuje wspólne ustawienie `integrations.http.ignore-ssl-errors`.
Przy false sprawdza certyfikat i nazwę hosta; przy true pomija te kontrole
dla API oraz OAuth2/OIDC, w tym tokenów, discovery i JWKS. Zmiana wymaga
restartu. Przeglądarka na stronie logowania ma własną politykę certyfikatów;
walidacja podpisu i tożsamości tokenów nadal obowiązuje.

## Wzorce tekstowe

Ograniczenie `pattern` jest zachowywane w schema i egzekwowane przed
wywołaniem API. Nieprawidłowy typ lub składnia regexu blokuje import.
Wzorzec bez `^` i `$` może pasować do fragmentu tekstu; TDW nie dopisuje
kotwic. Obsługiwana składnia wynika z silnika JDK w walidatorze networknt.

## Odpowiedzi tekstowe

Odpowiedź `text/plain` ze schema `type: string` jest obsługiwana przez SDK
i MCP. Treść trafia jako string do istniejącego pola `body` wyniku narzędzia.
TDW wysyła `Accept` z deklaracji OpenAPI, a odpowiedź odczytuje według
`Content-Type` i charsetu (domyślnie UTF-8). Tekst nie jest zamieniany na JSON
nawet wtedy, gdy wygląda jak JSON. Błędny application/json nadal jest błędem.
Obowiązuje limit 64 KiB i dotychczasowa blokada po nieustalonym zapisie.
Request body nadal obsługuje wyłącznie application/json.

## Operacje PATCH

Możesz importować i wybierać PATCH z `application/json` lub bez body.
Wysyłany jest dokładnie JSON przygotowany zgodnie ze schematem requestu;
TDW nie pobiera i nie odsyła całego DTO ani nie dopisuje domyślnych wartości.
Required nadal obowiązuje. Auth i blokada po nieustalonym wyniku działają
jak dla pozostałych potencjalnych zapisów.

Wymagane nagłówki, cookies oraz JSON Patch i Merge Patch z odrębnymi media
types pozostają nieobsługiwane.

## Opcjonalne nagłówki

Deklaracja opcjonalnego header, np. correlationId, nie blokuje importu.
Brak required oznacza false. Nagłówek pozostaje widoczny w dokumentacji,
ale AI nie otrzymuje argumentu do jego podania, a TDW go nie generuje ani
nie wysyła. Panel uwag informuje, które nagłówki są pomijane. Wymagany
header nadal blokuje import. Autoryzacja z profilu Auth działa niezależnie.

## Kompozycja modeli i skutki operacji

Importer zachowuje `allOf`, `oneOf`, `anyOf`, discriminatory i mapy
z typowanymi wartościami. Rozwiń `Uwagi do wybranych operacji` w modalu,
aby zobaczyć ograniczenia kontraktu. Nakładające się warianty `oneOf` są
odrzucane przed wysłaniem żądania; brak mapping nie jest naprawiany przez
zgadywanie nazw typów. Formularz requestu i DTO odpowiedzi pozostają odrębne.

Przykład: `addProduct` może używać formularzy opartych na
`DefaultProductDataForm`, a `getOrCreatePricingOrders` zwracać złożone DTO.
Import obu operacji nie potwierdza, że formularze w źródle jednoznacznie
rozróżniają wartości `type`. Kontrakt powinien zawierać ograniczenia wariantów
zgodne z rzeczywistym API.

Opisuj potwierdzone skutki w źródle przez `x-tdw-operation-effect`:
`READ_ONLY`, `MUTATION` albo `UNKNOWN`. Domyślne UNKNOWN jest traktowane jak
potencjalny zapis, również dla GET. Po nieustalonym wyniku można wykonywać
tylko potwierdzone odczyty. Aplikacja nie ponawia operacji, ale transport JDK
może ponowić GET po niektórych zerwaniach połączenia, także GET UNKNOWN.
POST/PUT/PATCH/DELETE nie są ponawiane.

Limit liczby operacji pozostaje 60 na kontrakt i 100 łącznie. Kontrakt może
mieć do 1000000 znaków, ale projekcja argumentów pojedynczego narzędzia do
250000, a wszystkich do 1000000. Schematy odpowiedzi nie powiększają
argumentów narzędzia. Rekurencyjne requesty nie są obsługiwane; rekursja
w dokumentacji odpowiedzi jest zachowywana. Pełne ograniczenia opisuje
[runtime](architecture/system-assistant-runtime-flow.md#importowany-podzbiór-openapi).

## Profile

W zakładce `Profile` dodaj profil, nadaj nazwę i przypisz istniejące API oraz
skille checkboxami. Domyślny `Wzorzec URL` to `*`. Możesz wpisać np.
`https://crm.example.com/customers/*`, aby profil pojawiał się tylko na
pasujących stronach w Browser Tools. Gwiazdka oznacza dowolny ciąg; pozostałe
znaki są literalne. Dopasowanie obejmuje origin i ścieżkę, bez query/hash.
Pełny ekran TDW zawsze pokazuje wszystkie profile.

Kliknij `Zapisz`, a w chacie wybierz profil obok modelu i reasoningu.
Przełączenie zachowuje rozmowę i jej kontekst. Kolejna tura używa bieżących
treści przypisanych zasobów; rozpoczęta kończy pracę na swoim zestawie.
Wybór profilu nie jest zapisywany w historii — po ponownym wejściu wybierz
profil do następnej wiadomości. Nie ma automatycznego wyboru pierwszego.

Usunięte API lub skill nie blokuje profilu. W edytorze zobaczysz ikonę
ostrzeżenia z tooltipem zawierającym brakujące ID. Możesz usunąć takie
przypisanie; pozostałe zasoby nadal działają. Profil bez dostępnych zasobów
pozwala rozmawiać bez narzędzi. Import/eksport konfiguracji obejmuje również
profile; bieżący format V1 wymaga list profiles i authProfiles oraz apiBindings z apiId/authProfileId w każdym profilu i nie importuje starego kształtu.

## Uwierzytelnianie API

W zakładce Auth wybierz Dodaj uwierzytelnianie, nazwij konfigurację i wybierz
Basic, Bearer, OAuth2 albo OIDC. Formularze są na stronie. Dane dostępowe
środowisk testowych są widoczne, bez maskowania, i zapisywane w tym samym
JSON-ie co kontrakty, skille i profile. Import/eksport całego zestawu obejmuje
również te pola.

Basic wymaga loginu i hasła; Bearer samego tokenu, bez prefiksu Bearer.
OAuth2 oznacza konto techniczne Client Credentials: podaj token URL,
client ID, client secret, opcjonalne scopes i sposób przekazania sekretu.
Backend sam uzyskuje i ponownie pobiera token po wygaśnięciu.

OIDC oznacza logowanie użytkownika. Podaj issuer URL, client ID, scopes
zawierające openid i adres powrotu TDW widoczny w formularzu. Dla klienta
publicznego pozostaw sekret pusty i metodę none; klient poufny wymaga sekretu.
Zarejestruj identyczny redirect URI u dostawcy, zapisz konfigurację i kliknij
Zaloguj. Hasło wpisujesz na stronie dostawcy, np. Keycloak. Po powrocie
zakładka Auth pokazuje stan połączenia. Rozłącz usuwa lokalne połączenie,
nie wylogowuje globalnie z SSO. Po restarcie TDW zaloguj się ponownie.

W Profilach zaznacz API i wybierz dla niego konfigurację w selekcie
Uwierzytelnianie. Nowe przypisanie zawsze ma No Auth. Ten sam kontrakt
może mieć różne Auth w różnych profilach. Zapisz cały zestaw. Usunięcie
używanego Auth wymaga wcześniejszej zmiany zależnych par w profilach.
Zewnętrzny klient MCP wybiera zestaw API i tożsamości przez adres
`/api/openapi-tools/{profileId}/mcp`. Adres wynika z ID profilu i nie jest
prezentowany w formularzu. Nieskopowany adres nie jest obsługiwany.
Niezapisany szkic konfiguracji nie zmienia wykonywanych requestów i blokuje
ręczny login w edytorze Auth. Login z rozmowy używa aktywnego zapisu.
Błąd auth nie ponawia automatycznie operacji biznesowej.
Jeśli OIDC wymaga ponownego logowania, wysłanie wiadomości najpierw próbuje
odnowić token. Gdy to niemożliwe, TDW przekierowuje do dostawcy bez uruchamiania
AI. Po logowaniu wrócisz do tej samej rozmowy i profilu. Wpisz wiadomość
ponownie — szkic nie jest zachowywany ani automatycznie wysyłany. Próba działa
też w ramce: returnPath odtwarza kompaktowy widok tej samej rozmowy i profil,
bez przejścia na pełny TDW. Na czas logowania ramka przechowuje tylko kontekst
strony do filtra profili (sessionStorage, 5 min), bez wiadomości i tokenów.
Cookie logowania ma SameSite=None; Secure. Potrzebne są HTTPS (z wyjątkami
przeglądarki dla localhost) i zgoda na third-party cookies; provider musi
pozwalać na iframe. Zabezpieczenia ramki pozostają bez zmian.
Szczegóły: [runtime Auth](architecture/http-api-authentication.md).

## Rozmowa i Browser Tools

Zakładka `Rozmowa` pozwala wybrać model AI i rozpocząć chat. Instrukcje mogą
określać sposób odczytu danych, wykonywania obliczeń i potwierdzania zmian.
Rozmowy są trwale zapisywane w historii. Kontynuacja zachowuje wcześniejszy
kontekst, ale korzysta z bieżących skilli i narzędzi.
Z `Platform / Analysis History` otwórz zapis, aby wrócić do zakładki Rozmowa.
`Nowa rozmowa` pozostawia poprzednią w historii. Usuwanie jest osobną akcją
i pozostaje zablokowane podczas aktywnej tury.

W zakładce `Uruchamianie w systemie` przeciągnij link `TDW Browser Tools`
na pasek zakładek Chrome. Na stronie
źródłowej wybierz kafelek `System Assistant`. Panel umożliwia wyszukanie
i kontynuowanie wcześniejszej rozmowy lub rozpoczęcie nowej bez otwierania
pełnego TDW. Ikona historii pozwala wrócić do listy rozmów.
Zamknięcie i ponowne otwarcie panelu zachowuje rozmowę i szkic wiadomości.
Lewą krawędź można przeciągać; ikona obok zamknięcia maksymalizuje panel.
W panelu nie pokazujemy toku działania AI ani kosztów. Na pełnym ekranie
TDW są dostępne we wspólnym panelu szczegółów. Enter wysyła wiadomość,
a Shift+Enter dodaje nową linię.

Nowa rozmowa otrzymuje origin, pathname, tytuł i czas otwarcia strony.
Nie przekazuje query/hash, formularzy, cookies ani autentykacji systemu
źródłowego. CSP lub polityka osadzania może zablokować ramkę; panel oferuje
ponowienie i link do pełnego TDW.
Wybranie wcześniejszej rozmowy zachowuje kontekst przekazany przy jej
utworzeniu, również gdy otwierasz ją z innej strony. Nawigacja w aplikacji
nie przepina automatycznie kontekstu chatu.

## Zapis i ograniczenia

Wymagany jest włączony lokalny workspace TDW. Aktualna konfiguracja jest
zapisana w `${tdw.workspace.directory}/system-assistant/configuration.json`,
a transkrypty w `${tdw.workspace.directory}/runs/{id}/run.json` ze wspólnym
indeksem historii. Skille i kontrakty nie mają rewizji ani archiwum;
historia rozmowy nie przywraca dawnej konfiguracji.

Po restarcie można kontynuować rozmowę. Tura przerwana przez restart jest
oznaczana błędem i nie jest automatycznie ponawiana. Przy nieustalonym
wyniku operacji sprawdź stan systemu przed ponownym zleceniem zapisu.
Błąd odświeżania chatu pozwala ponowić odczyt, bez ponawiania polecenia.

Feature obsługuje Basic, Bearer, OAuth2 Client Credentials i OIDC. Nie przejmuje
logowania ze strony i nie obsługuje załączników. Skille są
instrukcjami pracy, nie mechanizmem nadawania uprawnień. Importer obsługuje
wybrane konstrukcje OpenAPI, w tym readOnly, format i nullable;
pełna macierz i limity są w [runtime flow](architecture/system-assistant-runtime-flow.md#importowany-podzbiór-openapi).

## Testy i wyłączenie

Kontrakty CRM do testów automatycznych znajdują się w
`src/test/resources/openapi/{crm-api,contacts-api}.yaml`. Testy uruchamiają
własne izolowane serwery HTTP i nie wymagają osobnej aplikacji demo.
Test z rzeczywistym Copilotem jest uruchamiany tylko jawnie:

```powershell
$env:SYSTEM_ASSISTANT_LIVE_TEST='true'
mvn -q '-Dtest=SystemAssistantCopilotLiveTest' test
Remove-Item Env:SYSTEM_ASSISTANT_LIVE_TEST
```

Ten test wymaga istniejącego PAT, korzysta z fikcyjnych danych i izolowanego
katalogu CLI; nie zastępuje konfiguracji asystenta operatora.

Wyłączenie wymaga ponownego uruchomienia TDW z
`--system-assistant.enabled=false`. API i MCP asystenta zwracają 404,
a nawigacja i nowo załadowany Browser Tools ukrywają akcję. Bieżąca
konfiguracja pozostaje na dysku. Wyłączenie nie wycofuje zewnętrznych zapisów.
Kontrakty API, MCP, obsługiwaną składnię i limity opisuje
[runtime flow](architecture/system-assistant-runtime-flow.md).
