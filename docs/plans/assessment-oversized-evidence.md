# Podzial nadmiernego evidence w obu assessmentach

Status: done

Zatwierdzenie: uzytkownik zatwierdzil caly plan poleceniem `wykonaj` 2026-10-01.

Source need: [Ocena Delivery Unit przekraczajacej okno kontekstu](../needs/assessment-oversized-evidence.md)

## Potrzeba / dlaczego

Obecny jeden request na Delivery Unit moze przekroczyc limit modelu bez
`long_context`. Potrzebny jest pomiar, podzial evidence i przejrzysty wynik
przy zachowaniu pelnego kontekstu Jira/Confluence w kazdej analizie czesci.

Poziom zmiany: L3. Zmiana obejmuje wielosesyjne wykonanie jednej jednostki,
platformowy kontrakt budzetu oraz zapis i prezentacje wielu wywolan AI.
Wszystkie kroki planu sa zatwierdzone.

## Baseline

Stan sprawdzony 2026-10-01:

| Obszar | Obecne zachowanie |
| --- | --- |
| Start | Wlasne job API obu assessmentow: projekt Jira, zakres dat, model i effort; QUEUED zapisany przed async. |
| Jednostka | Spojny graf zakwalifikowanych issue-MR, deduplikacja tozsamosci MR; hierarchia Jira jest kontekstem, nie zmienia grafu. |
| Evidence | Pelny zwrocony material Jira/Confluence oraz MR/diffy; semantyczny zakres zadania; brak nazw typow Jira jako metadanych AI. |
| Prompt | Effective skill inline, kontrakt JSON i cztery artefakty; brak przycinania evidence. |
| Runtime | Jedna nowa sesja i jeden request na jednostke; pusta allowlista tools i katalogow skilli; brak follow-up i continuation. |
| Pomiar | `CopilotContextTierPolicy` szacuje znaki / 3.5 + 16000 rezerwy, lacznie z system message i tools. Pomiar sluzy wyborowi tieru, nie podzialowi evidence. |
| Katalog | `CopilotSdkModelOptionsProvider.contextWindows` zwraca zera, gdy nie wykryje wiekszego okna. Gubi wiec znany zwykly limit modelu bez long tieru; nie zachowuje osobno limitu promptu i outputu. |
| Tier | AUTO wybiera long tier na podstawie katalogu, runtime weryfikuje tier i obserwuje `session.usage_info`; odpowiedzialnosc platformowa. |
| Scoring | Assessment: siedem wymiarow 0-4, wazony score100 i nieliniowy bucket DSP. Scope: szesc wymiarow score/scopeSignal, wazone punkty, limit 200. |
| Agregat | Sumuje gotowe oceny jednostek; DSP distribution, srednia Scope, coverage, confidence i usage liczone po jednostkach. |
| Zapis | Jeden `preparedPrompt` i `rawAiResponse` przy jednostce, partial persistence przed parsowaniem; feature-owned envelope V1. |
| UI | Jeden rozwijany wiersz jednostki; shared progress/activity/prompts/usage; CSV jedno issue na wiersz i jeden wklad jednostki do agregacji. |
| Referencje | Incident Analysis i Flow Explorer konsumują ten sam runtime, z innymi tools, sesjami i raportami. Nie sa core assessmentow. |

Baseline: 13 suite'ow, 61 testow, 0 failures/errors/skips:

```text
mvn -q -Pbackend-dev -Dtest=DeliveryAssessmentCopilotProviderTest,DeliveryScopeCopilotProviderTest,DeliveryAssessmentCopilotRunRequestAssemblerTest,DeliveryScopeCopilotRunRequestAssemblerTest,DeliveryEvidencePacketBuilderTest,DeliveryAssessmentScoringServiceTest,DeliveryScopeScoringServiceTest,DeliveryComplexityAssessmentJobServiceTest,DeliveryScopeComplexityJobServiceTest,CopilotContextTierPolicyTest,CopilotSdkModelOptionsProviderTest,PackageDependencyGuardTest test
```

