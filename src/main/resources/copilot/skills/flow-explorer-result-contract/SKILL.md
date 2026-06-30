---
name: flow-explorer-result-contract
description: Kontrakt wyniku Flow Explorera - AnalysisReport zapisywany przez report tools, Overview plus aktywne sekcje sectionModes, compact/deep, source refs, confidence, visibility limits i fallback JSON.
---

# Skill Kontraktu Wyniku Flow Explorera

Uzywaj tego skilla przed zapisaniem raportu. Zrodlem prawdy initial analysis
jest `AnalysisReport` zapisany przez report tools. Finalna odpowiedz tekstowa
moze byc krotkim statusem i nie jest zrodlem prawdy wyniku.

Nie podawaj `reportId` w argumentach tooli. Backend przekazuje scope raportu
przez hidden `ToolContext`.

Fallback JSON zwracaj tylko wtedy, gdy report tools nie sa dostepne albo zapis
raportu sie nie powiedzie.

## Rola

Ten skill nie diagnozuje kodu i nie wybiera tools. Pilnuje finalnego ksztaltu
raportu Flow Explorera, zeby UI moglo pokazac `Overview` oraz tylko te sekcje,
ktore uzytkownik wlaczyl w `sectionModes`.

## Wymagany Report Contract

Zapisz raport przez tools w tej kolejnosci:

1. `report_upsert_section` dla `id=OVERVIEW`.
2. `report_upsert_section` dla kazdej aktywnej sekcji z `sectionModes`, w
   kolejnosci `FUNCTIONAL_FLOW`, `VALIDATIONS`, `PERSISTENCE`, `INTEGRATIONS`.
3. `report_update_meta` dla globalnych `references`, `visibilityLimits`,
   `openQuestions`, `gaps`, `confidence` i ewentualnych `warnings`.
4. `report_get_current`, zeby sprawdzic, czy zapisany raport zawiera `OVERVIEW`
   i wszystkie aktywne sekcje.

Kazde `report_upsert_section` musi miec:

- `id`: `OVERVIEW` albo aktywne id sekcji,
- `title`: czytelny tytul sekcji,
- `order`: `0` dla `OVERVIEW`, potem `1..n` dla aktywnych sekcji,
- `markdown`: glowna tresc sekcji,
- `meta.references`: source refs tej sekcji,
- `meta.visibilityLimits`: ograniczenia tylko tej sekcji,
- `meta.openQuestions`: pytania tylko tej sekcji,
- `meta.gaps`: luki tylko tej sekcji,
- `meta.confidence`: `high`, `medium` albo `low`.

`OVERVIEW` jest sekcja raportu, ale w publicznym UI zostanie pokazany jako
osobny blok `overview`. Pozostale aktywne sekcje raportu zostana pokazane jako
`sections`.

## Fallback JSON Contract

Jezeli report tools nie sa dostepne albo zapis raportu sie nie powiedzie,
zwroc dokladnie jeden obiekt JSON zgodny z polami:

```json
{
  "goal": "DEEP_DISCOVERY|TEST_SCENARIOS|RISK_DETECTION",
  "audience": "business_or_system_analyst_tester",
  "overview": {
    "markdown": "string",
    "confidence": "high|medium|low",
    "sourceRefs": ["string"]
  },
  "sections": [
    {
                      "id": "FUNCTIONAL_FLOW|VALIDATIONS|PERSISTENCE|INTEGRATIONS",
                      "title": "string",
                      "mode": "compact|deep",
      "markdown": "string",
      "sourceRefs": ["string"],
      "visibilityLimits": ["string"],
      "openQuestions": ["string"]
    }
  ],
  "globalVisibilityLimits": ["string"],
  "globalOpenQuestions": ["string"],
  "sourceReferences": ["string"],
  "confidence": "high|medium|low",
  "followUpPrompts": ["string"]
}
```

Nie dodawaj top-level pol spoza kontraktu. Informacje dla innych perspektyw
umieszczaj w aktywnych sekcjach z `sectionModes`, a nie w osobnych polach
na poziomie calej odpowiedzi.

## Follow-Up Prompts

MVP raportu nie ma osobnego pola `followUpPrompts`. Kierunki dalszej rozmowy
zapisuj jako `openQuestions` albo `gaps` w meta raportu lub meta sekcji.

