# Wspólne udostępnianie wyniku analizy

Status: complete

Source need: [Udostępnianie wyniku analizy](../needs/analysis-result-sharing.md)

## Potrzeba / dlaczego

Operator potrzebuje jednakowej, czytelnej akcji przekazania rezultatu analizy
między feature'ami. Obecne przyciski eksportują różne formaty i różne zakresy
danych.

## Proponowane rozwiązanie

Wspólny komponent Angular z ikoną `share` i `MatMenu` obsługuje cztery akcje.
Wspólny kontrakt treści Markdown rozdziela tekst wyniku i metadane. Raporty
`AnalysisReport` używają jednego renderera, a feature'y bez raportu dostarczają
lokalny adapter treści. Komponent odpowiada za schowek, pobranie i feedback.
JSON znika z akcji na ekranach feature'ow, ale pozostaje w Analysis History.
Dedykowany CSV ocen Delivery zostaje dla trendow. Menu i overlay uzywaja
globalnych tokenow stylu platformy i wspolnego wyrownania ikon z tekstem.

## Zakres

Ekrany Incident Analysis, Flow Explorer, Change Verification, Config Drift
Viewer, Delivery Complexity Assessment, Delivery Scope Complexity, UI Explorer,
UX Inspector i asysty Operational Context; wspólny komponent, adaptery,
testy i dokumentacja.

## Non-goals

Zmiana formatu archiwalnego JSON, importu runu, endpointów backendu, scoringu,
promptów i wyników AI.

## Ograniczenia i ryzyka

Zmiana L2 w shared frontendzie. Treść musi odpowiadać prezentacji każdego
feature'a; szczególną uwagę wymagają Config Drift BASIC, oceny częściowe i
draft asysty. Brak kompatybilności starego układu przycisków na ekranach.

## Kryteria akceptacji

Każdy ekran z merytorycznym wynikiem pokazuje jeden przycisk `share` z czterema
opcjami: kopiuj Markdown, kopiuj Markdown + Meta, pobierz Markdown, pobierz
Markdown + Meta. Testy sprawdzają treść i menu, a testy oraz build frontendu
przechodzą.

## Kroki

- [x] Zbudować wspólny model, renderer raportu i komponent menu; zweryfikować
  cztery akcje testem komponentu oraz rozdział treści od meta testem renderera.
- [x] Podłączyć wszystkie ekrany wyników, usunąć stare przyciski udostępniania
  i zweryfikować adaptery treści testami feature'ów.
- [x] Uruchomić testy i build frontendu, przejrzeć diff oraz opisać wynikowy
  mechanizm w dokumentacji architektury.

Weryfikacja: produkcyjny build Angulara przeszedl. Przy pelnym uruchomieniu
608/613 testow przeszlo, a 5 przekroczylo domyslny limit 5 sekund podczas
rownoleglego wykonania. Te 5 plikow uruchomione osobno przeszlo 112/112;
celowane testy menu, renderera i Config Drift przeszly 18/18.
