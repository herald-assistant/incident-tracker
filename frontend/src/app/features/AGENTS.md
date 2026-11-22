# Frontend Features AGENTS

System Assistant (`system-assistant`) jest konfigurowalnym chatem od pierwszej
wiadomości, bez initial raportu. Transkrypt jest we wspólnej historii analiz;
localRunId otwiera istniejący chat z bieżącymi skillami i tools. Reuse shared chat,
kontrolki, polling, activity/evidence i usage. Widoczność sidebar/landing
zależy od `systemAssistantEnabled`; aktywny link nie remountuje rozmowy.
Browser Tools setup ma parametry tekstowe z zachowaniem defaultów UX Inspectora.
Sześć widoków używa jednego poziomu wspólnych zakładek: Skille (domyślna),
Kontrakty, Auth, Profile, Rozmowa i Uruchamianie w systemie. Nie przywracaj nadrzędnej
Konfiguracji ani wewnętrznego przełącznika. Zachowuj instancję shared chatu,
szkic i polling podczas zmiany widoku. Nie pamiętaj wyboru w storage.
Przewodnik pokazuje kontrolki aktywnego widoku. Skille, Kontrakty, Auth i Profile mają
wspólny zapis całego zestawu oraz błąd bezpośrednio nad przyciskiem.
Kontrakty dodawaj wyłącznie przez modal importu z nazwą i wyborem operacji; po imporcie mają
podgląd lokalnym Swagger UI, eksport bieżącego YAML-a i usuwanie. Nie przywracaj edytora YAML,
ręcznych pól kontraktu ani Try it out. Przełączanie zakładek zachowuje szkic.
Import i podgląd reuse'ują feature-owned OpenApiRenderer. Checkboxy są pluginem
Swagger UI, a stan wyboru, filtry i strony po 50 operacji należą do Angulara.
Nie przywracaj równoległej listy endpointów ani wykonywania API w ramce.

Przed utworzeniem dedicated feature'a albo zmiana L1-L3 istniejacego feature'a
przeczytaj `docs/architecture/analysis-feature-delivery-playbook.md` i
wykonaj baseline/conformance delta wymagane dla tego poziomu.

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

Workspace Overview (`features/platform-landing`) pokazuje karty dostepnych
funkcji z ikonami zgodnymi z nawigacja i opisami w jezyku polskim.

## Formularze ekranow

- Reuse'uj `app-choice-select` i `app-text-field` takze w filtrach Platform,
  Tool Workbench i Delivery; etykiety, tooltipy, hinty i bledy przekazuj jako
  parametry. Wyszukiwalne lookupy zachowuja swoje zachowanie i wspolna oprawe.
- Model AI i reasoning renderuj przez `app-ai-execution-controls`, bez
  lokalnych mapperow opcji/komunikatow ani nadpisan wysokosci. Standard to
  58 px; operacyjny chat korzysta z jawnego wariantu compact 32 px.
  Katalog, defaulty, reset reasoning i request pozostaja w feature.
- Oba assessmenty Delivery nadal korzystaja z konfiguracji AI backendu;
  wspolny komponent nie jest powodem dodania im wyboru modelu.
- Zachowaj istniejaca siatke i semantyke formularza. Zasady wymiarow, fokusu,
  dostepnosci i testow opisuje
  [standard UI](../../../../docs/architecture/frontend-ui-standards.md).

## Config Drift Viewer

Kanoniczny opis prezentacji i kontraktow jest w
[runtime flow](../../../../docs/architecture/config-drift-viewer-runtime-flow.md)
oraz [standardzie UI](../../../../docs/architecture/frontend-ui-standards.md).

- Zachowaj projekcje przypominajaca plik zrodlowy: monospace, wciecia,
  prowadnice i inline diff wartosci. Nie przywracaj kolorowych kropek,
  pustej kolumny znacznikow ani ramek wokol pojedynczych wartosci.
  Naglowek pliku pokazuje sciezke po lewej, rzeczywiste nazwy srodowisk
  i obecnosc pliku po prawej. Nie dubluj sciezek w `path-comparison`.
