# Uwierzytelnianie wychodzących wywołań API

## Ownership

`integrations.httpauth` jest neutralną capability Basic, Bearer, OAuth2
Client Credentials i OIDC Authorization Code z PKCE. Root zawiera
`HttpAuthenticationPort` i `OidcLoginPort`. Modele są w `contract`, transport
Spring OAuth2 Client / REST w `adapter.rest`, a cache i orkiestracja w `service`.
Integracja nie zna feature'a, MCP, Copilota ani lokalnego storage.

Pierwszym konsumentem jest System Assistant. Feature posiada katalog Auth,
walidację referencji, trwały zapis konfiguracji i operatorskie HTTP API.
`integrations.openapi` wykonuje request z jawnie przekazanym profilem;
`agenttools.openapi` przechowuje `OpenApiExecution` łączący operację
i profil poza input schema narzędzia. Neutralny SDK i osobny MCP korzystają
z tego samego wykonawcy. Globalny runtime Copilota pozostaje niezależny.

## Konfiguracja i zapis

Aktualny V1 ma `{apis, skills, profiles, authProfiles}`. API zawiera tylko
`id`, `name`, `yaml`. Profil zawiera `apiBindings` z parami `{apiId, authProfileId}`;
null oznacza No Auth. Nowe przypisanie API w profilu otrzymuje null. Jeden
kontrakt może być użyty w wielu profilach z różnymi poświadczeniami, bez kopii
YAML-a. Import całej konfiguracji zachowuje pary. Brak wskazanego Auth blokuje
zapis; nie ma fallbacku do No Auth. Brakujące API jest nadal pomijane.

`authProfiles` to maksymalnie 20 nazwanych obiektów. Każdy ma dokładne pola
`id`, `name`, `type`, `username`, `password`, `token`, `tokenUrl`, `clientId`,
`clientSecret`, `scopes`, `issuerUrl`, `redirectUri`, `clientAuthentication`.
Pola nieużywane mają null. ID jest stabilnym identyfikatorem, nie rewizją.

| type | Ustawienia |
| --- | --- |
| BASIC | username bez dwukropka, password |
| BEARER | token bez prefiksu Bearer i spacji |
| OAUTH2 | tokenUrl, clientId, clientSecret, opcjonalne scopes; client_secret_basic albo client_secret_post |
| OIDC | issuerUrl, clientId, scopes zawierające openid, redirectUri do callbacku TDW; klient publiczny: brak sekretu i none; klient poufny: sekret i Basic/post |

Dane z formularza są jawnie zapisywane w istniejącym
`${tdw.workspace.directory}/system-assistant/configuration.json`, bez
szyfrowania i maskowania, zgodnie z decyzją operatora dla publicznych danych
dostępowych środowisk testowych. GET oraz import/export konfiguracji obejmują
te wartości. Nie ma osobnego sejfu ani bazy. Obowiązuje wyłącznie aktualny
kształt V1, bez migratora i odczytu poprzedniego formatu.

Konfiguracja i katalog publikowane są jako jedna decyzja z rollbackiem
nieudanego zapisu. Snapshot przyjętej tury zachowuje własne przypisania
i definicje. Kolejna tura i aktualny katalog MCP korzystają z bieżącego zapisu.
Profil System Assistant wskazuje pary API–Auth i skille. Chat wybiera profil;
nie wybiera poświadczeń dla poszczególnych wywołań. MCP ma oddzielny endpoint
`/api/openapi-tools/{profileId}/mcp` dla każdego profilu, z izolacją transportu,
sesji i katalogu. Adres wynika z ID profilu; UI go nie prezentuje. Nie istnieje
nieskopowany endpoint ani automatyczny wybór profilu. Usunięcie profilu
wyłącza jego endpoint i sesje; edycja zachowanego profilu aktualizuje katalog.

## Wykonanie

No Auth nie dodaje Authorization. Basic koduje username/password, Bearer
używa wprowadzonego tokenu. OAuth2 uzyskuje access token przez
client_credentials i cache'uje go do chwili bliskiej wygaśnięcia (15 s zapasu).
Cache jest pamięciowy, ograniczony do 128 definicji, a kluczem jest pełny
niezmienny profil, co izoluje także kolejne edycje tego samego ID.

Nagłówek jest dodawany wyłącznie do konkretnego requestu. Nie ma defaultowego
Authorization na współdzielonym builderze ani przekazywania go do innego API.
Docelowy URL wynika z operacji OpenAPI, nie z argumentów AI. Security metadata
w YAML dokumentują wymagania; nie wybierają ani nie zmieniają profilu operatora.
Importer obsługuje http basic/bearer, oauth2 i openIdConnect, waliduje nazwy
wymagań security, a selekcja domyka używane securitySchemes.
Podgląd Swagger pozostaje read-only i ukrywa własne przyciski autoryzacji;
poświadczenia konfiguruje się wyłącznie w formularzach zakładki Auth.

