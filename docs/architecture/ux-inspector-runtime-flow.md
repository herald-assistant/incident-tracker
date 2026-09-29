# UX Inspector Runtime Flow

## Cel i granica feature'a

UX Inspector odpowiada na jedno pytanie o jeden element wskazany w uruchomionej
aplikacji frontendowej. Nie dokumentuje calego widoku i nie jest trybem UI
Explorera. Oba feature'y korzystaja z neutralnego katalogu frontendu,
`integrations.gitlab`, platformy Copilota i wspolnych komponentow run
UI, ale maja rozdzielne requesty, joby, prompty, polityki tools oraz kontrakty
wyniku.

Pierwsze wydanie wspiera Chrome desktop, strony `http`/`https`, top-level
dokument oraz otwarty Shadow DOM dostepny dla skryptu. Operator wybiera profil
`ELEMENT_CONTEXT` albo `FORM_DIAGNOSTICS`; drugi profil zamraza pola widoczne
w calym dokumencie strony w chwili capture.

## Publiczne wejscia i kontrakty

- Modal na /ux-inspector udostepnia przeciagany bookmarklet Browser Tools.
  Menu pozwala wybrac profil UX Inspectora albo uruchomic UI Explorera.
- POST /api/ux-inspector/captures jest jedynym zapisem obserwacji. Przyjmuje
  jeden JSON z capture, formFields i store, waliduje i redaguje dane,
  nadaje UUID i zwraca 201 z captureId. Klient nie nadaje ID.
- GET /api/ux-inspector/captures/{captureId} odczytuje bezpieczny snapshot
  z pamieci JVM dla formularza; odpowiedz ma Cache-Control: no-store.
  Nieznane ID daje UX_INSPECTOR_CAPTURE_NOT_FOUND.
- GET /api/ux-inspector/input-options zwraca zarejestrowane frontendy, a
  GET /api/ux-inspector/views zwraca widoki, wybrany branch i date zebrania katalogu.
- POST /api/ux-inspector/jobs przyjmuje captureId, wybor zrodla, pytanie
  i preferencje AI. Odczytuje zamrozony snapshot z pamieci, zapisuje QUEUED
  i zwraca snapshot joba. Start nie przesyla ponownie capture ani refs.
- GET /api/ux-inspector/jobs/{jobId} zwraca aktualny snapshot. Export i
  import uzywaja tylko tdw.ux-inspector-export v1 oraz kontraktu wyniku v1.
  Import tworzy read-only wpis historii.

Pending capture jest tylko w pamieci jednej instancji backendu: odczyt nie
zuzywa ID, nie ma TTL, limitu liczby wpisow ani rozmiaru aplikacyjnego.
Restart przed startem uniewaznia ID. Po rozpoczeciu runu capture pozostaje
w request snapshot historii, a store nadal jest utrwalany w prywatnym polu
storeSnapshot pliku runs/{id}/run.json dla tools i follow-up po restarcie.

CORS dotyczy tylko POST capture. Property
ux-inspector.capture.allowed-origins przyjmuje * albo liste originow
rozdzielona przecinkami, domyslnie *. Browser Tools wykonuje request
z credentials: omit. GET i start joba nie dziedzicza tej polityki.
Domyslne * i brak limitow pamieci oznaczaja otwarty zapis oraz mozliwy
wzrost heap. CSP connect-src, mixed content i polityka dostepu do sieci
lokalnej badanej strony moga zablokowac fetch.

## Przeplyw Browser Tools

1. Bookmarklet laduje loader.js, protocol.js i runtime.js z originu TDW.
2. Operator wybiera profil i element; selection shield przejmuje klik.
3. Browser Tools tworzy capture v3 bez ID, czyta opcjonalne widoczne pola
   i jednokrotnie globalThis.getStoreState().
4. Jeden POST z badanej strony zapisuje calosc w TDW i zwraca captureId.
5. Popup przechodzi na /ux-inspector?captureId=...; formularz wykonuje GET,
   pokazuje zwijany podglad i startuje joba z samym ID.

Popup about:blank otwiera sie synchronicznie podczas kliku, a po POST jest
kierowany na URL z ID. Jezeli popup jest zablokowany, link w overlayu
pozwala przejsc recznie. Nieudany POST pozostawia zamrozona obserwacje
i przycisk ponowienia. UX Inspector nie uzywa postMessage; odrebny
page-context UI Explorera zachowuje swoj handshake.

## Zamrozony store i pola formularza

