# Prowadzona klasyfikacja frontendu w asyście Operational Context

Status: done

Source need: [Prowadzona klasyfikacja frontendu w Operational Context](../needs/operational-context-guided-frontend-classification.md)

## Potrzeba / dlaczego

Obecny przegląd pozwala operatorowi wybrać `systemSubtype: frontend`, ale nie
pozwala uzupełnić wymaganego `repositoryType: frontend` w powiązanej
propozycji, jeśli AI go nie podało. Następnie zwraca nieczytelny kod
walidacji. Ręczny edytor zgłasza tylko ogólny błąd. Potrzebna jest jedna
prowadzona decyzja operatora i jasna diagnoza niezależnie od drogi edycji.

## Poziom i baseline

Poziom: L2, ponieważ zmiana obejmuje kontrakt feature'a oraz neutralną
odpowiedź maintenance API używaną przez ręczny edytor.

- `CREATE_AREA` przyjmuje `repositoryFacts` z rolą projektu, nazwą systemu,
  opcjonalnym sygnałem runtime i powiązanymi systemami. Nie ma typowanego
  wyboru subtype nowego systemu.
- Prompt i skill wymagają jawnego oświadczenia operatora dla `frontend`;
  sam odczyt plików Angulara nie wystarcza. Parser oczekuje obecnie
  `operator:description` i potwierdzenia dla pól klasyfikacji frontendowej.
- Draft może zawierać `systemSubtype: unknown` bez `repositoryType`.
  Przegląd może zmienić tylko pola obecne w draftcie; backend odrzuca inne
  `selectedPaths` i `editedValues`.
- Neutralny walidator wymaga dokładnie jednego scope'u systemowego i jednego
  repozytorium `primary` z `repositoryType: frontend` dla systemu
  `internal-service/frontend`.
- Batch preview podaje `ruleCode` i fingerprint. Ręczny `PUT` może zakończyć
  się ogólnym `INVALID_CANDIDATE` bez komunikatu o powiązanym repozytorium.
- Lokalna historia przechowuje draft, review i decyzje operatora; starsze
  nierozstrzygnięte runy muszą pozostać możliwe do wznowienia.
- Baseline 2026-09-26: testy `OperationalContextAssistanceDraftParserTest`,
  `OperationalContextAssistanceJobServiceTest`,
  `OperationalContextCatalogMaintenanceServiceTest` oraz
  `OperationalContextMaintenanceControllerTest` przeszły.

## Proponowane rozwiązanie

1. Dodać opcjonalny, jawny wybór subtype dla `DEPLOYED_SYSTEM` w typowanych
   `repositoryFacts`, z `unknown` jako wyborem, gdy operator nie wie. Prompt,
   polski skill i parser mają używać tego faktu jako podstawy klasyfikacji;
   zachować dotychczasowe jawne oświadczenie w opisie dla kompatybilności.
   Dla potwierdzonego `frontend` AI ma zaproponować spójne pola systemu i
   głównego repozytorium, a preflight odrzucić niekompletny zestaw przed
   pokazaniem go do przeglądu.
2. W przeglądzie istniejącego lub nowego draftu umożliwić jedną wąską
   poprawkę operatora: jeśli wybrany system staje się `frontend`, a wybrane
   repozytorium `primary` nie ma zaproponowanego `repositoryType`, pokazać
   przy nim wymagane `repositoryType: frontend` z jawnym potwierdzeniem.
   Backend dopuści tę dodatkową wartość wyłącznie dla zweryfikowanej relacji
   system–scope–repozytorium w tym samym batchu. Historia odróżni ją od
   oryginalnej propozycji AI. Pozostałe pola nadal muszą pochodzić z draftu.
3. Neutralne maintenance ma zwracać opis naruszenia i referencję do pola
   powiązanej encji dla niepoprawnego kandydata. UI pokazuje operatorowi
   konkretny komunikat i przejście do właściwej encji. Ta sama treść
   diagnostyczna ma być dostępna w batch preview, bez interpretowania kodów
   walidacji po stronie Angulara.

Alternatywa polegająca na zapisaniu systemu jako `unknown`, późniejszym
zapisie repozytorium i dopiero zmianie systemu wymaga od operatora znajomości
wewnętrznej kolejności. Automatyczne wnioskowanie `frontend` z frameworka
narusza zasadę jawnej klasyfikacji. Obie alternatywy zostają odrzucone.

## Zakres

- Formularz startowy i review asysty, prompt, skill, parser, preflight,
  składanie batcha i lokalne odtworzenie review.
