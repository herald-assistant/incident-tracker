---
name: code-validation-or-business-rule-status-transition
expectedClassification: code_validation_or_business_rule
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-analysis-gitlab-tools
---

# Fixture: Code Validation Or Business Rule - Status Transition

## Cel

Ten fixture opisuje incydent, w ktorym flow zostaje zatrzymany przez walidacje
albo regule biznesowa w kodzie.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `code_validation_or_business_rule`.
3. Potem przejscie do `incident-analysis-gitlab-tools`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-rule-001`
- trigger: command `ApprovePayment`
- failure point: status transition validation
- log: `BusinessRuleViolationException: payment cannot move from CANCELLED to APPROVED`
- code hint: `PaymentStatusTransitionValidator.validate(from, to)`
- object: payment `PAY-42`

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `command -> payment load -> transition decision -> validation rejection`.
2. Zaladuj `incident-analysis-gitlab-tools`.
3. Przeczytaj walidator, service decision albo rule table w kodzie.
4. Oddziel poprawne odrzucenie biznesowe od blednej implementacji reguly.
5. Jesli mechanizm awarii to rule/validation path, utrzymaj
   `code_validation_or_business_rule`.
6. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, jaka decyzja biznesowa zatrzymuje proces.
- Pokazuje, czy to moze byc poprawna odmowa czy potencjalny blad reguly.

### `technicalAnalysis`

- Wskazuje walidator/regule, stan wejsciowy, oczekiwany stan i test.

## Antywzorce

- Nie traktuj kazdego `BusinessRuleViolationException` jako bug.
- Nie proponuj obejscia walidacji bez wyjasnienia ryzyka biznesowego.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