- Zakladki batcha korzystaja z `app-analysis-result-tabs` i paginacji
  Material, bez natywnego scrollbara. Zakladki, kontekst i wynik aktywnego
  komponentu tworza jedna powierzchnie; diff, uwagi, raport, Share i shared
  aside musza dotyczyc tego samego wybranego komponentu.
- Nazwa zakladki nie zawiera sufiksu statusu. `QUEUED`/`RUNNING` maja mini
  loader, `COMPLETED` nie ma wskaznika, `COMPLETED_WITH_LIMITATIONS` ma
  ikone info, a `FAILED` ikone bledu. Opis stanu i ograniczen przekazuj
  przez tooltip dostepny na hover/focus oraz dostepna nazwe zakladki.
- Gotowy wynik ma jeden naglowek nad plikami: kierunek porownania, liczbe
  zmian i plikow, istotny stan, Zmiany/Caly plik i Share. Kierunek pochodzi
  ze snapshotu wyniku, a licznik plikow obejmuje cale porownanie. Nie
  powtarzaj nazwy aktywnej zakladki ani naglowkow Configuration result /
  Roznice w plikach. Metadane runu i coverage sa w tooltipie informacji.
  Zachowaj rozdzial faktow i interpretacji AI bez dodatkowych kart z cieniem.
- Niepelne porownanie pozostaje widoczne niezaleznie od zakonczenia runu
  i pustego diffu. Coverage wyznaczaj z `branchExists` oraz trzech plikow
  o statusie `AVAILABLE`, zgodnie z backendem; JSON nie zawiera `complete`.
  Kompletna coverage nie uniewaznia deterministycznego `INCOMPLETE`.
- Sekcja ustalen nazywa sie `Uwagi do porównania`. Tytul, opis skutku,
  wskazowka do weryfikacji, waznosc i lokalizacja pochodza z
  `config-drift-viewer/utils/config-drift-viewer-findings.presentation.ts`.
  Reuse'uj ten mapper dla kart, BASIC Share i prezentacji sekcji raportu
  DEEP; nie tworz osobnych slownikow tekstow ani nie uruchamiaj AI.
  Prezentacja nie mutuje zapisanego raportu, findings ani eksportu JSON.
- Uwagi wskazuja rzeczywiste srodowisko, plik, parametr i linie, gdy dane
  sa dostepne. Nie wywodz braku pliku z samego `INCOMPLETE` i nie nazywaj
  nieobslugiwanej skladni dowiedzionym bledem konfiguracji. Brak danych
  albo nieznany kod ma jawny, neutralny opis. Nie ujawniaj wartosci
  wrazliwych ani ich tokenow w opisach. Kody i identyfikatory pozostaja
  w diagnostyce i opcjonalnych metadanych; odnosniki maja czytelne etykiety.
- Zero ustalen ukrywa cala sekcje wraz z filtrem. Gdy ustalenia istnieja,
  ale filtr nie daje dopasowan, pozostaw sekcje, filtr i komunikat.
- Przy zmianach weryfikuj zgodnosc tekstow UI/Share, rzeczywista coverage,
  lokalizacje i brak danych, oba przypadki pustej sekcji oraz przejscie
  miedzy komponentami. Zmiana prezentacji nie zmienia znaczenia ustalen,
  statusow, requestu, historii/importu ani granicy BASIC/DEEP.

## Release summary

