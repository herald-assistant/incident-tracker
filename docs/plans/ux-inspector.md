# UX Inspector - skupiona analiza wskazanego elementu

Status: in-progress

Source need: [UX Inspector - pytania o wskazany element uruchomionej aplikacji](../needs/ux-inspector.md)

Klasyfikacja: **L3**. Feature uruchamia kod na niezaufanej stronie, przekracza
granice originow, wprowadza nowy publiczny kontrakt joba i nowa polityke tools.

## Potrzeba / dlaczego

UI Explorer dokumentuje caly ekran i jego osiem sekcji. Nie powinien byc
rozszerzany o ukryty tryb pytania o pojedynczy element, bo taki tryb ma inna
jednostke analizy, inny prompt, inna polityke eksploracji i inny kontrakt
wyniku. Testowa analiza jednego elementu uruchomiona szerokim flow UI Explorera
wykonala dziewiec wywolan AI, zgromadzila okolo 250 tys. tokenow wejsciowych i
wytworzyla osiem sekcji. Wiekszosc pracy nie odpowiadala intencji operatora.

UX Inspector ma zaczynac od wskazanego elementu i odpowiadac tylko na konkretne
pytanie. Ma korzystac z tych samych platformowych capability co UI Explorer,
ale pozostac osobnym feature'em, z osobnym jobem, promptem, reportem,
historia i ekranem.

## Proponowane rozwiazanie

Powstaje feature `UX Inspector` z route'em `/ux-inspector` i API pod
`/api/ux-inspector/**`. TDW Browser Tools przekazuje do niego typowany capture
jednego elementu. Na zaufanym ekranie operator potwierdza frontend system,
branch i view, wybiera model oraz reasoning effort, wpisuje pytanie i uruchamia
asynchroniczny job.

Backend przypina immutable source revision, deterministycznie buduje
ograniczony zestaw kandydatow targetu i przygotowuje poczatkowy source slice.
Copilot dostaje kompletna procedure w prompcie, session-bound scope oraz waska
allowliste neutralnych i lokalnych tools. Rozszerza kontekst tylko w kierunku
wymaganym przez pytanie.

Kanonicznym wynikiem jest platformowy `AnalysisReport` z dokladnie jedna
dozwolona sekcja `answer`. Model zapisuje tresc przez `report_upsert_section`,
referencje i ograniczenia przez section `meta` oraz `report_update_meta`,
naglowek przez `report_update_header`, a na koncu sprawdza stan przez
`report_get_current`. Tekst finalnej wiadomosci Copilota nie jest wynikiem ani
fallbackiem raportu.

```text
badana strona
  -> TDW Browser Tools -> UX Inspector -> capture v3
  -> exact-origin/source/nonce postMessage
  -> /ux-inspector: capture preview + system + branch + view + pytanie + AI
  -> POST /api/ux-inspector/jobs
  -> pinned revision + deterministic source binding + focused source slice
  -> Copilot SDK + canonical prompt + scoped neutral read tools + report tools
  -> AnalysisReport[answer] + meta
  -> dedykowany ekran + Analysis History + export
```

## Decyzje graniczne

### Osobny feature, nie profil UI Explorera

- Backend mieszka pod `features.uxinspector`.
- Frontend mieszka pod `features/ux-inspector`.
- UX Inspector nie importuje `features.uiexplorer` ani jego modeli, facade,
  joba, promptu, skilli, report mappera lub kontraktu exportu.
- UI Explorer pozostaje screen-centered i nie zna capture, protokolu Browser
  Tools ani pytan o element.
- Reuse przechodzi przez `aiplatform`, `agenttools`, `integrations`, `shared`,
  `common` oraz wspolne komponenty Angulara.

### Brak fallbackow i kompatybilnosci wstecznej

- Jedynym obslugiwanym kontraktem jest capture v3 dla feature id
  `ux-inspector` i klienta `TDW UX Inspector`.
- Nie ma dual-read starszych capture'ow, aliasu `ui-explorer-inspector`, markera w
  `scenarioDescription`, przekierowania do UI Explorera ani importu starego
  payloadu.
- `capture.html`, dummy accepted i reczny copy/paste zostaja usuniete.
- Nie ma JSON response parsera jako rezerwowej sciezki wyniku. Brak poprawnie
  zapisanego raportu konczy job stanem `BLOCKED` albo `FAILED` zgodnie z typem
  bledu.
- Blad popupu, handshake albo polityki przegladarki zatrzymuje operacje,
  odrzuca capture i pokazuje komunikat. Nie uruchamia innego feature'a.
- Stare bookmarklety przestaja dzialac. Operator generuje nowy launcher ze
  strony instalacyjnej dostarczonej w tym samym wydaniu.
- Statyczne zasoby Browser Tools sa publikowane tylko pod
  `/browser-tools/**`; dotychczasowe `/tdw-inspector/**` zostaje usuniete bez
  redirectu albo aliasu.
- Cutover Browser Tools, route'u, receivera i backendu trafia w jednym
  samowystarczalnym wydaniu JAR; nie publikujemy polaczonej tylko polowy flow.

### UI podobne, kontrakt inny

UX Inspector reuse'uje uklad i wspolne komponenty UI Explorera, aby operator
rozpoznal sposob pracy. Nie kopiuje jednak jego logiki dokumentowania widoku.

- Glowny obszar zawiera formularz i wynik.
- Prawy aside korzysta z `AnalysisFeatureAsideComponent` oraz wspolnych paneli
  krokow, aktywnosci Copilota, tool feedback i usage.
- Formularz ma: frontend system, branch, view, podglad targetu, textarea
  pytania, model i reasoning effort.
- Nie ma section modes, scope selectorow dokumentacji ani osmiu kart wyniku.
- Wynik korzysta ze wspolnego renderera `AnalysisReport`, ale pokazuje jedna
  sekcje `Odpowiedz`.

## Baseline i conformance delta

### Baseline po odseparowaniu UI Explorera

- UI Explorer ma wlasny request, job, source preparation, trzy skille,
  osmisekcyjny report, historie i export. Nie przyjmuje danych Browser Tools.
- `frontend/public/tdw-inspector` dostarczyl sprawdzony spike launchera,
  izolowanego Shadow DOM, shielda, wow highlightu i minimalizowanego capture.
- `integrations.gitlab.frontend` udostepnia neutralne rozpoznawanie Angulara,
  route, template bindings, symbol slices i graf zaleznosci.
- Platforma Copilot udostepnia session-bound hidden context, allowliste tools,
  eventy, usage i report tools.
- Frontend ma wspolne komponenty aside, krokow analizy, reportu i historii.
- Shared/operator API udostepnia katalog modeli i dozwolonych reasoning
  efforts.

