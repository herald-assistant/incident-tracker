# Dostęp do historii kodu w follow-up chacie

## Problem i odbiorcy

Analitycy, operatorzy i developerzy po inicjalnej analizie chcą zapytać AI,
kiedy konkretna funkcjonalność została zmieniona albo od kiedy mogła
przestać działać. Brakuje dostępu do historii GitLaba potrzebnej do odpowiedzi.

Inicjalna analiza zawsze poprzedza follow-up chat. Użytkownik oczekuje
uzupełnienia możliwości odczytu kodu, a nie szczegółowego raportu czy
narzuconej procedury badania regresji. Odpowiedź może być krótka i treściwa.

## Oczekiwany rezultat i wartość

Po nowej inicjalnej analizie użytkownik zadaje pytanie w jej follow-up chacie.
AI może znaleźć potrzebny kod, przejrzeć commity i diffy oraz przeczytać
wcześniejszą wersję pliku. Dobór odczytów zależy od pytania; wcześniejsza
analiza nie musi już zawierać odpowiednich zmian ani historycznego kodu.

Fikcyjny przykład CRM: „Od kiedy zapis klienta wymaga numeru telefonu?”.
Oczekiwana odpowiedź może zawierać kilka zdań wyjaśnienia, datę zmiany
i link do commita. Użytkownik nie musi znać ścieżki pliku, SHA ani MR.

## Kryteria sukcesu

- Przebieg użytkownika pozostaje taki sam: inicjalna analiza, następnie chat.
- AI może na żądanie odczytać historię i kod w dozwolonych repozytoriach.
- Historia może dotyczyć jednego pliku albo zestawu plików; AI widzi commity
  dotyczące dowolnego z nich oraz powiązanie wyników z żądanymi plikami.
- AI otrzymuje nazwę i e-mail commitera, jeśli źródło je udostępnia;
  brak tych danych nie uniemożliwia odpowiedzi o zmianie.
- Odpowiedź może być krótka, ze źródłem i istotnymi ograniczeniami.
- Data zmiany kodu nie jest automatycznie przedstawiana jako data wdrożenia
  ani potwierdzony moment awarii.
- Pytanie samo w sobie nie zmienia zapisanego raportu.

## Ograniczenia i non-goals

- Dostęp tylko do odczytu, w dozwolonych repozytoriach i ścieżkach.
- Zakres dotyczy kodu, nie historii danych w bazie.
- Bez osobnego chatu startowanego przed analizą, nowego ekranu lub formularza.
- Bez automatycznego bisect, wykonywania kodu, klonowania repozytoriów,
  ustalania wdrożeń i obowiązkowego raportu regresji.

## Kompatybilność i decyzje produktowe

Na potrzeby dostarczenia zmiany zakładamy brak analiz i rozmów utworzonych
przed zmianą. Nie trzeba zachowywać ich odczytu, kontynuacji ani migrować
starych zapisów. To założenie o kompatybilności danych, a nie zmiana kolejności
pracy użytkownika. W nowym przebiegu inicjalna analiza nadal jest pierwsza.

Możliwość została zaimplementowana. Aktualne zachowanie opisuje
[kontrakt historii kodu](../architecture/gitlab-code-history-tools.md).
