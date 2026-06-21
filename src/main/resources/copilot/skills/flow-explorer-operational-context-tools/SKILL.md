---
name: flow-explorer-operational-context-tools
description: Playbook uzycia opctx tools w Flow Explorerze - system, proces, bounded context, ownership, glossary i handoff bez traktowania katalogu jako dowodu zachowania kodu.
---

# Skill Operational Context Tools Dla Flow Explorera

Uzywaj tego skilla, gdy Flow Explorer potrzebuje katalogowego kontekstu systemu,
procesu, ownershipu albo handoffu.

## Rola Wobec Orkiestratora

Operational context pomaga nazwac:

- kanoniczny system,
- proces albo bounded context,
- owner team,
- glossary pojec biznesowych,
- code-search scope,
- integracje i handoff route.

Operational context nie jest dowodem, ze kod faktycznie wykonuje dana logike.
Zachowanie endpointu potwierdzaj artefaktami albo GitLab tools.

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
- Jezeli owner albo process jest niejednoznaczny, wpisz to jako ograniczenie
  widocznosci.

## Wklad Do Wyniku

Operational context moze wzbogacic:

- `overview.markdown`,
- `BUSINESS_FLOW_RULES`,
- `VALIDATIONS`, gdy katalog wyjasnia regule albo warunek biznesowy,
- `PERSISTENCE`, gdy katalog pomaga nazwac bounded context albo dane,
- `INTEGRATIONS`, gdy katalog pomaga nazwac system, handoff albo ownership,
- `globalOpenQuestions` i `section.openQuestions`,
- `globalVisibilityLimits` i `section.visibilityLimits`,
- source refs dla katalogu albo handoffu.

Formuluj to prostym jezykiem dla analityka/testera. Oddzielaj "katalog mowi"
od "kod potwierdza".

## Kiedy Wrocic Do Orkiestratora

Wroc, gdy:

- kontekst katalogowy jest wystarczajacy,
- potrzebne jest dalsze czytanie kodu,
- katalog nie rozstrzyga ownershipu albo procesu,
- dodatkowe `opctx_*` calls nie zmienia odpowiedzi.
