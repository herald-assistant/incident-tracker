# Koszt analiz oparty o kredyty Copilota we wspolnym aside

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator powinien widziec wiarygodny, spojny koszt wykonanej analizy we wszystkich
feature'ach. Dotychczas frontend utrzymywal cennik modeli i liczyl przyblizenie
z agregatow tokenow, mimo ze runtime Copilota przekazuje usage per wywolanie.

## Proponowane rozwiazanie

Zmiana L2: `aiplatform.copilot` agreguje `totalNanoAiu` do `aiCredits` we
wspolnym kontrakcie `AnalysisAiUsage`. Jeden tab wspolnego aside pokazuje
kredyty, tokeny, wywolania, modele i orientacyjny ekwiwalent USD przy
zalozeniu 100 kredytow = 1 USD. Brak `totalNanoAiu` oznacza nieznany koszt.
Alternatywa z cennikiem per model wymaga ciaglych aktualizacji i nie zna
faktycznego trybu rozliczenia kazdego wywolania.

## Zakres

Wszystkie ekrany analityczne uzywajace `AnalysisAiUsage`: Incident Analysis,
Flow Explorer, Config Drift Viewer, Change Verification, UI Explorer,
UX Inspector, Delivery Complexity Assessment, Delivery Scope Complexity
i Operational Context Assistance. Obejmuje follow-up chat tam, gdzie istnieje.
Konsumenci kontraktu: job state, historia i import/export oraz frontend.

## Non-goals

Rozliczanie faktur GitHub, ceny modeli i filtracja kosztu wedlug jednostek
widocznych po filtrze ekranu.

## Ograniczenia i ryzyka

Publiczny kontrakt `cost` zostaje zastapiony przez `aiCredits` bez migracji
historycznych snapshotow. Starszy import moze miec nieznana kwote kredytow.
Wyswietlony USD jest przyblizeniem wedlug zalozenia produktowego.

## Kryteria akceptacji

Jedna implementacja prezentacji kosztu w aside, zero cennikow modeli,
brak prezentacji kosztu w glownym wyniku lub krokach, poprawne sumowanie
per-call i jawny stan braku danych. Testy oraz build obu warstw przechodza.

## Baseline i conformance delta

Baseline: wspolny `AnalysisAiUsage.cost` byl mnoznikiem SDK, a frontend
estymowal kredyty z tokenow i statycznej tabeli modeli. Incident Analysis,
Flow Explorer, oba assessmenty i Operational Context Assistance mialy
oddzielne miejsca prezentacji kosztu.

Delta: `aiCredits` pochodzi wprost z `totalNanoAiu`, a koszt jest renderowany
wyłącznie przez wspolny aside. Historia/import-export wszystkich korzystajacych
feature'ow odczytuje nowe pole. Testy: jednostkowy agregator Java i event
gateway, test wspolnego aside i ekrany z usunietymi lokalnymi kosztami,
pelny Angular test/build oraz backend package.

## Kroki

- [x] Ustalic baseline: znalezc wystapienia wyceny, kontrakt usage i wszystkich konsumentow; potwierdzic semantyke `totalNanoAiu` w SDK/upstream.
- [x] Zmienic runtime i kontrakt usage; zweryfikowac testem sumowanie kredytow i brak danych.
- [x] Przeniesc prezentacje do wspolnego aside i usunac pozostale miejsca oraz cennik modeli; zweryfikowac testami Angulara i przegladem szablonow.
- [x] Zaktualizowac historie/import-export i dokumentacje architektury; zweryfikowac kompilacje i diff.
- [x] Uruchomic produkcyjny build frontendu i pakowanie backendu z testami; przejrzec wynikowy bundle i `git diff --check`.
