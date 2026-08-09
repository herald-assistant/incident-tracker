# Change Verification - inferred critical checks

Status: done

Source need: [Change Verification](../needs/change-verification.md)

## Potrzeba / dlaczego

Story i material Confluence bywaja niepelne, mimo ze podczas refinementu zespol
ustalil dodatkowe zachowania istotne dla bezpiecznego release'u. Obecny wynik
Change Verification dopuszcza wymagania `inferred`, ale miesza je z wymaganiami
zrodlowymi w `STORY_COMPLIANCE`. Operator nie widzi wiec dostatecznie wyraznie,
co bylo zapisanym zobowiazaniem, a co jest krytyczna sugestia AI do manualnego
potwierdzenia.

Zmiana ma poprawic jakosc manualnego review bez wchodzenia w generowanie albo
wykonywanie testow. AI ma zaproponowac maksymalnie piec release-critical checks,
od razu ocenic je wzgledem tego samego evidence i pokazac osobno od zgodnosci ze
story oraz instrukcjami.

## Proponowane rozwiazanie

Rozszerzyc feature-owned kontrakt pojedynczego checka o jawne pochodzenie
`DEFINED` albo `INFERRED_CRITICAL` oraz pola wyjasniajace krytycznosc,
przeslanki inferencji, ryzyko pominiecia i pewnosc. `verificationChecks`
pozostanie jedna lista dla prostego mapowania i spojnosci runtime, ale
backend, report i frontend beda rozdzielac jej elementy po `origin`.

Nowy run doda osobna sekcje raportu `INFERRED_CRITICAL_CHECKS`, aktywna razem
ze Story Compliance. AI wygeneruje od zera do pieciu takich pozycji w tym samym
runie i na podstawie juz zebranego evidence. Backend wymusi limit pieciu,
niezaleznie od promptu. Statusy tych pozycji nie beda zmieniac statusu
`Story Compliance` ani `Instruction Compliance`; beda sygnalem ryzyka i
materialem do manualnej decyzji.

Eksport i import przejda na jeden aktualny kontrakt: wersje 4 /
`change-verification-result-v4`. Wersja 3 nie bedzie importowana ani migrowana,
a brak nowych wymaganych pol bedzie bledem kontraktu zamiast sygnalem do
uzupelnienia wartosci domyslnych. To jest produkt V1, dlatego zmiana nie
utrzymuje kompatybilnosci wstecznej.

W ramach tej zmiany zostanie wykonany audyt calego Change Verification i
usuniete zostana pozostale feature-specific sciezki istniejace wylacznie dla
kompatybilnosci ze starszym requestem, resultem, reportem, eksportem, importem
albo lokalnym snapshotem. Nie dotyczy to mechanizmow odpornosci aktualnego
kontraktu, takich jak bezpieczny wynik `INCONCLUSIVE` po awarii AI, walidacja
niezaufanego JSON, report fallback po braku odpowiedzi toola ani discovery MR
po Jira key. Te zachowania realizuja aktualny produkt i safety, a nie
kompatybilnosc wsteczna.

Alternatywa polegajaca tylko na filtrowaniu obecnego
`interpretationType=inferred` zostaje odrzucona, bo nie daje strukturalnie
powodu krytycznosci, ryzyka ani pewnosci i nie rozroznia zwyklej inferencji od
kontroli release-critical.

## Baseline

- Feature: `Change Verification`, compliance-only MVP.
- Wartosc dla operatora: porownanie Jira/Confluence i instrukcji repozytorium z
  implementacja widoczna w MR-ach oraz kodzie dociaganym tools.
- Publiczne API: `POST /api/change-verification/jobs` i
  `GET /api/change-verification/jobs/{jobId}` zwracaja snapshot joba.
- Wynik: `result.compliance` ma status, jedna liste `verificationChecks`,
  findings, suggested actions i visibility limits.
- Check ma scope `STORY_COMPLIANCE` albo `INSTRUCTION_COMPLIANCE` oraz
  `interpretationType`, ktory juz dopuszcza `inferred`.
- Prompt i runtime skills nakazuja przejsc poza jawne AC, ale inferowane wpisy
  pozostaja w Story Compliance.
- Kanoniczny `AnalysisReport` ma dwie opcjonalne sekcje:
  `STORY_COMPLIANCE` i `INSTRUCTION_COMPLIANCE`.
- FE buduje zakladki z sekcji reportu i filtruje `verificationChecks` po
  `scope`; jeden komponent pokazuje podsumowanie, wymagajace uwagi, potwierdzone
  wpisy i szczegoly.
