---
name: flow-explorer-map-integrations-section
description: Przygotowanie sekcji INTEGRATIONS Flow Explorera dla trybow COMPACT i DEEP: zewnetrzne systemy, kanaly, targety, transport, payload, error handling i handoff.
---

# Flow Explorer Map Integrations Section

Uzywaj tego skilla, gdy `sectionModes.INTEGRATIONS` ma tryb `COMPACT` albo
`DEEP` i aktywna sekcja ma opisac komunikacje poza analizowany komponent albo
system.

## Cel

Zbuduj `IntegrationBoundarySummary`: konkretna lista zewnetrznych systemow,
kanalow, requestow, eventow albo handoffow widocznych w flow endpointu.

Ten skill nie opisuje architektury wewnetrznej ani technicznego call graphu.
Jego zadaniem jest oddzielic prawdziwa granice integracyjna od klas klientow,
mapperow i wewnetrznych eventow.

## Kiedy Uzyc

Uzyj tego skilla, gdy:

- `sectionModes.INTEGRATIONS` ma tryb `COMPACT` albo `DEEP`,
- endpoint wywoluje downstream/upstream HTTP, publikuje lub konsumuje event,
  uzywa kolejki/topicu/streamu, pliku albo innego handoffu poza komponent,
- goal wymaga scenariuszy testowych albo ryzyk integracyjnych,
- operational context albo kod sugeruje boundary do innego systemu.

Nie uzywaj tego skilla, gdy:

- `INTEGRATIONS=OFF`,
- mechanizm jest tylko wewnetrznym eventem domenowym bez brokera, destination,
  topicu, queue, bindingu albo handoffu,
- pytanie dotyczy walidacji, persistence albo czysto lokalnej logiki.

## Wejscia

Wymagane:

- `EndpointFlowSummary`: momenty flow, w ktorych moze zachodzic komunikacja,
- canonical GitLab inputs z artefaktow Flow Explorera,
- snippet cards, compact flow manifest i OpenAPI endpoint contract, jesli jest
  dostepny,
- aktualne `goal`, `sectionModes` i `reasoningEffort`.

Opcjonalne:

- `CodeGroundingSummary` z klientami, mapperami, bindingami albo config keys,
- `OperationalGroundingSummary` z nazwami systemow, ownerami, handoff rules i
  glossary,
- `PersistenceMappingSummary`, gdy integracja dostarcza wartosci zapisywane
  pozniej do DB.

## Procedura

1. Zacznij od flow endpointu i wypisz tylko miejsca, gdzie sygnal opuszcza albo
   wchodzi spoza analizowanego komponentu/systemu.
2. Dla HTTP sprawdz `@FeignClient`, `RestClient`, `WebClient`,
   `RestTemplate`, method/path mapping, URL template, base URL property,
   request body, headers, response mapping i error handling.
3. Dla eventow i messagingu sprawdz `StreamBridge.send(...)`, binding name,
   destination/topic/queue, `@Bean Consumer<T>`, `Function`, `Supplier`,
   `@RabbitListener`, routing key, exchange, retry, DLQ i content type, jesli
   sa widoczne.
4. Dla konfiguracji czytaj YAML/properties tylko po ugruntowanych nazwach z
   kodu: client name, property placeholder, binding, destination, topic,
   queue albo prefix. Nie skanuj konfiguracji szeroko.
5. Uzyj `flow-explorer-operational-grounding`, gdy trzeba nazwac target
   biznesowo, potwierdzic ownera, bounded context, handoff albo glossary.
6. Uzyj `flow-explorer-code-grounding`, gdy brakuje konkretnego klienta,
   mappera payloadu, bindingu, path, headers albo error handlingu.
7. Nie opisuj mappera jako integracji. Mapper jest evidence dla payloadu albo
   response mappingu.
8. Oznacz target jako `Fakt z evidence`, `Inferencja` albo `Luka widocznosci`.

## Petla Poglebiania

Po wstepnym `IntegrationBoundarySummary` wykonaj gate zgodny z trybem sekcji:

