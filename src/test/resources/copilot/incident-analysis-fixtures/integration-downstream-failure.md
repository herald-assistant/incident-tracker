---
name: integration-downstream-failure-catalog-timeout
expectedClassification: integration_downstream_failure
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-analysis-gitlab-tools
---

# Fixture: Integration Downstream Failure - Catalog Timeout

## Cel

Ten fixture opisuje incydent, w ktorym lokalny system dochodzi do granicy
integracji, a blad pochodzi z downstream albo jego odpowiedzi.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `integration_downstream_failure`.
3. Potem przejscie do `incident-analysis-gitlab-tools`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-int-001`
- trigger: order calculation
- failure point: catalog service HTTP call
- log: `HttpServerErrorException 503 from GET /catalog/products/P-9`
- code hint: `CatalogClient.getProduct(productCode)`
- downstream: `catalog-service`

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `order request -> product lookup -> catalog client -> downstream 503`.
2. Zaladuj `incident-analysis-gitlab-tools`.
3. Przeczytaj klienta integracji, endpoint, timeout/retry i error handling.
4. Uzyj log/HTTP comparison tylko, gdy moze rozroznic local vs downstream.
5. Jesli lokalny kod poprawnie wywoluje downstream, a evidence wskazuje 5xx,
   utrzymaj `integration_downstream_failure`.
6. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze proces zalezy od katalogu produktow.
- Wskazuje downstream i skutek funkcjonalny braku odpowiedzi.

### `technicalAnalysis`

- Wskazuje endpoint, status, klienta, retry/error handling i handoff package.

## Antywzorce

- Nie wymuszaj local code root cause, gdy evidence wskazuje downstream.
- Nie zgaduj odpowiedzialnego zespolu bez operational context.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
