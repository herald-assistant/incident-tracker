# Ręczna edycja wyniku analiz z follow-up chatem

Status: in-progress

Source need: [Ręczna korekta wyniku analizy](../needs/manual-analysis-result-editing.md)

## Potrzeba / dlaczego

Operator musi móc poprawić wynik bez proszenia AI o redakcję. Następny turn
follow-up musi uwzględniać zapisany tekst. Obecny odczyt pełnej sekcji przez
`report_get_current(sectionId)` może przekroczyć limit odpowiedzi Copilot SDK;
model dostaje wtedy odwołanie do niedostępnego dla niego pliku.

## Klasyfikacja i baseline

Zmiana jest L3: dotyka wspólnego kontraktu raportu, publicznych operacji
mutujących wynik, trwałego snapshotu i wznowienia sesji AI.

- Incident Analysis, Flow Explorer, UI Explorer i UX Inspector mają
  `AnalysisReport` oraz follow-up chat. Inne ekrany z przyciskiem Share nie są
  konsumentami tej zmiany.
- Raport jest źródłem bieżącego rezultatu, ale każdy feature projektuje go na
  własne DTO wyniku. Job i lokalna historia przechowują oba obrazy.
- Live chat wznawia istniejącą sesję i przypina aktualny raport do
  efemerycznego `CopilotReportSessionStore`. Lokalna kontynuacja robi to samo
  po odczycie prywatnego snapshotu. Importowany run nie ma kontynuacji.
- `report_get_current` zwraca zwarty manifest, lecz z `sectionId` dokłada pełne
  body. Cztery feature'y obecnie instruują model, aby tak czytał sekcję przed
  zmianą. `report_patch_section` używa digestu i odrzuca nieaktualny tekst.
- Na ekranach Incident, UI Explorer i UX Inspector przycisk Share jest we
  wspólnym `analysis-result-header`; Flow Explorer umieszcza go w lokalnym
  nagłówku. Wspólne są renderer Markdown i follow-up chat.
- Nie ma ręcznej mutacji raportu, znacznika pochodzenia ani konfliktowej
  kontroli zapisu. Istniejący guard serializuje operacje lokalnego runu.
- Test bazowy `mvn -q -Dtest=CopilotReportToolsTest test` nie wystartował:
  sandbox nie mógł pobrać brakującego parent POM Spring Boot 3.5.11 z Maven
  Central. To ograniczenie środowiska, nie wynik testów aplikacji.

## Conformance delta i konsumenci

| Obszar | Zamierzona zmiana |
| --- | --- |
| Publiczne API i DTO | Dodanie warunkowego zapisu treści raportu dla czterech live jobów i kontynuowalnego lokalnego runu; odpowiedź zawiera aktualny snapshot. |
| Context i evidence | Bez ponownego pobierania evidence. Ręczny tekst jest jawnie oznaczony jako korekta operatora, nie jako dowód AI. |
| Prompt, skills | Krótka informacja o zmianie w następnym follow-up turnie; instrukcja odczytu bieżących sekcji fragmentami. Bez wklejania całego raportu do promptu. |
| Tools, policy i hidden scope | Neutralny, tylko do odczytu tool sekcji w małych fragmentach; ta sama ukryta tożsamość raportu i allowlista sekcji. Dopuszczenie w follow-up czterech feature'ów. |
| Report i wynik | Zapis tylko istniejących pól Markdown; zachowanie identyfikatorów, kolejności i metadata sekcji. Ponowna projekcja i walidacja feature-specific DTO przed atomowym zatwierdzeniem. |
| Job, historia, export | Znacznik ostatniej ręcznej rewizji, czas i zmienione części; spójny zapis raportu oraz DTO w live i local. Migracja brakującego znacznika do stanu „bez korekty”. |
| Shared FE/UX | Ikona edycji obok Share, wspólny edytor i podgląd przez obecny renderer; bez osobnego pill po ręcznej korekcie. |
| Zależności | Platforma i shared nie importują feature'ów; każdy feature zachowuje własną projekcję i walidację. |

Konsumenci wymagający audytu: cztery kontrolery/job state/chat handlery, cztery
local continuation/export/import ścieżki, `AnalysisRunHistoryService`, report
tools/factory/policy, cztery result views i ich modele/API, wspólne Share,
result header, renderer, parsery lokalnych eksportów oraz testy architektury.
Config Drift Viewer i pozostałe ekrany Share pozostają konsumentami tylko
niezmienionego renderowania raportu.

## Proponowane rozwiązanie

Edytor pracuje na strukturze `AnalysisReport`: wybór istniejącej sekcji oraz
opcjonalnego `markdownSummary`, pole Markdown, Podgląd, Zapisz i Anuluj.
Podgląd używa tego samego renderera co wynik. Nie parsujemy wyeksportowanego
dokumentu Markdown z powrotem do struktury raportu. Nagłówki, referencje,
limity, confidence, kolejność oraz identyfikatory sekcji pozostają poza
edycją w tym inkremencie.

