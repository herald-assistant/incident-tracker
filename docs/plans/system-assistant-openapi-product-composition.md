# Import OpenAPI produktów z kompozycją schematów

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator chce importować rzeczywisty kontrakt API do System Assistanta,
wybrać niewielki zestaw operacji i używać formularzy oraz DTO produktów
opartych na kompozycji i polimorfizmie. Potrzeba produktowa wynika z rozmowy
o przekazanym `backend.json`; ten plan obejmuje rozszerzenie istniejącego
mechanizmu, nie nowy feature ani osobną integrację dla produktów.

Pierwszy zestaw akceptacyjny:

- `POST /clp/tty/order/product/{orderId}`, `addProduct`, query `cif`:
  request `oneOf` z 14 formularzami, które używają `allOf` z
  `DefaultProductDataForm`; odpowiedź `IdDto`.
- `GET /clp/pricing-orders`, `getOrCreatePricingOrders`, query `cif`:
  odpowiedź `PricingOrdersDto`, z produktami, zabezpieczeniami i mapami
  o typowanych wartościach.

W źródle nie znaleziono `/form-to-dto`, `/base-product-data` ani
`getDtoForPricing`; nie są założeniem tego planu.

## Baseline

Poziom zmiany: **L2**, wspólna integracja i narzędzia, konsumowane przez
System Assistant oraz jego neutralną ekspozycję MCP.

- UI przyjmuje YAML, backend obsługuje wykonawczy podzbiór OpenAPI 3.0.x.
- Preview kataloguje duże źródło; selection zachowuje tylko wybrane operacje
  i osiągalne components. Zapis konfiguracji publikuje zestaw.
- Importer rozwija lokalne acykliczne `$ref`; ręczny walidator odrzuca
  `allOf`, `oneOf`, `anyOf`, `discriminator`, `uniqueItems` oraz
  schema-valued `additionalProperties`.
- `OpenApiOperation` rozdziela schema walidacji od projekcji argumentów AI.
  REST waliduje argumenty przed HTTP. Schematy odpowiedzi są sprawdzane przy
  imporcie, lecz nie są argumentami narzędzia ani walidatorem runtime response.
- Katalog Auth jest przypisywany przez operatora poza argumentami modelu.
  Bieżący plik ma też metadane `name` w HTTP bearer securityScheme, które
  obecna ścisła lista pól odrzuca.
- Callbacki SDK i MCP reuse'ują ten sam wykonawca. Obecna klasyfikacja
  mutacji opiera się na metodzie HTTP i uznaje każdy GET za odczyt.
- Limity: 60 operacji na kontrakt, 100 łącznie, 100000 znaków na zapisany
  YAML, 2000000 znaków źródła, 64 KiB argumentów i odpowiedzi narzędzia.
- Dwa wskazane endpointy mają 244 osiągalne schematy. Sam podzbiór paths
  i schemas w zwartym JSON ma 134985 znaków, bez pozostałych metadanych.
  Pozostawienie limitu 100000 nie pozwoli dostarczyć tego scenariusza.
- Istniejące testy: `OpenApi*Test`, feature configuration/selection/chat,
  `SystemAssistantMcpWireTest`, frontend import/picker/viewer i architecture
  guards. Wynik próby baseline jest odnotowany poniżej.
- Zastana zmiana użytkownika w `application.properties` pozostaje poza
  zakresem. Podczas przygotowania planu nie zmieniono kodu produkcyjnego.

## Proponowane rozwiązanie

Jeden mechanizm importu JSON/YAML i obsługi OpenAPI 3.0/3.1, z zachowaniem
wersji dialektu źródła. Bez zamiany samego numeru wersji dokumentu.
Wspólna reprezentacja przygotowanej operacji oddziela kontrakt źródłowy,
walidację requestu i schemat argumentów narzędzia. Rozwiązanie pozostaje
neutralne domenowo w `integrations.openapi` i `agenttools.openapi`.

