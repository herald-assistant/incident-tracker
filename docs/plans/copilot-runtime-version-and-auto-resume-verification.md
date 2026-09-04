# Kompatybilnosc Copilot SDK/CLI i weryfikacja runtime resume

Status: done

Source need: brak osobnego dokumentu

## Potrzeba / dlaczego

Change Verification osiagnal prog runtime context upgrade, ale uzywana wersja
Java SDK nie potrafila wiarygodnie odczytac `contextTier` zwracanego przez
Copilot CLI. `contextTier=null` zostal potraktowany jak potwierdzenie braku
`long_context`, przez co sesja zakonczyla sie przed wiadomoscia kontynuujaca.
Eksport nie zawieral wersji SDK, CLI ani protokolu, co utrudnilo rozpoznanie
niezgodnej pary.

## Proponowane rozwiazanie

Zaktualizowac Java SDK do stabilnej wersji zgodnej ze schematem CLI zawierajacym
`contextTier`, wymagac Copilot CLI co najmniej 1.0.57 i rejestrowac rzeczywiste
wersje po `client.start()`. Dla runtime upgrade w trybie `AUTO` brak tieru w
`model.getCurrent` bedzie stanem niepotwierdzonym: runtime wysle jedna wiadomosc
kontynuujaca i oceni pierwsze `session.usage_info` przez porownanie `tokenLimit`
sprzed i po resume.

## Zakres

- aktualizacja Java SDK i jawny minimalny poziom kompatybilnego Copilot CLI;
- walidacja oraz zapis wersji SDK, CLI i protokolu jako activity/export diagnostics;
- jedna proba `abort -> resume(long_context)` oraz jedna wiadomosc kontynuujaca;
- potwierdzenie upgrade'u przez wzrost `tokenLimit` albo ostrzezenie przy braku wzrostu;
- rzeczywisty, opt-in test SDK/CLI `create -> abort -> resume -> getCurrent`;
- regresja wszystkich konsumentow neutralnego runtime'u.

## Non-goals

- `fail-before-send` dla niepotwierdzonego tieru w `AUTO`;
- uruchamianie Change Verification od razu z `LONG_CONTEXT_REQUIRED`;
- druga proba runtime resume;
- zmiana promptu, skilli, tool policy albo merytorycznego kontraktu raportu.

## Ograniczenia i ryzyka

Java SDK korzysta z zewnetrznego Copilot CLI, dlatego aplikacja moze
zweryfikowac i zapisac jego rzeczywista wersje, ale instalacja binarium pozostaje
obowiazkiem srodowiska uruchomieniowego. Test rzeczywisty wymaga lokalnego CLI i
autoryzacji, wiec jest uruchamiany jawnie; zwykle testy pozostaja hermetyczne.
Format eksportu otrzyma nowe diagnostyczne metadane, bez zmiany result contract.

## Baseline i conformance delta

- Warstwa wlascicielska: `aiplatform.copilot.runtime` dla kompatybilnosci i
  lifecycle; Change Verification jedynie mapuje neutralne activity do eksportu.
- Obecnie SDK ma wersje 1.0.0, CLI nie jest wersjonowany ani zapisywany.
- Obecnie runtime weryfikuje `getCurrent` przed continuation i odrzuca `null`.
- Publiczne API joba pozostaje bez zmian; zmienia sie wersjonowany eksport.
- Context/evidence, prompt, skills, tools, policy, hidden scope i budzet bez zmian.
- Job state i persistence bez zmian poza dodatkowym activity oraz diagnostyka eksportu.
- Konsumenci: wszystkie feature'y korzystajace z `CopilotSdkExecutionGateway`;
  specjalna semantyka dotyczy tylko runtime resume w `AUTO`.
- Rollback: wylaczenie `analysis.ai.copilot.context-tier.enabled`.
- Znany drift: Change Verification pozostaje w `AUTO`.

## Kryteria akceptacji

- uruchomienie odrzuca CLI starszy od jawnie wspieranej wersji i zapisuje
  rzeczywiste wersje SDK/CLI/protokolu;
- `AUTO + contextTier=null` po resume nie blokuje continuation;
- pierwszy wiekszy `tokenLimit` potwierdza upgrade;
- pierwszy niewiekszy `tokenLimit` publikuje ostrzezenie i analiza moze zakonczyc
  sie odpowiedzia po kompaktowaniu;
- runtime nie podejmuje drugiej proby resume;
- rzeczywisty test pokrywa `create -> abort -> resume(long_context) -> getCurrent`;
- testy runtime'u, Change Verification i granic pakietow przechodza.

## Kroki

- [x] Krok 1: Zaktualizowac pare SDK/CLI, dodac runtime version activity i test kompatybilnosci.
- [x] Krok 2: Zmienic semantyke `AUTO` po resume i dodac regresje obu wariantow `tokenLimit`.
- [x] Krok 3: Rozszerzyc eksport Change Verification o wersje runtime'u i test kontraktu.
- [x] Krok 4: Dodac rzeczywisty opt-in test SDK/CLI, zaktualizowac dokumentacje i wykonac pelna weryfikacje.

## Weryfikacja

- testy celowane runtime'u, gatewaya i eksportu: zielone;
- pelny `mvn -q test`: 1366 testow, 0 failures, 0 errors, 1 skipped;
- `PackageDependencyGuardTest`: zielony;
- pelny `mvn -q clean package`: zielony, lacznie z produkcyjnym buildem frontendu;
- test live kompiluje sie i jest celowo pominiety bez
  `COPILOT_SDK_LIVE_TEST=true`; lokalne srodowisko nie ma Copilot CLI w `PATH`.