Ścieżka operacji identyfikuje run, a żądanie zapisu przekazuje tylko zmienione
pola i oczekiwany digest bieżącego raportu. Backend odrzuca pustą/nieznaną sekcję,
nieaktualny digest, stan z aktywną odpowiedzią AI i run bez kontynuacji.
Przed zatwierdzeniem tworzy nowy raport i projektuje go przez walidator danego
feature'a. Zapis raportu, DTO wyniku i znacznika rewizji jest atomowy w granicy
job state albo operacji local run. Żądanie nie przyjmuje metadata ani scope'u
repozytorium. Rozmiar wejścia ma jawny limit aplikacyjny uzgodniony z
istniejącymi limitami HTTP i eksportu, bez limitowania odczytu przez model do
jednej odpowiedzi.

Znacznik ręcznej rewizji przechowuje numer, czas oraz identyfikatory
wszystkich ręcznie zmienionych części, aby kilka poprawek przed follow-up
nie ukryło wcześniejszej zmiany. Trwa w historii i eksporcie bez osobnego pill przy wyniku;
stare snapshoty i eksporty otrzymują wartość domyślną. Zapis nie zmienia
`preparedPrompt`, evidence, usage ani historii odpowiedzi AI. Wskazanie
manualnej korekty nie oznacza, że AI ją zweryfikowało.

Nowy platformowy `report_read_section_chunk` czyta tylko dozwoloną sekcję z
ukrytego report scope. Przyjmuje `sectionId` i numer fragmentu; zwraca
Markdown o ograniczonej liczbie bajtów UTF-8, digest całej sekcji, numer
następnego fragmentu i znacznik końca. Odpowiedź wraz z JSON ma pozostać
wyraźnie poniżej 8 KB. `report_get_current(sectionId)` dla dużego body
powinien zwracać manifest oraz wskazówkę użycia nowego toola, a nie treść
uruchamiającą file fallback. Follow-up po ręcznej rewizji dodaje krótką
informację o jej numerze i zmienionych częściach oraz instruuje odczyt
aktualnego raportu przed odpowiedzią. Raport jest czytany przez tool, bez
filesystem/shell i bez model-facing `reportId`.

Alternatywa wklejenia całego wyniku do każdej wiadomości follow-up mnoży
tokeny i nadal nie rozwiązuje dużych sekcji. Edycja całego dokumentu jako
jednego stringa wymagałaby niepewnego parsowania na sekcje i mogłaby zgubić
metadata, więc nie jest proponowana.

## Zakres

- Cztery feature'y z follow-up chatem: Incident Analysis, Flow Explorer,
  UI Explorer i UX Inspector, w live job oraz kontynuowalnej historii.
- Wspólny wzorzec edycji, podglądu, oznaczenia i obsługi konfliktu.
- Trwały zapis i informacja dla AI o aktualnym wyniku.
- Fragmentowy odczyt sekcji dla modelu, także gdy ma ponad 8 KB.

## Non-goals

- Edycja metadata, referencji, confidence, pól wejściowych analizy i innych
  feature'ów bez follow-up chatu.
- Zmiana zewnętrznych systemów źródłowych, kodu repozytoriów lub sesji Copilota.
- Pełne wersjonowanie wszystkich historycznych wariantów raportu i edycja
  importu bez kontynuacji.

## Ograniczenia i ryzyka

- Ręczny tekst może merytorycznie przeczyć pozostawionym referencjom. UI
  oznacza pochodzenie, a walidatory nadal kontrolują strukturalną spójność;
  backend nie deklaruje semantycznej weryfikacji wpisu operatora.
- Dwa kanały zapisu (live job i local history) oraz feature-specific mappery
  muszą zachować identyczny wynik i znacznik; guard i digest chronią przed
  utratą późniejszej zmiany.
- Zmiana eksportu wymaga jawnej zgodności z bieżącymi wersjami każdego
  feature'a. Stare dane bez znacznika pozostają czytelne, importowane dane są
  read-only. Wersje kontraktów podnosimy tam, gdzie wymaga tego walidacja.
- Limity SDK dotyczą rozmiaru całej odpowiedzi toola, nie tylko Markdown;
  test mierzy zserializowany wynik z wielobajtowymi znakami.
- Rollback: można wyłączyć nową akcję zapisu i nowe endpointy bez usuwania
  poprawionych raportów. Odczyt snapshotów z addytywnym znacznikiem pozostaje
  kompatybilny, a chunk tool nie mutuje danych.

## Kryteria akceptacji

- Ikona Edytuj znajduje się obok Share tylko przy zakończonym, edytowalnym wyniku
  jednego z czterech feature'ów. Podgląd pokazuje szkic; Anuluj go odrzuca.
