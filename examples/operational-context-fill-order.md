# Operational Context Fill Order

## Recommended Order

1. `systems.yml`
   Zbuduj najpierw mape runtime systems. To sa podstawowe byty, do ktorych beda odnosic sie pozostale pliki.
2. `repo-map.yml`
   Zmapuj kod, repozytoria i moduly do runtime signals. To laczy incident evidence z realnym kodem.
3. `processes.yml`
   Opisz glowne przeplywy biznesowe i operacyjne, korzystajac z juz ustalonych systemow i repo.
4. `integrations.yml`
   Dodaj kontrakty miedzy systemami. Integracje sa latwiejsze do opisania, gdy istnieja juz systemy i procesy.
5. `bounded-contexts.yml`
   Uporzadkuj granice semantyczne i jezyk domenowy, korzystajac z procesow, systemow i repo.
6. `teams.yml`
   Dopiero na koncu przypisz ownership. W tym repo zespoly matchuja sie glownie przez `owns.*` do innych bytow.
7. `glossary.md`
   Doplenuj slownik terminow po ustaleniu procesow i bounded contexts.
8. `handoff-rules.md`
   Dopracuj reguly routingu, gdy wiadomo juz kto, co i w jakim obszarze posiada.
9. `operational-context-index.md`
   Zrob krotki sanity check dokumentacyjny, zeby opis katalogu odpowiadal realnej zawartosci.

## Common Rules

- Zaczynaj od stabilnych identyfikatorow i relacji, a dopiero potem dopisuj `signals.*`.
- Nie tworz osobnych wpisow per environment, namespace, branch, pod, host variant albo pojedynczy endpoint.
- Nie zgaduj ownershipu z samej nazwy repo, grupy GitLaba albo nazwy hosta.
- Gdy evidence jest slabe, wpisz `openQuestions` zamiast wymyslac model.
- Po kazdym pliku sprawdz, czy wszystkie referencje do innych ids sa spojne z pozostala czescia katalogu.

## Per File Details

### `systems.yml`

**Cel**

Zbudowac mape wszystkich runtime systems, wewnetrznych i zewnetrznych. Ten plik opisuje nodes, nie kontrakty miedzy nimi.

**Najlepsze zrodla**

- runtime evidence: `serviceName`, `containerName`, `applicationName`, host, queue, topic, schema
- GitLab deterministic evidence: repo, package roots, klasy, konfiguracja klientow
- istniejace `integrations.yml`, `processes.yml`, `repo-map.yml`
- config aplikacji i znane nazwy komponentow

**Co wpisywac**

- jeden wpis per stabilny runtime system
- `type` rozrozniajacy np. `internal` i `external`
- `ownerTeamId` tylko gdy ownership jest potwierdzony
- `repos`, `processes`, `contexts`, `dependsOn` tylko gdy istnieja mocne powiazania
- krotkie runtime fingerprints, po ktorych system da sie rozpoznac w incydencie

**Czego unikac**

- osobnych systemow dla kazdego host variant, poda, namespace albo deploya
- modelowania endpointu jako systemu
- przemianowywania tego pliku na `external-systems.yml`

**Done when**

Kazdy istotny system runtime da sie rozpoznac po incident signals, a odwolania do niego sa gotowe do uzycia w pozostalych plikach.

### `repo-map.yml`

**Cel**

Powiazac runtime evidence z repozytoriami, sciezkami kodu i modulami, tak aby wiadomo bylo, jaki kod otworzyc podczas analizy incydentu.

**Najlepsze zrodla**

- GitLab project path i group path
- resolved code references z analizy
- file paths, package roots, class names, entry classes
- stacktrace hotspots
- runtime aliases: `projectName`, `serviceName`, `containerName`

**Co wpisywac**

- jeden wpis per kanoniczne repozytorium GitLab
- `gitLab.projectPath` i `gitLab.groupPath` jako glowna tozsamosc repo
- `sourceLayout.*` i `sourceLookupHints.*` jako wskazowki do szybkiego wejscia w kod
- `modules` tylko dla stabilnych, powtarzalnych obszarow kodu
- `systems`, `processes`, `contexts` spojne z pozostala mapa

**Czego unikac**

- duplikatow repo z powodu roznych aliasow runtime
- jednego wpisu per plik albo per stacktrace frame
- wymyslania modulow, gdy nie ma wyraznych granic w kodzie

**Done when**

Z pojedynczego incydentu da sie przejsc od sygnalu runtime do konkretnego repo, pakietu i prawdopodobnego miejsca w kodzie.

### `processes.yml`

**Cel**

Opisac glowne przeplywy biznesowe i operacyjne, ktore lacza systemy, kroki i sygnaly zakonczenia.

**Najlepsze zrodla**

- logi i trace pokazujace kolejnosc krokow
- nazwy handlerow, commandow, eventow i workflow
- `systems.yml` i `repo-map.yml`
- incydenty pokazujace, gdzie flow najczesciej sie zatrzymuje

**Co wpisywac**

- jeden wpis per stabilny flow biznesowy lub operacyjny
- `steps` tylko dla krokow, ktore maja wartosc diagnostyczna
- `systems`, `repos`, `contexts` powiazane z flow
- `completionSignals`, `failureSignals`, `handoffHints`, jesli sa wyrazne

**Czego unikac**

- modelowania kazdego requestu jako osobnego procesu
- nazywania procesow czysto technicznie, jesli istnieje nazwa biznesowa
- zbyt szczegolowych krokow, ktore nic nie daja w triage

**Done when**

Da sie odpowiedziec na pytanie, na jakim kroku flow pojawil sie problem i jakie systemy sa zaangazowane.

### `integrations.yml`

**Cel**

