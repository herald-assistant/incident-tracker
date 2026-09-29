# Wspolna strategia branchowa UI Explorera i UX Inspectora

Status: done

Source need: [UI Explorer i UX Inspector: analiza wedlug wybranego brancha](../needs/frontend-analysis-branch-source-strategy.md)

## Potrzeba / dlaczego

Operator chce analizowac kod dostepny na wybranym branchu, tak jak w Flow
Explorerze. Obowiazkowa zgodnosc SHA pomiedzy katalogiem widokow i startem
blokuje ten przypadek, a obecne pinned tools i continuation utrzymuja inna
strategie nawet po usunieciu samego konfliktu startu.

## Baseline i porownanie

| Obszar | Flow Explorer | UI Explorer | UX Inspector |
| --- | --- | --- | --- |
| Wybor | System, branch, endpoint z cache | Frontend, branch, widok ze wspolnego katalogu | Frontend, branch, widok ze wspolnego katalogu i capture |
| Start request | Branch i endpoint; bez SHA | Branch, screenId i wymagane sourceRevision | Branch, viewId i wymagane sourceRevision |
| Start kontekstu | Ponowny odczyt po nazwie brancha, bez porownania SHA | Ponowne discovery i porownanie SHA; roznica blokuje run | Ponowne focused discovery i porownanie SHA; roznica blokuje run |
| GitLab cache | Trwaly cache inventory i 10-minutowy cache integracji | Trwaly cache katalogu widokow oraz 10-minutowy cache integracji | Ten sam trwaly cache katalogu i 10-minutowy cache integracji |
| Tools/follow-up | Scope branchowy bez pinned SHA | Initial generic reads uzywaja brancha, frontend slices SHA; follow-up uzywa SHA | Repository scope i frontend slices sa przypiete do SHA, takze w follow-up |

Katalog UI/UX jest wspolny w `frontendcatalog`; jego klucz zawiera ref, a
zapisany `cachedAt` nie jest publicznym polem. `refresh=true` usuwa wpis tego
katalogu. Neutralna warstwa GitLab ma osobny cache dla metadata commita,
drzewa i plikow, domyslnie na 10 minut. Sam `refresh=true` nie stanowi
gwarancji odczytu najnowszego HEAD.

Flow Explorer pokazuje `Data:` przy etykiecie `Endpoint`, w lokalnym formacie
`pl-PL` (krotka data i godzina). UI Explorer i UX Inspector pokazuja obecnie
SHA w katalogu widokow, statusie/parametrach runu i nazwach pobieranych
raportow. UX Inspector pokazuje je rowniez w stopce runu.
`FileSystemFrontendViewCatalogCache` przechowuje `cachedAt` w envelope V4,
ale nie przekazuje go do modelu. UI Explorer ma export/local run V6 oraz
reader V5, a UX Inspector export V3. Te zmieniane formaty trzeba sprowadzic
do jednego aktualnego V1 zgodnie z root `AGENTS.md`.

Zastany drift: kod discovery buduje graf z odczytow pod nazwa brancha, po czym
rozwiazuje ref do commit id. Wymaganie rownosci SHA nie dowodzi samo w sobie,
ze kazdy plik grafu pochodzi z tego commita. Tego ograniczenia nie nalezy
przedstawiac operatorowi jako gwarancji spojnego snapshotu.

## Proponowane rozwiazanie

Nowe analizy UI Explorera i UX Inspectora traktuje branch jako jedyny ref
zrodla. Katalog pozostaje wskazowka wyboru widoku i ma jawny czas zebrania;
job ponownie szuka tego widoku na tym samym branchu. Request startu nie wymaga
`sourceRevision`, a roznica SHA katalogu i biezacego odczytu nie jest
konfliktem. Brak wybranego widoku w zrodle pozostaje jawnym bledem/luka.

Wszystkie odczyty AI, initial i follow-up, uzywaja ukrytego branch scope'u.
Usunac feature'owe pinning SHA i gate'y `expectedRevision` tylko dla tych dwoch
feature'ow. Zachowac ograniczenie grupy, projektu, path scope, walidacje
argumentow i read-only allowliste. Neutralne tools musza obslugiwac jawnie
tryb branchowy obok istniejacego trybu pinned uzywanego przez innych
konsumentow; nie wolno globalnie rozluznic pinned scope.

Katalog przenosi czas jego utworzenia/zapisania do cache, zachowany bez zmian
przy cache hit. Obydwa formularze pokazuja `Data: <data i godzina>` przy
etykiecie `View`, z tym samym formatowaniem i wizualnym ukladem co etykieta
`Endpoint` we Flow Explorerze. Przy tworzeniu katalogu bez trwalego cache
data oznacza czas jego zebrania. Nie jest to czas wszystkich odczytow kodu.
Usunac z interfejsow SHA/commit id: dropdown widokow, status i parametry
runu, wynik oraz nazwy pobieranych raportow. Nowy wynik pokazuje branch i
czas zebrania kontekstu bez twierdzenia o spojnosci wszystkich plikow.

