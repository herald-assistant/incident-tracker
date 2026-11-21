# UX Inspector Runtime Flow

Initial i follow-up korzystają z jednej allowlisty obejmującej także dwa
[narzędzia historii kodu](gitlab-code-history-tools.md) oraz report patch/chunk.
Guidance historii znajduje się w opisach narzędzi.
Historia korzysta ze zwykłego dostępu GitLaba, bez osobnego scope ani grantów.
Dowolny commit można odczytać przez diff/file/chunk bez wcześniejszego listowania.
Ref listy commitów jest domyślnie brany z sesji; model może wskazać inny.
Reguły edycji raportu pozostają bez zmian.

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

- Sekcja startowa na /ux-inspector udostepnia przeciagany bookmarklet Browser Tools.
  Menu pozwala wybrac profil UX Inspectora albo uruchomic UI Explorera.
  Włączony flagą [System Assistant](system-assistant-runtime-flow.md) dodaje
  panel nowej rozmowy lub kontynuacji z historii z małym page-context;
  nie używa capture UX Inspectora.
- POST /api/ux-inspector/captures jest jedynym zapisem obserwacji. Przyjmuje
  jeden JSON z capture, formFields, store i httpHistory, waliduje i redaguje dane,
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
w request snapshot historii. Store i historia HTTP sa utrwalane w prywatnych
polach storeSnapshot oraz httpHistorySnapshot pliku runs/{id}/run.json
dla tools i follow-up po restarcie; nie trafiaja w calosci do portable export.

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
3. Browser Tools tworzy capture v1 bez ID, czyta opcjonalne widoczne pola
   oraz niezaleznie, jednokrotnie globalThis.getStoreState() i globalThis.getHttpHistory().
4. Jeden POST z badanej strony zapisuje calosc w TDW i zwraca captureId.
5. Popup przechodzi na /ux-inspector?captureId=...; ekran wykonuje GET
   i po odczycie pokazuje formularz ze zwijanym podgladem. Start joba uzywa ID.

Wejscie na /ux-inspector bez wskazanego elementu pokazuje instrukcje
i zakladke TDW Browser Tools do przeciagniecia na pasek zakladek Chrome.
Trwajacy odczyt pokazuje komunikat o wczytywaniu, a blad odczytu udostepnia
ponowienie.

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

## Zamrozona historia HTTP

Oba profile wywoluja opcjonalny `globalThis.getHttpHistory()` dokladnie raz
przy wyborze elementu, niezaleznie od store'a. Getter zwraca tablice do 50
wpisow albo Promise. Limit oczekiwania wynosi 3 sekundy; pozny wynik jest
ignorowany. Nie ma wywolan enable/clear, interceptora fetch/XHR, replay ani
odswiezania. Ponowienie POST wysyla te same zamrozone dane.

Upload `httpHistory` ma jeden z ksztaltow:

- `{status:"AVAILABLE", entries:[...], capturedAt:"...", redactions:0}`;
- `{status:"UNAVAILABLE", reason:"GETTER_MISSING|GETTER_FAILED|TIMEOUT|INVALID_HISTORY|SERIALIZATION_FAILED"}`.

Kazdy wpis zachowuje `id`, `startedAt`, `durationMs` (liczba lub null),
`state` (`pending|success|error|cancelled`), `request` i `response` (lub null).
Request zawiera `method`, pelny `url`, `headers` z tablicami stringow i `body`;
response zawiera `status`, `headers` i `body`. Browser Tools opakowuje
`unknown` body jako `{format:"json|text|unavailable", value:..., reason:null}`.
Blad serializacji pojedynczego body daje `format=unavailable`, `value=null`,
`reason=BODY_SERIALIZATION_FAILED`, zachowujac reszte wpisu. GET/persistence
zwraca status, entries, capturedAt, source, redactions i reason. Backend
ponownie waliduje i redaguje caly blok; >50 wpisow, duplikaty ID i niepoprawny
kontrakt daja jawne UNAVAILABLE bez zatrzymania capture lub store'a.