Stan repo po rollbacku jest celowo przejsciowy i fail-closed: UI Explorer jest
odlaczony od Browser Tools, dummy receiver oraz reczny transfer zostaly
usuniete, a Browser Tools wskazuje docelowy route `/ux-inspector`. Dopoki
dedykowany receiver i backend UX Inspectora nie powstana w jednym pionie,
handshake konczy sie czytelnym bledem i nie uruchamia innej analizy. Tego stanu
nie traktujemy jako wydania gotowego do dystrybucji.

### Conformance delta

| Obszar | Baseline | UX Inspector |
|---|---|---|
| Jednostka pracy | caly view | jedno pytanie o jeden target |
| Wejscie runtime | reczny wybor view | capture v3 + jawnie potwierdzony view |
| Backend | `features.uiexplorer` | nowe `features.uxinspector` |
| Publiczne API | `/api/ui-explorer/**` | `/api/ux-inspector/**` |
| Source preparation | reachability dla wielu sekcji | ranked target resolution i waski slice |
| Procedura AI | skille UI Explorera | kanoniczny prompt, skills wylaczone dla sesji |
| Report | do osmiu sekcji | dokladnie jedna sekcja `answer` |
| Historia/export | UI Explorer v5 | osobny typ i schema UX Inspector v2 |
| Browser transport | stary spike/capture v1 | wylacznie capture v3 do `/ux-inspector` |

## Konsumenci i reuse

Zmiana obejmuje nastepujacych konsumentow:

- statyczny installer, loader, protocol i runtime TDW Browser Tools,
- nowy route Angulara `/ux-inspector`, nawigacje i ekran instalacyjny,
- katalog frontend system/branch/view oraz katalog opcji AI,
- nowe job API, job state, historie i import/export,
- Copilot runtime, report session store i GitLab frontend tools,
- wspolne UI przebiegu analizy i `AnalysisReportPanelComponent`,
- produkcyjny bundle w `src/main/resources/static` oraz routing Springa.

Nie zmieniamy kontraktow ani zachowania UI Explorera. Neutralna logika katalogu
frontendu moze zostac wydzielona z obecnego feature'a tylko wtedy, gdy oba
feature'y korzystaja z niej przez stabilna granice. Publiczne endpointy UI
Explorera zachowuja wtedy swoje obecne zachowanie; nie jest to kompatybilnosc
starego Inspectora, lecz niezmiennik niezaleznego produktu.

## Delta optymalizacyjna po pierwszym rzeczywistym runie

Run `ef6b002f-b8f5-4f8e-8b74-cd55f2a97f66` potwierdzil poprawny target i
source grounding, ale nie spelnil progu efektywnosci: cztery obowiazkowe
wywolania skilli i cztery sekwencyjne operacje raportowe stanowily osiem z
dziewieciu wywolan tools. Ogolne pytanie zostalo dodatkowo skierowane przez
keywordowy fallback do `action-flow`, mimo ze operator prosil o zwiezly opis
funkcjonalny.

Zmiana ma poziom **L3**, poniewaz zmienia feature-owned workflow skilli oraz
uzywa istniejacego platformowego trybu sesji bez katalogu skilli przy
zachowaniu allowlistowanych tools. Publiczne API, capture v2, job lifecycle,
report/result, historia i export pozostaja bez zmian.

Baseline, ktory pozostaje prawdziwy:

- deterministic target resolution i focused source slice pozostaja pierwszym
  zrodlem evidence,
- `NOT_FOUND` nadal blokuje AI, a `AMBIGUOUS` korzysta z session-bound
  `targetRef`,
- pinned frontend, project i source revision pozostaja backendowym scope'em,
- jedynym wynikiem jest `AnalysisReport` z sekcja `answer`, bez fallbacku z
  finalnego tekstu,
- wspolny aside nadal pokazuje steps, activity, tools oraz usage.

Conformance delta:

- procedura pytania precyzyjnego i ogolnego przechodzi z siedmiu skilli UX
  Inspectora do jednego kanonicznego promptu feature'a,
- sesja UX Inspectora ustawia `skillsEnabled=false`, ale zachowuje jawna
  allowliste source/report tools; pozostale feature'y zachowuja wspolny
  katalog skilli,
- source research korzysta z neutralnych GitLab navigation/search/read tools,
  bez nowego repository toola `uxi_*`; feature-owned policy ogranicza project,
  branch, bezpieczna sciezke oraz powod, a hidden scope przypina commit. Nie
  stosuje Operational Context path prefixes,
- trzy niezalezne aktualizacje raportu maja zostac zlecone w jednym turnie,
  a `report_get_current` pozostaje osobnym sprawdzeniem po ich wykonaniu,
- kalkulator usage odejmuje `cacheWriteTokens` od zwyklego inputu, aby nie
  naliczac zapisu cache drugi raz,
- konsumenci wspolnego kalkulatora kosztu to wszystkie ekrany korzystajace z
`analysis-ai-usage-cost.utils.ts`; format DTO i import/export nie zmieniaja
  sie.

## Delta capture v3 po pilocie formularza

Test kontrolny pokazal, ze v2 wystarcza do znalezienia komponentu, ale nie
przekazuje modelowi jawnego powiazania targetu z bindingiem zrodlowym ani
stanu danych najblizszego formularza. Prompt nie powinien kompensowac braku
tych danych szerokim przeszukiwaniem repository.

Zmiana pozostaje poziomem **L3**, bo rozszerza publiczny capture oraz granice
danych pobieranych z niezaufanej strony. Uzytkownik zatwierdzil atomowy
cutover bez fallbackow i kompatybilnosci wstecznej.

Baseline zachowany przez v3:

- capture nadal powstaje raz, w chwili klikniecia, i jest przesylany tylko
  przez exact-origin/source/nonce handshake,
- system, branch, view i immutable revision nadal pochodza z zaufanego
  formularza TDW,
- resolver nadal blokuje `NOT_FOUND` i nie udostepnia arbitrary repository
  search,
- jedynym wynikiem pozostaje jednosekcyjny `AnalysisReport`,
- UI Explorer i neutralne GitLab file-read tools pozostaja bez zmian.

Conformance delta v3:

- Browser Tools oferuje jawny profil `ELEMENT_CONTEXT` albo
  `FORM_DIAGNOSTICS`; wybor zyje tylko w pamieci aktywnego shella,
- target dostaje ustrukturyzowany `domFingerprint` ze stabilnymi selector
  candidates, label linkage i granicami custom-element,
- `FORM_DIAGNOSTICS` zamraza najblizszy formularz, jego dozwolone wartosci,
  stan kontrolek i natywny `ValidityState`; hasla, hidden, pliki oraz pola
  sekretow sa zawsze wykluczone,
