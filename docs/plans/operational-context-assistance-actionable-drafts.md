# Propozycje asysty Operational Context bez dialogu

Status: done

Source need: [Pomoc AI przy tworzeniu i aktualizacji Operational Context](../needs/operational-context-ai-assisted-maintenance.md)

## Potrzeba / dlaczego

Asysta jest jednorazowa: operator nie moze odpowiedziec AI w trakcie runu.
Pytanie zamiast propozycji konczy kosztowna analize bez zmian do przegladu.
Jawny opis ownera przez operatora rowniez nie dociera do review, poniewaz
parser bezwarunkowo odrzuca takie pole.

## Baseline i conformance delta (L1)

- Baseline: skill kaze pytac o ownera, zastosowanie repozytorium i brakujace
  dowody; parser odrzuca `ownership: explicit` niezaleznie od jego zrodla.
  Pusty draft z pytaniem ma `BLOCKED`.
- Delta: prompt i skill wymagaja propozycji opartych na dostepnych faktach,
  bez pytan i bez oczekiwania na odpowiedz AI. Jawne przypisanie ownera w
  opisie operatora moze byc propozycja z `USER_STATEMENT`, refem
  `operator:description` i obowiazkowym potwierdzeniem pola w UI.
  Brak bezpiecznej propozycji daje jawne ograniczenie, a nie pytanie.
- Konsumenci: parser draftu, job asysty, UI przegladu i historii, effective
  skill Copilota. Aktualny kontrakt JSON opisuje plan
  [kanonicznego katalogu](operational-context-canonical-only.md).

## Proponowane rozwiazanie

Zmienic instrukcje jednorazowego promptu i skilla. W parserze zastapic
bezwzgledny zakaz potwierdzonego ownershipu warunkiem dokladnej proweniencji
i przegladu. Zostawic walidacje referencji, schematu oraz atomowego zapisu.
Alternatywa polegajaca na ponownym pytaniu AI wymagalaby dialogu, ktorego UI
nie oferuje i ktorego operator nie potrzebuje przy recznej edycji.

## Zakres

Prompt, skill, parser, komunikat joba, testy i dokumentacja zachowania.
Effective lokalny skill zostanie zsynchronizowany z pakietowym, jesli zawiera
wylacznie starsza wersje zasad asysty.

## Non-goals

Tworzenie chatu AI, automatyczny zapis, zgadywanie ownera z nazw zespolow lub
projektow. Dawny kontrakt `questions` zostal usuniety w kolejnym kroku.

## Ograniczenia i ryzyka

AI moze blednie przypisac tresc opisu do pola. Podglad i jawne potwierdzenie
kazdego takiego pola zostaja obowiazkowe. Zmiana effective skilla w juz
uruchomionym procesie wymaga odswiezenia jego stanu albo restartu aplikacji.

## Kryteria akceptacji

- Jawny opis przypisania zespolu do systemu daje parsowalna propozycje.
- Sama zbieznosc nazw, `AI_INTERPRETATION`, zly source ref lub brak wymaganego
  potwierdzenia pola nadal odrzucaja przypisanie.
- Prompt i skill nie polecaja konczyc runu pytaniem; ograniczenia sa
  deklaratywne. Gdy propozycji nie ma, job zachowuje draft i usage jako
  `BLOCKED` z czytelnym ograniczeniem.
- Testy odpowiednich warstw przechodza.

## Kroki

- [x] Zmieniono prompt, skill i parser. Testy sprawdzaja propozycje ownera
  oparta na opisie operatora, odmowe inferencji i brak pytan w instrukcjach,
  takze gdy starszy lokalny skill nadal sugeruje pytanie.
- [x] Zaktualizowano job, architekture, need i effective lokalny skill.
  Test sprawdza `BLOCKED` z zachowanym draftem i ograniczeniem, a hashe
  obu kopii skilla sa identyczne.
- [x] Uruchomiono `mvn -q test`: 1549 testow, 0 failures, 0 errors,
  1 skipped. Po zmianie formatowania skilla przeszedl jego test celowany.
  Diff oraz zmienione testy przejrzano pod katem danych domenowych.
