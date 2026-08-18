# AGENTS

## Zakres

Ten katalog zawiera dedykowany feature Delivery Complexity Assessment.
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
- Model zwraca wymiary `0-4` wedlug feature-local kotwic behawioralnych;
  kazdy niezerowy wymiar wymaga referencji do artifactu, a backend wylicza
  Delivered Story Points.
- Kazda istotna zmiana live state zapisuje ten sam local run snapshot.

## Weryfikacja

- Testuj Jira qualification, graf issue-MR, scoring, partial persistence i
  brak zaleznosci do sibling feature'ow.