W fallback JSON `followUpPrompts` to gotowe, proste prompty do follow-up chatu.
Maja pomoc uzytkownikowi przejsc od wyniku `medium`, limitow widocznosci albo
pytan otwartych do nastepnego konkretnego kroku.

Zwroc 3-5 promptow. Kazdy prompt ma byc samodzielnym pytaniem albo poleceniem,
ktore uzytkownik moze skopiowac bez edycji. Pisz po polsku, nietechnicznie i
od celu eksploracji, np. "Sprawdz, czy..." albo "Doprecyzuj, co sie dzieje,
gdy...".

Prompty powinny:

- wynikac z `visibilityLimits`, `openQuestions`, `globalVisibilityLimits`,
  `globalOpenQuestions` albo z obszarow `DEEP`, ktore zostaly tylko czesciowo
  domkniete,
- byc zrozumiale dla analityka/testera bez znajomosci klas, metod, plikow,
  tooli ani linii kodu,
- wskazywac kierunek dalszej eksploracji, np. wariant procesu, walidacje,
  zapis danych, integracje, terminy domenowe, scenariusze testowe albo ryzyka,
- nie obiecywac, ze AI znajdzie odpowiedz; jezeli widocznosc jest ograniczona,
  popros o "sprawdzenie w dostepnych zrodlach" i o wskazanie limitow.

Nie tworz promptow typu "przeczytaj klase X" ani "uzyj toola Y". Nazwy
techniczne wolno dodac tylko wtedy, gdy sa potrzebne do jednoznacznego
wskazania endpointu, pola API, statusu, eventu, tabeli albo systemu.

## Sekcje I Kolejnosc

Raport musi miec `OVERVIEW` oraz dokladnie tyle sekcji merytorycznych, ile
sekcji aktywnych wynika z `sectionModes`. Sekcje aktywne to tylko tryby
`COMPACT` i `DEEP`. Sekcja z trybem `OFF` ma nie pojawic sie w raporcie ani w
fallback JSON `sections`.

Zachowaj kolejnosc aktywnych sekcji wedlug stalego porzadku:

1. `FUNCTIONAL_FLOW` / `Functional flow`
2. `VALIDATIONS` / `Validations`
3. `PERSISTENCE` / `Persistence`
4. `INTEGRATIONS` / `Integrations`

Kazda aktywna sekcja musi odpowiadac `mode` z `sectionModes` z promptu:

- `deep`, gdy `sectionModes.<SECTION>=DEEP`,
- `compact`, gdy `sectionModes.<SECTION>=COMPACT`.

Nie zapisuj sekcji `OFF`. W fallback JSON nie ustawiaj `mode` na `off`. `OFF`
nie jest slabszym `compact`, tylko decyzja uzytkownika, ze dana sekcja nie jest
oczekiwana w wyniku.

`compact` nie znaczy powierzchownie. Compact ma zawierac najwazniejsze fakty,
decyzje i ograniczenia widocznosci w zwartej formie.

`deep` ma zawierac konkretne reguly, warianty, edge case'y, source refs,
otwarte pytania i limity widocznosci dla danej sekcji.

## Functional Flow Contract

Sekcja `FUNCTIONAL_FLOW` ma tytul `Functional flow`. Jej glownym elementem jest
chronologiczny flow endpointu: co system robi po kolei, jakie decyzje/warunki
sa widoczne w implementacji, jaki jest efekt dla procesu, uzytkownika albo
danych oraz gdzie termin domenowy jest potwierdzony albo tylko inferowany.

`markdown` sekcji `FUNCTIONAL_FLOW` zawsze formatuj jako te same punkty, w tej
kolejnosci:

- **Cel funkcjonalny:** po co endpoint istnieje z perspektywy procesu albo
  uzytkownika.
- **Flow krok po kroku:** uporzadkowany przebieg w kolejnosci wystapienia,
  np. autentykacja/autoryzacja, walidacja inputu, dociagniecie danych,
  kalkulacje, decyzje, wzmianka o persistence, publikacja zdarzen/kolejka,
  request downstream albo odpowiedz. Nie pomijaj etapu tylko dlatego, ze jest
  techniczny, jezeli zmienia zachowanie funkcjonalne.
- **Koordynacja i routing:** jak endpoint wybiera dalsza sciezke na podstawie
  inputu, dociagnietych danych, stalych, konfiguracji, typu obiektu, statusu
  albo kontekstu procesu.
