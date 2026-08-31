# Delivery Complexity - dane czasu pracy per issue

Status: approved

## Potrzeba

Porownanie samej sumy dostarczonej zlozonosci pomiedzy okresami nie pokazuje
efektywnosci dostarczania, poniewaz backlog i dostepnosc zespolu zmieniaja sie
w czasie. W przyszlosci uzytkownik chce analizowac relacje pomiedzy oceniona
zlozonoscia a czasem pracy, np. liczbe punktow na osobodzien dla miesiaca,
kwartalu, zespolu albo wybranej grupy.

Zeby taka analiza byla mozliwa dla przyszlych raportow, oba assessmenty musza
zaczac zapisywac wraz z issue snapshot aktualnych danych time tracking z Jira.

## Oczekiwany wynik tego inkrementu

Dla kazdego pobranego issue Delivery Complexity Assessment oraz Delivery Scope
Complexity zapisuje:

- `timeSpentSeconds`,
- `originalEstimateSeconds`,
- `remainingEstimateSeconds`,
- `timeTrackingCapturedAt`.

Dane sa czescia snapshotu joba, Analysis History, pelnego eksportu JSON oraz
biznesowego CSV. Brak wartosci w Jira jest zachowywany jako brak danych, a nie
zero.

## Non-goals

- Obliczanie punktow na osobodzien albo korelacji.
- Agregacja miesieczna, kwartalna, zespolowa lub osobowa.
- Pobieranie worklogow, ich autorow i dat wpisow.
- Atrybucja czasu albo punktow do konkretnej osoby.
- Zmiana scoringu, promptu, evidence lub zachowania AI.
- Przeliczanie sekund na osobodni bez jawnej konfiguracji dlugosci dnia pracy.

## Ograniczenia i ryzyka

- `timespent` jest wartoscia narastajaca i snapshot opisuje stan widoczny w
  chwili pobrania issue, nie historie wpisow czasu.
- Estimate moze byc zmieniany w trakcie realizacji; timestamp snapshotu jest
  konieczny do poprawnej interpretacji.
- Brak `timespent` nie oznacza zerowego nakladu pracy.
- Jedno issue moze obejmowac prace wielu osob; bez worklogow nie wolno
  interpretowac czasu jako wyniku konkretnego autora MR ani assignee.

## Kryteria sukcesu

- Jira adapter pobiera i typuje trzy wartosci czasu w sekundach.
- Oba assessmenty zapisują te same cztery pola per issue.
- Pelny eksport JSON i Analysis History zachowuja dane bez zmiany wersji V1,
  a starsze eksporty bez nowych pol nadal sa czytelne.
- Oba biznesowe CSV maja cztery jawne kolumny czasu per issue.
- Dane time tracking nie trafiaja do promptu ani evidence wysylanego do AI.
- Dashboard trendow nadal przyjmuje rozszerzone CSV, ale nie interpretuje
  jeszcze nowych kolumn.
