# Semantyczna hierarchia zadan w obu assessmentach

Status: dostarczona 2026-09-30.

Oba assessmenty przekazuja jawny zakres oceny i relacje nadrzedne/podrzedne
bez metadanej typu Jira. Testy potwierdzaja identyczny input przy zmianie
samych nazw typow, zachowanie kontekstu poza zakresem i brak zwielokrotnienia
jednostek przez hierarchie. Koncowa regresja i oba buildy przeszly.
Nowy workflow wymaga nowego runu po restarcie z aktualnym backendem;
historyczne wyniki pozostaja zapisem poprzedniej oceny.

## Problem

Ocena dostarczonej zlozonosci moze byc bledna, gdy projekt Jira uzywa
customowych typow zadan nadrzednych i podrzednych. Operator nie powinien
tlumaczyc modelowi nazw typow ani utrzymywac slownika nazw dla kazdego
projektu. Podzial tego samego zakresu pracy na zadania ma znaczenie
kontekstowe, ale sama nazwa typu nie jest sygnalem zlozonosci.

Problem dotyczy Delivery Complexity Assessment oraz Delivery Scope Complexity.

## Oczekiwany rezultat

Model rozumie, ktore zadania sa oceniane, jaki jest ich potwierdzony kontekst
nadrzedny oraz ktore zadania sa podrzedne. Rozroznia intencje calego zadania
nadrzednego od faktycznie dostarczonego zakresu ocenianej zmiany.

Ocena nie zalezy od nazwy typu zadania. Operator nadal moze korzystac
z oryginalnego typu w metadanych raportowych i analizie trendow.

## Kryteria sukcesu

- Zmiana samych nazw typow, przy tej samej hierarchii i implementacji, daje
  taki sam strukturalny input oceny.
- Zadanie podrzedne zachowuje kontekst nadrzedny bez przypisania mu calego
  zakresu rodzica lub pozostalych zadan.
- Zadanie nadrzedne i jego zadania podrzedne sa rozpoznawane na podstawie
  potwierdzonych relacji, bez slownika customowych typow.
- Brak dostepu do powiazanego zadania pozostaje jawna luka widocznosci.
- Ta sama implementacja nie jest punktowana wielokrotnie w ramach jednostki
  oceny.

## Ograniczenia

Hierarchia opisuje relacje zadan, a nie dowodzi dostarczenia zachowania.
Punkty nadal wymagaja evidence implementacyjnego. Sposob punktowania obu
assessmentow pozostaje odrebny. Potrzeba nie obejmuje zmiany jednostki
agregacji, rozszerzenia zakresu dat ani automatycznego oceniania wszystkich
zadan tej samej rodziny.
