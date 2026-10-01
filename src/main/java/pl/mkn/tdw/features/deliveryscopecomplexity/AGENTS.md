# AGENTS

## Zakres

Ten katalog zawiera dedykowany feature Delivery Scope Complexity.
Feature ocenia obserwowalna zlozonosc dostarczonych zmian dla projektu Jira i
zakresu dat.

## Zasady

- Nie importuj innych pakietow `features.*`.
- Jira search, issue material, GitLab i Confluence sa reusable capability w
  `integrations.*`.
- Feature posiada request, job state, source orchestration, Delivery Units,
  evidence packet, prompt, skill, scoring, wynik i local run mapping.
- Evidence packet przekazuje do AI pelna tresc zwrocona przez integracje bez
  lokalnego przycinania opisow, dokumentow, MR-ow, plikow ani diffow.
- AI nie dostaje Story Points, komentarzy, worklogow, autorow, assignee ani
  reviewerow.
- AI nie dostaje nazwy typu Jira jako metadanej. Artifact wskazuje oceniane
  zadania i potwierdzone relacje nadrzedne/podrzedne. Parent oraz dzieci
  spoza `unit.issues()` sa kontekstem poza zakresem oceny.
- Opis rodzica nie dowodzi dostarczenia jego calego zakresu. Oceniaj union
  zachowania potwierdzonego przez MR-y jednostki bez zwielokrotnienia przez
  podzial na zadania; hierarchia nie zmienia grafu Delivery Units.
- Zachowaj ograniczenia materialu powiazanego oraz pelne odmienne tresci.
  Identyczne zadania/dokumenty deduplikuj, a oryginalny `issueType` zachowaj
  w metadanych operatorskiego wyniku i CSV.
- Model zwraca dla szesciu wymiarow `score` `0-100`, `scopeSignal` `0-1` i
  evidence. Kazdy niezerowy score wymaga referencji do artifactu.
- Backend deterministycznie liczy `scope`, `scaledScore`, wazone punkty i
  finalny wynik `0-200`; AI nie zwraca wyliczonych skladowych.
- Kazda istotna zmiana live state zapisuje ten sam local run snapshot.
- Kazda jednostka posiada `aiInvocations` z prompt/raw, statusem, zakresem,
  ustaleniami, usage i bledem kazdej proby. Prompt zapisuj przed wykonaniem,
  raw przed parsowaniem; aktualizacja tego samego id nie sumuje usage ponownie.
- Podzial MR/plikow nalezy do feature'a, neutralny budzet i wybor/potwierdzenie
  tieru do platformy. Kazda czesc ma pelny Jira/Confluence i manifest, zwraca
  ustalenia bez punktowania; synteza ocenia union raz. Kod nie jest przycinany,
  zakres przekazanego materialu i referencje sa walidowane; nierozdzielny
  material ma jawny blad. Coverage ustalen przypina aplikacja, nie model.
- Bledy deklaracji coverage, confidence i pol opisowych sa jawnymi
  ostrzezeniami w visibilityLimits; nie blokuja poprawnego rdzenia.
  Niepoprawne confidence daje 0 z opisem przyczyny. Referencje poza zakresem,
  niepoprawne wymiary i utrata zachowan/referencji/zaleznosci przy redukcji
  nadal blokuja wynik. Raw pozostaje niezmieniony; nie ma ponowienia AI
  dla korekty metadanych. Ostrzezenia sa przenoszone deterministycznie.
- Czesci/redukcje/synteza sa sekwencyjne w bounded workerze, z jednym deadline,
  nowymi sesjami i pustymi tools. Tylko typowany overflow koryguje podzial.
  Awaria czesci blokuje finalna ocene, pozostawiajac partial evidence.
- UI, agregaty, CSV i Trends licza jedna ocene jednostki niezaleznie od liczby
  wywolan. Aktualny format V1 wymaga aiInvocations, bez migratora starego zapisu.
- Surowa odpowiedz AI jest zapisywana przy wywolaniu Delivery Unit przed parsowaniem,
  pozostaje dostepna rowniez po bledzie kontraktu i jest prezentowana w UI
  jako domyslnie zwiniety material diagnostyczny.

## Weryfikacja

- Testuj Jira qualification, graf issue-MR, scoring, partial persistence i
  brak zaleznosci do sibling feature'ow.
