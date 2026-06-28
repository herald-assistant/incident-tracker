---
name: flow-explorer-result-contract
description: Kontrakt odpowiedzi Flow Explorera - JSON-only, Overview plus aktywne sekcje sectionModes, compact/deep, source refs, confidence, visibility limits i gotowe follow-up prompts.
---

# Skill Kontraktu Wyniku Flow Explorera

Uzywaj tego skilla przed finalna odpowiedzia. Wynik musi byc jednym poprawnym
obiektem JSON. Nie zwracaj Markdown poza stringami pol `markdown`.

## Rola

Ten skill nie diagnozuje kodu i nie wybiera tools. Pilnuje finalnego ksztaltu
odpowiedzi Flow Explorera, zeby UI moglo pokazac `Overview` oraz tylko te
sekcje, ktore uzytkownik wlaczyl w `sectionModes`.

## Wymagany JSON Contract

Zwroc dokladnie jeden obiekt JSON zgodny z polami:

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

## Follow-Up Response Contract

W follow-up chacie nie zwracaj pelnego initial result jako top-level odpowiedzi.
Follow-up ma oddzielny, waski JSON contract:

```json
{
  "message": "string",
  "resultUpdate": {
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
}
```

`message` jest wymagane zawsze. `resultUpdate` jest opcjonalny i jest partialem
`FlowExplorerAiResponse`, ktory backend nalozy na aktualny authoritative result.

Zasady:

- zwroc samo `message`, gdy uzytkownik chce wyjasnienia bez zmiany wyniku,
- dodaj `resultUpdate`, gdy uzytkownik prosi o dopisanie, poglebienie,
  poprawienie, przeformulowanie albo aktualizacje wyniku,
- nie zwracaj `resultUpdate` jako pustego obiektu,
- brak pola w `resultUpdate` oznacza brak zmiany,
- brak sekcji w `sections` oznacza, ze ta sekcja zostaje bez zmian,
- sekcja obecna w `sections` musi miec `id`,
- pusta lista oznacza jawne zastapienie wartosci pusta lista,
- nie uzywaj `null`,
- nie zwracaj w `resultUpdate` pol `goal`, `systemId`, `endpointId`,
  `httpMethod`, `endpointPath`, `branch`, `prompt`, `usage`, `status`.

Przy aktualizacji sekcji nadal obowiazuje `sectionModes`. Nie dodawaj sekcji
`OFF`, nie tworz nowych sekcji i nie zmieniaj `mode` na wartosc sprzeczna z
trybem aktualnego wyniku. Jezeli aktualizujesz `markdown`, zachowaj poziom
szczegolow: `compact` ma byc zwarte, `deep` ma byc szczegolowe.

`resultUpdate` powinien zawierac tylko pola, ktore faktycznie proponujesz
zmienic. Nie przepisuj calego wyniku tylko po to, zeby pokazac brak zmiany.

## Follow-Up Prompts

`followUpPrompts` to gotowe, proste prompty do follow-up chatu. Maja pomoc
uzytkownikowi przejsc od wyniku `medium`, limitow widocznosci albo pytan
otwartych do nastepnego konkretnego kroku.

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

`sections` musi miec dokladnie tyle elementow, ile sekcji aktywnych wynika z
`sectionModes`. Sekcje aktywne to tylko tryby `COMPACT` i `DEEP`. Sekcja z
trybem `OFF` ma nie pojawic sie w tablicy `sections`.

Zachowaj kolejnosc aktywnych sekcji wedlug stalego porzadku:

1. `FUNCTIONAL_FLOW` / `Functional flow`
2. `VALIDATIONS` / `Validations`
3. `PERSISTENCE` / `Persistence`
4. `INTEGRATIONS` / `Integrations`

Kazda sekcja musi miec `mode` zgodny z `sectionModes` z promptu:

- `deep`, gdy `sectionModes.<SECTION>=DEEP`,
- `compact`, gdy `sectionModes.<SECTION>=COMPACT`.

Nie zwracaj sekcji `OFF`. Nie ustawiaj `mode` na `off`. `OFF` nie jest
slabszym `compact`, tylko decyzja uzytkownika, ze dana sekcja nie jest
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

`sections[].markdown` dla `FUNCTIONAL_FLOW` zawsze formatuj jako te same
punkty, w tej kolejnosci:

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
`sections[].markdown` dla tej sekcji musi zawierac biznesowa tabele mapowania:

| TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS |
| --- | --- | --- | --- |

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

## Source References

Preferuj source refs w formie:

- `flow-explorer/compact-flow-manifest.md`,
- `flow-explorer/snippet-cards.md`,
- `flow-explorer/openapi-endpoint-contract.md`,
- `projectName:path:Lx-Ly`,
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

- zwracaj Markdown zamiast JSON,
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