`HttpDebugSnapshotUtils.body()` zachowuje natywne obiekty. Browser Tools
serializuje je rekurencyjnie, takze wewnatrz zwyklego JSON:

- `FormData`: `{$tdwType:"FormData", entries:[{name, value}, ...]}`.
  Lista zachowuje kolejnosc, duplikaty nazw i puste pola; value jest tekstem
  albo reprezentacja File. Pole o nazwie sekretu ma value `[REDACTED]`.
- `File`: `{$tdwType:"File", name, type, size, lastModified, content}`;
  `Blob`: `{$tdwType:"Blob", type, size, content}`. `type` to MIME, `size`
  to oryginalny rozmiar bajtowy, a `lastModified` to epoch milliseconds.
- `content`: `{encoding:"utf-8|base64", data:"...", byteLength, redacted, reason:null}`.
  Poprawne UTF-8 jest zachowane bez zmiany BOM, Unicode i zakonczen linii.
  Pozostale bajty sa kodowane bezstratnie w Base64; nie stosujemy Blob.text().
  Redakcja tekstu moze zmienic jego bajty; `byteLength` opisuje zapisany wynik,
  `redacted=true` ujawnia te zmiane, a `size` nadal opisuje oryginal.
  Backend sprawdza kodowanie i rozmiary oraz redaguje takze tekst przeslany
  jako Base64. Opaque binary jest zachowane bez interpretacji jego formatu.
  `$tdwType` jest dyskryminatorem reprezentacji natywnych w tym kontrakcie.

Blad odczytu bajtow zachowuje metadane pliku i pozostale pola body;
`content` ma wtedy null encoding/data/byteLength, redacted=false i
`reason=BINARY_READ_FAILED`. Dokladny string `[OMITTED]` jako cale body
daje `format=unavailable`, value=null, `reason=SOURCE_OMITTED`, odrozniony
od bledu serializacji TDW. Identyczny literalny tekst jest nierozroznialny
od znacznika eksportera; wewnatrz JSON lub pola FormData pozostaje tekstem.
Wszelkie bajty i dane formularza sa zbierane z gettera HTTP, nie z kontrolek
file na stronie. Timeout 3 sekund obejmuje rowniez odczyty File/Blob.

Istniejace tools wybieraja metadane i dane, np. `/name`, `/content/data`
albo `/entries/2/value/content/data` wzgledem body. Duzy string/Base64 moze
zajmowac jedna linie; `nextOffset` pozwala odczytac go w calosci.
Nie ma drugiej zakodowanej kopii niezredagowanego tekstu. Naglowki juz
zamaskowane przez SAFE_HEADERS albo obciete do 256 znakow przez eksporter
nie moga byc odtworzone. Initial i follow-up otrzymuja te zasady w guidance.

Sekrety w kluczach, naglowkach, body i URL sa redagowane po obu stronach.
URL zachowuje cala sciezke, kolejnosc, kodowanie i zwykle wartosci query;
nie jest skracany. JSON przechowywany jako tekst zachowuje precyzje liczb.
Duplikaty kluczy nie sa scalane; gdy bezstratny odczyt strukturalny nie jest
mozliwy, narzedzia udostepniaja tekst. Dane utracone juz przez eksporter
aplikacji pozostaja poza widocznoscia TDW.

Po wymaganym zapisie QUEUED zapis HTTP i store'a odbywa sie niezaleznie,
przed dispatch. Blad jednego nie blokuje drugiego. Przygotowanie promptu
odczytuje faktycznie zapisany snapshot, wiec awaria zapisu nie tworzy
pozornej listy. Progress, chat i reczna edycja raportu zachowuja oba
prywatne pola. Pending snapshot nie jest wymagany do kontynuacji runu.

Initial prompt ma `ux-inspector/http-history-index.md`: status, czas, origin,
liczbe wpisow i redakcje oraz tabele wszystkich wpisow w kolejnosci gettera:
`index` (0-based), `entryId`, `method`, pelny `url`, `startedAt`, `durationMs`,
`state / HTTP`. Markdown jest escapowany, bez obcinania URL-i lub wierszy.
Naglowki i body sa dostepne na zadanie. Manifest i guidance sa feature-owned.

