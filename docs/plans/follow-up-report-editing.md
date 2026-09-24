# Aktualizacja raportu w follow-up chat

Status: complete

Source need: [Edycja raportu w follow-up chat](../needs/follow-up-report-editing.md)

## Potrzeba / dlaczego

Follow-up czterech feature'ow moze znalezc nowy dowod albo sprostowac
wniosek, lecz zapisany raport pozostaje niezmienny. Operator potrzebuje
aktualnego dokumentu w UI, historii i eksporcie bez przepisywania calej
sekcji przy drobnej korekcie.

## Proponowane rozwiazanie

Rozszerzyc neutralna platforme report tools o `report_patch_section`.
Patch wskazuje istniejacy `sectionId`, dokladny stary fragment, nowy fragment
i digest aktualnej sekcji. Store atomowo odrzuca niezgodny digest, brak lub
wielokrotne wystapienie fragmentu oraz wynik, ktory pozostawilby pusta sekcje.
`report_get_current` udostepni na zadanie tresc jednej wskazanej sekcji;
zwykly odczyt nadal zwroci zwarty manifest. `reportId` i dozwolone sekcje
pozostana w hidden context.

Kazdy follow-up przekaże aktualny raport jako `initialReport` do efemerycznego
store runu. Odczyt bedzie dostepny na potrzeby weryfikacji. Instrukcje
feature'ow pozwola wywolac `report_update_header`, `report_upsert_section`,
`report_patch_section` i `report_update_meta` tylko po jawnej prosbie operatora
w biezacej wiadomosci; po zapisie model sprawdzi stan przez
`report_get_current`. Bez takiej prosby odpowiedz pozostanie zwyklym chatem.

Po zakonczeniu turnu feature opublikuje zmieniony raport tylko po walidacji
jego wlasnego kontraktu. Zaktualizuje rownoczesnie publiczny `result` przez
istniejacy mapper, w live jobie i local continuation. Brak zmiany raportu
zachowa dotychczasowy stan. Nieudany turn lub niepoprawny raport nie opublikuje
czesciowego zapisu. Raport i wynik z awaryjnego initial JSON w Incident/Flow
zostana ujednolicone przed pierwsza dozwolona edycja follow-up.

## Zakres

Poziom L3: neutralny tool i lifecycle report store, hidden scope/allowlist,
cztery feature-owned follow-up flow, walidacja i projekcja wyniku, live job,
local history, import/export, UI korzystajace z aktualnego snapshotu oraz
dokumentacja runtime. Publiczny chat request moze pozostac bez zmiany
ksztaltu; zmieni sie znaczenie zwracanych `report` i `result`.

## Non-goals

- Automatyczna rewizja raportu po zwyklym pytaniu lub samym odczycie tools.
- Edycja raportu w Config Drift Viewer, Change Verification i assessmentach.
- Nowa sesja Copilota, ponowne zbieranie calego evidence albo dolaczanie
  pelnego raportu do kazdego promptu follow-up.
- Reczne edytowanie raportu w UI poza follow-up chatem.

## Ograniczenia i ryzyka

- Naturalnojezykowe rozpoznanie jawnej prosby jest odpowiedzialnoscia modelu
  prowadzonego feature-owned instrukcja. Tool sprawdza scope, digest i
  jednoznacznosc patcha, ale nie ocenia semantyki intencji operatora.
- Body sekcji moze byc duze; odczyt jest celowany do jednej sekcji, a nie
  kopia calego raportu w kazdym turnie.
- `report_update_meta` zastępuje globalne metadata, wiec instrukcja musi
  wymagac zachowania niezmienianych pol po odczycie.
- Walidatory UI/UX i mapowanie source references musza zachowac pinned
  repository scope. Edycja nie moze obejsc ograniczen initial result.
- Stary zapisany run nie ma gwarancji poprawnego raportu do mutacji. Brak
  kompatybilnosci wstecznej oznacza jawne odrzucenie nieobslugiwanej koperty,
  bez cichej migracji.
- Rollback: usuniecie nowego toola z follow-up allowlist oraz wylaczenie
  instrukcji mutacji; initial report tools i odczyt pozostaja dostepne.

## Baseline i conformance delta

- Platforma: cztery wspolne `report_*` tools zapisuja efemeryczny report
  initial; `report_get_current` zwraca manifest bez pelnego body. Delta:
  atomowy patch, celowany odczyt body i ponowna rejestracja aktualnego
  raportu na czas jednego follow-up turnu.
- Incident: follow-up ma scope Elastic/GitLab/DB/opctx i dopuszcza report
  tools w allowliscie, lecz nie ma hidden `REPORT_ID` ani `initialReport`.
  Delta: aktualny raport w scope, jawna polityka edycji, remapowanie wyniku.