Kompozycja zachowuje ograniczenia składowych. `oneOf` wymaga dokładnie jednego
dopasowania; `anyOf` co najmniej jednego; discriminator opisuje wybór
wariantu, ale nie usprawiedliwia pominięcia walidacji. Znaczenie mapowań,
rekursji i rodzeństwa `$ref` zależy od dialektu i wymaga jawnego wsparcia.
Proponowanym kierunkiem jest biblioteka JSON Schema z kontrolowanym lokalnym
resolverem oraz nasza adaptacja OpenAPI; konkretny wybór nastąpi w kroku 1.

Schematów odpowiedzi nie przepuszczamy przez ograniczenia generatora
argumentów requestu. Zachowujemy ich dokumentację i referencje. Nie dodajemy
w tym zakresie nowej walidacji biznesowych skutków odpowiedzi HTTP.

Odrzucone alternatywy: ręczne utrzymywanie uproszczonego kontraktu produktów,
usuwanie ograniczeń schematów przy imporcie, dalsze dopisywanie całego
silnika kompozycji do ręcznego walidatora.

## Conformance delta i konsumenci

- Właściciel parsera, resolvera, walidacji i REST: `integrations.openapi`.
  Callbacki i MCP: `agenttools.openapi`; workflow i UI: System Assistant.
- Publiczne endpointy preview/selection/document pozostają; pola diagnostyki
  i metadanych skutków mogą wymagać zmiany po rozstrzygnięciach kroku 1.
- Konsumenci BE: import/selection/document, configuration validation/save,
  katalog tools, snapshot per tura, SDK callbacks, REST i scoped MCP.
- Konsumenci FE: importer pliku, picker, viewer, licznik operacji,
  konfiguracja/import-export V1 i prezentacja błędów.
- Schemat argumentów i walidacja obejmą kompozycję; Auth pozostaje poza AI.
  Prompt zachowa bieżący katalog oraz opis znaczenia argumentów; bez
  produktowych wyjątków w platformie Copilota i bez runtime skilla produktów.
- Transkrypt, NEW sesja per tura, profile zasobów i historia rozmów pozostają.
- Format konfiguracji ma jedną bieżącą wersję `1`. Jeśli zmieni się jego
  kształt, wszystkie aktualne konsumujące ścieżki zostaną dostosowane razem,
  bez migratorów, aliasów i równoległej starej implementacji. Sprzątanie
  dotyczy wyłącznie wskazanych danych zmienianego mechanizmu.
- Zmiana zależności obejmie wybrany silnik walidacji. Nie zmienia grafu
  ownership ani nie dodaje zależności integracji od feature'a lub SDK.
- Dokumentacja docelowa: runtime i instrukcja obsługi System Assistanta,
  odpowiednie lokalne AGENTS i opis schema projection w key decisions.

## Zakres

- JSON/YAML, OpenAPI 3.0/3.1, lokalne refs i wymagane przez zestaw słowa
  schematów, w tym kompozycja, typowane mapy i `uniqueItems`.
- Dokładna diagnostyka wybranego endpointu i wskaźnika schematu; niewybrane
  niewspierane operacje nie blokują poprawnego wyboru.
- Metadane `x-*` zachowywane bez traktowania ich jako instrukcji wykonania.
- Osobny limit rozmiaru kontraktu/schematu i budżetu projekcji narzędzi,
  ustalony pomiarem; limit liczby operacji pozostaje 60/100.
- Weryfikacja semantyki skutków `getOrCreatePricingOrders` przed użyciem go
  jako operacji tylko do odczytu; mechanizm neutralny, bez rozpoznawania
  nazw produktów lub heurystyki `getOrCreate` w wykonawcy.

## Non-goals

- Pełna obsługa wszystkich możliwości OpenAPI.
- PATCH, multipart, upload plików, zewnętrzne refs i nowy mechanizm załączników.
- Dodawanie brakujących w źródle endpointów lub automatyczna naprawa
  kontraktu na podstawie nazw klas i enumów.
- Nowy obowiązkowy approval workflow w produkcie, zmiana sesji SDK lub
  historia pełnych źródłowych specyfikacji.
- Wykonywanie zapisów w docelowym środowisku jako część testów automatycznych.

## Ograniczenia i ryzyka

