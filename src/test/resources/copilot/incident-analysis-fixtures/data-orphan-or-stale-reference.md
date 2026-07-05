---
name: data-orphan-or-stale-reference-customer-segment-reference
expectedClassification: data_orphan_or_stale_reference
starterSkill: incident-analysis-orchestrator
specializedSkill: incident-data-diagnostics
---

# Fixture: Data Orphan Or Stale Reference - Customer Segment Reference

## Cel

Ten fixture opisuje incydent, w ktorym lokalny rekord istnieje, ale wskazuje na
nieistniejaca albo nieaktualna referencje.

Fixture testuje kontrakt routingu:

1. Najpierw research flow przez `incident-analysis-orchestrator`.
2. Potem klasyfikacja jako `data_orphan_or_stale_reference`.
3. Potem przejscie do `incident-data-diagnostics`.
4. Na koncu wynik w polach `functionalAnalysis` i `technicalAnalysis`.

## Minimalne Evidence

- `correlationId`: `corr-orphan-001`
- trigger: event `CustomerProfileUpdated`
- failure point: enrich customer segment reference
- log: `ReferenceDataNotFoundException: segmentRef=SEG-77 not found`
- code hint: `CustomerProfile.SEGMENT_REF -> CUSTOMER_SEGMENT.SEGMENT_REF`
- table/key: `CUSTOMER_PROFILE.SEGMENT_REF = SEG-77`

## Oczekiwany Dry Run Orkiestratora

1. Zbadaj flow use case'u przed klasyfikacja:
   `event -> customer profile load -> segment reference lookup -> missing reference`.
2. Ustal, czy blad dotyczy local data, reference data czy innego systemu.
3. Zaladuj `incident-data-diagnostics`.
4. Wykonaj DB test rozrozniajacy:
   - parent/local row exists: `CUSTOMER_PROFILE`
   - referenced row exists/current: `CUSTOMER_SEGMENT`
   - validity/status check referencji
5. Jesli parent istnieje, ale referencja jest missing/stale, utrzymaj
   `data_orphan_or_stale_reference`.
6. Wypelnij `functionalAnalysis` i `technicalAnalysis` bez legacy pol.

## Oczekiwany Wklad Do Wyniku

### `functionalAnalysis`

- Wyjasnia, ze proces zatrzymuje sie na niespojnosci powiazanych danych.
- Wskazuje, kto prawdopodobnie utrzymuje referencje.

### `technicalAnalysis`

- Wskazuje obie strony relacji, klucz referencji i expected owner.
- Proponuje akcje: odtworzyc referencje albo skorygowac wskazanie.

## Antywzorce

- Nie traktuj lokalnego rekordu jako wystarczajacego dowodu poprawnosci danych.
- Nie oglaszaj code bug, dopoki relacja i referencja nie sa sprawdzone.
- Nie wypelniaj starych pol `summary`, `recommendedAction`, `rationale`,
  `affectedFunction` ani `evidenceReferences`.