Pierwsza proba baseline byla zatrzymana przez sandbox przy odczycie/pobraniu
parent POM; ta sama komenda poza sandboxem zakonczyla sie sukcesem.

## Proponowane rozwiazanie

### Budzet wejscia

Rozdzielic platformowy pomiar przygotowanego promptu od decyzji o tierze.
Neutralna capability w `aiplatform.copilot.runtime.context` udostepnia
estymacje, znany limit promptu, limit outputu i rezerwe, takze gdy model
nie ma long tieru. Katalog zachowuje zwykle limity z typowanego `models.list`
niezaleznie od billing metadata. Zwyklego limitu promptu nie utozsamiac
z suma prompt + output. Nie dodawac tabeli modeli w feature'ach.

Polityka platformowa nadal wybiera tier. Gdy pelne wejscie miesci sie
w dostepnym i potwierdzonym przez runtime tierze, zachowac obecny request.
Gdy nie miesci sie w efektywnym budzecie, assessment wybiera podzial.
Niepotwierdzony long tier nie moze uzasadniac wyslania zbyt duzego promptu.
Platforma zwraca typowany sygnal przekroczenia budzetu, z pomiarem i limitem,
bez znajomosci Jira, MR ani zasad punktowania.

Pierwszy pomiar pozostaje konserwatywna estymacja z konfigurowalnym marginesem
i rezerwa na ukryty runtime oraz odpowiedz. Nie przedstawia sie go jako
dokladnego licznika providera. Nie uruchamiac osobnej inference do pomiaru.
Gdy katalog nie zawiera limitu, oznaczyc pomiar UNKNOWN; nie uznawac go za
potwierdzenie bezpieczenstwa. Obecna pojedyncza proba moze dostarczyc
jednoznaczny limit w bledzie providera dla korekty tego runu.

### Podzial i wykonanie

Kazdy feature posiada wlasny planer i kontrakt ustalen; nie importuje sibling
feature'a. Planer pracuje na typowanym materialu, nie tnie gotowego Markdownu.
Podstawowa jednostka podzialu to caly MR. Deterministyczne pakowanie wedlug
estymowanego rozmiaru uzupelnia ponowny pomiar kazdego gotowego promptu.
Nie wymuszac dwoch polowek ani rownej liczby MR-ow.

Kazda czesc otrzymuje identyczny pelny Jira/Confluence wraz z hierarchia
i kontekstem rodzica, manifestem calej dostawy oraz jawnym zakresem MR-ow
tej czesci. Ocenia tylko widoczne evidence implementacyjne. Podzial zachowuje
pelna tresc wszystkich MR-ow lacznie i propaguje limity integracji.

Gdy pojedynczy MR nie miesci sie z kontekstem, dzielic jego material po
pelnych zmienionych plikach, zachowujac metadata MR i informacje o innych
czesciach. Jezeli sam kontekst Jira/Confluence z instrukcjami albo pojedynczy
nierozdzielny plik nie miesci sie w budzecie, zwroc konkretny blad zakresu
z pomiarem, bez arbitralnego przycinania i bez syntetycznej oceny.

Czesci uruchamiac kolejno wewnatrz obecnego bounded zadania jednostki.
Limit rownoleglych jednostek nadal ogranicza rownolegle sesje; nie tworzyc
zagniezdzonego, nieograniczonego executor pool. Jeden deadline obejmuje
planowanie, czesci, korekty i synteze. Przerwanie nie uruchamia kolejnych sesji.

Kazda czesc zwraca typowane ustalenia: rozpoznane zachowania, fakty dla
wymiarow rubryki, referencje MR/plik/sekcja, zaleznosci, powtorzenia,
confidence i visibility limits. Nie zwraca finalnego DSP ani score jednostki.
Walidacja sprawdza referencje wobec zakresu czesci i kompletna manifest coverage.

