# Polityka deterministycznych sygnalow konfiguracji

Status: done

Source need: [runtime-configuration-verification](../needs/runtime-configuration-verification.md)

## Potrzeba / dlaczego

Heurystyki oparte tylko na nazwie sciezki i rownosci wartosci generuja wiele
falszywych ostrzezen dla wspoldzielonej infrastruktury developerskiej. W
reprezentatywnym eksporcie `SUSPICIOUS_UNCHANGED_ENVIRONMENT_VALUE` odpowiada
za 41 warningow, mimo ze wskazane wartosci nie sa roznicami.

## Klasyfikacja i baseline

Poziom: L1 - zmiana feature-owned deterministic result i jego konsumentow.

Pozostaja prawdziwe:

- kompletna projekcja i structural/effective differences sa zachowane,
- parse/coverage/reference failures nadal powoduja `INCOMPLETE`,
- literalne dodanie wartosci sensitive nadal jest twardym `ERROR`,
- AI dostaje differences i references bez potrzeby tworzenia heurystycznego
  findingu,
- kontrakt findingow pozostaje addytywnie kompatybilny ze starymi eksportami.

Konsumenci: deterministic status/result, prompt preparation, agreement,
annotation linking, eksport/import oraz lista findingow w UI.

## Rozwiazanie

Deterministyczne findingi sa ograniczone do problemow technicznych i twardych
polityk. Zwykle zmiany, zmiany typu, zmiany efektywne, sensitive placeholdery,
znaczniki nazw srodowisk oraz niepowiazane globalne roznice pozostaja faktami w
diffie/referencjach i nie tworza findingow. Ich interpretacja nalezy do warstwy
AI w osobnym kroku.

## Non-goals

- integracja AI z rzeczywista infrastruktura,
- zmiana algorytmu structural/effective diff,
- zmiana statusu `REVIEW_REQUIRED` dla runu zawierajacego roznice,
- usuwanie historycznych findingow z zaimportowanych eksportow.

## Kryteria akceptacji

- poprawnie sparsowana, identyczna wartosc srodowiskowa nie tworzy warningu,
- type/effective/sensitive-placeholder differences nie tworza warningow,
- wrong-environment marker i unrelated-global nie tworza findingow,
- literalny sensitive addition nadal tworzy `ERROR`,
- parse, coverage oraz unresolved/cyclic reference nadal tworza findingi,
- wszystkie nowe deterministic findingi maja severity `ERROR`,
- testy backendu i kontraktow konsumentow przechodza.

## Kroki

- [x] Usunac semantyczne heurystyki z deterministic findings.
- [x] Zaktualizowac testy oraz dokumentacje kontraktu.
- [x] Uruchomic testy engine, portability, prompt, annotation i architecture.

## Dowody weryfikacji

- testy parsera i deterministic engine: zaliczone,
- portability, controller, prompt preparation, annotation linking, agreement
  evaluator i architecture guard: zaliczone,
- `git diff --check`: bez bledow.
