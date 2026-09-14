# Operational Context bez obslugi dawnych kontraktow

Status: done

Source need: [Pomoc AI przy tworzeniu i aktualizacji Operational Context](../needs/operational-context-ai-assisted-maintenance.md)

## Potrzeba / dlaczego

Utrzymywanie kilku ksztaltow tych samych danych utrudnia review katalogu i
wyniku asysty. Operator chce jeden aktualny kontrakt, bez odtwarzania dawnych
pol ani dorozumianej normalizacji danych historycznych.

## Baseline i conformance delta (L3)

- Baseline: draft asysty ma `questions` na dwoch poziomach; historia zapisuje
  envelope v1. Backend i frontend rozpoznaja dawne ksztalty pol katalogu,
  zachowuja nieedytowalne historyczne pola i przeksztalcaja stare aliasy.
- Delta: draft ma tylko propozycje i ograniczenia widocznosci, envelope v2 jest
  jedynym odtwarzalnym formatem asysty, a katalog przyjmuje tylko kanoniczne
  ksztalty. Repozytorium wymaga jawnego `git.provider: gitlab` i
  `git.projectPath`. Nie ma cichego tlumaczenia starych wartosci na nowe.
- Wlasciciele: feature posiada draft, prompt i historie; integracja posiada
  walidacje katalogu; UI posiada typy i edytor; maintenance posiada reguly.
- Konsumenci: job/preview/restore asysty, historia analiz, katalogowe API,
  odczyt dla `opctx_*`, runtime analiz, formularz Operational Context i testy.
- Bez zmian: identyfikatory encji, dziewiec aktywnych dokumentow, atomowy zapis,
  scope GitLaba, inne feature'y i slownikowe wartosci `legacy`/`deprecated`.

## Proponowane rozwiazanie

Usunac pola i galezie zgodnosci z draftu, promptu, modelu UI i eksportu.
Walidator ma odrzucac stare ksztalty zamiast je normalizowac. Projekcja i
edytor maja czytac wylacznie kanoniczne pola. Reguly maintenance opisza jeden
obowiazujacy ksztalt. Alternatywa migracji w locie utrwalilaby drugi kontrakt.

## Zakres

Operational Context Assistance, katalogowe pola YAML, projekcja odczytu,
formularz, testy i kanoniczna dokumentacja tych kontraktow.

## Non-goals

Kasowanie historycznych plikow analiz lub katalogu uzytkownika oraz zmiana
wspolnego formatu historii innych feature'ow.

## Ograniczenia i ryzyka

Stare envelope nie beda odtwarzane. Stare ksztalty YAML nie beda akceptowane;
operator musi je jawnie poprawic. Skan aktualnych katalogow pakietowego i
lokalnego nie wykazal dawnych kluczy `match`, `targetProcessId`,
`targetContextId`, `sources` ani pol preserve-only.

## Kryteria akceptacji

- Draft bez `questions` przechodzi, z `questions` jest odrzucany.
- Historia v2 daje wznowienie review; v1 nie daje wznowienia ani cichej migracji.
- Stare ksztalty pol katalogu sa odrzucane, nie przeksztalcane; nieznane pola
  istniejącej encji sa usuwane przy jej aktualizacji.
- Prompt, UI i maintenance opisuja jeden aktualny kontrakt.
- Testy backendu i frontendu oraz build przechodza.

## Kroki

- [x] Usunac dawne pola draftu i podniesc wersje eksportu; zweryfikowac parser,
  job, historie i frontend.
- [x] Usunac stare ksztalty katalogu z walidacji, projekcji i edytora;
  zweryfikowac testy integracji i formularza.
- [x] Ujednolicic reguly maintenance i dokumentacje; przeskanowac pozostale
  aktywne galezie zgodnosci.
- [x] Uruchomic testy Angulara, build Angulara, pakiet backend-dev, audit diffu
  i zaleznosci.

Weryfikacja: 587/587 testow Angulara, build Angulara oraz
`mvn -q -Pbackend-dev clean package` zakonczone powodzeniem. Skan aktywnych
katalogow i instrukcji maintenance nie wykazal dawnych kluczy. Efektywna
lokalna kopia skilla zostala zsynchronizowana z wersja pakietowa.