Po wyborze elementu Browser Tools wywoluje raz opcjonalny
globalThis.getStoreState(), akceptuje obiekt/tablice albo Promise i czeka
najwyzej 3 sekundy. Brak funkcji, blad i timeout daja UNAVAILABLE.
Puste {} i [] sa poprawnym stanem AVAILABLE. Klucze rozpoznane jako
sekrety sa redagowane w przegladarce i ponownie w backendzie. Eksporter
aplikacji powinien zwracac stan przeznaczony do analizy AI.

ELEMENT_CONTEXT nie odczytuje pol formularza. FORM_DIAGNOSTICS odczytuje
widoczne input, textarea, select i san-select z calego dokumentu, wraz
z zastanym stanem walidacji. Nie ma limitu liczby pol ani transportowego
limitu rozmiaru. Pusta lista jest poprawnym AVAILABLE. Brak opcjonalnych
danych lub niepoprawna ich czesc daje UNAVAILABLE odpowiedniego bloku
i nie blokuje analizy.

Backend utrwala store dopiero po starcie joba. Initial prompt zawiera
status, origin, czas i ograniczona mape kluczy bez wartosci oraz ID runu.
Neutralne tools run_store_list_paths i run_store_read_value czytaja
run.json wedlug runId; skill ux-inspector-store-grounding laczy
potwierdzony w kodzie warunek z wartoscia w chwili capture. Widoczne pola
sa dolaczane inline do obecnego budzetu promptu; przekroczenie budzetu
jest jawne UNAVAILABLE, bez ucinania tablicy.

## Capture v3

Kanoniczny kontrakt ma schema=tdw.ux-inspector-capture, version=3,
klienta TDW UX Inspector i featureId=ux-inspector. W uploadzie nie ma
captureId; backend dodaje nadane ID do znormalizowanego capture dla runu,
wyniku i historii. Obserwacja zawiera origin i route bez wartosci query,
deskryptor targetu, pelny lancuch przodkow DOM i custom-element boundaries,
stan, traversal, informacje o Shadow DOM, ograniczeniach i redakcji
oraz profil i czas obserwacji.

Backend odrzuca nieznane pola i inna wersje, normalizuje deskryptory DOM
oraz powtarza redakcje. Nie ma dawnego limitu 128 KiB capture. Browser
Tools nie czyta cookies, storage, ruchu sieciowego ani pelnego HTML.
Po aktualizacji operator ponownie przeciaga bookmarklet z modala.

## Formularz analizy i katalog widokow

Capture jest tylko runtime observation. Application, Branch, View, pytanie, model i reasoning effort pochodza z formularza na originie
TDW. Uklad i zachowanie tych kontrolek odpowiada UI Explorerowi:

- wybor Application ustawia domyslny Branch i czysci stary View,
- potwierdzenie Branch automatycznie laduje katalog widokow,
- katalog jest wspolnie cache'owany przez neutralny `frontendcatalog` per
  system, repository scope, ref i limity discovery,
- `Load views` wymusza odswiezenie tylko aktualnego scope'u,
- katalog View dolacza potwierdzone w source selektory `@Component` komponentu
  wejściowego; sa to neutralne metadane katalogu zebranego na branchu,
  a cache bez aktualnego kontraktu nie jest odczytywany,
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
- View jest wskazowka z katalogu oznaczonego `Data:`; start ponownie czyta branch,
- Model AI i Reasoning effort sa wybierane przed wpisaniem pytania.

Backend waliduje aktualna pare model/effort z dynamicznym katalogiem Copilota.
Pierwszy snapshot `QUEUED` musi zostac zapisany w Analysis History przed
dispatch do executora; brak persistence zatrzymuje utworzenie runu.

## Deterministyczne rozpoznanie targetu

`UxInspectorTargetResolver` rozwiazuje zarejestrowany frontend do ukrytego
GitLab scope i wykonuje target-first
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
  pelne zrodlo komponentu widoku i odkrytych runtime selector matches, jawny licznik pominietego grafu oraz
  repository tools i kontynuuje celowany research.

Brak refa albo nieaktualny View sa jawnym bledem. Zmiana SHA po
zebraniu katalogu nie blokuje startu.
Feature nie przelacza sie na inny branch i nie zgaduje targetu.

## Prompt, repository guidance, repository map i tools