- Zapis odświeża wynik, Share i lokalny snapshot. Znacznik korekty pozostaje
  po odświeżeniu, eksporcie i ponownym otwarciu historii.
- Konflikt digestu albo aktywny follow-up dają czytelny błąd i nie zmieniają
  żadnego z raportu, DTO wyniku ani znacznika.
- Pierwszy follow-up po zmianie wskazuje AI aktualną rewizję. Model ma
  dostępny odczyt całej sekcji w kolejnych fragmentach bez niedostępnego pliku.
- Ta sama korekta działa dla wszystkich czterech feature'ów bez importów
  między nimi. Pozostałe ekrany Share zachowują dotychczasowe działanie.

## Kroki

- [x] Krok 1: Dodać neutralny kontrakt ręcznej rewizji i warunkowego zapisu,
  obsługę live/local oraz projekcje czterech feature'ów. Zweryfikować
  MockMvc, konflikt, atomowość, statusy, historię, import/export i zgodność
  starych snapshotów; po kroku backend zwraca spójny poprawiony wynik.
- [x] Krok 2: Dodać bezpieczny odczyt fragmentów w platformowych report tools,
  allowlisty czterech follow-up policy i guidance AI. Testy potwierdzają pełny
  odczyt >8 KB, UTF-8, stabilny digest, brak dostępu do obcej sekcji oraz
  zserializowane odpowiedzi poniżej progu SDK.
- [x] Krok 3: Dodać wspólny edytor i podgląd Markdown, ikonę obok Share
  i integrację live/local we wszystkich czterech
  ekranach. Testy komponentów i ekranów obejmują zapis, anulowanie, konflikt,
  stan imported/read-only, duży tekst oraz aktualny dokument Share.
- [x] Krok 4: Zaktualizować dokumenty architektury i lokalne `AGENTS.md`,
  przejrzeć zależności, anonimizację diffu i wszystkich konsumentów. Wykonać
  `PackageDependencyGuardTest`, testy Angulara, build frontendu, następnie
  `mvn -q -Pbackend-dev clean package` zgodnie z regułą zmian wspólnych;
  wynik albo środowiskową blokadę zapisać przy kroku.

Wszystkie cztery kroki zatwierdzone przez operatora 2026-09-25.

## Wynik weryfikacji

- `npm --prefix frontend test -- --watch=false`: 628 testów, 80 plików, sukces.
- `npm --prefix frontend run build`: sukces; bundle zapisany w `src/main/resources/static`.
- `mvn -q -Pbackend-dev clean package`: sukces, łącznie z `PackageDependencyGuardTest` i testami web.
- Celowane testy po ostatnich dopiskach do testów historii i guidance: sukces.
- `mvn -q -Pbackend-dev -DskipTests package`: sukces; finalny JAR zawiera ostatnią korektę opisu toola.

## Korekta widoku edycji 2026-09-25

Na podstawie zrzutu i uwagi operatora usunięto wybór pojedynczej części.
Wspólny edytor pokazuje podsumowanie i sekcje kolejno, każdą pod własnym
krótkim nagłówkiem. Pola Markdown używają automatycznej wysokości z minimum
10 wierszy; podgląd pokazuje wszystkie części jednocześnie. Usunięto etykiety
„Część raportu”, „Treść Markdown” oraz objaśnienie pod formularzem.
Testy Angulara: 628/628; produkcyjny build Angulara: sukces.

## Podświetlanie źródła Markdown 2026-09-25

Zmiana jest L2, ponieważ dotyka wspólnego edytora czterech feature'ów.
Baseline: osobne pola `textarea` z CDK autosize, minimum 10 wierszy oraz
wspólny podgląd, szkic i warunkowy zapis raportu. Kontrakt HTTP, report DTO,
historia, follow-up i tool policy pozostają bez zmian.

Conformance delta: pola źródła zastępuje CodeMirror z parserem GFM,
podświetleniem składni, zawijaniem linii i wysokością rosnącą wraz z treścią.
Minimum 10 wierszy, podgląd renderowany przez `MarkdownContentComponent`,
anulowanie, zapis i obsługa konfliktu zachowują dotychczasową semantykę.
Nowe zależności są ograniczone do frontendowego workspace'u. Konsumenci:
Incident Analysis, Flow Explorer, UI Explorer i UX Inspector w trybie live
oraz kontynuowalnej historii; import pozostaje tylko do odczytu.

- [x] Krok 5: Wprowadzić wspólny komponent CodeMirror i podmienić pola edycji;
  testy celowane potwierdzają podświetlenie, synchronizację szkicu, podgląd,
  zapis i konflikt. Operator zatwierdził zakres poleceniem „ok go”.
- [ ] Krok 6: Zweryfikować instalację z lockfile, pełne testy Angulara,
  produkcyjny build, czysty build Maven wymagany po zmianie `package.json`
  oraz zachowanie długich sekcji w przeglądarce.
