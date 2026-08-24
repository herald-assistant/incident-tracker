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
- Model zwraca dla szesciu wymiarow `score` `0-100`, `scopeSignal` `0-1` i
  evidence. Kazdy niezerowy score wymaga referencji do artifactu.
- Backend deterministycznie liczy `scope`, `scaledScore`, wazone punkty i
  finalny wynik `0-200`; AI nie zwraca wyliczonych skladowych.
- Kazda istotna zmiana live state zapisuje ten sam local run snapshot.
- Surowa odpowiedz AI jest zapisywana przy Delivery Unit przed parsowaniem,
  pozostaje dostepna rowniez po bledzie kontraktu i jest prezentowana w UI
  jako domyslnie zwiniety material diagnostyczny.

## Weryfikacja

- Testuj Jira qualification, graf issue-MR, scoring, partial persistence i
  brak zaleznosci do sibling feature'ow.
