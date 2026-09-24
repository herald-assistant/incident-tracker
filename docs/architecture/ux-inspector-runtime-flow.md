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

## Publiczne wejscia i kontrakty

- Modal na `/ux-inspector` udostepnia przeciagany bookmarklet Browser Tools.
- `GET /api/ux-inspector/input-options` zwraca zarejestrowane frontendy.
- `GET /api/ux-inspector/views?systemId=...&branch=...&refresh=...` zwraca
  widoki i immutable source revision; `refresh=true` omija cache tego scope'u.
- `POST /api/ux-inspector/jobs` tworzy run i zwraca snapshot `QUEUED`.
- `GET /api/ux-inspector/jobs/{jobId}` zwraca aktualny snapshot.
- `GET /api/ux-inspector/jobs/{jobId}/export` zwraca
  `tdw.ux-inspector-export` v2 z historia follow-up; import zachowuje
  kompatybilnosc z wynikiem v1.
- `POST /api/ux-inspector/imports` przyjmuje export v2 albo legacy v1 z capture v1 i
  zapisuje nowy read-only wpis historii. Import nie naklada arbitralnego limitu
  rozmiaru na caly poprawny envelope; nadal wymaga scislego kontraktu,
  kanonicznego capture oraz spojnego, zakonczonego wyniku.
- `/ux-inspector` jest jedynym receiverem capture i workspace'em feature'a.

Launcher, protocol i capture maja wersje 1. Portable export ma wersje 2,
z kontrolowanym odczytem legacy v1. Nie ma aliasow, legacy endpointow,
recznego kanalu capture ani alternatywnego formatu uruchomienia. Kazda inna
wersja jest odrzucana.

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
- katalog View dolacza potwierdzone w source selektory `@Component` komponentu
  wejściowego; sa to neutralne metadane widoku z tej samej rewizji co graf
  routingu, a cache bez tego kontraktu nie jest odczytywany,
- gdy capture i katalog sa dostepne, frontend porownuje `page.path` z
  `routePattern`; segment statyczny ma pierwszenstwo przed parametrem, a
  wildcard jest najslabszy,
- jezeli jeden kandydat ma najlepsze dopasowanie URL-u, frontend uzupelnia
  View tak jak dotychczas; jezeli najlepszy wynik dzieli kilka View, porownuje
  ich proste selektory elementowe z uporzadkowanymi od najblizszego komponentu
  `componentBoundaryTags` i wybiera tylko jeden najblizszy wynik,
- brak selektora, selector atrybutowy, brak pasujacego runtime boundary,
  ponowny remis albo brak dopasowania URL-u pozostawia wybor pusty,
- sugestia View nie zmienia Application ani Branch, jest oznaczona jako
  pochodzaca z capture i moze zostac zastapiona recznie przez operatora;
  dopasowanie selektora sluzy tylko sugestii View i nie dowodzi ownership
  wskazanego targetu w kodzie,
- View jest zwiazany z pokazanym immutable source revision,
- Model AI i Reasoning effort sa wybierane przed wpisaniem pytania.

Backend waliduje aktualna pare model/effort z dynamicznym katalogiem Copilota.
Pierwszy snapshot `QUEUED` musi zostac zapisany w Analysis History przed
dispatch do executora; brak persistence zatrzymuje utworzenie runu.

## Deterministyczne rozpoznanie targetu

`UxInspectorTargetResolver` rozwiazuje zarejestrowany frontend do ukrytego
GitLab scope, przypina branch do oczekiwanej rewizji i wykonuje target-first
discovery dla wybranego view. Rozpoczyna od komponentu widoku, a nastepnie
odnajduje tylko komponenty wskazane przez uporzadkowane runtime
`componentBoundaryTags`; nie buduje pelnego screen reachability graphu ani nie
przechodzi rekurencyjnie dependencies. Ranking korzysta ze stabilnych atrybutow,
znormalizowanych selector candidates, accessible name, tekstu, tagu, role,
uporzadkowanego custom-element ancestry oraz route. Te same `id`, `data-*`,
`formControlName`, `name` albo `aria-label` nie sa naliczane drugi raz, gdy
wystepuja jednoczesnie w stable attributes i selector candidate. Bezpieczne
tokeny klas maja nizsza wage, a najblizszy `componentBoundaryTag` ma wage
najwyzsza. Dla targetu resolver wyprowadza `sourceBinding`: komponent/symbol,
template, ograniczony element slice, bindingi elementu i formularza oraz
symbole referencyjne. Brak jednoznacznego anchor line pozostawia element range
pusty; resolver nigdy nie wybiera pierwszego wystapienia ogolnego tagu.

