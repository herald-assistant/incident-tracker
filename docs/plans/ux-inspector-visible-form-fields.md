# UX Inspector: pola widoczne przy capture formularza

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Operator wybierajacy zakres `Formularz` potrzebuje w initial prompt pelnego zestawu pol
widocznych na stronie w chwili capture. Obecny `formSnapshot` dotyczy najblizszego
formularza, obejmuje rowniez ukryte kontrolki i ucina liste po 64 polach. To nie
odpowiada obserwacji calej widocznej strony. Blad odczytu pol nie moze zatrzymac
analizy wskazanego elementu.

## Poziom i baseline

Zmiana L3: zmienia publiczny kontrakt capture, opcjonalny transfer danych formularza,
initial prompt i granice bezpieczenstwa runtime observation. UX capture v1 ma
limit 128 KiB, a jego `formSnapshot` ma limit 64 kontrolek, 16 KiB na wartosc
i 64 KiB lacznie. Browser Tools, Angular i backend waliduja go osobno. Backend
osadza caly capture w `ux-inspector/runtime-observation.json` oraz w initial
prompcie. Job, historia, export/import i follow-up zachowuja capture. Store ma
osobny, nieblokujacy transfer porcjami, ale nie jest zrodlem pol formularza.

Baseline testow: `node --test frontend/tests/browser-tools/browser-tools.test.mjs`
(16/16) oraz `mvn -q '-Dtest=UxInspectorCaptureContractTest,UxInspectorPromptAndSkillsTest' test`
(exit 0) dnia 2026-09-28.

## Conformance delta i konsumenci

- Wlasciciel: `frontend/public/browser-tools` zbiera obserwacje, a
  `features.uxinspector` waliduje i przygotowuje prompt; platforma i tools
  pozostaja neutralne.
- Kontrakt: usunac `formSnapshot` z capture, dodac osobny typowany transfer
  widocznych pol z jawnym statusem powodzenia/niepowodzenia. Podniesc wersje
  UX capture; starego ksztaltu formularza nie odczytywac ani nie emitowac.
- Evidence/prompt: dla udanego odczytu dolaczyc cala tablice pol jako
  `UNTRUSTED_RUNTIME_OBSERVATION` z informacja, ze pola byly widoczne na stronie
  w chwili capture. Pusta tablica oznacza udany odczyt bez widocznych pol.
  Niepowodzenie jest jawnym visibility limit, a analiza trwa dalej.
- Tools, policy, hidden scope, report/result i wspolne komponenty: bez zmian.
- Historia/export/import/continuation: nowy capture przechodzi istniejaca
  sciezka zapisu; stary ksztalt capture jest odrzucany bez migracji. Wynik
  pol wchodzi do initial promptu, ktory jest zapisywany w stanie joba i
  eksporcie, tak samo jak inne inline runtime evidence.
- Konsumenci: `protocol.js`, `runtime.js`, loader/bookmarklet version, receiver
  Angulara, model i podglad UX Inspectora, API uploadu i job start, backendowy
  deserializer/normalizer, prompt preparation, historia/import/export oraz ich
  testy i dokumentacja. UI Explorer i store transfer korzystaja ze wspolnego
  shellu, wiec wymagaja regresji bez zmiany swojego kontraktu.
- Znany drift: komunikat walidacji `protocol.js` mowi o kontrakcie v3 przy
  faktycznym capture v1; usunac ten blad przy zmianie wersji.

## Proponowane rozwiazanie

Zaadaptowac dolaczony skrypt do Browser Tools jako odczyt w chwili wyboru
elementu. Zakres to `document`; odczyt obejmuje natywne kontrolki i
`san-select span.san-select__value`, etykiety, wartosci prezentowane, stan
checkbox/radio oraz widoczne komunikaty walidacji. Filtr widocznosci obejmuje
same pola i ich przodkow (`hidden`, `aria-hidden`, `display`, `visibility`),
poniewaz skrypt zalaczony przez operatora stosuje go obecnie tylko do
komunikatow bledow. Odczyt nie mutuje strony, nie uruchamia walidacji i nie
zapisuje wyniku w `window.__formularze` ani `console.table`.

Zachowac istniejace wykluczenia hasel, plikow, tokenow, sesji i innych
sekretow oraz niezalezna redakcje backendu. Wartosc jest niezaufanym evidence.
Nie ograniczac liczby pol.

Transfer wykonac na tej samej zasadzie co obecny store NgRx: najpierw maly
capture i jego ACK, potem osobny JSON z tablica pol w porcjach 32000 znakow,
ACK po kazdej porcji i walidacja `origin`, `source`, nonce, `captureId`, numeru
oraz liczby porcji. Dla `FORM_DIAGNOSTICS` sukcesem jest rowniez `[]`.
Niepowodzenie odczytu, serializacji, limitu albo transferu ustawia stan
`UNAVAILABLE` i nie uniewaznia juz potwierdzonego capture. Store i pola maja
osobne statusy; awaria jednego transferu nie blokuje drugiego. Odbiornik FE
przechowuje kompletny wynik do recznego startu joba. Wtedy uploaduje go do
osobnego endpointu, otrzymuje jednorazowy ref i przekazuje ten ref w start
request. Backend przypina ref do `captureId`, originu i tozsamosci operatora,
powtarza walidacje i redakcje, a nastepnie dolacza wynik do initial promptu.
Blad uploadu/claim/preparation oznacza brak form evidence i nie blokuje joba.