- Local history zapisuje snapshot bez osobnej migracji backendowej. Import i
  eksport uzywa schema version 3 oraz `change-verification-result-v3`.
- Tools, allowlista, hidden scope, budzet, source discovery, job lifecycle i
  polling nie wymagaja zmiany.
- Baseline testow 2026-08-09: backend
  `*ChangeVerification*` + `PackageDependencyGuardTest` przechodzi; frontend
  37 plikow / 228 testow przechodzi.
- Zastane zmiany uzytkownika w `docs/README.md` oraz dokumentach Domain Skill
  Generation sa poza zakresem i pozostaja nietkniete.

## Conformance delta

- Cel zmiany: osobno pokazac do pieciu krytycznych kontroli zaproponowanych
  przez AI i od razu ocenionych wzgledem implementacji.
- Ownership: wylacznie `features.changeverification`, jego runtime skills,
  feature UI oraz jego import/export.
- Publiczne API/DTO: addytywne pola checka; brak nowych endpointow i pol
  requestu.
- Context/evidence: bez zmian; ten sam material Jira, Confluence, MR,
  instruction context i tool evidence.
- Prompt/artifacts/skills: jawna klasyfikacja `DEFINED` vs
  `INFERRED_CRITICAL`, limit i kryteria jakosci; brak dodatkowego runu AI.
- Tools/policy/hidden scope/budzet: bez zmian.
- Report/result: trzecia sekcja `INFERRED_CRITICAL_CHECKS`; statusy inferred
  nie wplywaja na compliance z materialem zrodlowym.
- Job state/persistence: snapshot przyjmuje addytywne pola i dodatkowa sekcje;
  lifecycle bez zmian.
- Export/import: tylko kontrakt v4; import v3 i migracje starszych pol zostaja
  usuniete albo sa jawnie odrzucane.
- FE/UX: osobna zakladka/sekcja z jednoznacznym komunikatem, ze nie sa to
  wymagania kontraktowe; reuse obecnego komponentu wyniku z wariantem dla
  inferred checks zamiast kopii calego widoku.
- Zaleznosci: bez nowych kierunkow zaleznosci i bez importu sibling feature'ow.
- Konsumenci: AI response parser, provider/job mapping, report factory i mapper,
  controller snapshot, local run persistence, backend export diagnostics,
  frontend models, page section mapping, compliance component, import/export,
  fixtures i testy.
- Compatibility cleanup: audyt feature-owned API/DTO, response parsera,
  reportu, local snapshotu i FE import/export; usuniecie aliasow, migratorow,
  tolerowania brakujacych starych pol i galezi obslugujacych wylacznie
  nieaktualny kontrakt. Shared mechanizmy i inne feature'y sa poza tym
  cleanupem.
- Dokumentacja: business need, system overview i lokalne instrukcje feature'a,
  jesli finalny diff ustanowi nowy invariant.
- Znany drift: obecne mieszanie `inferred` ze Story Compliance zostanie
  usuniete na bezposrednio zmienianej granicy; pozostale drifty pozostaja
  nietkniete.

## Zakres

- od zera do pieciu release-critical inferred checks,
- ocena kazdej pozycji na podstawie obecnego evidence,
- osobna prezentacja w report i FE,
- jawne pochodzenie, rationale, ryzyko, pewnosc i source signals,
- ochrona statusu source-defined compliance przed wplywem inferred checks,
- jeden aktualny kontrakt eksportu/importu v4,
- usuniecie feature-specific kompatybilnosci wstecznej Change Verification.

## Non-goals

- generowanie lub wykonywanie smoke testow,
- dodatkowy run AI albo dodatkowe source discovery,
- automatyczna zmiana story, Confluence, instrukcji lub kodu,
- traktowanie sugestii AI jako naruszenia instrukcji,
- organizacyjny/globalny compliance engine,
- zmiana tools, ich budzetu, hidden context albo job lifecycle.
- usuwanie shared mechanizmow wersjonowania albo kompatybilnosci uzywanych
  przez inne feature'y.

## Ograniczenia i ryzyka

- AI moze proponowac ogolne best practices. Prompt i skille musza wymagac
  konkretnego sygnalu w materialach/kodzie, znaczenia dla release'u i ryzyka
  pominiecia.
- Limit musi byc egzekwowany rowniez po parsowaniu odpowiedzi, nie tylko w
  instrukcji dla modelu.
- Stare eksporty i snapshoty nie maja nowych metadanych i przestana byc
  importowalne. Jest to swiadoma zmiana V1; uzytkownik musi uruchomic nowa
  weryfikacje.