- **Kalkulacje i reguly funkcjonalne:** szczegolowo opisz wyliczenia, reguly,
  transformacje, priorytety, klasyfikacje, statusy wynikowe i zaleznosci
  miedzy danymi. Jezeli to sa reguly biznesowe widoczne w kodzie, nazwij je w
  jezyku domenowym, ale nie obiecuj, ze jest to pelny katalog reguly poza
  analizowanym kodem.
- **Rozgalezienia zalezne od kontekstu:** warianty happy path, alternate path,
  brak danych, inny status, inny typ klienta/sprawy/obiektu, feature flag,
  konfiguracja albo upstream/downstream context. Dla kazdego wariantu wskaz
  warunek i efekt.
- **Handoffy i efekty uboczne:** tylko w zakresie flow: wspomnij, ze endpoint
  zapisuje stan, publikuje event, wysyla request, zleca prace kolejce albo
  zwraca odpowiedz. Szczegoly persistence zostaw sekcji `PERSISTENCE`, a
  szczegoly integracji sekcji `INTEGRATIONS`.
- **Akcent goal:** material zalezy od `goal`: dla `DEEP_DISCOVERY` najwazniejsze
  warianty flow, dla `TEST_SCENARIOS` sciezki do pokrycia testami, dla
  `RISK_DETECTION` ryzyka i pytania wokol functional flow.

Kazdy z tych punktow ma byc czytelny jako lista albo kroki, a nie jako jeden
dlugi ciag tekstu. Jezeli punkt zawiera wiecej niz jeden krok, warunek, regule,
wariant, kalkulacje, handoff albo efekt, rozbij go na osobne elementy, np.
numerowane kroki `1. ...`, `2. ...` albo wypunktowanie `- nazwa: opis`.
Jedno zdanie jest akceptowalne tylko wtedy, gdy punkt naprawde ma jeden prosty
fakt.

Compact ma wypelnic kazdy punkt zwarta lista najwazniejszych faktow. Deep moze
rozwinac punkty dluzszymi listami i opisami, ale nie zmienia ich nazw ani
kolejnosci. Poziom szczegolow ma wynikac ze zlozonosci flow: nie pomijaj
istotnych krokow, regul, kalkulacji, warunkow, rozgalezien ani handoffow tylko
po to, zeby odpowiedz byla krotsza. Dluzsza odpowiedz jest poprawna, jezeli
jest potrzebna do kompletnego i uzytecznego opisu zgodnego z `goal`.

Techniczne typy, statusy, enumy, stale i wartosci graniczne cytuj w
`FUNCTIONAL_FLOW.markdown`, gdy maja znaczenie funkcjonalne: steruja routingiem,
wyborem wariantu, rozgalezieniem, kalkulacja, walidacja, klasyfikacja, statusem
wynikowym albo handoffem. Nie cytuj ich jako czysto technicznego detalu; zawsze
dodaj krotkie wyjasnienie, jaki warunek albo efekt funkcjonalny z nich wynika.

Nie umieszczaj source refs, evidence ani ograniczen widocznosci w glownym
`markdown` tej sekcji. UI pokazuje je osobno jako zwijane elementy z pol
`sourceRefs`, `visibilityLimits` i `openQuestions`.
Jezeli nie masz potwierdzonego terminu domenowego, nazwij go jako inferencje i
dodaj limit widocznosci albo pytanie otwarte.

## Jezyk I Odbiorca

Pisz po polsku, prostym jezykiem dla analityka albo testera. Glowne pola
`markdown` sa dokumentacja funkcjonalno-techniczna endpointu, a nie opisem
kodu.

Kod, klasy, metody, pliki, line ranges i tools sa evidence. Trzymaj je przede
wszystkim w `sourceRefs`, `sourceReferences`, `visibilityLimits` albo w
zwijalnych referencjach UI. Nie uzywaj nazw klas/metod jako glownego sposobu
opisu zachowania endpointu.

W narracji uzywaj:

- celu endpointu,
- czynnosci systemowych,
- warunku funkcjonalnego albo decyzji widocznej w kodzie,
- stanu danych przed/po,
- walidacji i odrzucen,
- integracji, eventu, kolejki albo handoffu,
- terminu z operational context/glossary, jezeli jest dostepny.

