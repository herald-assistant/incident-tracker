# Asysta Operational Context bez budżetów eksploracji i materiału

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator zlecił usunięcie wszystkich budżetów asysty AI Operational Context, w tym limitów rozmiaru plików i materiału. Obecne progi zatrzymują eksplorację i pomijają pełne, dostępne źródła, przez co draft może być niekompletny. Zakres i usunięcie limitów rozmiaru zostały jawnie wskazane w rozmowie.

## Baseline i conformance delta

Zmiana L2: feature ma twardy budżet 20 wywołań (2 bez GitLaba), podlega też globalnemu budżetowi Copilota; collector pomija pliki po 16/32 KiB lub sumie 96 KiB; przygotowanie promptu powtarza te progi i limituje katalog, guidance oraz drzewo. Wspólny odczyt zweryfikowanego pliku ma limit 256 KiB, adapter REST 1 MiB. Publiczne API, typowany draft, decyzja operatora, allowlista tools, scope grupy/commita i kompletna walidacja źródeł pozostają bez zmian. Zmienia się hidden policy sesji, pełność materiału i neutralna możliwość pełnego zweryfikowanego odczytu. Konsumenci wspólnego odczytu: GitLab MCP, UX Inspector i pozostałe feature'y używające `GitLabVerifiedRepositoryFileReader`; ich domyślne limity pozostają bez zmian.

## Proponowane rozwiązanie

Sesja asysty użyje istniejącej polityki `GOAL_DRIVEN` i nie dostanie twardego budżetu. Feature przestanie obcinać lub odrzucać materiał na podstawie rozmiaru. Neutralny adapter GitLab otrzyma osobny kompletny odczyt, a zweryfikowany odczyt bez progu będzie włączany wyłącznie przez hidden scope tej sesji. Dotychczasowy bounded read pozostanie dla innych konsumentów. Stronicowanie wyników nawigacji pozostaje mechanizmem kontynuacji, a walidacja ścieżek, tekstu, commita, hashy i liczby wymaganych dokumentów pozostaje aktywna.

## Zakres

Backend asysty, neutralny pełny odczyt GitLab, testy i dokumentacja aktualnego zachowania.

## Non-goals

Zmiana walidacji katalogu, pól formularza, schematu draftu, modeli Copilota i limitów innych feature'ów.

## Ograniczenia i ryzyka

Duży pełny prompt lub wynik toola może przekroczyć rzeczywiste okno kontekstowe modelu, limit transportu, pamięć procesu lub limit dostawcy GitLab. Takie niepowodzenie ma być widoczne jako błąd, bez cichego pominięcia źródła. Rewizje i źródła pozostają zweryfikowane.

## Kryteria akceptacji

Asysta nie kończy eksploracji z powodu własnego ani globalnego budżetu wywołań; duży katalog, guidance i plik są przekazywane w całości; pozostałe sesje zachowują dotychczasowe limity; testy Maven przechodzą.

## Kroki

- [x] Usunąć budżety sesji i potwierdzić ukryty policy oraz allowlistę testem assemblera.
- [x] Usunąć feature-owned limity materiału i potwierdzić dużym katalogiem oraz plikiem w testach.
- [x] Dodać neutralny pełny odczyt GitLab tylko dla wskazanej sesji i potwierdzić w testach adaptera/tooli, że inni konsumenci zachowują bounded read.
- [x] Uaktualnić dokumentację, uruchomić testy celowane i pełny `mvn -q test`, sprawdzić diff. Wynik: 1710 testów, 0 błędów, 0 niepowodzeń, 1 pominięty; `git diff --check` bez błędów.