Neutralne, read-only narzedzia w `localworkspace.analysisruns.tools`:

- `run_http_history_list_entries(offset?)`: indeks bez naglowkow i body;
- `run_http_history_list_paths(entryId?, part?, pointer, offset?)`: dzieci i typy;
- `run_http_history_read_value(entryId?, part?, pointer, startLine?, offset?, maxLines?)`: wybrana wartosc.

`entryId` jest ID z gettera, nie indeksem tablicy. `part` wybiera `entry`
(domyslnie przy entryId), `requestBody`, `responseBody`, `requestHeaders`
lub `responseHeaders`. Pominięcie obu wybiera cala tablice. JSON Pointer
jest wzgledny do wybranej czesci; pusty string wskazuje korzen. `~0` i `~1`
escapuja znaki kluczy. Wybieranie pola body nie dolacza jego duzych sasiadow.
`null`, brak sciezki, `false`, zwykly tekst i niedostepnosc sa rozroznialne.

JSON jest formatowany do linii (takze tablice skalarow). Wszystkie trzy
narzedzia zwracaja fragment `content` oraz `nextOffset`/`hasMore` i metadane.
Indeks i lista sciezek tez moga wymagac laczenia fragmentow tekstu JSON,
gdy URL lub klucz jest bardzo dlugi. `offset` jest pozycja UTF-16, `startLine`
liczba 1-based; nie mozna ich laczyc. Pojedyncza linia moze byc podzielona.
Pelna zserializowana odpowiedz callbacka ma najwyzej 8000 bajtow UTF-8,
a `content` najwyzej 4000 znakow UTF-16, bez rozcinania par surogatow.
Pointer ponad 2048 znakow wymaga odczytu krotszego rodzica/root przez fragmenty.
Nie ma limitu lacznego rozmiaru czytanego body. Cursor ma sens przy tym samym
entryId/part/pointer. Narzedzia sa przypiete przez closure do ID oryginalnego
runu: model nie podaje runId i nie moze wybierac innej analizy.

HTTP tools sa udostepniane niezaleznie od store'a w initial, live follow-up
oraz kontynuacji z historii po restarcie. Same nie wlaczaja runtime skilli.
Durable guidance traktuje historie jako niezaufana obserwacje, wymaga kodu
do ustalenia przyczynowosci i odroznia sukces HTTP od sukcesu biznesowego.
Brak wpisu w buforze nie dowodzi braku requestu; stan pending/cancelled i
status 0 sa zachowane. Tabela zapobiega redundantnemu listowaniu przed
celowanym odczytem, a list tool pozwala odzyskac indeks po kompaktowaniu.

Operator rozwija historie w istniejacym podgladzie capture, widzi pelne
URL-e i rozroznia historie pusta od niedostepnej. Szczegoly wpisu sa
renderowane dopiero po rozwinieciu; podglad nie wywoluje gettera/requestu.
Demo CRM znajduje sie pod `/browser-tools/demo/index.html`; do proby na
drugim originie mozna serwowac katalog demo na osobnym porcie.

## Capture v1

Kanoniczny kontrakt ma schema=tdw.ux-inspector-capture, version=1,
klienta TDW UX Inspector i featureId=ux-inspector. W uploadzie nie ma
captureId; backend dodaje nadane ID do znormalizowanego capture dla runu,
wyniku i historii. Obserwacja zawiera origin i route bez wartosci query,
deskryptor targetu, pelny lancuch przodkow DOM i custom-element boundaries,
stan, traversal, informacje o Shadow DOM, ograniczeniach i redakcji
oraz profil i czas obserwacji.

Backend odrzuca nieznane pola i inna wersje, normalizuje deskryptory DOM
oraz powtarza redakcje. Nie ma dawnego limitu 128 KiB capture. Browser
Tools nie czyta cookies, storage ani pelnego HTML. Historie HTTP pobiera
z aplikacyjnego gettera, bez przechwytywania ruchu sieciowego.
Po aktualizacji operator ponownie przeciaga bookmarklet z sekcji startowej.