- backend tworzy jawny `sourceBinding` z owning component, zweryfikowanym
  fragmentem elementu, bindingami i submit handlerem; selector pozostaje
  sygnalem, nigdy instrukcja AI,
- limit capture rosnie do 128 KiB, a wartosci formularza maja osobny budzet,
  jawne flagi truncation oraz podwojna walidacje,
- receiver, backend, export/import i Browser Tools przechodza atomowo na
  capture/protocol v3 oraz export v2; starsze wersje sa odrzucane.

## Delta po pilotach source research: pelne wybrane repozytorium

Runy kontrolne po capture v3 pokazaly, ze poprawny `sourceBinding` nie
wystarcza dla pytan o pochodzenie danych, zapis, reguly biznesowe albo dalszy
skutek akcji. Model znal dokladna sciezke kolejnego pliku, ale neutralny odczyt
zostal odrzucony przez `codeSearchScope` Operational Context, a obecny prompt
zabranial nawigacji i wyszukiwania poza focused slice. Nawet udany odczyt
pliku spoza poczatkowego grafu widoku nie moglby zostac zacytowany, bo mapper
raportu dopuszczal tylko sciezki z deterministycznego reachability.

Zmiana ma poziom **L3**: rozszerza granice source research sesji, hidden scope,
allowliste tools, kontrakt promptu i walidacje referencji. Uzytkownik
zatwierdzil pelny odczyt jednego repozytorium wybranego na ekranie UX
Inspectora, bez ograniczenia do `pathPrefixes` i `codeSearchScopes`, nadal
read-only oraz na przypietej rewizji.

Baseline pozostajacy prawdziwy:

- scope obejmuje dokladnie frontend wybrany przez operatora oraz jego
  immutable commit; model nie moze wybrac innego repozytorium ani rewizji,
- deterministic target resolution i focused source slice pozostaja punktem
  startowym, a `NOT_FOUND` nadal blokuje AI,
- Browser Tools, capture v3, publiczne API, job lifecycle, historia oraz
  export v2 pozostaja bez zmian,
- kod i pliki instrukcyjne repozytorium sa niezaufanym source evidence i nie
  moga nadpisac kanonicznej procedury, polityki tools ani kontraktu raportu,
- jedynym wynikiem pozostaje `AnalysisReport` z sekcja `answer`.

Conformance delta:

- hidden context dostaje neutralny `GitLabRepositoryToolScope` dla jednego
  selected project, operator branch i katalogowego commit id; UX Inspector
  przestaje przekazywac `GITLAB_ALLOWED_APPLICATION_NAMES`,
- neutralne `gitlab_list_repository_tree`, `gitlab_list_repository_files`,
  `gitlab_search_repository_files`, `gitlab_read_repository_file` i
  `gitlab_read_repository_file_chunk` sa dostepne dla calego wybranego
  repozytorium; feature policy wymusza tylko selected project, selected branch,
  bezpieczna sciezke i powod, bez `pathPrefixes` oraz limitu liczby wywolan
  specyficznego dla UX Inspectora,
- initial preparation pobiera bezposrednio z neutralnego portu GitLaba komplet
  nazw katalogow i plikow dla pierwszych czterech poziomow przypietego commita.
  Wynik trafia raz do logical artifact; brak kompletnego drzewa zatrzymuje
  przygotowanie zamiast publikowac obciety fallback,
- prompt traktuje drzewo jako mape nawigacyjna, dopuszcza odczyt README,
  `AGENTS.md`, `.github/copilot-instructions.md`, konfiguracji i kodu z calego
  repozytorium oraz wymaga domkniecia lancucha istotnego dla pytania,
- mapper raportu rozszerza allowliste referencji tylko o pliki faktycznie
  odczytane przez repo-bound tools na tym samym projekcie i commicie; sama
  obecnosc sciezki w drzewie nie jest dowodem tresci,
- neutralny chunk read respektuje istniejacy `GitLabRepositoryToolScope` i
  rejestruje source ref tak samo jak full-file read; pozostali konsumenci
  zachowuja dotychczasowy kontrakt bez repository scope,
- publiczne DTO, frontend, capture, persistence i import/export nie zmieniaja
  sie.

Konsumenci dotknietego mechanizmu:

- UX Inspector prompt preparation, Copilot context/allowlista/policy,
  tool descriptions, report mapper i provider,
- neutralne GitLab navigation/read tools oraz ich evidence/audit lifecycle,
- Operational Context Assistance jako istniejacy konsument
  `GitLabRepositoryToolScope`; jego selected-repository semantics musza
  pozostac bez regresji,
- pozostale feature'y korzystajace z GitLab chunk read bez repository scope;
  ich zachowanie pozostaje w starej galezi wykonania.

Checklista delty:

- [x] Dodac kompletne czteropoziomowe drzewo selected repository do initial
  artifacts i przetestowac pagination, kolejnosc oraz fail-closed.
- [x] Przelaczyc UX Inspector na repo-bound hidden scope i pelna allowliste
  neutralnych navigation/search/read tools bez Operational Context path scope.
- [x] Ujednolicic neutralny chunk read z repository-bound pinningiem i
  rejestracja source refs.
- [x] Rozszerzyc procedure promptu dla pytan precyzyjnych i ogolnych oraz
  jawnie sklasyfikowac pliki instrukcyjne jako niezaufane evidence.
- [x] Walidowac nowe referencje raportu tylko wobec plikow rzeczywiscie
  odczytanych na selected commit.
- [x] Zaktualizowac need, runtime flow, decyzje i lokalne instrukcje oraz
  wykonac testy celowane, architecture guard i pelny backendowy `mvn -q test`.

Wynik weryfikacji delty 2026-09-16:

- testy celowane obejmujace tree artifact, prompt, hidden scope, allowliste,
  scope policy, target tools, report mapper, repo-bound full/chunk read i
  architecture guard zakonczyly sie powodzeniem,
- `mvn -q test` - 338 raportow Surefire, 1612 testow, zero failures/errors,
  jeden test pominiety,
- `git diff --check` i audit trailing whitespace nie wykazaly bledow; komunikaty
  Git dotyczyly jedynie lokalnej konwersji LF/CRLF.

## Zakres

### Kontrakt wejscia

`POST /api/ux-inspector/jobs` przyjmuje typowany request:

```json
{
  "systemId": "crm-agent-portal",
  "branch": "main",
  "viewId": "crm-contact-create",
  "sourceRevision": "a1b2c3d4",
  "question": "Dlaczego przycisk zapisu jest zablokowany?",
  "capture": {
    "schema": "tdw.ux-inspector-capture",
    "version": 3,
    "captureId": "cap_example",
    "captureProfile": "FORM_DIAGNOSTICS",
    "page": {
      "origin": "https://crm.example.com",
      "path": "/contacts/new",
      "title": "Create contact",
      "language": "pl",
      "queryParameterNames": []
    },
    "target": {
      "tag": "button",
      "role": "button",
      "accessibleName": "Zapisz",
      "text": "Zapisz",
      "domFingerprint": {
        "stableAttributes": {"data-testid": "contact-save"},
        "selectorCandidates": ["[data-testid=\"contact-save\"]"],
        "componentBoundaryTags": ["crm-contact-form"],
        "labelFor": null
      },
      "state": {"disabled": true}
    },
    "ancestors": [],
    "formSnapshot": {
      "valid": false,
      "controls": []
    },
    "limits": []
  },
  "model": "selected-model",
  "reasoningEffort": "medium"
}
```

Nazwy sa kierunkowe i zostana zamrozone w pierwszym kroku implementacyjnym.
Niezmienniki kontraktu:

- `question`, `systemId`, `branch`, `viewId`, `sourceRevision` i capture sa
  wymagane;
- backend nie ufa systemowi, view ani rewizji przeslanym przez badana strone;
  wybory pochodza z zaufanego formularza i sa ponownie walidowane;
- `sourceRevision` musi odpowiadac aktualnemu snapshotowi katalogu dla
  wybranego branch/view, inaczej start zwraca conflict;
- capture ma limit 128 KiB przed i po normalizacji, a wartosci formularza
  maja dodatkowy laczny budzet;
- nieznane pola sa odrzucane, a nie zachowywane „na przyszlosc”;
- pytanie i tekst DOM sa rozdzielonymi polami o roznych poziomach zaufania.

### Browser Tools i transport

Shell pozostaje uniwersalny: launcher, menu i feature registry nie znaja
Angulara ani API joba. Akcja `UX Inspector` dostarcza selektor i transport
capture v3. Installer, loader, runtime i demo sa serwowane pod
`/browser-tools/**`; stary publiczny katalog nie jest utrzymywany.

Po kliknieciu elementu runtime:

1. zatrzymuje natywna akcje i zamraza jeden capture,
2. otwiera `/ux-inspector#nonce=<...>&sourceOrigin=<...>`, bez payloadu w URL,
3. wymaga wiadomosci READY z dokladnego `tdwOrigin`, oczekiwanego
   `event.source`, nonce i wersji protokolu,
4. wysyla capture tylko do dokladnego `tdwOrigin`,
5. konczy dopiero po RECEIVED z tym samym nonce i `captureId`,
6. usuwa listenery, referencje do payloadu i overlay.

Receiver istnieje tylko w komponencie UX Inspectora; protokol nie jest
ladowany globalnie przez `index.html`. Fragment jest czyszczony przez
`history.replaceState` po odczytaniu parametrów transportu. Capture pozostaje
w pamieci do startu joba i nie trafia do `localStorage`, `sessionStorage`, URL,
logow ani analytics.

Przy bledzie runtime pokazuje krotki komunikat, usuwa capture i wraca do
launchera. Nie pokazuje JSON, nie kopiuje go do schowka i nie otwiera innego
ekranu.

### Capture v3

Normalizer ma allowliste danych i limity per pole. Profil `ELEMENT_CONTEXT`
zachowuje tylko sygnaly potrzebne do target resolution:

- semantyke targetu: tag, role, accessible name, ograniczony tekst,
- `domFingerprint`: allowliste stabilnych atrybutow, wygenerowane z nich
  selector candidates, label linkage i najblizsze custom-element boundaries,
- obserwowalne stany boolean/ARIA,
- ograniczona sciezke przodkow, z preferencja custom elementow i landmarkow,
- route metadata bez wartosci query,
- viewport bounds tylko do podgladu operatora, nie do mapowania kodu,
- flagi redakcji, obciecia, Shadow DOM i iframe.

Profil `FORM_DIAGNOSTICS` dodaje snapshot najblizszego formularza: nazwy,
typy, dozwolone pelne wartosci w granicach budzetu, checked/selected,
disabled/readonly/required oraz szczegoly `ValidityState`. Zawsze wyklucza
password, hidden, file oraz kontrolki rozpoznane jako token/session/secret.
Nie odczytuje cookies, storage ani requestow.

Normalizer usuwa inline handlers, style, dowolne `data-*`, URL-e z query
values, pelne klasy generowane, komentarze i wszystkie nieznane pola. Tekst
badanej strony oraz wartosci formularza sa oznaczone w promptcie jako
`UNTRUSTED_RUNTIME_OBSERVATION`.

### Target resolution i focused evidence

Backend nie przekazuje calego capture do ogolnego search promptu. Najpierw
deterministycznie buduje `UxInspectorTargetContext` w przypietej rewizji:

1. ogranicza repository do wybranego zarejestrowanego frontendu,
2. ogranicza graf do wybranego view i jego osiagalnego source scope,
3. dopasowuje stabilne atrybuty, accessible name, bindingi, custom-element
   ancestry i route,
4. szereguje kandydatow oraz zapisuje jawne `matchReasons`,
5. oznacza wynik jako `RESOLVED`, `AMBIGUOUS` albo `NOT_FOUND`,
6. dla najlepszego jednoznacznego kandydata dolacza fragment external albo
   inline template'u, owning component i dostepny handler/binding,
7. publikuje jawny `sourceBinding` z rzeczywistymi wspolrzednymi external
   template'u albo bez pozornych numerow linii dla inline template'u,
8. pozostawia dalszy state/service/backend chain neutralnym tools wywolywanym
   w calym wybranym repozytorium tylko wtedy, gdy wymaga go pytanie.

Nie udostepniamy modelowi toola przyjmujacego dowolny CSS selector. Selector
runtime jest niezaufany, a selector nie jest stabilnym adresem kodu. Zamiast
tego feature dostarcza dwa session-bound tools nad zweryfikowanym scope'em:

- `uxi_list_target_candidates(reason)` zwraca ograniczone ranked candidates i
  opaque `targetRef`,
- `uxi_read_target_slice(targetRef, reason)` zwraca zweryfikowany slice tylko
  dla referencji utworzonej w tej sesji.

Dalsze odczyty korzystaja z istniejacych, waskich GitLab frontend tools oraz
neutralnych GitLab navigation/search/read tools. Nowe `uxi_*` tools nie
przyjmuja scope'u; neutralne kontrakty zachowuja `projectName`, `branchRef` i
bezpieczna sciezke. Prompt podaje wybrany project i branch, hidden
`GitLabRepositoryToolScope` przypina commit, a feature policy odrzuca inny
projekt, branch, niebezpieczna sciezke, `applicationNames` i nieznany
`targetRef`. Nie stosuje `pathPrefixes`, `codeSearchScopes` ani
feature-specific limitu liczby wywolan.