Opisac stabilne kontrakty miedzy systemami. Ten plik opisuje edges pomiedzy systems.

**Najlepsze zrodla**

- client config, REST/SOAP clients, messaging config
- hosty, endpoint families, queue names, topic names
- timeout, retry, serialization i payload mismatch markers
- `systems.yml`, `processes.yml`, `teams.yml`

**Co wpisywac**

- jeden wpis per sensowny operational contract
- `from` i `to` odnoszace sie do system ids, jesli sa modelowane
- `protocol`, `type`, `processes`, `contexts`
- krotkie sygnaly rozpoznawcze przydatne podczas incydentu

**Czego unikac**

- jednego wpisu per pojedyncze wywolanie HTTP
- rozbijania tej samej integracji na request i response
- duplikatow przez rozne srodowiska albo host variants

**Done when**

Da sie latwo rozpoznac, czy incydent dotyczy konkretnego kontraktu miedzy systemami i komu taki kontrakt przekazac.

### `bounded-contexts.yml`

**Cel**

Opisac granice semantyczne i jezyk domenowy, ktore pomagaja zrozumiec, o jakim obszarze biznesowym jest incydent.

**Najlepsze zrodla**

- powtarzalne terminy biznesowe w kodzie i incydentach
- `processes.yml`, `systems.yml`, `repo-map.yml`, `integrations.yml`
- package roots i klasy sugerujace jezyk domenowy
- runbooki i lokalne slownictwo zespolu

**Co wpisywac**

- jeden wpis per stabilny semantic boundary
- `terms`, `purpose`, `scope.*`, `relations`
- `systems`, `repos`, `processes` zgodne z pozostala mapa
- `ubiquitousLanguage` tylko z realnym jezykiem domenowym

**Czego unikac**

- bounded context per controller, endpoint, adapter albo modul techniczny
- budowania contextu tylko z pakietu `common` lub `integration`
- kopiowania calego glossary do kazdego wpisu

**Done when**

Z incydentu da sie wywnioskowac, ktorej czesci domeny dotyczy problem i jakie inne konteksty moze przecinac.

### `teams.yml`

**Cel**

Opisac ownership map. W tym repo to jest warstwa najbardziej zalezna od pozostalych plikow.

**Najlepsze zrodla**

- explicit `ownerTeamId` z `systems.yml`, `processes.yml`, `repo-map.yml`, `bounded-contexts.yml`, `integrations.yml`
- runbooki, handoff rules, potwierdzony on-call routing
- powtarzalne reczne przekazania w incydentach

**Co wpisywac**

- `owns.systems`, `owns.repos`, `owns.processes`, `owns.contexts`, `owns.integrations`
- `purpose` tylko jako krotki opis odpowiedzialnosci
- `signals.*` jako wsparcie triage, nie glowna podstawa ownershipu
- `handoff.target` i `handoff.requiredEvidence` jako krotkie operacyjne wskazowki

**Czego unikac**

- zgadywania zespolu po namespace, GitLab group albo pojedynczym logu
- traktowania `signals.*` jako glownego dowodu ownershipu
- tworzenia aliasow dla tego samego zespolu

**Done when**

Matcher moze sensownie wskazac zespol glownie przez relacje ownershipu do innych, juz zmapowanych bytow.

### `glossary.md`

**Cel**

Dostarczyc lokalny slownik pojec, ktory poprawia interpretacje incydentu i wspiera matching terminow.

**Najlepsze zrodla**

- bounded contexts
- procesy i ich nazewnictwo
- lokalne runbooki i uzgodnione definicje

**Co wpisywac**

- terminy, synonimy, definicje, typowe evidence signals
- rozroznienia typu "nie mylic z"

**Czego unikac**

- generycznych terminow frameworkowych
- duplikowania tego samego slowa w wielu wariantach bez sensu

**Done when**

Najwazniejsze terminy z incydentow maja jednoznaczne znaczenie w waszym kontekscie.

### `handoff-rules.md`

**Cel**

Zdefiniowac praktyczne reguly routingu incydentow, gdy znane sa juz systems, integrations, bounded contexts i teams.

**Najlepsze zrodla**

- realne wzorce przekazan
- typowe failure modes integracji
- odpowiedzialnosci zespolow i partnerow

**Co wpisywac**

- kiedy route to current owner
- kiedy route to integration owner
- kiedy route do platformy, bazy albo partnera
- wymagane evidence dla przekazania

**Czego unikac**

- ogolnikowych zasad bez warunkow
- reguly sprzecznych z ownershipem z katalogu

**Done when**

Dla najczestszych klas problemow wiadomo, gdzie przekazac incydent i jakie evidence dolaczyc.

### `operational-context-index.md`

**Cel**

Utrzymac krotki opis calego katalogu i roli poszczegolnych plikow.

**Najlepsze zrodla**

- aktualna zawartosc katalogu
- realne znaczenie poszczegolnych plikow w kodzie

**Co wpisywac**

- zwięzly opis kazdego pliku
- ewentualnie jedna notke o sposobie uzycia katalogu

**Czego unikac**

- dlugiej dokumentacji procesowej
- opisow, ktore rozjechaly sie z kodem i loaderem

**Done when**

Nowa osoba po przeczytaniu indexu rozumie, po co istnieje kazdy plik i gdzie zaczac prace.

## Suggested Workflow

1. Zbierz twarde evidence z kodu i runtime.
2. Ustal kanoniczne ids dla systems i repositories.
3. Zmapuj procesy i integracje.
4. Wyodrebnij bounded contexts.
5. Na koncu przypisz teams i handoff.
6. Wszystko, czego nie da sie uczciwie potwierdzic, wpisz do `openQuestions`.
