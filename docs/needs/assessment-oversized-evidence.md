# Ocena Delivery Unit przekraczajacej okno kontekstu

Status: done

## Problem

W obu assessmentach caly material jednej Delivery Unit trafia do jednej
wiadomosci. Duza liczba merged MR-ow moze przekroczyc limit wejscia modelu.
Model bez `long_context` odrzuca request, a jednostka konczy sie bledem zamiast
ocena. Ponowienie tego samego requestu nie zmniejsza materialu.

Operator potrzebuje automatycznego podzialu materialu MR na mieszczace sie
czesci. Kazda czesc powinna dostac pelny zakres Jira/Confluence, semantyczna
hierarchie i te same zasady oceny. Podzial nie moze zgubic MR-a ani ukryc
ograniczen integracji.

## Wartosc i decyzja UX

Liczy sie przede wszystkim wynik zagregowany. Uzytkownik dopuszcza osobne
wyceny czesci i prosi o wybor lepszego UX. Osobna wycena kazdej czesci jest
mozliwa, ale suma obecnych skal nie jest rownowazna ocenie unii calej dostawy:
DSP ma nieliniowe progi, a Scope ma subliniowy zakres i limit wyniku.
Ta sama zmiana moglaby otrzymac inny wynik zalezne od okna wybranego modelu.

Zatwierdzona i dostarczona decyzja UX: jeden wiersz Delivery Unit, widoczna liczba
czesci i rozwijane ustalenia z kazdej czesci, a jedna finalna ocena unii
zachowan po syntezie. Agregat runu, CSV i Trends nadal licza jednostke raz.
Podzial sluzy przeczytaniu evidence; nie tworzy nowych jednostek dostawy.

## Kryteria sukcesu

- Budzet pochodzi z dynamicznego katalogu Copilota, takze dla modeli bez
  rozszerzonego tieru; aplikacja nie koduje nazw ani limitow modeli.
- Podzial zalezy od rozmiaru pelnego przygotowanego wejscia z rezerwa, a nie
  od liczby MR-ow. Niewielka jednostka zachowuje obecny pojedynczy request.
- Kazda czesc ma pelny zwrocony material Jira/Confluence i rozlaczny zakres
  evidence implementacyjnego. Suma zakresow obejmuje caly material MR.
- Wynik i usage/cost sa jawne; awaria jednej czesci nie daje pozornie pelnej
  oceny calej jednostki.
- UI wyjasnia podzial bez powielania wyniku w agregacie lub eksporcie.
- Nierozdzielny material przekraczajacy budzet ma konkretna diagnostyke.

## Ograniczenia

Estymacja przed requestem nie jest gwarancja identycznego tokenizowania jak
u providera. Potrzebna jest rezerwa oraz ograniczona korekta podzialu po
jednoznacznym bledzie przekroczenia kontekstu. Nie wolno maskowac takim
ponowieniem bledow autentykacji, rate limitu ani kontraktu odpowiedzi.

Pelny Jira/Confluence powtarzany w czesciach zwieksza koszt. Synteza pracuje
na typowanych ustaleniach i referencjach, wiec ma mniejsza widocznosc surowego
kodu niz pojedyncza analiza; ograniczenie musi pozostac widoczne.

Implementacja i weryfikacja zakonczone 2026-10-01. Male jednostki zachowuja
jedno wywolanie; wieksze korzystaja z rozlacznych czesci, pelnego Jira i jednej
finalnej syntezy. Wszystkie wywolania sa widoczne w historii/UI i aktualnym V1.

Plan i dowod: [Podzial nadmiernego evidence w assessmentach](../plans/assessment-oversized-evidence.md).