- Cleanup kompatybilnosci nie moze usunac safety fallbackow ani biezacego
  discovery tylko dlatego, ze w kodzie wystepuje slowo `fallback` albo
  normalizacja danych.
- Dodatkowa sekcja reportu zwiekszy rozmiar odpowiedzi, ale nie uruchomi
  dodatkowej sesji ani tools. Limit pieciu ogranicza tokeny wyjsciowe.
- UI musi jednoznacznie komunikowac, ze brak/porażka inferred checka wymaga
  decyzji czlowieka i nie dowodzi niezgodnosci ze story.

## Kryteria akceptacji

- Wymagania z Jira/Confluence i instrukcje sa nadal prezentowane oraz oceniane
  jako source-defined compliance.
- AI moze zwrocic maksymalnie piec osobnych `INFERRED_CRITICAL` checks; nadmiar
  jest deterministycznie odrzucany przez backend.
- Kazdy inferred check ma wyjasnione: dlaczego jest krytyczny, z jakich
  sygnalow wynika, status weryfikacji, evidence, ryzyko pominiecia,
  rekomendacje i pewnosc.
- Inferred checks nie zmieniaja werdyktu Story Compliance ani Instruction
  Compliance.
- FE pokazuje je w osobnej, jednoznacznie opisanej sekcji i obsluguje pusty
  wynik.
- Import przyjmuje tylko kompletny kontrakt wersji 4; wersja 3 i payload bez
  nowych wymaganych pol sa odrzucane czytelnym bledem.
- W Change Verification nie pozostaje feature-specific kod, test ani
  dokumentacja utrzymujaca starszy request/result/report/export/import lub
  mapujaca go na aktualny kontrakt.
- Testy backendu, frontendu, build produkcyjny i architecture guard przechodza.
- Dokumentacja opisuje wynikowy kontrakt i zachowanie.

## Kroki

- [x] Krok 1: rozszerzyc kontrakt checka, parser/normalizacje, prompt, runtime
  skills, report factory/mapper i status semantics; dodac limit maksymalnie 5
  oraz usunac backendowe sciezki kompatybilnosci poprzedniego kontraktu.
  Dodac testy parsera, promptu, skilli, reportu, joba i API. Wynik: backend
  zwraca i raportuje osobne inferred critical checks bez dodatkowego runu AI i
  nie mapuje starszego payloadu na nowy.
  Weryfikacja: celowane testy `*ChangeVerification*` i
  `PackageDependencyGuardTest`.
- [x] Krok 2: rozszerzyc modele FE, mapowanie sekcji, wariant prezentacji oraz
  import/export tylko dla v4; usunac feature-specific migracje, aliasy i
  tolerowanie niekompletnego starego payloadu. Wynik: operator widzi osobno
  wymagania zrodlowe i maksymalnie piec sugestii AI z wyraznym disclaimerem, a
  starszy eksport dostaje czytelny blad nieobslugiwanej wersji. Weryfikacja:
  test komponentu, strony, odrzucenia v3 i round-trip v4 oraz pelny
  `npm test -- --watch=false`.
- [x] Krok 3: zaktualizowac business need i kanoniczny opis runtime, wykonac
  finalny audyt `rg` dla feature-specific compatibility code, architecture diff
  i pelna weryfikacje. Wynik: kod, kontrakt i dokumentacja opisuja tylko
  aktualny kontrakt, plan ma dowody wykonania i status `done`. Weryfikacja:
  `mvn -q clean test`, `npm --prefix frontend test -- --watch=false`,
  `npm --prefix frontend run build`, `mvn -q -DskipTests package`,
  `git diff --check`.

## Dowody wykonania

- Celowane testy backendu: `*ChangeVerification*`,
  `CopilotRuntimeSkillFrontmatterTest` i `PackageDependencyGuardTest` przeszly.
- Pelna weryfikacja backendu: `mvn -q clean test` przeszla.
- Pelna weryfikacja frontendu: 37 plikow testowych i 230 testow przeszlo.
- Produkcyjny bundle Angulara zostal zbudowany przez `npm run build`.
- Pakiet aplikacji zostal zbudowany przez `mvn -q -DskipTests package`.
- Audyt feature-specific compatibility potwierdzil brak migracji, aliasow i
  obslugi result/export contract v1-v3; v3 wystepuje tylko w tescie jawnego
  odrzucenia nieobslugiwanej wersji.
- `git diff --check` nie wykazal bledow whitespace ani markerow konfliktu.