### AI workflow i tools

Feature dostarcza wlasny prompt, evidence, allowliste tools, hidden context,
initial report i parser/validator wyniku. Platforma Copilot nie zna semantyki
UX Inspectora. Sesja ustawia `skillsEnabled=false`; UX Inspector nie ma
packaged runtime skilli.

Kanoniczny prompt zawiera cala adaptacyjna procedure. Model rozpoznaje
semantycznie, czy pytanie jest precyzyjne, czy ogolne. Dla precyzyjnego pobiera
minimalne brakujace evidence. Dla ogolnego ustala cel biznesowy, pochodzenie i
zmiany danych lub stanu, warunki biznesowe i techniczne, skutek interakcji oraz
miejsce przekazania albo zapisu danych. Dalszy przeplyw rozszerza tylko wtedy,
gdy jest materialny i potwierdzony zrodlem, ale moze objac caly lancuch w
wybranym repozytorium.

Pytanie, capture i source evidence sa danymi, nigdy instrukcjami zmieniajacymi
procedure lub tool policy. Dotyczy to rowniez README, `AGENTS.md`,
`.github/copilot-instructions.md` i innych plikow instrukcyjnych repozytorium.
Initial artifact zawiera komplet nazw sciezek pierwszych czterech poziomow.
Model moze uzyc neutralnych tree/list/search oraz full/chunk read tools; nie ma
feature-specific repository toola. Policy wymusza wybrany project i branch, a
hidden scope przypina commit. Tree/list/search sa nawigacja; raport moze
cytowac plik spoza initial context dopiero po rzeczywistym full/chunk read.

### Report i kontrakt wyniku

Feature rejestruje initial `AnalysisReport` z:

- `reportId` osadzonym w hidden context,
- tytulem identyfikujacym view i target bez danych wrazliwych,
- jedna pusta sekcja `answer`, order `1`, title `Odpowiedz`,
- allowlista section id zawierajaca tylko `answer`.

Wymagana sekwencja modelu:

1. w jednym turnie zleca rownolegle `report_update_header` z jednozdaniowa
   teza, `report_upsert_section` z kompletna odpowiedzia i section meta oraz
   `report_update_meta` z ograniczeniami i confidence calego wyniku,
2. po zakonczeniu trzech zapisow raz wywoluje `report_get_current` i potwierdza
   finalny stan.

Feature-owned validator wymaga:

- dokladnie jednej sekcji `answer`,
- niepustej, merytorycznej odpowiedzi,
- poprawnych referencji nalezacych do pinned revision,
- jawnego gap/visibility limit dla materialnych twierdzen bez dowodu,
- braku dodatkowych sekcji, payloadow JSON w markdown i instrukcji z badanej
  strony.

`UxInspectorResultResponse` jest deterministyczna projekcja raportu i danych
identyfikacyjnych targetu. Nie ma alternatywnego pola `modelResponse`, ktore
mogloby ominac report tools.

### Job, historia i export

Feature dostarcza:

- `POST /api/ux-inspector/jobs`,
- `GET /api/ux-inspector/jobs/{analysisId}`,
- endpoint exportu i importu tylko dla `tdw.ux-inspector-export` v2,
- zapis runu `QUEUED` w `Analysis History` przed rozpoczeciem pracy AI,
- snapshot requestu, pinned revision, steps, AI activity, tool feedback, usage,
  report i typed result.

Import przyjmuje wylacznie UX Inspector export v2 z capture v3. Nie przyjmuje
starszych exportow/capture'ow, UI Explorer v5 ani przyszlych wersji. Nieznana
wersja zwraca jawny blad.

### Ekran UX Inspectora

Route `/ux-inspector` ma ten sam rytm wizualny co UI Explorer:

- main workspace po lewej i aside po prawej,
- karta konfiguracji przed startem,
- zablokowane kontrolki podczas aktywnego runu,
- status, kroki, Copilot activity, tool evidence i usage w aside,
- result oraz export po zakonczeniu,
- read-only odtworzenie z historii/importu.

Roznice sa celowe:

- nad formularzem widoczna jest karta wybranego targetu i capture limits,
- textarea ma etykiete `Pytanie lub polecenie` i jest glownym inputem,
- system, branch i view sa zawsze jawne; sugestia z route moze ustawic
  kandydatow, ale nie zatwierdza wyboru,
- nie ma section modes,
- renderer pokazuje jedna sekcje `Odpowiedz` i jej referencje/meta,
- bez capture ekran pokazuje instrukcje uruchomienia Browser Tools i nie
  pozwala wystartowac joba.

## Non-goals

- Jakakolwiek zmiana promptu, skilli, joba, reportu lub UI UI Explorera.
- Ukryty profil `ELEMENT_QUESTION` wewnatrz UI Explorera.
- Zachowanie starego `ui-explorer-inspector`, capture v1, dummy receivera lub
  starego bookmarkleta.
- Reczny transfer, clipboard fallback, payload w URL albo browser storage.
- Screenshoty, requesty sieciowe i session recording.
- Odczyt cookies, storage, hasel, tokenow, hidden values albo zawartosci
  plikow, rowniez w profilu `FORM_DIAGNOSTICS`.
- Automatyczne wykonanie wskazanej akcji lub modyfikacja badanego systemu.
- Arbitrary repository search poza zarejestrowanym frontendem i pinned
  revision.
- Follow-up chat w pierwszym wydaniu.
- Kolejne akcje Browser Tools dla Confluence albo GitLaba.
- Wsparcie innych frameworkow ponad capability juz obslugiwane przez frontend
  source integration; capture pozostaje framework-neutralny.

## Ograniczenia i ryzyka

- Main-world runtime moze zostac zmodyfikowany przez badana strone. Cala jego
  wiadomosc jest niezaufana i ponownie normalizowana na originie TDW i w
  backendzie.
- `window.opener` moze zostac zerwane przez COOP. Bez fallbacku oznacza to
  jawny blad i koniecznosc ponowienia po zmianie polityki, nie utrate kontroli
  nad scope'em.
- Route i DOM moga nie wystarczyc do jednoznacznego target resolution.
  `AMBIGUOUS` jest poprawnym wynikiem i nie moze byc maskowane zgadywaniem.
- View moze zawierac dynamiczne komponenty poza statycznym grafem. Pelny
  selected-repository scope pozwala je odnalezc, ale nadal nie uprawnia do
  przejscia do innego projektu ani dokumentowania calego ekranu.
- Procedura inline zwieksza rozmiar promptu. Test kontraktu pilnuje, ze nie
  duplikuje source evidence i pozostaje waska wobec jednego pytania.