Oddzielna, budzetowana synteza otrzymuje pelny Jira/Confluence, manifest
oraz wszystkie zweryfikowane ustalenia. Deduplikuje te same zachowania
i zwraca obecny feature-owned kontrakt wymiarow. Backend wykonuje obecny
scoring raz. Synteza nie udaje bezposredniego odczytu surowego diffu;
ten sposob analizy jest jawny w provenance/visibility.

Gdy ustalenia nie mieszcza sie w jednym prompcie syntezy, laczyc je
hierarchicznie w budzetowanych grupach bez punktowania; zachowac referencje
i manifest coverage. Nie usuwac ustalen przez proste limitowanie liczby wpisow.
Skille i zaufane instrukcje rozrozniaja tryb ustalen i tryb finalnej oceny;
istniejaca rubryka i semantyczna hierarchia pozostaja obowiazujace.

Po jednoznacznym context overflow dozwolona jest ograniczona, deterministyczna
korekta podzialu tylko odrzuconego zakresu, w nowej sesji. Kazda proba
zmniejsza zakres lub budzet; limit prob i deadline sa jawne. Nie ponawiac
tak bledow rate limitu, auth, transportu ani parsowania. Awaria czesci
blokuje finalna ocene, pozostawiajac dotychczasowe ustalenia do wgladu.

### UX, zapis i agregacja

Rekomendowany widok zachowuje jeden wiersz Delivery Unit i jedna ocene.
Widoczny komunikat `Analiza w 2 czesciach` otwiera szczegoly czesci:
zakres MR-ow, status, ustalenia i ograniczenia. Progress pokazuje np.
`Analiza czesci 1/2`, potem `Laczenie wynikow`. Nie dodawac osobnych,
punktowanych wierszy tylko dlatego, ze model potrzebowal podzialu.

Zapisac typowana liste wywolan przy jednostce: stabilne id, role
`ASSESSMENT`, `EVIDENCE_PART`, `REDUCTION`, `SYNTHESIS`, zakres MR/plikow,
pomiar/budzet, status, timestamps, prepared prompt, raw response,
zweryfikowane ustalenia, session id, usage i blad. Zapis promptu przed
wykonaniem i raw response przed parsowaniem dotyczy kazdego wywolania.
Zastapic stary pojedynczy zapis prompt/raw lista wywolan; nie konkatenowac
wielu JSON-ow w polu udajacym jedna odpowiedz.

Usage jednostki sumuje wywolania, a usage runu sumuje jednostki raz.
Nie sumowac limitow okien jak tokenow zuzycia. Zachowac dostepne usage
rowniez po nieudanej probie, bez ukrytego magazynu telemetryki.
Agregaty, CSV i Trends nadal licza jedna finalna ocene jednostki.
Koszt pozostaje w shared aside; szczegoly czesci reuse'ują wspolne
mechanizmy prepared prompts, activity, evidence i diagnostics.

Alternatywa uzytkownika: osobne wyceny i ich suma. Jest prostsza runtime'owo,
ale zmienia jednostke punktowania oraz distribution, average i coverage.
Nie jest rekomendowana jako przezroczysty fallback, bo rozmiar okna modelu
moglby zmieniac total. Jej wybor wymaga zatwierdzenia zmienionej semantyki
scoringu i odpowiedniej aktualizacji tego planu.

## Conformance delta i konsumenci

