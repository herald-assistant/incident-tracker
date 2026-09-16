# UX Inspector Runtime Flow

## Cel i granica feature'a

UX Inspector odpowiada na jedno pytanie o jeden element wskazany w uruchomionej
aplikacji frontendowej. Nie dokumentuje calego widoku i nie jest trybem UI
Explorera. Oba feature'y korzystaja z neutralnego katalogu frontendu,
`integrations.gitlab.frontend`, platformy Copilota i wspolnych komponentow run
UI, ale maja rozdzielne requesty, joby, prompty, polityki tools oraz kontrakty
wyniku.

Pierwsze wydanie wspiera Chrome desktop, strony `http`/`https`, top-level
dokument oraz otwarty Shadow DOM dostepny dla skryptu. Operator wybiera profil
`ELEMENT_CONTEXT` albo `FORM_DIAGNOSTICS`; drugi profil zamraza dozwolone
wartosci najblizszego formularza i jego natywny stan walidacji.

## Publiczne wejscia i kontrakty v1

- Modal na `/ux-inspector` udostepnia przeciagany bookmarklet Browser Tools.
- `GET /api/ux-inspector/input-options` zwraca zarejestrowane frontendy.
- `GET /api/ux-inspector/views?systemId=...&branch=...&refresh=...` zwraca
  widoki i immutable source revision; `refresh=true` omija cache tego scope'u.
- `POST /api/ux-inspector/jobs` tworzy run i zwraca snapshot `QUEUED`.
- `GET /api/ux-inspector/jobs/{jobId}` zwraca aktualny snapshot.
- `GET /api/ux-inspector/jobs/{jobId}/export` zwraca tylko
  `tdw.ux-inspector-export` w wersji 1 dla ukonczonego wyniku.
- `POST /api/ux-inspector/imports` przyjmuje tylko export v1 z capture v1 i
  zapisuje nowy read-only wpis historii.
- `/ux-inspector` jest jedynym receiverem capture i workspace'em feature'a.

Launcher, protocol, capture i export maja wersje 1. Nie ma aliasow, migratorow,
dual-read, recznego kanalu capture ani alternatywnego formatu uruchomienia.
Kazda inna wersja jest odrzucana.

## Przeplyw Browser Tools

```text
/ux-inspector -> modal -> przeciagany bookmarklet
  -> loader.js z originu TDW
  -> protocol.js + runtime.js
  -> Browser Tools shell w izolowanym Shadow DOM
  -> wybor profilu + selection shield + highlight
  -> jeden capture v1
  -> popup /ux-inspector#nonce=...&sourceOrigin=...
  -> READY / CAPTURE / RECEIVED przez exact-origin postMessage
  -> capture tylko w pamieci receivera
```

Bookmarklet zawiera publiczny origin TDW i adres statycznego `loader.js`, ale
nie zawiera tokenu, cookies ani klienta REST. Loader pobiera `protocol.js` i
`runtime.js` z tego samego originu. Klik w trybie selekcji jest przejmowany i
nie uruchamia natywnej akcji badanej strony.

Transfer wymaga jednoczesnie:

1. `event.origin` rownego skonfigurowanemu originowi TDW,
2. `event.source` rownego dokladnie utworzonemu oknu,
3. protokolu v1 i jednorazowego nonce,
4. potwierdzenia `captureId` w wiadomosci `RECEIVED`.

Obcy origin/source, niepoprawny nonce, replay, timeout, popup blocker albo COOP
koncza operacje bez wyslania drugiego capture. Nie powstaje zastepczy kanal.
Po sukcesie lub bledzie listenery, payload, overlay i referencja do okna sa
usuwane.

Receiver odczytuje z fragmentu tylko `nonce` i `sourceOrigin`, po czym od razu
usuwa fragment przez `history.replaceState`. Sprawdza exact message shape,
origin badanej strony, klienta, schema/version i limit 128 KiB. Capture nie
trafia do URL, storage, clipboardu ani analytics.

## Capture v1