W Analysis Features Release summary domyślnie występuje po Change Verification,
przed Config Drift Viewer, i używa ikony `deployed_code`. W Workspace Overview
karta Release summary występuje po Config Drift Viewer. Oba miejsca
respektują flagę dostępności feature'a.
Release summary używa wspólnego application select (także Flow Explorer) oraz
jednego branch select z paginacją, bez auto-select. Daty Od/Do (Do włącznie)
dobierają dwa first-parent commity jednego primary repo; pokazuj efektywną datę
z godziną, SHA i opis. Podgląd unieważniaj natychmiast po zmianie wyboru.
Historia otwiera także QUEUED/RUNNING i wznawia polling bez nowego startu.
Przebieg pokazuje manifest diff i dostepny patch per plik, nie pelne tresci
before/after. COLLECT_DIFF odroznia kompletna liste zmian od ograniczen patchy.
Pelny prompt jest na osobnym PREPARE_PROMPT;
przekazuj ten kod przez preparedPromptStepCode wspólnego panelu kroków.
Polling, raport, editor, chat, aside i Share pozostają shared. Nie sumuj usage
chatu drugi raz w initial job usage.
Import Release summary pozostaje read-only, a checkbox korekty raportu jest
domyślnie wyłączony. Wymagaj wybranego modelu lub modelu domyślnego backendu.
Formularz ma osobne rzędy źródeł i ustawień AI oraz dolny pasek akcji.
Używaj etykiet „Od” i „Do” bez godzin dla wybranych granic.
Wynik zaczyna się od AnalysisResultHeader z tytułem
„Zmiany bazodanowe · branch · Od – Do” wyprowadzonym z requestu zapisanego joba,
nie z edytowanego formularza.
Share używa tego samego tytułu. Nie renderuj zakładek, osobnego widoku
kompletności, statusu i kierunku przed nagłówkiem ani drugiego nagłówka raportu.
Shared report panel używa embedded=true i showTechnicalIds=false; zachowaj
kotwice sekcji, meta, appendix i źródła. Tabele mają trzy kolumny: Obiekt,
Zmiana (ze szczegółami SQL), Opis funkcjonalny. Pokazuj tylko opublikowane
sekcje; blokada przygotowania nie jest raportem. Błędy i informacja o imporcie
pozostają pod nagłówkiem, a diff i odczytane przez AI zrodla w przebiegu analizy.
Wynik release-summary-diff-report-v1 ma changeListComplete, changedFiles,
documentedTables, readSources i semanticCoverage=AI_ASSESSED; liczniki nie
sa dowodem pelnej interpretacji. visibilityLimits wyniku trafia do shared
meta raportu i Share. Import V1 odrzuca stary ksztalt skanu bez migracji.
Kontrakt opisuje
`docs/architecture/release-summary-runtime-flow.md`.

## Incident Analysis

Incident Analysis ma byc codziennym workspace'em, nie landing page.

Zasady:

- bez hero jako pierwszego viewportu,
- kompaktowy panel startu ze zrodlem logow, odpowiadajacym mu inputem,
  `Model AI`, `Reasoning effort` i primary `Run analysis`,
- po starcie pokazuj pasek kontekstu runu,
- finalny wynik ma miec pelna szerokosc,
- przebieg analizy i tok pracy AI sa sekcjami pomocniczymi, domyslnie
  zwijanymi po finalnym wyniku albo imporcie pliku,
- usage moze byc kompaktowe, ale szczegoly tokenow/kosztu musza byc dostepne
  w tooltipie.

Publiczny start analizy wysyla `source`; dla `ELASTICSEARCH` takze
`correlationId`, dla `CSV_UPLOAD` multipart `logFile`, oraz opcjonalne `model`
i `reasoningEffort`. Nie przywracaj request fields dla branch, environment ani
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

Wyjątek dla operacyjnego chatu modal-first: shared `ai-execution-controls`
obsługuje jawny `compact=true` (32 px). Shared chat posiada kompaktową
historię/edytor i slot akcji. Osadzony panel nie renderuje aside, a pełny
ekran używa standardowego aside. System Assistant
używa tych wariantów w ramce i zakładce Rozmowa. Nie kopiuj ich lokalnie.