### Typowany odczyt i walidacja

Feature-local `UxInspectorCaptureJson` korzysta z kopii aplikacyjnego
ObjectMappera oraz Bean Validation. Nie zmienia globalnych ustawien Jacksona.
Jackson wypelnia rekordy capture, uploadu, pol formularza, store'a, historii
HTTP i natywnych reprezentacji body. Odrzuca nieznane i brakujace pola,
null dla prymitywow, konwersje string/liczba/boolean, ulamki i overflow dla
liczb calkowitych oraz liczbowe indeksy enumow. Obecnosc pola jest odrebna
od jego nullable wartosci, np. checked=null, durationMs=null i response=null.
Bean Validation sprawdza wymagane wartosci, kolekcje, zakresy oraz zaleznosci
format/value/reason. Dowolne dane body i store'a pozostaja JsonNode.

Daty capture i historii HTTP sa ISO-8601 stringami. Standardowy odczyt
Instant w uzywanym Jackson JSR-310 przyjmuje tez liczby pomimo Shape.STRING;
lokalny StdDelegatingDeserializer deleguje do scislego odczytu String, a
StdConverter stosuje Instant.parse. Nie ma recznego parsera struktury capture.

Upload zachowuje opcjonalne bloki jako drzewa do osobnego, typowanego odczytu.
Blad formFields/store/httpHistory daje UNAVAILABLE tylko tego bloku; blad
wymaganego capture lub koperty daje dotychczasowy blad API.
UxInspectorCaptureNormalizer zachowuje reguly i redakcje deskryptorow DOM.
UxInspectorHttpHistoryNormalizer sklada zwalidowana historie, sprawdza
unikalnosc ID i deleguje redakcje do UxInspectorHttpHistorySanitizer.
Sanitizer posiada redakcje URL/header/body i bezstratna obsluge bajtow;
metadane FormData/File/Blob odczytuje przez te same DTO i walidacje.

UxInspectorExportEnvelopeReader jest wspolnym odczytem importu, eksportu
zapisanego runu i lokalnego continuation. Najpierw weryfikuje capture przez
scisly odczyt feature'a, nastepnie odczytuje cala kopertę standardowym mapperem.
Ten preflight zachowuje zasady pozostalych shared DTO raportu. Scisly odczyt
obowiazuje dla biezacego kontraktu v1 API, persistence oraz eksportu;
redakcja pozostaje odpowiedzialnoscia normalizerow i sanitizera.

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

`UxInspectorTargetResolver` zaczyna od komponentu wybranego widoku na branchu
z UI. Dla tagów z `componentBoundaryTags` sprawdza już odczytane deklaracje,
potem bezpośrednie importy komponentu, który używa tagu w szablonie. Jeśli nadal
brakuje deklaracji, szuka jej w repozytorium. Ten krok obejmuje komponent widoku
i zaobserwowane granice komponentów.

Resolver ocenia osobno każdy element szablonu o tagu zgodnym z capture.
Porównuje pełne tokeny klas i wartości atrybutów, m.in. `id`, `data-testid`,
`formControlName` i `aria-label`. Ten sam sygnał z atrybutu i selektora liczy
raz. Nazwa dostępnościowa, tekst i rola dostarczają dodatkowych wskazówek.
Słowo widoczne w nagłówku nie wystarcza do dopasowania klasy CSS. W rankingu
komponentów liczą się również zgodność trasy i odległość przodka w DOM.
Najbliższa granica komponentu ma największą wagę wśród przodków.

- `RESOLVED` oznacza, że komponent uzyskał wymagany wynik i przewagę. Jeśli
  resolver rozpoznał też element, `sourceBinding` wskazuje jego dokładny tag
  otwierający, zakres linii, powiązania Angulara i obsługę wysłania otaczającego
  formularza, gdy jest znana. Akcja sąsiedniego elementu w tej samej linii
  pozostaje przy swoim tagu. Gdy wystarczyły sygnały komponentu, a brakuje
  wskazania konkretnego elementu, zakres linii i fragment elementu są puste.