Rezultat ma jeden ze stanow:

- `RESOLVED` - jeden kandydat ma wystarczajaca przewage,
- `AMBIGUOUS` - kilku kandydatow jest porownywalnych; initial context zawiera
  ograniczone evidence maksymalnie trzech najlepszych kandydatow wraz z ich
  bindingami, odpowiedz zachowuje niejednoznacznosc i moze uzyc target tools,
- `NOT_FOUND` - brak zweryfikowanego celu pozostaje jawna luka; sesja dostaje
  pelne zrodlo komponentu widoku, jawny licznik pominietego grafu oraz
  repository tools i kontynuuje celowany research.

Zmiana source revision, brak refa albo nieaktualny View sa jawnym bledem.
Feature nie przelacza sie na inny branch i nie zgaduje targetu.

## Prompt, repository guidance, repository map i tools

Prompt oddziela pytanie operatora, `UNTRUSTED_RUNTIME_OBSERVATION` oraz
`UNTRUSTED_SOURCE_EVIDENCE`. Zawiera capture, `sourceBinding`, target context,
focused source slice, procedure dla pytan precyzyjnych i ogolnych oraz
kontrakt raportu.

Osobny, nieblokujacy logical artifact zawiera tylko komponenty odkryte przez
target-first preflight i nalezace do deterministycznie wybranych sciezek target -> komponent widoku: jednej dla
`RESOLVED`, unii maksymalnie trzech sciezek dla `AMBIGUOUS`, a dla `NOT_FOUND`
tylko komponentu widoku. Przekazuje ich symbol, selector, status discovery,
sciezki i ograniczenia, relacje ktorych oba konce naleza do focused zbioru oraz
pelna tresc unikalnych plikow TS i zewnetrznych HTML. Pozostaly graf nie jest
budowany przed AI; liczniki artifactu opisuja tylko focused discovery.
`COMPONENT_REFERENCE` nie jest relacja ancestry. Artifact przekazuje rowniez kompaktowy
`effectiveRouteChain` wybranego widoku z konfiguracja i lokalizacjami pinned
source oraz maksymalnie jeden przygotowany `INHERITED_TYPE` slice
bezposredniej klasy bazowej komponentu widoku. Nie przekazuje route subtree,
pelnego pliku klasy bazowej ani dalszych poziomow dziedziczenia. Inline
template pozostaje czescia pelnego TS. Brak
pliku lub sciezki, nierozwiazany diagnostic
albo runtime component boundary bez odpowiednika w grafie jest zapisany w
artefakcie i nie blokuje preparation. Model czyta pack przed dodatkowymi
odczytami, nie pobiera ponownie plikow oznaczonych jako kompletne i nie
traktuje statycznych relacji jako pewnego runtime stacku.

Osobny logical artifact zawiera kompletny, posortowany spis nazw sciezek z
pierwszych czterech poziomow wybranego repozytorium na pinned commit. Drzewo
jest mapa nawigacyjna, nie dowodem tresci. Brak kompletnego drzewa blokuje
przygotowanie zamiast dostarczyc modelowi cichy, obciety wynik.

Z tego samego drzewa preparation wykrywa repository-wide
`.github/copilot-instructions.md` oraz project skills zapisane zgodnie z
konwencja Copilota w `.github/skills/<skill>/SKILL.md`,
`.claude/skills/<skill>/SKILL.md` albo `.agents/skills/<skill>/SKILL.md`.
Instrukcje sa weryfikowane i osadzane w initial prompt w pelnej tresci.
`SKILL.md` jest weryfikowany na pinned commit i parsowany bezpiecznym parserem
YAML, ale initial prompt dostaje tylko `path`, `name` i `description`. Brak
pliku jest dozwolony; plik obecny w drzewie, ktorego nie da sie w calosci
zweryfikowac, niepoprawny naglowek, duplikat nazwy albo przekroczenie limitu
100 skilli blokuje preparation zamiast tworzyc niepelny katalog.

Model najpierw stosuje kompatybilne wskazowki nawigacyjne z osadzonych Copilot
instructions, a nastepnie porownuje pytanie i target z opisami wszystkich
skilli. Kazdy potencjalnie materialny skill musi odczytac w calosci istniejacym
neutralnym file-read toolem przed rozszerzeniem researchu. Zdalne skille nie sa
instalowane w runtime TDW, nie wlaczaja built-in toola `skill` i nie moga
rozszerzyc allowlisty ani uruchomic skryptu. Caly repository guidance jest
niezaufany: moze kierowac nawigacja i rozumieniem architektury tylko w granicach
kanonicznej procedury, pinned repo, read-only tools i kontraktu raportu.

