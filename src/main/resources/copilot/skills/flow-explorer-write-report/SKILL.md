---
name: flow-explorer-write-report
description: Kontrakt wyniku Flow Explorera - AnalysisReport zapisywany przez report tools, Overview plus aktywne sekcje sectionModes, compact/deep, source refs, confidence, visibility limits i fallback JSON.
---

# Flow Explorer Write Report

Uzywaj tego skilla przed zapisaniem raportu. Zrodlem prawdy initial analysis
jest `AnalysisReport` zapisany przez report tools. Finalna odpowiedz tekstowa
moze byc krotkim statusem i nie jest zrodlem prawdy wyniku.

Nie podawaj `reportId` w argumentach tooli. Backend przekazuje scope raportu
przez hidden `ToolContext`.

Fallback JSON zwracaj tylko wtedy, gdy report tools nie sa dostepne albo zapis
raportu sie nie powiedzie.

## Cel

Zapisz finalny raport Flow Explorera przez report tools albo, awaryjnie, zwroc
fallback JSON zgodny z kontraktem.

## Rola

Ten skill nie diagnozuje kodu i nie wybiera tools. Pilnuje finalnego ksztaltu
raportu Flow Explorera, zeby UI moglo pokazac `Overview` oraz tylko te sekcje,
ktore uzytkownik wlaczyl w `sectionModes`. Jezeli handoff z orkiestratora jest
zbyt plytki, a brak wyglada na rozstrzygalny przez wyspecjalizowany skill, nie
zapisuj raportu; zwroc `ReportReadinessFeedback`.

## Wejscia

Przyjmij od orkiestratora:

- `EndpointFlowSummary`,
- `GoalGuidance`,
- opcjonalny `CodeGroundingSummary`,
- opcjonalny `OperationalGroundingSummary`,
- opcjonalny `PersistenceMappingSummary`,
- opcjonalny `IntegrationBoundarySummary`,
- `sectionModes`, `goal`, source refs, visibility limits, open questions,
  gaps i confidence.

## Procedura

1. Wykonaj `Readiness Gate`.
2. Zbuduj `OVERVIEW`.
3. Zbuduj tylko aktywne sekcje z `sectionModes`.
4. Dla aktywnego `PERSISTENCE` uzyj `PersistenceMappingSummary`, jezeli endpoint
   czyta albo zmienia dane.
5. Dla aktywnego `INTEGRATIONS` uzyj `IntegrationBoundarySummary`, jezeli flow
   ma zewnetrzne granice.
6. Zapisz sekcje przez `report_upsert_section`.
7. Zapisz meta przez `report_update_meta`.
8. Potwierdz raport przez `report_get_current`.
9. Jezeli tools nie dzialaja, zwroc fallback JSON.

## Kontrakt Wyniku

Preferowany wynik to `AnalysisReport` zapisany przez report tools. Fallbackiem
jest pojedynczy JSON zgodny z `Fallback JSON Contract`.

## Readiness Gate

Przed pierwszym `report_upsert_section` sprawdz, czy handoff pozwala zapisac
raport bez zgadywania.

Zapisz raport tylko wtedy, gdy:

- `EndpointFlowSummary` wystarcza do `OVERVIEW` i aktywnych sekcji flow,
- `GoalGuidance` jest dostepne albo `goal` nie wymaga dodatkowego akcentu,
- aktywne `PERSISTENCE` ma `PersistenceMappingSummary`, jezeli endpoint czyta
  albo zmienia dane,
- aktywne `INTEGRATIONS` ma `IntegrationBoundarySummary`, jezeli flow ma
  zewnetrzne granice,
- mocne twierdzenia maja source refs albo jawne `visibilityLimits`.

Jezeli brak jest rozstrzygalny przez focused code/opctx/section skill, nie
zapisuj czesciowego raportu i nie uzupelniaj tresci z pamieci. Zwroc do
orkiestratora `ReportReadinessFeedback`:

```text
status: not_ready
missingArtifact: CodeGroundingSummary | OperationalGroundingSummary | PersistenceMappingSummary | IntegrationBoundarySummary | GoalGuidance
neededFor: OVERVIEW | FUNCTIONAL_FLOW | VALIDATIONS | PERSISTENCE | INTEGRATIONS | report_meta
suggestedSkill: flow-explorer-code-grounding | flow-explorer-operational-grounding | flow-explorer-map-persistence-section | flow-explorer-map-integrations-section | goal_skill
minimumNextQuestion: <najmniejsze pytanie evidence, ktore moze zmienic raport>
reason: <dlaczego obecny handoff jest za plytki>
```

Jezeli brak nie jest rozstrzygalny dostepnymi tools albo specjalistycznymi
skillami, zapisz raport z jawnym `visibilityLimits`, `openQuestions` albo
`gaps`.

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

## Persistence Mapping Input

Ten skill nie domyka samodzielnie sekcji `PERSISTENCE`. Jezeli `PERSISTENCE`
ma tryb `COMPACT` albo `DEEP`, najpierw uzyj
`flow-explorer-map-persistence-section` i przekaz do raportu
`PersistenceMappingSummary`.

Sekcja `PERSISTENCE.markdown` w trybie `DEEP` ma wtedy zawierac tabele:

| TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS |
| --- | --- | --- | --- |

Jezeli `PersistenceMappingSummary` jest czesciowe, wpisz jego braki do
`meta.visibilityLimits`, `meta.openQuestions` albo `meta.gaps` tej sekcji.
Nie uzupelniaj brakujacych tabel, kolumn ani zrodel wartosci przez zgadywanie.

## Integration Boundary Input

Ten skill nie domyka samodzielnie sekcji `INTEGRATIONS`. Jezeli aktywna sekcja
`INTEGRATIONS` ma tryb `COMPACT` albo `DEEP`, najpierw uzyj
`flow-explorer-map-integrations-section` i przekaz do raportu
`IntegrationBoundarySummary`.

Dla `COMPACT` sekcja powinna zawierac tabele:

| System/target | Typ | Adres/kanal/path | Moment w flow | Co jest wysylane albo odbierane | Cel | Pewnosc |
| --- | --- | --- | --- | --- | --- | --- |

Dla `DEEP` dodaj osobny blok na kazda zewnetrzna granice z summary: target,
transport/adres, payload, headers/metadane, response/error handling,
konfiguracja, owner/handoff i source refs. Jezeli nie ma widocznej integracji
zewnetrznej, napisz to wprost zamiast zostawiac pusta sekcje.

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

## Walidacja

Przed zakonczeniem sprawdz:

- raport zawiera `OVERVIEW` i tylko aktywne sekcje z `sectionModes`,
- sekcje `OFF` nie zostaly zapisane ani w raporcie, ani w fallback JSON,
- aktywne `PERSISTENCE` opiera sie na `PersistenceMappingSummary`, jezeli endpoint czyta albo zmienia dane,
- aktywne `INTEGRATIONS` opieraja sie na `IntegrationBoundarySummary`, jezeli flow ma zewnetrzne granice,
- source refs wskazuja artefakty, tool results albo konkretne pliki/linie,
- mocne twierdzenia sa faktami z evidence albo sa oznaczone jako inference/visibility limit,
- `report_get_current` potwierdza zapis sekcji i meta.

## Fallbacki

Jezeli report tools nie sa dostepne albo zapis raportu sie nie powiedzie,
zwroc fallback JSON z sekcji `Fallback JSON Contract`.

Jezeli wymagany summary artifact jest niepelny albo niedostepny, nie generuj
brakujacej tresci z pamieci. Uzyj tego, co jest potwierdzone, a braki wpisz do
`visibilityLimits`, `openQuestions` albo `gaps`.

## Artefakty Handoffu

Po poprawnym zapisie wystaw dla runtime/UI:

- `AnalysisReport` z `OVERVIEW` i aktywnymi sekcjami,
- globalne `references`, `visibilityLimits`, `openQuestions`, `gaps`,
  `confidence` i `warnings`,
- fallback JSON tylko jako awaryjny wynik diagnostyczny.

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