| Granica | Delta / konsumenci |
| --- | --- |
| Request i endpointy | Bez zmian w obu feature'ach; automatyczny podzial. |
| Platforma | Neutralna estymacja/budzet, zachowanie zwyklych limitow modeli, typowany overflow i dostepne usage awarii. Audit wszystkich assemblerow `CopilotRunRequest`, prepared session, context policy, execution gateway i katalogu. |
| Katalog modeli | `api.aioptions`, UX Inspector selection validator, platformowe model billing/usage, wszystkie testowe creatory `CopilotModelOption`; publiczne opcje UI nie otrzymuja feature-specific pol. |
| Feature evidence | Dwa lokalne planery i manifest coverage, full Jira w kazdej czesci, rozlaczny kod; Jira/GitLab adaptery bez nowej semantyki assessmentu. |
| Prompt/skills/parser | Dwa lokalne kontrakty ustalen i syntezy; finalne rubryki/dimensions/scoring bez zmian. Packaged seed oraz explicit refresh DEFAULT kopii, CUSTOM zachowane. |
| Job/state/persistence | Oba job services, unit state/DTO, mappers, local persisters, envelope validators i import/export V1; lista wywolan zamiast pojedynczego prompt/raw. |
| FE | Oba feature API/models/pages, shared prepared-prompt/activity/result diagnostics, history/import/export, CSV utils; Trends sprawdzany jako konsument CSV. |
| Agregaty | Wciaz jedna jednostka: DSP distribution, Scope average, coverage oraz filters/team/author i CSV counting bez zwielokrotnienia. |
| Sesje/tools | Nowa sesja dla kazdego wywolania; brak tools, attachments, nowych uprawnien, follow-up lub continuation. |
| Zaleznosci | Feature -> platforma/integracje/shared; brak sibling imports i feature-specific platform branches. |
| Drift | Naprawa utraty zwyklych limitow i pomiaru tylko dla tieru na dotykanej granicy. Inne platformowe migracje poza zakresem. |

Publiczny aktualny format pozostaje V1, bez aliasow ani migratora starej
listy pol. W ramach zatwierdzonej zmiany usunac lokalne stare snapshoty
tylko tych dwoch assessmentow i ich stare artefakty; pozostale feature'y
i pliki uzytkownika pozostaja poza tym zakresem. Przed usunieciem sprawdzic
resolved paths oraz feature id w envelope. Jest to utrata dotychczasowych
lokalnych wynikow tych assessmentow, wynikajaca z polityki przed wdrozeniem.

## Zakres

Oba assessmenty, neutralny budzet platformowy, podzial/synteza, kompletny
operatorski zapis wywolan, UI, import/export, regresja scoringu i konsumentow.

## Non-goals

Zmiana modelu bez decyzji operatora, nowa skala punktow, sumowanie wycen
technicznych czesci, pomijanie kodu, pobieranie evidence tools, zmiana grafu
issue-MR, nowe skille wybierane per run albo automatyczny podzial innych
feature'ow analitycznych.

## Ograniczenia i ryzyka

- Estymacja moze roznic sie od providera; margines i ograniczona korekta
  zmniejszaja ryzyko, ale nie daja gwarancji dla dowolnego wejscia.
- Pelny Jira w kazdej czesci oraz dodatkowa synteza zwiekszaja koszt i czas.
- Synteza nie widzi surowego kodu; typed findings i coverage zmniejszaja
  ryzyko zgubienia zachowania, nie gwarantuja identycznej oceny modelu.
- Granica MR/plik moze przecinac zaleznosc semantyczna; manifest i ustalenia
  o zaleznosciach musza trafic do syntezy.
- Zmiana zapisu wymaga wszystkich konsumentow i usuniecia starych lokalnych
  danych obu assessmentow. Nie zmieniac formatu innych feature'ow.
- Bez arbitralnego ciecia sam bardzo duzy Jira albo pojedynczy plik nadal
  moze pozostac nieocenialny w wybranym modelu, z czytelnym powodem.

## Kryteria akceptacji i macierz testow

