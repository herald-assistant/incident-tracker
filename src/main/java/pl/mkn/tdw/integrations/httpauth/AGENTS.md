# Uwierzytelnianie wychodzących API

Neutralna capability HTTP Auth, niezależna od feature'ów, MCP i Copilota.
Root zawiera tylko HttpAuthenticationPort i OidcLoginPort. Publiczne modele
są w contract, transport w adapter.rest, logika/cache w service.

Definicję profilu przekazuje konsument. Nie odczytuj konfiguracji System
Assistant ani localworkspace. Dane formularzy należą do konsumenta;
access/refresh tokeny i pending logowania pozostają w pamięci tej integracji.
Zmiana pełnej definicji nie używa tokenów starego profilu. Nie loguj wartości
poświadczeń, kodów, verifier ani odpowiedzi token endpoint.

OIDC używa Authorization Code, PKCE S256, jednorazowego state, nonce,
browser binding, TTL oraz walidacji issuer/audience/podpisu/ważności.
Konsument posiada adresy swoich callbacków i powrotu do UI. Integracja
nie zna nazw tras TDW. Brak tokenu daje jawny błąd; nie wykonuje biznesowego
requestu, retry ani przejmowania cookies aplikacji źródłowej.

Klienci protokołu stosują wspólną flagę integrations.http.ignore-ssl-errors
przez IntegrationRestClientBuilderFactory, także discovery, JWKS i wszystkie
wymiany tokenów. Domyślne false weryfikuje cert i hostname; true pomija te
kontrole lokalnie dla klienta, bez zmiany ustawień TLS JVM. Limity 3/8 s,
brak redirectów oraz walidacja JWT, issuer, audience, nonce i PKCE pozostają.
Oba tryby używają JDK HttpClient; true ma lokalny X509ExtendedTrustManager.
JDK może ponawiać odczyty GET po zerwaniu połączenia. Nie ponawiaj token POST.
Nie ma zależności Apache ani globalnego przypięcia fabryki Springa. Zmiana flagi wymaga restartu; polityka przeglądarki jest odrębna.
Porty i kontrakty są jedyną publiczną granicą dla konsumentów. Nowe capability
obejmij IntegrationPackageBoundaryTest i testami lokalnego dostawcy.

Kontrakt: docs/architecture/http-api-authentication.md.