Nazwy techniczne wolno pokazac w `markdown` tylko wtedy, gdy sa potrzebne
odbiorcy do rozroznienia kontraktu API, pola request/response, statusu, eventu,
kolejki albo systemu. Same klasy/metody nie sa dobra nazwa biznesowa.

Wyjatkiem, ktory nalezy opisac w wyniku, sa techniczne wartosci majace znaczenie
funkcjonalne: typy, statusy, enumy, stale, progi, limity i wartosci graniczne,
od ktorych zalezy routing, rozgalezienie, walidacja, kalkulacja, klasyfikacja,
persystencja, publikacja zdarzenia albo request downstream.

Kazdy fakt techniczny ma byc przetlumaczony na to, co endpoint robi, czego
wymaga, co zapisuje, z czym sie komunikuje albo co moze byc niewidoczne.

Jezeli implementacja sugeruje wazne pojecie domenowe, ale operational context
albo glossary go nie potwierdza, mozesz uzyc roboczej nazwy jako inferencji.
Dodaj wtedy limit widocznosci albo pytanie otwarte i nie prezentuj tej nazwy
jako potwierdzonego slownika domeny.

## Persistence Deep Contract

Gdy aktywna sekcja `PERSISTENCE` ma `mode=deep` i endpoint zapisuje dane,
`markdown` tej sekcji musi zawierac biznesowa tabele mapowania:

| TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS |
| --- | --- | --- | --- |

Tabela jest exhaustive persistence table closure dla analizowanego endpointu:
ma wypisac wszystkie kolumny kazdej tabeli tworzonej, aktualizowanej, usuwanej
albo zmienianej relacyjnie w ramach tego endpoint flow. Nie wystarczy pokazac
tylko glowna tabele encji ani tylko join table z identyfikatorami. Dla kazdej
zapisywanej relacji, kolekcji albo kompozycji zejdz rekurencyjnie do tabeli
docelowej i wypisz jej kolumny rowniez wtedy, gdy wymaga to dodatkowych
focused reads po formularzu, mapperze, encji, klasie bazowej, embeddable albo
DDL.

Tabela ma obejmowac kolumny widoczne przez mapowanie ORM analizowanego flow, a
nie tylko pola zadeklarowane bezposrednio w lokalnej klasie encji. Uwzglednij
kolumny z:

- klas bazowych i `@MappedSuperclass`, itp,
- `@Embedded`, `@Embeddable` i `@AttributeOverride(s)`,
- relacji `@JoinColumn`/`@JoinColumns`,
- kolekcji `@OneToMany`, `@ManyToMany`, `@ElementCollection` i
  `@JoinTable`, razem z kolumnami tabeli laczacej oraz kolumnami tabeli
  elementu kolekcji,
- metod encji albo obiektow zlozonych uzytych w flow, jezeli metoda odczytuje
  albo wylicza wartosc z pola mapowanego na kolumne.

Nie pomijaj kolumn parenta ani kompozycji tylko dlatego, ze flow odwoluje sie do
nich przez metode, getter albo helper. Jezeli nie udalo sie dojsc do typu
bezposrednio mapowanego na kolumne, wpisz konkretny brak w `visibilityLimits`
sekcji `PERSISTENCE`.

Nie koncz `PERSISTENCE=DEEP` na technicznym limicie depth z context buildera.
`maxDepth reached`, nierozwiazany interface, `More than one implementation`
albo join table bez tabeli docelowej oznacza konkretna luke eksploracyjna.
W takiej sytuacji uzyj waskich GitLab reads/search zgodnie ze skillem
`flow-explorer-gitlab-tools`, az domkniesz kolumny wszystkich aktualizowanych
tabel albo nazwiesz precyzyjny, twardy limit widocznosci. Limit widocznosci
jest akceptowalny dopiero po pokazaniu, ktorej tabeli, kolumny, typu elementu
kolekcji, mappera albo DDL nie udalo sie potwierdzic.

Checklist przed zapisem sekcji `PERSISTENCE`:

- dla kazdej operacji create/update/delete/link/unlink wskaz tabele dotkniete
  przez endpoint,
- dla kazdego pola requestu zapisywanego jako kolekcja `List<XForm>` albo
  `Set<XForm>` znajdz odpowiadajacy typ domenowy/encje `X`, mapper oraz tabele
  `X`,