Zachowac dla transferu limit 16 MiB, taki jak dla store, bez limitu liczby pol.
Pojemnosc initial promptu jest odrebnym ograniczeniem: jezeli cala tablica nie
miesci sie w bezpiecznym budzecie modelu, oznaczyc caly wynik jako
`UNAVAILABLE`/visibility limit i kontynuowac bez pol. Nie wolno przekazywac
pozornie kompletnej, ucietej listy.

Alternatywa: zwiekszyc limit 64 pol w dotychczasowym `formSnapshot`. Nie daje
obserwacji calej widocznej strony ani gwarancji kompletnosci, wiec odpada.

## Zakres

- Nowy odczyt i kontrakt pol dla profilu `FORM_DIAGNOSTICS`.
- Nieblokujacy blad odczytu, transferu, uploadu lub przygotowania form evidence.
- Przekazanie udanego wyniku do initial promptu i czytelny podglad w UI.
- Aktualizacja testow i kanonicznej dokumentacji runtime flow.

## Non-goals

- Zmiana trybu `Element`, UI Explorera, store, policy repository tools lub
  merytorycznego kontraktu raportu.
- Odczyt cookies, storage, sieci, ukrytych pol i danych sekretowych.
- Migracja starych capture lub kompatybilnosc starego `formSnapshot`.

## Ograniczenia i ryzyka

- Dowolnie duzy wynik nie zmiesci sie w transferze ani oknie modelu. Wymaganie
  `bez wzgledu na ilosc pol` oznacza brak arbitralnego limitu liczby pol;
  przekroczenie limitu 16 MiB lub okna musi byc nieblokujacym brakiem
  evidence, a nie cichym obcieciem.
- Skrypt dziala w niezaufanej stronie; getter DOM lub `getComputedStyle` moze
  rzucic wyjatek. Sam odczyt formularza musi byc izolowany od capture celu.
- Wartosc pola, etykieta i komunikat walidacji moga zawierac poufne dane lub
  tekst udajacy instrukcje. Wymagaja redakcji i granicy zaufania w prompcie.
- Zmiana wersji capture uniewazni stare bookmarklety i stare eksporty z
  capture v1; runtime URL musi wymusic pobranie nowej wersji skryptow.

## Kryteria akceptacji

- Dla profilu formularza initial prompt zawiera kazde bezpieczne pole widoczne
  na stronie w chwili capture, rowniez spoza najblizszego `form`, bez limitu
  liczby pol; zawiera jednoznaczna adnotacje o chwili i zakresie obserwacji.
- Wynik `[]` jest odrozniany od bledu. Pola ukryte i wrazliwe nie trafiaja do
  promptu. Validation messages sa tylko obserwacja, bez wywolywania walidacji.
- Blad skryptu, transferu, uploadu lub przekroczenie dopuszczalnego rozmiaru
  nie blokuje capture, joba ani odpowiedzi; brak form evidence jest jawny.
- Stary `formSnapshot` i capture v1 nie sa akceptowane przez nowy kontrakt.
- Testy Browser Tools, Angulara, backendu i pakowania statycznych zasobow
  przechodza zgodnie z macierza zmian wspolnych.

## Kroki

- [x] Krok 1: Zaimplementowac odczyt widocznych pol w Browser Tools na bazie
  zalaczonego skryptu, izolacje bledu i bezpieczna redakcje. Dowod: testy
  `[]`, wiele formularzy, ponad 64 pol, walidacja, custom select, pola
  ukryte/wrazliwe i wyjatek odczytu; capture celu nadal dochodzi.
- [x] Krok 2: Usunac `formSnapshot` bez aliasu, podniesc wersje capture i
  runtime oraz wdrozyc osobny transfer porcjami z ACK, statusem i limitem
  16 MiB obok store. Dowod: testy strict ingress, timeoutu, bladnego nonce,
  liczby/kolejnosci porcji, odrzucenia starego ksztaltu i niezaleznosci obu
  transferow.
- [x] Krok 3: Dodac osobny upload, jednorazowy ref i backendowy claim w
  zakresie capture/origin/operatora, a potem przekazac kompletna obserwacje
  do initial promptu z granica zaufania i budzetem kontekstu. Zaktualizowac
  podglad UI. Dowod: testy backendu i UI dla powodzenia, `[]`, ponad 64 pol,
  niepowodzenia uploadu/claim i przekroczenia budzetu; job trwa dalej.
- [x] Krok 4: Zaktualizowac `ux-inspector-runtime-flow.md`, README Browser
  Tools i lokalne instrukcje po zmianie invariantow; przejrzec diff pod
  katem fikcyjnej domeny CRM oraz sprawdzic graf zaleznosci. Dowod:
  `node --test frontend/tests/browser-tools/browser-tools.test.mjs`,
  `npm --prefix frontend test -- --watch=false`,
  `npm --prefix frontend run build`, `mvn -q -Pbackend-dev clean package`
  oraz `PackageDependencyGuardTest`.

## Weryfikacja wykonania

- Browser Tools: 18/18 testow.
- Angular: 646/646 testow; produkcyjny build zakonczony powodzeniem.
- Backend: `mvn -q -Pbackend-dev clean package` zakonczony powodzeniem;
  1736 testow, 0 failures, 0 errors, 1 skipped. JAR zawiera aktualny bundle.
- `PackageDependencyGuardTest` i `FrontendPageTest` przeszly w pelnym przebiegu.
