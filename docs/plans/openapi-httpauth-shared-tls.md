# Jeden transport JDK i wspólna flaga TLS

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Użytkownik zatwierdził uproszczenie do JDK HttpClient w obu trybach,
usunięcie Apache i spring.http.client.factory=jdk oraz weryfikację testów.
Po rzeczywistej próbie HTTPS jawnie zaakceptował wbudowany retry GET w JDK.
Flaga integrations.http.ignore-ssl-errors ma zmieniać wyłącznie politykę TLS.

## Baseline i conformance delta

Zmiana L3 wspólnego transportu/security. Baseline: API/Auth używają JDK dla
false, Apache dla true; starsze GitLab/Jira/Confluence/Dynatrace klonują
builder Springa, przy true nadpisując go HttpURLConnection bez PATCH.
Apache wymusiło globalne przypięcie JDK dla pozostałych klientów i osobny
lifecycle pul. Poprzednia pełna regresja: 2330 testów, zero failures/errors,
6 skipped. JAR nie zawiera alternatywnego Jetty/Reactor Netty HTTP klienta.

Delta: jedna implementacja JdkClientHttpRequestFactory. False zachowuje
normalny TLS; true ma klientowy SSLContext z X509ExtendedTrustManager,
który obsługuje również kontrole silnika/socketu. Brak globalnego trust-all,
zmian hostname JVM, truststore, domyślnych SSLContext/verifier ani socket factory.
Starszy strict builder pozostaje klonem Springa (standardowy transport JDK);
tryb true korzysta z JDK, zamiast HttpsURLConnection. API/Auth zachowują
jawne 3/8 s i Redirect.NEVER. Brak dodatkowej polityki retry aplikacji;
wbudowany retry GET jest świadomie dopuszczony, również przy efekcie UNKNOWN.
POST/PUT/PATCH/DELETE nie są ponawiane.

## Proponowane rozwiązanie i konsumenci

Reuse fabryki w integrations.support.http i dotychczasowych klientów API/Auth.
Usunięcie zależności Apache, jej kodu/lifecycle, starego HttpURLConnection,
wymuszenia globalnej fabryki oraz zależnych testów szczegółów implementacji.
Konsumenci: API SDK/MCP, OAuth2 client credentials, OIDC discovery/code/refresh/
JWKS, GitLab (także named), Jira, Confluence, Dynatrace, ich testy i dokumentacja.
Neutralne integracje nadal nie zależą od feature'a, AI ani MCP.

## Zakres

Transport, TLS, timeouty/redirecty, zachowanie retry i istniejące testy.

## Non-goals

Zmiana DTO, portów serwera, Auth/config V1, UI, JWT/issuer/audience/nonce/state/
PKCE, Elasticsearch i Copilot CLI. Bez nowych flag, migracji i commit/push.

## Ograniczenia i ryzyka

JDK ponawia niektóre GET po zerwaniu połączenia. Użytkownik wybrał
„Użyj JDK, akceptuję retry GET”; nie ukrywamy tej różnicy w teście ani docs.
Pozostaje brak retry POST/PUT/PATCH/DELETE i redirectów dla API/Auth.
Java release 17 pozostaje; usuwamy natywny lifecycle Apache, bez pustych close.
Własność transportu i polityki pozostaje we wspólnej fabryce.
Rollback przez cofnięcie zmiany, bez migracji danych.

## Kryteria akceptacji i macierz testów

False odrzuca niezaufany cert i zaufany cert z niewłaściwym hostname;
trusted localhost przechodzi. True dopuszcza oba błędy i PATCH. Oba wejścia
fabryki są pokryte. HTTPS OAuth2/OIDC/JWKS/code/refresh działa; błędne JWT
nadal są odrzucane. Redirecty nie są śledzone, zapisy nie są ponawiane,
timeout jest zachowany, JVM TLS niezmienione. Małe syntetyczne fixtures CRM.
Regresja wszystkich konsumentów, architecture guards i pełny backend; JAR
nie zawiera Apache ani starych klas implementacji. Frontend bez zmian.

## Kroki

- [x] Fabryka JDK i usunięcie zależności/properties/starego transportu;
  testy TLS/PATCH, builder isolation, timeoutu, redirectów i zapisów.
- [x] Aktualizacja dokumentacji/AGENTS i testów konsumentów/lifecycle;
  weryfikacja OAuth2/OIDC i pozostałych integracji.
- [x] Czyste testy i pakowanie backendu, inspekcja JAR-a/dependency tree,
  diff/anonimizacja i zapis dowodów.

## Dowody weryfikacji

- `logs/verification/jdk-http-focused-tests.log`: 18 testów, bez błędów.
  Cert/hostname false/true, oba wejścia fabryki, PATCH, izolacja JVM, timeout,
  redirecty i pojedyncze wysłanie POST/PATCH po zerwaniu połączenia.
  OAuth2 oraz pełny OIDC discovery/JWKS/code/refresh i odrzucenie błędnego JWT;
  również przypisania GitLab default/named.
- `logs/verification/jdk-http-full-package.log`:
  `mvn -q -Pbackend-dev clean package`, exit 0; 2331 testów,
  2325 zaliczone, 6 skipped, zero failures/errors. Wszystkie integracje,
  architektura, SDK/MCP i feature'y przechodzą. Frontend niezmieniony i nie
  był ponownie budowany ani testowany w tej zmianie.
- Inspekcja JAR-a potwierdza brak httpclient/httpcore, alternatywnego klienta
  Jetty/Reactor Netty, starego InsecureRequestFactory.class oraz globalnego
  spring.http.client.factory. Port backendu nadal 4200.
- `logs/verification/jdk-http-dependencies.txt`: puste drzewo filtrowane dla
  org.apache.httpcomponents, org.apache.httpcomponents.client5 i
  org.apache.httpcomponents.core5; dependency:tree exit 0.
- Diff sprawdzony; testy zachowują syntetyczne dane CRM i testowy cert localhost.
  JDK retry GET jest świadomie opisany, bez fałszywego testu „brak retry GET”.
