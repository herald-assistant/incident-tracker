# Nazwy załączników przy wiadomościach

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator chce widzieć pliki nad wiadomością, z którą je dodał, zamiast zbiorczej
listy u góry chatu. Zmiana zatwierdzona w rozmowie; wskazany JSON jest poza zakresem.

## Proponowane rozwiązanie

Przy przyjęciu wiadomości przypisujemy nieprzypisane metadane plików do jej ID.
Opcjonalne messageId jest częścią lokalnych metadanych załącznika, poza eksportem.
Shared chat dostaje neutralną mapę nazw per ID wiadomości i nazwy oczekujące przy
composerze. Feature mapuje dane; shared komponent posiada prezentację.

## Zakres

L2: metadane feature'a, trwałe przyjęcie/rollback, wspólna prezentacja chatu,
oba widoki System Assistanta, testy i dokumentacja. Bieżący kontrakt V1.

## Non-goals

Zmiana dostarczania plików do AI, usuwanie/download plików, eksport załączników,
migracja lub zgadywanie historycznych powiązań, obsługa wskazanego JSON-a.

## Baseline i conformance delta

Baseline: trwałe pliki przy runie, globalne nazwy u góry, NEW sesja z wszystkimi
plikami per tura. Delta: opcjonalne ID pierwszej przyjętej wiadomości i prezentacja
per wiadomość. Przy odrzuceniu przed AI powiązanie pozostaje puste.
Konsumenci shared chatu otrzymują domyślne puste dane; ich widoki i DTO bez zmian.
Bez migratorów, dodatkowych wersji ani zmian binary/storage lifecycle.

## Ograniczenia i ryzyka

Nazwy mogą być długie lub identyczne, więc zawijamy tekst i zachowujemy każdy
wpis. Historycznych powiązań nie wyprowadzamy z diagnostyki. Rollback: cofnięcie
powiązania i prezentacji; oryginalne pliki pozostają przy runie.

## Kryteria akceptacji

Nowe pliki widać nad właściwą wiadomością, bez powielania w następnej turze.
Nowo dodany plik trafia do następnej wiadomości. Powiązanie wraca po restarcie,
nie trafia do eksportu, a odrzucony send nie zużywa oczekujących plików.
Oba widoki oraz pozostali konsumenci shared chatu działają.

## Kroki

- [x] Powiązanie pliku z przyjętą wiadomością i rollback, testy historii/eksportu.
- [x] Wspólna prezentacja nazw nad wiadomością i oczekujących przy composerze,
  usunięcie zbiorczej listy; testy shared chatu i obu widoków.
- [x] Dokumentacja, testy backendu i FE, build i wizualna kontrola obu widoków.

Dowody w `tdw-data/assistant-attachment-cli-test`: `message-attachments-backend.log`
(20 testów bez błędów), `message-attachments-frontend.log` (1046 testów),
`message-attachments-focused-frontend.log` (129 testów końcowego stanu),
`message-attachments-build.log` i `message-attachments-package.log` (exit 0).
Przeglądarka: `message-attachments-ui-results.json` i screenshoty full/embedded;
wysłanie, reload, właściwe wiadomości, brak błędów JS i overflow przy 430 px.
`git diff --check` exit 0. Build FE ma zastane ostrzeżenie budżetu platform-landing.
