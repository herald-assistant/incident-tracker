---
name: incident-operational-context-tools
description: Playbook analizy incydentu dla neutralnych tools katalogu operational context: grounding systemow, procesow, bounded contextow, ownershipu, code scope i handoffu.
---

# Operational Context Tools W Analizie Incydentu

Uzywaj tego skilla, gdy operational context tools sa dostepne w sesji analizy
incydentu.

Operational context to katalog systemow, repozytoriow, code-search scopes,
procesow, integracji, bounded contexts, teamow, glossary terms i handoff rules.

Katalog jest przydatny do ugruntowania nazw, relacji, ownershipu, code scope,
DB targeting hints i handoff guidance. Nie jest samodzielnym dowodem root
cause.

## Kiedy Uzywac

Uzywaj operational context tools, gdy:

- deterministic operational-context evidence jest brakujace, czesciowe albo za
  waskie,
- affected process, bounded context, team, repository albo integration sa
  niejasne,
- GitLab project albo code-search scope nie jest ugruntowany,
- DB application/schema target potrzebuje pomocy z system albo repository
  context,
- finalna odpowiedz potrzebuje konkretnej trasy handoffu,
- uzytkownik pyta w follow-upie o znaczenie biznesowo-operacyjne, ownership,
  proces, glossary terms albo powiazane systemy.

Nie uzywaj tych tooli tylko dlatego, ze sa dostepne.

## Kolejnosc Tooli

Preferuj ta kolejnosc:

1. Najpierw dolaczone artefakty incydentu.
2. `opctx_get_scope` najwyzej raz, gdy nie wiesz, jakie typy katalogu sa
   dostepne.
3. `opctx_list_entities` jako table-of-contents browse, gdy nie znasz jeszcze
   terminu katalogowego.
4. `opctx_search`, gdy masz konkretny sygnal z logow, kodu, tool results albo
   pytania uzytkownika.
5. `opctx_get_entity` przed poleganiem na ownershipie, handoffie, procesie,
   bounded context, relacji albo code-search details.

## Zasady Browse

Uzywaj `opctx_list_entities` wasko:

- browse jednego typu naraz,
- preferuj prosty filter, gdy istnieje jakikolwiek clue,
- nie przegladaj calego katalogu,
- czytaj kolejna strone tylko wtedy, gdy poprzednia sugeruje, ze relevant
  entity moze byc blisko,
- uzywaj szczegolnie dla processes, bounded contexts, integrations i glossary
  terms, gdy model moze nie znac lokalnego slownictwa.

## Zasady Groundingu

Nie wymyslaj i nie podnos kontekstu katalogowego do rangi incident evidence.

Nazywaj `affectedProcess`, `affectedBoundedContext` albo `affectedTeam` tylko
wtedy, gdy catalog entity jest wsparta przez artefakty incydentu albo tool
results. Gdy dopasowanie jest slabe, wpisz `nieustalone` albo opisz
ograniczenie.

`system` jest kanoniczna encja katalogowa. Runtime, deployment, service i
container names sa sygnalami oraz wlasciwosciami systemu, nie osobnymi
kanonicznymi runtime components.

## Targetowanie GitLaba

Uzywaj operational context do zawezania GitLab exploration:

- preferuj `codeSearchScope`, gdy pasuje do semantic target analizy: bounded
  context, process, system albo integration,
- traktuj `codeSearchScope.target.type/id` jako powod, dla ktorego repository
  set nalezy czytac razem,
- przekaz wszystkie istotne `projectName` ze scope do GitLab search/flow tools,
- zaczynaj od repozytoriow `primary-implementation` albo priority `1`,
- przechodz do supporting libraries, generated clients, integration adapters,
  legacy modules albo collaborator repositories tylko wtedy, gdy evidence albo
  `traversal.expandWhen` to uzasadnia,
- uzywaj package prefixes, class hints, endpoint hints i queue/topic hints jako
  focused search terms.

Nie uznawaj kodu za niedostepny po jednym repository lookup, gdy operational
context listuje szerszy code-search scope.

## Targetowanie DB

Operational context moze pomoc wybrac application, deployment, system,
repository albo DB hint dla DB tools.

Nie dowodzi data issue. Dla JPA, repository albo data-access symptoms nadal
najpierw ugruntuj entity/repository/table hints z deterministic GitLab evidence
albo enabled GitLab tools, gdy to mozliwe.

## Handoff

Uzywaj handoff hints i handoff rules jako guidance routingu.

Gdy rekomendujesz handoff, uwzglednij:

- dlaczego ta trasa jest istotna dla incydentu,
- jakie evidence trzeba przekazac,
- co receiving team albo party powinien sprawdzic jako pierwsze.

Jezeli ownership jest niejednoznaczny, napisz to wprost.

## `reason` Dla Operational Context Tooli

Kazdy operational context tool call musi miec opcjonalny argument `reason`.

Pisz `reason` po polsku jako jedno krotkie praktyczne zdanie dla operatora.
Nie umieszczaj hidden reasoning, dlugiej analizy ani chain-of-thought.

Dobre przyklady:

```text
Sprawdzam, czy sygnaly z logow pasuja do znanego procesu lub bounded contextu.
Przegladam katalog integracji, bo evidence nie wskazuje jednoznacznego downstreamu.
Pobieram szczegoly systemu, zeby potwierdzic wlasciciela i zakres szukania kodu.
```

## Antywzorce

Nie:

- przegladaj calego katalogu "dla bezpieczenstwa",
- uzywaj glossary terms jako dowodu awarii,
- nazywaj ownerow, procesow albo bounded contexts bez incident groundingu,
- uzywaj operational context zamiast GitLaba do dowodzenia zachowania kodu,
- uzywaj operational context zamiast DB tools do dowodzenia stanu danych,
- wywoluj `opctx_get_scope` wielokrotnie,
- traktuj deployment/runtime names jako osobne canonical entities.
