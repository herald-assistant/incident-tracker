# Udostępnianie wyniku analizy

## Problem

Ekrany analiz oferują różne akcje kopiowania i eksportu. Pliki JSON służą do
odtworzenia runu, lecz nie nadają się do przekazania czytelnego wyniku innemu
analitykowi. Nie ma wspólnego wyboru pomiędzy samą treścią merytoryczną a
treścią z referencjami i ograniczeniami.

## Oczekiwany rezultat

Każdy ekran zakończonej analizy ma tę samą akcję udostępniania. Operator może
skopiować lub pobrać Markdown z wynikiem, z metadanymi albo bez nich. Metadane
obejmują widoczne dla operatora referencje, limity widoczności, otwarte pytania,
luki i ostrzeżenia. Tekst zachowuje strukturę i sens wyniku pokazanego na
ekranie, bez technicznych danych runu, promptu, tokenów czy kosztu.

## Kryteria sukcesu

- Cztery akcje mają identyczną kolejność, ikony i nazwy na ekranach analiz.
- Kopiowanie i pobieranie danego wariantu tworzy ten sam tekst.
- Wariant bez meta pomija metadane, wariant z meta zachowuje wszystkie widoczne
  kategorie i ich przypisanie do wyniku lub sekcji.
- Brak wyniku nie udostępnia pustego pliku.

## Ograniczenia

Techniczny zapis i import JSON w Analysis History pozostają osobnym mechanizmem
odtwarzania runu. Nie są formatem do przekazywania merytorycznego wyniku.