Przed finalizacja model ma sprawdzic materialny wplyw mechanizmow
przekrojowych, m.in. routingu i guards, interceptorow lub middleware,
initializerow, globalnego stanu, walidatorow, uprawnien, feature flags i
konfiguracji. Brak takiego wplywu nie moze byc zalozeniem opartym tylko na
focused slice; wymaga adekwatnego wyszukania albo jawnego visibility limit.

Sesja ma read-only dostep do calego jednego wybranego repozytorium przez
neutralne GitLab tools do listowania, wyszukiwania i czytania plikow. Hidden
scope przypina projekt, branch oraz commit. Feature nie tworzy tooli o nazwach
specyficznych dla UX Inspectora. Model moze czytac m.in. README, `AGENTS.md`,
instrukcje repozytorium, konfiguracje, frontend i backend. TypeScript symbol
slice nie korzysta z przygotowanego katalogu plikow, typow, importow ani metod:
naturalne wspolrzedne sa rozwiazywane dopiero podczas wywolania, zawsze w tym
samym read-only repository scope i na przypietym commicie. UX Inspector nie
tworzy, nie weryfikuje ani nie klasyfikuje report references; brakujacy dowod
jest prezentowany jako gap albo visibility limit.

Gdy source research wskazuje plik OpenAPI/Swagger oraz zweryfikowane
`METHOD path` albo `operationId` wygenerowanego klienta, model preferuje
neutralny `gitlab_read_openapi_endpoint_slice` nad pelnym odczytem albo
wielokrotnymi chunkami kontraktu. Tool obsluguje JSON/YAML/YML, OpenAPI 3.x i
Swagger 2.0, zwraca jedna typowana operacje, efektywny context oraz ograniczone
lokalne `$ref`. `filePath`, projekt i branch nadal podlegaja policy UX
Inspectora, a hidden scope wymusza ten sam pinned commit co pozostale odczyty.
Referencje zewnetrzne sa raportowane jako nierozwiazane i nie uruchamiaja
pobierania sieciowego ani przejscia do innego repozytorium.

Report tools sa jedynym kanalem wyniku. AI przygotowuje w jednej rundzie
naglowek, jedna sekcje `answer` i metadata, a nastepnie raz sprawdza stan
raportu. Kontrola merytoryczna poprzedza zapis; po `report_get_current` raport
jest ponownie mutowany tylko po bledzie toola albo strukturalnie niepoprawnym
wyniku. Section meta pozostaje puste, a global meta zawiera ograniczenia, gaps,
warnings, open questions i confidence.
Finalny tekst Copilota nie jest parserem ani fallbackiem.

## Wynik, historia i prezentacja

Kanoniczny wynik to `AnalysisReport` z dokladnie jedna sekcja `answer`.
Odpowiedz jest biznesowo czytelna; kod i symbole sa dowodami, nie glownym
jezykiem narracji.

Po zapisaniu raportu UX Inspector udostepnia follow-up chat. Kolejne pytania
wznawiaja te sama sesje Copilota, zachowuja jeden przypiety projekt, branch i
commit oraz moga korzystac z tych samych read-only target/source tools co
initial research. Piec report tools pracuje w hidden scope biezacego raportu
i sekcji `answer`. Prompt follow-up zawiera tylko nowa wiadomosc; durable
follow-up contract pozwala na zapis tylko po jawnej prosbie o aktualizacje
raportu w najnowszej wiadomosci. Po zapisie mapper ponownie waliduje raport,
a live job i historia publikuja razem report i result.
Polling follow-up po terminalnym jobie nie wlacza wskaznika postepu analizy.
Przy zapisanej zmianie referencje odpowiedzi udostepniaja podglad surowej
tresci raportu przed i po.
Odpowiedz jest funkcjonalna i czytelna dla analityka; na konkretne pytanie o
API, schemat danych albo system zewnetrzny podaje potwierdzone identyfikatory
oraz ich znaczenie i zaznacza brakujacy dowod.