Brak zalogowania lub błąd token endpoint zwraca `AUTH_REQUIRED` bez wysłania
requestu biznesowego. HTTP 401/403 pozostają odpowiedzią źródłowego API.
Nie ma automatycznego retry requestu po błędzie auth ani po timeout.
Odnowienie tokenu następuje przed requestem. Redirecty API i klienta protokołu
są wyłączone, a TLS jest weryfikowany. Connect/read timeout wynosi 3/8 s.

Wartości z katalogu Auth nie trafiają do promptu, input schema, tool evidence,
transkryptu ani eksportu rozmowy. Operatorski eksport konfiguracji jest
odrębnym, jawnym formatem. Nie przechwytujemy cookies strony Browser Tools.

## OIDC

Każdy wpis Auth o typie OIDC ma własne połączenie operatora workspace,
dostępne dla przypisanych API, również przez MCP. Różne ID Auth pozwalają
zalogować różnych użytkowników u tego samego dostawcy. Wspólny wpis Auth
w kilku profilach celowo oznacza wspólne połączenie. Nie jest to model wielu
kont TDW ani logowanie do TDW.

Operator zapisuje konfigurację i wybiera Zaloguj w pełnym ekranie. Backend
odkrywa endpointy z issuer URL, sprawdza zgodność issuer i buduje request
Authorization Code z PKCE S256 i nonce. Przeglądarka przechodzi do dostawcy;
użytkownik podaje tam swoje hasło. Klient musi być zarejestrowany u dostawcy
z dokładnym redirect URI i uprawnieniami docelowego API.

Rozpoczęte logowanie ma jednorazowy state, TTL 5 min i jest powiązane
z losowym cookie `tdw_oidc_browser` (HttpOnly, SameSite=Lax, Secure dla HTTPS),
zapisanym profilem i dokładnym callback URI. Zmiana konfiguracji, inna
przeglądarka, wygaśnięcie lub ponowny callback blokują wymianę kodu.
Pending logowania są ograniczone do 100. Callback sprawdza podpis JWT,
issuer, audience, ważność, subject i nonce ID tokenu. Do API wysyłany jest
access token, nigdy ID token.

Access/refresh tokeny, podpisany ID token i verifier nie są zapisywane na
dysku. Refresh używa otrzymanego refresh tokenu, zachowując tożsamość
użytkownika. Błąd refresh lub brak możliwości odnowienia wymaga ponownego
logowania. Restart usuwa połączenia i pending logowania. Rozłącz czyści
lokalny stan danego profilu i jego rozpoczęte logowania; nie wykonuje
globalnego wylogowania SSO ani cofnięcia operacji biznesowych.

| Endpoint | Znaczenie |
| --- | --- |
| GET /api/system-assistant/auth/status | Stan połączeń zapisanych profili OIDC, bez tokenów, no-store |
| POST /api/system-assistant/auth/{id}/login | JSON, zwraca authorizationUrl i cookie; wymaga callbacku w tym samym originie TDW |
| GET /api/system-assistant/auth/callback | Kod/state lub błąd dostawcy; 303 do /system-assistant?auth=success albo failed, bez tokenów i treści błędu dostawcy |
| POST /api/system-assistant/auth/{id}/disconnect | JSON, usuwa lokalne połączenie |

Endpointy są dostępne tylko po włączeniu System Assistant. Dev proxy
zachowuje oryginalny Host dla `/api/system-assistant/auth`. Reverse proxy
musi przekazać prawidłowy zewnętrzny host/protokół do Springa, aby callback
odpowiadał zarejestrowanemu redirect URI.

## UX i weryfikacja

Zakładka Auth używa formularzy inline i wspólnych kontrolek. Usunięcie
używanego Auth wymaga wcześniejszej zmiany lub odpięcia par API–Auth w profilach. Zmiana typu czyści pola
poprzedniego typu, zachowując nazwę/ID. Zaloguj/Rozłącz korzystają z zapisu,
a niezapisany szkic blokuje te akcje. Powrót OIDC wybiera Auth bez
zapamiętywania zakładki w storage. Stan połączenia i konfiguracja są oddzielne.

Testy: `SystemAssistantAuthConfigurationTest`, `SystemAssistantAuthControllerTest`,
`OpenApiAuthenticationTest`, `OidcLoginTest`, SDK/snapshot i MCP wire,
architektura oraz frontendowe Auth/page/import-export. Kontrolowany dostawca
OIDC weryfikuje także odrzucenia i refresh. Smoke test rzeczywistego Keycloak
wymaga dostawcy i rejestracji klienta operatora.
