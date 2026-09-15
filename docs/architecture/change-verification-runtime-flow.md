# Change Verification runtime flow

## Cel i ownership

Change Verification jest dedykowanym feature'em, ktory porownuje zmiane z
regulami zapisanymi przez autora w Jira, powiazanym Confluence, operator notes
oraz aktywnych instrukcjach repozytorium. Wynikiem nie jest zbior niezaleznych
findings ani raport tworzony obok wyniku, lecz jeden kanoniczny `ruleLedger`.

Backend feature'a mieszka w
`src/main/java/pl/mkn/tdw/features/changeverification`, a workspace Angulara w
`frontend/src/app/features/change-verification`. Feature moze korzystac z
`aiplatform`, `agenttools`, `integrations`, `shared` i `common`, ale nie moze
importowac innych pionowych feature'ow.

## Publiczne wejscie i lifecycle joba

Kanoniczne endpointy to:

- `POST /api/change-verification/jobs` - tworzy asynchroniczny job,
- `GET /api/change-verification/jobs/{jobId}` - zwraca aktualny snapshot joba,
- `GET /change-verification` - workspace operatora w SPA.

Request przyjmuje `issueKey` albo `issueUrl`, flagi
`checkStoryCompliance`/`checkInstructionCompliance`, opcjonalne
`userInstructions` oraz preferencje `model` i `reasoningEffort`. Co najmniej
jeden zakres musi byc wlaczony. Brak flag oznacza wlaczenie obu zakresow.

`ChangeVerificationJobService` uruchamia prace przez `TaskExecutor`, a
`ChangeVerificationJobState` jest wlascicielem statusu, krokow, evidence,
aktywnosci AI, przygotowanego promptu, usage, rezultatu i reportu. Kazda
istotna zmiana stanu jest przekazywana do
`ChangeVerificationLocalRunPersistence`, dzieki czemu run jest widoczny w
lokalnej historii analiz.

## Kolejnosc runtime

1. Request jest walidowany i normalizowany, a z URL w razie potrzeby
   wyprowadzany jest Jira key.
2. `ChangeVerificationSourceDiscoveryService` pobiera material target issue:
   opis, acceptance criteria, komentarze, parent context, direct subtasks,
   remote links i strony Confluence zwrocone przez integracje Jira.
3. Merge requesty sa wyszukiwane dla target issue oraz jego bezposrednich
   subtaskow. Parent i sibling issues nie rozszerzaja automatycznie zakresu MR.
   Powtorzone MR-y sa deduplikowane.
4. Dla kazdego repozytorium wybierany jest `analysisRef`: preferowany source
   branch, z kontrolowanym fallbackiem do target branch. Ograniczenia wyboru
   ref trafiaja do diagnostyki discovery.
5. Gdy wlaczono Instruction Compliance, instruction discovery jest wykonywane
   raz dla unikalnego `(projectPath, analysisRef)`. Zmienione sciezki ze
   wszystkich MR-ow tego scope'u sa laczone i deduplikowane.
6. `ChangeVerificationOperationalContextMatcher` wzbogaca snapshoty
   repozytoriow o dopasowanie do neutralnego katalogu Operational Context.
7. `ChangeVerificationPromptPreparationService` buduje kanoniczny prompt oraz
   inline artifacts opisane ponizej.
8. `ChangeVerificationCopilotRunRequestAssembler` tworzy jedna sesje Copilota
   z feature-owned promptem, skillami, hidden tool context i jawna allowlista
   tools.
9. `ChangeVerificationAiResponseParser` waliduje cala odpowiedz jako jeden
   rule ledger. Niepoprawna odpowiedz nie jest naprawiana przez ciche usuwanie
   pojedynczych wpisow.
10. Backend wylicza decyzje, buduje deterministyczny report, finalizuje job i
    utrwala snapshot.

## Semantyka zrodel

Target issue jest glownym zakresem weryfikacji.

- `STORY` obejmuje obowiazujace reguly z acceptance criteria, jawnych wymagan
  opisu i komentarzy oraz Confluence wyraznie powiazanego z target issue.
- `INSTRUCTION` obejmuje sprawdzalne zobowiazania z aktywnych instrukcji
  repozytorium dla zmienionego kodu.
- Konkretne wymaganie zapisane w `userInstructions` zachowuje source type
  `OPERATOR_INSTRUCTION`; zwykla notatka fokusujaca nie staje sie regula.
