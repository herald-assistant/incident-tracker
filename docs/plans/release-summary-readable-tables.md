# Release summary — raport tabel dla analityka

Status: in-progress

Source need: [Release summary](../needs/release-summary.md)

## Potrzeba / dlaczego

Użytkownik zlecił poprawę raportu: odbiorca zna SQL, ale nie kod aplikacji
ani Liquibase. Oczekuje podsumowania, listy tabel, niezależnych sekcji tabel,
krótkich opisów funkcjonalnych i źródeł w meta. Kolumna Zmiana zachowuje
typy, NULL, defaults, definicje i wartości przed/po. Obecna kolejność zależy
od czasu zapisu, a prompt i walidator wymuszają zbyt techniczną narrację.
Zakres jest zatwierdzony poleceniem poprawy i doprecyzowaniem użytkownika.

## Baseline i delta

L2: kontrakt feature'a i wspólny renderer. Zachowujemy source discovery,
pełne verified evidence, retry GitLab, pełne encje opctx, GOAL_DRIVEN,
historię od startu, podgląd diff/promptu, report tools, chat, edycję i V1.
Baseline testów feature'a: release-summary-readability-baseline.log.
Zastane zmiany formatowania Controller/Catalog/JobService pozostają własnością
użytkownika. Shared/platform nie otrzymuje semantyki Release summary.

Delta: grupowanie po repozytorium i kwalifikowanej tabeli; wspólne techniczne
operacje w appendixie; trzy kolumny: Obiekt, Zmiana, Opis funkcjonalny.
SQL ma jawne mapowanie na opisane obiekty w meta i nadal nie stanowi dowodu
pełności semantycznej. Nie powielamy skryptu i jego obiektów w głównej tabeli.
Źródła są zweryfikowanymi odnośnikami w metadanych. Znane definicje pozostają
obowiązkowe; brak dowodu funkcji jest jawny, bez wymuszonych etykiet w zdaniach.

## Rozwiązanie i konsumenci

- Feature posiada inwentarz, kolejność, szablony, walidację, prompt i skill.
- Shared panel sortuje według order i reuse'uje section-content/meta; appendix
  jest opcjonalnym końcowym obszarem. Brak nowego publicznego DTO raportu.
- Konsumenci: initial, checkpoint, follow-up, manual editor, historia,
  import/export i Share; pozostałe feature'y używające wspólnego panelu.
- Alternatywa samego promptu odpada: obecny walidator odrzuciłby poprawny format.
  Nie dodajemy parsera pełnej semantyki SQL ani nie wykonujemy migracji.

## Ryzyka i weryfikacja

Jednakowe nazwy tabel w różnych repozytoriach/schematach nie mogą się scalać.
Nieznane operacje pozostają w appendixie, bez domysłów co do ownershipu.
Zmiana nie może utracić kolumn, indeksów, constraints ani SQL. Wersja pozostaje
1, bez migratorów; lokalne stare artefakty Release summary są usuwane przy
uruchomieniu aktualnej wersji. Effective skill jest aktualizowany jawnie.
Testy i przykłady wyłącznie fikcyjnego CRM.

## Kroki

- [x] Inwentarz i kontrakt: grupowanie, appendix, trzy kolumny, meta,
  prompt/skill/policy i aktualne testy feature'a. Testy rozdziału/scalania
  tabel, NULL/default/przed-po, SQL coverage, źródeł i braku żargonu. Dowód:
  60 testów zaliczonych, 1 pilot opt-in pominięty; log
  `tdw-data/logs/release-summary-readability-step1.log`. `git diff --check` poprawny.
- [ ] Prezentacja: stabilne sortowanie, shared meta pod sekcjami, końcowy
  zwijany appendix, spójny Share/import. Testy całego frontendu i build. Commit.
- [ ] Pełna regresja backendu po buildzie UI, aktualizacja dokumentacji,
  porządek lokalnych artefaktów starego kontraktu, restart i wygenerowanie
  raportu. Kontrola czytelności w przeglądarce, historii i eksportu. Commit.