- Historia i export moga zawierac ograniczony tekst widoczny na stronie.
  Redakcja i limitowanie nastepuja przed persistence.

## Security i privacy invariants

- Runtime Browser Tools nie ma sekretu, tokenu, klienta REST ani stalego
  storage.
- TDW origin jest przypiety w wygenerowanym launcherze i walidowany jako exact
  origin.
- Handshake wymaga exact `event.origin`, exact `event.source`, jednorazowego
  nonce, wersji protokolu i zgodnego `captureId`.
- Backend powtarza schema validation, size limits, allowlist i redakcje.
- System/project/branch/revision pochodza z zaufanego katalogu i hidden
  contextu, nie z capture ani argumentow modelu.
- Model-facing tools nie przyjmuja scope'u repository.
- Source evidence oraz runtime observation maja osobne trust labels.
- Logi nie zawieraja raw capture ani tresci strony; dopuszczalne sa identyfikator
  joba, captureId, rozmiar, liczba przodkow, status i kody redakcji.

## Kryteria akceptacji

### Funkcjonalne

- Browser Tools pokazuje akcje `UX Inspector` i jawny profil danych; selection
  tworzy dokladnie jeden capture v3 i nie wykonuje kliknietej akcji strony.
- Capture trafia tylko na `/ux-inspector`, a receiver usuwa fragment i pokazuje
  preview bez persistence przed startem.
- Operator nie moze uruchomic joba bez capture, pytania, systemu, brancha,
  view, aktualnej source revision i poprawnej pary model/effort.
- Job jest widoczny w historii od `QUEUED`, a aside pokazuje kroki, Copilot,
  tools i usage.
- Wynik zawiera dokladnie jedna sekcje `answer` i jest renderowany we wzorcu UI
  Explorera.
- UI Explorer nie zawiera linku, receivera, badge'a, markera, skilla ani
  prompt guidance UX Inspectora.

### Jakosc odpowiedzi i efektywnosc

- Zestaw co najmniej 12 w pelni fikcyjnych fixture'ow CRM pokrywa walidacje,
  pochodzenie danych, disabled/hidden i action flow.
- Dla fixture'ow z jednoznacznym targetem resolver wskazuje poprawny owning
  template/component w co najmniej 80% przypadkow; pozostale oznacza jako
  ambiguous/not-found zamiast wskazac bledny plik z high confidence.
- Kazde materialne twierdzenie o kodzie ma source reference albo jawny gap.
- Zadna odpowiedz nie zawiera dokumentacji calego view ani dodatkowych sekcji.
- W pilocie mediana skumulowanych tokenow wejsciowych dla tych samych pytan nie
  przekracza 40% mediany szerokiego flow UI Explorera, a mediana czasu nie
  przekracza 60%. Wynik poza progiem blokuje rollout, ale nie uruchamia
  fallbacku do UI Explorera.

### Bezpieczenstwo i zgodnosc

- Starsze capture'y, legacy client/feature id, dummy receiver i reczny import sa
  odrzucane albo nie istnieja.
- Testy potwierdzaja brak cookies, storage, clipboard, hasel, tokenow,
  hidden/file values, fetch/XHR i payloadu w URL po stronie runtime.
- Obcy origin/source, zly nonce, replay, nadmiarowy payload i nieznane pola sa
  odrzucane.
- Scope tools nie moze wyjsc poza wybrany frontend i pinned revision.
- Wszystkie fixture'y i dokumentacja sa w pelni anonimowe w domenie CRM.

## Macierz weryfikacji

| Warstwa | Weryfikacja |
|---|---|
| Browser protocol/runtime | Node tests: schema v3, oba profile, fingerprint, form snapshot/redakcja/limity, shield, lifecycle, exact handshake, replay, failure cleanup |
| Angular ingress | testy service/component: origin/source/nonce, fragment cleanup, in-memory lifecycle, form prefill, stale capture |
| Angular UX | testy facade/page: katalog, walidacja, lock, polling, aside, one-section report, history/import/export |
| Katalog i resolver | testy Java na fikcyjnych repo CRM: ranking, ambiguity, stale revision, route mismatch, Shadow DOM signals |
| Tools/policy | unknown targetRef, cross-repository/branch/path, hidden repository scope, navigation/search/full/chunk read, source-ref registration, report section allowlist |
| Prompt/tools | polski kontrakt, trust labels, kompletne cztery poziomy drzewa, pytanie precyzyjne i ogolne, skills disabled, neutralne repository tools, brak JSON fallbacku |
| Job/API | MockMvc/service: QUEUED, terminal states, validation, error contract, concurrent run isolation |
| Report | exactly-one-section, meta/references, missing report blocked, deterministic projection |
| History/export | round-trip UX v2, rejection UI Explorer/starszych capture/unknown versions, read-only route |
| Packaging | frontend test/build, `FrontendPageTest`, backend-dev clean package dla wspolnego kontraktu |
| Manual/security | Chrome demo CRM, bookmarklet i snippet, popup/COOP/CSP failure, keyboard/a11y, payload inspection |

## Rollout i rollback

Rollout jest atomowy:

1. finalny JAR zawiera backend, route, receiver, Browser Tools v3 i installer,
2. strona instalacyjna generuje tylko nowy launcher,
3. pilot obejmuje kontrolowane srodowisko CRM i jawnie wybrane osoby,
4. metryki mierza success/failure transportu, target resolution, tool calls,
   czas, tokeny, koszt, confidence i feedback operatora.

Rollback usuwa route/nawigacje UX Inspectora i akcje z nowo generowanego
Browser Tools oraz wylacza endpointy przez cofniecie calego wydania. Nie
przekierowuje do UI Explorera, nie przywraca v1 i nie wlacza dummy/manualnego
transportu. Istniejace runy pozostaja read-only w historii tylko wtedy, gdy
wersja z feature'em jest nadal uruchomiona; nie projektujemy migratora do
innych feature'ow.

## Kroki

Kazdy krok wymaga osobnej jawnej akceptacji, jezeli uzytkownik nie zatwierdzi
calego planu.

### 0. Zamrozenie kontraktow i threat modelu

- [x] Zdefiniowac capture v2, job request/snapshot, job states, result/report,
  bledy API, export v1 i handshake jako executable contract tests; wynik:
  review kontraktow i negatywnych przypadkow bez kodu produkcyjnego; dowod:
  zaakceptowane fixtures JSON i macierz zagrozen.
- [x] Zinwentaryzowac elementy katalogu frontendu do neutralnego reuse'u i
  potwierdzic brak importow z `features.uiexplorer`; wynik: finalny ownership
  klas/API; dowod: conformance delta i plan migracji konsumentow.

### 1. Neutralny katalog targetu