Prompt oddziela pytanie operatora, `UNTRUSTED_RUNTIME_OBSERVATION` oraz
`UNTRUSTED_SOURCE_EVIDENCE`. Zawiera capture, `sourceBinding`, target context,
focused source slice, procedure dla pytan precyzyjnych i ogolnych oraz
kontrakt raportu.

Osobny, nieblokujacy logical artifact zawiera komponenty odkryte przez
target-first preflight i nalezace do deterministycznie wybranych sciezek target -> komponent widoku: jednej dla
`RESOLVED`, unii maksymalnie trzech sciezek dla `AMBIGUOUS`, a dla `NOT_FOUND`
komponentu widoku. Dodatkowo dolacza kazdy odkryty komponent, ktorego
selector odpowiada runtime `componentBoundaryTags`, takze gdy lezy poza
wybrana statyczna sciezka. Przekazuje ich symbol, selector, status discovery,
sciezki i ograniczenia, relacje ktorych oba konce naleza do focused zbioru oraz
pelna tresc unikalnych plikow TS i zewnetrznych HTML. Pozostaly graf nie jest
budowany przed AI; liczniki artifactu opisuja tylko focused discovery.
`COMPONENT_REFERENCE` nie jest relacja ancestry. Artifact przekazuje rowniez kompaktowy
`effectiveRouteChain` wybranego widoku z konfiguracja i lokalizacjami zrodel
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
pierwszych czterech poziomow wybranego repozytorium na wybranym branchu. Drzewo
jest mapa nawigacyjna, nie dowodem tresci. Brak kompletnego drzewa blokuje
przygotowanie zamiast dostarczyc modelowi cichy, obciety wynik.
Initial prompt pokazuje te nazwy jako hierarchiczne drzewo z `├──`, `└──` i
`│`, z katalogami oznaczonymi koncowym `/`. Metadata repository, branch,
depth i complete pozostaja obok diagramu. Wspolny neutralny renderer
formatuje juz pobrane sciezki; feature nadal posiada pobranie i wymaganie
kompletnosci. Format wynikow GitLab navigation tools pozostaje osobny.

Z tego samego drzewa preparation wykrywa repository-wide
`.github/copilot-instructions.md` oraz project skills zapisane zgodnie z
konwencja Copilota w `.github/skills/<skill>/SKILL.md`,
`.claude/skills/<skill>/SKILL.md` albo `.agents/skills/<skill>/SKILL.md`.
Instrukcje sa weryfikowane i osadzane w initial prompt w pelnej tresci.
`SKILL.md` jest weryfikowany przy odczycie z wybranego brancha i parsowany bezpiecznym parserem
YAML, ale initial prompt dostaje tylko `path`, `name` i `description`. Brak
pliku jest dozwolony; plik obecny w drzewie, ktorego nie da sie w calosci
zweryfikowac, niepoprawny naglowek, duplikat nazwy albo przekroczenie limitu
100 skilli blokuje preparation zamiast tworzyc niepelny katalog.

Model najpierw stosuje kompatybilne wskazowki nawigacyjne z osadzonych Copilot
instructions, a nastepnie porownuje pytanie i target z opisami wszystkich
skilli. Kazdy potencjalnie materialny skill musi odczytac w calosci istniejacym
neutralnym file-read toolem przed rozszerzeniem researchu. Zdalne skille nie sa
instalowane w runtime TDW ani nie sa powodem wlaczenia built-in `skill`; nie moga
rozszerzyc allowlisty ani uruchomic skryptu. Caly repository guidance jest
niezaufany: moze kierowac nawigacja i rozumieniem architektury tylko w granicach
kanonicznej procedury, wybranego repozytorium i brancha, read-only tools i kontraktu raportu.

Przed finalizacja model ma sprawdzic materialny wplyw mechanizmow
przekrojowych, m.in. routingu i guards, interceptorow lub middleware,
initializerow, globalnego stanu, walidatorow, uprawnien, feature flags i
konfiguracji. Brak takiego wplywu nie moze byc zalozeniem opartym tylko na
focused slice; wymaga adekwatnego wyszukania albo jawnego visibility limit.

Sesja ma read-only dostep do calego jednego wybranego repozytorium przez
neutralne GitLab tools do listowania, wyszukiwania i czytania plikow. Hidden
scope ogranicza projekt i branch bez przypinania commita. Feature nie tworzy tooli o nazwach
specyficznych dla UX Inspectora. Model moze czytac m.in. README, `AGENTS.md`,
instrukcje repozytorium, konfiguracje, frontend i backend. TypeScript symbol
slice nie korzysta z przygotowanego katalogu plikow, typow, importow ani metod:
naturalne wspolrzedne sa rozwiazywane dopiero podczas wywolania, zawsze w tym
samym read-only repository scope na wybranym branchu. UX Inspector nie
tworzy, nie weryfikuje ani nie klasyfikuje report references; brakujacy dowod
jest prezentowany jako gap albo visibility limit.