- Parent, subtaski i szerszy Confluence sa materialem interpretacyjnym. Ich
  tresc nie staje sie automatycznie regula target issue. Direct subtasks moga
  wskazywac implementacje i sa uzywane przy MR discovery.
- Kontrola zaproponowana przez model ma scope `ADDITIONAL` i source
  `AI_SUGGESTION`; nigdy nie jest przedstawiana jako wymaganie autora.

Kazda regula zrodlowa wystepuje dokladnie raz. `source.quote` zachowuje krotki,
doslowny fragment autora, `source.reference` pozwala wrocic do materialu, a
`normalizedRule` jest osobna, sprawdzalna interpretacja AI. Konflikt zrodel
jest reprezentowany przez `interpretationType=CONFLICTING`, a nie przez cichy
wybor jednej wersji.

## Kontrakt rule ledger

Publiczny `ChangeVerificationResultResponse` zawiera `ruleLedger`, ktory ma:

- `rules` - jedyne zrodlo prawdy o regulach autora,
- `additionalChecks` - od zera do pieciu dodatkowych kontroli AI,
- `visibilityLimits` - ograniczenia przypiete do konkretnych rule ids,
- `decision` - deterministyczna projekcje `rules`,
- flagi informujace, ktore zakresy zostaly zamowione.

Kazdy `ChangeVerificationRuleResultResponse` przechowuje source, cytat,
normalizacje, typ interpretacji, outcome, `releaseImpact`, conclusion,
evidence, missing evidence i action. Dodatkowe checks maja ponadto rationale,
risk, signals i confidence.

Outcome ma zamkniety zbior:

- `SATISFIED` - wymaga co najmniej jednego konkretnego evidence i
  `releaseImpact=NONE`,
- `NOT_SATISFIED` - wymaga action opisujacego usuniecie rozjazdu,
- `NOT_VERIFIED` - wymaga missing evidence oraz action prowadzacego do
  rozstrzygniecia.

`releaseImpact` jest osobna osia: `NONE`, `REVIEW` albo `BLOCKER`. Nie dodawaj
statusu `WARNING` ani nie koduj impactu w outcome.

Backend zawsze przelicza decyzje z `rules`; wartosc dostarczona z zewnatrz nie
jest zrodlem prawdy:

| Warunek | Decyzja |
| --- | --- |
| brak regul zrodlowych | `INCONCLUSIVE` |
| co najmniej jedna `NOT_SATISFIED` | `NEEDS_ACTION` |
| brak niezgodnosci i co najmniej jedna `NOT_VERIFIED` | `NEEDS_EVIDENCE` |
| wszystkie reguly `SATISFIED` | `READY` |

`additionalChecks` nie sa uwzgledniane w tej decyzji niezaleznie od ich
outcome ani impactu.

## Walidacja odpowiedzi AI

Model zwraca dokladnie jeden obiekt JSON. Parser wymaga obecnosci tablic
`rules`, `additionalChecks`, `visibilityLimits` oraz tablic evidence,
missingEvidence i signals w kazdym wpisie. Odrzuca cala odpowiedz, gdy:

- enum, wymagane pole albo invariant outcome jest niepoprawny,
- source rule ma scope `ADDITIONAL`, source `AI_SUGGESTION` albo
  `interpretationType=INFERRED`,
- additional check nie ma kompletnej semantyki inferred albo jest ich wiecej
  niz piec,
- dowolne id jest powtorzone,
- visibility limit nie wskazuje istniejacego rule id.

Fallback zawiera pusty ledger, decyzje `INCONCLUSIVE` oraz jedno globalne
ograniczenie widocznosci wyjasniajace blad odpowiedzi. Nie przywracaj
czesciowego parsowania, legacy aliases ani niezaleznego model-generated
statusu.

## Prompt, artifacts, skille i tools

Prompt jest artifact-first. Kanoniczne artifacts to:

- `change-verification/source-discovery.md`,
- `change-verification/jira-issue.md`,
- `change-verification/repository-scope.md`,
- `change-verification/merge-requests.md`,
- `change-verification/instruction-context.md`,
- `change-verification/response-contract.md`.

Sesja wskazuje tylko dwa skille:

- `change-verification-orchestrator`,
- `change-verification-compliance-check`.

