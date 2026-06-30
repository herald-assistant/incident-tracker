# Report Artifact Plan: Follow-Up Chat Updates

## Cel

Ten plan opisuje dodanie mozliwosci aktualizacji raportu podczas follow-up
chat. Uzytkownik po initial analysis widzi raport, a potem moze poprosic o
zmiane semantyczna, doprecyzowanie albo dodatkowe przeszukanie. AI w ramach
kolejnego `sendAndWait` korzysta z normalnych tools diagnostycznych oraz
generycznych report tools, a backend po zakonczeniu zapisuje ostatnia wersje
raportu w rezultacie.

MVP nie wersjonuje raportu. Przechowujemy tylko aktualny snapshot raportu.

## Zasady pracy z planem

- Przed implementacja kazdego kroku opisujemy zakres, dotkniete pakiety/pliki
  i plan weryfikacji.
- Implementacja kroku startuje dopiero po akceptacji zakresu.
- Po zakonczeniu kroku aktualizujemy ten plik: oznaczamy krok jako wykonany,
  dopisujemy odchylenia i aktualizujemy dalsze kroki.
- Jezeli w trakcie prac pojawi sie lepszy kierunek, najpierw aktualizujemy
  plan, potem implementujemy.

## Zalozenia MVP

- Jeden raport jest edytowany przez jedna osobe.
- Dla jednego joba nie ma rownoleglych follow-up `sendAndWait`.
- Wspolbieznosc ogranicza sie do tool calls w ramach jednej sesji AI.
- Raport jest stanem aplikacji/job state/local workspace, nie jedynie stanem
  sesji Copilota.
- `reportId` i aktualny raport sa kontrolowane przez backend.
- Model moze odczytac aktualny raport przez `report_get_current`, ale nie
  podaje `reportId`.
- Follow-up odpowiedz chatowa moze byc krotkim opisem zmian; zrodlem prawdy
  wyniku jest zaktualizowany `AnalysisReport`.

## Docelowy przebieg

```text
1. User wysyla follow-up message.
2. Backend blokuje rownolegle follow-up dla tego joba.
3. Backend laduje aktualny AnalysisReport do report session store.
4. Hidden ToolContext dostaje reportId i allowed section ids.
5. AI uzywa report_get_current, tools diagnostycznych i report_upsert_section.
6. Po sendAndWait backend pobiera ostatni snapshot raportu.
7. Backend zapisuje raport w job result/local workspace.
8. Chat message zapisuje odpowiedz AI, tool evidence, activity i info o zmianie raportu.
```

## Kroki

### 1. Kontrakt follow-up update

Status: [ ]

Przed wykonaniem:

- Opiszemy minimalny kontrakt danych potrzebnych do follow-up update:
  aktualny `AnalysisReport`, `reportId`, feature, allowed section ids i
  wiadomosc uzytkownika.
- Ustalimy, czy chat message ma miec osobne pole `reportUpdated` /
  `updatedReportSectionIds`, czy wystarczy porownanie snapshotu po stronie UI.

Implementacja:

- Rozszerzenie shared/chat DTO tylko tam, gdzie potrzebne dla wielu feature'ow.
- Brak wersjonowania historii; po update trzymamy ostatni raport.

### 2. Report snapshot w follow-up requestach feature'ow

Status: [ ]

Przed wykonaniem:

- Opiszemy zmiany w `AnalysisAiChatRequest` i
  `FlowExplorerFollowUpChatRequest`.
- Potwierdzimy, ze request follow-up dostaje aktualny raport z job state, a nie
  z finalnej odpowiedzi Copilota.

Implementacja:

- Incident Analysis przy `startChatMessage` przekazuje aktualny raport do
  follow-up preparation.
- Flow Explorer przy `startChatMessage` przekazuje aktualny raport do follow-up
  preparation.
- Local workspace continuation takze odtwarza aktualny raport.

### 3. Hidden context dla follow-up report update

Status: [ ]

Przed wykonaniem:

- Opiszemy, jakie hidden context keys sa ustawiane podczas follow-up:
  `reportId`, feature, allowed section ids i ewentualnie tryb `follow-up`.
- Potwierdzimy, ze model-facing schema report tools nie przyjmuje `reportId`.

Implementacja:

- Follow-up run assemblery dopisuja hidden context raportu.
- Report tools odczytuja scope z `ToolContext`.
- Brak aktywnego raportu daje kontrolowany wynik toola, nie wyjatek zabijajacy
  cala sesje.

