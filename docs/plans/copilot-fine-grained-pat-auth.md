# Uproszczenie autoryzacji Copilota do fine-grained PAT

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Workspace uzywa lokalnego tokena Copilota w Workspace Settings. Rownolegly
GitHub App OAuth, operator session, refresh i fallback z properties podwajaja
sciezki auth oraz komplikują UI i kontynuacje runow. Operator ma miec jeden
sposob konfiguracji: fine-grained PAT zapisany w Workspace Settings.

## Proponowane rozwiazanie

Runtime pobiera token przez neutralny port z lokalnego store ustawien i podaje
go jawnie do Copilot SDK z `useLoggedInUser=false`. Token musi miec prefiks
`github_pat_`; brak albo nieprawidlowy format daje czytelny blad i odsylacz do
Workspace Settings. Status auth informuje tylko, czy PAT jest skonfigurowany.
Usuwamy GitHub OAuth, jego klientow/store, cookies, refresh oraz wybor trybu.
Zachowujemy format lokalnych runow do odczytu, ale ich kontynuacje uzywaja
aktualnego PAT z Workspace Settings. Starych tokenow OAuth nie przenosimy.

GitHub dokumentuje wsparcie `github_pat_` w Copilot SDK oraz wymaganie
uprawnienia `Copilot Requests` dla PAT konta uzytkownika:
https://docs.github.com/en/copilot/how-tos/copilot-sdk/auth/authenticate
https://docs.github.com/en/copilot/how-tos/copilot-cli/set-up-copilot-cli/install-copilot-cli

## Baseline i conformance delta

Baseline: dwa tryby `LOCAL_TOKEN`/`GITHUB_APP`; token lokalny z properties lub
Workspace Settings; OAuth API `/status`, `/start`, `/callback`, `/logout`;
zaszyfrowany store w pamieci; UI z connect/reauth/logout; runy utrwalaja
auth reference. Przed zmiana `mvn -q test` przechodzil (1738 testow, 0 bledow).

Delta: zmienia sie publiczny status auth i UX, znikaja endpointy OAuth,
konfiguracja GitHub App i fallback `COPILOT_GITHUB_TOKEN` w aplikacji. Requesty
feature'ow, wyniki, skille, tools i evidence pozostaja takie same.

Konsumenci: wszystkie feature'y i katalog modeli Copilota, local run
continuation, Analysis History, `api.githubauth`, Workspace Settings oraz
ekrany Incident Analysis, Config Drift Viewer, Delivery Complexity Assessment,
Delivery Scope Complexity, UI Explorer i UX Inspector.

## Zakres

- Jedno zrodlo tokena w `settings.json` przez Workspace Settings.
- Jeden auth status oraz wskazanie Workspace Settings przy braku PAT.
- Usuniecie OAuth i martwego wiring backendu/tekstu UI.
- Aktualizacja testow kontraktowych, architektury i dokumentacji operatora.

## Non-goals

- Zmiana Copilot SDK, polityki modeli, tools i promptow.
- Rotacja PAT, zdalna walidacja uprawnien przy zapisie lub szyfrowanie
  istniejacego lokalnego `settings.json`.

## Ograniczenia i ryzyka

- To zmiana L1 publicznego statusu i shared auth, dlatego obejmuje UI oraz
  backend. Po aktualizacji PAT nowe uruchomienia i kontynuacje uzywaja nowej
  wartosci. Historyczne runy OAuth wymagaja aktualnego PAT.
- Sam prefiks nie dowodzi waznosci ani uprawnien; GitHub weryfikuje je podczas
  wywolania SDK. Testy uzywaja calkowicie fikcyjnych danych CRM.

## Kryteria akceptacji

1. Nie istnieje aktywna sciezka GitHub App OAuth, cookie ani refresh.
2. Brak tokena lub niepoprawny format blokuje AI przed runem i prowadzi do
   Workspace Settings; status nie deklaruje falszywie gotowosci.
3. SDK dostaje aktualny PAT jawnie, bez fallbacku do loginu CLI albo env.
4. UI nie pokazuje connect/reauth/logout; pozostale runy i kontynuacje dzialaja.
5. Testy Angulara, build Angulara i `mvn -q -Pbackend-dev clean package`
   przechodza, a diff nie zawiera starych danych organizacji.

## Kroki

- [x] Krok 1: uproscic backend auth, token source, status, bledy i kontynuacje;
  usunac OAuth i dopasowac testy backendu. Dowod: testy celowane oraz compile.
- [x] Krok 2: skierowac UI do Workspace Settings, usunac OAuth affordances i
  dopasowac testy frontendu. Dowod: testy Angulara i build.
- [x] Krok 3: zaktualizowac dokumentacje i plan granic pakietow, wykonac
  wspolny build oraz kontrole diffu. Dowod: `mvn -q -Pbackend-dev clean package`,
  `git diff --check` i przeglad kontraktow. Build wykonano w izolowanej kopii
  z powodu rownoleglego czyszczenia `target` w glownym katalogu: 1719 testow,
  0 failures, 0 errors, 1 skipped; JAR zawiera aktualny bundle UI.