Polimorfizm pliku wymaga audytu: występują `oneOf` z bazą i jej rozszerzeniami,
discriminatory bez jawnych mapowań oraz ograniczenia powtarzane w `allOf`.
Na przykład bazowe `amount.minimum=1` i minimum 0 w rozszerzeniu nadal
wymagają spełnienia obu ograniczeń. Zachowanie ograniczeń ma pierwszeństwo
przed łatwością importu. Niejasne mapowania wymagają dowodu z kontraktu lub
implementacji API; nie zgadujemy ich na podstawie nazw.

Mała liczba operacji nie oznacza małego grafu schematów. Powielenie refs
może znacznie powiększyć projekcję SDK/MCP; limity i diagnostyka muszą
obejmować także rozwinięcie schematów. Import bez udanego użycia narzędzia
nie jest dowodem dostarczenia scenariusza.

`getOrCreatePricingOrders` może mieć skutki zapisu mimo GET; nazwa nie
potwierdza implementacji. Do czasu rozstrzygnięcia nie używamy rzeczywistego
wywołania jako testu bezskutkowego odczytu. Nie dodajemy retry ani redirectów.

## Kryteria akceptacji

Operator może zaimportować źródłowy JSON, wybrać oba endpointy, obejrzeć
zależne modele i zapisać konfigurację. Bieżąca tura SDK oraz scoped MCP
otrzymują te same poprawne kontrakty argumentów bez poświadczeń.
Poprawny formularz obsługiwanego wariantu przechodzi do kontrolowanego HTTP,
a niepoprawny jest odrzucany przed HTTP z konkretnym powodem. Niejasna
semantyka źródła jest jawnie raportowana, bez pomijania ograniczeń.
Eksport/import V1 i ponowne uruchomienie odtwarzają ten sam aktualny zestaw.
Limit liczby operacji i pozostałe niezmienniki bezpieczeństwa pozostają.

## Decyzje kroku 1

Cały plan zatwierdzony przez użytkownika 2026-11-23. Baseline po uruchomieniu
z dostępnym loopbackiem: 22 testy, 0 failures/errors (target/openapi-baseline.log).
Wybrano networknt JSON Schema Validator 2.0.0 (Jackson 2, Java 17), lokalny
resolver i wyłączone pobieranie zasobów. Requesty mają ograniczoną projekcję
inline, odpowiedzi pozostają grafem refs w dokumencie. SDK Java przyjmuje
JSON Schema jako Object/Map; upstream Node potwierdza surowy JSON Schema,
ale nie gwarantuje zgodności każdego modelu. Dowodem transportu będą testy
serializacji SDK i MCP wire; live Copilot będzie raportowany osobno.

Nowe limity: zapisany kontrakt 1000000 znaków (oryginalny podzbiór YAML
znacznie większy niż 100000), projekcja argumentów 250000 znaków na tool,
1000000 łącznie w katalogu, 30000 węzłów/64 poziomy rozwinięcia requestu.
Limit operacji 60/100 i limit argumentów/odpowiedzi 64 KiB pozostają.

Nie znamy implementacji getOrCreatePricingOrders. Efekt domyślny operacji
pozostaje niepotwierdzony i jest traktowany konserwatywnie jak zapis, także
dla GET. Jawne x-tdw-operation-effect: READ_ONLY/MUTATION/UNKNOWN pozwala
operatorowi opisać potwierdzoną semantykę bez reguł opartych o nazwy.
Brak mapowania discriminator nie jest automatycznie naprawiany; diagnostyka
opisuje dokładną walidację oneOf i brak dowodu na mapowanie.

## Kroki

- [x] Krok 1: audyt i próba techniczna. Uruchomić baseline; zbudować inwentarz
  zależności obu operacji oraz macierz wariantów i konfliktów; ustalić
  semantykę discriminator i skutków GET z dostępnego kontraktu/kodu;
  porównać bibliotekę walidacji i schemat SDK/MCP na reprezentatywnych
  przypadkach. Sprawdzić upstream Copilot SDK/CLI, gdy lokalny artefakt nie
  wyjaśnia zachowania. Wynik: decyzja o silniku, projekcji, konkretnych
  limitach rozmiaru i metadanych skutków oraz lista blokad źródłowych.
  Weryfikacja: powtarzalny raport, pomiary i prototyp na lokalnym stubie,
  bez wywołań docelowego API. Akceptacja: brak niejawnych założeń; zmieniony
  zakres kolejnych kroków wymaga aktualizacji planu i zatwierdzenia.