- Neutralny wynik walidacji kandydata w maintenance i jego prezentacja w
  operator API oraz ręcznym edytorze.
- Testy backendu i Angulara oraz aktualizacja dokumentacji aktualnego flow.

## Non-goals

- Zmiana reguły `FRONTEND_PRIMARY_REPOSITORY_TYPE_MISMATCH` albo modelu
  katalogu.
- Automatyczna klasyfikacja z frameworka lub nazwy pliku.
- Zmiana budżetu GitLab, źródeł danych, uprawnień AI lub mechaniki zapisu YAML.
- Migracja już zapisanych wpisów katalogu.

## Ograniczenia i ryzyka

- Dodatkowe pole operatora musi mieć zamknięty zakres wartości i nie może
  tworzyć ogólnej możliwości dopisywania dowolnych pól poza draftem.
- Zmiana semantyki `selectedPaths`/`editedValues` wymaga kontroli zgodności
  historii, importu i eksportu. Starsze nierozstrzygnięte runy nie mogą
  stracić możliwości przeglądu.
- Komunikaty neutralnej walidacji nie powinny kodować na sztywno nawigacji
  jednego ekranu; API zwraca dane o encji i polu, a UI wybiera akcję.
- Nie zmieniać istniejących danych lokalnego katalogu ani niezwiązanej
  konfiguracji użytkownika w `application.properties`.

## Conformance delta i konsumenci

| Granica | Delta |
| --- | --- |
| API startu asysty | Opcjonalny typowany subtype tylko dla nowego wdrażanego systemu. |
| Prompt, skill, parser | Jawne fakty operatora; spójna para `systemSubtype` i `repositoryType`; bez inferencji z kodu. |
| Review, job, historia | Wąska poprawka powiązanego repozytorium w jednym batchu; starsze drafty zachowane. |
| Neutralne maintenance API | Szczegółowe naruszenie z encją i polem zamiast ogólnego błędu. |
| Katalog i zapis YAML | Bez zmian schematu i reguł; nadal warunkowy batch i walidacja całości. |
| Platforma, tools, hidden scope, budżet | Bez zmian. |

Konsumenci do sprawdzenia: asysta Operational Context, ręczny edytor i ekran
Validation, neutralne maintenance API, `FrontendApplicationCatalogService`
oraz UI Explorer konsumujący rejestr frontendów. Incident Analysis używa
katalogu do odczytu i nie powinien zmienić zachowania.

## Kryteria akceptacji

- Jawny wybór `frontend` w formularzu daje spójny draft systemu,
  repozytorium i scope albo czytelny blokujący powód przed przeglądem.
- Starszy draft bez `repositoryType` można naprawić i zatwierdzić w jednym
  batchu po potwierdzeniu operatora, bez ręcznego zapisu pośredniego.
- Usunięcie repozytorium lub scope'u z wybranego zestawu ponownie blokuje
  frontendową klasyfikację z jasną przyczyną; backend odrzuca podrobioną
  dodatkową wartość poza dozwoloną relacją.
- Ręczny edytor wskazuje główne repozytorium i jego wymagane pole przed
  zapisem systemu `frontend`.
- `unknown` pozostaje dozwolone, a istniejący zapis i odtwarzanie historii
  nie ulegają regresji.

## Kroki

- [x] Krok 1: Dodać typowany wybór operatora i spójność propozycji AI w
  formularzu, prompcie, skillu oraz parserze. Dowód: testy requestu, promptu,
  parsera, preflightu, joba i formularza Angular; brak klasyfikacji na
  podstawie samych plików źródłowych.
- [x] Krok 2: Umożliwić potwierdzoną poprawkę `repositoryType: frontend` dla
  powiązanego repozytorium w jednym review/batchu, także po wznowieniu
  starszego runu. Dowód: testy dozwolonej i odrzuconej poprawki, zapisu
  historii, preview, decyzji oraz UI przeglądu.
- [x] Krok 3: Zwrócić i pokazać szczegółowe naruszenia walidacji w ręcznym
  maintenance oraz batch preview. Dowód: testy neutralnej walidacji,
  kontrolera, edytora Angular i niezmienionej blokady niepoprawnego zapisu.
- [x] Krok 4: Zaktualizować kanoniczną architekturę i diagnostykę, sprawdzić
  konsumentów oraz wykonać weryfikację zmiany wspólnej: testy Angular,
  build Angular, `mvn -q -Pbackend-dev clean package`,
  `PackageDependencyGuardTest`, przegląd diffu i skan fikcyjnych danych CRM.
