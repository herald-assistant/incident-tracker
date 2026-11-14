# Frontend Components AGENTS

## Cel

Ten katalog zawiera wspolne komponenty UI, w tym `app-shell`, `app-brand` i
komponenty prezentacji analizy. Komponent wspolny powinien byc reusable w
ramach `Team Delivery Workspace`, a nie zakodowany pod pojedynczy route, jesli
nie jest to jawnie komponent analizy.

Komponenty dla powtarzalnych elementow analitycznych sa preferowanym miejscem
dla UI/UX wspolnego miedzy feature'ami: przebieg runu, tok pracy AI, follow-up
chat, evidence/tool feedback, usage/cost, import/export i powtarzalne fragmenty
prezentacji wyniku. Feature screen powinien dostarczac dane, copy i handler
akcji, ale nie duplikowac struktury, zachowan ani styli tych samych elementow.

## Kontrolki formularzy

Kanoniczny kontrakt i mapa konsumentow sa w
[standardzie UI](../../../../docs/architecture/frontend-ui-standards.md).
Zmieniaj wspolny komponent/mixin i weryfikuj konsumentow zamiast dodawac
feature-specific kopie albo nadpisania styli.

- Dla prostego wyboru uzywaj `app-choice-select` z `options` (value, label,
  opcjonalne hint, disabled, badge, tooltip) albo deklaratywnymi
  `app-choice-option` dla opcji budowanych w szablonie. Nie tworz osobnego
  dropdownu model/effort. `resetAfterSelection` sluzy pickerom dodajacym
  referencje: emituje wybor i przywraca pusta pozycje.
- Dla pary model AI / reasoning uzywaj `app-ai-execution-controls`.
  Przekaz katalog, wartosci, loading/error i blokady; komponent posiada
  etykiety, polskie nazwy poziomow, komunikaty i wariant descriptive 58 px,
  taki sam jak sasiednie selektory aplikacji, branchy i zakresu.
  Nazwa modelu zajmuje jeden wiersz, ID jest w tooltipie. Nie tworz lokalnych
  mapperow nazw ani opisow tych pol. Feature nadal pobiera katalog, wybiera
  defaulty, synchronizuje reasoning po zmianie modelu i buduje request.
  Komponent emituje tylko wybor operatora; nie nadpisuje preferencji podczas
  ladowania lub odtwarzania historii. `allowDefaultSelection` wlaczaj tylko
  tam, gdzie formularz pozwala wrocic do pustej preferencji backendu.
  Dla FormControl polacz tez dirty/touched oraz disabled; sama para nie
  przejmuje ownershipu formularza. Host display:contents zachowuje siatke.
- Dla tekstu, wyszukiwania, textarea, daty, liczby, hasla i URL uzywaj
  `app-text-field` z `type`.
  Oba komponenty obsluguja FormControl albo value/valueChange oraz opcjonalne
  label, tooltip, hint i error. Tekstowe pole `committed` odpowiada natywnemu
  change; data pozostaje stringiem YYYY-MM-DD, bez konwersji strefy.
  Pole number przekazuje do FormControl liczbe albo null; valueChange
  i committed przekazuja tekst do handlerow feature'a. Natywne ograniczenia
  i walidatory formularza sa zachowane. `autosize`, `minRows` i `maxRows`
  wlaczaja wspolna obsluge CDK; `appearance="code"` ustawia font monospace.
- `app-form-field` odpowiada za wspolna oprawe i identyfikatory opisow/bledow.
  Dla pola bez widocznej etykiety podaj ariaLabel lub labelledBy. Zewnetrzna
  etykieta powinna wskazywac inputId; describedBy dolacza istniejace opisy.
  tooltipClass pozwala zachowac format rozbudowanej pomocy.
  Feature dostarcza wartosci,
  opcje i komunikaty; nie przenos do kontrolek requestow ani reguł biznesowych.
- Wariant standard ma 44 px, descriptive 58 px i moze miec drugi wiersz
  opisu. Dobieraj wariant do istniejacej gestosci formularza.
  Gestosc edytorow i wysokosc pol kodowych mozna zachowac przez zmienne
  --field-min-height, --field-height, --field-max-height, --field-font-size,
  --field-line-height i --field-padding-*. Nie kopiuj obramowan ani focus ring.