Gdy source research wskazuje plik OpenAPI/Swagger oraz zweryfikowane
`METHOD path` albo `operationId` wygenerowanego klienta, model preferuje
neutralny `gitlab_read_openapi_endpoint_slice` nad pelnym odczytem albo
wielokrotnymi chunkami kontraktu. Tool obsluguje JSON/YAML/YML, OpenAPI 3.x i
Swagger 2.0, zwraca jedna typowana operacje, efektywny context oraz ograniczone
lokalne `$ref`. `filePath`, projekt i branch nadal podlegaja policy UX
Inspectora, a hidden scope wymusza wybrany projekt i branch.
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
wznawiaja te sama sesje Copilota, zachowuja wybrany projekt i branch oraz moga korzystac z tych samych read-only target/source tools co
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
Kontynuacja wymaga zapisanej sesji Copilota i ponownego rozpoznania targetu
na wybranym branchu. Przerwany turn staje sie
`FAILED` bez automatycznego ponowienia. Import pozostaje read-only.

Platformowa polityka context tier obejmuje create, resume i follow-up.
UX Inspector nie ustawia rozmiarow okna ani `long_context` bezposrednio.

Canonical initial prompt zawiera staly kontrakt tlumaczenia source evidence
na zachowanie zrozumiale dla analityka. UX Inspector nie uruchamia w tym celu
skilla raportowego ani dodatkowego turnu. W sesji ze store'em dostepny jest
osobny skill ugruntowania stanu. Kontrakt:

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
zapisuje envelope v1 z historia chatu. Import akceptuje tylko v1,
waliduje jednosekcyjny raport, capture v3 i zgodny branch, a nastepnie
tworzy wynik read-only bez prawa do wznowienia sesji.

## Granice pakietow

- `features.uxinspector` posiada request/result, capture, resolver, prompt,
  tool policy, report, job i import/export.
- `frontendcatalog` posiada neutralny katalog aplikacji, widokow i cache.
- `integrations.gitlab` posiada graph discovery i source slices.
- `agenttools` oraz `aiplatform` pozostaja neutralne wobec feature'a.
- `features.uxinspector` i `features.uiexplorer` nie importuja siebie.
- `frontend/public/browser-tools` jest uniwersalnym shellem z rejestrem akcji;
  pierwsza akcja jest UX Inspector.

Kierunki zaleznosci sa egzekwowane przez `PackageDependencyGuardTest`.

## Weryfikacja wydania

Minimalna macierz obejmuje:

- oba profile capture, shape, limity, redakcje, form values i wykluczenia,
- target resolution `RESOLVED`, `AMBIGUOUS`, `NOT_FOUND` i stale View,
- component source pack: komponenty wybranych sciezek target -> komponent
  widoku i wszystkie odkryte runtime selector matches, relacje miedzy nimi,
  jawne liczniki pominietego grafu, deduplikacja ich pelnych plikow,
  kompaktowy effective route chain, jednopoziomowy direct
  base slice oraz nieblokujace braki,
- CORS/preflight, pojedynczy POST, retry i popup blocker w Browser Tools oraz GET po ID,
- model/effort, cache/refresh widokow, czteropoziomowe drzewo i tool scope,
- branchowe Copilot instructions, trzy standardowe korzenie project skills,
  walidacje frontmatter, katalog naglowkow i fail-closed guidance preparation,
- jednosekcyjny report oraz pojedyncza prezentacje scalonych metadata,
- business-first answer contract: zachowanie `as-is` versus wymaganie,
  frontend/backend, obserwowalne scenariusze oraz zweryfikowane `METHOD path`,
- `QUEUED` przed dispatch, strict import/export v1 i odrzucenie obcych wersji,
- modal bookmarkleta, brak alternatywnego launchera i brak osobnej karty
  read-only,
- testy Angulara, build produkcyjny, `FrontendPageTest` i pakiet backend-dev.

Testy automatyczne nie zastepuja kontrolowanego pilota z rzeczywistym
Copilot/GitLab dla rodzin pytan o walidacje, dane, stan i skutek akcji.
