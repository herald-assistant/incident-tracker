---
name: code-mapping-or-type-conversion-local-date
expectedClassification: code_mapping_or_type_conversion
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-analysis-gitlab-tools
---

# Fixture: Code Mapping Or Type Conversion - Local Date

## Cel

Ten fixture opisuje incydent, w ktorym dane wejscia istnieja, ale kod zle mapuje
typ albo wartosc do modelu aplikacji.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `code_mapping_or_type_conversion`.
3. Potem przejscie do `incident-analysis-gitlab-tools`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-map-001`
- trigger: downstream response mapping
- failure point: DTO to domain mapper
- log: `DateTimeParseException: Text '2026-06-06T10:00:00Z' could not be parsed`
- code hint: `PolicyDtoMapper.toDomain(validFrom)`
- input field: `validFrom`

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `HTTP client -> response DTO -> mapper -> domain object`.
2. Zaladuj `incident-analysis-gitlab-tools`.
3. Przeczytaj mapper/converter i expected type.
4. Porownaj expected format z actual value z logow/evidence.
5. Jesli failure wynika z konwersji typu albo null handlingu, utrzymaj
   `code_mapping_or_type_conversion`.
6. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze proces zatrzymuje sie przy interpretacji odpowiedzi danych.
- Nie zamienia sekcji w opis klas Java.

### `technicalAnalysis`

- Wskazuje mapper, pole, expected/actual format i test regresyjny.
- Proponuje akcje: obsluzyc format albo zmienic kontrakt integracji.

## Antywzorce

- Nie diagnozuj DB issue, jezeli dane zostaly odebrane, ale padlo mapowanie.
- Nie finalizuj bez przeczytania mappera albo kontraktu pola.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