- `AMBIGUOUS` oznacza niewystarczającą pewność wyboru komponentu lub remis
  najlepiej dopasowanych elementów w zwycięskim komponencie. AI dostaje materiał
  maksymalnie trzech kandydatów i może doczytać ich źródła.
- `NOT_FOUND` oznacza brak dostatecznych wskazówek w badanych źródłach. AI
  dostaje dostępny kod widoku i rozpoznanych komponentów oraz narzędzia do
  dalszego badania.

Brak refa albo nieaktualny View sa jawnym bledem. Zmiana SHA po
zebraniu katalogu nie blokuje startu.
Resolver rozpoznaje target na branchu wybranym w UI.

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
kanonicznej procedury, dostępu do odczytu w skonfigurowanej grupie GitLab i kontraktu raportu.

Przed finalizacja model ma sprawdzic materialny wplyw mechanizmow
przekrojowych, m.in. routingu i guards, interceptorow lub middleware,
initializerow, globalnego stanu, walidatorow, uprawnien, feature flags i
konfiguracji. Brak takiego wplywu nie moze byc zalozeniem opartym tylko na
focused slice; wymaga adekwatnego wyszukania albo jawnego visibility limit.

Analiza zaczyna się w wybranym frontendzie. Gdy odpowiedź zależy od backendu
lub biblioteki, AI może wyszukiwać i czytać pliki oraz historię w projektach
głównej grupy GitLab i jej podgrup, zgodnie z uprawnieniami integracji.
W każdym projekcie preferuje branch wybrany w UI. Może listować inne gałęzie
przez `gitlab_list_repository_branches` i czytać inne branche, tagi lub SHA.
Pierwszy odczyt brancha lub tagu przez narzędzia repozytorium przypina jego
SHA dla pary projekt/ref na czas tury. Narzędzia frontendu do odczytu trasy
i symboli TypeScript zachowują repozytorium i branch wybranego widoku.
Symbol lub import jest rozwiązywany w chwili wywołania. Brakujące dowody
trafiają do luk lub ograniczeń widoczności raportu.

Gdy source research wskazuje plik OpenAPI/Swagger oraz zweryfikowane
`METHOD path` albo `operationId` wygenerowanego klienta, model preferuje
neutralny `gitlab_read_openapi_endpoint_slice` nad pelnym odczytem albo
wielokrotnymi chunkami kontraktu. Tool obsluguje JSON/YAML/YML, OpenAPI 3.x i
Swagger 2.0, zwraca jedna typowana operacje, efektywny context oraz ograniczone
lokalne `$ref`. Odczyt korzysta z tego samego zakresu grupy i przypiętej rewizji badanego projektu.
Referencje zewnetrzne sa raportowane jako nierozwiazane i nie uruchamiaja
pobierania sieciowego ani przejscia do innego repozytorium.

Initial i follow-up udostepniaja tez `opctx_get_scope`, `opctx_list_entities`,
`opctx_search` i `opctx_get_entity` jako opcjonalna wiedze o systemach,
procesach, bounded contextach, logice procesowej, terminach i handoffach.
Model moze czytac caly katalog, od dowolnej encji, bez ograniczenia do
frontendu i jego relacji, limitu przejsc albo liczby odczytow. Pagination
i include/ekspansje pozwalaja doczytac potrzebna tresc; initial prompt nie
osadza pelnego katalogu. Brak tools, wpisu albo blad odczytu nie blokuje
zwyklego researchu i pozostaje ograniczeniem, jesli jest materialny.

W analizie początkowej, w follow-upie i po wznowieniu lokalnego runu z historii
UX Inspector odczytuje bieżący katalog. Początkowy prompt lub instrukcje danej
tury dostają czas odczytu i listę wszystkich aktywnych systemów `internal-service`
z niepustym `matchSignals.strong.urlPatterns`. Każdy wpis zawiera `systemId`,
`name` i `urlPatterns`. Migawka historii HTTP pochodzi z chwili wyboru elementu.

