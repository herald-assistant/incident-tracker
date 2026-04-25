# Key Decisions

## 1. `correlationId` jest jedynym biznesowym polem requestu `/analysis`

`correlationId` nie jest traktowany jako header tej aplikacji, tylko jako dane
wejsciowe analizy.

`branch` i `environment` nie sa juz podawane przez klienta.
Sa wyprowadzane podczas zbierania evidence, glownie z logow Elasticsearch i
tagu obrazu kontenera.

Powod:

- aplikacja nie uczestniczy w badanym flow mikroserwisowym,
- uzytkownik recznie uruchamia analize dla konkretnego przypadku,
- kontekst deploymentu jest najlepiej dostepny w logs analizowanego incydentu,
- request `/analysis` pozostaje minimalny i prosty dla uzytkownika.

## 2. `gitLabGroup` pochodzi z konfiguracji aplikacji, a `gitLabBranch` z deployment evidence

Grupa GitLaba nie jest dedukowana z logs ani trace.

Powod:

- to stabilniejsza informacja infrastrukturalna niz evidence,
- ogranicza przestrzen przeszukiwania,
- upraszcza prompt i korzystanie z tooli.

Obecny model:

- `group` z `application.properties`,
- `branch` i `environment` z osobnego kroku deployment context,
- AI interpretuje tylko `project`, `filePath` i - jesli DB capability jest
  wlaczona - exact `schema.table` zwrocone przez tools.

## 3. Analiza jest AI-first

Wczesniejszy etap rule-based byl krokiem edukacyjnym.

Obecny kierunek jest taki:

- evidence jest zbierane przez providery,
- AI wykonuje wlasciwa interpretacje,
- wynik analizy pochodzi z providera AI.

To oznacza, ze nie wracamy juz do centralnego rule engine jako glownego flow,
chyba ze kiedys potrzebny bedzie fallback albo guardrails.

## 4. Evidence pipeline jest deterministyczny i pracuje na wspolnym context

`AnalysisEvidenceProvider` nie dostaje juz tylko `correlationId`.
Provider dostaje `AnalysisContext`, czyli:

- `correlationId`,
- evidence zebrane przez poprzednie providery.

Powod:

- GitLab potrzebuje wskazowek z logs i trace,
- przyszle providery tez moga zalezec od wczesniejszego evidence,
- unikamy centralnego, rosnacego mappera "od wszystkiego".

Aktualny model wykonania:

- Elasticsearch i deployment context pozostaja krokami sekwencyjnymi,
- Dynatrace i GitLab deterministic sa uruchamiane rownolegle po deployment
  context, bo czytaja ten sam snapshot danych wejsciowych,
- operational context nadal startuje dopiero po dolaczeniu obu wynikow.

Dodatkowo kazdy provider deklaruje teraz jawnie:

- faze kroku,
- jakie evidence czyta,
- jakie evidence publikuje.

To pozwala pokazac realne zaleznosci bez zgadywania ich po `@Order` i nazwach
stringowych.

## 5. AI dostaje generyczne sekcje evidence

Domena i adaptery zachowuja typowane modele.
Na granicy z AI evidence jest ujednolicone do:

- `AnalysisEvidenceSection`,
- `AnalysisEvidenceItem`,
- `AnalysisEvidenceAttribute`.

Powod:

- AI nie powinno znac klas adapterow,
- latwiej dodawac nowych providerow,
- prompt builder i provider AI sa bardziej stabilne.

## 6. GitLab ma dwa rozne capability pracy

### Tryb A: deterministic resolution code evidence

To jest czesc zbierania evidence.

GitLab provider analizuje juz zebrane logs i deployment context, a potem zwraca
konkretne odniesienia do kodu lub fragmenty plikow.

Sam deployment context nie jest juz odpowiedzialnoscia providera GitLaba.
To osobny krok evidence, ktory publikuje:

- `environment`
- `gitLabBranch`
- project hints z deploymentu

### Tryb B: AI-guided fetching

