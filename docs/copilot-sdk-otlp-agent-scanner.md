# Diagnostyka Copilot SDK w Agent Scanner

Team Delivery Workspace wysyla sygnaly OpenTelemetry procesu Copilot CLI do
osobnego Agent Scanner, gdy uruchomiona zostaje nowa sesja Copilota. Obecny
`application.properties` wlacza eksport do `http://127.0.0.1:8081`, a zwykle
usage, activity i tool evidence pozostaja widoczne w jobie aplikacji.
Scanner przechowuje surowy payload lokalnie i udostepnia jego widoki operatorowi.

## Konfiguracja lokalna

Agent Scanner uruchomiony na tej maszynie odpowiada pod
`http://127.0.0.1:8081/api/status`. Jego `README.md` i `/api/config` nadal
pokazuja przyklady dla 8080, ale faktyczny port wyznacza `PORT` i aktualny
`application.yml`; najpierw sprawdz port aktywnego odbiornika.

Domyslne ustawienia w `application.properties` to:

```properties
analysis.ai.copilot.telemetry.enabled=${TDW_COPILOT_OTLP_ENABLED:false}
analysis.ai.copilot.telemetry.otlp-endpoint=${TDW_COPILOT_OTLP_ENDPOINT:http://127.0.0.1:8081}
analysis.ai.copilot.telemetry.capture-content=${TDW_COPILOT_OTLP_CAPTURE_CONTENT:true}
```

Po zmianie konfiguracji uruchom ponownie aplikacje i rozpocznij **nowa**
analize uzywajaca Copilot SDK.
Run wymaga takze dzialajacego uwierzytelnienia Copilota (np. lokalnie
ustawionego `COPILOT_GITHUB_TOKEN` w trybie `LOCAL_TOKEN`); nie zapisuj tokena
w repo ani w danych telemetrycznych.
`TDW_COPILOT_OTLP_ENDPOINT` jest adresem bazowym OTLP/HTTP, bez `/v1/traces`;
CLI wybiera sciezke sygnalu. Protokol CLI moze byc JSON lub protobuf; Scanner
przyjmuje oba. Opcjonalny `TDW_COPILOT_OTLP_SOURCE_NAME` domyslnie ma wartosc
`team-delivery-workspace`. Nie trzeba konfigurowac eksportu GitHub Copilot we
wtyczce IDE, bo ten eksport dotyczy sesji SDK aplikacji.

Przy starcie log aplikacji pokazuje `Copilot OTLP export configured` z flaga,
endpointem, sourceName i captureContent. Niepoprawny endpoint przy wlaczonym
trybie zatrzymuje start aplikacji z nazwa wlasciwosci do poprawienia. Brak
Scanner podczas pozniejszego eksportu nie powinien zmieniac wyniku analizy;
brak sygnalu diagnozuj oddzielnie przez status i logi CLI.

## Kontrola odbioru

1. Przed runem odczytaj `Invoke-RestMethod http://127.0.0.1:8081/api/status` i
   zapisz `traces`, `metrics`, `logs`, `lastSignalAt` oraz `paused`.
2. Uruchom kontrolowana analize bez wrazliwych danych. Zanotuj czas startu,
   identyfikator joba i wersje CLI z eventu `platform.copilot_runtime`.
3. Odczytaj status ponownie. Potwierdz nowy czas sygnalu i wzrost `traces`.
   `connected=true` oznacza dowolny historyczny sygnal, nie biezace polaczenie.
   `contentCaptured=true` takze moze pochodzic ze starszych sesji i nie
   dowodzi, ze biezacy run TDW mial wlaczone `capture-content`.
4. Odczytaj `Invoke-RestMethod http://127.0.0.1:8081/api/sessions` i znajdz
   nowy rekord z czasu runu. Dla Incident Analysis `conversationId` Copilota
   ma prefiks `analysis-`; identyfikator sesji nie jest polem publicznego
   snapshotu joba. Jego
   `/api/sessions/{id}` udostepnia spany i raw signals. Sprawdz obecne
   `invoke_agent`, `chat`, model i liczniki usage. Brak atrybutu pozostaje
   brakiem danych; nie zastapuj go zerem ani estymacja.
5. Jesli Scanner odebral trace, ale nie potrafi znormalizowac sesji lub rund,
   porownaj raw keys z fixture Scanner i dodaj tam osobny, zanonimizowany test
   kontraktowy. Nie zmieniaj znaczenia wyemitowanych danych w TDW.

## Prywatnosc i rollback

`TDW_COPILOT_OTLP_CAPTURE_CONTENT=false` nie wysyla pelnych promptow i
odpowiedzi przez opcje capture content CLI. Metadane i inne atrybuty nadal
mogą ujawniac informacje operacyjne; ocen zasady dostepu i retencji Scanner.
`true` wlaczaj tylko swiadomie w kontrolowanym srodowisku: moze zapisac prompty,
kod, logi, argumenty i wyniki tools w jego bazie H2.

Aby wycofac eksport, ustaw `TDW_COPILOT_OTLP_ENABLED=false` w srodowisku
procesu i uruchom ponownie TDW. Samo usuniecie zmiennej przywraca domyslne
`true`. Dane juz zapisane w Scanner usuwa sie wedlug jego
retencji lub przez funkcje usuwania sesji; wylaczenie eksportu ich nie usuwa.
Ambientne zmienne `COPILOT_OTEL_*` lub `OTEL_EXPORTER_OTLP_*` ustawione poza TDW
mogą niezaleznie wlaczyc eksporter w procesie CLI, wiec przy diagnozie braku
rollbacku sprawdz takze srodowisko procesu.