Publiczne DTO katalogu, joba i wyniku maja tylko aktualny ksztalt branchowy.
Formaty z polem wersji (cache, export, local run i continuation) maja wersje
`1`. Nie utrzymujemy readerow
starych formatow, lokalnych runow ani kontynuacji pinned. Usunac powiazane
lokalne dane i artefakty poprzednich formatow. Import aktualnego formatu
pozostaje read-only.
`revisionSha256` raportu jest wewnetrznym warunkiem edycji raportu, a nie
commit id GitLaba; moze pozostac w kontrakcie technicznym, bez wyswietlania
uzytkownikowi.

Alternatywa: usunac tylko porownanie przy starcie i przypiac aktualny SHA dla
runu. Zostala odrzucona, poniewaz operator wskazal, ze caly run i follow-up
maja czytac branch z cache bez pinning SHA.

## Zakres

- Publiczne DTO startu, katalogu, joba, wyniku i prezentacja UI obu feature'ow,
  w tym wszystkie widoczne SHA/commit id i nazwy pobieranych raportow.
- `frontendcatalog` cache i timestamp katalogu, deterministic context,
  GitLab frontend research tools, session hidden scope i feature policies.
- Prompt/artifacts, source metadata, report mapping, history, import/export i
  follow-up po restarcie.
- Aktualizacja kanonicznych runtime flow, decyzji architektonicznych,
  lokalnych `AGENTS.md` o immutable revision i testow wszystkich konsumentow
  zmienionej neutralnej capability.
- Usuniecie starych readerow, fallbackow i sciezek kontynuacji tych dwoch
  feature'ow, ktore sluza tylko kompatybilnosci z poprzednimi kontraktami,
  wraz z lokalnymi danymi i artefaktami tych formatow.

## Non-goals

- Zmiana strategii Flow Explorera lub innych feature'ow GitLaba.
- Obietnica najnowszego HEAD przy cache hit albo jednego commita dla calego
  runu.
- Rozszerzenie dostepu AI poza wybrany frontend i dozwolone sciezki.

## Conformance delta i konsumenci

Poziom: L3, poniewaz zmienia sie model sesji GitLab, publiczne kontrakty,
portable formaty i continuation. `integrations.gitlab`, `agenttools.gitlab`
oraz `frontendcatalog` wymagaja audytu L2.

| Element | Zamierzona zmiana |
| --- | --- |
| Publiczne API/DTO | Usunac wymaganie `sourceRevision` ze startu nowych runow; dodac timestamp katalogu zachowany przez cache hit i branchowe metadata runu. |
| Context/evidence | Szukac widoku na wybranym branchu bez `expectedRevision`; zachowac rzeczywiste source gaps i limity. |
| Prompt/artifacts/skills | Usunac twierdzenia o immutable/pinned code, opisac cache i mozliwa zmiane brancha miedzy odczytami. |
| Tools/policy/hidden scope | Branch we wszystkich odczytach obu feature'ow, bez SHA; zachowac repository/path scope i budzet. |
| Report/result | Zachowac sekcje merytoryczne; zmienic metadane provenance tak, by nie deklarowaly pinned commit. |
| Job/persistence/export | Zmieniane formaty cache/export/local run/continuation sprowadzic do V1 i usunac stare readery, lokalne dane oraz artefakty poprzednich formatow. |
| Shared FE/UX | Pokazac `Data:` nad `View` jak nad `Endpoint` we Flow Explorerze; usunac wszystkie widoczne SHA/commit id, rowniez z nazw pobieranych raportow. |
| Zaleznosci | Bez importow miedzy sibling features; zmiana neutralnych capability pozostaje neutralna. |

Konsumenci wspolnego katalogu: UI Explorer i UX Inspector w backendzie i
Angularze. Konsumenci neutralnych GitLab frontend tools: oba feature'y.
Konsumenci `GitLabRepositoryToolScope`: UX Inspector i Operational Context
Assistance; pinned zachowanie tego drugiego musi pozostac nienaruszone.
Publiczny `sourceRevision` jest tez odczytywany przez job state, report,
local history, import/export, follow-up oraz komponenty wynikow obu feature'ow.

## Ograniczenia i ryzyka

- Branch moze przesunac sie miedzy odczytami oraz miedzy initial run i
  follow-up. Odpowiedz musi ujawniac to ograniczenie, nie deklarowac snapshotu.
- Cache katalogu nie ma TTL, a cache integracji ma domyslnie 10 minut.
  Timestamp katalogu nie jest dowodem czasu wszystkich odczytow kodu.
