# Release summary: raport dla analityka systemowego

Status: done

Source need: [Release summary](../needs/release-summary.md)

## Potrzeba / dlaczego

Użytkownik zgłosił, że wynik jest nieczytelny dla analityka systemowego
sprawdzającego zakres wdrożenia. Ekran prezentuje scaffold nawet po blokadzie
MODEL_REQUIRED, a kontrakt wymusza osobne wiersze węzłów parsera, surowe JSON,
identyfikatory i długie odwołania do źródeł. Korekta realizuje pierwotnie
zatwierdzony cel czytelnego, kompletnego raportu; zakres wynika z bieżącego
zgłoszenia użytkownika.

## Proponowane rozwiązanie

Zmiana L2: poprawa kontraktu raportu i jego prezentacji. Pełne źródła i manifest
Liquibase pozostają materiałem dowodowym. Feature buduje z nich inwentarz
obiektów do opisania: tabela, kolumna wraz z ograniczeniami, indeks wraz
z kolumnami, pozostałe operacje i reguły migracji. AI przygotowuje czytelne
tabele z definicją, funkcją i krótkimi odwołaniami do źródeł. Węzły strukturalne
nie stają się osobnymi zmianami dla odbiorcy. Grupowanie nie zależy od
technicznego podziału źródła na paczki. Nierozpoznane operacje pozostają jawne.

Wspólne report tools, renderer, Share, editor, polling, historia i aside są
ponownie używane. Scaffold nie jest publicznym raportem; dopiero zaakceptowane
zapisy AI tworzą wynik częściowy. Brak modelu jest widoczny przed startem.
Numer formatu pozostaje 1, bez migratora poprzedniego kontraktu raportu.

## Baseline i conformance delta

Baseline: deterministyczny odczyt obu SHA, pełne encje Operational Context,
GOAL_DRIVEN, kontrola źródeł, checkpoint i read-only follow-up pozostają.
Zmianie podlegają feature-owned report/inventory/prompt/skill/policy, projekcja
wyniku, UI i walidacja bieżącego eksportu. Shared renderer otrzymuje neutralną
opcję ukrycia technicznych identyfikatorów; pozostali konsumenci zachowują
dotychczasowe zachowanie. Brak nowych zależności między feature'ami.
Shared Share zachowuje kotwice sekcji, aby odnośniki z listy zmian działały
również w wyeksportowanym dokumencie; obejmuje go regresja wszystkich konsumentów.

Test rzeczywisty wykrył odrzucanie odczytów kodu po pełnym SHA wybranego commita.
Korekta neutralnego `forRevisions` akceptuje nazwę wybranego refa albo jego znany
SHA wyłącznie w tym samym projekcie, bez nowego resolve i bez rozszerzenia zakresu.
Tryby pojedynczego przypięcia i ruchomego brancha pozostają bez zmian.
Weryfikacja: oba SHA, obcy SHA/projekt, brak wywołania adaptera, restore i pełna
regresja backendu. Formularz jest dodatkowo sprawdzany z opóźnionym katalogiem AI.

Konsumenci: Release summary job, AI, edycja i kontynuacja, local history,
import/export, Share, frontend strony oraz wspólny renderer raportu.

## Zakres i non-goals

Zakres: czytelność i kompletność raportu Liquibase, obsługa braku modelu,
regresja i aktualizacja lokalnej aplikacji do testów. Poza zakresem: API,
wykonywanie migracji, zmiana dostępu do repozytoriów i platformy Copilota.

## Ograniczenia i ryzyka

Nie wolno utożsamiać usunięcia pliku z DROP w bazie. Grupowanie musi zachować
każdą kolumnę, indeks i nieznany atrybut. SQL wymaga oceny AI i pozostaje
semantycznie niezweryfikowany. Stare lokalne wyniki zmienionego kontraktu
zostaną usunięte tylko dla Release summary; inne feature'y pozostają bez zmian.

## Kryteria akceptacji

- Brak modelu nie pokazuje szablonu jako raportu ani nie pozwala na mylący start UI.
- Raport ma krótkie podsumowanie, listę zmian, szczegóły obiektów i wpływ wdrożenia.
- Kolumna i jej nullability/default/constraints są jednym opisem; indeks zachowuje kolejność kolumn.
- Widok raportu i Share nie zawierają hashy elementów, ścieżek parsera, surowych JSON ani placeholderów.
- Brak dowodów jest opisany zrozumiale; nie pomija wymaganej pozycji i nie udaje kompletności.
- Wynik jest sprawdzony testami i wizualnie, dokumentacja opisuje aktualny kontrakt.

## Kroki

- [x] 1. Naprawić wybór modelu i publikację wyniku po blokadzie/błędzie. Dowód: testy Report/Job/AI/Continuation przeszły przed zmianą i po niej; 13 testów Angulara Release summary przeszło. MODEL_REQUIRED nie publikuje scaffolda; brak zapisów AI nie daje raportu; poprawny checkpoint pozostaje dostępny. Sandbox zablokował cache Vitest przed startem testów; ponowienie poza nim przeszło.
- [x] 2. Wprowadzić inwentarz obiektów, czytelny kontrakt tabel, prompt/skill, walidację kompletności, bieżący eksport i prezentację. Dowód: 44 testy Release summary przeszły (1 osobny pilot opt-in pominięty), także guardy granic pakietów; 686 testów Angulara przeszło. Fikcyjny CRM obejmuje 120 kolumn, YAML, samą zmianę NULL/NOT NULL, kolejność kolumn indeksu, SQL, rollback i usunięcie changelogu. Kontrola wizualna na aktualnym JAR-ze należy do kroku 3.
- [x] 3. Wykonać regresję Angular, build UI i backend-dev clean package; uaktualnić kanoniczną dokumentację i effective skill, uruchomić aktualną aplikację i zweryfikować rzeczywisty raport. Dowód: 687 testów Angulara, build UI oraz `mvn -q -Pbackend-dev clean package` przeszły; backend: 2025 zaliczonych, 3 opt-in pominięte, zero błędów. Aktualny JAR działa lokalnie. Test rzeczywisty potwierdził odczyt kodu po obu przypiętych SHA, zapis i korektę raportu, wszystkie 23/23 oczekiwane pozycje w trzech zakresach, sześć sekcji, krótkie podsumowanie i poprawne odtworzenie modelu oraz reasoning effort. Przegląd w przeglądarce potwierdził czytelne cztery kolumny, krótkie źródła i działające przejścia do szczegółów. SQL i rzeczywisty brak uzasadnienia jednego pola pozostają jawnymi ograniczeniami; pełne pokrycie pozycji nie podnosi automatycznie statusu do COMPLETED. Uaktualniono dokumentację runtime, decyzje, potrzebę i effective skill; usunięto zastąpiony plan. Końcowy przegląd diffu i odnośników nie wykazał błędów.