- Flow: follow-up korzysta z tej samej allowlisty co initial, ale ukryty
  report scope i `initialReport` istnieja tylko dla initial. Delta jak wyzej.
- UI Explorer: follow-up ma piec read-only GitLab tools; report tools i
  hidden report scope sa wylaczone. Delta: odczyt/mutacja tylko biezacego
  raportu i ponowna walidacja source refs oraz wyniku.
- UX Inspector: follow-up ma target/source/feedback tools bez report tools.
  Delta: raport i sekcja `answer` sa aktualizowane w tym samym pinned scope;
  mapper utrzymuje zgodnosc `result` i `report`.
- Konsumenci: live job snapshots, local run continuation, export/import,
  Angular result/share i shared chat UI. Zmiana wartosci `report/result`
  dotyka backendu oraz UI; nie dodaje nowego pola publicznego DTO.
- Znany drift: Incident/Flow maja report tools w follow-up allowliscie bez
  aktywnego report store. Ta zmiana usuwa niespojnosc przez pełne podpiecie
  report scope, zamiast pozostawic martwe tools.

## Kryteria akceptacji

Jawna prosba o korekte naglowka, calej sekcji, unikalnego fragmentu albo
metadata dziala w kazdym z czterech follow-up chatow. Odczyt raportu na
potrzeby weryfikacji nie mutuje go. Patch odrzuca stary digest, nieznana
sekcje i niejednoznaczny fragment. Live i history continuation pokazuja
ten sam zaktualizowany report/result, a export zawiera aktualna wersje.
Wiadomosc bez prosby o edycje nie zmienia raportu. Zakres innych feature'ow
pozostaje bez zmian.

## Kroki

- [x] Krok 1: rozszerzyc platformowe report tools/store o celowany odczyt i
  atomowy `report_patch_section`; testy tool schema, hidden scope, stale
  digest, unikalnosci fragmentu, budgetu i lifecycle potwierdza rezultat.
- [x] Krok 2: podpiac aktualny raport, hidden scope i jednolita zasade
  jawnej prosby do initial/follow-up runtime czterech feature'ow; testy
  allow/deny, promptow i skilli potwierdza brak mutacji przy zwyklym pytaniu.
- [x] Krok 3: po follow-up zwalidowac i opublikowac report oraz zmapowany
  result w live jobie i local continuation kazdego feature'a; testy
  sprawdza sukces, blad, brak zmiany, fallback initial i source refs.
- [x] Krok 4: utrwalic aktualny snapshot w historii i eksporcie, sprawdzic
  import oraz wspolne widoki wyniku/share; testy Angulara, build Angulara,
  `mvn -q -Pbackend-dev clean package`, `git diff --check` i przeglad
  fikcyjnych danych testowych zamkna weryfikacje.
- [x] Krok 5: zaktualizowac dokumenty architektury czterech feature'ow,
  kontrakt platformy i opis rollbacku na stan wynikowy; test dokumentacji
  i review diffu potwierdza brak sprzecznych zasad read-only follow-up.

## Doprecyzowanie podgladu zmian

Operator potrzebuje powiazac aktualizacje raportu z odpowiedzia, ktora ja
wykonala, oraz porownac surowa tresc przed i po bez interpretacji Markdownu.
Wspolny chat juz pokazuje referencje wiadomosci; zapis roznicy korzysta z tego
samego modelu evidence, bez nowego pola publicznego DTO. Dla zakonczonego joba
polling chatu nie powinien aktywowac wskaznika przebiegu analizy.

Zakres L1: neutralny zapis zmienionych pol raportu w evidence odpowiedzi,
wspolny chip i modal, sanitizacja historii UI Explorera oraz warunek badge'a
w UI Explorerze i UX Inspectorze. Konsumenci: cztery live i local follow-up,
historia, eksport/import oraz shared chat. Ryzykiem jest rozmiar evidence przy
dlugich sekcjach; zapis obejmuje tylko zmienione elementy. Inne feature'y i
sam raport nie zyskuja nowego trybu edycji.

- [x] Utrwalic przed i po tylko po zaakceptowanej zmianie, w live i local
  continuation czterech feature'ow; test mappera oraz testy backendu.
- [x] Pokazac jeden badge i wspolny podglad raw Markdown przy referencjach
  odpowiedzi, z grupami sekcji i kolorami dodanych oraz usunietych linii;
  test komponentu, testy Angulara i build produkcyjny.
- [x] Potwierdzic brak spinnera przebiegu analizy podczas follow-up, przejscie
  pelnego backend builda oraz review diffu.
