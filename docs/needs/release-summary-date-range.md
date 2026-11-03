# Release summary: zakres dat

Status: zrealizowana. Kontrakt opisuje [runtime](../architecture/release-summary-runtime-flow.md).

Operator chce opisać zmiany z okresu na jednej linii rozwoju bez ręcznego
szukania rewizji. Dwa niezależne branche utrudniają wybranie takiego zakresu.

Użytkownik wybiera system, jeden branch i daty Od/Do (Do włącznie). Widzi
faktyczne daty z godziną, opis i SHA obu stanów. Początek to stan sprzed Od,
koniec to stan do końca Do. Brak wcześniejszego stanu jest jawnym brakiem;
identyczne stany oznaczają brak zmian. Daty granic są wyświetlane bez godzin.

Zakres obejmuje dokładnie jedno primary repozytorium priority 1. Interpretacja
Liquibase i istniejący workflow raportu pozostają bez zmian. Sukces oznacza
brak pominięcia pierwszej zmiany okresu oraz zgodność podglądu z analizą,
historią i eksportem. Daty commitów nie są datami wdrożenia ani pusha.
