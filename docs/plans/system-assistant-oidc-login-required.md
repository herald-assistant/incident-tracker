# Ponowne logowanie OIDC z rozmowy

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Użytkownik wracający do rozmowy po wygaśnięciu sesji nie powinien uruchamiać AI,
aby dopiero przez błąd narzędzia dowiedzieć się o konieczności logowania.
Zakres zatwierdzony w rozmowie: AUTH_LOGIN_REQUIRED i przekierowanie, powrót do
konkretnego chatu, bez zachowywania szkicu. Zwykłe przekierowanie próbujemy też
w ramce, bez zmiany sandbox/CSP ani dodatkowych okien.

## Proponowane rozwiązanie

Preflight feature'a korzysta z istniejącego OidcLoginPort.authorization, który
odnawia token. Brak gotowości daje 409 AUTH_LOGIN_REQUIRED z authProfileId.
Frontend wywołuje istniejący login, następnie location.assign. Backend wiąże
docelowy chat i profil z jednorazowym state i browser binding; callback zwraca
wyłącznie stałą lokalną trasę rozmowy z parametrami. Bez dowolnego returnUrl.

## Baseline i conformance delta

Zmiana L3: security/continuation HTTP i FE. Dotąd auth sprawdzano dopiero w
narzędziu, callback zawsze wybierał Auth. Teraz sprawdzenie poprzedza zapis
tury i sesję AI; tokeny nadal należą do neutralnej integracji. Nie ma zmiany
tools/MCP, SDK, transkryptu ani konfiguracji V1. Preflight dotyczy wyłącznie
unikalnych OIDC aktywnych operacji wybranego profilu, pomija brakujące API.
Wzorce: wspólny błąd HTTP i existing send/reconcile System Assistant; inne
feature'y zachowują dotychczasowe flow. Integracja nie zna tras UI.

## Zakres i konsumenci

Conversation service/controller, auth controller, OIDC refresh, feature FE,
testy konstruktorów i powrotu. Istniejący status/Auth i MCP zachowują działanie.

## Non-goals

Brak zapisu szkicu, automatycznej wysyłki po logowaniu, popupów, nowych
capability Browser Tools, obsługi błędów providera poza bieżącym callbackiem.

## Ograniczenia i ryzyka

Jeden bieżący kontrakt V1, bez aliasów i migracji. Provider lub cookies mogą
blokować login w iframe; sandbox/CSP pozostają bez zmian. Powrót otwiera pełny
widok wskazanego chatu także po próbie w ramce. Przekierowanie następuje tylko
po kontrolowanym odrzuceniu przed przyjęciem tury; inne błędy nadal wymagają
reconciliation, bez ponawiania biznesowych wywołań. Stan powrotu jest ograniczony,
pamięciowy i wygasa po 5 minutach. Rollback: cofnięcie tego slice'a bez migracji.

## Kryteria akceptacji i macierz testów

Brak tokenu / nieudany refresh nie zapisuje wiadomości i nie uruchamia AI;
poprawne odnowienie dopuszcza turę. FE przekierowuje tylko dla właściwego błędu,
nie ponawia wiadomości i nie przechowuje szkicu. Callback zachowuje chat/profil
także po odmowie, izoluje przeglądarki/state i nie umożliwia open redirect.
Testy syntetycznego dostawcy, MockMvc admission/callback, Angular send/return
i regresja wszystkich konsumentów oraz pełny build mieszany.

## Kroki

- [x] Backend: preflight, błąd i związany z state powrót; testy odrzucenia,
  poprawnego odnowienia, izolacji i callbacku.
- [x] FE: kontrolowane przekierowanie i odtworzenie chatu/profilu bez szkicu;
  testy nowej/istniejącej rozmowy, ramki i błędu logowania.
- [x] Dokumentacja bieżącego kontraktu, przegląd anonimizacji i diff,
  pełne testy Angulara, build UI i mvn -q -Pbackend-dev clean package.

## Dowody weryfikacji

`logs/verification/assistant-login-tests.log`: testy System Assistant i OIDC,
45 testów, 44 zaliczone, jeden live pominięty. Test dostawcy obejmuje brak
refresh tokenu, skuteczny refresh i jego odrzucenie. MockMvc sprawdza brak
przyjęcia tury, jednorazowy powrót, odmowę oraz izolację przeglądarek.

`logs/verification/assistant-login-frontend-tests.log`: 43 testy strony/API,
bez błędów. Nowy i istniejący chat, próba przekierowania także w embedded,
brak draftu/reconciliation/retry dla AUTH_LOGIN_REQUIRED, błąd rozpoczęcia
logowania oraz powrót po sukcesie/odmowie.

`logs/verification/assistant-login-frontend-full-retry.log`: wszystkie 950
testów FE zaliczone. Pierwsze pełne uruchomienie miało jeden timeout 5 s
w niezmienianym przewodniku Flow Explorera; powtórzenie całego zestawu z
`--runner-config=../logs/verification/oidc-vitest.mjs` ograniczyło maxWorkers
do 4, bez zmiany timeoutów, selekcji ani kodu testów.

`logs/verification/assistant-login-frontend-build.log`: produkcyjny build
zakończony poprawnie, wygenerowany bundle w static. Pobranie Google Fonts
wymagało dostępu sieciowego poza sandboxem; istniejące ostrzeżenie budżetu
stylów platform landing pozostaje niezwiązane ze zmianą.

`logs/verification/assistant-login-package.log` oraz raporty Surefire:
`mvn -q -Pbackend-dev clean package`, exit 0; 2330 testów, 2324 zaliczone,
6 pominiętych, zero failures/errors, wygenerowany JAR z bieżącym UI.
Diff sprawdzony; nowe scenariusze używają wyłącznie syntetycznych danych CRM.
Rzeczywiste logowanie u dostawcy w iframe pozostaje próbą zależną od jego
polityki i cookies; test FE potwierdza location.assign, nie zgodę providera.