Model zaczyna od materialu inline i wykonuje tylko celowane odczyty potrzebne
do oceny konkretnej reguly. Allowlista zawiera wybrane read-only GitLab tools
oraz `opctx_*`. Local filesystem, shell, terminal, Database tools i report
tools nie naleza do tego flow.

Packaged skille sa immutable seedem. Runtime loader dopisuje tylko brakujace
pliki do efektywnego katalogu i nie nadpisuje lokalnej tresci. Przy zmianie
packaged skilla najpierw porownaj effective kopie z poprzednim seedem. Kopie
bez zmian mozna usunac i pozwolic loaderowi odtworzyc; lokalnie zmienionych
skilli nie usuwaj bez jawnej decyzji operatora.

## Ograniczenia widocznosci

Ograniczenie zwrocone przez model musi wskazywac `affectedRuleIds` i wyjasniac,
jak brak widocznosci wplynal na ocene tych regul. Surowe bledy transportu,
truncation, brak MR, branch fallback i pozostale limity discovery zostaja w
krokach joba oraz evidence diagnostycznym. Nie kopiuj ich do ledgeru jako
samodzielnych regul, findings ani globalnych actions.

## Report, import/export i UI

`ChangeVerificationReportMapper` buduje report deterministycznie z ledgeru.
Sekcja `RULE_LEDGER` jest projekcja source rules, a opcjonalna sekcja
`ADDITIONAL_CHECKS` projekcja dodatkowych kontroli. Sesja AI nie tworzy
rownoleglego Markdown i nie wywoluje report tools.

Aktualny eksport ma schema `tdw.change-verification-export`, version `6` oraz
result contract `change-verification-result-v5`. Import przyjmuje tylko
zakonczony job w tym formacie, ponownie waliduje wszystkie reguly i przelicza
decyzje. Starsze formaty sa jawnie odrzucane; feature V1 nie utrzymuje
migratorow ani aliasow kompatybilnosci.

UI pokazuje najpierw decyzje i liczniki, a potem jedna liste regul z filtrami
`Wymagaja dzialania`, `Spelnione`, `Wszystkie`. Story i Instructions sa
pogrupowane, ale jedna regula ma tylko jeden rozwijany element: cytat, source,
conclusion i action sa widoczne przed szczegolami. Dodatkowe kontrole AI sa
domyslnie zwiniete i jawnie opisane jako niewplywajace na decyzje. Po
zakonczeniu joba rozbudowany composer przechodzi w kompaktowy kontekst.

## Mapa implementacji

- `source/ChangeVerificationSourceDiscoveryService` - Jira, MR, branch refs,
  instruction scopes i snapshoty repozytoriow,
- `ai/preparation/ChangeVerificationPromptPreparationService` - prompt i
  artifacts,
- `ai/copilot` - session assembly, hidden context, tool descriptions, allowlist
  i tool evidence capture,
- `ai/ChangeVerificationAiResponseParser` - scisla granica odpowiedzi modelu,
- `job/api` - publiczny request, snapshot i rule-ledger DTO,
- `job/state/ChangeVerificationJobState` - lifecycle i materializacja wyniku,
- `job/report/ChangeVerificationReportMapper` - deterministyczna projekcja,
- `job/export/ChangeVerificationExportEnvelope` - envelope i diagnostyka,
- `frontend/.../change-verification-rule-ledger` - kanoniczna prezentacja
  wyniku,
- `frontend/.../change-verification-import-export.utils` - walidacja i
  serializacja formatu v6/v5.

## Zasady dalszych zmian i weryfikacja

Zmiana requestu, ledgeru, decision rules, promptu, skilli, tool policy,
source scope, reportu, import/export albo UI jest co najmniej L1 i wymaga
consumer audit oraz nowego lub zaktualizowanego need/plan zgodnie z
`analysis-feature-delivery-playbook.md`. Zakonczonych planow nie odtwarzaj jako
zrodla prawdy; historie zachowuje Git, a aktualny stan opisuje ten dokument.

Minimalna macierz dla zmian tylko w feature'u:

- backend: `mvn -q "-Dtest=*ChangeVerification*" test`,
- frontend: `npm --prefix frontend test -- --include
  "src/app/features/change-verification/**/*.spec.ts"`.

Zmiana wspolnego kontraktu backend-frontend wymaga dodatkowo pelnych testow i
builda Angulara, a nastepnie `mvn -q -Pbackend-dev clean package`, zgodnie z
root `AGENTS.md`.
