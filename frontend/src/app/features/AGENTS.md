# Frontend Features AGENTS

## Feature vs Workbench

`Incident Analysis` jest pierwszym dedicated feature'em. Elastic Logs,
GitLab Source i Database Tools sa ekranami `Tool Workbench`, czyli
analysis-independent narzedziami operatorskimi nad reusable capability.

Nie mieszaj tych pojec:

- feature zbiera kontekst, uruchamia workflow i zwraca wynik operatorowi,
- workbench pozwala recznie testowac capability, debugowac integracje i
  zebrac input do dalszej analizy.

## Shared Feature UX

Dedicated feature moze miec wlasny start request, prompt, policy i
merytoryczny result contract, ale przekrojowe elementy workflow powinny byc
wspolne. Przed dodaniem lokalnego UI/modelu dla przebiegu analizy, toku pracy
AI, follow-up chatu, evidence/tool feedbacku, usage/cost, import/export albo
powtarzalnych fragmentow wyniku, sprawdz `core/models` i `components`.

Jezeli nowy feature potrzebuje podobnego zachowania jak istniejacy feature,
najpierw wydziel albo rozszerz shared komponent/model. Lokalny feature screen
powinien tylko mapowac swoje dane, teksty i akcje do wspolnego wzorca. Celem
jest to, zeby operator po poznaniu jednego feature'a rozpoznawal te same
elementy pracy w kolejnych feature'ach.

## Incident Analysis

Incident Analysis ma byc codziennym workspace'em, nie landing page.

Zasady:

- bez hero jako pierwszego viewportu,
- kompaktowy panel startu z `Correlation ID`, `Model AI`,
  `Reasoning effort` i primary `Run analysis`,
- po starcie pokazuj pasek kontekstu runu,
- finalny wynik ma miec pelna szerokosc,
- przebieg analizy i tok pracy AI sa sekcjami pomocniczymi, domyslnie
  zwijanymi po finalnym wyniku albo imporcie pliku,
- usage moze byc kompaktowe, ale szczegoly tokenow/kosztu musza byc dostepne
  w tooltipie.

Publiczny start analizy wysyla tylko `correlationId`, `model` i
`reasoningEffort`. Nie przywracaj request fields dla branch, environment ani
GitLab group.

## Tool Workbench screens

Database Tools jest referencyjnym layoutem dla Workbench.

Zasady:

- lewy panel: shared scope i lista elementow do testu,
- glowny panel: formularz aktywnego elementu i wynik pod formularzem,
- response JSON nie trafia do stalej trzeciej kolumny,
- request preview i response JSON sa zwijalne,
- po otrzymaniu wyniku request preview moze byc domyslnie zwiniety,
- copy/download actions dla JSON maja byc spojne miedzy Workbench screens,
- status HTTP, timing i blad requestu sa widoczne przy wyniku,
- lokalne `workbench-header` sa zakazane; statyczny opis capability idzie do
  route `capabilityInfo` i topbar info tooltipa.

Workbench endpointy nie powinny eksponowac `analysisRunId`. GitLab i Database
Workbench nie powinny eksponowac `correlationId` jako incident/session scope'u.
Elastic moze miec historyczny `correlationId` w helper payloadach; traktuj to
jako drift, nie wzorzec dla nowych tools.