Kanoniczny kontrakt ma `schema=tdw.ux-inspector-capture`, `version=1`, klienta
`TDW UX Inspector` i `featureId=ux-inspector`. Zawiera:

- origin oraz route bez wartosci query,
- tag, role, accessible name i ograniczony tekst targetu,
- `domFingerprint`: allowlistowane stabilne atrybuty, selector candidates,
  label linkage i lancuch custom-element boundaries,
- obserwowalny stan boolean/ARIA i bounds do preview,
- maksymalnie 24 istotnych przodkow,
- informacje o truncation, Shadow DOM, ramce i redakcji,
- `captureProfile` oraz opcjonalny `formSnapshot`.

`ELEMENT_CONTEXT` nie zawiera `formSnapshot`. `FORM_DIAGNOSTICS` odczytuje
najblizszy owning form, a gdy kontrolka nie nalezy do form - tylko wskazana
kontrolke. Snapshot przechowuje do 64 kontrolek, do 16 KiB na wartosc i lacznie
do 64 KiB znakow wartosci. Zachowuje `checked`, wybrane opcje,
disabled/readonly/required, `ValidityState`, validation message oraz submittery.
Kazde obciecie jest jawne.

Kontrolki `type=hidden` sa przechwytywane wraz z dozwolonymi wartosciami, aby
zachowac kontekst zaleznosci formularza. Hasla, pliki oraz pola lub wartosci
rozpoznane jako token, session, secret, CSRF/JWT albo jednorazowy kod sa nadal
wykluczane. Cookies, storage i ruch sieciowy nie sa odczytywane. Dozwolone
wartosci formularza sa niezaufanym runtime evidence.

Frontend receiver i backend waliduja niezaleznie ten sam kontrakt. Backend
odrzuca nieznane pola, inna wersje i payload wiekszy niz 128 KiB przed oraz po
normalizacji.

## Formularz analizy i katalog widokow

Capture jest tylko runtime observation. Application, Branch, View, source
revision, pytanie, model i reasoning effort pochodza z formularza na originie
TDW. Uklad i zachowanie tych kontrolek odpowiada UI Explorerowi:

- wybor Application ustawia domyslny Branch i czysci stary View,
- potwierdzenie Branch automatycznie laduje katalog widokow,
- katalog jest wspolnie cache'owany przez neutralny `frontendcatalog` per
  system, repository scope, ref i limity discovery,
- `Load views` wymusza odswiezenie tylko aktualnego scope'u,
- View jest zwiazany z pokazanym immutable source revision,
- Model AI i Reasoning effort sa wybierane przed wpisaniem pytania.

Backend waliduje aktualna pare model/effort z dynamicznym katalogiem Copilota.
Pierwszy snapshot `QUEUED` musi zostac zapisany w Analysis History przed
dispatch do executora; brak persistence zatrzymuje utworzenie runu.

## Deterministyczne rozpoznanie targetu

`UxInspectorTargetResolver` rozwiazuje zarejestrowany frontend do ukrytego
GitLab scope, przypina branch do oczekiwanej rewizji i buduje screen
reachability dla wybranego view. Ranking korzysta ze stabilnych atrybutow,
selector candidates, accessible name, tekstu, tagu, role, custom-element
ancestry oraz route. Dla targetu wyprowadza `sourceBinding`: komponent/symbol,
template, ograniczony element slice, bindingi elementu i formularza oraz
symbole referencyjne.

Rezultat ma jeden ze stanow:

- `RESOLVED` - jeden kandydat ma wystarczajaca przewage,
- `AMBIGUOUS` - kilku kandydatow jest porownywalnych; odpowiedz zachowuje
  niejednoznacznosc i moze uzyc target tools,
- `NOT_FOUND` - brak zweryfikowanego celu blokuje sesje.

Zmiana source revision, brak refa albo nieaktualny View sa jawnym bledem.
Feature nie przelacza sie na inny branch i nie zgaduje targetu.

## Prompt, repository map i tools