### 4. Ladowanie raportu do report session store

Status: [ ]

Przed wykonaniem:

- Opiszemy, gdzie w execution flow rejestrowany jest aktualny raport.
- Ustalimy, czy registration jest w `CopilotRunRequest`, prepared session czy
  bezposrednio w gateway.

Implementacja:

- Przed `sendAndWait` runtime rejestruje aktualny raport jako aktywny snapshot.
- W trakcie sesji report tools aktualizuja ten snapshot.
- Po `sendAndWait` runtime zwraca zaktualizowany snapshot.
- W `finally` runtime sprzata session-bound report state.

### 5. Prompt i skill follow-up

Status: [ ]

Przed wykonaniem:

- Opiszemy zmiany w promptach follow-up.
- Dla Flow Explorera szczegolnie dotyczy to obecnego promptu, ktory mowi, ze
  follow-up nie regeneruje pelnego JSON result.
- Ustalimy jezyk instrukcji: odpowiedz chatowa moze byc Markdownem, ale jesli
  uzytkownik prosi o zmiane raportu, AI ma zapisac zmiany przez report tools.

Implementacja:

- Follow-up prompt mowi modelowi, kiedy uzyc `report_get_current`.
- Follow-up prompt mowi modelowi, ze zmiany w raporcie zapisuje przez
  `report_upsert_section` / `report_update_meta`.
- Runtime skille sa aktualizowane po polsku.

### 6. Aktualizacja rezultatu po follow-up

Status: [ ]

Przed wykonaniem:

- Opiszemy, jak po wykonaniu chat response aktualizujemy job result.
- Ustalimy, czy mapowanie z generycznego raportu na feature-specific result
  jest wspolna metoda uzywana po initial i follow-up.

Implementacja:

- Po follow-up Incident Analysis mapuje aktualny raport na obecny publiczny
  `AnalysisResultResponse`.
- Po follow-up Flow Explorer mapuje aktualny raport na
  `FlowExplorerResultResponse`.
- Chat message zapisuje odpowiedz AI oraz ewentualne info o zmianie raportu.

### 7. Tool evidence i activity trace

Status: [ ]

Przed wykonaniem:

- Opiszemy, czy report tool calls maja byc widoczne w timeline jak inne tools.
- Ustalimy, czy zapis sekcji raportu generuje osobna sekcje evidence, czy
  wystarczy activity event + aktualny raport.

Implementacja:

- Report tool calls sa widoczne w `aiActivityEvents`.
- Opcjonalnie dodajemy user-visible evidence category typu `report/tool-updates`
  tylko jesli UI potrzebuje jawnego changelogu.
- Na MVP mozna pokazac zmiane raportu przez result snapshot, bez osobnego
  changelogu.

### 8. Local workspace continuation

Status: [ ]

Przed wykonaniem:

- Opiszemy zmiany w lokalnej kontynuacji dla Incident Analysis i Flow Explorer.
- Potwierdzimy, ze importowany export read-only nadal nie moze kontynuowac
  chatu bez lokalnej sesji Copilota.

Implementacja:

- Local run persistence zapisuje aktualny raport.
- Local run chat handler laduje raport przed follow-up.
- Po follow-up local run persistence zapisuje zaktualizowany raport.

### 9. UI minimalne

Status: [ ]

Przed wykonaniem:

- Opiszemy, jak UI ma reagowac na raport zaktualizowany przez follow-up.
- Ustalimy, czy wystarczy odswiezyc glowny panel wyniku z pollingu, czy
  potrzebny jest badge przy wiadomosci chat.

Implementacja:

- Polling po follow-up pokazuje aktualny raport.
- Chat message moze pokazac krotka informacje, ze raport zostal zaktualizowany,
  jezeli backend wystawi taka informacje.
- Brak osobnej historii wersji w MVP.

### 10. Testy i dokumentacja

Status: [ ]

Przed wykonaniem:

- Wskazemy testy dla sekwencyjnego follow-up update bez wersjonowania.
- Wskazemy dokumenty architektury do aktualizacji po wdrozeniu.

Implementacja:

- Test: follow-up bez aktywnego raportu nie moze uzyc report tools.
- Test: follow-up z aktywnym raportem moze zaktualizowac sekcje.
- Test: job result po follow-up odzwierciedla ostatni snapshot raportu.
- Test: aktywny follow-up blokuje drugi rownolegly follow-up.
- Aktualizacja dokumentow runtime flow i key decisions.
