---
name: flow-explorer-result-contract
description: Kontrakt odpowiedzi Flow Explorera - JSON-only, prosty jezyk dla analityka/testera, source refs, confidence i visibility limits.
---

# Skill Kontraktu Wyniku Flow Explorera

Uzywaj tego skilla przed finalna odpowiedzia. Wynik musi byc poprawnym JSON-em,
bez Markdown poza stringami pol.

## Rola

Ten skill nie diagnozuje kodu i nie wybiera tools. Pilnuje tylko finalnego
ksztaltu odpowiedzi Flow Explorera.

## Wymagany JSON Contract

Zwroc jeden obiekt JSON zgodny z polami:

```json
{
  "userIntentSummary": "string",
  "audienceSummary": "string",
  "endpointContract": {
    "method": "string",
    "path": "string",
    "purpose": "string",
    "request": ["string"],
    "response": ["string"],
    "parameters": ["string"]
  },
  "flowSteps": [
    {
      "order": 1,
      "title": "string",
      "plainLanguage": "string",
      "technicalGrounding": "string",
      "sourceRefs": ["string"]
    }
  ],
  "businessRules": ["string"],
  "validations": ["string"],
  "persistence": ["string"],
  "externalIntegrations": ["string"],
  "testScenarios": ["string"],
  "risksAndEdgeCases": ["string"],
  "openQuestions": ["string"],
  "visibilityLimits": ["string"],
  "sourceReferences": ["string"],
  "confidence": "high|medium|low"
}
```

Nie dodawaj pol spoza kontraktu.

## Jezyk I Odbiorca

Pisz po polsku, prostym jezykiem dla analityka albo testera. Techniczne nazwy
endpointow, klas, metod, tools, pol JSON i plikow zostaw w oryginalnym
brzmieniu.

Kazdy krok flow powinien miec:

- krotki tytul,
- wyjasnienie biznesowo-systemowe,
- techniczne ugruntowanie,
- source refs, jezeli sa dostepne.

## Source References

Preferuj source refs w formie:

- `flow-explorer/compact-flow-manifest.md`,
- `flow-explorer/snippet-cards.md`,
- `projectName:path:Lx-Ly`,
- `tool:gitlab_read_repository_file_chunk`,
- `tool:opctx_get_entity`.

Nie wymyslaj plikow, metod ani linii. Jezeli source ref jest niepewny, wpisz
brak do `visibilityLimits` zamiast tworzyc falszywa referencje.

## Confidence I Visibility Limits

Ustaw:

- `high`, gdy flow jest ugruntowany w deterministic context i snippet/code
  evidence,
- `medium`, gdy glowny flow jest jasny, ale brakuje szczegolow dla czesci
  focus areas,
- `low`, gdy endpoint, repozytorium, flow spine albo kluczowe evidence sa
  niepelne.

`visibilityLimits` musza jasno powiedziec, czego nie bylo widac: kodu,
konfiguracji, runtime data, operational context, ownera albo downstream
systemu.

## Antywzorce

Nie:

- zwracaj Markdown zamiast JSON,
- wpisuj "brak" jako confidence,
- ukrywaj limity widocznosci,
- mieszaj source refs z hipotezami,
- tworz dlugiego technicznego eseju w polach dla analityka,
- ignoruj `documentationPreset` i `focusAreas`.
