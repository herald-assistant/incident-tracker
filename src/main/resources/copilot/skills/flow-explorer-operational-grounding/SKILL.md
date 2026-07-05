---
name: flow-explorer-operational-grounding
description: Playbook uzycia opctx tools w Flow Explorerze - system, proces, bounded context, ownership, glossary i handoff bez traktowania katalogu jako dowodu zachowania kodu.
---

# Flow Explorer Operational Grounding

Uzywaj tego skilla, gdy Flow Explorer potrzebuje katalogowego kontekstu systemu,
procesu, resolved ownership albo handoffu.

## Cel

Zbuduj `OperationalGroundingSummary`: katalogowe nazwy i relacje potrzebne do
opisania endpointu jezykiem procesu, systemu, bounded contextu, resolved
ownership, glossary i handoffu.

## Rola Wobec Orkiestratora

Operational context pomaga nazwac:

- kanoniczny system,
- proces albo bounded context,
- resolved owner systemu albo bounded contextu,
- glossary pojec biznesowych,
- ubiquitous language dla flow endpointu,
- code-search scope,
- integracje i handoff decision.

Operational context nie jest dowodem, ze kod faktycznie wykonuje dana logike.
Zachowanie endpointu potwierdzaj artefaktami albo GitLab tools.

## Wejscia

Korzystaj z `applicationName`, `systemId`, endpoint scope, operational clues z
artefaktow, aktualnego `goal`, `sectionModes` oraz konkretnej luki katalogowej.

## Procedura

1. Zacznij od artefaktow i kanonicznego systemu.
2. Wybierz najmniejszy `opctx_*` tool call.
3. Potwierdz glossary, ownera przez system/bounded context, proces, context,
   code scope albo handoff.
4. Zwroc `OperationalGroundingSummary` albo limitation.

## Petla Katalogowa

Po kazdym `opctx_*` result sprawdz, czy katalog rozstrzyga pytanie:

- jezeli nazwa, owner, glossary term, handoff albo system sa potwierdzone,
  zwroc `OperationalGroundingSummary`,
- jezeli wynik jest niejednoznaczny, ale zawiera konkretny `system`, entity id,
  glossary term albo relation do doczytania, wykonaj jedno waskie
  doprecyzowanie,
- jezeli dalszy lookup bylby szerokim skanem katalogu, zwroc limitation,
- jezeli luka dotyczy jakosci katalogu, uzyj `record_tool_feedback`, jezeli
  tool jest dostepny.

Nie oznaczaj nazwy jako confirmed, gdy katalog tylko sugeruje dopasowanie.

## Dozwolone Tools

Flow Explorer moze korzystac z:

- `opctx_get_scope`,
- `opctx_list_entities`,
- `opctx_search`,
- `opctx_get_entity`.

Operational context tools nie przyjmuja `gitLabGroup`, `gitLabBranch`,
`environment` ani `correlationId` jako model-facing input. Podaj tylko krotki
`reason`, gdy tool tego wymaga.

Jezeli potrzebujesz wyszukac albo potwierdzic system, zacznij od
`applicationName` albo `systemId` z `flow-explorer/canonical-tool-inputs.md`.
Nie wyszukuj szeroko katalogu tylko po to, zeby potwierdzic system juz wybrany
przez Flow Explorer UI.

## Kiedy Uzyc

Uzyj `opctx_*`, gdy:

- nazwa systemu albo repozytorium wymaga doprecyzowania,
- wynik ma wskazac proces, bounded context albo ownera,
- user pyta o biznesowe znaczenie endpointu,
- potrzebne sa glossary/handoff/integration hints,
- GitLab result pokazuje boundary do innego systemu.

Nie uzywaj `opctx_*`, gdy pytanie dotyczy konkretnej metody, walidacji, query
predicate albo mapowania. To jest praca GitLab tools.

## Zasady Interpretacji

- Traktuj `system` jako kanoniczny byt katalogowy.
- Runtime/deployment/service names sa sygnalami systemu, nie osobnymi
  komponentami referencyjnymi.
- `codeSearchScope` pomaga dobrac repozytoria, ale nie dowodzi, ze endpoint
  uzywa danego pliku.
- `codeSearchScope` moze tez podac `searchMode/pathPrefixes`; to tylko granica
  GitLab searchu dla wiekszego repozytorium, nie inventory klas ani endpointow.
- Owner moze pochodzic tylko z `system` albo `bounded-context`; dla endpointa,
  klasy albo repozytorium idz przez code-search scope do systemu/contextu.
- `bounded-context` ma pierwszenstwo przed `system`, a system jest fallbackiem.
- Jezeli owner albo process jest niejednoznaczny, wpisz to jako ograniczenie
  widocznosci. Nie uzywaj `teams.yml`, procesu, integracji ani handoff rule
  jako bezposredniego zrodla ownera.

## Glossary I Ubiquitous Language

Operational context jest pierwszym miejscem do potwierdzania nazw domenowych:
procesow, bounded context, systemow, integracji, handoffow i terminow ze
slownika. Jezeli kod sugeruje pojecie domenowe, najpierw sprobuj znalezc je w
glossary albo powiazanym bycie katalogowym.