- dla kazdego `@JoinTable` wypisz kolumny join table oraz kolumny tabeli
  docelowej elementu kolekcji; same `*_ID` nie domykaja mapowania,
- dla kazdej encji potomnej wypisz kolumny parenta, klasy bazowej,
  embeddables, join columns i tabel potomnych bioracych udzial w zapisie,
- dla kazdej kolumny ustaw `SOURCE`; jezeli `SOURCE` nie jest znany, nie
  wpisuj wiersza jako pewnego faktu, tylko dociagnij evidence albo dodaj
  konkretny limit widocznosci.

`SOURCE` jest polem kontrolowanym. Dozwolone wartosci to tylko:

- `GENERATED`,
- `REQUEST`,
- `CALCULATED`,
- biznesowa nazwa systemu albo komponentu zewnetrznego, gdy wartosc pochodzi z
  dedykowanego systemu.

Nie wpisuj w `SOURCE` ani `SOURCE DETAILS` nazw klas, metod, beanow,
frameworkow, repozytoriow ani szczegolow implementacyjnych. Te informacje moga
byc tylko w `sourceRefs` albo `sourceReferences`. Jezeli nie potrafisz
potwierdzic zrodla po dostepnym kodzie i tools, wpisz limit widocznosci albo
pytanie otwarte zamiast technicznego placeholdera.

## Integration Boundary Contract

Sekcja `INTEGRATIONS` ma tytul `Integrations` i opisuje wylacznie komunikacje
poza analizowany komponent albo system w kontekscie wybranego endpointu. Jej
celem nie jest architektura wewnetrzna, porty/adapters, wewnetrzne eventy
domenowe, wywolania miedzy beanami ani techniczny call graph.

Do `INTEGRATIONS` wpisuj tylko:

- request HTTP albo inny request/response do zewnetrznego systemu,
- publikacje eventu, komunikat na brokerze, kolejke, topic, stream albo
  binding, jezeli sygnal opuszcza analizowany komponent/system,
- konsumpcje eventu/komunikatu tylko wtedy, gdy analizowany endpoint jest
  wyzwalany przez taki kanal albo widoczny flow endpointu zalezy od takiego
  handoffu,
- plik, scheduler, zlecenie asynchroniczne albo inny handoff, jezeli jest
  granica do zewnetrznej odpowiedzialnosci,
- operational-context handoff potwierdzony w evidence dla tego konkretnego
  endpointu.

Nie wpisuj do `INTEGRATIONS`:

- wewnetrznych eventow domenowych, listenerow, mediatorow albo callbackow,
  jezeli nie widac zewnetrznego brokera, destination, topic, queue, bindingu
  albo handoffu poza komponent,
- klas klientow, adapterow, mapperow, portow ani beanow jako glownej tresci,
- integracji znanych z operational context, jezeli nie ma evidence, ze biora
  udzial w analizowanym endpoint flow,
- ogolnej architektury komponentu albo komunikacji miedzy modulami.

Jezeli implementacja sugeruje integracje, ale operational context nie zna
systemu, nazwij target przez inferencje z kodu albo konfiguracji: nazwa
`@FeignClient`, `contextId`, property prefix, host placeholder, klasa klienta,
binding, destination, topic, queue, exchange albo routing key. Oznacz taka
nazwe jako `Inferencja` i dodaj limit widocznosci albo pytanie otwarte, jezeli
nazwa moze byc niepelna.

### Compact

`INTEGRATIONS=compact` ma wymienic wszystkie zewnetrzne systemy albo kanaly
widoczne w flow endpointu. Compact nie moze zastapic konkretow zdaniem typu
"endpoint komunikuje sie z integracjami".

Formatuj `markdown` sekcji jako krotka tabele:

| System/target | Typ | Adres/kanal/path | Moment w flow | Co jest wysylane albo odbierane | Cel | Pewnosc |
| --- | --- | --- | --- | --- | --- | --- |

W kolumnach uzywaj:

- `System/target`: nazwa z operational context albo inferowana z kodu/config,
- `Typ`: np. `REST downstream`, `REST upstream`, `EVENT_PUBLISH`,
  `EVENT_CONSUME`, `QUEUE`, `STREAM`, `FILE/HANDOFF`, `OTHER`,
- `Adres/kanal/path`: HTTP method + path, URL template, destination, topic,
  queue, binding albo property placeholder; jezeli brak konkretu, wpisz
  precyzyjny limit widocznosci,
