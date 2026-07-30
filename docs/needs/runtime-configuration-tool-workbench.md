# Runtime Configuration Tool Workbench

## Problem

Runtime Configuration Verification korzysta z named GitLab, mapowania plikow
`*.var` i `application.y[a]ml.kv`, klasyfikacji wrazliwosci, pseudonimizacji
oraz budowania artefaktow dla AI. Operator i developer widza koncowy wynik
analizy, ale nie maja jednego miejsca, w ktorym moga sprawdzic:

- jakie pliki i metadane zostaly pobrane z repozytorium konfiguracji,
- jak source coverage i parser odwzorowaly dane na kanoniczne sciezki,
- ktore wartosci zostaly pseudonimizowane, a ktore calkowicie ukryte,
- jaki dokladnie prompt i artefakty sa przekazywane do AI,
- gdzie truncation, brak pliku albo niepelny Operational Context ograniczyl
  widocznosc.

Bez takiego widoku diagnoza blednego mapowania albo anonimizacji wymaga
czytania testow, logow i kodu backendu. To utrudnia odbior integracji i
bezpieczna weryfikacje zmian reguł.

## Oczekiwany rezultat

Tool Workbench udostepnia readonly podglad pipeline'u Runtime Configuration:

1. operator wybiera allowlistowane repozytorium, `internal-system`, branche
   oraz tryb `BASIC/DEEP`,
2. widzi source acquisition jako coverage, sciezki, commit/size/status i
   limity, bez raw content,
3. widzi sanitizowane mapowanie dokumentow, wezlow, diffow i findingow,
4. widzi decyzje anonimizacji: sensitivity oraz reprezentacje
   `PSEUDONYMIZED` lub `SUPPRESSED`,
5. widzi dokladny AI-safe prompt i artefakty przygotowane przez ten sam kod,
   ktorego uzywa job,
6. moze skopiowac sanitizowane JSON-y do diagnostyki.

## Wartosc

- szybsze wykrywanie bledow integracji i parsera,
- audytowalna granica miedzy konfiguracja a inputem AI,
- latwiejsze potwierdzenie, ze nowa regula maskowania dziala,
- prostszy odbior named GitLab i konfiguracji system-to-directory,
- mniejsze ryzyko, ze debugowanie samo stworzy kanal wycieku danych.

## Success Criteria

- ten sam selector daje w jobie i Workbench ten sam deterministic context oraz
  AI artifacts,
- zadna odpowiedz Workbench nie zawiera raw configuration, sekretu, hasha,
  GitLab tokenu ani connection credentials,
- `BASIC` nie wykonuje Operational Context/code enrichment,
- `DEEP` pokazuje preflight, uzyty ref, scope i visibility limits,
- duzy/truncated plik jest jawnie widoczny jako ograniczenie,
- 401/403/404/timeout sa prezentowane jako bezpieczny status bez upstream
  body i exception message,
- UI pozwala szybko przejsc miedzy source, mapping, anonymization i AI input.

## Ograniczenia

- Workbench jest readonly i nie uruchamia sesji AI.
- Workbench nie pokazuje raw values nawet uzytkownikowi lokalnemu.
- Workbench nie pozwala podac dowolnego GitLab URL, tokenu, project path ani
  configuration directory; scope pochodzi z allowlisty i Operational Context.
- Podglad nie jest historia runu ani exportem do kontynuacji.

## Non-Goals

- edycja albo migracja konfiguracji,
- reczne obchodzenie klasyfikatora wrazliwosci,
- testowanie dowolnego pliku poza runtime configuration scope,
- duplikowanie calego GitLab Source lub Operational Context Workbench,
- uruchamianie AI i ocenianie jakosci odpowiedzi modelu.
