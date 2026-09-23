# Szczegoly tokenow w aside kosztu AI

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Obecny tab kosztu pokazuje jedynie sumaryczna liczbe tokenow, kredyty,
wywolania i modele. Operator nie widzi podzialu na input, cache read,
cache write, output i reasoning ani znaczenia tych wielkosci. Na szerokim
aside pojedyncza karta pozostawia duzo pustej przestrzeni.

## Proponowane rozwiazanie

Zmiana L2 wspolnego kontraktu i komponentu. Runtime przenosi opcjonalne
`reasoningTokens` z `assistant.usage` do `AnalysisAiUsage`. Wspolny aside
prezentuje koszt i tokeny w jednej zwartej kompozycji: glowne sumy input i
output, pod nimi cache read/write i reasoning wraz z objasnieniami. Reasoning
jest czescia output; cache i reasoning nie sa dodawane ponownie do sumy.
Brak pola cache read/write lub reasoning jest pokazany jako brak danych, a nie
zero. Kredyty i ich ekwiwalent USD sa zaokraglane do dwoch miejsc po przecinku.

## Zakres

Wspolny runtime Copilota, kontrakt usage, agregaty assessmentow, normalizatory
import/export oraz komponent aside uzywany przez wszystkie feature'y.

## Non-goals

Zmiana sposobu naliczania kredytow, cennik modeli, per-call billing detail.

## Ograniczenia i ryzyka

`assistant.usage` jest eventem per wywolanie i moze nie zawierac
`reasoningTokens` lub pol cache. Dokumentacja SDK okresla reasoning jako
podzbior output. Przy braku ktoregos pola w dowolnym wywolaniu odpowiedni
agregat pozostaje nieznany.

## Baseline, conformance delta i konsumenci

Baseline: `AnalysisAiUsage` zawiera input, output i cache, lecz nie reasoning;
aside pokazuje tylko `totalTokens`. Delta: nowy opcjonalny licznik reasoning i
wspolny widok pieciu kategorii. Konsumenci: wszystkie joby i follow-up chat,
agregaty Delivery Complexity Assessment i Delivery Scope Complexity,
historia/import-export oraz wspolny aside.

## Kryteria akceptacji

W aside widac wszystkie piec kategorii z prostym opisem, calkowita liczbe
tokenow, kredyty, USD, wywolania i modele. Brak opcjonalnej metryki jest jawny. Widok
jest czytelny na desktopie i mobile. Testy backendu i frontendu przechodza.

## Kroki

- [x] Dodac `reasoningTokens` do capture i agregatow; zweryfikowac testem eventow i sumowania.
- [x] Przeniesc pole przez kontrakt i import/export; zweryfikowac kompilacja i testami kontraktu.
- [x] Przebudowac wspolny aside, dodac opisy i responsive layout; zweryfikowac testami komponentu oraz przegladem widoku.
- [x] Zaktualizowac architekture i wykonac test Angulara, build frontendu oraz pakowanie backendu.