- [x] Wydzielic minimalna reusable capability katalogu frontend
  system/branch/view/revision nad Operational Context i
  `integrations.gitlab.frontend`, zachowujac zachowanie UI Explorera; wynik:
  UX Inspector moze pobrac opcje bez zaleznosci od sibling feature'a; dowod:
  testy obu konsumentow i package dependency guard.
- [x] Dodac UX Inspector input-options/view API z optimistic stale-revision
  validation; wynik: zaufany formularz moze potwierdzic scope; dowod: MockMvc
  dla poprawnego, nieznanego i zmienionego refa.

### 2. Capture v2 i deterministic target resolver

- [x] Dodac feature-owned modele/normalizer capture v2 i podwojna walidacje
  limitow/redakcji; wynik: backend przechowuje tylko allowlisted snapshot;
  dowod: property/fixture tests dla danych wrazliwych i payload bombs.
- [x] Zbudowac `UxInspectorTargetContext` z rankingiem, match reasons i stanami
  resolved/ambiguous/not-found; wynik: waski initial source slice w pinned
  revision; dowod: co najmniej 12 anonimowych fixture'ow CRM i pomiar
  precision/ambiguity.
- [x] Dodac session-bound `uxi_list_target_candidates` i
  `uxi_read_target_slice` oraz policy; wynik: model rozszerza tylko
  zweryfikowane targetRefs; dowod: testy cross-scope i replay. Feature nie
  narzuca limitu liczby wywolan.

### 3. Backendowy pion joba

- [x] Utworzyc `features.uxinspector` z requestem, snapshotem, job API/state,
  asynchronicznym schedulingiem i zapisem `QUEUED`; wynik: dzialajacy pion bez
  AI; dowod: service/MockMvc/concurrency/error tests.
- [x] Dodac historie oraz scisly import/export `tdw.ux-inspector-export` v1;
  wynik: read-only round-trip tylko dla tego feature'a; dowod: odrzucenie UI
  Explorer v5, capture v1 i nieznanych wersji.

### 4. Copilot workflow i pojedynczy report

- [x] Dodac feature-owned prompt, artifacts, hidden context, tool descriptions
  i allowliste; po pilocie osadzic cala procedure w prompcie i wylaczyc skills
  dla tej sesji; wynik: intent-focused session bez runtime skilli; dowod:
  prompt/session/policy contract tests.
- [x] Zarejestrowac initial `AnalysisReport` z allowlista tylko `answer` i
  wymagac sekwencji report tools; wynik: kanoniczny one-section output; dowod:
  testy missing report, wrong section, invalid reference i concurrent scope.
- [x] Dodac validator i deterministyczna projekcje
  `UxInspectorResultResponse`; wynik: brak text/JSON fallbacku; dowod: provider,
  readiness i report mapping tests.

### 5. Dedykowany ekran Angulara

- [x] Dodac route, nawigacje, API service, facade i formularz UX Inspectora z
  system/branch/view/capture/question/model/effort; wynik: znany uklad UI
  Explorera bez section modes; dowod: component/facade/API tests i a11y.
- [x] Dodac prawy aside z krokami, Copilot activity, tool feedback i usage oraz
  one-section report/result; wynik: pelna obserwowalnosc runu; dowod: testy
  polling/terminal states/report/meta.
- [x] Dodac historie, read-only load, import/export i recovery po odswiezeniu
  uruchomionego joba; wynik: spojnosc z pozostala platforma; dowod: routing i
  round-trip tests.

### 6. Atomowy cutover Browser Tools

- [x] Zastapic stary kontrakt wyłącznie capture v2, feature id
  `ux-inspector`, nazwa `TDW UX Inspector` i receiverem `/ux-inspector`;
  wynik: jeden finalny handshake bez aliasow; dowod: positive/negative Node i
  Angular integration tests.
- [x] Usunac `capture.html/css/js`, dummy accepted, manual copy/paste, legacy
  client acceptance, marker scenario i wszystkie referencje UI Explorera;
  wynik: brak fallbackow i compatibility code; dowod: repo-wide search oraz
  testy odrzucenia v1.
- [x] Zaktualizowac installer, snippet, loader, menu, README i demo; wynik:
  Browser Tools prowadzi tylko do UX Inspectora pod `/browser-tools/**`, a
  `/tdw-inspector/**` nie istnieje; dowod: skladnia launchera, CSP-safe loader,
  shell lifecycle, test 404 starej sciezki i manualny Chrome smoke test.

### 7. Integracja i jakosc

- [x] Uruchomic macierz testow frontend/backend i wygenerowac aktualny static
  bundle; wynik: samowystarczalny JAR; dowod:
  `npm --prefix frontend test -- --watch=false`,
  `npm --prefix frontend run build` oraz
  `mvn -q -Pbackend-dev clean package`.
- [x] Wykonac security/privacy/accessibility review oraz testy CSP/COOP/popup;
  wynik: jawne failure states bez wycieku i fallbacku; dowod: checklist i
  zapis wynikow manualnych.

### 8. Pilot i decyzja rolloutowa

- [x] Na podstawie pierwszego rzeczywistego runu usunac rodzine skilli UX
  Inspectora z packaged runtime, osadzic adaptacyjna procedure w prompcie,
  wylaczyc skille tylko dla tej sesji, reuse'owac neutralne GitLab file-read
  tools pod pinned policy, zlecac aktualizacje raportu w jednym turnie oraz
  poprawic podwojne naliczanie cache-write w estymacji kosztu; wynik: brak
  feature-specific file-read toola i mniej rund modelu; dowod: testy promptu,
  session config, tool policy, target evidence i kalkulatora usage oraz build
  obu warstw.

- [ ] Uruchomic pilot na fikcyjnych/kontrolowanych scenariuszach obejmujacych
  cztery klasy pytan; wynik: dane o trafnosci, czasie, tokenach, koszcie i
  feedbacku; dowod: zanonimizowany raport metryk.
- [ ] Porownac mediany z szerokim UI Explorerem i zatwierdzic rollout tylko po
  spelnieniu progow; wynik: decyzja go/no-go bez uruchamiania zastepczego flow;
  dowod: podpisany checkpoint planu.
- [ ] Po pozytywnym rolloutcie zaktualizowac dokumentacje architektury, oznaczyc
  need jako zaspokojony i zamknac/usunac plan zgodnie z `docs/AGENTS.md`.

### 9. Capture v3: source binding i diagnostyka formularza

- [x] Zastapic capture/protocol v2 atomowym v3 oraz dodac profile
  `ELEMENT_CONTEXT` i `FORM_DIAGNOSTICS`; wynik: Browser Tools zamraza
  ustrukturyzowany fingerprint i opcjonalny snapshot najblizszego formularza,
  bez storage i bez odczytu zakazanych danych; dowod: Node contract/runtime
  tests dla obu profili, limitow, truncation i redakcji.