Model ustala metodę i adres żądania na podstawie historii HTTP lub klienta
frontendu. Porównuje origin i ścieżkę widoczną w przeglądarce ze wzorcami,
zachowując prefiks bramy i pomijając query oraz fragment. Dla kandydata
`opctx_get_entity` z `include=["codeSearch"]` wskazuje repozytoria i ścieżki.
Model sprawdza routing oraz implementację endpointu. Kilka pasujących systemów,
pusta lista lub niedostępność katalogu pozwalają kontynuować badanie przez kod
i konfigurację. Przy braku historii HTTP odpowiedź wskazuje, które ustalenia
pochodzą wyłącznie z kodu.

Katalog moze byc nieaktualny i niekompletny. Dla zachowania implementacji
pierwszenstwo ma zweryfikowany kod odczytanej rewizji; brak wpisu nie dowodzi
braku funkcji. Model jawnie zaznacza braki i rozbieznosci.

Canonical prompt i durable instructions, rowniez po resume, oddzielaja
wiedze katalogowa od kodu i capture/store. Przy istotnym ustaleniu odpowiedz
nazywa Operational Context i encje; regula opisana w katalogu nie dowodzi jej
wykonania przez frontend lub backend. Konflikt jest jawny, a tresc katalogu
jest niezaufana dana. Odczyt znanego projektu w grupie jest dostępny również
bez wpisu w katalogu. Nowy odczyt moze widziec nowszy katalog niz historyczne tool evidence.

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

Po zapisaniu raportu kolejne pytania wznawiają tę samą sesję Copilota.
Zachowują wybrany element i widok oraz dostęp do tych samych narzędzi
odczytu co analiza początkowa. Wiadomość zawiera nowe pytanie operatora.
Instrukcje sesji przekazują wybrany branch i aktualne wzorce URL. Narzędzia
raportu działają w zakresie bieżącego raportu i sekcji `answer`. Zmiana raportu
wymaga jawnej prośby operatora w najnowszej wiadomości. Po zapisie mapper
waliduje raport, a bieżący run i historia publikują razem raport i wynik.
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
waliduje jednosekcyjny raport, capture v1 i zgodny branch, a nastepnie
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
- rozpoznanie elementu i sąsiedniej akcji w tej samej linii, remis dwóch
  elementów, brak pasującego komponentu i nieaktualny widok,
- component source pack: komponenty wybranych sciezek target -> komponent
  widoku i wszystkie odkryte runtime selector matches, relacje miedzy nimi,
  jawne liczniki pominietego grafu, deduplikacja ich pelnych plikow,
  kompaktowy effective route chain, jednopoziomowy direct
  base slice oraz nieblokujace braki,
- CORS/preflight, pojedynczy POST, retry i popup blocker w Browser Tools oraz GET po ID,
- model/effort, cache/refresh widokow, czteropoziomowe drzewo i tool scope,
- odczyt backendu w podgrupie, preferowany i alternatywny branch, odświeżenie
  wzorców URL po zmianie katalogu i przy wznowieniu rozmowy z lokalnej historii,
- branchowe Copilot instructions, trzy standardowe korzenie project skills,
  walidacje frontmatter, katalog naglowkow i fail-closed guidance preparation,
- jednosekcyjny report oraz pojedyncza prezentacje scalonych metadata,
- business-first answer contract: zachowanie `as-is` versus wymaganie,
  frontend/backend, obserwowalne scenariusze oraz zweryfikowane `METHOD path`,
- `QUEUED` przed dispatch, strict import/export v1 i odrzucenie obcych wersji,
- bookmarklet w sekcji startowej, brak alternatywnego launchera i brak osobnej karty
  read-only,
- testy Angulara, build produkcyjny, `FrontendPageTest` i pakiet backend-dev.

Testy automatyczne nie zastepuja kontrolowanego pilota z rzeczywistym
Copilot/GitLab dla rodzin pytan o walidacje, dane, stan i skutek akcji.

Walidacja parametrów, zwykłego zakresu repozytorium i reason należy do
neutralnych GitLab tools. UX policy zachowuje guard obecności repository scope,
zakaz applicationNames oraz ograniczenia rozszerzenia pliku i limitów OpenAPI.