- [x] Krok 2: parser i selekcja JSON/YAML 3.0/3.1, domknięcie lokalnych refs,
  metadane i nowe uzasadnione limity rozmiaru. Przygotować zanonimizowane
  fixture'y zachowujące rzeczywiste konstrukcje obu endpointów. Weryfikacja:
  selection/preview/document na obu formatach, niewybrane niewspierane
  operacje, błędne refs, limity i zachowanie adnotacji. Akceptacja:
  samodzielny kontrakt dokładnie wybranych operacji bez pełnego źródła.
- [x] Krok 3: wspólny silnik walidacji kompozycji i projekcja requestów do
  tools zgodnie z decyzją kroku 1, z zachowaniem readOnly, nullable i Auth.
  Usunąć zastąpione ścieżki ręcznej walidacji w tym samym zakresie.
  Weryfikacja: macierz wariantów, brakujące pola, błędny discriminator,
  nakładające się oneOf, przecięcia ograniczeń allOf, mapy, unikalność,
  cykle i budżety. Akceptacja: poprawne body przechodzi, błędne nie dociera
  do HTTP, schemat response nie jest argumentem requestu.
- [x] Krok 4: pełny lokalny przebieg narzędzi SDK/MCP i wykonania HTTP,
  w tym uzgodniona neutralna klasyfikacja skutków operacji. Weryfikacja:
  wire tests, kontrola liczby wywołań, Auth poza argumentami, błędy przed
  wysyłką, timeout bez retry i blokada kolejnego niepewnego zapisu.
  Akceptacja: oba kanały zachowują ten sam kontrakt i politykę wykonania.
- [x] Krok 5: UI importu `.json/.yaml/.yml`, diagnostyka wyboru, podgląd
  kompozycji i poprawny zapis/import-export bieżącego V1. Reuse istniejącego
  pickera i kontrolek. Weryfikacja: testy frontendowe importer/picker/viewer/
  konfiguracja, build i kontrola wizualna. Akceptacja: wybór i zapis dwóch
  operacji działa, anulowanie nie aktywuje ani nie przechowuje źródła.
- [x] Krok 6: regresja wszystkich konsumentów, demonstracja scenariusza
  na kontrolowanym API i aktualizacja dokumentacji docelowej. Weryfikacja:
  testy integracji, feature'a, MCP, architecture guards, frontend i test
  odtworzenia konfiguracji. Ewentualny test z rzeczywistym Copilotem
  raportować osobno od testów stubowanych. Akceptacja: dowody dla całego
  przebiegu i jawne ograniczenia; status done dopiero po wykonaniu zakresu.

## Weryfikacja przygotowania planu

Inwentarz zależności wykonano tylko do odczytu na pliku operatora, bez
kontaktowania się z docelowym serwerem. Próba baseline:
`mvn.cmd -o -Dfrontend.skip=true -Dtest=OpenApi*Test,SystemAssistantOpenApiSelectionTest,SystemAssistantMcpWireTest test`.
Kompilacja testów przeszła. `SystemAssistantMcpWireTest` zgłosił 3 błędy
inicjalizacji kontekstu: JDK HTTP client nie mógł zestawić połączenia
loopback (`SocketException: Permission denied: connect`). Proces przerwano
po dalszym braku postępu; cały zestaw nie został zweryfikowany. Starsze
raporty pozostałych testów nie są dowodem tej próby. Powtórzenie baseline
w środowisku z dostępnym lokalnym HTTP należy do kroku 1.


## Dowody wykonania