To nie jest evidence provider.
To jest zestaw narzedzi, z ktorych AI moze korzystac podczas sesji:

- search repository candidates,
- find flow context,
- read repository file outline,
- read repository file,
- read repository file chunk,
- read repository file chunks.

Powod rozdzielenia:

- inna odpowiedzialnosc,
- inny moment wykonania,
- inna kontrola kosztu i eksploracji.

W kodzie rozdzielamy to tez pakietowo:

- `analysis.adapter.gitlab`
  generyczny adapter i source resolver,
- `analysis.evidence.provider.deployment`
  rozpoznanie deployment context z logs,
- `analysis.evidence.provider.gitlabdeterministic`
  mapping evidence -> GitLab code references,
- `analysis.evidence.provider.operationalcontext`
  enrichment katalogiem operacyjnym,
- `analysis.mcp.gitlab`
  AI-guided tools.

## 7. Read modele evidence sa typowane

`AnalysisContext` nadal przechowuje generyczne sekcje evidence, ale czytanie
sekcji odbywa sie przez typowane widoki, np. dla:

- logs Elasticsearch,
- deployment context,
- kolejnych derived facts.

Powod:

- providery i orchestrator nie musza znac nazw atrybutow po stringach,
- latwiej utrzymac ewolucje kontraktu evidence,
- zaleznosci miedzy providerami sa czytelniejsze.

## 8. Flow synchroniczny i jobowy reuse'uja te sama orchestration warstwe

`AnalysisService` i `AnalysisJobService` nie skladaja juz analizy osobno.
Oba reuse'uja wspolny `AnalysisOrchestrator`, ktory odpowiada za:

- collect evidence,
- derive deployment facts,
- build `AnalysisAiAnalysisRequest`,
- prepare prompt,
- execute AI,
- zmapowac wynik.

Powod:

- mniej duplikacji,
- jeden runtime flow do utrzymania,
- mniejsze ryzyko, ze sync i async analiza beda zachowywac sie inaczej.

## 9. GitLab tools sa session-bound przez hidden `ToolContext`

Model nie podaje juz `group`, `branch` ani `correlationId` do GitLab MCP tools.

Powod:

- zmniejszenie swobody modelu tam, gdzie kontekst jest juz znany,
- mniejsze ryzyko odczytow z niewlasciwej galezi albo scope,
- lepszy audit trail po `sessionId`, `analysisRunId` i `toolCallId`.

## 10. Database diagnostics sa osobna, opcjonalna capability AI-guided

Database tools:

- nie sa evidence providerem,
- sa wlaczane warunkowo po `analysis.database.enabled=true`,
- biora `environment` z hidden `ToolContext`,
- rozwiazuja scope danych przez
  `application/deployment/container/project name -> configured schema`,
- pracuja na readonly, typed tools i exact `schema.table`.

Powod:

- model powinien myslec w kategoriach aplikacji z evidence, a nie Oracle
  ownera,
- DB capability ma pomagac potwierdzic lub obalic hipotezy danych, a nie stac
  sie osobnym pipeline ingestion.

## 11. Skill Copilota jest zasobem runtime aplikacji

Skill nie jest trzymany w `.github`, tylko w `src/main/resources`.

Powod:

- aplikacja ma byc deployowalna jako JAR/kontener,
- skill ma byc dostepny w runtime,
- skill zmienia sie razem z capability projektu.

Runtime loader wypakowuje skill do katalogu filesystemowego, bo sesja Copilota
potrzebuje realnej sciezki do katalogu ze skillami.

## 12. Token GitLaba pochodzi z konfiguracji aplikacji

Na obecnym etapie token nie jest brany z requestu.

Powod:

- to pasuje do obecnej architektury integracyjnej,
- upraszcza flow `analysis`,
- ogranicza liczbe wariantow do utrzymania.

Jesli kiedys pojawi sie potrzeba per-request auth, to powinno to byc swiadome
rozszerzenie, a nie przypadkowy mix modeli autoryzacji.

