# Krytyczne findingi Runtime Configuration Verification

Status: done

Source need: [runtime-configuration-verification](../needs/runtime-configuration-verification.md)

## Potrzeba / dlaczego

Realny wynik dev1 -> dev2 pokazuje blad skladni pliku VAR i jego skutek jako
dwa odrebne problemy, bez lokalizacji plik:linia. Literalnie dodane wartosci
username/password sa jednoczesnie prezentowane jako zwykle ostrzezenia. Taki
wynik utrudnia operatorowi wskazanie jednego blockera i dwoch krytycznych zmian
przed wdrozeniem.

## Klasyfikacja i baseline

Poziom: L1 - zmiana feature-owned deterministic result i UI.

Pozostaja prawdziwe:

- diff operatorski pokazuje dokladne wartosci zgodnie z zatwierdzonym
  kontraktem feature'a,
- deterministic result nie zawiera raw wartosci sensytywnych,
- parser VAR pozostaje ograniczonym parserem obserwowanej skladni,
- blad parsera albo unresolved/cyclic reference utrzymuje status `INCOMPLETE`,
- stare eksporty bez nowych opcjonalnych pol pozostaja importowalne.

Konsumenci: deterministic engine, job API i snapshot import/export, DEEP AI
preparation i annotation linking, modele Angulara oraz lista findingow.

## Proponowane rozwiazanie

- Traktowac przypisania `key: value` i `key = value` jako rownowazna skladnie
  pliku VAR, takze dla list i map.
- Powiazac issue parsera z unresolved reference po docelowej sciezce klucza,
  opublikowac jeden finding root-cause z referencja oraz lokalizacja plik:linia
  i nie publikowac drugiego findingu bedacego tylko skutkiem tego samego bledu.
- Literalne dodanie wartosci sklasyfikowanej jako sensitive publikowac jako
  `HARDCODED_SENSITIVE_VALUE_ADDED` o severity `ERROR`. Placeholdery i zwykle
  zmiany sensytywne zachowuja dotychczasowa klasyfikacje.
- W UI pokazac polski tytul, wyjasnienie, lokalizacje i linki do reference IDs.

## Zakres

Backend parser/model/engine, kompatybilny kontrakt JSON, lista findingow w UI,
testy backend/frontend oraz kanoniczny runtime flow.

## Non-goals

- kalibracja `SUSPICIOUS_UNCHANGED_ENVIRONMENT_VALUE`,
- zmiana zatwierdzonego kontraktu widocznosci dokladnych wartosci operatorowi,
- ogolny parser Terraform/HCL,
- przebudowa calego ekranu wyniku.

## Ograniczenia i ryzyka

Nowe pola findingu sa opcjonalne, aby zachowac import starych snapshotow.
Scalanie zachodzi tylko wtedy, gdy issue parsera niesie nazwe klucza i mozna
jednoznacznie powiazac unresolved reference po docelowej sciezce.

## Kryteria akceptacji

- `draftDocumentParentNodeId: ...` nie daje findingu parsera, a zalezne
  odwolanie zostaje rozwiazane,
- literalnie dodane username/password daja dwa findingi `ERROR`,
- dodany sensitive placeholder nie jest oznaczany jako hardcoded,
- stare konstruktory i importy bez lokalizacji pozostaja kompatybilne,
- testy celowane backendu i frontendu przechodza.

## Kroki

- [x] Krok 1: rozszerzyc parser, finding model i deterministic engine; pokryc
  root cause oraz sensitive additions testami backendu.
- [x] Krok 2: rozszerzyc prezentacje findingow i test Angulara.
- [x] Krok 3: zaktualizowac runtime flow, uruchomic testy celowane, build
  frontendu i architecture guard.
- [x] Krok 4: uznac operatory `:` i `=` za rownowazne oraz zweryfikowac
  ponownie scenariusz referencji.

## Dowody weryfikacji

- testy celowane parsera i deterministic engine: zaliczone,
- rozszerzony zestaw testow backendu wraz z portability, controllerem,
  przygotowaniem promptu, annotation linking i architecture guard: zaliczony,
- testy frontendu: 223/223 zaliczone,
- produkcyjny build frontendu: zaliczony,
- `git diff --check`: bez bledow,
- test regresyjny `:`/`=` oraz rozszerzony zestaw parser/engine/portability/
  controller/prompt/annotation/architecture guard: zaliczone.
