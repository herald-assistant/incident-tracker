# Edycja raportu w follow-up chat

## Problem

Operator moze doprecyzowac ustalenia w follow-up chat po Incident Analysis,
Flow Explorer, UI Explorer i UX Inspector, ale zapisany raport pozostaje w tych
rozmowach niezmienny. Nowy dowod lub korekta przekazana w odpowiedzi chatu nie
trafia do dokumentu, ktory operator kopiuje, eksportuje albo odczytuje pozniej
z historii.

## Oczekiwana wartosc

Gdy operator jawnie poprosi w biezacej wiadomosci o aktualizacje raportu,
asystent moze poprawic naglowek, jedna cala sekcje, wskazany fragment sekcji
albo globalne metadata. Zmiana jest widoczna w raporcie i odpowiadajacym mu
wyniku feature'a, rowniez po odswiezeniu strony i we wznowionej historii.
Zwykle pytanie o wyjasnienie raportu nie zmienia dokumentu.

## Kryteria sukcesu

- Wszystkie cztery follow-up chaty potrafia odczytac aktualny raport i zapisac
  tylko jawnie zadana zmiane.
- Korekta fragmentu nie wymaga ponownego przeslania calej sekcji i nie zmienia
  innych wystapien podobnego tekstu.
- Nieudana, niejednoznaczna albo przestarzala korekta nie nadpisuje raportu.
- Raport, publiczny wynik, lokalna historia i eksport pozostaja spojne.
- Raport nie zmienia sie po wiadomosci bez jawnej prosby o edycje.
- Przy odpowiedzi, ktora zmienila raport, jeden badge otwiera wszystkie zmiany
  pogrupowane wedlug sekcji, inline z oznaczeniem dodanych i usunietych linii;
  odpowiedz bez zmiany nie pokazuje takiego podgladu.
- Oczekiwanie na odpowiedz follow-up nie sygnalizuje ponownego uruchomienia
  zakonczonej analizy.

## Ograniczenia

- Zakres obejmuje cztery feature'y z follow-up chat; nie obejmuje innych
  analiz ani edycji raportu poza rozmowa.
- Report tools nie uzyskuja dostepu do niepowiazanych raportow ani sekcji
  spoza allowlisty danego feature'a.
- Na polecenie operatora ta zmiana nie utrzymuje kompatybilnosci wstecznej
  dla zmienianych kontraktow historii lub eksportu.