- `Moment w flow`: krok endpointu, w ktorym integracja jest uzywana,
- `Co jest wysylane albo odbierane`: biznesowy opis danych, sygnalu albo
  payloadu bez listy klas implementacyjnych,
- `Cel`: po co endpoint komunikuje sie z tym targetem,
- `Pewnosc`: `Fakt z evidence`, `Inferencja` albo `Luka widocznosci`.

Jezeli nie widac zadnej integracji zewnetrznej w flow endpointu, napisz to
wprost: "Brak widocznej integracji zewnetrznej w analizowanym endpoint flow."
Nie wypelniaj sekcji architektura wewnetrzna.

### Deep

`INTEGRATIONS=deep` zawiera wszystko z compact oraz osobny szczegolowy blok dla
kazdej integracji. Dla kazdego systemu albo kanalu pokaz:

- `System/target i kierunek`: nazwa, direction, owner/handoff z operational
  context, jezeli widoczne,
- `Transport i adres`: HTTP method, path, URL template, base URL property,
  destination, topic, queue, binding, exchange albo routing key,
- `Moment w flow`: warunek albo krok, ktory wyzwala integracje,
- `Request/event/payload`: przykladowy albo zrekonstruowany payload z polami
  biznesowymi; oznacz inferencje i nie wypisuj sekretow,
- `Headers/auth/metadane`: nazwy naglowkow, content type, correlation id,
  auth scheme albo event headers, jezeli widoczne; bez wartosci sekretow,
- `Response i error handling`: statusy, response fields, timeout, retry,
  fallback, DLQ, idempotencja, duplikacja eventu albo brak widocznosci tych
  mechanizmow,
- `Konfiguracja`: property keys, profile, bindingi albo klient konfiguracyjny,
  jezeli sa potrzebne do ustalenia adresu lub zachowania,
- `Source refs`: pliki, metody, artifact albo tool refs potwierdzajace
  kontrakt.

W deep nie wolno poprzestac na stwierdzeniu, ze uzywany jest `FeignClient`,
`RestClient`, `WebClient`, `RestTemplate`, `StreamBridge`, `Consumer` albo
listener. Te nazwy sa wskazowkami evidence, a wynik ma pokazac konkretny
zewnetrzny kontrakt: target, adres/kanal, dane, moment, cel i ograniczenia
widocznosci.

## Source References

Preferuj source refs w formie:

- `flow-explorer/compact-flow-manifest.md`,
- `flow-explorer/snippet-cards.md`,
- `flow-explorer/openapi-endpoint-contract.md`,
- `projectName:path:Lx-Ly`,
- `tool:gitlab_build_java_method_use_case_context`,
- `tool:gitlab_read_java_method_slice`,
- `tool:gitlab_read_openapi_endpoint_slice`,
- `tool:opctx_get_entity`.

Nie wymyslaj plikow, metod ani linii. Jezeli source ref jest niepewny, wpisz
brak do `visibilityLimits` danej sekcji albo `globalVisibilityLimits`.

## Confidence I Visibility Limits

Ustaw:

- `high`, gdy flow jest ugruntowany w deterministic context i snippet/code
  evidence,
- `medium`, gdy glowny flow jest jasny, ale brakuje szczegolow dla czesci
  sekcji,
- `low`, gdy endpoint, repozytorium, flow spine albo kluczowe evidence sa
  niepelne.

`globalVisibilityLimits` opisuje ograniczenia calej analizy. `visibilityLimits`
w sekcji opisuje brak tylko dla tej sekcji.

## Antywzorce

Nie:

- koncz initial analysis bez zapisu raportu przez report tools,
- wpisuj "brak" jako confidence,
- ukrywaj limity widocznosci,
- mieszaj source refs z hipotezami,
- tworz dlugiego technicznego eseju bez struktury; dluzsza odpowiedz jest
  akceptowalna, gdy zlozony flow wymaga wiecej krokow i list,
- zaczynaj akapitow od nazw klas, metod albo beanow,
- zwracaj sekcje oznaczone jako `OFF`,
- traktuj `OFF` jako pusta sekcje albo `compact`,
- traktuj `focusAreas` jako zrodlo prawdy, gdy prompt zawiera `sectionModes`,
- przenos scenariuszy testowych albo ryzyk do osobnych top-level pol.