Prompt oddziela pytanie operatora, `UNTRUSTED_RUNTIME_OBSERVATION` oraz
`UNTRUSTED_SOURCE_EVIDENCE`. Zawiera capture, `sourceBinding`, target context,
focused source slice, procedure dla pytan precyzyjnych i ogolnych oraz
kontrakt raportu.

Osobny logical artifact zawiera kompletny, posortowany spis nazw sciezek z
pierwszych czterech poziomow wybranego repozytorium na pinned commit. Drzewo
jest mapa nawigacyjna, nie dowodem tresci. Brak kompletnego drzewa blokuje
przygotowanie zamiast dostarczyc modelowi cichy, obciety wynik.

Sesja ma read-only dostep do calego jednego wybranego repozytorium przez
neutralne GitLab tools do listowania, wyszukiwania i czytania plikow. Hidden
scope przypina projekt, branch oraz commit. Feature nie tworzy tooli o nazwach
specyficznych dla UX Inspectora. Model moze czytac m.in. README, `AGENTS.md`,
instrukcje repozytorium, konfiguracje, frontend i backend, ale tylko plik
rzeczywiscie odczytany na przypietym commicie moze stac sie source reference.

Report tools sa jedynym kanalem wyniku. AI przygotowuje w jednej rundzie
naglowek, jedna sekcje `answer` i metadata, a nastepnie sprawdza stan raportu.
Finalny tekst Copilota nie jest parserem ani fallbackiem.

## Wynik, historia i prezentacja

Kanoniczny wynik to `AnalysisReport` z dokladnie jedna sekcja `answer`.
Odpowiedz jest biznesowo czytelna; kod i symbole sa dowodami, nie glownym
jezykiem narracji.

Ekran pokazuje:

- wspolny aside z przebiegiem, aktywnoscia AI, tool evidence i usage,
- jedna karte runu; dla historii/importu Application, Branch, Revision, View,
  Model oraz pytanie sa drobnym kontekstem w jej stopce,
- jedna sekcje odpowiedzi,
- scalone References, Visibility limits, Gaps i Warnings tylko raz, pod
  odpowiedzia i po prawej stronie jak w UI Explorerze.

Nie ma osobnej karty read-only ani osobnej sekcji metadata raportu. Import nie
wznawia sesji AI. Export i import sa scisle ograniczone do envelope v1,
jednosekcyjnego raportu, capture v1 i spojnego source revision.

## Granice pakietow

- `features.uxinspector` posiada request/result, capture, resolver, prompt,
  tool policy, report, job i import/export.
- `frontendcatalog` posiada neutralny katalog aplikacji, widokow i cache.
- `integrations.gitlab.frontend` posiada graph discovery i source slices.
- `agenttools` oraz `aiplatform` pozostaja neutralne wobec feature'a.
- `features.uxinspector` i `features.uiexplorer` nie importuja siebie.
- `frontend/public/browser-tools` jest uniwersalnym shellem z rejestrem akcji;
  pierwsza akcja jest UX Inspector.

Kierunki zaleznosci sa egzekwowane przez `PackageDependencyGuardTest`.

## Weryfikacja wydania

Minimalna macierz obejmuje:

- oba profile capture, shape, limity, redakcje, form values i wykluczenia,
- target resolution `RESOLVED`, `AMBIGUOUS`, `NOT_FOUND` i stale revision,
- origin/source/nonce/replay/popup failure w Browser Tools i receiverze,
- model/effort, cache/refresh widokow, czteropoziomowe drzewo i tool scope,
- jednosekcyjny report oraz pojedyncza prezentacje scalonych metadata,
- `QUEUED` przed dispatch, strict import/export v1 i odrzucenie obcych wersji,
- modal bookmarkleta, brak alternatywnego launchera i brak osobnej karty
  read-only,
- testy Angulara, build produkcyjny, `FrontendPageTest` i pakiet backend-dev.

Testy automatyczne nie zastepuja kontrolowanego pilota z rzeczywistym
Copilot/GitLab dla rodzin pytan o walidacje, dane, stan i skutek akcji.