| Scenariusz | Oczekiwany dowod |
| --- | --- |
| Model bez long tieru | Katalog zachowuje jego zwykly limit, planner respektuje budget bez hardcoded model id. |
| Mala jednostka | Jeden request, obecny finalny JSON i scoring; zapis jednego wywolania. |
| Model z long tierem | Mieszczacy sie input uzywa obecnej polityki tieru; brak podzialu tylko dlatego, ze przekroczono zwykle okno. |
| Duze nierowne MR-y | Kazdy gotowy prompt miesci sie w szacowanym budzecie; identyczny pelny Jira; union zakresow == input, brak utraty/dublowania. |
| Jeden duzy MR | Podzial po plikach, metadata i referencje zachowane; pojedynczy nierozdzielny plik -> jawny blad. |
| Sam Jira za duzy | Preflight diagnostyka, bez inference i przycinania evidence. |
| Niedoszacowanie | Synthetic provider overflow koryguje tylko odrzucony zakres i konczy sie po limicie prob/deadline. Inne bledy nie wyzwalaja podzialu. |
| Awaria lub zly JSON czesci | Partial zapis i raw response zachowane, brak finalnej oceny/niepelnej sumy, widoczny blad i dostepne usage. |
| Zaleznosc/powtorzenie miedzy czesciami | Synteza otrzymuje wszystkie fakty i referencje; dedup nie gubi odmiennych zachowan. |
| Nadmierna synteza | Budzetowana redukcja bez scoringu, kompletna coverage albo jawny failure. |
| UI/history/import/export | Wiersz jednostki raz, details czesci i progress dostepne; wszystkie wywolania po aktualnym V1 round-trip; stare lokalne formaty usuniete. |
| Agregat/CSV/Trends | Jedna ocena jednostki liczona raz, brak duplikatow punktow; usage sumuje kazde wywolanie raz. |
| Pozostali konsumenci runtime | Regresja create/resume, tier verification, tool allowlist, auth, activity, usage i raportow; brak nowych importow features do platformy. |

Wszystkie nowe i zmieniane scenariusze sa w pelni fikcyjnym CRM; nie kopiowac
Request-ID, danych ticketow, projektow ani diffow operatora.

## Kroki

- [x] Krok 1: dostarczyc neutralny kontrakt pomiaru/budzetu, poprawic katalog
  limitow, zintegrowac decyzje z polityka tieru i typowanym overflow; dowod:
  testy katalogu, context policy, execution, auth, API options, konsumentow
  model selection i `PackageDependencyGuardTest`, bez zmiany obecnego
  zachowania innych feature'ow poza zachowaniem poprawnych metadanych.
- [x] Krok 2: w obu assessmentach dostarczyc deterministyczny podzial pelnego
  materialu, typed findings, synteze/redukcje, deadline i ograniczona korekte;
  zaktualizowac prompt/skills/parser jako jeden kontrakt. Dowod: macierz
  completeness/budget/overflow/overlap/failure oraz wszystkie testy obu
  feature'ow, scoring finalny raz i zachowana mala jednostka.
- [x] Krok 3: dostarczyc liste wywolan, partial persistence, usage, oba UI,
  current V1 import/export i cleanup starego lokalnego formatu; refresh
  DEFAULT effective skills bez nadpisania CUSTOM. Dowod: state/persistence/
  envelope/API round-trip, shared UI i feature tests, CSV/Trends regression;
  widoczny status kazdej czesci i jedna ocena/agregacja jednostki.
- [x] Krok 4: wykonac architecture/consumer diff, anonimizacje i weryfikacje
  wspolnej zmiany: `npm --prefix frontend test -- --watch=false`, potem
  `npm --prefix frontend run build`, potem
  `mvn -q -Pbackend-dev clean package`; aktualizowac oba runtime flow,
  platformowe docs i lokalne AGENTS, przeniesc zatwierdzone decyzje do
  architektury oraz zamknac potrzebe/plan po dostarczeniu.

## Dowod wykonania 2026-10-01

- `npm --prefix frontend test -- --watch=false` z tymczasowym runner config
  Vitest `{ test: { maxWorkers: 4 } }`: 84 pliki, 640 testow, wszystkie passed.
  Config ograniczal wspolbieznosc na lokalnej maszynie; zostal usuniety.
- `npm --prefix frontend run build`: sukces, aktualny bundle zapisany w
  `src/main/resources/static`.
- `mvn -q -Pbackend-dev clean package`: sukces; 366 suite'ow, 1803 testy,
  0 failures, 0 errors, 1 opcjonalny live SDK test skipped. Powstal
  `target/team-delivery-workspace-0.0.1-SNAPSHOT.jar` z aktualnym UI.