- Capture UX Inspectora moze pochodzic z wdrozenia innego niz branch wybrany
  przez operatora; pozostaje runtime evidence, a nie walidacja rewizji.
- Zmiana source metadata dotyka portable exportow i wznowienia po restarcie.
  Stare dane nie sa migrowane; lokalne dane i artefakty poprzednich formatow
  sa usuwane, a reader aktualnego kontraktu odrzuca stare rekordy.
- Sprzatanie lokalnych danych wymaga precyzyjnego wskazania wpisow katalogu
  i runow UI/UX w workspace; nie moze usuwac runow innych feature'ow ani
  niespokrewnionych plikow operatora.
- Nie wolno oslabic pinned reads Operational Context Assistance przez zmiane
  neutralnego repository tool scope.

## Kryteria akceptacji

1. Zmiana SHA brancha po pobraniu katalogu nie blokuje startu tylko z powodu
   niezgodnosci rewizji; wybrany widok jest ponownie rozpoznawany na branchu.
2. Initial i follow-up, rowniez po restarcie, przekazuja do GitLaba branch,
   nigdy SHA jako ref odczytu nowego runu.
3. Wszystkie GitLab tools obu feature'ow pozostaja read-only i w granicach
   wybranego projektu oraz dozwolonego path scope; pinned inni konsumenci sa
   bez zmian.
4. Oba formularze pokazuja przy `View` `Data:` w formacie i ukladzie Flow
   Explorera. Cache hit zachowuje timestamp; refresh tworzy nowy timestamp.
   UI nie pokazuje SHA/commit id w wyborze widoku, stanie i wyniku runu ani
   nazwie pobieranego raportu.
5. Obslugiwany jest tylko aktualny kontrakt requestu, exportu, local runu i
   continuation w wersji `1`; stary format jest jawnie odrzucany, a lokalne
   dane i artefakty poprzednich formatow sa usuwane. Testy backendu, Angulara
   i `PackageDependencyGuardTest` przechodza.

## Kroki

- [x] Krok 1: Utrwalic baseline i testy zmiany brancha po katalogu dla obu
  feature'ow, osobno cache hit/miss, brak widoku i brak refa. Dowod: celowane
  testy katalogu, context service i joba z obecnym oczekiwanym konfliktem.
- [x] Krok 2: Zmienic neutralny katalog i feature-owned start/context/API tak,
  by nowy run nie wymagal SHA i uzywal branchowego discovery. Dodac czytelny
  timestamp katalogu zachowany przez cache hit bez obietnicy swiezosci GitLab
  cache. Dowod: testy MockMvc, serwisow i Angulara dla cache hit, refresh i
  obu formularzy.
- [x] Krok 3: Przelaczyc initial i follow-up tools, hidden scope, policy,
  przygotowanie AI i UX Inspector source artifacts na branch, bez rozluzniania
  pinned trybu innych konsumentow. Dowod: testy invocation i GitLab
  integration z asercja rzeczywistego refa w kazdym odczycie.
- [x] Krok 4: Zmienic source metadata wyniku, reportu, exportu, local history
  i continuation. Ustawic V1 w zmienianych formatach cache/export/local run,
  usunac stare readery, pinned continuation i precyzyjnie zidentyfikowane
  lokalne dane/artefakty poprzednich formatow; stary format jawnie odrzucac.
  Dowod: testy portability,
  odtwarzania i follow-up po restarcie tylko nowego formatu oraz odrzucenia
  starego formatu.
- [x] Krok 5: Zaktualizowac UI: `Data:` nad oboma polami `View` i usuniecie
  prezentacji SHA/commit id, takze w nazwach raportow. Zaktualizowac polskie
  runtime guidance, dokumenty `architecture/` i lokalne `AGENTS.md` oraz
  wykonac conformance diff. Dowod: testy Angulara,
  jego build, `mvn -q -Pbackend-dev clean package`, testy neutralnych
  konsumentow i `PackageDependencyGuardTest`; przed przekazaniem przejrzec
  diff i fikcyjne scenariusze CRM.

## Wynik weryfikacji

- Angular: 638/638 testow oraz produkcyjny `npm --prefix frontend run build`.
- Backend: `mvn -q -Pbackend-dev clean package` z pelnym zestawem testow;
  dodatkowo celowane testy GitLab verified reads i branchowego scope.
- Cache katalogu zachowuje `dataCollectedAt` na cache hit i nadaje nowy czas
  na refresh. Branch moze przesunac sie pomiedzy odczytami runu.
- Stare lokalne runy UI/UX oraz poprzedni cache widokow zostaly usuniete z
  lokalnego `tdw-data`; import poprzednich wersji jest odrzucany.
