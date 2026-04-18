# Krok 16: Copilot SDK Korzysta Z GitLab Tooli

W tym kroku spinamy `copilot-sdk-java` z naszym zestawem GitLab tooli.

Uwaga:
od tego czasu kontrakt GitLab tooli zostal doprecyzowany. Dzisiaj model
dostaje juz jawne `gitLabGroup` i `branch`, a narzedzia pracuja na
`group/projectName/branch/filePath`. Szczegoly aktualnego stanu sa w kroku 18.

To jest bardzo wazny moment, bo od teraz:

- nie mamy juz tylko statycznego promptu z evidence,
- Copilot moze dostac do sesji realne narzedzia,
- a te narzedzia sa tym samym zestawem capability, ktory wystawiamy przez MCP.

## Cel tego kroku

Chcemy, zeby provider AI:

1. dostawal evidence bazowe z Elastic, Dynatrace i deterministycznego GitLaba,
2. mogl sam zdecydowac, czy to wystarcza,
3. jesli nie, mogl iteracyjnie dociagac kod przez:
   - `gitlab_search_repository_candidates`
   - `gitlab_read_repository_file`
   - `gitlab_read_repository_file_chunk`

## Co dodalismy

- `CopilotSdkToolBridge`
- rejestracje Springowych `ToolCallback` jako `ToolDefinition` dla Copilot SDK
- dopiecie tooli do `SessionConfig`
- dopiecie `onPermissionRequest`, bo ta wersja Copilot SDK wymaga jawnego
  handlera uprawnien przy tworzeniu sesji
- rozszerzenie promptu o instrukcje korzystania z tooli

## Gdzie patrzec w kodzie

- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/tools/CopilotSdkToolBridge.java`
- `src/main/java/pl/mkn/incidenttracker/analysis/ai/copilot/preparation/CopilotSdkPreparationService.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/ai/copilot/CopilotSdkToolBridgeTest.java`
- `src/test/java/pl/mkn/incidenttracker/analysis/ai/copilot/CopilotSdkPreparationServiceTest.java`

## Jak to dziala

`CopilotSdkToolBridge`:

- zbiera wszystkie `ToolCallbackProvider` z IoC,
- flattenuje je do pojedynczej listy tool callbackow,
- deduplikuje po nazwie,
- zamienia kazdy Springowy tool na `ToolDefinition` dla Copilot SDK,
- przekazuje wywolanie z powrotem do `ToolCallback.call(...)`.

To oznacza, ze:

- nie duplikujemy logiki GitLaba,
- nie piszemy osobnych handlerow tylko dla Copilota,
- jeden zestaw tooli sluzy i MCP serverowi, i sesji Copilota.

## Obowiazkowy permission handler

W `copilot-sdk-java 0.2.1-java.0` sesja wymaga jawnego
`onPermissionRequest`.

Bez tego przy `createSession(...)` pojawia sie blad:

`An onPermissionRequest handler is required when creating a session`

Dlatego ustawiamy polityke w konfiguracji:

```properties
analysis.ai.copilot.permission-mode=approve-all
```

Na obecnym etapie to jest sensowne, bo:

- nasze GitLab tool-e sa read-only,
- chcemy przejsc przez pierwszy realny flow bez blokady sesji,
- polityka jest jawna i mozliwa do zmiany.

Dostepne tryby:

- `approve-all`
- `deny-all`

## Dlaczego to nie jest jeszcze pelny transport MCP

To wazne rozroznienie.

W tym kroku Copilot dostaje te same capability co MCP server, ale przez
embedded bridge w JVM, a nie przez osobny transport MCP do lokalnego endpointu.

To jest celowe, bo:

- jest prostsze do zrozumienia,
- nie wymaga dodatkowego hopa sieciowego,
- pozwala reuse'owac identyczne tool-e bez duplikacji,
- daje szybszy, stabilniejszy pierwszy krok integracji.

Czyli:

- MCP server nadal istnieje i moze byc uzywany z zewnatrz,
- Copilot session dostaje te same narzedzia lokalnie,
- pozniej mozemy przejsc do prawdziwego `mcpServers`, jesli bedziemy chcieli
  zewnetrznego transportu.

## Co zmienilo sie w promptcie

Prompt teraz jasno mowi modelowi:

- kiedy ma rozwazac uzycie GitLab tooli,
- zeby nie zakladal faktow bez evidence,
- zeby preferowal `gitlab_read_repository_file_chunk`
  przed czytaniem calego pliku.

To jest wazne, bo samo wystawienie tooli nie wystarczy. Model musi dostac tez
jasna strategie ich uzycia.

## Jak testowac

```powershell
mvn test
```

Najwazniejsze testy:

- `CopilotSdkToolBridgeTest`
- `CopilotSdkPreparationServiceTest`

## Jak debugowac

Ustaw breakpointy w:

- `CopilotSdkToolBridge.buildToolDefinitions()`
- `CopilotSdkToolBridge.invokeSpringToolCallback(...)`
- `CopilotSdkPreparationService.prepare(...)`
- `CopilotSdkExecutionGateway.execute(...)`
- `CopilotSessionEventLogger.log(...)`
- `CopilotClientLifecycleLogger.log(...)`

W logach aplikacji bridge wypisuje teraz dodatkowo:

- `sessionId`
- `toolCallId`
- `toolName`
- skrocone argumenty wejscia
- skrocony preview wyniku

To bardzo pomaga odroznic:

- wywolanie toola z zewnetrznego MCP klienta
- od wywolania tego samego toola przez sesje Copilota

Od tego kroku mamy tez osobny logger eventow sesji przez `session.on(...)`.
Loguje on m.in.:

- start wywolania toola,
- zakonczenie wywolania toola,
- bledy sesji,
- finalne wiadomosci asystenta.

Dodatkowo `CopilotClient` loguje teraz lifecycle na poziomie klienta przez
`onLifecycle(...)`, czyli np.:

- `CREATED`
- `UPDATED`
- `DELETED`
- `FOREGROUND`
- `BACKGROUND`

To jest bardziej warstwa control plane niz sama analiza, ale bardzo pomaga przy
debugowaniu zycia sesji i zachowania klienta SDK.

## Co warto zrozumiec po tym kroku

1. Dlaczego bridge do `ToolDefinition` jest lepszy niz kopiowanie logiki tooli?
2. Czym rozni sie embedded tool bridge od prawdziwego zewnetrznego MCP transportu?
3. Dlaczego prompt powinien sugerowac preferencje dla chunkow zamiast pelnych plikow?

## Co dalej

Nastepny dobry krok to:

- uruchomic realny flow z aktualnym runtime providerem Copilot SDK,
- sprawdzic, czy model faktycznie korzysta z GitLab tooli,
- a potem dodac obserwowalnosc sesji i eventow tool execution.