Jezeli glossary nie ma potrzebnego terminu:

- nie wymyslaj go jako potwierdzonego faktu,
- mozesz uzyc roboczej nazwy na podstawie implementacji, endpointu, pol
  requestu, enumow albo eventow,
- oznacz taka nazwe jako inferencje albo pytanie otwarte,
- zglos brak przez `record_tool_feedback`, jezeli feedback tool jest dostepny:
  `issueCategory=missing_operational_context`,
  `improvementArea=operational_context_data`,
  `summaryForOperator` po polsku z opisem brakujacego terminu.

Przyklad CRM-specific, zanonimizowany:

- Nie pisz: "`CustomerAssignmentService` wywoluje `SegmentRepository`".
- Pisz: "System przypisuje sprawe klienta do opiekuna na podstawie segmentu
  klienta. Termin 'segment klienta' wyglada na pojecie domenowe; jezeli nie ma
  go w glossary, oznacz go jako inferowany i zglos luke katalogu."

## Wklad Do Wyniku

Operational context moze wzbogacic:

- `overview.markdown`,
- `FUNCTIONAL_FLOW`,
- `VALIDATIONS`, gdy katalog wyjasnia regule albo warunek biznesowy,
- `PERSISTENCE`, gdy katalog pomaga nazwac bounded context albo dane,
- `INTEGRATIONS`, gdy katalog pomaga nazwac system, handoff albo ownership,
- `globalOpenQuestions` i `section.openQuestions`,
- `globalVisibilityLimits` i `section.visibilityLimits`,
- source refs dla katalogu albo handoffu.

Formuluj to prostym jezykiem dla analityka/testera. Oddzielaj "katalog mowi"
od "kod potwierdza".

### Biznesowe Nazwy Zrodel Danych

Gdy `flow-explorer-map-persistence-section` prosi o nazwanie zrodla wartosci,
operational context moze pomoc przetlumaczyc techniczny sygnal z kodu na
biznesowa nazwe systemu, komponentu zewnetrznego, handoffu albo glossary term.

Zasada dla `OperationalGroundingSummary`:

- `SOURCE` moze byc `GENERATED`, `REQUEST`, `CALCULATED` albo biznesowa nazwa
  systemu/komponentu zewnetrznego,
- Nie wpisuj jako `SOURCE` nazw klas, beanow, klientow technicznych,
  repozytoriow ani endpointow,
- jezeli katalog nie rozstrzyga technicznej nazwy do nazwy biznesowej, zwroc
  visibility limit albo open question,
- nie formatuj sekcji `PERSISTENCE`; przekaz tylko nazwe, source ref i limit do
  skilla sekcyjnego.

## Kiedy Wrocic Do Orkiestratora

Wroc, gdy:

- kontekst katalogowy jest wystarczajacy,
- potrzebne jest dalsze czytanie kodu,
- katalog nie rozstrzyga ownershipu albo procesu,
- dodatkowe `opctx_*` calls nie zmienia odpowiedzi.

## Kontrakt Wyniku

Zwroc `OperationalGroundingSummary`:

```text
system: <kanoniczny system albo Nie ustalono>
process: <proces albo Nie ustalono>
boundedContext: <bounded context albo Nie ustalono>
resolvedOwnership:
  situationType: <inside-bounded-context | inside-system | bounded-context-boundary | system-boundary | system-infrastructure | external-system-boundary | ambiguous | unknown>
  primaryOwners:
    - <system/bounded-context owner albo inferowany ownerLabel>
  partnerOwners:
    - <druga strona styku albo Nie dotyczy>
  handoffReason: <powod handoffu albo Nie ustalono>
glossaryTerms:
  - term: <pojecie>
    status: confirmed | inferred | missing
codeSearchScope:
  - <project/scope hint, jezeli potrzebny>
integrationHints:
  - <system/handoff/boundary hint>
sourceRefs:
  - <tool:opctx_* albo artifact>
visibilityLimits:
  - <konkretny brak katalogu>
```

## Walidacja

Sprawdz:

- kazda nazwa systemu, procesu, contextu albo ownera ma katalogowy source ref,
- operational context nie zostal uzyty jako dowod zachowania kodu,
- glossary term jest oznaczony jako confirmed, inferred albo missing,
- braki katalogu trafiaja do `visibilityLimits` albo feedback toola.

## Fallbacki

Jezeli katalog nie rozstrzyga:

- uzyj nazwy roboczej tylko jako inferencji,
- wpisz `Nie ustalono` dla ownera/procesu zamiast zgadywac,
- zwroc czesciowy `OperationalGroundingSummary`,
- zglos luke przez `record_tool_feedback`, jezeli feedback tool jest dostepny.

## Artefakty Handoffu

Przekaz:

- `OperationalGroundingSummary`,
- source refs dla `flow-explorer-write-report`,
- wskazanie, czy potrzebne jest dalsze code grounding albo handoff poza
  analizowany system.