- Pełny build wykryl kolizje domyslnych nazw nowych parserow. Nadano osobne
  nazwy beanow, potwierdzono startup Spring i powtorzono caly czysty build.
- Workflow tests obu feature'ow obejmuja mala jednostke, nierowne MR-y,
  pelny Jira/Confluence kazdej czesci, zachowanie plikow/coverage, atom/base
  overflow, korekte i limit prob, malformed JSON, partial failure, redukcje,
  jeden finalny parser/scoring oraz deadline/przerwanie bez kolejnej sesji.
- Parser tests chronia referencje w zakresie, coverage, odmienne zachowania,
  wymiary i zaleznosci, takze przed przeniesieniem dowodu do innego zachowania.
  Import/export V1 round-trip zachowuje wszystkie wywolania i odrzuca stary
  ksztalt pojedynczego prompt/raw. Timeout zwalnia worker; usage sumuje
  konsumpcje i zachowuje maksimum obserwacji okna.
- Pelny zestaw zawiera `PackageDependencyGuardTest`,
  `IntegrationPackageBoundaryTest`, pozostalych konsumentow runtime,
  state/persistence/API oraz CSV/Trends. Nie dodano sibling imports ani
  semantyki assessmentu do platformy/integracji.
- Diff review i `git diff --check`: sukces. Nowe i zmieniane scenariusze
  sa fikcyjnym CRM; nie uzyto danych organizacji ani Request-ID operatora.
- Sprawdzono index/envelopes oraz skonfigurowany katalog session-state:
  0 starych wynikow obu assessmentow, 0 ich starych sesji; 5 pozostalych
  runow zachowano. Obie effective kopie DEFAULT rubryk odswiezono przez
  porownanie z poprzednim seedem. CUSTOM nie sa nadpisywane.

Trwale decyzje przeniesiono do obu runtime-flow, key-decisions,
system-overview, continuation guide i lokalnych AGENTS. Plan pozostaje
operacyjna macierza akceptacji oraz instrukcja rollbacku. Wieloczesciowy
workflow zweryfikowano na syntetycznym evidence i kontrolowanych odpowiedziach
SDK; pomiar nie jest dokladnym tokenizerem providera, a model moze nadal
odrzucic nierozdzielny material z jawna diagnostyka.

## Etapowanie i rollback

Platformowy pomiar i katalog sa pierwszym kompilowalnym inkrementem.
Nowy multi-call workflow wlaczac dopiero z aktualnym zapisem i UI.
Rollback runtime'u moze wylaczyc automatyczny podzial i przywrocic jedno
wywolanie z jawna diagnostyka zbyt duzego inputu, zachowujac aktualny
format listy wywolan; nie przywraca starych danych ani kompatybilnosci.

## Zrodla techniczne

- [Copilot SDK: usage and billing](https://github.com/github/copilot-sdk/blob/main/docs/features/usage-and-billing.md)
  rozroznia eventy usage i metadata contextInfo; to ostatnie moze byc null
  przed inicjalizacja, nie jest licznikiem dowolnego niewyslanego promptu.
- [Generated Node RPC](https://github.com/github/copilot-sdk/blob/main/nodejs/src/generated/rpc.ts)
  rozroznia prompt budget od context window i opisuje
  `recomputeContextTokens` dla juz istniejacych wiadomosci sesji.
- [Node README: Infinite Sessions](https://github.com/github/copilot-sdk/blob/main/nodejs/README.md#infinite-sessions)
  opisuje compaction. Nie zakladac, ze rozwiazuje odrzucenie pierwszej
  nadmiernej wiadomosci, ktore operator zaobserwowal.

Upstream main sprawdzony 2026-10-01; implementacja musi wykorzystac
typowany kontrakt przypietego Java SDK 1.0.11 / CLI minimum 1.0.57.
Nie zakladac, ze nowe pola upstream sa juz w lokalnym artefakcie; ewentualna
zmiana wersji SDK wymaga osobnej decyzji, nie jest ukrytym krokiem tego planu.
