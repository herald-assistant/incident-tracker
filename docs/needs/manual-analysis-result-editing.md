# Ręczna korekta wyniku analizy

## Problem

Wynik przygotowany przez AI może wymagać korekty redakcyjnej albo dopisania
ustalenia operatora. Obecnie użytkownik może poprosić o zmianę przez follow-up
chat, ale nie może bezpośrednio poprawić widocznego rezultatu. Po takiej
poprawce kolejna odpowiedź AI musi opierać się na aktualnej wersji wyniku,
również gdy sekcja jest zbyt długa, aby model odczytał ją w pojedynczej
odpowiedzi narzędzia.

## Użytkownik i oczekiwany rezultat

Operator pracujący z zakończoną analizą i follow-up chatem może przejść od
wyniku do edytora Markdown, zmienić jego treść, obejrzeć ją przed zapisem i
zapisać. Wynik po zapisie pokazuje poprawioną treść, a informacja o ręcznej
korekcie pozostaje w danych raportu. Udostępnianie, historia i kolejne pytania
w czacie korzystają z aktualnej wersji.

## Miara sukcesu

- Operator może poprawić rezultat we wszystkich analizach oferujących
  follow-up chat, widzi składnię Markdown w trakcie edycji, a podgląd nie
  zapisuje zmian.
- Po zapisie poprawiona treść jest widoczna także po odświeżeniu i
  odtworzeniu lokalnej historii oraz w udostępnianym dokumencie.
- Przy kolejnym pytaniu AI otrzymuje informację o ręcznej zmianie i może
  odczytać całą zmienioną sekcję, również gdy przekracza ona 8 KB.
- Równoczesna edycja i odpowiedź AI nie nadpisują się po cichu.
- System zachowuje pochodzenie ręcznej korekty bez przypisywania jej jako dowodu AI.

## Ograniczenia i decyzje produktowe

- Zakres dotyczy analiz mających zarówno wynik, jak i follow-up chat.
- Ręczna edycja nie jest nową analizą i nie uruchamia ponownie zbierania danych.
- Referencje i ograniczenia widoczności pozostają jawne; ręczna korekta tekstu
  nie jest automatycznie nowym dowodem ani potwierdzeniem AI.
- Importowany zapis bez możliwości kontynuacji pozostaje tylko do odczytu.
- Pierwszy zakres obejmuje treść Markdown wyniku. Edycja strukturalnych
  referencji, ocen pewności i konfiguracji analizy wymaga osobnej potrzeby.
