# Przebieg i historia asysty Operational Context

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator widzi wynik asysty, ale nie moze wygodnie przejrzec przygotowanego promptu, przebiegu Copilota i kosztu w znanym panelu bocznym. Po restarcie traci tez run, mimo ze inne analizy sa zapisywane w historii. Potrzeba wynika z testu operatorskiego i prosby o ten sam sposob inspekcji co w Flow Explorer.

## Baseline i conformance delta (L2)

- Feature: trzy tryby asysty, jeden opcjonalny projekt GitLab przypiety do commita, typowany draft i osobny batch preview/apply. Bez zmiany tych granic.
- Kontrakt: job udostepnia kroki, zanonimizowane zdarzenia Copilota i usage; nie udostepnia promptu. Zdarzenia sa zbyt ogolne, aby pokazac prace AI. Dodamy przygotowany prompt i bezpieczne metadane zdarzen.
- Runtime: prompt i snapshot katalogu powstaja przed wywolaniem Copilota. Zachowujemy read-only tool scope i brak mutation tools.
- Persistence: job istnieje tylko w pamieci, podczas gdy Flow Explorer korzysta z neutralnego LocalAnalysisRunStore. Dodamy feature-owned zapis snapshotow runu, w tym bledow oraz decyzji operatora, bez historii wersji YAML i bez automatycznej kontynuacji AI.
- FE: asysta wyswietla wspolny AnalysisStepsPanel inline; Flow Explorer uzywa AnalysisFeatureAside. Przeniesiemy kroki i tok AI do tego aside, pokazemy usage/koszt i odtworzymy zapisany run w trybie read-only.
- Konsumenci shared: AnalysisStepsPanel uzywaja pozostale analizy; jego nowy kod kroku PREPARE_AI nie zmieni zachowania istniejacych kodow. LocalAnalysisRunStore i historia pozostaja neutralne; dodajemy identyfikator feature'u tylko w routingu i formatowaniu historii.
- Znany drift: obecny plan asysty opisuje brak historii analizy; odrozniamy ja od historii katalogu i aktualizujemy dokumentacje wynikowa.

## Proponowane rozwiazanie

Uzyc wspolnego aside i lokalnego magazynu runow. Snapshot joba bedzie zawieral finalny prompt zlozony przed wywolaniem Copilota. Historia zapisze kolejne kamienie milowe oraz stan koncowy, a ekran Operational Context odczyta je z `localRunId`. Zdarzenia Copilota zachowaja tylko bezpieczne nazwy i metadane pracy, bez raw promptu, reasoning ani payloadow tooli. Alternatywa osobnego ekranu historii i wlasnego panelu zdarzen duplikowalaby komponenty.

## Zakres

Backendowy snapshot, zapis runu, FE aside, prompt, koszt, odtwarzanie historii i dokumentacja.

## Non-goals

Historia/rollback YAML, import runu, follow-up chat oraz wznawianie zapisu propozycji ze starego runu.

## Ograniczenia i ryzyka

Prompt zawiera sanitizowany katalog i moze byc duzy; zapisujemy tylko jego przygotowana wersje. Archiwalny run jest read-only, aby nie zastosowac draftu do zmienionego katalogu. Bledy storage nie powinny przerywac analizy, ale musza byc logowane.

## Kryteria akceptacji

Przed wywolaniem providera snapshot zawiera prompt; krok Przygotuj asyste AI pokazuje go w panelu. Aside pokazuje kroki, prace Copilota i usage/koszt. Kazdy nowy run pojawia sie w historii, otwiera sie po restarcie i zawiera decyzje operatora. Odtworzony run nie pozwala ponownie zapisac zmian.

## Kroki

- [x] Krok 1: Zapisac prompt i uzyteczne, bezpieczne zdarzenia w job snapshot. Zweryfikowano testami stanu i serwisu.
- [x] Krok 2: Dodac feature-owned persistence do LocalAnalysisRunStore na etapach lifecycle i po decyzji. Zweryfikowano zapisem/odczytem oraz testem blednego runu.
- [x] Krok 3: Podlaczyc wspolny aside, prompt i koszt w UI. Zweryfikowano testami komponentu.
- [x] Krok 4: Dodac routing i odtwarzanie runu z historii w trybie read-only. Zweryfikowano testami historii oraz strony.
- [x] Krok 5: Uaktualnic architekture i wykonac macierz testow wspolnej zmiany: Angular 558 testow i build produkcyjny; Maven backend-dev clean package, po doprecyzowaniu zdarzen pelne `mvn test` (1524 testy, 0 bledow, 1 pominiety) i ponowne spakowanie JAR-a.