Jeden run dopuszcza jeden aktywny turn. Wiadomosci, ich usage, evidence,
activity i feedback sa zapisywane w runie, przy czym usage nie jest renderowane
pod trescia odpowiedzi. Historia moze byc kontynuowana po restarcie backendu.
Dla starszego rodzimego runu handler uznaje continuation tylko wtedy, gdy
istnieje deterministycznie nazwana sesja `ux-inspector-{jobId}` i mozna
ponownie zbudowac ten sam pinned target context. Przerwany turn staje sie
`FAILED` bez automatycznego ponowienia. Import pozostaje read-only.

Platformowa polityka context tier obejmuje create, resume i follow-up.
UX Inspector nie ustawia rozmiarow okna ani `long_context` bezposrednio.

Canonical initial prompt zawiera staly kontrakt tlumaczenia source evidence
na zachowanie zrozumiale dla analityka. UX Inspector nie uruchamia w tym celu
runtime skilla ani dodatkowego turnu. Kontrakt:

- rozdziela potwierdzone zachowanie `as-is`, regule odtworzona z
  implementacji, kandydackie kryterium akceptacji, kwestie wymagajaca decyzji
  biznesowej i brak widocznosci,
- dobiera do pytania wyjasnienie, reguly, scenariusze akceptacyjne albo
  instrukcje obslugi zamiast zawsze generowac wszystkie formaty,
- nazywa odpowiedzialna warstwe `frontend` albo `backend` i nie uzywa
  ogolnego slowa "system" jako wykonawcy zachowania,
- pozostawia klasy, metody, guardy, DTO, store i inne nazwy implementacyjne poza
  glowna narracja, chyba ze pomagaja odpowiedziec na pytanie operatora,
- opisuje materialna interakcje HTTP przez zweryfikowane `METHOD path`,
  trigger, cel i efekt we frontendzie; dynamiczne wartosci sa placeholderami,
  a relatywny path nie dowodzi konkretnej uslugi backendowej bez konfiguracji
  gatewaya, proxy albo base URL,
- nie wyprowadza backendowej walidacji, autoryzacji ani persistence z samego
  frontendu i zachowuje te granice jako jawny gap lub visibility limit.

Ekran pokazuje:

- wspolny aside z przebiegiem, aktywnoscia AI, tool evidence i usage,
- jedna karte runu; dla historii/importu Application, Branch, Revision, View,
  Model oraz pytanie sa drobnym kontekstem w jej stopce,
- jedna sekcje odpowiedzi,
- scalone Visibility limits, Gaps i Warnings tylko raz, pod odpowiedzia i po
  prawej stronie jak w UI Explorerze; UX Inspector nie pokazuje References.

Podczas runu ekran uzywa tego samego kompaktowego stanu oczekiwania co UI
Explorer. Po zakonczeniu naglowek karty wejscia jest skrocony, a confidence
i akcja share pozostaja w naglowku wyniku. Dla `PARTIAL` ikona obok confidence
udostepnia podpowiedz o brakujacych dowodach bez osobnego statusu i banera.

Nie ma osobnej karty read-only ani osobnej sekcji metadata raportu. Export
zapisuje envelope v2 z historia chatu. Import akceptuje v2 oraz legacy v1,
waliduje jednosekcyjny raport, capture v1 i spojny source revision, a nastepnie
tworzy wynik read-only bez prawa do wznowienia sesji.

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
- component source pack: tylko komponenty i relacje wybranych sciezek target ->
  komponent widoku, jawne liczniki pominietego grafu, deduplikacja pelnych
  plikow tych sciezek, kompaktowy effective route chain, jednopoziomowy direct
  base slice oraz nieblokujace braki,
- origin/source/nonce/replay/popup failure w Browser Tools i receiverze,
- model/effort, cache/refresh widokow, czteropoziomowe drzewo i tool scope,
- pinned Copilot instructions, trzy standardowe korzenie project skills,
  walidacje frontmatter, katalog naglowkow i fail-closed guidance preparation,
- jednosekcyjny report oraz pojedyncza prezentacje scalonych metadata,
- business-first answer contract: zachowanie `as-is` versus wymaganie,
  frontend/backend, obserwowalne scenariusze oraz zweryfikowane `METHOD path`,
- `QUEUED` przed dispatch, strict import/export v2 z odczytem legacy v1 i odrzucenie obcych wersji,
- modal bookmarkleta, brak alternatywnego launchera i brak osobnej karty
  read-only,
- testy Angulara, build produkcyjny, `FrontendPageTest` i pakiet backend-dev.

Testy automatyczne nie zastepuja kontrolowanego pilota z rzeczywistym
Copilot/GitLab dla rodzin pytan o walidacje, dane, stan i skutek akcji.
