# Krok 11: Frontend I Workflow Operatora

## Cel

Zobaczyc system oczami operatora i zrozumiec, jakie dane frontend potrzebuje od
job flow.

## Po tym kroku rozumiesz

- dlaczego frontend bazuje glownie na job API,
- jak prezentowany jest progres i evidence,
- po co UI pokazuje prepared prompt i dane diagnostyczne,
- jak wyglada drugi widok `/evidence`,
- jak dziala import i eksport zakonczonej analizy.

## Model pracy operatora

1. frontend pobiera katalog modeli z `GET /analysis/ai/options`,
2. operator wpisuje `correlationId` i opcjonalnie wybiera model AI oraz
   dostepny dla niego `reasoningEffort`,
3. frontend startuje job,
4. frontend polluje status,
5. pokazuje overview, kroki evidence, prompt i finalny wynik,
6. moze wyeksportowac zakonczony job do JSON albo zaimportowac go z pliku,
7. w razie potrzeby operator uzywa tez widoku `GET /evidence`.

Aktualny frontend ma dwa route'y Angulara:

- `/` jako glowny `analysis-console`,
- `/evidence` jako `evidence-console` z formularzami helper endpointow.

## Co jest wazne dla backend developera

- `AnalysisJobResponse` jest projekcja dla UI, nie nowy model domenowy,
- lista modeli i effortow pochodzi z backendu, a frontend tylko renderuje
  odpowiedz `GET /analysis/ai/options`,
- job snapshot pokazuje wybrane `aiModel` i `reasoningEffort`, ale te pola sa
  tylko preferencja sesji AI, nie scope'em evidence,
- frontend potrzebuje aktualnego kroku, historii krokow, evidence i bledow,
- prepared prompt jest przydatny diagnostycznie nawet wtedy, gdy sesja AI
  zawiedzie,
- `toolEvidenceSections` jest osobna lista od `evidenceSections`, bo pokazuje
  dane dociagniete przez AI tools juz w trakcie kroku `AI_ANALYSIS`,
- widok krokow ma specjalne rendery dla logow Elasticsearch, code/tool evidence
  z GitLaba, runtime signals Dynatrace i wynikow DB tools,
- GitLab tool evidence pokazuje juniorowi tylko nazwe/sciezke pliku, powod
  pobrania (`reason`) i kod,
- DB tool evidence pokazuje powod sprawdzenia (`reason`) oraz wynik jako prosta
  tabela albo JSON bez diagnostycznych pytan, parametrow i technicznych badge'y,
- route `/evidence` nie zna polaczen do backendow zewnetrznych; odpala tylko
  helper endpointy Spring Boot.

## Przeczytaj w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/job/AnalysisJobResponse.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/job/AnalysisJobState.java`
- `src/main/java/pl/mkn/incidenttracker/ui/FrontendRouteController.java`
- `frontend/src/app`

## Sprawdz lokalnie

- uruchom `GET /`,
- przejdz przez widok analizy i evidence,
- zobacz, ktore pola pochodza z job snapshotu,
- sprawdz import/eksport zakonczonego joba,
- przejdz do `/evidence` i odpal helper endpointy bez opuszczania SPA.

## Checkpoint

- Kiedy zmiana backendowa wymaga tez zmiany UI?
- Jakie dane sa diagnostycznie wazniejsze w job flow niz w sync response?
- Ktore elementy UI sa projekcja runtime `toolEvidenceSections`, a ktore
  glownych `evidenceSections`?
