# Standard UI i kontrolek formularzy

## Zakres i ownership

Dokument opisuje obowiazujacy standard prezentacji Team Delivery Workspace.
Dotyczy ekranow analitycznych, Platform, Tool Workbench i Operational Context,
w tym filtrow, wyszukiwarek, chatu oraz tekstowych edytorow i podgladow kodu.
Kierunek produktowy opisuje [Product Direction](product-direction.md),
a zasady rozwoju [playbook feature'ow](analysis-feature-delivery-playbook.md).

| Element | Wlasciciel implementacji |
| --- | --- |
| Paleta, typografia, cienie i tokeny kontrolek | `frontend/src/styles.scss` |
| Wspolne powierzchnie, stany, focus, menu i opcje | `frontend/src/styles/_form-controls.scss` |
| Sidebar, topbar i stan nawigacji | `frontend/src/app/components/app-shell` |
| Przewodnik po formularzu startowym | `frontend/src/app/components/form-tour` i kroki po stronie ekranu |
| Oprawa pola, wybor, tekst i preferencje AI | `frontend/src/app/components/{form-field,choice-select,text-field,ai-execution-controls}` |
| Siatka formularza, dane, walidacja biznesowa i request | Konsumujacy ekran lub feature |

Komponenty formularzy nie pobieraja danych HTTP, nie importuja feature'ow
i nie wybieraja za nie regul biznesowych. Rozszerzenie wspolnego mechanizmu
wymaga sprawdzenia jego konsumentow; lokalny override nie powinien tworzyc
drugiego standardu tej samej kontrolki.

## Jezyk wizualny

- Domyślny obszar roboczy ma ciepłe jasnoszare tło, panele w złamanej bieli
  i neutralne obramowania. Powierzchnie, pola formularza i separatory
  korzystają ze wspólnych tokenów `--color-bg`, `--color-surface*`
  i `--color-border*`.
  Niebieski oznacza marke, wybor i interakcje; kolory statusow zachowuja
  znaczenie semantyczne.
- Sidebar i etykiety pól mają wagę 500. Aktywna pozycja nawigacji ma
  wagę 700. Etykiety pól zachowują zwykłą pisownię i standardowe odstępy
  między literami. Krótkie nagłówki grup mogą być pisane wielkimi literami.
- Panele korzystaja z subtelnych `--shadow-soft` / `--shadow-card`.
  Hover nawigacji lekko rozjasnia pozycje dostepne. Workspace Overview
  pokazuje karty funkcji w poziomej karuzeli z widocznym fokusem klawiatury.
  Na szerokim ekranie widać trzy pełne karty i fragment czwartej, a na wąskim
  jedną pełną i fragment kolejnej. Rząd przewija się gestem lub przyciskami w nagłówku.
  Cała karta prowadzi do funkcji.
- AI Skills jest lista wierszy z delikatnymi separatorami, neutralnym hoverem
  i widocznym fokusem klawiatury. Wiersze i ikony nie dostaja osobnych ramek
  ani cieni tworzacych karty wewnatrz karty.
- Czytelnosc opiera sie na hierarchii tekstu i odstepach. Nie dodawaj
  dekoracyjnych gradientow ani nowych paneli tylko dla efektu wizualnego.

## Motywy specjalne

Frontend dobiera motyw z rejestru w
`frontend/src/app/special-themes/special-theme.service.ts` według lokalnej
daty przeglądarki. Przedziały obejmują obie daty graniczne.
Otwarta karta przelicza harmonogram o północy i po odzyskaniu fokusu.

Wejście na `/workspace-settings?special=true` włącza motywy specjalne zgodnie
z harmonogramem, a `/workspace-settings?special=false` je wyłącza. Samo
`/workspace-settings` oraz inne wartości parametru zachowują dotychczasowy
wybór. Wyłączenie jest zapisywane dla wszystkich edycji w `localStorage`
pod kluczem `team-delivery-workspace.special-themes.disabled`.

Kolejna edycja wymaga wpisu z datami oraz zestawu tokenów w
`frontend/src/app/special-themes/_special-themes.scss`. Opcjonalne
`overviewBanner` zawiera nagłówek, życzenia i ścieżkę ilustracji dla
komponentu `app-special-theme-banner` w tym samym katalogu.
Zasoby należą do `frontend/public/assets/themes/<id>/`. Wybieraj rozłączne
przedziały. Oprawa dotyczy nawigacji, palety i banera Workspace Overview.
Ozdobny krój pisma stosuj w życzeniach, a tekst roboczy i kontrolki
utrzymuj w podstawowym kroju. Kolory statusów zachowują swoje znaczenie.

## Lewy panel nawigacji

- Domyślny panel ma granatowe tło i jasny znak TDW. Aktywna pozycja ma
  ciepłe jasne tło oraz granatowy tekst i ikonę. Motywy specjalne ustawiają
  paletę tymi samymi tokenami `--color-sidebar-*`. Paleta obejmuje też
  wariant zwinięty i mobilny.
- Kolejnosc kategorii to Analysis Features, Platform i Tool Workbench.
  Przy pierwszym otwarciu wszystkie sa rozwiniete. Kazda zapisuje swoj stan
  osobno w `localStorage`. Po zwinieciu calego panelu tryb ikonowy pokazuje
  pozycje tylko z rozwinietych kategorii.
- Przeciągnięcie całej pozycji zmienia jej kolejność w danej kategorii.
  Na ekranie dotykowym gest zaczyna się po krótkim przytrzymaniu. Fokus
  na linku pozwala zmienić jego kolejność skrótem Shift ze strzałką zgodną
  z kierunkiem listy. Każda kategoria zapisuje kolejność osobno w `localStorage`.
  Tryb ikonowy na desktopie odtwarza ustawioną kolejność.
- Chevron po prawej stronie naglowka pojawia sie na hover i przy fokusie
  klawiatury. Na urzadzeniach bez hovera jest stale widoczny.
- Tooltipy pozycji uzywaja domyslnego wygladu Angular Material i otwieraja
  sie po prawej stronie. Dostepne pozycje pokazuja nazwe w trybie ikonowym,
  a pozycje niedostepne pokazuja komunikat „coming soon”.
- Pionowy pasek przewijania jest widoczny tylko na hover panelu.
  Przewijanie i szerokosc zawartosci sa niezalezne od widocznosci paska.

## Przewodnik po formularzu startowym

„Oprowadź mnie” w topbarze otwiera przewodnik aktywnego ekranu. Ekran
implementuje `FormTourSource`, dobiera listę kroków do stanu formularza
i oznacza kontrolki atrybutem `data-form-tour` zgodnym z `anchor` kroku.
Teksty opisują rolę kontrolki i kolejność wypełniania danych.

Shell zbiera kotwice z aktywnej trasy. Wspólny dialog CDK przewija do
kontrolki, wyróżnia ją i ustawia obok panel z instrukcją. Kroki bez kotwicy są
pomijane. W grupie z `display: contents` wyróżnienie obejmuje kontrolki potomne.
Panel mieści się w widocznym obszarze przeglądarki. Między krokami panel i
podświetlenie płynnie zmieniają położenie. Systemowa preferencja ograniczenia
ruchu wyłącza tę animację. Dialog obsługuje Wstecz, Dalej, Zakończ, Escape i
zwrot fokusu po zamknięciu. Zmiana trasy zamyka przewodnik. Wartości pól i
wykonanie analizy pozostają decyzją operatora.

Przewodniki obejmują wszystkie dostępne ekrany Analysis Features.

| Ekran | Zakres przygotowania |
| --- | --- |
| Incident Analysis | Źródła logów, opis objawu i preferencje AI |
| Flow Explorer | Aplikacja, branch, katalog endpointów i zakres dokumentacji |
| UI Explorer | Aplikacja, branch, katalog widoków i zakres dokumentacji |
| UX Inspector | Instalacja Browser Tools przed capture oraz formularz po wskazaniu elementu |
| Change Verification | Jira, zakres weryfikacji, preferencje AI i uwagi operatora |
| Release summary | Aplikacja, branch, daty i podgląd dobranych commitów |
| Config Drift Viewer | Dostępny tryb Basic, repozytorium, komponenty i kierunek porównania |
| Behavior Change Assessment | Projekt Jira i okres dostarczonych zmian zachowania |
| Technical Change Assessment | Projekt Jira i okres dostarczonych zmian technicznych |
| Delivery Trends | Import CSV oraz filtry dostępne po wczytaniu danych |
| System Assistant | Import kontraktów OpenAPI, skille, preferencje AI i pierwsza wiadomość |

Kroki są dobierane do stanu ekranu. Puste katalogi Flow Explorera,
UX Inspectora i Release summary wskazują konfigurację źródeł. Wyłączenie
nowych analiz Release summary pozostawia przewodnik po imporcie.

## Dobor komponentu

| Potrzeba | Komponent i sposob uzycia |
| --- | --- |
| Prosty wybor z listy | `app-choice-select`; tablica `ChoiceOption[]` albo deklaratywne `app-choice-option` |
| Input, wyszukiwanie, liczba, haslo, URL, email, telefon | `app-text-field` z odpowiednim `type` |
| Wielowierszowy tekst | `app-text-field type="textarea"`; `rows` albo `autosize`, `minRows`, `maxRows` |
| Data | `app-text-field type="date"`; natywny kalendarz przegladarki |
| Model AI i reasoning effort | Jedna para `app-ai-execution-controls` |
| Wlasny lookup z wyszukiwaniem/paginacja | Istniejacy selektor aplikacji, brancha, endpointu lub widoku; wspolny `app-form-field` i mixiny kontrolek |
| Checkbox, upload pliku, edytor CodeMirror | Istniejaca wyspecjalizowana kontrolka |

`app-form-field` jest oprawa uzywana wewnatrz wspolnych kontrolek. Nie owijaj
ich druga oprawa i nie dubluj etykiety. Zlozony lookup zachowuje wyszukiwanie,
paginacje, loading i dotychczasowy sposob wyboru; prosty select nie zastepuje
tych zachowan.

## Parametry i zachowanie pol

`app-choice-select` i `app-text-field` przyjmuja opcjonalne `label`, `tooltip`,
`tooltipClass`, `hint`, `error`, a takze `inputId`, `ariaLabel`, `labelledBy`
i `describedBy`. Pole bez widocznego labela nadal musi miec dostepna nazwe.
Etykieta wskazuje rzeczywista kontrolke; opisy i bledy sa powiazane przez ID.
Tooltip jest pomoca, nie zamiennikiem etykiety ani widocznego bledu.

Obie kontrolki obsluguja Angular `ControlValueAccessor` (`FormControl`) albo
sterowanie przez `value` / `valueChange`. Wybierz jeden sposob zarzadzania
wartoscia. Odtworzenie wartosci i zaladowanie opcji nie sa nowym wyborem
operatora. Zachowaj `disabled`, `readonly`, dirty/touched, walidatory i moment
wyslania zmiany do feature'a.

- `ChoiceOption` zawiera `value`, `label` oraz opcjonalne `hint`, `disabled`,
  `badge`, `tooltip`. `description` opisuje zamkniety select, a `hint` opcji
  opisuje wiersz listy. Opcje sa plaskimi wierszami, bez kart i ramek.
- `resetAfterSelection` jest przeznaczone dla pickerow dodajacych referencje:
  emituje wybrana wartosc i zeruje widoczny wybor. Liste referencji utrzymuje
  ekran; nie dodawaj lokalnego obejscia resetu dropdownu.
- `app-text-field` emituje `valueChange` podczas edycji, z obsluga IME;
  `committed` odpowiada natywnemu zdarzeniu `change`. Migracja pola nie moze
  zamienic zatwierdzenia zmiany na zapis przy kazdym znaku.
- `app-text-field type="password"` może mieć jawne `passwordToggle=true`:
  domyślnie maskuje wartość, a przycisk oka obok inputu odsłania ją bez zmiany
  wartości, walidacji i dirty/touched. Wyłączenie kontrolki ponownie maskuje
  wartość. Toggle ma dostępną nazwę, tooltip i stan `aria-pressed`.
- Typ `number` przekazuje do `FormControl` liczbe albo `null`, natomiast
  `valueChange` i `committed` przekazuja tekst. Typ `date` zachowuje string
  `YYYY-MM-DD` bez konwersji przez `Date` lub strefe czasowa.
- Parametry pol zachowuja ograniczenia natywne i formularzowe, m.in.
  `required`, `min`, `max`, `step`, `maxLength`, `pattern`, `list`,
  `autocomplete`, `spellcheck` i `wrap`. Ekran posiada walidacje biznesowa.
- `appearance="code"` ustawia monospace. Autosize textarea zapewnia CDK;
  specjalizowane edytory CodeMirror zachowuja swoje zachowanie.

## Wymiary i stany interakcji

| Wariant | Wysokosc zamknietej kontrolki | Zastosowanie |
| --- | --- | --- |
| `standard` | 44 px | Zwykle inputy, filtry i proste selecty |
| `descriptive` | 58 px | Selecty z drugim wierszem opisu i pola w tym samym rzedzie |
| Model AI / reasoning | 58 px, `descriptive` | Takze bez opisu i przy braku wyboru lub disabled |
| Pasek chatu `compact` | 32 px | Jawny wariant shared AI controls, bez widocznych etykiet, z aria-label |

Wysokosc obejmuje pole, bez etykiety i hintu. Textarea zachowuje liczbe
wierszy lub autosize. Gestosc istniejacych edytorow mozna ustawic przez
`--field-min-height`, `--field-height`, `--field-max-height`,
`--field-font-size`, `--field-line-height`, `--field-padding-*`,
`--field-resize` i `--field-tab-size`. Wariant descriptive wyrownuje rowniez
wysokosc etykiety i odstep pod nia. Nie zmniejszaj lokalnie pol pary AI.

Ramka korzysta z `--control-border`, promien z `--control-radius`, a waga
wartosci z `--control-value-weight`. Hover, disabled, expanded i invalid
pochodza ze wspolnych mixinow. Disabled nie otrzymuje hoveru dostepnej akcji.

Focus formularza pochodzi z `controls.focus`: obrys 2 px w kolorze primary,
`outline-offset: -2px`, zgodny kolor border i `box-shadow: none`. Tworzy
jeden obrys wewnatrz pola bez zmiany wymiarow. Nie dokladaj zewnetrznego
pierscienia, ale nie usuwaj widocznosci fokusu klawiatury. Token `--focus-ring`
pozostaje dla przyciskow i innych interakcji poza polami formularza.

Select korzysta z Angular Material i jego obslugi klawiatury/overlay.
Globalne style listy sa ograniczone do `.choice-select-panel`; nie zmieniaj
wszystkich `.mat-mdc-option` w aplikacji. Zachowaj obsluge Escape, Tab,
strzalek, fokusu oraz `prefers-reduced-motion`.

## Wspolny wybor modelu i reasoning

`app-ai-execution-controls` jest wspolnym wlascicielem etykiet, tooltipow,
formatowania nazw i komunikatow. Korzystaja z niego Incident Analysis,
Flow Explorer, Change Verification, Config Drift Viewer (pola trybu DEEP),
UI Explorer, UX Inspector i Release summary. Nie zmienia dostepnosci trybu
DEEP. Oba assessmenty Delivery zachowuja model i reasoning konfigurowane
po stronie backendu; nie dodawaj im recznego wyboru AI.

Komponent otrzymuje katalog `AnalysisAiModelOptionsResponse`, `model`,
`reasoningEffort`, stany `loading`, `unavailable`, `error` i blokady wspolne
lub per pole. Emisje `modelChange` / `reasoningEffortChange` oznaczaja decyzje
operatora; `modelTouched` / `reasoningTouched` oraz `opened` pozwalaja
zachowac zachowanie formularza. Przy adapterze do FormControl ekran przekazuje
wartosci i blokady, oznacza dirty/touched oraz zachowuje subskrypcje zmian.
Host `display: contents` pozostawia oba pola w istniejacej siatce rodzica.

Feature pobiera katalog i status PAT, wybiera defaulty, resetuje lub
synchronizuje reasoning po zmianie modelu i buduje request. Wspolne utils
sa w `core/utils/analysis-ai-model-options.utils.ts`. Nie kopiuj do ekranow
mapperow nazw, opisow ani warunkow pustych komunikatow pary AI.

Nazwa modelu jest pokazywana raz, w jednym wierszu; techniczne ID jest
w tooltipie. Nazwy effort sa polskie, ale emitowane wartosci API pozostaja
oryginalne: `none` = Wyłączony, `minimal` = Minimalny, `low` = Niski,
`medium` = Średni, `high` = Wysoki, `xhigh` = Bardzo wysoki, `max` = Maksymalny.
Nieznany przyszly poziom zachowuje oryginalna nazwe.

| Stan | Komunikat |
| --- | --- |
| Trwa pobieranie | „Ładowanie modeli…” / „Ładowanie poziomów…” |
| Blad lub niedostepny katalog | „Katalog modeli niedostępny”; szczegoly bledu przy polu |
| Pusty katalog | „Brak dostępnych modeli” |
| Brak modelu i brak defaultu | „Wybierz model AI”; reasoning: „Najpierw wybierz model” |
| Model nie obsluguje reasoning | „Model nie udostępnia reasoning” |
| Brak poziomow dla modelu | „Brak dostępnych poziomów” |
| Pusta preferencja przy dostepnym default | „Domyślny: …”; bez nazwanego effort: „Ustawienie domyślne” |

Pusta preferencja backendu i jawny wybor katalogowego modelu Auto nie sa tym
samym. `allowDefaultSelection` pozwala wrocic do pustej preferencji; obecnie
korzysta z niego Release summary. Pozostale ekrany zachowuja swoje zasady
wyboru. Wartosci odtworzone z historii/importu spoza katalogu pozostaja
widoczne jako niedostepna opcja, bez automatycznej zmiany i emisji eventu.

## Zakladki wynikow

Wspolny `app-analysis-result-tabs` udostepnia opcjonalny slot `[tabActions]`
na akcje obok paska zakladek. Akcje sa poza `tablist` i panelem; nie zmieniaja
wyboru zakladki ani obslugi jej klawiatury. Feature dostarcza handlery, ikony,
tooltipy i dostepne nazwy. Brak slota pozostawia pelna szerokosc nawigacji.
System Assistant grupuje import, eksport i zapis całego szkicu we wspólnym
dolnym pasku konfiguracji, poza nagłówkiem zakładek.

Opcjonalne `equalWidth=true` rozdziela całą dostępną szerokość na równe
zakładki. Długie etykiety mogą zajmować dwa wiersze; minimalna szerokość
148 px zachowuje paginację Material na wąskim ekranie. System Assistant
używa tego wariantu. Domyślny układ pozostałych konsumentów pozostaje bez zmian.

`AnalysisResultTabsComponent` uzywa paginowanego naglowka Angular Material
(`MatTabNav`) i powiazanego `MatTabNavPanel`. Po przekroczeniu szerokosci
pojawiaja sie strzalki paginacji; nie dodawaj natywnego scrollbara ani
ujemnych marginesow laczacych zakladki z osobna karta. Naglowek ma 48 px,
neutralne tlo i jeden niebieski wskaznik aktywnej zakladki. Style korzystaja
z tokenow przez `mat.tabs-overrides`.

Ekran przekazuje `tabs`, `activeTabId`, `idPrefix`, `ariaLabel`, obsluguje
`tabSelected` i projektuje tylko aktywna tresc wewnatrz komponentu.
Material posiada role tablist/tab/tabpanel, roving tabindex i powiazania
ARIA; nie dodawaj drugiego tabpanel w ekranie. Strzalki i Home/End przesuwaja
focus, a Enter/Space zatwierdzaja wybor. Panel jest dostepny przez Tab.
Feature zachowuje stan wyboru i lifecycle tresci; shared nie pobiera danych
ani nie uruchamia pracy przy nawigacji.

`AnalysisResultTabsComponent` posiada nazwe zakladki i opcjonalny neutralny
`status` z `kind` (`loading`, `info`, `error`), `label` oraz `tooltip`.
Feature mapuje status domenowy na ten opis; shared komponent nie interpretuje
statusow joba. Brak `status` oznacza sama nazwe, bez pustego miejsca na ikone.

`loading` pokazuje loader 14 px i `aria-busy`; `info` i `error` pokazuja
pojedyncza ikone. Etykieta stanu pozostaje w dostepnej nazwie zakladki,
a Material Tooltip dziala na hover i focus calej zakladki, takze nad ikona.
Nie dodawaj zagniezdzonego przycisku do ikony. Loader respektuje
`prefers-reduced-motion`. Zmiana stanu nie zmienia wyboru ani powiazania
zakladki z panelem.

Config Drift Viewer uzywa tych wskaznikow dla komponentow batcha.
Incident Analysis (`AnalysisFinalResultComponent`) przekazuje nazwy sekcji
bez wskaznikow. W obu przypadkach uzywaj tego samego komponentu.

W Config Drift zakladki, kontekst aktywnego runu i jego wynik maja jedna
wspolna powierzchnie. Nad plikami jest jeden kompaktowy naglowek: kierunek
porownania, liczba zmian i plikow calego porownania, istotny komunikat o stanie,
przelacznik Zmiany/Caly plik i Share. Nie powtarzaj nazwy aktywnej zakladki
ani naglowkow Configuration result / Roznice w plikach. Metadane runu,
dokumentow, referencji i coverage pozostaja w tooltipie informacji,
dostepnym tez na focus. Niepelne dane sa widocznym komunikatem, nawet gdy run
jest zakonczony albo diff jest pusty. Kolumny FACT/AI nie tworza kolejnych
kart z cieniem. Przebieg pracy pozostaje w shared aside.

Ustalenia Config Drift sa prezentowane jako `Uwagi do porównania`:
czytelny tytul, opis i lokalizacja zamiast kodu oraz identyfikatora.
Karty i udostepniany wynik korzystaja z jednego deterministycznego mappera
feature'a. Szczegoly interpretacji i diagnostyki opisuje
[runtime flow](config-drift-viewer-runtime-flow.md). Sekcja znika przy zerowej
liczbie ustalen; pusty wynik filtra zachowuje naglowek, filtr i komunikat.

## Weryfikacja zmian

Dla zmiany wspolnej kontrolki sprawdz jej zachowanie i dotknietych konsumentow:
wartosci/defaulty, blokady, walidacje, dirty/touched, moment zapisu,
odtworzenie historii i importu. Testy selectow korzystaja z Material harness;
pomocnicze funkcje sa w `frontend/src/testing/form-controls.ts`.

Kontrola wizualna obejmuje formularz z sasiednimi selectami, focus inputu
i textarea, otwarte menu, dlugie nazwy, loading/error/disabled oraz waski
viewport. Zmiana wygladu nie powinna zmieniac siatki, kolejnosci pol ani
funkcjonalnosci bez osobnego wymagania.

Uruchom testy i build frontendu zgodnie z [root AGENTS](../../AGENTS.md).
Produkcyjny bundle w `src/main/resources/static` pochodzi z builda i jest
commitowany razem ze zrodlami. Nie edytuj wygenerowanych plikow recznie.

## Wskaźnik pracy agenta w chacie

Wspólny `analysis-follow-up-chat` pokazuje jeden loader 24 px na środku,
absolutnie 8 px nad formularzem wiadomości, podczas wysyłania lub odpowiedzi
AI ze statusem `IN_PROGRESS`. Dotyczy follow-upów i obu widoków System
Assistant. Loader pozostaje widoczny bez hovera, nie zajmuje miejsca
w układzie ani nie przechwytuje interakcji; respektuje `prefers-reduced-motion`
i ma dostępny opis. Pasek akcji wiadomości nie zawiera loadera.

## Kompaktowy chat operacyjny

Osadzony System Assistant używa palety Browser Tools: tło `#fbfcfe`, białe
powierzchnie i pola, chłodne szarości hover/disabled oraz obramowania
`#d6e0ed` / `#c7d7ee`. Warunkowe tokeny dokumentu obejmują też CDK overlay
selectów; osłona Browser Tools używa tej samej palety. Pełny ekran zachowuje
domyślne tła TDW. Zmiana palety nie modyfikuje układu ani działania kontrolek.

System Assistant używa wspólnego `analysis-follow-up-chat` z `compact=true`
w panelu Browser Tools i zakładce Rozmowa. Historia przewija się samodzielnie;
edytor (2–4 wiersze) i jeden wiersz akcji pozostają na dole. Brak powtórzonego
nagłówka i dużego empty state. Enter wysyła, Shift+Enter wstawia nową linię;
kompozycja IME i blokady wykonania nie wysyłają wiadomości. Historia podąża za
nowymi wiadomościami, dopóki operator nie przewinie jej do wcześniejszych.

Para AI ma jawny shared wariant `compact` (32 px, tekst 12 px), z zachowaniem
opcji i blokad. Nie zmniejszaj pary przez nadpisania w feature. Panel osadzony nie renderuje toku działania AI ani kosztów. Pełny ekran
/system-assistant prezentuje te dane w standardowym fixed aside, jak inne
feature’y. Nie ma specjalnego wariantu inline ani jego styli.


## Formularze Auth API

Zakładka Auth w System Assistant utrzymuje kilka profili bezpośrednio na stronie,
bez modali CRUD. Wspólne text-field i choice-select prezentują pola typu,
nazwy i poświadczeń. Hasło, token i client secret używają `type="password"`
oraz `passwordToggle=true`, z prezentacją oka obok pola jak w Workspace Settings.
Select przy przypisanym API w profilu ma domyślne No Auth.

Cztery zakładki konfiguracji System Assistant mają wspólny wzorzec listy:
licznik wpisów i Dodaj, bez powtarzania nazwy aktywnej zakładki. Nazwa wpisu
z pomocniczym opisem znajduje się po lewej, a
ikonowe akcje 32 px po prawej. Usuwanie jest zawsze ostatnią akcją wiersza.
Skille, Auth i Profile rozwijają edytor inline; Kontrakty zachowują podgląd
i eksport. Wewnętrzne separatory są zastąpione odstępami; delikatny hover
obejmuje całą belkę z obszarem akcji. Jedna subtelna linia oddziela listę od
sticky paska akcji; wiersze listy pozostają bez separatorów. Pasek grupuje
import, eksport i `Zapisz konfigurację` dla całego zestawu. Pokazuje komunikaty
oraz stan tylko niezapisanych zmian; zakres zapisu wyjaśnia tooltip.
Przełączanie zakładek zachowuje szkic.
