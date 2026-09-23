# Spójna prezentacja wyniku i oczekiwania w eksploratorach UI

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator widzi powtórzony tytuł analizy, osobny pill stanu i rozbudowany komunikat o częściowym wyniku przed właściwą treścią. Stany oczekiwania UI Explorera i UX Inspectora zajmują dużo miejsca i wyglądają inaczej, choć opisują ten sam etap pracy.

## Baseline i conformance delta

- Poziom L2: zmienia się wspólny nagłówek wyniku i dochodzi wspólny komponent stanu runu.
- Przed zmianą oba wyniki miały osobny wiersz stanu i baner częściowego raportu; oba ekrany miały własne duże bloki oczekiwania. UI Explorer dodatkowo powtarzał tytuł widoku nad gotowym raportem.
- Wynik merytoryczny, status joba, API, DTO, raport, import/export, follow-up i aside pozostają bez zmian. Zmienia się tylko prezentacja.
- Konsumenci wspólnego nagłówka: UI Explorer, UX Inspector, Change Verification i Analysis Final Result. Nowe pole podpowiedzi jest opcjonalne, więc pozostałe ekrany zachowują treść i akcje.
- Konsumenci wspólnego stanu runu: UI Explorer i UX Inspector. Oba przekazują własny tytuł i komunikat.

## Proponowane rozwiązanie

Wspólny nagłówek przyjmuje opcjonalną treść podpowiedzi dla wyniku częściowego i pokazuje dostępną klawiaturą ikonę obok confidence. Oba wyniki usuwają status pill i pełny baner. Oba ekrany używają jednego kompaktowego komponentu dla stanu pending i braku raportu. Po ukończeniu UI Explorer zwija konfigurację i ukrywa powtórzoną kartę runu; UX Inspector skraca nagłówek karty z kontekstem wejścia.

Parametry UI Explorera z historii i importu tworzą zwarty wiersz, a szczegółowe tryby sekcji są dostępne po rozwinięciu.

Alternatywa z osobnymi stylami w każdym ekranie utrzymywałaby dwa warianty tego samego stanu i zwiększała ryzyko kolejnego rozjazdu.

## Zakres

UI Explorer, UX Inspector, wspólny nagłówek wyniku, wspólny stan runu oraz odpowiadające testy i dokumentacja.

## Non-goals

Zmiana aside, kontraktów backendu, zawartości raportu, eksportu i historii analiz.

## Ograniczenia i ryzyka

Podpowiedź musi być dostępna z klawiatury i mieć nazwę dla czytnika ekranu. Zmiana stylu wspólnego nagłówka nie może ukryć akcji share ani confidence u pozostałych konsumentów. Wąskie ekrany muszą zawijać akcje nagłówka.

## Kryteria akceptacji

Gotowy wynik nie powtarza tytułu workspace i nie ma osobnego pill/banera częściowości. Ikona z podpowiedzią jest przy confidence. Pending state ma ten sam kompaktowy wygląd w obu feature'ach. Aside pozostaje bez zmian. Testy Angulara i build produkcyjny przechodzą.

## Kroki

- [x] Sprawdzić baseline, konsumentów wspólnego nagłówka i istniejące testy.
- [x] Ujednolicić nagłówek wyniku i usunąć powtórzone statusy; weryfikacja testami komponentów.
- [x] Zastąpić lokalne bloki pending wspólnym komponentem i skrócić nagłówki po zakończeniu; weryfikacja testami ekranów.
- [x] Zaktualizować dokumentację i uruchomić pełne testy Angulara (615/615) oraz build produkcyjny.