- Fokus kontrolek formularza korzysta z mixinu `controls.focus`: pojedynczy
  obrys 2 px wewnatrz pola, bez zewnetrznego pierscienia i bez zmiany wymiarow.
  Zachowaj jego widocznosc przy obsludze klawiatura.
- Zlozone lookupy zachowuja wyszukiwanie i paginacje; reuse'uja oprawe oraz
  mixiny `src/styles/_form-controls.scss`. Style overlay selecta pozostaja
  ograniczone do klasy `choice-select-panel` w globalnym arkuszu.
- Standard dotyczy takze Platform, Delivery, Tool Workbench, Operational
  Context, filtrow, wyszukiwarek i pol chatu. Checkboxy, upload plikow
  i CodeMirror pozostaja wyspecjalizowanymi kontrolkami.

## App shell

`app-shell` jest jedynym wlascicielem:

- glownego sidebaru,
- kontekstowego topbaru,
- breadcrumbow,
- capability info tooltipa,
- zwijanego raila nawigacji.

Nie dodawaj drugiego topbaru w feature screen. Jezeli widok potrzebuje
statycznego opisu capability, dodaj go do route data jako `capabilityInfo`.

## Dostepnosc i gestosc UI

- Zakladki wyniku reuse'uja `app-analysis-result-tabs` z paginacja Material.
  Przekaz dane/aktywny ID i projektuj aktywna tresc do komponentu; Material
  posiada powiazanie z panelem i obsluge klawiatury. Nie przywracaj lokalnego
  scrollbara, recznego tabpanel ani osobnego wariantu naglowka.
  Feature mapuje stan domenowy na neutralny `status` zakladki
  (`loading`, `info`, `error`, label i tooltip); shared nie interpretuje
  statusow joba. Brak statusu nie rezerwuje miejsca na ikone. Zachowaj
  opis dla czytnika ekranu, tooltip na hover/focus i reduced motion loadera.
- Icon-only controls musza miec `aria-label` albo `title`.
- Tooltipy maja wyjasniac skrocone dane, ale nie moga zaslaniac glownego
  workflow.
- Tekst w kontrolkach nie powinien byc ucinany bez sensownego tooltipa albo
  alternatywy.
- Shared components powinny uzywac tokenow CSS, a nie lokalnych jednorazowych
  kolorow.
- Shared components powinny byc parametryzowane tekstem i stanami, ale miec
  jeden znany model interakcji. Nie tworz wariantu tylko po to, zeby feature
  mial inna etykiete albo inny pusty komunikat.
- Panel krokow otrzymuje `preparedPromptStepCode` od feature'a; nie dodawaj
  hardcoded kodow promptu. Para `beforeContent` / `afterContent` w evidence
  pokazuje pelny diff przez neutralny `core/utils/text-diff.utils`, wspolny
  z podgladem zmian raportu w chacie. Brak odczytu nie jest pustym plikiem.
- `app-gitlab-branch-select` zachowuje systemowy lookup dla Flow/UI Explorer;
  inny feature moze podac `sourceKey` i `branchLoader`, aby uzyc tej samej
  interakcji dla jednego projektu bez zaleznosci shared UI od API feature'a.

## Czego unikac

- Nie tworz komponentow marketingowych hero dla narzedzi roboczych.
- Nie buduj kart w kartach.
- Nie przenos logiki backendowego scope'u albo Copilot SDK do komponentow UI.
- Nie duplikuj brandu albo navigation state poza `app-shell`.

Selektor refow moze otrzymac `showCommitDate=true` oraz metadane committedAt
z datami per repozytorium. Wtedy zamiast rodzaju refa pokazuje dd.MM.yyyy,
bez godziny, z tooltipem znaczenia daty. Inni konsumenci bez tej opcji
zachowuja dotychczasowe badge. Przy paginacji scalaj metadane tej samej nazwy
ze wszystkich zaladowanych stron, bez gubienia repozytoriow i dat.