## 13. Ignorowanie SSL jest lokalne dla konkretnej integracji

Opcje ignorowania SSL istnieja lokalnie tylko tam, gdzie sa naprawde potrzebne,
np. dla GitLaba albo Elasticsearch/Kibana proxy.

Powod:

- projekt dziala w wewnetrznym srodowisku,
- problemy certyfikatowe moga dotyczyc tylko wybranych integracji,
- nie chcemy globalnie oslabiac bezpieczenstwa HTTP klienta dla calej aplikacji.

Aktualny stan:

- GitLab nadal ma osobna konfiguracje `analysis.gitlab.ignore-ssl-errors`,
- Elasticsearch ma lokalny klient REST, ktory zawsze ignoruje bledy certyfikatu
  i hosta tylko dla tej integracji,
- nie ma per-request ani per-property przelacznika `analysis.elasticsearch.mode`
  ani `analysis.elasticsearch.ignore-ssl-errors`.

## 14. Osobny GitLab source resolver jest pomocniczym capability

Endpointy `/api/gitlab/source/resolve` i `/api/gitlab/source/resolve/preview` sa celowym, prostym use
case'em pomocniczym.

Nie sa jeszcze czescia glownego flow `/analysis`.

Powod:

- daja latwy sposob testowania prawdziwego GitLaba,
- pomagaja sprawdzic ranking plikow i pobieranie tresci,
- sa reuse'owane przez deterministic provider do rozwiazywania symboli, ale nie
  musza byc twardo wpiete jako osobny krok orchestration flow.

## 15. Cache drzewa GitLaba jest ograniczony do jednego requestu

`GitLabSourceResolveService` moze cache'owac wynik `repository/tree`, ale tylko
w granicach jednego requestu HTTP.

Powod:

- deterministic provider i endpoint `resolve` potrafia prosic o to samo drzewo
  wielokrotnie przy tym samym `group/project/ref`,
- request-scoped cache ogranicza zbedne wywolania REST bez ryzyka przecieku
  danych miedzy analizami,
- nie chcemy jeszcze wprowadzac globalnego cache z polityka wygasania i
  synchronizacja.

## 16. Operacyjny frontend jest aplikacja Angular w tym samym repo

Ekran `GET /` nie jest juz utrzymywany jako reczne `html + js + scss + css`.
Zrodlowy frontend zyje w `frontend/`, a produkcyjny build zapisuje bundle do
`src/main/resources/static`.

Powod:

- poprzednia implementacja przestala byc utrzymywalna i trudna w review,
- Angular daje czytelniejszy podzial na komponenty, serwisy i modele UI,
- nadal zachowujemy jeden artefakt deployowalny po stronie Spring Boot,
- lokalny dev moze dzialac przez `npm start` z proxy na backend, bez
  osobnego deployu frontendu.

## 17. Sesja Copilota ma allowliste tooli i blokade lokalnego workspace

Sesja Copilota nie powinna miec dostepu do lokalnego dysku, terminala ani
domyslnych workspace tools podczas glownego flow `/analysis`.

Powod:

- analiza ma opierac sie najpierw na attachmentach wygenerowanych z evidence,
- dostep do lokalnego repo lub PowerShella wprowadza niepotrzebna sciezke
  eksploracji poza zakresem incydentu,
- chcemy miec jawna, backendowa kontrole nad tym, jakie capability sa dostepne
  modelowi w danej sesji.

Aktualny model:

- `SessionConfig.availableTools` ogranicza sesje do jawnie zarejestrowanych
  Spring tools,
- lokalne filesystem/shell tools nie sa wystawiane do sesji,
- Elasticsearch i GitLab capability sa attachment-gated:
  jesli odpowiadajace im dane sa juz dolaczone do sesji jako attachmenty,
  backend nie wystawia tych tool groups do modelu,
- tools pozostaja fallbackiem tylko dla brakujacej widocznosci, a nie pierwszym
  krokiem eksploracji.