- Kroki 1–4: `target/openapi-regression.log`: 72 testy, 0 failures/errors,
  1 pominięty opt-in live Copilot. Zestaw obejmuje OpenApi*, SystemAssistant*,
  IntegrationPackageBoundaryTest i PackageDependencyGuardTest. Testy SDK
  potwierdzają serializację parameters, MCP wire pełne wywołanie i guard
  po nieustalonym GET, a test persistence odczyt dużej konfiguracji V1.
- Testy kompozycji korzystają z fikcyjnej fixture: 4 schematy, dwa endpointy,
  oneOf/allOf/discriminator i dwa warianty ze wspólną bazą. Syntetyczny
  przypadek nakładających się wariantów chroni przed zgadywaniem mapping;
  dane graniczne są generowane. Skopiowany podzbiór kontraktu operatora
  usunięto zgodnie z planem text responses i przeglądu anonimizacji.
- Kontrolowany kontrakt dwóch poprawnie rozróżnionych wariantów potwierdza
  wysyłkę poprawnych formularzy, odrzucenie błędów przed HTTP, przecięcie
  size.minimum, typowane mapy, uniqueItems i readOnly.
- Build frontend: `target/openapi-frontend-build.log`, sukces; zastane
  ostrzeżenie budżetu platform-landing.scss (38.57 kB wobec 32 kB).
- Kontrola wizualna na produkcyjnym bundle z lokalnym testowym API:
  import pliku JSON, dwa endpointy w Swagger UI, zaznaczenie, uwagi i
  dostępny przycisk użycia wyboru. To kontrola UI ze stubem, nie wywołanie
  docelowego API. Początkowy timeout kontroli przeglądarki minął przy ponowieniu.
- Plik importu całej konfiguracji ma limit 100000000 bajtów, aby obsłużyć
  12 dokumentów do 1000000 znaków również po JSON escaping i UTF-8.

- Krok 5: `target/openapi-frontend-final-tests.log`: 88 testów, 10 plików,
  wszystkie przeszły. Kontrola wizualna potwierdziła układ modalu i uwag.

- Krok 6: `target/openapi-final-regression.log`: 73 testy, 0 failures/errors,
  1 pominięty live Copilot (72 wykonane). Nowy test potwierdza limit projekcji
  i odrzucenie wykładniczego rozwinięcia refs. Końcowe FE: 88/88, build
  produkcyjny przeszedł, entrypoint wskazuje wyłącznie istniejące assety,
  `git diff --check` bez błędów. Lokalny serwer QA został zatrzymany.
- Nie wykonywano wywołań docelowego API ani sesji prawdziwego modelu.
  Bez dowodu implementacji skutki getOrCreate pozostają UNKNOWN. Oryginalny
  nakładający się oneOf wymaga korekty po stronie źródła, zanim będzie
  można uznać formularz za jednoznaczny; obsługa nie obchodzi tego ograniczenia.
- Plan pozostaje do przeglądu zakresu i wyników; trwały kontrakt zapisano
  w runtime, instrukcji obsługi i instrukcji tworzenia konfiguracji.


## Poprawka prezentacji katalogu po weryfikacji operatorskiej

Lokalny bugfix L0: pełna lista top-level tags powodowała puste grupy Swaggera
na stronie zawierającej tylko 50 operacji. Projekcja przekazuje teraz wyłącznie
deklaracje tagów używanych na bieżącej stronie, zachowując ich opisy i pełne
źródło. Paginacja pokazuje zakres operacji oraz liczbę po filtrach.
Weryfikacja: 7/7 testów pickera (`target/openapi-pagination-tests.log`), build
produkcyjny i przeglądarka na pełnym zanonimizowanym źródle: 224 operacje,
5 stron, na pierwszej 50 operacji w 14 niepustych grupach; wyszukanie
addProduct z ostatniej strony pokazuje właściwy endpoint. Kontrola wizualna
korzystała z lokalnego testowego API. Nie rozszerza obsługi PATCH ani limitu
64 znaków nazwy narzędzia.

## Kolejny zatwierdzony zakres

Obsługę PATCH zlecono osobno po zakończeniu tego planu. Bieżący inkrement
i dowody opisuje [plan PATCH](system-assistant-openapi-patch.md).
