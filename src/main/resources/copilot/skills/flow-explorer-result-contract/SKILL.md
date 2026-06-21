---
name: flow-explorer-result-contract
description: Kontrakt odpowiedzi Flow Explorera - JSON-only, Overview plus cztery sekcje, compact/deep, source refs, confidence i visibility limits.
---

# Skill Kontraktu Wyniku Flow Explorera

Uzywaj tego skilla przed finalna odpowiedzia. Wynik musi byc jednym poprawnym
obiektem JSON. Nie zwracaj Markdown poza stringami pol `markdown`.

## Rola

Ten skill nie diagnozuje kodu i nie wybiera tools. Pilnuje finalnego ksztaltu
odpowiedzi Flow Explorera, zeby UI moglo zawsze pokazac ten sam uklad:
`Overview` oraz cztery stale sekcje.

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
      "id": "BUSINESS_FLOW_RULES|VALIDATIONS|PERSISTENCE|INTEGRATIONS",
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
  "confidence": "high|medium|low"
}
```

Nie dodawaj top-level pol spoza kontraktu. Nie przywracaj pol legacy:
`userIntentSummary`, `audienceSummary`, `endpointContract`, `flowSteps`,
`businessRules`, `testScenarios`, `risksAndEdgeCases`, `visibilityLimits`.

## Sekcje I Kolejnosc

`sections` musi miec dokladnie cztery elementy w tej kolejnosci:

1. `BUSINESS_FLOW_RULES` / `Business flow/rules`
2. `VALIDATIONS` / `Validations`
3. `PERSISTENCE` / `Persistence`
4. `INTEGRATIONS` / `Integrations`

Kazda sekcja musi miec `mode` zgodny z `sectionModes` z promptu:

- `deep`, gdy requestowe focus area wskazuje dana sekcje,
- `compact`, gdy dana sekcja nie byla wskazana.

`compact` nie znaczy powierzchownie. Compact ma zawierac najwazniejsze fakty,
decyzje i ograniczenia widocznosci w zwartej formie.

`deep` ma zawierac konkretne reguly, warianty, edge case'y, source refs,
otwarte pytania i limity widocznosci dla danej sekcji.

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
- reguly biznesowej albo decyzyjnej,
- stanu danych przed/po,
- walidacji i odrzucen,
- integracji, eventu, kolejki albo handoffu,
- terminu z operational context/glossary, jezeli jest dostepny.

Nazwy techniczne wolno pokazac w `markdown` tylko wtedy, gdy sa potrzebne
odbiorcy do rozroznienia kontraktu API, pola request/response, statusu, eventu,
kolejki albo systemu. Same klasy/metody nie sa dobra nazwa biznesowa.

Kazdy fakt techniczny ma byc przetlumaczony na to, co endpoint robi, czego
wymaga, co zapisuje, z czym sie komunikuje albo co moze byc niewidoczne.

Jezeli implementacja sugeruje wazne pojecie domenowe, ale operational context
albo glossary go nie potwierdza, mozesz uzyc roboczej nazwy jako inferencji.
Dodaj wtedy limit widocznosci albo pytanie otwarte i nie prezentuj tej nazwy
jako potwierdzonego slownika domeny.

## Source References

Preferuj source refs w formie:

- `flow-explorer/compact-flow-manifest.md`,
- `flow-explorer/snippet-cards.md`,
- `projectName:path:Lx-Ly`,
- `tool:gitlab_read_java_method_slice`,
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
- tworz dlugiego technicznego eseju,
- zaczynaj akapitow od nazw klas, metod albo beanow,
- pomijaj ktoras z czterech sekcji,
- traktuj `focusAreas` jako pozwolenie na pominiecie sekcji compact,
- przenos scenariuszy testowych albo ryzyk do osobnych top-level pol.