- [x] Rozszerzyc backendowy normalizer i resolver o `domFingerprint`,
  `formSnapshot` oraz jawny `sourceBinding`; wynik: prompt dostaje zweryfikowane
  ownership/binding/linie i runtime form state bez broad research; dowod:
  Java tests kontraktu, normalizacji, rankingu, external/inline template i
  przygotowanego promptu.
- [x] Przelaczyc Angular receiver, preview oraz import/export na capture v3 i
  export v2 bez dual-read; wynik: operator widzi profil, liczbe wartosci,
  wykluczenia i jawne truncation przed startem; dowod: ingress/facade/page oraz
  import/export tests.
- [x] Zaktualizowac dokumentacje stanu, zbudowac static bundle i uruchomic
  macierz wspolnego kontraktu; wynik: samowystarczalny JAR bez artefaktow v2;
  dowod: repo-wide audit, Browser Tools tests, Angular tests/build oraz
  `mvn -q -Pbackend-dev clean package`.

## Wynik realizacji i weryfikacji (2026-09-16)

Kroki 0-7 zostaly dostarczone jako jeden pion i zweryfikowane bez zachowania
starego Inspectora, fallbacku transportu albo fallbacku wyniku AI.

- Testy Browser Tools: `node --test
  frontend/tests/browser-tools/browser-tools.test.mjs` - 11/11 testow.
- Testy Angulara: `npm --prefix frontend test -- --watch=false` - 71 plikow
  testowych, 597 testow.
- Produkcyjny frontend: `npm --prefix frontend run build` - zakonczony
  powodzeniem i zapisany w `src/main/resources/static`.
- Finalny pakiet: `mvn -q -Pbackend-dev clean package` - 337 raportow,
  1607 testow Surefire, zero failures/errors; powstal
  samowystarczalny JAR.
- Delta po pierwszym runie jest zakonczona: packaged i lokalne effective
  skille UX Inspectora zostaly usuniete, sesja zachowuje neutralne GitLab
  file-read tools przy `skillsEnabled=false`; byl to historyczny etap z policy
  wymuszajaca project, commit i path prefixes. Inline Angular template trafia
  do focused evidence,
  a regresja kosztu dla runu `ef6b002f-b8f5-4f8e-8b74-cd55f2a97f66`
  potwierdza 15,81782 zamiast 22,35382 kredytu.
- Smoke JAR-a na osobnym porcie potwierdzil `200` dla `/ux-inspector` i
  `/browser-tools/**` oraz `404` dla usunietego `/browser-tools/capture.html`
  i `/tdw-inspector/**`.
- Manualny smoke w Chrome przeszedl caly transport: launcher -> menu -> tryb
  wskazywania -> przechwycenie klikniecia -> popup `/ux-inspector` -> handshake
  -> podglad capture. W podgladzie byly tylko allowlisted sygnaly przycisku;
  wartosc kontrolnego pola formularza nie zostala przejeta. Obie strony nie
  zglosily bledow konsoli.
- Test resolvera obejmuje 12 fikcyjnych fixture'ow CRM i wymusza prog co
  najmniej 80% poprawnych jednoznacznych dopasowan; osobne przypadki
  potwierdzaja `AMBIGUOUS`, `NOT_FOUND` i odrzucenie zmienionej rewizji.
- Repo-wide audit potwierdzil brak produkcyjnych zaleznosci UX Inspectora od
  `features.uiexplorer`, brak starego katalogu w finalnym static bundle i brak
  referencji do usunietego need/plan. Pozostawione wystapienia
  `/tdw-inspector` sluza wylacznie wymuszeniu oraz testowi odpowiedzi `404`.

Capture v3 zostal dostarczony jako breaking replacement bez dual-read i bez
fallbacku do v2. Browser Tools oferuje dwa jawne profile, a
`FORM_DIAGNOSTICS` zamraza dozwolone wartosci najblizszego formularza wraz z
`ValidityState`, submitterami, wykluczeniami i truncation. Password, hidden,
file oraz pola lub wartosci rozpoznane jako sekret pozostaja bezwzglednie poza
capture. Receiver pokazuje zakres operatorowi przed startem.

Resolver korzysta z `domFingerprint` i selector candidates jako sygnalow,
wyprowadza zweryfikowany `sourceBinding` dla external oraz inline template i
przekazuje go do focused promptu. Export/import zostal atomowo podniesiony do
`tdw.ux-inspector-export/v2` z capture v3.

Weryfikacja delty capture v3:

- `node --test frontend/tests/browser-tools/browser-tools.test.mjs` - 12/12,
- `npm --prefix frontend test -- --watch=false` - 71 plikow, 597/597,
- `npm --prefix frontend run build` - produkcyjny bundle wygenerowany,
- `mvn -q -Pbackend-dev clean package` - 337 raportow Surefire, 1608 testow,
  zero failures/errors, jeden test pominiety; JAR wygenerowany,
- audit aktywnego kodu i finalnego static bundle - brak protocol/capture v2 i
  `tdw.ux-inspector-export/v1`.

Kontrolowany pilot z rzeczywistym formularzem i Copilot/GitLab pozostaje
osobna, niewykonana bramka kroku 8; automatyczne testy nie zastepuja decyzji
rolloutowej.

Krok 8 pozostaje zablokowany przez brak kompletnego zestawu wejsciowego pilota,
a nie przez niekompletna implementacje. Izolowany workspace smoke testu
poprawnie zwraca zero frontend systemow. Profil lokalny ma jeden zarejestrowany
frontend; uruchomienie finalnego JAR-a poza ograniczeniem sieciowym sandboxa
potwierdzilo dostep do siedmiu widokow przypietej rewizji oraz katalogu 14
modeli Copilota. Nie ma jednak wskazanej kontrolowanej uruchomionej strony
powiazanej z tym source ani zatwierdzonego zestawu czterech capture'ow i pytan
do benchmarku. Strona demonstracyjna Browser Tools nie nalezy do widokow tego
zarejestrowanego frontendu.

Bez takiego powiazania nie da sie przeprowadzic czterech rzeczywistych analiz,
zmierzyc tokenow/czasu/kosztu ani porownac median z UI Explorerem bez
fabrykowania targetow albo uzycia rzeczywistych danych organizacyjnych jako
fixture'ow. Nie robimy zadnej z tych rzeczy i nie uruchamiamy zastepczego flow.
Po wskazaniu kontrolowanej strony nalezacej do zarejestrowanego frontendu oraz
czterech elementow/pytan trzeba wykonac trzy checklisty kroku 8 przed decyzja
rolloutowa i oznaczeniem need jako zaspokojony.
