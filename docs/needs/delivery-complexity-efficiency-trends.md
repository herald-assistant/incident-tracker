# Delivery Complexity Trends - efektywnosc dostarczania

Status: approved

## Potrzeba

Sam trend dostarczonej zlozonosci nie rozroznia wzrostu wyniku wynikajacego z
wiekszego nakladu od sytuacji, w ktorej podobna zlozonosc zostala dostarczona
mniejszym nakladem. Gdy biznesowe CSV zawieraja snapshot `timespent` i estimate
z Jira, uzytkownik chce zobaczyc, jak relacja punktow do zalogowanego czasu
zmienia sie dziennie, tygodniowo, miesiecznie albo kwartalnie dla calego tribe
lub wybranego zespolu.

## Oczekiwany wynik

Istniejacy ekran i jego wykresy pozostaja bez zmian. Ponizej nich pojawia sie
warunkowa sekcja nakladu, ktora pokazuje:

- Efficiency w jednostce CP/MD,
- Complexity Points (CP) i Time Spent (MD) bedace podstawa wskaznika,
- zmiane wskaznika pomiedzy kolejnymi okresami z danymi,
- pokrycie punktow kompletnym `timespent`,
- estymowany i faktyczny naklad dla calego zakresu oraz procentowy trend
  odchylenia Time Spent od Original Estimate per okres, gdy oba sa dostepne,
- liczebnosc proby i przyczyny pominiecia danych.

## Semantyka

- punkty sa liczone raz na Delivery Unit zgodnie z `pointsForAggregation`,
- czas jest sumowany raz z unikalnych issue Delivery Unit,
- brak `timespent` nie jest zerem,
- jednostka wchodzi do glownego wskaznika tylko, gdy wszystkie jej issue maja
  `timespent`, a suma czasu jest dodatnia,
- okres pochodzi z `doneAt` punktowej kotwicy Delivery Unit; caly snapshot
  czasu jest przypisany do okresu dostarczenia, a nie rozkladany po datach
  worklogow,
- przy widoku calego tribe jednostki wielozespolowe sa uwzgledniane,
- przy filtrze konkretnego zespolu wskaznik obejmuje tylko Delivery Units,
  ktorych wszystkie issue maja ten sam wybrany zespol; jednostki wspoldzielone
  sa wykazane osobno jako pominiete,
- filtr autora MR moze zawęzic zakres dostaw, ale nie oznacza efektywnosci tej
  osoby, poniewaz CSV nie zawiera worklogow per autor.

## Non-goals

- Ranking zespolow lub osob.
- Indywidualna atrybucja punktow i czasu.
- Rozklad czasu na faktyczne dni wykonania.
- Korekta swiat, urlopow, zmian skladu i dostepnosci zespolu.
- Statystyczne dowodzenie przyczynowosci albo uzysku z AI.
- Zmiana scoringu, eksportow assessmentow albo backendu.

## Kryteria sukcesu

- Starsze CSV nadal tworza dotychczasowy trend bez sekcji efektywnosci.
- Nowe pola sa walidowane jako opcjonalne, nieujemne sekundy.
- Deduplikacja preferuje nowszy `timeTrackingCapturedAt` przy tym samym
  `doneAt`.
- Wskaznik, delta, pokrycie, filtry i konwersja MD sa deterministyczne.
- Niepelne i zerowe dane czasu nie zawyzaja efektywnosci.
- Estymacja kontra wykonanie korzysta tylko z jednostek z kompletnymi obiema
  wartosciami.
- UI pokazuje probę i ograniczenia bez zmiany istniejacych prezentacji.