- `COMPACT`: summary ma wymienic wszystkie widoczne zewnetrzne targety, typ,
  adres/kanal/path, moment w flow, cel i pewnosc,
- `DEEP`: summary ma dodac dla kazdej granicy transport/adres, payload,
  headers/auth/metadane, response albo event handling, konfiguracje,
  owner/handoff i source refs, albo jawny visibility limit.

Jezeli summary jest zbyt plytkie dla aktywnego trybu:

- nazwij brak jako konkretne pytanie evidence,
- uzyj `flow-explorer-code-grounding` dla najmniejszego klienta, bindingu,
  mappera payloadu, path, destination, headers albo error handlingu,
- uzyj `flow-explorer-operational-grounding`, gdy brakuje biznesowej nazwy
  systemu, ownera, handoffu albo glossary,
- po targeted retry ponownie sprawdz gate,
- jezeli retry nie rozstrzyga braku, wpisz `visibilityLimits` albo
  `openQuestions` dla konkretnej granicy.

Nie finalizuj wyniku `DEEP` jako `COMPACT` tylko dlatego, ze pierwsze evidence
bylo za plytkie. Nie dopowiadaj payloadu, adresu, retry ani ownera bez evidence.

## Kontrakt Wyniku

Zwroc `IntegrationBoundarySummary`:

```text
boundaries:
  - target: <system/kanal/handoff>
    type: REST downstream | REST upstream | EVENT_PUBLISH | EVENT_CONSUME | QUEUE | STREAM | FILE/HANDOFF | OTHER
    direction: outbound | inbound | bidirectional | unknown
    address: <method path, URL template, destination, topic, queue, binding, property placeholder>
    flowMoment: <krok endpointu>
    payload: <biznesowy opis danych albo sygnalu>
    purpose: <po co endpoint komunikuje sie z targetem>
    confidence: Fakt z evidence | Inferencja | Luka widocznosci
    ownerOrHandoff: <owner/team/rule albo Nie ustalono>
    sourceRefs:
      - <artifact/tool/projectName:path:Lx-Ly>
visibilityLimits:
  - <brak adresu/payloadu/headerow/retry/ownera>
openQuestions:
  - <pytanie do zespolu albo analityka>
```

Dla `INTEGRATIONS=COMPACT` przygotuj tabele:

| System/target | Typ | Adres/kanal/path | Moment w flow | Co jest wysylane albo odbierane | Cel | Pewnosc |
| --- | --- | --- | --- | --- | --- | --- |

Dla `INTEGRATIONS=DEEP` dodaj osobny blok dla kazdej integracji:
transport/adres, moment w flow, request/event/payload, headers/auth/metadane,
response i error handling, konfiguracja, owner/handoff oraz source refs.

## Walidacja

Przed zakonczeniem sprawdz:

- kazda pozycja jest rzeczywista granica zewnetrzna, a nie wewnetrzny bean,
  mapper, port/adapters albo lokalny event bez brokera,
- compact zawiera wszystkie widoczne zewnetrzne targety w flow,
- deep zawiera path/destination, payload albo jawny visibility limit,
- brak integracji jest napisany wprost, a nie ukryty jako pusta sekcja,
- source refs potwierdzaja target, transport albo payload.

## Fallbacki

Jezeli brakuje danych:

- wykonaj najmniejszy focused code read albo opctx lookup,
- jezeli target pozostaje niepewny, nazwij go jako inferencje z kodu/configu,
- jezeli adres, payload, retry albo owner nie jest widoczny, wpisz konkretny
  visibility limit.

Jezeli wynik jest niepewny:

- nie wymyslaj awarii integracji,
- nie przypisuj ownera bez operational context albo evidence,
- nie podnos technicznej nazwy klienta do rangi kanonicznego systemu bez
  oznaczenia inferencji.

## Artefakty Handoffu

Gdy uzywa go orkiestrator albo `flow-explorer-write-report`, wystaw:

- `IntegrationBoundarySummary`,
- source refs dla sekcji `INTEGRATIONS`,
- visibility limits i open questions dotyczace tylko integracji.
